package com.chaminwoo.stary.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 히든 업적 **엠블럼**(2026-10-19 재디자인 — "더 예쁘고 특색 있고 수집욕구 들게").
 *
 * 육각 크리스탈(업적마다 두 색 그라데이션 + 빛 방향에 따른 면 음영 + 안쪽 테이블면 + 테두리 하이라이트) 안에 상징 아이콘,
 * 몇 초마다 **빛이 사선으로 훑고 지나간다**. 바깥에는 업적마다 다른 이펙트([ParticleEffect], 12종 — 겹치는 것 없음).
 *
 * 상태([EmblemState]) — 수집 진열장처럼 보이게:
 *  - OWNED   : 내가 가진 것 — 전부(오라·이펙트·광택).
 *  - CLAIMED : 다른 사람이 가져감 — 색·광택은 그대로, 이펙트는 없음("이미 주인이 있다").
 *  - SEALED  : 아직 아무도 못 찾음 — 색을 빼 어두운 봉인 상태 + 흐린 이펙트 미리보기(궁금증).
 *
 * ⚠️ iOS `HiddenAchievements.swift` `HiddenEmblemView` 와 같은 구성·수치(색/크기 비율/주기)를 유지한다.
 */
enum class EmblemState { OWNED, CLAIMED, SEALED }

/** 엠블럼 크기 대비 육각 반지름. */
private const val HEX_FRAC = 0.46f
/** 광택이 한 번 훑는 주기(초) / 그중 실제로 지나가는 시간. */
private const val SHINE_PERIOD = 3.6f
private const val SHINE_SWEEP = 1.1f
/** 이펙트를 그리는 캔버스 = 엠블럼의 이 배(넘쳐 그리되 레이아웃은 엠블럼 크기). */
private const val EFFECT_SCALE = 1.9f

private val SealedInk = Color(0xFF2A2E38)

/** 히든 아이콘 + 엠블럼 + 이펙트 (팝업·업적 목록 공용). */
@Composable
fun HiddenEmblem(
    achievement: HiddenAchievement,
    size: Dp,
    state: EmblemState = EmblemState.OWNED,
) {
    val t = rememberEffectClock()
    val icon = achievement.icon
    val sealed = state == EmblemState.SEALED
    val c1 = if (sealed) lerp(icon.color, SealedInk, 0.72f) else icon.color
    val c2 = if (sealed) lerp(icon.color2, SealedInk, 0.78f) else icon.color2
    val fx = when (state) {
        EmblemState.OWNED -> 1f
        EmblemState.CLAIMED -> 0f
        EmblemState.SEALED -> 0.35f
    }
    val seed = achievement.effect.ordinal
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (fx > 0f) EffectLayer(achievement.effect, icon.color, icon.color2, size, t, front = false, alpha = fx)
        Canvas(Modifier.size(size)) {
            drawEmblem(c1, c2, t, seed, aura = if (sealed) 0.25f else 1f)
        }
        // 상징 — 어두운 그림자 한 겹 + 밝은 본체(유리 안에 새긴 느낌).
        val glyph = size * 0.42f
        Icon(
            achievement.icon.vector, contentDescription = null,
            tint = lerp(c2, Color.Black, 0.45f).copy(alpha = if (sealed) 0.5f else 0.75f),
            modifier = Modifier.size(glyph).offset(y = size * 0.018f),
        )
        Icon(
            achievement.icon.vector, contentDescription = achievement.title,
            tint = if (sealed) Color.White.copy(alpha = 0.42f) else lerp(Color.White, icon.color, 0.18f),
            modifier = Modifier.size(glyph),
        )
        Canvas(Modifier.size(size)) { drawShine(t, seed, if (sealed) 0.22f else 0.5f) }
        if (fx > 0f) EffectLayer(achievement.effect, icon.color, icon.color2, size, t, front = true, alpha = fx)
    }
}

@Composable
private fun rememberEffectClock(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return t
}

// ───────────────────────────── 엠블럼 ─────────────────────────────

/** 꼭짓점이 위인 정육각형. */
private fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = (-90f + 60f * i) * (PI.toFloat() / 180f)
        val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun hexVertex(c: Offset, r: Float, i: Int): Offset {
    val a = (-90f + 60f * i) * (PI.toFloat() / 180f)
    return Offset(c.x + cos(a) * r, c.y + sin(a) * r)
}

