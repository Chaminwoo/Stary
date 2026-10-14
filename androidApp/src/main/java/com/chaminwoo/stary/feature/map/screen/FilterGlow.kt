package com.chaminwoo.stary.feature.map.screen

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapUiState
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import org.maplibre.android.geometry.LatLng as MlLatLng

/**
 * 지도 필터 종류 — **필터마다 다른 색**으로 "빛의 고리"(필터 전환 등장 연출)와 지도에 도는 은은한 빛을 물들인다(2026-10-14).
 * iOS `Features/Map/FilterGlow.swift` 의 `MapFilterKind` 와 **같은 색**(값 drift 금지).
 */
enum class MapFilterKind(val color: Color) {
    /** 미해금만 — 아직 열지 못한 별: 보랏빛 */
    LOCKED(Color(0xFFB69CFF)),
    /** 해금만 — 열려 있는 별: 금빛 */
    UNLOCKED(Color(0xFFFFD27A)),
    /** 친구만: 민트 */
    FRIENDS(Color(0xFF7CE8C4)),
    /** 나만보기: 장밋빛 */
    MINE(Color(0xFFFF9EC4)),
    /** 친구 선택: 하늘색 */
    PICK_FRIENDS(Color(0xFF7CC8FF)),
    /** 기간별: 산호색 */
    PERIOD(Color(0xFFFFA873)),
}

/** 필터를 모두 껐을 때 퍼지는 링의 색(색 없는 별빛). */
internal val FILTER_WAVE_NEUTRAL = Color(0xFFDCE6FF)

/**
 * 켜져 있는 필터들 중 "지금 색을 정하는 필터" = **가장 마지막에 켠 것**. 그 필터를 끄면 남은 필터 중 마지막 것.
 * 컴포지션에서 호출해도 되게 같은 입력엔 같은 값을 돌려준다(상태 객체가 아니라 리컴포지션을 일으키지 않는다).
 */
internal class FilterKindTracker {
    private var prev: List<MapFilterKind> = emptyList()
    private var latest: MapFilterKind? = null

    fun update(active: List<MapFilterKind>): MapFilterKind? {
        val added = active.firstOrNull { it !in prev }
        if (added != null) latest = added
        else if (latest !in active) latest = active.lastOrNull()
        prev = active
        return latest
    }
}

/**
 * 빛의 고리 한 프레임 — [DiaryMap] 의 등장 재생 루프가 매 프레임 채운다(null = 연출 없음).
 * 링은 **지도 위 원**(내 위치 중심, 반지름 m)을 매 프레임 화면에 투영한 것이라, 연출 중 지도를 움직여도 지도에 붙어 있다.
 */
internal class FilterWaveFrame(
    /** 화면 좌표로 투영한 링(닫힌 다각형). null = 링이 아직 없음(이전 별을 걷어내는 중). */
    val ring: Path?,
    /** 링 불투명도(0..1) — 마지막 별에 닿을 때까지 1, 그 뒤 [REVEAL_RING_FADE_MS] 동안 옅어진다. */
    val ringAlpha: Float,
    val ringColor: Color,
    /** 링 안(이미 지나간 자리)의 필터 빛 색 = 새 필터 색. null = 빛 없음(필터를 모두 끈 경우). */
    val insideTint: Color?,
    /** 링 밖(아직 안 지나간 자리)의 필터 빛 색 = 이전 필터 색. */
    val outsideTint: Color?,
    val flashes: List<WaveFlash>,
)

/** 링이 닿은 별 하나의 발광 — 화면 좌표(px)·반경·세기(0..1)·별 색. */
internal class WaveFlash(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val intensity: Float,
    val color: Color,
)

/**
 * 지도 위 원(중심 [center], 반지름 [radiusM] m)을 지금 카메라로 화면에 투영한 닫힌 경로.
 * 기울기(틸트)·이동·줌이 그대로 반영된다. 아주 큰 원은 그리기만 2,000km 로 자른다(화면 밖이라 차이 없음).
 */
