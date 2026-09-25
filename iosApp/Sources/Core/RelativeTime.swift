import Foundation

/// epoch millis → "방금 전 / N분 전 / N시간 전 / N일 전" 상대 시간(현재 앱 언어 — L10n time*).
/// Android `core.util.RelativeTime` 패리티. (예전엔 한국어 하드코딩이라 언어를 바꿔도 한국어로 나왔다.)
enum RelativeTime {
    @MainActor
    static func string(fromMillis millis: Int64) -> String {
        let seconds = Int(Double(FirestoreService.nowMillis - millis) / 1000)
        let l = LocaleManager.shared
        switch seconds {
        case ..<60: return l.t(.timeJustNow)
        case 60..<3600: return String(format: l.t(.timeMinutesAgo), seconds / 60)
        case 3600..<86400: return String(format: l.t(.timeHoursAgo), seconds / 3600)
        // Android 와 같은 경계(7일) — 그보다 오래되면 날짜로.
        case 86400..<604_800: return String(format: l.t(.timeDaysAgo), seconds / 86400)
        default:
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy.MM.dd"
            return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
        }
    }
}
