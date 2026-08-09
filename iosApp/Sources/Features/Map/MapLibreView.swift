
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

    /// 별 마커 탭 — 30m 안에서 합쳐진 멤버 전체(우선순위 정렬) +
    /// 탭한 별의 화면상 위치(0..1) + 지도 스냅샷을 넘긴다.
    /// 100m 게이팅/파장(warp) 연출/상세 진입은 호출부(MapScreen)의 책임.
    var onTapStar: (
        _ members: [Diary],
        _ originUnit: CGPoint,
        _ snapshot: UIImage?
    ) -> Void

    /// 도보 길찾기 경로(비었으면 표시 안 함).
    var route: [CLLocationCoordinate2D] = []

    /// 외부에서 "이 좌표로 카메라 이동" 요청.
    /// 값이 바뀔 때 1회 애니메이션 이동.
    var focusTarget: CLLocationCoordinate2D?

    /// 개척 퀘스트 미개척 대상국.
    /// 중심좌표에 금색 스파클 비콘 표시.
    var pioneerCountries: [PioneerQuest.Country] = []

    /// 개척 비콘 탭 → 국가 코드 전달.
    var onTapPioneer: ((String) -> Void)? = nil

    /// 줌 버튼(+/−) 요청.
    /// nonce가 바뀔 때 delta 만큼 애니메이션 줌.
    var zoomRequest: (delta: Double, nonce: Int) = (0, 0)

    /// "내 위치로" 요청.
    /// nonce가 바뀔 때 현재 위치로 줌 15 이동.
    var recenterNonce: Int = 0

    /// 별자리 라인 토글.
    var constellationEnabled: Bool = false

    /// 세계(웹메르카토르) 상하 타일 한계 밖 "빈 공간"의 화면 경계 보고.
    var onWorldVoid: (
        (_ topY: CGFloat, _ bottomY: CGFloat, _ zoom: Double) -> Void
    )? = nil

    // MARK: - Constants

    /// 지도 최소 줌.
    static let mapMinZoom = 2.4

    /// 지도 기본 기울기.
    static let baseTiltDeg: CGFloat = 25

    /// 야경 커스텀 스타일 URL.
    static let staryStyleURL: URL? = {
        guard
            let key = Bundle.main.object(
                forInfoDictionaryKey: "MAPTILER_KEY"
            ) as? String,
            !key.trimmingCharacters(
                in: .whitespacesAndNewlines
            ).isEmpty,
            let srcURL = Bundle.main.url(
                forResource: "maplibre_style",
                withExtension: "json"
            ),
            let raw = try? String(
                contentsOf: srcURL,
                encoding: .utf8
            )
        else {
            return nil
        }

        let trimmedKey = key.trimmingCharacters(
            in: .whitespacesAndNewlines
        )

        let json = raw.replacingOccurrences(
            of: "__MAPTILER_KEY__",
            with: trimmedKey
        )

        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent("stary_style.json")

        guard
            (try? json.write(
                to: dest,
                atomically: true,
                encoding: .utf8
            )) != nil
        else {
            return nil
        }

        return dest
    }()

    // MARK: - UIViewRepresentable

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)

        // 커스텀 스타일.
        // 키가 없거나 스타일 파일을 읽지 못하면 데모 스타일 사용.
        mapView.styleURL =
            Self.staryStyleURL
            ?? URL(
                string: "https://demotiles.maplibre.org/style.json"
            )

        mapView.delegate = context.coordinator

        // 초기 카메라.
        if let cam = LocationManager.lastCameraState,
           userLocation == nil {

            mapView.setCenter(
                cam.coordinate,
                zoomLevel: cam.zoom,
                animated: false
            )

        } else {

            let fallback =
                LocationManager.lastSavedCoordinate
                ?? CLLocationCoordinate2D(
                    latitude: AppConfig.defaultLat,
                    longitude: AppConfig.defaultLng
                )

            mapView.setCenter(
                userLocation ?? fallback,
                zoomLevel: 15,
                animated: false
            )
        }

        // 기본 기울기.
        let camera = mapView.camera
        camera.pitch = Self.baseTiltDeg
        mapView.setCamera(camera, animated: false)

        // 회전/기울기 제스처 잠금.
        mapView.allowsRotating = false
        mapView.allowsTilting = false

        // 사용자 위치.
        mapView.showsUserLocation = true

        // 최소 줌.
        mapView.minimumZoomLevel = Self.mapMinZoom

        // 지도 UI 정리.
        mapView.logoView.isHidden = true
        mapView.attributionButton.isHidden = true
        mapView.compassView.isHidden = true

        return mapView
    }

    func updateUIView(
        _ mapView: MLNMapView,
        context: Context
    ) {
        context.coordinator.parent = self

        // 최초 실제 위치 fix.
        if !context.coordinator.didAutoCenter,
           let me = userLocation {

            context.coordinator.didAutoCenter = true

            mapView.setCenter(
                me,
                zoomLevel: 15,
                animated: true
            )
        }

        // 외부 포커스 요청.
        if let target = focusTarget,
           !context.coordinator.sameAsLastFocus(target) {

            context.coordinator.lastFocus = target

            mapView.setCenter(
                target,
                zoomLevel: 15,
                animated: true
            )
        }

        // 줌 요청.
        if zoomRequest.nonce
            != context.coordinator.lastZoomNonce {

            context.coordinator.lastZoomNonce =
                zoomRequest.nonce

            let newZoom =
                mapView.zoomLevel + zoomRequest.delta

            mapView.setZoomLevel(
                newZoom,
                animated: true
            )
        }

        // 내 위치로 이동.
        if recenterNonce
            != context.coordinator.lastRecenterNonce {

            context.coordinator.lastRecenterNonce =
                recenterNonce

            if let me = userLocation {
                mapView.setCenter(
                    me,
                    zoomLevel: 15,
                    animated: true
                )
            }
        }

        // MARK: Annotation Hash

        var hasher = Hasher()

        for d in diaries {
            hasher.combine(d.id)
            hasher.combine(d.latitude)
            hasher.combine(d.longitude)
            hasher.combine(d.starType)
            hasher.combine(d.starColor)
            hasher.combine(d.likeCount)
        }

        for c in pioneerCountries {
            hasher.combine(c.code)
        }

        let annotationsKey = hasher.finalize()

        if annotationsKey
            != context.coordinator.lastAnnotationsKey {

            context.coordinator.lastAnnotationsKey =
                annotationsKey

            if let existing = mapView.annotations {
                mapView.removeAnnotations(existing)
            }

            // 30m 지오 머지.
            var toAdd: [MLNAnnotation] =
                StarMerge.merge(diaries).map {
                    DiaryAnnotation(merged: $0)
                }

            // 개척 퀘스트 비콘.
            toAdd.append(
                contentsOf:
                    pioneerCountries.map {
                        PioneerAnnotation(country: $0)
                    }
            )

            mapView.addAnnotations(toAdd)

            // 별 후광 갱신.
            context.coordinator.refreshAuraFeatures(
                mapView
            )
        }

        // 스타일 이펙트.
        context.coordinator.updateRouteShape()

        context.coordinator.setConstellation(
            enabled: constellationEnabled,
            mapView: mapView
        )

        context.coordinator.requestConstellationRebuild(
            mapView
        )
    }

    static func dismantleUIView(
        _ uiView: MLNMapView,
        coordinator: Coordinator
    ) {
        coordinator.teardownStyleEffects()
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, MLNMapViewDelegate {

        var parent: MapLibreView

        var didAutoCenter = false

        /// 마지막 포커스 좌표.
        var lastFocus: CLLocationCoordinate2D?

        /// 마지막 줌 요청 nonce.
        var lastZoomNonce: Int = 0

        /// 마지막 recenter 요청 nonce.
        var lastRecenterNonce: Int = 0

        /// 마지막 annotation 데이터 해시.
        var lastAnnotationsKey: Int?

        /// 현재 적용된 줌 기반 별 크기.
        var starZoomScale: CGFloat = 1

        // MARK: Style Effects

        weak var styleRef: MLNStyle?
        weak var mapRef: MLNMapView?

        var twinkleTimer: Timer?
        var twinkleT: Double = 0

        /// 카메라 이동 중 스타일 갱신 중지.
        var isCameraMoving = false

        var constellationOn = false

        var constellationFadeTask: Task<Void, Never>?
        var constellationRebuildTask: Task<Void, Never>?

        /// 현재 별자리 라인 불투명도.
        var constellationFadeValue: Double = 0

        /// 마지막 별자리 선 구성 키.
        var lastConstellationKey: String?

        /// 마지막 세계 빈 공간 경계.
        var lastVoid: (
            top: CGFloat,
            bottom: CGFloat
        ) = (
            0,
            .greatestFiniteMagnitude
        )

        /// 마지막 이펙트 줌.
        var lastEffectZoom: Double = -1

        /// 저줌 게이트 상태.
        var lastGateZero = false

        init(_ parent: MapLibreView) {
            self.parent = parent
            super.init()
        }

        // MARK: Star Scale

        /// 줌 레벨 → 별 크기 배율.
        static func starScale(
            forZoom zoom: Double
        ) -> CGFloat {

            let s: Double

            switch zoom {

            case ..<6:
                s = 0.06

            case ..<10:
                s =
                    0.06
                    + (zoom - 6) / 4
                    * (0.2 - 0.06)

            case ..<13:
                s =
                    0.2
                    + (zoom - 10) / 3
                    * (0.5 - 0.2)

            case ..<15:
                s =
                    0.5
                    + (zoom - 13) / 2
                    * (1.0 - 0.5)

            default:
                s = 1.0
            }

            return CGFloat(s)
        }

        func applyStarZoomScale(
            _ mapView: MLNMapView
        ) {

            let s = Self.starScale(
                forZoom: mapView.zoomLevel
            )

            guard
                abs(s - starZoomScale) > 0.015
            else {
                return
            }

            starZoomScale = s

            for annotation in mapView.annotations ?? [] {

                guard
                    annotation is DiaryAnnotation,
                    let v = mapView.view(
                        for: annotation
                    )
                else {
                    continue
                }

                v.transform =
                    CGAffineTransform(
                        scaleX: s,
                        y: s
                    )
            }
        }

        // MARK: World Void

        func reportWorldVoid(
            _ mapView: MLNMapView
        ) {

            guard
                let cb = parent.onWorldVoid
            else {
                return
            }

            let h = mapView.bounds.height

            guard h > 0 else {
                return
            }

            let cy = h / 2

            let maxLat =
                85.05112877980659

            let lng =
                mapView.centerCoordinate.longitude

            let topRaw =
                mapView.convert(
                    CLLocationCoordinate2D(
                        latitude: maxLat,
                        longitude: lng
                    ),
                    toPointTo: mapView
                ).y

            let bottomRaw =
                mapView.convert(
                    CLLocationCoordinate2D(
                        latitude: -maxLat,
                        longitude: lng
                    ),
                    toPointTo: mapView
                ).y

            let top: CGFloat =
                (
                    topRaw > 0
                    && topRaw < cy
                )
                ? topRaw
                : 0

            let bottom: CGFloat =
                (
                    bottomRaw < h
                    && bottomRaw > cy
                )
                ? bottomRaw
                : .greatestFiniteMagnitude

            guard
                top != lastVoid.top
                || bottom != lastVoid.bottom
            else {
                return
            }

            lastVoid = (
                top: top,
                bottom: bottom
            )

            cb(
                top,
                bottom,
                mapView.zoomLevel
            )
        }

        // MARK: Camera

        func mapViewRegionIsChanging(
            _ mapView: MLNMapView
        ) {

            isCameraMoving = true

            applyStarZoomScale(mapView)
            applyStyleEffectZoom(mapView)
            reportWorldVoid(mapView)
        }

        func mapView(
            _ mapView: MLNMapView,
            regionDidChangeAnimated animated: Bool
        ) {

            isCameraMoving = false

            applyStarZoomScale(mapView)
            applyStyleEffectZoom(mapView)
            reportWorldVoid(mapView)

            requestConstellationRebuild(
                mapView
            )

            LocationManager.persistCameraState(
                mapView.centerCoordinate,
                zoom: mapView.zoomLevel
            )
        }

        // MARK: Focus

        func sameAsLastFocus(
            _ c: CLLocationCoordinate2D
        ) -> Bool {

            guard
                let last = lastFocus
            else {
                return false
            }

            return
                abs(last.latitude - c.latitude)
                    < 1e-7
                &&
                abs(last.longitude - c.longitude)
                    < 1e-7
        }

        // MARK: Annotation View

        func mapView(
            _ mapView: MLNMapView,
            viewFor annotation: MLNAnnotation
        ) -> MLNAnnotationView? {

            guard
                let d = annotation as? DiaryAnnotation
            else {
                return nil
            }

            let scale =
                CGAffineTransform(
                    scaleX: starZoomScale,
                    y: starZoomScale
                )

            // 머지된 별.
            if d.members.count > 1 {

                let v =
                    MergedStarAnnotationView(
                        annotation: d
                    )

                v.transform = scale

                return v
            }

            // 단일 별.
            let id =
                "single-\(d.imageKey)"

            if let reused =
                mapView.dequeueReusableAnnotationView(
                    withIdentifier: id
                ) {

                reused.transform = scale

                return reused
            }

            let v =
                SingleStarAnnotationView(
                    annotation: d,
                    reuseIdentifier: id
                )

            v.transform = scale

            return v
        }

        // MARK: Annotation Image

        func mapView(
            _ mapView: MLNMapView,
            imageFor annotation: MLNAnnotation
        ) -> MLNAnnotationImage? {

            // 개척 비콘.
            if annotation is PioneerAnnotation {

                let key =
                    "pioneer-beacon-12"

                if let cached =
                    mapView.dequeueReusableAnnotationImage(
                        withIdentifier: key
                    ) {
                    return cached
                }

                let img =
                    StarImageRenderer.image(
                        type: 3,
                        colorIndex: 15,
                        size: 12
                    )

                return MLNAnnotationImage(
                    image: img,
                    reuseIdentifier: key
                )
            }

            guard
                let d = annotation as? DiaryAnnotation
            else {
                return nil
            }

            let key = d.imageKey

            if let cached =
                mapView.dequeueReusableAnnotationImage(
                    withIdentifier: key
                ) {
                return cached
            }

            let img =
                StarImageRenderer.image(
                    type: d.diary.starType,
                    colorIndex: d.diary.starColor,
                    size: d.markerSize
                )

            return MLNAnnotationImage(
                image: img,
                reuseIdentifier: key
            )
        }

        // MARK: Annotation Tap

        func mapView(
            _ mapView: MLNMapView,
            didSelect annotation: MLNAnnotation
        ) {

            if let d =
                annotation as? DiaryAnnotation {

                let sp =
                    mapView.convert(
                        d.coordinate,
                        toPointTo: mapView
                    )

                let w =
                    max(
                        mapView.bounds.width,
                        1
                    )

                let h =
                    max(
                        mapView.bounds.height,
                        1
                    )

                let origin =
                    CGPoint(
                        x: min(
                            max(
                                sp.x / w,
                                0
                            ),
                            1
                        ),
                        y: min(
                            max(
                                sp.y / h,
                                0
                            ),
                            1
                        )
                    )

                let snapshot =
                    UIGraphicsImageRenderer(
                        bounds: mapView.bounds
                    ).image { _ in

                        mapView.drawHierarchy(
                            in: mapView.bounds,
                            afterScreenUpdates: false
                        )
                    }

                parent.onTapStar(
                    d.members,
                    origin,
                    snapshot
                )
            }

            if let p =
                annotation as? PioneerAnnotation {

                parent.onTapPioneer?(
                    p.country.code
                )
            }

            mapView.deselectAnnotation(
                annotation,
                animated: false
            )
        }
    }
}

