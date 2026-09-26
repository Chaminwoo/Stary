package com.chaminwoo.stary.core.designsystem

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
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
 * - 종류(starType 0..20): [starPath] — 0~4 별/스파클, 5~8 창의적 형태(꽃·보석·초승달·행성),
 *   9~20 업적 보상 형태(2026-09-26 — 하트·혜성·눈꽃·벚꽃·태양·불꽃·열쇠·네잎클로버·왕관·나선 은하·고양이·종이비행기).
 * - 색상(starColor 0..20): 0~15 단색 / 16~20 2색 그라데이션(고난도 업적 보상).
 */
object StarStyle {
    const val TYPE_COUNT = 21
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

    /** [starPath] 기준 크기 — 이 크기로 한 번 만든 경로를 캐시해 두고 요청 크기로 배율만 준다. */
    private const val PATH_BASE = 100f
    private val pathCache = arrayOfNulls<Path>(TYPE_COUNT)

    /**
     * 별/형태 Path 생성. (sizePx × sizePx 정사각 중앙 기준)
     *
     * 0: 4꼭지 스파클 / 1: 5꼭지 별 / 2: 6꼭지 별 / 3: 8꼭지 가는 스파클 / 4: 다이아 스파클 /
     * 5: 꽃 / 6: 다이아몬드 / 7: 초승달 / 8: 행성  (5~8은 별 아닌 창의적 형태 — 수집 보상)
     * 9: 하트 / 10: 혜성 / 11: 눈꽃 / 12: 벚꽃 / 13: 태양 / 14: 불꽃 / 15: 열쇠 / 16: 네잎클로버 /
     * 17: 왕관 / 18: 나선 은하 / 19: 고양이 / 20: 종이비행기  (업적 보상, 2026-09-26)
     *
     * 9~20 은 부품 여러 개를 합집합(Path.op)으로 붙여 비싸므로, 모든 타입을 [PATH_BASE] 크기로 한 번만
     * 만들어 캐시하고 매 호출엔 복사 + 배율만 준다(반환값은 항상 새 Path — 호출자가 offset 해도 안전).
     */
    fun starPath(type: Int, sizePx: Float): Path {
        val t = type.coerceIn(0, TYPE_COUNT - 1)
        val base = pathCache[t] ?: synchronized(pathCache) {
            pathCache[t] ?: buildStarPath(t, PATH_BASE).also { pathCache[t] = it }
        }
        return Path(base).apply {
            if (sizePx != PATH_BASE) transform(Matrix().apply { setScale(sizePx / PATH_BASE, sizePx / PATH_BASE) })
        }
    }

