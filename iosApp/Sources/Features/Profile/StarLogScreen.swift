import CoreLocation
import SwiftUI
import UIKit

// ─── 별 도감 — 해금한(열어 본) 다른 사람의 별을 화면 중앙의 원 위에 모아 보기 (2026-09-25) ───
// Android `feature/profile/screen/StarLogScreen.kt` 이식. 수치(StarRingFx)는 Android RING_* 와 동일 — 값 drift 금지.
//  · 들어오면 12시부터 시계 방향으로 별이 하나씩 빠르게 떠올라 약 1.5초 만에 원이 완성(별마다 "톡톡 반짝").
//  · 누른 채로 별 위를 지나가면 그 별이 커지고 원 가운데에 제목·작성자·지도 버튼(손을 떼도 유지).
//    글씨는 그 별의 색 + 같은 색 후광.
//  · "지도에서 보기" → MapFocusStore(루트가 NavigationStack 을 비우고 카메라 + 파장) — 프로필 핀 별과 같은 로직.
//  · 정렬(해금순/최신순/거리순/인기순) · 필터(친구만/친구 선택) — 바꿀 때마다 원을 다시 그린다.
// 데이터 = DiaryUnlockStore(기기 로컬 영구 해금) ∩ 지금 존재하는 글(삭제·비공개·차단 제외). 내 글은 넣지 않는다.

/// 원 위 배치 순서(12시부터 시계 방향). Android StarLogSort 패리티.
enum StarLogSort: CaseIterable {
    case unlocked, latest, distance, popular

    var label: L10n {
        switch self {
        case .unlocked: return .starlogSortUnlocked
        case .latest: return .sortLatest
        case .distance: return .sortDistance
        case .popular: return .sortPopular
        }
    }
}

/// Android StarLogScreen RING_* 상수와 같은 값.
private enum StarRingFx {
    static let revealMs = 1500.0
    static let popMs = 260.0
    static let maxGapMs = 120.0
    static let radiusFrac: CGFloat = 0.40
    static let starMin: CGFloat = 12
    static let starMax: CGFloat = 34
    static let selectScale: CGFloat = 1.45
    static let hitMin: CGFloat = 26
    /// 고른 별이 커지는 데 걸리는 시간(초) — Android 는 프레임마다 90ms 시정수로 따라간다.
    static let hoverSec = 0.18

    static func appearMs(_ i: Int, _ n: Int) -> Double {
        guard n > 1 else { return 0 }
        return Double(i) * min(maxGapMs, (revealMs - popMs) / Double(n - 1))
    }

    /// 톡 떠오르는 크기 곡선 — 0.3 → 1.2 → 1.
    static func pop(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        if u < 0.6 { let k = 1 - u / 0.6; return 0.3 + 0.9 * (1 - k * k) }
        return 1.2 - 0.2 * ((u - 0.6) / 0.4)
    }
}

