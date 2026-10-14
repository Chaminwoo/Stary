import SwiftUI
import UIKit

/// 지도 필터 종류 — **필터마다 다른 색**으로 "빛의 고리"(필터 전환 등장 연출)와 지도에 도는 은은한 빛을 물들인다(2026-10-14).
/// Android `feature/map/screen/FilterGlow.kt` 의 `MapFilterKind` 와 **같은 색**(값 drift 금지).
enum MapFilterKind: Equatable {
    /// 미해금만(보랏빛) · 해금만(금빛) · 친구만(민트) · 나만보기(장밋빛) · 친구 선택(하늘색) · 기간별(산호색)
    case locked, unlocked, friends, mine, pickFriends, period

    /// 0xRRGGBB.
    var hex: UInt32 {
        switch self {
        case .locked: return 0xB69CFF
        case .unlocked: return 0xFFD27A
        case .friends: return 0x7CE8C4
        case .mine: return 0xFF9EC4
        case .pickFriends: return 0x7CC8FF
        case .period: return 0xFFA873
        }
    }

    /// 필터를 모두 껐을 때 퍼지는 링의 색(색 없는 별빛) — Android `FILTER_WAVE_NEUTRAL`.
    static let neutralHex: UInt32 = 0xDCE6FF
}

/// 켜져 있는 필터들 중 "지금 색을 정하는 필터" = **가장 마지막에 켠 것**. 그 필터를 끄면 남은 필터 중 마지막 것.
/// 뷰 body 에서 불러도 되게 같은 입력엔 같은 값을 돌려준다(@State 값이 아니라 참조 객체라 다시 그리기를 일으키지 않는다).
/// Android `FilterKindTracker` 패리티.
final class FilterKindTracker {
    private var prev: [MapFilterKind] = []
    private var latest: MapFilterKind?

    func update(_ active: [MapFilterKind]) -> MapFilterKind? {
        if let added = active.first(where: { !prev.contains($0) }) {
            latest = added
        } else if let current = latest, active.contains(current) {
            // 그대로
        } else {
            latest = active.last
        }
        prev = active
        return latest
    }
}

extension UIColor {
    /// 0xRRGGBB 정수로 UIColor 생성(`Color(hex:)` 의 UIKit 판).
    convenience init(rgbHex hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255,
                  alpha: alpha)
    }

    /// 흰색 쪽으로 섞기(0 = 그대로, 1 = 흰색).
    func mixedWithWhite(_ fraction: CGFloat) -> UIColor {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        let f = min(max(fraction, 0), 1)
        return UIColor(red: r + (1 - r) * f, green: g + (1 - g) * f, blue: b + (1 - b) * f, alpha: a)
    }
}

enum FilterGlowFx {
    /// 은은한 빛이 화면을 한 바퀴 도는 시간(초) — Android `GLOW_ORBIT_SEC`.
    static let orbitSeconds = 16.0
    /// 필터 색이 바뀔 때 섞이는 시간(초).
    static let colorBlend = 0.7
}

/// 필터가 걸려 있는 동안 지도에 도는 **은은한 그라데이션 빛** — 예전 여명·황혼 오버레이(SkyOverlay, 삭제)처럼
/// 화면 아래에서 차오르되, 색은 지금 필터의 색이고 옅은 빛 덩이가 화면 둘레를 천천히 돈다.
/// `tintHex` = nil(필터 없음)이면 서서히 꺼진다. 장식 전용(`allowsHitTesting(false)`).
/// (Android `FilterGlowOverlay` 패리티 — 같은 수치)
struct FilterGlowOverlay: View {
    /// 지금 필터 색(0xRRGGBB). nil = 필터 없음.
    var tintHex: UInt32?

    /// 색 전환(이전 색 → 지금 색) — Canvas 는 SwiftUI 애니메이션이 색을 보간해 주지 않아 직접 섞는다.
    @State private var fromHex: UInt32 = MapFilterKind.neutralHex
    @State private var toHex: UInt32 = MapFilterKind.neutralHex
    @State private var changedAt = Date.distantPast

    var body: some View {
        // 필터가 없으면 타임라인을 멈춘다(꺼지는 페이드는 아래 opacity 애니메이션이 맡는다).
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: tintHex == nil)) { timeline in
            let now = timeline.date
            let from = fromHex
            let to = toHex
            let blend = min(max(now.timeIntervalSince(changedAt) / FilterGlowFx.colorBlend, 0), 1)
            Canvas { ctx, size in
                let w = Double(size.width)
                let h = Double(size.height)
                guard w > 0, h > 0 else { return }
                let base = Self.mix(from, to, blend)
                let phase = now.timeIntervalSinceReferenceDate
                    .truncatingRemainder(dividingBy: FilterGlowFx.orbitSeconds)
                    / FilterGlowFx.orbitSeconds * 2 * Double.pi
                // 숨 쉬듯 아주 조금 밝아졌다 어두워진다
                let breath = 0.88 + 0.12 * sin(phase * 2)
                let full = Path(CGRect(origin: .zero, size: size))

                // 1) 아래에서 차오르는 빛(여명 오버레이와 같은 모양)
                let rise = Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: base.opacity(0.05 * breath), location: 0.50),
                    .init(color: base.opacity(0.13 * breath), location: 0.85),
                    .init(color: base.opacity(0.20 * breath), location: 1),
                ])
                ctx.fill(full, with: .linearGradient(rise, startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

                // 2) 위쪽 가장자리의 옅은 맞빛
                let top = Gradient(stops: [
                    .init(color: base.opacity(0.07), location: 0),
                    .init(color: .clear, location: 0.22),
                    .init(color: .clear, location: 1),
                ])
                ctx.fill(full, with: .linearGradient(top, startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

                // 3) 화면 둘레를 도는 빛 덩이
                let center = CGPoint(x: w * (0.5 + 0.52 * cos(phase)), y: h * (0.5 + 0.50 * sin(phase)))
                let radius = max(w, h) * 0.72
                let blob = Gradient(stops: [
                    .init(color: Self.mix(from, to, blend, white: 0.2).opacity(0.13), location: 0),
                    .init(color: base.opacity(0), location: 1),
                ])
                ctx.fill(full, with: .radialGradient(blob, center: center, startRadius: 0, endRadius: CGFloat(radius)))
            }
        }
        .opacity(tintHex == nil ? 0 : 1)
        .animation(.easeInOut(duration: 0.8), value: tintHex == nil)
        .allowsHitTesting(false)
        .onAppear {
            if let hex = tintHex {
                fromHex = hex
                toHex = hex
            }
        }
        .onChange(of: tintHex) { hex in
            guard let hex else { return } // 꺼질 땐 마지막 색 그대로 옅어진다
            fromHex = toHex
            toHex = hex
            changedAt = Date()
        }
    }

    /// 두 색(0xRRGGBB)을 섞고, 필요하면 흰색 쪽으로 더 섞는다.
    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double, white: Double = 0) -> Color {
        func channel(_ hex: UInt32, _ shift: UInt32) -> Double { Double((hex >> shift) & 0xFF) / 255 }
        func lerp(_ shift: UInt32) -> Double {
            let v = channel(a, shift) + (channel(b, shift) - channel(a, shift)) * t
            return v + (1 - v) * white
        }
        return Color(red: lerp(16), green: lerp(8), blue: lerp(0))
    }
}
