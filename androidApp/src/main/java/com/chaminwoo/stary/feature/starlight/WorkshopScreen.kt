package com.chaminwoo.stary.feature.starlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.quest.QuestEvent
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.launch
import java.util.Locale

internal val Ink = Color(0xFFF0F0F0)
internal val Sub = Color(0xFFB8C0CC)
internal val Dim = Color(0xFF7D8696)
internal val Card = Color(0x14FFFFFF)
internal val Line = Color(0x1AFFFFFF)
internal val Dust = Color(0xFFFFE6A8)
internal val Gold = Color(0xFFFFD27A)
internal val Violet = Color(0xFFB47CFF)

/**
 * 별빛 공방(2026-10-22) — 별가루를 열쇠·성운 상자로 바꾸고, 상자를 연다. 모은 것은 [CollectionScreen](수집품)에서.
 * 서버가 모든 계산을 한다([StarlightStore.request]) — 이 화면은 요청을 보내고 결과를 연출만 한다([NebulaOpening]).
 * iOS `WorkshopScreen.swift` 패리티.
 */
@Composable
fun WorkshopScreen(onOpenCollection: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = StarlightStore.config
    var busy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<OpeningState?>(null) }
    var keyCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) { StaryAnalytics.log("workshop_open", "stardust" to StarlightStore.stardust, "boxes" to StarlightStore.boxes) }

    fun fail(code: String) {
        Haptics.light()
        StaryToast.show(
            context.getString(
                when (code) {
                    "insufficient" -> R.string.starlight_err_insufficient
                    "no_boxes" -> R.string.starlight_err_no_boxes
                    "empty_pool" -> R.string.starlight_err_empty_pool
                    "timeout" -> R.string.starlight_err_timeout
                    else -> R.string.starlight_err_generic
                }
            )
        )
    }

    fun exchange(to: String, count: Int) {
        if (busy) return
        busy = true
        scope.launch {
            val r = StarlightStore.request("exchange", mapOf("to" to to, "count" to count))
            busy = false
            when (r) {
                is StarlightStore.OpResult.Done -> {
                    Haptics.celebrate()
                    StaryAnalytics.log("workshop_exchange", "to" to to, "count" to count)
                    StaryToast.show(
                        if (to == "key") context.getString(R.string.starlight_made_keys, count)
                        else context.getString(R.string.starlight_made_box, count)
                    )
                    keyCount = 1
                }
                is StarlightStore.OpResult.Failed -> fail(r.code)
            }
        }
    }

    fun open(count: Int) {
        if (busy || count <= 0) return
        busy = true
        opening = OpeningState(count = count)
        scope.launch {
            val r = StarlightStore.request("openBox", mapOf("count" to count))
            busy = false
            when (r) {
                is StarlightStore.OpResult.Done -> {
                    val draws = StarlightStore.draws(r.result)
                    StaryAnalytics.log(
                        "workshop_open_box", "count" to count,
                        "best" to (draws.maxByOrNull { StarlightStore.RARITIES.indexOf(it.rarity) }?.rarity ?: ""),
                    )
                    QuestStore.report(QuestEvent.OPEN_BOX)
                    opening = opening?.copy(draws = draws)
                }
                is StarlightStore.OpResult.Failed -> {
                    opening = null
                    fail(r.code)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = LocalTopBarInset.current)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BalanceCard()

        // ── 상자 열기(대표 전설 + 안내) ──
        OpenCard(busy = busy, onOpen = ::open)

        // ── 바꾸기 ──
        SectionTitle(stringResource(R.string.starlight_exchange_title))
        ExchangeRow(
            art = { CrystalKeyIcon(Modifier.size(28.dp)) },
            title = stringResource(R.string.starlight_to_key),
            desc = stringResource(R.string.starlight_to_key_desc),
            cost = cfg.keyCost * keyCount,
            enabled = !busy && StarlightStore.stardust >= cfg.keyCost * keyCount,
            stepper = {
                Stepper(
                    value = keyCount,
                    onChange = { keyCount = it },
                    max = (StarlightStore.stardust / cfg.keyCost.coerceAtLeast(1)).coerceIn(1, 99),
                )
            },
            onMake = { exchange("key", keyCount) },
        )
        ExchangeRow(
            art = { NebulaBoxArt(Modifier.size(40.dp), animated = false) },
            title = stringResource(R.string.starlight_to_box),
            desc = stringResource(R.string.starlight_to_box_desc),
            cost = cfg.boxCost,
            enabled = !busy && StarlightStore.stardust >= cfg.boxCost,
            onMake = { exchange("box", 1) },
        )

        // ── 도감 요약(목록은 수집품 화면에서) ──
        CollectionSummary(onOpenCollection)

        // ── 확률 안내 ──
        OddsCard()
        Spacer(Modifier.height(24.dp))
    }

    opening?.let { st ->
        NebulaOpening(st, onDone = { if (st.draws != null) opening = null })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BalanceCard() {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF221B3D), Color(0xFF14192A))))
            .border(1.dp, Brush.linearGradient(listOf(Dust.copy(alpha = 0.55f), Violet.copy(alpha = 0.35f))), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StardustIcon(Modifier.size(34.dp), Dust)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.starlight_stardust), color = Sub, fontSize = 12.sp)
            Text(
                if (StarlightStore.walletLoaded) StarlightStore.stardust.toString() else "…",
                style = TextStyle(color = Dust, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniBalance({ NebulaBoxArt(Modifier.size(20.dp), animated = false) }, StarlightStore.boxes, stringResource(R.string.starlight_box))
            MiniBalance({ CrystalKeyIcon(Modifier.size(17.dp)) }, QuestStore.keys, stringResource(R.string.starlight_keys))
        }
    }
}

