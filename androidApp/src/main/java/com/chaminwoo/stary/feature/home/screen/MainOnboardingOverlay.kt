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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
private const val STEP_COUNT = 7

/** 말풍선을 스포트라이트 원의 어느 쪽에 붙일지. */
private enum class PillSide { Above, Below, LeftOf, Center }

/**
 * 첫 실행 코치마크 — 한 번에 하나씩 주요 컨트롤만 스포트라이트로 밝히고 나머지는 어둡게.
 * 화면을 탭하면 다음 단계로 넘어가고, 마지막에서 탭하면 닫힌다. 단계 전환/등장/퇴장은 페이드.
 *
 * 단계: 0 위치 필터(좌하단) · 1 내 위치 · 2 별자리 · 3 몰입(지도만 보기) · 4 업로드 ·
 *       5 메뉴(좌상단) · 6 마무리. (지도 우측 FAB 컬럼 순서와 동일 — iOS `MainOnboardingOverlay.swift` 와 동일)
 * 우측 버튼 중심의 콘텐츠 하단 거리(dp): 내위치 228 / 별자리 168 / 몰입 108 / 업로드 44.
 *
 * ⚠️ **말풍선 위치는 하드코딩하지 않는다.** 예전엔 원(스포트라이트)과 말풍선이 각각 화면 가장자리
 *    기준 dp 상수로 따로 적혀 있어서, 지도 버튼이 바뀌어 원 좌표만 손보면 말풍선이 그대로 남아
 *    둘이 어긋났다. 지금은 말풍선이 [AnchoredCoachPill] 로 **그 단계의 원 좌표에서 계산**되므로
 *    원을 옮기면 말풍선이 따라오고, 기기/인셋/반응형 배율이 달라도 항상 붙어 있다.
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
                Triple(px(40f), contentBottom - px(44f), px(30f)),        // 0 필터
                Triple(rightX, contentBottom - px(228f), px(30f)),        // 1 내위치
                Triple(rightX, contentBottom - px(168f), px(30f)),        // 2 별자리
                Triple(rightX, contentBottom - px(108f), px(30f)),        // 3 몰입(지도만 보기)
                Triple(rightX, contentBottom - px(44f), px(36f)),         // 4 업로드
                Triple(px(28f), statusTopPx + px(32f), px(28f)),          // 5 메뉴
                Triple(wPx / 2f, hPx / 2f, 0f),                           // 6 마무리(스포트라이트 없음)
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

            // 안내 말풍선 — 단계 전환 시 크로스페이드. 위치는 위 스포트라이트(cx,cy,r) 에서 파생된다.
            Crossfade(targetState = step, animationSpec = tween(260), label = "coach-pill") { s ->
                val text = when (s) {
                    0 -> "보고 싶은 다이어리만 골라서 볼 수 있어요"
                    1 -> "시점을 현재 내 위치로 이동해요"
                    2 -> "별들을 이어 별자리를 만들어요"
                    // ⚠️ 이 자리의 버튼은 **배경음악 토글이 아니라 몰입(지도만 보기)** 이다.
                    //    지도 FAB 이 음악 → 몰입으로 바뀐 뒤에도 문구가 음악으로 남아 있었다.
                    3 -> "지도에만 집중해서 별들을 감상해요"
                    4 -> "이 버튼을 눌러 다이어리를 올려요"
                    5 -> "내 다이어리 · 프로필 · 업적 · 친구 등\n여러 설정을 여기서 관리해요"
                    else -> "지금부터 우주를 탐험하고,\n별들에 이야기를 남겨보세요!"
                }
                // 필터는 화면 맨 아래라 위로, 메뉴는 맨 위라 아래로, 우측 FAB 은 왼쪽에 붙인다.
                val side = when (s) {
                    0 -> PillSide.Above
                    in 1..4 -> PillSide.LeftOf
                    5 -> PillSide.Below
                    else -> PillSide.Center
                }
                AnchoredCoachPill(
                    text = text,
                    side = side,
                    cx = cx, cy = cy, r = r,
                    safeTopPx = statusTopPx, safeBottomPx = navBottomPx,
                    big = s >= STEP_COUNT - 1,
                )
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

/**
 * 스포트라이트 원([cx],[cy],[r] — 모두 px)에 **붙여서** 말풍선을 놓는다.
 *
 * 말풍선 크기를 먼저 재고 좌표를 계산하는 단일 패스 [Layout] 이라 첫 프레임부터 제자리에 뜬다
 * (측정 후 offset 을 주는 방식은 한 프레임 (0,0) 에 번쩍인다).
 * 화면 밖으로 나가지 않도록 상태바/내비바를 포함한 여백 안으로 클램프한다.
 */
@Composable
private fun AnchoredCoachPill(
    text: String,
    side: PillSide,
    cx: Float,
    cy: Float,
    r: Float,
    safeTopPx: Float,
    safeBottomPx: Float,
    big: Boolean = false,
) {
    val d = LocalDensity.current
    val marginPx = with(d) { 10.dp.toPx() }   // 화면 가장자리 최소 여백
    val gapPx = with(d) { 12.dp.toPx() }      // 원 테두리와 말풍선 사이 간격

    Layout(
        content = { CoachPill(text, big = big) },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val w = constraints.maxWidth
        val h = constraints.maxHeight

        var x: Float
        var y: Float
        when (side) {
            // 원의 왼쪽 옆 — 세로 중심을 원 중심에 맞춘다(우측 FAB 컬럼).
            PillSide.LeftOf -> {
                x = cx - r - gapPx - placeable.width
                y = cy - placeable.height / 2f
            }
            // 원 위 — 가로는 원의 왼쪽 끝에 맞춰 시작(좌하단 필터).
            PillSide.Above -> {
                x = cx - r
                y = cy - r - gapPx - placeable.height
            }
            // 원 아래 — 가로는 원의 왼쪽 끝에 맞춰 시작(좌상단 메뉴).
            PillSide.Below -> {
                x = cx - r
                y = cy + r + gapPx
            }
            PillSide.Center -> {
                x = (w - placeable.width) / 2f
                y = (h - placeable.height) / 2f
            }
        }

        val minX = marginPx
        val maxX = (w - placeable.width - marginPx).coerceAtLeast(minX)
        val minY = safeTopPx + marginPx
        val maxY = (h - safeBottomPx - marginPx - placeable.height).coerceAtLeast(minY)
        x = x.coerceIn(minX, maxX)
        y = y.coerceIn(minY, maxY)

        layout(w, h) { placeable.place(x.roundToInt(), y.roundToInt()) }
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
            fontWeight = if (big) FontWeight.Light else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
