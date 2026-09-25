import MapLibre
import QuartzCore
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 필터 전환 "별이 하나 둘" 순차 등장 — Android DiaryMap 의 revealKey 연출 패리티(2026-09-25, 광고 레퍼런스).
//  · 화면 안 별들을 무작위 순서(필터 조합마다 다른 순서)로 하나씩 "톡" 띄운다 — 투명도 + 크기 팝(0.35→1.18→1).
//  · 별이 뜨는 순간마다 "톡톡 반짝"(MusicManager.playSparkTick — 간격은 MusicManager 가 솎는다).
//  · 화면 밖 별은 바로 제자리. 첫 표시·데이터 갱신에는 연출 없음(값이 바뀐 revealKey 만).
// 크기 팝은 어노테이션 뷰의 transform(줌 배율이 쓰는 자리)을 건드리지 않도록 layer.sublayerTransform 으로 준다.
// 수치는 Android DiaryMapMarkers REVEAL_* 와 동일(값 drift 금지).
// ─────────────────────────────────────────────────────────────────────────────

enum StarRevealFx {
    /// 화면 안 별들이 모두 떠오르기까지(첫 별 시작 → 마지막 별 시작) 목표 시간(초).
    static let span = 1.3
    /// 별 사이 간격 하한/상한(초).
    static let minGap = 0.028
    static let maxGap = 0.12
    /// 별 하나가 톡 떠오르는 시간(초).
    static let pop = 0.38

    /// 순차 등장 팝 곡선 — u(0..1) → 크기 배율(Android revealPopScale 동일).
    static func popScale(_ u: Double) -> CGFloat {
        let x = min(max(u, 0), 1)
        if x < 0.55 {
            let k = 1 - pow(1 - x / 0.55, 3)
            return CGFloat(0.35 + (1.18 - 0.35) * k)
        }
        let v = (x - 0.55) / 0.45
        let e = v < 0.5 ? 2 * v * v : 1 - pow(-2 * v + 2, 2) / 2 // ease-in-out
        return CGFloat(1.18 + (1 - 1.18) * e)
    }
}

extension MapLibreView.Coordinator {

    /// 이번 updateUIView 에서 순차 등장을 해야 하는지 — revealKey 가 바뀌었을 때 1회만 true.
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

    /// 순차 등장 준비 — 화면 안 별들의 등장 지연을 정하고 숨김 목록을 만든다(뷰가 생기기 전에 호출해도 된다).
    func prepareStarReveal(_ annotations: [MLNAnnotation], mapView: MLNMapView, key: Int) {
        let bounds = mapView.bounds
        var onScreen: [String] = []
        for case let a as DiaryAnnotation in annotations {
            let p = mapView.convert(a.coordinate, toPointTo: mapView)
            if bounds.contains(p), let id = a.diary.id { onScreen.append(id) }
        }
        // 필터 조합마다 다른(하지만 같은 조합이면 같은) 순서 — 간단한 LCG 셔플.
        var seed = UInt64(bitPattern: Int64(key)) | 1
        func next() -> UInt64 {
            seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
            return seed >> 33
        }
        if onScreen.count > 1 {
            for i in stride(from: onScreen.count - 1, to: 0, by: -1) {
                let j = Int(next() % UInt64(i + 1))
                onScreen.swapAt(i, j)
            }
        }
        let n = onScreen.count
        let gap = n <= 1 ? 0 : min(max(StarRevealFx.span / Double(n - 1), StarRevealFx.minGap), StarRevealFx.maxGap)
        revealDelays = [:]
        for (i, id) in onScreen.enumerated() { revealDelays[id] = Double(i) * gap }
        revealSortedDelays = (0..<n).map { Double($0) * gap }
        revealHidden = Set(onScreen)
        revealStart = CACurrentMediaTime()
        revealSounded = 0
    }

    /// 순차 등장 시작 — 프레임마다 각 별 뷰의 투명도/크기를 맞춘다.
    func startStarReveal(_ mapView: MLNMapView) {
        revealLink?.invalidate()
        revealStart = CACurrentMediaTime()
        revealSounded = 0
        applyRevealToVisibleViews(mapView)
        let link = CADisplayLink(target: self, selector: #selector(revealTick(_:)))
        link.add(to: .main, forMode: .common)
        revealLink = link
    }

    @objc func revealTick(_ link: CADisplayLink) {
        guard let mapView = mapRef else { stopStarReveal(); return }
        let elapsed = CACurrentMediaTime() - revealStart
        while revealSounded < revealSortedDelays.count, elapsed >= revealSortedDelays[revealSounded] {
            MusicManager.shared.playSparkTick()
            revealSounded += 1
        }
        // 떠오르기 시작한 별은 후광(CircleLayer)도 켠다.
        let before = revealHidden.count
        revealHidden = revealHidden.filter { (revealDelays[$0] ?? 0) > elapsed }
        if revealHidden.count != before { refreshAuraFeatures(mapView) }

        applyRevealToVisibleViews(mapView)
        let total = (revealSortedDelays.last ?? 0) + StarRevealFx.pop
        if elapsed >= total { stopStarReveal() }
    }

    /// 순차 등장 종료 — 모든 별을 제자리(불투명·원래 크기)로.
    func stopStarReveal() {
        revealLink?.invalidate()
        revealLink = nil
        revealDelays = [:]
        revealSortedDelays = []
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
            view.layer.sublayerTransform = CATransform3DMakeScale(0.35, 0.35, 1)
            return
        }
        let a = min(u / 0.6, 1)
        view.alpha = CGFloat(1 - pow(1 - a, 3))
        let s = StarRevealFx.popScale(u)
        view.layer.sublayerTransform = CATransform3DMakeScale(s, s, 1)
    }
}
