package com.chaminwoo.stary.feature.chat.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

/** 입력창 포커스·커서·전송 버튼 강조 — 앱 전역 남색 계열(구 민트 0xFF6EE7B7 대체). */
private val Accent = Color(0xFF9FB3E8)

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

    // 이 화면에 들어온 시각 — 이후 내가 보낸 메시지만 "방금 보낸 것"으로 보고 등장 연출을 준다
    // (스크롤을 올려 과거 메시지를 봐도 연출이 재생되지 않도록).
    val sessionStartedAt = remember { System.currentTimeMillis() }

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
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    com.chaminwoo.stary.core.ui.StaryEmptyState(
                        title = stringResource(
                            R.string.chat_empty,
                            friendName.ifBlank { stringResource(R.string.common_friend) }
                        ),
                        starType = 0,        // 4꼭지 스파클 — 첫 인사
                        starColorIndex = 9,  // 민트
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
                            // 이번 화면에서 내가 방금 보낸 메시지만 떠오르는 등장 연출(과거 메시지는 조용히).
                            justSent = mine && msg.createdAt >= sessionStartedAt,
                            onLongPress = if (mine && vm.canDelete(msg)) {
                                { pendingDelete = msg }
                            } else null
                        )
                    }
                }
            }

            // 입력 바 — 하단 여백은 **한 번만**(키보드가 있으면 키보드 높이, 없으면 내비게이션 바 높이).
            // ⚠️ navigationBarsPadding() + imePadding() 을 이어 붙이면 두 인셋이 각각 적용될 수 있어
            //    키보드 위로 내비바 높이만큼 더 떠버렸다(#5). safeDrawing 의 Bottom 만 쓰면 둘 중 큰 값 한 번.
            //    (창 자체가 밀려 올라가지 않도록 Manifest 에 windowSoftInputMode=adjustResize 를 명시했다.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBgTop.copy(alpha = 0.92f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
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
                        focusedBorderColor = Accent.copy(alpha = 0.55f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = Accent,
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
                        .background(if (canSend) Accent else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = canSend) {
                            com.chaminwoo.stary.core.util.Haptics.light()
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

/** 내 말풍선 그라데이션(파랑→남색) — 앱 전역 강조색과 같은 계열. */
private val MineBubble = Brush.linearGradient(listOf(Color(0xFF2F4C9E), Color(0xFF1B2A5E)))

/**
 * 채팅 말풍선.
 *
 * 예전엔 내 말풍선이 초록 단색이라 남색으로 개편된 앱 톤에서 혼자 튀었다. 지금은
 *  - 내 말풍선: 파랑→남색 그라데이션 + 은은한 외곽 글로우,
 *  - 삭제 가능(내 메시지 1분 이내): 말풍선 왼쪽에 **남은 시간이 줄어드는 링 타이머** —
 *    "지금 롱프레스하면 지울 수 있다"를 말없이 알려준다(기존엔 아무 표시도 없었다),
 *  - 방금 보낸 내 메시지: 아래에서 떠오르며 별가루가 흩어지는 등장 연출.
 *
 * @param justSent 이 메시지가 **이번 세션에서 내가 방금 보낸 것**인지(등장 연출 1회용).
 */
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isMine: Boolean,
    justSent: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val time = remember(msg.createdAt) {
        SimpleDateFormat("a h:mm", Locale.KOREA).format(java.util.Date(msg.createdAt))
    }

    // 등장 연출 — 방금 보낸 내 메시지만 아래에서 떠오르며 별가루가 흩어진다.
    val appear = remember(msg.id) { Animatable(if (justSent) 0f else 1f) }
    LaunchedEffect(msg.id) {
        if (justSent) appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isMine) {
            // 삭제 가능 잔여 시간 링(1분) — 다 돌면 사라진다.
            DeleteWindowRing(createdAt = msg.createdAt, visible = onLongPress != null)
            Text(time, color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine) {
                Text(
                    // 보낸 시점 스냅샷이 아니라 상대의 현재 닉네임으로 표시.
                    com.chaminwoo.stary.core.util.rememberCurrentUserName(
                        msg.senderId, msg.senderName
                    ).ifBlank { stringResource(R.string.common_friend) },
                    color = TextMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            Box(contentAlignment = Alignment.Center) {
                // 별가루 트레일 — 등장 중에만 말풍선 뒤로 흩어진다(터치 통과).
                if (isMine && appear.value < 1f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val p = appear.value
                        val fade = (1f - p).coerceIn(0f, 1f)
                        repeat(7) { i ->
                            val seed = (msg.id.hashCode() + i * 31)
                            val fx = ((seed % 100) / 100f) * size.width
                            val drift = ((seed / 100 % 40) - 20) / 20f
                            val y = size.height * (1f - p) + size.height * 0.5f
                            drawCircle(
                                color = Color(0xFF9FB3E8).copy(alpha = 0.55f * fade),
                                radius = (1.4f + (i % 3)).dp.toPx() * 0.6f,
                                center = androidx.compose.ui.geometry.Offset(
                                    fx + drift * 14.dp.toPx() * p,
                                    y + 10.dp.toPx() * p
                                )
                            )
                        }
                    }
                }

                val shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // 아래에서 떠오르며 또렷해진다(등장 연출이 없으면 값이 1이라 무동작).
                            translationY = (1f - appear.value) * 14.dp.toPx()
                            alpha = 0.25f + 0.75f * appear.value
                        }
                        .clip(shape)
                        .then(
                            if (isMine) Modifier.background(MineBubble)
                            else Modifier.background(CardBgTop)
                        )
                        .border(
                            1.dp,
                            if (isMine) Color(0xFF9FB3E8).copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.06f),
                            shape
                        )
                        .then(
                            if (onLongPress != null) Modifier.pointerInput(msg.id) {
                                detectTapGestures(onLongPress = {
                                    com.chaminwoo.stary.core.util.Haptics.light()
                                    onLongPress()
                                })
                            } else Modifier
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(
                        msg.text,
                        color = if (isMine) Color(0xFFEDF1FF) else TextMain,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        }
        if (!isMine) {
            Spacer(Modifier.width(6.dp))
            Text(time, color = TextMuted, fontSize = 10.sp)
        }
    }
}

/**
 * 삭제 가능 잔여 시간 링 — 내 메시지를 보낸 뒤 [StaryConfig.CHAT_DELETE_WINDOW_MS] 동안
 * 조금씩 줄어드는 원호. 0 이 되면 사라진다(그때부터 롱프레스 삭제도 막힌다).
 */
@Composable
private fun DeleteWindowRing(createdAt: Long, visible: Boolean) {
    if (!visible) return
    val window = com.chaminwoo.stary.shared.config.StaryConfig.CHAT_DELETE_WINDOW_MS
    var remain by remember(createdAt) {
        mutableStateOf(((createdAt + window - System.currentTimeMillis()).toFloat() / window).coerceIn(0f, 1f))
    }
    LaunchedEffect(createdAt) {
        // 1초마다 갱신 — 초 단위 표시라 더 자주 그릴 이유가 없다(배터리).
        while (remain > 0f) {
            kotlinx.coroutines.delay(1000)
            remain = ((createdAt + window - System.currentTimeMillis()).toFloat() / window).coerceIn(0f, 1f)
        }
    }
    if (remain <= 0f) return
    Canvas(modifier = Modifier.size(11.dp)) {
        drawArc(
            color = Color(0xFF9FB3E8).copy(alpha = 0.18f),
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            style = Stroke(width = 1.6.dp.toPx())
        )
        drawArc(
            color = Color(0xFF9FB3E8).copy(alpha = 0.75f),
            startAngle = -90f, sweepAngle = 360f * remain, useCenter = false,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
    Spacer(Modifier.width(5.dp))
}
