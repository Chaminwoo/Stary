import CoreLocation
import MapLibre
import SwiftUI
import UIKit

/// 다이어리 좌표를 MapLibre 지도 위 별 마커로 표시한다.
/// Android `DiaryMap` 대응의 iOS 구현 시작점.
struct MapLibreView: UIViewRepresentable {
    let diaries: [Diary]
    /// 실제 위치 fix(없으면 nil) — 처음 들어오면 그 위치로 1회 카메라 이동.
    let userLocation: CLLocationCoordinate2D?
    /// 별 마커 탭 — 30m 안에서 합쳐진 멤버 전체(우선순위 정렬) + 탭한 별의 화면상 위치(0..1) +
    /// 지도 스냅샷을 넘긴다. 100m 게이팅/파장(warp) 연출/상세 진입은 호출부(MapScreen)의 책임.
    /// (Android DiaryMap 마커 클릭 → snapshot + DiaryOpenWarpData 흐름 대응)
    var onTapStar: (_ members: [Diary], _ originUnit: CGPoint, _ snapshot: UIImage?) -> Void
    /// 도보 길찾기 경로(비었으면 표시 안 함). (Android DiaryMap ROUTE_LAYER 패리티)
    var route: [CLLocationCoordinate2D] = []
    /// 외부(알림/친구 별 탭)에서 "이 좌표로 카메라 이동" 요청. 값이 바뀔 때 1회 애니메이션 이동.
    /// (Android MapFocusState → DiaryMap focusDiary 카메라 이동 패리티. iOS 는 파동 연출 없이 카메라만.)
    var focusTarget: CLLocationCoordinate2D?
    /// 줌이 [globeButtonZoom] 이하면 (중심 위경도, true), 위로 올라오면 (_, _, false) 로 보고.
    /// 호출부는 이 값으로 하단 "지구 보기" 버튼을 노출/숨김(자동 전환 없음 — 버튼으로만 진입).
    var onGlobeAvailability: ((_ lat: Double, _ lng: Double, _ available: Bool) -> Void)? = nil
    /// 글로브 → 지도 복귀 카메라 요청(nonce 로 같은 좌표 반복 요청도 트리거).
    var globeReturnCamera: GlobeReturnCamera? = nil
    /// 개척 퀘스트 미개척 대상국(체크리스트 32) — 중심좌표에 금색 스파클 비콘 표시.
    var pioneerCountries: [PioneerQuest.Country] = []
    /// 개척 비콘 탭 → 국가 코드 전달(호출부가 퀘스트 안내 표시).
    var onTapPioneer: ((String) -> Void)? = nil
    /// 줌 버튼(+/−) 요청 — nonce 가 바뀔 때 delta 만큼 애니메이션 줌. (Android zoomBy(±1) 대응)
    var zoomRequest: (delta: Double, nonce: Int) = (0, 0)
    /// "내 위치로" 요청 — nonce 가 바뀔 때 현재 위치로 줌 15 이동. (Android recenterToMyLocation 대응)
    var recenterNonce: Int = 0
    /// 별자리 라인 토글(Android constellationEnabled 대응) — 켜면 뷰포트 별들을 잇는 3겹 라인 페이드 인.
    var constellationEnabled: Bool = false

    /// 3D 글로브 "지구 보기" 버튼 노출 줌 / 지도 최소 줌.
    /// (Android DiaryMap GLOBE_BUTTON_ZOOM/MAP_MIN_ZOOM 패리티)
    static let globeButtonZoom = 3.0
    static let mapMinZoom = 2.4
    /// 지도 기본 기울기(도) — 살짝 눕혀 입체감. (Android BASE_TILT_DEG=25 패리티)
    static let baseTiltDeg: CGFloat = 25

