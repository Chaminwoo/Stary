import SwiftUI

/// 별가루 화면들(무대 · 항해 연출 · 탐사 수집품)이 같이 쓰는 색 — Android `WorkshopScreen.kt` 의 Ink · Sub · Dim · Line · Dust · Gold.
enum VoyageColor {
    static let ink = Color(hex: 0xF0F0F0)
    static let sub = Color(hex: 0xB8C0CC)
    static let dim = Color(hex: 0x7D8696)
    static let line = Color.white.opacity(0.10)
    static let dust = Color(hex: 0xFFE6A8)
    static let gold = Color(hex: 0xFFD27A)
}

/// 항해 경로 색 — accent 테·빛, light→deep 출항 버튼, ink 버튼 글자. 수정구 색(OrbPalette)과 짝. Android `RouteTint` 패리티.
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

/// 무대 수정구의 화면 위치(전역 좌표) — 바탕 성운을 그 뒤에 맞춘다. 스크롤마다 바뀌어도 화면 body 를 다시 계산하지 않게 참조로 둔다.
private final class StageAnchor {
    var y: CGFloat = -1
}

private let backdropSky = SkyStars(seed: 11, n: 90)

/// 별가루(2026-10-26 개편, 옛 "별빛 공방"). Android `WorkshopScreen.kt` 패리티.
///  - 맨 위: 별가루 · 열쇠(옆 + 로 열쇠 구매 창) / 그 아래 광고 보고 별가루 받기(하루 한도·간격은 서버).
///  - 가운데: **테두리 없이 화면을 넓게 쓰는 우주 항해 무대** — 천문 원반 위 수정구(C안, `OrbEngine`). 경로를 바꾸면 유리·금속 색이 섞여 바뀐다.
///  - 아래: 경로(일반 · 희귀 탐사) · 횟수(×1 · ×5 · ×10) · 출항. 확률은 작은 버튼으로만 연다. 찾은 것은 업적 › 특별 › 탐사.
/// 서버가 모든 계산을 한다(`StarlightStore`) — 이 화면은 요청을 보내고 결과를 연출만 한다(`VoyageOpening`).
struct WorkshopScreen: View {
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var quests = QuestStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @StateObject private var orb = OrbEngine(seed: UInt64.random(in: 1...UInt64.max), interactive: false, rare: false)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var route = "normal"
    @State private var count = 1
    @State private var busy = false
    @State private var adBusy = false
    @State private var opening: OpeningState?
    @State private var keySheet = false
    @State private var oddsSheet = false
    @State private var converted = false
    @State private var anchor = StageAnchor()

    /// 무대(수정구 시안 단위) 중 화면에 쓰는 높이 — 원반 그림자까지.
    private static let stageH: CGFloat = 392
    /// 무대를 뺀 나머지(지갑 · 광고 · 경로 · 출항 · 안내 + 사이)의 대략 높이 — 무대 크기를 정할 때.
    private static let chrome: CGFloat = 300

