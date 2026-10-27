import SwiftUI

/// 별가루 화면들(무대 · 깨우기 연출 · 탐사 수집품)이 같이 쓰는 색 — Android `WorkshopScreen.kt` 의 Ink · Sub · Dim · Line · Dust · Gold.
enum VoyageColor {
    static let ink = Color(hex: 0xF0F0F0)
    static let sub = Color(hex: 0xB8C0CC)
    static let dim = Color(hex: 0x7D8696)
    static let line = Color.white.opacity(0.10)
    static let dust = Color(hex: 0xFFE6A8)
    static let gold = Color(hex: 0xFFD27A)
}

/// 경로 색 — accent 테·빛, light→deep 깨우기 버튼, ink 버튼 글자. 수정구 색(OrbPalette)과 짝. Android `RouteTint` 패리티.
struct RouteTint {
    let accent: Color
    let light: Color
    let deep: Color
    let ink: Color

    static let normal = RouteTint(accent: Color(hex: 0x9FC3FF), light: Color(hex: 0xE6EEFF), deep: Color(hex: 0x9DB9FF), ink: Color(hex: 0x0B1430))
    static let rare = RouteTint(accent: Color(hex: 0xFFD27A), light: Color(hex: 0xFFF0C2), deep: Color(hex: 0xFFC15E), ink: Color(hex: 0x2A1600))

    static func of(_ route: String) -> RouteTint { route == "rare" ? rare : normal }
}

@MainActor
func routeName(_ route: String) -> String {
    LocaleManager.shared.t(route == "rare" ? .voyageRouteRare : .voyageRouteNormal)
}

/// 확률 표기 — 딱 떨어지면 소수 없이(66%), 1% 이상은 한 자리, 그 밑은 두 자리. Android `pct` 와 같은 규칙.
func voyagePct(_ v: Double) -> String {
    if abs(v - v.rounded()) < 1e-9 { return String(format: "%.0f%%", v) }
    if v >= 1 { return String(format: "%.1f%%", v) }
    return String(format: "%.2f%%", v)
}

/// 천 단위 쉼표(별가루는 금방 네 자리가 된다).
func voyageFmt(_ n: Int) -> String {
    let digits = String(abs(n))
    var out = ""
    for (i, ch) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out.append(",") }
        out.append(ch)
    }
    return n < 0 ? "-" + out : out
}

/// 누르면 살짝 작아졌다 돌아오는 버튼(Android `clickBounce` 대응).
struct VoyagePressStyle: ButtonStyle {
    var scale: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.28, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

/// 무대 배율 기준 폭 — 390pt 화면에서 배율 1.05(시안).
private let refW: CGFloat = 371.43
/// 상단바 아래 → 무대 원점(지갑 두 줄 + 제목 자리).
private let aboveStage: CGFloat = 104
/// 무대(446 단위)를 뺀 세로 몫 — 위 104 + 안내 → 깨우기 34 + 버튼 56 + 아래 여유 20.
private let chromeH: CGFloat = 214
/// 무대 단위로 문지르기 안내가 놓이는 높이(마법진 앞 가장자리 아래).
private let hintY: CGFloat = 446
/// 이만큼(무대 단위) 움직이면 두드림이 아니라 문지르기.
private let rubSlop: CGFloat = 14
private let voyageCounts = [1, 10]
private let buttonInk = Color(hex: 0x2A1A00)
/// 무대 잔별 — 깨우기 창도 같은 자리(같은 시드)라 창이 떠도 하늘이 그대로 이어진다.
private let stageSky = SkyStars(seed: 11)

/// CSS ease-in-out 근사(부드러운 계단).
private func ease(_ x: CGFloat) -> CGFloat {
    let t = min(max(x, 0), 1)
    return t * t * (3 - 2 * t)
}

/// CSS linear-gradient(각도) 의 시작·끝점 — deg 0 = 위로 · 90 = 오른쪽으로, 상자 r 에 맞춘다.
private func cssPoints(_ r: CGRect, _ deg: CGFloat) -> (CGPoint, CGPoint) {
    let a = deg * .pi / 180
    let dx = sin(a)
    let dy = -cos(a)
    let half = (abs(r.width * dx) + abs(r.height * dy)) / 2
    return (CGPoint(x: r.midX - dx * half, y: r.midY - dy * half), CGPoint(x: r.midX + dx * half, y: r.midY + dy * half))
}

/// 네 꼭지 반짝이 — 변이 가운데에서 k 만큼 비켜 휜다(0 = 가운데를 지나는 가는 별).
private func sparklePath(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ k: CGFloat) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: cx, y: cy - r))
    p.addQuadCurve(to: CGPoint(x: cx + r, y: cy), control: CGPoint(x: cx + r * k, y: cy - r * k))
    p.addQuadCurve(to: CGPoint(x: cx, y: cy + r), control: CGPoint(x: cx + r * k, y: cy + r * k))
    p.addQuadCurve(to: CGPoint(x: cx - r, y: cy), control: CGPoint(x: cx - r * k, y: cy + r * k))
    p.addQuadCurve(to: CGPoint(x: cx, y: cy - r), control: CGPoint(x: cx - r * k, y: cy - r * k))
    p.closeSubpath()
    return p
}

