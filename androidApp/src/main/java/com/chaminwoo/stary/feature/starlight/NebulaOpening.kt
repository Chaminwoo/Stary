package com.chaminwoo.stary.feature.starlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 성운 상자 여는 연출(2026-10-22) — 상자깡 게임(브롤스타즈 상자 · 클래시 로얄 상자 · 하스스톤 팩)을 참고한 세 박자.
 *
 *  1. **깨우기** — 떠 있는 크리스탈 상자를 톡톡 세 번. 두드릴 때마다 찌그러졌다 튀어 오르고, 금이 가고, 파편이 튄다.
 *     새는 빛은 **이번에 나온 가장 높은 등급까지 한 칸씩** 오른다(일반 → 희귀 → 영웅 → 전설) — 몇 번째에서 멈추는지가 기대감.
 *  2. **열림** — 뚜껑이 날아가고 등급 빛기둥이 솟는다. 전설은 금빛 충격파와 섬광.
 *  3. **카드** — 엎어진 크리스탈 카드가 솟아오른다. 카드 뒷면이 등급 빛으로 은은히 빛나고, 눌러서(또는 "모두 뒤집기") 한 장씩 뒤집는다.
 *
 * 요청은 상자를 띄우자마자 보낸다([state].draws 가 오기 전엔 빛이 보랏빛에 머물고, 세 번째 두드림 뒤엔 응답을 기다린다).
 * iOS `NebulaOpening.swift` 와 같은 흐름.
 */
internal data class OpeningState(val count: Int, val draws: List<StarlightStore.Draw>? = null)

private enum class Phase { WAKE, BURST, CARDS }

private const val TAPS = 3
private val Lilac = Color(0xFFC9A2FF)

/** 튀는 결정 파편 하나(원점 = 상자 가운데, px). */
private class Shard(
    val vx: Float, val vy: Float, val rot0: Float, val vr: Float,
    val size: Float, val color: Color, val born: Long, val life: Long,
    val ox: Float = 0f, val oy: Float = 0f,
)

