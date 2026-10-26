package com.chaminwoo.stary.feature.starlight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.diary.screen.watchAdForKey
import com.chaminwoo.stary.feature.quest.QuestEvent
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
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

/** 경로 색 — [accent] 테·빛, [light]→[deep] 깨우기 버튼, [ink] 버튼 글자. 수정구 색(OrbPalette)과 짝. */
internal class RouteTint(val accent: Color, val light: Color, val deep: Color, val ink: Color)

private val NormalTint = RouteTint(Color(0xFF9FC3FF), Color(0xFFE6EEFF), Color(0xFF9DB9FF), Color(0xFF0B1430))
private val RareTint = RouteTint(Color(0xFFFFD27A), Color(0xFFFFF0C2), Color(0xFFFFC15E), Color(0xFF2A1600))

internal fun routeTint(route: String): RouteTint = if (route == "rare") RareTint else NormalTint

@Composable
internal fun routeName(route: String): String =
    stringResource(if (route == "rare") R.string.voyage_route_rare else R.string.voyage_route_normal)

/** 무대 배율 기준 폭 — 390dp 화면에서 배율 1.05(시안). */
private val REF_W = 371.43.dp
/** 탑바 아래 → 무대 원점(지갑 두 줄 + 제목 자리). */
private val ABOVE_STAGE = 104.dp
/** 무대(446 단위)를 뺀 세로 몫 — 위 104 + 안내 → 깨우기 34 + 버튼 56 + 아래 여유 20. */
private val CHROME = 214.dp
/** 무대 단위로 문지르기 안내가 놓이는 높이(마법진 앞 가장자리 아래). */
private const val HINT_Y = 446f
/** 이만큼(무대 단위) 움직이면 두드림이 아니라 문지르기. */
private const val RUB_SLOP = 14f
private val COUNTS = listOf(1, 10)

private val ButtonInk = Color(0xFF2A1A00)
private val EaseOutCubic = Easing { x -> 1f - (1f - x) * (1f - x) * (1f - x) }

/**
 * 별가루 — 잠든 우주(2026-10-27 개편, 시안 그대로).
 *  - 왼쪽 위: 별가루 · 열쇠 두 줄(▶ = 광고 보고 별가루 받기 창 · + = 열쇠 구매 창), 오른쪽 위: 확률.
 *  - 가운데: 별로 이룬 마법진 위로 피어오르는 은하수와 둥둥 뜬 수정구([OrbEngine]). 제목은 별자리 각인(A안) + 지금 경로(옅은 잠 · 깊은 잠).
 *    수정구 왼쪽·오른쪽을 누르면 그쪽으로 돌고, 1~2초 문지르면 경로가 바뀐다(다시 문지르면 돌아온다).
 *  - 아래: "수정구는 문지르면 가치가 변합니다" + 깨우기(오른쪽 위 작은 ×1 · ×10).
 * 서버가 모든 계산을 한다([StarlightStore]) — 이 화면은 요청을 보내고 결과를 연출만 한다([VoyageOpening]).
 * iOS `WorkshopScreen.swift` 패리티.
 */
