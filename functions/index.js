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
const { translateDiary } = require("./translations");

initializeApp();

// StaryConfig 와 동일하게 유지할 것 (shared/.../StaryConfig.kt)
const DATABASE_ID = "stary-db";
const USERS = "users";
const FRIENDS = "friends";
const DIARIES = "diaries";
const VIEWED_DIARIES = "viewedDiaries";
const BLOCKED = "blocked";
const REPORTS = "reports";
/** users/{uid}/fcmTokens/{token} — 기기별 토큰(한 계정이 여러 기기에 로그인할 수 있다). */
const FCM_TOKENS = "fcmTokens";
const REGION = "asia-northeast3"; // stary-db 리전에 맞출 것
const GRACE_MS = 7 * 24 * 60 * 60 * 1000; // 계정 삭제 유예 7일
const REPORT_KEEP_MS = 365 * 24 * 60 * 60 * 1000; // 처리 끝난 신고 보관 1년(개인정보 처리방침과 같게)

/** FCM multicast 1회 최대 토큰 수 */
const FCM_BATCH = 500;

/**
 * 플랫폼별 발송 옵션.
 *  - android : heads-up 채널(앱이 미리 만들어 둔 stary_default) + high priority
 *  - apns    : iOS 는 이 블록이 없으면 소리 없이 조용히 오거나 표시가 지연될 수 있다.
 *              (iOS 팝업 알림이 안 오던 원인 중 하나 — 클라이언트 APNs 등록과 함께 필요)
 * ⚠️ Firebase Console > 프로젝트 설정 > 클라우드 메시징에 **APNs 인증 키(.p8)** 가 등록돼 있어야
 *    iOS 로 실제 발송된다(키가 없으면 서버가 성공을 반환해도 기기까지 가지 않음).
 */
const ANDROID_OPTS = { priority: "high", notification: { channelId: "stary_default" } };
const APNS_OPTS = {
  // apns-push-type: iOS 13+ 는 알림 종류 헤더를 요구한다 — 명시해 두면 "alert" 로 즉시 표시(누락 시 지연/무시 가능성 차단).
  headers: { "apns-push-type": "alert", "apns-priority": "10" },
  payload: { aps: { sound: "default" } },
};

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
    // 나만 보기 글은 아무에게도 알리지 않는다(친구 공개/전체 공개만 친구에게). 앱의 인앱 알림도 같은 규칙.
    if (diary.visibilityType === "private") {
      logger.info(`diary ${diaryId}: 나만 보기 → 발송 생략`);
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
    // 기기별 토큰(users/{uid}/fcmTokens) 도 합친다 — 한 계정이 여러 기기에 로그인한 경우.
    const perDevice = await Promise.all(
      friendDocs.map(async (d) => {
        const snap = await db.collection(USERS).doc(d.id).collection(FCM_TOKENS).get();
        return snap.docs.map((t) => ({ uid: d.id, token: t.id }));
      })
    );
    const seen = new Set(targets.map((t) => t.token));
    perDevice.flat().forEach((t) => {
      if (t.token && !seen.has(t.token)) {
        seen.add(t.token);
        targets.push(t);
      }
    });
    if (targets.length === 0) {
      logger.info(`diary ${diaryId}: fcmToken 보유 친구 없음 → 발송 생략`);
      return;
    }

    const senderName = diary.userName || "친구";
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
        android: ANDROID_OPTS,
        apns: APNS_OPTS,
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
          deadTokenOwners.push(batch[idx]);
        } else {
          logger.warn(`diary ${diaryId}: ${batch[idx].uid} 발송 실패 (${code})`);
        }
      });
    }

    if (deadTokenOwners.length > 0) {
      // 기기 문서 + (같은 값이면) 예전 단일 필드까지 정리.
      for (const dead of deadTokenOwners) {
        await pruneToken(db, dead.uid, dead.token);
      }
    }

    logger.info(
      `diary ${diaryId}: ${success}/${targets.length} 발송 성공` +
        (deadTokenOwners.length ? `, 만료 토큰 ${deadTokenOwners.length}개 정리` : "")
    );
  }
);

