# Stary-Project (KMP 분기본)

> ⚠️ **이 프로젝트는 새 분기(fork)이며, 기존 운영 프로젝트와 완전히 분리되어 있습니다.**
> 원본: `C:\Users\User\AndroidStudioProjects\mobile-project-Chaminwoo\Stary`
> 이 분기는 원본의 DB / 운영 환경 / 배포 설정 / 키 / 저장소 연결을 **공유하지 않습니다.**
> 모든 민감값(API 키, Firebase 설정, OAuth, 지도 키)은 하드코딩되어 있지 않고 **TODO/플레이스홀더**로 비워져 있습니다.

원본 Android 전용 앱을 **Android + iOS 확장 가능한 Kotlin Multiplatform(KMP) 구조**로 재편성하고,
**네이버맵(Naver Map) → Google Maps** 로 교체한 버전입니다.

---

## 1. 모듈 구조

```
Stary-Project/
├─ shared/                         # KMP 공용 모듈 (Android + iOS)
│  └─ src/
│     ├─ commonMain/               # 플랫폼 비종속 공용 코드
│     │  ├─ core/model/            # Diary, Comment, Like, AppNotification (순수 Kotlin)
│     │  ├─ core/geo/              # LatLng(공용 좌표), GeoUtils(Haversine 거리)
│     │  ├─ shared/platform/       # expect class Platform
│     │  ├─ shared/config/         # StaryConfig(상수), Secrets(민감값 계약)
│     │  └─ shared/data/repository # Repository 인터페이스(공용 데이터 계약)
│     ├─ androidMain/              # actual Platform (Android)
│     └─ iosMain/                  # actual Platform (iOS)
└─ androidApp/                     # Android 전용 앱 (Compose UI + Firebase + Google Maps)
   └─ src/main/java/com/chaminwoo/stary/
      ├─ feature/map/screen/DiaryGoogleMap.kt   # ← Google Maps Compose (구 DiaryNaverMap)
      ├─ data/repository/Firebase*Repository.kt # 공용 인터페이스의 Firestore 구현
      └─ ...
```

**설계 의도**
- `commonMain` : 도메인 모델·좌표·거리계산·설정·데이터 계약 등 **iOS 와 공유 가능한 순수 로직**.
- `androidApp` : Jetpack Compose UI, Firebase Firestore/Storage/Auth, Google Maps 등 **Android 전용 구현**.
- iOS 앱은 추후 Xcode 에서 `shared` 프레임워크를 임포트하고, `commonMain` 의 Repository 인터페이스를
  Firebase iOS SDK 등으로 구현하면 됩니다. (`expect/actual` 예시: `shared/.../platform/Platform.kt`)

---

## 2. 네이버맵 → Google Maps 전환 요약

| 항목 | 변경 전 (네이버) | 변경 후 (Google) |
|------|------------------|------------------|
| 좌표 타입 | `com.naver.maps.geometry.LatLng` | 공용 `com.chaminwoo.stary.core.geo.LatLng` (지도 경계에서만 `GmsLatLng` 변환) |
| 지도 화면 | `DiaryNaverMap.kt` (Naver Overlay/Clusterer) | `DiaryGoogleMap.kt` (maps-compose + maps-compose-utils Clustering) |
| SDK 초기화 | `StaryApplication` 의 `NaverMapSdk` + 하드코딩 클라이언트 키 | 초기화 불필요. 키는 Manifest `${MAPS_API_KEY}` 로 주입 |
| 거리 계산 | `android.location.Location.distanceBetween` | 공용 `GeoUtils.distanceBetween` (Haversine, 순수 Kotlin) |
| Gradle 저장소 | `repository.map.naver.com` | 제거 (google()/mavenCentral 만 사용) |

> 네이버 Overlay 전용의 per-marker pulse/float `ValueAnimator` 애니메이션은 SDK 종속이라 단순화했습니다.
> 100m 근접 열람 게이팅, 현재 위치 추적(follow), FAB 동작, 클러스터링은 유지됩니다.

---

## 3. 빌드 전 반드시 설정해야 하는 값 (모두 TODO)

