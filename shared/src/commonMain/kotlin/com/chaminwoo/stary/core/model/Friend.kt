package com.chaminwoo.stary.core.model

/** 수락된 친구. Firestore: users/{uid}/friends/{friendUid} */
data class Friend(
    val userId: String = "",
    val userName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = 0L
)

/** 친구 요청. Firestore: friendRequests/{id} (pending 상태만 저장, 수락/거절 시 삭제) */
data class FriendRequest(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val fromPhotoUrl: String = "",
    val toId: String = "",
    val toName: String = "",
    val createdAt: Long = 0L
)

/** 사용자 검색 결과(공개 프로필). Firestore: users/{uid} */
data class UserProfile(
    val userId: String = "",
    val userName: String = "",
    val profileImageUrl: String = "",
    /** 장착한 칭호(업적 id). 타인 프로필에서도 칭호를 보여주기 위해 공개 프로필에 함께 보관. */
    val equippedTitle: String = ""
)
