import SwiftUI
import UIKit

/// 성운 상자 여는 연출(2026-10-22) — 상자깡 게임(브롤스타즈 상자 · 클래시 로얄 상자 · 하스스톤 팩)을 참고한 세 박자. Android `NebulaOpening.kt` 패리티.
///
///  1. **깨우기** — 떠 있는 크리스탈 상자를 톡톡 세 번. 두드릴 때마다 찌그러졌다 튀어 오르고, 금이 가고, 파편이 튄다.
///     새는 빛은 **이번에 나온 가장 높은 등급까지 한 칸씩** 오른다(일반 → 희귀 → 영웅 → 전설) — 몇 번째에서 멈추는지가 기대감.
///  2. **열림** — 뚜껑이 날아가고 등급 빛기둥이 솟는다. 영웅·전설은 충격파와 섬광.
///  3. **카드** — 엎어진 크리스탈 카드가 솟아오른다. 뒷면이 등급 빛으로 은은히 빛나고, 눌러서(또는 "모두 뒤집기") 한 장씩 뒤집는다.
///
/// 요청은 상자를 띄우자마자 보낸다(`state.draws` 가 오기 전엔 빛이 보랏빛에 머물고, 세 번째 두드림 뒤엔 응답을 기다린다).
/// 움직임은 전부 시각(TimelineView)에서 계산한다 — 상자 그림(Canvas)에 넘기는 값이 애니메이션 보간을 타지 않기 때문.
struct OpeningState: Equatable {
    let count: Int
    var draws: [StarlightStore.Draw]?

    static func == (a: OpeningState, b: OpeningState) -> Bool { a.count == b.count && (a.draws == nil) == (b.draws == nil) }
}

private let taps3 = 3
private let lilac = Color(hex: 0xC9A2FF)
private let openInk = Color(hex: 0xF0F0F0)
private let openSub = Color(hex: 0xB8C0CC)
private let openDim = Color(hex: 0x7D8696)

private func now() -> Double { Date().timeIntervalSinceReferenceDate }
private func clamp01(_ v: Double) -> Double { min(max(v, 0), 1) }
private func easeOut(_ v: Double) -> Double { let x = clamp01(v); return 1 - (1 - x) * (1 - x) * (1 - x) }
private func rarityRank(_ r: String) -> Int { StarlightStore.rarities.firstIndex(of: r) ?? 0 }

/// 튀는 결정 파편 하나(원점 = 화면 좌표, pt).
private struct Shard {
    let x0, y0, vx, vy, rot0, vr, size: CGFloat
    let color: Color
    let born, life: Double
}

struct NebulaOpening: View {
    let state: OpeningState
    let onDone: () -> Void

    private enum Phase { case wake, burst, cards }

    @ObservedObject private var locale = LocaleManager.shared
    @State private var phase = Phase.wake
    @State private var taps = 0
    @State private var skip = false
    @State private var tapAt: Double = -10
    @State private var burstAt: Double?
    @State private var cardsAt: Double?
    @State private var shards: [Shard] = []
    @State private var boxCenter: CGPoint = .zero
    @State private var flipped: [Bool] = []
    @State private var stars: [(CGFloat, CGFloat, CGFloat)] = (0..<70).map { _ in
        (CGFloat.random(in: 0...1), CGFloat.random(in: 0...1), CGFloat.random(in: 0...1))
    }

    private var bestIdx: Int? { state.draws?.map { rarityRank($0.rarity) }.max() }
    private var best: String? { bestIdx.map { StarlightStore.rarities[$0] } }
    private var bestColor: Color { best.map(StarlightArt.rarityColor) ?? lilac }
    private var allFlipped: Bool { !flipped.isEmpty && flipped.allSatisfy { $0 } }

    /// 두드림 k 번째의 빛 색 — 나온 최고 등급까지 한 칸씩. 응답 전엔 보랏빛.
    private func tapColor(_ k: Int) -> Color {
        guard let bestIdx, k > 0 else { return lilac }
        return StarlightArt.rarityColor(StarlightStore.rarities[min(k, bestIdx)])
    }

