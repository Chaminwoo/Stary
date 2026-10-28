package com.chaminwoo.stary.feature.map.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/*
 * 지도 위 하늘 · 땅 빛 · 비네트(2026-10-28 "하나의 우주" 지도 시안 ground.js / world.js 를 수치 그대로 옮김).
 *  - 땅 빛: 시안 바닥 셰이더의 (1) 먼 곳 보랏빛 반사(프레넬) (2) 화면을 비스듬히 가로지르는 넓은 빛띠 두 겹(아주 느리게 흐른다)
 *          (3) 한 곳에 모인 부드러운 반사광 + 위쪽 땅이 남보라 안개로 잠기는 것.
 *  - 하늘: 위쪽 띠(성운 4 · 은하수 · 반짝이는 별 150 · 8초마다 유성 · 지평선 도시 빛).
 *  - 비네트: 아래쪽 어둠 + 가장자리 둥근 어둠.
 * 시안은 화면 390×844 기준 px — 여기선 위치는 화면 비율, 크기는 dp 로 쓴다(시안 1px ≈ 1dp).
 *
 * ⚠️ 지도(MapLibre)는 별도 GL 표면이라 이 오버레이의 BlendMode.Plus 는 지도와 더해지지 않는다(투명 창에 더해질 뿐).
 *    그래서 시안의 "더하기 빛 C" 는 C 를 가장 밝은 채널로 나눈 색 L 을 불투명도 max(C) 로 덮어 근사한다(어두운 땅에서 ≈ +C).
 * iOS `MapSkyOverlay.swift` 와 같은 수치.
 */

private const val TAU = (PI * 2).toFloat()
private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun fract(x: Float) = x - floor(x)

/** 시안 카메라(MapLibre 와 같은 원근: 거리 1.5H, 기울기 40°)에서 화면 y 비율(0=위) → 바닥 깊이(화면 가운데 = 1). */
private fun depthAt(yFrac: Float): Float {
    val s = sin(BASE_TILT_DEG * PI / 180).toFloat()
    val c = cos(BASE_TILT_DEG * PI / 180).toFloat()
    val u = (0.5f - yFrac) / 1.5f
    val den = c - u * s
    return if (den > 0.02f) 1f + u * s / den else 4f
}
private val FOG_NEAR = depthAt(268f / 844f)
private val FOG_FAR = depthAt(92f / 844f)

/** 하늘 띠 높이(화면 높이 비율) — 시안 skyHeight(): 줌 13 에서 122px, 17 에서 94px(844 중). */
internal fun skyHeightFrac(zoom: Float): Float {
    val px = when {
        zoom <= 13f -> 122f
        zoom >= 17f -> 94f
        else -> 122f + (94f - 122f) * (zoom - 13f) / 4f
    }
    return px / 844f
}

/** "더하기 빛" 근사 — 색 [r,g,b](0..1, 더할 양)를 가장 밝은 채널로 정규화한 색 + 그 채널 값의 불투명도. */
private fun addLight(r: Float, g: Float, b: Float, k: Float): Color {
    val m = maxOf(r, g, b)
    if (m <= 1e-4f || k <= 0f) return Color.Transparent
    return Color(r / m, g / m, b / m, (m * k).coerceIn(0f, 1f))
}

// ── 땅 빛 ─────────────────────────────────────────────────────────────

/**
 * 땅 빛 + 위쪽 안개. [k] 0..1(저줌 글로브 전환에선 0), [zoom] 카메라 줌, [t] 초.
 * 시안: near = 1 - smoothstep(0.7, 3.2, 픽셀 하나가 덮는 m) — 줌 15 에서 거의 1, 13 에서 0(멀리 볼수록 광택이 흐려진다).
 */
