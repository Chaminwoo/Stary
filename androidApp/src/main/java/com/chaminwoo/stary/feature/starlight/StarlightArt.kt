package com.chaminwoo.stary.feature.starlight

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarShapeIcon
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 별빛 공방 아이템 그림(2026-10-22) — 공방·뽑기 연출·보관함·프로필이 같이 쓴다. iOS `StarlightArt.swift` 와 같은 해석.
 *
 * 프레임·오라·배지·칭호는 **DB payload 의 매개변수로 그린다**(앱에 그림을 박지 않는다):
 *  - frame : `pattern`(ring · dash · orbit · petals · constellation · aurora) · `colors` · `width`(0~1, 기본 0.06) · `count` · `speed`(한 바퀴 초, 0=정지) · `glow`
 *  - aura  : `pattern`(halo · pulse · ring) · `colors` · `speed`
 *  - badge : `path`(0~1 SVG, StarCatalog 와 같은 규칙) · `colors`
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

private val svgCache = ConcurrentHashMap<String, Path>()

/** 0~1 단위 SVG 경로 → Compose Path(캐시). 읽지 못하면 null. */
internal fun unitSvgPath(svg: String?): Path? {
    if (svg.isNullOrBlank()) return null
    svgCache[svg]?.let { return it }
    val p = runCatching { PathParser.createPathFromPathData(svg).asComposePath() }.getOrNull() ?: return null
    svgCache[svg] = p
    return p
}

/** 별가루 아이콘 — 크기가 다른 네 꼭지 반짝이 셋. */
@Composable
fun StardustIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFFFE6A8)) {
    Canvas(modifier) {
        val s = size.minDimension
        fun sparkle(cx: Float, cy: Float, r: Float, alpha: Float) {
            val path = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(path, tint.copy(alpha = alpha))
        }
        sparkle(s * 0.40f, s * 0.42f, s * 0.36f, 1f)
        sparkle(s * 0.80f, s * 0.22f, s * 0.17f, 0.85f)
        sparkle(s * 0.78f, s * 0.80f, s * 0.13f, 0.7f)
    }
}

/** 성운 상자 — 보랏빛 성운이 소용돌이치는 둥근 상자. [glow] 0~1(열기 직전 밝아짐). */
@Composable
fun NebulaBoxArt(modifier: Modifier = Modifier, glow: Float = 0f, spin: Boolean = true) {
    val t by rememberInfiniteTransition(label = "nebula").animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (spin) 9_000 else 60_000, easing = LinearEasing)), label = "nebula_spin",
    )
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        // 바깥 빛
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFB47CFF).copy(alpha = 0.35f + 0.4f * glow), Color.Transparent), c, s * 0.62f),
            s * 0.62f, c,
        )
        // 상자 몸체(둥근 결정)
        val body = s * 0.36f
        rotate(t, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFF3A1C71), Color(0xFF7B2FF7), Color(0xFFD76D77), Color(0xFF3A8DFF), Color(0xFF3A1C71)), c),
                body, c,
            )
        }
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f + 0.45f * glow), Color(0x00FFFFFF)), c, body * 0.75f),
            body * 0.75f, c,
        )
        // 성운 소용돌이 팔 두 개
        rotate(-t * 1.6f, c) {
            for (k in 0 until 2) {
                val arm = Path()
                for (i in 0..24) {
                    val f = i / 24f
                    val a = (k * PI + f * 2.6).toFloat()
                    val r = body * (0.15f + 0.8f * f)
                    val x = c.x + cos(a) * r
                    val y = c.y + sin(a) * r
                    if (i == 0) arm.moveTo(x, y) else arm.lineTo(x, y)
                }
                drawPath(arm, Color.White.copy(alpha = 0.35f), style = Stroke(s * 0.025f, cap = StrokeCap.Round))
            }
        }
        // 테두리 고리
        drawCircle(Color(0xFFE9D8FF).copy(alpha = 0.7f), body, c, style = Stroke(s * 0.018f))
    }
}

