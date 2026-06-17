package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
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

    private val db = staryFirestore

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
        // ⚠️ Firestore + Kotlin Boolean 함정: 모델 필드 `isRead`(Boolean) 는 getter `isRead()` 의
        //    "is" 접두가 떨어져 **Firestore 문서에는 `read` 필드로 저장**된다(toObject 는 같은 규칙으로
        //    역매핑되어 정상). 따라서 raw 문자열 쿼리는 반드시 "read" 를 써야 매칭된다("isRead" 로 쿼리하면 0건).
        val listener = db.collection(StaryConfig.Collections.NOTIFICATIONS)
            .whereEqualTo("diaryOwnerId", ownerId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ -> trySend(snap?.size() ?: 0) }
        awaitClose { listener.remove() }
    }

    /**
     * 친구 새 글 인앱 알림 — 업로더 클라이언트가 친구마다 알림 문서를 생성한다.
     * (푸시(FCM)는 서버(Cloud Functions) 필요 — 체크리스트 7/8 잔여분)
     */
    suspend fun notifyFriendPost(
        actorId: String,
        actorName: String,
        diaryId: String,
        diaryTitle: String,
        friendIds: List<String>,
    ) {
        if (friendIds.isEmpty()) return
        try {
            val batch = db.batch()
            val now = System.currentTimeMillis()
            friendIds.forEach { friendId ->
                val doc = db.collection(StaryConfig.Collections.NOTIFICATIONS).document()
                batch.set(
                    doc,
                    AppNotification(
                        id = doc.id,
                        type = com.chaminwoo.stary.core.model.NotificationType.FRIEND_POST.name,
                        diaryId = diaryId,
                        diaryTitle = diaryTitle,
                        diaryOwnerId = friendId, // 알림 수신자
                        actorId = actorId,
                        actorName = actorName,
                        createdAt = now
                    )
                )
            }
            batch.commit().await()
        } catch (_: Exception) {}
    }

    override suspend fun markAllRead(ownerId: String) {
        // 저장 필드명은 `read`(위 observeUnreadCount 주석 참고). 쿼리/업데이트 모두 "read" 사용.
        val unread = db.collection(StaryConfig.Collections.NOTIFICATIONS)
            .whereEqualTo("diaryOwnerId", ownerId)
            .whereEqualTo("read", false)
            .get().await()

        val batch = db.batch()
        unread.documents.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }
}
