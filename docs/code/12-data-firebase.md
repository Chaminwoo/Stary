# 12. 데이터 계층 · Firebase · shared(KMP)

Android: `data/StaryFirestore.kt`, `data/repository/*`, `data/local/DiaryCache.kt`,
`feature/diary/DiaryViewModel.kt`, `core/util/UserDirectory.kt`
shared(KMP commonMain): `core/model/*`, `core/geo/*`, `shared/config/*`, `shared/data/repository/Repositories.kt`
iOS: `Data/FirestoreService.swift`, `DiaryStore.swift`, `Models.swift`, `ViewedStore.swift`,
`UserDirectory.swift`, `Moderation.swift`, `InviteStore.swift`, `PioneerStore.swift`, `ChatReadStore.swift`

---

## 연결 기본 — ⚠️ named DB
- Firebase 프로젝트: **momentdiary-f26c8**(포크 전용 — 원본 52b78 연결 금지, CLAUDE.md §0).
- Firestore DB 가 (default) 가 아니라 **named DB `stary-db`** —
  Android 는 반드시 `staryFirestore`(StaryFirestore.kt) 인스턴스를 쓴다
  (기본 인스턴스를 쓰면 NOT_FOUND 로 쓰기가 영원히 대기). iOS 는 `FirestoreService` 가 동일 처리.

## shared/config/StaryConfig.kt — 앱 공용 상수(**iOS AppConfig 와 값 동기화**)
- `FIRESTORE_DB_ID = "stary-db"` / `Collections`(diaries, comments, likes, notifications, users,
  users/{uid}/friends, friendRequests, users/{uid}/viewedDiaries, chats(+messages),
  users/{uid}/blocked, reports, hiddenAchievements, invites).
- `chatId(a,b)` : uid 정렬 결합 — 양쪽이 항상 같은 방을 가리키게.
- 게임 규칙 수치: `DIARY_OPEN_RADIUS_M=100` `DAILY_UPLOAD_LIMIT=10` `STAR_MERGE_RADIUS_M=30`
  `CHAT_DELETE_WINDOW_MS=60_000` `VIDEO_MAX_DURATION_MS=3_000` `INVITE_REDEEM_WINDOW_MS=7일`
  `NEARBY_ALERT_RADIUS_M=250`/`DAILY_LIMIT=5`/`MIN_INTERVAL=3분`.
- 입력 제한: 제목 30/본문 2000/댓글 300/채팅 500/닉네임 20.
- `ADMIN_EMAILS` / `isAdminEmail` : 어드민(히든 선점 제외 등).
- `DEFAULT_LAT/LNG` : 위치 폴백. `SHARE_BASE_URL`/`shareLink(id)`/`inviteLink(uid)` /
  `DEEP_LINK_SCHEME("stary")·HOST_DIARY·HOST_INVITE`.
- `shared/config/Secrets.kt` : 민감값 placeholder(하드코딩 금지). `PioneerQuest.kt` : 개척 대상국 로직.

## shared/core/model — 플랫폼 공용 모델(시간은 epoch millis Long)
- `Diary(id, userId, userName, isAnonymous, title, content, imageUrl, videoUrl(움짤/영상),
  latitude, longitude, createdAt, likeCount, commentCount, viewCount, starType, starColor,
  visibilityType)` — imageUrl/videoUrl 은 배타.
- `Comment` / `Like` / `AppNotification`(+NotificationType) / `Friend` / `ChatMessage` /
  (`UserProfile` 은 Android 쪽 모델) / `core/geo/LatLng`·`GeoUtils`(거리 계산).
- `shared/data/repository/Repositories.kt` : 플랫폼 구현이 따르는 인터페이스 모음
  (DiaryRepository/CommentRepository/LikeRepository/FriendRepository/NotificationRepository/
  ChatRepository/ViewedDiaryRepository).

## data/repository — Android Firestore 구현 (전부 `staryFirestore` 사용)
- `FirebaseDiaryRepository` : `observeAllDiaries()`(**createdAt DESC limit 1000**),
  save/update/delete, `observeMyDiaries(uid)`, `observeLatestVisibleDiaryOf(uid)`(친구 행 최근 별).
  Firestore ↔ 모델 변환(문서 id 를 doc.id 로 덮어씀 — id 필드 미저장 문서도 안전).
- `FirebaseCommentRepository` : observeComments/add/delete(+ diary commentCount 갱신, 알림 생성).
- `FirebaseLikeRepository` : observeIsLiked/observeLikeCount/toggle(+ likeCount, 알림 생성).
- `FirebaseNotificationRepository` : observeNotifications/observeUnreadCount/markAllRead/delete,
  `notifyFriendPost(...)`(업로드 시 친구들에게 FRIEND_POST 문서 생성).
