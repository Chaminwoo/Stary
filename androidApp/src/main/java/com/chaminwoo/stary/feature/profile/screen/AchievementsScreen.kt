package com.chaminwoo.stary.feature.profile.screen

import com.chaminwoo.stary.core.ui.ScreenBackground
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.chaminwoo.stary.core.ui.rememberBackgroundScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.chaminwoo.stary.core.ui.FirstVisitInfo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.rememberCurrentUserName
import com.chaminwoo.stary.data.repository.HiddenAchievementRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.profile.Achievement
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.HiddenAchievement
import com.chaminwoo.stary.feature.profile.HiddenAchievements
import com.chaminwoo.stary.feature.profile.HiddenClaim
import com.chaminwoo.stary.feature.profile.HiddenIconWithEffect
import com.chaminwoo.stary.feature.profile.Reward
import com.chaminwoo.stary.feature.profile.StigmaStore
import com.chaminwoo.stary.feature.profile.rememberUserStats
import kotlinx.coroutines.launch
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset

private val Accent = Color(0xFF9FB3E8) // 남색 계열 라이트 강조(구 민트)
private val Gold = Color(0xFFFFD86F)
private val Purple = Color(0xFFB388FF)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xFF15151F)

/**
 * 업적 화면 — **공용 / 특별 / 히든** 3탭(2026-10-19 재구성 — 69개가 한 목록에 섞여 난잡하던 것을 나눴다).
 *  - 공용: 누구나 쓰다 보면 얻는 업적. 칭호(주제별 묶음) · 별 모양 · 별 색을 작은 탭으로 나누고, 모양·색은 3열 타일.
 *  - 특별: 모두가 갖지는 못하는 업적 — 특별한 순간(날짜·시각) · 퀘스트 보상 · 초대 · 개척 칭호.
 *  - 히든: 앱 전체에서 단 한 명(HiddenAchievements).
 * 분류 목록은 [Achievements.commonTitleGroups]/[Achievements.specialGroups] (iOS 와 같은 id·순서).
 */
@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val userId = GoogleAuthHelper.currentUserId
    androidx.compose.runtime.LaunchedEffect(Unit) { com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.VISIT_ACHIEVEMENTS) }

    if (userId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_login_required), color = TextMuted, fontSize = 18.sp)
        }
        return
    }

    val stats = rememberUserStats(userId)
    var equipped by remember { mutableStateOf(StigmaStore.equipped(context, userId)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }

    // 히든 업적 달성 현황(전역)
    val hiddenRepo = remember { HiddenAchievementRepository() }
    val claims by remember { hiddenRepo.observe() }.collectAsState(initial = emptyMap())

    // 개척 퀘스트(체크리스트 32) — 내가 개척한 나라들 = 장착 가능한 개척 칭호.
    val pioneerClaims by remember {
        com.chaminwoo.stary.data.repository.FirebasePioneerRepository().observeClaims()
    }.collectAsState(initial = emptyMap())
    val myPioneerCodes = remember(pioneerClaims, userId) {
        pioneerClaims.filterValues { it.userId == userId }.keys.sorted()
    }

    // 칭호 장착/해제 — 공용·특별·히든·개척 공통. [display] = 토스트에 보여 줄 현재 언어 칭호명.
    val toggleEquip: (String, String) -> Unit = { id, display ->
        val next = if (equipped == id) null else id
        StigmaStore.equip(context, userId, next)
        equipped = next
        if (next != null) com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.EQUIP_TITLE)
        scope.launch {
            com.chaminwoo.stary.data.repository.FirebaseFriendRepository()
                .setEquippedTitle(userId, next)
        }
        com.chaminwoo.stary.core.ui.StaryToast.show(
            if (next != null) context.getString(R.string.ach_title_equipped, display)
            else context.getString(R.string.ach_title_unequipped)
        )
    }
    val toggleAch: (Achievement) -> Unit = { ach ->
        toggleEquip(ach.id, com.chaminwoo.stary.core.util.LocalizedNames.title(context, ach.id, ach.titleName ?: ach.name)!!)
    }

    val bgScroll = rememberBackgroundScroll()
    Box(modifier = modifier.nestedScroll(bgScroll).fillMaxSize()) {
        // 내 다이어리 페이지와 동일한 배경·밝기
        ScreenBackground(R.drawable.mydiary_bg, darken = 0.82f, scroll = bgScroll)

        FirstVisitInfo(
            seenKey = "info_achievements",
            icon = Icons.Filled.EmojiEvents,
            title = stringResource(R.string.onb_achievements_title),
            message = stringResource(R.string.onb_achievements_msg),
        )

        Column(Modifier.fillMaxSize().padding(top = LocalTopBarInset.current)) {
            TabBar(tab = tab, onSelect = { tab = it })

            when (tab) {
                0 -> CommonTab(stats = stats, equipped = equipped, onToggleEquip = toggleAch)
                1 -> SpecialTab(
                    stats = stats,
                    equipped = equipped,
                    myPioneerCodes = myPioneerCodes,
                    onToggleEquip = toggleAch,
                    onTogglePioneer = toggleEquip,
                )
                else -> HiddenTab(
                    claims = claims,
                    myUid = userId,
                    equipped = equipped,
                    onToggleEquip = { ach ->
                        toggleEquip(ach.id, com.chaminwoo.stary.core.util.LocalizedNames.title(context, ach.id, ach.title)!!)
                    }
                )
            }
        }
    }
}

