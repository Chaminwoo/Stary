package com.chaminwoo.stary.feature.map.screen

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.google.android.gms.maps.model.LatLng as GmsLatLng

private const val STAR_COUNT = 5

/** 앱 공용 좌표 -> Google Maps SDK 좌표 변환 */
private fun LatLng.toGms(): GmsLatLng = GmsLatLng(latitude, longitude)

/** Google Maps 클러스터링용 아이템 래퍼 */
private data class DiaryClusterItem(val diary: Diary) : ClusterItem {
    override fun getPosition(): GmsLatLng = GmsLatLng(diary.latitude, diary.longitude)
    override fun getTitle(): String = diary.title
    override fun getSnippet(): String = ""
    override fun getZIndex(): Float = 0f
}

private fun starResId(context: android.content.Context, diary: Diary): Int {
    val idx = (diary.id.hashCode().absoluteValue % STAR_COUNT) + 1
    return context.resources.getIdentifier("star_$idx", "drawable", context.packageName)
}

/**
 * 네이버맵 기반 DiaryNaverMap 을 Google Maps Compose 로 대체한 지도 화면.
 *
 * 변경 요약:
 *  - 좌표 타입: com.naver.maps.geometry.LatLng -> 공용 com.chaminwoo.stary.core.geo.LatLng
 *    (Google SDK 경계에서만 GmsLatLng 로 변환)
 *  - 지도/클러스터/마커: 네이버 Overlay/Clusterer -> Google Maps Compose + maps-compose-utils Clustering
 *  - 네이버 전용의 per-marker ValueAnimator(pulse/float) 애니메이션은 SDK 종속이라 단순화함
 *    (필요 시 clusterItemContent 의 Compose 애니메이션으로 재구현 가능)
 *  - 100m 근접 열람 게이팅, 현재 위치 추적(follow), FAB 동작은 유지
 *
 * API Key 는 코드가 아니라 AndroidManifest 의 com.google.android.geo.API_KEY 로 주입한다.
 */
@OptIn(com.google.maps.android.compose.MapsComposeExperimentalApi::class)
@Composable
fun DiaryGoogleMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    cameraPositionState: CameraPositionState,
    isFollowing: Boolean,
    onGestureDetected: () -> Unit,
    onRefollowClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onItemClickRef = rememberUpdatedState(onItemClick)
    val currentLatLngRef = rememberUpdatedState(currentLatLng)

    val clusterItems = remember(diaries) {
        diaries
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .map { DiaryClusterItem(it) }
    }

    // 사용자가 지도를 직접 제스처로 움직이면 follow 해제
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            onGestureDetected()
        }
    }

    // follow 모드일 때 현재 위치로 카메라 추적
    LaunchedEffect(currentLatLng, isFollowing) {
        if (isFollowing) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(currentLatLng.toGms())
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            mapColorScheme = ComposeMapColorScheme.DARK, // 네이버 night 모드 대체
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isBuildingEnabled = true,
                isTrafficEnabled = false,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
            ),
        ) {
            // 현재 위치 마커
            Marker(
                state = rememberMarkerState(
                    key = "current",
                    position = currentLatLng.toGms()
                ),
                title = "내 위치",
            )

            // 다이어리 마커 (클러스터링)
            Clustering(
                items = clusterItems,
                onClusterItemClick = { item ->
                    val distance = LocationHelper.distanceBetween(
                        currentLatLngRef.value.latitude,
                        currentLatLngRef.value.longitude,
                        item.diary.latitude,
                        item.diary.longitude
                    )
                    if (distance <= StaryConfig.DIARY_OPEN_RADIUS_M) {
                        onItemClickRef.value(item.diary.id)
                    } else {
                        Toast.makeText(
                            context,
                            "${StaryConfig.DIARY_OPEN_RADIUS_M.toInt()}m 이내에 있어야 열람할 수 있어요",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true // 이벤트 소비
                },
                clusterItemContent = { item ->
                    val res = starResId(context, item.diary)
                    if (res != 0) {
                        Image(
                            painter = painterResource(id = res),
                            contentDescription = item.diary.title,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
            )
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
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(currentLatLng.toGms(), 15f)
                            )
                        )
                    }
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
}
