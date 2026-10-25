package com.chaminwoo.stary.core.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StaryColors
import java.util.function.Consumer
import androidx.compose.ui.res.stringResource
import com.chaminwoo.stary.R

/**
 * 앱 공용 확인 창 — 머티리얼 `AlertDialog` 와 같은 칸(title / text / confirmButton / dismissButton).
 * iOS `StaryDialog.swift` 패리티(모서리 18 · 최대 폭 340 · 화면 좌우 32).
 *
 * 판은 [StaryPopupPanel](창 뒤 흐림 + 반투명 남색 유리). 버튼은 [StaryButton] compact — 취소 = Ghost, 확인 = Primary,
 * 지우기·차단 = Danger — 이고 아래 줄을 반씩 나눠 꽉 채운다(2026-10-26 — 오른쪽에 작게 몰린 버튼이 딱딱해 보였다).
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
        StaryPopupPanel(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)) {
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
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissButton != null) ButtonSlot(dismissButton)
                ButtonSlot(confirmButton)
            }
        }
    }
}

/** 확인 창 버튼 칸 — 줄을 반씩(하나면 통째로) 나눠 갖고, 안의 버튼이 칸 폭·높이 44 까지 늘어난다. */
@Composable
private fun RowScope.ButtonSlot(button: @Composable () -> Unit) {
    Box(Modifier.weight(1f).heightIn(min = 44.dp), propagateMinConstraints = true) { button() }
}

/**
 * 팝업 판 — 창 뒤를 흐리게 한 반투명 남색 유리(모서리 18 · 위가 밝은 1dp 테두리 · 최대 폭 [maxWidth] · 화면 좌우 [screenPadding]).
 * [StaryDialog] 와 직접 꾸민 팝업(약관·업데이트 안내·첫 방문 안내·업적 등)이 모두 이 판을 쓴다(iOS `glassPanel` 패리티).
 * Android 12+ 는 창 뒤가 흐려지고(창 단위라 지도처럼 따로 그려지는 화면도 흐려진다), 흐림을 못 쓰면
 * (11 이하·절전 모드·기기가 끈 경우) 거의 불투명한 판으로 바뀐다.
 * ⚠️ `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)) { }` 안에서 — 그 창에 흐림을 건다.
 */
@Composable
fun StaryPopupPanel(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 340.dp,
    screenPadding: Dp = 32.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = rememberBlurBehind()
    Column(
        modifier
            .padding(horizontal = screenPadding)
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .clip(PopupShape)
            .background(if (glass) GlassFill else SolidFill)
            .border(1.dp, StaryGlass.Edge, PopupShape)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * 직접 꾸민 팝업의 아래 두 버튼 — [StaryDialog] 버튼 줄과 같은 모양(왼쪽 취소 = 유리, 오른쪽 확인 = 크림, 줄을 반씩 · 높이 44).
 * [loading] 이면 확인 버튼에 도는 별이 돌고 취소는 눌리지 않는다.
 */
@Composable
fun StaryPopupButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(R.string.common_cancel),
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StaryButton(
            dismissText, onDismiss, Modifier.weight(1f).heightIn(min = 44.dp),
            kind = StaryButtonKind.Ghost, enabled = !loading, compact = true,
        )
        StaryButton(
            confirmText, onConfirm, Modifier.weight(1f).heightIn(min = 44.dp),
            enabled = confirmEnabled, compact = true, loading = loading,
        )
    }
}

/** 팝업 머리 아이콘 — 은은한 달빛 원(예전 민트→블루 그라데이션 원 대신). 첫 방문 안내·업데이트 안내 등. */
@Composable
fun StaryPopupBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x33F7EDD8), Color(0x12F7EDD8))))
            .border(1.dp, StaryGlass.Edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFF3E6C8), modifier = Modifier.size(28.dp))
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
private val PopupShape = RoundedCornerShape(18.dp)
private val GlassFill = Color(0x9E262D48)
private val SolidFill = Color(0xF71B2036)
private val TitleStyle = TextStyle(fontFamily = MinSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
private val BodyStyle = TextStyle(fontFamily = MinSans, fontSize = 14.sp, lineHeight = 21.sp)
