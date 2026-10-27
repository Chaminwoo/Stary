package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 별 탄생 연출 — 다이어리 저장이 **성공한 순간** 별빛이 모이고(①) 혜성처럼 날아(②) 지도의 내 위치(화면 중앙)에 내려앉는다(③).
 * 2026-10-28 "하나의 우주" 개편: 예전 응축→발광→하강(0.95초)을 수정구 시안 기법으로 다시 짰다.
 *
 * 전역 오버레이인 이유: 저장 직후 업로드 화면은 곧바로 pop 되므로, 화면 안에서 재생하면 연출이 잘린다.
 * [StarBirthState.trigger] 만 호출하면 [StarBirthHost](MainScreen 최상단)가 **지도 위에서** 이어 재생한다.
 * 실패(업로드 에러)에는 트리거하지 않는다 — 성공 경로에서만 호출할 것.
 *
 * 성능 약속(지도 위에서 도는 연출): 모든 빛은 미리 만든 **스프라이트 한 장**을 더하기 합성으로 찍는 것(프레임마다 그라데이션 생성 없음),
 * 입자는 상태 배열 없이 **시간의 함수**(태어난 시각 + 나이 → 위치·밝기), 한 프레임 스프라이트 ≤ 약 150 · 선 ≤ 약 30 · 반짝 ≤ 약 30.
 * 수치(시간표·경로·크기)는 iOS `StarBirthOverlay` 와 같다 — 한쪽만 고치지 말 것.
 */
object StarBirthState {
    /** 재생 중인 별(모양, 색). null = 연출 없음. */
    var pending by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun trigger(starType: Int, starColor: Int) {
        pending = starType to starColor
    }

    fun clear() {
        pending = null
    }
}

// ── 시간표(초). 기준 화면은 폭 390 · 높이 844 "디자인 px"(u = 실제 폭 / 390). ──
private const val CHARGE_S = 1.2f   // ① 별빛 모으기
private const val FLY_S = 1.4f      // ② 혜성 비행
private const val LAND_S = 1.4f     // ③ 내려앉기(마지막에 지도의 별로 이어 넘김)
private const val TOTAL_S = CHARGE_S + FLY_S + LAND_S
private const val GATHER_PARTICLES = 46
private const val DUST_PARTICLES = 30
private const val TRAIL_STEPS = 30
private const val GROUND_K = 0.7f   // 땅 위 원이 화면에서 납작해지는 비율(기울기 40° → cos 40°)

/** 출발(별빛이 모이는 자리) / 착지(지도의 내 위치 = 화면 중앙)의 화면 세로 비율. */
private const val START_Y_FRAC = 0.30f
private const val LAND_Y_FRAC = 0.50f

private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
private fun smooth(a: Float, b: Float, x: Float): Float { val t = clamp01((x - a) / (b - a)); return t * t * (3f - 2f * t) }
/** 결정론적 의사 난수 0..1 — 시안 `rnd(i, k)` 와 같은 식. */
private fun rnd(i: Int, k: Int = 0): Float {
    val v = sin(i * 127.1 + k * 311.7) * 43758.5453
    return (v - floor(v)).toFloat()
}

/** 빛 스프라이트(부드럽게 꺼지는 원) — 색마다 한 번만 만든다. */
private fun glowSprite(color: Color): ImageBitmap {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val argb = color.toArgb()
    fun a(alpha: Int) = (argb and 0x00FFFFFF) or (alpha shl 24)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            size / 2f, size / 2f, size / 2f,
            intArrayOf(a(255), a(128), a(34), a(0)),
            floatArrayOf(0f, 0.22f, 0.58f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    android.graphics.Canvas(bmp).drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bmp.asImageBitmap()
}

/** 반지름 1 의 굽은 4갈래 반짝(중심을 지나는 2차 곡선 4개) — 수정구 시안 `sparkleR` 와 같은 모양. */
private val UNIT_GLINT: Path = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0f, 0f, 1f, 0f)
    quadraticTo(0f, 0f, 0f, 1f)
    quadraticTo(0f, 0f, -1f, 0f)
    quadraticTo(0f, 0f, 0f, -1f)
    close()
}

