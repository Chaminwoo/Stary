package com.chaminwoo.stary.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 푸시 알림 탭 딥링크 전역 상태.
 *
 * MainActivity 가 (콜드 스타트 `onCreate` / 백그라운드 복귀 `onNewIntent` 둘 다) 인텐트 extra 를 읽어 여기에 채우고,
 * `MainScreen` 이 관찰해 해당 화면(채팅방/다이어리 상세)으로 이동한 뒤 consume 한다.
 * (singleTop 이라 앱이 살아있을 때 알림 탭도 onNewIntent 로 들어와 반영됨.)
 */
object DeepLinkState {
    var diaryId by mutableStateOf<String?>(null)
        private set
    var chatFriendId by mutableStateOf<String?>(null)
        private set
    var chatFriendName by mutableStateOf<String?>(null)
        private set

    /** 인텐트에서 읽은 딥링크 목적지를 등록(있는 것만). */
    fun request(diaryId: String? = null, chatFriendId: String? = null, chatFriendName: String? = null) {
        if (!chatFriendId.isNullOrBlank()) {
            this.chatFriendId = chatFriendId
            this.chatFriendName = chatFriendName ?: ""
        } else if (!diaryId.isNullOrBlank()) {
            this.diaryId = diaryId
        }
    }

    /** 다이어리 목적지를 꺼내고 비운다(1회 소비). */
    fun consumeDiary(): String? {
        val v = diaryId
        diaryId = null
        return v?.takeIf { it.isNotBlank() }
    }

    /** 채팅 목적지를 꺼내고 비운다(1회 소비). (friendId, friendName) */
    fun consumeChat(): Pair<String, String>? {
        val id = chatFriendId ?: return null
        val name = chatFriendName ?: ""
        chatFriendId = null
        chatFriendName = null
        return if (id.isNotBlank()) id to name else null
    }
}
