package com.chaminwoo.stary.feature.starlight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset
import com.chaminwoo.stary.core.ui.StaryChip
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.ThumbAsyncImage
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.StigmaStore
import kotlinx.coroutines.launch

/** 수집품 화면의 종류 순서(장착형 먼저). */
private val KIND_ORDER = listOf("frame", "badge", "title", "aura", "shape", "color", "telescope")

/**
 * 수집품(2026-10-22) — 별빛 공방에서 얻은 칭호·별 모양·프레임 등을 **가진 것만** 모아 보고 장착한다.
 * 위: 지금 장착 중(프레임 두른 내 사진 · 이름+배지 · 칭호 · 오라), 아래: 종류 칩 + 가진 아이템 격자(누르면 상세 → 장착/해제).
 * iOS `CollectionScreen.swift` 패리티.
 */
@Composable
fun CollectionScreen() {
    var kind by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<StarlightStore.Item?>(null) }
    val owned = StarlightStore.owned
    val mine = StarlightStore.items.filter { (owned[it.id] ?: 0) > 0 }
        .sortedWith(compareBy({ KIND_ORDER.indexOf(it.kind).let { i -> if (i < 0) 99 else i } }, { -StarlightStore.RARITIES.indexOf(it.rarity) }, { it.order }))
    val kinds = KIND_ORDER.filter { k -> mine.any { it.kind == k } } + mine.map { it.kind }.distinct().filter { it !in KIND_ORDER }
    val shown = if (kind == null) mine else mine.filter { it.kind == kind }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = LocalTopBarInset.current)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LoadoutCard(onSlot = { k -> kind = k })

        if (mine.isEmpty()) {
            Text(
                stringResource(R.string.starlight_collection_empty),
                color = Sub, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
            return@Column
        }

        // 종류 칩
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StaryChip("${stringResource(R.string.starlight_all)} ${mine.size}", kind == null, accent = Violet) { Haptics.tick(); kind = null }
            kinds.forEach { k ->
                StaryChip("${kindName(k)} ${mine.count { it.kind == k }}", kind == k, accent = Violet) { Haptics.tick(); kind = k }
            }
        }

        shown.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> OwnedCell(item, onClick = { detail = item }, modifier = Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    detail?.let { item -> ItemDetailDialog(item, onDismiss = { detail = null }) }
}

/** 지금 장착 중 — 프레임 두른 사진, 이름 + 배지, 칭호, 오라. 칸을 누르면 그 종류만 보여 준다. */
@Composable
private fun LoadoutCard(onSlot: (String) -> Unit) {
    val context = LocalContext.current
    val eq = StarlightStore.myEquips
    val uid = GoogleAuthHelper.currentUserId
    val name = GoogleAuthHelper.confirmedName.collectAsState().value ?: GoogleAuthHelper.currentUserName.orEmpty()
    val photo = GoogleAuthHelper.currentUserPhotoUrl
    val frame = StarlightStore.item(eq.frame)
    val badge = StarlightStore.item(eq.badge)
    val aura = StarlightStore.item(eq.aura)
    // 칭호는 업적 칭호와 한 자리 — 실제로 달린 값이 "sl:" 일 때만 공방 칭호.
    val title = uid?.let { StarlightStore.titleItem(StigmaStore.equipped(context, it)) }
    val lang = starlightLang()
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF221B3D), Color(0xFF131826))))
            .border(1.dp, Brush.linearGradient(listOf(Gold.copy(alpha = 0.45f), Violet.copy(alpha = 0.3f))), shape)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.starlight_loadout), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FramedAvatar(frame?.payload, size = 84.dp, modifier = Modifier.clickable { onSlot("frame") }) {
                if (photo != null) ThumbAsyncImage(photo, null, Modifier.fillMaxSize().clip(CircleShape), sizePx = 256)
                else Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF555555), modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "···" }, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    EquippedBadge(eq, size = 20.dp, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.height(4.dp))
                if (title != null) TitleText("『${title.nameIn(lang)}』", title.payload, fontSize = 13f, modifier = Modifier.clickable { onSlot("title") })
            }
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).clickable { onSlot("aura") },
                contentAlignment = Alignment.Center,
            ) {
                if (aura != null) AuraArt(aura.payload, Modifier.fillMaxSize(), itemId = aura.id)
                else Text(stringResource(R.string.starlight_kind_aura), color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(12.dp))
        // 칸 요약 — 무엇이 끼워져 있는지 / 비었는지
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("frame" to frame, "badge" to badge, "title" to title, "aura" to aura).forEach { (k, it) ->
                SlotPill(kindName(k), it?.nameIn(lang), it?.rarity, Modifier.weight(1f)) { onSlot(k) }
            }
        }
    }
}

@Composable
private fun SlotPill(label: String, value: String?, rarity: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = rarity?.let { rarityColor(it) } ?: Dim
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (value != null) c.copy(alpha = 0.10f) else Color(0x0FFFFFFF))
            .border(0.75.dp, if (value != null) c.copy(alpha = 0.45f) else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Dim, fontSize = 10.sp, maxLines = 1)
        Text(
            value ?: stringResource(R.string.starlight_slot_empty),
            color = if (value != null) Ink else Dim, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OwnedCell(item: StarlightStore.Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = rarityColor(item.rarity)
    val shape = RoundedCornerShape(16.dp)
    val slot = slotOf(item.kind)
    val equipped = slot != null && isEquipped(LocalContext.current, slot, item.id)
    val count = StarlightStore.owned[item.id] ?: 0
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.14f), Color(0x0AFFFFFF))))
            .border(if (equipped) 1.5.dp else 0.75.dp, c.copy(alpha = if (equipped) 0.9f else 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            ItemArt(item, Modifier.fillMaxSize(0.84f))
            if (equipped) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(c),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Color(0xFF111120), modifier = Modifier.size(11.dp)) }
            } else if (item.kind == "telescope" && count > 1) {
                Text("×$count", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.nameIn(starlightLang()), color = Ink, fontSize = 11.sp, lineHeight = 14.sp,
            textAlign = TextAlign.Center, maxLines = 2,
        )
        Text(
            if (equipped) stringResource(R.string.starlight_equipped) else rarityName(item.rarity),
            color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 장착 칸 — 프레임·배지·오라·칭호만(나머지는 null). */
internal fun slotOf(kind: String): StarlightStore.Slot? = StarlightStore.Slot.entries.firstOrNull { it.kind == kind }

@Composable
internal fun ItemDetailDialog(item: StarlightStore.Item, onDismiss: () -> Unit) {
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
                .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.16f), Color(0xFF121821), Color(0xFF121821))))
                .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ItemArt(item, Modifier.size(120.dp))
            Spacer(Modifier.height(10.dp))
            Text("${rarityName(item.rarity)} · ${kindName(item.kind)}", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(item.nameIn(lang), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(item.descIn(lang), color = Sub, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            if (count > 0) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.starlight_owned_n, count), color = c, fontSize = 12.sp)
                usageNote(item)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(it), color = Dim, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
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
internal fun isEquipped(context: android.content.Context, slot: StarlightStore.Slot, id: String): Boolean {
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
