package com.chaminwoo.stary.data.repository

import android.util.Log
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

                Log.e("CHAT_LISTENER", "observeMyChats", error)
                Log.d("CHAT", "docs=${snapshot?.documents?.size}")

                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participants =
                        (doc.get("participants") as? List<String>) ?: doc.id.split("_")

                    @Suppress("UNCHECKED_CAST")
                    val lastReadAt =
                        doc.get("lastReadAt") as? Map<String, Long> ?: emptyMap()

                    ChatSummary(
                        chatId = doc.id,
                        participants = participants,
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastSenderId = doc.getString("lastSenderId") ?: "",
                        lastSenderName = doc.getString("lastSenderName") ?: "",
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        lastReadAt = lastReadAt
                    )
                } ?: emptyList()

                Log.d("CHAT", "list size=${list.size}")

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
            // ⚠️ 방 메타를 **먼저** 만든다 — 방 문서가 없는 첫 메시지에서도 목록/규칙이 방을 인지하게.
            //    (서버 규칙이 방 문서 참여자로 게이팅하던 시절엔 이 순서가 아니면 첫 메시지가 거부됐다.)
            chats.document(chatId).set(
                mapOf(
                    "participants" to chatId.split("_"),
                    "lastMessage" to body,
                    "lastSenderId" to senderId,
                    "lastSenderName" to senderName,
                    "updatedAt" to now,
                    "lastReadAt" to mapOf(
                        senderId to now
                    )
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
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
            true
        } catch (e: Exception) {
            Log.w("CHAT", "메시지 전송 실패: ${e.localizedMessage}")
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
    suspend fun markAsRead(
        chatId: String,
        myId: String
    ) {
        chats.document(chatId)
            .update(
                "lastReadAt.$myId",
                System.currentTimeMillis()
            )
            .await()
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
    val lastReadAt: Map<String, Long> = emptyMap()
)
