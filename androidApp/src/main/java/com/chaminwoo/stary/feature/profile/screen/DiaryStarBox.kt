package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val colorIndex: Int,
    val type: Int,
    // 떠다니는 위상/진폭
    val phase: Float,
    val floatAmp: Float,
    val floatSpeed: Float,
    // 자유 회전(우주에 떠 있는 느낌) — 시작 각도 + 초당 회전 속도(±, deg/s)
    val rotPhase: Float,
    val rotSpeed: Float,
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
) {
    // 성능: 블러 후광+크리스탈 패싯은 비싸서 매 프레임 재생성하지 않고 비트맵으로 1회 굽는다.
    var bitmap: android.graphics.Bitmap? = null
    var bitmapCenter: Float = 0f
    var glowPaint: android.graphics.Paint? = null
}

private const val CELL_H_DP = 92

/**
 * 내 다이어리 별들을 공중에 자연스럽게 띄워 보여준다. 시작은 최신순.
 * [sortMode] 변경 시 그 순서대로 하나씩 끌려와 재배치되고, 별들은 은은하게 떠다닌다.
 */
@Composable
fun DiaryStarBox(
    diaries: List<Diary>,
    sortMode: DiarySort,
    sortNonce: Int = 0,
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
                    colorIndex = d.starColor,
                    type = d.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1),
                    phase = rnd.nextFloat() * 6.2832f,
                    floatAmp = with(density) { (3f + rnd.nextFloat() * 4f).dp.toPx() },
                    floatSpeed = 0.5f + rnd.nextFloat() * 0.6f,
                    rotPhase = rnd.nextFloat() * 360f,
                    rotSpeed = (rnd.nextFloat() - 0.5f) * 22f, // ±11 deg/s, 별마다 다른 방향·속도
                    jitterX = (rnd.nextFloat() - 0.5f) * 0.12f,
                    jitterY = (rnd.nextFloat() - 0.5f) * 0.10f,
                    baseX = sx, baseY = sy
                )
            }
        }

        // 별 비트맵/후광 페인트 1회 베이크(같은 모양·색·크기는 공유) — 매 프레임 크리스탈 재생성 방지
        remember(bodies) {
            val cache = HashMap<Int, android.graphics.Bitmap>()
            for (b in bodies) {
                val sizePx = (b.r * 2f).toInt().coerceAtLeast(4) // 양자화(1px) — 같은 키는 같은 비트맵 공유
                val key = (b.type shl 24) or (b.colorIndex shl 16) or sizePx.coerceAtMost(0xFFFF)
                b.bitmap = cache.getOrPut(key) { bakeStarBody(b.type, b.colorIndex, b.color.toArgb(), sizePx.toFloat()) }
                b.bitmapCenter = sizePx * 0.9f // r + margin(0.8r)
                b.glowPaint = makeGlowPaint(b.color, b.r)
            }
            bodies
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

        // ── 별 드래그/홀드 인터랙션 상태 ──
        // 누르고 있으면(또는 끌면) 그 별이 손가락을 따라다니며 커지고, 위에 제목(별색·후광)이 뜬다.
        // 놓으면 제자리로 부드럽게 복귀하고 제목은 페이드아웃. 빠른 탭은 다이어리 열기.
        val scope = rememberCoroutineScope()
        var activeBody by remember(bodies) { mutableStateOf<StarBody?>(null) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        val grabScale = remember { Animatable(1f) }   // 잡았을 때 별 확대 배율
        val titleAlpha = remember { Animatable(0f) }   // 제목 페이드
        var returnJob by remember { mutableStateOf<Job?>(null) }

        // 정렬/배치 + 떠다님을 단일 프레임 루프로. sortMode(또는 같은 정렬 재선택 토큰)가 바뀌면 재시작.
        LaunchedEffect(bodies, sortMode, sortNonce, wPx, hPx, here) {
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
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = bodies.minByOrNull { hypot(down.position.x - it.baseX, down.position.y - it.baseY) }
                            ?.takeIf { hypot(down.position.x - it.baseX, down.position.y - it.baseY) <= it.r * 2.4f }
                            ?: return@awaitEachGesture
                        returnJob?.cancel()
                        activeBody = hit
                        dragPos = Offset(hit.baseX, hit.baseY)

                        var grabbed = false
                        fun grab() {
                            if (grabbed) return
                            grabbed = true
                            scope.launch { grabScale.animateTo(1.9f, tween(180, easing = FastOutSlowInEasing)) }
                            scope.launch { titleAlpha.animateTo(1f, tween(180)) }
                        }
                        // 150ms 이상 누르면(움직이지 않아도) 잡힘 → 커지고 제목 표시
                        val holdJob = scope.launch { delay(150); grab() }

                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                grab()
                            }
                            if (grabbed) {
                                dragPos = change.position
                                change.consume() // 부모 세로 스크롤이 드래그를 가로채지 않게
                            }
                        }
                        holdJob.cancel()

                        if (!grabbed) {
                            // 빠른 탭(잡기 전 떼면) → 다이어리 열기
                            activeBody = null
                            onDiaryClick(hit.diary.id)
                        } else {
                            // 놓으면 제자리로 천천히 복귀 + 제목 페이드아웃
                            returnJob = scope.launch {
                                val from = dragPos
                                launch { grabScale.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
                                launch { titleAlpha.animateTo(0f, tween(620, easing = FastOutSlowInEasing)) }
                                animate(0f, 1f, animationSpec = tween(820, easing = FastOutSlowInEasing)) { t, _ ->
                                    dragPos = lerp(from, Offset(hit.baseX, hit.baseY), t)
                                }
                                activeBody = null
                            }
                        }
                    }
                }
        ) {
            if (tick >= 0) {
                val active = activeBody
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    for (b in bodies) {
                        if (b === active) continue
                        val dx = sin(floatT * b.floatSpeed + b.phase) * b.floatAmp
                        val dy = cos(floatT * b.floatSpeed * 0.8f + b.phase) * b.floatAmp * 0.7f
                        drawStarBaked(nc, b, b.baseX + dx, b.baseY + dy, rotationDeg = b.rotPhase + floatT * b.rotSpeed)
                    }
                }
                // 잡은 별은 손가락 위치에서 확대해 맨 위에 그림 — 회전은 그대로 이어져 놓을 때도 자연스럽게 돈다
                // (확대 중엔 비트맵 업스케일로 파편이 뭉개지지 않게 이 별 하나만 라이브 렌더)
                active?.let { drawStar(it, dragPos.x, dragPos.y, grabScale.value, it.rotPhase + floatT * it.rotSpeed) }
            }
        }

        // 잡은 별 위에 제목(별 색 + 후광) — 손가락을 따라 위쪽에 떠 있고, 놓으면 부드럽게 사라진다.
        activeBody?.let { titled ->
            val titleColor = titled.color
            val titleText = titled.diary.title.ifBlank { stringResource(R.string.common_untitled) }
            Text(
                text = titleText,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.merge(
                    TextStyle(shadow = Shadow(color = titleColor.copy(alpha = 0.95f), blurRadius = 28f))
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        alpha = titleAlpha.value
                        translationX = dragPos.x - size.width / 2f
                        translationY = dragPos.y - titled.r * grabScale.value - 18.dp.toPx() - size.height
                    }
            )
        }
    }
}

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u
}

