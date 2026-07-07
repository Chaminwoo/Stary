package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.data.repository.HiddenAchievementRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievement
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.HiddenAchievement
import com.chaminwoo.stary.feature.profile.HiddenAchievements
import com.chaminwoo.stary.feature.profile.HiddenClaim
import com.chaminwoo.stary.feature.profile.HiddenIconWithEffect
import com.chaminwoo.stary.feature.profile.Reward
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.profile.rememberUserStats
import kotlinx.coroutines.launch

private val Green = Color(0xFF6EE7B7)
private val Gold = Color(0xFFFFD86F)
private val Purple = Color(0xFFB388FF)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xFF15151F)

@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_login_required), color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val stats = rememberUserStats(userId)
    var equipped by remember { mutableStateOf(StigmaStore.equipped(context, userId)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }

    // 히든 업적 달성 현황(전역)
    val hiddenRepo = remember { HiddenAchievementRepository() }
    val claims by remember { hiddenRepo.observe() }.collectAsState(initial = emptyMap())

    val unlockedCount = Achievements.all.count { it.unlocked(stats) }

    Box(modifier = modifier.fillMaxSize()) {
        // 내 다이어리 페이지와 동일한 배경·밝기
        Image(
            painter = painterResource(R.drawable.mydiary_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        FirstVisitInfo(
            seenKey = "info_achievements",
            icon = Icons.Filled.EmojiEvents,
            title = stringResource(R.string.onb_achievements_title),
            message = stringResource(R.string.onb_achievements_msg),
        )

        Column(Modifier.fillMaxSize()) {
            TabBar(tab = tab, onSelect = { tab = it })

            when (tab) {
                0 -> NormalTab(
                    stats = stats,
                    unlockedCount = unlockedCount,
                    equipped = equipped,
                    onToggleEquip = { ach ->
                        val next = if (equipped == ach.id) null else ach.id
                        StigmaStore.equip(context, userId, next)
                        equipped = next
                        scope.launch {
                            com.chaminwoo.stary.data.repository.FirebaseFriendRepository()
                                .setEquippedTitle(userId, next)
                        }
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            if (next != null) context.getString(
                                R.string.ach_title_equipped,
                                com.chaminwoo.stary.core.util.LocalizedNames.title(context, ach.id, ach.titleName ?: ach.name)
                            )
                            else context.getString(R.string.ach_title_unequipped)
                        )
                    }
                )
                else -> HiddenTab(
                    claims = claims,
                    myUid = userId,
                    equipped = equipped,
                    onToggleEquip = { ach ->
                        val next = if (equipped == ach.id) null else ach.id
                        StigmaStore.equip(context, userId, next)
                        equipped = next
                        scope.launch {
                            com.chaminwoo.stary.data.repository.FirebaseFriendRepository()
                                .setEquippedTitle(userId, next)
                        }
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            if (next != null) context.getString(
                                R.string.ach_title_equipped,
                                com.chaminwoo.stary.core.util.LocalizedNames.title(context, ach.id, ach.title)
                            )
                            else context.getString(R.string.ach_title_unequipped)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TabBar(tab: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TabChip(stringResource(R.string.ach_tab_normal), selected = tab == 0, accent = Green) { onSelect(0) }
        TabChip(stringResource(R.string.ach_tab_hidden), selected = tab == 1, accent = Gold) { onSelect(1) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) accent else TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ────────────────────────── 일반 업적 탭 ──────────────────────────

@Composable
private fun NormalTab(
    stats: com.chaminwoo.stary.feature.profile.UserStats,
    unlockedCount: Int,
    equipped: String?,
    onToggleEquip: (Achievement) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(stringResource(R.string.ach_intro), color = TextMuted, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ach_progress, unlockedCount, Achievements.all.size),
                    color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        item { GroupHeader(stringResource(R.string.ach_group_titles), stringResource(R.string.ach_group_titles_sub)) }
        items(Achievements.titleAchievements, key = { it.id }) { ach ->
            val unlocked = ach.unlocked(stats)
            TitleAchievementRow(
                ach = ach,
                unlocked = unlocked,
                equipped = equipped == ach.id,
                onToggleEquip = { if (unlocked) onToggleEquip(ach) }
            )
        }

        item { GroupHeader(stringResource(R.string.ach_group_rewards), stringResource(R.string.ach_group_rewards_sub)) }
        items(Achievements.rewardAchievements, key = { it.id }) { ach ->
            RewardAchievementRow(ach = ach, unlocked = ach.unlocked(stats))
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ────────────────────────── 히든 업적 탭 ──────────────────────────

@Composable
private fun HiddenTab(
    claims: Map<String, HiddenClaim>,
    myUid: String,
    equipped: String?,
    onToggleEquip: (HiddenAchievement) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.ach_hidden_intro), color = Gold.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        items(HiddenAchievements.all, key = { it.id }) { ach ->
            HiddenAchievementRow(
                ach = ach,
                claim = claims[ach.id],
                mine = claims[ach.id]?.achieverId == myUid && myUid.isNotBlank(),
                equipped = equipped == ach.id,
                onToggleEquip = { onToggleEquip(ach) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HiddenAchievementRow(
    ach: HiddenAchievement,
    claim: HiddenClaim?,
    mine: Boolean,
    equipped: Boolean,
    onToggleEquip: () -> Unit,
) {
    val claimed = claim?.claimed == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                width = if (equipped) 1.5.dp else 1.dp,
                color = when {
                    equipped -> Gold
                    mine -> Gold.copy(alpha = 0.6f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .then(if (mine) Modifier.clickable(onClick = onToggleEquip) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘 + 파티클 (항상 노출 — 달성 전이라도 궁금증 유발)
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    Brush.radialGradient(listOf(ach.icon.color.copy(alpha = 0.16f), Color.Transparent)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            HiddenIconWithEffect(ach, size = 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // 칭호(제목)는 항상 보여준다. (언어 전환에 맞춰 로케일 해석)
            Text(
                com.chaminwoo.stary.core.util.LocalizedNames.title(LocalContext.current, ach.id, ach.title)!!,
                color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            // 조건은 달성 후에만 공개.
            Text(
                if (claimed) ach.condition else "???",
                color = if (claimed) TextMuted else Purple.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            if (claimed) {
                Spacer(Modifier.height(3.dp))
                Text(
                    if (mine) stringResource(R.string.ach_hidden_by_me)
                    else stringResource(R.string.ach_hidden_achiever, claim?.achieverName?.ifBlank { "?" } ?: "?"),
                    color = if (mine) Gold else Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            equipped -> Text(stringResource(R.string.ach_equipped), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            mine -> Text(stringResource(R.string.ach_equip), color = TextMain, fontSize = 12.sp)
            claimed -> Icon(Icons.Filled.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            else -> Text(stringResource(R.string.ach_hidden_unclaimed), color = TextMuted, fontSize = 11.sp)
        }
    }
}

// ────────────────────────── 공용 행 ──────────────────────────

@Composable
private fun GroupHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun TitleAchievementRow(
    ach: Achievement,
    unlocked: Boolean,
    equipped: Boolean,
    onToggleEquip: () -> Unit,
) {
    AchievementRowFrame(
        equipped = equipped,
        clickEnabled = unlocked,
        onClick = onToggleEquip,
        leading = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) Green.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (unlocked) Icons.Filled.Star else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) Green else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = com.chaminwoo.stary.core.util.LocalizedNames.title(LocalContext.current, ach.id, ach.name)!!,
        subtitle = ach.condition,
        unlocked = unlocked,
        trailing = {
            when {
                equipped -> Text(stringResource(R.string.ach_equipped), color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                unlocked -> Text(stringResource(R.string.ach_equip), color = TextMain, fontSize = 12.sp)
                else -> Text(stringResource(R.string.cd_locked), color = TextMuted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun RewardAchievementRow(ach: Achievement, unlocked: Boolean) {
    AchievementRowFrame(
        equipped = false,
        clickEnabled = false,
        onClick = {},
        leading = { RewardPreview(ach.reward, unlocked) },
        title = ach.name,
        subtitle = ach.condition,
        unlocked = unlocked,
        trailing = {
            if (unlocked) Text(stringResource(R.string.ach_obtained), color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else Text(stringResource(R.string.cd_locked), color = TextMuted, fontSize = 12.sp)
        }
    )
}

/** 보상 미리보기 — 모양이면 별 아이콘, 색이면 색 원. 잠겨 있으면 흐릿 + 자물쇠. */
@Composable
private fun RewardPreview(reward: Reward, unlocked: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        when (reward) {
            is Reward.Shape -> StarShapeIcon(
                type = reward.shapeType,
                color = if (unlocked) StarStyle.colorOf(9) else Color.White.copy(0.22f),
                modifier = Modifier.size(26.dp)
            )
            is Reward.StarColor -> {
                val cs = StarStyle.colorsOf(reward.colorIndex)
                val brush = if (cs.size > 1) androidx.compose.ui.graphics.Brush.linearGradient(cs)
                            else androidx.compose.ui.graphics.SolidColor(cs[0])
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(brush)
                        .then(if (unlocked) Modifier else Modifier.background(Color.Black.copy(alpha = 0.45f)))
                )
            }
            else -> Icon(Icons.Filled.Star, null, tint = Green, modifier = Modifier.size(22.dp))
        }
        if (!unlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun AchievementRowFrame(
    equipped: Boolean,
    clickEnabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    unlocked: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                width = if (equipped) 1.5.dp else 1.dp,
                color = if (equipped) Green else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = clickEnabled) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (unlocked) TextMain else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}
