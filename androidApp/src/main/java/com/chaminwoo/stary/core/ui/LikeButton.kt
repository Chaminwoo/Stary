package com.chaminwoo.stary.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.Haptics
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 하트가 튀는 시간(ms). 파편은 이보다 조금 더 길게 남는다. */
private const val POP_MS = 260
private const val BURST_MS = 620
private const val SHARD_COUNT = 12

/** 버스트가 퍼지는 반지름 — 버튼(44dp)보다 크지만 **레이아웃엔 참여하지 않는다**(아래 주석 참고). */
private val BURST_RADIUS = 36.dp

/**
 * 좋아요 버튼 — 하트 pop + 크리스탈 파편 버스트 + 숫자 롤링.
 *
 * 기존엔 Material 하트 아이콘 색만 바뀌고 토스트가 떴다("좋아요 ♥"). 앱에는 이미 파티클 연출
 * (`FloatingStatBox` 버스트, `StarBirth`)이 있는데 **가장 자주 누르는 버튼**만 밋밋했던 걸 맞춘 것.
 * 토스트는 없앴다 — 버스트 자체가 피드백이라 중복이다.
 *
 * 좋아요를 **켤 때만** 버스트/진동이 나간다(해제는 조용히) — 취소 동작까지 축하하면 과하다.
 *
 * @param isLiked 현재 내가 눌렀는지. @param count 표시할 좋아요 수.
 * @param accent 파편 색(별 색과 맞추면 다이어리마다 다른 색으로 터진다).
 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFFF6B8A),
) {
    // 버스트 1회분 = nonce 증가. 파편 각도/길이는 nonce 로 결정론적 랜덤.
    var burstNonce by remember { mutableIntStateOf(0) }
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }

    LaunchedEffect(burstNonce) {
        if (burstNonce == 0) return@LaunchedEffect
        burst.snapTo(0f)
        pop.snapTo(0.72f)
        // 하트: 살짝 눌렸다가 통통 튀어오른다.
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(burstNonce) {
        if (burstNonce == 0) return@LaunchedEffect
        burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
    }

    val shards = remember(burstNonce) {
        val rnd = Random(burstNonce * 7919)
        List(SHARD_COUNT) { i ->
            val base = (i.toFloat() / SHARD_COUNT) * 360f
            Shard(
                angleDeg = base + rnd.nextFloat() * 22f - 11f,
                distance = 0.62f + rnd.nextFloat() * 0.55f,
                size = 0.5f + rnd.nextFloat() * 0.7f,
                spin = rnd.nextFloat() * 220f - 110f,
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // ⚠️ 크기를 버튼(44dp)에 **고정**한다. 예전엔 파편 Canvas(72dp)가 Box 의 자식이라
        // 버스트가 뜨는 동안만 Box 가 72dp 로 커져 좋아요/공유/수정 행 전체가 밀렸다.
        // 파편은 matchParentSize + 고정 반지름으로 **경계 밖에 그리기만** 한다(측정에 안 잡힘).
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            // 파편 — 하트 뒤로 퍼져 나간다(터치 영역 밖이라 클릭에 영향 없음).
            if (burst.value > 0f && burst.value < 1f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val p = burst.value
                    val fade = (1f - p).coerceIn(0f, 1f)
                    val r = BURST_RADIUS.toPx()
                    shards.forEach { s ->
                        // easeOutCubic: 처음엔 빠르게 튀어나가고 끝에서 잦아든다.
                        val ease = 1f - (1f - p) * (1f - p) * (1f - p)
                        val dist = r * s.distance * ease
                        val rad = Math.toRadians(s.angleDeg.toDouble())
                        val cx = center.x + (cos(rad) * dist).toFloat()
                        val cy = center.y + (sin(rad) * dist).toFloat()
                        val side = (5.2.dp.toPx() * s.size) * (1f - 0.45f * p)
                        rotate(degrees = s.angleDeg + s.spin * p, pivot = Offset(cx, cy)) {
                            // 마름모 파편 — 크리스탈 별 파편과 같은 언어.
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(cx, cy - side)
                                lineTo(cx + side * 0.62f, cy)
                                lineTo(cx, cy + side)
                                lineTo(cx - side * 0.62f, cy)
                                close()
                            }
                            drawPath(path, color = accent.copy(alpha = 0.85f * fade))
                        }
                    }
                    // 퍼지는 링 — 파편보다 빨리 사라진다.
                    val ringP = (p / 0.55f).coerceIn(0f, 1f)
                    if (ringP < 1f) {
                        drawCircle(
                            color = accent.copy(alpha = 0.30f * (1f - ringP)),
                            radius = r * (0.25f + 0.8f * ringP),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx() * (1f - ringP)),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val willLike = !isLiked
                        if (willLike) {
                            burstNonce++
                            Haptics.medium()
                        }
                        onToggle()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_like),
                    tint = if (isLiked) accent else Color(0xFF8A92A6),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
            }
        }

        Spacer(Modifier.width(2.dp))

        // 숫자 롤링 — 늘면 위로, 줄면 아래로 흐른다.
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "like-count",
        ) { c ->
            Text(
                "$c",
                fontSize = 14.sp,
                color = if (isLiked) accent.copy(alpha = 0.9f) else Color(0xFF8A92A6),
                fontWeight = if (isLiked) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/** 파편 한 조각(각도/거리/크기/회전). */
private data class Shard(
    val angleDeg: Float,
    val distance: Float,
    val size: Float,
    val spin: Float,
)
