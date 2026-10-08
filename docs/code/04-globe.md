# 04. 3D 지구본(글로브)

Android: `feature/globe/GlobeScreen.kt`, `feature/globe/GlobeRenderer.kt`
iOS: `Features/Globe/GlobeScreen.swift` + `GlobeRenderer.swift`(**Metal — Android GL 렌더러 1:1 포팅**) + `GlobeGeometry.swift`

지도(03 문서)에서 줌을 `GLOBE_BUTTON_ZOOM(3.0)` 이하로 빼면 하단 "지구 보기" 버튼이 뜨고,
눌러야만 진입한다(자동 전환 없음). 진입/복귀는 검정 디졸브 스크림(`globeScrim`)이 SurfaceView
교체를 가린다.

---

## GlobeScreen.kt
- `GlobeScreen(diaries, startLat, startLng, onRequestExit)` :
  GLSurfaceView 오버레이. `startLat/Lng` 방향이 정면으로 오도록 시작 회전을 잡는다.
  드래그=회전, 핀치 인=지도 복귀 요청(`onRequestExit(현재 정면 lat, lng)` → MainListScreen 이
  `GlobeReturnCamera(zoom 4.0)` 로 지도 카메라 점프). 우상단 X 버튼도 동일 복귀.
- **성능 계약**: 글로브 진입 시에만 생성, 이탈 시 뷰 detach 로 GL 컨텍스트 해제(평상시 비용 0).
  2026-07-18 부터 지도 화면이 상시 렌더로 바뀌면서, **다른 화면으로 나가면 MainListScreen 이
  글로브를 자동 종료**한다(숨은 GLSurfaceView 렌더 낭비 방지 — 03 문서).

## GlobeRenderer.kt (GLSurfaceView.Renderer)
- 커스텀 GL 렌더: **은하수 성운 하늘 구** + 지구(**유리 지구** — 어두운 유리 구슬, 태양 쪽 반사광/반대쪽 어둠, 육지는 얼음 유리) + **푸른 대기광** + 별밭 +
  **핑크 은하수**(은하수.jpg 스타일) + **곡선 유성**(잔류 스파클) + 별 단위 12궁(zodiac) +
  다이어리 별 포인트.
- `setDiaries(...)` : 백그라운드에서 별 데이터 빌드 → `StarBatch`(버퍼+정점수 한 묶음, @Volatile)
  발행 → GL 스레드가 VBO 업로드 후에만 정점 수를 갱신.
  ⚠️ **버퍼와 정점 수를 따로 발행하면 안 된다** — "새 정점 수 + 옛 VBO" 를 그리는 프레임이 생겨
  버퍼 밖을 읽는다(별이 깨져 보임).
- 카메라: 쿼터니언 회전 + 관성. 핀치 아웃/인으로 진입 줌 느낌.

### 유리 지구 + 은하수 성운 (2026-10-08 — 레퍼런스 `references/지구본.jpg`)
이력: ① "GTA 같은 딱딱한 실사" → 수채 파스텔 지구 → ② 레퍼런스("미니멀 라인 + 파티클")를 받아 **대륙 점 지구** → ③ "점은 안 찍어도 되니 유리 질감만, 태양 쪽 반사·반대쪽 어둡게" → **유리 지구** →
④ "태양 위치의 동그란 반사 삭제 / 바다는 반사 적고 깊게 / 문명 빛 점 제거 / 육지 조금 어둡고 반사감 강하게" → ⑤ "육지 조금 더 어둡게 + 테두리 미세한 푸른 광택"(현재).
수채 지표·구름 텍스처와 구름 레이어, 도시 불빛(문명 점)은 삭제. **별(다이어리 불빛)·궤도 링(트레일)은 "지금 느낌 좋다"고 해서 계속 그대로**.
- **조명이 핵심**(`EARTH_FS`/iOS `earthFragment`): 실제 UTC 태양 방향 `uSunDir` 로 `day = smoothstep(−0.18, 0.55, n·sun)`. 낮 쪽 = 밝은 푸른 유리 + 림, 밤 쪽 = 훨씬 어둡다. 지구본을 돌려도 "지금 실제로 낮인 곳"이 밝다.
- **바다 = 깊고, 반사 없음**: 정면(수직으로 내려다보는 곳, `pow(ndv, 0.55)`)이 가장 깊은 남색 `(0.008,0.024,0.085)`, 가장자리로 갈수록 옅게 푸른 `(0.026,0.078,0.205)`(프레넬). 밤 쪽은 `×(0.28 + 0.72·day)`.
  **대륙붕**: 육지 마스크를 **흐린 밉**(바이어스 +4 ≈ 150km / +6 ≈ 600km)으로 읽어 해안에서 멀어질수록 부드럽게 옅어지는 얕은 바다 빛 — 처음엔 주변 8탭 샘플이었는데 줌인하면 네모 흔적이 생겨 밉 2탭으로 바꿨다.
