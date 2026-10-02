package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.data.repository.FirebaseNotificationRepository
import com.chaminwoo.stary.data.repository.FirstStarFeed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(private val userId: String) : ViewModel() {

    private val repo = FirebaseNotificationRepository()

    /** 내 알림 문서 + 첫 별 공지(FirstStarFeed)를 시간순으로 합친 목록. */
    val notifications: StateFlow<List<AppNotification>?> =
        combine(repo.observeNotifications(userId), FirstStarFeed.observe(userId)) { mine, firstStars ->
            (mine + firstStars).sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val unreadCount: StateFlow<Int> =
        combine(repo.observeUnreadCount(userId), FirstStarFeed.observe(userId)) { mine, firstStars ->
            mine + firstStars.count { !it.isRead }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAllRead() {
        FirstStarFeed.markAllSeen()
        viewModelScope.launch { repo.markAllRead(userId) }
    }

    fun delete(notificationId: String) {
        // 첫 별 공지는 모두가 보는 공용 문서 → 이 기기에서만 숨긴다.
        if (FirstStarFeed.isFirstStar(notificationId)) {
            FirstStarFeed.hide(notificationId)
            return
        }
        viewModelScope.launch { repo.deleteNotification(notificationId) }
    }

    companion object {
        fun factory(userId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotificationViewModel(userId) as T
        }
    }
}