struct StarLogScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var blocks: BlockStore
    @ObservedObject private var locale = LocaleManager.shared
    @ObservedObject private var unlocks = DiaryUnlockStore.shared
    @ObservedObject private var directory = UserDirectory.shared

    @State private var sort: StarLogSort = .unlocked
    @State private var friendsOnly = false
    @State private var selectedFriendIds: Set<String> = []
    @State private var showFriendPicker = false
    @State private var myFriends: [Friend] = []
    @State private var selectedId: String?
    /// 직전에 고른 별(작아지는 전환용) + 고른 시각.
    @State private var previousId: String?
    @State private var selectedAt = Date.distantPast
    @State private var revealStart = Date()
    /// 거리순 기준점 — 정렬을 고른 순간의 위치로 고정(걷는 동안 원이 계속 재배열되지 않게).
    @State private var anchor: CLLocationCoordinate2D?

    private var here: CLLocationCoordinate2D { location.coordinateOrDefault }
    private var friendIds: Set<String> { Set(myFriends.map { $0.userId }) }

    private var collected: [Diary] {
        let uid = auth.uid
        let fids = friendIds
        return store.diaries.filter { d in
            guard let id = d.id, unlocks.unlockedAt[id] != nil else { return false }
            return d.userId != uid && !blocks.blockedIds.contains(d.userId)
                && d.visibilityType != "private"
                && (d.visibilityType != "friends" || fids.contains(d.userId))
        }
    }

    private var shown: [Diary] {
        let fids = friendIds
        let list = collected.filter {
            (!friendsOnly || fids.contains($0.userId))
                && (selectedFriendIds.isEmpty || selectedFriendIds.contains($0.userId))
        }
        switch sort {
        case .unlocked:
            return list.sorted { (unlocks.unlockedAt[$0.id ?? ""] ?? 0) > (unlocks.unlockedAt[$1.id ?? ""] ?? 0) }
        case .latest: return list.sorted { $0.createdAt > $1.createdAt }
        case .popular: return list.sorted { $0.likeCount > $1.likeCount }
        case .distance:
            let a = anchor ?? here
            return list.sorted {
                Geo.distanceMeters(lat1: a.latitude, lng1: a.longitude, lat2: $0.latitude, lng2: $0.longitude)
                    < Geo.distanceMeters(lat1: a.latitude, lng1: a.longitude, lat2: $1.latitude, lng2: $1.longitude)
            }
        }
    }

    /// 원을 다시 그리는 기준 — 정렬/필터가 바뀌거나 빈 목록이 채워질 때.
    private var revealToken: Int {
        var h = Hasher()
        h.combine(sort); h.combine(friendsOnly); h.combine(selectedFriendIds); h.combine(shown.isEmpty)
        return h.finalize()
    }

    var body: some View {
        ZStack {
            ScreenBackground(name: "mydiary_bg", darken: 0.86)
            if auth.uid == nil {
                Text(locale.t(.commonLoginRequired))
                    .font(.minSans(18))
                    .foregroundStyle(Theme.textSecondary)
            } else if collected.isEmpty {
                StaryEmptyState(title: locale.t(.starlogEmptyTitle), description: locale.t(.starlogEmptyDesc))
            } else {
                VStack(spacing: 0) {
                    sortRow.padding(.top, 12)
                    ZStack {
                        if shown.isEmpty {
                            StaryEmptyState(title: locale.t(.starlogFilteredEmpty))
                        } else {
                            ring
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    filterRow.padding(.bottom, 20)
                }
            }
        }
        .navigationTitle(locale.t(.navStarLog))
        .navigationBarTitleDisplayMode(.inline)
        .firstVisitInfo(key: "starlog", systemImage: "star.circle.fill",
                        title: locale.t(.onbStarLogTitle),
                        message: locale.t(.onbStarLogMsg))
        .sheet(isPresented: $showFriendPicker) {
            FriendFilterPicker(friends: myFriends, initial: selectedFriendIds) { ids in
                selectedFriendIds = ids
            }
        }
        .task(id: auth.uid) {
            guard let uid = auth.uid else { myFriends = []; return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            myFriends = snap?.documents.compactMap { try? $0.data(as: Friend.self) } ?? []
        }
        .onChange(of: sort) { s in anchor = s == .distance ? here : nil }
        // 원 등장 — 기준 시각을 다시 잡고, 별이 떠오르는 순간마다 "톡톡 반짝"(간격은 MusicManager 가 솎는다).
        .task(id: revealToken) {
            revealStart = Date()
            let n = shown.count
            let start = revealStart
            for i in 0..<n {
                let wait = StarRingFx.appearMs(i, n) / 1000 - Date().timeIntervalSince(start)
                if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) }
                if Task.isCancelled { return }
                MusicManager.shared.playSparkTick()
            }
        }
    }

    // MARK: 정렬 / 필터 알약

    private var sortRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(StarLogSort.allCases, id: \.self) { s in
                    chip(locale.t(s.label), active: sort == s) { sort = s }
                }
            }
            .padding(.horizontal, 16)
        }
        .frame(height: 40)
    }

    private var filterRow: some View {
        HStack(spacing: 8) {
            chip(locale.t(.filterFriends), icon: "person.2", active: friendsOnly) { friendsOnly.toggle() }
            chip(selectedFriendIds.isEmpty
                 ? locale.t(.filterPickFriends)
                 : String(format: locale.t(.filterFriendsN), selectedFriendIds.count),
                 icon: "person.badge.plus", active: !selectedFriendIds.isEmpty) {
                if selectedFriendIds.isEmpty { showFriendPicker = true } else { selectedFriendIds = [] }
            }
        }
    }

    private func chip(_ label: String, icon: String? = nil, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 13))
                        .foregroundStyle(active ? Theme.navyAccent : Color.white)
                }
                Text(label)
                    .font(.minSans(13))
                    .foregroundStyle(active ? Color(hex: 0xDCE5FF) : Theme.textPrimary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(active ? Theme.navyAccent.opacity(0.24) : Color(hex: 0x111120).opacity(0.93),
                        in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 0.75))
        }
        .buttonStyle(.plain)
    }

    // MARK: 별의 원

    private struct RingLayout {
        let center: CGPoint
        let radius: CGFloat
        let starPx: CGFloat
        let positions: [CGPoint]
    }

    private func layout(_ size: CGSize, count n: Int) -> RingLayout {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = max(80, min(size.width * StarRingFx.radiusFrac, size.height / 2 - StarRingFx.starMax / 2 - 8))
        let starPx = min(max(2 * .pi * radius / CGFloat(max(n, 1)) * 0.62, StarRingFx.starMin), StarRingFx.starMax)
        let positions = (0..<n).map { i -> CGPoint in
            let a = -Double.pi / 2 + 2 * Double.pi * Double(i) / Double(n)
            return CGPoint(x: center.x + CGFloat(cos(a)) * radius, y: center.y + CGFloat(sin(a)) * radius)
        }
        return RingLayout(center: center, radius: radius, starPx: starPx, positions: positions)
    }

    private var ring: some View {
        let stars = shown
        return GeometryReader { geo in
            let lay = layout(geo.size, count: stars.count)
            ZStack {
                TimelineView(.animation) { tl in
                    Canvas { ctx, _ in
                        drawRing(ctx: ctx, lay: lay, stars: stars, now: tl.date)
                    }
                }
                // 누른 채로 별 위를 지나가면 그 별이 선택된다(손을 떼도 유지) — 가장 가까운 별 하나.
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in pick(v.location, lay: lay, stars: stars) }
                )

                centerPanel(stars: stars)
                    .frame(width: max(120, (lay.radius - lay.starPx * 1.3) * 2))
                    .position(lay.center)
            }
        }
    }

    private func pick(_ p: CGPoint, lay: RingLayout, stars: [Diary]) {
        var best = -1
        var bestD = CGFloat.greatestFiniteMagnitude
        for (i, q) in lay.positions.enumerated() {
            let d = hypot(q.x - p.x, q.y - p.y)
            if d < bestD { bestD = d; best = i }
        }
        let hit = max(lay.starPx * 0.8, StarRingFx.hitMin)
        guard best >= 0, bestD <= hit, let id = stars[best].id, id != selectedId else { return }
        previousId = selectedId
        selectedId = id
        selectedAt = Date()
        Haptics.tick()
    }

    private func hoverValue(_ id: String, now: Date) -> Double {
        let k = min(1, max(0, now.timeIntervalSince(selectedAt) / StarRingFx.hoverSec))
        let e = 1 - (1 - k) * (1 - k)
        if id == selectedId { return e }
        if id == previousId { return 1 - e }
        return 0
    }

    private func drawRing(ctx: GraphicsContext, lay: RingLayout, stars: [Diary], now: Date) {
        let elapsed = now.timeIntervalSince(revealStart) * 1000
        let n = stars.count

        // 1) 원 가이드 — 드러나는 동안 12시부터 시계 방향으로 쓸고, 완성 뒤엔 아주 옅게 남는다.
        let sweep = min(max(elapsed / StarRingFx.revealMs, 0), 1)
        var guide = Path()
        guide.addArc(center: lay.center, radius: lay.radius,
                     startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * sweep), clockwise: false)
        ctx.stroke(guide, with: .color(.white.opacity(0.07)), lineWidth: 1)
        if sweep < 1, n > 0 {
            let ha = (-90 + 360 * sweep) * .pi / 180
            let head = CGPoint(x: lay.center.x + CGFloat(cos(ha)) * lay.radius,
                               y: lay.center.y + CGFloat(sin(ha)) * lay.radius)
            let hr: CGFloat = 16
            ctx.fill(Path(ellipseIn: CGRect(x: head.x - hr, y: head.y - hr, width: hr * 2, height: hr * 2)),
                     with: .radialGradient(Gradient(colors: [Theme.mint.opacity(0.45), .clear]),
                                           center: head, startRadius: 0, endRadius: hr))
        }

        // 2) 별 — 톡 떠오르기 + 은은한 반짝임 + 고른 별 확대·후광
        let t = now.timeIntervalSinceReferenceDate
        for (i, d) in stars.enumerated() {
            let u = min(max((elapsed - StarRingFx.appearMs(i, n)) / StarRingFx.popMs, 0), 1)
            guard u > 0 else { continue }
            let pop = StarRingFx.pop(u)
            let alpha = min(1, u / 0.5)
            let hv = hoverValue(d.id ?? "", now: now)
            let scale = CGFloat(pop) * (1 + (StarRingFx.selectScale - 1) * CGFloat(hv))
            let c = StarStyle.color(d.starColor)
            let tw = 0.5 + 0.5 * sin(t * (1.3 + Double(i % 5) * 0.17) + Double(i) * 1.7)
            let p = lay.positions[i]

            let glowR = lay.starPx * (0.95 + 0.6 * CGFloat(hv)) * scale
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - glowR, y: p.y - glowR, width: glowR * 2, height: glowR * 2)),
                     with: .radialGradient(Gradient(colors: [c.opacity((0.20 + 0.12 * tw + 0.38 * hv) * alpha), .clear]),
                                           center: p, startRadius: 0, endRadius: glowR))
            // StarImageRenderer 는 본체가 캔버스의 78% — 본체 지름이 starPx 가 되도록 키워 그린다.
            let sz = lay.starPx * scale / 0.78
            let img = StarImageRenderer.image(type: d.starType, colorIndex: d.starColor, size: 64)
            var layer = ctx
            layer.opacity = alpha
            layer.draw(Image(uiImage: img), in: CGRect(x: p.x - sz / 2, y: p.y - sz / 2, width: sz, height: sz))
            if hv > 0.01 {
                let rr = lay.starPx * scale * 0.8
                ctx.stroke(Path(ellipseIn: CGRect(x: p.x - rr, y: p.y - rr, width: rr * 2, height: rr * 2)),
                           with: .color(c.opacity(0.55 * hv)), lineWidth: 1.2)
            }
        }
    }

    // MARK: 원 가운데

    @ViewBuilder
    private func centerPanel(stars: [Diary]) -> some View {
        ZStack {
            if let d = stars.first(where: { $0.id != nil && $0.id == selectedId }) {
                infoPanel(d)
                    .id(d.id)
                    .transition(.opacity.combined(with: .scale(scale: 0.95)))
            } else {
                countPanel(stars.count)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.22), value: selectedId)
    }

    private func countPanel(_ count: Int) -> some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.minSans(44, .light))
                .foregroundStyle(Theme.textPrimary)
                .shadow(color: Theme.mint.opacity(0.55), radius: 13)
            Text(locale.t(.starlogCountLabel))
                .font(.minSans(13))
                .foregroundStyle(Theme.textSecondary)
            Text(locale.t(.starlogHint))
                .font(.minSans(12))
                .foregroundStyle(Theme.textSecondary.opacity(0.8))
                .multilineTextAlignment(.center)
                .padding(.top, 10)
        }
    }

    /// 별 색을 글씨로 쓸 때 — 어두운 별(흑백 그라데이션 등)도 읽히도록 밝기를 끌어올린다(Android readableOn 동일).
    private func readable(_ c: Color) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        func lin(_ v: CGFloat) -> CGFloat { v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        let lum = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        let k: CGFloat = lum < 0.35 ? 0.55 : 0.18
        return Color(red: Double(r + (1 - r) * k), green: Double(g + (1 - g) * k), blue: Double(b + (1 - b) * k))
    }

    private func infoPanel(_ d: Diary) -> some View {
        let starColor = StarStyle.color(d.starColor)
        let ink = readable(starColor)
        let meters = Geo.distanceMeters(lat1: here.latitude, lng1: here.longitude, lat2: d.latitude, lng2: d.longitude)
        let dist = meters >= 1000 ? String(format: "%.1fkm", meters / 1000) : "\(Int(meters.rounded()))m"
        let meta: String = {
            guard let ms = unlocks.unlockedAt[d.id ?? ""] else {
                return String(format: locale.t(.starlogMetaDistance), dist)
            }
            let f = DateFormatter()
            f.locale = locale.swiftLocale
            f.dateStyle = .medium
            f.timeStyle = .none
            let date = f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
            return String(format: locale.t(.starlogMeta), date, dist)
        }()
        let author = directory.name(d.userId, fallback: d.userName)
        return VStack(spacing: 0) {
            Text(d.title.isEmpty ? "—" : d.title)
                .font(.minSans(20, .semibold))
                .foregroundStyle(ink)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .shadow(color: starColor.opacity(0.9), radius: 13)
            Text(String(format: locale.t(.starlogAuthor), author))
                .font(.minSans(13))
                .foregroundStyle(ink.opacity(0.85))
                .lineLimit(1)
                .shadow(color: starColor.opacity(0.6), radius: 8)
                .padding(.top, 6)
            Text(meta)
                .font(.minSans(11))
                .foregroundStyle(Theme.textSecondary)
                .lineLimit(1)
                .padding(.top, 4)
            Button {
                if let id = d.id { MapFocusStore.shared.request(diaryId: id) }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "map.fill").font(.system(size: 13))
                    Text(locale.t(.starlogOpenMap)).font(.minSans(13))
                }
                .foregroundStyle(ink)
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(starColor.opacity(0.14), in: Capsule())
                .overlay(Capsule().stroke(starColor.opacity(0.6), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.top, 14)
        }
    }
}
