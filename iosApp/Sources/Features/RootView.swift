import SwiftUI

/// 인증 게이트 — 로그인 상태에 따라 로그인/메인 화면을 전환.
struct RootView: View {
    @EnvironmentObject var auth: AuthManager
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        Group {
            if auth.isSignedIn {
                MainTabView()
            } else {
                LoginView()
            }
        }
        .font(.minSans(16))   // 앱 기본 폰트(Android bodyLarge = MinSans 16sp 대응)
        // 시스템 글꼴 크기 상한 — 고정 높이 카드가 많아 그대로 두면 큰 글꼴에서 글자가 잘린다
        // (Android StaryResponsive.MAX_FONT_SCALE=1.15 대응).
        .dynamicTypeSize(...StaryResponsive.maxDynamicType)
        .animation(.easeInOut, value: auth.isSignedIn)
        .environmentObject(locale)
        .environment(\.locale, locale.swiftLocale)
        // 언어 변경 시 전체 트리를 다시 그린다(Android activity.recreate() 대응).
        .id(locale.language)
    }
}

/// 채팅 배너 탭 대상.
private struct ChatTarget: Identifiable {
    let friendId: String
    let friendName: String
    var id: String { friendId }
}

/// 드로어(좌측 메뉴)/FAB 에서 여는 하위 화면 — Android `NavRoute` 대응.
enum DrawerDest: Hashable {
    case myDiary, profile, achievements, music, friends, settings, notifications, upload
}