internal fun DrawScope.drawMapGroundLight(k: Float, zoom: Float, t: Float) {
    if (k <= 0.01f) return
    val w = size.width
    val h = size.height
    val near = 1f - smooth(0.7f, 3.2f, 2f.pow(15f - zoom))

    // (1) 먼 곳일수록 하늘빛(보랏빛)을 비춘다 — col += (0.07, 0.062, 0.19) · gl0² · 0.5
    val fres = Array(9) { i ->
        val y = i / 8f
        val gl0 = ((depthAt(y) - 0.55f) * 1.15f).coerceIn(0f, 1f)
        y to addLight(0.07f, 0.062f, 0.19f, gl0 * gl0 * 0.5f * k)
    }
    drawRect(Brush.verticalGradient(*fres, startY = 0f, endY = h))

    // (2) 넓은 빛줄기 두 겹 — ph = x/W·0.78 + y/H·0.46 을 따라 가우스 띠가 아주 느리게 흐른다
    val fac = 0.3f + 0.7f * 0.52f + 1.1f * near
    val phMax = 1.24f
    val stops = Array(49) { i ->
        val s = i / 48f
        val ph = s * phMax
        val b1 = exp(-((fract(ph * 0.55f - t * 0.011f) - 0.5f) * 5.2f).pow(2))
        val b2 = exp(-((fract(ph * 0.9f + 0.37f - t * 0.017f) - 0.5f) * 9.0f).pow(2))
        val a = (b1 * 0.55f + b2 * 0.3f) * fac
        s to addLight(0.075f, 0.085f, 0.17f, a * k)
    }
    // 그라데이션 축 = ph 의 기울기 방향(0.78/W, 0.46/H). 끝점은 ph = phMax 가 되는 곳.
    val gx = 0.78f / w
    val gy = 0.46f / h
    val g2 = gx * gx + gy * gy
    drawRect(
        Brush.linearGradient(
            *stops,
            start = Offset.Zero,
            end = Offset(gx * phMax / g2, gy * phMax / g2),
            tileMode = TileMode.Clamp,
        )
    )

    // (3) 한 곳에 모인 부드러운 반사광 — (0.62W, 0.58H), exp(-(dx² + (1.45dy)²)·7)
    val hot = (1f + 1.4f * near) * k
    val gauss = Array(13) { i ->
        val r = i / 12f
        r to addLight(0.045f, 0.045f, 0.10f, exp(-r * r * 7f) * hot)
    }
    val c = Offset(w * 0.62f, h * 0.58f)
    withTransform({ scale(1f, (h / 1.45f) / w, c) }) {
        drawCircle(Brush.radialGradient(*gauss, center = c, radius = w), radius = w, center = c)
    }

    // 위쪽 땅은 남보라 안개(0.045, 0.045, 0.11)로 잠긴다 — fog = smoothstep(near, far, depth) 를 한 번 더 부드럽게 × 0.97
    val fogTop = 0.34f
    val fog = Array(11) { i ->
        val y = fogTop * i / 10f
        val f = smooth(FOG_NEAR, FOG_FAR, depthAt(y))
        (y / fogTop) to Color(0.045f, 0.045f, 0.11f, f * f * (3f - 2f * f) * 0.97f * k)
    }
    drawRect(Brush.verticalGradient(*fog, startY = 0f, endY = h * fogTop), size = Size(w, h * fogTop))
}

// ── 하늘 ──────────────────────────────────────────────────────────────

private class SkyStar(val x: Float, val y: Float, val s: Float, val ph: Float, val w: Float, val warm: Boolean)
private class Milky(val u: Float, val off: Float, val s: Float, val ph: Float, val w: Float)
private class Nebula(val x: Float, val y: Float, val rx: Float, val ry: Float, val rot: Float, val c: Color, val a: Float, val v: Float)

private object SkyData {
    val stars: List<SkyStar>
    val milky: List<Milky>
    init {
        val r = kotlin.random.Random(20261027)
        stars = List(150) {
            SkyStar(r.nextFloat(), r.nextFloat().pow(1.3f), 0.35f + r.nextFloat().pow(3) * 1.5f, r.nextFloat() * TAU, 0.5f + r.nextFloat() * 1.8f, r.nextFloat() < 0.18f)
        }
        milky = List(300) {
            val u = r.nextFloat()
            val gj = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) / 1.5f
            Milky(u, gj * 0.085f, 0.25f + r.nextFloat().pow(2.5f) * 0.9f, r.nextFloat() * TAU, 0.4f + r.nextFloat() * 1.5f)
        }
    }
}
private val NEBULAE = listOf(
    Nebula(0.16f, 0.26f, 0.46f, 0.17f, -0.22f, Color(123, 92, 255), 0.16f, 0.006f),
    Nebula(0.80f, 0.20f, 0.38f, 0.14f, 0.20f, Color(62, 123, 255), 0.12f, -0.004f),
    Nebula(0.50f, 0.50f, 0.60f, 0.13f, -0.06f, Color(176, 124, 255), 0.08f, 0.003f),
    Nebula(0.32f, 0.82f, 0.70f, 0.12f, 0.04f, Color(255, 150, 110), 0.045f, 0.002f),
)
private val STAR_COOL = Color(225, 233, 255)
private val STAR_WARM = Color(255, 226, 190)

