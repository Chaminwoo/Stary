import SwiftUI

/// "고칠 점 보내기" 입력 카드 — 설정 화면 행에서만 열린다(iOS 는 평점 카드를 못 띄우므로 Android 카드 진입은 없음).
/// Android `core/ui/FeedbackDialog.kt` 패리티: 앞뒤 공백을 자르고, 비어 있으면 보내기 비활성, 최대 [FeedbackService.maxLength]자,
/// 보내는 동안 버튼이 도는 별이 된다. 성공하면 토스트 + 닫힘, 실패하면 오류 토스트만 띄우고 입력과 카드를 그대로 둔다.
/// `staryDialog(isPresented:)` 안에서 쓴다.
struct FeedbackDialogCard: View {
    let uid: String
    let onClose: () -> Void

    @ObservedObject private var locale = LocaleManager.shared
    @State private var text = ""
    @State private var sending = false
    @FocusState private var focused: Bool

    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        StaryDialogCard(title: locale.t(.feedbackTitle)) {
            VStack(spacing: 0) {
                TextField(locale.t(.feedbackHint), text: $text, axis: .vertical)
                    .font(.minSans(15))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(5...8)
                    .focused($focused)
                    .disabled(sending)
                    .padding(10)
                    .background(Theme.surfaceAlt, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10)
                        .strokeBorder(Theme.textSecondary.opacity(0.35), lineWidth: 1))
                    .onChange(of: text) { v in
                        if v.count > FeedbackService.maxLength { text = String(v.prefix(FeedbackService.maxLength)) }
                    }
                Text("\(text.count)/\(FeedbackService.maxLength)")
                    .font(.minSans(11))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.top, 4)
            }
        } actions: {
            StaryButton(locale.t(.commonCancel), kind: .ghost, compact: true, enabled: !sending, action: onClose)
            StaryButton(locale.t(.feedbackSend), compact: true, enabled: !trimmed.isEmpty, loading: sending) {
                send()
            }
        }
    }

    private func send() {
        let message = trimmed
        guard !message.isEmpty, !sending else { return }
        focused = false
        sending = true
        Task { @MainActor in
            let ok = await FeedbackService.send(uid: uid, message: message)
            sending = false
            if ok {
                GlobalToast.shared.show(locale.t(.feedbackSent))
                onClose()
            } else {
                // 실패: 입력과 카드 유지 — 다시 누르면 재시도.
                GlobalToast.shared.show(locale.t(.feedbackFailed))
            }
        }
    }
}
