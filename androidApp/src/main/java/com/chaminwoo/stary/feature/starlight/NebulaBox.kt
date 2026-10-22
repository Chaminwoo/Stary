package com.chaminwoo.stary.feature.starlight

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 성운 상자(2026-10-22 리디자인) — **크리스탈 정육면체 보석함**.
 * 아이소메트릭 육면체: 윗면(밝은 라일락 결정) · 왼면(보랏빛 결정) · 오른면(짙은 남보라 결정), 금빛 모서리와 뚜껑 띠,
 * 앞 모서리의 금빛 별 자물쇠, 몸통 속에서 도는 성운. 면은 별과 같은 크리스탈 파편([StarStyle.drawCrystalFacets]).
 *
 * 정지된 부분(몸통·뚜껑)은 크기별로 한 번 구워 두고([Bake]), 움직이는 것(빛·금·뚜껑 들림·반짝이)만 매 프레임 그린다.
 * iOS `NebulaBox.swift` 와 같은 좌표(0~1).
 */
private object Geo {
    val T = Offset(0.5f, 0.17f)
    val L = Offset(0.17f, 0.36f)
    val R = Offset(0.83f, 0.36f)
    val C = Offset(0.5f, 0.55f)
    val BL = Offset(0.17f, 0.72f)
    val BR = Offset(0.83f, 0.72f)
    val B = Offset(0.5f, 0.91f)
    const val LID_H = 0.065f
    val Ld = Offset(L.x, L.y + LID_H)
    val Cd = Offset(C.x, C.y + LID_H)
    val Rd = Offset(R.x, R.y + LID_H)
    /** 뚜껑 회전축(윗면 가운데). */
    val LID_PIVOT = Offset(0.5f, 0.38f)

    /** 금 — 두드릴 때마다 하나씩(오른면 · 왼면 · 윗면). */
    val CRACKS = listOf(
        listOf(Offset(0.64f, 0.56f), Offset(0.69f, 0.61f), Offset(0.66f, 0.66f), Offset(0.71f, 0.71f), Offset(0.69f, 0.77f)),
        listOf(Offset(0.31f, 0.55f), Offset(0.27f, 0.60f), Offset(0.33f, 0.65f), Offset(0.29f, 0.71f), Offset(0.33f, 0.76f)),
        listOf(Offset(0.34f, 0.35f), Offset(0.42f, 0.33f), Offset(0.47f, 0.38f), Offset(0.56f, 0.35f), Offset(0.62f, 0.39f)),
    )
    /** 금의 곁가지. */
    val CRACK_BRANCHES = listOf(
        listOf(Offset(0.66f, 0.66f), Offset(0.61f, 0.69f)),
        listOf(Offset(0.33f, 0.65f), Offset(0.39f, 0.67f)),
        listOf(Offset(0.47f, 0.38f), Offset(0.49f, 0.44f)),
    )
}

private class BoxLayers(val body: ImageBitmap, val lid: ImageBitmap)

private val bakeCache = LruCache<Int, BoxLayers>(4)

private object Bake {
    private const val GOLD_HI = 0xFFFFE9B0.toInt()
    private const val GOLD = 0xFFFFC970.toInt()
    private const val GOLD_LO = 0xFFC98A3A.toInt()

    fun layers(px: Int): BoxLayers {
        bakeCache.get(px)?.let { return it }
        return BoxLayers(body(px).asImageBitmap(), lid(px).asImageBitmap()).also { bakeCache.put(px, it) }
    }

    private fun poly(s: Float, p: List<Offset>) = Path().apply {
        moveTo(p[0].x * s, p[0].y * s)
        for (i in 1 until p.size) lineTo(p[i].x * s, p[i].y * s)
        close()
    }

    /** 면 하나를 결정으로 — 메시 중심이 면 가운데 오도록 면의 외접 정사각에 그린다. */
    private fun face(c: android.graphics.Canvas, s: Float, seed: Int, colors: List<Int>, p: List<Offset>) {
        val minX = p.minOf { it.x }; val maxX = p.maxOf { it.x }
        val minY = p.minOf { it.y }; val maxY = p.maxOf { it.y }
        val side = maxOf(maxX - minX, maxY - minY)
        val left = (minX + maxX) / 2 - side / 2
        val top = (minY + maxY) / 2 - side / 2
        StarStyle.drawCrystalFacets(c, poly(s, p), seed, colors, left * s, top * s, side * s)
    }

    private fun goldPaint(s: Float, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = s * w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(0f, 0f, s, s, intArrayOf(GOLD_HI, GOLD, GOLD_LO), null, Shader.TileMode.CLAMP)
    }

    private fun line(c: android.graphics.Canvas, s: Float, p: Paint, a: Offset, b: Offset) = c.drawLine(a.x * s, a.y * s, b.x * s, b.y * s, p)

