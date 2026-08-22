package com.chaminwoo.stary.feature.profile

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.LocalizedNames
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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

/** 리빌(파편이 모여 별이 되는) 길이(ms) 와 파편 개수. */
private const val REVEAL_MS = 900
private const val REVEAL_SHARDS = 14

/**
 * 업적 달성 팝업 — **해금된 보상을 실제로 보여준다.**
 *
 * 예전엔 트로피 글리프 하나에 "새 별 모양 해금" 같은 **글자만** 떠서, 정작 무엇을 얻었는지
 * 업적 화면에 들어가야 알 수 있었다. 지금은 그 별(모양/색)을 크리스탈로 크게 띄우고
 * 파편이 사방에서 모여 별이 완성되는 리빌 + 뒤쪽 광선 회전 + 축하 진동으로 보상을 체감시킨다.
 *  - 칭호 업적: 앰버골드 5꼭지 별(칭호는 형태가 없어 "빛나는 이름표" 대역).
 *  - 별 모양 업적: 해금된 모양을 앰버골드로.
 *  - 별 색 업적: 해금된 색을 기본 5꼭지 별로.
 */
@Composable
private fun AchievementUnlockDialog(achievement: Achievement, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ach-pop"
    )

    // 보상 → 화면에 띄울 별(모양/색) + 설명 문구.
    val goldIndex = 15 // 앰버골드
    val (starType, starColor) = when (val r = achievement.reward) {
        is Reward.Shape -> r.shapeType to goldIndex
        is Reward.StarColor -> 1 to r.colorIndex
        is Reward.Title -> 1 to goldIndex
    }
    val rewardText = when (val r = achievement.reward) {
        is Reward.Title -> stringResource(
            R.string.ach_reward_title,
            LocalizedNames.title(context, achievement.id, r.name) ?: r.name
        )
        is Reward.Shape -> stringResource(R.string.ach_reward_shape)
        is Reward.StarColor -> stringResource(R.string.ach_reward_color)
    }
    val rewardColor = StarStyle.colorOf(starColor)

    // 리빌 진행도 0→1. 파편이 모여들고(0~0.55) 별이 부풀었다 안정된다.
    val reveal = remember(achievement.id) { Animatable(0f) }
    LaunchedEffect(achievement.id) {
        Haptics.celebrate() // 보상이 완성되는 순간의 축하 진동
        reveal.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
    }
    // 뒤쪽 광선은 팝업이 떠 있는 동안 아주 천천히 계속 돈다(정지 화면이 아니게).
    val rayTransition = rememberInfiniteTransition(label = "ach-rays")
    val rayAngle by rayTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "ach-ray-angle"
    )

    val shards = remember(achievement.id) {
        val rnd = Random(achievement.id.hashCode())
        List(REVEAL_SHARDS) { i ->
            RevealShard(
                angleDeg = (i.toFloat() / REVEAL_SHARDS) * 360f + rnd.nextFloat() * 18f - 9f,
                startDistance = 0.85f + rnd.nextFloat() * 0.6f,
                size = 0.55f + rnd.nextFloat() * 0.75f,
                delay = rnd.nextFloat() * 0.22f,
            )
        }
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
                // ── 보상 리빌: 광선 + 모여드는 파편 + 완성된 별 ──
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                    val p = reveal.value
                    Canvas(modifier = Modifier.size(132.dp)) {
                        val r = size.minDimension / 2f
                        val settled = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)

                        // 뒤쪽 광선 12갈래 — 별이 완성될수록 또렷해진다.
                        rotate(degrees = rayAngle) {
                            repeat(12) { i ->
                                val a = Math.toRadians((i * 30).toDouble())
                                val long = i % 2 == 0
                                val len = r * (if (long) 1.0f else 0.72f)
                                val half = (if (long) 3.2f else 2.0f).dp.toPx()
                                val tip = Offset(
                                    center.x + (cos(a) * len).toFloat(),
                                    center.y + (sin(a) * len).toFloat()
                                )
                                val side1 = Offset(
                                    center.x + (cos(a + Math.PI / 2) * half).toFloat(),
                                    center.y + (sin(a + Math.PI / 2) * half).toFloat()
                                )
                                val side2 = Offset(
                                    center.x + (cos(a - Math.PI / 2) * half).toFloat(),
                                    center.y + (sin(a - Math.PI / 2) * half).toFloat()
                                )
                                drawPath(
                                    Path().apply {
                                        moveTo(side1.x, side1.y); lineTo(tip.x, tip.y); lineTo(side2.x, side2.y); close()
                                    },
                                    color = rewardColor.copy(alpha = 0.16f * settled)
                                )
                            }
                        }

                        // 별 뒤 후광.
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(rewardColor.copy(alpha = 0.34f * settled), Color.Transparent),
                                center = center, radius = r * 0.86f
                            ),
                            radius = r * 0.86f
                        )

                        // 모여드는 파편 — 바깥에서 중심으로 빨려 들어와 별이 된다.
                        shards.forEach { sh ->
                            val span = (0.55f - sh.delay).coerceAtLeast(0.05f)
                            val local = ((p - sh.delay) / span).coerceIn(0f, 1f)
                            if (local < 1f) {
                                val ease = local * local // easeInQuad — 중심에 가까울수록 빨라진다
                                val dist = r * sh.startDistance * (1f - ease)
                                val rad = Math.toRadians(sh.angleDeg.toDouble())
                                val cx = center.x + (cos(rad) * dist).toFloat()
                                val cy = center.y + (sin(rad) * dist).toFloat()
                                val side = 6.dp.toPx() * sh.size * (0.4f + 0.6f * (1f - ease))
                                rotate(degrees = sh.angleDeg, pivot = Offset(cx, cy)) {
                                    drawPath(
                                        Path().apply {
                                            moveTo(cx, cy - side)
                                            lineTo(cx + side * 0.6f, cy)
                                            lineTo(cx, cy + side)
                                            lineTo(cx - side * 0.6f, cy)
                                            close()
                                        },
                                        color = rewardColor.copy(alpha = 0.9f * (1f - ease * 0.35f))
                                    )
                                }
                            }
                        }

                        // 완성 순간의 플래시 링(0.5~0.75 구간).
                        val flash = ((p - 0.5f) / 0.25f).coerceIn(0f, 1f)
                        if (flash > 0f && flash < 1f) {
                            drawCircle(
                                color = rewardColor.copy(alpha = 0.5f * (1f - flash)),
                                radius = r * (0.3f + 0.75f * flash),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 3.dp.toPx() * (1f - flash)
                                )
                            )
                        }
                    }

                    // 완성된 보상 별 — 파편이 다 모인 뒤 또렷해진다(살짝 부풀었다 안정).
                    val appear = ((reveal.value - 0.42f) / 0.35f).coerceIn(0f, 1f)
                    val settleScale = when {
                        reveal.value < 0.62f -> 0.7f + 0.48f * appear      // 0.70 → 1.18
                        else -> 1.18f - 0.18f * ((reveal.value - 0.62f) / 0.38f).coerceIn(0f, 1f)
                    }
                    StarShapeIcon(
                        type = starType,
                        colorIndex = starColor,
                        modifier = Modifier
                            .size(62.dp)
                            .graphicsLayer {
                                scaleX = settleScale; scaleY = settleScale; alpha = appear
                            }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.ach_unlocked),
                    color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    LocalizedNames.title(context, achievement.id, achievement.name) ?: achievement.name,
                    color = Color.White, fontSize = 22.sp,
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
                        .background(rewardColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .border(1.dp, rewardColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(rewardText, color = rewardColor, fontSize = 13.sp, fontWeight = FontWeight.Normal)
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
                    Text(
                        stringResource(R.string.common_confirm),
                        color = Color(0xFF0D0D0D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 리빌 파편 한 조각(시작 각도/거리/크기/지연). */
private data class RevealShard(
    val angleDeg: Float,
    val startDistance: Float,
    val size: Float,
    val delay: Float,
)
