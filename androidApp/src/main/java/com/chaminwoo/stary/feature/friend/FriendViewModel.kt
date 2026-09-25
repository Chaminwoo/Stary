package com.chaminwoo.stary.feature.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.FriendRequest
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.shared.data.repository.FriendRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendViewModel(
    private val me: UserProfile,
    private val repository: FriendRepository,
) : ViewModel() {

    val friends = repository.observeFriends(me.userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests = repository.observeIncomingRequests(me.userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 내가 보낸 pending 요청 — 검색 결과의 "요청됨" 상태 칩 표시용. */
    val outgoingRequests = repository.observeOutgoingRequests(me.userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    // 토스트 문구는 화면이 현재 언어로 푼다(뷰모델은 Context 를 들지 않는다).
    private val _event = MutableSharedFlow<FriendMessage>()
    val event = _event.asSharedFlow()

    fun search(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repository.searchUsers(query.trim(), me.userId)
            _isSearching.value = false
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun sendRequest(to: UserProfile) {
        viewModelScope.launch {
            val ok = repository.sendRequest(me, to)
            _event.emit(
                if (ok) FriendMessage(com.chaminwoo.stary.R.string.friend_toast_request_sent, to.userName)
                else FriendMessage(com.chaminwoo.stary.R.string.friend_toast_request_failed)
            )
        }
    }

    fun accept(request: FriendRequest) {
        viewModelScope.launch {
            val ok = repository.acceptRequest(request)
            _event.emit(
                if (ok) FriendMessage(com.chaminwoo.stary.R.string.friend_toast_accepted, request.fromName)
                else FriendMessage(com.chaminwoo.stary.R.string.friend_toast_accept_failed)
            )
        }
    }

    fun decline(request: FriendRequest) {
        viewModelScope.launch {
            repository.declineRequest(request.id)
            _event.emit(FriendMessage(com.chaminwoo.stary.R.string.friend_toast_declined))
        }
    }

    fun remove(friendId: String, friendName: String) {
        viewModelScope.launch {
            repository.removeFriend(me.userId, friendId)
            _event.emit(FriendMessage(com.chaminwoo.stary.R.string.friend_toast_removed, friendName))
        }
    }

    companion object {
        fun factory(me: UserProfile): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FriendViewModel(me, FirebaseFriendRepository()) as T
            }
        }
    }
}

/** 친구 기능 토스트 — 리소스 id + 인자. 화면에서 [resolve] 로 현재 언어 문자열을 만든다. */
class FriendMessage(@androidx.annotation.StringRes val res: Int, private vararg val args: Any) {
    fun resolve(context: android.content.Context): String = context.getString(res, *args)
}
