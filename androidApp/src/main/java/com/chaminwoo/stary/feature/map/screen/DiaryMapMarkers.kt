package com.chaminwoo.stary.feature.map.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.core.util.MapUiState
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.sin
import android.graphics.Color as AndroidColor
import org.maplibre.android.geometry.LatLng as MlLatLng

internal const val DEFAULT_ZOOM = 15.0
internal const val CURRENT_SOURCE = "current-location"
internal const val DIARY_SOURCE = "diaries"
internal const val STAR_PARTICLE_SOURCE = "star-particles"
internal const val PARTICLE_ICON_ID = "star-particle-dot"
internal const val CONSTELLATION_SOURCE = "constellation-lines"
internal const val CONSTELLATION_LAYER = "constellation-layer"
internal const val CONSTELLATION_GLOW_LAYER = "constellation-glow-layer"
internal const val CONSTELLATION_HALO_LAYER = "constellation-halo-layer"
// 길 위를 흐르는 빛(지도 시안 ground.js 의 FS_FLOW) — 머리가 빛나는 작은 혜성이 큰길을 따라 흐른다. 금빛은 앞으로, 장밋빛은 거꾸로.
// 한 혜성 = 같은 머리 위치에서 끝나는 점선 4겹(번짐 · 꼬리 · 몸통 · 머리, 짧을수록 밝다) — maplibre_style.json 의 flow-{줄기}-{겹} 선 레이어.
// 점선은 길 선을 따라가므로 곡선에서도 길 밖으로 안 나간다(2026-10-28: 심볼을 icon-offset 으로 밀던 방식은 곡선에서 튀어나갔다).
// 루프는 dasharray 위상만 흘린다(칸 단위로 양자화 — 금빛/장밋빛이 같은 점선 무늬 집합을 나눠 쓴다).
// ⚠️ 폭 배율 · 길이(칸)는 gen_style.py FLOW_PARTS · iOS `StyleFx.flowParts` 와 같아야 한다.
internal class FlowPart(val name: String, val k: Float, val len: Int)
internal val FLOW_PARTS = listOf(
    FlowPart("glow", 4.5f, 8), FlowPart("tail", 1.2f, 24), FlowPart("body", 1.6f, 10), FlowPart("head", 2.6f, 3),
)
/** 흐름 줄기 — 이름, 거꾸로 흐르는지, 한 칸 주기(초) = 한 바퀴(혜성 간격)를 도는 시간. */
internal class FlowStream(val name: String, val reverse: Boolean, val period: Float)
internal val FLOW_STREAMS = listOf(FlowStream("gold", false, 8f), FlowStream("rose", true, 11f))
/** 한 바퀴 칸 수 · 한 칸 길이(px, 줌 14 굵기 기준) — 96 × 2.5 = 240px 마다 혜성 하나. */
internal const val FLOW_STEPS = 96
internal const val FLOW_STEP_PX = 2.5f
internal fun flowLayerId(stream: FlowStream, part: FlowPart) = "flow-${stream.name}-${part.name}"

/**
 * 머리가 [head] 칸에 있고 길이가 [len] 칸인 점선(선 폭 단위 = px / [k]). 배열 합 = 한 바퀴라 타일 경계에서 이어진다.
 * 길이 0 의 대시는 butt 끝이라 그려지지 않는다. gen_style.py `flow_dash` · iOS `StyleFx.flowDash` 와 같은 식.
 */
internal fun flowDashArray(head: Int, len: Int, k: Float): Array<Float> {
    val n = FLOW_STEPS
    val u = FLOW_STEP_PX / k
    val s0 = ((head - len) % n + n) % n
    return if (s0 + len <= n) arrayOf(0f, s0 * u, len * u, (n - s0 - len) * u)
    else arrayOf((s0 + len - n) * u, (n - len) * u, (n - s0) * u, 0f)
}
internal const val CONSTELLATION_HALO_OPACITY = 0.18f
internal const val CONSTELLATION_GLOW_OPACITY = 0.42f
internal const val CONSTELLATION_LINE_OPACITY = 0.95f
/** 별자리: 각 별을 화면상 가장 가까운 별 몇 개와 연결. */
internal const val CONSTELLATION_NEIGHBORS = 2

// ── 별자리 "선 긋기" 연출(2026-09-25, 광고 레퍼런스) ──────────────────────────────
// 켜면 선이 한꺼번에 뜨지 않고 한 별에서 다음 별로 **뻗어 나간다**(화면 중앙에 가까운 별에서 출발,
// 그래프 거리 순 = 빛이 별자리를 타고 흐르는 모양). 끌 때는 예전처럼 레이어 페이드 아웃.
/** 선이 뻗는 기본 속도(dp/초). 네트워크가 크면 [CONSTELLATION_DRAW_MAX_MS] 안에 끝나도록 빨라진다. */
internal const val CONSTELLATION_DRAW_DP_PER_SEC = 320f
/** 전체 선 긋기 상한(ms) — 별이 아무리 많아도 이 시간 안에 다 그어진다. */
internal const val CONSTELLATION_DRAW_MAX_MS = 1800f
/** 줌/이동으로 빠지는 선이 되감기는 시간(ms). */
internal const val CONSTELLATION_RETRACT_MS = 240f
/** 선이 별에 닿는 순간 번지는 도착 플래시 — 지속(ms)/최대 반경(dp, MapLibre circle-radius 단위). */
internal const val CONSTELLATION_FLASH_MS = 560f
internal const val CONSTELLATION_FLASH_RADIUS_DP = 7.5f
/** 선 끝(뻗어 가는 머리) 빛점 + 도착 플래시 소스/레이어. */
internal const val CONSTELLATION_FX_SOURCE = "constellation-fx"
internal const val CONSTELLATION_FLASH_LAYER = "constellation-flash-layer"
internal const val CONSTELLATION_TIP_HALO_LAYER = "constellation-tip-halo-layer"
internal const val CONSTELLATION_TIP_LAYER = "constellation-tip-layer"

