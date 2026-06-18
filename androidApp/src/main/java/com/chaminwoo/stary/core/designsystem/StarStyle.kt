package com.chaminwoo.stary.core.designsystem

import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * 다이어리 별 마커의 종류(모양)×색상 팔레트.
 * 업로드 화면 피커와 지도 마커 렌더가 같은 정의를 공유한다.
 *
 * - 종류(starType 0..7): [starPath] — 0~4 별/스파클, 5~7 창의적 형태(꽃·보석·초승달).
 * - 색상(starColor 0..19): 0~15 단색 / 16~19 2색 그라데이션(고난도 업적 보상).
 */
object StarStyle {
    const val TYPE_COUNT = 8
    const val COLOR_COUNT = 20

    /**
     * 16색 단색 팔레트. 어두운 배경 위에서 "빛나는" 인상을 위해 흰색을 30% 섞어 밝게 쓴다.
     */
    val palette: List<Color> = listOf(
        Color(0xFFFFFFFF), // 0 화이트
        Color(0xFFFFD54F), // 1 골드
        Color(0xFFFF8A65), // 2 코랄
        Color(0xFFFF5252), // 3 레드
        Color(0xFFF48FB1), // 4 핑크
        Color(0xFFCE93D8), // 5 라벤더
        Color(0xFF9575CD), // 6 퍼플
        Color(0xFF64B5F6), // 7 블루
        Color(0xFF4DD0E1), // 8 시안
        Color(0xFF6EE7B7), // 9 민트 (앱 포인트)
        Color(0xFFAED581), // 10 라임
        Color(0xFFA1887F), // 11 브라운
        // ── 업적 해금용 보석빛 단색 ──
        Color(0xFFE040FB), // 12 마젠타
        Color(0xFF448AFF), // 13 코발트
        Color(0xFF00E676), // 14 에메랄드
        Color(0xFFFFAB00), // 15 앰버골드
    ).map { lerp(it, Color.White, 0.30f) }

    /** 2색 그라데이션 (인덱스 16부터). 가장 어려운 업적의 보상. */
    val gradients: List<Pair<Color, Color>> = listOf(
        Color(0xFFFF6FD8) to Color(0xFF8E7BFF), // 16 오로라 (핑크→퍼플)
        Color(0xFF43E97B) to Color(0xFF38F9D7), // 17 에메랄드 오로라 (그린→민트)
        Color(0xFFFFD86F) to Color(0xFFFB6F6F), // 18 석양 (골드→코랄)
        Color(0xFF5EE7FF) to Color(0xFF5B7CFF), // 19 빙하 (시안→블루)
    )

    private const val GRAD_START = 16 // 이 인덱스부터 그라데이션

    fun isGradient(index: Int): Boolean = index in GRAD_START until COLOR_COUNT

    /** 그라데이션 2색(없으면 null). */
    fun gradientOf(index: Int): Pair<Color, Color>? = gradients.getOrNull(index - GRAD_START)

    /** 대표(단색) 색 — 그라데이션이면 시작색. 후광/미리보기 등 단색 자리에서 사용. */
    fun colorOf(index: Int): Color {
        val i = index.coerceIn(0, COLOR_COUNT - 1)
        return if (i >= GRAD_START) gradients[i - GRAD_START].first else palette[i]
    }

    /** 색 구성(단색=1개, 그라데이션=2개). 미리보기 Brush 만들 때 사용. */
    fun colorsOf(index: Int): List<Color> =
        gradientOf(index)?.let { listOf(it.first, it.second) } ?: listOf(colorOf(index))

    /** 별 채우기용 Shader — 그라데이션 색일 때만 생성(아니면 null → 단색 Paint.color 사용). */
    fun fillShader(index: Int, left: Float, top: Float, sizePx: Float): Shader? {
        val g = gradientOf(index) ?: return null
        return LinearGradient(
            left, top, left + sizePx, top + sizePx,
            g.first.toArgb(), g.second.toArgb(), Shader.TileMode.CLAMP
        )
    }

