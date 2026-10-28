package com.chaminwoo.stary.feature.map.screen

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.toArgb
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

// ─────────────────────────────────────────────────────────────────────────────
// 별 둘레를 도는 혜성 — 별 곁 스파클(크기에 따라 1~3개) + 겹친 별 멤버(30m 머지 그룹의 숨은 별, 최대 4개).
//
// 2026-10-28: MapLibre 심볼 레이어 → **지도 위 화면 오버레이(매 프레임 Canvas)** 로 옮겼다.
//   icon-offset · icon-rotate · icon-size 는 layout 속성이라 바꿀 때마다 타일 배치를 비동기로 다시 해서
//   갱신이 들쭉날쭉 도착했다(혜성이 끊겨 움직임). 앞/뒤 레이어를 갈아탈 땐 옛 자리도 잠깐 보였다.
//   여기선 매 프레임 별의 화면 위치를 투영해 그 둘레에 직접 그린다 → 끊김 없이 돌고, 꼬리도 궤도를 따라 휜다.
// 3D 원 궤도: 궤도면이 화면 쪽으로 누워(납작함 flat) 앞쪽 반이 화면 아래로 온다. 뒤쪽 반은 별 몸 실루엣
//   바깥(clipOutPath)에만 그려 별 뒤로 숨고, 앞쪽 반은 별 위로 지나며 정면에서 반짝인다.
// 크기 · 반경은 별 마커 한 변(지도 기울기 원근 포함) 기준 — iOS `MapSparkle`/`StarMarkerView` 와 같은 비율 · 같은 시드.
// 파장 연출의 지도 스냅샷엔 이 오버레이가 안 찍히므로 DiaryMap 이 스냅샷 위에 같은 순간을 그려 넣는다(withComets).
// 2026-10-29: 줌 · 재클러스터에도 혜성을 지우지 않는다 — 멤버 고리는 줌과 무관한 30m 머지로 정하고([syncOrbitGroups]),
//   합쳐짐/펼쳐짐 전환 중엔 자기 별을 따라 날아가며 그 별의 투명도로 옅어진다. 사라지는 건 줌 게이트(최소 줌 쪽)뿐.
//   스파클 수 · 큰 별 머리처럼 크기 배율 문턱으로 갈리는 것은 툭 바뀌지 않게 [SPARKLE_SHIFT_SEC] 동안 따라간다.
// ─────────────────────────────────────────────────────────────────────────────

/** 혜성이 보이기 시작하는 줌 / 다 드러나는 줌 — 멀리서는 별 곁 반짝이가 잡동사니로 보인다(iOS `MapSparkle.minZoom/fullZoom`). */
internal const val COMET_MIN_ZOOM = 11.0
private const val COMET_FULL_ZOOM = 13.0

/** 별 곁 혜성 수 — 좀 큰 별은 2개, 더 큰 별은 3개(iOS `MapSparkle.particleCount`). */
internal fun sparkleCount(sizeMult: Float): Int = when {
    sizeMult >= 2.6f -> 3
    sizeMult >= 1.6f -> 2
    else -> 1
}

/** 이 sizeMult 이상이면 "큰 별" — 안쪽 두 혜성의 머리가 흰 반짝 대신 그 별 모양/색의 미니 크리스탈. 가장 바깥(set 2)은 항상 흰 반짝. */
internal const val SPARKLE_BIG_STAR_THRESHOLD = 1.75f
private const val SPARKLE_SMALL_SET = 2

/** 세트별 궤도 반경(별 마커 한 변 대비) — 별 몸(반지름 = 한 변의 0.39) 바로 바깥을 돈다. iOS `MapSparkle.install` radii 와 같은 값. */
private val SPARKLE_RADIUS = floatArrayOf(0.46f, 0.58f, 0.66f)
/** 머리 칸 크기(별 한 변 대비) — 예전 스프라이트(32px 칸 × iconSize)의 줌 15 비율 그대로. */
private val SPARKLE_HEAD = floatArrayOf(0.20f, 0.155f, 0.10f)
/** 꼬리가 궤도를 거슬러 뻗는 각(rad). */
private val SPARKLE_TAIL_SPAN = floatArrayOf(0.8f, 0.75f, 0.65f)

/**
 * 궤도면 기울기(rad) — 지도 시안 sparks 의 tilt: 세트 · 기울기 그룹(별마다 시드)마다 조금씩 비스듬히.
 * 궤도 납작함([SPARKLE_ORBIT_FLAT] = cos(궤도면이 누운 각))은 시안 값. iOS `MapSparkle.orbitTilt/orbitFlat` 와 같은 값.
 */
internal fun sparkleOrbitTilt(set: Int, group: Int): Float =
    (if (set == 1) -0.30f else 0.36f) + (group - 1.5f) * 0.12f
internal const val SPARKLE_ORBIT_FLAT = 0.42f

