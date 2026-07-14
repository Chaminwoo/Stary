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

### 17. 오류 수정 라운드 — 영상/알림삭제/배경음악 (✅ 수정, 테스트 대기 2026-06-26)
- [x] **문제 1. 로그아웃 시 인트로 영상 미재생(검은 화면+버튼 즉시)**:
      `MainScreen.onLogout` 이 `loginImmediate=true` 로 영상을 건너뛰던 것을 **`false`** 로 변경 → 첫 실행과 동일하게
      `login_video.mp4` 재생 후 로그인 UI 노출(`LoginScreen(immediate=false)`).
- [x] **문제 2. 알림 슬라이드 삭제 시 셀/삭제버튼 잔존**: 삭제가 Firestore 왕복 후에야 목록 반영되던 게 원인.
      → **낙관적 제거**(`NotificationScreen.locallyRemoved` 로컬 제거 후 `vm.delete`) + 셀 `Modifier.animateItem()` 로
      즉시 사라짐+아래 셀 collapse. 스와이프 임계값을 **절반 이상=즉시 삭제**로(버튼만 남는 중간 상태 제거).
- [x] **문제 3. 배경음악 변경 시 처음부터 재생**: 미리듣기 위치 이어받기(`currentPositionMs`)를 **0** 으로 되돌림
      (`MusicScreen.playTrack(id, 0)`). 단, 화면을 안 바꾸고 나가면 현재 재생은 그대로 유지(재시작 안 함).
- [ ] 사용자 테스트: 로그아웃→영상, 알림 스와이프 즉시 삭제, 음악 변경 시 처음부터 재생 확인.

---

## 📝 다음 작업 (TODO / 2026-06-27) — ✅ 전부 완료 (2026-06-28, 아래 8.25 참고)

- [x] **친구 메시지 토스트(인앱 배너) 반복 오류 수정** — 근본 원인이 와처별 로컬 dedup 의존(재마운트 시 리셋·스냅샷 다중 방출 시 중복 enqueue).
      → **`InAppBanner.show(key=...)` 프로세스 영속 dedup**(채팅 `방:updatedAt`, 알림 `notif:id`)으로 원인 무관 1회 보장. iOS 동일 미러.
- [x] **"미조회만" 필터 아이콘 변경** — `MainListScreen` 칩 아이콘을 `Icons.Filled.Visibility`(조회수 눈과 의미 충돌) → **`Icons.Filled.FiberNew`**(NEW 뱃지)로. 라벨은 유지.
- [x] **설정 음량 슬라이더 별 모양 thumb** — `SettingsScreen` VolumeRow `Slider` 에 `thumb` 슬롯 추가, `StarShapeIcon(type=1, color=Mint)` 5각 별(비활성 시 회색). (iOS 는 SwiftUI Slider 커스텀 thumb 미지원 → 별도 슬라이더 필요, iOS TODO 로 보류.)

---

## 🛡️ 라운드 (2026-06-29) — 안전기능·길찾기·계정삭제 유예

### 차단·신고 + 계정 삭제 유예 — ✅ 구현(테스트/배포 대기)
- [x] 차단/신고: `ReportDialog`, `FirebaseModerationRepository`(block/unblock/report/observeBlockedIds), 프로필 ⋮ 메뉴(신고·차단, 텍스트 가운데 정렬), 댓글/목록 차단 필터, 다이어리 신고. iOS 패리티(`Moderation.swift`, `ReportDialog.swift`).
- [x] **firestore.rules 보강**: `users/{uid}/blocked` 하위 + `reports`(create-only) 추가 — 누락 시 차단/신고 쓰기가 PERMISSION_DENIED.
- [x] 계정 삭제 **7일 유예**: 즉시삭제 → `users/{uid}.deletionRequestedAt`+`authUid` soft 예약 후 로그아웃. 재로그인/세션복원 시 `cancelPendingDeletion` 자동 취소. 7일 안내 다이얼로그. 안드+iOS.
- [x] 서버: `functions/index.js` `purgeExpiredDeletions` — 매일 자정(Asia/Seoul) `deletionRequestedAt ≤ now-7일` 계정의 데이터/Storage/Auth 완전 삭제(Android 는 `authUid` 로 Auth 계정 식별).
- [ ] **배포(사용자)**: `firebase deploy --only functions,firestore:rules` (Blaze + Cloud Scheduler API 필요 — 자정 잡 자동 생성).

### 지도 도보 길찾기(OpenRouteService) — ✅ 구현(키 주입 필요)
- [x] ~~백로그 2번 "길찾기 전부 삭제"~~ → **ORS foot-walking 부활**. `OrsRouting`(안드 HttpURLConnection / iOS URLSession, 의존성 0).
- [x] **진입 = "친구 별 탭"** (별 직접 클릭은 길찾기 안 함): 친구 별(`UserDiaryStarsScreen`) 탭 → 지도 카메라+파동 후 길찾기. 전체 경로를 저장하고 **실시간 위치 기준 "최근접점→목적지" 구간만** 렌더(지나온 길 숨김, 경로와 떨어지면 최근접점까지 직선 연결), 하단 **X 취소** 버튼, **연초록 후광 실선**(점선 제거). `MapFocusState.withRoute` + `DiaryMap.partialRouteFrom`.
- [ ] **키(사용자)**: openrouteservice.org 무료 키 → 안드 `secrets.properties` `ORS_API_KEY=...`, iOS `project.yml`/빌드설정 `ORS_API_KEY`. 미설정 시 자동 비활성.
- [x] **iOS 진입점+실시간 부분경로+파동 구현(8.26-iOS)**: 전역 `MapFocusStore`+`TabRouter` 신설 → `UserProfileScreen` 친구 별 목록의 `figure.walk` 버튼 / **프로필 핀 별 탭** 이 지도 탭 전환 → **파동(`MapWarpOverlay` 동심원 물결) 후 ORS 길찾기**. `MapScreen` 이 `partialRouteFrom`(안드 동일)으로 실시간 "최근접점→목적지"만 렌더, 하단 요약+X 취소. `MapLibreView` `focusTarget` 1회 카메라 이동. ⚠️ 파동은 안드 스냅샷 메시 굴절의 **간이판**(링 파동). 자세히 = PROJECT_NOTES 8.26-iOS.
- [x] **핀 별 = 파동+길찾기(안드 패리티)**: 사용자 요청 — 프로필 핀 별 탭도 다이어리 클릭처럼. 안드 `NavGraph` ProfileScreen `onOpenDiary` `withRoute=true`(BUILD SUCCESSFUL). 실작동엔 ORS 키 필요(안드 `secrets.properties` 설정됨 / iOS 빌드설정).