    private fun stud(c: android.graphics.Canvas, s: Float, v: Offset, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(v.x * s - r * s * 0.3f, v.y * s - r * s * 0.3f, r * s * 1.2f, intArrayOf(0xFFFFFBEA.toInt(), GOLD, GOLD_LO), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(v.x * s, v.y * s, r * s, p)
    }

    private fun body(px: Int): Bitmap {
        val s = px.toFloat()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        with(Geo) {
            face(c, s, 41, listOf(0xFF3E27B0.toInt(), 0xFF6B45E8.toInt(), 0xFFA778FF.toInt()), listOf(L, C, B, BL))
            face(c, s, 57, listOf(0xFF221A6E.toInt(), 0xFF4630B8.toInt(), 0xFF6E4DE0.toInt()), listOf(C, R, BR, B))
            // 몸통 속 성운 — 분홍·하늘빛 소용돌이(번진 선) + 별가루
            val save = c.save()
            val bodyClip = Path().apply { addPath(poly(s, listOf(L, C, B, BL))); addPath(poly(s, listOf(C, R, BR, B))) }
            c.clipPath(bodyClip)
            val neb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                maskFilter = BlurMaskFilter(s * 0.025f, BlurMaskFilter.Blur.NORMAL)
            }
            for (k in 0 until 2) {
                val arm = Path()
                for (i in 0..40) {
                    val t = i / 40f
                    val a = k * PI.toFloat() + t * 2.8f
                    val r = 0.04f + 0.2f * t
                    val x = (0.5f + cos(a) * r * 1.2f) * s
                    val y = (0.71f + sin(a) * r * 0.6f) * s
                    if (i == 0) arm.moveTo(x, y) else arm.lineTo(x, y)
                }
                neb.color = if (k == 0) 0x88FFB8F0.toInt() else 0x669FD8FF.toInt()
                neb.strokeWidth = s * 0.03f
                c.drawPath(arm, neb)
            }
            // 가운데 은은한 핵
            c.drawCircle(0.5f * s, 0.71f * s, 0.16f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(0.5f * s, 0.71f * s, 0.16f * s, intArrayOf(0x66FFE6FF, 0x00FFE6FF), null, Shader.TileMode.CLAMP)
            })
            val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
            for (i in 0 until 14) {
                val x = 0.2f + 0.6f * ((i * 37 % 23) / 23f)
                val y = 0.48f + 0.4f * ((i * 53 % 19) / 19f)
                c.drawCircle(x * s, y * s, s * (0.003f + 0.003f * (i % 3)), dust)
            }
            c.restoreToCount(save)
            // 세로 모서리의 빛 받는 테(유리 두께)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = s * 0.012f
                shader = LinearGradient(0f, Cd.y * s, 0f, B.y * s, intArrayOf(0x88FFFFFF.toInt(), 0x11FFFFFF), null, Shader.TileMode.CLAMP)
            }
            line(c, s, rim, Offset(Cd.x + 0.012f, Cd.y + 0.01f), Offset(B.x + 0.012f, B.y - 0.02f))
            // 금빛 모서리
            val g = goldPaint(s, 0.013f)
            line(c, s, g, Ld, BL); line(c, s, g, BL, B); line(c, s, g, B, BR); line(c, s, g, BR, Rd); line(c, s, g, Cd, B)
            stud(c, s, BL, 0.014f); stud(c, s, BR, 0.014f); stud(c, s, B, 0.016f)
            // 앞 모서리 금빛 별 자물쇠(크리스탈) + 받침 원판
            val lock = Offset(0.5f, 0.705f)
            c.drawCircle(lock.x * s, lock.y * s, 0.068f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(lock.x * s, lock.y * s, 0.068f * s, intArrayOf(0xCC1A1240.toInt(), 0x991A1240.toInt(), 0x00000000), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            })
            c.drawCircle(lock.x * s, lock.y * s, 0.06f * s, goldPaint(s, 0.008f))
            val star = 0.1f
            StarStyle.drawCrystalFill(c, 1, listOf(0xFFFFF4C8.toInt(), GOLD, 0xFFE08A2A.toInt()), (lock.x - star / 2) * s, (lock.y - star / 2) * s, star * s)
        }
        return bmp
    }

    private fun lid(px: Int): Bitmap {
        val s = px.toFloat()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        with(Geo) {
            // 뚜껑 띠 — 왼쪽(밝은 금) · 오른쪽(그늘진 금)
            c.drawPath(poly(s, listOf(L, C, Cd, Ld)), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, L.y * s, 0f, Ld.y * s, intArrayOf(0xFFFFE7A6.toInt(), 0xFFF2B65E.toInt()), null, Shader.TileMode.CLAMP)
            })
            c.drawPath(poly(s, listOf(C, R, Rd, Cd)), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, R.y * s, 0f, Rd.y * s, intArrayOf(0xFFDDA052.toInt(), 0xFFAA6A2A.toInt()), null, Shader.TileMode.CLAMP)
            })
            // 띠 위 홈(가는 어두운 선) — 금속 띠의 두께감
            val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.004f; color = 0x55704010 }
            val mid = LID_H * 0.55f
            line(c, s, groove, Offset(L.x, L.y + mid), Offset(C.x, C.y + mid)); line(c, s, groove, Offset(C.x, C.y + mid), Offset(R.x, R.y + mid))
            // 윗면 결정
            face(c, s, 23, listOf(0xFF9C78FF.toInt(), 0xFFCDB0FF.toInt(), 0xFFF6C4EE.toInt()), listOf(T, R, C, L))
            // 윗면 가운데 네 꼭지 별 문장(빛 번짐 + 크림색 별)
            val e = Offset(0.5f, 0.36f)
            c.drawCircle(e.x * s, e.y * s, 0.1f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(e.x * s, e.y * s, 0.1f * s, intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            })
            val emb = Path()
            val r1 = 0.09f; val r2 = 0.018f
            for (i in 0 until 8) {
                val r = if (i % 2 == 0) r1 else r2
                val a = (-90f + i * 45f) * PI.toFloat() / 180f
                val x = (e.x + r * cos(a)) * s
                val y = (e.y + r * sin(a) * 0.56f) * s
                if (i == 0) emb.moveTo(x, y) else emb.lineTo(x, y)
            }
            emb.close()
            c.drawPath(emb, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFBEE.toInt() })
            // 금빛 모서리 + 꼭짓점 징
            val g = goldPaint(s, 0.013f)
            line(c, s, g, T, R); line(c, s, g, R, C); line(c, s, g, C, L); line(c, s, g, L, T)
            line(c, s, g, Ld, Cd); line(c, s, g, Cd, Rd); line(c, s, g, L, Ld); line(c, s, g, C, Cd); line(c, s, g, R, Rd)
            stud(c, s, T, 0.014f); stud(c, s, L, 0.014f); stud(c, s, R, 0.014f); stud(c, s, C, 0.016f)
        }
        return bmp
    }
}

