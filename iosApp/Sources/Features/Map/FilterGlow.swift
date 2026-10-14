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
}

/// 필터 빛의 상태 — 지도(MapLibreView 코디네이터, 빛의 고리 재생)가 쓰고 `FilterGlowOverlay` 가 그린다.
/// 링이 퍼지는 동안엔 **링 안쪽(지나간 자리)만 새 색**, 바깥은 이전 색. 링이 다 퍼지면 새 색으로 확정한다.
/// (Android 는 DiaryMap 의 `glowTint` + `FilterWaveFrame.insideTint/outsideTint`)
final class FilterWaveState: ObservableObject {
    static let shared = FilterWaveState()
    private init() {}

    /// 확정된 필터 빛 색(0xRRGGBB, nil = 빛 없음).
    @Published var committedHex: UInt32?
    /// 빛의 고리가 퍼지는 중인가.
    @Published var active = false
    /// 퍼지는 중인 링 — 지도 뷰 좌표의 꼭짓점(매 프레임 갱신). nil = 아직 링 없음.
    @Published var ring: [CGPoint]?
    /// 링 안(새 필터 색) / 링 밖(이전 색) / 링(테두리·검은 구체 테) 색.
    var insideHex: UInt32?
    var outsideHex: UInt32?
    var ringHex: UInt32 = MapFilterKind.neutralHex

    /// 포털 성운 — 세기(0..1, 퍼지는 동안 1 → 화면을 다 지나간 뒤 가라앉음) / 구름 얼룩 / 잔별 / 시작점 검은 구체.
    /// 코디네이터가 매 프레임 `ring` 보다 먼저 채운다(`ring` 발행이 다시 그리기를 일으킨다).
    struct Blob { let point: CGPoint; let radius: CGFloat; let kind: Int }
    struct Spark { let point: CGPoint; let radius: CGFloat; let alpha: Double }
    struct Orb { let point: CGPoint; let radius: CGFloat; let alpha: Double }
    var portal: Double = 0
    var blobs: [Blob] = []
    var sparks: [Spark] = []
    var orb: Orb?
    /// 첫 표시 때 지금 필터 색으로 한 번 맞췄는지.
    var initialized = false
}

/// 필터가 걸려 있는 동안 지도에 도는 **은은한 그라데이션 빛** — 예전 여명·황혼 오버레이(SkyOverlay, 삭제)처럼
/// 화면 아래에서 차오르되, 색은 필터의 색이고 옅은 빛 덩이가 화면 둘레를 천천히 돈다. 장식 전용(`allowsHitTesting(false)`).
/// 빛의 고리가 퍼지는 동안엔 링 안쪽부터 새 색으로 깔린다(`FilterWaveState`).
/// (Android `FilterGlowOverlay` 패리티 — 같은 수치)
struct FilterGlowOverlay: View {
    /// 지금 필터 색(0xRRGGBB). nil = 필터 없음. 보통은 빛의 고리가 끝날 때 확정되고, 이 값은 연출이 없을 때의 안전망이다.
    var tintHex: UInt32?
    @ObservedObject private var wave = FilterWaveState.shared

