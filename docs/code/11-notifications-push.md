# 11. 알림 · 인앱 팝업 · FCM 푸시

Android: `feature/diary/screen/NotificationScreen.kt`, `feature/diary/NotificationViewModel.kt`,
`feature/diary/InAppPopupWatchers.kt`, `push/NotificationChannels.kt`, `push/StaryMessagingService.kt`
iOS: `Features/Notifications/NotificationsScreen.swift`, `NotificationsViewModel.swift`,
`Features/InAppWatcher.swift`, `Features/InAppBanner.swift`

알림은 3계층: ① Firestore 알림 문서(영구 목록) ② 인앱 배너(전면일 때) ③ FCM 시스템 푸시(후면/종료).
`AppForeground.isForeground` 로 ②③ 이중 표시를 방지한다.

---

## NotificationViewModel.kt
- `notifications : StateFlow<List<AppNotification>?>` : 알림 목록 실시간(**null=로딩 중** —
  인앱 팝업 감시기가 "최초 로드"를 구분하는 데 씀).
- `unreadCount` : 미읽음 수 — MainScreen 탑바 하트의 빨간 점.
- `markAllRead()` : 알림 화면 진입 시 전체 읽음. `delete(notificationId)` : 행 삭제.

## NotificationScreen.kt
- 알림 목록(타입별 아이콘/문구: ❤️ 좋아요 / 💬 댓글 / ⭐ 친구 새 글 / 🙋 친구 요청) + 상대 시각(RelativeTime).
- **차단한 사용자(actorId)의 알림은 숨긴다**(09 문서의 차단 규칙과 동일).
- 알림이 하나도 없으면 `StaryEmptyState`(골드 스파클 별 + `notif_empty`/`notif_empty_desc`, 02 문서).
- 행 탭: 다이어리 알림 → `onOpenDiary`(상세) / FRIEND_POST(친구 새 글) →
  `onFocusDiaryOnMap` → `MapFocusState.request(diaryId)`(지도 카메라+파장) /
  **FRIEND_REQUEST → `onOpenFriends`(친구 화면 = 받은 요청 목록)**.
- 미조회 다이어리 알림 행에는 FiberNew 아이콘 표시(8.25).

## 친구 요청 알림(FRIEND_REQUEST) — 8.45 신설
- 요청을 **보내는 쪽 클라이언트**가 `friendRequests/{id}` 를 만들 때 `notifications/{id}`(수신자=toId,
  diaryId 없음) 도 함께 만든다: Android `FirebaseFriendRepository.sendRequest` →
  `FirebaseNotificationRepository.notifyFriendRequest`, iOS `FriendsViewModel.notifyFriendRequest`.
  중복 요청(이미 pending)일 땐 만들지 않는다.
- 이 문서 하나로 3계층이 모두 동작한다: 목록 + 인앱 배너(InAppPopupWatchers/InAppWatcher) +
  푸시(Functions `notifyOnNotificationCreate` 의 FRIEND_REQUEST 분기).

## InAppPopupWatchers.kt — 전면 인앱 배너 감시기(둘 다 MainScreen 에 상주)
- `NotificationPopupWatcher(notifications, onOpen)` : 새 알림 도착 시 상단 배너.
  **최초 구독 시점 알림은 기준선으로만 잡고 띄우지 않는다**(앱 켤 때 과거 알림 폭주 방지).
  같은 알림 반복 배너는 key 로 dedup(8.25). `AppSettings.notificationsEnabled=false` 면 억제.
- `ChatPopupWatcher(userId, suppressChatWith, onOpenChat)` : 내 채팅방 메타(observeMyChats) 관찰 —
  마지막 메시지가 내 것이 아니고 updatedAt 증가 시 배너. **지금 보고 있는 방이면 생략**
  (`suppressChatWith` = 현재 Chat 라우트의 friendId).

## push/ — FCM
- `NotificationChannels.kt` : `ensureStaryNotificationChannel(context)` — heads-up(높은 중요도) 채널
  사전 생성(Application.onCreate). 종료 상태 수신 알림도 상단 배너로 뜨게.
