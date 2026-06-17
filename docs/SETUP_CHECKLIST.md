# CHECKLIST — Stary-Project 진행 체크리스트

> 셋업(레포·시크릿·Firebase·Maps·로그인)은 **완료**됨. 이 문서는 이제 **기능 개발 로드맵**이다.
> 작업 규칙/푸시 규칙은 `CLAUDE.md`, 코드 구조는 `docs/PROJECT_NOTES.md` 참고.
> 작업 경로: `C:\Users\User\AndroidStudioProjects\Stary-Project`

---

## ✅ 완료 (아카이브 — 더 안 건드림)
- GitHub 레포(`origin` = Chaminwoo/Stary) 생성 + main 푸시.
- `.gitignore` 정비, 시크릿 템플릿(`secrets.defaults/.example`) 제거.
- `applicationId` 분리: `com.chaminwoo.stary_ios` (namespace는 `com.chaminwoo.stary` 유지).
- 새 Firebase `momentdiary-f26c8` 연동(`google-services.json`), SHA-1 등록.
- `secrets.properties` 채움 → **지도 타일 로딩 + Google 로그인 에뮬레이터 동작 확인**.
- `MapsActivity` 중복 LAUNCHER 제거(런처 진입점 = `MainActivity` 단일).
- **지도 엔진 Google Maps → MapLibre + MapTiler 전환** + 커스텀 다크 스타일(배경/물/큰길, 줌 색보간) — 동작 확인.

---

## 🗺️ A. 지도 전면 리스타일 (= 아래 1번) — MapLibre + MapTiler

> Google Maps는 레이어 선택 로드가 안 돼(가린 레이어도 다운로드됨) **MapLibre GL Native + MapTiler 벡터 타일**로 전환함.
> 스타일은 자체 작성 `res/raw/maplibre_style.json`(MapLibre 스타일 스펙). 필요한 레이어만 넣어 불필요한 렌더/다운로드 없음.

### A-1. 베이스 맵 색상 — ✅ 완료
- [x] `res/raw/maplibre_style.json`: source=MapTiler `tiles/v3`, layers = **background / water / road-major만**.
      배경 검정, 물, 큰 길(`class` ∈ motorway~tertiary). 건물·POI·라벨은 스타일에 아예 없음.
- [x] 키 주입: `BuildConfig.MAPTILER_KEY` → 스타일의 `__MAPTILER_KEY__` 치환 (`DiaryMap.kt`).

### A-2. 줌별 표현 — ✅ 완료
- [x] 저줌 길 숨김: road-major `minzoom`(현재 8) 미만 줌에선 바다+땅만.
- [x] 줌 색 보간: bg/water/road `paint` 색을 `["interpolate",["linear"],["zoom"],6,..,16,..]` 로(줌아웃=밝게, 줌인=검정계).

### A-3. 마커 (별 종류×색상, 4번과 연동) — ✅ 완료 (기능 배치 1 + 버그 라운드 1·2)
- [x] 다이어리 별 마커: `StarStyle.starPath` 5종 × 12색 Path 직접 렌더(`starBitmap`), 위상 그룹 4레이어.
- [x] 색상 12색 / 종류 5형 `core/designsystem/StarStyle` 상수(렌더·업로드 공용).
- [x] 마커 클릭 → 100m 게이팅(`LocationHelper.distanceBetween` ≤ `StaryConfig.DIARY_OPEN_RADIUS_M`).

### A-4. 파티클 효과 — ✅ 완료 (MapLibre 전환, 2026-06-13)
- [x] ~~`StarParticleOverlay`(Compose Canvas)~~ → **삭제. MapLibre GeoJSON(`star-particles`) + SymbolLayer 4개로 재구현**:
      시드 고정 400개(반경 20km), 줌 6 이하 숨김(6→10 등장), depth 별 크기, 레이어별 위상 반짝임. 컬링은 MapLibre.

### A-5. 정리 — ✅ PROJECT_NOTES 6절(지도) 갱신 완료.

---

