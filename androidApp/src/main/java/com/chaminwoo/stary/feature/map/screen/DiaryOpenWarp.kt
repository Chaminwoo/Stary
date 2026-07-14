package com.chaminwoo.stary.feature.map.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.feature.map.OrsRouting
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.sin
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

/**
 * 지도 왜곡 연출 데이터 — 스냅샷 비트맵 + 파장 시작 위치(0..1) + 다이어리 id/별색.
 * [navigateAfter] true 면 파장 후 세부 화면으로(별 탭), false 면 파장만 내고 지도에 머문다(알림 포커스).
 */
internal class DiaryOpenWarpData(
    val bitmap: Bitmap,
    val ox: Float,
    val oy: Float,
    val id: String,
    val colorIndex: Int,
    val navigateAfter: Boolean,
    /** 30m 안에서 합쳐진 별들의 (모양, 색) — 파장 중심에서 작은 파티클로 퍼진다(합쳐진 별 열람 시만). */
    val burstStars: List<Pair<Int, Int>> = emptyList(),
    /** 합쳐진 멤버 다이어리 id(우선순위 정렬) — 2개 이상이면 파장 후 카드 뷰어로 이동. */
    val clusterIds: List<String> = emptyList(),
)

/**
 * 다이어리 진입 직전 연출 — 캡처한 지도 스냅샷을 1.3초간 **별 위치에서 퍼지는 물결**로 굴절시킨 뒤 [onFinished].
 * (위아래 흔들림이 아니라 방사형 파장.) `drawBitmapMesh` 로 그려 소프트웨어 렌더(에뮬레이터)에서도 동작.
 * 세부 화면 자체는 왜곡 없이 멀쩡하게 들어간다.
 */
@Composable
internal fun DiaryOpenWarp(data: DiaryOpenWarpData, onFinished: () -> Unit) {
    val progress = remember(data) { Animatable(0f) }
    LaunchedEffect(data) {
        progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        onFinished()
    }
    val p = progress.value
    val rippleColor = StarStyle.colorOf(data.colorIndex)
    val meshPaint = remember { Paint().apply { isFilterBitmap = true; isAntiAlias = true } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w * data.ox
        val cy = h * data.oy
        val maxR = max(
            max(hypot(cx, cy), hypot(w - cx, cy)),
            max(hypot(cx, h - cy), hypot(w - cx, h - cy))
        )
        val front = p * maxR
        val amp = 46f * (1f - p) // 파면이 퍼질수록 약해져 잔잔해짐

        // 스냅샷을 메시 격자로 그려 별 위치에서 방사형으로 굴절
        val mw = 14
        val mh = 14
        val verts = FloatArray((mw + 1) * (mh + 1) * 2)
        var i = 0
        for (row in 0..mh) {
            for (col in 0..mw) {
                val x = w * col / mw
                val y = h * row / mh
                val dx = x - cx
                val dy = y - cy
                val dist = hypot(dx, dy)
                val delta = dist - front
                val env = exp(-(delta * delta) / (220f * 220f)) // 넓은 밴드
                val disp = sin(delta * 0.045f) * env * amp
                if (dist > 0.001f) {
                    verts[i++] = x + dx / dist * disp
                    verts[i++] = y + dy / dist * disp
                } else {
                    verts[i++] = x
                    verts[i++] = y
                }
            }
        }
        drawIntoCanvas { c ->
            c.nativeCanvas.drawBitmapMesh(data.bitmap, mw, mh, verts, 0, null, 0, meshPaint)
        }

        // 합쳐진 별 파티클 — 파장 중심(별 위치)에서 각 멤버의 모양/색이 작은 별로 퍼져 나간다.
        if (data.burstStars.isNotEmpty() && p > 0.02f) {
            drawIntoCanvas { c ->
                val n = data.burstStars.size
                data.burstStars.forEachIndexed { i, (type, colorIdx) ->
                    // 황금비 시퀀스로 방향/거리/크기를 결정론적으로 흩뿌린다(매 프레임 동일).
                    val golden = (i * 0.61803398f) % 1f
                    val ang = (i.toFloat() / n) * 2f * Math.PI.toFloat() + golden * 0.9f
                    val dist = (70.dp.toPx() + golden * 90.dp.toPx()) * p
                    val x = cx + kotlin.math.cos(ang) * dist
                    val y = cy + sin(ang) * dist
                    val sizePx = (12f + golden * 8f).dp.toPx() * (1f - 0.35f * p)
                    val alpha = ((1f - p) * 1.4f).coerceIn(0f, 1f)
                    if (alpha <= 0.01f) return@forEachIndexed
                    val color = StarStyle.colorOf(colorIdx).copy(alpha = alpha).toArgb()
                    val path = android.graphics.Path(StarStyle.starPath(type, sizePx)).apply {
                        offset(x - sizePx / 2f, y - sizePx / 2f)
                    }
                    c.nativeCanvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        maskFilter = android.graphics.BlurMaskFilter(4.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    })
                    StarStyle.drawCrystalFill(
                        c.nativeCanvas, type, colorIdx,
                        x - sizePx / 2f, y - sizePx / 2f, sizePx,
                        alpha = (alpha * 255).toInt()
                    )
                }
            }
        }

        // 파장 링 — 별 위치에서 퍼지는 빛 테두리(후광 + 굴절 띠 + 가장자리 선).
        if (p < 1f && front >= 1f) {
            val center = Offset(cx, cy)
            val radius = front
            val fade = 1f - p
            drawIntoCanvas { c ->
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (22f * fade).coerceAtLeast(3f).dp.toPx()
                    color = rippleColor.copy(alpha = (fade * 0.7f).coerceIn(0f, 1f)).toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(20.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.nativeCanvas.drawCircle(cx, cy, radius, glow)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, rippleColor.copy(alpha = fade * 0.22f), Color.Transparent),
                    center = center, radius = radius.coerceAtLeast(1f)
                ),
                radius = radius, center = center,
                style = Stroke(width = (30f * fade).coerceAtLeast(1f).dp.toPx())
            )
            drawCircle(
                color = rippleColor.copy(alpha = fade * 0.7f),
                radius = radius, center = center,
                style = Stroke(width = (3f * fade).coerceAtLeast(0.6f).dp.toPx())
            )
        }
    }
}