    var body: some View {
        ZStack {
            // 시간에 따라 움직이는 층(배경·상자·섬광)만 매 프레임 — 카드는 밖에 둬서 다시 그리지 않는다.
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: false)) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                ZStack {
                    Color(hex: 0x070912).opacity(0.96).ignoresSafeArea()
                    backdrop(t).ignoresSafeArea().allowsHitTesting(false)
                    if phase != .cards || boxOut(t) < 1 { box(t) }
                    let fl = flash(t)
                    if fl > 0 { Color.white.opacity(fl).ignoresSafeArea().allowsHitTesting(false) }
                }
            }
            if phase == .wake { wakeHint.offset(y: 130).transition(.opacity) }
            if phase == .cards, let draws = state.draws { cards(draws) }
            if phase != .cards && !skip { skipButton }
        }
        .onAppear { flipped = Array(repeating: false, count: state.draws?.count ?? 0) }
        .onChange(of: state) { s in
            // 응답이 왔다 — 카드 칸을 만들고, 이미 세 번 두드렸거나 건너뛰었으면 바로 연다.
            flipped = Array(repeating: false, count: s.draws?.count ?? 0)
            if s.draws != nil, phase == .wake, taps >= taps3 || skip {
                if skip { burst() } else { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { burst() } }
            }
        }
    }

    // MARK: - 시간 함수

    /// 두드림 반동 — 찌그러졌다(0.84) 튀어 오르는 감쇠 진동.
    private func squash(_ t: Double) -> CGFloat {
        let e = t - tapAt
        guard e >= 0, e < 1.6 else { return 1 }
        return CGFloat(1 - 0.16 * exp(-e * 5.5) * cos(e * 22))
    }

    private func wobble(_ t: Double) -> Double {
        let e = t - tapAt
        guard e >= 0, e < 2 else { return 0 }
        return (taps % 2 == 0 ? 7 : -7) * exp(-e * 3.2) * cos(e * 14)
    }

    private func lid(_ t: Double) -> Double { burstAt.map { easeOut((t - $0) / 0.65) } ?? 0 }
    private func pillar(_ t: Double) -> Double { burstAt.map { easeOut((t - $0) / 0.52) } ?? 0 }
    private func shock(_ t: Double) -> Double {
        guard let b = burstAt, best == "legendary" || best == "epic" else { return 0 }
        return easeOut((t - b) / 1.0)
    }
    private func flash(_ t: Double) -> Double {
        guard let b = burstAt else { return 0 }
        let peak = best == "legendary" ? 0.9 : 0.55
        return peak * (1 - clamp01((t - b) / 0.7))
    }
    private func boxOut(_ t: Double) -> Double { cardsAt.map { clamp01((t - $0) / 0.42) } ?? 0 }

    // MARK: - 배경 · 빛기둥 · 빛살 · 충격파 · 파편

    private func backdrop(_ t: Double) -> some View {
        Canvas { ctx, size in
            // 별밭
            for (x, y, k) in stars {
                let tw = 0.35 + 0.65 * (0.5 + 0.5 * sin(t * (0.6 + Double(k)) + Double(k) * 20))
                ctx.fill(StarlightArt.circle(CGPoint(x: x * size.width, y: y * size.height), 0.6 + 1.4 * k),
                         with: .color(.white.opacity(0.12 + 0.4 * tw * Double(k))))
            }
            let bc = boxCenter == .zero ? CGPoint(x: size.width / 2, y: size.height * 0.42) : boxCenter
            let minD = min(size.width, size.height), maxD = max(size.width, size.height)
            // 은은한 성운
            ctx.fill(StarlightArt.circle(bc, minD * 0.75), with: .radialGradient(
                Gradient(colors: [Color(hex: 0x5B2BB5).opacity(0.28), .clear]), center: bc, startRadius: 0, endRadius: minD * 0.75))
            // 빛살 — 열리는 순간 확 터졌다가(섬광·렌즈 플레어) 카드 뒤에선 은은하게 돈다(2026-10-23 — "광선이 너무 심플" 요청, Android 패리티).
            let bt = burstAt.map { t - $0 } ?? -1
            let rayA: Double = best == nil || bt < 0 ? 0 : bt < 0.22 ? bt / 0.22 * 1.6 : 0.5 + 1.1 * exp(-(bt - 0.22) * 1.5)
            if rayA > 0, let best {
                let cardC = CGPoint(x: size.width / 2, y: size.height * 0.45)
                let bo = CGFloat(boxOut(t))
                let center = CGPoint(x: bc.x + (cardC.x - bc.x) * bo, y: bc.y + (cardC.y - bc.y) * bo)
                let spin = (t / 40).truncatingRemainder(dividingBy: 1) * 360
                godRays(&ctx, center, maxD * 0.8, rayA, spin, t, rayPalette(best, bestColor))
                if bt <= 1.3 { lensFlare(&ctx, center, size, 1 - bt / 1.3, spin, bestColor) }
            }
            // 빛기둥 — 위로 넓어지는 기둥 + 솟구치는 빛줄기·반짝이
            let pv = pillar(t) * (1 - boxOut(t))
            if pv > 0 { pillarBeam(&ctx, bc, size, pv, t, bestColor) }
            // 충격파(영웅·전설)
            let sv = shock(t)
            if sv > 0, sv < 1 {
                let r = maxD * 0.7 * sv
                ctx.stroke(StarlightArt.circle(bc, r), with: .color(bestColor.opacity(0.8 * (1 - sv))), lineWidth: 10 * (1 - sv) + 1)
                ctx.stroke(StarlightArt.circle(bc, r * 0.93), with: .color(.white.opacity(0.5 * (1 - sv))), lineWidth: 3 * (1 - sv) + 0.5)
            }
            // 결정 파편
            for sh in shards {
                let age = t - sh.born
                guard age >= 0, age <= sh.life else { continue }
                let a = CGFloat(age)
                let x = sh.x0 + sh.vx * a
                let y = sh.y0 + sh.vy * a + 0.5 * 300 * a * a
                let alpha = clamp01(1 - age / sh.life)
                var g = ctx
                g.translateBy(x: x, y: y)
                g.rotate(by: .degrees(Double(sh.rot0 + sh.vr * a)))
                let r = sh.size
                var d = Path()
                d.move(to: CGPoint(x: 0, y: -r)); d.addLine(to: CGPoint(x: r * 0.45, y: 0))
                d.addLine(to: CGPoint(x: 0, y: r)); d.addLine(to: CGPoint(x: -r * 0.45, y: 0)); d.closeSubpath()
                g.fill(d, with: .color(sh.color.opacity(alpha)))
                var hi = Path()
                hi.move(to: CGPoint(x: 0, y: -r)); hi.addLine(to: CGPoint(x: r * 0.45, y: 0)); hi.addLine(to: .zero); hi.closeSubpath()
                g.fill(hi, with: .color(.white.opacity(alpha * 0.6)))
            }
        }
    }

    // MARK: - 상자

    private func box(_ t: Double) -> some View {
        let glowK = clamp01(Double(taps) / Double(taps3))
        let waiting = phase == .wake && taps >= taps3
        let shiver = waiting ? sin(t * 2 * .pi / 0.22) : 0
        let sq = squash(t)
        let out = CGFloat(boxOut(t))
        return NebulaBoxArt(
            glow: phase == .wake ? glowK : 1,
            glowColor: phase == .wake ? tapColor(taps) : bestColor,
            cracks: phase == .wake ? taps : taps3,
            lidOpen: lid(t)
        )
        .frame(width: 240, height: 240)
        .background(GeometryReader { geo in
            Color.clear
                .onAppear { boxCenter = CGPoint(x: geo.frame(in: .global).midX, y: geo.frame(in: .global).midY) }
                .onChange(of: geo.frame(in: .global)) { f in boxCenter = CGPoint(x: f.midX, y: f.midY) }
        })
        .scaleEffect(x: (2 - sq) * (1 - 0.35 * out), y: sq * (1 - 0.35 * out), anchor: UnitPoint(x: 0.5, y: 0.9))
        .rotationEffect(.degrees(wobble(t) + shiver * 2.5), anchor: UnitPoint(x: 0.5, y: 0.9))
        .offset(x: CGFloat(shiver) * 3, y: -40)
        .opacity(1 - Double(out))
        .contentShape(Rectangle())
        .onTapGesture { tap() }
        .allowsHitTesting(phase == .wake)
    }

    private var wakeHint: some View {
        VStack(spacing: 12) {
            Text(locale.t(taps >= taps3 ? .starlightWaiting : .starlightTapToWake))
                .font(.minSans(15, .medium)).foregroundStyle(openInk).multilineTextAlignment(.center)
            HStack(spacing: 8) {
                ForEach(0..<taps3, id: \.self) { i in
                    let on = i < taps
                    Circle()
                        .fill(on ? tapColor(i + 1) : Color.white.opacity(0.2))
                        .overlay(Circle().strokeBorder(on ? Color.white.opacity(0.6) : Color.white.opacity(0.27), lineWidth: 0.75))
                        .frame(width: 9, height: 9)
                }
            }
        }
        .allowsHitTesting(false)
    }

    private var skipButton: some View {
        VStack {
            HStack {
                Spacer()
                Button {
                    skip = true
                    Haptics.tick()
                    if state.draws != nil, phase == .wake { burst() }
                } label: {
                    Text(locale.t(.starlightSkip)).font(.minSans(13)).foregroundStyle(openSub)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(Color.white.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(12)
            }
            Spacer()
        }
    }

    // MARK: - 카드

    private func cards(_ draws: [StarlightStore.Draw]) -> some View {
        VStack(spacing: 0) {
            CardsGrid(draws: draws, flipped: flipped) { i, center in flip(i, center) }
            VStack(spacing: 10) {
                if !allFlipped { Text(locale.t(.starlightTapCard)).font(.minSans(12.5)).foregroundStyle(openSub) }
                PillButton(text: locale.t(allFlipped ? .starlightConfirm : .starlightFlipAll), primary: allFlipped) {
                    if allFlipped { onDone() } else { flipAll() }
                }
            }
            .padding(.bottom, 22)
        }
    }

    // MARK: - 동작

    private func spray(_ n: Int, _ color: Color, _ power: CGFloat, at: CGPoint? = nil) {
        let o = at ?? boxCenter
        let t = now()
        shards.removeAll { t - $0.born > $0.life }
        for _ in 0..<n {
            let a = CGFloat.random(in: 0..<(2 * .pi))
            let v = CGFloat.random(in: 0.35...1) * power
            shards.append(Shard(
                x0: o.x, y0: o.y, vx: cos(a) * v, vy: sin(a) * v - power * 0.35,
                rot0: .random(in: 0..<360), vr: .random(in: -360...360),
                size: .random(in: 3...9), color: Double.random(in: 0..<1) < 0.3 ? .white : color,
                born: t, life: Double.random(in: 0.9...1.5)))
        }
    }

    private func tap() {
        guard phase == .wake, taps < taps3 else { return }
        taps += 1
        tapAt = now()
        Haptics.medium()
        spray(10 + taps * 4, tapColor(taps), CGFloat(150 + taps * 60))
        if taps >= taps3, state.draws != nil {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) { burst() }
        }
    }

    private func burst() {
        guard phase == .wake else { return }
        phase = .burst
        burstAt = now()
        switch best {
        case "legendary":
            Haptics.heavy()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) { Haptics.celebrate() }
        case "epic": Haptics.celebrate()
        default: Haptics.medium()
        }
        spray(best == "legendary" ? 46 : 30, bestColor, best == "legendary" ? 520 : 380)
        DispatchQueue.main.asyncAfter(deadline: .now() + (skip ? 0.25 : 0.75)) {
            phase = .cards
            cardsAt = now()
            if skip { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { flipAll() } }
        }
    }

    private func flip(_ i: Int, _ center: CGPoint) {
        guard i < flipped.count, !flipped[i], let draws = state.draws else { return }
        withAnimation(.easeInOut(duration: 0.56)) { flipped[i] = true }
        let r = draws[i].rarity
        switch r {
        case "legendary":
            Haptics.celebrate()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { spray(28, StarlightArt.rarityColor(r), 300, at: center) }
        case "epic":
            Haptics.medium()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { spray(16, StarlightArt.rarityColor(r), 220, at: center) }
        default: Haptics.tick()
        }
    }

    private func flipAll() {
        for (k, i) in flipped.indices.filter({ !flipped[$0] }).enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.14 * Double(k)) {
                guard i < flipped.count else { return }
                withAnimation(.easeInOut(duration: 0.56)) { flipped[i] = true }
                Haptics.tick()
            }
        }
    }
}

