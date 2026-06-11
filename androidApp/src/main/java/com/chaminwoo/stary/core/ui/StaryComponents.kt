package com.chaminwoo.stary.core.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.model.Diary
import java.text.SimpleDateFormat
import java.util.Locale

val PageBg = Color(0xFF0E1018)
val CardBg = Color(0xFF181C2A)
val CardBgTop = Color(0xFF1E2334)                 // 카드 상단 살짝 밝게
val CardBorder = Color.White.copy(alpha = 0.06f)   // 헤어라인
val TextMain = Color(0xFFF2F4FA)
val TextMuted = Color(0xFF8A92A6)


fun Modifier.appCard(radius: Dp = 16.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(CardBgTop.copy(alpha = 0.85f), CardBg.copy(alpha = 0.85f))))
    .border(1.dp, CardBorder, RoundedCornerShape(radius))

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
        // 뒤에 깔리는 따뜻한 발광 (API 31+, 하위 버전은 효과 생략)
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
    val shape = RoundedCornerShape(14.dp)

    // 일반: 카드와 같은 레이어드 배경 / 위험: 옅은 빨강 틴트
    val bgBrush = if (isDestructive)
        Brush.verticalGradient(
            listOf(
                Color(0xFFFF4F4F).copy(alpha = 0.12f),
                Color(0xFFFF4F4F).copy(alpha = 0.07f)
            )
        )
    else
        Brush.verticalGradient(listOf(CardBgTop, CardBg))

    val borderColor = if (isDestructive)
        Color(0xFFFF4F4F).copy(alpha = 0.30f)     // 빨간 헤어라인
    else
        Color.White.copy(alpha = 0.06f)            // 일반 헤어라인

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
            letterSpacing = 0.2.sp,   // 라벨 살짝 정돈
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
            //letterSpacing = (-0.5).sp,
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // 정사각형 카드 느낌
            .appCard(18.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = diary.title,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Text(
                text = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
                    .format(java.util.Date(diary.createdAt)),
                fontSize = 15.sp,
                color = TextMuted
            )
        }
    }
}
