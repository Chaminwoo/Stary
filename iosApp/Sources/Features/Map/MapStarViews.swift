import MapLibre
import QuartzCore
import SwiftUI // StarShape(Path) → 유리 결 빛 마스크
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 지도 별 마커 뷰 — Android DiaryMap/DiaryMapMarkers 의 부유·스파클·위성 패리티(2026-10-04 재작성).
//
// 뷰 계층(⚠️ 어노테이션 뷰 자신의 transform/layer.transform 은 **건드리지 않는다**):
//   StarMarkerView(MLNAnnotationView)   ← MapLibre 가 scalesWithViewingDistance 로 layer.transform 에 기울기 원근 배율을 넣는 곳
//     └ zoomHost    : 줌 단계별 별 크기 배율(transform). 뷰 bounds 도 같이 줄여 탭 영역이 별 크기를 따라가게 한다.
//         └ floatHost   : 위아래 부유(CABasicAnimation, 별마다 다른 위상) — 혜성/대표 별이 한 덩어리로 둥실
//             ├ 대표 별 이미지(zPosition 0) + 유리 결 빛(sheen, 0.5) : 흰 사선 띠가 ↘ 로 훑고 작은 반짝이 건너간다
//             └ 혜성(스파클 · 겹친 별 멤버) : 별 둘레 3D 원 궤도([Orbit3D]). zPosition 을 궤도 앞(+1)/뒤(-1)로 바꿔
//                 별 뒤로 돌아 들어갔다 앞으로 나오고, 정면을 지날 때 크게 반짝인다. 줌 게이트 alpha 는 각 혜성의 gate 뷰에.
// MapStarReveal 은 어노테이션 뷰의 alpha + layer.sublayerTransform 을 쓴다(위 구조와 충돌 없음).
//
// "별마다 다르게": 다이어리 id 의 **안정 해시(FNV-1a)** 로 시드한 난수. Swift `hashValue` 는 실행마다 달라지므로 쓰지 않는다.
// ⚠️ Android 혜성 오버레이(`MapComets.kt` 의 SparkleSeed/MemberSeed)가 **같은 해시 · 같은 순서**로 뽑아 같은 별이 두 앱에서
//    같은 방향 · 기울기 · 반경으로 돈다 — 여기서 rng 를 부르는 순서(부유 2 → 회전 방향 · 기울기 그룹 2 → 혜성 세트당 4 → 멤버 3)를
//    바꾸면 양쪽을 같이 바꾼다.
// 무한 애니메이션은 timeOffset 으로 위상을 주고(전역 시계에 동기화하지 않는다), 창에 붙을 때/포그라운드 복귀 때 다시 건다.
// ─────────────────────────────────────────────────────────────────────────────

/// 안정 해시 — 앱을 다시 켜도 같은 id → 같은 값(별마다 고정된 개성).
enum StarMotionHash {
    static func fnv1a(_ s: String) -> UInt64 {
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for b in s.utf8 {
            h ^= UInt64(b)
            h = h &* 0x0000_0100_0000_01b3
        }
        return h
    }
}

/// 별 모션용 시드 난수(SplitMix64). MapStyleEffects 의 SeededRandom 은 private 이라 따로 둔다.
struct StarMotionRandom {
    private var state: UInt64

    init(seed: UInt64) { state = seed }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// 0..<1 균등 난수.
    mutating func unit() -> Double {
        Double(next() >> 11) * (1.0 / 9_007_199_254_740_992.0)
    }

    mutating func range(_ lo: Double, _ hi: Double) -> Double {
        lo + (hi - lo) * unit()
    }

    mutating func flip() -> Bool {
        next() & 1 == 0
    }
}

/// 창에 붙을 때(또는 포그라운드 복귀 때) 다시 걸어야 하는 무한 애니메이션 1개.
struct StarAnimJob {
    let layer: CALayer
    let key: String
    let anim: CAAnimation
}

// MARK: - Sparkle

/// 별 마커 곁을 도는 스파클 파티클.
enum MapSparkle {

    /// 큰 별 기준.
    static let bigStarThreshold: Double = 1.75

    /// 최대 궤도 영역 비율(가장 바깥 반경 × 최대 흔들림 + 머리).
    static let maxOrbitExtentRatio: CGFloat = 0.66 * 1.08 + 0.16

    /// 스파클이 보이기 시작하는 줌 / 완전히 드러나는 줌 — Android `COMET_MIN_ZOOM`/`COMET_FULL_ZOOM`(MapComets.kt) 과 같은 값.
    /// 멀리서는 별 곁 반짝이가 잡동사니로 보이므로 지운다.
    static let minZoom: Double = 11
    static let fullZoom: Double = 13

    /// 줌 → 스파클 불투명도(0 = 안 보임).
    static func zoomOpacity(forZoom zoom: Double) -> CGFloat {
        guard zoom > minZoom else { return 0 }
        guard zoom < fullZoom else { return 1 }
        return CGFloat((zoom - minZoom) / (fullZoom - minZoom))
    }

    /// 짧게 번쩍이고 천천히 사라지는 곡선(0..1) — 수정구 시안 `flashEnv`, Android `sparkleFlash` 와 같은 식. u = 주기 안 위치 0..1.
    static func flashEnv(_ u: Double) -> Double {
        if u < 0.18 { let s = sin(u / 0.18 * .pi / 2); return s * s }
        let d = 1 - (u - 0.18) / 0.82
        return d * d * d
    }

