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
- `setDiaries(...)` : 백그라운드에서 별 데이터 세팅 → dirty 플래그 → GL 스레드가 VBO 업로드.
- 카메라: 쿼터니언 회전 + 관성. 핀치 아웃/인으로 진입 줌 느낌.

---

## iOS 대응 — GlobeScreen.swift (SceneKit)
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
| 복귀 줌(4.0) | `MainListScreen` GlobeReturnCamera(…, 4.0, …) | `MapScreen.exitGlobe`(zoom: 4.0) |
| 전환 스크림 시간 | MainListScreen 170ms/520·380ms | MapScreen enter/exitGlobe(0.17/0.52·0.38s) |
