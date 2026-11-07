// Firestore 규칙 테스트 — Stage 1 · Stage 2 두 파일 모두에 공통 스위트를 돌리고, 단계별 전용 스위트를 더한다.
const { describe, it, before, after, beforeEach } = require("node:test");
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc, setDoc, getDoc, updateDoc, deleteDoc, addDoc, collection, getDocs, query, where, orderBy, limit,
  writeBatch, runTransaction, increment, serverTimestamp, deleteField,
} = require("firebase/firestore");
const H = require("./helpers");
const { USERS: U, androidDiary, iosDiary, legacyDiary, notif, now, IMG, VID } = H;

const seed = (env, fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
const ok = (p) => assertSucceeds(p);
const no = (p) => assertFails(p);
const chatIdOf = (a, b) => [a, b].sort().join("_");

function commonSuite(stage) {
  describe(`공통 (${stage})`, () => {
    let t;
    before(async () => { t = await H.makeEnv(stage); });
    after(async () => { await t.env.cleanup(); });
    beforeEach(async () => { await t.env.clearFirestore(); });
    const db = (k) => t.as(k).firestore();

    describe("로그인 판정", () => {
      it("익명(anonymous provider) · custom · 비로그인은 읽기/쓰기 모두 거부", async () => {
        await seed(t.env, (s) => setDoc(doc(s, "diaries/d1"), androidDiary(U.A.app)));
        await seed(t.env, (s) => setDoc(doc(s, "users/" + U.A.app), { userName: "에이" }));
        for (const k of ["X", "Y"]) {
          const d = db(k);
          await no(getDoc(doc(d, "diaries/d1")));
          await no(getDocs(query(collection(d, "diaries"), where("visibilityType", "==", "public"))));
          await no(setDoc(doc(d, "diaries/" + k), androidDiary(U[k].app, { id: k })));
          await no(getDoc(doc(d, "users/" + U.A.app)));
          await no(setDoc(doc(d, "users/" + U[k].app), { userName: "나" }));
          await no(addDoc(collection(d, "feedback"), { uid: U[k].app, message: "m", platform: "ios", createdAt: serverTimestamp() }));
          await no(addDoc(collection(d, "reports"), { reporterId: U[k].app, type: "user", targetId: "x", reason: "spam" }));
          await no(getDoc(doc(d, "starlightItems/x")));
          await no(setDoc(doc(d, "chats/" + chatIdOf(U[k].app, "zzz")), { participants: [U[k].app, "zzz"].sort() }));
        }
        const n = t.anon().firestore();
        await no(getDoc(doc(n, "diaries/d1")));
        await no(getDoc(doc(n, "users/" + U.A.app)));
        await no(setDoc(doc(n, "users/q"), { userName: "q" }));
      });
      it("google · apple · password 는 자기 문서를 쓸 수 있다", async () => {
        for (const k of ["A", "C", "D"]) {
          await ok(setDoc(doc(db(k), "users/" + U[k].app), { userName: "이름", profileImageUrl: "https://lh3.googleusercontent.com/a/x=s96-c", fcmToken: "t", authUid: U[k].uid }, { merge: true }));
          await ok(setDoc(doc(db(k), "diaries/dd" + k), iosDiary(U[k].app)));
        }
      });
      it("남의 users 문서는 쓸 수 없다 · 서버 전용 컬렉션은 접근 불가", async () => {
        await no(setDoc(doc(db("B"), "users/" + U.A.app), { fcmToken: "x" }, { merge: true }));
        await no(setDoc(doc(db("A"), "rateLimits/k"), { n: 1 }));
        await no(getDoc(doc(db("A"), "rateLimits/k")));
        await no(setDoc(doc(db("A"), "abuseLog/k"), { n: 1 }));
        await no(getDoc(doc(db("A"), "abuseLog/k")));
        await no(setDoc(doc(db("A"), "wallets/" + U.A.app), { stardust: 9999 }));
      });
    });

    describe("diaries 생성", () => {
      it("Android 페이로드(전 필드, translations=null) 통과", async () => {
        await ok(setDoc(doc(db("A"), "diaries/d1"), androidDiary(U.A.app)));
      });
      it("iOS 페이로드(id 없음, anonymous 0/1) 통과 · addDocument 도 통과", async () => {
        await ok(setDoc(doc(db("C"), "diaries/i1"), iosDiary(U.C.app)));
        await ok(addDoc(collection(db("D"), "diaries"), iosDiary(U.D.app, { visibilityType: "friends" })));
      });
      it("구버전 필드 구성(공개 범위 · 고리 없음) 통과", async () => {
        await ok(setDoc(doc(db("A"), "diaries/l1"), legacyDiary(U.A.app)));
      });
      it("영상/이미지 URL(이 프로젝트 Storage) · 정의 경로 · 색 3개 · 고리 통과", async () => {
        await ok(setDoc(doc(db("A"), "diaries/m1"), androidDiary(U.A.app, { id: "m1", imageUrl: IMG, starPath: "M0 0L1 1".repeat(300), starColors: ["#FF0000", "#00FF00", "#0000FF"], starType: 77, starColor: 44, ring: "aura_x" })));
        await ok(setDoc(doc(db("A"), "diaries/m2"), androidDiary(U.A.app, { id: "m2", videoUrl: VID })));
      });
      it("과거 createdAt(오프라인 대기 저장)은 통과, +1시간 이내 미래도 통과", async () => {
        await ok(setDoc(doc(db("A"), "diaries/p1"), androidDiary(U.A.app, { id: "p1", createdAt: now() - 3 * 86400000 })));
        await ok(setDoc(doc(db("A"), "diaries/p2"), androidDiary(U.A.app, { id: "p2", createdAt: now() + 30 * 60000 })));
      });
      const bad = {
        "남의 userId": () => ({ userId: U.B.app }),
        "문서 id 와 다른 id 필드": () => ({ id: "other" }),
        "audience 직접 쓰기": () => ({ audience: [U.B.app] }),
        "translations 직접 쓰기": () => ({ translations: { title: { en: "x" } } }),
        "허용 안 된 키": () => ({ isAdmin: true }),
        "likeCount 조작": () => ({ likeCount: 5 }),
        "viewCount 조작": () => ({ viewCount: 1000 }),
        "commentCount 조작": () => ({ commentCount: 1 }),
        "위도 범위 밖": () => ({ latitude: 91 }),
        "경도 범위 밖": () => ({ longitude: -181 }),
        "위도가 문자열": () => ({ latitude: "37" }),
        "createdAt 2시간 뒤": () => ({ createdAt: now() + 2 * 3600000 }),
        "createdAt 0": () => ({ createdAt: 0 }),
        "createdAt 문자열": () => ({ createdAt: "2026" }),
        "visibilityType 알 수 없는 값": () => ({ visibilityType: "everyone" }),
        "visibilityType 숫자": () => ({ visibilityType: 1 }),
        "외부 호스트 imageUrl": () => ({ imageUrl: "https://evil.example.com/x.jpg" }),
        "다른 버킷 imageUrl": () => ({ imageUrl: IMG.replace("momentdiary-f26c8", "other-project") }),
        "프로필 경로 imageUrl": () => ({ imageUrl: IMG.replace("diary_images", "profile_images") }),
        "제목 101자": () => ({ title: "가".repeat(101) }),
        "본문 4001자": () => ({ content: "가".repeat(4001) }),
        "이름 101자": () => ({ userName: "가".repeat(101) }),
        "starPath 4001자": () => ({ starPath: "a".repeat(4001) }),
        "starColors 4개": () => ({ starColors: ["#111111", "#222222", "#333333", "#444444"] }),
        "starColors 긴 문자열": () => ({ starColors: ["#" + "f".repeat(60)] }),
        "starType 음수": () => ({ starType: -1 }),
        "ring 65자": () => ({ ring: "r".repeat(65) }),
        "title 숫자": () => ({ title: 5 }),
      };
      for (const [name, f] of Object.entries(bad)) {
        it(`거부: ${name}`, async () => {
          await no(setDoc(doc(db("A"), "diaries/d1"), androidDiary(U.A.app, f())));
        });
      }
      it("거부: 필수 키(title) 누락", async () => {
        const d = androidDiary(U.A.app); delete d.title;
        await no(setDoc(doc(db("A"), "diaries/d1"), d));
      });
      it("거부: 다른 사람(B)이 A 명의로 생성", async () => {
        await no(setDoc(doc(db("B"), "diaries/d1"), androidDiary(U.A.app)));
      });
    });

    describe("diaries 수정 · 삭제", () => {
      beforeEach(async () => {
        await seed(t.env, async (s) => {
          await setDoc(doc(s, "diaries/d1"), androidDiary(U.A.app, { likeCount: 3, commentCount: 2, viewCount: 9, translations: { sourceLang: "ko", status: "done", title: { ko: "제목", en: "t" }, content: { ko: "본문", en: "c" } }, audience: [U.B.app] }));
        });
      });
      it("Android 수정(title · content · visibilityType 3키 update) 통과", async () => {
        await ok(updateDoc(doc(db("A"), "diaries/d1"), { title: "새 제목", content: "새 본문", visibilityType: "friends" }));
      });
      it("iOS 수정(setData merge, 전체 필드 · 카운터는 현재 이하) 통과", async () => {
        const d = iosDiary(U.A.app, { title: "수정", likeCount: 3, commentCount: 2, viewCount: 9, createdAt: now() - 1000 });
        await ok(setDoc(doc(db("A"), "diaries/d1"), d, { merge: true }));
      });
      it("Android 재시도 set(전체 덮어쓰기, translations=null, audience 없음) 통과", async () => {
        await ok(setDoc(doc(db("A"), "diaries/d1"), androidDiary(U.A.app, { id: "d1" })));
      });
      it("거부: audience 값 변경 · translations 값 변경", async () => {
        await no(updateDoc(doc(db("A"), "diaries/d1"), { audience: [U.B.app, "evil"] }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { "translations.title.en": "위조" }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { translations: { sourceLang: "ko" } }));
      });
      it("거부: 없던 audience / translations 를 새로 넣기", async () => {
        await seed(t.env, (s) => setDoc(doc(s, "diaries/d2"), androidDiary(U.A.app, { id: "d2" })));
        await no(updateDoc(doc(db("A"), "diaries/d2"), { audience: [U.B.app] }));
        await no(updateDoc(doc(db("A"), "diaries/d2"), { translations: { sourceLang: "ko" } }));
      });
      it("audience · translations 삭제는 허용(트리거가 복구)", async () => {
        await ok(updateDoc(doc(db("A"), "diaries/d1"), { audience: deleteField() }));
        await ok(updateDoc(doc(db("A"), "diaries/d1"), { translations: deleteField() }));
      });
      it("거부: userId 변경 · 남의 글 본문 수정 · 소유자가 카운터 직접 올리기", async () => {
        await no(updateDoc(doc(db("A"), "diaries/d1"), { userId: U.B.app }));
        await no(updateDoc(doc(db("B"), "diaries/d1"), { title: "해킹" }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { likeCount: 999 }));
      });
      it("거부: 수정 시 긴 제목 · 외부 URL · 미래 createdAt · 이상한 공개 범위", async () => {
        await no(updateDoc(doc(db("A"), "diaries/d1"), { title: "가".repeat(101) }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { imageUrl: "https://evil.example.com/a.jpg" }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { createdAt: now() + 5 * 3600000 }));
        await no(updateDoc(doc(db("A"), "diaries/d1"), { visibilityType: "x" }));
      });
      it("카운터: 비소유자 +1 · 좋아요 취소 -1 · 조회수 +1 통과 (소유자도 증감 분기 사용)", async () => {
        await ok(updateDoc(doc(db("B"), "diaries/d1"), { likeCount: increment(1) }));
        await ok(updateDoc(doc(db("B"), "diaries/d1"), { likeCount: increment(-1) }));
        await ok(updateDoc(doc(db("B"), "diaries/d1"), { commentCount: increment(1) }));
        await ok(updateDoc(doc(db("B"), "diaries/d1"), { viewCount: increment(1) }));
        await ok(updateDoc(doc(db("A"), "diaries/d1"), { likeCount: increment(1) }));
      });
      it("카운터 거부: +2 · 조회수 -1 · 임의 값 · 다른 필드 동시 수정", async () => {
        await no(updateDoc(doc(db("B"), "diaries/d1"), { likeCount: increment(2) }));
        await no(updateDoc(doc(db("B"), "diaries/d1"), { viewCount: increment(-1) }));
        await no(updateDoc(doc(db("B"), "diaries/d1"), { likeCount: 1000 }));
        await no(updateDoc(doc(db("B"), "diaries/d1"), { likeCount: increment(1), title: "x" }));
      });
      it("삭제: 소유자만", async () => {
        await no(deleteDoc(doc(db("B"), "diaries/d1")));
        await ok(deleteDoc(doc(db("A"), "diaries/d1")));
      });
    });

    describe("comments · likes (좋아요 트랜잭션 · 댓글 배치)", () => {
      beforeEach(async () => { await seed(t.env, (s) => setDoc(doc(s, "diaries/d1"), androidDiary(U.A.app))); });
      it("Android 댓글 배치(댓글 + commentCount + 알림) 통과", async () => {
        const d = db("B"); const b = writeBatch(d);
        b.set(doc(d, "diaries/d1/comments/c1"), { id: "c1", diaryId: "d1", userId: U.B.app, userName: "비", content: "안녕", createdAt: now() });
        b.update(doc(d, "diaries/d1"), { commentCount: increment(1) });
        b.set(doc(d, "notifications/n1"), notif("COMMENT", U.B.app, U.A.app, { content: "안녕" }));
        await ok(b.commit());
      });
      it("iOS 댓글 배치(id 없음) 통과", async () => {
        const d = db("C"); const b = writeBatch(d);
        b.set(doc(d, "diaries/d1/comments/c2"), { diaryId: "d1", userId: U.C.app, userName: "애플", content: "굿", createdAt: now() });
        b.update(doc(d, "diaries/d1"), { commentCount: increment(1) });
        b.set(doc(d, "notifications/n2"), { type: "COMMENT", diaryId: "d1", diaryTitle: "제목", diaryOwnerId: U.A.app, actorId: U.C.app, actorName: "애플", content: "굿", createdAt: now(), read: false });
        await ok(b.commit());
      });
      it("댓글 거부: 남 명의 · 빈 내용 · 1001자 · 키 추가 · 미래 시각 · 수정", async () => {
        const d = db("B");
        const base = { diaryId: "d1", userId: U.B.app, userName: "비", content: "x", createdAt: now() };
        await no(setDoc(doc(d, "diaries/d1/comments/e1"), { ...base, userId: U.A.app }));
        await no(setDoc(doc(d, "diaries/d1/comments/e2"), { ...base, content: "" }));
        await no(setDoc(doc(d, "diaries/d1/comments/e3"), { ...base, content: "가".repeat(1001) }));
        await no(setDoc(doc(d, "diaries/d1/comments/e4"), { ...base, extra: 1 }));
        await no(setDoc(doc(d, "diaries/d1/comments/e5"), { ...base, createdAt: now() + 7200000 }));
        await ok(setDoc(doc(d, "diaries/d1/comments/e6"), base));
        await no(updateDoc(doc(d, "diaries/d1/comments/e6"), { content: "수정" }));
      });
      it("댓글 삭제: 작성자 · 글주인 통과, 제3자 거부", async () => {
        await seed(t.env, async (s) => {
          await setDoc(doc(s, "diaries/d1/comments/c9"), { userId: U.B.app, content: "x", createdAt: now() });
          await setDoc(doc(s, "diaries/d1/comments/c10"), { userId: U.B.app, content: "x", createdAt: now() });
        });
        await no(deleteDoc(doc(db("C"), "diaries/d1/comments/c9")));
        await ok(deleteDoc(doc(db("A"), "diaries/d1/comments/c9")));
        const dB = db("B");
        const b = writeBatch(dB);
        b.delete(doc(dB, "diaries/d1/comments/c10")); b.update(doc(dB, "diaries/d1"), { commentCount: increment(-1) });
        await ok(b.commit());
      });
      it("좋아요 트랜잭션(Android/iOS 동일 형태) 통과 · 취소 통과", async () => {
        const d = db("B");
        await ok(runTransaction(d, async (tx) => {
          const likeRef = doc(d, "diaries/d1/likes/" + U.B.app);
          const s = await tx.get(likeRef);
          if (!s.exists()) {
            tx.set(likeRef, { userId: U.B.app, userName: "비", createdAt: now() });
            tx.update(doc(d, "diaries/d1"), { likeCount: increment(1) });
            tx.set(doc(d, "notifications/nl"), notif("LIKE", U.B.app, U.A.app));
          }
        }));
        await ok(runTransaction(d, async (tx) => {
          const likeRef = doc(d, "diaries/d1/likes/" + U.B.app);
          await tx.get(likeRef);
          tx.delete(likeRef); tx.update(doc(d, "diaries/d1"), { likeCount: increment(-1) });
        }));
      });
      it("좋아요 거부: 남의 id · 키 추가 · 남이 삭제", async () => {
        const d = db("B");
        await no(setDoc(doc(d, "diaries/d1/likes/" + U.A.app), { userId: U.A.app, userName: "에이", createdAt: now() }));
        await no(setDoc(doc(d, "diaries/d1/likes/" + U.B.app), { userId: U.B.app, userName: "비", createdAt: now(), x: 1 }));
        await seed(t.env, (s) => setDoc(doc(s, "diaries/d1/likes/" + U.B.app), { userId: U.B.app, createdAt: now() }));
        await no(deleteDoc(doc(db("C"), "diaries/d1/likes/" + U.B.app)));
      });
    });

    describe("notifications", () => {
      it("네 종류 통과(Android: id 포함 / iOS: 일부 필드 생략)", async () => {
        for (const ty of ["LIKE", "COMMENT", "FRIEND_POST", "FRIEND_REQUEST"]) {
          await ok(setDoc(doc(db("B"), "notifications/n_" + ty), notif(ty, U.B.app, U.A.app, { id: "n_" + ty })));
        }
        await ok(setDoc(doc(db("B"), "notifications/fr"), { id: "fr", type: "FRIEND_REQUEST", diaryId: "", diaryTitle: "", diaryOwnerId: U.A.app, actorId: U.B.app, actorName: "비", content: "", createdAt: now(), read: false }));
      });
      it("거부: 알 수 없는 type · 남 명의 · 긴 content · 읽음 상태로 생성 · 키 추가 · 미래 시각", async () => {
        const d = db("B");
        await no(setDoc(doc(d, "notifications/a1"), notif("FIRST_STAR", U.B.app, U.A.app)));
        await no(setDoc(doc(d, "notifications/a2"), notif("ADMIN", U.B.app, U.A.app)));
        await no(setDoc(doc(d, "notifications/a3"), notif("LIKE", U.A.app, U.C.app)));
        await no(setDoc(doc(d, "notifications/a4"), notif("COMMENT", U.B.app, U.A.app, { content: "가".repeat(1001) })));
        await no(setDoc(doc(d, "notifications/a5"), notif("LIKE", U.B.app, U.A.app, { read: true })));
        await no(setDoc(doc(d, "notifications/a6"), notif("LIKE", U.B.app, U.A.app, { extra: 1 })));
        await no(setDoc(doc(d, "notifications/a7"), notif("LIKE", U.B.app, U.A.app, { createdAt: now() + 7200000 })));
      });
      it("수신자: 목록 쿼리 · 읽음 처리(read 만) · 삭제 / 남은 불가", async () => {
        await seed(t.env, (s) => setDoc(doc(s, "notifications/n1"), notif("LIKE", U.B.app, U.A.app)));
        await ok(getDocs(query(collection(db("A"), "notifications"), where("diaryOwnerId", "==", U.A.app), where("read", "==", false))));
        await no(getDocs(query(collection(db("B"), "notifications"), where("diaryOwnerId", "==", U.A.app))));
        await no(getDoc(doc(db("B"), "notifications/n1")));
        await ok(updateDoc(doc(db("A"), "notifications/n1"), { read: true }));
        await no(updateDoc(doc(db("A"), "notifications/n1"), { content: "변조" }));
        await no(updateDoc(doc(db("B"), "notifications/n1"), { read: true }));
        await ok(deleteDoc(doc(db("A"), "notifications/n1")));
      });
    });

    describe("chats / messages", () => {
      const cid = chatIdOf(U.A.app, U.B.app);
      it("첫 메시지 배치(방 생성 merge + 메시지) — Android 형태 통과", async () => {
        const d = db("A"); const b = writeBatch(d); const n = now();
        b.set(doc(d, "chats/" + cid), { participants: cid.split("_"), lastMessage: "안녕", lastSenderId: U.A.app, lastSenderName: "에이", updatedAt: n, lastReadAt: { [U.A.app]: n } }, { merge: true });
        b.set(doc(d, `chats/${cid}/messages/m1`), { id: "m1", senderId: U.A.app, senderName: "에이", text: "안녕", createdAt: n });
        await ok(b.commit());
      });
      it("첫 메시지 배치 — iOS 형태(메시지에 id 없음) 통과, 두 번째 메시지(방 갱신)도 통과", async () => {
        const cid2 = chatIdOf(U.C.app, U.A.app);
        for (let i = 0; i < 2; i++) {
          const d = db("C"); const b = writeBatch(d); const n = now();
          b.set(doc(d, "chats/" + cid2), { participants: cid2.split("_"), lastMessage: "hi" + i, lastSenderId: U.C.app, lastSenderName: "애플", updatedAt: n, lastReadAt: { [U.C.app]: n } }, { merge: true });
          b.set(doc(d, `chats/${cid2}/messages/${"m" + i}`), { senderId: U.C.app, senderName: "애플", text: "hi" + i, createdAt: n });
          await ok(b.commit());
        }
      });
      it("목록 쿼리(participants array-contains 나) 통과 · 남의 방 쿼리 거부 · 읽음 갱신 통과", async () => {
        await seed(t.env, async (s) => { await setDoc(doc(s, "chats/" + cid), { participants: cid.split("_"), lastMessage: "x", updatedAt: now(), lastReadAt: {} }); });
        await ok(getDocs(query(collection(db("A"), "chats"), where("participants", "array-contains", U.A.app))));
        await no(getDocs(query(collection(db("C"), "chats"), where("participants", "array-contains", U.A.app))));
        await ok(updateDoc(doc(db("A"), "chats/" + cid), { ["lastReadAt." + U.A.app]: now() }));
        await no(getDoc(doc(db("C"), "chats/" + cid)));
      });
      it("거부: 내 id 가 없는 chatId · participants 불일치 · 3인 · participants 변경 · 남 명의 lastSenderId", async () => {
        const other = chatIdOf(U.B.app, U.C.app);
        await no(setDoc(doc(db("A"), "chats/" + other), { participants: other.split("_") }));
        await no(setDoc(doc(db("A"), "chats/" + cid), { participants: [U.A.app, "evil"] }));
        await no(setDoc(doc(db("A"), "chats/" + cid), { participants: [U.A.app, U.B.app, "evil"] }));
        await no(setDoc(doc(db("A"), "chats/" + cid), { participants: cid.split("_"), lastSenderId: U.B.app }));
        await seed(t.env, async (s) => { await setDoc(doc(s, "chats/" + cid), { participants: cid.split("_") }); });
        await no(updateDoc(doc(db("A"), "chats/" + cid), { participants: [U.A.app, "evil"] }));
        await no(updateDoc(doc(db("A"), "chats/" + cid), { lastMessage: "가".repeat(1001) }));
      });
      it("메시지 거부: 남 명의 senderId · 빈/1001자 · 키 추가 · 미래 시각 · 참여자 아님 · 수정", async () => {
        const base = { senderId: U.A.app, senderName: "에이", text: "x", createdAt: now() };
        const ref = (k, id) => doc(db(k), `chats/${cid}/messages/${id}`);
        await no(setDoc(ref("A", "a1"), { ...base, senderId: U.B.app }));
        await no(setDoc(ref("A", "a2"), { ...base, text: "" }));
        await no(setDoc(ref("A", "a3"), { ...base, text: "가".repeat(1001) }));
        await no(setDoc(ref("A", "a4"), { ...base, extra: 1 }));
        await no(setDoc(ref("A", "a5"), { ...base, createdAt: now() + 7200000 }));
        await no(setDoc(ref("C", "a6"), { ...base, senderId: U.C.app }));
        await ok(setDoc(ref("A", "a7"), base));
        await no(updateDoc(ref("A", "a7"), { text: "수정" }));
        await ok(getDoc(ref("B", "a7")));
        await no(getDoc(ref("C", "a7")));
      });
      it("메시지 삭제: 보낸 사람 + 1분 이내만", async () => {
        await seed(t.env, async (s) => {
          await setDoc(doc(s, `chats/${cid}/messages/fresh`), { senderId: U.A.app, text: "x", createdAt: now() });
          await setDoc(doc(s, `chats/${cid}/messages/old`), { senderId: U.A.app, text: "x", createdAt: now() - 120000 });
        });
        await no(deleteDoc(doc(db("B"), `chats/${cid}/messages/fresh`)));
        await ok(deleteDoc(doc(db("A"), `chats/${cid}/messages/fresh`)));
        await no(deleteDoc(doc(db("A"), `chats/${cid}/messages/old`)));
      });
    });

    describe("친구 요청 · 수락 · 삭제", () => {
      const req = (from, to, over = {}) => ({ id: from + "_" + to, fromId: from, fromName: "보냄", fromPhotoUrl: "", toId: to, toName: "받음", createdAt: now(), ...over });
      it("요청 생성(Android: id 포함 / iOS: id 없음) 통과", async () => {
        await ok(setDoc(doc(db("A"), `friendRequests/${U.A.app}_${U.B.app}`), req(U.A.app, U.B.app)));
        const ios = req(U.C.app, U.A.app); delete ios.id;
        await ok(setDoc(doc(db("C"), `friendRequests/${U.C.app}_${U.A.app}`), ios));
      });
      it("거부: 남 명의 · 자기 자신 · 키 추가", async () => {
        await no(setDoc(doc(db("A"), `friendRequests/${U.B.app}_${U.C.app}`), req(U.B.app, U.C.app)));
        await no(setDoc(doc(db("A"), `friendRequests/${U.A.app}_${U.A.app}`), req(U.A.app, U.A.app)));
        await no(setDoc(doc(db("A"), `friendRequests/${U.A.app}_${U.B.app}`), req(U.A.app, U.B.app, { x: 1 })));
      });
      it("읽기: 당사자 쿼리만 · 제3자 거부", async () => {
        await seed(t.env, (s) => setDoc(doc(s, `friendRequests/${U.A.app}_${U.B.app}`), req(U.A.app, U.B.app)));
        await ok(getDocs(query(collection(db("B"), "friendRequests"), where("toId", "==", U.B.app))));
        await ok(getDocs(query(collection(db("A"), "friendRequests"), where("fromId", "==", U.A.app))));
        await no(getDocs(query(collection(db("C"), "friendRequests"), where("toId", "==", U.B.app))));
        await no(getDoc(doc(db("C"), `friendRequests/${U.A.app}_${U.B.app}`)));
      });
      it("수락 배치(내 friends + 상대 friends + 요청 삭제) 통과 — Android/iOS 동일", async () => {
        await seed(t.env, (s) => setDoc(doc(s, `friendRequests/${U.A.app}_${U.B.app}`), req(U.A.app, U.B.app)));
        const d = db("B"); const b = writeBatch(d); const n = now();
        b.set(doc(d, `users/${U.B.app}/friends/${U.A.app}`), { userId: U.A.app, userName: "에이", photoUrl: "", createdAt: n });
        b.set(doc(d, `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "https://x.example/p.jpg", createdAt: n });
        b.delete(doc(d, `friendRequests/${U.A.app}_${U.B.app}`));
        await ok(b.commit());
      });
      it("친구 끊기 배치(양쪽 삭제) 통과", async () => {
        await seed(t.env, async (s) => {
          await setDoc(doc(s, `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() });
          await setDoc(doc(s, `users/${U.B.app}/friends/${U.A.app}`), { userId: U.A.app, userName: "에이", photoUrl: "", createdAt: now() });
        });
        const d = db("A"); const b = writeBatch(d);
        b.delete(doc(d, `users/${U.A.app}/friends/${U.B.app}`)); b.delete(doc(d, `users/${U.B.app}/friends/${U.A.app}`));
        await ok(b.commit());
      });
      it("거부: 제3자가 남의 친구 목록 문서 삭제 · 남의 목록에 남 명의 문서 쓰기 / 읽기는 로그인 세션 공개", async () => {
        await seed(t.env, (s) => setDoc(doc(s, `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() }));
        await no(setDoc(doc(db("C"), `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() }));
        await no(deleteDoc(doc(db("C"), `users/${U.A.app}/friends/${U.B.app}`)));
        await ok(getDocs(collection(db("C"), `users/${U.A.app}/friends`)));
      });
    });

    describe("users · 기기 토큰 · 열람 기록 · 차단", () => {
      it("프로필 merge(이름 · 사진 · fcmToken · 기타 필드) 통과", async () => {
        await ok(setDoc(doc(db("A"), "users/" + U.A.app), { userId: U.A.app, userName: "에이", profileImageUrl: "https://firebasestorage.googleapis.com/v0/b/x/o/profile_images%2F1.jpg?alt=media", pinnedDiaries: ["a"], equippedTitle: "t" }, { merge: true }));
        await ok(updateDoc(doc(db("A"), "users/" + U.A.app), { fcmToken: "tok", deletionRequestedAt: deleteField() }));
      });
      it("거부: 이름 201자 · 사진 URL 2001자 · 문자열이 아닌 이름 · 남의 문서", async () => {
        await no(setDoc(doc(db("A"), "users/" + U.A.app), { userName: "가".repeat(201) }, { merge: true }));
        await no(setDoc(doc(db("A"), "users/" + U.A.app), { profileImageUrl: "h".repeat(2001) }, { merge: true }));
        await no(setDoc(doc(db("A"), "users/" + U.A.app), { userName: 7 }, { merge: true }));
        await seed(t.env, (s) => setDoc(doc(s, "users/" + U.A.app), { userName: "에이" }));
        await no(updateDoc(doc(db("B"), "users/" + U.A.app), { userName: "해킹" }));
      });
      it("fcmTokens · viewedDiaries · blocked · chatHidden: 본인만", async () => {
        for (const sub of ["fcmTokens/t1", "viewedDiaries/d1", "blocked/" + U.C.app, "chatHidden/c1"]) {
          await ok(setDoc(doc(db("A"), `users/${U.A.app}/${sub}`), { viewedAt: now(), lang: "ko" }, { merge: true }));
          await no(getDoc(doc(db("B"), `users/${U.A.app}/${sub}`)));
          await no(setDoc(doc(db("B"), `users/${U.A.app}/${sub}`), { x: 1 }));
        }
      });
      it("starlightOps: pending + 허용 op 만", async () => {
        await ok(setDoc(doc(db("A"), `users/${U.A.app}/starlightOps/o1`), { op: "openBox", status: "pending", createdAt: now() }));
        await no(setDoc(doc(db("A"), `users/${U.A.app}/starlightOps/o2`), { op: "grant", status: "pending" }));
        await no(setDoc(doc(db("A"), `users/${U.A.app}/starlightOps/o3`), { op: "openBox", status: "done" }));
        await no(updateDoc(doc(db("A"), `users/${U.A.app}/starlightOps/o1`), { status: "done" }));
        await no(setDoc(doc(db("B"), `users/${U.A.app}/starlightOps/o4`), { op: "openBox", status: "pending" }));
      });
    });

    describe("reports · feedback · 선점/초대 · 서버 전용", () => {
      const rep = (r, over = {}) => ({ reporterId: r, type: "diary", targetId: "d1", targetOwnerId: U.A.app, reason: "spam", createdAt: now(), status: "open", ...over });
      it("신고: Android/iOS 형태(스냅샷 필드 포함) 통과 · 차단 신고(user/blocked) 통과", async () => {
        await ok(addDoc(collection(db("B"), "reports"), rep(U.B.app, { targetTitle: "제목", targetContent: "x".repeat(280), targetOwnerName: "에이", targetImageUrl: IMG, reasonDetail: "r".repeat(200) })));
        await ok(addDoc(collection(db("C"), "reports"), rep(U.C.app, { type: "user", targetId: U.A.app, reason: "blocked", targetOwnerName: "에이", source: "block", diaryId: "d1" })));
        await ok(addDoc(collection(db("B"), "reports"), rep(U.B.app, { type: "comment", reason: "other", reasonDetail: "기타" })));
      });
      it("신고 거부: 남 명의 · 잘못된 type · status 조작 · 긴 스냅샷 · 읽기", async () => {
        await no(addDoc(collection(db("B"), "reports"), rep(U.A.app)));
        await no(addDoc(collection(db("B"), "reports"), rep(U.B.app, { type: "admin" })));
        await no(addDoc(collection(db("B"), "reports"), rep(U.B.app, { status: "action_ban" })));
        await no(addDoc(collection(db("B"), "reports"), rep(U.B.app, { targetContent: "x".repeat(1001) })));
        await no(addDoc(collection(db("B"), "reports"), rep(U.B.app, { reason: "r".repeat(101) })));
        await no(getDocs(collection(db("B"), "reports")));
      });
      it("feedback: 통과 · 남 명의/키 추가/길이/읽기 거부", async () => {
        const f = (uid, over = {}) => ({ uid, message: "고쳐 주세요", platform: "android", appVersion: "1.0", locale: "ko", createdAt: serverTimestamp(), ...over });
        await ok(addDoc(collection(db("A"), "feedback"), f(U.A.app)));
        await ok(addDoc(collection(db("C"), "feedback"), f(U.C.app, { platform: "ios" })));
        await no(addDoc(collection(db("A"), "feedback"), f(U.B.app)));
        await no(addDoc(collection(db("A"), "feedback"), f(U.A.app, { email: "a@b.c" })));
        await no(addDoc(collection(db("A"), "feedback"), f(U.A.app, { message: "가".repeat(2001) })));
        await no(getDocs(collection(db("A"), "feedback")));
      });
      it("히든 업적 · 개척 · 초대", async () => {
        await ok(setDoc(doc(db("A"), "hiddenAchievements/h1"), { achieverId: U.A.app, achieverName: "에이", claimedAt: now() }));
        await no(setDoc(doc(db("B"), "hiddenAchievements/h2"), { achieverId: U.A.app, achieverName: "에이", claimedAt: now() }));
        await no(updateDoc(doc(db("A"), "hiddenAchievements/h1"), { achieverId: U.B.app }));
        await ok(deleteDoc(doc(db("A"), "hiddenAchievements/h1")));
        await ok(setDoc(doc(db("A"), "pioneerClaims/KR"), { userId: U.A.app, userName: "에이", weekIndex: 3, claimedAt: now() }));
        await no(setDoc(doc(db("B"), "pioneerClaims/JP"), { userId: U.A.app, userName: "에이", weekIndex: 3 }));
        await no(updateDoc(doc(db("A"), "pioneerClaims/KR"), { userName: "변조" }));
        await ok(setDoc(doc(db("B"), "invites/" + U.B.app), { inviterId: U.A.app, redeemerId: U.B.app, createdAt: now() }));
        await no(setDoc(doc(db("C"), "invites/" + U.B.app), { inviterId: U.A.app, redeemerId: U.B.app, createdAt: now() }));
        await no(setDoc(doc(db("C"), "invites/" + U.C.app), { inviterId: U.C.app, redeemerId: U.C.app, createdAt: now() }));
      });
      it("서버 전용 문서(wallets · adFree · firstStars)는 쓰기 불가, 읽기는 규칙대로", async () => {
        await seed(t.env, async (s) => { await setDoc(doc(s, "wallets/" + U.A.app), { stardust: 5 }); await setDoc(doc(s, "firstStars/" + U.A.app), { actorName: "에이" }); });
        await ok(getDoc(doc(db("A"), "wallets/" + U.A.app)));
        await no(getDoc(doc(db("B"), "wallets/" + U.A.app)));
        await ok(getDoc(doc(db("B"), "firstStars/" + U.A.app)));
        await no(setDoc(doc(db("B"), "firstStars/zzz"), { actorName: "x" }));
        await no(getDoc(doc(db("B"), "adFree/" + U.A.app)));
      });
    });
  });
}

