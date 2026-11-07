// Storage 규칙 테스트
const { describe, it, before, after, beforeEach } = require("node:test");
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { ref, uploadBytes, getBytes, deleteObject } = require("firebase/storage");
const H = require("./helpers");
const { USERS: U } = H;

const bytes = (n = 16) => new Uint8Array(n).fill(1);
const ok = (p) => assertSucceeds(p);
const no = (p) => assertFails(p);

describe("storage", () => {
  let t;
  before(async () => { t = await H.makeEnv("stage1"); });
  after(async () => { await t.env.cleanup(); });
  beforeEach(async () => { await t.env.clearStorage(); });
  const st = (k) => t.as(k).storage();
  const seedFile = (path, contentType) => t.env.withSecurityRulesDisabled(async (ctx) => { await uploadBytes(ref(ctx.storage(), path), bytes(), { contentType }); });

  it("로그인(google · apple · password)은 새 이미지/영상을 올린다 (앱이 올리는 형식)", async () => {
    await ok(uploadBytes(ref(st("A"), "diary_images/u1.jpg"), bytes(), { contentType: "image/jpeg" }));
    await ok(uploadBytes(ref(st("C"), "diary_images/u2.gif"), bytes(), { contentType: "image/gif" }));
    await ok(uploadBytes(ref(st("D"), "diary_images/u3.jpg"), bytes(), { contentType: "image/png" }));
    await ok(uploadBytes(ref(st("A"), "diary_images/u4.jpg"), bytes(), { contentType: "image/webp" }));
    await ok(uploadBytes(ref(st("A"), "diary_images/u5.jpg"), bytes(), { contentType: "image/heic" }));
    await ok(uploadBytes(ref(st("A"), "diary_videos/v1.mp4"), bytes(), { contentType: "video/mp4" }));
    await ok(uploadBytes(ref(st("C"), "diary_videos/v2.mov"), bytes(), { contentType: "video/quicktime" }));
  });
  it("익명 · custom · 비로그인은 업로드/다운로드 모두 거부", async () => {
    await seedFile("diary_images/exist.jpg", "image/jpeg");
    for (const k of ["X", "Y"]) {
      await no(uploadBytes(ref(st(k), "diary_images/z.jpg"), bytes(), { contentType: "image/jpeg" }));
      await no(getBytes(ref(st(k), "diary_images/exist.jpg")));
    }
    await no(uploadBytes(ref(t.anon().storage(), "diary_images/z.jpg"), bytes(), { contentType: "image/jpeg" }));
    await no(getBytes(ref(t.anon().storage(), "diary_images/exist.jpg")));
    await ok(getBytes(ref(st("B"), "diary_images/exist.jpg")));
  });
  it("거부: SVG · HTML · 영상 타입을 이미지 경로에 · 이미지 타입을 영상 경로에 · 용량 초과", async () => {
    await no(uploadBytes(ref(st("A"), "diary_images/s.svg"), bytes(), { contentType: "image/svg+xml" }));
    await no(uploadBytes(ref(st("A"), "diary_images/h.jpg"), bytes(), { contentType: "text/html" }));
    await no(uploadBytes(ref(st("A"), "diary_images/v.jpg"), bytes(), { contentType: "video/mp4" }));
    await no(uploadBytes(ref(st("A"), "diary_videos/i.mp4"), bytes(), { contentType: "image/jpeg" }));
    await no(uploadBytes(ref(st("A"), "diary_videos/x.mp4"), bytes(), { contentType: "application/octet-stream" }));
    await no(uploadBytes(ref(st("A"), "diary_images/big.jpg"), bytes(10 * 1024 * 1024 + 1), { contentType: "image/jpeg" }));
    await no(uploadBytes(ref(st("A"), "diary_videos/big.mp4"), bytes(20 * 1024 * 1024 + 1), { contentType: "video/mp4" }));
  });
  it("거부: 이미 있는 diary 파일 덮어쓰기 · 삭제", async () => {
    await ok(uploadBytes(ref(st("A"), "diary_images/mine.jpg"), bytes(), { contentType: "image/jpeg" }));
    await ok(uploadBytes(ref(st("A"), "diary_videos/mine.mp4"), bytes(), { contentType: "video/mp4" }));
    await no(uploadBytes(ref(st("A"), "diary_images/mine.jpg"), bytes(32), { contentType: "image/jpeg" }));
    await no(uploadBytes(ref(st("B"), "diary_images/mine.jpg"), bytes(32), { contentType: "image/jpeg" }));
    await no(uploadBytes(ref(st("A"), "diary_videos/mine.mp4"), bytes(32), { contentType: "video/mp4" }));
    await no(deleteObject(ref(st("A"), "diary_images/mine.jpg")));
    await no(deleteObject(ref(st("A"), "diary_videos/mine.mp4")));
  });
  it("프로필: 본인 경로({sub}_{시각}.jpg · {uid}.jpg · apple uid)만 쓰기/삭제", async () => {
    await ok(uploadBytes(ref(st("A"), `profile_images/${U.A.app}_1700000000000.jpg`), bytes(), { contentType: "image/jpeg" }));
    await ok(uploadBytes(ref(st("A"), `profile_images/${U.A.app}.jpg`), bytes(), { contentType: "image/jpeg" }));
    await ok(uploadBytes(ref(st("C"), `profile_images/${U.C.app}_1700000000001.jpg`), bytes(), { contentType: "image/jpeg" }));
    await ok(deleteObject(ref(st("A"), `profile_images/${U.A.app}_1700000000000.jpg`)));
    await no(uploadBytes(ref(st("B"), `profile_images/${U.A.app}_1700000000002.jpg`), bytes(), { contentType: "image/jpeg" }));
    await no(uploadBytes(ref(st("A"), `profile_images/${U.A.app}_1700000000003.png`), bytes(), { contentType: "image/png" }));
    await no(uploadBytes(ref(st("X"), `profile_images/${U.X.app}_1700000000000.jpg`), bytes(), { contentType: "image/jpeg" }));
    await seedFile(`profile_images/${U.A.app}_1.jpg`, "image/jpeg");
    await no(deleteObject(ref(st("B"), `profile_images/${U.A.app}_1.jpg`)));
  });
  it("그 밖의 경로는 모두 거부", async () => {
    await no(uploadBytes(ref(st("A"), "other/x.jpg"), bytes(), { contentType: "image/jpeg" }));
    await no(getBytes(ref(st("A"), "other/x.jpg")));
  });
});