/// 메인 홈 — Android `MainScreen` 대응.
/// 상단바(햄버거·"지도"·알림 하트) + 지도 본문 + 글쓰기 FAB(민트→블루 그라데이션) + 좌측 드로어.
/// 하위 화면은 단일 NavigationStack push(Android 의 단일 NavHost 와 동일 구조).
struct MainTabView: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var locale: LocaleManager
    @StateObject private var store = DiaryStore()
    @StateObject private var location = LocationManager()
    @StateObject private var watcher = InAppWatcher()
    @StateObject private var viewed = ViewedStore()
    @StateObject private var blocks = BlockStore()
    /// 상단바 하트의 미열람 배지용(목록 화면과 별개 인스턴스여도 같은 쿼리라 일관).
    @StateObject private var notifications = NotificationsViewModel()
    @ObservedObject private var router = TabRouter.shared
    @ObservedObject private var focus = MapFocusStore.shared
    /// 푸시 알림 탭 → 채팅/상세/친구 화면 이동 요청(Android DeepLinkState 대응).
    @ObservedObject private var pushRouter = PushRouter.shared
    // 글로브/몰입 진입 시 상단바·FAB 를 숨긴다(#12).
    @ObservedObject private var chrome = MapChromeState.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var drawerOpen = false
    @State private var path = NavigationPath()
    @State private var chatTarget: ChatTarget?
    @State private var diaryTarget: Diary?
    // 일반 업적 해금 축하 팝업(Android AchievementUnlockWatcher 대응) — 큐 + 친구 수(스탯 계산용).
    @State private var friendsCount = 0
    @State private var friendsCountLoaded = false
    @State private var achievementQueue: [Achievement] = []

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                VStack(spacing: 0) {
                    if !chrome.chromeHidden { topBar }
                    MapScreen()
                }
                .background(Theme.background.ignoresSafeArea())

                if !chrome.chromeHidden { fab }

                drawerLayer

                InAppBannerHost()
                // 별 탄생 연출(34-8) — 업로드 성공 직후 지도 위에서 재생된다(터치 통과).
                StarBirthHost()

                if let ach = achievementQueue.first {
                    AchievementUnlockOverlay(achievement: ach) {
                        if !achievementQueue.isEmpty { achievementQueue.removeFirst() }
                    }
                }
            }
            // 루트(지도)는 커스텀 상단바를 쓰므로 시스템 내비바 숨김 — push 된 화면들은 시스템 내비바 사용
            // (전역 UINavigationBarAppearance 로 Android CenterAlignedTopAppBar 톤을 맞춘다 — StaryApp 참고).
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: DrawerDest.self) { destinationView($0) }
            // 인앱 배너 탭 → 채팅/상세 push (Android navController.navigate 대응).
            .navigationDestination(isPresented: Binding(
                get: { chatTarget != nil }, set: { if !$0 { chatTarget = nil } }
            )) {
                if let t = chatTarget {
                    ChatScreen(friendId: t.friendId, friendName: t.friendName, myUid: auth.uid ?? "")
                }
            }
            .navigationDestination(isPresented: Binding(
                get: { diaryTarget != nil }, set: { if !$0 { diaryTarget = nil } }
            )) {
                if let d = diaryTarget { DetailScreen(diary: d) }
            }
        }

        // 길찾기/포커스 요청 → 전부 pop 하고 지도(루트)로 (Android popUpTo Main 대응).
        .onChange(of: focus.pendingDiaryId) { id in
            if id != nil {
                path = NavigationPath()
                chatTarget = nil
                diaryTarget = nil
                drawerOpen = false
            }
        }
        // 푸시 알림 탭 → 해당 화면으로. onReceive 는 구독 시점의 현재 값도 받으므로
        // "종료 상태에서 알림을 눌러 실행"된 경우(뷰보다 먼저 설정된 요청)도 처리된다.
        .onReceive(pushRouter.$pending) { route in
            guard let route else { return }
            switch route {
            case .chat(let friendId, let friendName):
                path = NavigationPath()
                diaryTarget = nil
                chatTarget = ChatTarget(friendId: friendId, friendName: friendName)
            case .diary(let diaryId):
                // 상세 직행은 100m 열람 게이팅을 우회하므로 지도에서 그 별로 포커스(공유 딥링크와 동일 정책).
                path = NavigationPath()
                chatTarget = nil; diaryTarget = nil
                MapFocusStore.shared.request(diaryId: diaryId)
            case .friends:
                chatTarget = nil; diaryTarget = nil
                path = NavigationPath()
                path.append(DrawerDest.friends)
            }
            pushRouter.pending = nil
        }
        // 탭바 시절 go(tab) 호출부(딥링크/업로드 성공/프로필 버블) → 드로어 내비 목적지로 해석.
        .onChange(of: router.request.nonce) { _ in
            switch router.request.tab {
            case TabRouter.map: path = NavigationPath()
            case TabRouter.friends: path.append(DrawerDest.friends)
            case TabRouter.upload: path.append(DrawerDest.upload)
            case TabRouter.profile: path.append(DrawerDest.profile)
            default: path.append(DrawerDest.myDiary)
            }
        }
        .environmentObject(store)
        .environmentObject(location)
        .environmentObject(viewed)
        .environmentObject(blocks)
        .onAppear {
            location.requestPermission()
            location.start()
            store.startIfNeeded(uid: auth.uid)
            // 히든 업적 선점 현황 전역 구독 — 이름 옆 크리스탈 배지(34-4)가 어디서든 뜨도록.
            HiddenAchievementStore.shared.start()
            if let uid = auth.uid {
                viewed.start(uid: uid); blocks.start(uid: uid)
                notifications.start(ownerId: uid)
            }
            MusicManager.shared.resume() // 로그인 후 메인 진입 시 배경음악 시작
            startWatcher()
            loadFriendsCount()
            // 푸시 권한 요청 + fcmToken 기록(= 서버 발송 대상 등록). Android 로그인 직후 동작과 동일.
            PushManager.shared.setUser(auth.uid)
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
            PushManager.shared.setUser(newUid)
            if let uid = newUid {
                viewed.start(uid: uid); blocks.start(uid: uid)
                notifications.start(ownerId: uid)
            } else {
                viewed.stop(); blocks.stop(); notifications.stop()
            }
            startWatcher()
            friendsCountLoaded = false
            loadFriendsCount()

            // 로그인 전에 들어온 친구 초대 딥링크가 있으면 이제 리딤(체크리스트 31).
            Task { await InviteStore.redeemPendingIfPossible(uid: newUid) }
        }
        // 업적 해금 감시 — 내 통계(작성/좋아요/조회/친구/열람) 변화마다 재계산(내 것만 반영하는 Int 시그니처).
        .onChange(of: achievementSignature) { _ in syncAchievements() }
        .onChange(of: store.loading) { _ in syncAchievements() }
        .onChange(of: scenePhase) { phase in
            // 앱 백그라운드/복귀에 맞춰 배경음악 정지/이어재생(위치 보존)
            switch phase {
            case .active: MusicManager.shared.resume()
            case .background, .inactive: MusicManager.shared.pause()
            @unknown default: break
            }
        }
    }

    // MARK: - 상단 바 (Android CenterAlignedTopAppBar: 햄버거 / "지도" / 알림 하트)

    private var topBar: some View {
        HStack(spacing: 0) {
            Button {
                withAnimation(.easeOut(duration: 0.25)) { drawerOpen = true }
            } label: {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 22))
                    .foregroundStyle(Theme.textPrimary)
                    .frame(width: 48, height: 48)
                    .contentShape(Rectangle())
            }
            Spacer()
            Button {
                path.append(DrawerDest.notifications)
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "heart")
                        .font(.system(size: 22))
                        .foregroundStyle(Theme.textPrimary)
                    // 미열람 알림이 있으면 하트 우측 상단에 빨간 점(Android Badge 대응).
                    if notifications.unread > 0 {
                        Circle()
                            .fill(Color(hex: 0xFF3B30))
                            .frame(width: 7, height: 7)
                            .offset(x: 4, y: -4)
                    }
                }
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
            }
        }
        .overlay(
            // 좌우 버튼과 무관하게 정중앙 제목(Android CenterAligned 대응).
            Text(locale.t(.navMap))
                .font(.minSans(18, .semibold))
                .foregroundStyle(Theme.textPrimary)
        )
        .padding(.horizontal, 4)
        .frame(height: 56)
        .background(Theme.background)
    }

    // MARK: - 글쓰기 FAB (Android: 남색 그라데이션 56dp 원형 + 엠보스 테두리, 우하단)

    private var fab: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button {
                    path.append(DrawerDest.upload)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(
                            LinearGradient(colors: [Color(hex: 0x3A4676), Color(hex: 0x111936)],
                                           startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: Circle()
                        )
                        .raisedCosmicBorder()
                }
                .padding(.trailing, 16)
                .padding(.bottom, 16)
            }
        }
    }

    // MARK: - 드로어 (Android ModalNavigationDrawer: 0x111111 패널 + 우측 라운드 24 + 스크림 0.5)

    private var drawerLayer: some View {
        ZStack(alignment: .leading) {
            // 스크림 — 열려 있을 때만 탭을 받고, 탭하면 닫힌다.
            Color.black.opacity(drawerOpen ? 0.5 : 0)
                .ignoresSafeArea()
                .allowsHitTesting(drawerOpen)
                .onTapGesture { withAnimation(.easeOut(duration: 0.25)) { drawerOpen = false } }

            drawerPanel
                .offset(x: drawerOpen ? 0 : -320)
        }
        .animation(.easeOut(duration: 0.25), value: drawerOpen)
    }

    private var drawerPanel: some View {
        VStack(alignment: .leading, spacing: 4) {
            // 상단: "목록"(회색) + 우측 닫기(왼쪽 화살표) — Android 드로어 헤더 동일.
            HStack {
                Text(locale.t(.drawerList))
                    .font(.minSans(15, .semibold))
                    .foregroundStyle(Theme.textSecondary)
                Spacer()
                Button {
                    withAnimation(.easeOut(duration: 0.25)) { drawerOpen = false }
                } label: {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 18))
                        .foregroundStyle(Theme.textPrimary)
                        .frame(width: 44, height: 44)
                }
            }
            .padding(.leading, 12)
            .padding(.bottom, 3)

            drawerItem(locale.t(.navMyDiary), "book.closed") { open(.myDiary) }
            drawerItem(locale.t(.tabProfile), "person.fill") { open(.profile) }
            drawerItem(locale.t(.navAchievements), "trophy.fill") { open(.achievements) }
            drawerItem(locale.t(.navMusic), "music.note") { open(.music) }
            drawerItem(locale.t(.tabFriends), "person.2.fill") { open(.friends) }
            drawerItem(locale.t(.navSettings), "gearshape.fill") { open(.settings) }
            drawerItem(locale.t(.drawerLogout), "rectangle.portrait.and.arrow.right", danger: true) {
                withAnimation(.easeOut(duration: 0.25)) { drawerOpen = false }
                auth.signOut()
            }

            Spacer()
        }
        .padding(.horizontal, 12)
        // 패널이 화면 최상단까지 닿으므로(status bar 포함) 콘텐츠는 그 아래에서 시작.
        .padding(.top, 60)
        .padding(.bottom, 24)
        .frame(width: 300, alignment: .leading)
        .frame(maxHeight: .infinity)
        .background(
            Color(hex: 0x111111)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0,
                                                  bottomTrailingRadius: 24, topTrailingRadius: 24))
        )
        .ignoresSafeArea()
    }

    /// 드로어 항목 — Android NavigationDrawerItem(아이콘 22dp + 라벨 18sp) 대응.
    private func drawerItem(_ label: String, _ icon: String,
                            danger: Bool = false, action: @escaping () -> Void) -> some View {
        let color: Color = danger ? Color(hex: 0xFF6B6B) : Theme.textPrimary
        return Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundStyle(color)
                    .frame(width: 24)
                Text(label)
                    .font(.minSans(17, .semibold))
                    .foregroundStyle(color)
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 드로어 항목 탭 — 닫고 push.
    private func open(_ dest: DrawerDest) {
        withAnimation(.easeOut(duration: 0.25)) { drawerOpen = false }
        path.append(dest)
    }

    // MARK: - push 목적지 (Android NavGraph 대응)

    @ViewBuilder
    private func destinationView(_ dest: DrawerDest) -> some View {
        // iPad 에서 콘텐츠가 화면 폭만큼 늘어나지 않도록 상한 + 가운데 정렬(Android NavGraph 폭 상한 대응).
        // 지도(루트)는 여기 안 거치므로 계속 화면 전체를 쓴다.
        Group {
            switch dest {
            case .myDiary: MyStarsScreen()
            case .profile: ProfileScreen()
            case .achievements: AchievementsEntry()
            case .music: MusicScreen()
            case .friends: FriendsScreen()
            case .settings: SettingsScreen()
            case .notifications: NotificationsScreen()
            case .upload: UploadScreen()
            }
        }
        .staryContentWidth()
    }

    private func startWatcher() {
        guard let uid = auth.uid else { return }
        watcher.start(
            uid: uid,
            onOpenChat: { friendId, friendName in
                chatTarget = ChatTarget(friendId: friendId, friendName: friendName)
            },
            onOpenNotification: { n in
                // 친구 요청 알림은 다이어리가 없으므로 친구 화면(받은 요청)으로.
                if n.type == "FRIEND_REQUEST" {
                    path = NavigationPath()
                    path.append(DrawerDest.friends)
                    return
                }
                // 알림이 가리키는 다이어리를 현재 구독 목록에서 찾으면 상세를 push.
                if let d = store.diaries.first(where: { $0.id == n.diaryId }) {
                    diaryTarget = d
                }
            }
        )
    }

    // MARK: - 업적 해금 감시 (Android AchievementUnlockWatcher 대응)

    /// 내 통계 변화 감지용 경량 시그니처 — 내 다이어리 수·좋아요·조회 합 + 친구 수 + 열람 수.
    /// (남의 다이어리 변경엔 반응하지 않고, 스탯에 영향 주는 값만 바뀌면 갱신)
    private var achievementSignature: Int {
        guard let uid = auth.uid else { return 0 }
        let mine = store.mine(uid: uid)
        var h = Hasher()
        h.combine(mine.count)
        h.combine(mine.reduce(0) { $0 + $1.likeCount })
        h.combine(mine.reduce(0) { $0 + $1.viewCount })
        h.combine(friendsCount)
        h.combine(viewed.viewedIds.count)
        return h.finalize()
    }

    private func loadFriendsCount() {
        guard let uid = auth.uid else { return }
        Task {
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            friendsCount = snap?.documents.count ?? 0
            friendsCountLoaded = true
            syncAchievements()
        }
    }

    /// 현재 통계로 해금된 업적을 계산해, 저장된 기준선에 없던 새 업적만 큐에 넣어 팝업으로 보여준다.
    /// 최초 1회는 팝업 없이 기준선만 저장(이미 달성한 업적 제외). ⚠️ 데이터(다이어리+친구 수) 로드 후에만 실행해 오탐 방지.
    private func syncAchievements() {
        guard let uid = auth.uid, !store.loading, friendsCountLoaded else { return }
        let myDiaries = store.mine(uid: uid)
        let myIds = Set(myDiaries.compactMap { $0.id })
        let othersViewed = viewed.viewedIds.subtracting(myIds).count
        let stats = Achievements.computeStats(diaries: myDiaries, friendsCount: friendsCount, viewedCount: othersViewed)
        let unlocked = Achievements.unlockedIds(stats)
        let key = "ach_announced_\(uid)"
        let defaults = UserDefaults.standard
        guard let stored = defaults.stringArray(forKey: key) else {
            defaults.set(Array(unlocked), forKey: key)   // 최초 기준선 — 팝업 없음
            return
        }
        let newIds = unlocked.subtracting(Set(stored))
        guard !newIds.isEmpty else { return }
        let queuedIds = Set(achievementQueue.map { $0.id })
        let newAch = Achievements.all.filter { newIds.contains($0.id) && !queuedIds.contains($0.id) }
        achievementQueue.append(contentsOf: newAch)
        defaults.set(Array(Set(stored).union(unlocked)), forKey: key)
    }
}