    /// sizeMult에 따른 파티클 수.
    static func particleCount(sizeMult: Double) -> Int {
        if sizeMult >= 2.6 { return 3 }
        if sizeMult >= 1.6 { return 2 }
        return 1
    }

    /// 별 곁 혜성 설치 — 별마다 다른 반경/속도/방향/기울기/위상. 줌 게이트용 뷰와 걸어야 할 애니메이션 목록을 돌려준다.
    /// [host] 는 대표 별 이미지와 **같은 부모**여야 한다(zPosition 으로 별 앞/뒤를 바꾼다).
    static func install(
        on host: UIView,
        center: CGPoint,
        markerSize: CGFloat,
        sizeMult: Double,
        starType: Int,
        starColor: Int,
        rng: inout StarMotionRandom
    ) -> (gates: [UIView], jobs: [StarAnimJob]) {

        let count = particleCount(sizeMult: sizeMult)
        let big = sizeMult >= bigStarThreshold

        // 궤도 반경 — 별 몸(반지름 = 마커의 0.39) 바로 바깥. Android `MapComets.kt` SPARKLE_RADIUS 와 같은 값(2026-10-28: 0.42/0.56/0.62 → 몸에 걸치지 않게).
        let radii: [CGFloat] = [markerSize * 0.46, markerSize * 0.58, markerSize * 0.66]
        let sizes: [CGFloat] = [markerSize * 0.30, markerSize * 0.24, markerSize * 0.16]
        let flashPeriods: [Double] = [2.6, 3.4, 4.3]

        // 안쪽 궤도 방향은 별마다 랜덤, 가운데 궤도는 반대(Android 의 안/밖 역방향 관계 유지).
        let firstClockwise = rng.flip()
        // 궤도면 기울기 그룹(0..3) — (g - 1.5) × 0.12 를 더한다. 별마다 시드로 고른다(Android SparkleSeed 와 같은 값).
        let tiltGroup = Int(rng.next() % 4)

        var gates: [UIView] = []
        var jobs: [StarAnimJob] = []

        for set in 0..<count {

            let size = sizes[set]
            // 큰 별 곁은 그 별 모양 머리, 가장 바깥 작은 혜성(set 2)은 항상 흰 반짝이(Android 와 같이).
            let image = (big && set != 2)
                ? starComet(type: starType, colorIndex: starColor, head: size)
                : whiteComet(head: size)

            let clockwise = (set == 1) ? !firstClockwise : firstClockwise
            let orb = Orbit3D.install(
                on: host,
                image: image,
                imageSize: CGSize(width: size * cometAspect, height: size),
                center: center,
                radius: radii[set] * CGFloat(rng.range(0.94, 1.08)),
                flat: Double(orbitFlat),
                tilt: Double(orbitTilt(set: set, group: tiltGroup)),
                period: 2 * .pi / (orbitSpeed(set) * rng.range(0.9, 1.1)),
                dir: clockwise ? 1 : -1,
                phase: rng.unit(),
                backAlpha: 0.55,
                glintAlpha: 0.55,
                glintScale: 0.3
            )
            gates.append(orb.gate)
            jobs.append(contentsOf: orb.jobs)

            // 가끔 한 번 번쩍(수정구 시안 flashEnv) — 정면 반짝과 별개. 주기·곡선은 Android `sparkleFlashPeriod`/`sparkleFlash`.
            let twPeriod = flashPeriods[set]
            let twOffset = rng.unit() * twPeriod
            let steps = 48
            let envs = (0...steps).map { flashEnv(Double($0) / Double(steps)) }
            let keyTimes = (0...steps).map { NSNumber(value: Double($0) / Double(steps)) }
            func flashAnim(_ keyPath: String, _ values: [Double]) -> CAKeyframeAnimation {
                let a = CAKeyframeAnimation(keyPath: keyPath)
                a.values = values
                a.keyTimes = keyTimes
                a.calculationMode = .linear
                a.duration = twPeriod
                a.repeatCount = .infinity
                a.timeOffset = twOffset
                a.isRemovedOnCompletion = false
                return a
            }
            jobs.append(StarAnimJob(layer: orb.image.layer, key: "twinkle", anim: flashAnim("opacity", envs.map { 0.6 + 0.4 * $0 })))
            jobs.append(StarAnimJob(layer: orb.image.layer, key: "grow", anim: flashAnim("transform.scale", envs.map { 0.92 + 0.12 * $0 })))
        }

        return (gates, jobs)
    }

    /// 혜성 그림 가로 = 머리 크기 × 2.5(머리는 가운데, 꼬리는 -x). 꼬리는 곧게 진행 방향 반대로 눕는다
    /// (Android 는 매 프레임 그리는 오버레이라 꼬리가 궤도를 따라 휜다 — MapComets.kt `drawComet`).
    static let cometAspect: CGFloat = 2.5

    /// 궤도면 기울기(rad) — 지도 시안 sparks: 세트 · 그룹마다 조금씩 비스듬히. Android `sparkleOrbitTilt` 와 같은 값.
    static func orbitTilt(set: Int, group: Int) -> CGFloat {
        (set == 1 ? -0.30 : 0.36) + (CGFloat(group) - 1.5) * 0.12
    }