@Composable
fun WorkshopScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = StarlightStore.config
    var route by rememberSaveable { mutableStateOf("normal") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var adBusy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<OpeningState?>(null) }
    var adSheet by remember { mutableStateOf(false) }
    var keySheet by remember { mutableStateOf(false) }
    var oddsSheet by remember { mutableStateOf(false) }

    val orb = rememberOrbEngine(interactive = false, rare = route == "rare")
    // 깨우기 창이 덮는 동안엔 무대 수정구를 쉬게 한다(창이 제 수정구를 굴린다).
    val frame = rememberOrbClock(orb, running = opening == null)
    LaunchedEffect(route) { orb.setRoute(route == "rare", animate = true) }

    val accent by animateColorAsState(routeTint(route).accent, tween(600), label = "route_accent")
    val counts = COUNTS.filter { it <= cfg.maxVoyage }.ifEmpty { listOf(1) }
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topInset = LocalTopBarInset.current
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val avail = maxHeight - topInset - bottomInset
        // 무대는 화면이 허락하는 만큼 크게(폭은 시안 비율, 높이는 깨우기 버튼까지 한 화면에). 남는 높이는 위 4 : 아래 6.
        val k = min(maxWidth / REF_W, (avail - CHROME) / HINT_Y.dp).coerceIn(0.6f, 1.25f)
        val spare = (avail - CHROME - (HINT_Y * k).dp).coerceAtLeast(0.dp)
        val stageTop = topInset + ABOVE_STAGE + spare * 0.4f
        val stageLeft = (maxWidth - (ORB_W * k).dp) / 2
        val hintTop = stageTop + (HINT_Y * k).dp

        Stage(orb, frame, k, stageLeft, stageTop) {
            route = if (orb.rare) "rare" else "normal"
            // 시안 진동 [18, 40, 28] — 묵직하게 한 번, 잠깐 쉬고 가볍게 한 번.
            scope.launch {
                Haptics.medium()
                delay(58)
                Haptics.light()
            }
        }
        Wallet(
            Modifier.padding(start = 16.dp, top = topInset + 8.dp),
            adOff = loaded && StarlightStore.adsLeft <= 0,
            onAd = { adSheet = true },
            onKeys = { if (loaded) keySheet = true },
        )
        OddsChip(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = topInset + 11.dp)) { oddsSheet = true }
        StageTitle(route, accent, frame, orb, Modifier.fillMaxWidth().padding(top = stageTop - 2.dp))
        RubHint(accent, frame, orb, Modifier.fillMaxWidth().padding(top = hintTop))
        Box(Modifier.align(Alignment.TopCenter).padding(top = hintTop + 34.dp).size(236.dp, 56.dp)) {
            WakeButton(route, cost, short, loaded, busy, accent, frame, orb, onClick = ::sail)
            if (counts.size > 1) {
                CountChip(counts, n, accent, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-14).dp)) {
                    if (it != count) {
                        Haptics.tick()
                        count = it
                    }
                }
            }
        }
    }

    opening?.let { st -> VoyageOpening(st, onDone = { if (st.draws != null) opening = null }) }
    if (adSheet) {
        AdSheet(
            busy = adBusy,
            onWatch = {
                adSheet = false
                claimAd()
            },
            onDismiss = { adSheet = false },
        )
    }
    if (keySheet) KeySheet(busy = busy, onBuy = ::buyKeys, onDismiss = { keySheet = false })
    if (oddsSheet) OddsSheet(initialRoute = route, onDismiss = { oddsSheet = false })
}

// ── 바탕 · 무대 ──────────────────────────────────────────────────────────

/**
 * 깊은 우주 바탕 — 남색 그라데이션 + 수정구 뒤 경로 빛(문지르는 동안 · 바뀔 때 수정구 색을 그대로 따라간다) + 보랏빛·푸른 성운 두 덩이
 * + 깜빡이는 잔별 + 8초마다 지나가는 별똥별. [deep] = 깨우기 창(단색 바탕 + 경로 빛 + 잔별만).
 * 시간은 수정구 시계([OrbEngine.t])라 앱이 뒤로 가면 같이 쉰다.
 */
