import SwiftUI

/// 목록 탭 — 별 카드 리스트(거리순 정렬 옵션 포함).
struct ListScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var viewed: ViewedStore
    @EnvironmentObject var blocks: BlockStore
    @ObservedObject private var locale = LocaleManager.shared
    @State private var nearbyFirst = false
    @State private var unviewedOnly = false
    // 기간별 보기 — nil=전체 기간, 0=오늘(자정 이후), 그 외 N=최근 N일. (Android 기간 필터 패리티)
    @State private var periodDays: Int?

    private var periodCutoffMs: Int64? {
        guard let d = periodDays else { return nil }
        if d == 0 {
            return Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
        }
        return Int64((Date().timeIntervalSince1970 - Double(d) * 86_400) * 1000)
    }

    private func periodName(_ d: Int?) -> String {
        switch d {
        case 0: return locale.t(.periodToday)
        case 7: return locale.t(.periodWeek)
        case 30: return locale.t(.periodMonth)
        case 365: return locale.t(.periodYear)
        default: return locale.t(.periodAll)
        }
    }

    private var rows: [Diary] {
        // 차단한 사용자의 별은 숨긴다. (Android MainListScreen 패리티)
        var list = store.diaries.filter { !blocks.blockedIds.contains($0.userId) }
        if unviewedOnly { list = list.filter { !viewed.viewedIds.contains($0.id ?? "") } }
        if let cutoff = periodCutoffMs { list = list.filter { $0.createdAt >= cutoff } }
        guard nearbyFirst else { return list }
        let me = location.coordinateOrDefault
        return list.sorted {
            Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude, lat2: $0.latitude, lng2: $0.longitude)
                < Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude, lat2: $1.latitude, lng2: $1.longitude)
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                if store.loading {
                    ProgressView().tint(Theme.mint)
                } else if rows.isEmpty {
                    ContentUnavailableCompat(text: unviewedOnly ? "안 본 별이 없어요. 모두 둘러봤네요!" : "아직 별이 없어요. 첫 별을 남겨보세요.")
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(rows) { diary in
                                NavigationLink(value: diary) {
                                    DiaryCard(diary: diary, distance: distance(to: diary))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .navigationTitle("별 목록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        unviewedOnly.toggle()
                    } label: {
                        Label(locale.t(.filterUnviewed), systemImage: unviewedOnly ? "eye.slash.fill" : "eye")
                    }
                    .tint(unviewedOnly ? Theme.mint : Theme.textSecondary)
                }
                // 기간별 보기 — 전체/오늘/7일/30일/1년
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        Button(locale.t(.periodAll)) { periodDays = nil }
                        Button(locale.t(.periodToday)) { periodDays = 0 }
                        Button(locale.t(.periodWeek)) { periodDays = 7 }
                        Button(locale.t(.periodMonth)) { periodDays = 30 }
                        Button(locale.t(.periodYear)) { periodDays = 365 }
                    } label: {
                        Label(periodDays == nil ? locale.t(.filterPeriod) : periodName(periodDays),
                              systemImage: "clock")
                    }
                    .tint(periodDays != nil ? Theme.mint : Theme.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(nearbyFirst ? "가까운순" : "최신순") { nearbyFirst.toggle() }
                        .tint(Theme.mint)
                }
            }
            .navigationDestination(for: Diary.self) { DetailScreen(diary: $0) }
        }
    }

    private func distance(to diary: Diary) -> Double {
        let me = location.coordinateOrDefault
        return Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude, lat2: diary.latitude, lng2: diary.longitude)
    }
}

/// 별 카드 — 별 아이콘 + 제목/요약 + 거리.
struct DiaryCard: View {
    let diary: Diary
    var distance: Double?

    var body: some View {
        HStack(spacing: 14) {
            StarView(type: diary.starType, colorIndex: diary.starColor, size: 36)
            VStack(alignment: .leading, spacing: 4) {
                Text(diary.title.isEmpty ? "(제목 없음)" : diary.title)
                    .font(.headline)
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                Text(diary.isAnonymous ? "익명" : diary.userName)
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            if !diary.imageUrl.isEmpty {
                AsyncImage(url: URL(string: diary.imageUrl)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Theme.surfaceAlt
                }
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            if let distance {
                Text(distanceLabel(distance))
                    .font(.caption2)
                    .foregroundStyle(Theme.textFaint)
            }
        }
        .padding(14)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private func distanceLabel(_ m: Double) -> String {
        m < 1000 ? "\(Int(m))m" : String(format: "%.1fkm", m / 1000)
    }
}

/// iOS 16 호환 빈 상태 표시.
struct ContentUnavailableCompat: View {
    let text: String
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "sparkles")
                .font(.largeTitle)
                .foregroundStyle(Theme.textFaint)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }
}
