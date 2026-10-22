/**
 * 별빛 공방(2026-10-22) — 별가루 지갑 · 성운 상자 뽑기 서버부.
 *
 * 클라이언트는 별가루/아이템을 **직접 쓰지 못한다**(wallets 는 규칙상 읽기 전용). 대신
 *   users/{uid}/starlightOps/{opId} 에 { op, ...인자, status: "pending" } 문서를 만들면
 * 이 트리거가 트랜잭션으로 처리하고 같은 문서에 { status: "done", result } 또는 { status: "error", error } 를 쓴다.
 * (Callable 대신 요청 문서 — 두 앱에 Functions SDK 를 새로 넣지 않고, 오프라인이면 요청이 쌓였다가 처리된다.)
 * 트리거는 최소 1회 실행이라 같은 요청이 두 번 올 수 있다 → 트랜잭션 안에서 status 가 pending 일 때만 처리(멱등).
 *
 * 데이터
 *  - wallets/{uid}            : { stardust, boxes, items{itemId: n}, pity{sinceEpic, sinceLegendary},
 *                                 questClaims{day: [questId | "__bonus"]}, opened, updatedAt }
 *  - starlightConfig/main     : 비용·확률·천장·중복 환급(없으면 DEFAULT_CONFIG). 앱이 확률 공개에 그대로 쓴다.
 *  - starlightConfig/quests   : 일일 퀘스트 정의(tools/starlight/quests.js) — 목표 수(target)·난이도별 보상(rewards[tier]).
 *                               앱도 같은 문서로 목록을 뽑고 문구를 그린다(예전엔 세 곳에 따로 박혀 있었다).
 *  - starlightItems/{itemId}  : 뽑기 풀(tools/starlight/seed.js 로 올린다). active=false 면 뽑히지 않는다.
 *  - starlightEquips/{uid}    : 장착한 프레임·배지·오라·칭호(서버만 씀 — 가진 것만 장착되게, 모두 읽음). 칭호는 users.equippedTitle 에도 "sl:{id}".
 *  - starlightHints/{achId}   : 히든 업적 단서 { "1": {ko,en,ja}, "2": {...} } — **아무도 못 읽는다**. 망원경을 쓰면 그 문구를
 *                               지갑 hints 에 복사해 준다(망원경 없이 단서를 다 읽어 갈 수 없게).
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");
const crypto = require("crypto");

const DATABASE_ID = "stary-db";
const REGION = "asia-northeast3";

const RARITIES = ["common", "rare", "epic", "legendary"];
const RANK = { common: 0, rare: 1, epic: 2, legendary: 3 };

const DEFAULT_CONFIG = {
  questStardust: 10,
  bonusStardust: 20,
  keyCost: 10,
  boxCost: 100,
  rarityWeights: { common: 60, rare: 28, epic: 10, legendary: 2 },
  pityEpic: 10, // 이 번째 상자는 영웅 이상 확정(그 전 9개에서 못 뽑았으면)
  pityLegendary: 50, // 이 번째 상자는 전설 확정
  dupRefund: { common: 10, rare: 25, epic: 50, legendary: 100 },
  maxOpen: 10,
  maxExchange: 99,
};

/** 하루 퀘스트 수(QuestCatalog.PER_DAY) — users 문서는 본인이 쓸 수 있어 ids 를 늘려 보상을 더 받지 못하게 막는다. */
const QUESTS_PER_DAY = 3;
const BONUS_MARK = "__bonus";
/** 지갑에 남겨 두는 퀘스트 수령 기록 일수(시간대 차이로 하루 앞뒤가 겹칠 수 있어 넉넉히). */
const CLAIM_KEEP_DAYS = 4;

const db = () => getFirestore(DATABASE_ID);

class OpError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