### 프로필 떠다니는 통계 아이콘/별 드래그 — ✅ 안드+iOS (8.26-iOS 로 iOS 패리티 완료)
- [x] 안드: `FloatingStatBox`(부유/잡기/던지기/충돌/버스트 Compose 물리) + 핀 다이어리(별 모양, 탭→지도) + 탑바 `+` 핀 picker. `ProfileScreen` 중앙 아바타/이름/칭호 + 하단 로그아웃.
- [x] **iOS(8.26-iOS)**: `FloatingStatBox.swift`(TimelineView+Canvas+버블별 DragGesture 물리 포팅, 히트테스트 분리), `ProfileScreen` 재작성, `AchievementsScreen`/`MyStarsScreen` 분리, 핀 = `users.pinnedDiaries`. **CI(macOS) BUILD SUCCESS `e787ce8`**.
- [ ] **남은 점진 이관**: 친구 별-보드(`UserDiaryStarsScreen`)·내 다이어리 부유 보드(`DiaryStarBox` 드래그)는 iOS 미이관(MyStarsScreen 간이 리스트로 대체).

### 🌐 줌아웃 글로브(레퍼런스 `references/min_zoom.png`) — ✅ 완료 (2026-07-03, 테스트 대기)
- [x] 방침 변경: WebView(MapLibre GL JS globe) 대신 **네이티브 3D 렌더러**로 구현 —
      안드 `feature/globe/GlobeRenderer`(GLES2 커스텀) / iOS `Features/Globe/GlobeScreen.swift`(SceneKit).
- [x] 지도 줌 3.0 이하 → 하단 "지구 보기" 버튼 노출(자동 전환 없음) → 눌러야 진입. 글로브 안에서
      핀치는 카메라 줌만(화면 전환 없음, MIN 1.45~MAX 9.5), 아래쪽 탭 → X 버튼(4초 자동 숨김)/뒤로가기로 지도 복귀.
- [x] 지구: 원본 텍스처 3/4 밝기 균일. 별 플레어(좋아요 100+, 레퍼런스풍 다색 팔레트)·노란 도시 야경 점광(그 외 다이어리).
- [x] 궤적 5개: 얇은 코어+옅은 글로우, 반투명, 백색 빛무리가 궤적을 따라 흐름, 트레일별 투명도 차등.
- [x] 배경: 3겹 구면 셸(시차로 깊이감) + 성운 글로우 + 은하수 띠 + 별자리
      + 4방 광선 반짝별.
- [x] **별자리 → 황도 12궁 교체(2026-07-04, 레퍼런스 `references/zodiac.avif`)**: 기존 4종(북두칠성/카시오페이아/오리온/남십자) 제거,
      12궁(양~물고기)을 경도 30°씩 + 위도 4단 사이클로 하늘 전체에 골고루 배치. 디자인(밝은 별+희미한 연결선)은 기존 그대로,
      **궁마다 고유색**(코랄/연두/옐로/은청/골드/민트/핑크/크림슨/퍼플/틸/블루/라벤더). 안드 = 라인 VBO pos3+rgb3 로 확장(LINE 셰이더 aColor),
      iOS = equirect 텍스처에 동일 지점(픽셀 환산)·동일 도형·동일 색.
- [ ] 사용자 테스트 대기 중.

---

## 📝 다음 작업 (TODO / 2026-07-02 — 사용자 지정, 다음 토큰에 진행)

### 18. 채팅 FCM 알림(백그라운드/종료 상태) + 딥링크
> 사용자 목표(원문): 앱 **백그라운드**(홈으로 나감) 시 새 채팅 오면 상단 **Heads-up 알림**, 앱 **완전 종료**(Force Stop 제외) 상태에서도 알림, 알림 클릭 시 **해당 채팅방으로 바로 이동**, **Android 13+ `POST_NOTIFICATIONS` 권한**까지 고려.
>
> ⚠️ **이미 상당 부분 구현돼 있음 — 처음부터 만들지 말 것.** 아래 "기존 상태"를 먼저 확인하고 **빠진 것만** 채운다.

**기존 상태(이미 됨):**
- [x] 수신 서비스 `push/StaryMessagingService`(data 메시지 → 알림, 채널 `stary_default`). `AppForeground.isForeground` 면 시스템 알림 skip(인앱 배너로) — 8.22 참고.
- [x] 서버 함수 `functions/index.js` `notifyOnChatMessage`(`chats/{chatId}/messages` onCreate → 상대 `fcmToken` 으로 푸시) + `sendToUser`(만료 토큰 정리). **단 미배포**.
- [x] FCM 토큰 저장(로그인 + `onNewToken` → `users/{uid}.fcmToken`), `POST_NOTIFICATIONS` 권한 요청(API 33+).
- [x] 채팅 자체는 실시간 동작(앱 실행 중). 인앱 배너(전면)도 동작.

