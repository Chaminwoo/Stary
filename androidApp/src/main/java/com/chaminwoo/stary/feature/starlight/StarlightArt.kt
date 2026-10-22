package com.chaminwoo.stary.feature.starlight

import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 별빛 공방 아이템 그림(2026-10-22) — 공방·뽑기 연출·수집품·프로필이 같이 쓴다. iOS `StarlightArt.swift` 와 같은 해석.
 *
 * 프레임·오라·배지·칭호는 **DB payload 의 매개변수로 그린다**(앱에 그림을 박지 않는다):
 *  - frame : `pattern`(ring · dash · orbit · petals · constellation · aurora) · `colors` · `width`(0~1, 기본 0.06) · `count` · `speed`(한 바퀴 초, 0=정지) · `glow`
 *  - aura  : [StarlightAura] 참고(halo · pulse 는 빛, 나머지는 스프라이트)
 *  - badge : `path`(0~1 SVG, StarCatalog 와 같은 규칙) · `colors` — 별과 같은 **크리스탈 파편**으로 채운다
 *  - title : `colors`(1개 = 단색, 2~3개 = 그라데이션) · `shimmer`(빛이 흐름)
 * ⚠️ 모르는 pattern 은 가장 기본형(ring / halo)으로 — 새 버전에 추가된 패턴도 이전 앱에서 깨지지 않게(별 모양 정책과 같다).
 */

/** DB 아이템 문구(name/desc 의 ko·en·ja)를 고를 언어 — 인앱 언어를 따르는 리소스로 읽는다(API 32 이하 포함). */
@Composable
fun starlightLang(): String = stringResource(R.string.starlight_lang)

/** 등급 색 — 일반 은빛 · 희귀 푸른빛 · 영웅 보랏빛 · 전설 금빛. */
fun rarityColor(rarity: String): Color = when (rarity) {
    "rare" -> Color(0xFF7CB8FF)
    "epic" -> Color(0xFFD59CFF)
    "legendary" -> Color(0xFFFFD27A)
    else -> Color(0xFFC3CAD6)
}

internal fun Map<String, Any?>.colors(key: String = "colors"): List<Color> =
    (this[key] as? List<*>)?.mapNotNull { (it as? String)?.let(::hexColor) } ?: emptyList()

internal fun Map<String, Any?>.num(key: String, def: Float): Float = (this[key] as? Number)?.toFloat() ?: def
internal fun Map<String, Any?>.int(key: String, def: Int): Int = (this[key] as? Number)?.toInt() ?: def
internal fun Map<String, Any?>.str(key: String): String? = this[key] as? String

internal fun hexColor(s: String): Color? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

private val svgCache = ConcurrentHashMap<String, android.graphics.Path>()

/** 0~1 단위 SVG 경로 → android Path(캐시, 복사본을 돌려준다). 읽지 못하면 null. */
internal fun unitSvgPathNative(svg: String?): android.graphics.Path? {
    if (svg.isNullOrBlank()) return null
    val base = svgCache[svg] ?: runCatching { PathParser.createPathFromPathData(svg) }.getOrNull()?.also { svgCache[svg] = it } ?: return null
    return android.graphics.Path(base)
}

/** 0~1 단위 SVG 경로 → Compose Path. 읽지 못하면 null. */
internal fun unitSvgPath(svg: String?): Path? = unitSvgPathNative(svg)?.asComposePath()

// ── 크리스탈 글리프(배지·망원경·열쇠·별가루) ─────────────────────────────

/** 구운 크리스탈 그림 캐시(키 = 경로|색|px) — 목록에서 칸마다 파편을 다시 계산하지 않게. */
private val crystalCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/**
 * [unit] 실루엣(0~1)을 별과 같은 크리스탈 파편([StarStyle.drawCrystalFacets])으로 채운 비트맵.
 * 가장자리에 옅은 흰 테(빛을 받는 모서리)를 둘러 결정처럼 보이게 한다.
 */
