# iOS 부트스트랩 — 클라우드 Mac에서 "빈 화면 + shared 연결"까지

> ⚠️ **이 문서는 초기 실험 기록(구식)** — 지금은 `iosApp/project.yml` + XcodeGen 방식이라
> 수동 프로젝트 생성이 필요 없다. **클론 후 시뮬레이터 실행 절차는 [IOS_SETUP.md](IOS_SETUP.md) 를 볼 것.**

> 목표: 맥을 사지 않고 **클라우드 Mac**에서 이 레포를 받아, `iosApp` Xcode 프로젝트를 만들고
> **시뮬레이터에 빈 SwiftUI 화면을 띄우되 `shared`(Kotlin) 코드를 한 줄 호출**해 KMP 브리지가 동작함을 확인한다.
> (전체 출시 로드맵은 [IOS_RELEASE_CHECKLIST.md](IOS_RELEASE_CHECKLIST.md) 참고. 이 문서는 그 첫 발자국.)

---

## A. 클라우드 Mac 띄우기 (예: MacinCloud)

- [ ] MacinCloud 가입 → **Managed Server**(또는 Pay-As-You-Go) 플랜 선택. (RDP/VNC로 접속하는 원격 맥.)
  - 대안: MacStadium, AWS EC2 Mac, Scaleway Mac mini. 무엇이든 **실제 Apple 하드웨어 + macOS**면 됨.
- [ ] 원격 데스크톱으로 접속.
- [ ] **Xcode 설치**(App Store) — 시간이 꽤 걸림. 설치 후 한 번 실행해 추가 컴포넌트 설치 + 라이선스 동의.
  ```bash
  sudo xcodebuild -license accept
  xcode-select --install   # Command Line Tools (이미 있으면 스킵)
  ```
- [ ] **JDK 17 설치**(Gradle 빌드용). 예: Homebrew
  ```bash
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  brew install --cask temurin@17
  /usr/libexec/java_home -v 17   # JAVA_HOME 경로 확인 (메모해 둘 것)
  ```

## B. 레포 받기 + shared의 iOS 컴파일 확인

- [ ] 클론:
  ```bash
  git clone https://github.com/Chaminwoo/Stary.git
  cd Stary
  ```
- [ ] **시크릿/설정 파일** 채우기(없으면 런타임만 안 되고 컴파일엔 무방). `secrets.properties`, `androidApp/google-services.json` 등은 Android용 — iOS 빈 화면 단계에선 불필요.
- [ ] **shared iOS 프레임워크가 실제로 링크되는지** 먼저 확인(여기서부터는 macOS라 iOS 타깃이 활성화됨):
  ```bash
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
  ```
  - 성공하면 `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework` 생성됨.
  - (Intel 맥이면 `...IosX64` 태스크.)

## C. Xcode에서 `iosApp` 프로젝트 생성 (GUI)

> `.xcodeproj`는 손으로 쓰기 까다로워 **Xcode에서 생성**하는 게 안전하다.

- [ ] Xcode → **File ▸ New ▸ Project ▸ iOS ▸ App**.
- [ ] 옵션:
  - Product Name: `iosApp`
  - Organization Identifier: `com.chaminwoo` → Bundle Identifier가 **`com.chaminwoo.iosApp`** 로 잡힘.
    출시용 번들 ID는 **`com.chaminwoo.stary.ios`**(Apple 언더스코어 불가로 `stary_ios` 에서 점 표기로 변경, 2026-07-15)이므로, 생성 후 Target ▸ Signing & Capabilities(또는 Build Settings의 `PRODUCT_BUNDLE_IDENTIFIER`)에서 **`com.chaminwoo.stary.ios`** 로 변경.
  - Interface: **SwiftUI**, Language: **Swift**.
- [ ] 저장 위치: **레포 루트 안 `Stary/iosApp/`** 로 지정(=`Stary/iosApp/iosApp.xcodeproj`). 구조가 `:shared`와 형제가 되도록.

## D. Shared 프레임워크를 iOS 앱에 연결

> 권장 방식: **Run Script 빌드 단계**에서 Gradle이 현재 빌드(시뮬/실기기·Debug/Release)에 맞는 프레임워크를 자동으로 임베드.

