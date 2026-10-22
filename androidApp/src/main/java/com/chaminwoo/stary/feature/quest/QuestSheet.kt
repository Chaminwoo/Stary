package com.chaminwoo.stary.feature.quest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.clickBounce
import com.chaminwoo.stary.core.ui.raisedCosmicBorder
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.feature.starlight.StardustIcon
import com.chaminwoo.stary.feature.starlight.StarlightStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
/** 열쇠·퀘스트 강조색 — 웰컴 별/개척 비콘과 같은 "시스템 안내" 금빛. */
internal val QuestGold = Color(0xFFFFD27A)

/**
 * 지도 우측 FAB 열의 "!" 버튼(예전 몰입 버튼 자리, 2026-10-19).
 * - 받을 보상이 있으면 금빛 + 우상단 점 + 은은한 맥박.
 * - 남은 퀘스트가 있으면 금빛, 오늘 것을 다 받았으면 흰색 반투명.
 */
@Composable
fun QuestButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val daily = QuestStore.daily
    val claimable = daily?.claimable ?: 0
    val allDone = daily?.allClaimed == true
    val pulse by rememberInfiniteTransition(label = "quest_pulse").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "quest_pulse_scale",
    )
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            containerColor = Color(0xEE111120), // 다른 지도 버튼과 같은 남색빛 검정
            modifier = Modifier.size(48.dp).clickBounce().raisedCosmicBorder(),
        ) {
            Icon(
                Icons.Filled.PriorityHigh,
                stringResource(R.string.quest_today),
                tint = if (allDone) Color.White.copy(alpha = 0.55f) else QuestGold,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val s = if (claimable > 0) pulse else 1f
                        scaleX = s; scaleY = s
                    },
            )
        }
        if (claimable > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp, y = (-1).dp)
                    .size(11.dp)
                    .background(Mint, CircleShape)
                    .border(1.5.dp, Color(0xFF111120), CircleShape),
            )
        }
    }
}

/** 퀘스트 이벤트 → 행 아이콘(이 앱이 모르는 이벤트 = 별). */
private fun iconOf(event: QuestEvent?): ImageVector = when (event) {
    null -> Icons.Filled.Star
    QuestEvent.TUTORIAL_STAR -> Icons.Filled.Star
    QuestEvent.UPLOAD -> Icons.Filled.Add
    QuestEvent.UPLOAD_MEDIA -> Icons.Filled.Photo
    QuestEvent.VIEW_OTHER -> Icons.Filled.Visibility
    QuestEvent.LIKE -> Icons.Filled.Favorite
    QuestEvent.COMMENT -> Icons.AutoMirrored.Filled.Chat
    QuestEvent.EQUIP_TITLE -> Icons.Filled.WorkspacePremium
    QuestEvent.PROFILE_EDIT -> Icons.Filled.Edit
    QuestEvent.PIN_STAR -> Icons.Filled.PushPin
    QuestEvent.CONSTELLATION -> Icons.Filled.AutoAwesome
    QuestEvent.FILTER -> Icons.Filled.Explore
    QuestEvent.GLOBE -> Icons.Filled.Public
    QuestEvent.METEOR -> Icons.Filled.FlashOn
    QuestEvent.VISIT_ACHIEVEMENTS -> Icons.Filled.EmojiEvents
    QuestEvent.VISIT_STARLOG -> Icons.Filled.MenuBook
    QuestEvent.VISIT_PROFILE -> Icons.Filled.Person
    QuestEvent.FRIEND_REQUEST -> Icons.Filled.PersonAdd
    QuestEvent.SHARE -> Icons.Filled.Share
    QuestEvent.MUSIC -> Icons.Filled.MusicNote
    QuestEvent.FOCUS -> Icons.Filled.SelfImprovement
    QuestEvent.UNLOCK_KEY -> Icons.Filled.LockOpen
    QuestEvent.UPLOAD_STACK -> Icons.Filled.Layers
    QuestEvent.UNLOCK_VISIT -> Icons.AutoMirrored.Filled.DirectionsWalk
    QuestEvent.COMMENT_OTHER -> Icons.AutoMirrored.Filled.Chat
    QuestEvent.FRIEND_ADD -> Icons.Filled.Group
    QuestEvent.INVITE -> Icons.AutoMirrored.Filled.Send
    QuestEvent.OPEN_BOX -> Icons.Filled.Inventory2
    QuestEvent.ACHIEVEMENT -> Icons.Filled.MilitaryTech
}

