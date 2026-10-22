package com.chaminwoo.stary.feature.starlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFFF0F0F0)
private val Sub = Color(0xFFB8C0CC)
private val Dim = Color(0xFF7D8696)
private val Card = Color(0x14FFFFFF)
private val Line = Color(0x1AFFFFFF)
private val Dust = Color(0xFFFFE6A8)
private val Gold = Color(0xFFFFD27A)
private val Violet = Color(0xFFB47CFF)

/**
 * 별빛 공방(2026-10-22) — 별가루를 열쇠·성운 상자로 바꾸고, 상자를 열고, 모은 것을 본다.
 * 서버가 모든 계산을 한다([StarlightStore.request]) — 이 화면은 요청을 보내고 결과를 연출만 한다. iOS `WorkshopView.swift` 패리티.
 */
@Composable
fun WorkshopScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = StarlightStore.config
    var busy by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf<RevealState?>(null) }
    var keyCount by remember { mutableIntStateOf(1) }
    var detail by remember { mutableStateOf<StarlightStore.Item?>(null) }

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
        reveal = RevealState(count = count)
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
                    reveal = reveal?.copy(draws = draws)
                }
                is StarlightStore.OpResult.Failed -> {
                    reveal = null
                    fail(r.code)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.starlight_subtitle), color = Sub, fontSize = 13.sp, lineHeight = 19.sp)
        BalanceCard()

        // ── 바꾸기 ──
        SectionTitle(stringResource(R.string.starlight_exchange_title))
        ExchangeRow(
            art = { Icon(Icons.Filled.Key, null, tint = Gold, modifier = Modifier.size(26.dp)) },
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
            art = { NebulaBoxArt(Modifier.size(34.dp), spin = false) },
            title = stringResource(R.string.starlight_to_box),
            desc = stringResource(R.string.starlight_to_box_desc),
            cost = cfg.boxCost,
            enabled = !busy && StarlightStore.stardust >= cfg.boxCost,
            onMake = { exchange("box", 1) },
        )

        // ── 상자 열기 ──
        SectionTitle(stringResource(R.string.starlight_open_title))
        OpenCard(busy = busy, onOpen = ::open)

        // ── 도감 ──
        Collection(onItem = { detail = it })

        // ── 확률 안내 ──
        OddsCard()
        Spacer(Modifier.height(24.dp))
    }

    reveal?.let { st ->
        RevealDialog(st, onDone = { if (st.draws != null) reveal = null })
    }
    detail?.let { item -> ItemDetailDialog(item, onDismiss = { detail = null }) }
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
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
            MiniBalance({ NebulaBoxArt(Modifier.size(18.dp), spin = false) }, StarlightStore.boxes, stringResource(R.string.starlight_box))
            MiniBalance({ Icon(Icons.Filled.Key, null, tint = Gold, modifier = Modifier.size(15.dp)) }, QuestStore.keys, stringResource(R.string.starlight_keys))
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
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF26304A), Color(0xFF161C2C)))),
                contentAlignment = Alignment.Center,
            ) { art() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = Dim, fontSize = 11.5.sp, lineHeight = 16.sp)
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

@Composable
private fun OpenCard(busy: Boolean, onOpen: (Int) -> Unit) {
    val boxes = StarlightStore.boxes
    val many = boxes.coerceAtMost(StarlightStore.config.maxOpen)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1533), Color(0xFF111622))))
            .border(0.75.dp, Violet.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NebulaBoxArt(Modifier.size(132.dp), glow = if (boxes > 0) 0.25f else 0f)
        Spacer(Modifier.height(4.dp))
        Text(
            if (boxes > 0) stringResource(R.string.starlight_boxes_ready, boxes)
            else stringResource(R.string.starlight_no_box, StarlightStore.config.boxCost),
            color = Sub, fontSize = 12.5.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpenButton(stringResource(R.string.starlight_open_one), enabled = !busy && boxes >= 1, primary = true) { onOpen(1) }
            if (many >= 2) {
                OpenButton(stringResource(R.string.starlight_open_many, many), enabled = !busy, primary = false) { onOpen(many) }
            }
        }
    }
}