    var body: some View {
        let cfg = store.config
        let tint = RouteTint.of(route)
        let counts = [1, 5, 10].filter { $0 <= cfg.maxVoyage }
        let options = counts.isEmpty ? [1] : counts
        let n = options.contains(count) ? count : options[0]
        let cost = cfg.route(route).cost * n
        let loaded = store.walletLoaded
        let short = loaded ? max(0, cost - store.stardust) : 0
        ZStack {
            SpaceBackdrop(orb: orb, anchor: anchor)
                .ignoresSafeArea()
            GeometryReader { geo in
                let room = geo.size.height - Self.chrome
                // 화면이 허락하는 만큼 크게(폭은 받침 원반 + 여유, 높이는 아래 조작부가 한 화면에 들어오게).
                let k = min(max(min(room / Self.stageH, geo.size.width / 318), 0.72), 1.18)
                let spare = max(0, room - Self.stageH * k)
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        walletRow(loaded)
                            .padding(.top, 6)
                        AdStrip(busy: adBusy, action: claimAd)
                            .padding(.top, 10)
                        Spacer().frame(height: spare * 0.35)
                        VoyageStage(orb: orb, k: k, stageH: Self.stageH, accent: tint.accent) {
                            Haptics.tick()
                            oddsSheet = true
                        }
                        .background(GeometryReader { g in
                            let f = g.frame(in: .global)
                            Color.clear
                                .onAppear { anchor.y = f.minY + f.height * (orbCY / Self.stageH) }
                                .onChange(of: f) { nf in anchor.y = nf.minY + nf.height * (orbCY / Self.stageH) }
                        })
                        Spacer().frame(height: spare * 0.65)
                        HStack(spacing: 10) {
                            ForEach(StarlightStore.routes, id: \.self) { id in
                                let rt = cfg.route(id)
                                RouteTicket(route: id, selected: route == id, cost: rt.cost,
                                            epicPlus: rt.percent("epic") + rt.percent("legendary")) {
                                    guard route != id else { return }
                                    Haptics.tick()
                                    route = id
                                }
                            }
                        }
                        .padding(.horizontal, 18)
                        HStack(spacing: 10) {
                            if options.count > 1 {
                                CountPicker(counts: options, value: n, accent: tint.accent) { c in
                                    guard c != count else { return }
                                    Haptics.tick()
                                    count = c
                                }
                            }
                            SailButton(orb: orb, cost: cost, short: short, loaded: loaded, busy: busy, tint: tint) { sail(n) }
                        }
                        .frame(height: 56)
                        .padding(.horizontal, 18)
                        .padding(.top, 12)
                        findsLink.padding(.top, 10)
                    }
                    .padding(.bottom, 12)
                    .frame(width: geo.size.width)
                }
            }

