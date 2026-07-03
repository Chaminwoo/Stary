package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ChatRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 공용 [ChatRepository] 의 Firestore 구현.
 *
 * 구조:
 *  - chats/{chatId}                      : 방 메타(참여자, 마지막 메시지) — 목록/미리보기용
 *  - chats/{chatId}/messages/{messageId} : 메시지 (createdAt 오름차순)
 *
 * chatId 는 [StaryConfig.chatId] 로 두 사용자 ID 를 정렬·결합해 만든 결정적 값이다.
 */
class FirebaseChatRepository : ChatRepository {

    private val db = staryFirestore
    private val chats = db.collection(StaryConfig.Collections.CHATS)

    private fun messagesRef(chatId: String) =
        chats.document(chatId).collection(StaryConfig.Collections.MESSAGES)

    /**
     * 내가 참여한 모든 채팅방의 메타(마지막 메시지) 실시간 관찰 — 인앱 채팅 팝업용.
     * ⚠️ orderBy 를 서버에 두면 (participants arrayContains + updatedAt) 복합 인덱스가 필요해
     *    미생성 시 누락된다 → 서버는 whereArrayContains 만, 정렬/판단은 클라이언트에서.
     */
    fun observeMyChats(userId: String): Flow<List<ChatSummary>> = callbackFlow {
        val listener = chats
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participants = (doc.get("participants") as? List<String>) ?: doc.id.split("_")
                    ChatSummary(
                        chatId = doc.id,
                        participants = participants,
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastSenderId = doc.getString("lastSenderId") ?: "",
                        lastSenderName = doc.getString("lastSenderName") ?: "",
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = messagesRef(chatId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        text: String
    ): Boolean {
        val body = text.trim()
        if (body.isEmpty()) return false
        return try {
            val now = System.currentTimeMillis()
            val doc = messagesRef(chatId).document()
            doc.set(
                ChatMessage(
                    id = doc.id,
                    senderId = senderId,
                    senderName = senderName,
                    text = body,
                    createdAt = now
                )
            ).await()
            // 방 메타 갱신(목록/미리보기·재구독용). 실패해도 메시지 자체는 전송됨.
            try {
                chats.document(chatId).set(
                    mapOf(
                        "participants" to chatId.split("_"),
                        "lastMessage" to body,
                        "lastSenderId" to senderId,
                        "lastSenderName" to senderName, // 인앱 팝업에 보낸 사람 이름 표시용
                        "updatedAt" to now,
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 메시지 문서를 완전 삭제(양쪽 방에서 사라짐). "보낸 본인 + 전송 후 1분 이내"는 [ChatViewModel] 이 게이팅.
     * 방 메타(lastMessage 미리보기)는 갱신하지 않는다 — 다음 메시지가 오면 자연히 덮이고,
     * 규칙이 삭제 시간창(1분)만 강제하므로 과거 미리보기가 남아도 사소한 표시 문제일 뿐이다.
     */
    override suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
        return try {
            messagesRef(chatId).document(messageId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** 채팅방 메타 요약(인앱 팝업/목록용). */
data class ChatSummary(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val lastSenderName: String = "",
    val updatedAt: Long = 0L,
)
