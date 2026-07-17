package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.chaminwoo.stary.core.util.LocationHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.MusicManager
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val TextMuted = Color(0xFF8A8A8A)

// 정렬별 테마색 — 최신순=푸른색, 인기순=분홍색, 거리순=보라색 (별자리·다이얼 공용)
private val LatestBlue = Color(0xFF7FB7FF)
private val PopularPink = Color(0xFFFF9CC6)
private val DistancePurple = Color(0xFFB89BFF)

private fun sortColor(s: DiarySort): Color = when (s) {
    DiarySort.LATEST -> LatestBlue
    DiarySort.POPULAR -> PopularPink
    DiarySort.DISTANCE -> DistancePurple
}

/** 정렬 라벨 — 현재 언어로(enum 의 한국어 label 대신 리소스). */
@Composable
private fun sortLabel(s: DiarySort): String = when (s) {
    DiarySort.LATEST -> stringResource(R.string.sort_latest)
    DiarySort.POPULAR -> stringResource(R.string.sort_popular)
    DiarySort.DISTANCE -> stringResource(R.string.sort_distance)
}

@Composable
fun MyDiaryScreen(
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory()),
) {
    val userId = GoogleAuthHelper.currentUserId
    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_login_required), color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val myDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // 업로드 화면과 동일한 밝기의 배경
        Image(
            painter = painterResource(R.drawable.mydiary_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )
        DiaryStarsBoard(diaries = myDiaries, onDiaryClick = onDiaryClick)

        FirstVisitInfo(
            seenKey = "info_mydiary",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.onb_mydiary_title),
            message = stringResource(R.string.onb_mydiary_msg),
        )
    }
}

/**
 * 별 정렬 보드 — 별자리 배경 + 바나나 다이얼(최신/인기/거리) + 떠다니는 별.
 * 내 다이어리/타인 다이어리("별로 보기") 공용. [onDiaryClick] 으로 클릭 동작만 바뀐다.
 */
