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
    private static let camLatKey = "stary_cam_lat"
    private static let camLngKey = "stary_cam_lng"
    private static let camZoomKey = "stary_cam_zoom"

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    func start() {
        // 스크린샷 촬영 모드 — 실기기든 시뮬레이터든 촬영 좌표로 고정(샘플 별이 100m 안/밖으로 일정하게 놓인다).
        if let shot = ScreenshotMode.coordinate {
            coordinate = shot
            authorized = true
            return
        }
        #if targetEnvironment(simulator)
        // 컴퓨터(시뮬레이터)에서는 실제 GPS 가 없거나 엉뚱한 기본 위치라, 내 위치를 건국대로 시작한다(#2).
        if coordinate == nil {
            coordinate = CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
        }
        #endif
        // 권한이 없으면 시작하지 않는다 — 위치 창은 지도의 이유 카드(PermissionAsk)와 업로드 화면 안내에서만 띄운다.
        // 허용되면 권한 콜백(locationManagerDidChangeAuthorization)이 추적을 시작한다.
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return }
        manager.startUpdatingLocation()
    }

    /// 폴백 포함 현재 좌표(없으면 서울 기본값).
    var coordinateOrDefault: CLLocationCoordinate2D {
        coordinate ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
    }

    /// 지난 세션에서 저장한 마지막 실제 위치 — 앱 시작 시 초기 카메라용(없으면 nil).
    /// 100m 열람 게이팅에는 쓰지 않는다(게이팅은 실제 fix 인 `coordinate` 만). (Android lastSavedLatLng 패리티)
    /// UserDefaults 만 읽으므로 비격리 컨텍스트(지도 델리게이트 콜백)에서도 안전하다.
    nonisolated static var lastSavedCoordinate: CLLocationCoordinate2D? {
        let d = UserDefaults.standard
        guard d.object(forKey: lastLatKey) != nil, d.object(forKey: lastLngKey) != nil else { return nil }
        return CLLocationCoordinate2D(latitude: d.double(forKey: lastLatKey), longitude: d.double(forKey: lastLngKey))
    }

    /// 지난 세션에서 마지막으로 보던 지도 카메라(중심+줌) — 앱 시작 시 초기 카메라가
    /// "마지막으로 앱을 종료한 위치"에서 시작하게 복원한다. (Android LocationHelper.lastCameraState 패리티)
    nonisolated static var lastCameraState: (coordinate: CLLocationCoordinate2D, zoom: Double)? {
        let d = UserDefaults.standard
        guard d.object(forKey: camLatKey) != nil, d.object(forKey: camLngKey) != nil else { return nil }
        let zoom = d.object(forKey: camZoomKey) != nil ? d.double(forKey: camZoomKey) : 15
        return (CLLocationCoordinate2D(latitude: d.double(forKey: camLatKey),
                                       longitude: d.double(forKey: camLngKey)), zoom)
    }

    /// 카메라 idle 마다 호출(지도 델리게이트 = 비격리 컨텍스트) — 다음 실행의 초기 카메라 복원용.
    nonisolated static func persistCameraState(_ coord: CLLocationCoordinate2D, zoom: Double) {
        let d = UserDefaults.standard
        d.set(coord.latitude, forKey: camLatKey)
        d.set(coord.longitude, forKey: camLngKey)
        d.set(zoom, forKey: camZoomKey)
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

    /// 업로드 위치 결과 — 실패면 이유별 안내(권한 허용 / 위치 켜기 / 다시 시도 / 위치 조작). (Android LocationHelper.UploadFix 패리티)
    enum UploadFix {
        case ok(CLLocationCoordinate2D)
        case noPermission, locationOff, mock, notFound
    }

    /// 아직 권한을 묻지 않은 상태(시스템 창을 띄울 수 있다) — 아니면(거부) 설정 앱으로 보낸다.
    var permissionNotDetermined: Bool { manager.authorizationStatus == .notDetermined }

    private static let freshAge: TimeInterval = 30 * 60      // 실시간 좌표가 없을 때 믿는 최근 위치(30분)
    private static let fallbackAge: TimeInterval = 2 * 3600  // 새 위치를 끝내 못 잡았을 때 마지막 수단(2시간)

    /// 시스템이 들고 있는 마지막 위치 중 조작되지 않았고 `maxAge` 안의 것 — 쓰면 실시간 좌표에도 반영한다.
    private func recentSystemLocation(maxAge: TimeInterval) -> CLLocationCoordinate2D? {
        guard let loc = manager.location, !Self.isMock(loc), -loc.timestamp.timeIntervalSinceNow <= maxAge else { return nil }
        coordinate = loc.coordinate
        persistLastLocation(loc.coordinate)
        return loc.coordinate
    }

    /// 업로드용 위치(2026-10-23 완화) — 예전엔 실시간 좌표가 없으면 즉시 실패였다. 이제 순서대로:
    /// 실시간 좌표 → 30분 내 시스템 위치 → 최대 15초 기다림 → 2시간 내 시스템 위치. 대략적인 위치(정확한 위치 끔)도 그대로 쓴다.
    func locateForUpload() async -> UploadFix {
        // 위치 서비스가 기기 전체에서 꺼져 있으면 권한 상태도 '거부'로 보이므로 먼저 확인한다(메인 스레드에서 부르면 경고).
        let servicesOn = await Task.detached { CLLocationManager.locationServicesEnabled() }.value
        if !servicesOn {
            if let c = recentSystemLocation(maxAge: Self.fallbackAge) { return .ok(c) }
            return .locationOff
        }
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return .noPermission }
        if let c = coordinate { return .ok(c) }
        if let c = recentSystemLocation(maxAge: Self.freshAge) { return .ok(c) }

        manager.startUpdatingLocation()
        for _ in 0..<30 {
            if let c = coordinate { return .ok(c) }
            if mockDetected { break }
            try? await Task.sleep(nanoseconds: 500_000_000)
        }
        if let c = recentSystemLocation(maxAge: Self.fallbackAge) { return .ok(c) }
        return mockDetected ? .mock : .notFound
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if ScreenshotMode.isActive { return } // 촬영 좌표 고정
        #if targetEnvironment(simulator)
        // 컴퓨터(시뮬레이터)의 기본 시뮬레이션 위치는 미국(쿠퍼티노)이라 서울 폴백을 덮어쓴다 →
        // 시뮬레이터에서는 위치 업데이트를 무시하고 start() 의 서울 기본 좌표를 유지한다.
        return
        #else
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
        #endif
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorized = status == .authorizedWhenInUse || status == .authorizedAlways
            if self.authorized { manager.startUpdatingLocation() }
        }
    }
}
