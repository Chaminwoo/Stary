import CoreLocation
import MapLibre
import QuartzCore
import SwiftUI
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 필터 전환 "빛의 고리" 등장 — Android DiaryMap 의 revealKey 연출 패리티(2026-10-14, references/filtering.mp4).
//  · **지도 위 내 위치**에서 링이 퍼진다. 매 프레임 지도 위 원(반지름 m)을 화면에 투영하므로, 연출 중 지도를 움직여도
//    링이 지도에 붙어 함께 움직인다.
//  · 링이 닿는 순간 그 별이 차임과 함께 잠깐 커지고 밝아졌다가 제자리로 돌아오며 생긴다. 별마다 "내 위치까지 거리"로
//    닿는 시각을 정하므로 **화면 밖 별도** 링이 닿으면 소리가 난다.
//  · 링은 화면을 다 쓸 때까지 일정한 속도(wave), 그 뒤엔 점점 빨라지며(farTau) 먼 별까지 간다(maxTime 상한).
//  · 필터 빛은 링 안쪽(지나간 자리)부터 새 색으로 깔린다 — `FilterWaveState` 로 링 모양을 SwiftUI 오버레이에 넘긴다.
// 크기 팝은 어노테이션 뷰의 transform(줌 배율이 쓰는 자리)을 건드리지 않도록 layer.sublayerTransform 으로 준다.
// 수치는 Android DiaryMapMarkers REVEAL_* 와 동일(값 drift 금지).
// ─────────────────────────────────────────────────────────────────────────────

enum StarRevealFx {
    /// 링이 내 위치 → 화면의 가장 먼 모서리까지 **일정한 속도로** 퍼지는 시간(초).
    static let wave = 0.9
    /// 화면을 다 쓸고 난 뒤 링이 빨라지는 정도(초, 지수 시간 상수).
    static let farTau = 0.45
    /// 링이 별에 닿는 시각의 상한(초) — 이보다 먼 별은 이 시각에 한꺼번에 생긴다.
    static let maxTime = 3.6
    /// 링이 옅어져 사라지는 시간(초). 링은 모든 별에 닿고 + 지금 보고 있는 화면을 다 덮을 때까지 선명하게 계속 퍼지고,
    /// 그 뒤 퍼지는 채로 옅어진다(예전엔 정해진 시각에 끝나 화면 안에서 멈춘 듯 사라졌다 — 2026-10-14).
    static let ringFade = 0.3
    /// 화면을 다 덮지 못해도(내 위치가 아주 멀리 있는 등) 이 시각엔 옅어지기 시작한다(초).
    static let ringMaxTime = 6.0
    /// 지도 위 링을 화면에 투영할 때 쓰는 꼭짓점 수 — 구불구불한 선이 매끈하게 보이도록 넉넉히.
    static let ringSegments = 180
    /// 링의 구불거림 — 반지름 대비 비율(반지름 = R × (1 + wobble × RingWobble.at)).
    static let wobble = 0.07
    /// 링이 닿은 별 하나가 생기는 시간(초) — 크기 0.2 → 1.55 → 1 + 발광.
    static let pop = 0.52
    /// 링 선 굵기(pt) — 구불구불한 선 하나만 그린다(번짐·안쪽 빛 띠 없음, 2026-10-14 사용자 요청으로 2.2 → 1.6).
    static let ringStroke: CGFloat = 1.6
    /// 별 발광 반경 = 화면에 보이는 별 반지름 × 이 배율(하한 flashMin).
    static let flashScale: CGFloat = 1.9
    static let flashMin: CGFloat = 12

    /// 시작 후 t 초에 링 반지름(m) — sweep = 내 위치에서 화면의 가장 먼 모서리까지 거리(Android revealRadiusM).
    static func radius(at t: Double, sweep: Double) -> Double {
        if t <= wave { return sweep * t / wave }
        return sweep * (1 + farTau / wave * (exp((t - wave) / farTau) - 1))
    }