- [ ] Xcode ▸ 프로젝트 ▸ **TARGETS ▸ iosApp ▸ Build Phases ▸ ⊕ ▸ New Run Script Phase**. 만든 스크립트를 **"Compile Sources" 위로** 드래그(먼저 실행되게). 내용:
  ```bash
  cd "$SRCROOT/.."
  # JAVA_HOME 이 안 잡히면 아래 한 줄 주석 해제(경로는 B에서 확인한 값)
  # export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode
  ```
- [ ] **User Script Sandboxing 끄기**(최신 Xcode 필수): Build Settings에서 `ENABLE_USER_SCRIPT_SANDBOXING = No`. (안 끄면 gradle이 파일을 못 써서 실패.)
- [ ] **Framework Search Paths** 추가: Build Settings ▸ `FRAMEWORK_SEARCH_PATHS` 에
  ```
  $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
  ```
  (KMP 버전에 따라 경로가 다르면, 첫 빌드 후 `shared/build/` 아래에서 `Shared.framework` 위치를 찾아 맞춤.)
- [ ] (옵션) **General ▸ Frameworks, Libraries, and Embedded Content** 에 `Shared.framework`가 보이면 **Embed & Sign**.

## E. 빈 화면에서 shared 호출 (브리지 증명)

- [ ] `iosApp/iosApp/ContentView.swift` 를 아래로 교체 — `shared`의 `describePlatform()`(=Swift에선 `PlatformKt.describePlatform()`) 호출:
  ```swift
  import SwiftUI
  import Shared   // KMP 프레임워크 baseName = "Shared"

  struct ContentView: View {
      var body: some View {
          VStack(spacing: 12) {
              Text("Stary iOS")
                  .font(.largeTitle).bold()
              // ↓ Kotlin shared 코드 호출 (성공하면 "Running on iOS xx.x" 표시)
              Text(PlatformKt.describePlatform())
                  .foregroundColor(.secondary)
          }
          .padding()
      }
  }

  #Preview { ContentView() }
  ```
- [ ] 앱 진입점(`iosAppApp.swift`)은 기본 생성된 그대로 두면 됨:
  ```swift
  import SwiftUI

  @main
  struct iosAppApp: App {
      var body: some Scene {
          WindowGroup { ContentView() }
      }
  }
  ```

## F. 시뮬레이터 실행

- [ ] Xcode 상단 디바이스 선택 → **iPhone 15 (또는 임의 시뮬레이터)**.
- [ ] **⌘R (Run)**.
- [ ] 화면에 **"Stary iOS"** 와 **"Running on iOS 17.x"** 가 뜨면 성공 — KMP `shared` ↔ SwiftUI 브리지 동작 확인 완료. ✅

---

## 자주 막히는 곳 (체크포인트)

- [ ] **`./gradlew` 권한/자바**: Run Script에서 `command not found: java` 류 → `export JAVA_HOME=...` 주석 해제.
- [ ] **Sandboxing 에러**(`Sandbox: bash deny file-write`) → `ENABLE_USER_SCRIPT_SANDBOXING = No`.
- [ ] **`No such module 'Shared'`** → Run Script가 Compile Sources보다 먼저 실행되는지, Framework Search Paths 경로가 맞는지 확인. 한번 **Clean Build(⇧⌘K)** 후 재빌드.
- [ ] **Intel vs Apple Silicon 맥**: 시뮬레이터 타깃 태스크가 `IosSimulatorArm64`(M칩) / `IosX64`(인텔)로 갈림. `embedAndSign...`은 자동 판별하므로 보통 신경 안 써도 됨.
- [ ] **Xcode 버전**과 Kotlin/AGP 호환: 링크 에러 시 `gradle/libs.versions.toml`의 Kotlin 버전이 지원하는 Xcode인지 확인.

## 다음 단계 (빈 화면 이후)
1. `iosApp/`를 레포에 커밋(=이 프로젝트의 iOS 앱 골격 확보).
2. [IOS_RELEASE_CHECKLIST.md](IOS_RELEASE_CHECKLIST.md)의 **4번(플랫폼 구현)** 부터: Firebase iOS SDK + `GoogleService-Info.plist`(`momentdiary-f26c8`), 로그인, 지도(MapLibre iOS), 위치(CoreLocation) 순으로 화면을 하나씩 채운다.
3. UI를 많이 재사용하려면 이 시점에 **Compose Multiplatform 도입 여부**를 결정.
