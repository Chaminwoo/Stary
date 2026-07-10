import CoreLocation
import SwiftUI

/// 지도 탭 — 별 마커 + 탭 시 상세로 이동. "미조회만" 필터로 아직 열지 않은 별만 표시.
///
/// 길찾기: 별을 직접 클릭하는 게 아니라, 타인 프로필의 별 목록에서 "길찾기"를 누르면
/// (또는 프로필의 핀한 별 탭) `MapFocusStore` 로 요청이 들어와 지도 탭으로 전환되고,
/// 여기서 그 좌표로 카메라를 옮긴 뒤 현위치→목적지 도보 경로를 띄운다.
/// 실시간 위치에 맞춰 "최근접점→목적지" 구간만 렌더(지나온 길 숨김) — Android DiaryMap 패리티.
struct MapScreen: View {
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared
    @ObservedObject private var focus = MapFocusStore.shared
    @State private var selected: Diary?
    @State private var unviewedOnly = false
    // 개척 퀘스트(체크리스트 32) — 개척 현황 구독 + 비콘 탭 안내.
    @StateObject private var pioneer = PioneerStore()
    @State private var pioneerMessage: String?
    // 기간별 보기 — nil=전체 기간, 0=오늘(자정 이후), 그 외 N=최근 N일. (Android 기간 필터 패리티)
    @State private var periodDays: Int?

    // 도보 길찾기 상태.
    @State private var focusTarget: CLLocationCoordinate2D?
    @State private var fullRoute: [CLLocationCoordinate2D] = []   // 처음 받은 전체 경로(X 취소까지 유지)
    @State private var routeSummary: String?

    // 별 포커스 "파동" 연출 — 화면 중앙(포커스된 별)에서 물결이 퍼진 뒤 길찾기가 뜬다.
    @State private var showWarp = false
    @State private var warpColor: Color = .white
    @State private var warpId = 0

    // 3D 행성(글로브) 뷰 상태 — 줌을 충분히 빼면 하단 버튼이 뜨고, 눌러야 진입.
    @State private var globeCenter: GlobeCenter?
    @State private var globeReturn: GlobeReturnCamera?
    // 줌이 낮을 때 지도에서 보고되는 "지구 보기" 후보 중심(nil = 버튼 숨김).
    @State private var globeButtonCenter: GlobeCenter?
    // 지도 ↔ 글로브 교체를 가리는 검정 디졸브 스크림.
    @State private var globeScrim: Double = 0

    private struct GlobeCenter: Equatable {
        let lat: Double
        let lng: Double
    }

    /// 기간 컷오프(epoch ms) — 오늘=로컬 자정, 그 외=지금-N일.
    private var periodCutoffMs: Int64? {
        guard let d = periodDays else { return nil }
        if d == 0 {
            return Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
        }
        return Int64((Date().timeIntervalSince1970 - Double(d) * 86_400) * 1000)
    }

