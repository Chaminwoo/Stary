package com.chaminwoo.stary.core.designsystem

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.floor
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

        // 모든 별을 곡선(quad) 스파이크로 통일 — 직선 별(구 5각/6각)이 투박해 보여 재구성
        // (2026-07 모양 다듬기 2차, iOS StarShape 와 동기).
        // innerRatio 는 quad 제어점 거리: 곡선 별의 실제 허리 두께 ≈ 0.5·cos(180°/spikes) + 0.5·innerRatio.
        data class Spec(val spikes: Int, val innerRatio: Float, val rotateDeg: Double, val curved: Boolean)
        val spec = when (type.coerceIn(0, TYPE_COUNT - 1)) {
            0 -> Spec(4, 0.085f, 0.0, curved = true)
            1 -> Spec(5, 0.14f, -90.0, curved = true)
            2 -> Spec(6, 0.11f, -90.0, curved = true)
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

    // ── 수정 결정(크리스탈) 채움 ────────────────────────────────

    /**
     * 별 내부를 수정(크리스탈) 결정 단면처럼 채운다 — 실루엣([starPath])은 그대로 두고
     * clip 안에서 중심 방사형 패싯(조각)을 그려, 조각마다 명도·색조가 미세하게 다른
     * 컷 보석 느낌을 낸다. 지도 마커/아이콘/공유 카드 등 모든 별 렌더가 이 채움을 공유한다.
     *
     * @param alpha 전체 불투명도(0..255) — 파장 버스트처럼 페이드가 필요한 곳에서 사용.
     */
    fun drawCrystalFill(
        canvas: Canvas,
        type: Int,
        colorIndex: Int,
        left: Float,
        top: Float,
        sizePx: Float,
        alpha: Int = 255,
    ) = drawCrystalFill(canvas, type, colorsOf(colorIndex).map { it.toArgb() }, left, top, sizePx, alpha)

    /** [drawCrystalFill] 의 색 직접 지정 버전 — 팔레트 밖 색으로 그리는 곳용(1색 또는 그라데이션 2색). */
    fun drawCrystalFill(
        canvas: Canvas,
        type: Int,
        colors: List<Int>,
        left: Float,
        top: Float,
        sizePx: Float,
        alpha: Int = 255,
    ) {
        if (sizePx <= 0f || colors.isEmpty() || alpha <= 0) return
        val t = type.coerceIn(0, TYPE_COUNT - 1)
        val silhouette = Path(starPath(t, sizePx)).apply { offset(left, top) }
        val cx = left + sizePx / 2f
        val cy = top + sizePx / 2f
        val (wedges, startDeg) = facetLayout(t)

        val save = canvas.save()
        canvas.clipPath(silhouette)

        // 파편형 패싯 — 경계 각도를 지터하고 안/밖 2겹 링으로 갈라, 규칙적인 부채꼴이 아니라
        // 잘게 깨진 수정 파편처럼 보이게 한다. 모든 좌표/색은 결정론적 해시(같은 별 = 같은 무늬).
        val step = 360.0 / wedges
        val outR = sizePx // 실루엣보다 넉넉히 — clip 이 잘라낸다
        // 경계 각도(지터) + 그 경계에서의 안쪽 링 반지름(불규칙한 코어 다각형)
        val angles = DoubleArray(wedges + 1)
        val midR = FloatArray(wedges + 1)
        for (k in 0 until wedges) {
            val jitter = (facetHash(k * 17 + t * 57 + 3) - 0.5) * step * 0.55
            angles[k] = Math.toRadians(startDeg + k * step + jitter)
            midR[k] = sizePx * (0.13f + 0.15f * facetHash(k * 23 + t * 91 + 11))
        }
        angles[wedges] = angles[0] + 2 * Math.PI
        midR[wedges] = midR[0]

        fun px(a: Double, r: Float) = cx + (cos(a) * r).toFloat()
        fun py(a: Double, r: Float) = cy + (sin(a) * r).toFloat()

        // 조각 색 — 그라데이션은 좌상→우하 방향 투영으로 두 색을 섞고, 조각마다 색상(±24°)·
        // 채도·명도를 크게 흔든다. 드물게 빛을 정면으로 받은 "글린트"(백색 근접) 조각.
        val hsl = FloatArray(3)
        fun facetColor(seed: Int, midAng: Double, ring: Int): Int {
            val base = if (colors.size >= 2) {
                val f = (((cos(midAng) + sin(midAng)) / 2.0 + 1.0) / 2.0).toFloat()
                ColorUtils.blendARGB(colors[0], colors[1], f)
            } else colors[0]
            val h1 = facetHash(seed * 7 + t * 131 + 1)
            val h2 = facetHash(seed * 13 + t * 29 + 7)
            val h3 = facetHash(seed * 31 + t * 173 + 19)
            if (h3 > 0.94f) return ColorUtils.blendARGB(base, android.graphics.Color.WHITE, 0.55f) // 글린트
            ColorUtils.colorToHSL(base, hsl)
            hsl[0] = (hsl[0] + (h1 - 0.5f) * 48f + 360f) % 360f
            hsl[1] = (hsl[1] * (0.80f + 0.40f * h2)).coerceIn(0f, 1f)
            val lightBias = (if (seed % 2 == 0) 0.09f else -0.08f) + (if (ring == 0) 0.045f else -0.02f)
            hsl[2] = (hsl[2] + lightBias + (h1 - 0.5f) * 0.16f).coerceIn(0.05f, 0.97f)
            return ColorUtils.HSLToColor(hsl)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val piece = Path()
        for (k in 0 until wedges) {
            val a0 = angles[k]
            val a1 = angles[k + 1]
            val mid = (a0 + a1) / 2
            // 안쪽 조각(코어 파편) — 중심에서 불규칙 링까지
            paint.color = facetColor(k, mid, ring = 0)
            paint.alpha = alpha
            piece.reset()
            piece.moveTo(cx, cy)
            piece.lineTo(px(a0, midR[k]), py(a0, midR[k]))
            piece.lineTo(px(a1, midR[k + 1]), py(a1, midR[k + 1]))
            piece.close()
            canvas.drawPath(piece, paint)
            // 바깥 조각(스파이크 파편) — 링에서 실루엣 밖까지(clip 이 마무리)
            paint.color = facetColor(k + wedges * 3, mid, ring = 1)
            paint.alpha = alpha
            piece.reset()
            piece.moveTo(px(a0, midR[k]), py(a0, midR[k]))
            piece.lineTo(px(a0, outR), py(a0, outR))
            piece.lineTo(px(a1, outR), py(a1, outR))
            piece.lineTo(px(a1, midR[k + 1]), py(a1, midR[k + 1]))
            piece.close()
            canvas.drawPath(piece, paint)
        }

        // 패싯 능선 — 모든 조각 경계를 따라 옅은 흰 컷 라인(방사선 + 불규칙 코어 링)
        val ridge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.012f).coerceAtLeast(0.7f)
            color = android.graphics.Color.WHITE
            this.alpha = 46 * alpha / 255
        }
        val ridgePath = Path()
        for (k in 0 until wedges) {
            ridgePath.moveTo(cx, cy)
            ridgePath.lineTo(px(angles[k], midR[k]), py(angles[k], midR[k]))
            ridgePath.lineTo(px(angles[k], outR), py(angles[k], outR))
            ridgePath.moveTo(px(angles[k], midR[k]), py(angles[k], midR[k]))
            ridgePath.lineTo(px(angles[k + 1], midR[k + 1]), py(angles[k + 1], midR[k + 1]))
        }
        canvas.drawPath(ridgePath, ridge)

        // 중심 광채 — 수정 심부가 빛을 모은 듯한 은은한 흰 코어
        canvas.drawCircle(cx, cy, sizePx * 0.26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, sizePx * 0.26f,
                android.graphics.Color.argb(95 * alpha / 255, 255, 255, 255),
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        })

        canvas.restoreToCount(save)
    }

    /**
     * 패싯 경계(방사선) 수/시작각(도) — 별(0~4)은 꼭지·골 격자의 2배 밀도(반스파이크당 2조각),
     * 여기에 안/밖 2겹 링 분할이 더해져 실제 조각 수는 이 값의 2배가 된다.
     */
    private fun facetLayout(type: Int): Pair<Int, Double> = when (type) {
        0 -> 16 to 0.0
        1 -> 20 to -90.0
        2 -> 24 to -90.0
        3 -> 24 to 0.0
        4 -> 16 to 45.0
        5 -> 18 to -90.0
        6 -> 16 to -90.0
        7 -> 16 to -70.0
        else -> 18 to -90.0
    }

    /** 0..1 결정론적 해시 — 같은 조각은 항상 같은 색(렌더마다 흔들리지 않게). */
    private fun facetHash(seed: Int): Float {
        val x = sin(seed * 12.9898) * 43758.5453
        return (x - floor(x)).toFloat()
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
