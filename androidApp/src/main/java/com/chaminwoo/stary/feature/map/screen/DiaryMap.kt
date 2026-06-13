package com.chaminwoo.stary.feature.map.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.sin
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

private const val DEFAULT_ZOOM = 15.0
private const val CURRENT_SOURCE = "current-location"
private const val DIARY_SOURCE = "diaries"
private const val STAR_PARTICLE_SOURCE = "star-particles"
private const val PARTICLE_ICON_ID = "star-particle-dot"

/**
 * float/pulse 애니메이션 위상 그룹 수.
 * 한 SymbolLayer 의 iconTranslate 는 레이어 전체에 일괄 적용되므로,
 * 마커를 id 해시 기반 그룹으로 나눠 레이어별로 다른 위상을 줘 "따로따로" 부유하게 한다.
 */
private const val PHASE_GROUPS = 4
private fun diaryLayerId(group: Int) = "diary-stars-$group"
private val DIARY_LAYER_IDS = Array(PHASE_GROUPS) { diaryLayerId(it) }
private fun particleLayerId(group: Int) = "star-particles-$group"

/** 별가루 파티클: 개수/분포 반경/시드(고정 → 항상 같은 배치). */
private const val PARTICLE_COUNT = 400
private const val PARTICLE_RADIUS_M = 20_000.0
private const val PARTICLE_SEED = 42

/** 마커 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
private const val MARKER_SIDE_PX = 160

/**
 * 별 기본/근접 크기 (iconSize 배율).
 * 주의: MapLibre 의 addImage(bitmap)는 기기 밀도(pixelRatio)로 나눠 표시하므로
 * 화면 크기 ≈ 160/density × iconSize dp (density 2.6 기준 0.65 → 약 40dp).
 */
private const val STAR_SIZE_FAR = 0.65f
private const val STAR_SIZE_NEAR = 0.9f

/** 앱 공용 좌표 -> MapLibre 좌표 */
private fun LatLng.toMl(): MlLatLng = MlLatLng(latitude, longitude)

/** type×color 조합의 스타일 이미지 id */
private fun starIconId(type: Int, color: Int) = "star-t$type-c$color"

/**
 * 별 마커 비트맵 생성 — PNG 미사용, [StarStyle.starPath] 로 직접 그린다.
 * (PNG 디코드→텍스처 경로가 에뮬레이터에서 대각선 빗금으로 깨지는 문제가 있어 Path 렌더로 교체)
 * 구성: 팔레트색 글로우(blur) + 팔레트색 본체 + 흰색 중심 하이라이트.
 */
private fun starBitmap(type: Int, colorIdx: Int): Bitmap {
    val color = StarStyle.colorOf(colorIdx).toArgb()
    val side = MARKER_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    // 별 본체는 글로우 여백을 위해 변의 78% 크기로 중앙 배치
    val starSize = side * 0.78f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(type, starSize)).apply {
        offset(offset, offset)
    }

    // 1) 글로우 (2회 = 진하게)
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glowPaint)
    canvas.drawPath(path, glowPaint)

    // 2) 본체
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })

    // 3) 중심 코어: 원색보다 살짝 어두운 톤(35% 어둡게) + 65% 크기 —
    //    흰색/작은 코어는 본체와 분리돼 보였음. 어두운 코어가 글로우와 한 덩어리로 빛나는 인상.
    val coreColor = androidx.core.graphics.ColorUtils.blendARGB(color, AndroidColor.BLACK, 0.05f)
    val centerPath = android.graphics.Path(path)
    val m = android.graphics.Matrix().apply { setScale(0.8f, 0.8f, side / 2f, side / 2f) }
    centerPath.transform(m)
    canvas.drawPath(centerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = coreColor })
    return out
}

/**
 * iconSize 표현식: 줌 보간(줌아웃일수록 작게) × near 확대 × pulse(애니메이션 루프에서 갱신).
 * ["zoom"] 은 최상위 interpolate 에서만 허용되므로 줌 스톱의 출력값에 near 분기를 넣는다.
 */
private fun starSizeExpression(pulse: Float): Expression {
    fun sized(zoomMult: Float): Expression = Expression.switchCase(
        Expression.eq(Expression.get("near"), Expression.literal(true)),
        Expression.literal(STAR_SIZE_NEAR * pulse * zoomMult),
        Expression.literal(STAR_SIZE_FAR * zoomMult)
    )
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(8f, sized(0.3f)),
        Expression.stop(12f, sized(0.55f)),
        Expression.stop(15f, sized(1f)),
    )
}

