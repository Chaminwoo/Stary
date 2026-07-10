package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 친구 초대 보상(체크리스트 31).
 *
 * Firestore: invites/{redeemerUid} = { inviterId, redeemerId, createdAt }
 *  - 문서 id = 초대받은 사람 uid → 한 계정은 평생 1회만 리딤(중복 자동 방지).
 *  - 리딤 조건: 본인 코드 아님 + 가입 [StaryConfig.INVITE_REDEEM_WINDOW_MS] 이내(신규 유입 보상).
 *  - 업적 판정: 초대한 쪽 = inviterId 문서 수(별의 등대/별무리의 길잡이), 받은 쪽 = 내 문서 존재(별의 인연).
 */
class FirebaseInviteRepository {

    private val db = staryFirestore
    private val invites = db.collection(StaryConfig.Collections.INVITES)

    enum class RedeemResult { SUCCESS, ALREADY, SELF, TOO_OLD, FAILED }

    /** 내가 초대해 가입(리딤)한 친구 수 — 업적 통계용. */
    fun observeInvitedCount(uid: String): Flow<Int> = callbackFlow {
        val listener = invites.whereEqualTo("inviterId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { listener.remove() }
    }

    /** 내가 초대를 리딤했는지 — 업적 통계용. */
    fun observeRedeemed(uid: String): Flow<Boolean> = callbackFlow {
        val listener = invites.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.exists() == true)
            }
        awaitClose { listener.remove() }
    }

    /** 초대 리딤 — 성공 시 양쪽 업적 통계에 즉시 반영된다(위 observe 들이 실시간 갱신). */
    suspend fun redeem(inviterId: String, redeemerId: String): RedeemResult {
        if (inviterId.isBlank() || redeemerId.isBlank()) return RedeemResult.FAILED
        if (inviterId == redeemerId) return RedeemResult.SELF
        val createdAt = FirebaseAuth.getInstance().currentUser?.metadata?.creationTimestamp ?: 0L
        if (createdAt > 0 && System.currentTimeMillis() - createdAt > StaryConfig.INVITE_REDEEM_WINDOW_MS) {
            return RedeemResult.TOO_OLD
        }
        return try {
            val doc = invites.document(redeemerId)
            if (doc.get().await().exists()) return RedeemResult.ALREADY
            doc.set(
                mapOf(
                    "inviterId" to inviterId,
                    "redeemerId" to redeemerId,
                    "createdAt" to System.currentTimeMillis(),
                )
            ).await()
            RedeemResult.SUCCESS
        } catch (_: Exception) {
            RedeemResult.FAILED
        }
    }
}