    /// 야경 커스텀 스타일 URL — 번들 `maplibre_style.json` 의 `__MAPTILER_KEY__` 를 Info.plist 의
    /// `MAPTILER_KEY`(빌드 설정 주입, 하드코딩 금지)로 치환해 임시 파일로 저장한 뒤 그 URL 을 쓴다.
    /// 키가 비어 있으면 nil(호출부가 데모 스타일 폴백). Android `DiaryMap` 의 스타일 로드와 동일 계약.
    static let staryStyleURL: URL? = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "MAPTILER_KEY") as? String,
              !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let srcURL = Bundle.main.url(forResource: "maplibre_style", withExtension: "json"),
              let raw = try? String(contentsOf: srcURL, encoding: .utf8) else { return nil }
        let json = raw.replacingOccurrences(of: "__MAPTILER_KEY__",
                                            with: key.trimmingCharacters(in: .whitespacesAndNewlines))
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("stary_style.json")
        guard (try? json.write(to: dest, atomically: true, encoding: .utf8)) != nil else { return nil }
        return dest
    }()

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)
        // 야경 커스텀 스타일 — Android `res/raw/maplibre_style.json` 과 같은 파일을 번들에서 로드해
        // `__MAPTILER_KEY__` 를 주입한다. 키 미설정/로드 실패 시 데모 스타일로 폴백(지도는 항상 뜬다).
        mapView.styleURL = Self.staryStyleURL
            ?? URL(string: "https://demotiles.maplibre.org/style.json")
        mapView.delegate = context.coordinator
        // 초기 카메라 = 지난 세션에서 마지막으로 보던 카메라(중심+줌) → 없으면
        // "지난 세션 마지막 위치"(없으면 기본 좌표=건국대). 실제 fix 가 들어오면 재센터.
        // (Android DiaryMap lastCameraState 복원과 동일)
        if let cam = LocationManager.lastCameraState, userLocation == nil {
            mapView.setCenter(cam.coordinate, zoomLevel: cam.zoom, animated: false)
        } else {
            let fallback = LocationManager.lastSavedCoordinate
                ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
            mapView.setCenter(userLocation ?? fallback, zoomLevel: 15, animated: false)
        }
        // 살짝 기울인 카메라(입체감) — Android BASE_TILT_DEG(25°) 패리티.
        let camera = mapView.camera
        camera.pitch = Self.baseTiltDeg
        mapView.setCamera(camera, animated: false)
        // 회전/기울기 제스처 잠금 — 기본 틸트를 사용자 조작이 흐트러뜨리지 않게(Android uiSettings 동일).
        mapView.allowsRotating = false
        mapView.allowsTilting = false
        mapView.showsUserLocation = true
        mapView.minimumZoomLevel = Self.mapMinZoom // 이 밑은 3D 글로브가 담당
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.parent = self
        // 최초 진입 시 실제 위치 fix 가 들어오면 그 위치로 1회만 부드럽게 이동(Android didAutoCenter 패리티).
        if !context.coordinator.didAutoCenter, let me = userLocation {
            context.coordinator.didAutoCenter = true
            mapView.setCenter(me, zoomLevel: 15, animated: true)
        }
        // 포커스 요청(친구 별/알림) — 대상 좌표가 바뀌면 그 위치로 1회 카메라 이동.
        if let target = focusTarget, !context.coordinator.sameAsLastFocus(target) {
            context.coordinator.lastFocus = target
            mapView.setCenter(target, zoomLevel: 15, animated: true)
        }
        // 글로브 → 지도 복귀 시 "내 위치로" 이동을 1회 실행(체크리스트 27, Android recenterToMyLocation 패리티).
        // 실제 위치 fix 가 없으면 글로브에서 보던 좌표로 폴백. 줌은 Android DEFAULT_ZOOM(15) 과 일치.
        if let req = globeReturnCamera, req.nonce != context.coordinator.lastGlobeReturnNonce {
            context.coordinator.lastGlobeReturnNonce = req.nonce
            let target = userLocation ?? CLLocationCoordinate2D(latitude: req.lat, longitude: req.lng)
            mapView.setCenter(target, zoomLevel: 15, animated: true)
        }
        // 줌 +/− 버튼 (Android 좌상단 줌 버튼 대응).
        if zoomRequest.nonce != context.coordinator.lastZoomNonce {
            context.coordinator.lastZoomNonce = zoomRequest.nonce
            mapView.setZoomLevel(mapView.zoomLevel + zoomRequest.delta, animated: true)
        }
        // "내 위치로" 버튼 — 실제 fix 가 있을 때만(Android recenterToMyLocation 과 동일 게이팅).
        if recenterNonce != context.coordinator.lastRecenterNonce {
            context.coordinator.lastRecenterNonce = recenterNonce
            if let me = userLocation {
                mapView.setCenter(me, zoomLevel: 15, animated: true)
            }
        }
        if let existing = mapView.annotations { mapView.removeAnnotations(existing) }
        // 30m 지오 머지 — 겹치는 별은 대표 하나로 합치고, 크기는 멤버 합산으로 키운다.
        // (Android DiaryMap.mergeByProximity 패리티. 탭하면 멤버가 2개 이상일 때 카드 뷰어로.)
        var toAdd: [MLNAnnotation] = StarMerge.merge(diaries).map { DiaryAnnotation(merged: $0) }
        // 개척 퀘스트 비콘(체크리스트 32) — 미개척 대상국 중심좌표에 금색 스파클.
        toAdd.append(contentsOf: pioneerCountries.map { PioneerAnnotation(country: $0) })
        // 도보 경로 폴리라인(있으면) — 별 마커와 함께 한 번에 추가.
        if route.count >= 2 {
            toAdd.append(MLNPolyline(coordinates: route, count: UInt(route.count)))
        }
        mapView.addAnnotations(toAdd)
        // 스타일 이펙트 동기화 — 별 후광 소스 갱신 + 별자리 토글/재계산(MapStyleEffects.swift).
        context.coordinator.refreshAuraFeatures(mapView)
        context.coordinator.setConstellation(enabled: constellationEnabled, mapView: mapView)
        context.coordinator.requestConstellationRebuild(mapView)
    }

    /// 뷰 해체 — 트윙클 타이머/페이드 태스크 정리(유령 갱신 방지).
    static func dismantleUIView(_ uiView: MLNMapView, coordinator: Coordinator) {
        coordinator.teardownStyleEffects()
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
        var didAutoCenter = false
        /// 마지막으로 카메라를 옮긴 포커스 좌표(중복 이동 방지).
        var lastFocus: CLLocationCoordinate2D?
        /// 마지막으로 처리한 글로브 복귀 요청 nonce.
        var lastGlobeReturnNonce: Int = -1
        /// 마지막으로 처리한 줌 버튼/내 위치 버튼 요청 nonce.
        var lastZoomNonce: Int = 0
        var lastRecenterNonce: Int = 0
        /// 현재 적용 중인 줌 기반 별 크기 배율(Android iconSize 줌 보간 대응).
        var starZoomScale: CGFloat = 1

        // ── 스타일 이펙트(별가루/별자리/후광) 상태 — 로직은 MapStyleEffects.swift 확장에 ──
        weak var styleRef: MLNStyle?
        weak var mapRef: MLNMapView?
        var twinkleTimer: Timer?
        var twinkleT: Double = 0
        /// 카메라 이동 중엔 스타일 갱신을 멈춰 팬/줌 끊김을 방지(Android isCameraMoving 대응).
        var isCameraMoving = false
        var constellationOn = false
        var constellationFadeTask: Task<Void, Never>?
        var constellationRebuildTask: Task<Void, Never>?
        /// 마지막으로 파티클/후광 크기를 갱신한 줌(불필요한 expression 재설정 방지).
        var lastEffectZoom: Double = -1
        /// 파티클 저줌 게이트가 0(전부 숨김)으로 이미 적용됐는지 — 꺼진 동안 루프 갱신 스킵.
        var lastGateZero = false

        init(_ parent: MapLibreView) { self.parent = parent }

        /// 줌 상태 보고 → 호출부가 하단 "지구 보기" 버튼 노출을 결정(자동 전환 없음).
        private func reportGlobeAvailability(_ mapView: MLNMapView) {
            guard let cb = parent.onGlobeAvailability else { return }
            let c = mapView.centerCoordinate
            cb(c.latitude, c.longitude, mapView.zoomLevel <= MapLibreView.globeButtonZoom)
        }

        /// 줌 레벨 → 별 크기 배율. Android SymbolLayer iconSize 줌 보간과 동일 스톱:
        /// 8 → 0.3x, 12 → 0.55x, 15 → 1.0x (사이 구간 선형).
        static func starScale(forZoom zoom: Double) -> CGFloat {
            let s: Double
            switch zoom {
            case ..<8: s = 0.3
            case ..<12: s = 0.3 + (zoom - 8) / 4 * 0.25
            case ..<15: s = 0.55 + (zoom - 12) / 3 * 0.45
            default: s = 1.0
            }
            return CGFloat(s)
        }

        /// 현재 줌에 맞춰 모든 별 어노테이션 뷰의 스케일을 갱신 — 줌아웃 시 자연스럽게 작아진다(#2).
        func applyStarZoomScale(_ mapView: MLNMapView) {
            let s = Self.starScale(forZoom: mapView.zoomLevel)
            guard abs(s - starZoomScale) > 0.015 else { return }
            starZoomScale = s
            for annotation in mapView.annotations ?? [] {
                guard annotation is DiaryAnnotation,
                      let v = mapView.view(for: annotation) else { continue }
                v.transform = CGAffineTransform(scaleX: s, y: s)
            }
        }

        func mapViewRegionIsChanging(_ mapView: MLNMapView) {
            isCameraMoving = true
            reportGlobeAvailability(mapView)
            applyStarZoomScale(mapView)
            applyStyleEffectZoom(mapView)
        }

        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            isCameraMoving = false
            reportGlobeAvailability(mapView)
            applyStarZoomScale(mapView)
            applyStyleEffectZoom(mapView)
            // 카메라 idle → 별자리 라인 재계산(90ms 디바운스, Android 동일).
            requestConstellationRebuild(mapView)
            // 마지막 카메라(중심+줌) 저장 — 다음 실행의 초기 카메라가 "마지막으로 보던 곳"이 되게.
            LocationManager.persistCameraState(mapView.centerCoordinate, zoom: mapView.zoomLevel)
        }

        /// 직전 포커스와 (거의) 같은 좌표인지 — 같은 별 재요청 시 카메라를 다시 옮기지 않게.
        func sameAsLastFocus(_ c: CLLocationCoordinate2D) -> Bool {
            guard let last = lastFocus else { return false }
            return abs(last.latitude - c.latitude) < 1e-7 && abs(last.longitude - c.longitude) < 1e-7
        }

        /// 모든 별을 **뷰 어노테이션**으로 그린다 — `scalesWithViewingDistance` 로 줌아웃 시 별이
        /// 자연스럽게 작아진다(#1, Android SymbolLayer iconSize 줌 보간 대응).
        /// 겹친 별은 위성 부유 뷰, 단일 별은 단순 이미지 뷰. 비콘(PioneerAnnotation)은 nil→imageFor.
        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            guard let d = annotation as? DiaryAnnotation else { return nil }
            // 생성/재사용 시점에도 현재 줌 배율을 적용(#2 — 줌아웃 상태에서 추가되는 별도 작게).
            let scale = CGAffineTransform(scaleX: starZoomScale, y: starZoomScale)
            if d.members.count > 1 {
                let v = MergedStarAnnotationView(annotation: d)
                v.transform = scale
                return v
            }
            let id = "single-\(d.imageKey)"
            if let reused = mapView.dequeueReusableAnnotationView(withIdentifier: id) {
                reused.transform = scale
                return reused
            }
            let v = SingleStarAnnotationView(annotation: d, reuseIdentifier: id)
            v.transform = scale
            return v
        }

        func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
            // 개척 비콘 — 금색 스파클(8꼭지, 앰버골드 = Android starBitmap(3, 15) 패리티).
            if annotation is PioneerAnnotation {
                let key = "pioneer-beacon"
                if let cached = mapView.dequeueReusableAnnotationImage(withIdentifier: key) { return cached }
                let img = StarImageRenderer.image(type: 3, colorIndex: 15)
                return MLNAnnotationImage(image: img, reuseIdentifier: key)
            }
            guard let d = annotation as? DiaryAnnotation else { return nil }
            // 합쳐진 별일수록 큰 이미지(크기는 0.25 단위 양자화 → 이미지 재사용).
            let key = d.imageKey
            if let cached = mapView.dequeueReusableAnnotationImage(withIdentifier: key) { return cached }
            let img = StarImageRenderer.image(
                type: d.diary.starType, colorIndex: d.diary.starColor, size: d.markerSize
            )
            return MLNAnnotationImage(image: img, reuseIdentifier: key)
        }

        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            if let d = annotation as? DiaryAnnotation {
                // 파장 중심 = 이 별의 화면상 위치(0..1) — Android toScreenLocation 대응.
                let sp = mapView.convert(d.coordinate, toPointTo: mapView)
                let w = max(mapView.bounds.width, 1)
                let h = max(mapView.bounds.height, 1)
                let origin = CGPoint(x: min(max(sp.x / w, 0), 1), y: min(max(sp.y / h, 0), 1))
                // 지도 스냅샷 — 파장(warp) 연출의 배경(Android map.snapshot 대응).
                // Metal 레이어 캡처가 실패해도(빈 이미지) 링/버스트 연출은 그대로 재생된다.
                let snapshot = UIGraphicsImageRenderer(bounds: mapView.bounds).image { _ in
                    mapView.drawHierarchy(in: mapView.bounds, afterScreenUpdates: false)
                }
                parent.onTapStar(d.members, origin, snapshot)
            }
            if let p = annotation as? PioneerAnnotation { parent.onTapPioneer?(p.country.code) }
            mapView.deselectAnnotation(annotation, animated: false)
        }

        // 경로 폴리라인 스타일 — 연한 초록 실선(Android ROUTE_LAYER #86EFAC 와 동일).
        func mapView(_ mapView: MLNMapView, strokeColorForShapeAnnotation annotation: MLNShape) -> UIColor {
            UIColor(red: 0.525, green: 0.937, blue: 0.675, alpha: 1) // #86EFAC
        }
        func mapView(_ mapView: MLNMapView, lineWidthForPolylineAnnotation annotation: MLNPolyline) -> CGFloat { 5 }
        func mapView(_ mapView: MLNMapView, alphaForShapeAnnotation annotation: MLNShape) -> CGFloat { 0.95 }
    }
}

