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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.HiddenAchievementRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.launch

private val HiddenGold = Color(0xFFFFD86F)
private val HiddenPurple = Color(0xFFB388FF)

/**
 * 히든 업적 감시기 — 통계가 자동 조건을 만족하고 **아직 아무도 선점하지 않았으면** 선점을 시도한다.
 * 내가 선점에 성공하면(=앱 전체 첫 달성자) 특별 팝업을 띄우고 칭호를 자동 장착한다.
 * [userId] 가 있는(로그인) 상태에서만 호출. MainScreen 최상위에 둔다.
 */
@Composable
fun HiddenAchievementWatcher(userId: String, suppressed: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = rememberUserStats(userId)
    val repo = remember { HiddenAchievementRepository() }
    val claims by remember { repo.observe() }.collectAsState(initial = emptyMap())

    val allNormalDone = remember(stats) {
        Achievements.all.isNotEmpty() && Achievements.unlockedIds(stats).size >= Achievements.all.size
    }
    val satisfied = remember(stats, allNormalDone) {
        HiddenAchievements.satisfiedAutoIds(HiddenContext(stats, allNormalDone))
    }

    // 세션 내 중복 트랜잭션 방지 + 달성 팝업 큐
    val attempted = remember { mutableStateListOf<String>() }
    val queue = remember { mutableStateListOf<HiddenAchievement>() }

    // 어드민(테스트) 계정이 과거에 실수로 선점한 히든 업적은 슬롯을 되돌린다(실제 유저가 첫 달성자가 되게).
    LaunchedEffect(userId) {
        if (com.chaminwoo.stary.shared.config.StaryConfig.isAdminEmail(GoogleAuthHelper.currentUserEmail)) {
            repo.releaseOwnedBy(userId)
        }
    }

    LaunchedEffect(satisfied, claims) {
        val name = GoogleAuthHelper.currentUserName ?: userId.take(12)
        for (id in satisfied) {
            if (id in attempted) continue
            if (claims[id]?.claimed == true) continue // 이미 누군가 달성함
            attempted.add(id)
            scope.launch {
                val won = repo.claim(id, userId, name)
                if (won) {
                    HiddenAchievements.byId(id)?.let { ach ->
                        if (queue.none { it.id == ach.id }) queue.add(ach)
                        // 칭호 자동 장착 (원하면 나중에 업적 화면에서 변경 가능)
                        StigmaStore.equip(context, userId, id)
                        FirebaseFriendRepository().setEquippedTitle(userId, id)
                    }
                }
            }
        }
    }

    if (!suppressed) {
        queue.firstOrNull()?.let { ach ->
            HiddenUnlockDialog(ach) { queue.removeAt(0) }
        }
    }
}

@Composable
private fun HiddenUnlockDialog(achievement: HiddenAchievement, onDismiss: () -> Unit) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "hidden-pop"
    )
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop }
                .widthIn(max = 340.dp)
                .background(Color(0xFF120E1C), RoundedCornerShape(24.dp))
                .border(1.5.dp, Brush.linearGradient(listOf(HiddenGold, HiddenPurple)), RoundedCornerShape(24.dp))
                .padding(horizontal = 26.dp, vertical = 30.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 전용 아이콘(이펙트 미니 프리뷰 포함)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(achievement.icon.color.copy(alpha = 0.35f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    HiddenIconWithEffect(achievement, size = 46.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    androidx.compose.ui.res.stringResource(com.chaminwoo.stary.R.string.ach_hidden_unlocked),
                    color = HiddenGold, fontSize = 15.sp, fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    com.chaminwoo.stary.core.util.LocalizedNames.title(
                        androidx.compose.ui.platform.LocalContext.current, achievement.id, achievement.title
                    )!!,
                    color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    com.chaminwoo.stary.core.util.LocalizedNames.condition(
                        androidx.compose.ui.platform.LocalContext.current, achievement.id, achievement.condition
                    ),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    androidx.compose.ui.res.stringResource(com.chaminwoo.stary.R.string.ach_hidden_only_one),
                    color = HiddenPurple, fontSize = 12.sp, fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(HiddenGold, HiddenPurple)))
                        .clickable { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.chaminwoo.stary.R.string.common_confirm),
                        color = Color(0xFF0D0D0D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 히든 아이콘 + 주변 파티클 이펙트 (팝업·프로필 공용). */
@Composable
fun HiddenIconWithEffect(achievement: HiddenAchievement, size: androidx.compose.ui.unit.Dp) {
    Box(contentAlignment = Alignment.Center) {
        HiddenParticles(effect = achievement.effect, tint = achievement.icon.color, boxSize = size)
        Icon(
            imageVector = achievement.icon.vector,
            contentDescription = achievement.title,
            tint = achievement.icon.color,
            modifier = Modifier.size(size * 0.62f)
        )
    }
}