- `FirebaseFriendRepository` : observeFriends/incoming/outgoing 요청, accept/decline/remove,
  `getProfile`/`upsertProfile`/`setEquippedTitle`/`getPinnedDiaries`/`setPinnedDiaries`, 닉네임 검색.
- `FirebaseChatRepository` : observeMessages(chatId)/send/delete, `observeMyChats(uid)`(방 메타 —
  인앱 채팅 배너·미읽음).
- `FirebaseViewedRepository` : observeViewedIds/markViewed(미조회 필터·조회 기록).
- `FirebaseModerationRepository` : observeBlockedIds/isBlocked/block/unblock/report.
- `FirebaseInviteRepository` : observeInvitedCount/observeRedeemed/`redeem(inviter, redeemer)` —
  결과 enum(SUCCESS/ALREADY/SELF/TOO_OLD/FAILED).
- `FirebasePioneerRepository` : observeClaims/claim(국가 선점 트랜잭션).
- `HiddenAchievementRepository` : observe/claim(트랜잭션 선착순)/releaseOwnedBy(어드민 자가치유).
- `UserRepository` : getProfileImageUrl/uploadProfileImage(Storage `profile_images/`).

## DiaryViewModel.kt — 다이어리 목록의 단일 소스
- `diaries` : 전체 목록 StateFlow(지도/리스트가 공유). 지도 호이스팅 후 액티비티 스코프 → 영구 유지.
- `event` : "저장 완료!" 등 결과 문구(업로드 화면이 collect).
- `saveDiary/updateDiary/deleteDiary(onSuccess)` / `getMyDiaries(uid)`(uid별 StateFlow 캐시) /
  `prefetchNearby(...)`(가까운 별 이미지 Coil 프리페치).

## 기타
- `DiaryCache`(data/local) : 다이어리 목록 로컬 캐시(콜드 스타트 즉시 표시용).
- `UserDirectory`(core/util) : uid → **현재** 이름/프사 전역 실시간 캐시.
  문서에 박힌 스냅샷 이름 대신 표시 시점 해석(닉네임 변경 반영). `rememberUserName(uid, fallback)` 류.

---

## iOS 대응
- `FirestoreService.swift` : named DB 연결 + 컬렉션 헬퍼(`FirestoreService.diaries/users/
  friends(of:)/friendRequests/...`) + `nowMillis`.
- `DiaryStore.swift` : Android DiaryViewModel 대응 ObservableObject —
  `observeAll` = **createdAt DESC limit 1000**(Android 동일), `mine(uid:)`, 에러 시 기존 목록 유지.
- `Models.swift` : 공용 모델 Codable — ⚠️ **@DocumentID 함정(8.44 치명 버그)**:
  커스텀 `init(from:)` 을 쓰면 `_id = (try? c.decode(DocumentID<String>.self, forKey: .id)) ?? 필드 폴백`
  으로 **id 를 반드시 명시 디코드**할 것(안 하면 iOS 생성 문서 id=nil → 좋아요/댓글/삭제가 조용히 실패).
  타입 드리프트 방어용 flexString/flexMillis 디코딩 사용.
- `ViewedStore` / `BlockStore`(Moderation.swift) / `UserDirectory` / `InviteStore` / `PioneerStore` :
  Android 대응 스토어(ObservableObject + 리스너).
- 계정 id 규칙(8.44 #7): **userId = Google sub**(providerData google.com 의 uid; 익명은
  FirebaseAuth uid 폴백) — `AuthManager.appUserId(of:)`. Android `restoreSession` 과 동일 규칙.
  같은 구글 계정 = OS 불문 같은 유저.

### 값 조절(패리티 매핑)
| 항목 | Android/shared | iOS |
|---|---|---|
| 모든 게임 수치·컬렉션명 | `StaryConfig.kt`(단일 출처) | `AppConfig.swift`(**복제 — 함께 수정**) |
| 목록 쿼리(정렬/limit) | `FirebaseDiaryRepository.observeAllDiaries` | `DiaryStore.observeAll`(동일 쿼리 유지) |
| 모델 필드 추가 | shared `core/model` + Android 변환부 | `Models.swift`(Codable + flex 디코딩) |
| 사용자 문서 스키마 | users/{uid}: userName·profileImageUrl·equippedTitle·pinnedDiaries·fcmToken·authUid | 동일 |