// MARK: - Pioneer Annotation

/// 개척 퀘스트 대상국 비콘 어노테이션.
final class PioneerAnnotation:
    NSObject,
    MLNAnnotation {

    let country: PioneerQuest.Country

    var coordinate: CLLocationCoordinate2D

    var title: String?

    init(country: PioneerQuest.Country) {

        self.country = country

        self.coordinate =
            CLLocationCoordinate2D(
                latitude: country.lat,
                longitude: country.lng
            )

        self.title = nil

        super.init()
    }
}

// MARK: - Map Sparkle

/// 별 마커 곁을 도는 스파클 파티클.
enum MapSparkle {

    /// 큰 별 기준.
    static let bigStarThreshold: Double = 1.75

    /// 최대 궤도 영역 비율.
    static let maxOrbitExtentRatio: CGFloat =
        0.62 + 0.16

    /// sizeMult에 따른 파티클 수.
    static func particleCount(
        sizeMult: Double
    ) -> Int {

        if sizeMult >= 2.6 {
            return 3
        }

        if sizeMult >= 1.6 {
            return 2
        }

        return 1
    }

    /// 공전 파티클 설치.
    static func install(
        on host: UIView,
        center: CGPoint,
        markerSize: CGFloat,
        sizeMult: Double,
        starType: Int,
        starColor: Int
    ) {

        let count =
            particleCount(
                sizeMult: sizeMult
            )

        let big =
            sizeMult >= bigStarThreshold

        let radii: [CGFloat] = [
            markerSize * 0.42,
            markerSize * 0.56,
            markerSize * 0.62
        ]

        let sizes: [CGFloat] = [
            markerSize * 0.30,
            markerSize * 0.24,
            markerSize * 0.16
        ]

        let periods: [Double] = [
            2 * .pi / 1.1,
            2 * .pi / 0.8,
            2 * .pi / 1.5
        ]

        let clockwise: [Bool] = [
            true,
            false,
            true
        ]

        for set in 0..<count {

            let size =
                sizes[set]

            let image =
                big
                ? StarImageRenderer.image(
                    type: starType,
                    colorIndex: starColor,
                    size: size
                )
                : whiteSparkle(
                    size: size
                )

            let iv =
                UIImageView(
                    image: image
                )

            iv.bounds =
                CGRect(
                    x: 0,
                    y: 0,
                    width: size,
                    height: size
                )

            iv.center = center
            iv.alpha = 0.95

            host.addSubview(iv)

            addOrbit(
                to: iv,
                center: center,
                radius: radii[set],
                period: periods[set],
                clockwise: clockwise[set],
                phaseFraction:
                    Double(set) * 0.37
            )
        }
    }

