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
    // 프로필/설정 강조 — 남색 계열(Android profile Accent/Navy 와 값 동기).
    static let navyAccent = Color(hex: 0x9FB3E8)     // 라이트 남색(글씨/틴트, 구 민트 대체)
    static let navyDeep = Color(hex: 0x1E3A8A)       // 그라데이션 짝(파랑→남색)
    static let textPrimary = Color(hex: 0xF0F0F0)    // TextPrimary
    static let textSecondary = Color(hex: 0x8A8A8A)  // TextSub
    static let textFaint = Color.white.opacity(0.4)
}

extension View {
    /// 지도 원형 버튼용 볼록(엠보스) 테두리 — 좌상단 밝은 하이라이트에서 우하단으로 갈수록 짙은
    /// 남색(사선 — 정수리 일직선 하이라이트가 어색하다는 피드백, 2026-07-18).
    /// (Android `Modifier.raisedCosmicBorder()` 패리티 — 색/알파/두께/방향 동일)
    func raisedCosmicBorder(lineWidth: CGFloat = 0.75) -> some View {
        overlay(
            Circle().strokeBorder(
                LinearGradient(stops: [
                    .init(color: Color(hex: 0x9FB3E8).opacity(0.45), location: 0.00),
                    .init(color: Color(hex: 0x3A4570).opacity(0.35), location: 0.45),
                    .init(color: Color(hex: 0x10142B).opacity(0.30), location: 1.00),
                ], startPoint: .topLeading, endPoint: .bottomTrailing),
                lineWidth: lineWidth
            )
        )
    }
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