internal fun bakeCrystal(key: String, px: Int, seed: Int, colors: List<Int>, unit: () -> android.graphics.Path?): ImageBitmap? {
    if (px <= 0) return null
    val k = "$key|${colors.joinToString(",")}|$px"
    crystalCache.get(k)?.let { return it }
    val path = unit() ?: return null
    path.transform(android.graphics.Matrix().apply { setScale(px.toFloat(), px.toFloat()) })
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    StarStyle.drawCrystalFacets(c, path, seed, colors, 0f, 0f, px.toFloat())
    c.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = (px * 0.022f).coerceAtLeast(0.8f)
        shader = android.graphics.LinearGradient(
            0f, 0f, px.toFloat(), px.toFloat(),
            intArrayOf(0x99FFFFFF.toInt(), 0x33FFFFFF, 0x55FFFFFF), null, android.graphics.Shader.TileMode.CLAMP,
        )
    })
    return bmp.asImageBitmap().also { crystalCache.put(k, it) }
}

/** 크리스탈 그림을 정사각 가운데에 그린다(+ 선택적 뒤 빛). */
@Composable
private fun CrystalCanvas(
    key: String,
    seed: Int,
    colors: List<Color>,
    modifier: Modifier,
    glow: Boolean,
    unit: () -> android.graphics.Path?,
) {
    val argb = remember(colors) { colors.ifEmpty { listOf(Color.White) }.map { it.toArgb() } }
    Canvas(modifier) {
        val s = size.minDimension
        val px = s.roundToInt()
        val img = bakeCrystal(key, px, seed, argb, unit) ?: return@Canvas
        val left = ((size.width - s) / 2).roundToInt()
        val top = ((size.height - s) / 2).roundToInt()
        if (glow) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(Color(argb.last()).copy(alpha = 0.32f), Color.Transparent), c, s * 0.55f), s * 0.55f, c)
        }
        drawImage(img, dstOffset = IntOffset(left, top), dstSize = IntSize(px, px))
    }
}

/** 0~1 SVG 글리프를 크리스탈로 채운다 — 배지·망원경 아이콘. 색 1개 = 단색 결정, 2~3개 = 대각 그라데이션 결정. */
@Composable
fun SvgGlyph(svg: String?, colors: List<Color>, modifier: Modifier = Modifier, glow: Boolean = false) {
    if (svg.isNullOrBlank()) return
    CrystalCanvas("svg:${svg.hashCode()}", seed = (svg.hashCode() and 0x7FFF) % 97 + 3, colors, modifier, glow) { unitSvgPathNative(svg) }
}

private val KeyGold = listOf(Color(0xFFFFF0B8), Color(0xFFFFC75A), Color(0xFFE5892E))

/** 크리스탈 열쇠 — 별 모양 15번(열쇠) 실루엣을 금빛 결정으로. */
@Composable
fun CrystalKeyIcon(modifier: Modifier = Modifier, glow: Boolean = false) {
    CrystalCanvas("key", seed = 15, KeyGold, modifier, glow) {
        android.graphics.Path(StarStyle.starPath(15, 1f))
    }
}

/** 별가루 실루엣(크기가 다른 네 꼭지 반짝이 셋), 0~1. */
private fun stardustPath(): android.graphics.Path = android.graphics.Path().apply {
    fun sparkle(cx: Float, cy: Float, r: Float) {
        val k = 0.16f
        moveTo(cx, cy - r)
        quadTo(cx + r * k, cy - r * k, cx + r, cy)
        quadTo(cx + r * k, cy + r * k, cx, cy + r)
        quadTo(cx - r * k, cy + r * k, cx - r, cy)
        quadTo(cx - r * k, cy - r * k, cx, cy - r)
        close()
    }
    sparkle(0.40f, 0.44f, 0.38f)
    sparkle(0.80f, 0.20f, 0.18f)
    sparkle(0.80f, 0.80f, 0.15f)
}

/** 별가루 아이콘 — 밝은 색이면 크리스탈 결정, 어두운 색(금빛 버튼 위 글자색 등)이면 단색. */
@Composable
fun StardustIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFFFE6A8)) {
    if (tint.luminance() < 0.3f || tint.alpha < 0.95f) {
        Canvas(modifier) {
            val s = size.minDimension
            val p = stardustPath().apply { transform(android.graphics.Matrix().apply { setScale(s, s) }) }.asComposePath()
            drawPath(p, tint)
        }
        return
    }
    val colors = remember(tint) {
        val a = tint.toArgb()
        listOf(Color(ColorUtils.blendARGB(a, android.graphics.Color.WHITE, 0.55f)), tint, Color(ColorUtils.blendARGB(a, 0xFFB86A1E.toInt(), 0.45f)))
    }
    CrystalCanvas("dust", seed = 33, colors, modifier, glow = false) { stardustPath() }
}

