package com.chaminwoo.stary.core.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.util.AppUpdateChecker
import com.chaminwoo.stary.core.util.MapUiState
import kotlinx.coroutines.delay

/**
 * 평점·"고칠 점" 요청 카드 — 별을 올린 직후(별 탄생 연출이 끝난 뒤)에만, 아주 드물게 한 번 띄운다(2026-11-02).
 *
 * 정책(Play 리뷰 요청 정책 + 사용자 결정):
 *  - 문구는 의견을 묻거나("앱이 마음에 드세요?") 별점을 유도하지 않는 중립 문장만, 보상 언급 금지.
 *  - 두 버튼은 동등하다: [평점 남기기 → 플레이 스토어 상세] / [고칠 점 보내기 → 인앱 의견 창].
 *    Play 인앱 리뷰 API 는 쓰지 않는다(버튼이 스토어로 보내는 구조라 Google 안내상 API 호출 대신 스토어 이동이어야 함).
 *  - 조건: 설치 [MIN_INSTALL_DAYS]일 이상 + 내가 올린 별 [MIN_STARS]개 이상(MainScreen 이 이미 구독하는 `getMyDiaries` 의 개수 —
 *    새 쿼리 없음). 평생 [MAX_SHOWS]번까지, 직전 노출로부터 [MIN_GAP_DAYS]일 이상 간격.
 *  - 버튼을 한 번이라도 누르면(평점·의견 어느 쪽이든) 다시는 안 띄운다. 무시·닫기·8초 자동 닫힘은 "노출 1회"로 센다
 *    (노출 시점에 바로 기록하므로 앱이 꺼져도 센다).
 *  - 다른 안내가 떠 있으면 줄 세우지 않고 이번엔 건너뛴다(가드는 MainScreen 이 [ReviewPromptHost] 의 `canShow` 로 넘긴다).
 * 기록은 기기별 전용 SharedPreferences([PREFS]) — iOS `ReviewPromptStore`(UserDefaults)와 같은 상수/규칙.
 * iOS 는 Apple 가이드라인 5.6.1 때문에 카드 없이 시스템 평점 창(`requestReview`)만 같은 시점·같은 조건에서 띄운다.
 */
object ReviewPrompt {
    private const val PREFS = "stary_review_prompt"
    private const val KEY_SHOWN_COUNT = "shown_count"
    private const val KEY_LAST_SHOWN_AT = "last_shown_at"
    private const val KEY_ACTED = "acted"

    /** 설치 후 최소 일수 / 올린 별 최소 개수 / 평생 최대 노출 / 노출 사이 최소 간격(일). iOS `ReviewPromptStore` 와 같은 값. */
    const val MIN_INSTALL_DAYS = 7
    const val MIN_STARS = 3
    const val MAX_SHOWS = 3
    const val MIN_GAP_DAYS = 90

    /** 별 탄생 연출이 끝난 뒤 카드를 띄우기까지의 대기 / 카드가 저절로 닫히기까지. iOS 의 요청 지연(1.5초)과 같다. */
    const val DELAY_AFTER_BIRTH_MS = 1_500L
    const val AUTO_DISMISS_MS = 8_000L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 이 기기에서 앱을 처음 설치한 지 [MIN_INSTALL_DAYS]일이 지났는가(업데이트로는 갱신되지 않는 firstInstallTime). */
    private fun installedLongEnough(context: Context): Boolean = try {
        val first = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        System.currentTimeMillis() - first >= MIN_INSTALL_DAYS * DAY_MS
    } catch (_: Exception) {
        false
    }

    /** 기록(횟수·간격·버튼 눌렀는지) 기준으로 지금 띄워도 되는가. */
    private fun historyAllows(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_ACTED, false)) return false
        if (p.getInt(KEY_SHOWN_COUNT, 0) >= MAX_SHOWS) return false
        val last = p.getLong(KEY_LAST_SHOWN_AT, 0L)
        return last == 0L || System.currentTimeMillis() - last >= MIN_GAP_DAYS * DAY_MS
    }

    /** 설치 기간 + 별 개수 + 기록 — 화면 상태(가드)를 뺀 자격. */
    fun eligible(context: Context, starCount: Int): Boolean =
        starCount >= MIN_STARS && installedLongEnough(context) && historyAllows(context)

    /** 노출 1회 기록 — 카드를 띄우는 순간 부른다(무시·닫기·자동 닫힘도 이 한 번으로 센다). */
    fun markShown(context: Context) {
        val p = prefs(context)
        p.edit()
            .putInt(KEY_SHOWN_COUNT, p.getInt(KEY_SHOWN_COUNT, 0) + 1)
            .putLong(KEY_LAST_SHOWN_AT, System.currentTimeMillis())
            .apply()
    }

    /** 평점/의견 버튼을 눌렀다 — 다시는 안 띄운다. */
    fun markActed(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTED, true).apply()
    }
}

