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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.CardBgTop
import com.chaminwoo.stary.core.ui.PageBg
import com.chaminwoo.stary.core.ui.TextMain
import com.chaminwoo.stary.core.ui.TextMuted
import com.chaminwoo.stary.core.ui.appCard
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.friend.FriendViewModel

private val Green = Color(0xFF6EE7B7)
private val SoftRed = Color(0xFFFF6B6B)

@Composable
fun FriendScreen(modifier: Modifier = Modifier) {
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(
            modifier = modifier.fillMaxSize().background(PageBg),
            contentAlignment = Alignment.Center
        ) {
            Text("로그인이 필요해요", color = TextMuted, fontSize = 15.sp)
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

    LaunchedEffect(Unit) {
        vm.event.collect { com.chaminwoo.stary.core.ui.StaryToast.show(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(PageBg)) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
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
                    onValueChange = {
                        query = it
                        if (it.isBlank()) vm.clearSearch()
                    },
                    onSearch = { if (query.isNotBlank()) vm.search(query) }
                )
            }

            if (isSearching) {
                item { Text("검색 중...", color = TextMuted, fontSize = 13.sp) }
            }

            // --- 검색 결과 ---
            if (results.isNotEmpty()) {
                item { SectionHeader("검색 결과", results.size) }
                items(results, key = { "search_${it.userId}" }) { user ->
                    val alreadyFriend = friends.any { it.userId == user.userId }
                    PersonCard(name = user.userName, photoUrl = user.profileImageUrl) {
                        if (alreadyFriend) {
                            StatusChip("친구")
                        } else {
                            Pill("추가", Icons.Filled.PersonAdd, Green.copy(alpha = 0.16f), Green) {
                                vm.sendRequest(user)
                            }
                        }
                    }
                }
            }

            // --- 받은 요청 ---
            if (requests.isNotEmpty()) {
                item { SectionHeader("받은 친구 요청", requests.size) }
                items(requests, key = { "req_${it.id}" }) { req ->
                    PersonCard(name = req.fromName, photoUrl = req.fromPhotoUrl) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("수락", Icons.Filled.Check, Green.copy(alpha = 0.16f), Green) { vm.accept(req) }
                            Pill("거절", Icons.Filled.Close, Color.White.copy(alpha = 0.06f), SoftRed) { vm.decline(req) }
                        }
                    }
                }
            }

            // --- 친구 목록 ---
            item { SectionHeader("내 친구", friends.size) }
            if (friends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "아직 친구가 없어요.\n이름으로 검색해 친구를 추가해보세요!",
                            color = TextMuted, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            items(friends, key = { "friend_${it.userId}" }) { friend ->
                PersonCard(name = friend.userName, photoUrl = friend.photoUrl) {
                    Pill("삭제", null, Color.White.copy(alpha = 0.05f), TextMuted) {
                        vm.remove(friend.userId, friend.userName)
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
        placeholder = { Text("이름으로 친구 찾기", color = TextMuted) },
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
private fun PersonCard(name: String, photoUrl: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().appCard(16.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, photoUrl)
        Spacer(Modifier.width(12.dp))
        Text(
            name.ifBlank { "(이름 없음)" },
            color = TextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun Avatar(name: String, photoUrl: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardBgTop)
            .border(1.5.dp, Green.copy(alpha = 0.30f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
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