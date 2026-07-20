# 03. 지도 (밤하늘 별 지도)

Android: `feature/home/screen/MainListScreen.kt`, `feature/map/screen/DiaryMap.kt`,
`feature/map/screen/DiaryMapMarkers.kt`, `feature/map/screen/DiaryOpenWarp.kt`,
`feature/home/screen/MapOnlyOverlay.kt`, `feature/map/OrsRouting.kt`
iOS: `Features/Map/MapScreen.swift`, `MapLibreView.swift`, `MapStyleEffects.swift`,
`DiaryOpenWarpView.swift`, `StarMerge.swift`, `StarImageRenderer.swift`, `OrsRouting.swift`

구조 한 줄 요약: `MainListScreen`(필터/글로브/포커스 준비) ⊃ `DiaryMap`(MapLibre 지도 본체)
⊃ `DiaryMapMarkers`(상수·표현식·비트맵 헬퍼). **지도는 MainScreen 이 NavHost 뒤에 상시 렌더**(01 문서).

---

## MainListScreen.kt — 지도 화면 컨테이너(필터·글로브·포커스)

### 상태/변수
- `diaryViewModel : DiaryViewModel` : 전체 다이어리 실시간 목록(`diaries`)을 주는 뷰모델(12 문서).
  지도 호이스팅 이후 액티비티 스코프라 화면 전환에도 유지된다.
- `liveLocation` / `currentLatLng` : `LocationHelper.location`(StateFlow) 관찰값 / 화면에서 쓰는 현재 좌표.
  첫 fix 전엔 "지난 세션 마지막 위치 → 기본좌표(건국대)" 순 폴백.
- `mockDetected` : 모의 위치(위치 조작 앱) 감지 → 1회 경고 토스트.
- `userId` : 로그인 uid. `FirebaseAuth.AuthStateListener` 로 관찰(로그인 화면 뒤 미리 렌더된 화면이
  로그인 직후 리컴포즈되게 하는 장치).
- 필터 상태: `unviewedOnly`(미조회만) / `friendsOnly`(친구만) / `myOnly`(나만보기)
  / `selectedFriendIds`(친구 선택) / `periodDays`(기간: null=전체, 0=오늘, N=최근 N일)
  / `showFriendPicker` / `showPeriodPicker` / `speedDialExpanded`(필터 다이얼 펼침).
  상호배타 규칙: 나만보기 켜면 친구만/친구선택 해제, 그 반대도 동일.
- `viewedIds` : 내가 연 다이어리 id 집합(FirebaseViewedRepository).
- `friends` / `friendIds` : 내 친구 목록/id 집합 — friends 공개범위 판정에 사용.
- `blockedIds` : 내가 차단한 uid 집합 — 그 사람 별은 지도/목록에서 숨김.
- `filteredDiaries` : 위 조건을 모두 적용한 표시 대상. **공개범위(friends) 판정도 여기서**:
  `visibilityType != "friends" || 내 글 || 친구 글`.
- `globeCenter` / `globeReturn` / `globeButtonCenter` / `globeScrim` : 3D 글로브 진입 좌표 /
  복귀 카메라 요청 / "지구 보기" 버튼 노출 후보 중심 / 지도↔글로브 교체를 가리는 검정 디졸브(04 문서).
  `LaunchedEffect(MapUiState.mapVisible)` : 다른 화면으로 나가면 글로브 자동 종료(숨은 GLSurfaceView 낭비 방지).
- `focusTarget : DiaryFocusTarget?` : `MapFocusState.pendingDiaryId` 를 전체 목록에서 좌표로 해석한 것.
  필터와 무관하게 `diaries` 전체에서 찾는다(필터로 숨겨진 별도 포커스 가능).
- `startLocationIfGranted` : 위치 권한이 있으면 연속 위치 추적 시작 + 일회성 fix 당김.
  ON_RESUME 마다 재시도(권한 다이얼로그 직후 바로 반영).
- `devKeyModifier` : 디버그 전용 WASD 위치 이동 치트(릴리즈에선 비활성).

