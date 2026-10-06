/**
 * 큐레이션 별(어드민 시드) 업로드 — 전 세계 100곳, 현지어 글 + 위키미디어 공용 사진(2026-10-07).
 *
 *   cd tools/seed && npm install            (최초 1회 — firebase-admin)
 *   node seed.js review    # 로컬 미리보기 HTML(build/review.html) — 인증·DB 접근 없음
 *   node seed.js plan      # 읽기만: 검증(길이/사진/좌표 간격) + 어드민 계정 확인 + 쓸 내용 요약
 *   node seed.js upload    # 사진 업로드 + diaries 생성(이미 있으면 내용만 갱신 — 다시 돌려도 중복 안 생김)
 *   node seed.js remove    # 되돌리기: seed_* 다이어리(+댓글/좋아요) 와 사진 삭제
 *
 * 입력: build/meta.json(좌표·사진, fetch.py meta) + build/img/<id>.jpg(fetch.py images) + stars/*.json(제목·본문).
 *
 * ⚠️ 서버 트리거 우회: diaries onCreate 함수 2개(notifyFriendsOnDiaryCreate = 어드민 친구들에게 푸시,
 *    announceFirstStar = 첫 전체 공개 별이면 **모든 사용자에게** 푸시)가 100번 울리면 안 된다.
 *    두 함수 모두 "나만 보기" 글은 건너뛰므로 **private 으로 만든 뒤 public 으로 바꾼다**(onCreate 는 생성 시점 데이터만 본다).
 *    앱의 FRIEND_POST 인앱 알림은 클라이언트가 만드는 것이라 이 스크립트로는 생기지 않는다.
 *
 * 인증: GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 키)가 있으면 그걸, 없으면 **이 PC 의 firebase CLI 로그인**
 *    (firebase login)으로 임시 ADC 파일을 만들어 쓰고 끝나면 지운다. 프로젝트는 이 포크 전용 momentdiary-f26c8 고정.
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const crypto = require("crypto");

const PROJECT_ID = "momentdiary-f26c8";              // ⚠️ 포크 전용 프로젝트(원본 momentdiary-52b78 금지)
const BUCKET = "momentdiary-f26c8.firebasestorage.app";
const DATABASE_ID = "stary-db";
const ADMIN_EMAIL = "chaalsdn0217@gmail.com";       // StaryConfig.ADMIN_EMAILS
const TITLE_MAX = 30;                                 // StaryConfig.DIARY_TITLE_MAX_LEN
const CONTENT_MAX = 2000;                             // StaryConfig.DIARY_CONTENT_MAX_LEN
const MERGE_RADIUS_M = 30;                            // StaryConfig.STAR_MERGE_RADIUS_M
const SPREAD_DAYS = 120;                              // 작성 시각을 지난 120일에 고르게 흩는다
const DOC_PREFIX = "seed_";
const STORAGE_DIR = "diary_images/seed";

const ROOT = __dirname;
const BUILD = path.join(ROOT, "build");

/** 주제별 별 모양/색(StarStyle: 0~4 별·스파클, 5 꽃, 6 보석, 7 초승달, 8 행성 / 색 0~15 단색). */
const THEME_STYLE = {
  nature: { type: 5, colors: [9, 10, 14, 8] },
  history: { type: 6, colors: [1, 11, 15, 2] },
  science: { type: 8, colors: [7, 8, 13, 0] },
  art: { type: 0, colors: [5, 4, 12, 6] },
  food: { type: 1, colors: [2, 15, 1, 10] },
  vanishing: { type: 7, colors: [0, 8, 7, 5] },
};

