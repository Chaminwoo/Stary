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

        // Unity Ads(보상형) — 100m 밖 게시물 잠금 해제. SDK 미연결 동안엔 아무 일도 하지 않는다.
        // (Android StaryApplication 의 UnityAdsManager.init 패리티)
        // ⚠️ `MainActor.assumeIsolated` 는 iOS 17+ 라 배포 타깃(16.0)에서 못 쓴다 — Task 로 넘긴다.
        Task { @MainActor in AdsManager.shared.initialize() }

        // 키보드 바깥 탭으로 닫기(iOS 전용 — Core/KeyboardDismissOnTap.swift).
        Task { @MainActor in KeyboardDismissOnTap.shared.start() }

        Self.configureNavigationBarAppearance()
    }

    /// push 된 모든 화면의 시스템 내비바를 Android CenterAlignedTopAppBar 톤으로 통일 —
    /// **반투명 검정 + 블러**(화면 배경이 비쳐 이어져 보이게) + 구분선 없음 +
    /// 제목 **MinSans 18 SemiBold**/0xF0F0F0 + 뒤로가기 틴트 0xF0F0F0.
    /// (Android MainScreen 의 상단바: `TopBarScrim` 그라데이션 위에 같은 제목 스타일)
    private static func configureNavigationBarAppearance() {
        let appearance = UINavigationBarAppearance()
        // 화면 배경(ScreenBackground 는 ignoresSafeArea)이 내비바 뒤로 올라와 비친다.
        appearance.configureWithTransparentBackground()
        appearance.backgroundEffect = UIBlurEffect(style: .systemUltraThinMaterialDark)
        appearance.backgroundColor = UIColor(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0, alpha: 0.55)
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

    /// 앱 링크 해석 → (종류 host, id). 구글 로그인 콜백 등 다른 URL 이면 nil.
    ///  - 커스텀 스킴: `stary://diary/{id}` · `stary://invite/{uid}` (웹 랜딩의 "앱에서 열기" 버튼)
    ///  - 유니버설 링크: `https://{shareHost}/s/{id}` · `/i/{uid}` — 카톡/문자에서 공유 링크를 누르면 웹을 거치지 않고
    ///    앱이 바로 열린다(Android App Links 패리티). 동작하려면 Associated Domains 권한 + 서버의 apple-app-site-association
    ///    (web/ 의 팀 ID)가 맞아야 한다 — docs/code/10-friends-chat.md "iOS 초대 링크" 참고.
    private static func appLink(_ url: URL) -> (host: String, id: String)? {
        if url.scheme == AppConfig.deepLinkScheme, let host = url.host {
            return (host, url.lastPathComponent)
        }
        if url.scheme == "https", url.host == AppConfig.shareHost {
            let parts = url.pathComponents.filter { $0 != "/" }
            guard parts.count >= 2 else { return nil }
            switch parts[0] {
            case AppConfig.sharePathDiary: return (AppConfig.deepLinkHostDiary, parts[1])
            case AppConfig.sharePathInvite: return (AppConfig.deepLinkHostInvite, parts[1])
            default: return nil
            }
        }
        return nil
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    if let link = Self.appLink(url) {
                        guard !link.id.isEmpty, link.id != "/" else { return }
                        switch link.host {
                        case AppConfig.deepLinkHostDiary:
                            // 공유 랜딩 딥링크 → 지도 탭 전환 + 그 별로 포커스(체크리스트 30).
                            TabRouter.shared.go(TabRouter.map)
                            MapFocusStore.shared.request(diaryId: link.id)
                        case AppConfig.deepLinkHostInvite:
                            // 친구 초대 → 리딤(비로그인이면 보관해 두었다가 로그인 후 처리, 체크리스트 31).
                            InviteStore.handleDeepLink(inviterId: link.id)
                        default:
                            break
                        }
                        return
                    }
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
