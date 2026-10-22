import SwiftUI
import UIKit

/// 성운 상자(2026-10-22 리디자인) — **크리스탈 정육면체 보석함**. Android `NebulaBox.kt` 와 같은 좌표(0~1)·색.
/// 아이소메트릭 육면체: 윗면(밝은 라일락 결정) · 왼면(보랏빛 결정) · 오른면(짙은 남보라 결정), 금빛 모서리와 뚜껑 띠,
/// 앞 모서리의 금빛 별 자물쇠, 몸통 속에서 도는 성운. 면은 별과 같은 크리스탈 파편(`StarCrystal.drawFacets`).
///
/// 정지된 부분(몸통·뚜껑)은 크기별로 한 번 구워 두고(`NebulaBake`), 움직이는 것(빛·금·뚜껑 들림·반짝이)만 매 프레임 그린다.
enum NebulaGeo {
    static let T = CGPoint(x: 0.5, y: 0.17)
    static let L = CGPoint(x: 0.17, y: 0.36)
    static let R = CGPoint(x: 0.83, y: 0.36)
    static let C = CGPoint(x: 0.5, y: 0.55)
    static let BL = CGPoint(x: 0.17, y: 0.72)
    static let BR = CGPoint(x: 0.83, y: 0.72)
    static let B = CGPoint(x: 0.5, y: 0.91)
    static let lidH: CGFloat = 0.065
    static let Ld = CGPoint(x: L.x, y: L.y + lidH)
    static let Cd = CGPoint(x: C.x, y: C.y + lidH)
    static let Rd = CGPoint(x: R.x, y: R.y + lidH)
    /// 뚜껑 회전축(윗면 가운데).
    static let lidPivot = CGPoint(x: 0.5, y: 0.38)

    /// 금 — 두드릴 때마다 하나씩(오른면 · 왼면 · 윗면).
    static let cracks: [[CGPoint]] = [
        [CGPoint(x: 0.64, y: 0.56), CGPoint(x: 0.69, y: 0.61), CGPoint(x: 0.66, y: 0.66), CGPoint(x: 0.71, y: 0.71), CGPoint(x: 0.69, y: 0.77)],
        [CGPoint(x: 0.31, y: 0.55), CGPoint(x: 0.27, y: 0.60), CGPoint(x: 0.33, y: 0.65), CGPoint(x: 0.29, y: 0.71), CGPoint(x: 0.33, y: 0.76)],
        [CGPoint(x: 0.34, y: 0.35), CGPoint(x: 0.42, y: 0.33), CGPoint(x: 0.47, y: 0.38), CGPoint(x: 0.56, y: 0.35), CGPoint(x: 0.62, y: 0.39)],
    ]
    /// 금의 곁가지.
    static let crackBranches: [[CGPoint]] = [
        [CGPoint(x: 0.66, y: 0.66), CGPoint(x: 0.61, y: 0.69)],
        [CGPoint(x: 0.33, y: 0.65), CGPoint(x: 0.39, y: 0.67)],
        [CGPoint(x: 0.47, y: 0.38), CGPoint(x: 0.49, y: 0.44)],
    ]
}

