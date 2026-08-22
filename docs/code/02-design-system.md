# 02. 디자인 시스템 · 공용 UI

Android: `core/designsystem/`(Color, Type, Theme, StarStyle), `core/ui/`(공용 컴포넌트)
iOS: `Core/Theme.swift`, `AppFont.swift`, `StarStyle.swift`, `StarShape.swift`, `StarCrystal.swift`,
`BundleImage.swift`, `StarLoadingView.swift`, `StarBirth.swift`, `HiddenStarBadge.swift`,
`ImageCache.swift`, `LoopingVideoPlayer.swift`, `Features/InAppBanner.swift`, `FirstVisitInfo.swift`

---

## Color.kt — 색 토큰(단일 출처)
- `Bg=0xFF0D0D0D`(앱 배경) `Surface1=0xFF1A1A1A` `Surface2=0xFF242424` `Outline=0xFF2E2E2E`
- `TextPrimary=0xFFF0F0F0` `TextSub=0xFF8A8A8A` `AccentRed=0xFFFF4F4F`
- `Mint=0xFF6EE7B7` : 흩어져 있던 민트 리터럴의 단일 출처(별자리/내위치 마커 등 잔존).
- `MintBlue=0xFF3B82F6` : 그라데이션 짝.
- ⚠️ 참고: 2026-07 색 개편으로 화면 강조색은 대부분 **남색 계열**(`0xFF9FB3E8` Accent, `0xFF1E3A8A` Navy)
  로 바뀌었는데, 이 값들은 각 화면 파일 상단의 private val 로 선언돼 있다(화면별 튜닝 허용 설계).

## Type.kt — 폰트
- `PoetsenOne` : 영문 디스플레이(대형 헤더). `MinSans` : 한글 본문·UI 전반(가변 폰트).
- ⚠️ `MinSans` 는 "요청 굵기 → 실제 wght 축" 매핑 테이블(Light=300 … Bold=550).
  **일괄 굵기 치환 금지** — 같은 FontWeight 중복 등록 시 어떤 wght 가 걸릴지 모호해진다.
- `Typography` : display/headline/title=PoetsenOne, 본문/label=MinSans.

## Theme.kt — `StaryTheme` : Material3 다크 팔레트 + Typography 적용(MainActivity 에서 감쌈).

## StarStyle.kt — 별 모양·색·크리스탈 렌더의 심장 ⭐

**별과 관련된 모든 시각 요소가 이 object 를 지난다.** 지도 마커/피커/프로필/공유카드/파장 전부 공유.

- `TYPE_COUNT = 9` : 모양 수 — 0 4꼭지 스파클 / 1 5꼭지 별 / 2 6꼭지 / 3 8꼭지 가는 스파클 /
  4 다이아 스파클 / 5 꽃 / 6 다이아몬드 / 7 초승달 / 8 행성 (5~8은 수집 보상 형태).
- `COLOR_COUNT = 21` : 0~15 단색 팔레트(흰색 30% 혼합으로 "빛나는" 톤) + 16~20 2색 그라데이션
  (고난도 업적 보상, `GRAD_START=16`).
- `palette` / `gradients` / `isGradient(i)` / `gradientOf(i)` / `colorOf(i)`(대표색) / `colorsOf(i)`.
- `fillShader(index, left, top, sizePx)` : 그라데이션 채움 Shader(단색이면 null).
- `starPath(type, sizePx)` : 모양 Path. **모양 추가/수정은 여기 + iOS `StarShape.swift` 동시에.**
- `drawCrystalFill(canvas, type, colorIndex|colors, left, top, sizePx, alpha)` :
  실루엣 clip 후 내부를 불규칙 파편(크리스탈)으로 채우는 공용 렌더 —
  파편 메시는 결정론적 해시(같은 별=같은 무늬), 볼록 돔 셰이딩 + 좌상단 하이라이트.
