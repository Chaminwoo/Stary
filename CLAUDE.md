# CLAUDE.md — Stary-Project 작업 규칙

이 파일은 Claude Code 가 매 세션 자동으로 읽는 **규칙/워크플로 파일**이다.
코드 구조·분석 내용은 [`docs/PROJECT_NOTES.md`](docs/PROJECT_NOTES.md) 에 정리되어 있으니
작업 시작 전 그 파일부터 읽으면 코드를 처음부터 다시 읽지 않아도 된다.
화면·기능별 유지보수 문서(변수/함수/컴포넌트 연결/iOS 값 조절 매핑)는 [`docs/code/`](docs/code/README.md) 참고
— 특정 화면을 고칠 땐 해당 문서부터 읽고, **그 화면을 크게 바꾼 뒤에는 그 문서도 갱신**한다.
기능 개발 로드맵/체크리스트는 [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md) 참고(초기 셋업은 완료됨).
버전별 사용자 관점 변경 요약(스토어 출시 노트용)은 [`docs/PATCH_NOTES.md`](docs/PATCH_NOTES.md) — 새 버전을 낼 때 여기에 추가한다.
실기기(Android USB / iOS TestFlight) 테스트 방법은 [`docs/DEVICE_TESTING.md`](docs/DEVICE_TESTING.md) 참고.
iOS 보상형 광고(LevelPlay/AdMob) 키·대시보드 등 사용자가 직접 할 일은 [`docs/IOS_ADS_SETUP.md`](docs/IOS_ADS_SETUP.md) 참고.

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

## 2.6 ⚠️ 재로그인/재설치 때 "프로필 사진·이름 초기화" — 반복 재발(2026-08-15 1차 수정 후에도 2026-10-09 재발). 로그인·프로필 코드를 건드리기 전에 반드시 읽을 것
증상: 재로그인하거나 앱을 지웠다 깔면 닉네임/프로필 사진이 구글 기본값으로 돌아간다(서버 `users/{uid}` 값이 실제로 덮어써져 **다른 사람 화면에도** 기본값이 나간다).
**원인(코드 분석으로 확정한 약점 — Windows 라 실기기 재현은 못 했다)**:
1. 로그인 시 `users/{uid}` 를 **기본 `getDocument()`(iOS) / `get()`(Android)** 로 읽었다. 이 기본 읽기는 서버에 못 닿으면 **로컬 캐시를 "성공"으로 돌려준다.**
   재설치 직후 캐시는 비어 있고, 같은 순간 앱이 쓴 `fcmToken`·`authUid`·`termsAcceptedAt` 같은 **쓰기 대기분만 있는 불완전한 문서**가 된다.
   → "문서 있음 + userName/profileImageUrl 없음" 으로 오판 → 뒤이은 `setData(merge)`/`upsertProfile` 이 **서버의 진짜 닉네임·사진을 구글 기본값으로 덮어썼다.**
   (예전 방어 "읽기 실패 ≠ 문서 없음" 은 *예외가 날 때만* 통했다 — 캐시 응답은 예외가 아니라 못 걸렀다.)
2. 읽기 실패 때도 구글 기본값을 `UserDefaults nickname_{uid}` 캐시에 저장해, 이후 실패 폴백이 그 오염된 값을 믿었다. 실패 후 **재시도도 없었다**(다음 로그인까지 기본값 방치).
3. Sign in with Apple 은 이름을 **최초 1회만** 준다. 같은 로그인에서 리스너의 첫 `ensureProfile` 이 임시값(이메일)을 먼저 저장하면, 두 번째 호출이 그걸 "이미 있는 이름"으로 우선해 실명이 영구 손실됐다.

