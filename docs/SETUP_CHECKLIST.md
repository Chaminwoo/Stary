# SETUP_CHECKLIST.md — 사용자 구현 체크리스트

> 이 분기 프로젝트를 처음부터 돌아가게 만들기 위해 **사용자가** 해야 할 일.
> 각 단계에 ⌨️ 실행 명령(Windows PowerShell 기준)과 🤖 Claude 에게 줄 지시를 함께 적었다.
> 푸시 규칙: **로컬 테스트가 끝나기 전에는 푸시하지 않는다.** (자세한 건 `CLAUDE.md`)

작업 경로: `C:\Users\User\AndroidStudioProjects\Stary-Project`

---

## 0. 사전 준비 (도구 확인)
- [ ] Git / GitHub CLI 설치 확인. JDK(17+), Android Studio, Android SDK 설치 확인.

⌨️
```powershell
git --version
gh --version          # 없으면: winget install GitHub.cli   (또는 웹으로 레포 생성)
java -version
```

---

## 1. GitHub 레포 생성 & 연결
- [ ] 로컬을 git 저장소로 초기화하고 최초 커밋을 만든다. (아직 푸시는 안 함)
- [ ] GitHub 원격 레포를 만들고 origin 연결한다.

⌨️ (1-A) 로컬 git 초기화 + 첫 커밋
```powershell
cd C:\Users\User\AndroidStudioProjects\Stary-Project
git init -b main
git add .
git status        # secrets.properties / google-services.json / local.properties 가 안 올라가는지 확인!
git commit -m "chore: KMP 분기 초기 구성 (네이버맵->Google Maps, 시크릿 TODO)"
```

⌨️ (1-B) 원격 레포 생성 — gh CLI 사용 시 (푸시는 테스트 후이므로 --push 생략)
```powershell
gh auth login                                   # 최초 1회
gh repo create stary-project --private --source=. --remote=origin
```