// MARK: - 빛살 · 렌즈 플레어 · 빛기둥(Android drawGodRays / drawFlare / drawPillar 패리티)

/// 빛살 색 — 전설은 금·분홍·하늘이 번갈아(무지갯빛), 나머지는 등급 빛과 그 밝은 빛.
private func rayPalette(_ best: String, _ c: Color) -> [Color] {
    best == "legendary"
        ? [Color(hex: 0xFFD27A), Color(hex: 0xFF9ED8), Color(hex: 0x9FD3FF), Color(hex: 0xFFF0C2)]
        : [c, c.blended(with: .white, fraction: 0.45)]
}

private func wedge(_ c: CGPoint, _ ang: Double, _ half: Double, _ l: CGFloat) -> Path {
    var p = Path()
    p.move(to: c)
    p.addLine(to: CGPoint(x: c.x + CGFloat(cos(ang - half)) * l, y: c.y + CGFloat(sin(ang - half)) * l))
    p.addLine(to: CGPoint(x: c.x + CGFloat(cos(ang + half)) * l, y: c.y + CGFloat(sin(ang + half)) * l))
    p.closeSubpath()
    return p
}

/// 빛살 두 겹 — 넓고 부드러운 빛살(시계 방향, 길이가 일렁임) + 가늘고 날카로운 빛살(반대로 더 빨리) + 가운데 흰 심.
/// a 0..1.6(열리는 순간 1 넘게 → 은은하게 0.5).
private func godRays(_ ctx: inout GraphicsContext, _ center: CGPoint, _ len: CGFloat, _ a: Double, _ spinDeg: Double, _ t: Double, _ palette: [Color]) {
    let rot = spinDeg * .pi / 180
    let n = 14
    let step = 2 * Double.pi / Double(n)
    for i in 0..<n {
        let half = step * (i % 2 == 0 ? 0.2 : 0.11)
        let l = len * (i % 2 == 0 ? 1 : 0.7) * CGFloat(0.85 + 0.15 * sin(t * 1.3 + Double(i) * 1.7))
        ctx.fill(wedge(center, Double(i) * step + rot, half, l), with: .radialGradient(
            Gradient(colors: [.white.opacity(min(0.22 * a, 1)), palette[i % palette.count].opacity(min(0.14 * a, 1)), .clear]),
            center: center, startRadius: 0, endRadius: l))
    }
    let m = 28
    let st = 2 * Double.pi / Double(m)
    for i in 0..<m {
        let l = len * CGFloat(0.45 + 0.4 * Double((i * 37) % 11) / 10) * CGFloat(0.8 + 0.2 * sin(t * 2.1 + Double(i)))
        ctx.fill(wedge(center, Double(i) * st + st / 2 - rot * 1.7, st * 0.05, l), with: .radialGradient(
            Gradient(colors: [.white.opacity(min(0.4 * a, 1)), palette[(i + 1) % palette.count].opacity(min(0.12 * a, 1)), .clear]),
            center: center, startRadius: 0, endRadius: l))
    }
    let core = len * 0.28
    ctx.fill(StarlightArt.circle(center, core), with: .radialGradient(
        Gradient(colors: [palette[0].opacity(min(0.4 * a, 0.8)), .clear]), center: center, startRadius: 0, endRadius: core))
    ctx.fill(StarlightArt.circle(center, core * 0.3), with: .radialGradient(
        Gradient(colors: [.white.opacity(min(0.5 * a, 0.9)), .clear]), center: center, startRadius: 0, endRadius: core * 0.3))
}

