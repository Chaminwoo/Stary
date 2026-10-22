package com.chaminwoo.stary.feature.quest

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.util.ContentFilter
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapFocusState
import com.chaminwoo.stary.core.util.StaryAnalytics
import com.chaminwoo.stary.core.util.TutorialStarState
import com.chaminwoo.stary.feature.starlight.StarlightStore
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 첫 사용 안내(2026-10-22) — 새 계정이 앱을 한 바퀴 돌아보게 다음 할 일을 하나씩 보여 준다. iOS `FirstStarGuide.swift` 와 같은 단계·조건.
 *
 *  코치마크 → 웰컴 별 열기(기존 [TutorialStarState]) 뒤에:
 *  - [GuideStep.QUEST]   : 지도 "!"(오늘의 퀘스트) 버튼만 밝히는 스포트라이트([QuestSpotlight], MainScreen 이 띄움) — 탭하면 퀘스트 창.
 *  - [GuideStep.PROFILE] : 첫 퀘스트 "이름이나 프로필 사진 바꾸기" — 퀘스트 창에서 그 행을 강조, 지도에선 카드 → 프로필 화면.
 *  - [GuideStep.WRITE]   : 내 별이 아직 없다 → 업로드 화면.
 *  - [GuideStep.CLAIM]   : 받을 보상(별가루)이 있다 → 퀘스트 창 / 받을 것도 바꿀 별가루도 없으면 안내용 열쇠 1개를 바로 준다.
 *  - [GuideStep.EXCHANGE]: 열쇠는 없고 별가루가 열쇠 값 이상 → 별빛 공방(2026-10-22 퀘스트 보상이 별가루가 되며 추가).
 *  - [GuideStep.OPEN]    : 열쇠가 있다 → 가장 가까운 "잠긴" 남의 별로 카메라를 옮긴 뒤 상세(잠금 카드에서 열쇠 사용).
 *  - 끝: 열쇠를 처음 쓰면([QuestStore.spendKey]) 또는 열어 볼 별이 없으면 `users/{uid}.guideDone` — 다시 뜨지 않는다.
 *
 * 앞 세 단계의 진행(퀘스트 창을 봤는지 · 프로필을 바꿨는지)은 이 기기의 계정별 prefs([GuideProgress]),
 * 별·열쇠는 서버 상태에서 계산한다. ⚠️ **새 계정으로 확인된 계정만**([GuideProgress.started] — 로그인 때 returningAccount=false)
 * 안내한다(이미 쓰던 사용자에게 갑자기 뜨지 않게). 닫기(X)·"나중에"는 이번 실행에서만 숨긴다.
 */
enum class GuideStep(val id: String) { QUEST("quest"), PROFILE("profile"), WRITE("write"), CLAIM("claim"), EXCHANGE("exchange"), OPEN("open") }

/** 계정별 안내 진행(이 기기) — Compose 상태 + `stary_first_guide` prefs. */
object GuideProgress {
    private const val PREFS = "stary_first_guide"
    private var boundUid: String? = null

    /** 새 계정으로 확인돼 안내 대상인지. */
    var started by mutableStateOf(false)
        private set
    /** 퀘스트 창을 안내 중에 한 번 열었는지(→ 스포트라이트 끝). */
    var questSeen by mutableStateOf(false)
        private set
    /** 닉네임이나 프로필 사진을 바꿨는지(첫 퀘스트). */
    var profileDone by mutableStateOf(false)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 이 계정 값으로 맞춘다(계정이 바뀌면 다시 읽음). */
    fun bind(context: Context, uid: String?) {
        if (uid == boundUid) return
        boundUid = uid
        val p = prefs(context)
        started = uid != null && p.getBoolean("started_$uid", false)
        questSeen = uid != null && p.getBoolean("quest_$uid", false)
        profileDone = uid != null && p.getBoolean("profile_$uid", false)
    }

    private fun mark(context: Context, uid: String, key: String) {
        bind(context, uid)
        prefs(context).edit().putBoolean("${key}_$uid", true).apply()
        when (key) {
            "started" -> started = true
            "quest" -> questSeen = true
            "profile" -> profileDone = true
        }
    }