@Composable
private fun TabBar(tab: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabChip(stringResource(R.string.ach_tab_normal), selected = tab == 0, accent = Accent) { onSelect(0) }
        TabChip(stringResource(R.string.ach_tab_special), selected = tab == 1, accent = Purple) { onSelect(1) }
        TabChip(stringResource(R.string.ach_tab_hidden), selected = tab == 2, accent = Gold) { onSelect(2) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) accent else TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 업적 진행도 — 퍼센트 막대 대신 **성운이 차오르는 밴드**.
 * 왼쪽부터 달성 비율만큼 성운(민트+보라 blob)이 짙어지고, 나머지는 빈 밤하늘(잔별만).
 * 경계는 blob 별 알파 감쇠로 부드럽게 흐려진다(하드 컷 없음). 값이 바뀌면 채움이 애니메이션.
 *
 * 별/blob 배치는 고정 시드 해시라 매 리컴포지션마다 흔들리지 않는다.
 */
@Composable
private fun NebulaProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val target = fraction.coerceIn(0f, 1f)
    val filled by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(700),
        label = "nebula_fill",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .drawBehind {
                val w = size.width
                val h = size.height
                val fw = w * filled
                val soft = w * 0.14f // 채움 경계가 흐려지는 폭

                // 성운 blob — 채움 경계를 넘어갈수록 옅어진다(부드러운 채움).
                val blobs = listOf(
                    Triple(0.06f, 0.55f, Accent),
                    Triple(0.20f, 0.30f, Purple),
                    Triple(0.34f, 0.68f, Accent),
                    Triple(0.48f, 0.36f, Purple),
                    Triple(0.62f, 0.60f, Accent),
                    Triple(0.76f, 0.32f, Purple),
                    Triple(0.90f, 0.58f, Accent),
                )
                blobs.forEach { (px, py, color) ->
                    val cx = w * px
                    val reveal = ((fw - cx) / soft + 0.5f).coerceIn(0f, 1f)
                    if (reveal <= 0.01f) return@forEach
                    val r = h * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.30f * reveal),
                                color.copy(alpha = 0.10f * reveal),
                                Color.Transparent,
                            ),
                            center = Offset(cx, h * py),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(cx, h * py),
                    )
                }

                // 잔별 — 전 구간에 뿌리되, 성운 안쪽이 더 밝다.
                repeat(30) { i ->
                    val fx = ((i * 37 % 100) / 100f)
                    val fy = ((i * 61 % 100) / 100f)
                    val cx = w * fx
                    val inNebula = if (cx <= fw) 1f else 0.35f
                    val rad = 0.7f + (i % 3) * 0.5f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f + 0.35f * inNebula * ((i % 5) / 5f + 0.2f)),
                        radius = rad,
                        center = Offset(cx, h * (0.12f + 0.76f * fy)),
                    )
                }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

