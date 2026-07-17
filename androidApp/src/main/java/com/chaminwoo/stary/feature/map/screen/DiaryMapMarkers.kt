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

internal const val DEFAULT_ZOOM = 15.0
internal const val CURRENT_SOURCE = "current-location"
internal const val DIARY_SOURCE = "diaries"
internal const val STAR_PARTICLE_SOURCE = "star-particles"
internal const val PARTICLE_ICON_ID = "star-particle-dot"
internal const val CONSTELLATION_SOURCE = "constellation-lines"
internal const val CONSTELLATION_LAYER = "constellation-layer"
internal const val CONSTELLATION_GLOW_LAYER = "constellation-glow-layer"
internal const val CONSTELLATION_HALO_LAYER = "constellation-halo-layer"
internal const val ROUTE_SOURCE = "walking-route"
internal const val ROUTE_LAYER = "walking-route-layer"
internal const val ROUTE_GLOW_LAYER = "walking-route-glow-layer"
internal const val ROUTE_HALO_LAYER = "walking-route-halo-layer"
// 도로 글린트 — 대시 위상을 흘려 빛이 도로를 따라 흐른다.
// ⚠️ dash/gap 값(선 두께 배수)은 maplibre_style.json 의 line-dasharray 와 일치해야 한다.
internal const val ROAD_GLINT_LAYER = "road-glint"
internal const val ROAD_GLINT_DASH = 0.5f
internal const val ROAD_GLINT_GAP = 34f
internal const val ROAD_GLINT_SPEED = 8.5f // 초당 위상 이동(선 두께 배수)
internal const val ROAD_GLINT_STEPS = 40   // 위상 양자화 단계(대시 아틀라스 캐시 재사용)
internal const val ROAD_GLINT_FADE_SEC = 0.2f
internal val ROAD_GLINT_PERIOD_SEC = ROAD_GLINT_GAP / ROAD_GLINT_SPEED // 한 바퀴(초)
internal const val CONSTELLATION_HALO_OPACITY = 0.18f
internal const val CONSTELLATION_GLOW_OPACITY = 0.42f
internal const val CONSTELLATION_LINE_OPACITY = 0.95f
/** 별자리: 각 별을 화면상 가장 가까운 별 몇 개와 연결. */
internal const val CONSTELLATION_NEIGHBORS = 2

/** 인기 별의 글로우 오오라(CircleLayer) id — 위상 그룹별(별과 같은 float 적용). */
internal fun auraLayerId(group: Int) = "diary-aura-$group"
/** 바닥 빛 웅덩이 id — 별과 달리 지점 고정이라 부유 시차로 "떠 있음"이 읽힌다. */
internal fun groundLightLayerId(group: Int) = "diary-ground-light-$group"
internal const val GROUND_LIGHT_OPACITY = 0.30f
internal const val GROUND_LIGHT_OFFSET_Y = 8f // 별 중심보다 살짝 아래(지면 쪽)에 고인 빛
/** 화면상 클러스터 반경(dp). 이 거리 안에서 겹치는 별은 가장 좋아요 많은 별 하나로 합쳐 표시. */
internal const val CLUSTER_RADIUS_DP = 4f
/** 좋아요 수 → 크기 배율 상한(좋아요 100개에서 3배). */
internal const val LIKES_FOR_MAX_SIZE = 100
internal const val MAX_LIKE_SIZE_MULT = 3f

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

/** 3D 글로브 "지구 보기" 버튼 노출 줌(이하로 줌아웃하면 버튼 표시 — 자동 전환 없음) / 지도 최소 줌. */
internal const val GLOBE_BUTTON_ZOOM = 3.0
internal const val MAP_MIN_ZOOM = 2.4
/** 웹메르카토르 타일이 존재하는 위도 한계 — 이 밖은 지도가 없어 빈 공간이 보인다(카메라 클램프 기준). */
internal const val MERCATOR_MAX_LAT = 85.05112877980659
/** 대기 헤이즈 시작 줌 — 이 줌부터 [MAP_MIN_ZOOM] 까지 내려갈수록 파란 대기가 차올라 글로브 장면과 이어진다. */
internal const val HAZE_START_ZOOM = 4.4