internal fun DrawScope.drawSpace(orb: OrbEngine, sky: SkyStars, focusY: Float, deep: Boolean) {
    val t = orb.t
    val w = size.width
    val h = size.height
    if (deep) drawRect(Color(0xFF04060E))
    else drawRect(Brush.verticalGradient(0f to Color(0xFF03050D), 0.33f to Color(0xFF070B1E), 0.66f to Color(0xFF0A0B1C), 1f to Color(0xFF04050C)))
    val acc = Color(orb.glowColor)
    drawRect(
        Brush.radialGradient(
            0f to acc.copy(alpha = 0.17f), 0.5f to acc.copy(alpha = 0.05f), 1f to acc.copy(alpha = 0f),
            center = Offset(w / 2f, focusY), radius = w * 1.05f,
        ),
    )
    if (!deep) {
        drawRect(Brush.radialGradient(listOf(Color(0x1C7B5CFF), Color(0x007B5CFF)), Offset(w * 0.12f, h * 0.16f), w * 0.85f))
        drawRect(Brush.radialGradient(listOf(Color(0x143E7BFF), Color(0x003E7BFF)), Offset(w * 0.92f, h * 0.8f), w * 0.75f))
    }
    for (i in 0 until sky.size) {
        val tw = 0.5f + 0.5f * sin(t * sky.w[i] + sky.p[i])
        val p = Offset(sky.x[i] * w, sky.y[i] * h)
        if (sky.s[i] > 1.48f) drawSparkle(p, (2.2f + 1.6f * tw) * density, Color.White.copy(alpha = 0.35f + 0.5f * tw))
        else drawCircle(Color.White.copy(alpha = (0.1f + 0.45f * tw) * min(1f, sky.s[i] / 1.6f)), sky.s[i] * 0.75f * density, p)
    }
    if (!deep) {
        // 별똥별 — 8초마다 0.9초, 매번 다른 자리(위쪽 하늘)
        val idx = floor(t / 8f)
        val u = (t - idx * 8f) / 0.9f
        if (u in 0f..1f) {
            val seed = (sin(idx * 12.9898f) * 43758.547f).let { it - floor(it) }
            val sx = w * (0.15f + 0.6f * seed)
            val sy = h * (0.06f + 0.18f * ((seed * 7.31f) % 1f))
            val len = w * 0.28f
            val dx = len * 0.92f
            val dy = len * 0.38f
            val head = Offset(sx + dx * u, sy + dy * u)
            val tail = Offset(head.x - dx * 0.45f, head.y - dy * 0.45f)
            val fade = sin(u * PI.toFloat())
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * fade)), tail, head),
                tail, head, strokeWidth = 1.4f * density, cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 화면 전체 캔버스 — 바탕 + 마법진 · 은하수 · 수정구. 수정구 둘레(반지름 1.35배)에서 시작한 손가락만 받는다:
 * 짧게 누르면 누른 쪽으로 돌고, 끌면 문지르기(다 차면 [onRouteFlip]). 수정구 위의 손가락은 다른 곳(서랍 등)이 가져가지 않게 모두 먹는다.
 */
@Composable
private fun Stage(orb: OrbEngine, frame: State<Long>, k: Float, left: Dp, top: Dp, onRouteFlip: () -> Unit) {
    val sky = remember { SkyStars(seed = 11) }
    val flip by rememberUpdatedState(onRouteFlip)
    val density = LocalDensity.current
    val kp = k * density.density
    val ox = with(density) { left.toPx() }
    val oy = with(density) { top.toPx() }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(orb, kp, ox, oy) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val x0 = (down.position.x - ox) / kp
                    val y0 = (down.position.y - oy) / kp
                    if (hypot(x0 - orb.bx, y0 - orb.by) >= ORB_R * 1.35f) return@awaitEachGesture
                    down.consume()
                    val t0 = down.uptimeMillis
                    var tl = t0
                    var lx = x0
                    var ly = y0
                    var moved = 0f
                    var rub = false
                    try {
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                if (!rub && moved < RUB_SLOP && ch.uptimeMillis - t0 < 450) {
                                    orb.tap(x0, y0)
                                    Haptics.tick()
                                }
                                ch.consume()
                                break
                            }
                            val x = (ch.position.x - ox) / kp
                            val y = (ch.position.y - oy) / kp
                            val dist = hypot(x - lx, y - ly)
                            moved += dist
                            if (!rub && moved > RUB_SLOP) {
                                rub = true
                                orb.rubStart()
                            }
                            if (rub) {
                                val before = orb.rubP
                                if (orb.rubMove(x, y, dist, (ch.uptimeMillis - tl) / 1000f)) flip()
                                else if (floor(before * 4f) != floor(orb.rubP * 4f)) Haptics.tick()
                            }
                            lx = x
                            ly = y
                            tl = ch.uptimeMillis
                            ch.consume()
                        }
                    } finally {
                        if (rub) orb.rubEnd()
                    }
                }
            },
    ) {
        frame.value
        drawSpace(orb, sky, focusY = oy + ORB_CY * kp, deep = false)
        drawIntoCanvas { orb.draw(it.nativeCanvas, kp, ox, oy, size.width, size.height) }
    }
}

// ── 제목(별자리 각인) · 안내 ───────────────────────────────────────────────

