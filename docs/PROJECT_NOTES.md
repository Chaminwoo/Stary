# PROJECT_NOTES.md — Stary-Project 코드 분석 / 작업 핸드오프

> 목적: **다음 작업 시 코드를 처음부터 다시 읽지 않고** 바로 시작할 수 있도록 구조·연동·결정사항을 정리.
> 업데이트 규칙: 빌드+테스트 성공 때마다 갱신(자세한 건 `CLAUDE.md` 참고).
> 최종 갱신: **기능 배치 3**(업로드 별모양/색상 무한 캐러셀, 지도 필터 스피드다이얼 FAB, 맵 워터마크 제거)
> 이전: **기능 배치 2**(파장 애니메이션, 공개범위, 나만보기/친구선택 필터, 별자리, 배경음악, 마이페이지 별 모양)
> 이전: **기능 배치 1**(별 마커 5종×12색 Path 렌더, 친구, 미조회/친구 필터, 별 선택 업로드, FRIEND_POST 인앱 알림)
> + **named DB(stary-db) 연결 + firebase-bom 33.7.0 + Firebase Auth(Google/익명)** + 크래시 방어.
> ⚠️ 배경음악: `androidApp/src/main/res/raw/ambient_music.mp3` 파일 추가 필요. 없으면 버튼만 보이고 토스트 안내.
> 이전: MapLibre+MapTiler 전환, applicationId 분리(`com.chaminwoo.stary_ios`), Firebase `momentdiary-f26c8`.

---

## 1. 개요
- 앱: "Stary" — 지도 기반 위치 다이어리. 지도에 별(star) 마커로 다이어리가 뜨고, **100m 이내**에서만 열람 가능.
  좋아요/댓글/알림, Google 로그인, 프로필 이미지 업로드 기능.
- 원본: Android 전용(Jetpack Compose + Firebase + 네이버맵).
- 이 분기: **Android + iOS 확장형 KMP** 구조 + **MapLibre + MapTiler 지도**(구 네이버/Google Maps 대체) + 민감값 주입.
- 코드 패키지(namespace): `com.chaminwoo.stary` (androidApp), 공용은 동일 패키지 재사용 + `com.chaminwoo.stary.shared.*`.
- ⚠️ **applicationId = `com.chaminwoo.stary_ios`** (namespace와 다름). 원본 앱 `com.chaminwoo.stary`와 충돌/Firebase 분리를 위해 분기. 액티비티 풀네임은 여전히 `com.chaminwoo.stary.MainActivity`.

## 2. 기술 스택
- Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Compose BOM 2024.09.00, minSdk 26 / compileSdk 36(.1).
- Firebase: Firestore(**named DB `stary-db`** — `StaryConfig.FIRESTORE_DB_ID`, 모든 접근은 `data/StaryFirestore.kt`의
  `staryFirestore` 사용. 기본 `Firebase.firestore` 금지: (default) DB 없음→NOT_FOUND), Storage,
  **FirebaseAuth(Google signInWithCredential + 비로그인 익명)**, firebase-bom **33.7.0**(named DB API 필요).
- 지도: **MapLibre GL Native 11.11.0**(`org.maplibre.gl:android-sdk`, Google Maps 대체) + MapTiler 벡터 타일(OpenMapTiles v3), `play-services-location`.
- 기타: Coil(이미지), android-gif-drawable(로그인 GIF), kotlinx-serialization-json, kotlinx-coroutines.