    /// 궤도 납작함(= cos(궤도면이 누운 각)) — Android `SPARKLE_ORBIT_FLAT`.
    static let orbitFlat: CGFloat = 0.42

    /// 세트별 공전 각속도(rad/초) — Android `sparkleOrbitSpeed`.
    static func orbitSpeed(_ set: Int) -> Double {
        switch set {
        case 0: return 1.1
        case 1: return 0.8
        default: return 1.5
        }
    }

    /// 궤도 위 앞쪽 정도(0 = 뒤 … 1 = 앞) — Android `sparkleFrontness` 와 같은 식.
    static func frontness(_ sinAng: Double) -> Double {
        let x = min(max((sinAng + 0.3) / 0.6, 0), 1)
        return x * x * (3 - 2 * x)
    }

    // MARK: White Sparkle

    private static var whiteCache: [Int: UIImage] = [:]

    private static func whiteSparkle(size: CGFloat) -> UIImage {

        let key = Int(size.rounded())

        if let cached = whiteCache[key] {
            return cached
        }

        let px = CGFloat(max(key, 1))

        let img = UIGraphicsImageRenderer(
            size: CGSize(width: px, height: px)
        ).image { ctx in

            let cg = ctx.cgContext

            let body = px * 0.68

            let rect = CGRect(
                x: (px - body) / 2,
                y: (px - body) / 2,
                width: body,
                height: body
            )

            // 굽은 4갈래 반짝(중심을 지나는 2차 곡선 4개 — 수정구 시안 sparkleR, Android `MapComets.rays` 와 같은 비율) + 평소에도 보이는 작은 알갱이.
            let c = CGPoint(x: rect.midX, y: rect.midY)
            let r = px * 0.46
            let k = r * 0.10
            let path = UIBezierPath()
            path.move(to: CGPoint(x: c.x, y: c.y - r))
            path.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), controlPoint: CGPoint(x: c.x + k, y: c.y - k))
            path.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), controlPoint: CGPoint(x: c.x + k, y: c.y + k))
            path.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), controlPoint: CGPoint(x: c.x - k, y: c.y + k))
            path.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), controlPoint: CGPoint(x: c.x - k, y: c.y - k))
            path.close()

            cg.setShadow(
                offset: .zero,
                blur: px * 0.14,
                color: UIColor.white.withAlphaComponent(0.9).cgColor
            )

            cg.setFillColor(UIColor.white.cgColor)
            cg.addPath(path.cgPath)
            cg.fillPath()
            let core = px * 0.075
            cg.fillEllipse(in: CGRect(x: c.x - core, y: c.y - core, width: core * 2, height: core * 2))
        }

        whiteCache[key] = img

        return img
    }

    // MARK: Comet

    /// 혜성 꼬리 — 머리(hx, cy)에서 -x 로 [len] 만큼, 머리 쪽 폭 [w] 에서 끝으로 가늘어지며 사라진다(0 → 0.3α(60%) → α).
    static func drawTail(_ cg: CGContext, hx: CGFloat, cy: CGFloat, len: CGFloat, w: CGFloat, color: UIColor, alpha: CGFloat) {
        let tip = hx - len
        let path = CGMutablePath()
        path.move(to: CGPoint(x: hx, y: cy - w / 2))
        path.addQuadCurve(to: CGPoint(x: tip, y: cy), control: CGPoint(x: hx - len * 0.45, y: cy - w * 0.26))
        path.addQuadCurve(to: CGPoint(x: hx, y: cy + w / 2), control: CGPoint(x: hx - len * 0.45, y: cy + w * 0.26))
        path.closeSubpath()
        guard let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                    colors: [color.withAlphaComponent(0).cgColor,
                                             color.withAlphaComponent(0.3 * alpha).cgColor,
                                             color.withAlphaComponent(alpha).cgColor] as CFArray,
                                    locations: [0, 0.6, 1]) else { return }
        cg.saveGState()
        cg.addPath(path)
        cg.clip()
        cg.drawLinearGradient(grad, start: CGPoint(x: tip, y: cy), end: CGPoint(x: hx, y: cy), options: [])
        cg.restoreGState()
    }

    private static var cometCache: [String: UIImage] = [:]

    /// 혜성 캔버스(머리 [head] × [cometAspect] 가로) 위에 꼬리를 그리고 머리 그림을 가운데 얹는다.
    static func comet(key: String, head: CGFloat, tails: [(len: CGFloat, w: CGFloat, color: UIColor, alpha: CGFloat)],
                      headImage: () -> UIImage) -> UIImage {
        if let cached = cometCache[key] { return cached }
        let w = head * cometAspect
        let fmt = UIGraphicsImageRendererFormat()
        fmt.opaque = false
        let img = UIGraphicsImageRenderer(size: CGSize(width: w, height: head), format: fmt).image { ctx in
            let cg = ctx.cgContext
            for t in tails {
                drawTail(cg, hx: w / 2, cy: head / 2, len: t.len * head, w: t.w * head, color: t.color, alpha: t.alpha)
            }
            headImage().draw(in: CGRect(x: (w - head) / 2, y: 0, width: head, height: head))
        }
        cometCache[key] = img
        return img
    }

    /// 흰 혜성 — 굽은 4갈래 반짝 머리 + 따뜻한 흰 꼬리(Android `MapComets` 흰 반짝 머리의 꼬리 폭 · 색과 같은 값).
    static func whiteComet(head: CGFloat) -> UIImage {
        comet(key: "w\(Int((head * 4).rounded()))", head: head, tails: [
            (0.94, 0.22, UIColor(red: 1, green: 0.914, blue: 0.784, alpha: 1), 0.45),
            (0.81, 0.075, .white, 0.85),
        ]) { whiteSparkle(size: head) }
    }

    /// 큰 별 곁 혜성 — 그 별 모양 미니 크리스탈 머리 + 그 빛깔 꼬리(Android `MapComets` HEAD_STAR).
    static func starComet(type: Int, colorIndex: Int, head: CGFloat) -> UIImage {
        let color = UIColor(StarStyle.color(colorIndex))
        return comet(key: "s\(type)-\(colorIndex)-\(Int((head * 4).rounded()))", head: head, tails: [
            (0.94, 0.25, color, 0.55),
            (0.75, 0.075, UIColor(red: 1, green: 0.965, blue: 0.918, alpha: 1), 0.6),
        ]) { StarImageRenderer.image(type: type, colorIndex: colorIndex, size: head) }
    }

    /// 겹친 별 멤버 혜성 — 그 멤버 별 머리 + 그 빛깔의 긴 꼬리(Android `MapComets.drawMembers`).
    static func memberComet(type: Int, colorIndex: Int, head: CGFloat) -> UIImage {
        let color = UIColor(StarStyle.color(colorIndex))
        return comet(key: "m\(type)-\(colorIndex)-\(Int((head * 4).rounded()))", head: head, tails: [
            (1.03, 0.31, color, 0.5),
            (0.84, 0.078, UIColor(red: 1, green: 0.957, blue: 0.918, alpha: 1), 0.65),
        ]) { StarImageRenderer.image(type: type, colorIndex: colorIndex, size: head) }
    }
}

