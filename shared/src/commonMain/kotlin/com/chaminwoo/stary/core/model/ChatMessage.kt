package com.chaminwoo.stary.core.model

/**
 * 1:1 친구 채팅 메시지.
 * Firestore: chats/{chatId}/messages/{messageId}
 *  - chatId 는 두 사용자 ID 를 정렬해 합친 결정적 값(StaryConfig.chatId) → 양쪽이 같은 방을 본다.
 *  - createdAt 은 epoch millis(Long). (다른 모델과 동일 규칙)
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)
