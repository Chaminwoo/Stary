import SwiftUI

/// 인증 게이트 — 로그인 상태에 따라 로그인/메인 탭을 전환.
struct RootView: View {
    @EnvironmentObject var auth: AuthManager

    var body: some View {
        Group {
            if auth.isSignedIn {
                MainTabView()
            } else {
                LoginView()
            }
        }
        .animation(.easeInOut, value: auth.isSignedIn)
    }
}

/// 메인 4-탭: 지도 / 목록 / 올리기 / 프로필.
struct MainTabView: View {
    @EnvironmentObject var auth: AuthManager
    @StateObject private var store = DiaryStore()
    @StateObject private var location = LocationManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            MapScreen()
                .tabItem { Label("지도", systemImage: "map") }
            ListScreen()
                .tabItem { Label("목록", systemImage: "list.star") }
            UploadScreen()
                .tabItem { Label("올리기", systemImage: "plus.circle.fill") }
            FriendsScreen()
                .tabItem { Label("친구", systemImage: "person.2.fill") }
            ProfileScreen()
                .tabItem { Label("프로필", systemImage: "person.crop.circle") }
        }
        .tint(Theme.mint)
        .environmentObject(store)
        .environmentObject(location)
        .onAppear {
            location.requestPermission()
            location.start()
            store.startIfNeeded(uid: auth.uid)
            MusicManager.shared.resume() // 로그인 후 메인 진입 시 배경음악 시작
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
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
}
