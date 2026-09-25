# CLAUDE.md — Stary-Project 작업 규칙

이 파일은 Claude Code 가 매 세션 자동으로 읽는 **규칙/워크플로 파일**이다.
코드 구조·분석 내용은 [`docs/PROJECT_NOTES.md`](docs/PROJECT_NOTES.md) 에 정리되어 있으니
작업 시작 전 그 파일부터 읽으면 코드를 처음부터 다시 읽지 않아도 된다.
화면·기능별 유지보수 문서(변수/함수/컴포넌트 연결/iOS 값 조절 매핑)는 [`docs/code/`](docs/code/README.md) 참고
— 특정 화면을 고칠 땐 해당 문서부터 읽고, **그 화면을 크게 바꾼 뒤에는 그 문서도 갱신**한다.
기능 개발 로드맵/체크리스트는 [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md) 참고(초기 셋업은 완료됨).
버전별 사용자 관점 변경 요약(스토어 출시 노트용)은 [`docs/PATCH_NOTES.md`](docs/PATCH_NOTES.md) — 새 버전을 낼 때 여기에 추가한다.
실기기(Android USB / iOS TestFlight) 테스트 방법은 [`docs/DEVICE_TESTING.md`](docs/DEVICE_TESTING.md) 참고.

---

## 0. 프로젝트 정체성 (절대 규칙)
- 이 프로젝트는 **KMP 분기본(fork)** 이다. 원본과 **완전히 분리**되어 있다.
  - 원본 경로(절대 수정 금지): `C:\Users\User\AndroidStudioProjects\mobile-project-Chaminwoo\Stary`
  - 작업 경로: `C:\Users\User\AndroidStudioProjects\Stary-Project`
- **민감값(API 키, Firebase 설정, OAuth, 지도 키)은 절대 하드코딩/커밋 금지.** 항상 TODO/placeholder + 주입.
- 원본의 운영 Firebase(**원본 = `momentdiary-52b78` / 앱 패키지 `com.chaminwoo.stary`**)/DB/배포 설정에 **연결 금지.**
  - 이 포크 전용 Firebase는 `momentdiary-f26c8` / Android `com.chaminwoo.stary_ios` (별개 프로젝트, 사용 OK).
  - **iOS 번들 ID는 `com.chaminwoo.stary.ios`** (Apple App ID 가 언더스코어 불가라 `stary_ios` 사용 불가 — 2026-07-15 점 표기로 확정, Firebase `f26c8` 에 이 ID로 iOS 앱 등록 완료).
    원본과 이름이 같아도 **분리 기준은 Firebase 프로젝트**(iOS 앱은 `f26c8` 에 등록)라 위반이 아니다. 원본은 iOS 앱 없음.
  - **예외: iOS `GoogleService-Info.plist` 는 `iosApp/Sources/` 에 커밋되어 있다(2026-07-15 사용자 지시).**
    Firebase iOS 설정값은 앱 바이너리에 항상 포함되는 클라이언트 식별자라 실질적 비밀이 아니며, `iosApp/project.yml` 의
    `GOOGLE_REVERSED_CLIENT_ID` 도 이 plist 의 `REVERSED_CLIENT_ID` 와 동일한 실제 값으로 맞춰 두었다(로그인 URL 스킴에 필요).
    **다른 파일(루트, `iosApp/`)에 중복 커밋하지 말고, 이 규칙을 근거로 되돌리려 하지 말 것** — Android 시크릿(§1 하드코딩 금지)과는 별개 취급.
- **작업 중에는 확인받지 말고 자유롭게 진행한다(2026-06-28).** 파일 이동·생성·쓰기·편집, 빌드, (로컬) 커밋까지 사용자 확인/허락을 받지 않는다.
  - **단, `git push` 만은 예외 — 사용자가 "테스트 완료"라고 말할 때까지 기다린다(§1 워크플로 준수).** 빌드 성공 후 멈추고, 테스트 완료 신호가 오면 그때 push.
  - 즉 "테스트·푸시 직전까지는 확인 불필요, 푸시만 테스트 완료 대기."