// ── 인증 ────────────────────────────────────────────────────────────────────
let tempAdc = null;
function ensureCredentials() {
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) return "GOOGLE_APPLICATION_CREDENTIALS";
  const cfg = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  if (!fs.existsSync(cfg)) {
    throw new Error("firebase CLI 로그인 정보가 없다 — `firebase login` 하거나 GOOGLE_APPLICATION_CREDENTIALS 를 지정할 것");
  }
  const tokens = JSON.parse(fs.readFileSync(cfg, "utf8")).tokens || {};
  if (!tokens.refresh_token) throw new Error("firebase CLI refresh_token 없음 — `firebase login --reauth`");
  // firebase-tools 의 공개 OAuth 클라이언트(firebase-tools/lib/api.js) — CLI 가 쓰는 것과 같은 자격으로 동작.
  tempAdc = path.join(os.tmpdir(), `stary-seed-adc-${process.pid}.json`);
  fs.writeFileSync(tempAdc, JSON.stringify({
    type: "authorized_user",
    client_id: "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com",
    client_secret: "j9iVZfS8kkCEFUPaAeJV0sAi",
    refresh_token: tokens.refresh_token,
  }), { mode: 0o600 });
  process.env.GOOGLE_APPLICATION_CREDENTIALS = tempAdc;
  process.env.GOOGLE_CLOUD_PROJECT = PROJECT_ID;
  return "firebase CLI 로그인";
}
function cleanup() {
  if (tempAdc && fs.existsSync(tempAdc)) fs.unlinkSync(tempAdc);
}
process.on("exit", cleanup);
process.on("SIGINT", () => { cleanup(); process.exit(130); });

// ── 입력 ────────────────────────────────────────────────────────────────────
function loadStars() {
  const meta = JSON.parse(fs.readFileSync(path.join(BUILD, "meta.json"), "utf8"));
  const texts = {};
  const dir = path.join(ROOT, "stars");
  for (const f of fs.readdirSync(dir).filter((f) => f.endsWith(".json"))) {
    Object.assign(texts, JSON.parse(fs.readFileSync(path.join(dir, f), "utf8")));
  }
  const byTheme = {};
  return meta.map((m) => {
    const idx = (byTheme[m.theme] = (byTheme[m.theme] ?? -1) + 1);
    const style = THEME_STYLE[m.theme];
    const t = texts[m.id] || {};
    const ch = m.chosen || {};
    return {
      id: m.id,
      docId: DOC_PREFIX + m.id.replace(/[^a-z0-9-]/gi, "_"),
      theme: m.theme, cc: m.cc, lang: m.lang,
      lat: m.coord ? m.coord[0] : m.lat,
      lng: m.coord ? m.coord[1] : m.lng,
      title: (t.title || "").trim(),
      body: (t.content || "").trim(),
      credit: creditLine(ch),
      image: path.join(BUILD, "img", `${m.id}.jpg`),
      starType: t.starType ?? style.type,
      starColor: t.starColor ?? style.colors[idx % style.colors.length],
      createdAt: spreadTime(m.id),
    };
  });
}

/** 사진 출처 한 줄(라이선스 의무: 작가·라이선스·출처, BY 계열은 변경(크롭) 표시). */
function creditLine(ch) {
  if (!ch || !ch.file) return "";
  const artist = (ch.artist || "").replace(/\s+/g, " ").trim() || "Unknown";
  const lic = (ch.license || "").trim();
  const changed = /^cc[- ]by/i.test(lic) ? " (cropped)" : "";
  return `📷 ${artist} / ${lic} / Wikimedia Commons${changed}`;
}

/** id 로 정해지는 과거 시각(지난 SPREAD_DAYS 일 안, 재실행해도 같은 값). */
function spreadTime(id) {
  const h = crypto.createHash("sha1").update(id).digest();
  const day = h.readUInt32BE(0) % SPREAD_DAYS;
  const minute = h.readUInt32BE(4) % (24 * 60);
  const base = Date.UTC(2026, 9, 7, 0, 0, 0); // 2026-10-07(작성일) 기준 — 실행일과 무관하게 고정
  return base - (day + 1) * 86400000 + minute * 60000;
}

function fullContent(s) {
  return s.credit ? `${s.body}\n\n${s.credit}` : s.body;
}

function haversine(a, b) {
  const R = 6371000, r = Math.PI / 180;
  const dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}

function validate(stars) {
  const problems = [];
  for (const s of stars) {
    if (!s.title) problems.push(`${s.id}: 제목 없음`);
    if ([...s.title].length > TITLE_MAX) problems.push(`${s.id}: 제목 ${[...s.title].length}자 > ${TITLE_MAX}`);
    if (!s.body) problems.push(`${s.id}: 본문 없음`);
    if ([...fullContent(s)].length > CONTENT_MAX) problems.push(`${s.id}: 본문+출처 ${[...fullContent(s)].length}자 > ${CONTENT_MAX}`);
    if (typeof s.lat !== "number" || typeof s.lng !== "number") problems.push(`${s.id}: 좌표 없음`);
    if (!fs.existsSync(s.image)) problems.push(`${s.id}: 사진 파일 없음(fetch.py images)`);
    if (!s.credit) problems.push(`${s.id}: 사진 출처 없음`);
  }
  for (let i = 0; i < stars.length; i++) {
    for (let j = i + 1; j < stars.length; j++) {
      const d = haversine(stars[i], stars[j]);
      if (d < MERGE_RADIUS_M * 2) problems.push(`${stars[i].id} ↔ ${stars[j].id}: ${d.toFixed(0)}m (별 합치기 반경 근처)`);
    }
  }
  return problems;
}

