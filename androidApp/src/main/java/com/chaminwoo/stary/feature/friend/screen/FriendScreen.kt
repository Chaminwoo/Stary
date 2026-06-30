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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
            items(friends, key = { "friend_${it.userId}" }) { friend ->
                PersonCard(
                    name = friend.userName,
                    photoUrl = friend.photoUrl,
                    userId = friend.userId,
                    onClick = { onOpenProfile(friend.userId, friend.userName) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(R.string.friend_chat), Icons.AutoMirrored.Filled.Chat, Green.copy(alpha = 0.16f), Green) {
                            onOpenChat(friend.userId, friend.userName)
                        }
                        Pill(stringResource(R.string.common_delete), null, Color.White.copy(alpha = 0.05f), TextMuted) {
                            vm.remove(friend.userId, friend.userName)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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

@Composable
private fun Avatar(name: String, photoUrl: String, userId: String = "") {
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
            .size(44.dp)
            .clip(CircleShape)
            .background(CardBgTop)
            .border(1.5.dp, Green.copy(alpha = 0.30f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (resolved.isNotBlank()) {
            AsyncImage(
                model = resolved,
                contentDescription = stringResource(R.string.cd_profile_photo, name.ifBlank { stringResource(R.string.common_user) }),
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
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