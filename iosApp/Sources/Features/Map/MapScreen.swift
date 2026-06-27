import SwiftUI

/// 지도 탭 — 별 마커 + 탭 시 상세로 이동.
struct MapScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @State private var selected: Diary?

    var body: some View {
        ZStack {
            MapLibreView(
                diaries: store.diaries,
                userLocation: location.coordinate,
                onTapDiary: { selected = $0 }
            )
            .ignoresSafeArea()

            if store.loading {
                ProgressView().tint(Theme.mint)
            }
        }
        .sheet(item: $selected) { diary in
            NavigationStack { DetailScreen(diary: diary) }
        }
    }
}
