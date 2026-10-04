import MapLibre
import QuartzCore
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 지도 별 마커 뷰 — Android DiaryMap/DiaryMapMarkers 의 부유·스파클·위성 패리티(2026-10-04 재작성).
//
// 뷰 계층(⚠️ 어노테이션 뷰 자신의 transform/layer.transform 은 **건드리지 않는다**):
//   StarMarkerView(MLNAnnotationView)   ← MapLibre 가 scalesWithViewingDistance 로 layer.transform 에 기울기 원근 배율을 넣는 곳
//     └ zoomHost    : 줌 단계별 별 크기 배율(transform). 뷰 bounds 도 같이 줄여 탭 영역이 별 크기를 따라가게 한다.
//         └ floatHost   : 위아래 부유(CABasicAnimation, 별마다 다른 위상) — 스파클/위성/대표 별이 한 덩어리로 둥실
//             ├ sparkleHost : 공전 스파클(줌 게이트 alpha) — 별마다 반경/속도/방향/기울기/위상/반짝임이 다 다르다
//             ├ 위성(겹친 별) : 줌 게이트 alpha, 별마다 앵커 회전 + 떠다니는 위상
//             └ 대표 별 이미지
// MapStarReveal 은 어노테이션 뷰의 alpha + layer.sublayerTransform 을 쓴다(위 구조와 충돌 없음).
//
// "별마다 다르게": 다이어리 id 의 **안정 해시(FNV-1a)** 로 시드한 난수. Swift `hashValue` 는 실행마다 달라지므로 쓰지 않는다.
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

    /// 최대 궤도 영역 비율.
    static let maxOrbitExtentRatio: CGFloat = 0.62 + 0.16

    /// 스파클이 보이기 시작하는 줌 / 완전히 드러나는 줌 — Android `sparkleSizeExpression`(줌 11 이하 크기 0, 13 에서 0.55 …) 대응.
    /// 멀리서는 별 곁 반짝이가 잡동사니로 보이므로 지운다.
    static let minZoom: Double = 11
    static let fullZoom: Double = 13

    /// 줌 → 스파클 불투명도(0 = 안 보임).
    static func zoomOpacity(forZoom zoom: Double) -> CGFloat {
        guard zoom > minZoom else { return 0 }
        guard zoom < fullZoom else { return 1 }
        return CGFloat((zoom - minZoom) / (fullZoom - minZoom))
    }

    /// sizeMult에 따른 파티클 수.
    static func particleCount(sizeMult: Double) -> Int {
        if sizeMult >= 2.6 { return 3 }
        if sizeMult >= 1.6 { return 2 }
        return 1
    }

    /// 공전 파티클 설치 — 별마다 다른 반경/속도/방향/기울기/위상/반짝임. 걸어야 할 애니메이션 목록을 돌려준다.
    static func install(
        on host: UIView,
        center: CGPoint,
        markerSize: CGFloat,
        sizeMult: Double,
        starType: Int,
        starColor: Int,
        rng: inout StarMotionRandom
    ) -> [StarAnimJob] {

        let count = particleCount(sizeMult: sizeMult)
        let big = sizeMult >= bigStarThreshold

        let radii: [CGFloat] = [markerSize * 0.42, markerSize * 0.56, markerSize * 0.62]
        let sizes: [CGFloat] = [markerSize * 0.30, markerSize * 0.24, markerSize * 0.16]
        let periods: [Double] = [2 * .pi / 1.1, 2 * .pi / 0.8, 2 * .pi / 1.5]

        // 안쪽 궤도 방향은 별마다 랜덤, 바깥 궤도는 반대(Android 의 안/밖 역방향 관계 유지).
        let firstClockwise = rng.flip()

        var jobs: [StarAnimJob] = []

        for set in 0..<count {

            let size = sizes[set]

            let image = big
                ? StarImageRenderer.image(type: starType, colorIndex: starColor, size: size)
                : whiteSparkle(size: size)

            let iv = UIImageView(image: image)
            iv.bounds = CGRect(x: 0, y: 0, width: size, height: size)
            iv.center = center
            iv.alpha = 0.6 // 반짝임 애니메이션(0.30↔0.90)의 중간값 — 애니메이션이 빠져도 어색하지 않게

            host.addSubview(iv)

            let speed = rng.range(0.8, 1.25)
            let radius = radii[set] * CGFloat(rng.range(0.88, 1.12))
            let tilt = CGFloat(rng.range(-0.5, 0.5))
            let phase = rng.unit()
            let clockwise = (set == 1) ? !firstClockwise : firstClockwise
            let period = periods[set] / speed

            jobs.append(StarAnimJob(
                layer: iv.layer,
                key: "orbit",
                anim: orbitAnimation(
                    center: center,
                    radius: radius,
                    tilt: tilt,
                    period: period,
                    clockwise: clockwise,
                    phase: phase
                )
            ))

            // Android 식 반짝임 — op = 0.30 + 0.60·(…), 각속도 2.4…3.45, 위상 랜덤.
            let twPeriod = 2 * Double.pi / rng.range(2.4, 3.45)
            let tw = CABasicAnimation(keyPath: "opacity")
            tw.fromValue = 0.30
            tw.toValue = 0.90
            tw.duration = twPeriod / 2
            tw.autoreverses = true
            tw.repeatCount = .infinity
            tw.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
            tw.timeOffset = rng.unit() * twPeriod
            tw.isRemovedOnCompletion = false

            jobs.append(StarAnimJob(layer: iv.layer, key: "twinkle", anim: tw))
        }

        return jobs
    }

    /// 기울어진 타원 궤도를 도는 position 키프레임 애니메이션(위상은 timeOffset).
    private static func orbitAnimation(
        center: CGPoint,
        radius: CGFloat,
        tilt: CGFloat,
        period: Double,
        clockwise: Bool,
        phase: Double
    ) -> CAKeyframeAnimation {

        let ySquash: CGFloat = 0.55

        // 원점 기준 타원 → 기울기 회전 → 중심으로 평행이동.
        let oval = UIBezierPath(
            ovalIn: CGRect(
                x: -radius,
                y: -radius * ySquash,
                width: radius * 2,
                height: radius * ySquash * 2
            )
        )
        oval.apply(
            CGAffineTransform(rotationAngle: tilt)
                .concatenating(CGAffineTransform(translationX: center.x, y: center.y))
        )

        let path = clockwise ? oval : oval.reversing()

        let anim = CAKeyframeAnimation(keyPath: "position")
        anim.path = path.cgPath
        anim.duration = period
        anim.calculationMode = .paced
        anim.repeatCount = .infinity
        anim.isRemovedOnCompletion = false
        anim.timeOffset = phase * period

        return anim
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

            let path = StarShape(type: 0).path(in: rect).cgPath

            cg.setShadow(
                offset: .zero,
                blur: px * 0.18,
                color: UIColor.white.withAlphaComponent(0.9).cgColor
            )

            cg.setFillColor(UIColor.white.cgColor)
            cg.addPath(path)
            cg.fillPath(using: .evenOdd)
        }

        whiteCache[key] = img

        return img
    }
}