@Composable
private fun OpenButton(text: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
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

// ── 도감 ──────────────────────────────────────────────────────────────────

@Composable
private fun Collection(onItem: (StarlightStore.Item) -> Unit) {
    val items = StarlightStore.shownItems
    if (items.isEmpty()) return
    val owned = StarlightStore.owned
    val ownedCount = items.count { (owned[it.id] ?: 0) > 0 }
    Row(verticalAlignment = Alignment.Bottom) {
        SectionTitle(stringResource(R.string.starlight_collection))
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.starlight_collected, ownedCount, items.size), color = Dim, fontSize = 12.sp)
    }
    val cfg = StarlightStore.config
    StarlightStore.RARITIES.reversed().forEach { rarity ->
        val group = items.filter { it.rarity == rarity }
        if (group.isEmpty()) return@forEach
        val each = cfg.percent(rarity) / group.size
        Text(
            "${rarityName(rarity)} · ${pct(cfg.percent(rarity))}",
            color = rarityColor(rarity), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        group.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    ItemCell(item, owned = (owned[item.id] ?: 0) > 0, percent = each, onClick = { onItem(item) }, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ItemCell(item: StarlightStore.Item, owned: Boolean, percent: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = rarityColor(item.rarity)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (owned) c.copy(alpha = 0.10f) else Card)
            .border(0.75.dp, if (owned) c.copy(alpha = 0.55f) else Line, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (owned) 1f else 0.45f), contentAlignment = Alignment.Center) {
            ItemArt(item, Modifier.fillMaxSize(0.82f))
            if (owned) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(c),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Color(0xFF111120), modifier = Modifier.size(10.dp)) }
            }
        }
        Text(
            item.nameIn(starlightLang()), color = if (owned) Ink else Sub, fontSize = 10.5.sp, lineHeight = 13.sp,
            textAlign = TextAlign.Center, maxLines = 2,
        )
        val slot = slotOf(item.kind)
        if (slot != null && isEquipped(LocalContext.current, slot, item.id)) {
            Text(stringResource(R.string.starlight_equipped), color = c, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(pct(percent), style = TextStyle(color = Dim, fontSize = 9.5.sp, fontFeatureSettings = "tnum"))
        }
    }
}

/** 장착 칸 — 프레임·배지·오라·칭호만(나머지는 null). */
private fun slotOf(kind: String): StarlightStore.Slot? = StarlightStore.Slot.entries.firstOrNull { it.kind == kind }

