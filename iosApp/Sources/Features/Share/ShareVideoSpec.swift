import Foundation
import simd

/// 공유 영상(스토리용 세로 1080×1920)의 **연출 대본** — 카메라 움직임·타임라인·화면 배치 숫자.
/// 순수 계산만 있다(Metal/UIKit 없음). Android `feature/share/ShareVideoSpec.kt` 와 **같은 값·같은 식**이어야 두 플랫폼 영상이 같다.
///
/// 흐름: 지구가 빠르게 돌다가(`spinEnd` 초까지 감속) 별이 있는 곳에서 거의 멈춘다 → 별이 켜지면서 빛기둥이 같이 솟는다
///      → 제목(위) → 날짜·버튼(아래)이 차례로 떠오른다 → 끝(15초)까지 지구는 아주 천천히 계속 돌고, 별 주위로 3초마다 고리가 퍼진다.
/// 화면 좌표는 전부 **디자인 px(1080×1920, 왼쪽 위 원점)**. 마지막 프레임의 틀은 디자인 레퍼런스 "01 신호"(사용자 수정본, 2026-10-11).
enum ShareVideoSpec {
    static let w: Float = 1080
    static let h: Float = 1920
    static let fps: Int32 = 30
    static let duration: Float = 15.0
    static let frameCount = 450 // duration × fps

    // ── 카메라 ──
    private static let fovDeg: Double = 42          // GlobeRenderer 투영과 같은 세로 화각
    private static let endDist: Float = 6.038       // 멈췄을 때 거리 — 지구 반지름이 화면에서 420px
    private static let startDist: Float = 8.4       // 시작은 조금 멀리서(다가오며 멈춘다)
    static let ndcShiftY: Float = -0.1625           // 지구 중심을 y=1116px 로(화면 중앙 960 보다 아래)
    private static let targetRightDeg: Float = -9   // 감속이 끝났을 때 별을 지구 중심에서 왼쪽 9°(그 뒤 천천히 돌며 오른쪽 2° 까지 간다)
    private static let targetUpDeg: Float = 16      //                              위로 16° 에 둔다
    private static let spinDeg: Float = 620         // 감속이 끝날 때까지 도는 양(약 1.7바퀴)
    static let spinEnd: Float = 4.2                 // 이 시각에 감속이 끝난다
    private static let driftDegPerSec: Float = 1.0  // 그 뒤로도 계속 도는 아주 느린 회전(끝까지)
    /// 멈춘 화면 기준 태양 방향(오른쪽 위 앞) — 별 주변이 항상 보기 좋게 밝다. 지구 좌표로 바꿔 쓰므로 도는 동안엔 낮·밤이 같이 돈다.
    private static let sunView = SIMD3<Float>(0.72, 0.42, 0.40)

    // ── 타임라인(초) ──
    static let fadeIn: Float = 0.6        // 검정에서 밝아지는 시간
    static let starAt: Float = 3.6        // 주인공 별이 켜지기 시작
    static let starDur: Float = 0.7
    static let beamAt: Float = starAt     // 빛기둥은 별이 켜지는 순간 같이 솟는다
    static let beamDur: Float = 1.1
    static let ringAt: Float = starAt     // 별 주위로 퍼지는 고리 — `ringPeriod` 초마다 하나, 한 번에 `ringDur` 초 동안 퍼진다
    static let ringPeriod: Float = 3.0
    static let ringDur: Float = 1.8
    static let titleAt: Float = 4.6       // 위쪽 글(아이콘·좌표·제목)
    static let ctaAt: Float = 5.2         // 아래쪽 글(날짜·버튼)
    static let textDur: Float = 0.8
    static let textRise: Float = 24       // 글이 떠오르는 거리(px)