    /// 미조회/기간 필터 적용된 표시 대상.
    private var shownDiaries: [Diary] {
        var list = store.diaries
        if unviewedOnly { list = list.filter { !viewed.viewedIds.contains($0.id ?? "") } }
        if let cutoff = periodCutoffMs { list = list.filter { $0.createdAt >= cutoff } }
        return list
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

    /// 기간 칩 라벨(활성=민트 배경).
    private var periodChipLabel: some View {
        Label(
            periodDays == nil ? locale.t(.filterPeriod) : periodName(periodDays),
            systemImage: "clock"
        )
        .font(.caption.bold())
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background(periodDays != nil ? Theme.mint.opacity(0.9) : Theme.surface.opacity(0.92), in: Capsule())
        .foregroundStyle(periodDays != nil ? Color.black : Theme.textPrimary)
        .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.4), lineWidth: 1))
        .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
    }

    /// 실시간 위치 기준으로 "최근접점→목적지"만 남긴 경로(지나온 구간 제외).
    private var partialRoute: [CLLocationCoordinate2D] {
        guard fullRoute.count >= 2 else { return [] }
        guard let me = location.coordinate else { return fullRoute }
        return MapScreen.partialRouteFrom(full: fullRoute, me: me)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            MapLibreView(
                diaries: shownDiaries,
                userLocation: location.coordinate,
                onTapDiary: { selected = $0 },
                route: partialRoute,
                focusTarget: focusTarget,
                onGlobeAvailability: { lat, lng, available in
                    globeButtonCenter = available ? GlobeCenter(lat: lat, lng: lng) : nil
                },
                globeReturnCamera: globeReturn,
                pioneerCountries: pioneer.featured,
                onTapPioneer: { code in pioneerMessage = LocalizedNames.pioneerQuestMessage(code) }
            )
            .ignoresSafeArea()

            if store.loading {
                ProgressView().tint(Theme.mint)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // 별 포커스 파동 — 매 요청마다 .id 로 다시 그려 애니메이션 재생.
            if showWarp {
                MapWarpOverlay(color: warpColor)
                    .id(warpId)
                    .allowsHitTesting(false)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // 필터 칩 — 미조회만 + 기간별 보기
            VStack(alignment: .trailing, spacing: 8) {
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
                // 기간별 보기 — 비활성이면 메뉴(오늘/7일/30일/1년), 활성이면 재탭으로 해제.
                if periodDays != nil {
                    Button { periodDays = nil } label: { periodChipLabel }
                } else {
                    Menu {
                        Button(locale.t(.periodToday)) { periodDays = 0 }
                        Button(locale.t(.periodWeek)) { periodDays = 7 }
                        Button(locale.t(.periodMonth)) { periodDays = 30 }
                        Button(locale.t(.periodYear)) { periodDays = 365 }
                    } label: { periodChipLabel }
                }
            }
            .padding(.top, 12)
            .padding(.trailing, 14)

            // 길찾기 활성 시: 하단 요약 + 취소(X)
            if !fullRoute.isEmpty {
                routeControls
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }

            // ── 하단 "지구 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
            if let entry = globeButtonCenter, globeCenter == nil {
                Button {
                    enterGlobe(lat: entry.lat, lng: entry.lng)
                } label: {
                    Label(locale.t(.globeOpen), systemImage: "globe.asia.australia.fill")
                        .font(.caption.bold())
                        .padding(.horizontal, 18).padding(.vertical, 11)
                        .background(Theme.surface.opacity(0.93), in: Capsule())
                        .foregroundStyle(Theme.textPrimary)
                        .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.5), lineWidth: 1))
                        .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 30)
            }

            // ── 3D 행성(글로브) 오버레이 — 버튼으로 진입, 하단 탭 → X 버튼으로 그 지점 지도 복귀 ──
            if let center = globeCenter {
                GlobeScreen(
                    diaries: shownDiaries,
                    startLat: center.lat,
                    startLng: center.lng,
                    onRequestExit: { lat, lng in exitGlobe(lat: lat, lng: lng) }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea()
            }
            // 전환 스크림(검정 디졸브) — 지도 ↔ 글로브(SceneKit) 교체를 가린다.
            if globeScrim > 0.001 {
                Color.black.opacity(globeScrim)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
        .sheet(item: $selected) { diary in
            NavigationStack { DetailScreen(diary: diary) }
        }
        // 이미 지도 탭일 때(핀/친구 길찾기로 값이 바뀌면) 즉시 처리.
        .onChange(of: focus.pendingDiaryId) { id in
            handleFocus(id)
        }
        // 다른 탭에서 길찾기 요청 → 지도 탭으로 전환되며 나타날 때 처리(숨김 동안 onChange 미수신 대비).
        .onAppear {
            handleFocus(focus.pendingDiaryId)
            pioneer.start() // 개척 퀘스트 현황 구독(체크리스트 32)
        }
        // 개척 비콘 탭 → 퀘스트 안내(체크리스트 32)
        .alert(pioneerMessage ?? "", isPresented: Binding(
            get: { pioneerMessage != nil },
            set: { if !$0 { pioneerMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }

    /// 하단 길찾기 컨트롤 — 요약 칩 + 취소 버튼.
    private var routeControls: some View {
        VStack(spacing: 10) {
            if let s = routeSummary {
                Text(s)
                    .font(.caption.bold())
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Theme.surface.opacity(0.95), in: Capsule())
                    .foregroundStyle(Color(red: 0.525, green: 0.937, blue: 0.675))
                    .overlay(Capsule().strokeBorder(Color(red: 0.525, green: 0.937, blue: 0.675).opacity(0.5), lineWidth: 1))
            }
            Button {
                cancelRoute()
            } label: {
                Image(systemName: "xmark")
                    .font(.title3.bold())
                    .frame(width: 52, height: 52)
                    .background(Color(hex: 0x0E1520), in: Circle())
                    .foregroundStyle(Color(red: 0.525, green: 0.937, blue: 0.675))
            }
            .accessibilityLabel(locale.t(.routeCancel))
        }
        .padding(.bottom, 30)
    }

    /// 포커스 요청 처리 — 대상 별 좌표로 카메라 이동 → 파동(물결) → (요청 시) 도보 길찾기.
    private func handleFocus(_ id: String?) {
        guard let id, let d = store.diaries.first(where: { $0.id == id }) else { return }
        selected = nil // 열려 있던 상세 시트는 닫고 지도로
        let dest = CLLocationCoordinate2D(latitude: d.latitude, longitude: d.longitude)
        let wantRoute = focus.withRoute
        focusTarget = dest

        // 파동 연출 1회 재생(다이어리에서 클릭한 느낌).
        warpColor = StarStyle.color(d.starColor)
        warpId += 1
        showWarp = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { showWarp = false }

        if wantRoute {
            // 파동이 먼저 퍼진 뒤 경로가 뜨도록 살짝 지연 후 요청.
            Task {
                try? await Task.sleep(nanoseconds: 650_000_000)
                await fetchRoute(to: dest)
            }
        } else {
            fullRoute = []
            routeSummary = nil
        }
        focus.consume()
    }

    /// 현위치→목적지 도보 경로 요청(OpenRouteService). 키 미설정/실패 시 조용히 무시.
    private func fetchRoute(to dest: CLLocationCoordinate2D) async {
        guard let me = location.coordinate else { return }
        guard let route = await OrsRouting.walkingRoute(start: me, end: dest) else { return }
        fullRoute = route.coordinates
        let mins = max(1, Int((route.durationS / 60).rounded()))
        routeSummary = "\(mins)\(locale.t(.routeMinSuffix)) · \(Int(route.distanceM))m"
    }

    private func cancelRoute() {
        fullRoute = []
        routeSummary = nil
    }

    // MARK: 3D 글로브 전환 (Android MainListScreen 글로브 라운드 패리티)

    /// 지도 줌아웃 → 글로브 진입. 검정 디졸브로 지도 ↔ SceneKit 교체를 가린다.
    private func enterGlobe(lat: Double, lng: Double) {
        guard globeCenter == nil else { return }
        withAnimation(.easeInOut(duration: 0.17)) { globeScrim = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            globeCenter = GlobeCenter(lat: lat, lng: lng)
            withAnimation(.easeInOut(duration: 0.52)) { globeScrim = 0 }
        }
    }

    /// 글로브 핀치-인 → 지금 정면 지점의 지도(줌 4)로 복귀.
    private func exitGlobe(lat: Double, lng: Double) {
        withAnimation(.easeInOut(duration: 0.17)) { globeScrim = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            globeReturn = GlobeReturnCamera(lat: lat, lng: lng, zoom: 4.0,
                                            nonce: (globeReturn?.nonce ?? 0) + 1)
            globeCenter = nil
            // 지도 카메라 점프가 프레임에 반영될 시간을 살짝 준 뒤 걷는다.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.07) {
                withAnimation(.easeInOut(duration: 0.38)) { globeScrim = 0 }
            }
        }
    }

    /// 전체 경로 [full] 에서 현재 위치 [me] 의 최근접 투영점을 찾아
    /// "[me] → 최근접점 → 그 이후 ~ 목적지" 좌표열을 만든다(지나온 구간 제외).
    /// 위경도를 경도 cos(위도) 보정 평면으로 근사. (Android DiaryMap.partialRouteFrom 패리티)
    static func partialRouteFrom(full: [CLLocationCoordinate2D], me: CLLocationCoordinate2D) -> [CLLocationCoordinate2D] {
        guard full.count >= 2 else { return full }
        let kx = cos(me.latitude * .pi / 180)
        func px(_ p: CLLocationCoordinate2D) -> Double { p.longitude * kx }
        func py(_ p: CLLocationCoordinate2D) -> Double { p.latitude }
        let mx = px(me), my = py(me)
        var bestK = 0, bestT = 0.0, bestD = Double.greatestFiniteMagnitude
        for i in 0..<(full.count - 1) {
            let ax = px(full[i]), ay = py(full[i])
            let bx = px(full[i + 1]), by = py(full[i + 1])
            let dx = bx - ax, dy = by - ay
            let len2 = dx * dx + dy * dy
            let t = len2 < 1e-12 ? 0.0 : min(max(((mx - ax) * dx + (my - ay) * dy) / len2, 0.0), 1.0)
            let cx = ax + t * dx, cy = ay + t * dy
            let d = (mx - cx) * (mx - cx) + (my - cy) * (my - cy)
            if d < bestD { bestD = d; bestK = i; bestT = t }
        }
        let a = full[bestK], b = full[bestK + 1]
        let cLng = a.longitude + bestT * (b.longitude - a.longitude)
        let cLat = a.latitude + bestT * (b.latitude - a.latitude)
        var out: [CLLocationCoordinate2D] = [me, CLLocationCoordinate2D(latitude: cLat, longitude: cLng)]
        if bestK + 1 < full.count {
            out.append(contentsOf: full[(bestK + 1)...])
        }
        return out
    }
}

/// 별 포커스 "파동" 연출 — 화면 중앙(카메라가 별을 중앙에 둔 상태)에서 동심원 물결이 퍼진다.
/// (Android `DiaryOpenWarp`(지도 스냅샷 메시 왜곡)의 iOS 간이판 — 링 파동 + 중앙 발광.)
private struct MapWarpOverlay: View {
    let color: Color
    @State private var progress: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            let maxR = max(geo.size.width, geo.size.height) * 0.72
            ZStack {
                // 중앙 발광(퍼지며 옅어짐).
                Circle()
                    .fill(RadialGradient(colors: [color.opacity(0.55 * Double(1 - progress)), .clear],
                                         center: .center, startRadius: 0, endRadius: 120))
                    .frame(width: 260, height: 260)
                    .scaleEffect(0.4 + progress * 1.7)
                    .position(center)
                // 퍼지는 링 3겹(시차).
                ForEach(0..<3, id: \.self) { i in
                    let p = max(0, progress - CGFloat(i) * 0.18)
                    Circle()
                        .stroke(color.opacity(0.9 * Double(1 - p)), lineWidth: 3)
                        .frame(width: maxR * 2 * p, height: maxR * 2 * p)
                        .position(center)
                }
            }
        }
        .onAppear {
            progress = 0
            withAnimation(.easeOut(duration: 1.0)) { progress = 1 }
        }
    }
}