- **육지 = 얼음 유리**: 마스크 `smoothstep(0.46, 0.54)` 면(알파 `0.70 + 0.25·day`) — 낮 `(0.13,0.25,0.52)` / 밤 `(0.034,0.064,0.17)`(10-08 세 번 "더 어둡게" 반영 — 마지막은 "햇빛 받았을 때 지금보다 더 어둡게") × 서리 노이즈 ±12%(`fbm(n·14)`).
  **해안**: 넓은 모서리 빛 띠(`coast`, 마스크 0.18~0.82) + **해안선을 따라 가는 가는 푸른 광택 띠**(`gloss`, 마스크 0.38~0.60, 강도 `0.50·(0.30 + 0.70·태양쪽)`, 색 `(0.55,0.78,1.0)`) — 두꺼운 유리 모서리의 하이라이트, 태양 쪽 해안이 더 반짝인다.
  **환경 반사**: 법선을 촘촘한 노이즈(`vnoise(n·24)`·±0.30)로 흔든 반사 벡터가 태양을 향할 때 넓게(`pow(·, 2.6)`) 반짝이고 고운 결(`vnoise(n·46)`)로 깨져 유리 면에 일렁이는 반사처럼 보인다 + 프레넬 코팅(`pow(edge, 1.8)`).
  ⚠️ **동그란 하이라이트(블린-퐁 `pow(n·H, 260)`)는 일부러 없다** — 날카로운 반사 로브를 쓰면 태양 위치에 둥근 점이 생긴다(사용자가 삭제 요청). 반사 로브를 다시 좁히지 말 것.
- **림/윤곽선**("겉 테두리를 더 잘 보이게"로 강화): `pow(edge,2.6)·1.5` 푸른 번짐 + `smoothstep(0.72,1)²·1.05` 윤곽선 — **태양 쪽 림이 `0.30 → 1.0` 으로 훨씬 밝다**(`rimLit`, 어두운 쪽도 윤곽은 읽히게 최소 0.30). 대기광 셸(`ATMO_FS`)도 `0.30 + 0.70·smoothstep(−0.2,0.8, n·sun)` 로 태양 쪽이 더 환하다(세기 0.34).
- **육지 마스크** `assets/globe_land.jpg`(**4096×2048**, R=G=B 육지 0..255) — `tools/globe/bake_globe_land.py`: `bake_globe_textures.land_mask`(바다 = `b − max(r,g) > 17`) → **큰 블러(2.6px) + 문턱(smoothstep 0.38~0.62)으로 윤곽을 둥글게**
  (원본의 사각 커널 형태 연산 흔적 — 네모 호수·계단 해안 — 이 줌인에서 보여서) → 마지막 블러 1.0px. 폭 ≈ 25km 보다 작은 섬/해협은 사라진다. 밉맵 사용(면/해안을 연속 UV 로 읽어 줌아웃에서 깜빡이지 않는다).
- **은하수 성운**: `assets/globe_nebula.jpg`(2048) 를 지구 메쉬 ×`SKY_RADIUS 80` 구에 안쪽에서 — 가장 먼저, 불투명·깊이 무시.
  분홍 성운은 앱 은하수 띠(`Rz(28°)·Rx(62°)`, 법선 ≈ (−0.22, 0.41, 0.88))를 따라 짙어지게 구웠다 — 은하수 기울기를 바꾸면 다시 구울 것(`bake_globe_textures.py` 는 이제 이것만 굽는다).
  ⚠️ 레퍼런스의 하늘은 순수 남색이라 분홍 성운과 톤이 다르다 — 거슬리면 성운 세기를 낮추거나 푸른 톤으로 다시 구울 후보.
