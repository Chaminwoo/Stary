import SwiftUI

/// 밤하늘 톤 디자인 토큰. (Android designsystem 의 Color 대응 최소본)
enum Theme {
    static let background = Color(hex: 0x0B0E1A)
    static let surface = Color(hex: 0x161A2B)
    static let surfaceAlt = Color(hex: 0x1F2440)
    static let mint = Color(hex: 0x6EE7B7)        // 앱 포인트
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.65)
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
