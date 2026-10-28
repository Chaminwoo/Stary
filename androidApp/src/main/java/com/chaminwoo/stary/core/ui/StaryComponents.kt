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
import com.chaminwoo.stary.core.designsystem.StaryColors

val PageBg = StaryColors.Bg
val CardBg = StaryColors.Surface
val CardBgTop = StaryColors.Raised
val CardBorder = Color.White.copy(alpha = 0.06f)
val TextMain = StaryColors.Text
val TextMuted = StaryColors.Muted

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

/**
 * UploadScreen 피커와 DiaryCard에서 공통으로 쓰는 별 모양 아이콘. StarStyle 경로와 동일.
 * [shine] = 지도 별 같은 빛번짐 + 유리 결 빛([CrystalShine], 2026-10-28) — 색의 알파(잠김 · 흐림)만큼 같이 옅어진다.
 */
@Composable
fun StarShapeIcon(type: Int, color: Color, modifier: Modifier = Modifier, shine: Boolean = true) {
    CrystalStarIcon(type, color, modifier, shine) { canvas, left, top, side ->
        StarStyle.drawCrystalFill(canvas, type, listOf(color.toArgb()), left, top, side)
    }
}

/** 색 인덱스 기반 별 아이콘 — 수정 결정(크리스탈) 패싯 채움(그라데이션 색은 2색 혼합) + 빛번짐 · 유리 결 빛. */
@Composable
fun StarShapeIcon(type: Int, colorIndex: Int, modifier: Modifier = Modifier, shine: Boolean = true) {
    CrystalStarIcon(type, StarStyle.colorOf(colorIndex), modifier, shine) { canvas, left, top, side ->
        StarStyle.drawCrystalFill(canvas, type, colorIndex, left, top, side)
    }
}

/** 크리스탈 별 본체(요소 가운데 정사각) — [shine] 이면 뒤에 빛번짐, 위에 유리 결 빛 겹. */
@Composable
private fun CrystalStarIcon(
    type: Int,
    glow: Color,
    modifier: Modifier,
    shine: Boolean,
    fill: (android.graphics.Canvas, Float, Float, Float) -> Unit,
) {
    if (!shine) {
        Canvas(modifier = modifier) {
            val side = size.minDimension
            drawIntoCanvas { canvas -> fill(canvas.nativeCanvas, (size.width - side) / 2f, (size.height - side) / 2f, side) }
        }
        return
    }
    val argb = glow.toArgb()
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val side = size.minDimension
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                CrystalShine.drawHalo(nc, argb, left + side / 2f, top + side / 2f, side)
                CrystalShine.drawGlow(nc, CrystalShine.starGlow(type, side), argb, left, top, side)
                fill(nc, left, top, side)
            }
        }
        CrystalSheen(Modifier.matchParentSize(), alpha = glow.alpha) { nc, left, top, side ->
            CrystalShine.drawStarMask(nc, type, left, top, side)
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
            fontSize = 24.sp,
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
