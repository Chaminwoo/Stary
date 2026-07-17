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

    private var accent: LinearGradient {
        LinearGradient(colors: [Theme.mint, Color(hex: 0x3B82F6)], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
                .onTapGesture { onDismiss() }
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(accent).frame(width: 64, height: 64)
                    Image(systemName: systemImage)
                        .font(.system(size: 30, weight: .semibold))
                        .foregroundStyle(.white)
                }
                Text(title)
                    .font(.title2.weight(.medium))
                    .foregroundStyle(Theme.textPrimary)
                Text(message)
                    .font(.system(size: 14))
                    .foregroundStyle(Color(hex: 0xB8C0CC))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                Button(action: onDismiss) {
                    Text(LocaleManager.shared.t(.onbButton))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color(hex: 0x06121E))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(accent, in: RoundedRectangle(cornerRadius: 14))
                }
                .padding(.top, 4)
            }
            .padding(24)
            .background(Color(hex: 0x121821), in: RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(LinearGradient(colors: [Theme.mint.opacity(0.6), Color(hex: 0x3B82F6).opacity(0.45)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1)
            )
            .padding(24)
        }
    }
}
