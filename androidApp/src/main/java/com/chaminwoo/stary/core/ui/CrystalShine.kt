package com.chaminwoo.stary.core.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlinx.coroutines.delay
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 크리스탈 별 · 아이콘의 **빛번짐 + 유리 결 빛** — 지도 별(`starBitmap` 글로우 · 후광 · sheen)과 같은 연출을
 * 앱 곳곳의 크리스탈(별 아이콘 · 크리스탈 아이콘 · 공방 결정 · 배지)에 입힌다(2026-10-28 사용자 요청).
 *  - 빛번짐: 실루엣을 그 색으로 흐리게 두 번 겹친 테두리 빛([drawGlow]) + 아주 옅은 둥근 후광([drawHalo], 지도 후광 α .15).
 *    요소 바깥으로 번져 그린다 — 레이아웃 크기는 그대로.
 *  - 유리 결 빛: 요소마다 5.5~8초에 한 번, 흰 사선 빛줄기가 몸을 왼쪽 위 → 오른쪽 아래로 훑고 작은 굽은 반짝이가
 *    그 빛을 타고 건너간다([drawSheen]). 빛 방향은 화면 기준 — 돌아가는 아이콘도 빛은 늘 왼쪽 위에서 온다.
 * iOS `CrystalShine.swift` 와 같은 값. 지도 sheen(`DiaryMapMarkers` 의 SHEEN_*)도 이 값을 쓴다.
 */
object CrystalShine {
    /** 유리 결 빛 주기(초) = MIN + 0..SPREAD(요소마다), 지나가는 시간 = 주기 × WINDOW, 불투명도 OPACITY × sin. */
    const val SHEEN_PERIOD_MIN = 5.5f
    const val SHEEN_PERIOD_SPREAD = 2.5f
    const val SHEEN_WINDOW = 0.26f
    const val SHEEN_OPACITY = 0.75f

    /** 테두리 빛번짐 블러 반경(몸 한 변 대비) — 지도 마커(몸 125px 에 10px)보다 조금 넓게: 작은 아이콘에서도 번짐이 보이게. */
    private const val GLOW_BLUR = 0.14f
    /** 둥근 후광 반지름(몸 한 변 대비) · 가운데 불투명도(지도 후광 α .15). */
    private const val HALO_RADIUS = 1.0f
    private const val HALO_ALPHA = 0.15f

    // ── 때(유리 결 빛) ───────────────────────────────────────────────

    /** 요소마다 다른 주기(초) — [seed] 해시. */
    fun sheenPeriod(seed: Int): Double = SHEEN_PERIOD_MIN + hash01(seed, 0x51ED27) * SHEEN_PERIOD_SPREAD

    /** 요소마다 다른 위상(0..1). */
    fun sheenPhase(seed: Int): Double = hash01(seed, 0x2C1B3F)

    /** 단조 시계(초) — 유리 결 빛의 기준 시각. */
    fun nowSec(): Double = System.nanoTime() / 1e9

    /** [tSec] 에 유리 결 빛 진행(0..1) — 지나가는 중이 아니면 -1. 매 프레임 그리는 캔버스용. */
    fun sheenAt(tSec: Double, seed: Int): Float {
        val cyc = frac(tSec / sheenPeriod(seed) + sheenPhase(seed))
        return if (cyc < SHEEN_WINDOW) (cyc / SHEEN_WINDOW).toFloat() else -1f
    }

    internal fun frac(x: Double): Double = x - floor(x)

    /** 0..1 해시(murmur3 마무리 섞기) — 같은 seed 는 늘 같은 때. */
    private fun hash01(seed: Int, salt: Int): Double {
        var h = seed * -0x61c88647 + salt
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return (h ushr 8) / 16_777_216.0
    }

    // ── 빛번짐 ───────────────────────────────────────────────────────

    /** 빛번짐 마스크 — 흐린 실루엣의 알파(ALPHA_8, 두 번 겹쳐 진하게). 원본 [srcSize] px 정사각 자리에서 [padX]/[padY] px 씩 바깥까지. */
    class GlowMask internal constructor(val alpha: Bitmap, val srcSize: Int, val padX: Int, val padY: Int)