⌨️ (1-B') gh 없이 웹으로 만들 때: github.com 에서 빈 레포 생성 후
```powershell
git remote add origin https://github.com/<your-id>/stary-project.git
```

- [ ] ⚠️ `git status`/커밋에 **secrets.properties, google-services.json(실제), local.properties 가 포함되지 않았는지** 반드시 확인. (`.gitignore` 처리되어 있어야 정상)

🤖 Claude 에게: 푸시는 이 시점에 하지 않는다. **6번(로컬 테스트) 완료 후** "테스트 완료" 라고 말하면 그때 Claude 가 푸시한다.

---

## 2. 시크릿 파일 만들기 (`secrets.properties`, 커밋 금지)
- [ ] 예시 파일을 복사해 실제 값으로 채운다. (값은 4·5번에서 발급)

⌨️
```powershell
Copy-Item secrets.properties.example secrets.properties
notepad secrets.properties
```
```properties
MAPS_API_KEY=여기에_Google_Maps_키
GOOGLE_WEB_CLIENT_ID=여기에_웹_OAuth_클라이언트ID
```

---

## 3. 새 Firebase 프로젝트 생성 + `google-services.json`
> ⚠️ 원본 운영 프로젝트(`momentdiary-*`)에 연결하지 말 것. **새 프로젝트**를 만든다.
- [ ] Firebase Console 에서 **새 프로젝트** 생성.
- [ ] Android 앱 추가 — 패키지명 **`com.chaminwoo.stary`**.
- [ ] **Firestore**, **Storage** 사용 설정. **Authentication → Google 로그인** 사용 설정.
- [ ] `google-services.json` 다운로드 → 더미 파일을 교체.

⌨️ (다운받은 파일로 교체)
```powershell
Copy-Item "$env:USERPROFILE\Downloads\google-services.json" `
  "C:\Users\User\AndroidStudioProjects\Stary-Project\androidApp\google-services.json" -Force
```
- [ ] (참고) Firestore 보안 규칙/인덱스는 앱 쿼리에 맞춰 설정. 컬렉션: `diaries`(+`comments`,`likes`), `notifications`, `users`.

---

## 4. Google Maps API 키 발급
- [ ] Google Cloud Console(Firebase와 동일 프로젝트) → **APIs & Services → Maps SDK for Android** 사용 설정.
- [ ] **Credentials → Create credentials → API key** 발급. (Android 앱 + SHA-1 로 키 제한 권장)
- [ ] 발급한 키를 `secrets.properties` 의 `MAPS_API_KEY` 에 입력.

⌨️ (선택) gcloud 로 API 활성화
```powershell
gcloud services enable maps-android-backend.googleapis.com --project <PROJECT_ID>
```

---

## 5. Google 로그인(OAuth) + SHA-1 등록
- [ ] 디버그 키스토어의 **SHA-1** 을 확인한다.
- [ ] Firebase Console → 프로젝트 설정 → 내 앱 → **SHA 인증서 지문 추가** 에 SHA-1 등록 후 `google-services.json` 재다운로드.
- [ ] Console 의 **Web client (auto created by Google Service)** 의 client_id 를 `secrets.properties` 의 `GOOGLE_WEB_CLIENT_ID` 에 입력.

⌨️ SHA-1 확인 (둘 중 하나)
```powershell
cd C:\Users\User\AndroidStudioProjects\Stary-Project
.\gradlew.bat :androidApp:signingReport         # 출력에서 Variant: debug 의 SHA1 사용
```
```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

---

## 6. 빌드 & 로컬 테스트
- [ ] 디버그 APK 빌드가 성공하는지 확인.
- [ ] 기기/에뮬레이터에서 실행: 지도 표시 / 현재위치 / 다이어리 마커 / 100m 열람 / 로그인 / 업로드 / 좋아요·댓글·알림 확인.

⌨️ 빌드
```powershell
cd C:\Users\User\AndroidStudioProjects\Stary-Project
.\gradlew.bat :androidApp:assembleDebug
```
⌨️ 설치 (연결된 기기)
```powershell
.\gradlew.bat :androidApp:installDebug
```

🤖 Claude 에게:
- 빌드만 대신 돌려보게 하려면 → **"빌드 확인해줘"**
- 빌드 에러가 나면 → 에러 로그와 함께 **"이 에러 고쳐줘"**

---

## 7. 첫 푸시 (테스트 통과 후)
- [ ] 6번 테스트가 정상이면 푸시한다.

🤖 Claude 에게: **"테스트 완료"** 라고 말하면 Claude 가
`git push -u origin main` 으로 푸시하고 `docs/PROJECT_NOTES.md` 를 최신화한다.

⌨️ (직접 푸시할 경우)
```powershell
git push -u origin main
```

---

## 8. (추후) iOS 확장
- [ ] macOS + Xcode 환경에서 Xcode 프로젝트 추가 후 `:shared` 프레임워크 임포트.
- [ ] `shared/.../data/repository/Repositories.kt` 의 인터페이스를 iOS(Firebase iOS SDK)로 구현.
- [ ] iOS 지도: Google Maps SDK for iOS 또는 MapKit 으로 `DiaryGoogleMap` 대응 화면 구현.

🤖 Claude 에게: iOS 작업 시작할 때 **"iOS 쪽 작업 시작하자"** 라고 하면 `PROJECT_NOTES.md`의 남은 TODO 기준으로 진행.

---

## 빠른 체크 요약
| 단계 | 핵심 명령 |
|---|---|
| 1 git/레포 | `git init -b main` → `git add .` → `git commit` → `gh repo create ... --remote=origin` |
| 2 시크릿 | `Copy-Item secrets.properties.example secrets.properties` |
| 3 Firebase | Console에서 새 프로젝트 → `google-services.json` 교체 |
| 4 Maps키 | Cloud Console에서 키 발급 → `secrets.properties` |
| 5 SHA-1 | `.\gradlew.bat :androidApp:signingReport` |
| 6 빌드 | `.\gradlew.bat :androidApp:assembleDebug` / `installDebug` |
| 7 푸시 | (테스트 후) Claude에게 "테스트 완료" 또는 `git push -u origin main` |