// ── 필터 전환 "빛의 고리"(포털) 등장 — 2026-10-14, 레퍼런스: references/filtering.mp4 + 리그 오브 레전드 "창공 파이크" 펜타킬 ──
// 내 위치에 검은 구체가 생기고, 거기서 **울퉁불퉁 출렁이는 거품(포털)**이 지도 위로 퍼진다. 거품 안쪽은 필터 색의 성운
// (구름 얼룩·어두운 반점·잔별)이 되어 화면 전체를 덮었다가 은은한 필터 빛으로 가라앉는다. 테두리는 안쪽으로 번지는 밝은 빛 + 얇은 선.
// 링(거품 테두리)이 닿는 별은 차임과 함께 잠깐 커지고 밝아졌다 제자리로(화면 밖 별도 소리). **링은 별과 관계없이 지금 보고 있는
// 화면 전체를 지나갈 때까지 멈추지 않는다.** 지도 위 좌표로 그려 연출 중 지도를 움직이면 함께 움직인다.
// iOS `MapStarReveal.swift` 의 `StarRevealFx` 와 같은 값(값 drift 금지).
/**
 * 링이 내 위치 → 화면의 가장 먼 모서리까지 **일정한 속도로** 퍼지는 시간(ms).
 * 그 뒤로는 점점 빨라지며([REVEAL_FAR_TAU_MS]) 화면 밖 먼 별·더 넓은 지도까지 퍼진다.
 */
internal const val REVEAL_WAVE_MS = 900L
/** 화면을 다 쓸고 난 뒤 링이 빨라지는 정도(ms, 지수 시간 상수) — 작을수록 빨리 멀리 간다. */
internal const val REVEAL_FAR_TAU_MS = 450.0
/** 링이 별에 닿는 시각의 상한(ms) — 지구 반대편 별도 이 안에 닿는다(실제로는 거의 안 걸리는 안전 상한). */
internal const val REVEAL_MAX_MS = 8000L
/** 링이 지금 화면을 다 지나간 뒤 옅어지는 시간(ms) — 이때 링은 이미 화면 밖이다. */
internal const val REVEAL_RING_FADE_MS = 300L
/** 화면을 다 지나가지 못해도 이 시각엔 끝낸다(ms) — 정상적으로는 닿지 않는 안전 상한. */
internal const val REVEAL_RING_MAX_MS = 12000L
/** 링이 화면을 다 지나간 뒤 포털 성운이 은은한 필터 빛으로 가라앉는 시간(ms) — 레퍼런스의 "몇 초간 덮였다 걷힘". */
internal const val REVEAL_PORTAL_DECAY_MS = 1600L
/** 지도 위 링을 화면에 투영할 때 쓰는 꼭짓점 수. */
internal const val REVEAL_RING_SEGMENTS = 180
/**
 * 거품의 출렁임 — 반지름 대비 비율. 반지름(각도 θ, 시각 t) = R(t) × (1 + 이 값 × [RingWobble.at]).
 * 낮은 주파수(2·3·5) 위주라 정원이 아니라 울퉁불퉁한 덩어리가 부풀듯 퍼진다.
 */
internal const val REVEAL_RING_WOBBLE = 0.09
/** 링 선 굵기(dp) / 테두리 안쪽으로 번지는 빛 폭(dp) — 선 하나 + 안쪽 번짐(바깥으로는 번지지 않는다). */
internal const val REVEAL_RING_STROKE_DP = 1.4f
internal const val REVEAL_RIM_GLOW_DP = 22f
/** 시작점의 검은 구체 — 반지름(dp) / 생겼다 사라지는 시간(ms). */
internal const val REVEAL_ORB_DP = 15f
internal const val REVEAL_ORB_MS = 700L

/**
 * 거품 출렁임 모양 — 각도 θ(라디안, 동쪽 0·반시계)와 시각(초)에 따른 −1..1 값.
 * 주파수 2·3·5(덩어리) + 11(잔물결)의 사인을 섞고 시간에 따라 서로 다른 방향으로 흘린다. 위상은 연출마다 무작위.
 * iOS `RingWobble`(MapStarReveal.swift)과 **같은 식**.
 */
internal class RingWobble(seed: Int) {
    private val p1: Double
    private val p2: Double
    private val p3: Double
    private val p4: Double
    init {
        val r = kotlin.random.Random(seed)
        p1 = r.nextDouble() * 2 * Math.PI
        p2 = r.nextDouble() * 2 * Math.PI
        p3 = r.nextDouble() * 2 * Math.PI
        p4 = r.nextDouble() * 2 * Math.PI
    }

    fun at(theta: Double, tSec: Double): Double =
        0.45 * kotlin.math.sin(2 * theta + p1 + 0.7 * tSec) +
            0.30 * kotlin.math.sin(3 * theta + p2 - 1.1 * tSec) +
            0.17 * kotlin.math.sin(5 * theta + p3 + 1.6 * tSec) +
            0.08 * kotlin.math.sin(11 * theta + p4 - 2.6 * tSec)
}


/** 시작 후 [tMs] 에 링 반지름(m) — [sweepM] = 내 위치에서 화면의 가장 먼 모서리까지 거리. */
internal fun revealRadiusM(tMs: Double, sweepM: Double): Double {
    val t0 = REVEAL_WAVE_MS.toDouble()
    return if (tMs <= t0) sweepM * tMs / t0
    else sweepM * (1 + REVEAL_FAR_TAU_MS / t0 * (kotlin.math.exp((tMs - t0) / REVEAL_FAR_TAU_MS) - 1))
}

/** 내 위치에서 [distM] 떨어진 별에 링이 닿는 시각(ms) — [revealRadiusM] 의 역함수, [REVEAL_MAX_MS] 상한. */
internal fun revealHitMs(distM: Double, sweepM: Double): Long {
    val t0 = REVEAL_WAVE_MS.toDouble()
    val k = distM / sweepM.coerceAtLeast(1.0)
    val t = if (k <= 1) t0 * k else t0 + REVEAL_FAR_TAU_MS * kotlin.math.ln(1 + (k - 1) * t0 / REVEAL_FAR_TAU_MS)
    return t.toLong().coerceIn(0L, REVEAL_MAX_MS)
}
/** 링이 닿은 별 하나가 생기는 시간(ms) — 크기 0.2 → 1.55 → 1 + 발광. */
internal const val REVEAL_POP_MS = 520L
/** 등장 직전, 이전 필터의 별을 걷어내는 시간(ms). */
internal const val REVEAL_CLEAR_MS = 170L
/** 별 발광 반경 = 화면에 보이는 별 반지름 × 이 배율(하한 [REVEAL_FLASH_MIN_DP]). */
internal const val REVEAL_FLASH_SCALE = 1.9f
internal const val REVEAL_FLASH_MIN_DP = 12f

