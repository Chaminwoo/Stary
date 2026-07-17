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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.Mint
import com.chaminwoo.stary.core.designsystem.MintBlue
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.LocalizedNames
import com.chaminwoo.stary.core.util.RelativeTime
import com.chaminwoo.stary.core.util.UserProfileActionState
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseModerationRepository
import com.chaminwoo.stary.data.repository.HiddenAchievementRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.friend.FriendViewModel
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.rememberUserStats

private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xCC14181C)
private val AccentBrush get() = Brush.linearGradient(listOf(Mint, MintBlue))

/**
 * 타인(또는 본인) 공개 프로필 화면 — 내 프로필과 동일한 정보 구성.
 * 아바타/이름/장착 칭호 + 통계(좋아요·조회수·다이어리) + 업적 진행도 + 그 사람의 다이어리 목록.
 * 친구 상태별 액션(본인=내 프로필 / 친구=채팅 / 그 외=친구 추가)도 함께 제공.
 */
@Composable
fun UserProfileScreen(
    userId: String,
    userName: String,
    modifier: Modifier = Modifier,
    onOpenDiary: (String) -> Unit = {},
    onOpenDiaryStars: (userId: String, userName: String) -> Unit = { _, _ -> },
    onOpenChat: (friendId: String, friendName: String) -> Unit = { _, _ -> },
) {
    val myId = GoogleAuthHelper.currentUserId

    // 대상의 공개 프로필(사진/이름/장착 칭호) 로드.
    var photoUrl by remember(userId) { mutableStateOf("") }
    var resolvedName by remember(userId) { mutableStateOf(userName) }
    var equippedTitleId by remember(userId) { mutableStateOf("") }
    LaunchedEffect(userId) {
        FirebaseFriendRepository().getProfile(userId)?.let {
            photoUrl = it.profileImageUrl
            if (it.userName.isNotBlank()) resolvedName = it.userName
            equippedTitleId = it.equippedTitle
        }
    }
    // 칭호는 언어 전환에 맞춰 표시(로케일 해석)
    val equippedTitleName = LocalizedNames.equippedTitle(
        androidx.compose.ui.platform.LocalContext.current, equippedTitleId.ifBlank { null }
    )

    // 통계/업적/다이어리 — 대상 userId 기준.
    val diaryVm: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
    val stats = rememberUserStats(userId, diaryVm)
    val theirDiaries by diaryVm.getMyDiaries(userId).collectAsState()
    val unlockedCount = remember(stats) { Achievements.unlockedIds(stats).size }
    val totalCount = Achievements.all.size

    // 그 사람이 프로필에 띄운(핀) 다이어리 — 별로 떠 있고 탭하면 위치(지도)로.
    var pinnedIds by remember(userId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(userId) { pinnedIds = FirebaseFriendRepository().getPinnedDiaries(userId) }
    val pinnedDiaries = remember(theirDiaries, pinnedIds) {
        pinnedIds.mapNotNull { id -> theirDiaries.find { it.id == id } }
    }

    // 그 사람이 달성한 히든 업적 — 프로필에 떠다니는 전용 아이콘(오라/파티클)으로 표시.
    val hiddenRepo = remember { HiddenAchievementRepository() }
    val hiddenClaims by remember { hiddenRepo.observe() }.collectAsState(initial = emptyMap())
    val theirHiddenAch = remember(hiddenClaims, userId) {
        hiddenClaims.filterValues { it.achieverId == userId }.keys
            .mapNotNull { com.chaminwoo.stary.feature.profile.HiddenAchievements.byId(it) }
    }

    // 친구 상태/요청 — 기존 FriendViewModel 재사용.
    val me = remember {
        UserProfile(
            userId = myId ?: "",
            userName = GoogleAuthHelper.currentUserName ?: "",
            profileImageUrl = GoogleAuthHelper.currentUserPhotoUrl ?: ""
        )
    }
    val vm: FriendViewModel = viewModel(factory = FriendViewModel.factory(me))
    val friends by vm.friends.collectAsState()
    LaunchedEffect(Unit) {
        vm.event.collect { com.chaminwoo.stary.core.ui.StaryToast.show(it) }
    }

    val isMe = myId != null && myId == userId
    val isFriend = friends.any { it.userId == userId }
    var requested by remember(userId) { mutableStateOf(false) }
    var showCancelDialog by remember(userId) { mutableStateOf(false) }

    // 차단/신고
    val scope = rememberCoroutineScope()
    val moderation = remember { FirebaseModerationRepository() }
    val blockedIds by remember(myId) {
        if (myId != null) moderation.observeBlockedIds(myId) else kotlinx.coroutines.flow.flowOf(emptySet())
    }.collectAsState(initial = emptySet())
    val isBlocked = blockedIds.contains(userId)
    var showReportDialog by remember(userId) { mutableStateOf(false) }
    val blockedMsg = stringResource(R.string.toast_blocked)
    val unblockedMsg = stringResource(R.string.toast_unblocked)
    val reportedMsg = stringResource(R.string.toast_reported)

    // 친구/신고/차단 액션을 탑바(MainScreen) 우측 아이콘·메뉴로 그리도록 전역 브리지에 등록 — 본인 아닐 때만.
    SideEffect {
        UserProfileActionState.visible = !isMe
        UserProfileActionState.isFriend = isFriend
        UserProfileActionState.requested = requested
        UserProfileActionState.isBlocked = isBlocked
        UserProfileActionState.onClick = {
            when {
                isFriend -> showCancelDialog = true                 // 친구면 취소 확인창
                requested -> {}                                      // 이미 요청함 → 무시
                else -> {                                            // 친구 요청 전송(+토스트는 vm.event)
                    vm.sendRequest(UserProfile(userId, resolvedName, photoUrl))
                    requested = true
                }
            }
        }
        UserProfileActionState.onReport = { showReportDialog = true }
        UserProfileActionState.onToggleBlock = {
            val mine = myId
            if (mine != null) scope.launch {
                if (isBlocked) {
                    moderation.unblock(mine, userId)
                    com.chaminwoo.stary.core.ui.StaryToast.show(unblockedMsg)
                } else {
                    moderation.block(mine, userId, resolvedName)
                    if (isFriend) vm.remove(userId, resolvedName) // 차단 시 친구도 해제
                    com.chaminwoo.stary.core.ui.StaryToast.show(blockedMsg)
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { UserProfileActionState.reset() } // 화면 이탈 시 탑바 버튼 숨김
    }

    // 공개 범위에 맞춰 노출할 다이어리만(타인의 비공개/친구공개 보호).
    val visibleDiaries = remember(theirDiaries, isMe, isFriend) {
        theirDiaries.filter { d ->
            when (d.visibilityType) {
                "private" -> isMe
                "friends" -> isMe || isFriend
                else -> true
            }
        }
    }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 40.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 헤더: 아바타 + 이름 + 칭호 + 친구 액션 ──
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(150.dp).background(
                                Brush.radialGradient(
                                    listOf(
                                        Mint.copy(alpha = 0.28f),
                                        Color.Transparent
                                    ), radius = 210f
                                ),
                                CircleShape
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, AccentBrush, CircleShape)
                                .background(Color(0xFF0D0D0D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUrl.isNotBlank()) {
                                com.chaminwoo.stary.core.ui.ThumbAsyncImage(
                                    model = photoUrl,
                                    contentDescription = stringResource(
                                        R.string.cd_profile_photo,
                                        resolvedName.ifBlank { stringResource(R.string.common_user) }),
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    sizePx = 384,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = stringResource(R.string.cd_default_profile),
                                    tint = Color(0xFF555555),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = resolvedName.ifBlank { stringResource(R.string.friend_no_name) },
                            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextMain
                        )
                        com.chaminwoo.stary.core.ui.HiddenStarBadges(
                            userId = userId,
                            modifier = Modifier.padding(start = 8.dp),
                            size = 20.dp,
                        )
                    }

                    // 장착 칭호 — 히든 칭호는 금색 + 『 』 로 감싸 일반 칭호와 구분.
                    Spacer(Modifier.height(12.dp))
                    val isHiddenTitle = com.chaminwoo.stary.feature.profile.HiddenAchievements.byId(equippedTitleId.ifBlank { null }) != null
                    val titleColor = when {
                        equippedTitleName == null -> TextMuted
                        isHiddenTitle -> Color(0xFFFFD86F)
                        else -> Mint
                    }
                    Text(
                        text = when {
                            equippedTitleName == null -> stringResource(R.string.user_no_title)
                            isHiddenTitle -> "『$equippedTitleName』"
                            else -> equippedTitleName
                        },
                        color = titleColor,
                        fontSize = 15.sp, fontWeight = if (isHiddenTitle) FontWeight.SemiBold else FontWeight.Light,
                        style = LocalTextStyle.current.merge(
                            TextStyle(
                                shadow = Shadow(
                                    titleColor.copy(alpha = 0.95f),
                                    blurRadius = if (isHiddenTitle) 32f else 24f
                                )
                            )
                        )
                    )

                    // (친구 추가/취소는 탑바 버튼, 채팅은 통계 옆 편지 아이콘 — 본문 액션 없음)
                    Spacer(Modifier.height(26.dp))
                }
            }

            // 통계 떠다니는 아이콘은 전체화면 오버레이로(아래) — 여기선 자리만 비워둔다.
            item { Spacer(Modifier.height(130.dp)) }

            // (업적·칭호는 통계의 트로피 아이콘으로, 다이어리는 '다이어리' 아이콘 탭으로 — 카드 없음)
        }

        // ── 떠다니는 통계 오버레이 — 하트=버스트 / 친구 / 다이어리→별 / 편지=채팅 / 핀 별=위치 ──
        val chatNeedFriendMsg = stringResource(R.string.chat_need_friend)
        val untitled = stringResource(R.string.common_untitled)
        FloatingStatBox(
            items = listOf(
                StatBubble(Icons.Filled.Favorite, stats.likesReceived, Color(0xFFE7556B), stringResource(R.string.profile_stat_likes), burstOnTap = true),
                StatBubble(Icons.Filled.Person, stats.friends, Color(0xFF6EE7B7), stringResource(R.string.common_friend)),
                StatBubble(Icons.AutoMirrored.Filled.MenuBook, stats.diariesCreated, Color(0xFFF7E067), stringResource(R.string.profile_stat_diaries)),
                StatBubble(Icons.Filled.Email, 0, MintBlue, stringResource(R.string.user_chat_action), showCount = false), // 파란 편지=채팅
                StatBubble(Icons.Filled.EmojiEvents, unlockedCount, Color(0xFFF2C94C), stringResource(R.string.nav_achievements), burstOnTap = true), // 업적(누르면 버스트)
            ) + pinnedDiaries.map { d ->
                StatBubble(
                    Icons.Filled.Favorite, 0, StarStyle.colorOf(d.starColor), d.title.ifBlank { untitled },
                    showCount = false, starType = d.starType, starColorIndex = d.starColor
                )
            } + theirHiddenAch.map { ach ->
                StatBubble(
                    ach.icon.vector, 0, ach.icon.color, ach.title,
                    burstOnTap = true, showCount = false, hiddenEffect = ach.effect
                )
            },
            onTap = { idx ->
                val pinnedStart = 5
                val hiddenStart = pinnedStart + pinnedDiaries.size
                when {
                    idx == 2 -> onOpenDiaryStars(userId, resolvedName)
                    idx == 3 -> if (isFriend) onOpenChat(userId, resolvedName)
                                else com.chaminwoo.stary.core.ui.StaryToast.show(chatNeedFriendMsg)
                    idx in pinnedStart until hiddenStart -> pinnedDiaries.getOrNull(idx - pinnedStart)?.id?.let { onOpenDiary(it) }
                    // 히든 아이콘(idx >= hiddenStart)은 탭 시 버스트만(타인 프로필이라 업적 화면 이동 없음).
                }
            },
            avoidCenterYFraction = 0.25f // 상단의 프로필/이름 회피
        )

        // 친구 취소 확인 다이얼로그 — 탑바의 "친구(사람✓)" 버튼을 누르면 뜬다.
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                containerColor = Color(0xFF14181C),
                titleContentColor = TextMain,
                textContentColor = TextMuted,
                title = { Text(stringResource(R.string.user_unfriend_title)) },
                text = {
                    Text(stringResource(R.string.user_unfriend_confirm, resolvedName.ifBlank { stringResource(R.string.common_user) }))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCancelDialog = false
                        vm.remove(userId, resolvedName) // 토스트는 vm.event 가 처리
                    }) { Text(stringResource(R.string.user_unfriend_yes), color = Color(0xFFFF6B6B)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text(stringResource(R.string.user_unfriend_no), color = Mint)
                    }
                }
            )
        }

        if (showReportDialog) {
            com.chaminwoo.stary.core.ui.ReportDialog(
                title = stringResource(R.string.report_user),
                onDismiss = { showReportDialog = false },
                onSubmit = { reason ->
                    showReportDialog = false
                    scope.launch {
                        moderation.report(myId ?: "", "user", userId, userId, reason)
                        com.chaminwoo.stary.core.ui.StaryToast.show(reportedMsg)
                    }
                }
            )
        }
    }
}

