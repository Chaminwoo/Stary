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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
// 별자리 선 페이드용 최대 불투명도 (켜질 때 0→target 으로 부드럽게)
private const val CONSTELLATION_HALO_OPACITY = 0.18f
private const val CONSTELLATION_GLOW_OPACITY = 0.42f
private const val CONSTELLATION_LINE_OPACITY = 0.95f
/** 별자리: 각 별을 화면상 가장 가까운 별 몇 개와 연결. */
private const val CONSTELLATION_NEIGHBORS = 2

/** 인기 별의 글로우 오오라(CircleLayer) id — 위상 그룹별(별과 같은 float 적용). */
private fun auraLayerId(group: Int) = "diary-aura-$group"
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
 * 구성: 팔레트색 글로우(blur) + 팔레트색 본체 + 흰색 중심 하이라이트.
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

    // 2) 본체 (그라데이션 색이면 셰이더로 채움)
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val shader = StarStyle.fillShader(colorIdx, offset, offset, starSize)
        if (shader != null) this.shader = shader else this.color = color
    })

    // 3) 중심 코어: 원색보다 살짝 어두운 톤(35% 어둡게) + 65% 크기 —
    //    흰색/작은 코어는 본체와 분리돼 보였음. 어두운 코어가 글로우와 한 덩어리로 빛나는 인상.
    val coreColor = androidx.core.graphics.ColorUtils.blendARGB(color, AndroidColor.BLACK, 0.05f)
    val centerPath = android.graphics.Path(path)
    val m = android.graphics.Matrix().apply { setScale(0.8f, 0.8f, side / 2f, side / 2f) }
    centerPath.transform(m)
    canvas.drawPath(centerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = coreColor })
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
    // 화면에 실제 표시되는 대표 별만 사용
    val reps = clusterTopLiked(map, valid, radiusPx).reps
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
data class DiaryFocusTarget(
    val lat: Double,
    val lng: Double,
    val colorIndex: Int,
    val diaryId: String,
)