- **다이어리 불빛 덮어 그리기**: `pinProgram`(SPRITE_VS + PIN_FS, 블렌드 ONE/ONE_MINUS_SRC_ALPHA, 정점색은 밝기 정규화해 색조만). 어두운 유리 지구 위에서도 그대로 잘 읽혀 유지.
- 궤적·별밭·유성·태양·12궁은 그대로.
- **개발 방법(셰이더 튜닝)**: 기기에 올리기 전에 numpy 로 같은 식을 정사영 구에 그려 레퍼런스와 비교하며 상수를 정했다(⚠️ 시뮬 태양은 **카메라 기준**으로 잡을 것 — 지구 좌표로 잡으면 화면 중앙이 밤 쪽에 걸려 너무 어둡게 보인다).
  GLSL 문법은 `glslangValidator`(Android SDK `emulator/lib64/vulkan/`)에 `#version 100` 을 붙여 검증. 기기에서 글로브를 열려면 지도를 줌 3 이하로 내려(− 버튼) "우주에서 보기" 를 누른다.
  MSL 은 런타임 컴파일이라 CI 가 초록이어도 오타가 있으면 글로브가 검정 — **iOS 는 기기 확인 필수**.

### 다이어리 별빛 — "퍼지는 글로우" → "빛나는 반짝임" (2026-10-08)
사용자: "별들 조금 더 반짝이고, 퍼지는 느낌이 아니라 빛나는 느낌으로" → 이어서 "별빛이 전부 노란색 — 실제 별 색에 맞춰" · "빛 모양이 너무 아이콘 같다".
- **텍스처를 따로 만든다** — 점광(`sparkTex`, 좋아요 100 미만)과 인기 별(`starTex`, 100+)은 더 이상 배경 별/유성이 같이 쓰는
  `glowTex`(알파 1→0.4→0 로 넓게 번지는 원)/`flareTex` 를 쓰지 않는다.
  `makeSparkBitmap(mainLen, crossLen, diagWeight)` 는 **픽셀마다 식으로 계산**한다(처음엔 경로로 그린 반듯한 십자라 "아이콘 같다"는 피드백):
  또렷한 심지(가우시안 σ≈0.11) + 아주 옅은 후광 + **중심에서 지수로 약해지는 가는 빛줄기**(가로 `mainLen` > 세로 `crossLen` 이라 비대칭,
  끝으로 갈수록 가늘고 가장자리에서 0). 인기 별만 짧고 흐린 대각 빛줄기(`diagWeight`)가 붙는다.
  `glowTex`/`flareTex` 를 고치면 배경 별·유성·별자리 반짝별까지 바뀌니 **다이어리 별 모양은 이 두 텍스처에서만** 바꿀 것.
- **별마다 각도를 돌린다** — `SPRITE_VS` 가 다이어리 스프라이트의 코너를 `aPhase·π`(0~180°) 만큼 돌린다(UV 는 그대로라 텍스처가 같이 돈다).
  모든 별이 같은 가로세로 십자로 보이지 않게 하는 장치.
- **빛 색 = 그 별의 실제 색(`starColor`)** — `lightColorOf`(Android) / `StarStyle.lightRGB`(iOS, SwiftUI 를 거치지 않는 순수 계산): 단색은 팔레트색,
  그라데이션 별은 두 색의 평균, 마지막에 γ 1.5 로 채도를 살짝 올린다(PIN_FS 가 밝기를 정규화하므로 색조만 의미; 흰 심지와 섞여도 색이 남게).
  예전엔 점광 전부 금빛 `(1, 0.76, 0.36)`, 인기 별은 좌표 해시로 뽑은 `FLARE_COLORS` 7색이었다(삭제됨).
- **반짝임은 `SPRITE_VS` 의 `uSparkle`(=1) 일 때만** — 다이어리 스프라이트(`aMode < 0.5`)에서 트윙클을 `0.74 + 0.26·sin(t·(1.9 + 2.7·phase))`
  (이전 0.82 ± 0.28, 더 빠르고 깊게) + **glint**(`sin^12`, 별마다 주기 4~9초, 한 번에 약 0.5초) `+0.55` 밝기, 크기 `+0.30·glint`.
  다른 스프라이트(배경 별·유성·태양)는 `uSparkle = 0` 이라 이전과 동일.
- `GLOW_PIN_GAIN` 0.7 → 1.0(번지는 면적이 줄어든 만큼 밝기 보상), 점광 크기 0.030 → 0.034(광선이 닿는 길이 고려).
- iOS `GlobeRenderer.swift`: `sparkTex`/`starTex` = `drawSparkTexture`(같은 식을 `CGContext.data` 에 직접 계산) + `spriteVertex` 의 `u[36]`(sparkle) 로 같은 식.
  ⚠️ MSL 은 런타임 컴파일이라 오타가 있으면 글로브 전체가 검정 — 기기에서 확인.