async function loadConfig() {
  const snap = await db().collection("starlightConfig").doc("main").get();
  const c = snap.exists ? snap.data() : {};
  return {
    ...DEFAULT_CONFIG,
    ...c,
    rarityWeights: { ...DEFAULT_CONFIG.rarityWeights, ...(c.rarityWeights || {}) },
    dupRefund: { ...DEFAULT_CONFIG.dupRefund, ...(c.dupRefund || {}) },
  };
}

/** "yyyy-MM-dd" 가 서버 UTC 날짜와 하루 이내인지 — 기기 현지 날짜를 쓰므로 ±14시간 시간대를 덮는다. */
function isNearToday(day) {
  if (typeof day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(day)) return false;
  const t = Date.parse(day + "T00:00:00Z");
  if (Number.isNaN(t)) return false;
  const today = Date.parse(new Date().toISOString().slice(0, 10) + "T00:00:00Z");
  return Math.abs(t - today) <= 24 * 60 * 60 * 1000;
}

/** 퀘스트 정의 문서 → { byId: Map<id, def>, rewards }. 문서가 없거나 그 id 를 모르면 목표 1 · 보상 questStardust. */
async function loadQuests() {
  const snap = await db().collection("starlightConfig").doc("quests").get();
  const d = snap.exists ? snap.data() : {};
  const byId = new Map((Array.isArray(d.pool) ? d.pool : []).filter((q) => q && typeof q.id === "string").map((q) => [q.id, q]));
  return { byId, rewards: d.rewards || {} };
}

function pruneClaims(claims) {
  const days = Object.keys(claims).sort();
  const keep = days.slice(-CLAIM_KEEP_DAYS);
  const out = {};
  for (const d of keep) out[d] = claims[d];
  return out;
}

function intArg(v, min, max) {
  return Number.isInteger(v) && v >= min && v <= max ? v : null;
}

/** 균등 난수 [0, 1). */
function rand() {
  return crypto.randomInt(0, 0x7fffffff) / 0x7fffffff;
}

function weightedPick(entries) {
  const total = entries.reduce((s, e) => s + Math.max(0, e.weight), 0);
  if (total <= 0) return null;
  let r = rand() * total;
  for (const e of entries) {
    r -= Math.max(0, e.weight);
    if (r < 0) return e.value;
  }
  return entries[entries.length - 1].value;
}

/**
 * 등급 하나를 고른다. 천장: sinceLegendary+1 ≥ pityLegendary → 전설, sinceEpic+1 ≥ pityEpic → 영웅 이상(확률 비율 유지).
 * 풀에 그 등급 아이템이 없으면 한 단계씩 내려간다(그래도 없으면 올라간다).
 */
function pickRarity(cfg, pity, available) {
  let floor = "common";
  if (pity.sinceLegendary + 1 >= cfg.pityLegendary) floor = "legendary";
  else if (pity.sinceEpic + 1 >= cfg.pityEpic) floor = "epic";
  const entries = RARITIES.filter((r) => RANK[r] >= RANK[floor])
    .map((r) => ({ value: r, weight: Number(cfg.rarityWeights[r]) || 0 }));
  let rarity = weightedPick(entries) || floor;
  if (available.has(rarity)) return rarity;
  for (let i = RANK[rarity] - 1; i >= 0; i--) if (available.has(RARITIES[i])) return RARITIES[i];
  for (let i = RANK[rarity] + 1; i < RARITIES.length; i++) if (available.has(RARITIES[i])) return RARITIES[i];
  return null;
}

/** 쌓이는(소모품) 아이템인가 — 중복이어도 환급하지 않고 개수가 는다. 열쇠 묶음은 뽑는 즉시 열쇠로 바뀐다. */
function isStackable(item) {
  return item.kind === "telescope" || item.kind === "keyBundle" || item.stackable === true;
}

// ── op 처리기 — (tx, ctx) → result. ctx = { uid, op, walletRef, userRef, wallet, user, cfg, items } ──