/// 렌즈 플레어 — 열리는 순간 가로 빛줄 + 큰 네 꼭지 반짝이 + 퍼지는 고리 둘, f 1 → 0 으로 사라진다.
private func lensFlare(_ ctx: inout GraphicsContext, _ center: CGPoint, _ size: CGSize, _ f: Double, _ spinDeg: Double, _ c: Color) {
    let e = f * f
    let hw = size.width * CGFloat(0.4 + 0.6 * f)
    let hh = 3 * CGFloat(0.5 + f)
    ctx.fill(Path(CGRect(x: center.x - hw, y: center.y - hh, width: hw * 2, height: hh * 2)), with: .linearGradient(
        Gradient(colors: [.clear, c.opacity(0.6 * e), .white.opacity(0.95 * e), c.opacity(0.6 * e), .clear]),
        startPoint: CGPoint(x: center.x - hw, y: center.y), endPoint: CGPoint(x: center.x + hw, y: center.y)))
    let minD = min(size.width, size.height)
    let r = minD * 0.22 * CGFloat(0.6 + 0.4 * f)
    var g = ctx
    g.translateBy(x: center.x, y: center.y)
    g.rotate(by: .degrees(spinDeg * 2))
    g.fill(StarlightArt.sparkle(.zero, r), with: .color(.white.opacity(0.9 * e)))
    g.rotate(by: .degrees(45))
    g.fill(StarlightArt.sparkle(.zero, r * 0.55), with: .color(c.opacity(0.8 * e)))
    let rr = minD * CGFloat(0.18 + 0.35 * (1 - f))
    ctx.stroke(StarlightArt.circle(center, rr), with: .color(c.opacity(0.35 * e)), lineWidth: 2)
    ctx.stroke(StarlightArt.circle(center, rr * 1.35), with: .color(.white.opacity(0.18 * e)), lineWidth: 1)
}

