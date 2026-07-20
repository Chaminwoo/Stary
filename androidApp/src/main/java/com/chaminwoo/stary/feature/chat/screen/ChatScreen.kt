package com.chaminwoo.stary.feature.chat.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.core.ui.CardBgTop
import com.chaminwoo.stary.core.ui.PageBg
import com.chaminwoo.stary.core.ui.TextMain
import com.chaminwoo.stary.core.ui.TextMuted
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val Green = Color(0xFF6EE7B7)

@Composable
fun ChatScreen(
    friendId: String,
    friendName: String,
    modifier: Modifier = Modifier,
) {
    val myId = GoogleAuthHelper.currentUserId

    if (myId == null || friendId.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().background(PageBg),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.chat_cannot_open), color = TextMuted, fontSize = 15.sp)
        }
        return
    }

    val myName = remember { GoogleAuthHelper.currentUserName ?: "나" }
    val vm: ChatViewModel = viewModel(
        key = "chat_$friendId",
        factory = ChatViewModel.factory(myId, myName, friendId)
    )
    val messages by vm.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // 롱프레스한 내 메시지(1분 이내) — 완전 삭제 확인 대상. null 이면 다이얼로그 숨김.
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // 이 방을 보는 동안은 항상 읽음 처리(친구 목록의 미읽음 판정 기준 — ChatReadStore).
    val chatContext = androidx.compose.ui.platform.LocalContext.current
    val chatId = remember(myId, friendId) {
        com.chaminwoo.stary.shared.config.StaryConfig.chatId(myId, friendId)
    }
    LaunchedEffect(messages.size) {
        com.chaminwoo.stary.core.util.ChatReadStore.markRead(chatContext, chatId)
    }

    // 새 메시지가 오면 맨 아래로 스크롤.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(modifier = modifier.fillMaxSize().background(PageBg)) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.85f), blendMode = BlendMode.Darken)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.chat_empty, friendName.ifBlank { stringResource(R.string.common_friend) }),
                        color = TextMuted, fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.senderId == myId
                        // 내 메시지 + 전송 후 1분 이내면 롱프레스로 완전 삭제(그 외엔 롱프레스 비활성)
                        MessageBubble(
                            msg = msg,
                            isMine = mine,
                            onLongPress = if (mine && vm.canDelete(msg)) {
                                { pendingDelete = msg }
                            } else null
                        )
                    }
                }
            }

            // 입력 바 — 하단 시스템 바 여백은 상위 Scaffold(paddingValues)가 이미 처리하므로
            // 여기서 navigationBarsPadding 을 다시 주면 이중 여백이 생긴다(제거). imePadding 만 유지.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBgTop.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(com.chaminwoo.stary.shared.config.StaryConfig.CHAT_MESSAGE_MAX_LEN) },
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder), color = TextMuted, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PageBg.copy(alpha = 0.6f),
                        unfocusedContainerColor = PageBg.copy(alpha = 0.4f),
                        focusedBorderColor = Green.copy(alpha = 0.55f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = Green,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                    )
                )
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Green else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = canSend) {
                            vm.send(input)
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = if (canSend) Color(0xFF0E1018) else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 내 메시지 완전 삭제 확인(1분 이내) — 상대방 쪽에서도 사라진다.
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = CardBgTop,
                title = { Text(stringResource(R.string.chat_delete_title), color = TextMain) },
                text = { Text(stringResource(R.string.chat_delete_confirm), color = TextMuted) },
                confirmButton = {
                    TextButton(onClick = { vm.deleteMessage(target); pendingDelete = null }) {
                        Text(stringResource(R.string.common_delete), color = Color(0xFFFF6B6B))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.common_cancel), color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isMine: Boolean, onLongPress: (() -> Unit)? = null) {
    val time = remember(msg.createdAt) {
        SimpleDateFormat("a h:mm", Locale.KOREA).format(java.util.Date(msg.createdAt))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isMine) {
            Text(time, color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine) {
                Text(
                    msg.senderName.ifBlank { stringResource(R.string.common_friend) },
                    color = TextMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp
                        )
                    )
                    .background(if (isMine) Green.copy(alpha = 0.92f) else CardBgTop)
                    .then(
                        if (onLongPress != null) Modifier.pointerInput(msg.id) {
                            detectTapGestures(onLongPress = { onLongPress() })
                        } else Modifier
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text(
                    msg.text,
                    color = if (isMine) Color(0xFF0E1018) else TextMain,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        }
        if (!isMine) {
            Spacer(Modifier.width(6.dp))
            Text(time, color = TextMuted, fontSize = 10.sp)
        }
    }
}
