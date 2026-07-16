package com.chaminwoo.stary.feature.profile.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.feature.profile.ParticleEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** 떠다니는 통계 아이콘 한 개. (좋아요=하트 / 친구=사람 / 다이어리=책 / 편지=채팅 / 별=핀 다이어리) */
data class StatBubble(
    val icon: ImageVector,
    val count: Int,
    val color: Color,
    val label: String,
    /** 탭하면 작은 아이콘들이 파티클처럼 퍼졌다 사라짐(하트용). */
    val burstOnTap: Boolean = false,
    /** false 면 드래그 시 수 대신 라벨만 표시(편지/별 등 수가 의미 없을 때). */
    val showCount: Boolean = true,
    /** >=0 이면 벡터 아이콘 대신 StarStyle 별 모양으로 그린다(핀한 내 다이어리). */
    val starType: Int = -1,
    val starColorIndex: Int = -1,
    /** null 이 아니면 히든 업적 아이콘 — 오라 파티클 + 클릭/드래그 시 화려한 버스트·잔상. */
    val hiddenEffect: ParticleEffect? = null,
)

/** 탭 시 잠깐 퍼지는 파티클(작은 아이콘 + 후광). */
private class Particle(
    var pos: Offset,
    var vel: Offset,
    var life: Float,        // 1 → 0
    val painterIdx: Int,
    val color: Color,
    val spin: Float,
)

/** 물리 상태를 가진 아이콘. idle 일 땐 anchorBase 주위로 떠다니고, 잡거나 날아갈 땐 vel 로 움직인다. */
private class Body(
    var pos: Offset,
    var vel: Offset = Offset.Zero,
    var anchorBase: Offset,
    val phase: Float,
    val floatAmp: Float,
    val floatSpeed: Float,
    var rot: Float,
    val rotIdleSpeed: Float, // deg/s
    var floatT0: Float = 0f,
    /** 히든 아이콘 잔상(afterimage) 최근 위치들. */
    val trail: ArrayDeque<Offset> = ArrayDeque(),
)

private const val FLING_DAMP = 1.7f        // 감속(초당 지수). 던지면 그 방향으로 부드럽게 미끄러지다 멈춤
private const val WALL_REST = 0.72f        // 벽 반발(튕김 시 에너지 손실)
private const val BALL_REST = 0.82f        // 아이콘끼리 반발
private const val STOP_SPEED = 10f         // px/s 미만이면 정지로 간주
private const val MAX_FLING = 3600f        // 초기 속도 상한(과도한 드래그 방지)

private fun Offset.dot(o: Offset): Float = x * o.x + y * o.y
private fun Offset.clampLen(max: Float): Offset {
    val d = getDistance()
    return if (d > max && d > 0f) this * (max / d) else this
}
/** 현재 각도에서 가장 가까운 똑바른(0/360°) 각. */
private fun nearestUpright(deg: Float): Float = (deg / 360f).roundToInt() * 360f

/** 수가 많을수록 더 화려하게 — 크기 배율(1.0 ~ 약 1.5). 좋아요/조회 등 큰 수일수록 커진다. */
private fun richness(count: Int): Float = 1f + 0.5f * (1f - exp(-count / 90f))

/**
 * 통계(좋아요/친구/다이어리)를 떠다니는 아이콘으로 보여주고 **물리적으로** 다룬다.
 * - 잡으면 커지고 회전이 **똑바로 1회 정렬**되며, 그 위에 해당 수가 뜬다. 잡은 위치가 **기준점**.
 * - 놓으면 기준점 방향으로 **빠르게 날아가다 천천히 멈추되**(기준점보다 조금 더), 벽/다른 아이콘에 닿으면 **튕긴다**.
 * - 물리는 **잡았거나 이동 중일 때만** 계산해 평상시 랙을 줄인다(그 외엔 가벼운 부유만).
 */