@Composable
private fun MiniBalance(icon: @Composable () -> Unit, value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dim, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        icon()
        Spacer(Modifier.width(4.dp))
        Text(value.toString(), style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun ExchangeRow(
    art: @Composable () -> Unit,
    title: String,
    desc: String,
    cost: Int,
    enabled: Boolean,
    onMake: () -> Unit,
    stepper: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(0.75.dp, Line, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF26304A), Color(0xFF161C2C)))),
                contentAlignment = Alignment.Center,
            ) { art() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = Dim, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stepper != null) stepper()
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clickBounce()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (enabled) Brush.linearGradient(listOf(Dust, Color(0xFFFFB259)))
                        else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF)))
                    )
                    .clickable(enabled = enabled, onClick = onMake)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StardustIcon(Modifier.size(14.dp), if (enabled) Color(0xFF2A1A00) else Dim)
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.starlight_make_cost, cost),
                    color = if (enabled) Color(0xFF2A1A00) else Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, max: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clickable(enabled = value > 1) { Haptics.tick(); onChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Remove, null, tint = if (value > 1) Ink else Dim, modifier = Modifier.size(16.dp)) }
        Text(
            value.toString(),
            style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            modifier = Modifier.widthIn(min = 22.dp), textAlign = TextAlign.Center,
        )
        Box(
            Modifier.size(34.dp).clickable(enabled = value < max) { Haptics.tick(); onChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = if (value < max) Ink else Dim, modifier = Modifier.size(16.dp)) }
    }
}

/**
 * 상자 열기 카드 — 위에 **상자 속 전설 3개를 부채꼴로 살짝 겹쳐** 보여 주고(DB `showcase`), 그 앞에 성운 상자,
 * 아래에 안내 문구와 열기 버튼.
 */
@Composable
private fun OpenCard(busy: Boolean, onOpen: (Int) -> Unit) {
    val boxes = StarlightStore.boxes
    val many = boxes.coerceAtMost(StarlightStore.config.maxOpen)
    val shape = RoundedCornerShape(24.dp)
    val showcase = StarlightStore.showcaseItems
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF241A45), Color(0xFF151230), Color(0xFF0F1322))))
            .border(1.dp, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.45f), Violet.copy(alpha = 0.3f))), shape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.starlight_lineup),
            color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.TopCenter) {
            // 부채꼴 전설 카드(양옆 → 가운데 순으로 겹친다)
            val slots = listOf(Triple(-84, 18, -13f), Triple(84, 18, 13f), Triple(0, 0, 0f))
            val order = listOf(0, 2, 1) // showcase[0] 왼쪽, [1] 가운데, [2] 오른쪽
            slots.forEachIndexed { k, (dx, dy, rot) ->
                val item = showcase.getOrNull(order[k]) ?: return@forEachIndexed
                ShowcaseCard(
                    item,
                    Modifier
                        .offset(x = dx.dp, y = dy.dp)
                        .graphicsLayer { rotationZ = rot },
                )
            }
            NebulaBoxArt(Modifier.align(Alignment.BottomCenter).size(150.dp), glow = if (boxes > 0) 0.3f else 0f)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.starlight_showcase),
            color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (boxes > 0) stringResource(R.string.starlight_boxes_ready, boxes)
            else stringResource(R.string.starlight_no_box, StarlightStore.config.boxCost),
            color = Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpenButton(stringResource(R.string.starlight_open_one), enabled = !busy && boxes >= 1, primary = true) { onOpen(1) }
            if (many >= 2) {
                OpenButton(stringResource(R.string.starlight_open_many, many), enabled = !busy, primary = false) { onOpen(many) }
            }
        }
    }
}

