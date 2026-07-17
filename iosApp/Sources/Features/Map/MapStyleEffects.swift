import CoreLocation
import MapLibre
import UIKit

// ─────────────────────────────────────────────────────────────────────────────
// 지도 스타일 이펙트 — Android DiaryMap/DiaryMapMarkers 패리티(#3, #7).
//  · 별가루 파티클: 시작 위치 반경 20km 에 400개(시드 42, 위상 4그룹) + 트윙클 루프
//  · 별자리 라인: 뷰포트의 대표 별들을 최근접 2개와 잇는 3겹(후광/글로우/밝은 선) 라인
//  · 별 후광: 별색 바닥광 + 인기 별 오오라 2겹 CircleLayer(어노테이션 뷰 아래에 깔린다)
// ─────────────────────────────────────────────────────────────────────────────

/// Android DiaryMapMarkers 상수 패리티.
private enum StyleFx {
    static let particleCount = 400
    static let particleRadiusM = 20_000.0
    static let particleSeed: UInt64 = 42
    static let phaseGroups = 4
    static let particleSourceID = "star-particles-src"
    static let particleImageID = "star-particle"
    static func particleLayerID(_ g: Int) -> String { "star-particles-\(g)" }

    static let constellationSourceID = "constellation-lines"
    static let constellationHaloID = "constellation-halo"
    static let constellationGlowID = "constellation-glow"
    static let constellationLineID = "constellation-line"
    static let constellationHaloOpacity = 0.18
    static let constellationGlowOpacity = 0.42
    static let constellationLineOpacity = 0.95
    /// 별 하나가 이어지는 최근접 이웃 수(Android CONSTELLATION_NEIGHBORS).
    static let constellationNeighbors = 2

    /// 도보 길찾기 경로 — 별자리 선과 동일한 3겹(후광/글로우/얇은 밝은 선) 스타일(Android ROUTE_* 패리티).
    static let routeSourceID = "walking-route"
    static let routeHaloID = "walking-route-halo"
    static let routeGlowID = "walking-route-glow"
    static let routeLineID = "walking-route-line"

    static let auraSourceID = "diary-aura-src"
    static let auraLayerID = "diary-aura"
    static let groundLightLayerID = "diary-ground-light"
    /// 바닥 빛 웅덩이 불투명도(Android GROUND_LIGHT_OPACITY) + 지면 쪽 오프셋(GROUND_LIGHT_OFFSET_Y).
    static let groundLightOpacity = 0.30
    static let groundLightOffsetY = 8.0

    static let mint = UIColor(red: 0x6E / 255.0, green: 0xE7 / 255.0, blue: 0xB7 / 255.0, alpha: 1)
    static let brightLine = UIColor(red: 0xE6 / 255.0, green: 1.0, blue: 0xF4 / 255.0, alpha: 1)
}

/// 시드 고정 난수(SplitMix64) — Android `kotlin.random.Random(PARTICLE_SEED)` 대응.
/// (수열 자체는 다르지만 "매 실행 같은 배치"라는 계약은 동일하다.)
private struct SeededRandom {
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
    mutating func nextDouble() -> Double {
        Double(next() >> 11) * (1.0 / 9_007_199_254_740_992.0)
    }
}

extension MapLibreView.Coordinator {
    // MARK: 스타일 로드 → 이펙트 설치

    /// 스타일 로드 완료 — 파티클/별자리/후광 소스·레이어를 1회 설치하고 트윙클 루프를 시작한다.
    /// (Android `map.setStyle { style -> ... }` 블록 대응)
    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        styleRef = style
        mapRef = mapView
        guard style.source(withIdentifier: StyleFx.particleSourceID) == nil else { return }

