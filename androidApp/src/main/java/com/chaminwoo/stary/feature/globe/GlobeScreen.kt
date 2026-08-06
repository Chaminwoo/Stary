package com.chaminwoo.stary.feature.globe

import android.opengl.GLSurfaceView
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
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
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * EGL 설정 선택기 — RGBA8888 + **깊이 24비트 우선**(없는 기기만 16비트 폴백).
 *
 * 16비트 깊이버퍼로는 줌아웃(camDist 9.5)에서 깊이 해상도가 0.011 월드단위까지 벌어져,
 * 지표 바로 위에 있는 레이어(구름 +0.012, 다이어리 불빛 +0.008)가 지표와 같은 깊이 값으로
 * 뭉개지면서 얼룩덜룩 z-파이팅이 난다. 24비트면 같은 조건에서 해상도가 256배 촘촘해진다.
 * (GLSurfaceView.setEGLConfigChooser(r,g,b,a,depth,stencil) 는 조건에 맞는 설정이 없으면
 *  GL 스레드에서 예외를 던지므로, 직접 폴백을 가진 선택기를 쓴다.)
 */
private object DepthFirstConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (depth in intArrayOf(24, 16)) {
            val spec = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, depth,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_NONE,
            )
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, spec, null, 0, num) || num[0] <= 0) continue
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, spec, configs, num[0], num)) continue
            // eglChooseConfig 는 요청보다 큰 색 깊이(RGB1010102 등)도 돌려줄 수 있어
            // 정확히 8888 인 설정을 우선 고른다. 없으면 첫 후보로.
            fun size(c: EGLConfig, attr: Int): Int {
                val v = IntArray(1)
                return if (egl.eglGetConfigAttrib(display, c, attr, v)) v[0] else 0
            }
            val exact = configs.filterNotNull().firstOrNull {
                size(it, EGL10.EGL_RED_SIZE) == 8 && size(it, EGL10.EGL_GREEN_SIZE) == 8 &&
                    size(it, EGL10.EGL_BLUE_SIZE) == 8 && size(it, EGL10.EGL_ALPHA_SIZE) == 8
            }
            (exact ?: configs.firstOrNull { it != null })?.let { return it }
        }
        throw IllegalArgumentException("No EGL config with RGBA8888 + depth")
    }
}

/**
 * 3D 행성(지구) 화면 — 지도 하단 "지구 보기" 버튼으로 진입하는 전체화면 오버레이.
 *
 * - 드래그: 행성 회전(관성), 3초 무입력 시 느린 자동 회전.
 * - 핀치: 카메라 줌([GlobeRenderer.MIN_DIST]~[GlobeRenderer.MAX_DIST] 클램프) — 화면 전환 없음.
 * - 화면 아래쪽 탭: 닫기(X) 버튼 표시 → 누르면 지금 정면 지점의 지도로 복귀.
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
    // 화면 아래쪽을 탭하면 나타나는 닫기(X) 버튼 — 잠시 후 자동 숨김
    var closeVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(DepthFirstConfigChooser)
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
                            // 핀치는 카메라 줌만 — 화면 전환(지도 복귀) 없음
                            renderer.camDist = (renderer.camDist / zoom)
                                .coerceIn(GlobeRenderer.MIN_DIST, GlobeRenderer.MAX_DIST)
                        }
                    }
                }
                .pointerInput(Unit) {
                    // 화면 아래쪽(55% 이하 영역) 탭 → 닫기(X) 버튼 표시
                    detectTapGestures { offset ->
                        if (offset.y >= size.height * 0.55f) closeVisible = true
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
                    .padding(bottom = 96.dp) // 닫기(X) 버튼 자리 위
                    .graphicsLayer { alpha = hintAlpha }
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 닫기(X) 버튼 — 아래쪽 탭으로 표시, 4초 무입력 시 자동 숨김. 누르면 지도 복귀.
        LaunchedEffect(closeVisible) {
            if (closeVisible) {
                delay(4000)
                closeVisible = false
            }
        }
        val closeAlpha by animateFloatAsState(
            targetValue = if (closeVisible) 1f else 0f,
            animationSpec = tween(250), label = "globe-close"
        )
        if (closeAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .graphicsLayer { alpha = closeAlpha }
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(enabled = closeVisible) { fireExit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.globe_close),
                    tint = Color.White
                )
            }
        }
    }
}
