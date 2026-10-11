package com.chaminwoo.stary.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.feature.globe.GlobeRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 공유 영상 위에 얹는 그림들 — 글 블록 2장(위: 별 아이콘·좌표·제목 / 아래: 날짜·버튼)과
 * 빛(빛기둥·번짐·고리·주인공 별빛) 4장. 전부 한 번만 만들어 텍스처로 올리고, 프레임마다 위치·크기·투명도만 바꾼다.
 *
 * 빛 4장은 **픽셀마다 식으로 계산**한다(블러 API 를 쓰지 않는다) — iOS `ShareVideoOverlay.swift` 가 같은 식을 써서 두 플랫폼 모양이 같다.
 * 좌표·크기는 디자인 px(1080×1920). 글꼴은 기기 내장(명조 굵게 / 고정폭 / 산세리프) — 한·영·일 제목을 모두 그릴 수 있고 앱 용량이 늘지 않는다.
 *
 * ⚠️ [context] 는 인앱 언어가 적용된 것(액티비티)이어야 버튼 문구가 설정 언어로 나온다(CLAUDE.md §2.5).
 */
internal class ShareVideoOverlay(context: Context, diary: Diary) {

    /** 한 장의 글 블록 — [top] 은 블록 비트맵 맨 위가 놓일 화면 y(디자인 px). 가로는 화면 전체(1080). */
    class Block(val bitmap: Bitmap, val top: Float)

    /** 빛 색(0..1 RGB) — 별 색 그대로(그라데이션 별은 두 색 평균). */
    private val tint: FloatArray = StarStyle.colorsOf(diary.starColor).let { cs ->
        floatArrayOf(
            cs.map { it.red }.average().toFloat(),
            cs.map { it.green }.average().toFloat(),
            cs.map { it.blue }.average().toFloat(),
        )
    }

    val topBlock: Block = buildTopBlock(context, diary)
    val bottomBlock: Block = buildBottomBlock(context, diary)
    val beam: Bitmap = buildBeam()
    val glow: Bitmap = buildGlow()
    val ring: Bitmap = buildRing()
    val hero: Bitmap = buildHero()

    fun recycle() {
        listOf(topBlock.bitmap, bottomBlock.bitmap, beam, glow, ring, hero).forEach { if (!it.isRecycled) it.recycle() }
    }

    // ────────────────────────── 글 블록 ──────────────────────────

    private fun tintArgb(alpha: Float = 1f): Int =
        Color.argb((alpha * 255f + 0.5f).toInt(), (tint[0] * 255f).toInt(), (tint[1] * 255f).toInt(), (tint[2] * 255f).toInt())

    /** 위쪽: 그 별의 크리스탈 아이콘 → 좌표(별 색, 고정폭) → 제목(명조 굵게, 최대 2줄). */
    private fun buildTopBlock(context: Context, diary: Diary): Block {
        val w = ShareVideoSpec.W.toInt()
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 27f
            letterSpacing = 0.2f
            color = tintArgb()
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 81f
            letterSpacing = -0.01f
            color = Color.WHITE
            setShadowLayer(40f, 0f, 6f, 0xE602030A.toInt())
        }
        val title = diary.title.trim().ifBlank { context.getString(R.string.share_card_untitled) }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, TITLE_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

        val coordFm = coordPaint.fontMetrics
        val coordH = coordFm.descent - coordFm.ascent
        val height = PAD + ICON + ICON_GAP + coordH + COORD_GAP + titleLayout.height + PAD
        val bmp = Bitmap.createBitmap(w, height.toInt() + 1, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var y = PAD
        StarStyle.drawCrystalFill(c, diary.starType, diary.starColor, w / 2f - ICON / 2f, y, ICON)
        y += ICON + ICON_GAP
        c.drawText(coordText(diary.latitude, diary.longitude), w / 2f, y - coordFm.ascent, coordPaint)
        y += coordH + COORD_GAP
        c.save()
        c.translate((w - TITLE_WIDTH) / 2f, y)
        titleLayout.draw(c)
        c.restore()
        return Block(bmp, ShareVideoSpec.TOP_BLOCK_Y - PAD)
    }

