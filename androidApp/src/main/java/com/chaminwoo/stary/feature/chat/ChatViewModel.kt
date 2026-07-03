package com.chaminwoo.stary.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.data.repository.FirebaseChatRepository
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 1:1 친구 채팅 ViewModel.
 *
 * @param myId       내 사용자 ID
 * @param myName     내 표시 이름(메시지에 박제)
 * @param friendId   상대 사용자 ID
 */
class ChatViewModel(
    private val myId: String,
    private val myName: String,
    friendId: String,
    private val repository: ChatRepository,
) : ViewModel() {

    private val chatId = StaryConfig.chatId(myId, friendId)

    val messages = repository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(chatId, myId, myName, text)
        }
    }

    /** 이 메시지를 지금 삭제할 수 있는가 — 내가 보냈고 전송 후 1분 이내일 때만. (삭제 UI 노출 판단) */
    fun canDelete(message: ChatMessage): Boolean =
        message.senderId == myId &&
            System.currentTimeMillis() - message.createdAt <= StaryConfig.CHAT_DELETE_WINDOW_MS

    /** 내가 보낸 메시지를 전송 후 1분 이내에 한해 완전 삭제(상대방 쪽에서도 사라짐). 조건 미충족 시 무시. */
    fun deleteMessage(message: ChatMessage) {
        if (!canDelete(message)) return
        viewModelScope.launch {
            repository.deleteMessage(chatId, message.id)
        }
    }

    companion object {
        fun factory(myId: String, myName: String, friendId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(myId, myName, friendId, FirebaseChatRepository()) as T
                }
            }
    }
}
