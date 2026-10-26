import SwiftUI
import UIKit

/// 깨우기 연출(2026-10-27 잠든 우주 — 별 마법진 위로 뜬 수정구, 시안 그대로). 세 박자:
///
///  1. **두드리기** — 수정구를 세 번 두드린다. 누른 자리에서 금이 갈라지고(방사형 + 거미줄), 두드릴수록 안의 성운이 빨리 돈다.
///  2. **깨짐** — 결과 등급 빛으로 모였다가(1초) 누른 자리를 중심으로 유리가 불규칙한 조각으로 부서진다.
///     등급이 높을수록 빛이 넓고 화려하다(일반: 흰 빛 · 희귀: 빛줄기 8 · 영웅: 보랏빛 번쩍임 + 14 · 전설: 금빛 섬광 + 22 + 금가루).
///  3. **카드** — 조각이 거의 다 떨어지면 엎어진 카드가 솟는다. 눌러서(또는 "모두 뒤집기") 한 장씩 뒤집는다.
///     이미 가진 것은 그림의 빛이 빠지며 별가루로 바뀌는 과정을 보여 준다.
///
/// 요청은 창을 띄우자마자 보낸다(`state.draws` 가 오기 전에 세 번 두드리면 금이 천천히 번지며 수정구가 떨고 기다린다).
/// 건너뛰기 = 깨지는 장면 없이 카드로(카드는 자동으로 뒤집힌다). Android `VoyageOpening.kt` 와 같은 흐름.
struct OpeningState: Equatable {
    let count: Int
    let route: String
    var draws: [StarlightStore.Draw]?

    static func == (a: OpeningState, b: OpeningState) -> Bool {
        a.count == b.count && a.route == b.route && (a.draws == nil) == (b.draws == nil)
    }
}

private let taps3 = 3
/// 별가루 화면과 같은 잔별(같은 시드) — 창이 떠도 하늘이 그대로 이어진다.
private let openingSky = SkyStars(seed: 11)

private func now() -> Double { Date().timeIntervalSinceReferenceDate }
private func clamp01(_ v: Double) -> Double { min(max(v, 0), 1) }

struct VoyageOpening: View {
    let state: OpeningState
    let onDone: () -> Void

    @StateObject private var engine: OrbEngine
    @ObservedObject private var locale = LocaleManager.shared
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var skip = false
    @State private var flipped: [Bool] = []
    /// 카드가 솟기 시작한 수정구 시각 — 캔버스(받침이 지워지고 후광이 카드 뒤로 옮겨 가는 0.7초)가 이걸로 센다.
    @State private var cardStart: CGFloat?
    @State private var cardsVisible = false
    @State private var sparks = SparkField()

    init(state: OpeningState, onDone: @escaping () -> Void) {
        self.state = state
        self.onDone = onDone
        _engine = StateObject(wrappedValue: OrbEngine(seed: UInt64.random(in: 1...UInt64.max), interactive: true,
                                                      rare: state.route == "rare"))
    }

    private var best: String? {
        state.draws?.max { voyageRank($0.rarity) < voyageRank($1.rarity) }?.rarity
    }
    private var cardsShown: Bool { engine.cardsReady && state.draws != nil }
    private var allFlipped: Bool { !flipped.isEmpty && flipped.allSatisfy { $0 } }