## 3. 모듈 / 소스 트리
```
:shared (KMP)                         com.android.kotlin.multiplatform.library + kotlin.multiplatform
  commonMain/
    core/model/        Diary, Comment, Like, AppNotification, NotificationType  (순수 Kotlin, createdAt: Long)
    core/geo/          LatLng(공용 좌표), GeoUtils(Haversine distanceBetween)
    shared/platform/   Platform (expect class) + describePlatform()
    shared/config/     StaryConfig(컬렉션명/반경/기본좌표 상수), Secrets(민감값 인터페이스 계약)
    shared/data/repository/Repositories.kt
                       DiaryRepository / CommentRepository / LikeRepository / NotificationRepository (인터페이스)
  androidMain/ shared/platform/Platform.android.kt   (actual, android.os.Build)
  iosMain/     shared/platform/Platform.ios.kt       (actual, UIDevice) — macOS에서만 컴파일

:androidApp (com.android.application + AGP 내장 Kotlin + kotlin.compose)
  com/chaminwoo/stary/
    MainActivity.kt            ComponentActivity → StaryTheme { MainScreen() }
    StaryApplication.kt        (네이버 init 제거됨; Firebase 자동초기화)
    navigation/NavGraph.kt, NavRoute.kt    화면 라우팅. onLocationClick → LocationHelper.cameraTarget(공용 LatLng)
    core/designsystem/         Color, Theme(StaryTheme), Type
    core/ui/StaryComponents.kt DiaryCard 등 공통 컴포저블 (createdAt 포맷)
    core/util/
      LocationHelper.kt        FusedLocation 기반 현재위치/연속추적, 공용 LatLng, 거리계산은 GeoUtils 위임
      ImageUploadHelper.kt     Firebase Storage 업로드 (diary_images/*)
      TestDataHelper.kt        seed() — 전국 장소 더미 다이어리 생성(테스트용)
    data/local/DiaryCache.kt   메모리 캐시(id→Diary)
    data/repository/
      Firebase{Diary,Comment,Like,Notification}Repository.kt  ← 공용 인터페이스의 Firestore 구현
      UserRepository.kt        프로필 이미지 URL get/upload (android.net.Uri 사용 → 공용 인터페이스 미적용, Android 전용)
    feature/
      auth/GoogleAuthHelper.kt + screen/LoginScreen.kt   Google 로그인, WEB_CLIENT_ID=BuildConfig 주입
      home/screen/MainScreen.kt, MainListScreen.kt        메인/지도 홈
      map/screen/DiaryMap.kt                              ★지도 본체 (MapLibre + MapTiler, AndroidView)
      diary/DiaryViewModel.kt, InteractionViewModel.kt, NotificationViewModel.kt
      diary/screen/DetailScreen.kt, UploadScreen.kt, NotificationScreen.kt
      profile/ProfileViewModel.kt + screen/MyScreen.kt
```

## 4. 데이터 모델 (commonMain, 모두 순수 Kotlin data class)
- `Diary(id,userId,userName,isAnonymous,title,content,imageUrl,latitude,longitude,createdAt:Long,likeCount,commentCount,viewCount,starType,starColor,visibilityType)`
- `Comment(id,diaryId,userId,userName,content,createdAt:Long)`
- `Like(userId,userName,createdAt:Long)`
- `AppNotification(id,type,diaryId,diaryTitle,diaryOwnerId,actorId,actorName,content,createdAt:Long,isRead)`
- `NotificationType { LIKE, COMMENT }`
- ⚠️ **createdAt 은 epoch millis(Long)**. (원본은 Firebase `Timestamp`였음.) 생성 시점은 Firebase 구현부에서
  `System.currentTimeMillis()` 로 설정. 화면 포맷은 `java.util.Date(createdAt)`.
  Firestore `toObject()` reflection 으로 매핑되므로 필드명이 Firestore 문서와 일치해야 함.

## 5. Firestore 구조 (StaryConfig.Collections)
- `diaries/{id}` : 다이어리. 하위 컬렉션 `comments/{id}`, `likes/{userId}`.
- `notifications/{id}` : `diaryOwnerId` 로 조회, `isRead` 로 미읽음 카운트.
- `users/{userId}` : `profileImageUrl`.
- 좋아요/댓글은 batch/transaction 으로 카운트(`likeCount`/`commentCount`) 동시 갱신 + 알림 생성.
- Storage: `diary_images/{uuid}.jpg`, `profile_images/{userId}.jpg`.