private class BirthPalette(c: Color) {
    val base = c
    val mid = lerp(c, Color.White, 0.5f)
    val hot = lerp(c, Color.White, 0.7f)
    val white = Color.White
    val sBase = glowSprite(c)
    val sMid = glowSprite(mid)
    val sHot = glowSprite(hot)
    val sWhite = glowSprite(Color.White)
}

private fun DrawScope.glow(img: ImageBitmap, x: Float, y: Float, r: Float, a: Float) {
    if (a <= 0.003f || r <= 0.4f) return
    drawImage(
        img,
        dstOffset = IntOffset((x - r).roundToInt(), (y - r).roundToInt()),
        dstSize = IntSize((r * 2f).roundToInt().coerceAtLeast(1), (r * 2f).roundToInt().coerceAtLeast(1)),
        alpha = a.coerceAtMost(1f),
        blendMode = BlendMode.Plus,
    )
}

/** 납작한 타원 빛(바닥 번짐). [rx] 가로 반지름, [ry] 세로 반지름. */
private fun DrawScope.glowE(img: ImageBitmap, x: Float, y: Float, rx: Float, ry: Float, a: Float) {
    if (a <= 0.003f || rx <= 0.4f || ry <= 0.2f) return
    drawImage(
        img,
        dstOffset = IntOffset((x - rx).roundToInt(), (y - ry).roundToInt()),
        dstSize = IntSize((rx * 2f).roundToInt().coerceAtLeast(1), (ry * 2f).roundToInt().coerceAtLeast(1)),
        alpha = a.coerceAtMost(1f),
        blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.glint(x: Float, y: Float, r: Float, rotRad: Float, a: Float, color: Color = Color.White) {
    if (a <= 0.003f || r <= 0.3f) return
    withTransform({
        translate(x, y)
        rotate(Math.toDegrees(rotRad.toDouble()).toFloat(), Offset.Zero)
        scale(r, r, Offset.Zero)
    }) {
        drawPath(UNIT_GLINT, color.copy(alpha = a.coerceAtMost(1f)), blendMode = BlendMode.Plus)
    }
}

/**
 * 별 탄생 오버레이 호스트 — 앱 최상단(MainScreen)에 1개만 둔다.
 * 장식 전용: 터치를 받지 않으므로 연출 중에도 지도 조작이 막히지 않는다.
 */
@Composable
fun StarBirthHost() {
    val star = StarBirthState.pending ?: return
    val (type, colorIndex) = star

    val progress = remember(star) { Animatable(0f) }
    LaunchedEffect(star) {
        progress.snapTo(0f)
        com.chaminwoo.stary.core.util.Haptics.celebrate()
        // 유성이 내려와 안착 — 화음이 안착 순간에 오도록 음원 자체를 맞춰 두었다(플레이어가 지연을 갖는다).
        com.chaminwoo.stary.core.util.MusicManager.playStarBirth()
        progress.animateTo(1f, animationSpec = tween((TOTAL_S * 1000).toInt(), easing = LinearEasing))
        StarBirthState.clear()
    }

    val accent = StarStyle.colorOf(colorIndex)
    val pal = remember(colorIndex) { BirthPalette(accent) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 상태 읽기는 그리기 안에서만 — 프레임마다 리컴포즈하지 않는다.
            val T = progress.value * TOTAL_S
            drawBirth(T, type, colorIndex, pal)
        }
    }
}

private fun DrawScope.drawBirth(T: Float, type: Int, colorIndex: Int, pal: BirthPalette) {
    val w = size.width
    val h = size.height
    val u = w / 390f
    val sx = w * 0.5f
    val sy = h * START_Y_FRAC
    val lx = w * 0.5f
    val ly = h * LAND_Y_FRAC

    fun pathAt(f: Float): Offset {
        val e = easeInOut(f)
        val o = easeOut(f)
        return Offset(
            lerpF(sx, lx, e) + sin(f * PI.toFloat() * 2f) * 20f * u * (1f - f),
            lerpF(sy, ly, o) - sin(f * PI.toFloat()) * 74f * u,
        )
    }
    fun sizeAt(f: Float) = lerpF(128f, 46f, easeInOut(f)) * u

    val tFly = CHARGE_S
    val tLand = CHARGE_S + FLY_S
    val fly = clamp01((T - tFly) / FLY_S)
    val lt = T - tLand // 착지 후 경과(초)

    var px = sx
    var py = sy
    var starSize = 128f * u
    var flash = 0f
    var bodyAlpha = 1f

    if (T < tFly) {
        val cp = clamp01(T / CHARGE_S)
        drawGather(cp, T, sx, sy, u, pal)
        starSize *= 1f + 0.05f * sin(cp * PI.toFloat()) + 0.05f * cp * cp
        flash = 0.25f * cp * cp
        glow(pal.sBase, sx, sy, (120f + cp * 80f) * u, 0.18f + cp * 0.4f)
        if (cp > 0.86f) { // 떠나기 직전 팍
            val k = (cp - 0.86f) / 0.14f
            glow(pal.sWhite, sx, sy, (90f + 150f * k) * u, 0.55f * k)
        }
        bodyAlpha = smooth(0f, 0.25f, T) // 별이 서서히 떠오른다
    } else {
        val p = pathAt(fly)
        px = p.x; py = p.y; starSize = sizeAt(fly)
        if (T < tFly + 0.18f) { // 떠나는 순간의 번쩍 한 번
            val k = 1f - (T - tFly) / 0.18f
            glow(pal.sWhite, sx, sy, 230f * u, 0.5f * k * k)
        }
    }
    if (fly > 0f && fly < 1f) drawComet(fly, T - tFly, u, ::pathAt, ::sizeAt, pal)
    if (fly >= 1f) { // 내려앉는 별: 한 번 눌렸다 살아난다(감쇠하는 출렁임)
        val q = clamp01(lt / 1.1f)
        starSize *= 1f + 0.26f * exp(-q * 4.2f) * cos(q * 10.5f)
        py += 3f * u * exp(-q * 5f) * cos(q * 12f)
        flash += 0.55f * exp(-lt * 5f)
        drawLanding(lt, lx, ly, u, pal)
        // 끝 0.4초: 지도에 이미 올라온 진짜 별로 자연스럽게 넘긴다.
        bodyAlpha = 1f - smooth(LAND_S - 0.4f, LAND_S, lt)
    }

    // 별 본체 — 앱의 모든 별과 같은 크리스탈 렌더 + 번쩍임(흰 빛)
    if (bodyAlpha > 0.01f && starSize > 1f) {
        drawIntoCanvas { canvas ->
            StarStyle.drawCrystalFill(
                canvas.nativeCanvas, type, colorIndex,
                px - starSize / 2f, py - starSize / 2f, starSize,
                alpha = (255 * bodyAlpha).toInt().coerceIn(0, 255),
            )
        }
        if (flash > 0.01f) glow(pal.sHot, px, py, starSize * 0.8f, (flash * bodyAlpha).coerceAtMost(0.8f))
    }
    if (fly >= 1f) drawArrivalGlints(lt, lx, ly, starSize, T, u, pal)
}

// ── ① 별빛 모으기 — 로그 나선 팔을 따라 알갱이가 별로 빨려들고, 얇은 빛 고리가 오므라든다. ──
private fun DrawScope.drawGather(cp: Float, T: Float, sx: Float, sy: Float, u: Float, pal: BirthPalette) {
    val ec = easeInOut(clamp01(cp * 1.02f))
    for (i in 0 until GATHER_PARTICLES) {
        val delay = rnd(i) * 0.36f
        val q = clamp01((cp - delay) / (1f - delay))
        if (q <= 0f || q >= 1f) continue
        val rad = lerpF(190f + 60f * rnd(i, 2), 8f, q * q) * u
        val ang = i * 2.399963f + ln(rad / (8f * u) + 1f) * 1.1f + cp * 2.2f
        val x = sx + cos(ang) * rad
        val y = sy + sin(ang) * rad * 0.78f
        val a = sin(q * PI.toFloat()) * 0.9f
        val img = if (i % 5 == 0) pal.sWhite else if (i % 3 == 0) pal.sMid else pal.sBase
        glow(img, x, y, (6f + 5f * rnd(i, 6)) * u, 0.5f * a)
        if (i % 4 == 0) glint(x, y, (2f + 3.4f * rnd(i, 8)) * u, ang * 2f + T * 2f, a)
    }
    // 오므라드는 빛 고리(기울어진 타원, 두 겹 — 끝으로 갈수록 가늘고 밝다). 꼬리가 사라지는 호를 짧은 호 조각으로 그린다.
    val r = lerpF(190f, 82f, ec) * u
    val ry = r * 0.4f
    val tilt = Math.toDegrees((-0.28f + cp * 0.5f).toDouble()).toFloat()
    val segs = 12
    val a0 = cp * 5f
    withTransform({ translate(sx, sy); rotate(tilt, Offset.Zero) }) {
        for (layer in 0 until 2) {
            val sw = (if (layer == 1) 1.6f else 7f) * u
            val baseA = (if (layer == 1) 1f else 0.28f) * sin(minOf(1f, cp * 1.2f) * PI.toFloat() * 0.5f) * (1f - 0.35f * cp)
            val col = if (layer == 1) pal.hot else pal.base
            for (k in 0 until segs) {
                val f = (k.toFloat() / segs).pow(1.4f)
                val s0 = a0 + k.toFloat() / segs * 2f * PI.toFloat() * 0.92f
                val sweep = 2f * PI.toFloat() * 0.92f / segs + 0.02f
                drawArc(
                    color = col.copy(alpha = (baseA * f).coerceIn(0f, 1f)),
                    startAngle = Math.toDegrees(s0.toDouble()).toFloat(),
                    sweepAngle = Math.toDegrees(sweep.toDouble()).toFloat(),
                    useCenter = false,
                    topLeft = Offset(-r, -ry),
                    size = Size(r * 2f, ry * 2f),
                    style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

// ── ② 혜성 꼬리 + 흩날리는 빛 ──
private fun DrawScope.drawComet(
    fly: Float, flyS: Float, u: Float,
    pathAt: (Float) -> Offset, sizeAt: (Float) -> Float, pal: BirthPalette,
) {
    val step = 0.012f
    // 꼬리 — 길을 따라 촘촘히 놓은 빛 스프라이트(겹쳐도 줄무늬가 지지 않는다)
    for (i in TRAIL_STEPS downTo 1) {
        val f = maxOf(0f, fly - i * step)
        val p = pathAt(f)
        val k = 1f - i / (TRAIL_STEPS + 1f)
        val sz = sizeAt(f)
        glow(pal.sBase, p.x, p.y, sz * (0.3f + 0.5f * k), 0.06f + 0.3f * k * k)
        glow(pal.sHot, p.x, p.y, sz * 0.2f * k + 2f * u, 0.62f * k * k * k)
    }
    // 맨 안쪽의 가는 흰 심지
    var prev = pathAt(fly)
    for (i in 1..12) {
        val f = maxOf(0f, fly - i * step * 1.6f)
        val p = pathAt(f)
        val k = 1f - i / 13f
        drawLine(
            Color.White.copy(alpha = 0.85f * k * k), prev, p,
            strokeWidth = (1f + 2.4f * k) * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, blendMode = BlendMode.Plus,
        )
        prev = p
    }
    // 흩날리는 별가루 — 길을 따라 태어나 천천히 흩어지며 사라진다(시간의 함수)
    for (j in 0 until DUST_PARTICLES) {
        val tb = j * 0.043f + rnd(j) * 0.03f
        val age = flyS - tb
        if (age <= 0f || age > 0.75f) continue
        val q = age / 0.75f
        val p = pathAt(clamp01(tb / FLY_S))
        val dx = (rnd(j, 3) - 0.5f) * 70f * q * u
        val dy = ((rnd(j, 5) - 0.3f) * 48f * q + 40f * q * q) * u
        val a = sin(minOf(1f, q * 1.6f) * PI.toFloat() * 0.5f) * (1f - q)
        val x = p.x + dx
        val y = p.y + dy
        val img = if (j % 3 == 0) pal.sWhite else if (j % 3 == 1) pal.sMid else pal.sBase
        glow(img, x, y, (5f + 5f * rnd(j, 7) * (1f - q)) * u, 0.5f * a)
        if (j % 2 == 0) glint(x, y, (2f + 3.2f * rnd(j, 9) * (1f - q * 0.5f)) * u, q * 3f + j, a)
    }
}

// ── ③ 내려앉기 — 바닥을 쓸고 가는 빛 한 번, 얇은 고리 하나, 고리 위에 떠오르는 반짝 ──
private fun DrawScope.drawLanding(lt: Float, lx: Float, ly: Float, u: Float, pal: BirthPalette) {
    if (lt < 0f) return
    val q = clamp01(lt / 1.15f)
    val eo = easeOut(q)
    val fade = 1f - q
    val gy = ly + 8f * u
    // 바닥 빛 번짐(납작한 타원)
    val r1 = (40f + 92f * eo) * u
    glowE(pal.sBase, lx, gy, r1, r1 * GROUND_K, 0.6f * fade * fade)
    val r2 = (22f + 30f * eo) * u
    glowE(pal.sHot, lx, gy, r2, r2 * GROUND_K, 0.6f * fade * fade * fade)
    // 얇은 고리 하나(두 겹 — 번짐 + 선)
    val rr = (18f + 104f * eo) * u
    val topLeft = Offset(lx - rr, gy - rr * GROUND_K)
    val ringSize = Size(rr * 2f, rr * 2f * GROUND_K)
    drawOval(pal.base.copy(alpha = 0.22f * fade), topLeft, ringSize, style = Stroke(width = (6f * fade + 1f) * u), blendMode = BlendMode.Plus)
    drawOval(pal.hot.copy(alpha = 0.9f * fade), topLeft, ringSize, style = Stroke(width = 1.2f * u), blendMode = BlendMode.Plus)
    // 고리 위에 돋는 작은 반짝들(고리를 따라 퍼지며 차례로 번쩍)
    for (i in 0 until 9) {
        val an = i / 9f * 2f * PI.toFloat() + 0.4f
        val d = clamp01((q - rnd(i) * 0.35f) / 0.5f)
        if (d <= 0f || d >= 1f) continue
        val a = sin(d * PI.toFloat())
        val x = lx + cos(an) * rr
        val y = gy + sin(an) * rr * GROUND_K - 8f * u * d
        glow(pal.sBase, x, y, 9f * u, 0.35f * a)
        glint(x, y, (2.4f + 3.4f * a) * u, d * 3f + i, 0.95f * a)
    }
}

// 별 위에 한 번: 윗 가장자리의 큰 반짝 하나 + 지도의 새 별(반딧불)로 이어지는 떠오르는 빛
private fun DrawScope.drawArrivalGlints(lt: Float, lx: Float, ly: Float, starSize: Float, T: Float, u: Float, pal: BirthPalette) {
    if (lt < 0f) return
    val q = clamp01((lt - 0.12f) / 0.9f)
    if (q > 0f && q < 1f) {
        val a = sin(q * PI.toFloat())
        val x = lx - starSize * 0.08f
        val y = ly - starSize * 0.42f
        glow(pal.sHot, x, y, (26f * a + 8f) * u, 0.6f * a)
        glint(x, y, (5f + 17f * a) * u, q * 2.6f, a)
    }
    val on = smooth(0.3f, 1.0f, lt) // 지도에서 새 별에 붙는 반딧불(같은 식)
    val fadeOut = 1f - smooth(LAND_S - 0.5f, LAND_S, lt)
    for (i in 0 until 7) {
        val ph = (T * 0.2f + i / 7f) % 1f
        val ang = i * 2.4f + T * 0.35f
        val x = lx + cos(ang) * starSize * (0.75f + 0.45f * sin(i * 7.1f))
        val y = ly + starSize * 0.5f - ph * starSize * 2.3f
        val al = sin(ph * PI.toFloat()) * on * fadeOut * (0.55f + 0.45f * sin(T * 3.1f + i * 1.7f))
        glow(pal.sBase, x, y, maxOf(3f * u, starSize * 0.2f), 0.55f * al)
        glow(pal.sHot, x, y, 1.6f * u, 0.9f * al)
    }
}
