package com.chaminwoo.stary.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 반응형 UI 스케일 — **작은 폰/태블릿에서 레이아웃·폰트가 깨지지 않게** UI 전체를 비율로 축소/확대한다.
 *
 * ## 왜 이 방식인가
 * 이 앱의 화면은 `.dp`/`.sp` 리터럴 약 1000개가 **Galaxy S22+ 기준으로 픽셀 단위 튜닝**되어 있다
 * (커스텀 Canvas 별/파티클/카드 배치가 많아 값 하나하나가 디자인이다). 리터럴을 전부 반응형 헬퍼로
 * 치환하는 건 비현실적이고 회귀 위험도 크다. 대신 **[LocalDensity] 를 한 곳에서 스케일**하면
 * `dp → px`, `sp → px` 변환이 동시에 배율을 타므로 **디자인 비율을 그대로 둔 채 통째로** 커지고 작아진다.
 * (`sp → px = value × density × fontScale` 이라 density 배율이 폰트에도 그대로 적용된다.)
 *
 * ## 기준 밴드(neutral band) — S22+ 는 절대 건드리지 않는다
 * 기준 기기의 정확한 dp 는 사용자의 "화면 크기(display size)" 설정에 따라 360/384/393 등으로 달라진다.
 * 그래서 특정 값을 기준선으로 못 박지 않고 **일반 폰이 들어가는 구간([NEUTRAL_MIN_WIDTH_DP]~
 * [NEUTRAL_MAX_WIDTH_DP], [NEUTRAL_MIN_HEIGHT_DP]~[NEUTRAL_MAX_HEIGHT_DP])은 배율 1.0** 으로 고정한다.
 * → 기존 S22+ 디자인은 **비트 단위로 그대로**, 그 바깥(작은 폰/태블릿)만 조정된다.
 *
 * 폭·높이 배율 중 **작은 쪽**을 쓴다 — 한 축이라도 좁으면 그 축 기준으로 줄여야 잘리지 않는다.
 *
 * ⚠️ 이 스케일이 적용된 뒤에는 `LocalConfiguration.screenWidthDp` 같은 **raw dp 를 그대로 쓰면 안 된다**
 * (Configuration 은 배율을 모르는 원본 값이라 좌표계가 섞인다). 화면 크기가 필요하면 [LocalScreenSize] 를 쓸 것.
 * 반대로 MapLibre 마커처럼 **실제 화면 px** 로 계산해야 하는 코드는 `resources.displayMetrics.density`
 * (raw)를 그대로 유지해야 한다 — 지도는 Compose 좌표계 밖이다.
 */
object StaryResponsive {
    /** 이 구간의 폭(dp)은 배율 1.0 — 요즘 폰 대부분(작게 설정한 S22+ 360 ~ 큰 폰 420)이 여기 들어온다. */
    const val NEUTRAL_MIN_WIDTH_DP = 360f
    const val NEUTRAL_MAX_WIDTH_DP = 420f

    /** 이 구간의 높이(dp)는 배율 1.0. 700 미만은 세로가 짧아 잘릴 수 있어 축소한다. */
    const val NEUTRAL_MIN_HEIGHT_DP = 700f
    const val NEUTRAL_MAX_HEIGHT_DP = 900f

    /**
     * 밴드 위쪽(태블릿/폴더블) 확대 계수. 화면이 넓어진 만큼 그대로 키우면(1.0) 글씨가 우스꽝스럽게
     * 커지므로 초과분의 35% 만 반영해 "조금 크고 여백 있는" 배치가 되게 한다.
     */
    const val TABLET_GAIN = 0.35f

    const val MIN_SCALE = 0.75f
    const val MAX_SCALE = 1.25f

    /**
     * 콘텐츠(지도 제외 화면) 최대 폭. 태블릿에서 배율만 키우면 `fillMaxWidth` 레이아웃이 그대로
     * 늘어나 카드가 과도하게 넓어지고 행 양끝이 벌어진다 → 폭을 여기서 끊고 가운데 정렬한다.
     * 일반 폰은 배율 적용 후에도 이 값보다 좁아 **아무 영향이 없다**(상한이 걸리지 않음).
     * 지도(MainListScreen)는 이 상한 밖에서 렌더되어 계속 화면 전체를 쓴다.
     */
    const val MAX_CONTENT_WIDTH_DP = 480f

