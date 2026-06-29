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

/**
 * 지도에 "특정 다이어리로 카메라 이동 + 열람 파장" 을 요청하는 전역 상태.
 * 예: 새 다이어리(친구글) 알림을 탭하면 지도로 와서 그 위치로 날아가 파장을 1회 낸다.
 * (알림 화면은 diaryId 만 알고 좌표는 모르므로, 지도 화면이 목록에서 좌표를 찾아 처리한다.)
 */
object MapFocusState {
    var pendingDiaryId by mutableStateOf<String?>(null)
        private set
    /** true 면 포커스(카메라+파장) 후 그 별까지 도보 길찾기 경로를 띄운다(친구 별 탭). */
    var pendingRoute by mutableStateOf(false)
        private set

    fun request(diaryId: String, withRoute: Boolean = false) {
        pendingDiaryId = diaryId
        pendingRoute = withRoute
    }
    fun consume() {
        pendingDiaryId = null
        pendingRoute = false
    }
}