**구현 완료(2026-07-02, Android BUILD SUCCESSFUL — 실기기+배포 검증 대기):**
- [x] **Heads-up 채널 앱 시작 시 사전 생성** — `push/NotificationChannels.kt`(`ensureStaryNotificationChannel`, `STARY_CHANNEL_ID="stary_default"`, `IMPORTANCE_HIGH`), `StaryApplication.onCreate` 에서 호출. 채널은 영속되므로 종료 상태에서 시스템이 표시하는 알림도 상단 배너로 뜬다.
- [x] **딥링크 = 채팅방** — `MainActivity` 에 `EXTRA_CHAT_FRIEND_ID/EXTRA_CHAT_FRIEND_NAME` + `onNewIntent`(앱 살아있을 때), `launchMode=singleTop`(Manifest). `core/util/DeepLinkState`(콜드/웜 공용) → `MainScreen` 이 관찰해 `NavRoute.Chat(friendId,friendName)` 로 이동(로그인 오버레이 skip). diaryId 경로도 DeepLinkState 로 통일.
- [x] **StaryMessagingService** — 전면이면 skip(인앱 배너), 후면/종료면 채팅이면 채팅 extra, 아니면 diaryId 로 인텐트 구성(공유 채널 사용).
- [x] **서버 `notifyOnChatMessage`** — data 에 `chatFriendId(=senderId)`/`chatFriendName` 추가(딥링크용). `sendToUser` 는 이미 notification+data 혼합 + `android.priority:"high"` + `channelId:"stary_default"` → 종료/백그라운드에서 시스템 자동 표시(heads-up).

**남은 것:**
- [ ] **배포(사용자)**: `firebase deploy --only functions` (Blaze + `REGION` = stary-db 리전 일치). 미배포면 종료/백그라운드 푸시 안 옴.
- [ ] **실기기 검증**: 백그라운드/종료 상태에서 채팅 오면 상단 heads-up, 탭 → 해당 채팅방 진입, Android 13+ 권한 프롬프트.
- [ ] iOS 패리티(§1.5): APNs/FCM iOS 설정은 별도(GoogleService-Info + APNs 키). 안드 먼저, iOS 는 후속.

### 19. 프로필 로그아웃 버튼 오류 수정 — ✅ (Android BUILD SUCCESSFUL, 테스트 대기)
- [x] 원인: 히든 아이콘을 8.29에서 전체화면 `FloatingStatBox` 오버레이로 편입 → 오버레이가 하단 **로그아웃 버튼 위**에 그려져 터치를 가로챌 수 있음. **로그아웃 Column 에 `Modifier.zIndex(1f)`** 로 오버레이 위로 올려 항상 눌리게(하단 얇은 밴드만 차지 — 버블은 상단 80%라 무영향). iOS 는 `FloatingStatBox` 가 Canvas `allowsHitTesting(false)`+버블별 투명 히트뷰라 원래 로그아웃 정상(수정 불필요).

### 20. 히든 칭호를 일반 칭호와 다르게 표기 — ✅ (Android BUILD SUCCESSFUL, iOS CI 대기)
- [x] 히든 칭호는 **금색(0xFFD86F) + `『 』` 감쌈 + 더 강한 후광 + Bold** 로 일반 칭호(민트)와 구분. 판별 = `HiddenAchievements.byId(equippedId) != null`. 적용: 안드 `ProfileScreen`·`UserProfileScreen` / iOS `ProfileScreen`(titleDisplay*)·`UserProfileScreen`. 업적 히든 탭은 이미 금색 구분(8.29). 칭호 이름 자체는 비번역 유지.

### 21. 하루 별 업로드 10개 제한 — ✅ (Android BUILD SUCCESSFUL, iOS CI 대기)
- [x] 상수 `StaryConfig.DAILY_UPLOAD_LIMIT = 10` / iOS `AppConfig.dailyUploadLimit`. **로그인 사용자** 기준, 그날(로컬 자정 이후) 내가 올린 개수로 **선차단** + 안내 토스트. 안드 `UploadScreen`(구독한 `getMyDiaries` 로 오늘 개수 계산, 저장 버튼에서 차단, 문구 `upload_daily_limit` ko/en/ja) / iOS `UploadScreen.save()`(`store.mine` + `Calendar.startOfDay`).
- [ ] (후속) 서버 강제(rules/카운터 문서/Function) — 클라 우회 방지. 현재는 클라 차단만.

### 22. 어드민 계정은 히든 업적 선점에서 제외 — ✅ (Android BUILD SUCCESSFUL, iOS CI 대기)
- [x] **어드민 = `chaalsdn0217@gmail.com`**(`StaryConfig.ADMIN_EMAILS`/`isAdminEmail` + iOS `AppConfig`). `claim` 최상단에서 어드민이면 **트랜잭션 쓰기 skip + false 반환** → hiddenAchievements 미기록, 히든은 계속 "달성자 없음" 유지(실제 유저가 첫 달성 가능), 어드민은 팝업/아이콘도 안 뜸.
- [x] 이메일 취득: 안드 `GoogleAuthHelper.currentUserEmail`(로그인/세션복원 시 `FirebaseAuth.currentUser?.email` 저장, 로그아웃 시 null) / iOS `Auth.auth().currentUser?.email`.

---

## 📝 다음 작업 (TODO / 2026-07-03 — 사용자 지정)

### 23. 채팅 화면 하단 여백 제거 — ✅ (커밋 `9c4520a`)
- [x] 채팅 입력창 아래 불필요한 여백 제거(안드 `ChatScreen` / iOS `ChatScreen.swift`).

### 24. 프로필 — 사용자 이름/프로필 사진/칭호 터치 불가 오류 수정 — ✅ (커밋 `3ca29e5`)
- [x] 본인/타인 프로필의 이름·프로필 사진·칭호 탭 반응 안 함 수정(안드+iOS).