/** 등장 팝 곡선 — u(0..1) → 크기 배율. 0.2 에서 튀어 올라 1.55 를 찍고(u=0.3) 1 로 돌아온다. */
internal fun revealPopScale(u: Float): Float {
    val x = u.coerceIn(0f, 1f)
    return if (x < 0.3f) {
        val k = 1f - (1f - x / 0.3f).let { it * it * it }
        0.2f + (1.55f - 0.2f) * k
    } else {
        1.55f + (1f - 1.55f) * FastOutSlowInEasing.transform((x - 0.3f) / 0.7f)
    }
}

/** 등장 발광 세기 — u(0..1) → 0..1. 링이 닿자마자 확 밝아졌다가(u=0.18) 천천히 꺼진다. */
internal fun revealFlash(u: Float): Float {
    val x = u.coerceIn(0f, 1f)
    return if (x < 0.18f) x / 0.18f else (1f - (x - 0.18f) / 0.82f).let { it * it }
}

/** 지금 줌에서 별 아이콘의 줌 배율 — [starSizeExpression] 의 zoom stop 과 **같은 값**(6→0.06, 10→0.2, 13→0.5, 15→1). */
internal fun starZoomMult(zoom: Double): Float {
    val z = zoom.toFloat()
    fun seg(z0: Float, v0: Float, z1: Float, v1: Float) = v0 + (v1 - v0) * ((z - z0) / (z1 - z0))
    return when {
        z <= 6f -> 0.06f
        z < 10f -> seg(6f, 0.06f, 10f, 0.2f)
        z < 13f -> seg(10f, 0.2f, 13f, 0.5f)
        z < 15f -> seg(13f, 0.5f, 15f, 1f)
        else -> 1f
    }
}

/** 별 후광(CircleLayer) id — 위상 그룹별(별과 같은 float 적용). */
internal fun auraLayerId(group: Int) = "diary-aura-$group"
/** 바닥 빛 웅덩이 id — 별과 달리 지점 고정 · 땅에 누운 타원이라 부유 시차로 "떠 있음"이 읽힌다. */
internal fun groundLightLayerId(group: Int) = "diary-ground-light-$group"
internal const val GROUND_LIGHT_OPACITY = 0.24f
/**
 * 필터가 걸려 있을 때(2026-10-10 "조금 더 밝게") 보이는 별을 돋보이게 하는 후광 강화 — iOS `StyleFx.highlightGroundBoost` 와 같은 값.
 * 바닥 빛 웅덩이 불투명도만 키운다. (모든 별에 오오라 하한을 깔던 것은 지도에 반투명 원이 여러 개 생겨 2026-10-19 삭제.)
 */
internal const val FILTER_GROUND_BOOST = 1.6f
/** 화면상 클러스터 반경(dp). 이 거리 안에서 겹치는 별은 가장 좋아요 많은 별 하나로 합쳐 표시. */
internal const val CLUSTER_RADIUS_DP = 4f
/** 좋아요 수 → 크기 배율 상한(좋아요 100개에서 3배). */
internal const val LIKES_FOR_MAX_SIZE = 100
internal const val MAX_LIKE_SIZE_MULT = 3f

/** 신규 별(24시간 이내) 판정 창. */
internal const val FRESH_WINDOW_MS = 86_400_000L

/**
 * float/pulse 위상 그룹 수 — ⚠️ iconTranslate 는 레이어 전체 일괄 적용이라,
 * 마커를 id 해시 그룹으로 나눠 레이어별 위상을 달리해야 "따로따로" 부유한다.
 */
internal const val PHASE_GROUPS = 4
internal fun diaryLayerId(group: Int) = "diary-stars-$group"
internal val DIARY_LAYER_IDS = Array(PHASE_GROUPS) { diaryLayerId(it) }
internal fun particleLayerId(group: Int) = "star-particles-$group"

/** 별가루 파티클: 개수/분포 반경/시드(고정 → 항상 같은 배치). */
internal const val PARTICLE_COUNT = 400
internal const val PARTICLE_RADIUS_M = 20_000.0
internal const val PARTICLE_SEED = 42

/** 마커 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
internal const val MARKER_SIDE_PX = 160

/** 개척 퀘스트 대상국 비콘 소스/레이어/아이콘 id. */
internal const val PIONEER_SOURCE = "pioneer-source"
internal const val PIONEER_LAYER = "pioneer-layer"
internal const val PIONEER_ICON_ID = "pioneer-icon"

/**
 * 내 위치 점(초록 원) 크기 — **줌을 풀수록 작아진다**(2026-10-08). (줌, 반지름, 테두리 두께) — 사이는 선형 보간, 양 끝 밖은 끝값 유지.
 * 줌 15(기본 진입 줌) = 예전 고정값(반지름 7 / 테두리 2). iOS `MapStyleEffects` 의 `StyleFx.myLocationStops` 와 같은 값.
 */
internal val MY_LOCATION_STOPS = listOf(
    Triple(2f, 2.0f, 0.8f),
    Triple(6f, 3.0f, 1.0f),
    Triple(10f, 4.5f, 1.4f),
    Triple(13f, 6.0f, 1.8f),
    Triple(15f, 7.0f, 2.0f),
)

/** 3D 글로브 "지구 보기" 버튼 노출 줌(이하로 줌아웃하면 버튼 표시 — 자동 전환 없음) / 지도 최소 줌. */
internal const val GLOBE_BUTTON_ZOOM = 3.0
internal const val MAP_MIN_ZOOM = 2.4
/** 웹메르카토르 타일이 존재하는 위도 한계 — 이 밖은 지도가 없어 빈 공간이 보인다(카메라 클램프 기준). */
internal const val MERCATOR_MAX_LAT = 85.05112877980659
/** 대기 헤이즈 시작 줌 — 이 줌부터 [MAP_MIN_ZOOM] 까지 내려갈수록 파란 대기가 차올라 글로브 장면과 이어진다. */
internal const val HAZE_START_ZOOM = 4.4

/** 기본 카메라 틸트(도) — 줌과 무관하게 항상 살짝 기울여 입체감을 낸다. */
internal const val BASE_TILT_DEG = 40.0

