package com.chaminwoo.stary.feature.diary.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.util.BoomerangHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * 부메랑 캡처 컨트롤러 — ImageAnalysis 분석 스레드에서 프레임을 간격에 맞춰 모은다.
 * [capturing] 을 켜면 [BoomerangHelper.CAPTURE_FRAMES] 장을 모은 뒤 [onComplete] 호출.
 */
internal class BoomerangCaptureController {
    @Volatile var capturing = false
    @Volatile var mirror = false
    var onComplete: ((List<Bitmap>) -> Unit)? = null

    /** 캡처 진행(0..CAPTURE_FRAMES) — Compose 상태(스냅샷은 스레드 안전). */
    val progress = mutableIntStateOf(0)

    private var lastMs = 0L
    private val frames = ArrayList<Bitmap>(BoomerangHelper.CAPTURE_FRAMES)

    fun start() {
        frames.clear()
        progress.intValue = 0
        lastMs = 0L
        capturing = true
    }

    /** ImageAnalysis analyzer — 반드시 image.close() 보장. */
    fun onFrame(image: ImageProxy) {
        try {
            if (!capturing) return
            val now = SystemClock.elapsedRealtime()
            if (frames.isNotEmpty() && now - lastMs < BoomerangHelper.CAPTURE_INTERVAL_MS) return
            lastMs = now
            val bmp = BoomerangHelper.normalize(
                BoomerangHelper.toBitmap(image),
                image.imageInfo.rotationDegrees,
                mirror,
            )
            frames.add(bmp)
            progress.intValue = frames.size
            if (frames.size >= BoomerangHelper.CAPTURE_FRAMES) {
                capturing = false
                val done = frames.toList()
                frames.clear()
                onComplete?.invoke(done)
            }
        } catch (_: Exception) {
            // 프레임 하나 실패는 무시(다음 프레임에서 재시도)
        } finally {
            image.close()
        }
    }
}

/** ProcessCameraProvider 를 코루틴으로 획득. */
internal suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

/** 촬영 화면 상태. ADJUST = 촬영 후 크롭(위치/확대) 조정, ENCODING = GIF 생성 중. */
private enum class BoomerStage { LIVE, CAPTURING, ADJUST, ENCODING }

/**
 * 부메랑(3초 움짤) 커스텀 촬영 화면 — 전체 화면 오버레이.
 *
 * 프리뷰가 화면을 꽉 채우고, 하단에 카메라 전환 + 가운데 촬영 버튼만 둔다(텍스트/닫기 버튼 없음 —
 * 닫기는 시스템 뒤로가기). 촬영하면 약 1.5초간 프레임을 모으고, **사진 크롭처럼 드래그/핀치로
 * 4:3 프레임 안에서 위치·확대를 조정**한 뒤 확정하면 3초 GIF 로 만들어 [onResult] 로 돌려준다.
 */