private fun DrawScope.drawEmblem(c1: Color, c2: Color, t: Float, seed: Int, aura: Float) {
    val c = center
    val r = size.minDimension * HEX_FRAC
    // 오라 — 3초 주기로 숨 쉬듯.
    val pulse = 0.78f + 0.22f * sin(t * 2.1f + seed)
    drawCircle(
        Brush.radialGradient(listOf(c1.copy(alpha = 0.42f * pulse * aura), Color.Transparent), c, r * 1.65f),
        radius = r * 1.65f, center = c,
    )
    val hex = hexPath(c, r)
    drawPath(
        hex,
        Brush.linearGradient(
            listOf(lerp(c1, Color.White, 0.22f), c1, c2, lerp(c2, Color.Black, 0.38f)),
            start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
        ),
    )
    // 면 — 빛(왼쪽 위)을 향한 면은 밝게, 반대 면은 어둡게. 크리스탈처럼 깎인 느낌.
    val light = (-135f) * (PI.toFloat() / 180f)
    for (i in 0 until 6) {
        val a = hexVertex(c, r, i)
        val b = hexVertex(c, r, (i + 1) % 6)
        val mid = (-60f + 60f * i) * (PI.toFloat() / 180f)
        val k = cos(mid - light)
        val facet = Path().apply { moveTo(c.x, c.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
        drawPath(facet, if (k > 0) Color.White.copy(alpha = 0.16f * k) else Color.Black.copy(alpha = 0.2f * -k))
    }
    // 안쪽 테이블면.
    val table = hexPath(c, r * 0.6f)
    drawPath(table, lerp(c1, c2, 0.5f).copy(alpha = 0.55f))
    drawPath(table, Color.White.copy(alpha = 0.07f))
    drawPath(table, Color.White.copy(alpha = 0.24f), style = Stroke(width = 1.dp.toPx()))
    // 테이블 꼭짓점 → 바깥 꼭짓점 결정선.
    for (i in 0 until 6) {
        drawLine(
            Color.White.copy(alpha = 0.14f), hexVertex(c, r * 0.6f, i), hexVertex(c, r, i),
            strokeWidth = 0.8.dp.toPx(),
        )
    }
    // 테두리 — 은빛 하이라이트가 도는 림.
    val rot = t * 40f + seed * 30f
    rotate(rot, c) {
        drawPath(
            hex,
            Brush.sweepGradient(
                listOf(Color.White.copy(alpha = 0.95f), c1, c2, Color.White.copy(alpha = 0.4f), c1, Color.White.copy(alpha = 0.95f)),
                center = c,
            ),
            style = Stroke(width = size.minDimension * 0.05f, join = StrokeJoin.Round),
        )
    }
    // 왼쪽 위 꼭짓점의 작은 반짝임.
    val tw = 0.5f + 0.5f * sin(t * 3.1f + seed * 1.7f)
    sparkle(hexVertex(c, r, 5), size.minDimension * (0.06f + 0.05f * tw), Color.White.copy(alpha = 0.55f + 0.45f * tw), 0f)
}

/** 사선 광택 — [SHINE_PERIOD] 마다 [SHINE_SWEEP] 초 동안 육각 안을 훑는다(업적마다 시작이 엇갈림). */
private fun DrawScope.drawShine(t: Float, seed: Int, strength: Float) {
    val local = (t + seed * 0.7f) % SHINE_PERIOD
    if (local > SHINE_SWEEP) return
    val p = local / SHINE_SWEEP
    val c = center
    val r = size.minDimension * HEX_FRAC
    val dir = Offset(0.86f, 0.5f)
    val pos = Offset(c.x + (p * 2.6f - 1.3f) * r * dir.x, c.y + (p * 2.6f - 1.3f) * r * dir.y)
    val w = r * 0.32f
    clipPath(hexPath(c, r)) {
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
                start = Offset(pos.x - dir.x * w, pos.y - dir.y * w),
                end = Offset(pos.x + dir.x * w, pos.y + dir.y * w),
            )
        )
    }
}

// ───────────────────────────── 이펙트 ─────────────────────────────

private class Seed(
    val angle: Float, val radius: Float, val speed: Float, val phase: Float, val sizeK: Float, val drift: Float,
)