/** 시안 빛 스프라이트(가운데 1 → 0.2 에서 0.58 → 0.5 에서 0.16 → 0) 를 원 하나로. */
private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.003f || radius <= 0.2f) return
    val a = alpha.coerceAtMost(1f)
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = a), 0.2f to color.copy(alpha = 0.58f * a), 0.5f to color.copy(alpha = 0.16f * a), 1f to color.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

/** 비스듬한 타원 빛(성운 · 지평선) — 시안 glowE. */
private fun DrawScope.glowE(color: Color, center: Offset, rx: Float, ry: Float, rot: Float, alpha: Float) {
    if (alpha <= 0.003f || rx <= 0.2f || ry <= 0.05f) return
    withTransform({
        rotate(rot * 180f / PI.toFloat(), center)
        scale(1f, ry / rx, center)
    }) { glow(color, center, rx, alpha) }
}

/** 굽은 4갈래 반짝(시안 sparkle) — 가운데로 오목하게 휜 네 꼭지. */
private val sparklePath = Path()
private fun DrawScope.sparkle(center: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.003f || r <= 0.1f) return
    val x = center.x
    val y = center.y
    sparklePath.reset()
    sparklePath.moveTo(x, y - r)
    sparklePath.quadraticTo(x, y, x + r, y)
    sparklePath.quadraticTo(x, y, x, y + r)
    sparklePath.quadraticTo(x, y, x - r, y)
    sparklePath.quadraticTo(x, y, x, y - r)
    drawPath(sparklePath, color.copy(alpha = alpha.coerceAtMost(1f)))
}

/**
 * 하늘 띠 — 화면 맨 위(상태 표시줄 · 탑바 뒤)에서 지도 땅으로 이어진다. 시안 drawSky 와 같은 그리기.
 * [density] = dp→px(시안 1px ≈ 1dp).
 */
