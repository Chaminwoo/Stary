# 04. 3D 지구본(글로브)

Android: `feature/globe/GlobeScreen.kt`, `feature/globe/GlobeRenderer.kt`
iOS: `Features/Globe/GlobeScreen.swift`(SceneKit)

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

## iOS 대응 — GlobeScreen.swift (SceneKit)
- ⚠️ 노란 도시 야경 점광은 iOS 에선 **노드가 아니라 지구 emission 텍스처에 베이크** — 위 빌보드
  문제는 플레어(좋아요 100+)에만 해당한다. 플레어는 `readsFromDepthBuffer = false` +
  `.surface` 셰이더 모디파이어 지평선 컷(Android `SPRITE_VS` 와 동일 식) + `renderingOrder = 10`.
- 커스텀 UV 구체(`sphereGeometry`) + 낮/밤 반구 셰이더 모디파이어 + 구름 + 별밭/은하수/유성 +
  다이어리 별. 핀치 인 → `onRequestExit(lat, lng)` (MapScreen 이 `GlobeReturnCamera` 처리).
- ⚠️ **법선(normals) 필수**: 커스텀 메쉬에 셰이더 모디파이어(`_surface.normal` 사용)를 얹으면서
  법선 소스가 없으면 파이프라인 컴파일 실패로 **노드가 통째로 사라진다**(8.44 #12 — 지구 안 보임
  버그의 원인). 새 커스텀 메쉬 추가 시 방사방향 단위법선을 꼭 넣을 것.
- 지구 감광도 `원본×0.45` — Android `EARTH_BRIGHTNESS=0.45f` 와 동일 유지.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 진입 버튼 노출 줌(3.0)/최소 줌(2.4) | `DiaryMapMarkers` GLOBE_BUTTON_ZOOM·MAP_MIN_ZOOM | `MapLibreView` globeButtonZoom·mapMinZoom |
| 지구 밝기(0.45) | `GlobeRenderer` EARTH_BRIGHTNESS | GlobeScreen 셰이더 계수 (**동일 값**) |
| 근거리 클립면(0.3) | `GlobeRenderer` NEAR_PLANE | `camera.zNear` (**동일 값**) |
| 지평선 컷(-0.02~0.22) | `SPRITE_VS` 의 vis | 플레어 `.surface` 모디파이어 (**동일 식**) |
| 복귀 줌(4.0) | `MainListScreen` GlobeReturnCamera(…, 4.0, …) | `MapScreen.exitGlobe`(zoom: 4.0) |
| 전환 스크림 시간 | MainListScreen 170ms/520·380ms | MapScreen enter/exitGlobe(0.17/0.52·0.38s) |