/** 보유 열쇠 칩 — 퀘스트 창 머리. 열쇠를 받으면 [bump] 가 바뀌어 통통 튄다. */
@Composable
internal fun KeyChip(keys: Int, bump: Int = 0, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bump) {
        if (bump == 0) return@LaunchedEffect
        scale.snapTo(1.35f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(50))
            .background(QuestGold.copy(alpha = 0.14f))
            .border(0.75.dp, QuestGold.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.chaminwoo.stary.feature.starlight.CrystalKeyIcon(Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            keys.toString(),
            style = TextStyle(color = QuestGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
    }
}

/** 보유 별가루 칩 — 퀘스트 창 머리(탭 → 별빛 공방). 별가루를 받으면 [bump] 가 바뀌어 통통 튄다. */
@Composable
internal fun StardustChip(stardust: Int, bump: Int = 0, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bump) {
        if (bump == 0) return@LaunchedEffect
        scale.snapTo(1.35f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    val dust = Color(0xFFFFE6A8)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(50))
            .background(dust.copy(alpha = 0.12f))
            .border(0.75.dp, dust.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StardustIcon(Modifier.size(15.dp), dust)
        Spacer(Modifier.width(5.dp))
        Text(
            stardust.toString(),
            style = TextStyle(color = dust, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
    }
}

/**
 * 오늘의 퀘스트 창 — 3개 퀘스트(아이콘·제목·하는 법·진행도·보상) + 보너스 + 새 퀘스트까지 남은 시간.
 * 생김새는 화면 첫 진입 안내([com.chaminwoo.stary.core.ui.FirstVisitInfo])와 같은 카드.
 */
@Composable
fun QuestSheet(
    onDismiss: () -> Unit,
    /** 첫 사용 안내 중 강조할 퀘스트 id(그 행에 금빛 맥박 테두리 + "하러 가기"). */
    highlightId: String? = null,
    /** 강조한 행의 "하러 가기" — 그 기능 화면으로 보낸다(창은 호출부가 닫는다). */
    onGo: ((QuestDef) -> Unit)? = null,
    /** 별가루 칩 → 별빛 공방(창은 호출부가 닫는다). null 이면 칩만 보인다. */
    onOpenWorkshop: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val daily = QuestStore.daily
    val keys = QuestStore.keys
    val catalog = DailyQuests.catalog
    val lang = com.chaminwoo.stary.feature.starlight.starlightLang()
    val bonusReward = StarlightStore.config.bonusStardust
    var bump by remember { mutableStateOf(0) }
    var dustBump by remember { mutableStateOf(0) }
    fun claimed(gained: Int?, questId: String) {
        when {
            gained == null -> StaryToast.show(context.getString(R.string.starlight_err_generic))
            gained > 0 -> {
                com.chaminwoo.stary.core.util.StaryAnalytics.log("quest_claim", "quest" to questId)
                Haptics.celebrate()
                dustBump++
                StaryToast.show(context.getString(R.string.starlight_stardust_gained, gained))
            }
        }
    }
    var showAdKey by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { com.chaminwoo.stary.core.util.StaryAnalytics.log("quest_open", "claimable" to (daily?.claimable ?: 0), "keys" to keys) }
    if (showAdKey) {
        com.chaminwoo.stary.feature.diary.screen.KeyAdDialog(
            accent = QuestGold,
            message = stringResource(R.string.quest_ad_key_ask),
            onWatchAd = {
                showAdKey = false
                com.chaminwoo.stary.feature.diary.screen.watchAdForKey(context) {
                    QuestStore.grantAdKey()
                    Haptics.celebrate()
                    bump++
                    StaryToast.show(context.getString(R.string.quest_key_gained, 1))
                }
            },
            onDismiss = { showAdKey = false },
        )
    }
    // 열린 동안 날짜가 바뀌면 새 목록으로.
    LaunchedEffect(Unit) { QuestStore.ensureToday() }
    // 창이 떠 있는 동안엔 지도 집중 모드(가만히 10초)에 들어가지 않는다 — Dialog 터치는 지도 루트가 못 본다.
    DisposableEffect(Unit) {
        MapUiState.holdIdle()
        onDispose { MapUiState.releaseIdle() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF121821))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(QuestGold.copy(alpha = 0.55f), Blue.copy(alpha = 0.40f))),
                    RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.quest_today),
                    color = Color(0xFFF0F0F0), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 별가루 칩 → 별빛 공방 / 열쇠 칩 → "광고를 보고 열쇠를 얻으시겠어요?"
                StardustChip(
                    stardust = StarlightStore.stardust, bump = dustBump,
                    modifier = Modifier.clickable(enabled = onOpenWorkshop != null) { onOpenWorkshop?.invoke() },
                )
                Spacer(Modifier.width(6.dp))
                KeyChip(keys = keys, bump = bump, modifier = Modifier.clickable { showAdKey = true })
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.quest_close), tint = Color(0xFF8A93A3), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.quest_subtitle),
                color = Color(0xFFB8C0CC), fontSize = 13.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (daily == null || daily.day != QuestStore.today()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = QuestGold, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.quest_loading), color = Color(0xFFB8C0CC), fontSize = 13.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    daily.quests.forEachIndexed { i, def ->
                        QuestRow(
                            index = i,
                            icon = iconOf(def.event),
                            title = def.titleIn(lang),
                            hint = def.hintIn(lang),
                            tier = def.tier,
                            target = def.target,
                            reward = catalog?.reward(def, StarlightStore.config.questStardust) ?: StarlightStore.config.questStardust,
                            progress = daily.progressOf(def.id).coerceAtMost(def.target),
                            claimed = def.id in daily.claimed,
                            highlight = def.id == highlightId,
                            onGo = onGo?.let { go -> { go(def) } },
                            onClaim = { scope.launch { claimed(QuestStore.claim(def.id), def.id) } },
                        )
                    }
                    // 보너스 퀘스트(4번째 행) — 위 3개 보상을 모두 받으면 받을 수 있다(진행도 = 받은 보상 수).
                    QuestRow(
                        index = daily.quests.size,
                        icon = Icons.Filled.EmojiEvents,
                        title = stringResource(R.string.quest_bonus_title),
                        hint = stringResource(R.string.quest_bonus_hint),
                        target = daily.ids.size.coerceAtLeast(1),
                        reward = bonusReward,
                        progress = daily.ids.count { it in daily.claimed },
                        claimed = daily.bonus,
                        onClaim = { scope.launch { claimed(QuestStore.claimBonus(), "bonus") } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ResetCountdown()
        }
    }
}

