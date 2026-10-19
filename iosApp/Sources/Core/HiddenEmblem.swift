import SwiftUI

/// 히든 업적 **엠블럼**(2026-10-19 재디자인 — Android `HiddenParticles.kt` `HiddenEmblem` 패리티, 같은 비율·주기).
///
/// 육각 크리스탈(두 색 그라데이션 + 빛 방향 면 음영 + 안쪽 테이블면 + 도는 은빛 테두리) 안에 상징 아이콘,
/// 몇 초마다 빛이 사선으로 훑는다. 바깥에는 업적마다 다른 이펙트([ParticleEffect] 12종).
/// 상태: owned(전부) / claimed(남이 가져감 — 이펙트 없음) / sealed(아무도 못 찾음 — 어두운 봉인 + 흐린 이펙트).
enum EmblemState { case owned, claimed, sealed }

private enum EmblemK {
    static let hexFrac: CGFloat = 0.46
    static let shinePeriod: Double = 3.6
    static let shineSweep: Double = 1.1
    static let effectScale: CGFloat = 1.9
    static let sealedInk = Color(hex: 0x2A2E38)
}

struct HiddenEmblemView: View {
    let ach: HiddenAchievement
    var size: CGFloat = 40
    var state: EmblemState = .owned

    var body: some View {
        let sealed = state == .sealed
        let c1 = sealed ? ach.icon.color.blended(with: EmblemK.sealedInk, fraction: 0.72) : ach.icon.color
        let c2 = sealed ? ach.icon.color2.blended(with: EmblemK.sealedInk, fraction: 0.78) : ach.icon.color2
        let fx: Double = state == .owned ? 1 : (state == .claimed ? 0 : 0.35)
        let seed = ach.effect.rawValue
        TimelineView(.animation) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600)
            ZStack {
                if fx > 0 { effectCanvas(t: t, front: false, alpha: fx) }
                Canvas { g, sz in
                    EmblemDraw.emblem(&g, sz: sz, c1: c1, c2: c2, t: t, seed: seed, aura: sealed ? 0.25 : 1)
                }
                .frame(width: size, height: size)
                Image(systemName: ach.icon.systemImage)
                    .font(.system(size: size * 0.36, weight: .semibold))
                    .foregroundStyle(c2.blended(with: .black, fraction: 0.45).opacity(sealed ? 0.5 : 0.75))
                    .offset(y: size * 0.018)
                Image(systemName: ach.icon.systemImage)
                    .font(.system(size: size * 0.36, weight: .semibold))
                    .foregroundStyle(sealed ? Color.white.opacity(0.42) : Color.white.blended(with: ach.icon.color, fraction: 0.18))
                Canvas { g, sz in
                    EmblemDraw.shine(&g, sz: sz, t: t, seed: seed, strength: sealed ? 0.22 : 0.5)
                }
                .frame(width: size, height: size)
                if fx > 0 { effectCanvas(t: t, front: true, alpha: fx) }
            }
            .frame(width: size, height: size)
        }
        .allowsHitTesting(false)
    }

    private func effectCanvas(t: Double, front: Bool, alpha: Double) -> some View {
        let effect = ach.effect
        let c1 = ach.icon.color, c2 = ach.icon.color2
        let seeds = EmblemDraw.seeds(effect)
        return Canvas { g, sz in
            let r = min(sz.width, sz.height) / EmblemK.effectScale * EmblemK.hexFrac
            EmblemDraw.effect(&g, sz: sz, effect: effect, c1: c1, c2: c2, t: t, front: front, a: alpha, s: seeds, r: r)
        }
        .frame(width: size * EmblemK.effectScale, height: size * EmblemK.effectScale)
    }
}

// MARK: - 그리기

private struct ESeed { let angle, radius, speed, phase, sizeK, drift: Double }

private enum EmblemDraw {
    static let tau = Double.pi * 2

    static func seeds(_ effect: ParticleEffect) -> [ESeed] {
        (0..<14).map { i in
            var r = EmblemRNG(UInt64(i * 9973 + effect.rawValue * 131 + 17))
            return ESeed(angle: r.next() * tau, radius: r.next(), speed: 0.55 + r.next() * 0.9,
                         phase: r.next(), sizeK: r.next(), drift: r.next() - 0.5)
        }
    }

