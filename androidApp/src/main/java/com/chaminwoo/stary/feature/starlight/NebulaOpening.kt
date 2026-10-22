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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                // 카드 뒤 빛살(천천히 돈다)
                val rayA = if (phase == Phase.CARDS) 1f else pillar.value
                if (rayA > 0f && best != null) {
                    val center = if (phase == Phase.CARDS) Offset(size.width / 2, size.height * 0.45f) else bc
                    val n = if (best == "legendary") 18 else 12
                    rotate(spin, center) {
                        for (i in 0 until n) {
                            val a = i * 2 * PI.toFloat() / n
                            val len = size.maxDimension * 0.75f
                            val half = 2 * PI.toFloat() / n * 0.12f
                            val p = Path().apply {
                                moveTo(center.x, center.y)
                                lineTo(center.x + cos(a - half) * len, center.y + sin(a - half) * len)
                                lineTo(center.x + cos(a + half) * len, center.y + sin(a + half) * len)
                                close()
                            }
                            drawPath(p, Brush.radialGradient(listOf(bestColor.copy(alpha = 0.10f * rayA), bestColor.copy(alpha = 0.03f * rayA), Color.Transparent), center, len))
                        }
                    }
                }
                // 빛기둥
                val pv = pillar.value * (1f - boxOut.value)
                if (pv > 0f) {
                    val w = size.width * 0.32f * pv
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, bestColor.copy(alpha = 0.55f), Color.White.copy(alpha = 0.85f)), startY = 0f, endY = bc.y),
                        topLeft = Offset(bc.x - w / 2, 0f), size = androidx.compose.ui.geometry.Size(w, bc.y),
                    )
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f)), startY = 0f, endY = bc.y),
                        topLeft = Offset(bc.x - w * 0.12f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.24f, bc.y),
                    )
                }
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
            val base = listOf(0xFF0B0E22.toInt(), androidx.core.graphics.ColorUtils.blendARGB(0xFF100C2A.toInt(), c.toArgb(), 0.28f), 0xFF070916.toInt())
            drawIntoCanvas { cv ->
                val side = maxOf(size.width, size.height)
                StarStyle.drawCrystalFacets(
                    cv.nativeCanvas, null, 77, base,
                    (size.width - side) / 2, (size.height - side) / 2, side,
                )
            }
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
        // 비스듬히 지나가는 광택(따로 그려 파편 바탕은 매 프레임 다시 그리지 않는다)
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

@Composable
private fun CardFront(d: StarlightStore.Draw, shape: RoundedCornerShape, big: Boolean) {
    val item = StarlightStore.item(d.id)
    val c = rarityColor(d.rarity)
    val lang = starlightLang()
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), Color(0xFF121821), Color(0xFF0E131B))))
            .border(1.dp, c.copy(alpha = 0.55f), shape)
            .padding(if (big) 18.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(rarityName(d.rarity), color = c, fontSize = if (big) 13.sp else 10.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ce = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.55f
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), ce, r), r, ce)
            }
            if (item != null) ItemArt(item, if (big) Modifier.size(130.dp) else Modifier.fillMaxSize(0.82f))
            else StardustIcon(Modifier.fillMaxSize(0.45f), c)
        }
        Text(
            item?.nameIn(lang) ?: d.id, color = Ink, fontSize = if (big) 17.sp else 11.5.sp, lineHeight = if (big) 22.sp else 14.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2,
        )
        if (big && item != null) {
            Spacer(Modifier.height(4.dp))
            Text(item.descIn(lang), color = Sub, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, maxLines = 3)
        }
        Spacer(Modifier.height(3.dp))
        val note = when {
            d.keys > 0 -> stringResource(R.string.starlight_keys_plus, d.keys)
            d.dup -> stringResource(R.string.starlight_dup_refund, d.refund)
            item != null && item.kind == "telescope" -> stringResource(R.string.starlight_stacked)
            else -> stringResource(R.string.starlight_new)
        }
        Text(note, color = if (d.dup) Dim else Dust, fontSize = if (big) 12.sp else 9.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
    }
}