    var body: some View {
        // 빛이 없으면 타임라인을 멈춘다.
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: wave.committedHex == nil && !wave.active)) { timeline in
            let phase = timeline.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: FilterGlowFx.orbitSeconds)
                / FilterGlowFx.orbitSeconds * 2 * Double.pi
            let active = wave.active
            let ring = wave.ring
            let inside = wave.insideHex
            let outside = wave.outsideHex
            let committed = wave.committedHex
            let portal = wave.portal
            let blobs = wave.blobs
            let sparks = wave.sparks
            let orb = wave.orb
            let ringHex = wave.ringHex
            Canvas { ctx, size in
                guard size.width > 0, size.height > 0 else { return }
                if active {
                    if let ring, ring.count > 2 {
                        let path = Path { p in
                            p.addLines(ring)
                            p.closeSubpath()
                        }
                        if let hex = inside {
                            var inner = ctx
                            inner.clip(to: path)
                            Self.drawGlow(&inner, size: size, hex: hex, phase: phase)
                            // 거품 안 = 포털 성운(퍼지는 동안 짙게, 화면을 다 지나간 뒤 은은한 필터 빛으로 가라앉는다)
                            if portal > 0.01 {
                                Self.drawPortal(&inner, size: size, hex: hex, portal: portal, blobs: blobs, sparks: sparks)
                            }
                        }
                        if let hex = outside {
                            var outer = ctx
                            outer.clip(to: path, options: .inverse)
                            Self.drawGlow(&outer, size: size, hex: hex, phase: phase)
                        }
                    } else if let hex = outside {
                        var all = ctx
                        Self.drawGlow(&all, size: size, hex: hex, phase: phase)
                    }
                } else if let hex = committed {
                    var all = ctx
                    Self.drawGlow(&all, size: size, hex: hex, phase: phase)
                }
                // 시작점의 검은 구체 — 바깥 보랏빛 테 + 검은 속 + 작은 반짝임
                if active, let orb {
                    let rim = Color(uiColor: UIColor(rgbHex: ringHex).mixedWithWhite(0.3))
                    let g = Gradient(stops: [
                        .init(color: Color(hex: 0x05030C).opacity(0.95 * orb.alpha), location: 0),
                        .init(color: Color(hex: 0x0B0618).opacity(0.92 * orb.alpha), location: 0.62),
                        .init(color: rim.opacity(0.55 * orb.alpha), location: 0.82),
                        .init(color: rim.opacity(0), location: 1),
                    ])
                    let rect = CGRect(x: orb.point.x - orb.radius, y: orb.point.y - orb.radius,
                                      width: orb.radius * 2, height: orb.radius * 2)
                    ctx.fill(Path(ellipseIn: rect),
                             with: .radialGradient(g, center: orb.point, startRadius: 0, endRadius: orb.radius))
                    let sr = orb.radius * 0.09
                    let sc = CGPoint(x: orb.point.x + orb.radius * 0.18, y: orb.point.y - orb.radius * 0.12)
                    ctx.fill(Path(ellipseIn: CGRect(x: sc.x - sr, y: sc.y - sr, width: sr * 2, height: sr * 2)),
                             with: .color(.white.opacity(0.85 * orb.alpha)))
                }
            }
        }
        .allowsHitTesting(false)
        .onAppear {
            if !wave.initialized {
                wave.initialized = true
                wave.committedHex = tintHex
            }
        }
        .onChange(of: tintHex) { hex in
            // 안전망: 빛의 고리 없이 필터 색만 바뀐 경우(지도 준비 전 등) — 잠시 뒤에도 연출이 없으면 확정 색을 맞춘다.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                let state = FilterWaveState.shared
                if !state.active { state.committedHex = hex }
            }
        }
    }

    /// 포털 성운 — 짙은 남보라 바탕 + 구름 얼룩·어두운 반점·청록 빛줄기 + 잔별(Android drawPortal 과 같은 색·알파).
    private static func drawPortal(_ ctx: inout GraphicsContext, size: CGSize, hex: UInt32, portal: Double,
                                   blobs: [FilterWaveState.Blob], sparks: [FilterWaveState.Spark]) {
        let tint = UIColor(rgbHex: hex)
        func mix(_ a: UIColor, _ b: UIColor, _ t: CGFloat) -> UIColor {
            var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
            var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
            a.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
            b.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
            return UIColor(red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t, alpha: 1)
        }
        let deep = mix(tint, UIColor(rgbHex: 0x120A2A), 0.58)
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(uiColor: deep).opacity(0.30 * portal)))
        for b in blobs where b.radius >= 1 {
            let (c, a): (UIColor, Double) = {
                switch b.kind {
                case 0: return (mix(tint, UIColor(rgbHex: 0x1A0E3A), 0.35), 0.30)
                case 1: return (tint.mixedWithWhite(0.15), 0.16)
                case 2: return (UIColor(rgbHex: 0x07040F), 0.34)
                default: return (mix(tint, UIColor(rgbHex: 0x3FE0C8), 0.6), 0.12)
                }
            }()
            let color = Color(uiColor: c)
            let g = Gradient(stops: [
                .init(color: color.opacity(a * portal), location: 0),
                .init(color: color.opacity(0), location: 1),
            ])
            let rect = CGRect(x: b.point.x - b.radius, y: b.point.y - b.radius, width: b.radius * 2, height: b.radius * 2)
            ctx.fill(Path(ellipseIn: rect), with: .radialGradient(g, center: b.point, startRadius: 0, endRadius: b.radius))
        }
        let star = Color(uiColor: tint.mixedWithWhite(0.75))
        for s in sparks {
            let rect = CGRect(x: s.point.x - s.radius, y: s.point.y - s.radius, width: s.radius * 2, height: s.radius * 2)
            ctx.fill(Path(ellipseIn: rect), with: .color(star.opacity(s.alpha * portal)))
        }
    }

    /// 필터 빛 한 장 — 아래에서 차오르는 빛 + 위쪽 맞빛 + 화면 둘레를 도는 빛 덩이.
    private static func drawGlow(_ ctx: inout GraphicsContext, size: CGSize, hex: UInt32, phase: Double) {
        let w = Double(size.width)
        let h = Double(size.height)
        let base = Color(hex: hex)
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
            .init(color: Color(uiColor: UIColor(rgbHex: hex).mixedWithWhite(0.2)).opacity(0.13), location: 0),
            .init(color: base.opacity(0), location: 1),
        ])
        ctx.fill(full, with: .radialGradient(blob, center: center, startRadius: 0, endRadius: CGFloat(radius)))
    }
}
