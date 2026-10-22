import FirebaseFirestore
import SwiftUI

/// 별빛 공방 아이템 그림(2026-10-22) — 공방·뽑기 연출·업적·프로필이 같이 쓴다. Android `feature/starlight/StarlightArt.kt` 와 같은 해석.
///
/// 프레임·오라·배지·칭호는 **DB payload 의 매개변수로 그린다**(앱에 그림을 박지 않는다):
///  - frame : `pattern`(ring · dash · orbit · petals · constellation · aurora) · `colors` · `width`(0~1, 기본 0.06) · `count` · `speed`(한 바퀴 초, 0=정지) · `glow`
///  - aura  : `pattern`(halo · pulse · ring) · `colors` · `speed`
///  - badge : `path`(0~1 SVG, StarCatalog 와 같은 규칙) · `colors`
///  - title : `colors`(1개 = 단색, 2~3개 = 그라데이션) · `shimmer`(빛이 흐름)
/// ⚠️ 모르는 pattern 은 가장 기본형(ring / halo)으로 — 새 버전에 추가된 패턴도 이전 앱에서 깨지지 않게(별 모양 정책과 같다).
enum StarlightArt {
    /// 프레임 테가 차지하는 바깥 띠(지름 대비, 한쪽) — 사진은 그 안쪽에 그린다.
    static let frameInset: CGFloat = 0.13

    static let dust = Color(hex: 0xFFE6A8)
    static let violet = Color(hex: 0xB47CFF)

    /// 등급 색 — 일반 은빛 · 희귀 푸른빛 · 영웅 보랏빛 · 전설 금빛.
    static func rarityColor(_ rarity: String) -> Color {
        switch rarity {
        case "rare": return Color(hex: 0x7CB8FF)
        case "epic": return Color(hex: 0xD59CFF)
        case "legendary": return Color(hex: 0xFFD27A)
        default: return Color(hex: 0xC3CAD6)
        }
    }

    static func colors(_ item: StarlightStore.Item) -> [Color] {
        item.hexColors.compactMap { UInt32($0.dropFirst(), radix: 16).map { Color(hex: $0) } }
    }

    private static let lock = NSLock()
    private static var pathCache: [String: CGPath] = [:]

    /// 0~1 단위 SVG 경로 → CGPath(캐시, 채움 규칙 정규화). 읽지 못하면 nil.
    static func unitPath(_ svg: String?) -> CGPath? {
        guard let svg, !svg.isEmpty else { return nil }
        lock.lock()
        if let p = pathCache[svg] { lock.unlock(); return p }
        lock.unlock()
        guard let p = StarCatalog.parse(svg) else { return nil }
        lock.lock(); pathCache[svg] = p; lock.unlock()
        return p
    }

    /// 네 꼭지 반짝이(프레임·오라·별가루 장식).
    static func sparkle(_ c: CGPoint, _ r: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: c.x, y: c.y - r))
        p.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: c)
        p.closeSubpath()
        return p
    }

    static func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }
}

/// 별가루 아이콘 — 크기가 다른 네 꼭지 반짝이 셋.
struct StardustIcon: View {
    var tint: Color = StarlightArt.dust

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height)
            ctx.fill(StarlightArt.sparkle(CGPoint(x: s * 0.40, y: s * 0.42), s * 0.36), with: .color(tint))
            ctx.fill(StarlightArt.sparkle(CGPoint(x: s * 0.80, y: s * 0.22), s * 0.17), with: .color(tint.opacity(0.85)))
            ctx.fill(StarlightArt.sparkle(CGPoint(x: s * 0.78, y: s * 0.80), s * 0.13), with: .color(tint.opacity(0.7)))
        }
    }
}