internal fun ringScreenPath(map: MapLibreMap, center: LatLng, radiusM: Double): Path {
    val r = radiusM.coerceIn(0.5, 2_000_000.0)
    val dLat = r / 111_320.0
    val dLng = r / (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
    val path = Path()
    for (i in 0 until REVEAL_RING_SEGMENTS) {
        val a = 2.0 * Math.PI * i / REVEAL_RING_SEGMENTS
        val lat = (center.latitude + dLat * sin(a)).coerceIn(-85.0, 85.0)
        val lng = center.longitude + dLng * cos(a)
        val p = map.projection.toScreenLocation(MlLatLng(lat, lng))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

/**
 * 링이 화면을 다 쓰는 데 필요한 거리(m) = [center] 에서 화면 네 모서리(지도 좌표)까지 거리 중 최댓값.
 * 이 거리를 [REVEAL_WAVE_MS] 동안 일정한 속도로 가므로, 줌과 무관하게 링이 화면을 쓰는 시간이 같다.
 */
internal fun revealSweepMeters(map: MapLibreMap, width: Float, height: Float, center: LatLng): Double {
    val corners = listOf(PointF(0f, 0f), PointF(width, 0f), PointF(0f, height), PointF(width, height))
    return corners.maxOf { pt ->
        val ll = map.projection.fromScreenLocation(pt)
        LocationHelper.distanceBetween(center.latitude, center.longitude, ll.latitude, ll.longitude).toDouble()
    }.coerceAtLeast(30.0)
}

/** 빛의 고리 + 별 발광 오버레이(지도 위·버튼 아래, 터치 통과). */
@Composable
internal fun FilterWaveOverlay(frame: State<FilterWaveFrame?>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val f = frame.value ?: return@Canvas
        val ring = f.ring
        val a = f.ringAlpha
        if (ring != null && a > 0.01f) {
            val stroke = REVEAL_RING_STROKE_DP.dp.toPx()
            val trail = REVEAL_RING_TRAIL_DP.dp.toPx()
            // 링 안쪽으로 끌리는 빛 띠 — 굵은 선을 링 안쪽만 남기면 지나간 자리 가장자리가 물든다
            clipPath(ring) {
                drawPath(ring, f.ringColor.copy(alpha = 0.16f * a), style = Stroke(trail * 2f, join = StrokeJoin.Round))
            }
            // 번짐 + 밝은 선
            drawPath(ring, f.ringColor.copy(alpha = 0.26f * a), style = Stroke(stroke * 4.5f, join = StrokeJoin.Round))
            drawPath(
                ring, lerp(f.ringColor, Color.White, 0.35f).copy(alpha = 0.95f * a),
                style = Stroke(stroke, join = StrokeJoin.Round),
            )
        }
        f.flashes.forEach { fl ->
            val c = Offset(fl.x, fl.y)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.95f * fl.intensity),
                    0.32f to lerp(fl.color, Color.White, 0.35f).copy(alpha = 0.55f * fl.intensity),
                    1f to fl.color.copy(alpha = 0f),
                    center = c,
                    radius = fl.radiusPx,
                ),
                radius = fl.radiusPx,
                center = c,
            )
        }
    }
}

/** 은은한 빛이 화면을 한 바퀴 도는 시간(초). iOS `FilterGlowFx.orbitSeconds` 와 같은 값. */
private const val GLOW_ORBIT_SEC = 16f

/**
 * 필터가 걸려 있는 동안 지도에 도는 **은은한 그라데이션 빛** — 예전 여명·황혼 오버레이(SkyOverlay, 삭제)처럼
 * 화면 아래에서 차오르되, 색은 필터의 색이고 옅은 빛 덩이가 화면 둘레를 천천히 돈다. 장식 전용(터치 통과).
 *
 * - [tint] : 지금 확정된 필터 빛 색(null = 빛 없음). [DiaryMap] 이 **링이 다 퍼진 뒤에** 새 색으로 바꾼다.
 * - [wave] : 빛의 고리가 퍼지는 동안엔 **링 안쪽(지나간 자리)만 새 색**, 바깥은 이전 색으로 그린다.
 *
 * iOS `FilterGlowOverlay`(FilterGlow.swift)와 같은 수치.
 */
@Composable
internal fun FilterGlowOverlay(tint: Color?, wave: State<FilterWaveFrame?>, modifier: Modifier = Modifier) {
    // 빛이 도는 위상(라디안) — 빛이 있고 지도가 보일 때만 흐른다(가려진 동안 프레임 낭비 방지).
    val phase = remember { mutableFloatStateOf(0f) }
    val running = (tint != null || wave.value != null) && MapUiState.mapVisible
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) {
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                phase.floatValue = (phase.floatValue + dt * (2f * Math.PI.toFloat() / GLOW_ORBIT_SEC)) %
                    (2f * Math.PI.toFloat())
            }
            last = now
        }
    }

    Canvas(modifier = modifier) {
        val ph = phase.floatValue
        val f = wave.value
        val ring = f?.ring
        when {
            f == null -> tint?.let { drawFilterGlow(it, ph) }
            ring == null -> f.outsideTint?.let { drawFilterGlow(it, ph) }
            else -> {
                f.insideTint?.let { c -> clipPath(ring) { drawFilterGlow(c, ph) } }
                f.outsideTint?.let { c -> clipPath(ring, ClipOp.Difference) { drawFilterGlow(c, ph) } }
            }
        }
    }
}

/** 필터 빛 한 장 — 아래에서 차오르는 빛 + 위쪽 맞빛 + 화면 둘레를 도는 빛 덩이. */
private fun DrawScope.drawFilterGlow(color: Color, ph: Float) {
    // 숨 쉬듯 아주 조금 밝아졌다 어두워진다
    val breath = 0.88f + 0.12f * sin(ph * 2f)
    // 1) 아래에서 차오르는 빛(여명 오버레이와 같은 모양)
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.50f to color.copy(alpha = 0.05f * breath),
            0.85f to color.copy(alpha = 0.13f * breath),
            1.0f to color.copy(alpha = 0.20f * breath),
        )
    )
    // 2) 위쪽 가장자리의 옅은 맞빛
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to color.copy(alpha = 0.07f),
            0.22f to Color.Transparent,
            1.0f to Color.Transparent,
        )
    )
    // 3) 화면 둘레를 도는 빛 덩이
    val c = Offset(
        size.width * (0.5f + 0.52f * cos(ph)),
        size.height * (0.5f + 0.50f * sin(ph)),
    )
    val r = max(size.width, size.height) * 0.72f
    drawRect(
        brush = Brush.radialGradient(
            0f to lerp(color, Color.White, 0.2f).copy(alpha = 0.13f),
            1f to color.copy(alpha = 0f),
            center = c,
            radius = r,
        )
    )
}