    private static func addOrbit(
        to view: UIView,
        center: CGPoint,
        radius: CGFloat,
        period: Double,
        clockwise: Bool,
        phaseFraction: Double
    ) {

        let ySquash: CGFloat = 0.55

        let rect =
            CGRect(
                x: center.x - radius,
                y: center.y - radius * ySquash,
                width: radius * 2,
                height: radius * ySquash * 2
            )

        let oval =
            UIBezierPath(
                ovalIn: rect
            )

        let path =
            clockwise
            ? oval
            : oval.reversing()

        let anim =
            CAKeyframeAnimation(
                keyPath: "position"
            )

        anim.path = path.cgPath
        anim.duration = period
        anim.calculationMode = .paced
        anim.repeatCount = .infinity
        anim.isRemovedOnCompletion = false

        anim.timeOffset =
            (
                CACurrentMediaTime()
                + phaseFraction * period
            )
            .truncatingRemainder(
                dividingBy: period
            )

        view.layer.add(
            anim,
            forKey: "orbit"
        )
    }

    // MARK: White Sparkle

    private static var whiteCache:
        [Int: UIImage] = [:]

    private static func whiteSparkle(
        size: CGFloat
    ) -> UIImage {

        let key =
            Int(size.rounded())

        if let cached =
            whiteCache[key] {
            return cached
        }

        let px =
            CGFloat(
                max(key, 1)
            )

        let img =
            UIGraphicsImageRenderer(
                size: CGSize(
                    width: px,
                    height: px
                )
            ).image { ctx in

                let cg =
                    ctx.cgContext

                let body =
                    px * 0.68

                let rect =
                    CGRect(
                        x: (px - body) / 2,
                        y: (px - body) / 2,
                        width: body,
                        height: body
                    )

                let path =
                    StarShape(
                        type: 0
                    ).path(
                        in: rect
                    ).cgPath

                cg.setShadow(
                    offset: .zero,
                    blur: px * 0.18,
                    color:
                        UIColor.white
                        .withAlphaComponent(0.9)
                        .cgColor
                )

                cg.setFillColor(
                    UIColor.white.cgColor
                )

                cg.addPath(path)

                cg.fillPath(
                    using: .evenOdd
                )
            }

        whiteCache[key] = img

        return img
    }
}

