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
    var onTapDiary: (Diary) -> Void
    /// 도보 길찾기 경로(비었으면 표시 안 함). (Android DiaryMap ROUTE_LAYER 패리티)
    var route: [CLLocationCoordinate2D] = []

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)
        // 키 불필요한 데모 벡터 스타일(추후 자체 스타일/키로 교체).
        mapView.styleURL = URL(string: "https://demotiles.maplibre.org/style.json")
        mapView.delegate = context.coordinator
        // 위치 fix 전엔 기본 좌표로 시작(이후 실제 fix 가 들어오면 재센터).
        let fallback = CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
        mapView.setCenter(userLocation ?? fallback, zoomLevel: 13, animated: false)
        mapView.showsUserLocation = true
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.parent = self
        // 최초 진입 시 실제 위치 fix 가 들어오면 그 위치로 1회만 부드럽게 이동(Android didAutoCenter 패리티).
        if !context.coordinator.didAutoCenter, let me = userLocation {
            context.coordinator.didAutoCenter = true
            mapView.setCenter(me, zoomLevel: 14, animated: true)
        }
        if let existing = mapView.annotations { mapView.removeAnnotations(existing) }
        var toAdd: [MLNAnnotation] = diaries.compactMap { diary -> DiaryAnnotation? in
            guard diary.latitude != 0 || diary.longitude != 0 else { return nil }
            return DiaryAnnotation(diary: diary)
        }
        // 도보 경로 폴리라인(있으면) — 별 마커와 함께 한 번에 추가.
        if route.count >= 2 {
            toAdd.append(MLNPolyline(coordinates: route, count: UInt(route.count)))
        }
        mapView.addAnnotations(toAdd)
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
        var didAutoCenter = false
        init(_ parent: MapLibreView) { self.parent = parent }

        func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
            guard let d = annotation as? DiaryAnnotation else { return nil }
            let key = "star-\(d.diary.starType)-\(d.diary.starColor)"
            if let cached = mapView.dequeueReusableAnnotationImage(withIdentifier: key) { return cached }
            let img = StarImageRenderer.image(type: d.diary.starType, colorIndex: d.diary.starColor)
            return MLNAnnotationImage(image: img, reuseIdentifier: key)
        }

        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            if let d = annotation as? DiaryAnnotation { parent.onTapDiary(d.diary) }
            mapView.deselectAnnotation(annotation, animated: false)
        }

        // 경로 폴리라인 스타일 — 민트색 선.
        func mapView(_ mapView: MLNMapView, strokeColorForShapeAnnotation annotation: MLNShape) -> UIColor {
            UIColor(red: 0.43, green: 0.91, blue: 0.72, alpha: 1) // #6EE7B7
        }
        func mapView(_ mapView: MLNMapView, lineWidthForPolylineAnnotation annotation: MLNPolyline) -> CGFloat { 4 }
        func mapView(_ mapView: MLNMapView, alphaForShapeAnnotation annotation: MLNShape) -> CGFloat { 0.95 }
    }
}

/// 다이어리를 담는 지도 어노테이션.
final class DiaryAnnotation: NSObject, MLNAnnotation {
    let diary: Diary
    var coordinate: CLLocationCoordinate2D
    var title: String?

    init(diary: Diary) {
        self.diary = diary
        self.coordinate = CLLocationCoordinate2D(latitude: diary.latitude, longitude: diary.longitude)
        self.title = diary.title
    }
}