/// 글로브에서 지도로 복귀할 때의 카메라 요청(nonce 로 같은 좌표 반복 요청도 트리거).
/// (Android DiaryMap GlobeReturnCamera 패리티)
struct GlobeReturnCamera: Equatable {
    let lat: Double
    let lng: Double
    let zoom: Double
    let nonce: Int
}

/// 개척 퀘스트 대상국 비콘 어노테이션(체크리스트 32).
final class PioneerAnnotation: NSObject, MLNAnnotation {
    let country: PioneerQuest.Country
    var coordinate: CLLocationCoordinate2D
    var title: String?

    init(country: PioneerQuest.Country) {
        self.country = country
        self.coordinate = CLLocationCoordinate2D(latitude: country.lat, longitude: country.lng)
        self.title = nil
    }
}

/// 겹친 별(머지) 마커 뷰 — 대표 별을 중심에 두고, 나머지 멤버 별 미니어처(각자 자기 모양/색)가
/// 대표 곁 고정 자리(타원 배치, y×0.55)에서 저마다의 결로 잔잔히 둥둥 떠다닌다.
/// 컨테이너 전체(대표+위성)가 같은 상하 float 를 공유해 **대표 별과 함께** 오르내리고,
/// 그 위에 위성마다 작은 독립 흔들림을 더한다. 회전(공전) 없음. (Android DiaryMap 위성 부유 패리티)
final class MergedStarAnnotationView: MLNAnnotationView {
    private static let maxOrbitStars = 4
    /// 위성 고정 배치각(rad) — Android ORBIT_ANCHOR_ANGLES 와 동일(비대칭 배치).
    private static let anchorAngles: [CGFloat] = [-0.6, 2.3, 4.1, 1.1]
    /// 위성별 x/y 부유 주기(초) — 서로 어긋난 주기라 기계적으로 동기화되지 않는다.
    private static let driftPeriodsX: [Double] = [4.6, 5.7, 3.9, 5.2]
    private static let driftPeriodsY: [Double] = [3.3, 2.9, 3.8, 3.1]
    private static let satSize: CGFloat = 16
    /// 부유 진폭(pt) — 자리를 벗어나 보이지 않게 작게(대표 별 float 와 비슷한 스케일).
    private static let driftAmpX: CGFloat = 0.8
    private static let driftAmpY: CGFloat = 1.0
    /// 컨테이너(대표+위성 공유) float — Android floatDy(wave×4dp, 주기 2π/1.6s)와 동일한 결.
    private static let floatAmp: CGFloat = 4.0
    private static let floatPeriod: Double = 2 * .pi / 1.6
    private static let floatPhaseGroups = 4

