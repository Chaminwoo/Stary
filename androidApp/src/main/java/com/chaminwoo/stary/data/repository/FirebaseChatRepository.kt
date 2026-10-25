package com.chaminwoo.stary.data.repository

import android.util.Log
import com.chaminwoo.stary.core.model.ChatHidden
import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ChatRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
 *  - users/{나}/chatHidden/{chatId}      : **나에게서만** 지운 메시지/대화(본인 전용, [ChatHidden]) — 공용 문서는 건드리지 않는다
 *
 * chatId 는 [StaryConfig.chatId] 로 두 사용자 ID 를 정렬·결합해 만든 결정적 값이다.
 */
class FirebaseChatRepository : ChatRepository {

    private val db = staryFirestore
    private val chats = db.collection(StaryConfig.Collections.CHATS)

    private fun messagesRef(chatId: String) =
        chats.document(chatId).collection(StaryConfig.Collections.MESSAGES)

    private fun hiddenRef(myId: String) =
        db.collection(StaryConfig.Collections.USERS).document(myId)
            .collection(StaryConfig.Collections.CHAT_HIDDEN)

    /**
     * 내가 참여한 모든 채팅방의 메타(마지막 메시지) 실시간 관찰 — 인앱 채팅 팝업용.
     * ⚠️ orderBy 를 서버에 두면 (participants arrayContains + updatedAt) 복합 인덱스가 필요해
     *    미생성 시 누락된다 → 서버는 whereArrayContains 만, 정렬/판단은 클라이언트에서.
     */
    fun observeMyChats(userId: String): Flow<List<ChatSummary>> = callbackFlow {
        val listener = chats
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // 로그아웃 직후엔 규칙(request.auth != null)에 걸려 여기로 온다 — 정상 흐름이라 warn.
                    Log.w("CHAT", "observeMyChats 실패: ${error.localizedMessage}")
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

                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        // 메타 변경도 받는다 — 보낸 메시지가 서버에 올라가는 순간 "보내는 중" 표시를 지우려면 필요하다.
        val listener = messagesRef(chatId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id, pending = doc.metadata.hasPendingWrites())
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
            // 방 메타 + 메시지를 **한 묶음**으로 쓴다. 예전엔 방 메타의 서버 응답을 기다린 뒤에야 메시지를 써서,
            // 연결이 약하면 내 메시지가 화면에 안 뜨고 그사이 앱을 끄면 사라졌다. 묶음은 commit 순간 기기에 쌓여
            // 바로 보이고(보내는 중), 연결되면 함께 올라간다. 규칙은 get() 없이 chatId 로 판정해 첫 메시지 묶음도 통과한다.
            // (메시지는 맵으로 쓴다 — ChatMessage 의 화면용 pending 필드를 서버에 싣지 않게.)
            val batch = db.batch()
            batch.set(
                chats.document(chatId),
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
                SetOptions.merge()
            )
            batch.set(
                doc,
                mapOf(
                    "id" to doc.id,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "text" to body,
                    "createdAt" to now,
                )
            )
            batch.commit().await()
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

    override fun observeHidden(myId: String, chatId: String): Flow<ChatHidden> = callbackFlow {
        val listener = hiddenRef(myId).document(chatId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // 규칙 미배포(chatHidden 경로)면 여기로 온다 — 숨김 없이 보여 주고 앱은 계속 동작.
                Log.w("CHAT", "observeHidden 실패: ${error.localizedMessage}")
                trySend(ChatHidden.NONE)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toChatHidden() ?: ChatHidden.NONE)
        }
        awaitClose { listener.remove() }
    }

    /** 내 모든 방의 숨김 상태(chatId → 상태) — 친구 목록 미리보기/정렬용. */
    fun observeAllHidden(myId: String): Flow<Map<String, ChatHidden>> = callbackFlow {
        val listener = hiddenRef(myId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("CHAT", "observeAllHidden 실패: ${error.localizedMessage}")
                trySend(emptyMap())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.associate { it.id to it.toChatHidden() } ?: emptyMap())
        }
        awaitClose { listener.remove() }
    }

    override suspend fun hideMessageForMe(
        myId: String,
        chatId: String,
        messageId: String,
        latestAt: Long,
        remaining: ChatMessage?,
    ): Boolean = try {
        hiddenRef(myId).document(chatId).set(
            mapOf(
                "messageIds" to FieldValue.arrayUnion(messageId),
                "previewFor" to latestAt,
                "previewText" to (remaining?.text ?: ""),
                "previewAt" to (remaining?.createdAt ?: 0L),
                "previewSenderId" to (remaining?.senderId ?: ""),
            ),
            SetOptions.merge()
        ).await()
        true
    } catch (e: Exception) {
        Log.w("CHAT", "나만 삭제 실패: ${e.localizedMessage}")
        false
    }

    /**
     * 방 메타의 updatedAt 도 함께 본다 — 1분 삭제(모두에게서)로 사라진 메시지가 방 미리보기에는 남아 있을 수 있어서,
     * 그것까지 덮어야 친구 목록에서도 대화가 비어 보인다. clearedAt 이 그 이전 것을 전부 가리므로
     * 개별 숨김 id·미리보기 대체값은 비운다(문서가 계속 커지지 않게).
     */
    override suspend fun clearChatForMe(myId: String, chatId: String, upTo: Long): Boolean = try {
        val metaAt = try {
            chats.document(chatId).get().await().getLong("updatedAt") ?: 0L
        } catch (e: Exception) {
            0L // 방 메타를 못 읽어도 메시지 기준으로는 지운다
        }
        val cut = maxOf(upTo, metaAt)
        if (cut > 0L) {
            hiddenRef(myId).document(chatId).set(
                mapOf(
                    "clearedAt" to cut,
                    "messageIds" to emptyList<String>(),
                    "previewFor" to 0L,
                    "previewText" to "",
                    "previewAt" to 0L,
                    "previewSenderId" to "",
                )
            ).await()
        }
        true
    } catch (e: Exception) {
        Log.w("CHAT", "대화 나만 삭제 실패: ${e.localizedMessage}")
        false
    }

    private fun DocumentSnapshot.toChatHidden(): ChatHidden = ChatHidden(
        clearedAt = getLong("clearedAt") ?: 0L,
        messageIds = (get("messageIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        previewFor = getLong("previewFor") ?: 0L,
        previewText = getString("previewText") ?: "",
        previewAt = getLong("previewAt") ?: 0L,
        previewSenderId = getString("previewSenderId") ?: "",
    )

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
