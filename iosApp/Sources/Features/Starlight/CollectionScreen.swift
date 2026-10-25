import SwiftUI

private let ink = Color(hex: 0xF0F0F0)
private let sub = Color(hex: 0xB8C0CC)
private let dim = Color(hex: 0x7D8696)
private let card = Color.white.opacity(0.08)
private let line = Color.white.opacity(0.10)
private let gold = Color(hex: 0xFFD27A)

private let kindOrder = ["frame", "badge", "title", "aura", "shape", "color", "telescope"]

/// 수집품(2026-10-22) — 별빛 공방에서 얻은 칭호·별 모양·프레임 등을 **가진 것만** 모아 보고 장착한다.
/// 위: 지금 장착 중(프레임 두른 내 사진 · 이름+배지 · 칭호 · 오라), 아래: 종류 칩 + 가진 아이템 격자(누르면 상세 → 장착/해제).
/// Android `CollectionScreen.kt` 패리티.
struct CollectionScreen: View {
    @EnvironmentObject var auth: AuthManager
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var bgScroll = BackgroundScroll()
    @State private var kind: String?
    @State private var detail: StarlightStore.Item?
    /// 장착이 바뀌면 칭호(기기 사본)를 다시 읽게 하는 신호.
    @State private var tick = 0

    private var mine: [StarlightStore.Item] {
        store.items.filter { (store.owned[$0.id] ?? 0) > 0 }.sorted { a, b in
            let ka = kindOrder.firstIndex(of: a.kind) ?? 99, kb = kindOrder.firstIndex(of: b.kind) ?? 99
            if ka != kb { return ka < kb }
            let ra = StarlightStore.rarities.firstIndex(of: a.rarity) ?? 0, rb = StarlightStore.rarities.firstIndex(of: b.rarity) ?? 0
            if ra != rb { return ra > rb }
            return a.order < b.order
        }
    }