// MARK: - Star Marker View

/// 단일/겹친(머지) 별 공용 마커 뷰.
final class StarMarkerView: MLNAnnotationView {

    // ── 겹친 별 위성 ──
    private static let maxOrbitStars = 4

    private static let anchorAngles: [CGFloat] = [-0.6, 2.3, 4.1, 1.1]

    // 위성 부유 주기/위상 — Android `DiaryMap` 의 위성 식과 같은 값(값 drift 금지):
    //   driftX = sin(t × (0.7 + 0.14i) + i × 2.1) × 0.8
    //   driftY = sin(t × (1.15 + 0.18i) + i × 1.4) × 1.0
    // 주기 = 2π / 각속도, 위상 = (i × 위상상수) / 2π (0..1 비율). 여기에 **별마다 랜덤 오프셋**을 더해 다 다르게 만든다.
    private static let driftPeriodsX: [Double] = [
        2 * Double.pi / 0.70,
        2 * Double.pi / 0.84,
        2 * Double.pi / 0.98,
        2 * Double.pi / 1.12
    ]

    private static let driftPeriodsY: [Double] = [
        2 * Double.pi / 1.15,
        2 * Double.pi / 1.33,
        2 * Double.pi / 1.51,
        2 * Double.pi / 1.69
    ]

    private static let driftPhasesX: [Double] = [0, 2.1, 4.2, 6.3].map {
        ($0 / (2 * Double.pi)).truncatingRemainder(dividingBy: 1)
    }

    private static let driftPhasesY: [Double] = [0, 1.4, 2.8, 4.2].map {
        ($0 / (2 * Double.pi)).truncatingRemainder(dividingBy: 1)
    }

    private static let satSize: CGFloat = 16

    /// 위성 기본 불투명도(줌 게이트가 열렸을 때).
    static let satelliteAlpha: CGFloat = 0.92

    /// 위성이 보이기 시작하는 줌 / 완전히 드러나는 줌 —
    /// Android `orbitSizeExpression`(줌 11 이하 0) + 부유 갱신 게이트(zoom > 11.1) 대응.
    static let satelliteMinZoom: Double = 11
    static let satelliteFullZoom: Double = 13

