package com.chaminwoo.stary.core.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StaryColors
import java.util.function.Consumer

/**
 * 앱 공용 확인 창 — 머티리얼 `AlertDialog` 와 같은 칸(title / text / confirmButton / dismissButton).
 * iOS `StaryDialog.swift` 패리티(모서리 18 · 최대 폭 340 · 화면 좌우 32).
 *
 * 유리 패널: Android 12+ 는 창 뒤를 흐리게 하고(창 단위 흐림이라 지도처럼 따로 그려지는 화면도 흐려진다)
 * 패널은 반투명 남색. 흐림을 못 쓰면(11 이하, 절전 모드, 기기가 끈 경우) 거의 불투명한 패널로 바뀐다.
 * 버튼은 [StaryButton] compact — 취소 = Ghost, 확인 = Primary, 지우기·차단 = Danger.
 * 내용([text])이 길면 그 칸만 스크롤되고 버튼 줄은 늘 보인다.
 */
@Composable
fun StaryDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside, usePlatformDefaultWidth = false),
    ) {
        val glass = rememberBlurBehind()
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(if (glass) GlassFill else SolidFill)
                .border(1.dp, StaryGlass.Edge, shape)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides StaryColors.Text) {
                if (title != null) ProvideTextStyle(TitleStyle, title)
            }
            if (text != null) {
                if (title != null) Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalContentColor provides StaryColors.Muted) {
                    Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        ProvideTextStyle(BodyStyle, text)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/**
 * 이 확인 창의 창(window) 뒤를 흐리게 한다. 흐림이 실제로 켜졌으면 true — 패널을 더 투명하게 칠한다.
 * 절전 모드 등으로 도중에 꺼지면 리스너가 알려 줘 불투명 패널로 바꾼다.
 */
@Composable
private fun rememberBlurBehind(): Boolean {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val radius = with(LocalDensity.current) { 22.dp.roundToPx() }
    var on by remember(window) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window?.windowManager?.isCrossWindowBlurEnabled == true)
    }
    DisposableEffect(window) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            window?.setDimAmount(DIM_SOLID)
            return@DisposableEffect onDispose {}
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = radius }
        val wm = window.windowManager
        val listener = Consumer<Boolean> { enabled ->
            on = enabled
            window.setDimAmount(if (enabled) DIM_GLASS else DIM_SOLID)
        }
        wm.addCrossWindowBlurEnabledListener(listener)
        listener.accept(wm.isCrossWindowBlurEnabled)
        onDispose { wm.removeCrossWindowBlurEnabledListener(listener) }
    }
    return on
}

private const val DIM_GLASS = 0.45f // 흐림이 있으면 덜 어둡게 해도 패널이 뜬다
private const val DIM_SOLID = 0.6f
private val GlassFill = Color(0x9E262D48)
private val SolidFill = Color(0xF71B2036)
private val TitleStyle = TextStyle(fontFamily = MinSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
private val BodyStyle = TextStyle(fontFamily = MinSans, fontSize = 14.sp, lineHeight = 21.sp)
