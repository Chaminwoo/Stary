package com.chaminwoo.stary.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.chaminwoo.stary.feature.diary.screen.DetailScreen
import com.chaminwoo.stary.feature.diary.screen.NotificationScreen
import com.chaminwoo.stary.feature.diary.screen.UploadScreen
import com.chaminwoo.stary.feature.chat.screen.ChatScreen
import com.chaminwoo.stary.feature.friend.screen.FriendScreen
import com.chaminwoo.stary.feature.profile.screen.AchievementsScreen
import com.chaminwoo.stary.feature.profile.screen.BlockedUsersScreen
import com.chaminwoo.stary.feature.profile.screen.MusicScreen
import com.chaminwoo.stary.feature.profile.screen.MyDiaryScreen
import com.chaminwoo.stary.feature.profile.screen.ProfileScreen
import com.chaminwoo.stary.feature.profile.screen.SettingsScreen
import com.chaminwoo.stary.feature.profile.screen.UserDiaryStarsScreen
import com.chaminwoo.stary.feature.profile.screen.UserProfileScreen

// 화면 전환 공통 파라미터 — 깊이감 줌(scale+fade). 별 줌인 연출과 이어지도록 짧고 부드럽게.
private const val ENTER_MS = 320
private const val EXIT_MS = 300
private val transitionEasing = FastOutSlowInEasing
private const val ENTER_SCALE = 0.93f // 들어오는 화면 시작 배율
private const val EXIT_SCALE = 1.06f  // 나가는 화면 끝 배율(앞으로 멀어지듯)

