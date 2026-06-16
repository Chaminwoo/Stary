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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
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
import com.chaminwoo.stary.core.ui.StatCard
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.ProfileViewModel
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.profile.rememberUserStats

private val Green = Color(0xFF6EE7B7)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xFF15151F)

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

    val stats = rememberUserStats(userId)
    val equippedStigma = Achievements.byId(StigmaStore.equipped(context, userId))?.stigma

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.mypage_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 아바타
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .border(2.dp, Green.copy(alpha = 0.4f), CircleShape)
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

            Spacer(Modifier.height(14.dp))

            Text(
                text = GoogleAuthHelper.currentUserName ?: userId.take(12),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain
            )

            // 장착한 성흔 칩
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Green.copy(alpha = 0.14f))
                    .border(1.dp, Green.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable { onOpenAchievements() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = equippedStigma ?: "성흔 없음 · 업적 보기",
                    color = if (equippedStigma != null) Green else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(24.dp))

            // 통계
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("좋아요", stats.likesReceived.toString(), icon = {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE7556B), modifier = Modifier.size(14.dp))
                }, modifier = Modifier.weight(1f))
                StatCard("조회수", stats.viewsReceived.toString(), icon = {
                    Icon(Icons.Filled.Visibility, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                }, modifier = Modifier.weight(1f))
                StatCard("다이어리", stats.diariesCreated.toString(), icon = {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFF7E067), modifier = Modifier.size(14.dp))
                }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // 업적 바로가기
            ProfileMenuRow(
                icon = Icons.Filled.AutoAwesome,
                label = "업적 · 성흔",
                onClick = onOpenAchievements
            )

            Spacer(Modifier.height(10.dp))

            // 로그아웃
            ProfileMenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "로그아웃",
                danger = true,
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) Color(0xFFFF6B6B) else TextMain
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (!danger) Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}
