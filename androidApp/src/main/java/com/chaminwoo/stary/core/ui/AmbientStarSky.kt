package com.chaminwoo.stary.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// 정지된 배경 화면(지도 제외)의 반짝이는 잔별 — 로그인 화면 하늘(LoginDecor `LivingSky`)과 같은 별을 **더 어둡게**.
// iOS `Core/AmbientStarSky.swift` 와 같은 값/식(같은 시드 → 같은 배치).

/**
 * 화면 배경 이미지 + 어둡게 틴트 + 반짝이는 잔별 — iOS `ScreenBackground(name:darken:)` 대응.
 * 예전엔 화면마다 `Image(painterResource(..)) + ColorFilter.tint(Darken)` 을 직접 그렸다(정지 화면이라 너무 정적이었다, 2026-10-19).
 */
@Composable
fun ScreenBackground(@DrawableRes res: Int, darken: Float) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.tint(Color.Black.copy(alpha = darken), blendMode = BlendMode.Darken),
    )
    AmbientStarSky(Modifier.fillMaxSize())
}

/** 배경 잔별 1개. 좌표는 화면 비율(0..1), 반지름 dp. */
private class AmbientStar(
    val x: Float, val y: Float, val r: Float,
    val speed: Float, val phase: Float,
    val color: Color, val sparkle: Boolean,
)

private const val AMBIENT_STARS = 72
private const val AMBIENT_SPARKLES = 5
/** 로그인 하늘 대비 밝기 — 배경(검정 틴트 0.82~0.86)이 이미 아주 어두워 은은하게만 보이게. */
private const val AMBIENT_BRIGHTNESS = 0.42f
/** 화면에 들어올 때 별이 나타나는 시간(ms). */
private const val AMBIENT_FADE_IN_MS = 900

private val AMBIENT_COLORS = arrayOf(
    Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFBFD8FF), Color(0xFFBFD8FF),
    Color(0xFFFFE9B8), Color(0xFFFFC2E2),
)

/** iOS `AmbientSky.stars` 와 **같은 난수 호출 순서**(x, y, r, speed, phase, color). */
private val ambientStars: List<AmbientStar> by lazy {
    val rnd = Random(20261019L)
    List(AMBIENT_STARS) { i ->
        val sparkle = i < AMBIENT_SPARKLES
        AmbientStar(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            r = if (sparkle) 0.8f + rnd.nextFloat() * 0.5f else 0.4f + rnd.nextFloat() * 0.55f,
            speed = 0.4f + rnd.nextFloat() * 1.0f,
            phase = rnd.nextFloat() * (2f * PI.toFloat()),
            color = AMBIENT_COLORS[rnd.nextInt(AMBIENT_COLORS.size)],
            sparkle = sparkle,
        )
    }
}

/**
 * 천천히 숨 쉬듯 반짝이는 잔별(일부는 짧은 빛줄기) — 화면 전체에 흩어져 있고 로그인 하늘보다 훨씬 어둡다. 유성은 없다.
 * 화면이 컴포지션에 있는 동안만 프레임 루프가 돈다. 시간은 상태로 들고 **draw 단계에서만** 읽어 재구성 없이 다시 그리기만 한다.
 */
@Composable
fun AmbientStarSky(modifier: Modifier = Modifier) {
    val fade = remember { Animatable(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(AMBIENT_FADE_IN_MS)) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - t0) / 1_000_000_000f }
    }
    Canvas(modifier = modifier) {
        val a = fade.value * AMBIENT_BRIGHTNESS
        if (a <= 0.005f) return@Canvas
        val t = time.floatValue
        val w = size.width
        val h = size.height
        for (s in ambientStars) {
            val tw = 0.5f + 0.5f * sin(t * s.speed + s.phase)
            val k = 0.30f + 0.70f * tw
            val c = Offset(s.x * w, s.y * h)
            val rPx = s.r.dp.toPx()
            if (s.sparkle) drawSparkleStar(c, rPx, s.color, k, t * 0.12f + s.phase, dim = a)
            else drawCircle(s.color.copy(alpha = (0.80f * k * a).coerceIn(0f, 1f)), rPx, c)
        }
    }
}

/**
 * 큰 잔별 — 가장자리가 부드럽게 사라지는 작은 번짐 + 심지 + **짧고 가는** 빛줄기(가로가 세로보다 길다).
 * [k] 는 반짝임(0..1 — 빛줄기 길이와 밝기), [dim] 은 밝기에만 곱한다(배경 잔별을 어둡게 할 때 빛줄기 길이는 그대로).
 * 로그인 하늘(LoginDecor `LivingSky`)과 배경 잔별이 함께 쓴다.
 * (처음엔 딱 떨어지는 반투명 원판 후광 + 긴 십자선이라 회색 판에 십자 표시처럼 부자연스러웠다 — 방사형 그라데이션으로 바꾸고 반 이하로 줄였다.)
 */
internal fun DrawScope.drawSparkleStar(c: Offset, r: Float, color: Color, k: Float, angle: Float, dim: Float = 1f) {
    val a = (k * dim).coerceIn(0f, 1f)
    val glowR = r * 3.4f
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.22f * a), Color.Transparent), center = c, radius = glowR),
        radius = glowR, center = c,
    )
    drawCircle(color.copy(alpha = 0.95f * a), r, c)
    val len = r * (3.0f + 2.6f * k)
    val ca = cos(angle) * 0.35f // ±20° 안에서만 흔들려 반듯한 십자가 되지 않게
    val sa = sin(angle) * 0.35f
    for (dir in 0 until 2) {
        val dx = if (dir == 0) 1f else -sa
        val dy = if (dir == 0) ca else 1f
        val n = sqrt(dx * dx + dy * dy)
        val l = if (dir == 0) len else len * 0.7f   // 세로는 가로의 70%
        val ux = dx / n * l
        val uy = dy / n * l
        drawLine(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.60f * a), Color.Transparent),
                start = Offset(c.x - ux, c.y - uy), end = Offset(c.x + ux, c.y + uy),
            ),
            start = Offset(c.x - ux, c.y - uy), end = Offset(c.x + ux, c.y + uy),
            strokeWidth = max(0.8f, r * 0.38f), cap = StrokeCap.Round,
        )
    }
}
