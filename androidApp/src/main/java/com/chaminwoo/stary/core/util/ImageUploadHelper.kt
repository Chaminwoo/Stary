package com.chaminwoo.stary.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
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
        // Storage 보안 규칙(request.auth != null)을 통과하려면 인증 세션이 필요하다.
        // 익명 로그인이 Application 에서 fire-and-forget 로 시작되므로 업로드 시점엔
        // 아직 세션이 없을 수 있다 → 여기서 보장한다.
        try {
            ensureAuthenticated()
        } catch (e: Exception) {
            Log.e(TAG, "인증 세션 확보 실패", e)
            return Result(null, "로그인 세션을 만들 수 없어요(콘솔에서 익명 인증 활성화 필요): ${e.localizedMessage}")
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
            Log.e(TAG, "인증 세션 확보 실패", e)
            return Result(null, "로그인 세션을 만들 수 없어요(콘솔에서 익명 인증 활성화 필요): ${e.localizedMessage}")
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

    /** Firebase Auth 세션이 없으면 익명 로그인으로 만든다(이미 있으면 즉시 반환). */
    suspend fun ensureAuthenticated() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}
