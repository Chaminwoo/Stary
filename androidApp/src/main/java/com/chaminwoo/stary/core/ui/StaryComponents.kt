package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.StarStyle

val PageBg = Color(0xFF0E1018)
val CardBg = Color(0xFF181C2A)
val CardBgTop = Color(0xFF1E2334)
val CardBorder = Color.White.copy(alpha = 0.06f)
val TextMain = Color(0xFFF2F4FA)
val TextMuted = Color(0xFF8A92A6)

fun Modifier.appCard(radius: Dp = 16.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(CardBgTop.copy(alpha = 0.85f), CardBg.copy(alpha = 0.85f))))
    .border(1.dp, CardBorder, RoundedCornerShape(radius))

/**
 * 지도 원형 버튼용 볼록(엠보스) 테두리 — 좌상단 밝은 하이라이트에서 우하단으로 갈수록 짙은
 * 남색으로 떨어지는 **사선** 그라데이션이라, 좌상단에서 빛을 받아 살짝 튀어나온 듯 보인다.
 * (수직이면 하이라이트가 정수리에 일직선으로 걸려 어색하다는 피드백 — 2026-07-18 사선화)
 */
fun Modifier.raisedCosmicBorder(width: Dp = 0.75.dp, shape: Shape = CircleShape): Modifier = this.border(
    width = width,
    // linearGradient 기본 방향 = 좌상단(Offset.Zero) → 우하단(Offset.Infinite) 사선.
    brush = Brush.linearGradient(
        0.00f to Color(0xFF9FB3E8).copy(alpha = 0.45f), // 좌상단 하이라이트(은은한 청백)
        0.45f to Color(0xFF3A4570).copy(alpha = 0.35f), // 중간 남색
        1.00f to Color(0xFF10142B).copy(alpha = 0.30f), // 우하단 짙은 남색(그림자 쪽)
    ),
    shape = shape,
)

/** UploadScreen 피커와 DiaryCard에서 공통으로 쓰는 별 모양 아이콘. StarStyle 경로와 동일. */
@Composable
fun StarShapeIcon(type: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            StarStyle.drawCrystalFill(canvas.nativeCanvas, type, listOf(color.toArgb()), 0f, 0f, size.minDimension)
        }
    }
}

/** 색 인덱스 기반 별 아이콘 — 수정 결정(크리스탈) 패싯 채움(그라데이션 색은 2색 혼합). */
@Composable
fun StarShapeIcon(type: Int, colorIndex: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            StarStyle.drawCrystalFill(canvas.nativeCanvas, type, colorIndex, 0f, 0f, size.minDimension)
        }
    }
}

@Composable
fun StarDiaryButton(
    modifier: Modifier = Modifier,
    text: String = "로그인 버튼",
    onClick: () -> Unit = {}
) {
    val creamTop = Color(0xFFF7EDD8)
    val creamBottom = Color(0xFFE9D6AE)
    val charcoal = Color(0xFF2C2723)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(creamTop, creamBottom)))
                .clickable(onClick = onClick)
                .padding(horizontal = 26.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = charcoal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = charcoal,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .appCard(20.dp)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = value,
            fontSize = 25.sp,
            fontWeight = FontWeight.Light,
            color = TextMain
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            icon()
            Text(text = label, fontSize = 20.sp, color = TextMuted)
        }
    }
}
