package com.chaminwoo.stary.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Geocoder
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * 다이어리 공유 카드(체크리스트 30) — 인스타 스토리 규격(1080×1920) 세로 카드.
 *
 * 디자인(2026-07-11 전면 개편):
 *  배경 = AI 생성 밤하늘 이미지(`assets/share_card_bg.webp`, 은하수 포함).
 *  중앙에 **별의 실제 좌표를 중심으로 한 나라+주변국 지도**(MapTiler 정적 지도)를
 *  원형 페더 마스크로 은하수를 가리지 않을 만큼 은은하게 띄우고, 그 정중앙(=위치)에
 *  **작은 별**을 찍는다. 하단엔 제목·위치·날짜만(작성자 이름·상단 로고·별 십자광선 없음).
 *
 * 공유 경로:
 *  - [shareToInstagramStory] : 인스타 스토리 직접 공유(ADD_TO_STORY + content_url 링크스티커).
 *    미설치 시 일반 공유 시트로 폴백.
 *  - [shareDiary] : 일반 시스템 공유 시트(카카오/메시지 등, 링크는 본문 텍스트).
 *  링크(`StaryConfig.shareLink`)를 열면 웹 랜딩이 앱 설치자는 stary://diary/{id} 딥링크로,
 *  미설치자는 스토어로 보낸다.
 */
object ShareCardHelper {

    private const val W = 1080
    private const val H = 1920
    private const val INSTAGRAM_PKG = "com.instagram.android"

    /**
     * 카드에 추가로 얹는 장식 별(내 다이어리에서 가져온 별) — 개별 위치/크기 조절.
     * [xFrac]/[yFrac] 는 카드 내 상대 위치(0..1), [scale] 은 크기 배율(0.2..2.5).
     */
    data class ExtraStar(
        val type: Int,
        val colorIndex: Int,
        val xFrac: Float,
        val yFrac: Float,
        val scale: Float = 0.6f,
    )

    /**
     * 공유 카드 편집 옵션 — 공유 버튼을 누르면 뜨는 편집 화면(ShareCardEditor)에서 사용자가 조정한다.
     * [stageXFrac]/[stageYFrac] 는 별(+지도 무대) 중심의 카드 내 상대 위치(0..1).
     * 제목/위치/날짜도 각자 상대 위치를 가져 자유롭게 배치할 수 있다(앵커 = 요소 중심).
     */
    data class ShareCardOptions(
        val title: String? = null,          // null → diary.title
        val showMap: Boolean = true,        // 나라 지도 원 표시
        val showLocation: Boolean = true,   // 동네 이름 캡슐
        val showDate: Boolean = true,       // 날짜
        val stageXFrac: Float = 0.5f,
        val stageYFrac: Float = 0.40f,
        val starScale: Float = 1f,          // 별 크기 배율(0.25..2.2)
        val titleXFrac: Float = 0.5f,
        val titleYFrac: Float = 0.71f,
        val locationXFrac: Float = 0.5f,
        val locationYFrac: Float = 0.795f,
        val dateXFrac: Float = 0.5f,
        val dateYFrac: Float = 0.85f,
        val extraStars: List<ExtraStar> = emptyList(),
    )

    /**
     * 렌더에 필요한 네트워크 자산(역지오코딩 동네 이름 + 정적 지도) — 편집 미리보기에서
     * 옵션이 바뀔 때마다 다시 받지 않도록 1회 준비 후 재사용한다. 다 쓰면 [release].
     */
    class ShareCardAssets internal constructor(
        val locationHint: String?,
        val regionMap: Bitmap?,
    ) {
        fun release() { regionMap?.takeIf { !it.isRecycled }?.recycle() }
    }

    /** 편집 미리보기/공유 공용 — 카드 렌더 자산 1회 로드. */
    suspend fun prepareAssets(context: Context, diary: Diary): ShareCardAssets = ShareCardAssets(
        locationHint = resolveLocationHint(context, diary.latitude, diary.longitude),
        regionMap = fetchRegionMap(diary.latitude, diary.longitude),
    )