@Composable
private fun ItemDetailDialog(item: StarlightStore.Item, onDismiss: () -> Unit) {
    val lang = starlightLang()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = rarityColor(item.rarity)
    val count = StarlightStore.owned[item.id] ?: 0
    val slot = slotOf(item.kind)
    var busy by remember { mutableStateOf(false) }
    val equipped = slot != null && isEquipped(context, slot, item.id)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ItemArt(item, Modifier.size(110.dp))
            Spacer(Modifier.height(10.dp))
            Text("${rarityName(item.rarity)} · ${kindName(item.kind)}", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(item.nameIn(lang), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(item.descIn(lang), color = Sub, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            if (count > 0) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.starlight_owned_n, count), color = c, fontSize = 12.sp)
                usageNote(item)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(it), color = Dim, fontSize = 11.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                }
                if (slot != null) {
                    Spacer(Modifier.height(14.dp))
                    OpenButton(
                        stringResource(if (equipped) R.string.starlight_unequip else R.string.starlight_equip),
                        enabled = !busy, primary = !equipped,
                    ) {
                        busy = true
                        scope.launch {
                            val r = StarlightStore.equip(slot, if (equipped) null else item.id)
                            busy = false
                            when (r) {
                                is StarlightStore.OpResult.Done -> {
                                    if (slot == StarlightStore.Slot.TITLE) syncTitle(context, if (equipped) null else item.id)
                                    Haptics.medium()
                                    StaryAnalytics.log("workshop_equip", "slot" to slot.id, "item" to (if (equipped) "" else item.id))
                                    StaryToast.show(context.getString(if (equipped) R.string.starlight_unequip_done else R.string.starlight_equip_done))
                                }
                                is StarlightStore.OpResult.Failed -> StaryToast.show(
                                    context.getString(if (r.code == "timeout") R.string.starlight_err_timeout else R.string.starlight_err_generic)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 장착 중인가. 칭호는 업적 칭호와 한 자리를 나눠 쓰므로 실제로 달려 있는 값(StigmaStore = users.equippedTitle 의 기기 사본)이
 * "sl:{id}" 일 때만 — 공방 칭호를 단 뒤 업적 칭호로 바꾸면 starlightEquips.title 은 옛값으로 남는다.
 */
private fun isEquipped(context: android.content.Context, slot: StarlightStore.Slot, id: String): Boolean {
    val mine = StarlightStore.myEquips.of(slot) == id // 구독 상태를 읽어 장착이 바뀌면 다시 그린다
    if (slot != StarlightStore.Slot.TITLE) return mine
    val uid = GoogleAuthHelper.currentUserId ?: return false
    return StigmaStore.equipped(context, uid) == StarlightStore.TITLE_PREFIX + id
}

/** 서버가 users.equippedTitle 을 바꿨으니 기기 사본(StigmaStore)도 맞춘다 — 업적 화면·프로필이 이걸 읽는다. */
private fun syncTitle(context: android.content.Context, id: String?) {
    val uid = GoogleAuthHelper.currentUserId ?: return
    val cur = StigmaStore.equipped(context, uid)
    when {
        id != null -> StigmaStore.equip(context, uid, StarlightStore.TITLE_PREFIX + id)
        cur?.startsWith(StarlightStore.TITLE_PREFIX) == true -> StigmaStore.equip(context, uid, null)
    }
}

/** 가진 아이템을 어디서 쓰는지(장착형은 버튼으로 충분해 null). */
private fun usageNote(item: StarlightStore.Item): Int? = when (item.kind) {
    "shape" -> R.string.starlight_use_shape
    "color" -> R.string.starlight_use_color
    "telescope" -> if (item.payload["mode"] == "reveal") R.string.starlight_use_reveal else R.string.starlight_use_hint
    else -> null
}

// ── 확률 안내 ─────────────────────────────────────────────────────────────

@Composable
private fun OddsCard() {
    val cfg = StarlightStore.config
    val shape = RoundedCornerShape(16.dp)
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
                Text(rarityName(r), color = Sub, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Text(pct(cfg.percent(r)), style = TextStyle(color = Ink, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.starlight_pity, cfg.pityEpic, cfg.pityLegendary), color = Dim, fontSize = 11.5.sp, lineHeight = 16.sp)
        if (StarlightStore.walletLoaded) {
            Text(
                stringResource(R.string.starlight_pity_now, StarlightStore.sinceEpic, StarlightStore.sinceLegendary),
                color = Dim, fontSize = 11.5.sp, lineHeight = 16.sp,
            )
        }
        Text(
            stringResource(
                R.string.starlight_dup_rule,
                cfg.dupRefund["common"] ?: 0, cfg.dupRefund["rare"] ?: 0, cfg.dupRefund["epic"] ?: 0, cfg.dupRefund["legendary"] ?: 0,
            ),
            color = Dim, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
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

private fun pct(v: Double): String =
    if (v >= 10) String.format(Locale.US, "%.0f%%", v)
    else if (v >= 1) String.format(Locale.US, "%.1f%%", v)
    else String.format(Locale.US, "%.2f%%", v)

// ── 상자 열기 연출 ────────────────────────────────────────────────────────

/** [draws] = null 이면 서버 응답을 기다리는 중(상자가 떨며 빛을 모은다). */
private data class RevealState(val count: Int, val draws: List<StarlightStore.Draw>? = null)

@Composable
private fun RevealDialog(state: RevealState, onDone: () -> Unit) {
    val draws = state.draws
    val best = draws?.maxByOrNull { StarlightStore.RARITIES.indexOf(it.rarity) }?.rarity
    val bestColor = best?.let { rarityColor(it) } ?: Violet
    val shake by rememberInfiniteTransition(label = "box_shake").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "box_shake_x",
    )
    val charge = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var showCards by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { charge.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing)) }
    LaunchedEffect(draws) {
        if (draws == null) return@LaunchedEffect
        // 빛을 다 모은 뒤에 터뜨린다(응답이 빨라도 최소 연출 시간은 지킨다).
        while (charge.isRunning) delay(30)
        when (best) {
            "legendary" -> { Haptics.celebrate(); delay(120); Haptics.celebrate() }
            "epic" -> Haptics.celebrate()
            else -> Haptics.medium()
        }
        burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        showCards = true
    }
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF00A0C16))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = showCards, onClick = onDone,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 터지는 빛
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val b = burst.value
                if (b > 0f) {
                    val rr = size.minDimension * (0.2f + b * 0.9f)
                    drawCircle(Brush.radialGradient(listOf(bestColor.copy(alpha = 0.55f * (1 - b * 0.6f)), Color.Transparent), c, rr), rr, c)
                    val rays = if (best == "legendary") 16 else 10
                    for (i in 0 until rays) {
                        val a = i * 2 * PI / rays
                        val len = size.minDimension * (0.15f + b * 0.55f)
                        drawLine(
                            bestColor.copy(alpha = 0.5f * (1 - b)),
                            c, Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat()),
                            strokeWidth = 3f,
                        )
                    }
                }
            }
            if (!showCards) {
                NebulaBoxArt(
                    Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            val k = charge.value
                            translationX = shake * 6f * k
                            rotationZ = shake * 3f * k
                            val sc = 1f + 0.12f * k - 0.5f * burst.value
                            scaleX = sc; scaleY = sc
                            alpha = 1f - burst.value
                        },
                    glow = charge.value,
                )
            }
            if (showCards && draws != null) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (draws.size == 1) {
                        DrawCard(draws[0], index = 0, big = true)
                    } else {
                        draws.chunked(2).forEachIndexed { rowIdx, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                row.forEachIndexed { i, d ->
                                    DrawCard(d, index = rowIdx * 2 + i, big = false, modifier = Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.starlight_tap_close), color = Dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DrawCard(d: StarlightStore.Draw, index: Int, big: Boolean, modifier: Modifier = Modifier) {
    val item = StarlightStore.item(d.id)
    val c = rarityColor(d.rarity)
    val lang = starlightLang()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(110L * index)
        visible = true
        if (d.rarity == "legendary" || d.rarity == "epic") Haptics.medium() else Haptics.tick()
    }
    val pulse by rememberInfiniteTransition(label = "card").animateFloat(
        0.35f, 0.9f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "card_glow",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(if (big) 24.dp else 18.dp)
        Column(
            modifier = Modifier
                .then(if (big) Modifier.width(260.dp) else Modifier.fillMaxWidth())
                .clip(shape)
                .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.16f), Color(0xFF121821))))
                .border(
                    if (d.rarity == "legendary" || d.rarity == "epic") 1.5.dp else 1.dp,
                    c.copy(alpha = if (d.rarity == "legendary" || d.rarity == "epic") pulse else 0.55f), shape,
                )
                .padding(if (big) 20.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rarityName(d.rarity), color = c, fontSize = if (big) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(if (big) 140.dp else 72.dp), contentAlignment = Alignment.Center) {
                if (item != null) ItemArt(item, Modifier.fillMaxSize()) else StardustIcon(Modifier.fillMaxSize(0.5f), c)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item?.nameIn(lang) ?: d.id, color = Ink, fontSize = if (big) 17.sp else 13.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2,
            )
            if (big && item != null) {
                Spacer(Modifier.height(6.dp))
                Text(item.descIn(lang), color = Sub, fontSize = 12.5.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            val note = when {
                d.keys > 0 -> stringResource(R.string.starlight_keys_plus, d.keys)
                d.dup -> stringResource(R.string.starlight_dup_refund, d.refund)
                item != null && (item.kind == "telescope") -> stringResource(R.string.starlight_stacked)
                else -> stringResource(R.string.starlight_new)
            }
            Text(
                note,
                color = if (d.dup) Dim else Dust, fontSize = if (big) 12.sp else 10.5.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
