package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.NotificationRepository
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 공용 [NotificationRepository] 의 Android/Firestore 구현. */
class FirebaseNotificationRepository : NotificationRepository {

    private val db = Firebase.firestore

    override fun observeNotifications(ownerId: String): Flow<List<AppNotification>> = callbackFlow {
        val listener = db.collection(StaryConfig.Collections.NOTIFICATIONS)
            .whereEqualTo("diaryOwnerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun observeUnreadCount(ownerId: String): Flow<Int> = callbackFlow {
        val listener = db.collection(StaryConfig.Collections.NOTIFICATIONS)
            .whereEqualTo("diaryOwnerId", ownerId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, _ -> trySend(snap?.size() ?: 0) }
        awaitClose { listener.remove() }
    }

    override suspend fun markAllRead(ownerId: String) {
        val unread = db.collection(StaryConfig.Collections.NOTIFICATIONS)
            .whereEqualTo("diaryOwnerId", ownerId)
            .whereEqualTo("isRead", false)
            .get().await()

        val batch = db.batch()
        unread.documents.forEach { batch.update(it.reference, "isRead", true) }
        batch.commit().await()
    }
}
