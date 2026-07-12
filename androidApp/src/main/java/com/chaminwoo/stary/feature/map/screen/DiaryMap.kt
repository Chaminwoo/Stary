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
import com.chaminwoo.stary.feature.map.OrsRouting
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

private const val DEFAULT_ZOOM = 15.0
private const val CURRENT_SOURCE = "current-location"
private const val DIARY_SOURCE = "diaries"
private const val STAR_PARTICLE_SOURCE = "star-particles"
private const val PARTICLE_ICON_ID = "star-particle-dot"
private const val CONSTELLATION_SOURCE = "constellation-lines"
private const val CONSTELLATION_LAYER = "constellation-layer"
private const val CONSTELLATION_GLOW_LAYER = "constellation-glow-layer"
private const val CONSTELLATION_HALO_LAYER = "constellation-halo-layer"
// 도보 길찾기 경로 (OpenRouteService)
private const val ROUTE_SOURCE = "walking-route"
private const val ROUTE_LAYER = "walking-route-layer"
// 도로 글린트(빛 알갱이) — road-glint 레이어의 대시 위상을 흘려 빛이 도로를 따라 흐르게 한다.
// dash/gap 값(선 두께 배수)은 maplibre_style.json 의 line-dasharray 와 일치해야 한다.
private const val ROAD_GLINT_LAYER = "road-glint"
private const val ROAD_GLINT_DASH = 0.5f
private const val ROAD_GLINT_GAP = 34f
private const val ROAD_GLINT_SPEED = 8.5f // 초당 위상 이동(선 두께 배수)
private const val ROAD_GLINT_STEPS = 40   // 위상 양자화 단계(대시 아틀라스 캐시 재사용 — 매 프레임 새 패턴 생성 방지)
// 위상이 한 바퀴 돌아 처음으로 되돌아가는(= 알갱이가 다시 태어나는) 순간 전후로 부드럽게 페이드.
private const val ROAD_GLINT_FADE_SEC = 0.2f
private val ROAD_GLINT_PERIOD_SEC = ROAD_GLINT_GAP / ROAD_GLINT_SPEED // 한 바퀴(초)
// 별자리 선 페이드용 최대 불투명도 (켜질 때 0→target 으로 부드럽게)
private const val CONSTELLATION_HALO_OPACITY = 0.18f
private const val CONSTELLATION_GLOW_OPACITY = 0.42f
private const val CONSTELLATION_LINE_OPACITY = 0.95f
/** 별자리: 각 별을 화면상 가장 가까운 별 몇 개와 연결. */
private const val CONSTELLATION_NEIGHBORS = 2

/** 인기 별의 글로우 오오라(CircleLayer) id — 위상 그룹별(별과 같은 float 적용). */
private fun auraLayerId(group: Int) = "diary-aura-$group"
/**
 * 바닥 빛 웅덩이(CircleLayer) id — 별이 지면을 은은히 비추는 앵커 고정 광.
 * 별(iconTranslate 부유)과 달리 지점에 고정되어 부유 시 시차가 생겨 "떠 있음"이 읽힌다.
 */
private fun groundLightLayerId(group: Int) = "diary-ground-light-$group"
private const val GROUND_LIGHT_OPACITY = 0.30f
private const val GROUND_LIGHT_OFFSET_Y = 8f // 별 중심보다 살짝 아래(지면 쪽)에 고인 빛
/** 화면상 클러스터 반경(dp). 이 거리 안에서 겹치는 별은 가장 좋아요 많은 별 하나로 합쳐 표시. */
private const val CLUSTER_RADIUS_DP = 4f
/** 좋아요 수 → 크기 배율 상한(좋아요 100개에서 3배). */
private const val LIKES_FOR_MAX_SIZE = 100
private const val MAX_LIKE_SIZE_MULT = 3f

/**
 * float/pulse 애니메이션 위상 그룹 수.
 * 한 SymbolLayer 의 iconTranslate 는 레이어 전체에 일괄 적용되므로,
 * 마커를 id 해시 기반 그룹으로 나눠 레이어별로 다른 위상을 줘 "따로따로" 부유하게 한다.
 */
private const val PHASE_GROUPS = 4
private fun diaryLayerId(group: Int) = "diary-stars-$group"
private val DIARY_LAYER_IDS = Array(PHASE_GROUPS) { diaryLayerId(it) }
private fun particleLayerId(group: Int) = "star-particles-$group"

/** 별가루 파티클: 개수/분포 반경/시드(고정 → 항상 같은 배치). */
private const val PARTICLE_COUNT = 400
private const val PARTICLE_RADIUS_M = 20_000.0
private const val PARTICLE_SEED = 42

/** 마커 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
private const val MARKER_SIDE_PX = 160

/** 개척 퀘스트(체크리스트 32) 대상국 비콘 소스/레이어/아이콘 id. */
private const val PIONEER_SOURCE = "pioneer-source"
private const val PIONEER_LAYER = "pioneer-layer"
private const val PIONEER_ICON_ID = "pioneer-icon"

/** 3D 글로브 "지구 보기" 버튼 노출 줌(이하로 줌아웃하면 버튼 표시 — 자동 전환 없음) / 지도 최소 줌. */
private const val GLOBE_BUTTON_ZOOM = 3.0
private const val MAP_MIN_ZOOM = 2.4
/** 대기 헤이즈 시작 줌 — 이 줌부터 [MAP_MIN_ZOOM] 까지 내려갈수록 파란 대기가 차올라 글로브 장면과 이어진다. */
private const val HAZE_START_ZOOM = 4.4

/** 기본 카메라 틸트(도) — 줌과 무관하게 항상 살짝 기울여 입체감을 낸다. */
private const val BASE_TILT_DEG = 25.0

/**
 * 별 기본/근접 크기 (iconSize 배율).
 * 주의: MapLibre 의 addImage(bitmap)는 기기 밀도(pixelRatio)로 나눠 표시하므로
 * 화면 크기 ≈ 160/density × iconSize dp (density 2.6 기준 0.65 → 약 40dp).
 */
private const val STAR_SIZE_FAR = 0.65f
private const val STAR_SIZE_NEAR = 0.9f

/** 앱 공용 좌표 -> MapLibre 좌표 */
private fun LatLng.toMl(): MlLatLng = MlLatLng(latitude, longitude)

/** type×color 조합의 스타일 이미지 id */
private fun starIconId(type: Int, color: Int) = "star-t$type-c$color"

/**
 * 별 마커 비트맵 생성 — PNG 미사용, [StarStyle.starPath] 로 직접 그린다.
 * (PNG 디코드→텍스처 경로가 에뮬레이터에서 대각선 빗금으로 깨지는 문제가 있어 Path 렌더로 교체)
 * 구성: 팔레트색 글로우(blur) + 수정 결정(크리스탈) 패싯 본체([StarStyle.drawCrystalFill]).
 */
private fun starBitmap(type: Int, colorIdx: Int): Bitmap {
    val color = StarStyle.colorOf(colorIdx).toArgb()
    val side = MARKER_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    // 별 본체는 글로우 여백을 위해 변의 78% 크기로 중앙 배치
    val starSize = side * 0.78f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(type, starSize)).apply {
        offset(offset, offset)
    }

    // 1) 글로우 (2회 = 진하게)
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glowPaint)
    canvas.drawPath(path, glowPaint)

    // 2) 본체 — 크리스탈 패싯 채움(실루엣 동일, 조각마다 색 변주)
    StarStyle.drawCrystalFill(canvas, type, colorIdx, offset, offset, starSize)
    return out
}

/**
 * 개척 퀘스트 비콘 비트맵(체크리스트 32) — 다이어리 별과 확실히 구분되는 "금색 이중 링 + 스파클".
 * 바깥 글로우 링(blur) + 얇은 실선 링 + 중앙 8꼭지 금색 스파클.
 */
private fun pioneerBeaconBitmap(): Bitmap {
    val side = MARKER_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cx = side / 2f
    val cy = side / 2f
    val gold = AndroidColor.parseColor("#FFD86F")

    // 1) 바깥 글로우 링(은은히 번지는 금빛)
    canvas.drawCircle(cx, cy, side * 0.40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = side * 0.045f
        maskFilter = android.graphics.BlurMaskFilter(side * 0.07f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        alpha = 190
    })
    // 2) 얇은 실선 링(선명한 경계)
    canvas.drawCircle(cx, cy, side * 0.40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = side * 0.015f
    })
    // 3) 중앙 스파클(8꼭지, 앰버골드 그라데이션 느낌은 단색+코어로)
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
 * iconSize 표현식: 줌 보간(줌아웃일수록 작게) × near 확대 × pulse(애니메이션 루프에서 갱신).
 * ["zoom"] 은 최상위 interpolate 에서만 허용되므로 줌 스톱의 출력값에 near 분기를 넣는다.
 */
private fun starSizeExpression(pulse: Float): Expression {
    // ⚠️ MapLibre 는 ["zoom"] 이 최상위 interpolate 입력일 때만 줌에 따라 연속 평가한다.
    // sizeMult(좋아요 배율) 곱셈을 바깥에서 하면 zoom 이 중첩돼 줌 보간이 멈춘 것처럼 보인다.
    // → sizeMult/near 분기를 각 stop 출력 "안"에 넣고, interpolate(zoom) 을 최상위로 유지.
    fun sized(zoomMult: Float): Expression {
        val near = Expression.product(
            Expression.literal(STAR_SIZE_NEAR * pulse * zoomMult), Expression.get("sizeMult")
        )
        val far = Expression.product(
            Expression.literal(STAR_SIZE_FAR * zoomMult), Expression.get("sizeMult")
        )
        return Expression.switchCase(
            Expression.eq(Expression.get("near"), Expression.literal(true)),
            near, far
        )
    }
    // 줌아웃일수록 훨씬 작게(저줌 배율 대폭 축소). 줌인(15)은 기존과 동일하게 유지.
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, sized(0.06f)),
        Expression.stop(10f, sized(0.2f)),
        Expression.stop(13f, sized(0.5f)),
        Expression.stop(15f, sized(1f)),
    )
}

