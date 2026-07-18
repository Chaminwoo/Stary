# 01. 앱 구조 · 내비게이션

Android: `MainActivity.kt`, `StaryApplication.kt`, `feature/home/screen/MainScreen.kt`,
`navigation/NavGraph.kt`, `navigation/NavRoute.kt`, `core/util/`(전역 브리지)
iOS: `StaryApp.swift`, `Features/RootView.swift`, `Features/Map/MapFocusStore.swift`(TabRouter 포함),
`Features/Map/MapChromeState.swift`

---

## MainActivity.kt — 앱 진입 액티비티

역할: 단일 액티비티. 로케일 적용 → 세션 복원 → 권한 요청 → 딥링크 해석 → `MainScreen` 표시.

- `attachBaseContext(newBase)` : 저장된 인앱 언어를 **모든 리소스 해석 전에** 적용(`LocaleManager.wrap`).
  언어 변경 기능이 이상하면 여기와 `LocaleManager` 부터 본다.
- `onCreate()` 순서 :
  1. `GoogleAuthHelper.restoreSession()` — 영속 세션 복원(있으면 로그인 화면 생략).
  2. `GoogleAuthHelper.applyStoredNickname(this)` — 기기에 저장된 커스텀 닉네임을 표시명에 반영.
  3. 위치(FINE/COARSE)·알림(API 33+) 권한을 **켜자마자** 요청 — 늦으면 초기 지도가 기본 좌표로 뜬다.
  4. `handleDeepLinkIntent(intent)` — 아래 참고.
  5. `setContent { StaryTheme { MainScreen(initialDiaryId, initialChatFriendId) } }`
- `onNewIntent(intent)` : 앱이 살아있을 때 알림 탭(manifest `launchMode="singleTop"`) → 딥링크 재해석.
- `handleDeepLinkIntent(intent)` : 딥링크 3종을 전역 상태에 등록만 한다(이동은 MainScreen 이 수행).
  - 푸시 extras(`diaryId`/`chatFriendId`) → `DeepLinkState.request(...)`
  - `stary://diary/{id}` → `MapFocusState.request(id)` — **상세 직행이 아니라 지도 포커스**(100m 게이팅 우회 방지).
  - `stary://invite/{uid}` → `DeepLinkState.requestInvite(uid)` — 로그인 후 리딤.
- `EXTRA_DIARY_ID` / `EXTRA_CHAT_FRIEND_ID` / `EXTRA_CHAT_FRIEND_NAME` : FCM 서비스가 알림 인텐트에 싣는 extra 키.

## StaryApplication.kt — Application

- `newImageLoader()` : 앱 전역 Coil 이미지 로더(이미지 로딩 속도 튜닝의 핵심).
  - `respectCacheHeaders(false)` — Firebase Storage 의 보수적 캐시 헤더 무시, 항상 디스크 캐시.
  - 메모리 25% + 디스크 256MB, `crossfade(120)`, GIF 디코더 포함(부메랑 움짤).
- `onCreate()` :
  - `ensureStaryNotificationChannel(this)` — heads-up 알림 채널 사전 생성(11 문서).
  - `registerActivityLifecycleCallbacks` → `AppForeground.onResumed()/onPaused()` 갱신.
  - Firebase Auth 세션이 없으면 **익명 로그인**(Firestore 규칙 `request.auth != null` 통과용).

## MainScreen.kt — 홈 프레임(탑바 + 드로어 + 지도 + NavHost + 오버레이)

역할: 앱의 껍데기. 지도(`MainListScreen`)를 **NavHost 뒤 상시 레이어**로 깔고,
그 위에 NavHost(하위 화면), 그 위에 로그인/코치마크/배너/토스트 오버레이를 쌓는다.