// ────────────────────────── 공용 업적 탭 ──────────────────────────

@Composable
private fun CommonTab(
    stats: com.chaminwoo.stary.feature.profile.UserStats,
    equipped: String?,
    onToggleEquip: (Achievement) -> Unit,
) {
    // 0 = 칭호 / 1 = 별 모양 / 2 = 별 색
    var sub by remember { mutableIntStateOf(0) }
    val titles = Achievements.commonTitles
    val shapes = Achievements.commonShapes
    val colors = Achievements.commonColors
    val all = titles + shapes + colors
    val done = all.count { it.unlocked(stats) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(stringResource(R.string.ach_intro), color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                // 진행도 = 막대 대신 **성운이 차오르는 밴드**. 달성할수록 성운이 오른쪽으로 짙어진다.
                NebulaProgress(
                    fraction = done.toFloat() / all.size.coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.ach_progress, done, all.size),
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Light
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubChip(stringResource(R.string.ach_group_titles), titles.count { it.unlocked(stats) }, titles.size, sub == 0) { sub = 0 }
                    SubChip(stringResource(R.string.ach_sub_shapes), shapes.count { it.unlocked(stats) }, shapes.size, sub == 1) { sub = 1 }
                    SubChip(stringResource(R.string.ach_sub_colors), colors.count { it.unlocked(stats) }, colors.size, sub == 2) { sub = 2 }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (sub == 0) R.string.ach_group_titles_sub else R.string.ach_group_rewards_sub),
                    color = TextMuted.copy(alpha = 0.8f), fontSize = 12.sp
                )
            }
        }

        when (sub) {
            0 -> Achievements.commonTitleGroups.forEach { (theme, list) ->
                item(key = "theme_$theme") {
                    ThemeHeader(themeLabel(theme), list.count { it.unlocked(stats) }, list.size)
                }
                items(list, key = { it.id }) { ach ->
                    val unlocked = ach.unlocked(stats)
                    TitleAchievementRow(
                        ach = ach,
                        unlocked = unlocked,
                        equipped = equipped == ach.id,
                        onToggleEquip = { if (unlocked) onToggleEquip(ach) }
                    )
                }
            }
            else -> {
                // 모양·색은 이름이 짧고 그림이 중요해 3열 타일로 — 한눈에 무엇이 열렸는지 보인다.
                val list = if (sub == 1) shapes else colors
                items(list.chunked(3), key = { row -> "tiles_${row.first().id}" }) { row ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { ach ->
                            RewardTile(ach, ach.unlocked(stats), Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun themeLabel(theme: Achievements.Theme): String = stringResource(
    when (theme) {
        Achievements.Theme.RECORD -> R.string.ach_theme_record
        Achievements.Theme.STREAK -> R.string.ach_theme_streak
        Achievements.Theme.PLACE -> R.string.ach_theme_place
        Achievements.Theme.EXPLORE -> R.string.ach_theme_explore
        Achievements.Theme.SOCIAL -> R.string.ach_theme_social
        Achievements.Theme.DECOR -> R.string.ach_theme_decor
        Achievements.Theme.METEOR -> R.string.ach_theme_meteor
    }
)

/** 공용 탭 안의 작은 탭(칭호/모양/색) — 이름 아래에 달성 수. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SubChip(
    label: String,
    done: Int,
    total: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected) TextMain else TextMuted, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        Text("$done / $total", color = if (selected) Accent else TextMuted.copy(alpha = 0.7f), fontSize = 11.sp,
            fontWeight = FontWeight.Light)
    }
}

/** 칭호 주제 머리글 — 이름 · 가는 선 · 달성 수. */
@Composable
private fun ThemeHeader(label: String, done: Int, total: Int, accent: Color = Accent) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(accent.copy(alpha = 0.18f)))
        Spacer(Modifier.width(10.dp))
        Text("$done / $total", color = if (done == total) accent else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Light)
    }
}

/** 별 모양/색 타일 — 큰 미리보기 + 이름 + 조건. 열리면 그 별빛으로 은은하게 빛난다. */
@Composable
private fun RewardTile(ach: Achievement, unlocked: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val glow = when (val r = ach.reward) {
        is Reward.StarColor -> StarStyle.colorOf(r.colorIndex)
        else -> StarStyle.colorOf(9)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                1.dp,
                if (unlocked) glow.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (unlocked) Brush.radialGradient(listOf(glow.copy(alpha = 0.26f), Color.Transparent))
                    else Brush.radialGradient(listOf(Color.Transparent, Color.Transparent)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            RewardPreview(ach.reward, unlocked, box = 48.dp, icon = 30.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            com.chaminwoo.stary.core.util.LocalizedNames.title(context, ach.id, ach.name)!!,
            color = if (unlocked) TextMain else TextMuted,
            fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (ach.hidden && !unlocked) "???"
            else com.chaminwoo.stary.core.util.LocalizedNames.condition(context, ach.id, ach.condition),
            color = TextMuted.copy(alpha = if (unlocked) 0.9f else 0.7f),
            fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ────────────────────────── 특별 업적 탭 ──────────────────────────

@Composable
private fun SpecialTab(
    stats: com.chaminwoo.stary.feature.profile.UserStats,
    equipped: String?,
    myPioneerCodes: List<String>,
    onToggleEquip: (Achievement) -> Unit,
    onTogglePioneer: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val list = Achievements.special
    val done = list.count { it.unlocked(stats) } + myPioneerCodes.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(stringResource(R.string.ach_special_intro), color = Purple.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
                NebulaProgress(
                    fraction = done.toFloat() / (list.size + myPioneerCodes.size).coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.ach_special_count, done),
                        color = Purple, fontSize = 13.sp, fontWeight = FontWeight.Light
                    )
                }
            }
        }

        Achievements.specialGroups.forEach { (group, items) ->
            item(key = "special_$group") {
                val (title, sub) = when (group) {
                    Achievements.Special.MOMENTS -> R.string.ach_special_moments to R.string.ach_special_moments_sub
                    Achievements.Special.QUEST -> R.string.ach_special_quest to R.string.ach_special_quest_sub
                    Achievements.Special.INVITE -> R.string.ach_special_invite to R.string.ach_special_invite_sub
                }
                SpecialHeader(stringResource(title), stringResource(sub), items.count { it.unlocked(stats) }, items.size)
            }
            items(items, key = { it.id }) { ach ->
                val unlocked = ach.unlocked(stats)
                if (ach.reward is Reward.Title) {
                    TitleAchievementRow(
                        ach = ach,
                        unlocked = unlocked,
                        equipped = equipped == ach.id,
                        accent = Purple,
                        onToggleEquip = { if (unlocked) onToggleEquip(ach) }
                    )
                } else {
                    RewardAchievementRow(ach = ach, unlocked = unlocked, accent = Purple)
                }
            }
        }

        // 개척 칭호(체크리스트 32) — 내가 개척한 나라만, 탭으로 장착/해제. 없으면 얻는 법을 한 장으로 안내.
        item(key = "special_pioneer") {
            SpecialHeader(stringResource(R.string.ach_pioneer_section), stringResource(R.string.pioneer_condition),
                myPioneerCodes.size, null)
        }
        if (myPioneerCodes.isEmpty()) {
            item(key = "pioneer_empty") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Flag, null, tint = TextMuted, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.ach_pioneer_empty), color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        } else {
            items(myPioneerCodes, key = { "pioneer_$it" }) { code ->
                val titleId = com.chaminwoo.stary.shared.config.PioneerQuest.titleId(code)
                val display = com.chaminwoo.stary.core.util.LocalizedNames.pioneerTitle(context, titleId)
                    ?: com.chaminwoo.stary.core.util.LocalizedNames.countryName(code)
                val synthetic = Achievement(
                    id = titleId, name = display,
                    condition = stringResource(R.string.pioneer_condition),
                    reward = Reward.Title(display),
                ) { true }
                TitleAchievementRow(
                    ach = synthetic,
                    unlocked = true,
                    equipped = equipped == titleId,
                    accent = Purple,
                    leadingIcon = Icons.Filled.Flag,
                    onToggleEquip = { onTogglePioneer(titleId, display) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 특별 묶음 머리글 — 제목·달성 수 + 한 줄 설명(어떻게 얻는지). [total] null = 개수 제한 없음(개척). */
@Composable
private fun SpecialHeader(title: String, subtitle: String, done: Int, total: Int?) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Purple.copy(alpha = 0.22f)))
            Spacer(Modifier.width(10.dp))
            Text(
                if (total != null) "$done / $total" else "$done",
                color = if (total != null && done == total) Purple else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Light
            )
        }
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

// ────────────────────────── 히든 업적 탭 ──────────────────────────

@Composable
private fun HiddenTab(
    claims: Map<String, HiddenClaim>,
    myUid: String,
    equipped: String?,
    onToggleEquip: (HiddenAchievement) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(stringResource(R.string.ach_hidden_intro), color = Gold.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        items(HiddenAchievements.all, key = { it.id }) { ach ->
            HiddenAchievementRow(
                ach = ach,
                claim = claims[ach.id],
                mine = claims[ach.id]?.achieverId == myUid && myUid.isNotBlank(),
                equipped = equipped == ach.id,
                onToggleEquip = { onToggleEquip(ach) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HiddenAchievementRow(
    ach: HiddenAchievement,
    claim: HiddenClaim?,
    mine: Boolean,
    equipped: Boolean,
    onToggleEquip: () -> Unit,
) {
    val claimed = claim?.claimed == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                width = if (equipped) 1.5.dp else 1.dp,
                color = when {
                    equipped -> Gold
                    mine -> Gold.copy(alpha = 0.6f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .then(if (mine) Modifier.clickable(onClick = onToggleEquip) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 엠블럼(항상 노출 — 수집 진열장): 내 것 = 이펙트까지 / 남의 것 = 색·광택만 / 아직 아무도 = 어두운 봉인 + 흐린 이펙트.
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            HiddenIconWithEffect(
                ach, size = 48.dp,
                state = when {
                    mine -> com.chaminwoo.stary.feature.profile.EmblemState.OWNED
                    claimed -> com.chaminwoo.stary.feature.profile.EmblemState.CLAIMED
                    else -> com.chaminwoo.stary.feature.profile.EmblemState.SEALED
                },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // 칭호(제목)는 항상 보여준다. (언어 전환에 맞춰 로케일 해석)
            // 옆의 작은 크리스탈 = 달성자 이름 옆에 붙는 **이 업적 전용 배지**(어떤 별을 얻는지 미리 보여줌).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.chaminwoo.stary.core.util.LocalizedNames.title(LocalContext.current, ach.id, ach.title)!!,
                    color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Light
                )
                Spacer(Modifier.width(6.dp))
                com.chaminwoo.stary.core.ui.HiddenStarBadge(
                    type = ach.badgeType, colorIndex = ach.badgeColor, size = 14.dp
                )
            }
            Spacer(Modifier.height(2.dp))
            // 조건은 달성 후에만 공개.
            Text(
                if (claimed) com.chaminwoo.stary.core.util.LocalizedNames.condition(LocalContext.current, ach.id, ach.condition)
                else "???",
                color = if (claimed) TextMuted else Purple.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            if (claimed) {
                Spacer(Modifier.height(3.dp))
                // 달성자 이름은 선점 시점 스냅샷(achieverName)이 아니라 users/{uid} 의 **현재 닉네임**으로 표시.
                // (닉네임을 바꾸면 업적 화면의 달성자도 함께 바뀐다 — 댓글/작성자 표시와 같은 규칙)
                val achieverName = rememberCurrentUserName(
                    claim?.achieverId.orEmpty(),
                    claim?.achieverName?.ifBlank { "?" } ?: "?",
                )
                Text(
                    if (mine) stringResource(R.string.ach_hidden_by_me)
                    else stringResource(R.string.ach_hidden_achiever, achieverName),
                    color = if (mine) Gold else Accent, fontSize = 12.sp, fontWeight = FontWeight.Light
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            equipped -> Text(stringResource(R.string.ach_equipped), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Light)
            mine -> Text(stringResource(R.string.ach_equip), color = TextMain, fontSize = 12.sp)
            claimed -> Icon(Icons.Filled.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            else -> Text(stringResource(R.string.ach_hidden_unclaimed), color = TextMuted, fontSize = 11.sp)
        }
    }
}

// ────────────────────────── 공용 행 ──────────────────────────

@Composable
private fun TitleAchievementRow(
    ach: Achievement,
    unlocked: Boolean,
    equipped: Boolean,
    accent: Color = Accent,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Star,
    onToggleEquip: () -> Unit,
) {
    AchievementRowFrame(
        equipped = equipped,
        accent = accent,
        clickEnabled = unlocked,
        onClick = onToggleEquip,
        leading = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (unlocked) leadingIcon else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) accent else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = com.chaminwoo.stary.core.util.LocalizedNames.title(LocalContext.current, ach.id, ach.name)!!,
        // 숨김 업적은 달성 전까지 조건을 ??? 로 가린다(이름은 힌트로 보여 준다).
        subtitle = if (ach.hidden && !unlocked) "???"
            else com.chaminwoo.stary.core.util.LocalizedNames.condition(LocalContext.current, ach.id, ach.condition),
        unlocked = unlocked,
        trailing = {
            when {
                equipped -> Text(stringResource(R.string.ach_equipped), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Light)
                unlocked -> Text(stringResource(R.string.ach_equip), color = TextMain, fontSize = 12.sp)
                else -> Text(stringResource(R.string.cd_locked), color = TextMuted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun RewardAchievementRow(ach: Achievement, unlocked: Boolean, accent: Color = Accent) {
    AchievementRowFrame(
        equipped = false,
        accent = accent,
        clickEnabled = false,
        onClick = {},
        leading = { RewardPreview(ach.reward, unlocked) },
        title = com.chaminwoo.stary.core.util.LocalizedNames.title(LocalContext.current, ach.id, ach.name)!!,
        // 숨김 업적은 달성 전까지 조건을 ??? 로 가린다(이름은 힌트로 보여 준다).
        subtitle = if (ach.hidden && !unlocked) "???"
            else com.chaminwoo.stary.core.util.LocalizedNames.condition(LocalContext.current, ach.id, ach.condition),
        unlocked = unlocked,
        trailing = {
            if (unlocked) Text(stringResource(R.string.ach_obtained), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Light)
            else Text(stringResource(R.string.cd_locked), color = TextMuted, fontSize = 12.sp)
        }
    )
}

/** 보상 미리보기 — 모양이면 별 아이콘, 색이면 색 원. 잠겨 있으면 흐릿 + 자물쇠. */
@Composable
private fun RewardPreview(
    reward: Reward,
    unlocked: Boolean,
    box: androidx.compose.ui.unit.Dp = 44.dp,
    icon: androidx.compose.ui.unit.Dp = 26.dp,
) {
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        when (reward) {
            is Reward.Shape -> StarShapeIcon(
                type = reward.shapeType,
                color = if (unlocked) StarStyle.colorOf(9) else Color.White.copy(0.22f),
                modifier = Modifier.size(icon)
            )
            is Reward.StarColor -> {
                val cs = StarStyle.colorsOf(reward.colorIndex)
                val brush = if (cs.size > 1) androidx.compose.ui.graphics.Brush.linearGradient(cs)
                            else androidx.compose.ui.graphics.SolidColor(cs[0])
                Box(
                    modifier = Modifier
                        .size(icon)
                        .clip(CircleShape)
                        .background(brush)
                        .then(if (unlocked) Modifier else Modifier.background(Color.Black.copy(alpha = 0.45f)))
                )
            }
            else -> Icon(Icons.Filled.Star, null, tint = Accent, modifier = Modifier.size(22.dp))
        }
        if (!unlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun AchievementRowFrame(
    equipped: Boolean,
    accent: Color = Accent,
    clickEnabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    unlocked: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                width = if (equipped) 1.5.dp else 1.dp,
                color = if (equipped) accent else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = clickEnabled) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (unlocked) TextMain else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}
