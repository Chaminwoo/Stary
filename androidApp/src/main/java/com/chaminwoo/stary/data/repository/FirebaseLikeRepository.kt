package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.Like
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.LikeRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 공용 [LikeRepository] 의 Android/Firestore 구현. */
class FirebaseLikeRepository : LikeRepository {

    private val db = Firebase.firestore

    override fun observeIsLiked(diaryId: String, userId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank()) { trySend(false); awaitClose {}; return@callbackFlow }
        val listener = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
            .collection(StaryConfig.Collections.LIKES).document(userId)
            .addSnapshotListener { snap, _ -> trySend(snap?.exists() == true) }
        awaitClose { listener.remove() }
    }

    override fun observeLikeCount(diaryId: String): Flow<Int> = callbackFlow {
        val listener = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.getLong("likeCount")?.toInt() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLike(
        diaryId: String,
        diaryTitle: String,
        diaryOwnerId: String,
        userId: String,
        userName: String
    ) {
        if (userId.isBlank() || diaryId.isBlank()) return
        val now = System.currentTimeMillis()
        val diaryRef = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
        val likeRef = diaryRef.collection(StaryConfig.Collections.LIKES).document(userId)

        db.runTransaction { tx ->
            val likeSnap = tx.get(likeRef)
            if (likeSnap.exists()) {
                tx.delete(likeRef)
                tx.update(diaryRef, "likeCount", FieldValue.increment(-1))
            } else {
                tx.set(likeRef, Like(userId = userId, userName = userName, createdAt = now))
                tx.update(diaryRef, "likeCount", FieldValue.increment(1))
                if (diaryOwnerId != userId) {
                    val notifRef = db.collection(StaryConfig.Collections.NOTIFICATIONS).document()
                    tx.set(
                        notifRef,
                        AppNotification(
                            id = notifRef.id,
                            type = NotificationType.LIKE.name,
                            diaryId = diaryId,
                            diaryTitle = diaryTitle,
                            diaryOwnerId = diaryOwnerId,
                            actorId = userId,
                            actorName = userName,
                            createdAt = now
                        )
                    )
                }
            }
        }.await()
    }
}
