package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StaryColors

/** 버튼 종류 — 주(남색 그라데이션 + 별빛 테두리) / 보조(테두리만) / 위험(지우기·차단). iOS `StaryButtonKind` 패리티. */
enum class StaryButtonKind { Primary, Ghost, Danger }

/**
 * 앱 공용 버튼. 기본은 화면 아래 큰 버튼(높이 50), [compact] 는 확인 창·목록 행 안의 작은 버튼(높이 40).
 * 주 버튼 테두리는 지도 원형 버튼과 같은 [raisedCosmicBorder].
 * [loading] 이면 글자 대신 도는 별을 보여 주고 눌리지 않는다(흐려지지는 않는다 — 진행 중이지 막힌 게 아니라서).
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
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val content = if (kind == StaryButtonKind.Ghost) SoftInk else Color.White
    Row(
        modifier
            .defaultMinSize(minHeight = if (compact) 40.dp else 50.dp)
            .alpha(if (enabled || loading) 1f else 0.4f)
            .clip(shape)
            .then(kind.surface(shape))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            StarLoadingIndicator(size = if (compact) 16.dp else 20.dp, color = content)
            return@Row
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (compact) 15.dp else 18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text, color = content, fontFamily = MinSans, maxLines = 1,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = if (kind == StaryButtonKind.Ghost) FontWeight.Medium else FontWeight.SemiBold,
        )
    }
}

private fun StaryButtonKind.surface(shape: Shape): Modifier = when (this) {
    StaryButtonKind.Primary -> Modifier
        .background(Brush.verticalGradient(listOf(Color(0xFF3A4676), Color(0xFF111936))))
        .raisedCosmicBorder(width = 1.dp, shape = shape)
    StaryButtonKind.Ghost -> Modifier.border(1.dp, StaryColors.Accent.copy(alpha = 0.32f), shape)
    StaryButtonKind.Danger -> Modifier
        .background(Brush.verticalGradient(listOf(Color(0xFFB04A5C), Color(0xFF6B2033))))
        .border(1.dp, Color(0x66FF96A0), shape)
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