// ── 프로필 프레임 ─────────────────────────────────────────────────────────

/**
 * 프로필 프레임 — [content](사진)를 안쪽 [FRAME_INSET] 만큼 들여 그리고, 그 둘레에 payload 대로 테를 그린다.
 * payload 가 null 이면 테 없이 사진만.
 */
@Composable
fun FramedAvatar(
    payload: Map<String, Any?>?,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().padding(if (payload != null) size * FRAME_INSET else 0.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (payload != null) FrameArt(payload, Modifier.fillMaxSize())
    }
}

const val FRAME_INSET = 0.13f

/** [uid] 의 장착 상태 — 나는 [StarlightStore.myEquips], 남은 그 사람 문서를 구독(화면을 떠나면 해제). */
@Composable
fun rememberEquips(uid: String): StarlightStore.Equips {
    val mine = uid == com.chaminwoo.stary.feature.auth.GoogleAuthHelper.currentUserId
    var theirs by remember(uid) { mutableStateOf(StarlightStore.Equips()) }
    DisposableEffect(uid, mine) {
        val reg = if (mine || uid.isBlank()) null else StarlightStore.watchEquips(uid) { theirs = it }
        onDispose { reg?.remove() }
    }
    return if (mine) StarlightStore.myEquips else theirs
}

/** 장착 프레임 — 지름 [photo] 인 사진 둘레에 겹쳐 그린다(사진 크기는 그대로, 테는 바깥 띠에). 없으면 아무것도. */
@Composable
fun EquippedFrame(equips: StarlightStore.Equips, photo: Dp) {
    val item = StarlightStore.item(equips.frame) ?: return
    FrameArt(item.payload, Modifier.requiredSize(photo / (1 - 2 * FRAME_INSET)))
}

/** 장착 배지 — 이름 옆 작은 문장. 없으면 아무것도. */
@Composable
fun EquippedBadge(equips: StarlightStore.Equips, size: Dp, modifier: Modifier = Modifier) {
    val item = StarlightStore.item(equips.badge) ?: return
    SvgGlyph(item.payload.str("path"), item.payload.colors(), modifier.size(size), glow = item.rarity != "common")
}

