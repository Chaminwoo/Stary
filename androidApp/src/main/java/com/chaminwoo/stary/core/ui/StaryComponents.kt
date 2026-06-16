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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import java.text.SimpleDateFormat
import java.util.Locale

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

/** UploadScreen 피커와 DiaryCard에서 공통으로 쓰는 별 모양 아이콘. StarStyle 경로와 동일. */
@Composable
fun StarShapeIcon(type: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = StarStyle.starPath(type, size.minDimension)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(
                path,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                }
            )
        }
    }
}

@Composable
fun StarDiaryButton(
    text: String = "별 다이어리 남기기",
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val creamTop = Color(0xFFF7EDD8)
    val creamBottom = Color(0xFFE9D6AE)
    val charcoal = Color(0xFF2C2723)
    val glow = Color(0xFFF3E4C0)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .blur(28.dp)
                .background(glow.copy(alpha = 0.55f), RoundedCornerShape(50))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(creamTop, creamBottom)))
                .clickable(onClick = onClick)
                .padding(horizontal = 30.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = charcoal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = charcoal,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun StyleButton(
    text: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) Color(0xFFFF6B6B) else TextMuted

    Box(
        modifier = modifier
            .appCard(20.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            color = textColor
        )
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
            fontWeight = FontWeight.SemiBold,
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

@Composable
fun DiaryCard(
    diary: Diary,
    showStar: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .appCard(18.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // 별 모양을 카드 가운데 크게 배치
        if (showStar) {
            StarShapeIcon(
                type = diary.starType,
                color = StarStyle.colorOf(diary.starColor),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
            )
        }

        // 제목 — 좌측 상단
        Text(
            text = diary.title,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMain,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 날짜 — 우측 하단
        Text(
            text = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
                .format(java.util.Date(diary.createdAt)),
            fontSize = 15.sp,
            color = TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