private func ui(_ hex: UInt32, _ a: CGFloat = 1) -> UIColor {
    UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

/// 몸통·뚜껑 구운 그림(px 변, 배율 1).
final class NebulaLayers {
    let body: UIImage
    let lid: UIImage
    init(body: UIImage, lid: UIImage) { self.body = body; self.lid = lid }
}

enum NebulaBake {
    private static let goldHi = ui(0xFFE9B0), gold = ui(0xFFC970), goldLo = ui(0xC98A3A)
    private static let cache: NSCache<NSNumber, NebulaLayers> = {
        let c = NSCache<NSNumber, NebulaLayers>()
        c.countLimit = 4
        return c
    }()
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    static func layers(_ px: Int) -> NebulaLayers {
        if let hit = cache.object(forKey: NSNumber(value: px)) { return hit }
        let l = NebulaLayers(body: render(px, body), lid: render(px, lid))
        cache.setObject(l, forKey: NSNumber(value: px))
        return l
    }

    private static func render(_ px: Int, _ draw: (CGContext, CGFloat) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let s = CGFloat(px)
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { draw($0.cgContext, s) }
    }

    private static func poly(_ s: CGFloat, _ p: [CGPoint]) -> CGPath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: p[0].x * s, y: p[0].y * s))
        for q in p.dropFirst() { path.addLine(to: CGPoint(x: q.x * s, y: q.y * s)) }
        path.closeSubpath()
        return path
    }

    private static func gradient(_ colors: [UIColor], _ locs: [CGFloat]? = nil) -> CGGradient? {
        CGGradient(colorsSpace: space, colors: colors.map(\.cgColor) as CFArray, locations: locs)
    }

    /// 면 하나를 결정으로 — 메시 중심이 면 가운데 오도록 면의 외접 정사각에 그린다.
    private static func face(_ c: CGContext, _ s: CGFloat, _ seed: Int, _ colors: [UIColor], _ p: [CGPoint]) {
        let xs = p.map(\.x), ys = p.map(\.y)
        let minX = xs.min()!, maxX = xs.max()!, minY = ys.min()!, maxY = ys.max()!
        let side = max(maxX - minX, maxY - minY)
        let rect = CGRect(x: ((minX + maxX) / 2 - side / 2) * s, y: ((minY + maxY) / 2 - side / 2) * s, width: side * s, height: side * s)
        StarCrystal.drawFacets(in: c, silhouette: poly(s, p), seed: seed, colors: colors, rect: rect)
    }

    /// 금빛 선 — 좌상(밝음)→우하(그늘) 그라데이션 획.
    private static func goldLines(_ c: CGContext, _ s: CGFloat, _ w: CGFloat, _ segs: [(CGPoint, CGPoint)]) {
        let path = CGMutablePath()
        for (a, b) in segs {
            path.move(to: CGPoint(x: a.x * s, y: a.y * s))
            path.addLine(to: CGPoint(x: b.x * s, y: b.y * s))
        }
        strokeGradient(c, path, s * w, [goldHi, gold, goldLo], from: .zero, to: CGPoint(x: s, y: s))
    }

    private static func strokeGradient(_ c: CGContext, _ path: CGPath, _ width: CGFloat, _ colors: [UIColor], from: CGPoint, to: CGPoint) {
        c.saveGState()
        c.addPath(path)
        c.setLineWidth(width)
        c.setLineCap(.round)
        c.setLineJoin(.round)
        c.replacePathWithStrokedPath()
        c.clip()
        if let g = gradient(colors) { c.drawLinearGradient(g, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]) }
        c.restoreGState()
    }

    private static func fillGradient(_ c: CGContext, _ path: CGPath, _ colors: [UIColor], from: CGPoint, to: CGPoint) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        if let g = gradient(colors) { c.drawLinearGradient(g, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]) }
        c.restoreGState()
    }

    private static func radial(_ c: CGContext, _ center: CGPoint, _ r: CGFloat, _ colors: [UIColor], _ locs: [CGFloat]? = nil) {
        guard let g = gradient(colors, locs) else { return }
        c.drawRadialGradient(g, startCenter: center, startRadius: 0, endCenter: center, endRadius: r, options: [])
    }

    private static func stud(_ c: CGContext, _ s: CGFloat, _ v: CGPoint, _ r: CGFloat) {
        let center = CGPoint(x: v.x * s, y: v.y * s)
        c.saveGState()
        c.addEllipse(in: CGRect(x: center.x - r * s, y: center.y - r * s, width: r * s * 2, height: r * s * 2))
        c.clip()
        let hc = CGPoint(x: center.x - r * s * 0.3, y: center.y - r * s * 0.3)
        if let g = gradient([ui(0xFFFBEA), gold, goldLo], [0, 0.55, 1]) {
            c.drawRadialGradient(g, startCenter: hc, startRadius: 0, endCenter: hc, endRadius: r * s * 1.2, options: .drawsAfterEndLocation)
        }
        c.restoreGState()
    }

    private static func body(_ c: CGContext, _ s: CGFloat) {
        typealias G = NebulaGeo
        face(c, s, 41, [ui(0x3E27B0), ui(0x6B45E8), ui(0xA778FF)], [G.L, G.C, G.B, G.BL])
        face(c, s, 57, [ui(0x221A6E), ui(0x4630B8), ui(0x6E4DE0)], [G.C, G.R, G.BR, G.B])
        // 몸통 속 성운 — 분홍·하늘빛 소용돌이(번진 선) + 별가루
        c.saveGState()
        let clip = CGMutablePath()
        clip.addPath(poly(s, [G.L, G.C, G.B, G.BL]))
        clip.addPath(poly(s, [G.C, G.R, G.BR, G.B]))
        c.addPath(clip)
        c.clip()
        for k in 0..<2 {
            let arm = CGMutablePath()
            for i in 0...40 {
                let t = CGFloat(i) / 40
                let a = CGFloat(k) * .pi + t * 2.8
                let r = 0.04 + 0.2 * t
                let p = CGPoint(x: (0.5 + cos(a) * r * 1.2) * s, y: (0.71 + sin(a) * r * 0.6) * s)
                if i == 0 { arm.move(to: p) } else { arm.addLine(to: p) }
            }
            let col = k == 0 ? ui(0xFFB8F0, 0x88 / 255.0) : ui(0x9FD8FF, 0x66 / 255.0)
            c.saveGState()
            c.setShadow(offset: .zero, blur: s * 0.05, color: col.cgColor)
            c.setStrokeColor(col.withAlphaComponent(col.cgColor.alpha * 0.7).cgColor)
            c.setLineWidth(s * 0.03)
            c.setLineCap(.round)
            c.addPath(arm)
            c.strokePath()
            c.restoreGState()
        }
        // 가운데 은은한 핵
        radial(c, CGPoint(x: 0.5 * s, y: 0.71 * s), 0.16 * s, [ui(0xFFE6FF, 0x66 / 255.0), ui(0xFFE6FF, 0)])
        c.setFillColor(ui(0xFFFFFF, 0xCC / 255.0).cgColor)
        for i in 0..<14 {
            let x = 0.2 + 0.6 * (CGFloat(i * 37 % 23) / 23)
            let y = 0.48 + 0.4 * (CGFloat(i * 53 % 19) / 19)
            let r = s * (0.003 + 0.003 * CGFloat(i % 3))
            c.fillEllipse(in: CGRect(x: x * s - r, y: y * s - r, width: r * 2, height: r * 2))
        }
        c.restoreGState()
        // 세로 모서리의 빛 받는 테(유리 두께)
        let rim = CGMutablePath()
        rim.move(to: CGPoint(x: (G.Cd.x + 0.012) * s, y: (G.Cd.y + 0.01) * s))
        rim.addLine(to: CGPoint(x: (G.B.x + 0.012) * s, y: (G.B.y - 0.02) * s))
        strokeGradient(c, rim, s * 0.012, [ui(0xFFFFFF, 0x88 / 255.0), ui(0xFFFFFF, 0x11 / 255.0)],
                       from: CGPoint(x: 0, y: G.Cd.y * s), to: CGPoint(x: 0, y: G.B.y * s))
        // 금빛 모서리
        goldLines(c, s, 0.013, [(G.Ld, G.BL), (G.BL, G.B), (G.B, G.BR), (G.BR, G.Rd), (G.Cd, G.B)])
        stud(c, s, G.BL, 0.014); stud(c, s, G.BR, 0.014); stud(c, s, G.B, 0.016)
        // 앞 모서리 금빛 별 자물쇠(크리스탈) + 받침 원판
        let lock = CGPoint(x: 0.5 * s, y: 0.705 * s)
        radial(c, lock, 0.068 * s, [ui(0x1A1240, 0xCC / 255.0), ui(0x1A1240, 0x99 / 255.0), ui(0x000000, 0)], [0, 0.7, 1])
        let ring = CGMutablePath()
        ring.addEllipse(in: CGRect(x: lock.x - 0.06 * s, y: lock.y - 0.06 * s, width: 0.12 * s, height: 0.12 * s))
        strokeGradient(c, ring, s * 0.008, [goldHi, gold, goldLo], from: .zero, to: CGPoint(x: s, y: s))
        let star = 0.1 * s
        StarCrystal.draw(in: c, type: 1, colors: [ui(0xFFF4C8), gold, ui(0xE08A2A)],
                         rect: CGRect(x: lock.x - star / 2, y: lock.y - star / 2, width: star, height: star))
    }

    private static func lid(_ c: CGContext, _ s: CGFloat) {
        typealias G = NebulaGeo
        // 뚜껑 띠 — 왼쪽(밝은 금) · 오른쪽(그늘진 금)
        fillGradient(c, poly(s, [G.L, G.C, G.Cd, G.Ld]), [ui(0xFFE7A6), ui(0xF2B65E)],
                     from: CGPoint(x: 0, y: G.L.y * s), to: CGPoint(x: 0, y: G.Ld.y * s))
        fillGradient(c, poly(s, [G.C, G.R, G.Rd, G.Cd]), [ui(0xDDA052), ui(0xAA6A2A)],
                     from: CGPoint(x: 0, y: G.R.y * s), to: CGPoint(x: 0, y: G.Rd.y * s))
        // 띠 위 홈(가는 어두운 선) — 금속 띠의 두께감
        let mid = G.lidH * 0.55
        c.setStrokeColor(ui(0x704010, 0x55 / 255.0).cgColor)
        c.setLineWidth(s * 0.004)
        c.move(to: CGPoint(x: G.L.x * s, y: (G.L.y + mid) * s))
        c.addLine(to: CGPoint(x: G.C.x * s, y: (G.C.y + mid) * s))
        c.addLine(to: CGPoint(x: G.R.x * s, y: (G.R.y + mid) * s))
        c.strokePath()
        // 윗면 결정
        face(c, s, 23, [ui(0x9C78FF), ui(0xCDB0FF), ui(0xF6C4EE)], [G.T, G.R, G.C, G.L])
        // 윗면 가운데 네 꼭지 별 문장(빛 번짐 + 크림색 별)
        let e = CGPoint(x: 0.5, y: 0.36)
        radial(c, CGPoint(x: e.x * s, y: e.y * s), 0.1 * s, [ui(0xFFFFFF, 0x99 / 255.0), ui(0xFFFFFF, 0)])
        let emb = CGMutablePath()
        for i in 0..<8 {
            let r: CGFloat = i % 2 == 0 ? 0.09 : 0.018
            let a = (-90 + CGFloat(i) * 45) * .pi / 180
            let p = CGPoint(x: (e.x + r * cos(a)) * s, y: (e.y + r * sin(a) * 0.56) * s)
            if i == 0 { emb.move(to: p) } else { emb.addLine(to: p) }
        }
        emb.closeSubpath()
        c.setFillColor(ui(0xFFFBEE).cgColor)
        c.addPath(emb)
        c.fillPath()
        // 금빛 모서리 + 꼭짓점 징
        goldLines(c, s, 0.013, [(G.T, G.R), (G.R, G.C), (G.C, G.L), (G.L, G.T), (G.Ld, G.Cd), (G.Cd, G.Rd), (G.L, G.Ld), (G.C, G.Cd), (G.R, G.Rd)])
        stud(c, s, G.T, 0.014); stud(c, s, G.L, 0.014); stud(c, s, G.R, 0.014); stud(c, s, G.C, 0.016)
    }
}

