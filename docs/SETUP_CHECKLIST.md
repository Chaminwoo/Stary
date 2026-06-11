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

### A-3. 마커 (별 종류×색상, 4번과 연동) — ⏳ 남음
- [ ] 다이어리 별 마커 **재구현**(현재 제거, 내 위치만 표시). MapLibre `SymbolManager`(annotation plugin) 또는
      GeoJSON source + `SymbolLayer` 로 type(0~4)×color(0~11) 아이콘 렌더.
- [ ] 색상 12색 / 종류 5형을 `core/designsystem` 상수로 정의(렌더·업로드 공용).
- [ ] 마커 클릭 → 100m 게이팅(`LocationHelper.distanceBetween` ≤ `StaryConfig.DIARY_OPEN_RADIUS_M`).

### A-4. 파티클 효과 — ⏳ 남음
- [ ] MapLibre 내장 없음 → **지도 위 Compose 오버레이**(`Box { AndroidView(MapView) ; ParticleOverlay }`, `Canvas`).

### A-5. 정리 — ✅ PROJECT_NOTES 6절(지도) 갱신 완료.

---

## 📋 기능 백로그 (의존성 순서 고려)

### 1. 지도 UI 리팩토링  *(A-1·A-2 ✅ / A-3·A-4 남음)*
- [x] MapLibre 전환 + 베이스 스타일(검정/물/큰길) + 줌 색보간. (`feature/map/screen/DiaryMap.kt`, `res/raw/maplibre_style.json`, `MainListScreen.kt`)
- [ ] A-3 다이어리 마커 / A-4 파티클 (위 A 섹션 참고).

### 2. "위치 보기" 버튼 제거 + 100m 밖 다이어리 → 도보 길찾기  *(A-3 마커 재구현과 함께)*
- [ ] `DiaryMap`/`MainListScreen` 에서 재팔로우(`onRefollowClick`, `isFollowing`) **버튼 제거**.
- [ ] 다이어리 마커 클릭 시(A-3) 100m 밖이면 Toast 대신 **도보 길찾기 실행**:
      `Intent(ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))`(패키지 `com.google.android.apps.maps`),
      폴백 `https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking`.
- [ ] 100m 이내는 열람. (게이팅: `StaryConfig.DIARY_OPEN_RADIUS_M`)

### 3. 친구 추가 + FriendScreen  *(4·6·7의 선행)*
- [ ] Firestore 설계: `users/{uid}/friends/{friendUid}` (+ 요청용 `friendRequests` 또는 상태 필드).
- [ ] `shared` 에 `FriendRepository` 인터페이스 + Android Firebase 구현(`data/repository/FirebaseFriendRepository.kt`).
- [ ] `feature/friend/screen/FriendScreen.kt` + ViewModel: 사용자 검색(이름/이메일)·요청·수락·목록.
- [ ] `navigation/NavGraph.kt`·`NavRoute.kt` 에 라우트 추가, 진입점(프로필/메인) 연결.

### 4. 업로드 시 별 종류·색상 선택 + Firestore 기록  *(A-3와 연동)*
- [ ] `Diary` 모델에 `starType: Int`(0~4), `starColor: Int`(0~11) 추가 (`shared/core/model`). 기존 문서 기본값 처리.
- [ ] `UploadScreen` 에 종류/색상 피커 UI. 업로드 시 두 값 저장(`FirebaseDiaryRepository`).
- [ ] 마커 렌더가 이 값으로 그려지도록(A-3) 연결.

### 5. 미조회 다이어리만 보기
- [ ] 사용자별 조회 기록 필요: `users/{uid}/viewedDiaries/{diaryId}` (또는 로컬 캐시).
- [ ] 다이어리 열람 시 기록 적재. 메인/리스트에 **미조회만** 토글 필터.

### 6. 친구 다이어리만 보기  *(3 선행)*
- [ ] 친구 목록 기준으로 `diaries` 필터(작성자 `userId` ∈ friends).
- [ ] 메인 지도/리스트에 **친구만** 토글 필터(5번 토글과 함께 필터 상태 관리).

### 7. 친구 다이어리 알람  *(3 선행 + 서버)*
- [ ] `NotificationType` 에 `FRIEND_POST` 추가, `AppNotification` 생성 경로 추가.
- [ ] ⚠️ 타인에게 푸시하려면 **FCM + Cloud Functions(서버)** 필요(클라이언트만으로는 불가):
      친구가 업로드 → Function 트리거 → 친구들에게 FCM 발송.
- [ ] 인앱 알림 목록(`NotificationScreen`)에도 표시.

### 8. 알람·딥링크로 앱 실행  *(7과 함께)*
- [ ] FCM 수신 서비스(`FirebaseMessagingService`) 추가 + 알림 표시(PendingIntent).
- [ ] 딥링크: App Links(`https://`) 또는 커스텀 스킴 → `MainActivity`/NavGraph 에서 해당 다이어리로 라우팅.
- [ ] iOS 미지원/이슈 시 대안: Firebase Dynamic Links 종료(2025) 고려 → **Universal Links + FCM data payload** 또는 인앱 라우팅으로 대체.

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