// MARK: - Orbit 3D

/// 별 둘레 3D 원 궤도 — 궤도면이 화면 쪽으로 누워(납작함 flat) 앞쪽 반이 화면 아래로 온다. Android `MapComets.drawComet` 과 같은 식.
/// 앞쪽 반은 별 위(zPosition +1) · 뒤쪽 반은 별 아래(-1), 원근으로 앞에선 크고 뒤에선 작고 흐리게,
/// 정면을 지날 때 크게 반짝이고(glint), 꼬리는 진행 방향 반대로 눕는다.
enum Orbit3D {

    struct Point {
        let x: Double, y: Double   // 화면 위치(반경 1, y 아래가 +)
        let z: Double              // 깊이(+1 = 정면 가장 가까움)
        let front: Double          // 앞쪽 정도(0 뒤 … 1 앞)
        let headingDeg: Double     // 화면 진행 방향(°, 시계 방향)
    }

    static func point(_ ang: Double, flat: Double, tilt: Double, dir: Double) -> Point {
        let ca = cos(ang), sa = sin(ang), ct = cos(tilt), st = sin(tilt)
        let ox = ca, oy = sa * flat
        let vx = -sa * dir, vy = ca * flat * dir
        let hx = vx * ct - vy * st, hy = vx * st + vy * ct
        return Point(x: ox * ct - oy * st, y: ox * st + oy * ct, z: sa,
                     front: MapSparkle.frontness(sa), headingDeg: atan2(hy, hx) * 180 / .pi)
    }

    /// 정면을 지날 때의 반짝임(0..1) — Android `orbitGlint`.
    static func glint(_ z: Double) -> Double {
        let g = min(max((z - 0.5) / 0.5, 0), 1)
        return g * g * (3 - 2 * g)
    }

    /// 원근 배율 — 뒤(0.86) … 앞(1.14). Android `orbitPerspective`.
    static func perspective(_ z: Double) -> Double { 0.86 + 0.14 * (z + 1) }