@Composable
internal fun NebulaOpening(state: OpeningState, onDone: () -> Unit) {
    val draws = state.draws
    val bestIdx = draws?.maxOfOrNull { StarlightStore.RARITIES.indexOf(it.rarity).coerceAtLeast(0) }
    val best = bestIdx?.let { StarlightStore.RARITIES[it] }
    val bestColor = best?.let { rarityColor(it) } ?: Lilac
    val scope = rememberCoroutineScope()
    val dens = LocalDensity.current

    var phase by remember { mutableStateOf(Phase.WAKE) }
    var taps by remember { mutableIntStateOf(0) }
    var skip by remember { mutableStateOf(false) }
    val squash = remember { Animatable(1f) }
    val wobble = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val pillar = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    val boxOut = remember { Animatable(0f) }
    val shards = remember { mutableStateListOf<Shard>() }
    var now by remember { mutableLongStateOf(0L) }
    var boxCenter by remember { mutableStateOf(Offset.Zero) }
    var burstAt by remember { mutableLongStateOf(0L) }
    val rnd = remember { Random(System.nanoTime()) }

    // 프레임 시계 — 파편·빛살이 이걸로 움직인다.
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { t -> now = t; shards.removeAll { t - it.born > it.life } }
    }

    fun spray(n: Int, color: Color, power: Float, at: Offset = boxCenter) {
        val px = with(dens) { 1.dp.toPx() }
        repeat(n) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = (0.35f + rnd.nextFloat() * 0.65f) * power * px
            shards += Shard(
                vx = cos(a) * v, vy = sin(a) * v - power * px * 0.35f,
                rot0 = rnd.nextFloat() * 360f, vr = (rnd.nextFloat() - 0.5f) * 720f,
                size = (3f + rnd.nextFloat() * 6f) * px, color = if (rnd.nextFloat() < 0.3f) Color.White else color,
                born = now, life = 900L + rnd.nextLong(600L),
                ox = at.x - boxCenter.x, oy = at.y - boxCenter.y,
            )
        }
    }

    /** 두드림 k 번째의 빛 색 — 나온 최고 등급까지 한 칸씩. 응답 전엔 보랏빛. */
    fun tapColor(k: Int): Color = if (bestIdx == null || k == 0) Lilac else rarityColor(StarlightStore.RARITIES[min(k, bestIdx)])

    fun burst() {
        if (phase != Phase.WAKE) return
        phase = Phase.BURST
        burstAt = now
        scope.launch {
            when (best) {
                "legendary" -> { Haptics.heavy(); delay(90); Haptics.celebrate() }
                "epic" -> Haptics.celebrate()
                else -> Haptics.medium()
            }
            launch { flash.snapTo(if (best == "legendary") 0.9f else 0.55f); flash.animateTo(0f, tween(700)) }
            launch { lid.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            launch { pillar.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            if (best == "legendary" || best == "epic") launch { shock.animateTo(1f, tween(1_000, easing = FastOutSlowInEasing)) }
            spray(if (best == "legendary") 46 else 30, bestColor, if (best == "legendary") 520f else 380f)
            delay(if (skip) 250 else 750)
            phase = Phase.CARDS
            boxOut.animateTo(1f, tween(420))
        }
    }

    fun tap() {
        if (phase != Phase.WAKE || taps >= TAPS) return
        taps++
        Haptics.medium()
        spray(10 + taps * 4, tapColor(taps), 150f + taps * 60f)
        scope.launch {
            squash.snapTo(0.84f)
            squash.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        }
        scope.launch {
            wobble.snapTo(if (taps % 2 == 0) 7f else -7f)
            wobble.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow))
        }
        if (taps >= TAPS && draws != null) scope.launch { delay(380); burst() }
    }

    // 세 번 두드렸거나 건너뛰었는데 응답이 늦게 온 경우 — 오면 바로 연다.
    LaunchedEffect(draws, taps, skip) {
        if (draws != null && phase == Phase.WAKE && (taps >= TAPS || skip)) {
            if (skip) burst() else { delay(300); burst() }
        }
    }

    val flipped = remember(draws) { mutableStateListOf<Boolean>().apply { repeat(draws?.size ?: 0) { add(false) } } }
    val allFlipped = flipped.isNotEmpty() && flipped.all { it }

    fun flipAll() {
        scope.launch {
            flipped.indices.forEach { i ->
                if (!flipped[i]) { flipped[i] = true; delay(140) }
            }
        }
    }
    LaunchedEffect(phase) {
        if (phase == Phase.CARDS && skip) { delay(500); flipAll() }
    }

    val ambient = rememberInfiniteTransition(label = "opening")
    val spin by ambient.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "rays")
    val waiting by ambient.animateFloat(-1f, 1f, infiniteRepeatable(tween(110), RepeatMode.Reverse), label = "shiver")
    val stars = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }

    Dialog(
        onDismissRequest = { if (draws != null) onDone() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF5070912))) {
            // ── 배경 · 빛기둥 · 빛살 · 충격파 · 파편 ──
            Canvas(Modifier.fillMaxSize()) {
                val tSec = now / 1000f
                // 별밭
                stars.forEach { (x, y, k) ->
                    val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(tSec * (0.6f + k) + k * 20f))
                    drawCircle(Color.White.copy(alpha = 0.12f + 0.4f * tw * k), (0.6f + 1.4f * k) * 1.dp.toPx(), Offset(x * size.width, y * size.height))
                }
                // 은은한 성운
                val bc = if (boxCenter == Offset.Zero) Offset(size.width / 2, size.height * 0.42f) else boxCenter
                drawCircle(Brush.radialGradient(listOf(Color(0xFF5B2BB5).copy(alpha = 0.28f), Color.Transparent), bc, size.minDimension * 0.75f), size.minDimension * 0.75f, bc)
                // 빛살 — 열리는 순간 확 터졌다가(섬광·렌즈 플레어) 카드 뒤에선 은은하게 돈다(2026-10-23 — "광선이 너무 심플" 요청).
                val bt = if (burstAt > 0L) (now - burstAt) / 1000f else -1f
                val rayA = when {
                    best == null || bt < 0f -> 0f
                    bt < 0.22f -> bt / 0.22f * 1.6f
                    else -> 0.5f + 1.1f * kotlin.math.exp(-(bt - 0.22f) * 1.5f)
                }
                if (rayA > 0f && best != null) {
                    val cardC = Offset(size.width / 2, size.height * 0.45f)
                    val center = Offset(bc.x + (cardC.x - bc.x) * boxOut.value, bc.y + (cardC.y - bc.y) * boxOut.value)
                    drawGodRays(center, size.maxDimension * 0.8f, rayA, spin, tSec, rayPalette(best, bestColor))
                    if (bt in 0f..1.3f) drawFlare(center, size, 1f - bt / 1.3f, spin, bestColor)
                }
                // 빛기둥 — 위로 넓어지는 기둥 + 솟구치는 빛줄기·반짝이
                val pv = pillar.value * (1f - boxOut.value)
                if (pv > 0f) drawPillar(bc, size, pv, tSec, bestColor)
                // 충격파(영웅·전설)
                val sv = shock.value
                if (sv > 0f && sv < 1f) {
                    val r = size.maxDimension * 0.7f * sv
                    drawCircle(bestColor.copy(alpha = 0.8f * (1 - sv)), r, bc, style = Stroke(10.dp.toPx() * (1 - sv) + 1f))
                    drawCircle(Color.White.copy(alpha = 0.5f * (1 - sv)), r * 0.93f, bc, style = Stroke(3.dp.toPx() * (1 - sv) + 0.5f))
                }
                // 결정 파편
                shards.forEach { sh ->
                    val age = (now - sh.born).coerceAtLeast(0L) / 1000f
                    val life = sh.life / 1000f
                    if (age > life) return@forEach
                    val g = 300.dp.toPx()
                    val x = bc.x + sh.ox + sh.vx * age
                    val y = bc.y + sh.oy + sh.vy * age + 0.5f * g * age * age
                    val a = (1f - age / life).coerceIn(0f, 1f)
                    rotate(sh.rot0 + sh.vr * age, Offset(x, y)) {
                        val r = sh.size
                        val d = Path().apply { moveTo(x, y - r); lineTo(x + r * 0.45f, y); lineTo(x, y + r); lineTo(x - r * 0.45f, y); close() }
                        drawPath(d, sh.color.copy(alpha = a))
                        val hi = Path().apply { moveTo(x, y - r); lineTo(x + r * 0.45f, y); lineTo(x, y); close() }
                        drawPath(hi, Color.White.copy(alpha = a * 0.6f))
                    }
                }
            }

            // ── 상자 ──
            if (phase != Phase.CARDS || boxOut.value < 1f) {
                val glowK = (taps / TAPS.toFloat()).coerceIn(0f, 1f)
                val shiver = if (phase == Phase.WAKE && taps >= TAPS) waiting else 0f
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = (-40).dp)
                        .size(240.dp)
                        .onGloballyPositioned { boxCenter = it.boundsInRoot().center }
                        .graphicsLayer {
                            val sq = squash.value
                            scaleX = (2f - sq) * (1f - 0.35f * boxOut.value)
                            scaleY = sq * (1f - 0.35f * boxOut.value)
                            rotationZ = wobble.value + shiver * 2.5f
                            translationX = shiver * 3.dp.toPx()
                            alpha = 1f - boxOut.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            enabled = phase == Phase.WAKE, onClick = ::tap,
                        ),
                ) {
                    NebulaBoxArt(
                        Modifier.fillMaxSize(),
                        glow = if (phase == Phase.WAKE) glowK else 1f,
                        glowColor = if (phase == Phase.WAKE) tapColor(taps) else bestColor,
                        cracks = if (phase == Phase.WAKE) taps else TAPS,
                        lidOpen = lid.value,
                    )
                }
            }

            // ── 깨우기 안내 ──
            AnimatedVisibility(
                visible = phase == Phase.WAKE,
                enter = fadeIn(tween(400)), exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.Center).offset(y = 130.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (taps >= TAPS) R.string.starlight_waiting else R.string.starlight_tap_to_wake),
                        color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(TAPS) { i ->
                            val on = i < taps
                            Box(
                                Modifier.size(9.dp).clip(CircleShape)
                                    .background(if (on) tapColor(i + 1) else Color(0x33FFFFFF))
                                    .border(0.75.dp, if (on) Color.White.copy(alpha = 0.6f) else Color(0x44FFFFFF), CircleShape),
                            )
                        }
                    }
                }
            }

            // ── 섬광 ──
            if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))

            // ── 카드 ──
            if (phase == Phase.CARDS && draws != null) {
                CardsLayer(draws, flipped, onFlip = { i, center ->
                    if (flipped[i]) return@CardsLayer
                    flipped[i] = true
                    val r = draws[i].rarity
                    when (r) {
                        "legendary" -> { Haptics.celebrate(); scope.launch { delay(260); spray(28, rarityColor(r), 300f, center) } }
                        "epic" -> { Haptics.medium(); scope.launch { delay(260); spray(16, rarityColor(r), 220f, center) } }
                        else -> Haptics.tick()
                    }
                })
                // 아래 버튼
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!allFlipped) Text(stringResource(R.string.starlight_tap_card), color = Sub, fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton(
                        stringResource(if (allFlipped) R.string.starlight_confirm else R.string.starlight_flip_all),
                        primary = allFlipped,
                    ) { if (allFlipped) onDone() else flipAll() }
                }
            }

            // ── 건너뛰기 ──
            if (phase != Phase.CARDS && !skip) {
                Text(
                    stringResource(R.string.starlight_skip),
                    color = Sub, fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x1FFFFFFF))
                        .clickable { skip = true; Haptics.tick() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (primary) Color(0xFF1A0B33) else Ink,
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickBounce()
            .clip(RoundedCornerShape(50))
            .background(
                if (primary) Brush.linearGradient(listOf(Color(0xFFF1E3FF), Color(0xFFC9A2FF)))
                else Brush.linearGradient(listOf(Color(0x33C9A2FF), Color(0x22C9A2FF)))
            )
            .border(0.75.dp, if (primary) Color.Transparent else Lilac.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
}

/** 빛살 색 — 전설은 금·분홍·하늘이 번갈아(무지갯빛), 나머지는 등급 빛과 그 밝은 빛. */
private fun rayPalette(best: String, c: Color): List<Color> =
    if (best == "legendary") listOf(Color(0xFFFFD27A), Color(0xFFFF9ED8), Color(0xFF9FD3FF), Color(0xFFFFF0C2))
    else listOf(c, androidx.compose.ui.graphics.lerp(c, Color.White, 0.45f))

/**
 * 빛살 두 겹 — 넓고 부드러운 빛살(시계 방향, 길이가 숨 쉬듯 일렁임) + 가늘고 날카로운 빛살(반대로 더 빨리).
 * 가운데엔 흰 심 + 등급 빛 번짐. [a] 0..1.6(열리는 순간 1 넘게 → 은은하게 0.5). iOS `godRays` 와 같은 식.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGodRays(center: Offset, len: Float, a: Float, spin: Float, tSec: Float, palette: List<Color>) {
    val n = 14
    val step = 2 * PI.toFloat() / n
    rotate(spin, center) {
        for (i in 0 until n) {
            val ang = i * step
            val half = step * (if (i % 2 == 0) 0.2f else 0.11f)
            val l = len * (if (i % 2 == 0) 1f else 0.7f) * (0.85f + 0.15f * sin(tSec * 1.3f + i * 1.7f))
            val col = palette[i % palette.size]
            val p = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(ang - half) * l, center.y + sin(ang - half) * l)
                lineTo(center.x + cos(ang + half) * l, center.y + sin(ang + half) * l)
                close()
            }
            drawPath(p, Brush.radialGradient(
                listOf(Color.White.copy(alpha = (0.22f * a).coerceAtMost(1f)), col.copy(alpha = (0.14f * a).coerceAtMost(1f)), Color.Transparent), center, l,
            ))
        }
    }
    rotate(-spin * 1.7f, center) {
        val m = 28
        val st = 2 * PI.toFloat() / m
        for (i in 0 until m) {
            val ang = i * st + st / 2
            val half = st * 0.05f
            val l = len * (0.45f + 0.4f * ((i * 37) % 11) / 10f) * (0.8f + 0.2f * sin(tSec * 2.1f + i))
            val p = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(ang - half) * l, center.y + sin(ang - half) * l)
                lineTo(center.x + cos(ang + half) * l, center.y + sin(ang + half) * l)
                close()
            }
            drawPath(p, Brush.radialGradient(
                listOf(Color.White.copy(alpha = (0.4f * a).coerceAtMost(1f)), palette[(i + 1) % palette.size].copy(alpha = (0.12f * a).coerceAtMost(1f)), Color.Transparent),
                center, l,
            ))
        }
    }
    val core = len * 0.28f
    drawCircle(Brush.radialGradient(listOf(palette[0].copy(alpha = (0.4f * a).coerceAtMost(0.8f)), Color.Transparent), center, core), core, center)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (0.5f * a).coerceAtMost(0.9f)), Color.Transparent), center, core * 0.3f), core * 0.3f, center)
}

/** 렌즈 플레어 — 열리는 순간 가로 빛줄 + 큰 네 꼭지 반짝이 + 퍼지는 고리 둘, [f] 1 → 0 으로 사라진다. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlare(center: Offset, sz: androidx.compose.ui.geometry.Size, f: Float, spin: Float, c: Color) {
    val e = f * f
    val hw = sz.width * (0.4f + 0.6f * f)
    val hh = 3.dp.toPx() * (0.5f + f)
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Transparent, c.copy(alpha = 0.6f * e), Color.White.copy(alpha = 0.95f * e), c.copy(alpha = 0.6f * e), Color.Transparent),
            center.x - hw, center.x + hw,
        ),
        topLeft = Offset(center.x - hw, center.y - hh), size = androidx.compose.ui.geometry.Size(hw * 2, hh * 2),
    )
    val r = sz.minDimension * 0.22f * (0.6f + 0.4f * f)
    rotate(spin * 2f, center) {
        drawSparkle(center, r, Color.White.copy(alpha = 0.9f * e))
        rotate(45f, center) { drawSparkle(center, r * 0.55f, c.copy(alpha = 0.8f * e)) }
    }
    val rr = sz.minDimension * (0.18f + 0.35f * (1 - f))
    drawCircle(c.copy(alpha = 0.35f * e), rr, center, style = Stroke(2.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.18f * e), rr * 1.35f, center, style = Stroke(1.dp.toPx()))
}

/** 빛기둥 — 위로 갈수록 넓어지는 기둥, 안에서 빛줄기와 반짝이가 솟구친다. [pv] 0..1. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillar(bc: Offset, sz: androidx.compose.ui.geometry.Size, pv: Float, tSec: Float, c: Color) {
    val bottomW = sz.width * 0.16f * pv
    val topW = sz.width * 0.5f * pv
    val beam = Path().apply {
        moveTo(bc.x - bottomW / 2, bc.y); lineTo(bc.x + bottomW / 2, bc.y)
        lineTo(bc.x + topW / 2, 0f); lineTo(bc.x - topW / 2, 0f); close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.45f), Color.White.copy(alpha = 0.8f)), startY = 0f, endY = bc.y))
    val core = Path().apply {
        moveTo(bc.x - bottomW * 0.12f, bc.y); lineTo(bc.x + bottomW * 0.12f, bc.y)
        lineTo(bc.x + topW * 0.08f, 0f); lineTo(bc.x - topW * 0.08f, 0f); close()
    }
    drawPath(core, Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.95f)), startY = 0f, endY = bc.y))
    // 솟구치는 빛줄기
    for (i in 0 until 9) {
        val ph = ((tSec * (1.4f + (i % 3) * 0.35f) + i * 0.37f) % 1f)
        val y = bc.y * (1f - ph)
        val wAt = bottomW + (topW - bottomW) * ph
        val x = bc.x + (((i * 53) % 100) / 100f - 0.5f) * wAt * 0.8f
        val l = (40f + (i % 4) * 22f) * 1.dp.toPx()
        drawLine(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f * pv), Color.Transparent), startY = y, endY = y + l),
            Offset(x, y), Offset(x, y + l), strokeWidth = (1.5f + (i % 2)) * 1.dp.toPx(), cap = StrokeCap.Round,
        )
        if (i % 3 == 0) drawSparkle(Offset(x, y), 5.dp.toPx(), Color.White.copy(alpha = 0.9f * pv))
    }
}

/** 카드 격자 — 1장은 크게, 2~4장은 두 줄, 5장부터 세 줄. 카드가 아래(상자 쪽)에서 솟아오른다. */
@Composable
private fun CardsLayer(draws: List<StarlightStore.Draw>, flipped: List<Boolean>, onFlip: (Int, Offset) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 110.dp)) {
        val cols = when {
            draws.size == 1 -> 1
            draws.size <= 4 -> 2
            else -> 3
        }
        val gap = 10.dp
        val cardW: Dp = if (cols == 1) minOf(250.dp, maxWidth - 60.dp) else (maxWidth - 32.dp - gap * (cols - 1)) / cols
        val cardH = cardW * 1.42f
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        ) {
            draws.chunked(cols).forEachIndexed { row, list ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    list.forEachIndexed { j, d ->
                        val i = row * cols + j
                        FlipCard(d, i, flipped.getOrElse(i) { false }, big = cols == 1, w = cardW, h = cardH, onFlip = { onFlip(i, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipCard(d: StarlightStore.Draw, index: Int, flipped: Boolean, big: Boolean, w: Dp, h: Dp, onFlip: (Offset) -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(90L * index)
        Haptics.tick()
        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(560, easing = FastOutSlowInEasing), label = "flip")
    var center by remember { mutableStateOf(Offset.Zero) }
    val c = rarityColor(d.rarity)
    val shape = RoundedCornerShape(if (big) 22.dp else 14.dp)
    Box(
        Modifier
            .size(w, h)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .graphicsLayer {
                val e = enter.value
                translationY = (1f - e) * 320.dp.toPx()
                val sc = 0.45f + 0.55f * e
                scaleX = sc; scaleY = sc
                alpha = e.coerceIn(0f, 1f)
                rotationZ = (1f - e) * (if (index % 2 == 0) -14f else 14f)
                rotationY = angle
                cameraDistance = 14f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                enabled = !flipped,
            ) { onFlip(center) },
    ) {
        if (angle < 90f) CardBack(d.rarity, shape)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardFront(d, shape, big) }
        // 등급 테두리 빛 — 영웅·전설만 숨쉬듯(3.6초, 작은 폭)
        if (d.rarity == "legendary" || d.rarity == "epic") {
            val pulse by rememberInfiniteTransition(label = "card_rim").animateFloat(
                0.55f, 0.85f, infiniteRepeatable(tween(3_600), RepeatMode.Reverse), label = "card_rim_a",
            )
            Box(Modifier.fillMaxSize().border(1.5.dp, c.copy(alpha = pulse), shape))
        }
    }
}

/** 카드 뒷면 — 어두운 크리스탈 바탕에 등급 빛이 비치고, 가운데 별 문장. 등급이 뒤집기 전에 살짝 보인다(기대감). */
@Composable
private fun CardBack(rarity: String, shape: RoundedCornerShape) {
    val c = rarityColor(rarity)
    val shine by rememberInfiniteTransition(label = "back").animateFloat(
        -0.6f, 1.6f, infiniteRepeatable(tween(if (rarity == "legendary") 2_400 else 3_800, easing = LinearEasing)), label = "back_shine",
    )
    Box(
        Modifier.fillMaxSize().clip(shape).border(1.dp, c.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoseFacets(c)
            // 가운데 빛 + 문장
            val ce = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * 0.42f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.42f), c.copy(alpha = 0.12f), Color.Transparent), ce, r), r, ce)
            drawSparkle(ce, size.minDimension * 0.17f, Color.White.copy(alpha = 0.92f))
            drawSparkle(ce, size.minDimension * 0.09f, c)
            // 안쪽 얇은 테
            val inset = size.minDimension * 0.06f
            drawRoundRect(
                c.copy(alpha = 0.35f), topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(inset * 1.4f), style = Stroke(1.dp.toPx()),
            )
        }
        // 비스듬히 지나가는 광택(따로 그려 바탕은 매 프레임 다시 그리지 않는다)
        Canvas(Modifier.fillMaxSize()) {
            val x = shine * size.width
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (rarity == "legendary") 0.28f else 0.14f), Color.Transparent),
                    start = Offset(x - size.width * 0.3f, 0f), end = Offset(x + size.width * 0.3f, size.height),
                ),
            )
        }
    }
}

