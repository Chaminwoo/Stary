package com.chaminwoo.stary.feature.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 다이어리 공유 영상(스토리용 세로 8초) — 만들기 / SNS 로 보내기 / 갤러리에 저장.
 * 예전 공유 카드(이미지 + 편집 화면)를 대체한다(2026-10-11). 영상 자체는 [ShareVideoRenderer] 가 그린다.
 *
 * 링크(`StaryConfig.shareLink`)를 열면 웹 랜딩이 앱 설치자는 stary://diary/{id} 로, 미설치자는 스토어로 보낸다.
 */
object ShareVideoHelper {
    private const val INSTAGRAM_PKG = "com.instagram.android"
    private const val MIME = "video/mp4"

    /**
     * 영상을 만들어 캐시 파일로 돌려준다(실패·취소면 null). 수 초 걸리므로 [onProgress](0..1, 작업 스레드에서 호출)로 진행을 알린다.
     * 호출한 코루틴이 취소되면 다음 프레임에서 멈춘다.
     */
    suspend fun generate(context: Context, diary: Diary, onProgress: (Float) -> Unit): File? {
        val sky = mySkyDiaries()
        val job = coroutineContext
        return withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "share_video_${diary.id}.mp4")
            // ⚠️ render 는 한 스레드에서 끝까지 돈다(EGL) — 이 블록 안에서 suspend 하지 않는다.
            val ok = ShareVideoRenderer(context, diary, sky).render(out, onProgress) { !job.isActive }
            if (ok) out else null
        }
    }

    /** 지구 위에 같이 띄울 내 다른 별들 — 못 읽으면(비로그인·느린 망) 주인공 별만 띄운다. */
    private suspend fun mySkyDiaries(): List<Diary> {
        val uid = GoogleAuthHelper.currentUserId ?: return emptyList()
        return runCatching {
            withTimeoutOrNull(2_500) { withContext(Dispatchers.IO) { FirebaseDiaryRepository().observeMyDiaries(uid).first() } }
        }.getOrNull().orEmpty()
    }

    private fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** SNS 공유 — 인스타그램이 있으면 스토리로 바로, 없으면 일반 공유 시트. */
    fun shareToSns(context: Context, diary: Diary, file: File) {
        val link = StaryConfig.shareLink(diary.id)
        runCatching {
            val uri = uriOf(context, file)
            if (!launchInstagramStory(context, uri, link)) launchChooser(context, uri, link)
        }.onFailure { com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.share_failed)) }
    }

    private fun launchChooser(context: Context, uri: Uri, link: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_diary_text, link))
            clipData = ClipData.newRawUri(null, uri) // 공유 시트 미리보기가 영상을 읽을 수 있게
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_diary)))
    }

    /**
     * 인스타 스토리 인텐트(영상 배경). 인스타 미설치면 false(→ 공유 시트 폴백).
     *
     * ⚠️ 링크스티커(content_url)는 Meta 앱 ID(`source_application`) 등록이 있어야만 자동으로 붙는다 — 없으면 인스타가 조용히 무시한다.
     * 그래서 항상 링크를 클립보드에 복사해 두고, 스토리 편집기의 '링크 스티커'로 붙여넣으라고 시스템 토스트로 안내한다
     * (앱이 배경으로 가도 보이도록 StaryToast 가 아니다).
     */
    private fun launchInstagramStory(context: Context, uri: Uri, link: String): Boolean {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, MIME)
            if (BuildConfig.INSTAGRAM_APP_ID.isNotBlank()) {
                putExtra("source_application", BuildConfig.INSTAGRAM_APP_ID)
                putExtra("content_url", link)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.grantUriPermission(INSTAGRAM_PKG, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("stary-link", link))
            context.startActivity(intent)
            Toast.makeText(context, context.getString(R.string.share_story_link_copied), Toast.LENGTH_LONG).show()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Android 9 이하는 갤러리에 쓰려면 저장소 권한이 필요하다(10 이상은 MediaStore 로 권한 없이 저장). */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** 갤러리(동영상 > Stary)에 저장. 성공 여부를 돌려준다. */
    suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "stary_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, MIME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Stary")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, values) ?: return@withContext false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            true
        } catch (_: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } } // 쓰다 만 빈 항목이 갤러리에 남지 않게
            false
        }
    }
}