### 화면 구성(컴포넌트 연결)
- `DiaryMap` 에 `filteredDiaries`, `currentLatLng`, `onDiaryClick(상세)`, `onClusterClick(겹친 별)`,
  `onCreateClick(업로드)`, `focusDiary=focusTarget`, `onFocusHandled=MapFocusState.consume`,
  `showCreate=(로그인 시만)`, `onGlobeAvailability`, `globeReturnCamera` 를 넘긴다.
- 좌하단 **필터 스피드 다이얼**: 메인 원형 버튼(나침반, 필터 활성 시 남색 강조) → 위로 알약 옵션들
  (미조회만/친구만/나만보기/친구선택/기간별). "전체보기" 항목은 없음 — 활성 칩 재탭으로 해제.
- 하단 중앙 "지구 보기" 버튼: `globeButtonCenter != null && globeCenter == null` 일 때만.
- `GlobeScreen` 오버레이 + `globeScrim` 디졸브(04 문서).

## DiaryMap.kt — MapLibre 지도 본체

### 파라미터
- `diaries` : 표시할 다이어리(필터 적용분). `currentLatLng` : 내 위치.
- `onDiaryClick(id)` / `onClusterClick(ids)` / `onCreateClick()` : 상세/겹친별/업로드 진입 콜백.
- `focusDiary : DiaryFocusTarget?` : 외부 포커스 요청(lat/lng/색/id/`withRoute`). `onFocusHandled()` : 소비 통지.
- `showCreate` : 업로드 FAB 노출(비로그인 숨김). `onGlobeAvailability` / `globeReturnCamera` : 글로브 연동.

### 상태/변수
- `mapView` : `rememberMapViewWithLifecycle()` — MapView 를 Compose+액티비티 생명주기에 묶음.
- `styleJson` : `res/raw/maplibre_style.json` 을 읽어 `__MAPTILER_KEY__` 를 BuildConfig 키로 치환.
- `mapRef` / `styleRef` : 비동기 초기화된 MapLibreMap/Style 참조(null 이면 아직 준비 전).
- 소스 참조: `locationSource`(내 위치 점) / `diarySource`(별 마커) / `orbitSource`(겹친 별 위성)
  / `constellationSource`(별자리 라인) / `routeSource`(도보 경로) / `pioneerSource`(개척 비콘).
- `orbitFade` : 위성 등장 페이드(머지 전환이 끝난 뒤 부드럽게).
- `savedRoute : List<Point>?` : 도보 길찾기 **전체** 경로. null=길찾기 꺼짐(X 버튼으로 취소).
  `LaunchedEffect(savedRoute)` 가 `MapUiState.routeActive` 를 갱신(복귀 재센터 예외 판정용).
- `addedIcons` : style.addImage 중복 방지용 등록된 아이콘 id 집합.
- `isCameraMoving` : 카메라 이동 중 여부 — 애니 루프가 이동 중엔 스타일 갱신을 쉰다(팬 끊김 방지).
- `hazeAlpha` : 저줌 대기 헤이즈 강도(0..1) — 줌아웃할수록 파란 대기가 차오름(글로브 전환 연출).
- `worldVoid` : 웹메르카토르 상하 타일 한계 밖 "빈 공간" 화면 경계 — 바다색으로 덮는다.
- `cameraIdleTick` : 카메라 멈춤마다 +1 → 클러스터링/별자리 재계산 트리거.
- `constellationEnabled` : 별자리 라인 토글(우하단 FAB).
- `warpState : DiaryOpenWarpData?` : 열람/업로드 파장 연출 상태(재생 중이면 입력 차단 오버레이).
- `mapBoundsInRoot` / `createFabCenterInRoot` / `createFabScale` : 업로드 버튼 파장 중심 좌표/바운스.
- `mergeGroupsState` : 30m 머지 그룹(대표 id → 우선순위 정렬 멤버들) — 클릭 리스너가 최신값 참조.
- `didAutoCenter` : 최초 실행 시 "실제 fix 도착하면 내 위치로 1회 이동" 완료 여부.
- `lastRecenterNonce` : `MapUiState.recenterNonce` 소비 기록 — 지도 복귀 재센터 1회 실행
  (초기값=현재 nonce 라 액티비티 재생성 후 남은 옛 요청은 무시).