function claimQuest(tx, ctx) {
  const { op, wallet, user, cfg, quests } = ctx;
  const day = op.day;
  const id = op.questId;
  if (!isNearToday(day) || typeof id !== "string") throw new OpError("invalid");
  const quest = user.quest || {};
  if (quest.day !== day || !Array.isArray(quest.ids) || !quest.ids.includes(id)) throw new OpError("not_ready");
  if (quest.ids.length > QUESTS_PER_DAY) throw new OpError("invalid");
  const def = quests?.byId.get(id);
  const progress = Number((quest.progress || {})[id]) || 0;
  if (progress < Math.max(1, Number(def?.target) || 1)) throw new OpError("not_ready");
  const claims = { ...(wallet.questClaims || {}) };
  const todays = claims[day] || [];
  // 예전 앱이 열쇠로 이미 받은 퀘스트(quest.claimed)도 다시 받지 못한다.
  if (todays.includes(id) || (quest.claimed || []).includes(id)) throw new OpError("already");
  claims[day] = [...todays, id];
  // 난이도별 보상(쉬움·보통·어려움) — 정의를 모르면 기본값.
  const tierReward = Number(quests?.rewards?.[def?.tier]);
  const gained = Number.isFinite(tierReward) && tierReward > 0 ? tierReward : cfg.questStardust;
  wallet.stardust = (wallet.stardust || 0) + gained;
  wallet.questClaims = pruneClaims(claims);
  tx.update(ctx.userRef, { "quest.claimed": FieldValue.arrayUnion(id) });
  return { gained, stardust: wallet.stardust };
}

function claimBonus(tx, ctx) {
  const { op, wallet, user, cfg } = ctx;
  const day = op.day;
  if (!isNearToday(day)) throw new OpError("invalid");
  const quest = user.quest || {};
  const ids = Array.isArray(quest.ids) ? quest.ids : [];
  const claimed = quest.claimed || [];
  if (quest.day !== day || ids.length === 0 || !ids.every((q) => claimed.includes(q))) throw new OpError("not_ready");
  if (ids.length > QUESTS_PER_DAY) throw new OpError("invalid");
  const claims = { ...(wallet.questClaims || {}) };
  const todays = claims[day] || [];
  if (quest.bonus === true || todays.includes(BONUS_MARK)) throw new OpError("already");
  claims[day] = [...todays, BONUS_MARK];
  const gained = cfg.bonusStardust;
  wallet.stardust = (wallet.stardust || 0) + gained;
  wallet.questClaims = pruneClaims(claims);
  tx.update(ctx.userRef, { "quest.bonus": true, questClearDays: FieldValue.arrayUnion(day) });
  return { gained, stardust: wallet.stardust };
}

function exchange(tx, ctx) {
  const { op, wallet, cfg } = ctx;
  const count = intArg(op.count, 1, cfg.maxExchange);
  if (count == null || (op.to !== "key" && op.to !== "box")) throw new OpError("invalid");
  const cost = (op.to === "key" ? cfg.keyCost : cfg.boxCost) * count;
  if ((wallet.stardust || 0) < cost) throw new OpError("insufficient");
  wallet.stardust -= cost;
  if (op.to === "key") {
    tx.set(ctx.userRef, { questKeys: FieldValue.increment(count) }, { merge: true });
  } else {
    wallet.boxes = (wallet.boxes || 0) + count;
  }
  return { to: op.to, count, cost, stardust: wallet.stardust, boxes: wallet.boxes || 0 };
}

