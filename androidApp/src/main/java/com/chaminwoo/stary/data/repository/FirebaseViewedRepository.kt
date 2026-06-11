package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ViewedDiaryRepository
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** users/{uid}/viewedDiaries/{diaryId} — 미조회 필터용 열람 기록. */
class FirebaseViewedRepository : ViewedDiaryRepository {

    private fun col(userId: String) =
        staryFirestore.collection(StaryConfig.Collections.USERS)
            .document(userId).collection(StaryConfig.Collections.VIEWED_DIARIES)

    override fun observeViewedIds(userId: String): Flow<Set<String>> = callbackFlow {
        val listener = col(userId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
            trySend(snapshot?.documents?.map { it.id }?.toSet() ?: emptySet())
        }
        awaitClose { listener.remove() }
    }

    override suspend fun markViewed(userId: String, diaryId: String) {
        try {
            col(userId).document(diaryId)
                .set(mapOf("viewedAt" to System.currentTimeMillis())).await()
        } catch (_: Exception) {}
    }
}