- `drawCrystalFacets(..., silhouette, seed)` : 실루엣 없이 사각 영역에 파편만(아이콘 알파 마스킹용).
- ⚠️ 새 별 type 추가 시: `starPath` + `facetDensity`(파편 밀도) + iOS `StarShape.swift` + 업로드 피커
  해금 조건까지 한 세트.

## core/ui 공용 컴포넌트

- `StaryToast`(StaryToast.kt) : 하단 커스텀 토스트. `StaryToast.show(text)` 어디서든 호출,
  호스트(`StaryToastHost`)는 MainScreen 최상단 1개. 남색 배경(0xFF131B36 계열), 2.2초.
- `InAppBanner`(InAppBanner.kt) : 상단 인앱 배너(알림/채팅/근처 별). `InAppBanner.show(...)` +
  `InAppBannerHost`. 토스트와 별개 채널. 표시 4초. 같은 key 반복 dedup.
- `Haptics.kt`(core/util) : **앱 전역 햅틱** — `tick()`(눈금) `light()` `medium()`(좋아요)
  `heavy()` `celebrate()`(업적/별 탄생) `warp()`(별 열람 파장). `AppSettings.hapticsEnabled`
  가 꺼져 있으면 전부 무음, 진동 모터 없으면 조용히 무시. `AppSettings.init` 에서 `Haptics.init`.
  ⚠️ **이미 시각/청각 연출이 있는 지점에만** 준다 — 아무 버튼에나 넣으면 진동이 배경소음이 된다.
  Manifest 에 `android.permission.VIBRATE` 필요(런타임 요청 없는 일반 권한).
- `LikeButton.kt` : 좋아요 버튼 — 하트 pop(스프링) + **크리스탈 파편 12개 버스트** + 숫자 롤링.
  `accent` 에 그 별의 색을 넘기면 다이어리마다 다른 색으로 터진다. **켤 때만** 버스트/진동(해제는 조용히).
  ⚠️ **파편은 레이아웃에 참여하면 안 된다.** 크기는 버튼 44dp 로 고정하고 파편은
  `matchParentSize` + 고정 반지름(`BURST_RADIUS` 36dp)으로 **경계 밖에 그리기만** 한다.
  (예전엔 72dp Canvas 가 Box 의 자식이라 버스트 동안만 행이 밀렸다 — iOS 는 `.background` 로 동일 처리.)
- `StaryEmptyState.kt` : 빈 화면 공용 — 떠 있는 별(6초 부유 + 궤도 스파클 3개) + 문구 + 선택 액션.
  알림/친구/내 다이어리/차단 목록/채팅이 모두 이걸 쓴다(화면마다 `starType`/`starColorIndex` 만 다름).
- `ClickBounce.kt` : `Modifier.clickBounce(peak=1.12)` — 누르면 통통 튀는 스케일.
  Initial 패스 관찰이라 클릭 처리와 간섭 없음. size 다음·border 앞에 배치 권장.
- `StaryComponents.kt` :
  - `Modifier.appCard(radius)` : 공용 카드 배경/테두리.
  - `Modifier.raisedCosmicBorder(width, shape)` : 지도 원형 버튼의 볼록(엠보스) 남색 테두리.
  - `StarShapeIcon(type, color|colorIndex, modifier)` : 별 아이콘(크리스탈 채움) — 피커/목록/카드 공용.
  - `DiaryCard(...)` : 다이어리 리스트 카드(목록/클러스터에서 사용).
  - `TextMain=0xFFF2F4FA`/`TextMuted=0xFF8A92A6` 등 이 파일 전용 톤.
- `StarLoading.kt` : `StarLoadingIndicator(size, colorIndex|color)` — 회전하는 별 로딩
  (비트맵 1회 베이크 후 스케일/회전만 — 팔레트 밖 색은 별도 캐시).
- `StarBirth.kt` : 업로드 성공 연출(01·05 문서). `StarBirthState.trigger(type, color)` →
  `StarBirthHost`(MainScreen)가 화면 중앙에서 응축→발광→내려앉기 950ms 재생. 터치 통과.
