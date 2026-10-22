import SwiftUI

private let ink = Color(hex: 0xF0F0F0)
private let sub = Color(hex: 0xB8C0CC)
private let dim = Color(hex: 0x7D8696)
private let card = Color.white.opacity(0.08)
private let line = Color.white.opacity(0.10)

/// 별빛 공방(2026-10-22) — 별가루를 열쇠·성운 상자로 바꾸고, 상자를 열고, 모은 것을 본다.
/// 서버가 모든 계산을 한다(`StarlightStore.request`) — 이 화면은 요청을 보내고 결과를 연출만 한다. Android `WorkshopScreen.kt` 패리티.
struct WorkshopScreen: View {
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var quests = QuestStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var bgScroll = BackgroundScroll()
    @State private var busy = false
    @State private var reveal: RevealState?
    @State private var keyCount = 1
    @State private var detail: StarlightStore.Item?

    var body: some View {
        let cfg = store.config
        ZStack {
            ScreenBackground(name: "mydiary_bg", darken: 0.82, scroll: bgScroll)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(locale.t(.starlightSubtitle))
                        .font(.minSans(13)).foregroundStyle(sub).lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                    balanceCard

                    sectionTitle(locale.t(.starlightExchangeTitle))
                    ExchangeRow(
                        title: locale.t(.starlightToKey), desc: locale.t(.starlightToKeyDesc),
                        cost: cfg.keyCost * keyCount,
                        enabled: !busy && store.stardust >= cfg.keyCost * keyCount,
                        art: AnyView(Image(systemName: "key.fill").font(.system(size: 20)).foregroundStyle(QuestTheme.gold)),
                        stepper: AnyView(CountStepper(value: $keyCount,
                                                 max: min(99, max(1, store.stardust / max(1, cfg.keyCost)))))
                    ) { exchange("key", keyCount) }
                    ExchangeRow(
                        title: locale.t(.starlightToBox), desc: locale.t(.starlightToBoxDesc),
                        cost: cfg.boxCost,
                        enabled: !busy && store.stardust >= cfg.boxCost,
                        art: AnyView(NebulaBoxArt(spin: false).frame(width: 34, height: 34)),
                        stepper: nil
                    ) { exchange("box", 1) }

                    sectionTitle(locale.t(.starlightOpenTitle))
                    openCard

                    collection
                    oddsCard
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 18).padding(.vertical, 14)
            }
            .backgroundScrollSource(bgScroll)