    // ── 화면 배치(디자인 px) ──
    static let beamTop: Float = 600       // 빛기둥 끝
    static let beamHalfW: Float = 48      // 빛기둥 텍스처 반폭
    static let starHalf: Float = 140      // 주인공 별빛 반크기
    static let glowHalf: Float = 150      // 별 주위 번짐 반지름
    static let ringMin: Float = 26        // 고리 반지름 시작 → 끝
    static let ringMax: Float = 210
    static let ringSquash: Float = 0.92   // 구면에 누운 느낌(세로 살짝 납작)
    // 고리가 지나가는 자리의 화면을 살짝 일그러뜨린다(물결이 지나가듯 — 고리 안쪽은 밀고 바깥쪽은 당긴다).
    static let rippleWidth: Float = 34    // 일그러지는 띠의 폭
    static let rippleMaxAmp: Float = 12   // 가장 크게 밀리는 거리(고리가 퍼질수록 줄어든다)
    static let rippleHalf: Float = 312    // 일그러짐을 계산하는 정사각 영역의 반변(= ringMax + 3 × rippleWidth)
    /// x·e^(−x²) 의 최댓값(0.4289)을 1 로 맞추는 배율.
    static let rippleNorm: Float = 2.3316
    static let topBlockY: Float = 219     // 위쪽 글 블록의 맨 위(아이콘 윗변)
    static let bottomBlockY: Float = 1632 // 아래쪽 글 블록의 맨 위(날짜 윗변)
    /// 좌표 표기 소수 자릿수 — 2자리(약 1km)로 둔다. 공개 SNS 에 올라가는 영상이라 집 위치가 미터 단위로 드러나지 않게.
    static let coordDecimals = 2
    /// 주인공 별의 높이(지구 = 1) — 글로브가 그리는 작은 불빛(glowRadius 1.008)과 같은 자리에 얹는다.
    static let starRadius: Float = 1.008

    struct Pose: Sendable {
        var yawDeg: Float
        var pitchDeg: Float
        var dist: Float
    }

    private static func clamp01(_ v: Float) -> Float { min(max(v, 0), 1) }
    static func easeOutCubic(_ u: Float) -> Float { let k = 1 - clamp01(u); return 1 - k * k * k }
    static func smooth(_ u: Float) -> Float { let k = clamp01(u); return k * k * (3 - 2 * k) }
    /// 살짝 넘쳤다 돌아오는 등장(별이 켜질 때).
    static func easeOutBack(_ u: Float) -> Float {
        let k = clamp01(u) - 1
        return 1 + 2.70158 * k * k * k + 1.70158 * k * k
    }
    /// `at` 에서 시작해 `dur` 동안 0→1.
    static func progress(_ t: Float, at: Float, dur: Float) -> Float { clamp01((t - at) / dur) }

    private static func endYaw(_ lng: Double) -> Float { Float(-lng) + targetRightDeg }
    private static func endPitch(_ lat: Double) -> Float { min(max(Float(lat) - targetUpDeg, -75), 75) }

    /// 시각 `t` 의 카메라 — GlobeRenderer 의 yawDeg/pitchDeg/camDist 에 그대로 넣는다.
    static func pose(_ t: Float, lat: Double, lng: Double) -> Pose {
        let u = t / spinEnd
        let e = easeOutCubic(u)
        let pitchEnd = endPitch(lat)
        let pitchStart = pitchEnd * 0.4 + 8
        // 감속 곡선 + 처음부터 끝까지 깔린 아주 느린 회전(t = spinEnd 에서 정확히 endYaw 를 지난다 — 속도가 끊기지 않는다)
        return Pose(yawDeg: endYaw(lng) - spinDeg * (1 - e) + driftDegPerSec * (t - spinEnd),
                    pitchDeg: pitchStart + (pitchEnd - pitchStart) * e,
                    dist: startDist + (endDist - startDist) * smooth(u))
    }