    static func pt(_ c: CGPoint, _ ang: Double, _ r: Double, sy: Double = 1) -> CGPoint {
        CGPoint(x: Double(c.x) + cos(ang) * r, y: Double(c.y) + sin(ang) * r * sy)
    }

    static func hexVertex(_ c: CGPoint, _ r: Double, _ i: Int) -> CGPoint {
        pt(c, (-90 + 60 * Double(i)) * .pi / 180, r)
    }

    static func hexPath(_ c: CGPoint, _ r: Double) -> Path {
        var p = Path()
        for i in 0..<6 {
            let v = hexVertex(c, r, i)
            if i == 0 { p.move(to: v) } else { p.addLine(to: v) }
        }
        p.closeSubpath()
        return p
    }

    static func emblem(_ g: inout GraphicsContext, sz: CGSize, c1: Color, c2: Color, t: Double, seed: Int, aura: Double) {
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let r = Double(min(sz.width, sz.height)) * Double(EmblemK.hexFrac)
        let pulse = 0.78 + 0.22 * sin(t * 2.1 + Double(seed))
        let ar = r * 1.65
        g.fill(Path(ellipseIn: CGRect(x: Double(c.x) - ar, y: Double(c.y) - ar, width: ar * 2, height: ar * 2)),
               with: .radialGradient(Gradient(colors: [c1.opacity(0.42 * pulse * aura), .clear]),
                                     center: c, startRadius: 0, endRadius: CGFloat(ar)))
        let hex = hexPath(c, r)
        g.fill(hex, with: .linearGradient(
            Gradient(colors: [c1.blended(with: .white, fraction: 0.22), c1, c2, c2.blended(with: .black, fraction: 0.38)]),
            startPoint: CGPoint(x: Double(c.x) - r, y: Double(c.y) - r),
            endPoint: CGPoint(x: Double(c.x) + r, y: Double(c.y) + r)))
        // 면 — 왼쪽 위(빛) 쪽은 밝게, 반대는 어둡게.
        let light = -135 * Double.pi / 180
        for i in 0..<6 {
            let a = hexVertex(c, r, i), b = hexVertex(c, r, (i + 1) % 6)
            let mid = (-60 + 60 * Double(i)) * .pi / 180
            let k = cos(mid - light)
            var f = Path()
            f.move(to: c); f.addLine(to: a); f.addLine(to: b); f.closeSubpath()
            g.fill(f, with: .color(k > 0 ? Color.white.opacity(0.16 * k) : Color.black.opacity(0.2 * -k)))
        }
        let table = hexPath(c, r * 0.6)
        g.fill(table, with: .color(c1.blended(with: c2, fraction: 0.5).opacity(0.55)))
        g.fill(table, with: .color(Color.white.opacity(0.07)))
        g.stroke(table, with: .color(Color.white.opacity(0.24)), lineWidth: 1)
        for i in 0..<6 {
            var l = Path()
            l.move(to: hexVertex(c, r * 0.6, i)); l.addLine(to: hexVertex(c, r, i))
            g.stroke(l, with: .color(Color.white.opacity(0.14)), lineWidth: 0.8)
        }
        // 도는 은빛 테두리.
        var rim = g
        rim.translateBy(x: c.x, y: c.y)
        rim.rotate(by: .degrees(t * 40 + Double(seed) * 30))
        rim.translateBy(x: -c.x, y: -c.y)
        rim.stroke(hex, with: .conicGradient(
            Gradient(colors: [Color.white.opacity(0.95), c1, c2, Color.white.opacity(0.4), c1, Color.white.opacity(0.95)]),
            center: c), style: StrokeStyle(lineWidth: min(sz.width, sz.height) * 0.05, lineJoin: .round))
        let tw = 0.5 + 0.5 * sin(t * 3.1 + Double(seed) * 1.7)
        sparkle(&g, hexVertex(c, r, 5), Double(min(sz.width, sz.height)) * (0.06 + 0.05 * tw),
                Color.white.opacity(0.55 + 0.45 * tw), 0)
    }

