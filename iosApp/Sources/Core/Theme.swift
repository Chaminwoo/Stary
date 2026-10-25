import SwiftUI

/// 디자인 토큰(코스믹 네이비). Android `Color.kt` 의 `StaryColors` 와 값 1:1 동기화 — 한쪽 바꾸면 반대쪽도.
/// 앱 전체 틴트(`StaryApp` 의 `.tint`)도 [navyAccent] 라 따로 색을 주지 않은 버튼·토글·로딩도 이 색을 따른다.
enum Theme {
    static let background = Color(hex: 0x0E1018)     // Bg 바탕
    static let surface = Color(hex: 0x181C2A)        // Surface 면
    static let surfaceAlt = Color(hex: 0x1E2334)     // Raised 높은 면
    static let outline = Color(hex: 0x2B3247)        // Line 선
    static let mint = Color(hex: 0x6EE7B7)           // Mint(브랜드 강조)
    static let mintBlue = Color(hex: 0x3B82F6)       // MintBlue(민트→블루 그라데이션 짝)
    static let accentRed = Color(hex: 0xFF6B6B)      // Danger 위험
    static let navyAccent = Color(hex: 0x9FB3E8)     // Accent 강조(별빛 남색)
    static let navyDeep = Color(hex: 0x1E3A8A)       // 그라데이션 짝(파랑→남색)
    static let textPrimary = Color(hex: 0xF2F4FA)    // Text 글자
    static let textSecondary = Color(hex: 0x8A92A6)  // Muted 흐린 글자
    static let textFaint = Color.white.opacity(0.4)

    /// 볼록(엠보스) 테두리 색 — 좌상단 밝은 하이라이트에서 우하단으로 갈수록 짙은 남색(사선 — 정수리 일직선
    /// 하이라이트가 어색하다는 피드백, 2026-07-18). 지도 원형 버튼과 주 버튼([StaryButton])이 같이 쓴다.
    /// (Android `Modifier.raisedCosmicBorder()` 패리티 — 색/알파/방향 동일)
    static let cosmicEdge = LinearGradient(stops: [
        .init(color: Color(hex: 0x9FB3E8).opacity(0.45), location: 0.00),
        .init(color: Color(hex: 0x3A4570).opacity(0.35), location: 0.45),
        .init(color: Color(hex: 0x10142B).opacity(0.30), location: 1.00),
    ], startPoint: .topLeading, endPoint: .bottomTrailing)
}

extension View {
    /// 지도 원형 버튼용 볼록(엠보스) 테두리([Theme.cosmicEdge]).
    func raisedCosmicBorder(lineWidth: CGFloat = 0.75) -> some View {
        overlay(Circle().strokeBorder(Theme.cosmicEdge, lineWidth: lineWidth))
    }

    /// 유리 패널 — 뒤가 흐리게 비치는 남색 판 + 위가 밝은 1pt 테두리. 투명도 줄이기(손쉬운 사용)를 켜면
    /// 시스템이 재질을 불투명하게 바꾼다. (Android 확인 창 GlassFill/GlassEdge · 상단바 Haze 유리와 같은 값)
    func glassPanel(cornerRadius: CGFloat = 12, tint: Color = Theme.glassTint) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return background(tint, in: shape)
            .background(.ultraThinMaterial, in: shape)
            .overlay(shape.strokeBorder(Theme.glassEdge, lineWidth: 1))
    }
}

extension Theme {
    static let glassTint = Color(hex: 0x181E34, alpha: 0.36)
    static let glassEdge = LinearGradient(colors: [Color.white.opacity(0.18), Color.white.opacity(0.06)],
                                          startPoint: .top, endPoint: .bottom)
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
