import SwiftUI

/// 다이어리 별 마커의 종류(모양)×색상 팔레트.
/// Android `core.designsystem.StarStyle` 의 Swift 포팅 — 정의를 양쪽이 공유한다.
///
/// - 종류(starType 0..20): 0~4 별/스파클, 5~8 창의적 형태(꽃·보석·초승달·행성), 9~20 업적 보상 형태(2026-09-26).
/// - 색상(starColor 0..20): 0~15 단색 / 16~20 2색 그라데이션.
enum StarStyle {
    static let typeCount = 21
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

    /// 2색 그라데이션 원본 hex(인덱스 16부터) — `gradients`(Color)와 `lightRGB`(순수 계산)가 함께 쓴다.
    private static let gradientsRaw: [(UInt32, UInt32)] = [
        (0xFF6FD8, 0x8E7BFF), // 16 오로라
        (0x43E97B, 0x38F9D7), // 17 에메랄드 오로라
        (0xFFD86F, 0xFB6F6F), // 18 석양
        (0x5EE7FF, 0x5B7CFF), // 19 빙하
        (0x101010, 0xFFFFFF), // 20 흑백(밤→여명)
    ]

    /// 2색 그라데이션(인덱스 16부터).
    static let gradients: [(Color, Color)] = gradientsRaw.map { (Color(hex: $0.0), Color(hex: $0.1)) }

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

    /// 3D 글로브 별빛 색(0..1 RGB) — Android `GlobeRenderer.lightColorOf` 와 같은 식. SwiftUI/UIKit 을 거치지 않는 **순수 계산**이라
    /// 백그라운드(글로브 스프라이트 빌드)에서 불러도 된다. 단색은 흰색 30% 혼합(palette 와 동일), 그라데이션은 두 색의 평균,
    /// 마지막에 γ 1.5 로 채도를 살짝 올려 흰 심지와 섞여도 색이 남게 한다.
    static func lightRGB(_ index: Int) -> (r: Float, g: Float, b: Float) {
        let i = min(max(index, 0), colorCount - 1)
        func comps(_ hex: UInt32) -> (Float, Float, Float) {
            (Float((hex >> 16) & 0xFF) / 255, Float((hex >> 8) & 0xFF) / 255, Float(hex & 0xFF) / 255)
        }
        let r: Float, g: Float, b: Float
        if i >= gradStart {
            let (a, c) = gradientsRaw[i - gradStart]
            let (ar, ag, ab) = comps(a)
            let (cr, cg, cb) = comps(c)
            r = (ar + cr) / 2; g = (ag + cg) / 2; b = (ab + cb) / 2
        } else {
            let (sr, sg, sb) = comps(solidsRaw[i])
            r = sr + (1 - sr) * 0.30; g = sg + (1 - sg) * 0.30; b = sb + (1 - sb) * 0.30
        }
        return (powf(r, 1.5), powf(g, 1.5), powf(b, 1.5))
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
