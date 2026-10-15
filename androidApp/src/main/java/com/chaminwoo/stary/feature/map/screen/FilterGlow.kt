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
 * 빛의 고리(포털) 한 프레임 — [DiaryMap] 의 등장 재생 루프가 매 프레임 채운다(null = 연출 없음).
 * 링은 **지도 위 거품**(내 위치 중심, 반지름 m, 출렁임)을 매 프레임 화면에 투영한 것이라, 연출 중 지도를 움직여도 지도에 붙어 있다.
 *
 * 필터를 연속으로 바꾸면 거품이 **여러 개 함께** 퍼진다(2026-10-15). 화면의 각 자리는 **그 자리를 덮은 가장 새 거품의 색**이고,
 * 어느 거품에도 아직 안 덮인 자리는 [base] 색이다(필터 1 → 2 : 바깥 = 필터 1 전의 색, 두 거품 사이 = 필터 1 색, 가장 안쪽 = 필터 2 색).
 * 새 거품에 덮인 이전 거품의 테두리는 그리지 않는다(따라잡히면 덮인 쪽부터 삼켜진다).
 */
internal class FilterWaveFrame(
    /** 모든 거품 밖(가장 앞선 거품도 아직 안 지나간 자리)의 필터 빛 색. null = 빛 없음. */
    val base: Color?,
    /** 함께 퍼지고 있는 거품들 — 오래된(앞서가는) 것 → 새 것. 비어 있으면 아직 거품 없음(이전 별을 걷어내는 중). */
    val bubbles: List<WaveBubble>,
    val flashes: List<WaveFlash>,
)

/** 퍼지고 있는 거품 하나(화면 좌표). */
internal class WaveBubble(
    /** 화면 좌표로 투영한 거품 테두리(닫힌 다각형). */
    val ring: Path,
    /** 테두리 불투명도(0..1) — 지금 화면을 다 지나갈 때까지 1, 그 뒤 [REVEAL_RING_FADE_MS] 동안 옅어진다. */
    val ringAlpha: Float,
    val ringColor: Color,
    /** 거품 안(지나간 자리)의 필터 빛 색 = 이 거품을 낸 필터의 색. null = 빛 없음(필터를 모두 끈 경우). */
    val tint: Color?,
    /** 거품 안의 포털 성운 세기(0..1) — 퍼지는 동안 1, 화면을 다 지나간 뒤 [REVEAL_PORTAL_DECAY_MS] 동안 0 으로. */
    val portal: Float,
    /** 성운 속 잔별(화면 좌표). */
    val sparks: List<PortalSpark>,
    /** 시작점의 검은 구체(null = 이미 사라짐). */
    val orb: PortalOrb?,
)

/** 링이 닿은 별 하나의 발광 — 화면 좌표(px)·반경·세기(0..1)·별 색. */
internal class WaveFlash(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val intensity: Float,
    val color: Color,
)

/** 포털 성운 속 잔별 하나(화면 좌표 px). */
internal class PortalSpark(val x: Float, val y: Float, val radiusPx: Float, val alpha: Float)

/** 시작점의 검은 구체(화면 좌표 px). */
internal class PortalOrb(val x: Float, val y: Float, val radiusPx: Float, val alpha: Float, val rimColor: Color)

/**
 * 포털 성운의 고정 배치(연출마다 무작위) — 지도 위 위치를 "내 위치에서 방위·거리(× sweep)"로 들고, 매 프레임 화면에 투영한다.
 * (거품 안의 큰 동그라미들 — 구름 얼룩·어두운 반점·청록 빛 덩이 — 은 2026-10-15 삭제. 잔별만 남았다.)
 * iOS `PortalLayout`(MapStarReveal.swift)과 같은 개수·범위.
 */
internal class PortalLayout(seed: Int) {
    /** 잔별: 방위(rad), 거리(× sweep), 크기(dp), 반짝임 위상·속도. */
    class Spark(val bearing: Double, val dist: Double, val sizeDp: Float, val phase: Double, val speed: Double)

    val sparks: List<Spark>

