package com.chaminwoo.stary.feature.diary.screen

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ads.UnityAdsManager
import com.chaminwoo.stary.core.ui.MediaLoadingFrame
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.ui.bakeCrystalIcon
import com.chaminwoo.stary.core.util.DiaryUnlockStore
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.exp

// ── 100m 밖 게시물 잠금 UI (2026-09-22 개편) ─────────────────────────────────────
// DetailScreen 에서 떼어 둔 이유: 본체에 인라인하면 dex 메서드 레지스터 한계(256)로 VerifyError 이력이 있다.
//
// - 히어로: 미디어가 **있을 때만** 미디어 로딩 플레이스홀더(loading_dipper) + 우하단 작은 캡션 "이 사진/영상은 잠겨 있어요".
//   미디어가 없으면 잠금 표시 없이 일반 글과 같은 image_frame(DetailScreen 의 else 분기).
// - 본문 자리: 좌상단·우하단 십자 코너 안에 크리스탈 재생 로고(유튜브형) + "100m 이내로 다가가거나, 광고를 통해 열어보세요!"
//   + 현재 거리.
// - 재생 로고는 [CrystalPullIcon] — 제자리에 고정돼 있다가 잡아당기면 버티며 조금만 끌려오고,
//   놓으면 튕기듯 돌아간다. **탭 = 보상형 광고**([watchAdToUnlock]). 따로 "광고 보기" 버튼은 없다.

/** 잡아당겼을 때 끌려오는 최대 거리 — "아주 약간". */
private val PULL_MAX = 16.dp

/** 고무줄 강도 — 이만큼 끌면 [PULL_MAX] 의 약 63% 만큼 따라온다(클수록 덜 버틴다). */
private val PULL_SOFT = 70.dp

/**
 * 유튜브 로고 비율의 재생 아이콘 — 둥근 몸통(21×15, 반경 4.6) + 가운데 재생 삼각형 구멍.
 * Material `SmartDisplay` 는 모서리가 각져 유튜브 느낌이 안 나서 직접 그렸다(iOS `DiaryLockViews` 와 같은 좌표).
 */
private val PlayLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "StaryPlayLogo",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(6.1f, 4.5f)
            lineTo(17.9f, 4.5f)
            arcTo(4.6f, 4.6f, 0f, false, true, 22.5f, 9.1f)
            lineTo(22.5f, 14.9f)
            arcTo(4.6f, 4.6f, 0f, false, true, 17.9f, 19.5f)
            lineTo(6.1f, 19.5f)
            arcTo(4.6f, 4.6f, 0f, false, true, 1.5f, 14.9f)
            lineTo(1.5f, 9.1f)
            arcTo(4.6f, 4.6f, 0f, false, true, 6.1f, 4.5f)
            close()
            // 재생 삼각형 — EvenOdd 라 몸통에서 뚫린다.
            moveTo(9.9f, 8.6f)
            lineTo(15.8f, 12f)
            lineTo(9.9f, 15.4f)
            close()
        }
    }.build()
}

/** 파편 무늬 시드 — 게시물마다 다른 무늬, 같은 게시물은 항상 같은 무늬. */
private fun lockSeed(diaryId: String, slot: Int): Int = (diaryId.hashCode() and 0x3FF) * 2 + slot

/**
 * 잠긴 글의 히어로(4:3) — **미디어가 있는 글에서만** 쓴다.
 *
 * 미디어 로딩 플레이스홀더(loading_dipper, 북두칠성) 위에 **작은 캡션 한 줄**만 — "이 사진/영상은 잠겨 있어요".
 * (2026-09-22 피드백: 가운데 크리스탈 자물쇠가 예쁘지 않다 → 삭제, 어둡게 덮던 판도 삭제.)
 * - 색: 플레이스홀더 별자리 선의 청보라(선 중심 ≈ #4E5C80, 글로우 ≈ #797F9F)를 글자로 읽힐 만큼 밝힌 #AEBBDF
 *   + 같은 색 옅은 번짐 — 별자리 선이 글씨로 이어진 느낌.
 * - 위치: 우하단, 작성자·날짜 줄 바로 위. 별자리는 영상의 가로 14~88% · 세로 23~60% 에 있어 겹치지 않는다.
 *
 * ⚠️ 원본 미디어 URL 을 **로드조차 하지 않는다**. 흐림/덮개로 가리기만 하면 Coil 캐시·전체화면
 * 뷰어·스크린샷으로 새어나간다.
 */