/**
 * 인기 별 오오라(CircleLayer) 반경: 줌 보간 × per-feature sizeMult.
 * 별이 클수록(좋아요 많을수록) 더 넓게 빛난다.
 */
private fun auraRadiusExpression(): Expression {
    // starSizeExpression 과 동일하게 zoom 을 최상위로 유지하고 sizeMult 는 stop 안에서 곱한다.
    fun r(base: Float): Expression =
        Expression.product(Expression.literal(base), Expression.get("sizeMult"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, r(2f)),
        Expression.stop(10f, r(6f)),
        Expression.stop(13f, r(14f)),
        Expression.stop(15f, r(26f)),
    )
}

/** 바닥 빛 웅덩이 반경: 오오라보다 작고 은은하게, 모든 별 공통(줌 보간 × sizeMult). */
private fun groundLightRadiusExpression(): Expression {
    fun r(base: Float): Expression =
        Expression.product(Expression.literal(base), Expression.get("sizeMult"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, r(0.6f)),
        Expression.stop(10f, r(2f)),
        Expression.stop(13f, r(4.5f)),
        Expression.stop(15f, r(7f)),
    )
}

/**
 * 오오라 불투명도: sizeMult 데이터 보간.
 * sizeMult 1(좋아요 0) → 거의 안 보이고, 3(좋아요 100+) → 진하게.
 * 즉 인기 별만 두드러지게 발광한다.
 */
private fun auraOpacityExpression(): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.get("sizeMult"),
        Expression.stop(1f, 0f),
        Expression.stop(1.4f, 0.12f),
        Expression.stop(3f, 0.42f),
    )

/** 좋아요 수 → 별 크기 배율(1..[MAX_LIKE_SIZE_MULT]). */
private fun likeSizeMult(likeCount: Int): Float =
    1f + (likeCount.coerceIn(0, LIKES_FOR_MAX_SIZE).toFloat() / LIKES_FOR_MAX_SIZE) * (MAX_LIKE_SIZE_MULT - 1f)

/** 팔레트 색 → "#RRGGBB" (오오라 CircleLayer 색상용). */
private fun starColorHex(colorIdx: Int): String =
    String.format("#%06X", 0xFFFFFF and StarStyle.colorOf(colorIdx).toArgb())

/** 클러스터링 결과: 대표 별 목록 + (다이어리 id → 그 별이 속한 대표 id) 배정표. */
private data class ClusterResult(val reps: List<Diary>, val assignment: Map<String, String>)

/**
 * 화면 좌표 기반 클러스터링.
 * 현재 카메라/줌에서 각 다이어리를 화면 픽셀로 투영해, [radiusPx] 이내로 겹치는 별들을
 * **가장 좋아요가 많은 별 하나(대표)** 로 합친다. (배지/개수 표시 없음 — 대표 별만 렌더)
 *
 * 좋아요 내림차순으로 앵커를 잡으므로, 한 클러스터의 대표는 항상 그 묶음 중 최다 좋아요 별이다.
 * 줌에 따라 화면 거리가 달라지므로 카메라가 멈출 때마다 다시 계산한다.
 * 합쳐짐/펼쳐짐을 부드럽게 보이려면 [ClusterResult.assignment] 로 흡수 관계를 추적해 위치를 보간한다.
 */
private fun clusterTopLiked(
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
        reps.add(sorted[i]) // 대표 = 미할당 중 최다 좋아요
        assignment[sorted[i].id] = sorted[i].id
        val pi = screen[i]
        for (j in i + 1 until sorted.size) {
            if (assigned[j]) continue
            val pj = screen[j]
            val dx = pi.x - pj.x; val dy = pi.y - pj.y
            if (dx * dx + dy * dy <= r2) {
                assigned[j] = true
                assignment[sorted[j].id] = sorted[i].id // 흡수: 대표에 귀속
            }
        }
    }
    return ClusterResult(reps, assignment)
}

/** 합쳐진 다이어리 수 → 대표 별 크기 가산 배율(개수에 비례, 과도하지 않게 상한). */
private fun clusterSizeBoost(count: Int): Float =
    (1f + (count - 1) * 0.12f).coerceAtMost(2.2f)

/** 별 합치기(30m) 우선순위 — 좋아요 내림차순 → 오래된 순 → id(안정 타이브레이크). 대표 = 1순위. */
private val MERGE_PRIORITY: Comparator<Diary> =
    compareByDescending<Diary> { it.likeCount }.thenBy { it.createdAt }.thenBy { it.id }

/** 30m 지오 머지 결과: 대표 별 목록 + (대표 id → 우선순위 정렬된 멤버 전체). */
private data class MergeResult(val reps: List<Diary>, val groups: Map<String, List<Diary>>)

/**
 * 좌표 기반 별 합치기 — [StaryConfig.STAR_MERGE_RADIUS_M](30m) 안에 겹치는 다이어리는
 * 우선순위 1위(좋아요↓ → 오래된 순) 별을 대표로 한 별로 합친다(줌과 무관한 의미적 머지).
 * 대표의 모양/색이 합쳐진 별의 모양/색이 되고, 크기/밝기는 [mergeSizeMult] 로 합산 반영.
 */
private fun mergeByProximity(valid: List<Diary>): MergeResult {
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
private fun mergeSizeMult(members: List<Diary>): Float =
    likeSizeMult(members.sumOf { it.likeCount }) * clusterSizeBoost(members.size)

/**
 * 다이어리 → 별 마커 Feature.
 * [lng]/[lat] 와 [alpha] 는 합쳐짐/펼쳐짐 보간에 사용. [sizeMult] 는 최종 크기 배율(좋아요×클러스터 보너스).
 */
private fun diaryFeature(d: Diary, lng: Double, lat: Double, near: Boolean, alpha: Float, sizeMult: Float): Feature {
    val colorIdx = d.starColor.coerceIn(0, StarStyle.COLOR_COUNT - 1)
    return Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
        addStringProperty("id", d.id)
        addBooleanProperty("near", near)
        addNumberProperty("phaseGroup", kotlin.math.abs(d.id.hashCode()) % PHASE_GROUPS)
        addNumberProperty("sizeMult", sizeMult)
        addStringProperty("auraColor", starColorHex(colorIdx))
        addNumberProperty("alpha", alpha)
        addStringProperty("icon", starIconId(d.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1), colorIdx))
        addStringProperty("sparkleIcon", sparkleStarIconId(d.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1), colorIdx))
    }
}

/** 파티클 비트맵 변(px). 4의 배수 유지(GL 행 정렬). */
private const val PARTICLE_SIDE_PX = 24

/** 별가루 파티클 비트맵 — 흰색 글로우 + 흰색 코어의 작은 점. */
private fun particleBitmap(): Bitmap {
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

/** 다이어리 별 주위를 도는 마이크로 스파클 — 세트 수(궤도 2개: 안쪽/바깥쪽 역방향). */
private const val SPARKLE_SETS = 2
private const val SPARKLE_ICON_ID = "diary-sparkle-icon"
private fun sparkleLayerId(set: Int, group: Int) = "diary-sparkle-$set-$group"
private fun sparkleStarIconId(type: Int, color: Int) = "sparkle-star-t$type-c$color"

/** 스파클 iconSize 기본 배율(세트별) — 4차 피드백("파티클이 너무 작음")으로 상향. */
private fun sparkleSizeBase(set: Int) = if (set == 0) 0.90f else 0.68f

/** 마커 곁 스파클 비트맵 — 작은 4꼭지 별(글로우+본체). 작은/보통 별 곁에서 쓰는 기본 반짝이. */
private fun sparkleBitmap(): Bitmap {
    val side = 32
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val starSize = side * 0.62f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(0, starSize)).apply { offset(offset, offset) }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        maskFilter = android.graphics.BlurMaskFilter(4.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
    return out
}

/**
 * "큰 별" 곁을 도는 스파클 비트맵 — 그 별과 같은 모양([type])·색([colorIdx])의 미니 크리스탈.
 * [sparkleBitmap] 과 같은 32px 캔버스(같은 iconSize 스케일 기준)라 두 아이콘을 데이터 주도로
 * 섞어 써도 크기가 어긋나지 않는다.
 */
private fun sparkleStarBitmap(type: Int, colorIdx: Int): Bitmap {
    val color = StarStyle.colorOf(colorIdx).toArgb()
    val side = 32
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val starSize = side * 0.66f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(type, starSize)).apply { offset(offset, offset) }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        maskFilter = android.graphics.BlurMaskFilter(4.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    StarStyle.drawCrystalFill(canvas, type, colorIdx, offset, offset, starSize)
    return out
}

/** 스파클 크기의 sizeMult 지수 — 1(선형)에 가까울수록 큰 별 곁에서 눈에 띄게 커진다. */
private const val SPARKLE_SIZE_POW = 0.8f

/**
 * 스파클 iconSize — 줌인 상태에서만 보이는 장식(줌 11 이하 완전 숨김).
 * per-feature sizeMult^[SPARKLE_SIZE_POW] 를 곱해 큰(합쳐진/인기) 별 곁 스파클은 눈에 띄게 커진다
 * (기존 √sizeMult 보다 성장이 가팔라 "커짐"이 뚜렷하게 읽힌다).
 * 바깥 궤도(set 1)는 살짝 더 작게.
 */
private fun sparkleSizeExpression(set: Int): Expression {
    val base = sparkleSizeBase(set)
    fun sized(zoomMult: Float): Expression = Expression.product(
        Expression.literal(base * zoomMult),
        Expression.pow(Expression.get("sizeMult"), Expression.literal(SPARKLE_SIZE_POW))
    )
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(11f, sized(0f)),
        Expression.stop(13f, sized(0.55f)),
        Expression.stop(15.5f, sized(1f)),
    )
}

