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
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");
const { FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

// StaryConfig 와 동일하게 유지할 것 (shared/.../StaryConfig.kt)
const DATABASE_ID = "stary-db";
const USERS = "users";
const FRIENDS = "friends";
const DIARIES = "diaries";
const VIEWED_DIARIES = "viewedDiaries";
const BLOCKED = "blocked";
const REPORTS = "reports";
const REGION = "asia-northeast3"; // stary-db 리전에 맞출 것
const GRACE_MS = 7 * 24 * 60 * 60 * 1000; // 계정 삭제 유예 7일

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
        // notification 페이로드 → 백그라운드/종료 상태에서도 시스템 트레이 알림 표시.
        notification: { title: data.title, body: data.body },
        data,
        android: { priority: "high", notification: { channelId: "stary_default" } },
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

const CHATS = "chats";
const NOTIFICATIONS = "notifications";

/**
 * 단일 사용자에게 data 메시지 발송. 토큰 없으면 생략, 만료 토큰은 정리.
 * (data 값은 모두 문자열이어야 함 — 호출부에서 보장)
 */
async function sendToUser(uid, data) {
  if (!uid) return false;
  const db = getFirestore(DATABASE_ID);
  const userSnap = await db.collection(USERS).doc(uid).get();
  const token = userSnap.get("fcmToken");
  if (typeof token !== "string" || token.length === 0) return false;
  try {
    await getMessaging().send({
      token,
      // notification 페이로드 → 앱이 백그라운드/종료 상태여도 시스템이 트레이 알림을 표시한다.
      notification: { title: data.title, body: data.body },
      data,
      android: { priority: "high", notification: { channelId: "stary_default" } },
    });
    return true;
  } catch (e) {
    const code = e && e.code;
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      await db.collection(USERS).doc(uid).update({ fcmToken: FieldValue.delete() });
    } else {
      logger.warn(`sendToUser ${uid} 발송 실패 (${code})`);
    }
    return false;
  }
}

/**
 * 채팅 새 메시지 → 상대방에게 푸시.
 * chats/{chatId}/messages/{messageId} onCreate. chatId 는 두 사용자 id 를 정렬·결합한 값(StaryConfig.chatId).
 */
exports.notifyOnChatMessage = onDocumentCreated(
  {
    document: `${CHATS}/{chatId}/messages/{messageId}`,
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    const chatId = event.params.chatId;
    const senderId = msg.senderId;
    if (!senderId) return;
    const recipientId = chatId.split("_").find((id) => id !== senderId);
    if (!recipientId) {
      logger.warn(`chat ${chatId}: 수신자 식별 실패 → 발송 생략`);
      return;
    }
    const data = {
      type: "chat",
      chatId,
      // 알림 탭 딥링크용 — 수신자 입장에서 "상대(=발신자)" 와의 채팅방을 연다.
      chatFriendId: senderId,
      chatFriendName: msg.senderName || "",
      title: msg.senderName || "새 메시지",
      body: msg.text || "새 메시지가 도착했어요",
    };
    const ok = await sendToUser(recipientId, data);
    logger.info(`chat ${chatId}: ${recipientId} 푸시 ${ok ? "성공" : "생략/실패"}`);
  }
);

/**
 * 좋아요/댓글 알림 → 수신자(diaryOwnerId)에게 푸시.
 * notifications/{notifId} onCreate. FRIEND_POST 는 notifyFriendsOnDiaryCreate 가 이미 발송하므로 제외(이중 방지).
 */
exports.notifyOnNotificationCreate = onDocumentCreated(
  {
    document: `${NOTIFICATIONS}/{notifId}`,
    database: DATABASE_ID,
    region: REGION,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const n = snap.data();
    const type = n.type || "";
    if (type === "FRIEND_POST") return; // 다이어리 onCreate 함수가 담당
    const recipientId = n.diaryOwnerId;
    if (!recipientId) return;
    const actor = n.actorName || "누군가";
    const diaryTitle = n.diaryTitle || "";
    let title;
    let body;
    if (type === "LIKE") {
      title = `${actor}님이 좋아요를 눌렀어요`;
      body = diaryTitle ? `"${diaryTitle}"` : "내 별에 좋아요가 달렸어요";
    } else {
      title = `${actor}님의 댓글`;
      body = n.content || (diaryTitle ? `"${diaryTitle}"` : "새 댓글이 달렸어요");
    }
    const data = {
      type: String(type),
      diaryId: n.diaryId || "",
      title,
      body,
    };
    const ok = await sendToUser(recipientId, data);
    logger.info(
      `notif ${event.params.notifId}(${type}): ${recipientId} 푸시 ${ok ? "성공" : "생략/실패"}`
    );
  }
);

/**
 * 계정 삭제 유예(7일) 만료 처리 — 매일 자정(KST) 실행.
 * users/{uid}.deletionRequestedAt 가 7일 이상 지난(= 그 동안 재로그인하지 않은) 계정의
 * Firestore 데이터 / Storage 프로필 이미지 / FirebaseAuth 계정을 완전 삭제한다.
 * 재로그인하면 클라이언트가 deletionRequestedAt 를 지우므로 대상에서 자동 제외된다.
 * (authUid = FirebaseAuth uid. Android 는 userId=Google sub ≠ authUid 라 Auth 삭제에 필요.)
 */
