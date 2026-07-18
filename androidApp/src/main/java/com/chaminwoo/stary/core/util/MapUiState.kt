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

    /**
     * 지도(Main 라우트)가 현재 화면에 보이는지 — MainScreen 이 라우트 전환에 맞춰 갱신.
     * 지도는 NavHost 밖에서 상시 렌더되므로(재생성 방지), 다른 화면에 가려진 동안엔
     * 이 값으로 마커 애니메이션 루프를 휴면시켜 GPU/배터리를 아낀다.
     */
    var mapVisible by mutableStateOf(true)

    /** 도보 길찾기 경로가 활성인지(DiaryMap 이 갱신) — 활성 중엔 지도 복귀 재센터를 건너뛴다. */
    var routeActive by mutableStateOf(false)

    /**
     * 지도 복귀 시 "카메라만 내 위치로" 요청 nonce(0=요청 없음) — 다른 화면에서 지도로
     * 돌아올 때 MainScreen 이 발급하고 DiaryMap 이 소비한다(포커스/길찾기 요청 시엔 발급 안 함).
     */
    var recenterNonce by mutableStateOf(0L)
        private set

    fun requestRecenter() { recenterNonce++ }
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
