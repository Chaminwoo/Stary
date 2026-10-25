package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.designsystem.StaryColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 유리(흐림) — chrisbanes/haze. 흐릴 화면을 `Modifier.hazeSource(state)` 로 잡고, 그 위에 뜨는 바·시트가
 * [glassBar]/[glassSheet] 로 뒤를 흐리게 비춘다. Android 12 미만은 흐림 없이 거의 불투명한 남색 판.
 *
 * - 바·시트는 **자기가 비출 원본의 바깥(형제)** 에 둔다. 원본 안에 넣으면 자기 자신을 비추게 된다.
 * - ⚠️ 지도는 따로 그려지는 화면(GLSurfaceView)이라 흐림이 먹지 않는다 — 지도 위 상단바는
 *   [com.chaminwoo.stary.core.designsystem.TopBarScrim] 유지.
 * - ⚠️ 목록 카드에는 쓰지 않는다(카드마다 흐림을 돌리면 저가 폰에서 스크롤이 무거워진다) — 바와 시트만.
 * - iOS 는 `.ultraThinMaterial`(내비바는 `UIBlurEffect`) + 같은 남색 틴트.
 */
object StaryGlass {
    /** 상단바 — 화면 목록(NavGraph) 전체를 비춘다. */
    val Bar = HazeStyle(
        backgroundColor = StaryColors.Bg,
        tints = listOf(HazeTint(Color(0xFF181E34).copy(alpha = 0.62f))),
        blurRadius = 22.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(Color(0xF0101422)),
    )

    /** 아래에서 올라오는 시트 — 상단바보다 한 단 밝은 판(확인 창 유리와 같은 색). */
    val Sheet = HazeStyle(
        backgroundColor = StaryColors.Bg,
        tints = listOf(HazeTint(Color(0xFF262D48).copy(alpha = 0.66f))),
        blurRadius = 26.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(Color(0xF71B2036)),
    )

    /**
     * 드로어 — 상단바와 같은 유리(뒤 화면이 흐리게 비친다). 판이 커서 조금 더 흐리게.
     * 지도 위에선 지도를 직접 흐릴 수 없어, 열리는 동안 찍어 둔 한 장([com.chaminwoo.stary.core.util.MapUiState.frozenMap])을 비춘다.
     */
    val Drawer = HazeStyle(
        backgroundColor = StaryColors.Bg,
        tints = listOf(HazeTint(Color(0xFF181E34).copy(alpha = 0.60f))),
        blurRadius = 28.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(Color(0xF0101422)),
    )

    /** 유리 판 테두리 — 위가 밝고 아래로 옅어지는 1dp(빛을 받은 유리 가장자리). */
    val Edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)))
}

/** 상단바 유리 + 아래쪽 별빛 선 한 줄. */
fun Modifier.glassBar(state: HazeState): Modifier = this
    .hazeEffect(state, StaryGlass.Bar)
    .drawWithContent {
        drawContent()
        val y = size.height - 0.5.dp.toPx()
        drawLine(StaryColors.Accent.copy(alpha = 0.13f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }

/** 시트·드로어 유리 — [shape] 로 깎은 판 + 위가 밝은 테두리. */
fun Modifier.glassSheet(state: HazeState, shape: Shape, style: HazeStyle = StaryGlass.Sheet): Modifier = this
    .clip(shape)
    .hazeEffect(state, style)
    .border(1.dp, StaryGlass.Edge, shape)
