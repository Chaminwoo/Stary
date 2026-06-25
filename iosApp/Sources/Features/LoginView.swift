import SwiftUI

/// 첫 진입 — 익명으로 둘러보기 또는 구글 로그인.
struct LoginView: View {
    @EnvironmentObject var auth: AuthManager

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 28) {
                Spacer()

                VStack(spacing: 14) {
                    StarView(type: 1, colorIndex: 9, size: 72)
                    Text("Stary")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(Theme.textPrimary)
                    Text("지금 이 자리의 별을 남겨요")
                        .font(.subheadline)
                        .foregroundStyle(Theme.textSecondary)
                }

                Spacer()

                VStack(spacing: 12) {
                    Button {
                        Task { await auth.signInWithGoogle() }
                    } label: {
                        Label("Google 로 시작하기", systemImage: "g.circle.fill")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Theme.surfaceAlt, in: RoundedRectangle(cornerRadius: 14))
                    }

                    Button {
                        Task { await auth.signInAnonymously() }
                    } label: {
                        Text("둘러보기 (익명)")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Theme.mint.opacity(0.18), in: RoundedRectangle(cornerRadius: 14))
                    }
                }
                .foregroundStyle(Theme.textPrimary)
                .padding(.horizontal, 28)

                if let error = auth.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 28)
                }

                Spacer().frame(height: 24)
            }
            .disabled(auth.isBusy)

            if auth.isBusy {
                ProgressView().tint(Theme.mint)
            }
        }
    }
}
