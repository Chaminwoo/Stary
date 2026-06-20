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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val userId = GoogleAuthHelper.currentUserId

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = TextMuted, fontSize = 18.sp)
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
            com.chaminwoo.stary.core.ui.StaryToast.show("프로필 이미지 업로드 실패: $it")
            profileVm.clearError()
        }
    }

    val stats = rememberUserStats(userId)
    val equippedStigma = Achievements.byId(StigmaStore.equipped(context, userId))?.titleName

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        val unlockedCount = remember(stats) { Achievements.unlockedIds(stats).size }
        val totalCount = Achievements.all.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 48.dp, bottom = 112.dp), // 하단 고정 로그아웃 버튼에 가리지 않게 여유
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 아바타 (뒤 글로우 + 그라데이션 링) ──
            Box(
                modifier = Modifier.size(156.dp),
                contentAlignment = Alignment.Center
            ) {
                // 부드러운 후광
                Box(
                    Modifier
                        .size(156.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Green.copy(alpha = 0.28f), Color.Transparent),
                                radius = 220f
                            ),
                            CircleShape
                        )
                )
                // 그라데이션 링
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, AccentBrush, CircleShape)
                        .background(Color(0xFF0D0D0D), CircleShape)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isUploading -> CircularProgressIndicator(color = Green, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        profileImageUrl != null -> AsyncImage(profileImageUrl, "프로필", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        GoogleAuthHelper.currentUserPhotoUrl != null -> AsyncImage(GoogleAuthHelper.currentUserPhotoUrl, "프로필", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else -> Icon(Icons.Filled.AccountCircle, "기본 프로필", tint = Color(0xFF555555), modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = GoogleAuthHelper.currentUserName ?: userId.take(12),
                fontSize = 23.sp, fontWeight = FontWeight.Bold, color = TextMain
            )

            // 장착한 칭호 칩
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Green.copy(alpha = 0.12f))
                    .border(1.dp, Green.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onOpenAchievements() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = equippedStigma ?: "칭호 없음 · 업적 보기",
                    color = if (equippedStigma != null) Green else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── 통계 일체형 카드 ──
            GradientCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCell("좋아요", stats.likesReceived.toString(), Color(0xFFE7556B), Icons.Filled.Favorite, Modifier.weight(1f))
                    StatDivider()
                    StatCell("조회수", stats.viewsReceived.toString(), TextMuted, Icons.Filled.Visibility, Modifier.weight(1f))
                    StatDivider()
                    StatCell("다이어리", stats.diariesCreated.toString(), Color(0xFFF7E067), Icons.Filled.Star, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 업적 진행 카드 (클릭 → 업적 화면) ──
            GradientCard(onClick = onOpenAchievements) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("업적 · 칭호", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$unlockedCount / $totalCount", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    // 진행 바
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        val frac = if (totalCount == 0) 0f else unlockedCount.toFloat() / totalCount
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceIn(0f, 1f))
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(AccentBrush)
                        )
                    }
                }
            }

        }

        // ── 로그아웃 — 화면 하단 고정, 좌우 여백, 내용 가운데 정렬 ──
        GradientCard(
            onClick = onLogout,
            danger = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("로그아웃", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

@Composable
private fun StatCell(
    label: String,
    value: String,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
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
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}