exports.purgeExpiredDeletions = onSchedule(
  {
    schedule: "0 0 * * *",
    timeZone: "Asia/Seoul",
    region: REGION,
  },
  async () => {
    const db = getFirestore(DATABASE_ID);
    const cutoff = Date.now() - GRACE_MS;
    const snap = await db
      .collection(USERS)
      .where("deletionRequestedAt", "<=", cutoff)
      .get();
    if (snap.empty) {
      logger.info("계정 삭제 대상 없음");
      return;
    }
    for (const doc of snap.docs) {
      const uid = doc.id;
      const authUid = doc.get("authUid");
      try {
        await deleteUserData(db, uid);
        // Storage 프로필 이미지(profile_images/{userId}.jpg) — 없으면 무시
        try {
          await getStorage().bucket().file(`profile_images/${uid}.jpg`).delete();
        } catch (_) {
          /* 파일 없음 등 무시 */
        }
        // FirebaseAuth 계정
        if (authUid) {
          try {
            await getAuth().deleteUser(authUid);
          } catch (e) {
            logger.warn(`auth ${authUid} 삭제 실패: ${e.code || e.message}`);
          }
        }
        logger.info(`계정 ${uid} 완전 삭제 완료`);
      } catch (e) {
        logger.error(`계정 ${uid} 삭제 실패: ${e.message}`);
      }
    }
  }
);

/** 한 사용자의 Firestore 데이터 정리(다이어리 / 친구 양방향 / 열람·차단 하위 / 프로필 문서). */
async function deleteUserData(db, uid) {
  const diaries = await db.collection(DIARIES).where("userId", "==", uid).get();
  for (const d of diaries.docs) {
    await d.ref.delete();
  }
  const friends = await db.collection(USERS).doc(uid).collection(FRIENDS).get();
  for (const f of friends.docs) {
    try {
      await db.collection(USERS).doc(f.id).collection(FRIENDS).doc(uid).delete();
    } catch (_) {
      /* 상대 목록 정리 실패 무시 */
    }
    await f.ref.delete();
  }
  for (const sub of [VIEWED_DIARIES, BLOCKED]) {
    const s = await db.collection(USERS).doc(uid).collection(sub).get();
    for (const x of s.docs) {
      await x.ref.delete();
    }
  }
  await db.collection(USERS).doc(uid).delete();
}

/**
 * 신고 조치 — 관리자가 Firebase Console 에서 reports/{id}.status 를 바꾸면 실행(체크리스트 28).
 *   "action_delete" → 대상 다이어리 문서 + Storage 미디어(사진/영상) 삭제
 *   "action_ban"    → 대상 계정 완전 삭제(제재): Firestore 데이터 + 프로필 이미지 + FirebaseAuth 계정
 *   그 외("open"/"dismissed" 등)는 무시.
 * 신고 문서에는 검토용 스냅샷(targetTitle/targetContent/targetOwnerName/targetImageUrl)이 함께 저장돼
 * Console 목록만으로 판단 가능(클라이언트 report() 가 기록).
 * 무한 루프 방지: 처리 뒤 resolvedAt 를 기록하고, resolvedAt 이 있으면 이후 update 는 skip.
 */
exports.onReportAction = onDocumentUpdated(
  { document: `${REPORTS}/{reportId}`, database: DATABASE_ID, region: REGION },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!after || after.resolvedAt) return; // 이미 처리됨
    const status = after.status;
    if (before && before.status === status) return; // status 변화 없음
    if (status !== "action_delete" && status !== "action_ban") return;

    const db = getFirestore(DATABASE_ID);
    const { targetId, targetOwnerId, type } = after;
    try {
      if (status === "action_delete") {
        if (type === "diary" && targetId) {
          const ref = db.collection(DIARIES).doc(targetId);
          const snap = await ref.get();
          if (snap.exists) {
            for (const field of ["imageUrl", "videoUrl"]) {
              const path = storagePathFromUrl(snap.get(field));
              if (path) {
                try {
                  await getStorage().bucket().file(path).delete();
                } catch (_) {
                  /* 파일 없음/이미 삭제됨 무시 */
                }
              }
            }
            await ref.delete();
          }
        }
      } else if (status === "action_ban" && targetOwnerId) {
        const userDoc = await db.collection(USERS).doc(targetOwnerId).get();
        const authUid = userDoc.get("authUid");
        await deleteUserData(db, targetOwnerId);
        try {
          await getStorage().bucket().file(`profile_images/${targetOwnerId}.jpg`).delete();
        } catch (_) {
          /* 프로필 이미지 없음 무시 */
        }
        if (authUid) {
          try {
            await getAuth().deleteUser(authUid);
          } catch (e) {
            logger.warn(`ban auth ${authUid} 삭제 실패: ${e.code || e.message}`);
          }
        }
      }
      await event.data.after.ref.set({ resolvedAt: Date.now() }, { merge: true });
      logger.info(`신고 ${event.params.reportId} 조치 완료: ${status}`);
    } catch (e) {
      logger.error(`신고 조치 실패(${status}): ${e.message}`);
    }
  }
);

/** Firebase Storage 다운로드 URL 에서 객체 경로(diary_images/xxx.jpg)를 추출. 실패 시 null. */
function storagePathFromUrl(url) {
  if (!url || typeof url !== "string") return null;
  const m = url.match(/\/o\/([^?]+)/);
  if (!m) return null;
  try {
    return decodeURIComponent(m[1]);
  } catch (_) {
    return null;
  }
}
