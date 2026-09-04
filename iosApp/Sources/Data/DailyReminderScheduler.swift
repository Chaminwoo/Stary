import Foundation
import UserNotifications

/// 매일 1회 "오늘 기록해보세요" 로컬 알림 — 시간은 매일 **랜덤**(점심 12시 ~ 밤 10시 사이), 아침엔 안 온다.
/// Android `push.DailyReminderScheduler`(AlarmManager) 대응 — 값(창 시간대/문구 시간대)은 동일 유지.
///
/// iOS 로컬 알림(`UNCalendarNotificationTrigger`)은 한 번 울리면 자동 반복되지 않고, 매번 다른(랜덤) 시각을
/// 써야 해서 `repeats: true` 캘린더 트리거(항상 같은 시/분)를 쓸 수 없다 — 대신 예약 시각을
/// UserDefaults 에 같이 저장해 두고, [ensureScheduled] 가 그 시각이 이미 지났으면(알림이 울렸거나
/// 앱이 오래 안 켜져 있었거나) 다음 예약을 다시 잡는다. 앱 시작/포그라운드 복귀마다 호출해도 안전.
enum DailyReminderScheduler {
    private static let requestId = "daily_reminder"
    private static let keyScheduledAt = "daily_reminder_scheduled_at"

    /// 알림이 울릴 수 있는 창 — 점심부터 밤 10시까지(아침 제외). Android WINDOW_START/END_HOUR 와 동일 값.
    private static let windowStartHour = 12
    private static let windowEndHour = 22

    /// 이미 미래에 예약돼 있으면 그대로 둔다(중복 예약 방지) — 앱 시작/포그라운드 복귀마다 호출해도 안전.
    static func ensureScheduled() {
        let scheduledAt = UserDefaults.standard.double(forKey: keyScheduledAt)
        if scheduledAt > Date().timeIntervalSince1970 { return }
        scheduleNext()
    }

    /// 다음 랜덤 시각(오늘 창이 안 지났으면 오늘, 지났으면 내일)을 새로 예약.
    static func scheduleNext() {
        let target = nextRandomTrigger()
        UserDefaults.standard.set(target.timeIntervalSince1970, forKey: keyScheduledAt)

        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [requestId])
        guard AppSettings.shared.dailyReminderEnabled else { return } // 꺼져 있으면 시각만 갱신, 실제 예약은 생략

        let content = UNMutableNotificationContent()
        content.title = "Stary"
        content.body = bodyFor(hour: Calendar.current.component(.hour, from: target))
        content.sound = .default
        content.userInfo = ["type": "DAILY_REMINDER"]

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: target)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let request = UNNotificationRequest(identifier: requestId, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    private static func nextRandomTrigger() -> Date {
        let now = Date()
        let today = randomTime(in: now)
        // 오늘 창의 랜덤 시각이 이미 지났으면(또는 1분 이내로 임박) 내일 창에서 다시 뽑는다.
        if today > now.addingTimeInterval(60) { return today }
        let cal = Calendar.current
        let tomorrow = cal.date(byAdding: .day, value: 1, to: now) ?? now
        return randomTime(in: tomorrow)
    }

    private static func randomTime(in day: Date) -> Date {
        let cal = Calendar.current
        let startMinute = windowStartHour * 60
        let endMinute = windowEndHour * 60
        let randomMinute = Int.random(in: startMinute..<endMinute)
        var comps = cal.dateComponents([.year, .month, .day], from: day)
        comps.hour = randomMinute / 60
        comps.minute = randomMinute % 60
        comps.second = Int.random(in: 0..<60)
        return cal.date(from: comps) ?? day
    }

    /// 울릴 시각대에 맞춰 문구를 고른다 — Android DailyReminderReceiver 와 동일 시간대 구간.
    private static func bodyFor(hour: Int) -> String {
        switch hour {
        case 12...14: return LocaleManager.shared.t(.dailyReminderLunch)
        case 15...17: return LocaleManager.shared.t(.dailyReminderAfternoon)
        case 18...19: return LocaleManager.shared.t(.dailyReminderDinner)
        default: return LocaleManager.shared.t(.dailyReminderNight)
        }
    }
}
