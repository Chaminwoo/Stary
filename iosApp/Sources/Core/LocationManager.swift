import CoreLocation
import Foundation

/// 현재 위치 단일 소스. 다이어리 업로드/열람 반경 판정에 쓴다.
@MainActor
final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var authorized = false
    /// 모의 위치(시뮬레이션/외장 액세서리) 감지 — 감지된 좌표는 거부한다. (Android LocationHelper.mockDetected 패리티)
    @Published var mockDetected = false

    private let manager = CLLocationManager()
    private var lastPersistMs: TimeInterval = 0

    private static let lastLatKey = "stary_last_lat"
    private static let lastLngKey = "stary_last_lng"

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

    /// 지난 세션에서 저장한 마지막 실제 위치 — 앱 시작 시 초기 카메라용(없으면 nil).
    /// 100m 열람 게이팅에는 쓰지 않는다(게이팅은 실제 fix 인 `coordinate` 만). (Android lastSavedLatLng 패리티)
    static var lastSavedCoordinate: CLLocationCoordinate2D? {
        let d = UserDefaults.standard
        guard d.object(forKey: lastLatKey) != nil, d.object(forKey: lastLngKey) != nil else { return nil }
        return CLLocationCoordinate2D(latitude: d.double(forKey: lastLatKey), longitude: d.double(forKey: lastLngKey))
    }

    private func persistLastLocation(_ coord: CLLocationCoordinate2D) {
        let now = Date().timeIntervalSince1970
        guard now - lastPersistMs >= 10 else { return } // 업데이트마다 디스크에 쓰지 않게 스로틀
        lastPersistMs = now
        let d = UserDefaults.standard
        d.set(coord.latitude, forKey: Self.lastLatKey)
        d.set(coord.longitude, forKey: Self.lastLngKey)
    }

    /// 시뮬레이션(Xcode 위치 조작 등)/외장 액세서리가 만든 위치인지. iOS 15 미만은 판별 불가 → 통과.
    /// ⚠️ 시뮬레이터에서는 모든 위치가 소프트웨어 시뮬레이션이라 무조건 mock → 개발 중 내 위치가
    ///    아예 안 잡힌다. 시뮬레이터 빌드에서는 mock 판정을 끄고 좌표를 그대로 사용한다(실기기만 차단).
    private nonisolated static func isMock(_ loc: CLLocation) -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        guard let info = loc.sourceInformation else { return false }
        return info.isSimulatedBySoftware || info.isProducedByAccessory
        #endif
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        let mock = Self.isMock(loc)
        Task { @MainActor in
            if mock {
                self.mockDetected = true
                return // 조작된 좌표는 반영하지 않는다
            }
            self.mockDetected = false
            self.coordinate = loc.coordinate
            self.persistLastLocation(loc.coordinate)
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorized = status == .authorizedWhenInUse || status == .authorizedAlways
            if self.authorized { manager.startUpdatingLocation() }
        }
    }
}
