package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.MusicManager
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Green = Color(0xFF6EE7B7)
private val TextMuted = Color(0xFF8A8A8A)

@Composable
fun MyDiaryScreen(
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory()),
) {
    val userId = GoogleAuthHelper.currentUserId
    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val myDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()
    var sortMode by remember { mutableStateOf(DiarySort.LATEST) }

    Box(modifier = modifier.fillMaxSize()) {
        // 업로드 화면과 동일한 밝기의 배경
        Image(
            painter = painterResource(R.drawable.mydiary_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.7f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
        // 별자리 배경 + 바나나 다이얼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            ConstellationBackground(sortMode, Modifier.matchParentSize())
            BananaDial(
                selected = sortMode,
                onSelect = { sortMode = it; MusicManager.playWind() }, // 정렬 변경(애니메이션 시작) 시 바람 효과음
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }

        Text(
            text = "${sortMode.label} · ${myDiaries.size}개",
            color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            textAlign = TextAlign.Center
        )

        if (myDiaries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text("아직 기록한 다이어리가 없어요", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            DiaryStarBox(
                diaries = myDiaries,
                sortMode = sortMode,
                onDiaryClick = onDiaryClick,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        }
    }
}

// ─── 바나나(호) 다이얼 ─────────────────────────────────────────────────────────
// 3개 버튼이 아래로 휜 호(바나나/스마일)에 놓이고, 맨 아래에 온 버튼이 선택된다.
// 좌우로 드래그하거나 버튼을 탭하면 그 버튼이 아래로 굴러와 선택된다. (선택용 바늘 없음)

private const val DIAL_SPACING_DEG = 42f   // 버튼 사이 호 간격
private const val DIAL_RADIUS_DP = 150f    // 호 반지름
private const val DIAL_BOTTOM_DP = 92f     // 박스 중앙 기준 맨 아래 지점 깊이(아래로 내림)

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
            val pxPerIndex = 110.dp.toPx()
            detectDragGestures(
                onDragStart = { dragging = true },
                onDrag = { change, drag ->
                    change.consume()
                    // 드래그 중엔 시각 회전만 갱신 — 선택 이벤트는 놓을 때만(onDragEnd)
                    rot = (rot + drag.x / pxPerIndex).coerceIn(0f, (n - 1).toFloat())
                },
                onDragEnd = {
                    dragging = false
                    val idx = rot.roundToInt().coerceIn(0, n - 1)
                    animateRotTo(idx.toFloat())
                    // 놓은 위치가 이전 선택과 다를 때만 이벤트 실행
                    if (items[idx] != selected) onSelect(items[idx])
                },
                onDragCancel = {
                    dragging = false
                    val idx = rot.roundToInt().coerceIn(0, n - 1)
                    animateRotTo(idx.toFloat())
                    if (items[idx] != selected) onSelect(items[idx])
                }
            )
        }
    ) {
        // 바나나 호 가이드 — 세 별과 같은 원호 위에 있으며 함께 이동
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = DIAL_RADIUS_DP.dp.toPx()
            val center = Offset(cx, cy + DIAL_BOTTOM_DP.dp.toPx() - r)
            val a0 = 90f + (0 - rot) * DIAL_SPACING_DEG
            val aN = 90f + (n - 1 - rot) * DIAL_SPACING_DEG
            val startDeg = minOf(a0, aN) - 12f
            val endDeg = maxOf(a0, aN) + 12f
            val steps = 48
            var prev: Offset? = null
            for (s in 0..steps) {
                val deg = startDeg + (endDeg - startDeg) * s / steps
                val a = Math.toRadians(deg.toDouble())
                val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                if (prev != null) drawLine(Color.White.copy(alpha = 0.10f), prev!!, p, strokeWidth = 1.5.dp.toPx())
                prev = p
            }
        }

        items.forEachIndexed { i, sort ->
            val k = i - rot
            val ang = Math.toRadians((90f + k * DIAL_SPACING_DEG).toDouble())
            val x = (DIAL_RADIUS_DP * cos(ang)).toFloat().dp
            val y = (DIAL_BOTTOM_DP - DIAL_RADIUS_DP + DIAL_RADIUS_DP * sin(ang)).toFloat().dp
            // 바닥에 가까울수록 1 → 별이 점점 커지고 후광이 강해짐
            val closeness = 1f - abs(k).coerceAtMost(1f)
            val starSize = (14f + 14f * closeness).dp
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x, y = y)
                    .size(48.dp)
                    .clickable(interactionSource = interaction, indication = null) {
                        animateRotTo(i.toFloat())
                        if (sort != selected) onSelect(sort)
                    },
                contentAlignment = Alignment.Center
            ) {
                // 후광 (원이 아닌 부드러운 radial glow)
                if (closeness > 0.05f) {
                    Box(
                        Modifier
                            .size((26f + 20f * closeness).dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Green.copy(alpha = 0.5f * closeness), Color.Transparent)
                                )
                            )
                    )
                }
                StarShapeIcon(
                    type = 1,
                    color = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.3f), Green, closeness),
                    modifier = Modifier.size(starSize)
                )
            }
        }
    }
}

// ─── 별자리 배경 ──────────────────────────────────────────────────────────────

private class Constel(val pts: List<Offset>, val edges: List<Pair<Int, Int>>)

