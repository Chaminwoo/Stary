package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.core.model.BlockedUser
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

    /**
     * 차단 목록 화면용 상세 목록(최근 차단 순). id 만 필요한 곳은 [observeBlockedIds] 를 쓴다.
     * 이름/사진은 차단 시점 스냅샷이라 상대 프로필 문서를 다시 읽지 않는다.
     */
    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = blockedCol(userId).addSnapshotListener { snapshot, error ->
            if (error != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snapshot?.documents?.map { doc ->
                BlockedUser(
                    userId = doc.id,
                    userName = doc.getString("userName") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                )
            }?.sortedByDescending { it.createdAt } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** 사용자 차단. 이름/사진은 차단 목록 화면에서 바로 보여주기 위한 스냅샷. */
    suspend fun block(userId: String, targetId: String, targetName: String, targetPhotoUrl: String = "") {
        if (userId.isBlank() || targetId.isBlank() || userId == targetId) return
        try {
            blockedCol(userId).document(targetId).set(
                mapOf(
                    "userName" to targetName,
                    "photoUrl" to targetPhotoUrl,
                    "createdAt" to System.currentTimeMillis(),
                )
            ).await()
        } catch (_: Exception) {}
    }

    suspend fun unblock(userId: String, targetId: String) {
        if (userId.isBlank() || targetId.isBlank()) return
        try {
            blockedCol(userId).document(targetId).delete().await()
        } catch (_: Exception) {}
    }

    /**
     * 신고 접수. [type] = "diary" | "comment" | "user".
     * [extra] 는 관리자가 Firebase Console 에서 바로 검토할 수 있도록 넣는 사람이 읽을 스냅샷
     * (예: targetTitle, targetContent, targetOwnerName, targetImageUrl, reporterName). null 값은 제외.
     *
     * status 흐름: "open"(신고됨) → 관리자가 Console 에서 아래로 변경하면 Function 이 조치:
     *   "action_delete" = 대상 다이어리 삭제, "action_ban" = 대상 계정 7일 삭제 예약, "dismissed" = 기각.
     */
    suspend fun report(
        reporterId: String,
        type: String,
        targetId: String,
        targetOwnerId: String,
        reason: String,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        if (reporterId.isBlank() || targetId.isBlank()) return
        try {
            val doc = mutableMapOf<String, Any>(
                "reporterId" to reporterId,
                "type" to type,
                "targetId" to targetId,
                "targetOwnerId" to targetOwnerId,
                "reason" to reason,
                "createdAt" to System.currentTimeMillis(),
                "status" to "open",
            )
            for ((k, v) in extra) if (v != null) doc[k] = v
            reports.add(doc).await()
        } catch (_: Exception) {}
    }
}
