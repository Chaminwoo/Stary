// 규칙 테스트 공통 도우미 — 에뮬레이터(demo-stary)만 쓴다. 실프로젝트에 접근하지 않는다.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");

const ROOT = path.join(__dirname, "..", "..", "..");
const RULES = {
  stage1: path.join(ROOT, "firestore.rules"),
  stage2: path.join(ROOT, "firestore.stage2.rules"),
  storage: path.join(ROOT, "storage.rules"),
};

// 앱 사용자: Google = appUserId 가 sub(identities), Apple · 이메일 = appUserId 가 FirebaseAuth uid.
const USERS = {
  A: { uid: "authA", app: "1111111111", provider: "google.com", google: true },
  B: { uid: "authB", app: "2222222222", provider: "google.com", google: true },
  C: { uid: "appleC", app: "appleC", provider: "apple.com" },
  D: { uid: "pwD", app: "pwD", provider: "password" },
  X: { uid: "anonX", app: "anonX", provider: "anonymous" },
  Y: { uid: "customY", app: "customY", provider: "custom" },
};

function tokenOf(u) {
  const identities = u.google ? { "google.com": [u.app] } : {};
  return { firebase: { sign_in_provider: u.provider, identities } };
}

async function makeEnv(stage) {
  const env = await initializeTestEnvironment({
    projectId: "demo-stary",
    firestore: { rules: fs.readFileSync(RULES[stage], "utf8") },
    storage: { rules: fs.readFileSync(RULES.storage, "utf8") },
  });
  const as = (key) => env.authenticatedContext(USERS[key].uid, tokenOf(USERS[key]));
  return { env, as, anon: () => env.unauthenticatedContext() };
}

const now = () => Date.now();

// ── 실제 앱이 쓰는 형태 그대로의 페이로드 ─────────────────────────────────────────
// Android: Diary data class 를 통째로 set (id · anonymous(Boolean) · translations=null 포함, 항상 모든 필드).
const androidDiary = (app, over = {}) => ({
  id: over.id ?? "d1", userId: app, userName: "별이", anonymous: false, title: "제목", content: "본문",
  imageUrl: "", videoUrl: "", latitude: 37.5665, longitude: 126.978, createdAt: now(),
  likeCount: 0, commentCount: 0, viewCount: 0, starType: 0, starColor: 0, starPath: "", starColors: [],
  visibilityType: "public", ring: "", translations: null, ...over,
});
// iOS: Diary.encode — id 없음, anonymous 는 0/1, starPath · starColors · ring 은 비면 생략.
const iosDiary = (app, over = {}) => ({
  userId: app, userName: "별이", anonymous: 0, title: "제목", content: "본문", imageUrl: "", videoUrl: "",
  latitude: 37.5665, longitude: 126.978, createdAt: now(), likeCount: 0, commentCount: 0, viewCount: 0,
  starType: 1, starColor: 2, visibilityType: "public", ...over,
});
// 구버전(공개 범위 · 고리 · 정의 경로 필드 이전).
const legacyDiary = (app) => ({
  userId: app, userName: "옛별", anonymous: false, title: "옛", content: "글", imageUrl: "", videoUrl: "",
  latitude: 10, longitude: 20, createdAt: now() - 86400000, likeCount: 0, commentCount: 0, viewCount: 0,
  starType: 0, starColor: 0,
});

const IMG = "https://firebasestorage.googleapis.com/v0/b/momentdiary-f26c8.firebasestorage.app/o/diary_images%2F3f1c2a9e-0b7d-4c1e-9a55-1234567890ab.jpg?alt=media&token=abc-123";
const VID = "https://firebasestorage.googleapis.com/v0/b/momentdiary-f26c8.firebasestorage.app/o/diary_videos%2F3f1c2a9e-0b7d-4c1e-9a55-1234567890ab.mp4?alt=media&token=abc-123";

const notif = (type, actorApp, ownerApp, over = {}) => ({
  id: "n1", type, diaryId: "d1", diaryTitle: "제목", diaryOwnerId: ownerApp, actorId: actorApp, actorName: "행위자",
  content: "", createdAt: now(), read: false, ...over,
});

module.exports = { USERS, makeEnv, now, androidDiary, iosDiary, legacyDiary, notif, IMG, VID, RULES };
