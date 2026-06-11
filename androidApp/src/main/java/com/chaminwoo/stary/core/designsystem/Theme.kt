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

@Composable
fun StaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
