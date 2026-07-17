package com.chaminwoo.stary.feature.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.chaminwoo.stary.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.ui.raisedCosmicBorder
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.core.util.ProfilePinState
import com.chaminwoo.stary.core.util.UserProfileActionState
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.auth.screen.LoginScreen
import com.chaminwoo.stary.feature.diary.NotificationViewModel
import com.chaminwoo.stary.navigation.NavGraph
import com.chaminwoo.stary.navigation.NavRoute
import com.chaminwoo.stary.navigation.navigateToDetail
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    initialDiaryId: String? = null, // 푸시 알림 탭 딥링크 (해당 다이어리 상세로 이동)
    initialChatFriendId: String? = null, // 채팅 알림 탭 딥링크 (있으면 로그인 오버레이 생략; 실제 이동은 DeepLinkState)
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 앱 전역 배경음악 — 모든 스크린에서 재생, 생명주기에 맞춰 정지/이어재생
    val musicLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(musicLifecycleOwner) {
        com.chaminwoo.stary.core.util.MusicManager.init(context)
        com.chaminwoo.stary.core.util.AppSettings.init(context)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> com.chaminwoo.stary.core.util.MusicManager.resume()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> com.chaminwoo.stary.core.util.MusicManager.pause()
                else -> {}
            }
        }
        musicLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            musicLifecycleOwner.lifecycle.removeObserver(observer)
            com.chaminwoo.stary.core.util.MusicManager.release()
        }
    }
    // 토글 변경 시 즉시 반영
    androidx.compose.runtime.LaunchedEffect(com.chaminwoo.stary.core.util.MusicManager.enabled) {
        if (com.chaminwoo.stary.core.util.MusicManager.enabled) com.chaminwoo.stary.core.util.MusicManager.resume()
        else com.chaminwoo.stary.core.util.MusicManager.pause()
    }

    val currentRoute: NavRoute = when {
        currentDestination?.hasRoute<NavRoute.Main>() == true -> NavRoute.Main
        currentDestination?.hasRoute<NavRoute.Upload>() == true -> NavRoute.Upload
        currentDestination?.hasRoute<NavRoute.MyDiary>() == true -> NavRoute.MyDiary
        currentDestination?.hasRoute<NavRoute.Profile>() == true -> NavRoute.Profile
        currentDestination?.hasRoute<NavRoute.Achievements>() == true -> NavRoute.Achievements
        currentDestination?.hasRoute<NavRoute.Music>() == true -> NavRoute.Music
        currentDestination?.hasRoute<NavRoute.Settings>() == true -> NavRoute.Settings
        currentDestination?.hasRoute<NavRoute.Friends>() == true -> NavRoute.Friends
        currentDestination?.hasRoute<NavRoute.Notification>() == true -> NavRoute.Notification
        currentDestination?.hasRoute<NavRoute.Chat>() == true ->
            navBackStackEntry?.toRoute<NavRoute.Chat>() ?: NavRoute.Chat()
        currentDestination?.hasRoute<NavRoute.UserProfile>() == true ->
            navBackStackEntry?.toRoute<NavRoute.UserProfile>() ?: NavRoute.UserProfile()
        currentDestination?.hasRoute<NavRoute.UserDiaryStars>() == true ->
            navBackStackEntry?.toRoute<NavRoute.UserDiaryStars>() ?: NavRoute.UserDiaryStars()
        currentDestination?.hasRoute<NavRoute.Detail>() == true -> NavRoute.Detail()
        else -> NavRoute.Main
    }

    // 이미 로그인된 세션이면(앱 재시작 후 GoogleAuthHelper.restoreSession 으로 복원됨) 로그인 화면을
    // 건너뛰고 바로 지도로 진입한다("한 번 로그인하면 다음부터 바로 지도").
    val alreadyLoggedIn = remember { GoogleAuthHelper.currentUserId != null }

    // 로그인은 라우트가 아니라 오버레이 — 뒤에서 지도가 미리 렌더링되어
    // 로그인 직후 바로 지도가 보인다. 로그아웃 시 다시 true.
    // (푸시 딥링크로 진입했거나 이미 로그인 상태면 오버레이 생략)
    val deepLinked = initialDiaryId != null || initialChatFriendId != null
    var showLogin by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(!deepLinked && !alreadyLoggedIn)
    }

    // 지도(NavGraph) 로드 시점 제어 — 로그인 영상이 먼저 시작된 뒤에 지도를 로드한다(영상 우선).
    // 딥링크 진입/이미 로그인 상태면 즉시 로드. 한 번 true 가 되면 유지.
    var contentReady by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(deepLinked || alreadyLoggedIn)
    }

    // 로그아웃으로 로그인 화면에 진입했는지 — 이 경우 인트로 영상을 건너뛰고 로그인 UI 를 즉시 표시.
    var loginImmediate by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }

    // 푸시 알림 탭 딥링크 — DeepLinkState 관찰(콜드 스타트/앱 살아있을 때 모두). 소비 후 해당 화면으로 이동.
    androidx.compose.runtime.LaunchedEffect(com.chaminwoo.stary.core.util.DeepLinkState.diaryId) {
        com.chaminwoo.stary.core.util.DeepLinkState.consumeDiary()?.let {
            navController.navigateToDetail(it)
        }
    }
    androidx.compose.runtime.LaunchedEffect(com.chaminwoo.stary.core.util.DeepLinkState.chatFriendId) {
        com.chaminwoo.stary.core.util.DeepLinkState.consumeChat()?.let { (fid, fname) ->
            showLogin = false
            navController.navigate(NavRoute.Chat(friendId = fid, friendName = fname))
        }
    }
    // 친구 초대 딥링크(stary://invite/{uid}) 리딤 — 로그인 상태여야 소비. 비로그인이면 보관해 두고
    // 로그인 완료(showLogin 변경) 시 재시도한다. 결과는 토스트로 안내(체크리스트 31).
    androidx.compose.runtime.LaunchedEffect(com.chaminwoo.stary.core.util.DeepLinkState.inviterId, showLogin) {
        com.chaminwoo.stary.core.util.DeepLinkState.inviterId ?: return@LaunchedEffect
        val uid = GoogleAuthHelper.currentUserId ?: return@LaunchedEffect
        val inviter = com.chaminwoo.stary.core.util.DeepLinkState.consumeInvite() ?: return@LaunchedEffect
        val result = com.chaminwoo.stary.data.repository.FirebaseInviteRepository().redeem(inviter, uid)
        val msg = when (result) {
            com.chaminwoo.stary.data.repository.FirebaseInviteRepository.RedeemResult.SUCCESS -> R.string.invite_redeemed
            com.chaminwoo.stary.data.repository.FirebaseInviteRepository.RedeemResult.ALREADY -> R.string.invite_already
            com.chaminwoo.stary.data.repository.FirebaseInviteRepository.RedeemResult.SELF -> R.string.invite_self
            com.chaminwoo.stary.data.repository.FirebaseInviteRepository.RedeemResult.TOO_OLD -> R.string.invite_too_old
            com.chaminwoo.stary.data.repository.FirebaseInviteRepository.RedeemResult.FAILED -> R.string.invite_failed
        }
        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(msg))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // 첫 실행 코치마크(주요 컨트롤 안내) — SharedPreferences 로 1회만 노출.
    val onboardPrefs = remember { context.getSharedPreferences("stary_onboarding", Context.MODE_PRIVATE) }
    var showOnboarding by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(!onboardPrefs.getBoolean("main_coach_seen", false))
    }

    val userId = GoogleAuthHelper.currentUserId
    val notifVm: NotificationViewModel? = if (userId != null) {
        viewModel(factory = NotificationViewModel.factory(userId))
    } else null
    val unreadCount by (notifVm?.unreadCount?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(0))
    // 인앱 알림 팝업용 — 알림 목록을 관찰(null=로딩 중). 비로그인 시 null.
    val notifList by (notifVm?.notifications?.collectAsState()
        ?: remember { mutableStateOf<List<com.chaminwoo.stary.core.model.AppNotification>?>(null) })

    val onNavigate: (NavRoute) -> Unit = { route ->
        coroutineScope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 로그아웃 → 로그인 화면으로 이동(오버레이 표시).
    // 첫 실행과 동일하게 인트로 영상을 재생한다(immediate=false). 영상 종료 후 로그인 UI 노출.
    val onLogout: () -> Unit = {
        loginImmediate = false
        showLogin = true
        navController.navigate(NavRoute.Main) { popUpTo(0) { inclusive = true } }
        coroutineScope.launch {
            drawerState.close()
            GoogleAuthHelper.signOut(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111111),
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp)) {
                    // 상단: "목록"(회색 작은 글씨) + 우측 닫기(왼쪽 화살표)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 3.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.drawer_list),
                            fontFamily = com.chaminwoo.stary.core.designsystem.MinSans,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8A8A8A),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { coroutineScope.launch { drawerState.close() } }, modifier = Modifier.clickBounce()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_close), tint = Color(0xFFF0F0F0))
                        }
                    }

                    DrawerItem(stringResource(R.string.nav_my_diary), Icons.AutoMirrored.Filled.MenuBook, currentRoute is NavRoute.MyDiary) { onNavigate(NavRoute.MyDiary) }
                    DrawerItem(stringResource(R.string.nav_profile), Icons.Filled.Person, currentRoute is NavRoute.Profile) { onNavigate(NavRoute.Profile) }
                    DrawerItem(stringResource(R.string.nav_achievements), Icons.Filled.EmojiEvents, currentRoute is NavRoute.Achievements) { onNavigate(NavRoute.Achievements) }
                    DrawerItem(stringResource(R.string.nav_music), Icons.Filled.MusicNote, currentRoute is NavRoute.Music) { onNavigate(NavRoute.Music) }
                    DrawerItem(stringResource(R.string.nav_friends), Icons.Filled.People, currentRoute is NavRoute.Friends) { onNavigate(NavRoute.Friends) }
                    DrawerItem(stringResource(R.string.nav_settings), Icons.Filled.Settings, currentRoute is NavRoute.Settings) { onNavigate(NavRoute.Settings) }
                    // 로그인 상태면 로그아웃, 아니면 로그인 항목 노출
                    if (GoogleAuthHelper.currentUserId == null) {
                        DrawerItem(stringResource(R.string.drawer_login), Icons.AutoMirrored.Filled.Login, selected = false, alwaysAccent = true) {
                            coroutineScope.launch { drawerState.close() }
                            showLogin = true
                        }
                    } else {
                        DrawerItem(stringResource(R.string.drawer_logout), Icons.AutoMirrored.Filled.Logout, selected = false, danger = true) {
                            onLogout()
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF0D0D0D),
            topBar = {
                if (currentRoute.showTopBar && !MapUiState.mapOnly) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color(0xFF0D0D0D),
                            titleContentColor = Color(0xFFF0F0F0),
                            navigationIconContentColor = Color(0xFFF0F0F0),
                            actionIconContentColor = Color(0xFFF0F0F0)
                        ),
                        title = {
                            // 사람 이름이 제목인 화면(채팅/타인 프로필)에는 그 사람의 히든 업적 배지를 옆에 붙인다.
                            val titleUserId = when (val r = currentRoute) {
                                is NavRoute.Chat -> r.friendId
                                is NavRoute.UserProfile -> r.userId
                                else -> ""
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = localizedTitle(currentRoute),
                                    fontFamily = com.chaminwoo.stary.core.designsystem.MinSans,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFF0F0F0)
                                )
                                com.chaminwoo.stary.core.ui.HiddenStarBadges(
                                    userId = titleUserId,
                                    modifier = Modifier.padding(start = 6.dp),
                                    size = 15.dp,
                                )
                            }
                        },
                        navigationIcon = {
                            if (currentRoute.isRoot) {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }, modifier = Modifier.clickBounce()) {
                                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu), tint = Color(0xFFF0F0F0))
                                }
                            } else {
                                IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.clickBounce()) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color(0xFFF0F0F0))
                                }
                            }
                        },
                        actions = {
                            if (currentRoute is NavRoute.Main) {
                                IconButton(onClick = { navController.navigate(NavRoute.Notification) }, modifier = Modifier.clickBounce()) {
                                    BadgedBox(
                                        badge = {
                                            // 미열람 알림이 있으면 하트 우측 상단에 빨간 동그라미(알림 점)
                                            if (unreadCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = 4.dp,y = (-4).dp)
                                                        .size(7.dp)
                                                        .background(Color(0xFFFF3B30), CircleShape)
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(R.string.cd_notifications), tint = Color(0xFFF0F0F0))
                                    }
                                }
                            }
                            // 내 프로필: 우측 + 버튼 — 프로필에 띄울 별(다이어리) 고르기
                            if (currentRoute is NavRoute.Profile && ProfilePinState.visible) {
                                IconButton(onClick = { ProfilePinState.onOpen() }, modifier = Modifier.clickBounce()) {
                                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profile_pin_diaries), tint = Color(0xFFF0F0F0))
                                }
                            }
                            // 타인 프로필: 우측에 친구 액션 — 사람+(친구추가) / 사람✓(친구, 누르면 취소 확인)
                            if (currentRoute is NavRoute.UserProfile && UserProfileActionState.visible) {
                                val mint = Color(0xFF6EE7B7)
                                IconButton(onClick = { UserProfileActionState.onClick() }, modifier = Modifier.clickBounce()) {
                                    when {
                                        UserProfileActionState.isFriend ->
                                            Icon(Icons.Filled.HowToReg, contentDescription = stringResource(R.string.friend_status_friend), tint = mint)
                                        UserProfileActionState.requested ->
                                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.user_requested), tint = Color(0xFF8A8A8A))
                                        else ->
                                            Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.user_add_friend), tint = mint)
                                    }
                                }
                                // 더보기(⋮) — 신고 / 차단·차단해제
                                var menuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.clickBounce()) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = Color(0xFFF0F0F0))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.report_user), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                            onClick = { menuOpen = false; UserProfileActionState.onReport() }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (UserProfileActionState.isBlocked) stringResource(R.string.unblock) else stringResource(R.string.block), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                            onClick = { menuOpen = false; UserProfileActionState.onToggleBlock() }
                                        )
                                    }
                                }
                            }
                            // (로그아웃은 프로필 화면 내 버튼으로 이동)
                        }
                    )
                }
            },
            floatingActionButton = {
                if (currentRoute.showFab && !MapUiState.mapOnly) {
                    FloatingActionButton(
                        onClick = { navController.navigate(NavRoute.Upload) },
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        containerColor = Color.Transparent,
                        modifier = Modifier.clickBounce()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF3A4676), Color(0xFF111936)),
                                        start = Offset(0f, 0f),
                                        end = Offset(80f, 80f)
                                    ),
                                    CircleShape
                                )
                                .raisedCosmicBorder(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "글쓰기", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        ) { paddingValues ->
            // 영상이 시작된 뒤(contentReady)부터 지도를 로드 — 영상 우선.
            if (contentReady) {
                NavGraph(
                    navController = navController,
                    onLogout = onLogout,
                    modifier = modifier.padding(paddingValues)
                )
            }
        }
    }

    // 로그인 오버레이 — 영상을 먼저 끝까지 재생한 뒤(onVideoEnded) 또는 로그인 진행 시 지도를 로드한다.
    if (showLogin) {
        LoginScreen(
            immediate = loginImmediate,
            onLoginClick = { showLogin = false; loginImmediate = false; contentReady = true },
            onVideoEnded = { contentReady = true }
        )
    }

    // 첫 로그인 코치마크 — 로그인한 상태에서 지도(Main) 화면에 처음 들어왔을 때 1회만.
    // (비로그인 둘러보기에선 표시하지 않는다)
    if (showOnboarding && !showLogin && userId != null && currentRoute is NavRoute.Main) {
        MainOnboardingOverlay(onDismiss = {
            onboardPrefs.edit().putBoolean("main_coach_seen", true).apply()
            showOnboarding = false
        })
    }

    // 지도만 보기(몰입) — 하단 중앙 X 로 복귀. 지도 위에 떠 있어 지도 조작은 그대로.
    if (MapUiState.mapOnly && !showLogin) {
        MapOnlyOverlay(onExit = { MapUiState.exitMapOnly() })
    }

    // 업적 해금 팝업 감시 — 로그인 상태에서 통계 변화 시 새 업적 달성을 팝업으로 알림.
    // 코치마크가 떠 있는 동안엔 큐에 쌓아두고, 코치마크가 모두 닫힌 뒤에 팝업을 띄운다.
    if (userId != null && !showLogin) {
        com.chaminwoo.stary.feature.profile.AchievementUnlockWatcher(
            userId = userId,
            suppressed = showOnboarding,
        )

        // 히든 업적 감시 — 자동 조건 충족 시 앱 전체 선착순 선점(트랜잭션) + 첫 달성 팝업.
        com.chaminwoo.stary.feature.profile.HiddenAchievementWatcher(
            userId = userId,
            suppressed = showOnboarding,
        )

        // 인앱 알림 팝업 — 새 다이어리 알림(좋아요/댓글/친구 글) 도착 시 상단 배너.
        com.chaminwoo.stary.feature.diary.NotificationPopupWatcher(
            notifications = notifList,
            onOpen = { n ->
                if (n.type == com.chaminwoo.stary.core.model.NotificationType.FRIEND_POST.name && n.diaryId.isNotBlank()) {
                    com.chaminwoo.stary.core.util.MapFocusState.request(n.diaryId)
                    navController.navigate(NavRoute.Main) { popUpTo<NavRoute.Main> { inclusive = true } }
                } else if (n.diaryId.isNotBlank()) {
                    navController.navigateToDetail(n.diaryId)
                } else {
                    navController.navigate(NavRoute.Notification)
                }
            }
        )

        // 인앱 채팅 팝업 — 친구 새 메시지 도착 시 상단 배너. 그 채팅을 보고 있으면 생략.
        val activeChatFriendId = (currentRoute as? NavRoute.Chat)?.friendId
        com.chaminwoo.stary.feature.diary.ChatPopupWatcher(
            userId = userId,
            suppressChatWith = activeChatFriendId,
            onOpenChat = { friendId, friendName ->
                navController.navigate(NavRoute.Chat(friendId = friendId, friendName = friendName))
            }
        )
    }

    // 별 탄생 연출(34-8) — 업로드 성공 직후 업로드 화면이 pop 되고 나서 지도 위에서 재생된다.
    com.chaminwoo.stary.core.ui.StarBirthHost()
    // 인앱 알림 배너(상단) — 모든 콘텐츠 위에 표시. 토스트(하단)와 별개 채널.
    com.chaminwoo.stary.core.ui.InAppBannerHost()
    // 커스텀 토스트 — 모든 콘텐츠(로그인 오버레이 포함) 위에 표시
    com.chaminwoo.stary.core.ui.StaryToastHost()
    }
}

