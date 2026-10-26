package com.chaminwoo.stary.feature.starlight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ads.AdFreeAccount
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset
import com.chaminwoo.stary.core.ui.StaryButton
import com.chaminwoo.stary.core.ui.StaryButtonKind
import com.chaminwoo.stary.core.ui.StaryChip
import com.chaminwoo.stary.core.ui.StaryPopupPanel
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.diary.screen.watchAdForKey
import com.chaminwoo.stary.feature.quest.QuestEvent
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

internal val Ink = Color(0xFFF0F0F0)
internal val Sub = Color(0xFFB8C0CC)
internal val Dim = Color(0xFF7D8696)
internal val Card = Color(0x14FFFFFF)
internal val Line = Color(0x1AFFFFFF)
internal val Dust = Color(0xFFFFE6A8)
internal val Gold = Color(0xFFFFD27A)
internal val Violet = Color(0xFFB47CFF)

/** 항해 경로 색 — [accent] 테·빛, [light]→[deep] 출항 버튼, [ink] 버튼 글자. 수정구 색(OrbPalette)과 짝. */
internal class RouteTint(val accent: Color, val light: Color, val deep: Color, val ink: Color)

private val NormalTint = RouteTint(Color(0xFF9FC3FF), Color(0xFFE6EEFF), Color(0xFF9DB9FF), Color(0xFF0B1430))
private val RareTint = RouteTint(Color(0xFFFFD27A), Color(0xFFFFF0C2), Color(0xFFFFC15E), Color(0xFF2A1600))

internal fun routeTint(route: String): RouteTint = if (route == "rare") RareTint else NormalTint

@Composable
internal fun routeName(route: String): String =
    stringResource(if (route == "rare") R.string.voyage_route_rare else R.string.voyage_route_normal)

/** 무대(수정구 시안 단위) 중 화면에 쓰는 높이 — 원반 그림자까지. */
private const val STAGE_H = 392f

/** 무대를 뺀 나머지(지갑 · 광고 · 경로 · 출항 · 안내 + 사이)의 대략 높이 — 무대 크기를 정할 때. */
private val CHROME = 300.dp

/**
 * 별가루(2026-10-26 개편, 옛 "별빛 공방").
 *  - 맨 위: 별가루 · 열쇠(옆 + 로 열쇠 구매 창) / 그 아래 광고 보고 별가루 받기(하루 한도·간격은 서버).
 *  - 가운데: **테두리 없이 화면을 넓게 쓰는 우주 항해 무대** — 천문 원반 위 수정구(C안, [OrbEngine]). 경로를 바꾸면 유리·금속 색이 섞여 바뀐다.
 *  - 아래: 경로(일반 · 희귀 탐사) · 횟수(×1 · ×5 · ×10) · 출항. 확률은 작은 버튼으로만 연다. 찾은 것은 업적 › 특별 › 탐사.
 * 서버가 모든 계산을 한다([StarlightStore]) — 이 화면은 요청을 보내고 결과를 연출만 한다([VoyageOpening]).
 * iOS `WorkshopScreen.swift` 패리티.
 */
