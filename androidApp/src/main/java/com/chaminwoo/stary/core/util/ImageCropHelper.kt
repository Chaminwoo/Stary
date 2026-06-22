package com.chaminwoo.stary.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 다이어리 사진 크롭/로딩 유틸.
 *
 * 좌표 모델(업로드 크롭 프레임의 draw 와 동일):
 *   cover = max(frameW/bmpW, frameH/bmpH)   // 프레임을 항상 덮는 최소 배율
 *   dispScale = cover * scale               // scale 은 사용자 핀치(>=1)
 *   disp(W,H) = bmp(W,H) * dispScale
 *   left = (frameW - dispW)/2 + offsetX  (top 동일)
 * 이 모델 그대로 [cropToFile] 에서 역산해 잘라낸다.
 */
object ImageCropHelper {

    /** 다이어리 사진 고정 비율(가로/세로). 업로드 크롭 프레임과 디테일 표시가 공유한다. */
    const val ASPECT = 4f / 3f

    /** EXIF 회전 보정 + 다운샘플링하여 비트맵 로드. 실패 시 null. */
    fun loadDownsampled(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        return try {
            // 1) 경계만 디코드해 inSampleSize 계산(대형 사진 OOM 방지).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxDim) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // 2) EXIF 회전 보정.
            val rotation = context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotation == 0f) raw
            else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 현재 크롭 상태로 비트맵을 잘라 임시 jpg 로 저장하고 그 Uri 를 반환한다.
     * @param frameW,frameH 크롭 프레임 픽셀 크기 / @param scale,offsetX,offsetY 사용자 조작 값.
     */
    fun cropToFile(
        context: Context,
        bitmap: Bitmap,
        frameW: Float, frameH: Float,
        scale: Float, offsetX: Float, offsetY: Float,
        outWidth: Int = 1280,
    ): Uri? {
        return try {
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            if (frameW <= 0f || frameH <= 0f) return null

            val dispScale = max(frameW / bw, frameH / bh) * scale
            val dispW = bw * dispScale
            val dispH = bh * dispScale
            val left = (frameW - dispW) / 2f + offsetX
            val top = (frameH - dispH) / 2f + offsetY

            // 프레임(0..frameW, 0..frameH)에 대응하는 소스 사각형(px).
            var srcLeft = (-left) / dispScale
            var srcTop = (-top) / dispScale
            var srcW = frameW / dispScale
            var srcH = frameH / dispScale

            srcLeft = srcLeft.coerceIn(0f, bw - 1f)
            srcTop = srcTop.coerceIn(0f, bh - 1f)
            srcW = srcW.coerceIn(1f, bw - srcLeft)
            srcH = srcH.coerceIn(1f, bh - srcTop)

            val cropped = Bitmap.createBitmap(
                bitmap, srcLeft.roundToInt(), srcTop.roundToInt(),
                srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1)
            )
            val outHeight = (outWidth / (frameW / frameH)).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)

            val file = File.createTempFile("diary_crop_", ".jpg", context.cacheDir)
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }
}