    var body: some View {
        let mine = self.mine
        var kinds = kindOrder.filter { k in mine.contains { $0.kind == k } }
        for k in mine.map(\.kind) where !kinds.contains(k) { kinds.append(k) }
        let shown = kind.map { k in mine.filter { $0.kind == k } } ?? mine
        return ZStack {
            ScreenBackground(name: "mydiary_bg", darken: 0.82, scroll: bgScroll)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    loadout
                    if mine.isEmpty {
                        Text(locale.t(.starlightCollectionEmpty))
                            .font(.minSans(13)).foregroundStyle(sub).lineSpacing(4)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity).padding(.vertical, 40)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                chip(locale.t(.starlightAll), mine.count, kind == nil) { kind = nil }
                                ForEach(kinds, id: \.self) { k in
                                    chip(starlightKindName(k), mine.filter { $0.kind == k }.count, kind == k) { kind = k }
                                }
                            }
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                            ForEach(shown) { item in
                                OwnedCell(item: item, tick: tick)
                                    .onTapGesture { withAnimation(.easeOut(duration: 0.18)) { detail = item } }
                            }
                        }
                    }
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
            }
            .backgroundScrollSource(bgScroll)

            if let item = detail {
                StarlightItemDetail(item: item, onChanged: { tick += 1 }) { detail = nil }
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .navigationTitle(locale.t(.navCollection))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { StaryAnalytics.log("collection_open", ["owned": mine.count]) }
    }

    private func chip(_ text: String, _ count: Int, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        StaryChip("\(text) \(count)", selected: selected, accent: StarlightArt.violet) {
            Haptics.tick()
            withAnimation(.easeOut(duration: 0.18)) { action() }
        }
    }

    // MARK: 지금 장착 중

    private var loadout: some View {
        let eq = store.myEquips
        let lang = locale.effectiveLanguage
        let frame = store.item(eq.frame)
        let badge = store.item(eq.badge)
        let aura = store.item(eq.aura)
        // 칭호는 업적 칭호와 한 자리 — 실제로 달린 값이 "sl:" 일 때만 공방 칭호.
        let title = tick >= 0 ? auth.uid.flatMap { store.titleItem(UserDefaults.standard.string(forKey: "equipped_title_\($0)")) } : nil
        let photo: CGFloat = 84 * (1 - 2 * StarlightArt.frameInset)
        return VStack(alignment: .leading, spacing: 12) {
            Text(locale.t(.starlightLoadout))
                .font(.minSans(11, .semibold)).tracking(1.2).foregroundStyle(gold)
            HStack(spacing: 14) {
                ZStack {
                    LoadoutPhoto(url: auth.photoUrl)
                        .frame(width: frame != nil ? photo : 84, height: frame != nil ? photo : 84)
                        .clipShape(Circle())
                    if let frame { FrameArt(item: frame).frame(width: 84, height: 84).allowsHitTesting(false) }
                }
                .frame(width: 84, height: 84)
                .onTapGesture { kind = "frame" }
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(auth.confirmedName.map { $0.isEmpty ? "Stargazer" : $0 } ?? "···")
                            .font(.minSans(17, .semibold)).foregroundStyle(ink).lineLimit(1)
                        EquippedBadge(equips: eq, size: 20)
                    }
                    if let title {
                        TitleText(text: "『\(title.name(in: lang))』", item: title, fontSize: 13)
                            .onTapGesture { kind = "title" }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.08))
                    if let aura {
                        AuraArt(item: aura)
                    } else {
                        Text(locale.t(.starlightKindAura)).font(.minSans(10)).foregroundStyle(dim).multilineTextAlignment(.center)
                    }
                }
                .frame(width: 64, height: 64)
                .onTapGesture { kind = "aura" }
            }
            // 칸 요약 — 무엇이 끼워져 있는지 / 비었는지
            HStack(spacing: 6) {
                slotPill("frame", frame, lang)
                slotPill("badge", badge, lang)
                slotPill("title", title, lang)
                slotPill("aura", aura, lang)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinearGradient(colors: [Color(hex: 0x221B3D), Color(hex: 0x131826)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(
            LinearGradient(colors: [gold.opacity(0.45), StarlightArt.violet.opacity(0.3)], startPoint: .topLeading, endPoint: .bottomTrailing),
            lineWidth: 1))
    }

    private func slotPill(_ k: String, _ item: StarlightStore.Item?, _ lang: String) -> some View {
        let c = item.map { StarlightArt.rarityColor($0.rarity) } ?? dim
        return VStack(spacing: 1) {
            Text(starlightKindName(k)).font(.minSans(10)).foregroundStyle(dim).lineLimit(1)
            Text(item?.name(in: lang) ?? locale.t(.starlightSlotEmpty))
                .font(.minSans(10, .medium)).foregroundStyle(item != nil ? ink : dim).lineLimit(1)
        }
        .padding(.horizontal, 6).padding(.vertical, 7)
        .frame(maxWidth: .infinity)
        .background(item != nil ? c.opacity(0.10) : Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(item != nil ? c.opacity(0.45) : line, lineWidth: 0.75))
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture { withAnimation(.easeOut(duration: 0.18)) { kind = k } }
    }
}

/// 내 사진(로그인 때 서버에서 확정한 값) — 없으면 기본 아이콘.
private struct LoadoutPhoto: View {
    let url: String?
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(hex: 0x0D0D0D)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().scaledToFit()
                    .foregroundStyle(Color(hex: 0x555555))
            }
        }
        .task(id: url) {
            guard let url, !url.isEmpty else { image = nil; return }
            image = await AvatarThumbCache.shared.image(for: url, maxPixel: 256)
        }
    }
}