@Composable
fun DiaryMap(
    diaries: List<Diary>,
    currentLatLng: LatLng,
    onDiaryClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusDiary: DiaryFocusTarget? = null,
    onFocusHandled: () -> Unit = {},
    showCreate: Boolean = true, // 비로그인 시 다이어리 생성(업로드) 버튼 숨김
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
    val addedIcons = remember { mutableSetOf<String>() }
    val isCameraMoving = remember { mutableStateOf(false) }
    // 카메라가 멈출 때마다 증가 → 줌/이동에 따라 화면 클러스터링 재계산 트리거
    var cameraIdleTick by remember { mutableStateOf(0) }
    val clusterRadiusPx = remember { CLUSTER_RADIUS_DP * context.resources.displayMetrics.density }
    // 별자리 최대 연결 거리(px) — 화면 짧은 변의 절반 정도까지만 이어 과한 장거리 연결 방지
    val constellationMaxLinkPx = remember {
        context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) * 0.55f }
    }
    var constellationEnabled by remember { mutableStateOf(false) }
    // 배경음악은 앱 전역(MusicManager, MainScreen 에서 생명주기 관리)에서 처리. 여기선 FAB 토글만.

    // 다이어리 진입 직전 "지도 왜곡" 연출 — 탭 시 지도 스냅샷을 떠서 1초간 파장+울렁 후 세부 화면 이동.
    val warpState = remember { mutableStateOf<DiaryOpenWarpData?>(null) }

    val onDiaryClickRef = rememberUpdatedState(onDiaryClick)
    val onFocusHandledRef = rememberUpdatedState(onFocusHandled)
    val currentLatLngRef = rememberUpdatedState(currentLatLng)
    val diariesRef = rememberUpdatedState(diaries)
    val initialLatLngRef = rememberUpdatedState(currentLatLng)

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
                map.addOnCameraMoveStartedListener { isCameraMoving.value = true }
                map.addOnCameraIdleListener {
                    isCameraMoving.value = false
                    cameraIdleTick++
                }
                // 별 클릭 → 100m 게이팅 (길찾기 기능은 제거됨 — 밖이면 안내 토스트만)
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val features = map.queryRenderedFeatures(screen, *DIARY_LAYER_IDS)
                    val id = features.firstOrNull()?.getStringProperty("id")
                    if (id != null) {
                        val diary = diariesRef.value.firstOrNull { it.id == id }
                        if (diary != null) {
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
                                // 현재 지도를 스냅샷으로 떠서, 그 이미지를 1초간 왜곡(파장+울렁)한 뒤 세부 화면으로 이동
                                map.snapshot { bmp ->
                                    // 열람 애니메이션(파장) 시작과 동시에 열람 효과음 재생
                                    com.chaminwoo.stary.core.util.MusicManager.playOpenDiary()
                                    warpState.value = DiaryOpenWarpData(bmp, ox, oy, id, diary.starColor, navigateAfter = true)
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

                    // 다이어리 별 마커 source — 클러스터링은 클라이언트(화면 좌표)에서 처리.
                    val dSrc = GeoJsonSource(DIARY_SOURCE, FeatureCollection.fromFeatures(emptyList()))
                    style.addSource(dSrc)

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

                    diarySource = dSrc

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
                            .build()
                    } else {
                        LocationHelper.cameraTarget = null
                        val bounds = LatLngBounds.Builder()
                            .include(start.toMl())
                            .include(MlLatLng(target.latitude, target.longitude))
                            .build()
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                    }
                    styleRef = style
                    mapRef = map
                }
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
                onClick = {
                    mapRef?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(currentLatLng.toMl()).zoom(DEFAULT_ZOOM).build()
                        )
                    )
                },
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

        // 지도 왜곡 연출 — 스냅샷 이미지를 1초간 파장+울렁시킨 뒤 세부 화면으로 이동(세부는 멀쩡).
        warpState.value?.let { wd ->
            DiaryOpenWarp(wd) {
                warpState.value = null
                if (wd.navigateAfter) {
                    // 별 탭(100m 이내) → 파장 후 세부 화면으로
                    MapUiState.exitMapOnly()
                    onDiaryClickRef.value(wd.id)
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
        val byId = valid.associateBy { it.id }

        // 화면 좌표 기준 클러스터링 → 대표(최다 좋아요) + 배정표
        val result = clusterTopLiked(map, valid, clusterRadiusPx)
        val reps = result.reps
        val assignment = result.assignment
        val nearIds = reps.filter { d ->
            LocationHelper.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude, d.latitude, d.longitude
            ) <= StaryConfig.DIARY_OPEN_RADIUS_M
        }.map { it.id }.toSet()
        // 대표별 클러스터 크기(흡수된 다이어리 수, 자기 포함) → 크기 보너스
        val clusterCount = assignment.values.groupingBy { it }.eachCount()
        fun repSizeMult(d: Diary): Float =
            likeSizeMult(d.likeCount) * clusterSizeBoost(clusterCount[d.id] ?: 1)

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
            val feats = ArrayList<Feature>(valid.size)
            for (d in valid) {
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
                // 대표로 정착하는 별만 클러스터 보너스 적용(흡수되는 별은 기본 크기로 페이드)
                val sm = if (toRep == d.id) repSizeMult(d) else likeSizeMult(d.likeCount)
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
                val floatDy = (sin(t * 1.6f + phase) * 3f * zoomAmp) // 최대 -3..3 dp, 줌 작으면 축소
                val pulse = 1f + 0.20f * ((sin(t * 3.2f + phase) + 1f) / 2f) // 1.0..1.2 맥동
                layer.setProperties(
                    PropertyFactory.iconTranslate(arrayOf(0f, floatDy)),
                    PropertyFactory.iconSize(starSizeExpression(pulse)),
                )
                // 후광도 같은 float 적용 → 별과 함께 떠오른다
                (style.getLayer(auraLayerId(g)) as? CircleLayer)?.setProperties(
                    PropertyFactory.circleTranslate(arrayOf(0f, floatDy))
                )
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
            delay(50)
        }
    }

    // 현재 위치 마커 갱신
    LaunchedEffect(currentLatLng, mapRef) {
        locationSource?.setGeoJson(Point.fromLngLat(currentLatLng.longitude, currentLatLng.latitude))
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
                CameraPosition.Builder().target(currentLatLng.toMl()).zoom(DEFAULT_ZOOM).build()
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
                CameraPosition.Builder().target(MlLatLng(target.lat, target.lng)).zoom(DEFAULT_ZOOM).build()
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
                }
                override fun onCancel() {
                    onFocusHandledRef.value()
                }
            }
        )
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
