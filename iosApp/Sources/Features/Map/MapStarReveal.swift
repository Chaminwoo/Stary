import CoreLocation
import MapLibre
import QuartzCore
import SwiftUI
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 필터 전환 "빛의 고리"(포털) 등장 — Android DiaryMap 의 revealKey 연출 패리티
// (2026-10-14, 레퍼런스: references/filtering.mp4 + 리그 오브 레전드 "창공 파이크" 펜타킬).
//  · 내 위치에 검은 구체가 생기고, 거기서 **울퉁불퉁 출렁이는 거품(포털)**이 지도 위로 퍼진다. 거품 안쪽은 필터 색의 성운
//    (구름 얼룩·어두운 반점·잔별)이 되어 화면 전체를 덮었다가 은은한 필터 빛으로 가라앉는다. 테두리는 안쪽으로 번지는 빛 + 얇은 선.
//  · 매 프레임 지도 위 거품(대권 기준, 크기 제한 없음)을 화면에 투영 → 연출 중 지도를 움직여도 지도에 붙어 함께 움직인다.
//  · 링이 닿는 별은 차임과 함께 잠깐 커지고 밝아졌다 제자리로(화면 밖 별도 소리). **링은 별과 관계없이 지금 보고 있는 화면 전체를
//    지나갈 때까지 멈추지 않는다.**
//  · 필터 빛·포털 성운·검은 구체는 `FilterWaveState` 로 SwiftUI 오버레이(FilterGlowOverlay)가 그린다. 테두리 선만 지도 레이어.
// 크기 팝은 어노테이션 뷰의 transform(줌 배율이 쓰는 자리)을 건드리지 않도록 layer.sublayerTransform 으로 준다.
// 수치는 Android DiaryMapMarkers REVEAL_* 와 동일(값 drift 금지).
// ─────────────────────────────────────────────────────────────────────────────

enum StarRevealFx {
    /// 링이 내 위치 → 화면의 가장 먼 모서리까지 **일정한 속도로** 퍼지는 시간(초).
    static let wave = 0.9
    /// 화면을 다 쓸고 난 뒤 링이 빨라지는 정도(초, 지수 시간 상수).
    static let farTau = 0.45
    /// 링이 별에 닿는 시각의 상한(초) — 지구 반대편 별도 이 안에 닿는다(안전 상한).
    static let maxTime = 8.0
    /// 링이 지금 화면을 다 지나간 뒤 옅어지는 시간(초) — 이때 링은 이미 화면 밖이다.
    static let ringFade = 0.3
    /// 화면을 다 지나가지 못해도 이 시각엔 끝낸다(초) — 정상적으로는 닿지 않는 안전 상한.
    static let ringMaxTime = 12.0
    /// 링이 화면을 다 지나간 뒤 포털 성운이 은은한 필터 빛으로 가라앉는 시간(초).
    static let portalDecay = 1.6
    /// 지도 위 링을 화면에 투영할 때 쓰는 꼭짓점 수.
    static let ringSegments = 180
    /// 거품의 출렁임 — 반지름 대비 비율(반지름 = R × (1 + wobble × RingWobble.at)).
    static let wobble = 0.09
    /// 링 선 굵기(pt) / 테두리 안쪽으로 번지는 빛 폭(pt).
    static let ringStroke: CGFloat = 1.4
    static let rimGlow: CGFloat = 22
    /// 시작점의 검은 구체 — 반지름(pt) / 생겼다 사라지는 시간(초).
    static let orbRadius: CGFloat = 15
    static let orbTime = 0.7
    /// 포털 성운 속 잔별 개수.
    static let portalSparks = 56
    /// 지구 반지름(m) — 지도 위 원을 대권 기준으로 그린다.
    static let earthRadius = 6_371_000.0
    /// 링이 닿은 별 하나가 생기는 시간(초) — 크기 0.2 → 1.55 → 1 + 발광.
    static let pop = 0.52
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

    /// (lat, lng) 에서 방위 bearing(rad, 북쪽 0·시계방향)으로 dist(m) 간 지점(대권). 경도는 접지 않는다(Android geoDestination).
    static func destination(lat: Double, lng: Double, bearing: Double, distance: Double) -> (lat: Double, lng: Double) {
        let d = min(max(distance / earthRadius, 0), Double.pi)
        let p1 = lat * .pi / 180
        let l1 = lng * .pi / 180
        let sinP2 = sin(p1) * cos(d) + cos(p1) * sin(d) * cos(bearing)
        let p2 = asin(min(max(sinP2, -1), 1))
        let l2 = l1 + atan2(sin(bearing) * sin(d) * cos(p1), cos(d) - sin(p1) * sinP2)
        return (p2 * 180 / .pi, l2 * 180 / .pi)
    }