    static func shine(_ g: inout GraphicsContext, sz: CGSize, t: Double, seed: Int, strength: Double) {
        let local = (t + Double(seed) * 0.7).truncatingRemainder(dividingBy: EmblemK.shinePeriod)
        guard local <= EmblemK.shineSweep else { return }
        let p = local / EmblemK.shineSweep
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let r = Double(min(sz.width, sz.height)) * Double(EmblemK.hexFrac)
        let dx = 0.86, dy = 0.5
        let k = (p * 2.6 - 1.3) * r
        let pos = CGPoint(x: Double(c.x) + k * dx, y: Double(c.y) + k * dy)
        let w = r * 0.32
        var cg = g
        cg.clip(to: hexPath(c, r))
        cg.fill(Path(CGRect(origin: .zero, size: sz)), with: .linearGradient(
            Gradient(colors: [.clear, Color.white.opacity(strength), .clear]),
            startPoint: CGPoint(x: Double(pos.x) - dx * w, y: Double(pos.y) - dy * w),
            endPoint: CGPoint(x: Double(pos.x) + dx * w, y: Double(pos.y) + dy * w)))
    }

    // MARK: 이펙트

    static func effect(_ g: inout GraphicsContext, sz: CGSize, effect: ParticleEffect, c1: Color, c2: Color,
                       t: Double, front: Bool, a: Double, s: [ESeed], r: Double) {
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let half = Double(min(sz.width, sz.height)) / 2
        switch effect {
        case .sparkle:
            guard front else { return }
            for p in s.prefix(10) {
                let ang = p.angle + t * 0.25 * p.speed
                let rr = r * (1.15 + 0.65 * p.radius)
                let tw = max(0, sin(t * 2.2 * p.speed + p.phase * tau))
                sparkle(&g, pt(c, ang, rr), r * (0.1 + 0.14 * p.sizeK) * (0.3 + 0.7 * tw),
                        c1.blended(with: .white, fraction: 0.6).opacity(a * tw), t * 0.6)
            }
        case .snow:
            guard front else { return }
            for p in s.prefix(12) {
                let q = (t * p.speed * 0.16 + p.phase).truncatingRemainder(dividingBy: 1)
                let x = Double(c.x) + p.drift * half * 1.7 + sin(q * tau + p.phase * 6) * half * 0.08
                let y = q * Double(sz.height)
                snowflake(&g, CGPoint(x: x, y: y), r * (0.1 + 0.1 * p.sizeK),
                          Color.white.opacity(a * 0.9 * edgeFade(q)), t * p.speed)
            }
        case .sun:
            if !front {
                for k in 0..<12 {
                    let ang = (Double(k) * 30 + t * 12) * .pi / 180
                    let len = r * (1.55 + 0.18 * sin(t * 2 + Double(k)))
                    let from = pt(c, ang, r * 1.02), to = pt(c, ang, len)
                    var l = Path(); l.move(to: from); l.addLine(to: to)
                    g.stroke(l, with: .linearGradient(Gradient(colors: [c1.opacity(0.7 * a), .clear]), startPoint: from, endPoint: to),
                             style: StrokeStyle(lineWidth: k % 2 == 0 ? 2.4 : 1.2, lineCap: .round))
                }
            } else {
                for p in s.prefix(7) {
                    let (pos, q) = rising(p, t, c, half, 0.5)
                    let er = r * (0.04 + 0.04 * p.sizeK)
                    g.fill(circle(pos, er), with: .color(Color(hex: 0xFFD27A).blended(with: Color(hex: 0xFF7043), fraction: p.phase)
                        .opacity(a * (1 - q) * 0.9)))
                }
            }
        case .bubble:
            guard front else { return }
            for p in s.prefix(10) {
                let (pos, q) = rising(p, t, c, half, 0.36)
                let br = r * (0.06 + 0.1 * p.sizeK)
                g.stroke(circle(pos, br), with: .color(c1.blended(with: .white, fraction: 0.45).opacity(a * (1 - q) * 0.9)), lineWidth: 1)
                g.fill(circle(CGPoint(x: Double(pos.x) - br * 0.4, y: Double(pos.y) - br * 0.4), br * 0.28),
                       with: .color(Color.white.opacity(a * (1 - q) * 0.8)))
            }
        case .rift:
            let r1 = r * 1.5
            let rot1 = t * 22
            if !front {
                g.stroke(triangle(c, r1, rotDeg: rot1), with: .color(c1.opacity(0.75 * a)),
                         style: StrokeStyle(lineWidth: 1.4, dash: [6, 4]))
                g.stroke(triangle(c, r * 1.32, rotDeg: 180 - t * 15), with: .color(c2.opacity(0.5 * a)), lineWidth: 1)
            } else {
                for k in 0..<3 {
                    let v = pt(c, (-90 + 120 * Double(k) + rot1) * .pi / 180, r1)
                    let gl = 0.6 + 0.4 * sin(t * 3 + Double(k) * 2)
                    g.fill(circle(v, r * 0.22), with: .radialGradient(
                        Gradient(colors: [Color.white.opacity(a * gl), c1.opacity(a * 0.5 * gl), .clear]),
                        center: v, startRadius: 0, endRadius: CGFloat(r * 0.22)))
                }
            }
        case .crown:
            if !front {
                for k in 0..<8 {
                    let ang = (Double(k) * 45 + t * 10) * .pi / 180
                    let far = r * 2.0
                    var w = Path()
                    w.move(to: c); w.addLine(to: pt(c, ang - 0.13, far)); w.addLine(to: pt(c, ang + 0.13, far)); w.closeSubpath()
                    g.fill(w, with: .radialGradient(Gradient(colors: [c1.opacity(0.45 * a), .clear]),
                                                     center: c, startRadius: 0, endRadius: CGFloat(far)))
                }
            } else {
                for p in s.prefix(9) {
                    let ang = p.angle - t * 0.15
                    let rr = r * (1.25 + 0.55 * p.radius)
                    let tw = max(0, sin(t * 2.6 * p.speed + p.phase * tau))
                    sparkle(&g, pt(c, ang, rr), r * (0.09 + 0.12 * p.sizeK) * tw,
                            (p.sizeK > 0.5 ? Color(hex: 0xFFE38A) : Color(hex: 0xD9B8FF)).opacity(a * tw), 0)
                }
            }
        case .spark:
            guard front else { return }
            for k in 0..<4 {
                let period = 1.5 + Double(k) * 0.23
                let tt = t + Double(k) * 0.41
                let cycle = floor(tt / period)
                let lp = tt.truncatingRemainder(dividingBy: period) / period
                if lp > 0.16 { continue }
                var rng = EmblemRNG(UInt64(abs(Int(cycle) * 31 + k * 7)))
                let ang = rng.next() * tau
                let dirX = cos(ang), dirY = sin(ang)
                var bolt = Path()
                for j in 0...4 {
                    let d = r * (1.05 + 0.17 * Double(j))
                    let off: Double = (j == 0 || j == 4) ? 0 : (j % 2 == 0 ? 1 : -1) * r * 0.12
                    let p = CGPoint(x: Double(c.x) + dirX * d - dirY * off, y: Double(c.y) + dirY * d + dirX * off)
                    if j == 0 { bolt.move(to: p) } else { bolt.addLine(to: p) }
                }
                let fade = 1 - lp / 0.16
                g.stroke(bolt, with: .color(c1.opacity(a * 0.6 * fade)), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                g.stroke(bolt, with: .color(Color.white.opacity(a * fade)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
            }
            for p in s.prefix(6) {
                let pos = pt(c, p.angle + t * 2.4 * p.speed, r * (1.2 + 0.3 * p.radius))
                g.fill(circle(pos, r * 0.045), with: .color(c1.blended(with: .white, fraction: 0.3).opacity(a * 0.8)))
            }
        case .shadow:
            if !front {
                for p in s.prefix(6) {
                    let q = (t * 0.12 * p.speed + p.phase).truncatingRemainder(dividingBy: 1)
                    let pos = pt(c, p.angle, r * (0.8 + 0.95 * q))
                    let pr = r * (0.35 + 0.45 * q)
                    g.fill(circle(pos, pr), with: .radialGradient(
                        Gradient(colors: [Color(hex: 0x9AA7B5).opacity(0.22 * a * sin(q * .pi)), .clear]),
                        center: pos, startRadius: 0, endRadius: CGFloat(pr)))
                }
            } else {
                let orbit = r * 1.4
                for j in 0..<12 {
                    let pos = pt(c, t * 1.1 - Double(j) * 0.07, orbit, sy: 0.62)
                    let k = 1 - Double(j) / 12
                    g.fill(circle(pos, r * (j == 0 ? 0.075 : 0.045 * k)),
                           with: .color(Color.white.opacity(a * (j == 0 ? 1 : 0.45 * k))))
                }
                let head = pt(c, t * 1.1, orbit, sy: 0.62)
                g.fill(circle(head, r * 0.28), with: .radialGradient(Gradient(colors: [Color.white.opacity(0.5 * a), .clear]),
                                                                      center: head, startRadius: 0, endRadius: CGFloat(r * 0.28)))
            }
        case .meteor:
            guard front else { return }
            for k in 0..<7 {
                let p = s[k]
                let period = 2.4 + p.speed
                let q = (t + p.phase * period).truncatingRemainder(dividingBy: period) / period
                if q > 0.5 { continue }
                let m = q / 0.5
                let dx = -0.7071, dy = 0.7071
                let perp = p.drift * half * 1.4
                let sx = Double(c.x) + half * 0.85 + perp * 0.7071
                let sy = Double(c.y) - half * 0.85 + perp * 0.7071
                let len = half * 1.9
                let head = CGPoint(x: sx + dx * len * m, y: sy + dy * len * m)
                let tail = CGPoint(x: Double(head.x) - dx * r * 0.9, y: Double(head.y) - dy * r * 0.9)
                let al = a * sin(m * .pi)
                var l = Path(); l.move(to: tail); l.addLine(to: head)
                g.stroke(l, with: .linearGradient(Gradient(colors: [.clear, c1.blended(with: .white, fraction: 0.5).opacity(al)]),
                                                  startPoint: tail, endPoint: head),
                         style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                g.fill(circle(head, r * 0.05), with: .color(Color.white.opacity(al)))
            }
        case .heart:
            guard front else { return }
            for p in s.prefix(9) {
                let (pos, q) = rising(p, t, c, half, 0.42)
                let hs = r * (0.1 + 0.08 * p.sizeK) * (0.85 + 0.15 * sin(t * 6 + p.phase * tau))
                g.fill(heart(pos, hs), with: .color(Color(hex: 0xFF9EC2).blended(with: Color(hex: 0xFF4D79), fraction: p.phase).opacity(a * (1 - q))))
            }
        case .music:
            guard front else { return }
            for p in s.prefix(8) {
                let (pos, q) = rising(p, t, c, half, 0.36)
                note(&g, pos, r * (0.1 + 0.06 * p.sizeK), c1.blended(with: .white, fraction: 0.35).opacity(a * (1 - q)), p.sizeK > 0.5)
            }
        case .orbit:
            let rx = r * 1.62, ry = r * 0.5
            let tilt = -18 * Double.pi / 180
            if !front {
                var og = g
                og.translateBy(x: c.x, y: c.y)
                og.rotate(by: .radians(tilt))
                og.stroke(Path(ellipseIn: CGRect(x: -rx, y: -ry, width: rx * 2, height: ry * 2)),
                          with: .color(c1.opacity(0.45 * a)), lineWidth: 1.2)
            }
            for k in 0..<3 {
                let th = t * 0.8 + Double(k) * tau / 3
                let behind = sin(th) < 0
                if behind == front { continue }
                let ox = cos(th) * rx, oy = sin(th) * ry
                let pos = CGPoint(x: Double(c.x) + ox * cos(tilt) - oy * sin(tilt), y: Double(c.y) + ox * sin(tilt) + oy * cos(tilt))
                let mr = r * (k == 0 ? 0.11 : 0.075)
                let col = k == 0 ? c1.blended(with: .white, fraction: 0.4) : c2
                g.fill(circle(pos, mr), with: .color(col.opacity(a * (behind ? 0.5 : 1))))
            }
        }
    }

    // MARK: 도형

    static func circle(_ c: CGPoint, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: Double(c.x) - r, y: Double(c.y) - r, width: r * 2, height: r * 2))
    }

    static func rising(_ p: ESeed, _ t: Double, _ c: CGPoint, _ half: Double, _ k: Double) -> (CGPoint, Double) {
        let q = (t * p.speed * k + p.phase).truncatingRemainder(dividingBy: 1)
        let x = Double(c.x) + p.drift * half * 1.2 + sin((q + p.phase) * tau) * half * 0.12
        let y = Double(c.y) + half * 0.85 - q * half * 1.7
        return (CGPoint(x: x, y: y), q)
    }

    static func edgeFade(_ q: Double) -> Double { q < 0.12 ? q / 0.12 : (q > 0.88 ? (1 - q) / 0.12 : 1) }

    static func sparkle(_ g: inout GraphicsContext, _ c: CGPoint, _ r: Double, _ color: Color, _ rot: Double) {
        guard r > 0.3 else { return }
        var p = Path()
        for i in 0..<8 {
            let ang = rot + Double(i) * .pi / 4 - .pi / 2
            let v = pt(c, ang, i % 2 == 0 ? r : r * 0.26)
            if i == 0 { p.move(to: v) } else { p.addLine(to: v) }
        }
        p.closeSubpath()
        g.fill(p, with: .color(color))
    }

    static func snowflake(_ g: inout GraphicsContext, _ c: CGPoint, _ r: Double, _ color: Color, _ rot: Double) {
        let w = max(0.8, r * 0.16)
        var p = Path()
        for i in 0..<3 {
            let ang = rot + Double(i) * .pi / 3
            p.move(to: pt(c, ang + .pi, r)); p.addLine(to: pt(c, ang, r))
        }
        g.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: .round))
        var b = Path()
        for i in 0..<6 {
            let ang = rot + Double(i) * .pi / 3
            let base = pt(c, ang, r * 0.6)
            for sgn in [-1.0, 1.0] {
                b.move(to: base); b.addLine(to: pt(base, ang + sgn * 0.7, r * 0.3))
            }
        }
        g.stroke(b, with: .color(color), style: StrokeStyle(lineWidth: w * 0.8, lineCap: .round))
    }

