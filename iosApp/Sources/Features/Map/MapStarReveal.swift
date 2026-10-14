import MapLibre
import QuartzCore
import SwiftUI
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 필터 전환 "빛의 고리" 등장 — Android DiaryMap 의 revealKey 연출 패리티(2026-10-14, references/filtering.mp4).
//  · 화면(=카메라) 중심에서 링이 빠르게 퍼지고, 링이 닿는 순간 그 별이 차임과 함께 잠깐 커지고 밝아졌다가
//    제자리로 돌아오며 생긴다(중심에서 가까운 별부터). 예전(2026-09-25)엔 무작위 순서로 하나씩 "톡" 떴다.
//  · 링 색은 지금 필터의 색(`parent.filterTintHex`, 필터를 끄는 순간엔 색 없는 별빛).
//  · 화면 밖 별은 바로 제자리. 첫 표시·데이터 갱신에는 연출 없음(값이 바뀐 revealKey 만).
// 크기 팝은 어노테이션 뷰의 transform(줌 배율이 쓰는 자리)을 건드리지 않도록 layer.sublayerTransform 으로 준다.
// 수치는 Android DiaryMapMarkers REVEAL_* 와 동일(값 drift 금지).
// ─────────────────────────────────────────────────────────────────────────────

enum StarRevealFx {
    /// 링이 화면 중심 → 가장 먼 모서리까지 퍼지는 시간(초). 별의 등장 시각 = (중심까지 거리 / 모서리 거리) × 이 값.
    static let wave = 0.9
    /// 링이 닿은 별 하나가 생기는 시간(초) — 크기 0.2 → 1.55 → 1 + 발광.
    static let pop = 0.52
    /// 링 선 굵기 / 링 안쪽으로 끌리는 빛 띠 폭(pt).
    static let ringStroke: CGFloat = 2.2
    static let ringTrail: CGFloat = 54
    /// 별 발광 반경 = 화면에 보이는 별 반지름 × 이 배율(하한 flashMin).
    static let flashScale: CGFloat = 1.9
    static let flashMin: CGFloat = 12

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

    /// 등장 준비 — 화면 안 별마다 "링이 닿는 시각"을 정하고 숨김 목록을 만든다(뷰가 생기기 전에 호출해도 된다).
    func prepareStarReveal(_ annotations: [MLNAnnotation], mapView: MLNMapView) {
        let bounds = mapView.bounds
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let maxR = max(hypot(bounds.width / 2, bounds.height / 2), 1)
        revealDelays = [:]
        for case let a as DiaryAnnotation in annotations {
            let p = mapView.convert(a.coordinate, toPointTo: mapView)
            guard bounds.contains(p), let id = a.diary.id else { continue }
            let dist = hypot(p.x - center.x, p.y - center.y)
            revealDelays[id] = Double(dist / maxR) * StarRevealFx.wave
        }
        revealSortedDelays = revealDelays.values.sorted()
        revealHidden = Set(revealDelays.keys)
        revealStart = CACurrentMediaTime()
        revealSounded = 0
    }