private struct OwnedCell: View {
    let item: StarlightStore.Item
    let tick: Int
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        let equipped = tick >= 0 && workshopIsEquipped(item)
        let count = store.owned[item.id] ?? 0
        VStack(spacing: 4) {
            ItemArt(item: item)
                .aspectRatio(1, contentMode: .fit)
                .padding(6)
                .overlay(alignment: .topTrailing) {
                    if equipped {
                        Image(systemName: "checkmark")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(Color(hex: 0x111120))
                            .frame(width: 16, height: 16)
                            .background(c, in: Circle())
                    } else if item.kind == "telescope", count > 1 {
                        Text("×\(count)").font(.system(size: 11, weight: .bold)).foregroundStyle(c)
                    }
                }
            Text(item.name(in: locale.effectiveLanguage))
                .font(.minSans(11)).foregroundStyle(ink)
                .multilineTextAlignment(.center).lineLimit(2)
            Text(equipped ? locale.t(.starlightEquipped) : rarityName(item.rarity))
                .font(.minSans(10, .semibold)).foregroundStyle(c)
        }
        .padding(.vertical, 10).padding(.horizontal, 6)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [c.opacity(0.14), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(c.opacity(equipped ? 0.9 : 0.4), lineWidth: equipped ? 1.5 : 0.75))
        .contentShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// 아이템 상세 — 설명 · 가진 수 · 쓰는 곳 · 장착/해제. 수집품 화면이 쓴다(Android `ItemDetailDialog` 패리티).
struct StarlightItemDetail: View {
    let item: StarlightStore.Item
    var onChanged: () -> Void = {}
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
                // 희귀도 색은 그림 뒤 은은한 빛으로만(판을 물들이던 그라데이션·테두리 대신, 2026-10-26). Android 패리티.
                ItemArt(item: item).frame(width: 120, height: 120)
                    .background(Circle().fill(RadialGradient(colors: [c.opacity(0.24), .clear], center: .center,
                                                             startRadius: 0, endRadius: 60)))
                Text("\(rarityName(item.rarity)) · \(starlightKindName(item.kind))")
                    .font(.minSans(12, .semibold)).foregroundStyle(c).padding(.top, 10)
                Text(item.name(in: lang)).font(.minSans(17, .semibold)).foregroundStyle(ink)
                    .multilineTextAlignment(.center).padding(.top, 4)
                Text(item.desc(in: lang)).font(.minSans(13)).foregroundStyle(sub).lineSpacing(3)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 8)
                if count > 0 {
                    Text(String(format: locale.t(.starlightOwnedN), count)).font(.minSans(12)).foregroundStyle(c).padding(.top, 10)
                    if let note = usageNote {
                        Text(locale.t(note)).font(.minSans(11)).foregroundStyle(dim)
                            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
                    }
                    if let slot {
                        StaryButton(locale.t(equipped ? .starlightUnequip : .starlightEquip),
                                    kind: equipped ? .ghost : .primary, compact: true, loading: busy) {
                            toggle(slot, equipped)
                        }
                        .padding(.top, 14)
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: StaryDialogStyle.maxWidth)
            .glassPanel(cornerRadius: StaryDialogStyle.corner, tint: StaryDialogStyle.glassTint)
            .padding(.horizontal, StaryDialogStyle.screenPadding)
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
                if slot == .title { syncStarlightTitle(equipped ? nil : item.id) }
                tick += 1
                onChanged()
                Haptics.medium()
                StaryAnalytics.log("workshop_equip", ["slot": slot.rawValue, "item": equipped ? "" : item.id])
                GlobalToast.shared.show(locale.t(equipped ? .starlightUnequipDone : .starlightEquipDone))
            case .failed(let code):
                GlobalToast.shared.show(locale.t(code == "timeout" ? .starlightErrTimeout : .starlightErrGeneric))
            }
        }
    }
}

/// 서버가 users.equippedTitle 을 바꿨으니 기기 사본도 맞춘다 — 업적 화면·프로필이 이걸 읽는다.
@MainActor
func syncStarlightTitle(_ itemId: String?) {
    guard let uid = StarlightStore.shared.currentUid else { return }
    let key = "equipped_title_\(uid)"
    if let itemId {
        UserDefaults.standard.set(StarlightStore.titlePrefix + itemId, forKey: key)
    } else if UserDefaults.standard.string(forKey: key)?.hasPrefix(StarlightStore.titlePrefix) == true {
        UserDefaults.standard.removeObject(forKey: key)
    }
}
