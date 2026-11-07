package com.chaminwoo.stary.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chaminwoo.stary.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object ImageUploadHelper {

    private const val TAG = "ImageUploadHelper"

    /**
     * Storage 업로드 결과. 성공 시 [url], 실패 시 사람이 읽을 수 있는 [error] 를 담는다.
     * (기존엔 실패를 null 로만 돌려줘 원인이 토스트에 묻혔다 — 실제 원인을 화면에 노출하기 위함)
     */
    data class Result(val url: String?, val error: String? = null) {
        val isSuccess get() = url != null
    }

    suspend fun uploadImageResult(context: Context, imageUri: Uri): Result {
        // 업로드는 로그인한 사용자만 가능하다.
        try {
            ensureAuthenticated()
        } catch (e: Exception) {
            Log.w(TAG, "인증 세션 없음")
            return Result(null, context.getString(R.string.common_login_required))
        }

        return try {
            val storageRef = Firebase.storage.reference
            val imageRef = storageRef.child("diary_images/${UUID.randomUUID()}.jpg")
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            Result(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "이미지 업로드 실패", e)
            Result(null, e.localizedMessage ?: e.toString())
        }
    }

    /** 호환용: 성공 시 URL, 실패 시 null. */
    suspend fun uploadImage(context: Context, imageUri: Uri): String? =
        uploadImageResult(context, imageUri).url

    /**
     * 3초 이내 짧은 영상 업로드(diary_videos/). 길이 검증은 호출부(UploadScreen)가 하고,
     * 여기서는 인증 세션 확보 후 원본 파일을 그대로 올린다. contentType 을 명시해 Storage 규칙(video 타입)을 통과시킨다.
     */
    suspend fun uploadVideoResult(context: Context, videoUri: Uri): Result {
        try {
            ensureAuthenticated()
        } catch (e: Exception) {
            Log.w(TAG, "인증 세션 없음")
            return Result(null, context.getString(R.string.common_login_required))
        }
        return try {
            val contentType = context.contentResolver.getType(videoUri) ?: "video/mp4"
            val ext = when {
                contentType.contains("quicktime") || contentType.contains("mov") -> "mov"
                else -> "mp4"
            }
            val ref = Firebase.storage.reference.child("diary_videos/${UUID.randomUUID()}.$ext")
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .build()
            ref.putFile(videoUri, metadata).await()
            Result(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Log.e(TAG, "영상 업로드 실패", e)
            Result(null, e.localizedMessage ?: e.toString())
        }
    }

    /**
     * 부메랑(3초 움짤) GIF 업로드. contentType=image/gif 라 diary_images 규칙(이미지 타입, 10MB 미만)을
     * 그대로 통과한다(규칙 재배포 불필요). URL 은 diary.videoUrl 에 저장해 기존 스키마 유지.
     */
    suspend fun uploadGifResult(context: Context, gifFile: java.io.File): Result {
        try {
            ensureAuthenticated()
        } catch (e: Exception) {
            Log.w(TAG, "인증 세션 없음")
            return Result(null, context.getString(R.string.common_login_required))
        }
        return try {
            val ref = Firebase.storage.reference.child("diary_images/${UUID.randomUUID()}.gif")
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/gif")
                .build()
            ref.putFile(Uri.fromFile(gifFile), metadata).await()
            Result(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Log.e(TAG, "GIF 업로드 실패", e)
            Result(null, e.localizedMessage ?: e.toString())
        }
    }

    /** 로그인(비익명) 세션이 아니면 예외. */
    fun ensureAuthenticated() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) throw IllegalStateException("not signed in")
    }
}
