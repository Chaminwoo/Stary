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

    /// 그릴 수 있는 모양 번호 — 내장(0..20)이거나 `StarCatalog` 가 배운 번호면 그대로, 모르는 번호는 기본 별(1, 5꼭지).
    /// Android `StarStyle.safeType` 패리티(예전엔 범위로 잘라 모르는 번호가 엉뚱한 내장 모양이 됐다 — 2026-10-22).
    static func safeType(_ type: Int) -> Int {
        if (0..<typeCount).contains(type) || StarCatalog.shared.hasShape(type) { return type }
        return type < 0 ? 0 : 1
    }

    /// 그릴 수 있는 색 번호 — 내장(0..20)이거나 `StarCatalog` 가 배운 번호면 그대로, 모르는 번호는 화이트(0).
    static func safeColor(_ index: Int) -> Int {
        (0..<colorCount).contains(index) || StarCatalog.shared.hasColor(index) ? index : 0
    }

    static func isGradient(_ index: Int) -> Bool {
        (index >= gradStart && index < colorCount) || (StarCatalog.shared.rawColors(index)?.count ?? 0) >= 2
    }

    /// 그라데이션 양 끝 2색(없으면 nil). DB 정의 3색이면 처음·끝 — 3색 전부는 `colors(_:)`.
    static func gradient(_ index: Int) -> (Color, Color)? {
        if let raw = StarCatalog.shared.rawColors(index) {
            return raw.count >= 2 ? (Color(hex: raw[0]), Color(hex: raw[raw.count - 1])) : nil
        }
        guard isGradient(index) else { return nil }
        return gradients[index - gradStart]
    }

    /// 채우기 정지점 전부(단색이면 1개) — DB 정의 색은 흰색을 섞지 않고 그대로.
    static func colors(_ index: Int) -> [Color] {
        if let raw = StarCatalog.shared.rawColors(index) { return raw.map { Color(hex: $0) } }
        if let g = gradient(index) { return [g.0, g.1] }
        return [color(index)]
    }

    /// 대표(단색) 색 — 그라데이션이면 시작색.
    static func color(_ index: Int) -> Color {
        if let raw = StarCatalog.shared.rawColors(index) { return Color(hex: raw[0]) }
        let i = min(max(safeColor(index), 0), colorCount - 1)
        return i >= gradStart ? gradients[i - gradStart].0 : palette[i]
    }

    /// 3D 글로브 별빛 색(0..1 RGB) — Android `GlobeRenderer.lightColorOf` 와 같은 식. SwiftUI/UIKit 을 거치지 않는 **순수 계산**이라
    /// 백그라운드(글로브 스프라이트 빌드)에서 불러도 된다. 단색은 흰색 30% 혼합(palette 와 동일), 그라데이션은 두 색의 평균,
    /// 마지막에 γ 1.5 로 채도를 살짝 올려 흰 심지와 섞여도 색이 남게 한다.
    static func lightRGB(_ index: Int) -> (r: Float, g: Float, b: Float) {
        func comps(_ hex: UInt32) -> (Float, Float, Float) {
            (Float((hex >> 16) & 0xFF) / 255, Float((hex >> 8) & 0xFF) / 255, Float(hex & 0xFF) / 255)
        }
        // DB 정의 색 — 정지점 평균(StarCatalog 는 락으로 보호돼 백그라운드에서 불러도 된다).
        if let raw = StarCatalog.shared.rawColors(index) {
            var r: Float = 0, g: Float = 0, b: Float = 0
            for h in raw { let (cr, cg, cb) = comps(h); r += cr; g += cg; b += cb }
            let n = Float(raw.count)
            return (powf(r / n, 1.5), powf(g / n, 1.5), powf(b / n, 1.5))
        }
        let i = min(max(safeColor(index), 0), colorCount - 1)
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
        let cs = colors(index)
        if cs.count >= 2 {
            return LinearGradient(colors: cs, startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        let c = color(index)
        return LinearGradient(colors: [c, c], startPoint: .top, endPoint: .bottom)
    }
}