/// 깊은 우주 바탕 — 남색 그라데이션 + 수정구 뒤 경로 빛(문지르는 동안 · 바뀔 때 수정구 색을 그대로 따라간다) + 보랏빛·푸른 성운 두 덩이
/// + 깜빡이는 잔별 + 8초마다 지나가는 별똥별. deep 1 = 깨우기 창(단색 바탕 + 경로 빛 + 잔별만), 그 사이 값은 둘을 섞는다
/// (창이 무대에서 다가가는 동안). spread = 잔별을 수정구(focusY) 둘레로 벌리는 정도 — 다가갈 때 먼 별이 살짝 밀려난다.
/// 시간은 수정구 시계(`OrbEngine.t`)라 화면이 가려지면 같이 쉰다. Android `drawSpace` 패리티.
func drawSpace(_ c: inout GraphicsContext, _ size: CGSize, orb: OrbEngine, sky: SkyStars, focusY: CGFloat, deep: CGFloat, spread: CGFloat = 1) {
    let t = orb.t
    let w = size.width
    let h = size.height
    let near = 1 - deep
    let full = Path(CGRect(origin: .zero, size: size))
    c.blendMode = .normal
    if near > 0 {
        let bg: [Gradient.Stop] = [
            .init(color: Color(hex: 0x03050D), location: 0),
            .init(color: Color(hex: 0x070B1E), location: 0.33),
            .init(color: Color(hex: 0x0A0B1C), location: 0.66),
            .init(color: Color(hex: 0x04050C), location: 1),
        ]
        c.fill(full, with: .linearGradient(Gradient(stops: bg), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
    }
    if deep > 0 {
        c.fill(full, with: .color(Color(hex: 0x04060E).opacity(Double(deep))))
    }
    let acc = orb.glowColor
    let accStops: [Gradient.Stop] = [
        .init(color: acc.opacity(0.17), location: 0),
        .init(color: acc.opacity(0.05), location: 0.5),
        .init(color: acc.opacity(0), location: 1),
    ]
    c.fill(full, with: .radialGradient(Gradient(stops: accStops), center: CGPoint(x: w / 2, y: focusY), startRadius: 0, endRadius: w * 1.05))
    if near > 0 {
        let violet = Color(hex: 0x7B5CFF)
        let blue = Color(hex: 0x3E7BFF)
        c.fill(full, with: .radialGradient(Gradient(colors: [violet.opacity(Double(0.1098 * near)), violet.opacity(0)]),
                                           center: CGPoint(x: w * 0.12, y: h * 0.16), startRadius: 0, endRadius: w * 0.85))
        c.fill(full, with: .radialGradient(Gradient(colors: [blue.opacity(Double(0.0784 * near)), blue.opacity(0)]),
                                           center: CGPoint(x: w * 0.92, y: h * 0.8), startRadius: 0, endRadius: w * 0.75))
    }
    let fx = w / 2
    for i in 0..<sky.count {
        let tw = 0.5 + 0.5 * sin(t * sky.w[i] + sky.p[i])
        let p = CGPoint(x: fx + (sky.x[i] * w - fx) * spread, y: focusY + (sky.y[i] * h - focusY) * spread)
        if sky.s[i] > 1.48 {
            c.fill(StarlightArt.sparkle(p, 2.2 + 1.6 * tw), with: .color(.white.opacity(Double(0.35 + 0.5 * tw))))
        } else {
            let a = (0.1 + 0.45 * tw) * min(1, sky.s[i] / 1.6)
            c.fill(StarlightArt.circle(p, sky.s[i] * 0.75), with: .color(.white.opacity(Double(a))))
        }
    }
    guard near > 0 else { return }
    // 별똥별 — 8초마다 0.9초, 매번 다른 자리(위쪽 하늘)
    let idx = floor(t / 8)
    let u = (t - idx * 8) / 0.9
    guard u >= 0 && u <= 1 else { return }
    let sv = sin(idx * 12.9898) * 43758.547
    let seed = sv - floor(sv)
    let sx = w * (0.15 + 0.6 * seed)
    let sy = h * (0.06 + 0.18 * (seed * 7.31).truncatingRemainder(dividingBy: 1))
    let len = w * 0.28
    let dx = len * 0.92
    let dy = len * 0.38
    let head = CGPoint(x: sx + dx * u, y: sy + dy * u)
    let tail = CGPoint(x: head.x - dx * 0.45, y: head.y - dy * 0.45)
    let fade = Double(sin(u * .pi) * near)
    var streak = Path()
    streak.move(to: tail)
    streak.addLine(to: head)
    c.stroke(streak, with: .linearGradient(Gradient(colors: [.white.opacity(0), .white.opacity(0.55 * fade)]), startPoint: tail, endPoint: head),
             style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
}

/// 무대 자리(화면 pt) — 무대(시안 340 × 446 단위)는 화면이 허락하는 만큼 크게(폭은 시안 비율, 높이는 깨우기 버튼까지 한 화면에).
/// 남는 높이는 위 4 : 아래 6. origin = 무대 캔버스의 전역 위치(깨우기 창이 수정구 자리를 이어받을 때).
private struct StageLayout {
    let w: CGFloat
    let h: CGFloat
    let origin: CGPoint
    let top: CGFloat
    let k: CGFloat
    let stageTop: CGFloat
    let stageLeft: CGFloat
    let hintTop: CGFloat

    init(_ geo: GeometryProxy) {
        w = geo.size.width
        h = geo.size.height
        origin = geo.frame(in: .global).origin
        top = geo.safeAreaInsets.top
        let avail = h - top - geo.safeAreaInsets.bottom
        let fit = min(w / refW, (avail - chromeH) / hintY)
        k = min(max(fit, 0.6), 1.25)
        let spare = max(0, avail - chromeH - hintY * k)
        stageTop = top + aboveStage + spare * 0.4
        stageLeft = (w - orbW * k) / 2
        hintTop = stageTop + hintY * k
    }
}

/// 별가루 — 잠든 우주(2026-10-27 개편, 시안 그대로). Android `WorkshopScreen.kt` 패리티.
///  - 왼쪽 위: 별가루 · 열쇠 두 줄(▶ = 광고 보고 별가루 받기 창 · + = 열쇠 구매 창), 오른쪽 위: 확률.
///  - 가운데: 별로 이룬 마법진 위로 피어오르는 은하수와 둥둥 뜬 수정구(`OrbEngine`). 제목은 별자리 각인(A안) + 지금 경로(옅은 잠 · 깊은 잠).
///    수정구 왼쪽·오른쪽을 누르면 그쪽으로 돌고, 1~2초 문지르면 경로가 바뀐다(다시 문지르면 돌아온다).
///  - 아래: "수정구는 문지르면 가치가 변합니다" + 깨우기(오른쪽 위 작은 ×1 · ×10).
/// 서버가 모든 계산을 한다(`StarlightStore`) — 이 화면은 요청을 보내고 결과를 연출만 한다(`VoyageOpening`).
struct WorkshopScreen: View {
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var quests = QuestStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @StateObject private var orb = OrbEngine(seed: UInt64.random(in: 1...UInt64.max), interactive: false, rare: false)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var route = "normal"
    /// 경로 색이 깊은 잠 쪽으로 섞인 정도(0..1) — 경로를 바꾸면 0.6초 동안 넘어간다(제목 · 안내 · 깨우기 · 횟수 색).
    @State private var routeMix: CGFloat = 0
    @State private var count = 1
    @State private var busy = false
    @State private var adBusy = false
    @State private var opening: OpeningState?
    /// 깨우기 창이 떠 있는 동안 붙잡아 둔 무대 자리 — 상단바가 숨으며 안전 영역이 바뀌어도 아래 무대가 움직이지 않게.
    @State private var frozen: StageLayout?
    /// 깨우기 창이 화면을 다 덮었다 — 이때만 무대 수정구가 쉰다(스며들고 걷히는 동안엔 같이 돈다).
    @State private var covered = false
    @State private var adSheet = false
    @State private var keySheet = false
    @State private var oddsSheet = false
    @State private var converted = false

    var body: some View {
        let cfg = store.config
        let counts = voyageCounts.filter { $0 <= cfg.maxVoyage }
        let options = counts.isEmpty ? [1] : counts
        let n = options.contains(count) ? count : options[0]
        let cost = cfg.route(route).cost * n
        let loaded = store.walletLoaded
        let short = loaded ? max(0, cost - store.stardust) : 0
        ZStack {
            GeometryReader { geo in
                stage(frozen ?? StageLayout(geo), n: n, options: options, cost: cost, short: short, loaded: loaded)
            }
            .ignoresSafeArea()

            if let st = opening {
                VoyageOpening(state: st, stage: orb) {
                    if st.draws != nil { closeOpening() }
                }
                .transition(.opacity)
                .zIndex(10)
            }
        }
        .navigationTitle(locale.t(.navWorkshop))
        .navigationBarTitleDisplayMode(.inline)
        // 깨우기 연출은 화면 전체 — 상단바를 잠시 숨긴다.
        .toolbar(opening != nil ? .hidden : .visible, for: .navigationBar)
        .staryDialog(isPresented: $adSheet) {
            AdSheet(busy: adBusy) {
                adSheet = false
                // 창이 닫히는 도중에 광고를 띄우면 표시가 씹힌다 — 닫힌 뒤에.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { claimAd() }
            } onClose: {
                adSheet = false
            }
        }
        .staryDialog(isPresented: $keySheet) {
            KeySheet { keySheet = false }
        }
        .staryDialog(isPresented: $oddsSheet) {
            OddsSheet(initialRoute: route) { oddsSheet = false }
        }
        .onAppear {
            orb.setReducedMotion(reduceMotion)
            orb.run(stageRuns)
            routeMix = route == "rare" ? 1 : 0
            StaryAnalytics.log("workshop_open", ["stardust": store.stardust])
        }
        .onDisappear {
            orb.run(false)
            orb.rubEnd()
        }
        // 깨우기 창이 다 덮은 동안엔 무대 수정구를 쉬게 한다(창이 제 수정구를 굴린다).
        .onChange(of: stageRuns) { on in orb.run(on) }
        .onChange(of: route) { r in
            orb.setRoute(rare: r == "rare", animate: true)
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.6)) { routeMix = r == "rare" ? 1 : 0 }
        }
        .onChange(of: reduceMotion) { on in orb.setReducedMotion(on) }
        // 개편 전에 만들어 둔 성운 상자 → 별가루로(상자 값 그대로). 지갑을 받은 뒤 한 번.
        .task(id: loaded) { await convertLeftoverBoxes() }
    }

    /// 무대 수정구 시계 — 깨우기 창이 화면을 다 덮은 동안만 쉰다.
    private var stageRuns: Bool { opening == nil || !covered }

    // MARK: 무대

    private func stage(_ lay: StageLayout, n: Int, options: [Int], cost: Int, short: Int, loaded: Bool) -> some View {
        let eng = orb
        let k = lay.k
        let left = lay.stageLeft
        let top = lay.stageTop
        return ZStack(alignment: .topLeading) {
            OrbCanvas(frames: eng.frames) { c, size in
                drawSpace(&c, size, orb: eng, sky: stageSky, focusY: top + orbCY * k, deep: 0)
                eng.draw(&c, k: k, ox: left, oy: top, viewW: size.width, viewH: size.height)
            }
            .frame(width: lay.w, height: lay.h)
            .allowsHitTesting(false)

            StageTouch(orb: eng, k: k, left: left, top: top) { flipRoute() }

            RouteMixReader(mix: routeMix) { m in
                chrome(m, lay, n: n, options: options, cost: cost, short: short, loaded: loaded)
            }
        }
        .frame(width: lay.w, height: lay.h, alignment: .topLeading)
        .coordinateSpace(name: StageTouch.space)
    }

    /// 무대 위 글자 · 단추 — 경로 색(mix)이 섞이는 동안 프레임마다 다시 그린다.
    private func chrome(_ m: CGFloat, _ lay: StageLayout, n: Int, options: [Int], cost: Int, short: Int, loaded: Bool) -> some View {
        let accent = RouteTint.normal.accent.blended(with: RouteTint.rare.accent, fraction: Double(m))
        return ZStack(alignment: .topLeading) {
            StageTitle(route: route, accent: accent, orb: orb)
                .frame(width: lay.w)
                .padding(.top, lay.stageTop - 2)

            RubHint(accent: accent, orb: orb)
                .frame(width: lay.w)
                .padding(.top, lay.hintTop)

            ZStack(alignment: .topTrailing) {
                WakeButton(mix: m, accent: accent, cost: cost, short: short, loaded: loaded, busy: busy, orb: orb) { sail(n, from: lay) }
                if options.count > 1 {
                    CountChip(counts: options, value: n, accent: accent) { c in
                        guard c != count else { return }
                        Haptics.tick()
                        count = c
                    }
                    .offset(x: 12, y: -14)
                }
            }
            .frame(width: 236, height: 56)
            .frame(width: lay.w)
            .padding(.top, lay.hintTop + 34)

            WalletPanel(loaded: loaded, stardust: store.stardust, keys: quests.keys, adOff: loaded && store.adsLeft <= 0,
                        onAd: { adSheet = true },
                        onKeys: { if loaded { keySheet = true } })
                .padding(.leading, 16)
                .padding(.top, lay.top + 8)

            OddsChip { oddsSheet = true }
                .padding(.trailing, 16)
                .frame(width: lay.w, alignment: .trailing)
                .padding(.top, lay.top + 11)
        }
        .frame(width: lay.w, height: lay.h, alignment: .topLeading)
    }

    /// 문지르기가 다 차서 경로가 바뀌었다 — 시안 진동 [18, 40, 28]: 묵직하게 한 번, 잠깐 쉬고 가볍게 한 번.
    private func flipRoute() {
        route = orb.rare ? "rare" : "normal"
        Haptics.medium()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.058) { Haptics.soft() }
    }

    // MARK: 동작

    private func fail(_ code: String) {
        Haptics.soft()
        GlobalToast.shared.show(locale.t(Self.failKey(code)))
    }

    static func failKey(_ code: String) -> L10n {
        switch code {
        case "insufficient": return .starlightErrInsufficient
        case "empty_pool": return .starlightErrEmptyPool
        case "ad_limit": return .voyageAdLimit
        case "ad_cooldown": return .voyageAdCooldown
        case "timeout": return .starlightErrTimeout
        default: return .starlightErrGeneric
        }
    }

    private func sail(_ k: Int, from lay: StageLayout) {
        guard !busy, opening == nil, store.walletLoaded else { return }
        let r = route
        if store.stardust < store.config.route(r).cost * k {
            fail("insufficient")
            return
        }
        busy = true
        Haptics.medium()
        // 무대 자리를 붙잡아 두고(상단바가 숨어도 아래 무대가 그대로), 창은 그 수정구 자리에서 스며들어 다가간다.
        frozen = lay
        covered = false
        let from = OrbPlace(k: lay.k, x: lay.origin.x + lay.stageLeft, y: lay.origin.y + lay.stageTop)
        // 요청은 창을 띄우자마자 — 두드리는 동안 결과가 온다(늦으면 세 번째 두드림 뒤 수정구가 떨며 기다린다).
        withAnimation(.easeOut(duration: openingSeep)) { opening = OpeningState(count: k, route: r, from: from) }
        // 창이 다 스며든 뒤엔 무대 수정구를 쉬게 한다.
        DispatchQueue.main.asyncAfter(deadline: .now() + (openingSeep + 0.02)) {
            if opening != nil { covered = true }
        }
        Task {
            let res = await store.voyage(r, k)
            busy = false
            switch res {
            case .done(let result):
                let draws = StarlightStore.draws(result)
                guard !draws.isEmpty else {
                    closeOpening()
                    fail("server")
                    return
                }
                let best = draws.max { voyageRank($0.rarity) < voyageRank($1.rarity) }?.rarity ?? ""
                StaryAnalytics.log("voyage", ["route": r, "count": k, "best": best])
                QuestStore.report(.openBox)
                opening?.draws = draws
            case .failed(let code):
                closeOpening()
                fail(code)
            }
        }
    }

    /// 깨우기 창 걷기 — 0.32초 동안 옅어지며 별가루 화면이 드러난다(무대 수정구는 바로 다시 돈다).
    private func closeOpening() {
        covered = false
        withAnimation(.easeOut(duration: 0.32)) { opening = nil }
        // 상단바가 돌아온 뒤에 무대 자리를 다시 잰다(그 사이엔 연출 전 자리 그대로).
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if opening == nil { frozen = nil }
        }
    }

    private func claimAd() {
        guard !adBusy, store.walletLoaded else { return }
        if store.adsLeft <= 0 {
            fail("ad_limit")
            return
        }
        // 기기 시계가 서버와 어긋나도 막히지 않게 — 간격 안쪽일 때만 미리 거른다(나머지는 서버가 센다).
        let cool = Int64(store.config.adCooldownSec) * 1000
        let wait = store.adsLast + cool - Int64(Date().timeIntervalSince1970 * 1000)
        if wait >= 1 && wait <= cool {
            fail("ad_cooldown")
            return
        }
        if AdFreeAccount.shared.active {
            reward()
            return
        }
        let ads = AdsManager.shared
        guard !ads.showing else { return }
        guard ads.isConfigured else {
            GlobalToast.shared.show(locale.t(.detailAdUnavailable))
            return
        }
        ads.showRewardedWhenReady(
            onWaiting: { GlobalToast.shared.show(locale.t(.detailAdLoading)) },
            onUnavailable: { GlobalToast.shared.show(locale.t(.detailAdUnavailable)) },
            onResult: { ok in
                if ok { reward() } else { GlobalToast.shared.show(locale.t(.voyageAdNotFinished)) }
            }
        )
    }

    private func reward() {
        adBusy = true
        Task {
            let r = await store.adReward()
            adBusy = false
            switch r {
            case .done(let result):
                let gained = (result["gained"] as? NSNumber)?.intValue ?? store.config.adStardust
                Haptics.celebrate()
                StaryAnalytics.log("workshop_ad_reward", ["gained": gained, "ad_free": AdFreeAccount.shared.active])
                GlobalToast.shared.show(String(format: locale.t(.starlightStardustGained), gained))
            case .failed(let code):
                fail(code)
            }
        }
    }

    /// 지갑을 받은 뒤 한 번 — 상자 수로 걸면 결과보다 지갑 갱신이 먼저 와서 다시 불릴 수 있다.
    private func convertLeftoverBoxes() async {
        guard store.walletLoaded, store.boxes > 0, !converted else { return }
        converted = true
        let before = store.boxes
        let r = await store.convertBoxes()
        guard case .done(let result) = r else { return }
        let boxes = (result["boxes"] as? NSNumber)?.intValue ?? before
        let gained = (result["gained"] as? NSNumber)?.intValue ?? 0
        if boxes > 0 && gained > 0 {
            Haptics.celebrate()
            GlobalToast.shared.show(String(format: locale.t(.voyageBoxesConverted), boxes, gained))
        }
    }
}