**지켜야 할 규칙**:
- 프로필(닉네임/사진)을 **덮어쓸지 판단하는 읽기는 반드시 서버 확정값**으로: iOS `getDocument(source: .server)`(`AuthManager.fetchServerProfile`), Android `get(Source.SERVER)`(`GoogleAuthHelper.signInWithGoogle`). 캐시 응답을 믿고 기본값을 쓰는 코드 금지.
- **읽기 실패 / 문서 없음 / 문서는 있으나 필드 없음**을 구분한다. 읽기 실패면 서버·기기 캐시 **둘 다 쓰지 말고**, 재시도(iOS: 앱 복귀 시 `retryProfileSyncIfNeeded`)한다. 기본값을 닉네임 캐시에 굳히지 말 것.
- `users/{uid}` 에 `userName`/`profileImageUrl` 을 쓰는 곳은 `ensureProfile`/`upsertProfile`(로그인), `setNickname`, `ImageUploader.uploadProfile` **뿐**이어야 한다. 다른 곳에서 구글 기본값을 써 넣는 코드를 새로 만들지 말 것(항상 `merge`, 값이 있으면 보존).
- 화면의 내 사진은 `auth.photoUrl`(로그인 때 서버 확정) + 서버 우선 읽기. 기본 `getDocument()` 한 번 읽고 nil 이면 "사진 없음" 처리하지 말 것.
- 확인 순서: ① 닉네임·사진을 바꾼 계정으로 로그아웃→재로그인 ② 앱 삭제→재설치→로그인 ③ 비행기 모드/느린 망에서 위 둘 → 닉네임·사진이 유지되고, 망이 돌아오면 서버 값으로 맞춰지는지. Firestore 콘솔의 `users/{uid}` 값도 직접 확인.

## 2.7 ⚠️ iOS 알림 탭 → 다이어리 이동/열기 안 됨(2026-10-09 수정). 알림·딥링크 코드를 건드릴 때 읽을 것
원인 3가지: ① `NotificationsScreen`(알림 목록) 행에 **탭 핸들러가 아예 없었다**(Android `NotificationScreen` 의 onClick 이 iOS 에 이식되지 않음). ② 푸시 탭/지도 포커스 `MapScreen.handleFocus` 가 대상 별이 `store.diaries` 에 **아직 없으면 조용히 return + 요청도 소비 안 함** — 알림으로 앱을 막 켠 경우(목록 로드 전)엔 재시도하는 곳이 없어 영영 안 움직였다. ③ 인앱 배너가 친구 새 글도 상세로 직행(열람 거리 잠금 우회)했고, 목록에 없으면 무반응.
- 알림 → 이동 분기는 **`PushRoute.from(_ n: AppNotification)` 한 곳**(푸시 탭 `PushManager.didReceive`, 알림 목록 행, 인앱 배너가 모두 이걸 쓴다). Android 와 같은 분기: 친구 요청→친구 화면 / 친구 새 글·`FIRST_STAR`→지도 포커스(카메라+파장) / 좋아요·댓글→상세. **새 알림 type 을 추가하면** 서버 `functions/index.js` data · Android `NotificationScreen`/`MainScreen` · iOS `PushRoute.from`/`PushManager.didReceive` 를 함께 고친다.
- 알림이 가리키는 별을 열 때는 `DiaryStore.diary(id:viewerUid:)` 를 쓴다(구독 목록 → 없으면 Firestore 직접 읽기, 남의 나만 보기 글 제외). **`store.diaries.first(where:)` 만 믿고 없으면 무시하는 코드 금지** — 못 찾으면 토스트(`notifDiaryGone`)로 알린다.
- 지도 포커스 요청은 `MapFocusStore` 에 쌓이고 `handleFocus` 가 **목록 변화(`onChange`)마다 재시도**해 소비한다. 여기에 "없으면 return" 만 남기는 수정 금지.
- iOS 알림 확인은 **실기기 TestFlight** 에서(시뮬레이터는 APNs 불가): 앱 종료 상태/백그라운드/전면 각각에서 좋아요·댓글·친구 새 글 알림을 눌러 상세·지도 포커스로 가는지, 앱 안 알림 목록(하트) 행을 눌러도 같은지.

## 3. 마지막 검증 기준선
- 최근 검증: `:androidApp:assembleDebug` → **BUILD SUCCESSFUL** (디버그 APK 생성).
- 병합 매니페스트의 `com.google.android.geo.API_KEY` 가 placeholder(`TODO_ADD_YOUR_GOOGLE_MAPS_API_KEY`)로 정상 주입됨.
