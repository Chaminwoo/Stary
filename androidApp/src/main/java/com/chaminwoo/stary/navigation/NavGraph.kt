package com.chaminwoo.stary.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.chaminwoo.stary.feature.diary.screen.DetailScreen
import com.chaminwoo.stary.feature.diary.screen.NotificationScreen
import com.chaminwoo.stary.feature.diary.screen.UploadScreen
import com.chaminwoo.stary.feature.friend.screen.FriendScreen
import com.chaminwoo.stary.feature.home.screen.MainListScreen
import com.chaminwoo.stary.feature.profile.screen.MyScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // 로그인은 별도 라우트가 아니라 MainScreen 의 오버레이로 표시된다.
    // (지도를 로그인 화면 뒤에서 미리 렌더링해 로그인 직후 바로 보이게 하기 위함)
    NavHost(
        navController = navController,
        startDestination = NavRoute.Main,
        modifier = modifier
    ) {
        composable<NavRoute.Main> {
            MainListScreen(
                onItemClick = { diaryId ->
                    navController.navigate(NavRoute.Detail(diaryId = diaryId))
                },
                onCreateClick = {
                    navController.navigate(NavRoute.Upload)
                }
            )
        }
        composable<NavRoute.Upload> {
            UploadScreen(
                onSaveClick = { navController.navigateUp() }
            )
        }

        composable<NavRoute.Friends> {
            FriendScreen()
        }

        composable<NavRoute.MyPage> {
            MyScreen(
                onDiaryClick = { diaryId ->
                    navController.navigate(NavRoute.Detail(diaryId = diaryId))
                }
            )
        }

        composable<NavRoute.Notification> {
            NotificationScreen()
        }

        composable<NavRoute.Detail> { backStackEntry ->
            val detailArgs: NavRoute.Detail = backStackEntry.toRoute()
            DetailScreen(
                diaryId = detailArgs.diaryId,
                onBack = { navController.navigate(NavRoute.Main) {
                    popUpTo<NavRoute.Main> { inclusive = true }
                }}
            )
        }

    }
}