    /** 아래쪽: 날짜(고정폭, 옅게) → "Stary 에서 이 별 찾기" 알약 버튼(반투명 유리 + 화살표). */
    private fun buildBottomBlock(context: Context, diary: Diary): Block {
        val w = ShareVideoSpec.W.toInt()
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 27f
            letterSpacing = 0.12f
            color = 0xB8FFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }
        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 34.5f
            color = Color.WHITE
        }
        val cta = context.getString(R.string.share_video_cta)
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(diary.createdAt))

        val dateFm = datePaint.fontMetrics
        val dateH = dateFm.descent - dateFm.ascent
        val textW = ctaPaint.measureText(cta)
        val pillW = PILL_PAD_H * 2 + textW + PILL_GAP + ARROW
        val height = PAD + dateH + DATE_GAP + PILL_H + PAD
        val bmp = Bitmap.createBitmap(w, height.toInt() + 1, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var y = PAD
        c.drawText(date, w / 2f, y - dateFm.ascent, datePaint)
        y += dateH + DATE_GAP

        val left = (w - pillW) / 2f
        val pill = RectF(left, y, left + pillW, y + PILL_H)
        c.drawRoundRect(pill, PILL_H / 2f, PILL_H / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF })
        val inset = 1.5f
        c.drawRoundRect(
            RectF(pill.left + inset, pill.top + inset, pill.right - inset, pill.bottom - inset), PILL_H / 2f, PILL_H / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x4DFFFFFF },
        )
        val fm = ctaPaint.fontMetrics
        val cy = pill.centerY()
        c.drawText(cta, left + PILL_PAD_H, cy - (fm.ascent + fm.descent) / 2f, ctaPaint)
        // 화살표(→) — 12 단위 그리드의 "M2 6h8 M6.5 2.5 L10 6 L6.5 9.5" 를 ARROW 크기로
        val ax = left + PILL_PAD_H + textW + PILL_GAP
        val ay = cy - ARROW / 2f
        val k = ARROW / 12f
        val arrow = Path().apply {
            moveTo(ax + 2f * k, ay + 6f * k); lineTo(ax + 10f * k, ay + 6f * k)
            moveTo(ax + 6.5f * k, ay + 2.5f * k); lineTo(ax + 10f * k, ay + 6f * k); lineTo(ax + 6.5f * k, ay + 9.5f * k)
        }
        c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4.5f; color = Color.WHITE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        return Block(bmp, ShareVideoSpec.BOTTOM_BLOCK_Y - PAD)
    }

    // ────────────────────────── 빛(식으로 계산) ──────────────────────────

    /** (밝기, 색) → 비트맵 픽셀. 비트맵은 프리멀티플라이드로 저장되므로 더하기 합성하면 "색 × 밝기"가 더해진다. */
    private fun pixel(a: Float, r: Float, g: Float, b: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt(), (r.coerceIn(0f, 1f) * 255f).toInt(), (g.coerceIn(0f, 1f) * 255f).toInt(), (b.coerceIn(0f, 1f) * 255f).toInt())

    /**
     * 빛기둥 — 아래(별)에서 가장 밝고 위로 갈수록 가늘어지며 사라진다. 가는 흰 심지 + 별 색의 넓은 번짐.
     * 가로 1텍셀 = 1px(반폭 [ShareVideoSpec.BEAM_HALF_W]), 세로는 그릴 때 길이에 맞춰 늘린다.
     */
    private fun buildBeam(): Bitmap {
        val w = BEAM_W; val h = BEAM_H
        val px = IntArray(w * h)
        for (j in 0 until h) {
            val u = 1f - (j + 0.5f) / h                       // 0 = 별(아래), 1 = 끝(위)
            val along = if (u < 0.5f) 0.95f - 0.57f * (u / 0.5f) else 0.38f * (1f - (u - 0.5f) / 0.5f)
            val sigmaCore = 3.0f - 1.8f * u
            val sigmaHalo = 26f - 15f * u
            for (i in 0 until w) {
                val x = (i + 0.5f) - w / 2f
                val core = exp(-(x / sigmaCore) * (x / sigmaCore))
                val halo = 0.62f * exp(-(x / sigmaHalo) * (x / sigmaHalo))
                val hot = core * 0.85f                           // 심지는 흰빛으로
                px[j * w + i] = pixel(
                    along * (core + halo),
                    tint[0] + (1f - tint[0]) * hot, tint[1] + (1f - tint[1]) * hot, tint[2] + (1f - tint[2]) * hot,
                )
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 별 주위의 부드러운 번짐 — 중심 0.55 → 35% 지점 0.19 → 가장자리 0. */
    private fun buildGlow(): Bitmap = radial(128) { r ->
        if (r >= 1f) 0f else if (r < 0.35f) 0.55f - 0.3575f * (r / 0.35f) else 0.1925f * (1f - (r - 0.35f) / 0.65f)
    }

    /** 퍼지는 고리 — 반지름 86% 지점의 가는 선. */
    private fun buildRing(): Bitmap = radial(128) { r -> exp(-((r - 0.86f) / 0.045f) * ((r - 0.86f) / 0.045f)) }

    private inline fun radial(size: Int, alphaAt: (Float) -> Float): Bitmap {
        val px = IntArray(size * size)
        for (j in 0 until size) for (i in 0 until size) {
            val x = (i + 0.5f) / size * 2f - 1f
            val y = (j + 0.5f) / size * 2f - 1f
            px[j * size + i] = pixel(alphaAt(sqrt(x * x + y * y)), tint[0], tint[1], tint[2])
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * 주인공 별빛 — 글로브의 인기 별과 같은 모양([GlobeRenderer.sparkCoverage])을 `PIN_FS` 와 같은 식으로 색칠한다
     * (불투명도 = 밝기^1.6, 색 = 별 색조 → 심지 쪽은 흰색).
     */
    private fun buildHero(): Bitmap {
        val s = HERO_SIZE
        val mx = max(max(tint[0], tint[1]), max(tint[2], 0.001f))
        val tr = tint[0] / mx; val tg = tint[1] / mx; val tb = tint[2] / mx
        val px = IntArray(s * s)
        for (j in 0 until s) for (i in 0 until s) {
            val x = (i + 0.5f) / s * 2f - 1f
            val y = (j + 0.5f) / s * 2f - 1f
            val cov = GlobeRenderer.sparkCoverage(x, y, GlobeRenderer.STAR_MAIN_LEN, GlobeRenderer.STAR_CROSS_LEN, GlobeRenderer.STAR_DIAG_WEIGHT)
            val e = ((cov - 0.75f) / 0.25f).coerceIn(0f, 1f)
            val white = e * e * (3f - 2f * e) * 0.7f
            val a = Math.pow(cov.toDouble(), 1.6).toFloat()
            px[j * s + i] = pixel(a, tr + (1f - tr) * white, tg + (1f - tg) * white, tb + (1f - tb) * white)
        }
        return Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888)
    }

    companion object {
        // 글 블록 치수(디자인 px) — 레퍼런스(360 폭)의 3배.
        private const val PAD = 60f          // 그림자·획이 잘리지 않게 블록 둘레에 두는 여백
        private const val ICON = 42f
        private const val ICON_GAP = 33f
        private const val COORD_GAP = 22f
        private const val TITLE_WIDTH = 960
        private const val DATE_GAP = 51f
        private const val PILL_H = 99f
        private const val PILL_PAD_H = 45f
        private const val PILL_GAP = 21f
        private const val ARROW = 36f
        private const val BEAM_W = 96        // = 2 × ShareVideoSpec.BEAM_HALF_W
        private const val BEAM_H = 384
        private const val HERO_SIZE = 192

        /** "37.56° N · 126.93° E" — 소수 자릿수는 [ShareVideoSpec.COORD_DECIMALS]. */
        fun coordText(lat: Double, lng: Double): String {
            val d = ShareVideoSpec.COORD_DECIMALS
            return String.format(
                Locale.US, "%.${d}f° %s · %.${d}f° %s",
                abs(lat), if (lat >= 0) "N" else "S", abs(lng), if (lng >= 0) "E" else "W",
            )
        }
    }
}
