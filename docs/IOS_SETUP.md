# iOS 셋업 — 깃 클론 → 시뮬레이터 실행까지 (macOS)

> **완전히 새로 클론한 macOS 환경**에서 Stary iOS 앱을 시뮬레이터에 띄우기까지의 전체 단계.
> (CI가 통과하는 코드인데 로컬 Xcode 에서 빌드가 깨진다면 대부분 아래 3·4·6단계 누락이 원인이다.)
>
> - 초기 KMP 브리지 실험 기록은 [IOS_BOOTSTRAP.md](IOS_BOOTSTRAP.md)(구식 — 수동 프로젝트 생성 방식) 참고.
> - 실기기(TestFlight) 배포는 [IOS_RELEASE.md](IOS_RELEASE.md) / [DEVICE_TESTING.md](DEVICE_TESTING.md) 참고.

---

## 0. 준비물 (1회 설치)

| 도구 | 설치 | 용도 |
|---|---|---|
| Xcode 16+ | App Store | 빌드/시뮬레이터 (`macos-15` CI 러너와 동일 세대) |
| Homebrew | https://brew.sh | 아래 도구 설치 |
| JDK 17 | `brew install --cask temurin@17` | `:shared`(Kotlin) 프레임워크 빌드 |
| XcodeGen | `brew install xcodegen` | `.xcodeproj` 생성(레포엔 `project.yml` 만 커밋됨) |
| Android SDK | Android Studio 설치(가장 쉬움) 또는 `brew install --cask android-commandlinetools` | ⚠️ iOS 빌드인데도 필요 — `:shared` 구성 시 Gradle 이 `:androidApp` 까지 구성하며 AGP 가 SDK 위치를 요구한다 |

Xcode 는 설치 후 한 번 실행해 추가 컴포넌트 설치 + 라이선스 동의:

```bash
sudo xcodebuild -license accept
```

## 1. 클론

```bash
git clone https://github.com/Chaminwoo/Stary-Project.git
cd Stary-Project
```

## 2. `local.properties` 작성 (Android SDK 위치 — 필수)

Xcode 의 pre-build 스크립트 환경엔 `ANDROID_HOME` 이 전달되지 않으므로 **파일로 명시**해야 한다
(CI 도 같은 이유로 이 파일을 만든다):

```bash
# Android Studio 로 SDK 를 설치했다면 보통 이 경로
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

이걸 빼먹으면 Xcode 빌드 중 gradle 단계에서 `SDK location not found` 로 실패한다.

## 3. (선택) API 키 주입 — 커밋 금지

키가 없어도 **빌드/실행은 된다**(지도는 데모 스타일 폴백, 길찾기는 비활성).
실제 야경 지도/길찾기를 보려면 `iosApp/project.yml` 의 두 값을 채운다:

```yaml
# iosApp/project.yml → targets.Stary.settings.base
ORS_API_KEY: ""       # ← OpenRouteService 키 (Android secrets.properties 의 ORS 키와 동일 값)
MAPTILER_KEY: ""      # ← MapTiler 키 (Android secrets.properties 의 MAPTILER_KEY 와 동일 값)
```

⚠️ **이 변경은 절대 커밋하지 말 것**(§CLAUDE.md 민감값 규칙). 4단계 재생성(xcodegen) 후
Xcode Build Settings 에서 직접 넣는 방법도 있지만, 재생성 때마다 지워지므로 project.yml 로컬 수정이 편하다.

나머지 설정은 이미 레포에 커밋돼 있어 **할 일 없음**:
- `iosApp/Sources/GoogleService-Info.plist` — Firebase(`momentdiary-f26c8`) iOS 설정, 실제 파일 커밋됨(2026-07-15 결정).
- `GOOGLE_REVERSED_CLIENT_ID` — project.yml 에 실제 값 커밋됨(위 plist 와 동일 값, 구글 로그인 URL 스킴).

## 4. Xcode 프로젝트 생성 — **클론/풀 때마다 핵심 단계**

`.xcodeproj` 는 커밋되지 않는다. `project.yml` 에서 생성한다:

```bash
cd iosApp
xcodegen generate     # → iosApp/Stary.xcodeproj 생성
```

> ⚠️ **`git pull` 로 Swift 파일이 추가/삭제된 뒤에는 반드시 `xcodegen generate` 를 다시 실행**할 것.
> 프로젝트 파일이 옛 파일 목록을 물고 있으면 새 파일이 타깃에 안 들어가서
> `cannot find type 'DiaryOpenWarpData' in scope` 같은 "선언이 없다" 류 에러가 난다.
> (실제 선언은 `Sources/Features/Map/DiaryOpenWarpView.swift` 에 있다 — 코드 문제가 아니라 프로젝트 파일 문제.)

실기기 서명까지 하려면 생성 전에 팀 ID 를 환경변수로 주입(시뮬레이터만 쓰면 불필요):

```bash
DEVELOPMENT_TEAM=<Apple Team ID> xcodegen generate
```

## 5. Xcode 에서 열고 실행

```bash
open Stary.xcodeproj
```

1. 처음 열면 **Swift Package 해석**(Firebase / GoogleSignIn / MapLibre)을 기다린다 — 수 분 소요.
2. 상단 디바이스에서 **iPhone 시뮬레이터** 선택 → **⌘R**.
3. 첫 빌드는 pre-build 스크립트가 `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 를 돌려
   Kotlin 공유 프레임워크를 만들기 때문에 **첫 회만 5~10분** 걸린다(이후 증분).