@Composable
internal fun LockedHero(isVideo: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        // loaded=false 고정 → 콘텐츠 없이 플레이스홀더만.
        MediaLoadingFrame(loaded = false, modifier = Modifier.fillMaxSize()) {}
        Text(
            stringResource(if (isVideo) R.string.detail_locked_video else R.string.detail_locked_photo),
            style = TextStyle(
                color = LOCK_CAPTION_COLOR,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
                shadow = Shadow(color = LOCK_CAPTION_GLOW, blurRadius = 14f),
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 아래 20dp(헤더 오버레이 여백) + 작성자 줄 ≈ 20dp + 간격 12dp.
                .padding(end = 20.dp, bottom = 52.dp),
        )
    }
}

/** 별자리 선의 청보라를 글자용으로 밝힌 색 / 그 번짐. */
private val LOCK_CAPTION_COLOR = Color(0xE6AEBBDF)
private val LOCK_CAPTION_GLOW = Color(0x997F93CC)

/**
 * 잠긴 글의 본문 자리 — 크리스탈 재생 로고 + 여는 방법 + 현재 위치로부터의 거리.
 * 재생 로고를 탭하면 보상형 광고([watchAdToUnlock]).
 *
 * 카드(배경+테두리) 대신 **좌상단·우하단 십자 코너**([cornerCrossFrame])로 영역만 암시한다(2026-09-22 레퍼런스 재해석).
 */
@Composable
internal fun LockedContentCard(
    accent: Color,
    diaryId: String,
    locationKnown: Boolean,
    distance: Float,
) {
    val context = LocalContext.current
    val ads = UnityAdsManager
    // 화면에 들어온 순간 미리 로드 — 아이콘을 눌렀을 때 기다림이 없다.
    LaunchedEffect(ads.initialized) { ads.preload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .cornerCrossFrame(lerp(accent, Color.White, 0.18f))
            // 위쪽은 아이콘 터치 영역(후광 여백)이 이미 넉넉해서 얇게.
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CrystalPullIcon(
            icon = PlayLogo,
            color = accent,
            iconSize = 54.dp,
            seed = lockSeed(diaryId, 1),
            contentDescription = stringResource(R.string.detail_watch_ad),
            onTap = { watchAdToUnlock(context, diaryId) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.detail_locked_title, StaryConfig.DIARY_OPEN_RADIUS_M.toInt()),
            fontSize = 14.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.94f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 현재 위치로부터의 거리 — 위치를 아직 못 잡았으면 "확인 중". 숫자는 고정폭(tnum)이라 갱신돼도 흔들리지 않는다.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LocationOn, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                if (!locationKnown) stringResource(R.string.detail_locating)
                else stringResource(R.string.detail_locked_distance, formatDistanceM(distance)),
                style = TextStyle(
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }
}

/**
 * 십자 코너 프레임 — **좌상단·우하단 두 모서리에만** 가는 십자선(천체 관측 레티클 / 인쇄 재단선 느낌).
 *
 * 레퍼런스(렌즈 플레어 + 큰 후광)의 "AI 느낌"을 덜어내려고:
 * - 선은 헤어라인(0.75dp)만, 교차점에서 **안쪽 팔은 길게 서서히 사라지고**(가로 64dp · 세로 40dp) 바깥 팔은 7dp 로 짧게.
 * - 교차점엔 번짐 대신 작은 점 하나(+ 반경 6dp 의 아주 옅은 광) — 빛나는 별이 아니라 "눈금의 기준점".
 * - 대각 두 모서리만 → 네 모서리를 다 두르는 카드보다 비워 둔 느낌(여백이 레이아웃을 만든다).
 * 바깥 팔은 요소 경계 밖으로 7dp 나가므로 부모가 자르지 않는 곳에서 쓴다(DetailScreen 본문 좌우 여백 20dp 안).
 * iOS `DiaryLockViews.swift` `CornerCrossFrame` 과 같은 수치.
 */
private fun Modifier.cornerCrossFrame(color: Color): Modifier = drawBehind {
    val stroke = 0.75.dp.toPx().coerceAtLeast(1f)
    val armH = 64.dp.toPx()
    val armV = 40.dp.toPx()
    val stub = 7.dp.toPx()

    fun fadingLine(from: Offset, to: Offset, alpha: Float) {
        drawLine(
            brush = Brush.linearGradient(
                listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                start = from, end = to,
            ),
            start = from, end = to, strokeWidth = stroke,
        )
    }

    /** [p] 에 십자 하나 — [sx]/[sy] 는 요소 안쪽 방향(+1/−1). */
    fun cross(p: Offset, sx: Float, sy: Float) {
        fadingLine(p, Offset(p.x + sx * armH, p.y), 0.70f)   // 안쪽 가로(길게)
        fadingLine(p, Offset(p.x, p.y + sy * armV), 0.70f)   // 안쪽 세로
        fadingLine(p, Offset(p.x - sx * stub, p.y), 0.45f)   // 바깥 가로(짧게)
        fadingLine(p, Offset(p.x, p.y - sy * stub), 0.45f)   // 바깥 세로
        val glowR = 6.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = p, radius = glowR),
            radius = glowR, center = p,
        )
        drawCircle(color = lerp(color, Color.White, 0.45f), radius = 1.3.dp.toPx(), center = p)
    }

    cross(Offset(0f, 0f), 1f, 1f)
    cross(Offset(size.width, size.height), -1f, -1f)
}

