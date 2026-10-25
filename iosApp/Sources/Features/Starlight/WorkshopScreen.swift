import SwiftUI

private let ink = Color(hex: 0xF0F0F0)
private let sub = Color(hex: 0xB8C0CC)
private let dim = Color(hex: 0x7D8696)
private let card = Color.white.opacity(0.08)
private let line = Color.white.opacity(0.10)
private let gold = Color(hex: 0xFFD27A)

/// 별빛 공방(2026-10-22) — 별가루를 열쇠·성운 상자로 바꾸고, 상자를 연다. 모은 것은 수집품 화면에서 본다.
/// 서버가 모든 계산을 한다(`StarlightStore.request`) — 이 화면은 요청을 보내고 결과를 연출만 한다. Android `WorkshopScreen.kt` 패리티.
struct WorkshopScreen: View {
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var quests = QuestStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var bgScroll = BackgroundScroll()
    @State private var busy = false
    @State private var opening: OpeningState?
    @State private var keyCount = 1
    @State private var oddsOpen = false

    var body: some View {
        let cfg = store.config
        ZStack {
            ScreenBackground(name: "mydiary_bg", darken: 0.82, scroll: bgScroll)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    balanceCard

                    // ── 상자 열기(대표 전설 + 안내) ──
                    openCard

                    // ── 바꾸기 ──
                    sectionTitle(locale.t(.starlightExchangeTitle))
                    ExchangeRow(
                        title: locale.t(.starlightToKey), desc: locale.t(.starlightToKeyDesc),
                        cost: cfg.keyCost * keyCount,
                        enabled: !busy && store.stardust >= cfg.keyCost * keyCount,
                        art: AnyView(CrystalKeyIcon().frame(width: 28, height: 28)),
                        stepper: AnyView(CountStepper(value: $keyCount,
                                                      max: min(99, max(1, store.stardust / max(1, cfg.keyCost)))))
                    ) { exchange("key", keyCount) }
                    ExchangeRow(
                        title: locale.t(.starlightToBox), desc: locale.t(.starlightToBoxDesc),
                        cost: cfg.boxCost,
                        enabled: !busy && store.stardust >= cfg.boxCost,
                        art: AnyView(NebulaBoxArt(animated: false).frame(width: 40, height: 40)),
                        stepper: nil
                    ) { exchange("box", 1) }

                    // ── 도감 요약(목록은 수집품 화면에서) ──
                    collectionSummary

                    // ── 확률 안내 ──
                    oddsCard
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
            }
            .backgroundScrollSource(bgScroll)

            if let st = opening {
                NebulaOpening(state: st) { if st.draws != nil { withAnimation(.easeOut(duration: 0.2)) { opening = nil } } }
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .navigationTitle(locale.t(.navWorkshop))
        .navigationBarTitleDisplayMode(.inline)
        // 여는 연출은 화면 전체 — 상단바를 잠시 숨긴다.
        .toolbar(opening != nil ? .hidden : .visible, for: .navigationBar)
        .onAppear { StaryAnalytics.log("workshop_open", ["stardust": store.stardust, "boxes": store.boxes]) }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.minSans(15, .semibold)).foregroundStyle(ink).padding(.top, 4)
    }

    private func fail(_ code: String) {
        Haptics.tick()
        let key: L10n
        switch code {
        case "insufficient": key = .starlightErrInsufficient
        case "no_boxes": key = .starlightErrNoBoxes
        case "empty_pool": key = .starlightErrEmptyPool
        case "timeout": key = .starlightErrTimeout
        default: key = .starlightErrGeneric
        }
        GlobalToast.shared.show(locale.t(key))
    }

    private func exchange(_ to: String, _ count: Int) {
        guard !busy else { return }
        busy = true
        Task {
            let r = await store.request("exchange", ["to": to, "count": count])
            busy = false
            switch r {
            case .done:
                Haptics.celebrate()
                StaryAnalytics.log("workshop_exchange", ["to": to, "count": count])
                GlobalToast.shared.show(String(format: locale.t(to == "key" ? .starlightMadeKeys : .starlightMadeBox), count))
                keyCount = 1
            case .failed(let code): fail(code)
            }
        }
    }

    private func open(_ count: Int) {
        guard !busy, count > 0 else { return }
        busy = true
        withAnimation(.easeOut(duration: 0.2)) { opening = OpeningState(count: count) }
        Task {
            let r = await store.request("openBox", ["count": count])
            busy = false
            switch r {
            case .done(let result):
                let draws = StarlightStore.draws(result)
                let best = draws.max { rank($0.rarity) < rank($1.rarity) }?.rarity ?? ""
                StaryAnalytics.log("workshop_open_box", ["count": count, "best": best])
                QuestStore.report(.openBox)
                opening?.draws = draws
            case .failed(let code):
                withAnimation { opening = nil }
                fail(code)
            }
        }
    }