@Composable
fun WorkshopScreen(onOpenFinds: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = StarlightStore.config
    var route by rememberSaveable { mutableStateOf("normal") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var adBusy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<OpeningState?>(null) }
    var keySheet by remember { mutableStateOf(false) }
    var oddsSheet by remember { mutableStateOf(false) }

    val orb = rememberOrbEngine(interactive = false, rare = route == "rare")
    // 항해 연출 창이 덮는 동안엔 무대 수정구를 쉬게 한다(연출 창이 제 수정구를 굴린다).
    val frame = rememberOrbClock(orb, running = opening == null)
    LaunchedEffect(route) { orb.setRoute(route == "rare", animate = true) }

    val tint = routeTint(route)
    val accent by animateColorAsState(tint.accent, tween(600), label = "route_accent")
    val counts = listOf(1, 5, 10).filter { it <= cfg.maxVoyage }.ifEmpty { listOf(1) }
    val n = if (count in counts) count else counts.first()
    val cost = cfg.route(route).cost * n
    val loaded = StarlightStore.walletLoaded
    val short = if (loaded) (cost - StarlightStore.stardust).coerceAtLeast(0) else 0

    LaunchedEffect(Unit) { StaryAnalytics.log("workshop_open", "stardust" to StarlightStore.stardust) }

    // 개편 전에 만들어 둔 성운 상자 → 별가루로(상자 값 그대로). 지갑을 받은 뒤 한 번 — 상자 수로 키를 걸면
    // 결과보다 지갑 갱신이 먼저 와서 요청이 취소될 수 있다.
    LaunchedEffect(loaded) {
        if (!loaded || StarlightStore.boxes <= 0) return@LaunchedEffect
        val before = StarlightStore.boxes
        val r = StarlightStore.convertBoxes()
        if (r is StarlightStore.OpResult.Done) {
            val boxes = (r.result["boxes"] as? Number)?.toInt() ?: before
            val gained = (r.result["gained"] as? Number)?.toInt() ?: 0
            if (boxes > 0 && gained > 0) {
                Haptics.celebrate()
                StaryToast.show(context.getString(R.string.voyage_boxes_converted, boxes, gained))
            }
        }
    }

    fun fail(code: String) {
        Haptics.light()
        StaryToast.show(
            context.getString(
                when (code) {
                    "insufficient" -> R.string.starlight_err_insufficient
                    "empty_pool" -> R.string.starlight_err_empty_pool
                    "ad_limit" -> R.string.voyage_ad_limit
                    "ad_cooldown" -> R.string.voyage_ad_cooldown
                    "timeout" -> R.string.starlight_err_timeout
                    else -> R.string.starlight_err_generic
                }
            )
        )
    }

    fun sail() {
        if (busy || opening != null || !loaded) return
        val r = route
        val k = n
        if (StarlightStore.stardust < cfg.route(r).cost * k) return fail("insufficient")
        busy = true
        Haptics.medium()
        // 요청은 창을 띄우자마자 — 두드리는 동안 결과가 온다(늦으면 세 번째 두드림 뒤 수정구가 떨며 기다린다).
        opening = OpeningState(count = k, route = r)
        scope.launch {
            val res = StarlightStore.voyage(r, k)
            busy = false
            when (res) {
                is StarlightStore.OpResult.Done -> {
                    val draws = StarlightStore.draws(res.result)
                    if (draws.isEmpty()) {
                        opening = null
                        return@launch fail("server")
                    }
                    StaryAnalytics.log(
                        "voyage", "route" to r, "count" to k,
                        "best" to (draws.maxByOrNull { StarlightStore.RARITIES.indexOf(it.rarity) }?.rarity ?: ""),
                    )
                    QuestStore.report(QuestEvent.OPEN_BOX)
                    opening = opening?.copy(draws = draws)
                }
                is StarlightStore.OpResult.Failed -> {
                    opening = null
                    fail(res.code)
                }
            }
        }
    }

    fun claimAd() {
        if (adBusy || !loaded) return
        if (StarlightStore.adsLeft <= 0) return fail("ad_limit")
        val cool = cfg.adCooldownSec * 1000L
        // 기기 시계가 서버와 어긋나도 막히지 않게 — 간격 안쪽일 때만 미리 거른다(나머지는 서버가 센다).
        val wait = StarlightStore.adsLast + cool - System.currentTimeMillis()
        if (wait in 1L..cool) return fail("ad_cooldown")
        val reward: () -> Unit = {
            adBusy = true
            scope.launch {
                val r = StarlightStore.adReward()
                adBusy = false
                when (r) {
                    is StarlightStore.OpResult.Done -> {
                        val gained = (r.result["gained"] as? Number)?.toInt() ?: cfg.adStardust
                        Haptics.celebrate()
                        StaryAnalytics.log("workshop_ad_reward", "gained" to gained, "ad_free" to AdFreeAccount.active)
                        StaryToast.show(context.getString(R.string.starlight_stardust_gained, gained))
                    }
                    is StarlightStore.OpResult.Failed -> fail(r.code)
                }
            }
        }
        if (AdFreeAccount.active) reward() else watchAdForKey(context, notFinished = R.string.voyage_ad_not_finished, onRewarded = reward)
    }

    fun buyKeys(k: Int) {
        if (busy) return
        busy = true
        scope.launch {
            val r = StarlightStore.buyKeys(k)
            busy = false
            when (r) {
                is StarlightStore.OpResult.Done -> {
                    Haptics.celebrate()
                    StaryAnalytics.log("workshop_buy_keys", "count" to k)
                    StaryToast.show(context.getString(R.string.starlight_made_keys, k))
                    keySheet = false
                }
                is StarlightStore.OpResult.Failed -> fail(r.code)
            }
        }
    }

    // 무대 수정구의 화면 위치(창 좌표) — 바탕 성운을 그 뒤에 맞춘다.
    var rootY by remember { mutableFloatStateOf(0f) }
    var orbY by remember { mutableFloatStateOf(-1f) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootY = it.positionInWindow().y }) {
        SpaceBackdrop(frame, orb, accent) { if (orbY < 0f) -1f else orbY - rootY }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val topInset = LocalTopBarInset.current
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val room = maxHeight - topInset - bottomInset - CHROME
            // 화면이 허락하는 만큼 크게(폭은 받침 원반 + 여유, 높이는 아래 조작부가 한 화면에 들어오게).
            val k = min(room / STAGE_H.dp, maxWidth / 318.dp).coerceIn(0.72f, 1.18f)
            val spare = (room - (STAGE_H * k).dp).coerceAtLeast(0.dp)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topInset + 6.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                WalletRow(onBuyKeys = { if (loaded) keySheet = true })
                Spacer(Modifier.height(10.dp))
                AdStrip(busy = adBusy, onClick = ::claimAd)
                Spacer(Modifier.height(spare * 0.35f))
                VoyageStage(
                    orb, frame, k, accent,
                    onOdds = { oddsSheet = true },
                    modifier = Modifier.onGloballyPositioned { c ->
                        orbY = c.positionInWindow().y + c.size.height * (ORB_CY / STAGE_H)
                    },
                )
                Spacer(Modifier.height(spare * 0.65f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StarlightStore.ROUTES.forEach { id ->
                        val rt = cfg.route(id)
                        RouteTicket(
                            id, selected = route == id, cost = rt.cost,
                            epicPlus = rt.percent("epic") + rt.percent("legendary"),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (route != id) {
                                Haptics.tick()
                                route = id
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (counts.size > 1) {
                        CountPicker(counts, n, accent) {
                            if (it != count) {
                                Haptics.tick()
                                count = it
                            }
                        }
                    }
                    SailButton(cost, short, loaded, busy, tint, Modifier.weight(1f).fillMaxHeight(), onClick = ::sail)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenFinds)
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.voyage_finds_link), color = Sub, fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Sub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    opening?.let { st -> VoyageOpening(st, onDone = { if (st.draws != null) opening = null }) }
    if (keySheet) KeySheet(busy = busy, onBuy = ::buyKeys, onDismiss = { keySheet = false })
    if (oddsSheet) OddsSheet(initialRoute = route, onDismiss = { oddsSheet = false })
}

// ── 바탕 ─────────────────────────────────────────────────────────────────

/**
 * 깊은 우주 — 남색 그라데이션 + 수정구 뒤 경로 빛 성운(경로를 바꾸면 0.6초 동안 색이 바뀐다) + 보랏빛·푸른 성운 두 덩이
 * + 깜빡이는 잔별 + 가끔 지나가는 별똥별. 시간은 수정구 시계([OrbEngine.t])라 앱이 뒤로 가면 같이 쉰다.
 */
@Composable
private fun SpaceBackdrop(frame: State<Long>, orb: OrbEngine, accent: Color, focusY: () -> Float) {
    val sky = remember { SkyStars(seed = 11, n = 90) }
    Canvas(Modifier.fillMaxSize()) {
        frame.value
        val t = orb.t
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF03050D), Color(0xFF070B1E), Color(0xFF0A0B1C), Color(0xFF04050C))))
        val fy = focusY().takeIf { it > 0f } ?: (h * 0.42f)
        val c = Offset(w / 2f, fy)
        val r1 = w * 1.05f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.17f), accent.copy(alpha = 0.05f), Color.Transparent), c, r1), r1, c)
        val c2 = Offset(w * 0.12f, h * 0.16f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF7B5CFF).copy(alpha = 0.11f), Color.Transparent), c2, w * 0.85f), w * 0.85f, c2)
        val c3 = Offset(w * 0.92f, h * 0.80f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3E7BFF).copy(alpha = 0.08f), Color.Transparent), c3, w * 0.75f), w * 0.75f, c3)
        // 잔별(굵은 몇 개는 네 꼭지 반짝이)
        for (i in 0 until sky.size) {
            val tw = 0.5f + 0.5f * sin(t * sky.w[i] + sky.p[i])
            val p = Offset(sky.x[i] * w, sky.y[i] * h)
            val a = (0.10f + 0.45f * tw) * (sky.s[i] / 1.6f).coerceAtMost(1f)
            if (sky.s[i] > 1.48f) drawSparkle(p, (2.2f + 1.6f * tw) * density, Color.White.copy(alpha = 0.35f + 0.5f * tw))
            else drawCircle(Color.White.copy(alpha = a), sky.s[i] * 0.75f * density, p)
        }
        // 별똥별 — 8초마다 0.9초, 매번 다른 자리(위쪽 하늘)
        val cycle = 8f
        val idx = floor(t / cycle)
        val u = (t - idx * cycle) / 0.9f
        if (u in 0f..1f) {
            val seed = (idx * 12.9898f).let { (sin(it) * 43758.547f) }.let { it - floor(it) }
            val sx = w * (0.15f + 0.6f * seed)
            val sy = h * (0.06f + 0.18f * ((seed * 7.31f) % 1f))
            val len = w * 0.28f
            val dx = len * 0.92f
            val dy = len * 0.38f
            val head = Offset(sx + dx * u, sy + dy * u)
            val tail = Offset(head.x - dx * 0.45f, head.y - dy * 0.45f)
            val fade = sin(u * Math.PI.toFloat())
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * fade)), tail, head),
                tail, head, strokeWidth = 1.4f * density, cap = StrokeCap.Round,
            )
            drawCircle(Color.White.copy(alpha = 0.8f * fade), 1.4f * density, head)
        }
    }
}

