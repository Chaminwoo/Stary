package com.chaminwoo.stary.feature.profile.screen

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.designsystem.TextPrimary
import com.chaminwoo.stary.core.designsystem.TextSub
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import com.chaminwoo.stary.core.ui.FriendPickerDialog
import com.chaminwoo.stary.core.ui.StaryEmptyState
import com.chaminwoo.stary.core.ui.raisedCosmicBorder
import com.chaminwoo.stary.core.util.DiaryUnlockStore
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MusicManager
import com.chaminwoo.stary.core.util.rememberCurrentUserName
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseModerationRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 별 도감 — 해금한(열어 본) **다른 사람의** 별을 화면 중앙의 원 위에 모아 보는 화면(2026-09-25).
 *
 *  - 들어오면 12시 방향부터 **시계 방향으로 별이 하나씩 빠르게** 떠올라 약 1.5초 만에 원이 완성된다
 *    (별마다 지도 필터 전환과 같은 "톡톡 반짝" — MusicManager.playSparkTick).
 *  - 화면을 **누른 채로 별 위를 지나가면** 그 별이 살짝 커지고, 원 가운데에 제목·작성자·지도 버튼이 뜬다.
 *    손을 떼도 선택은 유지된다. 글씨는 그 별의 색을 따르고 같은 색 후광을 두른다.
 *  - "지도에서 보기" → 지도로 돌아가 그 별 위치로 카메라 + 파장(MapFocusState — 프로필 핀 별과 같은 로직).
 *  - 정렬(해금순/최신순/거리순/인기순) · 필터(친구만/친구 선택) — 바꿀 때마다 원이 다시 그려진다.
 *
 * 데이터 = DiaryUnlockStore(영구 해금 기록) ∩ 지금 존재하는 글(삭제·비공개 전환·차단 사용자 제외).
 *   해금 기록 = **실제로 열람한 모든 글** — 100m 접근 · 광고 · 잠금이 생기기 전(2026-09-21)의 열람까지
 *   (2026-09-26 "광고로 연 것만 뜬다" 피드백 → 서버 사본과 합침, DiaryUnlockStore.syncWithServer).
 * 내 글은 항상 열려 있으니 넣지 않는다(내 다이어리에 있다).
 * iOS: Features/Profile/StarLogScreen.swift — 아래 RING_* 수치를 그대로 복제해 둔다(값 drift 금지).
 */

/** 원 위 배치 순서(12시부터 시계 방향). */
private enum class StarLogSort { UNLOCKED, LATEST, DISTANCE, POPULAR }

/** 첫 별 시작 → 마지막 별 완성까지(ms). */
private const val RING_REVEAL_MS = 1500f
/** 별 하나가 톡 떠오르는 시간(ms). */
private const val RING_POP_MS = 260f
/** 별이 적을 때 별 사이 최대 간격(ms) — 몇 개 안 되면 1.5초를 다 쓰지 않고 빠르게 끝난다. */
private const val RING_MAX_GAP_MS = 120f
/** 원 반지름 = 화면 폭 × 이 값(세로가 모자라면 위아래 알약을 피하는 만큼으로 줄인다). */
private const val RING_RADIUS_FRAC = 0.40f
/** 별 지름(dp) 범위 — 별이 많을수록 작아진다(원 둘레 ÷ 개수 × 0.62). */
private const val RING_STAR_MIN_DP = 12f
private const val RING_STAR_MAX_DP = 34f
/** 고른 별 확대 배율. */
private const val RING_SELECT_SCALE = 1.45f
/** 손가락-별 판정 반경 하한(dp). */
private const val RING_HIT_DP = 26f

/** i 번째 별(0 = 12시)이 떠오르기 시작하는 시각(ms). */
private fun ringAppearMs(i: Int, n: Int): Float {
    if (n <= 1) return 0f
    val gap = min(RING_MAX_GAP_MS, (RING_REVEAL_MS - RING_POP_MS) / (n - 1))
    return i * gap
}

/** 톡 떠오르는 크기 곡선 — 0.3 에서 1.2 까지 튀어 오른 뒤 1 로 가라앉는다. */
private fun ringPop(u: Float): Float = when {
    u <= 0f -> 0f
    u < 0.6f -> 0.3f + 0.9f * (1f - (1f - u / 0.6f).let { it * it })
    else -> 1.2f - 0.2f * ((u - 0.6f) / 0.4f)
}