@Composable
fun DiaryStarsBoard(
    diaries: List<Diary>,
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMode by remember { mutableStateOf(DiarySort.LATEST) }
    // 같은 정렬(특히 기본값 최신순)을 다시 골라도 재정렬이 보이도록 하는 토큰
    var sortNonce by remember { mutableIntStateOf(0) }
    // 보기 모드 — false: 떠다니는 별 보드(기본), true: 별 아이콘+제목+날짜 1열 리스트
    var listMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // 별자리 배경 + 바나나 다이얼 (박스 360: 터치/텍스트가 내려간 다이얼까지 닿게, 별자리는 상단 260 고정)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            ConstellationBackground(
                sortMode, sortNonce,
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(260.dp)
            )
            BananaDial(
                selected = sortMode,
                onSelect = { sortMode = it; sortNonce++; MusicManager.playWind() }, // 정렬 반영 + 효과음
                modifier = Modifier.matchParentSize()
            )
        }

        // 정렬·개수 라벨 + 보기 전환 토글(별 보드 ↔ 1열 리스트)
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.mydiary_sort_count, sortLabel(sortMode), diaries.size),
                color = sortColor(sortMode), fontSize = 14.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { listMode = !listMode },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(34.dp)
            ) {
                Icon(
                    imageVector = if (listMode) Icons.Filled.AutoAwesome else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(
                        if (listMode) R.string.mydiary_view_stars else R.string.mydiary_view_list
                    ),
                    tint = sortColor(sortMode).copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (diaries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mydiary_empty), color = TextMuted, fontSize = 14.sp)
            }
        } else if (listMode) {
            DiaryListColumn(
                diaries = diaries,
                sortMode = sortMode,
                onDiaryClick = onDiaryClick,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            DiaryStarBox(
                diaries = diaries,
                sortMode = sortMode,
                sortNonce = sortNonce,
                onDiaryClick = onDiaryClick,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/**
 * 1열 리스트 보기 — 행마다 별 아이콘(모양·색 그대로) + 제목 + 작성 날짜.
 * 다이얼 정렬(sortMode)을 그대로 따르며, 행 탭 → 다이어리 상세.
 * 별 보드에서 찾기 어려운 특정 다이어리를 빠르게 찾는 용도.
 */
@Composable
private fun DiaryListColumn(
    diaries: List<Diary>,
    sortMode: DiarySort,
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 거리순용 현재 위치(캐시 우선, 없으면 1회 측정 — DiaryStarBox 와 동일 방식)
    val context = LocalContext.current
    var here by remember(diaries) { mutableStateOf(LocationHelper.getCurrentLatLng()) }
    LaunchedEffect(diaries) {
        if (here == null) {
            LocationHelper.getCurrentLocation(context)?.let {
                here = com.chaminwoo.stary.core.geo.LatLng(it.latitude, it.longitude)
            }
        }
    }
    val sorted = remember(diaries, sortMode, here) {
        val origin = here
        when (sortMode) {
            DiarySort.LATEST -> diaries.sortedByDescending { it.createdAt }
            DiarySort.POPULAR -> diaries.sortedByDescending { it.likeCount }
            DiarySort.DISTANCE ->
                if (origin != null) diaries.sortedBy {
                    LocationHelper.distanceBetween(
                        origin.latitude, origin.longitude, it.latitude, it.longitude
                    )
                } else diaries
        }
    }
    val dateFmt = remember { java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth()) {
        sorted.forEach { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66161B22))
                    .clickable { onDiaryClick(d.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarShapeIcon(
                    type = d.starType, colorIndex = d.starColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    d.title.ifBlank { stringResource(R.string.common_untitled) },
                    color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp,
                    fontWeight = FontWeight.Normal, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    dateFmt.format(java.util.Date(d.createdAt)),
                    color = TextMuted, fontSize = 12.sp
                )
            }
        }
    }
}

// ─── 바나나(호) 다이얼 ─────────────────────────────────────────────────────────
// 3개 버튼이 아래로 휜 곡선(바나나/스마일)에 놓이고, 맨 아래에 온 버튼이 선택된다.
// 좌우로 드래그하거나 버튼을 탭하면 그 버튼이 아래로 굴러와 선택된다. (선택용 바늘 없음)
//
// 곡선을 (원호가 아닌) 포물선으로 잡아 수평 퍼짐을 일정하게 묶는다.
// 기존 원호(반지름 150)는 끝 항목이 화면 밖으로 밀려 탭이 안 되던 버그가 있었음 →
// 수평 간격을 고정해 끝(마지막) 항목도 항상 화면 안에 들어와 선택된다.
private const val DIAL_H_SPACING_DP = 90f  // 항목 사이 수평 간격(px 변환해 드래그 감도로도 사용)
private const val DIAL_CURVE_DP = 30f      // 바나나 곡률(가장자리로 갈수록 위로 들림: k²)
// 선택(바닥) 항목 깊이 — 박스 중앙 기준.
// 박스 높이를 260→360 으로 키우며 중앙이 50dp 내려갔으므로, 다이얼 절대 위치를 유지하려고
// 기존 150 에서 50 을 빼 100 으로 보정(시각상 다이얼은 그대로, 터치/텍스트만 아래로 확장).
private const val DIAL_BOTTOM_DP = 100f

// 다이얼 세 버튼은 서로 다른 별 모양으로 구분 (StarStyle 타입 0..4)
private fun dialStarType(s: DiarySort): Int = when (s) {
    DiarySort.LATEST -> 0
    DiarySort.POPULAR -> 2  // 6꼭지 별
    DiarySort.DISTANCE -> 4 // 4꼭지 다이아 스파클
}

@Composable
private fun BananaDial(
    selected: DiarySort,
    onSelect: (DiarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val items = DiarySort.entries
    val n = items.size
    // 동기 Float 상태 — 드래그가 프레임 지연 없이 즉시 회전(이전 비동기 snapTo 지연 버그 수정)
    var rot by remember { mutableFloatStateOf(items.indexOf(selected).toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    fun animateRotTo(target: Float) {
        scope.launch {
            val anim = Animatable(rot)
            anim.animateTo(target, tween(300, easing = FastOutSlowInEasing)) { rot = value }
        }
    }

    LaunchedEffect(selected) {
        val t = items.indexOf(selected).toFloat()
        // 외부 요인으로 selected 가 바뀐 경우에만 회전 보정(다이얼 조작분은 이미 애니메이션 중)
        if (!dragging && rot.roundToInt() != t.toInt()) animateRotTo(t)
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(n) {
            val pxPerIndex = DIAL_H_SPACING_DP.dp.toPx()
            detectDragGestures(
                onDragStart = { dragging = true },
                onDrag = { change, drag ->
                    change.consume()
                    // 드래그 중엔 시각 회전만 갱신 — 선택 이벤트는 놓을 때만(onDragEnd)
                    // 방향 반대로: 미는 방향으로 별들이 따라오도록 부호 반전
                    rot = (rot - drag.x / pxPerIndex).coerceIn(0f, (n - 1).toFloat())
                },
                onDragEnd = {
                    dragging = false
                    val idx = rot.roundToInt().coerceIn(0, n - 1)
                    animateRotTo(idx.toFloat())
                    // 놓은 자리는 항상 선택을 반영(재정렬·효과음). 기본값(최신순)에 그대로 놓아도 동작.
                    onSelect(items[idx])
                },
                onDragCancel = {
                    dragging = false
                    val idx = rot.roundToInt().coerceIn(0, n - 1)
                    animateRotTo(idx.toFloat())
                    onSelect(items[idx])
                }
            )
        }
    ) {
        // 바나나 곡선 가이드 — 세 별이 놓인 포물선을 따라 함께 이동
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val sx = DIAL_H_SPACING_DP.dp.toPx()
            val bottom = DIAL_BOTTOM_DP.dp.toPx()
            val curve = DIAL_CURVE_DP.dp.toPx()
            fun pt(k: Float) = Offset(cx + sx * k, cy + bottom - curve * k * k)
            val kL = (0 - rot) - 0.25f
            val kR = (n - 1 - rot) + 0.25f
            val steps = 48
            var prev: Offset? = null
            for (s in 0..steps) {
                val k = kL + (kR - kL) * s / steps
                val p = pt(k)
                if (prev != null) drawLine(Color.White.copy(alpha = 0.10f), prev!!, p, strokeWidth = 1.5.dp.toPx())
                prev = p
            }
        }

        items.forEachIndexed { i, sort ->
            val k = i - rot
            val x = (DIAL_H_SPACING_DP * k).dp
            val y = (DIAL_BOTTOM_DP - DIAL_CURVE_DP * k * k).dp
            // 바닥에 가까울수록 1 → 별이 점점 커지고 후광이 강해짐
            val closeness = 1f - abs(k).coerceAtMost(1f)
            val col = sortColor(sort)
            val starSize = (15f + 13f * closeness).dp
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x, y = y)
                    .size(54.dp)
                    .clickable(interactionSource = interaction, indication = null) {
                        animateRotTo(i.toFloat())
                        onSelect(sort) // 같은 항목(기본 최신순 포함)이라도 항상 반영
                    },
                contentAlignment = Alignment.Center
            ) {
                // 후광 (부드러운 radial glow) — 항목 고유색으로
                Box(
                    Modifier
                        .size((22f + 24f * closeness).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(col.copy(alpha = 0.16f + 0.46f * closeness), Color.Transparent)
                            )
                        )
                )
                StarShapeIcon(
                    type = dialStarType(sort), // 항목마다 다른 모양
                    color = androidx.compose.ui.graphics.lerp(col.copy(alpha = 0.45f), col, closeness),
                    modifier = Modifier.size(starSize)
                )
            }
        }
    }
}

// ─── 별자리 배경 ──────────────────────────────────────────────────────────────

// 별 한 개: 위치(0..1 비율) + 크기/밝기 가중치(mag). mag 가 클수록 더 크고 밝다.
private class CStar(val x: Float, val y: Float, val mag: Float)
private class Constel(val stars: List<CStar>, val edges: List<Pair<Int, Int>>)

/**
 * 정렬별 별자리 문양 — drawable/reference{1,2,3}.png 를 픽셀 분석해 별 위치·크기·연결선을
 * 그대로 옮긴 것(좌표 0..1 비율, x 오른쪽 / y 아래쪽). mag 는 레퍼런스 별 크기에서 산출.
 * 순서: 최신순(reference1)·인기순(reference2)·거리순(reference3).
 */
private val CONSTELLATIONS: Map<DiarySort, Constel> = mapOf(
    DiarySort.LATEST to Constel(
        listOf(
            CStar(0.496f, 0.187f, 1.49f), // S0
            CStar(0.622f, 0.319f, 1.10f), // S1
            CStar(0.572f, 0.344f, 1.14f), // S2
            CStar(0.373f, 0.364f, 1.52f), // S3
            CStar(0.214f, 0.407f, 2.20f), // S4 (가장 큰 별)
            CStar(0.853f, 0.406f, 1.12f), // S5
            CStar(0.520f, 0.455f, 1.10f), // S6
            CStar(0.587f, 0.456f, 1.10f), // S7
            CStar(0.734f, 0.480f, 1.10f), // S8
            CStar(0.633f, 0.493f, 1.14f), // S9
            CStar(0.531f, 0.535f, 1.16f), // S10
            CStar(0.734f, 0.609f, 1.53f), // S11
            CStar(0.910f, 0.644f, 1.12f), // S12
            CStar(0.799f, 0.660f, 1.14f), // S13
            CStar(0.677f, 0.713f, 1.16f), // S14
            CStar(0.362f, 0.786f, 1.08f), // S15
            CStar(0.211f, 0.791f, 1.10f), // S16
            CStar(0.684f, 0.794f, 1.03f), // S17
            CStar(0.342f, 0.885f, 1.14f), // S18
        ),
        listOf(
            0 to 2, 1 to 2, 2 to 7, 3 to 4, 3 to 6, 3 to 9, 4 to 16, 5 to 8, 6 to 7,
            6 to 9, 6 to 10, 7 to 9, 8 to 9, 8 to 10, 8 to 11, 8 to 17, 9 to 10,
            11 to 13, 11 to 14, 12 to 13, 14 to 17, 15 to 16, 16 to 18
        )
    ),
    DiarySort.POPULAR to Constel(
        listOf(
            CStar(0.808f, 0.111f, 1.76f), // S0
            CStar(0.261f, 0.413f, 2.20f), // S1 (가장 큰 별)
            CStar(0.694f, 0.472f, 1.41f), // S2
            CStar(0.229f, 0.521f, 1.15f), // S3
            CStar(0.168f, 0.544f, 1.18f), // S4
            CStar(0.152f, 0.590f, 1.41f), // S5
            CStar(0.416f, 0.701f, 1.39f), // S6
            CStar(0.590f, 0.699f, 1.15f), // S7
            CStar(0.770f, 0.796f, 1.28f), // S8
            CStar(0.370f, 0.826f, 2.16f), // S9 (큰 별)
        ),
        listOf(0 to 1, 1 to 3, 1 to 5, 3 to 4, 3 to 5, 4 to 5, 5 to 9, 6 to 7, 6 to 9, 7 to 8)
    ),
    DiarySort.DISTANCE to Constel(
        listOf(
            CStar(0.840f, 0.122f, 1.64f), // S0
            CStar(0.859f, 0.232f, 1.61f), // S1
            CStar(0.661f, 0.333f, 1.66f), // S2
            CStar(0.874f, 0.359f, 1.64f), // S3
            CStar(0.622f, 0.387f, 1.59f), // S4
            CStar(0.515f, 0.605f, 2.02f), // S5 (큰 별)
            CStar(0.269f, 0.706f, 1.39f), // S6
            CStar(0.220f, 0.770f, 1.36f), // S7
            CStar(0.195f, 0.815f, 1.70f), // S8
            CStar(0.504f, 0.866f, 2.20f), // S9 (가장 큰 별)
            CStar(0.419f, 0.907f, 1.99f), // S10
            CStar(0.254f, 0.909f, 1.66f), // S11
        ),
        listOf(0 to 2, 0 to 4, 1 to 2, 2 to 3, 2 to 4, 2 to 5, 4 to 5, 5 to 9, 6 to 7, 6 to 8, 7 to 8, 8 to 11, 9 to 10, 10 to 11)
    ),
)

@Composable
private fun ConstellationBackground(sort: DiarySort, flashKey: Int, modifier: Modifier = Modifier) {
    val constel = CONSTELLATIONS[sort] ?: return
    val color = sortColor(sort)

    // 선택할 때마다(같은 정렬 재선택 포함) 모든 별이 번쩍 → 이후 은은하게 가라앉음
    val flash = remember { Animatable(0.78f) }
    LaunchedEffect(sort, flashKey) {
        flash.snapTo(1.7f)                                          // 번쩍
        flash.animateTo(0.78f, tween(900, easing = FastOutSlowInEasing)) // 살짝 은은하게
    }
    // 끊임없는 반짝임(펄스) — 별마다 위상이 달라 제각기 생동감 있게 깜빡인다
    val twinkle = rememberInfiniteTransition(label = "twinkle")
    val t by twinkle.animateFloat(
        initialValue = 0f, targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "t"
    )
    val f = flash.value   // 밝기 배수: 번쩍(1.7) → 은은(0.78)

    Canvas(modifier = modifier) {
        val padX = size.width * 0.13f
        val padY = size.height * 0.10f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        fun pos(s: CStar) = Offset(padX + s.x * w, padY + s.y * h)

        // 연결선
        constel.edges.forEach { (a, b) ->
            drawLine(
                color.copy(alpha = (0.20f * f).coerceIn(0f, 1f)),
                start = pos(constel.stars[a]), end = pos(constel.stars[b]),
                strokeWidth = 1.4.dp.toPx()
            )
        }
        // 별 + 후광(펄스) — mag 클수록 더 크고 더 밝게
        constel.stars.forEach { s ->
            val c = pos(s)
            val phase = s.x * 11f + s.y * 7f                 // 위치 기반 고정 위상
            val pulse = 0.5f + 0.5f * sin(t + phase)         // 0..1 깜빡임
            val magN = ((s.mag - 1.0f) / 1.2f).coerceIn(0f, 1f) // 0(작음)..1(큼)

            val haloR = (7f + 16f * s.mag) * (0.8f + 0.35f * pulse) * (0.85f + 0.25f * f)
            val haloA = (0.08f + 0.34f * pulse) * (0.45f + 0.55f * magN) * f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = haloA.coerceIn(0f, 1f)), Color.Transparent),
                    center = c, radius = haloR.dp.toPx()
                ),
                radius = haloR.dp.toPx(), center = c
            )
            val coreR = (1.0f + 1.8f * s.mag) * (0.88f + 0.2f * pulse)
            drawCircle(Color.White.copy(alpha = ((0.45f + 0.40f * pulse) * f).coerceIn(0f, 1f)), coreR.dp.toPx(), c)
        }
    }
}
