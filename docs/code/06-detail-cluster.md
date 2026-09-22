# 06. 다이어리 상세 · 겹친 별 · 공유 카드

Android: `feature/diary/screen/DetailScreen.kt`, `DiaryLock.kt`, `TutorialStarDetailScreen.kt`, `StarClusterScreen.kt`, `ShareCardEditor.kt`,
`feature/diary/InteractionViewModel.kt`, `core/util/ShareCardHelper.kt`, `core/util/DiaryUnlockStore.kt`
iOS: `Features/Detail/DetailScreen.swift`, `DiaryLockViews.swift`, `DetailViewModel.swift`, `TutorialStarDetailScreen.swift`,
`ShareCardEditorView.swift`, `Core/ShareCard.swift`, `Features/Map/StarClusterView.swift`.
공유 카드는 2026-07-19 iOS 에서 한 번 제거(e6e438e)됐다가 **2026-09 사용자 결정으로 편집기까지 포함해 재구현**(아래 iOS 대응).
배경 이미지 캐시 `ShareCardBackground` 는 여전히 `StarClusterView.swift` 에 있고 공유 카드/겹친 별 카드가 함께 쓴다.

---

## DetailScreen.kt — 다이어리 상세

### ⚠️ 열람 잠금(2026-09-21 도입 → 09-22 개편 — 여기가 100m 규칙의 **유일한** 집행 지점)
예전엔 지도에서 100m 밖 별을 탭하면 거리 토스트로 **진입 자체가 막혔다**. 지금은 누구나 들어와서
**제목까지** 보고, 그 외(히어로 미디어·본문·좋아요/공유/신고·댓글)는 잠긴다.
- `unlocked = isMyDiary || isNear || everUnlocked` — `isNear` 는 `StaryConfig.DIARY_OPEN_RADIUS_M`(100m),
  `everUnlocked` 는 `core/util/DiaryUnlockStore`(**영구 해금** 기록, SharedPreferences `ad_unlock_store`).
  - **한 번 열린 글은 계속 열린다**(09-22 "다이어리도 아예 해금 형식"): 광고를 끝까지 보거나 **100m 안에 한 번 들어오면**
    (`LaunchedEffect(diaryId, isNear, isMyDiary)`) 기록 → 멀어져도 잠기지 않는다. 예전 7일 TTL 은 폐지,
    TTL 시절 광고 기록도 같은 prefs 키라 그대로 영구 해금으로 승계.
- **댓글 작성은 해금과 무관하게 무조건 100m 이내**(내 글 포함 — 예전 게이팅 선례와 동일).
  `CommentInputRow`(같은 파일 private): 로그인 && `isNear` 가 아니면 입력창 비활성 + 자리표시 문구
  "100m 이내에서만 댓글을 남길 수 있어요", 탭하면 `submitComment` 가 이유 토스트(로그인/100m). 댓글 **열람**은 해금이면 가능.