// MARK: - Merged Star Annotation View

/// 겹친 별(머지) 마커 뷰.
final class MergedStarAnnotationView:
    MLNAnnotationView {

    private static let maxOrbitStars = 4

    private static let anchorAngles: [CGFloat] = [
        -0.6,
        2.3,
        4.1,
        1.1
    ]

    private static let driftPeriodsX: [Double] = [
        4.6,
        5.7,
        3.9,
        5.2
    ]

    private static let driftPeriodsY: [Double] = [
        3.3,
        2.9,
        3.8,
        3.1
    ]

    private static let satSize: CGFloat = 16

    private static let driftAmpX: CGFloat = 0.8
    private static let driftAmpY: CGFloat = 1.0

    private static let floatAmp: CGFloat = 4.0

    private static let floatPeriod: Double =
        2 * .pi / 1.6

    private static let floatPhaseGroups = 4

    private var satellites:
        [(view: UIImageView, index: Int)] = []

    private let repId: String

    private static func radius(
        markerSize: CGFloat,
        satIndex: Int
    ) -> CGFloat {

        markerSize * 0.12
        + 1
        + CGFloat(satIndex) * 1.6
    }

    init(annotation: DiaryAnnotation) {

        let markerSize =
            annotation.markerSize

        let members =
            Array(
                annotation.members
                    .dropFirst()
                    .prefix(Self.maxOrbitStars)
            )

        let maxRadius =
            Self.radius(
                markerSize: markerSize,
                satIndex:
                    max(
                        members.count - 1,
                        0
                    )
            )

        let satExtent =
            maxRadius
            + Self.satSize / 2
            + Self.driftAmpY
            + Self.floatAmp

        let orbitExtent =
            markerSize
            * MapSparkle.maxOrbitExtentRatio
            + Self.floatAmp

        let side =
            max(
                satExtent,
                orbitExtent
            ) * 2

        // Diary.id가 String?이라는 기존 코드 가정.
        self.repId =
            annotation.diary.id ?? ""

        super.init(
            reuseIdentifier: nil
        )

        frame =
            CGRect(
                x: 0,
                y: 0,
                width: side,
                height: side
            )

        backgroundColor = .clear
        clipsToBounds = false
        scalesWithViewingDistance = true

        // 스파클 먼저 설치.
        MapSparkle.install(
            on: self,
            center:
                CGPoint(
                    x: side / 2,
                    y: side / 2
                ),
            markerSize: markerSize,
            sizeMult: annotation.sizeMult,
            starType: annotation.diary.starType,
            starColor: annotation.diary.starColor
        )

        // 위성.
        for (i, m)
            in members.enumerated() {

            let iv =
                UIImageView(
                    image:
                        StarImageRenderer.image(
                            type: m.starType,
                            colorIndex: m.starColor,
                            size: Self.satSize
                        )
                )

            iv.bounds =
                CGRect(
                    x: 0,
                    y: 0,
                    width: Self.satSize,
                    height: Self.satSize
                )

            let ang =
                Self.anchorAngles[i]

            let r =
                Self.radius(
                    markerSize: markerSize,
                    satIndex: i
                )

            iv.center =
                CGPoint(
                    x:
                        side / 2
                        + cos(ang) * r,
                    y:
                        side / 2
                        + sin(ang)
                        * 0.55
                        * r
                )

            iv.alpha = 0.92

            addSubview(iv)

            satellites.append(
                (
                    view: iv,
                    index: i
                )
            )
        }

        // 대표 별.
        let rep =
            UIImageView(
                image:
                    StarImageRenderer.image(
                        type:
                            annotation.diary.starType,
                        colorIndex:
                            annotation.diary.starColor,
                        size: markerSize
                    )
            )

        rep.frame =
            CGRect(
                x:
                    (side - markerSize) / 2,
                y:
                    (side - markerSize) / 2,
                width: markerSize,
                height: markerSize
            )

        addSubview(rep)
    }

    required init?(
        coder: NSCoder
    ) {
        fatalError(
            "init(coder:) is not supported"
        )
    }

    override func didMoveToWindow() {

        super.didMoveToWindow()

        guard window != nil else {
            return
        }

        installFloat()
    }

    private func installFloat() {

        let now =
            CACurrentMediaTime()

        if layer.animation(
            forKey: "float"
        ) == nil {

            let group =
                Int(
                    UInt(
                        bitPattern:
                            repId.hashValue
                    )
                    % UInt(
                        Self.floatPhaseGroups
                    )
                )

            let phase =
                Double(group)
                / Double(
                    Self.floatPhaseGroups
                )

            layer.add(
                Self.drift(
                    keyPath:
                        "transform.translation.y",
                    amp: Self.floatAmp,
                    period: Self.floatPeriod,
                    now: now,
                    phaseFraction: phase
                ),
                forKey: "float"
            )
        }

        for (iv, i)
            in satellites {

            guard
                iv.layer.animation(
                    forKey: "drift-x"
                ) == nil
            else {
                continue
            }

            iv.layer.add(
                Self.drift(
                    keyPath:
                        "transform.translation.x",
                    amp: Self.driftAmpX,
                    period:
                        Self.driftPeriodsX[i],
                    now: now
                ),
                forKey: "drift-x"
            )

            iv.layer.add(
                Self.drift(
                    keyPath:
                        "transform.translation.y",
                    amp: Self.driftAmpY,
                    period:
                        Self.driftPeriodsY[i],
                    now: now
                ),
                forKey: "drift-y"
            )
        }
    }

    private static func drift(
        keyPath: String,
        amp: CGFloat,
        period: Double,
        now: Double,
        phaseFraction: Double = 0
    ) -> CABasicAnimation {

        let a =
            CABasicAnimation(
                keyPath: keyPath
            )

        a.fromValue = -amp
        a.toValue = amp
        a.duration = period / 2
        a.autoreverses = true
        a.repeatCount = .infinity

        a.timingFunction =
            CAMediaTimingFunction(
                name: .easeInEaseOut
            )

        a.timeOffset =
            (
                now
                + phaseFraction * period
            )
            .truncatingRemainder(
                dividingBy: period
            )

        return a
    }
}