@Composable
fun FloatingStatBox(
    items: List<StatBubble>,
    modifier: Modifier = Modifier,
    onTap: (index: Int) -> Unit = {},
    /** 이 y(세로 비율)의 프로필 사진·이름을 가리지 않도록 그 주변을 피해 랜덤 배치한다. */
    avoidCenterYFraction: Float = 0.5f,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val painters: List<VectorPainter> = items.map { rememberVectorPainter(it.icon) }
    // 벡터 아이콘도 별과 같은 크리스탈 파편으로 채운다. 무늬는 정적이므로 비트맵으로 한 번만 구워 두고
    // 매 프레임엔 스케일·회전해 그리기만 한다(파티클까지 파편을 매 프레임 그리면 비싸다).
    // 굽는 해상도는 아이콘 기본 크기(40dp)의 2배 — richness(≤1.5)·잡기 확대에도 뭉개지지 않게.
    val bakePx = with(density) { 80.dp.toPx() }.roundToInt().coerceIn(48, 320)
    val crystalIcons: List<ImageBitmap> = painters.mapIndexed { i, painter ->
        val color = items[i].color
        remember(painter, color, bakePx) { bakeCrystalIcon(painter, color, seed = i, sizePx = bakePx, layoutDirection = layoutDirection) }
    }
    // 핀 다이어리 별도 같은 이유로 비트맵 1회 베이크(매 프레임 크리스탈 파편 재생성 방지)
    val starIcons: List<ImageBitmap?> = items.map { item ->
        remember(item.starType, item.starColorIndex, bakePx) {
            if (item.starType >= 0) bakeCrystalStar(item.starType, item.starColorIndex, bakePx) else null
        }
    }
    val n = items.size
    // 물리 루프(재시작 안 함)에서도 최신 count 를 읽어 화려함(크기)에 반영.
    val liveItems by rememberUpdatedState(items)

    // 전체 화면을 덮는 오버레이 — 아이콘이 탑바 하단~화면 바닥, 양옆 끝까지 자유롭게 날아다니고 그 경계에서 튕긴다.
    // (아이콘 근처가 아닌 터치는 소비하지 않아 아래 콘텐츠가 그대로 눌린다.)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val iconPx = with(density) { 40.dp.toPx() }
        val rad = iconPx / 2f

        val bodies = remember(n, wPx, hPx, avoidCenterYFraction) {
            val rnd = kotlin.random.Random(n * 977 + 3)
            // 중앙(프로필/이름) 회피 타원 + 하단 카드 영역 위쪽에 서로 너무 겹치지 않게 랜덤 배치.
            val cx = wPx * 0.5f
            val cy = hPx * avoidCenterYFraction
            val avoidRx = wPx * 0.44f
            val avoidRy = hPx * 0.20f
            val top = rad + hPx * 0.03f
            val bottom = hPx * 0.80f
            val placed = ArrayList<Offset>()
            fun pick(): Offset {
                repeat(40) {
                    val x = rad + rnd.nextFloat() * (wPx - 2f * rad)
                    val y = top + rnd.nextFloat() * (bottom - top)
                    val ex = (x - cx) / avoidRx
                    val ey = (y - cy) / avoidRy
                    val inAvoid = ex * ex + ey * ey < 1f
                    val tooClose = placed.any { (it - Offset(x, y)).getDistance() < iconPx * 1.6f }
                    if (!inAvoid && !tooClose) return Offset(x, y)
                }
                return Offset(rad + rnd.nextFloat() * (wPx - 2f * rad), top + rnd.nextFloat() * (hPx * 0.1f))
            }
            List(n) { i ->
                val anchor = pick().also { placed.add(it) }
                Body(
                    pos = anchor,
                    anchorBase = anchor,
                    phase = rnd.nextFloat() * 6.2832f,
                    floatAmp = with(density) { (5f + rnd.nextFloat() * 5f).dp.toPx() },
                    floatSpeed = 0.5f + rnd.nextFloat() * 0.5f,
                    rot = rnd.nextFloat() * 360f,
                    rotIdleSpeed = (rnd.nextFloat() - 0.5f) * 30f,
                )
            }
        }

        val scope = rememberCoroutineScope()
        var tick by remember { mutableIntStateOf(0) }
        var animSec by remember { mutableFloatStateOf(0f) }   // 오라 파티클 애니메이션 시간(초)
        var physicsActive by remember { mutableStateOf(false) }
        var activeIdx by remember { mutableStateOf<Int?>(null) }  // 손가락을 따라가는(잡힌) 아이콘
        var scaledIdx by remember { mutableStateOf<Int?>(null) }  // 확대 표시 중인 아이콘(잡기~복귀)
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        val grabScale = remember { Animatable(1f) }
        val countAlpha = remember { Animatable(0f) }
        val particles = remember { mutableListOf<Particle>() }   // 하트 탭 버스트

        fun spawnBurst(i: Int) {
            val src = bodies[i].pos
            val hidden = liveItems[i].hiddenEffect != null
            val rnd = kotlin.random.Random(System.nanoTime())
            val cnt = if (hidden) 24 else 12
            repeat(cnt) {
                val ang = rnd.nextFloat() * 6.2832f
                val base = if (hidden) 90f else 60f
                val span = if (hidden) 170f else 120f
                val spd = with(density) { (base + rnd.nextFloat() * span).dp.toPx() }
                particles.add(
                    Particle(
                        pos = src,
                        vel = Offset(kotlin.math.cos(ang) * spd, kotlin.math.sin(ang) * spd - spd * 0.3f),
                        life = 1f,
                        painterIdx = i,
                        // 히든은 색+흰 스파클 섞어 더 화려하게.
                        color = if (hidden && it % 3 == 0) Color.White else items[i].color,
                        spin = (rnd.nextFloat() - 0.5f) * (if (hidden) 560f else 360f),
                    )
                )
            }
        }

        // 단일 프레임 루프 — physicsActive 일 때만 물리(벽/충돌) 계산, 그 외엔 가벼운 부유.
        LaunchedEffect(bodies) {
            var last = 0L
            var elapsed = 0f
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                elapsed += dt
                val grabbed = activeIdx

                if (physicsActive) {
                    var grabbedVel = Offset.Zero
                    for (i in bodies.indices) {
                        val b = bodies[i]
                        if (i == grabbed) {
                            grabbedVel = if (dt > 0f) (dragPos - b.pos) / dt else Offset.Zero
                            b.pos = dragPos
                            val tgt = nearestUpright(b.rot)               // 잡으면 똑바로 정렬
                            b.rot += (tgt - b.rot) * minOf(1f, dt * 10f)
                        } else {
                            b.vel *= exp(-FLING_DAMP * dt)               // 감속(빠르게→천천히)
                            b.pos += b.vel * dt
                            b.rot += b.rotIdleSpeed * dt
                        }
                    }
                    // 벽 튕김(잡힌 건 안으로 고정만). 반지름은 수가 많을수록 큼(화려).
                    for (i in bodies.indices) {
                        val b = bodies[i]
                        val r = rad * richness(liveItems[i].count)
                        if (i == grabbed) {
                            b.pos = Offset(b.pos.x.coerceIn(r, wPx - r), b.pos.y.coerceIn(r, hPx - r))
                            continue
                        }
                        if (b.pos.x < r) { b.pos = Offset(r, b.pos.y); b.vel = Offset(-b.vel.x * WALL_REST, b.vel.y) }
                        else if (b.pos.x > wPx - r) { b.pos = Offset(wPx - r, b.pos.y); b.vel = Offset(-b.vel.x * WALL_REST, b.vel.y) }
                        if (b.pos.y < r) { b.pos = Offset(b.pos.x, r); b.vel = Offset(b.vel.x, -b.vel.y * WALL_REST) }
                        else if (b.pos.y > hPx - r) { b.pos = Offset(b.pos.x, hPx - r); b.vel = Offset(b.vel.x, -b.vel.y * WALL_REST) }
                    }
                    // 아이콘끼리 충돌 → 튕김
                    for (i in 0 until n) for (j in i + 1 until n) {
                        collide(bodies[i], bodies[j], i == grabbed, j == grabbed, grabbedVel, rad * richness(liveItems[i].count), rad * richness(liveItems[j].count))
                    }
                    // 정지 판정 — 잡은 게 없고 모두 느려지면 부유 모드로 복귀(기준점=착지 위치)
                    if (grabbed == null && bodies.all { it.vel.getDistance() < STOP_SPEED }) {
                        for (b in bodies) {
                            b.vel = Offset.Zero
                            val off0 = Offset(sin(b.phase) * b.floatAmp, cos(b.phase) * b.floatAmp * 0.7f)
                            b.anchorBase = b.pos - off0      // 부유 시작점 보정(점프 방지)
                            b.floatT0 = elapsed
                        }
                        physicsActive = false
                    }
                } else {
                    // 가벼운 부유(물리 계산 없음)
                    for (b in bodies) {
                        val ft = elapsed - b.floatT0
                        b.pos = b.anchorBase + Offset(
                            sin(ft * b.floatSpeed + b.phase) * b.floatAmp,
                            cos(ft * b.floatSpeed * 0.85f + b.phase) * b.floatAmp * 0.7f
                        )
                        b.rot += b.rotIdleSpeed * dt
                    }
                }
                // 파티클(하트 버스트) — 있을 때만 갱신. 퍼지며 가라앉고 사라진다.
                if (particles.isNotEmpty()) {
                    val it = particles.iterator()
                    while (it.hasNext()) {
                        val p = it.next()
                        p.vel = p.vel * exp(-2.4f * dt) + Offset(0f, 220f * dt) // 감속 + 약한 중력
                        p.pos += p.vel * dt
                        p.life -= dt / 0.7f
                        if (p.life <= 0f) it.remove()
                    }
                }
                // 히든 아이콘 잔상(afterimage) — 잡았거나 빠르게 움직일 때 위치를 쌓고, 멈추면 서서히 지운다.
                for (i in bodies.indices) {
                    if (liveItems[i].hiddenEffect == null) continue
                    val b = bodies[i]
                    val moving = i == grabbed || b.vel.getDistance() > 45f
                    if (moving) {
                        b.trail.addLast(b.pos)
                        while (b.trail.size > 12) b.trail.removeFirst()
                    } else if (b.trail.isNotEmpty()) {
                        b.trail.removeFirst()
                    }
                }
                animSec = elapsed
                tick++
            }
        }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(bodies) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val idx = (0 until n).minByOrNull { (down.position - bodies[it].pos).getDistance() }
                            ?.takeIf { (down.position - bodies[it].pos).getDistance() <= rad * richness(liveItems[it].count) * 2.6f }
                            ?: return@awaitEachGesture
                        val velocityTracker = VelocityTracker()

                        var grabbed = false
                        fun grab() {
                            if (grabbed) return
                            grabbed = true
                            activeIdx = idx
                            scaledIdx = idx
                            bodies[idx].vel = Offset.Zero
                            physicsActive = true
                            scope.launch { grabScale.snapTo(1f); grabScale.animateTo(1.7f, tween(160)) }
                            scope.launch { countAlpha.animateTo(1f, tween(160)) }
                        }
                        val holdJob = scope.launch { delay(150); grab() }
                        dragPos = bodies[idx].pos

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) grab()
                            if (grabbed) {
                                dragPos = change.position
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                            }
                        }
                        holdJob.cancel()

                        if (grabbed) {
                            // 드래그한 방향·속도 그대로 던진다(자연스러운 throw). 이후 물리 루프가 감속/벽 튕김 처리.
                            val v = velocityTracker.calculateVelocity()
                            bodies[idx].vel = Offset(v.x, v.y).clampLen(MAX_FLING)
                            activeIdx = null
                            scope.launch { grabScale.animateTo(1f, tween(280)); scaledIdx = null }
                            scope.launch { countAlpha.animateTo(0f, tween(300)) }
                        } else {
                            if (items[idx].burstOnTap) spawnBurst(idx) // 하트 → 작은 하트 파티클 버스트
                            onTap(idx) // 빠른 탭 → 아이콘 동작(친구→친구, 다이어리→별 화면 등)
                        }
                    }
                }
        ) {
            if (tick >= 0) { // tick 을 읽어 매 프레임 재그리기
                val t = animSec
                // 히든 아이콘: 잔상(뒤) + 궤도 스파클 오라
                for (i in 0 until n) {
                    if (items[i].hiddenEffect == null) continue
                    val b = bodies[i]
                    val ic = iconPx * richness(items[i].count)
                    val ts = b.trail.size
                    if (ts > 0) {
                        b.trail.forEachIndexed { k, tp ->
                            val a = (k + 1f) / ts * 0.32f
                            drawBubble(crystalIcons[i], items[i].color, tp, ic * 0.92f, b.rot, 1f, glow = 0f, alpha = a)
                        }
                    }
                    drawHiddenAura(items[i].color, b.pos, ic * 0.5f, t, i)
                }
                // 파티클(후광 + 작은 아이콘) — 아이콘 아래에 먼저
                for (p in particles) {
                    val a = p.life.coerceIn(0f, 1f)
                    drawBubble(crystalIcons[p.painterIdx], p.color, p.pos, iconPx * 0.42f, p.spin * (1f - a), a, glow = 0.5f * a, alpha = a)
                }
                val scaled = scaledIdx
                for (i in 0 until n) {
                    if (i == scaled) continue
                    drawOne(items[i], crystalIcons[i], starIcons[i], bodies[i].pos, iconPx * richness(items[i].count), bodies[i].rot, 1f)
                }
                scaled?.let { drawOne(items[it], crystalIcons[it], starIcons[it], bodies[it].pos, iconPx * richness(items[it].count), bodies[it].rot, grabScale.value) }
            }
        }

        // 잡은 아이콘 위에 해당 수(count) + 라벨 — 손가락 위쪽, 놓으면 페이드아웃
        scaledIdx?.let { i ->
            val item = items[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        alpha = countAlpha.value
                        translationX = dragPos.x - size.width / 2f
                        translationY = dragPos.y - rad * grabScale.value - 12.dp.toPx() - size.height
                    }
            ) {
                if (item.showCount) {
                    Text(
                        item.count.toString(),
                        color = item.color,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.merge(
                            TextStyle(shadow = Shadow(item.color.copy(alpha = 0.95f), blurRadius = 28f))
                        )
                    )
                    Text(item.label, color = item.color.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text(
                        item.label,
                        color = item.color,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.merge(
                            TextStyle(shadow = Shadow(item.color.copy(alpha = 0.95f), blurRadius = 26f))
                        )
                    )
                }
            }
        }
    }
}