/// 빛기둥 — 위로 갈수록 넓어지는 기둥, 안에서 빛줄기와 반짝이가 솟구친다. pv 0..1.
private func pillarBeam(_ ctx: inout GraphicsContext, _ bc: CGPoint, _ size: CGSize, _ pv: Double, _ t: Double, _ c: Color) {
    let bw = size.width * 0.16 * CGFloat(pv)
    let tw = size.width * 0.5 * CGFloat(pv)
    func trap(_ b: CGFloat, _ top: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: bc.x - b / 2, y: bc.y)); p.addLine(to: CGPoint(x: bc.x + b / 2, y: bc.y))
        p.addLine(to: CGPoint(x: bc.x + top / 2, y: 0)); p.addLine(to: CGPoint(x: bc.x - top / 2, y: 0))
        p.closeSubpath()
        return p
    }
    ctx.fill(trap(bw, tw), with: .linearGradient(Gradient(colors: [.clear, c.opacity(0.45), .white.opacity(0.8)]),
                                                 startPoint: .zero, endPoint: CGPoint(x: 0, y: bc.y)))
    ctx.fill(trap(bw * 0.24, tw * 0.16), with: .linearGradient(Gradient(colors: [.clear, .white.opacity(0.95)]),
                                                              startPoint: .zero, endPoint: CGPoint(x: 0, y: bc.y)))
    // 솟구치는 빛줄기
    for i in 0..<9 {
        let ph = (t * (1.4 + Double(i % 3) * 0.35) + Double(i) * 0.37).truncatingRemainder(dividingBy: 1)
        let y = bc.y * CGFloat(1 - ph)
        let wAt = bw + (tw - bw) * CGFloat(ph)
        let x = bc.x + (CGFloat((i * 53) % 100) / 100 - 0.5) * wAt * 0.8
        let l = CGFloat(40 + (i % 4) * 22)
        var line = Path()
        line.move(to: CGPoint(x: x, y: y))
        line.addLine(to: CGPoint(x: x, y: y + l))
        ctx.stroke(line, with: .linearGradient(Gradient(colors: [.white.opacity(0.85 * pv), .clear]),
                                               startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + l)),
                   style: StrokeStyle(lineWidth: 1.5 + CGFloat(i % 2), lineCap: .round))
        if i % 3 == 0 { ctx.fill(StarlightArt.sparkle(CGPoint(x: x, y: y), 5), with: .color(.white.opacity(0.9 * pv))) }
    }
}

private struct PillButton: View {
    let text: String
    let primary: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.minSans(15, .semibold))
                .foregroundStyle(primary ? Color(hex: 0x1A0B33) : openInk)
                .padding(.horizontal, 28).padding(.vertical, 12)
                .background(primary
                            ? LinearGradient(colors: [Color(hex: 0xF1E3FF), lilac], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [lilac.opacity(0.2), lilac.opacity(0.13)], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: Capsule())
                .overlay(Capsule().strokeBorder(primary ? Color.clear : lilac.opacity(0.6), lineWidth: 0.75))
        }
        .buttonStyle(.plain)
    }
}

/// 카드 격자 — 1장은 크게, 2~4장은 두 줄, 5장부터 세 줄. 카드가 아래(상자 쪽)에서 솟아오른다.
private struct CardsGrid: View {
    let draws: [StarlightStore.Draw]
    let flipped: [Bool]
    let onFlip: (Int, CGPoint) -> Void

    var body: some View {
        GeometryReader { geo in
            let cols = draws.count == 1 ? 1 : draws.count <= 4 ? 2 : 3
            let gap: CGFloat = 10
            let w = cols == 1 ? min(250, geo.size.width - 60) : (geo.size.width - 32 - gap * CGFloat(cols - 1)) / CGFloat(cols)
            let h = w * 1.42
            let rows = stride(from: 0, to: draws.count, by: cols).map { Array($0..<min($0 + cols, draws.count)) }
            ScrollView(showsIndicators: false) {
                VStack(spacing: gap) {
                    ForEach(rows.indices, id: \.self) { r in
                        HStack(spacing: gap) {
                            ForEach(rows[r], id: \.self) { i in
                                FlipCard(draw: draws[i], index: i, flipped: i < flipped.count && flipped[i], big: cols == 1, w: w, h: h) { c in
                                    onFlip(i, c)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 24)
                .frame(maxWidth: .infinity, minHeight: geo.size.height)
            }
        }
    }
}

/// 뒤집히는 카드 — 각도(0~180)를 애니메이션 값으로 받아 90° 에서 앞뒤를 바꾼다.
private struct FlipFace<Back: View, Front: View>: View, Animatable {
    var angle: Double
    let back: Back
    let front: Front
    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    var body: some View {
        ZStack {
            if angle < 90 { back } else { front.rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0)) }
        }
        .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.45)
    }
}

private struct FlipCard: View {
    let draw: StarlightStore.Draw
    let index: Int
    let flipped: Bool
    let big: Bool
    let w: CGFloat
    let h: CGFloat
    let onFlip: (CGPoint) -> Void
    @State private var entered = false
    @State private var rim = false
    @State private var center: CGPoint = .zero