func voyageRank(_ rarity: String) -> Int { StarlightStore.rarities.firstIndex(of: rarity) ?? 0 }

/// 경로 색이 섞인 정도(0 옅은 잠 … 1 깊은 잠)를 애니메이션 프레임마다 내려 준다 — Color 는 그대로 보간되지 않아 숫자로 섞는다.
private struct RouteMixReader<Content: View>: View, Animatable {
    var mix: CGFloat
    @ViewBuilder let content: (CGFloat) -> Content

    var animatableData: CGFloat {
        get { mix }
        set { mix = newValue }
    }

    var body: some View { content(mix) }
}

/// 누르는 동안 살짝 작아진다(시안 :active — 0.12초).
private struct ChipPress: ButtonStyle {
    var scale: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.12), value: configuration.isPressed)
    }
}

// MARK: - 수정구 손가락

/// 한 번의 손가락(눌렀다 뗄 때까지) — 화면을 다시 그리지 않게 참조로 둔다.
private final class StageTouchState {
    var start = CGPoint(x: -1, y: -1)
    /// 손가락이 끝났다(뗐거나 취소) — 다음 움직임은 새 손가락.
    var over = true
    var handled = false
    var ignored = false
    var t0 = Date.distantPast
    var tl = Date.distantPast
    var x0: CGFloat = 0
    var y0: CGFloat = 0
    var lx: CGFloat = 0
    var ly: CGFloat = 0
    var moved: CGFloat = 0
    var rub = false
}