/** 기본 카메라 틸트(도) — 줌과 무관하게 항상 살짝 기울여 입체감을 낸다. */
internal const val BASE_TILT_DEG = 25.0

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
internal fun starSizeExpression(pulse: Float): Expression {
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
    return Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(6f, sized(0.06f)),
        Expression.stop(10f, sized(0.2f)),
        Expression.stop(13f, sized(0.5f)),
        Expression.stop(15f, sized(1f)),
    )
}

/** 인기 별 오오라 반경: 줌 보간 × sizeMult (zoom 최상위 규칙은 [starSizeExpression] 참고). */
internal fun auraRadiusExpression(): Expression {
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
internal fun groundLightRadiusExpression(): Expression {
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

/** 오오라 불투명도: sizeMult 보간 — 인기 별만 두드러지게 발광한다. */
internal fun auraOpacityExpression(): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.get("sizeMult"),
        Expression.stop(1f, 0f),
        Expression.stop(1.4f, 0.12f),
        Expression.stop(3f, 0.42f),
    )

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
 * 다이어리 별 주위를 도는 마이크로 스파클 — 세트 3개(별 크기에 따라 개수가 늘어난다):
 *  - set 0 : 안쪽 궤도. 모든 별에 항상 1개.
 *  - set 1 : 바깥 궤도(역방향). 별이 좀 커지면([SPARKLE_SET_MIN_SIZE]) 추가돼 2개가 된다.
 *  - set 2 : **위성** — 별이 아니라 set 1 파티클을 부모로 삼아 그 주위를 도는 작은 반짝이(주전원).
 *            더 큰 별에서만 등장. 항상 흰 4꼭지(작은 달 느낌).
 */
internal const val SPARKLE_SETS = 3
internal const val SPARKLE_SATELLITE_SET = 2
internal const val SPARKLE_ICON_ID = "diary-sparkle-icon"
internal fun sparkleLayerId(set: Int, group: Int) = "diary-sparkle-$set-$group"
internal fun sparkleStarIconId(type: Int, color: Int) = "sparkle-star-t$type-c$color"

/** 세트별 등장 최소 sizeMult — 이 값 미만인 별에서는 그 세트가 숨겨진다(작을 땐 1개 → 2개 → +위성). */
internal fun sparkleSetMinSize(set: Int): Float = when (set) {
    0 -> 1f      // 항상
    1 -> 1.6f    // 좀 큰 별 → 2개
    else -> 2.6f // 더 큰 별 → 위성까지
}

/** 스파클 iconSize 기본 배율(세트별). 위성(set 2)은 부모보다 확실히 작아야 "달"로 읽힌다. */
internal fun sparkleSizeBase(set: Int) = when (set) {
    0 -> 0.90f
    1 -> 0.68f
    else -> 0.42f
}

/** 마커 곁 스파클 비트맵 — 작은 4꼭지 별(글로우+본체). 작은/보통 별 곁에서 쓰는 기본 반짝이. */
internal fun sparkleBitmap(): Bitmap {
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
 * "큰 별" 곁 스파클 — 그 별과 같은 모양/색의 미니 크리스탈.
 * ⚠️ [sparkleBitmap] 과 같은 32px 캔버스 유지 — 두 아이콘을 데이터 주도로 섞어도 크기가 안 어긋난다.
 */
internal fun sparkleStarBitmap(type: Int, colorIdx: Int): Bitmap {
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
internal const val SPARKLE_SIZE_POW = 0.8f

/** 스파클 iconSize — 줌 11 이하 완전 숨김, sizeMult^[SPARKLE_SIZE_POW] 로 큰 별 곁일수록 커진다. */
internal fun sparkleSizeExpression(set: Int): Expression {
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
internal fun sparkleZoomFactor(zoom: Float): Float = when {
    zoom <= 11f -> 0f
    zoom <= 13f -> (zoom - 11f) / 2f * 0.55f
    zoom <= 15.5f -> 0.55f + (zoom - 13f) / 2.5f * 0.45f
    else -> 1f
}

/**
 * 스파클 궤도 목표 반경(dp) — "항상 별 바로 곁에서 작게 도는" 의도를 dp 로 직접 못박는다.
 * sizeMult 로그 성장(폭주 방지). 위성(set 2)은 [satelliteOrbitDp] 를 쓴다.
 */
internal fun orbitTargetDp(set: Int, sizeMult: Float): Float {
    val (base, growth) = if (set == 0) 5f to 3.2f else 7.5f to 4.6f
    return base + growth * kotlin.math.ln(sizeMult.coerceAtLeast(1f))
}

/** 위성(set 2)이 **부모 파티클(set 1) 주위**를 도는 주전원 반경(dp) — 작게 유지해야 "달"로 읽힌다. */
internal fun satelliteOrbitDp(sizeMult: Float): Float =
    3.2f + 1.5f * kotlin.math.ln(sizeMult.coerceAtLeast(1f))

/**
 * 스파클 iconOffset — step(sizeMult) 티어로 양자화한 목표 오프셋(dp).
 * ⚠️ offset 단위 = 스프라이트 px × icon-size ÷ 밀도로 표시된다 —
 * dp × density ÷ (해당 레이어 icon-size) 로 환산해야 기기 밀도와 무관하게 목표 dp 에 놓인다.
 */
internal fun sparkleOffsetExpression(
    set: Int, sizeZoomFactor: Float, density: Float, dpAt: (Float) -> Pair<Float, Float>,
): Expression {
    fun offsetFor(tier: Float): Expression {
        val (dxDp, dyDp) = dpAt(tier)
        val spriteScale = (
            sparkleSizeBase(set) * sizeZoomFactor *
                Math.pow(tier.toDouble(), SPARKLE_SIZE_POW.toDouble()).toFloat()
            ).coerceAtLeast(0.0001f)
        val k = density / spriteScale
        return Expression.literal(arrayOf<Any>(dxDp * k, dyDp * k))
    }
    // 티어 경계는 mergeSizeMult 실효 범위(1..6.6)를 커버
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
internal const val SPARKLE_BIG_STAR_THRESHOLD = 1.75f

// ─── 겹친 별 위성 — 30m 머지 그룹의 숨은 멤버 별들이 대표 별 곁에서 함께 둥둥 부유 ───
// 대표만 보이면 가 보기 전엔 겹친 별인지 알 수 없다 → 멤버 별(각자 자기 모양/색의 미니 크리스탈)을
// 대표 곁 고정 자리에 띄워 "여러 별이 겹쳐 있음"이 멀리서도 읽히게 한다. 회전(공전) 없이
// 저마다의 결로 잔잔히 떠다닌다. 스파클보다 확실히 크고 트윙클 없이 또렷하게 — "별"로 보이도록.
internal const val ORBIT_SOURCE = "merged-orbit-source"
/** 위성 레이어 id — satIndex × phaseGroup. 대표 별과 같은 phaseGroup 레이어라야 같은 float(iconTranslate)를 공유해 "함께" 움직인다. */
internal fun orbitLayerId(satIndex: Int, group: Int) = "merged-orbit-$satIndex-$group"
internal fun orbitStarIconId(type: Int, color: Int) = "orbit-star-t$type-c$color"

/** 대표 곁에 띄우는 멤버 별 최대 수(대표 제외). 그 이상은 대표 크기 보너스가 대신 말해준다. */
internal const val MAX_ORBIT_STARS = 4

/** 위성 스프라이트 변(px). 4의 배수(GL 행 정렬). */
internal const val ORBIT_STAR_SIDE_PX = 64

/** 위성 iconSize 기준 배율(줌 15) — 별 본체의 ~40% 크기. 스파클보다 두 배쯤 커 "별"로 읽힌다. */
internal const val ORBIT_STAR_BASE_SIZE = 0.76f

/** 위성 고정 배치각(rad) — 대표 주위에 비대칭으로 흩어 기계적인 등간격을 피한다. */
internal val ORBIT_ANCHOR_ANGLES = floatArrayOf(-0.6f, 2.3f, 4.1f, 1.1f)

/** 위성 미니 크리스탈 비트맵 — 그 멤버 별의 모양/색 그대로(글로우 + 크리스탈 패싯). */
internal fun orbitStarBitmap(type: Int, colorIdx: Int): Bitmap {
    val color = StarStyle.colorOf(colorIdx).toArgb()
    val side = ORBIT_STAR_SIDE_PX
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val starSize = side * 0.70f
    val offset = (side - starSize) / 2f
    val path = android.graphics.Path(StarStyle.starPath(type, starSize)).apply { offset(offset, offset) }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    StarStyle.drawCrystalFill(canvas, type, colorIdx, offset, offset, starSize)
    return out
}

/**
 * 위성 iconSize — 별 크기 곡선(줌 13→15 구간)과 같은 결로 커지고, 줌 11 이하에선 사라진다
 * (멀리서 겹침 식별이 필요한 건 "가 볼 만한" 줌부터라 저줌 잡동사니를 막는다).
 * per-feature 배율 없이 레이어 균일 — [orbitOffsetExpression] 의 dp→offset 환산이 정확해진다.
 */
internal fun orbitSizeExpression(): Expression = Expression.interpolate(
    Expression.linear(), Expression.zoom(),
    Expression.stop(11f, 0f),
    Expression.stop(13f, ORBIT_STAR_BASE_SIZE * 0.5f),
    Expression.stop(15f, ORBIT_STAR_BASE_SIZE),
)

/**
 * 위성 배치 반경(dp, 줌 15 기준) — 대표 별 바로 곁(가장자리와 겹치도록)에 붙이고,
 * 위성 인덱스마다 아주 조금씩만 바깥으로. 별이 커지면 배치도 함께 넓어진다.
 */
internal fun orbitRadiusDp(sizeMult: Float, satIndex: Int): Float =
    5f * sizeMult + 0.5f + satIndex * 1.6f

/**
 * 위성 iconOffset — 고정 배치각([ORBIT_ANCHOR_ANGLES]) 자리(타원 배치, y×0.55)에
 * [driftXDp]/[driftYDp](둥둥 부유)를 더해 sizeMult 티어(step)로 양자화해 돌려준다.
 * dp→offset 단위 환산은 [sparkleOffsetExpression] 규칙과 동일: units = dp × density / iconSize(줌15).
 * iconSize 가 줌을 따라 줄면 표시 오프셋도 같은 비율로 줄어 배치가 별 크기와 함께 스케일된다.
 */
internal fun orbitOffsetExpression(satIndex: Int, driftXDp: Float, driftYDp: Float, density: Float): Expression {
    val k = density / ORBIT_STAR_BASE_SIZE
    val ang = ORBIT_ANCHOR_ANGLES[satIndex]
    fun at(tier: Float): Expression {
        val r = orbitRadiusDp(tier, satIndex)
        return Expression.literal(
            arrayOf<Any>(
                (kotlin.math.cos(ang) * r + driftXDp) * k,
                (sin(ang) * 0.55f * r + driftYDp) * k,
            )
        )
    }
    // 티어 경계는 스파클(sparkleOffsetExpression)과 동일 — mergeSizeMult 실효 범위 커버
    return Expression.step(
        Expression.get("sizeMult"), at(1f),
        Expression.stop(1.25f, at(1.5f)),
        Expression.stop(1.75f, at(2f)),
        Expression.stop(2.35f, at(2.75f)),
        Expression.stop(3.1f, at(3.5f)),
        Expression.stop(4f, at(4.5f)),
        Expression.stop(5f, at(5.5f)),
    )
}

/**
 * 위성 Feature 목록 — 화면에 정착한 대표별로, 머지 그룹의 나머지 멤버(우선순위순 최대
 * [MAX_ORBIT_STARS])를 대표 좌표 위에 올린다(배치·부유는 iconOffset 이 그린다).
 * phaseGroup 은 대표 별과 동일하게 부여해, 같은 레이어의 iconTranslate(float)를 그대로 물려받는다.
 */
internal fun orbitFeatures(
    reps: List<Diary>,
    groups: Map<String, List<Diary>>,
    sizeMultOf: (Diary) -> Float,
): FeatureCollection {
    val feats = ArrayList<Feature>()
    for (rep in reps) {
        val members = groups[rep.id] ?: continue
        if (members.size < 2) continue
        val mult = sizeMultOf(rep)
        val phaseGroup = kotlin.math.abs(rep.id.hashCode()) % PHASE_GROUPS
        members.drop(1).take(MAX_ORBIT_STARS).forEachIndexed { i, m ->
            feats.add(
                Feature.fromGeometry(Point.fromLngLat(rep.longitude, rep.latitude)).apply {
                    addNumberProperty("satIndex", i)
                    addNumberProperty("phaseGroup", phaseGroup)
                    addNumberProperty("sizeMult", mult)
                    addStringProperty(
                        "icon",
                        orbitStarIconId(
                            m.starType.coerceIn(0, StarStyle.TYPE_COUNT - 1),
                            m.starColor.coerceIn(0, StarStyle.COLOR_COUNT - 1),
                        )
                    )
                }
            )
        }
    }
    return FeatureCollection.fromFeatures(feats)
}

/**
 * 스파클 iconOpacity — [alpha](합쳐짐 보간) × [op](트윙클) × **세트 등장 게이트**.
 * 게이트는 sizeMult 가 [sparkleSetMinSize] 미만이면 0 → 작은 별엔 1개, 좀 크면 2개,
 * 더 크면 위성까지 나타난다(레이어는 그대로 두고 데이터 주도로 켜고 끈다).
 */
internal fun sparkleOpacityExpression(set: Int, op: Float): Expression {
    val min = sparkleSetMinSize(set)
    val gate: Expression =
        if (min <= 1f) Expression.literal(1f)
        else Expression.step(
            Expression.get("sizeMult"), Expression.literal(0f),
            Expression.stop(min, Expression.literal(1f)),
        )
    return Expression.product(Expression.literal(op), Expression.get("alpha"), gate)
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
 * road-glint lineOpacity — [envelope](위상 순환 페이드)를 곱해 루프에서 갱신.
 * ⚠️ zoom 스톱은 maplibre_style.json 의 road-glint 스톱과 일치해야 한다.
 */
internal fun roadGlintOpacityExpression(envelope: Float): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(13f, 0f),
        Expression.stop(15f, 0.55f * envelope),
        Expression.stop(17f, 0.7f * envelope),
    )

/**
 * 별자리 라인 GeoJSON — 뷰포트에 보이는 대표 별들을 가장 가까운
 * [CONSTELLATION_NEIGHBORS] 개와 잇는다([maxLinkPx] 이내, 카메라 idle 마다 재계산).
 */
internal fun buildConstellationFeatures(
    map: MapLibreMap,
    diaries: List<Diary>,
    radiusPx: Float,
    maxLinkPx: Float,
): FeatureCollection {
    val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (valid.size < 2) return FeatureCollection.fromFeatures(emptyList())
    // 마커와 동일한 순서(30m 머지 → 화면 클러스터링)로 실제 표시되는 대표만 사용
    val reps = clusterTopLiked(map, mergeByProximity(valid).reps, radiusPx).reps
    val bounds = map.projection.visibleRegion.latLngBounds
    val visible = reps.filter { bounds.contains(MlLatLng(it.latitude, it.longitude)) }
    if (visible.size < 2) return FeatureCollection.fromFeatures(emptyList())

    val screen = visible.map { map.projection.toScreenLocation(MlLatLng(it.latitude, it.longitude)) }
    val maxLink2 = maxLinkPx * maxLinkPx
    val edges = HashSet<Long>() // i<j 를 i*N+j 로 인코딩해 중복 제거
    val lines = mutableListOf<Feature>()
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

/**
 * 저장된 전체 경로 [full] 에서 현재 위치 [me] 의 최근접 투영점을 찾아
 * "[me] → 최근접점 → 그 이후 ~ 목적지" 좌표열을 만든다(지나온 구간은 제외).
 * 위경도를 경도 cos(위도) 보정 평면으로 근사(도보 거리에선 충분히 정확).
 */
internal fun partialRouteFrom(full: List<Point>, me: Point): List<Point> {
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
    out.add(me)
    out.add(Point.fromLngLat(cLng, cLat))
    for (j in bestK + 1 until full.size) out.add(full[j])
    return out
}