## 🐞 버그/피드백 라운드 1 (2026-06-11 테스트 피드백)

> 📌 **DB 경위**: 사용자가 DB를 `(default)` 가 아닌 **named DB `stary-db`** 로 생성 →
> 앱이 `stary-db` 를 바라보도록 수정 완료(`StaryConfig.FIRESTORE_DB_ID` + `staryFirestore` 헬퍼,
> firebase-bom 26.2.0→**33.7.0** 업그레이드 — named DB API 필요). NOT_FOUND 해소 확인 ✅.
>
> ⚠️ **남은 콘솔 작업(사용자) — 이것만 하면 업로드/조회 전부 풀림**:
> **① stary-db 보안 규칙 변경** (현재 잠금 모드 → PERMISSION_DENIED):
>   Console > Firestore Database(stary-db) > 규칙 탭에 아래로 교체 후 게시:
>   ```
>   rules_version = '2';
>   service cloud.firestore {
>     match /databases/{database}/documents {
>       match /{document=**} {
>         allow read, write: if request.auth != null;
>       }
>     }
>   }
>   ```
> **② Authentication > Sign-in method > 익명(Anonymous) 사용 설정** —
>   앱이 비로그인(둘러보기) 시 익명 인증으로 규칙을 통과하도록 구현돼 있음
>   (현재 로그: "This operation is restricted to administrators only" = 익명 비활성).
> **③ Storage 시작하기**(이미지 업로드용; 기본 규칙이 auth 기반이라 ①②후 바로 동작).

- [x] 1. ~~길찾기~~ → **길찾기 기능 전부 삭제**(외부 앱·지도 내 직선 경로 모두). 100m 밖 별 클릭 시 거리 안내 토스트만.
- [x] 2. "내 위치로" 버튼 복구 (생성 FAB 위에 다시 추가).
- [x] 3. 별 마커 제대로 안 보임 → 1차(비율/크기) 수정으로도 대각선 빗금 → 원인: **PNG 디코드→GL 텍스처 경로가 깨짐(에뮬레이터)**.
      **PNG 폐기, `StarStyle.starPath` 로 5종 별을 Path 직접 렌더**(글로우+본체+흰 하이라이트). 스크린샷 검증 완료 ✅.
      업로드 피커도 같은 Path 모양으로 통일(마커=피커 일치).
- [x] 4. 마커 애니메이션 복원: **100m 이내 별 확대**(near 데이터 기반 크기) + **pulse**(근접 별 맥동) + **float**(상하 부유).
- [x] 5. 업로드 후 토스트/화면전환 안 됨 → 근본은 콘솔(①). 앱 쪽도 **친구 알림 생성을 fire-and-forget** 으로 분리.
- [x] 6. 로그인 후 화면전환 안 됨 → 동일 근본(①). 앱 쪽도 **프로필 upsert 를 fire-and-forget** 으로 분리(로그인 흐름 비차단).
- [x] 7. 인트로 GIF 너무 느림 → 기본 재생 속도 상향(2.5x 시작), 종반 감속 하한 0.15x→0.5x, 로그인 UI 등장 3s→1.5s.
- [x] 9. (라운드 1.5) 별 꼭지가 뭉뚝함 → 스파클 계열(0/3/4)을 **오목 곡선(quadTo) 변**으로 변경 + 글로우 블러 14→10,
      5각/6각 내접비 축소 → 날카롭게 반짝이는 인상. 업로드 피커 스크린샷 검증 ✅.
- [x] 10. (라운드 1.5) PERMISSION_DENIED 시 **앱 크래시** → 스냅샷 리스너의 `close(error)` 제거(에러 무시, Flow 유지).
- [x] 11. (라운드 1.5) **Firebase Auth 연동 추가**: Google 로그인 시 `signInWithCredential`, 비로그인 시 익명 로그인,
      로그아웃 시 `FirebaseAuth.signOut()` — 보안 규칙(request.auth != null) 대응. (콘솔 ② 필요)

