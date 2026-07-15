import SwiftUI

/// 지도 위 앱 크롬(상단바/글쓰기 FAB) 표시 여부 단일 소스.
/// 글로브(3D 지구)나 몰입 모드처럼 지도를 가득 채우는 상태에서는 크롬을 숨긴다.
/// (Android `MapUiState.mapOnly` 대응 — 글로브/몰입 진입 시 Scaffold 의 topBar/FAB 를 감춘다.)
@MainActor
final class MapChromeState: ObservableObject {
    static let shared = MapChromeState()
    private init() {}

    /// true 면 상단바·FAB 를 숨긴다(글로브 진입).
    @Published var hidden = false

    /// 몰입(지도만 보기) — 지도 위 모든 버튼/필터까지 숨기고 하단 중앙 X 로 복귀.
    /// (Android `MapUiState.mapOnly` 대응)
    @Published var mapOnly = false

    /// 상단바/FAB 를 숨겨야 하는 상태(글로브 또는 몰입).
    var chromeHidden: Bool { hidden || mapOnly }
}
