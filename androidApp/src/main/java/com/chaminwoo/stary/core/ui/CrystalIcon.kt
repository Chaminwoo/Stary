package com.chaminwoo.stary.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.chaminwoo.stary.core.designsystem.StarStyle

/**
 * 벡터 아이콘 실루엣을 [StarStyle] 크리스탈 파편으로 채운 비트맵을 굽는다(별과 같은 재질).
 * 아이콘을 먼저 그려 알파 마스크로 쓰고, SRC_IN 레이어에 파편을 그려 아이콘 모양 안에만 남긴다.
 * (프로필 부유 통계 아이콘에서 사용.)
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
