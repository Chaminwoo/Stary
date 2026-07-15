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
        .font(.poorStory(16))   // 앱 기본 폰트(Android bodyLarge=PoorStory 대응)
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
    @Environment(\.scenePhase) private var scenePhase

    @State private var drawerOpen = false
    @State private var path = NavigationPath()
    @State private var chatTarget: ChatTarget?
    @State private var diaryTarget: Diary?

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                VStack(spacing: 0) {
                    topBar
                    MapScreen()
                }
                .background(Theme.background.ignoresSafeArea())

                fab

                drawerLayer

                InAppBannerHost()
                // 별 탄생 연출(34-8) — 업로드 성공 직후 지도 위에서 재생된다(터치 통과).
                StarBirthHost()
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
        // 근처 미조회 별 발견 알림(체크리스트 33) — 실제 위치 fix 갱신마다 검사(빈도 제한은 내부에서).
        .onReceive(location.$coordinate) { coord in
            guard let coord else { return }
            NearbyStarAlert.check(me: coord, diaries: store.diaries,
                                  viewedIds: viewed.viewedIds, myUserId: auth.uid)
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
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
            if let uid = newUid {
                viewed.start(uid: uid); blocks.start(uid: uid)
                notifications.start(ownerId: uid)
            } else {
                viewed.stop(); blocks.stop(); notifications.stop()
            }
            startWatcher()
            // 로그인 전에 들어온 친구 초대 딥링크가 있으면 이제 리딤(체크리스트 31).
            Task { await InviteStore.redeemPendingIfPossible(uid: newUid) }
        }
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
                .font(.poorStory(20))
                .foregroundStyle(Theme.textPrimary)
        )
        .padding(.horizontal, 4)
        .frame(height: 56)
        .background(Theme.background)
    }

    // MARK: - 글쓰기 FAB (Android: 민트→블루 그라데이션 56dp 원형, 우하단)

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
                            LinearGradient(colors: [Theme.mint, Theme.mintBlue],
                                           startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: Circle()
                        )
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
                    .font(.poorStory(20))
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
                    .font(.poorStory(18))
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

    private func startWatcher() {
        guard let uid = auth.uid else { return }
        watcher.start(
            uid: uid,
            onOpenChat: { friendId, friendName in
                chatTarget = ChatTarget(friendId: friendId, friendName: friendName)
            },
            onOpenNotification: { n in
                // 알림이 가리키는 다이어리를 현재 구독 목록에서 찾으면 상세를 push.
                if let d = store.diaries.first(where: { $0.id == n.diaryId }) {
                    diaryTarget = d
                }
            }
        )
    }
}

/// 드로어에서 업적 화면 직접 진입용 — 장착 칭호 상태를 자체 보유(AchievementsScreen 이 서버에서 로드).
private struct AchievementsEntry: View {
    @State private var equippedTitleId: String?
    var body: some View {
        AchievementsScreen(equippedTitleId: $equippedTitleId)
    }
}