        // ── 별가루 파티클 — 시작 위치 기준 1회 생성, 이후 갱신 없음(Android 동일) ──
        style.setImage(Self.particleImage(), forName: StyleFx.particleImageID)
        let center = parent.userLocation
            ?? LocationManager.lastSavedCoordinate
            ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
        let particleSource = MLNShapeSource(
            identifier: StyleFx.particleSourceID,
            features: Self.starParticleFeatures(center: center),
            options: nil
        )
        style.addSource(particleSource)
        for g in 0..<StyleFx.phaseGroups {
            let layer = MLNSymbolStyleLayer(identifier: StyleFx.particleLayerID(g), source: particleSource)
            layer.iconImageName = NSExpression(forConstantValue: StyleFx.particleImageID)
            layer.iconScale = Self.particleScaleExpression(zoom: mapView.zoomLevel)
            layer.iconOpacity = NSExpression(forConstantValue: 0)
            layer.iconAllowsOverlap = NSExpression(forConstantValue: true)
            layer.iconIgnoresPlacement = NSExpression(forConstantValue: true)
            layer.predicate = NSPredicate(format: "phaseGroup == %d", g)
            style.addLayer(layer)
        }

        // ── 별자리 라인 — 후광/글로우/밝은 선 3겹, 초기 불투명도 0(토글 시 페이드) ──
        // (Android 레이어 순서: 파티클 → 별자리 → 바닥광 → 오오라 — 라인이 빛 웅덩이 밑에 깔린다)
        let cSource = MLNShapeSource(identifier: StyleFx.constellationSourceID, features: [], options: nil)
        style.addSource(cSource)
        func lineLayer(_ id: String, color: UIColor, width: Double, blur: Double) -> MLNLineStyleLayer {
            let l = MLNLineStyleLayer(identifier: id, source: cSource)
            l.lineColor = NSExpression(forConstantValue: color)
            l.lineWidth = NSExpression(forConstantValue: width)
            l.lineBlur = NSExpression(forConstantValue: blur)
            l.lineOpacity = NSExpression(forConstantValue: 0)
            l.lineCap = NSExpression(forConstantValue: NSValue(mlnLineCap: .round))
            l.lineJoin = NSExpression(forConstantValue: NSValue(mlnLineJoin: .round))
            return l
        }
        style.addLayer(lineLayer(StyleFx.constellationHaloID, color: StyleFx.mint, width: 16, blur: 16))
        style.addLayer(lineLayer(StyleFx.constellationGlowID, color: StyleFx.mint, width: 8, blur: 8))
        style.addLayer(lineLayer(StyleFx.constellationLineID, color: StyleFx.brightLine, width: 1.7, blur: 0.6))

        // ── 도보 길찾기 경로 — 별자리 선과 동일한 3겹, 불투명도는 고정(경로 유무는 소스가 결정) ──
        // (Android ROUTE_HALO/GLOW/LINE 레이어 패리티 — 별자리 위, 바닥광 아래.)
        let rSource = MLNShapeSource(identifier: StyleFx.routeSourceID, features: [], options: nil)
        style.addSource(rSource)
        func routeLayer(_ id: String, color: UIColor, width: Double, blur: Double, opacity: Double) -> MLNLineStyleLayer {
            let l = MLNLineStyleLayer(identifier: id, source: rSource)
            l.lineColor = NSExpression(forConstantValue: color)
            l.lineWidth = NSExpression(forConstantValue: width)
            l.lineBlur = NSExpression(forConstantValue: blur)
            l.lineOpacity = NSExpression(forConstantValue: opacity)
            l.lineCap = NSExpression(forConstantValue: NSValue(mlnLineCap: .round))
            l.lineJoin = NSExpression(forConstantValue: NSValue(mlnLineJoin: .round))
            return l
        }
        style.addLayer(routeLayer(StyleFx.routeHaloID, color: StyleFx.mint, width: 16, blur: 16,
                                  opacity: StyleFx.constellationHaloOpacity))
        style.addLayer(routeLayer(StyleFx.routeGlowID, color: StyleFx.mint, width: 8, blur: 8,
                                  opacity: StyleFx.constellationGlowOpacity))
        style.addLayer(routeLayer(StyleFx.routeLineID, color: StyleFx.brightLine, width: 1.7, blur: 0.6,
                                  opacity: StyleFx.constellationLineOpacity))