    /// 멈춘 화면에서 `sunView` 가 되는 태양 방향을 지구 좌표계로(R = Rx(pitch)·Ry(yaw) 의 전치를 곱한다).
    static func sunDir(lat: Double, lng: Double) -> SIMD3<Float> {
        let v = simd_normalize(sunView)
        let p = endPitch(lat) * .pi / 180
        let a = endYaw(lng) * .pi / 180
        let cp = cos(p), sp = sin(p), ca = cos(a), sa = sin(a)
        let y1 = v.y * cp + v.z * sp
        let z1 = -v.y * sp + v.z * cp
        return SIMD3(v.x * ca - z1 * sa, y1, v.x * sa + z1 * ca)
    }

    /// 위경도(반지름 `radius`, 지구 = 1) → 화면 위치(디자인 px). GlobeRenderer 의 행렬(모델 = Rx·Ry, 카메라 +Z, 화각 42°)과
    /// `ndcShiftY` 를 그대로 따라 계산한다. 반환 (x, y, 앞뒤(>0 = 카메라 쪽 반구)).
    static func project(lat: Double, lng: Double, radius: Float, pose: Pose) -> (x: Float, y: Float, front: Float) {
        let ph = lat * .pi / 180, la = lng * .pi / 180
        let x = Float(cos(ph) * sin(la)) * radius
        let y = Float(sin(ph)) * radius
        let z = Float(cos(ph) * cos(la)) * radius
        let a = pose.yawDeg * .pi / 180, p = pose.pitchDeg * .pi / 180
        let ca = cos(a), sa = sin(a), cp = cos(p), sp = sin(p)
        let x1 = x * ca + z * sa
        let z1 = -x * sa + z * ca
        let y2 = y * cp - z1 * sp
        let z2 = y * sp + z1 * cp
        let f = Float(1 / tan(fovDeg / 2 * .pi / 180))
        let wc = pose.dist - z2
        let nx = x1 * f / (w / h) / wc
        let ny = y2 * f / wc + ndcShiftY
        return ((nx * 0.5 + 0.5) * w, (1 - (ny * 0.5 + 0.5)) * h, z2)
    }

    /// 지금 퍼지고 있는 고리의 진행도(0..1). 고리가 없는 때(시작 전·다음 고리를 기다리는 중)는 음수.
    static func ringPhase(_ t: Float) -> Float {
        let local = t - ringAt
        if local < 0 { return -1 }
        let phase = local.truncatingRemainder(dividingBy: ringPeriod) / ringDur
        return phase < 1 ? phase : -1
    }
    static func ringRadius(_ phase: Float) -> Float { ringMin + (ringMax - ringMin) * easeOutCubic(phase) }
    static func ringAlpha(_ phase: Float) -> Float { (1 - phase) * (1 - phase) * 0.6 }

    /// 일그러짐 세기(px) — 고리가 막 생길 때 부드럽게 올라왔다가 퍼지면서 사라진다.
    static func rippleAmp(_ phase: Float) -> Float { rippleMaxAmp * smooth(phase / 0.15) * powf(1 - phase, 1.5) }

    /// 별에서 `dist` px 떨어진 점이 바깥쪽으로 밀려 보이는 거리(px) — 그 점의 색을 "별 쪽으로 이만큼 안쪽"에서 가져온다.
    /// 고리(`radius`) 바로 바깥은 +, 바로 안쪽은 −, 띠(`rippleWidth` 의 3배)를 벗어나면 0. Android 는 같은 식을 셰이더로 쓴다.
    static func rippleShift(dist: Float, radius: Float, amp: Float) -> Float {
        let x = (dist - radius) / rippleWidth
        return amp * rippleNorm * x * expf(-x * x)
    }

    /// "37.56° N · 126.93° E" — 소수 자릿수는 `coordDecimals`.
    static func coordText(lat: Double, lng: Double) -> String {
        String(format: "%.\(coordDecimals)f° %@ · %.\(coordDecimals)f° %@",
               locale: Locale(identifier: "en_US_POSIX"),
               abs(lat), lat >= 0 ? "N" : "S", abs(lng), lng >= 0 ? "E" : "W")
    }
}
