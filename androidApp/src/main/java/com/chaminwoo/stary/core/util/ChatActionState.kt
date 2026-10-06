package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 채팅 화면의 "대화 내용 삭제(나에게서만)"를 **탑바(MainScreen) 의 ⋮ 메뉴**에서 호출하기 위한 전역 브리지.
 * ChatScreen 이 진입 시 확인 팝업 열기 콜백을 등록하고, 탑바가 그 채팅방일 때 메뉴로 그걸 호출한다.
 * (ProfilePinState / UserProfileActionState 와 같은 전역 상태 패턴.)
 *
 * ⚠️ 채팅 → (인앱 배너) → 다른 채팅으로 바로 넘어가면 새 화면 등록이 옛 화면 해제보다 **먼저** 일어날 수 있다 →
 *    해제는 [ownerKey] 가 자기 것일 때만 한다(옛 화면이 새 화면 등록을 지우지 않게).
 */
object ChatActionState {
    /** 지금 메뉴를 등록한 채팅 상대 id(없으면 null) — 탑바는 현재 라우트의 friendId 와 같을 때만 ⋮ 를 띄운다. */
    var ownerKey by mutableStateOf<String?>(null)
        private set

    /** "대화 내용 삭제" 클릭 시 실행. ChatScreen 이 등록. */
    var onClearChat: () -> Unit = {}
        private set

    fun register(key: String, onClear: () -> Unit) {
        ownerKey = key
        onClearChat = onClear
    }

    fun unregister(key: String) {
        if (ownerKey != key) return
        ownerKey = null
        onClearChat = {}
    }
}
