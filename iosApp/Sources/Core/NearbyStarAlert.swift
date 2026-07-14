import CoreLocation
import Foundation

/// 근처 미조회 별 발견 알림(체크리스트 33) — 앱 사용 중 실제 위치 fix 가 갱신될 때
/// 반경 [AppConfig.nearbyAlertRadiusM] 안의 "아직 안 본 남의 별" 중 가장 가까운 1개를
/// 상단 인앱 배너로 알린다. 탭하면 지도에서 그 별로 포커스(카메라+파동).
/// (Android `core/util/NearbyStarAlert` 패리티 — 빈도 제한 규칙 동일.)
///
/// 빈도 제한:
///  - 같은 별은 평생 1회만(기기 로컬 영구 기록)
///  - 하루 상한 [AppConfig.nearbyAlertDailyLimit]
///  - 알림 간 최소 간격 [AppConfig.nearbyAlertMinIntervalMs]
@MainActor
enum NearbyStarAlert {
    private static let alertedKey = "stary_nearby_alerted_ids"
    private static let dayKey = "stary_nearby_day"
    private static let dayCountKey = "stary_nearby_day_count"
    private static var lastAlertMs: Int64 = 0

    /// 근처 미조회 별 검사 — 조건을 만족하는 가장 가까운 별 1개만 알린다.
    /// [diaries] 는 지도에 보이는 목록(공개범위 필터 반영분)을 그대로 받는다.
    static func check(
        me: CLLocationCoordinate2D,
        diaries: [Diary],
        viewedIds: Set<String>,
        myUserId: String?
    ) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard now - lastAlertMs >= AppConfig.nearbyAlertMinIntervalMs else { return }

        let defaults = UserDefaults.standard
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyyMMdd"
        fmt.locale = Locale(identifier: "en_US_POSIX")
        let today = fmt.string(from: Date())
        let dayCount = defaults.string(forKey: dayKey) == today ? defaults.integer(forKey: dayCountKey) : 0
        guard dayCount < AppConfig.nearbyAlertDailyLimit else { return }

        let alerted = Set(defaults.stringArray(forKey: alertedKey) ?? [])
        let candidate = diaries
            .compactMap { diary -> (Diary, Double)? in
                guard let id = diary.id, !id.isEmpty,
                      !alerted.contains(id), !viewedIds.contains(id),
                      !diary.userId.isEmpty, diary.userId != myUserId,
                      diary.latitude != 0 || diary.longitude != 0 else { return nil }
                let dist = Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude,
                                              lat2: diary.latitude, lng2: diary.longitude)
                guard dist <= AppConfig.nearbyAlertRadiusM else { return nil }
                return (diary, dist)
            }
            .min { $0.1 < $1.1 }
        guard let (diary, distance) = candidate, let diaryId = diary.id else { return }

        lastAlertMs = now
        defaults.set(Array(alerted.union([diaryId])), forKey: alertedKey)
        defaults.set(today, forKey: dayKey)
        defaults.set(dayCount + 1, forKey: dayCountKey)

        InAppBanner.shared.show(
            title: LocaleManager.shared.t(.nearbyStarTitle),
            body: String(format: LocaleManager.shared.t(.nearbyStarBody), Int(distance)),
            kind: .notification,
            key: "nearby_\(diaryId)"
        ) {
            MapFocusStore.shared.request(diaryId: diaryId)
        }
    }
}
