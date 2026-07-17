package com.chaminwoo.stary.feature.map.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.raisedCosmicBorder
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.feature.map.OrsRouting
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.sin
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

/** 외부(알림 등)에서 "이 다이어리로 카메라 이동 + 파장" 을 요청할 때 전달하는 대상. */
data class DiaryFocusTarget(
    val lat: Double,
    val lng: Double,
    val colorIndex: Int,
    val diaryId: String,
    val withRoute: Boolean = false,
)

/** 글로브에서 지도로 복귀할 때의 카메라 요청(nonce 로 같은 좌표 반복 요청도 트리거). */
data class GlobeReturnCamera(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val nonce: Long,
)

/**
 * MapLibre GL Native + MapTiler 벡터 타일 기반 밤하늘 지도.
 *
 * - 별 마커: GeoJSON + SymbolLayer(30m 지오 머지 → 화면 클러스터링 → 대표만 렌더).
 * - 100m 이내 별은 near 확대 + pulse, 전체 별은 float(부유) — [DiaryMapMarkers.kt] 지원 함수 참조.
 * - 별 탭: 100m 이내면 파장 연출 후 상세(겹친 별이면 카드 뷰어), 밖이면 거리 토스트.
 */
@Composable
fun DiaryMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    onDiaryClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 30m 안에서 합쳐진 별(2개 이상) 열람 — 파장 후 카드 스와이프 화면으로. 우선순위 정렬된 id 목록 전달. */
    onClusterClick: (List<String>) -> Unit = {},
    focusDiary: DiaryFocusTarget? = null,
    onFocusHandled: () -> Unit = {},
    showCreate: Boolean = true, // 비로그인 시 다이어리 생성(업로드) 버튼 숨김
    /**
     * 줌이 [GLOBE_BUTTON_ZOOM] 이하면 (중심 위경도, true), 위로 올라오면 (_, _, false) 로 보고.
     * 호출부는 이 값으로 하단 "지구 보기" 버튼을 노출/숨김(자동 전환 없음 — 버튼으로만 진입).
     */
    onGlobeAvailability: ((lat: Double, lng: Double, available: Boolean) -> Unit)? = null,
    /** 글로브 → 지도 복귀 카메라 요청. */
    globeReturnCamera: GlobeReturnCamera? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()

    val styleJson = remember {
        context.resources.openRawResource(R.raw.maplibre_style)
            .bufferedReader().use { it.readText() }
            .replace("__MAPTILER_KEY__", BuildConfig.MAPTILER_KEY)
    }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var locationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var diarySource by remember { mutableStateOf<GeoJsonSource?>(null) }
    // 겹친 별 위성(멤버 별 부유) 소스 + 등장 페이드(머지 전환이 끝난 뒤 부드럽게 나타난다).
    var orbitSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    val orbitFade = remember { Animatable(0f) }
    var constellationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var routeSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var pioneerSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    val routeScope = rememberCoroutineScope()
    // 도보 길찾기: 처음 받은 전체 경로(목적지까지)를 저장. null = 길찾기 비활성(X 취소 시).
    var savedRoute by remember { mutableStateOf<List<Point>?>(null) }
    val addedIcons = remember { mutableSetOf<String>() }
    val isCameraMoving = remember { mutableStateOf(false) }
    // 저줌 대기 헤이즈 강도(0..1) — 줌이 HAZE_START_ZOOM 밑으로 내려갈수록 1에 접근.
    val hazeAlpha = remember { mutableStateOf(0f) }
    // 카메라가 멈출 때마다 증가 → 줌/이동에 따라 화면 클러스터링 재계산 트리거
    var cameraIdleTick by remember { mutableStateOf(0) }
    val clusterRadiusPx = remember { CLUSTER_RADIUS_DP * context.resources.displayMetrics.density }
    val screenDensity = remember { context.resources.displayMetrics.density }
    // 별자리 최대 연결 거리(px) — 화면 짧은 변의 절반 정도까지만 이어 과한 장거리 연결 방지
    val constellationMaxLinkPx = remember {
        context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) * 0.55f }
    }
    var constellationEnabled by remember { mutableStateOf(false) }

    // 다이어리 진입 직전 "지도 왜곡" 연출 — 스냅샷을 파장+울렁시킨 뒤 상세로([DiaryOpenWarp]).
    val warpState = remember { mutableStateOf<DiaryOpenWarpData?>(null) }
    // 업로드 버튼 탭 연출용 — 파장 중심을 버튼 위치로 잡기 위한 좌표(루트 기준).
    var mapBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var createFabCenterInRoot by remember { mutableStateOf<Offset?>(null) }

    val onDiaryClickRef = rememberUpdatedState(onDiaryClick)
    val onClusterClickRef = rememberUpdatedState(onClusterClick)
    val onFocusHandledRef = rememberUpdatedState(onFocusHandled)
    // 30m 머지 그룹(대표 id → 우선순위 정렬된 멤버들) — 클릭 리스너(1회 등록)에서 최신값 참조.
    val mergeGroupsState = remember { mutableStateOf<Map<String, List<Diary>>>(emptyMap()) }
    val currentLatLngRef = rememberUpdatedState(currentLatLng)
    val diariesRef = rememberUpdatedState(diaries)
    val initialLatLngRef = rememberUpdatedState(currentLatLng)
    val onGlobeAvailabilityRef = rememberUpdatedState(onGlobeAvailability)

    // "내 위치로" 카메라 이동 — FAB 과 글로브 복귀에서 공용(동일 로직 1회 실행).
    val recenterToMyLocation: () -> Unit = {
        mapRef?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(currentLatLngRef.value.toMl()).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            )
        )
    }

    // 글로브 → 지도 복귀 시 "내 위치로" 카메라 이동 1회(nonce 로 같은 복귀도 매번 트리거).
    LaunchedEffect(globeReturnCamera) {
        globeReturnCamera ?: return@LaunchedEffect
        recenterToMyLocation()
    }

    // 도보 길찾기 — 전체 경로를 savedRoute 에 저장(X 취소까지 유지). ORS 실패 시 조용히 무시.
    val requestRoute: (Double, Double) -> Unit = { destLat, destLng ->
        val cur = currentLatLngRef.value
        routeScope.launch {
            val route = OrsRouting.walkingRoute(cur.latitude, cur.longitude, destLat, destLng) ?: return@launch
            savedRoute = route.coordinates.map { Point.fromLngLat(it[0], it[1]) }
            val mins = (route.durationS / 60.0).roundToInt().coerceAtLeast(1)
            com.chaminwoo.stary.core.ui.StaryToast.show(
                context.getString(R.string.map_route_summary, mins, route.distanceM.roundToInt())
            )
        }
    }
    val requestRouteRef = rememberUpdatedState(requestRoute)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { mapBoundsInRoot = it.boundsInRoot() }
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        ) { mv ->
            mv.getMapAsync { map ->
                if (mapRef != null) return@getMapAsync
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }
                map.setMinZoomPreference(MAP_MIN_ZOOM) // 이 밑은 3D 글로브가 담당
                map.addOnCameraMoveStartedListener { isCameraMoving.value = true }
                map.addOnCameraIdleListener {
                    isCameraMoving.value = false
                    cameraIdleTick++
                    // 마지막 카메라(중심+줌) 저장 — 다음 실행의 초기 카메라가 "마지막으로 보던 곳"이 되게.
                    map.cameraPosition.target?.let { c ->
                        LocationHelper.persistCameraState(
                            context, c.latitude, c.longitude, map.cameraPosition.zoom
                        )
                    }
                }
                // 줌 상태 보고 → 호출부가 하단 "지구 보기" 버튼 노출을 결정(자동 전환 없음)
                map.addOnCameraMoveListener {
                    val z = map.cameraPosition.zoom
                    // 대기 헤이즈: 알파가 실제로 변할 때만 state 갱신(팬 중 불필요한 리컴포지션 방지)
                    val a = ((HAZE_START_ZOOM - z) / (HAZE_START_ZOOM - MAP_MIN_ZOOM))
                        .toFloat().coerceIn(0f, 1f)
                    if (a != hazeAlpha.value) hazeAlpha.value = a
                    val cb = onGlobeAvailabilityRef.value ?: return@addOnCameraMoveListener
                    val c = map.cameraPosition.target
                    cb(c?.latitude ?: 0.0, c?.longitude ?: 0.0, z <= GLOBE_BUTTON_ZOOM)
                }
                // 별 탭 → 100m 게이팅(안이면 파장+상세, 밖이면 거리 토스트)
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val pioneer = map.queryRenderedFeatures(screen, PIONEER_LAYER).firstOrNull()
                    if (pioneer != null) {
                        val code = pioneer.getStringProperty("code") ?: ""
                        val country = com.chaminwoo.stary.core.util.LocalizedNames.countryName(code)
                        val (days, hours) = com.chaminwoo.stary.shared.config.PioneerQuest
                            .daysHoursUntilCountryChange(System.currentTimeMillis())
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            context.getString(R.string.pioneer_quest_toast, country, days, hours)
                        )
                        return@addOnMapClickListener true
                    }
                    val features = map.queryRenderedFeatures(screen, *DIARY_LAYER_IDS)
                    val id = features.firstOrNull()?.getStringProperty("id")
                    if (id != null) {
                        val diary = diariesRef.value.firstOrNull { it.id == id }
                        if (diary != null) {
                            // ⚠️ 실제 위치 fix 전에는 열람 불가 — 폴백 좌표로 100m 판정하면 우회 가능.
                            if (LocationHelper.getCurrentLatLng() == null) {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(R.string.map_waiting_fix)
                                )
                                return@addOnMapClickListener true
                            }
                            val cur = currentLatLngRef.value
                            val distance = LocationHelper.distanceBetween(
                                cur.latitude, cur.longitude, diary.latitude, diary.longitude
                            )
                            if (distance <= StaryConfig.DIARY_OPEN_RADIUS_M) {
                                // 파장 중심 = 이 별의 화면상 위치(0..1)
                                val sp = map.projection.toScreenLocation(
                                    MlLatLng(diary.latitude, diary.longitude)
                                )
                                val w = mv.width.toFloat().coerceAtLeast(1f)
                                val h = mv.height.toFloat().coerceAtLeast(1f)
                                val ox = (sp.x / w).coerceIn(0f, 1f)
                                val oy = (sp.y / h).coerceIn(0f, 1f)
                                // 머지 그룹이 2개 이상이면 파장에 멤버 별 파티클을 얹고 카드 뷰어로.
                                val group = mergeGroupsState.value[id] ?: listOf(diary)
                                map.snapshot { bmp ->
                                    com.chaminwoo.stary.core.util.MusicManager.playOpenDiary()
                                    warpState.value = DiaryOpenWarpData(
                                        bmp, ox, oy, id, diary.starColor, navigateAfter = true,
                                        burstStars = if (group.size > 1) {
                                            group.take(12).map { it.starType to it.starColor }
                                        } else emptyList(),
                                        clusterIds = if (group.size > 1) group.map { it.id } else emptyList(),
                                    )
                                }
                            } else {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(
                                        R.string.map_open_range,
                                        StaryConfig.DIARY_OPEN_RADIUS_M.toInt(),
                                        distance.toInt()
                                    )
                                )
                            }
                        }
                        true
                    } else false
                }
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    val start = initialLatLngRef.value
                    // ── 레이어 순서(아래→위): 파티클 → 별자리 → 경로 → 바닥광 → 오오라 → 위성 → 별 → 스파클 ──

                    // 별가루 파티클 — 생성 후 갱신 없음(반짝임은 레이어 opacity 만 루프에서 갱신).
                    style.addImage(PARTICLE_ICON_ID, particleBitmap())
                    style.addSource(
                        GeoJsonSource(STAR_PARTICLE_SOURCE, starParticleFeatures(start))
                    )
                    for (g in 0 until PHASE_GROUPS) {
                        val layer = SymbolLayer(particleLayerId(g), STAR_PARTICLE_SOURCE)
                            .withProperties(
                                PropertyFactory.iconImage(PARTICLE_ICON_ID),
                                PropertyFactory.iconSize(particleSizeExpression()),
                                PropertyFactory.iconOpacity(particleOpacityExpression(1f)),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                            )
                        layer.setFilter(
                            Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                        )
                        style.addLayer(layer)
                    }

                    // 별자리 라인 — 후광/글로우/밝은 선 3겹, 초기 불투명도 0(토글 시 페이드).
                    val cSrc = GeoJsonSource(CONSTELLATION_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(cSrc)
                    style.addLayer(
                        LineLayer(CONSTELLATION_HALO_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#6EE7B7"),
                            PropertyFactory.lineWidth(16f),
                            PropertyFactory.lineBlur(16f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addLayer(
                        LineLayer(CONSTELLATION_GLOW_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#6EE7B7"),
                            PropertyFactory.lineWidth(8f),
                            PropertyFactory.lineBlur(8f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addLayer(
                        LineLayer(CONSTELLATION_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#E6FFF4"),
                            PropertyFactory.lineWidth(1.7f),
                            PropertyFactory.lineBlur(0.6f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    constellationSource = cSrc

                    // 도보 길찾기 경로 — 초기 빈 상태, 길찾기 요청 시 채워진다.
                    val rSrc = GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(rSrc)
                    style.addLayer(
                        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                            PropertyFactory.lineColor("#86EFAC"),   // 연한 초록 실선(후광 없음)
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineOpacity(0.95f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    routeSource = rSrc

                    // 다이어리 별 마커 source — 클러스터링은 클라이언트(화면 좌표)에서 처리.
                    val dSrc = GeoJsonSource(DIARY_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(dSrc)

                    // 바닥 빛 웅덩이 — 지점 고정 광(부유하는 별과의 시차로 "떠 있음"이 읽힌다).
                    for (g in 0 until PHASE_GROUPS) {
                        val light = CircleLayer(groundLightLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.circleColor(Expression.toColor(Expression.get("auraColor"))),
                            PropertyFactory.circleRadius(groundLightRadiusExpression()),
                            PropertyFactory.circleOpacity(
                                Expression.product(
                                    Expression.literal(GROUND_LIGHT_OPACITY), Expression.get("alpha")
                                )
                            ),
                            PropertyFactory.circleBlur(1.4f),
                            PropertyFactory.circleTranslate(arrayOf(0f, GROUND_LIGHT_OFFSET_Y)),
                        )
                        light.setFilter(Expression.eq(Expression.get("phaseGroup"), Expression.literal(g)))
                        style.addLayer(light)
                    }

                    // 오오라(인기 별 글로우) — 위상 그룹별로 나눠 별과 같이 float.
                    for (g in 0 until PHASE_GROUPS) {
                        val aura = CircleLayer(auraLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.circleColor(Expression.toColor(Expression.get("auraColor"))),
                            PropertyFactory.circleRadius(auraRadiusExpression()),
                            PropertyFactory.circleOpacity(
                                Expression.product(auraOpacityExpression(), Expression.get("alpha"))
                            ),
                            PropertyFactory.circleBlur(1f),
                        )
                        aura.setFilter(Expression.eq(Expression.get("phaseGroup"), Expression.literal(g)))
                        style.addLayer(aura)
                    }

                    // 겹친 별 위성 — 대표 곁에 둥둥 떠 있는 머지 멤버 미니 크리스탈(별 아래에 깔린다).
                    // satIndex × phaseGroup 레이어 — phaseGroup 이 대표 별과 같아야 같은 iconTranslate(float)를 공유한다.
                    val oSrc = GeoJsonSource(ORBIT_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(oSrc)
                    for (i in 0 until MAX_ORBIT_STARS) {
                        for (g in 0 until PHASE_GROUPS) {
                            val layer = SymbolLayer(orbitLayerId(i, g), ORBIT_SOURCE).withProperties(
                                PropertyFactory.iconImage(Expression.get("icon")),
                                PropertyFactory.iconSize(orbitSizeExpression()),
                                PropertyFactory.iconOpacity(0f),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                            )
                            layer.setFilter(
                                Expression.all(
                                    Expression.eq(Expression.get("satIndex"), Expression.literal(i)),
                                    Expression.eq(Expression.get("phaseGroup"), Expression.literal(g)),
                                )
                            )
                            style.addLayer(layer)
                        }
                    }
                    orbitSource = oSrc

                    // 별 마커 — 위상 그룹별 레이어(같은 source, phaseGroup 필터). iconOpacity = alpha(합쳐짐 보간).
                    for (g in 0 until PHASE_GROUPS) {
                        val layer = SymbolLayer(diaryLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(starSizeExpression(1f)),
                            PropertyFactory.iconOpacity(Expression.get("alpha")),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                        layer.setFilter(
                            Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                        )
                        style.addLayer(layer)
                    }

                    // 별 곁 마이크로 스파클 — 크기에 따라 1개 → 2개 → +위성(궤도/부유/게이트는 루프에서 갱신).
                    // 큰 별 곁에선 미니 크리스탈(sparkleIcon)로 전환하되 위성(set 2)은 항상 흰 4꼭지.
                    style.addImage(SPARKLE_ICON_ID, sparkleBitmap())
                    val sparkleIconExpression = Expression.switchCase(
                        Expression.gte(Expression.get("sizeMult"), Expression.literal(SPARKLE_BIG_STAR_THRESHOLD)),
                        Expression.get("sparkleIcon"),
                        Expression.literal(SPARKLE_ICON_ID)
                    )
                    for (s in 0 until SPARKLE_SETS) {
                        for (g in 0 until PHASE_GROUPS) {
                            val layer = SymbolLayer(sparkleLayerId(s, g), DIARY_SOURCE).withProperties(
                                PropertyFactory.iconImage(
                                    if (s == SPARKLE_SATELLITE_SET) Expression.literal(SPARKLE_ICON_ID)
                                    else sparkleIconExpression
                                ),
                                PropertyFactory.iconSize(sparkleSizeExpression(s)),
                                PropertyFactory.iconOpacity(0f),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                            )
                            layer.setFilter(
                                Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                            )
                            style.addLayer(layer)
                        }
                    }

                    diarySource = dSrc

                    // 개척 퀘스트 비콘 — 미개척 대상국 중심좌표에 금색 링+스파클.
                    // ⚠️ 이 스타일 JSON 에는 glyphs 엔드포인트가 없어 textField 를 쓰면 심볼이 아예
                    // 렌더되지 않는다 → 아이콘 전용 유지(나라 이름은 탭 토스트).
                    style.addImage(PIONEER_ICON_ID, pioneerBeaconBitmap())
                    val pSrc = GeoJsonSource(PIONEER_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(pSrc)
                    style.addLayer(
                        SymbolLayer(PIONEER_LAYER, PIONEER_SOURCE).withProperties(
                            PropertyFactory.iconImage(PIONEER_ICON_ID),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(2f, 0.55f),
                                    Expression.stop(8f, 0.8f),
                                    Expression.stop(14f, 1.0f),
                                )
                            ),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                    )
                    pioneerSource = pSrc

                    // 내 위치 마커 (별 위에 표시)
                    val src = GeoJsonSource(
                        CURRENT_SOURCE,
                        Point.fromLngLat(start.longitude, start.latitude)
                    )
                    style.addSource(src)
                    style.addLayer(
                        CircleLayer("current-location-layer", CURRENT_SOURCE).withProperties(
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor(AndroidColor.parseColor("#6EE7B7")),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor(AndroidColor.parseColor("#FFFFFF")),
                        )
                    )
                    locationSource = src

                    val target = LocationHelper.cameraTarget
                    if (target == null) {
                        // 초기 카메라 = 지난 세션에서 마지막으로 보던 카메라(없으면 마지막 위치/기본좌표).
                        val savedCam = LocationHelper.lastCameraState(context)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(savedCam?.let { MlLatLng(it.latitude, it.longitude) } ?: start.toMl())
                            .zoom(savedCam?.zoom ?: DEFAULT_ZOOM)
                            .tilt(BASE_TILT_DEG)
                            .build()
                    } else {
                        LocationHelper.cameraTarget = null
                        val bounds = LatLngBounds.Builder()
                            .include(start.toMl())
                            .include(MlLatLng(target.latitude, target.longitude))
                            .build()
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                        map.cameraPosition = CameraPosition.Builder(map.cameraPosition)
                            .tilt(BASE_TILT_DEG).build()
                    }
                    styleRef = style
                    mapRef = map
                }
            }
        }

        // 비네트 + 저줌 대기 헤이즈 — 그리기 전용(터치는 지도로 통과), FAB/워프 오버레이보다 아래.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = hypot(size.width, size.height) / 2f
            if (r <= 0f) return@Canvas
            // 1) 상시 비네트: 가장자리를 살짝 어둡게 눌러 화면에 깊이감을 준다
            drawRect(
                brush = Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.30f),
                    center = center,
                    radius = r,
                )
            )
            // 2) 글로브 근접 대기 헤이즈: 줌아웃할수록 파란 대기가 차올라 글로브 전환과 이어진다
            val a = hazeAlpha.value
            if (a > 0f) {
                drawRect(Color(0xFF060E1C).copy(alpha = 0.35f * a))
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to Color(0xFF3E7CC4).copy(alpha = 0.10f * a),
                        1.0f to Color(0xFF9BD1FF).copy(alpha = 0.22f * a),
                        center = center,
                        radius = r,
                    )
                )
            }
        }

        // 지도만 보기 모드에선 모든 버튼(좌상단 줌 + 우하단 FAB)을 숨긴다.
        if (!MapUiState.mapOnly) {
        // 좌상단 줌 버튼 (+/-) — 버튼 1탭당 한 단계, 부드럽게 애니메이션 줌.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = { mapRef?.animateCamera(CameraUpdateFactory.zoomBy(1.0), 220) },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(44.dp).raisedCosmicBorder()
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.cd_zoom_in), tint = Color.White, modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = { mapRef?.animateCamera(CameraUpdateFactory.zoomBy(-1.0), 220) },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(44.dp).raisedCosmicBorder()
            ) {
                Icon(Icons.Filled.Remove, stringResource(R.string.cd_zoom_out), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 내 위치로 이동
            FloatingActionButton(
                onClick = { recenterToMyLocation() },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp).raisedCosmicBorder()
            ) {
                Icon(Icons.Filled.Navigation, stringResource(R.string.cd_my_location), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // 별자리 토글
            FloatingActionButton(
                onClick = { constellationEnabled = !constellationEnabled },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp).raisedCosmicBorder()
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    stringResource(R.string.map_constellation),
                    tint = if (constellationEnabled) Color(0xFF6EE7B7) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 몰입(지도만 보기) — 탑바/필터/버튼을 모두 숨기고 지도에 집중
            FloatingActionButton(
                onClick = { MapUiState.enterMapOnly() },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp).raisedCosmicBorder()
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    stringResource(R.string.map_only),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 다이어리 생성 (로그인 상태에서만 노출) — 탭하면 별 열람과 같은 파장 연출 후 작성 화면으로.
            if (showCreate) {
                FloatingActionButton(
                    onClick = {
                        if (warpState.value != null) return@FloatingActionButton // 연출 중 중복 탭 무시
                        val map = mapRef
                        val bounds = mapBoundsInRoot
                        val fabCenter = createFabCenterInRoot
                        if (map != null && bounds != null && fabCenter != null &&
                            bounds.width > 0f && bounds.height > 0f
                        ) {
                            // 파장 중심 = 업로드 버튼 위치(0..1)
                            val ox = ((fabCenter.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
                            val oy = ((fabCenter.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
                            map.snapshot { bmp ->
                                com.chaminwoo.stary.core.util.MusicManager.playOpenDiary()
                                warpState.value = DiaryOpenWarpData(
                                    bmp, ox, oy, id = "", colorIndex = 13, // 코발트 — 남색 버튼과 동계열 파장
                                    navigateAfter = false, openCreate = true,
                                )
                            }
                        } else onCreateClick() // 지도 준비 전이면 연출 없이 바로 이동
                    },
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    containerColor = Color.Transparent,
                    modifier = Modifier.onGloballyPositioned {
                        createFabCenterInRoot = it.boundsInRoot().center
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF3A4676), Color(0xFF111936)),
                                    start = Offset(0f, 0f), end = Offset(80f, 80f)
                                ),
                                CircleShape
                            )
                            .raisedCosmicBorder(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, stringResource(R.string.cd_create_diary), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        } // if (!MapUiState.mapOnly)

        // 길찾기 취소 — 도보 경로 활성 시 하단 중앙 X 버튼(누르면 경로 제거).
        if (savedRoute != null) {
            FloatingActionButton(
                onClick = { savedRoute = null },
                shape = CircleShape,
                containerColor = Color(0xFF0E1520),
                contentColor = Color(0xFF86EFAC),
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .size(52.dp)
                    .raisedCosmicBorder()
            ) {
                Icon(Icons.Filled.Close, contentDescription = context.getString(R.string.map_route_cancel))
            }
        }

        // 지도 왜곡 연출 — 스냅샷 이미지를 1초간 파장+울렁시킨 뒤 세부 화면으로 이동(세부는 멀쩡).
        warpState.value?.let { wd ->
            DiaryOpenWarp(wd) {
                warpState.value = null
                when {
                    // 업로드 버튼 탭 → 파장 후 다이어리 작성 화면으로
                    wd.openCreate -> onCreateClick()
                    wd.navigateAfter -> {
                        // 별 탭(100m 이내) → 파장 후 세부 화면으로 (합쳐진 별이면 카드 뷰어로)
                        MapUiState.exitMapOnly()
                        if (wd.clusterIds.size > 1) onClusterClickRef.value(wd.clusterIds)
                        else onDiaryClickRef.value(wd.id)
                    }
                    // 알림 포커스 → 파장만 내고 지도에 머문다
                    else -> onFocusHandledRef.value()
                }
            }
        }
    }

    // 다이어리/현재위치/카메라 변경 → ① 30m 지오 머지 → ② 화면 클러스터링 → 마커 갱신.
    // 합쳐짐/펼쳐짐은 배정표(id → 대표 id) 전이를 위치+투명도로 보간해 부드럽게.
    var lastFeaturesKey by remember { mutableStateOf<Any?>(null) }
    var prevAssignment by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(diaries, styleRef, currentLatLng, cameraIdleTick) {
        val style = styleRef ?: return@LaunchedEffect
        val source = diarySource ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect

        // 디바운스 — 연속 idle 마다 O(n²) 클러스터링을 돌리지 않는다(재시작이 직전 대기를 취소).
        delay(90)

        val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        val merged = mergeByProximity(valid)
        mergeGroupsState.value = merged.groups
        val mergedReps = merged.reps
        val byId = mergedReps.associateBy { it.id }

        val result = clusterTopLiked(map, mergedReps, clusterRadiusPx)
        val reps = result.reps
        val assignment = result.assignment
        val nearIds = reps.filter { d ->
            LocationHelper.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude, d.latitude, d.longitude
            ) <= StaryConfig.DIARY_OPEN_RADIUS_M
        }.map { it.id }.toSet()
        val clusterCount = assignment.values.groupingBy { it }.eachCount()
        fun mergeMult(d: Diary): Float = mergeSizeMult(merged.groups[d.id] ?: listOf(d))
        fun repSizeMult(d: Diary): Float =
            mergeMult(d) * clusterSizeBoost(clusterCount[d.id] ?: 1)

        val key = reps.map { it.id } to nearIds
        if (key == lastFeaturesKey) return@LaunchedEffect
        lastFeaturesKey = key

        // 전이 중 흡수되는 별도 잠깐 보이므로 valid 전체의 아이콘을 등록
        valid.map {
            it.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1) to
                it.starColor.coerceIn(0, StarStyle.COLOR_COUNT - 1)
        }
            .distinct()
            .forEach { (type, color) ->
                val iconId = starIconId(type, color)
                if (addedIcons.add(iconId)) {
                    style.addImage(iconId, starBitmap(type, color))
                }
                val sparkleIconId = sparkleStarIconId(type, color)
                if (addedIcons.add(sparkleIconId)) {
                    style.addImage(sparkleIconId, sparkleStarBitmap(type, color))
                }
                val orbitIconId = orbitStarIconId(type, color)
                if (addedIcons.add(orbitIconId)) {
                    style.addImage(orbitIconId, orbitStarBitmap(type, color))
                }
            }

        // 최종(정착) 상태: 대표만 alpha=1, 크기는 좋아요×클러스터 보너스
        fun settle() {
            source.setGeoJson(FeatureCollection.fromFeatures(
                reps.map { d -> diaryFeature(d, d.longitude, d.latitude, d.id in nearIds, 1f, repSizeMult(d)) }
            ))
        }

        // 겹친 별 위성 — 정착 후 멤버 별들을 대표 곁에 띄우고 부드럽게 페이드 인.
        suspend fun settleOrbits() {
            orbitSource?.setGeoJson(orbitFeatures(reps, merged.groups, ::repSizeMult))
            orbitFade.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }

        val from = prevAssignment
        prevAssignment = assignment

        // 최초 1회는 애니메이션 없이 바로 표시
        if (from.isEmpty()) {
            settle()
            settleOrbits()
            return@LaunchedEffect
        }

        fun lngOf(id: String) = byId[id]?.longitude
        fun latOf(id: String) = byId[id]?.latitude

        // 합쳐짐/펼쳐짐 보간: 각 별을 (이전 대표 위치 ↔ 새 대표 위치) 로 이동 + 투명도 페이드
        // 전환 동안 위성은 감춘다(멤버 별이 직접 날아드는 연출과 겹치지 않게) — 정착 후 다시 등장.
        orbitFade.snapTo(0f)
        orbitSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        val durationNanos = 320_000_000.0
        var startNanos = 0L
        var t = 0f
        do {
            val frame = withFrameNanos { it }
            if (startNanos == 0L) startNanos = frame
            t = ((frame - startNanos) / durationNanos).toFloat().coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(t)
            val feats = ArrayList<Feature>(mergedReps.size)
            for (d in mergedReps) {
                val fromRep = from[d.id] ?: d.id
                val toRep = assignment[d.id] ?: d.id
                val fromAlpha = if (fromRep == d.id) 1f else 0f
                val toAlpha = if (toRep == d.id) 1f else 0f
                if (fromAlpha == 0f && toAlpha == 0f) continue // 계속 흡수됨 → 대표가 대신 표시
                val fLng = lngOf(fromRep) ?: d.longitude
                val fLat = latOf(fromRep) ?: d.latitude
                val tLng = lngOf(toRep) ?: d.longitude
                val tLat = latOf(toRep) ?: d.latitude
                val lng = fLng + (tLng - fLng) * e
                val lat = fLat + (tLat - fLat) * e
                val a = fromAlpha + (toAlpha - fromAlpha) * e
                // 대표로 정착하는 별만 클러스터 보너스 적용(흡수되는 별은 머지 배율만으로 페이드)
                val sm = if (toRep == d.id) repSizeMult(d) else mergeMult(d)
                feats.add(diaryFeature(d, lng, lat, d.id in nearIds, a, sm))
            }
            source.setGeoJson(FeatureCollection.fromFeatures(feats))
        } while (t < 1f)

        settle()
        settleOrbits()
    }

    // 별자리 라인 GeoJSON — 켜져 있을 때만 "지금 화면에 보이는 별"로 다시 계산해 채운다.
    // (끌 때는 비우지 않고 아래 페이드 효과가 사라진 뒤 비워 — 부드럽게 사라지도록)
    LaunchedEffect(diaries, styleRef, constellationEnabled, cameraIdleTick) {
        val source = constellationSource ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (constellationEnabled) {
            delay(90) // 클러스터링과 동일하게 idle 디바운스(O(n²) 별자리 재계산 빈도 완화)
            source.setGeoJson(buildConstellationFeatures(map, diaries, clusterRadiusPx, constellationMaxLinkPx))
        }
    }

    // 별자리 페이드 인/아웃 — 토글 시 후광·글로우·선 불투명도를 0↔target 으로 부드럽게.
    val constellationFade = remember { Animatable(0f) }
    LaunchedEffect(constellationEnabled, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val halo = style.getLayer(CONSTELLATION_HALO_LAYER) as? LineLayer
        val glow = style.getLayer(CONSTELLATION_GLOW_LAYER) as? LineLayer
        val line = style.getLayer(CONSTELLATION_LAYER) as? LineLayer
        constellationFade.animateTo(
            targetValue = if (constellationEnabled) 1f else 0f,
            animationSpec = tween(if (constellationEnabled) 550 else 380, easing = FastOutSlowInEasing),
        ) {
            halo?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_HALO_OPACITY * value))
            glow?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_GLOW_OPACITY * value))
            line?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_LINE_OPACITY * value))
        }
        // 완전히 꺼졌으면 GeoJSON 비우기
        if (!constellationEnabled) {
            constellationSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // 마커 애니메이션 루프(20fps): float 부유 + pulse + 스파클 궤도 + 위성 공전 + 파티클 트윙클.
    // ⚠️ 카메라 이동 중엔 스타일 변경을 멈춰 팬/줌 끊김을 방지한다.
    LaunchedEffect(styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        // 레이어는 스타일 초기화 때 전부 추가되고 제거되지 않으므로(styleRef 는 그 뒤 설정)
        // 참조를 1회만 조회해 재사용 — 매 틱 문자열 getLayer(JNI) 조회 제거.
        val diaryLayers = Array(PHASE_GROUPS) { style.getLayer(diaryLayerId(it)) as? SymbolLayer }
        val auraLayers = Array(PHASE_GROUPS) { style.getLayer(auraLayerId(it)) as? CircleLayer }
        val groundLayers = Array(PHASE_GROUPS) { style.getLayer(groundLightLayerId(it)) as? CircleLayer }
        val sparkleLayers = Array(SPARKLE_SETS) { s -> Array(PHASE_GROUPS) { g -> style.getLayer(sparkleLayerId(s, g)) as? SymbolLayer } }
        val orbitLayers = Array(MAX_ORBIT_STARS) { i -> Array(PHASE_GROUPS) { g -> style.getLayer(orbitLayerId(i, g)) as? SymbolLayer } }
        val particleLayers = Array(PHASE_GROUPS) { style.getLayer(particleLayerId(it)) as? SymbolLayer }
        val roadGlintLayer = style.getLayer(ROAD_GLINT_LAYER) as? LineLayer
        // 값이 연속 변하는 표현식은 양자화해 캐시(눈에 안 보이는 1/64 단위) — 매 틱 트리 재생성 방지.
        val sizeExprCache = HashMap<Int, Expression>()
        val groundExprCache = HashMap<Int, Expression>()
        val sparkleOpExprCache = HashMap<Int, Expression>()
        val particleOpExprCache = HashMap<Int, Expression>()
        fun quant(v: Float) = (v * 64f).toInt()
        var t = 0f
        var lastDashOffset = -1f
        var lastGlintEnvelope = -1f
        while (isActive) {
            if (isCameraMoving.value) {
                delay(100)
                continue
            }
            // 앱이 후면이면 스타일 갱신을 멈춰 배터리를 아낀다(전면 복귀 시 자동 재개).
            if (!com.chaminwoo.stary.core.util.AppForeground.isForeground) {
                delay(300)
                continue
            }
            val zoom = (mapRef?.cameraPosition?.zoom ?: 13.0).toFloat()
            // 움직일 게 없으면(별 없음 + 파티클 숨김 줌) 루프를 쉬어 배터리를 아낀다.
            val particlesVisible = zoom >= 9f
            if (diariesRef.value.isEmpty() && !particlesVisible) {
                delay(250)
                continue
            }
            t += 0.05f
            val zoomAmp = ((zoom - 6f) / (15f - 6f)).coerceIn(0.1f, 1f)
            for (g in 0 until PHASE_GROUPS) {
                val layer = diaryLayers[g] ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val wave = sin(t * 1.6f + phase) // -1(위)..1(아래)
                val floatDy = wave * 4f * zoomAmp // 최대 -4..4 dp, 줌 작으면 축소
                val pulse = 1f + 0.20f * ((sin(t * 3.2f + phase) + 1f) / 2f) // 1.0..1.2 맥동
                layer.setProperties(
                    PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                    PropertyFactory.iconSize(
                        sizeExprCache.getOrPut(quant(pulse)) { starSizeExpression(quant(pulse) / 64f) }
                    ),
                )
                // 후광도 같은 float 적용 → 별과 함께 떠오른다
                auraLayers[g]?.setProperties(
                    PropertyFactory.circleTranslate(arrayOf(0f, floatDy))
                )
                // 바닥 빛 웅덩이는 지점 고정(시차의 기준점). 별이 내려와 가까워질수록 살짝 밝게.
                val downFrac = (wave + 1f) / 2f // 0(위)..1(아래)
                val groundOp = GROUND_LIGHT_OPACITY * (0.7f + 0.3f * downFrac)
                groundLayers[g]?.setProperties(
                    PropertyFactory.circleOpacity(
                        groundExprCache.getOrPut(quant(groundOp)) {
                            Expression.product(
                                Expression.literal(quant(groundOp) / 64f),
                                Expression.get("alpha")
                            )
                        }
                    )
                )
                // 스파클 — 타원 궤도(icon-offset) + 별 부유 동기(iconTranslate) + 개수 게이트(opacity).
                val sparkleZoom = sparkleZoomFactor(zoom)
                if (sparkleZoom > 0.01f) { // 숨김 줌에서는 갱신 자체를 건너뛴다
                    // 부모(set 1) 궤도 각 — 위성(set 2)이 이 위치를 중심으로 주전원 공전한다.
                    val parentAng = t * -0.8f + phase + 1.9f
                    val parentUx = kotlin.math.cos(parentAng)
                    val parentUy = sin(parentAng) * 0.55f
                    val satAng = t * 3.4f + phase
                    val satUx = kotlin.math.cos(satAng)
                    val satUy = sin(satAng) * 0.55f

                    for (s in 0 until SPARKLE_SETS) {
                        val sl = sparkleLayers[s][g] ?: continue
                        val op = 0.30f + 0.60f * ((sin(t * (2.4f + 0.35f * g) + phase + s * 2.1f) + 1f) / 2f)
                        // 세트별 목표 오프셋(dp) — 위성은 부모 파티클 위치 + 주전원(그 파티클을 공전).
                        val dpAt: (Float) -> Pair<Float, Float> = when (s) {
                            SPARKLE_SATELLITE_SET -> { tier ->
                                val pr = orbitTargetDp(1, tier)
                                val sr = satelliteOrbitDp(tier)
                                (parentUx * pr + satUx * sr) to (parentUy * pr + satUy * sr)
                            }
                            else -> { tier ->
                                val ang = t * (if (s == 0) 1.1f else -0.8f) + phase + s * 1.9f
                                val r = orbitTargetDp(s, tier)
                                (kotlin.math.cos(ang) * r) to (sin(ang) * 0.55f * r)
                            }
                        }
                        sl.setProperties(
                            PropertyFactory.iconOffset(
                                sparkleOffsetExpression(s, sparkleZoom, screenDensity, dpAt)
                            ),
                            PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                            PropertyFactory.iconOpacity(
                                sparkleOpExprCache.getOrPut(s * 100_000 + quant(op)) {
                                    sparkleOpacityExpression(s, quant(op) / 64f)
                                }
                            ),
                        )
                    }
                }
                // 겹친 별 위성 부유 — 회전(공전) 없이 고정 자리에서 위성마다 다른 주기로 잔잔히
                // 흔들리되, iconTranslate 는 대표 별과 같은 floatDy 를 그대로 물려받아 **대표 별과
                // 함께** 오르내린다(같은 phaseGroup 레이어라 가능).
                if (zoom > 11.1f) {
                    for (i in 0 until MAX_ORBIT_STARS) {
                        val ol = orbitLayers[i][g] ?: continue
                        val driftX = sin(t * (0.7f + 0.14f * i) + i * 2.1f) * 0.8f
                        val driftY = sin(t * (1.15f + 0.18f * i) + i * 1.4f) * 1.0f
                        val op = orbitFade.value * (0.88f + 0.12f * sin(t * 1.7f + i * 1.3f))
                        ol.setProperties(
                            PropertyFactory.iconOffset(orbitOffsetExpression(i, driftX, driftY, screenDensity)),
                            PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                            PropertyFactory.iconOpacity(op),
                        )
                    }
                }
            }
            // 별가루 반짝임: 위상 그룹별 레이어 opacity 만 갱신 (GeoJSON 재생성 없음)
            for (g in 0 until PHASE_GROUPS) {
                val layer = particleLayers[g] ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val speed = 2.0f + g * 0.4f // 그룹마다 다른 주기 → 덜 기계적인 반짝임
                val twinkle = 0.25f + 0.75f * ((sin(t * speed + phase) + 1f) / 2f)
                layer.setProperties(
                    PropertyFactory.iconOpacity(
                        particleOpExprCache.getOrPut(quant(twinkle)) { particleOpacityExpression(quant(twinkle) / 64f) }
                    )
                )
            }
            // 도로 글린트: 대시 위상을 흘려 빛 알갱이가 도로를 따라 흐른다.
            // 위상을 ROAD_GLINT_STEPS 단계로 양자화해 대시 아틀라스 캐시를 재사용(무한 패턴 생성 방지).
            val off = (t * ROAD_GLINT_SPEED) % ROAD_GLINT_GAP
            val q = (off / ROAD_GLINT_GAP * ROAD_GLINT_STEPS).toInt()
                .toFloat() / ROAD_GLINT_STEPS * ROAD_GLINT_GAP
            if (q != lastDashOffset) {
                lastDashOffset = q
                roadGlintLayer?.setProperties(
                    PropertyFactory.lineDasharray(
                        arrayOf(0f, q, ROAD_GLINT_DASH, ROAD_GLINT_GAP - q)
                    )
                )
            }
            // 위상이 한 바퀴 돌아 처음으로 되돌아가는(알갱이가 다시 태어나는) 순간 전후
            // ROAD_GLINT_FADE_SEC 씩 부드럽게 사라졌다 나타나도록 불투명도에 삼각 envelope 를 곱한다.
            val cyclePos = t % ROAD_GLINT_PERIOD_SEC
            val envelope = when {
                cyclePos < ROAD_GLINT_FADE_SEC -> cyclePos / ROAD_GLINT_FADE_SEC
                cyclePos > ROAD_GLINT_PERIOD_SEC - ROAD_GLINT_FADE_SEC ->
                    (ROAD_GLINT_PERIOD_SEC - cyclePos) / ROAD_GLINT_FADE_SEC
                else -> 1f
            }.coerceIn(0f, 1f)
            if (envelope != lastGlintEnvelope) {
                lastGlintEnvelope = envelope
                roadGlintLayer?.setProperties(
                    PropertyFactory.lineOpacity(roadGlintOpacityExpression(envelope))
                )
            }
            delay(50)
        }
    }

    // 현재 위치 마커 갱신
    LaunchedEffect(currentLatLng, mapRef) {
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
    }

    // 개척 퀘스트 비콘 갱신(체크리스트 32) — 개척 현황을 구독해 "등장했지만 미개척"인 나라만 표시.
    val pioneerClaims by remember {
        com.chaminwoo.stary.data.repository.FirebasePioneerRepository().observeClaims()
    }.collectAsState(initial = emptyMap())
    LaunchedEffect(pioneerClaims, pioneerSource) {
        val src = pioneerSource ?: return@LaunchedEffect
        val featured = com.chaminwoo.stary.shared.config.PioneerQuest
            .featuredCountries(System.currentTimeMillis(), pioneerClaims.keys)
        val features = featured.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lng, c.lat)).apply {
                addStringProperty("code", c.code)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // 최초 진입 시 내 위치로 카메라 1회 이동 — 스타일 로드 시점엔 아직 위치 fix 가 없어 기본 좌표로
    // 떠 있다. 실제 위치 fix 가 들어오면 그 위치로 카메라를 한 번 옮긴다(이후엔 사용자 조작 존중).
    var didAutoCenter by remember { mutableStateOf(false) }
    LaunchedEffect(currentLatLng, mapRef, focusDiary) {
        if (didAutoCenter) return@LaunchedEffect
        if (focusDiary != null) return@LaunchedEffect       // 포커스 요청이 카메라를 직접 다룬다
        val map = mapRef ?: return@LaunchedEffect
        if (LocationHelper.getCurrentLatLng() == null) return@LaunchedEffect // 실제 fix 대기(기본 좌표면 보류)
        didAutoCenter = true
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(currentLatLng.toMl()).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            ),
            700
        )
    }

    // 외부(알림) 요청: 특정 다이어리로 카메라 이동 후 열람 파장 1회 (세부 화면 이동 없음).
    LaunchedEffect(focusDiary, mapRef) {
        val target = focusDiary ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(MlLatLng(target.lat, target.lng)).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            ),
            800,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    // 카메라가 다이어리를 화면 중앙에 둔 상태 → 파장 중심도 화면 중앙(0.5,0.5)
                    map.snapshot { bmp ->
                        warpState.value = DiaryOpenWarpData(
                            bmp, 0.5f, 0.5f, target.diaryId, target.colorIndex, navigateAfter = false
                        )
                    }
                    // 친구 별 탭(withRoute) → 파장과 함께 그 별까지 도보 길찾기 시작.
                    if (target.withRoute) requestRouteRef.value(target.lat, target.lng)
                }
                override fun onCancel() {
                    onFocusHandledRef.value()
                }
            }
        )
    }

    // 도보 길찾기 실시간 렌더 — 저장된 전체 경로에서 내 위치의 최근접점부터 목적지까지만 그린다.
    // 지나온 길(출발점~최근접점)은 숨기고, 내 위치가 경로에서 떨어져 있으면 최근접점까지 직선으로 잇는다.
    LaunchedEffect(currentLatLng, savedRoute, routeSource) {
        val src = routeSource ?: return@LaunchedEffect
        val full = savedRoute
        if (full == null || full.size < 2) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        val me = Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude)
        src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(partialRouteFrom(full, me))))
    }
}
