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
    /// 내가 차단한 사용자 — 그 사람의 별은 지도에서 제외한다(Android MainListScreen blockedIds 패리티).
    @EnvironmentObject var blocks: BlockStore
    @ObservedObject private var locale = LocaleManager.shared
    @ObservedObject private var focus = MapFocusStore.shared
    /// 첫 실행 기기 전용 웰컴 별(Android TutorialStarState 패리티).
    @ObservedObject private var tutorial = TutorialStarState.shared
    @State private var selected: Diary?
    /// 30m 안에서 겹친 별 무리(2개 이상) — 카드 뷰어 시트로 연다.
    @State private var cluster: ClusterSelection?
    /// 미해금만 — 눌렀을 때 잠금 화면이 뜨는 별만(남의 글 + 해금 안 함 + 100m 밖). 해금만의 정확한 반대. Android lockedOnly 패리티.
    @State private var lockedOnly = false
    // 개척 퀘스트(체크리스트 32) — 개척 현황 구독 + 비콘 탭 안내.
    @StateObject private var pioneer = PioneerStore()
    @State private var pioneerMessage: String?
    // 기간별 보기 — nil=전체 기간, 0=오늘(자정 이후), 그 외 N=최근 N일. (Android 기간 필터 패리티)
    @State private var periodDays: Int?
    // 친구만/나만보기/친구선택 필터(Android MainListScreen 필터 패리티).
    @EnvironmentObject var auth: AuthManager
    @State private var friendsOnly = false
    @State private var myOnly = false
    /// 해금만 — 잠금 없이 바로 열리는 별만(내 글 / 100m 이내 / 영구 해금). Android unlockedOnly 패리티.
    @State private var unlockedOnly = false
    /// 영구 해금 기록 — 해금되는 순간 지도 필터도 같이 갱신되게 관찰한다.
    @ObservedObject private var unlocks = DiaryUnlockStore.shared
    @State private var selectedFriendIds: Set<String> = []
    /// 지금 필터 상태를 어느 계정 것으로 읽어 왔는지(nil = 아직) — 읽기 전엔 저장하지 않는다(MapFilterStore).
    @State private var filterOwner: String?

    /// 현재 필터 조합 — 바뀔 때마다 계정별로 저장해, 앱을 껐다 켜도 그대로 걸려 있게 한다(Android MapFilterStore 패리티).
    private var currentFilters: MapFilters {
        MapFilters(lockedOnly: lockedOnly, friendsOnly: friendsOnly, myOnly: myOnly,
                   unlockedOnly: unlockedOnly, selectedFriendIds: selectedFriendIds, periodDays: periodDays)
    }

    /// 이 계정의 마지막 필터를 불러와 적용한다(앱 시작·로그인 계정 변경 시).
    private func applySavedFilters() {
        let f = MapFilterStore.load(uid: auth.uid)
        lockedOnly = f.lockedOnly
        friendsOnly = f.friendsOnly
        myOnly = f.myOnly
        unlockedOnly = f.unlockedOnly
        selectedFriendIds = f.selectedFriendIds
        periodDays = f.periodDays
        filterOwner = auth.uid ?? "guest"
    }

    /// 필터 조합 식별값 — 바뀌면 지도 별이 순차 등장으로 다시 뜬다(데이터·위치 갱신은 포함하지 않는다).
    private var filterRevealKey: Int {
        var h = Hasher()
        h.combine(lockedOnly); h.combine(friendsOnly); h.combine(myOnly); h.combine(unlockedOnly)
        h.combine(selectedFriendIds); h.combine(periodDays)
        return h.finalize()
    }
    /// 마지막에 켠 필터 추적(참조 객체 — body 에서 갱신해도 다시 그리기를 일으키지 않는다).
    @State private var filterKindTracker = FilterKindTracker()
    @State private var showFriendPicker = false
    /// 내 친구 목록 — 친구만/친구선택 필터용 1회 로드.
    @State private var myFriends: [Friend] = []
    // 좌하단 필터 스피드 다이얼 펼침(Android speedDialExpanded 대응) + 기간 선택 다이얼로그.
    @State private var speedDialExpanded = false
    @State private var showPeriodPicker = false
    // 줌 +/− / 내 위치 버튼 → MapLibreView 커맨드(nonce 로 반복 요청 허용).
    @State private var zoomRequest: (delta: Double, nonce: Int) = (0, 0)
    /// 줌 버튼 꾹 누르기 연속 줌 컨트롤러 — MapLibreView 가 지도 뷰를 연결해 준다.
    @State private var zoomHold = MapZoomHold()
    @State private var recenterNonce = 0
    /// 최초 onAppear 를 구분 — 이후의 onAppear = 하위 화면에서 지도(루트)로 복귀.
    @State private var rootAppearedOnce = false
    /// 지도에서 별(상세/겹친 별 카드)을 열러 나갔는지 — 돌아왔을 때 재센터하지 않고 보던 카메라 유지
    /// (2026-09-25 사용자 요청, Android MainScreen leftMapFor 패리티). 상세에서 더 들어갔다 와도 유지.
    @State private var leftForDiary = false
    /// 겹친 별 카드 → 상세로 넘어가는 사이(카드 pop 후 0.35s 뒤 상세 push) — 그 사이 루트가 잠깐 나타나도 플래그 유지.
    @State private var diaryHandoff = false
    // 별자리 라인 토글(Android constellationEnabled) + 몰입(지도만 보기) 크롬 상태.
    @State private var constellationOn = false
    @ObservedObject private var chrome = MapChromeState.shared
    /// 첫 별 안내(FirstStarGuide) — 열쇠 수·안내 완료 여부가 바뀌면 카드도 바뀐다.
    @ObservedObject private var quests = QuestStore.shared
    /// 첫 사용 안내 진행(퀘스트 창을 봤는지·프로필을 바꿨는지) — 바뀌면 카드도 바뀐다.
    @ObservedObject private var guideProgress = FirstStarGuideProgress.shared
    /// 별가루 지갑 — 열쇠가 없을 때 "별가루로 열쇠 만들기" 안내 단계가 이 값으로 바뀐다.
    @ObservedObject private var starlight = StarlightStore.shared

    // 포커스(카메라 이동) 대상 + 요청 번호(같은 별을 다시 요청해도 카메라가 움직이게 — MapLibreView.focusNonce).
    @State private var focusTarget: CLLocationCoordinate2D?
    @State private var focusNonce = 0
    /// 마지막으로 포커스를 적용한 시각 — 직후의 "지도 복귀 재센터"를 건너뛰는 데 쓴다(onAppear 주석 참고).
    @State private var lastFocusAt = Date.distantPast
    /// 목록에 없는 별을 Firestore 에서 직접 읽는 중(중복 요청 방지) — handleFocus.
    @State private var focusFetching = false

    // 별 포커스 "파동" 연출 — 화면 중앙(포커스된 별)에서 물결이 퍼진다.
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

    // 3D 행성(글로브) — 줌을 충분히 빼면 하단 버튼이 뜨고, 눌러야 진입(Android MainListScreen 글로브 상태 패리티).
    @State private var globeCenter: GlobeCenter?
    @State private var globeReturn: GlobeReturnCamera?
    /// 줌이 낮을 때 지도에서 보고되는 "우주에서 보기" 후보 중심(nil = 버튼 숨김).
    @State private var globeButtonCenter: GlobeCenter?
    /// 지도 ↔ 글로브 교체를 가리는 검정 디졸브 스크림.
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

    /// 미해금/해금/친구/나만/친구선택/기간 필터 적용된 표시 대상. (Android MainListScreen 필터 파이프라인 대응)
    private var shownDiaries: [Diary] {
        // 차단한 사용자의 별은 어떤 필터 조합에서도 뜨지 않는다(가장 먼저 제외).
        var list = store.diaries.filter { !blocks.blockedIds.contains($0.userId) }
        // 부적절한 표현이 든 **남의** 글은 걸러낸다(App Store 1.2 — Android MainListScreen 패리티).
        list = list.filter { $0.userId == auth.uid || !ContentFilter.anyObjectionable($0.title, $0.content) }
        if friendsOnly {
            let ids = Set(myFriends.map { $0.userId })
            list = list.filter { ids.contains($0.userId) }
        }
        if myOnly { list = list.filter { $0.userId == auth.uid } }
        // 해금만: 내 글 / 이미 해금한 글 / 지금 100m 이내 — 셋 중 하나면 통과.
        // (= 이 필터로 보이는 별은 눌렀을 때 잠금 화면이 뜨지 않는다. DetailScreen 의 unlocked 규칙과 동일)
        // 100m 판정은 **실제 fix**(location.coordinate)만 — 없으면 거리로는 통과시키지 않는다.
        // 미해금만: 위 "바로 열림" 의 정확한 반대 — 눌렀을 때 잠금 화면이 뜨는 별만. 위치 fix 가 없으면 근처가 아니므로 잠긴 쪽.
        if unlockedOnly || lockedOnly {
            let fix = location.coordinate
            let uid = auth.uid
            let wantOpen = unlockedOnly
            list = list.filter { d in
                var isOpen = d.userId == uid
                if !isOpen, let id = d.id, unlocks.isUnlocked(id) { isOpen = true }
                if !isOpen, let me = fix {
                    isOpen = Geo.distanceMeters(
                        lat1: me.latitude, lng1: me.longitude, lat2: d.latitude, lng2: d.longitude
                    ) <= AppConfig.diaryOpenRadiusM
                }
                return isOpen == wantOpen
            }
        }
        if !selectedFriendIds.isEmpty { list = list.filter { selectedFriendIds.contains($0.userId) } }
        if let cutoff = periodCutoffMs { list = list.filter { $0.createdAt >= cutoff } }
        // 웰컴 별 — 서버에 없는 합성 다이어리라 필터와 무관하게 맨 뒤에 덧붙인다(Android mapDiaries 동일).
        if let t = tutorial.diary { list.append(t) }
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
            mapCircleLabel(icon, size: size, tint: tint)
        }
    }

    /// 지도 위 원형 버튼의 겉모습(Button 없이) — 줌 버튼처럼 자체 제스처를 붙이는 쪽에서도 같은 모양을 쓴다.
    private func mapCircleLabel(_ icon: String, size: CGFloat, tint: Color = .white) -> some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.42, weight: .medium))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(Color(hex: 0x111120).opacity(0.93), in: Circle())
            .raisedCosmicBorder()
            .shadow(color: .black.opacity(0.3), radius: 5, y: 2)
    }

    /// 지금 색을 정하는 필터 = 가장 마지막에 켠 필터 — 빛의 고리(필터 전환 등장)와 지도에 도는 은은한 빛의 색.
    /// 필터가 없으면 nil. (Android MainListScreen filterKind 패리티 — 필터 조합과 같은 렌더에서 정해져야 링 색이 한 박자 늦지 않는다)
    private var filterKind: MapFilterKind? {
        var active: [MapFilterKind] = []
        if lockedOnly { active.append(.locked) }
        if unlockedOnly { active.append(.unlocked) }
        if friendsOnly { active.append(.friends) }
        if myOnly { active.append(.mine) }
        if !selectedFriendIds.isEmpty { active.append(.pickFriends) }
        if periodDays != nil { active.append(.period) }
        return filterKindTracker.update(active)
    }

    /// 필터가 하나라도 켜져 있는가(메인 FAB 민트 강조 — Android anyActive).
    private var anyFilterActive: Bool {
        lockedOnly || friendsOnly || myOnly || unlockedOnly
            || !selectedFriendIds.isEmpty || periodDays != nil
    }

    /// 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응.
    /// 메인 원형 버튼(나침반) 탭 → 위로 알약 옵션(미해금만/해금만/친구만/나만보기/친구선택/기간별).
    /// 상호배타 로직도 Android 동일(나만보기 ↔ 친구만/친구선택 등).
    private var filterSpeedDial: some View {
        VStack(alignment: .leading, spacing: 8) {
            if speedDialExpanded {
                // "전체보기"는 기본 상태(필터 없음)와 같아 목록에서 제외 — 각 필터 재탭으로 해제(Android 동일).
                // 미해금만 — 눌렀을 때 잠금 화면이 뜨는 별(남의 글 + 해금 안 함 + 100m 밖)만 남긴다. 아직 열지 못한 별 찾기용.
                filterPill(locale.t(.filterLocked), icon: "lock.badge.clock", active: lockedOnly) {
                    // 미해금 ↔ 해금만은 정확히 반대말, 내 글은 항상 열려 있으니 나만보기와도 같이 켜면 항상 빈 지도 → 서로 끈다.
                    lockedOnly.toggle()
                    if lockedOnly { myOnly = false; unlockedOnly = false }
                }
                // 해금만 — 눌렀을 때 잠금 화면이 뜨지 않는 별만 남긴다.
                filterPill(locale.t(.filterUnlocked), icon: "lock.open", active: unlockedOnly) {
                    unlockedOnly.toggle(); if unlockedOnly { lockedOnly = false }
                }
                filterPill(locale.t(.filterFriends), icon: "person.2", active: friendsOnly) {
                    friendsOnly.toggle(); if friendsOnly { myOnly = false }
                }
                filterPill(locale.t(.filterMine), icon: "lock", active: myOnly) {
                    myOnly.toggle()
                    if myOnly { friendsOnly = false; selectedFriendIds = []; lockedOnly = false }
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
                withAnimation(.easeOut(duration: 0.18)) {
                    speedDialExpanded.toggle()
                }
            } label: {
                Image(systemName: "safari")
                    .font(.system(size: 22))
                    .foregroundStyle(
                        anyFilterActive
                        ? Theme.navyAccent
                        : .white.opacity(0.75)
                    )
                    .frame(width: 48, height: 48)
                    .background(
                        Color(hex: 0x111120).opacity(0.93),
                        in: Circle()
                    )
                    .overlay(
                        Circle().strokeBorder(
                            anyFilterActive
                            ? Theme.navyAccent
                            : Color.white.opacity(0.18),
                            lineWidth: 1.5
                        )
                    )
            }
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .preference(
                            key: CoachMarkPositionKey.self,
                            value: [
                                CoachMarkAnchor.filter:
                                    CGPoint(
                                        x: proxy.frame(in: .global).midX,
                                        y: proxy.frame(in: .global).midY
                                    )
                            ]
                        )
                }
            )
        }
        .staryChoiceDialog(locale.t(.filterPeriod), isPresented: $showPeriodPicker, options: [
            StaryDialogOption(locale.t(.periodToday), action: { periodDays = 0 }),
            StaryDialogOption(locale.t(.periodWeek), action: { periodDays = 7 }),
            StaryDialogOption(locale.t(.periodMonth), action: { periodDays = 30 }),
            StaryDialogOption(locale.t(.periodYear), action: { periodDays = 365 }),
        ])
        .sheet(isPresented: $showFriendPicker) {
            FriendFilterPicker(friends: myFriends, initial: selectedFriendIds) { ids in
                selectedFriendIds = ids
                if !ids.isEmpty { myOnly = false }
            }
        }
        // 필터 영속: 계정이 정해지면(앱 시작·로그인 변경) 그 계정의 마지막 필터를 불러오고, 바뀔 때마다 저장한다.
        .task(id: auth.uid) { applySavedFilters() }
        .onChange(of: currentFilters) { filters in
            // 다른 계정의 필터가 아직 화면에 남아 있는 순간(로그인 직후)에는 저장하지 않는다.
            guard filterOwner == (auth.uid ?? "guest") else { return }
            MapFilterStore.save(filters, uid: auth.uid)
        }
        .task(id: auth.uid) {
            // 서버 해금 사본 + 잠금 이전 열람 기록을 로컬에 합친다(실행당 1회 — 별 도감·상세 잠금·"해금만" 필터 공용).
            if let uid = auth.uid { await DiaryUnlockStore.shared.syncWithServer(uid: uid) }
        }
        .task(id: auth.uid) {
            // 친구만/친구선택 필터용 친구 목록 1회 로드(uid 바뀌면 재로드).
            guard let uid = auth.uid else { myFriends = []; return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            myFriends = snap?.documents.compactMap { try? $0.data(as: Friend.self) } ?? []
            // 퀘스트 "친구 만들기" — 오늘 생긴 친구(내 요청이 수락된 경우 포함). 같은 친구는 하루 한 번(Android MainListScreen 패리티).
            let startOfDay = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
            for f in myFriends where f.createdAt >= startOfDay { QuestStore.report(.friendAdd, key: f.userId) }
        }
    }

    /// 스피드 다이얼 알약 옵션(Android FilterOpt 행 대응 — 활성=민트 톤).
    private func filterPill(_ label: String, icon: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button {
            action()
            // 일일 퀘스트 "지도 필터 써 보기" — 켤 때만(Android FilterOpt 클릭과 같은 규칙).
            if !active { QuestStore.report(.filter) }
        } label: {
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

    /// 별 탭 — Android DiaryMap 마커 클릭 패리티. 거리와 무관하게 파장(warp) 후 상세·카드 진입.
    /// 100m 게이팅은 "진입 차단"이 아니라 **상세 화면의 내용 잠금**으로 옮겼다(2026-09-21).
    private func handleStarTap(members: [Diary], origin: CGPoint, snapshot: UIImage?) {
        guard members.first != nil else { return }
        guard openWarp == nil else { return } // 파장 재생 중 중복 탭 무시
        MusicManager.shared.playOpenDiary() // Android: 파장 시작과 함께 열람 효과음
        Haptics.warp()                       // 파장과 같은 결의 진동(Android DiaryMap 패리티)
        // 빈(검정/투명) 스냅샷은 버린다 — 굴절시키면 연출 내내 화면이 까매진다. 없으면 링만 재생.
        let usable = snapshot.flatMap { WarpMesh.isUsable($0) ? $0 : nil }
        openWarp = DiaryOpenWarpData(snapshot: usable, origin: origin, members: members)
    }

    /// 글로브에 넘길 별 — 웰컴 별(합성 다이어리)은 제외(Android 도 filteredDiaries 만 넘긴다).
    private var globeDiaries: [Diary] {
        shownDiaries.filter { $0.id != TutorialStarState.diaryId }
    }

    // MARK: 3D 글로브 전환 (Android MainListScreen: 진입 170ms→교체→520ms / 복귀 170ms→교체→70ms→380ms)
    // ⚠️ DispatchQueue 클로저 대신 메인 액터 Task — @MainActor 객체(chrome) 접근이 격리 에러가 되지 않게.

    private func enterGlobe(lat: Double, lng: Double) {
        guard globeCenter == nil else { return }
        withAnimation(.easeInOut(duration: 0.17)) { globeScrim = 1 }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 180_000_000)
            globeCenter = GlobeCenter(lat: lat, lng: lng)
            chrome.globeOpen = true
            QuestStore.report(.globe)
            withAnimation(.easeInOut(duration: 0.52)) { globeScrim = 0 }
        }
    }

    /// 글로브 X → 지도 복귀(지도는 내 위치 줌 15로 이동 — MapLibreView globeReturnCamera).
    private func exitGlobe(lat: Double, lng: Double) {
        withAnimation(.easeInOut(duration: 0.17)) { globeScrim = 1 }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 180_000_000)
            globeReturn = GlobeReturnCamera(lat: lat, lng: lng, zoom: 4.0, nonce: (globeReturn?.nonce ?? 0) + 1)
            globeCenter = nil
            chrome.globeOpen = false
            try? await Task.sleep(nanoseconds: 70_000_000) // 지도 카메라 점프가 프레임에 반영될 시간
            withAnimation(.easeInOut(duration: 0.38)) { globeScrim = 0 }
        }
    }

    // MARK: 첫 별 안내(FirstStarGuide) — Android FirstStarGuide.kt 패리티

    private var guideStep: GuideStep? {
        FirstStarGuideLogic.step(uid: auth.uid, diaries: store.diaries, loading: store.loading, here: location.coordinate)
    }

    /// 지도에서 안내를 띄울 수 있을 때의 단계 — RootView 가 `.quest` 스포트라이트·퀘스트 창 강조에 쓴다.
    private var publishedGuideStep: GuideStep? {
        guard !chrome.coachMarkShowing, globeCenter == nil else { return nil }
        return guideStep
    }

    private func guideShown(_ step: GuideStep) {
        if step == .open { quests.guideOpenShown = true }
        StaryAnalytics.logOnce("guide_" + step.rawValue, "guide_step", ["step": step.rawValue])
    }

    private func guideAction(_ step: GuideStep) {
        guard let uid = auth.uid else { return }
        StaryAnalytics.log("guide_action", ["step": step.rawValue])
        Haptics.tick()
        switch step {
        case .quest:
            withAnimation(.easeOut(duration: 0.2)) { chrome.questsOpen = true }
        case .profile:
            TabRouter.shared.go(TabRouter.profile)
        case .write:
            TabRouter.shared.go(TabRouter.upload)
        case .claim:
            if (quests.daily?.claimable ?? 0) > 0 {
                withAnimation(.easeOut(duration: 0.2)) { chrome.questsOpen = true }
            } else if quests.grantGuideKey() {
                // 오늘 퀘스트에 "올리기"가 없던 날 — 첫 별 보상 열쇠를 바로 준다.
                Haptics.celebrate()
                GlobalToast.shared.show(String(format: locale.t(.questKeyGained), 1))
            } else {
                withAnimation(.easeOut(duration: 0.2)) { chrome.questsOpen = true }
            }
        case .exchange:
            TabRouter.shared.go(TabRouter.workshop)
        case .open:
            guard let target = FirstStarGuideLogic.target(diaries: store.diaries, uid: uid,
                                                          here: location.coordinate, blocked: blocks.blockedIds),
                  let id = target.id else {
                GlobalToast.shared.show(locale.t(.guideNoTarget))
                quests.markGuideDone(via: "no_target")
                return
            }
            // 그 별로 카메라를 옮기고(파동) 잠깐 보여 준 뒤 상세로 — 잠금 카드에서 열쇠를 쓴다.
            MapFocusStore.shared.request(diaryId: id)
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 1_800_000_000)
                leftForDiary = true
                selected = target
                try? await Task.sleep(nanoseconds: 700_000_000)
                GlobalToast.shared.show(locale.t(.guideOpenHint))
            }
        }
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
                focusTarget: focusTarget,
                pioneerCountries: pioneer.activeCountries,
                onTapPioneer: { code in pioneerMessage = LocalizedNames.pioneerQuestMessage(code) },
                zoomRequest: zoomRequest,
                recenterNonce: recenterNonce,
                constellationEnabled: constellationOn,
                onGlobeAvailability: { lat, lng, available in
                    globeButtonCenter = available ? GlobeCenter(lat: lat, lng: lng) : nil
                },
                globeReturnCamera: globeReturn,
                onWorldVoid: { top, bottom, zoom in
                    voidTopY = top
                    voidBottomY = bottom
                    voidZoom = zoom
                },
                // 필터 조합이 바뀌면 화면 중심에서 빛의 고리가 퍼지며 링이 닿는 별부터 다시 생긴다(Android DiaryMap revealKey 패리티).
                revealKey: filterRevealKey,
                zoomHold: zoomHold,
                // 필터가 걸려 있으면 남은 별의 후광을 조금 더 밝게(2026-10-10, Android highlight 패리티).
                filterHighlight: anyFilterActive,
                // 필터마다 다른 색 — 빛의 고리 색(필터 없으면 nil = 색 없는 별빛).
                filterTintHex: filterKind?.hex,
                focusNonce: focusNonce
            )
            .ignoresSafeArea()

            // 다이어리 열람 파장의 지도 굴절 — 스냅샷을 별 위치에서 퍼지는 물결로 민다(Android drawBitmapMesh 패리티).
            // 지도 **바로 위**에 둬서 바다 덮개·하늘·버튼은 그대로 위에 보이고, 링/버스트는 아래 DiaryOpenWarpView 가 그린다.
            // 파장 끝(p=1)엔 변위 0 = 라이브 지도와 같은 그림이라 걷어낼 때 이음매가 없다.
            if let w = openWarp, let snap = w.snapshot {
                DiaryWarpMeshView(snapshot: snap, origin: w.origin, startedAt: w.startedAt)
                    .id(w.id)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }

            // 세계 상하 끝(타일 한계) 밖 빈 공간 — 물 레이어와 같은 색(줌 보간)으로 덮어
            // 바다가 이어진 것처럼 보이게 한다(Android 비네트 Canvas 의 바다색 덮기 패리티).
            worldVoidOverlay
                .ignoresSafeArea()
                .allowsHitTesting(false)

            // 필터가 걸려 있는 동안 지도에 도는 은은한 빛(필터 색) — 지도 위, 버튼 아래. 터치 통과.
            // (예전 여명·황혼 오버레이 SkyOverlay 는 2026-10-14 삭제 — 같은 모양을 필터 빛이 물려받았다.)
            FilterGlowOverlay(tintHex: filterKind?.hex)
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

            // 다이어리 열람 파장(warp) — 파장 링 + (겹친 별) 버스트(지도 굴절은 위 DiaryWarpMeshView).
            // 끝나면 멤버 수에 따라 상세/겹친 별 카드로 진입(Android DiaryOpenWarp 흐름 동일).
            if let w = openWarp {
                DiaryOpenWarpView(data: w) {
                    openWarp = nil
                    leftForDiary = true // 돌아와도 카메라 그대로(onAppear 재센터 생략)
                    if w.members.count > 1 {
                        cluster = ClusterSelection(members: w.members)
                    } else if let one = w.members.first {
                        selected = one
                    }
                }
            }

            // 하단 토스트(Android StaryToast 대응) — 100m 게이팅/위치 확인 안내
            if let t = toast {
                ToastView(text: t)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }

            // 집중(지도만 보기)에선 지도 위 모든 버튼을 숨긴다(Android MapUiState.mapOnly 대응).
            // MapChromeState 가 withAnimation 으로 바꾸므로 기본 전환(opacity)으로 들어갈 땐 천천히, 나올 땐 빠르게 페이드된다.
            if !chrome.mapOnly {
            // ── 좌상단 줌 버튼(+/−) — Android DiaryMap 대응(44pt, 남색빛 검정 원형) ──
            // 탭 = 한 단계 부드럽게, 꾹 누르면 손을 뗄 때까지 연속 줌(MapZoomHold — Android ZoomHoldButton 패리티).
            VStack(spacing: 10) {
                mapCircleLabel("plus", size: 44)
                    .zoomHoldGesture(direction: 1, size: 44, hold: zoomHold) {
                        zoomRequest = (1, zoomRequest.nonce + 1)
                    }
                mapCircleLabel("minus", size: 44)
                    .zoomHoldGesture(direction: -1, size: 44, hold: zoomHold) {
                        zoomRequest = (-1, zoomRequest.nonce + 1)
                    }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.top, 16)
            .padding(.leading, 16)

            // ── 첫 사용 안내 카드(프로필 → 첫 별 → 퀘스트 열쇠 → 남의 별 열기) — 화면 가운데(2026-10-22 — 필터 버튼 옆은 어색하다는 요청, Android 동일).
            //    `.quest` 는 카드 대신 RootView 의 "!" 스포트라이트.
            if let step = guideStep, step != .quest, !chrome.guideHidden.contains(step),
               !chrome.questsOpen, !chrome.coachMarkShowing, globeCenter == nil {
                FirstStarGuideCard(
                    step: step,
                    onAction: { guideAction(step) },
                    onClose: {
                        StaryAnalytics.log("guide_dismiss", ["step": step.rawValue])
                        withAnimation(.easeOut(duration: 0.2)) { _ = chrome.guideHidden.insert(step) }
                    }
                )
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                .id(step)
                .transition(.opacity.combined(with: .scale(scale: 0.85)))
                .onAppear { guideShown(step) }
            }

            // ── 우하단 열: 내 위치 → 별자리 토글 → 몰입 — Android DiaryMap 우하단 열 순서 동일.
            // (생성 FAB(56pt)은 MainTabView 소유라 그 위(16+56+12)에 얹는다 — Android 세로 나열과 동일한 시각 배치.)
            VStack(spacing: 12) {
                mapCircleButton("location.north.fill", size: 48) {
                    recenterNonce += 1
                }
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .preference(
                                key: CoachMarkPositionKey.self,
                                value: [
                                    CoachMarkAnchor.location:
                                        CGPoint(
                                            x: proxy.frame(in: .global).midX,
                                            y: proxy.frame(in: .global).midY
                                        )
                                ]
                            )
                    }
                )    // 별자리 토글 — 활성 시 민트(Android AutoAwesome tint 동일).
                mapCircleButton(
                    "sparkles",
                    size: 48,
                    tint: constellationOn ? Theme.mint : .white
                ) {
                    constellationOn.toggle()
                    if constellationOn { QuestStore.report(.constellation) }
                }
                .accessibilityLabel(locale.t(.mapConstellation))
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .preference(
                                key: CoachMarkPositionKey.self,
                                value: [
                                    CoachMarkAnchor.constellation:
                                        CGPoint(
                                            x: proxy.frame(in: .global).midX,
                                            y: proxy.frame(in: .global).midY
                                        )
                                ]
                            )
                    }
                )
                // 오늘의 퀘스트("!") — 예전 몰입 버튼 자리(2026-10-19). 집중 모드는 이제 가만히 두면 들어간다(MapChromeState).
                // 코치마크 앵커 키는 예전 이름(.eye) 그대로 — 자리(3번째 단계)가 같다. 비로그인이면 버튼 없음.
                if auth.uid != nil {
                    QuestMapButton { withAnimation(.easeOut(duration: 0.2)) { chrome.questsOpen = true } }
                    .background(
                        GeometryReader { proxy in
                            Color.clear
                                .preference(
                                    key: CoachMarkPositionKey.self,
                                    value: [
                                        CoachMarkAnchor.eye:
                                            CGPoint(
                                                x: proxy.frame(in: .global).midX,
                                                y: proxy.frame(in: .global).midY
                                            )
                                    ]
                                )
                        }
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.trailing, 16)
            .padding(.bottom, 84)

            // ── 좌하단 필터 스피드 다이얼 — Android MainListScreen 대응 ──
            filterSpeedDial
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 16)
                .padding(.bottom, 20)
            } // if !chrome.mapOnly


            // ── 하단 "우주에서 보기" 버튼 — 줌을 충분히 빼면 나타나고, 눌러야 글로브로 전환 ──
            // (Android: 0xEE111120 알약 + 0x9FB3E8 0.5 테두리, Public 아이콘 + 13sp 흰 0.9, 가로 18·세로 11, 하단 24)
            if let entry = globeButtonCenter, globeCenter == nil, !chrome.mapOnly {
                Button { enterGlobe(lat: entry.lat, lng: entry.lng) } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "globe.asia.australia.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.navyAccent)
                        Text(locale.t(.globeOpen))
                            .font(.minSans(13))
                            .foregroundStyle(Color.white.opacity(0.9))
                    }
                    .padding(.horizontal, 18).padding(.vertical, 11)
                    .background(Color(hex: 0x111120).opacity(0.93), in: Capsule())
                    .overlay(Capsule().strokeBorder(Theme.navyAccent.opacity(0.5), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 24)
                .transition(.opacity)
            }

            // ── 3D 행성(글로브) 오버레이 — 지도와 지도 위 버튼을 덮는다(상단바는 RootView 소속이라 그대로 보인다) ──
            if let center = globeCenter {
                GlobeScreen(
                    diaries: globeDiaries,
                    startLat: center.lat,
                    startLng: center.lng,
                    onRequestExit: { lat, lng in exitGlobe(lat: lat, lng: lng) }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea()
            }
            // 전환 스크림(검정 디졸브) — 지도 ↔ 글로브(Metal) 교체를 가린다.
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
            if let d = selected {
                // 웰컴 별 — Firestore 를 타는 DetailScreen 대신 게시물형 튜토리얼 화면(Android NavGraph 분기 패리티).
                if d.id == TutorialStarState.diaryId {
                    TutorialStarDetailScreen()
                } else {
                    DetailScreen(diary: d)
                }
            }
        }
        // 겹친 별 카드 뷰어 — 전체 화면 push(NavRoute.StarCluster 대응). 카드 탭 → 그 별의 상세로 이어서.
        .navigationDestination(isPresented: Binding(
            get: { cluster != nil }, set: { if !$0 { cluster = nil } }
        )) {
            if let selection = cluster {
                StarClusterView(
                    diaries: selection.members,
                    onOpenDiary: { diary in
                        diaryHandoff = true
                        cluster = nil
                        // pop 애니메이션과 겹치지 않도록 한 틱 뒤에 상세를 연다.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            selected = diary
                            diaryHandoff = false
                        }
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
        // 대기 중인 포커스 요청이 있는데 별 목록이 뒤늦게 도착/갱신되면 다시 시도(위 handleFocus 주석 참고).
        .onChange(of: store.diaries.count) { _ in
            if focus.pendingDiaryId != nil { handleFocus(focus.pendingDiaryId) }
        }
        .onChange(of: store.loading) { _ in
            if focus.pendingDiaryId != nil { handleFocus(focus.pendingDiaryId) }
        }
        // 웰컴 별 배치 — 실제 위치 fix 가 오면 그 근방에 1회(이미 놨거나 끝났으면 무시). Publisher 는 구독 시
        // 현재 값도 주므로 이미 fix 가 있는 상태로 지도가 떠도 바로 놓인다.
        // 로그인 상태면 **새 계정으로 확인됐을 때만** — 이전에 가입한 계정은 재설치여도 웰컴 별을 띄우지 않는다(ReturningAccountOnboarding).
        .onReceive(location.$coordinate) { c in
            guard !ScreenshotMode.isActive else { return } // 스크린샷엔 웰컴 별을 놓지 않는다
            if let c, auth.uid == nil || auth.returningAccount == false { tutorial.ensurePlaced(near: c) }
        }
        // 스크린샷 자동 촬영 장면(Core/ScreenshotMode.swift) — 별자리 선 켜기 / 별이 뜬 뒤 글로브 자동 진입.
        .task {
            switch ScreenshotMode.scene {
            case "constellation":
                constellationOn = true
            case "globe":
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                let c = location.coordinate
                    ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
                enterGlobe(lat: c.latitude, lng: c.longitude)
            default:
                break
            }
        }
        .onAppear {
            guideProgress.bind(auth.uid)
            chrome.guideStep = publishedGuideStep
        }
        .onChange(of: auth.uid) { guideProgress.bind($0) }
        .onChange(of: publishedGuideStep) { chrome.guideStep = $0 }
        .onChange(of: auth.returningAccount) { r in
            if r == false, let c = location.coordinate { tutorial.ensurePlaced(near: c) }
        }
        // 모의 위치 감지 시 1회 경고 — 조작된 좌표는 LocationManager 가 이미 거부한다(Android MainListScreen 패리티).
        .onChange(of: location.mockDetected) { detected in
            if detected { showToast(locale.t(.locationMockBlocked)) }
        }
        // 다른 탭에서 포커스 요청 → 지도 탭으로 전환되며 나타날 때 처리(숨김 동안 onChange 미수신 대비).
        .onAppear {
            // 하위 화면 → 지도(루트) 복귀: NavigationStack 루트는 파괴되지 않으므로(지도 유지)
            // 카메라만 내 위치로 옮긴다. 최초 진입, 포커스 요청 대기(아래 handleFocus 가
            // 카메라를 다룸 — 반드시 이 검사보다 뒤에 소비) 중엔 건너뛴다.
            // (Android MainScreen 라우트 전환 재센터 패리티)
            // 별(상세/카드)을 열러 나갔다 돌아온 경우는 보던 카메라 그대로 둔다.
            // ⚠️ 방금 포커스를 적용했으면 재센터하지 않는다. 알림 목록처럼 push 된 화면에서 요청이 오면, 이 화면이 가려진 채로
            //    onChange 가 먼저 돌아 요청을 소비할 수 있다 → 그러면 여기서 pendingDiaryId 가 nil 이라 "내 위치로"가 뒤따라 나가
            //    방금 간 별에서 내 위치로 되돌아간다("첫 별 알림을 눌러도 그 별로 안 간다" — 2026-10-14, 코드 분석으로 찾은 약점.
            //    Windows 라 실기기 재현은 못 했다. 같은 날 `focusNonce` 로 "같은 별 재요청 무시"도 함께 고쳤다).
            let justFocused = Date().timeIntervalSince(lastFocusAt) < 2.5
            if rootAppearedOnce, focus.pendingDiaryId == nil, !leftForDiary, !justFocused {
                recenterNonce += 1
            }
            // 여정이 끝났으면(상세·카드 모두 닫힘) 플래그 해제 — pop 바인딩 반영 뒤에 보도록 한 틱 미룬다.
            DispatchQueue.main.async {
                if selected == nil, cluster == nil, !diaryHandoff { leftForDiary = false }
            }
            // 지도가 뜨기 전에 이미 감지돼 있었으면 onChange 가 안 오므로 최초 1회만 여기서 안내.
            if !rootAppearedOnce, location.mockDetected {
                showToast(locale.t(.locationMockBlocked))
            }
            rootAppearedOnce = true
            chrome.mapVisible = true
            chrome.resetIdle()
            handleFocus(focus.pendingDiaryId)
            pioneer.start() // 개척 퀘스트 현황 구독(체크리스트 32)
        }
        // 다른 화면으로 나가면(지도가 가려짐) 글로브를 닫는다 — 가려진 채 렌더 루프가 도는 낭비 방지 +
        // "복귀하면 지도" 동작 유지(Android MainListScreen 의 mapVisible 감시와 동일).
        .onDisappear {
            chrome.mapVisible = false
            chrome.exitMapOnly()
            if globeCenter != nil {
                globeCenter = nil
                globeScrim = 0
                chrome.globeOpen = false
            }
        }
        // 개척 비콘 탭 → 퀘스트 안내(체크리스트 32)
        .staryInfoDialog(pioneerMessage ?? "", isPresented: Binding(
            get: { pioneerMessage != nil },
            set: { if !$0 { pioneerMessage = nil } }
        ))
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

    /// 포커스 요청 처리 — 대상 별 좌표로 카메라 이동 → 파동(물결) 1회.
    ///
    /// ⚠️ 대상 별이 아직 `store.diaries` 에 없을 수 있다 — 알림을 눌러 앱이 막 켜진 경우(목록 로드 전)가 대표적.
    ///    예전엔 여기서 조용히 `return` 하고 **요청도 소비하지 않아**, 목록이 뒤늦게 도착해도 다시 시도하는 곳이 없어
    ///    "알림을 눌러도 그 별로 안 간다"가 됐다. 지금은 ① 목록이 바뀔 때마다 [onChange] 가 재시도하고
    ///    ② 로드가 끝났는데도 없으면(최신 1000개 밖/삭제) Firestore 에서 직접 읽어 보고, 그래도 없으면 안내 후 소비한다.
    private func handleFocus(_ id: String?) {
        guard let id else { return }
        if let d = store.diaries.first(where: { $0.id == id }) {
            applyFocus(d)
            return
        }
        guard !store.loading, !focusFetching else { return }
        focusFetching = true
        Task { @MainActor in
            defer { focusFetching = false }
            guard focus.pendingDiaryId == id else { return } // 그 사이 다른 요청으로 바뀜
            if let d = await store.diary(id: id, viewerUid: auth.uid) {
                if focus.pendingDiaryId == id { applyFocus(d) }
            } else if focus.pendingDiaryId == id {
                focus.consume()
                showToast(locale.t(.notifDiaryGone))
            }
        }
    }

    private func applyFocus(_ d: Diary) {
        selected = nil // 열려 있던 상세 시트는 닫고 지도로
        focusTarget = CLLocationCoordinate2D(latitude: d.latitude, longitude: d.longitude)
        focusNonce += 1
        lastFocusAt = Date()

        // 파동 연출 1회 재생(다이어리에서 클릭한 느낌).
        warpColor = StarStyle.color(d.starColor)
        warpId += 1
        showWarp = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { showWarp = false }
        focus.consume()
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
/// (별 도감 StarLogScreen 에서도 같은 시트를 쓴다 — 2026-09-25 private 해제)
struct FriendFilterPicker: View {
    let friends: [Friend]
    let initial: Set<String>
    let onConfirm: (Set<String>) -> Void

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var locale = LocaleManager.shared
    /// 친구 이름은 친구 문서 스냅샷이 아니라 users/{uid} 의 현재 닉네임으로.
    @ObservedObject private var directory = UserDirectory.shared
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
                Text(directory.name(f.userId, fallback: f.userName))
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
        .watchUser(f.userId)
        .buttonStyle(.plain)
    }
}
