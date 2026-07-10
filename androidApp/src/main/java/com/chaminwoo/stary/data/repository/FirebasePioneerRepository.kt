package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.PioneerQuest
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 개척 선점 기록 — pioneerClaims/{countryCode}. */
data class PioneerClaim(
    val userId: String = "",
    val userName: String = "",
    val weekIndex: Int = 0,
    val claimedAt: Long = 0L,
)

/**
 * 주간 개척 퀘스트 선점 저장소(체크리스트 32) — `pioneerClaims/{countryCode}`.
 * "그 나라에 처음으로 다이어리를 올린 단 한 사람" 을 히든 업적과 같은 트랜잭션 선점으로 보장한다.
 * (보안 규칙에서 create-only 로 서버 강제 — 이미 있으면 실패.)
 */
class FirebasePioneerRepository {

    private val col = staryFirestore.collection(PioneerQuest.COLLECTION)

    /** 모든 나라의 개척 현황 실시간 구독. (countryCode → 기록) */
    fun observeClaims(): Flow<Map<String, PioneerClaim>> = callbackFlow {
        val reg = col.addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener // 권한/네트워크 에러로 죽지 않게 무시
            val map = snap?.documents?.associate { d ->
                d.id to PioneerClaim(
                    userId = d.getString("userId") ?: "",
                    userName = d.getString("userName") ?: "",
                    weekIndex = (d.getLong("weekIndex") ?: 0L).toInt(),
                    claimedAt = d.getLong("claimedAt") ?: 0L,
                )
            } ?: emptyMap()
            trySend(map)
        }
        awaitClose { reg.remove() }
    }

    /**
     * 원자적 선점 — 아직 개척자가 없으면 [uid] 로 기록하고 true(=내가 개척자).
     * 이미 있으면 그게 나면 true, 남이면 false. 어드민 계정은 기록하지 않는다(히든 업적과 동일 정책).
     */
    suspend fun claim(countryCode: String, uid: String, name: String): Boolean {
        if (countryCode.isBlank() || uid.isBlank()) return false
        if (StaryConfig.isAdminEmail(com.chaminwoo.stary.feature.auth.GoogleAuthHelper.currentUserEmail)) return false
        return try {
            val ref = col.document(countryCode.uppercase())
            staryFirestore.runTransaction { tx ->
                val owner = tx.get(ref).getString("userId")
                if (owner.isNullOrBlank()) {
                    tx.set(
                        ref,
                        mapOf(
                            "userId" to uid,
                            "userName" to name,
                            "weekIndex" to PioneerQuest.weekIndex(System.currentTimeMillis()),
                            "claimedAt" to System.currentTimeMillis(),
                        )
                    )
                    true
                } else {
                    owner == uid
                }
            }.await()
        } catch (_: Exception) {
            false
        }
    }
}