            if let st = opening {
                VoyageOpening(state: st) {
                    if st.draws != nil { withAnimation(.easeOut(duration: 0.2)) { opening = nil } }
                }
                .transition(.opacity)
                .zIndex(10)
            }
        }
        .navigationTitle(locale.t(.navWorkshop))
        .navigationBarTitleDisplayMode(.inline)
        // 항해 연출은 화면 전체 — 상단바를 잠시 숨긴다.
        .toolbar(opening != nil ? .hidden : .visible, for: .navigationBar)
        .staryDialog(isPresented: $keySheet) {
            KeySheet { keySheet = false }
        }
        .staryDialog(isPresented: $oddsSheet) {
            OddsSheet(initialRoute: route) { oddsSheet = false }
        }
        .onAppear {
            orb.setReducedMotion(reduceMotion)
            orb.run(opening == nil)
            StaryAnalytics.log("workshop_open", ["stardust": store.stardust])
        }
        .onDisappear { orb.run(false) }
        // 항해 연출 창이 덮는 동안엔 무대 수정구를 쉬게 한다(연출 창이 제 수정구를 굴린다).
        .onChange(of: opening == nil) { idle in orb.run(idle) }
        .onChange(of: route) { r in orb.setRoute(rare: r == "rare", animate: true) }
        .onChange(of: reduceMotion) { on in orb.setReducedMotion(on) }
        // 개편 전에 만들어 둔 성운 상자 → 별가루로(상자 값 그대로). 지갑을 받은 뒤 한 번.
        .task(id: loaded) { await convertLeftoverBoxes() }
    }

    // MARK: 지갑 · 안내

    private func walletRow(_ loaded: Bool) -> some View {
        HStack(spacing: 10) {
            WalletChip(label: locale.t(.starlightStardust), value: loaded ? voyageFmt(store.stardust) : "…",
                       valueColor: VoyageColor.dust, hasTrailing: false) {
                StardustIcon().frame(width: 19, height: 19)
            } trailing: {
                EmptyView()
            }
            WalletChip(label: locale.t(.starlightKeys), value: voyageFmt(quests.keys),
                       valueColor: VoyageColor.ink, hasTrailing: true) {
                CrystalKeyIcon().frame(width: 19, height: 19)
            } trailing: {
                Button {
                    Haptics.tick()
                    if loaded { keySheet = true }
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color(hex: 0x2A1A00))
                        .frame(width: 34, height: 34)
                        .background(LinearGradient(colors: [Color(hex: 0xFFF0C2), VoyageColor.gold, Color(hex: 0xFFB259)],
                                                   startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                }
                .buttonStyle(VoyagePressStyle(scale: 0.88))
                .accessibilityLabel(locale.t(.voyageKeyTitle))
            }
        }
        .padding(.horizontal, 18)
    }

    private var findsLink: some View {
        Button {
            Haptics.tick()
            AchievementsTab.shared.request(AchievementsTab.special)
            TabRouter.shared.go(TabRouter.achievements)
        } label: {
            HStack(spacing: 2) {
                Text(locale.t(.voyageFindsLink)).font(.minSans(12)).foregroundStyle(VoyageColor.sub)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(VoyageColor.sub)
            }
            .padding(.leading, 12).padding(.trailing, 6).padding(.vertical, 6)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
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

    private func sail(_ k: Int) {
        guard !busy, opening == nil, store.walletLoaded else { return }
        let r = route
        if store.stardust < store.config.route(r).cost * k {
            fail("insufficient")
            return
        }
        busy = true
        Haptics.medium()
        // 요청은 창을 띄우자마자 — 두드리는 동안 결과가 온다(늦으면 세 번째 두드림 뒤 수정구가 떨며 기다린다).
        withAnimation(.easeOut(duration: 0.2)) { opening = OpeningState(count: k, route: r) }
        Task {
            let res = await store.voyage(r, k)
            busy = false
            switch res {
            case .done(let result):
                let draws = StarlightStore.draws(result)
                guard !draws.isEmpty else {
                    withAnimation { opening = nil }
                    fail("server")
                    return
                }
                let best = draws.max { voyageRank($0.rarity) < voyageRank($1.rarity) }?.rarity ?? ""
                StaryAnalytics.log("voyage", ["route": r, "count": k, "best": best])
                QuestStore.report(.openBox)
                opening?.draws = draws
            case .failed(let code):
                withAnimation { opening = nil }
                fail(code)
            }
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

// MARK: - 바탕

/// 깊은 우주 — 남색 그라데이션 + 수정구 뒤 경로 빛 성운(경로를 바꾸면 0.6초 동안 색이 바뀐다) + 보랏빛·푸른 성운 두 덩이
/// + 깜빡이는 잔별 + 가끔 지나가는 별똥별. 시간은 수정구 시계(`OrbEngine.t`)라 화면이 가려지면 같이 쉰다. Android `SpaceBackdrop` 패리티.
private struct SpaceBackdrop: View {
    let orb: OrbEngine
    let anchor: StageAnchor

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0x03050D), Color(hex: 0x070B1E), Color(hex: 0x0A0B1C), Color(hex: 0x04050C)],
                           startPoint: .top, endPoint: .bottom)
            GeometryReader { geo in
                let w = geo.size.width
                ZStack {
                    RadialGradient(colors: [Color(hex: 0x7B5CFF).opacity(0.11), .clear],
                                   center: UnitPoint(x: 0.12, y: 0.16), startRadius: 0, endRadius: w * 0.85)
                    RadialGradient(colors: [Color(hex: 0x3E7BFF).opacity(0.08), .clear],
                                   center: UnitPoint(x: 0.92, y: 0.8), startRadius: 0, endRadius: w * 0.75)
                }
            }
            GeometryReader { geo in
                let top = geo.frame(in: .global).minY
                OrbCanvas(frames: orb.frames) { c, size in
                    draw(&c, size, top: top)
                }
            }
        }
        .allowsHitTesting(false)
    }

    private func draw(_ c: inout GraphicsContext, _ size: CGSize, top: CGFloat) {
        let t = orb.t
        let w = size.width
        let h = size.height
        let accent = RouteTint.normal.accent.blended(with: RouteTint.rare.accent, fraction: Double(orb.routeMix))
        let fy = anchor.y > 0 ? anchor.y - top : h * 0.42
        let center = CGPoint(x: w / 2, y: fy)
        let r1 = w * 1.05
        c.fill(StarlightArt.circle(center, r1), with: .radialGradient(
            Gradient(colors: [accent.opacity(0.17), accent.opacity(0.05), .clear]), center: center, startRadius: 0, endRadius: r1))
        // 잔별(굵은 몇 개는 네 꼭지 반짝이)
        let sky = backdropSky
        for i in 0..<sky.count {
            let tw = 0.5 + 0.5 * sin(t * sky.w[i] + sky.p[i])
            let p = CGPoint(x: sky.x[i] * w, y: sky.y[i] * h)
            if sky.s[i] > 1.48 {
                c.fill(StarlightArt.sparkle(p, 2.2 + 1.6 * tw), with: .color(.white.opacity(Double(0.35 + 0.5 * tw))))
            } else {
                let a = (0.10 + 0.45 * tw) * min(sky.s[i] / 1.6, 1)
                c.fill(StarlightArt.circle(p, sky.s[i] * 0.75), with: .color(.white.opacity(Double(a))))
            }
        }
        // 별똥별 — 8초마다 0.9초, 매번 다른 자리(위쪽 하늘)
        let cycle: CGFloat = 8
        let idx = floor(t / cycle)
        let u = (t - idx * cycle) / 0.9
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
        let fade = Double(sin(u * .pi))
        var streak = Path()
        streak.move(to: tail)
        streak.addLine(to: head)
        c.stroke(streak, with: .linearGradient(Gradient(colors: [.clear, .white.opacity(0.55 * fade)]), startPoint: tail, endPoint: head),
                 style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        c.fill(StarlightArt.circle(head, 1.4), with: .color(.white.opacity(0.8 * fade)))
    }
}

// MARK: - 무대

/// 우주 항해 무대 — 제목이 수정구 빛 위에 떠 있고, 오른쪽 위 작은 "확률" 버튼. 수정구를 누르면 빛이 출렁인다(깨지지 않는다).
private struct VoyageStage: View {
    let orb: OrbEngine
    let k: CGFloat
    let stageH: CGFloat
    let accent: Color
    let onOdds: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        ZStack(alignment: .top) {
            OrbCanvas(frames: orb.frames) { c, size in
                let ox = size.width / 2 - orbCX * k
                orb.draw(&c, k: k, ox: ox, oy: 0, viewW: size.width, viewH: size.height)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                orb.tap(nil, nil)
                Haptics.tick()
            }
            VStack(spacing: 4) {
                Text(locale.t(.voyageTitle))
                    .font(.minSans(24, .light))
                    .tracking(7)
                    .foregroundStyle(VoyageColor.ink)
                    .shadow(color: accent.opacity(0.7), radius: 10)
                Text(locale.t(.voyageSub))
                    .font(.minSans(12))
                    .foregroundStyle(VoyageColor.sub.opacity(0.85))
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 2)
            .padding(.horizontal, 70)
            .allowsHitTesting(false)
            HStack {
                Spacer()
                Button(action: onOdds) {
                    HStack(spacing: 4) {
                        Image(systemName: "info.circle").font(.system(size: 12, weight: .medium))
                        Text(locale.t(.voyageOdds)).font(.minSans(11, .medium))
                    }
                    .foregroundStyle(VoyageColor.sub)
                    .padding(.leading, 8).padding(.trailing, 10).padding(.vertical, 5)
                    .background(Color.white.opacity(0.08), in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.15), lineWidth: 0.75))
                    .contentShape(Capsule())
                }
                .buttonStyle(VoyagePressStyle(scale: 0.94))
            }
            .padding(.trailing, 16)
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .frame(height: stageH * k)
    }
}

