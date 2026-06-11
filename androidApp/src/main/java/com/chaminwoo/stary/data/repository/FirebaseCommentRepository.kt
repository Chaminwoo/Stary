package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.CommentRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 공용 [CommentRepository] 의 Android/Firestore 구현. */
class FirebaseCommentRepository : CommentRepository {

    private val db = Firebase.firestore

    override fun observeComments(diaryId: String): Flow<List<Comment>> = callbackFlow {
        val listener = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
            .collection(StaryConfig.Collections.COMMENTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addComment(
        diaryId: String,
        diaryTitle: String,
        diaryOwnerId: String,
        userId: String,
        userName: String,
        content: String
    ) {
        val now = System.currentTimeMillis()
        val diaryRef = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
        val commentRef = diaryRef.collection(StaryConfig.Collections.COMMENTS).document()
        val batch = db.batch()

        batch.set(
            commentRef,
            Comment(
                id = commentRef.id,
                diaryId = diaryId,
                userId = userId,
                userName = userName,
                content = content,
                createdAt = now
            )
        )
        batch.update(diaryRef, "commentCount", FieldValue.increment(1))

        if (diaryOwnerId != userId) {
            val notifRef = db.collection(StaryConfig.Collections.NOTIFICATIONS).document()
            batch.set(
                notifRef,
                AppNotification(
                    id = notifRef.id,
                    type = NotificationType.COMMENT.name,
                    diaryId = diaryId,
                    diaryTitle = diaryTitle,
                    diaryOwnerId = diaryOwnerId,
                    actorId = userId,
                    actorName = userName,
                    content = content,
                    createdAt = now
                )
            )
        }

        batch.commit().await()
    }

    override suspend fun deleteComment(diaryId: String, commentId: String) {
        val diaryRef = db.collection(StaryConfig.Collections.DIARIES).document(diaryId)
        val commentRef = diaryRef.collection(StaryConfig.Collections.COMMENTS).document(commentId)
        val batch = db.batch()
        batch.delete(commentRef)
        batch.update(diaryRef, "commentCount", FieldValue.increment(-1))
        batch.commit().await()
    }
}
