import Foundation

/// 지도 필터 조합(MapScreen 필터 상태와 1:1). Android `core/util/MapFilterStore.kt` 의 `MapFilters` 패리티.
struct MapFilters: Codable, Equatable {
    var unviewedOnly = false
    var friendsOnly = false
    var myOnly = false
    var unlockedOnly = false
    var selectedFriendIds: Set<String> = []
    /// nil=전체 기간, 0=오늘, N=최근 N일.
    var periodDays: Int?
}

/// 지도 필터 영속(2026-10-07) — 앱을 껐다 켜도 마지막 필터가 그대로 걸려 있게.
///
/// **계정별**로 저장한다(키 = uid, 비로그인 = "guest"). 친구 선택은 uid 목록이라 다른 계정에선 의미가 없고,
/// 필터 취향도 계정마다 다를 수 있어서다. 다이얼 펼침·피커 표시 같은 일시 UI 상태는 저장하지 않는다.
enum MapFilterStore {
    private static func key(_ uid: String?) -> String { "map_filters_\(uid ?? "guest")" }

    static func load(uid: String?) -> MapFilters {
        guard let data = UserDefaults.standard.data(forKey: key(uid)),
              let filters = try? JSONDecoder().decode(MapFilters.self, from: data) else { return MapFilters() }
        return filters
    }

    static func save(_ filters: MapFilters, uid: String?) {
        guard let data = try? JSONEncoder().encode(filters) else { return }
        UserDefaults.standard.set(data, forKey: key(uid))
    }
}
