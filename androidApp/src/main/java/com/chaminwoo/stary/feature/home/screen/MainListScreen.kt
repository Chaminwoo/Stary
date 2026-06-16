package com.chaminwoo.stary.feature.home.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.map.screen.DiaryMap
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private data class FilterOpt(
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun MainListScreen(
    onItemClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    val context = LocalContext.current
    val diaries by diaryViewModel.diaries.collectAsState()
    val step = 0.0001
    val focusRequester = remember { FocusRequester() }

    var currentLatLng by remember {
        mutableStateOf(LocationHelper.getCurrentLatLng() ?: LatLng(StaryConfig.DEFAULT_LAT, StaryConfig.DEFAULT_LNG))
    }

    val userId = GoogleAuthHelper.currentUserId
    var unviewedOnly by remember { mutableStateOf(false) }
    var friendsOnly by remember { mutableStateOf(false) }
    var myOnly by remember { mutableStateOf(false) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var selectedFriendIds by remember { mutableStateOf(emptySet<String>()) }
    var speedDialExpanded by remember { mutableStateOf(false) }

    val viewedIds by remember(userId) {
        if (userId != null) FirebaseViewedRepository().observeViewedIds(userId)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())

    val friends by remember(userId) {
        if (userId != null) FirebaseFriendRepository().observeFriends(userId)
        else flowOf(emptyList<Friend>())
    }.collectAsState(initial = emptyList())

    val friendIds = remember(friends) { friends.map { it.userId }.toSet() }

    val filteredDiaries = remember(diaries, unviewedOnly, friendsOnly, myOnly, selectedFriendIds, viewedIds, friendIds, userId) {
        diaries.filter { diary ->
            val visibilityOk = diary.visibilityType != "friends" ||
                diary.userId == userId || diary.userId in friendIds
            val filterOk = (!unviewedOnly || diary.id !in viewedIds) &&
                (!friendsOnly || diary.userId in friendIds) &&
                (!myOnly || diary.userId == userId) &&
                (selectedFriendIds.isEmpty() || diary.userId in selectedFriendIds)
            visibilityOk && filterOk
        }
    }

    // 친구 선택 다이얼로그
    if (showFriendPicker) {
        var tempSelected by remember { mutableStateOf(selectedFriendIds) }
        AlertDialog(
            onDismissRequest = { showFriendPicker = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("친구 선택", color = Color(0xFFF0F0F0), fontSize = 16.sp) },
            text = {
                if (friends.isEmpty()) {
                    Text("친구가 없어요", color = Color(0xFF8A8A8A), fontSize = 14.sp)
                } else {
                    Column {
                        friends.forEach { friend ->
                            val checked = friend.userId in tempSelected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        tempSelected = if (it) tempSelected + friend.userId
                                        else tempSelected - friend.userId
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF6EE7B7),
                                        uncheckedColor = Color(0xFF8A8A8A)
                                    )
                                )
                                Text(
                                    friend.userName.ifBlank { friend.userId.take(8) },
                                    color = Color(0xFFF0F0F0), fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFriendIds = tempSelected; showFriendPicker = false }) {
                    Text("적용", color = Color(0xFF6EE7B7))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFriendPicker = false }) {
                    Text("취소", color = Color(0xFF8A8A8A))
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(context, "위치 권한이 필요해요", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(currentLatLng) {
        delay(600)
        diaryViewModel.prefetchNearby(diaries, currentLatLng.latitude, currentLatLng.longitude)
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
            return@LaunchedEffect
        }
        LocationHelper.startContinuousUpdates(context)
        val latLng = LocationHelper.getCurrentLatLng()
            ?: LocationHelper.getCurrentLocation(context)?.let { LatLng(it.latitude, it.longitude) }
        latLng?.let { currentLatLng = it }
        focusRequester.requestFocus()
    }

    fun moveLocation(latDelta: Double, lngDelta: Double) {
        val newLatLng = LatLng(currentLatLng.latitude + latDelta, currentLatLng.longitude + lngDelta)
        currentLatLng = newLatLng
        LocationHelper.setCurrentLocation(newLatLng)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.W -> { moveLocation(step, 0.0); true }
                        Key.S -> { moveLocation(-step, 0.0); true }
                        Key.A -> { moveLocation(0.0, -step); true }
                        Key.D -> { moveLocation(0.0, step); true }
                        else -> false
                    }
                } else false
            }
    ) {
        DiaryMap(
            diaries = filteredDiaries,
            currentLatLng = currentLatLng,
            onDiaryClick = onItemClick,
            onCreateClick = onCreateClick,
        )

        // 필터 스피드 다이얼 (로그인한 경우)
        if (userId != null) {
            val anyActive = unviewedOnly || friendsOnly || myOnly || selectedFriendIds.isNotEmpty()
            val mint = Color(0xFF6EE7B7)
            val pillBg = Color(0xEE111120)

            val filterOpts = listOf(
                FilterOpt("전체보기", Icons.Filled.Public, !anyActive) {
                    unviewedOnly = false; friendsOnly = false; myOnly = false
                    selectedFriendIds = emptySet(); speedDialExpanded = false
                },
                FilterOpt("미조회만", Icons.Filled.Visibility, unviewedOnly) {
                    unviewedOnly = !unviewedOnly; if (unviewedOnly) myOnly = false
                },
                FilterOpt("친구만", Icons.Filled.People, friendsOnly) {
                    friendsOnly = !friendsOnly; if (friendsOnly) myOnly = false
                },
                FilterOpt("나만보기", Icons.Filled.Lock, myOnly) {
                    myOnly = !myOnly; if (myOnly) { friendsOnly = false; selectedFriendIds = emptySet() }
                },
                FilterOpt(
                    if (selectedFriendIds.isEmpty()) "친구 선택" else "친구 ${selectedFriendIds.size}명",
                    Icons.Filled.GroupAdd, selectedFriendIds.isNotEmpty()
                ) { showFriendPicker = true },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 88.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 옵션 목록 (위로 펼쳐짐)
                AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        filterOpts.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (opt.isActive) mint.copy(alpha = 0.18f) else pillBg)
                                    .border(
                                        width = if (opt.isActive) 1.5.dp else 1.dp,
                                        color = if (opt.isActive) mint else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { opt.onClick() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = if (opt.isActive) mint else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = opt.label,
                                    color = if (opt.isActive) mint else Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 메인 FAB 원형 버튼
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.5.dp, if (anyActive) mint else Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { speedDialExpanded = !speedDialExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "필터",
                        tint = if (anyActive) mint else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DpadButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 18.sp, color = Color.White)
    }
}