// MARK: - 지갑 · 광고

private struct WalletChip<Icon: View, Trailing: View>: View {
    let label: String
    let value: String
    let valueColor: Color
    let hasTrailing: Bool
    @ViewBuilder let icon: () -> Icon
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 0) {
            icon()
                .frame(width: 34, height: 34)
                .background(Color.white.opacity(0.07), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(.minSans(10)).foregroundStyle(VoyageColor.dim).lineLimit(1)
                Text(value)
                    .font(.system(size: 17, weight: .bold).monospacedDigit())
                    .foregroundStyle(valueColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .padding(.leading, 9)
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .padding(.leading, 7)
        .padding(.trailing, hasTrailing ? 7 : 16)
        .frame(height: 48)
        .background(LinearGradient(colors: [Color.white.opacity(0.12), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                    in: Capsule())
        .overlay(Capsule().strokeBorder(LinearGradient(colors: [Color.white.opacity(0.2), Color.white.opacity(0.05)],
                                                       startPoint: .top, endPoint: .bottom), lineWidth: 0.75))
    }
}

/// 광고 보고 별가루 받기(광고제거 계정은 광고 없이) — 오른쪽에 오늘 남은 횟수. 다 받으면 흐려진다.
private struct AdStrip: View {
    let busy: Bool
    let action: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var adFree = AdFreeAccount.shared
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let loaded = store.walletLoaded
        let left = store.adsLeft
        let on = loaded && left > 0
        let free = adFree.active
        let dust = VoyageColor.dust
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        let status = !loaded || busy ? "…"
            : left > 0 ? String(format: locale.t(.voyageAdLeft), left) : locale.t(.voyageAdDoneToday)
        Button(action: action) {
            HStack(spacing: 0) {
                Image(systemName: free ? "sparkles" : "play.fill")
                    .font(.system(size: free ? 12 : 11, weight: .bold))
                    .foregroundStyle(on ? Color(hex: 0x2A1A00) : VoyageColor.dim)
                    .frame(width: 26, height: 26)
                    .background(on
                                ? LinearGradient(colors: [Color(hex: 0xFFF3D0), Color(hex: 0xFFC75A)], startPoint: .topLeading, endPoint: .bottomTrailing)
                                : LinearGradient(colors: [Color.white.opacity(0.2), Color.white.opacity(0.13)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: Circle())
                Text(String(format: locale.t(free ? .voyageAdFree : .voyageAd), store.config.adStardust))
                    .font(.minSans(13, .medium))
                    .foregroundStyle(on ? VoyageColor.ink : VoyageColor.dim)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.leading, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(status)
                    .font(.minSans(11))
                    .foregroundStyle(on ? dust.opacity(0.85) : VoyageColor.dim)
                    .lineLimit(1)
            }
            .padding(.leading, 9)
            .padding(.trailing, 14)
            .frame(height: 44)
            .background(LinearGradient(colors: [dust.opacity(on ? 0.16 : 0.06), dust.opacity(0.03)], startPoint: .leading, endPoint: .trailing),
                        in: shape)
            .overlay(shape.strokeBorder(LinearGradient(colors: [dust.opacity(on ? 0.45 : 0.14), dust.opacity(0.08)],
                                                       startPoint: .leading, endPoint: .trailing), lineWidth: 0.75))
            .contentShape(shape)
        }
        .buttonStyle(VoyagePressStyle(scale: 0.98))
        .disabled(busy)
        .padding(.horizontal, 18)
    }
}

// MARK: - 경로 · 횟수 · 출항

/// 경로 표 — 이름 · 한 번 값 · 영웅 이상 확률. 고른 표는 경로 빛 테가 밝아진다.
private struct RouteTicket: View {
    let route: String
    let selected: Bool
    let cost: Int
    let epicPlus: Double
    let action: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let tint = RouteTint.of(route)
        let sel: Double = selected ? 1 : 0
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 7) {
                    ZStack {
                        Circle().fill(tint.accent.opacity(0.25 * sel))
                        Circle()
                            .fill(RadialGradient(colors: [tint.light, tint.deep, tint.deep.opacity(0.6)],
                                                 center: UnitPoint(x: 0.26, y: 0.18), startRadius: 0, endRadius: 5.4))
                            .frame(width: 7.4, height: 7.4)
                    }
                    .frame(width: 12, height: 12)
                    Text(routeName(route))
                        .font(.minSans(13, .semibold))
                        .foregroundStyle(selected ? VoyageColor.ink : VoyageColor.sub)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                HStack(spacing: 5) {
                    StardustIcon().frame(width: 16, height: 16)
                    Text(voyageFmt(cost))
                        .font(.system(size: 20, weight: .bold).monospacedDigit())
                        .foregroundStyle(VoyageColor.dust.opacity(selected ? 1 : 0.7))
                }
                .padding(.top, 8)
                Text(String(format: locale.t(.voyageEpicPlus), voyagePct(epicPlus)))
                    .font(.minSans(11))
                    .foregroundStyle(tint.accent.opacity(0.5 + 0.5 * sel))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.top, 3)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(LinearGradient(colors: [tint.accent.opacity(0.05 + 0.13 * sel), Color.white.opacity(0.04)],
                                       startPoint: .top, endPoint: .bottom), in: shape)
            .overlay(shape.strokeBorder(LinearGradient(colors: [tint.accent.opacity(0.16 + 0.64 * sel), tint.accent.opacity(0.05 + 0.22 * sel)],
                                                       startPoint: .top, endPoint: .bottom), lineWidth: 0.75 + 0.5 * sel))
            .contentShape(shape)
        }
        .buttonStyle(VoyagePressStyle(scale: 0.97))
        .animation(.easeInOut(duration: 0.32), value: selected)
    }
}

/// ×1 · ×5 · ×10 — 유리 캡슐 안 고른 칸만 경로 빛.
private struct CountPicker: View {
    let counts: [Int]
    let value: Int
    let accent: Color
    let onPick: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(counts, id: \.self) { c in
                let on = c == value
                Button { onPick(c) } label: {
                    Text("×\(c)")
                        .font(.system(size: 14, weight: .semibold).monospacedDigit())
                        .foregroundStyle(on ? VoyageColor.ink : VoyageColor.dim)
                        .padding(.horizontal, 8)
                        .frame(minWidth: 42, maxHeight: .infinity)
                        .background(accent.opacity(on ? 0.2 : 0), in: Capsule())
                        .overlay(Capsule().strokeBorder(accent.opacity(on ? 0.55 : 0), lineWidth: 0.75))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .frame(maxHeight: .infinity)
        .background(Color.white.opacity(0.07), in: Capsule())
        .overlay(Capsule().strokeBorder(VoyageColor.line, lineWidth: 0.75))
        .animation(.easeOut(duration: 0.2), value: value)
        .animation(.easeInOut(duration: 0.6), value: accent)
    }
}

/// 출항 — 경로 빛 캡슐에 "출항하기 | ✦ 값", 가끔 빛이 비스듬히 스친다(수정구 시계 — 4.6초마다 2.4초). 모자라면 얼마 모자란지.
private struct SailButton: View {
    let orb: OrbEngine
    let cost: Int
    let short: Int
    let loaded: Bool
    let busy: Bool
    let tint: RouteTint
    let action: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let ready = loaded && short <= 0
        Button(action: action) {
            label(ready)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ready
                            ? LinearGradient(colors: [tint.light, tint.deep], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [Color.white.opacity(0.14), Color.white.opacity(0.07)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay {
                    if ready {
                        OrbCanvas(frames: orb.frames) { c, size in shine(&c, size) }
                            .allowsHitTesting(false)
                    }
                }
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(ready ? Color.white.opacity(0.45) : VoyageColor.line, lineWidth: 0.75))
                .contentShape(Capsule())
                .animation(.easeInOut(duration: 0.6), value: tint.accent)
        }
        .buttonStyle(VoyagePressStyle(scale: ready ? 0.97 : 1))
        .disabled(!ready || busy)
    }

    @ViewBuilder
    private func label(_ ready: Bool) -> some View {
        if !loaded {
            Text("…").font(.minSans(16)).foregroundStyle(VoyageColor.sub)
        } else if short > 0 {
            Text(String(format: locale.t(.voyageShort), short))
                .font(.minSans(14, .medium)).foregroundStyle(VoyageColor.sub)
                .lineLimit(1).minimumScaleFactor(0.8)
        } else {
            HStack(spacing: 0) {
                Text(locale.t(.voyageLaunch))
                    .font(.minSans(16, .bold)).tracking(1)
                    .foregroundStyle(tint.ink)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Rectangle().fill(tint.ink.opacity(0.25)).frame(width: 1, height: 16).padding(.horizontal, 12)
                StardustIcon(tint: tint.ink).frame(width: 15, height: 15)
                Text(voyageFmt(cost))
                    .font(.system(size: 15, weight: .bold).monospacedDigit())
                    .foregroundStyle(tint.ink)
                    .padding(.leading, 4)
            }
            .padding(.horizontal, 12)
        }
    }

    private func shine(_ c: inout GraphicsContext, _ size: CGSize) {
        let p = orb.t.truncatingRemainder(dividingBy: 4.6)
        guard p >= 2.2 else { return }
        let x = (-0.5 + 2 * (p - 2.2) / 2.4) * size.width
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
            Gradient(colors: [.clear, .white.opacity(0.45), .clear]),
            startPoint: CGPoint(x: x - size.height, y: 0), endPoint: CGPoint(x: x + size.height * 0.2, y: size.height)))
    }
}