/** 네 꼭지 반짝이 — 변이 가운데에서 [k] 만큼 비켜 휜다(0 = 가운데를 지나는 가는 별). */
private fun sparklePath(cx: Float, cy: Float, r: Float, k: Float) = Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx + r * k, cy - r * k, cx + r, cy)
    quadraticTo(cx + r * k, cy + r * k, cx, cy + r)
    quadraticTo(cx - r * k, cy + r * k, cx - r, cy)
    quadraticTo(cx - r * k, cy - r * k, cx, cy - r)
    close()
}

/** CSS ease-in-out 근사(부드러운 계단). */
private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 제목 위 작은 별자리 — 가는 선으로 이은 별 여덟(하나는 반짝이)이 3.2초 주기로 따로따로 깜빡인다. 136×18. */
private val TitleStars = floatArrayOf(
    // x, y, 반지름, 시작 위상(초) — 반지름 0 = 반짝이(56, 5)
    3f, 14f, 0.9f, 0.4f,
    19f, 9f, 1.3f, 1.9f,
    37f, 12f, 0.8f, 0.9f,
    56f, 5f, 0f, 2.4f,
    76f, 8f, 1.1f, 1.2f,
    96f, 3f, 1.4f, 0.2f,
    115f, 10f, 0.85f, 2.9f,
    133f, 13f, 1.1f, 1.5f,
)

private fun DrawScope.drawTitleConstellation(t: Float, accent: Color) {
    val s = density
    val line = Path()
    for (i in 0 until TitleStars.size / 4) {
        val x = TitleStars[i * 4] * s
        val y = TitleStars[i * 4 + 1] * s
        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    drawPath(line, accent.copy(alpha = 0.45f), style = Stroke(0.6f * s))
    for (i in 0 until TitleStars.size / 4) {
        val x = TitleStars[i * 4] * s
        val y = TitleStars[i * 4 + 1] * s
        val r = TitleStars[i * 4 + 2]
        val u = ((t + TitleStars[i * 4 + 3]) % 3.2f) / 3.2f
        val a = 0.35f + 0.65f * (0.5f - 0.5f * cos(u * 2f * PI.toFloat()))
        if (r == 0f) drawPath(sparklePath(x, y, 3.8f * s, 0.105f), Color.White.copy(alpha = a))
        else drawCircle(Color.White.copy(alpha = a), r * s, Offset(x, y))
    }
}

/** 제목 A — 별자리 · "잠든 우주"(흰 → 경로 빛, 은은한 번짐) · 가는 장식선 · 지금 경로(바뀌면 0.6초 동안 떠오르며 바뀐다). */
@Composable
private fun StageTitle(route: String, accent: Color, frame: State<Long>, orb: OrbEngine, modifier: Modifier) {
    var shown by remember { mutableStateOf(route) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(route) {
        if (route == shown) return@LaunchedEffect
        shown = route
        swap.snapTo(0f)
        swap.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    val glow = with(LocalDensity.current) { 11.3.dp.toPx() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(136.dp, 18.dp)) {
            frame.value
            drawTitleConstellation(orb.t, accent)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.voyage_title),
            maxLines = 1,
            style = TextStyle(
                brush = Brush.verticalGradient(0f to Color.White, 0.3f to Color.White, 1f to lerp(Color.White, accent, 0.667f)),
                fontSize = 23.sp, lineHeight = 28.75.sp, fontWeight = FontWeight.Light, letterSpacing = 0.26.em,
                shadow = Shadow(accent.copy(alpha = 0.5f), blurRadius = glow),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp, 1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.42f)))))
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(9.dp)) { drawPath(sparklePath(center.x, center.y, size.minDimension / 2f, 0.08f), accent) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(46.dp, 1.dp).background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.Transparent))))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            routeName(shown),
            color = accent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.24.em, maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = swap.value
                translationY = (1f - swap.value) * 4.dp.toPx()
            },
        )
    }
}