// ── 무대 ─────────────────────────────────────────────────────────────────

/** 우주 항해 무대 — 제목이 수정구 빛 위에 떠 있고, 오른쪽 위 작은 "확률" 버튼. 수정구를 누르면 빛이 출렁인다(깨지지 않는다). */
@Composable
private fun VoyageStage(
    orb: OrbEngine,
    frame: State<Long>,
    k: Float,
    accent: Color,
    onOdds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height((STAGE_H * k).dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(orb) {
                    detectTapGestures {
                        orb.tap(null, null)
                        Haptics.tick()
                    }
                },
        ) {
            frame.value
            val kp = k * density
            val ox = size.width / 2f - ORB_CX * kp
            drawIntoCanvas { orb.draw(it.nativeCanvas, kp, ox, 0f, size.width, size.height) }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.voyage_title),
                style = TextStyle(
                    color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 7.sp,
                    shadow = Shadow(accent.copy(alpha = 0.7f), blurRadius = 28f),
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.voyage_sub), color = Sub.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x14FFFFFF))
                .border(0.75.dp, Color(0x26FFFFFF), RoundedCornerShape(50))
                .clickable {
                    Haptics.tick()
                    onOdds()
                }
                .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, null, tint = Sub, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.voyage_odds), color = Sub, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── 지갑 · 광고 ──────────────────────────────────────────────────────────