// ── Stage 1 전용 ─────────────────────────────────────────────────────────────────
describe("Stage 1 전용", () => {
  let t;
  before(async () => { t = await H.makeEnv("stage1"); });
  after(async () => { await t.env.cleanup(); });
  beforeEach(async () => { await t.env.clearFirestore(); });
  const db = (k) => t.as(k).firestore();

  it("구버전 앱의 옛 전체 쿼리(createdAt 정렬 limit 1000)가 로그인 사용자에게 통과한다", async () => {
    await seed(t.env, async (s) => {
      await setDoc(doc(s, "diaries/p"), androidDiary(U.A.app, { id: "p" }));
      await setDoc(doc(s, "diaries/f"), androidDiary(U.A.app, { id: "f", visibilityType: "friends", audience: [] }));
    });
    const snap = await ok(getDocs(query(collection(db("B"), "diaries"), orderBy("createdAt", "desc"), limit(1000))));
    if (snap.size !== 2) throw new Error("expected 2 docs, got " + snap.size);
  });
  it("새 앱의 쿼리 3종과 사용자별 쿼리도 Stage 1 에서 통과한다", async () => {
    await ok(getDocs(query(collection(db("B"), "diaries"), where("visibilityType", "==", "public"), orderBy("createdAt", "desc"), limit(1000))));
    await ok(getDocs(query(collection(db("B"), "diaries"), where("userId", "==", U.B.app))));
    await ok(getDocs(query(collection(db("B"), "diaries"), where("visibilityType", "==", "friends"), where("audience", "array-contains", U.B.app), orderBy("createdAt", "desc"), limit(300))));
  });
  it("친구 요청: 옛 random id 로도 생성 · 수락(상대 목록에 쓰기) 가능", async () => {
    const r = { fromId: U.A.app, fromName: "에이", fromPhotoUrl: "", toId: U.B.app, toName: "비", createdAt: now() };
    await ok(setDoc(doc(db("A"), "friendRequests/randomId123"), r));
    const d = db("B"); const b = writeBatch(d);
    b.set(doc(d, `users/${U.B.app}/friends/${U.A.app}`), { userId: U.A.app, userName: "에이", photoUrl: "", createdAt: now() });
    b.set(doc(d, `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() });
    b.delete(doc(d, "friendRequests/randomId123"));
    await ok(b.commit());
  });
});

// ── Stage 2 전용 ─────────────────────────────────────────────────────────────────
describe("Stage 2 전용", () => {
  let t;
  before(async () => { t = await H.makeEnv("stage2"); });
  after(async () => { await t.env.cleanup(); });
  const db = (k) => t.as(k).firestore();
  const dcol = (k) => collection(db(k), "diaries");

  beforeEach(async () => {
    await t.env.clearFirestore();
    await seed(t.env, async (s) => {
      await setDoc(doc(s, "diaries/pub"), androidDiary(U.A.app, { id: "pub" }));
      await setDoc(doc(s, "diaries/priv"), androidDiary(U.A.app, { id: "priv", visibilityType: "private", createdAt: now() - 10 }));
      await setDoc(doc(s, "diaries/frAud"), androidDiary(U.A.app, { id: "frAud", visibilityType: "friends", audience: [U.B.app], createdAt: now() - 20 }));
      await setDoc(doc(s, "diaries/frNoAud"), androidDiary(U.A.app, { id: "frNoAud", visibilityType: "friends", createdAt: now() - 30 }));
      await setDoc(doc(s, "diaries/noVis"), legacyDiary(U.A.app));
      await setDoc(doc(s, `users/${U.A.app}/friends/${U.D.app}`), { userId: U.D.app, userName: "디", photoUrl: "", createdAt: now() });
    });
  });

  it("새 앱 쿼리 3종 통과: 공개 / 내 글 / 친구 공개(audience)", async () => {
    const pub = await getDocs(query(dcol("B"), where("visibilityType", "==", "public"), orderBy("createdAt", "desc"), limit(1000)));
    if (pub.size !== 1) throw new Error("public query returned " + pub.size);
    const mine = await getDocs(query(dcol("A"), where("userId", "==", U.A.app)));
    if (mine.size !== 5) throw new Error("own query returned " + mine.size);
    const fr = await getDocs(query(dcol("B"), where("visibilityType", "==", "friends"), where("audience", "array-contains", U.B.app), orderBy("createdAt", "desc"), limit(300)));
    if (fr.size !== 1) throw new Error("friends query returned " + fr.size);
  });
  it("사용자별 쿼리 통과: userId==X && public / userId==X && friends && audience 포함 나", async () => {
    await ok(getDocs(query(dcol("B"), where("userId", "==", U.A.app), where("visibilityType", "==", "public"))));
    await ok(getDocs(query(dcol("B"), where("userId", "==", U.A.app), where("visibilityType", "==", "friends"), where("audience", "array-contains", U.B.app))));
    await ok(getDocs(query(dcol("B"), where("userId", "==", U.A.app), where("visibilityType", "==", "public"), orderBy("createdAt", "desc"), limit(6))));
  });
  it("옛 전체 쿼리 · 남의 userId 전체 쿼리 · 남의 private 쿼리 거부", async () => {
    await no(getDocs(query(dcol("B"), orderBy("createdAt", "desc"), limit(1000))));
    await no(getDocs(query(dcol("B"), where("userId", "==", U.A.app))));
    await no(getDocs(query(dcol("B"), where("userId", "==", U.A.app), where("visibilityType", "==", "private"))));
    await no(getDocs(query(dcol("B"), where("visibilityType", "==", "friends"), orderBy("createdAt", "desc"))));
    await no(getDocs(query(dcol("B"), where("visibilityType", "==", "friends"), where("audience", "array-contains", U.A.app))));
  });
  it("단건 get: 공개 · 본인 · audience · 글쓴이의 친구 목록 통과", async () => {
    await ok(getDoc(doc(db("B"), "diaries/pub")));
    await ok(getDoc(doc(db("A"), "diaries/priv")));
    await ok(getDoc(doc(db("B"), "diaries/frAud")));
    await ok(getDoc(doc(db("D"), "diaries/frNoAud")));
    await ok(getDoc(doc(db("C"), "diaries/noVis")));
  });
  it("단건 get 거부: 남의 private · 친구 아닌 사람의 friends 글", async () => {
    await no(getDoc(doc(db("B"), "diaries/priv")));
    await no(getDoc(doc(db("C"), "diaries/frAud")));
    await no(getDoc(doc(db("C"), "diaries/frNoAud")));
    await no(getDoc(doc(db("B"), "diaries/frNoAud")));
  });
  it("friendRequests 생성은 문서 id 가 {from}_{to} 여야 한다", async () => {
    const r = { fromId: U.A.app, fromName: "에이", fromPhotoUrl: "", toId: U.B.app, toName: "비", createdAt: now() };
    await no(setDoc(doc(db("A"), "friendRequests/randomId123"), r));
    await no(setDoc(doc(db("A"), `friendRequests/${U.B.app}_${U.A.app}`), r));
    await ok(setDoc(doc(db("A"), `friendRequests/${U.A.app}_${U.B.app}`), r));
  });
  it("friends: 요청 없이 상대 목록에 나를 추가 거부 · 내 목록은 가능 · 요청이 있으면 수락 배치 통과", async () => {
    await no(setDoc(doc(db("B"), `users/${U.A.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() }));
    await ok(setDoc(doc(db("B"), `users/${U.B.app}/friends/${U.A.app}`), { userId: U.A.app, userName: "에이", photoUrl: "", createdAt: now() }));
    await seed(t.env, (s) => setDoc(doc(s, `friendRequests/${U.C.app}_${U.B.app}`), { fromId: U.C.app, fromName: "씨", fromPhotoUrl: "", toId: U.B.app, toName: "비", createdAt: now() }));
    const d = db("B"); const b = writeBatch(d);
    b.set(doc(d, `users/${U.B.app}/friends/${U.C.app}`), { userId: U.C.app, userName: "씨", photoUrl: "", createdAt: now() });
    b.set(doc(d, `users/${U.C.app}/friends/${U.B.app}`), { userId: U.B.app, userName: "비", photoUrl: "", createdAt: now() });
    b.delete(doc(d, `friendRequests/${U.C.app}_${U.B.app}`));
    await ok(b.commit());
  });
  it("friends: 다른 사람의 요청으로는 추가 불가 · update/delete 는 isMe(friendId) 허용", async () => {
    await seed(t.env, (s) => setDoc(doc(s, `friendRequests/${U.C.app}_${U.B.app}`), { fromId: U.C.app, toId: U.B.app }));
    await no(setDoc(doc(db("D"), `users/${U.C.app}/friends/${U.D.app}`), { userId: U.D.app, userName: "디", photoUrl: "", createdAt: now() }));
    await ok(updateDoc(doc(db("D"), `users/${U.A.app}/friends/${U.D.app}`), { userName: "새 이름" }));
    await no(deleteDoc(doc(db("B"), `users/${U.A.app}/friends/${U.D.app}`)));
    await ok(deleteDoc(doc(db("D"), `users/${U.A.app}/friends/${U.D.app}`)));
  });
  it("diaries 쓰기 · 카운터는 Stage 1 과 동일(읽기만 다름)", async () => {
    await ok(setDoc(doc(db("B"), "diaries/new"), iosDiary(U.B.app)));
    await ok(updateDoc(doc(db("A"), "diaries/pub"), { likeCount: increment(1) }));
  });
});

commonSuite("stage1");
commonSuite("stage2");