- 잠금 UI 는 전부 **`DiaryLock.kt`**(dex 레지스터 이슈로 본체와 분리):
  - `LockedHero(isVideo)` — **미디어가 있을 때만**: 미디어 로딩 플레이스홀더(`MediaLoadingFrame(loaded=false)` = loading_dipper)
    + 우하단(작성자·날짜 줄 바로 위, end 20 · bottom 52dp) **작은 캡션** "이 사진/영상은 잠겨 있어요"(`detail_locked_photo/video`).
    색 = 별자리 선의 청보라를 밝힌 `0xE6AEBBDF` + 같은 계열 번짐 `0x997F93CC`(blur 14), 12sp · 자간 0.4.
    별자리는 영상의 가로 14~88% · 세로 23~60% 라 캡션과 안 겹친다. (09-22: 가운데 크리스탈 자물쇠·35% 어둡게 판은 "예쁘지 않다"로 삭제)
    **미디어가 없으면 잠금 표시 없이** 일반 글과 같은 `image_frame`(DetailScreen `when` 의 else). 원본 미디어 URL 은
    **로드조차 하지 않는다**(가리기만 하면 Coil 캐시·전체화면 뷰어로 새어나감). `FullScreenMediaViewer` 도 `unlocked` 로 한 번 더 막는다.
  - `LockedContentCard` — 카드(배경+테두리) 대신 **`cornerCrossFrame`**: 좌상단·우하단 두 모서리에만 헤어라인(0.75dp) 십자.
    안쪽 팔 가로 64 · 세로 40dp(끝으로 사라짐), 바깥 팔 7dp, 교차점 점(1.3dp) + 반경 6dp 옅은 광. 색 = accent 에 흰색 18%.
    레퍼런스(`references/내용 부분 레퍼런스.png`)의 렌즈 플레어·큰 후광은 일부러 뺐다("AI 티" 피드백).
    안: **크리스탈 재생 로고**(유튜브 비율 직접 그린 `PlayLogo`, 54dp) + "100m 이내로 다가가거나, / 광고를 통해 열어보세요!"
    (`detail_locked_title`, 14.5sp Medium) + "현재 위치로부터 N"(`detail_locked_distance`, 12sp tnum). **별도 "광고 보기" 버튼은 없다** — 아이콘이 버튼.
  - `CrystalPullIcon` — 재생 로고. `bakeCrystalIcon`(프로필 부유 아이콘과 같은 파편 재질, 무늬 시드 = diaryId 해시)
    + 뒤 후광 숨쉬기(1.6s). **제자리 고정**, 잡아당기면 `PULL_MAX(16dp)·(1−e^(−d/PULL_SOFT(70dp)))` 만큼만 끌려오고
    당긴 쪽으로 최대 6° 기울며, 놓으면 `spring(0.38, 380)` 로 출렁이며 복귀. 터치 슬롭 안에서 떼면 **탭 → `watchAdToUnlock`**.
  - `watchAdToUnlock` → `core/ads/AdsManager.showRewardedWhenReady`(Unity **LevelPlay**): 로드돼 있으면 바로 재생, 아니면 "광고를 불러오는 중이에요"
    후 최대 8초 기다려 도착 즉시 재생. 키 없음/로드 실패/시간 초과/Activity 없음 → "지금은 광고를 불러올 수 없어요".
    디버그 빌드에서는 원인(키 미설정 / LevelPlay 오류 코드 `lastError`)을 토스트로 알려 준다.
    끝까지 봄 → `DiaryUnlockStore.unlock` + `Haptics.celebrate()` + 토스트, 건너뜀 → "끝까지 봐야 열려요".

### 구조(위 → 아래)
1. **4:3 히어로 헤더** : 미디어(사진/움짤) 또는 `image_frame` + 가독성 스크림 +
   별·작성자(탭=프로필)·공개 배지·날짜 오버레이. 미디어 탭 → `FullScreenMediaViewer`.
   ⚠️ 진입 시 사진이 깨지며 드러나던 **크리스탈 리빌은 삭제됨**(2026-08-22 사용자 테스트 피드백).
2. 제목 / 본문 카드(0xCC14181C + accent 그라데이션 테두리).
3. 인라인 액션: 좋아요(`LikeButton` — 하트 pop + 크리스탈 파편 버스트 + 숫자 롤링, 파편 색 = 그 별의 색,
   02 문서) / 공유(`ShareDiaryButton`) / (내 글) 수정·삭제 / (남 글) 신고.
   수정·삭제·신고는 `TextButton` 이 아니라 **`CompactTextAction`**(같은 파일 private) —
   `TextButton` 의 최소 폭 58dp 때문에 "수정 삭제" 사이가 과하게 벌어져서, 글자 폭 + 좌우 8dp 로 좁혔다
   (터치 높이는 40dp 유지). iOS 는 `Spacer().frame(width: 16)` 로 같은 간격.
   ⚠️ 좋아요 토스트는 없앴다 — 버스트 자체가 피드백이라 중복이었다.