/** 다이어리 한 줄 — 별 모양/색 + 제목/시간 + 좋아요·조회수. 탭하면 상세로. */
@Composable
private fun DiaryRow(d: Diary, onClick: () -> Unit) {
    val timeStr = remember(d.createdAt) { RelativeTime.format(d.createdAt) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Brush.linearGradient(listOf(Mint.copy(alpha = 0.30f), MintBlue.copy(alpha = 0.20f))), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StarShapeIcon(type = d.starType, colorIndex = d.starColor, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                d.title.ifBlank { stringResource(R.string.common_untitled) },
                color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(timeStr, color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        MetaCount(Icons.Filled.Favorite, d.likeCount, Color(0xFFE7556B))
        Spacer(Modifier.width(8.dp))
        MetaCount(Icons.Filled.Visibility, d.viewCount, TextMuted)
    }
}

@Composable
private fun MetaCount(icon: ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text("$count", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AccentBrush)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF0D0D0D), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF0D0D0D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Mint.copy(alpha = 0.12f))
            .border(1.dp, Mint.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun GradientCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Brush.linearGradient(listOf(Mint.copy(alpha = 0.55f), MintBlue.copy(alpha = 0.45f))), RoundedCornerShape(18.dp))
    ) { content() }
}

@Composable
private fun StatCell(label: String, value: String, iconTint: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.08f)))
}