/** 문지르는 손짓 — 물결 선이 2.6초마다 그려졌다가(앞에서부터) 지워진다. 22×10. */
private fun DrawScope.drawRubGlyph(t: Float, accent: Color) {
    val s = density
    val p = Path().apply {
        moveTo(1f * s, 6f * s)
        cubicTo(4f * s, 1f * s, 6f * s, 9f * s, 9f * s, 5f * s)
        cubicTo(12f * s, 1f * s, 14f * s, 1f * s, 16f * s, 5f * s)
        cubicTo(18f * s, 9f * s, 20f * s, 7f * s, 21f * s, 4f * s)
    }
    val u = (t % 2.6f) / 2.6f
    val off = when {
        u < 0.45f -> 26f * (1f - ease(u / 0.45f))
        u < 0.6f -> 0f
        else -> -26f * ease((u - 0.6f) / 0.4f)
    }
    val phase = ((off % 52f) + 52f) % 52f
    drawPath(
        p, accent,
        style = Stroke(1.4f * s, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f * s, 26f * s), phase * s)),
    )
}

@Composable
private fun RubHint(accent: Color, frame: State<Long>, orb: OrbEngine, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp, 10.dp)) {
            frame.value
            drawRubGlyph(orb.t, accent)
        }
        Spacer(Modifier.width(7.dp))
        Text(stringResource(R.string.voyage_rub_hint), color = Color(0xA8B8C0CC), fontSize = 12.sp, letterSpacing = 0.02.em)
    }
}

// ── 지갑 · 확률 ──────────────────────────────────────────────────────────

/** 바뀐 숫자를 0.6초 동안 굴려 보여 준다 — 처음 그릴 때와 지갑을 막 받았을 때([ready] 가 바뀔 때)는 바로. */
@Composable
private fun rolling(value: Int, ready: Boolean): Int {
    val anim = remember(ready) { Animatable(value.toFloat()) }
    LaunchedEffect(value, ready) {
        if (anim.targetValue != value.toFloat()) anim.animateTo(value.toFloat(), tween(600, easing = EaseOutCubic))
    }
    return anim.value.roundToInt()
}

/** 왼쪽 위 지갑 — 별가루 · 열쇠 두 줄(폭은 긴 쪽에 맞춘다). 줄 끝 금빛 단추: ▶ 광고 보고 별가루 받기 · + 열쇠 구매. */
@Composable
private fun Wallet(modifier: Modifier, adOff: Boolean, onAd: () -> Unit, onKeys: () -> Unit) {
    val loaded = StarlightStore.walletLoaded
    val dust = rolling(StarlightStore.stardust, loaded)
    val keys = rolling(QuestStore.keys, true)
    Column(modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WalletRow(
            icon = { StardustIcon(Modifier.size(18.dp)) },
            label = stringResource(R.string.starlight_stardust),
            value = if (loaded) fmt(dust) else "…",
            valueColor = Dust,
        ) {
            WalletButton(adOff, stringResource(R.string.voyage_ad_title), onAd) { c ->
                val s = density
                val ox = size.width / 2f - 5.5f * s
                val oy = size.height / 2f - 6f * s
                drawPath(
                    Path().apply {
                        moveTo(ox + 2f * s, oy + 1.2f * s)
                        lineTo(ox + 2f * s, oy + 10.8f * s)
                        lineTo(ox + 10f * s, oy + 6f * s)
                        close()
                    },
                    c,
                )
            }
        }
        WalletRow(
            icon = { CrystalKeyIcon(Modifier.size(18.dp)) },
            label = stringResource(R.string.starlight_keys),
            value = fmt(keys),
            valueColor = Ink,
        ) {
            WalletButton(false, stringResource(R.string.voyage_key_title), onKeys) { c ->
                val s = density
                val h = 4.5f * s
                drawLine(c, Offset(center.x, center.y - h), Offset(center.x, center.y + h), 2f * s, StrokeCap.Round)
                drawLine(c, Offset(center.x - h, center.y), Offset(center.x + h, center.y), 2f * s, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun WalletRow(icon: @Composable () -> Unit, label: String, value: String, valueColor: Color, button: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x09FFFFFF))), shape)
            .border(0.75.dp, Color(0x21FFFFFF), shape)
            .padding(start = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(7.dp))
        Text(label, color = Dim, fontSize = 12.sp, letterSpacing = 0.02.em, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(
            value, maxLines = 1, modifier = Modifier.padding(start = 21.dp),
            style = TextStyle(color = valueColor, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
        )
        Spacer(Modifier.width(13.dp))
        button()
    }
}

/** 누르는 동안 살짝 작아진다(시안 :active). */
@Composable
private fun pressScale(source: MutableInteractionSource, to: Float): Float {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) to else 1f, tween(120), label = "press")
    return s
}

