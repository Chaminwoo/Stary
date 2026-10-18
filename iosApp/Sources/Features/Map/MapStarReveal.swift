import CoreLocation
import MapLibre
import QuartzCore
import SwiftUI
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 필터 전환 "빛의 고리"(포털) 등장 — Android DiaryMap 의 revealKey 연출 패리티
// (2026-10-14, 레퍼런스: references/filtering.mp4 + 리그 오브 레전드 "창공 파이크" 펜타킬).
//  · 내 위치에 검은 구체가 생기고, 거기서 **울퉁불퉁 출렁이는 거품(포털)**이 지도 위로 퍼진다. 거품 안쪽은 필터 색의 성운
//    (짙은 바탕·잔별)이 되어 화면 전체를 덮었다가 은은한 필터 빛으로 가라앉는다. 테두리는 안쪽으로 번지는 빛 + 얇은 선.
//  · 필터를 연속으로 바꾸면 거품이 **여러 개 함께** 퍼진다(2026-10-15). 각 자리 = 그 자리를 덮은 가장 새 거품의 색
//    (두 거품 사이 = 앞서가는 이전 필터 색, 가장 안쪽 = 새 필터 색). 새 거품이 따라잡으면 이전 거품은 삼켜져 사라진다.
//  · 매 프레임 지도 위 거품(대권 기준, 크기 제한 없음)을 화면에 투영 → 연출 중 지도를 움직여도 지도에 붙어 함께 움직인다.
//  · 링이 닿는 별은 차임과 함께 잠깐 커지고 밝아졌다 제자리로(화면 밖 별도 소리). **링은 별과 관계없이 지금 보고 있는 화면 전체를
//    지나갈 때까지 멈추지 않는다.**
//  · 필터 빛·포털 성운·테두리·검은 구체는 `FilterWaveState` 로 SwiftUI 오버레이(FilterGlowOverlay)가 그린다
//    (테두리도 거기서 그려야 새 거품에 덮인 이전 테두리를 가릴 수 있다 — 예전엔 지도 레이어였다).
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

}

// MARK: - 평면 지도(웹 메르카토르) — 필터 링의 좌표계(2026-10-19, Android FilterGlow.kt 머리말과 같은 식)
// 예전엔 대권(지구 곡면) 원이라, 지도를 멀리(유럽·미주) 옮겨 필터를 켜면 수천 km 원이 극 쪽으로 휘어 위에서 내려오고,
// 극을 넘는 점이 ±85° 로 잘려 가로 직선이, 경도 접힘으로 대각선이 생겼다. 이제 링은 지도에 보이는 그대로의 원이라
// 내 위치가 있는 쪽에서 동그랗게 밀려온다. 단위 = 적도 둘레(m)로 늘린 메르카토르 좌표(시간 계산은 비율만 쓴다).
// 화면 사상은 평면 사영변환(호모그래피) — 기울인 지도도 4점으로 정확히 맞고 극 밖 좌표도 잘리지 않는다.

/// 평면 지도 위 한 점(x 동쪽+, y 남쪽+ — 화면과 같은 방향).
struct FlatPt {
    static let world = 40_075_016.686
    let x: Double, y: Double

    func dist(_ o: FlatPt) -> Double { hypot(o.x - x, o.y - y) }
    /// 이 점에서 o 를 보는 각도 — RingWobble 과 같은 기준(동쪽 0·반시계, rad).
    func theta(_ o: FlatPt) -> Double { atan2(-(o.y - y), o.x - x) }
    /// 경도 한 바퀴씩 옮겨 refX 에 가장 가까운 사본으로.
    func nearX(_ refX: Double) -> FlatPt { FlatPt(x: x + ((refX - x) / Self.world).rounded() * Self.world, y: y) }

    /// 위경도 → 평면 지도. 경도는 refLng 에서 ±180° 안의 사본(지금 화면 쪽 지구)으로 펼친다.
    static func of(_ c: CLLocationCoordinate2D, refLng: Double) -> FlatPt {
        var l = c.longitude
        while l - refLng > 180 { l -= 360 }
        while l - refLng < -180 { l += 360 }
        let p = min(max(c.latitude, -85.05112878), 85.05112878) * .pi / 180
        return FlatPt(x: (l + 180) / 360 * world,
                      y: (1 - log(tan(.pi / 4 + p / 2)) / .pi) / 2 * world)
    }
}