/// 성운 상자 — 보랏빛 성운이 소용돌이치는 둥근 상자. glow 0~1(열기 직전 밝아짐).
struct NebulaBoxArt: View {
    var glow: Double = 0
    var spin = true

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let deg = (t / (spin ? 9 : 60) * 360).truncatingRemainder(dividingBy: 360)
            Canvas { ctx, size in
                let s = min(size.width, size.height)
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let g = CGFloat(glow)
                ctx.fill(StarlightArt.circle(c, s * 0.62), with: .radialGradient(
                    Gradient(colors: [StarlightArt.violet.opacity(0.35 + 0.4 * g), .clear]),
                    center: c, startRadius: 0, endRadius: s * 0.62))
                let body = s * 0.36
                ctx.fill(StarlightArt.circle(c, body), with: .conicGradient(
                    Gradient(colors: [Color(hex: 0x3A1C71), Color(hex: 0x7B2FF7), Color(hex: 0xD76D77), Color(hex: 0x3A8DFF), Color(hex: 0x3A1C71)]),
                    center: c, angle: .degrees(deg)))
                ctx.fill(StarlightArt.circle(c, body * 0.75), with: .radialGradient(
                    Gradient(colors: [Color.white.opacity(0.55 + 0.45 * g), Color.white.opacity(0)]),
                    center: c, startRadius: 0, endRadius: body * 0.75))
                // 성운 소용돌이 팔 두 개
                let armRot = -deg * 1.6 * .pi / 180
                for k in 0..<2 {
                    var arm = Path()
                    for i in 0...24 {
                        let f = Double(i) / 24
                        let a = Double(k) * .pi + f * 2.6 + armRot
                        let r = body * (0.15 + 0.8 * f)
                        let p = CGPoint(x: c.x + cos(a) * r, y: c.y + sin(a) * r)
                        if i == 0 { arm.move(to: p) } else { arm.addLine(to: p) }
                    }
                    ctx.stroke(arm, with: .color(.white.opacity(0.35)), style: StrokeStyle(lineWidth: s * 0.025, lineCap: .round))
                }
                ctx.stroke(StarlightArt.circle(c, body), with: .color(Color(hex: 0xE9D8FF).opacity(0.7)), lineWidth: s * 0.018)
            }
        }
    }
}

/// 0~1 SVG 글리프를 색(1개=단색, 2~3개=대각 그라데이션)으로 채운다 — 배지·망원경 아이콘.
struct SvgGlyph: View {
    let svg: String?
    let colors: [Color]
    var glow = false

    var body: some View {
        Canvas { ctx, size in
            guard let unit = StarlightArt.unitPath(svg) else { return }
            let s = min(size.width, size.height)
            let ox = (size.width - s) / 2, oy = (size.height - s) / 2
            let cs = colors.isEmpty ? [Color.white] : colors
            let path = Path(unit).applying(CGAffineTransform(scaleX: s, y: s).concatenating(CGAffineTransform(translationX: ox, y: oy)))
            if glow {
                let k: CGFloat = 1.12
                let g = Path(unit).applying(CGAffineTransform(scaleX: s * k, y: s * k)
                    .concatenating(CGAffineTransform(translationX: ox - s * (k - 1) / 2, y: oy - s * (k - 1) / 2)))
                ctx.fill(g, with: .color(cs[cs.count - 1].opacity(0.28)))
            }
            let shading: GraphicsContext.Shading = cs.count >= 2
                ? .linearGradient(Gradient(colors: cs), startPoint: CGPoint(x: ox, y: oy), endPoint: CGPoint(x: ox + s, y: oy + s))
                : .color(cs[0])
            ctx.fill(path, with: shading)
        }
    }
}

/// 프로필 프레임 테(사진은 안쪽 `frameInset` 띠 안에 따로 그린다).
struct FrameArt: View {
    let item: StarlightStore.Item