    /// 등장 시작 — 링을 띄우고, 프레임마다 각 별 뷰의 투명도/크기를 맞춘다.
    func startStarReveal(_ mapView: MLNMapView) {
        revealLink?.invalidate()
        revealStart = CACurrentMediaTime()
        revealSounded = 0
        MusicManager.shared.resetRevealChime()
        applyRevealToVisibleViews(mapView)
        startRevealRing(mapView)
        let link = CADisplayLink(target: self, selector: #selector(revealTick(_:)))
        link.add(to: .main, forMode: .common)
        revealLink = link
    }

    @objc func revealTick(_ link: CADisplayLink) {
        guard let mapView = mapRef else { stopStarReveal(); return }
        let elapsed = CACurrentMediaTime() - revealStart
        // 링이 별에 닿는 순간마다 차임(간격은 MusicManager 가 솎는다).
        while revealSounded < revealSortedDelays.count, elapsed >= revealSortedDelays[revealSounded] {
            MusicManager.shared.playRevealChime()
            revealSounded += 1
        }
        // 링이 닿은 별: 후광(CircleLayer)을 켜고 그 자리에 발광을 띄운다.
        let hit = revealHidden.filter { (revealDelays[$0] ?? 0) <= elapsed }
        if !hit.isEmpty {
            revealHidden.subtract(hit)
            refreshAuraFeatures(mapView)
            for case let a as DiaryAnnotation in mapView.annotations ?? [] {
                guard let id = a.diary.id, hit.contains(id) else { continue }
                spawnRevealFlash(for: a, mapView: mapView)
            }
        }

        applyRevealToVisibleViews(mapView)
        let total = max(StarRevealFx.wave, (revealSortedDelays.last ?? 0) + StarRevealFx.pop)
        if elapsed >= total { stopStarReveal() }
    }

    /// 등장 종료 — 모든 별을 제자리(불투명·원래 크기)로, 링은 걷어낸다.
    func stopStarReveal() {
        revealLink?.invalidate()
        revealLink = nil
        revealDelays = [:]
        revealSortedDelays = []
        revealRingLayer?.removeFromSuperlayer()
        revealRingLayer = nil
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

    /// 화면 중심에서 가장 먼 모서리까지 일정한 속도로 퍼지는 링(번짐 + 안쪽 빛 띠 + 밝은 선). 퍼질수록 옅어진다.
    private func startRevealRing(_ mapView: MLNMapView) {
        revealRingLayer?.removeFromSuperlayer()
        let bounds = mapView.bounds
        guard bounds.width > 0, bounds.height > 0 else { return }
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let maxR = max(hypot(bounds.width / 2, bounds.height / 2), 1)
        let color = UIColor(rgbHex: parent.filterTintHex ?? MapFilterKind.neutralHex)

        func circle(_ radius: CGFloat) -> CGPath {
            let r = max(radius, 0.5)
            return UIBezierPath(ovalIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)).cgPath
        }

        let host = CALayer()
        host.frame = bounds
        let stroke = StarRevealFx.ringStroke
        let trail = StarRevealFx.ringTrail
        // (선 굵기, 색, 링 반지름에서 안쪽으로 당기는 양) — 빛 띠 / 번짐 / 밝은 선
        let bands: [(width: CGFloat, color: UIColor, inset: CGFloat)] = [
            (trail, color.withAlphaComponent(0.10), trail / 2),
            (stroke * 4.5, color.withAlphaComponent(0.26), 0),
            (stroke, color.mixedWithWhite(0.35).withAlphaComponent(0.95), 0),
        ]
        for band in bands {
            let layer = CAShapeLayer()
            layer.fillColor = nil
            layer.strokeColor = band.color.cgColor
            layer.lineWidth = band.width
            layer.path = circle(maxR - band.inset)
            let grow = CABasicAnimation(keyPath: "path")
            grow.fromValue = circle(0.5)
            grow.toValue = circle(maxR - band.inset)
            grow.duration = StarRevealFx.wave
            grow.timingFunction = CAMediaTimingFunction(name: .linear)
            layer.add(grow, forKey: "grow")
            host.addSublayer(layer)
        }
        // 불투명도 1 − p³ (Android revealRingAlpha 와 같은 곡선)
        let fade = CAKeyframeAnimation(keyPath: "opacity")
        fade.values = [1.0, 0.875, 0.578, 0.271, 0.0]
        fade.keyTimes = [0, 0.5, 0.75, 0.9, 1]
        fade.duration = StarRevealFx.wave
        host.opacity = 0
        host.add(fade, forKey: "fade")
        mapView.layer.addSublayer(host)
        revealRingLayer = host
    }

    /// 링이 닿은 별 자리의 발광 — 흰 심 + 별 색 번짐이 확 밝아졌다가 꺼진다(Android WaveFlash / revealFlash 패리티).
    private func spawnRevealFlash(for annotation: DiaryAnnotation, mapView: MLNMapView) {
        let p = mapView.convert(annotation.coordinate, toPointTo: mapView)
        // 화면에 보이는 별 반지름 = 마커 변 × 줌 배율 ÷ 2 × 본체 비율(0.78)
        let starRadius = annotation.markerSize * starZoomScale * 0.5 * 0.78
        let r = max(StarRevealFx.flashMin, starRadius * StarRevealFx.flashScale)
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
