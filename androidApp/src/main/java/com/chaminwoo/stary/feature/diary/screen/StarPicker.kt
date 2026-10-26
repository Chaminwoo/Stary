package com.chaminwoo.stary.feature.diary.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.LocalScreenSize
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.designsystem.StaryColors
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.ui.StarySegmented
import com.chaminwoo.stary.core.ui.glassSheet
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.LocalizedNames
import com.chaminwoo.stary.feature.profile.Achievement
import com.chaminwoo.stary.feature.profile.StarUnlocks
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 별 고르기에서 "가진 것만 보기" — 한 번 켜 두면 기억한다. */
internal object StarPickerPrefs {
    private const val PREFS = "stary_prefs"
    private const val KEY_OWNED_ONLY = "upload_owned_only"

    fun ownedOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OWNED_ONLY, false)

    fun setOwnedOnly(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OWNED_ONLY, value).apply()
    }
}

/**
 * 별 모양/색 무한 회전 휠 — 고른 항목이 항상 정중앙(별빛 링), 끝까지 돌리면 처음 항목이 다시 나온다.
 * - 빠르게 밀면 손을 뗀 뒤에도 몇 칸 더 미끄러지다 멈춘다(관성). 천천히 끌면 놓은 자리에서 가장 가까운 칸으로.
 * - 바깥에서 [selection] 이 바뀌면(모두 보기 시트) 그 칸까지 굴러간다.
 * - 칸이 적어도(가진 것만 보기) 같은 별을 두 번 그리지 않는다 — 항목마다 가운데에서 가장 가까운 한 자리에만 둔다.
 * - 스냅이 끝난 뒤 selection 을 한 번에 갱신한다(좌표 불연속 방지). 잠긴 항목도 가운데 멈추며, 저장 차단은 호출부 몫.
 * iOS `WheelPicker`(StarPicker.swift) 패리티.
 */
