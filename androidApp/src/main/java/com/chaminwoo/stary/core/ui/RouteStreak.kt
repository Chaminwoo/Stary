package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlin.math.sin

/**
 * 화면 전환 별 잔상(34-10) — 라우트가 바뀔 때 **가는 빛줄기 2개**가 화면을 대각선으로 스치고 사라진다.
 * NavGraph 의 깊이감 줌 전환(320ms)과 같은 길이라 "우주를 항해하며 화면을 지난다"는 느낌으로 이어진다.
 *
 * - 드릴인(push) = 좌하 → 우상, 뒤로가기(pop) = 반대 방향.
 * - 앱 첫 진입(최초 목적지)에는 재생하지 않는다.
 * - 장식 전용: 터치를 받지 않으므로 전환 직후 조작을 막지 않는다.
 *
 * 방향 판정: NavController 의 백스택 깊이 API 에 의존하지 않고, **본 적 있는 엔트리 id 가 다시 오면 pop**
 * 이라는 규칙으로 자체 스택을 관리한다(라우트 문자열 비교보다 안정적 — 같은 라우트 중첩에도 맞는다).
 */
@Composable
fun RouteTransitionStreak(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // +1 = 드릴인, -1 = 뒤로가기. nonce 로 같은 방향 연속 전환도 재시작시킨다.
    var direction by remember { mutableIntStateOf(1) }
    var nonce by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) } // 1 = 재생 끝(아무것도 안 그림)

    LaunchedEffect(navController) {
        val seen = ArrayList<String>()
        var first = true
        navController.currentBackStackEntryFlow.collect { entry ->
            val idx = seen.indexOf(entry.id)
            if (idx >= 0) {
                // 이미 지나온 엔트리로 돌아왔다 = 뒤로가기. 그 위쪽 스택은 버린다.
                while (seen.size > idx + 1) seen.removeAt(seen.size - 1)
                direction = -1
            } else {
                seen.add(entry.id)
                direction = 1
            }
            if (first) {
                first = false // 앱 첫 화면 진입은 연출 없음
            } else {
                nonce++
            }
        }
    }

    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(340, easing = FastOutSlowInEasing))
    }

    val p = progress.value
    if (p >= 1f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 드릴인 = 좌하 → 우상, 뒤로가기 = 우상 → 좌하.
        val from = if (direction > 0) Offset(-0.12f * w, 1.12f * h) else Offset(1.12f * w, -0.12f * h)
        val to = if (direction > 0) Offset(1.12f * w, -0.12f * h) else Offset(-0.12f * w, 1.12f * h)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len

        // 빛줄기 2개 — 살짝 어긋난 시점/경로(옆으로 밀어 나란히 지나가지 않게).
        repeat(2) { i ->
            val t = ((p - i * 0.10f) / 0.90f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@repeat
            val fade = sin(t * 3.14159f) // 나타났다 사라짐
            val tail = (110f + i * 40f).dp.toPx()
            // 경로에 수직으로 살짝 오프셋(줄기마다 다른 궤적)
            val nx = -uy * (if (i == 0) -0.14f else 0.16f) * w
            val ny = ux * (if (i == 0) -0.14f else 0.16f) * w
            val head = Offset(from.x + dx * t + nx, from.y + dy * t + ny)
            val tailPt = Offset(head.x - ux * tail, head.y - uy * tail)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f * fade)),
                    start = tailPt, end = head,
                ),
                start = tailPt,
                end = head,
                strokeWidth = (1.6f - i * 0.5f).dp.toPx(),
                cap = StrokeCap.Round,
            )
            // 머리 부분 작은 글로우
            drawCircle(Color.White.copy(alpha = 0.35f * fade), radius = 2.2f.dp.toPx(), center = head)
        }
    }
}
