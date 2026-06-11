package com.chaminwoo.stary.feature.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.NotificationViewModel
import com.chaminwoo.stary.navigation.NavGraph
import com.chaminwoo.stary.navigation.NavRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentRoute: NavRoute = when {
        currentDestination?.hasRoute<NavRoute.Login>() == true -> NavRoute.Login
        currentDestination?.hasRoute<NavRoute.Main>() == true -> NavRoute.Main
        currentDestination?.hasRoute<NavRoute.Upload>() == true -> NavRoute.Upload
        currentDestination?.hasRoute<NavRoute.MyPage>() == true -> NavRoute.MyPage
        currentDestination?.hasRoute<NavRoute.Notification>() == true -> NavRoute.Notification
        currentDestination?.hasRoute<NavRoute.Detail>() == true -> NavRoute.Detail()
        else -> NavRoute.Login
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val userId = GoogleAuthHelper.currentUserId
    val notifVm: NotificationViewModel? = if (userId != null) {
        viewModel(factory = NotificationViewModel.factory(userId))
    } else null
    val unreadCount by (notifVm?.unreadCount?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(0))

    val onNavigate: (NavRoute) -> Unit = { route ->
        coroutineScope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111111),
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp)) {
                    Text(
                        "목록",
                        fontSize = 40.sp,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = Color(0xFFF0F0F0),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        color = Color(0xFF2A2A2A),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationDrawerItem(
                        label = { Text("마이페이지", fontSize = 20.sp, color = if (currentRoute is NavRoute.MyPage) Color(0xFF6EE7B7) else Color(0xFFF0F0F0)) },
                        selected = currentRoute is NavRoute.MyPage,
                        onClick = { onNavigate(NavRoute.MyPage) },
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF6EE7B7).copy(alpha = 0.10f),
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF0D0D0D),
            topBar = {
                if (currentRoute.showTopBar) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color(0xFF0D0D0D),
                            titleContentColor = Color(0xFFF0F0F0),
                            navigationIconContentColor = Color(0xFFF0F0F0),
                            actionIconContentColor = Color(0xFFF0F0F0)
                        ),
                        title = {
                            Text(
                                text = currentRoute.title,
                                fontSize = 20.sp,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFF0F0F0)
                            )
                        },
                        navigationIcon = {
                            if (currentRoute.isRoot) {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "메뉴", tint = Color(0xFFF0F0F0))
                                }
                            } else {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color(0xFFF0F0F0))
                                }
                            }
                        },
                        actions = {
                            if (currentRoute is NavRoute.Main) {
                                IconButton(onClick = { navController.navigate(NavRoute.Notification) }) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadCount > 0) {
                                                Badge(containerColor = Color(0xFF6EE7B7)) {
                                                    Text(unreadCount.toString(), color = Color(0xFF0D0D0D))
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.FavoriteBorder, contentDescription = "알림", tint = Color(0xFFF0F0F0))
                                    }
                                }
                            }
                            if (currentRoute is NavRoute.MyPage) {
                                val isLoggedIn = GoogleAuthHelper.currentUserId != null
                                TextButton(
                                    onClick = {
                                        if (isLoggedIn) {
                                            coroutineScope.launch {
                                                GoogleAuthHelper.signOut(context)
                                                navController.navigate(NavRoute.Login) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        } else {
                                            navController.navigate(NavRoute.Login) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (isLoggedIn) "로그아웃" else "로그인",
                                        color = if (isLoggedIn) Color(0xFFFF4F4F) else Color(0xFF6EE7B7),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (currentRoute.showFab) {
                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoute.Upload) },
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        containerColor = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF6EE7B7), Color(0xFF3B82F6)),
                                        start = Offset(0f, 0f),
                                        end = Offset(80f, 80f)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "글쓰기", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavGraph(
                navController = navController,
                modifier = modifier.padding(paddingValues)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewMainScreen() {
    MainScreen()
}