/// 수정구 둘레(반지름 1.35배)에서 시작한 손가락만 받는다: 짧게 누르면 누른 쪽으로 돌고, 끌면 문지르기(다 차면 onFlip).
/// 다른 곳은 비워 둔다(뒤로 가기 밀기 등을 막지 않게). Android `Stage` 의 pointerInput 패리티.
private struct StageTouch: View {
    static let space = "workshopStage"

    let orb: OrbEngine
    let k: CGFloat
    let left: CGFloat
    let top: CGFloat
    let onFlip: () -> Void
    @State private var st = StageTouchState()
    @GestureState private var touching = false

    var body: some View {
        // 구슬은 둥실 떠 흔들리니(±4) 받는 원을 조금 넉넉히 — 실제 판정은 그 순간 구슬 가운데로.
        let d = (orbR * 1.35 * k + 6) * 2
        Color.clear
            .frame(width: d, height: d)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                    .updating($touching) { _, on, _ in on = true }
                    .onChanged { v in changed(v) }
                    .onEnded { v in ended(v) }
            )
            .position(x: left + orbCX * k, y: top + orbCY * k)
            // 시스템이 손가락을 가져가면(취소) onEnded 가 안 온다 — 손을 뗀 것과 같이 정리한다.
            .onChange(of: touching) { on in if !on { finish() } }
            .onDisappear { finish() }
    }

    private func unit(_ p: CGPoint) -> CGPoint {
        CGPoint(x: (p.x - left) / k, y: (p.y - top) / k)
    }

    private func changed(_ v: DragGesture.Value) {
        let s = st
        if s.over || s.start != v.startLocation {
            if s.rub { orb.rubEnd() }
            let p0 = unit(v.startLocation)
            s.start = v.startLocation
            s.over = false
            s.handled = false
            s.t0 = v.time
            s.tl = v.time
            s.x0 = p0.x
            s.y0 = p0.y
            s.lx = p0.x
            s.ly = p0.y
            s.moved = 0
            s.rub = false
            let ddx = p0.x - orb.bx
            let ddy = p0.y - orb.by
            s.ignored = (ddx * ddx + ddy * ddy).squareRoot() >= orbR * 1.35
        }
        guard !s.ignored else { return }
        let p = unit(v.location)
        let mx = p.x - s.lx
        let my = p.y - s.ly
        let dist = (mx * mx + my * my).squareRoot()
        s.moved += dist
        if !s.rub && s.moved > rubSlop {
            s.rub = true
            orb.rubStart()
        }
        if s.rub {
            let before = orb.rubP
            let dt = CGFloat(v.time.timeIntervalSince(s.tl))
            if orb.rubMove(p.x, p.y, dist: dist, dt: dt) {
                onFlip()
            } else if floor(before * 4) != floor(orb.rubP * 4) {
                Haptics.tick()
            }
        }
        s.lx = p.x
        s.ly = p.y
        s.tl = v.time
    }

    private func ended(_ v: DragGesture.Value) {
        let s = st
        if s.start == v.startLocation && !s.handled && !s.ignored && s.moved < rubSlop && v.time.timeIntervalSince(s.t0) < 0.45 {
            orb.tap(s.x0, s.y0)
            Haptics.tick()
        }
        s.handled = true
        finish()
    }

    /// 손가락 정리(여러 번 불려도 된다).
    private func finish() {
        let s = st
        if s.rub {
            orb.rubEnd()
            s.rub = false
        }
        s.over = true
    }
}

// MARK: - 제목(별자리 각인) · 안내

/// 제목 위 작은 별자리 — 가는 선으로 이은 별 여덟(하나는 반짝이)이 3.2초 주기로 따로따로 깜빡인다. 136×18.
/// (x, y, 반지름, 시작 위상(초)) — 반지름 0 = 반짝이(56, 5).
private let titleStars: [(x: CGFloat, y: CGFloat, r: CGFloat, ph: CGFloat)] = [
    (3, 14, 0.9, 0.4), (19, 9, 1.3, 1.9), (37, 12, 0.8, 0.9), (56, 5, 0, 2.4),
    (76, 8, 1.1, 1.2), (96, 3, 1.4, 0.2), (115, 10, 0.85, 2.9), (133, 13, 1.1, 1.5),
]

