package com.chaminwoo.stary.feature.starlight

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
 * 우주 항해 연출(2026-10-26 — 성운 상자 대신 수정구, 시안 "천문 원반 받침"). 세 박자:
 *
 *  1. **두드리기** — 수정구를 세 번 두드린다. 누른 자리에서 금이 갈라지고(방사형 + 거미줄), 두드릴수록 안의 성운이 빨리 돈다.
 *  2. **깨짐** — 결과 등급 빛으로 모였다가(1초) 누른 자리를 중심으로 유리가 불규칙한 조각으로 부서진다.
 *     등급이 높을수록 빛이 넓고 화려하다(일반: 흰 빛 · 희귀: 빛줄기 8 · 영웅: 보랏빛 번쩍임 + 14 · 전설: 금빛 섬광 + 22 + 금가루).
 *  3. **카드** — 조각이 거의 다 떨어지면 엎어진 카드가 솟는다. 눌러서(또는 "모두 뒤집기") 한 장씩 뒤집는다.
 *     이미 가진 것은 그림의 빛이 빠지며 별가루로 바뀌는 과정을 보여 준다.
 *
 * 요청은 창을 띄우자마자 보낸다([OpeningState.draws] 가 오기 전에 세 번 두드리면 금이 천천히 번지며 수정구가 떨고 기다린다).
 * 건너뛰기 = 깨지는 장면 없이 카드로(카드는 자동으로 뒤집힌다). iOS `VoyageOpening.swift` 와 같은 흐름.
 */
internal data class OpeningState(val count: Int, val route: String, val draws: List<StarlightStore.Draw>? = null)

private const val TAPS = 3

