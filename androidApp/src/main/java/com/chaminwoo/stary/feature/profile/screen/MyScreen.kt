package com.chaminwoo.stary.feature.profile.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StatCard
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.profile.ProfileViewModel

private val Green = Color(0xFF6EE7B7)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
// DiarySort 는 DiaryStarBox.kt 로 이동(공용)

@Composable
fun MyScreen(
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = TextMuted, fontSize = 25.sp)
        }
        return
    }

    val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(userId))
    val profileImageUrl by profileVm.profileImageUrl.collectAsState()
    val isUploading by profileVm.isUploading.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { profileVm.uploadProfileImage(it) }
    }

    val myDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()
    val totalLikes = myDiaries.sumOf { it.likeCount }
    val totalViews = myDiaries.sumOf { it.viewCount }
    // 시작은 최신순 정렬 상태
    var sortMode by remember { mutableStateOf(DiarySort.LATEST) }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.mypage_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(
                Color.Black.copy(alpha = 0.8f),
                blendMode = BlendMode.Darken
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            // ── Profile header ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isUploading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF2A2A2A), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 28.dp, color = Green)
                                }
                            }

                            profileImageUrl != null -> {
                                com.chaminwoo.stary.core.ui.ThumbAsyncImage(
                                    model = profileImageUrl,
                                    contentDescription = "프로필 이미지",
                                    modifier = Modifier.fillMaxSize(),
                                    sizePx = 256,
                                )
                            }

                            GoogleAuthHelper.currentUserPhotoUrl != null -> {
                                com.chaminwoo.stary.core.ui.ThumbAsyncImage(
                                    model = GoogleAuthHelper.currentUserPhotoUrl,
                                    contentDescription = "구글 프로필",
                                    modifier = Modifier.fillMaxSize(),
                                    sizePx = 256,
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = "기본 프로필",
                                    tint = Color(0xFF555555),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = GoogleAuthHelper.currentUserName ?: userId.take(12),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))
                }
            }

            // ── Stats ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("좋아요", totalLikes.toString(), icon = {
                    Icon(
                        Icons.Filled.Favorite,
                        null,
                        tint = Color(0xFFE7556B),
                        modifier = Modifier.size(14.dp)
                    )
                }, modifier = Modifier.weight(1f))
                StatCard("조회수", totalViews.toString(), icon = {
                    Icon(
                        Icons.Filled.Visibility,
                        null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }, modifier = Modifier.weight(1f))
                StatCard("다이어리", myDiaries.size.toString(), icon = {
                    Icon(
                        Icons.Filled.Star,
                        null,
                        tint = Color(0xFFF7E067),
                        modifier = Modifier.size(14.dp)
                    )
                }, modifier = Modifier.weight(1f))
            }

            // (위치 초기화 / 테스트 데이터 생성 버튼 숨김)

            // ── Diary list header ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("내 다이어리", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Green.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${myDiaries.size}",
                        fontSize = 12.sp,
                        color = Green,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── 정렬 선택 ─────────────────────────────────────────────────────
            if (myDiaries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiarySort.entries.forEach { mode ->
                        val selected = sortMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Green.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                                .clickable { sortMode = mode }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                color = if (selected) Green else TextMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ── 공중에 떠 있는 별들 ────────────────────────────────────────────
            if (myDiaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("아직 기록한 다이어리가 없어요", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                DiaryStarBox(
                    diaries = myDiaries,
                    sortMode = sortMode,
                    onDiaryClick = onDiaryClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}
