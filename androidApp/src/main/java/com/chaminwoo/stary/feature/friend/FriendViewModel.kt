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

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _event = MutableSharedFlow<String>()
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
            _event.emit(if (ok) "${to.userName}님에게 친구 요청을 보냈어요" else "요청 실패")
        }
    }

    fun accept(request: FriendRequest) {
        viewModelScope.launch {
            val ok = repository.acceptRequest(request)
            _event.emit(if (ok) "${request.fromName}님과 친구가 되었어요" else "수락 실패")
        }
    }

    fun decline(request: FriendRequest) {
        viewModelScope.launch {
            repository.declineRequest(request.id)
            _event.emit("요청을 거절했어요")
        }
    }

    fun remove(friendId: String, friendName: String) {
        viewModelScope.launch {
            repository.removeFriend(me.userId, friendId)
            _event.emit("${friendName}님을 친구에서 삭제했어요")
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
