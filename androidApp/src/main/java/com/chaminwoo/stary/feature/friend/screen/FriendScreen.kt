package com.chaminwoo.stary.feature.friend.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Group
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.tasks.await
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.CardBgTop
import com.chaminwoo.stary.core.ui.PageBg
import com.chaminwoo.stary.core.ui.TextMain
import com.chaminwoo.stary.core.ui.TextMuted
import com.chaminwoo.stary.core.ui.appCard
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.friend.FriendViewModel

private val Green = com.chaminwoo.stary.core.designsystem.Mint
private val SoftRed = Color(0xFFFF6B6B)

@Composable
fun FriendScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (friendId: String, friendName: String) -> Unit = { _, _ -> },
    onOpenProfile: (userId: String, userName: String) -> Unit = { _, _ -> },
) {
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(
            modifier = modifier.fillMaxSize().background(PageBg),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.common_login_required), color = TextMuted, fontSize = 15.sp)
        }
        return
    }

    val context = LocalContext.current
    val me = remember {
        UserProfile(
            userId = userId,
            userName = GoogleAuthHelper.currentUserName ?: "",
            profileImageUrl = GoogleAuthHelper.currentUserPhotoUrl ?: ""
        )
    }
    val vm: FriendViewModel = viewModel(factory = FriendViewModel.factory(me))
    val friends by vm.friends.collectAsState()
    val requests by vm.incomingRequests.collectAsState()
    val results by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    // 채팅방 메타(마지막 메시지/시각) — 친구 행의 미리보기·미읽음 점에 사용.
    val chatRepo = remember { com.chaminwoo.stary.data.repository.FirebaseChatRepository() }
    val chatSummaries by remember(userId) { chatRepo.observeMyChats(userId) }
        .collectAsState(initial = emptyList())
    val summaryByFriend = remember(chatSummaries) {
        chatSummaries.associateBy { c -> c.participants.firstOrNull { it != userId } ?: "" }
    }
    var query by remember { mutableStateOf("") }
    // 현재 query 로 검색이 실제 디스패치됐는지 추적 — '결과 없음' 표시를 디바운스 중 깜빡임 없이 띄우기 위함.
    var lastSearched by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.event.collect { com.chaminwoo.stary.core.ui.StaryToast.show(it) }
    }

    // 입력하면 타이핑 멈춘 뒤(350ms) 자동 검색 — 매번 엔터를 누르지 않아도 결과가 갱신된다.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            vm.clearSearch()
            lastSearched = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        vm.search(q)
        lastSearched = q
    }

    Box(modifier = modifier.fillMaxSize().background(PageBg)) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        FirstVisitInfo(
            seenKey = "info_friends",
            icon = Icons.Filled.Group,
            title = stringResource(R.string.onb_friends_title),
            message = stringResource(R.string.onb_friends_msg),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- 검색 ---
            item {
                SearchField(
                    query = query,
                    onValueChange = { query = it },
                    onSearch = { if (query.isNotBlank()) vm.search(query) }
                )
            }

            // --- 친구 초대(체크리스트 31) — 초대 링크 공유. 가입+리딤 시 양쪽 다 칭호 보상 ---
            item {
                InviteCard(
                    onClick = {
                        val text = context.getString(
                            R.string.invite_share_text,
                            StaryConfig.inviteLink(userId)
                        )
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(send, context.getString(R.string.invite_friends))
                        )
                    }
                )
            }

            if (isSearching) {
                item { Text(stringResource(R.string.friend_searching), color = TextMuted, fontSize = 13.sp) }
            }

            // --- 검색 결과 ---
            if (results.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.friend_search_results), results.size) }
                items(results, key = { "search_${it.userId}" }) { user ->
                    val alreadyFriend = friends.any { it.userId == user.userId }
                    PersonCard(
                        name = user.userName,
                        photoUrl = user.profileImageUrl,
                        userId = user.userId,
                        onClick = { onOpenProfile(user.userId, user.userName) }
                    ) {
                        if (alreadyFriend) {
                            StatusChip(stringResource(R.string.friend_status_friend))
                        } else {
                            Pill(stringResource(R.string.friend_add), Icons.Filled.PersonAdd, Green.copy(alpha = 0.16f), Green) {
                                vm.sendRequest(user)
                            }
                        }
                    }
                }
            } else if (lastSearched != null && lastSearched == query.trim() && !isSearching) {
                // 검색은 했는데 결과가 없을 때
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.friend_no_results, query.trim()), color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            // --- 받은 요청 ---
            if (requests.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.friend_requests), requests.size) }
                items(requests, key = { "req_${it.id}" }) { req ->
                    PersonCard(
                        name = req.fromName,
                        photoUrl = req.fromPhotoUrl,
                        userId = req.fromId,
                        onClick = { onOpenProfile(req.fromId, req.fromName) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(stringResource(R.string.friend_accept), Icons.Filled.Check, Green.copy(alpha = 0.16f), Green) { vm.accept(req) }
                            Pill(stringResource(R.string.friend_decline), Icons.Filled.Close, Color.White.copy(alpha = 0.06f), SoftRed) { vm.decline(req) }
                        }
                    }
                }
            }

            // --- 친구 목록 ---
            item { SectionHeader(stringResource(R.string.friend_my_friends), friends.size) }
            if (friends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.friend_empty),
                            color = TextMuted, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            // 메신저형 친구 행(2026-07-12 개편) — 채팅/삭제 버튼 없이
            // [사진] [이름 / 마지막 채팅 ㆍ상대시간] [미읽음 파란 점]. 행 탭=채팅, 사진 탭=프로필.
            items(friends, key = { "friend_${it.userId}" }) { friend ->
                val chatId = StaryConfig.chatId(userId, friend.userId)
                val summary = summaryByFriend[friend.userId]
                val lastReadAt = com.chaminwoo.stary.core.util.ChatReadStore.lastReadAt(context, chatId)
                val unread = summary != null && summary.lastMessage.isNotBlank() &&
                    summary.lastSenderId != userId && summary.updatedAt > lastReadAt
                FriendRow(
                    name = friend.userName,
                    photoUrl = friend.photoUrl,
                    userId = friend.userId,
                    lastMessage = summary?.lastMessage.orEmpty(),
                    lastAt = summary?.updatedAt ?: 0L,
                    unread = unread,
                    onOpenProfile = { onOpenProfile(friend.userId, friend.userName) },
                    onClick = {
                        com.chaminwoo.stary.core.util.ChatReadStore.markRead(context, chatId)
                        onOpenChat(friend.userId, friend.userName)
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/** 친구 초대 카드 — 탭하면 초대 링크를 공유 시트로 보낸다(체크리스트 31). */
@Composable
private fun InviteCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(16.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Green.copy(alpha = 0.14f))
                .border(1.dp, Green.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.invite_friends),
                color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.invite_friends_desc),
                color = TextMuted, fontSize = 12.sp
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
private fun SearchField(query: String, onValueChange: (String) -> Unit, onSearch: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.friend_search_placeholder), color = TextMuted) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(); keyboard?.hide() }),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardBgTop.copy(alpha = 0.85f),
            unfocusedContainerColor = CardBgTop.copy(alpha = 0.6f),
            focusedBorderColor = Green.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            cursorColor = Green,
            focusedTextColor = TextMain,
            unfocusedTextColor = TextMain,
        )
    )
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
        Spacer(Modifier.width(8.dp))
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Green.copy(alpha = 0.14f))
                .border(1.dp, Green.copy(alpha = 0.30f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) {
            Text("$count", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PersonCard(
    name: String,
    photoUrl: String,
    userId: String = "",
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().appCard(16.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아바타+이름 영역 탭 → 프로필 진입(trailing 버튼들과 별개로 동작).
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name, photoUrl, userId)
            Spacer(Modifier.width(12.dp))
            Text(
                name.ifBlank { stringResource(R.string.friend_no_name) },
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/**
 * 친구 행(메신저형) — [프로필 사진] [이름 / 마지막 채팅 ㆍ상대시간] [미읽음 파란 점].
 * 행 탭 = 채팅 열기, 사진 탭 = 프로필 열기. 사진은 텍스트 2줄보다 조금 크게(52dp).
 */
@Composable
private fun FriendRow(
    name: String,
    photoUrl: String,
    userId: String,
    lastMessage: String,
    lastAt: Long,
    unread: Boolean,
    onOpenProfile: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(16.dp)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, photoUrl, userId, size = 52.dp, onClick = onOpenProfile)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name.ifBlank { stringResource(R.string.friend_no_name) },
                color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (lastMessage.isNotBlank()) {
                Text(
                    "$lastMessage · ${com.chaminwoo.stary.core.util.RelativeTime.format(lastAt)}",
                    color = TextMuted, fontSize = 12.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    stringResource(R.string.friend_no_chat_yet),
                    color = TextMuted.copy(alpha = 0.7f), fontSize = 12.5.sp, maxLines = 1
                )
            }
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4C8DFF))
            )
        }
    }
}