    /// 혜성 하나 설치 — wrapper(궤도 위치 · 원근 · 앞뒤 zPosition) → gate(줌 게이트 alpha) → rotator(진행 방향) → image.
    /// [backAlpha] 뒤쪽 불투명도, [glintAlpha] 정면 반짝이 불투명도에 주는 몫, [glintScale] 정면에서 커지는 몫. 위상은 timeOffset.
    static func install(
        on host: UIView,
        image: UIImage,
        imageSize: CGSize,
        center: CGPoint,
        radius: CGFloat,
        flat: Double,
        tilt: Double,
        period: Double,
        dir: Double,
        phase: Double,
        backAlpha: Double,
        glintAlpha: Double,
        glintScale: Double
    ) -> (gate: UIView, image: UIImageView, jobs: [StarAnimJob]) {

        let wrapper = UIView(frame: CGRect(x: center.x - imageSize.width / 2, y: center.y - imageSize.height / 2,
                                           width: imageSize.width, height: imageSize.height))
        let gate = UIView(frame: wrapper.bounds)
        let rotator = UIView(frame: gate.bounds)
        let iv = UIImageView(image: image)
        iv.frame = rotator.bounds
        for v in [wrapper, gate, rotator, iv] as [UIView] { v.isUserInteractionEnabled = false }
        rotator.addSubview(iv)
        gate.addSubview(rotator)
        wrapper.addSubview(gate)
        host.addSubview(wrapper)

        let steps = 72
        var points: [NSValue] = [], scales: [Double] = [], alphas: [Double] = [], zs: [Double] = [], headings: [Double] = []
        var prev: Double?
        for i in 0...steps {
            let p = point(dir * 2 * .pi * Double(i) / Double(steps), flat: flat, tilt: tilt, dir: dir)
            points.append(NSValue(cgPoint: CGPoint(x: center.x + CGFloat(p.x) * radius, y: center.y + CGFloat(p.y) * radius)))
            let g = glint(p.z)
            scales.append(perspective(p.z) * (1 + glintScale * g))
            alphas.append((backAlpha + (1 - backAlpha) * p.front) * ((1 - glintAlpha) + glintAlpha * g))
            zs.append(p.front >= 0.5 ? 1 : -1)
            // 진행 방향은 이어지게 풀어 둔다(359° → 1° 를 거꾸로 한 바퀴 돌지 않게).
            var deg = p.headingDeg
            if let pd = prev {
                while deg - pd > 180 { deg -= 360 }
                while deg - pd < -180 { deg += 360 }
            }
            prev = deg
            headings.append(deg * .pi / 180)
        }
        let keyTimes = (0...steps).map { NSNumber(value: Double($0) / Double(steps)) }
        wrapper.layer.zPosition = CGFloat(zs[0])
        wrapper.layer.position = points[0].cgPointValue

        func keyframe(_ keyPath: String, _ values: [Any], discrete: Bool = false) -> CAKeyframeAnimation {
            let a = CAKeyframeAnimation(keyPath: keyPath)
            // discrete 는 keyTimes 가 값보다 하나 많아야 한다(마지막 칸의 끝 = 1).
            a.values = discrete ? Array(values.dropLast()) : values
            a.keyTimes = keyTimes
            a.calculationMode = discrete ? .discrete : .linear
            a.duration = period
            a.repeatCount = .infinity
            a.timeOffset = phase * period
            a.isRemovedOnCompletion = false
            return a
        }

        let jobs = [
            StarAnimJob(layer: wrapper.layer, key: "orbit", anim: keyframe("position", points)),
            StarAnimJob(layer: wrapper.layer, key: "orbit-depth", anim: keyframe("transform.scale", scales)),
            StarAnimJob(layer: wrapper.layer, key: "orbit-alpha", anim: keyframe("opacity", alphas)),
            StarAnimJob(layer: wrapper.layer, key: "orbit-z", anim: keyframe("zPosition", zs, discrete: true)),
            StarAnimJob(layer: rotator.layer, key: "orbit-heading", anim: keyframe("transform.rotation.z", headings)),
        ]
        return (gate, iv, jobs)
    }
}

// MARK: - Star Marker View

/// 단일/겹친(머지) 별 공용 마커 뷰.
final class StarMarkerView: MLNAnnotationView {

    // ── 겹친 별 위성 ──
    private static let maxOrbitStars = 4

    /// 멤버 혜성 공전 — 약 15초에 한 바퀴, 멤버 사이 2.4rad(황금각에 가까워 몇 개든 고르게 벌어진다). Android `MEMBER_ORBIT_*`.
    private static let memberOrbitSpeed = 0.42
    private static let memberOrbitSpacing = 2.4

    /// 멤버 혜성 머리 최소 크기(pt) — 별 크기의 40% 와 이것 중 큰 쪽.
    private static let satSize: CGFloat = 14

    /// 위성 기본 불투명도(줌 게이트가 열렸을 때).
    static let satelliteAlpha: CGFloat = 0.92

    /// 위성이 보이기 시작하는 줌 / 완전히 드러나는 줌 —
    /// Android `COMET_MIN_ZOOM`/`COMET_FULL_ZOOM`(MapComets.kt) 대응.
    static let satelliteMinZoom: Double = 11
    static let satelliteFullZoom: Double = 13

    /// 줌 → 위성 불투명도(0 = 안 보임).
    static func satelliteGateAlpha(forZoom zoom: Double) -> CGFloat {
        guard zoom > satelliteMinZoom else { return 0 }
        guard zoom < satelliteFullZoom else { return satelliteAlpha }
        let t = (zoom - satelliteMinZoom) / (satelliteFullZoom - satelliteMinZoom)
        return satelliteAlpha * CGFloat(t)
    }

    // ── 부유 — Android: 모든 별 sin(t·1.6 + phase)·4dp ──
    private static let floatAmp: CGFloat = 4.0
    private static let floatPeriod: Double = 2 * .pi / 1.6

    /// 탭 영역 최소 한 변(pt) — 멀리서(별이 아주 작을 때)도 한 번에 눌리게. Apple HIG 최소 터치 크기 44pt
    /// (2026-10-08: 28 → 44 — 작게 보이는 별이 한 번에 안 열린다는 피드백).
    private static let minTapSide: CGFloat = 44

    private let side: CGFloat
    private let zoomHost = UIView()
    private let floatHost = UIView()
    /// 줌 게이트 alpha 를 받는 혜성들의 gate 뷰(스파클 / 겹친 별 멤버).
    private var sparkleGates: [UIView] = []
    private var memberGates: [UIView] = []
    private var jobs: [StarAnimJob] = []
    private var zoomScale: CGFloat = 1