/** 별 본체(블러 후광+크리스탈)를 비트맵으로 1회 굽는다. 여백 = 0.8r(블러 번짐 수용). */
private fun bakeStarBody(type: Int, colorIndex: Int, fallbackColor: Int, sizePx: Float): android.graphics.Bitmap {
    val r = sizePx / 2f
    val margin = r * 0.8f
    val side = ceil(sizePx + margin * 2f).toInt().coerceAtLeast(8)
    val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val path = android.graphics.Path(StarStyle.starPath(type, sizePx)).apply { offset(margin, margin) }
    val shader = StarStyle.fillShader(colorIndex, margin, margin, sizePx)
    canvas.drawPath(
        path,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            if (shader != null) this.shader = shader else this.color = fallbackColor
            maskFilter = android.graphics.BlurMaskFilter(r * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    )
    StarStyle.drawCrystalFill(canvas, type, colorIndex, margin, margin, sizePx)
    return bmp
}

/** 원형 후광 페인트(중심 (0,0) 기준 RadialGradient) — 별마다 1회 생성 후 재사용. */
private fun makeGlowPaint(color: Color, r: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            0f, 0f, r * 2.4f,
            color.copy(alpha = 0.42f).toArgb(), android.graphics.Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
    }

private val starBitmapPaint = android.graphics.Paint(
    android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
)

/** 구운 비트맵으로 별 1개 그리기 — 후광(원형, 회전 무관) + 본체(회전). */
private fun drawStarBaked(nc: android.graphics.Canvas, b: StarBody, cx: Float, cy: Float, rotationDeg: Float) {
    val bmp = b.bitmap ?: return
    val save = nc.save()
    nc.translate(cx, cy)
    b.glowPaint?.let { nc.drawCircle(0f, 0f, b.r * 2.4f, it) }
    nc.rotate(rotationDeg)
    nc.drawBitmap(bmp, -b.bitmapCenter, -b.bitmapCenter, starBitmapPaint)
    nc.restoreToCount(save)
}

/** 별 한 개 그리기 — 후광(radial glow) + StarStyle 별 모양. [scale] 확대, [rotationDeg] 회전(자유 회전). */
private fun DrawScope.drawStar(b: StarBody, cx: Float, cy: Float, scale: Float = 1f, rotationDeg: Float = 0f) {
    val r = b.r * scale
    val center = Offset(cx, cy)
    // 후광 (잡았을 때 더 진하게) — 회전 무관(원형)
    val glowAlpha = (0.42f * scale).coerceAtMost(0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(b.color.copy(alpha = glowAlpha), Color.Transparent),
            center = center,
            radius = r * 2.4f
        ),
        radius = r * 2.4f,
        center = center
    )
    // 별 모양 (StarStyle 경로 — 지도 마커와 동일). 중심 기준 회전.
    val sizePx = r * 2f
    val path = android.graphics.Path(StarStyle.starPath(b.type, sizePx)).apply {
        offset(cx - r, cy - r)
    }
    val gradShader = StarStyle.fillShader(b.colorIndex, cx - r, cy - r, r * 2f)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.rotate(rotationDeg, cx, cy)
        nc.drawPath(
            path,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                if (gradShader != null) this.shader = gradShader else this.color = b.color.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(r * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        )
        // 본체 — 수정 결정(크리스탈) 패싯 채움(지도 마커와 동일)
        StarStyle.drawCrystalFill(nc, b.type, b.colorIndex, cx - r, cy - r, sizePx)
        nc.restoreToCount(save)
    }
}