### 25. 채팅 완전 삭제(1분 이내), 본인 계정에서만 삭제 — ✅ (커밋 `ab7edae`)
- [x] 전송 후 **1분 이내** 완전 삭제(상대 쪽에서도 사라짐), 본인 메시지만 삭제 노출.

### 26. 업로드 — 짧은 영상(3초 이내) 업로드/조회 기능 — ✅ (커밋 `c8cfe7b`)
- [x] 3초 이내 짧은 영상 업로드 + `DetailScreen` 루프 재생(안드+iOS).

### 27. 글로브 모드 → 지도 복귀 시 "내 위치" 버튼 로직 1회 재실행 — ✅ (Android BUILD SUCCESSFUL, 테스트 대기)
- [x] 글로브 복귀 시 "내 위치로" 버튼과 동일한 카메라 이동을 1회 자동 실행. 안드 `DiaryMap` —
      FAB·글로브 복귀가 공용 `recenterToMyLocation()`(현위치 `currentLatLng` → `DEFAULT_ZOOM=15` animate) 사용.
      이전엔 글로브에서 나온 좌표(zoom 4.0)로 점프하던 것을 현위치 복귀로 변경.
- [x] iOS `MapLibreView`: 글로브 복귀(`globeReturnCamera` nonce) 시 `userLocation`(폴백=글로브 좌표)로 zoom 15 animate.

### 28. 다이어리 신고 → Firebase 신고 게시물 등록 + 검토 후 조치 — ✅ (Android BUILD SUCCESSFUL, 테스트/배포 대기)
- [x] 신고 등록에 **검토용 스냅샷** 추가: `report(extra=)` 로 `targetTitle`/`targetContent`(280자)/`targetOwnerName`/`targetImageUrl`
      함께 저장 → 관리자가 Firebase Console `reports` 목록만으로 판단 가능(대상·신고자·사유·시각은 기존대로). 안드 `FirebaseModerationRepository`+`DetailScreen` / iOS `Moderation.swift`+`DetailScreen.swift`.
- [x] **관리자 조치 = Console 에서 `reports/{id}.status` 변경** → 서버 `functions/index.js` `onReportAction`(onDocumentUpdated) 실행:
      `"action_delete"` = 대상 다이어리 문서 + Storage 미디어(URL 파싱) 삭제 / `"action_ban"` = 대상 계정 완전 삭제(데이터+프로필+Auth, `deleteUserData` 재사용) / 그 외 무시. 처리 후 `resolvedAt` 기록(재실행 방지).
- [ ] **배포(사용자)**: `firebase deploy --only functions` (Blaze 필요). 미배포 시 `status` 바꿔도 자동 조치 안 됨(수동 삭제는 Console 로 가능).

---

## 📝 다음 작업 (TODO / 2026-07-10 — 사용자 지정: 유입/흥미 라운드)

### 29. 앱 시작 위치 개선 — 마지막 위치에서 시작 + 위치 조작 차단 — ✅ (Android BUILD SUCCESSFUL, 테스트 대기)
- [x] **마지막 위치에서 시작**: `LocationHelper` 가 실제 fix 를 SharedPreferences(`stary_location`, 10초 스로틀)에 저장,
      `lastSavedLatLng()` 로 복원. `MainListScreen` 초기 좌표 = 실시간 fix → 지난 세션 위치 → 기본좌표(건국대) 순.
      첫 fix 도착 시 카메라 보정(didAutoCenter)은 기존 그대로 — 가까우면 점프가 체감되지 않는다.
- [x] **모의 위치 차단**: `Location.isMock`(API 31+)/`isFromMockProvider` 감지 시 위치 반영 거부 + `mockDetected` StateFlow
      → `MainListScreen` 경고 토스트(`location_mock_blocked`, ko/en/ja). 폴백 `getLastKnownLocation` 경로도 필터.
      VPN 은 IP 만 바꾸고 GPS 는 못 바꾸므로 별도 차단 불필요(모의 위치 차단이 핵심).
- [x] **fix 전 열람 차단**: 실제 fix 없으면 지도 별 클릭 시 열람 대신 안내(`map_waiting_fix`) — 저장 좌표/기본좌표로
      100m 판정하던 구멍 제거.
- [x] iOS 패리티: `LocationManager` UserDefaults 저장/복원 + `sourceInformation.isSimulatedBySoftware/isProducedByAccessory`
      모의 위치 거부 + `MapLibreView` 시작 좌표 폴백 + `DetailScreen.canOpen` fix 필수. (경고 토스트 UI 는 iOS 전역 토스트 부재로 후속)

### 30. 다이어리 공유 카드 + 웹 랜딩 (유입) — ✅ (Android BUILD SUCCESSFUL, 테스트/호스팅 배포 대기)
- [x] **공유 카드**: 밤하늘(시드 고정 잔별) + 그 별 모양/색(StarStyle 재사용) + 제목/작성자/날짜/역지오코딩 동네 힌트 +
      태그라인·STARY 브랜드를 1080×1920 카드로 렌더 → 시스템 공유 시트(이미지+링크 텍스트, ko/en/ja).
      안드 `core/util/ShareCardHelper.kt` + `DetailScreen` 공유 버튼 / iOS `Core/ShareCard.swift`(ImageRenderer) + 툴바 버튼.
- [x] **웹 랜딩**: `web/index.html`(밤하늘 캔버스 + "이 별은 그 장소에 가야 열려요" + 앱 열기/설치 버튼) +
      `firebase.json` hosting 블록. 링크 = `StaryConfig.shareLink()` → `https://momentdiary-f26c8.web.app/s/{diaryId}`.
- [x] **딥링크**: `stary://diary/{id}` (Manifest/Info.plist 스킴 등록). ⚠️ 상세 직행이면 100m 게이팅 우회라
      **지도 포커스(MapFocusState/MapFocusStore)** 로 처리(카메라+파장만). 푸시 알림(extras) 딥링크는 기존 상세 직행 유지.