    var body: some View {
        let tint = RouteTint.of(state.route)
        let eng = engine
        let sp = sparks
        let start = cardStart
        let waking = eng.phase == .idle || eng.phase == .wait
        GeometryReader { geo in
            // 수정구 무대(시안 단위) → 화면 pt: 가로를 꽉 채우되 세로가 짧으면 줄인다. 구슬은 화면 40% 높이.
            let w = geo.size.width
            let h = geo.size.height
            let k = min(w / orbW, h / 560)
            let ox = w / 2 - orbCX * k
            let oy = h * 0.40 - orbCY * k
            let origin = geo.frame(in: .global).origin
            let safe = geo.safeAreaInsets
            ZStack {
                Color(hex: 0x04060E).opacity(0.98)

                // ── 하늘 · 수정구 · 깨짐 ──
                OrbCanvas(frames: eng.frames) { c, size in
                    drawSpace(&c, size, orb: eng, sky: openingSky, focusY: oy + orbCY * k, deep: true)
                    let ci = Self.cardProgress(eng.t, start)
                    eng.draw(&c, k: k, ox: ox, oy: oy, viewW: size.width, viewH: size.height,
                             stageAlpha: 1 - ci, auraShift: ci, auraY: (size.height * 0.45 - oy) / k)
                }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { v in tapOrb(v.location, k: k, ox: ox, oy: oy) })

                // ── 경로 · 횟수(카드가 뜨면 사라진다) ──
                Text("\(routeName(state.route)) · ×\(state.count)")
                    .font(.minSans(13, .semibold)).tracking(1)
                    .foregroundStyle(tint.accent)
                    .padding(.top, safe.top + 18)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .opacity(cardsVisible ? 0 : 1)
                    .allowsHitTesting(false)

                // ── 두드리기 안내(받침 바로 아래) ──
                prompt(tint)
                    .padding(.top, oy + 452 * k)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .opacity(waking && !skip ? 1 : 0)
                    .animation(.easeInOut(duration: 0.32), value: waking && !skip)
                    .allowsHitTesting(false)

                // ── 카드 ──
                if cardsShown, let draws = state.draws {
                    cards(draws, tint: tint, safe: safe, origin: origin)
                }

                // ── 뒤집을 때 튀는 반짝이(카드 위) ──
                OrbCanvas(frames: eng.frames) { c, _ in sp.draw(&c, now: eng.t) }
                    .allowsHitTesting(false)

                // ── 건너뛰기 ──
                if !cardsShown && !skip {
                    skipButton
                        .padding(12)
                        .padding(.top, safe.top)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                }
            }
        }
        .ignoresSafeArea()
        .onAppear {
            eng.pxScale = displayScale
            eng.setReducedMotion(reduceMotion)
            eng.onConverge = { Haptics.medium() }
            eng.onShatter = { lv in
                switch lv {
                case 3:
                    Haptics.heavy()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) { Haptics.celebrate() }
                case 2:
                    Haptics.celebrate()
                default:
                    Haptics.medium()
                }
            }
            eng.run(true)
            flipped = Array(repeating: false, count: state.draws?.count ?? 0)
            if let best { eng.setResult(best) }
        }
        .onDisappear {
            eng.run(false)
            eng.onConverge = nil
            eng.onShatter = nil
        }
        .onChange(of: state) { s in
            // 결과가 왔다 — 카드 칸을 만들고, 등급을 수정구에 알린다(세 번 두드린 뒤라면 바로 모인다). 건너뛰었으면 바로 카드로.
            flipped = Array(repeating: false, count: s.draws?.count ?? 0)
            guard let b = s.draws?.max(by: { voyageRank($0.rarity) < voyageRank($1.rarity) })?.rarity else { return }
            eng.setResult(b)
            if skip { eng.skip() }
        }
        .onChange(of: cardsShown) { shown in
            guard shown else { return }
            cardStart = eng.t
            withAnimation(.easeInOut(duration: 0.7)) { cardsVisible = true }
            if skip { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { flipAll() } }
        }
    }

    // MARK: - 그림

    /// 카드가 솟는 0.7초(0 → 1, 부드럽게) — 받침·문장이 지워지고 후광이 카드 뒤로 옮겨 간다.
    private static func cardProgress(_ t: CGFloat, _ start: CGFloat?) -> CGFloat {
        guard let start else { return 0 }
        let x = min(max((t - start) / 0.7, 0), 1)
        return x * x * (3 - 2 * x)
    }

    private func prompt(_ tint: RouteTint) -> some View {
        VStack(spacing: 12) {
            Text(locale.t(engine.taps >= taps3 ? .starlightWaiting : .starlightTapToWake))
                .font(.minSans(15, .medium))
                .foregroundStyle(VoyageColor.ink)
                .multilineTextAlignment(.center)
            HStack(spacing: 8) {
                ForEach(0..<taps3, id: \.self) { i in
                    let on = i < engine.taps
                    Circle()
                        .fill(on ? tint.accent : Color.white.opacity(0.2))
                        .overlay(Circle().strokeBorder(on ? Color.white.opacity(0.6) : Color.white.opacity(0.27), lineWidth: 0.75))
                        .frame(width: 9, height: 9)
                }
            }
        }
        .padding(.horizontal, 24)
    }

    private var skipButton: some View {
        Button {
            skip = true
            Haptics.tick()
            if let b = best {
                engine.setResult(b)
                engine.skip()
            }
        } label: {
            Text(locale.t(.starlightSkip))
                .font(.minSans(13)).foregroundStyle(VoyageColor.sub)
                .padding(.horizontal, 14).padding(.vertical, 7)
                .background(Color.white.opacity(0.12), in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func cards(_ draws: [StarlightStore.Draw], tint: RouteTint, safe: EdgeInsets, origin: CGPoint) -> some View {
        ZStack(alignment: .bottom) {
            CardsGrid(draws: draws, flipped: flipped) { i, center in
                flip(i, at: CGPoint(x: center.x - origin.x, y: center.y - origin.y))
            }
            .padding(.top, safe.top)
            .padding(.bottom, 110 + safe.bottom)
            VStack(spacing: 10) {
                if !allFlipped {
                    Text(locale.t(.starlightTapCard)).font(.minSans(12)).foregroundStyle(VoyageColor.sub)
                }
                PillButton(text: locale.t(allFlipped ? .starlightConfirm : .starlightFlipAll), primary: allFlipped, tint: tint) {
                    if allFlipped { onDone() } else { flipAll() }
                }
            }
            .padding(.bottom, 22 + safe.bottom)
        }
        .opacity(cardsVisible ? 1 : 0)
    }

    // MARK: - 동작

    private func tapOrb(_ p: CGPoint, k: CGFloat, ox: CGFloat, oy: CGFloat) {
        guard k > 0, engine.tap((p.x - ox) / k, (p.y - oy) / k) else { return }
        switch engine.taps {
        case 1: Haptics.soft()
        case 2: Haptics.medium()
        default: Haptics.heavy()
        }
    }

    private func flip(_ i: Int, at center: CGPoint) {
        guard i < flipped.count, !flipped[i], let draws = state.draws else { return }
        withAnimation(.easeInOut(duration: 0.56)) { flipped[i] = true }
        let r = draws[i].rarity
        let eng = engine
        let sp = sparks
        let color = StarlightArt.rarityColor(r)
        switch r {
        case "legendary":
            Haptics.celebrate()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { sp.burst(at: center, color: color, n: 34, power: 340, now: eng.t) }
        case "epic":
            Haptics.medium()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { sp.burst(at: center, color: color, n: 20, power: 250, now: eng.t) }
        default:
            Haptics.tick()
        }
    }

    private func flipAll() {
        for (k, i) in flipped.indices.filter({ !flipped[$0] }).enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.14 * Double(k)) {
                guard i < flipped.count, !flipped[i] else { return }
                withAnimation(.easeInOut(duration: 0.56)) { flipped[i] = true }
                Haptics.tick()
            }
        }
    }
}