@Composable
internal fun StarWheelPicker(
    count: Int,
    selection: Int,
    onSelect: (Int) -> Unit,
    itemSize: Dp,
    onLanded: (Int) -> Unit,
    /** false 면 드래그/탭 모두 막는다(업로드 진행 중 값 변경 방지) — 시각적으로도 살짝 어둡게. */
    enabled: Boolean = true,
    item: @Composable (Int) -> Unit,
) {
    if (count <= 0) return
    // ⚠️ LocalDensity(반응형 배율 적용) 로 잰다 — raw displayMetrics 를 쓰면 작은 폰에서 칸 간격과 별 크기가 어긋난다.
    val slotPx = with(LocalDensity.current) { (itemSize + 28.dp).toPx() }
    val scope = rememberCoroutineScope()
    // 그려지는 가운데 칸 — 휠이 스스로 돌 때도, 바깥에서 바뀔 때도 이 칸에서 출발해 굴러간다.
    var shown by remember(count) { mutableIntStateOf(selection.coerceIn(0, count - 1)) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val ticked = remember { intArrayOf(0) } // 마지막으로 딸깍한 눈금(그림과 무관해 상태로 두지 않는다)
    val wraps = count >= 3                  // 1~2칸이면 돌지 않고 양 끝에서 멈춘다
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 1.8f) }

    fun wrap(i: Int): Int = ((i % count) + count) % count
    fun tick(px: Float) {
        val s = (-px / slotPx).roundToInt()
        if (s != ticked[0]) { ticked[0] = s; Haptics.tick() }
    }

    // 지금 자리에서 steps 칸 떨어진 항목이 가운데 오도록 굴린 뒤 shown 을 바꾼다(좌표가 같아 튀지 않는다).
    // 반 칸을 못 넘긴 드래그(steps=0)도 반드시 원위치로 — 그냥 두면 항목 **사이**에 멈춘다(8.45 수정).
    suspend fun settle(rawSteps: Int, spec: AnimationSpec<Float>, notify: Boolean) {
        val steps = if (wraps) rawSteps else (shown + rawSteps).coerceIn(0, count - 1) - shown
        settling = true
        try {
            animate(dragPx, -steps * slotPx, animationSpec = spec) { v, _ -> dragPx = v; tick(v) }
            if (steps != 0) {
                val target = wrap(shown + steps)
                shown = target
                if (notify) { onSelect(target); onLanded(target) }
            }
        } finally {
            dragPx = 0f
            ticked[0] = 0
            settling = false
        }
    }

    // 바깥에서 고른 칸(모두 보기 시트)까지 짧은 쪽으로 굴러간다.
    LaunchedEffect(selection, count) {
        val target = selection.coerceIn(0, count - 1)
        if (target == shown || settling) return@LaunchedEffect
        var d = target - shown
        if (wraps) { d = wrap(d); if (d > count / 2) d -= count }
        settle(d, tween((260 + 30 * abs(d)).coerceAtMost(620), easing = FastOutSlowInEasing), notify = false)
    }

    // 드래그 중 위치는 동기적으로 바로 더한다 — 예전엔 snapTo 를 코루틴으로 띄운 뒤 옛 값을 읽어 빠르게 끌면 휠이 튀었다.
    val dragState = rememberDraggableState { delta ->
        if (!settling) { dragPx += delta; tick(dragPx) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemSize * 1.7f)
            .clipToBounds()
            .alpha(if (enabled) 1f else 0.5f)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = { velocity ->
                    if (!settling) {
                        // 손을 뗀 속도로 멈출 자리를 미리 계산 — 천천히 놓으면 지금 자리에서 가장 가까운 칸과 같다.
                        val here = (-dragPx / slotPx).roundToInt()
                        val limit = minOf(count, 12)
                        val steps = (-decay.calculateTargetValue(dragPx, velocity) / slotPx).roundToInt().coerceIn(-limit, limit)
                        val glide = abs(steps - here)
                        scope.launch {
                            settle(
                                steps,
                                if (glide >= 2) tween((200 + 45 * glide).coerceAtMost(720), easing = LinearOutSlowInEasing)
                                else tween(240, easing = FastOutSlowInEasing),
                                notify = true,
                            )
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 가운데 강조 링(선택 자리).
        Box(
            Modifier
                .size(itemSize + 18.dp)
                .border(2.dp, StaryColors.Accent.copy(alpha = 0.9f), CircleShape)
        )

        val center = -dragPx / slotPx // 지금 가운데에 와 있는 자리(shown 기준, 칸 단위)
        for (idx in 0 until count) {
            // 이 항목이 놓일 자리 — 가운데에서 가장 가까운 한 곳(칸이 적어도 같은 별이 두 번 보이지 않는다).
            val base = idx - shown
            val off = if (wraps) base + count * ((center - base) / count).roundToInt() else base
            val d = abs(off - center)
            if (d > 3.5f) continue
            // 칸이 적으면 반대편으로 넘어가는 자리(count/2)에서 사라졌다 나타나게 — 툭 튀어 보이지 않게.
            val edgeFade = if (wraps) ((count / 2f - d) / 0.6f).coerceIn(0f, 1f) else 1f
            key(idx) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val x = (off - center) * slotPx
                            val dist = abs(x)
                            val s = max(0.55f, 1.25f - dist / slotPx * 0.35f)
                            translationX = x
                            scaleX = s; scaleY = s
                            alpha = max(0.3f, 1f - dist / (slotPx * 2.2f)) * edgeFade
                        }
                        .clickable(enabled = enabled, interactionSource = null, indication = null) {
                            if (!settling) scope.launch { settle(off, tween(240, easing = FastOutSlowInEasing), notify = true) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    item(idx)
                }
            }
        }
    }
}

/** "가진 것만 보기" — 휠 아래와 모두 보기 시트 머리에 같은 모양으로 둔다. */
@Composable
internal fun OwnedOnlyToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) StaryColors.Accent else StaryColors.Muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.upload_owned_only), color = if (checked) StaryColors.Text else StaryColors.Muted, fontSize = 13.sp)
    }
}

/**
 * '모두 보기' 시트 — 휠로 하나씩 넘기지 않고 모양/색을 한눈에 고른다(휠은 그대로 둔다).
 * 묶음: 기본 · 업적 보상 · 별빛 공방. 모양 칸은 지금 고른 색으로, 색 칸은 지금 고른 모양으로 그려 실제 조합을 바로 본다.
 * 잠긴 칸을 누르면 아래에 필요한 업적과 조건이 나오고, 열린 칸을 고르면 시트가 닫히며 휠이 그 칸까지 굴러간다.
 *
 * 화면 안 오버레이라 [hazeState](업로드 화면 본문)를 유리로 비춘다 — 시스템 바텀시트는 창이 달라 흐림이 안 먹는다.
 * iOS `StarPickerSheet` 패리티.
 */
@Composable
internal fun StarPickerSheet(
    visible: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    hazeState: HazeState,
    starType: Int,
    starColor: Int,
    /** 휠과 같은 전체 목록(가진 것만 거르기 전). */
    shapeIds: List<Int>,
    colorIds: List<Int>,
    unlockedIds: Set<String>,
    ownedOnly: Boolean,
    onOwnedOnly: (Boolean) -> Unit,
    onPickShape: (Int) -> Unit,
    onPickColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
        ) {
            SheetBody(
                tab, onTab, hazeState, starType, starColor, shapeIds, colorIds, unlockedIds,
                ownedOnly, onOwnedOnly, onPickShape, onPickColor, onDismiss,
            )
        }
    }
}