    fun markStarted(context: Context, uid: String) { if (boundUid != uid || !started) mark(context, uid, "started") }
    fun markQuestSeen(context: Context, uid: String) { if (boundUid != uid || !questSeen) mark(context, uid, "quest") }
    fun markProfileDone(context: Context, uid: String) { if (boundUid != uid || !profileDone) mark(context, uid, "profile") }
}

/** 지도 화면(MainListScreen) ↔ 앱 루트(MainScreen) 사이의 안내 상태 브리지. */
object GuideUi {
    /** 지금 안내 단계(지도에서 안내를 띄울 수 있을 때만, 아니면 null) — MainScreen 이 QUEST 스포트라이트에 쓴다. */
    var step by mutableStateOf<GuideStep?>(null)
    /** 지도 "!" 버튼의 화면(루트) 좌표 — 스포트라이트 구멍 위치. */
    var questButtonBounds by mutableStateOf<Rect?>(null)
    /** 스포트라이트에서 버튼을 눌렀다 — MainListScreen 이 퀘스트 창을 연다. */
    var questRequest by mutableIntStateOf(0)
    /** 이번 실행에서 닫은(X/나중에) 단계. */
    var hidden by mutableStateOf<Set<GuideStep>>(emptySet())
}

/** 지금 안내할 단계(null = 안내 없음). */
fun guideStep(context: Context, userId: String?, diaries: List<Diary>, here: LatLng?): GuideStep? {
    if (userId == null || !QuestStore.guideLoaded || QuestStore.guideDone) return null
    GuideProgress.bind(context, userId)
    if (!GuideProgress.started) return null
    // 목록을 아직 못 받았으면(빈 목록) "내 별 없음"으로 오판하지 않게 기다린다.
    if (diaries.isEmpty()) return null
    if (diaries.none { it.userId == userId }) {
        // 웰컴 별을 먼저 열어 본 뒤부터(위치를 못 잡아 웰컴 별이 안 놓인 경우는 기다리지 않는다).
        val welcomePending = !TutorialStarState.done && (TutorialStarState.latLng != null || here != null)
        if (welcomePending) return null
        if (!GuideProgress.questSeen) return GuideStep.QUEST
        if (!GuideProgress.profileDone) return GuideStep.PROFILE
        return GuideStep.WRITE
    }
    if ((QuestStore.daily?.claimable ?: 0) > 0) return GuideStep.CLAIM
    if (QuestStore.keys > 0) return GuideStep.OPEN
    // 열쇠가 없다 — 별가루로 만들 수 있으면 공방으로(지갑을 아직 모르면 기다린다: 안내 열쇠를 괜히 주지 않게).
    if (!StarlightStore.walletLoaded) return null
    return if (StarlightStore.stardust >= StarlightStore.config.keyCost) GuideStep.EXCHANGE else GuideStep.CLAIM
}

/**
 * 열쇠로 열어 볼 별 — 남의 공개 별 중 아직 안 연 것, **100m 밖(= 잠긴) 가장 가까운 별** 우선.
 * 잠긴 별이 없으면 가장 가까운 남의 별(열쇠 없이 열린다 — 그래도 "남의 별 보기"는 된다).
 */
private fun pickTarget(
    diaries: List<Diary>, userId: String, here: LatLng?, unlockedIds: Set<String>, blockedIds: Set<String>,
): Diary? {
    val candidates = diaries.filter {
        it.userId != userId && it.userId !in blockedIds && it.id !in unlockedIds &&
            it.visibilityType == "public" && it.id != TutorialStarState.DIARY_ID &&
            (it.latitude != 0.0 || it.longitude != 0.0) &&
            !ContentFilter.anyObjectionable(it.title, it.content)
    }
    if (here == null) return candidates.maxByOrNull { it.likeCount }
    fun dist(d: Diary) = LocationHelper.distanceBetween(here.latitude, here.longitude, d.latitude, d.longitude)
    return candidates.filter { dist(it) > StaryConfig.DIARY_OPEN_RADIUS_M }.minByOrNull { dist(it) }
        ?: candidates.minByOrNull { dist(it) }
}

