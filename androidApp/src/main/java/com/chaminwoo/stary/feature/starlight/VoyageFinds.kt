package com.chaminwoo.stary.feature.starlight

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StaryButton
import com.chaminwoo.stary.core.ui.StaryButtonKind
import com.chaminwoo.stary.core.ui.StaryPopupPanel
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.ThumbAsyncImage
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.quest.QuestEvent
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.launch

/*
 * 탐사 수집품(2026-10-26 — 옛 "수집품" 화면을 업적 › 특별 › 탐사로 옮겼다). 우주 항해에서 찾은 것을 모아 보고,
 * 프레임·배지·칭호는 눌러서 장착한다. 별 고리는 별을 올릴 때 별 색 아래에서 고르고(그 별에만), 모양·색은 업로드 고르기에,
 * 망원경은 업적 화면에서 쓴다. 업적 화면 특별 탭이 [OwnedCell] · [ItemDetailDialog] 를 늘어놓는다
 * (2026-10-27 — "지금 장착 중" 요약은 사용자 지시로 뺐다. 모은 것만 보인다).
 * iOS `VoyageFinds.swift` 패리티.
 */

/** 종류 순서(장착형 먼저). */
private val KIND_ORDER = listOf("frame", "badge", "title", "aura", "shape", "color", "telescope")

/** 가진 아이템 — 종류 순서 → 희귀한 것부터 → 풀 순서. */
fun ownedFinds(): List<StarlightStore.Item> {
    val owned = StarlightStore.owned
    return StarlightStore.items.filter { (owned[it.id] ?: 0) > 0 }.sortedWith(
        compareBy(
            { KIND_ORDER.indexOf(it.kind).let { i -> if (i < 0) 99 else i } },
            { -StarlightStore.RARITIES.indexOf(it.rarity) },
            { it.order },
        )
    )
}

/** 가진 것들의 종류(칩 순서). */
fun findKinds(finds: List<StarlightStore.Item>): List<String> =
    KIND_ORDER.filter { k -> finds.any { it.kind == k } } + finds.map { it.kind }.distinct().filter { it !in KIND_ORDER }

/** 가진 아이템 한 칸 — 등급 빛 바탕, 장착 중이면 체크, 망원경은 남은 개수. */
@Composable
fun OwnedCell(item: StarlightStore.Item, equippedTitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = rarityColor(item.rarity)
    val shape = RoundedCornerShape(16.dp)
    val slot = slotOf(item.kind)
    val equipped = slot != null && isEquipped(slot, item.id, equippedTitle)
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

/** 장착 칸 — 프레임·배지·칭호만(별 고리는 별마다 고르고, 나머지는 쓰는 곳이 따로라 null). */
internal fun slotOf(kind: String): StarlightStore.Slot? = StarlightStore.Slot.entries.firstOrNull { it.kind == kind }

/**
 * 장착 중인가. 칭호는 업적 칭호와 한 자리를 나눠 쓰므로 실제로 달려 있는 값([equippedTitle] = users.equippedTitle 의 기기 사본)이
 * "sl:{id}" 일 때만 — 탐사 칭호를 단 뒤 업적 칭호로 바꾸면 starlightEquips.title 은 옛값으로 남는다.
 */
internal fun isEquipped(slot: StarlightStore.Slot, id: String, equippedTitle: String?): Boolean =
    if (slot == StarlightStore.Slot.TITLE) equippedTitle == StarlightStore.TITLE_PREFIX + id
    else StarlightStore.myEquips.of(slot) == id // 구독 상태를 읽어 장착이 바뀌면 다시 그린다

/** 서버가 users.equippedTitle 을 바꿨으니 기기 사본(StigmaStore)도 맞춘다 — 업적 화면·프로필이 이걸 읽는다. 바뀐 값을 돌려준다. */
private fun syncTitle(context: android.content.Context, id: String?): String? {
    val uid = GoogleAuthHelper.currentUserId ?: return null
    val cur = StigmaStore.equipped(context, uid)
    when {
        id != null -> StigmaStore.equip(context, uid, StarlightStore.TITLE_PREFIX + id)
        cur?.startsWith(StarlightStore.TITLE_PREFIX) == true -> StigmaStore.equip(context, uid, null)
    }
    return StigmaStore.equipped(context, uid)
}

/** 가진 아이템을 어디서 쓰는지(장착형은 버튼으로 충분해 null). */
private fun usageNote(item: StarlightStore.Item): Int? = when (item.kind) {
    "aura" -> R.string.starlight_use_ring
    "shape" -> R.string.starlight_use_shape
    "color" -> R.string.starlight_use_color
    "telescope" -> if (item.payload["mode"] == "reveal") R.string.starlight_use_reveal else R.string.starlight_use_hint
    else -> null
}

/**
 * 아이템 상세 — 그림 · 등급·종류 · 이름 · 설명 · 가진 수 · 쓰는 곳, 장착형이면 장착/해제 버튼.
 * 칭호를 바꾸면 [onTitleChanged] 로 새 칭호 id 를 알린다(업적 화면의 칭호 행이 같은 자리를 쓴다).
 */
@Composable
fun ItemDetailDialog(
    item: StarlightStore.Item,
    equippedTitle: String?,
    onTitleChanged: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val lang = starlightLang()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = rarityColor(item.rarity)
    val count = StarlightStore.owned[item.id] ?: 0
    val slot = slotOf(item.kind)
    var busy by remember { mutableStateOf(false) }
    val equipped = slot != null && isEquipped(slot, item.id, equippedTitle)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // 판은 다른 팝업과 같은 유리 — 희귀도 색은 그림 뒤 은은한 빛으로만.
        StaryPopupPanel(
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ItemArt(
                item,
                Modifier.size(120.dp).drawBehind { drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.24f), Color.Transparent))) },
            )
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
                    StaryButton(
                        stringResource(if (equipped) R.string.starlight_unequip else R.string.starlight_equip),
                        onClick = {
                            busy = true
                            scope.launch {
                                val r = StarlightStore.equip(slot, if (equipped) null else item.id)
                                busy = false
                                when (r) {
                                    is StarlightStore.OpResult.Done -> {
                                        if (slot == StarlightStore.Slot.TITLE) {
                                            onTitleChanged(syncTitle(context, if (equipped) null else item.id))
                                            if (!equipped) QuestStore.report(QuestEvent.EQUIP_TITLE)
                                        }
                                        Haptics.medium()
                                        StaryAnalytics.log("workshop_equip", "slot" to slot.id, "item" to (if (equipped) "" else item.id))
                                        StaryToast.show(context.getString(if (equipped) R.string.starlight_unequip_done else R.string.starlight_equip_done))
                                    }
                                    is StarlightStore.OpResult.Failed -> StaryToast.show(
                                        context.getString(if (r.code == "timeout") R.string.starlight_err_timeout else R.string.starlight_err_generic)
                                    )
                                }
                            }
                        },
                        kind = if (equipped) StaryButtonKind.Ghost else StaryButtonKind.Primary,
                        compact = true,
                        loading = busy,
                    )
                }
            }
        }
    }
}