function openBox(tx, ctx) {
  const { op, wallet, cfg, items } = ctx;
  const count = intArg(op.count, 1, cfg.maxOpen);
  if (count == null) throw new OpError("invalid");
  if ((wallet.boxes || 0) < count) throw new OpError("no_boxes");
  if (items.length === 0) throw new OpError("empty_pool");
  const byRarity = new Map();
  for (const it of items) {
    if (!byRarity.has(it.rarity)) byRarity.set(it.rarity, []);
    byRarity.get(it.rarity).push(it);
  }
  const available = new Set(byRarity.keys());
  const owned = { ...(wallet.items || {}) };
  const pity = {
    sinceEpic: Number(wallet.pity?.sinceEpic) || 0,
    sinceLegendary: Number(wallet.pity?.sinceLegendary) || 0,
  };
  const results = [];
  let refundTotal = 0;
  let keysTotal = 0;
  for (let i = 0; i < count; i++) {
    const rarity = pickRarity(cfg, pity, available);
    if (!rarity) throw new OpError("empty_pool");
    const pool = byRarity.get(rarity);
    const item = weightedPick(pool.map((it) => ({ value: it, weight: Number(it.weight) || 1 })));
    pity.sinceEpic = RANK[rarity] >= RANK.epic ? 0 : pity.sinceEpic + 1;
    pity.sinceLegendary = rarity === "legendary" ? 0 : pity.sinceLegendary + 1;
    const r = { id: item.id, rarity, kind: item.kind, dup: false, refund: 0, keys: 0 };
    if (item.kind === "keyBundle") {
      r.keys = Math.max(1, Number(item.payload?.keys) || 3);
      keysTotal += r.keys;
      owned[item.id] = (owned[item.id] || 0) + 1; // 몇 번 뽑았는지(도감 표시용)
    } else if (isStackable(item)) {
      owned[item.id] = (owned[item.id] || 0) + 1;
    } else if (owned[item.id]) {
      r.dup = true;
      r.refund = Number(cfg.dupRefund[rarity]) || 0;
      refundTotal += r.refund;
    } else {
      owned[item.id] = 1;
    }
    results.push(r);
  }
  wallet.boxes -= count;
  wallet.items = owned;
  wallet.pity = pity;
  wallet.stardust = (wallet.stardust || 0) + refundTotal;
  wallet.opened = (wallet.opened || 0) + count;
  if (keysTotal > 0) tx.set(ctx.userRef, { questKeys: FieldValue.increment(keysTotal) }, { merge: true });
  return { results, stardust: wallet.stardust, boxes: wallet.boxes };
}

const SLOTS = { frame: "frame", badge: "badge", aura: "aura", title: "title" };

/** 장착/해제 — 가진 아이템만, 칸과 종류가 맞아야 한다. ctx.item = 대상 아이템 문서(없으면 null). */
function equip(tx, ctx) {
  const { op, wallet, item, user } = ctx;
  const slot = SLOTS[op.slot];
  if (!slot) throw new OpError("invalid");
  const id = op.itemId == null ? null : String(op.itemId);
  if (id) {
    if (!item || item.kind !== slot) throw new OpError("invalid");
    if (!((wallet.items || {})[id] > 0)) throw new OpError("not_owned");
  }
  tx.set(ctx.equipRef, { [slot]: id, updatedAt: Date.now() }, { merge: true });
  if (slot === "title") {
    const cur = typeof user.equippedTitle === "string" ? user.equippedTitle : "";
    // 해제는 지금 공방 칭호를 달고 있을 때만 비운다(업적 칭호를 단 사람의 칭호를 지우지 않게).
    if (id) tx.update(ctx.userRef, { equippedTitle: "sl:" + id });
    else if (cur.startsWith("sl:")) tx.update(ctx.userRef, { equippedTitle: "" });
  }
  return { slot, itemId: id };
}

