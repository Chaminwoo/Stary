# SECURITY_AUDIT.md — Stary-Project 보안 점검 (2026-07-18)

전체 코드/설정 보안 점검 결과. **가장 큰 위험은 Firebase 보안 규칙**이었고, 이번에
`firestore.rules` / `storage.rules` 를 소유권 기반으로 재작성해 대응했다(에뮬레이터 문법 검증 완료).
**아직 배포 전** — 사용자가 `firebase deploy` 로 적용해야 실제 서버에 반영된다.

---

## 0. 요약(심각도순)

| # | 심각도 | 항목 | 상태 |
|---|--------|------|------|
| 1 | 🔴 Critical | Firestore 규칙이 `request.auth != null` 만 검사 + **익명 로그인 활성** → 누구나 전 DB read/write | ✅ 규칙 재작성(배포 대기) |
| 2 | 🔴 Critical | 위 규칙 + `deletionRequestedAt` 오염으로 **전 계정 대량 삭제** 유발 가능(서버 스케줄러 악용) | ✅ users 쓰기 본인 전용화 |
| 3 | 🟠 High | 1:1 채팅을 로그인만 하면 **누구나 열람/위조** | ✅ 참여자 전용화 |
| 4 | 🟠 High | 남의 `fcmToken` 덮어쓰기 → **푸시 알림 가로채기/차단** | ✅ users 쓰기 본인 전용화 |
| 5 | 🟠 High | `profile_images/{sub}.jpg` 를 **누구나 덮어쓰기**(프로필 사진 변조) | ✅ 소유자 경로 전용화 |
| 6 | 🟡 Medium | 다이어리/댓글/좋아요/알림/친구요청 **타인 명의 위·변조·삭제** | ✅ 소유권 강제 |
| 7 | 🟡 Medium | 히든 업적/개척 선점 **익명 그리핑**(create-only라 덮어쓰기는 원래 불가) | ✅ Google 세션+본인 명의로 |
| 8 | 🟢 Low | Storage 전체 **공개 read**(경로 열거) | ✅ 로그인 세션으로 |
| 9 | 🟢 Low/정보 | `allowBackup=true`, Firebase API 키 제한/App Check 부재 | ⚠️ 권고(아래) |

**시크릿 관리는 양호**: `secrets.properties`/`keystore.properties`/`*.jks`/`local.properties`/`google-services.json`
모두 gitignore + 미추적. git 히스토리에도 실제 키·서명 비밀번호 유출 없음(`AIza…` 검색 시 iOS `GoogleService-Info.plist`
의 Firebase 클라이언트 API 키만 — 이는 앱 바이너리에 포함되는 공개 식별자로, CLAUDE.md §0 의 명시적 예외).
Android 앱에 cleartext HTTP·WebView 사용 없음. 외부 호출(ORS/MapTiler)은 전부 HTTPS. 딥링크·웹 랜딩에 XSS 없음.

---

## 1. 🔴 근본 원인 — 규칙이 인증 세션만 확인, 소유권 미검증

### 문제
- `StaryApplication` 이 로그인 전(둘러보기 포함) **익명 로그인**으로 세션을 만든다 → `request.auth != null` 은
  **아무나** 충족한다(익명 인증은 Firebase 클라이언트 키만 있으면 스크립트로도 가능).
- 예전 `firestore.rules` 는 거의 모든 컬렉션에 `allow read, write: if request.auth != null` 뿐이었다.
- 결과: **인터넷의 누구나** 공개된 Firebase 설정으로 익명 세션을 얻어
  - 모든 사용자의 **1:1 채팅 전문 열람**,
  - 임의 **다이어리 삭제/변조**, 남 명의로 글·댓글·좋아요·알림 작성,
  - 임의 **프로필 문서 덮어쓰기**(이름/사진/`fcmToken`/`authUid`/`deletionRequestedAt`),
  - 특히 모든 `users/*.deletionRequestedAt` 를 과거값으로 심으면 **자정 스케줄러(`purgeExpiredDeletions`)가 전 계정 데이터를 삭제**.

### 왜 고칠 수 있었나 (핵심)
앱 `userId` = **Google sub** 이고, 이 값은 규칙 안에서 **위조 불가**하게 읽을 수 있다:
```
request.auth.token.firebase.identities['google.com'][0]
```
→ `users/{uid}` 문서 id, `diaries.userId`, `likes/{uid}`, `comments.userId`, `notifications.actorId/diaryOwnerId`,
`chats/{a_b}`, `invites.redeemerId`, `hiddenAchievements.achieverId` 가 전부 이 값과 같으므로 **소유권을 규칙에서 강제**할 수 있다.
익명 세션엔 `google.com` identity 가 없어(`isGoogle()=false`) 소유가 필요한 쓰기는 자동 차단(둘러보기 전용).