## 6. 지도 (MapLibre + MapTiler) 핵심
- `DiaryMap(diaries, currentLatLng:공용LatLng, isFollowing, onGestureDetected, onRefollowClick, onItemClick, onCreateClick)` — `feature/map/screen/DiaryMap.kt`.
- 엔진: **MapLibre GL Native**. Compose는 `AndroidView`로 `MapView` 래핑 + `rememberMapViewWithLifecycle()`(생명주기 연결). `MapLibre.getInstance()`는 MapView 생성 전 1회. 좌표 변환 `LatLng.toMl()`.
- 스타일: `res/raw/maplibre_style.json`(자체 작성). 소스=MapTiler `tiles/v3?key=__MAPTILER_KEY__`(BuildConfig.MAPTILER_KEY 치환). 레이어 = **background / water(fill) / road-major(line)만** → 건물·POI·라벨은 아예 없음(다운로드·렌더 안 함 = 경량).
- 큰 길만: road-major `filter` = transportation `class` ∈ {motorway,trunk,primary,secondary,tertiary}. `minzoom`(현재 8)으로 저줌에선 길 숨김(바다+땅만).
- 줌 색 보간: background/water/road `paint` 색이 `["interpolate",["linear"],["zoom"],6,<줌아웃색>,16,<상세색>]` 로 줌6↔16 부드럽게 변함.
- 내 위치: GeoJSON source(`current-location`) + CircleLayer. "내 위치로" FAB = 카메라 이동.
- **다이어리 별 마커**: GeoJSON source(`diaries`) + SymbolLayer(`diary-stars`).
  - 아이콘 = `StarStyle.starPath`(5종: 십자/5각/6각/8각/대각 스파클 — 스파클은 오목 quad 곡선으로 꼭지 날카롭게)
    × 12색, 글로우(blur)+본체+흰 하이라이트로 비트맵 생성(`starBitmap`), 사용 조합만 `style.addImage`.
  - ⚠️ **PNG(star_1~5)를 마커로 쓰지 말 것** — 에뮬레이터에서 PNG→GL 텍스처가 대각선 빗금으로 깨짐. Path 렌더 유지.
  - ⚠️ 비트맵은 정사각+4의 배수 변(현재 160px). addImage 는 기기밀도로 나눠 표시(화면크기 ≈ 160/density × iconSize).
  - near(100m 이내) = feature bool 속성 → iconSize 확대 + pulse, 전체 float 애니메이션(50ms 루프 setProperties).
  - 클릭: queryRenderedFeatures → 100m 이내 열람 / 밖 거리 토스트. (길찾기 기능은 사용자 요청으로 삭제)
- **별가루 파티클**: GeoJSON source(`star-particles`) + SymbolLayer 4개(`star-particles-0..3`, phaseGroup 필터).
  - Compose Canvas(`StarParticleOverlay`) 는 **삭제됨** — 실좌표 마커라 카메라 동기화 코드 불필요, 컬링은 MapLibre 가 담당.
  - 시드 42 고정, 400개를 초기 currentLatLng 반경 20km 면적 균등 분포로 1회 생성(이후 setGeoJson 없음).
    feature 속성: phase / twinkleSpeed / depth(0.5~1.0 크기 배율) / phaseGroup.
  - 아이콘 = 24px 흰 점(글로우+코어) 비트맵. iconSize = 줌 보간(6→0, 10→0.4, 15→0.8) × depth.
    iconOpacity = 줌 보간(6→0, 10→twinkle) — **줌 6 이하 완전 숨김**(사용자 튜닝: 8→6).
  - 반짝임 = 기존 50ms 애니메이션 루프에서 레이어 4개의 iconOpacity 만 위상/주기 달리 갱신(GeoJSON 재생성 금지).
- 초기 카메라(현재 위치 중심 / `LocationHelper.cameraTarget` 경계)는 DiaryMap이 style 로드 시 처리.
- ⚠️ 키 없으면(placeholder) 타일 안 뜸. `secrets.properties`의 `MAPTILER_KEY` 필요.

## 7. 민감값 주입 배선 (하드코딩 없음)
- `secrets.properties`(루트, gitignore) 에서 읽음. 파일/키 없으면 build.gradle 의 `?:` 기본 placeholder 사용.
  - ⚠️ 과거의 `secrets.defaults.properties` / `secrets.properties.example` 는 **삭제됨**(실제 키 혼입 우려 + gitignore 추가). 폴백 로직은 `takeIf{exists}` 라 없어도 무방.