            if let st = reveal {
                RevealOverlay(state: st) { if st.draws != nil { reveal = nil } }
                    .transition(.opacity)
                    .zIndex(10)
            }
            if let item = detail {
                ItemDetailOverlay(item: item) { detail = nil }
                    .transition(.opacity)
                    .zIndex(11)
            }
        }
        .navigationTitle(locale.t(.navWorkshop))
        .navigationBarTitleDisplayMode(.inline)
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
        withAnimation(.easeOut(duration: 0.2)) { reveal = RevealState(count: count) }
        Task {
            let r = await store.request("openBox", ["count": count])
            busy = false
            switch r {
            case .done(let result):
                let draws = StarlightStore.draws(result)
                let best = draws.max { rank($0.rarity) < rank($1.rarity) }?.rarity ?? ""
                StaryAnalytics.log("workshop_open_box", ["count": count, "best": best])
                reveal?.draws = draws
            case .failed(let code):
                withAnimation { reveal = nil }
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
                miniBalance(AnyView(NebulaBoxArt(spin: false).frame(width: 18, height: 18)), store.boxes, locale.t(.starlightBox))
                miniBalance(AnyView(Image(systemName: "key.fill").font(.system(size: 12)).foregroundStyle(QuestTheme.gold)),
                            quests.keys, locale.t(.starlightKeys))
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
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

    private var openCard: some View {
        let boxes = store.boxes
        let many = min(boxes, store.config.maxOpen)
        return VStack(spacing: 0) {
            NebulaBoxArt(glow: boxes > 0 ? 0.25 : 0).frame(width: 132, height: 132)
            Text(boxes > 0 ? String(format: locale.t(.starlightBoxesReady), boxes)
                           : String(format: locale.t(.starlightNoBox), store.config.boxCost))
                .font(.minSans(12.5)).foregroundStyle(sub).multilineTextAlignment(.center)
                .padding(.top, 4)
            HStack(spacing: 10) {
                OpenButton(text: locale.t(.starlightOpenOne), enabled: !busy && boxes >= 1, primary: true) { open(1) }
                if many >= 2 {
                    OpenButton(text: String(format: locale.t(.starlightOpenMany), many), enabled: !busy, primary: false) { open(many) }
                }
            }
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(LinearGradient(colors: [Color(hex: 0x1B1533), Color(hex: 0x111622)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(StarlightArt.violet.opacity(0.35), lineWidth: 0.75))
    }

    // MARK: 도감

    @ViewBuilder
    private var collection: some View {
        let items = store.shownItems
        if !items.isEmpty {
            let ownedCount = items.filter { (store.owned[$0.id] ?? 0) > 0 }.count
            HStack(alignment: .bottom) {
                sectionTitle(locale.t(.starlightCollection))
                Spacer()
                Text(String(format: locale.t(.starlightCollected), ownedCount, items.count))
                    .font(.minSans(12)).foregroundStyle(dim)
            }
            ForEach(StarlightStore.rarities.reversed(), id: \.self) { rarity in
                let group = items.filter { $0.rarity == rarity }
                if !group.isEmpty {
                    let each = store.config.percent(rarity) / Double(group.count)
                    Text("\(rarityName(rarity)) · \(pct(store.config.percent(rarity)))")
                        .font(.minSans(12, .semibold)).foregroundStyle(StarlightArt.rarityColor(rarity))
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(group) { item in
                            ItemCell(item: item, owned: (store.owned[item.id] ?? 0) > 0, percent: each,
                                     equipped: workshopIsEquipped(item))
                                .onTapGesture { withAnimation(.easeOut(duration: 0.18)) { detail = item } }
                        }
                    }
                }
            }
        }
    }

    // MARK: 확률 안내

    private var oddsCard: some View {
        let cfg = store.config
        return VStack(alignment: .leading, spacing: 6) {
            Text(locale.t(.starlightOddsTitle)).font(.minSans(14, .semibold)).foregroundStyle(ink)
            ForEach(StarlightStore.rarities.reversed(), id: \.self) { r in
                HStack(spacing: 8) {
                    Circle().fill(StarlightArt.rarityColor(r)).frame(width: 8, height: 8)
                    Text(rarityName(r)).font(.minSans(12.5)).foregroundStyle(sub)
                    Spacer()
                    Text(pct(cfg.percent(r))).font(.system(size: 12.5).monospacedDigit()).foregroundStyle(ink)
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
            .font(.minSans(11.5)).foregroundStyle(dim).lineSpacing(2)
            .fixedSize(horizontal: false, vertical: true)
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

/// 서버가 users.equippedTitle 을 바꿨으니 기기 사본도 맞춘다 — 업적 화면·프로필이 이걸 읽는다.
@MainActor
private func syncTitle(_ itemId: String?) {
    guard let uid = StarlightStore.shared.currentUid else { return }
    let key = "equipped_title_\(uid)"
    if let itemId {
        UserDefaults.standard.set(StarlightStore.titlePrefix + itemId, forKey: key)
    } else if UserDefaults.standard.string(forKey: key)?.hasPrefix(StarlightStore.titlePrefix) == true {
        UserDefaults.standard.removeObject(forKey: key)
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
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.minSans(14, .medium)).foregroundStyle(ink)
                    Text(desc).font(.minSans(11.5)).foregroundStyle(dim).fixedSize(horizontal: false, vertical: true)
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

private struct OpenButton: View {
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

private struct ItemCell: View {
    let item: StarlightStore.Item
    let owned: Bool
    let percent: Double
    let equipped: Bool
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        VStack(spacing: 2) {
            ItemArt(item: item)
                .aspectRatio(1, contentMode: .fit)
                .padding(4)
                .opacity(owned ? 1 : 0.45)
                .overlay(alignment: .topTrailing) {
                    if owned {
                        Image(systemName: "checkmark")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(Color(hex: 0x111120))
                            .frame(width: 14, height: 14)
                            .background(c, in: Circle())
                    }
                }
            Text(item.name(in: locale.effectiveLanguage))
                .font(.minSans(10.5)).foregroundStyle(owned ? ink : sub)
                .multilineTextAlignment(.center).lineLimit(2)
            if equipped {
                Text(locale.t(.starlightEquipped)).font(.minSans(9.5, .semibold)).foregroundStyle(c)
            } else {
                Text(pct(percent)).font(.system(size: 9.5).monospacedDigit()).foregroundStyle(dim)
            }
        }
        .padding(.vertical, 8).padding(.horizontal, 4)
        .frame(maxWidth: .infinity)
        .background(owned ? c.opacity(0.10) : card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(owned ? c.opacity(0.55) : line, lineWidth: 0.75))
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }
}

/// 아이템 상세 — 설명 · 가진 수 · 쓰는 곳 · 장착/해제.
private struct ItemDetailOverlay: View {
    let item: StarlightStore.Item
    let onDismiss: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var busy = false
    @State private var tick = 0

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        let lang = locale.effectiveLanguage
        let count = store.owned[item.id] ?? 0
        let slot = StarlightStore.Slot.of(kind: item.kind)
        let equipped = tick >= 0 && workshopIsEquipped(item)
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea().onTapGesture { onDismiss() }
            VStack(spacing: 0) {
                ItemArt(item: item).frame(width: 110, height: 110)
                Text("\(rarityName(item.rarity)) · \(starlightKindName(item.kind))")
                    .font(.minSans(12, .semibold)).foregroundStyle(c).padding(.top, 10)
                Text(item.name(in: lang)).font(.minSans(18, .semibold)).foregroundStyle(ink)
                    .multilineTextAlignment(.center).padding(.top, 4)
                Text(item.desc(in: lang)).font(.minSans(13)).foregroundStyle(sub).lineSpacing(3)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 8)
                if count > 0 {
                    Text(String(format: locale.t(.starlightOwnedN), count)).font(.minSans(12)).foregroundStyle(c).padding(.top, 10)
                    if let note = usageNote {
                        Text(locale.t(note)).font(.minSans(11.5)).foregroundStyle(dim)
                            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
                    }
                    if let slot {
                        OpenButton(text: locale.t(equipped ? .starlightUnequip : .starlightEquip), enabled: !busy, primary: !equipped) {
                            toggle(slot, equipped)
                        }
                        .padding(.top, 14)
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 340)
            .background(Color(hex: 0x121821), in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(c.opacity(0.6), lineWidth: 1))
            .padding(.horizontal, 24)
            .onTapGesture { onDismiss() }
        }
    }

    /// 가진 아이템을 어디서 쓰는지(장착형은 버튼으로 충분해 nil).
    private var usageNote: L10n? {
        switch item.kind {
        case "shape": return .starlightUseShape
        case "color": return .starlightUseColor
        case "telescope": return item.str("mode") == "reveal" ? .starlightUseReveal : .starlightUseHint
        default: return nil
        }
    }

    private func toggle(_ slot: StarlightStore.Slot, _ equipped: Bool) {
        busy = true
        Task {
            let r = await store.equip(slot, equipped ? nil : item.id)
            busy = false
            switch r {
            case .done:
                if slot == .title { syncTitle(equipped ? nil : item.id) }
                tick += 1
                Haptics.medium()
                StaryAnalytics.log("workshop_equip", ["slot": slot.rawValue, "item": equipped ? "" : item.id])
                GlobalToast.shared.show(locale.t(equipped ? .starlightUnequipDone : .starlightEquipDone))
            case .failed(let code):
                GlobalToast.shared.show(locale.t(code == "timeout" ? .starlightErrTimeout : .starlightErrGeneric))
            }
        }
    }
}

// MARK: - 상자 열기 연출

/// draws = nil 이면 서버 응답을 기다리는 중(상자가 떨며 빛을 모은다).
private struct RevealState: Equatable {
    let count: Int
    var draws: [StarlightStore.Draw]?

    static func == (a: RevealState, b: RevealState) -> Bool { a.count == b.count && (a.draws == nil) == (b.draws == nil) }
}

private struct RevealOverlay: View {
    let state: RevealState
    let onDone: () -> Void
    @ObservedObject private var locale = LocaleManager.shared
    @State private var charge: CGFloat = 0
    @State private var chargedAt: Date?
    @State private var burst: CGFloat = 0
    @State private var showCards = false

    private var best: String? { state.draws?.max { rank($0.rarity) < rank($1.rarity) }?.rarity }

    var body: some View {
        let bestColor = best.map(StarlightArt.rarityColor) ?? StarlightArt.violet
        ZStack {
            Color(hex: 0x0A0C16).opacity(0.94).ignoresSafeArea()
                .onTapGesture { if showCards { onDone() } }
            // 터지는 빛
            Canvas { ctx, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let b = burst
                guard b > 0 else { return }
                let rr = min(size.width, size.height) * (0.2 + b * 0.9)
                ctx.fill(StarlightArt.circle(c, rr), with: .radialGradient(
                    Gradient(colors: [bestColor.opacity(0.55 * (1 - b * 0.6)), .clear]), center: c, startRadius: 0, endRadius: rr))
                let rays = best == "legendary" ? 16 : 10
                for i in 0..<rays {
                    let a = Double(i) * 2 * .pi / Double(rays)
                    let len = min(size.width, size.height) * (0.15 + b * 0.55)
                    var l = Path()
                    l.move(to: c)
                    l.addLine(to: CGPoint(x: c.x + cos(a) * len, y: c.y + sin(a) * len))
                    ctx.stroke(l, with: .color(bestColor.opacity(0.5 * (1 - b))), lineWidth: 3)
                }
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()

            if !showCards {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { tl in
                    let shake = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 0.18)
                    NebulaBoxArt(glow: Double(charge))
                        .frame(width: 180, height: 180)
                        .rotationEffect(.degrees(shake * 3 * charge))
                        .offset(x: shake * 6 * charge)
                        .scaleEffect(1 + 0.12 * charge - 0.5 * burst)
                        .opacity(1 - burst)
                }
                .allowsHitTesting(false)
            }
            if showCards, let draws = state.draws {
                ScrollView {
                    VStack(spacing: 10) {
                        if draws.count == 1 {
                            DrawCard(draw: draws[0], index: 0, big: true)
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                                ForEach(Array(draws.enumerated()), id: \.offset) { i, d in
                                    DrawCard(draw: d, index: i, big: false)
                                }
                            }
                        }
                        Text(locale.t(.starlightTapClose)).font(.minSans(12)).foregroundStyle(dim).padding(.top, 18)
                    }
                    .padding(.horizontal, 20).padding(.vertical, 40)
                    .frame(maxWidth: 520)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    .onTapGesture { onDone() }
                }
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.4)) { charge = 1 }
            chargedAt = Date().addingTimeInterval(1.4)
            if state.draws != nil { explode() }
        }
        .onChange(of: state) { s in if s.draws != nil { explode() } }
    }

    /// 빛을 다 모은 뒤에 터뜨린다(응답이 빨라도 최소 연출 시간은 지킨다).
    private func explode() {
        guard burst == 0, !showCards else { return }
        let wait = max(0, (chargedAt ?? Date()).timeIntervalSinceNow)
        DispatchQueue.main.asyncAfter(deadline: .now() + wait) {
            switch best {
            case "legendary":
                Haptics.celebrate()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { Haptics.celebrate() }
            case "epic": Haptics.celebrate()
            default: Haptics.medium()
            }
            withAnimation(.easeOut(duration: 0.52)) { burst = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.52) { showCards = true }
        }
    }
}

private struct DrawCard: View {
    let draw: StarlightStore.Draw
    let index: Int
    let big: Bool
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var visible = false
    @State private var pulse = false

    var body: some View {
        let item = store.item(draw.itemId)
        let c = StarlightArt.rarityColor(draw.rarity)
        let lang = locale.effectiveLanguage
        let shiny = draw.rarity == "legendary" || draw.rarity == "epic"
        VStack(spacing: 6) {
            Text(rarityName(draw.rarity)).font(.minSans(big ? 13 : 11, .semibold)).foregroundStyle(c)
            Group {
                if let item { ItemArt(item: item) } else { StardustIcon(tint: c).padding(big ? 35 : 18) }
            }
            .frame(width: big ? 140 : 72, height: big ? 140 : 72)
            Text(item?.name(in: lang) ?? draw.itemId)
                .font(.minSans(big ? 17 : 13, .semibold)).foregroundStyle(ink)
                .multilineTextAlignment(.center).lineLimit(2)
            if big, let item {
                Text(item.desc(in: lang)).font(.minSans(12.5)).foregroundStyle(sub)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            }
            Text(note(item))
                .font(.minSans(big ? 12 : 10.5, .medium))
                .foregroundStyle(draw.dup ? dim : StarlightArt.dust)
                .multilineTextAlignment(.center)
        }
        .padding(big ? 20 : 12)
        .frame(width: big ? 260 : nil)
        .frame(maxWidth: big ? nil : .infinity)
        .background(LinearGradient(colors: [c.opacity(0.16), Color(hex: 0x121821)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: big ? 24 : 18))
        .overlay(RoundedRectangle(cornerRadius: big ? 24 : 18)
            .strokeBorder(c.opacity(shiny ? (pulse ? 0.9 : 0.35) : 0.55), lineWidth: shiny ? 1.5 : 1))
        .scaleEffect(visible ? 1 : 0.6)
        .opacity(visible ? 1 : 0)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.11 * Double(index)) {
                withAnimation(.spring(response: 0.45, dampingFraction: 0.55)) { visible = true }
                if shiny { Haptics.medium() } else { Haptics.tick() }
            }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = true }
        }
    }

    private func note(_ item: StarlightStore.Item?) -> String {
        if draw.keys > 0 { return String(format: locale.t(.starlightKeysPlus), draw.keys) }
        if draw.dup { return String(format: locale.t(.starlightDupRefund), draw.refund) }
        if item?.kind == "telescope" { return locale.t(.starlightStacked) }
        return locale.t(.starlightNew)
    }
}