private val CardNavy = Color(0xFF1B2546)      // 토스트와 같은 남색
private val CardNavyDeep = Color(0xFF131B36)

/**
 * 요청 카드 호스트 — MainScreen 의 [PermissionAskHost] / [StaryToastHost] 근처에 한 번 둔다.
 * 별 탄생 연출([StarBirthState.pending] 이 값 → null)이 끝나고 [ReviewPrompt.DELAY_AFTER_BIRTH_MS] 뒤,
 * [canShow]( 지도 위에 다른 안내가 없는지 )와 [starCount]( 내가 올린 별 수 )·기록이 모두 맞을 때만 카드를 띄운다.
 * 카드 밖은 터치를 막지 않는다. 떠 있는 동안 집중 모드(지도만 보기)로 들어가지 않게 [MapUiState.holdIdle] 한다.
 */
@Composable
fun ReviewPromptHost(
    canShow: () -> Boolean,
    starCount: () -> Int,
) {
    val context = LocalContext.current
    val canShowNow by rememberUpdatedState(canShow)
    val starCountNow by rememberUpdatedState(starCount)
    var visible by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    // 이번 실행에서 별 탄생 연출을 본 적이 있는가 — 연출이 끝나는 순간(pending → null)에만 카드를 노린다.
    var sawBirth by remember { mutableStateOf(false) }
    val birth = StarBirthState.pending

    LaunchedEffect(birth) {
        if (birth != null) {
            sawBirth = true
            return@LaunchedEffect
        }
        if (!sawBirth) return@LaunchedEffect
        sawBirth = false
        delay(ReviewPrompt.DELAY_AFTER_BIRTH_MS)
        // 대기 중 다른 안내가 떴으면(업적 팝업 등) 줄 세우지 않고 이번엔 건너뛴다.
        if (visible || showFeedback) return@LaunchedEffect
        if (!canShowNow() || !ReviewPrompt.eligible(context, starCountNow())) return@LaunchedEffect
        ReviewPrompt.markShown(context)
        visible = true
    }

    // 8초 뒤 저절로 닫힘(무시와 같은 취급 — 이미 노출 1회로 기록됨).
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(ReviewPrompt.AUTO_DISMISS_MS)
        visible = false
    }

    // 카드/의견 창이 떠 있는 동안엔 집중 모드로 들어가지 않는다(코치마크와 같은 방식).
    if (visible || showFeedback) {
        DisposableEffect(Unit) {
            MapUiState.holdIdle()
            onDispose { MapUiState.releaseIdle() }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(220)),
        ) {
            ReviewPromptCard(
                onRate = {
                    ReviewPrompt.markActed(context)
                    visible = false
                    AppUpdateChecker.openStore(context)
                },
                onFeedback = {
                    ReviewPrompt.markActed(context)
                    visible = false
                    showFeedback = true
                },
                onClose = { visible = false },
            )
        }
    }

    if (showFeedback) FeedbackDialog(onDismiss = { showFeedback = false })
}

@Composable
private fun ReviewPromptCard(onRate: () -> Unit, onFeedback: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    // 아래 버튼·토스트(하단 96dp)와 겹치지 않도록 조금 더 위에 둔다.
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 120.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(16.dp, shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(CardNavy, CardNavyDeep)))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.review_prompt_msg),
                    color = Color(0xFFF3F5FF), fontFamily = MinSans, fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close, stringResource(R.string.review_prompt_close),
                        tint = Color(0xFFC5CBDB), modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 두 버튼은 같은 모양·같은 폭(한쪽으로 유도하지 않는다).
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StaryButton(
                    stringResource(R.string.review_prompt_rate), onRate,
                    Modifier.weight(1f).heightIn(min = 44.dp), kind = StaryButtonKind.Ghost, compact = true,
                )
                StaryButton(
                    stringResource(R.string.review_prompt_feedback), onFeedback,
                    Modifier.weight(1f).heightIn(min = 44.dp), kind = StaryButtonKind.Ghost, compact = true,
                )
            }
        }
    }
}