- `HiddenStarBadge.kt` : `HiddenStarBadges(userId, size, max)` — 이름 옆 히든 업적 크리스탈 배지
  (HiddenClaimStore 전역 구독). `HiddenStarBadge(type, colorIndex)` 단독 사용 가능.
- `CrystalIcon.kt` : `bakeCrystalIcon(painter, color, seed, sizePx, layoutDirection)` —
  벡터 아이콘 실루엣을 크리스탈 파편으로 채운 비트맵 1회 베이크(SRC_IN 마스킹).
- `FirstVisitInfo.kt` : `FirstVisitInfo(seenKey, icon, title, message)` — 화면 첫 진입 1회 안내
  다이얼로그(prefs `stary_onboarding`). 화면 본문 어디서든 호출 가능(Dialog 는 자체 윈도우).
- `ReportDialog.kt` : 신고 사유 선택(스팸 등 키를 `onSubmit`) — 다이어리/댓글/사용자 공용.
- `GifImage.kt` : 움짤(GIF) 표시 — 전역 Coil 로더 재사용. 로컬 File/원격 URL 지원.
- `ThumbAsyncImage.kt` : `ThumbAsyncImage(model, contentDescription, modifier, sizePx)` —
  지정 크기로 다운샘플 디코드(목록 썸네일 최적화).
- `VideoPlayer.kt` : `LoopingVideoPlayer(...)` — VideoView 기반 루프 재생(muted 지원).

---

## iOS 대응

- `Theme.swift` : Android Color.kt 1:1 토큰(Bg/Surface/Outline/TextPrimary/TextSub/Mint/MintBlue/
  AccentRed) + `navyAccent`(0x9FB3E8)·`navyDeep` 등 남색 계열. `Color(hex:)` 유틸.
- `AppFont.swift` : **Android 와 같은 폰트 파일을 그대로 쓴다**(8.45) —
  `.font(.minSans(size, weight))` 가 앱 전역 기본. PoetsenOne 은 영문 디스플레이용(양쪽 다 실사용 거의 없음).
  - 폰트 파일은 `androidApp/src/main/res/font/min_sans.ttf` **한 곳**을 iOS 타깃이 참조
    (`project.yml` sources 에 상대경로 + `UIAppFonts: min_sans.ttf`). 9MB 짜리를 복제하지 않는다.
  - MinSans 는 **가변 폰트(wght 100~900, 기본 100=Thin)** — 그냥 `UIFont(name:)` 로 만들면 안드로이드보다
    훨씬 얇게 나온다. `MinSansWeight`(light 300 / normal 400 / medium 450 / semibold 500 / bold 550 —
    **Android Type.kt 매핑표와 동일**)로 `kCTFontVariationAttribute` 의 'wght' 축을 지정해 생성한다.
  - 폰트 이름은 런타임에 해석(`AppFont.body`) — PostScript `MinSansVF-VF` → 패밀리 후보 → "minsans"
    포함 패밀리 순. 실패 시 시스템 폰트 폴백 + 콘솔 경고(레이아웃은 유지).
  - `Font(UIFont)` 기반이라 **Dynamic Type 비례 확대가 없다**(고정 크기). Android 가 폰트 배율 상한
    (`StaryResponsive.MAX_FONT_SCALE`=1.15)을 두는 것과 같은 취지 — 고정 높이 카드에서 글자가 잘리지 않게.
  - ⚠️ 크기 값은 **Android 의 sp 숫자를 그대로** 쓴다(예: 상단바 18 SemiBold, 드로어 항목 17 SemiBold,
    본문 16, 보조 13, 캡션 12/11). 새 화면을 만들 땐 Android 대응 화면의 `fontSize` 를 그대로 옮길 것.
  - 참고(치수 감각): 같은 pt 에서 MinSans 는 이전 PoorStory 대비 x-height 약 1.4배, 줄높이 약 1.07배라
    **글자가 더 크고 꽉 차 보인다** — 이게 안드로이드의 실제 모습이다(축소해서 맞추지 말 것).
