package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
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

    companion object {
        /**
         * 전체 다이어리 실시간 구독 상한.
         * 전 컬렉션을 무제한으로 받으면 다이어리가 늘수록 모든 기기가 전부 메모리에 들고
         * 변경마다 재읽기해 Firestore 비용·메모리·렌더 부담이 선형 증가한다.
         * 최신순 상한으로 잘라 가드(필요 시 추후 뷰포트/지오해시 쿼리로 대체).
         */
        private const val MAX_OBSERVED_DIARIES = 1000L
    }

    // 전체 다이어리 실시간 조회
    override fun observeAllDiaries(): Flow<List<Diary>> = callbackFlow {
        // 앱 시작 시 지도는 로그인 전(auth==null)에 미리 렌더된다. 그때 첫 구독이 시작되면
        // 보안 규칙(request.auth!=null)에 막혀 PERMISSION_DENIED 로 Firestore 리스너가 죽고,
        // 로그인 후에도 스스로 복구되지 않아 지도에 다이어리가 안 뜬다.
        // → auth 상태가 바뀔 때마다(로그인/익명 완료) 리스너를 다시 건다.
        var registration: ListenerRegistration? = null

        fun subscribe() {
            registration?.remove()
            registration = diaries
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_OBSERVED_DIARIES)
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
        }

        subscribe()
        val auth = FirebaseAuth.getInstance()
        // addAuthStateListener 는 등록 즉시 1회 + 이후 상태 변경마다 호출된다.
        val authListener = FirebaseAuth.AuthStateListener { subscribe() }
        auth.addAuthStateListener(authListener)

        awaitClose {
            registration?.remove()
            auth.removeAuthStateListener(authListener)
        }
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
        // userId 동등 필터만 서버에서 수행하고 정렬은 클라이언트에서 처리한다.
        // (userId + createdAt 복합 인덱스를 새 DB 에 따로 만들 필요 없게 — 내 글은 소량이라 부담 없음)
        val listener = diaries
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Diary::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()
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
