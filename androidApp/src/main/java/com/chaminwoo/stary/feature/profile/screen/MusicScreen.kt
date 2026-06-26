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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.util.MusicCatalog
import com.chaminwoo.stary.core.util.MusicManager
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.rememberUserStats
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val MusicTextMuted = Color(0xFF8A8A8A)

/**
 * 배경음악 선택 화면 — [MyDiaryScreen] 의 다이얼/별자리 연출을 그대로 본떴다.
 * 트랙마다 고유색·별 모양·별자리가 있고, 좌우 드래그/탭으로 고른다.
 *
 * - 해금된 트랙을 고르면 처음부터 미리듣기([MusicManager.playTrack]).
 * - 잠긴 트랙은 자물쇠 표시 + 토스트 안내, 미리듣기/확정 안 됨.
 * - 화면을 나갈 때 바꿨으면 [MusicManager.commitSelectedTrack] 으로 확정,
 *   안 바꿨으면 원래 트랙을 원래 위치부터 복원([MusicManager.playTrack]).
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    val userId = GoogleAuthHelper.currentUserId
    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("로그인이 필요해요", color = MusicTextMuted, fontSize = 18.sp)
        }
        return
    }
    val unlockedIds: Set<String> = Achievements.unlockedIds(rememberUserStats(userId))

    val tracks = MusicCatalog.tracks
    fun isUnlocked(t: MusicCatalog.Track): Boolean =
        t.unlockAchievementId == null || t.unlockAchievementId in unlockedIds

    // 진입 시 원래 트랙 기억(미변경 판별용).
    val originalId = remember { MusicManager.selectedTrackId }

    var selectedIndex by remember { mutableIntStateOf(MusicCatalog.indexOf(MusicManager.selectedTrackId)) }
    // 마지막으로 고른 '해금된' 트랙 = 확정 대상(잠긴 트랙을 둘러봐도 바뀌지 않음).
    var pendingId by remember { mutableStateOf(MusicManager.selectedTrackId) }

    // 선택 변경 시 미리듣기. 진입 직후(첫 컴포지션)엔 현재 재생을 끊지 않는다.
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(selectedIndex) {
        val t = tracks[selectedIndex]
        if (firstComposition) {
            firstComposition = false
            return@LaunchedEffect
        }
        if (isUnlocked(t)) {
            pendingId = t.id
            // 처음부터가 아니라 지금 듣던 위치를 이어받아 전환(끊김 없이 연속).
            MusicManager.playTrack(t.id, MusicManager.currentPositionMs())
        } else {
            val ach = Achievements.byId(t.unlockAchievementId)
            StaryToast.show("‘${ach?.name ?: "비밀"}’ 업적을 달성하여 해금하세요!")
        }
    }

    // 이탈 시: 바꿨으면 확정(미리듣기로 재생 중이던 위치 그대로 이어짐).
    // 안 바꿨으면 현재 재생을 건드리지 않는다(재시작 없이 듣던 위치 유지).
    DisposableEffect(Unit) {
        onDispose {
            if (pendingId != originalId) MusicManager.commitSelectedTrack(pendingId)
        }
    }

    val selected = tracks[selectedIndex]
    val selectedColor = Color(selected.colorArgb)
    val selectedUnlocked = isUnlocked(selected)

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.mydiary_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(380.dp),
                contentAlignment = Alignment.Center
            ) {
                // 정말 '원형' 다이얼 — 별이 원 둘레에 놓이고, 그 원 안쪽에 별자리가 보인다.
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MusicConstellationBackground(
                        trackId = selected.id, color = selectedColor, flashKey = selectedIndex,
                        modifier = Modifier.size(186.dp)
                    )
                    MusicDial(
                        tracks = tracks,
                        selectedIndex = selectedIndex,
                        isUnlocked = ::isUnlocked,
                        onSelect = { selectedIndex = it },
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            Text(
                text = selected.displayName,
                color = if (selectedUnlocked) selectedColor else MusicTextMuted,
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            val sub = if (selectedUnlocked) "좌우로 드래그해 음악을 골라보세요"
            else "🔒 ‘${Achievements.byId(selected.unlockAchievementId)?.name ?: "비밀"}’ 달성 시 해금"
            Text(
                text = sub,
                color = MusicTextMuted, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── 음악 다이얼(원형 로터리) ──────────────────────────────────────────────────
// 별이 원 둘레에 놓이고, 드래그로 고리를 돌리면 위쪽(topAngle)에 온 트랙이 선택된다.
// 원 안쪽 중앙엔 선택된 트랙의 별자리가 보인다.
private const val DIAL_RING_RADIUS_DP = 124f  // 별 고리 반지름

@Composable
private fun MusicDial(
    tracks: List<MusicCatalog.Track>,
    selectedIndex: Int,
    isUnlocked: (MusicCatalog.Track) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val n = tracks.size
    val step = (2.0 * Math.PI / n).toFloat()   // 항목 간 각도 간격
    val topAngle = (-Math.PI / 2.0).toFloat()  // 선택 기준(위쪽)

    // angleOffset: 고리 회전량(라디안). 초기엔 selectedIndex 가 위쪽에 오도록.
    var angleOffset by remember { mutableFloatStateOf(-selectedIndex * step) }
    var dragging by remember { mutableStateOf(false) }

    fun indexAt(off: Float): Int = (((-off / step).roundToInt() % n) + n) % n

    // angleOffset 와 가장 가까운 등가 각(±2π)으로 보정해 짧게 회전.
    fun nearest(target: Float): Float {
        var t = target
        val twoPi = (2.0 * Math.PI).toFloat()
        while (t - angleOffset > Math.PI) t -= twoPi
        while (angleOffset - t > Math.PI) t += twoPi
        return t
    }

    fun animateOffsetTo(target: Float) {
        scope.launch {
            val anim = Animatable(angleOffset)
            anim.animateTo(target, tween(320, easing = FastOutSlowInEasing)) { angleOffset = value }
        }
    }

    fun settle() {
        val idx = indexAt(angleOffset)
        animateOffsetTo(nearest(-idx * step))
        onSelect(idx)
    }

    LaunchedEffect(selectedIndex) {
        if (!dragging && indexAt(angleOffset) != selectedIndex) {
            animateOffsetTo(nearest(-selectedIndex * step))
        }
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(n) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            detectDragGestures(
                onDragStart = { dragging = true; MusicManager.setDialTurning(true) },
                onDrag = { change, drag ->
                    change.consume()
                    val cur = change.position
                    val a1 = atan2((cur.y - drag.y) - cy, (cur.x - drag.x) - cx)
                    val a2 = atan2(cur.y - cy, cur.x - cx)
                    var d = a2 - a1
                    if (d > Math.PI) d -= (2.0 * Math.PI).toFloat()
                    if (d < -Math.PI) d += (2.0 * Math.PI).toFloat()
                    angleOffset += d
                },
                onDragEnd = { dragging = false; MusicManager.setDialTurning(false); settle() },
                onDragCancel = { dragging = false; MusicManager.setDialTurning(false); settle() }
            )
        }
    ) {
        // 다이얼 고리(은은한 원)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = DIAL_RING_RADIUS_DP.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        tracks.forEachIndexed { i, track ->
            val ang = topAngle + i * step + angleOffset
            val x = (cos(ang) * DIAL_RING_RADIUS_DP).dp
            val y = (sin(ang) * DIAL_RING_RADIUS_DP).dp
            // 위쪽(topAngle)에 가까울수록 1 → 더 크고 밝게.
            val closeness = ((cos(ang - topAngle) + 1f) / 2f).coerceIn(0f, 1f)
            val col = Color(track.colorArgb)
            val unlocked = isUnlocked(track)
            val starSize = (16f + 14f * closeness).dp
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x, y = y)
                    .size(54.dp)
                    .clickable(interactionSource = interaction, indication = null) {
                        animateOffsetTo(nearest(-i * step))
                        onSelect(i)
                    },
                contentAlignment = Alignment.Center
            ) {
                val glowA = (0.16f + 0.46f * closeness) * (if (unlocked) 1f else 0.4f)
                Box(
                    Modifier
                        .size((22f + 24f * closeness).dp)
                        .background(Brush.radialGradient(listOf(col.copy(alpha = glowA), Color.Transparent)))
                )
                val starColor = lerp(col.copy(alpha = 0.45f), col, closeness)
                StarShapeIcon(
                    type = track.starType,
                    color = if (unlocked) starColor else starColor.copy(alpha = 0.30f),
                    modifier = Modifier.size(starSize)
                )
                if (!unlocked) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = "잠김",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size((10f + 5f * closeness).dp)
                    )
                }
            }
        }
    }
}

// ─── 트랙별 별자리 ─────────────────────────────────────────────────────────────
// 좌표는 0..1 비율(x 오른쪽 / y 아래쪽), mag 는 별 크기/밝기 가중치.
private class MStar(val x: Float, val y: Float, val mag: Float)
private class MConstel(val stars: List<MStar>, val edges: List<Pair<Int, Int>>)

@Composable
private fun MusicConstellationBackground(
    trackId: String, color: Color, flashKey: Int, modifier: Modifier = Modifier
) {
    val constel = MUSIC_CONSTELLATIONS[trackId] ?: return

    // 선택할 때마다 번쩍 → 은은하게 가라앉음.
    val flash = remember { Animatable(0.78f) }
    LaunchedEffect(trackId, flashKey) {
        flash.snapTo(1.7f)
        flash.animateTo(0.78f, tween(900, easing = FastOutSlowInEasing))
    }
    // 끊임없는 반짝임(별마다 위상 다름).
    val twinkle = rememberInfiniteTransition(label = "mtwinkle")
    val t by twinkle.animateFloat(
        initialValue = 0f, targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "mt"
    )
    val f = flash.value

    Canvas(modifier = modifier) {
        val padX = size.width * 0.13f
        val padY = size.height * 0.10f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        fun pos(s: MStar) = Offset(padX + s.x * w, padY + s.y * h)

        constel.edges.forEach { (a, b) ->
            drawLine(
                color.copy(alpha = (0.20f * f).coerceIn(0f, 1f)),
                start = pos(constel.stars[a]), end = pos(constel.stars[b]),
                strokeWidth = 1.4.dp.toPx()
            )
        }
        constel.stars.forEach { s ->
            val c = pos(s)
            val phase = s.x * 11f + s.y * 7f
            val pulse = 0.5f + 0.5f * sin(t + phase)
            val magN = ((s.mag - 1.0f) / 1.2f).coerceIn(0f, 1f)

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

// 별의 속삭임 — 리라(거문고) 모양.
private val MC_STAR_WHISPER = MConstel(
    listOf(
        MStar(0.50f, 0.15f, 2.10f),
        MStar(0.34f, 0.34f, 1.40f),
        MStar(0.66f, 0.34f, 1.45f),
        MStar(0.30f, 0.60f, 1.30f),
        MStar(0.70f, 0.60f, 1.35f),
        MStar(0.42f, 0.80f, 1.60f),
        MStar(0.58f, 0.82f, 1.55f),
    ),
    listOf(0 to 1, 0 to 2, 1 to 3, 2 to 4, 3 to 5, 4 to 6, 5 to 6)
)

// 작은 탐험가 — 작은 국자(북두칠성) 모양.
private val MC_TINY_EXPLORER = MConstel(
    listOf(
        MStar(0.18f, 0.30f, 1.80f),
        MStar(0.36f, 0.24f, 1.40f),
        MStar(0.54f, 0.30f, 1.45f),
        MStar(0.70f, 0.40f, 1.50f),
        MStar(0.74f, 0.58f, 1.35f),
        MStar(0.58f, 0.66f, 1.40f),
        MStar(0.42f, 0.58f, 1.95f),
    ),
    listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 3)
)

// 천상의 표류 — 흐르는 물결 모양.
private val MC_CELESTIAL_DRIFT = MConstel(
    listOf(
        MStar(0.14f, 0.40f, 1.50f),
        MStar(0.30f, 0.26f, 1.35f),
        MStar(0.44f, 0.46f, 1.70f),
        MStar(0.58f, 0.66f, 1.35f),
        MStar(0.72f, 0.46f, 1.45f),
        MStar(0.86f, 0.30f, 2.00f),
        MStar(0.50f, 0.84f, 1.30f),
    ),
    listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 3 to 6)
)

// 코스믹 펑크 — 번개 같은 지그재그 모양.
private val MC_COSMIC_FUNK = MConstel(
    listOf(
        MStar(0.32f, 0.16f, 1.80f),
        MStar(0.58f, 0.30f, 1.35f),
        MStar(0.38f, 0.46f, 1.40f),
        MStar(0.64f, 0.60f, 1.45f),
        MStar(0.44f, 0.74f, 1.35f),
        MStar(0.70f, 0.86f, 1.95f),
    ),
    listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5)
)

// 잊혀진 은하 — 안쪽으로 감기는 나선 모양.
private val MC_FORGOTTEN_GALAXY = MConstel(
    listOf(
        MStar(0.50f, 0.50f, 2.10f),
        MStar(0.64f, 0.46f, 1.30f),
        MStar(0.70f, 0.62f, 1.35f),
        MStar(0.54f, 0.74f, 1.30f),
        MStar(0.34f, 0.68f, 1.40f),
        MStar(0.26f, 0.44f, 1.35f),
        MStar(0.42f, 0.26f, 1.45f),
        MStar(0.72f, 0.24f, 1.60f),
    ),
    listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7)
)

// 성운의 정원 — 중심을 둘러싼 꽃 모양.
private val MC_NEBULA_GARDEN = MConstel(
    listOf(
        MStar(0.50f, 0.50f, 1.90f),
        MStar(0.50f, 0.22f, 1.40f),
        MStar(0.74f, 0.36f, 1.35f),
        MStar(0.74f, 0.64f, 1.40f),
        MStar(0.50f, 0.80f, 1.35f),
        MStar(0.26f, 0.64f, 1.40f),
        MStar(0.26f, 0.36f, 1.35f),
    ),
    listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6)
)

private val MUSIC_CONSTELLATIONS: Map<String, MConstel> = mapOf(
    "star_whisper" to MC_STAR_WHISPER,
    "tiny_explorer" to MC_TINY_EXPLORER,
    "celestial_drift" to MC_CELESTIAL_DRIFT,
    "cosmic_funk" to MC_COSMIC_FUNK,
    "forgotten_galaxy" to MC_FORGOTTEN_GALAXY,
    "nebula_garden" to MC_NEBULA_GARDEN,
)
