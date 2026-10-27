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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 수정구 — 잠든 우주(2026-10-27). 별가루 화면 무대와 깨우기 창이 함께 쓰는 그림. 시안 engine.js 를 옮겼다(iOS `CrystalOrb.swift` 와 같은 수치).
 *
 *  - 무대 단위 340 × 440(그리는 쪽이 px/단위 k 를 곱한다). 세계 좌표(X 오른쪽, Y 위, Z 보는 사람 쪽)를 카메라(고도 0.43rad, 거리 820)로 비춰 원근을 준다.
 *  - 아래: 불규칙한 별로 이룬 마법진(높이가 다른 여러 층, 원근으로 앞이 크다) — 바닥 빛, 별가루 고리 + 사슬로 이은 밝은 별, 별자리 띠, 칠각별,
 *    떠 있는 둘째·셋째 층(빛 실), 고리에서 솟는 빛 커튼, 피어오르는 반딧불.
 *  - 그 위로 피어오르는 은하수 꽃잔(두 팔 나선 별가루 · 성운 띠 · 먼지 띠 · 가운데 빛 기둥)이 둥둥 뜬 수정구를 감싸고, 둘레를 3D 궤도로 티끌이 돈다.
 *  - 수정구 안: 3D 별밭 · 나선 은하 · 성운/먼지 · 가끔 별똥별 — 앞으로 기울어진 세로축을 따라 돈다. 누른 쪽으로 돌고(앞은 누른 쪽, 뒤는 반대),
 *    같은 쪽을 거듭 누르면 빨라지고, 빨리 돌 때 반대쪽을 누르면 먼저 느려진다(관성). 빨리 돌면 별이 잔상으로 늘어진다.
 *  - 문지르기(1~2초): 둘레 고리가 차오르고 속이 휘저어지다가 다 차면 경로가 바뀐다(옅은 잠 ↔ 깊은 잠, 색이 0.9초 동안 섞인다).
 *  - 깨기(깨우기 창): 두드릴 때마다 유리 금(곧은 방사 금 + 다각형 거미줄 금 + 부서진 점 + 튀는 유리 조각), 세 번째 뒤 결과 등급 빛이
 *    금 사이로 새어 나오다가 수많은 조각으로 깨지며 속의 우주가 터져 나온다(조각마다 깨지기 직전 구슬 그림을 그대로 입힌다).
 *  - 화면 번쩍임(영웅·전설)은 시스템 "애니메이션 끄기"면 생략하고 움직임도 느리게([OrbEngine.motion]).
 * 그리기는 android.graphics.Canvas — 시안의 'lighter'(더하기) 합성을 그대로 쓰려고.
 */

internal const val ORB_W = 340f
internal const val ORB_H = 440f
/** 수정구 반지름(무대 단위). */
internal const val ORB_R = 84f
private const val ST_CX = 170f
private const val ST_GY = 350f
private const val CAM_D = 820f
private val CAM_SE = sin(.43f)
private val CAM_CE = cos(.43f)
/** 마법진 반지름(세계). */
private const val CR = 118f
/** 수정구 높이(세계). */
private const val YO = 172f
/** 은하수 꽃잔 가장자리 높이(세계) — 수정구 아랫도리를 감싼다. */
private const val YT = YO * .9f
/** 수정구 속 회전축 기울기(위가 조금 보이게). */
private const val TILT = .5f
private val ORB_S = CAM_D / (CAM_D - YO * CAM_SE)
/** 수정구 가운데(무대 단위) — 세계 (0, [YO], 0) 을 비춘 자리. */
internal const val ORB_CX = ST_CX
internal val ORB_CY = ST_GY - YO * CAM_CE * ORB_S
/** 수정구 반지름(세계). */
private val RW = ORB_R / ORB_S

private const val TAU = (PI * 2).toFloat()
private const val PI_F = PI.toFloat()
private const val RAD2DEG = (180.0 / PI).toFloat()

private const val SPIN_IDLE = .22f
private const val SPIN_KICK = 2.3f
private const val SPIN_MAX = 14f
private const val SPIN_DECAY = .55f
private const val RUB_LEN = 340f
private const val RUB_MIN_T = 1f
private const val CONV = 1f
private const val SHAT = 1.9f
private const val GRAV = 270f

/** 깨진 뒤 이만큼(초) 지나면 카드가 솟는다 — 조각이 거의 다 떨어졌을 때. */
internal const val ORB_CARDS_AT = 1.55f

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
private fun smooth(a: Float, b: Float, x: Float): Float { val t = clamp01((x - a) / (b - a)); return t * t * (3f - 2f * t) }
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

/** 옅은 잠 = 맑은 유리 · 푸른 은 / 깊은 잠 = 연기 유리 · 금. [setLerp] 는 섞인 값을 제 자리에 쓴다. */
internal class OrbPalette(
    val fog: IntArray,
    var ink: Int,
    val body: IntArray,
    var rim: Int,
    var caustic: Int,
    var glow: Int,
    var star: Int,
    var line: Int,
    var cstar: Int,
    val band: IntArray,
    val gal: IntArray,
) {
    fun copy() = OrbPalette(fog.copyOf(), ink, body.copyOf(), rim, caustic, glow, star, line, cstar, band.copyOf(), gal.copyOf())

    /** [a] → [b] 를 [t] 만큼 섞어 제 자리에 쓴다([a] 가 자기 자신이어도 된다). */
    fun setLerp(a: OrbPalette, b: OrbPalette, t: Float) {
        for (i in fog.indices) fog[i] = mixc(a.fog[i], b.fog[i], t)
        ink = mixc(a.ink, b.ink, t)
        for (i in body.indices) body[i] = mixc(a.body[i], b.body[i], t)
        rim = mixc(a.rim, b.rim, t)
        caustic = mixc(a.caustic, b.caustic, t)
        glow = mixc(a.glow, b.glow, t)
        star = mixc(a.star, b.star, t)
        line = mixc(a.line, b.line, t)
        cstar = mixc(a.cstar, b.cstar, t)
        for (i in band.indices) band[i] = mixc(a.band[i], b.band[i], t)
        for (i in gal.indices) gal[i] = mixc(a.gal[i], b.gal[i], t)
    }

    companion object {
        val NORMAL = OrbPalette(
            fog = intArrayOf(hexc("4F7DFF"), hexc("7FB0FF"), hexc("9B7BFF"), hexc("5FD3FF"), hexc("D08BFF")),
            ink = hexc("030512"),
            body = intArrayOf(argb(44, 66, 140, .22f), argb(14, 20, 52, .6f), argb(5, 8, 24, .93f)),
            rim = hexc("C8DCFF"), caustic = hexc("96C3FF"), glow = hexc("5A8CFF"), star = hexc("DDE8FF"),
            line = hexc("9FC3FF"), cstar = hexc("E8F0FF"),
            band = intArrayOf(hexc("FFF0DA"), hexc("B9CCFF"), hexc("9A86FF"), hexc("6A8DFF")),
            gal = intArrayOf(hexc("FFF1D6"), hexc("BFD4FF"), hexc("7C8CFF")),
        )
        val RARE = OrbPalette(
            fog = intArrayOf(hexc("FFB347"), hexc("FFD27A"), hexc("FF8A5C"), hexc("FFE6A8"), hexc("FF7A9C")),
            ink = hexc("0A0503"),
            body = intArrayOf(argb(90, 52, 30, .26f), argb(26, 14, 14, .66f), argb(8, 5, 7, .94f)),
            rim = hexc("FFDEA0"), caustic = hexc("FFC970"), glow = hexc("FF9F43"), star = hexc("FFE9B0"),
            line = hexc("FFD27A"), cstar = hexc("FFF4D8"),
            band = intArrayOf(hexc("FFFFFF"), hexc("FFDDA6"), hexc("FF9FB4"), hexc("FFB566")),
            gal = intArrayOf(hexc("FFFFFF"), hexc("FFD9A0"), hexc("FF8F70")),
        )
    }
}

/** 한 번 굽고 함께 쓰는 것들 — 빛 번짐 스프라이트 · 색 필터. */
private object OrbInk {
    val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)

    /** 흰 빛 번짐(시안 sprite — 0:1 · .22:.6 · .55:.16 · 1:0). 색은 필터로 입힌다. */
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

    /** 6 단계로 묶어 캐시가 끝없이 늘지 않게. */
    fun filter(c: Int): PorterDuffColorFilter {
        fun q(s: Int) = (((c ushr s) and 0xFF) / 6f).roundToInt().times(6).coerceAtMost(255)
        val key = BLACK or (q(16) shl 16) or (q(8) shl 8) or q(0)
        filters[key]?.let { return it }
        if (filters.size > 600) filters.clear()
        return PorterDuffColorFilter(key, PorterDuff.Mode.SRC_IN).also { filters[key] = it }
    }
}

/**
 * 유리 몸통 그라데이션 — 시안의 두 점 원뿔 그라데이션(안쪽 원 (-.25, -.3) r .05 → 바깥 원 (0, 0) r 1)의 매개변수를 한 번 구워 둔다
 * (Android RadialGradient 는 API 31 전엔 한 점뿐이라). 색은 [OrbBody] 가 표로 칠한다.
 */
private object OrbBodyMap {
    const val N = 160
    /** 칸마다 그라데이션 매개변수(0..255). */
    val t = IntArray(N * N)
    /** 칸마다 원 안에 든 정도(가장자리 부드럽게). */
    val cover = FloatArray(N * N)

    init {
        val dcx = .25f
        val dcy = .3f
        val r0 = .05f
        val dr = .95f
        val a = dcx * dcx + dcy * dcy - dr * dr
        for (j in 0 until N) for (i in 0 until N) {
            val x = (i + .5f) / N * 2f - 1f
            val y = (j + .5f) / N * 2f - 1f
            cover[j * N + i] = clamp01((1f - sqrt(x * x + y * y)) * N / 2f + .5f)
            val qx = x + dcx
            val qy = y + dcy
            val b = -2f * (qx * dcx + qy * dcy + r0 * dr)
            val c = qx * qx + qy * qy - r0 * r0
            val disc = b * b - 4f * a * c
            var tt = 1f
            if (disc >= 0f) {
                val s = sqrt(disc)
                val t1 = (-b + s) / (2f * a)
                val t2 = (-b - s) / (2f * a)
                val best = max(t1, t2)
                tt = if (r0 + best * dr >= 0f) best else min(t1, t2)
            }
            t[j * N + i] = (clamp01(tt) * 255f).roundToInt()
        }
    }
}

/** 엔진 하나의 유리 몸통 그림 — 색 셋(정지점 0 · .72 · 1)이 바뀔 때만 다시 칠한다(경로 색이 섞이는 동안은 프레임마다). */
private class OrbBody {
    private val n = OrbBodyMap.N
    val bitmap: Bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    private val px = IntArray(n * n)
    private val lut = IntArray(256)
    private var k0 = 0
    private var k1 = 0
    private var k2 = 0
    private var ready = false

    fun update(c0: Int, c1: Int, c2: Int) {
        if (ready && c0 == k0 && c1 == k1 && c2 == k2) return
        k0 = c0; k1 = c1; k2 = c2; ready = true
        for (q in 0 until 256) {
            val tt = q / 255f
            lut[q] = if (tt <= .72f) premulMix(c0, c1, tt / .72f) else premulMix(c1, c2, (tt - .72f) / .28f)
        }
        val tm = OrbBodyMap.t
        val cv = OrbBodyMap.cover
        for (i in px.indices) {
            val cov = cv[i]
            px[i] = when {
                cov <= 0f -> 0
                cov >= 1f -> lut[tm[i]]
                else -> lut[tm[i]].let { c -> (c and 0xFFFFFF) or ((((c ushr 24) and 0xFF) * cov).roundToInt() shl 24) }
            }
        }
        bitmap.setPixels(px, 0, n, 0, 0, n, n)
    }