    /** 일반 공유 시트 — 카드 이미지 + 링크(본문). */
    suspend fun shareDiary(
        context: Context,
        diary: Diary,
        options: ShareCardOptions = ShareCardOptions(),
        assets: ShareCardAssets? = null,
    ) {
        try {
            val uri = renderAndCache(context, diary, options, assets)
            launchChooser(context, uri, StaryConfig.shareLink(diary.id))
        } catch (_: Exception) {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.share_failed))
        }
    }

    /**
     * 인스타그램 스토리 직접 공유 — 카드를 스토리 배경으로, [content_url] 을 탭 가능한 링크스티커로.
     * 인스타 미설치/실패 시 일반 공유 시트로 폴백해 항상 무언가는 동작하게 한다.
     */
    suspend fun shareToInstagramStory(
        context: Context,
        diary: Diary,
        options: ShareCardOptions = ShareCardOptions(),
        assets: ShareCardAssets? = null,
    ) {
        try {
            val uri = renderAndCache(context, diary, options, assets)
            val link = StaryConfig.shareLink(diary.id)
            if (!launchInstagramStory(context, uri, link)) {
                launchChooser(context, uri, link)
            }
        } catch (_: Exception) {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.share_failed))
        }
    }

    // ────────────────────────── 공유 실행 ──────────────────────────

    private fun launchChooser(context: Context, uri: Uri, link: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_diary_text, link))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_diary)))
    }

    /**
     * 인스타 스토리 인텐트 실행. 인스타 미설치면 false 반환(→ 폴백).
     *
     * ⚠️ 링크스티커(content_url)는 Meta(Facebook) 앱 ID(`source_application`) 등록 + 인스타 측
     * 인정이 있어야만 자동으로 붙는다 — 미등록(빈 값)이면 인스타가 조용히 무시해 "링크가 안 뜨는"
     * 증상이 된다. 그래서 항상 링크를 클립보드에 복사해 두고, 스토리 편집기의 '링크 스티커'로
     * 직접 붙여넣을 수 있게 시스템 토스트로 안내한다(앱이 배경으로 가도 보이도록 StaryToast 아님).
     */
    private fun launchInstagramStory(context: Context, uri: Uri, link: String): Boolean {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            if (BuildConfig.INSTAGRAM_APP_ID.isNotBlank()) {
                putExtra("source_application", BuildConfig.INSTAGRAM_APP_ID)
                putExtra("content_url", link) // 탭 가능한 링크스티커(귀속엔 source_application 필요)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // FileProvider Uri 를 인스타에 명시적으로 읽기 권한 부여
        context.grantUriPermission(INSTAGRAM_PKG, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            // 링크 클립보드 복사 → 스토리에서 링크 스티커로 붙여넣기 안내(전환 직전에 띄워야 보인다)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("stary-link", link))
            context.startActivity(intent)
            android.widget.Toast.makeText(
                context, context.getString(R.string.share_story_link_copied), android.widget.Toast.LENGTH_LONG
            ).show()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** 카드 렌더 → cache PNG 저장 → 공유 가능한 content:// Uri. [assets] 없으면 내부 준비 후 해제. */
    private suspend fun renderAndCache(
        context: Context,
        diary: Diary,
        options: ShareCardOptions,
        sharedAssets: ShareCardAssets?,
    ): Uri {
        val assets = sharedAssets ?: prepareAssets(context, diary)
        val bitmap = try {
            withContext(Dispatchers.Default) { renderCard(context, diary, assets, options) }
        } finally {
            if (sharedAssets == null) assets.release() // 편집 화면이 준 자산은 그쪽에서 해제
        }
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "share_card_${diary.id}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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

    /** 지역 지도 타일 줌/출력 변(px). 줌 4 = 나라+주변국 스케일. */
    private const val REGION_MAP_ZOOM = 4
    private const val REGION_MAP_SIDE = 512

    /**
     * 별 좌표를 중심으로 한 나라+주변국 지도 — MapTiler **래스터 타일**(dataviz-dark, z4)을
     * 직접 이어붙여 좌표 중심 512px 비트맵을 만든다.
     * ⚠️ 정적 지도 API(/maps/{style}/static/...)는 이 프로젝트 키/플랜에서 403 이라 사용 불가
     *    (라이브 지도가 쓰는 타일 엔드포인트는 허용) → 타일 스티칭으로 대체(2026-07-12).
     * 네트워크/키 문제 시 null → 지도 없이 하늘 위 별만 그린다(카드는 항상 생성).
     */
    private suspend fun fetchRegionMap(lat: Double, lng: Double): Bitmap? = withContext(Dispatchers.IO) {
        val key = BuildConfig.MAPTILER_KEY
        if (key.isBlank() || key.startsWith("TODO")) return@withContext null
        runCatching {
            val n = 1 shl REGION_MAP_ZOOM
            // 웹 메르카토르: 좌표 → 전역 픽셀(타일 한 변 = REGION_MAP_SIDE 로 정규화)
            val latRad = Math.toRadians(lat.coerceIn(-85.05, 85.05))
            val px = (lng + 180.0) / 360.0 * n * REGION_MAP_SIDE
            val py = (1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * n * REGION_MAP_SIDE
            val left = px - REGION_MAP_SIDE / 2.0
            val top = py - REGION_MAP_SIDE / 2.0

            val out = Bitmap.createBitmap(REGION_MAP_SIDE, REGION_MAP_SIDE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(0xFF10141F.toInt()) // 타일 결손/극지 폴백(스타일 바다색 근사)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var drawnAny = false
            val tx0 = floor(left / REGION_MAP_SIDE).toInt()
            val ty0 = floor(top / REGION_MAP_SIDE).toInt()
            for (ty in ty0..ty0 + 1) {
                if (ty < 0 || ty >= n) continue
                for (tx in tx0..tx0 + 1) {
                    val wrapX = ((tx % n) + n) % n // 날짜변경선 래핑
                    val url = "https://api.maptiler.com/maps/dataviz-dark/$REGION_MAP_ZOOM/$wrapX/$ty.png?key=$key"
                    val tile = runCatching {
                        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 6000
                            readTimeout = 6000
                        }
                        conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull() ?: continue
                    canvas.drawBitmap(
                        tile, null,
                        RectF(
                            (tx * REGION_MAP_SIDE - left).toFloat(),
                            (ty * REGION_MAP_SIDE - top).toFloat(),
                            ((tx + 1) * REGION_MAP_SIDE - left).toFloat(),
                            ((ty + 1) * REGION_MAP_SIDE - top).toFloat()
                        ),
                        paint
                    )
                    tile.recycle()
                    drawnAny = true
                }
            }
            if (!drawnAny) {
                out.recycle()
                null
            } else out
        }.getOrNull()
    }

    // ────────────────────────── 렌더 ──────────────────────────

    /** 카드 렌더 — 편집 미리보기(ShareCardEditor)에서도 호출하므로 public. 자산은 재사용(재활용 안 함). */
    fun renderCard(context: Context, diary: Diary, assets: ShareCardAssets, options: ShareCardOptions): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val accent = StarStyle.colorOf(diary.starColor).toArgb()
        val handFont = runCatching { ResourcesCompat.getFont(context, R.font.poor_story_regular) }.getOrNull()
        val locationHint = assets.locationHint
        val regionMap = assets.regionMap?.takeIf { options.showMap && !it.isRecycled }

        // 중앙 무대 좌표 — 지도 원과 별이 여기 겹쳐 앉는다(편집에서 드래그로 이동 가능).
        val stageCx = W * options.stageXFrac.coerceIn(0.08f, 0.92f)
        val stageCy = H * options.stageYFrac.coerceIn(0.08f, 0.90f)
        val mapRadius = 250f

        drawBackground(context, canvas, accent, stageCx, stageCy)

        // ── [무대] 나라 지도(있으면) → 그 정중앙에 별 ──
        if (regionMap != null) {
            drawRegionMap(canvas, regionMap, stageCx, stageCy, mapRadius, accent)
        }

        // ── 추가 별(내 다이어리에서 가져온 별) — 히어로 별 아래 레이어 ──
        options.extraStars.forEach { star ->
            drawExtraStar(canvas, star)
        }

        drawHeroStar(
            canvas, diary, accent, stageCx, stageCy,
            hasMap = regionMap != null,
            scale = options.starScale.coerceIn(0.2f, 2.5f)
        )

        // ── 텍스트 — 제목/위치/날짜 각자 위치(앵커=요소 중심)에 자유 배치 ──
        val title = (options.title ?: diary.title).ifBlank { context.getString(R.string.share_card_untitled) }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 90f
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
        canvas.translate(
            W * options.titleXFrac.coerceIn(0.08f, 0.92f) - titleWidth / 2f,
            H * options.titleYFrac.coerceIn(0.05f, 0.95f) - titleLayout.height / 2f
        )
        titleLayout.draw(canvas)
        canvas.restore()

        // 위치 캡슐(있을 때만 + 옵션 켜짐) — 별색 테두리 알약형 배지
        if (options.showLocation && locationHint != null) {
            drawLocationPill(
                canvas,
                W * options.locationXFrac.coerceIn(0.08f, 0.92f),
                H * options.locationYFrac.coerceIn(0.05f, 0.96f),
                "✦ $locationHint", accent, handFont
            )
        }

        // 날짜 — 작고 은은하게(옵션으로 숨김 가능)
        if (options.showDate) {
            val date = SimpleDateFormat("yyyy. MM. dd", Locale.getDefault()).format(Date(diary.createdAt))
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 255, 255, 255)
                textSize = 40f
                textAlign = Paint.Align.CENTER
                typeface = handFont ?: Typeface.DEFAULT
            }
            val dateCy = H * options.dateYFrac.coerceIn(0.05f, 0.97f)
            val baseline = dateCy - (datePaint.fontMetrics.ascent + datePaint.fontMetrics.descent) / 2f
            canvas.drawText(date, W * options.dateXFrac.coerceIn(0.08f, 0.92f), baseline, datePaint)
        }

        return out
    }

    /**
     * 배경 — 밤하늘 이미지(`assets/share_card_bg.webp`) 센터크롭 + 별색 무드 틴트 + 하단 텍스트 스크림.
     * 이미지 로드 실패 시 남색 그라데이션 폴백(카드는 항상 만들어진다).
     */
    private fun drawBackground(context: Context, canvas: Canvas, accent: Int, stageCx: Float, stageCy: Float) {
        val bg = runCatching {
            context.assets.open("share_card_bg.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bg != null) {
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
        // 하단 텍스트 존 스크림 — 흰 글자 가독성(위 투명 → 아래 은은한 검정)
        canvas.drawRect(0f, H * 0.60f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, H * 0.60f, 0f, H.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(96, 0, 0, 8), Color.argb(130, 0, 0, 8)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        })
        // 중앙 무대에 배어나는 별색 무드
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(
                stageCx, stageCy, H * 0.36f,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 30), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
    }

    /**
     * 나라 지도 — 정사각 정적 지도를 원형 페더 마스크로 부드럽게 잘라 은은하게(반투명) 올린다.
     * 가장자리는 하늘로 자연스럽게 녹아들어 은하수를 가리지 않는다.
     */
    private fun drawRegionMap(canvas: Canvas, map: Bitmap, cx: Float, cy: Float, radius: Float, accent: Int) {
        // 지도 뒤 은은한 별색 후광 — 지도를 하늘에서 살짝 띄운다
        canvas.drawCircle(cx, cy, radius * 1.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 1.02f,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 46), Color.TRANSPARENT),
                floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
            )
        })

        val d = (radius * 2f).toInt()
        val layer = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        lc.drawBitmap(map, Rect(0, 0, map.width, map.height), Rect(0, 0, d, d), Paint(Paint.FILTER_BITMAP_FLAG))
        // 페더: 중심 불투명 → 가장자리 투명 (DST_IN 으로 알파만 남긴다)
        lc.drawRect(0f, 0f, d.toFloat(), d.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(
                radius, radius, radius,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.68f, 1f), Shader.TileMode.CLAMP
            )
        })
        // 반투명으로 올려 은하수가 비쳐 보이게
        canvas.drawBitmap(layer, cx - radius, cy - radius, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 150 })
        layer.recycle()

        // 지도 둘레 얇은 별색 링 — "떠 있는 원" 느낌의 마무리
        canvas.drawCircle(cx, cy, radius * 0.68f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = ColorUtils.setAlphaComponent(accent, 70)
        })
    }

    /** 주인공 별 — 작은 본체 + 은은한 후광 + 궤도 스파클(십자 광선 없음). 지도 정중앙 = 위치 마커. */
    private fun drawHeroStar(
        canvas: Canvas, diary: Diary, accent: Int, cx: Float, cy: Float, hasMap: Boolean, scale: Float = 1f,
    ) {
        val starSize = 150f * scale
        // 후광 — 지도가 있으면 더 조이고(위치 핀 느낌), 없으면 조금 넉넉히
        val haloR = if (hasMap) starSize * 1.5f else starSize * 2.2f
        canvas.drawCircle(cx, cy, haloR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, haloR,
                intArrayOf(ColorUtils.setAlphaComponent(accent, if (hasMap) 150 else 90), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })

        // 별 본체 — 마커와 같은 정의(StarStyle), 수정 결정(크리스탈) 패싯 채움
        val left = cx - starSize / 2f
        val top = cy - starSize / 2f
        val path = android.graphics.Path(StarStyle.starPath(diary.starType, starSize)).apply { offset(left, top) }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, glow)
        canvas.drawPath(path, glow)
        StarStyle.drawCrystalFill(canvas, diary.starType, diary.starColor, left, top, starSize)

        // 작은 궤도 스파클 2개 — 정적이지만 "반짝" 포인트
        drawSparkle(canvas, cx - starSize * 0.78f, cy - starSize * 0.52f, 9f, ColorUtils.setAlphaComponent(Color.WHITE, 210))
        drawSparkle(canvas, cx + starSize * 0.82f, cy + starSize * 0.18f, 7f, ColorUtils.setAlphaComponent(accent, 230))
    }

    /** 추가 별(내 다이어리에서 가져온 장식 별) — 은은한 후광 + 크리스탈 본체. */
    private fun drawExtraStar(canvas: Canvas, star: ExtraStar) {
        val color = StarStyle.colorOf(star.colorIndex).toArgb()
        val size = 150f * star.scale.coerceIn(0.2f, 2.5f)
        val cx = W * star.xFrac.coerceIn(0.04f, 0.96f)
        val cy = H * star.yFrac.coerceIn(0.03f, 0.97f)
        val haloR = size * 1.3f
        canvas.drawCircle(cx, cy, haloR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, haloR,
                intArrayOf(ColorUtils.setAlphaComponent(color, 70), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
        val left = cx - size / 2f
        val top = cy - size / 2f
        val path = android.graphics.Path(StarStyle.starPath(star.type, size)).apply { offset(left, top) }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        })
        StarStyle.drawCrystalFill(canvas, star.type, star.colorIndex, left, top, size)
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
        val baseline = cy - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(text, cx, baseline, textPaint)
    }
}
