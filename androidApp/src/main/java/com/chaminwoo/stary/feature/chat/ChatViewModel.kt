package com.chaminwoo.stary.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
