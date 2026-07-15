import SwiftUI

/// 디자인 토큰. (Android `Color.kt` 와 값 1:1 동기화 — 한쪽 바꾸면 반대쪽도)
enum Theme {
    static let background = Color(hex: 0x0D0D0D)     // Bg
    static let surface = Color(hex: 0x1A1A1A)        // Surface1
    static let surfaceAlt = Color(hex: 0x242424)     // Surface2
    static let outline = Color(hex: 0x2E2E2E)        // Outline
    static let mint = Color(hex: 0x6EE7B7)           // Mint(브랜드 강조)
    static let mintBlue = Color(hex: 0x3B82F6)       // MintBlue(민트→블루 그라데이션 짝)
    static let accentRed = Color(hex: 0xFF4F4F)      // AccentRed
    static let textPrimary = Color(hex: 0xF0F0F0)    // TextPrimary
    static let textSecondary = Color(hex: 0x8A8A8A)  // TextSub
    static let textFaint = Color.white.opacity(0.4)
}

extension Color {
    /// 0xRRGGBB 정수로 Color 생성.
    init(hex: UInt32, alpha: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    /// other 쪽으로 t(0..1) 만큼 선형 보간. (Android lerp 대응)
    func blended(with other: Color, fraction t: Double) -> Color {
        let a = UIColor(self), b = UIColor(other)
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        let f = CGFloat(t)
        return Color(.sRGB,
                     red: Double(ar + (br - ar) * f),
                     green: Double(ag + (bg - ag) * f),
                     blue: Double(ab + (bb - ab) * f),
                     opacity: Double(aa + (ba - aa) * f))
    }
}
