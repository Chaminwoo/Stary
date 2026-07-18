# 06. 다이어리 상세 · 겹친 별 · 공유 카드

Android: `feature/diary/screen/DetailScreen.kt`, `StarClusterScreen.kt`, `ShareCardEditor.kt`,
`feature/diary/InteractionViewModel.kt`, `core/util/ShareCardHelper.kt`
iOS: `Features/Detail/DetailScreen.swift`, `DetailViewModel.swift`, `Features/Map/StarClusterView.swift`,
`Core/ShareCard.swift`

---

## DetailScreen.kt — 다이어리 상세

### 구조(위 → 아래)
1. **4:3 히어로 헤더** : 미디어(사진/움짤) 또는 `image_frame` + 가독성 스크림 +
   별·작성자(탭=프로필)·공개 배지·날짜 오버레이. 미디어 탭 → `FullScreenMediaViewer`.
2. 제목 / 본문 카드(0xCC14181C + accent 그라데이션 테두리).
3. 인라인 액션: 좋아요(하트) / 공유(`ShareDiaryButton`) / (내 글) 수정·삭제 / (남 글) 신고.
4. 댓글: "댓글 N" 헤더 + `CommentItem` 목록 + 입력창. 댓글 작성자 탭=프로필, 내 댓글 삭제 가능.
5. 100m 밖이면 잠금 pill(map_open_range) — 본문/댓글 가림.

### 상태/변수(주요)
- `ViewCountSession` : 앱 세션 동안 조회수를 올린 다이어리 id 집합(재진입 중복 카운트 방지).
- `interactionVm : InteractionViewModel` : 좋아요/댓글 상태(아래).
- 수정 모드 상태(제목/본문 편집 + 저장), 삭제 확인 다이얼로그, 신고 다이얼로그(`ReportDialog`).
- `DetailAuroraVeil(accent)` : 콘텐츠 위 고정 오로라 장식(15s 드리프트).
  ⚠️ **DetailScreen 본체에 인라인 금지** — dex 레지스터 한계로 크래시 이력. `ShareDiaryButton` 도 동일
  이유로 별도 컴포저블 유지.
- `FullScreenMediaViewer(mediaUrl, isVideo, onClose)` : 원본 비율(Fit) 전체화면 + 핀치 확대/드래그,
  mp4 는 소리와 함께 루프.

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
- `DetailScreen.swift` : Android 와 같은 구성(히어로/본문 카드/인라인 액션/댓글/잠금 pill/
  `FullScreenMediaViewer`/`RemoteGifFitView`/`DetailAuroraVeil`). push 진입(시트 아님).
- `DetailViewModel.swift` : 좋아요/댓글 리스너 — ⚠️ 모델 디코딩은 `@DocumentID` 명시 디코드 필수
  (12 문서의 id=nil 버그 참고).
- `StarClusterView.swift` : 겹친 별 카드 뷰어(자체 뒤로가기, 내비바 숨김). 카드 탭 → pop 후 0.35s
  뒤 상세 push(애니메이션 겹침 방지).
- `ShareCard.swift` : 카드 렌더 + 편집 + 인스타 스토리 공유(Android 와 같은 규격/옵션).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 카드 규격(1080×1920)/배치 클램프 | `ShareCardHelper.kt` | `ShareCard.swift` |
| 공유 링크/딥링크 | shared `StaryConfig.shareLink` | `AppConfig` |
| 히어로 비율(4:3) | `ImageCropHelper.ASPECT` 공용 | iOS 상수 |
| 조회수 중복 방지 | `ViewCountSession`(세션 집합) | iOS 대응 로직(ViewedStore/세션) |
