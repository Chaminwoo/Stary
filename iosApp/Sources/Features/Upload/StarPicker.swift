import SwiftUI

/// 별 고르기 "가진 것만 보기" 저장 키 — 한 번 켜 두면 기억한다(Android `StarPickerPrefs` 패리티).
enum StarPickerPrefs {
    static let ownedOnlyKey = "upload_owned_only"
}

/// 별 모양/색 무한 회전 휠 — 고른 항목이 항상 정중앙(별빛 링), 끝까지 돌리면 처음 항목이 다시 나온다.
/// - 빠르게 밀면 손을 뗀 뒤에도 몇 칸 더 미끄러지다 멈춘다(관성). 천천히 끌면 놓은 자리에서 가장 가까운 칸으로.
/// - 바깥에서 `selection` 이 바뀌면(모두 보기 시트·이어 쓰기) 그 칸까지 굴러간다.
/// - 칸이 적어도(가진 것만 보기) 같은 별을 두 번 그리지 않는다 — 항목마다 가운데에서 가장 가까운 한 자리에만 둔다.
/// - 스냅이 끝난 뒤 selection 을 한 번에 갱신(좌표 불연속 방지). 잠긴 항목도 가운데 멈추며, 저장 차단은 호출부 몫.
/// Android `StarWheelPicker`(StarPicker.kt) 패리티.
struct WheelPicker<Content: View>: View {
    let count: Int
    @Binding var selection: Int
    let itemSize: CGFloat
    var isLocked: (Int) -> Bool = { _ in false }
    var onLocked: (Int) -> Void = { _ in }
    /// false 면 드래그/탭 모두 막는다(업로드 진행 중 값 변경 방지) — Android StarWheelPicker(enabled) 패리티.
    /// `.disabled()` 만으로는 커스텀 DragGesture/onTapGesture 가 계속 반응하므로 allowsHitTesting 으로 막는다.
    var enabled: Bool = true
    @ViewBuilder let item: (Int) -> Content

    /// 슬롯 간격 — 가운데 양옆으로 두세 칸이 보이게.
    private var slot: CGFloat { itemSize + 28 }
    @State private var drag: CGFloat = 0
    @State private var settling = false
    /// 드래그 중 슬롯을 지날 때마다 딸깍(햅틱) — 중복 방지용 마지막 눈금.
    @State private var tickedStep = 0
    /// 그려지는 가운데 칸 — 휠이 스스로 돌 때도, 바깥에서 바뀔 때도 이 칸에서 출발해 굴러간다(nil = 첫 그림 전).
    @State private var shown: Int?
    @State private var shownCount = 0
    /// 손가락 속도(pt/s) — iOS 16 의 DragGesture 에는 속도가 없어 마지막 움직임들로 잰다.
    @State private var lastSample: (time: Date, x: CGFloat)?
    @State private var velocity: CGFloat = 0

    /// 1~2칸이면 돌지 않고 양 끝에서 멈춘다.
    private var wraps: Bool { count >= 3 }
    private var center: Int { min(max(shown ?? selection, 0), max(count - 1, 0)) }
    private func wrap(_ i: Int) -> Int { ((i % count) + count) % count }