    /// (lat1, lng1) 에서 (lat2, lng2) 를 보는 각도 — RingWobble 과 같은 기준(동쪽 0·반시계, rad). Android geoTheta.
    static func theta(lat1: Double, lng1: Double, lat2: Double, lng2: Double) -> Double {
        let p1 = lat1 * .pi / 180
        let p2 = lat2 * .pi / 180
        let dl = (lng2 - lng1) * .pi / 180
        let bearing = atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl))
        return .pi / 2 - bearing
    }
}

/// 거품 출렁임 모양 — 각도 θ(라디안, 동쪽 0·반시계)와 시각(초)에 따른 −1..1 값.
/// 주파수 2·3·5(덩어리) + 11(잔물결)의 사인을 섞고 시간에 따라 서로 다른 방향으로 흘린다. 위상은 연출마다 무작위.
/// Android `RingWobble`(DiaryMapMarkers.kt)과 **같은 식**.
struct RingWobble {
    let p1 = Double.random(in: 0..<(2 * Double.pi))
    let p2 = Double.random(in: 0..<(2 * Double.pi))
    let p3 = Double.random(in: 0..<(2 * Double.pi))
    let p4 = Double.random(in: 0..<(2 * Double.pi))

    func at(_ theta: Double, _ t: Double) -> Double {
        0.45 * sin(2 * theta + p1 + 0.7 * t)
            + 0.30 * sin(3 * theta + p2 - 1.1 * t)
            + 0.17 * sin(5 * theta + p3 + 1.6 * t)
            + 0.08 * sin(11 * theta + p4 - 2.6 * t)
    }
}

/// 포털 성운의 고정 배치(연출마다 무작위) — 지도 위 위치를 "내 위치에서 방위·거리(× sweep)"로 들고 매 프레임 투영한다.
/// Android `PortalLayout`(FilterGlow.kt)과 같은 개수·범위.
struct PortalLayout {
    /// 구름 얼룩 — kind: 0 = 필터색 짙게, 1 = 필터색 옅게, 2 = 어두운 반점, 3 = 청록 빛줄기.
    struct Blob { let bearing: Double; let dist: Double; let radius: Double; let kind: Int }
    struct Spark { let bearing: Double; let dist: Double; let size: CGFloat; let phase: Double; let speed: Double }

    let blobs: [Blob]
    let sparks: [Spark]

