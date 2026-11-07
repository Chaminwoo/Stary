/**
 * 서버 쿼터/입력 정리 공용 도구.
 *  - rateLimits/{key}        : 고정 창 카운터(Admin SDK 전용 컬렉션 — 클라이언트 규칙은 기본 거부).
 *  - 오래된 카운터 문서는 `expireAt` 필드에 Firestore TTL 정책을 걸어 정리한다(콘솔에서 rateLimits.expireAt).
 * 순수 함수(decideQuota, clip, cleanName, audience 판정)는 테스트에서 직접 쓴다.
 */
const logger = require("firebase-functions/logger");

const RATE_LIMITS = "rateLimits";
const USERS = "users";

/** 문서 id 로 안전한 키로 바꾼다 — "/" 금지, 예약 id("."/"..", "__x__") 회피, 너무 길면 잘라 해시 대신 접미 처리. */
function safeKey(key) {
  let k = String(key == null ? "" : key).replace(/\//g, "_");
  if (k === "" || k === "." || k === ".." || /^__.*__$/.test(k)) k = `k_${k}`;
  if (Buffer.byteLength(k, "utf8") > 400) {
    k = `${[...k].slice(0, 100).join("")}~${require("crypto").createHash("sha1").update(k).digest("hex")}`;
  }
  return k;
}

/**
 * 고정 창 판정(순수). prev = 저장된 { start, count } 또는 undefined.
 * @returns { allowed, next } — allowed 면 next 를 저장한다.
 */
function decideQuota(prev, now, windowMs, max, amount = 1) {
  const start = Math.floor(now / windowMs) * windowMs;
  const count = prev && prev.start === start && Number.isFinite(prev.count) ? prev.count : 0;
  if (count + amount > max) return { allowed: false, next: null };
  return { allowed: true, next: { start, count: count + amount } };
}

/**
 * 쿼터 1회(또는 amount) 소비. 허용이면 true, 초과면 false.
 * Firestore 오류 시: opts.onError === "deny" 면 false, 그 외("allow", 기본) 면 true.
 * opts 는 문자열("allow"|"deny")도 받는다. opts.amount 로 글자 수 같은 가중치를 소비할 수 있다.
 */
async function consumeQuota(db, key, windowMs, max, opts) {
  const o = typeof opts === "string" ? { onError: opts } : opts || {};
  const onError = o.onError === "deny" ? "deny" : "allow";
  const amount = o.amount > 0 ? o.amount : 1;
  try {
    const ref = db.collection(RATE_LIMITS).doc(safeKey(key));
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const prev = snap.exists ? snap.data() : undefined;
      const now = Date.now();
      const { allowed, next } = decideQuota(prev, now, windowMs, max, amount);
      if (allowed) tx.set(ref, { ...next, expireAt: new Date(next.start + windowMs * 2) });
      return allowed;
    });
  } catch (e) {
    logger.warn(`quota ${String(key).slice(0, 60)}: 확인 실패(${onError}) — ${e.code || ""} ${e.message}`);
    return onError !== "deny";
  }
}

/** 문자열이 아니면 "", 코드포인트(서로게이트 쌍) 기준 n 글자로 자른다. */
function clip(s, n) {
  if (typeof s !== "string") return "";
  const cps = [...s];
  return cps.length <= n ? s : cps.slice(0, n).join("");
}

/** 표시 이름 정리 — 제어문자·개행 제거, 공백 정리, 30자. */
function cleanName(s) {
  if (typeof s !== "string") return "";
  // eslint-disable-next-line no-control-regex
  const t = s.replace(/[\u0000-\u001f\u007f-\u009f  ]+/g, " ").replace(/\s+/g, " ").trim();
  return clip(t, 30);
}

/** users/{uid}.userName(서버 저장값). 없거나 읽기 실패면 null. */
async function serverUserName(db, uid) {
  if (!uid || typeof uid !== "string") return null;
  try {
    const snap = await db.collection(USERS).doc(uid).get();
    if (!snap.exists) return null;
    const name = cleanName(snap.get("userName"));
    return name || null;
  } catch (e) {
    logger.warn(`serverUserName ${uid}: 읽기 실패 — ${e.message}`);
    return null;
  }
}

/** recipient 가 sender 를 차단했는가(users/{recipient}/blocked/{sender}). 읽기 실패 시 false(푸시 한 건 정도는 허용). */
async function isBlocked(db, recipient, sender) {
  if (!recipient || !sender) return false;
  try {
    const snap = await db.collection(USERS).doc(recipient).collection("blocked").doc(sender).get();
    return snap.exists;
  } catch (e) {
    logger.warn(`isBlocked ${recipient}: 읽기 실패 — ${e.message}`);
    return false;
  }
}

/** users/{owner}/friends/{other} 존재 여부. 읽기 실패 시 false. */
async function hasFriend(db, owner, other) {
  if (!owner || !other) return false;
  try {
    const snap = await db.collection(USERS).doc(owner).collection("friends").doc(other).get();
    return snap.exists;
  } catch (e) {
    logger.warn(`hasFriend ${owner}: 읽기 실패 — ${e.message}`);
    return false;
  }
}

/** 남용 기록(abuseLog) — 실패해도 호출부를 막지 않는다. */
async function logAbuse(db, uid, kind, refId) {
  try {
    await db.collection("abuseLog").add({ uid: String(uid || ""), kind, refId: String(refId || ""), at: Date.now() });
  } catch (e) {
    logger.warn(`abuseLog 기록 실패 — ${e.message}`);
  }
}

// ── 공개 범위 audience 판정(순수) ──────────────────────────────────────────────

const sameSet = (a, b) => {
  const x = new Set(a), y = new Set(b);
  return x.size === y.size && [...x].every((v) => y.has(v));
};

/**
 * 글 문서 변경에 대해 audience 를 어떻게 해야 하는가: "set" | "delete" | "none".
 *  - 삭제 이벤트(after 없음)는 none.
 *  - friends 글: audience 가 배열이 아니거나, 생성 직후이거나, 공개 범위가 방금 friends 로 바뀐 경우만 set.
 *  - friends 가 아닌데 audience 가 있으면 delete.
 * 자기 쓰기의 결과(set 후/delete 후)에 다시 넣어 보면 항상 none 이 된다.
 */
function audienceAction(before, after) {
  if (!after) return "none";
  if (after.visibilityType === "friends") {
    const created = !before;
    const justChanged = !!before && before.visibilityType !== "friends";
    if (created || justChanged || !Array.isArray(after.audience)) return "set";
    return "none";
  }
  return Object.prototype.hasOwnProperty.call(after, "audience") ? "delete" : "none";
}

module.exports = {
  RATE_LIMITS, safeKey, decideQuota, consumeQuota, clip, cleanName, serverUserName,
  isBlocked, hasFriend, logAbuse, audienceAction, sameSet,
};
