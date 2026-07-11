package com.chaminwoo.stary.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Geocoder
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 다이어리 공유 카드(체크리스트 30) — 인스타 스토리 규격(1080×1920) 세로 카드.
 *
 * 배경 = AI 생성 밤하늘 이미지(`assets/share_card_bg.webp`, 은하수+헤어라인 프레임+하단 실루엣 포함).
 * 배치는 이미지 구도에 맞춰 3단 구성:
 *  [상단]  마스트헤드 — STARY(Poetsen One) + 태그라인 (깨끗한 상단 하늘의 레터헤드)
 *  [중단]  주인공 별 — 은하수 밴드(세로 42~60% 대각선) 위쪽에 띄워 글로우가 밴드와 겹치게
 *  [하단]  디바이더 → 제목(Poor Story) → 작성자·날짜 → 위치 캡슐 (밴드 아래 어두운 하늘+스크림)
 *  최하단 산·숲 실루엣은 텍스트 없이 그대로 비워 이미지가 숨 쉬게 한다.
 */
object ShareCardHelper {

    private const val W = 1080
    private const val H = 1920

    /** 카드 렌더 → cache 저장 → 공유 시트. 실패해도 앱이 죽지 않게 조용히 무시. */
    suspend fun shareDiary(context: Context, diary: Diary) {
        try {
            val locationHint = resolveLocationHint(context, diary.latitude, diary.longitude)
            val bitmap = withContext(Dispatchers.Default) { renderCard(context, diary, locationHint) }
            val uri = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "share_card_${diary.id}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            val link = StaryConfig.shareLink(diary.id)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_diary_text, link))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_diary)))
        } catch (_: Exception) {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.share_failed))
        }
    }

    /** 역지오코딩으로 동네 이름(예: "서울 광진구"). 실패/불가 시 null → 카드에서 생략. */
    private suspend fun resolveLocationHint(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val addr = Geocoder(context, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@withContext null
                listOfNotNull(addr.adminArea, addr.locality ?: addr.subLocality ?: addr.subAdminArea)
                    .distinct().joinToString(" ").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }

    // ────────────────────────── 렌더 ──────────────────────────

    private fun renderCard(context: Context, diary: Diary, locationHint: String?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val accent = StarStyle.colorOf(diary.starColor).toArgb()
        val handFont = runCatching { ResourcesCompat.getFont(context, R.font.poor_story_regular) }.getOrNull()
        val brandFont = runCatching { ResourcesCompat.getFont(context, R.font.poetsen_one_regular) }.getOrNull()

        drawBackground(context, canvas, accent)

        // ── [상단] 마스트헤드 — STARY 로고 + 태그라인. 이미지 상단의 깨끗한 하늘을 레터헤드로 쓴다 ──
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 255, 255, 255)
            textSize = 50f
            textAlign = Paint.Align.CENTER
            typeface = brandFont ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.34f
            setShadowLayer(16f, 0f, 0f, ColorUtils.setAlphaComponent(accent, 110))
        }
        canvas.drawText("STARY", W / 2f, 206f, brandPaint)
        val brandHalf = brandPaint.measureText("STARY") / 2f
        drawSparkle(canvas, W / 2f - brandHalf - 58f, 189f, 11f, ColorUtils.setAlphaComponent(accent, 220))
        drawSparkle(canvas, W / 2f + brandHalf + 58f, 189f, 11f, ColorUtils.setAlphaComponent(accent, 220))
        val taglinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = handFont ?: Typeface.DEFAULT
        }
        canvas.drawText(context.getString(R.string.share_card_tagline), W / 2f, 282f, taglinePaint)

        // ── [중단] 주인공 별 — 은하수 밴드 위쪽(0.345H)에 띄워 글로우가 밴드에 살짝 겹치게 ──
        drawHeroStar(canvas, diary, accent, W / 2f, H * 0.345f)

        // ── [하단] 텍스트 블록 — 은하수 밴드(~0.60H)를 피해 0.66H 아래부터 ──
        var y = 1264f
        drawDivider(canvas, W / 2f, y, accent)
        y += 78f

        // 제목 — 손글씨(Poor Story), 최대 2줄
        val title = diary.title.ifBlank { context.getString(R.string.share_card_untitled) }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 92f
            typeface = handFont ?: Typeface.DEFAULT_BOLD
            setShadowLayer(24f, 0f, 0f, ColorUtils.setAlphaComponent(accent, 90))
        }
        val titleWidth = (W * 0.82f).toInt()
        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, titleWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.06f)
            .build()
        canvas.save()
        canvas.translate((W - titleWidth) / 2f, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 58f

        // 작성자 · 날짜
        val author = if (diary.isAnonymous || diary.userName.isBlank())
            context.getString(R.string.share_card_anonymous) else diary.userName
        val date = SimpleDateFormat("yyyy. MM. dd", Locale.getDefault()).format(Date(diary.createdAt))
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = 42f
            textAlign = Paint.Align.CENTER
            typeface = handFont ?: Typeface.DEFAULT
        }
        canvas.drawText("$author  ·  $date", W / 2f, y, metaPaint)

        // 위치 캡슐(있을 때만) — 별색 테두리 + 은은한 채움. 최하단 실루엣 존은 비워둔다.
        if (locationHint != null) {
            drawLocationPill(canvas, W / 2f, y + 88f, "✦ $locationHint", accent, handFont)
        }

        return out
    }

    /**
     * 배경 — AI 생성 밤하늘 이미지(`assets/share_card_bg.webp`)를 센터크롭으로 채운 뒤,
     * ① 하단 텍스트 존 가독성 스크림 ② 별색 무드 틴트를 얹는다.
     * 이미지 로드 실패 시 기존 남색 그라데이션으로 폴백(카드는 항상 만들어진다).
     */
    private fun drawBackground(context: Context, canvas: Canvas, accent: Int) {
        val bg = runCatching {
            context.assets.open("share_card_bg.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bg != null) {
            // 센터크롭 — 카드(9:16)와 비율이 달라도 중앙 기준으로 꽉 채운다
            val cardRatio = W.toFloat() / H
            val srcRatio = bg.width.toFloat() / bg.height
            val src = if (srcRatio > cardRatio) {
                val cropW = (bg.height * cardRatio).toInt()
                val x = (bg.width - cropW) / 2
                Rect(x, 0, x + cropW, bg.height)
            } else {
                val cropH = (bg.width / cardRatio).toInt()
                val yTop = (bg.height - cropH) / 2
                Rect(0, yTop, bg.width, yTop + cropH)
            }
            canvas.drawBitmap(bg, src, Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
            bg.recycle()
        } else {
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, H.toFloat(),
                    intArrayOf(0xFF0C1130.toInt(), 0xFF070B1E.toInt(), 0xFF04050D.toInt()),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            })
        }
        // 하단 텍스트 존 스크림 — 은하수 잔광 위 흰 글자 가독성 확보(위 투명 → 아래 은은한 검정)
        canvas.drawRect(0f, H * 0.56f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, H * 0.56f, 0f, H.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(92, 0, 0, 8), Color.argb(122, 0, 0, 8)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        })
        // 별 뒤에서 은은히 배어나는 별색 무드(카드마다 분위기가 달라진다)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(
                W / 2f, H * 0.345f, H * 0.42f,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 34), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
    }

    /** 주인공 별 — 원형 후광 + 수직/수평 빛줄기 + 글로우 본체 + 어두운 코어 + 궤도 스파클. */
    private fun drawHeroStar(canvas: Canvas, diary: Diary, accent: Int, cx: Float, cy: Float) {
        val starSize = 440f
        // 1) 큰 원형 후광(별색 → 투명)
        canvas.drawCircle(cx, cy, starSize * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, starSize * 1.1f,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 64), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
        // 2) 수직/수평으로 길게 스러지는 빛줄기(다이아몬드 광채)
        //    ⚠️ 수직 빛줄기가 상단 마스트헤드(STARY/태그라인) 텍스트를 관통하지 않게 1.25×로 제한
        val vLen = starSize * 1.25f
        val hLen = starSize * 1.05f
        val rayV = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            shader = RadialGradient(
                cx, cy, vLen,
                intArrayOf(Color.argb(150, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawLine(cx, cy - vLen, cx, cy + vLen, rayV)
        canvas.drawLine(cx - hLen, cy, cx + hLen, cy, rayV)

        // 3) 별 본체 — 마커와 같은 정의(StarStyle)
        val left = cx - starSize / 2f
        val top = cy - starSize / 2f
        val path = android.graphics.Path(StarStyle.starPath(diary.starType, starSize)).apply {
            offset(left, top)
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, glow)
        canvas.drawPath(path, glow)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val shader = StarStyle.fillShader(diary.starColor, left, top, starSize)
            if (shader != null) this.shader = shader else color = accent
        })
        val core = android.graphics.Path(path)
        core.transform(android.graphics.Matrix().apply { setScale(0.8f, 0.8f, cx, cy) })
        canvas.drawPath(core, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accent, Color.BLACK, 0.05f)
        })

        // 4) 별 주위를 도는 작은 스파클 3개 — 정적이지만 "궤도" 리듬을 준다
        drawSparkle(canvas, cx - starSize * 0.62f, cy - starSize * 0.40f, 15f, ColorUtils.setAlphaComponent(Color.WHITE, 220))
        drawSparkle(canvas, cx + starSize * 0.66f, cy - starSize * 0.10f, 11f, ColorUtils.setAlphaComponent(accent, 235))
        drawSparkle(canvas, cx + starSize * 0.40f, cy + starSize * 0.55f, 9f, ColorUtils.setAlphaComponent(Color.WHITE, 190))
    }

    /** 작은 4꼭지 스파클(장식). */
    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = android.graphics.Path(StarStyle.starPath(0, r * 2f)).apply { offset(x - r, y - r) }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** 장식 디바이더 — "─── ✦ ───" 하늘과 글 사이의 숨고르기. */
    private fun drawDivider(canvas: Canvas, cx: Float, cy: Float, accent: Int) {
        val half = 180f
        val gap = 44f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.6f }
        line.shader = LinearGradient(
            cx - half, cy, cx - gap, cy,
            Color.TRANSPARENT, Color.argb(120, 255, 255, 255), Shader.TileMode.CLAMP
        )
        canvas.drawLine(cx - half, cy, cx - gap, cy, line)
        line.shader = LinearGradient(
            cx + gap, cy, cx + half, cy,
            Color.argb(120, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawLine(cx + gap, cy, cx + half, cy, line)
        drawSparkle(canvas, cx, cy, 12f, ColorUtils.setAlphaComponent(accent, 230))
    }

    /** 위치 캡슐 — 별색 테두리 + 은은한 채움의 알약형 배지. */
    private fun drawLocationPill(canvas: Canvas, cx: Float, cy: Float, text: String, accent: Int, font: Typeface?) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accent, Color.WHITE, 0.25f)
            textSize = 44f
            textAlign = Paint.Align.CENTER
            typeface = font ?: Typeface.DEFAULT
        }
        val textWidth = textPaint.measureText(text)
        val padH = 46f
        val pillH = 92f
        val rect = RectF(cx - textWidth / 2f - padH, cy - pillH / 2f, cx + textWidth / 2f + padH, cy + pillH / 2f)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accent, 26)
        })
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = ColorUtils.setAlphaComponent(accent, 140)
        })
        // 세로 중앙 정렬(baseline 보정)
        val baseline = cy - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(text, cx, baseline, textPaint)
    }
}
