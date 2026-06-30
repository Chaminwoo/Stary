import SwiftUI

/// 지도에 "특정 다이어리로 카메라 이동(+도보 길찾기)" 을 요청하는 전역 상태.
/// Android `core.util.MapFocusState` 의 iOS 패리티.
///
/// 친구 별을 탭(타인 프로필의 별 목록)하거나 핀한 내 별을 탭하면 여기에 요청을 넣고,
/// `MainTabView` 가 지도 탭으로 전환하며 `MapScreen` 이 그 좌표로 카메라를 옮긴다.
/// `withRoute` 면 현위치→그 별까지 도보 경로(OpenRouteService)를 띄운다.
/// (메인 스레드(SwiftUI)에서만 변경되므로 actor 격리는 두지 않는다 — 비격리 콜백에서도 호출 가능.)
final class MapFocusStore: ObservableObject {
    static let shared = MapFocusStore()
    private init() {}

    /// 포커스 대상 다이어리 id (nil = 없음).
    @Published private(set) var pendingDiaryId: String?
    /// true 면 포커스 후 그 별까지 도보 길찾기 경로를 띄운다(친구 별 탭).
    @Published private(set) var withRoute = false

    func request(diaryId: String, withRoute: Bool = false) {
        self.withRoute = withRoute
        // 같은 id 재요청도 onChange 가 잡히도록 먼저 nil 로 비운 뒤 설정.
        pendingDiaryId = nil
        pendingDiaryId = diaryId
    }

    func consume() {
        pendingDiaryId = nil
        withRoute = false
    }
}

/// 5-탭(지도/목록/올리기/친구/프로필) 선택을 전역에서 전환하기 위한 라우터.
/// (프로필의 떠다니는 아이콘 탭이나 길찾기 요청이 지도/친구 탭으로 점프할 때 사용.)
final class TabRouter: ObservableObject {
    static let shared = TabRouter()
    private init() {}

    static let map = 0
    static let list = 1
    static let upload = 2
    static let friends = 3
    static let profile = 4

    @Published var selected = TabRouter.map

    func go(_ tab: Int) { selected = tab }
}