### 대응(재작성된 규칙 요지)
- `isGoogle()` / `mySub()` / `ownsField()` / `isChatMember()` 헬퍼 도입.
- 다이어리: 읽기=로그인 / 생성=`userId==나` / 수정=소유자(전체) 또는 **비소유자는 카운터(좋아요·댓글·조회수)만** / 삭제=소유자.
- 채팅: 방·메시지 모두 **참여자(chatId 안의 두 sub)만**, 메시지 생성=`senderId==나`, 삭제=본인+1분 이내.
- users: 읽기=로그인 / **쓰기=본인 문서만**(fcmToken/authUid/삭제예약 보호). 친구 하위=당사자만, 열람·차단=본인만.
- 알림: 생성=행위자 본인 / 읽기·수정·삭제=수신자 본인.
- 친구요청: 생성=보낸이 / 읽기·삭제=당사자. 신고: 생성만(읽기·수정·삭제 불가).
- 히든업적: 생성=Google+본인명의 / 수정 불가 / 삭제=본인 선점만(어드민 자가치유 유지). 개척선점: 생성만(Google).
- 초대: 생성만, 문서id·redeemerId=나 & **자기 초대 금지**.

배포:
```
firebase deploy --only firestore:rules      # named DB(stary-db) 는 firebase.json 에 지정됨
firebase deploy --only storage
```

---

## 2. 규칙만으로 못 막는 잔여 한계(후속 권고)

1. **private/friends 다이어리 기밀** — `observeAllDiaries` 가 정렬+limit 로 **전 컬렉션**을 받아 클라이언트에서
   private 를 거른다. 문서별 가시성 규칙을 걸면 이 쿼리가 통째로 막히므로, 지금은 읽기를 로그인 세션에 열어 두고
   **클라이언트 필터에 의존**한다. 근본 해결은 private 를 `users/{uid}/privateDiaries` 하위로 분리하거나
   친구 공개용 별도 컬렉션/필드 인덱스로 재설계.
2. **users 문서 read 노출** — 이름/사진 검색 때문에 `users/{uid}` 읽기를 로그인 세션에 열어 둬 `fcmToken` 등도 읽힌다.
   (쓰기는 본인만이라 **탈취는 불가**하고, FCM 토큰만으론 발송도 불가하나) 민감 필드는
   `users/{uid}/private/*` 하위(본인 read 전용)로 옮기면 완전 차단 가능.
3. **친구 자기추가 그리핑** — 수락자가 상대 friends 하위에 자신을 넣는 구조라 규칙상 "요청 없이 나를 남의 친구로 추가"가
   가능하다. 근본 차단은 `acceptRequest` 를 **Cloud Function(콜러블)** 로 옮겨 서버가 pending 요청을 검증하게 하거나,
   friendRequest 문서 id 를 결정적(정렬 결합)으로 만들어 규칙에서 `exists()` 로 검증.
4. **카운터 반달리즘** — 비소유자가 좋아요/조회수 카운터를 임의 값으로 바꿀 수 있다(내용 변조는 불가). 정밀 차단은
   증가량을 서버(Function/트랜잭션 규칙)로 강제해야 하나 영향이 낮아 후순위.

---

## 3. 플랫폼/기타

- **Android 매니페스트**: `MainActivity exported=true`(런처라 필수), FileProvider/메시징 서비스 `exported=false` — 정상.
  `stary://diary|invite` 딥링크는 BROWSABLE 이나 지도 포커스/초대 리딤(서버 create-only+창+자기초대금지)로 게이팅 — 저위험.
  - 권고: `android:allowBackup="true"` → **`false`**(루팅/adb 로 로컬 앱 데이터[SharedPreferences·캐시] 추출 방지).
- **Cloud Functions**: Admin SDK 로 규칙 우회(정상). `onReportAction` 은 Console 관리자만(규칙이 reports update 차단).
  의존성 `firebase-admin ^12.7 / firebase-functions ^6.1` — 최신권, 알려진 치명 취약점 없음.
- **web/index.html**: URL 코드가 `encodeURIComponent` 로만 href 에 삽입, 나머지 innerHTML 은 정적 문자열 — XSS 없음.
- **권고(defense-in-depth)**: **Firebase App Check** 활성화(정품 앱만 Firestore/Storage/Functions 호출 가능 →
  익명-키 악용 표면 자체를 축소) + Google Cloud Console 에서 Firebase/Maps API 키에 앱·API 제한 적용.
  익명 로그인이 꼭 필요 없으면 **익명 인증 비활성**도 검토(현재는 둘러보기·업로드 세션 확보에 사용).

---

## 4. 검증

- `firebase emulators:exec --only firestore,storage` 로 두 규칙 파일 **문법 컴파일 성공**(JBR 21 사용).
- 규칙 변경은 Gradle/Android 빌드에 영향 없음(코드 미변경).
- ⚠️ 실제 적용 = 사용자가 위 `firebase deploy` 실행. 배포 후 **정상 동작 스모크 테스트 필수**:
  로그인→업로드→지도 표시→좋아요/댓글→친구 요청·수락→채팅 송수신→프로필 사진 변경→닉네임 변경→(가능하면)신고.