## 🐞 버그/피드백 라운드 2 — ✅ 전부 완료 (스크린샷 검증)
- [x] 1. 지도 팬/줌 끊김 → 원인: 50ms 애니메이션 `setProperties` + 위치 갱신마다 GeoJSON 재생성.
      **카메라 이동 중 애니메이션 일시정지** + **다이어리/near 집합 변화 시에만 재생성**.
- [x] 2. 마커 중심 코어: 흰색·작음(분리감) → **원색 계열 코어 + 80% 크기**(사용자 튜닝값 0.05/0.8 유지).
- [x] 3. 로그인 중 지도 미리 렌더 → **Login 을 라우트가 아닌 MainScreen 오버레이로** 변경(NavHost start=Main).
      GIF 도는 동안 지도 로딩 → 로그인 직후 즉시 표시. 로그아웃 시 오버레이 복귀.
- [x] 4. float 위상 분산: 마커를 **4개 위상 그룹 레이어**(id 해시 % 4, phaseGroup 필터)로 나눠 그룹별 위상차 적용
      → 별들이 따로따로 부유/맥동.
- [x] 5. 줌아웃 시 마커 축소: iconSize 를 **줌 보간**(8→0.3x, 12→0.55x, 15→1x) × near × pulse 합성 표현식으로.
- [x] 6. 별 색 밝게: 팔레트 전체에 **흰색 30% 혼합**(`lerp(c, White, 0.3f)`) — 피커·마커 공통.
- [x] 8. "Storage 에 다이어리가 없음" → **다이어리 본문/메타는 Firestore(diaries 컬렉션)에 저장**되고 Storage 에는 첨부 이미지만 올라감.
      현재는 ①((default) DB 미생성) 때문에 서버에 아무것도 못 올라간 상태. 이미지 업로드는 ②(Storage 시작) 필요.

---

## 📋 기능 백로그 (의존성 순서 고려)

### 1. 지도 UI 리팩토링 — ✅ 완료 *(A-1~A-4 전부)*
- [x] MapLibre 전환 + 베이스 스타일(검정/물/큰길) + 줌 색보간. (`feature/map/screen/DiaryMap.kt`, `res/raw/maplibre_style.json`, `MainListScreen.kt`)
- [x] A-3 다이어리 마커 / A-4 파티클 (위 A 섹션 참고).

### 2. ~~"위치 보기" 버튼 제거 + 100m 밖 → 도보 길찾기~~ — ✅ 종결 (방침 변경)
- [x] "위치 보기" 버튼 삭제(DetailScreen) — 버그 라운드 1.
- [x] ~~도보 길찾기~~ → **사용자 결정으로 길찾기 기능 전부 삭제**. 100m 밖 클릭 = 거리 안내 토스트만.

### 3. 친구 추가 + FriendScreen — ✅ 완료 (기능 배치 1)
- [x] Firestore: `users/{uid}/friends/{friendUid}` 양방향 + `friendRequests` 컬렉션.
- [x] `shared` `FriendRepository` + `FirebaseFriendRepository`, `feature/friend/` FriendScreen/ViewModel.
- [x] NavRoute.Friends + 드로어 "친구" 진입점.

### 4. 업로드 시 별 종류·색상 선택 + Firestore 기록 — ✅ 완료 (기능 배치 1)
- [x] `Diary.starType`(0~4)/`starColor`(0~11), UploadScreen 피커(StarShapeIcon=마커 동일 Path), 마커 렌더 연동.

### 5. 미조회 다이어리만 보기 — ✅ 완료 (기능 배치 1)
- [x] `users/{uid}/viewedDiaries`(DetailScreen 진입 시 기록) + MainListScreen "미조회만" 칩.

### 6. 친구 다이어리만 보기 — ✅ 완료 (기능 배치 1)
- [x] friends 기준 `diaries` 필터 + MainListScreen "친구만" 칩.

