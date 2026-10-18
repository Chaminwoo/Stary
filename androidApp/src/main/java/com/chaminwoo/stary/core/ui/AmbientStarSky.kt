package com.chaminwoo.stary.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
 * 화면 배경 이미지 + 어둡게 틴트 + 반짝이는 잔별 — iOS `ScreenBackground(name:darken:scroll:)` 대응.
 * 예전엔 화면마다 `Image(painterResource(..)) + ColorFilter.tint(Darken)` 을 직접 그렸다(정지 화면이라 너무 정적이었다, 2026-10-19).
 *
 * **시차 스크롤**: [scroll] 을 넘기고 같은 객체를 부모 Box 에 `Modifier.nestedScroll(scroll)` 로 달면, 안쪽 목록/스크롤이 움직인 만큼
 * 배경은 [BG_PARALLAX], 잔별은 [STAR_PARALLAX] 비율로 같은 방향(내리면 위로)으로 살짝 따라간다.
 * 이미지는 **위아래로 뒤집은 사본을 번갈아 이어 붙여**(거울 타일) 이음매 없이 끝없이 이어진다 — 아무리 길게 내려도 배경이 끊기지 않는다.
 */
@Composable
fun ScreenBackground(@DrawableRes res: Int, darken: Float, scroll: BackgroundScroll? = null) {
    val context = LocalContext.current
    val image = remember(res) { backgroundBitmap(context, res) }
    val tint = remember(darken) { ColorFilter.tint(Color.Black.copy(alpha = darken), blendMode = BlendMode.Darken) }
    Canvas(Modifier.fillMaxSize()) {
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        if (iw < 1f || ih < 1f || size.width < 1f || size.height < 1f) return@Canvas
        // ContentScale.Crop 과 같은 맞춤(가운데 기준) — 스크롤 전에는 예전 모습 그대로.
        val s = max(size.width / iw, size.height / ih)
        val tw = ceil(iw * s)
        val th = ceil(ih * s)
        val left = floor((size.width - tw) / 2f)
        val base = (size.height - th) / 2f - (scroll?.offsetPx ?: 0f) * BG_PARALLAX
        var k = floor(-base / th).toInt()
        while (base + k * th < size.height) {
            val y = base + k * th
            val mirrored = Math.floorMod(k, 2) == 1
            // 1px 겹쳐 그려 타일 사이 머리카락 같은 틈이 생기지 않게
            val draw: DrawScope.() -> Unit = {
                drawImage(
                    image,
                    dstOffset = IntOffset(left.toInt(), floor(y).toInt()),
                    dstSize = IntSize(tw.toInt(), th.toInt() + 1),
                    colorFilter = tint,
                )
            }
            if (mirrored) scale(1f, -1f, pivot = Offset(size.width / 2f, floor(y) + (th + 1f) / 2f)) { draw() } else draw()
            k++
        }
    }
    AmbientStarSky(Modifier.fillMaxSize(), scroll)
}

/** 배경 시차 비율 — 콘텐츠가 100px 움직이면 배경 이미지는 12px, 잔별은 20px(잔별이 더 가까운 층처럼 보이게). iOS `ScreenBackground` 와 같은 값. */
private const val BG_PARALLAX = 0.12f
private const val STAR_PARALLAX = 0.20f

/**
 * 배경 시차용 스크롤 누적값 — 화면의 부모 Box 에 `Modifier.nestedScroll(this)` 로 달면 안쪽 스크롤(verticalScroll·Lazy 목록, 플링 포함)이
 * **실제로 움직인 양**(consumed)만 모은다. [offsetPx] 는 콘텐츠가 위로 올라간 누적 거리(px)이고 draw 단계에서만 읽힌다(재구성 없음).
 */
@Stable
class BackgroundScroll : NestedScrollConnection {
    var offsetPx by mutableFloatStateOf(0f)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        offsetPx -= consumed.y
        return Offset.Zero
    }
}

@Composable
fun rememberBackgroundScroll(): BackgroundScroll = remember { BackgroundScroll() }

/**
 * 배경 비트맵 — **밀도 확대 없이** 원본 크기로 디코드해 한 번만 들고 있는다(`res/drawable` 은 밀도 구분이 없어
 * painterResource 로 읽으면 기기 밀도만큼 몇 배로 확대 디코드된다 — 로그인 로고와 같은 이유).
 */
private val backgroundCache = HashMap<Int, ImageBitmap>()

private fun backgroundBitmap(context: Context, @DrawableRes res: Int): ImageBitmap = backgroundCache.getOrPut(res) {
    val opts = BitmapFactory.Options().apply { inScaled = false }
    BitmapFactory.decodeResource(context.resources, res, opts)?.asImageBitmap()
        ?: ImageBitmap.imageResource(context.resources, res)
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
 * [scroll] 이 있으면 스크롤에 [STAR_PARALLAX] 비율로 따라 움직인다.
 * 화면이 컴포지션에 있는 동안만 프레임 루프가 돈다. 시간은 상태로 들고 **draw 단계에서만** 읽어 재구성 없이 다시 그리기만 한다.
 */
@Composable
fun AmbientStarSky(modifier: Modifier = Modifier, scroll: BackgroundScroll? = null) {
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
        if (h < 1f) return@Canvas
        val shift = (scroll?.offsetPx ?: 0f) * STAR_PARALLAX
        for (s in ambientStars) {
            val tw = 0.5f + 0.5f * sin(t * s.speed + s.phase)
            val k = 0.30f + 0.70f * tw
            // 스크롤 시차 — 위로 밀려 나간 별은 아래에서 다시 들어온다(화면 높이로 감싼다).
            val y = ((s.y * h - shift) % h + h) % h
            val c = Offset(s.x * w, y)
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