### 상태/변수
- `navController` : `rememberNavController()` — 단일 NavHost 컨트롤러.
- `currentRoute : NavRoute` : 현재 백스택 목적지를 NavRoute 로 매핑한 값. 탑바 제목/버튼 분기에 사용.
- `alreadyLoggedIn` : 진입 시점에 세션이 이미 있는지(있으면 로그인 오버레이 생략).
- `showLogin` : 로그인 **오버레이** 표시 여부(로그인은 라우트가 아님 — 지도를 뒤에서 미리 렌더).
- `contentReady` : 지도(하단 레이어+NavHost) 로드 시점 제어 — 로그인 인트로 영상이 먼저 시작된 뒤 true.
- `loginImmediate` : 로그아웃으로 돌아온 경우 인트로 영상 생략 여부.
- `isMapRoute` / `wasMapRoute` : 지도(Main) 라우트 전환 감지 쌍. 전환 시
  ① `MapUiState.mapVisible` 갱신(가려짐 → 마커 애니 루프 휴면),
  ② **비지도 → 지도 복귀**면 `MapUiState.requestRecenter()` 로 "카메라만 내 위치로" 요청.
  단 `MapFocusState.pendingDiaryId != null`(포커스/길찾기 예약) 또는 `MapUiState.routeActive`(경로 따라가는 중)면 건너뛴다.
- `drawerState` / `coroutineScope` : 좌측 드로어 개폐.
- `onboardPrefs` / `showOnboarding` : 첫 실행 코치마크 1회 노출(SharedPreferences `stary_onboarding`).
- `userId` : `GoogleAuthHelper.currentUserId` — null 이면 비로그인(둘러보기).
- `notifVm : NotificationViewModel?` : 로그인 시에만 생성. `unreadCount`(하트 빨간점), `notifList`(인앱 팝업 감시용).
- `onNavigate(route)` : 드로어 항목 탭 → `popUpTo(start){saveState}` + `launchSingleTop` + `restoreState` 로 이동.
- `onLogout` : 로그인 오버레이 복귀 + 백스택 전체 리셋(`popUpTo(0)`) + `GoogleAuthHelper.signOut`.

### 화면 구성(컴포넌트 연결)
- `ModalNavigationDrawer` 에 드로어 시트(0x111111, 우측 라운드 24)를 넘겨 좌측 메뉴를 만든다.
  항목: 내 다이어리/프로필/업적/배경음악/친구/설정 + 로그인 또는 로그아웃(`DrawerItem`).
- `Scaffold.topBar` = `CenterAlignedTopAppBar`. 분기:
  - 루트(지도)면 햄버거, 아니면 뒤로가기(`navigateUp`).
  - 지도 라우트면 우측 하트(알림) + `unreadCount > 0` 시 빨간 점.
  - 내 프로필이면 `ProfilePinState` 의 + 버튼(핀 별 picker 열기).
  - 타인 프로필이면 `UserProfileActionState` 의 친구 추가/친구✓/요청됨 버튼 + ⋮(신고/차단) 메뉴.
  - 채팅/타인 프로필 제목 옆에 `HiddenStarBadges`(히든 업적 배지).
- Scaffold 본문 : `if (contentReady) Box { MainListScreen(...); NavGraph(...) }`
  - **`MainListScreen`(지도)를 NavHost "뒤"에 깔아** 화면 전환에도 파괴되지 않게 한다(별 깜빡임 제거).
  - `MainListScreen` 에 `onItemClick→navigateToDetail`, `onOpenCluster→StarCluster`, `onCreateClick→Upload` 를 넘긴다.
- 오버레이(위에서 아래 순): `StaryToastHost` > `InAppBannerHost` > `StarBirthHost`(별 탄생 연출)
  > `MapOnlyOverlay`(몰입 종료 X) > `MainOnboardingOverlay`(코치마크) > `LoginScreen`(로그인).
- 감시자(로그인 시): `AchievementUnlockWatcher`, `HiddenAchievementWatcher`,
  `NotificationPopupWatcher`(FRIEND_POST 탭 → `MapFocusState.request` 후 지도 복귀), `ChatPopupWatcher`.
- `localizedTitle(route)` : 라우트 → 번역된 탑바 제목(동적 제목인 Chat 은 `route.title` 그대로).
- `DrawerItem(label, icon, selected, alwaysAccent, danger, onClick)` : 드로어 한 줄(선택=남색, 위험=빨강).

## NavRoute.kt — 타입세이프 라우트 정의

- `NavRoute` : sealed class. 각 라우트가 `title`(탑바 제목) / `isRoot`(햄버거 vs 뒤로가기) /
  `showTopBar` / `showFab` 를 갖는다. 새 화면 추가 시 여기부터.
