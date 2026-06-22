package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.data.repository.FirebaseNotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(private val userId: String) : ViewModel() {

    private val repo = FirebaseNotificationRepository()

    val notifications: StateFlow<List<AppNotification>?> = repo.observeNotifications(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val unreadCount: StateFlow<Int> = repo.observeUnreadCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAllRead() {
        viewModelScope.launch { repo.markAllRead(userId) }
    }

    fun delete(notificationId: String) {
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
