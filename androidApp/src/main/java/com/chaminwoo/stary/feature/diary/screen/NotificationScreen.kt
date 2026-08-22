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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.chaminwoo.stary.core.designsystem.LocalScreenSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.core.util.RelativeTime
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.NotificationViewModel

@Composable
fun NotificationScreen(
    modifier: Modifier = Modifier,
    onOpenDiary: (String) -> Unit = {},
    onFocusDiaryOnMap: (String) -> Unit = {},
    onOpenFriends: () -> Unit = {},
) {
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_login_required), color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    // key = userId — 기본 키(클래스 이름)만 쓰면 계정 전환 후에도 이전 사용자의 VM 이 재사용된다.
    val vm: NotificationViewModel = viewModel(key = userId, factory = NotificationViewModel.factory(userId))
    val notifications by vm.notifications.collectAsState()

    LaunchedEffect(Unit) { vm.markAllRead() }

    // null = Firestore 응답 대기 중 — 빈 화면으로 간주하지 않고 로딩 표시(검은 화면 방지).
    if (notifications == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.chaminwoo.stary.core.ui.StarLoadingIndicator()
        }
        return
    }

    // 낙관적 삭제: 스와이프 즉시 로컬에서 제거(서버 반영 왕복을 기다리지 않음).
    val locallyRemoved = remember { mutableStateListOf<String>() }
    // 차단한 사용자가 남긴 좋아요/댓글/친구 요청 알림은 숨긴다(지도·목록·댓글과 동일 규칙).
    val blockedIds by remember(userId) {
        com.chaminwoo.stary.data.repository.FirebaseModerationRepository().observeBlockedIds(userId)
    }.collectAsState(initial = emptySet())
    val visibleNotifs = notifications!!.filter { it.id !in locallyRemoved && it.actorId !in blockedIds }

    if (visibleNotifs.isEmpty()) {
        // 빈 상태도 별 언어로(떠 있는 골드 별 + 안내) — StaryEmptyState 공용.
        com.chaminwoo.stary.core.ui.StaryEmptyState(
            title = stringResource(R.string.notif_empty),
            description = stringResource(R.string.notif_empty_desc),
            starType = 0,
            starColorIndex = 1, // 골드
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(visibleNotifs, key = { it.id }) { notif ->
            // animateItem(): 삭제되면 그 셀이 사라지며 아래 셀들이 빈 공간을 부드럽게 채운다.
            Column(modifier = Modifier.animateItem()) {
                SwipeToDeleteNotification(onDelete = {
                    locallyRemoved.add(notif.id) // 즉시 셀 제거(애니메이션 collapse)
                    vm.delete(notif.id)          // 서버 삭제
                }) {
                    // 새 다이어리(친구 글) 알림은 상세 대신 지도에서 그 위치로 날아가 파장을 낸다.
                    // 친구 요청 알림은 다이어리가 없으므로 친구 화면(받은 요청)으로 보낸다.
                    val isFriendPost = notif.type == NotificationType.FRIEND_POST.name
                    val isFriendRequest = notif.type == NotificationType.FRIEND_REQUEST.name
                    NotificationItem(
                        notif,
                        onClick = when {
                            isFriendRequest -> { { onOpenFriends() } }
                            notif.diaryId.isBlank() -> null
                            isFriendPost -> { { onFocusDiaryOnMap(notif.diaryId) } }
                            else -> { { onOpenDiary(notif.diaryId) } }
                        }
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}


// 부드러운 삭제색(기존 형광 빨강 대비 톤다운)
private val SoftDeleteRed = Color(0xFFE57373)

/**
 * 왼쪽으로 당기면 오른쪽에 고정 폭 삭제 버튼이 드러난다.
 * - **끝까지(최대로) 당겨 놓으면**: 행이 화면 밖으로 밀려나며 곧바로 삭제된다.
 * - 중간쯤 당겨 놓으면: 삭제 버튼이 드러난 채 머무르고, 버튼을 탭하면 삭제된다.
 * - 살짝 당기면: 원위치로 닫힌다.
 */
@Composable
private fun SwipeToDeleteNotification(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealDp = 84.dp
    val density = LocalDensity.current
    val revealPx = with(density) { revealDp.toPx() }
    // 삭제 확정 시 행을 완전히 밀어낼 화면 밖 목표 위치(화면 폭만큼).
    // ⚠️ raw Configuration 이 아니라 [LocalScreenSize] — density 가 스케일돼 있어 원본 dp 를 toPx 하면 어긋난다.
    val dismissPx = with(density) { LocalScreenSize.current.width.toPx() }
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
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.common_delete), color = Color.White, fontSize = 12.sp)
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
                            // 절반 이상 당기면 즉시 삭제 — 셀이 바로 사라지고(낙관적 제거) 아래가 채워진다.
                            // 덜 당기면 원위치로 닫힌다(버튼만 남는 중간 상태 없음).
                            if (offsetX.value <= -revealPx / 2f) {
                                onDelete()
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun NotificationItem(notif: AppNotification, onClick: (() -> Unit)? = null) {
    val dateStr = remember(notif.createdAt) { RelativeTime.format(notif.createdAt) }
    val isLike = notif.type == NotificationType.LIKE.name
    val isFriendPost = notif.type == NotificationType.FRIEND_POST.name
    val isFriendRequest = notif.type == NotificationType.FRIEND_REQUEST.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                isFriendRequest -> "🙋"
                isFriendPost -> "⭐"
                isLike -> "❤️"
                else -> "💬"
            },
            fontSize = 20.sp
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notif.actorName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isFriendRequest -> stringResource(R.string.notif_friend_request)
                    isFriendPost -> stringResource(R.string.notif_friend_post, notif.diaryTitle)
                    isLike -> stringResource(R.string.notif_like, notif.diaryTitle)
                    else -> stringResource(R.string.notif_comment, notif.diaryTitle)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!isLike && !isFriendPost && !isFriendRequest && notif.content.isNotEmpty()) {
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
