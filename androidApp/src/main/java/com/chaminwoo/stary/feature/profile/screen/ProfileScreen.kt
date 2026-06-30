package com.chaminwoo.stary.feature.profile.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.ProfileViewModel
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.profile.rememberUserStats
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ProfilePinState
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import kotlinx.coroutines.launch

private val Green = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xCC14181C)
private val AccentBrush get() = Brush.linearGradient(listOf(Green, Blue))

@Composable
fun ProfileScreen(
    onOpenAchievements: () -> Unit,
    onLogout: () -> Unit,
    onOpenMyDiary: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenDiary: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_login_required), color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(userId))
    val profileImageUrl by profileVm.profileImageUrl.collectAsState()
    val isUploading by profileVm.isUploading.collectAsState()
    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { profileVm.uploadProfileImage(it) }
    }
    val uploadError by profileVm.uploadError.collectAsState()
    LaunchedEffect(uploadError) {
        uploadError?.let {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.profile_upload_failed, it))
            profileVm.clearError()
        }
    }

    val stats = rememberUserStats(userId)
    val equippedStigmaId = StigmaStore.equipped(context, userId)
    val equippedStigma = Achievements.byId(equippedStigmaId)?.titleName

    // 프로필에 띄울(핀) 다이어리 — 최대 3개. "+"로 선택, 별 모양으로 떠다니고 탭하면 위치(지도)로.
    val diaryVm: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
    val myDiaries by diaryVm.getMyDiaries(userId).collectAsState()
    var pinnedIds by remember(userId) { mutableStateOf<List<String>>(emptyList()) }
    var showPinPicker by remember { mutableStateOf(false) }
    LaunchedEffect(userId) {
        pinnedIds = com.chaminwoo.stary.data.repository.FirebaseFriendRepository().getPinnedDiaries(userId)
    }
    val pinnedDiaries = remember(myDiaries, pinnedIds) {
        pinnedIds.mapNotNull { id -> myDiaries.find { it.id == id } }
    }
    // 탑바 "+" 버튼이 핀 picker 를 열도록 등록(별 올리기 = 탑바 +).
    SideEffect {
        ProfilePinState.visible = true
        ProfilePinState.onOpen = { showPinPicker = true }
    }
    DisposableEffect(Unit) { onDispose { ProfilePinState.reset() } }
    // 내가 장착한 칭호를 공개 프로필에 백필 동기화(이전에 장착해 둔 값도 타인에게 보이도록).
    LaunchedEffect(userId, equippedStigmaId) {
        com.chaminwoo.stary.data.repository.FirebaseFriendRepository()
            .setEquippedTitle(userId, equippedStigmaId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        FirstVisitInfo(
            seenKey = "info_profile",
            icon = Icons.Filled.Person,
            title = stringResource(R.string.onb_profile_title),
            message = stringResource(R.string.onb_profile_msg),
        )

        val unlockedCount = remember(stats) { Achievements.unlockedIds(stats).size }
        val totalCount = Achievements.all.size

        // ── 중앙: 아바타 + 이름 + 칭호 (위아래 빈 공간 비슷하게 수직 중앙) ──
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-44).dp)   // 화면 가운데보다 살짝 위
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 아바타 (뒤 글로우 + 그라데이션 링) ──
            Box(
                modifier = Modifier.size(188.dp),
                contentAlignment = Alignment.Center
            ) {
                // 부드러운 후광
                Box(
                    Modifier
                        .size(188.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Green.copy(alpha = 0.28f), Color.Transparent),
                                radius = 270f
                            ),
                            CircleShape
                        )
                )
                // 그라데이션 링
                Box(
                    modifier = Modifier
                        .size(154.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, AccentBrush, CircleShape)
                        .background(Color(0xFF0D0D0D), CircleShape)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isUploading -> CircularProgressIndicator(color = Green, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        profileImageUrl != null -> AsyncImage(profileImageUrl, stringResource(R.string.nav_profile), Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        GoogleAuthHelper.currentUserPhotoUrl != null -> AsyncImage(GoogleAuthHelper.currentUserPhotoUrl, stringResource(R.string.nav_profile), Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else -> Icon(Icons.Filled.AccountCircle, stringResource(R.string.cd_default_profile), tint = Color(0xFF555555), modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = GoogleAuthHelper.currentUserName ?: userId.take(12),
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextMain
            )

            // 장착 칭호 — 글씨만 + 후광 (테두리/배경/아이콘 없음)
            Spacer(Modifier.height(12.dp))
            val titleColor = if (equippedStigma != null) Green else TextMuted
            Text(
                text = equippedStigma ?: stringResource(R.string.profile_no_title),
                color = titleColor,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                style = LocalTextStyle.current.merge(
                    TextStyle(shadow = Shadow(titleColor.copy(alpha = 0.9f), blurRadius = 24f))
                ),
                modifier = Modifier.clickable { onOpenAchievements() }.padding(6.dp)
            )
        }

        // ── 로그아웃 — 화면 맨 아래 ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 로그아웃
            GradientCard(onClick = onLogout, danger = true, modifier = Modifier.padding(horizontal = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.drawer_logout), color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── 떠다니는 통계 오버레이(전체 화면) — 하트=버스트 / 친구=친구창 / 다이어리=내 다이어리 / 핀 별=위치 ──
        val untitled = stringResource(R.string.common_untitled)
        FloatingStatBox(
            items = listOf(
                StatBubble(Icons.Filled.Favorite, stats.likesReceived, Color(0xFFE7556B), stringResource(R.string.profile_stat_likes), burstOnTap = true),
                StatBubble(Icons.Filled.Person, stats.friends, Green, stringResource(R.string.nav_friends)),
                StatBubble(Icons.AutoMirrored.Filled.MenuBook, stats.diariesCreated, Color(0xFFF7E067), stringResource(R.string.profile_stat_diaries)),
                StatBubble(Icons.Filled.EmojiEvents, unlockedCount, Color(0xFFF2C94C), stringResource(R.string.nav_achievements), burstOnTap = true), // 업적 — 누르면 버스트+보기
            ) + pinnedDiaries.map { d ->
                StatBubble(
                    Icons.Filled.Star, 0, StarStyle.colorOf(d.starColor), d.title.ifBlank { untitled },
                    showCount = false, starType = d.starType, starColorIndex = d.starColor
                )
            },
            onTap = { idx ->
                when {
                    idx == 1 -> onOpenFriends()
                    idx == 2 -> onOpenMyDiary()
                    idx == 3 -> onOpenAchievements()
                    idx >= 4 -> pinnedDiaries.getOrNull(idx - 4)?.id?.let { onOpenDiary(it) }
                }
            },
            avoidCenterYFraction = 0.5f // 화면 중앙의 프로필/이름 회피
        )

        // ("별 올리기" + 버튼은 탑바로 이동 — ProfilePinState)

        if (showPinPicker) {
            PinDiaryPicker(
                diaries = myDiaries,
                initial = pinnedIds,
                onDismiss = { showPinPicker = false },
                onConfirm = { ids ->
                    showPinPicker = false
                    pinnedIds = ids
                    scope.launch {
                        com.chaminwoo.stary.data.repository.FirebaseFriendRepository().setPinnedDiaries(userId, ids)
                    }
                }
            )
        }
    }
}

/** 프로필에 띄울 다이어리(별) 고르기 — 최대 3개 토글. */
@Composable
private fun PinDiaryPicker(
    diaries: List<com.chaminwoo.stary.core.model.Diary>,
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(1.dp, Brush.linearGradient(listOf(Green.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f))), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.profile_pin_picker_title), color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (diaries.isEmpty()) {
                Text(stringResource(R.string.mydiary_empty), color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(diaries, key = { it.id }) { d ->
                        val isSel = d.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Green.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    selected = when {
                                        isSel -> selected - d.id
                                        selected.size < 3 -> selected + d.id
                                        else -> selected
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StarShapeIcon(type = d.starType, colorIndex = d.starColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                d.title.ifBlank { stringResource(R.string.common_untitled) },
                                color = TextMain, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            if (isSel) Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onConfirm(selected.toList()) }) { Text(stringResource(R.string.common_save), color = Green) }
            }
        }
    }
}

/** 민트→블루 그라데이션 테두리의 다크 글래스 카드. */
@Composable
private fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val border = if (danger) Brush.linearGradient(listOf(Color(0xFFFF6B6B).copy(alpha = 0.5f), Color(0xFFFF6B6B).copy(alpha = 0.2f)))
    else Brush.linearGradient(listOf(Green.copy(alpha = 0.55f), Blue.copy(alpha = 0.45f)))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) { content() }
}

// (통계는 FloatingStatBox 로 대체됨 — 기존 StatCell/StatDivIder 제거)
