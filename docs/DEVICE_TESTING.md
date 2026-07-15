# DEVICE_TESTING.md — 실기기 테스트 방법 (Android / iOS)

> 작성: 2026-07-08. "빌드 성공"과 "실기기 동작 확인"은 다른 단계다 — `CLAUDE.md` §1 워크플로의
> "빌드 성공 → 사용자 테스트 차례"가 바로 이 문서가 다루는 구간이다.
> 여기 적힌 절차대로 확인한 뒤 **"테스트 완료"** 라고 말하면 그때 `git push`(Android push 시점) /
> TestFlight 배포 결과 유지(iOS는 이미 push 시 CI가 컴파일 검증)로 넘어간다.

---

## 1. Android — USB 실기기 (가장 흔한 경로)

### 1.1 사전 준비 (최초 1회)
1. 폰에서 **설정 → 휴대전화 정보 → 빌드번호 7번 탭** → 개발자 모드 활성화.
2. **설정 → 개발자 옵션 → USB 디버깅** 켜기.
3. PC와 USB 연결 → 폰에 뜨는 "USB 디버깅 허용?" 팝업에서 **허용**(이 컴퓨터 항상 허용 체크 권장).
4. 확인:
   ```powershell
   adb devices
   ```
   기기 하나가 `device` 상태로 나오면 준비 끝(`unauthorized` 면 폰 팝업을 다시 확인).