/**
 * 별 기본/근접 크기(iconSize 배율).
 * ⚠️ addImage(bitmap)는 기기 밀도로 나눠 표시 — 화면 크기 ≈ 160/density × iconSize dp.
 */
internal const val STAR_SIZE_FAR = 0.65f
internal const val STAR_SIZE_NEAR = 0.9f

/** 앱 공용 좌표 -> MapLibre 좌표 */
internal fun LatLng.toMl(): MlLatLng = MlLatLng(latitude, longitude)

/** type×color 조합의 스타일 이미지 id */
internal fun starIconId(type: Int, color: Int) = "star-t$type-c$color"

/**
 * 별 마커 비트맵 — 글로우 + 크리스탈 패싯 본체를 Path 로 직접 그린다.
 * ⚠️ PNG 디코드→텍스처 경로는 에뮬레이터에서 대각선 빗금으로 깨져 Path 렌더를 유지할 것.
 */
internal fun starBitmap(type: Int, colorIdx: Int): Bitmap {
    val color = StarStyle.colorOf(colorIdx).toArgb()
    val side = MARKER_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    val starSize = side * 0.78f // 글로우 여백을 위해 본체는 78%
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(type, starSize)).apply {
        offset(offset, offset)
    }

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glowPaint)
    canvas.drawPath(path, glowPaint)

    StarStyle.drawCrystalFill(canvas, type, colorIdx, offset, offset, starSize)
    return out
}

/**
 * 개척 퀘스트 비콘 비트맵(체크리스트 32) — 다이어리 별과 확실히 구분되는 "금색 이중 링 + 스파클".
 * 바깥 글로우 링(blur) + 얇은 실선 링 + 중앙 8꼭지 금색 스파클.
 */
internal fun pioneerBeaconBitmap(): Bitmap {
    val side = MARKER_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cx = side / 2f
    val cy = side / 2f
    val gold = AndroidColor.parseColor("#FFD86F")

    canvas.drawCircle(cx, cy, side * 0.40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = side * 0.045f
        maskFilter = android.graphics.BlurMaskFilter(side * 0.07f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        alpha = 190
    })
    canvas.drawCircle(cx, cy, side * 0.40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = side * 0.015f
    })
    val starSize = side * 0.52f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(3, starSize)).apply { offset(offset, offset) }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        maskFilter = android.graphics.BlurMaskFilter(9f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glow)
    canvas.drawPath(path, glow)
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
    return out
}

/**
 * 별 iconSize: 줌 보간 × near 확대 × pulse(루프에서 갱신).
 * ⚠️ MapLibre 는 ["zoom"] 이 최상위 interpolate 입력일 때만 연속 평가한다 —
 * sizeMult/near 분기는 반드시 각 stop 출력 "안"에 넣을 것(중첩 시 줌 보간이 멈춘 듯 보인다).
 */
internal fun starSizeExpression(pulse: Float, scale: Float = 1f): Expression {
    fun sized(zoomMult: Float): Expression {
        val near = Expression.product(
            Expression.literal(STAR_SIZE_NEAR * pulse * zoomMult * scale), Expression.get("sizeMult")
        )
        val far = Expression.product(
            Expression.literal(STAR_SIZE_FAR * zoomMult * scale), Expression.get("sizeMult")
        )
        return Expression.switchCase(
            Expression.eq(Expression.get("near"), Expression.literal(true)),
            near, far
        )
    }
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, sized(0.06f)),
        Expression.stop(10f, sized(0.2f)),
        Expression.stop(13f, sized(0.5f)),
        Expression.stop(15f, sized(1f)),
    )
}

/**
 * 별 본체 지름(dp) × [k] — 별 아이콘과 같은 줌 곡선. 본체는 비트맵의 78%([starBitmap]), 화면 dp = px/density.
 * 시안(app.js)의 후광 · 바닥 빛은 별 크기의 배수라 이 값으로 반경을 잡는다(blur 1 원 ≈ 스프라이트 반경의 0.55).
 */
internal fun starBodyExpression(k: Float, density: Float): Expression =
    starSizeExpression(1f, k * MARKER_SIDE_PX * 0.78f / density)

/** 모든 별의 후광 — 시안 2.2×크기 · α.15 스프라이트(blur 원으로 본체 지름 × 1.21). */
internal const val STAR_HALO_K = 1.21f
internal const val STAR_HALO_OPACITY = 0.15f
/** 바닥 빛 웅덩이 — 시안 rx 1.9×크기 · α.24 땅에 누운 타원(본체 지름 × 1.05, pitch-alignment map). */
internal const val GROUND_LIGHT_K = 1.05f

/**
 * 별빛 공방 오라 — 오라를 단 사람의 별 둘레(위상 그룹별, 별과 같이 float). 2026-10-22 리디자인.
 *  - glow   : 은은한 빛(모든 패턴의 바탕, 숨 쉬듯 밝기만 변함)
 *  - pulse  : 별에서 천천히 퍼져 나가며 사라지는 고리 하나
 *  - sprite : [StarlightAura] 스프라이트(달 · 반딧불 · 쌍둥이 · 나선 · 토성 고리 · 혜성) — 심볼 레이어가 별 아래에서
 *             icon-rotate(돌기 `slSpin` °/초 · 흔들림 `slWobble` °) · 깜빡임(`slFlicker`)으로 움직인다.
 * 모르는 패턴은 halo(glow 만)로 그린다 — 새 패턴을 DB 에 넣어도 이전 버전은 빛으로만 보인다.
 */
internal fun slAuraGlowLayerId(group: Int) = "sl-aura-glow-$group"
internal fun slAuraPulseLayerId(group: Int) = "sl-aura-pulse-$group"
internal fun slAuraSpriteLayerId(group: Int) = "sl-aura-sprite-$group"

/** 오라 스프라이트 이미지 id — payload 가 바뀌면(DB 수정) 다른 id 가 되어 새로 등록된다. */
internal fun slAuraSpriteImageId(item: com.chaminwoo.stary.feature.starlight.StarlightStore.Item) =
    "sl-aura-img-${item.id}-${item.payload.hashCode()}"

/** 그 사람이 단 오라의 지도 표현. */
internal data class SlAura(
    val pattern: String, // halo · pulse · sprite
    val color: String,
    val color2: String,
    val sprite: String,  // 스프라이트 이미지 id(없으면 "")
    val spin: Float,
    val wobble: Float,
    val flicker: Boolean,
)