    /**
     * 시스템 글꼴 크기(접근성) 상한. Android 14+ 는 200% 까지 올릴 수 있어 그대로 두면 이 앱처럼
     * 고정 높이 카드가 많은 레이아웃은 글자가 잘린다. 배율 자체는 존중하되 상한만 둔다.
     * (더 키우고 싶으면 이 값만 올리면 된다 — 대신 카드 높이 재검토 필요.)
     */
    const val MAX_FONT_SCALE = 1.15f

    /** 한 축의 배율 — [lo]..[hi] 밴드 안이면 1.0, 아래면 비례 축소, 위면 [TABLET_GAIN] 만큼 완만히 확대. */
    private fun axisScale(value: Float, lo: Float, hi: Float): Float = when {
        value < lo -> value / lo
        value > hi -> 1f + (value / hi - 1f) * TABLET_GAIN
        else -> 1f
    }

    /** 화면 크기(raw dp)에 대한 UI 배율. 폭·높이 중 더 빡빡한 쪽을 따른다. */
    fun scaleFor(widthDp: Float, heightDp: Float): Float {
        if (widthDp <= 0f || heightDp <= 0f) return 1f
        val w = axisScale(widthDp, NEUTRAL_MIN_WIDTH_DP, NEUTRAL_MAX_WIDTH_DP)
        val h = axisScale(heightDp, NEUTRAL_MIN_HEIGHT_DP, NEUTRAL_MAX_HEIGHT_DP)
        return min(w, h).coerceIn(MIN_SCALE, MAX_SCALE)
    }
}

/** 현재 UI 배율(1.0 = 기준 폰). 지도 등 Compose 밖 px 계산에 배율을 반영해야 할 때 참고. */
val LocalUiScale = compositionLocalOf { 1f }

/**
 * **배율이 적용된 좌표계 기준** 화면 크기. `LocalConfiguration.screenWidthDp`(raw) 대신 이걸 쓴다.
 * 밴드 안에서는 실제 화면 dp 와 같고, 축소/확대가 걸리면 "기준 폰으로 환산한" 크기가 된다.
 */
val LocalScreenSize = compositionLocalOf { DpSize(384.dp, 832.dp) } // 기본값 = 기준 폰(실제 값은 테마가 주입)

/**
 * 실제로 콘텐츠가 그려지는 폭 — `min(화면 폭, `[StaryResponsive.MAX_CONTENT_WIDTH_DP]`)`.
 * 화면 폭 기준으로 열 수·카드 배치를 계산하는 곳은 [LocalScreenSize] 대신 이걸 써야
 * 태블릿에서 폭 상한이 걸렸을 때도 계산이 실제 컨테이너와 어긋나지 않는다.
 */
@Composable
fun staryContentWidth(): androidx.compose.ui.unit.Dp =
    LocalScreenSize.current.width.coerceAtMost(StaryResponsive.MAX_CONTENT_WIDTH_DP.dp)

/**
 * [LocalDensity] 를 화면 크기에 맞춰 스케일하고 [LocalUiScale]/[LocalScreenSize] 를 제공한다.
 * [StaryTheme] 안에서만 호출 — 앱 전체를 한 번만 감싸야 배율이 중첩되지 않는다.
 */
@Composable
internal fun ProvideResponsiveUi(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.toFloat()
    val heightDp = configuration.screenHeightDp.toFloat()

    val scale = remember(widthDp, heightDp) { StaryResponsive.scaleFor(widthDp, heightDp) }
    val density = remember(base.density, base.fontScale, scale) {
        Density(
            // dp·sp 변환이 함께 배율을 타 UI 전체가 같은 비율로 커지고 작아진다.
            density = base.density * scale,
            fontScale = base.fontScale.coerceAtMost(StaryResponsive.MAX_FONT_SCALE),
        )
    }
    val screenSize = remember(widthDp, heightDp, scale) {
        DpSize((widthDp / scale).dp, (heightDp / scale).dp)
    }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalUiScale provides scale,
        LocalScreenSize provides screenSize,
        content = content,
    )
}