    /**
     * 별/형태 Path 생성. (sizePx × sizePx 정사각 중앙 기준)
     *
     * 0: 4꼭지 스파클 / 1: 5꼭지 별 / 2: 6꼭지 별 / 3: 8꼭지 가는 스파클 / 4: 다이아 스파클 /
     * 5: 꽃 / 6: 보석 / 7: 초승달  (5~7은 별 아닌 창의적 형태 — 수집 보상)
     */
    fun starPath(type: Int, sizePx: Float): Path {
        when (type.coerceIn(0, TYPE_COUNT - 1)) {
            5 -> return flowerPath(sizePx)
            6 -> return gemPath(sizePx)
            7 -> return crescentPath(sizePx)
        }

        data class Spec(val spikes: Int, val innerRatio: Float, val rotateDeg: Double, val curved: Boolean)
        val spec = when (type.coerceIn(0, TYPE_COUNT - 1)) {
            0 -> Spec(4, 0.10f, 0.0, curved = true)
            1 -> Spec(5, 0.40f, -90.0, curved = false)
            2 -> Spec(6, 0.26f, -90.0, curved = false)
            3 -> Spec(8, 0.13f, 0.0, curved = true)
            else -> Spec(4, 0.10f, 45.0, curved = true) // 4
        }
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val outer = sizePx / 2f * 0.95f
        val inner = outer * spec.innerRatio
        val path = Path()
        val total = spec.spikes * 2
        fun pointAt(index: Int, len: Float): Pair<Float, Float> {
            val angle = Math.toRadians(index * 360.0 / total + spec.rotateDeg)
            return (cx + cos(angle) * len).toFloat() to (cy + sin(angle) * len).toFloat()
        }
        if (spec.curved) {
            val (sx, sy) = pointAt(0, outer)
            path.moveTo(sx, sy)
            for (i in 0 until spec.spikes) {
                val (ix, iy) = pointAt(i * 2 + 1, inner)
                val (nx, ny) = pointAt(((i + 1) % spec.spikes) * 2, outer)
                path.quadTo(ix, iy, nx, ny)
            }
        } else {
            for (i in 0 until total) {
                val len = if (i % 2 == 0) outer else inner
                val (x, y) = pointAt(i, len)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        return path
    }

    // ── 창의적 형태 ──────────────────────────────────────────────

    /** 꽃 — 둥근 꽃잎 6장 + 중심(겹친 원들의 합집합 실루엣). */
    private fun flowerPath(s: Float): Path {
        val cx = s / 2f; val cy = s / 2f
        val ring = s * 0.255f   // 꽃잎 중심까지 거리
        val petal = s * 0.225f  // 꽃잎 반지름
        val path = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 - 90.0)
            path.addCircle(cx + (ring * cos(a)).toFloat(), cy + (ring * sin(a)).toFloat(), petal, Path.Direction.CW)
        }
        path.addCircle(cx, cy, s * 0.17f, Path.Direction.CW)
        return path
    }

    /** 보석 — 윗면(테이블) + 아래로 뾰족한 컬릿의 컷팅된 보석 실루엣. */
    private fun gemPath(s: Float): Path {
        fun p(fx: Float, fy: Float) = (fx * s) to (fy * s)
        val path = Path()
        val pts = listOf(
            0.30f to 0.20f, 0.70f to 0.20f, // 윗면 좌우
            0.95f to 0.42f,                 // 오른쪽 어깨
            0.50f to 0.95f,                 // 아래 꼭지(컬릿)
            0.05f to 0.42f                  // 왼쪽 어깨
        )
        pts.forEachIndexed { i, (fx, fy) ->
            val (x, y) = p(fx, fy)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** 초승달 — 큰 원에서 살짝 비낀 원을 빼서 만든 크레센트. 살짝 반시계로 눕힌다. */
    private fun crescentPath(s: Float): Path {
        val cx = s / 2f; val cy = s / 2f
        val outer = Path().apply { addCircle(cx - s * 0.05f, cy, s * 0.42f, Path.Direction.CW) }
        val inner = Path().apply { addCircle(cx + s * 0.16f, cy - s * 0.04f, s * 0.37f, Path.Direction.CW) }
        return Path().apply {
            op(outer, inner, Path.Op.DIFFERENCE)
            // 반시계 방향으로 살짝 회전(화면 y축이 아래라 음수 = 반시계) → 누운 초승달
            transform(android.graphics.Matrix().apply { postRotate(-22f, cx, cy) })
        }
    }
}
