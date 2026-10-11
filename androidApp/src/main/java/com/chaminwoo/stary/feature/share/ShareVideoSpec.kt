package com.chaminwoo.stary.feature.share

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 공유 영상(스토리용 세로 1080×1920)의 **연출 대본** — 카메라 움직임·타임라인·화면 배치 숫자.
 * 순수 계산만 있다(GL/Canvas 없음). iOS `ShareVideoSpec.swift` 와 **같은 값·같은 식**이어야 두 플랫폼 영상이 같다.
 *
 * 흐름: 지구가 빠르게 돌다가([SPIN_END] 초까지 감속) 별이 있는 곳에서 거의 멈춘다 → 별이 켜지면서 빛기둥이 같이 솟는다
 *      → 제목(위) → 날짜·버튼(아래)이 차례로 떠오른다 → 끝(15초)까지 지구는 아주 천천히 계속 돌고, 별 주위로 3초마다 고리가 퍼진다.
 * 화면 좌표는 전부 **디자인 px(1080×1920, 왼쪽 위 원점)** — 실제 인코딩 크기가 달라도(720p 폴백) 비율로 그린다.
 * 마지막 프레임의 틀은 디자인 레퍼런스 "01 신호"(사용자 수정본, 2026-10-11).
 */
object ShareVideoSpec {
    const val W = 1080f
    const val H = 1920f
    const val FPS = 30
    const val DURATION = 15.0f
    const val FRAME_COUNT = 450 // DURATION × FPS

    // ── 카메라 ──
    private const val FOV_DEG = 42.0                // GlobeRenderer 투영과 같은 세로 화각
    private const val END_DIST = 6.038f             // 멈췄을 때 거리 — 지구 반지름이 화면에서 420px
    private const val START_DIST = 8.4f             // 시작은 조금 멀리서(다가오며 멈춘다)
    const val NDC_SHIFT_Y = -0.1625f                // 지구 중심을 y=1116px 로(화면 중앙 960 보다 아래)
    private const val TARGET_RIGHT_DEG = -9f        // 감속이 끝났을 때 별을 지구 중심에서 왼쪽 9°(그 뒤 천천히 돌며 오른쪽 2° 까지 간다)
    private const val TARGET_UP_DEG = 16f           //                              위로 16° 에 둔다
    private const val SPIN_DEG = 620f               // 감속이 끝날 때까지 도는 양(약 1.7바퀴)
    const val SPIN_END = 4.2f                       // 이 시각에 감속이 끝난다
    private const val DRIFT_DEG_PER_SEC = 1.0f      // 그 뒤로도 계속 도는 아주 느린 회전(끝까지)
    /** 멈춘 화면 기준 태양 방향(오른쪽 위 앞) — 별 주변이 항상 보기 좋게 밝다. 지구 좌표로 바꿔 쓰므로 도는 동안엔 낮·밤이 같이 돈다. */
    private val SUN_VIEW = floatArrayOf(0.72f, 0.42f, 0.40f)

    // ── 타임라인(초) ──
    const val FADE_IN = 0.6f        // 검정에서 밝아지는 시간
    const val STAR_AT = 3.6f        // 주인공 별이 켜지기 시작
    const val STAR_DUR = 0.7f
    const val BEAM_AT = STAR_AT     // 빛기둥은 별이 켜지는 순간 같이 솟는다
    const val BEAM_DUR = 1.1f
    const val RING_AT = STAR_AT     // 별 주위로 퍼지는 고리 — [RING_PERIOD] 초마다 하나, 한 번에 [RING_DUR] 초 동안 퍼진다
    const val RING_PERIOD = 3.0f
    const val RING_DUR = 1.8f
    const val TITLE_AT = 4.6f       // 위쪽 글(아이콘·좌표·제목)
    const val CTA_AT = 5.2f         // 아래쪽 글(날짜·버튼)
    const val TEXT_DUR = 0.8f
    const val TEXT_RISE = 24f       // 글이 떠오르는 거리(px)