### ⚠️ 다이어리 불빛(별)은 깊이 버퍼로 가리지 않는다 (2026-08-06)
지표 바로 위에 뜬 스프라이트(글로 +0.008, 플레어 +0.045)는 **카메라 정면 빌보드**라
깊이 테스트를 켜면 두 가지로 깨진다:
1. **빌보드가 구면을 파고든다** — 정면에서 17°(글로)/50°(플레어)만 벗어나도 안쪽 절반이
   호 모양으로 싹둑 잘린다(스프라이트 반크기 > 지표와의 간격이라 기하학적으로 불가피).
2. **z-파이팅** — 깊이 해상도는 `z²·(1/near)` 에 비례. near 0.1 + 16비트에선 줌아웃(camDist 9.5)에서
   0.011 월드단위 = 글로 간격(0.008)보다 커서 지표와 같은 깊이 값이 되고, GL_LESS 라 얼룩덜룩
   탈락한다(구름 +0.012 도 아슬아슬).

→ 해결: 불빛은 `drawSprites(depthTest = false)`, 뒷면 가림은 `SPRITE_VS` 의 **해석적 지평선 컷**
(`smoothstep(-0.02, 0.22, dot(n, toCam))` — dot=0 이 정확히 구 접선이라 카메라 거리와 무관하게 정확).
남는 깊이 사용처(지구/구름/트레일)를 위해 **near 0.3**(`NEAR_PLANE`) + **깊이 24비트 EGL 설정 우선**
(`DepthFirstConfigChooser`, 없으면 16비트 폴백).
**스프라이트 반지름/크기를 키우거나 near/MIN_DIST 를 바꿀 땐 위 근거를 다시 계산할 것.**

---

## iOS 대응 — Metal 포팅 (2026-09, SceneKit 버전 대체)
- 역사: 2026-07 SceneKit 버전(별밭·은하수·12궁을 구면 **텍스처에 구워** 넣은 방식)은 Android(카메라 정면 빌보드
  스프라이트 수천 개)와 모양이 달랐고, 2026-08-09 `fba7329` 에서 통째로 제거됐다. 2026-09 사용자 요청
  "안드로이드와 동일하게"로 **Android GL 렌더러를 Metal 로 1:1 옮겨** 복원.
- `GlobeRenderer.swift` (MTKViewDelegate): Android 와 **같은 셰이더 식(MSL 로 번역) / 같은 정점 레이아웃
  (스프라이트 12 float) / 같은 그리기 순서·블렌딩(ONE,ONE / SRC_ALPHA) / 같은 깊이 규칙(스프라이트는 깊이 무시,
  지구 쓰기, 구름·트레일 테스트만)**. 투영은 GL 과 같은 fovy 42°·near 0.3·far 100 이되 깊이만 Metal NDC(0..1).
  - **`JavaRandom`** = `java.util.Random` 비트 단위 복제(LCG 48비트 + nextGaussian 극좌표법) — 별밭 Random(7),
    트레일 Random(11) 을 **같은 호출 순서**로 써서 별 위치/밝기가 Android 와 같다. ⚠️ buildStarfield 의 난수 호출
    순서를 바꾸면 하늘 전체가 달라진다(Android 쪽도 마찬가지) — 양쪽 같이 고칠 것.
  - 셰이더는 **런타임 컴파일**(`makeLibrary(source:)`) — CI 최신 Xcode 는 Metal 툴체인이 별도 컴포넌트라 `.metal`
    파일이 빌드를 막을 수 있어서. 대신 MSL 오타는 CI 가 못 잡고 실행 시 init 이 nil(검정+힌트만) → 기기 확인 필수.
  - 별자리 선: Metal 은 선 굵기가 1px 고정이라 GL_LINES(2px)를 **화면 공간 두께 사각형**으로(두께 0.77pt×배율).
  - 텍스처: 색 공간은 GL 과 같게 감마 그대로(`.bgra8Unorm`, 로더 `SRGB: false`), 생성 텍스처(플레어/글로우/태양)는
    CoreGraphics 프리멀티플라이드 RGBA + 밉맵. 지구/구름 JPG 는 **Android assets 파일을 project.yml 로 직접 참조**
    (예전 iOS 사본은 7096px 로 달라 삭제).
  - 셰이더 차이 1곳: MSL `smoothstep(1.0, 0.8, x)`(edge0≥edge1)은 정의되지 않아 `1 − smoothstep(0.8, 1.0, x)` 로(값 동일).
- `GlobeGeometry.swift`: 순수 계산(스프라이트/선/원호 빌더, 행렬, 좌표) — **격리 없는 enum**. 렌더러가 MTKViewDelegate
  채택으로 메인 액터 격리가 추론돼도 백그라운드 스프라이트 빌드가 컴파일되게 분리했다(여기에 UIKit/렌더러 참조 금지).