- 라우트 목록: `Main`(지도) `Upload` `Friends` `MyDiary` `Profile` `Achievements` `Music` `Settings`
  `Detail(diaryId)` `Chat(friendId, friendName)` `Notification` `UserProfile(userId, userName)`
  `StarCluster(ids)` `UserDiaryStars(userId, userName)` `Login`(미사용 — 로그인은 오버레이).
- `NavHostController.navigateToDetail(diaryId)` : **백스택에 Detail 을 항상 1개만 유지**
  (`launchSingleTop` + `popUpTo<Detail>{inclusive}`) — 알림 연타로 상세가 겹겹이 쌓이지 않게.

## NavGraph.kt — NavHost 와 화면 전환

- 전환 상수: `ENTER_MS=320`, `EXIT_MS=300`, `ENTER_SCALE=0.93`, `EXIT_SCALE=1.06` — "깊이감 줌"(fade+scale).
  업로드만 예외로 아래→위 슬라이드(모달 느낌).
- `composable<NavRoute.Main>` : **빈 투명 화면.** 지도는 MainScreen 이 NavHost 뒤에 상시 렌더하므로
  이 라우트는 "지도가 보이는 상태" 표시일 뿐이고 터치는 아래 지도로 통과한다.
- "지도로 돌아가 별 포커스" 패턴(4곳: Friends 행 별, Profile 핀 별, UserProfile 핀 별, UserDiaryStars 별):
  ```kotlin
  MapFocusState.request(diaryId, withRoute = true)   // 길찾기 포함
  navController.navigate(NavRoute.Main) { popUpTo<NavRoute.Main> { inclusive = true } }
  ```
  Notification 의 `onFocusDiaryOnMap` 만 `withRoute` 없이 카메라+파장만.
- `Detail.onBack` 도 지도 복귀(`popUpTo Main inclusive`) — 깊은 스택을 접고 지도로.

## 전역 상태 브리지 (core/util) — 화면끼리 직접 못 부를 때 쓰는 싱글턴

### MapUiState
- `mapOnly` : 몰입(지도만 보기) — true 면 탑바/필터/FAB 전부 숨김. `enterMapOnly()/exitMapOnly()`.
- `mapVisible` : 지도(Main 라우트)가 화면에 보이는지 — MainScreen 이 갱신.
  DiaryMap 의 20fps 마커 애니 루프가 이 값이 false 면 휴면(GPU 절약).
- `routeActive` : 도보 길찾기 경로 활성 여부 — DiaryMap 이 갱신. 활성 중엔 복귀 재센터 생략.
- `recenterNonce` / `requestRecenter()` : "카메라만 내 위치로" 요청 카운터.
  MainScreen(라우트 전환)이 발급 → DiaryMap 이 `lastRecenterNonce` 와 비교해 1회 소비.

### MapFocusState
- `pendingDiaryId` : 지도에 "이 다이어리로 카메라+파장" 요청(null=없음).
- `pendingRoute` : true 면 파장 후 그 별까지 도보 길찾기 경로도 띄운다.
- `request(diaryId, withRoute)` / `consume()` : 요청/소비. 소비는 지도(DiaryMap 파장 종료 시).

### UserProfileActionState
- 타인 프로필 화면의 친구/신고/차단 액션을 **MainScreen 탑바**에서 그리기 위한 브리지.
- `visible`(본인 아닐 때만) / `isFriend` / `requested` / `isBlocked` : 탑바 아이콘 상태.
- `onClick`(친구 추가·취소) / `onReport` / `onToggleBlock` : UserProfileScreen 이 진입 시 등록,
  이탈 시 `reset()`.

### ProfilePinState
- `visible` / `onOpen()` : 내 프로필의 "별 올리기(핀 picker)" 열기를 탑바 + 버튼에 연결.

### DeepLinkState
- `diaryId` / `chatFriendId·chatFriendName` / `inviterId` : 푸시·초대 딥링크 목적지 보관.
- `request(...)` / `requestInvite(...)` : MainActivity 가 등록.
- `consumeDiary()` / `consumeChat()` / `consumeInvite()` : MainScreen 의 LaunchedEffect 가 1회 소비 후 이동.

### AppForeground
- `isForeground` : 앱 전면 여부(Application 이 갱신). FCM 시스템 알림 vs 인앱 배너 이중 표시 방지,
  DiaryMap 애니 루프 휴면 판정에 사용.

---

## iOS 대응