/** 단계가 처음 보일 때 — 분석 이벤트 + 열쇠 단계 표시 기록. */
private fun onStepShown(step: GuideStep) {
    if (step == GuideStep.OPEN) QuestStore.guideOpenShown = true
    StaryAnalytics.logOnce("guide_${step.id}", "guide_step", "step" to step.id)
}

/** 지도 위 안내 카드(PROFILE·WRITE·CLAIM·OPEN). 단계를 계산해 [GuideUi.step] 에도 알린다(QUEST 는 MainScreen 의 스포트라이트). */
@Composable
fun FirstStarGuide(
    userId: String?,
    diaries: List<Diary>,
    here: LatLng?,
    unlockedIds: Set<String>,
    blockedIds: Set<String>,
    /** 다른 창·코치마크·집중 모드 등으로 지금은 띄우면 안 될 때 false. */
    enabled: Boolean,
    onWrite: () -> Unit,
    onOpenQuests: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenStar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val step = guideStep(context, userId, diaries, here)
    val published = if (enabled) step else null
    SideEffect { GuideUi.step = published }
    DisposableEffect(Unit) { onDispose { GuideUi.step = null } }

    val cardStep = step?.takeIf { it != GuideStep.QUEST && it !in GuideUi.hidden }
    val shown = enabled && cardStep != null

    LaunchedEffect(cardStep, shown) {
        if (shown && cardStep != null) onStepShown(cardStep)
    }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { -it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 },
        modifier = modifier,
    ) {
        // 사라지는 동안에도 마지막 단계 내용을 그대로 그린다.
        val last = remember { mutableStateOf(cardStep ?: GuideStep.WRITE) }
        if (cardStep != null) last.value = cardStep
        AnimatedContent(
            targetState = last.value,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "guide_step",
        ) { s ->
            GuideCard(
                step = s,
                onAction = action@{
                    val uid = userId ?: return@action
                    StaryAnalytics.log("guide_action", "step" to s.id)
                    Haptics.light()
                    when (s) {
                        GuideStep.QUEST -> onOpenQuests()
                        GuideStep.PROFILE -> onOpenProfile()
                        GuideStep.WRITE -> onWrite()
                        GuideStep.CLAIM -> {
                            if ((QuestStore.daily?.claimable ?: 0) > 0) {
                                onOpenQuests()
                            } else if (QuestStore.grantGuideKey()) {
                                // 오늘 퀘스트에 "올리기"가 없던 날 — 첫 별 보상 열쇠를 바로 준다.
                                Haptics.celebrate()
                                StaryToast.show(context.getString(R.string.quest_key_gained, 1))
                            } else {
                                onOpenQuests()
                            }
                        }
                        GuideStep.EXCHANGE -> onOpenWorkshop()
                        GuideStep.OPEN -> {
                            val target = pickTarget(diaries, uid, here, unlockedIds, blockedIds)
                            if (target == null) {
                                StaryToast.show(context.getString(R.string.guide_no_target))
                                QuestStore.markGuideDone("no_target")
                            } else {
                                // 그 별로 카메라를 옮기고(파장) 잠깐 보여 준 뒤 상세로 — 잠금 카드에서 열쇠를 쓴다.
                                MapFocusState.request(target.id)
                                scope.launch {
                                    delay(1_800)
                                    onOpenStar(target.id)
                                    delay(700)
                                    StaryToast.show(context.getString(R.string.guide_open_hint))
                                }
                            }
                        }
                    }
                },
                onClose = {
                    StaryAnalytics.log("guide_dismiss", "step" to s.id)
                    GuideUi.hidden = GuideUi.hidden + s
                },
            )
        }
    }
}

