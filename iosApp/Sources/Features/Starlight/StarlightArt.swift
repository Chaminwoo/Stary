import FirebaseFirestore
import SwiftUI
import UIKit

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

// MARK: - 크리스탈 글리프(배지·망원경·열쇠·별가루)

extension StarlightArt {
    /// 구운 크리스탈 그림 캐시(키 = 경로|색|px) — 목록에서 칸마다 파편을 다시 계산하지 않게.
    private static let crystalCache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.totalCostLimit = 8 * 1024 * 1024
        return c
    }()

    /// Java `String.hashCode` — 글리프 파편 무늬 시드를 Android 와 같게.
    static func javaHash(_ s: String) -> Int32 {
        var h: Int32 = 0
        for u in s.utf16 { h = h &* 31 &+ Int32(u) }
        return h
    }

    private static func hex(_ c: UIColor) -> String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }

    /// [unit] 실루엣(0~1)을 별과 같은 크리스탈 파편으로 채운 그림(px 변, 배율 1).
    /// 가장자리에 옅은 흰 테(빛을 받는 모서리)를 둘러 결정처럼 보이게 한다. Android `bakeCrystal` 패리티.
    static func bakeCrystal(key: String, px: CGFloat, seed: Int, colors: [UIColor], unit: () -> CGPath?) -> UIImage? {
        let side = px.rounded()
        guard side > 0 else { return nil }
        let k = "\(key)|\(colors.map(hex).joined(separator: ","))|\(Int(side))" as NSString
        if let hit = crystalCache.object(forKey: k) { return hit }
        guard let u = unit() else { return nil }
        var tr = CGAffineTransform(scaleX: side, y: side)
        guard let path = u.copy(using: &tr) else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let rect = CGRect(x: 0, y: 0, width: side, height: side)
        let img = UIGraphicsImageRenderer(size: rect.size, format: format).image { ctx in
            let cg = ctx.cgContext
            StarCrystal.drawFacets(in: cg, silhouette: path, seed: seed, colors: colors.isEmpty ? [.white] : colors, rect: rect)
            cg.saveGState()
            cg.addPath(path)
            cg.setLineWidth(max(side * 0.022, 0.8))
            cg.replacePathWithStrokedPath()
            cg.clip()
            let rim = [UIColor.white.withAlphaComponent(0.6), UIColor.white.withAlphaComponent(0.2), UIColor.white.withAlphaComponent(0.33)]
            if let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: rim.map(\.cgColor) as CFArray, locations: [0, 0.5, 1]) {
                cg.drawLinearGradient(g, start: .zero, end: CGPoint(x: side, y: side), options: [])
            }
            cg.restoreGState()
        }
        crystalCache.setObject(img, forKey: k, cost: Int(side * side * 4))
        return img
    }

    /// 별가루 실루엣(크기가 다른 네 꼭지 반짝이 셋), 0~1.
    static let stardustPath: CGPath = {
        let p = CGMutablePath()
        func sparkle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
            let k: CGFloat = 0.16
            p.move(to: CGPoint(x: cx, y: cy - r))
            p.addQuadCurve(to: CGPoint(x: cx + r, y: cy), control: CGPoint(x: cx + r * k, y: cy - r * k))
            p.addQuadCurve(to: CGPoint(x: cx, y: cy + r), control: CGPoint(x: cx + r * k, y: cy + r * k))
            p.addQuadCurve(to: CGPoint(x: cx - r, y: cy), control: CGPoint(x: cx - r * k, y: cy + r * k))
            p.addQuadCurve(to: CGPoint(x: cx, y: cy - r), control: CGPoint(x: cx - r * k, y: cy - r * k))
            p.closeSubpath()
        }
        sparkle(0.40, 0.44, 0.38)
        sparkle(0.80, 0.20, 0.18)
        sparkle(0.80, 0.80, 0.15)
        return p
    }()

    /// 열쇠 실루엣 — 별 모양 15번(열쇠), 0~1.
    static let keyPath: CGPath = StarShape(type: 15).path(in: CGRect(x: 0, y: 0, width: 1, height: 1)).cgPath
}