/** 최상위: 첫 별 공지(문서 id = 작성자 appUserId — 계정당 1번). StaryConfig.FIRST_STARS 와 같은 값. */
const FIRST_STARS = "firstStars";

/**
 * 첫 별 공지 푸시 문구 — 받는 기기의 앱 언어(users/{uid}/fcmTokens/{token}.lang, 앱이 토큰 저장 시 기록).
 * lang 이 없으면(구버전 앱) 한국어. 앱 문자열 notif_first_star 와 뜻을 맞출 것.
 */
const FIRST_STAR_TEXT = {
  ko: (name) => ({ title: "새로운 별이 떴어요", body: `${name}님이 세상에 첫 별을 남겼어요` }),
  en: (name) => ({ title: "A new star is born", body: `${name} left their first star in the world` }),
  ja: (name) => ({ title: "新しい星が生まれました", body: `${name}さんが世界に初めての星を残しました` }),
};

/**
 * 전체 공지 푸시를 보내지 않는 받는 사람 현지 시각 — 21시~다음 날 8시(정보통신망법 §50 의 야간 기준과 같다, 2026-11-08).
 * 시간대는 기기 토큰 문서의 `tz`(앱이 토큰 저장 때 기록). 없으면(구버전 앱) 언어로 짐작하고, 그것도 모르면 서울.
 */
const QUIET_START_HOUR = 21;
const QUIET_END_HOUR = 8;
const LANG_TZ = { ko: "Asia/Seoul", ja: "Asia/Tokyo" };

function localHour(zone, now) {
  return Number(
    new Intl.DateTimeFormat("en-US", { timeZone: zone, hour: "numeric", hourCycle: "h23" }).format(now)
  );
}

function inQuietHours(tz, lang, now = new Date()) {
  let hour;
  try {
    hour = localHour(tz || LANG_TZ[lang] || "Asia/Seoul", now);
  } catch (_) {
    hour = localHour("Asia/Seoul", now); // 알 수 없는 시간대 문자열
  }
  return hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR;
}

/**
 * 첫 별 공지(2026-10-02) — 사용자가 **처음으로 전체 공개** 별을 올리면 모든 사용자에게 알린다.
 *  - 받지 않는 기기(2026-11-08): 설정 "새 별 소식"을 끈 기기(토큰 문서 firstStar == false),
 *    받는 사람 현지 시각 21~08시인 기기([inQuietHours]) — 그 시간대 사람에게는 이 공지를 아예 보내지 않는다(나중에 몰아 보내지 않음).
 *  - 공개 범위: 전체 공개만. 친구 공개는 친구에게만(notifyFriendsOnDiaryCreate), 나만 보기는 아무에게도.
 *  - 판정: 이 작성자의 다른 전체 공개 별이 이미 있으면 첫 별이 아니다(기능 도입 전에 올린 별 포함).
 *  - 계정당 1번: firstStars/{작성자} 를 create() — 이미 있으면 실패하므로 재시도·동시 업로드에도 한 번만.
 *  - 알림 목록: 앱이 firstStars 를 직접 읽어 알림 화면에 합친다(사용자마다 문서를 만들지 않는다 — 사용자 수만큼 쓰기 방지).
 *  - 푸시: 모든 기기 토큰(컬렉션 그룹 1회 조회)으로 기기 언어별 발송. 작성자 본인과 친구는 제외
 *    (친구는 이미 "○○님의 새 별" 푸시를 받는다 — 같은 별로 두 번 울리지 않게).
 */
