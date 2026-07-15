import SwiftUI

/// 인증 게이트 — 로그인 상태에 따라 로그인/메인 탭을 전환.
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

/// 메인 5-탭: 지도 / 목록 / 올리기 / 친구 / 프로필.
struct MainTabView: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var locale: LocaleManager
    @StateObject private var store = DiaryStore()
    @StateObject private var location = LocationManager()
    @StateObject private var watcher = InAppWatcher()
    @StateObject private var viewed = ViewedStore()
    @StateObject private var blocks = BlockStore()
    @ObservedObject private var router = TabRouter.shared
    @ObservedObject private var focus = MapFocusStore.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var chatTarget: ChatTarget?
    @State private var diaryTarget: Diary?

    var body: some View {
        ZStack(alignment: .top) {
            TabView(selection: $router.selected) {
                MapScreen()
                    .tabItem { Label(locale.t(.tabMap), systemImage: "map") }
                    .tag(TabRouter.map)
                ListScreen()
                    .tabItem { Label(locale.t(.tabList), systemImage: "list.star") }
                    .tag(TabRouter.list)
                UploadScreen()
                    .tabItem { Label(locale.t(.tabUpload), systemImage: "plus.circle.fill") }
                    .tag(TabRouter.upload)
                FriendsScreen()
                    .tabItem { Label(locale.t(.tabFriends), systemImage: "person.2.fill") }
                    .tag(TabRouter.friends)
                ProfileScreen()
                    .tabItem { Label(locale.t(.tabProfile), systemImage: "person.crop.circle") }
                    .tag(TabRouter.profile)
            }
            .tint(Theme.mint)

            InAppBannerHost()
            // 별 탄생 연출(34-8) — 업로드 성공 직후 지도 탭 위에서 재생된다(터치 통과).
            StarBirthHost()
        }
        // 근처 미조회 별 발견 알림(체크리스트 33) — 실제 위치 fix 갱신마다 검사(빈도 제한은 내부에서).
        .onReceive(location.$coordinate) { coord in
            guard let coord else { return }
            NearbyStarAlert.check(me: coord, diaries: store.diaries,
                                  viewedIds: viewed.viewedIds, myUserId: auth.uid)
        }
        // 길찾기/포커스 요청이 들어오면 지도 탭으로 전환하고, 위에 떠 있던 시트(채팅/상세)는 닫는다.
        .onChange(of: focus.pendingDiaryId) { id in
            if id != nil {
                router.selected = TabRouter.map
                chatTarget = nil
                diaryTarget = nil
            }
        }
        .environmentObject(store)
        .environmentObject(location)
        .environmentObject(viewed)
        .environmentObject(blocks)
        .sheet(item: $chatTarget) { t in
            NavigationStack { ChatScreen(friendId: t.friendId, friendName: t.friendName, myUid: auth.uid ?? "") }
                .environmentObject(auth)
        }
        .sheet(item: $diaryTarget) { d in
            NavigationStack { DetailScreen(diary: d) }
                .environmentObject(auth).environmentObject(store).environmentObject(location)
        }
        .onAppear {
            location.requestPermission()
            location.start()
            store.startIfNeeded(uid: auth.uid)
            // 히든 업적 선점 현황 전역 구독 — 이름 옆 크리스탈 배지(34-4)가 어디서든 뜨도록.
            HiddenAchievementStore.shared.start()
            if let uid = auth.uid { viewed.start(uid: uid); blocks.start(uid: uid) }
            MusicManager.shared.resume() // 로그인 후 메인 진입 시 배경음악 시작
            startWatcher()
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
            if let uid = newUid { viewed.start(uid: uid); blocks.start(uid: uid) }
            else { viewed.stop(); blocks.stop() }
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

    private func startWatcher() {
        guard let uid = auth.uid else { return }
        watcher.start(
            uid: uid,
            onOpenChat: { friendId, friendName in
                chatTarget = ChatTarget(friendId: friendId, friendName: friendName)
            },
            onOpenNotification: { n in
                // 알림이 가리키는 다이어리를 현재 구독 목록에서 찾으면 상세를 띄운다.
                if let d = store.diaries.first(where: { $0.id == n.diaryId }) {
                    diaryTarget = d
                }
            }
        )
    }
}