/** CSS linear-gradient(각도) — [deg] 0 = 위로 · 90 = 오른쪽으로, 상자 [size] 에 맞춘 시작·끝점. */
private fun cssLinear(size: Size, deg: Float, vararg stops: Pair<Float, Color>, at: Offset = Offset.Zero): Brush {
    val a = Math.toRadians(deg.toDouble())
    val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
    val len = abs(size.width * d.x) + abs(size.height * d.y)
    val c = at + Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(*stops, start = c - d * (len / 2f), end = c + d * (len / 2f))
}

/** 지갑 줄 끝 금빛 동그란 단추(26) — [off] 면 흐린 유리. [glyph] 는 단추 가운데 그림(색을 받는다). */
@Composable
private fun WalletButton(off: Boolean, label: String, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val sc = pressScale(source, 0.9f)
    val ink = if (off) Dim else ButtonInk
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
            }
            .drawBehind {
                val r = size.minDimension / 2f
                if (off) {
                    drawCircle(Color(0x1AFFFFFF), r)
                    drawCircle(Color(0x1FFFFFFF), r - 0.375.dp.toPx(), style = Stroke(0.75.dp.toPx()))
                } else {
                    // 금빛 그림자(0 2 10, 35%)
                    val gc = center + Offset(0f, 2.dp.toPx())
                    val gr = r + 10.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(0f to Color(0x59FFBE5A), (r - 3.dp.toPx()) / gr to Color(0x59FFBE5A), 1f to Color(0x00FFBE5A), center = gc, radius = gr),
                        gr, gc,
                    )
                    drawCircle(cssLinear(size, 145f, 0f to Color(0xFFFFF0C2), 0.55f to Gold, 1f to Color(0xFFFFB259)), r)
                    // 위쪽 안쪽 빛 테
                    val inset = 0.5.dp.toPx()
                    drawArc(
                        Color.White.copy(alpha = 0.7f), 205f, 130f, false,
                        topLeft = Offset(inset, inset + 0.5.dp.toPx()), size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                glyph(ink)
            }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = label, role = Role.Button) {
                Haptics.tick()
                onClick()
            },
    )
}

