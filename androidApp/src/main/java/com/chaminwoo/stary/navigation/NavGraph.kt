package com.chaminwoo.stary.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.screen.LoginScreen
import com.chaminwoo.stary.feature.diary.screen.DetailScreen
import com.chaminwoo.stary.feature.diary.screen.NotificationScreen
import com.chaminwoo.stary.feature.diary.screen.UploadScreen
import com.chaminwoo.stary.feature.home.screen.MainListScreen
import com.chaminwoo.stary.feature.profile.screen.MyScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.Login,
        modifier = modifier
    ) {
        composable<NavRoute.Login> {
            LoginScreen(
                onLoginClick = {
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Login> { inclusive = true } // 백스택 청소
                    }
                }
            )
        }

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
                onLocationClick = { latLng ->

                    LocationHelper.cameraTarget = latLng
                    navController.navigate(NavRoute.Main) {
                        popUpTo<NavRoute.Main> { inclusive = true }
                    }
                },
                onBack = { navController.navigate(NavRoute.Main) {
                    popUpTo<NavRoute.Main> { inclusive = true }
                }}
            )
        }

    }
}