@Composable
fun FrameArt(payload: Map<String, Any?>, modifier: Modifier = Modifier) {
    val pattern = payload.str("pattern") ?: "ring"
    val colors = payload.colors().ifEmpty { listOf(Color(0xFFE9EEF5)) }
    val width = payload.num("width", 0.06f).coerceIn(0.02f, 0.12f)
    val count = payload.int("count", 0)
    val speed = payload.num("speed", 0f)
    val glow = payload["glow"] as? Boolean ?: false
    val transition = rememberInfiniteTransition(label = "frame")
    val spin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(((if (speed > 0) speed else 60f) * 1000).toInt(), easing = LinearEasing)), label = "frame_spin",
    )
    // 반짝임 숨 — 차분하게(예전 2.4초는 영웅 프레임이 "두근거려" 보였다).
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4_800, easing = LinearEasing)), label = "frame_wave")
    val angle = if (speed > 0) spin else 0f
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        val w = s * width
        val r = s / 2 - s * FRAME_INSET / 2 - w * 0.2f
        val ringBrush = Brush.sweepGradient(if (colors.size >= 2) colors + colors.first() else listOf(colors[0], colors[0]), c)
        if (glow && pattern != "aurora") drawCircle(colors.last().copy(alpha = 0.22f), r, c, style = Stroke(w * 2.6f))
        when (pattern) {
            "dash" -> rotate(angle, c) {
                val circ = (2 * PI * r).toFloat()
                val n = count.takeIf { it > 0 } ?: 18
                drawCircle(
                    ringBrush, r, c,
                    style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(circ / n * 0.55f, circ / n * 0.45f))),
                )
            }
            "orbit" -> {
                drawCircle(ringBrush, r, c, style = Stroke(w * 0.45f), alpha = 0.75f)
                val n = count.takeIf { it > 0 } ?: 3
                for (i in 0 until n) {
                    val a = Math.toRadians((angle + i * 360f / n).toDouble())
                    val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                    drawSparkle(p, w * 1.9f, colors[i % colors.size])
                }
            }
            "petals" -> rotate(angle, c) {
                drawCircle(ringBrush, r, c, style = Stroke(w * 0.4f), alpha = 0.6f)
                val n = count.takeIf { it > 0 } ?: 12
                for (i in 0 until n) {
                    rotate(i * 360f / n, c) {
                        val tip = Offset(c.x, c.y - r - w * 1.3f)
                        val base = Offset(c.x, c.y - r + w * 1.1f)
                        val petal = Path().apply {
                            moveTo(base.x, base.y)
                            cubicTo(base.x + w * 1.3f, base.y - w * 0.6f, tip.x + w * 0.9f, tip.y + w * 0.4f, tip.x, tip.y)
                            cubicTo(tip.x - w * 0.9f, tip.y + w * 0.4f, base.x - w * 1.3f, base.y - w * 0.6f, base.x, base.y)
                            close()
                        }
                        drawPath(petal, colors[i % colors.size])
                    }
                }
            }
            "constellation" -> rotate(angle, c) {
                val n = count.takeIf { it > 0 } ?: 9
                // 별마다 반지름을 조금씩 흔들어(결정적) 별자리처럼 보이게.
                val pts = (0 until n).map { i ->
                    val a = Math.toRadians((i * 360.0 / n) + (if (i % 2 == 0) 6 else -4))
                    val rr = r + w * (if (i % 3 == 0) 0.9f else if (i % 3 == 1) -0.6f else 0.2f)
                    Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
                }
                for (i in pts.indices) {
                    drawLine(colors[0].copy(alpha = 0.55f), pts[i], pts[(i + 1) % n], strokeWidth = w * 0.28f)
                }
                pts.forEachIndexed { i, p ->
                    val tw = 0.7f + 0.3f * sin((wave * 2 * PI + i * 1.7).toFloat())
                    drawCircle(colors[i % colors.size].copy(alpha = 0.22f * tw), w * 1.5f, p)
                    drawSparkle(p, w * (if (i % 3 == 0) 1.5f else 1.0f), colors[i % colors.size].copy(alpha = 0.7f + 0.3f * tw))
                }
            }
            "aurora" -> drawAurora(c, r, w, colors, angle, wave)
            else -> rotate(angle, c) { drawCircle(ringBrush, r, c, style = Stroke(w)) } // ring + 모르는 패턴
        }
    }
}

/**
 * 오로라 베일(2026-10-22 리디자인) — 고리 바깥으로 드리운 **빛의 커튼**.
 * 각도마다 높이가 다른 세로 빛줄기(커튼 주름)를 세 겹 겹치고, 그 위를 얇은 빛 고리가 감싼다.
 * 겹마다 다른 속도로 물결쳐 커튼이 일렁이고, 바깥엔 작은 별이 천천히 반짝인다.
 */
