package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievement
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.Reward
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.profile.rememberUserStats

private val Green = Color(0xFF6EE7B7)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xFF15151F)

@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val stats = rememberUserStats(userId)
    var equipped by remember { mutableStateOf(StigmaStore.equipped(context, userId)) }

    val unlockedCount = Achievements.all.count { it.unlocked(stats) }

    Box(modifier = modifier.fillMaxSize()) {
        // 내 다이어리 페이지와 동일한 배경·밝기
        Image(
            painter = painterResource(R.drawable.mydiary_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {

                Text(
                    "업적을 달성하면 칭호와 새로운 별 모양·색을 얻어요.",
                    color = TextMuted, fontSize = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("달성 $unlockedCount / ${Achievements.all.size}", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── 칭호 업적 ──
        item { GroupHeader("칭호", "완료한 업적을 눌러 장착하세요") }
        items(Achievements.titleAchievements, key = { it.id }) { ach ->
            val unlocked = ach.unlocked(stats)
            TitleAchievementRow(
                ach = ach,
                unlocked = unlocked,
                equipped = equipped == ach.id,
                onToggleEquip = {
                    if (unlocked) {
                        val next = if (equipped == ach.id) null else ach.id
                        StigmaStore.equip(context, userId, next)
                        equipped = next
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            if (next != null) "‘${ach.titleName ?: ach.name}’ 칭호를 장착했어요" else "칭호를 해제했어요"
                        )
                    }
                }
            )
        }

        // ── 별 모양/색 보상 업적 ──
        item { GroupHeader("별 모양 · 색", "달성하면 업로드에서 해금돼요") }
        items(Achievements.rewardAchievements, key = { it.id }) { ach ->
            RewardAchievementRow(ach = ach, unlocked = ach.unlocked(stats))
        }

        item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

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
    val locked = !unlocked
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
        title = if (ach.hidden && locked) "???" else ach.name,
        subtitle = if (ach.hidden && locked) "숨겨진 업적" else ach.condition,
        unlocked = unlocked,
        trailing = {
            when {
                equipped -> Text("장착됨", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                unlocked -> Text("장착", color = TextMain, fontSize = 12.sp)
                else -> Text("잠김", color = TextMuted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun RewardAchievementRow(ach: Achievement, unlocked: Boolean) {
    val locked = !unlocked
    AchievementRowFrame(
        equipped = false,
        clickEnabled = false,
        onClick = {},
        leading = { RewardPreview(ach.reward, unlocked) },
        title = if (ach.hidden && locked) "???" else ach.name,
        subtitle = if (ach.hidden && locked) "숨겨진 업적" else ach.condition,
        unlocked = unlocked,
        trailing = {
            if (unlocked) Text("획득", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else Text("잠김", color = TextMuted, fontSize = 12.sp)
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
    androidx.compose.foundation.layout.Row(
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
