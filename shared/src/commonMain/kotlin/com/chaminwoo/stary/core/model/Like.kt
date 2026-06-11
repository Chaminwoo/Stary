package com.chaminwoo.stary.core.model

data class Like(
    val userId: String = "",
    val userName: String = "",
    val createdAt: Long = 0L // epoch millis (UTC)
)
