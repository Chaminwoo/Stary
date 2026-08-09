import FirebaseAuth
import FirebaseCore
import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

/// 푸시 알림 탭 → 이동할 화면. (Android `DeepLinkState` 대응)
enum PushRoute: Equatable {
    case chat(friendId: String, friendName: String)
    case diary(String)
    case friends
}

/// 푸시 탭 라우팅 요청 보관 — RootView 가 관찰해 push 한다.
/// (MapFocusStore/TabRouter 와 같은 정책: 메인 스레드에서만 변경하고 actor 격리는 두지 않는다 —
///  비격리 델리게이트 콜백에서도 호출할 수 있어야 하기 때문.)
final class PushRouter: ObservableObject {
    static let shared = PushRouter()
    private init() {}

    /// 처리 후 nil 로 되돌린다(같은 알림으로 두 번 이동하지 않게).
    @Published var pending: PushRoute?

    func request(_ route: PushRoute) {
        DispatchQueue.main.async { self.pending = route }
    }
}

/**
 * FCM(APNs) 푸시 등록/수신 — Android `StaryMessagingService` + `GoogleAuthHelper.syncFcmToken` 대응.
 *
 * 흐름:
 *  1. 앱 시작 → [configure] 로 델리게이트 연결(AppDelegate).
 *  2. 로그인/세션 복원 → [setUser] → 권한 요청 + APNs 등록 + users/{uid}.fcmToken 기록.
 *     (서버 Cloud Functions 가 이 토큰으로 발송 — 토큰이 없으면 iOS 는 아무 푸시도 못 받는다.)
 *  3. 전면 수신 → 시스템 배너 억제(인앱 배너 InAppWatcher 가 담당 — Android 와 동일하게 이중 표시 방지).
 *  4. 알림 탭 → [PushRouter] 에 이동 요청.
 *
 * ⚠️ 실제 발송에는 Firebase 콘솔에 **APNs 인증 키(.p8)** 등록 + 앱에 Push Notifications 권한
 *    (entitlements aps-environment)이 필요하다. 자세한 건 docs/PROJECT_NOTES.md 참고.
 */
final class PushManager: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    static let shared = PushManager()

    private var appUserId: String?
    private var fcmToken: String?
    private var didRequestAuthorization = false

    /// 앱 시작 직후(AppDelegate) 1회 — 델리게이트만 연결한다(권한 요청은 로그인 후 [setUser]).
    func configure() {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
    }

    /// 로그인/세션 복원/로그아웃 시 호출. uid = appUserId(Google sub, 익명은 FirebaseAuth uid).
    func setUser(_ uid: String?) {
        appUserId = uid
        guard uid != nil else { return }
        requestAuthorizationIfNeeded()
        saveTokenIfPossible()
    }

    /// 알림 권한 요청 + APNs 등록. (Android 의 POST_NOTIFICATIONS 요청 대응 — 로그인 후 1회)
    private func requestAuthorizationIfNeeded() {
        guard !didRequestAuthorization else { return }
        didRequestAuthorization = true
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            guard granted else {
                // 권한 거부 = 서버가 보내도 기기에 안 뜬다(설정 앱에서 켜야 함).
                print("⚠️ 알림 권한 거부됨 — 푸시 미수신 \(error?.localizedDescription ?? "")")
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    /// APNs 기기 토큰 → FCM 에 연결(AppDelegate 에서 전달). 이 연결 없이는 FCM 토큰이 발급되지 않는다.
    func setAPNsToken(_ deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
        // 델리게이트 콜백만 믿지 않고 여기서도 한 번 당겨온다(등록 순서에 따라 콜백이 이미 지나갔을 수 있음).
        fetchTokenAndSave()
    }

    /// 현재 FCM 토큰을 명시적으로 조회해 저장. 실패 사유를 콘솔에 남긴다(진단용).
    private func fetchTokenAndSave() {
        Messaging.messaging().token { [weak self] token, error in
            if let error {
                // APNs 미등록/인증 키 미설정이면 여기서 실패한다.
                print("⚠️ FCM 토큰 발급 실패: \(error.localizedDescription)")
                return
            }
            guard let token, !token.isEmpty else { return }
            self?.fcmToken = token
            self?.saveTokenIfPossible()
        }
    }

    // MARK: - MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken token: String?) {
        fcmToken = token
        saveTokenIfPossible()
    }

    /// users/{uid} 에 fcmToken + authUid 기록(Android syncFcmToken 과 동일 필드).
    /// 이 문서에 fcmToken 이 없으면 서버(Cloud Functions)는 그 사용자를 **조용히 건너뛴다**
    /// → "푸시가 안 온다"의 1순위 확인 지점.
    private func saveTokenIfPossible() {
        guard let uid = appUserId, !uid.isEmpty,
              let token = fcmToken, !token.isEmpty else { return }
        let authUid = Auth.auth().currentUser?.uid ?? ""
        Task {
            do {
                try await FirestoreService.users.document(uid).setData([
                    "fcmToken": token,
                    "authUid": authUid,
                ], merge: true)
                print("✅ fcmToken 저장 완료 users/\(uid) …\(token.suffix(8))")
            } catch {
                print("⚠️ fcmToken 저장 실패: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// 전면 수신 — 시스템 배너를 띄우지 않는다(인앱 배너가 이미 같은 내용을 보여줌, Android 와 동일 정책).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([])
    }

    /// 알림 탭 — data 페이로드로 이동 대상 결정(Cloud Functions 가 보내는 키와 동일).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        let type = info["type"] as? String ?? ""
        let chatFriendId = info["chatFriendId"] as? String ?? ""
        let diaryId = info["diaryId"] as? String ?? ""

        if !chatFriendId.isEmpty {
            PushRouter.shared.request(.chat(friendId: chatFriendId,
                                            friendName: info["chatFriendName"] as? String ?? ""))
        } else if type == "FRIEND_REQUEST" {
            PushRouter.shared.request(.friends)
        } else if !diaryId.isEmpty {
            PushRouter.shared.request(.diary(diaryId))
        }
        completionHandler()
    }
}
