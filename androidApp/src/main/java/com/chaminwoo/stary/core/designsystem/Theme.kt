package com.chaminwoo.stary.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 머티리얼 부품이 쓰는 칸을 전부 [StaryColors] 로 채운다 — 비워 두면 머티리얼 기본(보라 회색)이 나온다
// (확인 창 = surfaceContainerHigh, 메뉴 = surfaceContainer, 시트 = surfaceContainerLow, 구분선 = outlineVariant).
private val AppColorScheme = darkColorScheme(
    primary                 = StaryColors.Accent,
    onPrimary               = StaryColors.Bg,
    primaryContainer        = Color(0xFF26304F),
    onPrimaryContainer      = StaryColors.Text,
    secondary               = StaryColors.Muted,
    onSecondary             = StaryColors.Text,
    secondaryContainer      = StaryColors.Raised,
    onSecondaryContainer    = StaryColors.Text,
    tertiary                = StaryColors.Mint,
    onTertiary              = StaryColors.Bg,
    background              = StaryColors.Bg,
    onBackground            = StaryColors.Text,
    surface                 = StaryColors.Surface,
    onSurface               = StaryColors.Text,
    surfaceVariant          = StaryColors.Raised,
    onSurfaceVariant        = StaryColors.Muted,
    surfaceTint             = StaryColors.Accent,
    inverseSurface          = StaryColors.Text,
    inverseOnSurface        = StaryColors.Bg,
    error                   = StaryColors.Danger,
    onError                 = Color.White,
    outline                 = StaryColors.Line,
    outlineVariant          = StaryColors.Line,
    surfaceBright           = Color(0xFF252B3F),
    surfaceDim              = StaryColors.Bg,
    surfaceContainerLowest  = Color(0xFF0A0C13),
    surfaceContainerLow     = Color(0xFF131726),
    surfaceContainer        = StaryColors.Surface,
    surfaceContainerHigh    = StaryColors.Raised,
    surfaceContainerHighest = Color(0xFF252B3F),
)

/**
 * 앱 전역 테마. 색/타이포와 함께 **반응형 UI 배율**([ProvideResponsiveUi])을 적용한다 —
 * 작은 폰에선 전체가 비례 축소, 태블릿에선 완만히 확대되어 S22+ 기준 디자인이 깨지지 않는다.
 * ⚠️ 앱에서 단 한 번만(최상위) 호출할 것 — 중첩하면 배율이 곱해진다.
 */
@Composable
fun StaryTheme(content: @Composable () -> Unit) {
    ProvideResponsiveUi {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