### 7. 친구 다이어리 알람  *(인앱 ✅ / 푸시는 8번 서버와 동일)*
- [x] `NotificationType.FRIEND_POST` 추가, 업로드 성공 시 친구들에게 알림 문서 생성(fire-and-forget).
- [x] 인앱 알림 목록(`NotificationScreen`)에 ⭐ "새 다이어리 …" 표시 + 미읽음 배지.
- [ ] 실제 푸시 발송 = 8번의 Cloud Functions 배포 필요(클라이언트 수신부는 준비 완료).

### 8. 알람·딥링크로 앱 실행  *(클라이언트 ✅ / 서버 ⏳)*
- [x] FCM 수신 서비스 `push/StaryMessagingService`: data 메시지 `{diaryId, title, body}` 수신 → 알림 표시(채널 stary_default).
- [x] 딥링크: 알림 탭 → `MainActivity` extra `diaryId` → `MainScreen(initialDiaryId)` → Detail 라우팅(로그인 오버레이 생략).
- [x] FCM 토큰 저장: 로그인 시 + `onNewToken` 시 `users/{uid}.fcmToken` merge. POST_NOTIFICATIONS 권한 요청(API 33+).
- [x] **서버(Cloud Functions) 코드 작성 완료**: `functions/index.js` `notifyFriendsOnDiaryCreate` —
      `diaries` onCreate(named DB stary-db) → `users/{uid}/friends` 조회 → 친구 `fcmToken` 으로
      data 메시지 `{diaryId, title: "{userName}님의 새 별", body: 제목}` 발송(만료 토큰 자동 정리 포함).
- [ ] **배포(사용자)**: ① Blaze 요금제 활성화 ② `functions/index.js` 의 `REGION` 을 stary-db 리전과 일치 확인
      ③ `cd functions && npm install` ④ `firebase deploy --only functions` (firebase CLI 로그인 필요).

---

## 🌟 기능 배치 2 (2026-06-16)

### 9. 다이어리 열람 파장 애니메이션 — ✅ 완료
- [x] `DetailScreen` 진입 시 3개 물결 링이 중심에서 확장 (1초). `Animatable` + Canvas `Stroke`.
- [x] 콘텐츠는 동시에 scale 0.93→1.0 + alpha 0→1 로 파장과 함께 등장.

### 10. 업로드 공개 범위 선택 — ✅ 완료
- [x] `Diary.visibilityType: String` 필드 추가 ("public"/"friends"/"private").
- [x] `UploadScreen` 공개 범위 피커(전체공개/친구만/나만보기) 3-옵션 선택 UI.
- [x] `FirebaseDiaryRepository.observeAllDiaries()`: private 다이어리 소유자 외 필터링.

### 11. 추가 필터 (나만보기, 친구 선택) — ✅ 완료
- [x] "나만보기" 칩: `diary.userId == currentUserId`.
- [x] "친구 선택" 칩: 클릭 시 체크박스 다이얼로그 → 선택된 친구 ID Set 으로 필터.
- [x] "friends" 공개범위 다이어리: 본인 or 친구 글만 지도에 표시.
- [x] 필터 칩 행 `horizontalScroll` 처리(4개 칩 가로 스크롤).

### 12. 별자리 기능 — ✅ 완료
- [x] DiaryMap FAB "별자리" 토글(AutoAwesome 아이콘, 활성 시 민트색).
- [x] `buildConstellationFeatures`: 1000m 이내 다이어리 쌍을 GeoJSON LineString 으로.
- [x] `CONSTELLATION_SOURCE` + `LineLayer`(민트 점선, opacity 0.5). 파티클 위/마커 아래.

### 13. 배경음악 — ✅ 인프라 완료 (음악 파일 추가 필요)
- [x] DiaryMap FAB "음악" 토글(MusicNote/MusicOff 아이콘).
- [x] `DisposableEffect(musicEnabled)` → `MediaPlayer` 루프 재생/해제.
- [ ] **사용자**: `res/raw/ambient_music.mp3` (또는 .ogg) 파일 추가 필요.
      파일 없으면 토스트 안내 표시 후 자동 비활성.

