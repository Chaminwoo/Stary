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
    @Environment(\.scenePhase) private var scenePhase

    @State private var chatTarget: ChatTarget?
    @State private var diaryTarget: Diary?

    var body: some View {
        ZStack(alignment: .top) {
            TabView {
                MapScreen()
                    .tabItem { Label(locale.t(.tabMap), systemImage: "map") }
                ListScreen()
                    .tabItem { Label(locale.t(.tabList), systemImage: "list.star") }
                UploadScreen()
                    .tabItem { Label(locale.t(.tabUpload), systemImage: "plus.circle.fill") }
                FriendsScreen()
                    .tabItem { Label(locale.t(.tabFriends), systemImage: "person.2.fill") }
                ProfileScreen()
                    .tabItem { Label(locale.t(.tabProfile), systemImage: "person.crop.circle") }
            }
            .tint(Theme.mint)

            InAppBannerHost()
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
            if let uid = auth.uid { viewed.start(uid: uid); blocks.start(uid: uid) }
            MusicManager.shared.resume() // 로그인 후 메인 진입 시 배경음악 시작
            startWatcher()
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
            if let uid = newUid { viewed.start(uid: uid); blocks.start(uid: uid) }
            else { viewed.stop(); blocks.stop() }
            startWatcher()
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
