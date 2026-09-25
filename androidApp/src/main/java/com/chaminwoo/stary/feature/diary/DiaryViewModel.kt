package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiaryViewModel(
    private val repository: DiaryRepository
) : ViewModel() {

    // 전체 다이어리 목록 — StateFlow로 변환해서 UI에 연결
    val diaries = repository.observeAllDiaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 저장, 수정, 삭제 결과 이벤트 — 문구는 화면이 현재 언어로 푼다([DiaryEvent.messageRes]).
    private val _event = MutableSharedFlow<DiaryEvent>()
    val event = _event.asSharedFlow()

    fun saveDiary(diary: Diary) {
        viewModelScope.launch {
            val success = repository.saveDiary(diary)
            _event.emit(if (success) DiaryEvent.SAVED else DiaryEvent.SAVE_FAILED)
        }
    }

    // Factory — Repository를 ViewModel에 전달하기 위해
    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiaryViewModel(FirebaseDiaryRepository()) as T
            }
        }
    }

    private val myDiariesCache = mutableMapOf<String, StateFlow<List<Diary>>>()

    fun getMyDiaries(userId: String): StateFlow<List<Diary>> =
        myDiariesCache.getOrPut(userId) {
            repository.observeMyDiaries(userId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
        }

    fun prefetchNearby(allDiaries: List<Diary>, lat: Double, lng: Double) {
        val ids = allDiaries
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .filter { LocationHelper.distanceBetween(lat, lng, it.latitude, it.longitude) <= 150f }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.prefetchDiaries(ids) }
    }

    fun updateDiary(diary: Diary) {
        viewModelScope.launch {
            val success = repository.updateDiary(diary)
            _event.emit(if (success) DiaryEvent.UPDATED else DiaryEvent.UPDATE_FAILED)
        }
    }

    // 기존 deleteDiary에 onSuccess 콜백 추가
    fun deleteDiary(diaryId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.deleteDiary(diaryId)
            if (success) {
                _event.emit(DiaryEvent.DELETED)
                onSuccess()
            } else {
                _event.emit(DiaryEvent.DELETE_FAILED)
            }
        }
    }

}

/** 다이어리 저장/수정/삭제 결과 — 예전엔 한국어 문자열("저장 완료!")을 흘려 비교까지 했다(언어 전환 시 번역 불가). */
enum class DiaryEvent(@androidx.annotation.StringRes val messageRes: Int) {
    SAVED(com.chaminwoo.stary.R.string.toast_diary_saved),
    SAVE_FAILED(com.chaminwoo.stary.R.string.toast_diary_save_failed),
    UPDATED(com.chaminwoo.stary.R.string.toast_diary_updated),
    UPDATE_FAILED(com.chaminwoo.stary.R.string.toast_diary_update_failed),
    DELETED(com.chaminwoo.stary.R.string.toast_diary_deleted),
    DELETE_FAILED(com.chaminwoo.stary.R.string.toast_diary_delete_failed),
}