    /// 내 위치에서 dist(m) 떨어진 별에 링이 닿는 시각(초) — radius 의 역함수, maxTime 상한(Android revealHitMs).
    static func hitTime(distance dist: Double, sweep: Double) -> Double {
        let k = dist / max(sweep, 1)
        let t = k <= 1 ? wave * k : wave + farTau * log(1 + (k - 1) * wave / farTau)
        return min(max(t, 0), maxTime)
    }

    /// 등장 팝 곡선 — u(0..1) → 크기 배율(Android revealPopScale 동일). 0.2 에서 튀어 올라 1.55 를 찍고(u=0.3) 1 로.
    static func popScale(_ u: Double) -> CGFloat {
        let x = min(max(u, 0), 1)
        if x < 0.3 {
            let k = 1 - pow(1 - x / 0.3, 3)
            return CGFloat(0.2 + (1.55 - 0.2) * k)
        }
        let v = (x - 0.3) / 0.7
        let e = v < 0.5 ? 2 * v * v : 1 - pow(-2 * v + 2, 2) / 2 // ease-in-out
        return CGFloat(1.55 + (1 - 1.55) * e)
    }
}

/// 링 구불거림 모양 — 각도 θ(라디안, 동쪽 0·반시계)와 시각(초)에 따른 −1..1 값.
/// 주파수 3·7·13 의 사인을 섞고 시간에 따라 서로 다른 방향으로 흘려, 테두리가 꿈틀대며 넓어지는 느낌을 낸다.
/// 위상은 연출마다 무작위. Android `RingWobble`(DiaryMapMarkers.kt)과 **같은 식**.
struct RingWobble {
    let p1 = Double.random(in: 0..<(2 * Double.pi))
    let p2 = Double.random(in: 0..<(2 * Double.pi))
    let p3 = Double.random(in: 0..<(2 * Double.pi))

    func at(_ theta: Double, _ t: Double) -> Double {
        0.50 * sin(3 * theta + p1 + 0.9 * t)
            + 0.32 * sin(7 * theta + p2 - 1.6 * t)
            + 0.18 * sin(13 * theta + p3 + 2.4 * t)
    }
}

extension MapLibreView.Coordinator {

    /// 이번 updateUIView 에서 등장 연출을 해야 하는지 — revealKey 가 바뀌었을 때 1회만 true.
    /// 첫 호출(지도 첫 표시)은 기준값만 기억하고 false.
    func consumeRevealRequest(_ key: Int) -> Bool {
        guard let last = lastRevealKey else {
            lastRevealKey = key
            return false
        }
        guard last != key else { return false }
        lastRevealKey = key
        return true
    }