@Composable
fun StarLogScreen(
    onOpenMap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val userId = GoogleAuthHelper.currentUserId
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.86f), blendMode = BlendMode.Darken)
        )
        if (userId == null) {
            Text(
                stringResource(R.string.common_login_required),
                color = TextSub, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            StarLogContent(userId = userId, onOpenMap = onOpenMap)
            FirstVisitInfo(
                seenKey = "info_starlog",
                icon = Icons.Filled.Stars,
                title = stringResource(R.string.onb_starlog_title),
                message = stringResource(R.string.onb_starlog_msg),
            )
        }
    }
}

@Composable
private fun StarLogContent(userId: String, onOpenMap: (String) -> Unit) {
    val context = LocalContext.current
    // 지도(MainListScreen)와 **같은 액티비티 범위 뷰모델**을 쓴다 — 이미 받아 둔 목록이 바로 보여
    // 원이 빈 채로 시작했다가 데이터가 와서 다시 그려지는 일이 없다.
    val activity = remember(context) { context.findComponentActivity() }
    val diaryViewModel: DiaryViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = DiaryViewModel.factory())
    } else viewModel(factory = DiaryViewModel.factory())
    val diaries by diaryViewModel.diaries.collectAsState()
    val unlockedAt = DiaryUnlockStore.unlockedAt(context)
    // 지도에서 이미 끝났으면 즉시 반환(프로세스당 1회) — 지도를 거치지 않고 들어온 경우 대비.
    LaunchedEffect(userId) { DiaryUnlockStore.syncWithServer(context, userId) }
    val friends by remember(userId) { FirebaseFriendRepository().observeFriends(userId) }
        .collectAsState(initial = emptyList<Friend>())
    val friendIds = remember(friends) { friends.mapTo(HashSet()) { it.userId } }
    val blockedIds by remember(userId) { FirebaseModerationRepository().observeBlockedIds(userId) }
        .collectAsState(initial = emptySet())
    val liveLocation by LocationHelper.location.collectAsState()
    val fallbackLocation = remember {
        LocationHelper.lastSavedLatLng(context) ?: LatLng(StaryConfig.DEFAULT_LAT, StaryConfig.DEFAULT_LNG)
    }
    val here = liveLocation ?: fallbackLocation

    var sort by rememberSaveable { mutableStateOf(StarLogSort.UNLOCKED) }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    var selectedFriendIds by remember { mutableStateOf(emptySet<String>()) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // 거리순 기준점은 정렬을 고른 순간의 위치로 고정 — 걷는 동안 원이 계속 재배열되지 않게.
    val anchor = remember(sort) { here }

    val collected = remember(diaries, unlockedAt, userId, friendIds, blockedIds) {
        diaries.filter { d ->
            d.id in unlockedAt && d.userId != userId && d.userId !in blockedIds &&
                d.visibilityType != "private" &&
                (d.visibilityType != "friends" || d.userId in friendIds)
        }
    }
    val shown = remember(collected, unlockedAt, sort, friendsOnly, selectedFriendIds, friendIds, anchor) {
        val list = collected.filter {
            (!friendsOnly || it.userId in friendIds) &&
                (selectedFriendIds.isEmpty() || it.userId in selectedFriendIds)
        }
        when (sort) {
            StarLogSort.UNLOCKED -> list.sortedByDescending { unlockedAt[it.id] ?: 0L }
            StarLogSort.LATEST -> list.sortedByDescending { it.createdAt }
            StarLogSort.POPULAR -> list.sortedByDescending { it.likeCount }
            StarLogSort.DISTANCE -> list.sortedBy {
                LocationHelper.distanceBetween(anchor.latitude, anchor.longitude, it.latitude, it.longitude)
            }
        }
    }
    val selected = shown.firstOrNull { it.id == selectedId }

    // ── 시계 + 원 등장 ──
    // clock 은 Canvas 그리기 단계에서만 읽는다 → 매 프레임 다시 그리지만 리컴포즈는 일어나지 않는다.
    val clock = remember { mutableFloatStateOf(0f) }
    val revealStart = remember { mutableFloatStateOf(0f) }
    val revealCount = remember { mutableIntStateOf(0) }
    val sounded = remember { mutableIntStateOf(0) }
    val revealKey = listOf(sort, friendsOnly, selectedFriendIds, shown.isNotEmpty())
    LaunchedEffect(revealKey) {
        revealStart.floatValue = clock.floatValue
        revealCount.intValue = shown.size
        sounded.intValue = 0
    }
    LaunchedEffect(Unit) {
        var t0 = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (t0 < 0L) t0 = now
                clock.floatValue = (now - t0) / 1_000_000f
            }
            // 별이 떠오르는 순간마다 "톡톡 반짝"(간격이 촘촘하면 MusicManager 가 알아서 솎는다)
            val n = revealCount.intValue
            val elapsed = clock.floatValue - revealStart.floatValue
            while (sounded.intValue < n && elapsed >= ringAppearMs(sounded.intValue, n)) {
                MusicManager.playSparkTick()
                sounded.intValue++
            }
        }
    }

    // 탑바가 반투명이라 화면은 탑바 뒤까지 차지한다 — 콘텐츠는 LocalTopBarInset 만큼 내려서 시작(다른 화면과 동일).
    val topClear = com.chaminwoo.stary.core.designsystem.LocalTopBarInset.current + 12.dp
    val bottomClear = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            collected.isEmpty() -> StaryEmptyState(
                title = stringResource(R.string.starlog_empty_title),
                description = stringResource(R.string.starlog_empty_desc),
                modifier = Modifier.align(Alignment.Center),
            )
            shown.isEmpty() -> StaryEmptyState(
                title = stringResource(R.string.starlog_filtered_empty),
                modifier = Modifier.align(Alignment.Center),
            )
            else -> StarRing(
                stars = shown,
                selectedId = selectedId,
                onSelect = { id ->
                    if (id != selectedId) {
                        selectedId = id
                        Haptics.tick()
                    }
                },
                clock = clock,
                revealStart = revealStart,
                here = here,
                unlockedAt = unlockedAt,
                selected = selected,
                onOpenMap = onOpenMap,
                // 원 중심은 화면 정중앙 — 위(정렬)·아래(필터) 알약과 겹치지 않을 만큼만 반지름을 제한.
                verticalReserve = maxOf(topClear + 52.dp, bottomClear + 56.dp),
            )
        }

        if (collected.isNotEmpty()) {
            // 정렬 — 상단 알약
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topClear)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StarLogSort.entries.forEach { s ->
                    LogChip(label = sortLabel(s), selected = sort == s) { sort = s }
                }
            }
            // 필터 — 하단 알약(지도 필터와 같은 동작: 친구 선택은 다시 누르면 해제)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomClear),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogChip(
                    label = stringResource(R.string.filter_friends),
                    selected = friendsOnly,
                    icon = Icons.Filled.People,
                ) { friendsOnly = !friendsOnly }
                LogChip(
                    label = if (selectedFriendIds.isEmpty()) stringResource(R.string.filter_pick_friends)
                    else stringResource(R.string.filter_friends_n, selectedFriendIds.size),
                    selected = selectedFriendIds.isNotEmpty(),
                    icon = Icons.Filled.GroupAdd,
                ) {
                    if (selectedFriendIds.isEmpty()) showFriendPicker = true else selectedFriendIds = emptySet()
                }
            }
        }
    }

    if (showFriendPicker) {
        FriendPickerDialog(
            friends = friends,
            initial = selectedFriendIds,
            onApply = { selectedFriendIds = it; showFriendPicker = false },
            onDismiss = { showFriendPicker = false },
        )
    }
}

