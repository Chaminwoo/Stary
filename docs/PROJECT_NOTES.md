# PROJECT_NOTES.md — Stary-Project 코드 분석 / 작업 핸드오프

> 목적: **다음 작업 시 코드를 처음부터 다시 읽지 않고** 바로 시작할 수 있도록 구조·연동·결정사항을 정리.
> 업데이트 규칙: 빌드+테스트 성공 때마다 갱신(자세한 건 `CLAUDE.md` 참고).
> 최종 갱신: **지도 엔진 Google Maps → MapLibre GL Native + MapTiler 전환** + 커스텀 다크 스타일(검정 배경/물/큰 길만, 줌 색보간). (installDebug BUILD SUCCESSFUL, 에뮬레이터 동작 확인.) 이전: applicationId 분리(`com.chaminwoo.stary_ios`) + Firebase `momentdiary-f26c8` + 시크릿 템플릿 제거.

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
- Firebase: Firestore, Storage, Auth(Google ID Token via Credential Manager), firebase-bom 26.2.0.
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
- `Diary(id,userId,userName,isAnonymous,title,content,imageUrl,latitude,longitude,createdAt:Long,likeCount,commentCount,viewCount)`
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
- 줌 색 보간: background/water/road `paint` 색이 `["interpolate",["linear"],["zoom"],6,<줌아웃색>,16,<상세색>]` 로 줌6↔16 부드럽게 변함(줌아웃=어둡게, 줌인=덜 어둡게).
- 내 위치: GeoJSON source(`current-location`) + CircleLayer. `currentLatLng` 변경→`setGeoJson` 갱신, follow면 `animateCamera`. 제스처 감지=`addOnCameraMoveStartedListener` REASON_API_GESTURE → follow 해제.
- 초기 카메라(현재 위치 중심 / `LocationHelper.cameraTarget` 경계)는 DiaryMap이 style 로드 시 처리(과거 MainListScreen의 maps-compose 카메라 로직 제거).
- ⚠️ 다이어리 별 마커/클러스터/100m 게이팅은 **현재 제거됨**(내 위치·길·물만). 별 종류(0~4)×색(0~11) 커스텀 마커 + 100m·도보 길찾기로 재구현 예정(체크리스트 A-3/기능2·4).
- ⚠️ 키 없으면(placeholder `TODO_FILL_MAPTILER_KEY`) 타일 안 뜸. `secrets.properties`의 `MAPTILER_KEY` 필요.

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

## 9. 남은 작업 / TODO (다음에 할 것)
- [ ] iOS 앱(Xcode 프로젝트) 추가 + iOS용 Repository 구현(Firebase iOS SDK) — 현재 `shared` 스캐폴딩만(iosX64/Arm64/Sim 타깃만, iosApp/.xcodeproj 없음). **iOS 빌드·실행은 macOS+Xcode 필요(Windows 불가).**
- [x] 실제 `secrets.properties` / `google-services.json`(f26c8) 채워 런타임 확인 — 지도·Google 로그인 동작 확인됨.
- [x] 지도 엔진 Google Maps → **MapLibre + MapTiler** 전환 + 커스텀 스타일(검정/물/큰길, 줌 색보간) — 동작 확인.
- [ ] 다이어리 별 마커(종류0~4×색0~11) 커스텀 렌더 재구현 + 클릭 100m 게이팅 + 100m 밖 도보 길찾기(체크리스트 A-3/기능2·4).
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