// MARK: - Single Star Annotation View

/// 단일 별 마커 뷰.
final class SingleStarAnnotationView:
    MLNAnnotationView {

    init(
        annotation: DiaryAnnotation,
        reuseIdentifier: String
    ) {

        let markerSize =
            annotation.markerSize

        let box =
            (
                markerSize / 2
                + markerSize
                * MapSparkle.maxOrbitExtentRatio
            ) * 2

        super.init(
            reuseIdentifier: reuseIdentifier
        )

        frame =
            CGRect(
                x: 0,
                y: 0,
                width: box,
                height: box
            )

        backgroundColor = .clear
        clipsToBounds = false
        scalesWithViewingDistance = true

        let center =
            CGPoint(
                x: box / 2,
                y: box / 2
            )

        // 파티클 먼저.
        MapSparkle.install(
            on: self,
            center: center,
            markerSize: markerSize,
            sizeMult: annotation.sizeMult,
            starType: annotation.diary.starType,
            starColor: annotation.diary.starColor
        )

        // 대표 별.
        let iv =
            UIImageView(
                image:
                    StarImageRenderer.image(
                        type:
                            annotation.diary.starType,
                        colorIndex:
                            annotation.diary.starColor,
                        size: markerSize
                    )
            )

        iv.frame =
            CGRect(
                x:
                    center.x - markerSize / 2,
                y:
                    center.y - markerSize / 2,
                width: markerSize,
                height: markerSize
            )

        iv.contentMode = .scaleAspectFit

        addSubview(iv)
    }

    required init?(
        coder: NSCoder
    ) {
        fatalError(
            "init(coder:) is not supported"
        )
    }
}