private fun DrawScope.drawAurora(c: Offset, r: Float, w: Float, colors: List<Color>, angle: Float, wave: Float) {
    val cs = if (colors.size >= 2) colors else listOf(colors[0], colors[0])
    fun colorAt(f: Float): Color {
        val x = ((f % 1f) + 1f) % 1f * cs.size
        val i = x.toInt() % cs.size
        return androidx.compose.ui.graphics.lerp(cs[i], cs[(i + 1) % cs.size], x - x.toInt())
    }
    val ph = wave * 2 * PI.toFloat()
    val rot = angle * PI.toFloat() / 180f
    // 바탕 안개 — 고리 둘레의 옅은 빛
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent, ((r - w) / (r + w * 3.2f)) to Color.Transparent,
            (r / (r + w * 3.2f)) to cs[0].copy(alpha = 0.22f), 1f to Color.Transparent,
            center = c, radius = r + w * 3.2f,
        ),
        r + w * 3.2f, c,
    )
    // 커튼 주름 — 세 겹(뒤 → 앞), 겹마다 줄기 수·위상·속도가 다르다.
    for (layer in 0 until 3) {
        val n = 54 + layer * 12
        val lw = w * (0.42f - layer * 0.08f)
        for (i in 0 until n) {
            val f = i / n.toFloat()
            val a = f * 2 * PI.toFloat() + rot * (1f + layer * 0.35f)
            // 주름 높이: 느린 큰 물결 × 빠른 잔물결 — 커튼이 접힌 듯 들쭉날쭉
            val big = 0.5f + 0.5f * sin(a * 3f + ph * (1 + layer) + layer * 2.1f)
            val small = 0.6f + 0.4f * sin(a * 11f - ph * 2f + layer)
            val h = w * (0.8f + 2.1f * big * small)
            val inner = r - w * 0.35f
            val start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner)
            val end = Offset(c.x + cos(a) * (inner + h), c.y + sin(a) * (inner + h))
            val col = colorAt(f + layer * 0.17f + angle / 360f)
            val alpha = (0.32f + 0.38f * big) * (1f - layer * 0.18f)
            drawLine(
                Brush.linearGradient(listOf(col.copy(alpha = alpha), col.copy(alpha = alpha * 0.45f), Color.Transparent), start, end),
                start, end, strokeWidth = lw, cap = StrokeCap.Round,
            )
        }
    }
    // 빛 고리 — 가는 두 줄(밝은 안쪽 + 색 바깥)
    drawCircle(Brush.sweepGradient(cs + cs.first(), c), r, c, style = Stroke(w * 0.55f))
    drawCircle(Color.White.copy(alpha = 0.55f), r - w * 0.1f, c, style = Stroke(w * 0.16f))
    // 바깥 작은 별 — 천천히 반짝
    for (i in 0 until 7) {
        val a = (i * 51.4f + 12f) * PI.toFloat() / 180f + rot * 0.5f
        val rr = r + w * (2.6f + (i % 3) * 0.55f)
        val tw = 0.5f + 0.5f * sin(ph + i * 1.9f)
        drawSparkle(Offset(c.x + cos(a) * rr, c.y + sin(a) * rr), w * (0.5f + 0.25f * (i % 2)), Color.White.copy(alpha = 0.35f + 0.55f * tw))
    }
}

/** 네 꼭지 반짝이(프레임·오라 장식). */
internal fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(p, color)
}

// ── 별 오라 미리보기 ──────────────────────────────────────────────────────

/**
 * 지도 별 오라 미리보기 — 별 하나와 그 둘레 빛. 지도([StarlightAura] 스프라이트 + 원 레이어)와 같은 그림·움직임.
 * 스프라이트 변 = 미리보기 변(별은 그 0.42 — 지도 별 160px : 스프라이트 384px 와 같은 비율).
 */
@Composable
fun AuraArt(payload: Map<String, Any?>, modifier: Modifier = Modifier, starType: Int = 1, starColor: Int = 0, itemId: String = "") {
    val pattern = payload.str("pattern") ?: "halo"
    val colors = payload.colors().ifEmpty { listOf(Color(0xFFBFD8FF)) }
    val speed = payload.num("speed", 4f).coerceIn(2f, 120f)
    val sprite = remember(itemId, payload) {
        StarlightAura.sprite(payload)?.asImageBitmap()
    }
    val motion = remember(payload) { StarlightAura.motion(payload) }
    val tr = rememberInfiniteTransition(label = "aura")
    // 한 주기 = speed 초. 회전·흔들림·반짝임 모두 이 위상에서 나온다.
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween((speed * 1000).toInt(), easing = LinearEasing)), label = "aura_phase")
    val breathe by tr.animateFloat(0.8f, 1f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "aura_breathe")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)
            val base = s * 0.30f
            when {
                pattern == "pulse" -> {
                    drawCircle(Brush.radialGradient(listOf(colors[0].copy(alpha = 0.5f * breathe), Color.Transparent), c, base * 1.25f), base * 1.25f, c)
                    // 퍼지는 고리 하나(예전 둘 → 하나, 주기도 느리게 — "두근거림"이 정신없지 않게)
                    val f = ph
                    drawCircle(colors.last().copy(alpha = (1 - f) * 0.6f), base * (0.6f + f * 1.0f), c, style = Stroke(s * 0.016f))
                }
                sprite != null -> {
                    drawCircle(Brush.radialGradient(listOf(colors[0].copy(alpha = 0.28f * breathe), Color.Transparent), c, base * 1.2f), base * 1.2f, c)
                    val deg = motion.spinDegPerSec * speed * ph + motion.wobbleDeg * sin(ph * 2 * PI.toFloat())
                    val alpha = if (motion.flicker) 0.75f + 0.25f * sin(ph * 6 * PI.toFloat()) else 1f
                    rotate(deg, c) {
                        drawImage(
                            sprite,
                            dstOffset = IntOffset((c.x - s / 2).roundToInt(), (c.y - s / 2).roundToInt()),
                            dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                            alpha = alpha,
                        )
                    }
                }
                else -> drawCircle( // halo + 모르는 패턴
                    Brush.radialGradient(
                        (colors.map { it.copy(alpha = 0.55f * breathe) } + Color.Transparent).let { if (it.size < 2) it + Color.Transparent else it },
                        c, base * 1.45f,
                    ),
                    base * 1.45f, c,
                )
            }
        }
        StarShapeIcon(type = starType, colorIndex = starColor, modifier = Modifier.size(maxWidth * 0.42f))
    }
}