@Composable
private fun WalletRow(onBuyKeys: () -> Unit) {
    val loaded = StarlightStore.walletLoaded
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WalletChip(
            label = stringResource(R.string.starlight_stardust),
            value = if (loaded) fmt(StarlightStore.stardust) else "…",
            valueColor = Dust,
            modifier = Modifier.weight(1f),
            icon = { StardustIcon(Modifier.size(19.dp)) },
        )
        val buy = stringResource(R.string.voyage_key_title)
        WalletChip(
            label = stringResource(R.string.starlight_keys),
            value = fmt(QuestStore.keys),
            valueColor = Ink,
            modifier = Modifier.weight(1f),
            icon = { CrystalKeyIcon(Modifier.size(19.dp)) },
            trailing = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clickBounce()
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFFF0C2), Gold, Color(0xFFFFB259))))
                        .clickable(onClickLabel = buy) {
                            Haptics.tick()
                            onBuyKeys()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, buy, tint = Color(0xFF2A1A00), modifier = Modifier.size(20.dp)) }
            },
        )
    }
}

@Composable
private fun WalletChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
            .border(0.75.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF))), shape)
            .padding(start = 7.dp, end = if (trailing != null) 7.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x12FFFFFF)), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Dim, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
            Text(
                value, maxLines = 1,
                style = TextStyle(color = valueColor, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
        }
        trailing?.invoke()
    }
}

