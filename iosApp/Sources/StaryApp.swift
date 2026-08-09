import GoogleSignIn
import SwiftUI
import UIKit
import FirebaseCore
/// iOS 앱 진입점.
/// Firebase 초기화 + 구글 로그인 콜백 처리 후 인증 게이트(RootView)를 표시한다.
@main
struct StaryApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    private var appDelegate
    
    @StateObject private var auth = AuthManager()
    
    init() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }

        print("🔥 Firebase 초기화 성공")

        Self.configureNavigationBarAppearance()
    }

    /// push 된 모든 화면의 시스템 내비바를 Android CenterAlignedTopAppBar 톤으로 통일 —
    /// 배경 0x0D0D0D 불투명 + 구분선 없음 + 제목 **MinSans 18 SemiBold**/0xF0F0F0 + 뒤로가기 틴트 0xF0F0F0.
    /// (Android MainScreen 의 상단바 제목: fontFamily=MinSans, 18.sp, FontWeight.SemiBold)
    private static func configureNavigationBarAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0, alpha: 1)
        appearance.shadowColor = .clear
        let titleColor = UIColor(red: 0xF0 / 255.0, green: 0xF0 / 255.0, blue: 0xF0 / 255.0, alpha: 1)
        let titleFont = UIFont.minSans(18, .semibold)
        appearance.titleTextAttributes = [.foregroundColor: titleColor, .font: titleFont]
        appearance.largeTitleTextAttributes = [.foregroundColor: titleColor]
        // 뒤로가기 버튼 라벨도 같은 폰트로(기본 SF 가 섞이지 않게).
        let buttonAppearance = UIBarButtonItemAppearance()
        buttonAppearance.normal.titleTextAttributes = [.foregroundColor: titleColor, .font: UIFont.minSans(16)]
        appearance.buttonAppearance = buttonAppearance
        appearance.backButtonAppearance = buttonAppearance
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().tintColor = titleColor

        // 세그먼티드 컨트롤(업로드 화면 공개범위)도 같은 서체로 — UIKit 이 그리는 위젯이라
        // SwiftUI 의 .font 가 닿지 않는다(여기서 안 맞추면 이 칩만 시스템 폰트로 튄다).
        let segmentFont = UIFont.minSans(13)
        UISegmentedControl.appearance().setTitleTextAttributes(
            [.font: segmentFont, .foregroundColor: titleColor], for: .normal)
        UISegmentedControl.appearance().setTitleTextAttributes(
            [.font: UIFont.minSans(13, .semibold), .foregroundColor: UIColor.black], for: .selected)
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
                    // 친구 초대 딥링크(stary://invite/{uid}) → 리딤(비로그인이면 보관, 체크리스트 31).
                    if url.scheme == AppConfig.deepLinkScheme, url.host == AppConfig.deepLinkHostInvite {
                        let id = url.lastPathComponent
                        if !id.isEmpty, id != "/" { InviteStore.handleDeepLink(inviterId: id) }
                        return
                    }
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