    var body: some View {
        let speed = item.num("speed") ?? 0
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let angle = speed > 0 ? (t / speed * 360).truncatingRemainder(dividingBy: 360) : 0
            let wave = (t / 2.4).truncatingRemainder(dividingBy: 1)
            Canvas { ctx, size in draw(&ctx, size, angle: angle, wave: wave) }
        }
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, angle: Double, wave: Double) {
        let pattern = item.str("pattern") ?? "ring"
        var colors = StarlightArt.colors(item)
        if colors.isEmpty { colors = [Color(hex: 0xE9EEF5)] }
        let width = CGFloat(min(max(item.num("width") ?? 0.06, 0.02), 0.12))
        let count = item.int("count") ?? 0
        let s = min(size.width, size.height)
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let w = s * width
        let r = s / 2 - s * StarlightArt.frameInset / 2 - w * 0.2
        let ring: GraphicsContext.Shading = colors.count >= 2
            ? .conicGradient(Gradient(colors: colors + [colors[0]]), center: c, angle: .degrees(angle))
            : .color(colors[0])
        func rotated(_ deg: Double) -> GraphicsContext {
            var g = ctx
            g.translateBy(x: c.x, y: c.y)
            g.rotate(by: .degrees(deg))
            g.translateBy(x: -c.x, y: -c.y)
            return g
        }
        if item.bool("glow") {
            ctx.stroke(StarlightArt.circle(c, r), with: .color(colors[colors.count - 1].opacity(0.22)), lineWidth: w * 2.6)
        }
        switch pattern {
        case "dash":
            let n = CGFloat(count > 0 ? count : 18)
            let circ = 2 * .pi * r
            rotated(angle).stroke(StarlightArt.circle(c, r), with: ring,
                                  style: StrokeStyle(lineWidth: w, lineCap: .round, dash: [circ / n * 0.55, circ / n * 0.45]))
        case "orbit":
            var g = ctx
            g.opacity = 0.75
            g.stroke(StarlightArt.circle(c, r), with: ring, lineWidth: w * 0.45)
            let n = count > 0 ? count : 3
            for i in 0..<n {
                let a = (angle + Double(i) * 360 / Double(n)) * .pi / 180
                let p = CGPoint(x: c.x + cos(a) * r, y: c.y + sin(a) * r)
                ctx.fill(StarlightArt.sparkle(p, w * 1.9), with: .color(colors[i % colors.count]))
            }
        case "petals":
            let g = rotated(angle)
            var g2 = g
            g2.opacity = 0.6
            g2.stroke(StarlightArt.circle(c, r), with: ring, lineWidth: w * 0.4)
            let n = count > 0 ? count : 12
            for i in 0..<n {
                var pg = g
                pg.translateBy(x: c.x, y: c.y)
                pg.rotate(by: .degrees(Double(i) * 360 / Double(n)))
                pg.translateBy(x: -c.x, y: -c.y)
                let tip = CGPoint(x: c.x, y: c.y - r - w * 1.3)
                let base = CGPoint(x: c.x, y: c.y - r + w * 1.1)
                var petal = Path()
                petal.move(to: base)
                petal.addCurve(to: tip, control1: CGPoint(x: base.x + w * 1.3, y: base.y - w * 0.6), control2: CGPoint(x: tip.x + w * 0.9, y: tip.y + w * 0.4))
                petal.addCurve(to: base, control1: CGPoint(x: tip.x - w * 0.9, y: tip.y + w * 0.4), control2: CGPoint(x: base.x - w * 1.3, y: base.y - w * 0.6))
                petal.closeSubpath()
                pg.fill(petal, with: .color(colors[i % colors.count]))
            }
        case "constellation":
            let g = rotated(angle)
            let n = count > 0 ? count : 9
            // 별마다 반지름을 조금씩 흔들어(결정적) 별자리처럼 보이게.
            let pts: [CGPoint] = (0..<n).map { i in
                let a = (Double(i) * 360 / Double(n) + (i % 2 == 0 ? 6 : -4)) * .pi / 180
                let rr = r + w * (i % 3 == 0 ? 0.9 : i % 3 == 1 ? -0.6 : 0.2)
                return CGPoint(x: c.x + cos(a) * rr, y: c.y + sin(a) * rr)
            }
            for i in pts.indices {
                var l = Path()
                l.move(to: pts[i]); l.addLine(to: pts[(i + 1) % n])
                g.stroke(l, with: .color(colors[0].opacity(0.55)), lineWidth: w * 0.28)
            }
            for (i, p) in pts.enumerated() {
                let tw = 0.55 + 0.45 * sin(wave * 2 * .pi + Double(i) * 1.7)
                g.fill(StarlightArt.circle(p, w * 1.5), with: .color(colors[i % colors.count].opacity(0.25 * tw)))
                g.fill(StarlightArt.sparkle(p, w * (i % 3 == 0 ? 1.5 : 1.0)), with: .color(colors[i % colors.count].opacity(0.6 + 0.4 * tw)))
            }
        case "aurora":
            ctx.stroke(StarlightArt.circle(c, r), with: ring, lineWidth: w * 1.25)
            ctx.stroke(StarlightArt.circle(c, r), with: .conicGradient(
                Gradient(colors: [.clear, .white.opacity(0.55), .clear, .clear]), center: c, angle: .degrees(-angle * 1.5)), lineWidth: w * 0.7)
            ctx.stroke(StarlightArt.circle(c, r + w * 1.2),
                       with: .color(colors[0].opacity(0.18 + 0.12 * sin(wave * 2 * .pi))), lineWidth: w * 0.5)
        default: // ring + 모르는 패턴
            ctx.stroke(StarlightArt.circle(c, r), with: ring, lineWidth: w)
        }
    }
}

