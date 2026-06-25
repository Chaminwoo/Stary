import CoreLocation
import Foundation

/// 현재 위치 단일 소스. 다이어리 업로드/열람 반경 판정에 쓴다.
@MainActor
final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var authorized = false

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    func start() {
        manager.startUpdatingLocation()
    }

    /// 폴백 포함 현재 좌표(없으면 서울 기본값).
    var coordinateOrDefault: CLLocationCoordinate2D {
        coordinate ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        Task { @MainActor in self.coordinate = loc.coordinate }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorized = status == .authorizedWhenInUse || status == .authorizedAlways
            if self.authorized { manager.startUpdatingLocation() }
        }
    }
}
