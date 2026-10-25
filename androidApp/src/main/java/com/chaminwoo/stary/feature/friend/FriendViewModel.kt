package com.chaminwoo.stary.feature.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.FriendRequest
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.core.util.launchSafely
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.data.repository.FriendRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class FriendViewModel(
    private val me: UserProfile,
    private val repository: FriendRepository,
) : ViewModel() {

    private val _friendsReady = MutableStateFlow(false)
    /** [friends] 가 첫 목록을 받았는지 — 받기 전의 빈 목록은 "친구 없음"이 아니라 "불러오는 중"(자리 표시). */
    val friendsReady = _friendsReady.asStateFlow()
    val friends = repository.observeFriends(me.userId)
        .onEach { _friendsReady.value = true }
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
        launchSafely {
            _isSearching.value = true
            try {
                _searchResults.value = repository.searchUsers(query.trim(), me.userId)
            } finally {
                _isSearching.value = false // 실패해도 검색 중 표시가 남지 않게
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun sendRequest(to: UserProfile) {
        launchSafely {
            // 요청에 박히는 내 이름/사진은 보내는 순간의 서버 확인값 — 화면을 열 때 잡아 둔 [me] 는 첫 실행 직후 구글 기본값일 수 있다.
            val name = GoogleAuthHelper.requireConfirmedName() ?: return@launchSafely
            val from = me.copy(userName = name, profileImageUrl = GoogleAuthHelper.currentUserPhotoUrl ?: me.profileImageUrl)
            val ok = repository.sendRequest(from, to)
            if (ok) {
                com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.FRIEND_REQUEST)
                // 친구가 되면 그 친구의 새 별을 알려 줄 수 있게 — 알림을 켤지 물을 차례(PermissionAsk).
                com.chaminwoo.stary.core.ui.PermissionAsk.requestNotificationAsk()
            }
            _event.emit(
                if (ok) FriendMessage(com.chaminwoo.stary.R.string.friend_toast_request_sent, to.userName)
                else FriendMessage(com.chaminwoo.stary.R.string.friend_toast_request_failed)
            )
        }
    }

    fun accept(request: FriendRequest) {
        launchSafely {
            val ok = repository.acceptRequest(request)
            // 친구가 늘었다 — 퀘스트 "친구 사귀기"(같은 사람은 하루 한 번). 내 요청이 수락된 경우는 친구 목록 구독에서 센다.
            if (ok) {
                com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.FRIEND_ADD, key = request.fromId)
                com.chaminwoo.stary.core.ui.PermissionAsk.requestNotificationAsk()
            }
            _event.emit(
                if (ok) FriendMessage(com.chaminwoo.stary.R.string.friend_toast_accepted, request.fromName)
                else FriendMessage(com.chaminwoo.stary.R.string.friend_toast_accept_failed)
            )
        }
    }

    fun decline(request: FriendRequest) {
        launchSafely {
            repository.declineRequest(request.id)
            _event.emit(FriendMessage(com.chaminwoo.stary.R.string.friend_toast_declined))
        }
    }

    fun remove(friendId: String, friendName: String) {
        launchSafely {
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
