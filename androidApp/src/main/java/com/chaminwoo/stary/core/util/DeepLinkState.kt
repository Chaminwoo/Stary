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
    /** 친구 초대 딥링크(stary://invite/{uid})의 초대자 uid — 로그인 후 리딤 시 소비(체크리스트 31). */
    var inviterId by mutableStateOf<String?>(null)
        private set

    /** 친구 요청 푸시 탭 — 친구 화면(받은 요청)으로 보낼지. 소비할 때마다 nonce 가 올라간다. */
    var friendsNonce by mutableStateOf(0)
        private set

    /** 인텐트에서 읽은 딥링크 목적지를 등록(있는 것만). */
    fun request(
        diaryId: String? = null,
        chatFriendId: String? = null,
        chatFriendName: String? = null,
        openFriends: Boolean = false,
    ) {
        if (!chatFriendId.isNullOrBlank()) {
            this.chatFriendId = chatFriendId
            this.chatFriendName = chatFriendName ?: ""
        } else if (!diaryId.isNullOrBlank()) {
            this.diaryId = diaryId
        } else if (openFriends) {
            friendsNonce += 1
        }
    }

    /** 초대 딥링크 등록 — 로그인 전에 들어와도 보관했다가 로그인 후 리딤한다. */
    fun requestInvite(inviterId: String?) {
        if (!inviterId.isNullOrBlank()) this.inviterId = inviterId
    }

    /** 초대자 uid 를 꺼내고 비운다(1회 소비). */
    fun consumeInvite(): String? {
        val v = inviterId
        inviterId = null
        return v?.takeIf { it.isNotBlank() }
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
