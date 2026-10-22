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
            // 카드 뒤 빛살(천천히 돈다)
            let rayA = phase == .cards ? 1 : pillar(t)
            if rayA > 0, let best {
                let center = phase == .cards ? CGPoint(x: size.width / 2, y: size.height * 0.45) : bc
                let n = best == "legendary" ? 18 : 12
                let spin = (t / 40).truncatingRemainder(dividingBy: 1) * 2 * .pi
                let len = maxD * 0.75
                let half = 2 * Double.pi / Double(n) * 0.12
                for i in 0..<n {
                    let a = Double(i) * 2 * .pi / Double(n) + spin
                    var p = Path()
                    p.move(to: center)
                    p.addLine(to: CGPoint(x: center.x + CGFloat(cos(a - half)) * len, y: center.y + CGFloat(sin(a - half)) * len))
                    p.addLine(to: CGPoint(x: center.x + CGFloat(cos(a + half)) * len, y: center.y + CGFloat(sin(a + half)) * len))
                    p.closeSubpath()
                    ctx.fill(p, with: .radialGradient(
                        Gradient(colors: [bestColor.opacity(0.10 * rayA), bestColor.opacity(0.03 * rayA), .clear]),
                        center: center, startRadius: 0, endRadius: len))
                }
            }
            // 빛기둥
            let pv = pillar(t) * (1 - boxOut(t))
            if pv > 0 {
                let w = size.width * 0.32 * pv
                ctx.fill(Path(CGRect(x: bc.x - w / 2, y: 0, width: w, height: bc.y)), with: .linearGradient(
                    Gradient(colors: [.clear, bestColor.opacity(0.55), .white.opacity(0.85)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: bc.y)))
                ctx.fill(Path(CGRect(x: bc.x - w * 0.12, y: 0, width: w * 0.24, height: bc.y)), with: .linearGradient(
                    Gradient(colors: [.clear, .white.opacity(0.9)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: bc.y)))
            }
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
    @Environment(\.displayScale) private var scale
    private static let cache = NSCache<NSString, UIImage>()

    private func base(_ size: CGSize, _ c: Color) -> UIImage {
        let side = max(size.width, size.height) * scale
        let key = "\(rarity)|\(Int(size.width * scale))x\(Int(size.height * scale))" as NSString
        if let hit = Self.cache.object(forKey: key) { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let pw = size.width * scale, ph = size.height * scale
        let img = UIGraphicsImageRenderer(size: CGSize(width: pw, height: ph), format: format).image { ctx in
            let mid = UIColor(red: 0x10 / 255.0, green: 0x0C / 255.0, blue: 0x2A / 255.0, alpha: 1).blended(with: UIColor(c), fraction: 0.28)
            StarCrystal.drawFacets(in: ctx.cgContext, seed: 77,
                                   colors: [UIColor(red: 0x0B / 255.0, green: 0x0E / 255.0, blue: 0x22 / 255.0, alpha: 1), mid,
                                            UIColor(red: 0x07 / 255.0, green: 0x09 / 255.0, blue: 0x16 / 255.0, alpha: 1)],
                                   rect: CGRect(x: (pw - side) / 2, y: (ph - side) / 2, width: side, height: side))
        }
        Self.cache.setObject(img, forKey: key)
        return img
    }

    var body: some View {
        let c = StarlightArt.rarityColor(rarity)
        GeometryReader { geo in
            let size = geo.size
            ZStack {
                Image(uiImage: base(size, c)).resizable().frame(width: size.width, height: size.height)
                Canvas { ctx, size in
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
        }
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(c.opacity(0.6), lineWidth: 1))
    }
}

private struct CardFront: View {
    let draw: StarlightStore.Draw
    let radius: CGFloat
    let big: Bool
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let item = store.item(draw.itemId)
        let c = StarlightArt.rarityColor(draw.rarity)
        let lang = locale.effectiveLanguage
        VStack(spacing: 3) {
            Text(rarityName(draw.rarity)).font(.minSans(big ? 13 : 10, .semibold)).foregroundStyle(c)
            ZStack {
                RadialGradient(colors: [c.opacity(0.35), .clear], center: .center, startRadius: 0, endRadius: big ? 90 : 45)
                if let item {
                    ItemArt(item: item).frame(width: big ? 130 : nil, height: big ? 130 : nil).padding(big ? 0 : 6)
                } else {
                    StardustIcon(tint: c).padding(big ? 40 : 20)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            Text(item?.name(in: lang) ?? draw.itemId)
                .font(.minSans(big ? 17 : 11.5, .semibold)).foregroundStyle(openInk)
                .multilineTextAlignment(.center).lineLimit(2)
            if big, let item {
                Text(item.desc(in: lang)).font(.minSans(12)).foregroundStyle(openSub)
                    .multilineTextAlignment(.center).lineLimit(3).padding(.top, 2)
            }
            Text(note(item))
                .font(.minSans(big ? 12 : 9.5, .medium))
                .foregroundStyle(draw.dup ? openDim : StarlightArt.dust)
                .multilineTextAlignment(.center).lineLimit(2)
                .padding(.top, 2)
        }
        .padding(big ? 18 : 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LinearGradient(colors: [c.opacity(0.22), Color(hex: 0x121821), Color(hex: 0x0E131B)], startPoint: .top, endPoint: .bottom))
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(c.opacity(0.55), lineWidth: 1))
    }

    private func note(_ item: StarlightStore.Item?) -> String {
        if draw.keys > 0 { return String(format: locale.t(.starlightKeysPlus), draw.keys) }
        if draw.dup { return String(format: locale.t(.starlightDupRefund), draw.refund) }
        if item?.kind == "telescope" { return locale.t(.starlightStacked) }
        return locale.t(.starlightNew)
    }
}