    private var satellites: [(view: UIImageView, index: Int)] = []
    private let repId: String

    /// 위성 배치 반경(pt) — 대표 별 바로 곁(가장자리와 겹치도록)에 붙이고, 인덱스마다 아주 조금씩만 바깥으로.
    private static func radius(markerSize: CGFloat, satIndex: Int) -> CGFloat {
        markerSize * 0.12 + 1 + CGFloat(satIndex) * 1.6
    }

    init(annotation: DiaryAnnotation) {
        let markerSize = annotation.markerSize
        let members = Array(annotation.members.dropFirst().prefix(Self.maxOrbitStars))
        let maxRadius = Self.radius(markerSize: markerSize, satIndex: max(members.count - 1, 0))
        let side = (maxRadius + Self.satSize / 2 + Self.driftAmpY + Self.floatAmp) * 2
        self.repId = annotation.diary.id ?? ""
        super.init(reuseIdentifier: nil)
        frame = CGRect(x: 0, y: 0, width: side, height: side)
        backgroundColor = .clear
        scalesWithViewingDistance = true   // 줌아웃 시 별 무리도 작아지게(#1)

        // 위성 먼저 추가 → 대표 별이 항상 위(Android 레이어 순서와 동일).
        for (i, m) in members.enumerated() {
            let iv = UIImageView(image: StarImageRenderer.image(
                type: m.starType, colorIndex: m.starColor, size: Self.satSize
            ))
            iv.bounds = CGRect(x: 0, y: 0, width: Self.satSize, height: Self.satSize)
            let ang = Self.anchorAngles[i]
            let r = Self.radius(markerSize: markerSize, satIndex: i)
            iv.center = CGPoint(x: side / 2 + cos(ang) * r, y: side / 2 + sin(ang) * 0.55 * r)
            iv.alpha = 0.92
            addSubview(iv)
            satellites.append((iv, i))
        }

        let rep = UIImageView(image: StarImageRenderer.image(
            type: annotation.diary.starType, colorIndex: annotation.diary.starColor, size: markerSize
        ))
        rep.frame = CGRect(
            x: (side - markerSize) / 2, y: (side - markerSize) / 2,
            width: markerSize, height: markerSize
        )
        addSubview(rep)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        installFloat()
    }