// ── Firebase ────────────────────────────────────────────────────────────────
function firebase() {
  const { initializeApp, applicationDefault } = require("firebase-admin/app");
  const { getFirestore } = require("firebase-admin/firestore");
  const { getStorage } = require("firebase-admin/storage");
  const { getAuth } = require("firebase-admin/auth");
  const app = initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID, storageBucket: BUCKET });
  return { db: getFirestore(app, DATABASE_ID), bucket: getStorage(app).bucket(), auth: getAuth(app) };
}

/** 어드민의 appUserId = Google sub(Auth 사용자 providerData 의 google.com uid) — 앱/규칙과 같은 규칙. */
async function resolveAdmin({ db, auth }) {
  let uid = process.env.STARY_ADMIN_UID || null;
  if (!uid) {
    const user = await auth.getUserByEmail(ADMIN_EMAIL);
    const google = (user.providerData || []).find((p) => p.providerId === "google.com");
    uid = google ? google.uid : user.uid;
  }
  const prof = await db.collection("users").doc(uid).get();
  if (!prof.exists) throw new Error(`users/${uid} 문서가 없다 — 어드민 계정으로 앱에 한 번 로그인했는지 확인`);
  const name = (prof.get("userName") || "").trim();
  if (!name) throw new Error(`users/${uid}.userName 이 비어 있다`);
  return { uid, name };
}

async function uploadImage(bucket, s) {
  const dest = `${STORAGE_DIR}/${s.id}.jpg`;
  const file = bucket.file(dest);
  let token = null;
  const [exists] = await file.exists();
  if (exists) {
    const [md] = await file.getMetadata();
    token = (md.metadata && md.metadata.firebaseStorageDownloadTokens || "").split(",")[0] || null;
  }
  token = token || crypto.randomUUID();
  await bucket.upload(s.image, {
    destination: dest,
    metadata: {
      contentType: "image/jpeg",
      cacheControl: "public, max-age=31536000",
      metadata: { firebaseStorageDownloadTokens: token, seedId: s.id },
    },
  });
  return `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/${encodeURIComponent(dest)}?alt=media&token=${token}`;
}

