import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

/// 푸시 알림 탭 → 이동할 화면. (Android `DeepLinkState` 대응)
enum PushRoute: Equatable {
    case chat(friendId: String, friendName: String)
    /// 지도에서 그 별로 카메라 이동 + 파장(친구 새 글·첫 별 공지) — Android `MapFocusState` 대응.
    case diary(String)
    /// 상세 화면 직행(내 글에 달린 좋아요·댓글) — Android `navigateToDetail` 대응.
    case diaryDetail(String)
    case friends
    /// 일일 알림(오늘 기록 유도) 탭 → 업로드 화면. Android `EXTRA_OPEN_UPLOAD` 대응.
    case upload
}

extension PushRoute {
    /// 인앱 알림(알림 목록 행 · 상단 배너) 탭 → 이동 대상. Android `NotificationScreen` 의 onClick 과 같은 분기.
    ///  - 친구 요청 → 친구 화면 / 다이어리 없음 → 이동 없음(nil)
    ///  - 친구 새 글 · 첫 별 공지 → 지도에서 그 별로(카메라 + 파장) / 그 외(좋아요·댓글) → 상세
    static func from(_ n: AppNotification) -> PushRoute? {
        if n.type == "FRIEND_REQUEST" { return .friends }
        guard !n.diaryId.isEmpty else { return nil }
        if n.type == "FRIEND_POST" || n.type == "FIRST_STAR" { return .diary(n.diaryId) }
        return .diaryDetail(n.diaryId)
    }
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
    /// APNs 등록 실패 재시도 횟수 / 토큰 저장 실패 재시도 횟수(성공하면 0 으로).
    private var registerRetries = 0
    private var saveRetries = 0
    /// 마지막으로 토큰을 서버에 확인 저장한 시각 — 포그라운드 복귀 때마다 쓰기가 나가지 않게 간격을 둔다.
    private var lastSavedAt: Date?

    /// 앱 시작 직후(AppDelegate) 1회 — 델리게이트만 연결한다(권한 요청은 로그인 후 [setUser]).
    func configure() {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
    }

    /// 로그인/세션 복원/로그아웃 시 호출. uid = appUserId(Google sub, 익명은 FirebaseAuth uid).
    func setUser(_ uid: String?) {
        appUserId = uid
        guard uid != nil else { return }
        // 계정이 바뀌었을 수 있다 → 저장 간격(10분)을 무시하고 바로 이 계정에 토큰을 기록한다(refreshRegistration 이 저장까지 한다).
        lastSavedAt = nil
        refreshRegistration()
    }

