package com.chaminwoo.stary.core.model

/**
 * 채팅 **나에게서만 삭제** 상태 — Firestore `users/{나}/chatHidden/{chatId}` (본인만 읽고 씀, 2026-10-07).
 * 메시지/방 문서는 그대로 두고 내 화면에서만 가린다 → 상대 대화방에는 아무 영향이 없다.
 * (iOS `Models.swift` 의 `ChatHidden` 과 필드·판정이 같아야 한다.)
 *
 *  - [clearedAt]  : 대화방 나만 삭제. createdAt 이 이 값 **이하**인 메시지는 내 화면에서 사라진다(0 = 없음).
 *                   ⚠️ "지운 순간 내 기기 시각"이 아니라 **그때 방에 있던 마지막 메시지의 createdAt** 을 쓴다 —
 *                   createdAt 은 보낸 사람 기기 시각이라, 내 시계로 자르면 시계가 늦은 상대의 새 메시지까지 숨을 수 있다.
 *  - [messageIds] : 메시지 하나씩 나만 삭제한 id.
 *  - [previewFor]/[previewText]/[previewAt]/[previewSenderId] : 친구 목록 미리보기 대체값.
 *    방 메타(`chats/{chatId}.lastMessage`)는 양쪽 공용이라 내가 숨긴 마지막 메시지가 그대로 보인다 →
 *    숨길 때 "그 시점 방의 마지막 메시지 시각([previewFor])"과 "내게 남은 마지막 메시지"를 같이 적어 두고,
 *    방 updatedAt 이 previewFor 이하인 동안(= 그 뒤로 새 메시지가 없는 동안)만 대체값을 쓴다.
 */
data class ChatHidden(
    val clearedAt: Long = 0L,
    val messageIds: List<String> = emptyList(),
    val previewFor: Long = 0L,
    val previewText: String = "",
    val previewAt: Long = 0L,
    val previewSenderId: String = "",
) {
    /** 이 메시지가 내 화면에서 가려지는가. */
    fun hides(message: ChatMessage, hiddenIds: Set<String> = messageIds.toSet()): Boolean =
        (clearedAt > 0L && message.createdAt <= clearedAt) || message.id in hiddenIds

    /**
     * 친구 목록 행에 보일 마지막 대화. null = 보일 게 없음("아직 대화가 없어요" — 지운 뒤 새 메시지가 없을 때).
     * 인자는 방 메타(공용) 값 그대로.
     */
    fun preview(lastMessage: String, updatedAt: Long, lastSenderId: String): ChatPreview? {
        if (updatedAt <= 0L || updatedAt <= clearedAt) return null
        if (previewFor > 0L && updatedAt <= previewFor) {
            return if (previewText.isBlank() || previewAt <= clearedAt) null
            else ChatPreview(previewText, previewAt, previewSenderId)
        }
        if (lastMessage.isBlank()) return null
        return ChatPreview(lastMessage, updatedAt, lastSenderId)
    }

    companion object {
        /** 문서가 없을 때(아무것도 지우지 않음). */
        val NONE = ChatHidden()
    }
}

/** 친구 목록 행 미리보기 한 줄(내 숨김 상태를 반영한 결과). */
data class ChatPreview(
    val text: String,
    val at: Long,
    val senderId: String,
)
