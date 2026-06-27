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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.Mint
import com.chaminwoo.stary.core.designsystem.MintBlue
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.RelativeTime
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
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
    val equippedTitleName = Achievements.byId(equippedTitleId.ifBlank { null })?.titleName

    // 통계/업적/다이어리 — 대상 userId 기준.
    val diaryVm: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
    val stats = rememberUserStats(userId, diaryVm)
    val theirDiaries by diaryVm.getMyDiaries(userId).collectAsState()
    val unlockedCount = remember(stats) { Achievements.unlockedIds(stats).size }
    val totalCount = Achievements.all.size

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
                                Brush.radialGradient(listOf(Mint.copy(alpha = 0.28f), Color.Transparent), radius = 210f),
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
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "${resolvedName.ifBlank { "사용자" }} 프로필 사진",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AccountCircle, contentDescription = "기본 프로필",
                                    tint = Color(0xFF555555), modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = resolvedName.ifBlank { "(이름 없음)" },
                        fontSize = 23.sp, fontWeight = FontWeight.Bold, color = TextMain
                    )

                    // 장착 칭호 칩
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Mint.copy(alpha = 0.12f))
                            .border(1.dp, Mint.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Mint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = equippedTitleName ?: "칭호 없음",
                            color = if (equippedTitleName != null) Mint else TextMuted,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }

                    // 친구 액션
                    Spacer(Modifier.height(18.dp))
                    when {
                        isMe -> StatusChip("내 프로필")
                        isFriend -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip("친구", Icons.Filled.Check)
                            ActionButton("채팅하기", Icons.AutoMirrored.Filled.Chat) { onOpenChat(userId, resolvedName) }
                        }
                        requested -> StatusChip("요청됨", Icons.Filled.Check)
                        else -> ActionButton("친구 추가", Icons.Filled.PersonAdd) {
                            vm.sendRequest(UserProfile(userId, resolvedName, photoUrl))
                            requested = true
                        }
                    }

                    Spacer(Modifier.height(26.dp))
                }
            }

            // ── 통계 카드 ──
            item {
                GradientCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatCell("좋아요", stats.likesReceived.toString(), Color(0xFFE7556B), Icons.Filled.Favorite, Modifier.weight(1f))
                        StatDivider()
                        StatCell("친구", stats.friends.toString(), Color(0xFF6EE7B7), Icons.Filled.People, Modifier.weight(1f))
                        StatDivider()
                        StatCell("다이어리", stats.diariesCreated.toString(), Color(0xFFF7E067), Icons.Filled.Star, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── 업적 진행 카드 (표시 전용) ──
            item {
                GradientCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Mint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("업적 · 칭호", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("$unlockedCount / $totalCount", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            val frac = if (totalCount == 0) 0f else unlockedCount.toFloat() / totalCount
                            Box(
                                Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(7.dp)
                                    .clip(RoundedCornerShape(50)).background(AccentBrush)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── 다이어리 목록 ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Mint))
                    Spacer(Modifier.width(8.dp))
                    Text("다이어리", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("${visibleDiaries.size}", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (visibleDiaries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("볼 수 있는 다이어리가 없어요", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(visibleDiaries, key = { it.id }) { d ->
                    DiaryRow(d) { onOpenDiary(d.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }
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
                d.title.ifBlank { "(제목 없음)" },
                color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium,
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
        Text(text, color = Color(0xFF0D0D0D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        Text(text, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
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