/// 드로어에서 업적 화면 직접 진입용 — 장착 칭호 상태를 자체 보유(AchievementsScreen 이 서버에서 로드).
private struct AchievementsEntry: View {
    @State private var equippedTitleId: String?
    var body: some View {
        AchievementsScreen(equippedTitleId: $equippedTitleId)
    }
}

/// 일반 업적 해금 축하 팝업 — Android `AchievementUnlockDialog` 패리티(트로피 + 스프링 팝 + 보상 pill).
private struct AchievementUnlockOverlay: View {
    let achievement: Achievement
    let onDismiss: () -> Void
    @ObservedObject private var locale = LocaleManager.shared
    @State private var pop: CGFloat = 0.6

    private var displayName: String {
        LocalizedNames.title(achievement.id, fallback: achievement.name) ?? achievement.name
    }
    private var rewardText: String {
        switch achievement.reward {
        case .title(let n): return "칭호 «\(n)» 획득"
        case .shape:        return "새 별 모양 해금"
        case .color:        return "새 별 색 해금"
        }
    }
    private var grad: LinearGradient {
        LinearGradient(colors: [Color(hex: 0x3B82F6), Color(hex: 0x1E3A8A)],
                       startPoint: .leading, endPoint: .trailing)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture { onDismiss() }
            VStack(spacing: 0) {
                Image(systemName: "trophy.fill").font(.system(size: 46)).foregroundStyle(Theme.navyAccent)
                Spacer().frame(height: 14)
                Text("업적 달성!").font(.minSans(15)).foregroundStyle(Theme.navyAccent)
                Spacer().frame(height: 8)
                Text(displayName).font(.minSans(22)).foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 6)
                Text(achievement.condition).font(.minSans(13))
                    .foregroundStyle(Theme.textPrimary.opacity(0.6)).multilineTextAlignment(.center)
                Spacer().frame(height: 16)
                Text(rewardText).font(.minSans(13)).foregroundStyle(Theme.navyAccent)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .background(Theme.navyAccent.opacity(0.12), in: Capsule())
                    .overlay(Capsule().stroke(Theme.navyAccent.opacity(0.4), lineWidth: 1))
                Spacer().frame(height: 22)
                Button(action: onDismiss) {
                    Text(locale.t(.commonOk)).font(.minSans(15)).foregroundStyle(Color(hex: 0x0D0D0D))
                        .frame(maxWidth: .infinity).padding(.vertical, 13)
                        .background(grad, in: RoundedRectangle(cornerRadius: 14))
                }
            }
            .padding(.horizontal, 26).padding(.vertical, 30)
            .frame(maxWidth: 330)
            .background(Color(hex: 0x14181C), in: RoundedRectangle(cornerRadius: 24))
            .overlay(RoundedRectangle(cornerRadius: 24).stroke(grad, lineWidth: 1.5))
            .padding(.horizontal, 32)
            .scaleEffect(pop)
            .opacity(Double(min(pop / 0.6, 1)))
        }
        .onAppear { withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) { pop = 1 } }
    }
}