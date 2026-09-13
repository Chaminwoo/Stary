import SwiftUI

/// 앱 전역 하단 토스트 — Android `core.ui.StaryToast` 대응.
///
/// 화면마다 따로 두던 `@State toast`(MapScreen/ChatScreen 등)와 달리, 특정 화면에 묶이지 않는 안내
/// (초대 리딤 결과처럼 로그인/딥링크 흐름에서 생기는 결과)를 어느 화면 위에서든 띄운다.
/// `RootView` 가 NavigationStack **바깥** overlay 로 [GlobalToastHost] 를 달아 push 된 화면 위에도 보인다.
@MainActor
final class GlobalToast: ObservableObject {
    static let shared = GlobalToast()

    @Published private(set) var text: String?
    private var hideTask: Task<Void, Never>?

    private init() {}

    /// 새 메시지는 이전 것을 즉시 대체하고 타이머를 다시 잰다.
    func show(_ message: String, seconds: Double = 2.4) {
        text = message
        hideTask?.cancel()
        hideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.text = nil
        }
    }
}

/// [GlobalToast] 표시 레이어 — 터치는 통과시킨다.
struct GlobalToastHost: View {
    @ObservedObject private var toast = GlobalToast.shared

    var body: some View {
        ZStack {
            if let t = toast.text {
                ToastView(text: t)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .animation(.easeOut(duration: 0.2), value: toast.text)
        .allowsHitTesting(false)
    }
}
