import CoreLocation
import MapLibre
import QuartzCore
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 별자리 "선 긋기" — Android DiaryMap/DiaryMapMarkers 의 planConstellationDraw 패리티(2026-09-25, 광고 레퍼런스).
//  · 켜면 선이 한꺼번에 뜨지 않고 **한 별에서 다음 별로 뻗어 나간다** — 화면 중앙에 가까운 별에서 출발해
//    그래프 거리 순(일정한 속도)으로 흐르고, 선 끝에는 머리 빛점, 별에 닿는 순간 도착 플래시(링).
//  · 줌/이동으로 구성이 바뀌면: 남는 선은 그대로, 새 선은 기존 별자리에서 이어 자라고, 빠지는 선은 되감긴다.
//  · 끌 때는 예전처럼 레이어 불투명도 페이드 아웃(MapStyleEffects.setConstellation).
// 수치는 Android CONSTELLATION_* 와 동일(dp = pt). 값 drift 금지.
// ─────────────────────────────────────────────────────────────────────────────

/// 별자리 선 1개 — 두 대표 별(a, b). key = 두 id 를 정렬해 이은 것(방향 무관 동일성).
struct ConstellationEdgeIOS {
    let key: String
    let aId: String
    let a: CLLocationCoordinate2D
    let ap: CGPoint
    let bId: String
    let b: CLLocationCoordinate2D
    let bp: CGPoint
    var length: Double { max(1, Double(hypot(bp.x - ap.x, bp.y - ap.y))) }
}

/// 선 긋기 계획 한 줄 — fromA 쪽 별에서 반대쪽으로. 진행도 = (d − startPx) / length 를 0..1 로.
struct ConstellationDrawIOS {
    let edge: ConstellationEdgeIOS
    let fromA: Bool
    let startPx: Double
    func progress(_ d: Double) -> Double { min(max((d - startPx) / edge.length, 0), 1) }
    var fromId: String { fromA ? edge.aId : edge.bId }
    var toId: String { fromA ? edge.bId : edge.aId }
    var from: CLLocationCoordinate2D { fromA ? edge.a : edge.b }
    var to: CLLocationCoordinate2D { fromA ? edge.b : edge.a }
}

/// 진행 중인 선 긋기 1회분.
final class ConstellationRun {
    var plan: [ConstellationDrawIOS] = []
    var retract: [(edge: ConstellationEdgeIOS, fromA: Bool, p0: Double)] = []
    var start: CFTimeInterval = 0
    var pxPerMs: Double = 1
    /// 별 id → 도착 플래시 시작(ms).
    var flashStart: [String: Double] = [:]
    /// 이미 선이 닿아 있던 별 — 도착 플래시 생략.
    var alreadyThere: Set<String> = []
    var nodes: [String: CLLocationCoordinate2D] = [:]
}

enum ConstellationFx {
    /// 선이 뻗는 기본 속도(pt/초) — Android CONSTELLATION_DRAW_DP_PER_SEC.
    static let drawPtPerSec = 320.0
    /// 전체 선 긋기 상한(ms) — CONSTELLATION_DRAW_MAX_MS.
    static let drawMaxMs = 1800.0
    /// 빠지는 선 되감기(ms) — CONSTELLATION_RETRACT_MS.
    static let retractMs = 240.0
    /// 도착 플래시 지속(ms)/최대 반경(pt) — CONSTELLATION_FLASH_MS / _RADIUS_DP.
    static let flashMs = 560.0
    static let flashRadius = 15.0

    static let fxSourceID = "constellation-fx"
    static let flashLayerID = "constellation-flash"
    static let tipHaloLayerID = "constellation-tip-halo"
    static let tipLayerID = "constellation-tip"

