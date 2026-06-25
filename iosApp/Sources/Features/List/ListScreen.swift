import SwiftUI

/// 목록 탭 — 별 카드 리스트(거리순 정렬 옵션 포함).
struct ListScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @State private var nearbyFirst = false

    private var rows: [Diary] {
        guard nearbyFirst else { return store.diaries }
        let me = location.coordinateOrDefault
        return store.diaries.sorted {
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
                    ContentUnavailableCompat(text: "아직 별이 없어요. 첫 별을 남겨보세요.")
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