// MARK: - 카드 반짝이

/// 카드를 뒤집을 때 튀는 반짝이 — 위치는 (태어난 시각, 지금)만의 함수, 프레임마다 다시 그린다. 시각은 수정구 시계(초).
private final class SparkField {
    private struct Spark {
        let x: CGFloat, y: CGFloat, vx: CGFloat, vy: CGFloat
        let size: CGFloat
        let color: Color
        let born: CGFloat, life: CGFloat, spin: CGFloat
    }

    private var list: [Spark] = []

    /// at 에서 n 개를 power(pt/초)로 흩뿌린다.
    func burst(at p: CGPoint, color: Color, n: Int, power: CGFloat, now: CGFloat) {
        for _ in 0..<n {
            let a = CGFloat.random(in: 0..<(2 * .pi))
            let v = CGFloat.random(in: 0.3...1) * power
            list.append(Spark(
                x: p.x, y: p.y, vx: cos(a) * v, vy: sin(a) * v - power * 0.35,
                size: .random(in: 2.5...7), color: Double.random(in: 0..<1) < 0.3 ? .white : color,
                born: now, life: .random(in: 0.8...1.4), spin: .random(in: -270...270)))
        }
    }

    func draw(_ c: inout GraphicsContext, now: CGFloat) {
        guard !list.isEmpty else { return }
        let g: CGFloat = 520
        list.removeAll { now - $0.born > $0.life }
        for s in list {
            let age = max(0, now - s.born)
            let a = min(max(1 - age / s.life, 0), 1)
            let p = CGPoint(x: s.x + s.vx * age, y: s.y + s.vy * age + 0.5 * g * age * age)
            let r = s.size * (0.6 + 0.4 * a)
            c.fill(StarlightArt.circle(p, r * 2.2), with: .color(s.color.opacity(Double(0.22 * a))))
            var l = c
            l.translateBy(x: p.x, y: p.y)
            l.rotate(by: .degrees(Double(s.spin * age)))
            l.fill(StarlightArt.sparkle(.zero, r * 1.6), with: .color(s.color.opacity(Double(a))))
        }
    }
}