    /// 멤버 혜성 궤도 반경 — 스파클(0.56) 바깥에서 대표 별을 크게 감싼다. 멤버마다 조금씩 바깥으로.
    private static func satelliteRadius(markerSize: CGFloat, satIndex: Int) -> CGFloat {
        markerSize * (0.75 + 0.05 * CGFloat(satIndex))
    }

    /// 뷰 한 변(줌 배율 1 기준) — 스파클 궤도/위성/부유 진폭이 다 들어가는 크기.
    private static func boxSide(for annotation: DiaryAnnotation) -> CGFloat {
        let markerSize = annotation.markerSize

        guard annotation.members.count > 1 else {
            return (markerSize / 2 + markerSize * MapSparkle.maxOrbitExtentRatio) * 2
        }

        let members = annotation.members.dropFirst().prefix(maxOrbitStars)

        let maxRadius = satelliteRadius(
            markerSize: markerSize,
            satIndex: max(members.count - 1, 0)
        )

        let satExtent = maxRadius + max(satSize, markerSize * 0.4) / 2 + floatAmp
        let orbitExtent = markerSize * MapSparkle.maxOrbitExtentRatio + floatAmp

        return max(satExtent, orbitExtent) * 1.8
    }

    init(annotation: DiaryAnnotation) {

        let side = Self.boxSide(for: annotation)
        self.side = side

        super.init(reuseIdentifier: nil) // 재사용 안 함 — 별마다 자기만의 모션(시드)을 가진다

        frame = CGRect(x: 0, y: 0, width: side, height: side)
        backgroundColor = .clear
        clipsToBounds = false
        scalesWithViewingDistance = true

        let markerSize = annotation.markerSize
        let center = CGPoint(x: side / 2, y: side / 2)

        zoomHost.frame = CGRect(x: 0, y: 0, width: side, height: side)
        floatHost.frame = zoomHost.bounds
        zoomHost.isUserInteractionEnabled = false
        floatHost.isUserInteractionEnabled = false

        addSubview(zoomHost)
        zoomHost.addSubview(floatHost)

        // 이 별만의 시드 — id 의 안정 해시(+ 좌표로 id 없는 합성 별도 구분).
        let repId = annotation.diary.id ?? "\(annotation.diary.latitude),\(annotation.diary.longitude)"
        var rng = StarMotionRandom(seed: StarMotionHash.fnv1a(repId))

        // 부유 — 위상은 연속값(별마다 전부 다름), 주기도 ±5% 흔든다.
        let floatPeriod = Self.floatPeriod * rng.range(0.95, 1.05)
        jobs.append(StarAnimJob(
            layer: floatHost.layer,
            key: "float",
            anim: Self.sway(
                keyPath: "transform.translation.y",
                amp: Self.floatAmp,
                period: floatPeriod,
                phase: rng.unit()
            )
        ))

        // 별 곁 혜성 — 대표 별과 같은 부모(floatHost)에 두고 zPosition 으로 별 앞/뒤를 오간다.
        let sparkles = MapSparkle.install(
            on: floatHost,
            center: center,
            markerSize: markerSize,
            sizeMult: annotation.sizeMult,
            starType: annotation.diary.starType,
            starColor: annotation.diary.starColor,
            rng: &rng
        )
        sparkleGates = sparkles.gates
        jobs.append(contentsOf: sparkles.jobs)

        // 겹친 별 멤버 혜성.
        if annotation.members.count > 1 {
            installMembers(annotation, center: center, markerSize: markerSize, rng: &rng)
        }

        // 대표 별.
        let rep = UIImageView(
            image: StarImageRenderer.image(
                type: annotation.diary.starType,
                colorIndex: annotation.diary.starColor,
                size: markerSize
            )
        )
        rep.frame = CGRect(
            x: center.x - markerSize / 2,
            y: center.y - markerSize / 2,
            width: markerSize,
            height: markerSize
        )
        rep.contentMode = .scaleAspectFit
        rep.layer.zPosition = 0 // 혜성: 앞 +1 / 뒤 -1
        floatHost.addSubview(rep)

        // 유리 결 빛 — 흰 빛줄기가 별 몸을 왼쪽 위 → 오른쪽 아래로 훑고, 작은 반짝이 하나가 그 빛을 타고 건너간다.
        installSheen(over: rep.frame, type: annotation.diary.starType, markerSize: markerSize, rng: &rng)

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// 겹친 별 멤버 혜성 — 대표 둘레를 천천히 도는 3D 원 궤도. 멤버끼리는 같은 속도라 고르게 벌어진 채 한 고리처럼 돌고,
    /// 궤도면만 멤버마다 조금씩 달라(납작함 0.36 + 0.04i) 겹친 고리로 보인다. Android `MEMBER_ORBIT_*`/`memberOrbit*` 와 같은 값.
    private func installMembers(
        _ annotation: DiaryAnnotation,
        center: CGPoint,
        markerSize: CGFloat,
        rng: inout StarMotionRandom
    ) {
        let members = Array(annotation.members.dropFirst().prefix(Self.maxOrbitStars))
        let head = max(Self.satSize, markerSize * 0.4)
        let base = rng.unit()
        let tilt = 0.22 + (Double(rng.next() % 4) - 1.5) * 0.10
        let dir: Double = rng.flip() ? 1 : -1
        let period = 2 * Double.pi / Self.memberOrbitSpeed

        for (i, m) in members.enumerated() {
            let orb = Orbit3D.install(
                on: floatHost,
                image: MapSparkle.memberComet(type: m.starType, colorIndex: m.starColor, head: head),
                imageSize: CGSize(width: head * MapSparkle.cometAspect, height: head),
                center: center,
                radius: Self.satelliteRadius(markerSize: markerSize, satIndex: i),
                flat: 0.36 + 0.04 * Double(i),
                tilt: tilt,
                period: period,
                dir: dir,
                phase: (base + Double(i) * Self.memberOrbitSpacing / (2 * .pi)).truncatingRemainder(dividingBy: 1),
                backAlpha: 0.7,
                glintAlpha: 0.28,
                glintScale: 0.18
            )
            orb.gate.alpha = Self.satelliteAlpha
            memberGates.append(orb.gate)
            jobs.append(contentsOf: orb.jobs)
        }
    }

    // MARK: Sheen

    /// 유리 결 빛 — 지도 시안 drawSheen, Android `sheenBitmap`/`sheenImageExpression` 과 같은 값:
    /// 주기 5.5~8초(별마다), 지나가는 시간은 주기의 26%, 불투명도 0.75 × sin. 시안(7~11초 · 30%)보다 조금 빠르다.
    private static let sheenPeriodMin = 5.5
    private static let sheenPeriodSpread = 2.5
    private static let sheenWindow = 0.26
    private static let sheenOpacity = 0.75

    private func installSheen(over frame: CGRect, type: Int, markerSize: CGFloat, rng: inout StarMotionRandom) {
        let starSize = markerSize * 0.78
        let period = Self.sheenPeriodMin + rng.unit() * Self.sheenPeriodSpread
        let offset = rng.unit() * period
        let w = Self.sheenWindow

        let host = UIView(frame: frame)
        host.isUserInteractionEnabled = false
        host.alpha = 0
        host.layer.zPosition = 0.5 // 별 바로 위, 앞쪽 혜성 아래
        floatHost.addSubview(host)

        // 빛줄기 — 별 몸(이미지 78%) 모양으로 오린다.
        let bandHost = UIView(frame: host.bounds)
        bandHost.isUserInteractionEnabled = false
        let off = (markerSize - starSize) / 2
        let mask = CAShapeLayer()
        mask.path = StarShape(type: type).path(in: CGRect(x: off, y: off, width: starSize, height: starSize)).cgPath
        bandHost.layer.mask = mask
        host.addSubview(bandHost)

        // 시안: 128 캔버스에서 띠 중심 p = -46 + q·220, 반폭 30 — 사선(↘) 그라데이션. 큰 정사각 레이어의 대각선 가운데에 띠를 둔다.
        let hw = 30.0 / 128.0 * markerSize
        let big = markerSize * 3
        let band = CAGradientLayer()
        band.bounds = CGRect(x: 0, y: 0, width: big, height: big)
        band.startPoint = CGPoint(x: 0, y: 0)
        band.endPoint = CGPoint(x: 1, y: 1)
        let a = Double(hw / big)
        let clear = UIColor.white.withAlphaComponent(0).cgColor
        band.colors = [clear, clear, UIColor.white.withAlphaComponent(0.95).cgColor, clear, clear]
        band.locations = [0, NSNumber(value: 0.5 - a), 0.5, NSNumber(value: 0.5 + a), 1]
        func p(_ q: Double) -> CGFloat { CGFloat(-46 + q * 220) / 128 * markerSize }
        band.position = CGPoint(x: p(0), y: p(0))
        bandHost.layer.addSublayer(band)

        // 빛을 타고 건너가는 반짝(중심에서 (q - 0.5)·0.62·크기, 반지름 0.17·크기·sin)
        let rMax = 0.17 * starSize
        let spark = CALayer()
        spark.bounds = CGRect(x: 0, y: 0, width: rMax * 2, height: rMax * 2)
        spark.contents = Self.sheenSparkle(radius: rMax).cgImage
        spark.opacity = 0
        host.layer.addSublayer(spark)

        let steps = 24
        var keyTimes: [NSNumber] = []
        var hostAlpha: [NSNumber] = []
        var sparkPos: [NSValue] = []
        var sparkScale: [NSNumber] = []
        var sparkAlpha: [NSNumber] = []
        for i in 0...steps {
            let q = Double(i) / Double(steps)
            let e = sin(q * .pi)
            keyTimes.append(NSNumber(value: q * w))
            hostAlpha.append(NSNumber(value: Self.sheenOpacity * e))
            let c = markerSize / 2 + CGFloat(q - 0.5) * 0.62 * starSize
            sparkPos.append(NSValue(cgPoint: CGPoint(x: c, y: c)))
            sparkScale.append(NSNumber(value: max(e, 0.001)))
            sparkAlpha.append(NSNumber(value: 0.95 * e))
        }
        // 지나간 뒤에는 다음 주기까지 쉰다.
        keyTimes.append(1)
        hostAlpha.append(0)
        sparkPos.append(sparkPos[sparkPos.count - 1])
        sparkScale.append(0.001)
        sparkAlpha.append(0)

        func keyframe(_ keyPath: String, _ values: [Any], _ times: [NSNumber]) -> CAKeyframeAnimation {
            let anim = CAKeyframeAnimation(keyPath: keyPath)
            anim.values = values
            anim.keyTimes = times
            anim.calculationMode = .linear
            anim.duration = period
            anim.repeatCount = .infinity
            anim.timeOffset = offset
            anim.isRemovedOnCompletion = false
            return anim
        }

        let bandFrom = NSValue(cgPoint: CGPoint(x: p(0), y: p(0)))
        let bandTo = NSValue(cgPoint: CGPoint(x: p(1), y: p(1)))
        let bandPos = keyframe("position", [bandFrom, bandTo, bandTo], [0, NSNumber(value: w), 1])

        jobs.append(StarAnimJob(layer: host.layer, key: "sheen-alpha", anim: keyframe("opacity", hostAlpha, keyTimes)))
        jobs.append(StarAnimJob(layer: band, key: "sheen-pos", anim: bandPos))
        jobs.append(StarAnimJob(layer: spark, key: "sheen-pos", anim: keyframe("position", sparkPos, keyTimes)))
        jobs.append(StarAnimJob(layer: spark, key: "sheen-scale", anim: keyframe("transform.scale", sparkScale, keyTimes)))
        jobs.append(StarAnimJob(layer: spark, key: "sheen-op", anim: keyframe("opacity", sparkAlpha, keyTimes)))
    }

    private static var sheenSparkleCache: [Int: UIImage] = [:]

    /// 빛을 타고 가는 굽은 4갈래 반짝 + 둥근 번짐(Android `sheenBitmap` 의 반짝과 같은 비율).
    private static func sheenSparkle(radius r: CGFloat) -> UIImage {
        let key = Int((r * 4).rounded())
        if let cached = sheenSparkleCache[key] { return cached }
        let side = r * 2
        let fmt = UIGraphicsImageRendererFormat()
        fmt.opaque = false
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: fmt).image { ctx in
            let cg = ctx.cgContext
            let c = CGPoint(x: r, y: r)
            if let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                     colors: [UIColor.white.withAlphaComponent(0.5).cgColor,
                                              UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                                     locations: [0, 1]) {
                cg.drawRadialGradient(grad, startCenter: c, startRadius: 0, endCenter: c, endRadius: r * 0.9, options: [])
            }
            let k = r * 0.10
            let path = UIBezierPath()
            path.move(to: CGPoint(x: c.x, y: c.y - r))
            path.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), controlPoint: CGPoint(x: c.x + k, y: c.y - k))
            path.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), controlPoint: CGPoint(x: c.x + k, y: c.y + k))
            path.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), controlPoint: CGPoint(x: c.x - k, y: c.y + k))
            path.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), controlPoint: CGPoint(x: c.x - k, y: c.y - k))
            path.close()
            cg.setFillColor(UIColor.white.cgColor)
            cg.addPath(path.cgPath)
            cg.fillPath()
        }
        sheenSparkleCache[key] = img
        return img
    }

    // MARK: Animation

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        installAnimations()
    }

    @objc private func appWillEnterForeground() {
        guard window != nil else { return }
        installAnimations()
    }

    /// 빠져 있는 무한 애니메이션만 다시 건다(이미 돌고 있으면 건드리지 않아 위상이 튀지 않는다).
    private func installAnimations() {
        for job in jobs where job.layer.animation(forKey: job.key) == nil {
            job.layer.add(job.anim, forKey: job.key)
        }
    }

    /// -amp ↔ +amp 왕복(sin 근사) — [period] 가 한 사이클, [phase](0..1)는 timeOffset 으로.
    private static func sway(
        keyPath: String,
        amp: CGFloat,
        period: Double,
        phase: Double
    ) -> CABasicAnimation {

        let a = CABasicAnimation(keyPath: keyPath)
        a.fromValue = -amp
        a.toValue = amp
        a.duration = period / 2
        a.autoreverses = true
        a.repeatCount = .infinity
        a.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        a.timeOffset = phase * period
        a.isRemovedOnCompletion = false
        return a
    }

    // MARK: Zoom

    /// 줌 단계별 별 크기 배율 — zoomHost 의 transform 으로만 준다(어노테이션 뷰 자신의 transform 은 MapLibre 몫).
    /// bounds 도 같이 줄이고 zoomHost 를 가운데로 다시 맞춰, 탭 영역이 별 크기를 따라가게 한다.
    func applyZoomScale(_ s: CGFloat) {
        guard abs(s - zoomScale) > 0.001 else { return }
        zoomScale = s

        let w = max(side * s, Self.minTapSide)
        bounds = CGRect(x: 0, y: 0, width: w, height: w)
        zoomHost.center = CGPoint(x: w / 2, y: w / 2)
        zoomHost.transform = CGAffineTransform(scaleX: s, y: s)
    }

    /// 줌 게이트 반영 — 일정 줌 이하로 빼면 스파클/위성이 사라진다(Android 패리티).
    func applyZoomGate(sparkle: CGFloat, satellite: CGFloat) {
        for g in sparkleGates {
            g.alpha = sparkle
            g.isHidden = sparkle <= 0.001
        }
        for g in memberGates {
            g.alpha = satellite
            g.isHidden = satellite <= 0.001
        }
    }
}