/** 파티클 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
private const val PARTICLE_SIDE_PX = 24

/** 별가루 파티클 비트맵 — 흰색 글로우 + 흰색 코어의 작은 점. */
private fun particleBitmap(): Bitmap {
    val side = PARTICLE_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val c = side / 2f
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        maskFilter = android.graphics.BlurMaskFilter(4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(c, c, side * 0.22f, glowPaint)
    canvas.drawCircle(c, c, side * 0.13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
    return out
}

/**
 * 별가루 파티클 FeatureCollection — [center] 기준 반경 [PARTICLE_RADIUS_M] 내 균등 분포.
 * 시드 고정이라 매 실행 같은 배치. 한 번 생성 후 갱신하지 않는다(카메라 이동과 무관).
 */
private fun starParticleFeatures(center: LatLng): FeatureCollection {
    val rnd = kotlin.random.Random(PARTICLE_SEED)
    val cosLat = kotlin.math.cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)
    val features = (0 until PARTICLE_COUNT).map { i ->
        // sqrt 로 면적 균등 분포
        val r = PARTICLE_RADIUS_M * kotlin.math.sqrt(rnd.nextDouble())
        val theta = rnd.nextDouble() * 2.0 * Math.PI
        val dLat = r * kotlin.math.cos(theta) / 111_320.0
        val dLng = r * kotlin.math.sin(theta) / (111_320.0 * cosLat)
        Feature.fromGeometry(
            Point.fromLngLat(center.longitude + dLng, center.latitude + dLat)
        ).apply {
            addNumberProperty("phase", rnd.nextDouble() * 2.0 * Math.PI)
            addNumberProperty("twinkleSpeed", 0.5 + rnd.nextDouble())
            addNumberProperty("depth", 0.5 + rnd.nextDouble() * 0.5) // 0.5..1.0 크기 배율
            addNumberProperty("phaseGroup", i % PHASE_GROUPS)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * 파티클 iconSize: 줌 6→0, 10→0.4, 15→0.8 보간 × per-feature depth(0.5..1.0).
 * 데이터 주도 expression 이므로 한 번만 설정하면 된다.
 * (사라지는 시점은 opacity 와 함께 줌 6 — 사용자 튜닝값)
 */
private fun particleSizeExpression(): Expression {
    fun sized(base: Float): Expression =
        Expression.product(Expression.literal(base), Expression.get("depth"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, sized(0f)),
        Expression.stop(10f, sized(0.4f)),
        Expression.stop(15f, sized(0.8f)),
    )
}

/**
 * 파티클 iconOpacity: 줌 6 이하 완전 숨김 → 10 에서 [twinkle] 까지 선형 등장.
 * twinkle 은 애니메이션 루프에서 레이어(위상 그룹)별로 갱신해 반짝임을 만든다.
 */
private fun particleOpacityExpression(twinkle: Float): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, 0f),
        Expression.stop(10f, twinkle),
    )

/**
 * MapView 를 Compose 생명주기에 묶어 반환. (MapLibre.getInstance 는 MapView 생성 전 1회)
 */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { id = View.generateViewId() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/**
 * MapLibre GL Native + MapTiler 벡터 타일 기반 지도.
 *
 * - 다이어리 별 마커: GeoJSON + SymbolLayer. star_1..5 × 12색 tint, 비율 유지 렌더.
 * - 100m 이내 별은 크게(near) + pulse, 전체 별은 float(부유) 애니메이션.
 * - 별 클릭: 100m 이내 → 열람 / 밖 → 거리 안내 토스트. (길찾기 기능은 제거됨)
 * - "내 위치로" FAB 로 현재 위치 복귀.
 */
@Composable
fun DiaryMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    onDiaryClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val addedIcons = remember { mutableSetOf<String>() }
    // 지도 제스처/카메라 이동 중에는 스타일 변경(애니메이션 setProperties)을 멈춰 끊김 방지
    val isCameraMoving = remember { mutableStateOf(false) }

    val onDiaryClickRef = rememberUpdatedState(onDiaryClick)
    val currentLatLngRef = rememberUpdatedState(currentLatLng)
    val diariesRef = rememberUpdatedState(diaries)
    val initialLatLngRef = rememberUpdatedState(currentLatLng)

    Box(modifier = modifier.fillMaxSize()) {
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
                }
                map.addOnCameraMoveStartedListener { isCameraMoving.value = true }
                map.addOnCameraIdleListener { isCameraMoving.value = false }
                // 별 클릭 → 100m 게이팅 (길찾기 기능은 제거됨 — 밖이면 안내 토스트만)
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val features = map.queryRenderedFeatures(screen, *DIARY_LAYER_IDS)
                    val id = features.firstOrNull()?.getStringProperty("id")
                    if (id != null) {
                        val diary = diariesRef.value.firstOrNull { it.id == id }
                        if (diary != null) {
                            val cur = currentLatLngRef.value
                            val distance = LocationHelper.distanceBetween(
                                cur.latitude, cur.longitude, diary.latitude, diary.longitude
                            )
                            if (distance <= StaryConfig.DIARY_OPEN_RADIUS_M) {
                                onDiaryClickRef.value(id)
                            } else {
                                Toast.makeText(
                                    context,
                                    "${StaryConfig.DIARY_OPEN_RADIUS_M.toInt()}m 이내에 있어야 열람할 수 있어요 (현재 ${distance.toInt()}m)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        true
                    } else false
                }
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    val start = initialLatLngRef.value

                    // 별가루 파티클 — 실제 지도 좌표 GeoJSON, MapLibre 컬링으로 화면 내만 렌더.
                    // 다이어리 별보다 먼저 추가해 아래에 깔린다. 생성 후 갱신 없음.
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

                    // 다이어리 별 마커 — 위상 그룹별 레이어(같은 source, phaseGroup 필터)
                    val dSrc = GeoJsonSource(DIARY_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(dSrc)
                    for (g in 0 until PHASE_GROUPS) {
                        val layer = SymbolLayer(diaryLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(starSizeExpression(1f)),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                        layer.setFilter(
                            Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                        )
                        style.addLayer(layer)
                    }
                    diarySource = dSrc

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
                        map.cameraPosition = CameraPosition.Builder()
                            .target(start.toMl())
                            .zoom(DEFAULT_ZOOM)
                            .build()
                    } else {
                        LocationHelper.cameraTarget = null
                        val bounds = LatLngBounds.Builder()
                            .include(start.toMl())
                            .include(MlLatLng(target.latitude, target.longitude))
                            .build()
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                    }
                    styleRef = style
                    mapRef = map
                }
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
                onClick = {
                    mapRef?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(currentLatLng.toMl())
                                .zoom(DEFAULT_ZOOM)
                                .build()
                        )
                    )
                },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = "내 위치로",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 다이어리 생성
            FloatingActionButton(
                onClick = onCreateClick,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6EE7B7), Color(0xFF3B82F6)),
                                start = Offset(0f, 0f),
                                end = Offset(80f, 80f)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "다이어리 생성",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // 다이어리/현재위치 변경 → 마커 갱신 (near = 100m 이내, 아이콘은 사용 조합만 등록)
    // 위치는 자주 갱신되므로 "다이어리 목록 or near 집합"이 실제로 바뀐 경우에만 setGeoJson (끊김 방지)
    var lastFeaturesKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(diaries, styleRef, currentLatLng) {
        val style = styleRef ?: return@LaunchedEffect
        val source = diarySource ?: return@LaunchedEffect
        val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        val nearIds = valid.filter { d ->
            LocationHelper.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude, d.latitude, d.longitude
            ) <= StaryConfig.DIARY_OPEN_RADIUS_M
        }.map { it.id }.toSet()
        val key = diaries to nearIds
        if (key == lastFeaturesKey) return@LaunchedEffect
        lastFeaturesKey = key

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
            }
        val features = valid.map { d ->
            Feature.fromGeometry(Point.fromLngLat(d.longitude, d.latitude)).apply {
                addStringProperty("id", d.id)
                addBooleanProperty("near", d.id in nearIds)
                addNumberProperty("phaseGroup", kotlin.math.abs(d.id.hashCode()) % PHASE_GROUPS)
                addStringProperty(
                    "icon",
                    starIconId(
                        d.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1),
                        d.starColor.coerceIn(0, StarStyle.COLOR_COUNT - 1)
                    )
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // 마커 애니메이션: float(상하 부유) + 근접 별 pulse(맥동 확대).
    // 위상 그룹 레이어마다 다른 위상을 적용해 별들이 따로따로 부유한다.
    // 카메라 이동 중엔 스타일 변경을 멈춰 팬/줌 끊김을 방지한다.
    LaunchedEffect(styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        var t = 0f
        while (isActive) {
            if (isCameraMoving.value) {
                delay(100)
                continue
            }
            t += 0.05f
            for (g in 0 until PHASE_GROUPS) {
                val layer = style.getLayer(diaryLayerId(g)) as? SymbolLayer ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val floatDy = (sin(t * 1.6f + phase) * 3f) // -3..3 dp 부유
                val pulse = 1f + 0.20f * ((sin(t * 3.2f + phase) + 1f) / 2f) // 1.0..1.2 맥동
                layer.setProperties(
                    PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                    PropertyFactory.iconSize(starSizeExpression(pulse)),
                )
            }
            // 별가루 반짝임: 위상 그룹별 레이어 opacity 만 갱신 (GeoJSON 재생성 없음)
            for (g in 0 until PHASE_GROUPS) {
                val layer = style.getLayer(particleLayerId(g)) as? SymbolLayer ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val speed = 2.0f + g * 0.4f // 그룹마다 다른 주기 → 덜 기계적인 반짝임
                val twinkle = 0.25f + 0.75f * ((sin(t * speed + phase) + 1f) / 2f)
                layer.setProperties(
                    PropertyFactory.iconOpacity(particleOpacityExpression(twinkle))
                )
            }
            delay(50)
        }
    }

    // 현재 위치 마커 갱신
    LaunchedEffect(currentLatLng, mapRef) {
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
    }
}