@Composable
internal fun VoyageOpening(state: OpeningState, onDone: () -> Unit) {
    val draws = state.draws
    val best = draws?.maxByOrNull { StarlightStore.RARITIES.indexOf(it.rarity) }?.rarity
    val tint = routeTint(state.route)
    val scope = rememberCoroutineScope()
    val engine = rememberOrbEngine(interactive = true, rare = state.route == "rare")
    val frame = rememberOrbClock(engine)
    val sparks = remember { SparkField() }
    val sky = remember { SkyStars(seed = 29, n = 80) }
    var skip by remember { mutableStateOf(false) }

    DisposableEffect(engine) {
        engine.onConverge = { Haptics.medium() }
        engine.onShatter = { lv ->
            when (lv) {
                3 -> {
                    Haptics.heavy()
                    scope.launch { delay(90); Haptics.celebrate() }
                }
                2 -> Haptics.celebrate()
                else -> Haptics.medium()
            }
        }
        onDispose {
            engine.onConverge = null
            engine.onShatter = null
        }
    }
    LaunchedEffect(best) { if (best != null) engine.setResult(best) }
    LaunchedEffect(skip, best) {
        if (skip && best != null) {
            engine.setResult(best)
            engine.skip()
        }
    }

    val cardsShown = engine.cardsReady && draws != null
    val cardIn by animateFloatAsState(if (cardsShown) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "cards_in")
    val phase = engine.phase
    val waking = phase == OrbEngine.Phase.IDLE || phase == OrbEngine.Phase.WAIT
    val promptA by animateFloatAsState(if (waking && !skip) 1f else 0f, tween(320), label = "prompt")

    val flipped = remember(draws) { mutableStateListOf<Boolean>().apply { repeat(draws?.size ?: 0) { add(false) } } }
    val allFlipped = flipped.isNotEmpty() && flipped.all { it }
    fun flipAll() {
        scope.launch {
            flipped.indices.forEach { i ->
                if (!flipped[i]) {
                    flipped[i] = true
                    delay(140)
                }
            }
        }
    }
    LaunchedEffect(cardsShown) {
        if (cardsShown && skip) {
            delay(500)
            flipAll()
        }
    }

    Dialog(
        onDismissRequest = { if (cardsShown) onDone() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFA04060E))) {
            val dens = LocalDensity.current
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            // 수정구 무대(시안 단위) → 화면 px: 가로를 꽉 채우되 세로가 짧으면 줄인다. 구슬은 화면 40% 높이.
            val k = min(w / ORB_W, h / 560f)
            val ox = w / 2f - ORB_CX * k
            val oy = h * 0.40f - ORB_CY * k

            // ── 하늘 · 수정구 · 깨짐 ──
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(engine, k, ox, oy) {
                        detectTapGestures { p ->
                            if (engine.tap((p.x - ox) / k, (p.y - oy) / k)) {
                                when (engine.taps) {
                                    1 -> Haptics.light()
                                    2 -> Haptics.medium()
                                    else -> Haptics.heavy()
                                }
                            }
                        }
                    },
            ) {
                frame.value
                val t = engine.t
                for (i in 0 until sky.size) {
                    val tw = 0.5f + 0.5f * sin(t * sky.w[i] + sky.p[i])
                    drawCircle(
                        Color.White.copy(alpha = (0.08f + 0.4f * tw) * (sky.s[i] / 1.6f).coerceAtMost(1f)),
                        sky.s[i] * 0.75f * density, Offset(sky.x[i] * size.width, sky.y[i] * size.height),
                    )
                }
                val c = Offset(size.width / 2f, oy + ORB_CY * k)
                val r = size.width * 0.95f
                drawCircle(Brush.radialGradient(listOf(tint.accent.copy(alpha = 0.15f), tint.accent.copy(alpha = 0.04f), Color.Transparent), c, r), r, c)
                drawIntoCanvas {
                    engine.draw(
                        it.nativeCanvas, k, ox, oy, size.width, size.height,
                        stageAlpha = 1f - cardIn, auraShift = cardIn, auraY = (size.height * 0.45f - oy) / k,
                    )
                }
            }

            // ── 경로 · 횟수(카드가 뜨면 사라진다) ──
            if (cardIn < 0.99f) {
                Text(
                    "${routeName(state.route)} · ×${state.count}",
                    color = tint.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 18.dp)
                        .graphicsLayer { alpha = 1f - cardIn },
                )
            }

            // ── 두드리기 안내(받침 바로 아래) ──
            if (promptA > 0.01f) {
                val promptY = with(dens) { (oy + 402f * k).toDp() }
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = promptY)
                        .graphicsLayer { alpha = promptA },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(if (engine.taps >= TAPS) R.string.starlight_waiting else R.string.starlight_tap_to_wake),
                        color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(TAPS) { i ->
                            val on = i < engine.taps
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (on) tint.accent else Color(0x33FFFFFF))
                                    .border(0.75.dp, if (on) Color.White.copy(alpha = 0.6f) else Color(0x44FFFFFF), CircleShape),
                            )
                        }
                    }
                }
            }

            // ── 카드 ──
            if (cardsShown && draws != null) {
                CardsLayer(
                    draws, flipped, appear = cardIn,
                    onFlip = { i, center ->
                        if (!flipped[i]) {
                            flipped[i] = true
                            val rar = draws[i].rarity
                            when (rar) {
                                "legendary" -> {
                                    Haptics.celebrate()
                                    scope.launch { delay(260); sparks.burst(center, rarityColor(rar), 34, 340f * dens.density, frame.value) }
                                }
                                "epic" -> {
                                    Haptics.medium()
                                    scope.launch { delay(260); sparks.burst(center, rarityColor(rar), 20, 250f * dens.density, frame.value) }
                                }
                                else -> Haptics.tick()
                            }
                        }
                    },
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 22.dp)
                        .graphicsLayer { alpha = cardIn },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!allFlipped) Text(stringResource(R.string.starlight_tap_card), color = Sub, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton(
                        stringResource(if (allFlipped) R.string.starlight_confirm else R.string.starlight_flip_all),
                        primary = allFlipped, tint = tint,
                    ) { if (allFlipped) onDone() else flipAll() }
                }
            }

            // ── 뒤집을 때 튀는 반짝이(카드 위) ──
            Canvas(Modifier.fillMaxSize()) {
                sparks.draw(this, frame.value)
            }

            // ── 건너뛰기 ──
            if (!cardsShown && !skip) {
                Text(
                    stringResource(R.string.starlight_skip),
                    color = Sub, fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x1FFFFFFF))
                        .clickable {
                            skip = true
                            Haptics.tick()
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 카드를 뒤집을 때 튀는 반짝이 — 위치는 (태어난 시각, 지금)만의 함수, 프레임마다 다시 그린다. */
private class CardSpark(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val size: Float, val color: Color, val born: Long, val life: Float, val spin: Float,
)

private class SparkField {
    private val list = ArrayList<CardSpark>()
    private val rnd = Random(System.nanoTime())

    /** [at] 에서 [n] 개를 [power](px/초) 로 흩뿌린다. [now] = 프레임 시계(나노초). */
    fun burst(at: Offset, color: Color, n: Int, power: Float, now: Long) {
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val v = (0.3f + rnd.nextFloat() * 0.7f) * power
            list += CardSpark(
                at.x, at.y, cos(a) * v, sin(a) * v - power * 0.35f,
                (2.5f + rnd.nextFloat() * 4.5f), if (rnd.nextFloat() < 0.3f) Color.White else color,
                now, 0.8f + rnd.nextFloat() * 0.6f, (rnd.nextFloat() - 0.5f) * 540f,
            )
        }
    }

    fun draw(scope: DrawScope, now: Long) = with(scope) {
        if (list.isEmpty()) return@with
        val g = 520f * density
        list.removeAll { (now - it.born) / 1e9f > it.life }
        for (s in list) {
            val age = ((now - s.born) / 1e9f).coerceAtLeast(0f)
            val a = (1f - age / s.life).coerceIn(0f, 1f)
            val p = Offset(s.x + s.vx * age, s.y + s.vy * age + 0.5f * g * age * age)
            val r = s.size * density * (0.6f + 0.4f * a)
            drawCircle(s.color.copy(alpha = 0.22f * a), r * 2.2f, p)
            rotate(s.spin * age, p) { drawSparkle(p, r * 1.6f, s.color.copy(alpha = a)) }
        }
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, tint: RouteTint, onClick: () -> Unit) {
    Text(
        text,
        color = if (primary) tint.ink else Ink,
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickBounce()
            .clip(RoundedCornerShape(50))
            .background(
                if (primary) Brush.linearGradient(listOf(tint.light, tint.deep))
                else Brush.linearGradient(listOf(tint.accent.copy(alpha = 0.2f), tint.accent.copy(alpha = 0.12f)))
            )
            .border(0.75.dp, if (primary) Color.Transparent else tint.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
}

/** 카드 격자 — 1장은 크게, 2~4장은 두 줄, 5장부터 세 줄. 카드가 아래에서 솟아오른다([appear] 0..1 로 함께 밝아진다). */
@Composable
private fun CardsLayer(draws: List<StarlightStore.Draw>, flipped: List<Boolean>, appear: Float, onFlip: (Int, Offset) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 110.dp)
            .graphicsLayer { alpha = appear },
    ) {
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
                scaleX = sc
                scaleY = sc
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
            val ce = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * 0.42f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.42f), c.copy(alpha = 0.12f), Color.Transparent), ce, r), r, ce)
            drawSparkle(ce, size.minDimension * 0.17f, Color.White.copy(alpha = 0.92f))
            drawSparkle(ce, size.minDimension * 0.09f, c)
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
 * 카드 뒷면 무늬 — 가운데에서 퍼지는 **대칭** 크리스탈 면(장미창). 빛은 왼쪽 위에서, 가운데로 갈수록 등급 빛.
 * 고리 k 의 꼭짓점은 k 가 홀수면 반 칸 엇갈려 삼각형으로 이어진다. iOS `CardBack.roseFacets` 와 같은 식.
 */
private fun DrawScope.drawRoseFacets(tint: Color) {
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
 * 카드 앞면. 중복(이미 가진 것)이면 뒤집힌 뒤 **바뀌는 과정**을 보여 준다:
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
                        scaleX = sc
                        scaleY = sc
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
private fun DrawScope.drawDustFlow(t: Float, from: Offset, radius: Float, to: Offset, big: Boolean) {
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
        val e = 1 - (1 - p) * (1 - p)
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
