package com.chaminwoo.stary.core.model

/**
 * 알림 종류.
 * - LIKE / COMMENT      : 내 다이어리에 달린 반응 (diaryId 有)
 * - FRIEND_POST         : 친구의 새 다이어리 (diaryId 有)
 * - FRIEND_REQUEST      : 받은 친구 요청 (diaryId 無 — 탭하면 친구 화면)
 */
enum class NotificationType { LIKE, COMMENT, FRIEND_POST, FRIEND_REQUEST }

data class AppNotification(
    val id: String = "",
    val type: String = NotificationType.LIKE.name,
    val diaryId: String = "",
    val diaryTitle: String = "",
    val diaryOwnerId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val content: String = "",
    val createdAt: Long = 0L, // epoch millis (UTC)
    val isRead: Boolean = false
)
