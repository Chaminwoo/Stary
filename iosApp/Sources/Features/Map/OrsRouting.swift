import CoreLocation
import Foundation

/// OpenRouteService 도보 길찾기 (Android `feature.map.OrsRouting` 패리티).
///
/// - 무료 티어(키 발급 무료). 키는 Info.plist 의 `ORS_API_KEY` 로 주입(코드 하드코딩 금지).
///   Info.plist 에 키가 없거나 placeholder 면 `isConfigured == false` → 호출을 건너뛴다.
/// - URLSession GET 한 번으로 GeoJSON 경로를 받는다. 좌표는 [lng, lat] 순서.
enum OrsRouting {

    struct Route {
        let coordinates: [CLLocationCoordinate2D]
        let distanceM: Double
        let durationS: Double
    }

    /// Info.plist 에서 읽은 키(placeholder/빈값이면 nil).
    private static var apiKey: String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "ORS_API_KEY") as? String,
              !raw.isEmpty, !raw.hasPrefix("TODO_") else { return nil }
        return raw
    }

    static var isConfigured: Bool { apiKey != nil }

    /// 출발(현위치) → 도착(별) 도보 경로. 실패/미설정 시 nil.
    static func walkingRoute(start: CLLocationCoordinate2D, end: CLLocationCoordinate2D) async -> Route? {
        guard let key = apiKey else { return nil }
        let urlStr = "https://api.openrouteservice.org/v2/directions/foot-walking" +
            "?api_key=\(key)&start=\(start.longitude),\(start.latitude)&end=\(end.longitude),\(end.latitude)"
        guard let url = URL(string: urlStr) else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
            let parsed = try JSONDecoder().decode(ORSResponse.self, from: data)
            guard let feature = parsed.features.first, feature.geometry.coordinates.count >= 2 else { return nil }
            let coords = feature.geometry.coordinates.compactMap { pair -> CLLocationCoordinate2D? in
                guard pair.count >= 2 else { return nil }
                return CLLocationCoordinate2D(latitude: pair[1], longitude: pair[0])
            }
            return Route(coordinates: coords,
                         distanceM: feature.properties?.summary?.distance ?? 0,
                         durationS: feature.properties?.summary?.duration ?? 0)
        } catch {
            return nil
        }
    }

    // MARK: - GeoJSON 응답 모델(필요한 필드만)
    private struct ORSResponse: Decodable { let features: [ORSFeature] }
    private struct ORSFeature: Decodable { let geometry: ORSGeometry; let properties: ORSProperties? }
    private struct ORSGeometry: Decodable { let coordinates: [[Double]] }
    private struct ORSProperties: Decodable { let summary: ORSSummary? }
    private struct ORSSummary: Decodable { let distance: Double; let duration: Double }
}