/** 오른쪽 위 작은 "확률" 칩. */
@Composable
private fun OddsChip(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(28.dp)
            .clip(shape)
            .background(Color(0x12FFFFFF))
            .border(0.75.dp, Color(0x26FFFFFF), shape)
            .clickable {
                Haptics.tick()
                onClick()
            }
            .padding(start = 8.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, null, tint = Sub, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.voyage_odds), color = Sub, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

// ── 깨우기 · 횟수 ──────────────────────────────────────────────────────────

/**
 * 깨우기 — 경로 빛 캡슐(경로를 바꾸면 0.6초 동안 색이 넘어간다)에 "깨우기 | ✦ 값", 4.6초마다 빛이 비스듬히 스친다.
 * 모자라면 흐린 유리에 얼마 모자란지만.
 */
@Composable
private fun WakeButton(
    route: String, cost: Int, short: Int, loaded: Boolean, busy: Boolean, accent: Color,
    frame: State<Long>, orb: OrbEngine, onClick: () -> Unit,
) {
    val ready = loaded && short <= 0
    val rare by animateFloatAsState(if (route == "rare") 1f else 0f, tween(600), label = "wake_rare")
    val ink = lerp(NormalTint.ink, RareTint.ink, rare)
    val source = remember { MutableInteractionSource() }
    val sc = pressScale(source, 0.97f)
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
            }
            .drawBehind {
                if (!ready) return@drawBehind
                // 경로 빛 그림자(0 12 30 -8, 60%) — 버튼 아래로 번지는 빛
                val c = Offset(center.x, center.y + 12.dp.toPx())
                val rx = size.width / 2f + 22.dp.toPx()
                val ry = size.height / 2f + 22.dp.toPx()
                scale(1f, ry / rx, pivot = c) {
                    drawCircle(
                        Brush.radialGradient(0f to accent.copy(alpha = 0.5f), 0.6f to accent.copy(alpha = 0.3f), 1f to accent.copy(alpha = 0f), center = c, radius = rx),
                        rx, c,
                    )
                }
            }
            .clip(shape)
            .drawWithContent {
                if (ready) {
                    drawRect(cssLinear(size, 135f, 0f to NormalTint.light, 1f to NormalTint.deep))
                    if (rare > 0f) drawRect(cssLinear(size, 135f, 0f to RareTint.light, 1f to RareTint.deep), alpha = rare)
                    // 비스듬히 스치는 빛 — 1.2초 쉬고 4.6초마다(그중 45% 동안 지나간다)
                    frame.value
                    val cyc = orb.t - 1.2f
                    if (cyc >= 0f) {
                        val u = (cyc % 4.6f) / 4.6f
                        if (u < 0.45f) {
                            val bw = size.width * 0.6f
                            val left = size.width * (-0.7f + 2f * ease(u / 0.45f))
                            val band = Size(bw, size.height)
                            drawRect(
                                cssLinear(band, 105f, 0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.5f), 1f to Color.Transparent, at = Offset(left, 0f)),
                                topLeft = Offset(left, 0f), size = band,
                            )
                        }
                    }
                    // 위쪽 안쪽 빛 테
                    drawRoundRect(
                        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.7f), (2.dp.toPx() / size.height) to Color.Transparent),
                        cornerRadius = CornerRadius(size.height / 2f), style = Stroke(2.dp.toPx()),
                    )
                } else {
                    drawRect(Color(0x14FFFFFF))
                    drawRoundRect(
                        Color(0x1FFFFFFF), topLeft = Offset(0.375.dp.toPx(), 0.375.dp.toPx()),
                        size = Size(size.width - 0.75.dp.toPx(), size.height - 0.75.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2f), style = Stroke(0.75.dp.toPx()),
                    )
                }
                drawContent()
            }
            .clickable(interactionSource = source, indication = null, enabled = ready && !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !loaded -> Text("…", color = Sub, fontSize = 16.sp)
            short > 0 -> Text(
                stringResource(R.string.voyage_short, short), color = Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 0.02.em, maxLines = 1,
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voyage_launch), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(16.dp).background(ink.copy(alpha = 0.25f)))
                Spacer(Modifier.width(12.dp))
                StardustIcon(Modifier.size(14.dp), ink)
                Spacer(Modifier.width(4.dp))
                Text(fmt(cost), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
    }
}

/** 깨우기 오른쪽 위에 걸친 작은 ×1 · ×10 — 어두운 캡슐 안 고른 칸만 경로 빛. */
@Composable
private fun CountChip(counts: List<Int>, value: Int, accent: Color, modifier: Modifier, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .shadow(8.dp, shape, clip = false)
            .background(Color(0xEB090C18), shape)
            .border(0.75.dp, Color(0x2EFFFFFF), shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        counts.forEach { c ->
            key(c) {
                val sel by animateFloatAsState(if (c == value) 1f else 0f, tween(200), label = "count_sel")
                val bs = RoundedCornerShape(11.dp)
                Box(
                    Modifier
                        .height(22.dp)
                        .widthIn(min = 34.dp)
                        .clip(bs)
                        .background(accent.copy(alpha = 0.26f * sel))
                        .border(0.75.dp, accent.copy(alpha = 0.62f * sel), bs)
                        .clickable { onPick(c) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "×$c",
                        style = TextStyle(color = lerp(Dim, Color.White, sel), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}

// ── 창(광고 · 열쇠) ────────────────────────────────────────────────────────

/** 시안 창 — 가운데 290 폭, 어두운 남색 유리, 0.28초 동안 통 튀며 나타난다. */
@Composable
private fun PopSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, tween(280, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f))) }
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .width(290.dp)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF0202640), Color(0xF50E111E))))
                .border(0.75.dp, Color(0x24FFFFFF), shape)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** 창 맨 위 그림 — 동그란 빛(64) 위에 아이콘. */