/** [sparkleSizeExpression] 의 줌 구간 배율(0..1) — iconOffset px→icon 단위 환산에 사용. */
private fun sparkleZoomFactor(zoom: Float): Float = when {
    zoom <= 11f -> 0f
    zoom <= 13f -> (zoom - 11f) / 2f * 0.55f
    zoom <= 15.5f -> 0.55f + (zoom - 13f) / 2.5f * 0.45f
    else -> 1f
}

/**
 * 스파클 궤도 목표 반경(**dp**, 화면에 실제로 보일 크기) — 별의 렌더 공식을 그대로 따라가면
 * (near/far·pulse 등 변수가 많아) 오히려 너무 크게 벌어지는 문제가 반복돼, "항상 별 바로
 * 곁에서 작게 도는" 디자인 의도를 직접 dp 로 못박아 관리한다(4차 피드백: 여전히 궤도가 큼).
 * sizeMult(1..6.6)에 따라 로그 성장(작은 별과 큰 별의 차이는 있되 폭주하지 않음).
 */
private fun orbitTargetDp(set: Int, sizeMult: Float): Float {
    val (base, growth) = if (set == 0) 5f to 3.2f else 7.5f to 4.6f
    return base + growth * kotlin.math.ln(sizeMult.coerceAtLeast(1f))
}

/**
 * 스파클 궤도 iconOffset — [orbitTargetDp] 를 기기 밀도로 스프라이트 픽셀 단위로 환산한 뒤,
 * 스파클 아이콘 자신의 icon-size 배율로 나눠 offset-unit 을 구한다(offset 은 icon-size 와 같은
 * 스프라이트 픽셀 공간에서 정의되고 최종적으로 함께 밀도로 나뉘어 표시되므로, 이렇게 하면
 * 기기 밀도와 무관하게 항상 목표 dp 반경으로 보인다). step(sizeMult) 티어로 양자화.
 */
private fun sparkleOrbitOffsetExpression(
    set: Int, ux: Float, uy: Float, sizeZoomFactor: Float, density: Float,
): Expression {
    fun offsetFor(tier: Float): Expression {
        val desiredSpritePx = orbitTargetDp(set, tier) * density
        val spriteScale = (
            sparkleSizeBase(set) * sizeZoomFactor *
                Math.pow(tier.toDouble(), SPARKLE_SIZE_POW.toDouble()).toFloat()
            ).coerceAtLeast(0.0001f)
        val u = desiredSpritePx / spriteScale
        return Expression.literal(arrayOf<Any>(ux * u, uy * u))
    }
    // 티어 경계는 mergeSizeMult(좋아요×개수 보너스) 실효 범위 1..6.6 을 커버
    return Expression.step(
        Expression.get("sizeMult"), offsetFor(1f),
        Expression.stop(1.25f, offsetFor(1.5f)),
        Expression.stop(1.75f, offsetFor(2f)),
        Expression.stop(2.35f, offsetFor(2.75f)),
        Expression.stop(3.1f, offsetFor(3.5f)),
        Expression.stop(4f, offsetFor(4.5f)),
        Expression.stop(5f, offsetFor(5.5f)),
    )
}

/** 이 sizeMult 이상이면 "큰 별" — 스파클이 흰색 4꼭지 대신 그 별의 모양/색을 닮은 미니 크리스탈로 바뀐다. */
private const val SPARKLE_BIG_STAR_THRESHOLD = 1.75f

/**
 * 별가루 파티클 FeatureCollection — [center] 기준 반경 [PARTICLE_RADIUS_M] 내 균등 분포.
 * 시드 고정이라 매 실행 같은 배치. 한 번 생성 후 갱신하지 않는다(카메라 이동과 무관).
 */
private fun starParticleFeatures(center: LatLng): FeatureCollection {
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

/**
 * 파티클 iconSize: 줌 6→0, 10→0.4, 15→0.8 보간 × per-feature depth(0.5..1.0).
 * 데이터 주도 expression 이므로 한 번만 설정하면 된다.
 * (사라지는 시점은 opacity 와 함께 줌 6 — 사용자 튜닝값)
 */
private fun particleSizeExpression(): Expression {
    fun sized(base: Float): Expression =
        Expression.product(Expression.literal(base), Expression.get("depth"))
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(9f, sized(0f)),
        Expression.stop(12f, sized(0.45f)),
        Expression.stop(15f, sized(0.8f)),
    )
}

/**
 * 파티클 iconOpacity: 줌 6 이하 완전 숨김 → 10 에서 [twinkle] 까지 선형 등장.
 * twinkle 은 애니메이션 루프에서 레이어(위상 그룹)별로 갱신해 반짝임을 만든다.
 */
private fun particleOpacityExpression(twinkle: Float): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(9f, 0f),
        Expression.stop(12f, twinkle),
    )

/**
 * road-glint 레이어 iconOpacity — maplibre_style.json 의 zoom 스톱(13→0, 15→0.55, 17→0.7)을
 * 그대로 유지하면서 [envelope](0..1, 위상 순환 페이드)를 곱해 애니메이션 루프에서 갱신한다.
 * (2026-07 튜닝: 최소줌·페이드 구간을 더 높은 줌으로 올려 줌아웃 시 더 빨리 사라지게)
 */
private fun roadGlintOpacityExpression(envelope: Float): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(13f, 0f),
        Expression.stop(15f, 0.55f * envelope),
        Expression.stop(17f, 0.7f * envelope),
    )

/**
 * 별자리 라인 GeoJSON — **현재 화면(뷰포트)에 보이는 별(클러스터 대표)** 들을 잇는다.
 * 각 별을 화면 좌표상 가장 가까운 [CONSTELLATION_NEIGHBORS] 개 별과 연결([maxLinkPx] 이내).
 * 카메라가 바뀌면 다시 계산해 항상 "지금 보이는 별"만 이어지게 한다.
 */
private fun buildConstellationFeatures(
    map: MapLibreMap,
    diaries: List<Diary>,
    radiusPx: Float,
    maxLinkPx: Float,
): FeatureCollection {
    val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (valid.size < 2) return FeatureCollection.fromFeatures(emptyList())
    // 화면에 실제 표시되는 대표 별만 사용 (30m 지오 머지 → 화면 클러스터링 순서로 마커와 동일)
    val reps = clusterTopLiked(map, mergeByProximity(valid).reps, radiusPx).reps
    val bounds = map.projection.visibleRegion.latLngBounds
    val visible = reps.filter { bounds.contains(MlLatLng(it.latitude, it.longitude)) }
    if (visible.size < 2) return FeatureCollection.fromFeatures(emptyList())

    val screen = visible.map { map.projection.toScreenLocation(MlLatLng(it.latitude, it.longitude)) }
    val maxLink2 = maxLinkPx * maxLinkPx
    val edges = HashSet<Long>() // i<j 를 i*N+j 로 인코딩해 중복 제거
    val lines = mutableListOf<Feature>()
    for (i in visible.indices) {
        // i 에서 가까운 순으로 이웃 정렬 → 상위 N 개(거리 제한 내) 연결
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
            val lo = minOf(i, j); val hi = maxOf(i, j)
            if (!edges.add(lo.toLong() * visible.size + hi)) continue
            lines.add(
                Feature.fromGeometry(
                    LineString.fromLngLats(listOf(
                        Point.fromLngLat(visible[lo].longitude, visible[lo].latitude),
                        Point.fromLngLat(visible[hi].longitude, visible[hi].latitude)
                    ))
                )
            )
        }
    }
    return FeatureCollection.fromFeatures(lines)
}

/**
 * MapView 를 Compose 생명주기에 묶어 반환. (MapLibre.getInstance 는 MapView 생성 전 1회)
 */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
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

/**
 * MapLibre GL Native + MapTiler 벡터 타일 기반 지도.
 *
 * - 다이어리 별 마커: GeoJSON + SymbolLayer. star_1..5 × 12색 tint, 비율 유지 렌더.
 * - 100m 이내 별은 크게(near) + pulse, 전체 별은 float(부유) 애니메이션.
 * - 별 클릭: 100m 이내 → 열람 / 밖 → 거리 안내 토스트. (길찾기 기능은 제거됨)
 * - "내 위치로" FAB 로 현재 위치 복귀.
 */
/** 외부(알림 등)에서 "이 다이어리로 카메라 이동 + 파장" 을 요청할 때 전달하는 대상. */
/**
 * 저장된 전체 경로 [full] 에서 현재 위치 [me] 의 최근접 투영점을 찾아
 * "[me] → 최근접점 → 그 이후 ~ 목적지" 좌표열을 만든다(지나온 구간은 제외).
 * 위경도를 경도 cos(위도) 보정 평면으로 근사(도보 거리에선 충분히 정확).
 */