private fun seeds(effect: ParticleEffect, n: Int): List<Seed> = List(n) { i ->
    val r = Random(i * 9973 + effect.ordinal * 131 + 17)
    Seed(
        angle = r.nextFloat() * 2f * PI.toFloat(),
        radius = r.nextFloat(),
        speed = 0.55f + r.nextFloat() * 0.9f,
        phase = r.nextFloat(),
        sizeK = r.nextFloat(),
        drift = r.nextFloat() - 0.5f,
    )
}

/**
 * 이펙트 한 겹 — [front] = 엠블럼 앞(입자) / 뒤(광선·고리). 측정은 [boxSize] 로 고정하고
 * 그리기만 [EFFECT_SCALE] 배로 넘친다(부모 배치를 흔들지 않게).
 */
@Composable
private fun EffectLayer(
    effect: ParticleEffect, c1: Color, c2: Color, boxSize: Dp, t: Float, front: Boolean, alpha: Float,
) {
    val list = remember(effect) { seeds(effect, 14) }
    Canvas(
        Modifier.layout { measurable, _ ->
            val px = boxSize.roundToPx()
            val draw = (px * EFFECT_SCALE).toInt()
            val placeable = measurable.measure(Constraints.fixed(draw, draw))
            layout(px, px) { placeable.place(-(draw - px) / 2, -(draw - px) / 2) }
        }
    ) {
        val r = size.minDimension / EFFECT_SCALE * HEX_FRAC // 엠블럼 육각 반지름(이 캔버스 좌표)
        drawEffect(effect, c1, c2, t, front, alpha, list, r)
    }
}