    init {
        val r = kotlin.random.Random(seed)
        sparks = List(PORTAL_SPARKS) {
            Spark(
                bearing = r.nextDouble() * 2 * Math.PI,
                dist = kotlin.math.sqrt(r.nextDouble()) * 1.35,
                sizeDp = 0.8f + r.nextFloat() * 1.4f,
                phase = r.nextDouble() * 2 * Math.PI,
                speed = 2.0 + r.nextDouble() * 3.0,
            )
        }
    }
}

/** 포털 성운 속 잔별 개수. */
private const val PORTAL_SPARKS = 56

/** 포털 성운의 바탕색 = 필터 색을 아주 깊은 남보라 쪽으로 섞은 것(레퍼런스의 "짙은 보라 우주"). */
internal fun portalDeep(tint: Color): Color = lerp(tint, Color(0xFF120A2A), 0.58f)

/**
 * 지도 위 **출렁이는 거품 테두리**(중심 [center], 기본 반지름 [radiusM] m, 각도마다 [wobble] 로 출렁임)를
 * 지금 카메라로 화면에 투영한 닫힌 경로. 대권(지구 곡면) 기준이라 아주 크게 퍼져도 모양이 깨지지 않고, **크기 제한이 없다**
 * (예전엔 2,000km 에서 잘라 지도를 넓게 보면 링이 그 크기에서 멈춰 보였다 — 2026-10-14).
 */
internal fun ringScreenPath(map: MapLibreMap, center: LatLng, radiusM: Double, wobble: RingWobble, tSec: Double): Path {
    val r = radiusM.coerceAtLeast(0.5)
    val path = Path()
    var prevLng = Double.NaN
    for (i in 0 until REVEAL_RING_SEGMENTS) {
        val a = 2.0 * Math.PI * i / REVEAL_RING_SEGMENTS
        val ra = r * (1 + REVEAL_RING_WOBBLE * wobble.at(a, tSec))
        val ll = geoDestination(center.latitude, center.longitude, Math.PI / 2 - a, ra)
        // 경도가 ±180 을 넘나들 때 선이 화면을 가로지르지 않게 이어 붙인다
        var lng = ll[1]
        if (!prevLng.isNaN()) {
            while (lng - prevLng > 180) lng -= 360
            while (lng - prevLng < -180) lng += 360
        }
        prevLng = lng
        val p = map.projection.toScreenLocation(MlLatLng(ll[0].coerceIn(-85.0, 85.0), lng))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

/** 지도 위 지점(내 위치에서 방위 [bearing]·거리 [distM])을 지금 카메라 화면 좌표로. */
internal fun geoToScreen(map: MapLibreMap, center: LatLng, bearing: Double, distM: Double): PointF {
    val ll = geoDestination(center.latitude, center.longitude, bearing, distM)
    return map.projection.toScreenLocation(MlLatLng(ll[0].coerceIn(-85.0, 85.0), ll[1]))
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

/** 빛의 고리 테두리 + 시작점 검은 구체 + 별 발광 오버레이(지도 위·버튼 아래, 터치 통과). */
@Composable
internal fun FilterWaveOverlay(frame: State<FilterWaveFrame?>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val f = frame.value ?: return@Canvas
        val bubbles = f.bubbles
        bubbles.forEachIndexed { i, b ->
            val a = b.ringAlpha
            if (a <= 0.01f) return@forEachIndexed
            val ring = b.ring
            // 더 새 거품에 덮인 부분은 그리지 않는다 — 따라잡히면 덮인 쪽부터 삼켜진다.
            clipOutsideBubbles(bubbles, i + 1) {
                val rim = lerp(b.ringColor, Color.White, 0.5f)
                // 테두리 안쪽으로만 번지는 빛 — 굵은 선을 거품 안쪽만 남긴다(바깥은 깨끗하게, 거품이 부푸는 느낌)
                clipPath(ring) {
                    drawPath(ring, rim.copy(alpha = 0.10f * a), style = Stroke(REVEAL_RIM_GLOW_DP.dp.toPx() * 2f, join = StrokeJoin.Round))
                    drawPath(ring, rim.copy(alpha = 0.22f * a), style = Stroke(REVEAL_RIM_GLOW_DP.dp.toPx() * 0.7f, join = StrokeJoin.Round))
                }
                // 얇은 경계선 하나
                drawPath(
                    ring, lerp(b.ringColor, Color.White, 0.6f).copy(alpha = 0.92f * a),
                    style = Stroke(REVEAL_RING_STROKE_DP.dp.toPx(), join = StrokeJoin.Round),
                )
            }
        }
        // ⚠️ 방사형 그라데이션은 반지름이 0 이면 IllegalArgumentException(RadialGradient) 으로 앱이 죽는다 —
        //    구체는 첫 프레임(0ms)에 반지름 0 에서 자라므로 1px 미만은 그리지 않는다(2026-10-14 필터 켜자마자 튕기던 원인).
        bubbles.forEach { b -> b.orb?.takeIf { it.radiusPx >= 1f }?.let { o ->
            val c = Offset(o.x, o.y)
            // 검은 구체 — 바깥 보랏빛 테 + 검은 속 + 작은 반짝임(레퍼런스의 "마지막 자리에 생기는 검은 구슬")
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF05030C).copy(alpha = 0.95f * o.alpha),
                    0.62f to Color(0xFF0B0618).copy(alpha = 0.92f * o.alpha),
                    0.82f to o.rimColor.copy(alpha = 0.55f * o.alpha),
                    1f to o.rimColor.copy(alpha = 0f),
                    center = c, radius = o.radiusPx,
                ),
                radius = o.radiusPx, center = c,
            )
            drawCircle(Color.White.copy(alpha = 0.85f * o.alpha), o.radiusPx * 0.09f, Offset(o.x + o.radiusPx * 0.18f, o.y - o.radiusPx * 0.12f))
        } }
        f.flashes.forEach { fl ->
            if (fl.radiusPx < 1f) return@forEach // 방사형 그라데이션 반지름 0 방지(위 구체와 같은 이유)
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
 * - [wave] : 빛의 고리가 퍼지는 동안엔 **각 자리를 덮은 가장 새 거품의 색**, 어느 거품에도 안 덮인 자리는 연속 전환 전의 색으로 그린다.
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
        if (f == null) {
            tint?.let { drawFilterGlow(it, ph) }
            return@Canvas
        }
        // 화면의 각 자리 = 그 자리를 덮은 가장 새 거품의 색, 어느 거품에도 안 덮였으면 base(겹쳐 칠하지 않게 영역을 나눠 그린다).
        val bubbles = f.bubbles
        f.base?.let { c -> clipOutsideBubbles(bubbles, 0) { drawFilterGlow(c, ph) } }
        bubbles.forEachIndexed { i, b ->
            val c = b.tint ?: return@forEachIndexed
            clipPath(b.ring) {
                clipOutsideBubbles(bubbles, i + 1) {
                    drawFilterGlow(c, ph)
                    // 거품 안 = 포털 성운(퍼지는 동안 짙게, 화면을 다 지나간 뒤 은은한 필터 빛으로 가라앉는다)
                    if (b.portal > 0.01f) drawPortal(c, b)
                }
            }
        }
    }
}

