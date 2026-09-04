package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 설정 화면의 "도움말 다시 보기" → 지도(MainScreen) 코치마크를 다시 띄우기 위한 전역 브리지.
 * SettingsScreen 은 화면이 달라 MainScreen 의 `showOnboarding` 을 직접 못 건드리므로,
 * 여기에 요청만 남기면 MainScreen 이 감지해 지도로 돌아가 코치마크를 재생한다.
 * (DeepLinkState 와 동일한 요청/소비 패턴.)
 */
object OnboardingReplayState {
    var requested by mutableStateOf(false)
        private set

    fun request() {
        requested = true
    }

    /** MainScreen 이 처리 후 호출. */
    fun consume() {
        requested = false
    }
}