private func drawTitleConstellation(_ c: inout GraphicsContext, t: CGFloat, accent: Color) {
    var line = Path()
    for (i, s) in titleStars.enumerated() {
        let p = CGPoint(x: s.x, y: s.y)
        if i == 0 {
            line.move(to: p)
        } else {
            line.addLine(to: p)
        }
    }
    c.stroke(line, with: .color(accent.opacity(0.45)), lineWidth: 0.6)
    for s in titleStars {
        let u = (t + s.ph).truncatingRemainder(dividingBy: 3.2) / 3.2
        let a = 0.35 + 0.65 * (0.5 - 0.5 * cos(u * 2 * .pi))
        let col = Color.white.opacity(Double(a))
        if s.r == 0 {
            c.fill(sparklePath(s.x, s.y, 3.8, 0.105), with: .color(col))
        } else {
            c.fill(StarlightArt.circle(CGPoint(x: s.x, y: s.y), s.r), with: .color(col))
        }
    }
}

/// 제목 A — 별자리 · "잠든 우주"(흰 → 경로 빛, 은은한 번짐) · 가는 장식선 · 지금 경로(바뀌면 0.6초 동안 떠오르며 바뀐다).
private struct StageTitle: View {
    let route: String
    let accent: Color
    let orb: OrbEngine
    @ObservedObject private var locale = LocaleManager.shared
    @State private var shown: String
    @State private var swap: CGFloat = 1

    init(route: String, accent: Color, orb: OrbEngine) {
        self.route = route
        self.accent = accent
        self.orb = orb
        _shown = State(initialValue: route)
    }

    var body: some View {
        let acc = accent
        let eng = orb
        let titleFill = LinearGradient(stops: [
            .init(color: .white, location: 0),
            .init(color: .white, location: 0.3),
            .init(color: Color.white.blended(with: acc, fraction: 0.667), location: 1),
        ], startPoint: .top, endPoint: .bottom)
        VStack(spacing: 0) {
            OrbCanvas(frames: eng.frames) { c, _ in drawTitleConstellation(&c, t: eng.t, accent: acc) }
                .frame(width: 136, height: 18)
            Text(locale.t(.voyageTitle))
                .font(.minSans(23, .light))
                .tracking(5.98)
                .lineLimit(1)
                // 자간이 마지막 글자 뒤에도 붙는다 — 앞에도 같은 만큼 띄워 가운데를 맞춘다.
                .padding(.leading, 5.98)
                .frame(height: 28.75)
                .foregroundStyle(titleFill)
                .shadow(color: acc.opacity(0.5), radius: 7)
                .padding(.top, 2)
            HStack(spacing: 8) {
                LinearGradient(colors: [.white.opacity(0), .white.opacity(0.42)], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 46, height: 1)
                Canvas { c, size in
                    c.fill(sparklePath(size.width / 2, size.height / 2, min(size.width, size.height) / 2, 0.08), with: .color(acc))
                }
                .frame(width: 9, height: 9)
                LinearGradient(colors: [.white.opacity(0.42), .white.opacity(0)], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 46, height: 1)
            }
            .padding(.top, 6)
            Text(routeName(shown))
                .font(.minSans(11.5, .medium))
                .tracking(2.76)
                .lineLimit(1)
                .padding(.leading, 2.76)
                .foregroundStyle(acc)
                .opacity(Double(swap))
                .offset(y: (1 - swap) * 4)
                .padding(.top, 6)
        }
        .allowsHitTesting(false)
        .onChange(of: route) { r in
            guard r != shown else { return }
            shown = r
            var snap = Transaction()
            snap.disablesAnimations = true
            withTransaction(snap) { swap = 0 }
            DispatchQueue.main.async {
                withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.6)) { swap = 1 }
            }
        }
    }
}

/// 문지르는 손짓 — 물결 선이 2.6초마다 그려졌다가(앞에서부터) 지워진다. 22×10.
private func drawRubGlyph(_ c: inout GraphicsContext, t: CGFloat, accent: Color) {
    var p = Path()
    p.move(to: CGPoint(x: 1, y: 6))
    p.addCurve(to: CGPoint(x: 9, y: 5), control1: CGPoint(x: 4, y: 1), control2: CGPoint(x: 6, y: 9))
    p.addCurve(to: CGPoint(x: 16, y: 5), control1: CGPoint(x: 12, y: 1), control2: CGPoint(x: 14, y: 1))
    p.addCurve(to: CGPoint(x: 21, y: 4), control1: CGPoint(x: 18, y: 9), control2: CGPoint(x: 20, y: 7))
    let u = t.truncatingRemainder(dividingBy: 2.6) / 2.6
    let off: CGFloat
    if u < 0.45 {
        off = 26 * (1 - ease(u / 0.45))
    } else if u < 0.6 {
        off = 0
    } else {
        off = -26 * ease((u - 0.6) / 0.4)
    }
    let phase = (off.truncatingRemainder(dividingBy: 52) + 52).truncatingRemainder(dividingBy: 52)
    c.stroke(p, with: .color(accent), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, dash: [26, 26], dashPhase: phase))
}

private struct RubHint: View {
    let accent: Color
    let orb: OrbEngine
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let acc = accent
        let eng = orb
        HStack(spacing: 7) {
            OrbCanvas(frames: eng.frames) { c, _ in drawRubGlyph(&c, t: eng.t, accent: acc) }
                .frame(width: 22, height: 10)
            Text(locale.t(.voyageRubHint))
                .font(.minSans(12))
                .tracking(0.24)
                .foregroundStyle(Color(hex: 0xB8C0CC, alpha: 0.659))
                .lineLimit(1)
        }
        .allowsHitTesting(false)
    }
}

// MARK: - 지갑 · 확률

/// 바뀐 숫자를 0.6초 동안 굴려 보여 준다(지갑을 막 받았을 때는 새로 생기니 바로).
private struct RollingNumber: View, Animatable {
    var value: Double
    let color: Color

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text(voyageFmt(Int(value.rounded())))
            .font(.minSans(14.5, .bold).monospacedDigit())
            .foregroundStyle(color)
            .lineLimit(1)
    }
}

/// 왼쪽 위 지갑 — 별가루 · 열쇠 두 줄(폭은 긴 쪽에 맞춘다). 줄 끝 금빛 단추: ▶ 광고 보고 별가루 받기 · + 열쇠 구매.
private struct WalletPanel: View {
    let loaded: Bool
    let stardust: Int
    let keys: Int
    let adOff: Bool
    let onAd: () -> Void
    let onKeys: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WalletRow(label: locale.t(.starlightStardust), value: loaded ? stardust : nil, valueColor: VoyageColor.dust,
                      glyph: .play, off: adOff, buttonLabel: locale.t(.voyageAdTitle), action: onAd) {
                StardustIcon()
            }
            WalletRow(label: locale.t(.starlightKeys), value: keys, valueColor: VoyageColor.ink,
                      glyph: .plus, off: false, buttonLabel: locale.t(.voyageKeyTitle), action: onKeys) {
                CrystalKeyIcon()
            }
        }
        .fixedSize(horizontal: true, vertical: false)
    }
}

private enum WalletGlyph {
    case play, plus
}

private struct WalletRow<Icon: View>: View {
    let label: String
    /// nil = 아직 지갑을 못 받았다("…").
    let value: Int?
    let valueColor: Color
    let glyph: WalletGlyph
    let off: Bool
    let buttonLabel: String
    let action: () -> Void
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        HStack(spacing: 0) {
            icon().frame(width: 18, height: 18)
            Text(label)
                .font(.minSans(12))
                .tracking(0.24)
                .foregroundStyle(VoyageColor.dim)
                .lineLimit(1)
                .padding(.leading, 7)
            Spacer(minLength: 0)
            Group {
                if let value {
                    RollingNumber(value: Double(value), color: valueColor)
                        .animation(.timingCurve(0.33, 1, 0.68, 1, duration: 0.6), value: value)
                } else {
                    Text("…").font(.minSans(14.5, .bold)).foregroundStyle(valueColor)
                }
            }
            .padding(.leading, 21)
            WalletButton(glyph: glyph, off: off, label: buttonLabel, action: action)
                .padding(.leading, 13)
        }
        .padding(.leading, 9)
        .padding(.trailing, 4)
        .frame(height: 34)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [Color.white.opacity(0.102), Color.white.opacity(0.035)], startPoint: .top, endPoint: .bottom),
                    in: Capsule())
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.129), lineWidth: 0.75))
    }
}

