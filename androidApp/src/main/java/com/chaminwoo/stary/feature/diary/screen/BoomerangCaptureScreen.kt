package com.chaminwoo.stary.feature.diary.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

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

/** 촬영 화면 상태. */
private enum class BoomerStage { LIVE, CAPTURING, PROCESSING, REVIEW }

/**
 * 부메랑(3초 움짤) 커스텀 촬영 화면 — 전체 화면 오버레이.
 *
 * 하단에 카메라 전환 버튼 + 가운데 촬영 버튼. 촬영을 누르면 약 1.5초간 프레임을 모아
 * 정→역으로 이어 붙인 3초 GIF(저화질)로 만들고, 확인 후 [onResult] 로 파일을 돌려준다.
 */
@Composable
fun BoomerangCaptureScreen(
    onResult: (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(BoomerStage.LIVE) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var reviewFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val controller = remember { BoomerangCaptureController() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    // 캡처 완료(분석 스레드) → 메인에서 처리 단계로 전환 + GIF 인코딩
    controller.onComplete = { frames ->
        scope.launch(Dispatchers.Main) {
            stage = BoomerStage.PROCESSING
            val file = runCatching { BoomerangHelper.encodeToFile(context, frames) }.getOrNull()
            if (file != null) {
                resultFile = file
                reviewFrames = BoomerangHelper.boomerangSequence(frames)
                stage = BoomerStage.REVIEW
            } else {
                StaryToast.show(context.getString(R.string.boomer_failed))
                stage = BoomerStage.LIVE
            }
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
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
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

    BoomerangCaptureUi(
        stage = stage,
        previewView = previewView,
        captureProgress = controller.progress.intValue,
        reviewFrames = reviewFrames,
        onShoot = {
            if (stage == BoomerStage.LIVE) {
                stage = BoomerStage.CAPTURING
                controller.start()
            }
        },
        onFlip = {
            if (stage == BoomerStage.LIVE) {
                lensFacing =
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
            }
        },
        onRetake = {
            resultFile?.delete()
            resultFile = null
            reviewFrames = emptyList()
            stage = BoomerStage.LIVE
        },
        onUse = { resultFile?.let(onResult) },
        onClose = onClose,
    )
}

// ─── UI ──────────────────────────────────────────────────────────────────────

@Composable
private fun BoomerangCaptureUi(
    stage: BoomerStage,
    previewView: PreviewView,
    captureProgress: Int,
    reviewFrames: List<Bitmap>,
    onShoot: () -> Unit,
    onFlip: () -> Unit,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    onClose: () -> Unit,
) {
    val mint = Color(0xFF6EE7B7)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2050510))
            // 아래 화면으로 터치 통과 방지
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 바 — 제목 + 닫기
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.boomer_title),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            // 4:3 프레임 — 프리뷰 또는 결과 미리보기(움짤 재생)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BoomerangHelper.ASPECT)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (stage == BoomerStage.REVIEW && reviewFrames.isNotEmpty()) {
                    // 결과 미리보기 — 프레임 순환으로 부메랑 재생
                    var frameIdx by remember { mutableIntStateOf(0) }
                    LaunchedEffect(reviewFrames) {
                        while (true) {
                            delay(BoomerangHelper.FRAME_DELAY_CS * 10L)
                            frameIdx = (frameIdx + 1) % reviewFrames.size
                        }
                    }
                    Image(
                        bitmap = reviewFrames[frameIdx % reviewFrames.size].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    if (stage == BoomerStage.PROCESSING) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000)),
                        ) {
                            CircularProgressIndicator(color = mint, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.boomer_processing), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 안내 문구(대기) / 캡처 진행바
            Spacer(Modifier.height(14.dp))
            when (stage) {
                BoomerStage.LIVE -> Text(
                    stringResource(R.string.boomer_hint),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )
                BoomerStage.CAPTURING -> {
                    Text(
                        stringResource(R.string.boomer_capturing),
                        color = mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { captureProgress.toFloat() / BoomerangHelper.CAPTURE_FRAMES },
                        color = mint,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                }
                else -> {}
            }

            Spacer(Modifier.weight(1f))

            // 하단 컨트롤
            if (stage == BoomerStage.REVIEW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    ) {
                        Text(stringResource(R.string.boomer_retake), color = Color.White)
                    }
                    Button(
                        onClick = onUse,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mint),
                    ) {
                        Text(
                            stringResource(R.string.boomer_use),
                            color = Color(0xFF0D0D0D),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                // 하단 바 — 왼쪽 카메라 전환 + 가운데 촬영 버튼(요청 레이아웃)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                ) {
                    IconButton(
                        onClick = onFlip,
                        enabled = stage == BoomerStage.LIVE,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 40.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                    ) {
                        Icon(
                            Icons.Filled.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.cd_boomer_flip),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    // 촬영 버튼 — 이중 링, 캡처 중이면 민트 링
                    val capturing = stage == BoomerStage.CAPTURING
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(78.dp)
                            .clip(CircleShape)
                            .border(3.dp, if (capturing) mint else Color.White, CircleShape)
                            .clickable(enabled = stage == BoomerStage.LIVE) { onShoot() }
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