## 6. 자주 막히는 곳

| 증상 | 원인/해결 |
|---|---|
| gradle 단계 `SDK location not found` | 2단계 `local.properties` 누락 |
| gradle 단계 `Unable to locate a Java Runtime` | JDK 17 미설치. 설치 후에도 안 되면 pre-build 스크립트(project.yml `preBuildScripts`)에 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 한 줄 추가 |
| `Sandbox: bash deny file-write` | Build Settings → `ENABLE_USER_SCRIPT_SANDBOXING = No` |
| `No such module 'Shared'` | pre-build gradle 단계가 실패한 것 — 빌드 로그 상단의 gradle 에러부터 해결. Clean(⇧⌘K) 후 재빌드 |
| `cannot find type '...' in scope` (예: DiaryOpenWarpData) | 4단계 `xcodegen generate` 재실행 누락 |
| `the compiler is unable to type-check this expression in reasonable time` | 곱셈이 3개 이상 얽힌 한 줄 수식이 원인 — **부분식 여러 개로 분해**해서 작성한다(예: `MusicScreen.swift` / `MyDiaryBoardScreen.swift` 의 `magnitudePart · pulsePart · frequencyPart` 패턴). CGFloat·Double 혼합 `+` 도 모호성 에러 → Double 로 통일 후 마지막에 CGFloat |
| 지도가 회색 데모 타일 | `MAPTILER_KEY` 미주입(3단계) — 기능 확인엔 지장 없음 |
| 길찾기가 아무 반응 없음 | `ORS_API_KEY` 미주입(3단계) — 의도된 조용한 비활성 |

## 7. 코드 수정 시 규칙 (요약)

- **긴 수식 금지**: 위 표의 type-check 항목 — 복잡한 계산식은 항상 이름 붙인 부분식으로 나눈다.
- Swift 파일을 새로 만들면 커밋 전 로컬에선 `xcodegen generate` 재실행(프로젝트 파일에 반영).
- Windows 쪽에서 작업된 변경은 push 하면 **GitHub Actions `ios.yml`** 이 시뮬레이터 빌드로 검증한다
  (`CODE_SIGNING_ALLOWED=NO`, 서명 불필요). 로컬 맥과 CI 는 같은 단계(local.properties → xcodegen → xcodebuild)를 쓴다.