    /// 등장 준비 — 링 중심(내 위치)·화면을 쓰는 거리를 정하고, **화면 안팎의 모든 별**마다 링이 닿는 시각을 정한다.
    /// (뷰가 생기기 전에 호출해도 된다.)
    func prepareStarReveal(_ annotations: [MLNAnnotation], mapView: MLNMapView) {
        let center = parent.userLocation ?? mapView.centerCoordinate
        let here = CLLocation(latitude: center.latitude, longitude: center.longitude)
        let sweep = revealSweepMeters(mapView, center: center)
        let wobble = RingWobble()
        revealCenter = center
        revealSweepM = sweep
        revealWobble = wobble
        revealFadeStart = -1

        // 별마다 링이 닿는 시각 — 구불구불한 선이 그 별 방향에서 실제로 닿는 순간에 맞춘다(Android hitTimeOf 와 같은 보정).
        let mPerLng = 111_320 * max(cos(center.latitude * .pi / 180), 0.01)
        revealDelays = [:]
        for case let a as DiaryAnnotation in annotations {
            guard let id = a.diary.id else { continue }
            let d = here.distance(from: CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude))
            let theta = atan2((a.coordinate.latitude - center.latitude) * 111_320,
                              (a.coordinate.longitude - center.longitude) * mPerLng)
            let base = StarRevealFx.hitTime(distance: d, sweep: sweep)
            let w = wobble.at(theta, base)
            revealDelays[id] = StarRevealFx.hitTime(distance: d / (1 + StarRevealFx.wobble * w), sweep: sweep)
        }
        revealSortedDelays = revealDelays.values.sorted()
        revealHidden = Set(revealDelays.keys)
        revealStart = CACurrentMediaTime()
    }

    /// 등장 시작 — 링을 만들고, 프레임마다 링·별 뷰·필터 빛 경계를 맞춘다.
    func startStarReveal(_ mapView: MLNMapView) {
        revealLink?.invalidate()
        revealStart = CACurrentMediaTime()
        MusicManager.shared.resetRevealChime()

        // 필터 빛: 링 안쪽 = 새 색, 바깥 = 지금까지의 색. 링이 다 퍼지면 새 색으로 확정(stopStarReveal).
        let wave = FilterWaveState.shared
        if !wave.active { wave.outsideHex = wave.committedHex }
        wave.insideHex = parent.filterTintHex
        wave.ring = nil
        wave.active = true

        buildRevealRing(mapView)
        applyRevealToVisibleViews(mapView)
        let link = CADisplayLink(target: self, selector: #selector(revealTick(_:)))
        link.add(to: .main, forMode: .common)
        revealLink = link
    }

    @objc func revealTick(_ link: CADisplayLink) {
        guard let mapView = mapRef else { stopStarReveal(); return }
        let elapsed = CACurrentMediaTime() - revealStart

        // 링이 닿은 별: 차임(화면 밖 별도) · 후광(CircleLayer) 켜기 · 화면 안이면 그 자리에 발광.
        let hit = revealHidden.filter { (revealDelays[$0] ?? 0) <= elapsed }
        if !hit.isEmpty {
            revealHidden.subtract(hit)
            for _ in hit { MusicManager.shared.playRevealChime() } // 간격은 MusicManager 가 솎는다
            refreshAuraFeatures(mapView)
            for case let a as DiaryAnnotation in mapView.annotations ?? [] {
                guard let id = a.diary.id, hit.contains(id) else { continue }
                spawnRevealFlash(for: a, mapView: mapView)
            }
        }
        applyRevealToVisibleViews(mapView)

        // 링은 모든 별에 닿고 + **지금 보고 있는 화면을 다 덮을 때까지** 선명하게 계속 퍼진다(연출 중 지도를 옮겨도 그 화면을
        // 다 덮을 때까지). 그 뒤 퍼지는 채로 옅어진다. 내 위치가 아주 멀면 ringMaxTime 에서 옅어지기 시작.
        let lastHit = revealSortedDelays.last ?? 0
        if revealFadeStart < 0, elapsed >= lastHit + StarRevealFx.pop, let center = revealCenter {
            let radius = StarRevealFx.radius(at: elapsed, sweep: revealSweepM)
            let covers = radius * (1 - StarRevealFx.wobble) >= revealSweepMeters(mapView, center: center)
            if covers || elapsed >= StarRevealFx.ringMaxTime { revealFadeStart = elapsed }
        }
        let alpha = revealFadeStart < 0 ? 1 : min(max(1 - (elapsed - revealFadeStart) / StarRevealFx.ringFade, 0), 1)
        updateRevealRing(mapView, elapsed: elapsed, alpha: Float(alpha))

        if revealFadeStart >= 0, elapsed >= revealFadeStart + StarRevealFx.ringFade { stopStarReveal() }
    }

    /// 등장 종료 — 모든 별을 제자리(불투명·원래 크기)로, 링은 걷어내고 필터 빛을 새 색으로 확정한다.
    func stopStarReveal() {
        revealLink?.invalidate()
        revealLink = nil
        revealDelays = [:]
        revealSortedDelays = []
        revealRingLayer?.removeFromSuperlayer()
        revealRingLayer = nil
        revealRingParts = []
        let wave = FilterWaveState.shared
        if wave.active {
            wave.committedHex = wave.insideHex
            wave.ring = nil
            wave.active = false
        }
        let hadHidden = !revealHidden.isEmpty
        revealHidden = []
        if let mapView = mapRef {
            applyRevealToVisibleViews(mapView)
            if hadHidden { refreshAuraFeatures(mapView) }
        }
    }

    private func applyRevealToVisibleViews(_ mapView: MLNMapView) {
        for case let a as DiaryAnnotation in mapView.annotations ?? [] {
            guard let v = mapView.view(for: a) else { continue }
            applyRevealState(to: v, id: a.diary.id ?? "")
        }
    }

    /// 뷰 1개에 현재 등장 진행도를 입힌다 — viewFor(새로 만들거나 재사용한 뷰)에서도 호출(깜빡임 방지).
    func applyRevealState(to view: MLNAnnotationView, id: String) {
        guard let delay = revealDelays[id] else {
            view.alpha = 1
            view.layer.sublayerTransform = CATransform3DIdentity
            return
        }
        let u = (CACurrentMediaTime() - revealStart - delay) / StarRevealFx.pop
        if u <= 0 {
            view.alpha = 0
            view.layer.sublayerTransform = CATransform3DMakeScale(0.2, 0.2, 1)
            return
        }
        view.alpha = CGFloat(min(u / 0.15, 1))
        let s = StarRevealFx.popScale(u)
        view.layer.sublayerTransform = CATransform3DMakeScale(s, s, 1)
    }

    // MARK: 빛의 고리

    /// 링 레이어(지도 뷰 레이어 위) — 구불구불한 선 하나. 모양은 매 프레임 updateRevealRing 이 바꾼다.
    private func buildRevealRing(_ mapView: MLNMapView) {
        revealRingLayer?.removeFromSuperlayer()
        let color = UIColor(rgbHex: parent.filterTintHex ?? MapFilterKind.neutralHex)
        let host = CALayer()
        host.frame = mapView.bounds

        let line = CAShapeLayer()
        line.fillColor = nil
        line.strokeColor = color.mixedWithWhite(0.35).withAlphaComponent(0.95).cgColor
        line.lineWidth = StarRevealFx.ringStroke
        line.lineJoin = .round
        line.frame = host.bounds
        host.addSublayer(line)

        mapView.layer.addSublayer(host)
        revealRingLayer = host
        revealRingParts = [line]
    }

    /// 링이 화면을 다 덮는 데 필요한 거리(m) = center 에서 화면 네 모서리(지도 좌표)까지 거리 중 최댓값(Android revealSweepMeters).
    private func revealSweepMeters(_ mapView: MLNMapView, center: CLLocationCoordinate2D) -> Double {
        let here = CLLocation(latitude: center.latitude, longitude: center.longitude)
        let b = mapView.bounds
        var sweep = 30.0
        for corner in [CGPoint(x: b.minX, y: b.minY), CGPoint(x: b.maxX, y: b.minY),
                       CGPoint(x: b.minX, y: b.maxY), CGPoint(x: b.maxX, y: b.maxY)] {
            let c = mapView.convert(corner, toCoordinateFrom: mapView)
            sweep = max(sweep, here.distance(from: CLLocation(latitude: c.latitude, longitude: c.longitude)))
        }
        return sweep
    }

    /// 지도 위 구불구불한 링(내 위치 중심, 지금 반지름, 각도마다 출렁임)을 지금 카메라로 화면에 투영해 링·필터 빛 경계를 맞춘다.
    private func updateRevealRing(_ mapView: MLNMapView, elapsed: Double, alpha: Float) {
        guard let center = revealCenter, let host = revealRingLayer else { return }
        let wobble = revealWobble ?? RingWobble()
        let r = min(max(StarRevealFx.radius(at: elapsed, sweep: revealSweepM), 0.5), 2_000_000)
        let mPerLng = 111_320 * max(cos(center.latitude * .pi / 180), 0.01)
        var points: [CGPoint] = []
        points.reserveCapacity(StarRevealFx.ringSegments)
        for i in 0..<StarRevealFx.ringSegments {
            let a = 2 * Double.pi * Double(i) / Double(StarRevealFx.ringSegments)
            let ra = r * (1 + StarRevealFx.wobble * wobble.at(a, elapsed))
            let coord = CLLocationCoordinate2D(latitude: min(max(center.latitude + ra / 111_320 * sin(a), -85), 85),
                                               longitude: center.longitude + ra / mPerLng * cos(a))
            points.append(mapView.convert(coord, toPointTo: mapView))
        }
        let path = CGMutablePath()
        path.addLines(between: points)
        path.closeSubpath()

        CATransaction.begin()
        CATransaction.setDisableActions(true) // 매 프레임 직접 그린다(암시적 애니메이션 끔)
        host.frame = mapView.bounds
        host.opacity = alpha
        for part in revealRingParts {
            part.frame = host.bounds
            part.path = path
        }
        CATransaction.commit()

        // 필터 빛 경계(SwiftUI FilterGlowOverlay) — 지도 뷰와 같은 좌표계(둘 다 전체 화면, 안전 영역 무시).
        FilterWaveState.shared.ring = points
    }

    /// 링이 닿은 별 자리의 발광 — 흰 심 + 별 색 번짐이 확 밝아졌다가 꺼진다(Android WaveFlash / revealFlash 패리티).
    /// 화면 밖 별은 건너뛴다(소리만 난다).
    private func spawnRevealFlash(for annotation: DiaryAnnotation, mapView: MLNMapView) {
        let p = mapView.convert(annotation.coordinate, toPointTo: mapView)
        // 화면에 보이는 별 반지름 = 마커 변 × 줌 배율 ÷ 2 × 본체 비율(0.78)
        let starRadius = annotation.markerSize * starZoomScale * 0.5 * 0.78
        let r = max(StarRevealFx.flashMin, starRadius * StarRevealFx.flashScale)
        guard mapView.bounds.insetBy(dx: -r, dy: -r).contains(p) else { return }
        let star = UIColor(StarStyle.color(annotation.diary.starColor))

        let glow = CAGradientLayer()
        glow.type = .radial
        glow.frame = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
        glow.startPoint = CGPoint(x: 0.5, y: 0.5)
        glow.endPoint = CGPoint(x: 1, y: 1)
        glow.colors = [
            UIColor.white.withAlphaComponent(0.95).cgColor,
            star.mixedWithWhite(0.35).withAlphaComponent(0.55).cgColor,
            star.withAlphaComponent(0).cgColor,
        ]
        glow.locations = [0, 0.32, 1]
        glow.opacity = 0

        // 세기: 0 → 1(u=0.18) → 0 (Android revealFlash), 크기: 0.7 → 1 → 0.7
        let opacity = CAKeyframeAnimation(keyPath: "opacity")
        opacity.values = [0.0, 1.0, 0.37, 0.06, 0.0]
        opacity.keyTimes = [0, 0.18, 0.5, 0.8, 1]
        let scale = CAKeyframeAnimation(keyPath: "transform.scale")
        scale.values = [0.7, 1.0, 0.7]
        scale.keyTimes = [0, 0.18, 1]
        let group = CAAnimationGroup()
        group.animations = [opacity, scale]
        group.duration = StarRevealFx.pop

        CATransaction.begin()
        CATransaction.setCompletionBlock { [weak glow] in glow?.removeFromSuperlayer() }
        glow.add(group, forKey: "flash")
        mapView.layer.addSublayer(glow)
        CATransaction.commit()
    }
}