    /// 앱이 활성화될 때마다(RootView scenePhase) 호출 — 알림 권한 상태를 **다시 읽고** APNs 등록을 맞춘다.
    ///
    /// ⚠️ 예전엔 프로세스당 한 번(권한 팝업 응답 직후)만 `registerForRemoteNotifications` 를 불러서,
    ///    설정 앱에서 알림을 켜고 돌아와도(앱이 죽지 않았다면) 등록이 안 돼 **다시 켤 때까지 푸시가 안 왔다.**
    ///    또 토큰 문서가 서버에서 정리(만료 판정)돼도 앱이 계속 켜져 있으면 되살아날 길이 없었다 → 일정 간격으로 다시 저장한다.
    func refreshRegistration() {
        guard appUserId != nil else { return }
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            let status = settings.authorizationStatus
            DispatchQueue.main.async { self?.applyAuthorizationStatus(status) }
        }
        // 토큰이 이미 있으면 (간격을 두고) 서버 문서가 살아 있도록 다시 저장.
        if let last = lastSavedAt, Date().timeIntervalSince(last) < 600 { return }
        saveTokenIfPossible()
    }

    private func applyAuthorizationStatus(_ status: UNAuthorizationStatus) {
        switch status {
        case .notDetermined:
            requestAuthorizationIfNeeded()
        case .authorized, .provisional, .ephemeral:
            // 이미 허용됨 — 매번 등록을 불러 둔다(토큰이 바뀌었거나 방금 설정에서 켠 경우를 모두 커버, 비용 없음).
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        case .denied:
            // 권한 거부 = 서버가 보내도 기기에 안 뜬다(설정 앱에서 켜야 함). 설정 화면이 안내 행을 보여 준다.
            print("⚠️ 알림 권한이 꺼져 있음 — 설정 앱에서 켜야 푸시가 온다")
        @unknown default:
            break
        }
    }

    /// 시스템 알림 권한이 꺼져 있는가 — 설정 화면의 "알림이 꺼져 있어요" 안내 행용.
    static func isSystemPushDenied() async -> Bool {
        await withCheckedContinuation { cont in
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                cont.resume(returning: settings.authorizationStatus == .denied)
            }
        }
    }

    /// iOS 설정 앱의 이 앱 알림 화면 열기.
    @MainActor
    static func openSystemNotificationSettings() {
        // 배포 타깃이 iOS 16 이라 이 앱 전용 알림 설정 화면으로 바로 갈 수 있다.
        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    /// 알림 권한 요청 + APNs 등록. (Android 의 POST_NOTIFICATIONS 요청 대응 — 로그인 후 최초 1회 팝업)
    private func requestAuthorizationIfNeeded() {
        guard !didRequestAuthorization else { return }
        didRequestAuthorization = true
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            guard granted else {
                print("⚠️ 알림 권한 거부됨 — 푸시 미수신 \(error?.localizedDescription ?? "")")
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    /// APNs 등록 실패(AppDelegate) — 네트워크가 순간 끊긴 경우가 흔하다. 몇 번만 다시 시도한다
    /// (시뮬레이터·권한 문제는 계속 실패하므로 횟수 제한).
    func registrationFailed(_ error: Error) {
        print("⚠️ APNs 등록 실패: \(error.localizedDescription)")
        guard registerRetries < 3 else { return }
        registerRetries += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    /// APNs 기기 토큰 → FCM 에 연결(AppDelegate 에서 전달). 이 연결 없이는 FCM 토큰이 발급되지 않는다.
    func setAPNsToken(_ deviceToken: Data) {
        registerRetries = 0
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

    /// 토큰을 **두 곳**에 기록(Android `GoogleAuthHelper.registerFcmToken` 패리티).
    ///  - `users/{uid}/fcmTokens/{token}` : 기기별. 한 계정이 폰+아이패드처럼 여러 기기에 로그인해도
    ///    **모든 기기**로 알림이 간다(예전엔 필드 하나뿐이라 마지막 로그인 기기만 받았다).
    ///  - `users/{uid}.fcmToken`          : 예전 단일 필드(구버전 서버 함수 호환).
    ///
    /// 여기에 아무것도 없으면 서버(Cloud Functions)는 그 사용자를 **조용히 건너뛴다**
    /// → "푸시가 안 온다"의 1순위 확인 지점.
    private func saveTokenIfPossible() {
        guard let uid = appUserId, !uid.isEmpty,
              let token = fcmToken, !token.isEmpty else { return }
        let authUid = Auth.auth().currentUser?.uid ?? ""
        Task {
            // 이 기기의 앱 언어 — 서버가 전체 공지 푸시(첫 별)를 기기 언어로 보낼 때 쓴다(Android 패리티).
            let lang = await MainActor.run { LocaleManager.shared.effectiveLanguage }
            // 두 문서는 서로 독립적으로 쓴다 — 예전엔 앞 쓰기가 실패하면 서버가 실제로 쓰는 기기별 문서(fcmTokens)까지 건너뛰었다.
            var ok = true
            do {
                try await FirestoreService.users.document(uid).setData([
                    "fcmToken": token,
                    "authUid": authUid,
                ], merge: true)
            } catch {
                ok = false
                print("⚠️ fcmToken(users) 저장 실패: \(error.localizedDescription)")
            }
            do {
                try await FirestoreService.fcmTokens(of: uid).document(token).setData([
                    "platform": "ios",
                    "updatedAt": FirestoreService.nowMillis,
                    "lang": lang,
                ])
            } catch {
                ok = false
                print("⚠️ fcmToken(fcmTokens) 저장 실패: \(error.localizedDescription)")
            }
            await self.finishSave(ok: ok, uid: uid, token: token)
        }
    }

    /// 저장 결과 정리 — 실패(대개 순간 오프라인)면 잠시 뒤 몇 번 다시 시도한다. 서버에 토큰이 없으면 그 사용자는 푸시를 아예 못 받는다.
    private func finishSave(ok: Bool, uid: String, token: String) async {
        if ok {
            saveRetries = 0
            lastSavedAt = Date()
            print("✅ fcmToken 저장 완료 users/\(uid) …\(token.suffix(8))")
            return
        }
        guard saveRetries < 4 else { return }
        saveRetries += 1
        try? await Task.sleep(nanoseconds: 15_000_000_000)
        saveTokenIfPossible()
    }

    /// 이 기기의 토큰을 사용자에게서 떼어낸다 — **로그아웃 직전**에 부른다.
    ///
    /// ⚠️ 안 지우면 이 기기 토큰이 이전 계정 문서에 남아, 그 계정으로 온 알림이
    ///    지금 로그인한 **다른 사람 화면에** 뜬다(또는 원래 주인은 못 받는다).
    ///    Firestore 규칙이 본인 문서만 허용하므로 **로그아웃 전(인증이 살아 있을 때)** 해야 한다.
    func clearToken(for uid: String) async {
        guard !uid.isEmpty, let token = fcmToken, !token.isEmpty else { return }
        do {
            try await FirestoreService.fcmTokens(of: uid).document(token).delete()
            // 예전 단일 필드는 값이 이 기기 것일 때만 지운다(다른 기기가 나중에 쓴 값은 보존).
            let snap = try await FirestoreService.users.document(uid).getDocument()
            if snap.get("fcmToken") as? String == token {
                try await FirestoreService.users.document(uid)
                    .updateData(["fcmToken": FieldValue.delete()])
            }
            print("✅ fcmToken 해제 완료 users/\(uid) …\(token.suffix(8))")
        } catch {
            print("⚠️ fcmToken 해제 실패: \(error.localizedDescription)")
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// 전면 수신 — 시스템 **배너는** 띄우지 않는다(인앱 배너가 이미 같은 내용을 보여줌, Android 와 동일 정책).
    /// 다만 **소리는 낸다**: 예전엔 전면에서 `[]` 만 돌려줘 배너만 소리 없이 슬며시 떠서 "알림이 안 울린다"로 느껴졌다.
    /// 소리를 내지 않는 경우(인앱 배너도 안 뜨는 경우와 같은 조건): 알림 팝업을 꺼 둠 / 지금 그 채팅방을 보는 중 / 다른 계정 앞으로 온 알림.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let info = notification.request.content.userInfo
        let recipientId = info["recipientId"] as? String ?? ""
        let chatFriendId = info["chatFriendId"] as? String ?? ""
        let type = info["type"] as? String ?? ""
        Task { @MainActor in
            let foreignAccount = !recipientId.isEmpty && self.appUserId != nil && recipientId != self.appUserId
            let viewingThisChat = !chatFriendId.isEmpty && ChatPresence.shared.activeFriendId == chatFriendId
            // 운영자 신고 알림(ADMIN_REPORT)은 인앱 배너가 없다 → 전면에서도 시스템 배너로 보여 줘야 놓치지 않는다.
            if type == "ADMIN_REPORT" {
                completionHandler([.banner, .sound])
            } else if type == "DAILY_REMINDER" {
                completionHandler([]) // 일일 알림(로컬)은 앱을 쓰는 중이면 울리지 않는다 — 예전 동작 유지.
            } else if foreignAccount || viewingThisChat || !AppSettings.shared.notificationsEnabled {
                completionHandler([])
            } else {
                completionHandler([.sound])
            }
        }
    }

    /// 알림 탭 — data 페이로드로 이동 대상 결정(Cloud Functions 가 보내는 키와 동일).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        // 다른 계정 앞으로 온 알림은 무시 — 이 기기에서 로그아웃/계정 전환을 했는데
        // 서버에 예전 토큰이 남아 있으면 남의 알림이 뜰 수 있다(recipientId 는 서버가 넣는다).
        let recipientId = info["recipientId"] as? String ?? ""
        if !recipientId.isEmpty, let mine = appUserId, recipientId != mine {
            completionHandler(); return
        }
        let type = info["type"] as? String ?? ""
        let chatFriendId = info["chatFriendId"] as? String ?? ""
        let diaryId = info["diaryId"] as? String ?? ""

        if type == "DAILY_REMINDER" {
            PushRouter.shared.request(.upload)
        } else if !chatFriendId.isEmpty {
            PushRouter.shared.request(.chat(friendId: chatFriendId,
                                            friendName: info["chatFriendName"] as? String ?? ""))
        } else if type == "FRIEND_REQUEST" {
            PushRouter.shared.request(.friends)
        } else if !diaryId.isEmpty {
            // Android 와 같은 분기: 좋아요·댓글(내 글) = 상세, 친구 새 글·첫 별 공지(type 없음/FIRST_STAR) = 지도 포커스.
            if type == "LIKE" || type == "COMMENT" {
                PushRouter.shared.request(.diaryDetail(diaryId))
            } else {
                PushRouter.shared.request(.diary(diaryId))
            }
        }
        completionHandler()
    }
}