4. 댓글: "댓글 N" 헤더 + `CommentInputRow`(100m 밖이면 잠김) + `CommentItem` 목록. 댓글 작성자 탭=프로필, 내 댓글 삭제 가능.
5. 잠겨 있으면 3·4 는 아예 그리지 않고, 2 의 본문 카드 자리에 `LockedContentCard` 만 남는다.

### 상태/변수(주요)
- `ViewCountSession` : 앱 세션 동안 조회수를 올린 다이어리 id 집합(재진입 중복 카운트 방지).
- `interactionVm : InteractionViewModel` : 좋아요/댓글 상태(아래).
- 수정 모드 상태(제목/본문 편집 + 저장), 삭제 확인 다이얼로그, 신고 다이얼로그(`ReportDialog`).
  ⚠️ **DetailScreen 본체에 인라인 금지** — dex 레지스터 한계로 크래시 이력. `ShareDiaryButton` 도 동일
  이유로 별도 컴포저블 유지.
- `FullScreenMediaViewer(mediaUrl, isVideo, onClose)` : 원본 비율(Fit) 전체화면 + 핀치 확대/드래그,
  mp4 는 소리와 함께 루프.

## TutorialStarDetailScreen.kt — 웰컴 별(튜토리얼) 게시물
- 첫 실행 기기에만 근처(약 40m)에 놓이는 클라이언트 전용 별(`core/util/TutorialStarState`)을 탭하면
  일반 별과 같은 경로(파장 → `navigateToDetail("tutorial_star")`)로 오고, NavGraph 가 id 를 보고 이 화면으로 분기.
  (예전엔 지도 위 안내 카드 다이얼로그였다 → 2026-09 게시물형으로 교체.)
- `DetailScreen` 을 재사용하지 않는다 — Firestore 로드/조회수/좋아요·댓글 리스너가 diaryId 에 묶여 있어
  가짜 id 로는 "불러오기 실패" 또는 쓰기가 나간다. **레이아웃만 흉내**(4:3 히어로 + 스크림 + 별·작성자·날짜 →
  제목 → 본문 카드) — DetailScreen 레이아웃을 크게 바꾸면 여기도 맞출 것.
- 히어로 = `loading_dipper`(북두칠성 애니메이션 WebP, 640×480 = 4:3, 89프레임 무한루프) — 실제 글의 로딩 플레이스홀더를
  이 별의 "사진"으로 둔다. 좋아요/공유/댓글 없음, 하단 민트→블루 "확인" 버튼 = 지도로(`navigateUp`).
- 모양/색/작성자는 `TutorialStarState.STAR_TYPE`(3)/`STAR_COLOR`(15 앰버골드)/`AUTHOR_NAME`("STARY") — 지도 마커와 공유.
- 화면이 **열리는 순간** `markDone()` — 지도가 가려진 동안 마커가 빠져서, 돌아가면 별이 이미 없다
  (나갈 때 지우면 퇴장 페이드 중 별이 뚝 사라지는 게 보인다).
- ⚠️ 알려진 한계: 웰컴 별 30m 안에 실제 별이 있으면 30m 머지로 그 별 그룹에 흡수되고(0좋아요·최신이라 대표가 못 됨),
  겹친 별 카드 뷰어는 Firestore 에서 못 찾는 id 를 버리므로 웰컴 별을 열 수 없다.
- iOS: `Features/Map/TutorialStarState.swift` + `Features/Detail/TutorialStarDetailScreen.swift` (같은 상수/40m 오프셋/
  열리는 순간 소비). 분기는 `MapScreen` 의 상세 `navigationDestination`(id 비교). iOS 는 겹친 별 카드 뷰어가
  Diary 를 그대로 받으므로 30m 머지에 흡수돼도 카드로 열린다(합성 다이어리에 제목을 채워 둔 이유).

## InteractionViewModel.kt — 좋아요/댓글
- `isLiked` / `likeCount` : `FirebaseLikeRepository` 실시간 관찰(StateFlow).
- `comments` : `FirebaseCommentRepository.observeComments` 실시간 목록.
- `toggleLike()` / `addComment(content)` / `deleteComment(commentId)`.
- 좋아요/댓글 성공 시 알림 문서 생성은 repository 계층에서(11 문서).