- `GlobeScreen.swift`: MTKView 래퍼 + 제스처(드래그 degPerPx 0.075×((camDist−1)/2.2) × 화면 배율, 속도×0.55 /
  핀치 camDist÷zoom / 아래 55% 탭 → X) + 힌트 4.2s·X 4s 자동 숨김. 백그라운드 진입 시 렌더 루프 정지.
- 지도 연동(`MapScreen`/`MapLibreView`): 줌 ≤3.0 → "우주에서 보기" 알약 버튼, 진입/복귀 스크림 170/520·170/70/380ms,
  복귀 시 내 위치 줌 15(`GlobeReturnCamera`). 가능 여부 콜백은 **바뀔 때 + 카메라 idle 때만**(매 프레임 SwiftUI 상태
  갱신 금지 — 과거 지도 행 원인 후보). 다른 화면으로 나가면(onDisappear) 글로브 닫힘. 웰컴 별은 글로브에 안 넘긴다.
- 크롬: Android 처럼 **상단바는 글로브 위에 남고**, 글쓰기 FAB 만 숨긴다(`MapChromeState.globeOpen`).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 진입 버튼 노출 줌(3.0)/최소 줌(2.4) | `DiaryMapMarkers` GLOBE_BUTTON_ZOOM·MAP_MIN_ZOOM | `MapLibreView` globeButtonZoom·mapMinZoom |
| 육지 마스크(R=G=B, 4096×2048) | `assets/globe_land.jpg` ← `tools/globe/bake_globe_land.py` | 같은 파일(project.yml 참조) |
| 유리 지구(태양 조명·깊은 바다·대륙붕·얼음 육지·해안 광택·환경 반사·림) | `EARTH_FS` | `shaderSource` earthFragment (**동일 식·값**) |
| 성운 하늘(반지름 80) | `SKY_RADIUS` + SKY_FS, `assets/globe_nebula.jpg` | `skyRadius` + skyFragment (같은 파일) |
| 대기광(셸 1.16, 푸른 (0.42,0.62,1.0)·0.26·태양 쪽 가중 0.30~1.0) | `ATMO_SCALE` + ATMO_FS | `atmoScale` + atmoFragment (**동일 식**) |
| 다이어리 불빛 덮어 그리기(gain 0.7/1.0) | `pinProgram`/PIN_FS, GLOW/FLARE_PIN_GAIN | `pinPipeline`/pinFragment(블렌드 3), glow/flarePinGain |
| 근거리 클립면(0.3) | `GlobeRenderer` NEAR_PLANE | `GlobeRenderer.nearPlane` (**동일 값**) |
| 지평선 컷(-0.02~0.22) | `SPRITE_VS` 의 vis | MSL `spriteVertex` (**동일 식**) |
| 카메라 거리/돌리/관성/자동회전 | companion ENTER/IDLE/MIN/MAX_DIST, stepSimulation | `GlobeRenderer` static + stepSimulation (**동일**) |
| 별밭/은하수/12궁/반짝별 데이터 | buildStarfield(Random(7)) | buildStarfield(`JavaRandom(7)`, **같은 호출 순서**) |
| 트레일 | buildTrails(Random(11)) + RING_FS | buildTrails(`JavaRandom(11)`) + MSL ringFragment |
| 유성/잔류 파장 | METEOR_* / SPARK_* | `GlobeRenderer` 같은 이름 static (**동일 값**) |
| 다이어리 스프라이트 | FLARE_* / GLOW_* | `GlobeGeometry` (**동일 값**) |
| 다이어리 별빛 모양(가로/세로 빛줄기 길이·대각 세기) | `SPARK_MAIN/CROSS_LEN` / `STAR_MAIN/CROSS_LEN` / `STAR_DIAG_WEIGHT` + `makeSparkBitmap` | `sparkMainLen` 등 + `drawSparkTexture` (**동일 식·값**) |
| 다이어리 별빛 색 | `GlobeRenderer.lightColorOf` ← `StarStyle.colorsOf` | `StarStyle.lightRGB` (**동일 식: 평균 후 γ 1.5**) |
| 다이어리 별 반짝임(빠른 깜빡임 + glint) | `SPRITE_VS` `uSparkle` 분기 | `spriteVertex` `sparkle`(= `u[36]`) 분기 (**동일 식**) |
| 복귀 카메라 | `DiaryMap` recenterToMyLocation(줌 15) | `MapLibreView` globeReturnCamera(내 위치 줌 15) |
| 전환 스크림 시간 | MainListScreen 170ms/520·380ms | MapScreen enter/exitGlobe(0.17/0.52·0.07·0.38s) |
