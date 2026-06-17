/**
 * Stary Cloud Functions — 체크리스트 8 (FCM 푸시 발송 서버부).
 *
 * diaries/{diaryId} onCreate(named DB stary-db) →
 *   작성자의 users/{uid}/friends 조회 →
 *   각 친구 users/{fid}.fcmToken 으로 data 메시지 { diaryId, title, body } 발송.
 * 클라이언트 수신부(push/StaryMessagingService)는 이미 배포돼 있음.
 *
 * 배포(사용자): Blaze 요금제 활성화 후
 *   cd functions && npm install && cd ..
 *   firebase deploy --only functions
 *
 * ⚠️ REGION 은 stary-db 데이터베이스의 리전과 일치해야 한다.
 *   (불일치 시 배포가 명확한 에러로 실패 — Firebase Console > Firestore > stary-db 위치 확인)
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

// StaryConfig 와 동일하게 유지할 것 (shared/.../StaryConfig.kt)
const DATABASE_ID = "stary-db";
const USERS = "users";
const FRIENDS = "friends";
const REGION = "asia-northeast3"; // stary-db 리전에 맞출 것

/** FCM multicast 1회 최대 토큰 수 */
const FCM_BATCH = 500;

exports.notifyFriendsOnDiaryCreate = onDocumentCreated(
  {
    document: "diaries/{diaryId}",
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const diary = snap.data();
    const diaryId = event.params.diaryId;
    const authorId = diary.userId;
    if (!authorId) {
      logger.warn(`diary ${diaryId}: userId 없음 → 발송 생략`);
      return;
    }

    const db = getFirestore(DATABASE_ID);
    const friendsSnap = await db
      .collection(USERS).doc(authorId)
      .collection(FRIENDS).get();
    if (friendsSnap.empty) {
      logger.info(`diary ${diaryId}: 친구 없음 → 발송 생략`);
      return;
    }

    // 친구 프로필에서 fcmToken 수집 (토큰 없는 친구는 제외)
    const friendIds = friendsSnap.docs.map((d) => d.id);
    const friendDocs = await db.getAll(
      ...friendIds.map((id) => db.collection(USERS).doc(id))
    );
    const targets = friendDocs
      .map((d) => ({ uid: d.id, token: d.get("fcmToken") }))
      .filter((t) => typeof t.token === "string" && t.token.length > 0);
    if (targets.length === 0) {
      logger.info(`diary ${diaryId}: fcmToken 보유 친구 없음 → 발송 생략`);
      return;
    }

    const senderName = diary.isAnonymous
      ? "누군가"
      : (diary.userName || "친구");
    const data = {
      diaryId,
      title: `${senderName}님의 새 별`,
      body: diary.title || "새 다이어리가 등록됐어요",
    };

    const messaging = getMessaging();
    let success = 0;
    const deadTokenOwners = [];
    for (let i = 0; i < targets.length; i += FCM_BATCH) {
      const batch = targets.slice(i, i + FCM_BATCH);
      const res = await messaging.sendEachForMulticast({
        tokens: batch.map((t) => t.token),
        data,
        android: { priority: "high" },
      });
      success += res.successCount;
      res.responses.forEach((r, idx) => {
        if (r.success) return;
        const code = r.error && r.error.code;
        // 만료/해지된 토큰은 프로필에서 제거해 다음 발송에서 제외
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          deadTokenOwners.push(batch[idx].uid);
        } else {
          logger.warn(`diary ${diaryId}: ${batch[idx].uid} 발송 실패 (${code})`);
        }
      });
    }

    if (deadTokenOwners.length > 0) {
      const cleanup = db.batch();
      deadTokenOwners.forEach((uid) => {
        cleanup.update(db.collection(USERS).doc(uid), {
          fcmToken: FieldValue.delete(),
        });
      });
      await cleanup.commit();
    }

    logger.info(
      `diary ${diaryId}: ${success}/${targets.length} 발송 성공` +
        (deadTokenOwners.length ? `, 만료 토큰 ${deadTokenOwners.length}개 정리` : "")
    );
  }
);
