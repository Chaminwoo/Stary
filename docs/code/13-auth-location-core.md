# 13. 인증 · 위치 · 언어/이름 해석 · 기타 core 유틸

Android: `feature/auth/GoogleAuthHelper.kt`, `feature/auth/screen/LoginScreen.kt`,
`core/util/LocationHelper.kt`, `LocalizedNames.kt`, `RelativeTime.kt`, `TestDataHelper.kt`,
shared `core/geo/GeoUtils.kt`
iOS: `Data/AuthManager.swift`, `Features/LoginView.swift`, `Core/LocationManager.swift`,
`Core/Geo.swift`, `Core/LocalizedNames.swift`, `Core/RelativeTime.swift`

---

## GoogleAuthHelper.kt — 인증 싱글턴

⚠️ **계정 식별자 규칙(전 플랫폼 공통)**: `userId = Google sub`(JWT subject) — FirebaseAuth uid 가
아니다. 익명 사용자만 FirebaseAuth uid 폴백. 같은 구글 계정 = Android/iOS 동일 유저(8.44 #7).

- `currentUserId` / `currentUserName` / `currentUserPhotoUrl` / `currentUserEmail` :
  로그인 사용자 정보(일반 var — Compose 관찰 불가. 관찰이 필요하면 AuthStateListener 를 붙인다.
  예: MainListScreen 의 userId 상태).
- `WEB_CLIENT_ID` : `secrets.properties → BuildConfig.GOOGLE_WEB_CLIENT_ID` 주입(하드코딩 금지).
- `signInWithGoogle(context)` : Credential Manager 구글 로그인 →
  FirebaseAuth `signInWithCredential` 교체 → userId=sub 세팅 → users 문서 upsert(+authUid 병행 기록)
  → 삭제 예약이 있으면 `cancelPendingDeletion`.
- `restoreSession()` : 영속 FirebaseUser 의 google.com providerData 에서 식별자 복원
  (앱 시작 시 MainActivity 가 호출 — 성공하면 로그인 화면 생략).
- `signOut(context)` : FirebaseAuth 로그아웃 + 익명 세션 재생성(Firestore 규칙 통과 유지).
- `applyStoredNickname(context)` / `setNickname(context, name)` : 커스텀 닉네임 반영/변경
  (메모리 + `users.userName` + NicknameStore 동시 갱신).
- `requestDeletion(context)` : **7일 유예 탈퇴 예약**(users 문서에 예약 기록 + authUid) —
  유예 내 재로그인 시 취소, 만료 시 서버 함수가 완전 삭제. `cancelPendingDeletion(uid)`.
- `getUserIdFromToken(idToken)` : JWT 에서 sub 파싱.

## LoginScreen.kt — 로그인 오버레이(라우트 아님)
- 무음 인트로 영상(`login_video.mp4`, 2.5x→감속) → 후광 로고 + 크림색 "Google 계정으로 로그인"
  캡슐 버튼 + "로그인 없이 둘러보기".
- `immediate=true`(로그아웃 복귀)면 영상 생략. `onVideoEnded` → MainScreen 이 지도 로드 시작
  (`contentReady`), `onLoginClick` → 로그인 진행 + 오버레이 닫기.

## LocationHelper.kt — 위치 싱글턴
- `location : StateFlow<LatLng?>` : 실시간 위치(null=fix 없음). 지도 마커/카메라/게이팅의 소스.
- `mockDetected` : GPS 스푸핑 앱 감지 — 조작 좌표는 거부 + UI 경고 1회.
- `cameraTarget` : (특수) 시작 시 카메라를 특정 좌표로 잡아달라는 1회 요청 슬롯.
- `setCurrentLocation(latLng)` : 수동 오버라이드(디버그 WASD 치트 등).
- `getCurrentLatLng()` : 현재 fix(없으면 null) — **100m 게이팅은 반드시 이걸로**(저장된 폴백 금지).
- `lastSavedLatLng(context)` : 지난 세션 마지막 실제 위치(초기 카메라 폴백용).
- `lastCameraState(context)` / `persistCameraState(...)` : 마지막 본 카메라(중심+줌) 저장·복원
  (카메라 idle 마다, 2초 스로틀) — 앱 시작 초기 카메라 = 마지막 보던 곳.
- `startContinuousUpdates(context)` / `stopContinuousUpdates` : FusedLocation 연속 업데이트.
- `getCurrentLocation(context)` : 일회성 fix 당기기(suspend).
- `distanceBetween(...)` : shared `GeoUtils`(Haversine) 위임.

## LocalizedNames.kt — "한국어 정의 데이터"의 표시 시점 번역
- 업적/칭호/음악 이름·국가명 등 데이터는 한국어로 정의돼 있고, 표시할 때 현재 로케일로 해석한다.
- `equippedTitle(context, id)` / `title(context, id, fallback)` / `countryName(code)` 등.
- 새 업적/음악 추가 시 여기(ko/en/ja 표시명)와 iOS `LocalizedNames.swift` 를 함께 갱신.

## RelativeTime.kt — 상대 시각 포맷("3분 전" 등). `format(epochMs)`.

## TestDataHelper.kt — 개발용 더미 데이터 삽입 헬퍼(릴리즈 미사용).

## shared core/geo — `LatLng`(공용 좌표), `GeoUtils.distanceBetween`(Haversine, m).

---

## iOS 대응
- `AuthManager.swift` : `isSignedIn`/`uid`(= **appUserId: Google sub 규칙**)/`displayName`.
  구글 로그인(GIDSignIn)+FirebaseAuth, `ensureProfile`(users upsert + authUid),
  `requestDeletion`(7일 유예 — 문서 id=sub, authUid 기록), 상태 리스너가 `appUserId(of:)` 사용.
- `LoginView.swift` : 인트로 영상 + 로그인 버튼(Android LoginScreen 과 동일 연출·자산).
- `LocationManager.swift` : `coordinate`(@Published), `coordinateOrDefault`(서울 폴백),
  `persistCameraState`/`lastSavedCoordinate`(⚠️ nonisolated — 지도 델리게이트에서 읽음).
  **시뮬레이터 빌드는 위치 업데이트 무시**(서울=건국대 고정 — 쿠퍼티노 기본값 덮어씀 방지, 8.44 #3).
- `Geo.swift` : `distanceMeters` — GeoUtils 패리티.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 계정 id 규칙(Google sub) | `GoogleAuthHelper.restoreSession` | `AuthManager.appUserId(of:)` (**규칙 동일 필수**) |
| 기본 좌표 폴백 | shared `StaryConfig.DEFAULT_LAT/LNG` | `AppConfig` + LocationManager 폴백 |
| 마지막 카메라 저장 스로틀(2s) | `LocationHelper.persistCameraState` | `LocationManager.persistCameraState` |
| 탈퇴 유예(7일) | `requestDeletion` + 서버 함수 | `AuthManager.requestDeletion`(동일 스키마) |
| 로그인 클라이언트 키 | secrets.properties GOOGLE_WEB_CLIENT_ID | `GoogleService-Info.plist`(+project.yml REVERSED_CLIENT_ID) |