- `androidApp/build.gradle.kts` 가 `secrets.properties` 읽어서:
  - `MAPTILER_KEY` → `buildConfigField` → `BuildConfig.MAPTILER_KEY` → `res/raw/maplibre_style.json`의 `__MAPTILER_KEY__` 치환.
  - `GOOGLE_WEB_CLIENT_ID` → `buildConfigField` → `BuildConfig.GOOGLE_WEB_CLIENT_ID` → `GoogleAuthHelper` 사용.
  - (구 `MAPS_API_KEY` / Google geo `API_KEY` 메타데이터는 MapLibre 전환으로 제거됨.)
- **Firebase 프로젝트**: 이 포크 = `momentdiary-f26c8`(번호 7962996464) / 앱 `com.chaminwoo.stary_ios`.
  원본(연결 금지) = `momentdiary-52b78` / `com.chaminwoo.stary`.
- `google-services.json`(androidApp/, gitignore): f26c8 실파일 사용 중. **로그인 3종(json·웹클라ID·SHA-1)은 반드시 같은 프로젝트(f26c8).**
  - 웹 클라이언트 ID(`secrets.properties` GOOGLE_WEB_CLIENT_ID)는 json 의 `client_type:3` 값(`7962996464-...`)이어야 함. 다른 프로젝트 ID 넣으면 28444.
  - 지도 타일 안 뜨면: `secrets.properties`의 `MAPTILER_KEY` 확인(placeholder면 타일 미표시).
- 디버그 SHA-1: `F3:48:0A:53:FA:F3:EF:D7:60:1D:E7:A2:CA:EA:37:9C:E2:DE:A5:D0`.

## 8. 빌드 시스템 특이점 (재확인용)
- `settings.gradle.kts`: `:shared`, `:androidApp` 포함. 네이버 maven 저장소 제거.
- `gradle/libs.versions.toml`: 네이버·Google Maps 제거, **MapLibre**(`org.maplibre.gl:android-sdk`)·KMP·coroutines.
- `:shared` → `com.android.kotlin.multiplatform.library` + `kotlin { android { } }` + iOS framework(baseName "Shared").
- `:androidApp` → `kotlin.android` 명시 금지(AGP 내장 Kotlin과 충돌). AppCompat 의존성 명시 추가
  (themes.xml 이 `Theme.AppCompat.Light.NoActionBar` 상속 — 과거 네이버 의존성이 transitive 로 제공하던 것).
- `gradle.properties`: `kotlin.native.ignoreDisabledTargets=true`, `android.useAndroidX=true`.

## 8.5 기능 배치 1 (이번 라운드 추가 — 테스트는 콘솔 규칙 해제 후)
- **친구**: `shared` `FriendRepository`/`Friend`/`FriendRequest`/`UserProfile` + `FirebaseFriendRepository`
  (users/{uid}/friends 양방향, friendRequests 컬렉션, userName prefix 검색) + `feature/friend/` FriendScreen/ViewModel
  + NavRoute.Friends(드로어 "친구"). 로그인 시 `upsertProfile` 로 users/{uid} 공개 프로필 기록(검색용, fire-and-forget).
- **별 선택 업로드**: Diary += `starType`(0~4)/`starColor`(0~11). UploadScreen 피커(StarShapeIcon=마커와 동일 Path).
- **필터**: MainListScreen 칩 "미조회만"(users/{uid}/viewedDiaries — DetailScreen 진입 시 기록) / "친구만"(friends 기준).
- **FRIEND_POST 인앱 알림**: NotificationType.FRIEND_POST. saveDiary 성공 시 친구들에게 알림 문서 생성(fire-and-forget).
  푸시(FCM)는 Cloud Functions 필요 — 미구현(체크리스트 7/8).
- **안정화**: 스냅샷 리스너 `close(error)` 금지(권한 에러 크래시 방지), 로그인/저장 경로의 Firestore 부수 작업은
  전부 fire-and-forget, GIF 인트로 속도 상향.
- **위치 보기 버튼 삭제**(DetailScreen) — 100m 밖은 지도에서 거리 토스트만.
- **(라운드 2)** 로그인 = MainScreen **오버레이**(NavHost start=Main, 지도 선로딩 → 로그인 직후 즉시 표시),
  마커 위상 그룹 4개(따로 부유), iconSize 줌 보간(8→0.3x~15→1x), 팔레트 흰색 30% 혼합(밝게),
  팬/줌 중 애니메이션 일시정지 + GeoJSON 변화시에만 재생성(끊김 해소).
