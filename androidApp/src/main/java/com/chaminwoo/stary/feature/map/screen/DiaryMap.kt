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
private const val DIARY_LAYER = "diary-stars"

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

    // 3) 흰 중심 하이라이트 (별 중심이 빛나 보이게, 45% 축소본)
    val centerPath = android.graphics.Path(path)
    val m = android.graphics.Matrix().apply { setScale(0.45f, 0.45f, side / 2f, side / 2f) }
    centerPath.transform(m)
    canvas.drawPath(centerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        alpha = 220
    })
    return out
}

/** near 여부에 따른 iconSize 표현식 (pulse 배율은 애니메이션 루프에서 곱해 갱신). */
private fun starSizeExpression(pulse: Float): Expression =
    Expression.switchCase(
        Expression.eq(Expression.get("near"), Expression.literal(true)),
        Expression.literal(STAR_SIZE_NEAR * pulse),
        Expression.literal(STAR_SIZE_FAR)
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
                // 별 클릭 → 100m 게이팅 (길찾기 기능은 제거됨 — 밖이면 안내 토스트만)
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val features = map.queryRenderedFeatures(screen, DIARY_LAYER)
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

                    // 다이어리 별 마커
                    val dSrc = GeoJsonSource(DIARY_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(dSrc)
                    style.addLayer(
                        SymbolLayer(DIARY_LAYER, DIARY_SOURCE).withProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(starSizeExpression(1f)),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                    )
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
    LaunchedEffect(diaries, styleRef, currentLatLng) {
        val style = styleRef ?: return@LaunchedEffect
        val source = diarySource ?: return@LaunchedEffect
        val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }
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
            val near = LocationHelper.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude, d.latitude, d.longitude
            ) <= StaryConfig.DIARY_OPEN_RADIUS_M
            Feature.fromGeometry(Point.fromLngLat(d.longitude, d.latitude)).apply {
                addStringProperty("id", d.id)
                addBooleanProperty("near", near)
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

    // 마커 애니메이션: 전체 별 float(상하 부유) + 근접 별 pulse(맥동 확대)
    LaunchedEffect(styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        var t = 0f
        while (isActive) {
            val layer = style.getLayer(DIARY_LAYER) as? SymbolLayer ?: break
            t += 0.05f
            val floatDy = (sin(t * 1.6f) * 3f) // -3..3 dp 부유
            val pulse = 1f + 0.20f * ((sin(t * 3.2f) + 1f) / 2f) // 1.0..1.2 맥동
            layer.setProperties(
                PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                PropertyFactory.iconSize(starSizeExpression(pulse)),
            )
            delay(50)
        }
    }

    // 현재 위치 마커 갱신
    LaunchedEffect(currentLatLng, mapRef) {
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
    }
}