// MARK: - Diary Annotation

/// 다이어리를 담는 지도 어노테이션.
/// 30m 지오 머지 결과라 [members]가 2개 이상일 수 있다.
final class DiaryAnnotation:
    NSObject,
    MLNAnnotation {

    let diary: Diary

    /// 이 마커에 합쳐진 다이어리 전체.
    let members: [Diary]

    /// 마커 크기 배율.
    let sizeMult: Double

    var coordinate: CLLocationCoordinate2D

    var title: String?

    init(
        merged: StarMerge.MergedStar
    ) {

        self.diary =
            merged.rep

        self.members =
            merged.members

        self.sizeMult =
            merged.sizeMult

        self.coordinate =
            CLLocationCoordinate2D(
                latitude:
                    merged.rep.latitude,
                longitude:
                    merged.rep.longitude
            )

        self.title =
            merged.rep.title

        super.init()
    }

    /// 마커 이미지 크기(pt).
    var markerSize: CGFloat {

        let quantized =
            (
                sizeMult / 0.25
            ).rounded()
            * 0.25

        return 40
            * CGFloat(
                min(
                    max(
                        quantized,
                        1.0
                    ),
                    2.5
                )
            )
    }

    /// 같은 (모양, 색, 크기) 마커는 이미지를 공유.
    var imageKey: String {

        "star-\(diary.starType)-\(diary.starColor)-\(Int(markerSize))"
    }
}