/// 지도 별 오라 미리보기 — 별 하나와 그 둘레 빛.
struct AuraArt: View {
    let item: StarlightStore.Item
    var starType = 1
    var starColor = 0

    var body: some View {
        let pattern = item.str("pattern") ?? "halo"
        let raw = StarlightArt.colors(item)
        let colors = raw.isEmpty ? [Color(hex: 0xBFD8FF)] : raw
        let speed = min(max(item.num("speed") ?? 2.4, 0.6), 10)
        return GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            ZStack {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                    let t = tl.date.timeIntervalSinceReferenceDate
                    let ph = (t / speed).truncatingRemainder(dividingBy: 1)
                    let breathe = 0.75 + 0.25 * (0.5 + 0.5 * sin(t / speed * 2 * .pi))
                    Canvas { ctx, size in
                        let s = min(size.width, size.height)
                        let c = CGPoint(x: size.width / 2, y: size.height / 2)
                        let base = s * 0.30
                        switch pattern {
                        case "pulse":
                            ctx.fill(StarlightArt.circle(c, base * 1.25), with: .radialGradient(
                                Gradient(colors: [colors[0].opacity(0.55), .clear]), center: c, startRadius: 0, endRadius: base * 1.25))
                            for k in 0..<2 {
                                let f = (ph + Double(k) * 0.5).truncatingRemainder(dividingBy: 1)
                                ctx.stroke(StarlightArt.circle(c, base * (0.6 + f)), with: .color(colors[k % colors.count].opacity((1 - f) * 0.7)), lineWidth: s * 0.018)
                            }
                        case "ring":
                            ctx.fill(StarlightArt.circle(c, base * 1.3), with: .radialGradient(
                                Gradient(colors: [colors[0].opacity(0.45 * breathe), .clear]), center: c, startRadius: 0, endRadius: base * 1.3))
                            var g = ctx
                            g.translateBy(x: c.x, y: c.y)
                            g.rotate(by: .degrees(ph * 360))
                            g.translateBy(x: -c.x, y: -c.y)
                            g.stroke(Path(ellipseIn: CGRect(x: c.x - base * 1.35, y: c.y - base * 0.42, width: base * 2.7, height: base * 0.84)),
                                     with: .conicGradient(Gradient(colors: colors + [colors[0]]), center: c), lineWidth: s * 0.02)
                        default: // halo
                            ctx.fill(StarlightArt.circle(c, base * 1.45), with: .radialGradient(
                                Gradient(colors: colors.map { $0.opacity(0.55 * breathe) } + [.clear]), center: c, startRadius: 0, endRadius: base * 1.45))
                        }
                    }
                }
                StarShape(type: starType)
                    .fill(StarStyle.fill(starColor), style: FillStyle(eoFill: false))
                    .frame(width: side * 0.42, height: side * 0.42)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

/// 칭호 텍스트 — 색 1개 = 단색, 2~3개 = 그라데이션, shimmer = 빛이 좌→우로 흐른다.
struct TitleText: View {
    let text: String
    let item: StarlightStore.Item
    var fontSize: CGFloat = 13

    var body: some View {
        let raw = StarlightArt.colors(item)
        let colors = raw.isEmpty ? [Color(hex: 0xE9EEF5)] : raw
        let label = Text(text).font(.minSans(fontSize, .semibold))
        if item.bool("shimmer") {
            let c1 = colors[0], c2 = colors.count > 1 ? colors[1] : colors[0], c3 = colors[colors.count - 1]
            return AnyView(TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                let x = (tl.date.timeIntervalSinceReferenceDate / 2.6).truncatingRemainder(dividingBy: 1) * 3 - 1 // -1..2
                label.foregroundStyle(LinearGradient(
                    colors: [c1, c2, .white, c2, c3],
                    startPoint: UnitPoint(x: x - 0.7, y: 0), endPoint: UnitPoint(x: x + 0.3, y: 1)))
            })
        }
        return AnyView(label.foregroundStyle(colors.count >= 2
            ? LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing)
            : LinearGradient(colors: [colors[0], colors[0]], startPoint: .leading, endPoint: .trailing)))
    }
}

