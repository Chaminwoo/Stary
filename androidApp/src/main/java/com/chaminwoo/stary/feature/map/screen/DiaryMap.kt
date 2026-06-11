package com.chaminwoo.stary.feature.map.screen

import android.view.View
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

private const val DEFAULT_ZOOM = 15.0
private const val CURRENT_SOURCE = "current-location"

/** 앱 공용 좌표 -> MapLibre 좌표 */
private fun LatLng.toMl(): MlLatLng = MlLatLng(latitude, longitude)

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
 * - 스타일은 res/raw/maplibre_style.json (배경 검정 / 물 / 큰 길만, 건물·POI·라벨 없음).
 *   필요한 레이어만 그리므로 Google Maps 처럼 불필요한 레이어를 다운로드/렌더하지 않는다.
 * - 줌 < 11 에서는 스타일의 road-major minzoom 으로 길이 자동으로 사라지고 바다+땅만 남는다.
 * - 내 위치 마커만 표시(GeoJSON circle layer). 다이어리 마커/100m/길찾기는 이후 단계.
 * - 키: BuildConfig.MAPTILER_KEY 가 스타일 JSON 의 __MAPTILER_KEY__ 에 주입된다.
 */
@Composable
fun DiaryMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    isFollowing: Boolean,
    onGestureDetected: () -> Unit,
    onRefollowClick: () -> Unit,
    onItemClick: (String) -> Unit,
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
    var locationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    val onGestureRef = rememberUpdatedState(onGestureDetected)
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
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        onGestureRef.value()
                    }
                }
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    val start = initialLatLngRef.value
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

                    // 초기 카메라: cameraTarget(다이어리 위치 보기)이 있으면 현재+타겟 경계,
                    // 없으면 현재 위치 중심.
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
            FloatingActionButton(
                onClick = {
                    onRefollowClick()
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

    // 현재 위치 갱신 + follow 모드 카메라 추적
    LaunchedEffect(currentLatLng, isFollowing, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
        if (isFollowing) {
            map.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng.toMl()))
        }
    }
}
