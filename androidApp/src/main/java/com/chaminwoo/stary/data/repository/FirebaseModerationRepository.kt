package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 차단/신고 — 안전 기능(Play 정책 대응).
 *  - users/{uid}/blocked/{blockedUid} : 내가 차단한 사용자(문서 id = 상대 uid)
 *  - reports/{id}                     : 콘텐츠/사용자 신고
 */
class FirebaseModerationRepository {

    private val db = staryFirestore
    private val users = db.collection(StaryConfig.Collections.USERS)
    private val reports = db.collection(StaryConfig.Collections.REPORTS)
    private fun blockedCol(uid: String) =
        users.document(uid).collection(StaryConfig.Collections.BLOCKED)

    /** 내가 차단한 사용자 id 집합 실시간 관찰 — 차단한 사람의 다이어리/댓글을 숨기는 데 쓴다. */
    fun observeBlockedIds(userId: String): Flow<Set<String>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val listener = blockedCol(userId).addSnapshotListener { snapshot, error ->
            if (error != null) { trySend(emptySet()); return@addSnapshotListener }
            trySend(snapshot?.documents?.map { it.id }?.toSet() ?: emptySet())
        }
        awaitClose { listener.remove() }
    }

    suspend fun isBlocked(userId: String, targetId: String): Boolean {
        if (userId.isBlank() || targetId.isBlank()) return false
        return try {
            blockedCol(userId).document(targetId).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun block(userId: String, targetId: String, targetName: String) {
        if (userId.isBlank() || targetId.isBlank() || userId == targetId) return
        try {
            blockedCol(userId).document(targetId).set(
                mapOf("userName" to targetName, "createdAt" to System.currentTimeMillis())
            ).await()
        } catch (_: Exception) {}
    }

    suspend fun unblock(userId: String, targetId: String) {
        if (userId.isBlank() || targetId.isBlank()) return
        try {
            blockedCol(userId).document(targetId).delete().await()
        } catch (_: Exception) {}
    }

    /** 신고 접수. [type] = "diary" | "comment" | "user". */
    suspend fun report(
        reporterId: String,
        type: String,
        targetId: String,
        targetOwnerId: String,
        reason: String,
    ) {
        if (reporterId.isBlank() || targetId.isBlank()) return
        try {
            reports.add(
                mapOf(
                    "reporterId" to reporterId,
                    "type" to type,
                    "targetId" to targetId,
                    "targetOwnerId" to targetOwnerId,
                    "reason" to reason,
                    "createdAt" to System.currentTimeMillis(),
                    "status" to "open",
                )
            ).await()
        } catch (_: Exception) {}
    }
}
