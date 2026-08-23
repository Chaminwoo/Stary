import SwiftUI

// MARK: - 코치마크 실제 버튼 위치 전달용

struct CoachMarkPositionKey: PreferenceKey {

    static var defaultValue: [String: CGPoint] = [:]

    static func reduce(
        value: inout [String: CGPoint],
        nextValue: () -> [String: CGPoint]
    ) {
        value.merge(
            nextValue(),
            uniquingKeysWith: { _, new in new }
        )
    }
}

// MARK: - 코치마크 위치 이름

enum CoachMarkAnchor {

    static let filter = "coach_filter"
    static let location = "coach_location"
    static let constellation = "coach_constellation"
    static let eye = "coach_eye"
    static let upload = "coach_upload"
    static let menu = "coach_menu"
}
