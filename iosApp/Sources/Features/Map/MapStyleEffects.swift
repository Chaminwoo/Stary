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

    static let auraSourceID = "diary-aura-src"
    static let auraLayerID = "diary-aura"
    static let groundLightLayerID = "diary-ground-light"
    /// 바닥 빛 웅덩이 불투명도(Android GROUND_LIGHT_OPACITY) + 지면 쪽 오프셋(GROUND_LIGHT_OFFSET_Y).
    static let groundLightOpacity = 0.30
    static let groundLightOffsetY = 8.0
    /// 필터가 걸려 있을 때(2026-10-10 "조금 더 밝게") 바닥 빛 불투명도 배율 — Android FILTER_GROUND_BOOST 와 같은 값.
    /// (모든 별에 오오라 하한을 깔던 것은 지도에 반투명 원이 여러 개 생겨 2026-10-19 삭제 — Android 와 같이.)
    static let highlightGroundBoost = 1.6
    /// 신규 별(24시간 이내) 오오라 — 좋아요 0개여도 옅게 발광(Android FRESH_WINDOW_MS / FRESH_AURA_OPACITY, 값 동일).
    static let freshWindowMs: Int64 = 86_400_000
    static let freshAuraOpacity = 0.16

    /// 별빛 공방 오라(장착한 사람의 별) — Android sl-aura-glow/pulse/sprite 패리티(위상 그룹 없이 1벌).
    ///  · glow  : 은은한 빛(모든 패턴의 바탕, 숨 쉬듯 밝기만 변함)
    ///  · pulse : 별에서 천천히 퍼져 나가며 사라지는 고리(약 4.2초)
    ///  · sprite: 별 둘레 그림(`StarlightAura` — 달·반딧불·나선·토성 고리·혜성)이 돌거나 흔들린다. 별 어노테이션 뷰 아래에 깔린다.
    /// 모르는 패턴은 halo(glow 만)로 그린다 — 새 패턴을 DB 에 넣어도 이전 버전은 빛으로만 보인다.
    static let slGlowLayerID = "sl-aura-glow"
    static let slPulseLayerID = "sl-aura-pulse"
    static let slSpriteLayerID = "sl-aura-sprite"
    static let slPatterns: Set<String> = ["halo", "pulse"]

    /// 내 위치 점(초록 원) — Android `current-location` 소스 / `current-location-layer` 패리티.
    static let myLocationSourceID = "current-location-src"
    static let myLocationLayerID = "current-location-layer"
    /// 줌을 풀수록 작아진다: (줌, 반지름, 테두리 두께). 사이는 선형 보간, 양 끝 밖은 끝값 유지.
    /// 줌 15(기본 진입 줌) = 예전 고정값(반지름 7 / 테두리 2). Android `MY_LOCATION_STOPS`(DiaryMapMarkers.kt) 와 같은 값.
    static let myLocationStops: [(zoom: Double, radius: Double, stroke: Double)] = [
        (2, 2.0, 0.8), (6, 3.0, 1.0), (10, 4.5, 1.4), (13, 6.0, 1.8), (15, 7.0, 2.0),
    ]

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
        // 새 스타일엔 공방 오라 스프라이트 이미지가 없다 — 다음 갱신에서 다시 등록하게 키를 비운다.
        lastSlAuraKey = "|"
        ensureBaseTilt(mapView) // 기본 기울기(25°) — 프레임이 잡힌 뒤라 여기서부터 pitch 가 먹는다
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

        // ── 별자리 FX — 뻗어 가는 선의 머리 빛점(tip) + 별 도착 플래시(flash). Android CONSTELLATION_FX_* 패리티 ──
        // 속성: kind("tip"/"flash"), a(불투명도 0..1), r(플래시 반경 pt).
        let fxSource = MLNShapeSource(identifier: ConstellationFx.fxSourceID, features: [], options: nil)
        style.addSource(fxSource)
        let flash = MLNCircleStyleLayer(identifier: ConstellationFx.flashLayerID, source: fxSource)
        flash.predicate = NSPredicate(format: "kind == %@", "flash")
        flash.circleRadius = NSExpression(forKeyPath: "r")
        flash.circleColor = NSExpression(forConstantValue: StyleFx.mint)
        flash.circleOpacity = NSExpression(mglJSONObject: ["*", 0.16, ["get", "a"]] as [Any])
        flash.circleStrokeColor = NSExpression(forConstantValue: StyleFx.brightLine)
        flash.circleStrokeWidth = NSExpression(forConstantValue: 1.4)
        flash.circleStrokeOpacity = NSExpression(forKeyPath: "a")
        flash.circleBlur = NSExpression(forConstantValue: 0.15)
        style.addLayer(flash)
        let tipHalo = MLNCircleStyleLayer(identifier: ConstellationFx.tipHaloLayerID, source: fxSource)
        tipHalo.predicate = NSPredicate(format: "kind == %@", "tip")
        tipHalo.circleRadius = NSExpression(forConstantValue: 9)
        tipHalo.circleColor = NSExpression(forConstantValue: StyleFx.mint)
        tipHalo.circleBlur = NSExpression(forConstantValue: 1)
        tipHalo.circleOpacity = NSExpression(mglJSONObject: ["*", 0.55, ["get", "a"]] as [Any])
        style.addLayer(tipHalo)
        let tip = MLNCircleStyleLayer(identifier: ConstellationFx.tipLayerID, source: fxSource)
        tip.predicate = NSPredicate(format: "kind == %@", "tip")
        tip.circleRadius = NSExpression(forConstantValue: 2.6)
        tip.circleColor = NSExpression(forConstantValue: UIColor(red: 0xF4 / 255.0, green: 1, blue: 0xF9 / 255.0, alpha: 1))
        tip.circleBlur = NSExpression(forConstantValue: 0.4)
        tip.circleOpacity = NSExpression(forKeyPath: "a")
        style.addLayer(tip)

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
        // 인기 곡선(sizeMult)과 신규 별 바닥값 중 큰 값 — Android auraOpacityExpression 패리티.
        // fresh 는 숫자(1/0)로 넣는다(Bool↔NSNumber 비교 타입이 어긋나 항상 false 가 되는 일을 피하려고).
        aura.circleOpacity = NSExpression(mglJSONObject:
            ["max",
             ["interpolate", ["linear"], ["get", "sizeMult"], 1, 0, 1.4, 0.12, 3, 0.42] as [Any],
             ["*", ["get", "fresh"] as [Any], StyleFx.freshAuraOpacity] as [Any]] as [Any])
        aura.circleBlur = NSExpression(forConstantValue: 1.0)
        style.addLayer(aura)

        // 별빛 공방 오라 — 빛 · 퍼지는 고리 · 숨 쉬는 고리. 트윙클 루프가 밝기/반경을 갱신한다.
        let slGlow = MLNCircleStyleLayer(identifier: StyleFx.slGlowLayerID, source: auraSource)
        slGlow.circleColor = NSExpression(mglJSONObject: ["to-color", ["get", "slAuraColor"]] as [Any])
        slGlow.circleRadius = Self.slAuraRadius(1)
        slGlow.circleOpacity = NSExpression(forConstantValue: 0.45)
        slGlow.circleBlur = NSExpression(forConstantValue: 1.0)
        slGlow.predicate = NSPredicate(format: "slAura != ''")
        style.addLayer(slGlow)
        let slPulse = MLNCircleStyleLayer(identifier: StyleFx.slPulseLayerID, source: auraSource)
        slPulse.circleOpacity = NSExpression(forConstantValue: 0)
        slPulse.circleRadius = Self.slAuraRadius(0.8)
        slPulse.circleStrokeColor = NSExpression(mglJSONObject: ["to-color", ["get", "slAuraColor2"]] as [Any])
        slPulse.circleStrokeWidth = NSExpression(forConstantValue: 1.6)
        slPulse.circleStrokeOpacity = NSExpression(forConstantValue: 0.7)
        slPulse.predicate = NSPredicate(format: "slAura == %@", "pulse")
        style.addLayer(slPulse)
        // 스프라이트 — 별 마커(40pt × sizeMult × 줌 배율)의 2.4배 = 96pt 로 둘레를 감싼다(Android 별 160px : 스프라이트 384px).
        let slSprite = MLNSymbolStyleLayer(identifier: StyleFx.slSpriteLayerID, source: auraSource)
        slSprite.iconImageName = NSExpression(mglJSONObject: ["get", "slSprite"] as [Any])
        slSprite.iconScale = Self.slSpriteScale()
        slSprite.iconAllowsOverlap = NSExpression(forConstantValue: true)
        slSprite.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        slSprite.predicate = NSPredicate(format: "slSprite != ''")
        style.addLayer(slSprite)

        // ── 내 위치 점(초록 원) — Android 는 이걸 맨 위 레이어로 그린다. 줌을 풀수록 작아진다(StyleFx.myLocationStops).
        //    MapLibre 기본 퍽(파란 점)은 끄고(`showsUserLocation = false`) 이 레이어로 대체했다.
        let meSource = MLNShapeSource(identifier: StyleFx.myLocationSourceID, features: [], options: nil)
        style.addSource(meSource)
        func stopsExpr(_ value: ((zoom: Double, radius: Double, stroke: Double)) -> Double) -> NSExpression {
            var json: [Any] = ["interpolate", ["linear"], ["zoom"]]
            for st in StyleFx.myLocationStops {
                json.append(st.zoom)
                json.append(value(st))
            }
            return NSExpression(mglJSONObject: json)
        }
        let me = MLNCircleStyleLayer(identifier: StyleFx.myLocationLayerID, source: meSource)
        me.circleRadius = stopsExpr { $0.radius }
        me.circleColor = NSExpression(forConstantValue: StyleFx.mint)
        me.circleStrokeWidth = stopsExpr { $0.stroke }
        me.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        style.addLayer(me)
        myLocationSource = meSource
        updateMyLocation(parent.userLocation)

        refreshAuraFeatures(mapView)
        applyFilterHighlight(mapView, force: true) // 스타일이 (다시) 로드되면 레이어가 평소 값으로 새로 만들어졌다 → 현재 필터 상태에 맞춘다
        reportWorldVoid(mapView)
        startTwinkleLoop()
    }

    /// 필터가 걸려 있으면(`parent.filterHighlight`) 별 바닥 빛을 조금 더 밝게(×배율). 오오라는 건드리지 않는다(2026-10-19).
    /// Android `DiaryMap` 의 `highlight` 패리티. 값이 그대로면 아무것도 안 한다(updateUIView 가 자주 불린다).
    /// 스타일이 아직 안 올라왔으면(레이어 없음) 조용히 건너뛰고, 로드 완료 때 `force` 로 다시 맞춘다.
    func applyFilterHighlight(_ mapView: MLNMapView, force: Bool = false) {
        let on = parent.filterHighlight
        guard force || on != lastHighlight else { return }
        guard let style = mapView.style,
              let ground = style.layer(withIdentifier: StyleFx.groundLightLayerID) as? MLNCircleStyleLayer
        else { return }
        lastHighlight = on
        ground.circleOpacity = NSExpression(
            forConstantValue: StyleFx.groundLightOpacity * (on ? StyleFx.highlightGroundBoost : 1))
    }

    /// 내 위치 점 좌표 갱신 — 같은 좌표면 건너뛴다(updateUIView 가 자주 불린다). 스타일 로드 전이면 로드 때 `parent.userLocation` 으로 채운다.
    func updateMyLocation(_ coord: CLLocationCoordinate2D?) {
        guard let source = myLocationSource else { return }
        if let a = coord, let b = lastMyLocation, a.latitude == b.latitude, a.longitude == b.longitude { return }
        if coord == nil, lastMyLocation == nil { return }
        lastMyLocation = coord
        if let c = coord {
            let feature = MLNPointFeature()
            feature.coordinate = c
            source.shape = feature
        } else {
            source.shape = nil
        }
    }

    /// 뷰 해체 시 타이머/태스크 정리(순환 참조·유령 갱신 방지).
    func teardownStyleEffects() {
        twinkleTimer?.invalidate()
        twinkleTimer = nil
        constellationFadeTask?.cancel()
        constellationLink?.invalidate()
        constellationLink = nil
        revealLink?.invalidate()
        revealLink = nil
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

    /// 공방 오라 반경 — 인기 오오라와 같은 줌 곡선 × scale × sizeMult(Android slAuraRadiusExpression 패리티).
    static func slAuraRadius(_ scale: Double) -> NSExpression {
        var json: [Any] = ["interpolate", ["linear"], ["zoom"]]
        for (zoom, base) in [(6.0, 2.5), (10.0, 7.0), (13.0, 15.0), (15.0, 24.0)] {
            json.append(zoom)
            json.append(["*", base * scale, ["get", "sizeMult"] as [Any]] as [Any])
        }
        return NSExpression(mglJSONObject: json)
    }

    /// 공방 오라 스프라이트 배율 — 별 마커와 같은 줌 곡선(starScale) × sizeMult. 스프라이트 그림은 96pt(384px @4x).
    static func slSpriteScale() -> NSExpression {
        var json: [Any] = ["interpolate", ["linear"], ["zoom"]]
        for (zoom, s) in [(6.0, 0.06), (10.0, 0.2), (13.0, 0.5), (15.0, 1.0)] {
            json.append(zoom)
            json.append(["*", s, ["get", "sizeMult"] as [Any]] as [Any])
        }
        return NSExpression(mglJSONObject: json)
    }

    /// 스프라이트 이미지 이름 — 아이템과 그림(payload)이 같으면 같은 이름(Android slAuraSpriteImageId 패리티).
    static func slSpriteName(_ item: StarlightStore.Item) -> String {
        "sl-aura-img-\(item.id)-\(StarlightArt.javaHash(StarlightAura.key(item.payload)))"
    }

    /// 그 사람이 단 오라. 안 달았거나 아이템을 모르면 nil. (Android slAuraOf 패리티)
    @MainActor
    static func slAuraOf(_ userId: String) -> SlAura? {
        let store = StarlightStore.shared
        guard let id = store.auraByUser[userId], let item = store.item(id) else { return nil }
        let raw = item.str("pattern") ?? "halo"
        let hasSprite = StarlightAura.spritePatterns.contains(raw)
        let pattern = StyleFx.slPatterns.contains(raw) ? raw : "halo"
        let hexes = item.hexColors
        let c1 = hexes.first ?? "#BFD8FF"
        let m = StarlightAura.motion(item.payload)
        return SlAura(pattern: pattern, color: c1, color2: hexes.count > 1 ? hexes[1] : c1,
                      sprite: hasSprite ? slSpriteName(item) : "",
                      spin: m.spinDegPerSec, wobble: m.wobbleDeg, flicker: m.flicker ? 1 : 0)
    }

    /// 공방 오라 구성이 바뀌었으면(누가 달았나 · 아이템 목록) 후광 소스를 다시 만든다 — updateUIView 에서 매번 부른다.
    @MainActor
    func refreshStarlightAuraIfNeeded(_ mapView: MLNMapView) {
        let store = StarlightStore.shared
        let key = store.auraByUser.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
            + "|\(store.items.count)"
        guard key != lastSlAuraKey else { return }
        lastSlAuraKey = key
        // 후광 소스 갱신(비격리)에서 쓰도록 사람별 오라를 미리 풀어 둔다.
        var byUser: [String: SlAura] = [:]
        for uid in store.auraByUser.keys { if let sl = Self.slAuraOf(uid) { byUser[uid] = sl } }
        slAuraByUser = byUser
        // 지금 누군가 달고 있는 오라의 스프라이트만 등록(한 번 등록하면 스타일이 바뀔 때까지 유지).
        if let style = styleRef {
            for id in Set(store.auraByUser.values) {
                guard let item = store.item(id) else { continue }
                let name = Self.slSpriteName(item)
                guard style.image(forName: name) == nil,
                      let cg = StarlightAura.sprite(item.payload)?.cgImage else { continue }
                style.setImage(UIImage(cgImage: cg, scale: 4, orientation: .up), forName: name)
            }
        }
        refreshAuraFeatures(mapView)
    }

    /// 후광 소스 갱신 — 현재 대표 별(머지 결과) 좌표 + sizeMult + 별색. 마커 갱신 시 함께 호출.
    func refreshAuraFeatures(_ mapView: MLNMapView) {
        guard let style = styleRef,
              let src = style.source(withIdentifier: StyleFx.auraSourceID) as? MLNShapeSource
        else { return }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let features: [MLNPointFeature] = (mapView.annotations ?? [])
            .compactMap { $0 as? DiaryAnnotation }
            // 필터 전환 순차 등장 중 아직 떠오르지 않은 별은 후광도 뺀다(MapStarReveal).
            .filter { !revealHidden.contains($0.diary.id ?? "") }
            .map { a in
                let f = MLNPointFeature()
                f.coordinate = a.coordinate
                let fresh = nowMs - a.diary.createdAt <= StyleFx.freshWindowMs
                let sl = self.slAuraByUser[a.diary.userId]
                f.attributes = [
                    "sizeMult": max(a.sizeMult, 1.0),
                    "auraColor": Self.starColorHex(a.diary.starColor),
                    "fresh": fresh ? 1.0 : 0.0,
                    "slAura": sl?.pattern ?? "",
                    "slAuraColor": sl?.color ?? "#000000",
                    "slAuraColor2": sl?.color2 ?? "#000000",
                    "slSprite": sl?.sprite ?? "",
                    "slSpin": sl?.spin ?? 0,
                    "slWobble": sl?.wobble ?? 0,
                    "slFlicker": sl?.flicker ?? 0,
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
            // 별빛 공방 오라 — 빛은 숨 쉬고, pulse 고리는 퍼지며 사라지고, ring 고리는 천천히 커졌다 작아진다.
            // (오라를 단 사람이 없으면 건너뛴다 — 레이어는 비어 있다.)
            if !self.lastSlAuraKey.hasPrefix("|") { self.animateStarlightAura(style) }
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

    /// 공방 오라 한 틱(Android 애니메이션 루프의 sl-aura 부분 패리티). 반경 표현식은 1/32 단위로 캐시한다.
    private func animateStarlightAura(_ style: MLNStyle) {
        let t = twinkleT
        let breathe = (sin(t * 1.2) + 1) / 2              // 0..1
        let spread = (t * 0.24).truncatingRemainder(dividingBy: 1) // 0..1 퍼짐(약 4.2초 — 예전 2.2초는 "두근거림"이 정신없었다)
        func radius(_ scale: Double) -> NSExpression {
            let q = Int(scale * 32)
            if let e = slRadiusCache[q] { return e }
            let e = Self.slAuraRadius(Double(q) / 32)
            slRadiusCache[q] = e
            return e
        }
        if let glow = style.layer(withIdentifier: StyleFx.slGlowLayerID) as? MLNCircleStyleLayer {
            glow.circleOpacity = NSExpression(forConstantValue: 0.32 + 0.26 * breathe)
        }
        if let pulse = style.layer(withIdentifier: StyleFx.slPulseLayerID) as? MLNCircleStyleLayer {
            pulse.circleRadius = radius(0.45 + 0.85 * spread)
            pulse.circleStrokeOpacity = NSExpression(forConstantValue: 0.85 * (1 - spread))
        }
        // 스프라이트 — 회전 = 피처별 slSpin(°/초) × 시간 + slWobble(°) × 흔들림, 반딧불은 밝기가 일렁인다.
        // 시간은 한 시간마다 되감아 값이 커지지 않게(Android 동일).
        if let sprite = style.layer(withIdentifier: StyleFx.slSpriteLayerID) as? MLNSymbolStyleLayer {
            let tt = t.truncatingRemainder(dividingBy: 3600)
            let wob = sin(tt * (2 * .pi / 14))
            let flick = 0.3 * (0.5 + 0.5 * sin(tt * 2.3))
            sprite.iconRotation = NSExpression(mglJSONObject:
                ["+", ["*", ["get", "slSpin"] as [Any], tt] as [Any], ["*", ["get", "slWobble"] as [Any], wob] as [Any]] as [Any])
            sprite.iconOpacity = NSExpression(mglJSONObject:
                ["-", 1, ["*", ["get", "slFlicker"] as [Any], flick] as [Any]] as [Any])
        }
    }

    // MARK: 별자리 라인

    /// 별자리 토글 — 켜면 선 긋기(rebuildConstellation → startConstellationDraw),
    /// 끄면 페이드 아웃(420ms) 후 선을 비운다. (Android constellationFade + 끄기 effect 대응.)
    func setConstellation(enabled: Bool, mapView: MLNMapView) {
        guard enabled != constellationOn else { return }
        constellationOn = enabled
        if enabled {
            constellationFadeTask?.cancel()
            rebuildConstellation(mapView)
            return
        }
        lastConstellationKey = nil
        constellationRebuildTask?.cancel()
        stopConstellationDraw() // 머리 빛점/플래시는 즉시 정리, 선은 페이드로
        constellationFadeTask?.cancel()
        constellationFadeTask = Task { @MainActor [weak self] in
            let steps = 14
            let start = self?.constellationFadeValue ?? 1
            for s in 1...steps {
                guard let self, !Task.isCancelled else { return }
                self.applyConstellationOpacity(start * (1 - Double(s) / Double(steps)))
                try? await Task.sleep(nanoseconds: UInt64(0.42 / Double(steps) * 1_000_000_000))
            }
            guard let self, !Task.isCancelled, let style = self.styleRef,
                  let src = style.source(withIdentifier: StyleFx.constellationSourceID) as? MLNShapeSource
            else { return }
            src.shape = MLNShapeCollectionFeature(shapes: [])
            self.constellationDrawn = [:]
            self.constellationGeometry = [:]
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

    /// 뷰포트에 보이는 대표 별들을 화면거리 최근접 2개와 잇는 선 목록을 만들고, 바뀌었으면 선 긋기를 시작한다.
    /// (Android buildConstellationEdges 대응 — maxLink = min(화면 변)×0.55, 키는 id 정렬.)
    private func rebuildConstellation(_ mapView: MLNMapView) {
        guard constellationOn, styleRef != nil else { return }
        // 켜진 동안 레이어는 항상 최대 불투명도 — 끄기 페이드 도중 다시 켠 경우도 여기서 복구.
        if constellationFadeValue < 1 { applyConstellationOpacity(1) }
        let w = mapView.bounds.width
        let h = mapView.bounds.height
        guard w > 1, h > 1 else { return }
        var pts: [(id: String, screen: CGPoint, coord: CLLocationCoordinate2D)] = []
        for a in (mapView.annotations ?? []).compactMap({ $0 as? DiaryAnnotation }) {
            let p = mapView.convert(a.coordinate, toPointTo: mapView)
            if p.x >= 0, p.x <= w, p.y >= 0, p.y <= h { pts.append((a.diary.id ?? "", p, a.coordinate)) }
        }
        let maxLink = Double(min(w, h)) * 0.55
        let maxLink2 = maxLink * maxLink
        var seen = Set<Int>() // i<j 를 i*N+j 로 인코딩해 중복 제거(Android 동일)
        var edges: [ConstellationEdgeIOS] = []
        if pts.count >= 2 {
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
                    guard seen.insert(min(i, j) * pts.count + max(i, j)).inserted else { continue }
                    // 키가 id 정렬 기준이라 a/b 도 id 순서로 고정(재계산해도 같은 선은 같은 a/b).
                    let (lo, hi) = pts[i].id < pts[j].id ? (i, j) : (j, i)
                    edges.append(ConstellationEdgeIOS(
                        key: pts[lo].id + "|" + pts[hi].id,
                        aId: pts[lo].id, a: pts[lo].coord, ap: pts[lo].screen,
                        bId: pts[hi].id, b: pts[hi].coord, bp: pts[hi].screen
                    ))
                }
            }
        }
        let newKeys = Set(edges.map { $0.key })
        let key = newKeys.sorted().joined(separator: ";")
        // 선 구성이 그대로면: 긋는 중이면 그대로 두고(SwiftUI 갱신마다 재시작 금지), 다 그려졌으면 할 일 없음.
        if key == lastConstellationKey {
            if constellationRun != nil { return }
            if constellationDrawn.values.allSatisfy({ $0.p >= 1 }) { return }
        }
        lastConstellationKey = key
        startConstellationDraw(edges: edges, mapView: mapView)
    }
}

/// 별빛 공방 오라 한 사람분(Android `SlAura` 패리티) — 지도 피처 속성으로 풀어 넣는다.
struct SlAura {
    /// 원 레이어 패턴(halo · pulse) — 스프라이트 패턴은 halo(빛) + 스프라이트.
    let pattern: String
    let color: String
    let color2: String
    /// 스프라이트 이미지 이름(없으면 "").
    let sprite: String
    let spin: Double
    let wobble: Double
    let flicker: Double
}