    /// 빛이 별자리를 타고 흐르듯 긋는 순서(Android planConstellationDraw 와 같은 알고리즘).
    /// - 이미 그려진 선은 그 방향 그대로 이어 뻗고, 그 출발 별은 도달 거리 0.
    /// - 출발점이 없는 연결 덩어리는 화면 중앙에 가장 가까운 별에서 시작.
    /// - 각 별 도달 거리 = 선을 타고 간 최단 화면 거리(다익스트라). 새 선은 가까운 쪽에서 그만큼 늦게 출발.
    static func plan(edges: [ConstellationEdgeIOS],
                     drawn: [String: (fromA: Bool, p: Double)],
                     cx: Double, cy: Double) -> (plan: [ConstellationDrawIOS], starts: [String]) {
        var adj: [String: [ConstellationEdgeIOS]] = [:]
        var pos: [String: CGPoint] = [:]
        for e in edges {
            adj[e.aId, default: []].append(e)
            adj[e.bId, default: []].append(e)
            pos[e.aId] = e.ap
            pos[e.bId] = e.bp
        }
        var reach: [String: Double] = [:]
        func seed(_ id: String, _ d: Double) { if d < (reach[id] ?? .greatestFiniteMagnitude) { reach[id] = d } }
        for e in edges {
            guard let prev = drawn[e.key] else { continue }
            seed(prev.fromA ? e.aId : e.bId, 0)
            seed(prev.fromA ? e.bId : e.aId, (1 - prev.p) * e.length)
        }
        var starts: [String] = []
        var visited = Set<String>()
        for node in adj.keys where !visited.contains(node) {
            visited.insert(node)
            var comp = [node]
            var k = 0
            while k < comp.count {
                for e in adj[comp[k]] ?? [] {
                    let o = e.aId == comp[k] ? e.bId : e.aId
                    if visited.insert(o).inserted { comp.append(o) }
                }
                k += 1
            }
            if !comp.contains(where: { reach[$0] != nil }) {
                let s = comp.min { l, r in
                    let pl = pos[l]!, pr = pos[r]!
                    let dl = (Double(pl.x) - cx) * (Double(pl.x) - cx) + (Double(pl.y) - cy) * (Double(pl.y) - cy)
                    let dr = (Double(pr.x) - cx) * (Double(pr.x) - cx) + (Double(pr.y) - cy) * (Double(pr.y) - cy)
                    return dl < dr
                }!
                reach[s] = 0
                starts.append(s)
            }
        }
        var done = Set<String>()
        while let u = reach.filter({ !done.contains($0.key) }).min(by: { $0.value < $1.value })?.key {
            done.insert(u)
            let du = reach[u]!
            for e in adj[u] ?? [] {
                seed(e.aId == u ? e.bId : e.aId, du + e.length)
            }
        }
        let plan = edges.map { e -> ConstellationDrawIOS in
            if let prev = drawn[e.key] {
                return ConstellationDrawIOS(edge: e, fromA: prev.fromA, startPx: -prev.p * e.length)
            }
            let ra = reach[e.aId] ?? 0
            let rb = reach[e.bId] ?? 0
            return ConstellationDrawIOS(edge: e, fromA: ra <= rb, startPx: min(ra, rb))
        }
        return (plan, starts)
    }

    /// 웹 메르카토르 직선 위의 점 — 지도에 그려지는 직선과 정확히 겹치도록 위도는 메르카토르 y 로 보간.
    static func lerpMercator(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D, _ t: Double) -> CLLocationCoordinate2D {
        func y(_ lat: Double) -> Double { log(tan(.pi / 4 + lat * .pi / 180 / 2)) }
        let yy = y(a.latitude) + (y(b.latitude) - y(a.latitude)) * t
        let lat = (2 * atan(exp(yy)) - .pi / 2) * 180 / .pi
        return CLLocationCoordinate2D(latitude: lat, longitude: a.longitude + (b.longitude - a.longitude) * t)
    }
}

extension MapLibreView.Coordinator {