/// 크리스탈 그림을 정사각 가운데에 그린다(+ 선택적 뒤 빛). Android `CrystalCanvas` 패리티.
struct CrystalGlyph: View {
    let key: String
    let seed: Int
    let colors: [Color]
    var glow = false
    let unit: () -> CGPath?
    @Environment(\.displayScale) private var scale

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            let ui = colors.isEmpty ? [UIColor.white] : colors.map { UIColor($0) }
            ZStack {
                if glow {
                    Circle()
                        .fill(RadialGradient(colors: [Color(ui[ui.count - 1]).opacity(0.32), .clear], center: .center, startRadius: 0, endRadius: s * 0.55))
                        .frame(width: s * 1.1, height: s * 1.1)
                }
                if let img = StarlightArt.bakeCrystal(key: key, px: s * scale, seed: seed, colors: ui, unit: unit) {
                    Image(uiImage: img).resizable().interpolation(.high).frame(width: s, height: s)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

/// 크리스탈 열쇠 — 별 모양 15번(열쇠) 실루엣을 금빛 결정으로. Android `CrystalKeyIcon` 패리티.
struct CrystalKeyIcon: View {
    var glow = false
    var body: some View {
        CrystalGlyph(key: "key", seed: 15, colors: [Color(hex: 0xFFF0B8), Color(hex: 0xFFC75A), Color(hex: 0xE5892E)], glow: glow) {
            StarlightArt.keyPath
        }
    }
}

/// 별가루 아이콘 — 밝은 색이면 크리스탈 결정, 어두운 색(금빛 버튼 위 글자색 등)·반투명이면 단색.
struct StardustIcon: View {
    var tint: Color = StarlightArt.dust

    var body: some View {
        let ui = UIColor(tint)
        if Self.isFlat(ui) {
            Canvas { ctx, size in
                let s = min(size.width, size.height)
                ctx.fill(Path(StarlightArt.stardustPath).applying(CGAffineTransform(scaleX: s, y: s)), with: .color(tint))
            }
        } else {
            CrystalGlyph(key: "dust", seed: 33,
                         colors: [Color(ui.blended(with: .white, fraction: 0.55)), tint,
                                  Color(ui.blended(with: UIColor(red: 0xB8 / 255.0, green: 0x6A / 255.0, blue: 0x1E / 255.0, alpha: 1), fraction: 0.45))]) {
                StarlightArt.stardustPath
            }
        }
    }

    private static func isFlat(_ c: UIColor) -> Bool {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.3 || a < 0.95
    }
}

/// 0~1 SVG 글리프를 크리스탈로 채운다 — 배지·망원경 아이콘. 색 1개 = 단색 결정, 2~3개 = 대각 그라데이션 결정.
struct SvgGlyph: View {
    let svg: String?
    let colors: [Color]
    var glow = false

    var body: some View {
        if let svg, !svg.isEmpty {
            let h = Int(StarlightArt.javaHash(svg))
            CrystalGlyph(key: "svg:\(h)", seed: (h & 0x7FFF) % 97 + 3, colors: colors, glow: glow) {
                StarlightArt.unitPath(svg)
            }
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
            // 반짝임 숨 — 차분하게(예전 2.4초는 영웅 프레임이 "두근거려" 보였다).
            let wave = (t / 4.8).truncatingRemainder(dividingBy: 1)
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
        if item.bool("glow") && pattern != "aurora" {
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
                let tw = 0.7 + 0.3 * sin(wave * 2 * .pi + Double(i) * 1.7)
                g.fill(StarlightArt.circle(p, w * 1.5), with: .color(colors[i % colors.count].opacity(0.22 * tw)))
                g.fill(StarlightArt.sparkle(p, w * (i % 3 == 0 ? 1.5 : 1.0)), with: .color(colors[i % colors.count].opacity(0.7 + 0.3 * tw)))
            }
        case "aurora":
            drawAurora(&ctx, c: c, r: r, w: w, colors: colors, angle: angle, wave: wave)
        default: // ring + 모르는 패턴
            ctx.stroke(StarlightArt.circle(c, r), with: ring, lineWidth: w)
        }
    }

    /// 오로라 베일(2026-10-22 리디자인) — 고리 바깥으로 드리운 **빛의 커튼**. Android `drawAurora` 패리티.
    /// 각도마다 높이가 다른 세로 빛줄기(커튼 주름)를 세 겹 겹치고, 그 위를 얇은 빛 고리가 감싼다.
    /// 겹마다 다른 속도로 물결쳐 커튼이 일렁이고, 바깥엔 작은 별이 천천히 반짝인다.
    private func drawAurora(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, w: CGFloat, colors: [Color], angle: Double, wave: Double) {
        let cs = (colors.count >= 2 ? colors : [colors[0], colors[0]]).map { UIColor($0) }
        func colorAt(_ f: Double) -> Color {
            let x = (f.truncatingRemainder(dividingBy: 1) + 1).truncatingRemainder(dividingBy: 1) * Double(cs.count)
            let i = Int(x) % cs.count
            return Color(cs[i].blended(with: cs[(i + 1) % cs.count], fraction: CGFloat(x - Double(Int(x)))))
        }
        let ph = wave * 2 * .pi
        let rot = angle * .pi / 180
        // 바탕 안개 — 고리 둘레의 옅은 빛
        let outer = r + w * 3.2
        ctx.fill(StarlightArt.circle(c, outer), with: .radialGradient(
            Gradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: .clear, location: (r - w) / outer),
                .init(color: Color(cs[0]).opacity(0.22), location: r / outer),
                .init(color: .clear, location: 1),
            ]), center: c, startRadius: 0, endRadius: outer))
        // 커튼 주름 — 세 겹(뒤 → 앞), 겹마다 줄기 수·위상·속도가 다르다.
        for layer in 0..<3 {
            let n = 54 + layer * 12
            let lw = w * (0.42 - CGFloat(layer) * 0.08)
            for i in 0..<n {
                let f = Double(i) / Double(n)
                let a = f * 2 * .pi + rot * (1 + Double(layer) * 0.35)
                // 주름 높이: 느린 큰 물결 × 빠른 잔물결 — 커튼이 접힌 듯 들쭉날쭉
                let big = 0.5 + 0.5 * sin(a * 3 + ph * Double(1 + layer) + Double(layer) * 2.1)
                let small = 0.6 + 0.4 * sin(a * 11 - ph * 2 + Double(layer))
                let h = w * CGFloat(0.8 + 2.1 * big * small)
                let inner = r - w * 0.35
                let start = CGPoint(x: c.x + CGFloat(cos(a)) * inner, y: c.y + CGFloat(sin(a)) * inner)
                let end = CGPoint(x: c.x + CGFloat(cos(a)) * (inner + h), y: c.y + CGFloat(sin(a)) * (inner + h))
                let col = colorAt(f + Double(layer) * 0.17 + angle / 360)
                let alpha = (0.32 + 0.38 * big) * (1 - Double(layer) * 0.18)
                var line = Path()
                line.move(to: start)
                line.addLine(to: end)
                ctx.stroke(line, with: .linearGradient(
                    Gradient(colors: [col.opacity(alpha), col.opacity(alpha * 0.45), .clear]), startPoint: start, endPoint: end),
                    style: StrokeStyle(lineWidth: lw, lineCap: .round))
            }
        }
        // 빛 고리 — 가는 두 줄(밝은 안쪽 + 색 바깥)
        let ringColors = cs.map { Color($0) }
        ctx.stroke(StarlightArt.circle(c, r), with: .conicGradient(Gradient(colors: ringColors + [ringColors[0]]), center: c), lineWidth: w * 0.55)
        ctx.stroke(StarlightArt.circle(c, r - w * 0.1), with: .color(.white.opacity(0.55)), lineWidth: w * 0.16)
        // 바깥 작은 별 — 천천히 반짝
        for i in 0..<7 {
            let a = (Double(i) * 51.4 + 12) * .pi / 180 + rot * 0.5
            let rr = r + w * (2.6 + CGFloat(i % 3) * 0.55)
            let tw = 0.5 + 0.5 * sin(ph + Double(i) * 1.9)
            let p = CGPoint(x: c.x + CGFloat(cos(a)) * rr, y: c.y + CGFloat(sin(a)) * rr)
            ctx.fill(StarlightArt.sparkle(p, w * (0.5 + 0.25 * CGFloat(i % 2))), with: .color(.white.opacity(0.35 + 0.55 * tw)))
        }
    }
}

