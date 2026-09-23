import Foundation
import UserNotifications

/// 매일 1회 "오늘 기록해보세요" 로컬 알림 — **매번 다른 시간대, 매번 다른 문장**. 아침엔 오지 않는다.
/// Android `push.DailyReminderScheduler`(AlarmManager) 대응 — 밴드 경계·문구는 동일 유지.
///
/// 2026-09-23 개편: 예전엔 12~22시 균일 랜덤 + 시간대별 고정 문장 1개라 며칠이면 문구가 외워졌다.
/// 이제 두 축을 같이 흔든다 —
/// 1. **시간대**: `DailyReminderMessages.Band` 5구간 중 **직전에 쓴 밴드를 빼고** 고른 뒤 그 안에서 분 단위 랜덤.
/// 2. **문장**: 그 밴드의 풀에서 **직전에 쓴 문장을 빼고** 랜덤.
/// (Android 는 문장을 알람이 울릴 때 고르고, iOS 는 예약 시점에 고른다 — 로컬 알림 내용이 미리 확정돼야 해서다.
///  결과적으로 "매번 다르다"는 동일.)
///
/// iOS 로컬 알림(`UNCalendarNotificationTrigger`)은 한 번 울리면 자동 반복되지 않고, 매번 다른(랜덤) 시각을
/// 써야 해서 `repeats: true` 캘린더 트리거(항상 같은 시/분)를 쓸 수 없다 — 대신 예약 시각을
/// UserDefaults 에 같이 저장해 두고, [ensureScheduled] 가 그 시각이 이미 지났으면(알림이 울렸거나
/// 앱이 오래 안 켜져 있었거나) 다음 예약을 다시 잡는다. 앱 시작/포그라운드 복귀마다 호출해도 안전.
///
/// ⚠️ `@MainActor` 필수 — 설정값(`AppSettings.shared`)과 문구 풀(`DailyReminderMessages.pool`, `LocaleManager`)이
/// 메인 액터 격리라, 격리 없는 enum 에서 부르면 컴파일 에러(CI 에서 확인). 호출부(AppSettings 토글 /
/// RootView scenePhase)는 모두 메인 액터라 추가 조치 불필요 — 백그라운드 콜백에서 부르게 되면
/// `Task { @MainActor in ... }` 로 감쌀 것.
@MainActor
enum DailyReminderScheduler {
    private static let requestId = "daily_reminder"
    private static let keyScheduledAt = "daily_reminder_scheduled_at"
    private static let keyLastBand = "daily_reminder_last_band"
    private static let keyLastMsgPrefix = "daily_reminder_last_msg_"

    /// 이미 미래에 예약돼 있으면 그대로 둔다(중복 예약 방지) — 앱 시작/포그라운드 복귀마다 호출해도 안전.
    static func ensureScheduled() {
        let scheduledAt = UserDefaults.standard.double(forKey: keyScheduledAt)
        if scheduledAt > Date().timeIntervalSince1970 { return }
        scheduleNext()
    }

    /// 다음 알림을 새로 예약 — 직전과 다른 밴드 + 직전과 다른 문장.
    static func scheduleNext() {
        let band = pickBand()
        let target = nextTrigger(in: band)
        UserDefaults.standard.set(target.timeIntervalSince1970, forKey: keyScheduledAt)
        UserDefaults.standard.set(band.rawValue, forKey: keyLastBand)

        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [requestId])
        guard AppSettings.shared.dailyReminderEnabled else { return } // 꺼져 있으면 시각만 갱신, 실제 예약은 생략

        let content = UNMutableNotificationContent()
        content.title = "Stary"
        content.body = pickMessage(band)
        content.sound = .default
        content.userInfo = ["type": "DAILY_REMINDER"]

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: target)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let request = UNNotificationRequest(identifier: requestId, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    /// 직전에 쓴 밴드를 빼고 고른다 — 같은 시간대가 이틀 연속 오지 않게.
    private static func pickBand() -> DailyReminderMessages.Band {
        let last = UserDefaults.standard.string(forKey: keyLastBand)
        let choices = DailyReminderMessages.Band.allCases.filter { $0.rawValue != last }
        return choices.randomElement() ?? .night
    }

    /// 밴드의 문구 풀에서 하나 — 직전에 쓴 문장은 빼고(풀이 2개 이상일 때). 고른 위치를 저장해 다음 번에 제외한다.
    private static func pickMessage(_ band: DailyReminderMessages.Band) -> String {
        let pool = DailyReminderMessages.pool(band)
        guard !pool.isEmpty else { return "" }
        let key = keyLastMsgPrefix + band.rawValue
        let last = UserDefaults.standard.object(forKey: key) as? Int ?? -1
        let remaining = pool.indices.filter { $0 != last }
        let picked = (remaining.isEmpty ? Array(pool.indices) : remaining).randomElement() ?? 0
        UserDefaults.standard.set(picked, forKey: key)
        return pool[picked]
    }

    /// 오늘 그 밴드가 아직 안 지났으면 오늘, 지났으면 내일 같은 밴드에서 뽑는다.
    private static func nextTrigger(in band: DailyReminderMessages.Band) -> Date {
        let now = Date()
        let today = randomTime(in: band, on: now)
        // 1분 이내로 임박한 경우도 내일로 — 예약 직후 바로 울리는 걸 막는다.
        if today > now.addingTimeInterval(60) { return today }
        let cal = Calendar.current
        let tomorrow = cal.date(byAdding: .day, value: 1, to: now) ?? now
        return randomTime(in: band, on: tomorrow)
    }

    private static func randomTime(in band: DailyReminderMessages.Band, on day: Date) -> Date {
        let cal = Calendar.current
        let randomMinute = Int.random(in: band.startMinute..<band.endMinute)
        var comps = cal.dateComponents([.year, .month, .day], from: day)
        comps.hour = randomMinute / 60
        comps.minute = randomMinute % 60
        comps.second = Int.random(in: 0..<60)
        return cal.date(from: comps) ?? day
    }
}