- **(라운드 3)** 별가루 파티클을 Compose Canvas(`StarParticleOverlay`) → **MapLibre GeoJSON+SymbolLayer 전환**(6절 참고).
- **FCM 클라이언트**: `push/StaryMessagingService`(data {diaryId,title,body} → 알림), 알림 탭 →
  `MainActivity` extra → Detail 딥링크, 토큰은 `users/{uid}.fcmToken`. **발송은 Cloud Functions 배포 필요**(체크리스트 8).
- **FCM 서버(코드 완료, 배포 대기)**: 루트 `firebase.json`/`.firebaserc`(default=momentdiary-f26c8) +
  `functions/`(node 20, firebase-admin 12 / firebase-functions 6 v2 API).
  `notifyFriendsOnDiaryCreate` = diaries onCreate(**database: stary-db** 명시) → 친구 fcmToken 수집(`db.getAll`) →
  `sendEachForMulticast`(500개 청크, android priority high) → 만료 토큰(`registration-token-not-registered`) 은
  users/{uid}.fcmToken 필드 삭제로 정리. ⚠️ `REGION`(현재 asia-northeast3)은 stary-db 리전과 일치 필수.
  배포: Blaze + `cd functions && npm install` + `firebase deploy --only functions`.

## 8.7 기능 배치 4 (BUILD SUCCESSFUL + 테스트 완료)
- **이미지 업로드 안정화/원인 추적**: `ImageUploadHelper` 가 업로드 직전 `ensureAuthenticated()`(세션 없으면
  `signInAnonymously().await()`)로 Auth 세션 보장 → Storage 규칙(`request.auth != null`) 통과. 실패 시
  실제 에러 메시지를 `Result(url,error)` 로 반환(기존엔 null 만 → 원인 묻힘). `UserRepository.uploadProfileImage` 도
  동일하게 세션 보장. `ProfileViewModel` 에 `uploadError` StateFlow 추가 → `ProfileScreen` 에서 토스트로 노출.
  - ⚠️ 경로의 userId 는 Google sub(JWT)라 Firebase uid 와 다름 → Storage 규칙에서 `auth.uid == userId` 쓰면 안 됨.
- **Storage 보안 규칙 파일화**: 루트 `storage.rules`(diary_images/profile_images = 읽기공개 + 로그인+이미지<10MB 쓰기,
  그 외 거부) + `firebase.json` 에 `"storage": {"rules":"storage.rules"}`. 배포: `firebase deploy --only storage`.
  - ⚠️ 원본 앱(momentdiary-52b78)은 Firebase Auth 세션을 안 만들어(익명/credential 로그인 없음) `request.auth` 항상 null.
    이 규칙을 원본에 적용하면 업로드 전부 거부됨 → 원본은 콘솔 버전기록 롤백 또는 오픈 규칙 필요(원본 코드 수정 금지).
- **미열람 알림 빨간 점**: `MainScreen` 하트 BadgedBox 배지를 민트 숫자 → 빨간 동그라미 점(0xFFFF3B30, 어두운 테두리).
- **커스텀 토스트**: `core/ui/StaryToast.kt` — 시스템 Toast(Android 12+ setView 무시) 대신 Compose 전역 오버레이
  `StaryToastHost`(MainScreen 최상단, 로그인 오버레이 포함 위). 남색 그라데이션+PoorStory 폰트. 호출은 `StaryToast.show(msg)`.
  기존 `Toast.makeText` 10곳 전부 교체(Profile/Login/MainList/Friend/DiaryMap/Upload).
- **앱 아이콘**: `AndroidManifest` icon/roundIcon → `@drawable/app_image`. (런처에 따라 사각 PNG 그대로 보일 수 있음;
  어댑티브 마스킹 원하면 별도 작업 필요.)
- **토스트 확장**: 댓글 작성/삭제·좋아요(하트)·칭호 장착/해제에도 `StaryToast` 적용(DetailScreen/AchievementsScreen).
- **미열람 알림 빨간 점 버그픽스**: ⚠️ Kotlin `Boolean isRead` 는 Firestore 에 **`read`** 필드로 저장됨(getter "is" 접두 제거).
  쿼리/업데이트가 `"isRead"` 였어서 항상 0건 → `read` 로 수정(`FirebaseNotificationRepository`). 빨간 점 위로 살짝(-2dp)+테두리 제거.