    /// 새 선 구성으로 선 긋기 시작(진행 중이던 것은 진행도를 이어받는다).
    func startConstellationDraw(edges: [ConstellationEdgeIOS], mapView: MLNMapView) {
        let result = ConstellationFx.plan(
            edges: edges, drawn: constellationDrawn,
            cx: Double(mapView.bounds.width / 2), cy: Double(mapView.bounds.height / 2)
        )
        for e in edges { constellationGeometry[e.key] = e }
        let newKeys = Set(edges.map { $0.key })
        let run = ConstellationRun()
        run.plan = result.plan
        run.retract = constellationDrawn.compactMap { key, v in
            guard !newKeys.contains(key), let g = constellationGeometry[key] else { return nil }
            return (edge: g, fromA: v.fromA, p0: v.p)
        }
        for d in result.plan where d.progress(0) >= 1 {
            run.alreadyThere.insert(d.fromId)
            run.alreadyThere.insert(d.toId)
        }
        let basePxPerMs = ConstellationFx.drawPtPerSec / 1000
        let totalPx = result.plan.map { $0.startPx + $0.edge.length }.max() ?? 0
        run.pxPerMs = max(basePxPerMs, totalPx / ConstellationFx.drawMaxMs)
        for s in result.starts { run.flashStart[s] = 0 } // 출발 별도 한 번 반짝
        for e in edges { run.nodes[e.aId] = e.a; run.nodes[e.bId] = e.b }
        run.start = CACurrentMediaTime()
        constellationRun = run

        constellationLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(constellationTick(_:)))
        link.add(to: .main, forMode: .common)
        constellationLink = link
    }

    /// 선 긋기 즉시 중단(끄기/해체) — 선은 그대로 두고 머리 빛점/플래시만 지운다.
    func stopConstellationDraw() {
        constellationLink?.invalidate()
        constellationLink = nil
        constellationRun = nil
        (styleRef?.source(withIdentifier: ConstellationFx.fxSourceID) as? MLNShapeSource)?.shape =
            MLNShapeCollectionFeature(shapes: [])
    }

    @objc func constellationTick(_ link: CADisplayLink) {
        guard let run = constellationRun, let style = styleRef,
              let src = style.source(withIdentifier: "constellation-lines") as? MLNShapeSource,
              let fxSrc = style.source(withIdentifier: ConstellationFx.fxSourceID) as? MLNShapeSource
        else { stopConstellationDraw(); return }
        let ms = (CACurrentMediaTime() - run.start) * 1000
        let dist = ms * run.pxPerMs
        var lines: [MLNPolylineFeature] = []
        var fx: [MLNPointFeature] = []
        var busy = false

        for d in run.plan {
            let p = d.progress(dist)
            if p > 0 { constellationDrawn[d.edge.key] = (d.fromA, p) }
            guard p > 0 else { busy = true; continue }
            let head = ConstellationFx.lerpMercator(d.from, d.to, p)
            var coords = [d.from, head]
            lines.append(MLNPolylineFeature(coordinates: &coords, count: 2))
            if p < 1 {
                busy = true
                let tip = MLNPointFeature()
                tip.coordinate = head
                // 도착 직전 살짝 사그라들어 플래시로 자연스럽게 이어진다(Android 동일).
                tip.attributes = ["kind": "tip", "a": 1 - min(max((p - 0.85) / 0.15, 0), 1) * 0.6]
                fx.append(tip)
            } else if !run.alreadyThere.contains(d.toId), run.flashStart[d.toId] == nil {
                run.flashStart[d.toId] = (d.startPx + d.edge.length) / run.pxPerMs
            }
        }
        for r in run.retract {
            let p = r.p0 * (1 - ms / ConstellationFx.retractMs)
            guard p > 0 else { constellationDrawn[r.edge.key] = nil; continue }
            busy = true
            constellationDrawn[r.edge.key] = (r.fromA, p)
            let from = r.fromA ? r.edge.a : r.edge.b
            let to = r.fromA ? r.edge.b : r.edge.a
            var coords = [from, ConstellationFx.lerpMercator(from, to, p)]
            lines.append(MLNPolylineFeature(coordinates: &coords, count: 2))
        }
        for (id, st) in run.flashStart {
            let u = (ms - st) / ConstellationFx.flashMs
            if u >= 1 { continue }
            busy = true
            guard u >= 0, let c = run.nodes[id] else { continue }
            let ease = 1 - (1 - u) * (1 - u)
            let f = MLNPointFeature()
            f.coordinate = c
            f.attributes = [
                "kind": "flash",
                "r": 3 + (ConstellationFx.flashRadius - 3) * ease,
                "a": 0.9 * (1 - u) * (1 - u),
            ]
            fx.append(f)
        }
        src.shape = MLNShapeCollectionFeature(shapes: lines)
        fxSrc.shape = MLNShapeCollectionFeature(shapes: fx)
        if !busy {
            constellationLink?.invalidate()
            constellationLink = nil
            constellationRun = nil
        }
    }
}
