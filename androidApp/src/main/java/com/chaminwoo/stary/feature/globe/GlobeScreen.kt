package com.chaminwoo.stary.feature.globe

import android.opengl.GLSurfaceView
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 3D 행성(지구) 화면 — 지도에서 줌을 최소로 빼면 나타나는 전체화면 오버레이.
 *
 * - 드래그: 행성 회전(관성), 3초 무입력 시 느린 자동 회전.
 * - 핀치: 카메라 줌. [GlobeRenderer.MIN_DIST] 밑으로 당기면 지금 정면 지점의 지도(줌인)로 복귀.
 * - 뒤로가기: 동일하게 지도 복귀.
 *
 * 성능: GLSurfaceView/렌더러/텍스처는 이 컴포저블이 컴포지션에 들어올 때만 생성 —
 * 지도 화면 평상시 비용 0. 이탈 시 뷰 detach 로 GL 컨텍스트 해제.
 */
@Composable
fun GlobeScreen(
    diaries: List<Diary>,
    startLat: Double,
    startLng: Double,
    onRequestExit: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember {
        GlobeRenderer(context).apply { setInitialFacing(startLat, startLng) }
    }
    var exitFired by remember { mutableStateOf(false) }
    val fireExit = {
        if (!exitFired) {
            exitFired = true
            val (lat, lng) = renderer.facingLatLng()
            onRequestExit(lat, lng)
        }
    }

    // 다이어리 → 별/글로우 지오메트리(무거운 계산은 백그라운드)
    LaunchedEffect(diaries) {
        withContext(Dispatchers.Default) { renderer.setDiaries(diaries) }
    }

    BackHandler { fireExit() }

    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setZOrderMediaOverlay(true) // MapLibre SurfaceView 위에 얹히도록
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 드래그 회전 + 핀치 줌. 속도는 렌더러가 관성으로 사용.
                    var lastMs = 0L
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        val now = SystemClock.uptimeMillis()
                        val dt = if (lastMs == 0L) 0.016f else ((now - lastMs) / 1000f).coerceIn(0.004f, 0.1f)
                        lastMs = now
                        renderer.lastInteractionMs = now

                        // 드래그 감도: 멀수록(줌아웃) 크게 회전
                        val degPerPx = 0.075f * ((renderer.camDist - 1f) / 2.2f)
                        val dYaw = pan.x * degPerPx
                        val dPitch = pan.y * degPerPx
                        renderer.yawDeg += dYaw
                        renderer.pitchDeg = (renderer.pitchDeg + dPitch).coerceIn(-75f, 75f)
                        renderer.yawVelDeg = dYaw / dt * 0.55f
                        renderer.pitchVelDeg = dPitch / dt * 0.55f

                        if (zoom != 1f) {
                            val next = (renderer.camDist / zoom)
                                .coerceIn(GlobeRenderer.MIN_DIST - 0.05f, GlobeRenderer.MAX_DIST)
                            renderer.camDist = next
                            // 핀치-인으로 최소 거리 도달 → 그 지점 지도로 줌인 복귀
                            if (next <= GlobeRenderer.MIN_DIST && zoom > 1f) fireExit()
                        }
                    }
                }
        )

        // GLSurfaceView 는 액티비티 pause 시 GL 컨텍스트를 놓아야 한다.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                    Lifecycle.Event.ON_RESUME -> glView?.onResume()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 조작 힌트 — 잠깐 보였다 사라짐
        var hintVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(4200); hintVisible = false }
        val hintAlpha by animateFloatAsState(
            targetValue = if (hintVisible) 1f else 0f,
            animationSpec = tween(700), label = "globe-hint"
        )
        if (hintAlpha > 0.01f) {
            Text(
                text = stringResource(R.string.globe_hint),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .graphicsLayer { alpha = hintAlpha }
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
