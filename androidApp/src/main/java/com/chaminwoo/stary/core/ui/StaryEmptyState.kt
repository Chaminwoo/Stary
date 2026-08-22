package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.cos
import kotlin.math.sin

private val Accent = Color(0xFF9FB3E8)

/**
 * 빈 화면 공용 표현 — **떠 있는 별 하나 + 문구(+ 선택 액션)**.
 *
 * 알림/친구/내 다이어리/차단 목록/타인 프로필 등 빈 상태가 전부 "검은 배경에 회색 한 줄"이라
 * 신규 사용자가 가장 많이 보는 화면이 제일 허전했다. 별 언어(크리스탈 별 + 부유 + 스파클)를
 * 그대로 써서 "아직 비어 있음"도 앱의 일부처럼 보이게 한다.
 *
 * 장식은 전부 [Canvas] 로 그리고 터치를 받지 않는다 — 액션 버튼만 클릭 대상.
 *
 * @param starType/[starColorIndex] 화면 성격에 맞는 별(예: 알림=골드, 친구=민트).
 * @param actionLabel null 이 아니면 문구 아래에 알약 버튼을 띄운다.
 */
@Composable
fun StaryEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    starType: Int = 1,
    starColorIndex: Int = 9,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = remember(starColorIndex) { StarStyle.colorOf(starColorIndex) }

    // 6초 주기의 부유/반짝임 위상 — 지도 마커 애니메이션과 같은 결(느리게, 은은하게).
    val transition = rememberInfiniteTransition(label = "empty-float")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "empty-phase",
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                // 후광 + 궤도 스파클 3개(별보다 느리게 돈다).
                Canvas(modifier = Modifier.size(104.dp)) {
                    val r = size.minDimension / 2f
                    val bob = sin(phase) * 5f.dp.toPx()

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(center.x, center.y + bob),
                            radius = r * 0.9f,
                        ),
                        radius = r * 0.9f,
                        center = Offset(center.x, center.y + bob),
                    )

                    repeat(3) { i ->
                        val a = phase * 0.45f + i * (2f * Math.PI / 3f).toFloat()
                        val orbit = r * 0.66f
                        val sx = center.x + cos(a) * orbit
                        val sy = center.y + bob + sin(a) * orbit * 0.55f
                        // 뒤로 돌 때(사인 음수) 더 흐리게 — 깊이감.
                        val depth = ((sin(a) + 1f) / 2f).coerceIn(0f, 1f)
                        drawCircle(
                            color = accent.copy(alpha = 0.18f + 0.42f * depth),
                            radius = (1.6f + 1.1f * depth).dp.toPx(),
                            center = Offset(sx, sy),
                        )
                    }
                }

                StarShapeIcon(
                    type = starType,
                    colorIndex = starColorIndex,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            translationY = sin(phase) * 5.dp.toPx()
                            // 아주 미세한 크기 맥동(1.0 ~ 1.06).
                            val pulse = 1f + 0.06f * ((sin(phase * 1.7f) + 1f) / 2f)
                            scaleX = pulse; scaleY = pulse
                            alpha = 0.92f
                        },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = TextMain, fontFamily = MinSans, fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    color = TextMuted, fontFamily = MinSans, fontSize = 12.5.sp,
                    lineHeight = 18.sp, textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent.copy(alpha = 0.14f))
                        .border(1.dp, Accent.copy(alpha = 0.34f), RoundedCornerShape(50))
                        .clickable { onAction() }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                ) {
                    Text(
                        actionLabel,
                        color = Accent, fontFamily = MinSans, fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}
