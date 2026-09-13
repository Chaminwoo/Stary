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
- 커스텀 GL 렌더: 지구(주/야 반구 셰이딩, `EARTH_BRIGHTNESS=0.45`) + 구름 + 별밭 +
  **핑크 은하수**(은하수.jpg 스타일) + **곡선 유성**(잔류 스파클) + 별 단위 12궁(zodiac) +
  다이어리 별 포인트.
- `setDiaries(...)` : 백그라운드에서 별 데이터 빌드 → `StarBatch`(버퍼+정점수 한 묶음, @Volatile)
  발행 → GL 스레드가 VBO 업로드 후에만 정점 수를 갱신.
  ⚠️ **버퍼와 정점 수를 따로 발행하면 안 된다** — "새 정점 수 + 옛 VBO" 를 그리는 프레임이 생겨
  버퍼 밖을 읽는다(별이 깨져 보임).
- 카메라: 쿼터니언 회전 + 관성. 핀치 아웃/인으로 진입 줌 느낌.

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
| 지구 밝기(0.45) | `GlobeRenderer` EARTH_BRIGHTNESS | `GlobeRenderer.shaderSource` earthFragment (**동일 값**) |
| 근거리 클립면(0.3) | `GlobeRenderer` NEAR_PLANE | `GlobeRenderer.nearPlane` (**동일 값**) |
| 지평선 컷(-0.02~0.22) | `SPRITE_VS` 의 vis | MSL `spriteVertex` (**동일 식**) |
| 카메라 거리/돌리/관성/자동회전 | companion ENTER/IDLE/MIN/MAX_DIST, stepSimulation | `GlobeRenderer` static + stepSimulation (**동일**) |
| 별밭/은하수/12궁/반짝별 데이터 | buildStarfield(Random(7)) | buildStarfield(`JavaRandom(7)`, **같은 호출 순서**) |
| 트레일 | buildTrails(Random(11)) + RING_FS | buildTrails(`JavaRandom(11)`) + MSL ringFragment |
| 유성/잔류 파장 | METEOR_* / SPARK_* | `GlobeRenderer` 같은 이름 static (**동일 값**) |
| 다이어리 스프라이트 | FLARE_* / GLOW_* | `GlobeGeometry` (**동일 값**) |
| 복귀 카메라 | `DiaryMap` recenterToMyLocation(줌 15) | `MapLibreView` globeReturnCamera(내 위치 줌 15) |
| 전환 스크림 시간 | MainListScreen 170ms/520·380ms | MapScreen enter/exitGlobe(0.17/0.52·0.07·0.38s) |
