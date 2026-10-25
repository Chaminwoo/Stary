package com.chaminwoo.stary.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.launchSafely
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

class DiaryViewModel(
    private val repository: DiaryRepository
) : ViewModel() {

    // 전체 다이어리 목록 — StateFlow로 변환해서 UI에 연결
    private val _diariesReady = MutableStateFlow(false)
    /** [diaries] 가 첫 목록을 받았는지 — 받기 전의 빈 목록은 "없음"이 아니라 "불러오는 중"(자리 표시). */
    val diariesReady = _diariesReady.asStateFlow()
    val diaries = repository.observeAllDiaries()
        .onEach { _diariesReady.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 저장, 수정, 삭제 결과 이벤트 — 문구는 화면이 현재 언어로 푼다([DiaryEvent.messageRes]).
    private val _event = MutableSharedFlow<DiaryEvent>()
    val event = _event.asSharedFlow()

    /**
     * 별 저장. 20초 안에 서버 응답이 없으면(연결 약함) [DiaryEvent.SAVE_SLOW] — 예전엔 끝없이 기다려 '올리는 중'이 멈췄다.
     * 쓰기는 이미 기기에 쌓여 있어 연결되면 올라가고, 다시 시도는 같은 번호(diary.id)로 써서 두 번 올라가지 않는다.
     */
    fun saveDiary(diary: Diary) {
        launchSafely {
            val success = withTimeoutOrNull(SAVE_TIMEOUT_MS) { repository.saveDiary(diary) }
            _event.emit(
                when (success) {
                    true -> DiaryEvent.SAVED
                    false -> DiaryEvent.SAVE_FAILED
                    null -> DiaryEvent.SAVE_SLOW
                }
            )
        }
    }

    /** 새 별 번호 — 업로드 화면이 한 번 받아 두고 다시 시도할 때도 같은 번호를 쓴다. */
    fun newDiaryId(): String = FirebaseDiaryRepository.newId()

    // Factory — Repository를 ViewModel에 전달하기 위해
    companion object {
        private const val SAVE_TIMEOUT_MS = 20_000L

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiaryViewModel(FirebaseDiaryRepository()) as T
            }
        }
    }

    private val myDiariesCache = mutableMapOf<String, StateFlow<List<Diary>>>()
    private val myReadyFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /** [getMyDiaries] 가 첫 목록을 받았는지([diariesReady] 와 같은 뜻). */
    fun myDiariesReady(userId: String): StateFlow<Boolean> =
        myReadyFlags.getOrPut(userId) { MutableStateFlow(false) }

    fun getMyDiaries(userId: String): StateFlow<List<Diary>> =
        myDiariesCache.getOrPut(userId) {
            val ready = myReadyFlags.getOrPut(userId) { MutableStateFlow(false) }
            repository.observeMyDiaries(userId)
                .onEach { ready.value = true }
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
        launchSafely { repository.prefetchDiaries(ids) }
    }

    fun updateDiary(diary: Diary) {
        launchSafely {
            val success = repository.updateDiary(diary)
            _event.emit(if (success) DiaryEvent.UPDATED else DiaryEvent.UPDATE_FAILED)
        }
    }

    // 기존 deleteDiary에 onSuccess 콜백 추가
    fun deleteDiary(diaryId: String, onSuccess: () -> Unit = {}) {
        launchSafely {
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
    /** 20초 넘게 서버 응답 없음 — 업로드 화면이 토스트 대신 "연결이 약해요" 창(다시 시도)을 띄운다. */
    SAVE_SLOW(com.chaminwoo.stary.R.string.upload_slow_title),
    UPDATED(com.chaminwoo.stary.R.string.toast_diary_updated),
    UPDATE_FAILED(com.chaminwoo.stary.R.string.toast_diary_update_failed),
    DELETED(com.chaminwoo.stary.R.string.toast_diary_deleted),
    DELETE_FAILED(com.chaminwoo.stary.R.string.toast_diary_delete_failed),
}
