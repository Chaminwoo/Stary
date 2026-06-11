package com.chaminwoo.stary.core.model

data class Comment(
    val id: String = "",
    val diaryId: String = "",
    val userId: String = "",
    val userName: String = "",
    val content: String = "",
    val createdAt: Long = 0L // epoch millis (UTC)
)
