package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ViewedDiaryRepository
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * users/{uid}/viewedDiaries/{diaryId} — 열람 기록.
 *  - `viewedAt`  : 마지막으로 상세에 들어온 시각(잠겨 있어도 기록) — 미조회 필터·열람 업적용.
 *  - `unlockedAt`: 본문을 **실제로 연(해금한)** 시각 — 100m 접근 또는 광고. 별 도감·영구 해금의 서버 사본
 *    (DiaryUnlockStore 가 쓰고 읽는다). 두 필드를 따로 쓰므로 쓰기는 항상 merge.
 */
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
            // merge — 덮어쓰면 unlockedAt(해금 기록)이 지워진다.
            col(userId).document(diaryId)
                .set(mapOf("viewedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        } catch (_: Exception) {}
    }

    /** 해금 기록을 서버에 남긴다(여러 개면 배치). 실패는 무시 — 다음 동기화 때 다시 올라간다. */
    suspend fun markUnlocked(userId: String, unlocks: Map<String, Long>) {
        if (unlocks.isEmpty()) return
        try {
            unlocks.entries.chunked(400).forEach { chunk ->
                val batch = staryFirestore.batch()
                chunk.forEach { (id, at) ->
                    batch.set(col(userId).document(id), mapOf("unlockedAt" to at), SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (_: Exception) {}
    }

    /**
     * 서버에 남은 "실제로 연" 기록 — id → 연 시각. 실패하면 null.
     *  - `unlockedAt` 이 있으면 그 시각.
     *  - 없더라도 `viewedAt` 이 [StaryConfig.DIARY_LOCK_SINCE_MS] 이전이면(잠금이 없던 시절 = 본문까지 열람) 그 시각.
     * 두 번째 경우는 [OpenedRecords.legacy] 에 따로 담아, 호출자가 `unlockedAt` 으로 굳혀 둘 수 있게 한다
     * (viewedAt 은 다시 열 때마다 갱신돼 잠금 이후 시각으로 밀려나면 옛 열람이라는 증거가 사라진다).
     */
    suspend fun fetchOpened(userId: String): OpenedRecords? = try {
        val docs = col(userId).get().await().documents
        val unlocked = HashMap<String, Long>()
        val legacy = HashMap<String, Long>()
        docs.forEach { d ->
            val unlockedAt = d.getLong("unlockedAt")
            val viewedAt = d.getLong("viewedAt")
            when {
                unlockedAt != null -> unlocked[d.id] = unlockedAt
                viewedAt != null && viewedAt < StaryConfig.DIARY_LOCK_SINCE_MS -> legacy[d.id] = viewedAt
            }
        }
        OpenedRecords(unlocked, legacy)
    } catch (_: Exception) {
        null
    }

    data class OpenedRecords(val unlocked: Map<String, Long>, val legacy: Map<String, Long>)
}
