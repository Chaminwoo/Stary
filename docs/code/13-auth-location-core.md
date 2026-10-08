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
- 무음 인트로 영상(`login_video.mp4`, 2.5x→감속) → 후광 로고 + **크림 캡슐** "Google 계정으로 로그인"
  버튼 + "로그인 없이 둘러보기".
- **장식은 `LoginDecor.kt` 로 분리**(2026-10-08 로그인 다듬기, iOS `LoginDecor.swift` 와 같은 값):
  - `LivingSky(visible)` — **영상이 멈춘 뒤에도 하늘이 살아 있게**: 잔별 84개(그중 6개는 짧은 빛줄기가 붙는 큰 별, 반지름 0.45~1.1 / 큰 별 0.9~1.5)가 숨 쉬듯 반짝이고(`k = 0.40 + 0.60·tw`),
    7.5초 주기 안의 무작위 시각에 0.95초짜리 유성이 우상단→좌하단으로 스친다(첫 유성은 1.8초 뒤). 별은 **화면 위 60% 에만**(`SKY_MAX_Y`) —
    영상의 지구(화면 약 71% 아래)와 푸른 테두리 빛을 가리지 않는다. Android 는 영상 `STATE_ENDED`(또는 `immediate`)에, iOS 는 로그인 UI 가 뜨는 시점(영상 종료 2초 전)에 켠다.
    시간은 상태로 들고 draw 단계에서만 읽어 재구성 없이 다시 그리기만 한다. 보이지 않는 동안 프레임 루프도 쉰다.
  - **로고 숨쉬는 빛** — 후광 로고의 알파 0.62~0.92 / 크기 ±3% 가 3.8초 주기로 맥동(인트로 후광 폭 애니메이션 뒤에도 계속). iOS 는 `repeatForever` 로 Core Animation 이 돌린다.
  - **로고 글린트** — 약 5.2초 간격으로 1.3초 동안 비스듬한 빛줄기(폭 ±0.13, 기울기 dy 0.45)가 로고 글자·별 위만 훑는다
    (Android `Modifier.logoGlint` = `SrcAtop` + 오프스크린 레이어, iOS `LogoGlintOverlay` = 로고 알파 마스크).
  - **큰 별 모양(2026-10-08 2차 "너무 부자연스러워 — 작고 자연스럽게")**: 처음엔 딱 떨어지는 반투명 **원판** 후광(4.2r) + 긴 십자선(5~10r)이라 회색 판에 십자 표시처럼 보였다 →
    후광을 **방사형 그라데이션**(3.4r, 알파 0.22 → 0)으로, 빛줄기를 **짧고 가늘게**(3.0~5.6r, 굵기 0.38r, 알파 0.6, 세로는 가로의 70%)로. 일반 별도 더 작게·알파 0.8.
  - `CreamCapsuleButton` — **예전 크림 캡슐(`StarDiaryButton`)로 되돌린 모습**: 크림 그라데이션(`F7EDD8`→`E9D6AE`, 알파 0.94 — 아주 살짝 비침) + 위에서 아래로 약해지는 1dp 하이라이트 테두리
    + **진한 숯색 글자/★(`2C2723`)**. 누르면 0.98배로 작아지고 면이 밝아지며 **터치 down 순간 진동**(Android `Haptics.light`, iOS `Haptics.soft`). 뒤 후광은 0.62(iOS 0.50).
    (1차였던 "글래스 — 투명 흰 면 + 흰 글씨"는 지구 위에서 회색빛으로 흐려 글씨가 안 읽혀 같은 날 되돌렸다. 영상 위에서 읽혀야 하는 글자는 밝은 면 + 어두운 글씨가 가장 안전.)
    `StarDiaryButton`(core/ui)은 로그인에서 더 이상 쓰지 않는다 — 다른 곳에서 안 쓰이면 삭제해도 된다.
- **로고 화질(2026-10-08)**: `logo.webp` 가 `res/drawable`(밀도 구분 없음)에 있어 안드로이드가 기기 밀도(예: 450dpi)만큼 **확대해서 디코드**했다
  (1536×1024 → 4320×2880, 약 50MB) 한 뒤 다시 618px 로 줄여 그려 먼지 같은 잔별과 글자 가장자리가 거칠었다. → `res/drawable-nodpi/logo.webp`(1152×768)로 옮기고
  `rememberLogoBitmap()` 이 `inScaled=false` + `setHasMipMap(true)` 로 읽는다. 에셋은 `tools/logo/make_logo.py` 가 무손실 마스터(`tools/logo/logo_master_1536.png`)에서
  프리멀티플라이드 2배 확대 → 언샤프 → 축소로 만든다(Android 238KB, iOS PNG 577KB — 예전 1.7MB). 로고를 바꾸려면 마스터를 교체하고 스크립트를 다시 돌릴 것.
  ⚠️ `res/drawable` 에 둔 다른 큰 이미지(`image_frame`·`mydiary_bg`·`mypage_bg`·`upload_bg`·`app_image` 등 1MB 안팎 webp)도 같은 확대 디코드 대상이다 — 후속 점검 후보.
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
- `LoginView.swift` : 인트로 영상 + 로그인 버튼(Android LoginScreen 과 동일 연출·자산). 하늘/글린트/크림 버튼은 `LoginDecor.swift`.
  별 배치·유성은 `JavaRandom`(시드 20261008 / 주기 번호) 으로 Android 와 **같은 난수 호출 순서** — 순서를 바꾸면 양쪽 배치가 달라진다(`JavaRandom.nextInt(bound)` 는 이 때문에 추가).
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
| 로그인 하늘(별 수 84·큰 별 6·y ≤ 0.60·유성 7.5s/0.95s) | `LoginDecor.kt` `SKY_*` / `METEOR_*` | `LoginDecor.swift` `LoginSky` (**동일 값·동일 난수 순서**) |
| 로고 맥동(3.8s, 알파 0.62~0.92, ±3%) / 글린트(5.2s 간격, 1.3s) | `LoginScreen.kt` breatheT · `logoGlint` | `LoginView.swift` `startBreathing` · `LogoGlintOverlay` (**동일 값**) |
| 로그인 버튼(크림 면·테두리·숯색 글자·터치 진동) | `CreamCapsuleButton` + `Haptics.light` | `CreamCapsuleButton` + `Haptics.soft` (**색·테두리 값 동일**) |
| 로고 에셋 | `res/drawable-nodpi/logo.webp` | `Sources/Resources/logo.png` (같은 픽셀 — `tools/logo/make_logo.py`) |