private fun partialRouteFrom(full: List<Point>, me: Point): List<Point> {
    val kx = kotlin.math.cos(Math.toRadians(me.latitude()))
    fun px(p: Point) = p.longitude() * kx
    fun py(p: Point) = p.latitude()
    val mx = px(me); val my = py(me)
    var bestK = 0; var bestT = 0.0; var bestD = Double.MAX_VALUE
    for (i in 0 until full.size - 1) {
        val ax = px(full[i]); val ay = py(full[i])
        val bx = px(full[i + 1]); val by = py(full[i + 1])
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 < 1e-12) 0.0 else (((mx - ax) * dx + (my - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx; val cy = ay + t * dy
        val d = (mx - cx) * (mx - cx) + (my - cy) * (my - cy)
        if (d < bestD) { bestD = d; bestK = i; bestT = t }
    }
    val a = full[bestK]; val b = full[bestK + 1]
    val cLng = a.longitude() + bestT * (b.longitude() - a.longitude())
    val cLat = a.latitude() + bestT * (b.latitude() - a.latitude())
    val out = ArrayList<Point>(full.size - bestK + 2)
    out.add(me)                                   // 내 실시간 위치
    out.add(Point.fromLngLat(cLng, cLat))         // 경로 위 최근접점(떨어져 있으면 직선으로 연결)
    for (j in bestK + 1 until full.size) out.add(full[j]) // 그 이후 ~ 목적지
    return out
}

data class DiaryFocusTarget(
    val lat: Double,
    val lng: Double,
    val colorIndex: Int,
    val diaryId: String,
    val withRoute: Boolean = false,
)

/** 글로브에서 지도로 복귀할 때의 카메라 요청(nonce 로 같은 좌표 반복 요청도 트리거). */
data class GlobeReturnCamera(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val nonce: Long,
)

@Composable
fun DiaryMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    onDiaryClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 30m 안에서 합쳐진 별(2개 이상) 열람 — 파장 후 카드 스와이프 화면으로. 우선순위 정렬된 id 목록 전달. */
    onClusterClick: (List<String>) -> Unit = {},
    focusDiary: DiaryFocusTarget? = null,
    onFocusHandled: () -> Unit = {},
    showCreate: Boolean = true, // 비로그인 시 다이어리 생성(업로드) 버튼 숨김
    /**
     * 줌이 [GLOBE_BUTTON_ZOOM] 이하면 (중심 위경도, true), 위로 올라오면 (_, _, false) 로 보고.
     * 호출부는 이 값으로 하단 "지구 보기" 버튼을 노출/숨김(자동 전환 없음 — 버튼으로만 진입).
     */
    onGlobeAvailability: ((lat: Double, lng: Double, available: Boolean) -> Unit)? = null,
    /** 글로브 → 지도 복귀 카메라 요청. */
    globeReturnCamera: GlobeReturnCamera? = null,
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()

    val styleJson = remember {
        context.resources.openRawResource(R.raw.maplibre_style)
            .bufferedReader().use { it.readText() }
            .replace("__MAPTILER_KEY__", BuildConfig.MAPTILER_KEY)
    }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var locationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var diarySource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var constellationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var routeSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var pioneerSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    val routeScope = rememberCoroutineScope()
    // 도보 길찾기: 처음 받은 전체 경로(목적지까지)를 저장. null = 길찾기 비활성(X 취소 시).
    var savedRoute by remember { mutableStateOf<List<Point>?>(null) }
    val addedIcons = remember { mutableSetOf<String>() }
    val isCameraMoving = remember { mutableStateOf(false) }
    // 저줌 대기 헤이즈 강도(0..1) — 줌이 HAZE_START_ZOOM 밑으로 내려갈수록 1에 접근.
    val hazeAlpha = remember { mutableStateOf(0f) }
    // 카메라가 멈출 때마다 증가 → 줌/이동에 따라 화면 클러스터링 재계산 트리거
    var cameraIdleTick by remember { mutableStateOf(0) }
    val clusterRadiusPx = remember { CLUSTER_RADIUS_DP * context.resources.displayMetrics.density }
    val screenDensity = remember { context.resources.displayMetrics.density }
    // 별자리 최대 연결 거리(px) — 화면 짧은 변의 절반 정도까지만 이어 과한 장거리 연결 방지
    val constellationMaxLinkPx = remember {
        context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) * 0.55f }
    }
    var constellationEnabled by remember { mutableStateOf(false) }
    // 배경음악은 앱 전역(MusicManager, MainScreen 에서 생명주기 관리)에서 처리. 여기선 FAB 토글만.

    // 다이어리 진입 직전 "지도 왜곡" 연출 — 탭 시 지도 스냅샷을 떠서 1초간 파장+울렁 후 세부 화면 이동.
    val warpState = remember { mutableStateOf<DiaryOpenWarpData?>(null) }

    val onDiaryClickRef = rememberUpdatedState(onDiaryClick)
    val onClusterClickRef = rememberUpdatedState(onClusterClick)
    val onFocusHandledRef = rememberUpdatedState(onFocusHandled)
    // 30m 머지 그룹(대표 id → 우선순위 정렬된 멤버들) — 클릭 리스너(1회 등록)에서 최신값 참조.
    val mergeGroupsState = remember { mutableStateOf<Map<String, List<Diary>>>(emptyMap()) }
    val currentLatLngRef = rememberUpdatedState(currentLatLng)
    val diariesRef = rememberUpdatedState(diaries)
    val initialLatLngRef = rememberUpdatedState(currentLatLng)
    val onGlobeAvailabilityRef = rememberUpdatedState(onGlobeAvailability)

    // "내 위치로" 카메라 이동 — FAB 과 글로브 복귀에서 공용(동일 로직 1회 실행).
    val recenterToMyLocation: () -> Unit = {
        mapRef?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(currentLatLngRef.value.toMl()).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            )
        )
    }

    // 글로브 → 지도 복귀 시 "내 위치로" 버튼과 동일한 카메라 이동을 1회 자동 실행(체크리스트 27).
    // nonce 로 같은 복귀도 매번 트리거. 글로브가 가리던 동안 애니메이션이 시작돼 스크림이 걷히면 이미 내 위치.
    LaunchedEffect(globeReturnCamera) {
        globeReturnCamera ?: return@LaunchedEffect
        recenterToMyLocation()
    }

    // 도보 길찾기(친구 별 탭) — 현위치→목적지 전체 경로를 받아 savedRoute 에 저장(X 취소까지 유지).
    // 실시간 위치에 맞춰 "내 위치→경로 최근접점→목적지"만 렌더하는 건 아래 LaunchedEffect 가 담당.
    // ORS 키 미설정/네트워크 실패 시 조용히 무시.
    val requestRoute: (Double, Double) -> Unit = { destLat, destLng ->
        val cur = currentLatLngRef.value
        routeScope.launch {
            val route = OrsRouting.walkingRoute(cur.latitude, cur.longitude, destLat, destLng) ?: return@launch
            savedRoute = route.coordinates.map { Point.fromLngLat(it[0], it[1]) }
            val mins = (route.durationS / 60.0).roundToInt().coerceAtLeast(1)
            com.chaminwoo.stary.core.ui.StaryToast.show(
                context.getString(R.string.map_route_summary, mins, route.distanceM.roundToInt())
            )
        }
    }
    val requestRouteRef = rememberUpdatedState(requestRoute)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        ) { mv ->
            mv.getMapAsync { map ->
                if (mapRef != null) return@getMapAsync
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }
                map.setMinZoomPreference(MAP_MIN_ZOOM) // 이 밑은 3D 글로브가 담당
                map.addOnCameraMoveStartedListener { isCameraMoving.value = true }
                map.addOnCameraIdleListener {
                    isCameraMoving.value = false
                    cameraIdleTick++
                }
                // 줌 상태 보고 → 호출부가 하단 "지구 보기" 버튼 노출을 결정(자동 전환 없음)
                map.addOnCameraMoveListener {
                    val z = map.cameraPosition.zoom
                    // 대기 헤이즈: 알파가 실제로 변할 때만 state 갱신(팬 중 불필요한 리컴포지션 방지)
                    val a = ((HAZE_START_ZOOM - z) / (HAZE_START_ZOOM - MAP_MIN_ZOOM))
                        .toFloat().coerceIn(0f, 1f)
                    if (a != hazeAlpha.value) hazeAlpha.value = a
                    val cb = onGlobeAvailabilityRef.value ?: return@addOnCameraMoveListener
                    val c = map.cameraPosition.target
                    cb(c?.latitude ?: 0.0, c?.longitude ?: 0.0, z <= GLOBE_BUTTON_ZOOM)
                }
                // 별 클릭 → 100m 게이팅 (길찾기 기능은 제거됨 — 밖이면 안내 토스트만)
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    // 개척 퀘스트 비콘 탭 → 퀘스트 안내(체크리스트 32)
                    val pioneer = map.queryRenderedFeatures(screen, PIONEER_LAYER).firstOrNull()
                    if (pioneer != null) {
                        val code = pioneer.getStringProperty("code") ?: ""
                        val country = com.chaminwoo.stary.core.util.LocalizedNames.countryName(code)
                        val title = context.getString(R.string.pioneer_title_format, country)
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            context.getString(R.string.pioneer_quest_toast, country, title)
                        )
                        return@addOnMapClickListener true
                    }
                    val features = map.queryRenderedFeatures(screen, *DIARY_LAYER_IDS)
                    val id = features.firstOrNull()?.getStringProperty("id")
                    if (id != null) {
                        val diary = diariesRef.value.firstOrNull { it.id == id }
                        if (diary != null) {
                            // 실제 위치 fix 전에는 열람 불가 — currentLatLng 폴백(마지막 저장 위치/기본좌표)으로
                            // 100m 판정하면 이동/조작으로 우회될 수 있다.
                            if (LocationHelper.getCurrentLatLng() == null) {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(R.string.map_waiting_fix)
                                )
                                return@addOnMapClickListener true
                            }
                            val cur = currentLatLngRef.value
                            val distance = LocationHelper.distanceBetween(
                                cur.latitude, cur.longitude, diary.latitude, diary.longitude
                            )
                            if (distance <= StaryConfig.DIARY_OPEN_RADIUS_M) {
                                // 파장이 이 별 위치에서 퍼지도록 화면상 위치(0..1) 계산
                                val sp = map.projection.toScreenLocation(
                                    MlLatLng(diary.latitude, diary.longitude)
                                )
                                val w = mv.width.toFloat().coerceAtLeast(1f)
                                val h = mv.height.toFloat().coerceAtLeast(1f)
                                val ox = (sp.x / w).coerceIn(0f, 1f)
                                val oy = (sp.y / h).coerceIn(0f, 1f)
                                // 30m 머지 그룹 — 2개 이상이면 파장에 멤버 별 모양 파티클을 얹고 카드 뷰어로.
                                val group = mergeGroupsState.value[id] ?: listOf(diary)
                                // 현재 지도를 스냅샷으로 떠서, 그 이미지를 1초간 왜곡(파장+울렁)한 뒤 세부 화면으로 이동
                                map.snapshot { bmp ->
                                    // 열람 애니메이션(파장) 시작과 동시에 열람 효과음 재생
                                    com.chaminwoo.stary.core.util.MusicManager.playOpenDiary()
                                    warpState.value = DiaryOpenWarpData(
                                        bmp, ox, oy, id, diary.starColor, navigateAfter = true,
                                        burstStars = if (group.size > 1) {
                                            group.take(12).map { it.starType to it.starColor }
                                        } else emptyList(),
                                        clusterIds = if (group.size > 1) group.map { it.id } else emptyList(),
                                    )
                                }
                            } else {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(
                                        R.string.map_open_range,
                                        StaryConfig.DIARY_OPEN_RADIUS_M.toInt(),
                                        distance.toInt()
                                    )
                                )
                            }
                        }
                        true
                    } else false
                }
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    val start = initialLatLngRef.value

                    // 별가루 파티클 — 실제 지도 좌표 GeoJSON, MapLibre 컬링으로 화면 내만 렌더.
                    // 다이어리 별보다 먼저 추가해 아래에 깔린다. 생성 후 갱신 없음.
                    style.addImage(PARTICLE_ICON_ID, particleBitmap())
                    style.addSource(
                        GeoJsonSource(STAR_PARTICLE_SOURCE, starParticleFeatures(start))
                    )
                    for (g in 0 until PHASE_GROUPS) {
                        val layer = SymbolLayer(particleLayerId(g), STAR_PARTICLE_SOURCE)
                            .withProperties(
                                PropertyFactory.iconImage(PARTICLE_ICON_ID),
                                PropertyFactory.iconSize(particleSizeExpression()),
                                PropertyFactory.iconOpacity(particleOpacityExpression(1f)),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                            )
                        layer.setFilter(
                            Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                        )
                        style.addLayer(layer)
                    }

                    // 별자리 라인 (파티클 위, 마커 아래) — 외곽 후광 + 글로우 + 밝은 선 3겹.
                    // 초기 불투명도 0 → 토글 시 페이드 인/아웃(부드럽게 켜짐).
                    val cSrc = GeoJsonSource(CONSTELLATION_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(cSrc)
                    // 0) 가장 뒤 외곽 후광: 아주 굵고 크게 번지는 빛무리
                    style.addLayer(
                        LineLayer(CONSTELLATION_HALO_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#6EE7B7"),
                            PropertyFactory.lineWidth(16f),
                            PropertyFactory.lineBlur(16f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    // 1) 중간 후광: 굵고 흐리게(blur)
                    style.addLayer(
                        LineLayer(CONSTELLATION_GLOW_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#6EE7B7"),
                            PropertyFactory.lineWidth(8f),
                            PropertyFactory.lineBlur(8f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    // 2) 위에 그려지는 밝은 선: 얇고 선명하게
                    style.addLayer(
                        LineLayer(CONSTELLATION_LAYER, CONSTELLATION_SOURCE).withProperties(
                            PropertyFactory.lineColor("#E6FFF4"),
                            PropertyFactory.lineWidth(1.7f),
                            PropertyFactory.lineBlur(0.6f),
                            PropertyFactory.lineOpacity(0f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    constellationSource = cSrc

                    // 도보 길찾기 경로 (별자리와 같은 레벨, 마커 아래). 초기 빈 상태 → 별(밖) 탭 시 채워진다.
                    val rSrc = GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(rSrc)
                    style.addLayer(
                        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                            PropertyFactory.lineColor("#86EFAC"),   // 연한 초록 실선(후광 없음)
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineOpacity(0.95f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    routeSource = rSrc

                    // 다이어리 별 마커 source — 클러스터링은 클라이언트(화면 좌표)에서 처리.
                    val dSrc = GeoJsonSource(DIARY_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(dSrc)

                    // 바닥 빛 웅덩이 — 별이 지면을 은은히 비추는 앵커 고정 광(모든 별 공통).
                    // 별은 iconTranslate 로 부유하지만 이 빛은 지점에 고정 → 시차로 "떠 있음"이 읽힌다.
                    for (g in 0 until PHASE_GROUPS) {
                        val light = CircleLayer(groundLightLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.circleColor(Expression.toColor(Expression.get("auraColor"))),
                            PropertyFactory.circleRadius(groundLightRadiusExpression()),
                            PropertyFactory.circleOpacity(
                                Expression.product(
                                    Expression.literal(GROUND_LIGHT_OPACITY), Expression.get("alpha")
                                )
                            ),
                            PropertyFactory.circleBlur(1.4f),
                            PropertyFactory.circleTranslate(arrayOf(0f, GROUND_LIGHT_OFFSET_Y)),
                        )
                        light.setFilter(Expression.eq(Expression.get("phaseGroup"), Expression.literal(g)))
                        style.addLayer(light)
                    }

                    // 오오라(인기 별 글로우) — 별 아래에 깔리는 CircleLayer. 위상 그룹별로 나눠 별과 같이 float.
                    for (g in 0 until PHASE_GROUPS) {
                        val aura = CircleLayer(auraLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.circleColor(Expression.toColor(Expression.get("auraColor"))),
                            PropertyFactory.circleRadius(auraRadiusExpression()),
                            PropertyFactory.circleOpacity(
                                Expression.product(auraOpacityExpression(), Expression.get("alpha"))
                            ),
                            PropertyFactory.circleBlur(1f),
                        )
                        aura.setFilter(Expression.eq(Expression.get("phaseGroup"), Expression.literal(g)))
                        style.addLayer(aura)
                    }

                    // 별 마커 — 위상 그룹별 레이어(같은 source, phaseGroup 필터). iconOpacity = alpha(합쳐짐 보간).
                    for (g in 0 until PHASE_GROUPS) {
                        val layer = SymbolLayer(diaryLayerId(g), DIARY_SOURCE).withProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconSize(starSizeExpression(1f)),
                            PropertyFactory.iconOpacity(Expression.get("alpha")),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                        layer.setFilter(
                            Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                        )
                        style.addLayer(layer)
                    }

                    // 별 곁 마이크로 스파클 — 각 별 주위를 도는 작은 반짝이 2개(안쪽/바깥쪽 역방향 궤도).
                    // 같은 source 를 쓰고 icon-offset(별 크기 비례 궤도)+iconTranslate(부유)를 루프에서 갱신.
                    // 큰 별(sizeMult ≥ SPARKLE_BIG_STAR_THRESHOLD) 곁에서는 흰 4꼭지 대신
                    // 그 별의 모양/색을 닮은 미니 크리스탈(sparkleIcon)로 데이터 주도 전환.
                    style.addImage(SPARKLE_ICON_ID, sparkleBitmap())
                    val sparkleIconExpression = Expression.switchCase(
                        Expression.gte(Expression.get("sizeMult"), Expression.literal(SPARKLE_BIG_STAR_THRESHOLD)),
                        Expression.get("sparkleIcon"),
                        Expression.literal(SPARKLE_ICON_ID)
                    )
                    for (s in 0 until SPARKLE_SETS) {
                        for (g in 0 until PHASE_GROUPS) {
                            val layer = SymbolLayer(sparkleLayerId(s, g), DIARY_SOURCE).withProperties(
                                PropertyFactory.iconImage(sparkleIconExpression),
                                PropertyFactory.iconSize(sparkleSizeExpression(s)),
                                PropertyFactory.iconOpacity(0f),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                            )
                            layer.setFilter(
                                Expression.eq(Expression.get("phaseGroup"), Expression.literal(g))
                            )
                            style.addLayer(layer)
                        }
                    }

                    diarySource = dSrc

                    // 개척 퀘스트 대상국 비콘(체크리스트 32) — 미개척 대상국 중심좌표에 "금색 링+스파클" 아이콘.
                    // ⚠️ 이 스타일 JSON 에는 glyphs(폰트) 엔드포인트가 없어 textField 를 쓰면 심볼이 아예
                    // 렌더되지 않는다 → 아이콘 전용으로 유지(나라 이름은 탭 토스트로 안내).
                    style.addImage(PIONEER_ICON_ID, pioneerBeaconBitmap())
                    val pSrc = GeoJsonSource(PIONEER_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(pSrc)
                    style.addLayer(
                        SymbolLayer(PIONEER_LAYER, PIONEER_SOURCE).withProperties(
                            PropertyFactory.iconImage(PIONEER_ICON_ID),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(2f, 0.55f),
                                    Expression.stop(8f, 0.8f),
                                    Expression.stop(14f, 1.0f),
                                )
                            ),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                    )
                    pioneerSource = pSrc

                    // 내 위치 마커 (별 위에 표시)
                    val src = GeoJsonSource(
                        CURRENT_SOURCE,
                        Point.fromLngLat(start.longitude, start.latitude)
                    )
                    style.addSource(src)
                    style.addLayer(
                        CircleLayer("current-location-layer", CURRENT_SOURCE).withProperties(
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor(AndroidColor.parseColor("#6EE7B7")),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor(AndroidColor.parseColor("#FFFFFF")),
                        )
                    )
                    locationSource = src

                    val target = LocationHelper.cameraTarget
                    if (target == null) {
                        map.cameraPosition = CameraPosition.Builder()
                            .target(start.toMl())
                            .zoom(DEFAULT_ZOOM)
                            .tilt(BASE_TILT_DEG)
                            .build()
                    } else {
                        LocationHelper.cameraTarget = null
                        val bounds = LatLngBounds.Builder()
                            .include(start.toMl())
                            .include(MlLatLng(target.latitude, target.longitude))
                            .build()
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                        map.cameraPosition = CameraPosition.Builder(map.cameraPosition)
                            .tilt(BASE_TILT_DEG).build()
                    }
                    styleRef = style
                    mapRef = map
                }
            }
        }

        // 비네트 + 저줌 대기 헤이즈 — 그리기 전용(터치는 지도로 통과), FAB/워프 오버레이보다 아래.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = hypot(size.width, size.height) / 2f
            if (r <= 0f) return@Canvas
            // 1) 상시 비네트: 가장자리를 살짝 어둡게 눌러 화면에 깊이감을 준다
            drawRect(
                brush = Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.30f),
                    center = center,
                    radius = r,
                )
            )
            // 2) 글로브 근접 대기 헤이즈: 줌아웃할수록 파란 대기가 차올라 글로브 전환과 이어진다
            val a = hazeAlpha.value
            if (a > 0f) {
                drawRect(Color(0xFF060E1C).copy(alpha = 0.35f * a))
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to Color(0xFF3E7CC4).copy(alpha = 0.10f * a),
                        1.0f to Color(0xFF9BD1FF).copy(alpha = 0.22f * a),
                        center = center,
                        radius = r,
                    )
                )
            }
        }

        // 지도만 보기 모드에선 모든 버튼(좌상단 줌 + 우하단 FAB)을 숨긴다.
        if (!MapUiState.mapOnly) {
        // 좌상단 줌 버튼 (+/-) — 버튼 1탭당 한 단계, 부드럽게 애니메이션 줌.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = { mapRef?.animateCamera(CameraUpdateFactory.zoomBy(1.0), 220) },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.cd_zoom_in), tint = Color.White, modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = { mapRef?.animateCamera(CameraUpdateFactory.zoomBy(-1.0), 220) },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.Remove, stringResource(R.string.cd_zoom_out), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 내 위치로 이동
            FloatingActionButton(
                onClick = { recenterToMyLocation() },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Navigation, stringResource(R.string.cd_my_location), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // 별자리 토글
            FloatingActionButton(
                onClick = { constellationEnabled = !constellationEnabled },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    stringResource(R.string.map_constellation),
                    tint = if (constellationEnabled) Color(0xFF6EE7B7) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 몰입(지도만 보기) — 탑바/필터/버튼을 모두 숨기고 지도에 집중
            FloatingActionButton(
                onClick = { MapUiState.enterMapOnly() },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                containerColor = Color(0xFF1A1A1A),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    stringResource(R.string.map_only),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 다이어리 생성 (로그인 상태에서만 노출)
            if (showCreate) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    containerColor = Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6EE7B7), Color(0xFF3B82F6)),
                                    start = Offset(0f, 0f), end = Offset(80f, 80f)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, stringResource(R.string.cd_create_diary), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        } // if (!MapUiState.mapOnly)

        // 길찾기 취소 — 도보 경로 활성 시 하단 중앙 X 버튼(누르면 경로 제거).
        if (savedRoute != null) {
            FloatingActionButton(
                onClick = { savedRoute = null },
                containerColor = Color(0xFF0E1520),
                contentColor = Color(0xFF86EFAC),
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .size(52.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = context.getString(R.string.map_route_cancel))
            }
        }

        // 지도 왜곡 연출 — 스냅샷 이미지를 1초간 파장+울렁시킨 뒤 세부 화면으로 이동(세부는 멀쩡).
        warpState.value?.let { wd ->
            DiaryOpenWarp(wd) {
                warpState.value = null
                if (wd.navigateAfter) {
                    // 별 탭(100m 이내) → 파장 후 세부 화면으로 (합쳐진 별이면 카드 뷰어로)
                    MapUiState.exitMapOnly()
                    if (wd.clusterIds.size > 1) onClusterClickRef.value(wd.clusterIds)
                    else onDiaryClickRef.value(wd.id)
                } else {
                    // 알림 포커스 → 파장만 내고 지도에 머문다
                    onFocusHandledRef.value()
                }
            }
        }
    }

    // 다이어리/현재위치/카메라 변경 → 화면 클러스터링 후 마커 갱신.
    // 겹친 별은 가장 좋아요 많은 별 하나(대표)로 합친다. 합쳐짐/펼쳐짐은 위치+투명도 보간으로 부드럽게.
    var lastFeaturesKey by remember { mutableStateOf<Any?>(null) }
    // 직전 표시 상태의 배정표(다이어리 id → 대표 id). 전이의 "from".
    var prevAssignment by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(diaries, styleRef, currentLatLng, cameraIdleTick) {
        val style = styleRef ?: return@LaunchedEffect
        val source = diarySource ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect

        // 디바운스: 연속 팬/줌으로 idle 이 잇따라 발생하면 O(n²) 클러스터링을 매번 돌리지 않게
        // 잠깐 모아서 한 번만 계산한다(LaunchedEffect 가 재시작되며 직전 대기를 취소).
        delay(90)

        val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        // ① 30m 지오 머지(줌 무관, 의미적 합치기) — 겹친 다이어리는 우선순위 1위 별로 합쳐진다.
        //    대표의 모양/색으로 렌더되고, 크기/밝기는 멤버 좋아요 합산(mergeSizeMult)으로 반영.
        val merged = mergeByProximity(valid)
        mergeGroupsState.value = merged.groups
        val mergedReps = merged.reps
        val byId = mergedReps.associateBy { it.id }

        // ② 화면 좌표 기준 클러스터링(저줌 시각 병합) → 대표(최다 좋아요) + 배정표
        val result = clusterTopLiked(map, mergedReps, clusterRadiusPx)
        val reps = result.reps
        val assignment = result.assignment
        val nearIds = reps.filter { d ->
            LocationHelper.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude, d.latitude, d.longitude
            ) <= StaryConfig.DIARY_OPEN_RADIUS_M
        }.map { it.id }.toSet()
        // 대표별 클러스터 크기(흡수된 다이어리 수, 자기 포함) → 크기 보너스
        val clusterCount = assignment.values.groupingBy { it }.eachCount()
        // 30m 머지 합산 배율(멤버 좋아요 합 + 개수 보너스)
        fun mergeMult(d: Diary): Float = mergeSizeMult(merged.groups[d.id] ?: listOf(d))
        fun repSizeMult(d: Diary): Float =
            mergeMult(d) * clusterSizeBoost(clusterCount[d.id] ?: 1)

        val key = reps.map { it.id } to nearIds
        if (key == lastFeaturesKey) return@LaunchedEffect
        lastFeaturesKey = key

        // 전이 중 흡수되는 별도 잠깐 보이므로 valid 전체의 아이콘을 등록
        valid.map {
            it.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1) to
                it.starColor.coerceIn(0, StarStyle.COLOR_COUNT - 1)
        }
            .distinct()
            .forEach { (type, color) ->
                val iconId = starIconId(type, color)
                if (addedIcons.add(iconId)) {
                    style.addImage(iconId, starBitmap(type, color))
                }
                val sparkleIconId = sparkleStarIconId(type, color)
                if (addedIcons.add(sparkleIconId)) {
                    style.addImage(sparkleIconId, sparkleStarBitmap(type, color))
                }
            }

        // 최종(정착) 상태: 대표만 alpha=1, 크기는 좋아요×클러스터 보너스
        fun settle() {
            source.setGeoJson(FeatureCollection.fromFeatures(
                reps.map { d -> diaryFeature(d, d.longitude, d.latitude, d.id in nearIds, 1f, repSizeMult(d)) }
            ))
        }

        val from = prevAssignment
        prevAssignment = assignment

        // 최초 1회는 애니메이션 없이 바로 표시
        if (from.isEmpty()) {
            settle()
            return@LaunchedEffect
        }

        fun lngOf(id: String) = byId[id]?.longitude
        fun latOf(id: String) = byId[id]?.latitude

        // 합쳐짐/펼쳐짐 보간: 각 별을 (이전 대표 위치 ↔ 새 대표 위치) 로 이동 + 투명도 페이드
        val durationNanos = 320_000_000.0
        var startNanos = 0L
        var t = 0f
        do {
            val frame = withFrameNanos { it }
            if (startNanos == 0L) startNanos = frame
            t = ((frame - startNanos) / durationNanos).toFloat().coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(t)
            val feats = ArrayList<Feature>(mergedReps.size)
            for (d in mergedReps) {
                val fromRep = from[d.id] ?: d.id
                val toRep = assignment[d.id] ?: d.id
                val fromAlpha = if (fromRep == d.id) 1f else 0f
                val toAlpha = if (toRep == d.id) 1f else 0f
                if (fromAlpha == 0f && toAlpha == 0f) continue // 계속 흡수됨 → 대표가 대신 표시
                val fLng = lngOf(fromRep) ?: d.longitude
                val fLat = latOf(fromRep) ?: d.latitude
                val tLng = lngOf(toRep) ?: d.longitude
                val tLat = latOf(toRep) ?: d.latitude
                val lng = fLng + (tLng - fLng) * e
                val lat = fLat + (tLat - fLat) * e
                val a = fromAlpha + (toAlpha - fromAlpha) * e
                // 대표로 정착하는 별만 클러스터 보너스 적용(흡수되는 별은 머지 배율만으로 페이드)
                val sm = if (toRep == d.id) repSizeMult(d) else mergeMult(d)
                feats.add(diaryFeature(d, lng, lat, d.id in nearIds, a, sm))
            }
            source.setGeoJson(FeatureCollection.fromFeatures(feats))
        } while (t < 1f)

        settle()
    }

    // 별자리 라인 GeoJSON — 켜져 있을 때만 "지금 화면에 보이는 별"로 다시 계산해 채운다.
    // (끌 때는 비우지 않고 아래 페이드 효과가 사라진 뒤 비워 — 부드럽게 사라지도록)
    LaunchedEffect(diaries, styleRef, constellationEnabled, cameraIdleTick) {
        val source = constellationSource ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (constellationEnabled) {
            delay(90) // 클러스터링과 동일하게 idle 디바운스(O(n²) 별자리 재계산 빈도 완화)
            source.setGeoJson(buildConstellationFeatures(map, diaries, clusterRadiusPx, constellationMaxLinkPx))
        }
    }

    // 별자리 페이드 인/아웃 — 토글 시 후광·글로우·선 불투명도를 0↔target 으로 부드럽게.
    val constellationFade = remember { Animatable(0f) }
    LaunchedEffect(constellationEnabled, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val halo = style.getLayer(CONSTELLATION_HALO_LAYER) as? LineLayer
        val glow = style.getLayer(CONSTELLATION_GLOW_LAYER) as? LineLayer
        val line = style.getLayer(CONSTELLATION_LAYER) as? LineLayer
        constellationFade.animateTo(
            targetValue = if (constellationEnabled) 1f else 0f,
            animationSpec = tween(if (constellationEnabled) 550 else 380, easing = FastOutSlowInEasing),
        ) {
            halo?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_HALO_OPACITY * value))
            glow?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_GLOW_OPACITY * value))
            line?.setProperties(PropertyFactory.lineOpacity(CONSTELLATION_LINE_OPACITY * value))
        }
        // 완전히 꺼졌으면 GeoJSON 비우기
        if (!constellationEnabled) {
            constellationSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // 마커 애니메이션: float(상하 부유) + 근접 별 pulse(맥동 확대).
    // 위상 그룹 레이어마다 다른 위상을 적용해 별들이 따로따로 부유한다.
    // 카메라 이동 중엔 스타일 변경을 멈춰 팬/줌 끊김을 방지한다.
    LaunchedEffect(styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        var t = 0f
        var lastDashOffset = -1f
        var lastGlintEnvelope = -1f
        while (isActive) {
            if (isCameraMoving.value) {
                delay(100)
                continue
            }
            // 줌이 작을수록 float 진폭을 줄인다(별 크기 곡선과 동일한 결: 줌6 거의 정지 ~ 줌15 최대).
            val zoom = (mapRef?.cameraPosition?.zoom ?: 13.0).toFloat()
            // 화면에 움직일 게 없으면(별 없음 + 파티클 숨김 줌) 루프를 쉬어 유휴 배터리 소모를 줄인다.
            val particlesVisible = zoom >= 9f
            if (diariesRef.value.isEmpty() && !particlesVisible) {
                delay(250)
                continue
            }
            t += 0.05f
            val zoomAmp = ((zoom - 6f) / (15f - 6f)).coerceIn(0.1f, 1f)
            for (g in 0 until PHASE_GROUPS) {
                val layer = style.getLayer(diaryLayerId(g)) as? SymbolLayer ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val wave = sin(t * 1.6f + phase) // -1(위)..1(아래)
                val floatDy = wave * 4f * zoomAmp // 최대 -4..4 dp, 줌 작으면 축소
                val pulse = 1f + 0.20f * ((sin(t * 3.2f + phase) + 1f) / 2f) // 1.0..1.2 맥동
                layer.setProperties(
                    PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                    PropertyFactory.iconSize(starSizeExpression(pulse)),
                )
                // 후광도 같은 float 적용 → 별과 함께 떠오른다
                (style.getLayer(auraLayerId(g)) as? CircleLayer)?.setProperties(
                    PropertyFactory.circleTranslate(arrayOf(0f, floatDy))
                )
                // 바닥 빛 웅덩이는 지점 고정(시차의 기준점). 별이 내려와 가까워질수록 살짝 밝게.
                val downFrac = (wave + 1f) / 2f // 0(위)..1(아래)
                (style.getLayer(groundLightLayerId(g)) as? CircleLayer)?.setProperties(
                    PropertyFactory.circleOpacity(
                        Expression.product(
                            Expression.literal(GROUND_LIGHT_OPACITY * (0.7f + 0.3f * downFrac)),
                            Expression.get("alpha")
                        )
                    )
                )
                // 별 곁 마이크로 스파클 — 안쪽/바깥쪽 역방향 타원 궤도 + 별 부유 동기 + 트윙클.
                // 궤도는 icon-offset(step on sizeMult)으로 별 크기에 비례, 부유는 iconTranslate 로 동기.
                val sparkleZoom = sparkleZoomFactor(zoom)
                for (s in 0 until SPARKLE_SETS) {
                    val sl = style.getLayer(sparkleLayerId(s, g)) as? SymbolLayer ?: continue
                    if (sparkleZoom <= 0.01f) continue // 숨김 줌(iconSize 0) — 궤도 갱신 불필요
                    val speed = if (s == 0) 1.1f else -0.8f
                    val ang = t * speed + phase + s * 1.9f
                    val ux = kotlin.math.cos(ang)
                    val uy = sin(ang) * 0.55f
                    val op = 0.30f + 0.60f * ((sin(t * (2.4f + 0.35f * g) + phase + s * 2.1f) + 1f) / 2f)
                    sl.setProperties(
                        PropertyFactory.iconOffset(
                            sparkleOrbitOffsetExpression(s, ux, uy, sparkleZoom, screenDensity)
                        ),
                        PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                        PropertyFactory.iconOpacity(
                            Expression.product(Expression.literal(op), Expression.get("alpha"))
                        ),
                    )
                }
            }
            // 별가루 반짝임: 위상 그룹별 레이어 opacity 만 갱신 (GeoJSON 재생성 없음)
            for (g in 0 until PHASE_GROUPS) {
                val layer = style.getLayer(particleLayerId(g)) as? SymbolLayer ?: continue
                val phase = g * (2f * Math.PI.toFloat() / PHASE_GROUPS)
                val speed = 2.0f + g * 0.4f // 그룹마다 다른 주기 → 덜 기계적인 반짝임
                val twinkle = 0.25f + 0.75f * ((sin(t * speed + phase) + 1f) / 2f)
                layer.setProperties(
                    PropertyFactory.iconOpacity(particleOpacityExpression(twinkle))
                )
            }
            // 도로 글린트: 대시 위상을 흘려 빛 알갱이가 도로를 따라 흐른다.
            // 위상을 ROAD_GLINT_STEPS 단계로 양자화해 대시 아틀라스 캐시를 재사용(무한 패턴 생성 방지).
            val off = (t * ROAD_GLINT_SPEED) % ROAD_GLINT_GAP
            val q = (off / ROAD_GLINT_GAP * ROAD_GLINT_STEPS).toInt()
                .toFloat() / ROAD_GLINT_STEPS * ROAD_GLINT_GAP
            if (q != lastDashOffset) {
                lastDashOffset = q
                (style.getLayer(ROAD_GLINT_LAYER) as? LineLayer)?.setProperties(
                    PropertyFactory.lineDasharray(
                        arrayOf(0f, q, ROAD_GLINT_DASH, ROAD_GLINT_GAP - q)
                    )
                )
            }
            // 위상이 한 바퀴 돌아 처음으로 되돌아가는(알갱이가 다시 태어나는) 순간 전후
            // ROAD_GLINT_FADE_SEC 씩 부드럽게 사라졌다 나타나도록 불투명도에 삼각 envelope 를 곱한다.
            val cyclePos = t % ROAD_GLINT_PERIOD_SEC
            val envelope = when {
                cyclePos < ROAD_GLINT_FADE_SEC -> cyclePos / ROAD_GLINT_FADE_SEC
                cyclePos > ROAD_GLINT_PERIOD_SEC - ROAD_GLINT_FADE_SEC ->
                    (ROAD_GLINT_PERIOD_SEC - cyclePos) / ROAD_GLINT_FADE_SEC
                else -> 1f
            }.coerceIn(0f, 1f)
            if (envelope != lastGlintEnvelope) {
                lastGlintEnvelope = envelope
                (style.getLayer(ROAD_GLINT_LAYER) as? LineLayer)?.setProperties(
                    PropertyFactory.lineOpacity(roadGlintOpacityExpression(envelope))
                )
            }
            delay(50)
        }
    }

    // 현재 위치 마커 갱신
    LaunchedEffect(currentLatLng, mapRef) {
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
    }

    // 개척 퀘스트 비콘 갱신(체크리스트 32) — 개척 현황을 구독해 "등장했지만 미개척"인 나라만 표시.
    val pioneerClaims by remember {
        com.chaminwoo.stary.data.repository.FirebasePioneerRepository().observeClaims()
    }.collectAsState(initial = emptyMap())
    LaunchedEffect(pioneerClaims, pioneerSource) {
        val src = pioneerSource ?: return@LaunchedEffect
        val featured = com.chaminwoo.stary.shared.config.PioneerQuest
            .featuredCountries(System.currentTimeMillis(), pioneerClaims.keys)
        val features = featured.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lng, c.lat)).apply {
                addStringProperty("code", c.code)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // 최초 진입 시 내 위치로 카메라 1회 이동 — 스타일 로드 시점엔 아직 위치 fix 가 없어 기본 좌표로
    // 떠 있다. 실제 위치 fix 가 들어오면 그 위치로 카메라를 한 번 옮긴다(이후엔 사용자 조작 존중).
    var didAutoCenter by remember { mutableStateOf(false) }
    LaunchedEffect(currentLatLng, mapRef, focusDiary) {
        if (didAutoCenter) return@LaunchedEffect
        if (focusDiary != null) return@LaunchedEffect       // 포커스 요청이 카메라를 직접 다룬다
        val map = mapRef ?: return@LaunchedEffect
        if (LocationHelper.getCurrentLatLng() == null) return@LaunchedEffect // 실제 fix 대기(기본 좌표면 보류)
        didAutoCenter = true
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(currentLatLng.toMl()).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            ),
            700
        )
    }

    // 외부(알림) 요청: 특정 다이어리로 카메라 이동 후 열람 파장 1회 (세부 화면 이동 없음).
    LaunchedEffect(focusDiary, mapRef) {
        val target = focusDiary ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(MlLatLng(target.lat, target.lng)).zoom(DEFAULT_ZOOM)
                    .tilt(BASE_TILT_DEG).build()
            ),
            800,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    // 카메라가 다이어리를 화면 중앙에 둔 상태 → 파장 중심도 화면 중앙(0.5,0.5)
                    map.snapshot { bmp ->
                        warpState.value = DiaryOpenWarpData(
                            bmp, 0.5f, 0.5f, target.diaryId, target.colorIndex, navigateAfter = false
                        )
                    }
                    // 친구 별 탭(withRoute) → 파장과 함께 그 별까지 도보 길찾기 시작.
                    if (target.withRoute) requestRouteRef.value(target.lat, target.lng)
                }
                override fun onCancel() {
                    onFocusHandledRef.value()
                }
            }
        )
    }

    // 도보 길찾기 실시간 렌더 — 저장된 전체 경로에서 내 위치의 최근접점부터 목적지까지만 그린다.
    // 지나온 길(출발점~최근접점)은 숨기고, 내 위치가 경로에서 떨어져 있으면 최근접점까지 직선으로 잇는다.
    LaunchedEffect(currentLatLng, savedRoute, routeSource) {
        val src = routeSource ?: return@LaunchedEffect
        val full = savedRoute
        if (full == null || full.size < 2) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        val me = Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude)
        src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(partialRouteFrom(full, me))))
    }
}