/**
 * 크리스탈 파편으로 채운 아이콘(프로필 부유 아이콘과 같은 [bakeCrystalIcon] 재질) — **제자리 고정 + 고무줄**.
 *
 * - 평소엔 움직이지 않는다(뒤 후광만 천천히 숨쉬듯 밝아졌다 어두워짐 — 누를 수 있다는 힌트).
 * - 잡아당기면 손가락을 따라오되 버틴다: 끌린 거리 d 에 대해 `PULL_MAX·(1 − e^(−d/PULL_SOFT))` 만큼만
 *   이동(최대 16dp) + 당긴 쪽으로 살짝 기울어짐.
 * - 놓으면 스프링으로 튕기듯 제자리.
 * - 터치 슬롭 안에서 떼면 **탭** → [onTap](광고). 드래그로 판정되면 탭이 아니다.
 * - 제스처를 여기서 소비하므로 아이콘 위에서 시작한 드래그는 화면 스크롤로 넘어가지 않는다.
 */
@Composable
private fun CrystalPullIcon(
    icon: ImageVector,
    color: Color,
    iconSize: Dp,
    seed: Int,
    contentDescription: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val painter = rememberVectorPainter(icon)
    // 잡으면 1.06배로 커지므로 1.5배 해상도로 굽는다. 무늬는 정적 → 1회만.
    val bakePx = with(density) { (iconSize * 1.5f).roundToPx() }.coerceIn(48, 512)
    val crystal = remember(painter, color, seed, bakePx) {
        bakeCrystalIcon(painter, color, seed = seed, sizePx = bakePx, layoutDirection = layoutDirection)
    }
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val press = remember { Animatable(1f) }
    val latestOnTap by rememberUpdatedState(onTap)
    val maxPullPx = with(density) { PULL_MAX.toPx() }
    val softPx = with(density) { PULL_SOFT.toPx() }

    val glow by rememberInfiniteTransition(label = "crystal_lock_glow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crystal_lock_glow_alpha",
    )

    Box(
        modifier = modifier
            .size(iconSize * 1.7f)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick { latestOnTap(); true }
            }
            .pointerInput(maxPullPx, softPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scope.launch { press.animateTo(1.06f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.position - down.position
                        if (!dragged && delta.getDistance() > viewConfiguration.touchSlop) dragged = true
                        if (dragged) {
                            val target = rubberBand(delta, maxPullPx, softPx)
                            scope.launch { pull.snapTo(target) }
                        }
                        change.consume()
                    }
                    scope.launch { press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
                    // 놓으면 튕기듯 제자리 — 낮은 감쇠로 한두 번 출렁인다.
                    scope.launch { pull.animateTo(Offset.Zero, spring(dampingRatio = 0.38f, stiffness = 380f)) }
                    if (!dragged) {
                        Haptics.light()
                        latestOnTap()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 뒤 후광 — 아이콘을 따라 절반만 움직여 깊이감.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = pull.value
            translate(p.x * 0.5f, p.y * 0.5f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.30f * glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                )
            }
        }
        Image(
            bitmap = crystal,
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    val p = pull.value
                    translationX = p.x
                    translationY = p.y
                    scaleX = press.value
                    scaleY = press.value
                    rotationZ = (p.x / maxPullPx) * 6f
                },
        )
    }
}