    /// 줌 → 위성 불투명도(0 = 안 보임).
    static func satelliteGateAlpha(forZoom zoom: Double) -> CGFloat {
        guard zoom > satelliteMinZoom else { return 0 }
        guard zoom < satelliteFullZoom else { return satelliteAlpha }
        let t = (zoom - satelliteMinZoom) / (satelliteFullZoom - satelliteMinZoom)
        return satelliteAlpha * CGFloat(t)
    }

    private static let driftAmpX: CGFloat = 0.8
    private static let driftAmpY: CGFloat = 1.0

    // ── 부유 — Android: 모든 별 sin(t·1.6 + phase)·4dp ──
    private static let floatAmp: CGFloat = 4.0
    private static let floatPeriod: Double = 2 * .pi / 1.6

    /// 탭 영역 최소 한 변(pt) — 멀리서(별이 아주 작을 때)도 누를 수 있게.
    private static let minTapSide: CGFloat = 28

    private let side: CGFloat
    private let zoomHost = UIView()
    private let floatHost = UIView()
    private let sparkleHost = UIView()
    private var satellites: [UIImageView] = []
    private var jobs: [StarAnimJob] = []
    private var zoomScale: CGFloat = 1

    private static func satelliteRadius(markerSize: CGFloat, satIndex: Int) -> CGFloat {
        markerSize * 0.09 + 0.5 + CGFloat(satIndex) * 1.0
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

        let satExtent = maxRadius + satSize / 2 + driftAmpY + floatAmp
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
        sparkleHost.frame = floatHost.bounds
        zoomHost.isUserInteractionEnabled = false
        floatHost.isUserInteractionEnabled = false
        sparkleHost.isUserInteractionEnabled = false

        addSubview(zoomHost)
        zoomHost.addSubview(floatHost)
        floatHost.addSubview(sparkleHost)

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

        // 스파클 먼저(대표 별 아래에 깔린다).
        jobs.append(contentsOf: MapSparkle.install(
            on: sparkleHost,
            center: center,
            markerSize: markerSize,
            sizeMult: annotation.sizeMult,
            starType: annotation.diary.starType,
            starColor: annotation.diary.starColor,
            rng: &rng
        ))

        // 겹친 별 위성.
        if annotation.members.count > 1 {
            installSatellites(annotation, center: center, markerSize: markerSize, rng: &rng)
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
        floatHost.addSubview(rep)

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

    private func installSatellites(
        _ annotation: DiaryAnnotation,
        center: CGPoint,
        markerSize: CGFloat,
        rng: inout StarMotionRandom
    ) {

        let members = Array(annotation.members.dropFirst().prefix(Self.maxOrbitStars))

        // 별마다 앵커 전체를 같은 각도만큼 돌려 배치 자체가 달라 보이게.
        let rotation = CGFloat(rng.range(0, 2 * Double.pi))
        let offX = rng.unit()
        let offY = rng.unit()

        for (i, m) in members.enumerated() {

            let iv = UIImageView(
                image: StarImageRenderer.image(
                    type: m.starType,
                    colorIndex: m.starColor,
                    size: Self.satSize
                )
            )
            iv.bounds = CGRect(x: 0, y: 0, width: Self.satSize, height: Self.satSize)

            let ang = Self.anchorAngles[i] + rotation
            let r = Self.satelliteRadius(markerSize: markerSize, satIndex: i)

            iv.center = CGPoint(
                x: center.x + cos(ang) * r,
                y: center.y + sin(ang) * 0.55 * r
            )
            iv.alpha = Self.satelliteAlpha

            floatHost.addSubview(iv)
            satellites.append(iv)

            jobs.append(StarAnimJob(
                layer: iv.layer,
                key: "drift-x",
                anim: Self.sway(
                    keyPath: "transform.translation.x",
                    amp: Self.driftAmpX,
                    period: Self.driftPeriodsX[i],
                    phase: (Self.driftPhasesX[i] + offX).truncatingRemainder(dividingBy: 1)
                )
            ))

            jobs.append(StarAnimJob(
                layer: iv.layer,
                key: "drift-y",
                anim: Self.sway(
                    keyPath: "transform.translation.y",
                    amp: Self.driftAmpY,
                    period: Self.driftPeriodsY[i],
                    phase: (Self.driftPhasesY[i] + offY).truncatingRemainder(dividingBy: 1)
                )
            ))
        }
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
        sparkleHost.alpha = sparkle
        sparkleHost.isHidden = sparkle <= 0.001
        for iv in satellites {
            iv.alpha = satellite
        }
    }
}
