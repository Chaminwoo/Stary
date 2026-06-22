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
}