- [x] iOS 패리티(§1.5): AppConfig.shareLink/deepLink*, StaryApp.onOpenURL, L10n 공유 키.
- [ ] **배포(사용자)**: `firebase deploy --only hosting` (미배포 시 링크가 404 — 카드 이미지 공유는 무관하게 동작).
- [ ] (후속) iOS App Store 등록 후 랜딩의 스토어 링크 교체.

### 31. 친구 초대 보상 (유입) — ✅ (Android BUILD SUCCESSFUL, 테스트 대기)
- [x] **초대 링크 공유**: 친구 탭 상단 초대 카드 → `{SHARE_BASE_URL}/i/{내uid}` 텍스트 공유(ko/en/ja).
      랜딩(`web/index.html` /i/ 모드)이 "친구가 초대했어요" + `stary://invite/{uid}` 버튼 표시.
- [x] **리딤**: 딥링크 수신 → 로그인 후 `invites/{내uid}` 생성(문서 id = 리딤자 → 계정당 1회 자동 중복방지).
      본인 링크 불가 + **가입 7일 이내**(`INVITE_REDEEM_WINDOW_MS`)만. 결과 토스트(성공/중복/본인/기간초과/실패).
- [x] **보상 = 칭호 업적 3종**(별 색/모양 슬롯은 전부 사용 중): 받은 쪽 `별의 인연`(리딤), 초대한 쪽
      `별의 등대`(1명)/`별무리의 길잡이`(5명). UserStats.invitedFriends/redeemedInvite → 실시간 판정.
- [x] **서버 강제**: `firestore.rules` — invites 는 create 만 허용(이미 있으면 실패), update/delete 거부.
      (+ 기존 파일에 누락돼 있던 `hiddenAchievements` 규칙도 함께 추가 — 배포 시 히든 업적 깨짐 방지)
- [x] iOS 패리티(§1.5): InviteStore(딥링크 보관→로그인 후 리딤/통계) + FriendsScreen ShareLink 초대 카드 +
      AchievementsScreen 통계 반영. ⚠️ iOS 리딤 결과 안내 UI 는 전역 토스트 부재로 후속(무음 처리).
- [ ] **배포(사용자)**: `firebase deploy --only firestore:rules,hosting`.

### 32. 주간 개척 퀘스트 — 매주 랜덤 나라, 첫 다이어리 = 개척자 칭호 (흥미) — ✅ (Android BUILD SUCCESSFUL, 테스트 대기)
- [x] **서버 없는 결정적 로테이션**: `shared/.../PioneerQuest.kt` ↔ iOS `PioneerQuest.swift` — 나라 79개 목록을
      고정 시드(xorshift64) Fisher-Yates 순열로 셔플, 주차(2026-07-06 월 UTC 기준)가 순열을 가리킴.
      모든 클라이언트가 같은 "이번 주 나라"를 계산(⚠️ 목록 순서/시드/주차 계산 양 플랫폼 비트 단위 동일 유지).
- [x] **선점**: 업로드 성공 → 역지오코딩 국가 코드 → 활성 대상국(등장했고 미개척)이면 `pioneerClaims/{code}`
      트랜잭션 선점(전 세계 1명, 히든 업적 패턴·어드민 제외). rules create-only 로 서버 강제.
      안드 `PioneerClaimHelper`(자체 스코프 — 네비게이션에 안 죽음) / iOS `PioneerStore.attemptClaim`.
- [x] **칭호**: `pioneer_{code}` 동적 칭호 — 표시명 "○○ 개척자"(국가명은 로케일 API, ko/en/ja).
      `LocalizedNames.equippedTitle` 이 pioneer_ 접두사 처리. 업적 화면에 "개척 칭호" 섹션(내가 개척한 나라만, 장착 가능).
- [x] **지도 특별 표시**: 미개척 대상국 중심좌표에 금색 스파클 비콘(starBitmap(3,15)) + "개척 퀘스트 · ○○" 라벨,
      탭 → 퀘스트 안내. 개척되면 비콘 자동 제거(실시간 구독). 안드 `DiaryMap` PIONEER_* 레이어 / iOS `PioneerAnnotation`.
- [x] iOS 패리티(§1.5). (글로브(3D)에서의 표시는 후속 TODO)

### 33. 근처 별 발견 알림 (리텐션) — ✅ Android (BUILD SUCCESSFUL, 테스트 대기 / iOS 연기)
- [x] **앱 사용 중(foreground) 감지**: 실제 위치 fix 갱신 시 `NearbyStarAlert.check` — 반경
      `NEARBY_ALERT_RADIUS_M`(250m) 안의 "아직 안 본 남의 별" 중 가장 가까운 1개를 상단 인앱 배너로.
      탭 → 그 별로 지도 포커스(MapFocusState). 대상 = 지도에 보이는 목록(공개범위 반영).
- [x] **빈도 제한**: 같은 별 평생 1회(SharedPreferences 영구 기록) + 하루 5회 상한 + 최소 간격 3분.
- [ ] 백그라운드 지오펜스 확장은 후속 검토(위치 권한 정책 부담).
- [x] **iOS 패리티 — 완료(2026-07-14, 34 라운드 iOS 일괄과 함께)**:
      AppConfig `nearbyAlert*` 동기화 + `Core/NearbyStarAlert.swift`(같은 별 평생 1회/하루 5회/3분 간격 — UserDefaults) +
      MainTabView `.onReceive(location.$coordinate)` 훅 + InAppBanner + 탭 → MapFocusStore + L10n `nearbyStar*`(ko/en/ja).

> ⚠️ **iOS 진행 방침(2026-07-10 사용자 지시)**: 이번 유입/흥미 라운드의 추가 iOS 작업은
> **안드로이드 실기기 테스트와 최종 수정이 끝난 뒤** 일괄 진행한다. (29~32 의 iOS 반영분은 이미 커밋됨 — CI 검증은 push 후)