/** 고무줄 — 끈 거리가 길수록 덜 따라온다(상한 [maxPx]). */
private fun rubberBand(delta: Offset, maxPx: Float, softPx: Float): Offset {
    val len = delta.getDistance()
    if (len <= 0f) return Offset.Zero
    val pulled = maxPx * (1f - exp(-len / softPx))
    return delta * (pulled / len)
}

/**
 * 보상형 광고를 보여주고, 끝까지 보면 이 게시물을 **영구 해금**한다([DiaryUnlockStore]).
 * 아직 로드 전이면 "광고를 불러오는 중이에요" 후 도착 즉시 재생([UnityAdsManager.showRewardedWhenReady]).
 * 키 없음/로드 실패/시간 초과/Activity 없음 → "지금은 광고를 불러올 수 없어요".
 * 디버그 빌드에서 원인이 **비딩 전용 Placement** 설정이면 그 사실을 그대로 알려 준다(릴리스엔 일반 안내만).
 */
internal fun watchAdToUnlock(context: Context, diaryId: String) {
    val ads = UnityAdsManager
    if (ads.showing) return
    val activity = context.findHostActivity()
    if (activity == null) {
        StaryToast.show(context.getString(R.string.detail_ad_unavailable))
        return
    }
    ads.showRewardedWhenReady(
        activity = activity,
        onWaiting = { StaryToast.show(context.getString(R.string.detail_ad_loading)) },
        onUnavailable = {
            StaryToast.show(
                if (BuildConfig.DEBUG && ads.biddingOnlyPlacement)
                    "[개발용] '${ads.rewardedPlacementId}' 는 비딩 전용 Placement 라 로드가 안 돼요 — " +
                        "Unity 대시보드에서 일반 보상형 Placement 를 만들어 secrets 에 넣어 주세요"
                else context.getString(R.string.detail_ad_unavailable)
            )
        },
        onResult = { rewarded ->
            if (rewarded) {
                DiaryUnlockStore.unlock(context, diaryId)
                Haptics.celebrate()
                StaryToast.show(context.getString(R.string.detail_ad_unlocked))
            } else {
                StaryToast.show(context.getString(R.string.detail_ad_not_finished))
            }
        },
    )
}

/** 거리 표기 — 1km 이상이면 소수 1자리 km, 그 미만이면 정수 m. */
private fun formatDistanceM(meters: Float): String =
    if (meters >= 1000f) String.format(Locale.getDefault(), "%.1fkm", meters / 1000f)
    else "${meters.toInt()}m"

/** ContextWrapper 체인을 거슬러 올라가 Activity 를 찾는다(광고 show 에 Activity 가 필요). */
private fun Context.findHostActivity(): android.app.Activity? {
    var c: Context = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