- `StaryMessagingService.kt` : FCM 수신 서비스.
  - data 메시지 { diaryId | friendId, ... } → 시스템 알림 생성(전면이면 인앱 배너가 대신하므로 생략).
  - 알림 탭 → MainActivity extras(`diaryId`/`chatFriendId`/**`openFriends`**) → `DeepLinkState` →
    상세/채팅방/**친구 화면** 딥링크(친구 요청 푸시는 `type=FRIEND_REQUEST` 로 판별).
  - 토큰 갱신 시 `users/{uid}.fcmToken` 저장.
  - ⚠️ 토큰 최초 등록은 `GoogleAuthHelper.syncFcmToken(uid)` — **로그인 + 세션 복원(앱 재시작) 양쪽**에서 호출한다
    (예전엔 로그인 화면을 실제로 거친 순간에만 저장해서, 로그인이 유지되는 기기는 토큰이 낡은 채 방치될 수 있었다).
  - ⚠️ **실제 발송은 Cloud Functions 필요**(친구 새 글/채팅 메시지 → 상대 fcmToken 으로 data 발송).
    Functions 미배포 환경에선 인앱(전면) 알림만 동작한다.

---

## iOS 대응
- `NotificationsViewModel/Screen` : 목록/미읽음/전체 읽음 — Android 와 같은 문서 스키마·행 구성.
- `InAppWatcher.swift` : NotificationPopupWatcher+ChatPopupWatcher 통합판 —
  MainTabView 가 `startWatcher()` 로 시작, 배너 탭 → `chatTarget`/`diaryTarget` push.
- `InAppBanner.swift` : 상단 배너 호스트(`InAppBannerHost` — MainTabView ZStack).
- **`Data/PushManager.swift`(8.45 신설) : 원격 푸시(FCM/APNs)** — Android StaryMessagingService 대응.
  - `configure()`(AppDelegate.didFinishLaunching) : Messaging/UNUserNotificationCenter 델리게이트 연결.
  - `setUser(uid)`(RootView onAppear + auth.uid 변경) : 알림 권한 요청 → `registerForRemoteNotifications`
    → 토큰 수신 시 `users/{uid}.fcmToken`+`authUid` 저장(Android syncFcmToken 과 같은 필드).
  - 전면 수신 = `willPresent` 에서 `[]` 반환(시스템 배너 억제 — 인앱 배너가 담당, Android 정책 동일).
  - 탭 = `PushRouter.shared`(pending) → RootView `.onReceive` → 채팅/지도 포커스/친구 화면.
    (`onReceive` 는 구독 시 현재 값도 받으므로 앱이 꺼진 상태에서 알림으로 실행된 경우도 처리됨.)
  - 앱 설정: `project.yml` 의 `UIBackgroundModes: remote-notification` +
    `entitlements`(iosApp/Stary.entitlements, `aps-environment`) + SPM `FirebaseMessaging`.
  - ⚠️ **Firebase 콘솔에 APNs 인증 키(.p8) 등록 + 유료 Apple Developer 계정 필수** — 없으면 토큰만 생기고
    실제 발송이 되지 않는다. 시뮬레이터는 원격 푸시 불가(실기기 필요).

---

## 일일 알림(오늘 기록 유도) — 2026-09-23 개편: 시간대·문구 둘 다 랜덤

Android `push/DailyReminderScheduler.kt` + `DailyReminderReceiver.kt` + `res/values*/reminder_messages.xml`
iOS `Data/DailyReminderScheduler.swift` + `Data/DailyReminderMessages.swift`

**왜 바꿨나**: 예전엔 12~22시 균일 랜덤 + **시간대별 고정 문장 1개**(`daily_reminder_lunch` 등 4개)라
며칠만 써도 문구가 외워졌다("또 그 문장") → 알림을 안 읽게 된다. 시간과 문장을 **같이** 흔든다.

- **밴드(시간대) 5구간** — 낮 11:00 ~ 밤 23:30. 아침은 일부러 비워 둔다.
  `NOON` 11:00–13:30 / `AFTERNOON` 13:30–17:00 / `EVENING` 17:00–19:30 / `NIGHT` 19:30–22:00 / `LATE` 22:00–23:30.
  **직전에 쓴 밴드를 빼고** 고른다 → 같은 시간대가 이틀 연속 오지 않는다. 그 안에서 분·초는 랜덤.
- **문구 풀**: 밴드마다 6문장(ko/en/ja). **직전에 쓴 문장을 빼고** 랜덤. 고른 위치는 밴드별로 prefs 에 저장.
- prefs/UserDefaults 키: `daily_reminder_scheduled_at`(예약 시각) · `daily_reminder_last_band` ·
  `daily_reminder_last_msg_<밴드>`.
- **문구를 고르는 시점이 플랫폼마다 다르다**: Android 는 알람이 **울릴 때**(저장된 밴드로 풀을 찾음 —
  Doze 로 늦게 울려도 의도한 시간대 문장이 나간다), iOS 는 로컬 알림 내용이 미리 확정돼야 해서 **예약할 때**.
  ⚠️ Android 리시버는 **알림을 먼저 띄우고 그다음 재예약**해야 한다 — `scheduleNext` 가 `last_band` 를 덮어쓴다.
- 문장을 추가/수정할 때는 **Android XML 과 iOS `DailyReminderMessages.table` 을 같은 순서로** 함께 고친다.

## 푸시가 안 올 때 — 확인 순서(경로가 길어서 반드시 위에서부터)

메시지 1건이 기기에 뜨기까지: **문서 생성 → Functions 트리거 → 수신자 fcmToken 조회 → FCM 발송 →
(Android: 채널/알림권한 / iOS: APNs 키·권한) → 표시**. 한 칸만 비어도 **조용히** 아무 일도 안 일어난다.

1. **Functions 가 배포돼 있나** — `firebase deploy --only functions` (규칙 배포와 별개다).
   Console > Functions 에 `notifyOnChatMessage`/`notifyOnNotificationCreate`/`notifyFriendsOnDiaryCreate` 가 보여야 한다.
2. **Functions 로그** — `firebase functions:log` 또는 Console.
   - `chat {chatId}: A → B 푸시 시도` 가 없다 → 트리거 자체가 안 걸림(배포/DB(stary-db)/리전 확인).
   - `fcmToken 없음 → 발송 생략` → **수신자 앱이 토큰을 저장 못 한 상태**(3번).
   - `발송 실패 (…)` → APNs 키 미등록(iOS)·토큰 만료 등. 코드가 그대로 찍힌다.
   - `발송 성공 …` → 서버는 끝. 기기 쪽 문제(4번).
3. **수신자 토큰이 실제로 있나** — Console > Firestore(stary-db) 에서 직접 확인.
   - `users/{uid}/fcmTokens/{token}` : **기기별**(문서 id = 토큰, 2026-08-25 신설). 로그인한 기기 수만큼 있어야 한다.
   - `users/{uid}.fcmToken` : 예전 단일 필드(구버전 앱/함수 호환). 둘 다 없으면 서버가 조용히 건너뛴다.
   - Android 로그: `GoogleAuthHelper: fcmToken 저장 완료 users/…`
   - iOS 콘솔: `✅ fcmToken 저장 완료 users/…` / `⚠️ FCM 토큰 발급 실패` / `⚠️ 알림 권한 거부됨`
   - ⚠️ 토큰은 **로그인/세션 복원 시** 저장한다 → 앱을 한 번 껐다 켜야 갱신되는 경우가 있다.
   - ⚠️ **한 기기에서 계정을 바꿔가며 테스트했다면** 예전 계정 문서에 이 기기 토큰이 남아 있을 수 있다.
     지금은 로그아웃 시 떼어내지만(`clearFcmToken` / `PushManager.clearToken`), 그 전에 생긴 찌꺼기는
     Console 에서 직접 지워야 한다. 서버가 보내는 `recipientId` 로 앱이 남의 알림은 무시한다.
4. **기기 조건**
   - Android: 알림 권한(13+), 채널 `stary_default`(앱 실행 시 생성), 배터리 최적화 예외.
   - iOS: **실기기 필수**(시뮬레이터는 원격 푸시 불가), Push Notifications 권한(entitlement),
     Firebase 콘솔에 **APNs 인증 키(.p8)** 등록, 알림 권한 허용.
   - 전면(포그라운드)에서는 **일부러 시스템 배너를 막고** 인앱 배너를 띄운다(양 플랫폼 동일 정책) —
     백그라운드/종료 상태로 테스트할 것.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 배너 노출 시간 | `InAppBanner.kt` BannerVisibleMs=4000 | `InAppBanner.swift` |
| 알림 팝업 on/off | `AppSettings.notificationsEnabled` | `AppSettings.shared` |
| 알림 문구 | `strings.xml` + `notificationTitle(n)` | `L10n` + `AppNotification.displayText/emoji` |
| 딥링크 키 | MainActivity EXTRA_* | `PushManager` userInfo(diaryId/chatFriendId/type) — 같은 키 |
| 토큰 저장 | `GoogleAuthHelper.syncFcmToken` / `registerFcmToken` | `PushManager.setUser` → `saveTokenIfPossible` |
| 토큰 해제(로그아웃) | `GoogleAuthHelper.clearFcmToken`(signOut 안에서) | `PushManager.clearToken(for:)`(AuthManager.signOut 안에서) |
| 기기별 토큰 컬렉션 | shared `StaryConfig.Collections.FCM_TOKENS` | `AppConfig.Collections.fcmTokens` (**같은 값**) |
| 남의 계정 알림 무시 | `StaryMessagingService` `recipientId` 비교 | `PushManager` `userInfo["recipientId"]` 비교 |
| 일일 알림 밴드(11:00~23:30, 5구간)·문구 풀·on-off | `push.DailyReminderScheduler.Band`(AlarmManager) + `reminder_messages.xml` + `AppSettings.dailyReminderEnabled` | `DailyReminderMessages.Band`(UNCalendarNotificationTrigger) + `DailyReminderMessages.table` + `AppSettings.shared.dailyReminderEnabled` — **경계·문장 동일 유지** |
| 일일 알림 재예약 시점 | 알림 발사 시(`DailyReminderReceiver`) + 재부팅(`BootReceiver`) — 앱이 안 켜져 있어도 시스템이 깨워 재예약 | 앱 시작(`AppDelegate`) + 포그라운드 복귀(`RootView` scenePhase) — **iOS 는 로컬 알림이 앱을 안 깨우므로 한동안 앱을 안 열면 다음날 재예약이 밀릴 수 있음(구조적 차이)** |
| 일일 알림 탭 목적지 | `MainActivity.EXTRA_OPEN_UPLOAD` → `DeepLinkState.uploadNonce` | `PushRoute.upload` → `DrawerDest.upload` |