    // ── 화면 배치(디자인 px) ──
    const val BEAM_TOP = 600f       // 빛기둥 끝
    const val BEAM_HALF_W = 48f     // 빛기둥 텍스처 반폭
    const val STAR_HALF = 140f      // 주인공 별빛 반크기
    const val GLOW_HALF = 150f      // 별 주위 번짐 반지름
    const val RING_MIN = 26f        // 고리 반지름 시작 → 끝
    const val RING_MAX = 210f
    const val RING_SQUASH = 0.92f   // 구면에 누운 느낌(세로 살짝 납작)
    // 고리가 지나가는 자리의 화면을 살짝 일그러뜨린다(물결이 지나가듯 — 고리 안쪽은 밀고 바깥쪽은 당긴다).
    const val RIPPLE_WIDTH = 34f    // 일그러지는 띠의 폭
    const val RIPPLE_MAX_AMP = 12f  // 가장 크게 밀리는 거리(고리가 퍼질수록 줄어든다)
    const val RIPPLE_HALF = 312f    // 일그러짐을 계산하는 정사각 영역의 반변(= RING_MAX + 3 × RIPPLE_WIDTH)
    const val TOP_BLOCK_Y = 219f    // 위쪽 글 블록의 맨 위(아이콘 윗변)
    const val BOTTOM_BLOCK_Y = 1632f // 아래쪽 글 블록의 맨 위(날짜 윗변)
    /** 좌표 표기 소수 자릿수 — 2자리(약 1km)로 둔다. 공개 SNS 에 올라가는 영상이라 집 위치가 미터 단위로 드러나지 않게. */
    const val COORD_DECIMALS = 2

    class Pose(val yawDeg: Float, val pitchDeg: Float, val dist: Float)

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
    fun easeOutCubic(u: Float): Float { val k = 1f - clamp01(u); return 1f - k * k * k }
    fun smooth(u: Float): Float { val k = clamp01(u); return k * k * (3f - 2f * k) }
    /** 살짝 넘쳤다 돌아오는 등장(별이 켜질 때). */
    fun easeOutBack(u: Float): Float {
        val k = clamp01(u) - 1f
        return 1f + 2.70158f * k * k * k + 1.70158f * k * k
    }
    /** [at] 에서 시작해 [dur] 동안 0→1. */
    fun progress(t: Float, at: Float, dur: Float): Float = clamp01((t - at) / dur)

    private fun endYaw(lng: Double) = (-lng).toFloat() + TARGET_RIGHT_DEG
    private fun endPitch(lat: Double) = (lat.toFloat() - TARGET_UP_DEG).coerceIn(-75f, 75f)

    /** 시각 [t] 의 카메라 — GlobeRenderer 의 yawDeg/pitchDeg/camDist 에 그대로 넣는다. */
    fun pose(t: Float, lat: Double, lng: Double): Pose {
        val u = t / SPIN_END
        val e = easeOutCubic(u)
        val pitchEnd = endPitch(lat)
        val pitchStart = pitchEnd * 0.4f + 8f
        return Pose(
            // 감속 곡선 + 처음부터 끝까지 깔린 아주 느린 회전(t = SPIN_END 에서 정확히 endYaw 를 지난다 — 속도가 끊기지 않는다)
            yawDeg = endYaw(lng) - SPIN_DEG * (1f - e) + DRIFT_DEG_PER_SEC * (t - SPIN_END),
            pitchDeg = pitchStart + (pitchEnd - pitchStart) * e,
            dist = START_DIST + (END_DIST - START_DIST) * smooth(u),
        )
    }