        // ── 별 후광 — Android 바닥광+오오라 2겹 패리티(별색, 줌 보간 스톱 동일) ──
        //  · 바닥 빛 웅덩이: 모든 별 밑에 은은하게(반경 0.6→7 × sizeMult, 지면 쪽 +8pt).
        //  · 오오라: 인기(큰) 별만 발광(불투명도 sizeMult 1→0 / 1.4→0.12 / 3→0.42, 반경 2→26 × sizeMult).
        // 색은 feature 의 auraColor(그 별의 팔레트 색) — 데이터 주도 표현식은 JSON 으로 만든다.
        let auraSource = MLNShapeSource(identifier: StyleFx.auraSourceID, features: [], options: nil)
        style.addSource(auraSource)
        let auraColorExpr = NSExpression(mglJSONObject: ["to-color", ["get", "auraColor"]] as [Any])
        func radiusExpr(_ stops: [Double: Double]) -> NSExpression {
            var json: [Any] = ["interpolate", ["linear"], ["zoom"]]
            for zoom in stops.keys.sorted() {
                json.append(zoom)
                json.append(["*", stops[zoom]!, ["get", "sizeMult"] as [Any]] as [Any])
            }
            return NSExpression(mglJSONObject: json)
        }
        let ground = MLNCircleStyleLayer(identifier: StyleFx.groundLightLayerID, source: auraSource)
        ground.circleColor = auraColorExpr
        ground.circleRadius = radiusExpr([6: 0.6, 10: 2, 13: 4.5, 15: 7])
        ground.circleOpacity = NSExpression(forConstantValue: StyleFx.groundLightOpacity)
        ground.circleBlur = NSExpression(forConstantValue: 1.4)
        ground.circleTranslation = NSExpression(forConstantValue:
            NSValue(cgVector: CGVector(dx: 0, dy: StyleFx.groundLightOffsetY)))
        style.addLayer(ground)
        let aura = MLNCircleStyleLayer(identifier: StyleFx.auraLayerID, source: auraSource)
        aura.circleColor = auraColorExpr
        aura.circleRadius = radiusExpr([6: 2, 10: 6, 13: 14, 15: 26])
        aura.circleOpacity = NSExpression(mglJSONObject:
            ["interpolate", ["linear"], ["get", "sizeMult"], 1, 0, 1.4, 0.12, 3, 0.42] as [Any])
        aura.circleBlur = NSExpression(forConstantValue: 1.0)
        style.addLayer(aura)