    private val starGlows = object : LruCache<Long, GlowMask>(3 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: GlowMask) = value.alpha.byteCount
    }
    private val bitmapGlows = WeakHashMap<Bitmap, GlowMask>()

    /** 별 모양 [type] 을 한 변 [sizePx] 로 그렸을 때의 빛번짐(4px 단위로 묶어 캐시 — 그릴 때 배율로 맞춘다). */
    fun starGlow(type: Int, sizePx: Float): GlowMask {
        val px = ((sizePx / 4f).roundToInt() * 4).coerceIn(8, 768)
        // 그릴 모양 번호로 — DB 모양을 나중에 배우면 새 키로 다시 굽는다(모르는 동안은 기본 별).
        val key = (StarStyle.safeType(type).toLong() shl 32) or px.toLong()
        starGlows.get(key)?.let { return it }
        val src = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8)
        android.graphics.Canvas(src).drawPath(StarStyle.starPath(type, px.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG))
        return blurred(src, px).also {
            src.recycle()
            starGlows.put(key, it)
        }
    }

    /** 구운 크리스탈 그림(정사각) 알파의 빛번짐 — 그림마다 1회(그림이 버려지면 같이 버려지는 약한 참조 캐시). */
    fun glowOf(bitmap: Bitmap): GlowMask = synchronized(bitmapGlows) {
        bitmapGlows[bitmap] ?: blurred(bitmap, maxOf(bitmap.width, bitmap.height)).also { bitmapGlows[bitmap] = it }
    }

    private fun blurred(src: Bitmap, srcSize: Int): GlowMask {
        val radius = (srcSize * GLOW_BLUR).coerceAtLeast(1.5f)
        val off = IntArray(2)
        val soft = src.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) }, off)
        // 두 번 겹쳐 진하게 — 지도 마커 글로우(같은 실루엣을 두 번 깐다)와 같은 느낌.
        val acc = Bitmap.createBitmap(soft.width, soft.height, Bitmap.Config.ALPHA_8)
        android.graphics.Canvas(acc).apply {
            drawBitmap(soft, 0f, 0f, null)
            drawBitmap(soft, 0f, 0f, null)
        }
        soft.recycle()
        return GlowMask(acc, srcSize, -off[0], -off[1])
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowRect = RectF()

    /** 테두리 빛번짐 — 몸이 (left, top, size) 정사각에 그려진다고 보고 그 둘레에 [color] 로. */
    fun drawGlow(canvas: android.graphics.Canvas, mask: GlowMask, color: Int, left: Float, top: Float, size: Float, alpha: Float = 1f) {
        val a = (android.graphics.Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        if (a == 0 || size <= 0f) return
        val k = size / mask.srcSize
        glowRect.set(
            left - mask.padX * k, top - mask.padY * k,
            left + (mask.alpha.width - mask.padX) * k, top + (mask.alpha.height - mask.padY) * k,
        )
        glowPaint.color = color
        glowPaint.alpha = a
        canvas.drawBitmap(mask.alpha, null, glowRect, glowPaint)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 둥근 후광 — 몸 가운데 (cx, cy), 몸 한 변 [size]. 가운데 α .15 에서 바깥으로 부드럽게 사라진다. */
    fun drawHalo(canvas: android.graphics.Canvas, color: Int, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val a = HALO_ALPHA * alpha * android.graphics.Color.alpha(color) / 255f
        val r = size * HALO_RADIUS
        if (a <= 0.003f || r <= 0f) return
        haloPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(color, a), withAlpha(color, a * 0.45f), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, haloPaint)
    }

    // ── 유리 결 빛 ───────────────────────────────────────────────────

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val sparkGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPath = Path()

    /** 마스크(몸 실루엣)를 그릴 때 쓰는 불투명 페인트 — 알파만 쓰이므로 색은 상관없다. */
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 유리 결 빛 한 장면(진행 [q] 0..1) — [mask] 가 그린 알파 안에서만 흰 사선 빛줄기가 지나가고, 작은 굽은 반짝이가 그 빛을 탄다.
     * (left, top, size) = 몸 정사각 자리(화면 기준 — 빛 방향은 회전과 무관). [mask] 는 이 캔버스에 몸 실루엣을 불투명하게 그린다
     * (회전 · 이동은 [mask] 안에서 자유롭게).
     * 수치는 지도 시안 drawSheen(마커 한 변 = 몸 / 0.78 인 128 캔버스에서 띠 중심 -46 + q·220, 반폭 30) 그대로.
     */
    fun drawSheen(
        canvas: android.graphics.Canvas,
        q: Float,
        left: Float,
        top: Float,
        size: Float,
        alpha: Float = 1f,
        mask: (android.graphics.Canvas) -> Unit,
    ) {
        if (q < 0f || size < 2f) return
        val e = sin(q * PI).toFloat()
        val a = SHEEN_OPACITY * e * alpha
        if (a < 0.004f) return
        val side = size / 0.78f
        val p = (-46f + q * 220f) / 128f * side - (side - size) / 2f
        val hw = 30f / 128f * side
        val pad = size * 0.3f
        val l = left - pad
        val t = top - pad
        val r = left + size + pad
        val b = top + size + pad
        val save = canvas.saveLayer(l, t, r, b, null)
        mask(canvas)
        bandPaint.shader = LinearGradient(
            left + p - hw, top + p - hw, left + p + hw, top + p + hw,
            intArrayOf(withAlpha(android.graphics.Color.WHITE, 0f), withAlpha(android.graphics.Color.WHITE, 0.95f * a), withAlpha(android.graphics.Color.WHITE, 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(l, t, r, b, bandPaint)
        canvas.restoreToCount(save)
        // 빛을 타고 건너가는 반짝(중심에서 (q - 0.5)·0.62·크기, 반지름 0.17·크기·sin)
        val rr = 0.17f * size * e
        if (rr > 0.5f) {
            val c = (q - 0.5f) * 0.62f * size
            val cx = left + size / 2f + c
            val cy = top + size / 2f + c
            sparkGlowPaint.shader = RadialGradient(
                cx, cy, rr * 0.9f,
                withAlpha(android.graphics.Color.WHITE, 0.5f * e * a), withAlpha(android.graphics.Color.WHITE, 0f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, rr * 0.9f, sparkGlowPaint)
            sparkPaint.color = withAlpha(android.graphics.Color.WHITE, 0.95f * e * a)
            canvas.drawPath(sparkle(cx, cy, rr), sparkPaint)
        }
    }

    /** 굽은 4갈래 반짝(수정구 시안 sparkleR — 지도 sheen 의 반짝과 같은 곡률). */
    private fun sparkle(cx: Float, cy: Float, r: Float): Path = sparkPath.apply {
        reset()
        val k = r * 0.10f
        moveTo(cx, cy - r)
        quadTo(cx + k, cy - k, cx + r, cy)
        quadTo(cx + k, cy + k, cx, cy + r)
        quadTo(cx - k, cy + k, cx - r, cy)
        quadTo(cx - k, cy - k, cx, cy - r)
        close()
    }

    // ── 그리기 도우미 ─────────────────────────────────────────────────

    private val starPaths = LruCache<Long, Path>(64)

    /** 별 모양 경로(한 변 [sizePx], 원점 0,0) — 마스크용 캐시. 돌려받은 경로는 고치지 말 것. */
    fun starPath(type: Int, sizePx: Float): Path {
        val key = (StarStyle.safeType(type).toLong() shl 32) or (sizePx * 4f).roundToInt().toLong()
        return starPaths.get(key) ?: StarStyle.starPath(type, sizePx).also { starPaths.put(key, it) }
    }

    /** 별 실루엣을 (left, top, size) 정사각에 [maskPaint] 로 — [drawSheen] 의 마스크. */
    fun drawStarMask(canvas: android.graphics.Canvas, type: Int, left: Float, top: Float, size: Float) {
        val save = canvas.save()
        canvas.translate(left, top)
        canvas.drawPath(starPath(type, size), maskPaint)
        canvas.restoreToCount(save)
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapRect = RectF()

    /** 정사각 그림을 (left, top, size) 에. */
    fun drawBitmap(canvas: android.graphics.Canvas, bitmap: Bitmap, left: Float, top: Float, size: Float, paint: Paint = bitmapPaint) {
        bitmapRect.set(left, top, left + size, top + size)
        canvas.drawBitmap(bitmap, null, bitmapRect, paint)
    }

    private fun withAlpha(color: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0xFFFFFF)
}

/**
 * 유리 결 빛 진행(0..1, 지나가는 중이 아니면 -1) — [seed] 마다 다른 때에. 지나가는 동안만 매 프레임 갱신하고 나머지 시간은 잔다.
 * ⚠️ 그리기 단계에서만 읽을 것(컴포지션에서 읽으면 지나가는 동안 매 프레임 재구성된다).
 */
@Composable
fun rememberSheenProgress(seed: Int): State<Float> {
    val q = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(seed) {
        val period = CrystalShine.sheenPeriod(seed)
        val phase = CrystalShine.sheenPhase(seed)
        val window = CrystalShine.SHEEN_WINDOW.toDouble()
        while (true) {
            val cyc = CrystalShine.frac(CrystalShine.nowSec() / period + phase)
            if (cyc < window) {
                q.floatValue = (cyc / window).toFloat()
                withFrameNanos { }
            } else {
                q.floatValue = -1f
                delay(((1.0 - cyc) * period * 1000.0).toLong().coerceAtLeast(16L))
            }
        }
    }
    return q
}

/**
 * 유리 결 빛 겹 — 자기 graphicsLayer 라서 지나가는 동안 매 프레임 다시 그려도 아래 크리스탈(파편 계산이 무겁다)은 다시 그리지 않는다.
 * 몸 자리 = 요소 가운데 정사각. [mask] 는 (캔버스, 왼쪽, 위, 한 변) 자리에 몸 실루엣을 불투명하게 그린다.
 * [seed] 가 없으면 요소마다 아무 때나(같은 목록의 같은 별도 따로 반짝이게).
 */
@Composable
fun CrystalSheen(
    modifier: Modifier,
    seed: Int? = null,
    alpha: Float = 1f,
    mask: (android.graphics.Canvas, Float, Float, Float) -> Unit,
) {
    val s = seed ?: remember { Random.nextInt() }
    val q = rememberSheenProgress(s)
    Canvas(modifier.graphicsLayer()) {
        val p = q.value
        if (p < 0f) return@Canvas
        val side = size.minDimension
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        drawIntoCanvas { c ->
            CrystalShine.drawSheen(c.nativeCanvas, p, left, top, side, alpha) { nc -> mask(nc, left, top, side) }
        }
    }
}

/**
 * 구운 크리스탈 그림(아이콘 · 배지 · 결정) + 빛번짐 · 유리 결 빛 — [androidx.compose.foundation.Image] 자리에 그대로 쓴다.
 * 그림은 요소 가운데 정사각에 맞춘다. [halo] = 둥근 후광까지(뒤에 이미 후광을 깐 자리는 false).
 */
@Composable
fun CrystalImage(
    bitmap: ImageBitmap,
    glowColor: Color,
    modifier: Modifier = Modifier,
    halo: Boolean = true,
    seed: Int? = null,
) {
    val bmp = bitmap.asAndroidBitmap()
    val glow = remember(bmp) { CrystalShine.glowOf(bmp) }
    val argb = glowColor.toArgb()
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val side = size.minDimension
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                if (halo) CrystalShine.drawHalo(nc, argb, left + side / 2f, top + side / 2f, side)
                CrystalShine.drawGlow(nc, glow, argb, left, top, side)
                CrystalShine.drawBitmap(nc, bmp, left, top, side)
            }
        }
        CrystalSheen(Modifier.matchParentSize(), seed, glowColor.alpha) { nc, l, t, s ->
            CrystalShine.drawBitmap(nc, bmp, l, t, s, CrystalShine.maskPaint)
        }
    }
}
