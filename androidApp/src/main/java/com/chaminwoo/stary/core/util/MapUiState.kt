package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "지도만 보기"(집중) 모드 전역 상태.
 * true 면 탑바·필터·FAB·줌버튼을 모두 숨기고 지도만 보여준다(여러 화면이 동시에 관찰).
 *
 * 2026-10-19: 버튼으로 들어가던 방식을 없애고 **지도에서 [IDLE_MS] 동안 아무것도 안 건드리면** 부드럽게 들어가고,
 * 화면을 건드리면(지도를 움직이거나 탭) 바로 나온다. 터치는 MainScreen 루트가 [noteTouch] 로 알린다(소비하지 않음 —
 * 나오게 만든 그 터치가 지도에도 그대로 간다). 판정 루프는 MainScreen.
 */
object MapUiState {
    /** 이만큼 가만히 있으면 집중 모드(iOS `MapChromeState.idleSeconds` 와 같은 값). */
    const val IDLE_MS = 20_000L
    /** 집중 모드로 들어갈 때 버튼·탑바가 사라지는 페이드(천천히) / 나올 때 다시 보이는 페이드(빠르게). iOS 와 같은 값. */
    const val CHROME_HIDE_MS = 1200
    const val CHROME_SHOW_MS = 260

    var mapOnly by mutableStateOf(false)
        private set

    /** 마지막으로 화면을 건드린 시각(ms). */
    @Volatile var lastTouchAt: Long = System.currentTimeMillis()
        private set

    fun enterMapOnly() { mapOnly = true }
    fun exitMapOnly() { mapOnly = false }

    /** 화면 터치 — 집중 모드면 나오고, 대기 시간을 처음부터. */
    fun noteTouch() {
        lastTouchAt = System.currentTimeMillis()
        if (mapOnly) mapOnly = false
    }

    /** 터치는 아니지만 대기 시간을 처음부터(지도로 돌아옴·앱 복귀·창 닫힘 등). */
    fun resetIdle() { lastTouchAt = System.currentTimeMillis() }

    /**
     * 지도 위에 창(Dialog — 자체 윈도우라 루트가 터치를 못 본다)이 떠 있는 수. 0 보다 크면 집중 모드에 들어가지 않는다.
     * 창 쪽에서 [holdIdle] 로 감싼다(예: 오늘의 퀘스트 창).
     */
    var idleHolds by mutableIntStateOf(0)
        private set

    fun holdIdle() { idleHolds++ }
    fun releaseIdle() {
        idleHolds = (idleHolds - 1).coerceAtLeast(0)
        resetIdle()
    }

    /**
     * 지도(Main 라우트)가 현재 화면에 보이는지 — MainScreen 이 라우트 전환에 맞춰 갱신.
     * 지도는 NavHost 밖에서 상시 렌더되므로(재생성 방지), 다른 화면에 가려진 동안엔
     * 이 값으로 마커 애니메이션 루프를 휴면시켜 GPU/배터리를 아낀다.
     */
    var mapVisible by mutableStateOf(true)

    /**
     * 지도 복귀 시 "카메라만 내 위치로" 요청 nonce(0=요청 없음) — 다른 화면에서 지도로
     * 돌아올 때 MainScreen 이 발급하고 DiaryMap 이 소비한다(포커스 요청 시엔 발급 안 함).
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

    fun request(diaryId: String) {
        pendingDiaryId = diaryId
    }
    fun consume() {
        pendingDiaryId = null
    }
}