    /// 부유 설치 — ① 컨테이너(자신) 전체에 대표 별과 같은 상하 float(대표+위성이 함께 오르내림),
    /// ② 위성마다 그 위에 작은 독립 x/y 흔들림을 더한다. 위상을 벽시계 기준으로 잡아
    /// 어노테이션이 재생성돼도 이어진다.
    private func installFloat() {
        let now = CACurrentMediaTime()
        if layer.animation(forKey: "float") == nil {
            let group = abs(repId.hashValue) % Self.floatPhaseGroups
            let phase = Double(group) / Double(Self.floatPhaseGroups)
            layer.add(Self.drift(keyPath: "transform.translation.y", amp: Self.floatAmp,
                                 period: Self.floatPeriod, now: now, phaseFraction: phase), forKey: "float")
        }
        for (iv, i) in satellites {
            guard iv.layer.animation(forKey: "drift-x") == nil else { continue }
            iv.layer.add(Self.drift(keyPath: "transform.translation.x", amp: Self.driftAmpX,
                                    period: Self.driftPeriodsX[i], now: now), forKey: "drift-x")
            iv.layer.add(Self.drift(keyPath: "transform.translation.y", amp: Self.driftAmpY,
                                    period: Self.driftPeriodsY[i], now: now), forKey: "drift-y")
        }
    }

