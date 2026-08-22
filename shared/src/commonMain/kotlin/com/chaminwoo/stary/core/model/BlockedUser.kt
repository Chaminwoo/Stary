package com.chaminwoo.stary.core.model

/**
 * 내가 차단한 사용자. Firestore: users/{uid}/blocked/{blockedUid}
 * (문서 id = 상대 uid. 이름/사진은 차단 시점 스냅샷 — 차단 목록 화면에서 상대 문서를 다시 읽지 않아도 되게.)
 */
data class BlockedUser(
    val userId: String = "",
    val userName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = 0L
)
