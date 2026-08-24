# 07. 프로필 (내 프로필 · 타인 프로필 · 내 다이어리 보드)

Android: `feature/profile/screen/ProfileScreen.kt`, `FloatingStatBox.kt`, `UserProfileScreen.kt`,
`UserDiaryStarsScreen.kt`, `MyDiaryScreen.kt`, `DiaryStarBox.kt`, (`MyScreen.kt` — 구버전, 미사용 경로),
`feature/profile/ProfileViewModel.kt`, `core/util/NicknameStore.kt`, `core/util/ProfilePinState.kt`
iOS: `Features/Profile/ProfileScreen.swift`, `FloatingStatBox.swift`, `UserProfileScreen.swift`,
`MyDiaryBoardScreen.swift`(안에 `MyStarsScreen` 진입점 포함)

---

## ProfileScreen.kt — 내 프로필

### 상태/변수
- `profileVm : ProfileViewModel` : 프로필 이미지 URL·업로드 진행/에러를 담는 뷰모델.
  - `profileImageUrl` : Firestore 에 저장된 커스텀 프로필 사진 URL(null 이면 구글 사진 → 기본 아이콘 폴백).
  - `isUploading` : 업로드 중 별 로딩 표시. `uploadError` : 실패 메시지(토스트 후 `clearError()`).
- `galleryLauncher` : 갤러리에서 사진 선택(`GetContent`) → `profileVm.uploadProfileImage(uri)`.
- `stats : UserStats` : `rememberUserStats(userId)` — 좋아요/친구/다이어리/조회 등 통계 집계(08 문서).
- `equippedStigmaId` / `equippedStigma` : 장착 칭호 id(`StigmaStore`, 로컬 저장) / 로케일 해석된 표시명.
  `LaunchedEffect` 로 Firestore `users/{uid}.equippedTitle` 에 백필(타인에게 보이도록).
- `hiddenClaims` / `myHiddenIds` : 히든 업적 선점 현황 / 그중 내가 달성한 id 목록.
- `diaryVm : DiaryViewModel` + `myDiaries` : 내 다이어리 전체(핀 후보 목록).
- `pinnedIds` / `pinnedDiaries` / `showPinPicker` : 프로필에 띄울(핀) 다이어리 id(최대 3) / 해석된 목록 /
  선택 다이얼로그 표시. **핀 열기는 탑바 + 버튼** — `SideEffect` 로 `ProfilePinState.onOpen` 등록.
- `displayName` / `showNicknameDialog` : 표시 닉네임(기본=구글 닉네임, 이름 탭 → 변경 다이얼로그, 20자 제한).
  변경 시 Firestore `users.userName` + `NicknameStore`(로컬 캐시) + `GoogleAuthHelper` 갱신.
- `unlockedCount` / `totalCount` : 일반 업적 달성/전체 수.

### 화면 구성(컴포넌트 연결)
- 배경: `mydiary_bg` + 검정 0.82 틴트. `FirstVisitInfo("info_profile")` 1회 안내.
- 중앙 Column(`zIndex(1f)` — 부유 아이콘 오버레이보다 위): 아바타(후광, 탭=사진 교체) →
  닉네임(+`HiddenStarBadges`) → 칭호(히든이면 금색 『』, 탭=업적 화면).
- 하단: 로그아웃 카드(`zIndex(1f)` 로 항상 눌리게).
- `FloatingStatBox` 에 items = [하트(버스트), 친구, 다이어리(책), 업적(버스트)] + 핀 별 + 히든 아이콘,
  `onTap` = idx1→친구 화면 / idx2→내 다이어리 / idx3→업적 / 핀 별→`onOpenDiary(id)`
  (NavGraph 에서 `MapFocusState.request(id, withRoute=true)` — 지도 길찾기) / 히든→업적 화면.
- `PinDiaryPicker` : 내 다이어리 중 최대 3개 토글 선택 → `FirebaseFriendRepository.setPinnedDiaries`.

## FloatingStatBox.kt — 떠다니는 물리 아이콘 (프로필 공용)

### 데이터
- `StatBubble(icon, count, color, label, burstOnTap, showCount, starType, starColorIndex, hiddenEffect)` :
  아이콘 1개 정의. `burstOnTap`=빠른 탭에 파티클 버스트(하트/친구/업적/히든),
  `starType>=0` 면 벡터 대신 크리스탈 별(핀 다이어리), `hiddenEffect` 는 히든 업적 전용 오라/잔상.
- `Particle` : 버스트 파편(위치/속도/수명/스핀). `Body` : 아이콘 물리 상태(부유 기준점/속도/회전/잔상 큐).

### 동작(핵심 상수)
- `FLING_DAMP=1.7`(던진 뒤 감속) `WALL_REST=0.72`(벽 반발) `BALL_REST=0.82`(아이콘끼리 반발)
  `STOP_SPEED=10`(정지 판정) `MAX_FLING=3600`(던지기 상한) `richness(count)`(수가 많을수록 1~1.5배).
