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

    // 이미 배너로 띄운 "방:updatedAt" 키 집합 — 같은 메시지는 두 번 다시 뜨지 않는다(스냅샷 재방출/리컴포지션 무관).
    val shownKeys = remember { mutableStateListOf<String>() }
    var baselineDone by remember { mutableStateOf(false) }

    LaunchedEffect(chats) {
        if (!baselineDone) {
            // 최초 구독: 기존 방들은 기준선으로만 기록(앱 켤 때 과거 메시지 폭주 방지).
            chats.forEach { shownKeys.add("${it.chatId}:${it.updatedAt}") }
            baselineDone = true
            return@LaunchedEffect
        }
        chats.forEach { c ->
            val key = "${c.chatId}:${c.updatedAt}"
            if (key in shownKeys) return@forEach
            val friendId = c.participants.firstOrNull { it != userId } ?: ""
            val isIncoming = c.lastSenderId != userId && c.lastMessage.isNotBlank()
            if (!isIncoming) { shownKeys.add(key); return@forEach }
            shownKeys.add(key) // 한 번만 처리되도록 즉시 기록(이중 방지)
            val viewingThisChat = suppressChatWith != null && suppressChatWith == friendId
            // 전면 + 그 채팅을 보고 있지 않을 때만 인앱 배너. 후면/종료는 FCM 시스템 알림이 담당.
            if (!viewingThisChat && AppForeground.isForeground && AppSettings.notificationsEnabled) {
                InAppBanner.show(
                    title = c.lastSenderName.ifBlank { "새 메시지" },
                    body = c.lastMessage,
                    kind = InAppBanner.Kind.CHAT,
                    onClick = { onOpenChat(friendId, c.lastSenderName) },
                )
            }
        }
    }
}
