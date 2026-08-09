package com.chaminwoo.stary.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.core.ui.InAppBanner
import com.chaminwoo.stary.core.util.AppForeground
import com.chaminwoo.stary.core.util.AppSettings
import com.chaminwoo.stary.data.repository.FirebaseChatRepository

/**
 * 다이어리 알림(좋아요/댓글/친구 새 글) 인앱 팝업 감시기.
 * 앱이 전면에 있는 동안 새 알림이 도착하면 상단 배너를 띄운다.
 * 최초 구독 시점의 알림은 기준선으로만 잡고 띄우지 않는다(앱 켤 때 과거 알림 폭주 방지).
 *
 * [notifications] 는 [NotificationViewModel.notifications] (null=로딩 중) 을 그대로 전달한다.
 */
@Composable
fun NotificationPopupWatcher(
    notifications: List<AppNotification>?,
    onOpen: (AppNotification) -> Unit,
) {
    val shownIds = remember { mutableStateListOf<String>() }
    var baseline by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(notifications) {
        val list = notifications ?: return@LaunchedEffect
        val maxCreated = list.maxOfOrNull { it.createdAt } ?: 0L
        if (baseline == null) {
            // 최초 구독: 기존 알림은 기준선으로만 기록(팝업 X)
            baseline = maxCreated
            return@LaunchedEffect
        }
        val base = baseline ?: 0L
        list.filter { it.createdAt > base && it.id !in shownIds }
            .sortedBy { it.createdAt }
            .forEach { n ->
                shownIds.add(n.id) // 후면에서 온 알림도 seen 처리(전면 복귀 시 한꺼번에 뜨지 않게)
                // 전면일 때만 인앱 배너. 후면/종료 상태는 FCM 시스템 알림이 담당(이중 방지).
                if (AppForeground.isForeground && AppSettings.notificationsEnabled) {
                    InAppBanner.show(
                        title = notificationTitle(n),
                        body = n.content.ifBlank { n.diaryTitle },
                        kind = InAppBanner.Kind.NOTIFICATION,
                        key = "notif:${n.id}",
                        onClick = { onOpen(n) },
                    )
                }
            }
        baseline = maxOf(base, maxCreated)
    }
}

private fun notificationTitle(n: AppNotification): String {
    val who = n.actorName.ifBlank { "누군가" }
    return when (n.type) {
        NotificationType.LIKE.name -> "${who}님이 좋아요를 눌렀어요"
        NotificationType.COMMENT.name -> "${who}님이 댓글을 남겼어요"
        NotificationType.FRIEND_POST.name -> "${who}님이 새 다이어리를 올렸어요"
        NotificationType.FRIEND_REQUEST.name -> "${who}님이 친구 요청을 보냈어요"
        else -> "${who}님의 새 알림"
    }
}

/**
 * 친구 채팅 새 메시지 인앱 팝업 감시기.
 * 내가 참여한 채팅방 메타([FirebaseChatRepository.observeMyChats])를 관찰해, 마지막 메시지가
 * 내가 보낸 게 아니고 새로(updatedAt 증가) 도착하면 상단 배너를 띄운다.
 * [suppressChatWith] 가 그 방 상대와 같으면(=지금 그 채팅을 보고 있으면) 띄우지 않는다.
 */
@Composable
fun ChatPopupWatcher(
    userId: String,
    suppressChatWith: String?,
    onOpenChat: (friendId: String, friendName: String) -> Unit,
) {
    val repo = remember { FirebaseChatRepository() }
    val chats by remember(userId) { repo.observeMyChats(userId) }
        .collectAsState(initial = emptyList())

    // 와처가 뜬 시각 — 이 시각 이후로 "갱신된(updatedAt)" 메시지만 배너로 띄운다.
    // ⚠️ 이전엔 "첫 방출 = 기준선" 방식이었는데, collectAsState 의 초기 emptyList 방출이 그 기준선을 먼저
    //    소비해 버려서( baselineDone=true ) 직후 도착한 실제 스냅샷이 "새 메시지"로 잘못 떴다 →
    //    앱 실행 때마다 기존 채팅이 한 번씩 뜨던 원인(에뮬레이터와 무관). 타임스탬프 기준으로 근본 해결.
    val sinceTime = remember(userId) { System.currentTimeMillis() }

    LaunchedEffect(chats) {
        chats.forEach { c ->
            if (c.updatedAt <= sinceTime) return@forEach // 와처 시작 전 메시지(=기존) → 무시
            val friendId = c.participants.firstOrNull { it != userId } ?: ""
            val isIncoming = c.lastSenderId != userId && c.lastMessage.isNotBlank()
            if (!isIncoming) return@forEach
            val viewingThisChat = suppressChatWith != null && suppressChatWith == friendId
            // 전면 + 그 채팅을 보고 있지 않을 때만 인앱 배너. 후면/종료는 FCM 시스템 알림이 담당.
            // 중복 enqueue 는 InAppBanner.show(key) 의 프로세스 영속 dedup 이 막는다(스냅샷 재방출/리컴포지션 무관).
            if (!viewingThisChat && AppForeground.isForeground && AppSettings.notificationsEnabled) {
                InAppBanner.show(
                    title = c.lastSenderName.ifBlank { "새 메시지" },
                    body = c.lastMessage,
                    kind = InAppBanner.Kind.CHAT,
                    key = "${c.chatId}:${c.updatedAt}",
                    onClick = { onOpenChat(friendId, c.lastSenderName) },
                )
            }
        }
    }
}