/** 공방 오라 반경 — 인기 오오라와 같은 줌 곡선 × [scale] × sizeMult. */
internal fun slAuraRadiusExpression(scale: Float): Expression {
    fun r(base: Float): Expression =
        Expression.product(Expression.literal(base * scale), Expression.get("sizeMult"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, r(2.5f)),
        Expression.stop(10f, r(7f)),
        Expression.stop(13f, r(15f)),
        Expression.stop(15f, r(24f)),
    )
}

/**
 * 그 별에 두른 고리(`diaries/{id}.ring` — 2026-10-26부터 사람이 아니라 별마다) → 지도 표현.
 * 안 둘렀거나 아이템을 모르면 null.
 */
internal fun slAuraOf(ringId: String): SlAura? {
    val item = com.chaminwoo.stary.feature.starlight.StarlightStore.item(ringId) ?: return null
    val raw = item.payload["pattern"] as? String
    val hexes = (item.payload["colors"] as? List<*>).orEmpty().mapNotNull { v ->
        (v as? String)?.trim()?.removePrefix("#")?.takeIf { it.length == 6 && it.toLongOrNull(16) != null }?.let { "#" + it.uppercase() }
    }
    val c1 = hexes.firstOrNull() ?: "#BFD8FF"
    val c2 = hexes.getOrNull(1) ?: c1
    if (raw in com.chaminwoo.stary.feature.starlight.StarlightAura.SPRITE_PATTERNS) {
        val m = com.chaminwoo.stary.feature.starlight.StarlightAura.motion(item.payload)
        return SlAura("sprite", c1, c2, slAuraSpriteImageId(item), m.spinDegPerSec, m.wobbleDeg, m.flicker)
    }
    return SlAura(if (raw == "pulse") "pulse" else "halo", c1, c2, "", 0f, 0f, false)
}


/** 좋아요 수 → 별 크기 배율(1..[MAX_LIKE_SIZE_MULT]). */
internal fun likeSizeMult(likeCount: Int): Float =
    1f + (likeCount.coerceIn(0, LIKES_FOR_MAX_SIZE).toFloat() / LIKES_FOR_MAX_SIZE) * (MAX_LIKE_SIZE_MULT - 1f)

/** 팔레트 색 → "#RRGGBB" (오오라 CircleLayer 색상용). */
internal fun starColorHex(colorIdx: Int): String =
    String.format("#%06X", 0xFFFFFF and StarStyle.colorOf(colorIdx).toArgb())

/** 클러스터링 결과: 대표 별 목록 + (다이어리 id → 그 별이 속한 대표 id) 배정표. */
internal data class ClusterResult(val reps: List<Diary>, val assignment: Map<String, String>)

/**
 * 화면 좌표 기반 클러스터링 — [radiusPx] 안에 겹치는 별을 최다 좋아요 별(대표)로 합친다.
 * 카메라가 멈출 때마다 재계산하고, [ClusterResult.assignment] 로 합쳐짐/펼쳐짐 보간을 추적한다.
 */
internal fun clusterTopLiked(
    map: MapLibreMap,
    valid: List<Diary>,
    radiusPx: Float,
): ClusterResult {
    if (valid.isEmpty()) return ClusterResult(emptyList(), emptyMap())
    val sorted = valid.sortedByDescending { it.likeCount }
    val screen = sorted.map { map.projection.toScreenLocation(MlLatLng(it.latitude, it.longitude)) }
    val assigned = BooleanArray(sorted.size)
    val r2 = radiusPx * radiusPx
    val reps = ArrayList<Diary>()
    val assignment = HashMap<String, String>()
    for (i in sorted.indices) {
        if (assigned[i]) continue
        assigned[i] = true
        reps.add(sorted[i])
        assignment[sorted[i].id] = sorted[i].id
        val pi = screen[i]
        for (j in i + 1 until sorted.size) {
            if (assigned[j]) continue
            val pj = screen[j]
            val dx = pi.x - pj.x; val dy = pi.y - pj.y
            if (dx * dx + dy * dy <= r2) {
                assigned[j] = true
                assignment[sorted[j].id] = sorted[i].id
            }
        }
    }
    return ClusterResult(reps, assignment)
}

/** 합쳐진 다이어리 수 → 대표 별 크기 가산 배율(개수에 비례, 과도하지 않게 상한). */
internal fun clusterSizeBoost(count: Int): Float =
    (1f + (count - 1) * 0.12f).coerceAtMost(2.2f)

/** 별 합치기(30m) 우선순위 — 좋아요 내림차순 → 오래된 순 → id(안정 타이브레이크). 대표 = 1순위. */
internal val MERGE_PRIORITY: Comparator<Diary> =
    compareByDescending<Diary> { it.likeCount }.thenBy { it.createdAt }.thenBy { it.id }

/** 30m 지오 머지 결과: 대표 별 목록 + (대표 id → 우선순위 정렬된 멤버 전체). */
internal data class MergeResult(val reps: List<Diary>, val groups: Map<String, List<Diary>>)

/**
 * 30m 지오 머지 — [StaryConfig.STAR_MERGE_RADIUS_M] 안에 겹치는 다이어리를 우선순위 1위
 * 별(대표)로 합친다(줌 무관). 모양/색은 대표, 크기/밝기는 [mergeSizeMult] 합산.
 */
internal fun mergeByProximity(valid: List<Diary>): MergeResult {
    if (valid.isEmpty()) return MergeResult(emptyList(), emptyMap())
    val sorted = valid.sortedWith(MERGE_PRIORITY)
    val taken = BooleanArray(sorted.size)
    val reps = ArrayList<Diary>()
    val groups = HashMap<String, List<Diary>>()
    for (i in sorted.indices) {
        if (taken[i]) continue
        taken[i] = true
        val anchor = sorted[i]
        val members = ArrayList<Diary>()
        members.add(anchor)
        for (j in i + 1 until sorted.size) {
            if (taken[j]) continue
            val d = sorted[j]
            if (LocationHelper.distanceBetween(anchor.latitude, anchor.longitude, d.latitude, d.longitude)
                <= StaryConfig.STAR_MERGE_RADIUS_M
            ) {
                taken[j] = true
                members.add(d) // sorted 순회라 members 도 우선순위 정렬 상태
            }
        }
        reps.add(anchor)
        groups[anchor.id] = members
    }
    return MergeResult(reps, groups)
}