/** 광고 보고 별가루 받기(광고제거 계정은 광고 없이) — 오른쪽에 오늘 남은 횟수. 다 받으면 흐려진다. */
@Composable
private fun AdStrip(busy: Boolean, onClick: () -> Unit) {
    val cfg = StarlightStore.config
    val loaded = StarlightStore.walletLoaded
    val left = StarlightStore.adsLeft
    val on = loaded && left > 0
    val adFree = AdFreeAccount.active
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(44.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Dust.copy(alpha = if (on) 0.16f else 0.06f), Dust.copy(alpha = 0.03f))))
            .border(0.75.dp, Brush.horizontalGradient(listOf(Dust.copy(alpha = if (on) 0.45f else 0.14f), Dust.copy(alpha = 0.08f))), shape)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(start = 9.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (on) Brush.linearGradient(listOf(Color(0xFFFFF3D0), Color(0xFFFFC75A))) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (adFree) Icons.Filled.AutoAwesome else Icons.Filled.PlayArrow, null,
                tint = if (on) Color(0xFF2A1A00) else Dim, modifier = Modifier.size(if (adFree) 15.dp else 18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (adFree) R.string.voyage_ad_free else R.string.voyage_ad, cfg.adStardust),
            color = if (on) Ink else Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                !loaded || busy -> "…"
                left > 0 -> stringResource(R.string.voyage_ad_left, left)
                else -> stringResource(R.string.voyage_ad_done_today)
            },
            color = if (on) Dust.copy(alpha = 0.85f) else Dim, fontSize = 11.sp, maxLines = 1,
        )
    }
}

// ── 경로 · 횟수 · 출항 ─────────────────────────────────────────────────────