    var body: some View {
        let c = StarlightArt.rarityColor(draw.rarity)
        let radius: CGFloat = big ? 22 : 14
        let shiny = draw.rarity == "legendary" || draw.rarity == "epic"
        FlipFace(angle: flipped ? 180 : 0,
                 back: CardBack(rarity: draw.rarity, radius: radius),
                 front: CardFront(draw: draw, radius: radius, big: big))
            .frame(width: w, height: h)
            .overlay {
                // 등급 테두리 빛 — 영웅·전설만 숨쉬듯(3.6초, 작은 폭)
                if shiny {
                    RoundedRectangle(cornerRadius: radius).strokeBorder(c.opacity(rim ? 0.85 : 0.55), lineWidth: 1.5)
                        .animation(.easeInOut(duration: 3.6).repeatForever(autoreverses: true), value: rim)
                }
            }
            .background(GeometryReader { geo in
                Color.clear
                    .onAppear { center = CGPoint(x: geo.frame(in: .global).midX, y: geo.frame(in: .global).midY) }
                    .onChange(of: geo.frame(in: .global)) { f in center = CGPoint(x: f.midX, y: f.midY) }
            })
            .scaleEffect(entered ? 1 : 0.45)
            .rotationEffect(.degrees(entered ? 0 : (index % 2 == 0 ? -14 : 14)))
            .offset(y: entered ? 0 : 320)
            .opacity(entered ? 1 : 0)
            .contentShape(Rectangle())
            .onTapGesture { if !flipped { onFlip(center) } }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.09 * Double(index)) {
                    Haptics.tick()
                    withAnimation(.spring(response: 0.6, dampingFraction: 0.62)) { entered = true }
                }
                rim = true
            }
    }
}

/// 카드 뒷면 — 어두운 크리스탈 바탕에 등급 빛이 비치고, 가운데 별 문장. 등급이 뒤집기 전에 살짝 보인다(기대감).
private struct CardBack: View {
    let rarity: String
    let radius: CGFloat

    var body: some View {
        let c = StarlightArt.rarityColor(rarity)
        ZStack {
            Canvas { ctx, size in
                for (p, col) in Self.roseFacets(size, c) {
                    ctx.fill(p, with: .color(col))
                    ctx.stroke(p, with: .color(.white.opacity(0.09)), lineWidth: 1)
                }
                let ce = CGPoint(x: size.width / 2, y: size.height / 2)
                let m = min(size.width, size.height)
                let r = m * 0.42
                ctx.fill(StarlightArt.circle(ce, r), with: .radialGradient(
                    Gradient(colors: [c.opacity(0.42), c.opacity(0.12), .clear]), center: ce, startRadius: 0, endRadius: r))
                ctx.fill(StarlightArt.sparkle(ce, m * 0.17), with: .color(.white.opacity(0.92)))
                ctx.fill(StarlightArt.sparkle(ce, m * 0.09), with: .color(c))
                let inset = m * 0.06
                ctx.stroke(Path(roundedRect: CGRect(x: inset, y: inset, width: size.width - inset * 2, height: size.height - inset * 2),
                                cornerRadius: inset * 1.4), with: .color(c.opacity(0.35)), lineWidth: 1)
            }
            // 비스듬히 지나가는 광택
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                let period = rarity == "legendary" ? 2.4 : 3.8
                let x = -0.6 + 2.2 * (tl.date.timeIntervalSinceReferenceDate / period).truncatingRemainder(dividingBy: 1)
                LinearGradient(colors: [.clear, .white.opacity(rarity == "legendary" ? 0.28 : 0.14), .clear],
                               startPoint: UnitPoint(x: x - 0.3, y: 0), endPoint: UnitPoint(x: x + 0.3, y: 1))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(c.opacity(0.6), lineWidth: 1))
    }

    /// 카드 뒷면 무늬 — 가운데에서 퍼지는 **대칭** 크리스탈 면(장미창). 예전 무작위 파편은 밝은 심이 왼쪽 위로 쏠려
    /// "카드가 한쪽으로 치우치고 가운데가 빈" 느낌이었다(2026-10-23). 빛은 왼쪽 위에서. Android `drawRoseFacets` 와 같은 식.
    private static func roseFacets(_ size: CGSize, _ tint: Color) -> [(Path, Color)] {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let m = min(size.width, size.height)
        let n = 8
        let radii: [CGFloat] = [0, m * 0.2, m * 0.43, m * 0.72, size.width + size.height]
        func pt(_ k: Int, _ i: Int) -> CGPoint {
            let a = (Double(i) + Double(k % 2) * 0.5) / Double(n) * 2 * .pi - .pi / 2
            return CGPoint(x: c.x + CGFloat(cos(a)) * radii[k], y: c.y + CGFloat(sin(a)) * radii[k])
        }
        let deep = Color(hex: 0x070916)
        let mid = Color(hex: 0x100C2A).blended(with: tint, fraction: 0.32)
        func tri(_ a: CGPoint, _ b: CGPoint, _ d: CGPoint, _ k: Int, _ parity: Int) -> (Path, Color) {
            let cx = Double((a.x + b.x + d.x) / 3 - c.x), cy = Double((a.y + b.y + d.y) / 3 - c.y)
            let light = 0.5 + 0.5 * cos(atan2(cy, cx) + 3 * .pi / 4) // 왼쪽 위가 밝다
            let v = min(max((0.25 + 0.6 * light + (parity == 0 ? 0.1 : -0.06)) * (1 - Double(k) * 0.2), 0), 1)
            var p = Path()
            p.move(to: a); p.addLine(to: b); p.addLine(to: d); p.closeSubpath()
            return (p, deep.blended(with: mid, fraction: v))
        }
        var out: [(Path, Color)] = []
        for k in 0..<(radii.count - 1) {
            for i in 0..<n {
                if k % 2 == 0 {
                    out.append(tri(pt(k, i), pt(k + 1, i), pt(k, i + 1), k, 0))
                    if k > 0 { out.append(tri(pt(k + 1, i), pt(k, i + 1), pt(k + 1, i + 1), k, 1)) }
                } else {
                    out.append(tri(pt(k + 1, i), pt(k, i), pt(k + 1, i + 1), k, 0))
                    out.append(tri(pt(k, i), pt(k + 1, i + 1), pt(k, i + 1), k, 1))
                }
            }
        }
        return out
    }
}