/** 원형 두 아이콘 충돌 해소(위치 분리 + 법선 방향 속도 반발). 잡힌 아이콘은 무한질량(grabbedVel 로 밀어냄). */
private fun collide(a: Body, b: Body, aGrab: Boolean, bGrab: Boolean, grabbedVel: Offset, ra: Float, rb: Float) {
    val delta = b.pos - a.pos
    var dist = delta.getDistance()
    val minDist = ra + rb
    if (dist >= minDist) return
    if (dist < 0.001f) dist = 0.001f
    val nrm = delta / dist                  // a → b
    val overlap = minDist - dist
    when {                                   // 위치 분리
        aGrab -> b.pos += nrm * overlap
        bGrab -> a.pos -= nrm * overlap
        else -> { a.pos -= nrm * (overlap * 0.5f); b.pos += nrm * (overlap * 0.5f) }
    }
    val va = a.vel.dot(nrm)
    val vb = b.vel.dot(nrm)
    when {
        aGrab -> {                           // a 고정(손가락 속도로 밀어냄) → b 튕김
            val gvn = grabbedVel.dot(nrm)
            if (gvn - vb > 0f) b.vel += nrm * ((1f + BALL_REST) * (gvn - vb))
        }
        bGrab -> {                           // b 고정 → a 튕김
            val gvn = grabbedVel.dot(nrm)
            if (va - gvn > 0f) a.vel -= nrm * ((1f + BALL_REST) * (va - gvn))
        }
        else -> {                            // 둘 다 자유(동일 질량 탄성)
            if (va - vb > 0f) {
                val transfer = nrm * ((va - vb) * 0.5f * (1f + BALL_REST))
                a.vel -= transfer
                b.vel += transfer
            }
        }
    }
}