## 1. 작업 → 빌드 → 테스트 → 푸시 워크플로 (반드시 이 순서)

1. **작업(코드 변경) 수행.**
2. **빌드 정상 확인** — 코드가 바뀌었으면 항상 빌드를 돌려서 통과를 확인한다.
   - 명령(Windows PowerShell): `.\gradlew.bat :androidApp:assembleDebug --console=plain`
   - 빠른 검증만 필요하면: `.\gradlew.bat :androidApp:compileDebugKotlin`
   - 빌드 실패 시 → 원인 수정 후 성공할 때까지 반복. 실패한 채로 다음 단계로 넘어가지 않는다.
   - 문서(.md)만 바꾼 경우엔 빌드에 영향 없으므로 직전 빌드 상태를 유지한다(불필요한 재빌드 금지).
3. **빌드 성공 → 사용자 테스트 차례.** 여기서 멈춘다.
   - ⚠️ **사용자가 "테스트 완료"라고 명시하기 전에는 절대 `git push` 하지 않는다.** (커밋(로컬)은 §0 에 따라 확인 없이 진행 가능 — push 만 대기.)
4. **사용자가 "테스트 완료"라고 하면 → 그때 push 한다.**
   - GitHub 레포는 추후 사용자가 생성. remote 가 설정되어 있는지 먼저 확인하고, 없으면 사용자에게 알린다.
   - main 브랜치에 직접 푸시하지 말고 필요 시 브랜치 전략을 사용자와 맞춘다.
5. **(빌드 성공 + 테스트 성공) 이후에는 항상 [`docs/PROJECT_NOTES.md`](docs/PROJECT_NOTES.md) 를 최신화한다.**
   - 무엇을 바꿨는지, 새 파일/삭제 파일, 새로 알게 된 제약/주의점, 남은 TODO 를 반영한다.

## 1.5 Android ↔ iOS 패리티 (사용자 지시, 2026-06-25~)
- **앞으로 Android 쪽 기능/로직을 고치면, 같은 변경을 iOS(`iosApp/` SwiftUI)에도 적용한다.**
  - 공용 모델/상수는 `shared` 의 commonMain + iOS `AppConfig.swift`/`Models.swift` 양쪽을 함께 맞춘다(값 drift 금지).
  - 별 모양/색은 `StarStyle.kt` ↔ `iosApp/.../Core/StarStyle.swift`+`StarShape.swift` 가 정의를 공유 — 한쪽 바꾸면 반대쪽도.
  - iOS 는 Windows 에서 컴파일 불가 → 변경 후 push 하면 **GitHub Actions(macOS) `ios.yml` 가 컴파일 검증**(red/green). 빌드 로그로 오류 수정 반복.
  - Android 변경분과 iOS 반영분은 (가능하면) 같은 작업 단위로 처리하고, 못 하면 iOS TODO 로 PROJECT_NOTES 에 남긴다.

## 2. 빌드 관련 메모
- AGP 9 + KMP 조합:
  - `:shared` 는 `com.android.kotlin.multiplatform.library` 플러그인 사용 (`com.android.library` 와 비호환).
    `kotlin { android { ... } }` 블록 사용 (구 `androidLibrary` 는 deprecated).
  - `:androidApp` 는 AGP 내장 Kotlin 을 쓰므로 `org.jetbrains.kotlin.android` 를 **명시 적용하면 안 된다**('kotlin' extension 중복 오류).
- iOS 네이티브 컴파일/링크는 **macOS + Xcode 에서만** 가능. Windows 에선 iOS 타깃 자동 비활성(Android 빌드엔 무영향).
- 빌드 전 필요한 값(없으면 placeholder 로 빌드는 되지만 런타임 동작 안 함):
  - 루트 `secrets.properties` 의 `MAPS_API_KEY`, `GOOGLE_WEB_CLIENT_ID`
  - `androidApp/google-services.json` (현재 더미 → 실제 새 Firebase 파일로 교체)