    private fun buildStarPath(type: Int, sizePx: Float): Path {
        when (type) {
            5 -> return flowerPath(sizePx)
            6 -> return gemPath(sizePx)
            7 -> return crescentPath(sizePx)
            8 -> return planetPath(sizePx)
            9 -> return heartPath(sizePx)
            10 -> return cometPath(sizePx)
            11 -> return snowflakePath(sizePx)
            12 -> return sakuraPath(sizePx)
            13 -> return sunPath(sizePx)
            14 -> return flamePath(sizePx)
            15 -> return keyPath(sizePx)
            16 -> return cloverPath(sizePx)
            17 -> return crownPath(sizePx)
            18 -> return galaxyPath(sizePx)
            19 -> return catPath(sizePx)
            20 -> return paperPlanePath(sizePx)
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
        drawCrystalFacets(canvas, silhouette, t, colors, left, top, sizePx, alpha)
    }

    /**
     * [drawCrystalFill] 의 임의 실루엣 버전 — 별이 아닌 모양(프로필 부유 아이콘 등)도
     * 같은 파편 메시·돔 셰이딩으로 채울 수 있게 분리한 몸체.
     *
     * @param silhouette clip 할 실루엣. **null 이면 clip 없이 정사각 영역 전체**에 파편을 그린다 —
     *   호출부가 결과를 아이콘 알파 등으로 마스킹(SRC_IN)하는 용도.
     * @param seed 파편 무늬 시드(같은 시드 = 항상 같은 무늬). 별은 type 을 그대로 쓴다.
     */
    fun drawCrystalFacets(
        canvas: Canvas,
        silhouette: Path?,
        seed: Int,
        colors: List<Int>,
        left: Float,
        top: Float,
        sizePx: Float,
        alpha: Int = 255,
    ) {
        if (sizePx <= 0f || colors.isEmpty() || alpha <= 0) return
        val t = seed
        val cx = left + sizePx / 2f
        val cy = top + sizePx / 2f
        val n = facetDensity(t)

        val save = canvas.save()
        if (silhouette != null) canvas.clipPath(silhouette)
        else canvas.clipRect(left, top, left + sizePx, top + sizePx)

        // ── 불규칙 파편 메시 — 방사형 "패턴"이 생기지 않도록:
        //  · 중심은 한 점이 아니라 불규칙 코어 다각형(스포크/부챗살 소멸)
        //  · 링 3겹의 꼭짓점 각도를 서로 어긋나게(중간 링은 반 스텝 시프트) + 강한 지터
        //    → 경계선이 한 줄로 이어지지 않아 그냥 "깨진 조각"으로 읽힌다
        //  · 명도는 중심(밝음)→가장자리(어두움) 구배 = 가운데가 볼록한 돔 셰이딩
        // 모든 좌표/색은 결정론적 해시(같은 별 = 항상 같은 무늬).
        val step = 2.0 * Math.PI / n
        fun jit(seed: Int) = facetHash(seed) - 0.5f

        val x0 = FloatArray(n + 1); val y0 = FloatArray(n + 1) // 코어 다각형
        val x1 = FloatArray(n + 1); val y1 = FloatArray(n + 1) // 중간 링(반 스텝 시프트)
        val x2 = FloatArray(n + 1); val y2 = FloatArray(n + 1) // 외곽(실루엣 밖 — clip 마무리)
        for (k in 0 until n) {
            val a0 = k * step + jit(k * 17 + t * 57 + 3) * step * 0.7
            val a1 = (k + 0.5) * step + jit(k * 29 + t * 71 + 5) * step * 0.7
            val a2 = k * step + jit(k * 41 + t * 13 + 9) * step * 0.7
            val r0 = sizePx * (0.09f + 0.10f * facetHash(k * 23 + t * 91 + 11))
            val r1 = sizePx * (0.24f + 0.15f * facetHash(k * 37 + t * 143 + 17))
            x0[k] = cx + (cos(a0) * r0).toFloat(); y0[k] = cy + (sin(a0) * r0).toFloat()
            x1[k] = cx + (cos(a1) * r1).toFloat(); y1[k] = cy + (sin(a1) * r1).toFloat()
            x2[k] = cx + (cos(a2) * sizePx).toFloat(); y2[k] = cy + (sin(a2) * sizePx).toFloat()
        }
        x0[n] = x0[0]; y0[n] = y0[0]; x1[n] = x1[0]; y1[n] = y1[0]; x2[n] = x2[0]; y2[n] = y2[0]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shard = Path()
        val edges = Path()
        val hsl = FloatArray(3)

        // 파편 색 — 명도 = 돔 구배(ringBias) + 해시 변주, 색상 ±24°, 채도 0.8~1.2, 6% 글린트(백색 혼합)
        fun shardColor(seed: Int, px: Float, py: Float, ringBias: Float): Int {
            val base = if (colors.size >= 2) {
                val f = (((px - left) + (py - top)) / (2f * sizePx)).coerceIn(0f, 1f)
                ColorUtils.blendARGB(colors[0], colors[1], f)
            } else colors[0]
            val h1 = facetHash(seed * 7 + t * 131 + 1)
            val h2 = facetHash(seed * 13 + t * 29 + 7)
            val h3 = facetHash(seed * 31 + t * 173 + 19)
            if (h3 > 0.94f) return ColorUtils.blendARGB(base, android.graphics.Color.WHITE, 0.55f) // 글린트
            ColorUtils.colorToHSL(base, hsl)
            hsl[0] = (hsl[0] + (h1 - 0.5f) * 48f + 360f) % 360f
            hsl[1] = (hsl[1] * (0.80f + 0.40f * h2)).coerceIn(0f, 1f)
            hsl[2] = (hsl[2] + ringBias + (h1 - 0.5f) * 0.18f).coerceIn(0.05f, 0.97f)
            return ColorUtils.HSLToColor(hsl)
        }

        fun drawShard(seed: Int, ringBias: Float, vararg pts: Float) {
            shard.reset()
            shard.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                shard.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            shard.close()
            var sx = 0f; var sy = 0f
            var j = 0
            while (j < pts.size) { sx += pts[j]; sy += pts[j + 1]; j += 2 }
            val m = pts.size / 2
            paint.color = shardColor(seed, sx / m, sy / m, ringBias)
            paint.alpha = alpha
            canvas.drawPath(shard, paint)
            edges.addPath(shard)
        }

        // 1) 중심 코어 다각형 — 가장 밝게(볼록의 정점)
        val core = FloatArray(n * 2)
        for (k in 0 until n) { core[k * 2] = x0[k]; core[k * 2 + 1] = y0[k] }
        drawShard(1, 0.15f, *core)

        // 2) 코어→중간 / 3) 중간→외곽 — 엇갈린 삼각형 스트립(공유 꼭짓점 없음 = 무패턴)
        for (k in 0 until n) {
            drawShard(100 + k * 2, 0.05f, x0[k], y0[k], x1[k], y1[k], x0[k + 1], y0[k + 1])
            drawShard(101 + k * 2, 0.02f, x1[k], y1[k], x0[k + 1], y0[k + 1], x1[k + 1], y1[k + 1])
            drawShard(400 + k * 2, -0.05f, x1[k], y1[k], x2[k], y2[k], x1[k + 1], y1[k + 1])
            drawShard(401 + k * 2, -0.09f, x2[k], y2[k], x1[k + 1], y1[k + 1], x2[k + 1], y2[k + 1])
        }

        // 능선 — 모든 파편 경계를 한 번에 옅은 흰 선으로
        canvas.drawPath(edges, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.011f).coerceAtLeast(0.7f)
            color = android.graphics.Color.WHITE
            this.alpha = 40 * alpha / 255
        })

