package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.friend.FriendViewModel

/**
 * 타인의 다이어리를 **내 다이어리처럼** 다이얼(최신/인기/거리) + 떠다니는 별로 보여주는 화면.
 * 동작은 내 다이어리와 동일하되, **별을 탭하면 지도로 이동**(카메라+파장, [onOpenMap]).
 * 공개 범위에 맞춰 비공개/친구공개 글은 보호한다.
 */
@Composable
fun UserDiaryStarsScreen(
    userId: String,
    userName: String,
    onOpenMap: (String) -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory()),
) {
    val myId = GoogleAuthHelper.currentUserId
    val isMe = myId != null && myId == userId

    val me = remember {
        UserProfile(
            userId = myId ?: "",
            userName = GoogleAuthHelper.currentUserName ?: "",
            profileImageUrl = GoogleAuthHelper.currentUserPhotoUrl ?: ""
        )
    }
    val friendVm: FriendViewModel = viewModel(factory = FriendViewModel.factory(me))
    val friends by friendVm.friends.collectAsState()
    val isFriend = friends.any { it.userId == userId }

    val theirDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()
    val visible = remember(theirDiaries, isMe, isFriend) {
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
        // 내 다이어리와 동일한 보드(다이얼+별자리+떠다니는 별). 클릭만 지도 이동으로.
        DiaryStarsBoard(diaries = visible, onDiaryClick = onOpenMap)
    }
}