/** 난이도 표시 — 쉬움(민트) · 보통(하늘) · 어려움(보라). 모르는 값은 쉬움. */
@Composable
private fun TierTag(tier: String) {
    val (label, color) = when (tier) {
        "hard" -> R.string.quest_tier_hard to Color(0xFFD59CFF)
        "normal" -> R.string.quest_tier_normal to Color(0xFF7CB8FF)
        else -> R.string.quest_tier_easy to Mint
    }
    Text(
        stringResource(label),
        color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(0.5.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** "새 퀘스트까지 N시간 M분" — 기기 현지 자정 기준, 30초마다 갱신. */
@Composable
private fun ResetCountdown() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            QuestStore.ensureToday()
        }
    }
    val left = remember(now) {
        Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).toMinutes().coerceAtLeast(0)
    }
    val h = (left / 60).toInt()
    val m = (left % 60).toInt()
    val text = if (h > 0) stringResource(R.string.quest_hm, h, m) else stringResource(R.string.quest_m, m.coerceAtLeast(1))
    Text(
        stringResource(R.string.quest_reset_in, text),
        color = Color(0xFF7D8696), fontSize = 12.sp,
        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QuestRow(
    index: Int,
    icon: ImageVector,
    title: String,
    hint: String,
    target: Int,
    reward: Int,
    progress: Int,
    claimed: Boolean,
    onClaim: () -> Unit,
    /** 난이도(easy · normal · hard) — 제목 위 작은 표시. null = 보너스 행. */
    tier: String? = null,
    highlight: Boolean = false,
    onGo: (() -> Unit)? = null,
) {
    val complete = progress >= target
    val guiding = highlight && !complete && !claimed
    val glow by rememberInfiniteTransition(label = "quest_guide").animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "quest_guide_glow",
    )
    val shown = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60L * index); shown.value = true }
    val fill by animateFloatAsState(
        targetValue = progress / target.toFloat(),
        animationSpec = tween(500), label = "quest_progress",
    )
    AnimatedVisibility(
        visible = shown.value,
        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if ((complete && !claimed) || guiding) QuestGold.copy(alpha = 0.08f) else Color(0x14FFFFFF))
                .border(
                    if (guiding) 1.5.dp else 0.75.dp,
                    when {
                        guiding -> QuestGold.copy(alpha = glow)
                        complete && !claimed -> QuestGold.copy(alpha = 0.45f)
                        else -> Color(0x1AFFFFFF)
                    },
                    RoundedCornerShape(16.dp),
                )
                .then(if (guiding && onGo != null) Modifier.clickable(onClick = onGo) else Modifier)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (claimed) Brush.linearGradient(listOf(Mint.copy(alpha = 0.35f), Blue.copy(alpha = 0.35f)))
                        else Brush.linearGradient(listOf(Color(0xFF26304A), Color(0xFF161C2C)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (claimed) Icons.Filled.Check else icon, null,
                    tint = if (claimed) Mint else QuestGold, modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (guiding) {
                    Text(
                        stringResource(R.string.quest_start_here),
                        color = QuestGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                } else if (tier != null && !claimed) {
                    TierTag(tier)
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    title,
                    color = if (claimed) Color(0xFF8A93A3) else Color(0xFFF0F0F0),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(hint, color = Color(0xFF8A93A3), fontSize = 11.5.sp, lineHeight = 16.sp)
                if (target > 1 && !claimed) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x22FFFFFF)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Mint, QuestGold))),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$progress/$target",
                            style = TextStyle(color = Color(0xFFB8C0CC), fontSize = 11.sp, fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            when {
                claimed -> Text(stringResource(R.string.quest_claimed), color = Color(0xFF6B7484), fontSize = 12.sp)
                guiding && onGo != null -> Text(
                    stringResource(R.string.quest_go),
                    color = Color(0xFF2A1A00), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickBounce()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(QuestGold, Color(0xFFFFB259))))
                        .clickable(onClick = onGo)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                complete -> Box(
                    modifier = Modifier
                        .clickBounce()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(QuestGold, Color(0xFFFFB259))))
                        .clickable(onClick = onClaim)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StardustIcon(Modifier.size(14.dp), Color(0xFF2A1A00))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.quest_claim), color = Color(0xFF2A1A00), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StardustIcon(Modifier.size(14.dp), QuestGold.copy(alpha = 0.75f))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "×$reward",
                        color = QuestGold.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