- `StarStyle.swift` + `StarShape.swift` : 팔레트/그라데이션/모양 Path — **Android StarStyle 과 값 동일
  유지**(모양·색 추가 시 양쪽 동시). 달·행성·꽃·보석은 진짜 boolean 연산 —
  ⚠️ iOS16 타깃이라 `Path.union/subtracting`(iOS17+) 대신 `CGPath` 기반 `unionCompat/subtractingCompat`.
- `StarCrystal.swift` : 크리스탈 렌더 + NSCache —
  `StarCrystal.image(type:colorIndex:size:)`(별) / `iconImage(systemName:color:seed:size:)`(SF Symbol 을
  알파 마스크로 파편 채움) / 내부 `drawMesh(salt:silhouette:)`. ⚠️ 매 프레임 파편 재생성 금지 —
  항상 이 캐시 이미지 재사용.
- `BundleImage.swift` : 번들 이미지 NSCache 로더 + `ScreenBackground(name:darken:)` —
  Android 의 "배경 이미지 + 검정 틴트" 대응(값: Upload 0.82, Settings 0.84, Chat 0.85 등).
- `Haptics.swift` / `LikeButton.swift` / `StaryEmptyState.swift` :
  Android 동명 컴포넌트 포팅(파편 개수·시간·이징 **값 동일**). 햅틱은 `UIImpactFeedbackGenerator`
  (제너레이터 재사용 — 매번 만들면 첫 진동이 늦다), 토글은 `AppSettings.shared.hapticsEnabled`.
- `StarLoadingView.swift` / `StarBirth.swift`(StarBirthStore.shared+StarBirthHost) /
  `HiddenStarBadge.swift` : Android 동명 컴포넌트 포팅(비트맵 1회 베이크 원칙 동일).
- `InAppBanner.swift`(InAppBannerHost) / `FirstVisitInfo.swift` / `ImageCache.swift`(썸네일 캐시) /
  `LoopingVideoPlayer.swift` : 각각 배너/1회 안내/이미지 캐시/루프 영상 대응.
- ⚠️ iOS 장식 Canvas 공통 규칙: `TimelineView(.animation)` + `allowsHitTesting(false)`,
  Canvas 수식은 Double 통일 후 CGFloat 변환(혼합 시 컴파일 에러).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 별 팔레트/그라데이션/모양 | `StarStyle.kt` | `StarStyle.swift`+`StarShape.swift` (**값 동일**) |
| 크리스탈 파편 밀도/셰이딩 | `StarStyle.drawCrystalFill/Facets` | `StarCrystal.drawMesh` |
| 배경 이미지·틴트 | 화면별 Image+ColorFilter alpha | `ScreenBackground(name:darken:)` |
| 토스트/배너 노출 시간 | `StaryToast`(2.2s)/`InAppBanner`(4s) | iOS ToastView/`InAppBanner.swift` |
| 로딩 별 | `StarLoadingIndicator` | `StarLoadingView` |
| 햅틱 세기/패턴 | `core/util/Haptics.kt`(진동 길이·진폭) | `Core/Haptics.swift`(UIImpactFeedbackGenerator 스타일) |
| 좋아요 파편 수/시간 | `LikeButton.kt` SHARD_COUNT=12 / BURST_MS=620 | `LikeButton.swift` (**값 동일**) |
| 좋아요 파편 반경(레이아웃 제외) | `LikeButton.kt` BURST_RADIUS=36dp + matchParentSize | `LikeButton.swift` `.background{ .frame(72) }` |
| 빈 화면 별 부유 주기 | `StaryEmptyState.kt` 6초 | `StaryEmptyState.swift` (**값 동일**) |
| 별 탄생 연출 길이 | `StarBirth.kt` BIRTH_MS=950 | `StarBirth.swift` 대응 상수 |
