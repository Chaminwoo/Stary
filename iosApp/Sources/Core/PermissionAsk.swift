import Foundation

/// 권한은 이유를 먼저 보여 주고 묻는다(2026-10, S2).
/// 예전엔 앱을 켜자마자(MainTabView onAppear) 위치 창이, 로그인 직후(PushManager.setUser) 알림 창이 떴다.
///  - 위치: 지도에 들어왔을 때 이유 카드 → "계속" → 시스템 창.
///  - 알림: 별을 올렸거나 친구를 추가한 뒤에 카드 → "계속" → 시스템 창.
///  - iOS 는 시스템 창을 한 번만 띄울 수 있어(거절하면 설정 앱) 아직 안 물었을 때(.notDetermined)만 카드를 띄운다.
///    거절한 뒤엔 업로드 화면의 위치 안내와 설정 화면의 "기기 알림이 꺼져 있어요" 행이 설정 앱으로 보낸다.
///
/// ⚠️ App Store 심사 5.1.1(iv) 반려(2026-11-03, 1.0.1 (13)) — 시스템 창 앞의 안내는 **닫거나 미룰 수 없고 항상 시스템 창으로 이어져야 한다.**
///    카드에 "나중에"·바깥 탭 닫기·재요청 대기 시간을 다시 넣지 말 것. 버튼 이름도 "허용"은 쓰지 않는다(시스템 창 버튼처럼 보인다) → "계속".
///    Android `core/ui/PermissionAsk.kt` 는 "나중에"가 그대로 있다 — Apple 규칙 때문에 생긴 iOS 만의 차이다.
@MainActor
final class PermissionAsk: ObservableObject {
    static let shared = PermissionAsk()
    private init() {}

    /// 이번 실행에서 위치 카드에 답했는지(실행당 한 번).
    @Published private(set) var locationAnswered = false
    /// 알림 카드를 띄울 차례 — 별을 올렸거나 친구를 추가했고, 아직 알림을 묻지 않았을 때.
    @Published private(set) var notificationPending = false

    func locationDue(_ location: LocationManager) -> Bool {
        !ScreenshotMode.isActive && !locationAnswered && location.permissionNotDetermined
    }

    func answerLocation() {
        locationAnswered = true
    }

    /// 별을 올렸거나 친구를 추가했을 때 — 알림을 아직 안 물었으면 카드를 띄울 차례로 둔다.
    func requestNotificationAsk() {
        guard !notificationPending else { return }
        Task {
            if await PushManager.isAuthorizationUndecided() { notificationPending = true }
        }
    }

    func answerNotification() {
        notificationPending = false
    }
}
