import SwiftUI

/// 다이어리 별 마커의 종류(모양)×색상 팔레트.
/// Android `core.designsystem.StarStyle` 의 Swift 포팅 — 정의를 양쪽이 공유한다.
///
/// - 종류(starType 0..8): 0~4 별/스파클, 5~8 창의적 형태(꽃·보석·초승달·행성).
/// - 색상(starColor 0..20): 0~15 단색 / 16~20 2색 그라데이션.
enum StarStyle {
    static let typeCount = 9
    static let colorCount = 21
    private static let gradStart = 16

    /// 16색 단색(흰색 30% 혼합으로 밝게).
    private static let solidsRaw: [UInt32] = [
        0xFFFFFF, 0xFFD54F, 0xFF8A65, 0xFF5252, 0xF48FB1, 0xCE93D8,
        0x9575CD, 0x64B5F6, 0x4DD0E1, 0x6EE7B7, 0xAED581, 0xA1887F,
        0xE040FB, 0x448AFF, 0x00E676, 0xFFAB00,
    ]

    static let palette: [Color] = solidsRaw.map {
        Color(hex: $0).blended(with: .white, fraction: 0.30)
    }

    /// 2색 그라데이션(인덱스 16부터).
    static let gradients: [(Color, Color)] = [
        (Color(hex: 0xFF6FD8), Color(hex: 0x8E7BFF)), // 16 오로라
        (Color(hex: 0x43E97B), Color(hex: 0x38F9D7)), // 17 에메랄드 오로라
        (Color(hex: 0xFFD86F), Color(hex: 0xFB6F6F)), // 18 석양
        (Color(hex: 0x5EE7FF), Color(hex: 0x5B7CFF)), // 19 빙하
        (Color(hex: 0x101010), Color(hex: 0xFFFFFF)), // 20 흑백(밤→여명)
    ]

    static func isGradient(_ index: Int) -> Bool { index >= gradStart && index < colorCount }

    static func gradient(_ index: Int) -> (Color, Color)? {
        guard isGradient(index) else { return nil }
        return gradients[index - gradStart]
    }

    /// 대표(단색) 색 — 그라데이션이면 시작색.
    static func color(_ index: Int) -> Color {
        let i = min(max(index, 0), colorCount - 1)
        return i >= gradStart ? gradients[i - gradStart].0 : palette[i]
    }

    /// 채우기 스타일 — 단색/그라데이션을 공용으로 다루기 위한 묶음.
    static func fill(_ index: Int) -> LinearGradient {
        if let g = gradient(index) {
            return LinearGradient(colors: [g.0, g.1], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        let c = color(index)
        return LinearGradient(colors: [c, c], startPoint: .top, endPoint: .bottom)
    }
}