### StaryApp.swift — 진입점
- `@UIApplicationDelegateAdaptor(AppDelegate.self)` + `FirebaseApp.configure()`.
- `configureNavigationBarAppearance()` : push 화면의 시스템 내비바를 Android 탑바 톤으로 통일
  (배경 0x0D0D0D 불투명, 제목 PoorStory 20 / 0xF0F0F0). **탑바 색/폰트를 바꾸려면 여기.**
- `onOpenURL` : `stary://diary/{id}` → `TabRouter.go(map)` + `MapFocusStore.request(id)`,
  `stary://invite/{uid}` → `InviteStore.handleDeepLink`, 그 외 → 구글 로그인 콜백.

### RootView.swift — 로그인 게이트 + MainTabView(=Android MainScreen)
- `RootView` : `auth.isSignedIn` ? `MainTabView` : `LoginView`. `.id(locale.language)` 로
  언어 변경 시 전체 트리 재생성(Android `recreate()` 대응). 앱 기본 폰트 `.poorStory(16)` 지정.
- `MainTabView` 상태:
  - `store: DiaryStore` / `location: LocationManager` / `viewed` / `blocks` / `notifications` : 전역 데이터 (12·13 문서).
  - `router = TabRouter.shared` : 구 탭 시절 `go(tab)` 호출부 호환 — map=루트 pop, 그 외=push.
  - `focus = MapFocusStore.shared` : 포커스 요청 오면 `path = NavigationPath()` 로 전부 pop(지도 복귀).
  - `chrome = MapChromeState.shared` : 글로브/몰입 시 상단바·FAB 숨김.
  - `path : NavigationPath` : 단일 NavigationStack 스택. `DrawerDest`(myDiary/profile/achievements/
    music/friends/settings/notifications/upload) enum 을 push.
  - `chatTarget` / `diaryTarget` : 인앱 배너 탭 → 채팅/상세 push 용 별도 isPresented 목적지.
  - `drawerOpen` : 커스텀 드로어(스크림 0.5 + 좌측 300pt 패널 0x111111).
- 구성: `NavigationStack { ZStack { (topBar + MapScreen) + fab + drawerLayer + InAppBannerHost + StarBirthHost } }`
  — **지도가 NavigationStack 루트**라 push 되어도 파괴되지 않는다(Android 호이스팅과 동일 효과).
- `topBar` : 햄버거 / 중앙 "지도" / 하트(+빨간점 `notifications.unread`).
- `destinationView(dest)` : DrawerDest → 실제 화면 매핑(새 화면 추가 시 여기).

### TabRouter / MapFocusStore / MapChromeState (Features/Map/)
- `TabRouter.go(tab)` : `(tab, nonce)` 발행 — MainTabView 가 onChange 로 해석.
- `MapFocusStore.request(diaryId, withRoute)` : Android `MapFocusState` 패리티.
  같은 id 재요청도 잡히도록 nil → id 순서로 발행. `consume()` 은 MapScreen 이.
- `MapChromeState` : `hidden`(글로브) / `mapOnly`(몰입) / `chromeHidden`(둘 중 하나).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 화면 전환 연출 | `NavGraph.kt` ENTER/EXIT 상수 | NavigationStack 기본 push(별도 상수 없음) |
| 드로어 폭/색 | `MainScreen.kt` ModalDrawerSheet(0x111111, 라운드 24) | `RootView.swift` drawerPanel(300pt, 0x111111, 라운드 24) |
| 글쓰기 FAB 그라데이션 | `MainScreen.kt`/`DiaryMap.kt` (0x3A4676→0x111936, 56dp) | `RootView.swift` fab (같은 hex, 56pt) |
| 지도 복귀 재센터 | `MainScreen.kt` isMapRoute 효과 + `MapUiState.requestRecenter` | `MapScreen.swift` `.onAppear` 의 `rootAppearedOnce` 블록 |
| 탑바 높이/제목 폰트 | CenterAlignedTopAppBar + MinSans 18 | topBar(56pt) + PoorStory 20, push 화면은 `StaryApp.configureNavigationBarAppearance()` |
| 딥링크 스킴/호스트 | `shared StaryConfig.DEEP_LINK_*` | `AppConfig.deepLinkScheme/HostDiary/HostInvite` (**값 동일 유지**) |
