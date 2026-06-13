package com.chaminwoo.stary.core.designsystem

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 다이어리 별 마커의 종류(모양)×색상 팔레트.
 * 업로드 화면 피커와 지도 마커 렌더가 같은 정의를 공유한다.
 *
 * - 종류(starType 0..4): [starPath] 로 그리는 5가지 별 모양 (PNG 미사용 — 렌더 신뢰성).
 * - 색상(starColor 0..11): 12색 팔레트.
 */
object StarStyle {
    const val TYPE_COUNT = 5
    const val COLOR_COUNT = 12

    /**
     * 12색 팔레트 (ARGB). 어두운 지도 배경 위에서 "빛나는" 인상을 위해
     * 기본 톤에 흰색을 30% 섞어 전체적으로 밝게 사용한다. (피커·마커 공통)
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
    ).map { lerp(it, Color.White, 0.30f) }

    fun colorOf(index: Int): Color = palette[index.coerceIn(0, COLOR_COUNT - 1)]

    /**
     * 별 모양 Path 생성. (sizePx × sizePx 정사각 중앙 기준)
     *
     * type 0: 4꼭지 스파클(십자) / 1: 5꼭지 클래식 별 / 2: 6꼭지 /
     * type 3: 8꼭지 가는 스파클 / 4: 4꼭지 대각(다이아) 스파클
     *
     * 스파클 계열(0,3,4)은 변을 안쪽으로 휘는 곡선(quad)으로 그려
     * 꼭지가 바늘처럼 날카롭게 반짝이는 인상을 준다. (직선 폴리곤은 꼭지가 뭉뚝해 보임)
     */
    fun starPath(type: Int, sizePx: Float): Path {
        data class Spec(val spikes: Int, val innerRatio: Float, val rotateDeg: Double, val curved: Boolean)
        val spec = when (type.coerceIn(0, TYPE_COUNT - 1)) {
            0 -> Spec(4, 0.10f, 0.0, curved = true)
            1 -> Spec(5, 0.40f, -90.0, curved = false)
            2 -> Spec(6, 0.26f, -90.0, curved = false)
            3 -> Spec(8, 0.13f, 0.0, curved = true)
            else -> Spec(4, 0.10f, 45.0, curved = true)
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
            // 꼭지(outer)들 사이를 안쪽 점(inner)을 제어점으로 한 곡선으로 연결 → 오목한 변 = 날카로운 꼭지
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
}
