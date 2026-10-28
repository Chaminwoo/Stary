package com.chaminwoo.stary.feature.map.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/*
 * 지도 위 땅 빛 · 비네트(2026-10-28 "하나의 우주" 지도 시안 ground.js / world.js 를 수치 그대로 옮김).
 *  - 땅 빛: 시안 바닥 셰이더의 (1) 먼 곳 보랏빛 반사(프레넬) (2) 화면을 비스듬히 가로지르는 넓은 빛띠 두 겹(아주 느리게 흐른다)
 *          (3) 한 곳에 모인 부드러운 반사광.
 *  - 비네트: 아래쪽 어둠 + 가장자리 둥근 어둠.
 * 2026-10-28 피드백: 하늘 띠 · 위쪽 안개 삭제, 확대(줌 13+)에서만 시안 느낌 — 줌을 빼면(11-) 예전 지도처럼
 * 땅 빛이 꺼지고 예전 비네트로 돌아간다. 그 사이는 [mapNearFactor] 로 부드럽게 섞는다(스타일 JSON 의 전환 구간과 같다).
 * 시안은 화면 390×844 기준 px — 여기선 위치는 화면 비율로 쓴다.
 *
 * ⚠️ 지도(MapLibre)는 별도 GL 표면이라 이 오버레이의 BlendMode.Plus 는 지도와 더해지지 않는다(투명 창에 더해질 뿐).
 *    그래서 시안의 "더하기 빛 C" 는 C 를 가장 밝은 채널로 나눈 색 L 을 불투명도 max(C) 로 덮어 근사한다(어두운 땅에서 ≈ +C).
 * iOS `MapSkyOverlay.swift` 와 같은 수치.
 */

/** 예전 지도 → 새(시안) 지도 전환 줌 구간 — maplibre_style.json(gen_style.py 의 Z_FAR/Z_NEAR) · iOS 와 같은 값. */
internal const val MAP_NEAR_FROM = 11f
internal const val MAP_NEAR_TO = 13f

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
private fun fract(x: Float) = x - floor(x)

/** 0(줌 11 이하, 예전 지도) .. 1(줌 13 이상, 새 지도) — 부드러운 전환 계수. */
internal fun mapNearFactor(zoom: Float): Float = smooth(MAP_NEAR_FROM, MAP_NEAR_TO, zoom)

/** 시안 카메라(MapLibre 와 같은 원근: 거리 1.5H, 기울기 40°)에서 화면 y 비율(0=위) → 바닥 깊이(화면 가운데 = 1). */
private fun depthAt(yFrac: Float): Float {
    val s = sin(BASE_TILT_DEG * PI / 180).toFloat()
    val c = cos(BASE_TILT_DEG * PI / 180).toFloat()
    val u = (0.5f - yFrac) / 1.5f
    val den = c - u * s
    return if (den > 0.02f) 1f + u * s / den else 4f
}
/** "더하기 빛" 근사 — 색 [r,g,b](0..1, 더할 양)를 가장 밝은 채널로 정규화한 색 + 그 채널 값의 불투명도. */
private fun addLight(r: Float, g: Float, b: Float, k: Float): Color {
    val m = maxOf(r, g, b)
    if (m <= 1e-4f || k <= 0f) return Color.Transparent
    return Color(r / m, g / m, b / m, (m * k).coerceIn(0f, 1f))
}

// ── 땅 빛 ─────────────────────────────────────────────────────────────

/**
 * 땅 빛. [k] 0..1(저줌 글로브 전환 · 줌을 뺀 예전 지도에선 0), [zoom] 카메라 줌, [t] 초.
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
}

/**
 * 비네트 — [near] 0 이면 예전 비네트(가장자리 둥근 어둠 0.30), 1 이면 시안 비네트(아래쪽 190px 어둠 + 가장자리 어둠), 사이는 섞는다.
 */
internal fun DrawScope.drawMapVignette(near: Float) {
    val w = size.width
    val h = size.height
    val old = 1f - near
    if (old > 0.003f) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.30f * old),
                center = center,
                radius = hypot(w, h) / 2f,
            )
        )
    }
    if (near <= 0.003f) return
    val bh = h * 190f / 844f
    drawRect(
        Brush.verticalGradient(0f to Color(3, 4, 12).copy(alpha = 0f), 1f to Color(3, 4, 12).copy(alpha = 0.55f * near), startY = h - bh, endY = h),
        topLeft = Offset(0f, h - bh), size = Size(w, bh),
    )
    val c = Offset(w / 2f, h * 0.55f)
    val r1 = h * 0.78f
    drawRect(
        Brush.radialGradient(
            0f to Color(2, 3, 10).copy(alpha = 0f),
            (0.32f / 0.78f) to Color(2, 3, 10).copy(alpha = 0f),
            1f to Color(2, 3, 10).copy(alpha = 0.5f * near),
            center = c, radius = r1,
        )
    )
}