    /** 미리 곱한 알파로 섞는다(캔버스 그라데이션과 같게) — 결과는 보통 ARGB. */
    private fun premulMix(c0: Int, c1: Int, u: Float): Int {
        val a0 = ((c0 ushr 24) and 0xFF) / 255f
        val a1 = ((c1 ushr 24) and 0xFF) / 255f
        val al = a0 + (a1 - a0) * u
        fun ch(sh: Int): Int {
            val v0 = ((c0 ushr sh) and 0xFF) * a0
            val v1 = ((c1 ushr sh) and 0xFF) * a1
            return if (al <= 0f) 0 else ((v0 + (v1 - v0) * u) / al).roundToInt().coerceIn(0, 255)
        }
        return ((al * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

// ── 그림 재료(구슬 속 · 마법진 · 은하수 · 깨짐) ─────────────────────────────

private class OrbStar(val x: Float, val y: Float, val z: Float, val s: Float, val big: Boolean, val f: Float, val ph: Float)
private class OrbGalPt(val rr: Float, val a: Float, val ox: Float, val oy: Float, val oz: Float, val w: Float, val ph: Float, val s: Float)
private class OrbNeb(
    val x: Float, val y: Float, val z: Float, val s: Float, val c: Int, val dark: Boolean,
    val ph: Float, val al: Float, val el: Float, val rot: Float,
)
private class OrbFar(val x: Float, val y: Float, val z: Float, val rot: Float, val s: Float)
private class OrbRingDust(val a: Float, val rr: Float, val y: Float, val s: Float, val ph: Float, val f: Float)
private class OrbRingStar(val a: Float, val rr: Float, val s: Float, val big: Boolean, val f: Float, val ph: Float, val y: Float, val link: Boolean)
private class OrbCPt(val a: Float, val rr: Float, val s: Float, val ph: Float)
private class OrbHDust(val e: Int, val u: Float, val off: Float, val s: Float, val ph: Float)
private class OrbRiser(val a: Float, val d: Float, val p0: Float, val life: Float, val h: Float, val s: Float, val ph: Float, val sw: Float)
private class OrbFlow(
    val p0: Float, val life: Float, val arm: Int, val off: Float, val rj: Float, val s: Float,
    val ci: Int, val ph: Float, val f: Float, val bright: Boolean, val core: Boolean,
)
private class OrbBand(val p0: Float, val life: Float, val arm: Int, val off: Float, val rj: Float, val s: Float, val ci: Int, val al: Float, val dark: Boolean)
private class OrbMote(val a0: Float, val w: Float, val rr: Float, inc: Float, yaw: Float, val s: Float, val ph: Float, val f: Float) {
    val si = sin(inc)
    val ci = cos(inc)
    val sy = sin(yaw)
    val cy = cos(yaw)
}
private class OrbRubSpark(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Float, val life: Float, val s: Float)
private class OrbWave(val born: Float, val dur: Float, val c: Int)
private class OrbShoot(val x: Float, val y: Float, val dir: Float, val born: Float)
private class OrbRay(val xs: FloatArray, val ys: FloatArray, val cum: FloatArray, val lum: IntArray, val start: Float, val len: Float) {
    val n get() = xs.size
}
private class OrbRingSeg(val ax: Float, val ay: Float, val mx: Float, val my: Float, val bx: Float, val by: Float, val d: Float)
private class OrbGlint(val ray: Int, val v: Float, val ph: Float)
private class OrbImpact(
    val x: Float, val y: Float, val rays: List<OrbRay>, val rings: List<OrbRingSeg>,
    /** 잔금 — [x0, y0, x1, y1] 이 이어진다. */
    val micro: FloatArray,
    /** 부서진 점 — 일곱 꼭짓점 [x, y] 가 이어진다. */
    val crush: FloatArray,
    val glints: List<OrbGlint>,
    var target: Float,
) {
    var g = 0f
}
private class OrbFlake(
    val x: Float, val y: Float, val vx: Float, val vy: Float, val vz: Float, val born: Float,
    val life: Float, val rot: Float, val vr: Float, val tri: FloatArray,
)
private class OrbShard(
    val path: Path, val x0: Float, val y0: Float, val vx: Float, val vy: Float, val vz: Float,
    val vr: Float, val vt: Float, val vq: Float, val gp: Float, val life: Float, val delay: Float, val shader: BitmapShader,
)
private class OrbGrit(val x0: Float, val y0: Float, val vx: Float, val vy: Float, val s: Float, val life: Float, val ph: Float)
private class OrbEscape(val a: Float, val r0: Float, val v: Float, val w: Float, val life: Float, val c: Int)
private class OrbPuff(val a: Float, val d: Float, val c: Int, val s0: Float)
private class OrbLeak(val a: Float, val w: Float, val l: Float)

/**
 * 수정구 한 개의 상태와 그림. 프레임마다 [step] → [draw].
 * [interactive] = false(별가루 화면 무대)면 두드려도 금이 가지 않고 누른 쪽으로 돈다. 문지르기([rubStart]/[rubMove]/[rubEnd])는 무대에서만.
 * 같은 [seed] 면 별 · 은하 · 마법진 배치가 같다 — 깨우기 창은 무대 수정구의 seed 로 만들어 [follow] 로 이어받는다.
 */
internal class OrbEngine(val seed: Int, private val interactive: Boolean) {
    enum class Phase { IDLE, WAIT, CONVERGE, SHATTER, REVEAL }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var taps by mutableIntStateOf(0)
        private set
    /** 깨진 뒤 [ORB_CARDS_AT] 이 지났거나 건너뛰었다 — 카드를 띄울 때. */
    var cardsReady by mutableStateOf(false)
        private set

    /** 1 = 보통, 0.35 = 시스템 "애니메이션 끄기". */
    var motion = 1f
    /** 영웅·전설 화면 번쩍임 — 애니메이션을 끈 사람에겐 끈다. */
    var flashOk = true
    /** 결과 등급이 정해져 모이기 시작할 때 / 깨지는 순간(등급 0..3) — 진동은 부르는 쪽이. */
    var onConverge: (() -> Unit)? = null
    var onShatter: ((Int) -> Unit)? = null

    var t = 0f
        private set

    private val rnd = Random(seed)
    private fun r() = rnd.nextFloat()

    // ── 돌리기 ──
    private var omega = SPIN_IDLE
    private var idleDir = 1f
    private var spinA = 0f
    private var pulse = 0f
    private var flowA = 0f
    private var ringA = 0f
    private var tiltNow = TILT

    // ── 문지르기 ──
    /** 문지른 정도 0..1 — 다 차면 경로가 바뀐다. */
    var rubP = 0f
        private set
    private var rubbing = false
    private var rubLock = false
    private var lastRubT = -9f
    private var flash = 0f
    private val rubSparks = ArrayList<OrbRubSpark>()
    private val waves = ArrayList<OrbWave>()
    private val ringR = ORB_R + 11f

    // ── 색 ──
    /** 지금 경로(깊은 잠 = true). 문지르기로 바뀌면 화면이 이 값을 따라간다. */
    var rare = false
        private set
    private var from = OrbPalette.NORMAL
    private var to = OrbPalette.NORMAL
    private var mixT = 1f
    private val pal = OrbPalette.NORMAL.copy()
    private var palVer = 0
    private var lastE = -1f
    private var lastRubMix = -1f
    private var lastFrom: OrbPalette? = null
    private var lastTo: OrbPalette? = null

    /** 지금 경로 빛(바탕 성운이 따라 쓴다 — 색이 섞이는 동안에도). */
    val glowColor: Int get() = pal.glow

    // ── 깨기 ──
    private var pt = 0f
    private var conv = 0f
    private var lv = 0
    private var rc = WHITE
    private var pending: String? = null
    private val impacts = ArrayList<OrbImpact>()
    private val flakes = ArrayList<OrbFlake>()
    private var shards: List<OrbShard> = emptyList()
    private var grit: List<OrbGrit> = emptyList()
    private var escape: List<OrbEscape> = emptyList()
    private var puffs: List<OrbPuff> = emptyList()
    private var leaks: List<OrbLeak> = emptyList()
    private var needShards = false
    private var shake = 0f
    private var flareAt = -9f

    /** 지금 그려진 구슬 가운데(무대 단위) — 누른 자리가 구슬 위인지 볼 때. */
    var bx = ORB_CX
        private set
    var by = ORB_CY
        private set
    private var orbYW = YO
    private var viewTop = 0f
    /** 무대 전체 투명도(카드가 뜰 때 무대를 지운다) — 빛·반짝이·점이 곱한다. */
    private var fade = 1f

    // ── 수정구 속 우주 ──
    private val stars = List(240) {
        val d = .97f * Math.cbrt(r().toDouble()).toFloat()
        val u = r() * 2f - 1f
        val th = r() * TAU
        val q = sqrt(1f - u * u)
        val big = r() < .06f
        OrbStar(
            q * cos(th) * d, u * d, q * sin(th) * d,
            if (big) 1.5f + r() * 1.1f else .3f + r().pow(3) * 1.1f, big, .8f + r() * 2.6f, r() * TAU,
        )
    }
    /** 나선 은하 — 두 팔 + 흩어진 원반. 은하판은 회전축에 비스듬해서 돌 때마다 모양이 달라 보인다. */
    private val gal = List(420) { i ->
        val disc = i % 5 == 4
        val rr = .72f * r().pow(if (disc) .8f else 1.25f)
        val br = i % 2
        fun sc(k: Float) = r().pow(2.2f) * (if (r() < .5f) -1f else 1f) * k * (.3f + rr)
        OrbGalPt(
            rr, if (disc) r() * TAU else br * PI_F + rr * 7.2f,
            sc(if (disc) .1f else .16f), sc(.04f), sc(if (disc) .1f else .16f), .05f / (.1f + rr), r() * TAU, .4f + r() * .8f,
        )
    }
    private val neb = List(46) {
        val d = .16f + .68f * sqrt(r())
        val u = r() * 2f - 1f
        val th = r() * TAU
        val q = sqrt(1f - u * u)
        val dark = r() < .24f
        OrbNeb(
            q * cos(th) * d, u * d * .8f, q * sin(th) * d, if (dark) .14f + r() * .18f else .22f + r() * .36f,
            (r() * 5f).toInt().coerceAtMost(4), dark, r() * TAU, if (dark) .42f else .1f + r() * .08f, .4f + r() * .55f, r() * PI_F,
        )
    }
    private var shoot: OrbShoot? = null
    private var shootAt = 1.2f + r() * 2f
    private val far = List(3) {
        val u = r() * 1.4f - .7f
        val th = r() * TAU
        val q = sqrt(1f - u * u)
        OrbFar(q * cos(th) * .88f, u * .88f, q * sin(th) * .88f, r() * PI_F, .05f + r() * .04f)
    }

    // ── 마법진 ──
    /** 바깥 고리 — 밀도가 들쭉날쭉한 별가루 수백 개(뭉친 데 · 성긴 데). */
    private val ringDust = ArrayList<OrbRingDust>(420).also { list ->
        while (list.size < 420) {
            val a = r() * TAU
            val dens = .55f + .45f * sin(a * 3f + 1.3f) * sin(a * 7f + .4f)
            if (r() > dens + .25f) continue
            val gj = (r() + r() + r() - 1.5f) / 1.5f
            list += OrbRingDust(a, 1f + gj * .05f, abs(gj) * 4f * r(), .3f + r().pow(3) * 1.1f, r() * TAU, .6f + r() * 2.2f)
        }
    }
    /** 크기가 제각각인 밝은 별(이웃끼리 짧은 별자리 사슬) — 각도 순. */
    private val ring1 = List(56) {
        val big = r() < .15f
        OrbRingStar(
            r() * TAU, 1f + (r() - .5f) * .08f, if (big) 1.3f + r() * 1.3f else .4f + r().pow(2) * .9f, big,
            .7f + r() * 2.4f, r() * TAU, if (r() < .2f) 3f + r() * 9f else 0f, r() < .62f,
        )
    }.sortedBy { it.a }
    private val constel = List(7) { k ->
        val c0 = k / 7f * TAU + .22f + (r() - .5f) * .16f
        val m = 3 + (r() * 3f).toInt().coerceAtMost(2)
        List(m) { OrbCPt(c0 + (r() - .5f) * .3f, .76f + r() * .12f, .6f + r() * 1.1f, r() * TAU) }.sortedBy { it.a }
    }
    private val heptaDust = List(35) { i -> OrbHDust(i / 5, .08f + r() * .84f, (r() - .5f) * .03f, .3f + r() * .8f, r() * TAU) }
    /** 떠 있는 둘째 층(높이 14) · 셋째 층(높이 30). */
    private val ring2 = List(36) { i -> OrbCPt(i / 36f * TAU + (r() - .5f) * .14f, .5f + (r() - .5f) * .04f, .35f + r() * .85f, r() * TAU) }
    private val ring3 = List(6) { i -> OrbCPt(i / 6f * TAU + (r() - .5f) * .25f, .26f + (r() - .5f) * .03f, .9f + r() * .9f, r() * TAU) }
    /** 마법진에서 피어올라 사라지는 반딧불. */
    private val risers = List(40) {
        OrbRiser(r() * TAU, sqrt(r()) * CR * .95f, r(), 2.6f + r() * 3f, 40f + r() * 90f, .6f + r() * 1.2f, r() * TAU, (r() - .5f) * .9f)
    }

    // ── 은하수 꽃잔 — 두 팔 나선으로 감겨 오르며 활짝 벌어지는 별가루(팔 가운데일수록 촘촘하고 밝다) ──
    private val flow = List(760) { i ->
        val gj = (r() + r() + r() - 1.5f) / 1.5f
        OrbFlow(
            r(), 4f + r() * 3f, i % 2, gj * .32f, .8f + r() * .3f + abs(gj) * .2f, .45f + r().pow(2.6f) * 1.6f,
            1 + (r() * 3f).toInt().coerceAtMost(2), r() * TAU, 1f + r() * 3f, r() < .04f, abs(gj) < .25f,
        )
    }
    private val band = List(40) { i ->
        val dark = i % 4 == 3
        OrbBand(
            r(), 7f + r() * 5f, i % 2, (r() - .5f) * .3f + (if (dark) .18f else 0f), .85f + r() * .3f,
            if (dark) 9f + r() * 10f else 16f + r() * 22f, 1 + (r() * 3f).toInt().coerceAtMost(2), if (dark) .32f else .08f + r() * .06f, dark,
        )
    }
    private val motes = List(34) {
        OrbMote(
            r() * TAU, (.16f + r() * .38f) * (if (r() < .5f) 1f else -1f), RW * (1.25f + r() * .7f), (r() - .5f) * 1.2f, r() * TAU,
            .5f + r() * 1.2f, r() * TAU, 1f + r() * 3f,
        )
    }

    // ── 다시 쓰는 그리기 도구(프레임마다 새로 만들지 않는다) ──
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = OrbInk.ADD }
    private val pShard = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val sparkPath = Path()
    private val clip = Path()
    private val lit = Array(3) { Path() }
    private val ringsPath = Path()
    private val microPath = Path()
    private val mtx = Matrix()
    private val mtxVals = FloatArray(9)
    private val body = OrbBody()

    // 프레임마다 다시 쓰는 계산 칸
    private var pX = 0f
    private var pY = 0f
    private var pS = 1f
    private var fX = 0f
    private var fY = 0f
    private var fZ = 0f
    private var fH = 0f
    private var qx = 0f
    private var qy = 0f
    private var qz = 0f
    private var qu = 0f
    private var qv = 0f
    private var iCa = 1f
    private var iSa = 0f
    private var iCt = 1f
    private var iSt = 0f
    private var iCr = 1f
    private var iSr = 0f
    private var iShrink = 1f
    private var iSw = 0f
    private var iSwT = 0f
    private var iCx = 0f
    private var iCy = 0f
    private var iR = ORB_R
    private var innerCv = 0f
    private var innerGo = false
    private var sweep = 0f
    private var cpx = 0f
    private var cpy = 0f

    private val r1x = FloatArray(ring1.size)
    private val r1y = FloatArray(ring1.size)
    private val r1s = FloatArray(ring1.size)
    private val r1X = FloatArray(ring1.size)
    private val r1Z = FloatArray(ring1.size)
    private val r1a = FloatArray(ring1.size)
    private val csX = FloatArray(8)
    private val csY = FloatArray(8)
    private val csS = FloatArray(8)
    private val csZ = FloatArray(8)
    private val csA = FloatArray(8)
    private val hvX = FloatArray(7)
    private val hvY = FloatArray(7)
    private val hvS = FloatArray(7)
    private val hvZ = FloatArray(7)
    private val hvA = FloatArray(7)
    private val cuB0x = FloatArray(73)
    private val cuB0y = FloatArray(73)
    private val cuB1x = FloatArray(73)
    private val cuB1y = FloatArray(73)
    private val cuN = FloatArray(73)
    private val cuZ = FloatArray(73)
    private val flX = FloatArray(flow.size)
    private val flY = FloatArray(flow.size)
    private val flS = FloatArray(flow.size)
    private val flA = FloatArray(flow.size)
    private val flC = IntArray(flow.size)
    /** 0 = 그리지 않음(수정구 속) · 1 = 뒤 · 2 = 앞. */
    private val flSide = ByteArray(flow.size)
    private var flFl = 1f
    private val stX = FloatArray(stars.size)
    private val stY = FloatArray(stars.size)
    private val stZ = FloatArray(stars.size)
    private val stU = FloatArray(stars.size)
    private val stV = FloatArray(stars.size)
    private val galCol = IntArray(gal.size)
    private var galColVer = -1

    private var curtainShader: LinearGradient? = null
    private var curtainColor = 0
    private var glassVer = -1
    private var gFresnel: Shader? = null
    private var gCaustic: Shader? = null
    private var gCausticArc: Shader? = null
    private val gCrescent = LinearGradient(
        -ORB_R * .95f, ORB_R * .1f, -ORB_R * .05f, -ORB_R * .95f,
        intArrayOf(alphaOf(WHITE, 0f), alphaOf(WHITE, .55f), alphaOf(WHITE, .2f), alphaOf(WHITE, 0f)),
        floatArrayOf(0f, .42f, .75f, 1f), Shader.TileMode.CLAMP,
    )
    private val gSoft = RadialGradient(
        0f, 0f, ORB_R * .4f, intArrayOf(alphaOf(WHITE, .16f), alphaOf(WHITE, .04f), alphaOf(WHITE, 0f)),
        floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP,
    )
    /** 창 반사 — 왼쪽 위 가장자리를 따라 휜 초승달(가운데가 두껍고 끝이 가늘다), 구슬 가운데 기준. */
    private val crescent = Path().apply {
        val rr = ORB_R
        arcTo(RectF(-rr * .935f, -rr * .935f, rr * .935f, rr * .935f), 1.05f * 180f, (1.47f - 1.05f) * 180f, true)
        val c2 = rr * .236f
        val r2 = rr * 1.178f
        arcTo(RectF(c2 - r2, c2 - r2, c2 + r2, c2 + r2), 1.414f * 180f, -(1.414f - 1.101f) * 180f, false)
        close()
    }
    /** 땅 위 안내선 셋(반지름 · 높이)의 뒤/앞 반쪽 — 움직이지 않으니 한 번 비춘다. */
    private val staticRings: Array<Path> by lazy {
        val defs = floatArrayOf(CR * 1.05f, 0f, CR * .5f, 14f, CR * .26f, 30f)
        Array(6) { k -> ringPath(Path(), defs[(k / 2) * 2], defs[(k / 2) * 2 + 1], front = k % 2 == 1) }
    }

    private val going get() = phase == Phase.CONVERGE || phase == Phase.SHATTER || phase == Phase.REVEAL

    /** 깨지기 시작한 뒤 흐른 시간(초), 깨지기 전엔 -1. */
    val shatterAge: Float get() = when (phase) {
        Phase.SHATTER -> pt
        Phase.REVEAL -> SHAT + pt
        else -> -1f
    }

    init {
        updatePalette()
    }

    // ── 조작 ──────────────────────────────────────────────────────────────

    /** 경로 색 — [animate] 면 0.9초 동안 섞는다(이미 그쪽으로 가고 있으면 그대로 둔다). */
    fun setRoute(rare: Boolean, animate: Boolean) {
        val target = if (rare) OrbPalette.RARE else OrbPalette.NORMAL
        if (this.rare == rare && to === target) return
        this.rare = rare
        if (animate) {
            from = pal.copy()
            to = target
            mixT = 0f
        } else {
            from = target; to = target; mixT = 1f
        }
        updatePalette()
    }

    /**
     * 무대 수정구([src], 같은 [seed])를 지금 모습 그대로 이어받는다 — 깨우기 창이 무대 구슬에서 끊김 없이 다가가게.
     * 배치는 seed 가 같아 이미 같고, 여기선 시계 · 돌기 · 경로 색 · 잠깐 남은 빛(물결 · 불꽃 · 별똥별)만 옮긴다.
     */
    fun follow(src: OrbEngine) {
        t = src.t
        omega = src.omega; idleDir = src.idleDir; spinA = src.spinA
        pulse = src.pulse; flowA = src.flowA; ringA = src.ringA
        flash = src.flash; rubP = src.rubP
        rubSparks.clear(); rubSparks += src.rubSparks
        waves.clear(); waves += src.waves
        shoot = src.shoot; shootAt = src.shootAt
        rare = src.rare; from = src.from; to = src.to; mixT = src.mixT
        lastE = -1f
        updatePalette()
        bx = src.bx; by = src.by
    }

    /** 누른 쪽으로 돌린다(-1 왼쪽 · 1 오른쪽) — 더해지기만 하니 빨리 돌 때 반대쪽은 먼저 브레이크가 된다. */
    fun spin(side: Int) {
        omega = (omega + side * SPIN_KICK).coerceIn(-SPIN_MAX, SPIN_MAX)
        if (abs(omega) > SPIN_IDLE * .5f) idleDir = sign(omega)
        pulse = 1f
    }

    fun rubStart() {
        if (!rubLock) { rubbing = true; lastRubT = t }
    }

    /** 문지르기 — ([x], [y]) 무대 단위, [dist] 움직인 거리, [dt] 직전 움직임과의 간격(초). 다 차서 경로가 바뀌었으면 true. */
    fun rubMove(x: Float, y: Float, dist: Float, dt: Float): Boolean {
        if (!rubbing || rubLock) return false
        if (hypot(x - bx, y - by) < ORB_R * 1.45f) {
            rubP = min(1f, rubP + min(dist / RUB_LEN, max(0f, dt) / RUB_MIN_T))
            lastRubT = t
        }
        if (rubSparks.size < 90) repeat(2) {
            rubSparks += OrbRubSpark(x, y, (r() - .5f) * 70f, (r() - .5f) * 70f - 24f, t, .45f + r() * .4f, .8f + r() * 1.5f)
        }
        if (rubP >= 1f) {
            rubLock = true
            rubbing = false
            transform()
            return true
        }
        return false
    }

    fun rubEnd() {
        rubbing = false
        rubLock = false
    }

    /** 경로 바꾸기 — 색이 0.9초 동안 섞이고, 번쩍임 · 바닥 고리 · 불꽃이 함께 터진다. */
    fun transform() {
        setRoute(!rare, animate = true)
        flash = 1f
        rubP = 0f
        pulse = 1f
        omega = idleDir * max(abs(omega), 7f)
        waves += OrbWave(t, 1.1f, (if (rare) OrbPalette.RARE else OrbPalette.NORMAL).line)
        repeat(26) {
            val a = r() * TAU
            val v = 60f + r() * 120f
            rubSparks += OrbRubSpark(bx + cos(a) * ORB_R * .9f, by + sin(a) * ORB_R * .9f, cos(a) * v, sin(a) * v - 30f, t, .6f + r() * .5f, 1f + r() * 1.8f)
        }
    }

    /** 두드림(무대 단위 좌표) — 무대에서는 누른 쪽으로 돌리기, 깨우기 창에서는 금. 금이 갔으면 true. */
    fun tap(ux: Float, uy: Float): Boolean {
        pulse = 1f
        if (!interactive) {
            spin(if (ux < bx) -1 else 1)
            return false
        }
        if (phase != Phase.IDLE || taps >= 3) return false
        var x = (ux - bx) / ORB_R
        var y = (uy - by) / ORB_R
        if (hypot(x, y) > .92f) {
            val a = r() * TAU
            val d = .15f + r() * .45f
            x = cos(a) * d; y = sin(a) * d
        }
        taps++
        spin(if (x < 0f) -1 else 1)
        shake = 1f
        when (taps) {
            1 -> impacts += makeImpact(x, y, .8f)
            2 -> { impacts[0].target = 1.25f; impacts += makeImpact(x, y, 1f) }
            else -> {
                impacts.forEach { it.target = max(it.target, 1.5f) }
                impacts += makeImpact(x, y, 1.25f)
                if (pending != null) startConverge() else phase = Phase.WAIT
            }
        }
        spawnFlakes(x, y, if (taps == 3) 12 else 7)
        return true
    }

    /** 결과 등급 — 세 번 두드린 뒤라면 바로 모이기 시작한다. */
    fun setResult(rarity: String) {
        pending = rarity
        if (phase == Phase.WAIT) startConverge()
    }

    private fun startConverge() {
        val rar = pending ?: return
        lv = rarityLevel(rar); rc = burstColor(rar)
        impacts.forEach { it.target = 1.9f }
        // 가장자리까지 간 금 — 빛이 새어 나갈 자리
        val out = ArrayList<OrbLeak>()
        for (im in impacts) for (ray in im.rays) {
            val n = ray.n - 1
            val x = ray.xs[n]
            val y = ray.ys[n]
            if (x * x + y * y > .9f && out.size < 14) out += OrbLeak(kotlin.math.atan2(y, x), .035f + r() * .03f, .8f + r() * .7f)
        }
        leaks = out
        phase = Phase.CONVERGE
        pt = 0f
        onConverge?.invoke()
    }

    /** 건너뛰기 — 조각 없이 결과 빛만 남기고 카드로. */
    fun skip() {
        val rar = pending ?: return
        lv = rarityLevel(rar); rc = burstColor(rar)
        shards = emptyList(); grit = emptyList(); escape = emptyList(); puffs = emptyList(); needShards = false
        phase = Phase.REVEAL
        pt = 3f
        cardsReady = true
    }

    // ── 시간 ──────────────────────────────────────────────────────────────

    fun step(dt: Float) {
        t += dt
        val m = motion
        pulse = max(0f, pulse - dt * 1.8f)
        flash = max(0f, flash - dt * 1.5f)
        if (mixT < 1f) mixT = min(1f, mixT + dt / .9f)
        updatePalette()
        // 문지르기 — 손을 멈추면 천천히, 떼면 빨리 빠진다
        if (rubbing && t - lastRubT > .15f) rubP = max(0f, rubP - dt * .8f)
        if (!rubbing && rubP > 0f) rubP = max(0f, rubP - dt * 1.6f)
        // 돌기 — 목표(평소: 마지막 방향으로 천천히)로 지수적으로 돌아간다
        var target = idleDir * SPIN_IDLE
        var rate = SPIN_DECAY
        if (rubbing) { target = idleDir * (1.5f + 7f * rubP); rate = 2.5f }
        if (phase == Phase.WAIT) { target = idleDir * 3f; rate = 1.2f }
        if (phase == Phase.CONVERGE) { target = idleDir * (3f + 15f * conv); rate = 3f }
        omega += (target - omega) * (1f - exp(-dt * rate))
        spinA += omega * dt * m
        flowA += dt * m * (.22f + abs(omega) * .03f + (if (phase == Phase.CONVERGE) conv * 1.6f else 0f))
        ringA += dt * m * (1f + abs(omega) * .07f + (if (phase == Phase.CONVERGE) conv * 4f else 0f))
        // 가끔 속을 가로지르는 별똥별
        if (t > shootAt) {
            val a = r() * TAU
            val d = .15f + r() * .5f
            shoot = OrbShoot(cos(a) * d, sin(a) * d, a + PI_F * (.65f + r() * .7f), t)
            shootAt = t + 2.2f + r() * 3.5f
        }
        rubSparks.removeAll { t - it.born >= it.life }
        waves.removeAll { t - it.born >= it.dur }
        flakes.removeAll { t - it.born >= it.life }
        // 금 번짐 — 두드릴 때는 "팍"(0.2초), 기다릴 땐 천천히
        val gr = when (phase) {
            Phase.CONVERGE -> 3f
            Phase.WAIT -> 1.2f
            else -> 13f
        }
        for (im in impacts) im.g += (im.target - im.g) * min(1f, dt * gr)
        shake = max(0f, shake - dt * 6f)
        when (phase) {
            Phase.CONVERGE -> {
                pt += dt
                if (pt >= CONV) {
                    phase = Phase.SHATTER
                    pt = 0f
                    needShards = true
                    flareAt = t
                    waves += OrbWave(t, 1.3f, rc)
                    if (lv >= 2) waves += OrbWave(t + .16f, 1.4f, mixc(rc, WHITE, .4f))
                    onShatter?.invoke(lv)
                }
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
        if (!cardsReady && shatterAge >= ORB_CARDS_AT) cardsReady = true
    }

    /** 경로 색 섞기 + 문지르는 동안 반대 경로 쪽으로 반쯤. 바뀐 게 없으면 그대로 둔다. */
    private fun updatePalette() {
        val e = if (mixT < 1f) easeInOut(mixT) else 1f
        val rubMix = if (rubP > 0f && mixT >= 1f) rubP * .5f else 0f
        if (e == lastE && rubMix == lastRubMix && from === lastFrom && to === lastTo) return
        lastE = e; lastRubMix = rubMix; lastFrom = from; lastTo = to
        pal.setLerp(from, to, e)
        if (rubMix > 0f) pal.setLerp(pal, if (rare) OrbPalette.NORMAL else OrbPalette.RARE, rubMix)
        palVer++
    }

    // ── 그리기 ────────────────────────────────────────────────────────────

    /**
     * 한 프레임 — 캔버스 px 좌표에서 무대 원점이 ([ox], [oy]), 무대 1단위 = [k] px. [viewW]×[viewH] 는 화면 크기.
     * 카드가 뜨면 [stageAlpha] 로 무대를 지우고, 후광은 [auraShift] 만큼 ([auraX], [auraY])(무대 단위)로 옮긴다.
     */
    fun draw(
        c: Canvas, k: Float, ox: Float, oy: Float, viewW: Float, viewH: Float,
        stageAlpha: Float = 1f, auraShift: Float = 0f, auraX: Float = ORB_CX, auraY: Float = ORB_CY,
    ) {
        c.save()
        c.translate(ox, oy)
        c.scale(k, k)
        viewTop = -oy / k
        val m = motion
        val bob = sin(t * 1.15f) * 3.2f * m
        orbYW = YO + bob / (CAM_CE * ORB_S)
        var cx = ORB_CX + sin(t * .7f) * 1.1f * m
        var cy = ORB_CY - bob
        fade = stageAlpha
        if (stageAlpha > 0f) {
            prepCurtain()
            prepFlow()
            drawCircle(c)
            drawCurtain(c, front = false)
            drawBand(c)
            drawCore(c)
            drawFlow(c, front = false)
            drawRisers(c, front = false)
            drawMotes(c, front = false)
        }
        fade = 1f
        when (phase) {
            Phase.IDLE, Phase.WAIT, Phase.CONVERGE -> {
                val j = (when (phase) {
                    Phase.CONVERGE -> conv * 2.6f
                    Phase.WAIT -> .8f
                    else -> 0f
                }) + shake * 2.4f
                if (j > 0f) { cx += (r() - .5f) * j; cy += (r() - .5f) * j }
                bx = cx; by = cy
                drawBall(c, cx, cy, snap = false)
                if (phase == Phase.CONVERGE) drawLeaks(c, cx, cy)
            }
            Phase.SHATTER, Phase.REVEAL -> {
                if (needShards) buildShards(k)
                val s = shatterAge
                val ax = bx + (auraX - bx) * auraShift
                val ay = by + (auraY - by) * auraShift
                aura(c, ax, ay, ORB_R, clamp01((s - .4f) / 1f) * (1f - .35f * auraShift))
                if (stageAlpha > 0f) {
                    fade = stageAlpha
                    emblem(c, bx, by, ORB_R * .28f * backOut(clamp01((s - .1f) / .5f)), rc, clamp01((s - .05f) / .25f))
                    fade = 1f
                }
                if (phase == Phase.SHATTER) {
                    drawPillar(c, s)
                    drawEscape(c, s)
                    drawShards(c, s)
                    rarityBurst(c, bx, by, ORB_R, s)
                }
            }
        }
        drawFlakes(c)
        fade = stageAlpha
        if (stageAlpha > 0f) {
            drawCurtain(c, front = true)
            drawFlow(c, front = true)
            drawRisers(c, front = true)
            drawMotes(c, front = true)
        }
        fade = 1f
        drawRub(c)
        c.restore()
    }

    // ── 빛 · 반짝이 · 점 ───────────────────────────────────────────────────

    private fun glow(c: Canvas, col: Int, x: Float, y: Float, r: Float, a: Float, add: Boolean) {
        val aa = a * fade
        if (aa <= .002f || r <= .1f) return
        pGlow.colorFilter = OrbInk.filter(col)
        pGlow.alpha = (min(aa, 1f) * 255f).toInt()
        pGlow.xfermode = if (add) OrbInk.ADD else null
        rect.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(OrbInk.sprite, null, rect, pGlow)
    }

    /** 길게 늘인 빛(성운 가닥 · 먼 은하). */
    private fun glowE(c: Canvas, col: Int, x: Float, y: Float, r: Float, sy: Float, rot: Float, a: Float, add: Boolean) {
        val aa = a * fade
        if (aa <= .002f || r <= .1f) return
        c.save()
        c.translate(x, y)
        c.rotate(rot * RAD2DEG)
        c.scale(1f, sy)
        glow(c, col, 0f, 0f, r, a, add)
        c.restore()
    }

    /** 네 꼭지 반짝이(가운데를 조절점으로 휜 변). */
    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, a: Float, col: Int, add: Boolean) {
        val aa = a * fade
        if (aa <= .002f || r <= .1f) return
        sparkPath.rewind()
        sparkPath.moveTo(x, y - r)
        sparkPath.quadTo(x, y, x + r, y)
        sparkPath.quadTo(x, y, x, y + r)
        sparkPath.quadTo(x, y, x - r, y)
        sparkPath.quadTo(x, y, x, y - r)
        sparkPath.close()
        pFill.shader = null
        pFill.xfermode = if (add) OrbInk.ADD else null
        pFill.color = alphaOf(col, aa)
        c.drawPath(sparkPath, pFill)
        pFill.xfermode = null
    }

    /** 작은 둥근 점(지름 ≈ [s]) — 시안에서 점은 늘 더하기 합성. */
    private fun dot(c: Canvas, col: Int, x: Float, y: Float, s: Float, a: Float) {
        val aa = a * fade
        if (aa <= .002f) return
        pDot.color = alphaOf(col, aa)
        c.drawCircle(x, y, s * .56f, pDot)
    }

    /** 두 점 사이 그라데이션 선. */
    private fun gradLine(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, w: Float, add: Boolean) {
        if ((c0 ushr 24) == 0 && (c1 ushr 24) == 0) return
        pStroke.shader = LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        pStroke.color = BLACK
        pStroke.strokeWidth = w
        pStroke.xfermode = if (add) OrbInk.ADD else null
        c.drawLine(x0, y0, x1, y1, pStroke)
        pStroke.shader = null
        pStroke.xfermode = null
    }

    private fun wedge(c: Canvas, x: Float, y: Float, ang: Float, len: Float, w: Float, c0: Int, c1: Int) {
        pFill.shader = LinearGradient(x, y, x + cos(ang) * len, y + sin(ang) * len, c0, c1, Shader.TileMode.CLAMP)
        pFill.color = BLACK
        pFill.xfermode = OrbInk.ADD
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x + cos(ang - w) * len, y + sin(ang - w) * len)
        path.lineTo(x + cos(ang + w) * len, y + sin(ang + w) * len)
        path.close()
        c.drawPath(path, pFill)
        pFill.shader = null
        pFill.xfermode = null
    }

    // ── 마법진 ────────────────────────────────────────────────────────────

    private fun proj(x: Float, y: Float, z: Float) {
        val yc = y * CAM_CE - z * CAM_SE
        val zc = y * CAM_SE + z * CAM_CE
        val s = CAM_D / (CAM_D - zc)
        pX = ST_CX + x * s
        pY = ST_GY - yc * s
        pS = s
    }

    private fun flare(): Float {
        var f = 0f
        if (phase == Phase.CONVERGE) f += conv * .9f
        if (flareAt > 0f) f += 1.8f * exp(-(t - flareAt) * 1.8f)
        return 1f + f + flash * .8f + pulse * .15f
    }

    private fun dep(z: Float) = .42f + .58f * clamp01((z / CR + 1f) / 2f)

    private fun boost(a: Float): Float {
        var d = abs(((a - sweep) % TAU + TAU) % TAU)
        d = min(d, TAU - d)
        return exp(-d * d / .05f)
    }

    /** 땅 위 원 반쪽(뒤 = π..2π, 앞 = 0..π)을 원근으로 비춘 길. */
    private fun ringPath(out: Path, rr: Float, y: Float, front: Boolean): Path {
        out.rewind()
        for (i in 0..40) {
            val a = (if (front) 0f else PI_F) + i / 40f * PI_F
            proj(cos(a) * rr, y, sin(a) * rr)
            if (i == 0) out.moveTo(pX, pY) else out.lineTo(pX, pY)
        }
        return out
    }

    /** 땅 위 원 하나를 원근으로(뒤 반쪽 · 앞 반쪽 따로 — 뒤가 흐리다). [idx] 0..2 = 고정 안내선, -1 = [rr]·[y] 로 새로. */
    private fun ringLine(c: Canvas, idx: Int, rr: Float, y: Float, col: Int, aF: Float, aB: Float, w: Float) {
        pStroke.shader = null
        pStroke.strokeWidth = w
        pStroke.xfermode = OrbInk.ADD
        for (half in 0..1) {
            val p = if (idx >= 0) staticRings[idx * 2 + half] else ringPath(path, rr, y, front = half == 1)
            pStroke.color = alphaOf(col, (if (half == 1) aF else aB) * fade)
            c.drawPath(p, pStroke)
        }
        pStroke.xfermode = null
    }

    private fun drawCircle(c: Canvas) {
        val p = pal
        val rot = ringA * .03f
        val fl = flare()
        val ff = min(1.6f, fl)
        sweep = (t * .5f) % TAU
        // 바닥 빛(수정구가 비추는 웅덩이 + 은하수 뿌리)
        c.save()
        c.translate(ST_CX, ST_GY)
        c.scale(1f, CAM_SE * 1.05f)
        glow(c, p.glow, 0f, 0f, CR * 1.5f, .11f * fl, true)
        glow(c, mixc(p.glow, WHITE, .35f), 0f, 0f, CR * .55f, (.14f + sin(t * 1.15f) * .03f) * fl, true)
        c.restore()
        // 가는 안내선 — 바깥 하나는 땅에, 안쪽 둘은 층마다 떠 있다(높이가 달라 원근으로 어긋나 보인다)
        ringLine(c, 0, 0f, 0f, p.line, .1f * fl, .045f * fl, .5f)
        ringLine(c, 1, 0f, 0f, p.line, .15f * fl, .06f * fl, .5f)
        ringLine(c, 2, 0f, 0f, p.line, .2f * fl, .08f * fl, .5f)
        // 퍼져 나가는 고리(문지르기 완료 · 깨짐)
        for (w in waves) {
            val u = (t - w.born) / w.dur
            if (u < 0f || u > 1f) continue
            ringLine(c, -1, CR * (.08f + 1.3f * outC(u)), 1f, w.c, .8f * (1f - u), .4f * (1f - u), .5f + 1.8f * (1f - u))
        }
        // 별가루 고리
        for (d in ringDust) {
            val a = d.a + rot
            proj(cos(a) * CR * d.rr, d.y, sin(a) * CR * d.rr)
            dot(c, p.cstar, pX, pY, d.s * pS, dep(sin(a) * CR) * (.55f + .45f * sin(t * d.f + d.ph)) * (.7f + .8f * boost(a)) * ff)
        }
        // 밝은 별 — 이웃끼리 짧은 사슬로, 떠 있는 별은 땅까지 빛 실을 늘어뜨린다
        val n1 = ring1.size
        for (i in 0 until n1) {
            val s = ring1[i]
            val a = s.a + rot
            val x = cos(a) * CR * s.rr
            val z = sin(a) * CR * s.rr
            proj(x, s.y, z)
            r1x[i] = pX; r1y[i] = pY; r1s[i] = pS; r1X[i] = x; r1Z[i] = z; r1a[i] = a
        }
        pStroke.shader = null
        pStroke.strokeWidth = .5f
        pStroke.xfermode = OrbInk.ADD
        for (i in 0 until n1) {
            val j = (i + 1) % n1
            var gap = ring1[j].a - ring1[i].a
            if (gap < 0f) gap += TAU
            if (!ring1[i].link || gap > .3f) continue
            pStroke.color = alphaOf(p.line, .3f * dep((r1Z[i] + r1Z[j]) / 2f) * ff * fade)
            c.drawLine(r1x[i], r1y[i], r1x[j], r1y[j], pStroke)
        }
        pStroke.xfermode = null
        for (i in 0 until n1) {
            val s = ring1[i]
            val tw = .55f + .45f * sin(t * s.f + s.ph)
            val b = boost(r1a[i])
            val al = dep(r1Z[i]) * tw * (.75f + .9f * b) * ff
            val sz = s.s * r1s[i]
            if (s.y > 0f) {
                proj(r1X[i], 0f, r1Z[i])
                gradLine(c, r1x[i], r1y[i], pX, pY, alphaOf(p.line, .32f * al * fade), alphaOf(p.line, 0f), .5f, add = true)
            }
            if (s.big) {
                glow(c, p.glow, r1x[i], r1y[i], sz * 5f, .3f * al, true)
                sparkle(c, r1x[i], r1y[i], sz * (1.7f + b), al, p.cstar, true)
            } else dot(c, p.cstar, r1x[i], r1y[i], sz * 1.15f, al * .9f)
        }
        // 별자리 띠 — 반대로 돈다(시안의 합성 상태 그대로: 첫 무리의 선만 더하기)
        val rot2 = -ringA * .024f
        for ((gi, cs) in constel.withIndex()) {
            for ((q, s) in cs.withIndex()) {
                val a = s.a + rot2
                proj(cos(a) * CR * s.rr, 1f, sin(a) * CR * s.rr)
                csX[q] = pX; csY[q] = pY; csS[q] = pS; csZ[q] = sin(a); csA[q] = a
            }
            pStroke.strokeWidth = .5f
            pStroke.xfermode = if (gi == 0) OrbInk.ADD else null
            for (q in 0 until cs.size - 1) {
                pStroke.color = alphaOf(p.line, .26f * dep(csZ[q] * CR) * ff * fade)
                c.drawLine(csX[q], csY[q], csX[q + 1], csY[q + 1], pStroke)
            }
            pStroke.xfermode = null
            for ((q, s) in cs.withIndex()) {
                val tw = .6f + .4f * sin(t * 1.7f + s.ph)
                val al = dep(csZ[q] * CR) * tw * (.8f + .8f * boost(csA[q])) * ff
                glow(c, p.glow, csX[q], csY[q], s.s * csS[q] * 3.2f, .25f * al, true)
                sparkle(c, csX[q], csY[q], s.s * csS[q] * 1.5f, al, p.cstar, true)
            }
        }
        // 칠각별 {7/3} — 꼭짓점은 밝은 별, 변은 가는 선 + 그 위 잔별
        val rot3 = ringA * .05f
        for (i in 0 until 7) {
            val a = i / 7f * TAU + rot3
            proj(cos(a) * CR * .64f, 2f, sin(a) * CR * .64f)
            hvX[i] = pX; hvY[i] = pY; hvS[i] = pS; hvZ[i] = sin(a) * CR; hvA[i] = a
        }
        for (i in 0 until 7) {
            val j = (i + 3) % 7
            pStroke.shader = LinearGradient(
                hvX[i], hvY[i], hvX[j], hvY[j],
                intArrayOf(alphaOf(p.line, .24f * dep(hvZ[i]) * fl * fade), alphaOf(p.line, .06f * fl * fade), alphaOf(p.line, .24f * dep(hvZ[j]) * fl * fade)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
            )
            pStroke.color = BLACK
            pStroke.strokeWidth = .5f
            pStroke.xfermode = OrbInk.ADD
            c.drawLine(hvX[i], hvY[i], hvX[j], hvY[j], pStroke)
        }
        pStroke.shader = null
        pStroke.xfermode = null
        for (d in heptaDust) {
            val i = d.e
            val j = (i + 3) % 7
            dot(c, p.cstar, hvX[i] + (hvX[j] - hvX[i]) * d.u, hvY[i] + (hvY[j] - hvY[i]) * d.u + d.off * CR, d.s, (.35f + .35f * sin(t * 2.2f + d.ph)) * ff)
        }
        for (i in 0 until 7) {
            val al = dep(hvZ[i]) * (.75f + .25f * sin(t * 2f + hvA[i] * 3f)) * (.8f + boost(hvA[i])) * ff
            glow(c, p.glow, hvX[i], hvY[i], 9f * hvS[i], .3f * al, true)
            sparkle(c, hvX[i], hvY[i], 2.8f * hvS[i], al, p.cstar, true)
        }
        // 떠 있는 둘째 층(높이 14) · 셋째 층(높이 30, 땅까지 빛 실)
        val rot4 = -ringA * .08f
        for (s in ring2) {
            val a = s.a + rot4
            proj(cos(a) * CR * s.rr, 14f, sin(a) * CR * s.rr)
            dot(c, p.cstar, pX, pY, s.s * pS * 1.1f, dep(sin(a) * CR) * (.45f + .3f * sin(t * 2.4f + s.ph)) * ff)
        }
        val rot5 = ringA * .12f
        for ((i, s) in ring3.withIndex()) {
            val a = s.a + rot5
            val x = cos(a) * CR * s.rr
            val z = sin(a) * CR * s.rr
            proj(x, 30f, z)
            val tx = pX
            val ty = pY
            val ts = pS
            proj(x, 0f, z)
            gradLine(c, tx, ty, pX, pY, alphaOf(p.line, .3f * fl * fade), alphaOf(p.line, 0f), .55f, add = i == 0)
            glow(c, p.glow, tx, ty, 7f * ts, .3f * fl, true)
            sparkle(c, tx, ty, s.s * 1.6f * ts, (.7f + .3f * sin(t * 2f + s.ph)) * ff, p.cstar, true)
        }
        // 빛기둥 — 바깥 고리 별 몇 개에서 잠깐 솟는다
        for (i in 0 until 5) {
            val cyc = t / 2.7f + i / 5f
            val u = cyc % 1f
            val idx = floor(cyc * 7.31f + i * 13f).toInt() % n1
            val s = ring1[idx]
            val a = s.a + rot
            val x = cos(a) * CR * s.rr
            val z = sin(a) * CR * s.rr
            val h = 22f + ((idx * 37) % 40)
            val al = sin(PI_F * u) * dep(z) * .5f * min(1.6f, fl)
            proj(x, 0f, z)
            val x0 = pX
            val y0 = pY
            val s0 = pS
            proj(x, h * (.6f + .4f * u), z)
            gradLine(c, x0, y0, pX, pY, alphaOf(p.line, al * fade), alphaOf(p.line, 0f), .9f * s0, add = false)
        }
        // 가운데 — 은하수가 솟는 자리
        proj(0f, 3f, 0f)
        glow(c, p.band[0], pX, pY, 26f, .5f * min(1.8f, fl), true)
        glow(c, WHITE, pX, pY, 7f, .7f, true)
        sparkle(c, pX, pY, 6f + sin(t * 2.3f) * 1.2f, .9f, WHITE, true)
    }

    // ── 은하수 ────────────────────────────────────────────────────────────

    /**
     * 꽃잔 위의 한 점 — 오른 정도 [u](0 뿌리 ~ 1 가장자리) · 팔 · 팔에서 벗어난 각 · 반지름 배율 → 세계 좌표(fX, fY, fZ, 높이 비율 fH).
     * 마법진 한가운데서 가늘게 솟아 수정구 아래에서 활짝 벌어지고, 끝(u > .82)에서는 꽃잎처럼 바깥 · 아래로 살짝 휜다.
     * 잔 전체는 flowA 로 돌고, 모일 때는 조인다.
     */
    private fun cupPoint(u: Float, arm: Int, off: Float, rj: Float) {
        val w = min(u / .82f, 1f)
        val curl = if (u > .82f) (u - .82f) / .18f else 0f
        val y = YT * (1f - (1f - w).pow(1.6f)) - curl * curl * 16f
        val h = y / YT
        val rr = RW * (.05f + 1.18f * smooth(0f, 1f, h).pow(1.2f)) * rj * (1f + curl * .22f) * (1f - .3f * inC(conv))
        val a = arm * PI_F + off + u * TAU * 1.55f + flowA
        fX = cos(a) * rr; fY = y; fZ = sin(a) * rr; fH = h
    }

    /** 부드러운 성운 띠(밝은 덩이 + 팔 안쪽을 따라가는 어두운 먼지 띠). */
    private fun drawBand(c: Canvas) {
        val p = pal
        for (b in band) {
            val u = (t / b.life + b.p0) % 1f
            cupPoint(u, b.arm, b.off, b.rj)
            proj(fX, fY, fZ)
            val al = sin(PI_F * u) * b.al
            if (b.dark) glow(c, p.ink, pX, pY, b.s * pS, al, false)
            else glow(c, mixc(p.band[0], p.band[b.ci], smooth(.1f, 1f, fH)), pX, pY, b.s * pS, al, true)
        }
    }

    /** 가운데 빛 기둥 — 마법진 한가운데에서 수정구 바닥까지, 아래가 밝다. */
    private fun drawCore(c: Canvas) {
        val p = pal
        val fl = min(1.8f, flare())
        val top = orbYW - RW * .85f
        for (i in 0 until 10) {
            val u = i / 9f
            proj(0f, top * u, 0f)
            glow(c, mixc(p.band[0], p.band[1], u), pX, pY, (10f + 9f * u) * pS, (.28f - .18f * u) * fl, true)
        }
    }

    /** 별가루 자리 · 빛 · 색을 한 번 셈한다(뒤 반쪽 · 앞 반쪽이 나눠 그린다). 수정구 속으로 들어가는 것은 깨지기 전까지 가린다. */
    private fun prepFlow() {
        val p = pal
        val oy = orbYW
        val whole = shatterAge < 0f
        flFl = min(1.5f, flare())
        for (i in flow.indices) {
            val f = flow[i]
            val u = (t / f.life + f.p0) % 1f
            cupPoint(u, f.arm, f.off, f.rj)
            val front = fZ > 0f
            val dy = fY - oy
            if (whole && fX * fX + dy * dy + fZ * fZ < RW * RW * 1.03f) { flSide[i] = 0; continue }
            proj(fX, fY, fZ)
            var al = (if (u < .05f) u / .05f else 1f) * (if (u > .8f) clamp01(1f - (u - .8f) / .2f) else 1f) * (if (front) .85f else .6f)
            al *= (.6f + .4f * sin(t * f.f + f.ph)) * (if (f.core) 1.2f else .8f)
            flX[i] = pX; flY[i] = pY; flS[i] = f.s * pS; flA[i] = al
            flC[i] = mixc(p.band[0], p.band[f.ci], smooth(.15f, 1f, fH))
            flSide[i] = if (front) 2 else 1
        }
    }

    /** 별가루 — 두 팔에 몰려 감겨 오른다. */
    private fun drawFlow(c: Canvas, front: Boolean) {
        val want: Byte = if (front) 2 else 1
        for (i in flow.indices) {
            if (flSide[i] != want) continue
            if (flow[i].bright) {
                glow(c, flC[i], flX[i], flY[i], flS[i] * 4.5f, flA[i] * .4f, true)
                sparkle(c, flX[i], flY[i], flS[i] * 2.4f, flA[i], WHITE, true)
            } else dot(c, flC[i], flX[i], flY[i], flS[i], flA[i] * flFl)
        }
    }

    private fun prepCurtain() {
        for (i in 0..72) {
            val a = i / 72f * TAU + ringA * .02f
            val x = cos(a) * CR * 1.05f
            val z = sin(a) * CR * 1.05f
            val n = .5f + .5f * sin(a * 5f + t * .7f) * sin(a * 3f - t * .45f + 1.7f)
            proj(x, 0f, z)
            cuB0x[i] = pX; cuB0y[i] = pY
            proj(x, 14f + 40f * n, z)
            cuB1x[i] = pX; cuB1y[i] = pY
            cuN[i] = n; cuZ[i] = z
        }
    }

    /** 빛 커튼 — 바깥 고리에서 솟는 옅은 빛의 벽. 높이 · 밝기가 천천히 물결친다(이웃 기둥끼리 사각형으로 이어 칠한다). */
    private fun drawCurtain(c: Canvas, front: Boolean) {
        val col = pal.line
        val sh = curtainShader?.takeIf { curtainColor == col }
            ?: LinearGradient(0f, 0f, 0f, 1f, col, alphaOf(col, 0f), Shader.TileMode.CLAMP).also { curtainShader = it; curtainColor = col }
        val fl = min(1.6f, flare())
        pFill.shader = sh
        pFill.xfermode = OrbInk.ADD
        for (i in 1..72) {
            val j = i - 1
            if (((cuZ[i] + cuZ[j]) / 2f > 0f) != front) continue
            val nn = (cuN[i] + cuN[j]) / 2f
            val al = (.015f + .085f * nn * nn) * (if (front) 1f else .6f) * fl * fade
            if (al <= .002f) continue
            val yb = (cuB0y[i] + cuB0y[j]) / 2f
            val yt = (cuB1y[i] + cuB1y[j]) / 2f
            mtx.setScale(1f, yt - yb)
            mtx.postTranslate(0f, yb)
            sh.setLocalMatrix(mtx)
            pFill.color = alphaOf(BLACK, al)
            path.rewind()
            path.moveTo(cuB0x[j], cuB0y[j])
            path.lineTo(cuB0x[i], cuB0y[i])
            path.lineTo(cuB1x[i], cuB1y[i])
            path.lineTo(cuB1x[j], cuB1y[j])
            path.close()
            c.drawPath(path, pFill)
        }
        pFill.shader = null
        pFill.xfermode = null
    }

    /** 수정구 둘레를 3D 궤도로 도는 티끌. */
    private fun drawMotes(c: Canvas, front: Boolean) {
        val p = pal
        for (m in motes) {
            val a = m.a0 + t * m.w
            val x = cos(a) * m.rr
            val z = sin(a) * m.rr
            val y1 = -z * m.si
            val z1 = z * m.ci
            val xx = x * m.cy + z1 * m.sy
            val zz = -x * m.sy + z1 * m.cy
            if ((zz > 0f) != front) continue
            proj(xx, orbYW + y1, zz)
            val tw = .5f + .5f * sin(t * m.f + m.ph)
            val al = (if (front) .9f else .45f) * tw
            if (m.s > 1.4f) {
                glow(c, p.star, pX, pY, m.s * 3f * pS, al * .3f, true)
                sparkle(c, pX, pY, m.s * 1.6f * pS, al, WHITE, true)
            } else dot(c, p.star, pX, pY, m.s * pS, al)
        }
    }

    /** 마법진에서 피어올라 사라지는 반딧불. */
    private fun drawRisers(c: Canvas, front: Boolean) {
        val p = pal
        for (s in risers) {
            val u = (t / s.life + s.p0) % 1f
            val a = s.a + s.sw * u + ringA * .03f
            val x = cos(a) * s.d
            val z = sin(a) * s.d
            if ((z > 0f) != front) continue
            proj(x, s.h * u, z)
            sparkle(c, pX, pY, s.s * pS * (.8f + .4f * sin(t * 4f + s.ph)), sin(PI_F * u) * (if (front) .75f else .45f), p.star, true)
        }
    }

    // ── 수정구 ────────────────────────────────────────────────────────────

    /** 구슬 한 장 — [snap] 이면 깨지기 직전 사진용(바깥 번짐 없이). */
    private fun drawBall(c: Canvas, cx: Float, cy: Float, snap: Boolean) {
        val p = pal
        val r = ORB_R * (1f + pulse * .022f)
        if (!snap) glow(c, p.glow, cx, cy, r * 2.2f, .22f + pulse * .12f + rubP * .25f, true)
        c.save()
        clip.rewind()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        c.clipPath(clip)
        body.update(p.body[0], p.body[1], p.body[2])
        rect.set(cx - r, cy - r, cx + r, cy + r)
        pBody.xfermode = null
        c.drawBitmap(body.bitmap, null, rect, pBody)
        drawInner(c, cx, cy, r)
        c.restore()
        glassFront(c, cx, cy, r)
        if (impacts.isNotEmpty()) drawCracks(c, cx, cy, r)
    }

    /** 속 우주의 한 점 → 구슬 화면 자리(qx, qy) · 깊이 qz · 돌기 속도용 (qu, qv). 렌즈처럼 가운데가 조금 크고, 문지르면 휘저어진다. */
    private fun pr(x: Float, y: Float, z: Float) {
        val x1 = x * iCa + z * iSa
        val z1 = -x * iSa + z * iCa
        val y2 = y * iCt - z1 * iSt
        val z2 = y * iSt + z1 * iCt
        val rho2 = x1 * x1 + y2 * y2
        val l = (1.14f - .17f * rho2) * iShrink
        var sx = (x1 * iCr + y2 * iSr) * l
        var sy = (x1 * iSr - y2 * iCr) * l
        if (iSw > .001f) {
            val rho = sqrt(rho2)
            val an = iSw * (1f - min(1f, rho)) * sin(iSwT + rho * 5f)
            val cc = cos(an)
            val ss = sin(an)
            val nx = sx * cc - sy * ss
            sy = sx * ss + sy * cc
            sx = nx
        }
        qx = iCx + sx * iR; qy = iCy + sy * iR; qz = z2; qu = x1 * l; qv = z1 * l
    }

    private fun tint(col: Int) = if (innerGo) mixc(col, rc, innerCv) else col

    /** 속 우주 — 세로축(앞으로 기울어진, 천천히 흔들리는)을 따라 돈 3D 점들. 뒤 → 은하 → 앞 순서. */
    private fun drawInner(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = pal
        val cv = inC(conv)
        val go = going
        val tilt = TILT + .07f * sin(t * .37f)
        val roll = .06f * sin(t * .23f)
        tiltNow = tilt
        iCa = cos(spinA); iSa = sin(spinA); iCt = cos(tilt); iSt = sin(tilt); iCr = cos(roll); iSr = sin(roll)
        iShrink = 1f - cv * .92f; iSw = rubP * .9f; iSwT = t * 3.2f; iCx = cx; iCy = cy; iR = r
        innerCv = cv; innerGo = go
        for (f in far) {
            pr(f.x, f.y, f.z)
            glowE(c, p.gal[1], qx, qy, f.s * r, .32f, f.rot, .26f * (1f - cv), true)
        }
        nebPass(c, false, r, cv)
        for (i in stars.indices) {
            val s = stars[i]
            pr(s.x, s.y, s.z)
            stX[i] = qx; stY[i] = qy; stZ[i] = qz; stU[i] = qu; stV[i] = qv
        }
        starPass(c, false, r, cv)
        galaxy(c, r, cv)
        nebPass(c, true, r, cv)
        starPass(c, true, r, cv)
        val sh = shoot
        val su = if (sh != null) (t - sh.born) / .55f else 1f
        if (sh != null && su >= 0f && su < 1f && !go) {
            val hx = sh.x + cos(sh.dir) * .6f * su
            val hy = sh.y + sin(sh.dir) * .6f * su
            val tx = hx - cos(sh.dir) * .24f
            val ty = hy - sin(sh.dir) * .24f
            val fa = sin(PI_F * su)
            pStroke.shader = LinearGradient(cx + tx * r, cy + ty * r, cx + hx * r, cy + hy * r, alphaOf(p.star, 0f), alphaOf(WHITE, .85f * fa), Shader.TileMode.CLAMP)
            pStroke.color = BLACK
            pStroke.strokeWidth = .9f
            pStroke.strokeCap = Paint.Cap.ROUND
            pStroke.xfermode = OrbInk.ADD
            c.drawLine(cx + tx * r, cy + ty * r, cx + hx * r, cy + hy * r, pStroke)
            pStroke.shader = null
            pStroke.xfermode = null
            pStroke.strokeCap = Paint.Cap.BUTT
            glow(c, WHITE, cx + hx * r, cy + hy * r, 4f, .6f * fa, true)
        }
        glow(c, p.band[0], cx, cy + r * .84f, r * .55f, .16f + rubP * .12f, true)
        glow(c, p.glow, cx, cy, r * .75f, .1f + pulse * .14f + rubP * .12f, true)
        if (go) {
            glow(c, rc, cx, cy, r * (.15f + .55f * cv), cv * .95f, true)
            glow(c, WHITE, cx, cy, r * (.05f + .14f * cv), cv, true)
        }
    }

    private fun nebPass(c: Canvas, front: Boolean, r: Float, cv: Float) {
        val p = pal
        for (b in neb) {
            pr(b.x + sin(t * .31f + b.ph) * .035f, b.y + cos(t * .27f + b.ph * 1.3f) * .035f, b.z + sin(t * .23f + b.ph * .7f) * .035f)
            if ((qz >= 0f) != front) continue
            val dep = (qz + 1f) / 2f
            val sz = b.s * r * (.7f + .5f * dep) * (1f - cv * .5f)
            val rot = b.rot + spinA * .25f
            if (b.dark) glowE(c, p.ink, qx, qy, sz, b.el, rot, b.al * (.55f + .45f * dep) * (1f - cv), false)
            else glowE(c, tint(p.fog[b.c]), qx, qy, sz, b.el, rot, b.al * (.5f + .65f * dep) * (1f + pulse * .6f + rubP * .5f + cv * 1.4f), true)
        }
    }

    /** 별밭 — 빨리 돌면 0.022초 노출 동안 움직인 만큼 잔상으로 늘어진다(길이는 반지름의 0.2배까지). */
    private fun starPass(c: Canvas, front: Boolean, r: Float, cv: Float) {
        val om = omega * motion
        val k = r / 84f
        val st = sin(tiltNow)
        val col = tint(pal.star)
        val colW = mixc(col, WHITE, .5f)
        val cap = r * .2f
        pStroke.shader = null
        pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.xfermode = OrbInk.ADD
        for (i in stars.indices) {
            val z = stZ[i]
            if ((z >= 0f) != front) continue
            val s = stars[i]
            val dep = (z + 1f) / 2f
            val tw = .55f + .45f * sin(t * s.f + s.ph)
            val al = (.2f + .8f * dep) * tw * (1f - cv * .4f)
            val sz = s.s * (.55f + .75f * dep) * k
            var vx = om * stV[i] * r * .022f
            var vy = -om * stU[i] * st * r * .022f
            val vl = sqrt(vx * vx + vy * vy)
            if (vl > cap) { vx *= cap / vl; vy *= cap / vl }
            val x = stX[i]
            val y = stY[i]
            if (vx * vx + vy * vy > 2f) {
                val aa = al * .85f * fade
                if (aa > .002f) {
                    pStroke.color = alphaOf(col, aa)
                    pStroke.strokeWidth = max(.5f, sz * .9f)
                    c.drawLine(x - vx, y - vy, x, y, pStroke)
                }
            } else if (s.big) {
                glow(c, col, x, y, sz * 3.2f, al * .45f, true)
                sparkle(c, x, y, sz * (1.6f + tw), al, WHITE, true)
            } else dot(c, colW, x, y, sz, al)
        }
        pStroke.xfermode = null
        pStroke.strokeCap = Paint.Cap.BUTT
    }

    /** 은하판의 한 점 — 판을 회전축에 비스듬히(.3 · .42rad) 눕혀 구슬이 돌 때 납작했다 둥글었다 한다. */
    private fun gp(x: Float, y: Float, z: Float) {
        val x1 = x * GB_C - y * GB_S
        val y1 = x * GB_S + y * GB_C
        pr(x1, y1 * GA_C - z * GA_S, y1 * GA_S + z * GA_C)
    }

    /** 나선 은하 — 판 빛(판 방향 그대로의 타원) + 별 420개(안쪽일수록 빨리 돈다) + 밝은 핵. */
    private fun galaxy(c: Canvas, r: Float, cv: Float) {
        val p = pal
        val k = r / 84f
        if (galColVer != palVer) {
            for (i in gal.indices) {
                val rr = gal[i].rr
                galCol[i] = if (rr < .22f) mixc(p.gal[0], p.gal[1], rr / .22f) else mixc(p.gal[1], p.gal[2], clamp01((rr - .22f) / .5f))
            }
            galColVer = palVer
        }
        gp(0f, 0f, 0f)
        val ox = qx
        val oy = qy
        gp(.62f, 0f, 0f)
        val ax = qx - ox
        val ay = qy - oy
        gp(0f, 0f, .62f)
        val vx = qx - ox
        val vy = qy - oy
        c.save()
        mtxVals[0] = ax; mtxVals[1] = vx; mtxVals[2] = ox
        mtxVals[3] = ay; mtxVals[4] = vy; mtxVals[5] = oy
        mtxVals[6] = 0f; mtxVals[7] = 0f; mtxVals[8] = 1f
        mtx.setValues(mtxVals)
        c.concat(mtx)
        glow(c, tint(p.gal[1]), 0f, 0f, 1f, .2f, true)
        glow(c, tint(p.gal[2]), 0f, 0f, 1.25f, .1f, true)
        c.restore()
        for (i in gal.indices) {
            val s = gal[i]
            val a = s.a + t * s.w
            gp(cos(a) * s.rr + s.ox, s.oy, sin(a) * s.rr + s.oz)
            val dep = (qz + 1f) / 2f
            val col = if (innerGo) mixc(galCol[i], rc, innerCv) else galCol[i]
            dot(c, col, qx, qy, s.s * k * (.7f + .5f * dep), (.32f + .45f * dep) * (.7f + .3f * sin(t * 2f + s.ph)) * (1f - cv * .3f))
        }
        glow(c, tint(p.gal[0]), ox, oy, r * .2f, .5f, true)
        glow(c, WHITE, ox, oy, r * .055f, .75f, true)
    }

    private fun buildGlass() {
        val p = pal
        val rr = ORB_R
        gFresnel = RadialGradient(
            0f, 0f, rr,
            intArrayOf(alphaOf(p.rim, 0f), alphaOf(p.rim, 0f), alphaOf(p.rim, .1f), alphaOf(p.rim, .38f), alphaOf(p.rim, .12f)),
            floatArrayOf(0f, .78f, .954f, .991f, 1f), Shader.TileMode.CLAMP,
        )
        gCaustic = RadialGradient(
            0f, 0f, rr * .62f, intArrayOf(alphaOf(p.caustic, .45f), alphaOf(p.caustic, .15f), alphaOf(p.caustic, 0f)),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
        )
        gCausticArc = LinearGradient(
            -rr * .7f, 0f, rr * .7f, 0f, intArrayOf(alphaOf(p.caustic, 0f), alphaOf(p.caustic, .5f), alphaOf(p.caustic, 0f)),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
        )
        glassVer = palVer
    }

    /**
     * 유리 앞면 — 프레넬 테 · 바닥 집광(타원 + 호) · 창 반사 초승달 · 부드러운 반사광 · 오른쪽 아래 가는 되비침 · 점 반짝임 · 가는 테.
     * 구슬 가운데 기준으로 그리고 두드림 출렁임([r] / [ORB_R])만큼 늘린다(그라데이션을 프레임마다 새로 만들지 않게).
     */
    private fun glassFront(c: Canvas, cx: Float, cy: Float, r: Float) {
        if (glassVer != palVer) buildGlass()
        val p = pal
        val rr = ORB_R
        val sc = r / rr
        c.save()
        c.translate(cx, cy)
        c.scale(sc, sc)
        pFill.color = BLACK
        pFill.xfermode = OrbInk.ADD
        pFill.shader = gFresnel
        c.drawCircle(0f, 0f, rr, pFill)
        // 바닥 집광 — 아래 은하수 빛이 유리 바닥에 모인다
        c.save()
        c.translate(0f, rr * .66f)
        c.scale(1f, .42f)
        pFill.shader = gCaustic
        c.drawCircle(0f, 0f, rr * .62f, pFill)
        c.restore()
        pStroke.color = BLACK
        pStroke.xfermode = OrbInk.ADD
        pStroke.shader = gCausticArc
        pStroke.strokeWidth = rr * .022f
        pStroke.strokeCap = Paint.Cap.ROUND
        rect.set(-rr * .91f, -rr * .91f, rr * .91f, rr * .91f)
        c.drawArc(rect, .24f * 180f, (.76f - .24f) * 180f, false, pStroke)
        pStroke.shader = null
        // 창 반사 — 왼쪽 위 가장자리를 따라 휜 초승달
        pFill.shader = gCrescent
        c.drawPath(crescent, pFill)
        // 부드러운 반사광(넓게 번진 하늘빛)
        c.save()
        c.translate(-rr * .3f, -rr * .38f)
        c.rotate(-.7f * RAD2DEG)
        c.scale(1f, .6f)
        pFill.shader = gSoft
        c.drawCircle(0f, 0f, rr * .4f, pFill)
        c.restore()
        pFill.shader = null
        pFill.xfermode = null
        // 오른쪽 아래 가는 되비침
        pStroke.color = alphaOf(mixc(p.rim, WHITE, .5f), .3f)
        pStroke.strokeWidth = rr * .014f
        rect.set(-rr * .89f, -rr * .89f, rr * .89f, rr * .89f)
        c.drawArc(rect, .06f * 180f, (.2f - .06f) * 180f, false, pStroke)
        pStroke.xfermode = null
        pStroke.strokeCap = Paint.Cap.BUTT
        glow(c, WHITE, -rr * .52f, -rr * .71f, rr * .07f, .8f, true)
        sparkle(c, -rr * .52f, -rr * .71f, rr * .06f, .9f, WHITE, true)
        pStroke.color = alphaOf(p.rim, .35f)
        pStroke.strokeWidth = 1f
        c.drawCircle(0f, 0f, rr, pStroke)
        c.restore()
    }

    // ── 문지르기 고리 · 불꽃 ───────────────────────────────────────────────

    private fun drawRub(c: Canvas) {
        val p = pal
        if (rubP > .01f) {
            val tc = (if (rare) OrbPalette.NORMAL else OrbPalette.RARE).line
            val rr = ringR
            val a0 = -PI_F / 2f
            val a1 = a0 + rubP * TAU
            rect.set(bx - rr, by - rr, bx + rr, by + rr)
            pStroke.shader = null
            pStroke.xfermode = OrbInk.ADD
            pStroke.strokeCap = Paint.Cap.ROUND
            pStroke.color = alphaOf(tc, .09f)
            pStroke.strokeWidth = .8f
            c.drawCircle(bx, by, rr, pStroke)
            pStroke.color = alphaOf(tc, .22f)
            pStroke.strokeWidth = 3.4f
            c.drawArc(rect, a0 * RAD2DEG, rubP * 360f, false, pStroke)
            pStroke.color = alphaOf(mixc(tc, WHITE, .25f), .95f)
            pStroke.strokeWidth = 1.1f
            c.drawArc(rect, a0 * RAD2DEG, rubP * 360f, false, pStroke)
            pStroke.xfermode = null
            pStroke.strokeCap = Paint.Cap.BUTT
            val hx = bx + cos(a1) * rr
            val hy = by + sin(a1) * rr
            glow(c, tc, hx, hy, 10f, .85f, true)
            sparkle(c, hx, hy, 4.6f, 1f, WHITE, true)
        }
        for (s in rubSparks) {
            val age = t - s.born
            val a = 1f - age / s.life
            if (a <= 0f || age < 0f) continue
            sparkle(c, s.x + s.vx * age, s.y + s.vy * age + 50f * age * age, s.s * 2f * (.5f + .5f * a), a, p.star, true)
        }
        if (flash > 0f) {
            val f = flash
            glow(c, WHITE, bx, by, ORB_R * (1.1f + (1f - f) * 1.4f), f * .65f, true)
            glow(c, p.glow, bx, by, ORB_R * (1.8f + (1f - f) * 2.2f), f * .5f, true)
        }
    }

    // ── 금 ────────────────────────────────────────────────────────────────

    /** 금 위 [s] 거리의 점 → (cpx, cpy). 금이 거기까지 안 가면 false. */
    private fun crackPoint(ray: OrbRay, s: Float): Boolean {
        if (s > ray.len) return false
        for (j in 1 until ray.n) if (ray.cum[j] >= s) {
            val u = (s - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
            cpx = ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u
            cpy = ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u
            return true
        }
        return false
    }

    /** 곧게 뻗는 방사 금 8~12(가끔 꺾이고 Y 로 갈라진다) + 그 사이를 곧은 선으로 잇는 다각형 거미줄 + 부서진 점 + 잔금. */
    private fun makeImpact(x: Float, y: Float, target: Float): OrbImpact {
        val rays = ArrayList<OrbRay>()
        val mains = ArrayList<OrbRay>()
        val n = 8 + (r() * 5f).toInt().coerceAtMost(4)
        val base = r() * TAU
        // 마디마다 빛을 받는 정도(0 어둑 · 1 보통 · 2 번쩍) — 몇 마디씩 이어지게 바꾼다
        fun grow(sx: Float, sy: Float, ang: Float, start: Float, maxLen: Float, wob: Float): OrbRay {
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            val cum = ArrayList<Float>()
            val lum = ArrayList<Int>()
            xs += sx; ys += sy; cum += 0f
            var px = sx
            var py = sy
            var a = ang
            var len = 0f
            var lum0 = (r() * 3f).toInt().coerceAtMost(2)
            while (len < maxLen) {
                val step = .05f + .09f * r()
                a += (r() - .5f) * wob + (if (r() < .12f) (r() - .5f) * .5f else 0f)
                px += cos(a) * step; py += sin(a) * step; len += step
                if (r() < .32f) lum0 = (r() * 3f).toInt().coerceAtMost(2)
                xs += px; ys += py; cum += len; lum += lum0
                if (px * px + py * py > 1.02f) break
            }
            return OrbRay(xs.toFloatArray(), ys.toFloatArray(), cum.toFloatArray(), lum.toIntArray(), start, len)
        }
        for (i in 0 until n) {
            val ang = base + i.toFloat() / n * TAU + (r() - .5f) * (TAU / n) * .6f
            val ray = grow(x, y, ang, 0f, .5f + r() * 1.6f, .1f)
            rays += ray; mains += ray
            val forks = if (r() < .55f) 1 + (if (r() < .3f) 1 else 0) else 0
            for (f in 0 until forks) {
                if (ray.n <= 4) break
                val kk = 2 + (r() * (ray.n - 3)).toInt().coerceAtMost(ray.n - 4)
                rays += grow(ray.xs[kk], ray.ys[kk], ang + (if (r() < .5f) -1f else 1f) * (.3f + r() * .45f), ray.cum[kk], .15f + r() * .55f, .12f)
            }
        }
        val rings = ArrayList<OrbRingSeg>()
        for (d in floatArrayOf(.05f, .11f, .19f, .3f, .45f)) {
            if (r() < .2f) continue
            for (i in 0 until n) {
                if (r() < .38f) continue
                if (!crackPoint(mains[i], d * (.8f + .4f * r()))) continue
                val ax = cpx
                val ay = cpy
                if (!crackPoint(mains[(i + 1) % n], d * (.8f + .4f * r()))) continue
                rings += OrbRingSeg(ax, ay, (ax + cpx) / 2f + (r() - .5f) * .03f, (ay + cpy) / 2f + (r() - .5f) * .03f, cpx, cpy, d)
            }
        }
        val nm = 14 + (r() * 8f).toInt().coerceAtMost(7)
        val micro = FloatArray(nm * 4)
        for (i in 0 until nm) {
            val a = r() * TAU
            val l = .015f + r() * .05f
            micro[i * 4] = x + cos(a) * .008f; micro[i * 4 + 1] = y + sin(a) * .008f
            micro[i * 4 + 2] = x + cos(a) * l; micro[i * 4 + 3] = y + sin(a) * l
        }
        val cr = .022f + r() * .018f
        val crush = FloatArray(14)
        for (i in 0 until 7) {
            val a = i / 7f * TAU + (r() - .5f) * .6f
            val rr = cr * (.6f + .6f * r())
            crush[i * 2] = x + cos(a) * rr; crush[i * 2 + 1] = y + sin(a) * rr
        }
        val glints = List(3) { OrbGlint((r() * mains.size).toInt().coerceAtMost(mains.size - 1), .35f + r() * .5f, r()) }
        return OrbImpact(x, y, rays, rings, micro, crush, glints, target)
    }

    private fun spawnFlakes(x: Float, y: Float, n: Int) {
        repeat(n) {
            val a = r() * TAU
            val sp = 40f + r() * 110f
            val s = 1.2f + r() * 2.4f
            val tri = FloatArray(6)
            for (j in 0..2) {
                val q = j / 3f * TAU + (r() - .5f) * 1.2f
                tri[j * 2] = cos(q) * s
                tri[j * 2 + 1] = sin(q) * s * (.5f + r() * .5f)
            }
            flakes += OrbFlake(bx + x * ORB_R, by + y * ORB_R, cos(a) * sp, sin(a) * sp - 60f, .4f + r() * 1.6f, t, .5f + r() * .45f, r() * TAU, (r() - .5f) * 20f, tri)
        }
    }

    /** 두드릴 때 튀는 작은 유리 조각(앞으로 다가오며 뒤집힌다). */
    private fun drawFlakes(c: Canvas) {
        if (flakes.isEmpty()) return
        val fillC = mixc(pal.rim, WHITE, .5f)
        pFill.shader = null
        pStroke.shader = null
        for (f in flakes) {
            val age = t - f.born
            val a = 1f - age / f.life
            if (a <= 0f) continue
            val sc = 1f + f.vz * age
            c.save()
            c.translate(f.x + f.vx * age, f.y + f.vy * age + .5f * GRAV * age * age)
            c.rotate((f.rot + f.vr * age) * RAD2DEG)
            c.scale(sc * cos(age * 9f + f.rot), sc)
            path.rewind()
            path.moveTo(f.tri[0], f.tri[1])
            path.lineTo(f.tri[2], f.tri[3])
            path.lineTo(f.tri[4], f.tri[5])
            path.close()
            pFill.xfermode = OrbInk.ADD
            pFill.color = alphaOf(fillC, .45f * a)
            c.drawPath(path, pFill)
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(WHITE, .8f * a)
            pStroke.strokeWidth = .5f
            c.drawPath(path, pStroke)
            c.restore()
        }
        pFill.xfermode = null
        pStroke.xfermode = null
    }

    /**
     * 금 — 마디를 밝기별 세 갈래 길로 나눠 그린다(같은 금도 어떤 마디는 번쩍이고 어떤 마디는 거의 안 보인다, 진짜 유리처럼).
     * 어두운 그림자 → 번짐 → 밝은 심 → 거미줄 → 비켜 난 반사 → 잔금 → 부서진 점 · 반짝 지나가는 빛.
     */
    private fun drawCracks(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = pal
        val go = phase == Phase.CONVERGE || phase == Phase.WAIT
        val cv = conv
        val col = if (phase == Phase.CONVERGE) mixc(p.rim, rc, cv) else p.rim
        c.save()
        clip.rewind()
        clip.addCircle(cx, cy, r * .995f, Path.Direction.CW)
        c.clipPath(clip)
        for (pa in lit) pa.rewind()
        ringsPath.rewind()
        microPath.rewind()
        for (im in impacts) {
            val gg = im.g
            if (gg <= .001f) continue
            for (ray in im.rays) {
                val vis = gg - ray.start
                if (vis <= 0f) continue
                for (j in 1 until ray.n) {
                    val c0 = ray.cum[j - 1]
                    if (c0 >= vis) break
                    var x1 = ray.xs[j]
                    var y1 = ray.ys[j]
                    if (ray.cum[j] > vis) {
                        val u = (vis - c0) / (ray.cum[j] - c0)
                        x1 = ray.xs[j - 1] + (x1 - ray.xs[j - 1]) * u
                        y1 = ray.ys[j - 1] + (y1 - ray.ys[j - 1]) * u
                    }
                    val pa = lit[ray.lum[j - 1]]
                    pa.moveTo(cx + ray.xs[j - 1] * r, cy + ray.ys[j - 1] * r)
                    pa.lineTo(cx + x1 * r, cy + y1 * r)
                }
            }
            for (s in im.rings) {
                if (gg < s.d * 1.25f + .05f) continue
                ringsPath.moveTo(cx + s.ax * r, cy + s.ay * r)
                ringsPath.lineTo(cx + s.mx * r, cy + s.my * r)
                ringsPath.lineTo(cx + s.bx * r, cy + s.by * r)
            }
            val mk = min(1f, gg * 6f)
            val m = im.micro
            for (q in 0 until m.size / 4) {
                val x0 = m[q * 4]
                val y0 = m[q * 4 + 1]
                microPath.moveTo(cx + x0 * r, cy + y0 * r)
                microPath.lineTo(cx + (x0 + (m[q * 4 + 2] - x0) * mk) * r, cy + (y0 + (m[q * 4 + 3] - y0) * mk) * r)
            }
        }
        val ga = if (go) .45f + .55f * cv else .3f + pulse * .3f
        val light = mixc(col, WHITE, .7f)
        pStroke.shader = null
        pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.strokeJoin = Paint.Join.ROUND
        pStroke.xfermode = null
        pStroke.color = argb(0, 0, 0, .3f)
        pStroke.strokeWidth = 1.3f
        for (pa in lit) c.drawPath(pa, pStroke)
        c.drawPath(ringsPath, pStroke)
        pStroke.xfermode = OrbInk.ADD
        pStroke.color = alphaOf(col, ga * .2f)
        pStroke.strokeWidth = 2.3f
        c.drawPath(lit[1], pStroke)
        c.drawPath(lit[2], pStroke)
        for (i in 0..2) {
            pStroke.color = alphaOf(light, (.55f + ga * .45f) * LUM[i])
            pStroke.strokeWidth = .46f + i * .1f
            c.drawPath(lit[i], pStroke)
        }
        pStroke.color = alphaOf(light, .25f + ga * .3f)
        pStroke.strokeWidth = .4f
        c.drawPath(ringsPath, pStroke)
        c.save()
        c.translate(.7f, .55f)
        pStroke.color = alphaOf(WHITE, .14f)
        c.drawPath(lit[2], pStroke)
        c.restore()
        pStroke.color = alphaOf(WHITE, .5f)
        c.drawPath(microPath, pStroke)
        pStroke.xfermode = null
        pStroke.strokeCap = Paint.Cap.BUTT
        pStroke.strokeJoin = Paint.Join.MITER
        for ((n, im) in impacts.withIndex()) {
            val kk = min(1f, im.g * 5f)
            val ix = cx + im.x * r
            val iy = cy + im.y * r
            path.rewind()
            for (q in 0 until 7) {
                val x = cx + im.crush[q * 2] * r
                val y = cy + im.crush[q * 2 + 1] * r
                if (q == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            pFill.shader = null
            // 시안의 합성 상태 그대로 — 첫 부서진 점만 더하기(뒤는 빛을 그린 뒤라 보통 합성)
            pFill.xfermode = if (n == 0) OrbInk.ADD else null
            pFill.color = alphaOf(WHITE, .24f * kk)
            c.drawPath(path, pFill)
            pFill.xfermode = null
            glow(c, col, ix, iy, r * .16f * kk, .3f + ga * .4f, true)
            glow(c, WHITE, ix, iy, r * .05f * kk, .9f * kk, true)
            for (gl in im.glints) {
                val ray = im.rays[gl.ray]
                val vis = min(im.g - ray.start, ray.len)
                if (vis <= .05f) continue
                val u = (t * gl.v + gl.ph) % 1f
                if (crackPoint(ray, u * vis)) {
                    val su = sin(u * PI_F)
                    sparkle(c, cx + cpx * r, cy + cpy * r, 2.8f * su + .5f, .9f * su, WHITE, true)
                }
            }
        }
        c.restore()
    }

    /** 모이는 동안 금 사이로 새어 나오는 빛줄기. */
    private fun drawLeaks(c: Canvas, cx: Float, cy: Float) {
        val cv = conv
        val light = mixc(rc, WHITE, .6f)
        for ((i, lk) in leaks.withIndex()) {
            val x = cx + cos(lk.a) * ORB_R * .97f
            val y = cy + sin(lk.a) * ORB_R * .97f
            val len = ORB_R * (.3f + 1.5f * cv) * lk.l * (.85f + .15f * sin(t * 23f + i * 1.7f))
            wedge(c, x, y, lk.a, len, lk.w * 1.8f, alphaOf(rc, .26f * cv), alphaOf(rc, 0f))
            wedge(c, x, y, lk.a, len * .8f, lk.w * .45f, alphaOf(light, .9f * cv), alphaOf(light, 0f))
            glow(c, light, x, y, ORB_R * .13f, .55f * cv, true)
        }
    }

    // ── 깨짐 ──────────────────────────────────────────────────────────────

    /** 볼록 다각형([x0,y0,x1,y1,…])을 반평면 n·(p - m) ≤ 0 으로 자른다. */
    private fun clipHalf(poly: FloatArray, nx: Float, ny: Float, mx: Float, my: Float): FloatArray {
        val n = poly.size / 2
        val out = FloatArray((n + 1) * 2 + 2)
        var o = 0
        for (i in 0 until n) {
            val ax = poly[i * 2]
            val ay = poly[i * 2 + 1]
            val j = (i + 1) % n
            val bx = poly[j * 2]
            val by = poly[j * 2 + 1]
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

    private fun polyArea(p: FloatArray): Float {
        val n = p.size / 2
        var s = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            s += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return abs(s) / 2f
    }

    /** 누른 자리 둘레로 씨앗을 여덟 겹 동심원(바깥일수록 성기게) + 가장자리에 뿌려 보로노이로 자른다. 큰 칸은 한 번 더 쪼갠다. */
    private fun shardCells(ix: Float, iy: Float): List<FloatArray> {
        val seeds = ArrayList<FloatArray>()
        seeds += floatArrayOf(ix, iy)
        for (k in 0 until 8) {
            val rad = .045f * 1.47f.pow(k)
            val n = 5 + 4 * k
            val off = r() * TAU
            for (i in 0 until n) {
                val a = off + i.toFloat() / n * TAU + (r() - .5f) * (TAU / n) * .75f
                val rr = rad * (.72f + .56f * r())
                val sx = ix + cos(a) * rr
                val sy = iy + sin(a) * rr
                if (sx * sx + sy * sy < 1.5f) seeds += floatArrayOf(sx, sy)
            }
        }
        for (i in 0 until 14) {
            val a = i / 14f * TAU + r() * .3f
            val rr = .9f + r() * .15f
            val sx = cos(a) * rr
            val sy = sin(a) * rr
            if (hypot(sx - ix, sy - iy) > .35f) seeds += floatArrayOf(sx, sy)
        }
        val disk = FloatArray(112).also { d -> for (i in 0 until 56) { d[i * 2] = cos(i / 56f * TAU); d[i * 2 + 1] = sin(i / 56f * TAU) } }
        val cells = ArrayList<FloatArray>()
        for (s in seeds) {
            var poly = disk
            for (q in seeds) {
                if (q === s) continue
                poly = clipHalf(poly, q[0] - s[0], q[1] - s[1], (s[0] + q[0]) / 2f, (s[1] + q[1]) / 2f)
                if (poly.size < 6) break
            }
            if (poly.size < 6) continue
            if (polyArea(poly) > .018f && r() < .75f) {
                var mx = 0f
                var my = 0f
                val n = poly.size / 2
                for (i in 0 until n) { mx += poly[i * 2]; my += poly[i * 2 + 1] }
                mx /= n; my /= n
                val ang = r() * PI_F
                val a = clipHalf(poly, cos(ang), sin(ang), mx, my)
                val b = clipHalf(poly, -cos(ang), -sin(ang), mx, my)
                if (a.size >= 6) cells += a
                if (b.size >= 6) cells += b
            } else cells += poly
        }
        return cells
    }

    /** 깨지기 직전 구슬을 한 장 찍어(소프트웨어 캔버스) 조각마다 입힌다 + 유리 가루 · 터져 나오는 별줄기 · 성운 덩이. */
    private fun buildShards(k: Float) {
        needShards = false
        val rr = ORB_R
        val sk = min(k, 3f)
        val pad = 3f
        val ox = bx - rr - pad
        val oy = by - rr - pad
        val side = ceil((rr + pad) * 2f * sk).toInt().coerceAtLeast(8)
        val snap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(snap).apply {
            scale(sk, sk)
            translate(-ox, -oy)
            drawBall(this, bx, by, snap = true)
        }
        val last = impacts.lastOrNull()
        val ix = last?.x ?: 0f
        val iy = last?.y ?: 0f
        shards = shardCells(ix, iy).map { poly ->
            val n = poly.size / 2
            var ux = 0f
            var uy = 0f
            for (i in 0 until n) { ux += poly[i * 2]; uy += poly[i * 2 + 1] }
            ux /= n; uy /= n
            val sp = Path()
            for (i in 0 until n) {
                val px = (poly[i * 2] - ux) * rr
                val py = (poly[i * 2 + 1] - uy) * rr
                if (i == 0) sp.moveTo(px, py) else sp.lineTo(px, py)
            }
            sp.close()
            var dx = ux - ix
            var dy = uy - iy
            val dist = hypot(dx, dy).let { if (it == 0f) 1f else it }
            dx /= dist; dy /= dist
            val cl = hypot(ux, uy).let { if (it == 0f) 1f else it }
            var vx = dx * .7f + ux / cl * .3f
            var vy = dy * .7f + uy / cl * .3f
            val vl = hypot(vx, vy).let { if (it == 0f) 1f else it }
            vx /= vl; vy /= vl
            val speed = rr * (1.3f + 2.1f * r()) * (1.3f - .5f * min(1f, dist))
            val x0 = bx + ux * rr
            val y0 = by + uy * rr
            val shader = BitmapShader(snap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(1f / sk, 1f / sk); postTranslate(ox - x0, oy - y0) })
            }
            val toCam = r() < .38f
            OrbShard(
                sp, x0, y0, vx * speed, vy * speed - rr * .6f,
                if (toCam) rr * (1.2f + 2.2f * r()) else rr * (r() - .5f) * .5f,
                (r() - .5f) * 8f, (r() - .5f) * 14f, if (r() < .4f) (r() - .5f) * 9f else 0f, r() * TAU,
                1f + r() * .75f, dist * .05f, shader,
            )
        }
        grit = List(200) {
            val a = r() * TAU
            val sp = rr * (.8f + 2.8f * r())
            OrbGrit(
                bx + (ix + (r() - .5f) * .8f) * rr, by + (iy + (r() - .5f) * .8f) * rr,
                cos(a) * sp, sin(a) * sp - rr * .5f, .5f + r() * 1.3f, .5f + r() * .9f, r() * TAU,
            )
        }
        val p = pal
        val cols = intArrayOf(p.star, p.fog[0], p.fog[1], p.fog[2], p.gal[0], rc, rc)
        escape = List(160) {
            OrbEscape(r() * TAU, rr * (.04f + .5f * sqrt(r())), rr * (2.4f + 6f * r()), .5f + r() * 1.2f, .55f + r() * .6f, cols[(r() * cols.size).toInt().coerceAtMost(cols.size - 1)])
        }
        puffs = List(12) { i ->
            OrbPuff(r() * TAU, rr * (.1f + .4f * r()), if (i % 3 == 0) rc else p.fog[(r() * 5f).toInt().coerceAtMost(4)], rr * (.3f + .3f * r()))
        }
    }

    private fun drawShards(c: Canvas, s: Float) {
        val edge = mixc(rc, WHITE, .5f)
        pStroke.shader = null
        pStroke.strokeJoin = Paint.Join.ROUND
        for (sh in shards) {
            val u = max(0f, s - sh.delay)
            val dr = (1f - exp(-2.1f * u)) / 2.1f
            val z = sh.vz * dr
            val ps = min(3.2f, 1f / max(.3f, 1f - z / (ORB_R * 5f)))
            var a = if (u < sh.life * .55f) 1f else clamp01(1f - (u - sh.life * .55f) / (sh.life * .45f))
            if (ps > 1.8f) a *= clamp01(1f - (ps - 1.8f) / 1.2f)
            if (a <= 0f) continue
            val x = sh.x0 + sh.vx * dr
            val y = sh.y0 + sh.vy * dr + .5f * GRAV * u * u
            val px = bx + (x - bx) * ps
            val py = by + (y - by) * ps
            val ph = sh.vt * u
            val cs = cos(ph)
            val sx = (if (cs < 0f) -1f else 1f) * max(.06f, abs(cs))
            val cq = cos(sh.vq * u)
            val sy = (if (cq < 0f) -1f else 1f) * max(.1f, abs(cq))
            c.save()
            c.translate(px, py)
            c.rotate(sh.vr * u * RAD2DEG)
            c.scale(sx * ps, sy * ps)
            pShard.shader = sh.shader
            pShard.alpha = (a * 255f).toInt()
            c.drawPath(sh.path, pShard)
            val gl = abs(sin(ph * .5f + sh.gp)).pow(14)
            pFill.shader = null
            pFill.xfermode = OrbInk.ADD
            if (gl > .03f) { pFill.color = alphaOf(WHITE, gl * .6f * a); c.drawPath(sh.path, pFill) }
            pFill.color = alphaOf(rc, .14f * a * (1f - clamp01(u / .6f)))
            c.drawPath(sh.path, pFill)
            pFill.xfermode = null
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(edge, (.35f + gl * .6f) * a)
            pStroke.strokeWidth = .8f / ps
            c.drawPath(sh.path, pStroke)
            pStroke.xfermode = null
            c.restore()
        }
        pStroke.strokeJoin = Paint.Join.MITER
        val dr = (1f - exp(-2.6f * s)) / 2.6f
        for (d in grit) {
            val a = clamp01(1f - s / d.life)
            if (a <= 0f) continue
            val tw = .5f + .5f * sin(s * 30f + d.ph)
            dot(c, edge, d.x0 + d.vx * dr, d.y0 + d.vy * dr + .5f * GRAV * s * s, d.s, a * (.4f + .6f * tw))
        }
    }

    /** 속의 우주가 터져 나온다 — 바깥으로 뻗는 별줄기(워프) + 커지며 흩어지는 성운. */
    private fun drawEscape(c: Canvas, s: Float) {
        val cx = bx
        val cy = by
        for (pf in puffs) {
            val u = clamp01(s / 1.3f)
            val d = pf.d * (1f + 2.4f * outC(u))
            glow(c, pf.c, cx + cos(pf.a) * d, cy + sin(pf.a) * d, pf.s0 * (1f + 2.6f * outC(u)), .32f * (1f - u) * (1f - u), true)
        }
        pStroke.shader = null
        pStroke.xfermode = OrbInk.ADD
        pStroke.strokeCap = Paint.Cap.ROUND
        for (e in escape) {
            val u = s / e.life
            if (u >= 1f) continue
            val head = e.r0 + e.v * s * (1f - .3f * s)
            val tail = max(e.r0, head - e.v * .1f)
            val ca = cos(e.a)
            val sa = sin(e.a)
            pStroke.color = alphaOf(e.c, (1f - u).pow(1.4f))
            pStroke.strokeWidth = e.w * (1f - .5f * u)
            c.drawLine(cx + ca * tail, cy + sa * tail, cx + ca * head, cy + sa * head, pStroke)
        }
        pStroke.xfermode = null
        pStroke.strokeCap = Paint.Cap.BUTT
    }

    /** 영웅·전설 — 마법진에서 하늘로 치솟는 빛기둥. */
    private fun drawPillar(c: Canvas, s: Float) {
        if (lv < 2) return
        val u = clamp01(s / 1.3f)
        val a = (if (lv == 3) .75f else .5f) * (1f - u) * (1f - u)
        if (a <= 0f) return
        val w = ORB_R * (.36f * (1f - u) + .05f)
        pFill.shader = LinearGradient(
            ST_CX - w, 0f, ST_CX + w, 0f,
            intArrayOf(alphaOf(rc, 0f), alphaOf(rc, a * .22f), alphaOf(mixc(rc, WHITE, .8f), a), alphaOf(rc, a * .22f), alphaOf(rc, 0f)),
            floatArrayOf(0f, .3f, .5f, .7f, 1f), Shader.TileMode.CLAMP,
        )
        pFill.color = BLACK
        pFill.xfermode = OrbInk.ADD
        c.drawRect(ST_CX - w, viewTop, ST_CX + w, ST_GY, pFill)
        pFill.shader = null
        pFill.xfermode = null
    }

    /** 깨지는 순간 — 번쩍임(영웅·전설: 가운데가 하얗고 빨리 빠지는 큰 빛 두 겹) · 번짐 · 가로 섬광(희귀↑) · 빛줄기 0/8/14/22 · 충격파. */
    private fun rarityBurst(c: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val f = clamp01(s / SHAT)
        val e = outC(f)
        val a = 1f - f
        if (lv >= 2 && flashOk) {
            val q = clamp01(1f - s / .4f)
            val fa = (if (lv == 3) 1f else .6f) * q * q
            if (fa > 0f) {
                glow(c, mixc(rc, WHITE, .5f), cx, cy, r * (2.6f + 2f * (1f - q)), fa * .75f, true)
                glow(c, WHITE, cx, cy, r * (1.5f + 2f * (1f - q)), fa, true)
            }
        }
        glow(c, rc, cx, cy, r * (.7f + .7f * lv) * (.45f + .55f * e), (.55f + .12f * lv) * a, true)
        glow(c, WHITE, cx, cy, r * (.35f + .25f * lv) * (.3f + .7f * e), a.pow(1.5f) * .9f, true)
        if (lv >= 1) {
            c.save()
            c.translate(cx, cy)
            c.scale(1f, .05f + .02f * lv)
            glow(c, mixc(rc, WHITE, .3f), 0f, 0f, r * (1.4f + 1.1f * lv) * (.5f + .5f * e), a * (.3f + .15f * lv), true)
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
            rect.set(cx - rr, cy - rr * .92f, cx + rr, cy + rr * .92f)
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(rc, (1f - u) * .14f)
            pStroke.strokeWidth = 7f * (1f - u) + 1f
            c.drawOval(rect, pStroke)
            pStroke.color = alphaOf(ring, (1f - u) * .6f)
            pStroke.strokeWidth = (1.5f - kk * .3f) * (1f - u) + .4f
            c.drawOval(rect, pStroke)
            pStroke.xfermode = null
        }
    }

    /** 열린 뒤 남는 후광 — 번짐 · 도는 빛줄기(영웅 10 / 전설 16) · 전설은 고리. [k] 0..1. */
    private fun aura(c: Canvas, cx: Float, cy: Float, r: Float, k: Float) {
        if (k <= 0f) return
        glow(c, rc, cx, cy, r * (.7f + .35f * lv), (.25f + .1f * lv) * k * (.85f + .15f * sin(t * 2f)), true)
        if (lv >= 2) {
            val n = if (lv == 3) 16 else 10
            val amp = (if (lv == 3) .22f else .12f) * k
            for (i in 0 until n) {
                wedge(c, cx, cy, i.toFloat() / n * TAU + t * .15f, r * (.8f + .4f * lv) * (if (i % 2 == 1) .7f else 1f), .05f, alphaOf(rc, amp), alphaOf(rc, 0f))
            }
        }
        if (lv == 3) {
            pStroke.shader = null
            pStroke.xfermode = OrbInk.ADD
            pStroke.color = alphaOf(rc, .35f * k)
            pStroke.strokeWidth = 1f
            c.drawCircle(cx, cy, r * .62f, pStroke)
            pStroke.xfermode = null
        }
    }

    /** 결과 문장 — 등급 빛 별(왼쪽 위에서 빛을 받는 입체 그라데이션)이 통 튀어나와 둥실, 둘레를 반짝이 다섯이 돈다. */
    private fun emblem(c: Canvas, x: Float, y: Float, s: Float, col: Int, alpha: Float) {
        if (alpha <= 0f || s <= .5f) return
        glow(c, col, x, y, s * 3.4f, .6f * alpha, true)
        glow(c, WHITE, x, y, s * 1.2f, .3f * alpha, true)
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
        val ai = (clamp01(alpha * fade) * 255f).toInt()
        pFill.xfermode = null; pFill.shader = shader; pFill.color = BLACK; pFill.alpha = ai
        c.drawPath(path, pFill)
        pStroke.xfermode = null; pStroke.shader = shader; pStroke.color = BLACK; pStroke.alpha = ai
        pStroke.strokeWidth = s * .18f
        pStroke.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, pStroke)
        pFill.shader = null
        pStroke.shader = null
        pStroke.strokeJoin = Paint.Join.MITER
        for (i in 0 until 5) {
            val a = t * .6f + i * TAU / 5f
            val rr = s * (1.9f + .25f * sin(t * 1.3f + i))
            sparkle(
                c, x + cos(a) * rr, yy + sin(a) * rr * .8f, s * (.17f + .07f * sin(t * 3f + i * 2f)),
                alpha * (.55f + .45f * sin(t * 2.4f + i)), WHITE, true,
            )
        }
    }

    private companion object {
        val RAYS = intArrayOf(0, 8, 14, 22)
        /** 금 마디 밝기 셋(어둑 · 보통 · 번쩍). */
        val LUM = floatArrayOf(.3f, .62f, 1f)
        val GA_C = cos(.42f)
        val GA_S = sin(.42f)
        val GB_C = cos(.3f)
        val GB_S = sin(.3f)
    }
}

/** 시스템 "애니메이션 끄기"(개발자 옵션·접근성)를 켠 사람 — 움직임을 줄이고 번쩍임을 뺀다. */
internal fun reducedMotion(context: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** [from] = 이어받을 무대 수정구 — 같은 seed 로 만들어 지금 모습 그대로 시작한다(경로가 다르면 그쪽으로 섞어 간다). */
@Composable
internal fun rememberOrbEngine(interactive: Boolean, rare: Boolean, from: OrbEngine? = null): OrbEngine {
    val context = LocalContext.current
    return remember {
        OrbEngine(from?.seed ?: Random.nextInt(), interactive).apply {
            setRoute(rare, animate = false)
            if (reducedMotion(context)) { motion = .35f; flashOk = false }
            if (from != null) {
                follow(from)
                setRoute(rare, animate = true)
            }
        }
    }
}

/**
 * 프레임 시계 — 엔진을 한 프레임씩 굴리고, 값을 읽는 Canvas 가 다시 그려지게 한다(앱이 뒤로 가면 쉰다).
 * [running] = false 면 멈춘다(무대 수정구는 깨우기 창이 덮고 있는 동안 쉰다).
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
internal class SkyStars(seed: Int, n: Int = 110) {
    private val r = Random(seed)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val s = FloatArray(n) { .5f + r.nextFloat() * 1.1f }
    val p = FloatArray(n) { r.nextFloat() * TAU }
    val w = FloatArray(n) { .6f + r.nextFloat() * 1.6f }
    val size get() = x.size
}
