package com.chaminwoo.stary.core.model

enum class NotificationType { LIKE, COMMENT }

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
