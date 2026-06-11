package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import android.net.Uri
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = staryFirestore
    private val storage = Firebase.storage

    suspend fun getProfileImageUrl(userId: String): String? =
        db.collection("users").document(userId).get().await()
            .getString("profileImageUrl")

    suspend fun uploadProfileImage(userId: String, uri: Uri): String {
        // 이전 이미지 삭제 (항상 같은 경로 사용)
        try {
            storage.reference.child("profile_images/$userId.jpg").delete().await()
        } catch (_: Exception) {}

        val ref = storage.reference.child("profile_images/$userId.jpg")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        db.collection("users").document(userId)
            .set(mapOf("profileImageUrl" to downloadUrl), SetOptions.merge())
            .await()

        return downloadUrl
    }
}