/** 칭호 텍스트 — 색 1개 = 단색, 2~3개 = 그라데이션, shimmer = 빛이 좌→우로 흐른다. */
@Composable
fun TitleText(text: String, payload: Map<String, Any?>, fontSize: Float = 13f, modifier: Modifier = Modifier) {
    val colors = payload.colors().ifEmpty { listOf(Color(0xFFE9EEF5)) }
    val shimmer = payload["shimmer"] as? Boolean ?: false
    val x by rememberInfiniteTransition(label = "title").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(3_400, easing = LinearEasing)), label = "title_shimmer",
    )
    val base = if (colors.size >= 2) Brush.linearGradient(colors) else Brush.linearGradient(listOf(colors[0], colors[0]))
    val brush = if (shimmer) {
        Brush.linearGradient(
            listOf(colors.first(), colors.getOrElse(1) { colors.first() }, Color.White, colors.getOrElse(1) { colors.first() }, colors.last()),
            start = Offset(x * 300f - 200f, 0f), end = Offset(x * 300f + 100f, 40f),
        )
    } else base
    Text(text, modifier = modifier, style = TextStyle(brush = brush, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold))
}

/** 아이템 대표 그림(정사각형 칸 안). */
@Composable
fun ItemArt(item: StarlightStore.Item, modifier: Modifier = Modifier) {
    val p = item.payload
    Box(modifier, contentAlignment = Alignment.Center) {
        when (item.kind) {
            "shape" -> StarShapeIcon(type = p.int("type", 1), colorIndex = 9, modifier = Modifier.fillMaxSize(0.78f))
            "color" -> StarShapeIcon(type = 1, colorIndex = p.int("index", 0), modifier = Modifier.fillMaxSize(0.78f))
            "frame" -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FramedAvatar(p, size = minOf(maxWidth, maxHeight)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF263042)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = Color(0xFF8A93A3), modifier = Modifier.fillMaxSize(0.6f))
                    }
                }
            }
            "aura" -> AuraArt(p, Modifier.fillMaxSize(), itemId = item.id)
            "badge" -> SvgGlyph(p.str("path"), p.colors(), Modifier.fillMaxSize(0.66f), glow = item.rarity != "common")
            "title" -> Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x22FFFFFF))
                    .border(0.75.dp, rarityColor(item.rarity).copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { TitleText(item.nameIn(starlightLang()), p, fontSize = 10.5f) }
            "telescope" -> SvgGlyph(p.str("icon"), p.colors().ifEmpty { listOf(rarityColor(item.rarity)) }, Modifier.fillMaxSize(0.7f), glow = true)
            "keyBundle" -> Row(verticalAlignment = Alignment.CenterVertically) {
                CrystalKeyIcon(Modifier.size(26.dp))
                Spacer(Modifier.width(2.dp))
                Text("×${p.int("keys", 3)}", color = Color(0xFFFFD27A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            else -> StardustIcon(Modifier.fillMaxSize(0.5f), rarityColor(item.rarity))
        }
    }
}