- 제스처: 150ms 홀드 또는 slop 이동 → "잡기"(1.7배 확대 + 수/라벨 표시), 놓으면 속도 그대로 던져짐 →
  벽/아이콘 충돌 반발 → 모두 느려지면 부유 모드 복귀. **빠른 탭** → `burstOnTap` 버스트 + `onTap(idx)`.
- 렌더: 벡터 아이콘도 `bakeCrystalIcon` 으로 크리스탈 파편 채움 비트맵 1회 베이크(80dp 2배 해상도),
  핀 별은 `bakeCrystalStar`. 히든 아이콘은 궤도 스파클 오라 + 이동 시 잔상 trail.
- 물리 루프는 `physicsActive` 일 때만 충돌 계산(평상시엔 가벼운 sin 부유) — 렉 방지.

## UserProfileScreen.kt — 타인 프로필

### 상태/변수
- `photoUrl` / `resolvedName` / `equippedTitleId` : 대상의 공개 프로필(사진/이름/칭호) 1회 로드.
- `stats` / `theirDiaries` / `unlockedCount` : 대상 uid 기준 통계·다이어리·업적 수.
- `pinnedIds` / `pinnedDiaries` : 그 사람이 핀한 다이어리 — 별로 떠 있고 **탭하면 지도 길찾기**.
- `theirHiddenAch` : 그 사람이 달성한 히든 업적(전용 아이콘/파티클).
- `vm : FriendViewModel` + `friends` / `isFriend` / `requested` / `showCancelDialog` : 친구 상태·요청.
- `moderation` / `blockedIds` / `isBlocked` / `showReportDialog` / `showBlockDialog` : 차단/신고.
  **차단은 확인 다이얼로그를 거친다**(숨겨지는 범위 + 친구 해제 안내). 해제는 즉시.
  차단 시 `block(..., targetPhotoUrl = photoUrl)` 로 이름·사진 스냅샷을 남겨 차단 목록에서 그대로 쓴다(09 문서).
- `visibleDiaries` : 공개범위 필터(private=본인만, friends=본인·친구만) 적용된 목록.
- `SideEffect` → `UserProfileActionState` 에 탑바 버튼 상태/콜백 등록(친구추가·취소/신고/차단),
  `DisposableEffect.onDispose` 에서 `reset()`.

### 화면 구성
- 헤더(아바타/이름/칭호) + `FloatingStatBox`:
  items = [하트(버스트), **친구(버스트)**, 다이어리(책), 편지(채팅), 업적(버스트)] + 핀 별 + 히든 아이콘.
  `onTap` = idx2→`UserDiaryStars` 화면 / idx3→친구면 채팅·아니면 "친구만 채팅" 토스트 /
  핀 별→`onOpenDiary(id)`(NavGraph 가 `withRoute=true` 로 지도 길찾기) / 히든→버스트만.
- 친구 취소 확인 / **차단 확인** / 신고 다이얼로그.

## UserDiaryStarsScreen.kt — "OO님의 별" (타인 다이어리 별 보드)
- 내 다이어리 보드와 동일한 다이얼+부유 별 UI 재사용. 차이: **별 탭 → `onOpenMap(diaryId)`**
  (NavGraph 에서 `MapFocusState.request(id, withRoute=true)` → 지도 카메라+파장+도보 길찾기).

## MyDiaryScreen.kt + DiaryStarBox.kt — 내 다이어리(별 보드)

### MyDiaryScreen.kt
- `sortColor(s)` : 정렬별 테마색 — 최신=`0xFF7FB7FF` 파랑 / 인기=`0xFFFF9CC6` 분홍 / 거리=`0xFFB89BFF` 보라.
- `MyDiaryScreen(onDiaryClick)` : 내 다이어리를 `DiaryStarsBoard` 로 표시(탭=상세).
- `DiaryStarsBoard(diaries, onDiaryClick, ...)` : **별자리 배경 + 바나나 다이얼 + 떠다니는 별 + 1열 리스트**
  묶음. 내/타인 화면 공용(클릭 동작만 다름).
- `BananaDial(selected, onSelect)` : 포물선 모양 다이얼(최신/인기/거리). 선택 시 `MusicManager.playWind()`.
- `CONSTELLATIONS` : 정렬별 별자리 문양 데이터(reference png 픽셀 분석 좌표 0..1 + 연결선 + mag).
- `ConstellationBackground(sort, flashKey)` : 별자리 렌더(트윙클 + 선택 시 번쩍임).
- `DiaryListColumn` : 1열 리스트 보기(행=별 아이콘+제목+날짜, 탭=상세).

### DiaryStarBox.kt
- `DiarySort` : LATEST/POPULAR/DISTANCE — 다이얼·보드 공용 정렬 enum.
- `StarBody` : 떠 있는 별 1개의 물리 상태(반지름/좌표/위상/구운 비트맵).
- `DiaryStarBox(diaries, sortMode, onClick...)` : 별들을 공중에 띄우고 정렬 변경 시 그 순서대로
  하나씩 끌려와 재배치(easeOutCubic). 별은 `bakeStarBody` 로 후광+크리스탈을 1회 굽고 회전만 그린다.