    private var balanceCard: some View {
        HStack(spacing: 12) {
            StardustIcon().frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text(locale.t(.starlightStardust)).font(.minSans(12)).foregroundStyle(sub)
                Text(store.walletLoaded ? "\(store.stardust)" : "…")
                    .font(.system(size: 28, weight: .bold).monospacedDigit())
                    .foregroundStyle(StarlightArt.dust)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                miniBalance(AnyView(NebulaBoxArt(animated: false).frame(width: 20, height: 20)), store.boxes, locale.t(.starlightBox))
                miniBalance(AnyView(CrystalKeyIcon().frame(width: 17, height: 17)), quests.keys, locale.t(.starlightKeys))
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .background(LinearGradient(colors: [Color(hex: 0x221B3D), Color(hex: 0x14192A)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(
            LinearGradient(colors: [StarlightArt.dust.opacity(0.55), StarlightArt.violet.opacity(0.35)],
                           startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
    }

    private func miniBalance(_ icon: AnyView, _ value: Int, _ label: String) -> some View {
        HStack(spacing: 4) {
            Text(label).font(.minSans(11)).foregroundStyle(dim).padding(.trailing, 2)
            icon
            Text("\(value)").font(.system(size: 14, weight: .semibold).monospacedDigit()).foregroundStyle(ink)
        }
    }

    /// 상자 열기 카드 — 위에 **상자 속 전설 3개를 부채꼴로 살짝 겹쳐** 보여 주고(DB `showcase`), 그 앞에 성운 상자,
    /// 아래에 안내 문구와 열기 버튼.
    private var openCard: some View {
        let boxes = store.boxes
        let many = min(boxes, store.config.maxOpen)
        let showcase = store.showcaseItems
        // 양옆 → 가운데 순으로 겹친다: showcase[0] 왼쪽, [1] 가운데, [2] 오른쪽.
        let slots = [FanSlot(idx: 0, dx: -84, dy: 18, rot: -13), FanSlot(idx: 2, dx: 84, dy: 18, rot: 13), FanSlot(idx: 1, dx: 0, dy: 0, rot: 0)]
        return VStack(spacing: 0) {
            Text(locale.t(.starlightLineup))
                .font(.minSans(11, .semibold)).tracking(1.5).foregroundStyle(gold)
            ZStack(alignment: .top) {
                ForEach(slots, id: \.idx) { s in
                    if s.idx < showcase.count {
                        ShowcaseCard(item: showcase[s.idx])
                            .rotationEffect(.degrees(s.rot))
                            .offset(x: s.dx, y: s.dy)
                    }
                }
                NebulaBoxArt(glow: boxes > 0 ? 0.3 : 0)
                    .frame(width: 150, height: 150)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 250)
            .padding(.top, 10)
            Text(locale.t(.starlightShowcase))
                .font(.minSans(15, .semibold)).foregroundStyle(ink).lineSpacing(4)
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
            Text(boxes > 0 ? String(format: locale.t(.starlightBoxesReady), boxes)
                           : String(format: locale.t(.starlightNoBox), store.config.boxCost))
                .font(.minSans(12)).foregroundStyle(sub).multilineTextAlignment(.center)
                .padding(.top, 6)
            HStack(spacing: 10) {
                StarlightOpenButton(text: locale.t(.starlightOpenOne), enabled: !busy && boxes >= 1, primary: true) { open(1) }
                if many >= 2 {
                    StarlightOpenButton(text: String(format: locale.t(.starlightOpenMany), many), enabled: !busy, primary: false) { open(many) }
                }
            }
            .padding(.top, 14)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16).padding(.vertical, 18)
        .background(LinearGradient(colors: [Color(hex: 0x241A45), Color(hex: 0x151230), Color(hex: 0x0F1322)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(
            LinearGradient(colors: [gold.opacity(0.45), StarlightArt.violet.opacity(0.3)], startPoint: .top, endPoint: .bottom), lineWidth: 1))
    }

    /// 도감 요약 — 모은 수와 진행 막대, 누르면 수집품 화면.
    private var collectionSummary: some View {
        let items = store.shownItems
        let ownedCount = items.filter { (store.owned[$0.id] ?? 0) > 0 }.count
        let frac = items.isEmpty ? 0 : CGFloat(ownedCount) / CGFloat(items.count)
        return Button {
            Haptics.tick()
            TabRouter.shared.go(TabRouter.collection)
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(locale.t(.starlightCollection)).font(.minSans(15, .semibold)).foregroundStyle(ink)
                        Text(locale.t(.starlightCollectionDesc)).font(.minSans(11)).foregroundStyle(dim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Text(locale.t(.starlightCollectionOpen)).font(.minSans(12, .semibold)).foregroundStyle(StarlightArt.violet)
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(StarlightArt.violet)
                }
                HStack(spacing: 10) {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.white.opacity(0.12))
                            Capsule().fill(LinearGradient(colors: [StarlightArt.violet, gold], startPoint: .leading, endPoint: .trailing))
                                .frame(width: geo.size.width * frac)
                        }
                    }
                    .frame(height: 6)
                    Text(String(format: locale.t(.starlightCollected), ownedCount, items.count))
                        .font(.system(size: 12).monospacedDigit()).foregroundStyle(sub)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(line, lineWidth: 0.75))
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    // MARK: 확률 안내

    private var oddsCard: some View {
        let cfg = store.config
        let lang = locale.effectiveLanguage
        return VStack(alignment: .leading, spacing: 6) {
            Text(locale.t(.starlightOddsTitle)).font(.minSans(14, .semibold)).foregroundStyle(ink)
            ForEach(StarlightStore.rarities.reversed(), id: \.self) { r in
                HStack(spacing: 8) {
                    Circle().fill(StarlightArt.rarityColor(r)).frame(width: 8, height: 8)
                    Text(rarityName(r)).font(.minSans(12)).foregroundStyle(sub)
                    Spacer()
                    Text(pct(cfg.percent(r))).font(.system(size: 12).monospacedDigit()).foregroundStyle(ink)
                }
            }
            Group {
                Text(String(format: locale.t(.starlightPity), cfg.pityEpic, cfg.pityLegendary))
                if store.walletLoaded {
                    Text(String(format: locale.t(.starlightPityNow), store.sinceEpic, store.sinceLegendary))
                }
                Text(String(format: locale.t(.starlightDupRule),
                            cfg.dupRefund["common"] ?? 0, cfg.dupRefund["rare"] ?? 0, cfg.dupRefund["epic"] ?? 0, cfg.dupRefund["legendary"] ?? 0))
            }
            .font(.minSans(11)).foregroundStyle(dim).lineSpacing(2)
            .fixedSize(horizontal: false, vertical: true)
            // 아이템별 확률(접어 둔다) — 같은 등급 안에서는 모두 같은 확률.
            Button {
                withAnimation(.easeInOut(duration: 0.22)) { oddsOpen.toggle() }
            } label: {
                HStack {
                    Text(locale.t(oddsOpen ? .starlightOddsHide : .starlightOddsItems))
                        .font(.minSans(12, .medium)).foregroundStyle(StarlightArt.violet)
                    Spacer()
                    Image(systemName: oddsOpen ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(StarlightArt.violet)
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if oddsOpen {
                let pool = store.items.filter(\.active)
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(StarlightStore.rarities.reversed(), id: \.self) { r in
                        let group = pool.filter { $0.rarity == r }
                        if !group.isEmpty {
                            let each = cfg.percent(r) / Double(group.count)
                            Text(rarityName(r)).font(.minSans(11, .semibold)).foregroundStyle(StarlightArt.rarityColor(r)).padding(.top, 4)
                            ForEach(group) { item in
                                HStack {
                                    Text("\(item.name(in: lang)) · \(starlightKindName(item.kind))")
                                        .font(.minSans(11)).foregroundStyle(sub).lineLimit(1)
                                    Spacer()
                                    Text(pct(each)).font(.system(size: 11).monospacedDigit()).foregroundStyle(dim)
                                }
                            }
                        }
                    }
                }
                .transition(.opacity)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(line, lineWidth: 0.75))
    }
}

// MARK: - 공용 조각

private func rank(_ r: String) -> Int { StarlightStore.rarities.firstIndex(of: r) ?? 0 }

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

private func pct(_ v: Double) -> String {
    if v >= 10 { return String(format: "%.0f%%", v) }
    if v >= 1 { return String(format: "%.1f%%", v) }
    return String(format: "%.2f%%", v)
}

/// 장착 중인가. 칭호는 업적 칭호와 한 자리를 나눠 쓰므로 실제로 달려 있는 값(기기 사본 = users.equippedTitle)이 "sl:{id}" 일 때만 —
/// 공방 칭호를 단 뒤 업적 칭호로 바꾸면 starlightEquips.title 은 옛값으로 남는다(Android `isEquipped` 패리티).
@MainActor
func workshopIsEquipped(_ item: StarlightStore.Item) -> Bool {
    guard let slot = StarlightStore.Slot.of(kind: item.kind) else { return false }
    let mine = StarlightStore.shared.myEquips.of(slot) == item.id
    guard slot == .title else { return mine }
    guard let uid = StarlightStore.shared.currentUid else { return false }
    return UserDefaults.standard.string(forKey: "equipped_title_\(uid)") == StarlightStore.titlePrefix + item.id
}

/// 부채꼴 자리 하나(showcase 순번 · 위치 · 기울기).
private struct FanSlot {
    let idx: Int
    let dx: CGFloat
    let dy: CGFloat
    let rot: Double
}

/// 상자 속 전설 한 장 — 금테 크리스탈 카드.
private struct ShowcaseCard: View {
    let item: StarlightStore.Item
    @ObservedObject private var locale = LocaleManager.shared
    @State private var glow = false

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        VStack(spacing: 0) {
            ItemArt(item: item).frame(width: 70, height: 70)
            Spacer(minLength: 0)
            Text(item.name(in: locale.effectiveLanguage))
                .font(.minSans(10, .medium)).foregroundStyle(ink)
                .multilineTextAlignment(.center).lineLimit(2)
        }
        .padding(.horizontal, 6).padding(.vertical, 8)
        .frame(width: 98, height: 128)
        .background(LinearGradient(colors: [Color(hex: 0x3A2A1A), Color(hex: 0x1C1830), Color(hex: 0x121626)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(
            LinearGradient(colors: [c.opacity(glow ? 0.8 : 0.45), c.opacity(0.25)], startPoint: .top, endPoint: .bottom), lineWidth: 1.2))
        .onAppear { withAnimation(.easeInOut(duration: 4.2).repeatForever(autoreverses: true)) { glow = true } }
    }
}

private struct ExchangeRow: View {
    let title: String
    let desc: String
    let cost: Int
    let enabled: Bool
    let art: AnyView
    let stepper: AnyView?
    let onMake: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(LinearGradient(colors: [Color(hex: 0x26304A), Color(hex: 0x161C2C)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    art
                }
                .frame(width: 46, height: 46)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.minSans(14, .medium)).foregroundStyle(ink)
                    Text(desc).font(.minSans(11)).foregroundStyle(dim).fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack {
                if let stepper { stepper }
                Spacer()
                Button(action: onMake) {
                    HStack(spacing: 5) {
                        StardustIcon(tint: enabled ? QuestTheme.ink : dim).frame(width: 14, height: 14)
                        Text(String(format: locale.t(.starlightMakeCost), cost))
                            .font(.minSans(13, .semibold))
                            .foregroundStyle(enabled ? QuestTheme.ink : dim)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(enabled
                                ? LinearGradient(colors: [StarlightArt.dust, QuestTheme.goldDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
                                : LinearGradient(colors: [Color.white.opacity(0.13), Color.white.opacity(0.13)], startPoint: .top, endPoint: .bottom),
                                in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
            }
        }
        .padding(12)
        .background(card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(line, lineWidth: 0.75))
    }
}

private struct CountStepper: View {
    @Binding var value: Int
    let max: Int

    var body: some View {
        HStack(spacing: 0) {
            Button { Haptics.tick(); value -= 1 } label: {
                Image(systemName: "minus").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(value > 1 ? ink : dim).frame(width: 34, height: 34)
            }
            .buttonStyle(.plain).disabled(value <= 1)
            Text("\(value)").font(.system(size: 14, weight: .semibold).monospacedDigit()).foregroundStyle(ink)
                .frame(minWidth: 22)
            Button { Haptics.tick(); value += 1 } label: {
                Image(systemName: "plus").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(value < max ? ink : dim).frame(width: 34, height: 34)
            }
            .buttonStyle(.plain).disabled(value >= max)
        }
        .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// 공방·수집품 공용 버튼(보라 = 주 동작, 테두리 = 보조).
struct StarlightOpenButton: View {
    let text: String
    let enabled: Bool
    let primary: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.minSans(14, .semibold))
                .foregroundStyle(!enabled ? dim : primary ? Color(hex: 0x1A0B33) : ink)
                .padding(.horizontal, 18).padding(.vertical, 11)
                .background(
                    !enabled ? LinearGradient(colors: [Color.white.opacity(0.13), Color.white.opacity(0.13)], startPoint: .top, endPoint: .bottom)
                    : primary ? LinearGradient(colors: [Color(hex: 0xE2C8FF), StarlightArt.violet], startPoint: .topLeading, endPoint: .bottomTrailing)
                    : LinearGradient(colors: [StarlightArt.violet.opacity(0.2), StarlightArt.violet.opacity(0.13)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(primary || !enabled ? Color.clear : StarlightArt.violet.opacity(0.6), lineWidth: 0.75))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