    var body: some View {
        ZStack {
            // 가운데 강조 링(선택 자리).
            Circle()
                .stroke(Theme.navyAccent.opacity(0.9), lineWidth: 2)
                .frame(width: itemSize + 18, height: itemSize + 18)

            WheelStrip(drag: drag, count: count, center: center, slot: slot, wraps: wraps, item: item) { off in
                guard !settling else { return }
                commit(steps: off)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: itemSize * 1.7)
        .contentShape(Rectangle())
        .clipped()
        .gesture(
            DragGesture()
                .onChanged { g in
                    guard !settling else { return }
                    drag = g.translation.width
                    sample(g)
                    let step = Int((-drag / slot).rounded())
                    if step != tickedStep {
                        tickedStep = step
                        Haptics.tick()
                    }
                }
                .onEnded { g in
                    guard !settling else { return }
                    fling(g)
                }
        )
        .opacity(enabled ? 1 : 0.5)
        .allowsHitTesting(enabled)
        .onAppear {
            if shown == nil { shown = selection; shownCount = count }
        }
        // 칸 수가 바뀌면(가진 것만 보기·새로 얻은 별) 지금 고른 칸에서 다시 시작한다.
        .onChange(of: count) { _ in
            shown = selection; shownCount = count; drag = 0
        }
        .onChange(of: selection) { follow($0) }
    }

    /// 바깥(모두 보기 시트·이어 쓰기)에서 바뀐 칸까지 짧은 쪽으로 굴러간다. 휠이 스스로 바꾼 값이면 shown 이 이미 같다.
    /// 굴러가는 중에 바뀌면 그 굴림이 끝난 뒤 다시 부른다(commit).
    private func follow(_ newValue: Int) {
        guard !settling else { return }
        guard shownCount == count, let from = shown else {
            shown = newValue; shownCount = count
            return
        }
        guard newValue != from else { return }
        var d = newValue - from
        if wraps {
            d = wrap(d)
            if d > count / 2 { d -= count }
        }
        commit(steps: d, duration: min(0.26 + 0.03 * Double(abs(d)), 0.62), notify: false)
    }

    private func sample(_ g: DragGesture.Value) {
        if let s = lastSample {
            let dt = g.time.timeIntervalSince(s.time)
            guard dt > 0.008 else { return }
            let v = (g.translation.width - s.x) / CGFloat(dt)
            velocity = velocity == 0 ? v : velocity * 0.4 + v * 0.6
        }
        lastSample = (g.time, g.translation.width)
    }

    /// 손을 뗀 속도로 멈출 자리를 미리 계산 — Android `exponentialDecay(1.8)` 와 같은 식(이동 = 속도 / (4.2 × 1.8)).
    /// 천천히 놓으면 지금 자리에서 가장 가까운 칸과 같다.
    private func fling(_ g: DragGesture.Value) {
        let fresh = lastSample.map { g.time.timeIntervalSince($0.time) < 0.1 } ?? false
        let v: CGFloat = fresh ? velocity : 0
        lastSample = nil
        velocity = 0
        let here = Int((-g.translation.width / slot).rounded())
        let target: CGFloat = g.translation.width + v / (4.2 * 1.8)
        let limit = min(count, 12)
        let steps = min(max(Int((-target / slot).rounded()), -limit), limit)
        let glide = abs(steps - here)
        commit(steps: steps, duration: glide >= 2 ? min(0.20 + 0.045 * Double(glide), 0.72) : 0.24)
    }

    /// 지금 자리에서 steps 칸 떨어진 항목이 **놓은 자리에서 그대로** 가운데 오도록 굴린 뒤 한 번에 갱신한다
    /// (좌표가 같아 되돌아가는 현상이 없다). 반 칸을 못 넘긴 드래그(steps=0)도 반드시 원위치로(#2).
    private func commit(steps rawSteps: Int, duration: Double = 0.24, notify: Bool = true) {
        let from = center
        let steps = wraps ? rawSteps : min(max(from + rawSteps, 0), count - 1) - from
        guard steps != 0 else {
            tickedStep = 0
            if drag != 0 { withAnimation(.easeOut(duration: 0.18)) { drag = 0 } }
            return
        }
        settling = true
        let target = wrap(from + steps)
        let end = -CGFloat(steps) * slot
        let layout = shownCount
        scheduleTicks(from: drag, to: end, duration: duration)
        withAnimation(.easeOut(duration: duration)) { drag = end }
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            tickedStep = 0
            settling = false
            // 굴러가는 사이 칸 수가 바뀌었으면(가진 것만 보기) 이미 새 목록 기준으로 다시 섰다 — 이 결과는 버린다.
            guard shownCount == layout else { return }
            shown = target      // off=steps 였던 항목이 이제 off=0
            drag = 0            // 좌표 동일 → 시각적 점프 없음
            if notify {
                selection = target
                if isLocked(target) { onLocked(target) }
            } else if selection != target {
                follow(selection)
            }
        }
    }