internal fun DrawScope.drawMapSkyBand(k: Float, zoom: Float, t: Float, density: Float) {
    if (k <= 0.01f) return
    val w = size.width
    val hs = size.height * skyHeightFrac(zoom)
    // 바탕: 위는 거의 검정 → 아래로 옅어진다
    drawRect(
        Brush.verticalGradient(
            0f to Color(3, 5, 13).copy(alpha = k),
            0.28f to Color(5, 7, 20).copy(alpha = 0.95f * k),
            0.5f to Color(8, 9, 26).copy(alpha = 0.78f * k),
            0.72f to Color(10, 10, 30).copy(alpha = 0.4f * k),
            0.9f to Color(11, 11, 30).copy(alpha = 0.12f * k),
            1f to Color(11, 11, 30).copy(alpha = 0f),
            startY = 0f, endY = hs,
        ),
        size = Size(w, hs),
    )
    val ox = t * 1.1f * density // 하늘이 아주 천천히 옆으로 흐른다
    // 성운
    for (n in NEBULAE) {
        val x = ((n.x + ox / w * 0.25f + t * n.v * 0.02f) % 1.4f + 1.4f) % 1.4f - 0.2f
        glowE(n.c, Offset(x * w, n.y * hs), n.rx * w, n.ry * hs * 2.2f, n.rot, n.a * k)
    }
    // 은하수(비스듬한 띠)
    val span = w * 1.2f
    for (m in SkyData.milky) {
        val x0 = -0.1f + m.u * 1.2f
        val y0 = 0.64f - m.u * 0.5f + m.off
        val xx = (((x0 * w + ox * 0.7f) % span) + span) % span - w * 0.1f
        val yy = y0 * hs
        val fade = 1f - smooth(0.5f, 0.95f, yy / hs)
        val tw = 0.55f + 0.45f * sin(t * m.w + m.ph)
        val a = 0.32f * tw * fade * k
        if (a <= 0.004f) continue
        val sz = m.s * density
        drawRect(Color(220, 226, 255).copy(alpha = a.coerceAtMost(1f)), Offset(xx, yy), Size(sz, sz))
    }
    glowE(Color(150, 140, 255), Offset(w * 0.5f, hs * 0.42f), w * 0.75f, hs * 0.09f, -0.38f, 0.07f * k)
    // 별
    for (s in SkyData.stars) {
        val xx = (((s.x * w + ox) % w) + w) % w
        val yy = s.y * hs * 0.92f
        val fade = 1f - smooth(0.48f, 0.92f, yy / hs)
        if (fade <= 0.01f) continue
        val tw = 0.5f + 0.5f * sin(t * s.w + s.ph)
        val c = if (s.warm) STAR_WARM else STAR_COOL
        val p = Offset(xx, yy)
        if (s.s > 1.45f) {
            glow(c, p, (6f + 3f * tw) * density, 0.22f * fade * tw * k)
            sparkle(p, (2.4f + 1.8f * tw) * density, c, (0.35f + 0.5f * tw) * fade * k)
        } else {
            drawCircle(c.copy(alpha = ((0.12f + 0.5f * tw) * (s.s / 1.4f).coerceAtMost(1f) * fade * k).coerceIn(0f, 1f)), s.s * 0.72f * density, p)
        }
    }
    // 유성 — 8초마다 한 번 0.9초
    val idx = floor(t / 8f)
    val u = (t - idx * 8f - 2f) / 0.9f
    if (u in 0f..1f) {
        val sd = kotlin.math.abs((sin(idx * 12.9898f) * 43758.547f) % 1f)
        val sx = w * (0.12f + 0.6f * sd)
        val sy = hs * (0.08f + 0.3f * ((sd * 7.31f) % 1f))
        val len = w * 0.3f
        val dx = len * 0.92f
        val dy = len * 0.36f
        val hx = sx + dx * u
        val hy = sy + dy * u
        val tx = hx - dx * 0.45f
        val ty = hy - dy * 0.45f
        val fade = sin(u * PI.toFloat()) * (1f - smooth(0.5f, 0.9f, hy / hs)) * k
        if (fade > 0.003f) {
            drawLine(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.6f * fade)), Offset(tx, ty), Offset(hx, hy)),
                Offset(tx, ty), Offset(hx, hy), strokeWidth = 1.4f * density, cap = StrokeCap.Round,
            )
            glow(Color.White, Offset(hx, hy), 5f * density, 0.5f * fade)
        }
    }
    // 지평선의 도시 빛(보랏빛 + 따뜻한 빛) — 땅과 하늘이 만나는 곳
    glowE(Color(120, 100, 255), Offset(w * 0.5f, hs * 0.86f), w * 0.9f, hs * 0.16f, 0f, 0.14f * k)
    glowE(Color(255, 160, 110), Offset(w * 0.55f, hs * 0.92f), w * 0.7f, hs * 0.1f, 0f, 0.07f * k)
}

/** 비네트 — 아래쪽 190px(844 중) 어둠 + 화면 가장자리 둥근 어둠(시안 drawVignette). */
internal fun DrawScope.drawMapVignette() {
    val w = size.width
    val h = size.height
    val bh = h * 190f / 844f
    drawRect(
        Brush.verticalGradient(0f to Color(3, 4, 12).copy(alpha = 0f), 1f to Color(3, 4, 12).copy(alpha = 0.55f), startY = h - bh, endY = h),
        topLeft = Offset(0f, h - bh), size = Size(w, bh),
    )
    val c = Offset(w / 2f, h * 0.55f)
    val r1 = h * 0.78f
    drawRect(
        Brush.radialGradient(
            0f to Color(2, 3, 10).copy(alpha = 0f),
            (0.32f / 0.78f) to Color(2, 3, 10).copy(alpha = 0f),
            1f to Color(2, 3, 10).copy(alpha = 0.5f),
            center = c, radius = r1,
        )
    )
}
