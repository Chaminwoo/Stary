package com.chaminwoo.stary.feature.friend.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.friend.FriendViewModel

@Composable
fun FriendScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = MaterialTheme.colorScheme.secondary)
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 검색 ---
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) vm.clearSearch()
                },
                label = { Text("이름으로 친구 찾기") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) vm.search(query) }) {
                        Icon(Icons.Filled.Search, contentDescription = "검색",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.secondary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.onBackground,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        if (isSearching) {
            item { Text("검색 중...", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
        }

        if (results.isNotEmpty()) {
            item { SectionHeader("검색 결과") }
            items(results, key = { "search_${it.userId}" }) { user ->
                val alreadyFriend = friends.any { it.userId == user.userId }
                PersonRow(
                    name = user.userName,
                    photoUrl = user.profileImageUrl,
                    trailing = {
                        if (alreadyFriend) {
                            Text("친구", color = Color(0xFF6EE7B7), fontSize = 13.sp)
                        } else {
                            IconButton(onClick = { vm.sendRequest(user) }) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = "친구 요청",
                                    tint = Color(0xFF6EE7B7))
                            }
                        }
                    }
                )
            }
        }

        // --- 받은 요청 ---
        if (requests.isNotEmpty()) {
            item { SectionHeader("받은 친구 요청 (${requests.size})") }
            items(requests, key = { "req_${it.id}" }) { req ->
                PersonRow(
                    name = req.fromName,
                    photoUrl = req.fromPhotoUrl,
                    trailing = {
                        Row {
                            IconButton(onClick = { vm.accept(req) }) {
                                Icon(Icons.Filled.Check, contentDescription = "수락",
                                    tint = Color(0xFF6EE7B7))
                            }
                            IconButton(onClick = { vm.decline(req) }) {
                                Icon(Icons.Filled.Close, contentDescription = "거절",
                                    tint = Color(0xFFFF4F4F))
                            }
                        }
                    }
                )
            }
        }

        // --- 친구 목록 ---
        item { SectionHeader("내 친구 (${friends.size})") }
        if (friends.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("아직 친구가 없어요. 이름으로 검색해 친구를 추가해보세요!",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
            }
        }
        items(friends, key = { "friend_${it.userId}" }) { friend ->
            PersonRow(
                name = friend.userName,
                photoUrl = friend.photoUrl,
                trailing = {
                    TextButton(onClick = { vm.remove(friend.userId, friend.userName) }) {
                        Text("삭제", color = Color(0xFFFF4F4F), fontSize = 13.sp)
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Spacer(modifier = Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun PersonRow(
    name: String,
    photoUrl: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).ifBlank { "?" }, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            name.ifBlank { "(이름 없음)" },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