/**
 * 카드 뒷면 무늬 — 가운데에서 퍼지는 **대칭** 크리스탈 면(장미창). 예전 무작위 파편은 밝은 심이 왼쪽 위로 쏠려
 * "카드가 한쪽으로 치우치고 가운데가 빈" 느낌이었다(2026-10-23). 빛은 왼쪽 위에서, 가운데로 갈수록 등급 빛.
 * 고리 k 의 꼭짓점은 k 가 홀수면 반 칸 엇갈려 삼각형으로 이어진다. iOS `CardBack.roseFacets` 와 같은 식.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoseFacets(tint: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val m = size.minDimension
    val n = 8
    val radii = floatArrayOf(0f, m * 0.2f, m * 0.43f, m * 0.72f, size.width + size.height)
    fun pt(k: Int, i: Int): Offset {
        val a = ((i + (k % 2) * 0.5f) / n * 2 * PI - PI / 2).toFloat()
        return Offset(c.x + cos(a) * radii[k], c.y + sin(a) * radii[k])
    }
    val deep = Color(0xFF070916)
    val mid = androidx.compose.ui.graphics.lerp(Color(0xFF100C2A), tint, 0.32f)
    val line = Color.White.copy(alpha = 0.09f)
    val lw = 1.dp.toPx()
    fun tri(a: Offset, b: Offset, d: Offset, k: Int, parity: Int) {
        val cx = (a.x + b.x + d.x) / 3 - c.x
        val cy = (a.y + b.y + d.y) / 3 - c.y
        val light = 0.5f + 0.5f * cos(kotlin.math.atan2(cy, cx) + 3 * PI.toFloat() / 4) // 왼쪽 위가 밝다
        val v = ((0.25f + 0.6f * light + if (parity == 0) 0.1f else -0.06f) * (1f - k * 0.2f)).coerceIn(0f, 1f)
        val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() }
        drawPath(p, androidx.compose.ui.graphics.lerp(deep, mid, v))
        drawPath(p, line, style = Stroke(lw))
    }
    for (k in 0 until radii.size - 1) for (i in 0 until n) {
        // 안쪽 고리 점 P, 바깥 고리 점 Q — 엇갈림에 따라 삼각형 두 개
        if (k % 2 == 0) {
            tri(pt(k, i), pt(k + 1, i), pt(k, i + 1), k, 0)
            if (k > 0) tri(pt(k + 1, i), pt(k, i + 1), pt(k + 1, i + 1), k, 1)
        } else {
            tri(pt(k + 1, i), pt(k, i), pt(k + 1, i + 1), k, 0)
            tri(pt(k, i), pt(k + 1, i + 1), pt(k, i + 1), k, 1)
        }
    }
}

/**
 * 카드 앞면. 중복(이미 가진 것)이면 뒤집힌 뒤 **바뀌는 과정**을 보여 준다(2026-10-23 — 회색 글씨 한 줄로는 몰랐다는 요청):
 * 그림은 가운데 그대로 두고 빛이 빠지며(채도·밝기↓) "이미 있어요" 도장이 찍히고, 그림에서 별가루 알갱이가 흘러나와
 * 아래 금빛 "별가루 +N" 칩으로 빨려 든다(칩이 튀어 오르며 숫자가 올라간다). iOS `CardFront` 와 같은 시간표.
 */