/// 지갑 줄 끝 금빛 동그란 단추(26) — off 면 흐린 유리(그래도 눌러서 창은 연다).
private struct WalletButton: View {
    let glyph: WalletGlyph
    let off: Bool
    let label: String
    let action: () -> Void

    var body: some View {
        let ink = off ? VoyageColor.dim : buttonInk
        Button {
            Haptics.tick()
            action()
        } label: {
            ZStack {
                if off {
                    Circle().fill(Color.white.opacity(0.1))
                    Circle().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.75)
                } else {
                    Circle().fill(LinearGradient(stops: [
                        .init(color: Color(hex: 0xFFF0C2), location: 0),
                        .init(color: VoyageColor.gold, location: 0.55),
                        .init(color: Color(hex: 0xFFB259), location: 1),
                    ], startPoint: UnitPoint(x: 0.1006, y: -0.0704), endPoint: UnitPoint(x: 0.8994, y: 1.0704)))
                    // 위쪽 안쪽 빛 테
                    Path { p in
                        p.addArc(center: CGPoint(x: 13, y: 13.5), radius: 12.5, startAngle: .degrees(205), endAngle: .degrees(335), clockwise: false)
                    }
                    .stroke(Color.white.opacity(0.7), lineWidth: 1)
                }
                glyphView(ink)
            }
            .frame(width: 26, height: 26)
            // 금빛 그림자(0 2 10, 35%)
            .background {
                if !off {
                    Circle()
                        .fill(RadialGradient(stops: [
                            .init(color: Color(hex: 0xFFBE5A, alpha: 0.349), location: 0),
                            .init(color: Color(hex: 0xFFBE5A, alpha: 0.349), location: 10.0 / 23.0),
                            .init(color: Color(hex: 0xFFBE5A, alpha: 0), location: 1),
                        ], center: .center, startRadius: 0, endRadius: 23))
                        .frame(width: 46, height: 46)
                        .offset(y: 2)
                        .allowsHitTesting(false)
                }
            }
            .contentShape(Circle())
        }
        .buttonStyle(ChipPress(scale: 0.9))
        .accessibilityLabel(label)
    }

    @ViewBuilder
    private func glyphView(_ ink: Color) -> some View {
        switch glyph {
        case .play:
            Path { p in
                p.move(to: CGPoint(x: 9.5, y: 8.2))
                p.addLine(to: CGPoint(x: 9.5, y: 17.8))
                p.addLine(to: CGPoint(x: 17.5, y: 13))
                p.closeSubpath()
            }
            .fill(ink)
        case .plus:
            Path { p in
                p.move(to: CGPoint(x: 13, y: 8.5))
                p.addLine(to: CGPoint(x: 13, y: 17.5))
                p.move(to: CGPoint(x: 8.5, y: 13))
                p.addLine(to: CGPoint(x: 17.5, y: 13))
            }
            .stroke(ink, style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }
}

/// 오른쪽 위 작은 "확률" 칩.
private struct OddsChip: View {
    let action: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        Button {
            Haptics.tick()
            action()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "info.circle")
                    .font(.system(size: 12.5))
                    .frame(width: 13, height: 13)
                Text(locale.t(.voyageOdds))
                    .font(.minSans(11.5, .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(VoyageColor.sub)
            .padding(.leading, 8)
            .padding(.trailing, 11)
            .frame(height: 28)
            .background(Color.white.opacity(0.0706), in: Capsule())
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.149), lineWidth: 0.75))
            .contentShape(Capsule())
        }
        .buttonStyle(ChipPress(scale: 0.96))
    }
}

// MARK: - 깨우기 · 횟수

/// 깨우기 버튼 바탕 — 경로 빛 그라데이션(경로를 바꾸면 0.6초 동안 넘어간다) + 4.6초마다 비스듬히 스치는 빛 + 위쪽 안쪽 빛 테.
private func drawWakeFill(_ c: inout GraphicsContext, _ size: CGSize, mix: CGFloat, t: CGFloat) {
    let r = CGRect(origin: .zero, size: size)
    let full = Path(r)
    let (s, e) = cssPoints(r, 135)
    c.fill(full, with: .linearGradient(Gradient(colors: [RouteTint.normal.light, RouteTint.normal.deep]), startPoint: s, endPoint: e))
    if mix > 0 {
        var g = c
        g.opacity = Double(min(mix, 1))
        g.fill(full, with: .linearGradient(Gradient(colors: [RouteTint.rare.light, RouteTint.rare.deep]), startPoint: s, endPoint: e))
    }
    // 비스듬히 스치는 빛 — 1.2초 쉬고 4.6초마다(그중 45% 동안 지나간다)
    let cyc = t - 1.2
    if cyc >= 0 {
        let u = cyc.truncatingRemainder(dividingBy: 4.6) / 4.6
        if u < 0.45 {
            let band = CGRect(x: size.width * (-0.7 + 2 * ease(u / 0.45)), y: 0, width: size.width * 0.6, height: size.height)
            let (bs, be) = cssPoints(band, 105)
            let shine: [Gradient.Stop] = [
                .init(color: .white.opacity(0), location: 0),
                .init(color: .white.opacity(0.5), location: 0.5),
                .init(color: .white.opacity(0), location: 1),
            ]
            c.fill(Path(band), with: .linearGradient(Gradient(stops: shine), startPoint: bs, endPoint: be))
        }
    }
    c.stroke(Capsule().path(in: r), with: .linearGradient(Gradient(colors: [.white.opacity(0.7), .white.opacity(0)]),
                                                          startPoint: .zero, endPoint: CGPoint(x: 0, y: 2)), lineWidth: 2)
}