private fun DrawScope.drawEffect(
    effect: ParticleEffect, c1: Color, c2: Color, t: Float, front: Boolean, a: Float, s: List<Seed>, r: Float,
) {
    val c = center
    val half = size.minDimension / 2f
    val tau = 2f * PI.toFloat()
    when (effect) {
        // 별의 암호 — 4꼭지 반짝임이 천천히 돌며 깜빡인다.
        ParticleEffect.SPARKLE -> if (front) s.take(10).forEach { p ->
            val ang = p.angle + t * 0.25f * p.speed
            val rr = r * (1.15f + 0.65f * p.radius)
            val tw = max(0f, sin(t * 2.2f * p.speed + p.phase * tau))
            sparkle(
                Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr), r * (0.1f + 0.14f * p.sizeK) * (0.3f + 0.7f * tw),
                lerp(c1, Color.White, 0.6f).copy(alpha = a * tw), t * 0.6f,
            )
        }
        // 극야의 개척자 — 육각 눈송이가 돌며 내린다.
        ParticleEffect.SNOW -> if (front) s.take(12).forEach { p ->
            val q = (t * p.speed * 0.16f + p.phase) % 1f
            val x = c.x + p.drift * half * 1.7f + sin(q * tau + p.phase * 6f) * half * 0.08f
            val y = q * size.height
            val fade = edgeFade(q)
            snowflake(Offset(x, y), r * (0.1f + 0.1f * p.sizeK), Color.White.copy(alpha = a * 0.9f * fade), t * p.speed)
        }
        // 태양의 잔영 — 뒤: 도는 햇살 12갈래 / 앞: 피어오르는 불씨.
        ParticleEffect.SUN -> if (!front) {
            for (k in 0 until 12) {
                val ang = (k * 30f + t * 12f) * (PI.toFloat() / 180f)
                val len = r * (1.55f + 0.18f * sin(t * 2f + k))
                val from = Offset(c.x + cos(ang) * r * 1.02f, c.y + sin(ang) * r * 1.02f)
                val to = Offset(c.x + cos(ang) * len, c.y + sin(ang) * len)
                drawLine(
                    Brush.linearGradient(listOf(c1.copy(alpha = 0.7f * a), Color.Transparent), from, to),
                    from, to, strokeWidth = if (k % 2 == 0) 2.4.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        } else s.take(7).forEach { p -> rising(p, t, c, half, 0.5f) { pos, q ->
            drawCircle(lerp(Color(0xFFFFD27A), Color(0xFFFF7043), p.phase).copy(alpha = a * (1f - q) * 0.9f),
                r * (0.04f + 0.04f * p.sizeK), pos)
        } }
        // 심연의 별 — 반짝이는 고리 거품이 흔들리며 오른다.
        ParticleEffect.BUBBLE -> if (front) s.take(10).forEach { p -> rising(p, t, c, half, 0.36f) { pos, q ->
            val br = r * (0.06f + 0.1f * p.sizeK)
            val col = lerp(c1, Color.White, 0.45f).copy(alpha = a * (1f - q) * 0.9f)
            drawCircle(col, br, pos, style = Stroke(width = 1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = a * (1f - q) * 0.8f), br * 0.28f, Offset(pos.x - br * 0.4f, pos.y - br * 0.4f))
        } }
        // 사라진 항로 — 뒤: 서로 반대로 도는 점선 삼각형 2개 / 앞: 꼭짓점의 빛.
        ParticleEffect.RIFT -> {
            val r1 = r * 1.5f
            val rot1 = t * 22f
            if (!front) {
                rotate(rot1, c) {
                    drawPath(trianglePath(c, r1), c1.copy(alpha = 0.75f * a),
                        style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                }
                rotate(180f - t * 15f, c) {
                    drawPath(trianglePath(c, r * 1.32f), c2.copy(alpha = 0.5f * a), style = Stroke(1.dp.toPx()))
                }
            } else for (k in 0 until 3) {
                val ang = (-90f + 120f * k + rot1) * (PI.toFloat() / 180f)
                val v = Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1)
                val g = 0.6f + 0.4f * sin(t * 3f + k * 2f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a * g), c1.copy(alpha = a * 0.5f * g), Color.Transparent), v, r * 0.22f), r * 0.22f, v)
            }
        }
        // 은하의 정점 — 뒤: 천천히 도는 금빛 후광 / 앞: 금·보라 반짝임.
        ParticleEffect.CROWN -> if (!front) {
            for (k in 0 until 8) {
                val ang = (k * 45f + t * 10f) * (PI.toFloat() / 180f)
                val wdt = 0.13f
                val far = r * 2.0f
                val wedge = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + cos(ang - wdt) * far, c.y + sin(ang - wdt) * far)
                    lineTo(c.x + cos(ang + wdt) * far, c.y + sin(ang + wdt) * far)
                    close()
                }
                drawPath(wedge, Brush.radialGradient(listOf(c1.copy(alpha = 0.45f * a), Color.Transparent), c, far))
            }
        } else s.take(9).forEach { p ->
            val ang = p.angle - t * 0.15f
            val rr = r * (1.25f + 0.55f * p.radius)
            val tw = max(0f, sin(t * 2.6f * p.speed + p.phase * tau))
            sparkle(Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr), r * (0.09f + 0.12f * p.sizeK) * tw,
                (if (p.sizeK > 0.5f) Color(0xFFFFE38A) else Color(0xFFD9B8FF)).copy(alpha = a * tw), 0f)
        }
        // 별도둑 — 짧게 번쩍이는 지그재그 번개 + 빠르게 도는 보라 점.
        ParticleEffect.SPARK -> if (front) {
            for (k in 0 until 4) {
                val period = 1.5f + k * 0.23f
                val cycle = floor((t + k * 0.41f) / period)
                val lp = ((t + k * 0.41f) % period) / period
                if (lp > 0.16f) continue
                val rnd = Random((cycle * 31 + k * 7).toInt())
                val ang = rnd.nextFloat() * tau
                val dirX = cos(ang); val dirY = sin(ang)
                val bolt = Path()
                for (j in 0..4) {
                    val d = r * (1.05f + 0.17f * j)
                    val off = if (j == 0 || j == 4) 0f else (if (j % 2 == 0) 1f else -1f) * r * 0.12f
                    val x = c.x + dirX * d - dirY * off
                    val y = c.y + dirY * d + dirX * off
                    if (j == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
                }
                val fade = 1f - lp / 0.16f
                drawPath(bolt, c1.copy(alpha = a * 0.6f * fade), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(bolt, Color.White.copy(alpha = a * fade), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            s.take(6).forEach { p ->
                val ang = p.angle + t * 2.4f * p.speed
                val rr = r * (1.2f + 0.3f * p.radius)
                drawCircle(lerp(c1, Color.White, 0.3f).copy(alpha = a * 0.8f), r * 0.045f, Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr))
            }
        }
        // 홀로 빛나는 별 — 뒤: 퍼져 나가는 옅은 연기 / 앞: 혼자 도는 위성 하나와 꼬리.
        ParticleEffect.SHADOW -> if (!front) s.take(6).forEach { p ->
            val q = (t * 0.12f * p.speed + p.phase) % 1f
            val d = r * (0.8f + 0.95f * q)
            val pos = Offset(c.x + cos(p.angle) * d, c.y + sin(p.angle) * d)
            val pr = r * (0.35f + 0.45f * q)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9AA7B5).copy(alpha = 0.22f * a * sin(q * PI.toFloat())), Color.Transparent), pos, pr), pr, pos)
        } else {
            val orbit = r * 1.4f
            for (j in 0 until 12) {
                val ang = t * 1.1f - j * 0.07f
                val pos = Offset(c.x + cos(ang) * orbit, c.y + sin(ang) * orbit * 0.62f)
                val k = 1f - j / 12f
                drawCircle(Color.White.copy(alpha = a * (if (j == 0) 1f else 0.45f * k)), r * (if (j == 0) 0.075f else 0.045f * k), pos)
            }
            val head = Offset(c.x + cos(t * 1.1f) * orbit, c.y + sin(t * 1.1f) * orbit * 0.62f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * a), Color.Transparent), head, r * 0.28f), r * 0.28f, head)
        }
        // 일곱 번째 소원 — 사선으로 떨어지는 유성 일곱.
        ParticleEffect.METEOR -> if (front) for (k in 0 until 7) {
            val p = s[k]
            val period = 2.4f + p.speed
            val q = ((t + p.phase * period) % period) / period
            if (q > 0.5f) continue
            val m = q / 0.5f
            val dir = Offset(-0.7071f, 0.7071f)
            val perp = p.drift * half * 1.4f
            val start = Offset(c.x + half * 0.85f + perp * 0.7071f, c.y - half * 0.85f + perp * 0.7071f)
            val len = half * 1.9f
            val head = Offset(start.x + dir.x * len * m, start.y + dir.y * len * m)
            val tail = Offset(head.x - dir.x * r * 0.9f, head.y - dir.y * r * 0.9f)
            val alpha = a * sin(m * PI.toFloat())
            drawLine(Brush.linearGradient(listOf(Color.Transparent, lerp(c1, Color.White, 0.5f).copy(alpha = alpha)), tail, head),
                tail, head, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = alpha), r * 0.05f, head)
        }
        // 두근두근 — 작은 하트가 흔들리며 오른다.
        ParticleEffect.HEART -> if (front) s.take(9).forEach { p -> rising(p, t, c, half, 0.42f) { pos, q ->
            val hs = r * (0.1f + 0.08f * p.sizeK) * (0.85f + 0.15f * sin(t * 6f + p.phase * tau))
            drawPath(heartPath(pos, hs), lerp(Color(0xFFFF9EC2), Color(0xFFFF4D79), p.phase).copy(alpha = a * (1f - q)))
        } }
        // 별들의 오케스트라 — 음표가 흔들리며 오른다.
        ParticleEffect.MUSIC -> if (front) s.take(8).forEach { p -> rising(p, t, c, half, 0.36f) { pos, q ->
            note(pos, r * (0.1f + 0.06f * p.sizeK), lerp(c1, Color.White, 0.35f).copy(alpha = a * (1f - q)), p.sizeK > 0.5f)
        } }
        // 푸른 행성의 발자취 — 기울어진 궤도 고리와 그 위를 도는 달 셋(뒤로 가면 엠블럼에 가려진다).
        ParticleEffect.ORBIT -> {
            val rx = r * 1.62f
            val ry = r * 0.5f
            val tilt = -18f
            if (!front) rotate(tilt, c) {
                drawOval(c1.copy(alpha = 0.45f * a), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.2.dp.toPx()))
            }
            val cosT = cos(tilt * PI.toFloat() / 180f)
            val sinT = sin(tilt * PI.toFloat() / 180f)
            for (k in 0 until 3) {
                val th = t * 0.8f + k * tau / 3f
                val behind = sin(th) < 0f
                if (behind == front) continue
                val ox = cos(th) * rx
                val oy = sin(th) * ry
                val pos = Offset(c.x + ox * cosT - oy * sinT, c.y + ox * sinT + oy * cosT)
                val mr = r * (if (k == 0) 0.11f else 0.075f)
                val col = if (k == 0) lerp(c1, Color.White, 0.4f) else c2
                drawCircle(col.copy(alpha = a * (if (behind) 0.5f else 1f)), mr, pos)
            }
        }
    }
}

