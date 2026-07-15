import SwiftUI

/// 지도 위 앱 크롬(상단바/글쓰기 FAB) 표시 여부 단일 소스.
/// 글로브(3D 지구)나 몰입 모드처럼 지도를 가득 채우는 상태에서는 크롬을 숨긴다.
/// (Android `MapUiState.mapOnly` 대응 — 글로브/몰입 진입 시 Scaffold 의 topBar/FAB 를 감춘다.)
@MainActor
final class MapChromeState: ObservableObject {
    static let shared = MapChromeState()
    private init() {}

    /// true 면 상단바·FAB 를 숨긴다(글로브/몰입 진입).
    @Published var hidden = false
}
