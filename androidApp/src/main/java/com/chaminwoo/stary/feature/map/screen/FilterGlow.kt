package com.chaminwoo.stary.feature.map.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.util.MapUiState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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

/** 빛의 고리 한 프레임 — [DiaryMap] 의 순차 등장 루프가 매 프레임 채운다(null = 연출 없음). */
internal class FilterWaveFrame(
    val radiusPx: Float,
    /** 링 불투명도(0..1) — 퍼질수록 옅어진다. */
    val ringAlpha: Float,
    val color: Color,
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
 * 빛의 고리 + 별 발광 오버레이(지도 위·버튼 아래, 터치 통과).
 * 링은 화면(=카메라) 중심에서 퍼지고, 링이 닿은 별 자리에 잠깐 밝은 빛이 번진다.
 */
@Composable
internal fun FilterWaveOverlay(frame: State<FilterWaveFrame?>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val f = frame.value ?: return@Canvas
        val r = f.radiusPx
        if (f.ringAlpha > 0.01f && r > 1f) {
            val stroke = REVEAL_RING_STROKE_DP.dp.toPx()
            val trail = REVEAL_RING_TRAIL_DP.dp.toPx()
            // 링 안쪽으로 끌리는 빛 띠 — 고리가 지나간 자리가 잠깐 물든다
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    ((r - trail) / r).coerceIn(0f, 1f) to Color.Transparent,
                    1f to f.color.copy(alpha = 0.20f * f.ringAlpha),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            // 번짐 + 밝은 선
            drawCircle(f.color.copy(alpha = 0.26f * f.ringAlpha), r, center, style = Stroke(stroke * 4.5f))
            drawCircle(
                lerp(f.color, Color.White, 0.35f).copy(alpha = 0.95f * f.ringAlpha),
                r, center, style = Stroke(stroke),
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
 * 화면 아래에서 차오르되, 색은 지금 필터의 색([tint])이고 옅은 빛 덩이가 화면 둘레를 천천히 돈다.
 * [tint] = null(필터 없음)이면 서서히 꺼진다. 장식 전용이라 터치를 받지 않는다.
 *
 * iOS `FilterGlowOverlay`(FilterGlow.swift)와 같은 수치.
 */
@Composable
internal fun FilterGlowOverlay(tint: Color?, modifier: Modifier = Modifier) {
    // 꺼지는 동안에도 마지막 색으로 옅어지도록 기억해 둔다.
    val lastTint = remember { arrayOf(Color.Transparent) }
    if (tint != null) lastTint[0] = tint
    val color by animateColorAsState(tint ?: lastTint[0], tween(700), label = "filterGlowColor")
    val strength by animateFloatAsState(if (tint != null) 1f else 0f, tween(800), label = "filterGlowStrength")

    // 빛이 도는 위상(라디안) — 필터가 켜져 있고 지도가 보일 때만 흐른다(가려진 동안 프레임 낭비 방지).
    val phase = remember { mutableFloatStateOf(0f) }
    val running = tint != null && MapUiState.mapVisible
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
        val s = strength
        if (s <= 0.01f) return@Canvas
        val ph = phase.floatValue
        // 숨 쉬듯 아주 조금 밝아졌다 어두워진다
        val breath = 0.88f + 0.12f * sin(ph * 2f)
        // 1) 아래에서 차오르는 빛(여명 오버레이와 같은 모양)
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.50f to color.copy(alpha = 0.05f * s * breath),
                0.85f to color.copy(alpha = 0.13f * s * breath),
                1.0f to color.copy(alpha = 0.20f * s * breath),
            )
        )
        // 2) 위쪽 가장자리의 옅은 맞빛
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to color.copy(alpha = 0.07f * s),
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
                0f to lerp(color, Color.White, 0.2f).copy(alpha = 0.13f * s),
                1f to color.copy(alpha = 0f),
                center = c,
                radius = r,
            )
        )
    }
}