@Composable
private fun CardFront(d: StarlightStore.Draw, shape: RoundedCornerShape, big: Boolean) {
    val item = StarlightStore.item(d.id)
    val c = rarityColor(d.rarity)
    val lang = starlightLang()
    // 0..1 — 뒤집힌 뒤 0.35초 쉬고 1.5초 동안 바뀐다.
    val conv = remember { Animatable(if (d.dup) 0f else 1f) }
    LaunchedEffect(d.dup) {
        if (!d.dup) return@LaunchedEffect
        delay(350)
        conv.animateTo(1f, tween(1_500, easing = LinearEasing))
    }
    val t = conv.value
    val fade = if (d.dup) ((t - 0.05f) / 0.4f).coerceIn(0f, 1f) else 0f
    var cardCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var artCenter by remember { mutableStateOf(Offset.Zero) }
    var artRadius by remember { mutableStateOf(0f) }
    var chipCenter by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.22f * (1 - fade * 0.6f)), Color(0xFF121821), Color(0xFF0E131B))))
            .border(1.dp, c.copy(alpha = 0.55f), shape)
            .onGloballyPositioned { cardCoords = it },
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (big) 18.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(rarityName(d.rarity), color = c, fontSize = if (big) 13.sp else 10.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { lc ->
                        val parent = cardCoords ?: return@onGloballyPositioned
                        if (!parent.isAttached || !lc.isAttached) return@onGloballyPositioned
                        artCenter = parent.localPositionOf(lc, Offset(lc.size.width / 2f, lc.size.height / 2f))
                        artRadius = minOf(lc.size.width, lc.size.height) / 2f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val ce = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension * 0.55f
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f * (1 - fade * 0.7f)), Color.Transparent), ce, r), r, ce)
                }
                val artMod = (if (big) Modifier.size(130.dp) else Modifier.fillMaxSize(0.82f))
                    .graphicsLayer {
                        val sc = 1f - 0.12f * fade
                        scaleX = sc; scaleY = sc
                        alpha = 1f - 0.45f * fade
                    }
                    .desaturate(fade)
                if (item != null) ItemArt(item, artMod)
                else StardustIcon(Modifier.fillMaxSize(0.45f), c)
                if (d.dup) OwnedStamp(big, appear = ((t - 0.12f) / 0.18f).coerceIn(0f, 1f), Modifier.align(Alignment.BottomCenter))
            }
            Text(
                item?.nameIn(lang) ?: d.id, color = Ink.copy(alpha = 1f - 0.25f * fade), fontSize = if (big) 17.sp else 11.5.sp,
                lineHeight = if (big) 22.sp else 14.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            if (big && item != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (d.dup) stringResource(R.string.starlight_dup_explain) else item.descIn(lang),
                    color = Sub, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(if (big) 8.dp else 4.dp))
            if (d.dup) {
                DustChip(
                    shown = (d.refund * ((t - 0.55f) / 0.4f).coerceIn(0f, 1f)).toInt(),
                    pop = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f), big = big,
                    modifier = Modifier.onGloballyPositioned { lc ->
                        val parent = cardCoords ?: return@onGloballyPositioned
                        if (!parent.isAttached || !lc.isAttached) return@onGloballyPositioned
                        chipCenter = parent.localPositionOf(lc, Offset(lc.size.width * 0.28f, lc.size.height / 2f))
                    },
                )
            } else {
                val note = when {
                    d.keys > 0 -> stringResource(R.string.starlight_keys_plus, d.keys)
                    item != null && item.kind == "telescope" -> stringResource(R.string.starlight_stacked)
                    else -> stringResource(R.string.starlight_new)
                }
                Text(
                    note, color = Dust, fontSize = if (big) 12.sp else 9.5.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // 별가루 알갱이 — 그림에서 흩어졌다가 칩으로 빨려 든다.
        if (d.dup && t in 0.08f..0.99f && artRadius > 0f && chipCenter != Offset.Zero) {
            Canvas(Modifier.fillMaxSize()) { drawDustFlow(t, artCenter, artRadius, chipCenter, big) }
        }
    }
}

