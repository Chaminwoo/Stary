package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 내 다이어리 정렬 기준. (DiaryStarBox / 다이얼 공용) */
enum class DiarySort(val label: String) {
    LATEST("최신순"), POPULAR("인기순"), DISTANCE("거리순")
}

/** 공중에 떠 있는 별 한 개. */
private class StarBody(
    val diary: Diary,
    val r: Float,
    val color: Color,
    val type: Int,
    // 떠다니는 위상/진폭
    val phase: Float,
    val floatAmp: Float,
    val floatSpeed: Float,
    // 자연스러운 흩뿌림용 셀 내 지터(셀 크기 대비 비율)
    val jitterX: Float,
    val jitterY: Float,
    // 정착 위치(애니메이션 대상)
    var baseX: Float,
    var baseY: Float,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
)

private const val CELL_H_DP = 92

/**
 * 내 다이어리 별들을 공중에 자연스럽게 띄워 보여준다. 시작은 최신순.
 * [sortMode] 변경 시 그 순서대로 하나씩 끌려와 재배치되고, 별들은 은은하게 떠다닌다.
 */
@Composable
fun DiaryStarBox(
    diaries: List<Diary>,
    sortMode: DiarySort,
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val n = diaries.size
    // 화면 폭에 맞춘 열 수(3~5), 너무 빽빽하지 않게
    val cols = (((config.screenWidthDp - 24) / 92f).toInt()).coerceIn(3, 5)
    val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
    val boxHeightDp = rows * CELL_H_DP + 24

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(boxHeightDp.dp)
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        // 별 생성(다이어리 집합 고정 시 동일 배치). 좋아요 많을수록 약간 큼.
        val bodies = remember(diaries) {
            val rnd = Random(diaries.size * 131 + 17)
            diaries.map { d ->
                val likeF = (d.likeCount.coerceIn(0, 100)).toFloat() / 100f
                val sizeVar = 0.9f + rnd.nextFloat() * 0.25f
                val r = with(density) { ((11f + 8f * likeF) * sizeVar).dp.toPx() }
                // 시작은 화면 곳곳에 흩어진 상태 → 최신순 슬롯으로 날아 들어온다
                val sx = rnd.nextFloat() * wPx
                val sy = rnd.nextFloat() * hPx
                StarBody(
                    diary = d,
                    r = r,
                    color = StarStyle.colorOf(d.starColor),
                    type = d.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1),
                    phase = rnd.nextFloat() * 6.2832f,
                    floatAmp = with(density) { (3f + rnd.nextFloat() * 4f).dp.toPx() },
                    floatSpeed = 0.5f + rnd.nextFloat() * 0.6f,
                    jitterX = (rnd.nextFloat() - 0.5f) * 0.12f,
                    jitterY = (rnd.nextFloat() - 0.5f) * 0.10f,
                    baseX = sx, baseY = sy
                )
            }
        }

        // 현재 위치(거리순용). 캐시가 비어 있으면(아직 측정 전) 비동기로 한 번 가져와 채운다.
        // (기존엔 캐시만 읽어 null 이면 거리순이 적용되지 않았음)
        val context = LocalContext.current
        var here by remember(diaries) { mutableStateOf(LocationHelper.getCurrentLatLng()) }
        LaunchedEffect(diaries) {
            if (here == null) {
                LocationHelper.getCurrentLocation(context)?.let {
                    here = com.chaminwoo.stary.core.geo.LatLng(it.latitude, it.longitude)
                }
            }
        }

        var tick by remember { mutableIntStateOf(0) }
        var floatT by remember { mutableFloatStateOf(0f) }

        // 정렬/배치 + 떠다님을 단일 프레임 루프로. sortMode 바뀌면 재시작(현재 위치에서 새 슬롯으로).
        LaunchedEffect(bodies, sortMode, wPx, hPx, here) {
            if (bodies.isEmpty()) return@LaunchedEffect

            val origin = here
            val order = when (sortMode) {
                DiarySort.LATEST -> bodies.sortedByDescending { it.diary.createdAt }
                DiarySort.POPULAR -> bodies.sortedByDescending { it.diary.likeCount }
                DiarySort.DISTANCE ->
                    if (origin != null) bodies.sortedBy {
                        LocationHelper.distanceBetween(origin.latitude, origin.longitude, it.diary.latitude, it.diary.longitude)
                    } else bodies
            }

            val cellW = wPx / cols
            val cellH = hPx / rows
            order.forEachIndexed { idx, b ->
                val col = idx % cols
                val row = idx / cols
                // 홀수 행은 살짝 밀어 벌집처럼(딱딱한 격자 느낌 제거) — 아주 약간만
                val rowShift = if (row % 2 == 1) cellW * 0.12f else 0f
                val tx = col * cellW + cellW / 2f + rowShift + b.jitterX * cellW
                val ty = row * cellH + cellH / 2f + b.jitterY * cellH
                b.startX = b.baseX; b.startY = b.baseY
                b.targetX = tx.coerceIn(b.r, wPx - b.r)
                b.targetY = ty.coerceIn(b.r, hPx - b.r)
            }

            // 모이는 단계 없이, 각 별이 제자리에서 슬롯으로 하나씩(staggered) 이동
            val dur = 300f       // 개별 별 이동 시간(ms)
            val stagger = 22f    // 별 사이 시차(ms) → 하나씩 끌려옴
            var startNanos = 0L
            while (true) {
                val frame = withFrameNanos { it }
                if (startNanos == 0L) startNanos = frame
                val elapsed = (frame - startNanos) / 1_000_000f
                floatT = elapsed / 1000f
                order.forEachIndexed { idx, b ->
                    val lt = ((elapsed - idx * stagger) / dur).coerceIn(0f, 1f)
                    val e = easeOutCubic(lt)
                    b.baseX = b.startX + (b.targetX - b.startX) * e
                    b.baseY = b.startY + (b.targetY - b.startY) * e
                }
                tick++
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bodies) {
                    detectTapGestures { off ->
                        val hit = bodies.minByOrNull { hypot(off.x - it.baseX, off.y - it.baseY) }
                        if (hit != null && hypot(off.x - hit.baseX, off.y - hit.baseY) <= hit.r * 1.8f) {
                            onDiaryClick(hit.diary.id)
                        }
                    }
                }
        ) {
            if (tick >= 0) {
                for (b in bodies) {
                    val dx = sin(floatT * b.floatSpeed + b.phase) * b.floatAmp
                    val dy = cos(floatT * b.floatSpeed * 0.8f + b.phase) * b.floatAmp * 0.7f
                    drawStar(b, b.baseX + dx, b.baseY + dy)
                }
            }
        }
    }
}

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u
}

/** 별 한 개 그리기 — 후광(radial glow) + StarStyle 별 모양. */
private fun DrawScope.drawStar(b: StarBody, cx: Float, cy: Float) {
    val center = Offset(cx, cy)
    // 후광
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(b.color.copy(alpha = 0.42f), Color.Transparent),
            center = center,
            radius = b.r * 2.4f
        ),
        radius = b.r * 2.4f,
        center = center
    )
    // 별 모양 (StarStyle 경로 — 지도 마커와 동일)
    val sizePx = b.r * 2f
    val path = android.graphics.Path(StarStyle.starPath(b.type, sizePx)).apply {
        offset(cx - b.r, cy - b.r)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(
            path,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = b.color.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(b.r * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        )
        canvas.nativeCanvas.drawPath(
            path,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = b.color.toArgb()
            }
        )
    }
}