/** 0~1 SVG 글리프를 색(1개=단색, 2~3개=대각 그라데이션)으로 채운다 — 배지·망원경 아이콘. */
@Composable
fun SvgGlyph(svg: String?, colors: List<Color>, modifier: Modifier = Modifier, glow: Boolean = false) {
    val path = remember(svg) { unitSvgPath(svg) }
    Canvas(modifier) {
        val p = path ?: return@Canvas
        val s = size.minDimension
        val cs = colors.ifEmpty { listOf(Color.White) }
        val brush = if (cs.size >= 2) Brush.linearGradient(cs, Offset.Zero, Offset(s, s)) else Brush.linearGradient(listOf(cs[0], cs[0]))
        translate((size.width - s) / 2, (size.height - s) / 2) {
            if (glow) {
                scale(1.12f, pivot = Offset(s / 2, s / 2)) {
                    scale(s, s, pivot = Offset.Zero) { drawPath(p, cs.last().copy(alpha = 0.28f)) }
                }
            }
            scale(s, s, pivot = Offset.Zero) { drawPath(p, brush) }
        }
    }
}

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
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "frame_wave")
    val angle = if (speed > 0) spin else 0f
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        val w = s * width
        val r = s / 2 - s * FRAME_INSET / 2 - w * 0.2f
        val ringBrush = Brush.sweepGradient(if (colors.size >= 2) colors + colors.first() else listOf(colors[0], colors[0]), c)
        if (glow) drawCircle(colors.last().copy(alpha = 0.22f), r, c, style = Stroke(w * 2.6f))
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
                    val tw = 0.55f + 0.45f * sin((wave * 2 * PI + i * 1.7).toFloat())
                    drawCircle(colors[i % colors.size].copy(alpha = 0.25f * tw), w * 1.5f, p)
                    drawSparkle(p, w * (if (i % 3 == 0) 1.5f else 1.0f), colors[i % colors.size].copy(alpha = 0.6f + 0.4f * tw))
                }
            }
            "aurora" -> {
                rotate(angle, c) { drawCircle(ringBrush, r, c, style = Stroke(w * 1.25f)) }
                rotate(-angle * 1.5f, c) {
                    drawCircle(
                        Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Transparent), c),
                        r, c, style = Stroke(w * 0.7f),
                    )
                }
                drawCircle(colors.first().copy(alpha = 0.18f + 0.12f * sin((wave * 2 * PI).toFloat())), r + w * 1.2f, c, style = Stroke(w * 0.5f))
            }
            else -> rotate(angle, c) { drawCircle(ringBrush, r, c, style = Stroke(w)) } // ring + 모르는 패턴
        }
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

/** 지도 별 오라 미리보기 — 별 하나와 그 둘레 빛. */
@Composable
fun AuraArt(payload: Map<String, Any?>, modifier: Modifier = Modifier, starType: Int = 1, starColor: Int = 0) {
    val pattern = payload.str("pattern") ?: "halo"
    val colors = payload.colors().ifEmpty { listOf(Color(0xFFBFD8FF)) }
    val speed = payload.num("speed", 2.4f).coerceIn(0.6f, 10f)
    val tr = rememberInfiniteTransition(label = "aura")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween((speed * 1000).toInt(), easing = LinearEasing)), label = "aura_phase")
    val breathe by tr.animateFloat(0.75f, 1f, infiniteRepeatable(tween((speed * 500).toInt()), RepeatMode.Reverse), label = "aura_breathe")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)
            val base = s * 0.30f
            when (pattern) {
                "pulse" -> {
                    drawCircle(Brush.radialGradient(listOf(colors[0].copy(alpha = 0.55f), Color.Transparent), c, base * 1.25f), base * 1.25f, c)
                    for (k in 0 until 2) {
                        val f = (ph + k * 0.5f) % 1f
                        drawCircle(colors[k % colors.size].copy(alpha = (1 - f) * 0.7f), base * (0.6f + f * 1.0f), c, style = Stroke(s * 0.018f))
                    }
                }
                "ring" -> {
                    drawCircle(Brush.radialGradient(listOf(colors[0].copy(alpha = 0.45f * breathe), Color.Transparent), c, base * 1.3f), base * 1.3f, c)
                    rotate(ph * 360f, c) {
                        drawOval(
                            Brush.sweepGradient(colors + colors.first(), c),
                            topLeft = Offset(c.x - base * 1.35f, c.y - base * 0.42f), size = Size(base * 2.7f, base * 0.84f),
                            style = Stroke(s * 0.02f),
                        )
                    }
                }
                else -> drawCircle( // halo
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
        -1f, 2f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "title_shimmer",
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
            "frame" -> FramedAvatar(p, size = 56.dp) {
                Box(Modifier.fillMaxSize().background(Color(0xFF263042)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Color(0xFF8A93A3), modifier = Modifier.fillMaxSize(0.6f))
                }
            }
            "aura" -> AuraArt(p, Modifier.fillMaxSize())
            "badge" -> SvgGlyph(p.str("path"), p.colors(), Modifier.fillMaxSize(0.62f), glow = item.rarity != "common")
            "title" -> Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x22FFFFFF))
                    .border(0.75.dp, rarityColor(item.rarity).copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { TitleText(item.nameIn(starlightLang()), p, fontSize = 10.5f) }
            "telescope" -> SvgGlyph(p.str("icon"), p.colors().ifEmpty { listOf(rarityColor(item.rarity)) }, Modifier.fillMaxSize(0.7f), glow = true)
            "keyBundle" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, null, tint = Color(0xFFFFD27A), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(2.dp))
                Text("×${p.int("keys", 3)}", color = Color(0xFFFFD27A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            else -> StardustIcon(Modifier.fillMaxSize(0.5f), rarityColor(item.rarity))
        }
    }
}
