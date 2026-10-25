package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 불러오는 동안 자리 표시 — 내용 모양의 흐린 틀 위로 빛이 1.7초마다 지나간다.
 * (오픈소스 compose-shimmer(Apache-2.0)를 참고해 필요한 만큼만 직접 구현 — 라이브러리 없이 40줄 남짓)
 * 빛은 그리기 단계에서만 읽어 리컴포즈 없이 움직인다. iOS `Skeleton.swift` 패리티.
 */

private val SkBase = Color(0x129FB3E8)
private val SkLight = Color(0x339FB3E8)

/** 0→1 을 1.7초마다 되풀이 — 한 화면의 틀들이 같은 박자로 반짝이게 하나만 만들어 나눠 쓴다. */
@Composable
fun rememberShimmer(): State<Float> =
    rememberInfiniteTransition(label = "shimmer")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "shimmer")

/** 흐린 틀 + 지나가는 빛. [phase] 를 주면 빛이 그만큼 늦게 온다(고리처럼 차례로 빛나게). */
fun Modifier.skeleton(shimmer: State<Float>, shape: Shape = RoundedCornerShape(6.dp), phase: Float = 0f): Modifier =
    clip(shape).drawBehind {
        val w = size.width
        val c = -0.5f * w + 2f * w * ((shimmer.value + phase) % 1f) // 빛 띠 가운데: 왼쪽 밖 → 오른쪽 밖
        drawRect(Brush.horizontalGradient(listOf(SkBase, SkLight, SkBase), startX = c - 0.45f * w, endX = c + 0.45f * w))
    }

/**
 * 첫 응답을 기다리는 동안 true. 응답이 끝내 안 오면(오프라인·권한 오류로 리스너가 조용히 끝난 경우)
 * [maxMs] 뒤 false 가 되어 빈 화면 안내로 넘어간다 — 자리 표시가 영원히 돌지 않게.
 */
@Composable
fun rememberStillLoading(ready: Boolean, maxMs: Long = 6_000): Boolean {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(maxMs); timedOut = true }
    return !ready && !timedOut
}

/** 목록 행 모양 틀 — (동그라미) + 두 줄 + 오른쪽 짧은 칸. 내 다이어리·알림·친구 목록. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 6, avatar: Boolean = true) {
    val shimmer = rememberShimmer()
    Column(modifier) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (avatar) {
                    Box(Modifier.size(40.dp).skeleton(shimmer, CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.72f).height(11.dp).skeleton(shimmer))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.46f).height(9.dp).skeleton(shimmer))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(36.dp).height(10.dp).skeleton(shimmer))
            }
        }
    }
}

/** 별 도감 고리 모양 틀 — 고리를 따라 빛이 차례로 돈다. */
@Composable
fun SkeletonRing(modifier: Modifier = Modifier, dots: Int = 16) {
    val shimmer = rememberShimmer()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val r = minOf(maxWidth, maxHeight) * 0.36f
        repeat(dots) { i ->
            val a = -PI / 2 + 2 * PI * i / dots
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = r * cos(a).toFloat(), y = r * sin(a).toFloat())
                    .size(14.dp)
                    .skeleton(shimmer, CircleShape, phase = 1f - i.toFloat() / dots),
            )
        }
        Box(Modifier.align(Alignment.Center).size(width = 64.dp, height = 40.dp).skeleton(shimmer, RoundedCornerShape(10.dp)))
    }
}
