import SwiftUI
import UIKit

/// 키보드 바깥 영역을 탭하면 키보드를 닫는다 — **iOS 전용**(Android 는 기기 뒤로가기로 닫는다, 2026-10-02 사용자 결정).
/// 여러 줄 입력칸의 리턴키는 줄바꿈 그대로 두고, 닫기는 이 바깥 탭 + 스크롤 드래그(scrollDismissesKeyboard)로 한다.
///
/// 앱 창(UIWindow)에 탭 인식기 하나를 달고 **키보드가 떠 있는 동안에만** 켠다 — 평소(지도 등) 터치에는 관여하지 않는다.
/// - 터치를 가로채지 않는다(cancelsTouchesInView/delays = false, 다른 제스처와 동시 인식) → 버튼·스크롤·드래그는 그대로
///   동작하고 키보드만 함께 닫힌다.
/// - 입력칸(UITextField/UITextView) 자체를 탭하면 제외 — 제목→본문처럼 포커스만 옮길 때 닫혔다 열리지 않게.
/// - `.keepsKeyboardOnTap(_:)` 를 단 영역도 제외 — 채팅/댓글 입력 줄처럼 키보드를 연 채로 전송하는 곳.
///   (전송 탭과 동시에 키보드가 닫히면 한글 조합 중이던 마지막 글자가 비운 입력칸에 다시 확정돼 남을 수 있다.)
final class KeyboardDismissOnTap: NSObject, UIGestureRecognizerDelegate {
    static let shared = KeyboardDismissOnTap()

    private let tap = UITapGestureRecognizer()
    private var started = false

    /// 앱 시작 시 한 번(StaryApp.init). 창은 이 시점에 아직 없을 수 있어 실제 부착은 키보드가 뜰 때 한다.
    func start() {
        guard !started else { return }
        started = true
        tap.addTarget(self, action: #selector(handleTap))
        tap.cancelsTouchesInView = false
        tap.delaysTouchesBegan = false
        tap.delaysTouchesEnded = false
        tap.delegate = self
        tap.isEnabled = false
        NotificationCenter.default.addObserver(self, selector: #selector(keyboardWillShow),
                                               name: UIResponder.keyboardWillShowNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(keyboardWillHide),
                                               name: UIResponder.keyboardWillHideNotification, object: nil)
    }

    @objc private func keyboardWillShow() {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) else { return }
        if tap.view !== window {
            tap.view?.removeGestureRecognizer(tap)
            window.addGestureRecognizer(tap)
        }
        tap.isEnabled = true
    }

    @objc private func keyboardWillHide() {
        tap.isEnabled = false
    }

    @objc private func handleTap() {
        tap.view?.endEditing(true)
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var view: UIView? = touch.view
        while let v = view {
            if v is UITextField || v is UITextView { return false }
            view = v.superview
        }
        let point = touch.location(in: nil)   // 창 좌표 = SwiftUI .global
        return !KeyboardTapExclusion.rects.values.contains { $0.contains(point) }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }
}

/// 바깥 탭으로 키보드를 닫지 않을 영역(창 좌표) — `.keepsKeyboardOnTap(_:)` 가 채운다.
enum KeyboardTapExclusion {
    static var rects: [String: CGRect] = [:]
}

private struct KeepsKeyboardOnTap: ViewModifier {
    let key: String

    func body(content: Content) -> some View {
        content.background(
            GeometryReader { g in
                Color.clear
                    .onAppear { KeyboardTapExclusion.rects[key] = g.frame(in: .global) }
                    .onChange(of: g.frame(in: .global)) { KeyboardTapExclusion.rects[key] = $0 }
                    .onDisappear { KeyboardTapExclusion.rects[key] = nil }
            }
        )
    }
}

extension View {
    /// 이 영역을 탭해도 키보드를 닫지 않는다(KeyboardDismissOnTap 제외 영역). `key` 는 화면마다 고유하게.
    func keepsKeyboardOnTap(_ key: String) -> some View {
        modifier(KeepsKeyboardOnTap(key: key))
    }
}
