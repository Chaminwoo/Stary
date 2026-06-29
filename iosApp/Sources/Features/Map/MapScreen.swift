import CoreLocation
import SwiftUI

/// 지도 탭 — 별 마커 + 탭 시 상세로 이동. "미조회만" 필터로 아직 열지 않은 별만 표시.
/// 멀리 있는 별을 누르면 도보 길찾기 경로를 지도에 그린다(OpenRouteService).
struct MapScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared
    @State private var selected: Diary?
    @State private var unviewedOnly = false
    @State private var route: [CLLocationCoordinate2D] = []
    @State private var routeInfo: String?

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
                onTapDiary: { handleTap($0) },
                route: route
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
                Label(locale.t(.filterUnviewed), systemImage: unviewedOnly ? "eye.slash.fill" : "eye")
                    .font(.caption.bold())
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(unviewedOnly ? Theme.mint.opacity(0.9) : Theme.surface.opacity(0.92), in: Capsule())
                    .foregroundStyle(unviewedOnly ? Color.black : Theme.textPrimary)
                    .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.4), lineWidth: 1))
                    .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
            }
            .padding(.top, 12)
            .padding(.trailing, 14)

            // 도보 길찾기 안내 칩(경로 있을 때, 하단) — 누르면 경로 지움.
            if !route.isEmpty {
                VStack {
                    Spacer()
                    Button {
                        route = []; routeInfo = nil
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "figure.walk")
                            Text(routeInfo ?? "")
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.textSecondary)
                        }
                        .font(.caption.bold())
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Theme.surface.opacity(0.95), in: Capsule())
                        .foregroundStyle(Theme.textPrimary)
                        .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.5), lineWidth: 1))
                        .shadow(color: .black.opacity(0.3), radius: 8, y: 3)
                    }
                    .padding(.bottom, 24)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .sheet(item: $selected) { diary in
            NavigationStack { DetailScreen(diary: diary) }
        }
    }

    /// 별 탭 — 100m 이내면 상세 열람, 밖이면 도보 경로 표시(Android DiaryMap 게이팅 패리티).
    private func handleTap(_ diary: Diary) {
        let me = location.coordinateOrDefault
        let dist = Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude,
                                      lat2: diary.latitude, lng2: diary.longitude)
        if dist <= AppConfig.diaryOpenRadiusM {
            route = []; routeInfo = nil
            selected = diary
        } else {
            Task {
                let end = CLLocationCoordinate2D(latitude: diary.latitude, longitude: diary.longitude)
                if let r = await OrsRouting.walkingRoute(start: me, end: end) {
                    route = r.coordinates
                    let mins = max(1, Int((r.durationS / 60).rounded()))
                    routeInfo = "도보 약 \(mins)분 · \(Int(r.distanceM))m"
                } else {
                    // ORS 키 미설정/실패 → 기존 동작(상세 진입, 거리 잠금은 DetailScreen)
                    selected = diary
                }
            }
        }
    }
}
