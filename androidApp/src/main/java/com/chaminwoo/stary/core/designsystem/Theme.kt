package com.chaminwoo.stary.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    background        = Bg,
    surface           = Surface1,
    surfaceVariant    = Surface2,
    outline           = Outline,
    primary           = Accent,
    onPrimary         = Bg,
    secondary         = TextSub,
    onSecondary       = TextPrimary,
    onBackground      = TextPrimary,
    onSurface         = TextPrimary,
    onSurfaceVariant  = TextSub,
    error             = AccentRed,
    onError           = Color.White,
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