@Composable
private fun sortLabel(s: StarLogSort): String = when (s) {
    StarLogSort.UNLOCKED -> stringResource(R.string.starlog_sort_unlocked)
    StarLogSort.LATEST -> stringResource(R.string.sort_latest)
    StarLogSort.DISTANCE -> stringResource(R.string.sort_distance)
    StarLogSort.POPULAR -> stringResource(R.string.sort_popular)
}

/** 별의 원 + 가운데 정보. 원 중심 = 이 영역의 중심(화면 중앙). */
@Composable
private fun StarRing(
    stars: List<Diary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    clock: androidx.compose.runtime.MutableFloatState,
    revealStart: androidx.compose.runtime.MutableFloatState,
    here: LatLng,
    unlockedAt: Map<String, Long>,
    selected: Diary?,
    onOpenMap: (String) -> Unit,
    verticalReserve: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    // 크리스탈 별 이미지 캐시(모양×색) — 매 프레임 결정 패싯을 새로 그리지 않도록 한 번 굽는다.
    val starImages = remember { HashMap<Int, ImageBitmap>() }
    // 고른 별 확대/후광 진행도(별 id → 0..1) — 그리기 단계에서 프레임 시간만큼 목표로 따라간다.
    val hover = remember { HashMap<String, Float>() }
    val lastDraw = remember { floatArrayOf(-1f) }
    val selectedIdRef = rememberUpdatedState(selectedId)
    val onSelectRef = rememberUpdatedState(onSelect)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val center = Offset(w / 2f, h / 2f)
        val radius = with(density) {
            min(w * RING_RADIUS_FRAC, h / 2f - verticalReserve.toPx() - RING_STAR_MAX_DP.dp.toPx() / 2f)
                .coerceAtLeast(80.dp.toPx())
        }
        val n = stars.size
        val starPx = with(density) {
            (2f * PI.toFloat() * radius / max(n, 1) * 0.62f)
                .coerceIn(RING_STAR_MIN_DP.dp.toPx(), RING_STAR_MAX_DP.dp.toPx())
        }
        val positions = remember(n, radius, center) {
            List(n) { i ->
                val a = -PI / 2 + 2 * PI * i / n
                Offset(center.x + (cos(a) * radius).toFloat(), center.y + (sin(a) * radius).toFloat())
            }
        }
        val hitPx = with(density) { max(starPx * 0.8f, RING_HIT_DP.dp.toPx()) }
        val ids = stars.map { it.id }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 누른 채로 별 위를 지나가면 그 별이 선택된다(손을 떼도 유지). 가장 가까운 별 하나만.
                .pointerInput(ids, positions, hitPx) {
                    fun pick(pos: Offset) {
                        var best = -1
                        var bestD = Float.MAX_VALUE
                        positions.forEachIndexed { i, p ->
                            val d = (p - pos).getDistance()
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (best >= 0 && bestD <= hitPx && ids[best] != selectedIdRef.value) {
                            onSelectRef.value(ids[best])
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pick(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            pick(ch.position)
                        }
                    }
                }
        ) {
            val now = clock.floatValue
            val elapsed = now - revealStart.floatValue
            val dt = if (lastDraw[0] < 0f) 16f else (now - lastDraw[0]).coerceIn(0f, 64f)
            lastDraw[0] = now

            // 1) 원 가이드 — 드러나는 동안 12시부터 시계 방향으로 쓸고, 완성 뒤엔 아주 옅게 남는다.
            //    (원주를 따라 돌던 민트 빛 머리는 2026-09-25 사용자 요청으로 삭제 — 옅은 흰 선만 남긴다.)
            val sweep = (elapsed / RING_REVEAL_MS).coerceIn(0f, 1f)
            val guideTopLeft = Offset(center.x - radius, center.y - radius)
            val guideSize = Size(radius * 2f, radius * 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                topLeft = guideTopLeft, size = guideSize,
                style = Stroke(width = 1.dp.toPx()),
            )
            // 2) 별 — 톡 떠오르기 + 은은한 반짝임 + 고른 별 확대·후광
            stars.forEachIndexed { i, d ->
                val u = ((elapsed - ringAppearMs(i, n)) / RING_POP_MS).coerceIn(0f, 1f)
                if (u <= 0f) return@forEachIndexed
                val pop = ringPop(u)
                val alpha = min(1f, u / 0.5f)
                val target = if (d.id == selectedId) 1f else 0f
                val hv0 = hover[d.id] ?: 0f
                val hv = hv0 + (target - hv0) * min(1f, dt / 90f)
                hover[d.id] = hv
                val scale = pop * (1f + (RING_SELECT_SCALE - 1f) * hv)
                val c = StarStyle.colorOf(d.starColor)
                val tw = 0.5f + 0.5f * sin(now / 1000f * (1.3f + (i % 5) * 0.17f) + i * 1.7f)
                val p = positions[i]

                val glowR = starPx * (0.95f + 0.6f * hv) * scale
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.copy(alpha = (0.20f + 0.12f * tw + 0.38f * hv) * alpha), Color.Transparent),
                        p, glowR,
                    ),
                    radius = glowR, center = p,
                )
                val img = starImages.getOrPut(d.starType * 1000 + d.starColor) {
                    val px = 112
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    StarStyle.drawCrystalFill(
                        android.graphics.Canvas(bmp), d.starType, d.starColor, 0f, 0f, px.toFloat()
                    )
                    bmp.asImageBitmap()
                }
                val sz = (starPx * scale).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = img,
                    dstOffset = IntOffset((p.x - sz / 2f).roundToInt(), (p.y - sz / 2f).roundToInt()),
                    dstSize = IntSize(sz, sz),
                    alpha = alpha,
                    filterQuality = FilterQuality.Medium,
                )
                if (hv > 0.01f) {
                    drawCircle(
                        color = c.copy(alpha = 0.55f * hv),
                        radius = sz * 0.8f, center = p,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            }
        }

        // 3) 원 가운데 — 고른 별 정보(없으면 모은 별 수 + 안내)
        val panelWidth = with(density) { ((radius - starPx * 1.3f) * 2f).coerceAtLeast(120.dp.toPx()).toDp() }
        Box(modifier = Modifier.align(Alignment.Center).width(panelWidth)) {
            AnimatedContent(
                targetState = selected,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.95f, animationSpec = tween(220))) togetherWith
                        fadeOut(tween(150))
                },
                label = "starlog-center",
                modifier = Modifier.align(Alignment.Center),
            ) { d ->
                if (d == null) CountPanel(count = stars.size)
                else StarInfoPanel(d = d, here = here, unlockedAt = unlockedAt[d.id], onOpenMap = onOpenMap)
            }
        }
    }
}

