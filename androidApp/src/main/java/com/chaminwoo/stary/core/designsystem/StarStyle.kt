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
 * - 색상(starColor 0..20): 0~15 단색 / 16~20 2색 그라데이션(고난도 업적 보상).
 */
object StarStyle {
    const val TYPE_COUNT = 9
    const val COLOR_COUNT = 21

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
        Color(0xFF101010) to Color(0xFFFFFFFF), // 20 흑백 (검정→하양, 밤→여명)
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
     * 5: 꽃 / 6: 다이아몬드 / 7: 초승달 / 8: 행성  (5~8은 별 아닌 창의적 형태 — 수집 보상)
     */
    fun starPath(type: Int, sizePx: Float): Path {
        when (type.coerceIn(0, TYPE_COUNT - 1)) {
            5 -> return flowerPath(sizePx)
            6 -> return gemPath(sizePx)
            7 -> return crescentPath(sizePx)
            8 -> return planetPath(sizePx)
        }

        // 내부 반지름 비율을 낮춰 꼭지를 더 뾰족하고 깔끔하게(2026-07 모양 다듬기, iOS StarShape 와 동기).
        data class Spec(val spikes: Int, val innerRatio: Float, val rotateDeg: Double, val curved: Boolean)
        val spec = when (type.coerceIn(0, TYPE_COUNT - 1)) {
            0 -> Spec(4, 0.085f, 0.0, curved = true)
            1 -> Spec(5, 0.34f, -90.0, curved = false)
            2 -> Spec(6, 0.21f, -90.0, curved = false)
            3 -> Spec(8, 0.10f, 0.0, curved = true)
            else -> Spec(4, 0.085f, 45.0, curved = true) // 4
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

    /** 꽃 — 둥근 꽃잎 6장(합집합) + 가운데 빈 원. 전체를 20% 작게. */
    private fun flowerPath(s: Float): Path {
        val cx = s / 2f; val cy = s / 2f
        val scale = 0.8f               // 기존 대비 20% 축소
        val ring = s * 0.255f * scale  // 꽃잎 중심까지 거리
        val petal = s * 0.225f * scale // 꽃잎 반지름
        val body = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 - 90.0)
            body.addCircle(cx + (ring * cos(a)).toFloat(), cy + (ring * sin(a)).toFloat(), petal, Path.Direction.CW)
        }
        // 가운데 원을 빼서 빈 공간(구멍)으로 만든다.
        val hole = Path().apply { addCircle(cx, cy, s * 0.135f, Path.Direction.CW) }
        return Path().apply { op(body, hole, Path.Op.DIFFERENCE) }
    }

    /**
     * 보석(다이아몬드) — 컷 다이아몬드 실루엣(테이블·거들·컬릿) 위에
     * 패싯(컷) 라인을 빈 공간으로 뚫어 면이 갈라져 보이게 한다.
     */
    private fun gemPath(s: Float): Path {
        fun p(fx: Float, fy: Float) = (fx * s) to (fy * s)

        // 외곽: 테이블(윗면) + 좌우 어깨 → 거들(최대폭) → 컬릿(아래 한 점)
        val outline = Path().apply {
            val pts = listOf(
                0.31f to 0.11f, // 테이블 좌
                0.69f to 0.11f, // 테이블 우
                0.84f to 0.14f, // 오른 어깨
                0.97f to 0.40f, // 오른 거들(최대폭)
                0.50f to 0.95f, // 컬릿
                0.03f to 0.40f, // 왼 거들(최대폭)
                0.16f to 0.14f  // 왼 어깨
            )
            pts.forEachIndexed { i, (fx, fy) ->
                val (x, y) = p(fx, fy)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // 패싯(컷) 라인 — diamond.jpg 문양. 이 선들을 빈 공간으로 뚫는다.
        // 핵심: 크라운 중앙이 X자(테이블 양끝→중앙점 A→거들)로 갈라진다.
        val lines = Path().apply {
            fun seg(a: Pair<Float, Float>, b: Pair<Float, Float>) {
                val (ax, ay) = p(a.first, a.second); val (bx, by) = p(b.first, b.second)
                moveTo(ax, ay); lineTo(bx, by)
            }
            val a = 0.50f to 0.22f      // 크라운 중앙 수렴점
            // 거들(가로) — 크라운/파빌리온 경계
            seg(0.03f to 0.40f, 0.97f to 0.40f)
            // 크라운: 테이블 윗변
            seg(0.31f to 0.11f, 0.69f to 0.11f)
            // 크라운: 테이블 양끝 → 중앙점 A (역삼각 윗면)
            seg(0.31f to 0.11f, a)
            seg(0.69f to 0.11f, a)
            // 크라운: 중앙점 A → 거들 중앙 두 점 (정삼각 — 중앙 패싯)
            seg(a, 0.40f to 0.40f)
            seg(a, 0.60f to 0.40f)
            // 크라운: 테이블 모서리 → 중간 거들점
            seg(0.31f to 0.11f, 0.29f to 0.40f)
            seg(0.69f to 0.11f, 0.71f to 0.40f)
            // 크라운: 어깨 → 중간 거들점
            seg(0.16f to 0.14f, 0.29f to 0.40f)
            seg(0.84f to 0.14f, 0.71f to 0.40f)
            // 파빌리온: 중간 거들점 → 컬릿(중앙 큰 삼각 + 양옆 면)
            seg(0.29f to 0.40f, 0.50f to 0.95f)
            seg(0.71f to 0.40f, 0.50f to 0.95f)
        }
        // 선을 두께 있는 채움 경로로 변환 후 외곽에서 빼서 컷 라인을 만든다.
        val lineFill = Path()
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = s * 0.03f
            strokeJoin = android.graphics.Paint.Join.MITER
        }.getFillPath(lines, lineFill)

        return Path().apply { op(outline, lineFill, Path.Op.DIFFERENCE) }
    }

    /** 행성 — 본체 원 + 기울어진 고리(타원 밴드)의 합집합 실루엣. (planet.jpeg 참고) */
    private fun planetPath(s: Float): Path {
        val cx = s / 2f; val cy = s * 0.52f
        val body = Path().apply { addCircle(cx, cy, s * 0.26f, Path.Direction.CW) }
        // 고리 = 바깥 타원 − 안쪽 타원 = 밴드 → 살짝 기울임("/").
        val outer = Path().apply {
            addOval(android.graphics.RectF(cx - s * 0.46f, cy - s * 0.15f, cx + s * 0.46f, cy + s * 0.15f), Path.Direction.CW)
        }
        val inner = Path().apply {
            addOval(android.graphics.RectF(cx - s * 0.37f, cy - s * 0.105f, cx + s * 0.37f, cy + s * 0.105f), Path.Direction.CW)
        }
        val band = Path().apply {
            op(outer, inner, Path.Op.DIFFERENCE)
            transform(android.graphics.Matrix().apply { postRotate(-20f, cx, cy) })
        }
        return Path().apply { op(body, band, Path.Op.UNION) }
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
