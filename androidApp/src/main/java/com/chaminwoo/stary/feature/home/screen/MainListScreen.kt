package com.chaminwoo.stary.feature.home.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapFocusState
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.globe.GlobeScreen
import com.chaminwoo.stary.feature.map.screen.DiaryFocusTarget
import com.chaminwoo.stary.feature.map.screen.DiaryMap
import com.chaminwoo.stary.feature.map.screen.GlobeReturnCamera
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class FilterOpt(
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val onClick: () -> Unit,
)

/** 기간별 보기 라벨 — null=전체 기간, 0=오늘, 그 외 N=최근 N일. */
@Composable
private fun periodLabel(days: Int?): String = when (days) {
    0 -> stringResource(R.string.period_today)
    7 -> stringResource(R.string.period_week)
    30 -> stringResource(R.string.period_month)
    365 -> stringResource(R.string.period_year)
    else -> stringResource(R.string.period_all)
}

@Composable
fun MainListScreen(
    onItemClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 30m 안에서 합쳐진 별(2개 이상) 열람 — 우선순위 정렬된 다이어리 id 목록. */
    onOpenCluster: (List<String>) -> Unit = {},
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    val context = LocalContext.current
    val diaries by diaryViewModel.diaries.collectAsState()
    val step = 0.0001
    val focusRequester = remember { FocusRequester() }

    // 실시간 위치 — LocationHelper 의 StateFlow 를 관찰해 연속 업데이트가 들어올 때마다 따라온다.
    // (예전엔 진입 시 1회만 currentLatLng 를 채워 내 위치 마커가 실시간으로 움직이지 않았다.)
    // 첫 fix 전 초기 좌표는 "지난 세션 마지막 위치"(없으면 기본좌표) — 기본좌표(건국대)에서
    // 내 위치로 크게 점프하는 어색한 간격을 줄인다. 열람 게이팅은 실제 fix 만 사용(DiaryMap).
    val liveLocation by LocationHelper.location.collectAsState()
    var currentLatLng by remember {
        mutableStateOf(
            liveLocation
                ?: LocationHelper.lastSavedLatLng(context)
                ?: LatLng(StaryConfig.DEFAULT_LAT, StaryConfig.DEFAULT_LNG)
        )
    }
    LaunchedEffect(liveLocation) { liveLocation?.let { currentLatLng = it } }

    // 모의 위치(위치 조작 앱) 감지 시 1회 경고 — 조작된 좌표는 LocationHelper 가 이미 거부한다.
    val mockDetected by LocationHelper.mockDetected.collectAsState()
    LaunchedEffect(mockDetected) {
        if (mockDetected) {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.location_mock_blocked))
        }
    }

    // currentUserId 는 일반 var(관찰 불가)라, 로그인 화면 뒤에서 미리 렌더된 이 화면이
    // 로그인 완료 시 리컴포즈되지 않는다. FirebaseAuth 상태를 관찰해 reactive 하게 만든다.
    var userId by remember { mutableStateOf(GoogleAuthHelper.currentUserId) }
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { userId = GoogleAuthHelper.currentUserId }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    var unviewedOnly by remember { mutableStateOf(false) }
    var friendsOnly by remember { mutableStateOf(false) }
    var myOnly by remember { mutableStateOf(false) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var selectedFriendIds by remember { mutableStateOf(emptySet<String>()) }
    // 기간별 보기 — null=전체 기간, 0=오늘(로컬 자정 이후), 그 외 N=최근 N일
    var periodDays by remember { mutableStateOf<Int?>(null) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var speedDialExpanded by remember { mutableStateOf(false) }

    val viewedIds by remember(userId) {
        val uid = userId
        if (uid != null) FirebaseViewedRepository().observeViewedIds(uid)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())

    val friends by remember(userId) {
        val uid = userId
        if (uid != null) FirebaseFriendRepository().observeFriends(uid)
        else flowOf(emptyList<Friend>())
    }.collectAsState(initial = emptyList())

    val friendIds = remember(friends) { friends.map { it.userId }.toSet() }

    // 내가 차단한 사용자 — 그 사람의 별은 지도/목록에서 숨긴다.
    val blockedIds by remember(userId) {
        val uid = userId
        if (uid != null) com.chaminwoo.stary.data.repository.FirebaseModerationRepository().observeBlockedIds(uid)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())

    val filteredDiaries = remember(diaries, unviewedOnly, friendsOnly, myOnly, selectedFriendIds, viewedIds, friendIds, blockedIds, userId, periodDays) {
        // 기간 컷오프(epoch ms) — 오늘=로컬 자정, 그 외=지금-N일. 선택이 바뀔 때 재계산.
        val periodCutoff: Long? = when (val d = periodDays) {
            null -> null
            0 -> java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> System.currentTimeMillis() - d * 86_400_000L
        }
        diaries.filter { diary ->
            val visibilityOk = diary.visibilityType != "friends" ||
                diary.userId == userId || diary.userId in friendIds
            val filterOk = (!unviewedOnly || diary.id !in viewedIds) &&
                (!friendsOnly || diary.userId in friendIds) &&
                (!myOnly || diary.userId == userId) &&
                (selectedFriendIds.isEmpty() || diary.userId in selectedFriendIds) &&
                (periodCutoff == null || diary.createdAt >= periodCutoff)
            visibilityOk && filterOk && diary.userId !in blockedIds
        }
    }

    // 근처 미조회 별 발견 알림(체크리스트 33) — 실제 위치가 갱신될 때마다 검사(빈도 제한은 내부에서).
    // 지도에 보이는 목록(공개범위 반영) 기준. 탭 → 그 별로 지도 포커스.
    LaunchedEffect(liveLocation, filteredDiaries, viewedIds, userId) {
        val me = liveLocation ?: return@LaunchedEffect
        com.chaminwoo.stary.core.util.NearbyStarAlert.check(context, me, filteredDiaries, viewedIds, userId)
    }

    // 기간별 보기 다이얼로그 — 전체/오늘/7일/30일/1년 중 선택
    if (showPeriodPicker) {
        AlertDialog(
            onDismissRequest = { showPeriodPicker = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text(stringResource(R.string.filter_period), color = Color(0xFFF0F0F0), fontSize = 16.sp) },
            text = {
                Column {
                    // '전체 기간' 항목은 없음 — 해제는 활성 칩을 다시 탭.
                    listOf(0, 7, 30, 365).forEach { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { periodDays = d; showPeriodPicker = false }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = periodDays == d,
                                onClick = { periodDays = d; showPeriodPicker = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6EE7B7),
                                    unselectedColor = Color(0xFF8A8A8A)
                                )
                            )
                            Text(periodLabel(d), color = Color(0xFFF0F0F0), fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPeriodPicker = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFF8A8A8A))
                }
            }
        )
    }

    // 친구 선택 다이얼로그
    if (showFriendPicker) {
        var tempSelected by remember { mutableStateOf(selectedFriendIds) }
        AlertDialog(
            onDismissRequest = { showFriendPicker = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text(stringResource(R.string.filter_pick_friends), color = Color(0xFFF0F0F0), fontSize = 16.sp) },
            text = {
                if (friends.isEmpty()) {
                    Text(stringResource(R.string.filter_no_friends), color = Color(0xFF8A8A8A), fontSize = 14.sp)
                } else {
                    Column {
                        friends.forEach { friend ->
                            val checked = friend.userId in tempSelected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        tempSelected = if (it) tempSelected + friend.userId
                                        else tempSelected - friend.userId
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF6EE7B7),
                                        uncheckedColor = Color(0xFF8A8A8A)
                                    )
                                )
                                Text(
                                    friend.userName.ifBlank { friend.userId.take(8) },
                                    color = Color(0xFFF0F0F0), fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFriendIds = tempSelected; showFriendPicker = false }) {
                    Text(stringResource(R.string.filter_apply), color = Color(0xFF6EE7B7))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFriendPicker = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFF8A8A8A))
                }
            }
        )
    }

    val scope = rememberCoroutineScope()

    // ── 3D 행성(글로브) 뷰 상태 — 줌을 충분히 빼면 하단 버튼이 뜨고, 눌러야 진입. ──
    var globeCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var globeReturn by remember { mutableStateOf<GlobeReturnCamera?>(null) }
    // 줌이 낮을 때 지도에서 보고되는 "지구 보기" 후보 중심(null = 버튼 숨김)
    var globeButtonCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // 지도(SurfaceView) ↔ 글로브(GLSurfaceView) 교체를 가리는 검정 디졸브 스크림
    val globeScrim = remember { Animatable(0f) }

    // 권한 요청은 MainActivity 가 앱 시작 즉시 수행. 여기서는 "허용되어 있으면" 위치 추적을 시작하고
    // 현재 위치로 카메라를 맞춘다. 권한 다이얼로그가 닫히며 액티비티가 ON_RESUME 될 때도 재시도해
    // 허용 직후 곧바로 위치가 반영되도록 한다(예전엔 허용해도 시작 코드가 없어 기본 좌표에 멈춰 있었음).
    val startLocationIfGranted: () -> Unit = start@{
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return@start
        LocationHelper.startContinuousUpdates(context)
        // 일회성 fix 를 한 번 당겨 진입 직후 빠르게 위치를 잡는다(결과는 LocationHelper.location flow 로 반영).
        if (LocationHelper.getCurrentLatLng() == null) {
            scope.launch { LocationHelper.getCurrentLocation(context) }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) startLocationIfGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentLatLng) {
        delay(600)
        diaryViewModel.prefetchNearby(diaries, currentLatLng.latitude, currentLatLng.longitude)
    }

    LaunchedEffect(Unit) {
        startLocationIfGranted()
        // WASD 위치 이동은 디버그 전용(실기기에선 포커스를 가로채지 않도록 요청도 디버그에서만).
        if (BuildConfig.DEBUG) runCatching { focusRequester.requestFocus() }
    }

    fun moveLocation(latDelta: Double, lngDelta: Double) {
        val newLatLng = LatLng(currentLatLng.latitude + latDelta, currentLatLng.longitude + lngDelta)
        currentLatLng = newLatLng
        LocationHelper.setCurrentLocation(newLatLng)
    }

    // WASD 로 현재 위치를 옮기는 건 개발/에뮬 테스트용 치트. 릴리즈에선 키 입력/포커스 탈취를 비활성화한다.
    val devKeyModifier = if (BuildConfig.DEBUG) {
        Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.W -> { moveLocation(step, 0.0); true }
                        Key.S -> { moveLocation(-step, 0.0); true }
                        Key.A -> { moveLocation(0.0, -step); true }
                        Key.D -> { moveLocation(0.0, step); true }
                        else -> false
                    }
                } else false
            }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(devKeyModifier)
    ) {
        // 알림에서 요청된 다이어리로 카메라 이동 + 파장 — 좌표는 필터와 무관하게 전체 목록에서 찾는다.
        val focusTarget = remember(MapFocusState.pendingDiaryId, MapFocusState.pendingRoute, diaries) {
            val id = MapFocusState.pendingDiaryId ?: return@remember null
            diaries.firstOrNull { it.id == id }?.let {
                DiaryFocusTarget(it.latitude, it.longitude, it.starColor, it.id, MapFocusState.pendingRoute)
            }
        }

        DiaryMap(
            diaries = filteredDiaries,
            currentLatLng = currentLatLng,
            onDiaryClick = onItemClick,
            onClusterClick = onOpenCluster,
            onCreateClick = onCreateClick,
            focusDiary = focusTarget,
            onFocusHandled = { MapFocusState.consume() },
            showCreate = userId != null, // 비로그인 시 업로드 버튼 숨김
            onGlobeAvailability = { lat, lng, available ->
                globeButtonCenter = if (available) lat to lng else null
            },
            globeReturnCamera = globeReturn,
        )

        // 필터 스피드 다이얼 (로그인한 경우 + 지도만 보기 모드가 아닐 때)
        if (userId != null && !MapUiState.mapOnly) {
            val anyActive = unviewedOnly || friendsOnly || myOnly || selectedFriendIds.isNotEmpty() || periodDays != null
            val mint = Color(0xFF6EE7B7)
            val pillBg = Color(0xEE111120)

            // "전체보기"는 기본 상태(필터 없음)와 같아 목록에서 제외 — 각 필터 재탭으로 해제.
            val filterOpts = listOf(
                FilterOpt(stringResource(R.string.filter_unviewed), Icons.Filled.FiberNew, unviewedOnly) {
                    unviewedOnly = !unviewedOnly; if (unviewedOnly) myOnly = false
                },
                FilterOpt(stringResource(R.string.filter_friends), Icons.Filled.People, friendsOnly) {
                    friendsOnly = !friendsOnly; if (friendsOnly) myOnly = false
                },
                FilterOpt(stringResource(R.string.filter_mine), Icons.Filled.Lock, myOnly) {
                    myOnly = !myOnly; if (myOnly) { friendsOnly = false; selectedFriendIds = emptySet() }
                },
                // 친구 선택 — 비활성이면 선택 다이얼로그, 활성이면 재탭으로 해제(기간 필터와 동일 패턴).
                FilterOpt(
                    if (selectedFriendIds.isEmpty()) stringResource(R.string.filter_pick_friends)
                    else stringResource(R.string.filter_friends_n, selectedFriendIds.size),
                    Icons.Filled.GroupAdd, selectedFriendIds.isNotEmpty()
                ) {
                    if (selectedFriendIds.isEmpty()) showFriendPicker = true
                    else selectedFriendIds = emptySet()
                },
                // 기간별 보기 — 비활성이면 다이얼로그(오늘/7일/30일/1년), 활성이면 재탭으로 해제.
                FilterOpt(
                    if (periodDays == null) stringResource(R.string.filter_period)
                    else stringResource(R.string.filter_period_n, periodLabel(periodDays)),
                    Icons.Filled.Schedule, periodDays != null
                ) {
                    if (periodDays == null) showPeriodPicker = true else periodDays = null
                },
                // (지도만 보기 항목은 제거됨 — 지도 우하단 몰입 버튼으로 대체)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 옵션 목록 (위로 펼쳐짐)
                AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        filterOpts.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (opt.isActive) mint.copy(alpha = 0.18f) else pillBg)
                                    .border(
                                        width = if (opt.isActive) 1.5.dp else 1.dp,
                                        color = if (opt.isActive) mint else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { opt.onClick() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = if (opt.isActive) mint else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = opt.label,
                                    color = if (opt.isActive) mint else Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 메인 FAB 원형 버튼
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.5.dp, if (anyActive) mint else Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { speedDialExpanded = !speedDialExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = stringResource(R.string.cd_filter),
                        tint = if (anyActive) mint else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── 하단 "지구 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
        AnimatedVisibility(
            visible = globeButtonCenter != null && globeCenter == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE111120))
                    .border(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable {
                        val (lat, lng) = globeButtonCenter ?: return@clickable
                        if (globeCenter == null) {
                            scope.launch {
                                globeScrim.snapTo(0f)
                                globeScrim.animateTo(1f, tween(170))
                                globeCenter = lat to lng
                                globeScrim.animateTo(0f, tween(520))
                            }
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.globe_open),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }

        // ── 3D 행성(글로브) 오버레이 — 버튼으로 진입, X 버튼/뒤로가기로 그 지점 지도 복귀 ──
        globeCenter?.let { (lat, lng) ->
            GlobeScreen(
                diaries = filteredDiaries,
                startLat = lat,
                startLng = lng,
                onRequestExit = { exitLat, exitLng ->
                    scope.launch {
                        globeScrim.animateTo(1f, tween(170))
                        globeReturn = GlobeReturnCamera(exitLat, exitLng, 4.0, System.nanoTime())
                        globeCenter = null
                        delay(70) // 지도 카메라 점프가 프레임에 반영될 시간
                        globeScrim.animateTo(0f, tween(380))
                    }
                },
            )
        }
        // 전환 스크림(검정 디졸브) — 지도 SurfaceView ↔ 글로브 GLSurfaceView 교체를 가린다.
        if (globeScrim.value > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = globeScrim.value))
            )
        }
    }
}

@Composable
fun DpadButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 18.sp, color = Color.White)
    }
}
