import SwiftUI

/// 잠든 우주 수집품(2026-10-26 — 옛 "수집품" 화면을 업적 › 특별로 옮겼다). 잠든 우주를 깨워 찾은 것을 모아 보고,
/// 프레임·배지·칭호는 눌러서 장착한다. 별 고리는 별을 올릴 때 별 색 아래에서 고르고(그 별에만), 모양·색은 업로드 고르기에,
/// 망원경은 업적 화면에서 쓴다. 업적 화면 특별 탭이 `OwnedCell` · `StarlightItemDetail` 을 늘어놓는다(2026-10-27 — "지금 장착 중" 요약은 뺐다).
/// Android `VoyageFinds.kt` 패리티.

/// 종류 순서(장착형 먼저).
private let findKindOrder = ["frame", "badge", "title", "aura", "shape", "color", "telescope"]

/// 가진 아이템 — 종류 순서 → 희귀한 것부터 → 풀 순서.
@MainActor
func ownedFinds() -> [StarlightStore.Item] {
    let store = StarlightStore.shared
    return store.items.filter { (store.owned[$0.id] ?? 0) > 0 }.sorted { a, b in
        let ka = findKindOrder.firstIndex(of: a.kind) ?? 99, kb = findKindOrder.firstIndex(of: b.kind) ?? 99
        if ka != kb { return ka < kb }
        let ra = StarlightStore.rarities.firstIndex(of: a.rarity) ?? 0, rb = StarlightStore.rarities.firstIndex(of: b.rarity) ?? 0
        if ra != rb { return ra > rb }
        return a.order < b.order
    }
}

/// 가진 것들의 종류(칩 순서).
func findKinds(_ finds: [StarlightStore.Item]) -> [String] {
    var kinds = findKindOrder.filter { k in finds.contains { $0.kind == k } }
    for k in finds.map(\.kind) where !kinds.contains(k) { kinds.append(k) }
    return kinds
}

/// 장착 중인가. 칭호는 업적 칭호와 한 자리를 나눠 쓰므로 실제로 달려 있는 값(equippedTitle = users.equippedTitle 의 기기 사본)이
/// "sl:{id}" 일 때만 — 탐사 칭호를 단 뒤 업적 칭호로 바꾸면 starlightEquips.title 은 옛값으로 남는다.
@MainActor
func starlightIsEquipped(_ item: StarlightStore.Item, equippedTitle: String?) -> Bool {
    guard let slot = StarlightStore.Slot.of(kind: item.kind) else { return false }
    if slot == .title { return equippedTitle == StarlightStore.titlePrefix + item.id }
    return StarlightStore.shared.myEquips.of(slot) == item.id
}

/// 서버가 users.equippedTitle 을 바꿨으니 기기 사본도 맞춘다 — 업적 화면·프로필이 이걸 읽는다. 바뀐 값을 돌려준다.
@MainActor
@discardableResult
func syncStarlightTitle(_ itemId: String?) -> String? {
    guard let uid = StarlightStore.shared.currentUid else { return nil }
    let key = "equipped_title_\(uid)"
    if let itemId {
        UserDefaults.standard.set(StarlightStore.titlePrefix + itemId, forKey: key)
    } else if UserDefaults.standard.string(forKey: key)?.hasPrefix(StarlightStore.titlePrefix) == true {
        UserDefaults.standard.removeObject(forKey: key)
    }
    return UserDefaults.standard.string(forKey: key)
}

// MARK: - 가진 아이템 한 칸

/// 등급 빛 바탕, 장착 중이면 체크, 망원경은 남은 개수.
struct OwnedCell: View {
    let item: StarlightStore.Item
    let equippedTitle: String?
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        let equipped = starlightIsEquipped(item, equippedTitle: equippedTitle)
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
                .font(.minSans(11)).foregroundStyle(VoyageColor.ink)
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

// MARK: - 아이템 상세

/// 그림 · 등급·종류 · 이름 · 설명 · 가진 수 · 쓰는 곳, 장착형이면 장착/해제 버튼(Android `ItemDetailDialog` 패리티).
/// 칭호를 바꾸면 onTitleChanged 로 새 칭호 id 를 알린다(업적 화면의 칭호 행이 같은 자리를 쓴다).
struct StarlightItemDetail: View {
    let item: StarlightStore.Item
    let equippedTitle: String?
    var onTitleChanged: (String?) -> Void = { _ in }
    let onDismiss: () -> Void
    @ObservedObject private var store = StarlightStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var busy = false

    var body: some View {
        let c = StarlightArt.rarityColor(item.rarity)
        let lang = locale.effectiveLanguage
        let count = store.owned[item.id] ?? 0
        let slot = StarlightStore.Slot.of(kind: item.kind)
        let equipped = starlightIsEquipped(item, equippedTitle: equippedTitle)
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea().onTapGesture { if !busy { onDismiss() } }
            VStack(spacing: 0) {
                // 희귀도 색은 그림 뒤 은은한 빛으로만(판은 다른 팝업과 같은 유리).
                ItemArt(item: item).frame(width: 120, height: 120)
                    .background(Circle().fill(RadialGradient(colors: [c.opacity(0.24), .clear], center: .center,
                                                             startRadius: 0, endRadius: 60)))
                Text("\(rarityName(item.rarity)) · \(starlightKindName(item.kind))")
                    .font(.minSans(12, .semibold)).foregroundStyle(c).padding(.top, 10)
                Text(item.name(in: lang)).font(.minSans(17, .semibold)).foregroundStyle(VoyageColor.ink)
                    .multilineTextAlignment(.center).padding(.top, 4)
                Text(item.desc(in: lang)).font(.minSans(13)).foregroundStyle(VoyageColor.sub).lineSpacing(3)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 8)
                if count > 0 {
                    Text(String(format: locale.t(.starlightOwnedN), count)).font(.minSans(12)).foregroundStyle(c).padding(.top, 10)
                    if let note = usageNote {
                        Text(locale.t(note)).font(.minSans(11)).foregroundStyle(VoyageColor.dim).lineSpacing(2)
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
            .onTapGesture { if !busy { onDismiss() } }
        }
    }

    /// 가진 아이템을 어디서 쓰는지(장착형은 버튼으로 충분해 nil).
    private var usageNote: L10n? {
        switch item.kind {
        case "aura": return .starlightUseRing
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
                if slot == .title {
                    onTitleChanged(syncStarlightTitle(equipped ? nil : item.id))
                    if !equipped { QuestStore.report(.equipTitle) }
                }
                Haptics.medium()
                StaryAnalytics.log("workshop_equip", ["slot": slot.rawValue, "item": equipped ? "" : item.id])
                GlobalToast.shared.show(locale.t(equipped ? .starlightUnequipDone : .starlightEquipDone))
            case .failed(let code):
                GlobalToast.shared.show(locale.t(code == "timeout" ? .starlightErrTimeout : .starlightErrGeneric))
            }
        }
    }
}