/// 아이템 대표 그림(정사각형 칸 안).
struct ItemArt: View {
    let item: StarlightStore.Item
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            content(s).frame(width: geo.size.width, height: geo.size.height)
        }
    }

    @ViewBuilder
    private func content(_ s: CGFloat) -> some View {
        switch item.kind {
        case "shape":
            StarShape(type: item.int("type") ?? 1).fill(StarStyle.fill(9)).frame(width: s * 0.78, height: s * 0.78)
        case "color":
            StarShape(type: 1).fill(StarStyle.fill(item.int("index") ?? 0)).frame(width: s * 0.78, height: s * 0.78)
        case "frame":
            ZStack {
                ZStack {
                    Color(hex: 0x263042)
                    Image(systemName: "person.fill").font(.system(size: s * 0.3)).foregroundStyle(Color(hex: 0x8A93A3))
                }
                .clipShape(Circle())
                .padding(s * StarlightArt.frameInset)
                FrameArt(item: item)
            }
            .frame(width: s, height: s)
        case "aura":
            AuraArt(item: item).frame(width: s, height: s)
        case "badge":
            SvgGlyph(svg: item.str("path"), colors: StarlightArt.colors(item), glow: item.rarity != "common")
                .frame(width: s * 0.62, height: s * 0.62)
        case "title":
            TitleText(text: item.name(in: locale.effectiveLanguage), item: item, fontSize: 10.5)
                .lineLimit(1).minimumScaleFactor(0.6)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Color.white.opacity(0.13), in: Capsule())
                .overlay(Capsule().strokeBorder(StarlightArt.rarityColor(item.rarity).opacity(0.5), lineWidth: 0.75))
                .frame(maxWidth: s)
        case "telescope":
            let cs = StarlightArt.colors(item)
            SvgGlyph(svg: item.str("icon"), colors: cs.isEmpty ? [StarlightArt.rarityColor(item.rarity)] : cs, glow: true)
                .frame(width: s * 0.7, height: s * 0.7)
        case "keyBundle":
            HStack(spacing: 2) {
                Image(systemName: "key.fill").font(.system(size: max(10, s * 0.26)))
                Text("×\(item.int("keys") ?? 3)").font(.system(size: max(9, s * 0.15), weight: .bold))
            }
            .foregroundStyle(QuestTheme.gold)
        default:
            StardustIcon(tint: StarlightArt.rarityColor(item.rarity)).frame(width: s * 0.5, height: s * 0.5)
        }
    }
}

/// 장착 프레임 — 지름 `photo` 인 사진 둘레에 겹쳐 그린다(사진 크기는 그대로, 테는 바깥 띠에, 터치는 통과). 없으면 아무것도.
struct EquippedFrame: View {
    let equips: StarlightStore.Equips
    let photo: CGFloat
    @ObservedObject private var store = StarlightStore.shared

    var body: some View {
        if let item = store.item(equips.frame) {
            let side = photo / (1 - 2 * StarlightArt.frameInset)
            FrameArt(item: item)
                .frame(width: side, height: side)
                .allowsHitTesting(false)
        }
    }
}

/// 장착 배지 — 이름 옆 작은 문장. 없으면 아무것도.
struct EquippedBadge: View {
    let equips: StarlightStore.Equips
    var size: CGFloat = 22
    @ObservedObject private var store = StarlightStore.shared

    var body: some View {
        if let item = store.item(equips.badge) {
            SvgGlyph(svg: item.str("path"), colors: StarlightArt.colors(item), glow: item.rarity != "common")
                .frame(width: size, height: size)
        }
    }
}

/// 장착 칭호(sl:) — 아이템이 정한 색·반짝임으로. 공방 칭호가 아니면 nil 을 돌려 호출부가 원래 모양으로 그린다.
@MainActor
func starlightTitleView(_ titleId: String?, _ text: String, fontSize: CGFloat = 15) -> AnyView? {
    guard let item = StarlightStore.shared.titleItem(titleId) else { return nil }
    return AnyView(TitleText(text: text, item: item, fontSize: fontSize))
}

/// 다른 사람의 장착 상태 구독 — 프로필 화면용(나는 `StarlightStore.myEquips`).
@MainActor
final class EquipsWatcher: ObservableObject {
    @Published private(set) var equips = StarlightStore.Equips()
    private var reg: ListenerRegistration?
    private var uid: String?

    func watch(_ uid: String) {
        guard uid != self.uid, !uid.isEmpty else { return }
        reg?.remove()
        self.uid = uid
        reg = StarlightStore.watchEquips(uid) { [weak self] e in self?.equips = e }
    }

    func stop() {
        reg?.remove()
        reg = nil
        uid = nil
    }
}
