import SwiftUI

/// 알림 화면 — 좋아요/댓글/친구 새 글. 스와이프 삭제 + 진입 시 모두 읽음.
/// (ProfileScreen 에서 push 되므로 자체 NavigationStack 없음)
struct NotificationsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @StateObject private var vm = NotificationsViewModel()

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if vm.items.isEmpty {
                ContentUnavailableCompat(text: LocaleManager.shared.t(.notifEmpty))
            } else {
                List {
                    ForEach(vm.items) { n in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(n.displayText)
                                .font(.poorStory(15))
                                .foregroundStyle(Theme.textPrimary)
                            if !n.diaryTitle.isEmpty {
                                Text("‘\(n.diaryTitle)’")
                                    .font(.poorStory(12))
                                    .foregroundStyle(Theme.textSecondary)
                            }
                            Text(RelativeTime.string(fromMillis: n.createdAt))
                                .font(.poorStory(11))
                                .foregroundStyle(Theme.textFaint)
                        }
                        .listRowBackground(n.read ? Theme.background : Theme.surface)
                    }
                    .onDelete { idx in
                        let targets = idx.map { vm.items[$0] }
                        Task { for t in targets { await vm.delete(t) } }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(LocaleManager.shared.t(.navNotification))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if let uid = auth.uid { vm.start(ownerId: uid) } }
        .onDisappear { vm.stop() }
        .task {
            if let uid = auth.uid { await vm.markAllRead(ownerId: uid) }
        }
    }
}
