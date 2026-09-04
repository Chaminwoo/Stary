import SwiftUI

/// 앱 전역 사용자 설정(음악/효과음 볼륨 외의 잡다한 토글) — Android `core.util.AppSettings` 패리티.
///
/// - [notificationsEnabled] : 인앱 알림 팝업(채팅/다이어리 알림 배너) 표시 여부.
///   끄면 새 알림/메시지가 와도 배너를 띄우지 않는다(미읽음 카운트·알림 목록 자체는 유지).
/// - [hapticsEnabled] : 햅틱(진동) 피드백. 끄면 `Haptics` 호출이 전부 무음이 된다.
/// - [dailyReminderEnabled] : 매일 1회(점심~밤 10시 랜덤) "오늘 기록해보세요" 알림.
///   끄면 시스템 알림은 안 뜨지만 예약 자체는 `DailyReminderScheduler` 가 계속 갱신한다(다시 켜면 바로 이어짐).
///
/// UserDefaults 에 영속. SwiftUI 에서 관찰 가능하도록 ObservableObject.
@MainActor
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    private let keyNotif = "notifications_enabled"
    private let keyHaptics = "haptics_enabled"
    private let keyDailyReminder = "daily_reminder_enabled"

    /// 인앱 알림 팝업 on/off (기본 켜짐).
    @Published private(set) var notificationsEnabled: Bool
    /// 햅틱(진동) on/off (기본 켜짐).
    @Published private(set) var hapticsEnabled: Bool
    /// 일일 알림(오늘 기록 유도) on/off (기본 켜짐).
    @Published private(set) var dailyReminderEnabled: Bool

    private init() {
        notificationsEnabled = (UserDefaults.standard.object(forKey: keyNotif) as? Bool) ?? true
        hapticsEnabled = (UserDefaults.standard.object(forKey: keyHaptics) as? Bool) ?? true
        dailyReminderEnabled = (UserDefaults.standard.object(forKey: keyDailyReminder) as? Bool) ?? true
    }

    func updateNotificationsEnabled(_ value: Bool) {
        guard notificationsEnabled != value else { return }
        notificationsEnabled = value
        UserDefaults.standard.set(value, forKey: keyNotif)
    }

    func updateHapticsEnabled(_ value: Bool) {
        guard hapticsEnabled != value else { return }
        hapticsEnabled = value
        UserDefaults.standard.set(value, forKey: keyHaptics)
        if value { Haptics.soft() } // 켠 순간 어떤 느낌인지 바로 보여준다
    }

    func updateDailyReminderEnabled(_ value: Bool) {
        guard dailyReminderEnabled != value else { return }
        dailyReminderEnabled = value
        UserDefaults.standard.set(value, forKey: keyDailyReminder)
        if value { DailyReminderScheduler.scheduleNext() } // 꺼져 있던 동안 지난 예약을 즉시 다시 잡는다
    }
}
