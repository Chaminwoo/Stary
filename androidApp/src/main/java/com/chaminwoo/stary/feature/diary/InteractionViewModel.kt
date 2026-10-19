package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.data.repository.FirebaseCommentRepository
import com.chaminwoo.stary.data.repository.FirebaseLikeRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InteractionViewModel(
    private val diaryId: String,
    private val diaryTitle: String,
    private val diaryOwnerId: String,
    private val userId: String,
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
            // 좋아요/댓글 알림에 박히는 이름 — 서버 확인값만(첫 실행 직후 구글 기본 이름 방지, CLAUDE.md §2.6).
            val userName = GoogleAuthHelper.requireConfirmedName() ?: return@launch
            val liking = !isLiked.value
            likeRepo.toggleLike(diaryId, diaryTitle, diaryOwnerId, userId, userName)
            // 퀘스트는 좋아요를 "누를" 때만, 같은 별은 하루 한 번(눌렀다 취소 반복으로 채우지 않게).
            if (liking) com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.LIKE, key = diaryId)
        }
    }

    fun addComment(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val userName = GoogleAuthHelper.requireConfirmedName() ?: return@launch
            commentRepo.addComment(diaryId, diaryTitle, diaryOwnerId, userId, userName, content)
            com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.COMMENT)
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
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InteractionViewModel(diaryId, diaryTitle, diaryOwnerId, userId) as T
        }
    }
}