// MARK: - 열쇠 구매(작고 단순하게)

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
        let top = min(max(store.stardust / each, 1), max(1, cfg.maxExchange))
        let total = each * n
        let short = max(0, total - store.stardust)
        let keyInk = Color(hex: 0x2A1A00)
        VStack(spacing: 0) {
            CrystalKeyIcon(glow: true).frame(width: 46, height: 46)
            Text(locale.t(.voyageKeyTitle))
                .font(.minSans(16, .semibold)).foregroundStyle(VoyageColor.ink)
                .padding(.top, 8)
            Text(String(format: locale.t(.voyageKeyEach), each))
                .font(.minSans(12)).foregroundStyle(VoyageColor.dim)
                .padding(.top, 2)
            HStack(spacing: 0) {
                StepButton(systemImage: "minus", enabled: n > 1) { n -= 1 }
                Text("\(n)")
                    .font(.system(size: 30, weight: .semibold).monospacedDigit())
                    .foregroundStyle(VoyageColor.ink)
                    .frame(minWidth: 76)
                StepButton(systemImage: "plus", enabled: n < top) { n += 1 }
            }
            .padding(.top, 16)
            Button { buy(n) } label: {
                HStack(spacing: 0) {
                    if short > 0 {
                        Text(String(format: locale.t(.voyageShort), short))
                            .font(.minSans(14, .medium)).foregroundStyle(VoyageColor.sub)
                    } else if busy {
                        StarLoadingView(size: 18, color: keyInk)
                    } else {
                        Text(locale.t(.voyageBuy)).font(.minSans(15, .bold)).foregroundStyle(keyInk)
                        StardustIcon(tint: keyInk).frame(width: 14, height: 14).padding(.leading, 10)
                        Text(voyageFmt(total))
                            .font(.system(size: 15, weight: .bold).monospacedDigit())
                            .foregroundStyle(keyInk)
                            .padding(.leading, 4)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(short <= 0
                            ? LinearGradient(colors: [Color(hex: 0xFFF0C2), Color(hex: 0xFFC75A)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [Color.white.opacity(0.14), Color.white.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: Capsule())
                .contentShape(Capsule())
            }
            .buttonStyle(VoyagePressStyle(scale: 0.97))
            .disabled(short > 0 || busy)
            .padding(.top, 18)
            if let error {
                Text(error)
                    .font(.minSans(12)).foregroundStyle(Color(hex: 0xFFB3B3))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
            }
        }
        .padding(20)
        .frame(maxWidth: 300)
        .glassPanel(cornerRadius: StaryDialogStyle.corner, tint: StaryDialogStyle.glassTint)
        .padding(.horizontal, StaryDialogStyle.screenPadding)
        .onChange(of: top) { m in if n > m { n = m } }
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
                // 이 창은 화면 전체를 덮어 아래 토스트가 안 보인다 — 창 안에 적는다.
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
                .foregroundStyle(enabled ? VoyageColor.ink : VoyageColor.dim)
                .frame(width: 42, height: 42)
                .background(Color.white.opacity(enabled ? 0.12 : 0.05), in: Circle())
                .overlay(Circle().strokeBorder(enabled ? Color.white.opacity(0.2) : VoyageColor.line, lineWidth: 0.75))
        }
        .buttonStyle(VoyagePressStyle(scale: 0.9))
        .disabled(!enabled)
    }
}

// MARK: - 확률(작은 버튼으로만 연다)

/// 확률 안내 — 경로마다 등급 확률 · 그 등급에 든 아이템 수(n종) · 하나당 확률, 그리고 규칙(같은 등급은 같은 확률 · 매번 독립 · 중복 환급).
/// 확률형 아이템 표시 기준에 맞춰 **모든 확률을 숫자로** 보여 준다(아이템 이름은 숨긴다 — 무엇이 나올지는 떠나 봐야 안다).
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