@Composable
private fun SheetArt(glow: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .drawBehind { drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center, size.minDimension * 0.495f)) },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 금빛 큰 단추(48) — 쓸 수 없으면 흐린 유리 + 흐린 글자. */
@Composable
private fun GoldButton(enabled: Boolean, onClick: () -> Unit, content: @Composable RowScope.(Color) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val ink = if (enabled) ButtonInk else Sub
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .drawBehind {
                if (enabled) drawRect(cssLinear(size, 135f, 0f to Color(0xFFFFF0C2), 1f to Color(0xFFFFC75A)))
                else drawRect(Color(0x1AFFFFFF))
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { content(ink) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Sub, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

/** 광고 보고 별가루 받기 — 받을 양 · 오늘 남은 횟수. 광고 제거 계정은 광고 없이 바로 받는다. */
@Composable
private fun AdSheet(busy: Boolean, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val cfg = StarlightStore.config
    val loaded = StarlightStore.walletLoaded
    val left = StarlightStore.adsLeft
    val adFree = AdFreeAccount.active
    PopSheet(onDismiss) {
        SheetArt(Color(0x47FFD68C)) { StardustIcon(Modifier.size(38.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (adFree) R.string.voyage_ad_title_free else R.string.voyage_ad_title),
            color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text("+${cfg.adStardust}", style = TextStyle(color = Dust, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                !loaded -> "…"
                left > 0 -> stringResource(R.string.voyage_ad_today_left, left)
                else -> stringResource(R.string.voyage_ad_limit)
            },
            color = Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        GoldButton(enabled = loaded && left > 0 && !busy, onClick = onWatch) { ink ->
            if (busy) {
                Text("…", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
                if (!adFree) {
                    Canvas(Modifier.size(11.dp, 12.dp)) {
                        val s = density
                        drawPath(
                            Path().apply {
                                moveTo(2f * s, 1.2f * s)
                                lineTo(2f * s, 10.8f * s)
                                lineTo(10f * s, 6f * s)
                                close()
                            },
                            ink,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(if (adFree) R.string.voyage_ad_claim else R.string.voyage_ad_watch),
                    color = ink, fontSize = 15.sp, fontWeight = if (ink == ButtonInk) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
        GhostButton(stringResource(R.string.cd_close), onDismiss)
    }
}

/** 열쇠 구매 — 개수를 고르고 별가루로 산다(모자라면 얼마 모자란지). */
@Composable
private fun KeySheet(busy: Boolean, onBuy: (Int) -> Unit, onDismiss: () -> Unit) {
    val cfg = StarlightStore.config
    val each = cfg.keyCost.coerceAtLeast(1)
    val cap = cfg.maxExchange.coerceAtLeast(1)
    var n by remember { mutableIntStateOf(1) }
    val total = each * n
    val short = (total - StarlightStore.stardust).coerceAtLeast(0)
    PopSheet(onDismiss) {
        SheetArt(Color(0x47A8B8FF)) { CrystalKeyIcon(Modifier.size(36.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.voyage_key_title), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.voyage_key_each, each), color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Filled.Remove, enabled = n > 1) { n-- }
            Text(
                n.toString(), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp),
                style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
            StepButton(Icons.Filled.Add, enabled = n < cap) { n++ }
        }
        Spacer(Modifier.height(18.dp))
        GoldButton(enabled = short <= 0 && !busy, onClick = { onBuy(n) }) { ink ->
            when {
                short > 0 -> Text(stringResource(R.string.voyage_short, short), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                busy -> Text("…", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                else -> {
                    Text(stringResource(R.string.voyage_buy), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    StardustIcon(Modifier.size(13.dp), ink)
                    Spacer(Modifier.width(8.dp))
                    Text(fmt(total), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                }
            }
        }
        GhostButton(stringResource(R.string.cd_close), onDismiss)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .border(0.75.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(enabled = enabled) {
                Haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Ink, modifier = Modifier.size(20.dp)) }
}

// ── 확률(작은 칩으로만 연다) ───────────────────────────────────────────────

/**
 * 확률 안내 — 경로마다 등급 확률 · 그 등급에 든 아이템 수(n종) · 하나당 확률, 그리고 규칙(같은 등급은 같은 확률 · 매번 독립 · 중복 환급).
 * 확률형 아이템 표시 기준에 맞춰 **모든 확률을 숫자로** 보여 준다(아이템 이름은 숨긴다 — 무엇이 나올지는 깨워 봐야 안다).
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
