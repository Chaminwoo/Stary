package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.data.repository.FirebaseCommentRepository
import com.chaminwoo.stary.data.repository.FirebaseLikeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InteractionViewModel(
    private val diaryId: String,
    private val diaryTitle: String,
    private val diaryOwnerId: String,
    private val userId: String,
    private val userName: String
) : ViewModel() {

    private val likeRepo = FirebaseLikeRepository()
    private val commentRepo = FirebaseCommentRepository()

    val isLiked: StateFlow<Boolean> = likeRepo.observeIsLiked(diaryId, userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val likeCount: StateFlow<Int> = likeRepo.observeLikeCount(diaryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val comments: StateFlow<List<Comment>> = commentRepo.observeComments(diaryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleLike() {
        viewModelScope.launch {
            likeRepo.toggleLike(diaryId, diaryTitle, diaryOwnerId, userId, userName)
        }
    }

    fun addComment(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            commentRepo.addComment(diaryId, diaryTitle, diaryOwnerId, userId, userName, content)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            commentRepo.deleteComment(diaryId, commentId)
        }
    }

    companion object {
        fun factory(
            diaryId: String,
            diaryTitle: String,
            diaryOwnerId: String,
            userId: String,
            userName: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InteractionViewModel(diaryId, diaryTitle, diaryOwnerId, userId, userName) as T
        }
    }
}
