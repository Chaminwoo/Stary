package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.roundToInt

/**
 * 벡터 아이콘 실루엣을 [StarStyle] 크리스탈 파편으로 채운 비트맵을 굽는다(별과 같은 재질).
 * 아이콘을 먼저 그려 알파 마스크로 쓰고, SRC_IN 레이어에 파편을 그려 아이콘 모양 안에만 남긴다.
 */
fun bakeCrystalIcon(
    painter: VectorPainter,
    color: Color,
    seed: Int,
    sizePx: Int,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val size = Size(sizePx.toFloat(), sizePx.toFloat())
    CanvasDrawScope().draw(Density(1f), layoutDirection, androidx.compose.ui.graphics.Canvas(image), size) {
        with(painter) { draw(size) }
    }
    val canvas = android.graphics.Canvas(image.asAndroidBitmap())
    val maskPaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    }
    val layer = canvas.saveLayer(0f, 0f, size.width, size.height, maskPaint)
    StarStyle.drawCrystalFacets(canvas, silhouette = null, seed = seed, colors = listOf(color.toArgb()), left = 0f, top = 0f, sizePx = size.width)
    canvas.restoreToCount(layer)
    return image
}

/**
 * 크리스탈 결정 재질의 원판 — 원형 버튼 몸체 전체를 별과 같은 파편으로 채운다.
 * 언뜻 보면 그라데이션 단색 버튼, 자세히 보면 결정 무늬가 드러난다.
 * 무늬는 정적이므로 2배 해상도로 1회만 구워 두고 매 프레임엔 그리기만 한다.
 *
 * @param colors 1색(단색) 또는 2색(그라데이션) — 파편이 위치에 따라 두 색 사이를 오간다.
 */
@Composable
fun CrystalCircle(
    size: Dp,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    seed: Int = 4,
) {
    val density = LocalDensity.current
    val bakePx = with(density) { (size * 2).toPx() }.roundToInt().coerceIn(48, 512)
    val bitmap = remember(colors, seed, bakePx) {
        val image = ImageBitmap(bakePx, bakePx)
        val canvas = android.graphics.Canvas(image.asAndroidBitmap())
        val s = bakePx.toFloat()
        val silhouette = android.graphics.Path().apply {
            addCircle(s / 2f, s / 2f, s / 2f, android.graphics.Path.Direction.CW)
        }
        StarStyle.drawCrystalFacets(canvas, silhouette, seed, colors.map { it.toArgb() }, 0f, 0f, s)
        image
    }
    Image(bitmap, contentDescription = null, modifier = modifier.size(size))
}