exports.announceFirstStar = onDocumentCreated(
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
    const authorName = (diary.userName || "").trim();
    if (!authorId || !authorName) return;
    if ((diary.visibilityType || "public") !== "public") return;

    const db = getFirestore(DATABASE_ID);
    const mine = await db.collection(DIARIES).where("userId", "==", authorId).get();
    const hasOlderPublic = mine.docs.some(
      (d) => d.id !== diaryId && (d.get("visibilityType") || "public") === "public"
    );
    if (hasOlderPublic) return;

    try {
      await db.collection(FIRST_STARS).doc(authorId).create({
        actorId: authorId,
        actorName: authorName,
        diaryId,
        diaryTitle: diary.title || "",
        createdAt: Date.now(),
      });
    } catch (e) {
      logger.info(`firstStar ${authorId}: 이미 공지됨 → 생략`);
      return;
    }

    // 제외 대상: 작성자 본인 + 친구.
    const friendsSnap = await db.collection(USERS).doc(authorId).collection(FRIENDS).get();
    const excludedUids = new Set([authorId, ...friendsSnap.docs.map((d) => d.id)]);
    const tokenSnap = await db.collectionGroup(FCM_TOKENS).get();
    // 작성자 기기 토큰은 다른 계정 문서에 남아 있어도 제외(같은 기기로 계정을 바꿔 쓴 경우).
    const authorTokens = new Set(
      tokenSnap.docs
        .filter((d) => d.ref.parent.parent && d.ref.parent.parent.id === authorId)
        .map((d) => d.id)
    );
    const byLang = { ko: [], en: [], ja: [] };
    const seen = new Set();
    tokenSnap.docs.forEach((d) => {
      const owner = d.ref.parent.parent;
      if (!owner || excludedUids.has(owner.id) || authorTokens.has(d.id) || seen.has(d.id)) return;
      seen.add(d.id);
      if (d.get("firstStar") === false) return; // 설정 > 알림 > 새 별 소식 끔
      if (inQuietHours(d.get("tz"), d.get("lang"))) return; // 받는 사람 현지 밤 9시~아침 8시
      const lang = byLang[d.get("lang")] ? d.get("lang") : "ko";
      byLang[lang].push({ uid: owner.id, token: d.id });
    });

    const messaging = getMessaging();
    let sent = 0;
    let total = 0;
    const dead = [];
    for (const [lang, targets] of Object.entries(byLang)) {
      if (targets.length === 0) continue;
      const text = FIRST_STAR_TEXT[lang](authorName);
      const data = { type: "FIRST_STAR", diaryId, title: text.title, body: text.body };
      for (let i = 0; i < targets.length; i += FCM_BATCH) {
        const batch = targets.slice(i, i + FCM_BATCH);
        const res = await messaging.sendEachForMulticast({
          tokens: batch.map((t) => t.token),
          notification: { title: text.title, body: text.body },
          data,
          android: ANDROID_OPTS,
          apns: APNS_OPTS,
        });
        total += batch.length;
        sent += res.successCount;
        res.responses.forEach((r, idx) => {
          if (r.success) return;
          const code = r.error && r.error.code;
          if (
            code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token"
          ) {
            dead.push(batch[idx]);
          }
        });
      }
    }
    for (const t of dead) await pruneToken(db, t.uid, t.token);
    logger.info(
      `firstStar ${authorId}(${diaryId}): ${sent}/${total} 발송 성공` +
        (dead.length ? `, 만료 토큰 ${dead.length}개 정리` : "")
    );
  }
);

/**
 * 다이어리 자동 선번역(2026-10-07) — 저장 구조·규칙·언어 추가 방법은 translations.js 머리말 참고.
 * 기존 생성 흐름과 별개의 트리거라 번역이 실패/지연돼도 다이어리 생성·푸시에는 영향이 없다(translateDiary 는 throw 하지 않는다).
 *  - 생성 직후: 원문 언어를 감지해 나머지 언어로 번역 → diaries/{id}.translations 에 저장.
 *  - 수정(제목/본문 변경)·비공개→공개 전환: 낡은 번역을 버리고 다시 번역. 좋아요/조회수 같은 다른 갱신과 번역 결과 저장 자체는
 *    아래 비교에서 걸러져 즉시 종료되므로 무한 루프가 없다.
 */
const TRANSLATE_OPTS = { database: DATABASE_ID, region: REGION, timeoutSeconds: 120 };

/**
 * 위치정보 이용 확인자료(위치정보법 §16②, 2026-11-08) — 별이 위치와 함께 저장될 때마다 한 줄 남긴다.
 *  - 관리자 전용 컬렉션(클라이언트 규칙 기본 거부). 좌표는 담지 않는다(누가 · 언제 · 무엇에 · 누구에게 보이게 썼는지만).
 *  - 6개월 이상 보관: expireAt(약 7개월 뒤)에 Firestore TTL 정책을 콘솔에서 건다(locationLogs.expireAt).
 *  - 계정을 지워도 법정 보관 기간 동안은 남는다(deleteUserData 대상 아님) — 위치기반서비스 이용약관 제8조와 같게.
 */
