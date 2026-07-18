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
- 알림 목록(타입별 아이콘/문구: 좋아요/댓글/친구 요청/친구 새 글) + 상대 시각(RelativeTime).
- 행 탭: 다이어리 알림 → `onOpenDiary`(상세) / FRIEND_POST(친구 새 글) →
  `onFocusDiaryOnMap` → `MapFocusState.request(diaryId)`(지도 카메라+파장, 길찾기 없음).
- 미조회 다이어리 알림 행에는 FiberNew 아이콘 표시(8.25).

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
  - 알림 탭 → MainActivity extras(`diaryId`/`chatFriendId`) → `DeepLinkState` → 상세/채팅방 딥링크.
  - 토큰 갱신 시 `users/{uid}.fcmToken` 저장.
  - ⚠️ **실제 발송은 Cloud Functions 필요**(친구 새 글/채팅 메시지 → 상대 fcmToken 으로 data 발송).
    Functions 미배포 환경에선 인앱(전면) 알림만 동작한다.

---

## iOS 대응
- `NotificationsViewModel/Screen` : 목록/미읽음/전체 읽음 — Android 와 같은 문서 스키마·행 구성.
- `InAppWatcher.swift` : NotificationPopupWatcher+ChatPopupWatcher 통합판 —
  MainTabView 가 `startWatcher()` 로 시작, 배너 탭 → `chatTarget`/`diaryTarget` push.
- `InAppBanner.swift` : 상단 배너 호스트(`InAppBannerHost` — MainTabView ZStack).
- ⚠️ iOS 원격 푸시(APNs)는 미구현 — 인앱 배너만. Android 와 다른 점 유의.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 배너 노출 시간 | `InAppBanner.kt` BannerVisibleMs=4000 | `InAppBanner.swift` |
| 알림 팝업 on/off | `AppSettings.notificationsEnabled` | `AppSettings.shared` |
| 알림 문구 | `strings.xml` + `notificationTitle(n)` | `L10n` + NotificationItem 구성(패리티) |
| 딥링크 키 | MainActivity EXTRA_* | (APNs 도입 시 동일 키 사용 권장) |