/** 라우트 → 현재 언어로 번역된 탑바 제목. Detail/Chat/UserProfile 의 동적 제목(사용자명 등)은 그대로 사용. */
@Composable
private fun localizedTitle(route: NavRoute): String = when (route) {
    is NavRoute.Main -> stringResource(R.string.nav_map)
    is NavRoute.Settings -> stringResource(R.string.nav_settings)
    is NavRoute.Friends -> stringResource(R.string.nav_friends)
    is NavRoute.Profile -> stringResource(R.string.nav_profile)
    is NavRoute.Achievements -> stringResource(R.string.nav_achievements)
    is NavRoute.Music -> stringResource(R.string.nav_music)
    is NavRoute.MyDiary -> stringResource(R.string.nav_my_diary)
    is NavRoute.Notification -> stringResource(R.string.nav_notification)
    is NavRoute.Upload -> stringResource(R.string.nav_upload)
    is NavRoute.Detail -> stringResource(R.string.nav_detail)
    is NavRoute.StarCluster -> stringResource(R.string.nav_star_cluster)
    is NavRoute.UserProfile -> stringResource(R.string.nav_profile) // 타인 프로필도 탑바엔 "프로필"
    else -> route.title // Chat(친구명) 등 동적 제목
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    alwaysAccent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        danger -> Color(0xFFFF6B6B)
        selected || alwaysAccent -> Color(0xFF6EE7B7)
        else -> Color(0xFFF0F0F0)
    }
    NavigationDrawerItem(
        icon = { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) },
        label = { Text(label, fontFamily = com.chaminwoo.stary.core.designsystem.MinSans, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color) },
        selected = selected,
        onClick = onClick,
        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF6EE7B7).copy(alpha = 0.10f),
            unselectedContainerColor = Color.Transparent
        )
    )
}

@Preview
@Composable
private fun PreviewMainScreen() {
    MainScreen()
}
