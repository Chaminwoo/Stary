import FirebaseCore
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions:
            [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {

        // StaryApp.init 이 먼저 돌면 이미 설정돼 있다(순서에 의존하지 않도록 방어).
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        // 푸시(FCM/APNs) 델리게이트 연결. 권한 요청/토큰 저장은 로그인 후 PushManager.setUser 에서.
        PushManager.shared.configure()

        return true
    }

    /// APNs 등록 성공 → FCM 에 기기 토큰 연결(이게 없으면 FCM 토큰이 발급되지 않는다).
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushManager.shared.setAPNsToken(deviceToken)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // 시뮬레이터/권한 거부 등 — 푸시만 비활성, 앱 흐름은 그대로.
        print("⚠️ APNs 등록 실패: \(error.localizedDescription)")
    }
}