@Composable
private fun CountPanel(count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(
            "$count",
            color = TextPrimary, fontFamily = MinSans, fontSize = 44.sp, fontWeight = FontWeight.Light,
            style = TextStyle(shadow = Shadow(color = Color(0xFF6EE7B7).copy(alpha = 0.55f), blurRadius = 26f)),
        )
        Text(stringResource(R.string.starlog_count_label), color = TextSub, fontFamily = MinSans, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.starlog_hint),
            color = TextSub.copy(alpha = 0.8f), fontFamily = MinSans, fontSize = 12.sp,
            textAlign = TextAlign.Center, lineHeight = 17.sp,
        )
        // 광고로 해금한 다이어리는 도감에 영구히 남는다는 안내(2026-09-25 사용자 요청) — 보조 문구라 한 톤 옅게.
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.starlog_permanent_note),
            color = Color(0xFF9FB3E8).copy(alpha = 0.75f), fontFamily = MinSans, fontSize = 11.sp,
            textAlign = TextAlign.Center, lineHeight = 15.sp,
        )
    }
}

/** 별 색을 글씨로 쓸 때 — 어두운 별(흑백 그라데이션 등)도 읽히도록 밝기를 끌어올린다. */
private fun readableOn(c: Color): Color =
    if (c.luminance() < 0.35f) lerp(c, Color.White, 0.55f) else lerp(c, Color.White, 0.18f)

