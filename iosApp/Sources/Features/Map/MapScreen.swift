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
    /// 최초 onAppear 를 구분 — 이후의 onAppear = 하위 화면에서 지도(루트)로 복귀.
    @State private var rootAppearedOnce = false
    // 별자리 라인 토글(Android constellationEnabled) + 몰입(지도만 보기) 크롬 상태.
    @State private var constellationOn = false
    @ObservedObject private var chrome = MapChromeState.shared

    // 도보 길찾기 상태.
    @State private var focusTarget: CLLocationCoordinate2D?
    @State private var fullRoute: [CLLocationCoordinate2D] = []   // 처음 받은 전체 경로(X 취소까지 유지)
    @State private var routeSummary: String?

    // 별 포커스 "파동" 연출 — 화면 중앙(포커스된 별)에서 물결이 퍼진 뒤 길찾기가 뜬다.
    @State private var showWarp = false
    @State private var warpColor: Color = .white
    @State private var warpId = 0

    // 다이어리 열람 파장(warp) + 게이팅 토스트(Android DiaryOpenWarp/StaryToast 대응).
    @State private var openWarp: DiaryOpenWarpData?
    @State private var toast: String?

    // 세계(웹메르카토르) 상하 타일 한계 밖 "빈 공간" 오버레이 상태 — (위쪽 끝 y, 아래쪽 시작 y, 줌).
    // 빈 공간이 화면에 없으면 (0, ∞) 센티널 유지(Android worldVoid 패리티).
    @State private var voidTopY: CGFloat = 0
    @State private var voidBottomY: CGFloat = .greatestFiniteMagnitude
    @State private var voidZoom: Double = 0

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

    /// 지도 위 원형 버튼 공통 — 필터 버튼과 같은 남색빛 검정 + 엠보스 테두리.
    /// (Android FloatingActionButton 0xEE111120 + raisedCosmicBorder 대응)
    private func mapCircleButton(
        _ icon: String, size: CGFloat, tint: Color = .white, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size * 0.42, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .background(Color(hex: 0x111120).opacity(0.93), in: Circle())
                .raisedCosmicBorder()
                .shadow(color: .black.opacity(0.3), radius: 5, y: 2)
        }
    }

    /// 필터가 하나라도 켜져 있는가(메인 FAB 민트 강조 — Android anyActive).
    private var anyFilterActive: Bool {
        unviewedOnly || friendsOnly || myOnly || !selectedFriendIds.isEmpty || periodDays != nil
    }

    /// 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응.
    /// 메인 원형 버튼(나침반) 탭 → 위로 알약 옵션(미조회만/친구만/나만보기/친구선택/기간별).
    /// 상호배타 로직도 Android 동일(나만보기 ↔ 친구만/친구선택 등).
    private var filterSpeedDial: some View {
        VStack(alignment: .leading, spacing: 8) {
            if speedDialExpanded {
                // "전체보기"는 기본 상태(필터 없음)와 같아 목록에서 제외 — 각 필터 재탭으로 해제(Android 동일).
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
                    .foregroundStyle(anyFilterActive ? Theme.navyAccent : .white.opacity(0.75))
                    .frame(width: 48, height: 48)
                    .background(Color(hex: 0x111120).opacity(0.93), in: Circle())
                    .overlay(Circle().strokeBorder(
                        anyFilterActive ? Theme.navyAccent : Color.white.opacity(0.18), lineWidth: 1.5))
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
                    .foregroundStyle(active ? Theme.navyAccent : .white.opacity(0.7))
                Text(label)
                    .font(.minSans(13))
                    .foregroundStyle(active ? Theme.navyAccent : .white.opacity(0.85))
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(active ? Theme.navyAccent.opacity(0.18) : Color(hex: 0x111120).opacity(0.93), in: Capsule())
            .overlay(Capsule().strokeBorder(
                active ? Theme.navyAccent : Color.white.opacity(0.12), lineWidth: active ? 1.5 : 1))
        }
    }

    /// 실시간 위치 기준으로 "최근접점→목적지"만 남긴 경로(지나온 구간 제외).
    private var partialRoute: [CLLocationCoordinate2D] {
        guard fullRoute.count >= 2 else { return [] }
        guard let me = location.coordinate else { return fullRoute }
        return MapScreen.partialRouteFrom(full: fullRoute, me: me)
    }

    /// 별 탭 — Android DiaryMap 마커 클릭과 동일한 100m 게이팅:
    /// 위치 불명 → 안내 토스트 / 100m 밖 → 거리 토스트 / 이내 → 파장(warp) 후 상세·카드 진입.
    private func handleStarTap(members: [Diary], origin: CGPoint, snapshot: UIImage?) {
        guard let rep = members.first else { return }
        guard openWarp == nil else { return } // 파장 재생 중 중복 탭 무시
        guard let me = location.coordinate else {
            showToast(locale.t(.mapWaitingFix))
            return
        }
        let dist = Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude,
                                      lat2: rep.latitude, lng2: rep.longitude)
        guard dist <= AppConfig.diaryOpenRadiusM else {
            showToast(String(format: locale.t(.mapOpenRange),
                             Int(AppConfig.diaryOpenRadiusM), Geo.formatDistance(dist)))
            return
        }
        MusicManager.shared.playOpenDiary() // Android: 파장 시작과 함께 열람 효과음
        openWarp = DiaryOpenWarpData(snapshot: snapshot, origin: origin, members: members)
    }

    private func showToast(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            toast = nil
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            MapLibreView(
                diaries: shownDiaries,
                userLocation: location.coordinate,
                onTapStar: { members, origin, snapshot in
                    handleStarTap(members: members, origin: origin, snapshot: snapshot)
                },
                route: partialRoute,
                focusTarget: focusTarget,
                onGlobeAvailability: { lat, lng, available in
                    globeButtonCenter = available ? GlobeCenter(lat: lat, lng: lng) : nil
                },
                globeReturnCamera: globeReturn,
                pioneerCountries: pioneer.activeCountries,
                onTapPioneer: { code in pioneerMessage = LocalizedNames.pioneerQuestMessage(code) },
                zoomRequest: zoomRequest,
                recenterNonce: recenterNonce,
                constellationEnabled: constellationOn,
                onWorldVoid: { top, bottom, zoom in
                    voidTopY = top
                    voidBottomY = bottom
                    voidZoom = zoom
                }
            )
            .ignoresSafeArea()

            // 세계 상하 끝(타일 한계) 밖 빈 공간 — 물 레이어와 같은 색(줌 보간)으로 덮어
            // 바다가 이어진 것처럼 보이게 한다(Android 비네트 Canvas 의 바다색 덮기 패리티).
            worldVoidOverlay
                .ignoresSafeArea()
                .allowsHitTesting(false)

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

            // 다이어리 열람 파장(warp) — 지도 스냅샷 굴절 + 파장 링 + (겹친 별) 버스트.
            // 끝나면 멤버 수에 따라 상세/겹친 별 카드로 진입(Android DiaryOpenWarp 흐름 동일).
            if let w = openWarp {
                DiaryOpenWarpView(data: w) {
                    openWarp = nil
                    if w.members.count > 1 {
                        cluster = ClusterSelection(members: w.members)
                    } else if let one = w.members.first {
                        selected = one
                    }
                }
            }

            // 하단 토스트(Android StaryToast 대응) — 100m 게이팅/위치 확인 안내.
            if let t = toast {
                ToastView(text: t)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }

            // 몰입(지도만 보기)에선 지도 위 모든 버튼을 숨긴다(Android MapUiState.mapOnly 대응).
            if !chrome.mapOnly {
            // ── 좌상단 줌 버튼(+/−) — Android DiaryMap 대응(44pt, 남색빛 검정 원형) ──
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

            // ── 우하단 열: 내 위치 → 별자리 토글 → 몰입 — Android DiaryMap 우하단 열 순서 동일.
            // (생성 FAB(56pt)은 MainTabView 소유라 그 위(16+56+12)에 얹는다 — Android 세로 나열과 동일한 시각 배치.)
            VStack(spacing: 12) {
                mapCircleButton("location.north.fill", size: 48) {
                    recenterNonce += 1
                }
                // 별자리 토글 — 활성 시 민트(Android AutoAwesome tint 동일).
                mapCircleButton("sparkles", size: 48,
                                tint: constellationOn ? Theme.mint : .white) {
                    constellationOn.toggle()
                }
                .accessibilityLabel(locale.t(.mapConstellation))
                // 몰입(지도만 보기) — 탑바/필터/버튼을 모두 숨기고 지도에 집중.
                mapCircleButton("eye", size: 48) {
                    chrome.mapOnly = true
                }
                .accessibilityLabel(locale.t(.mapOnlyMode))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.trailing, 16)
            .padding(.bottom, 84)

            // ── 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응(전체/미조회만/기간별) ──
            filterSpeedDial
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 16)
                .padding(.bottom, 20)
            } // if !chrome.mapOnly

            // 몰입 종료 — 하단 중앙 X(3초 뒤 자동 숨김, 재탭으로 다시 표시). (Android MapOnlyOverlay 대응)
            if chrome.mapOnly {
                MapOnlyExitOverlay { chrome.mapOnly = false }
            }

            // 길찾기 활성 시: 하단 요약 + 취소(X)
            if !fullRoute.isEmpty {
                routeControls
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }

            // ── 하단 "지구 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
            // (Android: 0xEE111120 알약 + 민트 0.5 테두리)
            if let entry = globeButtonCenter, globeCenter == nil, !chrome.mapOnly {
                Button {
                    enterGlobe(lat: entry.lat, lng: entry.lng)
                } label: {
                    Label(locale.t(.globeOpen), systemImage: "globe.asia.australia.fill")
                        .font(.minSans(13))
                        .padding(.horizontal, 18).padding(.vertical, 11)
                        .background(Color(hex: 0x111120).opacity(0.93), in: Capsule())
                        .foregroundStyle(Theme.textPrimary)
                        .overlay(Capsule().strokeBorder(Theme.navyAccent.opacity(0.5), lineWidth: 1))
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
            // 하위 화면 → 지도(루트) 복귀: NavigationStack 루트는 파괴되지 않으므로(지도 유지)
            // 카메라만 내 위치로 옮긴다. 최초 진입, 포커스/길찾기 요청 대기(아래 handleFocus 가
            // 카메라를 다룸 — 반드시 이 검사보다 뒤에 소비), 도보 경로 진행 중엔 건너뛴다.
            // (Android MainScreen 라우트 전환 재센터 패리티)
            if rootAppearedOnce, focus.pendingDiaryId == nil, fullRoute.isEmpty {
                recenterNonce += 1
            }
            rootAppearedOnce = true
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

    /// 세계 상하 끝 밖 빈 공간을 덮는 바다색 사각형(위/아래) — 물 레이어 색과 같은 줌 보간.
    /// (스타일 레이어는 타일 밖을 못 그리므로 SwiftUI 오버레이로 덮는다 — Android 동일 접근)
    private var worldVoidOverlay: some View {
        GeometryReader { geo in
            let h = geo.size.height
            if voidTopY > 0 || voidBottomY < h {
                let f = min(max((voidZoom - 6) / 10, 0), 1)
                let sea = Color(hex: 0x080617).blended(with: Color(hex: 0x142229), fraction: f)
                ZStack(alignment: .top) {
                    if voidTopY > 0 {
                        sea.frame(height: min(voidTopY, h))
                            .frame(maxHeight: .infinity, alignment: .top)
                    }
                    if voidBottomY < h {
                        sea.frame(height: h - max(voidBottomY, 0))
                            .frame(maxHeight: .infinity, alignment: .bottom)
                    }
                }
            }
        }
    }

    /// 하단 길찾기 컨트롤 — 요약 칩 + 취소 버튼.
    private var routeControls: some View {
        VStack(spacing: 10) {
            if let s = routeSummary {
                Text(s)
                    .font(.minSans(12, .medium))
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
                    .raisedCosmicBorder()
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
        routeSummary = "\(mins)\(locale.t(.routeMinSuffix)) · \(Geo.formatDistance(route.distanceM))"
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

/// 몰입(지도만 보기) 종료 오버레이 — 하단 중앙 X 버튼. 3초 뒤 자동 숨김, 그 자리를 다시
/// 탭하면 X 가 재등장한다(지도 조작은 그대로 통과). (Android MapOnlyOverlay 대응)
private struct MapOnlyExitOverlay: View {
    let onExit: () -> Void
    @State private var xVisible = true
    /// 값이 바뀔 때마다 X 를 다시 보이고 3초 자동 숨김 타이머를 재시작.
    @State private var poke = 0

    var body: some View {
        Button {
            if xVisible { onExit() } else { poke += 1 }
        } label: {
            ZStack {
                if xVisible {
                    Image(systemName: "xmark")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Color(hex: 0x141414).opacity(0.8), in: Circle())
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.22), lineWidth: 1))
                        .transition(.opacity.combined(with: .scale(scale: 0.7)))
                }
            }
            .frame(width: 64, height: 64)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .padding(.bottom, 24)
        .task(id: poke) {
            withAnimation(.easeOut(duration: 0.18)) { xVisible = true }
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: 0.25)) { xVisible = false }
        }
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
                    Button(locale.t(.commonSave)) { onConfirm(selected); dismiss() }.tint(Theme.navyAccent)
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
                    .font(.minSans(15))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                Spacer()
                if isSel { Image(systemName: "checkmark").foregroundStyle(Theme.navyAccent) }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(isSel ? Theme.navyAccent.opacity(0.14) : Color.white.opacity(0.04),
                        in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