/** 합쳐진 별 크기/밝기 배율 — 멤버 전체 좋아요 "합산" + 개수 보너스(모든 별 합산 반영 규칙). */
internal fun mergeSizeMult(members: List<Diary>): Float =
    likeSizeMult(members.sumOf { it.likeCount }) * clusterSizeBoost(members.size)

/** 다이어리 → 별 마커 Feature. [lng]/[lat]/[alpha] 는 합쳐짐 보간, [sizeMult] 는 최종 크기 배율. */
internal fun diaryFeature(d: Diary, lng: Double, lat: Double, near: Boolean, alpha: Float, sizeMult: Float): Feature {
    val colorIdx = StarStyle.safeColor(d.starColor)
    return Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
        addStringProperty("id", d.id)
        addBooleanProperty("near", near)
        addNumberProperty("phaseGroup", kotlin.math.abs(d.id.hashCode()) % PHASE_GROUPS)
        // 유리 결 빛(sheen) — 별마다 다른 때 · 다른 빠르기로 지나가게 하는 값(id 해시, 0..1)
        val hh = (d.id.hashCode().toLong() and 0xFFFFFFFFL)
        addNumberProperty("starType", StarStyle.safeType(d.starType))
        addNumberProperty("sheenPh", (hh % 1000L) / 1000f)
        addNumberProperty("sheenRate", 1f / (SHEEN_PERIOD_MIN + (hh / 1000L % 1000L) / 1000f * SHEEN_PERIOD_SPREAD))
        addNumberProperty("sizeMult", sizeMult)
        addStringProperty("auraColor", starColorHex(colorIdx))
        addBooleanProperty("fresh", System.currentTimeMillis() - d.createdAt <= FRESH_WINDOW_MS)
        addNumberProperty("alpha", alpha)
        addStringProperty("icon", starIconId(StarStyle.safeType(d.starType), colorIdx))
        val sl = slAuraOf(d.ring)
        addStringProperty("slAura", sl?.pattern ?: "")
        addStringProperty("slAuraColor", sl?.color ?: "#000000")
        addStringProperty("slAuraColor2", sl?.color2 ?: "#000000")
        addStringProperty("slSprite", sl?.sprite ?: "")
        addNumberProperty("slSpin", sl?.spin ?: 0f)
        addNumberProperty("slWobble", sl?.wobble ?: 0f)
        addNumberProperty("slFlicker", if (sl?.flicker == true) 1 else 0)
    }
}

/** 파티클 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
internal const val PARTICLE_SIDE_PX = 24

/** 별가루 파티클 비트맵 — 흰색 글로우 + 흰색 코어의 작은 점. */
internal fun particleBitmap(): Bitmap {
    val side = PARTICLE_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val c = side / 2f
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        maskFilter = android.graphics.BlurMaskFilter(4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(c, c, side * 0.22f, glowPaint)
    canvas.drawCircle(c, c, side * 0.13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
    return out
}

/**
 * 유리 결 빛(sheen) — 지도 시안 drawSheen: 흰 빛줄기 하나가 별 몸의 **왼쪽 위 → 오른쪽 아래**로 훑고 지나가고,
 * 그 빛을 타고 작은 반짝이 하나가 건너간다. 시안(주기 7~11초, 지나가는 데 주기의 30%)보다 조금 빠르게:
 * 주기 [SHEEN_PERIOD_MIN]~+[SHEEN_PERIOD_SPREAD] 초, 지나가는 시간은 주기의 [SHEEN_WINDOW].
 * 별 레이어 바로 위의 심볼 레이어가 프레임 그림(sheen-t{모양}-{0..N})을 바꿔 끼운다 — 프레임 [SHEEN_FRAMES] 는 빈 그림.
 * iOS `StarMarkerView` 의 sheen 과 같은 값.
 */
internal const val SHEEN_PERIOD_MIN = 5.5f
internal const val SHEEN_PERIOD_SPREAD = 2.5f
internal const val SHEEN_WINDOW = 0.26f
internal const val SHEEN_FRAMES = 14
internal const val SHEEN_SIDE_PX = 64
internal const val SHEEN_OPACITY = 0.75f
internal fun sheenLayerId(group: Int) = "diary-sheen-$group"
internal fun sheenIconId(type: Int, frame: Int) = "sheen-t$type-$frame"

/** 유리 결 빛 한 프레임 — 별 본체([starBitmap] 과 같은 78% 비율) 모양으로 오린 흰 사선 띠 + 띠를 타고 가는 굽은 반짝. */
internal fun sheenBitmap(type: Int, frame: Int): Bitmap {
    val side = SHEEN_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    if (frame >= SHEEN_FRAMES) return out // 빈 그림(지나가는 중이 아닐 때)
    val cv = Canvas(out)
    val q = (frame + 0.5f) / SHEEN_FRAMES // 0..1 진행
    val starSize = side * 0.78f
    val off = (side - starSize) / 2f
    val body = android.graphics.Path(StarStyle.starPath(type, starSize)).apply { offset(off, off) }
    // 시안: 128 캔버스에서 p = -46 + u*220, 띠 = (p-30,p-30)→(p+30,p+30) 의 흰색(가운데 0.95)
    val p = (-46f + q * 220f) / 128f * side
    val hw = 30f / 128f * side
    cv.save()
    cv.clipPath(body)
    cv.drawRect(0f, 0f, side.toFloat(), side.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            p - hw, p - hw, p + hw, p + hw,
            intArrayOf(0x00FFFFFF, 0xF2FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    })
    cv.restore()
    // 빛을 타고 건너가는 반짝(시안: 중심에서 (q-0.5)*0.62*크기, 반지름 0.17*크기*sin)
    val e = sin(q * Math.PI.toFloat())
    val c = side / 2f + (q - 0.5f) * 0.62f * starSize
    val r = 0.17f * starSize * e
    if (r > 0.5f) {
        val k = r * 0.10f
        val sp = android.graphics.Path().apply {
            moveTo(c, c - r)
            quadTo(c + k, c - k, c + r, c)
            quadTo(c + k, c + k, c, c + r)
            quadTo(c - k, c + k, c - r, c)
            quadTo(c - k, c - k, c, c - r)
            close()
        }
        cv.drawCircle(c, c, r * 0.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(c, c, r * 0.9f, (((0.5f * e * 255).toInt()) shl 24) or 0xFFFFFF, 0x00FFFFFF,
                android.graphics.Shader.TileMode.CLAMP)
        })
        cv.drawPath(sp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (((0.95f * e * 255).toInt()) shl 24) or 0xFFFFFF })
    }
    return out
}

