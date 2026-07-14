package com.chaminwoo.stary.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 부메랑(3초 움짤) 프레임 처리 — ImageProxy → Bitmap 변환, 회전/미러/4:3 크롭/다운스케일,
 * 정→역 부메랑 시퀀스 구성, GIF 인코딩(파일).
 *
 * 인스타 부메랑처럼 **짧은 순간(약 1.5초, 12프레임)을 담아 앞으로→뒤로 이어 붙여**
 * 약 3초 길이의 무한 루프 GIF 로 만든다. 용량을 위해 저해상도(400×300)/감색(GifEncoder).
 */
object BoomerangHelper {
    /** 촬영으로 담는 프레임 수. */
    const val CAPTURE_FRAMES = 12

    /** 캡처 프레임 간격(ms). 12×125ms ≈ 1.5초의 순간을 담는다. */
    const val CAPTURE_INTERVAL_MS = 125L

    /** 재생 프레임 지연(1/100초). 22프레임 × 0.13s ≈ 2.9초 루프. */
    const val FRAME_DELAY_CS = 13

    /** 결과 GIF 폭(4:3 → 400×300). 다이어리 사진 프레임(ImageCropHelper.ASPECT)과 동일 비율. */
    const val TARGET_WIDTH = 400

    /** 결과 비율 — 업로드/상세 프레임과 동일(4:3). */
    const val ASPECT = 4f / 3f

    /** 캡처 프레임 작업 해상도(긴 변). 크롭은 촬영 후 조정 단계에서 하므로 전체 프레임을 이 크기로 보관. */
    const val WORK_MAX = 640

    /**
     * CameraX RGBA_8888 [ImageProxy] → [Bitmap].
     * rowStride 가 width*4 보다 클 수 있어 stride 폭으로 만든 뒤 실제 폭으로 잘라낸다.
     */
    fun toBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val strideWidth = plane.rowStride / plane.pixelStride
        val bmp = Bitmap.createBitmap(strideWidth, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        return if (strideWidth == image.width) bmp
        else Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
    }

    /**
     * 센서 프레임을 화면 그대로의 모습으로 정규화 —
     * [rotationDegrees] 회전 + (전면 카메라면) 좌우 미러 + 긴 변 [WORK_MAX] 다운스케일.
     * 4:3 크롭은 여기서 하지 않고, 촬영 후 조정 단계([cropFrames])에서 사용자가 정한다.
     */
    fun normalize(src: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val rotated = if (rotationDegrees != 0 || mirror) {
            val m = Matrix()
            if (rotationDegrees != 0) m.postRotate(rotationDegrees.toFloat())
            if (mirror) m.postScale(-1f, 1f)
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } else src

        val longest = maxOf(rotated.width, rotated.height)
        if (longest <= WORK_MAX) return rotated
        val s = WORK_MAX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * s).roundToInt().coerceAtLeast(1),
            (rotated.height * s).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * 조정 프레임(4:3) 안에서 촬영 원본 전체가 보이는 최소 배율 — cover 기준 상대값(≤ 1).
     * 이 값까지 축소할 수 있어야 **찍힌 화면이 하나도 잘리지 않는다**(남는 자리는 검은 여백).
     */
    fun minScaleFor(bmpW: Int, bmpH: Int, frameW: Float, frameH: Float): Float {
        if (bmpW <= 0 || bmpH <= 0 || frameW <= 0f || frameH <= 0f) return 1f
        val cover = maxOf(frameW / bmpW, frameH / bmpH)
        val fit = minOf(frameW / bmpW, frameH / bmpH)
        return (fit / cover).coerceIn(0.1f, 1f)
    }

    /**
     * 조정 단계의 크롭 상태로 모든 프레임을 4:3(400×300)으로 렌더한다.
     * 좌표 모델은 사진 크롭([ImageCropHelper.cropToFile])과 동일:
     * cover = max(frameW/bmpW, frameH/bmpH), disp = bmp × cover × scale, left = (frameW-dispW)/2 + offsetX.
     *
     * 잘라내기(createBitmap)가 아니라 **캔버스에 그대로 그린다** — 축소해서 프레임보다 작아진 경우
     * (전체 화각 담기)에도 화면에서 본 그대로 검은 여백과 함께 나온다.
     */
    fun cropFrames(
        frames: List<Bitmap>,
        frameW: Float, frameH: Float,
        scale: Float, offsetX: Float, offsetY: Float,
    ): List<Bitmap> {
        if (frameW <= 0f || frameH <= 0f) return frames
        val targetW = TARGET_WIDTH
        val targetH = (TARGET_WIDTH / ASPECT).roundToInt()
        // 조정 프레임(pt) → 결과 픽셀 배율. 프레임/결과 모두 4:3 이라 한 값으로 충분하다.
        val k = targetW / frameW
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        return frames.map { bmp ->
            val dispScale = maxOf(frameW / bmp.width, frameH / bmp.height) * scale
            val left = (frameW - bmp.width * dispScale) / 2f + offsetX
            val top = (frameH - bmp.height * dispScale) / 2f + offsetY

            val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(android.graphics.Color.BLACK)
                val m = Matrix().apply {
                    setScale(dispScale * k, dispScale * k)
                    postTranslate(left * k, top * k)
                }
                drawBitmap(bmp, m, paint)
            }
            out
        }
    }

    /** 부메랑 시퀀스 — 정방향 + 역방향(양 끝 중복 제거). 12장 → 22장. */
    fun boomerangSequence(capture: List<Bitmap>): List<Bitmap> =
        if (capture.size < 3) capture
        else capture + capture.reversed().drop(1).dropLast(1)

    /** 캡처 프레임을 부메랑 GIF 파일로 인코딩(cacheDir). */
    suspend fun encodeToFile(context: Context, capture: List<Bitmap>): File =
        withContext(Dispatchers.Default) {
            val seq = boomerangSequence(capture)
            val file = File.createTempFile("boomerang_", ".gif", context.cacheDir)
            file.outputStream().buffered().use { GifEncoder.encode(seq, FRAME_DELAY_CS, it) }
            file
        }
}
