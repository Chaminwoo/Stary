package com.chaminwoo.stary.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 입자 한 개의 고정 시드(모양/속도/위상). */
private class ParticleSeed(
    val angle: Float,
    val radiusFrac: Float,
    val speed: Float,
    val phase: Float,
    val sizeFrac: Float,
    val drift: Float,
)

private enum class Motion { ORBIT, RISE, FALL }

private fun motionOf(effect: ParticleEffect): Motion = when (effect) {
    ParticleEffect.SNOW -> Motion.FALL
    ParticleEffect.EMBER, ParticleEffect.HEART, ParticleEffect.MUSIC, ParticleEffect.BUBBLE -> Motion.RISE
    else -> Motion.ORBIT
}

/**
 * 히든 업적 아이콘 주변의 파티클 이펙트.
 * 아이콘 크기([boxSize])보다 넉넉하게 그려도 레이아웃(측정)은 [boxSize] 로 고정해
 * 부모 배치를 흔들지 않는다(그리기만 넘침).
 */
@Composable
fun HiddenParticles(
    effect: ParticleEffect,
    tint: Color,
    boxSize: Dp,
    particleCount: Int = 12,
) {
    val seeds = remember(effect, particleCount) {
        List(particleCount) { i ->
            val r = Random(i * 9973 + effect.ordinal * 31 + 7)
            ParticleSeed(
                angle = r.nextFloat() * (2f * PI.toFloat()),
                radiusFrac = 0.30f + r.nextFloat() * 0.22f,
                speed = 0.35f + r.nextFloat() * 0.85f,
                phase = r.nextFloat(),
                sizeFrac = 0.035f + r.nextFloat() * 0.05f,
                drift = (r.nextFloat() - 0.5f) * 0.5f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(effect) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    val motion = motionOf(effect)
    Canvas(
        Modifier
            // 측정은 boxSize 로 고정하되, 실제 그리기 영역은 1.7배로 잡아 넘치게 그린다.
            .layout { measurable, _ ->
                val px = boxSize.roundToPx()
                val draw = (px * 1.7f).toInt()
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(draw, draw)
                )
                layout(px, px) {
                    placeable.place(-(draw - px) / 2, -(draw - px) / 2)
                }
            }
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val half = size.minDimension / 2f
        for (s in seeds) {
            val col = particleColor(effect, tint, s, t)
            val rad = s.sizeFrac * half * (0.8f + 0.4f * sin((t * s.speed * 3f + s.phase * 6.28f)))
            val pos: Offset
            val alpha: Float
            when (motion) {
                Motion.ORBIT -> {
                    val a = s.angle + t * s.speed * 0.9f
                    val rr = s.radiusFrac * half * (1f + 0.10f * sin(t * 1.3f + s.phase * 6.28f))
                    pos = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
                    alpha = 0.35f + 0.55f * (0.5f + 0.5f * sin(t * 2.2f * s.speed + s.phase * 6.28f))
                }
                Motion.RISE -> {
                    val p = ((t * s.speed * 0.5f) + s.phase) % 1f
                    val x = c.x + s.drift * half + sin((p + s.phase) * 6.28f) * half * 0.14f
                    val y = c.y + half * 0.9f - p * half * 1.8f
                    pos = Offset(x, y)
                    alpha = (1f - p) * 0.85f
                }
                Motion.FALL -> {
                    val p = ((t * s.speed * 0.4f) + s.phase) % 1f
                    val x = c.x + s.drift * half + sin((p + s.phase) * 6.28f) * half * 0.16f
                    val y = c.y - half * 0.9f + p * half * 1.8f
                    pos = Offset(x, y)
                    alpha = if (p < 0.15f) p / 0.15f else if (p > 0.85f) (1f - p) / 0.15f else 0.85f
                }
            }
            drawCircle(color = col.copy(alpha = (col.alpha * alpha).coerceIn(0f, 1f)), radius = rad, center = pos)
        }
    }
}

private fun particleColor(effect: ParticleEffect, tint: Color, s: ParticleSeed, t: Float): Color = when (effect) {
    ParticleEffect.STARDUST -> lerp(tint, Color.White, 0.5f + 0.5f * sin(t * 2f + s.phase * 6.28f))
    ParticleEffect.AURORA -> lerp(Color(0xFFFFD86F), Color(0xFFB388FF), 0.5f + 0.5f * sin(t * 1.2f + s.phase * 6.28f))
    ParticleEffect.SHADOW -> Color(0xFF9AA7B0).copy(alpha = 0.5f)
    ParticleEffect.EMBER -> lerp(Color(0xFFFFB74D), Color(0xFFFF7043), s.phase)
    ParticleEffect.HEART -> lerp(Color(0xFFFF8FB3), Color(0xFFFF4D79), s.phase)
    ParticleEffect.MUSIC -> lerp(tint, Color.White, 0.3f)
    ParticleEffect.SNOW -> Color.White
    ParticleEffect.ORBIT -> tint
    ParticleEffect.BUBBLE -> lerp(Color(0xFF7FE0FF), Color.White, 0.5f + 0.5f * sin(t * 2.4f + s.phase * 6.28f))
}