/** 유리 결 빛 심볼의 icon-image — 별마다 (T·sheenRate + sheenPh) 주기 위상이 [SHEEN_WINDOW] 안일 때만 프레임 0..N-1, 아니면 빈 그림 N. */
internal fun sheenImageExpression(tSec: Float): Expression {
    val cyc = Expression.mod(
        Expression.sum(Expression.product(Expression.literal(tSec), Expression.get("sheenRate")), Expression.get("sheenPh")),
        Expression.literal(1f),
    )
    val frame = Expression.min(
        Expression.floor(Expression.product(cyc, Expression.literal(SHEEN_FRAMES / SHEEN_WINDOW))),
        Expression.literal(SHEEN_FRAMES),
    )
    return Expression.concat(
        Expression.literal("sheen-t"), Expression.toString(Expression.get("starType")),
        Expression.literal("-"), Expression.toString(frame),
    )
}

/** 유리 결 빛 불투명도 — 지나가는 동안 sin 으로 차올랐다 사라진다(시안 globalAlpha = a·sin(uπ)). */
internal fun sheenOpacityExpression(tSec: Float): Expression {
    val cyc = Expression.mod(
        Expression.sum(Expression.product(Expression.literal(tSec), Expression.get("sheenRate")), Expression.get("sheenPh")),
        Expression.literal(1f),
    )
    val u = Expression.min(Expression.division(cyc, Expression.literal(SHEEN_WINDOW)), Expression.literal(1f))
    return Expression.product(
        Expression.literal(SHEEN_OPACITY), Expression.get("alpha"),
        Expression.sin(Expression.product(u, Expression.literal(Math.PI.toFloat()))),
    )
}

/**
 * 별가루 파티클 FeatureCollection — [center] 기준 반경 [PARTICLE_RADIUS_M] 내 균등 분포.
 * 시드 고정이라 매 실행 같은 배치. 한 번 생성 후 갱신하지 않는다(카메라 이동과 무관).
 */