/** [from] 번째부터의 거품들(더 새 거품들) **바깥**만 남기고 [block] 을 그린다. */
private fun DrawScope.clipOutsideBubbles(bubbles: List<WaveBubble>, from: Int, block: DrawScope.() -> Unit) {
    if (from >= bubbles.size) block()
    else clipPath(bubbles[from].ring, ClipOp.Difference) { clipOutsideBubbles(bubbles, from + 1, block) }
}

/** 필터 빛 한 장 — 아래에서 차오르는 빛 + 위쪽 맞빛 + 화면 둘레를 도는 빛 덩이. */
private fun DrawScope.drawFilterGlow(color: Color, ph: Float) {
    if (size.width < 1f || size.height < 1f) return // 레이아웃 전(크기 0) — 아래 방사형 그라데이션 반지름 0 방지
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

/** 포털 성운 — 짙은 남보라 바탕 + 잔별. 세기는 [WaveBubble.portal]. (구름 얼룩 동그라미들은 2026-10-15 삭제) */
private fun DrawScope.drawPortal(tint: Color, f: WaveBubble) {
    val k = f.portal
    drawRect(portalDeep(tint).copy(alpha = 0.30f * k))
    val star = lerp(tint, Color.White, 0.75f)
    f.sparks.forEach { s -> drawCircle(star.copy(alpha = s.alpha * k), s.radiusPx, Offset(s.x, s.y)) }
}