@Composable
private fun Avatar(
    name: String,
    photoUrl: String,
    userId: String = "",
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    // photoUrl 이 비어 있으면(예전 친구 데이터) users/{userId}.profileImageUrl 을 조회해 채운다.
    var resolved by remember(userId, photoUrl) { mutableStateOf(photoUrl) }
    LaunchedEffect(userId, photoUrl) {
        if (photoUrl.isBlank() && userId.isNotBlank()) {
            val url = try {
                com.chaminwoo.stary.data.staryFirestore
                    .collection(StaryConfig.Collections.USERS).document(userId)
                    .get().await().getString("profileImageUrl")
            } catch (_: Exception) { null }
            if (!url.isNullOrBlank()) resolved = url
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CardBgTop)
            .border(1.5.dp, Green.copy(alpha = 0.30f), CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (resolved.isNotBlank()) {
            // 작은 아바타 — 96px 다운샘플 디코드로 목록 스크롤에서도 즉시 뜨게.
            com.chaminwoo.stary.core.ui.ThumbAsyncImage(
                model = resolved,
                contentDescription = stringResource(R.string.cd_profile_photo, name.ifBlank { stringResource(R.string.common_user) }),
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                sizePx = 96,
            )
        } else {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                color = Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    icon: ImageVector?,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = text, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = contentColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Green.copy(alpha = 0.10f))
            .border(1.dp, Green.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Green, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}