    /// 굴러가며 지나는 칸마다 딸깍 — 애니메이션 중 값을 매 프레임 받을 수 없어 감속 곡선(easeOut ≈ 1-(1-t)²)에 맞춰 미리 예약한다.
    private func scheduleTicks(from start: CGFloat, to end: CGFloat, duration: Double) {
        let p0 = Double(-start / slot)
        let p1 = Double(-end / slot)
        guard abs(p1 - p0) >= 0.5 else { return }
        let up = p1 > p0
        var h = up ? (p0 + 0.5).rounded(.down) + 0.5 : (p0 - 0.5).rounded(.up) - 0.5
        var n = 0
        while (up ? h < p1 : h > p1), n < 12 {
            let f = (h - p0) / (p1 - p0)
            let t = 1 - (1 - f).squareRoot()
            DispatchQueue.main.asyncAfter(deadline: .now() + duration * t) { Haptics.tick() }
            h += up ? 1 : -1
            n += 1
        }
    }
}

/// 휠의 별 줄(그리기 전용). `Animatable` 이라 `drag` 가 애니메이션으로 바뀌는 동안 **매 프레임** 다시 그려진다 —
/// 그냥 두면 SwiftUI 가 끝 값으로 한 번에 배치해, 여러 칸을 미끄러질 때 별이 굴러가지 않고 제자리에서 바뀌어 보인다.
private struct WheelStrip<Content: View>: View, Animatable {
    var drag: CGFloat
    let count: Int
    let center: Int
    let slot: CGFloat
    let wraps: Bool
    let item: (Int) -> Content
    let onTap: (Int) -> Void

    var animatableData: CGFloat {
        get { drag }
        set { drag = newValue }
    }

    private struct Placed {
        let idx: Int
        let off: Int
    }

    /// 지금 보이는 항목과 그 자리 — 항목마다 가운데에서 가장 가까운 한 자리.
    private func placedItems() -> [Placed] {
        guard count > 0 else { return [] }
        let c = Double(-drag / slot)
        var out: [Placed] = []
        for idx in 0..<count {
            let base = idx - center
            var off = base
            if wraps {
                let turns = ((c - Double(base)) / Double(count)).rounded()
                off = base + count * Int(turns)
            }
            if abs(Double(off) - c) <= 3.5 { out.append(Placed(idx: idx, off: off)) }
        }
        return out
    }

    /// 칸이 적으면 반대편으로 넘어가는 자리(count/2)에서 사라졌다 나타나게 — 툭 튀어 보이지 않게.
    private func edgeFade(_ slotsFromCenter: CGFloat) -> Double {
        guard wraps else { return 1 }
        let v = (Double(count) / 2 - Double(slotsFromCenter)) / 0.6
        return min(max(v, 0), 1)
    }

    var body: some View {
        ZStack {
            ForEach(placedItems(), id: \.idx) { p in
                let x: CGFloat = CGFloat(p.off) * slot + drag
                let dist: CGFloat = abs(x)
                let scale: CGFloat = max(0.55, 1.25 - dist / slot * 0.35)
                item(p.idx)
                    .scaleEffect(scale)
                    .opacity(Double(max(0.3, 1 - dist / (slot * 2.2))) * edgeFade(dist / slot))
                    .offset(x: x)
                    .onTapGesture { onTap(p.off) }
            }
        }
    }
}

/// "가진 것만 보기" — 휠 아래와 모두 보기 시트 머리에 같은 모양으로 둔다(Android `OwnedOnlyToggle`).
struct OwnedOnlyToggle: View {
    @Binding var isOn: Bool

