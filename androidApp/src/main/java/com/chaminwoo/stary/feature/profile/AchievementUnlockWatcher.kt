package com.chaminwoo.stary.feature.profile

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val Accent = Color(0xFF9FB3E8) // 남색 계열 라이트 강조(구 민트)
private val Blue = Color(0xFF3B82F6)
private val Navy = Color(0xFF1E3A8A) // 그라데이션 짝(파랑→남색)

/**
 * 업적 해금 감시기 — 사용자 통계가 바뀌어 새 업적이 달성되면 팝업을 띄운다.
 * 최초 1회는 이미 달성한 업적을 알림 없이 기준선으로 저장하고, 이후 새로 넘긴 업적만 알린다.
 * [userId] 가 있는(로그인) 상태에서만 호출한다. MainScreen 최상위에 두어 어느 화면에서든 동작.
 */
@Composable
fun AchievementUnlockWatcher(userId: String, suppressed: Boolean = false) {
    val context = LocalContext.current
    val stats = rememberUserStats(userId)
    val unlocked = remember(stats) { Achievements.unlockedIds(stats) }
    val queue = remember { mutableStateListOf<Achievement>() }

    LaunchedEffect(unlocked, userId) {
        val prefs = context.getSharedPreferences("stary_prefs", Context.MODE_PRIVATE)
        val key = "ach_announced_$userId"
        val stored = prefs.getStringSet(key, null)
        if (stored == null) {
            // 최초: 이미 달성한 업적은 팝업 없이 기준선으로 기록
            prefs.edit().putStringSet(key, HashSet(unlocked)).apply()
        } else {
            val newIds = unlocked - stored
            if (newIds.isNotEmpty()) {
                Achievements.all
                    .filter { it.id in newIds && queue.none { q -> q.id == it.id } }
                    .forEach { queue.add(it) }
                prefs.edit().putStringSet(key, HashSet(stored + unlocked)).apply()
            }
        }
    }

    // 코치마크(온보딩) 등 다른 오버레이가 떠 있는 동안엔 큐에 쌓아만 두고, 닫힌 뒤에 표시.
    if (!suppressed) {
        queue.firstOrNull()?.let { ach ->
            AchievementUnlockDialog(ach) { queue.removeAt(0) }
        }
    }
}

@Composable
private fun AchievementUnlockDialog(achievement: Achievement, onDismiss: () -> Unit) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ach-pop"
    )
    val rewardText = when (val r = achievement.reward) {
        is Reward.Title -> "칭호 «${r.name}» 획득"
        is Reward.Shape -> "새 별 모양 해금"
        is Reward.StarColor -> "새 별 색 해금"
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop }
                .widthIn(max = 330.dp)
                .background(Color(0xFF14181C), RoundedCornerShape(24.dp))
                .border(1.5.dp, Brush.linearGradient(listOf(Blue, Navy)), RoundedCornerShape(24.dp))
                .padding(horizontal = 26.dp, vertical = 30.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Accent, modifier = Modifier.size(58.dp))
                Spacer(Modifier.height(14.dp))
                Text("업적 달성!", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(
                    achievement.name, color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    achievement.condition, color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(rewardText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Normal)
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Blue, Navy)))
                        .clickable { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("확인", color = Color(0xFF0D0D0D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
