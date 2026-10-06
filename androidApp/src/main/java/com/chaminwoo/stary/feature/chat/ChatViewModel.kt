package com.chaminwoo.stary.feature.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.ChatHidden
import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.data.repository.FirebaseChatRepository
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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

    /** 방의 전체 메시지(양쪽 공용, createdAt 오름차순). */
    private val allMessages = repository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 내가 나에게서만 지운 것(users/{나}/chatHidden/{chatId}). */
    private val hidden = repository.observeHidden(myId, chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatHidden.NONE)

    /** 화면에 보일 메시지 = 전체 − 나에게서만 삭제한 것. */
    val messages = combine(allMessages, hidden) { all, h ->
        val ids = h.messageIds.toSet()
        all.filterNot { h.hides(it, ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _event = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
    /** 결과 토스트 — 문자열 대신 리소스 id(언어 전환 규칙, CLAUDE.md §2.5). */
    val event: SharedFlow<ChatEvent> = _event.asSharedFlow()

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

    /**
     * 내가 보낸 메시지를 전송 후 1분 이내에 한해 완전 삭제(상대방 쪽에서도 사라짐).
     * 선택 팝업을 띄워 둔 사이 1분이 지났으면 안내만 한다(서버 규칙도 60초를 강제).
     */
    fun deleteMessage(message: ChatMessage) {
        if (!canDelete(message)) {
            if (message.senderId == myId) _event.tryEmit(ChatEvent.DELETE_EXPIRED)
            return
        }
        viewModelScope.launch {
            if (!repository.deleteMessage(chatId, message.id)) _event.tryEmit(ChatEvent.FAILED)
        }
    }

    /**
     * 메시지 하나를 **나에게서만** 삭제(내 것/상대 것, 시간 제한 없음). 상대 방에는 그대로 남는다.
     * 친구 목록 미리보기용으로 "숨긴 뒤 내게 남는 마지막 메시지"를 같이 넘긴다.
     */
    fun hideForMe(message: ChatMessage) {
        viewModelScope.launch {
            val all = allMessages.value
            val h = hidden.value
            val ids = h.messageIds.toSet() + message.id
            val remaining = all.lastOrNull { !h.hides(it, ids) }
            val latestAt = maxOf(all.maxOfOrNull { it.createdAt } ?: 0L, message.createdAt)
            val ok = repository.hideMessageForMe(myId, chatId, message.id, latestAt, remaining)
            _event.tryEmit(if (ok) ChatEvent.HIDDEN else ChatEvent.FAILED)
        }
    }

    /** 지금 보이는 대화가 있는가 — "대화 내용 삭제" 메뉴 판단용. */
    fun hasVisibleMessages(): Boolean = messages.value.isNotEmpty()

    /** 대화방을 **나에게서만** 삭제 — 지금까지의 메시지를 내 화면에서 전부 가린다(새 메시지는 그대로 보인다). */
    fun clearForMe() {
        if (messages.value.isEmpty()) {
            _event.tryEmit(ChatEvent.NOTHING_TO_CLEAR)
            return
        }
        viewModelScope.launch {
            val upTo = allMessages.value.maxOfOrNull { it.createdAt } ?: 0L
            val ok = repository.clearChatForMe(myId, chatId, upTo)
            _event.tryEmit(if (ok) ChatEvent.CLEARED else ChatEvent.FAILED)
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

/** 채팅 화면 토스트(나만 삭제 결과 등). */
enum class ChatEvent(@StringRes val messageRes: Int) {
    HIDDEN(R.string.chat_hidden_done),
    CLEARED(R.string.chat_clear_done),
    NOTHING_TO_CLEAR(R.string.chat_clear_nothing),
    DELETE_EXPIRED(R.string.chat_delete_expired),
    FAILED(R.string.chat_action_failed),
}