/**
 * 성운 상자 그림.
 * @param glow 0~1 — 이음새·금 사이로 새는 빛의 세기(두드릴수록 커진다).
 * @param glowColor 새는 빛 색(두드릴 때 등급 색으로 바뀐다).
 * @param cracks 0~3 — 보이는 금 수.
 * @param lidOpen 0~1 — 뚜껑이 들려 날아가는 정도.
 * @param animated 둥실 떠 있기·반짝이(목록의 작은 아이콘은 false — 매 프레임 다시 그리지 않게).
 */
@Composable
fun NebulaBoxArt(
    modifier: Modifier = Modifier,
    glow: Float = 0f,
    glowColor: Color = Color(0xFFC9A2FF),
    cracks: Int = 0,
    lidOpen: Float = 0f,
    animated: Boolean = true,
) {
    if (animated) {
        val t by rememberInfiniteTransition(label = "nebula_box").animateFloat(
            0f, 1f, infiniteRepeatable(tween(4_800, easing = LinearEasing)), label = "nebula_box_t",
        )
        Canvas(modifier) { drawNebulaBox(t, glow, glowColor, cracks, lidOpen, decor = true) }
    } else {
        Canvas(modifier) { drawNebulaBox(0f, glow, glowColor, cracks, lidOpen, decor = false) }
    }
}