@Composable
fun NavGraph(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 로그인은 별도 라우트가 아니라 MainScreen 의 오버레이로 표시된다.
    // 지도(MainListScreen)도 라우트가 아니라 MainScreen 이 NavHost 뒤에 상시 렌더한다
    // (로그인 직후 바로 보이고, 화면 전환에도 재생성되지 않도록).
    NavHost(
        navController = navController,
        startDestination = NavRoute.Main,
        modifier = modifier,
        // 기본 전환: 깊이감 줌. 드릴인=확대되며 또렷, 드릴아웃=멀어지며 흐림(뒤로가기는 대칭 반전).
        enterTransition = {
            fadeIn(tween(ENTER_MS, easing = transitionEasing)) +
                scaleIn(initialScale = ENTER_SCALE, animationSpec = tween(ENTER_MS, easing = transitionEasing))
        },
        exitTransition = {
            fadeOut(tween(EXIT_MS, easing = transitionEasing)) +
                scaleOut(targetScale = EXIT_SCALE, animationSpec = tween(EXIT_MS, easing = transitionEasing))
        },
        popEnterTransition = {
            fadeIn(tween(ENTER_MS, easing = transitionEasing)) +
                scaleIn(initialScale = EXIT_SCALE, animationSpec = tween(ENTER_MS, easing = transitionEasing))
        },
        popExitTransition = {
            fadeOut(tween(EXIT_MS, easing = transitionEasing)) +
                scaleOut(targetScale = ENTER_SCALE, animationSpec = tween(EXIT_MS, easing = transitionEasing))
        }
    ) {
        composable<NavRoute.Main> {
            // 지도(MainListScreen)는 MainScreen 이 NavHost "뒤"에 상시 렌더한다 — 화면 전환마다
            // 지도가 재생성/리로드되며 별이 깜빡이던 문제 방지. 이 라우트는 "지도가 보이는 상태"를
            // 나타내는 빈 투명 레이어(터치는 아래 지도로 통과).
        }

        composable<NavRoute.StarCluster> { backStackEntry ->
            val args: NavRoute.StarCluster = backStackEntry.toRoute()
            com.chaminwoo.stary.feature.diary.screen.StarClusterScreen(
                ids = args.ids.split(",").filter { it.isNotBlank() },
                onOpenDiary = { diaryId -> navController.navigateToDetail(diaryId) },
                onBack = { navController.navigateUp() }
            )
        }
        // 글쓰기는 '모달' 느낌 — 아래에서 위로 슬라이드, 닫을 땐 아래로.
        composable<NavRoute.Upload>(
            enterTransition = {
                fadeIn(tween(ENTER_MS, easing = transitionEasing)) +
                    slideInVertically(tween(ENTER_MS, easing = transitionEasing)) { it }
            },
            popExitTransition = {
                fadeOut(tween(EXIT_MS, easing = transitionEasing)) +
                    slideOutVertically(tween(EXIT_MS, easing = transitionEasing)) { it }
            }
        ) {
            UploadScreen(
                onSaveClick = { navController.navigateUp() }
            )
        }

        composable<NavRoute.Friends> {
            FriendScreen(
                onOpenChat = { friendId, friendName ->
                    navController.navigate(NavRoute.Chat(friendId = friendId, friendName = friendName))
                },
                onOpenProfile = { uid, uname ->
                    navController.navigate(NavRoute.UserProfile(userId = uid, userName = uname))
                },
                onOpenDiaryOnMap = { diaryId ->
                    // 친구 행의 최근 별 탭 → 지도로 가서 파동 후 그 별까지 도보 길찾기(프로필 핀 별과 동일).
                    com.chaminwoo.stary.core.util.MapFocusState.request(diaryId, withRoute = true)
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                }
            )
        }

        composable<NavRoute.Chat> { backStackEntry ->
            val chatArgs: NavRoute.Chat = backStackEntry.toRoute()
            ChatScreen(friendId = chatArgs.friendId, friendName = chatArgs.friendName)
        }

        composable<NavRoute.MyDiary> {
            MyDiaryScreen(
                onDiaryClick = { diaryId ->
                    navController.navigateToDetail(diaryId)
                }
            )
        }

        composable<NavRoute.Profile> {
            ProfileScreen(
                onOpenAchievements = { navController.navigate(NavRoute.Achievements) },
                onLogout = onLogout,
                onOpenMyDiary = { navController.navigate(NavRoute.MyDiary) },
                onOpenFriends = { navController.navigate(NavRoute.Friends) },
                onOpenDiary = { diaryId ->
                    // 프로필 핀 별 탭 → 지도로 가서 파동 후 그 별까지 도보 길찾기(친구 별 탭과 동일).
                    com.chaminwoo.stary.core.util.MapFocusState.request(diaryId, withRoute = true)
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                }
            )
        }

        composable<NavRoute.Achievements> {
            AchievementsScreen()
        }

        composable<NavRoute.Music> {
            MusicScreen()
        }

        composable<NavRoute.Settings> {
            SettingsScreen(
                onAccountDeleted = onLogout,
                onOpenBlockedUsers = { navController.navigate(NavRoute.BlockedUsers) },
            )
        }

        composable<NavRoute.BlockedUsers> {
            BlockedUsersScreen(
                onOpenProfile = { uid, uname ->
                    navController.navigate(NavRoute.UserProfile(userId = uid, userName = uname))
                }
            )
        }

        composable<NavRoute.Notification> {
            NotificationScreen(
                onOpenDiary = { diaryId ->
                    navController.navigateToDetail(diaryId)
                },
                onFocusDiaryOnMap = { diaryId ->
                    // 지도로 돌아가 해당 다이어리 위치로 카메라 이동 + 파장 연출
                    com.chaminwoo.stary.core.util.MapFocusState.request(diaryId)
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                },
                // 친구 요청 알림 → 친구 화면(받은 요청에서 수락/거절).
                onOpenFriends = { navController.navigate(NavRoute.Friends) }
            )
        }

        composable<NavRoute.Detail> { backStackEntry ->
            val detailArgs: NavRoute.Detail = backStackEntry.toRoute()
            DetailScreen(
                diaryId = detailArgs.diaryId,
                onBack = { navController.navigate(NavRoute.Main) {
                    popUpTo<NavRoute.Main> { inclusive = true }
                }},
                onOpenProfile = { uid, uname ->
                    navController.navigate(NavRoute.UserProfile(userId = uid, userName = uname))
                }
            )
        }

        composable<NavRoute.UserProfile> { backStackEntry ->
            val args: NavRoute.UserProfile = backStackEntry.toRoute()
            UserProfileScreen(
                userId = args.userId,
                userName = args.userName,
                onOpenDiary = { diaryId ->
                    // 타인 프로필의 핀 별 탭 → 지도로 가서 카메라 + 파장 후 그 별까지
                    // 도보 길찾기(내 프로필 핀 별/친구 행 별 탭과 동일).
                    com.chaminwoo.stary.core.util.MapFocusState.request(diaryId, withRoute = true)
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                },
                onOpenDiaryStars = { uid, uname ->
                    navController.navigate(NavRoute.UserDiaryStars(userId = uid, userName = uname))
                },
                onOpenChat = { friendId, friendName ->
                    navController.navigate(NavRoute.Chat(friendId = friendId, friendName = friendName))
                }
            )
        }

        composable<NavRoute.UserDiaryStars> { backStackEntry ->
            val args: NavRoute.UserDiaryStars = backStackEntry.toRoute()
            UserDiaryStarsScreen(
                userId = args.userId,
                userName = args.userName,
                onOpenMap = { diaryId ->
                    // 친구 별 탭 → 지도로 이동해 그 위치로 카메라 + 파장 후 도보 길찾기 경로 표시.
                    com.chaminwoo.stary.core.util.MapFocusState.request(diaryId, withRoute = true)
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                }
            )
        }

    }
}