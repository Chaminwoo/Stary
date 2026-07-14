# IOS_RELEASE.md — 맥 없이 iOS 앱스토어까지 가는 방법

> 이 문서는 **맥(Mac) 없이** Windows에서 작업하며 iOS 앱을 App Store(혹은 TestFlight)에 올리기 위한
> 전체 경로/런북이다. 핵심 아이디어: **빌드/서명/업로드가 일어나는 macOS를 "클라우드 CI"로 빌려서 자동화**한다.

---

## 0. 현실 직시 (왜 한 방에 안 되나)
- iOS 컴파일·코드사이닝·App Store 업로드는 **반드시 macOS + Xcode** 에서 일어난다. 우회 불가.
  → 맥을 **소유**할 필요는 없지만, macOS 실행 환경(클라우드 CI / 렌트 맥)은 **반드시** 필요하다.
- 이 프로젝트의 UI는 전부 **안드로이드 Jetpack Compose** 다. `shared` 모듈은 순수 Kotlin 모델 + 저장소 인터페이스 + 좌표 유틸뿐.
  → iOS 앱은 **SwiftUI 로 새로 구현**해야 한다(또는 추후 Compose Multiplatform 이전). 이 작업은 Windows에서 **작성은 가능, 컴파일/검증은 불가**.
- 따라서 작업 모델은 **"CI를 컴파일러로 사용"**: Windows에서 코드 작성 → 푸시 → macOS CI가 빌드 → 에러 로그 확인 → 수정 반복.

---

## 1. 한 번만 하면 되는 준비 (맥 불필요)
1. **Apple Developer Program 가입 ($99/년, 필수·우회 불가).**
   - https://developer.apple.com/programs/ — 웹/아이폰에서 가입 가능(맥 불필요). 개인/법인 선택.
2. **App Store Connect 에서 앱 레코드 생성.**
   - Bundle ID: **`com.chaminwoo.stary`** — ⚠️ Apple App ID 는 언더스코어(`_`) 불가라 `stary_ios` 를 못 쓴다(2026-07-14 변경).
     원본과의 분리 기준은 **Firebase 프로젝트(`momentdiary-f26c8`)**이지 이름이 아니며, 원본은 iOS 앱이 없어 충돌 없음.
     (Android `applicationId` 는 `com.chaminwoo.stary_ios` 유지 — 둘이 같을 필요 없음.)
   - 앱 이름: **Stary**, 기본 언어: 한국어, SKU 임의.
3. **App Store Connect API Key 발급** (CI 업로드용, 비밀번호/2FA 없이 자동화).
   - App Store Connect → Users and Access → Integrations(또는 Keys) → **App Store Connect API** → Key 생성(Role: App Manager).
   - 받은 값 3개를 보관: **Issuer ID**, **Key ID**, **`.p8` 파일 내용**. → CI Secrets 로 등록(아래 4절).
4. **Firebase iOS 앱 등록 + `GoogleService-Info.plist` 다운로드.**
   - Firebase 콘솔(`momentdiary-f26c8`) → iOS 앱 추가 → Bundle ID `com.chaminwoo.stary` → `GoogleService-Info.plist` 다운로드.
     (기존에 `com.chaminwoo.stary_ios` 로 등록한 iOS 앱이 있다면 번들 ID 수정은 불가 — 새 iOS 앱으로 추가하고 새 plist 를 받을 것.)
   - ⚠️ 이 파일은 **커밋 금지**. CI Secret(base64)로 주입하거나, 빌드 시 생성(아래 4절).

---

## 2. macOS 실행 환경 선택
| 방식 | 비용 | 추천 상황 |
|---|---|---|
| **GitHub Actions macOS 러너** (추천) | 공개 레포 **무료 무제한**, 비공개 레포는 분당 10배 차감(무료 한도 빠듯) | 자동화/반복 빌드. 가능하면 레포 공개 권장 |
| **Codemagic / Bitrise / Xcode Cloud** | 무료 티어 있음(분 제한) | GH Actions 분 부족 시 |
| **렌트 맥** (MacinCloud / MacStadium / AWS EC2 mac) | 월 과금 | 수동으로 Xcode 직접 만지고 싶을 때 |

> **권장**: GitHub Actions. 이미 `origin = github.com/Chaminwoo/Stary` 가 있으므로 워크플로만 추가하면 된다.
> 비용이 걱정되면 레포를 **public** 으로 전환(무료 무제한 macOS 분).

---

