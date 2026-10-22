package com.chaminwoo.stary.feature.starlight

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 별빛 공방 지도 별 오라(2026-10-22 리디자인) — **한 장의 스프라이트**를 지도와 미리보기가 똑같이 쓴다.
 * 스프라이트는 별 둘레(가운데 별 자리는 비움)에 그린 정지 그림이고, 움직임은 그리는 쪽이 회전([AuraMotion.spinDegPerSec])·
 * 흔들림([AuraMotion.wobbleDeg])·밝기 숨쉬기로 준다 — 지도에선 심볼 레이어의 icon-rotate/opacity, 미리보기에선 graphicsLayer.
 * iOS `StarlightAura.swift` 와 같은 패턴·좌표.
 *
 * payload: `pattern`(아래) · `colors`(#RRGGBB 1~3) · `speed`(한 주기 초) · `count` · `tilt`(saturn, 도)
 *  - halo / pulse : 스프라이트 없음(빛·퍼지는 고리는 원 레이어로 — 지도 [com.chaminwoo.stary.feature.map.screen] sl-aura-glow/pulse)
 *  - orbit     : 옅은 궤도 + count 개 위성(1 = 달, 2 = 쌍둥이 반짝이)
 *  - fireflies : count 개 반딧불(반지름·각도를 흩뜨린 점)
 *  - spiral    : 별가루 점으로 그린 나선 팔 둘
 *  - saturn    : 기울인 겹고리(바깥 넓은 띠 · 카시니 틈 · 안쪽 띠 · 가장 바깥 실고리) + 고리 위 작은 위성 — 회전 대신 살짝 흔들린다
 *  - comet     : 둘레를 도는 혜성(밝은 머리 + 가늘어지며 사라지는 꼬리)
 * 모르는 pattern 은 null(빛만) — 새 패턴을 DB 에 넣어도 이전 버전은 빛으로만 보인다.
 */
object StarlightAura {
    /** 스프라이트 변(px) — 지도 별 비트맵(160px)의 2.4배. 4의 배수(GL 행 정렬). */
    const val SPRITE_PX = 384

    /** 스프라이트를 그리는 패턴. */
    val SPRITE_PATTERNS = setOf("orbit", "fireflies", "spiral", "saturn", "comet")

    data class Motion(val spinDegPerSec: Float, val wobbleDeg: Float, val flicker: Boolean)

    /** 패턴별 움직임 — speed(초) = 한 바퀴. saturn 은 돌지 않고 ±6° 흔들린다. */
    fun motion(payload: Map<String, Any?>): Motion {
        val speed = (payload["speed"] as? Number)?.toFloat()?.coerceIn(2f, 120f) ?: 10f
        return when (payload["pattern"]) {
            "saturn" -> Motion(0f, 6f, false)
            "fireflies" -> Motion(360f / speed, 0f, true)
            "orbit", "spiral", "comet" -> Motion(360f / speed, 0f, false)
            else -> Motion(0f, 0f, false)
        }
    }

    private fun argb(payload: Map<String, Any?>): IntArray {
        val cs = (payload["colors"] as? List<*>).orEmpty().mapNotNull { v ->
            (v as? String)?.trim()?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        }
        return (cs.ifEmpty { listOf(0xFFBFD8FF.toInt()) }).toIntArray()
    }

    private fun alpha(c: Int, a: Float) = ColorUtils.setAlphaComponent(c, (a.coerceIn(0f, 1f) * 255).toInt())

    /** 스프라이트 비트맵(가운데 별 자리는 비어 있다). 스프라이트가 없는 패턴이면 null. */
    fun sprite(payload: Map<String, Any?>, side: Int = SPRITE_PX): Bitmap? {
        val pattern = payload["pattern"] as? String ?: return null
        if (pattern !in SPRITE_PATTERNS) return null
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), pattern, payload, side.toFloat())
        return out
    }

    private fun draw(c: Canvas, pattern: String, payload: Map<String, Any?>, s: Float) {
        val cs = argb(payload)
        val cx = s / 2f
        val cy = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun col(i: Int) = cs[i % cs.size]
        fun glowDot(x: Float, y: Float, core: Float, glow: Float, color: Int, coreColor: Int = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.55f)) {
            p.shader = RadialGradient(x, y, glow, intArrayOf(alpha(color, 0.75f), alpha(color, 0.25f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, glow, p)
            p.shader = null
            p.color = coreColor
            c.drawCircle(x, y, core, p)
        }
        fun sparkle(x: Float, y: Float, r: Float, color: Int) {
            val path = Path().apply {
                moveTo(x, y - r)
                quadTo(x + r * 0.14f, y - r * 0.14f, x + r, y)
                quadTo(x + r * 0.14f, y + r * 0.14f, x, y + r)
                quadTo(x - r * 0.14f, y + r * 0.14f, x - r, y)
                quadTo(x - r * 0.14f, y - r * 0.14f, x, y - r)
                close()
            }
            p.shader = RadialGradient(x, y, r * 2.2f, intArrayOf(alpha(color, 0.55f), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 2.2f, p)
            p.shader = null
            p.color = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.45f)
            c.drawPath(path, p)
        }
        when (pattern) {
            "orbit" -> {
                val n = ((payload["count"] as? Number)?.toInt() ?: 1).coerceIn(1, 4)
                val r = s * 0.36f
                // 궤도선 — 위성 쪽은 진하고 반대쪽은 옅어지는 쓸림(돌 때 꼬리처럼 보인다).
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.008f
                p.shader = SweepGradient(cx, cy, intArrayOf(alpha(col(n - 1), 0.05f), alpha(col(0), 0.5f), alpha(col(n - 1), 0.05f)), null)
                c.drawCircle(cx, cy, r, p)
                p.shader = null
                p.style = Paint.Style.FILL
                for (i in 0 until n) {
                    val a = (i * 360f / n) * PI.toFloat() / 180f
                    val x = cx + cos(a) * r
                    val y = cy + sin(a) * r
                    if (n == 1) glowDot(x, y, s * 0.042f, s * 0.11f, col(1), col(0)) // 달: 흰 핵 + 푸른 달무리
                    else sparkle(x, y, s * 0.045f, col(i))
                }
            }
            "fireflies" -> {
                val n = ((payload["count"] as? Number)?.toInt() ?: 5).coerceIn(2, 9)
                for (i in 0 until n) {
                    // 각도·반지름을 결정적으로 흩뜨린다(같은 아이템 = 항상 같은 배치).
                    val a = (i * 360f / n + (if (i % 2 == 0) 14f else -9f)) * PI.toFloat() / 180f
                    val r = s * (0.27f + 0.13f * ((i * 37) % 10) / 10f)
                    glowDot(cx + cos(a) * r, cy + sin(a) * r, s * 0.014f, s * 0.06f, col(i))
                }
            }
            "spiral" -> {
                for (arm in 0 until 2) {
                    val steps = 16
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val a = (arm * 180f + t * 290f) * PI.toFloat() / 180f
                        val r = s * (0.17f + 0.28f * t)
                        val color = ColorUtils.blendARGB(col(0), col(1), t)
                        p.color = alpha(color, 0.95f - 0.6f * t)
                        p.maskFilter = BlurMaskFilter(s * 0.006f, BlurMaskFilter.Blur.NORMAL)
                        c.drawCircle(cx + cos(a) * r, cy + sin(a) * r, s * (0.02f - 0.011f * t), p)
                        p.maskFilter = null
                    }
                }
            }
            "saturn" -> {
                val tilt = (payload["tilt"] as? Number)?.toFloat() ?: -20f
                c.save()
                c.rotate(tilt, cx, cy)
                p.style = Paint.Style.STROKE
                fun band(rx: Float, ry: Float, w: Float, a: Int, b: Int) {
                    p.strokeWidth = w
                    p.shader = android.graphics.LinearGradient(cx - rx, cy, cx + rx, cy, intArrayOf(a, b, a), null, Shader.TileMode.CLAMP)
                    c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
                }
                // 바깥 → 안: 가는 실고리, 넓은 A 고리, (카시니 틈), B 고리
                band(s * 0.47f, s * 0.135f, s * 0.008f, alpha(col(2), 0.35f), alpha(col(0), 0.6f))
                p.maskFilter = BlurMaskFilter(s * 0.01f, BlurMaskFilter.Blur.NORMAL)
                band(s * 0.42f, s * 0.12f, s * 0.05f, alpha(col(1), 0.35f), alpha(col(0), 0.55f))
                p.maskFilter = null
                band(s * 0.42f, s * 0.12f, s * 0.034f, alpha(col(1), 0.85f), alpha(col(0), 0.95f))
                band(s * 0.345f, s * 0.098f, s * 0.022f, alpha(col(2), 0.7f), alpha(col(1), 0.85f))
                p.shader = null
                p.style = Paint.Style.FILL
                // 고리 위의 작은 위성(오른쪽 앞)
                val ma = 28f * PI.toFloat() / 180f
                glowDot(cx + cos(ma) * s * 0.42f, cy + sin(ma) * s * 0.12f, s * 0.018f, s * 0.05f, col(2), col(0))
                c.restore()
            }
            "comet" -> {
                val r = s * 0.37f
                val head = 0f
                // 꼬리 — 머리 뒤로 160°, 조각마다 굵기·밝기를 줄인다(뒤로 갈수록 가늘고 흐릿하게).
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                val seg = 48
                val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                // 두 번 그린다: 번진 빛(블러) → 선명한 꼬리.
                for (pass in 0..1) {
                    p.maskFilter = if (pass == 0) BlurMaskFilter(s * 0.025f, BlurMaskFilter.Blur.NORMAL) else null
                    for (i in 0 until seg) {
                        val t0 = i / seg.toFloat()
                        val a0 = head - t0 * 170f
                        val a1 = head - (t0 + 1f / seg) * 170f
                        val color = if (t0 < 0.5f) ColorUtils.blendARGB(col(0), col(1), t0 * 2) else ColorUtils.blendARGB(col(1), col(2), (t0 - 0.5f) * 2)
                        val fade = (1f - t0).let { it * it }
                        p.color = alpha(color, fade * (if (pass == 0) 0.7f else 0.95f))
                        p.strokeWidth = s * ((if (pass == 0) 0.075f else 0.05f) * (1f - t0) + 0.004f)
                        c.drawArc(oval, a1, a0 - a1 + 0.6f, false, p)
                    }
                }
                p.maskFilter = null
                p.style = Paint.Style.FILL
                // 꼬리 곁에 흩날리는 부스러기 셋
                for ((k, deg) in listOf(-38f, -74f, -112f).withIndex()) {
                    val a = deg * PI.toFloat() / 180f
                    val rr = r + s * (if (k % 2 == 0) 0.035f else -0.03f)
                    glowDot(cx + cos(a) * rr, cy + sin(a) * rr, s * 0.007f, s * 0.026f, col(1))
                }
                val hx = cx + r
                glowDot(hx, cy, s * 0.032f, s * 0.12f, col(1), 0xFFFFFFFF.toInt())
            }
        }
    }
}
