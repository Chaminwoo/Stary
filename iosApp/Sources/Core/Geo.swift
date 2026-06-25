import CoreLocation
import Foundation

/// 좌표 거리 계산(Haversine). KMP `GeoUtils` 와 동일한 의미.
enum Geo {
    private static let earthRadiusM = 6_371_000.0

    static func distanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLng = (lng2 - lng1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180)
            * sin(dLng / 2) * sin(dLng / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    static func distanceMeters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        distanceMeters(lat1: a.latitude, lng1: a.longitude, lat2: b.latitude, lng2: b.longitude)
    }
}
