package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 공용 [DiaryRepository] 인터페이스(commonMain)의 Android/Firestore 구현.
 * iOS 에서는 동일 인터페이스를 Firebase iOS SDK 등으로 별도 구현한다.
 */
class FirebaseDiaryRepository : DiaryRepository {

    private val diaries = staryFirestore.collection(StaryConfig.Collections.DIARIES)

    // 전체 다이어리 실시간 조회
    override fun observeAllDiaries(): Flow<List<Diary>> = callbackFlow {
        val listener = diaries
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot: QuerySnapshot?, error: Exception? ->
                if (error != null) return@addSnapshotListener
                val currentUid = GoogleAuthHelper.currentUserId
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Diary::class.java)?.copy(id = doc.id)
                }?.filter { diary ->
                    // private 다이어리는 본인만 볼 수 있음
                    diary.visibilityType != "private" || diary.userId == currentUid
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // 다이어리 저장
    override suspend fun saveDiary(diary: Diary): Boolean {
        return try {
            // 먼저 document 참조 생성해서 id 확보
            val docRef = diaries.document()
            val withMeta = diary.copy(
                id = docRef.id,
                createdAt = if (diary.createdAt == 0L) System.currentTimeMillis() else diary.createdAt
            )
            docRef.set(withMeta).await()
            // fire-and-forget: 친구 알림 생성이 저장 완료 이벤트(토스트/화면전환)를 막지 않게 분리
            CoroutineScope(Dispatchers.IO).launch { notifyFriendsOfPost(withMeta) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 업로드 성공 시 친구들에게 FRIEND_POST 인앱 알림 생성 (실패해도 저장엔 영향 없음). */
    private suspend fun notifyFriendsOfPost(diary: Diary) {
        if (diary.userId.isBlank()) return
        try {
            val friendIds = staryFirestore
                .collection(StaryConfig.Collections.USERS)
                .document(diary.userId)
                .collection(StaryConfig.Collections.FRIENDS)
                .get().await()
                .documents.map { it.id }
            FirebaseNotificationRepository().notifyFriendPost(
                actorId = diary.userId,
                actorName = if (diary.isAnonymous) "익명" else diary.userName,
                diaryId = diary.id,
                diaryTitle = diary.title,
                friendIds = friendIds
            )
        } catch (_: Exception) {}
    }

    // 다이어리 삭제
    override suspend fun deleteDiary(diaryId: String): Boolean {
        return try {
            diaries.document(diaryId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getDiaryById(diaryId: String): Diary? {
        return try {
            val doc = diaries.document(diaryId).get().await()
            doc.toObject(Diary::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    override fun observeMyDiaries(userId: String): Flow<List<Diary>> = callbackFlow {
        val listener = diaries
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Diary::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun incrementViewCount(diaryId: String) {
        try {
            diaries.document(diaryId).update("viewCount", FieldValue.increment(1)).await()
        } catch (_: Exception) {}
    }

    override suspend fun prefetchDiaries(diaryIds: List<String>) {
        diaryIds.forEach { id ->
            if (DiaryCache.get(id) != null) return@forEach
            try {
                val doc = diaries.document(id).get().await()
                doc.toObject(Diary::class.java)?.copy(id = doc.id)?.let { DiaryCache.put(it) }
            } catch (_: Exception) {}
        }
    }

    override suspend fun updateDiary(diary: Diary): Boolean {
        return try {
            diaries.document(diary.id).update(
                mapOf("title" to diary.title, "content" to diary.content)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