민감값은 소스에 하드코딩되어 있지 않습니다. 아래를 직접 채워야 실제로 동작합니다.

### 3-1. `secrets.properties` (프로젝트 루트, **커밋 금지** — `.gitignore` 처리됨)
`secrets.properties.example` 를 복사해서 생성하세요.

```properties
# Google Cloud Console > APIs & Services > Credentials 에서 발급한 Maps SDK for Android 키
MAPS_API_KEY=여기에_실제_키

# Firebase 웹 OAuth 클라이언트(client_type 3) client_id (Google 로그인 ID 토큰용)
GOOGLE_WEB_CLIENT_ID=여기에_실제_웹클라이언트ID
```
- `MAPS_API_KEY` → `AndroidManifest` 의 `com.google.android.geo.API_KEY` 로 주입 (build.gradle.kts `manifestPlaceholders`)
- `GOOGLE_WEB_CLIENT_ID` → `BuildConfig.GOOGLE_WEB_CLIENT_ID` 로 주입 (`GoogleAuthHelper` 가 사용)
- 값이 없으면 `secrets.defaults.properties` 의 `TODO_...` 플레이스홀더로 폴백합니다.

### 3-2. `androidApp/google-services.json` (**커밋 금지**)
- 현재는 **빌드만 통과하는 더미 placeholder** 파일이 들어 있습니다 (실제 비밀 아님, 전부 `0`/placeholder).
- **새 Firebase 프로젝트**를 만들고 Console 에서 받은 실제 `google-services.json` 으로 교체하세요.
- 템플릿: `androidApp/google-services.json.example`
- ⚠️ 원본 운영 Firebase 프로젝트(`momentdiary-...`)와는 **절대 연결하지 마세요.** 새 프로젝트를 사용하세요.

### 3-3. `local.properties`
- `sdk.dir` (Android SDK 경로). Android Studio 가 자동 생성하기도 합니다.

---

## 4. 빌드 / 실행

```bash
# Android 앱
./gradlew :androidApp:assembleDebug
```

- **Android 빌드**는 `shared` 의 `commonMain` + `androidMain` 만 컴파일합니다.
- **iOS 네이티브 컴파일/프레임워크 링크는 macOS + Xcode 환경에서만** 수행됩니다.
  Windows 호스트에서는 iOS 링크 태스크가 비활성화되며 Android 빌드에는 영향이 없습니다.

### 알려진 주의점 / 후속 작업 (TODO)
- `shared` 모듈은 AGP 9 + Kotlin 2.2 KMP 조합으로 구성했습니다. Android Studio 동기화 시
  KMP/AGP 버전 호환 경고가 나오면 `shared/build.gradle.kts` 의 `androidTarget`/`compileSdk` DSL 을
  사용 중인 AGP 버전에 맞게 조정하세요.
- `Diary.createdAt` 등 모델의 시간 필드는 Firebase `Timestamp` → **epoch millis(Long)** 로 변경되었습니다.
  새 Firestore 데이터는 숫자(밀리초)로 저장됩니다. (원본 DB 와 데이터 포맷 호환을 의도하지 않음 — 새 DB 사용)
- iOS 앱(Xcode 프로젝트)과 iOS 쪽 Repository 구현은 아직 포함되어 있지 않습니다(스캐폴딩만 제공).
- 클러스터 마커의 정교한 애니메이션은 Google Maps 기준으로 재구현이 필요할 수 있습니다.

---

## 5. 보안 체크리스트 (이 분기에서 지킨 것)
- [x] 네이버맵 클라이언트 키 하드코딩 제거 (`StaryApplication`)
- [x] Google 로그인 Web Client ID 하드코딩 제거 → `BuildConfig` 주입
- [x] Google Maps API 키 하드코딩 없음 → Manifest placeholder 주입
- [x] 실제 `google-services.json`(운영 project_id/키) 미포함 → placeholder + `.example`
- [x] `secrets.properties`, 실제 `google-services.json`, `local.properties` → `.gitignore`
- [x] 원본 프로젝트 경로 무수정 (이 분기는 별도 경로에서만 작업)
