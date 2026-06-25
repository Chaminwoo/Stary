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

    var body: some View {
        TabView {
            MapScreen()
                .tabItem { Label("지도", systemImage: "map") }
            ListScreen()
                .tabItem { Label("목록", systemImage: "list.star") }
            UploadScreen()
                .tabItem { Label("올리기", systemImage: "plus.circle.fill") }
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
        }
        .onChange(of: auth.uid) { newUid in
            store.startIfNeeded(uid: newUid)
        }
    }
}