@Composable
private fun StarInfoPanel(d: Diary, here: LatLng, unlockedAt: Long?, onOpenMap: (String) -> Unit) {
    val starColor = StarStyle.colorOf(d.starColor)
    val ink = readableOn(starColor)
    val halo = Shadow(color = starColor.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = 26f)
    val locale: Locale = LocalConfiguration.current.locales[0]
    val date = remember(unlockedAt, locale) {
        unlockedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it)) }
    }
    val meters = LocationHelper.distanceBetween(here.latitude, here.longitude, d.latitude, d.longitude)
    val dist = if (meters >= 1000f) String.format(Locale.getDefault(), "%.1fkm", meters / 1000f)
    else "${meters.roundToInt()}m"
    val author = rememberCurrentUserName(d.userId, d.userName)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(
            d.title.ifBlank { "—" },
            color = ink, fontFamily = MinSans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp,
            style = TextStyle(shadow = halo),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.starlog_author, author),
            color = ink.copy(alpha = 0.85f), fontFamily = MinSans, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = halo.copy(color = starColor.copy(alpha = 0.6f), blurRadius = 16f)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (date != null) stringResource(R.string.starlog_meta, date, dist)
            else stringResource(R.string.starlog_meta_distance, dist),
            color = TextSub, fontFamily = MinSans, fontSize = 11.sp, maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(starColor.copy(alpha = 0.14f))
                .border(1.dp, starColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                .clickable { onOpenMap(d.id) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.starlog_open_map), color = ink, fontFamily = MinSans, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val accent = Color(0xFF9FB3E8)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.24f) else Color(0xEE111120))
            .raisedCosmicBorder(shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (selected) Color(0xFFDCE5FF) else Color(0xFFF0F0F0),
            fontFamily = MinSans, fontSize = 13.sp, maxLines = 1,
        )
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