## MyScreen.kt — ⚠️ 구버전 화면(현재 NavGraph 미연결). 수정 금지·참고만.

## ProfileViewModel.kt / NicknameStore.kt
- `ProfileViewModel(userId)` : `profileImageUrl`/`isUploading`/`uploadError` StateFlow +
  `uploadProfileImage(uri)`(UserRepository 경유 Storage 업로드), `factory(userId)`.
- `NicknameStore` : uid별 커스텀 닉네임 **로컬 캐시**(진짜 소스는 Firestore `users.userName`).
  `get(context, uid)` / `set(context, uid, name)`.

---

## iOS 대응

### ProfileScreen.swift (내 프로필)
- `mine` : `store.mine(uid:)` 정렬본. `stats`/`unlockedCount` : Android 와 같은 집계.
- `equippedTitleId`(+`titleDisplayText/Color`, 히든=금색 『』), `myHiddenAch`, `pinnedDiaries`,
  `bubbles : [StatBubble]`(하트/친구/다이어리/업적 + 핀 별 + 히든 — Android 와 같은 구성).
- `handleBubbleTap(idx)` : 친구/내 별/업적 push, **핀 별 → `MapFocusStore.request(id, withRoute: true)`**.
- `runHiddenClaims()` : 자동 조건 히든 업적 선점 시도. `PinDiaryPicker` : 최대 3개 토글(Android 패리티).
- 아바타 탭 → PhotosPicker 로 프로필 사진 교체(ImageUploader).

### UserProfileScreen.swift (타인 프로필)
- `visibleDiaries`(공개범위 필터) / `pinnedIds` / `friendsCount` / `isFriend`·`requested` /
  `isBlocked`·신고 메뉴(툴바 ⋮) / `theirHiddenAch`.
- `bubbleData` : [하트(버스트), **친구(버스트)**, 다이어리(책), 조회] + 핀 별 + 히든 아이콘.
- `handleBubbleTap` : **핀 별 → `MapFocusStore.request(diaryId, withRoute: true)`** —
  MainTabView 가 pendingDiaryId 변화를 보고 루트(지도)로 pop, MapScreen 이 카메라+파동+길찾기(03 문서).
- 하단 `actionRow` : 본인/친구(채팅 버튼)/친구 요청 버튼.

### FloatingStatBox.swift
- `StatBubble` : Android 와 동일 필드(순서: systemImage, count, color, label, burstOnTap, showCount,
  starType, starColorIndex, hiddenEffect).
- `FloatingEngine`(StateObject) : 물리/버스트/오라를 담당. `Canvas(symbols:)` 로 그리고,
  버블마다 투명 히트 뷰(DragGesture)를 겹쳐 터치를 받는다(전체 캔버스가 터치를 먹지 않게).
- ⚠️ `Canvas(symbols:)` 심볼 안에 Canvas 중첩 금지 — 크리스탈 별/아이콘은
  `StarCrystal.image / StarCrystal.iconImage`(NSCache 비트맵)로 넘긴다.

### MyDiaryBoardScreen.swift
- `MyStarsScreen`(드로어 "내 다이어리" 진입점) → `MyDiaryBoardScreen` : 별자리 3종(좌표/연결선
  **Android CONSTELLATIONS 와 동일 값**) + 바나나 다이얼(`MusicManager.playWind()`) + 부유 별 보드 +
  1열 리스트(0x66161B22 행). ⚠️ 드래그 물리는 Android 대비 간이(후속 TODO).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 부유 아이콘 물리 상수 | `FloatingStatBox.kt` FLING_DAMP 등 | `FloatingStatBox.swift` FloatingEngine 상수 |
| 핀 별 최대 수(3) | `PinDiaryPicker`(양쪽 하드코딩 3) | `PinDiaryPicker` |
| 정렬 테마색(파랑/분홍/보라) | `MyDiaryScreen.kt` LatestBlue 등 | `MyDiaryBoardScreen.swift` 동일 hex |
| 별자리 문양 좌표 | `MyDiaryScreen.kt` CONSTELLATIONS | `MyDiaryBoardScreen.swift` (**값 동일 유지**) |
| 핀 별 탭 동작 | NavGraph `MapFocusState.request(id, withRoute=true)` | 각 화면 `MapFocusStore.request(diaryId:withRoute:)` |
| 닉네임 20자 제한 | ProfileScreen 다이얼로그 | ProfileScreen `.onChange(of: nicknameDraft)` 선차단 |
| 프로필 사진 크롭(위치·확대) | `core/ui/ProfilePhotoCropDialog.kt`(결과 640px 정사각) | `Features/Profile/ProfilePhotoCropView.swift` + `Core/ImageCrop.profileOutPixels` |
| 타인 프로필 사진 확대 뷰어 | `core/ui/PhotoViewer.kt`(핀치 1~5배, 더블탭 2.5배) | `Core/PhotoViewer.swift` (**동작/배율 동일**) |
