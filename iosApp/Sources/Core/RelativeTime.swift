import Foundation

/// epoch millis → "방금 전 / N분 전 / N시간 전 / N일 전" 상대 시간.
enum RelativeTime {
    static func string(fromMillis millis: Int64) -> String {
        let seconds = Int(Double(FirestoreService.nowMillis - millis) / 1000)
        switch seconds {
        case ..<0: return "방금 전"
        case 0..<60: return "방금 전"
        case 60..<3600: return "\(seconds / 60)분 전"
        case 3600..<86400: return "\(seconds / 3600)시간 전"
        case 86400..<2_592_000: return "\(seconds / 86400)일 전"
        default:
            let f = DateFormatter()
            f.dateFormat = "yyyy.MM.dd"
            return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
        }
    }
}
