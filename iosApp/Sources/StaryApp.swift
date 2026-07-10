import FirebaseCore
import GoogleSignIn
import SwiftUI

/// iOS 앱 진입점.
/// Firebase 초기화 + 구글 로그인 콜백 처리 후 인증 게이트(RootView)를 표시한다.
@main
struct StaryApp: App {
    @StateObject private var auth = AuthManager()

    init() {
        // GoogleService-Info.plist 가 번들에 있을 때만 구성(CI 시뮬레이터 빌드 등 없을 때 크래시 방지).
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    // 공유 랜딩 딥링크(stary://diary/{id}) → 지도 탭 전환 + 그 별로 포커스(체크리스트 30).
                    if url.scheme == AppConfig.deepLinkScheme, url.host == AppConfig.deepLinkHostDiary {
                        let id = url.lastPathComponent
                        if !id.isEmpty, id != "/" {
                            TabRouter.shared.go(TabRouter.map)
                            MapFocusStore.shared.request(diaryId: id)
                        }
                        return
                    }
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