/// 카드 앞면. 중복(이미 가진 것)이면 뒤집힌 뒤 **바뀌는 과정**을 보여 준다(2026-10-23 — 회색 글씨 한 줄로는 몰랐다는 요청):
/// 그림은 가운데 그대로 두고 빛이 빠지며(채도·밝기↓) "이미 있어요" 도장이 찍히고, 그림에서 별가루 알갱이가 흘러나와
/// 아래 금빛 "별가루 +N" 칩으로 빨려 든다(칩이 튀어 오르며 숫자가 올라간다). Android `CardFront` 와 같은 시간표.
private struct CardFront: View {
    let draw: StarlightStore.Draw
    let radius: CGFloat
    let big: Bool
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var startAt: Double?
    @State private var running = false
    @State private var artFrame: CGRect = .zero
    @State private var chipFrame: CGRect = .zero

    private static let space = "starlightCardFront"

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60, paused: !running)) { tl in
            content(progress(tl.date.timeIntervalSinceReferenceDate))
        }
        .onAppear {
            guard draw.dup, startAt == nil else { return }
            // 뒤집힌 뒤 0.35초 쉬고 1.5초 동안 바뀐다.
            startAt = now() + 0.35
            running = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { running = false }
        }
    }

    private func progress(_ t: Double) -> Double {
        guard draw.dup else { return 1 }
        guard let startAt else { return 0 }
        if !running { return 1 }
        return clamp01((t - startAt) / 1.5)
    }

    private func content(_ t: Double) -> some View {
        let item = store.item(draw.itemId)
        let c = StarlightArt.rarityColor(draw.rarity)
        let lang = locale.effectiveLanguage
        let fade = draw.dup ? clamp01((t - 0.05) / 0.4) : 0
        return ZStack {
            VStack(spacing: 3) {
                Text(rarityName(draw.rarity)).font(.minSans(big ? 13 : 10, .semibold)).foregroundStyle(c)
                ZStack {
                    RadialGradient(colors: [c.opacity(0.35 * (1 - fade * 0.7)), .clear], center: .center, startRadius: 0, endRadius: big ? 90 : 45)
                    Group {
                        if let item {
                            ItemArt(item: item).frame(width: big ? 130 : nil, height: big ? 130 : nil).padding(big ? 0 : 6)
                        } else {
                            StardustIcon(tint: c).padding(big ? 40 : 20)
                        }
                    }
                    .saturation(1 - 0.85 * fade)
                    .opacity(1 - 0.45 * fade)
                    .scaleEffect(1 - 0.12 * fade)
                    if draw.dup {
                        OwnedStamp(big: big, appear: clamp01((t - 0.12) / 0.18))
                            .frame(maxHeight: .infinity, alignment: .bottom)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(GeometryReader { g in
                    Color.clear
                        .onAppear { artFrame = g.frame(in: .named(Self.space)) }
                        .onChange(of: g.frame(in: .named(Self.space))) { artFrame = $0 }
                })
                Text(item?.name(in: lang) ?? draw.itemId)
                    .font(.minSans(big ? 17 : 11.5, .semibold)).foregroundStyle(openInk.opacity(1 - 0.25 * fade))
                    .multilineTextAlignment(.center).lineLimit(2)
                    .frame(maxWidth: .infinity)
                if big, let item {
                    Text(draw.dup ? locale.t(.starlightDupExplain) : item.desc(in: lang)).font(.minSans(12)).foregroundStyle(openSub)
                        .multilineTextAlignment(.center).lineLimit(3).padding(.top, 2)
                        .frame(maxWidth: .infinity)
                }
                if draw.dup {
                    DustChip(shown: Int(Double(draw.refund) * clamp01((t - 0.55) / 0.4)), pop: clamp01((t - 0.5) / 0.5), big: big)
                        .background(GeometryReader { g in
                            Color.clear
                                .onAppear { chipFrame = g.frame(in: .named(Self.space)) }
                                .onChange(of: g.frame(in: .named(Self.space))) { chipFrame = $0 }
                        })
                        .padding(.top, big ? 6 : 2)
                } else {
                    Text(note(item))
                        .font(.minSans(big ? 12 : 9.5, .medium))
                        .foregroundStyle(StarlightArt.dust)
                        .multilineTextAlignment(.center).lineLimit(2)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 2)
                }
            }
            .padding(big ? 18 : 8)
            // 별가루 알갱이 — 그림에서 흩어졌다가 칩으로 빨려 든다.
            if draw.dup, t > 0.08, t < 0.99, artFrame != .zero, chipFrame != .zero {
                Canvas { ctx, _ in
                    dustFlow(&ctx, t, from: CGPoint(x: artFrame.midX, y: artFrame.midY), radius: min(artFrame.width, artFrame.height) / 2,
                             to: CGPoint(x: chipFrame.minX + chipFrame.width * 0.28, y: chipFrame.midY), big: big)
                }
                .allowsHitTesting(false)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .coordinateSpace(name: Self.space)
        .background(LinearGradient(colors: [c.opacity(0.22 * (1 - fade * 0.6)), Color(hex: 0x121821), Color(hex: 0x0E131B)],
                                   startPoint: .top, endPoint: .bottom))
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(c.opacity(0.55), lineWidth: 1))
    }

    private func note(_ item: StarlightStore.Item?) -> String {
        if draw.keys > 0 { return String(format: locale.t(.starlightKeysPlus), draw.keys) }
        if item?.kind == "telescope" { return locale.t(.starlightStacked) }
        return locale.t(.starlightNew)
    }
}

/// "이미 있어요" 도장 — 그림 아래쪽 가운데, 찍히듯 커졌다 제자리.
private struct OwnedStamp: View {
    let big: Bool
    let appear: Double
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let sc = appear < 1 ? 1.6 - 0.6 * appear : 1
        Text(locale.t(.starlightDupOwned))
            .font(.minSans(big ? 12 : 9, .semibold))
            .foregroundStyle(Color(hex: 0xE6E9F0))
            .lineLimit(1)
            .padding(.horizontal, big ? 12 : 7).padding(.vertical, big ? 4 : 2)
            .background(Color(hex: 0x1A1F2B).opacity(0.8), in: Capsule())
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.35), lineWidth: 0.75))
            .scaleEffect(sc)
            .opacity(appear)
    }
}