/**
 * 벡터 아이콘 실루엣을 [StarStyle] 크리스탈 파편으로 채운 비트맵을 굽는다(별과 같은 재질).
 * 아이콘을 먼저 그려 알파 마스크로 쓰고, SRC_IN 레이어에 파편을 그려 아이콘 모양 안에만 남긴다.
 */
private fun bakeCrystalIcon(
    painter: VectorPainter,
    color: Color,
    seed: Int,
    sizePx: Int,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val size = Size(sizePx.toFloat(), sizePx.toFloat())
    CanvasDrawScope().draw(Density(1f), layoutDirection, androidx.compose.ui.graphics.Canvas(image), size) {
        with(painter) { draw(size) }
    }
    val canvas = android.graphics.Canvas(image.asAndroidBitmap())
    val maskPaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    }
    val layer = canvas.saveLayer(0f, 0f, size.width, size.height, maskPaint)
    StarStyle.drawCrystalFacets(canvas, silhouette = null, seed = seed, colors = listOf(color.toArgb()), left = 0f, top = 0f, sizePx = size.width)
    canvas.restoreToCount(layer)
    return image
}

/** 후광 + 크리스탈 아이콘 한 개를 [pos] 중심에 [scale] 확대 · [rotationDeg] 회전해 그린다. */
private fun DrawScope.drawBubble(
    icon: ImageBitmap,
    color: Color,
    pos: Offset,
    iconPx: Float,
    rotationDeg: Float,
    scale: Float,
    glow: Float = -1f,   // <0 이면 기본식, 그 외는 지정 알파
    alpha: Float = 1f,
) {
    val r = iconPx / 2f
    val glowAlpha = if (glow >= 0f) glow else (0.32f * scale).coerceAtMost(0.6f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
            center = pos,
            radius = r * 2.6f * scale
        ),
        radius = r * 2.6f * scale,
        center = pos
    )
    val sz = iconPx * scale
    if (sz < 1f) return
    rotate(rotationDeg, pivot = pos) {
        drawImage(
            image = icon,
            dstOffset = IntOffset((pos.x - sz / 2f).roundToInt(), (pos.y - sz / 2f).roundToInt()),
            dstSize = IntSize(sz.roundToInt(), sz.roundToInt()),
            alpha = alpha,
        )
    }
}

