package com.chaminwoo.stary.feature.auth.screen

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.Haptics
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 로그인 화면 장식 — 살아있는 하늘 · 로고 글린트/화질 · 크림 캡슐 버튼. (iOS `LoginView.swift` 와 같은 값/식)

// ───────────────────────── 로고 비트맵(화질) ─────────────────────────

/**
 * 로고 비트맵 — **밀도 확대 없이 + 밉맵으로** 읽는다.
 * `res/drawable-nodpi/logo.webp`(1152x768, tools/logo/make_logo.py 로 생성)를 `inScaled=false` 로 디코드하고
 * `setHasMipMap(true)` 로 HWUI 가 축소할 때 밉맵을 쓰게 한다. 예전엔 `res/drawable`(밀도 구분 없음)에 있어서
 * 안드로이드가 기기 밀도만큼 확대해 디코드한 뒤(예: 450dpi → 4320x2880, 약 50MB) 다시 줄여 그려 가장자리가 거칠었다.
 */
@Composable
internal fun rememberLogoBitmap(): ImageBitmap {
    val context = LocalContext.current
    return remember {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.logo, opts)
        if (bmp != null) {
            bmp.setHasMipMap(true)
            bmp.asImageBitmap()
        } else {
            ImageBitmap.imageResource(context.resources, R.drawable.logo) // 디코드 실패 시 일반 경로
        }
    }
}

/**
 * 로고 글린트 — 가끔 비스듬한 빛줄기가 로고 글자·별 위를 훑고 지나간다.
 * [progress] 는 -0.3 → 1.3 (로고 폭 비율 위치, 범위 밖이면 그리지 않음). `SrcAtop` 이라 **로고가 있는 픽셀 위에만** 칠해진다
 * (그래서 오프스크린 레이어로 격리). 값은 draw 단계에서만 읽어 매 프레임 재구성이 없다.
 */
internal fun Modifier.logoGlint(progress: Animatable<Float, AnimationVector1D>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress.value
        if (p <= -0.3f || p >= 1.3f) return@drawWithContent
        val w = size.width
        val h = size.height
        val cx = w * p
        val half = w * 0.13f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.80f), Color.Transparent),
                start = Offset(cx - half, 0f),
                end = Offset(cx + half, h * 0.45f), // 기울어진 띠("/" 방향으로 휩쓴다)
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

// ───────────────────────── 살아있는 하늘 ─────────────────────────

/** 하늘 위 잔별 1개. 좌표는 화면 비율(x 0..1, y 0..SKY_MAX_Y), 반지름 dp. */
private class SkyStar(
    val x: Float, val y: Float, val r: Float,
    val speed: Float, val phase: Float,
    val color: Color, val sparkle: Boolean,
)

private const val SKY_STARS = 84
private const val SKY_SPARKLES = 6
/** 별은 화면 위 60% 에만 — 영상의 지구(화면 약 71% 아래)와 푸른 테두리 빛을 가리지 않는다. */
private const val SKY_MAX_Y = 0.60f
/** 유성 — 주기(초) / 한 번 날아가는 시간(초) / 첫 유성이 나오기 전 대기(초). */
private const val METEOR_PERIOD = 7.5f
private const val METEOR_DURATION = 0.95f
private const val METEOR_FIRST_AFTER = 1.8f

private val SKY_COLORS = arrayOf(
    Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFBFD8FF), Color(0xFFBFD8FF),
    Color(0xFFFFE9B8), Color(0xFFFFC2E2),
)

private fun buildSkyStars(): List<SkyStar> {
    val rnd = Random(20261008L)
    return List(SKY_STARS) { i ->
        val sparkle = i < SKY_SPARKLES
        SkyStar(
            x = rnd.nextFloat(),
            y = rnd.nextFloat() * SKY_MAX_Y,
            r = if (sparkle) 0.9f + rnd.nextFloat() * 0.6f else 0.45f + rnd.nextFloat() * 0.65f,
            speed = 0.6f + rnd.nextFloat() * 1.7f,
            phase = rnd.nextFloat() * (2f * PI.toFloat()),
            color = SKY_COLORS[rnd.nextInt(SKY_COLORS.size)],
            sparkle = sparkle,
        )
    }
}

/**
 * 영상이 끝나 정지한 뒤에도 하늘이 살아 있게 — 잔별이 천천히 반짝이고(일부는 십자 빛줄기) 7~8초마다 유성이 스친다.
 * [visible] 이 true 가 되면 1.4초에 걸쳐 나타난다(영상 마지막 프레임 위에 이음매 없이). 보이지 않는 동안엔 프레임 루프도 쉰다.
 * 시간은 상태로 들고 **draw 단계에서만** 읽어 매 프레임 재구성 없이 다시 그리기만 한다.
 */