## StarClusterScreen.kt — 겹친 별 카드 뷰어
- `StarClusterScreen(ids, onOpenDiary, onBack)` : 30m 머지 그룹을 **우선순위(좋아요↓→오래된 순,
  지도 대표 선정과 동일) 순서의 좌우 스와이프 카드**로. 헤더의 별들이 현재 페이지와 연동
  (활성 별만 밝게+확대+후광).
- `ClusterDiaryCard(diary, rank, ...)` : 세로 포트레이트 카드(밤하늘 프레임 + 스크림).
  탭 → `onOpenDiary(id)`(상세로).

## ShareCardHelper.kt — 공유 카드 생성/공유 (인스타 스토리 1080×1920)
- 디자인: AI 밤하늘 배경(`assets/share_card_bg.webp`) + **별 좌표 중심 나라 지도**(원형 페더 마스크)
  + 정중앙 별 + 하단 제목·위치·날짜.
- `ExtraStar(xFrac, yFrac, scale, type, colorIndex)` : 편집에서 얹는 장식 별(내 다이어리 별).
- `CardOptions(stageXFrac/stageYFrac, titleXFrac/YFrac, locationXFrac/YFrac, dateXFrac/YFrac,
  extraStars...)` : 편집 화면에서 조정하는 배치 옵션(전부 카드 내 상대좌표 0..1).
- `prepareAssets(...)` / `release()` : 역지오코딩 동네명 + 지도 비트맵을 1회 준비 후 재사용.
- `shareToInstagramStory(...)` : ADD_TO_STORY 인텐트. ⚠️ 링크스티커(content_url)는 Meta 앱 ID 등록
  없이는 인스타가 조용히 무시 — 그래서 링크를 클립보드에 복사하고 시스템 토스트로 안내한다.
  미설치/실패 시 `shareDiary`(일반 공유 시트) 폴백.
- `regionMapBitmap(...)` : ⚠️ MapTiler **정적 지도 API 는 이 키/플랜에서 403** —
  래스터 타일(z4)을 직접 스티칭해 512px 지도 생성. 실패 시 지도 없이 별만(카드는 항상 생성).
- 공유 링크: `StaryConfig.shareLink(id)` → 웹 랜딩 → 설치자는 `stary://diary/{id}`, 미설치자는 스토어.

## ShareCardEditor.kt — 공유 카드 편집 화면
- 공유 버튼 → 미리보기 + 드래그로 별/제목/위치/날짜 배치, 장식 별 추가/크기 조절 →
  `CardOptions` 를 만들어 ShareCardHelper 렌더에 전달.

---

## iOS 대응
- `DetailScreen.swift` : Android 와 같은 구성(히어로/본문 카드/인라인 액션/댓글/
  `FullScreenMediaViewer`/`RemoteGifFitView`). push 진입(시트 아님).
  잠금도 동일 — `canOpen = isOwner || isNear || DiaryUnlockStore.isUnlocked(id)`(영구, `onAppear`/`onChange(of: isNear)`
  에서 접근 해금 기록), `canComment = 로그인 && isNear`. 잠김이면 미디어 있을 때만 `lockedHero`(loading_dipper + 우하단 캡션) + `lockedContentCard`(크리스탈 재생 로고 `DiaryLock.playLogoPath` — Android `PlayLogo` 와 같은 24×24 좌표).
  공용 부품은 `DiaryLockViews.swift`(`DiaryLock` 상수/시드/고무줄 + `CrystalPullIcon`), 경로 아이콘 베이크는
  `StarCrystal.pathIconImage`. 시드는 Java `String.hashCode` 와 같은 식이라 두 플랫폼 무늬가 같다.
  광고 결과·댓글 100m 안내는 `ToastView` 토스트. ⚠️ **iOS 는 아직 Unity Ads SDK 미연결**(`Core/AdsManager.swift`
  스텁, `isConfigured == false`) → 아이콘을 탭하면 "광고를 불러올 수 없어요". 다음 iOS 광고 라운드에서 TODO 자리를 채운다.