/** 상자 속 전설 한 장 — 금테 크리스탈 카드. */
@Composable
private fun ShowcaseCard(item: StarlightStore.Item, modifier: Modifier = Modifier) {
    val c = rarityColor(item.rarity)
    val shape = RoundedCornerShape(16.dp)
    val glow by rememberInfiniteTransition(label = "showcase").animateFloat(
        0.45f, 0.8f, infiniteRepeatable(tween(4_200), RepeatMode.Reverse), label = "showcase_rim",
    )
    Column(
        modifier
            .width(98.dp)
            .height(128.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2A1A), Color(0xFF1C1830), Color(0xFF121626))))
            .border(1.2.dp, Brush.verticalGradient(listOf(c.copy(alpha = glow), c.copy(alpha = 0.25f))), shape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemArt(item, Modifier.size(70.dp))
        Spacer(Modifier.weight(1f))
        Text(
            item.nameIn(starlightLang()), color = Ink, fontSize = 10.sp, lineHeight = 13.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

@Composable
internal fun OpenButton(text: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = when {
            !enabled -> Dim
            primary -> Color(0xFF1A0B33)
            else -> Ink
        },
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickBounce()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF)))
                    primary -> Brush.linearGradient(listOf(Color(0xFFE2C8FF), Violet))
                    else -> Brush.linearGradient(listOf(Color(0x33B47CFF), Color(0x22B47CFF)))
                }
            )
            .border(0.75.dp, if (primary || !enabled) Color.Transparent else Violet.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

/** 도감 요약 — 모은 수와 진행 막대, 누르면 수집품 화면. */
@Composable
private fun CollectionSummary(onOpen: () -> Unit) {
    val items = StarlightStore.shownItems
    val owned = StarlightStore.owned
    val ownedCount = items.count { (owned[it.id] ?: 0) > 0 }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(0.75.dp, Line, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.starlight_collection), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.starlight_collection_desc), color = Dim, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.starlight_collection_open), color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Violet, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val frac = if (items.isEmpty()) 0f else ownedCount / items.size.toFloat()
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0x1FFFFFFF))) {
                Box(
                    Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Violet, Gold))),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.starlight_collected, ownedCount, items.size),
                style = TextStyle(color = Sub, fontSize = 12.sp, fontFeatureSettings = "tnum"),
            )
        }
    }
}

// ── 확률 안내 ─────────────────────────────────────────────────────────────

@Composable
private fun OddsCard() {
    val cfg = StarlightStore.config
    val shape = RoundedCornerShape(16.dp)
    var expanded by remember { mutableStateOf(false) }
    val lang = starlightLang()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(0.75.dp, Line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.starlight_odds_title), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        StarlightStore.RARITIES.reversed().forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(rarityColor(r)))
                Spacer(Modifier.width(8.dp))
                Text(rarityName(r), color = Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(pct(cfg.percent(r)), style = TextStyle(color = Ink, fontSize = 12.sp, fontFeatureSettings = "tnum"))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.starlight_pity, cfg.pityEpic, cfg.pityLegendary), color = Dim, fontSize = 11.sp, lineHeight = 16.sp)
        if (StarlightStore.walletLoaded) {
            Text(
                stringResource(R.string.starlight_pity_now, StarlightStore.sinceEpic, StarlightStore.sinceLegendary),
                color = Dim, fontSize = 11.sp, lineHeight = 16.sp,
            )
        }
        Text(
            stringResource(
                R.string.starlight_dup_rule,
                cfg.dupRefund["common"] ?: 0, cfg.dupRefund["rare"] ?: 0, cfg.dupRefund["epic"] ?: 0, cfg.dupRefund["legendary"] ?: 0,
            ),
            color = Dim, fontSize = 11.sp, lineHeight = 16.sp,
        )
        // 아이템별 확률(접어 둔다) — 같은 등급 안에서는 모두 같은 확률.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (expanded) R.string.starlight_odds_hide else R.string.starlight_odds_items),
                color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Violet, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val pool = StarlightStore.items.filter { it.active }
                StarlightStore.RARITIES.reversed().forEach { r ->
                    val group = pool.filter { it.rarity == r }
                    if (group.isEmpty()) return@forEach
                    val each = cfg.percent(r) / group.size
                    Text(rarityName(r), color = rarityColor(r), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    group.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.nameIn(lang)} · ${kindName(item.kind)}", color = Sub, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(pct(each), style = TextStyle(color = Dim, fontSize = 11.sp, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
        }
    }
}

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

internal fun pct(v: Double): String =
    if (v >= 10) String.format(Locale.US, "%.0f%%", v)
    else if (v >= 1) String.format(Locale.US, "%.1f%%", v)
    else String.format(Locale.US, "%.2f%%", v)