    /** 멈춘 화면에서 [SUN_VIEW] 가 되는 태양 방향을 지구 좌표계로(R = Rx(pitch)·Ry(yaw) 의 전치를 곱한다). */
    fun sunDir(lat: Double, lng: Double): FloatArray {
        val n = sqrt(SUN_VIEW[0] * SUN_VIEW[0] + SUN_VIEW[1] * SUN_VIEW[1] + SUN_VIEW[2] * SUN_VIEW[2])
        val x = SUN_VIEW[0] / n; val y = SUN_VIEW[1] / n; val z = SUN_VIEW[2] / n
        val p = Math.toRadians(endPitch(lat).toDouble()); val a = Math.toRadians(endYaw(lng).toDouble())
        val cp = cos(p).toFloat(); val sp = sin(p).toFloat(); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val y1 = y * cp + z * sp
        val z1 = -y * sp + z * cp
        return floatArrayOf(x * ca - z1 * sa, y1, x * sa + z1 * ca)
    }

    /**
     * 위경도(반지름 [radius], 지구 = 1) → 화면 위치(디자인 px). GlobeRenderer 의 행렬(모델 = Rx·Ry, 카메라 +Z, 화각 42°)과
     * [NDC_SHIFT_Y] 를 그대로 따라 계산한다. 반환 [x, y, 앞뒤(>0 = 카메라 쪽 반구)].
     */
    fun project(lat: Double, lng: Double, radius: Float, pose: Pose): FloatArray {
        val ph = Math.toRadians(lat); val la = Math.toRadians(lng)
        val x = (cos(ph) * sin(la)).toFloat() * radius
        val y = sin(ph).toFloat() * radius
        val z = (cos(ph) * cos(la)).toFloat() * radius
        val a = Math.toRadians(pose.yawDeg.toDouble()); val p = Math.toRadians(pose.pitchDeg.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat(); val cp = cos(p).toFloat(); val sp = sin(p).toFloat()
        val x1 = x * ca + z * sa
        val z1 = -x * sa + z * ca
        val y2 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val f = (1.0 / tan(Math.toRadians(FOV_DEG / 2))).toFloat()
        val w = pose.dist - z2
        val nx = x1 * f / (W / H) / w
        val ny = y2 * f / w + NDC_SHIFT_Y
        return floatArrayOf((nx * 0.5f + 0.5f) * W, (1f - (ny * 0.5f + 0.5f)) * H, z2)
    }

    /** 지금 퍼지고 있는 고리의 진행도(0..1). 고리가 없는 때(시작 전·다음 고리를 기다리는 중)는 음수. */
    fun ringPhase(t: Float): Float {
        val local = t - RING_AT
        if (local < 0f) return -1f
        val phase = (local % RING_PERIOD) / RING_DUR
        return if (phase < 1f) phase else -1f
    }
    fun ringRadius(phase: Float): Float = RING_MIN + (RING_MAX - RING_MIN) * easeOutCubic(phase)
    fun ringAlpha(phase: Float): Float = (1f - phase).pow(2) * 0.6f

    /** 일그러짐 세기(px) — 고리가 막 생길 때 부드럽게 올라왔다가 퍼지면서 사라진다. */
    fun rippleAmp(phase: Float): Float = RIPPLE_MAX_AMP * smooth(phase / 0.15f) * (1f - phase).pow(1.5f)

    /**
     * 별에서 [dist] px 떨어진 점이 바깥쪽으로 밀려 보이는 거리(px) — 그 점의 색을 "별 쪽으로 이만큼 안쪽"에서 가져온다.
     * 고리([radius]) 바로 바깥은 +, 바로 안쪽은 −, 띠([RIPPLE_WIDTH]의 3배)를 벗어나면 0. Android 는 같은 식을 셰이더로 쓴다(RIPPLE_FS).
     */
    fun rippleShift(dist: Float, radius: Float, amp: Float): Float {
        val x = (dist - radius) / RIPPLE_WIDTH
        return amp * RIPPLE_NORM * x * kotlin.math.exp(-x * x)
    }
    /** x·e^(−x²) 의 최댓값(0.4289)을 1 로 맞추는 배율. */
    const val RIPPLE_NORM = 2.3316f
}