- `DetailViewModel.swift` : 좋아요/댓글 리스너 — ⚠️ 모델 디코딩은 `@DocumentID` 명시 디코드 필수
  (12 문서의 id=nil 버그 참고).
- `StarClusterView.swift` : 겹친 별 카드 뷰어(자체 뒤로가기, 내비바 숨김). 카드 탭 → pop 후 0.35s
  뒤 상세 push(애니메이션 겹침 방지).
- **공유 카드**: `Core/ShareCard.swift` = `ShareCardHelper.kt` 포팅(CoreGraphics, 1080×1920 픽셀 공간에서
  **같은 좌표/알파/반경**) — 배경 늘려 채움 + 하단 스크림 + 무대 무드, 지도 타일 z4 스티칭 + 원형 페더(destinationIn)
  + 이중 링, 히어로 별 이중 글로우(CG shadow) + `StarCrystal` + 스파클, 제목(투명 레이어 + sourceAtop 그라데이션,
  `hangulWordPriority`), 위치 캡슐, 날짜, 비네트. `ShareCardOptions`/`ShareExtraStar` 필드·기본값 동일.
  - 편집기 `ShareCardEditorView`(fullScreenCover): 드래그 대상 판정 반경·클램프 범위·추가 별 프리셋 동일,
    드래그 중엔 재렌더 생략(점선 링만) + 60ms 스로틀 + `Task.detached` 렌더.
  - 공유: 인스타 = `instagram-stories://share`(+`source_application`=Info.plist `INSTAGRAM_APP_ID`) + 페이스트보드
    (배경 PNG, 앱 ID 있을 때만 contentURL, 링크 텍스트) + "링크 스티커로 붙여넣기" 안내 / 미설치 → `UIActivityViewController`.
    `LSApplicationQueriesSchemes: instagram-stories` 필수(project.yml).
  - ⚠️ 공유 본문은 `L10n.value(for:)` 원문 사용 — `LocaleManager.t` 는 한글에 U+2060 결합자를 넣어 다른 앱으로 나가면 안 된다.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 공유 링크/딥링크 | shared `StaryConfig.shareLink` | `AppConfig` |
| 히어로 비율(4:3) | `ImageCropHelper.ASPECT` 공용 | iOS 상수 |
| 조회수 중복 방지 | `ViewCountSession`(세션 집합) | iOS 대응 로직(ViewedStore/세션) |
| 겹친 별 헤더(5개 이상 = 다이얼) | `StarClusterScreen.ClusterStarDial`(칸 30, 창 5칸, ±3, 아이콘 26) | `StarClusterView.starDial` (**수치 동일**) |
| 4개 이하 헤더(고정 겹침 배치) | `StarClusterScreen` 의 else 분기 | `StarClusterView.fixedStars` |
| 잠금 아이콘 고무줄 | `DiaryLock.kt` `PULL_MAX`(16dp) / `PULL_SOFT`(70dp) / `spring(0.38, 380)` / 최대 6° | `DiaryLock.pullMax`/`pullSoft` / `interpolatingSpring(380, 14.8)` (**수치 동일**) |
| 잠금 아이콘/캡션 | 재생 로고 54dp · 터치 ×1.7 / 캡션 12sp `0xE6AEBBDF` end 20 · bottom 52 | 54 · ×1.7 / `DiaryLock.captionColor` 같은 값 |
| 십자 코너 프레임 | `cornerCrossFrame` 0.75dp · 팔 64/40/7 · 점 1.3 · 광 6 | `CornerCrossFrame` (**수치 동일**) |
| 신고 사유 + "기타" 상세 | `core/ui/ReportDialog.kt` `onSubmit(reason, detail)` / REPORT_DETAIL_MAX_LEN | `Features/ReportDialog.swift` `onPick(reason, detail)` — iOS 는 "기타"만 알럿 한 단계 더 |
