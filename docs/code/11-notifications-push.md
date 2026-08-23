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
  `onFocusDiaryOnMap` → `MapFocusState.request(diaryId)`(지도 카메라+파장, 길찾기 없음) /
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
3. **수신자 `users/{uid}.fcmToken` 이 실제로 있나** — Console > Firestore(stary-db) 에서 직접 확인.
   - Android 로그: `GoogleAuthHelper: fcmToken 저장 완료 users/…`
   - iOS 콘솔: `✅ fcmToken 저장 완료 users/…` / `⚠️ FCM 토큰 발급 실패` / `⚠️ 알림 권한 거부됨`
   - ⚠️ 토큰은 **로그인/세션 복원 시** 저장한다 → 앱을 한 번 껐다 켜야 갱신되는 경우가 있다.
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
| 토큰 저장 | `GoogleAuthHelper.syncFcmToken` | `PushManager.setUser` |