private struct PillButton: View {
    let text: String
    let primary: Bool
    let tint: RouteTint
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.minSans(15, .semibold))
                .foregroundStyle(primary ? tint.ink : VoyageColor.ink)
                .padding(.horizontal, 28).padding(.vertical, 12)
                .background(primary
                            ? LinearGradient(colors: [tint.light, tint.deep], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [tint.accent.opacity(0.2), tint.accent.opacity(0.12)], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: Capsule())
                .overlay(Capsule().strokeBorder(primary ? Color.clear : tint.accent.opacity(0.6), lineWidth: 0.75))
                .contentShape(Capsule())
        }
        .buttonStyle(VoyagePressStyle())
    }
}

// MARK: - 카드

/// 카드 격자 — 1장은 크게, 2~4장은 두 줄, 5장부터 세 줄. 카드가 아래에서 솟아오른다.
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

    /// 카드 뒷면 무늬 — 가운데에서 퍼지는 **대칭** 크리스탈 면(장미창). 빛은 왼쪽 위에서. Android `drawRoseFacets` 와 같은 식.
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

/// 카드 앞면. 중복(이미 가진 것)이면 뒤집힌 뒤 **바뀌는 과정**을 보여 준다:
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

    private static let space = "voyageCardFront"

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
                    .font(.minSans(big ? 17 : 11.5, .semibold)).foregroundStyle(VoyageColor.ink.opacity(1 - 0.25 * fade))
                    .multilineTextAlignment(.center).lineLimit(2)
                    .frame(maxWidth: .infinity)
                if big, let item {
                    Text(draw.dup ? locale.t(.starlightDupExplain) : item.desc(in: lang)).font(.minSans(12)).foregroundStyle(VoyageColor.sub)
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
                        .foregroundStyle(VoyageColor.dust)
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
                .foregroundStyle(VoyageColor.dust)
                .lineLimit(1)
        }
        .padding(.leading, big ? 8 : 5).padding(.trailing, big ? 12 : 8).padding(.vertical, 3)
        .background(LinearGradient(colors: [Color(hex: 0xFFC75A).opacity(0.25), Color(hex: 0xFFE6A8).opacity(0.15)],
                                   startPoint: .leading, endPoint: .trailing), in: Capsule())
        .overlay(Capsule().strokeBorder(VoyageColor.dust.opacity(0.35 + 0.45 * pop), lineWidth: 1))
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
        ctx.fill(StarlightArt.circle(pos, size * 1.8), with: .color(VoyageColor.dust.opacity(0.18 * alpha)))
        ctx.fill(StarlightArt.sparkle(pos, size * 1.7), with: .color(Color(hex: 0xFFF3D0).opacity(alpha)))
    }
}