### 주요 함수/로직
- `recenterToMyLocation()` : 내 위치로 `DEFAULT_ZOOM`+`BASE_TILT_DEG` 카메라 애니메이션.
  내위치 FAB·글로브 복귀·지도 복귀 재센터가 공용.
- `requestRoute(lat, lng)` : `OrsRouting.walkingRoute` 호출 → `savedRoute` 저장 + "N분 · Nm" 토스트.
- `onWarpFinished(wd)` : 파장 종료 분기 — 업로드(`openCreate`)→작성 화면 / 별 탭(`navigateAfter`)→상세
  또는 겹친별 카드 / 알림 포커스→`onFocusHandled()`(지도에 머묾).
- 지도 클릭 리스너(1회 등록): 개척 비콘 탭→퀘스트 토스트, 별 탭→**100m 게이팅**
  (fix 없으면 "위치 확인 중" 토스트 / 밖이면 거리 토스트 / 안이면 효과음+스냅샷 → `warpState` 파장 → 상세).
- 스타일 초기화(`map.setStyle`): 레이어 순서(아래→위) =
  **별가루 파티클 → 별자리 3겹 → 경로 3겹 → 바닥광 → 오오라 → 위성 → 별 → 스파클 → 개척 비콘 → 내 위치**.
  초기 카메라 = 지난 세션 마지막 카메라(`LocationHelper.lastCameraState`), 없으면 시작 좌표.
- 클러스터링 `LaunchedEffect(diaries, styleRef, currentLatLng, cameraIdleTick)` :
  90ms 디바운스 → ① `mergeByProximity`(30m 지오 머지) → ② `clusterTopLiked`(화면 픽셀 클러스터)
  → 대표만 렌더. 배정표(id→대표) 전이를 320ms 보간(위치+투명도)해 합쳐짐/펼쳐짐이 부드럽다.
  이후 `settleOrbits()` 로 위성(멤버 미니 별) 페이드 인.
- 별자리 `LaunchedEffect` : 켜져 있을 때만 화면에 보이는 별들로 최근접 `CONSTELLATION_NEIGHBORS`(2) 연결.
  구성 바뀌면 짧게 페이드 아웃 → 새 구성 페이드 인.
- **마커 애니메이션 루프(20fps, `delay(50)`)** : float 부유(sin, ±4dp) + pulse(1.0~1.2) + 바닥광 밝기 +
  스파클 궤도/게이트 + 위성 흔들림 + 파티클 트윙클 + 도로 글린트(대시 위상).
  쉬는 조건: 카메라 이동 중 / 앱 후면(`AppForeground`) / **지도 가려짐(`MapUiState.mapVisible=false`)** /
  별 없음+파티클 숨김 줌.
- 포커스 `LaunchedEffect(focusDiary, mapRef)` : 대상 좌표로 800ms 카메라 → onFinish 에서 스냅샷 파장
  (`navigateAfter=false` — 지도에 머묾) + `withRoute` 면 `requestRoute` 로 도보 경로.
- 경로 렌더 `LaunchedEffect(currentLatLng, savedRoute, routeSource)` :
  전체 경로에서 **내 위치 최근접점→목적지 구간만**(`partialRouteFrom`) 그린다(지나온 길 숨김).
- 오버레이 Canvas: 세계 밖 빈 공간 바다색 덮기 + 상시 비네트 + 저줌 대기 헤이즈(터치 통과).
- 우하단 FAB 열: 내 위치 → 별자리 토글 → 몰입(지도만 보기) → 업로드(+, 파장 후 작성 화면).
  좌상단: 줌 +/−. 길찾기 활성 시 하단 중앙 X(경로 취소).

## DiaryMapMarkers.kt — 상수·표현식·비트맵 헬퍼 (지도 튜닝은 대부분 여기)

