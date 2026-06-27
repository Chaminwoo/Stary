import SwiftUI

/// 앱 전역 사용자 설정(음악/효과음 볼륨 외의 잡다한 토글) — Android `core.util.AppSettings` 패리티.
///
/// - [notificationsEnabled] : 인앱 알림 팝업(채팅/다이어리 알림 배너) 표시 여부.
///   끄면 새 알림/메시지가 와도 배너를 띄우지 않는다(미읽음 카운트·알림 목록 자체는 유지).
///
/// UserDefaults 에 영속. SwiftUI 에서 관찰 가능하도록 ObservableObject.
@MainActor
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    private let keyNotif = "notifications_enabled"

    /// 인앱 알림 팝업 on/off (기본 켜짐).
    @Published private(set) var notificationsEnabled: Bool

    private init() {
        notificationsEnabled = (UserDefaults.standard.object(forKey: keyNotif) as? Bool) ?? true
    }

    func updateNotificationsEnabled(_ value: Bool) {
        guard notificationsEnabled != value else { return }
        notificationsEnabled = value
        UserDefaults.standard.set(value, forKey: keyNotif)
    }
}
