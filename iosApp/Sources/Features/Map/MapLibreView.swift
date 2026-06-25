import CoreLocation
import MapLibre
import SwiftUI

/// 다이어리 좌표를 MapLibre 지도 위 별 마커로 표시한다.
/// Android `DiaryMap` 대응의 iOS 구현 시작점.
struct MapLibreView: UIViewRepresentable {
    let diaries: [Diary]
    let center: CLLocationCoordinate2D
    var onTapDiary: (Diary) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)
        // 키 불필요한 데모 벡터 스타일(추후 자체 스타일/키로 교체).
        mapView.styleURL = URL(string: "https://demotiles.maplibre.org/style.json")
        mapView.delegate = context.coordinator
        mapView.setCenter(center, zoomLevel: 13, animated: false)
        mapView.showsUserLocation = true
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.parent = self
        if let existing = mapView.annotations { mapView.removeAnnotations(existing) }
        let annotations = diaries.compactMap { diary -> DiaryAnnotation? in
            guard diary.latitude != 0 || diary.longitude != 0 else { return nil }
            return DiaryAnnotation(diary: diary)
        }
        mapView.addAnnotations(annotations)
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
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
