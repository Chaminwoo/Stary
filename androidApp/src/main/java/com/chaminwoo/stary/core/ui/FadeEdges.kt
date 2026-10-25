package com.chaminwoo.stary.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 스크롤 영역의 위·아래 끝을 [top]/[bottom] 만큼 투명하게 녹인다 — 위아래에 떠 있는 버튼·알약 앞에서
 * 글이 칼같이 잘려 보이지 않게(업로드 저장 버튼, 별 도감 목록). iOS 는 `.mask` 그라데이션으로 같은 효과.
 */
fun Modifier.fadeEdges(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val t = top.toPx()
        val b = bottom.toPx()
        if (t > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = t),
                size = Size(size.width, t),
                blendMode = BlendMode.DstIn,
            )
        }
        if (b > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - b, endY = size.height),
                topLeft = Offset(0f, size.height - b),
                size = Size(size.width, b),
                blendMode = BlendMode.DstIn,
            )
        }
    }