    /// [phaseFraction](0..1, 주기 대비 비율)만큼 시작점을 밀어 같은 주기라도 서로 다른
    /// 위상에서 시작하게 한다(Android phaseGroup 패리티 — 여러 겹친 별이 한 박자로 안 움직이도록).
    private static func drift(
        keyPath: String, amp: CGFloat, period: Double, now: Double, phaseFraction: Double = 0
    ) -> CABasicAnimation {
        let a = CABasicAnimation(keyPath: keyPath)
        a.fromValue = -amp
        a.toValue = amp
        a.duration = period / 2 // autoreverse 왕복 = period
        a.autoreverses = true
        a.repeatCount = .infinity
        a.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        a.timeOffset = (now + phaseFraction * period).truncatingRemainder(dividingBy: period)
        return a
    }
}

/// 단일 별 마커 뷰 — `scalesWithViewingDistance` 로 줌아웃 시 자연스럽게 작아진다(#1).
final class SingleStarAnnotationView: MLNAnnotationView {
    init(annotation: DiaryAnnotation, reuseIdentifier: String) {
        let size = annotation.markerSize
        super.init(reuseIdentifier: reuseIdentifier)
        frame = CGRect(x: 0, y: 0, width: size, height: size)
        backgroundColor = .clear
        scalesWithViewingDistance = true
        let iv = UIImageView(image: StarImageRenderer.image(
            type: annotation.diary.starType, colorIndex: annotation.diary.starColor, size: size
        ))
        iv.frame = bounds
        iv.contentMode = .scaleAspectFit
        addSubview(iv)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

/// 다이어리를 담는 지도 어노테이션.
/// 30m 지오 머지 결과라 [members] 가 2개 이상일 수 있다(그 경우 탭 → 겹친 별 카드 뷰어).
/// [diary] 는 대표 별(멤버 중 우선순위 1위) — 마커의 모양/색은 대표를 따른다.
final class DiaryAnnotation: NSObject, MLNAnnotation {
    let diary: Diary
    /// 이 마커에 합쳐진 다이어리 전체(우선순위 정렬, 대표 포함).
    let members: [Diary]
    /// 마커 크기 배율(좋아요 합산 × 개수 보너스).
    let sizeMult: Double
    var coordinate: CLLocationCoordinate2D
    var title: String?

    init(merged: StarMerge.MergedStar) {
        self.diary = merged.rep
        self.members = merged.members
        self.sizeMult = merged.sizeMult
        self.coordinate = CLLocationCoordinate2D(latitude: merged.rep.latitude, longitude: merged.rep.longitude)
        self.title = merged.rep.title
    }

    /// 마커 이미지 크기(pt) — 배율을 0.25 단위로 양자화해 이미지 재사용(reuse identifier) 효율 유지.
    /// 기본 40pt(단일 별) ~ 최대 약 100pt(좋아요 많고 여러 개 합쳐진 별).
    var markerSize: CGFloat {
        let quantized = (sizeMult / 0.25).rounded() * 0.25
        return 40 * CGFloat(min(max(quantized, 1.0), 2.5))
    }

    /// 같은 (모양, 색, 크기) 마커는 이미지를 공유한다.
    var imageKey: String {
        "star-\(diary.starType)-\(diary.starColor)-\(Int(markerSize))"
    }
}