@Composable
internal fun LivingSky(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(1400), label = "skyAlpha")
    val stars = remember { buildSkyStars() }
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val t0 = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { timeSec = (it - t0) / 1_000_000_000f }
    }
    Canvas(modifier = modifier) {
        val a = alpha
        if (a <= 0.01f) return@Canvas
        val t = timeSec
        val w = size.width
        val h = size.height
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(t * s.speed + s.phase)
            val k = 0.40f + 0.60f * tw                 // 은은한 깜빡임(번쩍이지 않고 숨 쉬듯)
            val c = Offset(s.x * w, s.y * h)
            val rPx = s.r.dp.toPx()
            if (s.sparkle) drawSparkleStar(c, rPx, s.color, k * a, t * 0.15f + s.phase)
            else drawCircle(s.color.copy(alpha = (0.80f * k * a).coerceIn(0f, 1f)), rPx, c)
        }
        drawMeteor(t, a, w, h)
    }
}

/**
 * 큰 잔별 — 가장자리가 부드럽게 사라지는 작은 번짐 + 심지 + **짧고 가는** 빛줄기(가로가 세로보다 길다).
 * (처음엔 딱 떨어지는 반투명 원판 후광 + 긴 십자선이라 회색 판에 십자 표시처럼 부자연스러웠다 — 방사형 그라데이션으로 바꾸고 반 이하로 줄였다.)
 */
private fun DrawScope.drawSparkleStar(c: Offset, r: Float, color: Color, k: Float, angle: Float) {
    val a = k.coerceIn(0f, 1f)
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
        val n = kotlin.math.sqrt(dx * dx + dy * dy)
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

/** 주기(METEOR_PERIOD)마다 한 번, 주기 안의 무작위 시각에 우상단→좌하단으로 스치는 유성. 위치/각도는 주기 번호의 해시로 결정(재현 가능). */
private fun DrawScope.drawMeteor(t: Float, alpha: Float, w: Float, h: Float) {
    if (t < METEOR_FIRST_AFTER) return
    val tt = t - METEOR_FIRST_AFTER
    val k = (tt / METEOR_PERIOD).toInt()
    val rnd = Random(k * 7919L + 13L)
    val start = 0.6f + rnd.nextFloat() * 3.2f                 // 주기 안 시작 오프셋
    val local = tt - (k * METEOR_PERIOD + start)
    if (local < 0f || local > METEOR_DURATION) return
    val p = local / METEOR_DURATION
    val x0 = w * (0.55f + rnd.nextFloat() * 0.40f)
    val y0 = h * (0.03f + rnd.nextFloat() * 0.22f)
    val ang = (205f + rnd.nextFloat() * 25f) * (PI.toFloat() / 180f)   // 왼쪽 아래로
    val dirX = cos(ang); val dirY = -sin(ang)                          // 화면 좌표(y 아래)
    val travel = w * 0.55f
    val e = 1f - (1f - p) * (1f - p)                                    // easeOut
    val head = Offset(x0 + dirX * travel * e, y0 + dirY * travel * e)
    val tailLen = w * 0.20f * min(1f, p * 3f) * (1f - 0.5f * p)
    val tail = Offset(head.x - dirX * tailLen, head.y - dirY * tailLen)
    val fade = sin(PI.toFloat() * p) * alpha                            // 양 끝에서 사라짐
    drawLine(
        brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * fade)), start = tail, end = head),
        start = tail, end = head, strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round,
    )
    drawCircle(Color.White.copy(alpha = 0.35f * fade), 5.5.dp.toPx(), head)
    drawCircle(Color.White.copy(alpha = fade), 1.8.dp.toPx(), head)
}

// ───────────────────────── 크림 캡슐 버튼 ─────────────────────────

/**
 * 로그인 버튼 — 예전 크림색 캡슐(`StarDiaryButton`)의 모습으로 되돌리되, 아주 살짝 비치는 면(알파 0.94) + 위에서 아래로 약해지는
 * 얇은 하이라이트 테두리 + 누를 때 반응(작아짐·밝아짐·**진동**)만 더했다. 글자/아이콘은 진한 숯색(0xFF2C2723) — 크림 면 위에서 가장 잘 읽힌다.
 * (2026-10-08 첫 시도인 "글래스(투명 흰 면 + 흰 글씨)"는 지구 위에서 회색빛으로 흐려 글씨가 안 보여 되돌림.)
 * 진동은 **터치 down 순간**(`Haptics.light`) — iOS `CreamCapsuleButton` 은 `Haptics.soft`.
 */
@Composable
internal fun CreamCapsuleButton(
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "creamPress")
    LaunchedEffect(pressed) { if (pressed) Haptics.light() }
    val shape = RoundedCornerShape(50)
    val creamTop = Color(0xFFF7EDD8)
    val creamBottom = Color(0xFFE9D6AE)
    val charcoal = Color(0xFF2C2723)
    Box(
        modifier = modifier.graphicsLayer {
            val s = 1f - 0.02f * press
            scaleX = s; scaleY = s
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(creamTop, Color.White, 0.35f * press).copy(alpha = 0.94f),
                            lerp(creamBottom, Color.White, 0.25f * press).copy(alpha = 0.94f),
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.10f))),
                    shape,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 26.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = charcoal, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
            Text(text = text, color = charcoal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