---

## 🎨 34. 디자인 라운드 — ✅ Android 구현 완료 (2026-07-13) + ✅ iOS 패리티 일괄 (2026-07-14, CI 검증 대기 / 34-3 폐기 · 34-10 삭제)
> **iOS 패리티 완료(2026-07-14)** — 34-1/2/4/5/6/7/8/9 + 체크리스트 33 근처 별 알림 + 닉네임 클램프. push 후 ios.yml CI 로 컴파일 검증.
> **34-10(전환 잔상)은 사용자 지시로 Android 에서도 삭제**(RouteStreak.kt 제거) — iOS 미구현 유지.
> 남은 사용자 액션:
> - [ ] (사용자, 선택) Firestore 복합 인덱스 `diaries`: `userId` ASC + `createdAt` DESC — 34-6 친구 최근 별의 저렴한 경로.
>   없어도 **자동 폴백**(정렬 없는 쿼리 + 클라 정렬)으로 동작하므로 급하지 않음. Logcat 의 콘솔 링크로 생성 가능.

> 디자인 영감 리스트에서 사용자가 고른 10건. **이 섹션은 구현 가이드**(이번 세션은 문서만 작성).
> 진행 방식: **Android 먼저 → 빌드 → 사용자 테스트 → iOS 패리티 일괄**(8.38 방식). 문서만 바꾼 커밋은 재빌드 불필요.
>
> **공통 원칙(전 항목):**
> - 성능: 화면당 `InfiniteTransition` 1개로 시간축 공유, 파티클은 Canvas 직접 렌더(`drawBehind`/단일 Canvas), 개수 상한 명시.
>   장식 레이어는 **히트테스트 금지**(체크리스트 19 로그아웃 zIndex 사고 전례 — 터치 가로채기 절대 금지).
> - 크리스탈 미니 렌더 재사용: 안드 `StarStyle.drawCrystalFill`/`StarShapeIcon`, iOS `StarCrystal.image`(NSCache) — 신규 렌더러 만들지 말 것.
> - 톤: 전부 "은은하게". 장식 alpha 상한을 각 항목에 명시했으니 넘기지 말 것(1차 피드백 대부분 "과하다"로 온다).
> - 권장 구현 순서: ① 34-9(로딩 별 = 공용 부품) → ② 34-1/2/5(저위험 장식) → ③ 34-4/6(데이터 연동) → ④ 34-8/3/7/10(연출).

### 34-1. 겹친 별 카드 — 스와이프 ↔ 헤더 별 밝기 연동 (별자리 선은 안 함)
- [ ] `StarClusterScreen.kt` 헤더 Row(`diaries.take(5)`): 아이콘 i 강조 = `i == pagerState.currentPage`.
      표현 = `animateFloatAsState` 로 alpha 0.35↔1.0 + scale 1.0↔1.15(+선택: 별색 글로우). **currentPage ≥ 5 면 아무것도 강조 안 함**(헤더는 5개까지만 표시).
- [ ] 스와이프 중간값은 무시하고 settled page 기준(깜빡임 방지) — `pagerState.currentPage` 그대로면 충분.
- [x] iOS `StarClusterView.swift` header — `page` state 로 동일 로직, `.animation(.easeOut(duration: 0.2), value: page)`. (2026-07-14)
- ⚠️ 별자리 선 연결은 **하지 않는다**(사용자 명시 제외).

### 34-2. 상세 진입 여운 — 별색 오로라
- [ ] `DetailScreen.kt`: 배경 최상단(스크롤 무관 고정 레이어)에 `accent`(= `StarStyle.colorOf(diary.starColor)`, 이미 존재) 오로라 —
      높이 ~200dp `verticalGradient(accent.copy(alpha≤0.16) → Transparent)`. 선택: 그라데이션 중심을 12~18s 주기로 아주 느리게 수평 드리프트.
- [ ] 파장(DiaryOpenWarp) 색과 이어져 "열람의 여운"으로 읽히게 — 별색 그대로, 화이트 혼합 금지.
- [x] iOS `DetailScreen.swift`: `DetailAuroraVeil`(TimelineView+Canvas, 15s 드리프트) — 콘텐츠 위 고정 레이어(Android 동일). (2026-07-14)

### 34-3. 내 하늘 헤더 — ❌ **폐기(2026-07-13 사용자 지시: "내 하늘만 삭제")**
> 구현했다가 제거함(프로필 화면은 기존 배경/부유 아이콘 유지). **iOS 에도 만들지 말 것.**

<details><summary>(폐기된 원안)</summary>
- [ ] `ProfileScreen.kt` 상단 헤더 영역(프사/이름/칭호 뒤) 배경에 **내 별들로 미니 밤하늘**:
      데이터 = 내 다이어리(이미 구독 중인 flow 재사용 — 신규 쿼리 금지), **최대 30개** 샘플.
- [ ] 배치/모션: `diary.id` 해시로 결정론적 (x,y)(같은 유저=항상 같은 하늘), 크기 6~14dp(likeCount 가중),
      개별 위상 sin 부유 + 트윙클. **Canvas 1개에 직접 렌더**(StarShapeIcon 30개 나열 금지) — 큰 별 몇 개만 크리스탈, 작은 별은 글로우 점.
- [ ] 기존 `FloatingStatBox`(부유 아이콘 오버레이)와 시각적으로 겹치지 않게 z 순서/영역 확인. 히트테스트 없음(이름/프사 탭 기능 침해 금지 — 체크리스트 24 전례).
- [ ] iOS `ProfileScreen.swift`: 헤더 Canvas + `StarCrystal.image` 캐시 재사용, 동일 해시 배치.
</details>

