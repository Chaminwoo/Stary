package com.chaminwoo.stary.feature.home.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
private const val STEP_COUNT = 7

/**
 * 첫 실행 코치마크 — 한 번에 하나씩 주요 컨트롤만 스포트라이트로 밝히고 나머지는 어둡게.
 * 화면을 탭하면 다음 단계로 넘어가고, 마지막에서 탭하면 닫힌다. 단계 전환/등장/퇴장은 페이드.
 *
 * 단계: 0 메뉴(좌상단) · 1 위치 필터(좌하단) · 2 내 위치 · 3 별자리 · 4 배경음악 · 5 업로드(우측 세로 버튼).
 * 우측 버튼 중심의 콘텐츠 하단 거리(dp): 내위치 228 / 별자리 168 / 음악 108 / 업로드 44.
 */
@Composable
fun MainOnboardingOverlay(onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val dp = density.density
    fun px(v: Float) = v * dp

    // 등장/퇴장 페이드: false→true 로 시작해 fade-in, finish() 시 fade-out 후 onDismiss.
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }
    LaunchedEffect(visibleState.currentState, visibleState.targetState) {
        if (!visibleState.targetState && !visibleState.currentState) onDismiss()
    }
    val finish = { visibleState.targetState = false }
    val advance = { if (step >= STEP_COUNT - 1) finish() else step++ }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(280)),
        exit = fadeOut(tween(280)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable { advance() }
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val contentBottom = hPx - navBottomPx

            // 단계별 스포트라이트 구멍 (중심x, 중심y, 반지름) — px
            // 우측 FAB 컬럼: end=16, 너비=업로드(56dp) 기준 CenterHorizontally → 48dp 버튼도 중심 end 44dp.
            // 메뉴: TopAppBar nav 아이콘(start 4 + 24) ≈ 28dp, 바 높이 64 중앙 → statusTop+32.
            // 필터: BottomStart start=16 + 24 = 40dp, bottom=20 + 24 = 44dp.
            val rightX = wPx - px(44f)
            val holes = listOf(
                Triple(px(40f), contentBottom - px(44f), px(30f)),        // 1 필터
                Triple(rightX, contentBottom - px(228f), px(30f)),        // 2 내위치
                Triple(rightX, contentBottom - px(168f), px(30f)),        // 3 별자리
                Triple(rightX, contentBottom - px(108f), px(30f)),        // 4 음악
                Triple(rightX, contentBottom - px(44f), px(36f)),         // 5 업로드
                Triple(px(28f), statusTopPx + px(32f), px(28f)), //메뉴
                Triple(wPx / 2f, hPx / 2f, 0f),                           // 마무리(스포트라이트 없음)
            )
            val target = holes[step.coerceIn(0, holes.lastIndex)]
            // 스포트라이트가 단계 사이를 부드럽게 이동
            val cx by animateFloatAsState(target.first, tween(340), label = "cx")
            val cy by animateFloatAsState(target.second, tween(340), label = "cy")
            val r by animateFloatAsState(target.third, tween(340), label = "r")

            // 어두운 스크림 + 타깃만 부드럽게 뚫기 (offscreen 레이어라야 BlendMode.Clear 동작)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawRect(Color.Black.copy(alpha = 0.78f))
                // 반지름이 0보다 클 때만 구멍을 뚫는다. (마지막 단계는 r=0 → 스포트라이트 없이 전체 어둡게.
                //  radialGradient 는 radius<=0 이면 IllegalArgumentException 으로 크래시하므로 반드시 가드)
                if (r > 0f) {
                    val soft = r * 1.35f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black, Color.Black, Color.Transparent),
                            center = Offset(cx, cy), radius = soft
                        ),
                        radius = soft, center = Offset(cx, cy), blendMode = BlendMode.Clear
                    )
                    // 강조 링
                    drawCircle(
                        color = Mint.copy(alpha = 0.55f), radius = r, center = Offset(cx, cy),
                        style = Stroke(width = px(2f))
                    )
                }
            }

            // 안내 말풍선 — 단계 전환 시 크로스페이드
            Crossfade(targetState = step, animationSpec = tween(260), label = "coach-pill") { s ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    when (s) {

                        0 -> CoachPill(
                            "보고 싶은 다이어리만 골라서 볼 수 있어요",
                            Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 88.dp)
                        )
                        1 -> CoachPill(
                            "시점을 현재 내 위치로 이동해요",
                            Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 196.dp)
                        )
                        2 -> CoachPill(
                            "별들을 이어 별자리를 만들어요",
                            Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 136.dp)
                        )
                        3 -> CoachPill(
                            "음악 및 효과음을 켜고 끌 수 있어요",
                            Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 78.dp)
                        )
                        4 -> CoachPill(
                            "이 버튼을 눌러 다이어리를 올려요",
                            Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 18.dp)
                        )
                        5 -> CoachPill(
                            "내 다이어리 · 프로필 · 업적 · 친구를 여기서 관리해요",
                            Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 66.dp)
                        )
                        6 -> CoachPill(
                            "지금부터 우주를 탐험하고, 별들에 이야기를 남겨보세요!",
                            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                            big = true
                        )
                    }
                }
            }

            // 건너뛰기 (우상단)
            Text(
                "건너뛰기",
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 14.dp, top = 8.dp)
                    .clickable { finish() }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun CoachPill(text: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Box(
        modifier = modifier
            .widthIn(max = if (big) 300.dp else 232.dp)
            .background(Color(0xFF14181C), RoundedCornerShape(if (big) 18.dp else 14.dp))
            .border(if (big) 1.5.dp else 1.dp, Brush.linearGradient(listOf(Mint, Blue)), RoundedCornerShape(if (big) 18.dp else 14.dp))
            .padding(horizontal = if (big) 22.dp else 14.dp, vertical = if (big) 18.dp else 10.dp)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = if (big) 17.sp else 14.sp,
            lineHeight = if (big) 25.sp else 19.sp,
            fontWeight = if (big) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
