import CoreLocation
import Foundation

/// 좌표 거리 계산(Haversine). KMP `GeoUtils` 와 동일한 의미.
enum Geo {
    private static let earthRadiusM = 6_371_000.0
    private static let unknownDistanceM = 20_000_000.0

    static func distanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ) -> Double {
        // 좌표가 NaN/무한대면 거리를 알 수 없다 — 지구 반대편 값으로 취급해 어떤 반경 판정도 통과하지 않게 한다.
        guard lat1.isFinite, lng1.isFinite, lat2.isFinite, lng2.isFinite else { return Geo.unknownDistanceM }
        let dLat = (lat2 - lat1) * .pi / 180
        let dLng = (lng2 - lng1) * .pi / 180
        // 반올림 오차로 1 을 살짝 넘으면 sqrt(1 - a) 가 NaN 이 된다 — 0...1 로 가둔다.
        let a = min(1, max(0, sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180)
            * sin(dLng / 2) * sin(dLng / 2)))
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    static func distanceMeters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        distanceMeters(lat1: a.latitude, lng1: a.longitude, lat2: b.latitude, lng2: b.longitude)
    }

    /// 거리(미터) 표기 — 1km 이상이면 소수 1자리 km, 그 미만이면 정수 m. (예: 1234m → "1.2km", 340m → "340m")
    /// ⚠️ Android `DiaryMap.formatDistance` 와 규칙이 같아야 한다(표기 파리티).
    static func formatDistance(_ meters: Double) -> String {
        meters >= 1000
            ? String(format: "%.1fkm", meters / 1000)
            : "\(meters.isFinite ? Int(max(0, meters).rounded()) : 0)m"
    }
}