/**
 * 정렬별 실제 별자리 문양(asterism, 좌표 0..1 비율 — x 오른쪽, y 아래쪽).
 * 실제 항성 배치(별자리 선그림)를 보고 주요 별과 연결선을 그대로 옮긴 것.
 */
private val CONSTELLATIONS: Map<DiarySort, Constel> = mapOf(
    // ── 최신순 — 사수자리(Sagittarius) '찻주전자(Teapot)' ──
    // λ(뚜껑) / δ,φ,ζ,ε(몸통) / γ(주둥이) / σ,τ(손잡이)
    DiarySort.LATEST to Constel(
        listOf(
            Offset(0.44f, 0.16f), // 0 λ Kaus Borealis (뚜껑 꼭지)
            Offset(0.30f, 0.42f), // 1 δ Kaus Media
            Offset(0.30f, 0.66f), // 2 ε Kaus Australis
            Offset(0.10f, 0.52f), // 3 γ Alnasl (주둥이 끝)
            Offset(0.56f, 0.40f), // 4 φ Sgr
            Offset(0.56f, 0.66f), // 5 ζ Ascella
            Offset(0.72f, 0.38f), // 6 σ Nunki (손잡이 위)
            Offset(0.74f, 0.62f), // 7 τ Sgr (손잡이 아래)
        ),
        listOf(
            0 to 1, 0 to 4,         // 뚜껑
            1 to 2, 2 to 5, 5 to 4, // 몸통(사각)
            1 to 3,                 // 주둥이
            4 to 6, 6 to 7, 7 to 5  // 손잡이
        )
    ),
    // ── 인기순 — 처녀자리(Virgo) ──
    // Spica(α) 아래 끝, γ Porrima 중심에서 ε(위)·η→β(오른쪽)로 뻗는 Y자
    DiarySort.POPULAR to Constel(
        listOf(
            Offset(0.40f, 0.86f), // 0 α Spica
            Offset(0.50f, 0.66f), // 1 ζ Vir
            Offset(0.46f, 0.52f), // 2 γ Porrima (중심)
            Offset(0.40f, 0.38f), // 3 δ Vir
            Offset(0.34f, 0.22f), // 4 ε Vindemiatrix
            Offset(0.66f, 0.50f), // 5 η Zaniah
            Offset(0.84f, 0.44f), // 6 β Zavijava
        ),
        listOf(
            0 to 1, 1 to 2,        // Spica→ζ→γ
            2 to 3, 3 to 4,        // γ→δ→ε (위로)
            2 to 5, 5 to 6         // γ→η→β (오른쪽)
        )
    ),
    // ── 거리순 — 전갈자리(Scorpius) 'J자 낚싯바늘' ──
    // 머리(β,δ,π) → Antares(α) → 몸통/꼬리 곡선 → 독침(λ Shaula, υ Lesath)
    DiarySort.DISTANCE to Constel(
        listOf(
            Offset(0.30f, 0.12f), // 0 β Graffias (머리 위)
            Offset(0.42f, 0.16f), // 1 δ Dschubba (머리 중앙)
            Offset(0.52f, 0.13f), // 2 π Sco (머리 아래쪽 집게)
            Offset(0.44f, 0.34f), // 3 α Antares (심장)
            Offset(0.46f, 0.46f), // 4 τ Sco
            Offset(0.42f, 0.58f), // 5 ε Sco
            Offset(0.37f, 0.69f), // 6 μ Sco
            Offset(0.34f, 0.78f), // 7 ζ Sco
            Offset(0.38f, 0.87f), // 8 η Sco
            Offset(0.48f, 0.90f), // 9 θ Sargas
            Offset(0.58f, 0.86f), // 10 ι Sco
            Offset(0.64f, 0.78f), // 11 κ Sco
            Offset(0.70f, 0.70f), // 12 λ Shaula (독침)
            Offset(0.74f, 0.64f), // 13 υ Lesath
        ),
        listOf(
            0 to 1, 1 to 2,                         // 머리(집게)
            1 to 3,                                 // 머리→Antares
            3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8, // 몸통 아래로
            8 to 9, 9 to 10, 10 to 11, 11 to 12,    // 꼬리 곡선(위로 휨)
            12 to 13                                // 독침
        )
    ),
)

@Composable
private fun ConstellationBackground(sort: DiarySort, modifier: Modifier = Modifier) {
    val glow = remember { Animatable(0.28f) }
    LaunchedEffect(sort) {
        glow.snapTo(0f)
        glow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        glow.animateTo(0.30f, tween(900, easing = FastOutSlowInEasing))
    }
    val constel = CONSTELLATIONS[sort] ?: return
    val g = glow.value

    Canvas(modifier = modifier) {
        val padX = size.width * 0.16f
        val padY = size.height * 0.14f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        fun pos(o: Offset) = Offset(padX + o.x * w, padY + o.y * h)

        val lineAlpha = 0.08f + 0.40f * g
        constel.edges.forEach { (a, b) ->
            drawLine(
                Green.copy(alpha = lineAlpha),
                start = pos(constel.pts[a]), end = pos(constel.pts[b]),
                strokeWidth = (1f + 1.5f * g).dp.toPx()
            )
        }
        constel.pts.forEach { p ->
            val c = pos(p)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Green.copy(alpha = 0.5f * g), Color.Transparent), center = c, radius = 18.dp.toPx()
                ),
                radius = 18.dp.toPx(), center = c
            )
            drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * g), radius = (2f + 1.5f * g).dp.toPx(), center = c)
        }
    }
}
