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
    /// 별 마커 탭 — 30m 안에서 합쳐진 멤버 전체(우선순위 정렬)를 넘긴다.
    /// 1개면 바로 상세, 2개 이상이면 겹친 별 카드 뷰어를 여는 것이 호출부의 책임.
    var onTapStar: ([Diary]) -> Void
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

    /// 3D 글로브 "지구 보기" 버튼 노출 줌 / 지도 최소 줌.
    /// (Android DiaryMap GLOBE_BUTTON_ZOOM/MAP_MIN_ZOOM 패리티)
    static let globeButtonZoom = 3.0
    static let mapMinZoom = 2.4

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)
        // 키 불필요한 데모 벡터 스타일(추후 자체 스타일/키로 교체).
        mapView.styleURL = URL(string: "https://demotiles.maplibre.org/style.json")
        mapView.delegate = context.coordinator
        // 위치 fix 전엔 "지난 세션 마지막 위치"(없으면 기본 좌표)로 시작 — 기본좌표에서 내 위치로
        // 크게 점프하는 간격을 줄인다(체크리스트 29). 실제 fix 가 들어오면 재센터.
        let fallback = LocationManager.lastSavedCoordinate
            ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
        mapView.setCenter(userLocation ?? fallback, zoomLevel: 13, animated: false)
        mapView.showsUserLocation = true
        mapView.minimumZoomLevel = Self.mapMinZoom // 이 밑은 3D 글로브가 담당
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.parent = self
        // 최초 진입 시 실제 위치 fix 가 들어오면 그 위치로 1회만 부드럽게 이동(Android didAutoCenter 패리티).
        if !context.coordinator.didAutoCenter, let me = userLocation {
            context.coordinator.didAutoCenter = true
            mapView.setCenter(me, zoomLevel: 14, animated: true)
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
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
        var didAutoCenter = false
        /// 마지막으로 카메라를 옮긴 포커스 좌표(중복 이동 방지).
        var lastFocus: CLLocationCoordinate2D?
        /// 마지막으로 처리한 글로브 복귀 요청 nonce.
        var lastGlobeReturnNonce: Int = -1
        init(_ parent: MapLibreView) { self.parent = parent }

        /// 줌 상태 보고 → 호출부가 하단 "지구 보기" 버튼 노출을 결정(자동 전환 없음).
        private func reportGlobeAvailability(_ mapView: MLNMapView) {
            guard let cb = parent.onGlobeAvailability else { return }
            let c = mapView.centerCoordinate
            cb(c.latitude, c.longitude, mapView.zoomLevel <= MapLibreView.globeButtonZoom)
        }

        func mapViewRegionIsChanging(_ mapView: MLNMapView) {
            reportGlobeAvailability(mapView)
        }

        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            reportGlobeAvailability(mapView)
        }

        /// 직전 포커스와 (거의) 같은 좌표인지 — 같은 별 재요청 시 카메라를 다시 옮기지 않게.
        func sameAsLastFocus(_ c: CLLocationCoordinate2D) -> Bool {
            guard let last = lastFocus else { return false }
            return abs(last.latitude - c.latitude) < 1e-7 && abs(last.longitude - c.longitude) < 1e-7
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
            // 겹친 별(멤버 2개 이상)이면 카드 뷰어로, 하나면 바로 상세로.
            if let d = annotation as? DiaryAnnotation { parent.onTapStar(d.members) }
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