/** 소모품 사용 — 천체 망원경. reveal: 업적 ??? 조건 1개 공개 / hint: 히든 업적 단서(레벨 1 흐릿 · 2 선명). */
function useItem(tx, ctx) {
  const { op, wallet, item, hint } = ctx;
  const id = String(op.itemId || "");
  const target = typeof op.target === "string" ? op.target : "";
  if (!item || item.kind !== "telescope" || !/^[a-z0-9_]{1,40}$/.test(target)) throw new OpError("invalid");
  const owned = { ...(wallet.items || {}) };
  if (!(owned[id] > 0)) throw new OpError("not_owned");
  const mode = item.payload?.mode;
  let result;
  if (mode === "reveal") {
    const revealed = { ...(wallet.revealed || {}) };
    if (revealed[target]) throw new OpError("already");
    revealed[target] = true;
    wallet.revealed = revealed;
    result = { mode, target };
  } else if (mode === "hint") {
    const level = Math.max(1, Math.min(2, Number(item.payload?.level) || 1));
    const hints = { ...(wallet.hints || {}) };
    if ((hints[target]?.level || 0) >= level) throw new OpError("already");
    const text = hint?.[String(level)];
    if (!text) throw new OpError("invalid");
    hints[target] = { level, text };
    wallet.hints = hints;
    result = { mode, target, level, text };
  } else {
    throw new OpError("invalid");
  }
  owned[id] -= 1;
  if (owned[id] <= 0) delete owned[id];
  wallet.items = owned;
  return result;
}

const HANDLERS = { claimQuest, claimBonus, exchange, openBox, equip, useItem };

exports.onStarlightOp = onDocumentCreated(
  {
    document: "users/{uid}/starlightOps/{opId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const uid = event.params.uid;
    const opRef = snap.ref;
    const first = snap.data() || {};
    const handler = HANDLERS[first.op];
    if (!handler) {
      await opRef.set({ status: "error", error: "unknown_op", doneAt: Date.now() }, { merge: true });
      return;
    }
    const cfg = await loadConfig();
    const quests = first.op === "claimQuest" ? await loadQuests() : null;
    let items = [];
    if (first.op === "openBox") {
      const q = await db().collection("starlightItems").where("active", "==", true).get();
      items = q.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((it) => RARITIES.includes(it.rarity) && typeof it.kind === "string");
    }
    let item = null;
    let hint = null;
    if ((first.op === "equip" || first.op === "useItem") && typeof first.itemId === "string" && first.itemId) {
      const it = await db().collection("starlightItems").doc(first.itemId).get();
      item = it.exists ? it.data() : null;
      if (first.op === "useItem" && item?.payload?.mode === "hint" && typeof first.target === "string" && first.target) {
        const h = await db().collection("starlightHints").doc(first.target).get();
        hint = h.exists ? h.data() : null;
      }
    }
    const walletRef = db().collection("wallets").doc(uid);
    const userRef = db().collection("users").doc(uid);
    const equipRef = db().collection("starlightEquips").doc(uid);
    try {
      await db().runTransaction(async (tx) => {
        const [opSnap, walletSnap, userSnap] = await Promise.all([tx.get(opRef), tx.get(walletRef), tx.get(userRef)]);
        const op = opSnap.data() || {};
        if (op.status !== "pending") return; // 이미 처리됨(트리거 재실행)
        if (!userSnap.exists) throw new OpError("not_ready");
        const wallet = walletSnap.exists ? { ...walletSnap.data() } : {};
        const ctx = { uid, op, walletRef, userRef, equipRef, wallet, user: userSnap.data(), cfg, items, item, hint, quests };
        let result;
        try {
          result = handler(tx, ctx);
        } catch (e) {
          if (!(e instanceof OpError)) throw e;
          tx.update(opRef, { status: "error", error: e.code, doneAt: Date.now() });
          return;
        }
        wallet.updatedAt = Date.now();
        tx.set(walletRef, wallet);
        tx.update(opRef, { status: "done", result, doneAt: Date.now() });
      });
    } catch (e) {
      // 트랜잭션 앞단에서 던진 OpError(예: users 문서 없음 → not_ready)는 그 코드 그대로 돌려준다 — 클라이언트 안내가 맞게.
      const code = e instanceof OpError ? e.code : "server";
      if (code === "server") logger.error(`starlight op ${first.op} 실패 uid=${uid}`, e);
      else logger.warn(`starlight op ${first.op} 거절 uid=${uid} ${code}`);
      await opRef.set({ status: "error", error: code, doneAt: Date.now() }, { merge: true }).catch(() => {});
    }
  },
);