private fun DrawScope.drawNebulaBox(t: Float, glow: Float, glowColor: Color, cracks: Int, lidOpen: Float, decor: Boolean) {
    val s = size.minDimension
    val px = s.roundToInt().coerceAtLeast(8)
    val ox = (size.width - s) / 2
    val oy = (size.height - s) / 2
    val layers = Bake.layers(px)
    val ph = t * 2 * PI.toFloat()
    val bob = if (decor) sin(ph) * 0.018f * s else 0f
    fun at(p: Offset) = Offset(ox + p.x * s, oy + p.y * s + bob)

    // 뒤 빛
    val back = Offset(ox + 0.5f * s, oy + 0.55f * s)
    if (decor) {
        drawCircle(
            Brush.radialGradient(listOf(glowColor.copy(alpha = 0.30f + 0.45f * glow), Color.Transparent), back, s * 0.6f),
            s * 0.6f, back,
        )
    }
    // 바닥 그림자(떠오르면 작아진다)
    if (decor) {
        val sh = 1f - bob / s * 4f
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(ox + 0.5f * s, oy + 0.95f * s), s * 0.28f * sh),
            topLeft = Offset(ox + s * (0.5f - 0.28f * sh), oy + s * 0.915f), size = Size(s * 0.56f * sh, s * 0.07f),
        )
    }
    // 몸통
    drawImage(layers.body, dstOffset = IntOffset(ox.roundToInt(), (oy + bob).roundToInt()), dstSize = IntSize(px, px))

    // 금 — 바깥 번짐 → 색 → 흰 심
    val lit = glowColor
    for (i in 0 until cracks.coerceIn(0, 3)) {
        if (i == 2 && lidOpen > 0f) continue // 윗면 금은 뚜껑과 함께 날아간다(아래에서 뚜껑에 그린다)
        drawCrack(Geo.CRACKS[i], Geo.CRACK_BRANCHES[i], ::at, s, lit, glow)
    }

    // 이음새로 새는 빛
    val seamA = (0.25f + 0.75f * glow) * (if (decor) 0.85f + 0.15f * sin(ph * 2) else 1f)
    val seam = androidx.compose.ui.graphics.Path().apply {
        moveTo(at(Geo.Ld).x, at(Geo.Ld).y); lineTo(at(Geo.Cd).x, at(Geo.Cd).y); lineTo(at(Geo.Rd).x, at(Geo.Rd).y)
    }
    for ((w, a) in listOf(0.06f to 0.12f, 0.03f to 0.3f, 0.012f to 0.9f)) {
        drawPath(seam, (if (a > 0.5f) Color.White else lit).copy(alpha = (a * seamA).coerceIn(0f, 1f)), style = Stroke(s * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 열린 입구 — 뚜껑이 들리면 윗면 자리에서 빛이 쏟아진다
    if (lidOpen > 0f) {
        val mouth = androidx.compose.ui.graphics.Path().apply {
            val l = at(Geo.Ld); val tt = at(Offset(Geo.T.x, Geo.T.y + Geo.LID_H)); val r = at(Geo.Rd); val c = at(Geo.Cd)
            moveTo(tt.x, tt.y); lineTo(r.x, r.y); lineTo(c.x, c.y); lineTo(l.x, l.y); close()
        }
        val mc = at(Offset(0.5f, 0.42f))
        drawPath(mouth, Brush.radialGradient(listOf(Color.White, lit, lit.copy(alpha = 0.6f)), mc, s * 0.4f), alpha = lidOpen.coerceIn(0f, 1f))
    }

    // 뚜껑(들리며 기울고 사라진다)
    val lift = lidOpen.coerceIn(0f, 1f)
    val lidAlpha = (1f - ((lift - 0.55f) / 0.45f).coerceIn(0f, 1f))
    if (lidAlpha > 0f) {
        val pivot = at(Geo.LID_PIVOT)
        translate(s * 0.08f * lift, -s * 0.62f * lift) {
            rotate(-24f * lift, pivot) {
                drawImage(layers.lid, dstOffset = IntOffset(ox.roundToInt(), (oy + bob).roundToInt()), dstSize = IntSize(px, px), alpha = lidAlpha)
                if (cracks >= 3) drawCrack(Geo.CRACKS[2], Geo.CRACK_BRANCHES[2], ::at, s, lit, glow, lidAlpha)
            }
        }
    }

    // 둘레 반짝이
    if (decor) {
        val spots = listOf(Offset(0.12f, 0.2f) to 0.032f, Offset(0.88f, 0.24f) to 0.022f, Offset(0.92f, 0.6f) to 0.018f, Offset(0.08f, 0.56f) to 0.016f)
        spots.forEachIndexed { i, (p, r) ->
            val tw = 0.5f + 0.5f * sin(ph * 2 + i * 1.7f)
            drawSparkle(Offset(ox + p.x * s, oy + p.y * s), s * r * (0.7f + 0.3f * tw), Color.White.copy(alpha = 0.35f + 0.6f * tw))
        }
    }
}

private fun DrawScope.drawCrack(
    pts: List<Offset>,
    branch: List<Offset>,
    at: (Offset) -> Offset,
    s: Float,
    color: Color,
    glow: Float,
    alpha: Float = 1f,
) {
    fun path(list: List<Offset>) = androidx.compose.ui.graphics.Path().apply {
        list.forEachIndexed { i, p -> val q = at(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
    }
    val main = path(pts)
    val br = path(branch)
    val k = (0.6f + 0.4f * glow) * alpha
    for ((w, a, white) in listOf(Triple(0.05f, 0.18f, false), Triple(0.022f, 0.55f, false), Triple(0.008f, 1f, true))) {
        val col = (if (white) Color.White else color).copy(alpha = (a * k).coerceIn(0f, 1f))
        drawPath(main, col, style = Stroke(s * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(br, col, style = Stroke(s * w * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