/// 평면 지도 → 지금 카메라 화면 좌표. 화면 안쪽 네 점을 지도 좌표로 바꿔 사영변환을 푼다(매 프레임 새로).
/// refX = 지금 화면 쪽 지구의 x(다른 사본의 점은 nearX 로 맞춰 쓴다).
struct FlatScreen {
    private let h: [Double]
    private let ox: Double, oy: Double, s: Double
    let refX: Double

    init?(_ mapView: MLNMapView) {
        let b = mapView.bounds
        guard b.width >= 1, b.height >= 1 else { return nil }
        let refLng = mapView.centerCoordinate.longitude
        let scr = [CGPoint(x: b.width * 0.2, y: b.height * 0.3), CGPoint(x: b.width * 0.8, y: b.height * 0.3),
                   CGPoint(x: b.width * 0.8, y: b.height * 0.8), CGPoint(x: b.width * 0.2, y: b.height * 0.8)]
        let world = scr.map { FlatPt.of(mapView.convert($0, toCoordinateFrom: mapView), refLng: refLng) }
        let ox = world.reduce(0) { $0 + $1.x } / 4
        let oy = world.reduce(0) { $0 + $1.y } / 4
        let s = world.map { max(abs($0.x - ox), abs($0.y - oy)) }.max() ?? 0
        guard s > 0, !s.isNaN else { return nil }
        // u = (h0 x + h1 y + h2) / (h6 x + h7 y + 1), v = (h3 x + h4 y + h5) / (…) — 8 미지수 연립방정식
        var a: [[Double]] = []
        for i in 0..<4 {
            let x = (world[i].x - ox) / s, y = (world[i].y - oy) / s
            let u = Double(scr[i].x), v = Double(scr[i].y)
            a.append([x, y, 1, 0, 0, 0, -x * u, -y * u, u])
            a.append([0, 0, 0, x, y, 1, -x * v, -y * v, v])
        }
        guard let h = Self.solve(a) else { return nil }
        self.h = h; self.ox = ox; self.oy = oy; self.s = s
        refX = FlatPt.of(CLLocationCoordinate2D(latitude: 0, longitude: refLng), refLng: refLng).x
    }

    func toScreen(_ p: FlatPt) -> CGPoint {
        let x = (p.x - ox) / s, y = (p.y - oy) / s
        // 지평선 너머(w ≤ 0)는 아주 먼 곳으로 — 부호가 뒤집혀 화면 안쪽으로 튀지 않게
        let w = max(h[6] * x + h[7] * y + 1, 1e-4)
        return CGPoint(x: (h[0] * x + h[1] * y + h[2]) / w, y: (h[3] * x + h[4] * y + h[5]) / w)
    }

