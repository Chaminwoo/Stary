import SwiftUI

/// 앱 전역 인앱 알림 배너 — 채팅 새 메시지·다이어리 알림(좋아요/댓글/친구 새 글)이 도착하면
/// 화면 **상단**에서 슬라이드 인 되는 배너를 띄운다. 탭하면 해당 화면으로 이동, 잠시 후 자동으로 사라진다.
/// (Android `core.ui.InAppBanner` 패리티. 하단 토스트와 별개 채널.)
@MainActor
final class InAppBanner: ObservableObject {
    static let shared = InAppBanner()
    private init() {}

    enum Kind { case notification, chat }

    struct Event: Identifiable {
        let id = UUID()
        let title: String
        let body: String
        let kind: Kind
        let onTap: () -> Void
    }

    @Published private(set) var events: [Event] = []

    // 프로세스 동안 이미 띄운 dedup 키 — 같은 메시지가 큐에 두 번 들어가지 않게(Android InAppBanner.shownKeys 패리티).
    private var shownKeys: Set<String> = []

    /// 배너 한 건 추가(큐). 여러 건이 빠르게 와도 순서대로 표시된다.
    /// [key] 가 주어지면 프로세스 동안 그 키로 1회만 표시(중복 enqueue 방지).
    func show(title: String, body: String, kind: Kind, key: String? = nil, onTap: @escaping () -> Void) {
        if let key, !shownKeys.insert(key).inserted { return } // 이미 띄운 메시지 → 무시
        events.append(Event(title: title, body: body, kind: kind, onTap: onTap))
    }

    func consume(_ id: UUID) {
        events.removeAll { $0.id == id }
    }
}

/// 배너 호스트 — 화면 최상단에 한 번 배치하면, 어디서든 [InAppBanner.show] 로 띄울 수 있다.
struct InAppBannerHost: View {
    @ObservedObject private var banner = InAppBanner.shared
    @State private var visible = false

    private let visibleSeconds: Double = 4

    var body: some View {
        VStack {
            if let e = banner.events.first {
                bannerView(e)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: e.id) {
                        visible = true
                        try? await Task.sleep(nanoseconds: UInt64(visibleSeconds * 1_000_000_000))
                        banner.consume(e.id)
                    }
            }
            Spacer()
        }
        .animation(.easeInOut(duration: 0.28), value: banner.events.first?.id)
        .allowsHitTesting(banner.events.first != nil)
    }

    private func bannerView(_ e: InAppBanner.Event) -> some View {
        Button {
            e.onTap()
            banner.consume(e.id)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: e.kind == .chat ? "bubble.left.fill" : "bell.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.mint)
                    .frame(width: 38, height: 38)
                    .background(Theme.mint.opacity(0.16), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(e.title.isEmpty ? "새 알림" : e.title)
                        .font(.subheadline).bold()
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                    if !e.body.isEmpty {
                        Text(e.body)
                            .font(.caption)
                            .foregroundStyle(Theme.textSecondary)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(
                LinearGradient(colors: [Color(hex: 0x18203A), Color(hex: 0x111733)],
                               startPoint: .top, endPoint: .bottom),
                in: RoundedRectangle(cornerRadius: 18)
            )
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.10), lineWidth: 1))
            .shadow(color: .black.opacity(0.4), radius: 14, y: 6)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }
}