- **스플래시 완전 검정**: `values/themes.xml` windowBackground=검정, `values-v31/themes.xml` 시스템 스플래시 배경 검정 +
  아이콘 숨김(`drawable/splash_icon_none` 투명). 콜드스타트 흰 번쩍임 제거.
- **내 다이어리 배경**: `MyDiaryScreen` 을 Box 로 감싸 `drawable/mydiary_bg`(업로드와 동일 밝기) 깔음.
- **정렬 효과음**: `MusicManager.playWind()`(`res/raw/wind.mp3`, 배경음악과 별개 SFX 플레이어, enabled 시만). 정렬 변경 시 호출.
- **바나나 다이얼 수정**: 드래그 중엔 회전만, 선택 이벤트는 놓을 때/클릭 시·이전과 다를 때만(onDragCancel 추가).
- **거리순 수정**: `DiaryStarBox.here` 가 위치 캐시 null 이면 `getCurrentLocation` 비동기 측정해 채움(이전엔 거리순 무반응).
- **별자리 실제 배치**: `MyDiaryScreen.CONSTELLATIONS` = 최신순 사수자리(Teapot)/인기순 처녀자리/거리순 전갈자리(Scorpius).

## 8.6 기능 배치 3 (BUILD SUCCESSFUL 확인됨)
- **UploadScreen 무한 캐러셀**: 별 모양/색상 피커를 `HorizontalPager`(pageCount=10_000, initialPage=5000-based)로 교체.
  - 중앙 외 페이지: `graphicsLayer(scale/alpha)` 로 페이드+축소 효과. `contentPadding` 으로 양쪽 미리보기.
  - 별 모양 아이콘: `StarShapeIcon`(56px 박스+RoundedCorner18) 선택 시 mint 테두리/배경. 색상: CircleShape 원형 슬롯.
- **MainListScreen 필터 스피드 다이얼**: 기존 수평 칩 Row 제거 → 좌측 하단 원형 FAB + `AnimatedVisibility`(expandVertically).
  - 5가지 옵션 pill(전체보기/미조회만/친구만/나만보기/친구선택). 선택된 필터는 mint 색상 강조.
  - FAB 자체도 활성 필터 있으면 mint 테두리로 표시.
  - `private FilterOpt` data class로 옵션 정의(ImageVector 사용).
- **MapLibre 워터마크 제거**: `map.uiSettings { isLogoEnabled=false; isAttributionEnabled=false }`.

## 8.7 기능 배치 4 — 다이얼/별자리/업적 해금 (BUILD SUCCESSFUL)
- **내 다이어리 별자리**: `MyDiaryScreen.CONSTELLATIONS` 를 `drawable/reference{1,2,3}.png`(미사용 참고 이미지, 미커밋) 픽셀
  분석으로 옮긴 `CStar(x,y,mag)`+edges 로 교체. 정렬별 색 = 최신순 파랑/인기순 분홍/거리순 보라(`sortColor`).
  별마다 후광 pulse(무한 twinkle) + `onSelect` 시 전체 번쩍(flash 1.7→0.78) `sortNonce` 연동.
- **바나나 다이얼**: 원호→포물선(`DIAL_H_SPACING/CURVE/BOTTOM`). 드래그 방향 반전, 세 버튼 모양 구분(`dialStarType`).
  - **터치 영역 = 별자리 박스 전체**(`matchParentSize`), `DIAL_BOTTOM_DP=100` 으로 별이 박스 안에 들어와 아래쪽 탭도 인식.
  - 선택은 `!=selected` 가드 제거(기본 최신순 재선택도 동작) + `sortNonce` 로 같은 정렬 재선택도 재정렬.
