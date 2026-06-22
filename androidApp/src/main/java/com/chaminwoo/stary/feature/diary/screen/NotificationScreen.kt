package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotificationScreen(modifier: Modifier = Modifier) {
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    val vm: NotificationViewModel = viewModel(factory = NotificationViewModel.factory(userId))
    val notifications by vm.notifications.collectAsState()

    LaunchedEffect(Unit) { vm.markAllRead() }

    // null = Firestore 응답 대기 중 — 빈 화면으로 간주하지 않음
    if (notifications == null) return

    if (notifications!!.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔔", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("알림이 없습니다", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
            }
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(notifications!!, key = { it.id }) { notif ->
            SwipeToDeleteNotification(onDelete = { vm.delete(notif.id) }) {
                NotificationItem(notif)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// 부드러운 삭제색(기존 형광 빨강 대비 톤다운)
private val SoftDeleteRed = Color(0xFFE57373)

/**
 * 왼쪽으로 당기면 오른쪽에 고정 폭 삭제 버튼이 드러난다(버튼 보일 만큼만 당겨짐).
 * 삭제 버튼은 왼쪽 면이 둥글고, 탭하면 삭제된다.
 */
@Composable
private fun SwipeToDeleteNotification(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealDp = 84.dp
    val revealPx = with(LocalDensity.current) { revealDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        // 배경: 오른쪽 고정 폭 삭제 버튼(왼쪽 면 둥글게)
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(revealDp)
                    .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
                    .background(SoftDeleteRed)
                    .clickable {
                        onDelete()
                        scope.launch { offsetX.animateTo(0f) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("삭제", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        // 전경: 알림 행. 왼쪽으로만 당겨지고 최대 폭은 삭제 버튼 폭까지.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2f) -revealPx else 0f)
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun NotificationItem(notif: AppNotification) {
    val dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
        .format(java.util.Date(notif.createdAt))
    val isLike = notif.type == NotificationType.LIKE.name
    val isFriendPost = notif.type == NotificationType.FRIEND_POST.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (isFriendPost) "⭐" else if (isLike) "❤️" else "💬", fontSize = 20.sp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notif.actorName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isFriendPost -> "새 다이어리 \"${notif.diaryTitle}\"를 남겼어요"
                    isLike -> "\"${notif.diaryTitle}\"를 좋아해요"
                    else -> "\"${notif.diaryTitle}\"에 댓글을 남겼어요"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!isLike && !isFriendPost && notif.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${notif.content}\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