/** 별 모양(핀 다이어리) 또는 크리스탈 아이콘을 골라 그린다. */
private fun DrawScope.drawOne(
    item: StatBubble, icon: ImageBitmap, starIcon: ImageBitmap?, pos: Offset, iconPx: Float, rotationDeg: Float, scale: Float,
) {
    if (item.starType >= 0 && starIcon != null) drawStarBubble(starIcon, item.color, pos, iconPx, rotationDeg, scale)
    else drawBubble(icon, item.color, pos, iconPx, rotationDeg, scale)
}

/** StarStyle 별(핀 다이어리) 크리스탈 채움을 1회 굽는다 — 지도 마커/내 다이어리와 동일 무늬. */
private fun bakeCrystalStar(type: Int, colorIndex: Int, sizePx: Int): ImageBitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val canvas = android.graphics.Canvas(image.asAndroidBitmap())
    StarStyle.drawCrystalFill(canvas, type, colorIndex, 0f, 0f, sizePx.toFloat())
    return image
}

/** 후광 + 구운 별 비트맵(핀 다이어리). */
private fun DrawScope.drawStarBubble(
    icon: ImageBitmap, color: Color, pos: Offset, iconPx: Float, rotationDeg: Float, scale: Float,
) {
    val r = (iconPx / 2f) * scale
    val glowAlpha = (0.34f * scale).coerceAtMost(0.62f)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = glowAlpha), Color.Transparent), center = pos, radius = r * 2.6f),
        radius = r * 2.6f, center = pos
    )
    val sz = r * 2f
    if (sz < 1f) return
    rotate(rotationDeg, pivot = pos) {
        drawImage(
            image = icon,
            dstOffset = IntOffset((pos.x - r).roundToInt(), (pos.y - r).roundToInt()),
            dstSize = IntSize(sz.roundToInt(), sz.roundToInt()),
        )
    }
}

/** 히든 아이콘 주변을 도는 스파클 오라(궤도 + 반짝임 + 흰 하이라이트). 떠 있을 때도 화려하게. */
private fun DrawScope.drawHiddenAura(color: Color, pos: Offset, r: Float, t: Float, seed: Int) {
    val count = 7
    for (k in 0 until count) {
        val ph = seed * 1.7f + k * (6.2832f / count)
        val ang = t * 0.9f + ph
        val rr = r * (1.4f + 0.25f * sin(t * 1.6f + ph))
        val p = Offset(pos.x + cos(ang) * rr, pos.y + sin(ang) * rr)
        val a = 0.22f + 0.34f * (0.5f + 0.5f * sin(t * 2.4f + ph))
        val dot = (r * 0.14f * (0.7f + 0.5f * sin(t * 3f + ph))).coerceAtLeast(0.5f)
        drawCircle(color = color.copy(alpha = a), radius = dot, center = p)
        drawCircle(color = Color.White.copy(alpha = a * 0.5f), radius = (dot * 0.42f).coerceAtLeast(0.4f), center = p)
    }
}