/** 아래에서 위로 흔들리며 오르는 입자 공통 — [speedK] 가 클수록 빠르다. */
private inline fun DrawScope.rising(
    p: Seed, t: Float, c: Offset, half: Float, speedK: Float, draw: DrawScope.(Offset, Float) -> Unit,
) {
    val q = (t * p.speed * speedK + p.phase) % 1f
    val x = c.x + p.drift * half * 1.2f + sin((q + p.phase) * 2f * PI.toFloat()) * half * 0.12f
    val y = c.y + half * 0.85f - q * half * 1.7f
    draw(Offset(x, y), q)
}

private fun edgeFade(q: Float): Float = when {
    q < 0.12f -> q / 0.12f
    q > 0.88f -> (1f - q) / 0.12f
    else -> 1f
}

/** 4꼭지 반짝임(가운데가 오목한 별). */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color, rotation: Float) {
    if (r <= 0.3f || color.alpha <= 0.01f) return
    val path = Path()
    for (i in 0 until 8) {
        val ang = rotation + i * (PI.toFloat() / 4f) - PI.toFloat() / 2f
        val rr = if (i % 2 == 0) r else r * 0.26f
        val x = c.x + cos(ang) * rr
        val y = c.y + sin(ang) * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/** 6가지 눈송이(가지 끝에 작은 갈래). */
private fun DrawScope.snowflake(c: Offset, r: Float, color: Color, rotation: Float) {
    val w = max(0.8f, r * 0.16f)
    for (i in 0 until 3) {
        val ang = rotation + i * (PI.toFloat() / 3f)
        val dx = cos(ang) * r
        val dy = sin(ang) * r
        drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), strokeWidth = w, cap = StrokeCap.Round)
    }
    for (i in 0 until 6) {
        val ang = rotation + i * (PI.toFloat() / 3f)
        val bx = c.x + cos(ang) * r * 0.6f
        val by = c.y + sin(ang) * r * 0.6f
        for (sgn in listOf(-1f, 1f)) {
            val b = ang + sgn * 0.7f
            drawLine(color, Offset(bx, by), Offset(bx + cos(b) * r * 0.3f, by + sin(b) * r * 0.3f), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
        }
    }
}

private fun trianglePath(c: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 3) {
        val ang = (-90f + 120f * k) * (PI.toFloat() / 180f)
        val x = c.x + cos(ang) * r
        val y = c.y + sin(ang) * r
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun heartPath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y + r * 0.9f)
    cubicTo(c.x - r * 1.3f, c.y + r * 0.1f, c.x - r * 0.9f, c.y - r * 1.0f, c.x, c.y - r * 0.35f)
    cubicTo(c.x + r * 0.9f, c.y - r * 1.0f, c.x + r * 1.3f, c.y + r * 0.1f, c.x, c.y + r * 0.9f)
    close()
}

/** 음표 — 머리(기운 타원) + 기둥(+ 깃발). [beamed] 면 깃발 둘. */
private fun DrawScope.note(c: Offset, r: Float, color: Color, beamed: Boolean) {
    rotate(-20f, c) {
        drawOval(color, Offset(c.x - r * 0.62f, c.y - r * 0.42f), Size(r * 1.24f, r * 0.84f))
    }
    val sx = c.x + r * 0.52f
    val top = c.y - r * 2.1f
    drawLine(color, Offset(sx, c.y - r * 0.1f), Offset(sx, top), strokeWidth = max(1f, r * 0.2f))
    drawLine(color, Offset(sx, top), Offset(sx + r * 0.75f, top + r * 0.6f), strokeWidth = max(1f, r * 0.24f), cap = StrokeCap.Round)
    if (beamed) drawLine(color, Offset(sx, top + r * 0.5f), Offset(sx + r * 0.75f, top + r * 1.1f), strokeWidth = max(1f, r * 0.24f), cap = StrokeCap.Round)
}
