import Foundation

/// 권한은 이유를 먼저 보여 주고 묻는다(2026-10, S2 — Android `core/ui/PermissionAsk.kt` 패리티).
/// 예전엔 앱을 켜자마자(MainTabView onAppear) 위치 창이, 로그인 직후(PushManager.setUser) 알림 창이 떴다.
///  - 위치: 지도에 들어왔을 때 이유 카드 → "허용하기"를 눌러야 시스템 창. 실행당 한 번, "나중에"면 3일 뒤에 다시.
///  - 알림: 별을 올렸거나 친구를 추가한 뒤에 카드 → "알림 받기"를 눌러야 시스템 창. "나중에"면 7일 뒤.
///  - iOS 는 시스템 창을 한 번만 띄울 수 있어(거절하면 설정 앱) 아직 안 물었을 때(.notDetermined)만 카드를 띄운다.
///    거절한 뒤엔 업로드 화면의 위치 안내와 설정 화면의 "기기 알림이 꺼져 있어요" 행이 설정 앱으로 보낸다.
@MainActor
final class PermissionAsk: ObservableObject {
    static let shared = PermissionAsk()
    private init() {}

    private static let locationLaterKey = "perm_loc_later_at"
    private static let notificationLaterKey = "perm_notif_later_at"
    private static let locationCooldown: TimeInterval = 3 * 24 * 3600
    private static let notificationCooldown: TimeInterval = 7 * 24 * 3600

    /// 이번 실행에서 위치 카드에 답했는지(실행당 한 번).
    @Published private(set) var locationAnswered = false
    /// 알림 카드를 띄울 차례 — 별을 올렸거나 친구를 추가했고, 아직 알림을 묻지 않았을 때.
    @Published private(set) var notificationPending = false

    func locationDue(_ location: LocationManager) -> Bool {
        !locationAnswered && location.permissionNotDetermined && Self.cooled(Self.locationLaterKey, Self.locationCooldown)
    }

    func answerLocation(later: Bool) {
        locationAnswered = true
        if later { Self.markLater(Self.locationLaterKey) }
    }

    /// 별을 올렸거나 친구를 추가했을 때 — 알림을 아직 안 물었으면 카드를 띄울 차례로 둔다.
    func requestNotificationAsk() {
        guard !notificationPending, Self.cooled(Self.notificationLaterKey, Self.notificationCooldown) else { return }
        Task {
            if await PushManager.isAuthorizationUndecided() { notificationPending = true }
        }
    }

    func answerNotification(later: Bool) {
        notificationPending = false
        if later { Self.markLater(Self.notificationLaterKey) }
    }

    private static func cooled(_ key: String, _ cooldown: TimeInterval) -> Bool {
        Date().timeIntervalSince1970 - UserDefaults.standard.double(forKey: key) >= cooldown
    }

    private static func markLater(_ key: String) {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: key)
    }
}
