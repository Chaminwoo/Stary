package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 내 프로필 화면의 "별(다이어리) 올리기" 동작을 **탑바(MainScreen) 의 + 버튼**에서 호출하기 위한 전역 브리지.
 * ProfileScreen 이 진입 시 picker 열기 콜백을 등록하고, 탑바가 프로필 화면일 때 + 버튼으로 그걸 호출한다.
 * (UserProfileActionState 와 동일한 전역 상태 패턴.)
 */
object ProfilePinState {
    /** 내 프로필 화면일 때만 true → 탑바 + 버튼 노출. */
    var visible by mutableStateOf(false)

    /** + 버튼 클릭 시 실행(핀 다이어리 선택 picker 열기). ProfileScreen 이 등록. */
    var onOpen: () -> Unit = {}

    fun reset() {
        visible = false
        onOpen = {}
    }
}
