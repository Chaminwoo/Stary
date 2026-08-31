package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.model.BlockedUser
import com.chaminwoo.stary.core.ui.CardBgTop
import com.chaminwoo.stary.core.ui.TextMain
import com.chaminwoo.stary.core.ui.TextMuted
import com.chaminwoo.stary.core.ui.StaryEmptyState
import com.chaminwoo.stary.core.ui.ThumbAsyncImage
import com.chaminwoo.stary.core.ui.appCard
import com.chaminwoo.stary.data.repository.FirebaseModerationRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF9FB3E8)
private val SoftRed = Color(0xFFFF6B6B)
private val DialogBg = Color(0xFF0A0F1D)

/**
 * 차단 목록 — 설정 > 안전 에서 진입. 내가 차단한 사용자를 보고 해제한다.
 *
 * 차단은 `users/{내uid}/blocked/{상대uid}` 한 방향 기록이라 상대는 알 수 없고,
 * 차단된 사용자의 별은 지도/목록에서, 댓글은 상세에서 숨겨진다(MainListScreen/DetailScreen).
 * 이름·사진은 차단 시점 스냅샷이라 상대 프로필 문서를 다시 읽지 않는다.
 */
@Composable
fun BlockedUsersScreen(
    modifier: Modifier = Modifier,
    onOpenProfile: (userId: String, userName: String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val moderation = remember { FirebaseModerationRepository() }
    val myId = GoogleAuthHelper.currentUserId

    val blocked by remember(myId) {
        if (myId != null) moderation.observeBlockedUsers(myId) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // 해제 확인 대상(null = 다이얼로그 닫힘).
    var confirmTarget by remember { mutableStateOf<BlockedUser?>(null) }
    val unblockedMsg = stringResource(R.string.toast_unblocked)

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        // 설정/프로필과 동일한 우주 배경 톤.
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.84f), blendMode = BlendMode.Darken)
        )

        if (myId == null) {
            Text(
                stringResource(R.string.common_login_required),
                color = TextMuted, fontFamily = MinSans, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        if (blocked.isEmpty()) {
            StaryEmptyState(
                title = stringResource(R.string.blocked_empty),
                description = stringResource(R.string.blocked_empty_desc),
                starType = 7,        // 초승달 — 조용히 가려둔 상태
                starColorIndex = 0,  // 화이트
            )
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.blocked_hint),
                    color = TextMuted, fontFamily = MinSans, fontSize = 12.5.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(blocked, key = { it.userId }) { user ->
                BlockedRow(
                    user = user,
                    onOpenProfile = { onOpenProfile(user.userId, user.userName) },
                    onUnblock = { confirmTarget = user }
                )
            }
        }

        confirmTarget?.let { target ->
            val name = target.userName.ifBlank { stringResource(R.string.common_user) }
            AlertDialog(
                onDismissRequest = { confirmTarget = null },
                containerColor = DialogBg,
                titleContentColor = TextMain,
                textContentColor = TextMuted,
                title = { Text(stringResource(R.string.unblock), fontFamily = MinSans) },
                text = { Text(stringResource(R.string.unblock_confirm_msg, name), fontFamily = MinSans) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmTarget = null
                        scope.launch {
                            moderation.unblock(myId, target.userId)
                            com.chaminwoo.stary.core.ui.StaryToast.show(unblockedMsg)
                        }
                    }) { Text(stringResource(R.string.unblock), color = Accent, fontFamily = MinSans) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmTarget = null }) {
                        Text(stringResource(R.string.common_cancel), color = TextMuted, fontFamily = MinSans)
                    }
                }
            )
        }
    }
}

/** 차단 목록 한 줄 — [사진] [이름 / 차단일] [차단 해제]. 사진·이름 탭 = 프로필 열기. */
@Composable
private fun BlockedRow(
    user: BlockedUser,
    onOpenProfile: () -> Unit,
    onUnblock: () -> Unit,
) {
    // 차단 문서의 이름/사진은 차단 시점 스냅샷 → 현재 프로필로 표시(폴백은 스냅샷).
    val display = com.chaminwoo.stary.core.util.rememberUserDisplay(
        user.userId, user.userName, user.photoUrl
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenProfile() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(CardBgTop),
                contentAlignment = Alignment.Center
            ) {
                if (display.photoUrl.isNotBlank()) {
                    ThumbAsyncImage(
                        model = display.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        sizePx = 96,
                    )
                } else {
                    Text(
                        display.name.take(1).uppercase().ifBlank { "?" },
                        color = Accent, fontFamily = MinSans, fontSize = 16.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    display.name.ifBlank { stringResource(R.string.common_user) },
                    color = TextMain, fontFamily = MinSans, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.blocked_at, formatBlockedAt(user.createdAt)),
                    color = TextMuted, fontFamily = MinSans, fontSize = 12.sp, maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 해제 pill — 확인 다이얼로그를 거친다(오탭으로 바로 풀리지 않게).
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SoftRed.copy(alpha = 0.14f))
                .border(1.dp, SoftRed.copy(alpha = 0.32f), RoundedCornerShape(50))
                .clickable { onUnblock() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.unblock),
                color = SoftRed, fontFamily = MinSans, fontSize = 13.sp, fontWeight = FontWeight.Light
            )
        }
    }
}

/** 차단 시각 → yyyy.MM.dd (값이 없으면 "-"). */
private fun formatBlockedAt(createdAt: Long): String =
    if (createdAt <= 0L) "-"
    else SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(createdAt))