internal fun starParticleFeatures(center: LatLng): FeatureCollection {
    val rnd = kotlin.random.Random(PARTICLE_SEED)
    val cosLat = kotlin.math.cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)
    val features = (0 until PARTICLE_COUNT).map { i ->
        // sqrt 로 면적 균등 분포
        val r = PARTICLE_RADIUS_M * kotlin.math.sqrt(rnd.nextDouble())
        val theta = rnd.nextDouble() * 2.0 * Math.PI
        val dLat = r * kotlin.math.cos(theta) / 111_320.0
        val dLng = r * kotlin.math.sin(theta) / (111_320.0 * cosLat)
        Feature.fromGeometry(
            Point.fromLngLat(center.longitude + dLng, center.latitude + dLat)
        ).apply {
            addNumberProperty("phase", rnd.nextDouble() * 2.0 * Math.PI)
            addNumberProperty("twinkleSpeed", 0.5 + rnd.nextDouble())
            addNumberProperty("depth", 0.5 + rnd.nextDouble() * 0.5) // 0.5..1.0 크기 배율
            addNumberProperty("phaseGroup", i % PHASE_GROUPS)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/** 파티클 iconSize: 줌 보간 × per-feature depth. 데이터 주도라 1회 설정로 충분. */
internal fun particleSizeExpression(): Expression {
    fun sized(base: Float): Expression =
        Expression.product(Expression.literal(base), Expression.get("depth"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(9f, sized(0f)),
        Expression.stop(12f, sized(0.45f)),
        Expression.stop(15f, sized(0.8f)),
    )
}

/** 파티클 iconOpacity: 저줌 숨김 → [twinkle](루프에서 위상 그룹별 갱신)까지 선형 등장. */
internal fun particleOpacityExpression(twinkle: Float): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(9f, 0f),
        Expression.stop(12f, twinkle),
    )

/**
 * 별자리 선 1개 — 두 대표 별(a, b). [key] 는 두 id 를 정렬해 이은 것(방향 무관 동일성).
 * 화면 좌표(ax..by)는 계산 시점 기준 — 선 긋기 순서/속도 계산에만 쓰고, 실제 선은 위경도로 그린다.
 */
internal class ConstellationEdge(
    val key: String,
    val aId: String, val aLat: Double, val aLng: Double, val ax: Float, val ay: Float,
    val bId: String, val bLat: Double, val bLng: Double, val bx: Float, val by: Float,
) {
    val lengthPx: Float get() = hypot(bx - ax, by - ay).coerceAtLeast(1f)
}

/**
 * 별자리 선 목록 — 뷰포트에 보이는 대표 별들을 가장 가까운
 * [CONSTELLATION_NEIGHBORS] 개와 잇는다([maxLinkPx] 이내, 카메라 idle 마다 재계산).
 */
internal fun buildConstellationEdges(
    map: MapLibreMap,
    diaries: List<Diary>,
    radiusPx: Float,
    maxLinkPx: Float,
): List<ConstellationEdge> {
    val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (valid.size < 2) return emptyList()
    // 마커와 동일한 순서(30m 머지 → 화면 클러스터링)로 실제 표시되는 대표만 사용
    val reps = clusterTopLiked(map, mergeByProximity(valid).reps, radiusPx).reps
    val bounds = map.projection.visibleRegion.latLngBounds
    val visible = reps.filter { bounds.contains(MlLatLng(it.latitude, it.longitude)) }
    if (visible.size < 2) return emptyList()

    val screen = visible.map { map.projection.toScreenLocation(MlLatLng(it.latitude, it.longitude)) }
    val maxLink2 = maxLinkPx * maxLinkPx
    val seen = HashSet<Long>() // i<j 를 i*N+j 로 인코딩해 중복 제거
    val out = mutableListOf<ConstellationEdge>()
    for (i in visible.indices) {
        val nearest = visible.indices
            .filter { it != i }
            .map { j ->
                val dx = screen[i].x - screen[j].x; val dy = screen[i].y - screen[j].y
                j to (dx * dx + dy * dy)
            }
            .filter { it.second <= maxLink2 }
            .sortedBy { it.second }
            .take(CONSTELLATION_NEIGHBORS)
        for ((j, _) in nearest) {
            // 키가 id 정렬 기준이라 a/b 도 id 순서로 고정(재계산해도 같은 선은 같은 a/b)
            val (lo, hi) = if (visible[i].id < visible[j].id) i to j else j to i
            if (!seen.add(minOf(i, j).toLong() * visible.size + maxOf(i, j))) continue
            val a = visible[lo]; val b = visible[hi]
            out.add(
                ConstellationEdge(
                    key = a.id + "|" + b.id,
                    aId = a.id, aLat = a.latitude, aLng = a.longitude, ax = screen[lo].x, ay = screen[lo].y,
                    bId = b.id, bLat = b.latitude, bLng = b.longitude, bx = screen[hi].x, by = screen[hi].y,
                )
            )
        }
    }
    return out
}

/**
 * 선 긋기 계획의 한 줄 — [fromA] 쪽 별에서 반대쪽으로 뻗는다.
 * 진행도 p = ((d − [startPx]) / length) 를 0..1 로 자른 값(d = 경과시간 × 속도, px).
 * 이미 일부 그려져 있던 선은 startPx 가 음수(= −p0·length)라 이어서 뻗는다.
 */
internal class ConstellationDraw(val edge: ConstellationEdge, val fromA: Boolean, val startPx: Float) {
    fun progress(d: Float): Float = ((d - startPx) / edge.lengthPx).coerceIn(0f, 1f)
    val fromId: String get() = if (fromA) edge.aId else edge.bId
    val toId: String get() = if (fromA) edge.bId else edge.aId
}

/**
 * 별자리를 "빛이 별을 타고 흐르듯" 긋는 순서를 정한다.
 *
 * - 이미 그려져 있던 선([drawn]: key → (fromA, p))은 그 방향 그대로 이어서 뻗고, 그 선의 출발 별은
 *   도달 거리 0 = 새 선들의 출발점이 된다(줌/이동으로 선이 늘 때 기존 별자리에서 자라 나간다).
 * - 그런 출발점이 없는 연결 덩어리는 **화면 중앙([cx],[cy])에 가장 가까운 별**에서 시작한다.
 * - 각 별의 도달 거리 = 출발점에서 선을 타고 간 최단 화면 거리(다익스트라). 새 선은 도달 거리가 짧은
 *   쪽 별에서 출발해 그 거리만큼 늦게 시작 → 속도가 일정하면 모든 선이 "이어서" 그어진다.
 *
 * @return 선별 계획 + 각 덩어리의 출발 별 id(도착 플래시를 출발 별에도 한 번 터뜨린다).
 */
internal fun planConstellationDraw(
    edges: List<ConstellationEdge>,
    drawn: Map<String, Pair<Boolean, Float>>,
    cx: Float,
    cy: Float,
): Pair<List<ConstellationDraw>, List<String>> {
    val adj = HashMap<String, MutableList<ConstellationEdge>>()
    val pos = HashMap<String, Offset>()
    for (e in edges) {
        adj.getOrPut(e.aId) { mutableListOf() }.add(e)
        adj.getOrPut(e.bId) { mutableListOf() }.add(e)
        pos[e.aId] = Offset(e.ax, e.ay)
        pos[e.bId] = Offset(e.bx, e.by)
    }
    val reach = HashMap<String, Float>()
    fun seed(id: String, d: Float) { if (d < (reach[id] ?: Float.MAX_VALUE)) reach[id] = d }
    for (e in edges) {
        val (fromA, p) = drawn[e.key] ?: continue
        seed(if (fromA) e.aId else e.bId, 0f)
        seed(if (fromA) e.bId else e.aId, (1f - p) * e.lengthPx)
    }
    // 출발점이 없는 연결 덩어리 → 화면 중앙에 가장 가까운 별에서 시작
    val starts = mutableListOf<String>()
    val visited = HashSet<String>()
    for (node in adj.keys) {
        if (!visited.add(node)) continue
        val comp = mutableListOf(node)
        var k = 0
        while (k < comp.size) {
            for (e in adj[comp[k]].orEmpty()) {
                val o = if (e.aId == comp[k]) e.bId else e.aId
                if (visited.add(o)) comp.add(o)
            }
            k++
        }
        if (comp.none { it in reach }) {
            val s = comp.minBy { id -> pos[id]!!.let { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) } }
            reach[s] = 0f
            starts.add(s)
        }
    }
    // 다익스트라(노드 수가 화면 안 별 수라 단순 선택 방식으로 충분)
    val done = HashSet<String>()
    while (true) {
        val u = reach.entries.filter { it.key !in done }.minByOrNull { it.value }?.key ?: break
        done.add(u)
        val du = reach[u]!!
        for (e in adj[u].orEmpty()) {
            val v = if (e.aId == u) e.bId else e.aId
            seed(v, du + e.lengthPx)
        }
    }
    val plan = edges.map { e ->
        val prev = drawn[e.key]
        if (prev != null) {
            ConstellationDraw(e, prev.first, -prev.second * e.lengthPx)
        } else {
            val ra = reach[e.aId] ?: 0f
            val rb = reach[e.bId] ?: 0f
            ConstellationDraw(e, fromA = ra <= rb, startPx = minOf(ra, rb))
        }
    }
    return plan to starts
}

/** 웹 메르카토르 직선 위의 점 — 지도에 그려지는 직선과 정확히 겹치도록 위도는 메르카토르 y 로 보간. */
internal fun lerpOnMercator(lat1: Double, lng1: Double, lat2: Double, lng2: Double, t: Float): Point {
    fun y(lat: Double) = kotlin.math.ln(kotlin.math.tan(Math.PI / 4 + Math.toRadians(lat) / 2))
    val yy = y(lat1) + (y(lat2) - y(lat1)) * t
    val lat = Math.toDegrees(2 * kotlin.math.atan(kotlin.math.exp(yy)) - Math.PI / 2)
    return Point.fromLngLat(lng1 + (lng2 - lng1) * t, lat)
}

/** MapView 를 Compose 생명주기에 묶어 반환. ⚠️ MapLibre.getInstance 는 MapView 생성 전 1회 필수. */
@Composable
internal fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { id = View.generateViewId() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