/** 경로 표 — 이름 · 한 번 값 · 영웅 이상 확률. 고른 표는 경로 빛 테가 밝아진다. */
@Composable
private fun RouteTicket(route: String, selected: Boolean, cost: Int, epicPlus: Double, modifier: Modifier, onClick: () -> Unit) {
    val tint = routeTint(route)
    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(320), label = "route_sel")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.accent.copy(alpha = 0.05f + 0.13f * sel), Color(0x0AFFFFFF))))
            .border(
                (0.75f + 0.5f * sel).dp,
                Brush.verticalGradient(listOf(tint.accent.copy(alpha = 0.16f + 0.64f * sel), tint.accent.copy(alpha = 0.05f + 0.22f * sel))),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(12.dp)) {
                val r = size.minDimension / 2f
                drawCircle(tint.accent.copy(alpha = 0.25f * sel), r * 1.0f)
                drawCircle(
                    Brush.radialGradient(listOf(tint.light, tint.deep, tint.deep.copy(alpha = 0.6f)), Offset(r * 0.7f, r * 0.6f), r * 0.9f),
                    r * 0.62f,
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(routeName(route), color = lerp(Sub, Ink, sel), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StardustIcon(Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                fmt(cost),
                style = TextStyle(color = lerp(Dust.copy(alpha = 0.7f), Dust, sel), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(R.string.voyage_epic_plus, pct(epicPlus)),
            color = tint.accent.copy(alpha = 0.5f + 0.5f * sel), fontSize = 11.sp, maxLines = 1,
        )
    }
}

/** ×1 · ×5 · ×10 — 유리 캡슐 안 고른 칸만 경로 빛. */
@Composable
private fun CountPicker(counts: List<Int>, value: Int, accent: Color, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0x12FFFFFF))
            .border(0.75.dp, Line, shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        counts.forEach { c ->
            key(c) {
                val sel by animateFloatAsState(if (c == value) 1f else 0f, tween(200), label = "count_sel")
                Box(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(min = 42.dp)
                        .clip(shape)
                        .background(accent.copy(alpha = 0.2f * sel))
                        .border(0.75.dp, accent.copy(alpha = 0.55f * sel), shape)
                        .clickable { onPick(c) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "×$c",
                        style = TextStyle(color = lerp(Dim, Ink, sel), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}

/** 출항 — 경로 빛 캡슐에 "출항하기 | ✦ 값", 가끔 빛이 비스듬히 스친다. 모자라면 얼마 모자란지. */
@Composable
private fun SailButton(cost: Int, short: Int, loaded: Boolean, busy: Boolean, tint: RouteTint, modifier: Modifier, onClick: () -> Unit) {
    val ready = loaded && short <= 0
    val shape = RoundedCornerShape(50)
    val shine by rememberInfiniteTransition(label = "sail").animateFloat(
        -0.5f, 1.5f, infiniteRepeatable(tween(2_400, delayMillis = 2_200, easing = LinearEasing), RepeatMode.Restart), label = "sail_shine",
    )
    Box(
        modifier
            .then(if (ready) Modifier.clickBounce(1.04f) else Modifier)
            .clip(shape)
            .background(
                if (ready) Brush.linearGradient(listOf(tint.light, tint.deep))
                else Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x12FFFFFF)))
            )
            .border(0.75.dp, if (ready) Color.White.copy(alpha = 0.45f) else Line, shape)
            .drawWithContent {
                drawContent()
                if (ready) {
                    val x = shine * size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = Offset(x - size.height, 0f), end = Offset(x + size.height * 0.2f, size.height),
                        ),
                    )
                }
            }
            .clickable(enabled = ready && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !loaded -> Text("…", color = Sub, fontSize = 16.sp)
            short > 0 -> Text(stringResource(R.string.voyage_short, short), color = Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voyage_launch), color = tint.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(16.dp).background(tint.ink.copy(alpha = 0.25f)))
                Spacer(Modifier.width(12.dp))
                StardustIcon(Modifier.size(15.dp), tint.ink)
                Spacer(Modifier.width(4.dp))
                Text(fmt(cost), style = TextStyle(color = tint.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
    }
}

// ── 열쇠 구매(작고 단순하게) ───────────────────────────────────────────────

@Composable
private fun KeySheet(busy: Boolean, onBuy: (Int) -> Unit, onDismiss: () -> Unit) {
    val cfg = StarlightStore.config
    val each = cfg.keyCost.coerceAtLeast(1)
    val max = (StarlightStore.stardust / each).coerceIn(1, cfg.maxExchange.coerceAtLeast(1))
    var n by remember { mutableIntStateOf(1) }
    val total = each * n
    val short = (total - StarlightStore.stardust).coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StaryPopupPanel(maxWidth = 300.dp, horizontalAlignment = Alignment.CenterHorizontally) {
            CrystalKeyIcon(Modifier.size(46.dp), glow = true)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.voyage_key_title), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.voyage_key_each, each), color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(Icons.Filled.Remove, enabled = n > 1) { n-- }
                Text(
                    n.toString(), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp),
                    style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
                StepButton(Icons.Filled.Add, enabled = n < max) { n++ }
            }
            Spacer(Modifier.height(18.dp))
            val ok = short <= 0 && !busy
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (short <= 0) Brush.linearGradient(listOf(Color(0xFFFFF0C2), Color(0xFFFFC75A)))
                        else Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
                    )
                    .clickable(enabled = ok) { onBuy(n) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (short > 0) {
                    Text(stringResource(R.string.voyage_short, short), color = Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } else if (busy) {
                    Text("…", color = Color(0xFF2A1A00), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(stringResource(R.string.voyage_buy), color = Color(0xFF2A1A00), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    StardustIcon(Modifier.size(14.dp), Color(0xFF2A1A00))
                    Spacer(Modifier.width(4.dp))
                    Text(fmt(total), style = TextStyle(color = Color(0xFF2A1A00), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(if (enabled) 0x1FFFFFFF else 0x0DFFFFFF))
            .border(0.75.dp, if (enabled) Color(0x33FFFFFF) else Line, CircleShape)
            .clickable(enabled = enabled) {
                Haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Ink else Dim, modifier = Modifier.size(20.dp)) }
}

// ── 확률(작은 버튼으로만 연다) ─────────────────────────────────────────────

/**
 * 확률 안내 — 경로마다 등급 확률 · 그 등급에 든 아이템 수(n종) · 하나당 확률, 그리고 규칙(같은 등급은 같은 확률 · 매번 독립 · 중복 환급).
 * 확률형 아이템 표시 기준에 맞춰 **모든 확률을 숫자로** 보여 준다(아이템 이름은 숨긴다 — 무엇이 나올지는 떠나 봐야 안다).
 */
@Composable
private fun OddsSheet(initialRoute: String, onDismiss: () -> Unit) {
    val cfg = StarlightStore.config
    var r by remember { mutableStateOf(initialRoute) }
    val rt = cfg.route(r)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StaryPopupPanel(maxWidth = 340.dp) {
            Text(stringResource(R.string.starlight_odds_title), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlightStore.ROUTES.forEach { id ->
                    StaryChip(routeName(id), selected = r == id, accent = routeTint(id).accent) {
                        Haptics.tick()
                        r = id
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                StarlightStore.RARITIES.reversed().forEach { rar ->
                    val p = rt.percent(rar)
                    val kinds = StarlightStore.poolCount(rar)
                    val c = rarityColor(rar)
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rarityName(rar), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (kinds > 0) {
                                    Text(
                                        "${stringResource(R.string.voyage_odds_kinds, kinds)} · ${stringResource(R.string.voyage_odds_each, pct(p / kinds))}",
                                        color = Dim, fontSize = 11.sp,
                                    )
                                }
                            }
                            Text(pct(p), style = TextStyle(color = c, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF))) {
                            Box(
                                Modifier
                                    .fillMaxWidth((p / 100.0).toFloat().coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.5f), c))),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    stringResource(R.string.voyage_odds_equal),
                    stringResource(R.string.voyage_odds_independent),
                    stringResource(
                        R.string.starlight_dup_rule,
                        cfg.dupRefund["common"] ?: 0, cfg.dupRefund["rare"] ?: 0, cfg.dupRefund["epic"] ?: 0, cfg.dupRefund["legendary"] ?: 0,
                    ),
                ).forEach { note ->
                    Text("· $note", color = Dim, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            StaryButton(
                stringResource(R.string.starlight_confirm), onDismiss, Modifier.fillMaxWidth(),
                kind = StaryButtonKind.Ghost, compact = true,
            )
        }
    }
}

// ── 공용 ─────────────────────────────────────────────────────────────────

@Composable
internal fun rarityName(r: String): String = stringResource(
    when (r) {
        "rare" -> R.string.starlight_rarity_rare
        "epic" -> R.string.starlight_rarity_epic
        "legendary" -> R.string.starlight_rarity_legendary
        else -> R.string.starlight_rarity_common
    }
)

@Composable
internal fun kindName(k: String): String = stringResource(
    when (k) {
        "shape" -> R.string.starlight_kind_shape
        "color" -> R.string.starlight_kind_color
        "title" -> R.string.starlight_kind_title
        "frame" -> R.string.starlight_kind_frame
        "badge" -> R.string.starlight_kind_badge
        "aura" -> R.string.starlight_kind_aura
        "telescope" -> R.string.starlight_kind_telescope
        "keyBundle" -> R.string.starlight_kind_key_bundle
        else -> R.string.starlight_kind_other
    }
)

/** 확률 표기 — 딱 떨어지면 소수 없이(66%), 1% 이상은 한 자리, 그 밑은 두 자리. */
internal fun pct(v: Double): String = when {
    abs(v - v.roundToLong()) < 1e-9 -> String.format(Locale.US, "%.0f%%", v)
    v >= 1 -> String.format(Locale.US, "%.1f%%", v)
    else -> String.format(Locale.US, "%.2f%%", v)
}

/** 천 단위 쉼표(별가루는 금방 네 자리가 된다). */
internal fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)
