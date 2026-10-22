/**
 * 별빛 공방 뽑기 풀·설정 올리기(2026-10-22) — items.js 가 원본.
 *
 *   node tools/starlight/seed.js plan      # 읽기만: DB 와 비교해 새로 생길/바뀔/DB 에만 있는 아이템 요약
 *   node tools/starlight/seed.js upload    # starlightItems/{id} 덮어쓰기 + starlightConfig/main + starlightHints/{업적 id}
 *
 * - firebase-admin 은 tools/seed/node_modules 의 것을 쓴다(cd tools/seed && npm install 1회).
 * - 인증: GOOGLE_APPLICATION_CREDENTIALS 가 있으면 그것, 없으면 이 PC 의 firebase CLI 로그인으로 임시 ADC(tools/seed/seed.js 와 같은 방식).
 * - DB 에만 있고 items.js 에 없는 아이템은 **지우지 않는다**(이미 가진 사람이 있다) — 빼려면 items.js 에서 active:false.
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { items, config } = require("./items");
const hints = require("./hints");

const PROJECT_ID = "momentdiary-f26c8"; // ⚠️ 포크 전용 프로젝트(원본 momentdiary-52b78 금지)
const DATABASE_ID = "stary-db";
const MODULES = path.join(__dirname, "..", "seed", "node_modules");

let tempAdc = null;
function ensureCredentials() {
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) return;
  const cfg = path.join(os.homedir(), ".config", "configstore", "firebase-tools.json");
  if (!fs.existsSync(cfg)) throw new Error("firebase CLI 로그인 정보가 없다 — `firebase login`");
  const tokens = JSON.parse(fs.readFileSync(cfg, "utf8")).tokens || {};
  if (!tokens.refresh_token) throw new Error("firebase CLI refresh_token 없음 — `firebase login --reauth`");
  // firebase-tools 의 공개 OAuth 클라이언트 — CLI 와 같은 자격.
  tempAdc = path.join(os.tmpdir(), `stary-starlight-adc-${process.pid}.json`);
  fs.writeFileSync(tempAdc, JSON.stringify({
    type: "authorized_user",
    client_id: "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com",
    client_secret: "j9iVZfS8kkCEFUPaAeJV0sAi",
    refresh_token: tokens.refresh_token,
  }), { mode: 0o600 });
  process.env.GOOGLE_APPLICATION_CREDENTIALS = tempAdc;
  process.env.GOOGLE_CLOUD_PROJECT = PROJECT_ID;
}
const cleanup = () => { if (tempAdc && fs.existsSync(tempAdc)) fs.unlinkSync(tempAdc); };
process.on("exit", cleanup);
process.on("SIGINT", () => { cleanup(); process.exit(130); });

/** 올리기 전 검증 — 번호 중복·범위, 필수 문구, 경로 명령. */
function validate() {
  const errors = [];
  const ids = new Set();
  const types = new Map();
  const colors = new Map();
  for (const it of items) {
    if (ids.has(it.id)) errors.push(`id 중복: ${it.id}`);
    ids.add(it.id);
    for (const k of ["ko", "en", "ja"]) {
      if (!it.name?.[k]) errors.push(`${it.id}: name.${k} 없음`);
      if (!it.desc?.[k]) errors.push(`${it.id}: desc.${k} 없음`);
    }
    const p = it.payload || {};
    const svgs = [p.path, p.icon].filter(Boolean);
    for (const s of svgs) if (/[^MLCQZ0-9 .\-]/.test(s)) errors.push(`${it.id}: 허용되지 않은 경로 명령/문자`);
    if (it.kind === "shape") {
      if (!(p.type >= 21)) errors.push(`${it.id}: type 은 21 이상`);
      if (types.has(p.type)) errors.push(`${it.id}: type ${p.type} 이 ${types.get(p.type)} 와 겹침`);
      types.set(p.type, it.id);
    }
    if (it.kind === "color") {
      if (!(p.index >= 21)) errors.push(`${it.id}: index 는 21 이상`);
      if (colors.has(p.index)) errors.push(`${it.id}: index ${p.index} 가 ${colors.get(p.index)} 와 겹침`);
      colors.set(p.index, it.id);
      if (!Array.isArray(p.colors) || p.colors.length < 1 || p.colors.length > 3) errors.push(`${it.id}: colors 1~3개`);
    }
  }
  if (errors.length) {
    console.error(errors.join("\n"));
    process.exit(1);
  }
}

async function main() {
  const cmd = process.argv[2];
  if (!["plan", "upload"].includes(cmd)) {
    console.log("사용법: node tools/starlight/seed.js plan|upload");
    return;
  }
  validate();
  ensureCredentials();
  const { initializeApp, applicationDefault } = require(path.join(MODULES, "firebase-admin/lib/app"));
  const { getFirestore } = require(path.join(MODULES, "firebase-admin/lib/firestore"));
  initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
  const db = getFirestore(DATABASE_ID);

  const existing = new Map((await db.collection("starlightItems").get()).docs.map((d) => [d.id, d.data()]));
  const fresh = items.filter((it) => !existing.has(it.id));
  const canon = (o) => JSON.stringify(o, Object.keys(o).sort());
  const changed = items.filter((it) => {
    if (!existing.has(it.id)) return false;
    const { id, ...data } = it;
    return canon(existing.get(id)) !== canon(data);
  });
  const orphan = [...existing.keys()].filter((id) => !items.some((it) => it.id === id));
  const byRarity = {};
  for (const it of items.filter((x) => x.active)) byRarity[it.rarity] = (byRarity[it.rarity] || 0) + 1;
  console.log(`아이템 ${items.length}개 (활성 등급별 ${JSON.stringify(byRarity)})`);
  console.log(`  새로 생김 ${fresh.length}: ${fresh.map((x) => x.id).join(", ")}`);
  console.log(`  바뀜 ${changed.length}: ${changed.map((x) => x.id).join(", ")}`);
  if (orphan.length) console.log(`  DB 에만 있음(그대로 둠) ${orphan.length}: ${orphan.join(", ")}`);
  if (cmd === "plan") return;

  const batch = db.batch();
  for (const it of items) {
    const { id, ...data } = it;
    batch.set(db.collection("starlightItems").doc(id), data);
  }
  // hintTargets: 단서가 있는 히든 업적 — 앱이 망원경 버튼을 이 업적에만 띄운다(단서 문구 자체는 서버만 읽는다).
  batch.set(db.collection("starlightConfig").doc("main"), { ...config, hintTargets: Object.keys(hints).sort() });
  for (const [ach, levels] of Object.entries(hints)) batch.set(db.collection("starlightHints").doc(ach), levels);
  await batch.commit();
  console.log(`올림: starlightItems ${items.length}개 + starlightConfig/main + starlightHints ${Object.keys(hints).length}개`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