    init() {
        blobs = [0, 0, 0, 1, 1, 2, 2, 2, 3, 3].map { k in
            Blob(bearing: Double.random(in: 0..<(2 * Double.pi)),
                 dist: Double.random(in: 0..<1).squareRoot() * 1.25,
                 radius: k == 3 ? 0.18 + Double.random(in: 0..<0.12) : 0.32 + Double.random(in: 0..<0.38),
                 kind: k)
        }
        sparks = (0..<StarRevealFx.portalSparks).map { _ in
            Spark(bearing: Double.random(in: 0..<(2 * Double.pi)),
                  dist: Double.random(in: 0..<1).squareRoot() * 1.35,
                  size: CGFloat(0.8 + Double.random(in: 0..<1.4)),
                  phase: Double.random(in: 0..<(2 * Double.pi)),
                  speed: 2 + Double.random(in: 0..<3))
        }
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
        revealPortal = PortalLayout()
        revealPassedAt = -1

        // 별마다 링이 닿는 시각 — 출렁이는 테두리가 그 별 방향에서 실제로 닿는 순간에 맞춘다(Android hitTimeOf 와 같은 보정).
        revealDelays = [:]
        for case let a as DiaryAnnotation in annotations {
            guard let id = a.diary.id else { continue }
            let d = here.distance(from: CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude))
            let theta = StarRevealFx.theta(lat1: center.latitude, lng1: center.longitude,
                                           lat2: a.coordinate.latitude, lng2: a.coordinate.longitude)
            let base = StarRevealFx.hitTime(distance: d, sweep: sweep)
            let w = wobble.at(theta, base)
            revealDelays[id] = StarRevealFx.hitTime(distance: d / (1 + StarRevealFx.wobble * w), sweep: sweep)
        }
        revealSortedDelays = revealDelays.values.sorted()
        revealHidden = Set(revealDelays.keys)
        revealStart = CACurrentMediaTime()
    }

    /// 등장 시작 — 링을 만들고, 프레임마다 링·별 뷰·필터 빛/포털을 맞춘다.
    func startStarReveal(_ mapView: MLNMapView) {
        revealLink?.invalidate()
        revealStart = CACurrentMediaTime()
        MusicManager.shared.resetRevealChime()

        // 필터 빛: 링 안쪽 = 새 색(+ 포털 성운), 바깥 = 지금까지의 색. 다 끝나면 새 색으로 확정(stopStarReveal).
        let wave = FilterWaveState.shared
        if !wave.active { wave.outsideHex = wave.committedHex }
        wave.insideHex = parent.filterTintHex
        wave.ringHex = parent.filterTintHex ?? MapFilterKind.neutralHex
        wave.portal = 0
        wave.blobs = []
        wave.sparks = []
        wave.orb = nil
        wave.ring = nil
        wave.active = true

        buildRevealRing(mapView)
        applyRevealToVisibleViews(mapView)
        let link = CADisplayLink(target: self, selector: #selector(revealTick(_:)))
        link.add(to: .main, forMode: .common)
        revealLink = link
    }

    @objc func revealTick(_ link: CADisplayLink) {
        guard let mapView = mapRef, let center = revealCenter else { stopStarReveal(); return }
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

        // 링은 **별과 관계없이** 지금 보고 있는 화면을 다 지나갈 때까지 계속 퍼진다(연출 중 지도를 옮기거나 넓게 보면
        // 그 화면을 다 지나갈 때까지). 크기 제한도 없다(대권 기준).
        let radius = StarRevealFx.radius(at: elapsed, sweep: revealSweepM)
        if revealPassedAt < 0 {
            let passed = radius * (1 - StarRevealFx.wobble) >= revealSweepMeters(mapView, center: center)
            if passed || elapsed >= StarRevealFx.ringMaxTime { revealPassedAt = elapsed }
        }
        let ringAlpha = revealPassedAt < 0 ? 1 : min(max(1 - (elapsed - revealPassedAt) / StarRevealFx.ringFade, 0), 1)
        updateRevealRing(mapView, elapsed: elapsed, radius: radius, alpha: Float(ringAlpha))
        updatePortal(mapView, elapsed: elapsed, center: center)

        let lastHit = revealSortedDelays.last ?? 0
        if revealPassedAt >= 0,
           elapsed >= revealPassedAt + max(StarRevealFx.ringFade, StarRevealFx.portalDecay),
           elapsed >= lastHit + StarRevealFx.pop {
            stopStarReveal()
        }
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
            wave.portal = 0
            wave.blobs = []
            wave.sparks = []
            wave.orb = nil
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

    // MARK: 빛의 고리(포털)

    /// 테두리 레이어(지도 뷰 레이어 위) — 안쪽으로만 번지는 빛 2겹(거품 모양 마스크) + 얇은 경계선. 모양은 매 프레임 updateRevealRing.
    private func buildRevealRing(_ mapView: MLNMapView) {
        revealRingLayer?.removeFromSuperlayer()
        let color = UIColor(rgbHex: parent.filterTintHex ?? MapFilterKind.neutralHex)
        let rim = color.mixedWithWhite(0.5)
        let host = CALayer()
        host.frame = mapView.bounds

        let glowWide = CAShapeLayer()
        glowWide.fillColor = nil
        glowWide.strokeColor = rim.withAlphaComponent(0.10).cgColor
        glowWide.lineWidth = StarRevealFx.rimGlow * 2
        glowWide.lineJoin = .round
        let glowNarrow = CAShapeLayer()
        glowNarrow.fillColor = nil
        glowNarrow.strokeColor = rim.withAlphaComponent(0.22).cgColor
        glowNarrow.lineWidth = StarRevealFx.rimGlow * 0.7
        glowNarrow.lineJoin = .round
        let glowHost = CALayer()          // 두 번짐을 거품 안쪽만 남긴다(바깥은 깨끗하게)
        let glowMask = CAShapeLayer()
        glowMask.fillColor = UIColor.black.cgColor
        glowHost.mask = glowMask

        let line = CAShapeLayer()
        line.fillColor = nil
        line.strokeColor = color.mixedWithWhite(0.6).withAlphaComponent(0.92).cgColor
        line.lineWidth = StarRevealFx.ringStroke
        line.lineJoin = .round

        glowHost.addSublayer(glowWide)
        glowHost.addSublayer(glowNarrow)
        host.addSublayer(glowHost)
        host.addSublayer(line)
        for l in [glowHost, glowMask, glowWide, glowNarrow, line] as [CALayer] { l.frame = host.bounds }
        mapView.layer.addSublayer(host)
        revealRingLayer = host
        revealRingGlowHost = glowHost
        revealRingParts = [glowMask, glowWide, glowNarrow, line]
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

    /// 지도 위 지점(center 에서 방위·거리)을 화면 좌표로.
    private func screenPoint(_ mapView: MLNMapView, center: CLLocationCoordinate2D, bearing: Double, distance: Double) -> CGPoint {
        let ll = StarRevealFx.destination(lat: center.latitude, lng: center.longitude, bearing: bearing, distance: distance)
        return mapView.convert(CLLocationCoordinate2D(latitude: min(max(ll.lat, -85), 85), longitude: ll.lng), toPointTo: mapView)
    }

    /// 지도 위 출렁이는 거품 테두리(내 위치 중심, 대권, 크기 제한 없음)를 지금 카메라로 화면에 투영해 테두리·필터 빛 경계를 맞춘다.
    private func updateRevealRing(_ mapView: MLNMapView, elapsed: Double, radius: Double, alpha: Float) {
        guard let center = revealCenter, let host = revealRingLayer else { return }
        let wobble = revealWobble ?? RingWobble()
        let r = max(radius, 0.5)
        var points: [CGPoint] = []
        points.reserveCapacity(StarRevealFx.ringSegments)
        var prevLng = Double.nan
        for i in 0..<StarRevealFx.ringSegments {
            let a = 2 * Double.pi * Double(i) / Double(StarRevealFx.ringSegments)
            let ra = r * (1 + StarRevealFx.wobble * wobble.at(a, elapsed))
            let ll = StarRevealFx.destination(lat: center.latitude, lng: center.longitude, bearing: .pi / 2 - a, distance: ra)
            // 경도가 ±180 을 넘나들 때 선이 화면을 가로지르지 않게 이어 붙인다
            var lng = ll.lng
            if !prevLng.isNaN {
                while lng - prevLng > 180 { lng -= 360 }
                while lng - prevLng < -180 { lng += 360 }
            }
            prevLng = lng
            let coord = CLLocationCoordinate2D(latitude: min(max(ll.lat, -85), 85), longitude: lng)
            points.append(mapView.convert(coord, toPointTo: mapView))
        }
        let path = CGMutablePath()
        path.addLines(between: points)
        path.closeSubpath()

        CATransaction.begin()
        CATransaction.setDisableActions(true) // 매 프레임 직접 그린다(암시적 애니메이션 끔)
        host.frame = mapView.bounds
        host.opacity = alpha
        revealRingGlowHost?.frame = host.bounds
        for part in revealRingParts {
            part.frame = host.bounds
            part.path = path
        }
        CATransaction.commit()

        // 필터 빛·포털 경계(SwiftUI FilterGlowOverlay) — 지도 뷰와 같은 좌표계(둘 다 전체 화면, 안전 영역 무시).
        FilterWaveState.shared.ring = points
    }

    /// 포털 성운(구름·잔별)·검은 구체를 지금 카메라 화면 좌표로 계산해 FilterWaveState 에 넘긴다(그리기는 SwiftUI).
    private func updatePortal(_ mapView: MLNMapView, elapsed: Double, center: CLLocationCoordinate2D) {
        let wave = FilterWaveState.shared
        let portal: Double
        if wave.insideHex == nil {
            portal = 0
        } else if revealPassedAt < 0 {
            portal = 1
        } else {
            let x = min(max((elapsed - revealPassedAt) / StarRevealFx.portalDecay, 0), 1)
            portal = 1 - (x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2)
        }
        wave.portal = portal

        var blobs: [FilterWaveState.Blob] = []
        var sparks: [FilterWaveState.Spark] = []
        if portal > 0.01, let layout = revealPortal {
            let mpp = max(mapView.metersPerPoint(atLatitude: center.latitude), 1e-6)
            let b = mapView.bounds
            for blob in layout.blobs {
                let p = screenPoint(mapView, center: center, bearing: blob.bearing, distance: blob.dist * revealSweepM)
                blobs.append(.init(point: p, radius: CGFloat(blob.radius * revealSweepM / mpp), kind: blob.kind))
            }
            for s in layout.sparks {
                let p = screenPoint(mapView, center: center, bearing: s.bearing, distance: s.dist * revealSweepM)
                guard b.insetBy(dx: -4, dy: -4).contains(p) else { continue }
                let tw = 0.35 + 0.65 * abs(sin(elapsed * s.speed + s.phase))
                sparks.append(.init(point: p, radius: s.size, alpha: 0.8 * tw))
            }
        }
        wave.blobs = blobs
        wave.sparks = sparks

        // 시작점의 검은 구체 — 툭 생겼다가(0.15초) 거품이 터져 나오며 커지면서 사라진다.
        if elapsed < StarRevealFx.orbTime {
            let c = mapView.convert(center, toPointTo: mapView)
            let grow = min(elapsed / 0.15, 1)
            let fade = min(max((elapsed - 0.15) / (StarRevealFx.orbTime - 0.15), 0), 1)
            let r = StarRevealFx.orbRadius * CGFloat((0.15 + 0.85 * (1 - (1 - grow) * (1 - grow))) * (1 + 0.3 * fade)) // 0 에서 시작하지 않는다(Android 와 같음)
            wave.orb = .init(point: c, radius: r, alpha: 1 - fade)
        } else {
            wave.orb = nil
        }
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
