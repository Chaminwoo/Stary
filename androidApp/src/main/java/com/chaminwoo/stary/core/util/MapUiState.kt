package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "지도만 보기"(몰입) 모드 전역 상태.
 * true 면 탑바·필터·FAB·줌버튼을 모두 숨기고 지도만 보여준다(여러 화면이 동시에 관찰).
 */
object MapUiState {
    var mapOnly by mutableStateOf(false)
        private set

    fun enterMapOnly() { mapOnly = true }
    fun exitMapOnly() { mapOnly = false }
}
