package com.chaminwoo.stary.data.repository

import android.net.Uri
import android.util.Log
import com.chaminwoo.stary.core.util.ImageUploadHelper
import com.chaminwoo.stary.data.staryFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = staryFirestore
    private val storage = Firebase.storage

    /**
     * 프로필 사진 교체(2026-10-19) — **새 사진 업로드 → users/{uid}.profileImageUrl 연결 → 예전 사진(기존 URL)을 Storage 에서 삭제**.
     * - 파일명은 매번 새로 `profile_images/{uid}_{시각}.jpg`(storage.rules 가 본인 것만 허용). URL 이 바뀌므로 어디서도 옛 사진이 캐시로 남지 않는다.
     * - 순서가 중요하다: 먼저 지우고 올리다 업로드가 실패하면 프로필 URL 이 **없는 파일**을 가리킨다. 그래서 새 사진을 연결한 **뒤에** 지운다.
     * - 예전 URL 은 `users/{uid}` 를 **서버에서** 읽어 얻는다. 우리 Storage 의 내 프로필 파일일 때만 지운다(구글 사진 URL 등은 그대로).
     *   삭제 실패(이미 없음·네트워크)는 무시 — 새 사진 연결은 이미 끝났다.
     * iOS `ImageUploader.uploadProfile` 과 같은 순서.
     */
    suspend fun uploadProfileImage(userId: String, uri: Uri): String {
        // Storage 보안 규칙(request.auth != null)을 통과하려면 인증 세션 필요.
        ImageUploadHelper.ensureAuthenticated()

        val userRef = db.collection("users").document(userId)
        val oldUrl = try {
            userRef.get(Source.SERVER).await().getString("profileImageUrl")
        } catch (e: Exception) {
            null // 못 읽으면 지우지 않는다(잘못 지우는 것보다 남는 게 낫다)
        }

        val ref = storage.reference.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        userRef.set(mapOf("profileImageUrl" to downloadUrl), SetOptions.merge()).await()

        if (!oldUrl.isNullOrBlank() && oldUrl != downloadUrl && isMyProfileFile(oldUrl, userId)) {
            try {
                storage.getReferenceFromUrl(oldUrl).delete().await()
            } catch (e: Exception) {
                Log.w("UserRepository", "예전 프로필 사진 삭제 실패(무시): ${e.localizedMessage}")
            }
        }
        return downloadUrl
    }

    /** [url] 이 우리 Storage 의 **내** 프로필 파일(profile_images/{uid}.jpg 또는 {uid}_{시각}.jpg)인가. */
    private fun isMyProfileFile(url: String, userId: String): Boolean {
        val path = try {
            storage.getReferenceFromUrl(url).path.trimStart('/')
        } catch (e: Exception) {
            return false // 구글 사진 등 Storage URL 이 아님
        }
        return path == "profile_images/$userId.jpg" ||
            (path.startsWith("profile_images/${userId}_") && path.endsWith(".jpg"))
    }
}