### 14. 마이페이지 다이어리 별 모양 — ✅ 완료
- [x] `DiaryCard(showStar = true)` → 카드 우측 상단에 `StarShapeIcon`(내 별 모양×색상).
- [x] `StarShapeIcon` 을 `StaryComponents.kt` 로 이동(UploadScreen 과 공용).

---

## 🛠️ 기능 배치 4 / 버그 라운드 (2026-06-18)

### 15. 내 다이어리 바나나 다이얼 오류 수정 — ✅ 수정(테스트 대기)
- [x] 다이얼 돌려도 정렬이 안 바뀌는 경우 多 → 드래그 중 실시간 `onSelect` 호출이 부모 recomposition 과
      충돌하던 게 원인. **드래그 중엔 회전(rot)만, 이벤트는 놓을 때(onDragEnd)·클릭 시에만** 발생하도록 변경
      + 이전 선택과 다를 때만 실행. `onDragCancel` 추가, `LaunchedEffect(selected)` 보정은 외부 변경 시에만.
- [x] **거리순(DISTANCE) 미적용** → `DiaryStarBox.here`(현재 위치)가 캐시만 읽어 null 이면 정렬이 원본으로 떨어짐.
      위치 캐시가 비면 `LocationHelper.getCurrentLocation` 으로 비동기 측정해 채우도록 수정(권한 필요).
- [ ] 사용자 테스트: 다이얼로 3정렬(최신/인기/거리) 전부 전환되는지 확인.

### 16. 별자리 구조를 실제 항성 배치로 교체 — ✅ 수정(테스트 대기)
- [x] 정렬 기준별 별자리를 실제 선그림(주요 항성+연결선)으로 재배치 (`MyDiaryScreen.CONSTELLATIONS`):
      **최신순=사수자리(Teapot)**, **인기순=처녀자리(Virgo)**, **거리순=전갈자리(Scorpius, 기존 물병자리에서 교체)**.
- [ ] 사용자 테스트: 각 별자리 모양이 실제와 닮았는지 확인(좌표 미세조정 필요 시 `CONSTELLATIONS` 수정).

### (기타 이번 라운드 반영분)
- [x] 이미지 업로드: 업로드 전 Auth 세션 보장 + 실제 에러 노출, `storage.rules`/`firebase.json` 추가.
- [x] 미열람 알림: 하트 우상단 **빨간 점**(Firestore Boolean `isRead`→실제 필드명 `read` 쿼리 버그 수정).
- [x] 커스텀 남색 토스트(`StaryToast`/`StaryToastHost`) + 기존 토스트 전부 교체(댓글/좋아요/칭호 포함).
- [x] 앱 아이콘 `@drawable/app_image`, 콜드스타트 스플래시 **완전 검정 배경**(values-v31, 아이콘 숨김).
- [x] 내 다이어리 배경 `mydiary_bg`(업로드 화면과 동일 밝기), 정렬 시작 시 `wind.mp3` 효과음(MusicManager).

---

## 🍎 (추후) iOS 확장 — **macOS + Xcode 필요(Windows 불가)**
- [ ] `iosApp/` Xcode(SwiftUI) 프로젝트 생성 + `:shared` 프레임워크 임포트(`linkDebugFrameworkIosSimulatorArm64`).
- [ ] `Repositories.kt` 인터페이스를 Firebase iOS SDK로 구현, `GoogleService-Info.plist`(f26c8 iOS 앱) 추가.
- [ ] iOS 지도: Google Maps iOS SDK 또는 MapKit 으로 `DiaryGoogleMap` 대응. 로그인: Google Sign-In iOS.
- 🤖 "iOS 쪽 작업 시작하자" 하면 위 기준으로 진행.

---

## 메모
- 서버 작업 필요 항목: **7, 8 (FCM/Cloud Functions)**. 나머지는 클라이언트만으로 가능.
- 의존성: 3 → (4 마커, 6 필터, 7 알림). A(맵 리스타일) → 1, 그리고 A-3 ↔ 4.
- 각 항목 완료 시: 빌드 통과 + 에뮬 테스트 → "테스트 완료" → 푸시 → PROJECT_NOTES/이 체크리스트 갱신.