/// 깨우기 — 경로 빛 캡슐에 "깨우기 | ✦ 값". 모자라면 흐린 유리에 얼마 모자란지만.
private struct WakeButton: View {
    let mix: CGFloat
    let accent: Color
    let cost: Int
    let short: Int
    let loaded: Bool
    let busy: Bool
    let orb: OrbEngine
    let action: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let ready = loaded && short <= 0
        let ink = RouteTint.normal.ink.blended(with: RouteTint.rare.ink, fraction: Double(mix))
        let eng = orb
        let m = mix
        Button(action: action) {
            ZStack {
                if ready {
                    OrbCanvas(frames: eng.frames) { c, size in drawWakeFill(&c, size, mix: m, t: eng.t) }
                } else {
                    Capsule().fill(Color.white.opacity(0.078))
                    Capsule().strokeBorder(Color.white.opacity(0.1216), lineWidth: 0.75)
                }
                label(ink)
            }
            .frame(width: 236, height: 56)
            .clipShape(Capsule())
            // 경로 빛 그림자(0 12 30 -8, 60%) — 버튼 아래로 번지는 빛
            .background {
                if ready {
                    Circle()
                        .fill(RadialGradient(stops: [
                            .init(color: accent.opacity(0.5), location: 0),
                            .init(color: accent.opacity(0.3), location: 0.6),
                            .init(color: accent.opacity(0), location: 1),
                        ], center: .center, startRadius: 0, endRadius: 140))
                        .frame(width: 280, height: 280)
                        .scaleEffect(x: 1, y: 100.0 / 280.0)
                        .offset(y: 12)
                        .allowsHitTesting(false)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(ChipPress(scale: ready ? 0.97 : 1))
        .disabled(!ready || busy)
    }

    @ViewBuilder
    private func label(_ ink: Color) -> some View {
        if !loaded {
            Text("…").font(.minSans(16)).foregroundStyle(VoyageColor.sub)
        } else if short > 0 {
            Text(String(format: locale.t(.voyageShort), short))
                .font(.minSans(14, .medium))
                .tracking(0.28)
                .foregroundStyle(VoyageColor.sub)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .padding(.horizontal, 14)
        } else {
            HStack(spacing: 0) {
                Text(locale.t(.voyageLaunch))
                    .font(.minSans(16, .bold))
                    .tracking(1.6)
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Rectangle().fill(ink.opacity(0.25)).frame(width: 1, height: 16).padding(.horizontal, 12)
                StardustIcon(tint: ink).frame(width: 14, height: 14)
                Text(voyageFmt(cost))
                    .font(.minSans(15, .bold).monospacedDigit())
                    .foregroundStyle(ink)
                    .padding(.leading, 4)
            }
            .padding(.horizontal, 14)
        }
    }
}

/// 깨우기 오른쪽 위에 걸친 작은 ×1 · ×10 — 어두운 캡슐 안 고른 칸만 경로 빛.
private struct CountChip: View {
    let counts: [Int]
    let value: Int
    let accent: Color
    let onPick: (Int) -> Void

    var body: some View {
        let outer = RoundedRectangle(cornerRadius: 13, style: .continuous)
        HStack(spacing: 2) {
            ForEach(counts, id: \.self) { c in
                let sel: Double = c == value ? 1 : 0
                let cell = RoundedRectangle(cornerRadius: 11, style: .continuous)
                Button {
                    onPick(c)
                } label: {
                    Text("×\(c)")
                        .font(.minSans(11.5, .semibold).monospacedDigit())
                        .foregroundStyle(VoyageColor.dim.blended(with: .white, fraction: sel))
                        .lineLimit(1)
                        .padding(.horizontal, 6)
                        .frame(minWidth: 34)
                        .frame(height: 22)
                        .background(accent.opacity(0.26 * sel), in: cell)
                        .overlay(cell.strokeBorder(accent.opacity(0.62 * sel), lineWidth: 0.75))
                        .contentShape(cell)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(2)
        .background(Color(hex: 0x090C18, alpha: 0.92), in: outer)
        .overlay(outer.strokeBorder(Color.white.opacity(0.18), lineWidth: 0.75))
        .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
        .animation(.easeOut(duration: 0.2), value: value)
    }
}

// MARK: - 창(광고 · 열쇠)

/// 시안 창 — 가운데 290 폭, 어두운 남색 유리, 통 튀며 나타난다(바탕 어둡게 · 바깥 누르면 닫힘은 `staryDialog`).
private struct PopSheet<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var pop = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        VStack(spacing: 0) {
            content()
        }
        .padding(.top, 24)
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .frame(width: 290)
        .background(LinearGradient(colors: [Color(hex: 0x202640, alpha: 0.94), Color(hex: 0x0E111E, alpha: 0.96)],
                                   startPoint: .top, endPoint: .bottom), in: shape)
        .overlay(shape.strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75))
        .scaleEffect(pop ? 1 : 0.97)
        .onAppear {
            withAnimation(.timingCurve(0.2, 0.9, 0.3, 1.2, duration: 0.28)) { pop = true }
        }
    }
}

/// 창 맨 위 그림 — 동그란 빛(64) 위에 아이콘.
private struct SheetArt<Icon: View>: View {
    let glow: Color
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        ZStack {
            Circle().fill(RadialGradient(colors: [glow, glow.opacity(0)], center: .center, startRadius: 0, endRadius: 31.7))
            icon()
        }
        .frame(width: 64, height: 64)
    }
}

/// 금빛 큰 단추(48) — 쓸 수 없으면 흐린 유리 + 흐린 글자.
private struct GoldButton<Label: View>: View {
    let enabled: Bool
    let action: () -> Void
    @ViewBuilder let label: (Color) -> Label

    var body: some View {
        let ink = enabled ? buttonInk : VoyageColor.sub
        Button(action: action) {
            HStack(spacing: 0) {
                label(ink)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background {
                if enabled {
                    Canvas { c, size in
                        let r = CGRect(origin: .zero, size: size)
                        let (s, e) = cssPoints(r, 135)
                        c.fill(Path(r), with: .linearGradient(Gradient(colors: [Color(hex: 0xFFF0C2), Color(hex: 0xFFC75A)]),
                                                              startPoint: s, endPoint: e))
                    }
                } else {
                    Color.white.opacity(0.1)
                }
            }
            .clipShape(Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(ChipPress(scale: enabled ? 0.97 : 1))
        .disabled(!enabled)
    }
}

private struct GhostButton: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.minSans(15, .medium))
                .foregroundStyle(VoyageColor.sub)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .contentShape(Capsule())
        }
        .buttonStyle(ChipPress(scale: 0.97))
        .padding(.top, 6)
    }
}

/// 광고 보고 별가루 받기 — 받을 양 · 오늘 남은 횟수. 광고 제거 계정은 광고 없이 바로 받는다.
private struct AdSheet: View {
    let busy: Bool
    let onWatch: () -> Void
    let onClose: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var adFree = AdFreeAccount.shared
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let loaded = store.walletLoaded
        let left = store.adsLeft
        let free = adFree.active
        let on = loaded && left > 0 && !busy
        let note = !loaded ? "…" : left > 0 ? String(format: locale.t(.voyageAdTodayLeft), left) : locale.t(.voyageAdLimit)
        PopSheet {
            SheetArt(glow: Color(hex: 0xFFD68C, alpha: 0.278)) {
                StardustIcon().frame(width: 38, height: 38)
            }
            Text(locale.t(free ? .voyageAdTitleFree : .voyageAdTitle))
                .font(.minSans(16, .medium))
                .foregroundStyle(VoyageColor.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Text("+\(store.config.adStardust)")
                .font(.minSans(30, .bold).monospacedDigit())
                .foregroundStyle(VoyageColor.dust)
                .padding(.top, 16)
            Text(note)
                .font(.minSans(12))
                .foregroundStyle(VoyageColor.sub)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
            GoldButton(enabled: on, action: onWatch) { ink in
                if busy {
                    Text("…").font(.minSans(15, .bold)).foregroundStyle(ink)
                } else {
                    if !free {
                        Path { p in
                            p.move(to: CGPoint(x: 2, y: 1.2))
                            p.addLine(to: CGPoint(x: 2, y: 10.8))
                            p.addLine(to: CGPoint(x: 10, y: 6))
                            p.closeSubpath()
                        }
                        .fill(ink)
                        .frame(width: 11, height: 12)
                        .padding(.trailing, 8)
                    }
                    Text(locale.t(free ? .voyageAdClaim : .voyageAdWatch))
                        .font(.minSans(15, on ? .bold : .medium))
                        .foregroundStyle(ink)
                }
            }
            .padding(.top, 16)
            GhostButton(text: locale.t(.questClose), action: onClose)
        }
    }
}