/** 업로드 전 사람 눈으로 훑어보는 페이지(사진·제목·본문·출처·지도 링크). build/ 는 gitignore. */
function writeReview(stars) {
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const cards = stars.map((s, i) => `
<article>
  <img src="img/${esc(s.id)}.jpg" alt="" loading="lazy">
  <div class="meta">${i + 1}. ${esc(s.theme)} · ${esc(s.cc)} · <b>${esc(s.lang)}</b> · star ${s.starType}/${s.starColor} ·
    <a href="https://www.openstreetmap.org/?mlat=${s.lat}&mlon=${s.lng}#map=15/${s.lat}/${s.lng}" target="_blank">${s.lat.toFixed(4)}, ${s.lng.toFixed(4)}</a> ·
    ${new Date(s.createdAt).toISOString().slice(0, 10)}</div>
  <h2 dir="auto">${esc(s.title)}</h2>
  <p dir="auto">${esc(fullContent(s))}</p>
</article>`).join("\n");
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Stary 큐레이션 별 미리보기</title>
<style>
:root{color-scheme:dark}body{margin:0;background:#0b0d14;color:#e8ebf5;font:15px/1.65 system-ui,sans-serif}
main{max-width:760px;margin:0 auto;padding:16px}h1{font-size:20px}article{background:#151a28;border-radius:14px;margin:18px 0;overflow:hidden}
img{width:100%;aspect-ratio:4/3;object-fit:cover;display:block}.meta{font-size:12px;color:#9aa3bf;padding:10px 16px 0}
h2{font-size:18px;margin:6px 16px}p{white-space:pre-wrap;margin:0 16px 16px}a{color:#9fb3e8}
</style><main><h1>큐레이션 별 ${stars.length}개 — 업로드 전 미리보기</h1>${cards}</main>`;
  const out = path.join(BUILD, "review.html");
  fs.writeFileSync(out, html, "utf8");
  console.log("미리보기:", out);
}

function summarize(stars) {
  const by = (k) => stars.reduce((m, s) => ((m[s[k]] = (m[s[k]] || 0) + 1), m), {});
  console.log(`별 ${stars.length}개 · 나라 ${Object.keys(by("cc")).length}곳 · 언어 ${Object.keys(by("lang")).length}개`);
  console.log("주제:", JSON.stringify(by("theme")));
  console.log("언어:", JSON.stringify(by("lang")));
}

async function main() {
  const cmd = process.argv[2];
  if (!["review", "plan", "upload", "remove"].includes(cmd)) {
    console.log("사용법: node seed.js review | plan | upload | remove");
    process.exit(1);
  }
  const stars = loadStars();
  if (cmd === "review") {
    writeReview(stars);
    return;
  }
  if (cmd !== "remove") {
    summarize(stars);
    const problems = validate(stars);
    if (problems.length) {
      console.log(`\n✗ 문제 ${problems.length}건:\n  ` + problems.join("\n  "));
      if (cmd === "upload") process.exit(2);
    } else {
      console.log("✓ 길이·좌표·사진·출처 검증 통과");
    }
  }

  console.log("인증:", ensureCredentials(), "/ 프로젝트:", PROJECT_ID, "/ DB:", DATABASE_ID);
  const fb = firebase();
  const admin = await resolveAdmin(fb);
  console.log(`어드민: ${admin.name} (${admin.uid.slice(0, 6)}…)`);
  const diaries = fb.db.collection("diaries");

  if (cmd === "plan") {
    const existing = await diaries.where("userId", "==", admin.uid).get();
    const seeded = existing.docs.filter((d) => d.id.startsWith(DOC_PREFIX)).length;
    console.log(`어드민 기존 별 ${existing.size}개(그중 시드 ${seeded}개) — upload 시 시드는 갱신, 나머지는 그대로`);
    const s = stars[0];
    console.log(`\n예시 ${s.docId}: [${s.lang}] ${s.title} @ ${s.lat},${s.lng} · ${new Date(s.createdAt).toISOString()}`);
    console.log(fullContent(s).slice(0, 300) + "…");
    return;
  }

  if (cmd === "remove") {
    const snap = await diaries.where("userId", "==", admin.uid).get();
    const targets = snap.docs.filter((d) => d.id.startsWith(DOC_PREFIX));
    for (const d of targets) await fb.db.recursiveDelete(d.ref);
    const [files] = await fb.bucket.getFiles({ prefix: STORAGE_DIR + "/" });
    for (const f of files) await f.delete();
    console.log(`삭제: 다이어리 ${targets.length}개, 사진 ${files.length}장`);
    return;
  }

  // upload
  let created = 0, updated = 0;
  for (const s of stars) {
    const imageUrl = await uploadImage(fb.bucket, s);
    const ref = diaries.doc(s.docId);
    const snap = await ref.get();
    const body = {
      id: s.docId,
      userId: admin.uid,
      userName: admin.name,
      anonymous: false,
      title: s.title,
      content: fullContent(s),
      imageUrl,
      videoUrl: "",
      latitude: s.lat,
      longitude: s.lng,
      starType: s.starType,
      starColor: s.starColor,
      seedId: s.id,
      seedLang: s.lang,
    };
    if (snap.exists) {
      // 재실행: 글/사진/좌표만 갱신(좋아요·댓글·조회수·작성 시각·공개 범위는 건드리지 않는다).
      await ref.update(body);
      updated++;
    } else {
      // ⚠️ private 으로 만들고 바로 public — onCreate 푸시 함수들이 건너뛰게(파일 상단 설명).
      await ref.set({ ...body, createdAt: s.createdAt, likeCount: 0, commentCount: 0, viewCount: 0, visibilityType: "private" });
      await ref.update({ visibilityType: "public" });
      created++;
    }
    process.stdout.write(`\r${created + updated}/${stars.length} ${s.id}            `);
  }
  console.log(`\n완료: 새로 ${created}개, 갱신 ${updated}개`);
}

main().catch((e) => {
  console.error("\n✗", e.message || e);
  process.exit(1);
});