@Composable
private fun SheetBody(
    tab: Int,
    onTab: (Int) -> Unit,
    hazeState: HazeState,
    starType: Int,
    starColor: Int,
    shapeIds: List<Int>,
    colorIds: List<Int>,
    unlockedIds: Set<String>,
    ownedOnly: Boolean,
    onOwnedOnly: (Boolean) -> Unit,
    onPickShape: (Int) -> Unit,
    onPickColor: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val shapes = tab == 0
    // 마지막으로 누른 잠긴 칸 — 아래 안내 줄에 조건을 띄운다(탭을 바꾸면 지운다).
    var lockedPick by remember(tab) { mutableStateOf<Pair<Int, Achievement>?>(null) }
    val builtIn = if (shapes) StarStyle.TYPE_COUNT else StarStyle.COLOR_COUNT
    val ids = if (shapes) shapeIds else colorIds
    fun lockOf(id: Int): Achievement? =
        if (shapes) StarUnlocks.lockedShapeAch(id, unlockedIds) else StarUnlocks.lockedColorAch(id, unlockedIds)
    fun needsAchievement(id: Int) = (if (shapes) StarUnlocks.shape else StarUnlocks.color).containsKey(id)

    val basic = ids.filter { it < builtIn && !needsAchievement(it) }
    val rewards = ids.filter { it < builtIn && needsAchievement(it) }
    val rewardsShown = if (ownedOnly) rewards.filter { lockOf(it) == null } else rewards
    val workshop = ids.filter { it >= builtIn }

    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = LocalScreenSize.current.height * 0.72f)
            .glassSheet(hazeState, sheetShape)
            // 판 안쪽을 눌러도 바깥(닫기)으로 새지 않게.
            .clickable(interactionSource = null, indication = null) {}
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp),
    ) {
        Box(
            Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.22f))
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StarySegmented(
                options = listOf(stringResource(R.string.picker_tab_shape) to null, stringResource(R.string.picker_tab_color) to null),
                selected = tab,
                onSelect = onTab,
                modifier = Modifier.width(164.dp),
            )
            Spacer(Modifier.weight(1f))
            OwnedOnlyToggle(ownedOnly, onOwnedOnly)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            @Composable
            fun group(label: String, count: String, items: List<Int>) {
                if (items.isEmpty()) return
                Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = StaryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text(count, color = StaryColors.Muted.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                PickerGrid(items) { id ->
                    val lock = lockOf(id)
                    val selected = id == (if (shapes) starType else starColor)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) {
                                if (lock != null) {
                                    Haptics.tick()
                                    lockedPick = id to lock
                                } else {
                                    if (shapes) onPickShape(id) else onPickColor(id)
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val ring = when {
                            selected -> StaryColors.Accent.copy(alpha = 0.9f)
                            lockedPick?.first == id -> Color.White.copy(alpha = 0.3f)
                            else -> null
                        }
                        if (ring != null) Box(Modifier.size(46.dp).border(1.5.dp, ring, CircleShape))
                        StarShapeIcon(
                            type = if (shapes) id else starType,
                            colorIndex = if (shapes) starColor else id,
                            modifier = Modifier.size(30.dp).alpha(if (lock != null) 0.28f else 1f),
                        )
                        if (lock != null) {
                            Icon(
                                Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_locked),
                                tint = StaryColors.Muted,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 9.dp).size(11.dp),
                            )
                        }
                    }
                }
            }
            group(stringResource(R.string.picker_group_basic), basic.size.toString(), basic)
            group(
                stringResource(R.string.picker_group_achievement),
                "${rewards.count { lockOf(it) == null }}/${rewards.size}",
                rewardsShown,
            )
            group(stringResource(R.string.ach_voyage_section), workshop.size.toString(), workshop)
        }
        // 잠긴 칸 안내 — 어떤 업적을 달성하면 열리는지.
        AnimatedVisibility(lockedPick != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val ach = lockedPick?.second
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(StaryColors.Line.copy(alpha = 0.6f)))
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = StaryColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    if (ach != null) {
                        Column {
                            Text(
                                stringResource(R.string.picker_locked_hint, LocalizedNames.title(context, ach.id, ach.name) ?: ach.name),
                                color = StaryColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (ach.hidden) "???" else LocalizedNames.condition(context, ach.id, ach.condition),
                                color = StaryColors.Muted, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 칸을 폭에 맞춰 고르게 나눈 격자(항목이 수십 개라 지연 목록 없이 그린다). */
@Composable
private fun PickerGrid(items: List<Int>, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 60.dp).toInt().coerceIn(5, 8)
        val size = maxWidth / cols
        Column {
            items.chunked(cols).forEach { row ->
                Row {
                    row.forEach { id -> key(id) { Box(Modifier.size(size)) { cell(id) } } }
                }
            }
        }
    }
}
