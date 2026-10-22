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
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
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
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.util.DiaryUnlockStore
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapFilterStore
import com.chaminwoo.stary.core.util.MapFilters
import com.chaminwoo.stary.core.util.MapFocusState
import com.chaminwoo.stary.core.util.MapUiState
import androidx.compose.foundation.layout.statusBarsPadding
import com.chaminwoo.stary.core.util.TutorialStarState
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.globe.GlobeScreen
import com.chaminwoo.stary.feature.map.screen.DiaryFocusTarget
import com.chaminwoo.stary.feature.map.screen.DiaryMap
import com.chaminwoo.stary.feature.map.screen.FilterKindTracker
import com.chaminwoo.stary.feature.map.screen.GlobeReturnCamera
import com.chaminwoo.stary.feature.map.screen.MapFilterKind
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
    /** 첫 별 안내 카드를 띄워도 되는지(로그인 화면·코치마크·약관 창이 없을 때) — MainScreen 이 정한다. */
    guideEnabled: Boolean = true,
    /** 첫 사용 안내의 "프로필 바꾸기" — 프로필 화면으로. */
    onOpenProfile: () -> Unit = {},
    /** 별빛 공방(퀘스트 창 별가루 칩 · 안내의 "열쇠 만들기"). */
    onOpenWorkshop: () -> Unit = {},
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

    // 첫 실행 기기 전용 웰컴 별 — 실제 위치 fix 가 잡히면 그 근방에 1회 배치(TutorialStarState 참고).
    // 로그인 상태면 **새 계정으로 확인됐을 때만** — 이전에 가입한 계정은 재설치여도 웰컴 별을 띄우지 않는다(ReturningAccountOnboarding).
    val returningAccount by GoogleAuthHelper.returningAccount.collectAsState()
    LaunchedEffect(liveLocation, TutorialStarState.done, returningAccount) {
        if (GoogleAuthHelper.currentUserId != null && returningAccount != false) return@LaunchedEffect
        liveLocation?.let { TutorialStarState.ensurePlaced(context, it) }
    }

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
    // 로그인 때 currentUserId 는 Firebase Auth 가 끝난 뒤에 채워져 위 리스너보다 늦을 수 있다 — 값 자체도 따라간다.
    val observedUserId = GoogleAuthHelper.currentUserId
    LaunchedEffect(observedUserId) { userId = observedUserId }
    // 필터는 앱을 껐다 켜도 유지된다(계정별 저장 — MapFilterStore). 계정이 바뀌면 그 계정의 마지막 필터로 다시 읽는다.
    val savedFilters = remember(userId) { MapFilterStore.load(context, userId) }
    // 미해금만 — 눌렀을 때 잠금 화면이 뜨는 별만(내 글도 아니고, 해금한 적도 없고, 지금 100m 밖). 해금만의 정확한 반대.
    var lockedOnly by remember(userId) { mutableStateOf(savedFilters.lockedOnly) }
    var friendsOnly by remember(userId) { mutableStateOf(savedFilters.friendsOnly) }
    var myOnly by remember(userId) { mutableStateOf(savedFilters.myOnly) }
    // 해금만 — 잠금 없이 바로 열리는 별만(내 글 / 100m 이내 / 영구 해금). DetailScreen 의 unlocked 규칙과 동일.
    var unlockedOnly by remember(userId) { mutableStateOf(savedFilters.unlockedOnly) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var selectedFriendIds by remember(userId) { mutableStateOf(savedFilters.selectedFriendIds) }
    // 기간별 보기 — null=전체 기간, 0=오늘(로컬 자정 이후), 그 외 N=최근 N일
    var periodDays by remember(userId) { mutableStateOf(savedFilters.periodDays) }
    // 필터가 바뀔 때마다 저장(uid 가 바뀐 컴포지션에서는 위 상태가 이미 새 계정 값으로 다시 읽힌 뒤라 섞이지 않는다).
    LaunchedEffect(userId, lockedOnly, friendsOnly, myOnly, unlockedOnly, selectedFriendIds, periodDays) {
        MapFilterStore.save(
            context, userId,
            MapFilters(lockedOnly, friendsOnly, myOnly, unlockedOnly, selectedFriendIds, periodDays)
        )
    }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var speedDialExpanded by remember { mutableStateOf(false) }
    // 집중 모드로 들어가면 펼친 필터 다이얼도 접는다(나왔을 때 옵션이 덮고 있지 않게).
    LaunchedEffect(MapUiState.mapOnly) { if (MapUiState.mapOnly) speedDialExpanded = false }
    // 오늘의 퀘스트 창(지도 "!" 버튼).
    var showQuests by remember { mutableStateOf(false) }
    // 첫 사용 안내 스포트라이트(MainScreen)에서 "!" 를 누르면 여기서 창을 연다 — 이 화면이 다시 그려질 때 예전 요청으로 열리지 않게 처리한 값을 기억.
    var handledQuestRequest by remember { androidx.compose.runtime.mutableIntStateOf(com.chaminwoo.stary.feature.quest.GuideUi.questRequest) }
    LaunchedEffect(com.chaminwoo.stary.feature.quest.GuideUi.questRequest) {
        val req = com.chaminwoo.stary.feature.quest.GuideUi.questRequest
        if (req != handledQuestRequest) {
            handledQuestRequest = req
            showQuests = true
        }
    }

    // 지금 색을 정하는 필터 = 가장 마지막에 켠 필터 — 빛의 고리(필터 전환 등장)와 지도에 도는 은은한 빛의 색.
    // 필터가 없으면 null(빛 꺼짐). 필터 조합(revealKey)과 같은 컴포지션에서 값이 정해져야 링 색이 한 박자 늦지 않는다.
    val filterKindTracker = remember(userId) { FilterKindTracker() }
    val filterKind = filterKindTracker.update(
        buildList {
            if (lockedOnly) add(MapFilterKind.LOCKED)
            if (unlockedOnly) add(MapFilterKind.UNLOCKED)
            if (friendsOnly) add(MapFilterKind.FRIENDS)
            if (myOnly) add(MapFilterKind.MINE)
            if (selectedFriendIds.isNotEmpty()) add(MapFilterKind.PICK_FRIENDS)
            if (periodDays != null) add(MapFilterKind.PERIOD)
        }
    )

    val friends by remember(userId) {
        val uid = userId
        if (uid != null) FirebaseFriendRepository().observeFriends(uid)
        else flowOf(emptyList<Friend>())
    }.collectAsState(initial = emptyList())

    val friendIds = remember(friends) { friends.map { it.userId }.toSet() }
    // 퀘스트 "친구 사귀기" — 오늘 맺어진 친구(내가 수락했든, 내 요청이 수락됐든). 같은 사람은 하루 한 번(QuestStore key).
    LaunchedEffect(friends) {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        friends.filter { it.createdAt >= todayStart }.forEach {
            com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.FRIEND_ADD, key = it.userId)
        }
    }

    // "해금만"/"미해금만" 필터용 — 영구 해금 기록(상태 맵이라 해금되는 순간 이 화면도 갱신된다).
    val unlockedIds = DiaryUnlockStore.unlockedIds(context)
    // 서버 해금 사본 + 잠금 이전 열람 기록을 로컬에 합친다(프로세스당 1회 — 별 도감·상세 잠금·이 필터 공용).
    LaunchedEffect(userId) {
        userId?.let { DiaryUnlockStore.syncWithServer(context, it) }
    }
    // 100m 판정에 쓸 좌표는 **실제 fix**(liveLocation)만 — currentLatLng 는 마지막/기본 좌표 폴백이라
    // 엉뚱한 곳의 별이 "근처"로 잡힐 수 있다. 두 필터(해금만/미해금만)가 모두 꺼져 있으면 null 이라 위치가 갱신돼도 재계산하지 않고,
    // 켜져 있어도 약 11m 격자로 반올림해 GPS 지터마다 목록/지도 레이어가 흔들리는 걸 막는다.
    val unlockFix: LatLng? = if (unlockedOnly || lockedOnly) liveLocation?.let {
        LatLng(
            Math.round(it.latitude * 10_000.0) / 10_000.0,
            Math.round(it.longitude * 10_000.0) / 10_000.0,
        )
    } else null

    // 내가 차단한 사용자 — 그 사람의 별은 지도/목록에서 숨긴다.
    val blockedIds by remember(userId) {
        val uid = userId
        if (uid != null) com.chaminwoo.stary.data.repository.FirebaseModerationRepository()
            .observeBlockedIds(uid)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())

    val filteredDiaries = remember(
        diaries,
        lockedOnly,
        friendsOnly,
        myOnly,
        unlockedOnly,
        unlockedIds,
        unlockFix,
        selectedFriendIds,
        friendIds,
        blockedIds,
        userId,
        periodDays
    ) {
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
            // 해금만: 내 글 / 이미 해금한 글 / 지금 100m 이내 — 셋 중 하나면 통과.
            // (= 이 필터로 보이는 별은 눌렀을 때 잠금 화면이 뜨지 않는다)
            val openNow = diary.userId == userId || diary.id in unlockedIds ||
                    (unlockFix != null && LocationHelper.distanceBetween(
                        unlockFix.latitude, unlockFix.longitude, diary.latitude, diary.longitude
                    ) <= StaryConfig.DIARY_OPEN_RADIUS_M)
            // 미해금만: 위 "바로 열림" 의 정확한 반대 — 눌렀을 때 잠금 화면이 뜨는 별만. 실제 위치 fix 가 없으면 근처가 아니므로 잠긴 쪽.
            val lockOk = when {
                unlockedOnly -> openNow
                lockedOnly -> !openNow
                else -> true
            }
            val filterOk = (!friendsOnly || diary.userId in friendIds) &&
                    (!myOnly || diary.userId == userId) &&
                    lockOk &&
                    (selectedFriendIds.isEmpty() || diary.userId in selectedFriendIds) &&
                    (periodCutoff == null || diary.createdAt >= periodCutoff)
            // 부적절한 표현이 든 **남의** 글은 지도에서 걸러낸다(App Store 1.2 — 필터 이전 글·구버전 앱 글 대비).
            visibilityOk && filterOk && diary.userId !in blockedIds &&
                (diary.userId == userId || !com.chaminwoo.stary.core.util.ContentFilter.anyObjectionable(diary.title, diary.content))
        }
    }

    // 기간별 보기 다이얼로그 — 전체/오늘/7일/30일/1년 중 선택
    if (showPeriodPicker) {
        AlertDialog(
            onDismissRequest = { showPeriodPicker = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(
                    stringResource(R.string.filter_period),
                    color = Color(0xFFF0F0F0),
                    fontSize = 16.sp
                )
            },
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
                                    selectedColor = Color(0xFF9FB3E8),
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

    // 친구 선택 다이얼로그(별 도감과 공용 — core/ui/FriendPickerDialog)
    if (showFriendPicker) {
        com.chaminwoo.stary.core.ui.FriendPickerDialog(
            friends = friends,
            initial = selectedFriendIds,
            onApply = { selectedFriendIds = it; showFriendPicker = false },
            onDismiss = { showFriendPicker = false },
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

    // 다른 화면으로 나가면(지도 숨김) 글로브를 닫는다 — 이 화면이 NavHost 밖 상시 렌더로 바뀌어
    // 이탈해도 파괴되지 않으므로, 가려진 GLSurfaceView 가 계속 도는 낭비를 막고
    // "복귀하면 지도"라는 기존 동작(예전엔 이탈 시 통째로 파괴돼 글로브도 닫혔음)을 유지한다.
    LaunchedEffect(MapUiState.mapVisible) {
        if (!MapUiState.mapVisible && globeCenter != null) {
            globeCenter = null
            globeScrim.snapTo(0f)
        }
    }

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
        val newLatLng =
            LatLng(currentLatLng.latitude + latDelta, currentLatLng.longitude + lngDelta)
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
                        Key.W -> {
                            moveLocation(step, 0.0); true
                        }

                        Key.S -> {
                            moveLocation(-step, 0.0); true
                        }

                        Key.A -> {
                            moveLocation(0.0, -step); true
                        }

                        Key.D -> {
                            moveLocation(0.0, step); true
                        }

                        else -> false
                    }
                } else false
            }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(devKeyModifier)
    ) {
        // 웰컴 별(TutorialStarState) — 서버에 없는 클라이언트 전용 합성 다이어리라 필터와 무관하게
        // filteredDiaries 뒤에 덧붙인다. 모양/색/작성자는 게시물 화면과 공유(TutorialStarState 상수).
        val tutorialDiary = remember(TutorialStarState.latLng) {
            TutorialStarState.latLng?.let { pos ->
                Diary(
                    id = TutorialStarState.DIARY_ID,
                    userId = TutorialStarState.AUTHOR_ID,
                    userName = TutorialStarState.AUTHOR_NAME,
                    latitude = pos.latitude,
                    longitude = pos.longitude,
                    createdAt = System.currentTimeMillis(),
                    starType = TutorialStarState.STAR_TYPE,
                    starColor = TutorialStarState.STAR_COLOR,
                    visibilityType = "public",
                )
            }
        }
        val mapDiaries = remember(filteredDiaries, tutorialDiary) {
            tutorialDiary?.let { filteredDiaries + it } ?: filteredDiaries
        }

        // 알림에서 요청된 다이어리로 카메라 이동 + 파장 — 좌표는 필터와 무관하게 전체 목록에서 찾는다.
        // ⚠️ 대상 별이 구독 목록(최신 1000개)에 없을 수 있다(목록 로드 전·오래된 별·삭제). 예전엔 그러면 아무 일도
        //    일어나지 않고 요청도 남아(이후 지도 복귀 재센터까지 막힘) "알림을 눌러도 그 별로 안 간다"가 됐다 →
        //    목록에 없으면 Firestore 에서 직접 읽고, 그래도 없으면 안내 후 요청을 비운다(iOS MapScreen.handleFocus 패리티).
        val pendingFocusId = MapFocusState.pendingDiaryId
        var fetchedFocus by remember { mutableStateOf<Diary?>(null) }
        LaunchedEffect(pendingFocusId, diaries) {
            val id = pendingFocusId
            if (id == null) {
                fetchedFocus = null
                return@LaunchedEffect
            }
            if (fetchedFocus?.id == id || diaries.any { it.id == id }) return@LaunchedEffect
            // 목록이 아직 비어 있으면 막 켜진 직후일 수 있다 — 잠깐 기다린다(목록이 도착하면 이 effect 가 다시 돈다).
            if (diaries.isEmpty()) delay(1500)
            val d = com.chaminwoo.stary.data.repository.FirebaseDiaryRepository().getDiaryById(id)
            if (MapFocusState.pendingDiaryId != id) return@LaunchedEffect // 그 사이 다른 요청으로 바뀜
            val visible = d != null && (d.latitude != 0.0 || d.longitude != 0.0) &&
                (d.visibilityType != "private" || d.userId == userId)
            if (visible) {
                fetchedFocus = d
            } else {
                MapFocusState.consume()
                com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.notif_diary_gone))
            }
        }
        val focusTarget =
            remember(pendingFocusId, diaries, fetchedFocus) {
                val id = pendingFocusId ?: return@remember null
                (diaries.firstOrNull { it.id == id } ?: fetchedFocus?.takeIf { it.id == id })?.let {
                    DiaryFocusTarget(it.latitude, it.longitude, it.starColor, it.id)
                }
            }

        DiaryMap(
            diaries = mapDiaries,
            currentLatLng = currentLatLng,
            // 웰컴 별도 일반 별과 같은 경로(파장 → Detail 라우트) — NavGraph 가 id 를 보고 튜토리얼 게시물로 분기.
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
            // 필터 조합이 바뀌면 화면 중심에서 빛의 고리가 퍼지며 링이 닿는 별부터 다시 생긴다. 위치 갱신(unlockFix)·
            // 데이터 실시간 갱신은 여기 안 들어가므로 그때는 기존 합쳐짐/펼쳐짐 보간만 돈다.
            revealKey = listOf(
                lockedOnly, friendsOnly, myOnly, unlockedOnly, selectedFriendIds, periodDays,
            ),
            // 필터마다 다른 색 — 고리와 지도에 도는 은은한 빛을 물들인다(필터 없으면 null).
            filterTint = filterKind?.color,
            // 필터가 걸려 있으면 남은 별의 후광을 조금 더 밝게(2026-10-10).
            highlight = lockedOnly || friendsOnly || myOnly || unlockedOnly ||
                    selectedFriendIds.isNotEmpty() || periodDays != null,
            onQuestClick = if (userId != null) {
                { showQuests = true }
            } else null,
            modifier = modifier,
        )
        // 첫 사용 안내 — 퀘스트 창을 열면 "!" 스포트라이트 단계가 끝나고, 첫 퀘스트(프로필 바꾸기) 행을 강조한다.
        val guideNow = com.chaminwoo.stary.feature.quest.guideStep(context, userId, diaries, liveLocation)
        LaunchedEffect(showQuests, guideNow, userId) {
            val uid = userId ?: return@LaunchedEffect
            if (showQuests && guideNow == com.chaminwoo.stary.feature.quest.GuideStep.QUEST) {
                com.chaminwoo.stary.feature.quest.GuideProgress.markQuestSeen(context, uid)
            }
        }
        if (showQuests) {
            val guidingProfile = guideNow == com.chaminwoo.stary.feature.quest.GuideStep.QUEST ||
                guideNow == com.chaminwoo.stary.feature.quest.GuideStep.PROFILE
            com.chaminwoo.stary.feature.quest.QuestSheet(
                onDismiss = { showQuests = false },
                highlightId = if (guidingProfile) com.chaminwoo.stary.feature.quest.DailyQuests.PROFILE_EDIT_ID else null,
                onGo = { def ->
                    showQuests = false
                    com.chaminwoo.stary.core.util.StaryAnalytics.log("guide_action", "step" to "quest_go_${def.id}")
                    if (def.event == com.chaminwoo.stary.feature.quest.QuestEvent.PROFILE_EDIT) onOpenProfile()
                },
                onOpenWorkshop = {
                    showQuests = false
                    onOpenWorkshop()
                },
            )
        }

        // 첫 별 안내(첫 별 → 퀘스트 열쇠 → 남의 별 열기) — 화면 가운데 카드(2026-10-22: 필터 버튼 옆은 어색해서 옮김).
        com.chaminwoo.stary.feature.quest.FirstStarGuide(
            userId = userId,
            diaries = diaries,
            here = liveLocation,
            unlockedIds = unlockedIds,
            blockedIds = blockedIds,
            enabled = guideEnabled && !showQuests && !MapUiState.mapOnly && MapUiState.mapVisible && globeCenter == null,
            onWrite = onCreateClick,
            onOpenQuests = { showQuests = true },
            onOpenProfile = onOpenProfile,
            onOpenWorkshop = onOpenWorkshop,
            onOpenStar = onItemClick,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
        )

        // 필터 스피드 다이얼 (로그인한 경우) — 집중(지도만 보기) 모드에선 다른 버튼과 같이 페이드로 숨는다.
        if (userId != null) {
            val anyActive =
                lockedOnly || friendsOnly || myOnly || unlockedOnly ||
                        selectedFriendIds.isNotEmpty() || periodDays != null
            val mint = Color(0xFF9FB3E8)
            val pillBg = Color(0xEE111120)

            // "전체보기"는 기본 상태(필터 없음)와 같아 목록에서 제외 — 각 필터 재탭으로 해제.
            val filterOpts = listOf(
                // 미해금만 — 눌렀을 때 잠금 화면이 뜨는 별(남의 글 + 해금 안 함 + 100m 밖)만 남긴다. 아직 열지 못한 별 찾기용.
                FilterOpt(
                    stringResource(R.string.filter_locked),
                    Icons.Filled.LockClock,
                    lockedOnly
                ) {
                    // 미해금 ↔ 해금만은 정확히 반대말, 내 글은 항상 열려 있으니 나만보기와도 같이 켜면 항상 빈 지도 → 서로 끈다.
                    lockedOnly = !lockedOnly
                    if (lockedOnly) { myOnly = false; unlockedOnly = false }
                },
                // 해금만 — 눌렀을 때 잠금 화면이 뜨지 않는 별(내 글/해금/100m 이내)만 남긴다.
                FilterOpt(
                    stringResource(R.string.filter_unlocked),
                    Icons.Filled.LockOpen,
                    unlockedOnly
                ) {
                    unlockedOnly = !unlockedOnly; if (unlockedOnly) lockedOnly = false
                },
                FilterOpt(
                    stringResource(R.string.filter_friends),
                    Icons.Filled.People,
                    friendsOnly
                ) {
                    friendsOnly = !friendsOnly; if (friendsOnly) myOnly = false
                },
                FilterOpt(stringResource(R.string.filter_mine), Icons.Filled.Lock, myOnly) {
                    myOnly = !myOnly; if (myOnly) {
                    friendsOnly = false; selectedFriendIds = emptySet(); lockedOnly = false
                }
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

            AnimatedVisibility(
                visible = !MapUiState.mapOnly,
                enter = fadeIn(tween(MapUiState.CHROME_SHOW_MS)),
                exit = fadeOut(tween(MapUiState.CHROME_HIDE_MS)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp)
                    .navigationBarsPadding(),
            ) {
            Column(
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
                                    .clickable {
                                        val wasActive = opt.isActive
                                        opt.onClick()
                                        if (!wasActive) com.chaminwoo.stary.feature.quest.QuestStore.report(
                                            com.chaminwoo.stary.feature.quest.QuestEvent.FILTER
                                        )
                                    }
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
                        .border(
                            1.5.dp,
                            if (anyActive) mint else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
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
            } // AnimatedVisibility(!mapOnly)
        }

        // ── 하단 "지구 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
        AnimatedVisibility(
            visible = globeButtonCenter != null && globeCenter == null && !MapUiState.mapOnly,
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
                    .border(1.dp, Color(0xFF9FB3E8).copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable {
                        val (lat, lng) = globeButtonCenter ?: return@clickable
                        if (globeCenter == null) {
                            scope.launch {
                                globeScrim.snapTo(0f)
                                globeScrim.animateTo(1f, tween(170))
                                globeCenter = lat to lng
                                com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.GLOBE)
                                globeScrim.animateTo(0f, tween(520))
                            }
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = Color(0xFF9FB3E8),
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