- **wind SFX**: `MusicManager` 효과음을 `MediaPlayer`→`SoundPool`(미리 로드, USAGE_MEDIA) 로 교체(지연/묵음 해결).
- **친구 화면**: `FriendScreen` 카드형 리팩토링(아바타 링, pill 버튼, 배경).
- **별 모양/색 업적 해금**: `Achievement.reward` = `Reward.Title|Shape|StarColor` 로 칭호 업적과 별·색 업적 **분리**.
  `StarUnlocks` 는 보상 정의에서 자동 도출. 업로드 피커의 잠긴 항목은 흐릿+자물쇠, 탭/저장 시 해금 토스트.
  - 새 통계: `UserStats.maxSpanMeters/maxLikesOnOne/distinctDays/nightPosts`(`rememberUserStats` 가 좌표·시각으로 계산).
  - 창의적 업적: 친구 N명/기록 거리(50km·1000km)/심야 기록/서로 다른 N일 등.
- **창의적 별 모양**(`StarStyle` TYPE_COUNT=8): 5=꽃 / 6=보석 / 7=초승달(반시계 22° 회전). 0~4 별/스파클 유지.
- **그라데이션 색**(COLOR_COUNT=20): 16~19 2색 그라데이션(`fillShader` LinearGradient) — 지도·내다이어리·카드·피커 일관 적용.
  가장 어려운 업적(좋아요 300/친구 20/100개 작성/조회 1000)에 배치.
- **업적 화면**: `AchievementsScreen` 「칭호」/「별 모양·색」 2섹션, 보상 미리보기. 배경 = `mydiary_bg`(0.7 darken).

## 9. 남은 작업 / TODO (다음에 할 것)
- [ ] iOS 앱(Xcode 프로젝트) 추가 + iOS용 Repository 구현(Firebase iOS SDK) — 현재 `shared` 스캐폴딩만(iosX64/Arm64/Sim 타깃만, iosApp/.xcodeproj 없음). **iOS 빌드·실행은 macOS+Xcode 필요(Windows 불가).**
- [x] 실제 `secrets.properties` / `google-services.json`(f26c8) 채워 런타임 확인 — 지도·Google 로그인 동작 확인됨.
- [x] 지도 엔진 Google Maps → **MapLibre + MapTiler** 전환 + 커스텀 스타일(검정/물/큰길, 줌 색보간) — 동작 확인.
- [x] 다이어리 별 마커(종류0~4×색0~11) 커스텀 렌더 + 클릭 100m 게이팅 — 완료(길찾기는 사용자 결정으로 미구현/삭제).
- [x] 별가루 파티클 Canvas → MapLibre GeoJSON+SymbolLayer 전환(줌 6 이하 숨김) — 완료.
- [ ] **FCM 푸시 발송 Function 배포(사용자)**: Blaze + `cd functions && npm install` + `firebase deploy --only functions`
      (코드는 `functions/index.js` 완료, REGION=stary-db 리전 확인).
- [ ] ViewModel 들이 Firebase* 구현 대신 공용 인터페이스 타입을 주입받도록 DI 정리(현재는 직접 생성).
- [x] GitHub remote(`origin` = Chaminwoo/Stary) 연결 + 푸시 완료(main).

## 10. 빠른 네비게이션 (기능 → 파일)
| 하고 싶은 일 | 파일 |
|---|---|
| 지도/스타일/마커 수정 | `feature/map/screen/DiaryMap.kt`, `res/raw/maplibre_style.json`, `feature/home/screen/MainListScreen.kt` |
| 다이어리 CRUD/쿼리 | `data/repository/FirebaseDiaryRepository.kt` (+ 인터페이스 `shared/.../Repositories.kt`) |
| 좋아요/댓글/알림 | `FirebaseLikeRepository`, `FirebaseCommentRepository`, `FirebaseNotificationRepository`, `InteractionViewModel` |
| 로그인/인증 | `feature/auth/GoogleAuthHelper.kt`, `LoginScreen.kt` |
| 좌표/거리 공용 로직 | `shared/.../core/geo/LatLng.kt`, `GeoUtils.kt`, `core/util/LocationHelper.kt` |
| 상수/설정/민감값 계약 | `shared/.../shared/config/StaryConfig.kt`, `Secrets.kt` |
| 키/시크릿 주입 | `androidApp/build.gradle.kts`, `secrets.properties`(MAPTILER_KEY / GOOGLE_WEB_CLIENT_ID) |