        // ── 볼록 돔 셰이딩 ──
        // 좌상단으로 살짝 치우친 하이라이트(빛을 받는 볼록면) …
        canvas.drawRect(left, top, left + sizePx, top + sizePx, Paint().apply {
            shader = RadialGradient(
                cx - sizePx * 0.10f, cy - sizePx * 0.12f, sizePx * 0.45f,
                android.graphics.Color.argb(85 * alpha / 255, 255, 255, 255),
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        })
        // … + 실루엣 가장자리로 갈수록 가라앉는 음영(돔의 낙조면)
        canvas.drawRect(left, top, left + sizePx, top + sizePx, Paint().apply {
            shader = RadialGradient(
                cx, cy, sizePx * 0.5f,
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(70 * alpha / 255, 0, 0, 20)
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        })

        canvas.restoreToCount(save)
    }

    /**
     * 파편 메시 밀도(링당 꼭짓점 수) — 실제 조각 수 = 1(코어) + 4n.
     * 각도는 전부 지터되므로 별 꼭지 정렬 개념은 없다(불규칙 파편이 목적).
     */
    private fun facetDensity(type: Int): Int = when (type) {
        0, 4 -> 10
        // 결정: 컷 라인(💎 윤곽 안 검은 선)을 없앤 대신(2026-09-25) 파편을 더 촘촘하게 쪼개
        // 무늬만으로 "컷 면이 많다"는 인상을 준다 — 성기던 7 → 16(다른 타입보다도 촘촘).
        6 -> 16
        1, 7 -> 10
        2, 5 -> 12
        3 -> 14
        else -> 12
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
     * 결정(다이아몬드) 모양 크기 — 꽉 찬 다각형이라 뾰족한 별보다 무거워 보여 줄인다(iOS 동일 값).
     * 0.66(2026-09-14) → 0.56(2026-09-22 "더 줄여" 피드백).
     */
    private const val GEM_SCALE = 0.56f

    /**
     * 보석(다이아몬드) — 컷 다이아몬드 실루엣(테이블·거들·컬릿).
     *
     * 2026-09 "결정 모양이 구리다" 피드백으로 단순화: 컷 라인 12개 → 거들 높이 중심에서 5갈래
     * → 2026-09-22 "가운데 선 제거": 중심→컬릿 세로선을 빼고 크라운 V + 거들 가로선만.
     * → **2026-09-25 "안쪽 검은 선 전부 제거"**: 패싯 라인을 실루엣에서 빼내던(DIFFERENCE) 방식이라
     *   배경이 어두우면 그 틈이 검은 선으로 비쳤다 — 라인을 완전히 없애고, 대신 [facetDensity] 를
     *   높여(7→16) 파편 무늬 자체가 촘촘해지면서 "컷 면이 많다"는 인상을 대신 준다.
     * 크기는 [GEM_SCALE] 로 줄이고 세로 중심(원래 0.11~0.95 → 0.53)을 정사각 중앙으로 맞춘다.
     */
    private fun gemPath(s: Float): Path {
        // 원래 좌표계(0..1)를 중심 기준으로 GEM_SCALE 만큼 줄이고, 세로 중심 0.53 → 0.5 로 올린다.
        fun p(fx: Float, fy: Float) =
            ((0.5f + (fx - 0.5f) * GEM_SCALE) * s) to ((0.5f + (fy - 0.53f) * GEM_SCALE) * s)

        // 외곽: 테이블(윗면) + 좌우 어깨 → 거들(최대폭) → 컬릿(아래 한 점)
        return Path().apply {
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

    // ── 업적 보상 형태 9~20 (2026-09-26) ─────────────────────────────
    // 모두 0..1 정사각 좌표로 부품을 그린 뒤 [UnitKit] 이 sizePx 로 확대하고, 부품끼리 합집합([union])/
    // 차집합([minus])으로 실루엣을 만든다. iOS StarShape.swift 에 같은 좌표로 복제돼 있다(값 drift 금지).
    // 미리보기(사용자 승인본): https://claude.ai/artifact/527iWoEcZmTADTyG953YXs

    /** 0..1 좌표 부품 → 픽셀 경로. [scale] 은 정사각 중심 기준 배율(속이 꽉 찬 묵직한 형태를 줄일 때). */
    private class UnitKit(s: Float, scale: Float = 1f) {
        private val toPx = Matrix().apply { postScale(scale, scale, 0.5f, 0.5f); postScale(s, s) }

        fun part(local: Matrix? = null, build: Path.() -> Unit): Path =
            Path().apply(build).apply { local?.let { transform(it) }; transform(toPx) }

        fun circle(x: Float, y: Float, r: Float, local: Matrix? = null) =
            part(local) { addCircle(x, y, r, Path.Direction.CW) }

        fun oval(x: Float, y: Float, rx: Float, ry: Float) =
            part { addOval(RectF(x - rx, y - ry, x + rx, y + ry), Path.Direction.CW) }

        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, local: Matrix? = null) =
            part(local) { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }

        fun poly(xy: FloatArray) = part {
            moveTo(xy[0], xy[1])
            var i = 2
            while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
            close()
        }
    }

    private fun union(parts: List<Path>): Path =
        parts.reduce { acc, p -> Path().apply { op(acc, p, Path.Op.UNION) } }

    private fun Path.minus(vararg holes: Path): Path =
        holes.fold(this) { acc, h -> Path().apply { op(acc, h, Path.Op.DIFFERENCE) } }

    /** 원점 기준 부품 → 회전(도) 후 정사각 중앙으로 이동. */
    private fun aroundCenter(deg: Float) = Matrix().apply { postRotate(deg); postTranslate(0.5f, 0.5f) }

    /** 곡선 스파이크 별(0~4 와 같은 방식) — 임의 중심/반지름. */
    private fun Path.sparkle(cx: Float, cy: Float, r: Float, innerRatio: Float, spikes: Int) {
        val total = spikes * 2
        fun ptX(i: Int, len: Float) = (cx + cos(Math.toRadians(i * 360.0 / total)) * len).toFloat()
        fun ptY(i: Int, len: Float) = (cy + sin(Math.toRadians(i * 360.0 / total)) * len).toFloat()
        moveTo(ptX(0, r), ptY(0, r))
        for (i in 0 until spikes) {
            val next = ((i + 1) % spikes) * 2
            quadTo(ptX(i * 2 + 1, r * innerRatio), ptY(i * 2 + 1, r * innerRatio), ptX(next, r), ptY(next, r))
        }
        close()
    }

    /** 하트 윤곽(0..1, 뾰족한 끝 = (0.5, 0.84)). 네잎클로버 잎에도 쓴다. */
    private fun Path.heartOutline() {
        moveTo(0.5f, 0.84f)
        cubicTo(0.14f, 0.62f, 0.06f, 0.42f, 0.14f, 0.28f)
        cubicTo(0.23f, 0.12f, 0.43f, 0.13f, 0.5f, 0.30f)
        cubicTo(0.57f, 0.13f, 0.77f, 0.12f, 0.86f, 0.28f)
        cubicTo(0.94f, 0.42f, 0.86f, 0.62f, 0.5f, 0.84f)
        close()
    }

    /** 9 하트 — 속이 꽉 찬 형태라 0.82 로 줄인다. */
    private fun heartPath(s: Float): Path = UnitKit(s, 0.82f).part { heartOutline() }

    /**
     * 10 혜성 — 둥근 머리 + 살짝 휘며 가늘어지는 먼지 꼬리 + 틈을 두고 나란히 흐르는 가는 이온 꼬리 2줄
     * + 머리 앞의 작은 반짝임(2026-09-26 "혜성만 더 디테일하게").
     * 좌표는 머리 (0.64, 0.64) 기준 (a = 꼬리 방향 ↖ 성분, b = 옆 방향 ↗ 성분) 으로 적는다.
     */
    private fun cometPath(s: Float): Path {
        val k = UnitKit(s)
        val d = 0.70710677f
        fun x(a: Float, b: Float) = 0.64f - a * d + b * d
        fun y(a: Float, b: Float) = 0.64f - a * d - b * d
        fun Path.m(a: Float, b: Float) = moveTo(x(a, b), y(a, b))
        fun Path.q(ca: Float, cb: Float, a: Float, b: Float) = quadTo(x(ca, cb), y(ca, cb), x(a, b), y(a, b))
        val head = k.circle(0.64f, 0.64f, 0.145f)
        val tail = k.part { m(0f, 0.145f); q(0.36f, 0.14f, 0.76f, 0.05f); q(0.40f, -0.02f, 0f, -0.145f); close() }
        // 이온 꼬리는 양 끝이 뾰족한 렌즈 모양(시작점 = 끝점) — 뭉툭하게 잘린 밑동이 없게.
        val ionUpper = k.part { m(0.06f, 0.20f); q(0.30f, 0.26f, 0.62f, 0.20f); q(0.32f, 0.185f, 0.06f, 0.20f); close() }
        val ionLower = k.part { m(0.08f, -0.19f); q(0.28f, -0.235f, 0.50f, -0.10f); q(0.30f, -0.155f, 0.08f, -0.19f); close() }
        val glint = k.part { sparkle(x(-0.30f, 0f), y(-0.30f, 0f), 0.075f, 0.12f, 4) }
        return union(listOf(head, tail, ionUpper, ionLower, glint))
    }

    /** 11 눈꽃 — 둥근 막대 팔 6개 + 팔마다 비스듬한 가지 2개 + 가운데 원. */
    private fun snowflakePath(s: Float): Path {
        val k = UnitKit(s)
        val parts = mutableListOf(k.circle(0.5f, 0.5f, 0.10f))
        for (i in 0 until 6) {
            parts += k.rrect(-0.04f, -0.44f, 0.08f, 0.44f, 0.04f, aroundCenter(i * 60f))
            for (sgn in intArrayOf(-1, 1)) {
                val m = Matrix().apply {
                    postRotate(sgn * 55f); postTranslate(0f, -0.24f); postRotate(i * 60f); postTranslate(0.5f, 0.5f)
                }
                parts += k.rrect(-0.034f, -0.15f, 0.068f, 0.15f, 0.034f, m)
            }
        }
        return union(parts)
    }

    /** 12 벚꽃 — 끝이 살짝 갈라진 꽃잎 5장 + 가운데 원. */
    private fun sakuraPath(s: Float): Path {
        val k = UnitKit(s)
        val parts = mutableListOf(k.circle(0.5f, 0.5f, 0.08f))
        for (i in 0 until 5) {
            parts += k.part(aroundCenter(i * 72f)) {
                moveTo(0f, -0.05f)
                cubicTo(-0.11f, -0.11f, -0.20f, -0.27f, -0.12f, -0.44f)
                quadTo(-0.05f, -0.47f, 0f, -0.39f)
                quadTo(0.05f, -0.47f, 0.12f, -0.44f)
                cubicTo(0.20f, -0.27f, 0.11f, -0.11f, 0f, -0.05f)
                close()
            }
        }
        return union(parts)
    }

    /** 13 태양 — 원 + 떨어져 있는 곡선 광선 8갈래. */
    private fun sunPath(s: Float): Path {
        val k = UnitKit(s)
        val parts = mutableListOf(k.circle(0.5f, 0.5f, 0.21f))
        for (i in 0 until 8) {
            parts += k.part(aroundCenter(i * 45f)) {
                moveTo(-0.055f, -0.28f)
                quadTo(-0.02f, -0.38f, 0f, -0.47f)
                quadTo(0.02f, -0.38f, 0.055f, -0.28f)
                close()
            }
        }
        return union(parts)
    }

    /** 14 불꽃 — 옆으로 한 갈래 날름거리는 불꽃 + 아래쪽 속불(빈 물방울). */
    private fun flamePath(s: Float): Path {
        val k = UnitKit(s, 0.95f)
        val outer = k.part {
            moveTo(0.52f, 0.06f)
            cubicTo(0.60f, 0.22f, 0.80f, 0.34f, 0.80f, 0.60f)
            cubicTo(0.80f, 0.80f, 0.66f, 0.92f, 0.50f, 0.92f)
            cubicTo(0.34f, 0.92f, 0.20f, 0.80f, 0.20f, 0.62f)
            cubicTo(0.20f, 0.48f, 0.26f, 0.40f, 0.30f, 0.30f)
            cubicTo(0.34f, 0.40f, 0.38f, 0.46f, 0.42f, 0.48f)
            cubicTo(0.40f, 0.34f, 0.44f, 0.18f, 0.52f, 0.06f)
            close()
        }
        val inner = k.part {
            moveTo(0.50f, 0.54f)
            cubicTo(0.57f, 0.63f, 0.63f, 0.69f, 0.62f, 0.78f)
            cubicTo(0.61f, 0.86f, 0.39f, 0.86f, 0.38f, 0.78f)
            cubicTo(0.37f, 0.69f, 0.43f, 0.63f, 0.50f, 0.54f)
            close()
        }
        return outer.minus(inner)
    }

    /** 15 열쇠 — 고리(가운데 구멍) + 자루 + 이 2개, 45° 기울임. */
    private fun keyPath(s: Float): Path {
        val k = UnitKit(s)
        val tilt = Matrix().apply { postRotate(45f, 0.5f, 0.5f) }
        val body = union(listOf(
            k.circle(0.5f, 0.24f, 0.18f, tilt),
            k.rrect(0.455f, 0.38f, 0.09f, 0.52f, 0.03f, tilt),
            k.rrect(0.5f, 0.72f, 0.18f, 0.06f, 0.02f, tilt),
            k.rrect(0.5f, 0.82f, 0.13f, 0.06f, 0.02f, tilt),
        ))
        return body.minus(k.circle(0.5f, 0.24f, 0.075f, tilt))
    }

    /** 16 네잎클로버 — 하트 잎 4장(끝이 가운데로) + 오른쪽 아래로 휜 줄기. */
    private fun cloverPath(s: Float): Path {
        val k = UnitKit(s)
        val parts = mutableListOf<Path>()
        for (i in 0 until 4) {
            val m = Matrix().apply {
                postTranslate(-0.5f, -0.84f); postScale(0.5f, 0.5f); postTranslate(0f, -0.02f)
                postRotate(i * 90f); postTranslate(0.5f, 0.5f)
            }
            parts += k.part(m) { heartOutline() }
        }
        parts += k.part {
            moveTo(0.52f, 0.52f)
            quadTo(0.66f, 0.70f, 0.80f, 0.87f)
            lineTo(0.85f, 0.83f)
            quadTo(0.70f, 0.67f, 0.57f, 0.49f)
            close()
        }
        return union(parts)
    }

    /** 17 왕관 — 뾰족 세 개 몸통 + 아래 띠 + 꼭대기 구슬 3개. */
    private fun crownPath(s: Float): Path {
        val k = UnitKit(s, 0.92f)
        return union(listOf(
            k.poly(floatArrayOf(0.16f, 0.72f, 0.12f, 0.30f, 0.33f, 0.50f, 0.50f, 0.22f, 0.67f, 0.50f, 0.88f, 0.30f, 0.84f, 0.72f)),
            k.rrect(0.16f, 0.70f, 0.68f, 0.12f, 0.03f),
            k.circle(0.12f, 0.27f, 0.055f),
            k.circle(0.5f, 0.19f, 0.06f),
            k.circle(0.88f, 0.27f, 0.055f),
        ))
    }

    /** 18 나선 은하 — 가운데 팽대부 + 가늘어지며 도는 팔 2개(극좌표 샘플링). */
    private fun galaxyPath(s: Float): Path {
        val k = UnitKit(s)
        fun arm(start: Double): Path {
            val n = 28
            val outer = FloatArray((n + 1) * 2)
            val inner = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                val t = i / n.toDouble()
                val th = start + t * 1.25 * Math.PI
                val r = 0.10 + 0.34 * t
                val ri = maxOf(0.0, r - (0.13 * Math.pow(1 - t, 0.8) + 0.006))
                outer[i * 2] = (0.5 + r * cos(th)).toFloat(); outer[i * 2 + 1] = (0.5 + r * sin(th)).toFloat()
                inner[i * 2] = (0.5 + ri * cos(th)).toFloat(); inner[i * 2 + 1] = (0.5 + ri * sin(th)).toFloat()
            }
            // 바깥 가장자리를 따라 나갔다가 안쪽 가장자리로 되돌아온다.
            val pts = FloatArray(outer.size * 2)
            outer.copyInto(pts)
            for (i in 0..n) {
                pts[outer.size + i * 2] = inner[(n - i) * 2]
                pts[outer.size + i * 2 + 1] = inner[(n - i) * 2 + 1]
            }
            return k.poly(pts)
        }
        return union(listOf(k.circle(0.5f, 0.5f, 0.11f), arm(0.0), arm(Math.PI)))
    }

    /** 19 고양이 — 둥근 얼굴 + 뾰족 귀 2개 + 눈(빈 타원 2개). */
    private fun catPath(s: Float): Path {
        val k = UnitKit(s, 0.95f)
        val head = union(listOf(
            k.oval(0.5f, 0.6f, 0.34f, 0.27f),
            k.part { moveTo(0.2f, 0.5f); lineTo(0.19f, 0.2f); quadTo(0.19f, 0.14f, 0.25f, 0.17f); lineTo(0.45f, 0.36f); close() },
            k.part { moveTo(0.8f, 0.5f); lineTo(0.81f, 0.2f); quadTo(0.81f, 0.14f, 0.75f, 0.17f); lineTo(0.55f, 0.36f); close() },
        ))
        return head.minus(k.oval(0.38f, 0.6f, 0.045f, 0.065f), k.oval(0.62f, 0.6f, 0.045f, 0.065f))
    }

    /** 20 종이비행기 — 접힌 날개 실루엣(안쪽 접힌 선은 넣지 않는다 — 결정 모양 "검은 선" 피드백과 같은 이유). */
    private fun paperPlanePath(s: Float): Path =
        UnitKit(s).poly(floatArrayOf(0.92f, 0.14f, 0.07f, 0.47f, 0.37f, 0.57f, 0.45f, 0.88f, 0.56f, 0.66f, 0.78f, 0.77f))
}