/// 금빛 별가루 칩 — 튀어 오르며 나타나고 숫자가 shown 까지 올라간다.
private struct DustChip: View {
    let shown: Int
    let pop: Double
    let big: Bool
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        // 0.6 → 1.15 → 1 로 통 튀는 크기
        let sc = pop <= 0 ? 0.6 : pop < 0.5 ? 0.6 + 0.55 * (pop / 0.5) : 1.15 - 0.15 * ((pop - 0.5) / 0.5)
        HStack(spacing: big ? 5 : 3) {
            StardustIcon().frame(width: big ? 18 : 13, height: big ? 18 : 13)
            Text(String(format: locale.t(.starlightDustPlus), shown))
                .font(.minSans(big ? 14 : 10.5, .bold))
                .foregroundStyle(StarlightArt.dust)
                .lineLimit(1)
        }
        .padding(.leading, big ? 8 : 5).padding(.trailing, big ? 12 : 8).padding(.vertical, 3)
        .background(LinearGradient(colors: [Color(hex: 0xFFC75A).opacity(0.25), Color(hex: 0xFFE6A8).opacity(0.15)],
                                   startPoint: .leading, endPoint: .trailing), in: Capsule())
        .overlay(Capsule().strokeBorder(StarlightArt.dust.opacity(0.35 + 0.45 * pop), lineWidth: 1))
        .scaleEffect(sc)
        .opacity(0.35 + 0.65 * min(pop, 1))
    }
}

/// 별가루 흐름 — 알갱이마다 그림 둘레 한 점에서 태어나 바깥으로 살짝 튀었다가 곡선으로 칩에 들어간다(도착하며 작아짐).
/// 위치는 t(0..1) 만의 함수. Android `drawDustFlow` 와 같은 식.
private func dustFlow(_ ctx: inout GraphicsContext, _ t: Double, from: CGPoint, radius: CGFloat, to: CGPoint, big: Bool) {
    let n = big ? 22 : 14
    for j in 0..<n {
        let start = 0.08 + 0.3 * Double((j * 7) % n) / Double(n)
        let p = clamp01((t - start) / 0.5)
        if p <= 0 || p >= 1 { continue }
        let a = Double(j) * 2.399963 // 황금각으로 고르게
        let r0 = radius * CGFloat(0.25 + 0.35 * Double((j * 5) % 7) / 7)
        let s = CGPoint(x: from.x + CGFloat(cos(a)) * r0, y: from.y + CGFloat(sin(a)) * r0)
        let ctrl = CGPoint(x: from.x + CGFloat(cos(a)) * radius * 1.05, y: from.y + CGFloat(sin(a)) * radius * 0.9)
        let e = CGFloat(1 - (1 - p) * (1 - p))
        let u = 1 - e
        let pos = CGPoint(x: u * u * s.x + 2 * u * e * ctrl.x + e * e * to.x,
                          y: u * u * s.y + 2 * u * e * ctrl.y + e * e * to.y)
        let size = radius * (big ? 0.032 : 0.05) * CGFloat(1 - 0.5 * p) * (j % 3 == 0 ? 1.5 : 1)
        let alpha = (p < 0.15 ? p / 0.15 : 1) * (1 - p * 0.4)
        ctx.fill(StarlightArt.circle(pos, size * 1.8), with: .color(StarlightArt.dust.opacity(0.18 * alpha)))
        ctx.fill(StarlightArt.sparkle(pos, size * 1.7), with: .color(Color(hex: 0xFFF3D0).opacity(alpha)))
    }
}
