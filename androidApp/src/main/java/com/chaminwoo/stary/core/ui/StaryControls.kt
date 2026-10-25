package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StaryColors

/** 버튼 종류 — 주(달빛 크림) / 보조(유리) / 위험(장미색, 지우기·차단). iOS `StaryButtonKind` 패리티. */
enum class StaryButtonKind { Primary, Ghost, Danger }

/**
 * 앱 공용 버튼 — 둥근 캡슐(2026-10-26 "딱딱해 보인다" → 감성 다이어리에 맞게 부드럽게).
 * 주 버튼은 로그인 화면 크림 캡슐(`CreamCapsuleButton`)과 같은 달빛 크림, 보조는 뒤가 비치는 유리, 위험은 옅은 장미색.
 * 누르면 살짝 작아지며 밝아진다(물결 효과 없음).
 * 기본은 화면 아래 큰 버튼(높이 50), [compact] 는 확인 창·목록 행 안의 작은 버튼(높이 40).
 * [loading] 이면 글자 대신 도는 별을 보여 주고 눌리지 않는다(흐려지지는 않는다 — 진행 중이지 막힌 게 아니라서).
 * 글자가 칸보다 길면 한 줄 안에서 조금 줄어든다(확인 창은 두 버튼이 폭을 반씩 나눠 쓴다).
 */
@Composable
fun StaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: StaryButtonKind = StaryButtonKind.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "buttonPress")
    val ink = kind.ink
    val fontSize = if (compact) 14.sp else 15.sp
    Row(
        modifier
            .graphicsLayer {
                val s = 1f - 0.02f * press
                scaleX = s; scaleY = s
                alpha = if (enabled || loading) 1f else 0.4f
            }
            .defaultMinSize(minHeight = if (compact) 40.dp else 50.dp)
            .clip(Capsule)
            .background(kind.fill(press))
            .border(1.dp, kind.edge, Capsule)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = if (compact) 18.dp else 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            StarLoadingIndicator(size = if (compact) 16.dp else 20.dp, color = ink)
            return@Row
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(if (compact) 15.dp else 18.dp))
            Spacer(Modifier.width(6.dp))
        }
        BasicText(
            text,
            style = TextStyle(
                color = ink, fontFamily = MinSans, fontSize = fontSize,
                fontWeight = if (kind == StaryButtonKind.Ghost) FontWeight.Medium else FontWeight.SemiBold,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = fontSize, stepSize = 0.5.sp),
        )
    }
}

private val Capsule = RoundedCornerShape(50)

private val StaryButtonKind.ink: Color
    get() = when (this) {
        StaryButtonKind.Primary -> Color(0xFF2C2723) // 크림 위 숯색(로그인 버튼과 같은 값)
        StaryButtonKind.Ghost -> Color(0xFFE3E7F1)
        StaryButtonKind.Danger -> Color(0xFF3E1A22)
    }

/** 면 — 누를수록([press] 0..1) 위쪽부터 밝아진다. 보조는 흰빛이 조금 짙어진다. */
private fun StaryButtonKind.fill(press: Float): Brush = when (this) {
    StaryButtonKind.Primary -> softPill(Color(0xFFF7EDD8), Color(0xFFE9D6AE), press)
    StaryButtonKind.Danger -> softPill(Color(0xFFF5C9CF), Color(0xFFE5A3AE), press)
    StaryButtonKind.Ghost -> SolidColor(Color.White.copy(alpha = 0.08f + 0.06f * press))
}

private fun softPill(top: Color, bottom: Color, press: Float): Brush = Brush.verticalGradient(
    listOf(
        lerp(top, Color.White, 0.35f * press).copy(alpha = 0.94f),
        lerp(bottom, Color.White, 0.25f * press).copy(alpha = 0.94f),
    )
)

/** 테두리 — 위가 밝고 아래로 옅어지는 1dp(빛을 받은 가장자리). */
private val StaryButtonKind.edge: Brush
    get() = when (this) {
        StaryButtonKind.Ghost -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f)))
        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.08f)))
    }

/**
 * 앱 공용 칩 — 선택 / 선택 안 됨 / 잠김 세 상태가 어디서나 같은 모양.
 * 선택되면 [accent] 로 꽉 채우고 글씨는 어둡게(화면마다 주제색이 있으면 그 색을 넘긴다).
 * [onClick] 이 null 이면 누를 수 없는 상태 표시(예: 친구 검색의 "친구"·"요청함").
 * 잠긴 칩도 누를 수 있다 — 왜 잠겼는지 알려 주는 건 호출부 몫.
 */
@Composable
fun StaryChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = StaryColors.Accent,
    locked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val on = selected && !locked
    val content = when {
        on -> StaryColors.Bg
        locked -> StaryColors.Muted
        else -> SoftInk
    }
    Row(
        modifier
            .clip(shape)
            .background(if (on) accent else ChipIdle)
            .then(
                if (on) Modifier
                else Modifier.border(1.dp, if (locked) Color.White.copy(alpha = 0.08f) else accent.copy(alpha = 0.28f), shape)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lead = if (locked) Icons.Filled.Lock else icon
        if (lead != null) {
            Icon(lead, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label, color = content, fontFamily = MinSans, fontSize = 13.sp, maxLines = 1,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 면으로만 나눈 선택 줄(테두리 없음) — 고른 칸 아래로 옅은 별빛 판이 미끄러져 간다.
 * 업로드 화면 공개 범위·별 고르기 시트의 모양/색 탭. iOS `StarySegmented` 패리티.
 * ⚠️ 칸 폭을 나눠 쓰므로 [modifier] 로 폭을 정해 줘야 한다(fillMaxWidth 또는 width).
 */
@Composable
fun StarySegmented(
    options: List<Pair<String, ImageVector?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BoxWithConstraints(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(SegTrack)
            .padding(3.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, tween(220, easing = FastOutSlowInEasing), label = "segment")
        val thumb = RoundedCornerShape(9.dp)
        Box(Modifier.offset(x = x).width(w).height(38.dp).clip(thumb).background(SegThumb))
        Row {
            options.forEachIndexed { i, (label, icon) ->
                val on = i == selected
                Row(
                    Modifier
                        .width(w)
                        .height(38.dp)
                        .clip(thumb)
                        .clickable(enabled = enabled && !on) { onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = if (on) StaryColors.Accent else StaryColors.Muted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label, color = if (on) StaryColors.Text else StaryColors.Muted, fontFamily = MinSans, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    )
                }
            }
        }
    }
}

private val SoftInk = Color(0xFFC5CBDB)  // 보조 버튼·칩 글씨(흐린 글자보다 한 단 밝게)
private val ChipIdle = Color(0x8C181E34) // 별하늘 위에서도 글씨가 읽히는 반투명 남색
private val SegTrack = Color(0x99181E34) // 선택 줄 바탕
private val SegThumb = Color(0x2E9FB3E8) // 고른 칸(별빛 18%)