## 2.5 ⚠️ 인앱 언어 전환 — "한국어밖에 안 된다" 3번 재발(2026-09-03 / 09-04 / 09-25). 건드리기 전에 반드시 읽을 것
원인이 **한 군데가 아니라 여러 층에 엮여 있어서**, 한 층만 고치고 "해결"로 착각하면 다른 층에서 다시 터진다.
언어·문자열·빌드 설정·`MainActivity`·`LocaleManager`·알림/리시버 코드를 바꿀 때는 아래를 **전부** 점검한다.
1. **적용 메커니즘(Android)** — `core/util/LocaleManager.kt` 가 버전별로 다르게 처리한다.
   API 33+ = 시스템 `android.app.LocaleManager.applicationLocales` 직접 set/get, API 26~32 = prefs + `MainActivity.attachBaseContext` 의 `wrap()`.
   - **`AppCompatDelegate.setApplicationLocales` 금지** — `MainActivity` 가 `ComponentActivity` 라 조용히 무시된다(09-04 재발 원인).
   - `attachBaseContext`/`LocaleManager` 를 고치면 **API 33+ 와 32 이하를 둘 다** 확인한다.
2. **배포(Play AAB)** — `androidApp/build.gradle.kts` 의 `bundle { language { enableSplit = false } }` 를 **지우지 말 것**(09-25 재발 원인).
   켜져 있으면 Play 가 기기 언어 리소스만 설치해서 스토어 설치본은 영어/일본어로 바꿔도 한국어로 폴백된다.
   **USB 디버그 APK 에는 모든 언어가 들어 있어 재현되지 않는다** → 언어 관련 수정은 Play 내부 테스트 설치본으로도 확인.
3. **하드코딩 한국어 금지** — UI 문자열은 `stringResource`/`context.getString`(Android), `locale.t(.키)`(iOS L10n).
   - 뷰모델은 문자열을 만들지 말고 리소스 id/enum 을 내보낸다(`DiaryEvent`, `FriendMessage` 패턴). **문자열을 비교 로직에 쓰지 말 것**(예전 `"저장 완료!"` 비교).
   - `NavRoute.title` 은 한국어 하드코딩(식별용) — 탑바 제목은 `MainScreen.localizedTitle()` 에서 리소스로. 새 라우트는 거기에 분기를 추가.
   - 점검 명령(주석·로그 제외 후 남는 한국어 리터럴 확인 — 2026-09-25 기준 남은 건 로그/개발용 진단/DB 저장용 이름/미사용 `MyScreen.kt` 뿐):
     `grep -rn '"[^"]*[가-힣][^"]*"' androidApp/src/main/java --include=*.kt | grep -v ':[0-9]*:\s*\(//\|\*\|/\*\)' | grep -v 'Log\.\|TestData'`
4. **새 문자열 키는 3곳 동시** — `values/`·`values-en/`·`values-ja/strings.xml`(하나라도 빠지면 그 키만 한국어). iOS 는 `L10n` 케이스에 ko/en/ja 튜플 동시.
   영어 `'` 는 XML 에서 `\'` 로(파이썬 heredoc 으로 편집할 때 이스케이프가 풀리는 함정 — PROJECT_NOTES 8.62 참고).
5. **API 32 이하에서 `applicationContext` 는 인앱 언어가 안 먹는다** — 리시버·서비스·알림 채널·`RelativeTime` 등은
   액티비티 Context 를 쓰거나 `LocaleManager.wrap(context)` 로 감싼다.
6. **확인 순서(수정 후 필수)**: 설정에서 English/日本語 전환 → 로그인 화면·지도·드로어·설정·상세·알림 배너·토스트까지 훑기
   → 가능하면 API 32 이하 에뮬레이터 1회 → 스토어(내부 테스트) 설치본 1회.

## 3. 마지막 검증 기준선
- 최근 검증: `:androidApp:assembleDebug` → **BUILD SUCCESSFUL** (디버그 APK 생성).
- 병합 매니페스트의 `com.google.android.geo.API_KEY` 가 placeholder(`TODO_ADD_YOUR_GOOGLE_MAPS_API_KEY`)로 정상 주입됨.
