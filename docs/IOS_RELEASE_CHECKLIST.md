# iOS 앱스토어 릴리즈 체크리스트 — Stary (포크본)

> 작성 기준일: 2026-06-19. 이 문서는 **현재 코드 상태에서 iOS App Store 출시까지 무엇이 필요한지**를 정리한다.

## 0. 큰 그림 (반드시 먼저 이해)

**현재 상태:** 이 프로젝트는 사실상 **Android 전용**이다.
- `iosApp/`(Xcode 프로젝트) **없음**, `GoogleService-Info.plist` **없음**.
- `shared/`(KMP)에는 **모델·`GeoUtils`·Repository 인터페이스**만 있음. `iosMain`은 `Platform.ios.kt` 스텁뿐.
- **모든 화면(Jetpack Compose)·ViewModel·Firebase 구현·지도·로그인·오디오**가 `androidApp`에 Android 전용으로 들어있음.
- iOS 타깃(`iosX64/Arm64/SimulatorArm64`)은 `shared/build.gradle.kts`에 선언되어 프레임워크(`Shared`, static)는 생성되지만, **그 안에 든 게 거의 없다.**

**환경 제약(중요):** iOS 컴파일/링크/아카이브/업로드는 **macOS + Xcode 필수**. 현재 개발 PC는 Windows라 **Mac이 없으면 한 줄도 빌드 못 한다.** (Mac mini/MacBook 또는 클라우드 Mac(MacStadium 등) 필요.)

**따라서 이건 "릴리즈 설정" 작업이 아니라 "iOS 앱 구축" 프로젝트다.** 아래는 그 전체 로드맵.

---

## 1. 선결정 — iOS UI 아키텍처 (둘 중 택1)

- [ ] **Path A · 네이티브 SwiftUI** + Kotlin `shared`(모델/도메인 재사용)
  - UI를 Swift로 새로 작성. 학습/작성량 많지만 iOS 네이티브 품질·안정성 높음.
- [ ] **Path B · Compose Multiplatform(CMP)** — `androidApp`의 Compose UI를 `shared/commonMain`으로 이전
  - 화면 코드 상당수 재사용 가능. 단, 아래 Android 전용 의존성을 **전부 expect/actual로 분리**해야 해서 실제론 만만치 않음.
  - 현재 `compose-multiplatform` 플러그인 **미설정** → 도입부터 필요.

> 어느 path든 **플랫폼 의존 코드(아래 4번)는 iOS 구현을 새로 작성**해야 한다. UI만 공유한다고 끝나지 않음.

---

## 2. 공유 모듈(`shared`)을 "진짜 공유"로 끌어올리기

- [ ] ViewModel/도메인 로직을 `androidApp` → `shared/commonMain`으로 이동 (현재 ViewModel은 androidApp 전용).
- [ ] Repository **인터페이스는 이미 commonMain에 있음**(`shared/.../data/repository/Repositories.kt`). 구현을 플랫폼별로 분리.
- [ ] 플랫폼 의존 지점을 `expect/actual`로 추상화: 로그인, 저장소(Firestore/Storage), 위치, 이미지 업로드, 알림, 오디오, 지도.
- [ ] DI 정리: ViewModel이 `Firebase*Repository` 구상클래스 대신 인터페이스를 주입받도록(현재 직접 생성 — PROJECT_NOTES TODO).

---

## 3. iOS 앱 타깃 생성

- [ ] `iosApp/` 디렉터리 + **Xcode 프로젝트(`iosApp.xcodeproj`)** 생성 (KMP 표준 구조).
- [ ] `Shared` 프레임워크를 iOS 앱에 임베드 (Build Phases / `embedAndSignAppleFrameworkForXcode` 또는 SPM/직접 링크).
- [ ] 앱 진입점(SwiftUI `App` 또는 CMP `UIViewController`) 작성.
- [ ] **Bundle Identifier = `com.chaminwoo.stary_ios`** (포크 전용. androidApp `applicationId`와 동일하게 이미 잡혀 있음 — 충돌 방지로 원본 `com.chaminwoo.stary` 절대 사용 금지).

---

## 4. iOS 플랫폼 구현 (Android 전용 → iOS 대체) — **가장 큰 덩어리**

| 기능 | 현재(Android) | iOS에서 해야 할 일 |
|---|---|---|
| DB/저장소 | Firebase **Android** SDK (Firestore) | Firebase **iOS** SDK(Firestore) 또는 KMP용 래퍼로 `*Repository` iOS 구현 |
| 인증 | Google Sign-In (Android) | **Sign in with Google (iOS)** + (권장) **Sign in with Apple** ※소셜 로그인 앱은 Apple 로그인 사실상 필수 |
| 파일 업로드 | Firebase Storage(Android) + `FileProvider`/카메라 | Firebase Storage(iOS) + `PHPicker`/`UIImagePickerController` |
| 지도 | **MapLibre Android** + MapTiler | **MapLibre iOS(`MapLibre` SPM)** + 동일 MapTiler 키/스타일(`maplibre_style.json` 재사용 가능) |
| 위치 | `LocationHelper`(FusedLocation) | `CoreLocation`(`CLLocationManager`) |
| 이미지 로딩 | Coil | `AsyncImage`(SwiftUI) 또는 Kingfisher |
| 효과음/배경음 | `SoundPool`/`MediaPlayer` (`MusicManager`) | `AVAudioPlayer`/`AVAudioEngine` |
| 푸시 알림 | FCM(Android) | FCM(iOS) + **APNs 인증키(.p8)** 연동 |

