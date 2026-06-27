import SwiftUI

/// 지도 탭 — 별 마커 + 탭 시 상세로 이동. "미조회만" 필터로 아직 열지 않은 별만 표시.
struct MapScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var viewed: ViewedStore
    @State private var selected: Diary?
    @State private var unviewedOnly = false

    /// 미조회 필터 적용된 표시 대상.
    private var shownDiaries: [Diary] {
        guard unviewedOnly else { return store.diaries }
        return store.diaries.filter { !viewed.viewedIds.contains($0.id ?? "") }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            MapLibreView(
                diaries: shownDiaries,
                userLocation: location.coordinate,
                onTapDiary: { selected = $0 }
            )
            .ignoresSafeArea()

            if store.loading {
                ProgressView().tint(Theme.mint)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // 미조회만 필터 칩
            Button {
                unviewedOnly.toggle()
            } label: {
                Label("미조회만", systemImage: unviewedOnly ? "eye.slash.fill" : "eye")
                    .font(.caption.bold())
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(unviewedOnly ? Theme.mint.opacity(0.9) : Theme.surface.opacity(0.92), in: Capsule())
                    .foregroundStyle(unviewedOnly ? Color.black : Theme.textPrimary)
                    .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.4), lineWidth: 1))
                    .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
            }
            .padding(.top, 12)
            .padding(.trailing, 14)
        }
        .sheet(item: $selected) { diary in
            NavigationStack { DetailScreen(diary: diary) }
        }
    }
}