const LOCATION_LOGS = "locationLogs";
const LOCATION_LOG_KEEP_MS = 210 * 24 * 60 * 60 * 1000;
exports.logLocationUse = onDocumentCreated(
  { document: "diaries/{diaryId}", database: DATABASE_ID, region: REGION },
  async (event) => {
    const d = event.data && event.data.data();
    if (!d || !d.userId) return;
    const now = Date.now();
    await getFirestore(DATABASE_ID)
      .collection(LOCATION_LOGS)
      .add({
        userId: d.userId,
        diaryId: event.params.diaryId,
        purpose: "star_create",
        visibility: d.visibilityType || "public",
        createdAt: now,
        expireAt: new Date(now + LOCATION_LOG_KEEP_MS),
      });
  }
);

exports.translateDiaryOnCreate = onDocumentCreated(
  { document: "diaries/{diaryId}", ...TRANSLATE_OPTS },
  async (event) => {
    await translateDiary(getFirestore(DATABASE_ID), event.params.diaryId);
  }
);

exports.translateDiaryOnEdit = onDocumentUpdated(
  { document: "diaries/{diaryId}", ...TRANSLATE_OPTS },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;
    const textChanged = before.title !== after.title || before.content !== after.content;
    const becamePublic =
      before.visibilityType === "private" && (after.visibilityType || "public") !== "private";
    if (!textChanged && !becamePublic) return;
    await translateDiary(getFirestore(DATABASE_ID), event.params.diaryId);
  }
);

const CHATS = "chats";
const NOTIFICATIONS = "notifications";

/**
 * 한 사용자의 **모든 기기 토큰**을 모은다.
 *  - users/{uid}/fcmTokens/{token} : 기기별(문서 id = 토큰). 한 계정이 폰+아이패드처럼 여러 기기에 로그인 가능.
 *  - users/{uid}.fcmToken          : 예전 단일 필드(구버전 앱 호환) — 함께 넣고 중복은 제거한다.
 */
async function collectTokens(db, uid) {
  const userRef = db.collection(USERS).doc(uid);
  const [userSnap, tokenSnap] = await Promise.all([
    userRef.get(),
    userRef.collection(FCM_TOKENS).get(),
  ]);
  if (!userSnap.exists) {
    // 수신자 프로필 문서가 없음 = uid 가 잘못됐다는 뜻(앱의 appUserId 규칙 확인 필요).
    logger.warn(`sendToUser ${uid}: users 문서 없음 → 발송 생략`);
    return null;
  }
  const tokens = new Set();
  tokenSnap.docs.forEach((d) => {
    if (d.id) tokens.add(d.id);
  });
  const legacy = userSnap.get("fcmToken");
  if (typeof legacy === "string" && legacy.length > 0) tokens.add(legacy);
  return [...tokens];
}

/** 죽은 토큰 제거 — 기기 문서와, 같은 값이면 예전 단일 필드까지. */
async function pruneToken(db, uid, token) {
  const userRef = db.collection(USERS).doc(uid);
  await userRef.collection(FCM_TOKENS).doc(token).delete().catch(() => {});
  const snap = await userRef.get();
  if (snap.exists && snap.get("fcmToken") === token) {
    await userRef.update({ fcmToken: FieldValue.delete() }).catch(() => {});
  }
}

/**
 * 단일 사용자에게 data 메시지 발송 — **로그인해 둔 모든 기기**로 보낸다.
 * 토큰이 하나도 없으면 생략하고, 만료 토큰은 정리한다.
 * (data 값은 모두 문자열이어야 함 — 호출부에서 보장)
 */