        refreshAuraFeatures(mapView)
        updateRouteShape()
        reportWorldVoid(mapView)
        startTwinkleLoop()
    }

    /// 도보 경로 소스 갱신 — parent.route(최근접점→목적지 부분 경로)를 그대로 반영.
    /// 비어 있으면 소스를 비워 레이어가 그리지 않는다(불투명도는 항상 고정).
    func updateRouteShape() {
        guard let style = styleRef,
              let src = style.source(withIdentifier: StyleFx.routeSourceID) as? MLNShapeSource
        else { return }
        var coords = parent.route
        guard coords.count >= 2 else {
            src.shape = MLNShapeCollectionFeature(shapes: [])
            return
        }
        src.shape = MLNPolylineFeature(coordinates: &coords, count: UInt(coords.count))
    }

    /// 뷰 해체 시 타이머/태스크 정리(순환 참조·유령 갱신 방지).
    func teardownStyleEffects() {
        twinkleTimer?.invalidate()
        twinkleTimer = nil
        constellationFadeTask?.cancel()
    }

    // MARK: 별가루 파티클

    /// 파티클 비트맵 — 흰 글로우 + 흰 코어의 작은 점(Android particleBitmap 24px 대응).
    private static func particleImage() -> UIImage {
        let side: CGFloat = 24
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { ctx in
            let cg = ctx.cgContext
            let center = CGPoint(x: side / 2, y: side / 2)
            let colors = [
                UIColor.white.withAlphaComponent(0.85).cgColor,
                UIColor.white.withAlphaComponent(0).cgColor,
            ] as CFArray
            if let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                     colors: colors, locations: [0, 1]) {
                cg.drawRadialGradient(grad, startCenter: center, startRadius: 0,
                                      endCenter: center, endRadius: side * 0.34, options: [])
            }
            cg.setFillColor(UIColor.white.cgColor)
            let core = side * 0.13
            cg.fillEllipse(in: CGRect(x: center.x - core, y: center.y - core,
                                      width: core * 2, height: core * 2))
        }
    }

    /// 별가루 FeatureCollection — [center] 반경 20km 균등 분포 400개, 시드 고정.
    /// (Android starParticleFeatures 대응 — sqrt 로 면적 균등 분포.)
    private static func starParticleFeatures(center: CLLocationCoordinate2D) -> [MLNPointFeature] {
        var rnd = SeededRandom(seed: StyleFx.particleSeed)
        let cosLat = max(cos(center.latitude * .pi / 180), 0.01)
        return (0..<StyleFx.particleCount).map { i in
            let r = StyleFx.particleRadiusM * rnd.nextDouble().squareRoot()
            let theta = rnd.nextDouble() * 2 * .pi
            let dLat = r * cos(theta) / 111_320.0
            let dLng = r * sin(theta) / (111_320.0 * cosLat)
            let f = MLNPointFeature()
            f.coordinate = CLLocationCoordinate2D(
                latitude: center.latitude + dLat, longitude: center.longitude + dLng
            )
            f.attributes = [
                "depth": 0.5 + rnd.nextDouble() * 0.5, // 0.5..1.0 크기 배율(원근감)
                "phaseGroup": i % StyleFx.phaseGroups,
            ]
            return f
        }
    }

    /// 파티클 iconScale — 줌 보간(9→0, 12→0.45, 15→0.8) × per-feature depth.
    /// (Android particleSizeExpression 스톱 동일 — 줌은 카메라 콜백에서 갱신한다.)
    private static func particleScaleExpression(zoom: Double) -> NSExpression {
        let base: Double
        switch zoom {
        case ..<9: base = 0
        case ..<12: base = (zoom - 9) / 3 * 0.45
        case ..<15: base = 0.45 + (zoom - 12) / 3 * 0.35
        default: base = 0.8
        }
        return NSExpression(forFunction: "multiply:by:", arguments: [
            NSExpression(forConstantValue: base),
            NSExpression(forKeyPath: "depth"),
        ])
    }

    /// 카메라 이동 중/후 — 파티클 크기를 현재 줌에 맞춰 갱신(변화 있을 때만).
    /// (후광/오오라 반경은 줌 interpolate 표현식이라 갱신 불필요)
    func applyStyleEffectZoom(_ mapView: MLNMapView) {
        guard let style = styleRef else { return }
        let zoom = mapView.zoomLevel
        guard abs(zoom - lastEffectZoom) > 0.05 else { return }
        lastEffectZoom = zoom
        for g in 0..<StyleFx.phaseGroups {
            guard let layer = style.layer(
                withIdentifier: StyleFx.particleLayerID(g)) as? MLNSymbolStyleLayer else { continue }
            layer.iconScale = Self.particleScaleExpression(zoom: zoom)
        }
    }

    /// 팔레트 색 → "#RRGGBB"(오오라 CircleLayer 색상용). (Android starColorHex 패리티)
    private static func starColorHex(_ colorIndex: Int) -> String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(StarStyle.color(colorIndex)).getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X",
                      Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
    }

    /// 후광 소스 갱신 — 현재 대표 별(머지 결과) 좌표 + sizeMult + 별색. 마커 갱신 시 함께 호출.
    func refreshAuraFeatures(_ mapView: MLNMapView) {
        guard let style = styleRef,
              let src = style.source(withIdentifier: StyleFx.auraSourceID) as? MLNShapeSource
        else { return }
        let features: [MLNPointFeature] = (mapView.annotations ?? [])
            .compactMap { $0 as? DiaryAnnotation }
            .map { a in
                let f = MLNPointFeature()
                f.coordinate = a.coordinate
                f.attributes = [
                    "sizeMult": max(a.sizeMult, 1.0),
                    "auraColor": Self.starColorHex(a.diary.starColor),
                ]
                return f
            }
        src.shape = MLNShapeCollectionFeature(shapes: features)
    }

    /// 트윙클 루프(20fps) — 위상 그룹별 파티클 레이어 opacity 만 갱신(GeoJSON 재생성 없음).
    /// Android 루프와 동일 공식: 그룹마다 속도(2.0+0.4g)·위상(g·2π/4)이 달라 덜 기계적으로 반짝인다.
    private func startTwinkleLoop() {
        guard twinkleTimer == nil else { return }
        let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.twinkleT += 0.05
            // 카메라 이동 중엔 스타일 변경을 멈춰 팬/줌 끊김을 방지(Android 동일).
            guard !self.isCameraMoving, let style = self.styleRef, let map = self.mapRef else { return }
            let zoom = map.zoomLevel
            // 저줌 게이트: 9 밑 숨김 → 12 에서 트윙클 최대(Android particleOpacityExpression 스톱).
            let gate = min(max((zoom - 9) / 3, 0), 1)
            if gate <= 0 {
                if self.lastGateZero { return } // 이미 꺼져 있으면 스킵(배터리 절약)
                self.lastGateZero = true
            } else {
                self.lastGateZero = false
            }
            for g in 0..<StyleFx.phaseGroups {
                guard let layer = style.layer(
                    withIdentifier: StyleFx.particleLayerID(g)) as? MLNSymbolStyleLayer else { continue }
                let phase = Double(g) * (2 * .pi / Double(StyleFx.phaseGroups))
                let speed = 2.0 + Double(g) * 0.4
                let twinkle = 0.25 + 0.75 * ((sin(self.twinkleT * speed + phase) + 1) / 2)
                layer.iconOpacity = NSExpression(forConstantValue: gate * twinkle)
            }
        }
        twinkleTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }

    // MARK: 별자리 라인

    /// 별자리 토글 — 켜면 재계산이 페이드 인까지 담당(rebuildConstellation),
    /// 끄면 페이드 아웃(380ms) 후 소스를 비운다. (Android constellationFade Animatable 대응.)
    func setConstellation(enabled: Bool, mapView: MLNMapView) {
        guard enabled != constellationOn else { return }
        constellationOn = enabled
        if enabled {
            rebuildConstellation(mapView)
            return
        }
        lastConstellationKey = nil
        constellationRebuildTask?.cancel()
        constellationFadeTask?.cancel()
        constellationFadeTask = Task { @MainActor [weak self] in
            let steps = 12
            let start = self?.constellationFadeValue ?? 1
            for s in 1...steps {
                guard let self, !Task.isCancelled else { return }
                self.applyConstellationOpacity(start * (1 - Double(s) / Double(steps)))
                try? await Task.sleep(nanoseconds: UInt64(0.38 / Double(steps) * 1_000_000_000))
            }
            guard let self, !Task.isCancelled, let style = self.styleRef,
                  let src = style.source(withIdentifier: StyleFx.constellationSourceID) as? MLNShapeSource
            else { return }
            src.shape = MLNShapeCollectionFeature(shapes: [])
        }
    }

    private func applyConstellationOpacity(_ f: Double) {
        guard let style = styleRef else { return }
        constellationFadeValue = f
        let sets: [(String, Double)] = [
            (StyleFx.constellationHaloID, StyleFx.constellationHaloOpacity),
            (StyleFx.constellationGlowID, StyleFx.constellationGlowOpacity),
            (StyleFx.constellationLineID, StyleFx.constellationLineOpacity),
        ]
        for (id, target) in sets {
            (style.layer(withIdentifier: id) as? MLNLineStyleLayer)?
                .lineOpacity = NSExpression(forConstantValue: target * f)
        }
    }

    /// 카메라 idle/마커 갱신 시 별자리 재계산 요청 — 90ms 디바운스(Android idle 디바운스 동일).
    func requestConstellationRebuild(_ mapView: MLNMapView) {
        guard constellationOn else { return }
        constellationRebuildTask?.cancel()
        constellationRebuildTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 90_000_000)
            guard let self, !Task.isCancelled else { return }
            self.rebuildConstellation(mapView)
        }
    }

    /// 뷰포트에 보이는 대표 별들을 화면거리 최근접 2개와 잇는다(중복 간선 제거).
    /// 선 구성이 바뀌면 짧게 페이드 아웃(160ms) 후 새 구성으로 페이드 인(550ms) —
    /// 줌/이동 후 갱신이 즉시 스냅으로 바뀌는 어색함을 없앤다(Android 동일).
    /// (Android buildConstellationFeatures 대응 — maxLink = min(화면 변)×0.55.)
    private func rebuildConstellation(_ mapView: MLNMapView) {
        guard constellationOn, let style = styleRef,
              let src = style.source(withIdentifier: StyleFx.constellationSourceID) as? MLNShapeSource
        else { return }
        let w = mapView.bounds.width
        let h = mapView.bounds.height
        guard w > 1, h > 1 else { return }
        var pts: [(screen: CGPoint, coord: CLLocationCoordinate2D)] = []
        for a in (mapView.annotations ?? []).compactMap({ $0 as? DiaryAnnotation }) {
            let p = mapView.convert(a.coordinate, toPointTo: mapView)
            if p.x >= 0, p.x <= w, p.y >= 0, p.y <= h { pts.append((p, a.coordinate)) }
        }
        guard pts.count >= 2 else {
            lastConstellationKey = ""
            src.shape = MLNShapeCollectionFeature(shapes: [])
            return
        }
        let maxLink = Double(min(w, h)) * 0.55
        let maxLink2 = maxLink * maxLink
        var edges = Set<Int>() // i<j 를 i*N+j 로 인코딩해 중복 제거(Android 동일)
        var lines: [MLNPolylineFeature] = []
        var key = ""
        for i in pts.indices {
            let nearest = pts.indices
                .filter { $0 != i }
                .map { j -> (Int, Double) in
                    let dx = Double(pts[i].screen.x - pts[j].screen.x)
                    let dy = Double(pts[i].screen.y - pts[j].screen.y)
                    return (j, dx * dx + dy * dy)
                }
                .filter { $0.1 <= maxLink2 }
                .sorted { $0.1 < $1.1 }
                .prefix(StyleFx.constellationNeighbors)
            for (j, _) in nearest {
                let lo = min(i, j)
                let hi = max(i, j)
                guard edges.insert(lo * pts.count + hi).inserted else { continue }
                var coords = [pts[lo].coord, pts[hi].coord]
                lines.append(MLNPolylineFeature(coordinates: &coords, count: 2))
                key += String(format: "%.6f,%.6f-%.6f,%.6f;",
                              coords[0].latitude, coords[0].longitude,
                              coords[1].latitude, coords[1].longitude)
            }
        }
        guard key != lastConstellationKey else { return } // 선 구성 그대로면 페이드 불필요
        lastConstellationKey = key
        constellationFadeTask?.cancel()
        constellationFadeTask = Task { @MainActor [weak self] in
            guard let self else { return }
            // 이미 보이던 중의 갱신이면 짧게 사라진 뒤 새 구성으로 교체.
            if self.constellationFadeValue > 0.01 {
                let outSteps = 6
                let start = self.constellationFadeValue
                for s in 1...outSteps {
                    guard !Task.isCancelled else { return }
                    self.applyConstellationOpacity(start * (1 - Double(s) / Double(outSteps)))
                    try? await Task.sleep(nanoseconds: UInt64(0.16 / Double(outSteps) * 1_000_000_000))
                }
            }
            guard !Task.isCancelled else { return }
            src.shape = MLNShapeCollectionFeature(shapes: lines)
            let inSteps = 12
            for s in 1...inSteps {
                guard !Task.isCancelled else { return }
                self.applyConstellationOpacity(Double(s) / Double(inSteps))
                try? await Task.sleep(nanoseconds: UInt64(0.55 / Double(inSteps) * 1_000_000_000))
            }
        }
    }
}
