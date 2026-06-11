package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                Text("❤️", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("아직 반응이 없어요", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
            }
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(notifications!!, key = { it.id }) { notif ->
            NotificationItem(notif)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun NotificationItem(notif: AppNotification) {
    val dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
        .format(java.util.Date(notif.createdAt))
    val isLike = notif.type == NotificationType.LIKE.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (isLike) "❤️" else "💬", fontSize = 20.sp)

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
                text = if (isLike) "\"${notif.diaryTitle}\"를 좋아해요"
                       else "\"${notif.diaryTitle}\"에 댓글을 남겼어요",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (!isLike && notif.content.isNotEmpty()) {
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