## 3. 작업 루프 (맥 없이 iOS 개발하는 법)
```
[Windows] Swift/프로젝트 코드 작성·수정
   → git push
      → [GitHub Actions = macOS] gradlew로 Shared.framework 빌드 → XcodeGen으로 .xcodeproj 생성
        → xcodebuild archive → (성공 시) TestFlight 업로드
           → 빌드 로그/에러 확인
              → [Windows] 수정 → 다시 push  (반복)
```
- `.xcodeproj` 는 커밋하지 않고 **XcodeGen**(`iosApp/project.yml`)으로 CI에서 생성한다 → Windows에서 텍스트로 프로젝트 관리 가능.
- 첫 목표(마일스톤 0): **빈 SwiftUI 앱이 Shared.framework 를 링크해 빌드 성공** → 파이프라인 검증.
- 이후 화면을 하나씩 SwiftUI로 구현하며 CI 그린 유지.

---

## 4. CI Secrets (GitHub → Settings → Secrets and variables → Actions)
| Secret 이름 | 내용 |
|---|---|
| `APP_STORE_CONNECT_KEY_ID` | API Key ID |
| `APP_STORE_CONNECT_ISSUER_ID` | API Issuer ID |
| `APP_STORE_CONNECT_API_KEY` | `.p8` 파일 내용(텍스트 그대로 또는 base64) |
| `GOOGLE_SERVICE_INFO_PLIST` | `GoogleService-Info.plist` 를 base64 인코딩한 문자열 |
| `IOS_DISTRIBUTION_CERT_P12` | 배포 인증서(.p12) base64 — fastlane match 사용 시 불필요 |
| `IOS_CERT_PASSWORD` | .p12 비밀번호 |

> 인증서/프로비저닝은 **fastlane match**(전용 비공개 레포에 암호화 저장) 또는 **App Store Connect API 기반 자동 서명**으로 관리하면
> 맥에서 수동으로 keychain 만질 필요가 없다. 처음엔 `xcodebuild -allowProvisioningUpdates` + API Key 자동 서명이 가장 간단하다.

---

## 5. 빌드/업로드 실제 명령 (CI에서 실행됨)
```bash
# 1) KMP 공유 프레임워크 (CI가 Xcode 빌드 단계에서 자동 호출)
./gradlew :shared:embedAndSignAppleFrameworkForXcode   # Xcode run-script phase 내부에서 실행

# 2) 프로젝트 생성
brew install xcodegen
( cd iosApp && xcodegen generate )

# 3) 아카이브
xcodebuild -project iosApp/Stary.xcodeproj -scheme Stary \
  -configuration Release -archivePath build/Stary.xcarchive \
  -allowProvisioningUpdates archive

# 4) IPA export + App Store 업로드 (fastlane 권장)
#   fastlane pilot upload (TestFlight) / fastlane deliver (심사 제출)
```
구체 설정은 `.github/workflows/ios.yml` 와 `iosApp/fastlane/` 참고.

---

## 6. 남은 큰 작업 (CI 파이프라인 위에서 반복 구현)
> 진행 현황(2026-06-25): 1차 코어 슬라이스 작성 완료 → **CI 컴파일 검증 대기**. 자세한 건 `docs/PROJECT_NOTES.md` 8.18.
- [x] **Firebase iOS SDK 연동** (SPM: firebase-ios-sdk → Firestore/Auth/Storage). `FirebaseApp.configure()`(plist 있을 때만). `GoogleService-Info.plist` 주입은 CI Secret.
- [x] **named DB(stary-db)** 접근: `FirestoreService.db = Firestore.firestore(database: "stary-db")`.
- [x] **지도**: MapLibre iOS (`MapLibreView`) — 별 마커(`StarImageRenderer`)·탭→상세. (스타일은 데모 타일; 키 주입 교체 TODO)
- [x] **인증**: Google Sign-In + Firebase Auth(credential) / 익명 (`AuthManager`).
- [~] **화면**: 지도/목록/업로드/상세/프로필 = 1차 구현. **친구/채팅/알림/댓글·좋아요/업적 = TODO.** 사진 첨부(Storage+PhotosPicker) TODO.
- [x] **별 마커/피커**: `StarStyle.swift`+`StarShape.swift` 로 모양·팔레트 포팅(0~4 정밀, 5~8 even-odd 근사). 마커 비트맵 굽기. (그라데이션 채움 정교화 TODO)
- [ ] **앱 아이콘/스플래시/스크린샷/개인정보처리방침** (App Store 심사 필수 자료).
- [x] **권한 설명**(Info.plist 위치/카메라/사진 — project.yml 에 정의). ATT 미사용.

> 대안: **Compose Multiplatform 이전**으로 일부 UI를 공유할 수 있으나, 현재 UI가 MapLibre-Android/Firebase-Android/`android.graphics`
> 에 깊게 묶여 있어 이전도 대규모 작업이다. 어느 쪽이든 최종 빌드/제출은 macOS(CI) 필요.

---

## 7. 비용 요약
- Apple Developer Program: **$99/년** (필수).
- GitHub Actions macOS: 공개 레포 무료 / 비공개는 유료 분 소모.
- (선택) 렌트 맥: 월 $20~.