    var body: some View {
        Button { isOn.toggle() } label: {
            HStack(spacing: 6) {
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 15))
                    .foregroundStyle(isOn ? Theme.navyAccent : Theme.textSecondary)
                Text(LocaleManager.shared.t(.uploadOwnedOnly))
                    .font(.minSans(13))
                    .foregroundStyle(isOn ? Theme.textPrimary : Theme.textSecondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// '모두 보기' 시트 — 휠로 하나씩 넘기지 않고 모양/색을 한눈에 고른다(휠은 그대로 둔다).
/// 묶음: 기본 · 업적 보상 · 별빛 공방. 모양 칸은 지금 고른 색으로, 색 칸은 지금 고른 모양으로 그려 실제 조합을 바로 본다.
/// 잠긴 칸을 누르면 아래에 필요한 업적과 조건이 나오고, 열린 칸을 고르면 시트가 닫히며 휠이 그 칸까지 굴러간다.
/// 화면 안 오버레이(업로드 본문 위) — 유리는 `.ultraThinMaterial`. Android `StarPickerSheet` 패리티.
struct StarPickerSheet: View {
    @Binding var isPresented: Bool
    @Binding var tab: Int
    let starType: Int
    let starColor: Int
    /// 휠과 같은 전체 목록(가진 것만 거르기 전).
    let shapeIds: [Int]
    let colorIds: [Int]
    let unlocked: Set<String>
    @Binding var ownedOnly: Bool
    let onPickShape: (Int) -> Void
    let onPickColor: (Int) -> Void

    /// 마지막으로 누른 잠긴 칸 — 아래 안내 줄에 조건을 띄운다(탭을 바꾸면 지운다).
    @State private var lockedPick: Int?
    @State private var lockedAch: Achievement?

    var body: some View {
        ZStack(alignment: .bottom) {
            if isPresented {
                Color.black.opacity(0.45)
                    .ignoresSafeArea()
                    .onTapGesture { isPresented = false }
                    .transition(.opacity)
                panel
                    .transition(.move(edge: .bottom))
            }
        }
        // 닫혀 있어도 화면 크기를 유지해야 판이 아래 끝에서 올라온다(빈 곳은 터치를 막지 않는다).
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .animation(.easeOut(duration: 0.26), value: isPresented)
        .onChange(of: tab) { _ in
            lockedPick = nil
            lockedAch = nil
        }
        .onChange(of: isPresented) { open in
            if !open { lockedPick = nil; lockedAch = nil }
        }
    }

    private var panel: some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: 24, bottomLeadingRadius: 0,
                                           bottomTrailingRadius: 0, topTrailingRadius: 24, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            Capsule()
                .fill(Color.white.opacity(0.22))
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
            HStack {
                StarySegmented(options: [(LocaleManager.shared.t(.pickerTabShape), nil),
                                         (LocaleManager.shared.t(.pickerTabColor), nil)],
                               selection: $tab)
                    .frame(width: 164)
                Spacer()
                OwnedOnlyToggle(isOn: $ownedOnly)
            }
            .padding(.top, 14)
            .padding(.bottom, 4)
            // 다 들어가면 그대로, 넘치면 스크롤.
            ViewThatFits(in: .vertical) {
                groups
                ScrollView(showsIndicators: false) { groups }
            }
            if let ach = lockedAch {
                lockedInfo(ach)
                    .transition(.opacity)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity)
        .frame(maxHeight: UIScreen.main.bounds.height * 0.72)
        .background {
            ZStack {
                shape.fill(.ultraThinMaterial)
                shape.fill(StaryDialogStyle.glassTint)
            }
            .overlay(shape.stroke(Theme.glassEdge, lineWidth: 1))
            .ignoresSafeArea(edges: .bottom)
        }
    }

    private struct Groups {
        let basic: [Int]
        let rewards: [Int]
        let rewardsShown: [Int]
        let rewardsOwned: Int
        let workshop: [Int]
    }

    private func makeGroups() -> Groups {
        let shapes = tab == 0
        let builtIn = shapes ? StarStyle.typeCount : StarStyle.colorCount
        let ids = shapes ? shapeIds : colorIds
        let table = shapes ? StarUnlocks.shape : StarUnlocks.color
        let basic = ids.filter { $0 < builtIn && table[$0] == nil }
        let rewards = ids.filter { $0 < builtIn && table[$0] != nil }
        let owned = rewards.filter { lock($0) == nil }
        return Groups(basic: basic, rewards: rewards, rewardsShown: ownedOnly ? owned : rewards,
                      rewardsOwned: owned.count, workshop: ids.filter { $0 >= builtIn })
    }

    private var groups: some View {
        let g = makeGroups()
        return VStack(alignment: .leading, spacing: 0) {
            group(LocaleManager.shared.t(.pickerGroupBasic), "\(g.basic.count)", g.basic)
            group(LocaleManager.shared.t(.pickerGroupAchievement), "\(g.rewardsOwned)/\(g.rewards.count)", g.rewardsShown)
            group(LocaleManager.shared.t(.achVoyageSection), "\(g.workshop.count)", g.workshop)
        }
        .padding(.bottom, 12)
    }

    @ViewBuilder private func group(_ label: String, _ count: String, _ items: [Int]) -> some View {
        if !items.isEmpty {
            HStack(spacing: 6) {
                Text(label).font(.minSans(12, .medium)).foregroundStyle(Theme.textSecondary)
                Text(count).font(.minSans(12)).foregroundStyle(Theme.textSecondary.opacity(0.6))
            }
            .padding(.top, 16)
            .padding(.bottom, 4)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 60), spacing: 0)], spacing: 0) {
                ForEach(items, id: \.self) { id in cell(id) }
            }
        }
    }

    private func lock(_ id: Int) -> Achievement? {
        tab == 0 ? StarUnlocks.lockedShapeAch(id, unlocked) : StarUnlocks.lockedColorAch(id, unlocked)
    }

    private func cell(_ id: Int) -> some View {
        let shapes = tab == 0
        let ach = lock(id)
        let selected = id == (shapes ? starType : starColor)
        let ring: Color? = selected ? Theme.navyAccent.opacity(0.9) : (lockedPick == id ? Color.white.opacity(0.3) : nil)
        return ZStack {
            if let ring {
                Circle().stroke(ring, lineWidth: 1.5).frame(width: 46, height: 46)
            }
            StarView(type: shapes ? id : starType, colorIndex: shapes ? starColor : id, size: 30, glow: false)
                .opacity(ach == nil ? 1 : 0.28)
            if ach != nil {
                Image(systemName: "lock.fill")
                    .font(.system(size: 9))
                    .foregroundStyle(Theme.textSecondary)
                    .offset(x: 14, y: 14)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 60)
        .contentShape(Rectangle())
        .onTapGesture {
            if let ach {
                Haptics.tick()
                withAnimation(.easeOut(duration: 0.2)) {
                    lockedPick = id
                    lockedAch = ach
                }
            } else {
                if shapes { onPickShape(id) } else { onPickColor(id) }
                isPresented = false
            }
        }
    }

    /// 잠긴 칸 안내 — 어떤 업적을 달성하면 열리는지.
    private func lockedInfo(_ ach: Achievement) -> some View {
        let name = LocalizedNames.title(ach.id, fallback: ach.name) ?? ach.name
        return VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(Theme.outline.opacity(0.6)).frame(height: 1)
            HStack(spacing: 10) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.navyAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(String(format: LocaleManager.shared.t(.pickerLockedHint), name))
                        .font(.minSans(13, .medium))
                        .foregroundStyle(Theme.textPrimary)
                    Text(ach.hidden ? "???" : LocalizedNames.condition(ach.id, fallback: ach.condition))
                        .font(.minSans(12))
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            .padding(.vertical, 12)
        }
    }
}
