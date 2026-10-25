import SwiftUI

/// 화면에 **처음 들어왔을 때 1회** 설명 오버레이를 띄운다. (Android `core.ui.FirstVisitInfo` 패리티)
/// `UserDefaults` 의 `onb_<key>` 로 본 적 있는지 기록한다. 시트 대신 오버레이라 탭 전환에 안전.
extension View {
    func firstVisitInfo(key: String, systemImage: String, title: String, message: String) -> some View {
        modifier(FirstVisitInfoModifier(key: key, systemImage: systemImage, title: title, message: message))
    }
}

private struct FirstVisitInfoModifier: ViewModifier {
    let key: String
    let systemImage: String
    let title: String
    let message: String
    @State private var show = false

    private var prefKey: String { "onb_\(key)" }

    func body(content: Content) -> some View {
        content
            .onAppear {
                if !UserDefaults.standard.bool(forKey: prefKey) { show = true }
            }
            .overlay {
                if show {
                    FirstVisitInfoCard(systemImage: systemImage, title: title, message: message) {
                        UserDefaults.standard.set(true, forKey: prefKey)
                        withAnimation(.easeOut(duration: 0.2)) { show = false }
                    }
                    .transition(.opacity)
                }
            }
    }
}

private struct FirstVisitInfoCard: View {
    let systemImage: String
    let title: String
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(StaryDialogStyle.scrimOpacity).ignoresSafeArea()
                .onTapGesture { onDismiss() }
            // 판·뱃지·버튼은 다른 팝업과 같은 유리 + 달빛(2026-10-26, 민트→블루 그라데이션에서 바꿈). Android 패리티.
            VStack(spacing: 16) {
                StaryPopupBadge(systemImage: systemImage)
                Text(title)
                    .font(.minSans(20, .medium))
                    .foregroundStyle(Theme.textPrimary)
                Text(message)
                    .font(.minSans(14))
                    .foregroundStyle(Color(hex: 0xB8C0CC))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                StaryButton(LocaleManager.shared.t(.onbButton), fullWidth: true, action: onDismiss)
                    .padding(.top, 6)
            }
            .padding(.horizontal, 20).padding(.vertical, 24)
            .frame(maxWidth: 360)
            .glassPanel(cornerRadius: StaryDialogStyle.corner, tint: StaryDialogStyle.glassTint)
            .padding(.horizontal, StaryDialogStyle.screenPadding)
        }
    }
}