/**
 * 지도 왜곡 연출 데이터 — 스냅샷 비트맵 + 파장 시작 위치(0..1) + 다이어리 id/별색.
 * [navigateAfter] true 면 파장 후 세부 화면으로(별 탭), false 면 파장만 내고 지도에 머문다(알림 포커스).
 */
private class DiaryOpenWarpData(
    val bitmap: Bitmap,
    val ox: Float,
    val oy: Float,
    val id: String,
    val colorIndex: Int,
    val navigateAfter: Boolean,
    /** 30m 안에서 합쳐진 별들의 (모양, 색) — 파장 중심에서 작은 파티클로 퍼진다(합쳐진 별 열람 시만). */
    val burstStars: List<Pair<Int, Int>> = emptyList(),
    /** 합쳐진 멤버 다이어리 id(우선순위 정렬) — 2개 이상이면 파장 후 카드 뷰어로 이동. */
    val clusterIds: List<String> = emptyList(),
)

/**
 * 다이어리 진입 직전 연출 — 캡처한 지도 스냅샷을 1.3초간 **별 위치에서 퍼지는 물결**로 굴절시킨 뒤 [onFinished].
 * (위아래 흔들림이 아니라 방사형 파장.) `drawBitmapMesh` 로 그려 소프트웨어 렌더(에뮬레이터)에서도 동작.
 * 세부 화면 자체는 왜곡 없이 멀쩡하게 들어간다.
 */