/// 성운 상자 그림.
/// - glow: 0~1 — 이음새·금 사이로 새는 빛의 세기(두드릴수록 커진다).
/// - glowColor: 새는 빛 색(두드릴 때 등급 색으로 바뀐다).
/// - cracks: 0~3 — 보이는 금 수.
/// - lidOpen: 0~1 — 뚜껑이 들려 날아가는 정도.
/// - animated: 둥실 떠 있기·반짝이(목록의 작은 아이콘은 false — 매 프레임 다시 그리지 않게).
struct NebulaBoxArt: View {
    var glow: Double = 0
    var glowColor: Color = Color(hex: 0xC9A2FF)
    var cracks = 0
    var lidOpen: Double = 0
    var animated = true
    @Environment(\.displayScale) private var scale

    var body: some View {
        if animated {
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: false)) { tl in
                let t = (tl.date.timeIntervalSinceReferenceDate / 4.8).truncatingRemainder(dividingBy: 1)
                Canvas { ctx, size in draw(&ctx, size, t: t, decor: true) }
            }
        } else {
            Canvas { ctx, size in draw(&ctx, size, t: 0, decor: false) }
        }
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, t: Double, decor: Bool) {
        let s = min(size.width, size.height)
        guard s > 0 else { return }
        let px = max(8, Int((s * scale).rounded()))
        let ox = (size.width - s) / 2, oy = (size.height - s) / 2
        let layers = NebulaBake.layers(px)
        let ph = t * 2 * .pi
        let bob = decor ? CGFloat(sin(ph)) * 0.018 * s : 0
        func at(_ p: CGPoint) -> CGPoint { CGPoint(x: ox + p.x * s, y: oy + p.y * s + bob) }
        let g = CGFloat(min(max(glow, 0), 1))

        if decor {
            // 뒤 빛
            let back = CGPoint(x: ox + 0.5 * s, y: oy + 0.55 * s)
            ctx.fill(StarlightArt.circle(back, s * 0.6), with: .radialGradient(
                Gradient(colors: [glowColor.opacity(0.30 + 0.45 * g), .clear]), center: back, startRadius: 0, endRadius: s * 0.6))
            // 바닥 그림자(떠오르면 작아진다)
            let sh = 1 - bob / s * 4
            let sc = CGPoint(x: ox + 0.5 * s, y: oy + 0.95 * s)
            ctx.fill(Path(ellipseIn: CGRect(x: ox + s * (0.5 - 0.28 * sh), y: oy + s * 0.915, width: s * 0.56 * sh, height: s * 0.07)),
                     with: .radialGradient(Gradient(colors: [.black.opacity(0.55), .clear]), center: sc, startRadius: 0, endRadius: s * 0.28 * sh))
        }
        // 몸통
        ctx.draw(Image(uiImage: layers.body), in: CGRect(x: ox, y: oy + bob, width: s, height: s))

        // 금 — 바깥 번짐 → 색 → 흰 심
        let lift = CGFloat(min(max(lidOpen, 0), 1))
        for i in 0..<min(max(cracks, 0), 3) {
            if i == 2 && lift > 0 { continue } // 윗면 금은 뚜껑과 함께 날아간다(아래에서 뚜껑에 그린다)
            drawCrack(&ctx, NebulaGeo.cracks[i], NebulaGeo.crackBranches[i], at, s, glowColor, g, 1)
        }

        // 이음새로 새는 빛
        let seamA = (0.25 + 0.75 * g) * (decor ? 0.85 + 0.15 * CGFloat(sin(ph * 2)) : 1)
        var seam = Path()
        seam.move(to: at(NebulaGeo.Ld)); seam.addLine(to: at(NebulaGeo.Cd)); seam.addLine(to: at(NebulaGeo.Rd))
        for (w, a) in [(CGFloat(0.06), CGFloat(0.12)), (0.03, 0.3), (0.012, 0.9)] {
            ctx.stroke(seam, with: .color((a > 0.5 ? Color.white : glowColor).opacity(Double(min(max(a * seamA, 0), 1)))),
                       style: StrokeStyle(lineWidth: s * w, lineCap: .round, lineJoin: .round))
        }

        // 열린 입구 — 뚜껑이 들리면 윗면 자리에서 빛이 쏟아진다
        if lift > 0 {
            var mouth = Path()
            mouth.move(to: at(CGPoint(x: NebulaGeo.T.x, y: NebulaGeo.T.y + NebulaGeo.lidH)))
            mouth.addLine(to: at(NebulaGeo.Rd)); mouth.addLine(to: at(NebulaGeo.Cd)); mouth.addLine(to: at(NebulaGeo.Ld))
            mouth.closeSubpath()
            let mc = at(CGPoint(x: 0.5, y: 0.42))
            var m = ctx
            m.opacity = Double(lift)
            m.fill(mouth, with: .radialGradient(Gradient(colors: [.white, glowColor, glowColor.opacity(0.6)]), center: mc, startRadius: 0, endRadius: s * 0.4))
        }

        // 뚜껑(들리며 기울고 사라진다)
        let lidAlpha = 1 - min(max((lift - 0.55) / 0.45, 0), 1)
        if lidAlpha > 0 {
            let pivot = at(NebulaGeo.lidPivot)
            var l = ctx
            l.translateBy(x: s * 0.08 * lift, y: -s * 0.62 * lift)
            l.translateBy(x: pivot.x, y: pivot.y)
            l.rotate(by: .degrees(Double(-24 * lift)))
            l.translateBy(x: -pivot.x, y: -pivot.y)
            l.opacity = Double(lidAlpha)
            l.draw(Image(uiImage: layers.lid), in: CGRect(x: ox, y: oy + bob, width: s, height: s))
            if cracks >= 3 { drawCrack(&l, NebulaGeo.cracks[2], NebulaGeo.crackBranches[2], at, s, glowColor, g, 1) }
        }

        // 둘레 반짝이
        if decor {
            let spots: [(CGPoint, CGFloat)] = [(CGPoint(x: 0.12, y: 0.2), 0.032), (CGPoint(x: 0.88, y: 0.24), 0.022),
                                               (CGPoint(x: 0.92, y: 0.6), 0.018), (CGPoint(x: 0.08, y: 0.56), 0.016)]
            for (i, (p, r)) in spots.enumerated() {
                let tw = 0.5 + 0.5 * sin(ph * 2 + Double(i) * 1.7)
                ctx.fill(StarlightArt.sparkle(CGPoint(x: ox + p.x * s, y: oy + p.y * s), s * r * CGFloat(0.7 + 0.3 * tw)),
                         with: .color(.white.opacity(0.35 + 0.6 * tw)))
            }
        }
    }

    private func drawCrack(_ ctx: inout GraphicsContext, _ pts: [CGPoint], _ branch: [CGPoint], _ at: (CGPoint) -> CGPoint,
                           _ s: CGFloat, _ color: Color, _ glow: CGFloat, _ alpha: CGFloat) {
        func path(_ list: [CGPoint]) -> Path {
            var p = Path()
            for (i, q) in list.enumerated() { if i == 0 { p.move(to: at(q)) } else { p.addLine(to: at(q)) } }
            return p
        }
        let main = path(pts), br = path(branch)
        let k = (0.6 + 0.4 * glow) * alpha
        for (w, a, white) in [(CGFloat(0.05), CGFloat(0.18), false), (0.022, 0.55, false), (0.008, 1, true)] {
            let col = (white ? Color.white : color).opacity(Double(min(max(a * k, 0), 1)))
            ctx.stroke(main, with: .color(col), style: StrokeStyle(lineWidth: s * w, lineCap: .round, lineJoin: .round))
            ctx.stroke(br, with: .color(col), style: StrokeStyle(lineWidth: s * w * 0.7, lineCap: .round, lineJoin: .round))
        }
    }
}