@Composable
fun BoomerangCaptureScreen(
    onResult: (frames: List<Bitmap>, scale: Float, offsetNormX: Float, offsetNormY: Float) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(BoomerStage.LIVE) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    // 촬영된 원본(전체 화면) 프레임 — 조정 단계에서 크롭된다.
    var capturedFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val controller = remember { BoomerangCaptureController() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // FIT_CENTER — 프리뷰가 **실제로 담기는 화각 전체**를 보여준다(FILL_CENTER 는 화면을 채우려고
    // 좌우/상하를 잘라, 촬영 중 보이는 범위와 조정 단계에서 쓸 수 있는 범위가 어긋났다).
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    // 닫기 버튼 없음 — 시스템 뒤로가기로 닫는다.
    BackHandler { onClose() }

    // 캡처 완료(분석 스레드) → 곧바로 조정 단계(인코딩은 확정 시).
    controller.onComplete = { frames ->
        scope.launch(Dispatchers.Main) {
            capturedFrames = frames
            stage = BoomerStage.ADJUST
        }
    }

    // 권한 요청(없으면 진입 즉시)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) {
            StaryToast.show(context.getString(R.string.toast_camera_permission))
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 카메라 바인딩 — 렌즈 전환 시 재바인딩
    LaunchedEffect(hasPermission, lensFacing) {
        if (!hasPermission) return@LaunchedEffect
        val provider = awaitCameraProvider(context)
        cameraProvider = provider
        controller.mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
        // 프리뷰도 분석(캡처)과 같은 4:3 해상도로 묶는다 — 화각이 어긋나면 "보이는 것 ≠ 찍히는 것".
        @Suppress("DEPRECATION")
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(android.util.Size(640, 480))
            .build()
        analysis.setAnalyzer(analysisExecutor) { image -> controller.onFrame(image) }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview, analysis,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    val mint = Color(0xFF9FB3E8) // 남색 계열 라이트 강조(구 민트)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 아래 화면으로 터치 통과 방지
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        if (stage == BoomerStage.ADJUST || stage == BoomerStage.ENCODING) {
            BoomerangAdjustUi(
                frames = capturedFrames,
                encoding = stage == BoomerStage.ENCODING,
                mint = mint,
                onRetake = {
                    capturedFrames = emptyList()
                    stage = BoomerStage.LIVE
                },
                onConfirm = { frameW, frameH, cropScale, cropOffset ->
                    // 여기서 인코딩하지 않는다 — 업로드 화면에서도 위치·확대를 더 조절할 수 있어야 하므로
                    // **촬영 원본 프레임과 지금까지의 크롭 상태**를 그대로 넘기고, GIF 는 저장 직전에 만든다.
                    // offset 은 프레임 크기로 나눈 비율로 넘겨(프레임 폭이 달라도 같은 자리) 재현한다.
                    onResult(
                        capturedFrames,
                        cropScale,
                        if (frameW > 0f) cropOffset.x / frameW else 0f,
                        if (frameH > 0f) cropOffset.y / frameH else 0f,
                    )
                },
            )
        } else {
            // ── 촬영 모드 — 프리뷰 풀스크린 + 하단 컨트롤 ──
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stage == BoomerStage.CAPTURING) {
                    LinearProgressIndicator(
                        progress = { controller.progress.intValue.toFloat() / BoomerangHelper.CAPTURE_FRAMES },
                        color = mint,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                    Spacer(Modifier.height(18.dp))
                }

                // 하단 바 — 왼쪽 카메라 전환 + 가운데 촬영 버튼
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    IconButton(
                        onClick = {
                            if (stage == BoomerStage.LIVE) {
                                lensFacing =
                                    if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                                    else CameraSelector.LENS_FACING_BACK
                            }
                        },
                        enabled = stage == BoomerStage.LIVE,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 40.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            Icons.Filled.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.cd_boomer_flip),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    val capturing = stage == BoomerStage.CAPTURING
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(78.dp)
                            .clip(CircleShape)
                            .border(3.dp, if (capturing) mint else Color.White, CircleShape)
                            .clickable(enabled = stage == BoomerStage.LIVE) {
                                stage = BoomerStage.CAPTURING
                                controller.start()
                            }
                            .padding(7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(if (capturing) mint.copy(alpha = 0.5f) else Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AllInclusive,
                                contentDescription = stringResource(R.string.cd_boomer_shoot),
                                tint = Color(0xFF0D0D0D),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 촬영 후 조정 화면 — 4:3 프레임 안에서 움짤이 재생되고, 사진 크롭처럼 드래그/핀치로
 * 위치·확대를 정한다(좌표 모델 = 업로드 사진 크롭과 동일). 확정하면 그 상태로 잘라 GIF 생성.
 */
@Composable
private fun BoomerangAdjustUi(
    frames: List<Bitmap>,
    encoding: Boolean,
    mint: Color,
    onRetake: () -> Unit,
    onConfirm: (frameW: Float, frameH: Float, scale: Float, offset: Offset) -> Unit,
) {
    // 크롭 상태(사용자 조작) — UploadScreen CropController 와 동일 모델/클램프.
    var cropScale by remember(frames) { mutableFloatStateOf(1f) }
    var cropOffset by remember(frames) { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var fitApplied by remember(frames) { mutableStateOf(false) }

    // 처음엔 **찍힌 화면 전체**가 프레임에 들어오도록 맞춘다(잘림 없음). 여기서 핀치로 확대하면
    // 원하는 부분만 채워 담을 수 있다.
    LaunchedEffect(frames, frameSize) {
        val bmp = frames.firstOrNull() ?: return@LaunchedEffect
        if (fitApplied || frameSize.width <= 0 || frameSize.height <= 0) return@LaunchedEffect
        cropScale = BoomerangHelper.minScaleFor(
            bmp.width, bmp.height, frameSize.width.toFloat(), frameSize.height.toFloat(),
        )
        cropOffset = Offset.Zero
        fitApplied = true
    }

    // 부메랑 재생(정→역) 프레임 인덱스
    val playback = remember(frames) { BoomerangHelper.boomerangSequence(frames) }
    var frameIdx by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(playback) {
        if (playback.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(BoomerangHelper.FRAME_DELAY_CS * 10L)
            frameIdx = (frameIdx + 1) % playback.size
        }
    }

    fun onTransform(zoomChange: Float, panChange: Offset) {
        val bmp = frames.firstOrNull() ?: return
        val fw = frameSize.width.toFloat()
        val fh = frameSize.height.toFloat()
        if (fw <= 0f || fh <= 0f) return
        // 최소 배율까지 축소하면 촬영 원본이 통째로 들어온다(잘리는 부분 없음, 남는 자리는 검은 여백).
        val minScale = BoomerangHelper.minScaleFor(bmp.width, bmp.height, fw, fh)
        cropScale = (cropScale * zoomChange).coerceIn(minScale, 4f)
        val dispScale = maxOf(fw / bmp.width, fh / bmp.height) * cropScale
        val dispW = bmp.width * dispScale
        val dispH = bmp.height * dispScale
        val maxX = ((dispW - fw) / 2f).coerceAtLeast(0f)
        val maxY = ((dispH - fh) / 2f).coerceAtLeast(0f)
        val moved = cropOffset + panChange
        cropOffset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // 4:3 크롭 프레임 — 썸네일/상세에 보일 영역 그대로.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BoomerangHelper.ASPECT)
                .clipToBounds()
                .onSizeChanged { frameSize = it }
                .border(1.dp, Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.isNotEmpty()) {
                val current = playback[frameIdx % playback.size]
                val image = current.asImageBitmap()
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(frames) {
                            detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
                        }
                ) {
                    val fw = size.width
                    val fh = size.height
                    val dispScale = maxOf(fw / current.width, fh / current.height) * cropScale
                    val dispW = current.width * dispScale
                    val dispH = current.height * dispScale
                    val left = (fw - dispW) / 2f + cropOffset.x
                    val top = (fh - dispH) / 2f + cropOffset.y
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt()),
                    )
                    // 3분할 크롭 가이드(사진 크롭과 동일)
                    val guide = Color.White.copy(alpha = 0.35f)
                    val sw = 1.dp.toPx()
                    drawLine(guide, Offset(fw / 3f, 0f), Offset(fw / 3f, fh), sw)
                    drawLine(guide, Offset(fw * 2f / 3f, 0f), Offset(fw * 2f / 3f, fh), sw)
                    drawLine(guide, Offset(0f, fh / 3f), Offset(fw, fh / 3f), sw)
                    drawLine(guide, Offset(0f, fh * 2f / 3f), Offset(fw, fh * 2f / 3f), sw)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !encoding,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            ) {
                Text(stringResource(R.string.boomer_retake), color = Color.White)
            }
            Button(
                onClick = {
                    onConfirm(frameSize.width.toFloat(), frameSize.height.toFloat(), cropScale, cropOffset)
                },
                enabled = !encoding,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mint),
            ) {
                if (encoding) {
                    com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 20.dp, color = Color(0xFF0D0D0D))
                } else {
                    Text(
                        stringResource(R.string.boomer_use),
                        color = Color(0xFF0D0D0D),
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}
