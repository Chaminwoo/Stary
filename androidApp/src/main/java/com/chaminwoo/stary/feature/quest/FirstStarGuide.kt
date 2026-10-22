package com.chaminwoo.stary.feature.quest

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 첫 별 안내(2026-10-22) — 광고로 들어온 새 사용자가 "첫 별 남기기 → 퀘스트에서 열쇠 받기 → 그 열쇠로 남의 별 열기"를
 * 차례로 해 보도록 지도 위에 한 장의 카드로 다음 할 일을 보여 준다. iOS `FirstStarGuide.swift` 와 같은 단계·조건.
 *
 * 단계는 저장하지 않고 **지금 상태에서 계산**한다(어느 기기에서 무엇을 했든 맞게 따라온다):
 *  - [GuideStep.WRITE] : 내 별이 아직 없다 → 업로드 화면으로.
 *  - [GuideStep.CLAIM] : 내 별은 있고 열쇠가 0개 → 퀘스트 창(받을 보상이 있을 때) / 없으면 안내용 열쇠 1개를 바로 준다.
 *  - [GuideStep.OPEN]  : 열쇠가 있다 → 가장 가까운 "잠긴" 남의 별로 카메라를 옮긴 뒤 상세(잠금 카드에서 열쇠 사용).
 *  - 끝: 열쇠를 처음 쓰면([QuestStore.spendKey]) 또는 열어 볼 별이 없으면 `users/{uid}.guideDone` — 다시 뜨지 않는다.
 * ⚠️ 이미 별을 올려 본 기존 사용자에게 CLAIM/OPEN 이 갑자기 뜨지 않도록, **이 기기에서 WRITE 단계를 본 계정만** 다음 단계로 간다
 *    ([startedKey], 계정별 prefs). 닫기(X)는 이번 실행에서만 숨긴다.
 */
enum class GuideStep(val id: String) { WRITE("write"), CLAIM("claim"), OPEN("open") }

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("stary_first_guide", Context.MODE_PRIVATE)

private fun startedKey(uid: String) = "started_$uid"

/** 지금 안내할 단계(null = 안내 없음). */
private fun currentStep(context: Context, userId: String?, diaries: List<Diary>): GuideStep? {
    if (userId == null || !QuestStore.guideLoaded || QuestStore.guideDone) return null
    // 목록을 아직 못 받았으면(빈 목록) "내 별 없음"으로 오판하지 않게 기다린다.
    if (diaries.isEmpty()) return null
    val hasOwn = diaries.any { it.userId == userId }
    if (!hasOwn) return GuideStep.WRITE
    if (!prefs(context).getBoolean(startedKey(userId), false)) return null
    return if (QuestStore.keys > 0) GuideStep.OPEN else GuideStep.CLAIM
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
    onOpenStar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hiddenStep by remember(userId) { mutableStateOf<GuideStep?>(null) }
    val step = currentStep(context, userId, diaries)
    val shown = enabled && step != null && step != hiddenStep

    LaunchedEffect(step, shown) {
        if (!shown || step == null || userId == null) return@LaunchedEffect
        if (step == GuideStep.WRITE) prefs(context).edit().putBoolean(startedKey(userId), true).apply()
        if (step == GuideStep.OPEN) QuestStore.guideOpenShown = true
        StaryAnalytics.logOnce("guide_${step.id}", "guide_step", "step" to step.id)
    }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { -it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 },
        modifier = modifier,
    ) {
        // 사라지는 동안에도 마지막 단계 내용을 그대로 그린다.
        val last = remember { mutableStateOf(step ?: GuideStep.WRITE) }
        if (step != null) last.value = step
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
                    hiddenStep = s
                },
            )
        }
    }
}

@Composable
private fun GuideCard(step: GuideStep, onAction: () -> Unit, onClose: () -> Unit) {
    val (icon, title, msg, btn) = when (step) {
        GuideStep.WRITE -> GuideText(Icons.Filled.Add, R.string.guide_write_title, R.string.guide_write_msg, R.string.guide_write_btn)
        GuideStep.CLAIM -> GuideText(Icons.Filled.Key, R.string.guide_claim_title, R.string.guide_claim_msg, R.string.guide_claim_btn)
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
                Text(stringResource(msg), color = Color(0xFFB8BCCB), fontSize = 12.sp, lineHeight = 16.sp)
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
