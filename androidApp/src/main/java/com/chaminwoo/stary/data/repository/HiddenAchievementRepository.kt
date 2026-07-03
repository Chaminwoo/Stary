package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.feature.profile.HiddenClaim
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 히든 업적 선점(달성) 저장소 — `hiddenAchievements/{achievementId}`.
 *
 * "앱 전체에서 단 한 사람만" 을 **Firestore 트랜잭션**으로 보장한다.
 * 두 사용자가 동시에 시도해도 트랜잭션이 재시도되며 먼저 기록한 쪽이 주인이 된다.
 *
 * ⚠️ 완전한 도용 방지는 보안 규칙에서 "create only(존재하면 수정 불가)" 로 마무리해야 한다(PROJECT_NOTES 참고).
 */
class HiddenAchievementRepository {

    private val col = staryFirestore.collection(StaryConfig.Collections.HIDDEN_ACHIEVEMENTS)

    /**
     * 원자적 선점. 아직 주인이 없으면 [uid] 로 기록하고 true(=내가 달성).
     * 이미 주인이 있으면 그게 나면 true, 남이면 false.
     */
    suspend fun claim(id: String, uid: String, name: String): Boolean {
        if (id.isBlank() || uid.isBlank()) return false
        // 어드민(테스트) 계정은 선점을 서버에 기록하지 않는다 → 히든 슬롯을 차지하지 않아 실제 유저가 첫 달성자가 될 수 있게.
        if (StaryConfig.isAdminEmail(com.chaminwoo.stary.feature.auth.GoogleAuthHelper.currentUserEmail)) return false
        return try {
            val ref = col.document(id)
            staryFirestore.runTransaction { tx ->
                val owner = tx.get(ref).getString("achieverId")
                if (owner.isNullOrBlank()) {
                    tx.set(
                        ref,
                        mapOf(
                            "achieverId" to uid,
                            "achieverName" to name,
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

    /**
     * [uid] 가 주인으로 기록된 히든 업적 선점을 서버에서 모두 제거해 **슬롯을 되돌린다.**
     * 어드민(테스트) 계정이 과거에 실수로 선점한 히든 업적을 풀어 실제 유저가 첫 달성자가 될 수 있게 한다.
     * (어드민 로그인 시에만 호출 — 일반 유저의 정당한 선점을 지우지 않도록 호출부에서 가드한다.)
     */
    suspend fun releaseOwnedBy(uid: String) {
        if (uid.isBlank()) return
        try {
            val snap = col.whereEqualTo("achieverId", uid).get().await()
            for (doc in snap.documents) doc.reference.delete().await()
        } catch (_: Exception) {
            // 권한/네트워크 에러는 무시(다음 로그인에서 재시도).
        }
    }

    /** 모든 히든 업적의 달성 현황을 실시간 구독. (id → 달성 기록) */
    fun observe(): Flow<Map<String, HiddenClaim>> = callbackFlow {
        val reg = col.addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener // 권한/네트워크 에러로 죽지 않게 무시
            val map = snap?.documents?.associate { d ->
                d.id to HiddenClaim(
                    achieverId = d.getString("achieverId") ?: "",
                    achieverName = d.getString("achieverName") ?: "",
                    claimedAt = d.getLong("claimedAt") ?: 0L,
                )
            } ?: emptyMap()
            trySend(map)
        }
        awaitClose { reg.remove() }
    }
}