/** 세트별 공전 각속도(rad/초) — iOS `MapSparkle.orbitSpeed` 와 같은 값(별마다 ±10%). */
internal fun sparkleOrbitSpeed(set: Int) = when (set) {
    0 -> 1.1f
    1 -> 0.8f
    else -> 1.5f
}

/**
 * 가끔 한 번 번쩍 — 수정구 시안 `flashEnv`: 날카롭게 올라(0.18) 느리게(세제곱) 꺼진다. [u] = 0..1 주기 위상.
 * 반환 0..1. iOS `MapSparkle.flashEnv` 와 같은 식.
 */
internal fun sparkleFlash(u: Float): Float {
    if (u < 0.18f) {
        val s = sin(u / 0.18f * (PI.toFloat() / 2f))
        return s * s
    }
    val d = 1f - (u - 0.18f) / 0.82f
    return d * d * d
}

/** 세트별 번쩍임 주기(초) — 세트마다 달라 한꺼번에 깜박이지 않는다(정면 반짝과 별개로 가끔 한 번). */
internal fun sparkleFlashPeriod(set: Int) = when (set) {
    0 -> 2.6f
    1 -> 3.4f
    else -> 4.3f
}

/** 궤도 위 앞쪽 정도(0 = 뒤 … 1 = 앞) — 깊이 z 가 -0.3..0.3 을 지날 때 부드럽게 넘어간다. iOS `MapSparkle.frontness` 와 같은 식. */
internal fun sparkleFrontness(z: Float): Float {
    val x = ((z + 0.3f) / 0.6f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** 정면을 지날 때의 반짝임(0..1) — 깊이 z 가 0.5 → 1(정면 한가운데)로 갈수록 차오른다. iOS `Orbit3D.glint` 와 같은 식. */
internal fun orbitGlint(z: Float): Float {
    val g = ((z - 0.5f) / 0.5f).coerceIn(0f, 1f)
    return g * g * (3f - 2f * g)
}

/** 원근 배율 — 뒤(0.86) … 앞(1.14). iOS `Orbit3D.perspective`. */
internal fun orbitPerspective(z: Float) = 0.86f + 0.14f * (z + 1f)

/** 대표 둘레를 도는 멤버 별 최대 수(대표 제외). 그 이상은 대표 크기 보너스가 대신 말해준다. */
internal const val MAX_ORBIT_STARS = 4

/** 멤버 혜성 공전 — 각속도(rad/초, 약 15초에 한 바퀴), 멤버 사이 각(rad, 황금각에 가까워 몇 개든 고르게 벌어진다). iOS 와 같은 값. */
internal const val MEMBER_ORBIT_SPEED = 0.42f
internal const val MEMBER_ORBIT_SPACING = 2.4f
/** 멤버 궤도 납작함 — 멤버마다 조금씩 달라 한 장의 판이 아니라 겹친 고리로 보인다. */
internal fun memberOrbitFlat(satIndex: Int) = 0.36f + 0.04f * satIndex
/**
 * 멤버 궤도 반경(별 한 변 대비) — 대표 별 몸 바로 바깥을 감싼다. iOS `StarMarkerView.satelliteRadius`.
 * (2026-10-28 "너무 넓어서 겉돈다" → 0.75 + 0.05i 에서 조금 안쪽으로.)
 */
private fun memberOrbitRadius(satIndex: Int) = 0.64f + 0.04f * satIndex
/** 멤버 머리(별 몸) 크기 — 대표 한 변의 0.26(iOS 머리 1/3 × 몸 0.78). 너무 작거나 크지 않게 dp 로 묶는다(2026-10-28 0.30 → 살짝 작게). */
private const val MEMBER_BODY = 0.26f
private const val MEMBER_BODY_MIN_DP = 7f
private const val MEMBER_BODY_MAX_DP = 23f
private const val MEMBER_TAIL_SPAN = 0.7f
/** 멤버 고리가 새로 생기거나 바뀔 때 서서히 나타나는 시간(초). */
private const val MEMBER_FADE_IN_SEC = 0.65
/** 크기 배율이 문턱을 넘을 때 스파클 수 · 큰 별 머리가 바뀌는 데 걸리는 시간(초, 한 개당). */
private const val SPARKLE_SHIFT_SEC = 0.45f

/**
 * 겹친 별 하나의 멤버 고리 — [members] = 둘레를 도는 멤버(대표 제외, 최대 [MAX_ORBIT_STARS]),
 * [sparkles] = 고리 시드를 뽑기 전에 건너뛰는 스파클 수(30m 머지 크기로 정한다 — 줌 · 클러스터 크기 보너스와 무관해야
 * 궤도가 튀지 않고, iOS `StarMarkerView` 와 같은 수열이 된다), [since] = 이 고리가 나타나기 시작한 시각(혜성 시계, 초).
 */
internal data class OrbitGroup(val members: List<Diary>, val sparkles: Int, val since: Double)

/**
 * 30m 머지 결과 → 대표 id 별 멤버 고리. 바뀐 고리만 새로 만들어 [nowSec] 부터 서서히 나타나게 하고, 그대로인 고리는 손대지 않는다.
 * (2026-10-29 "줌인 · 줌아웃 뒤 혜성이 사라졌다 처음부터 다시 나온다" — 예전엔 카메라가 멈춰 재클러스터할 때마다 전부 지우고 다시 띄웠다.)
 */
internal fun syncOrbitGroups(orbits: HashMap<String, OrbitGroup>, merged: MergeResult, nowSec: Double) {
    val keep = HashSet<String>()
    for (rep in merged.reps) {
        val group = merged.groups[rep.id] ?: continue
        if (group.size < 2) continue
        keep.add(rep.id)
        val members = group.drop(1).take(MAX_ORBIT_STARS)
        val sparkles = sparkleCount(mergeSizeMult(group))
        val old = orbits[rep.id]
        if (old != null && old.sparkles == sparkles && old.members.map { it.id } == members.map { it.id }) {
            // 같은 고리 — 멤버 글(모양 · 색)만 바뀌었을 수 있어 목록만 갈아 끼우고 나타난 시각은 그대로 둔다.
            if (old.members != members) orbits[rep.id] = old.copy(members = members)
            continue
        }
        orbits[rep.id] = OrbitGroup(members, sparkles, nowSec)
    }
    orbits.keys.retainAll(keep)
}

/** MapLibre 카메라 → 화면 중심 거리 = 화면 높이 × 1.5 (0.5·H / tan(fov/2), 기본 fov = 2·atan(1/3)). */
private const val CAMERA_DIST_PER_H = 1.5f

private const val HEAD_WHITE = 0
private const val HEAD_STAR = 1
private const val HEAD_MEMBER = 2
/** 흰 반짝 그림 캔버스 = 머리 칸 × 이 배(번진 빛이 잘리지 않게). */
private const val RAYS_BOX = 1.4f
/** 미니 크리스탈 그림 — 캔버스 한 변(px)과 그 안의 별 몸 비율. */
private const val MINI_PX = 128
private const val MINI_BODY = 0.62f
/** 꼬리 점 최대 개수(머리 제외). */
private const val MAX_TAIL = 12

/**
 * 별 모션 시드 — 다이어리 id 의 FNV-1a(64bit) → SplitMix64.
 * iOS `StarMotionHash.fnv1a` / `StarMotionRandom` 과 같은 수열이라 같은 별은 두 앱에서 같은 방향 · 같은 기울기로 돈다.
 */
private class StarMotionRandom(id: String) {
    private var state: ULong = fnv1a64(id)

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    fun unit(): Double = (next() shr 11).toDouble() * (1.0 / 9_007_199_254_740_992.0)
    fun range(lo: Double, hi: Double): Double = lo + (hi - lo) * unit()
    fun flip(): Boolean = (next() and 1uL) == 0uL
}

private fun fnv1a64(s: String): ULong {
    var h = 0xcbf29ce484222325uL
    for (b in s.encodeToByteArray()) {
        h = h xor b.toUByte().toULong()
        h *= 0x100000001b3uL
    }
    return h
}

/** 별 하나의 스파클 개성 — iOS `StarMarkerView.init` → `MapSparkle.install` 과 같은 순서로 뽑는다. */
private class SparkleSeed(id: String) {
    val tiltGroup: Int
    val dir = FloatArray(3)
    val radius = FloatArray(3)
    val speed = FloatArray(3)
    val phase = DoubleArray(3)
    val flash = DoubleArray(3)

    init {
        val rng = StarMotionRandom(id)
        rng.next(); rng.next() // iOS 부유 주기 · 위상 몫(Android 별 부유는 위상 그룹이 맡는다)
        val firstClockwise = rng.flip()
        tiltGroup = (rng.next() % 4uL).toInt()
        for (s in 0 until 3) {
            radius[s] = rng.range(0.94, 1.08).toFloat()
            speed[s] = rng.range(0.9, 1.1).toFloat()
            phase[s] = rng.unit()
            flash[s] = rng.unit()
            // 가운데 궤도(set 1)만 반대로 돈다.
            dir[s] = if ((s == 1) != firstClockwise) 1f else -1f
        }
    }
}

/** 겹친 별 멤버 고리의 개성 — iOS 처럼 스파클을 [sparkles]개 뽑은 다음 자리에서 이어 뽑는다. */
private class MemberSeed(id: String, val sparkles: Int) {
    val base: Double
    val tilt: Float
    val dir: Float

    init {
        val rng = StarMotionRandom(id)
        repeat(4 + 4 * sparkles) { rng.next() } // 부유 2 + 스파클 머리 2 + 세트당 4
        base = rng.unit()
        tilt = 0.22f + ((rng.next() % 4uL).toInt() - 1.5f) * 0.10f
        dir = if (rng.flip()) 1f else -1f
    }
}

/**
 * 별 하나의 스파클 표시 상태 — [level] = 보이는 스파클 수(1..3, 연속값), [big] = 큰 별 머리(미니 크리스탈) 정도(0..1),
 * [seenAt] = 마지막으로 그린 시각. 재클러스터로 크기 배율이 바뀌어도 이 값이 천천히 따라가 혜성이 툭 생기거나 사라지지 않는다.
 */
private class SparkleState(var level: Float, var big: Float, var seenAt: Double)

/** 그릴 혜성 하나 — 매번 새로 만들지 않고 이 한 벌을 고쳐 쓴다. */
private class Comet {
    var cx = 0f
    var cy = 0f
    var radius = 0f
    var flat = 0f
    var cosT = 1f
    var sinT = 0f
    var ang = 0.0
    var dir = 1f
    var span = 0f
    /** 별 alpha × 줌 게이트(× 멤버 페이드). */
    var alpha = 0f
    /** 뒤쪽 불투명도 · 정면 반짝이 밝기에 주는 몫 · 정면에서 커지는 몫. */
    var backDim = 0f
    var glintAlpha = 0f
    var glintScale = 0f
    /** 가끔 번쩍(0..1)과 그 밝기 · 크기 배율. */
    var env = 0f
    var flashMul = 1f
    var flashGrow = 1f
    var head = HEAD_WHITE
    var headType = 0
    var headColor = 0
    /** 흰 반짝 · 별 머리 = 머리 칸, 멤버 = 별 몸 크기(px). */
    var headSize = 0f
    var spinDeg = 0f
    var haloRgb = 0
    var haloScale = 0f
    var haloAlpha = 0f
    var glowW = 0f
    var glowRgb = 0
    var glowAlpha = 0f
    var coreW = 0f
    var coreRgb = 0
    var coreAlpha = 0f
}

/**
 * 별 둘레 혜성 그리기 — DiaryMap 의 화면 오버레이(Compose Canvas)가 매 프레임 [draw] 한다.
 * 모든 호출은 메인 스레드(그리기 · 스냅샷 콜백)라 재사용 버퍼를 그대로 쓴다.
 */
internal class MapComets(private val density: Float) {

    /** 스프라이트 px → 화면 px. MapLibre 는 비트맵을 (비트맵 밀도/160)으로 나눠 dp 로 놓고 지도 밀도를 곱한다 — 보통 1. */
    private val spritePx: Float = run {
        val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val d = probe.density
        probe.recycle()
        if (d > 0) density * DisplayMetrics.DENSITY_DEFAULT / d else 1f
    }

    /** 부드러운 빛 점(알파 마스크) — 꼬리 빛 · 후광. ALPHA_8 이라 Paint 색으로 물든다. */
    private val glowDot: Bitmap = run {
        val px = 64
        val src = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = px / 2f
        Canvas(src).drawCircle(c, c, c, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                c, c, c,
                intArrayOf(0xFFFFFFFF.toInt(), 0x9CFFFFFF.toInt(), 0x30FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.3f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
        src.extractAlpha().also { src.recycle() }
    }

    /** 굽은 4갈래 반짝(수정구 시안 sparkleR — 중심을 지나는 2차 곡선 4개) + 평소에도 보이는 작은 알갱이. */
    private val rays: Bitmap = run {
        val px = 128
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val c = px / 2f
        val h = px / RAYS_BOX // 머리 칸
        val r = h * 0.46f
        val k = r * 0.10f // 제어점을 중심에서 살짝 비껴 갈래 뿌리를 가늘게 모은다
        val path = Path().apply {
            moveTo(c, c - r)
            quadTo(c + k, c - k, c + r, c)
            quadTo(c + k, c + k, c, c + r)
            quadTo(c - k, c + k, c - r, c)
            quadTo(c - k, c - k, c, c - r)
            close()
        }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            maskFilter = BlurMaskFilter(h * 0.1f, BlurMaskFilter.Blur.NORMAL)
        })
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
        cv.drawCircle(c, c, h * 0.075f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
        out
    }

    /** 모양×색 미니 크리스탈(색 글로우 + 패싯 채움) — 큰 별 곁 혜성 머리 · 겹친 별 멤버 머리. */
    private val miniStars = HashMap<Long, Bitmap>()

    private fun miniStar(type: Int, color: Int): Bitmap {
        if (miniStars.size > 96) miniStars.clear() // 그리기에 이미 넘긴 그림은 recycle 하지 않는다(참조만 놓는다)
        return miniStars.getOrPut((type.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)) {
            val out = Bitmap.createBitmap(MINI_PX, MINI_PX, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val body = MINI_PX * MINI_BODY
            val o = (MINI_PX - body) / 2f
            val path = StarStyle.starPath(type, body).apply { offset(o, o) }
            cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = StarStyle.colorOf(color).toArgb()
                maskFilter = BlurMaskFilter(MINI_PX * 0.1f, BlurMaskFilter.Blur.NORMAL)
            })
            StarStyle.drawCrystalFill(cv, type, color, o, o, body)
            out
        }
    }

    /** 별 몸 실루엣(모양별 100px 기준 경로) — 뒤쪽 반을 별 뒤로 숨기는 clipOut 에 쓴다. */
    private val bodyPaths = HashMap<Int, Path>()
    private val clip = Path()
    private val matrix = Matrix()

    private fun bodyClip(type: Int, size: Float, cx: Float, cy: Float): Path {
        val base = bodyPaths.getOrPut(type) { StarStyle.starPath(type, 100f) }
        matrix.setScale(size / 100f, size / 100f)
        matrix.postTranslate(cx - size / 2f, cy - size / 2f)
        base.transform(matrix, clip)
        return clip
    }

    private val sparkleSeeds = HashMap<String, SparkleSeed>()
    private val memberSeeds = HashMap<String, MemberSeed>()
    private val sparkleStates = HashMap<String, SparkleState>()
    private var lastT = Double.NaN

    private val comet = Comet()
    private val xs = FloatArray(MAX_TAIL + 1)
    private val ys = FloatArray(MAX_TAIL + 1)
    private val zs = FloatArray(MAX_TAIL + 1)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    /**
     * 한 프레임 — [stars] = 지금 지도 source 에 든 별(위치 · 투명도 · 크기 보간 중인 값 그대로),
     * [members] = 30m 머지 대표 id → 멤버 고리([syncOrbitGroups]), [memberFade] = 필터 전환 동안 멤버를 숨기는 전체 페이드,
     * [tSec] = 마커 루프와 같은 시계(초).
     */
    fun draw(
        cv: Canvas,
        map: MapLibreMap,
        viewW: Float,
        viewH: Float,
        tSec: Double,
        stars: List<ShownStar>,
        members: Map<String, OrbitGroup>,
        memberFade: Float,
    ) {
        if (stars.isEmpty() || viewH <= 0f) return
        val cam = map.cameraPosition
        val gate = ((cam.zoom - COMET_MIN_ZOOM) / (COMET_FULL_ZOOM - COMET_MIN_ZOOM)).toFloat().coerceIn(0f, 1f)
        if (gate < 0.004f) return
        val zoomMult = starZoomMult(cam.zoom)
        val zoomAmp = ((cam.zoom.toFloat() - 6f) / 9f).coerceIn(0.1f, 1f)
        val tanPitch = tan(Math.toRadians(cam.tilt)).toFloat()
        val headMul = 0.6f + 0.4f * gate // 줌을 빼면 머리도 같이 작아지며 사라진다
        val proj = map.projection
        if (sparkleSeeds.size > 4096) sparkleSeeds.clear()
        if (memberSeeds.size > 4096) memberSeeds.clear()
        if (sparkleStates.size > 4096) sparkleStates.clear()
        // 스파클 수 · 머리 모양이 이번 프레임에 따라가는 몫(멈췄다 다시 그릴 땐 한 번에 따라잡지 않게 묶는다).
        val shift = if (lastT.isNaN()) 1f else ((tSec - lastT).toFloat() / SPARKLE_SHIFT_SEC).coerceIn(0f, 1f)
        lastT = tSec

        for (s in stars) {
            if (s.alpha < 0.01f) continue
            val id = s.d.id
            val p = proj.toScreenLocation(MlLatLng(s.lat, s.lng))
            val phase = (abs(id.hashCode()) % PHASE_GROUPS) * (2.0 * PI / PHASE_GROUPS)
            // 별 부유(마커 루프의 iconTranslate)와 같은 식 · 같은 시계 — 혜성이 별과 한 덩어리로 둥실 뜬다.
            val cx = p.x
            val cy = p.y + (sin(tSec * 1.6 + phase) * 4.0 * zoomAmp).toFloat() * density
            // 기울인 지도의 원근 — MapLibre 심볼 셰이더의 perspective_ratio 와 같은 식(먼 위쪽 별은 작게, 가까운 아래쪽은 크게).
            val persp = (1f + tanPitch * (p.y - viewH / 2f) / (2f * CAMERA_DIST_PER_H * viewH)).coerceIn(0f, 4f)
            // 별 마커 한 변(px) — starSizeExpression 과 같은 값. 맥동은 빼서 궤도가 숨 쉬지 않게 한다.
            val side = MARKER_SIDE_PX * (if (s.near) STAR_SIZE_NEAR else STAR_SIZE_FAR) *
                zoomMult * s.sizeMult * persp * spritePx
            if (side < 6f) continue
            val reach = side * 1.25f
            if (cx < -reach || cx > viewW + reach || cy < -reach || cy > viewH + reach) continue

            val type = StarStyle.safeType(s.d.starType)
            val color = StarStyle.safeColor(s.d.starColor)
            val seed = sparkleSeeds.getOrPut(id) { SparkleSeed(id) }
            // 스파클 수 · 큰 별 머리 — 클러스터 크기 보너스로 sizeMult 가 문턱을 넘나들면 천천히 따라간다.
            // 처음 보는 별 · 한동안 안 그린 별은 바로 맞춘다(별 자체가 페이드로 나타난다).
            val count = sparkleCount(s.sizeMult).toFloat()
            val bigTarget = if (s.sizeMult >= SPARKLE_BIG_STAR_THRESHOLD) 1f else 0f
            val state = sparkleStates[id]?.takeIf { tSec - it.seenAt <= 1.0 }?.apply {
                level = approach(level, count, shift)
                big = approach(big, bigTarget, shift)
                seenAt = tSec
            } ?: SparkleState(count, bigTarget, tSec).also { sparkleStates[id] = it }
            val alpha = s.alpha * gate
            // 멤버 고리 — 고리가 새로 생기거나 바뀐 때부터만 서서히(그대로인 고리는 줌 · 재클러스터와 상관없이 계속 1).
            val orbit = members[id]
            val memberAlpha = if (orbit == null) 0f
            else alpha * memberFade * smooth01(((tSec - orbit.since) / MEMBER_FADE_IN_SEC).toFloat())
            val mSeed = if (orbit == null || memberAlpha < 0.004f) null
            else memberSeeds[id]?.takeIf { it.sparkles == orbit.sparkles }
                ?: MemberSeed(id, orbit.sparkles).also { memberSeeds[id] = it }
            // 가까운 별은 맥동한다(루프의 pulse) — 숨기는 실루엣만 같이 키운다.
            val pulse = if (s.near) 1f + 0.2f * ((sin(tSec * 3.2 + phase).toFloat() + 1f) / 2f) else 1f

            // 뒤쪽 반 — 별 몸 실루엣 바깥에만 그려 별 뒤로 숨는다. 큰 궤도(멤버)가 더 뒤라 먼저.
            cv.save()
            cv.clipOutPath(bodyClip(type, side * pulse * 0.78f, cx, cy))
            if (mSeed != null) drawMembers(cv, true, cx, cy, side, tSec, mSeed, orbit!!.members, memberAlpha, headMul)
            drawSparkles(cv, true, cx, cy, side, tSec, seed, state, s.sizeMult, type, color, alpha, headMul)
            cv.restore()
            // 앞쪽 반 — 별 위로 지나며 정면에서 반짝인다. 큰 궤도(멤버)가 더 앞이라 나중에.
            drawSparkles(cv, false, cx, cy, side, tSec, seed, state, s.sizeMult, type, color, alpha, headMul)
            if (mSeed != null) drawMembers(cv, false, cx, cy, side, tSec, mSeed, orbit!!.members, memberAlpha, headMul)
        }
    }

    private fun drawSparkles(
        cv: Canvas, back: Boolean, cx: Float, cy: Float, side: Float, tSec: Double,
        seed: SparkleSeed, state: SparkleState, sizeMult: Float, type: Int, color: Int, alpha: Float, headMul: Float,
    ) {
        val starRgb = StarStyle.colorOf(color).toArgb()
        // 머리는 별보다 덜 커진다(예전 iconSize 의 sizeMult^0.8).
        val headShrink = sizeMult.coerceAtLeast(1f).pow(-0.2f)
        val c = comet
        for (set in 0 until ceil(state.level).toInt()) {
            // 늘거나 줄어드는 중인 혜성은 level 의 소수부만큼만 보인다.
            val shown = if (set == 0) 1f else (state.level - set).coerceIn(0f, 1f)
            if (shown <= 0f) continue
            c.cx = cx
            c.cy = cy
            c.radius = side * SPARKLE_RADIUS[set] * seed.radius[set]
            c.flat = SPARKLE_ORBIT_FLAT
            val tilt = sparkleOrbitTilt(set, seed.tiltGroup)
            c.cosT = cos(tilt)
            c.sinT = sin(tilt)
            c.dir = seed.dir[set]
            val period = 2.0 * PI / (sparkleOrbitSpeed(set) * seed.speed[set])
            c.ang = c.dir * 2.0 * PI * frac(tSec / period + seed.phase[set])
            c.span = SPARKLE_TAIL_SPAN[set]
            val env = sparkleFlash(frac(tSec / sparkleFlashPeriod(set) + seed.flash[set]).toFloat())
            c.env = env
            c.flashMul = 0.6f + 0.4f * env
            c.flashGrow = 0.92f + 0.12f * env
            c.backDim = 0.55f
            c.glintAlpha = 0.55f
            c.glintScale = 0.3f
            val h = side * SPARKLE_HEAD[set] * headShrink * headMul
            c.headSize = h
            c.haloScale = 0.5f
            c.haloAlpha = 0.16f
            // 갈래는 평소엔 제자리, 번쩍일 때 살짝 비틀린다(시안 sparkleR 회전 = env·1.2 + 위상).
            c.spinDeg = Math.toDegrees(env * 1.2 + seed.phase[set] * 2.0 * PI).toFloat()
            // 큰 별이면 안쪽 두 혜성 머리가 미니 크리스탈 — 크기가 문턱을 넘나드는 동안엔 흰 반짝과 서로 스며들며 바뀐다.
            val star = if (set == SPARKLE_SMALL_SET) 0f else state.big
            if (star > 0f) {
                c.head = HEAD_STAR
                c.headType = type
                c.headColor = color
                c.haloRgb = starRgb
                c.glowW = h * 0.25f; c.glowRgb = starRgb; c.glowAlpha = 0.55f
                c.coreW = h * 0.075f; c.coreRgb = 0xFFF6EA; c.coreAlpha = 0.6f
                c.alpha = alpha * shown * star
                drawComet(cv, back, c)
            }
            if (star < 1f) {
                c.head = HEAD_WHITE
                c.haloRgb = mixWhite(starRgb, 0.65f) // 시안: 별빛이 살짝 도는 흰 빛
                c.glowW = h * 0.22f; c.glowRgb = 0xFFE9C8; c.glowAlpha = 0.45f
                c.coreW = h * 0.075f; c.coreRgb = 0xFFFFFF; c.coreAlpha = 0.85f
                c.alpha = alpha * shown * (1f - star)
                drawComet(cv, back, c)
            }
        }
    }

    private fun drawMembers(
        cv: Canvas, back: Boolean, cx: Float, cy: Float, side: Float, tSec: Double,
        seed: MemberSeed, list: List<Diary>, alpha: Float, headMul: Float,
    ) {
        val period = 2.0 * PI / MEMBER_ORBIT_SPEED
        val body = (side * MEMBER_BODY).coerceIn(MEMBER_BODY_MIN_DP * density, MEMBER_BODY_MAX_DP * density) * headMul
        val c = comet
        for (i in 0 until minOf(list.size, MAX_ORBIT_STARS)) {
            val m = list[i]
            val color = StarStyle.safeColor(m.starColor)
            val rgb = StarStyle.colorOf(color).toArgb()
            c.cx = cx
            c.cy = cy
            c.radius = side * memberOrbitRadius(i)
            c.flat = memberOrbitFlat(i)
            c.cosT = cos(seed.tilt)
            c.sinT = sin(seed.tilt)
            c.dir = seed.dir
            // 멤버끼리는 같은 속도 — 고르게 벌어진 채 한 고리처럼 돈다.
            c.ang = c.dir * 2.0 * PI * frac(tSec / period + seed.base + i * MEMBER_ORBIT_SPACING / (2.0 * PI))
            c.span = MEMBER_TAIL_SPAN
            c.env = 0f
            c.flashMul = 1f
            c.flashGrow = 1f
            c.alpha = alpha
            c.backDim = 0.7f
            c.glintAlpha = 0.28f
            c.glintScale = 0.18f
            c.head = HEAD_MEMBER
            c.headType = StarStyle.safeType(m.starType)
            c.headColor = color
            c.headSize = body
            c.haloRgb = rgb
            c.haloScale = 0.95f
            c.haloAlpha = 0.3f
            c.glowW = body * 0.44f; c.glowRgb = rgb; c.glowAlpha = 0.5f
            c.coreW = body * 0.11f; c.coreRgb = 0xFFF4EA; c.coreAlpha = 0.65f
            c.spinDeg = 0f
            drawComet(cv, back, c)
        }
    }

    /**
     * 혜성 하나의 [back](뒤쪽) 또는 앞쪽 판 — 머리 · 꼬리 점마다 자기 깊이로 판이 갈린다.
     * 꼬리는 머리에서 궤도를 거슬러 [Comet.span] 만큼: 부드러운 빛 점(도장) + 가는 밝은 심, 끝으로 갈수록 가늘고 옅게.
     */
    private fun drawComet(cv: Canvas, back: Boolean, c: Comet) {
        val zHead = sin(c.ang).toFloat()
        // 반 바퀴보다 짧은 꼬리라 머리 · 꼬리 끝이 둘 다 다른 판이면 이 판엔 그릴 게 없다.
        val zTip = sin(c.ang - c.dir * c.span).toFloat()
        if (back && zHead >= 0f && zTip >= 0f) return
        if (!back && zHead < 0f && zTip < 0f) return

        val gl = orbitGlint(zHead)
        val bright = ((1f - c.glintAlpha) + c.glintAlpha * gl) * c.flashMul // 정면 반짝 × 가끔 번쩍
        val arc = c.radius * c.span
        val n = (arc / max(1.6f, c.glowW * 0.55f)).toInt().coerceIn(3, MAX_TAIL)
        for (k in 0..n) {
            val a = c.ang - c.dir * c.span * k / n
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val oy = sa * c.flat
            xs[k] = c.cx + (ca * c.cosT - oy * c.sinT) * c.radius
            ys[k] = c.cy + (ca * c.sinT + oy * c.cosT) * c.radius
            zs[k] = sa
        }
        val spacing = arc / n

        // 꼬리 빛 — 겹치는 도장이라 간격/반경만큼 덜어 낸다(겹쳐 쌓인 밝기 ≈ 목표 밝기).
        for (k in n downTo 0) {
            val z = zs[k]
            if ((z < 0f) != back) continue
            val q = k.toFloat() / n
            val rad = (c.glowW * (1f - q).pow(0.6f) + 0.5f) * orbitPerspective(z)
            val a = c.glowAlpha * (1f - q) * (1f - q) * depth(c, z) * bright * c.alpha *
                (spacing / rad).coerceIn(0.2f, 1f)
            stamp(cv, xs[k], ys[k], rad, a, c.glowRgb)
        }
        // 꼬리 심 — 1px 보다 가늘어지면 굵기 대신 밝기를 줄인다.
        for (k in 0 until n) {
            val z = (zs[k] + zs[k + 1]) * 0.5f
            if ((z < 0f) != back) continue
            val q = (k + 0.5f) / n
            var w = c.coreW * (1f - q).pow(0.6f) * orbitPerspective(z)
            var a = c.coreAlpha * (1f - q).pow(1.6f) * depth(c, z) * bright * c.alpha
            if (w < 1f) {
                a *= w
                w = 1f
            }
            if (a < 0.004f) continue
            line.strokeWidth = w
            line.color = argb(a, c.coreRgb)
            cv.drawLine(xs[k], ys[k], xs[k + 1], ys[k + 1], line)
        }

        // 머리 — 후광 → 머리 그림 → (별 머리) 정면 반짝.
        if ((zHead < 0f) != back) return
        val pz = orbitPerspective(zHead)
        val dep = depth(c, zHead)
        val scale = pz * (1f + c.glintScale * gl) * c.flashGrow
        val hx = xs[0]
        val hy = ys[0]
        val lift = max(gl, c.env)
        stamp(cv, hx, hy, c.headSize * (c.haloScale + 0.45f * lift) * pz, (c.haloAlpha + 0.3f * lift) * dep * c.alpha, c.haloRgb)
        val ha = dep * bright * c.alpha
        if (ha < 0.004f) return
        when (c.head) {
            HEAD_WHITE -> sprite(cv, rays, hx, hy, c.headSize * RAYS_BOX * scale, ha, c.spinDeg)
            else -> {
                // 별 몸 크기 — 큰 별 곁 혜성은 예전 그림처럼 머리 칸의 0.66, 멤버는 headSize 그대로.
                val body = if (c.head == HEAD_STAR) c.headSize * 0.66f else c.headSize
                sprite(cv, miniStar(c.headType, c.headColor), hx, hy, body / MINI_BODY * scale, ha, 0f)
                // 정면을 지날 때 머리 위로 작은 흰 반짝 — "앞에서 돌며 반짝".
                if (gl > 0.03f) {
                    sprite(cv, rays, hx, hy, body * 1.15f * (0.55f + 0.45f * gl) * pz, 0.9f * gl * dep * c.alpha, c.spinDeg)
                }
            }
        }
    }

    private fun depth(c: Comet, z: Float) = c.backDim + (1f - c.backDim) * sparkleFrontness(z)

    private fun stamp(cv: Canvas, x: Float, y: Float, r: Float, a: Float, rgb: Int) {
        if (a < 0.004f || r < 0.5f) return
        paint.color = argb(a, rgb)
        rect.set(x - r, y - r, x + r, y + r)
        cv.drawBitmap(glowDot, null, rect, paint)
    }

    private fun sprite(cv: Canvas, bmp: Bitmap, x: Float, y: Float, sizePx: Float, a: Float, rotDeg: Float) {
        if (a < 0.004f || sizePx < 1f) return
        val half = sizePx / 2f
        paint.color = argb(a, 0xFFFFFF)
        rect.set(x - half, y - half, x + half, y + half)
        if (rotDeg != 0f) {
            cv.save()
            cv.rotate(rotDeg, x, y)
            cv.drawBitmap(bmp, null, rect, paint)
            cv.restore()
        } else cv.drawBitmap(bmp, null, rect, paint)
    }
}

private fun frac(x: Double): Double = x - floor(x)

/** [v] 를 [target] 쪽으로 [d] 만큼(넘지 않게). */
private fun approach(v: Float, target: Float, d: Float): Float =
    if (v < target) minOf(v + d, target) else maxOf(v - d, target)

/** 0..1 을 양 끝이 부드러운 곡선으로. */
private fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun argb(a: Float, rgb: Int): Int =
    ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (rgb and 0xFFFFFF)

/** 색을 흰색 쪽으로 [k] 만큼 섞는다. */
private fun mixWhite(rgb: Int, k: Float): Int {
    fun ch(shift: Int): Int {
        val v = (rgb shr shift) and 0xFF
        return (v + (255 - v) * k).toInt().coerceIn(0, 255) shl shift
    }
    return ch(16) or ch(8) or ch(0)
}