### 자주 만지는 상수
- `DEFAULT_ZOOM = 15.0` : 내위치/포커스 이동 줌.
- `CLUSTER_RADIUS_DP = 4` : 화면상 이 거리 안이면 좋아요 1위 별로 합쳐 표시.
- `LIKES_FOR_MAX_SIZE = 100`, `MAX_LIKE_SIZE_MULT = 3` : 좋아요 → 별 크기 배율(100개에서 3배 상한).
- `PHASE_GROUPS = 4` : 부유 위상 그룹 수(id 해시로 분배 — "따로따로" 떠다니게).
- `PARTICLE_COUNT = 400` / `PARTICLE_RADIUS_M = 20km` / `PARTICLE_SEED = 42` : 별가루 배치(시드 고정).
- `MARKER_SIDE_PX = 160` : 별 마커 비트맵 변(4의 배수 유지 — GL 행 정렬).
- `STAR_SIZE_FAR = 0.65` / `STAR_SIZE_NEAR = 0.9` : 100m 밖/안 별 iconSize 기준.
- `GLOBE_BUTTON_ZOOM = 3.0` / `MAP_MIN_ZOOM = 2.4` / `HAZE_START_ZOOM = 4.4` / `BASE_TILT_DEG = 25.0`.
- `GROUND_LIGHT_OPACITY = 0.30` / `GROUND_LIGHT_OFFSET_Y = 8` : 바닥 빛 웅덩이.
- 별자리: `CONSTELLATION_NEIGHBORS = 2`, 불투명도 3겹 = 0.18/0.42/0.95 (경로 레이어도 같은 값).
- 스파클: `SPARKLE_SETS = 3`(안쪽/바깥/위성), `sparkleSetMinSize`(세트 등장 최소 크기 — 1.6/2.6),
  `sparkleSizeBase`(0.90/0.68/0.42), `SPARKLE_SIZE_POW = 0.8`, `SPARKLE_BIG_STAR_THRESHOLD = 1.75`
  (이상이면 흰 4꼭지 대신 그 별을 닮은 미니 크리스탈), `orbitTargetDp`(5dp+3.2·ln / 7.5dp+4.6·ln).
- 겹친 별 위성: `MAX_ORBIT_STARS = 4`, `ORBIT_STAR_BASE_SIZE = 0.76`, `ORBIT_ANCHOR_ANGLES`(비대칭 배치각).
- 도로 글린트: `ROAD_GLINT_DASH/GAP/SPEED/STEPS` — ⚠️ dash/gap 은 `maplibre_style.json` 의
  line-dasharray 와 일치해야 한다.

### 주요 함수
- `starBitmap(type, colorIdx)` : 별 마커 비트맵(글로우+크리스탈 본체). ⚠️ PNG 디코드 경로는 에뮬레이터에서
  깨져 **Path 렌더 유지**.
- `starSizeExpression(pulse)` : 줌 interpolate × near/far × sizeMult. ⚠️ 분기는 각 zoom stop "안"에.
- `auraRadiusExpression` / `groundLightRadiusExpression` / `auraOpacityExpression` : 후광 수치.
- `mergeByProximity(valid)` : 30m 지오 머지(줌 무관). `MERGE_PRIORITY` = 좋아요↓ → 오래된 순 → id.
- `clusterTopLiked(map, reps, radiusPx)` : 화면 좌표 클러스터링(카메라 idle 마다).
- `diaryFeature(...)` : 별 1개의 GeoJSON Feature(icon/alpha/sizeMult/near/phaseGroup/sparkleIcon/auraColor).
- `sparkleOffsetExpression` / `orbitOffsetExpression` : dp → 스프라이트 px 환산(밀도 무관 배치의 핵심).
- `buildConstellationFeatures` : 뷰포트 별 최근접 연결 라인 생성.
- `rememberMapViewWithLifecycle()` : ⚠️ `MapLibre.getInstance` 는 MapView 생성 전 1회 필수.
- `partialRouteFrom(full, me)` : 경로에서 내 최근접 투영점 이후만 잘라 반환.

