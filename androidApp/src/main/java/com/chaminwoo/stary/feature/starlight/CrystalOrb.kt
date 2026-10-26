package com.chaminwoo.stary.feature.starlight

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import com.chaminwoo.stary.core.util.AppForeground
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 수정구(2026-10-26) — 별가루 화면의 우주 항해 무대와 항해 연출이 함께 쓰는 그림. 시안 "천문 원반 받침"(C안)을 옮겼다.
 *
 *  - 무대는 시안 단위 340 × 420(그리는 쪽이 px/단위 k 를 곱한다). 구슬 가운데 (170, 186), 반지름 94, 원반 윗면 y 344.
 *  - 받침: 법랑 원반(금 눈금 72개 · 동심원 · 별자리가 천천히 돈다) + 금속 목·띠 + 구슬을 받치는 고리(뒤 반쪽은 구슬 뒤, 앞 반쪽은 앞).
 *  - 구슬: 두 점 원뿔 그라데이션 유리(굽는다) 안에서 성운 덩어리 46개 · 별 티끌 24개가 돈다. 앞면에 프레넬 테 · 바닥 집광 ·
 *    부드러운 반사광 · 날카로운 빛줄. 일반 탐사 = 맑은 유리·푸른 빛·은, 희귀 탐사 = 연기 유리·금빛·금(경로를 바꾸면 0.6초 동안 섞인다).
 *  - 깨짐: 누른 자리에서 방사형 금 + 거미줄 금. 세 번째 두드림 뒤 1초 동안 결과 등급 빛으로 모이고(converge),
 *    누른 자리를 중심으로 보로노이로 자른 조각이 1.8초 동안 흩어진다(조각마다 깨지기 직전 구슬 그림을 그대로 입힌다).
 *    등급이 높을수록 빛이 넓고 화려하다(일반: 흰 빛 한 번 · 희귀: 빛줄기 8 · 영웅: 보랏빛 번쩍임 + 14 · 전설: 금빛 섬광 + 22 + 금가루).
 *  - 화면 번쩍임(영웅·전설)은 시스템 "애니메이션 끄기"면 생략하고 움직임도 느리게([OrbEngine.motion]).
 * 그리기는 android.graphics.Canvas — 'lighter'(더하기) 합성을 그대로 쓰려고. iOS `CrystalOrb.swift` 와 같은 수치.
 */

internal const val ORB_W = 340f
internal const val ORB_H = 420f
internal const val ORB_CX = 170f
internal const val ORB_CY = 186f
internal const val ORB_R = 94f
private const val DISK_Y = 344f
private const val TAU = (PI * 2).toFloat()
private const val PI_F = PI.toFloat()
private const val CONV = 1.0f
private const val SHAT = 1.8f
private const val GRAV = 240f
private const val RAD2DEG = (180.0 / PI).toFloat()

/** 깨진 뒤 이만큼(초) 지나면 카드가 솟는다 — 조각이 거의 다 떨어졌을 때. */
internal const val ORB_CARDS_AT = 1.5f

private const val WHITE = -0x1 // 0xFFFFFFFF
private const val BLACK = -0x1000000 // 0xFF000000

private fun rgb(r: Int, g: Int, b: Int): Int = BLACK or (r shl 16) or (g shl 8) or b
private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f) + .5f).toInt() shl 24) or (r shl 16) or (g shl 8) or b
private fun hexc(s: String): Int = (0xFF000000L or s.toLong(16)).toInt()

