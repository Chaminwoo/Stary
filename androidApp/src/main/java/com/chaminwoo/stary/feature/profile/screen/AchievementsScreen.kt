package com.chaminwoo.stary.feature.profile.screen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievement
import com.chaminwoo.stary.feature.profile.Achievements
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("칭호", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "업적을 달성하면 칭호를 얻어요. 완료한 업적을 눌러 장착하세요.",
                    color = TextMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("달성 $unlockedCount / ${Achievements.all.size}", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
        }

        items(Achievements.all, key = { it.id }) { ach ->
            val unlocked = ach.unlocked(stats)
            AchievementRow(
                ach = ach,
                unlocked = unlocked,
                equipped = equipped == ach.id,
                onToggleEquip = {
                    if (unlocked) {
                        val next = if (equipped == ach.id) null else ach.id
                        StigmaStore.equip(context, userId, next)
                        equipped = next
                    }
                }
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AchievementRow(
    ach: Achievement,
    unlocked: Boolean,
    equipped: Boolean,
    onToggleEquip: () -> Unit,
) {
    val locked = !unlocked
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
            .clickable(enabled = unlocked) { onToggleEquip() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘 원
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

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (ach.hidden && locked) "???" else ach.stigma,
                color = if (unlocked) TextMain else TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (ach.hidden && locked) "숨겨진 업적" else ach.condition,
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(10.dp))

        when {
            equipped -> Text("장착됨", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            unlocked -> Text("장착", color = TextMain, fontSize = 12.sp)
            else -> Text("잠김", color = TextMuted, fontSize = 12.sp)
        }
    }
}