/// 지도 별 오라 미리보기 — 별 하나와 그 둘레 빛. 지도(`StarlightAura` 스프라이트 + 원 레이어)와 같은 그림·움직임.
/// 스프라이트 변 = 미리보기 변(별은 그 0.42 — 지도 별 160px : 스프라이트 384px 와 같은 비율). Android `AuraArt` 패리티.
struct AuraArt: View {
    let item: StarlightStore.Item
    var starType = 1
    var starColor = 0

    var body: some View {
        let pattern = item.str("pattern") ?? "halo"
        let raw = StarlightArt.colors(item)
        let colors = raw.isEmpty ? [Color(hex: 0xBFD8FF)] : raw
        let speed = min(max(item.num("speed") ?? 4, 2), 120)
        let sprite = StarlightAura.sprite(item.payload)
        let motion = StarlightAura.motion(item.payload)
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                // 한 주기 = speed 초. 회전·흔들림·반짝임 모두 이 위상에서 나온다.
                let ph = (t / speed).truncatingRemainder(dividingBy: 1)
                let breathe = 0.8 + 0.2 * (0.5 + 0.5 * sin(t / 2.6 * .pi))
                let deg = motion.spinDegPerSec * speed * ph + motion.wobbleDeg * sin(ph * 2 * .pi)
                let alpha = motion.flicker ? 0.75 + 0.25 * sin(ph * 6 * .pi) : 1
                ZStack {
                    Canvas { ctx, size in
                        let s = min(size.width, size.height)
                        let c = CGPoint(x: size.width / 2, y: size.height / 2)
                        let base = s * 0.30
                        if pattern == "pulse" {
                            ctx.fill(StarlightArt.circle(c, base * 1.25), with: .radialGradient(
                                Gradient(colors: [colors[0].opacity(0.5 * breathe), .clear]), center: c, startRadius: 0, endRadius: base * 1.25))
                            // 퍼지는 고리 하나(예전 둘 → 하나, 주기도 느리게 — "두근거림"이 정신없지 않게)
                            ctx.stroke(StarlightArt.circle(c, base * (0.6 + CGFloat(ph))),
                                       with: .color(colors[colors.count - 1].opacity((1 - ph) * 0.6)), lineWidth: s * 0.016)
                        } else if sprite != nil {
                            ctx.fill(StarlightArt.circle(c, base * 1.2), with: .radialGradient(
                                Gradient(colors: [colors[0].opacity(0.28 * breathe), .clear]), center: c, startRadius: 0, endRadius: base * 1.2))
                        } else { // halo + 모르는 패턴
                            ctx.fill(StarlightArt.circle(c, base * 1.45), with: .radialGradient(
                                Gradient(colors: colors.map { $0.opacity(0.55 * breathe) } + [.clear]), center: c, startRadius: 0, endRadius: base * 1.45))
                        }
                    }
                    if let sprite, pattern != "pulse" {
                        Image(uiImage: sprite)
                            .resizable()
                            .interpolation(.high)
                            .frame(width: side, height: side)
                            .rotationEffect(.degrees(deg))
                            .opacity(alpha)
                    }
                    Image(uiImage: StarCrystal.image(type: starType, colorIndex: starColor, size: side * 0.42))
                        .resizable()
                        .frame(width: side * 0.42, height: side * 0.42)
                }
                .frame(width: geo.size.width, height: geo.size.height)
            }
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
        // 칭호는 한 줄 — 자리가 모자라면 줄을 바꾸지 않고 글자를 줄인다(Android TitleText 패리티).
        let label = Text(text).font(.minSans(fontSize, .semibold)).lineLimit(1).minimumScaleFactor(0.5)
        if item.bool("shimmer") {
            let c1 = colors[0], c2 = colors.count > 1 ? colors[1] : colors[0], c3 = colors[colors.count - 1]
            return AnyView(TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                let x = (tl.date.timeIntervalSinceReferenceDate / 3.4).truncatingRemainder(dividingBy: 1) * 3 - 1 // -1..2
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
            Image(uiImage: StarCrystal.image(type: item.int("type") ?? 1, colorIndex: 9, size: s * 0.78))
                .resizable().frame(width: s * 0.78, height: s * 0.78)
        case "color":
            Image(uiImage: StarCrystal.image(type: 1, colorIndex: item.int("index") ?? 0, size: s * 0.78))
                .resizable().frame(width: s * 0.78, height: s * 0.78)
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
                .frame(width: s * 0.66, height: s * 0.66)
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
                CrystalKeyIcon().frame(width: max(14, s * 0.3), height: max(14, s * 0.3))
                Text("×\(item.int("keys") ?? 3)").font(.system(size: max(9, s * 0.15), weight: .bold))
                    .foregroundStyle(Color(hex: 0xFFD27A))
            }
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