## DiaryOpenWarp.kt — 열람 파장(왜곡) 연출
- `DiaryOpenWarpData(bitmap, ox, oy, id, colorIndex, navigateAfter, openCreate, burstStars, clusterIds)` :
  지도 스냅샷 + 파장 시작점(0..1) + 종료 후 분기 정보. `burstStars` 는 겹친 별 멤버 파티클.
- `DiaryOpenWarp(data, onFinished)` : 스냅샷을 약 1.3초 파장+울렁(메시 왜곡)시키고 링을 퍼뜨린 뒤
  `onFinished` — 상세 진입 자체는 왜곡 없이 깔끔하게.

## MapOnlyOverlay.kt — 몰입(지도만 보기) 종료 오버레이
- `poke` / `xVisible` : X 버튼 재표시 트리거 / 표시 여부(3초 자동 숨김).
- 하단 중앙 64dp 탭 영역만 터치를 가져가고 나머지는 지도 조작 그대로. 뒤로가기=X 재표시(이탈 방지).

## OrsRouting.kt — 도보 길찾기 API
- `isConfigured` : `ORS_API_KEY`(secrets.properties) 주입 여부.
- `walkingRoute(startLat, startLng, endLat, endLng): Route?` : OpenRouteService GET 1회.
  `Route(coordinates=[[lng,lat],...], distanceM, durationS)`. 실패/미설정 시 null(조용히 무시).
- provider 교체 시 이 object 만 바꾸면 된다(호출부는 좌표 list 만 사용).

## iOS 대응

### MapScreen.swift (= MainListScreen + DiaryMap 의 화면 로직)
- 상태: `selected`(상세 push) / `cluster`(겹친별 push) / 필터 상태 일체(unviewedOnly·friendsOnly·
  myOnly·selectedFriendIds·periodDays·speedDialExpanded — Android 와 동일 상호배타) /
  `zoomRequest`·`recenterNonce`(MapLibreView 커맨드 채널) / `rootAppearedOnce`(복귀 감지) /
  `constellationOn` / `focusTarget`·`fullRoute`·`routeSummary`(길찾기) / `showWarp`·`warpColor`·`warpId`
  (포커스 파동) / `openWarp`(열람 파장)·`toast` / `voidTopY·voidBottomY·voidZoom`(세계 밖 빈 공간) /
  `globeCenter`·`globeReturn`·`globeButtonCenter`·`globeScrim`(글로브).
- `shownDiaries` : Android `filteredDiaries` 대응 필터 파이프라인.
- `handleStarTap(members, origin, snapshot)` : 100m 게이팅 → 파장(`DiaryOpenWarpData`) → 상세/카드.
- `handleFocus(id)` : 포커스 요청 처리 — 카메라 이동 + 파동(MapWarpOverlay) + (withRoute 시 650ms 후)
  `fetchRoute`. 끝나면 `focus.consume()`.
- `.onAppear` : **하위 화면 → 루트 복귀 감지 지점.** `rootAppearedOnce && pendingDiaryId == nil &&
  fullRoute.isEmpty` 면 `recenterNonce += 1`(카메라만 내 위치로 — Android 재센터 패리티).
- `partialRouteFrom` : Android 동일 공식(static).

### MapLibreView.swift (UIViewRepresentable)
- 입력 프로퍼티가 곧 커맨드 채널: `diaries`/`userLocation`/`onTapStar`/`route`/`focusTarget`/
  `onGlobeAvailability`/`globeReturnCamera`/`pioneerCountries`/`onTapPioneer`/`zoomRequest`/
  `recenterNonce`/`constellationEnabled`/`onWorldVoid`.
- 상수: `globeButtonZoom=3.0`, `mapMinZoom=2.4`, `baseTiltDeg=25` (**Android 값과 동일 유지**).
- `staryStyleURL` : 번들의 `maplibre_style.json` 에서 `__MAPTILER_KEY__` 를 Info.plist `MAPTILER_KEY`
  (project.yml 주입)로 치환한 임시 URL. 키 없으면 demotiles 폴백.