### 1.2 빌드 + 설치
```powershell
# 빌드(코드가 바뀐 경우 항상 먼저 — CLAUDE.md §1 준수)
.\gradlew.bat :androidApp:assembleDebug --console=plain

# 설치 + 자동 실행까지 한 번에(연결된 기기가 1대일 때 가장 빠름)
.\gradlew.bat :androidApp:installDebug --console=plain
adb shell am start -n com.chaminwoo.stary_ios/com.chaminwoo.stary.MainActivity
```
- `installDebug` 대신 APK를 직접 넣고 싶으면: `adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- 여러 대 연결 시 `adb devices` 로 시리얼 확인 후 `adb -s <시리얼> install -r ...`.
- 재설치 시 `-r`(reinstall, 데이터 유지) 사용 — 지우고 새로 깔고 싶으면 먼저 `adb uninstall com.chaminwoo.stary_ios`.

### 1.3 현재 로컬 설정 상태(중요 — 실기능 테스트 전 확인)
- `secrets.properties`(루트, gitignore 대상) — **`MAPTILER_KEY`/`GOOGLE_WEB_CLIENT_ID`/`ORS_API_KEY` 값이 채워져 있음** →
  지도·구글 로그인·도보 길찾기가 **실제로 동작**한다(placeholder 아님).
- `androidApp/google-services.json` — **포크 전용 Firebase `momentdiary-f26c8`** 의 실제 설정 파일(더미 아님) →
  Firestore/Auth/Storage/FCM 전부 실제 백엔드로 붙는다. ⚠️ 원본 프로젝트(`momentdiary-52b78`)와는 무관.
- 즉 지금 이 저장소를 그대로 `assembleDebug`→설치하면 **모든 핵심 기능이 실동작**한다. 별도 키 발급 불필요.

### 1.4 릴리즈(서명) 빌드로 테스트하고 싶을 때
R8/ProGuard 축소가 적용된 실제 배포 형태를 확인하려면:
```powershell
.\gradlew.bat :androidApp:assembleRelease --console=plain
```
`androidApp/build.gradle.kts` 의 `signingConfigs.release` 는 `keystoreProps`(별도 keystore 파일)가 있을 때만 서명한다 —
없으면 서명 없이 빌드만 되고 기기 설치는 안 된다(디버그로 충분하면 1.2 방법 사용). 서명 keystore 위치/설정은
`docs/PROJECT_NOTES.md` 8.8 절 참고.

### 1.5 확인 로그 보기
```powershell
adb logcat *:E com.chaminwoo.stary_ios:V   # 에러 + 앱 자체 로그만
```
크래시나 이상 동작 시 이 창을 띄워둔 채로 재현하면 원인 파악이 빠르다.

---

## 2. iOS — TestFlight 경유 (맥 없이 하는 유일한 경로)

Windows 개발 환경이라 **Xcode로 기기에 직접 꽂아 설치하는 방법은 불가능**하다.
빌드·서명·배포는 GitHub Actions(macOS 러너)가 대신하고, 실기기 확인은 **TestFlight 앱**으로 한다.
(`.github/workflows/ios.yml` 의 `deploy` 잡, 상세 배경은 `docs/IOS_RELEASE.md` §2~5 참고.)

### 2.1 현재 상태 (2026-07-08 확인)
- ✅ 컴파일 파이프라인은 그린 — push 할 때마다 `build` 잡이 시뮬레이터 빌드로 컴파일 검증한다
  (최근: `0367fcb` **BUILD SUCCESS**, `docs/PROJECT_NOTES.md` 8.36-iOS 참고).
- ⚠️ **TestFlight 업로드(`deploy` 잡)는 아직 실행 불가** — GitHub 저장소에 **Actions Secrets 가 0개** 등록되어 있다
  (`APP_STORE_CONNECT_KEY_ID`/`APP_STORE_CONNECT_ISSUER_ID`/`APP_STORE_CONNECT_API_KEY`/`IOS_DEVELOPMENT_TEAM`/
  `GOOGLE_SERVICE_INFO_PLIST` 전부 미설정). 이 상태로 `upload: true` 를 눌러도 서명 단계에서 실패한다.
  → **아래 2.2를 먼저 1회 완료해야** 실기기 테스트가 가능하다.

### 2.2 최초 1회 설정 (맥 불필요, 웹에서 전부 가능)
1. **Apple Developer Program 가입**($99/년) — https://developer.apple.com/programs/
2. **App Store Connect**에서 앱 레코드 생성 — Bundle ID `com.chaminwoo.stary.ios`(⚠️ Apple 은 언더스코어 불가 — `stary_ios` 는 등록 자체가 안 됨, 2026-07-15 점 표기로 확정), 이름 Stary.
3. **App Store Connect API Key 발급**(Users and Access → Integrations → App Store Connect API, Role: App Manager) →
   `Issuer ID`, `Key ID`, `.p8` 파일 내용 3개 확보.
4. Firebase 콘솔(`momentdiary-f26c8`) → Bundle ID `com.chaminwoo.stary.ios` 인 iOS 앱의 `GoogleService-Info.plist` — 이미 `iosApp/Sources/GoogleService-Info.plist` 로 커밋되어 있음(재발급 불필요).
5. GitHub 저장소 → **Settings → Secrets and variables → Actions → New repository secret** 로 아래 5개 등록:

   | Secret 이름 | 값 |
   |---|---|
   | `APP_STORE_CONNECT_KEY_ID` | 3번의 Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | 3번의 Issuer ID |
   | `APP_STORE_CONNECT_API_KEY` | 3번의 `.p8` 파일 내용(텍스트 그대로) |
   | `IOS_DEVELOPMENT_TEAM` | Apple Developer 계정의 Team ID |
   | `GOOGLE_SERVICE_INFO_PLIST` | 4번 plist 파일을 **base64 인코딩**한 문자열 (`certutil -encode` 또는 `[Convert]::ToBase64String([IO.File]::ReadAllBytes("GoogleService-Info.plist"))` PowerShell) |

   이 5개는 한 번만 등록하면 이후 모든 배포에 재사용된다.

### 2.3 TestFlight로 빌드 올리기 (설정 완료 후, 배포마다 반복)
1. GitHub 저장소 → **Actions** 탭 → 왼쪽에서 **iOS** 워크플로 선택.
2. **Run workflow** 버튼 클릭 → 브랜치 선택 → **`upload` 체크박스를 켠다** → Run workflow.
3. `build` 잡(컴파일) → `deploy` 잡(fastlane beta → TestFlight 업로드) 순서로 실행, 총 15~25분 정도.
4. 성공하면 App Store Connect → TestFlight 탭에 새 빌드가 뜬다(Apple 자체 처리에 추가로 몇 분~십수 분 더 걸릴 수 있음).

### 2.4 아이폰에서 설치·테스트
1. 테스터로 등록된 Apple ID 이메일로 App Store Connect → TestFlight → **테스터 추가**(내부 테스터는 즉시, 외부 테스터는 첫 심사 필요).
2. 아이폰에 **TestFlight 앱**(App Store) 설치.
3. 초대 이메일의 링크(또는 App Store Connect가 발급하는 공개 링크) 열기 → TestFlight에서 **설치**.
4. 이후 새 빌드를 올릴 때마다(2.3 반복) TestFlight 앱에서 **업데이트**만 누르면 됨 — 재초대 불필요.

### 2.5 CI가 레드일 때
- 먼저 `build` 잡(컴파일)부터 그린으로 만들어야 `deploy` 잡이 실행된다(`needs: build`).
- 실패 로그 확인 절차(이번 세션에 확립):
  ```bash
  # 1) 저장소가 public → 인증 없이도 되지만 API 호출 제한(60/시)이 낮아 토큰 사용 권장
  TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')
  # 2) 최신 run id 확인
  curl -s -H "Authorization: Bearer $TOKEN" \
    "https://api.github.com/repos/Chaminwoo/Stary/actions/workflows/ios.yml/runs?per_page=1"
  # 3) 로그 zip 다운로드 후 압축 해제, "error:" 검색
  curl -sL -H "Authorization: Bearer $TOKEN" -o log.zip \
    "https://api.github.com/repos/Chaminwoo/Stary/actions/runs/<run_id>/logs"
  unzip -o -q log.zip -d log && grep -rn "error:" log/
  ```
  `gh` CLI는 이 환경에 설치되어 있지 않음 — 위 curl 방식 사용.

---

## 3. 빠른 판단 기준

| 하고 싶은 것 | 방법 |
|---|---|
| 코드 바꾼 뒤 안드로이드에서 바로 확인 | §1 (USB, 지금 바로 가능) |
| 배포용 서명 빌드가 실제로 도는지 확인 | §1.4 |
| iOS 코드가 컴파일되는지만 확인 | push 하면 자동(별도 조작 불필요) |
| iOS를 아이폰에서 직접 눌러보고 싶음 | §2 — **2.2 시크릿 등록이 먼저** (아직 미완료) |