@Composable
private fun GuideCard(step: GuideStep, onAction: () -> Unit, onClose: () -> Unit) {
    val (icon, title, msg, btn) = when (step) {
        GuideStep.QUEST, GuideStep.PROFILE ->
            GuideText(Icons.Filled.Edit, R.string.guide_profile_title, R.string.guide_profile_msg, R.string.quest_go)
        GuideStep.WRITE -> GuideText(Icons.Filled.Add, R.string.guide_write_title, R.string.guide_write_msg, R.string.guide_write_btn)
        GuideStep.CLAIM -> GuideText(Icons.Filled.Key, R.string.guide_claim_title, R.string.guide_claim_msg, R.string.guide_claim_btn)
        GuideStep.EXCHANGE -> GuideText(Icons.Filled.AutoAwesome, R.string.guide_exchange_title, R.string.guide_exchange_msg, R.string.starlight_go)
        GuideStep.OPEN -> GuideText(Icons.Filled.LockOpen, R.string.guide_open_title, R.string.guide_open_msg, R.string.guide_open_btn)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xF0111120))
            .border(0.75.dp, QuestGold.copy(alpha = 0.45f), shape)
            .padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(QuestGold.copy(alpha = 0.16f), CircleShape)
                    .border(0.75.dp, QuestGold.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = QuestGold, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), color = Color(0xFFF0F0F0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (step == GuideStep.EXCHANGE) stringResource(msg, StarlightStore.config.keyCost) else stringResource(msg),
                    color = Color(0xFFB8BCCB), fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(btn),
                color = Color(0xFF111120),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickBounce()
                    .clip(RoundedCornerShape(50))
                    .background(QuestGold)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.cd_close),
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(5.dp),
            )
        }
    }
}

private data class GuideText(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: Int,
    val msg: Int,
    val btn: Int,
)

/**
 * QUEST 단계 — 화면 전체를 어둡게 하고 지도 "!" 버튼만 금빛 링으로 밝힌다(코치마크와 같은 스포트라이트).
 * 구멍(버튼)을 탭하면 [onOpen](퀘스트 창), 우상단 "나중에"는 이번 실행에서만 숨긴다. 구멍 밖 탭은 무시.
 * 앱 루트(MainScreen)에서 띄워야 탑바까지 덮는다 — 버튼 좌표는 [GuideUi.questButtonBounds](루트 좌표).
 */
@Composable
fun QuestSpotlight(onOpen: () -> Unit, onLater: () -> Unit) {
    val bounds = GuideUi.questButtonBounds ?: return
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        visible.targetState = true
        onStepShown(GuideStep.QUEST)
    }
    val pulse by rememberInfiniteTransition(label = "spot_pulse").animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "spot_pulse_r",
    )
    AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(320)), exit = fadeOut(tween(200))) {
        val center = Offset(bounds.center.x - origin.x, bounds.center.y - origin.y)
        val r = bounds.width / 2f + with(density) { 8.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(center, r) {
                    detectTapGestures { p ->
                        if (hypot(p.x - center.x, p.y - center.y) <= r * 1.4f) onOpen()
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(Color.Black.copy(alpha = 0.74f))
                val soft = r * 1.35f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Black, Color.Black, Color.Transparent),
                        center = center, radius = soft,
                    ),
                    radius = soft, center = center, blendMode = BlendMode.Clear,
                )
                drawCircle(QuestGold.copy(alpha = 0.85f), radius = r, center = center, style = Stroke(width = 2.dp.toPx()))
                drawCircle(
                    QuestGold.copy(alpha = (0.5f * (1.18f - pulse) / 0.18f).coerceIn(0f, 0.5f)),
                    radius = r * pulse, center = center, style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // 말풍선 — 버튼 왼쪽에 세로 중심을 맞춘다(코치마크 우측 FAB 단계와 같은 자리).
            val margin = with(density) { 10.dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            Layout(content = {
                Box(
                    modifier = Modifier
                        .widthIn(max = 232.dp)
                        .background(Color(0xFF14181C), RoundedCornerShape(14.dp))
                        .border(1.dp, Brush.linearGradient(listOf(QuestGold, Color(0xFFFFB259))), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.guide_quest_tip),
                        color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    )
                }
            }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val x = (center.x - r - gap - p.width).coerceIn(margin, (w - p.width - margin).coerceAtLeast(margin))
                val y = (center.y - p.height / 2f)
                    .coerceIn(statusTopPx + margin, (h - navBottomPx - margin - p.height).coerceAtLeast(statusTopPx + margin))
                layout(w, h) { p.place(x.roundToInt(), y.roundToInt()) }
            }
            Text(
                stringResource(R.string.guide_later),
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 14.dp, top = 8.dp)
                    .clickable(onClick = onLater)
                    .padding(6.dp),
            )
        }
    }
}