### 34-4. 히든 업적 → 전역 "이름 옆 전용 크리스탈 별" + 달성자 현재 이름 추적
**(a) 달성자 이름 = 현재 이름 (버그 수정 성격 — 먼저 처리)**
- [ ] `AchievementsScreen.kt:370` — `claim?.achieverName`(선점 시점 스냅샷) → `rememberCurrentUserName(claim.achieverId, claim.achieverName)` 로 교체.
      헬퍼는 `core/util/UserDirectory.kt` 에 이미 있음(DetailScreen 댓글과 같은 패턴 — users/{uid} 실시간 구독).
- [x] iOS `AchievementsScreen.swift` 의 달성자 표기도 동일하게 — 기존 `UserDirectory.shared` 재사용(`ensureWatching`+`name`). (2026-07-14)

**(b) 업적별 전용 크리스탈 배지 — 이름이 표시되는 모든 곳**
- [ ] **업적→크리스탈 매핑**: `HiddenAchievement` 에 배지용 `(starType, colorIndex)` 필드 추가 — 11개 업적 전부 **서로 다른 (모양×색)** 조합 지정
      (업적의 기존 `HiddenIcon.color` 와 톤이 맞는 색 인덱스 선택). iOS `HiddenAchievements.swift` 정의와 값 동일 유지(§1.5 drift 금지).
- [ ] **`HiddenClaimStore` 신설**(안드 `core/util/` 또는 feature/profile): `hiddenAchievements` 컬렉션(**≤11 문서**) 전체 1회 구독 →
      `achieverId → Set<achievementId>` 맵 전역 상태. 어느 화면에서든 uid 로 O(1) 조회. AchievementsScreen 의 기존 claims 로딩 로직을 이 스토어로 승격/재사용.
- [ ] **공용 컴포저블 `core/ui/HiddenStarBadge.kt`**: `HiddenStarBadges(userId, size=12.dp)` — 그 uid 가 달성한 히든 업적 각각의 미니 크리스탈을
      이름 뒤에 나란히(상한 3개 권장, 정적 렌더 — 파티클 이펙트는 프로필 전용 유지). 비트맵 캐시로 그릴 것(리스트 스크롤 성능).
- [ ] **삽입 지점(이름 나오는 곳 전부)**: DetailScreen 작성자 행(≈:368) / DetailScreen `CommentItem` 이름(≈:649) /
      `FriendScreen.FriendRow` 이름 + 친구 검색 결과 행 / `UserProfileScreen` 이름 / `ProfileScreen` 내 이름 / `ChatScreen` 상단 상대 이름 /
      알림 화면 등 이름 노출부 grep 으로 전수 확인. **익명 다이어리/댓글은 제외**(작성자 은닉 유지).
- [x] iOS: `HiddenAchievementStore.shared` 승격(전역 리스너 1개) + `Core/HiddenStarBadge.swift` 뷰,
      삽입 = Detail 작성자/댓글·친구 행·친구 검색·채팅 타이틀·내/타인 프로필. `HiddenAchievements.swift` 에
      badgeType/badgeColor(값 Android 동일) + 칭호 fallback drift 정정. (2026-07-14)

### 34-5. 업적 화면 성운 진행도
- [ ] `AchievementsScreen.kt` ≈:236(`ach_progress` 텍스트) — 텍스트 뒤에 폭 전체·높이 ~48dp **성운 밴드**:
      채움 fraction = `unlockedCount / Achievements.all.size`. 채움부 = 2~3색 radial blob(민트 Green + 보라 계열) 겹침 + 미세 잔별 점,
      빈 영역 = 아주 옅은 잔별만. 경계 soft fade, 값 변화 시 `animateFloatAsState`. 텍스트는 밴드 위 오버레이 유지.
- [ ] **일반 탭만** 적용(히든 탭은 선착순 개념이라 진행도 없음).
- [x] iOS `AchievementsScreen.swift`: `NebulaProgressBand`(Canvas+Animatable 보간) 동일 밴드. (2026-07-14)

### 34-6. 친구 행 — 미읽음 파란 점 삭제 → "최근 올린 별" 표시
- [ ] `FriendScreen.kt`: FriendRow 의 파란 점(:448~456) + `unread` 파라미터/판정(:261) 제거(죽은 코드 정리).
      ⚠️ `ChatReadStore` 자체와 행 탭 시 `markRead` 는 유지(ChatScreen 이 계속 사용).
- [ ] 대체 표시: 행 최우측에 그 친구의 **가장 최근 다이어리 별**(starType/starColor 미니 크리스탈, 22~26dp). 최근 별이 없으면 빈 자리.
- [ ] 데이터: `FirebaseDiaryRepository` 에 `observeLatestDiaryOf(uid)`(whereEqualTo userId + orderBy createdAt desc + **limit 1**) 신설 —
      친구 수만큼 limit-1 리스너(전체 다이어리 구독 금지). ⚠️ **복합 인덱스** 필요 가능(에러 로그의 콘솔 링크로 생성 — 사용자 액션 항목으로 남길 것).
      ⚠️ **공개범위 준수**: 반환 별이 내가 볼 수 있는 것(공개/친구공개)인지 기존 visibility 필터 로직으로 걸러서 "볼 수 없는 최신 별"이 새지 않게.
- [ ] 행 탭=채팅/사진 탭=프로필은 그대로(별은 장식, 탭 없음).
- [x] iOS: `FriendsScreen` 행 최우측 = 최근 별(store.diaries 에서 비공개/익명 제외 최신) — 탭 시 지도 길찾기. (2026-07-14 이전 세션분)

### 34-7. 채팅방 배경 미세 별가루
- [ ] `ChatScreen.kt`: 메시지 리스트 **뒤** 배경 Canvas — 파티클 **8~12개**, 크기 1.5~3dp, alpha ≤ 0.35,
      개별 속도/위상의 느린 드리프트 + 트윙클. 단일 InfiniteTransition float 하나로 전 파티클 파라메트릭 계산.
