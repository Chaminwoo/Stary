package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.data.repository.FirebaseCommentRepository
import com.chaminwoo.stary.data.repository.FirebaseLikeRepository
import com.chaminwoo.stary.core.util.launchSafely
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.quest.QuestEvent
import com.chaminwoo.stary.feature.quest.QuestStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn

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

    /**
     * 서버 반영 실패 — 화면이 "연결을 확인해 주세요" 토스트를 띄운다(문자열은 화면에서, CLAUDE.md §2.5).
     * 값은 되살릴 댓글 내용(댓글 등록 실패일 때만, 그 외 null). 하트·댓글 수는 서버 구독값이라 실패하면 그대로다.
     */
    private val _failed = MutableSharedFlow<String?>(extraBufferCapacity = 2)
    val failed: SharedFlow<String?> = _failed.asSharedFlow()

    private var likeJob: Job? = null

    fun toggleLike() {
        if (likeJob?.isActive == true) return // 빠른 연타로 좋아요/취소가 겹치지 않게
        likeJob = launchSafely {
            // 좋아요/댓글 알림에 박히는 이름 — 서버 확인값만(첫 실행 직후 구글 기본 이름 방지, CLAUDE.md §2.6).
            val userName = GoogleAuthHelper.requireConfirmedName() ?: return@launchSafely
            val liking = !isLiked.value
            if (!likeRepo.toggleLike(diaryId, diaryTitle, diaryOwnerId, userId, userName)) {
                _failed.tryEmit(null)
                return@launchSafely
            }
            // 퀘스트는 좋아요를 "누를" 때만, 같은 별은 하루 한 번(눌렀다 취소 반복으로 채우지 않게).
            if (liking) QuestStore.report(QuestEvent.LIKE, key = diaryId)
        }
    }

    fun addComment(content: String) {
        if (content.isBlank()) return
        launchSafely {
            val userName = GoogleAuthHelper.requireConfirmedName() ?: return@launchSafely
            if (!commentRepo.addComment(diaryId, diaryTitle, diaryOwnerId, userId, userName, content)) {
                _failed.tryEmit(content)
                return@launchSafely
            }
            QuestStore.report(QuestEvent.COMMENT)
            // 남의 별 댓글은 별마다 하루 한 번(같은 별에 여러 번 달아 채우지 않게).
            if (diaryOwnerId != userId) QuestStore.report(QuestEvent.COMMENT_OTHER, key = diaryId)
        }
    }

    fun deleteComment(commentId: String) {
        launchSafely {
            if (!commentRepo.deleteComment(diaryId, commentId)) _failed.tryEmit(null)
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