/** 채도 빼기 — [amount] 0 = 그대로, 1 = 거의 흑백(조금 남긴다). 모든 API 에서 되도록 레이어 페인트로. */
private fun Modifier.desaturate(amount: Float): Modifier = if (amount <= 0f) this else drawWithContent {
    val paint = androidx.compose.ui.graphics.Paint().apply {
        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1f - 0.85f * amount) },
        )
    }
    drawIntoCanvas { cv ->
        cv.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
        drawContent()
        cv.restore()
    }
}

/** "이미 있어요" 도장 — 그림 아래쪽 가운데, 찍히듯 커졌다 제자리. */
@Composable
private fun OwnedStamp(big: Boolean, appear: Float, modifier: Modifier = Modifier) {
    if (appear <= 0f) return
    val sc = if (appear < 1f) 1.6f - 0.6f * appear else 1f
    Text(
        stringResource(R.string.starlight_dup_owned),
        color = Color(0xFFE6E9F0), fontSize = if (big) 12.sp else 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = appear }
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC1A1F2B))
            .border(0.75.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = if (big) 12.dp else 7.dp, vertical = if (big) 4.dp else 2.dp),
    )
}

/** 금빛 별가루 칩 — 튀어 오르며 나타나고 숫자가 [shown] 까지 올라간다. */
@Composable
private fun DustChip(shown: Int, pop: Float, big: Boolean, modifier: Modifier = Modifier) {
    // 0 → 1.15 → 1 로 통 튀는 크기
    val sc = when {
        pop <= 0f -> 0.6f
        pop < 0.5f -> 0.6f + 0.55f * (pop / 0.5f)
        else -> 1.15f - 0.15f * ((pop - 0.5f) / 0.5f)
    }
    Row(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = 0.35f + 0.65f * pop.coerceAtMost(1f) }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0x40FFC75A), Color(0x26FFE6A8))))
            .border(1.dp, Dust.copy(alpha = 0.35f + 0.45f * pop), RoundedCornerShape(50))
            .padding(start = if (big) 8.dp else 5.dp, end = if (big) 12.dp else 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StardustIcon(Modifier.size(if (big) 18.dp else 13.dp))
        Spacer(Modifier.width(if (big) 5.dp else 3.dp))
        Text(
            stringResource(R.string.starlight_dust_plus, shown), color = Dust, fontSize = if (big) 14.sp else 10.5.sp,
            fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/**
 * 별가루 흐름 — 알갱이마다 그림 둘레 한 점에서 태어나 바깥으로 살짝 튀었다가 곡선으로 칩에 들어간다(도착하며 작아짐).
 * 위치는 [t](0..1) 만의 함수라 프레임이 끊겨도 같은 그림. iOS `dustFlow` 와 같은 식.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDustFlow(t: Float, from: Offset, radius: Float, to: Offset, big: Boolean) {
    val n = if (big) 22 else 14
    for (j in 0 until n) {
        val start = 0.08f + 0.3f * ((j * 7) % n) / n
        val life = 0.5f
        val p = ((t - start) / life).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val a = (j * 2.399963f) // 황금각으로 고르게
        val r0 = radius * (0.25f + 0.35f * ((j * 5) % 7) / 7f)
        val s = Offset(from.x + cos(a) * r0, from.y + sin(a) * r0)
        val ctrl = Offset(from.x + cos(a) * radius * 1.05f, from.y + sin(a) * radius * 0.9f)
        val e = 1 - (1 - p) * (1 - p) // 빨려 들수록 빨라지지 않게 ease-out
        val u = 1 - e
        val pos = Offset(
            u * u * s.x + 2 * u * e * ctrl.x + e * e * to.x,
            u * u * s.y + 2 * u * e * ctrl.y + e * e * to.y,
        )
        val size = radius * (if (big) 0.032f else 0.05f) * (1f - 0.5f * p) * (if (j % 3 == 0) 1.5f else 1f)
        val alpha = (if (p < 0.15f) p / 0.15f else 1f) * (1f - p * 0.4f)
        drawCircle(Dust.copy(alpha = 0.18f * alpha), size * 1.8f, pos)
        drawSparkle(pos, size * 1.7f, Color(0xFFFFF3D0).copy(alpha = alpha))
    }
}