    static func triangle(_ c: CGPoint, _ r: Double, rotDeg: Double) -> Path {
        var p = Path()
        for k in 0..<3 {
            let v = pt(c, (-90 + 120 * Double(k) + rotDeg) * .pi / 180, r)
            if k == 0 { p.move(to: v) } else { p.addLine(to: v) }
        }
        p.closeSubpath()
        return p
    }

    static func heart(_ c: CGPoint, _ r: Double) -> Path {
        let x = Double(c.x), y = Double(c.y)
        var p = Path()
        p.move(to: CGPoint(x: x, y: y + r * 0.9))
        p.addCurve(to: CGPoint(x: x, y: y - r * 0.35),
                   control1: CGPoint(x: x - r * 1.3, y: y + r * 0.1), control2: CGPoint(x: x - r * 0.9, y: y - r * 1.0))
        p.addCurve(to: CGPoint(x: x, y: y + r * 0.9),
                   control1: CGPoint(x: x + r * 0.9, y: y - r * 1.0), control2: CGPoint(x: x + r * 1.3, y: y + r * 0.1))
        p.closeSubpath()
        return p
    }

    static func note(_ g: inout GraphicsContext, _ c: CGPoint, _ r: Double, _ color: Color, _ beamed: Bool) {
        var hg = g
        hg.translateBy(x: c.x, y: c.y)
        hg.rotate(by: .degrees(-20))
        hg.fill(Path(ellipseIn: CGRect(x: -r * 0.62, y: -r * 0.42, width: r * 1.24, height: r * 0.84)), with: .color(color))
        let sx = Double(c.x) + r * 0.52
        let top = Double(c.y) - r * 2.1
        var stem = Path()
        stem.move(to: CGPoint(x: sx, y: Double(c.y) - r * 0.1)); stem.addLine(to: CGPoint(x: sx, y: top))
        g.stroke(stem, with: .color(color), lineWidth: max(1, r * 0.2))
        var flag = Path()
        flag.move(to: CGPoint(x: sx, y: top)); flag.addLine(to: CGPoint(x: sx + r * 0.75, y: top + r * 0.6))
        if beamed { flag.move(to: CGPoint(x: sx, y: top + r * 0.5)); flag.addLine(to: CGPoint(x: sx + r * 0.75, y: top + r * 1.1)) }
        g.stroke(flag, with: .color(color), style: StrokeStyle(lineWidth: max(1, r * 0.24), lineCap: .round))
    }
}

/// 결정적 난수(안정된 배치용).
private struct EmblemRNG {
    var state: UInt64
    init(_ seed: UInt64) { state = seed &* 2862933555777941757 &+ 3037000493 }
    mutating func next() -> Double {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Double(state >> 11) / Double(UInt64(1) << 53)
    }
}