- [ ] 위 표의 각 항목 iOS 구현 완료 + 동작 확인.
- [ ] `functions/`(FCM 발송 Function)은 백엔드라 **재사용 가능** — iOS 토큰만 등록되면 됨.

---

## 5. Firebase iOS 설정 (포크 프로젝트 `momentdiary-f26c8`)

- [ ] Firebase 콘솔 → **`momentdiary-f26c8`** 프로젝트에 **iOS 앱 추가** (Bundle ID `com.chaminwoo.stary_ios`).
  - ⚠️ 원본 운영 프로젝트(`momentdiary-52b78`)에 **연결 금지**.
- [ ] **`GoogleService-Info.plist`** 내려받아 iOS 앱에 추가 (커밋 금지 — 시크릿).
- [ ] Firestore/Storage **보안 규칙**이 iOS 트래픽에도 맞는지 확인(`storage.rules` 이미 존재).
- [ ] 푸시: **APNs 인증키(.p8)** 생성(Apple Developer) → Firebase Cloud Messaging에 업로드.
- [ ] 로그인: OAuth 클라이언트(iOS) 발급 + `REVERSED_CLIENT_ID` URL Scheme 등록.

---

## 6. Apple Developer / App Store Connect (유료 — 연 $99)

- [ ] **Apple Developer Program 가입**(연 $99 결제).
- [ ] App ID 등록(`com.chaminwoo.stary_ios`) + Capabilities(Push Notifications, Sign in with Apple 등) 체크.
- [ ] **인증서/프로비저닝**: Distribution 인증서 + App Store 프로비저닝 프로파일 (Xcode 자동 관리 권장).
- [ ] **App Store Connect**에서 앱 레코드 생성(이름/SKU/Bundle ID).
- [ ] 앱 메타데이터: 이름, 부제, 설명, 키워드, 카테고리, 지원 URL.
- [ ] **스크린샷**(6.7"/6.5"/5.5" 등 필수 사이즈), 앱 아이콘 1024×1024(알파 없음).
- [ ] **개인정보 처리방침 URL**(필수) + App Store **개인정보 보호 설문(Privacy Nutrition Labels)** 작성(위치/사진/계정 수집 항목 신고).

---

## 7. iOS 필수 컴플라이언스 (자주 누락 → 리뷰 리젝)

- [ ] **권한 사용 설명 문자열**(`Info.plist`): `NSLocationWhenInUseUsageDescription`, `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`(+필요시 AddUsage), 푸시 등 **모두 한국어 사유 작성**.
- [ ] **Privacy Manifest**(`PrivacyInfo.xcprivacy`) — 수집 데이터 종류·사용 SDK 신고(2024+ 필수).
- [ ] **App Tracking Transparency**: 광고/추적 없으면 미사용 명시(있으면 ATT 프롬프트).
- [ ] **암호화 수출 규정**: `ITSAppUsesNonExemptEncryption = NO`(표준 HTTPS만 쓰면) `Info.plist`에 추가.
- [ ] **Sign in with Apple**: 타사 소셜 로그인(구글)을 제공하면 Apple 로그인 동시 제공이 가이드라인상 요구됨.
- [ ] 지도/타일·폰트 등 **서드파티 라이선스 고지**(MapLibre/MapTiler).

---

## 8. 빌드 → 릴리즈 파일(.ipa) → 제출

- [ ] (Mac에서) `./gradlew :shared:assembleSharedReleaseXCFramework` 등으로 **Release 프레임워크** 생성.
- [ ] Xcode에서 **Scheme = Release**, 디바이스 타깃으로 **Archive**.
- [ ] **Organizer → Distribute App → App Store Connect**로 업로드(= iOS의 "릴리즈 파일" .ipa는 보통 직접 들고 다니지 않고 업로드).
- [ ] **TestFlight** 내부 테스트로 실기기 검증.
- [ ] App Store Connect에서 빌드 선택 → 심사 제출(Submit for Review).
- [ ] 리뷰 통과 후 출시(수동/자동 릴리즈).

---

## 9. 현실적 권고

1. **Mac 확보**가 0순위(없으면 진행 자체 불가).
2. 범위가 크므로 **MVP iOS 화면**(지도+다이어리 보기/작성+로그인)부터 좁혀서 시작 권장.
3. UI 재사용을 노리면 **Path B(CMP)**, iOS 품질·단순함을 노리면 **Path A(SwiftUI)**. 다만 어느 쪽이든 **4번(플랫폼 구현)**이 일의 대부분이다.
4. 백엔드(Firebase 스키마, `functions/` FCM, `storage.rules`)는 **재사용 가능** — 새로 만들 필요 없음.

---

### 한 줄 요약
> 지금은 **iOS 앱이 없다.** 출시하려면 ① Mac, ② iOS UI(SwiftUI 또는 CMP), ③ iOS용 Firebase/지도/로그인/위치/오디오 구현, ④ Firebase iOS 등록 + `GoogleService-Info.plist`, ⑤ Apple Developer($99) + App Store Connect 등록, ⑥ Xcode Archive→업로드→TestFlight→심사 가 모두 필요하다.
