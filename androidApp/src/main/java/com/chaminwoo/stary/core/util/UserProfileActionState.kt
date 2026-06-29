package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 타인 프로필 화면의 친구 액션을 **탑바(MainScreen)** 에서 그릴 수 있게 잇는 전역 브리지.
 *
 * UserProfileScreen 이 진입하면 자기 상태(친구 여부/요청됨)와 클릭 동작을 여기에 등록하고,
 * MainScreen 탑바가 그 값을 읽어 우측 아이콘 버튼(사람+ = 친구추가 / 사람✓ = 친구)을 렌더한다.
 * (MapUiState / MapFocusState 와 동일한 전역 상태 패턴.)
 */
object UserProfileActionState {
    /** UserProfile 화면(본인 아님)일 때만 true → 탑바 버튼 노출. */
    var visible by mutableStateOf(false)
    var isFriend by mutableStateOf(false)
    var requested by mutableStateOf(false)
    var isBlocked by mutableStateOf(false)

    /** 탑바 친구 버튼 클릭(친구면 취소 확인, 아니면 요청 보내기). UserProfileScreen 이 등록. */
    var onClick: () -> Unit = {}

    /** 탑바 더보기(⋮) 메뉴 — 신고 / 차단·차단해제. */
    var onReport: () -> Unit = {}
    var onToggleBlock: () -> Unit = {}

    fun reset() {
        visible = false
        isFriend = false
        requested = false
        isBlocked = false
        onClick = {}
        onReport = {}
        onToggleBlock = {}
    }
}