- [ ] 키보드 개폐/스크롤에 레이아웃 영향 없게 배경 고정(imePadding 영역 밖), 히트테스트 없음.
- [x] iOS `ChatScreen.swift`: `ChatStardust`(TimelineView+Canvas, 시드/주기 Android 동일). (2026-07-14)

### 34-8. 별 탄생 연출 (업로드 완료)
- [ ] 트리거: `UploadScreen.kt` 저장 성공 경로(≈:505 이후 — 이미지/영상 업로드 완료 → Firestore 저장 성공 직후, 실패 시 연출 없음).
- [ ] 연출(~900ms): 선택한 별(starType/starColor)이 화면 중앙에서 **응축**(scale 2.4→1.0 + 글로우 버스트) → 스파클 링 6~10개 확산 →
      축소되며 지도 쪽으로 날아가 소멸. 사운드 없음.
- [ ] 구현: **전역 오버레이 권장** — `DiaryOpenWarp`(파장) 전례처럼 상태 홀더 + MainScreen 오버레이(`core/ui/StarBirth.kt` 신설).
      Upload 화면이 pop 된 뒤 지도 위에서 연출이 이어져 "별이 실제로 심기는" 체감. (UploadScreen 내부 오버레이 후 pop 은 차선.)
- [x] iOS: `UploadScreen.save()` 성공 훅 → `StarBirthStore.trigger` + 지도 탭 전환, `StarBirthHost`(MainTabView 오버레이, `Core/StarBirth.swift`). (2026-07-14)

### 34-9. 로딩 인디케이터 = 크리스탈 별 (기본 스피너 전면 교체) — **가장 먼저(공용 부품)**
- [ ] `core/ui/StarLoading.kt` 신설 — `StarLoadingIndicator(modifier, size=36.dp)`:
      중앙 크리스탈 별 **맥동(pulse+글로우)** + 주위를 도는 스파클 점 2개(회전하는 별보다 크리스탈 무늬에 자연스러움 — 구현 세션 재량).
- [ ] `CircularProgressIndicator` 사용처 **9파일 전부 교체**: StarClusterScreen / ShareCardEditor / ProfileScreen / MyScreen /
      DetailScreen / UploadScreen / SettingsScreen / BoomerangCaptureScreen / NotificationScreen. (버튼 내 소형은 size 파라미터로.)
- [x] iOS: `Core/StarLoadingView.swift` 신설, `ProgressView` 사용처 9곳 교체(Detail×2/Login/Friends/List/Map/Settings/BoomerangCamera/BoomerangCaptureView — 부메랑 캡처 진행 바(값 표시)는 유지). (2026-07-14)

### 34-10. 화면 전환 별 잔상 — ❌ **삭제(2026-07-14 사용자 지시: "실선 2개 띄우는 거 삭제")**
> Android 로 구현했었으나(RouteStreak.kt + MainScreen 오버레이) 사용자 지시로 완전 제거. **iOS 에도 만들지 말 것.**

> 완료 기준(라운드 공통): `:androidApp:assembleDebug` 그린 → 사용자 테스트 → push → iOS 패리티 일괄 → CI 그린 → PROJECT_NOTES 갱신.

---

## 🧹 35. 전체 리팩토링 + 주석 다이어트 (2026-07-14 시작 — DiaryMap 완료, 나머지 이월)

> 기준(사용자 확정): **구조 리팩토링 + 주석은 "핵심/함정 경고"만 유지**.
> 유지 = 파일·클래스·함수 헤더(한 줄), ⚠️ 크래시/렌더 함정 경고, Android↔iOS 패리티 앵커, 외부 파일 계약(예: maplibre_style.json 스톱 일치).
> 삭제 = 줄 단위 서술, 단계 나열(// 1) …), 이력/피드백/체크리스트 번호 참조, 대안 검토 메모.
> 완료 예시 = `feature/map/screen/` 3파일(DiaryMap / DiaryMapMarkers / DiaryOpenWarp) — 이 톤을 그대로 따를 것.

- [x] 35-1. DiaryMap.kt(2079줄) → 3파일 분할(DiaryMap 컴포저블 / DiaryMapMarkers 상수·비트맵·표현식·머지·클러스터 / DiaryOpenWarp 파장 연출) + 주석 다이어트. 빌드 그린(2026-07-14).
- [ ] 35-2. GlobeRenderer.kt(1606줄) — seam 분할(지오메트리/셰이더·렌더 패스) + 주석 다이어트.
- [ ] 35-3. Android diary 화면들 — DetailScreen(786) / UploadScreen(678) / BoomerangCaptureScreen(510) / ShareCardEditor(549) / StarClusterScreen / NotificationScreen. ⚠️ DetailScreen 의 dex 레지스터(VerifyError) 경고 주석은 반드시 유지.
- [ ] 35-4. Android profile/home/friend/chat/auth 화면들 — AchievementsScreen(637) / MainListScreen(596) / SettingsScreen(588) / FloatingStatBox(586) / FriendScreen(570) / MainScreen(556) / MyDiaryScreen(555) / ProfileScreen(519) / UserProfileScreen(515) / MusicScreen(489) / DiaryStarBox / MyScreen / ChatScreen / LoginScreen / MainOnboardingOverlay.
- [ ] 35-5. Android core/data/navigation/push + shared — ShareCardHelper(563) / StarStyle(444) / MusicManager / GifEncoder / repository 들 / NavGraph / GoogleAuthHelper 등.
- [ ] 35-6. iOS 전체(66파일) — GlobeScreen(1440) 우선, 파리티 앵커 주석은 유지. 컴파일은 push 후 CI(ios.yml)로 검증.

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

