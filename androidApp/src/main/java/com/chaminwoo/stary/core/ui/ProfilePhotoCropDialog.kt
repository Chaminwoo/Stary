package com.chaminwoo.stary.core.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.ImageCropHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)

/** 프로필 사진 크롭 결과 한 변(px). 원형으로 표시되므로 정사각으로 잘라 올린다. */
private const val PROFILE_CROP_OUT_PX = 640

/**
 * 프로필 사진 조절 다이얼로그 — 고른 사진을 **정사각(원형 표시) 프레임 안에서
 * 드래그로 위치, 두 손가락으로 확대/축소**해 잘라낸 뒤 [onConfirm] 에 결과 Uri 를 넘긴다.
 *
 * 좌표 모델은 업로드 화면 사진 크롭([ImageCropHelper])과 동일:
 *   cover = max(frameW/bmpW, frameH/bmpH), disp = bmp × cover × scale,
 *   left = (frameW − dispW)/2 + offsetX  (offset 은 프레임 밖 빈자리가 안 생기게 클램프)
 *
 * (iOS 대응: `Features/Profile/ProfilePhotoCropView.swift` — 수치/동작 drift 금지.)
 */
@Composable
fun ProfilePhotoCropDialog(
    uri: Uri,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var frame by remember(uri) { mutableStateOf(IntSize.Zero) }
    var working by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { ImageCropHelper.loadDownsampled(context, uri) }
    }

    /** 핀치/드래그 반영 — 이미지가 프레임을 항상 덮도록 배율/이동을 제한한다. */
    fun onTransform(zoomChange: Float, panChange: Offset) {
        val bmp = bitmap ?: return
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        if (fw <= 0f || fh <= 0f) return
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        val dispScale = max(fw / bmp.width, fh / bmp.height) * scale
        val maxX = ((bmp.width * dispScale - fw) / 2f).coerceAtLeast(0f)
        val maxY = ((bmp.height * dispScale - fh) / 2f).coerceAtLeast(0f)
        val moved = offset + panChange
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }

    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Mint.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f))),
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Text(
                stringResource(R.string.profile_photo_adjust),
                color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.profile_photo_adjust_hint), color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            // 정사각 프레임 — 실제 프로필은 원형으로 보이므로 원형으로 마스킹해 그대로 미리 본다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(0xFF0D0D0D))
                    .clipToBounds()
                    .onSizeChanged { frame = it },
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp == null) {
                    StarLoadingIndicator(size = 28.dp, color = Color.White)
                } else {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
                            }
                    ) {
                        val fw = size.width
                        val fh = size.height
                        val dispScale = max(fw / bmp.width, fh / bmp.height) * scale
                        val dispW = bmp.width * dispScale
                        val dispH = bmp.height * dispScale
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(
                                ((fw - dispW) / 2f + offset.x).roundToInt(),
                                ((fh - dispH) / 2f + offset.y).roundToInt(),
                            ),
                            dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !working) {
                    Text(stringResource(R.string.common_cancel), color = TextMuted)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    enabled = bitmap != null && !working,
                    onClick = {
                        val bmp = bitmap ?: return@TextButton
                        working = true
                        val cropped = ImageCropHelper.cropToFile(
                            context, bmp,
                            frame.width.toFloat(), frame.height.toFloat(),
                            scale, offset.x, offset.y,
                            outWidth = PROFILE_CROP_OUT_PX,
                        )
                        working = false
                        // 크롭 실패(디코딩/저장 오류)면 원본을 그대로 올려 흐름이 끊기지 않게 한다.
                        onConfirm(cropped ?: uri)
                    }
                ) {
                    Text(stringResource(R.string.common_save), color = Mint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