/** 색에 알파 덮어쓰기(0..1, 넘치면 자른다 — 시안 rgba() 와 같다). */
private fun alphaOf(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((((a.coerceIn(0f, 1f)) * 255f) + .5f).toInt() shl 24)

private fun mixc(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    fun ch(s: Int): Int {
        val x = (a ushr s) and 0xFF
        val y = (b ushr s) and 0xFF
        return (x + (y - x) * u).roundToInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun inC(t: Float) = t * t * t
private fun outC(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
private fun backOut(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}
private fun easeInOut(t: Float): Float = if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

/** 결과 등급 빛(시안 값 — 더하기 합성에 맞춘 색). */
internal fun burstColor(rarity: String): Int = when (rarity) {
    "rare" -> hexc("7CC8FF")
    "epic" -> hexc("B47CFF")
    "legendary" -> hexc("FFD27A")
    else -> hexc("EEF3FB")
}

internal fun rarityLevel(rarity: String): Int = when (rarity) {
    "rare" -> 1
    "epic" -> 2
    "legendary" -> 3
    else -> 0
}

/** 일반 탐사 = 맑은 유리·푸른 빛·은 / 희귀 탐사 = 연기 유리·금빛·금. [setLerp] 는 섞인 값을 제 자리에 쓴다. */
internal class OrbPalette(
    val fog: IntArray,
    var ink: Int,
    val body: IntArray,
    var rim: Int,
    var caustic: Int,
    var glow: Int,
    var star: Int,
    val metal: IntArray,
    val enamel: IntArray,
) {
    fun copy() = OrbPalette(fog.copyOf(), ink, body.copyOf(), rim, caustic, glow, star, metal.copyOf(), enamel.copyOf())

    fun setLerp(a: OrbPalette, b: OrbPalette, t: Float) {
        for (i in fog.indices) fog[i] = mixc(a.fog[i], b.fog[i], t)
        ink = mixc(a.ink, b.ink, t)
        for (i in body.indices) body[i] = mixc(a.body[i], b.body[i], t)
        rim = mixc(a.rim, b.rim, t)
        caustic = mixc(a.caustic, b.caustic, t)
        glow = mixc(a.glow, b.glow, t)
        star = mixc(a.star, b.star, t)
        for (i in metal.indices) metal[i] = mixc(a.metal[i], b.metal[i], t)
        for (i in enamel.indices) enamel[i] = mixc(a.enamel[i], b.enamel[i], t)
    }

    companion object {
        val NORMAL = OrbPalette(
            fog = intArrayOf(rgb(79, 125, 255), rgb(127, 176, 255), rgb(155, 123, 255), rgb(95, 211, 255)),
            ink = rgb(3, 5, 18),
            body = intArrayOf(argb(44, 66, 140, .2f), argb(14, 20, 52, .58f), argb(5, 8, 24, .92f)),
            rim = rgb(200, 220, 255), caustic = rgb(150, 195, 255), glow = rgb(90, 140, 255), star = rgb(221, 232, 255),
            metal = intArrayOf(hexc("F7F9FD"), hexc("C5CEDC"), hexc("8A94A8"), hexc("4E5668")),
            enamel = intArrayOf(hexc("1B2452"), hexc("080B20")),
        )
        val RARE = OrbPalette(
            fog = intArrayOf(rgb(255, 179, 71), rgb(255, 210, 122), rgb(255, 138, 92), rgb(255, 230, 168)),
            ink = rgb(10, 5, 3),
            body = intArrayOf(argb(90, 52, 30, .24f), argb(26, 14, 14, .66f), argb(8, 5, 7, .94f)),
            rim = rgb(255, 222, 160), caustic = rgb(255, 201, 112), glow = rgb(255, 159, 67), star = rgb(255, 233, 176),
            metal = intArrayOf(hexc("FFF2CC"), hexc("FFC970"), hexc("C98A3A"), hexc("6E461A")),
            enamel = intArrayOf(hexc("2E1C15"), hexc("0D0706")),
        )
    }
}

/** 한 번 굽고 함께 쓰는 것들 — 빛 번짐 스프라이트 · 색 필터 · 유리 몸통. */
private object OrbInk {
    val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)

    /** 흰 빛 번짐(시안 spr — 0:1 · .22:.6 · .55:.16 · 1:0). 색은 필터로 입힌다. */
    val sprite: Bitmap by lazy {
        val s = 128
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                64f, 64f, 64f,
                intArrayOf(WHITE, alphaOf(WHITE, .6f), alphaOf(WHITE, .16f), 0),
                floatArrayOf(0f, .22f, .55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        Canvas(b).drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        b
    }

    private val filters = HashMap<Int, PorterDuffColorFilter>()

    /** 6 단계로 묶어 캐시가 끝없이 늘지 않게(시안과 같은 양자화). */
    fun filter(c: Int): PorterDuffColorFilter {
        fun q(s: Int) = (((c ushr s) and 0xFF) / 6f).roundToInt().times(6).coerceAtMost(255)
        val key = BLACK or (q(16) shl 16) or (q(8) shl 8) or q(0)
        filters[key]?.let { return it }
        if (filters.size > 600) filters.clear()
        return PorterDuffColorFilter(key, PorterDuff.Mode.SRC_IN).also { filters[key] = it }
    }

    private val bodies = HashMap<OrbPalette, Bitmap>()

    /**
     * 유리 몸통 — 시안의 두 점 원뿔 그라데이션(안쪽 원 (-.25, -.3) r .05 → 바깥 원 (0, 0) r 1)을 직접 굽는다
     * (Android RadialGradient 는 API 31 전엔 한 점뿐이라 기기마다 다르게 보이지 않게). 색은 미리 곱한 알파로 섞는다(캔버스와 같게).
     */
    fun body(pal: OrbPalette): Bitmap = bodies.getOrPut(pal) {
        val n = 192
        val px = IntArray(n * n)
        val dcx = .25f; val dcy = .3f; val r0 = .05f; val dr = .95f
        val a = dcx * dcx + dcy * dcy - dr * dr
        val stops = floatArrayOf(0f, .72f, 1f)
        val cols = pal.body
        for (j in 0 until n) for (i in 0 until n) {
            val x = (i + .5f) / n * 2f - 1f
            val y = (j + .5f) / n * 2f - 1f
            val dist = sqrt(x * x + y * y)
            val cover = clamp01((1f - dist) * n / 2f + .5f)
            if (cover <= 0f) continue
            val qx = x + dcx; val qy = y + dcy
            val b = -2f * (qx * dcx + qy * dcy + r0 * dr)
            val c = qx * qx + qy * qy - r0 * r0
            val disc = b * b - 4f * a * c
            var t = 1f
            if (disc >= 0f) {
                val s = sqrt(disc)
                val t1 = (-b + s) / (2f * a)
                val t2 = (-b - s) / (2f * a)
                val best = max(t1, t2)
                t = if (r0 + best * dr >= 0f) best else min(t1, t2)
            }
            t = clamp01(t)
            val k = if (t <= stops[1]) 0 else 1
            val u = (t - stops[k]) / (stops[k + 1] - stops[k])
            val c0 = cols[k]; val c1 = cols[k + 1]
            val a0 = ((c0 ushr 24) and 0xFF) / 255f; val a1 = ((c1 ushr 24) and 0xFF) / 255f
            val al = a0 + (a1 - a0) * u
            fun ch(sh: Int): Int {
                val v0 = ((c0 ushr sh) and 0xFF) * a0
                val v1 = ((c1 ushr sh) and 0xFF) * a1
                val pm = v0 + (v1 - v0) * u
                return if (al <= 0f) 0 else (pm / al).roundToInt().coerceIn(0, 255)
            }
            px[j * n + i] = (((al * cover) * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }
}

private class OrbRay(val xs: FloatArray, val ys: FloatArray, val cum: FloatArray, val start: Float, val len: Float) {
    val n get() = xs.size
}
private class OrbRingSeg(val ax: Float, val ay: Float, val mx: Float, val my: Float, val bx: Float, val by: Float, val d: Float)
private class OrbImpact(val x: Float, val y: Float, val rays: List<OrbRay>, val rings: List<OrbRingSeg>, var target: Float) {
    var g = 0f
}
private class OrbShard(
    val path: Path, val x0: Float, val y0: Float, val vx: Float, val vy: Float,
    val vr: Float, val vt: Float, val gp: Float, val life: Float, val delay: Float, val shader: BitmapShader,
)
private class OrbGrit(val x0: Float, val y0: Float, val vx: Float, val vy: Float, val s: Float, val life: Float, val ph: Float)
private class OrbBlob(val rr: Float, val a: Float, val w: Float, val h: Float, val s: Float, val c: Int, val ph: Float, val dark: Boolean)
private class OrbMote(val rr: Float, val a: Float, val w: Float, val h: Float, val s: Float, val ph: Float, val f: Float)
private class OrbSpark(val a: Float, val v: Float, val s: Float, val g: Float, val ph: Float, val lv: Int, val gold: Boolean)
private class OrbGlit(val a: Float, val r: Float, val w: Float, val y: Float, val v: Float, val s: Float, val ph: Float)

/**
 * 수정구 한 개의 상태와 그림. 프레임마다 [step] → [draw].
 * [interactive] = false(별가루 화면 무대)면 두드려도 금이 가지 않고 빛만 출렁인다.
 */
internal class OrbEngine(seed: Int, private val interactive: Boolean) {
    enum class Phase { IDLE, WAIT, CONVERGE, SHATTER, REVEAL }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var taps by mutableIntStateOf(0)
        private set
    /** 깨진 뒤 [ORB_CARDS_AT] 이 지났거나 건너뛰었다 — 카드를 띄울 때. */
    var cardsReady by mutableStateOf(false)
        private set

    /** 1 = 보통, 0.35 = 시스템 "애니메이션 끄기"(시안 prefers-reduced-motion). */
    var motion = 1f
    /** 영웅·전설 화면 번쩍임 — 애니메이션을 끈 사람에겐 끈다. */
    var flashOk = true
    /** 결과 등급이 정해져 모이기 시작할 때 / 깨지는 순간(등급 0..3) — 진동은 부르는 쪽이. */
    var onConverge: (() -> Unit)? = null
    var onShatter: ((Int) -> Unit)? = null

    var t = 0f
        private set
    private var clock = 0f
    private var pulse = 0f
    private var pt = 0f
    private var conv = 0f
    private var lv = 0
    private var rc = WHITE
    private var pendingRarity: String? = null

    private var from = OrbPalette.NORMAL
    private var to = OrbPalette.NORMAL
    private var mixT = 1f
    private val pal = OrbPalette.NORMAL.copy()
    private var palVer = 0

    private val rnd = Random(seed)
    private val blobs = List(46) {
        OrbBlob(.12f + .78f * sqrt(rnd.nextFloat()), rnd.nextFloat() * TAU, .22f + .34f * rnd.nextFloat(), (rnd.nextFloat() - .5f) * 1.1f,
            .2f + .3f * rnd.nextFloat(), rnd.nextInt(4), rnd.nextFloat() * TAU, rnd.nextFloat() < .24f)
    }
    private val motes = List(24) {
        OrbMote(.1f + .78f * rnd.nextFloat(), rnd.nextFloat() * TAU, .3f + .4f * rnd.nextFloat(), (rnd.nextFloat() - .5f) * 1.3f,
            .6f + 1.2f * rnd.nextFloat(), rnd.nextFloat() * TAU, 1.5f + 3f * rnd.nextFloat())
    }
    private val sparks = List(110) { i ->
        OrbSpark(rnd.nextFloat() * TAU, .4f + rnd.nextFloat() * 1.1f, 1.5f + rnd.nextFloat() * 3.5f, rnd.nextFloat(), rnd.nextFloat() * TAU,
            if (i < 8) 0 else if (i < 22) 1 else if (i < 50) 2 else 3, rnd.nextFloat() < .5f)
    }
    private val glit = List(26) {
        OrbGlit(rnd.nextFloat() * TAU, .2f + .9f * rnd.nextFloat(), (rnd.nextFloat() - .5f) * .6f, rnd.nextFloat(), .12f + .15f * rnd.nextFloat(),
            1.5f + 2.5f * rnd.nextFloat(), rnd.nextFloat() * TAU)
    }

    private val impacts = ArrayList<OrbImpact>()
    private var shards: List<OrbShard> = emptyList()
    private var dust: List<OrbGrit> = emptyList()
    private var needShards = false
    private var bx = ORB_CX
    private var by = ORB_CY

    // 다시 쓰는 그리기 도구(프레임마다 새로 만들지 않는다)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pShard = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val view = RectF()
    private val path = Path()
    private val crack = Path()
    private val ticksMajor = FloatArray(12 * 4)
    private val ticksMinor = FloatArray(60 * 4)
    private val starXs = FloatArray(9)
    private val starYs = FloatArray(9)
    private val gradCache = HashMap<Long, Shader>()
    private var gradVer = -1
    private var enamelShader: Shader? = null
    private var enamelVer = -1
    private val shadowShader = RadialGradient(0f, 0f, 150f, intArrayOf(argb(0, 0, 0, .55f), 0), null, Shader.TileMode.CLAMP)

    private val going get() = phase == Phase.CONVERGE || phase == Phase.SHATTER || phase == Phase.REVEAL

    /** 깨지기 시작한 뒤 흐른 시간(초), 깨지기 전엔 -1. */
    val shatterAge: Float get() = when (phase) {
        Phase.SHATTER -> pt
        Phase.REVEAL -> SHAT + pt
        else -> -1f
    }

    /** 경로 색 — [animate] 면 0.6초 동안 섞는다(무대에서 경로를 바꿀 때). */
    fun setRoute(rare: Boolean, animate: Boolean) {
        val target = if (rare) OrbPalette.RARE else OrbPalette.NORMAL
        if (target === to && mixT >= 1f) return
        if (animate) {
            from = if (mixT >= 1f) to else OrbPalette.NORMAL.copy().also { it.setLerp(from, to, easeInOut(mixT)) }
            to = target
            mixT = 0f
        } else {
            from = target; to = target; mixT = 1f
            pal.setLerp(target, target, 1f)
            palVer++
        }
    }

    /** 결과 등급 — 세 번 두드린 뒤라면 바로 모이기 시작한다. */
    fun setResult(rarity: String) {
        pendingRarity = rarity
        if (phase == Phase.WAIT) startConverge()
    }

    /** 건너뛰기 — 조각 없이 결과 빛만 남기고 카드로. */
    fun skip() {
        val r = pendingRarity ?: return
        lv = rarityLevel(r); rc = burstColor(r)
        shards = emptyList(); dust = emptyList(); needShards = false
        phase = Phase.REVEAL
        pt = 3f
        cardsReady = true
    }

    /** 무대에서 두드림 — 시안 단위 좌표(null = 아무 데나). 금이 갔으면 true. */
    fun tap(ux: Float?, uy: Float?): Boolean {
        pulse = 1f
        if (!interactive || phase != Phase.IDLE || taps >= 3) return false
        var x = if (ux == null) 9f else (ux - ORB_CX) / ORB_R
        var y = if (uy == null) 9f else (uy - ORB_CY) / ORB_R
        if (hypot(x, y) > .9f) {
            val a = rnd.nextFloat() * TAU
            val d = .15f + rnd.nextFloat() * .45f
            x = cos(a) * d; y = sin(a) * d
        }
        taps++
        when (taps) {
            1 -> impacts += makeImpact(x, y, .32f)
            2 -> { impacts[0].target = .7f; impacts += makeImpact(x, y, .36f) }
            else -> {
                impacts += makeImpact(x, y, .25f)
                if (pendingRarity != null) startConverge()
                else {
                    // 결과를 기다리는 동안 — 금이 천천히 번지고 구슬이 떤다.
                    phase = Phase.WAIT
                    impacts.forEach { it.target = max(it.target, .9f) }
                }
            }
        }
        return true
    }

    private fun startConverge() {
        val r = pendingRarity ?: return
        lv = rarityLevel(r); rc = burstColor(r)
        impacts.forEach { it.target = 1.5f }
        phase = Phase.CONVERGE
        pt = 0f
        onConverge?.invoke()
    }

    fun step(dt: Float) {
        t += dt
        pulse = max(0f, pulse - dt * 1.8f)
        if (mixT < 1f) {
            mixT = min(1f, mixT + dt / .6f)
            pal.setLerp(from, to, easeInOut(mixT))
            palVer++
        }
        val rate = when (phase) {
            Phase.CONVERGE -> 2.6f
            Phase.WAIT -> 1.2f
            else -> 8f
        }
        for (im in impacts) im.g += (im.target - im.g) * min(1f, dt * rate)
        when (phase) {
            Phase.CONVERGE -> {
                pt += dt
                if (pt >= CONV) { phase = Phase.SHATTER; pt = 0f; needShards = true; onShatter?.invoke(lv) }
            }
            Phase.SHATTER -> { pt += dt; if (pt >= SHAT) { phase = Phase.REVEAL; pt = 0f } }
            Phase.REVEAL -> pt += dt
            else -> {}
        }
        conv = when (phase) {
            Phase.CONVERGE -> clamp01(pt / CONV)
            Phase.IDLE, Phase.WAIT -> 0f
            else -> 1f
        }
        clock += dt * motion * (1f + taps * 1.2f + pulse * 2.6f + (if (phase == Phase.CONVERGE) conv * 10f else 0f) + (if (phase == Phase.WAIT) 1.5f else 0f))
        if (!cardsReady && shatterAge >= ORB_CARDS_AT) cardsReady = true
    }

    /**
     * 한 프레임 — 캔버스 px 좌표에서 무대 원점이 ([ox], [oy]), 시안 1단위 = [k] px. [viewW]×[viewH] 는 화면 크기(번쩍임용).
     * 카드가 뜨면 [stageAlpha] 로 받침·문장을 지우고, 후광은 [auraShift] 만큼 ([auraX], [auraY])(시안 단위)로 옮긴다.
     */
    fun draw(
        c: Canvas, k: Float, ox: Float, oy: Float, viewW: Float, viewH: Float,
        stageAlpha: Float = 1f, auraShift: Float = 0f, auraX: Float = ORB_CX, auraY: Float = ORB_CY,
    ) {
        c.save()
        c.translate(ox, oy)
        c.scale(k, k)
        view.set(-ox / k, -oy / k, (viewW - ox) / k, (viewH - oy) / k)
        val layered = stageAlpha < .999f
        if (layered) c.saveLayerAlpha(view, (clamp01(stageAlpha) * 255).toInt())
        if (stageAlpha > 0f) standBack(c)
        if (layered) c.restore()
        when (phase) {
            Phase.IDLE, Phase.WAIT, Phase.CONVERGE -> {
                var x = ORB_CX
                var y = ORB_CY
                val j = when (phase) {
                    Phase.CONVERGE -> conv * 2f
                    Phase.WAIT -> .7f
                    else -> 0f
                }
                if (j > 0f) { x += (rnd.nextFloat() - .5f) * j; y += (rnd.nextFloat() - .5f) * j }
                drawBall(c, x, y, layered = true)
                bx = x; by = y
                collar(c, ORB_CX, ORB_CY + ORB_R * .9f, front = true)
            }
            Phase.SHATTER, Phase.REVEAL -> {
                if (needShards) buildShards(k)
                val s = shatterAge
                val ax = bx + (auraX - bx) * auraShift
                val ay = by + (auraY - by) * auraShift
                aura(c, ax, ay, ORB_R, clamp01((s - .4f) / 1f) * (1f - .35f * auraShift))
                if (stageAlpha > 0f) {
                    if (layered) c.saveLayerAlpha(view, (clamp01(stageAlpha) * 255).toInt())
                    emblem(c, bx, by, ORB_R * .28f * backOut(clamp01((s - .06f) / .5f)), rc, clamp01(s / .25f))
                    collar(c, ORB_CX, ORB_CY + ORB_R * .9f, front = true)
                    if (layered) c.restore()
                }
                if (phase == Phase.SHATTER) {
                    drawShards(c, s)
                    rarityBurst(c, bx, by, ORB_R, s)
                }
            }
        }
        c.restore()
    }

    // ── 빛·반짝이 ──────────────────────────────────────────────────────────

    private fun glow(c: Canvas, col: Int, x: Float, y: Float, r: Float, a: Float, add: Boolean) {
        if (a <= 0f || r <= 0f) return
        pGlow.colorFilter = OrbInk.filter(col)
        pGlow.alpha = (min(a, 1f) * 255f).toInt()
        pGlow.xfermode = if (add) OrbInk.ADD else null
        rect.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(OrbInk.sprite, null, rect, pGlow)
    }

    /** 네 꼭지 반짝이(가운데를 조절점으로 휜 변). */
    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, a: Float, col: Int, add: Boolean) {
        if (a <= 0f || r <= 0f) return
        path.rewind()
        path.moveTo(x, y - r)
        path.quadTo(x, y, x + r, y)
        path.quadTo(x, y, x, y + r)
        path.quadTo(x, y, x - r, y)
        path.quadTo(x, y, x, y - r)
        path.close()
        pFill.shader = null
        pFill.xfermode = if (add) OrbInk.ADD else null
        pFill.color = alphaOf(col, a)
        c.drawPath(path, pFill)
        pFill.xfermode = null
    }

    private fun metalGrad(x0: Float, x1: Float): Shader {
        if (gradVer != palVer) { gradCache.clear(); gradVer = palVer }
        val key = (x0.toRawBits().toLong() shl 32) or (x1.toRawBits().toLong() and 0xFFFFFFFFL)
        return gradCache.getOrPut(key) {
            val m = pal.metal
            LinearGradient(
                x0, 0f, x1, 0f,
                intArrayOf(m[3], m[1], m[0], m[2], m[1], m[0], m[3]),
                floatArrayOf(0f, .15f, .32f, .5f, .7f, .86f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }

    // ── 받침(천문 원반) ─────────────────────────────────────────────────────

    private fun standBack(c: Canvas) {
        val p = pal
        val m = p.metal
        val cx = ORB_CX
        val y = DISK_Y
        val rx = 126f
        val ry = 25f
        val h = 11f
        val f = ry / rx
        val lc = if (going) mixc(p.glow, rc, conv) else p.glow
        // 바닥 그림자 + 은은한 바닥 빛
        c.save(); c.translate(cx, y + h + 8f); c.scale(1f, .15f)
        pFill.xfermode = null; pFill.shader = shadowShader; pFill.color = BLACK
        c.drawCircle(0f, 0f, 150f, pFill)
        pFill.shader = null
        glow(c, p.glow, 0f, 0f, 150f * 1.25f, .1f, add = true)
        c.restore()
        // 두께 있는 원반(아래 면 → 옆면 → 법랑 윗면 → 앞 테두리 빛)
        pFill.color = m[3]
        rect.set(cx - rx, y + h - ry, cx + rx, y + h + ry)
        c.drawOval(rect, pFill)
        pFill.shader = metalGrad(cx - rx, cx + rx); pFill.color = BLACK
        c.drawRect(cx - rx, y, cx + rx, y + h, pFill)
        c.drawArc(rect, 0f, 180f, false, pFill)
        if (enamelVer != palVer || enamelShader == null) {
            enamelShader = RadialGradient(cx, y - 3f, rx, intArrayOf(p.enamel[0], p.enamel[1]), null, Shader.TileMode.CLAMP)
            enamelVer = palVer
        }
        pFill.shader = enamelShader
        rect.set(cx - rx, y - ry, cx + rx, y + ry)
        c.drawOval(rect, pFill)
        pFill.shader = null
        pStroke.shader = null; pStroke.xfermode = null; pStroke.strokeCap = Paint.Cap.BUTT
        pStroke.color = alphaOf(m[0], .7f); pStroke.strokeWidth = .8f
        c.drawArc(rect, .08f * RAD2DEG, (PI_F - .16f) * RAD2DEG, false, pStroke)
        // 원반 위 빛 웅덩이(두드릴수록 · 깨질 때 밝아진다)
        c.save(); c.translate(cx, y); c.scale(1f, f)
        glow(c, lc, 0f, 0f, rx * .75f, .2f + taps * .06f + pulse * .2f + (if (going) .3f else 0f), add = true)
        c.restore()
        // 동심원
        for ((kk, a) in RINGS) {
            pStroke.color = alphaOf(m[1], a); pStroke.strokeWidth = .8f
            rect.set(cx - rx * kk, y - ry * kk, cx + rx * kk, y + ry * kk)
            c.drawOval(rect, pStroke)
        }
        // 눈금 72개(큰 눈금 6칸마다) — 천천히 돈다
        val rot = t * .05f * motion
        var mi = 0
        var ni = 0
        for (i in 0 until 72) {
            val a = i / 72f * TAU + rot
            val major = i % 6 == 0
            val r0 = rx * (if (major) .8f else .86f)
            val r1 = rx * .93f
            val ca = cos(a); val sa = sin(a)
            val arr = if (major) ticksMajor else ticksMinor
            val o = if (major) mi else ni
            arr[o] = cx + ca * r0; arr[o + 1] = y + sa * r0 * f; arr[o + 2] = cx + ca * r1; arr[o + 3] = y + sa * r1 * f
            if (major) mi += 4 else ni += 4
        }
        pStroke.color = alphaOf(m[1], .6f); pStroke.strokeWidth = .9f
        c.drawLines(ticksMajor, pStroke)
        pStroke.color = alphaOf(m[1], .32f); pStroke.strokeWidth = .6f
        c.drawLines(ticksMinor, pStroke)
        // 별자리 — 눈금과 반대로 돈다
        for (i in 0 until 9) {
            val a = STAR_ANGLES[i] - rot * 1.4f
            val r = rx * (.66f + (i % 3) * .035f)
            starXs[i] = cx + cos(a) * r; starYs[i] = y + sin(a) * r * f
        }
        pStroke.color = alphaOf(m[1], .28f); pStroke.strokeWidth = .6f
        for (e in STAR_LINES) c.drawLine(starXs[e[0]], starYs[e[0]], starXs[e[1]], starYs[e[1]], pStroke)
        for (i in 0 until 9) {
            glow(c, m[1], starXs[i], starYs[i], 5f, .5f, add = true)
            sparkle(c, starXs[i], starYs[i], 2f + (i % 3), .7f + .3f * sin(t * 2f + i), m[0], add = true)
        }
        // 목과 띠
        val top = ORB_CY + ORB_R * .9f
        pFill.shader = metalGrad(cx - 18f, cx + 18f); pFill.color = BLACK
        path.rewind()
        path.moveTo(cx - 11f, top); path.lineTo(cx + 11f, top); path.lineTo(cx + 17f, y); path.lineTo(cx - 17f, y); path.close()
        c.drawPath(path, pFill)
        pFill.shader = null; pFill.color = argb(0, 0, 0, .25f)
        rect.set(cx - 17f, y - 3.4f, cx + 17f, y + 3.4f)
        c.drawOval(rect, pFill)
        for (yy in floatArrayOf(y - 12f, top + 14f)) {
            val w = 11f + (yy - top) / (y - top) * 6f + 2f
            pFill.shader = metalGrad(cx - w, cx + w); pFill.color = BLACK
            rect.set(cx - w, yy - 2.6f, cx + w, yy + 2.6f)
            c.drawOval(rect, pFill)
            pFill.shader = null
            pStroke.color = alphaOf(m[0], .7f); pStroke.strokeWidth = .6f
            c.drawArc(rect, .1f * RAD2DEG, (PI_F - .2f) * RAD2DEG, false, pStroke)
        }
        collar(c, cx, top, front = false)
    }

    /** 구슬을 받치는 고리 — 뒤 반쪽(구슬 뒤) / 앞 반쪽(구슬 앞, 위 모서리 빛). */
    private fun collar(c: Canvas, cx: Float, y: Float, front: Boolean) {
        val m = pal.metal
        val rx = 40f
        val ry = 8.5f
        val alpha = if (front) 1f else .8f
        rect.set(cx - rx, y - ry, cx + rx, y + ry)
        val start = if (front) 0f else 180f
        pStroke.xfermode = null; pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.shader = null; pStroke.color = alphaOf(m[3], alpha); pStroke.strokeWidth = 5f
        c.drawArc(rect, start, 180f, false, pStroke)
        pStroke.shader = metalGrad(cx - rx, cx + rx); pStroke.color = alphaOf(BLACK, alpha); pStroke.strokeWidth = 3.6f
        c.drawArc(rect, start, 180f, false, pStroke)
        pStroke.shader = null
        if (front) {
            rect.set(cx - rx, y - 1f - ry, cx + rx, y - 1f + ry)
            pStroke.color = alphaOf(m[0], .9f); pStroke.strokeWidth = .8f
            c.drawArc(rect, .2f * RAD2DEG, (PI_F - .4f) * RAD2DEG, false, pStroke)
        }
        pStroke.strokeCap = Paint.Cap.BUTT
    }

    // ── 유리구슬 ───────────────────────────────────────────────────────────

    private fun drawBody(c: Canvas, cx: Float, cy: Float, r: Float) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        pBody.xfermode = null
        if (mixT >= 1f) {
            pBody.alpha = 255
            c.drawBitmap(OrbInk.body(to), null, rect, pBody)
        } else {
            val e = easeInOut(mixT)
            pBody.alpha = ((1f - e) * 255f).toInt()
            c.drawBitmap(OrbInk.body(from.takeIf { it === OrbPalette.NORMAL || it === OrbPalette.RARE } ?: if (to === OrbPalette.RARE) OrbPalette.NORMAL else OrbPalette.RARE), null, rect, pBody)
            pBody.alpha = (e * 255f).toInt()
            c.drawBitmap(OrbInk.body(to), null, rect, pBody)
        }
    }

    /**
     * 구슬 한 장 — [layered] 면 구슬 칸만 따로 그려 합친다(시안처럼 성운 빛이 무대가 아니라 유리에만 더해지게,
     * 그리고 깨질 때 찍는 사진과 같은 모습이 되게).
     */
    private fun drawBall(c: Canvas, cx: Float, cy: Float, layered: Boolean) {
        val p = pal
        val r = ORB_R
        val cv = inC(conv)
        val g = going
        glow(c, p.glow, cx, cy, r * 2.1f, .2f, add = true)
        if (layered) {
            rect.set(cx - r - 2f, cy - r - 2f, cx + r + 2f, cy + r + 2f)
            c.saveLayer(rect, null)
        }
        drawBody(c, cx, cy, r)
        c.save()
        path.rewind()
        path.addCircle(cx, cy, r * .985f, Path.Direction.CW)
        c.clipPath(path)
        for (b in blobs) {
            val a = b.a + clock * b.w * (1.25f - b.rr * .55f)
            val rr = b.rr * (1f - cv * .95f)
            val d = sin(a)
            val x = cx + cos(a) * rr * r + sin(t * .6f + b.ph) * r * .035f
            val y = cy + b.h * r * .62f * (1f - cv) + d * rr * r * .3f
            val sz = b.s * r * (.8f + .25f * d) * (1f - cv * .55f)
            if (b.dark) glow(c, p.ink, x, y, sz * 1.1f, .42f * (1f - cv), add = false)
            else glow(
                c, if (g) mixc(p.fog[b.c], rc, cv) else p.fog[b.c], x, y, sz,
                (.11f + .035f * (d + 1f)) * (1f + pulse * .7f + cv * 1.2f), add = true,
            )
        }
        for (s in motes) {
            val a = s.a + clock * s.w
            val rr = s.rr * (1f - cv)
            val d = sin(a)
            val x = cx + cos(a) * rr * r
            val y = cy + s.h * r * .6f * (1f - cv) + d * rr * r * .3f
            val tw = .5f + .5f * sin(t * s.f + s.ph)
            glow(c, if (g) mixc(p.star, rc, cv) else p.star, x, y, r * .045f * s.s * (1f + d * .3f), .35f * tw, add = true)
            pFill.shader = null; pFill.xfermode = OrbInk.ADD; pFill.color = alphaOf(WHITE, .4f + .6f * tw)
            c.drawRect(x - .6f, y - .6f, x + .6f, y + .6f, pFill)
            pFill.xfermode = null
            if (s.s > 1.5f) sparkle(c, x, y, 3.5f * tw + 1f, .8f * tw, WHITE, add = true)
        }
        if (g) {
            glow(c, rc, cx, cy, r * (.15f + .55f * cv), cv * .95f, add = true)
            glow(c, WHITE, cx, cy, r * (.05f + .14f * cv), cv, add = true)
        }
        c.restore()
        glassFront(c, cx, cy, r)
        if (impacts.isNotEmpty()) {
            drawCracks(
                c, cx, cy, r,
                if (g) mixc(p.rim, rc, conv) else p.rim,
                if (g) .4f + .6f * conv else .25f + pulse * .4f,
            )
        }
        if (layered) c.restore()
    }

    /** 유리 앞면 — 프레넬 테 · 바닥 집광(타원 + 호) · 부드러운 반사광 · 날카로운 빛줄 둘 · 점 반짝임 · 가는 테. 모두 구슬 안쪽이라 자르지 않는다. */
    private fun glassFront(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = pal
        pFill.xfermode = OrbInk.ADD
        pFill.color = BLACK
        // 프레넬: 안쪽 .78R 까지 비우고 가장자리에서 밝게(동심 원뿔 = 정지점 옮긴 한 점 그라데이션)
        pFill.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(alphaOf(p.rim, 0f), alphaOf(p.rim, 0f), alphaOf(p.rim, .1f), alphaOf(p.rim, .38f), alphaOf(p.rim, .12f)),
            floatArrayOf(0f, .78f, .78f + .22f * .8f, .78f + .22f * .96f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, r, pFill)
        // 바닥 집광 타원
        c.save(); c.translate(cx, cy + r * .66f); c.scale(1f, .42f)
        pFill.shader = RadialGradient(
            0f, 0f, r * .62f,
            intArrayOf(alphaOf(p.caustic, .42f), alphaOf(p.caustic, .14f), alphaOf(p.caustic, 0f)),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(0f, 0f, r * .62f, pFill)
        c.restore()
        pFill.shader = null
        pFill.xfermode = null
        // 바닥 집광 호
        pStroke.xfermode = OrbInk.ADD
        pStroke.shader = LinearGradient(
            cx - r * .7f, 0f, cx + r * .7f, 0f,
            intArrayOf(alphaOf(p.caustic, 0f), alphaOf(p.caustic, .55f), alphaOf(p.caustic, 0f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
        )
        pStroke.color = BLACK; pStroke.strokeWidth = r * .025f; pStroke.strokeCap = Paint.Cap.ROUND
        rect.set(cx - r * .9f, cy - r * .9f, cx + r * .9f, cy + r * .9f)
        c.drawArc(rect, .22f * 180f, (.78f - .22f) * 180f, false, pStroke)
        pStroke.shader = null
        pStroke.xfermode = null
        // 부드러운 반사광(왼쪽 위, 기울인 타원)
        c.save(); c.translate(cx - r * .36f, cy - r * .42f); c.rotate(-.7f * RAD2DEG); c.scale(1f, .62f)
        pFill.shader = RadialGradient(
            0f, 0f, r * .34f,
            intArrayOf(alphaOf(WHITE, .42f), alphaOf(WHITE, .1f), alphaOf(WHITE, 0f)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP,
        )
        pFill.color = BLACK
        c.drawCircle(0f, 0f, r * .34f, pFill)
        pFill.shader = null
        c.restore()
        // 날카로운 빛줄(가운데가 가장 밝고 양끝이 사라진다)
        pStroke.strokeCap = Paint.Cap.BUTT
        pStroke.strokeWidth = r * .042f
        val a0 = PI_F * 1.07f
        val a1 = PI_F * 1.4f
        rect.set(cx - r * .8f, cy - r * .8f, cx + r * .8f, cy + r * .8f)
        for (i in 0 until 16) {
            val u0 = i / 16f
            val u1 = (i + 1) / 16f
            val w = sin(PI_F * (u0 + u1) / 2f)
            pStroke.color = alphaOf(WHITE, .75f * w * w)
            c.drawArc(rect, (a0 + (a1 - a0) * u0) * RAD2DEG, ((a1 - a0) / 16f + .003f) * RAD2DEG, false, pStroke)
        }
        pStroke.strokeWidth = r * .018f
        rect.set(cx - r * .86f, cy - r * .86f, cx + r * .86f, cy + r * .86f)
        for (i in 0 until 10) {
            val u0 = i / 10f
            val u1 = (i + 1) / 10f
            val w = sin(PI_F * (u0 + u1) / 2f)
            pStroke.color = alphaOf(WHITE, .28f * w)
            c.drawArc(rect, PI_F * (.08f + .22f * u0) * RAD2DEG, (PI_F * .022f + .003f) * RAD2DEG, false, pStroke)
        }
        glow(c, WHITE, cx - r * .16f, cy - r * .7f, r * .06f, .7f, add = true)
        pStroke.color = alphaOf(p.rim, .35f); pStroke.strokeWidth = 1f
        c.drawCircle(cx, cy, r, pStroke)
    }

    // ── 금 ────────────────────────────────────────────────────────────────

    private fun crackPoint(ray: OrbRay, s: Float): FloatArray? {
        if (s > ray.len) return null
        for (j in 1 until ray.n) if (ray.cum[j] >= s) {
            val u = (s - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
            return floatArrayOf(ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u, ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u)
        }
        return null
    }

    /** 누른 자리(구슬 단위 좌표)에서 갈라져 나가는 방사형 금 6~9 + 가지 + 그 사이를 잇는 거미줄 금. */
    private fun makeImpact(x: Float, y: Float, target: Float): OrbImpact {
        val r = rnd
        val rays = ArrayList<OrbRay>()
        val mains = ArrayList<OrbRay>()
        val n = 6 + r.nextInt(4)
        val base = r.nextFloat() * TAU
        fun grow(sx: Float, sy: Float, ang: Float, start: Float, maxLen: Float): OrbRay {
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val cum = ArrayList<Float>()
            xs += sx; ys += sy; cum += 0f
            var px = sx; var py = sy; var a = ang; var len = 0f
            while (len < maxLen) {
                val step = .04f + .07f * r.nextFloat()
                a += (r.nextFloat() - .5f) * .24f
                px += cos(a) * step; py += sin(a) * step; len += step
                xs += px; ys += py; cum += len
                if (px * px + py * py > 1.03f) break
            }
            return OrbRay(xs.toFloatArray(), ys.toFloatArray(), cum.toFloatArray(), start, len)
        }
        for (i in 0 until n) {
            val ang = base + i.toFloat() / n * TAU + (r.nextFloat() - .5f) * .55f
            val ray = grow(x, y, ang, 0f, .35f + r.nextFloat() * 1.1f)
            rays += ray; mains += ray
            if (ray.n > 4 && r.nextFloat() < .4f) {
                val k = 1 + r.nextInt(ray.n - 3)
                rays += grow(ray.xs[k], ray.ys[k], ang + (if (r.nextFloat() < .5f) -1f else 1f) * (.45f + r.nextFloat() * .6f), ray.cum[k], .15f + r.nextFloat() * .6f)
            }
        }
        val rings = ArrayList<OrbRingSeg>()
        for (d in floatArrayOf(.07f, .15f, .27f, .43f)) {
            if (r.nextFloat() < .2f) continue
            for (i in 0 until n) {
                if (r.nextFloat() < .3f) continue
                val a = crackPoint(mains[i], d * (.85f + .3f * r.nextFloat()))
                val b = crackPoint(mains[(i + 1) % n], d * (.85f + .3f * r.nextFloat()))
                if (a != null && b != null) {
                    rings += OrbRingSeg(
                        a[0], a[1],
                        (a[0] + b[0]) / 2f + (r.nextFloat() - .5f) * .04f, (a[1] + b[1]) / 2f + (r.nextFloat() - .5f) * .04f,
                        b[0], b[1], d,
                    )
                }
            }
        }
        return OrbImpact(x, y, rays, rings, target)
    }

    private fun drawCracks(c: Canvas, cx: Float, cy: Float, r: Float, col: Int, ga: Float) {
        c.save()
        path.rewind()
        path.addCircle(cx, cy, r * .995f, Path.Direction.CW)
        c.clipPath(path)
        pStroke.shader = null
        pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.strokeJoin = Paint.Join.ROUND
        for (im in impacts) {
            val g = im.g
            if (g <= .001f) continue
            crack.rewind()
            for (ray in im.rays) {
                val vis = g - ray.start
                if (vis <= 0f) continue
                crack.moveTo(cx + ray.xs[0] * r, cy + ray.ys[0] * r)
                for (j in 1 until ray.n) {
                    if (ray.cum[j] <= vis) { crack.lineTo(cx + ray.xs[j] * r, cy + ray.ys[j] * r); continue }
                    val u = (vis - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
                    crack.lineTo(
                        cx + (ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u) * r,
                        cy + (ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u) * r,
                    )
                    break
                }
            }
            for (s in im.rings) {
                if (g < s.d * 1.3f + .04f) continue
                crack.moveTo(cx + s.ax * r, cy + s.ay * r)
                crack.quadTo(cx + s.mx * r, cy + s.my * r, cx + s.bx * r, cy + s.by * r)
            }
            pStroke.xfermode = null; pStroke.color = argb(0, 0, 0, .4f); pStroke.strokeWidth = 1.8f
            c.drawPath(crack, pStroke)
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(col, ga * .4f); pStroke.strokeWidth = 3.4f
            c.drawPath(crack, pStroke)
            pStroke.color = alphaOf(mixc(col, WHITE, .65f), .6f + ga * .4f); pStroke.strokeWidth = .75f
            c.drawPath(crack, pStroke)
            pStroke.xfermode = null
            val ix = cx + im.x * r
            val iy = cy + im.y * r
            val kk = min(1f, g * 4f)
            glow(c, col, ix, iy, r * .14f * kk, .3f + ga * .4f, add = true)
            glow(c, WHITE, ix, iy, r * .04f * kk, .85f * kk, add = true)
        }
        pStroke.strokeCap = Paint.Cap.BUTT
        pStroke.strokeJoin = Paint.Join.MITER
        c.restore()
    }

    // ── 조각 ──────────────────────────────────────────────────────────────

    /** 볼록 다각형([x0,y0,x1,y1,…])을 반평면 n·(p - m) ≤ 0 으로 자른다. */
    private fun clipHalf(poly: FloatArray, nx: Float, ny: Float, mx: Float, my: Float): FloatArray {
        val n = poly.size / 2
        val out = FloatArray((n + 1) * 2 + 2)
        var o = 0
        for (i in 0 until n) {
            val ax = poly[i * 2]; val ay = poly[i * 2 + 1]
            val j = (i + 1) % n
            val bx = poly[j * 2]; val by = poly[j * 2 + 1]
            val da = (ax - mx) * nx + (ay - my) * ny
            val db = (bx - mx) * nx + (by - my) * ny
            if (da <= 0f) { out[o++] = ax; out[o++] = ay }
            if ((da <= 0f) != (db <= 0f)) {
                val u = da / (da - db)
                out[o++] = ax + (bx - ax) * u; out[o++] = ay + (by - ay) * u
            }
        }
        return out.copyOf(o)
    }

    /** 누른 자리 둘레에 씨앗을 동심원으로 뿌려 보로노이로 자른다(바깥쪽일수록 큰 조각), 절반은 한 번 더 쪼갠다. */
    private fun shardCells(ix: Float, iy: Float): List<FloatArray> {
        val seeds = ArrayList<FloatArray>()
        seeds += floatArrayOf(ix, iy)
        for (k in 0 until 6) {
            val rad = .07f * 1.8f.pow(k)
            val n = 7 + 3 * k
            val off = rnd.nextFloat() * TAU
            for (i in 0 until n) {
                val a = off + i.toFloat() / n * TAU + (rnd.nextFloat() - .5f) * (TAU / n) * .7f
                val rr = rad * (.7f + .6f * rnd.nextFloat())
                val sx = ix + cos(a) * rr
                val sy = iy + sin(a) * rr
                if (sx * sx + sy * sy < 1.8f) seeds += floatArrayOf(sx, sy)
            }
        }
        val disk = FloatArray(96).also { d -> for (i in 0 until 48) { d[i * 2] = cos(i / 48f * TAU); d[i * 2 + 1] = sin(i / 48f * TAU) } }
        val cells = ArrayList<FloatArray>()
        for (s in seeds) {
            var poly = disk
            for (q in seeds) {
                if (q === s) continue
                poly = clipHalf(poly, q[0] - s[0], q[1] - s[1], (s[0] + q[0]) / 2f, (s[1] + q[1]) / 2f)
                if (poly.size < 6) break
            }
            if (poly.size < 6) continue
            if (poly.size >= 10 && rnd.nextFloat() < .5f) {
                var mx = 0f; var my = 0f
                val n = poly.size / 2
                for (i in 0 until n) { mx += poly[i * 2]; my += poly[i * 2 + 1] }
                mx /= n; my /= n
                val ang = rnd.nextFloat() * PI_F
                val a = clipHalf(poly, cos(ang), sin(ang), mx, my)
                val b = clipHalf(poly, -cos(ang), -sin(ang), mx, my)
                if (a.size >= 6) cells += a
                if (b.size >= 6) cells += b
            } else cells += poly
        }
        return cells
    }

    /** 깨지기 직전 구슬을 한 장 찍어(소프트웨어 캔버스) 조각마다 입힌다 — 조각이 돌며 날아가도 그 자리 그림이 따라간다. */
    private fun buildShards(k: Float) {
        needShards = false
        val r = ORB_R
        val sk = min(k, 2.2f)
        val pad = 2f
        val ox = bx - r - pad
        val oy = by - r - pad
        val side = ((r + pad) * 2f * sk).roundToInt().coerceAtLeast(8)
        val snap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(snap).apply {
            scale(sk, sk)
            translate(-ox, -oy)
            drawBall(this, bx, by, layered = false)
        }
        val last = impacts.lastOrNull()
        val ix = last?.x ?: 0f
        val iy = last?.y ?: 0f
        shards = shardCells(ix, iy).map { poly ->
            val n = poly.size / 2
            var ux = 0f; var uy = 0f
            for (i in 0 until n) { ux += poly[i * 2]; uy += poly[i * 2 + 1] }
            ux /= n; uy /= n
            val sp = Path()
            for (i in 0 until n) {
                val px = (poly[i * 2] - ux) * r
                val py = (poly[i * 2 + 1] - uy) * r
                if (i == 0) sp.moveTo(px, py) else sp.lineTo(px, py)
            }
            sp.close()
            var dx = ux - ix
            var dy = uy - iy
            val dist = hypot(dx, dy).let { if (it == 0f) 1f else it }
            dx /= dist; dy /= dist
            val cl = hypot(ux, uy).let { if (it == 0f) 1f else it }
            var vx = dx * .65f + ux / cl * .35f
            var vy = dy * .65f + uy / cl * .35f
            val vl = hypot(vx, vy).let { if (it == 0f) 1f else it }
            vx /= vl; vy /= vl
            val speed = r * (1.4f + 1.8f * rnd.nextFloat()) * (1.2f - .45f * min(1f, dist))
            val x0 = bx + ux * r
            val y0 = by + uy * r
            val shader = BitmapShader(snap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(1f / sk, 1f / sk); postTranslate(ox - x0, oy - y0) })
            }
            OrbShard(
                sp, x0, y0, vx * speed, vy * speed - r * .7f,
                (rnd.nextFloat() - .5f) * 7f, (rnd.nextFloat() - .5f) * 10f, rnd.nextFloat() * TAU,
                .9f + rnd.nextFloat() * .7f, dist * .07f, shader,
            )
        }
        dust = List(90) {
            val a = rnd.nextFloat() * TAU
            val speed = r * (1f + 2.6f * rnd.nextFloat())
            OrbGrit(
                bx + (ix + (rnd.nextFloat() - .5f) * .7f) * r, by + (iy + (rnd.nextFloat() - .5f) * .7f) * r,
                cos(a) * speed, sin(a) * speed - r * .5f, .6f + rnd.nextFloat() * 1.2f, .5f + rnd.nextFloat() * .8f, rnd.nextFloat() * TAU,
            )
        }
    }

    private fun drawShards(c: Canvas, s: Float) {
        val edge = mixc(rc, WHITE, .5f)
        pStroke.shader = null
        pStroke.strokeJoin = Paint.Join.ROUND
        for (sh in shards) {
            val u = max(0f, s - sh.delay)
            val dr = (1f - exp(-2.2f * u)) / 2.2f
            val a = if (u < sh.life * .55f) 1f else clamp01(1f - (u - sh.life * .55f) / (sh.life * .45f))
            if (a <= 0f) continue
            val x = sh.x0 + sh.vx * dr
            val y = sh.y0 + sh.vy * dr + .5f * GRAV * u * u
            val ph = sh.vt * u
            val cs = cos(ph)
            val sx = (if (cs < 0f) -1f else 1f) * max(.06f, abs(cs))
            c.save()
            c.translate(x, y)
            c.rotate(sh.vr * u * RAD2DEG)
            c.scale(sx, 1f)
            pShard.shader = sh.shader
            pShard.alpha = (a * 255f).toInt()
            c.drawPath(sh.path, pShard)
            val gl = abs(sin(ph * .5f + sh.gp)).pow(14)
            pFill.shader = null
            pFill.xfermode = OrbInk.ADD
            if (gl > .03f) { pFill.color = alphaOf(WHITE, gl * .55f * a); c.drawPath(sh.path, pFill) }
            pFill.color = alphaOf(rc, .14f * a * (1f - clamp01(u / .6f)))
            c.drawPath(sh.path, pFill)
            pFill.xfermode = null
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(edge, (.35f + gl * .6f) * a); pStroke.strokeWidth = .8f
            c.drawPath(sh.path, pStroke)
            pStroke.xfermode = null
            c.restore()
        }
        pStroke.strokeJoin = Paint.Join.MITER
        pFill.xfermode = OrbInk.ADD
        pFill.shader = null
        val dr = (1f - exp(-2.6f * s)) / 2.6f
        for (d in dust) {
            val a = clamp01(1f - s / d.life)
            if (a <= 0f) continue
            val x = d.x0 + d.vx * dr
            val y = d.y0 + d.vy * dr + .5f * GRAV * s * s
            val tw = .5f + .5f * sin(s * 30f + d.ph)
            pFill.color = alphaOf(edge, a * (.4f + .6f * tw))
            c.drawRect(x - d.s / 2f, y - d.s / 2f, x + d.s / 2f, y + d.s / 2f, pFill)
        }
        pFill.xfermode = null
    }

    // ── 등급 빛 ────────────────────────────────────────────────────────────

    private fun wedge(c: Canvas, cx: Float, cy: Float, ang: Float, len: Float, w: Float, c0: Int, c1: Int) {
        val ex = cx + cos(ang) * len
        val ey = cy + sin(ang) * len
        pFill.shader = LinearGradient(cx, cy, ex, ey, c0, c1, Shader.TileMode.CLAMP)
        pFill.color = BLACK
        pFill.xfermode = OrbInk.ADD
        path.rewind()
        path.moveTo(cx, cy)
        path.lineTo(cx + cos(ang - w) * len, cy + sin(ang - w) * len)
        path.lineTo(cx + cos(ang + w) * len, cy + sin(ang + w) * len)
        path.close()
        c.drawPath(path, pFill)
        pFill.shader = null
        pFill.xfermode = null
    }

    /** 깨지는 순간 — 번쩍임(영웅·전설) · 번짐 · 가로 섬광(희귀↑) · 빛줄기 0/8/14/22 · 충격파 등급 수만큼 · 반짝이(전설은 금가루가 쏟아진다). */
    private fun rarityBurst(c: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val f = clamp01(s / SHAT)
        val e = outC(f)
        val a = 1f - f
        if (lv >= 2 && flashOk) {
            val q = clamp01(1f - s / .45f)
            val fa = (if (lv == 3) .55f else .28f) * q * q
            if (fa > 0f) {
                pFill.shader = null; pFill.xfermode = OrbInk.ADD
                pFill.color = alphaOf(if (lv == 3) mixc(rc, WHITE, .4f) else rc, fa)
                c.drawRect(view, pFill)
                pFill.xfermode = null
            }
        }
        glow(c, rc, cx, cy, r * (.7f + .7f * lv) * (.45f + .55f * e), (.55f + .12f * lv) * a, add = true)
        glow(c, WHITE, cx, cy, r * (.35f + .25f * lv) * (.3f + .7f * e), a.pow(1.5f) * .9f, add = true)
        if (lv >= 1) {
            c.save(); c.translate(cx, cy); c.scale(1f, .05f + .02f * lv)
            glow(c, mixc(rc, WHITE, .3f), 0f, 0f, r * (1.4f + 1.1f * lv) * (.5f + .5f * e), a * (.3f + .15f * lv), add = true)
            c.restore()
        }
        val nr = RAYS[lv]
        val light = mixc(rc, WHITE, .5f)
        for (i in 0 until nr) {
            val ang = i.toFloat() / nr * TAU + t * .25f + (i % 2) * .13f
            val len = r * (.9f + .55f * lv) * (.35f + .65f * e) * (if (i % 3 == 0) 1f else .62f)
            wedge(c, cx, cy, ang, len, .035f + .015f * (i % 2), alphaOf(light, .75f * a), alphaOf(rc, 0f))
        }
        val ring = mixc(rc, WHITE, .3f)
        pStroke.shader = null
        for (kk in 0 until lv) {
            val u = clamp01((s - kk * .18f) / .9f)
            if (u <= 0f || u >= 1f) continue
            val rr = r * (.4f + 2.4f * outC(u))
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(ring, (1f - u) * .8f); pStroke.strokeWidth = (3.5f - kk) * (1f - u) + .5f
            rect.set(cx - rr, cy - rr * .92f, cx + rr, cy + rr * .92f)
            c.drawOval(rect, pStroke)
            pStroke.xfermode = null
        }
        val goldDust = rgb(255, 214, 130)
        for (p in sparks) {
            if (p.lv > lv) continue
            val d = r * (.25f + p.v * (1.4f + .5f * lv) * e)
            val x = cx + cos(p.a) * d
            val y = cy + sin(p.a) * d * .9f + p.g * r * (if (lv == 3) 1.2f else .4f) * f * f
            val tw = .6f + .4f * sin(t * 14f + p.ph)
            val col = if (lv == 3) (if (p.gold) goldDust else WHITE) else ring
            glow(c, col, x, y, p.s * 2.6f, a * .5f * tw, add = true)
            sparkle(c, x, y, p.s * (1f - .4f * f), a * tw, col, add = true)
        }
    }

    /** 열린 뒤 남는 후광 — 번짐(모든 등급) · 도는 빛줄기(영웅 10 / 전설 16) · 전설은 고리 + 내려앉는 금가루. [k] 0..1. */
    private fun aura(c: Canvas, cx: Float, cy: Float, r: Float, k: Float) {
        if (k <= 0f) return
        glow(c, rc, cx, cy, r * (.7f + .35f * lv), (.25f + .1f * lv) * k * (.85f + .15f * sin(t * 2f)), add = true)
        if (lv >= 2) {
            val n = if (lv == 3) 16 else 10
            val amp = (if (lv == 3) .22f else .12f) * k
            for (i in 0 until n) {
                val ang = i.toFloat() / n * TAU + t * .15f
                val len = r * (.8f + .4f * lv) * (if (i % 2 == 1) .7f else 1f)
                wedge(c, cx, cy, ang, len, .05f, alphaOf(rc, amp), alphaOf(rc, 0f))
            }
        }
        if (lv == 3) {
            pStroke.shader = null; pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(rc, .35f * k); pStroke.strokeWidth = 1f
            c.drawCircle(cx, cy, r * .62f, pStroke)
            pStroke.xfermode = null
            val gold = hexc("FFE9B0")
            for (p in glit) {
                val ang = p.a + t * p.w
                val ph = (p.y + t * p.v) % 1f
                val yy = cy - r * .9f + ph * r * 2f
                val x = cx + cos(ang) * p.r * r
                sparkle(c, x, yy, p.s, k * (.4f + .6f * abs(sin(t * 3f + p.ph))) * sin(ph * PI_F), gold, add = true)
            }
        }
    }

    /** 결과 문장 — 등급 빛 별(왼쪽 위에서 빛을 받는 입체 그라데이션)이 통 튀어나와 둥실, 둘레를 반짝이 다섯이 돈다. */
    private fun emblem(c: Canvas, x: Float, y: Float, s: Float, col: Int, alpha: Float) {
        if (alpha <= 0f || s <= .5f) return
        glow(c, col, x, y, s * 3.4f, .6f * alpha, add = true)
        glow(c, WHITE, x, y, s * 1.2f, .3f * alpha, add = true)
        val yy = y + sin(t * 1.6f) * s * .06f
        val rot = sin(t * .8f) * .12f
        path.rewind()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 1) s * .47f else s
            val a = rot - PI_F / 2f + i * PI_F / 5f
            val px = x + cos(a) * rr
            val py = yy + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val shader = RadialGradient(
            x - s * .18f, yy - s * .25f, s * 1.25f,
            intArrayOf(WHITE, mixc(col, WHITE, .35f), mixc(col, rgb(20, 10, 40), .3f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP,
        )
        val ai = (clamp01(alpha) * 255f).toInt()
        pFill.xfermode = null; pFill.shader = shader; pFill.color = BLACK; pFill.alpha = ai
        c.drawPath(path, pFill)
        pStroke.xfermode = null; pStroke.shader = shader; pStroke.color = BLACK; pStroke.alpha = ai
        pStroke.strokeWidth = s * .18f; pStroke.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, pStroke)
        pFill.shader = null; pStroke.shader = null; pStroke.strokeJoin = Paint.Join.MITER
        for (i in 0 until 5) {
            val a = t * .6f + i * TAU / 5f
            val rr = s * (1.9f + .25f * sin(t * 1.3f + i))
            sparkle(
                c, x + cos(a) * rr, yy + sin(a) * rr * .8f, s * (.17f + .07f * sin(t * 3f + i * 2f)),
                alpha * (.55f + .45f * sin(t * 2.4f + i)), WHITE, add = true,
            )
        }
    }

    private companion object {
        val RINGS = listOf(.93f to .6f, .8f to .45f, .58f to .35f)
        val STAR_ANGLES = floatArrayOf(0f, .5f, 1.1f, 1.6f, 2.4f, 3f, 3.7f, 4.5f, 5.3f)
        val STAR_LINES = arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(7, 8))
        val RAYS = intArrayOf(0, 8, 14, 22)
    }
}

/** 시스템 "애니메이션 끄기"(개발자 옵션·접근성)를 켠 사람 — 움직임을 줄이고 번쩍임을 뺀다. */
internal fun reducedMotion(context: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

@Composable
internal fun rememberOrbEngine(interactive: Boolean, rare: Boolean): OrbEngine {
    val context = LocalContext.current
    return remember {
        OrbEngine(Random.nextInt(), interactive).apply {
            setRoute(rare, animate = false)
            if (reducedMotion(context)) { motion = .35f; flashOk = false }
        }
    }
}

/**
 * 프레임 시계 — 엔진을 한 프레임씩 굴리고, 값을 읽는 Canvas 가 다시 그려지게 한다(앱이 뒤로 가면 쉰다).
 * [running] = false 면 멈춘다(무대 수정구는 항해 연출 창이 덮고 있는 동안 쉰다).
 */
@Composable
internal fun rememberOrbClock(engine: OrbEngine, running: Boolean = true): State<Long> {
    val frame = remember(engine) { mutableLongStateOf(0L) }
    LaunchedEffect(engine, running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            if (!AppForeground.isForeground) { delay(300); last = 0L; continue }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, .05f)
                last = now
                engine.step(dt)
                frame.longValue = now
            }
        }
    }
    return frame
}

/** 하늘 별(무대·연출 바탕) — 0..1 위치 · 크기 · 깜빡임 위상 · 속도. */
internal class SkyStars(seed: Int, n: Int = 70) {
    private val r = Random(seed)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val s = FloatArray(n) { .5f + r.nextFloat() * 1.1f }
    val p = FloatArray(n) { r.nextFloat() * TAU }
    val w = FloatArray(n) { .6f + r.nextFloat() * 1.6f }
    val size get() = x.size
}