async function sendToUser(uid, data) {
  if (!uid) return false;
  const db = getFirestore(DATABASE_ID);
  const tokens = await collectTokens(db, uid);
  if (tokens === null) return false;
  if (tokens.length === 0) {
    // ⚠️ "푸시가 안 온다"의 1순위 원인. 수신자 앱이 토큰을 저장하지 못한 상태다.
    //    (iOS: APNs 인증 키 미등록/푸시 권한 거부/시뮬레이터, Android: 알림 권한 거부 등)
    logger.warn(`sendToUser ${uid}: 토큰 없음 → 발송 생략(수신자 앱이 토큰 미저장)`);
    return false;
  }
  // 잘못된 기기(로그아웃 뒤 다른 계정이 쓰는 기기)에서 남의 알림이 뜨지 않도록 수신자를 명시한다.
  const payload = { ...data, recipientId: uid };
  const res = await getMessaging().sendEachForMulticast({
    tokens,
    // notification 페이로드 → 앱이 백그라운드/종료 상태여도 시스템이 트레이 알림을 표시한다.
    notification: { title: data.title, body: data.body },
    data: payload,
    android: ANDROID_OPTS,
    apns: APNS_OPTS,
  });
  const dead = [];
  res.responses.forEach((r, i) => {
    if (r.success) return;
    const code = r.error && r.error.code;
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      dead.push(tokens[i]);
    } else {
      // iOS 에서 APNs 인증 키(.p8) 미등록이면 여기로 온다(third-party auth error 등).
      logger.error(`sendToUser ${uid} 발송 실패 (${code}): ${r.error && r.error.message}`);
    }
  });
  for (const t of dead) {
    logger.warn(`sendToUser ${uid}: 만료된 토큰 → 삭제(…${t.slice(-8)})`);
    await pruneToken(db, uid, t);
  }
  logger.info(
    `sendToUser ${uid}: 기기 ${tokens.length}대 중 ${res.successCount}대 발송 성공` +
      (dead.length ? `, 만료 토큰 ${dead.length}개 정리` : "")
  );
  return res.successCount > 0;
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
    if (!senderId) {
      logger.warn(`chat ${chatId}: senderId 없음 → 발송 생략`);
      return;
    }
    // chatId = "작은appUserId_큰appUserId" → 나머지 한 쪽이 수신자.
    // ⚠️ 발신자 id 가 chatId 안에 없으면(= 앱의 appUserId 규칙이 어긋난 상태) 수신자를 못 고른다.
    const recipientId = chatId.split("_").find((id) => id !== senderId);
    if (!recipientId || !chatId.split("_").includes(senderId)) {
      logger.warn(
        `chat ${chatId}: 수신자 식별 실패(senderId=${senderId}) → 발송 생략`
      );
      return;
    }
    logger.info(`chat ${chatId}: ${senderId} → ${recipientId} 푸시 시도`);
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
 * 좋아요/댓글/친구 요청 알림 → 수신자(diaryOwnerId)에게 푸시.
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
    } else if (type === "FRIEND_REQUEST") {
      // 친구 요청 — 다이어리가 없는 알림(탭하면 앱의 친구 화면으로).
      title = `${actor}님의 친구 요청`;
      body = "친구 요청이 도착했어요";
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
 * 사용자의 프로필 사진 파일을 모두 지운다 — 예전 고정 경로 profile_images/{uid}.jpg + 교체마다 새로 만드는
 * profile_images/{uid}_{시각}.jpg(2026-10-19~). 없으면 무시.
 * ⚠️ 접두어를 `${uid}` 로만 잡으면 다른 사용자(uid 가 그걸로 시작하는)의 파일까지 지운다 → `${uid}_` 로 정확히.
 */
async function deleteProfileImages(uid) {
  const bucket = getStorage().bucket();
  try {
    await bucket.file(`profile_images/${uid}.jpg`).delete();
  } catch (_) {
    /* 파일 없음 등 무시 */
  }
  try {
    await bucket.deleteFiles({ prefix: `profile_images/${uid}_` });
  } catch (_) {
    /* 파일 없음 등 무시 */
  }
}

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
    // 처리 끝난 지 1년이 지난 신고는 지운다(개인정보 처리방침의 보관 기간과 같다, 2026-11-08).
    try {
      await forEachDoc(
        db.collection(REPORTS).where("resolvedAt", "<=", Date.now() - REPORT_KEEP_MS),
        (doc) => doc.ref.delete()
      );
    } catch (e) {
      logger.warn(`오래된 신고 정리 실패: ${e.message}`);
    }
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
        // Storage 프로필 이미지(예전 고정 경로 + 교체본들) — 없으면 무시
        await deleteProfileImages(uid);
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

/**
 * 한 사용자의 데이터 전부 정리 — 계정 삭제(7일 유예 뒤 purgeExpiredDeletions) · 제재(onReportAction action_ban) 공용.
 * 개인정보보호법 §21(파기) · Apple 5.1.1(v)(계정과 관련 데이터 삭제) 대응으로 2026-11-08 범위를 넓혔다. 새 컬렉션에 사용자 데이터를 쓰기 시작하면 **여기에도 추가할 것.**
 *  1. 내 다이어리: Storage 사진 · 영상 + 문서와 하위(남이 단 댓글 · 좋아요 포함 — 글이 사라지면 같이 사라진다)
 *  2. 남의 글에 남긴 내 댓글 · 좋아요(컬렉션 그룹 — firestore.indexes.json fieldOverrides 필요) + 그 글의 개수 -1
 *  3. 채팅: 내가 보낸 메시지 삭제, 남은 메시지가 없으면 방도 삭제(상대가 보낸 메시지는 상대의 것이라 남긴다)
 *  4. 알림(받은 것 · 내가 행위자인 것), 친구 요청(보낸 · 받은), 의견(feedback), 히든 업적 · 개척 선점 · 초대 · 첫 별 공지 · 광고 제거 · 지갑 · 장착
 *  5. 친구 양방향, users/{uid} 와 모든 하위 컬렉션(fcmTokens · viewedDiaries · blocked · chatHidden · starlightOps …)
 *  6. 내가 낸 신고: 신고자 id 만 지운다(신고 자체는 운영 기록 — 처리 후 1년 뒤 purgeExpiredDeletions 가 지운다)
 * 남에 대한 신고(targetOwnerId == 나)는 제재 기록이라 남긴다(위와 같은 1년 보관).
 */
async function deleteUserData(db, uid) {
  // 1) 내 다이어리
  const diaries = await db.collection(DIARIES).where("userId", "==", uid).get();
  for (const d of diaries.docs) {
    for (const field of ["imageUrl", "videoUrl"]) {
      const path = storagePathFromUrl(d.get(field));
      if (!path) continue;
      try {
        await getStorage().bucket().file(path).delete();
      } catch (_) {
        /* 파일 없음/이미 삭제됨 무시 */
      }
    }
    await db.recursiveDelete(d.ref);
  }

  // 2) 남의 글에 남긴 댓글 · 좋아요 — 글의 개수도 함께 줄인다(앱이 쓸 때 increment 로 올린 값).
  const counters = [
    ["comments", "commentCount"],
    ["likes", "likeCount"],
  ];
  for (const [sub, countField] of counters) {
    await forEachDoc(db.collectionGroup(sub).where("userId", "==", uid), async (doc) => {
      const diaryRef = doc.ref.parent.parent;
      await doc.ref.delete();
      if (diaryRef) {
        try {
          await diaryRef.update({ [countField]: FieldValue.increment(-1) });
        } catch (_) {
          /* 글이 이미 없음 */
        }
      }
    });
  }

  // 3) 채팅 — 내가 보낸 메시지만 지우고, 빈 방은 방째로.
  const chats = await db.collection(CHATS).where("participants", "array-contains", uid).get();
  for (const chat of chats.docs) {
    await forEachDoc(chat.ref.collection("messages").where("senderId", "==", uid), (m) => m.ref.delete());
    const left = await chat.ref.collection("messages").limit(1).get();
    if (left.empty) {
      await db.recursiveDelete(chat.ref);
    } else if (chat.get("lastSenderId") === uid) {
      await chat.ref.update({ lastMessage: "", lastSenderName: "" });
    }
  }

  // 4) 나를 가리키는 최상위 문서들
  const byField = [
    [NOTIFICATIONS, "diaryOwnerId"],
    [NOTIFICATIONS, "actorId"],
    ["friendRequests", "fromId"],
    ["friendRequests", "toId"],
    ["feedback", "uid"],
    ["hiddenAchievements", "achieverId"],
    ["pioneerClaims", "userId"],
    ["invites", "inviterId"],
  ];
  for (const [col, field] of byField) {
    await forEachDoc(db.collection(col).where(field, "==", uid), (doc) => doc.ref.delete());
  }
  for (const col of ["invites", FIRST_STARS, "adFree", "wallets", "starlightEquips"]) {
    try {
      await db.collection(col).doc(uid).delete();
    } catch (_) {
      /* 없음 */
    }
  }

  // 6) 내가 낸 신고 — 신고자 id 만 지운다.
  await forEachDoc(db.collection(REPORTS).where("reporterId", "==", uid), (doc) =>
    doc.ref.update({ reporterId: "deleted", reporterName: FieldValue.delete() })
  );

  // 5) 친구 양방향 + 사용자 문서와 모든 하위 컬렉션
  const friends = await db.collection(USERS).doc(uid).collection(FRIENDS).get();
  for (const f of friends.docs) {
    try {
      await db.collection(USERS).doc(f.id).collection(FRIENDS).doc(uid).delete();
    } catch (_) {
      /* 상대 목록 정리 실패 무시 */
    }
  }
  await db.recursiveDelete(db.collection(USERS).doc(uid));
}

/** 쿼리 결과를 300개씩 끊어 하나씩 처리(지운 문서는 다음 페이지에서 빠지므로 처음부터 다시 읽는다). */
async function forEachDoc(query, fn) {
  for (let guard = 0; guard < 1000; guard++) {
    const snap = await query.limit(300).get();
    if (snap.empty) return;
    for (const doc of snap.docs) await fn(doc);
    if (snap.size < 300) return;
  }
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
        await deleteProfileImages(targetOwnerId);
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

/**
 * 운영자(관리자) 계정 이메일 — StaryConfig.ADMIN_EMAILS / iOS AppConfig 와 동일하게 유지할 것.
 * 신고·차단이 들어오면 이 계정이 로그인해 둔 모든 기기로 즉시 푸시한다(아래 notifyAdminOnReport).
 */
const ADMIN_EMAILS = ["chaalsdn0217@gmail.com"];

/** 관리자 이메일 → 앱 사용자 id(Google 로그인이면 Google sub, 아니면 FirebaseAuth uid). 없으면 null. */
async function adminAppUserId(email) {
  try {
    const u = await getAuth().getUserByEmail(email);
    const google = (u.providerData || []).find((p) => p.providerId === "google.com");
    return (google && google.uid) || u.uid;
  } catch (e) {
    logger.warn(`관리자 계정 조회 실패(${email}): ${e.code || e.message}`);
    return null;
  }
}

/**
 * 신고 접수 즉시 운영자에게 푸시 — App Store Guideline 1.2 "24시간 이내 조치" 대응(2026-09-26).
 * reports/{id} onCreate. 사용자가 차단해도 앱이 reason="blocked" 신고를 함께 남기므로 차단도 여기로 온다.
 * 운영자는 Console 에서 status 를 action_delete / action_ban 으로 바꿔 조치한다(onReportAction).
 */
exports.notifyAdminOnReport = onDocumentCreated(
  { document: `${REPORTS}/{reportId}`, database: DATABASE_ID, region: REGION },
  async (event) => {
    const r = event.data && event.data.data();
    if (!r) return;
    const kind = r.reason === "blocked" ? "차단" : "신고";
    const what = { diary: "다이어리", comment: "댓글", user: "사용자" }[r.type] || r.type || "콘텐츠";
    const target = r.targetTitle || r.targetContent || r.targetOwnerName || r.targetId || "";
    const data = {
      type: "ADMIN_REPORT",
      reportId: String(event.params.reportId),
      title: `[운영] 새 ${kind} — ${what}`,
      body: `${String(target).slice(0, 80)} (사유: ${r.reason || "-"}) · 24시간 안에 검토해 주세요`,
    };
    for (const email of ADMIN_EMAILS) {
      const uid = await adminAppUserId(email);
      if (uid) await sendToUser(uid, data);
    }
    logger.info(`신고 ${event.params.reportId}(${r.type}/${r.reason}) → 운영자 알림`);
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

// ── 별가루(지갑 · 우주 항해 · 광고 보상 · 별 고리 확인) — starlight.js ──
const starlight = require("./starlight");
exports.onStarlightOp = starlight.onStarlightOp;
exports.checkDiaryRing = starlight.checkDiaryRing;
