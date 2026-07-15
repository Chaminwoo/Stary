import CoreLocation
import FirebaseFirestore
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
    /// 30m 안에서 겹친 별 무리(2개 이상) — 카드 뷰어 시트로 연다.
    @State private var cluster: ClusterSelection?
    @State private var unviewedOnly = false
    // 개척 퀘스트(체크리스트 32) — 개척 현황 구독 + 비콘 탭 안내.
    @StateObject private var pioneer = PioneerStore()
    @State private var pioneerMessage: String?
    // 기간별 보기 — nil=전체 기간, 0=오늘(자정 이후), 그 외 N=최근 N일. (Android 기간 필터 패리티)
    @State private var periodDays: Int?
    // 친구만/나만보기/친구선택 필터(Android MainListScreen 필터 패리티).
    @EnvironmentObject var auth: AuthManager
    @State private var friendsOnly = false
    @State private var myOnly = false
    @State private var selectedFriendIds: Set<String> = []
    @State private var showFriendPicker = false
    /// 내 친구 목록 — 친구만/친구선택 필터용 1회 로드.
    @State private var myFriends: [Friend] = []
    // 좌하단 필터 스피드 다이얼 펼침(Android speedDialExpanded 대응) + 기간 선택 다이얼로그.
    @State private var speedDialExpanded = false
    @State private var showPeriodPicker = false
    // 줌 +/− / 내 위치 버튼 → MapLibreView 커맨드(nonce 로 반복 요청 허용).
    @State private var zoomRequest: (delta: Double, nonce: Int) = (0, 0)
    @State private var recenterNonce = 0

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

    /// 미조회/친구/나만/친구선택/기간 필터 적용된 표시 대상. (Android MainListScreen 필터 파이프라인 대응)
    private var shownDiaries: [Diary] {
        var list = store.diaries
        if unviewedOnly { list = list.filter { !viewed.viewedIds.contains($0.id ?? "") } }
        if friendsOnly {
            let ids = Set(myFriends.map { $0.userId })
            list = list.filter { ids.contains($0.userId) }
        }
        if myOnly { list = list.filter { $0.userId == auth.uid } }
        if !selectedFriendIds.isEmpty { list = list.filter { selectedFriendIds.contains($0.userId) } }
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

    /// 지도 위 원형 버튼 공통(Android FloatingActionButton 0x1A1A1A 대응).
    private func mapCircleButton(_ icon: String, size: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size * 0.42, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Color(hex: 0x1A1A1A), in: Circle())
                .shadow(color: .black.opacity(0.3), radius: 5, y: 2)
        }
    }

    /// 필터가 하나라도 켜져 있는가(메인 FAB 민트 강조 — Android anyActive).
    private var anyFilterActive: Bool {
        unviewedOnly || friendsOnly || myOnly || !selectedFriendIds.isEmpty || periodDays != nil
    }

    /// 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응.
    /// 메인 원형 버튼(나침반) 탭 → 위로 알약 옵션(전체보기/미조회만/친구만/나만보기/친구선택/기간별).
    /// 상호배타 로직도 Android 동일(나만보기 ↔ 친구만/친구선택 등).
    private var filterSpeedDial: some View {
        VStack(alignment: .leading, spacing: 8) {
            if speedDialExpanded {
                filterPill(locale.t(.filterAll), icon: "globe.asia.australia", active: !anyFilterActive) {
                    unviewedOnly = false; friendsOnly = false; myOnly = false
                    selectedFriendIds = []; periodDays = nil; speedDialExpanded = false
                }
                filterPill(locale.t(.filterUnviewed), icon: "sparkles", active: unviewedOnly) {
                    unviewedOnly.toggle(); if unviewedOnly { myOnly = false }
                }
                filterPill(locale.t(.filterFriends), icon: "person.2", active: friendsOnly) {
                    friendsOnly.toggle(); if friendsOnly { myOnly = false }
                }
                filterPill(locale.t(.filterMine), icon: "lock", active: myOnly) {
                    myOnly.toggle()
                    if myOnly { friendsOnly = false; selectedFriendIds = [] }
                }
                // 친구 선택 — 비활성이면 선택 시트, 활성이면 재탭으로 해제(Android 동일 패턴).
                filterPill(
                    selectedFriendIds.isEmpty
                        ? locale.t(.filterPickFriends)
                        : String(format: locale.t(.filterFriendsN), selectedFriendIds.count),
                    icon: "person.badge.plus", active: !selectedFriendIds.isEmpty
                ) {
                    if selectedFriendIds.isEmpty { showFriendPicker = true }
                    else { selectedFriendIds = [] }
                }
                filterPill(
                    periodDays == nil ? locale.t(.filterPeriod) : periodName(periodDays),
                    icon: "clock", active: periodDays != nil
                ) {
                    if periodDays == nil { showPeriodPicker = true } else { periodDays = nil }
                }
            }
            Button {
                withAnimation(.easeOut(duration: 0.18)) { speedDialExpanded.toggle() }
            } label: {
                Image(systemName: "safari")
                    .font(.system(size: 22))
                    .foregroundStyle(anyFilterActive ? Theme.mint : .white.opacity(0.75))
                    .frame(width: 48, height: 48)
                    .background(Color(hex: 0x111120).opacity(0.93), in: Circle())
                    .overlay(Circle().strokeBorder(
                        anyFilterActive ? Theme.mint : Color.white.opacity(0.18), lineWidth: 1.5))
            }
        }
        .confirmationDialog(locale.t(.filterPeriod), isPresented: $showPeriodPicker, titleVisibility: .visible) {
            Button(locale.t(.periodToday)) { periodDays = 0 }
            Button(locale.t(.periodWeek)) { periodDays = 7 }
            Button(locale.t(.periodMonth)) { periodDays = 30 }
            Button(locale.t(.periodYear)) { periodDays = 365 }
        }
        .sheet(isPresented: $showFriendPicker) {
            FriendFilterPicker(friends: myFriends, initial: selectedFriendIds) { ids in
                selectedFriendIds = ids
                if !ids.isEmpty { myOnly = false }
            }
        }
        .task(id: auth.uid) {
            // 친구만/친구선택 필터용 친구 목록 1회 로드(uid 바뀌면 재로드).
            guard let uid = auth.uid else { myFriends = []; return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            myFriends = snap?.documents.compactMap { try? $0.data(as: Friend.self) } ?? []
        }
    }

    /// 스피드 다이얼 알약 옵션(Android FilterOpt 행 대응 — 활성=민트 톤).
    private func filterPill(_ label: String, icon: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundStyle(active ? Theme.mint : .white.opacity(0.7))
                Text(label)
                    .font(.poorStory(13))
                    .foregroundStyle(active ? Theme.mint : .white.opacity(0.85))
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(active ? Theme.mint.opacity(0.18) : Color(hex: 0x111120).opacity(0.93), in: Capsule())
            .overlay(Capsule().strokeBorder(
                active ? Theme.mint : Color.white.opacity(0.12), lineWidth: active ? 1.5 : 1))
        }
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
                onTapStar: { members in
                    // 30m 안에서 겹친 별이면 카드 뷰어로, 하나면 바로 상세로.
                    if members.count > 1 {
                        cluster = ClusterSelection(members: members)
                    } else if let one = members.first {
                        selected = one
                    }
                },
                route: partialRoute,
                focusTarget: focusTarget,
                onGlobeAvailability: { lat, lng, available in
                    globeButtonCenter = available ? GlobeCenter(lat: lat, lng: lng) : nil
                },
                globeReturnCamera: globeReturn,
                pioneerCountries: pioneer.featured,
                onTapPioneer: { code in pioneerMessage = LocalizedNames.pioneerQuestMessage(code) },
                zoomRequest: zoomRequest,
                recenterNonce: recenterNonce
            )
            .ignoresSafeArea()

            if store.loading {
                StarLoadingView(size: 40)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // 별 포커스 파동 — 매 요청마다 .id 로 다시 그려 애니메이션 재생.
            if showWarp {
                MapWarpOverlay(color: warpColor)
                    .id(warpId)
                    .allowsHitTesting(false)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // ── 좌상단 줌 버튼(+/−) — Android DiaryMap 대응(44pt, 0x1A1A1A 원형) ──
            VStack(spacing: 10) {
                mapCircleButton("plus", size: 44) {
                    zoomRequest = (1, zoomRequest.nonce + 1)
                }
                mapCircleButton("minus", size: 44) {
                    zoomRequest = (-1, zoomRequest.nonce + 1)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.top, 16)
            .padding(.leading, 16)

            // ── 우하단: 내 위치 버튼 — Android DiaryMap 우하단 열 대응.
            // (생성 FAB(56pt)은 MainTabView 소유라 그 위(16+56+12)에 얹는다 — Android 세로 나열과 동일한 시각 배치.)
            mapCircleButton("location.north.fill", size: 48) {
                recenterNonce += 1
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.trailing, 16)
            .padding(.bottom, 84)

            // ── 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응(전체/미조회만/기간별) ──
            filterSpeedDial
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 16)
                .padding(.bottom, 20)

            // 길찾기 활성 시: 하단 요약 + 취소(X)
            if !fullRoute.isEmpty {
                routeControls
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }

            // ── 하단 "지구 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
            // (Android: 0xEE111120 알약 + 민트 0.5 테두리)
            if let entry = globeButtonCenter, globeCenter == nil {
                Button {
                    enterGlobe(lat: entry.lat, lng: entry.lng)
                } label: {
                    Label(locale.t(.globeOpen), systemImage: "globe.asia.australia.fill")
                        .font(.poorStory(13))
                        .padding(.horizontal, 18).padding(.vertical, 11)
                        .background(Color(hex: 0x111120).opacity(0.93), in: Capsule())
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
        // 별 상세 — Android 처럼 전체 화면 push(NavRoute.Detail 대응).
        .navigationDestination(isPresented: Binding(
            get: { selected != nil }, set: { if !$0 { selected = nil } }
        )) {
            if let d = selected { DetailScreen(diary: d) }
        }
        // 겹친 별 카드 뷰어 — 전체 화면 push(NavRoute.StarCluster 대응). 카드 탭 → 그 별의 상세로 이어서.
        .navigationDestination(isPresented: Binding(
            get: { cluster != nil }, set: { if !$0 { cluster = nil } }
        )) {
            if let selection = cluster {
                StarClusterView(
                    diaries: selection.members,
                    onOpenDiary: { diary in
                        cluster = nil
                        // pop 애니메이션과 겹치지 않도록 한 틱 뒤에 상세를 연다.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { selected = diary }
                    },
                    onClose: { cluster = nil }
                )
                .toolbar(.hidden, for: .navigationBar) // 자체 뒤로가기 버튼 사용(전체 화면 연출)
            }
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
        MapChromeState.shared.hidden = true   // 글로브에서는 상단바·글쓰기 FAB 숨김(#12)
        withAnimation(.easeInOut(duration: 0.17)) { globeScrim = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            globeCenter = GlobeCenter(lat: lat, lng: lng)
            withAnimation(.easeInOut(duration: 0.52)) { globeScrim = 0 }
        }
    }

    /// 글로브 핀치-인 → 지금 정면 지점의 지도(줌 4)로 복귀.
    private func exitGlobe(lat: Double, lng: Double) {
        MapChromeState.shared.hidden = false  // 지도 복귀 시 크롬 다시 표시
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

/// 친구 선택 필터 시트 — 체크 토글 후 저장. (Android 친구 선택 다이얼로그 대응)
private struct FriendFilterPicker: View {
    let friends: [Friend]
    let initial: Set<String>
    let onConfirm: (Set<String>) -> Void

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var locale = LocaleManager.shared
    @State private var selected: Set<String>

    init(friends: [Friend], initial: Set<String>, onConfirm: @escaping (Set<String>) -> Void) {
        self.friends = friends
        self.initial = initial
        self.onConfirm = onConfirm
        _selected = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(friends) { f in
                            row(f)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle(locale.t(.filterPickFriends))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(locale.t(.commonCancel)) { dismiss() }.tint(Theme.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(locale.t(.commonSave)) { onConfirm(selected); dismiss() }.tint(Theme.mint)
                }
            }
        }
    }

    private func row(_ f: Friend) -> some View {
        let isSel = selected.contains(f.userId)
        return Button {
            if isSel { selected.remove(f.userId) } else { selected.insert(f.userId) }
        } label: {
            HStack(spacing: 12) {
                Text(f.userName)
                    .font(.poorStory(15))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                Spacer()
                if isSel { Image(systemName: "checkmark").foregroundStyle(Theme.mint) }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(isSel ? Theme.mint.opacity(0.14) : Color.white.opacity(0.04),
                        in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
