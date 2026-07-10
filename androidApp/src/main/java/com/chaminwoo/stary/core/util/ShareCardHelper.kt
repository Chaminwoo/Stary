package com.chaminwoo.stary.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.location.Geocoder
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
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
import kotlin.math.pow
import kotlin.random.Random

/**
 * 다이어리 공유 카드(체크리스트 30) — 밤하늘 배경 + 그 별의 모양/색 + 제목/장소 힌트를
 * 세로형(1080×1920, 인스타 스토리 규격) 이미지로 렌더해 시스템 공유 시트로 내보낸다.
 * 함께 붙는 텍스트에 웹 랜딩 링크([StaryConfig.shareLink])를 넣어 유입 경로를 만든다.
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

    private fun renderCard(context: Context, diary: Diary, locationHint: String?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1) 밤하늘 배경 — 세로 그라데이션(짙은 남색 → 검정)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(0xFF0B1026.toInt(), 0xFF070A18.toInt(), 0xFF04050C.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        })

        // 2) 배경 잔별 — 시드 고정(같은 다이어리 = 같은 하늘), 크기/밝기 변주
        val rnd = Random(diary.id.hashCode())
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(170) {
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H
            val r = 1f + rnd.nextFloat().pow(2) * 3.2f
            dotPaint.color = Color.WHITE
            dotPaint.alpha = (40 + rnd.nextInt(160))
            canvas.drawCircle(x, y, r, dotPaint)
        }
        // 큰 반짝이 몇 개(별색 계열)
        val accent = StarStyle.colorOf(diary.starColor).toArgb()
        repeat(6) {
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H * 0.8f
            dotPaint.color = accent
            dotPaint.alpha = 70 + rnd.nextInt(60)
            dotPaint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, 4f + rnd.nextFloat() * 5f, dotPaint)
        }
        dotPaint.maskFilter = null

        // 3) 중앙 별 — 마커와 같은 정의(StarStyle)로 크게. 후광 → 본체 → 어두운 코어.
        val starSize = 460f
        val cx = W / 2f
        val cy = H * 0.36f
        // 은은한 원형 후광
        canvas.drawCircle(cx, cy, starSize * 0.95f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, starSize * 0.95f,
                intArrayOf((accent and 0xFFFFFF) or 0x33000000, (accent and 0xFFFFFF)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
        val left = cx - starSize / 2f
        val top = cy - starSize / 2f
        val path = android.graphics.Path(StarStyle.starPath(diary.starType, starSize)).apply {
            offset(left, top)
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
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
            color = androidx.core.graphics.ColorUtils.blendARGB(accent, Color.BLACK, 0.05f)
        })

        // 4) 제목(최대 2줄) + 작성자/날짜 + 장소 힌트
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 66f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val title = diary.title.ifBlank { context.getString(R.string.share_card_untitled) }
        val titleWidth = (W * 0.78f).toInt()
        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, titleWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.withSave {
            translate((W - titleWidth) / 2f, H * 0.60f)
            titleLayout.draw(this)
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        val author = if (diary.isAnonymous || diary.userName.isBlank())
            context.getString(R.string.share_card_anonymous) else diary.userName
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(diary.createdAt))
        canvas.drawText("$author · $date", cx, H * 0.60f + titleLayout.height + 76f, metaPaint)

        if (locationHint != null) {
            val locPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = 44f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText("✦ $locationHint", cx, H * 0.60f + titleLayout.height + 156f, locPaint)
        }

        // 5) 하단 — 태그라인 + 브랜드
        val taglinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(context.getString(R.string.share_card_tagline), cx, H * 0.875f, taglinePaint)
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = 0.28f
        }
        canvas.drawText("STARY", cx, H * 0.875f + 86f, brandPaint)

        return out
    }

    /** Canvas.save/restore 블록 헬퍼. */
    private inline fun Canvas.withSave(block: Canvas.() -> Unit) {
        val s = save()
        try {
            block()
        } finally {
            restoreToCount(s)
        }
    }
}