- `Coordinator(MLNMapViewDelegate)` : 마커 어노테이션 관리 —
  `DiaryAnnotation`(대표+members+sizeMult, `markerSize` 40~100pt 0.25 단위 양자화, `imageKey` 공유),
  `MergedStarAnnotationView`(대표+위성 함께 float — CABasicAnimation 벽시계 위상),
  `SingleStarAnnotationView`(`scalesWithViewingDistance` 로 줌아웃 시 축소).
- `recenterNonce`/`zoomRequest` 는 `lastRecenterNonce` 등과 비교해 1회 소비(Android 와 같은 패턴).

### MapStyleEffects.swift (Coordinator extension)
- `StyleFx` enum = **Android DiaryMapMarkers 상수 패리티 묶음**: particleCount 400/20km/시드 42,
  phaseGroups 4, 별자리 불투명도 0.18/0.42/0.95·이웃 2, 경로 레이어 3겹 id, 바닥광 0.30/+8.
- `didFinishLoading` : 파티클/별자리/후광/경로 소스·레이어 1회 설치 + 트윙클 루프 시작.
- `startTwinkleLoop()` : 20fps, 그룹별 speed 2.0+0.4g — Android 루프와 동일 공식.
- `refreshAuraFeatures` : 대표 별 좌표+sizeMult+별색으로 후광(바닥광+오오라 2겹) 갱신.
- `setConstellation`/`requestConstellationRebuild` : 토글 페이드/90ms 디바운스 재계산(Android 동일).

### StarMerge.swift / StarImageRenderer.swift / DiaryOpenWarpView.swift
- `StarMerge` : 30m 지오 머지 + 우선순위(좋아요↓→오래된 순) — Android `mergeByProximity` 패리티.
- `StarImageRenderer` : 별 마커 UIImage 렌더/캐시(크리스탈 채움) — Android `starBitmap` 대응.
- `DiaryOpenWarpView` : 지도 스냅샷 CIBumpDistortion 굴절 + 파장 링 + 겹친별 버스트(1.3s) —
  Android `DiaryOpenWarp` 대응.

### 값 조절(패리티 매핑) — 지도에서 수치를 바꿀 때
| 항목 | Android | iOS |
|---|---|---|
| 열람 반경 100m | shared `StaryConfig.DIARY_OPEN_RADIUS_M` | `AppConfig.diaryOpenRadiusM` |
| 기본 줌/틸트/글로브 버튼 줌 | `DiaryMapMarkers` DEFAULT_ZOOM·BASE_TILT_DEG·GLOBE_BUTTON_ZOOM | `MapLibreView` 카메라 코드·baseTiltDeg·globeButtonZoom |
| 파티클 수/반경/시드 | PARTICLE_COUNT/RADIUS_M/SEED | `StyleFx.particleCount/RadiusM/Seed` |
| 별자리 선 색/불투명도/이웃 수 | CONSTELLATION_* 상수 | `StyleFx.constellation*` |
| 바닥광/오오라 | GROUND_LIGHT_*·aura*Expression | `StyleFx.groundLight*`·refreshAuraFeatures |
| 별 크기(좋아요/근접/줌) | likeSizeMult·starSizeExpression·STAR_SIZE_* | `DiaryAnnotation.markerSize`·어노테이션 transform(줌 보간 8→0.3/12→0.55/15→1.0) |
| 100m 게이팅 토스트 문구 | `strings.xml` map_waiting_fix/map_open_range | `L10n` mapWaitingFix/mapOpenRange |
| 길찾기 API | `OrsRouting.kt`(ORS_API_KEY, secrets.properties) | `OrsRouting.swift`(동일 계약) |
| 야경 스타일 | `res/raw/maplibre_style.json` | iOS 번들 동일 파일 + Info.plist MAPTILER_KEY |
| 복귀 재센터 예외 조건 | `MainScreen`(pendingDiaryId·routeActive) | `MapScreen.onAppear`(pendingDiaryId·fullRoute) |