@Composable
private fun DiaryOpenWarp(data: DiaryOpenWarpData, onFinished: () -> Unit) {
    val progress = remember(data) { Animatable(0f) }
    LaunchedEffect(data) {
        progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        onFinished()
    }
    val p = progress.value
    val rippleColor = StarStyle.colorOf(data.colorIndex)
    val meshPaint = remember { Paint().apply { isFilterBitmap = true; isAntiAlias = true } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w * data.ox
        val cy = h * data.oy
        val maxR = max(
            max(hypot(cx, cy), hypot(w - cx, cy)),
            max(hypot(cx, h - cy), hypot(w - cx, h - cy))
        )
        val front = p * maxR
        val amp = 46f * (1f - p) // 파면이 퍼질수록 약해져 잔잔해짐

        // 스냅샷을 메시 격자로 그려 별 위치에서 방사형으로 굴절
        val mw = 14
        val mh = 14
        val verts = FloatArray((mw + 1) * (mh + 1) * 2)
        var i = 0
        for (row in 0..mh) {
            for (col in 0..mw) {
                val x = w * col / mw
                val y = h * row / mh
                val dx = x - cx
                val dy = y - cy
                val dist = hypot(dx, dy)
                val delta = dist - front
                val env = exp(-(delta * delta) / (220f * 220f)) // 넓은 밴드
                val disp = sin(delta * 0.045f) * env * amp
                if (dist > 0.001f) {
                    verts[i++] = x + dx / dist * disp
                    verts[i++] = y + dy / dist * disp
                } else {
                    verts[i++] = x
                    verts[i++] = y
                }
            }
        }
        drawIntoCanvas { c ->
            c.nativeCanvas.drawBitmapMesh(data.bitmap, mw, mh, verts, 0, null, 0, meshPaint)
        }

        // 합쳐진 별 파티클 — 파장 중심(별 위치)에서 각 멤버의 모양/색이 작은 별로 퍼져 나간다.
        if (data.burstStars.isNotEmpty() && p > 0.02f) {
            drawIntoCanvas { c ->
                val n = data.burstStars.size
                data.burstStars.forEachIndexed { i, (type, colorIdx) ->
                    // 황금비 시퀀스로 방향/거리/크기를 결정론적으로 흩뿌린다(매 프레임 동일).
                    val golden = (i * 0.61803398f) % 1f
                    val ang = (i.toFloat() / n) * 2f * Math.PI.toFloat() + golden * 0.9f
                    val dist = (70.dp.toPx() + golden * 90.dp.toPx()) * p
                    val x = cx + kotlin.math.cos(ang) * dist
                    val y = cy + sin(ang) * dist
                    val sizePx = (12f + golden * 8f).dp.toPx() * (1f - 0.35f * p)
                    val alpha = ((1f - p) * 1.4f).coerceIn(0f, 1f)
                    if (alpha <= 0.01f) return@forEachIndexed
                    val color = StarStyle.colorOf(colorIdx).copy(alpha = alpha).toArgb()
                    val path = android.graphics.Path(StarStyle.starPath(type, sizePx)).apply {
                        offset(x - sizePx / 2f, y - sizePx / 2f)
                    }
                    c.nativeCanvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        maskFilter = android.graphics.BlurMaskFilter(4.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    })
                    StarStyle.drawCrystalFill(
                        c.nativeCanvas, type, colorIdx,
                        x - sizePx / 2f, y - sizePx / 2f, sizePx,
                        alpha = (alpha * 255).toInt()
                    )
                }
            }
        }

        // 파장 링 — 별 위치에서 퍼지는 빛 테두리 (+ 강한 후광)
        if (p < 1f && front >= 1f) {
            val center = Offset(cx, cy)
            val radius = front
            val fade = 1f - p
            // 0) 강한 후광 링 — 블러 처리한 굵은 스트로크. 링과 같은 반지름이라 함께 퍼진다.
            drawIntoCanvas { c ->
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (22f * fade).coerceAtLeast(3f).dp.toPx()
                    color = rippleColor.copy(alpha = (fade * 0.7f).coerceIn(0f, 1f)).toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(20.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.nativeCanvas.drawCircle(cx, cy, radius, glow)
            }
            // 1) 넓은 굴절 띠
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, rippleColor.copy(alpha = fade * 0.22f), Color.Transparent),
                    center = center, radius = radius.coerceAtLeast(1f)
                ),
                radius = radius, center = center,
                style = Stroke(width = (30f * fade).coerceAtLeast(1f).dp.toPx())
            )
            // 2) 밝은 가장자리 선
            drawCircle(
                color = rippleColor.copy(alpha = fade * 0.7f),
                radius = radius, center = center,
                style = Stroke(width = (3f * fade).coerceAtLeast(0.6f).dp.toPx())
            )
        }
    }
}
