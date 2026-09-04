import SwiftUI

/// 설정 화면의 "도움말 다시 보기" → 지도 코치마크를 다시 띄우기 위한 전역 브리지.
/// SettingsScreen 은 RootView 위에 push 된 별개 화면이라 `showCoachMark` 를 직접 못 건드리므로,
/// 여기에 요청만 남기면 RootView 가 감지해 지도로 돌아가 코치마크를 재생한다.
/// (Android `OnboardingReplayState` 패리티.)
@MainActor
final class OnboardingReplayState: ObservableObject {
    static let shared = OnboardingReplayState()
    private init() {}

    @Published private(set) var requested = false

    func request() { requested = true }

    /// RootView 가 처리 후 호출.
    func consume() { requested = false }
}