/// 열쇠 구매 — 개수를 고르고 별가루로 산다(모자라면 얼마 모자란지). 이 창은 화면 전체를 덮어 아래 토스트가 안 보인다 — 실패는 창 안에.
private struct KeySheet: View {
    let onClose: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var n = 1
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        let cfg = store.config
        let each = max(1, cfg.keyCost)
        let cap = max(1, cfg.maxExchange)
        let total = each * n
        let short = max(0, total - store.stardust)
        PopSheet {
            SheetArt(glow: Color(hex: 0xA8B8FF, alpha: 0.278)) {
                CrystalKeyIcon().frame(width: 36, height: 36)
            }
            Text(locale.t(.voyageKeyTitle))
                .font(.minSans(16, .medium))
                .foregroundStyle(VoyageColor.ink)
                .padding(.top, 10)
            Text(String(format: locale.t(.voyageKeyEach), each))
                .font(.minSans(12))
                .foregroundStyle(VoyageColor.dim)
                .padding(.top, 2)
            HStack(spacing: 0) {
                StepButton(systemImage: "minus", enabled: n > 1) { n -= 1 }
                Text("\(n)")
                    .font(.minSans(30, .medium).monospacedDigit())
                    .foregroundStyle(VoyageColor.ink)
                    .frame(minWidth: 76)
                StepButton(systemImage: "plus", enabled: n < cap) { n += 1 }
            }
            .padding(.top, 16)
            GoldButton(enabled: short <= 0 && !busy, action: { buy(n) }) { ink in
                if short > 0 {
                    Text(String(format: locale.t(.voyageShort), short))
                        .font(.minSans(14, .medium))
                        .foregroundStyle(ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                } else if busy {
                    Text("…").font(.minSans(15, .bold)).foregroundStyle(ink)
                } else {
                    Text(locale.t(.voyageBuy)).font(.minSans(15, .bold)).foregroundStyle(ink)
                    StardustIcon(tint: ink).frame(width: 13, height: 13).padding(.horizontal, 8)
                    Text(voyageFmt(total)).font(.minSans(15, .bold).monospacedDigit()).foregroundStyle(ink)
                }
            }
            .padding(.top, 18)
            if let error {
                Text(error)
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0xFFB3B3))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
            }
            GhostButton(text: locale.t(.questClose), action: onClose)
        }
        .onChange(of: cap) { m in if n > m { n = m } }
    }

    private func buy(_ k: Int) {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let r = await store.buyKeys(k)
            busy = false
            switch r {
            case .done:
                Haptics.celebrate()
                StaryAnalytics.log("workshop_buy_keys", ["count": k])
                GlobalToast.shared.show(String(format: locale.t(.starlightMadeKeys), k))
                onClose()
            case .failed(let code):
                Haptics.soft()
                error = locale.t(WorkshopScreen.failKey(code))
            }
        }
    }
}

private struct StepButton: View {
    let systemImage: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tick()
            action()
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(VoyageColor.ink)
                .frame(width: 42, height: 42)
                .background(Color.white.opacity(0.1216), in: Circle())
                .overlay(Circle().strokeBorder(Color.white.opacity(0.2), lineWidth: 0.75))
                .contentShape(Circle())
        }
        .buttonStyle(ChipPress(scale: 0.9))
        .opacity(enabled ? 1 : 0.35)
        .disabled(!enabled)
    }
}

// MARK: - 확률(작은 칩으로만 연다)

/// 확률 안내 — 경로마다 등급 확률 · 그 등급에 든 아이템 수(n종) · 하나당 확률, 그리고 규칙(같은 등급은 같은 확률 · 매번 독립 · 중복 환급).
/// 확률형 아이템 표시 기준에 맞춰 **모든 확률을 숫자로** 보여 준다(아이템 이름은 숨긴다 — 무엇이 나올지는 깨워 봐야 안다).
private struct OddsSheet: View {
    let initialRoute: String
    let onClose: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var picked: String?

    var body: some View {
        let route = picked ?? initialRoute
        VStack(alignment: .leading, spacing: 0) {
            Text(locale.t(.starlightOddsTitle)).font(.minSans(16, .semibold)).foregroundStyle(VoyageColor.ink)
            HStack(spacing: 8) {
                ForEach(StarlightStore.routes, id: \.self) { id in
                    StaryChip(routeName(id), selected: route == id, accent: RouteTint.of(id).accent) {
                        guard route != id else { return }
                        Haptics.tick()
                        picked = id
                    }
                }
            }
            .padding(.top, 12)
            ViewThatFits(in: .vertical) {
                rows(route)
                ScrollView(showsIndicators: false) { rows(route) }
            }
            .padding(.top, 10)
            StaryButton(locale.t(.starlightConfirm), kind: .ghost, compact: true, fullWidth: true, action: onClose)
                .padding(.top, 16)
        }
        .padding(20)
        .frame(maxWidth: StaryDialogStyle.maxWidth)
        .glassPanel(cornerRadius: StaryDialogStyle.corner, tint: StaryDialogStyle.glassTint)
        .padding(.horizontal, StaryDialogStyle.screenPadding)
        .padding(.vertical, 24)
    }

    private func rows(_ route: String) -> some View {
        let cfg = store.config
        let rt = cfg.route(route)
        let notes = [
            locale.t(.voyageOddsEqual),
            locale.t(.voyageOddsIndependent),
            String(format: locale.t(.starlightDupRule),
                   cfg.dupRefund["common"] ?? 0, cfg.dupRefund["rare"] ?? 0, cfg.dupRefund["epic"] ?? 0, cfg.dupRefund["legendary"] ?? 0),
        ]
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(StarlightStore.rarities.reversed(), id: \.self) { rar in
                row(rar, rt)
            }
            VStack(alignment: .leading, spacing: 4) {
                ForEach(notes, id: \.self) { note in
                    Text("· " + note)
                        .font(.minSans(11)).foregroundStyle(VoyageColor.dim)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 8)
        }
    }

    private func row(_ rar: String, _ rt: StarlightStore.Route) -> some View {
        let p = rt.percent(rar)
        let kinds = store.poolCount(rar)
        let c = StarlightArt.rarityColor(rar)
        return VStack(spacing: 6) {
            HStack(spacing: 10) {
                Circle().fill(c).frame(width: 8, height: 8)
                VStack(alignment: .leading, spacing: 1) {
                    Text(rarityName(rar)).font(.minSans(13, .medium)).foregroundStyle(VoyageColor.ink)
                    if kinds > 0 {
                        Text(String(format: locale.t(.voyageOddsKinds), kinds) + " · "
                             + String(format: locale.t(.voyageOddsEach), voyagePct(p / Double(kinds))))
                            .font(.minSans(11)).foregroundStyle(VoyageColor.dim)
                    }
                }
                Spacer(minLength: 8)
                Text(voyagePct(p))
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(c)
            }
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.08))
                    Capsule()
                        .fill(LinearGradient(colors: [c.opacity(0.5), c], startPoint: .leading, endPoint: .trailing))
                        .frame(width: g.size.width * CGFloat(min(max(p / 100, 0), 1)))
                }
            }
            .frame(height: 3)
        }
        .padding(.vertical, 7)
    }
}

// MARK: - 공용

@MainActor
func rarityName(_ r: String) -> String {
    let l = LocaleManager.shared
    switch r {
    case "rare": return l.t(.starlightRarityRare)
    case "epic": return l.t(.starlightRarityEpic)
    case "legendary": return l.t(.starlightRarityLegendary)
    default: return l.t(.starlightRarityCommon)
    }
}

@MainActor
func starlightKindName(_ k: String) -> String {
    let l = LocaleManager.shared
    switch k {
    case "shape": return l.t(.starlightKindShape)
    case "color": return l.t(.starlightKindColor)
    case "title": return l.t(.starlightKindTitle)
    case "frame": return l.t(.starlightKindFrame)
    case "badge": return l.t(.starlightKindBadge)
    case "aura": return l.t(.starlightKindAura)
    case "telescope": return l.t(.starlightKindTelescope)
    case "keyBundle": return l.t(.starlightKindKeyBundle)
    default: return l.t(.starlightKindOther)
    }
}