    /// 가우스 소거(부분 피벗) — 8×8. 특이하면 nil.
    private static func solve(_ m: [[Double]]) -> [Double]? {
        var a = m
        let n = 8
        for c in 0..<n {
            guard let p = (c..<n).max(by: { abs(a[$0][c]) < abs(a[$1][c]) }), abs(a[p][c]) >= 1e-12 else { return nil }
            a.swapAt(c, p)
            for r in 0..<n where r != c {
                let f = a[r][c] / a[c][c]
                if f == 0 { continue }
                for k in c...n { a[r][k] -= f * a[c][k] }
            }
        }
        return (0..<n).map { a[$0][n] / a[$0][$0] }
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

/// 퍼지고 있는 거품 하나 — 중심(내 위치, 평면 지도)·화면을 쓰는 거리·필터 색·출렁임·출발 시각.
/// 필터를 연속으로 바꾸면 여러 개가 함께 퍼진다(2026-10-15, Android `RevealRun`).
final class RevealRun {
    /// 링 중심 = 내 위치의 **계획 때 화면 쪽 사본**(평면 지도).
    let center: FlatPt
    /// 계획 때 카메라 경도 — 별 좌표를 같은(화면 쪽) 지구 사본으로 펼치는 기준.
    let refLng: Double
    let sweep: Double
    /// 거품 안 필터 빛 색(nil = 빛 없음, 필터를 모두 끈 경우).
    let tintHex: UInt32?
    let wobble = RingWobble()
    var start: CFTimeInterval = 0
    /// 링이 지금 화면을 다 지나간 시각(초, 출발 기준). −1 = 아직.
    var passedAt: Double = -1

    init(center: FlatPt, refLng: Double, sweep: Double, tintHex: UInt32?) {
        self.center = center
        self.refLng = refLng
        self.sweep = sweep
        self.tintHex = tintHex
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

    /// 등장 준비 — 새 거품의 중심(내 위치)·화면을 쓰는 거리를 정하고, **화면 안팎의 모든 별**마다 링이 닿는 시각을 정한다.
    /// (뷰가 생기기 전에 호출해도 된다.) 별은 마지막 필터 기준 — 이 (가장 새) 거품이 닿을 때 생긴다.
    func prepareStarReveal(_ annotations: [MLNAnnotation], mapView: MLNMapView) {
        let here = parent.userLocation ?? mapView.centerCoordinate
        // 링 중심 = 내 위치의 지금 화면 쪽 사본(평면 지도). 멀리 와 있으면 내 위치 쪽에서 밀려온다.
        let refLng = mapView.centerCoordinate.longitude
        let center = FlatPt.of(here, refLng: refLng)
        let sweep = revealSweep(mapView, center: center)
        let run = RevealRun(center: center, refLng: refLng, sweep: sweep, tintHex: parent.filterTintHex)
        revealPending = run

        // 별마다 링이 닿는 시각 — 출렁이는 테두리가 그 별 방향에서 실제로 닿는 순간에 맞춘다(Android hitTimeOf 와 같은 보정).
        revealDelays = [:]
        for case let a as DiaryAnnotation in annotations {
            guard let id = a.diary.id else { continue }
            // 링과 같은 평면 지도 거리·각도(별도 화면 쪽 사본 기준)
            let p = FlatPt.of(a.coordinate, refLng: refLng)
            let d = center.dist(p)
            let theta = center.theta(p)
            let base = StarRevealFx.hitTime(distance: d, sweep: sweep)
            let w = run.wobble.at(theta, base)
            revealDelays[id] = StarRevealFx.hitTime(distance: d / (1 + StarRevealFx.wobble * w), sweep: sweep)
        }
        revealSortedDelays = revealDelays.values.sorted()
        revealHidden = Set(revealDelays.keys)
        revealStart = CACurrentMediaTime()
    }

    /// 등장 시작 — 새 거품을 띄우고, 프레임마다 거품들·별 뷰·필터 빛/포털을 맞춘다.
    /// 이미 퍼지고 있는 거품이 있으면 **지우지 않고 함께 퍼지게** 둔다(예전엔 링을 처음부터 다시 그리며 이전 거품이 사라지고
    /// 바깥 색이 아직 확정 전인 기본색으로 돌아갔다 — 2026-10-15).
    func startStarReveal(_ mapView: MLNMapView) {
        revealLink?.invalidate()
        revealStart = CACurrentMediaTime()
        MusicManager.shared.resetRevealChime()

        // 필터 빛: 각 자리 = 그 자리를 덮은 가장 새 거품의 색, 어느 거품에도 안 덮인 자리 = 연속 전환 전의 확정 색.
        let wave = FilterWaveState.shared
        if revealRuns.isEmpty { wave.baseHex = wave.committedHex }
        if let run = revealPending {
            run.start = revealStart
            revealRuns.append(run)
        }
        revealPending = nil
        wave.active = true

        applyRevealToVisibleViews(mapView)
        updateRevealWaves(mapView, now: revealStart)
        let link = CADisplayLink(target: self, selector: #selector(revealTick(_:)))
        link.add(to: .main, forMode: .common)
        revealLink = link
    }

    @objc func revealTick(_ link: CADisplayLink) {
        guard let mapView = mapRef, let newest = revealRuns.last else { stopStarReveal(); return }
        let now = CACurrentMediaTime()
        let elapsed = now - revealStart

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

        // 거품들(이전 거품 포함) — 화면 통과 판정·삼켜진 거품 정리도 여기서 한다.
        updateRevealWaves(mapView, now: now)

        // 끝: 가장 새 거품이 화면을 다 지나가고 + 포털이 가라앉고 + 모든 별(화면 밖 포함)에 닿아 생겼을 때.
        let e = now - newest.start
        let lastHit = revealSortedDelays.last ?? 0
        if newest.passedAt >= 0,
           e >= newest.passedAt + max(StarRevealFx.ringFade, StarRevealFx.portalDecay),
           elapsed >= lastHit + StarRevealFx.pop {
            stopStarReveal()
        }
    }

    /// 등장 종료 — 모든 별을 제자리(불투명·원래 크기)로, 거품들은 걷어내고 필터 빛을 마지막 필터 색으로 확정한다.
    func stopStarReveal() {
        revealLink?.invalidate()
        revealLink = nil
        revealDelays = [:]
        revealSortedDelays = []
        let finalHex = revealRuns.last?.tintHex ?? parent.filterTintHex
        revealRuns = []
        revealPending = nil
        let wave = FilterWaveState.shared
        if wave.active {
            wave.committedHex = finalHex
            wave.bubbles = []
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

    /// 링이 화면을 다 덮는 데 필요한 거리(평면 지도) = center 에서 화면 네 모서리(지도 좌표)까지 거리 중 최댓값(Android revealSweepFlat).
    private func revealSweep(_ mapView: MLNMapView, center: FlatPt) -> Double {
        let refLng = mapView.centerCoordinate.longitude
        let c = center.nearX(FlatPt.of(CLLocationCoordinate2D(latitude: 0, longitude: refLng), refLng: refLng).x)
        let b = mapView.bounds
        var sweep = 30.0
        for corner in [CGPoint(x: b.minX, y: b.minY), CGPoint(x: b.maxX, y: b.minY),
                       CGPoint(x: b.minX, y: b.maxY), CGPoint(x: b.maxX, y: b.maxY)] {
            let ll = mapView.convert(corner, toCoordinateFrom: mapView)
            sweep = max(sweep, c.dist(FlatPt.of(ll, refLng: refLng)))
        }
        return sweep
    }

    /// 지도 위 출렁이는 거품 테두리(평면 지도 위 원, 크기 제한 없음)를 지금 카메라로 화면에 투영한 꼭짓점. Android ringScreenPath.
    private func ringPoints(_ screen: FlatScreen, run: RevealRun, elapsed: Double, radius: Double) -> [CGPoint] {
        let c = run.center.nearX(screen.refX)
        let r = max(radius, 0.5)
        var points: [CGPoint] = []
        points.reserveCapacity(StarRevealFx.ringSegments)
        for i in 0..<StarRevealFx.ringSegments {
            let a = 2 * Double.pi * Double(i) / Double(StarRevealFx.ringSegments)
            let ra = r * (1 + StarRevealFx.wobble * run.wobble.at(a, elapsed))
            points.append(screen.toScreen(FlatPt(x: c.x + ra * cos(a), y: c.y - ra * sin(a))))
        }
        return points
    }

    /// 함께 퍼지는 거품들의 한 프레임을 계산해 FilterWaveState 에 넘긴다(그리기는 SwiftUI FilterGlowOverlay). Android `revealWavesFrame`.
    ///  · 화면을 다 지나간 거품이 생기면 그보다 오래된 거품은 화면에 더는 안 보이므로 지우고, 바깥 색을 그 거품 색으로 넘긴다.
    ///  · 새 거품이 이전 거품을 **완전히 삼키면**(줌아웃 뒤 켠 거품은 더 빨라 따라잡는다) 이전 거품을 지운다.
    ///    덮인 부분은 그 전에도 그리기에서 가려지므로 삼켜지듯 자연스럽게 사라진다.
    private func updateRevealWaves(_ mapView: MLNMapView, now: CFTimeInterval) {
        let wave = FilterWaveState.shared
        // 1) 화면 통과 판정 — 링은 별과 관계없이 지금 보고 있는 화면을 다 지나갈 때까지 퍼진다.
        for r in revealRuns where r.passedAt < 0 {
            let e = now - r.start
            let radius = StarRevealFx.radius(at: e, sweep: r.sweep)
            if radius * (1 - StarRevealFx.wobble) >= revealSweep(mapView, center: r.center) || e >= StarRevealFx.ringMaxTime {
                r.passedAt = e
            }
        }
        // 2) 화면을 다 덮은 가장 새 거품보다 오래된 거품은 지운다(화면 밖 = 이제 그 거품 색).
        if let lastPassed = revealRuns.lastIndex(where: { $0.passedAt >= 0 }) {
            revealRuns.removeFirst(lastPassed)
            wave.baseHex = revealRuns[0].tintHex
        }
        // 3) 새 거품에 완전히 삼켜진 이전 거품은 지운다(출렁임 최대·최소까지 감안해 확실히 안쪽일 때만).
        if revealRuns.count > 1 {
            let runs = revealRuns
            let radii = runs.map { StarRevealFx.radius(at: now - $0.start, sweep: $0.sweep) }
            var keep: [RevealRun] = []
            for i in runs.indices {
                let ci = runs[i].center
                let swallowed = ((i + 1)..<runs.count).contains { j in
                    let gap = ci.dist(runs[j].center.nearX(ci.x))
                    return gap + radii[i] * (1 + StarRevealFx.wobble) <= radii[j] * (1 - StarRevealFx.wobble)
                }
                if !swallowed { keep.append(runs[i]) }
            }
            revealRuns = keep
        }

        let screen = FlatScreen(mapView)
        let bubbles: [FilterWaveState.Bubble] = revealRuns.map { r in
            let e = now - r.start
            let radius = StarRevealFx.radius(at: e, sweep: r.sweep)
            let ringAlpha = r.passedAt < 0 ? 1 : min(max(1 - (e - r.passedAt) / StarRevealFx.ringFade, 0), 1)
            // 포털 성운 세기 — 퍼지는 동안 1, 화면을 다 지나간 뒤 은은한 필터 빛으로 가라앉는다.
            let portal: Double
            if r.tintHex == nil {
                portal = 0
            } else if r.passedAt < 0 {
                portal = 1
            } else {
                let x = min(max((e - r.passedAt) / StarRevealFx.portalDecay, 0), 1)
                portal = 1 - (x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2)
            }
            // (성운 잔별 56개 — 작은 반투명 원 — 은 2026-10-19 삭제, Android 와 같이. 바탕 빛만 남았다.)
            // 시작점의 검은 구체 — 툭 생겼다가(0.15초) 거품이 터져 나오며 커지면서 사라진다.
            var orb: FilterWaveState.Orb?
            if e < StarRevealFx.orbTime, let screen {
                let c = screen.toScreen(r.center.nearX(screen.refX))
                let grow = min(e / 0.15, 1)
                let fade = min(max((e - 0.15) / (StarRevealFx.orbTime - 0.15), 0), 1)
                let rr = StarRevealFx.orbRadius * CGFloat((0.15 + 0.85 * (1 - (1 - grow) * (1 - grow))) * (1 + 0.3 * fade)) // 0 에서 시작하지 않는다(Android 와 같음)
                orb = .init(point: c, radius: rr, alpha: 1 - fade)
            }
            // 화면을 다 덮은 뒤엔 테두리가 옅어지는 동안(0.3초)만 경로를 만든다 — 그 뒤 링은 수천 km 로 커져 투영이 뒤집힌다.
            return .init(ring: ringAlpha > 0.01 && screen != nil ? ringPoints(screen!, run: r, elapsed: e, radius: radius) : [],
                         covers: r.passedAt >= 0,
                         ringAlpha: ringAlpha,
                         ringHex: r.tintHex ?? MapFilterKind.neutralHex,
                         tintHex: r.tintHex,
                         portal: portal,
                         orb: orb)
        }
        // 필터 빛·포털·테두리 경계(SwiftUI FilterGlowOverlay) — 지도 뷰와 같은 좌표계(둘 다 전체 화면, 안전 영역 무시).
        wave.bubbles = bubbles
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
