package com.chaminwoo.stary.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object ImageUploadHelper {

    suspend fun uploadImage(context: Context, imageUri: Uri): String? {
        return try {
            val storageRef = Firebase.storage.reference
            val imageRef = storageRef.child("diary_images/${UUID.randomUUID()}.jpg")
            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e("ImageUploadHelper", "이미지 업로드 실패", e)
            null
        }
    }
}
