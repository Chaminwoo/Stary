import SwiftUI

/// 알림 화면 — 좋아요/댓글/친구 새 글. 스와이프 삭제 + 진입 시 모두 읽음.
/// (ProfileScreen 에서 push 되므로 자체 NavigationStack 없음)
struct NotificationsScreen: View {
    @EnvironmentObject var auth: AuthManager
    /// 차단한 사용자가 남긴 좋아요/댓글/친구 새 글 알림은 숨긴다(Android NotificationScreen 패리티).
    @EnvironmentObject var blocks: BlockStore
    @StateObject private var vm = NotificationsViewModel()
    /// 알림 문서의 actorName 은 발생 시점 스냅샷 → users/{actorId} 의 현재 이름으로 표시.
    @ObservedObject private var directory = UserDirectory.shared

    /// 차단 필터를 적용한 표시 대상.
    private var items: [AppNotification] { vm.items.filter { !blocks.blockedIds.contains($0.actorId) } }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if items.isEmpty {
                // 빈 상태도 별 언어로(떠 있는 골드 별 + 안내) — StaryEmptyState 공용.
                    StaryEmptyState(title: LocaleManager.shared.t(.notifEmpty),
                                description: LocaleManager.shared.t(.notifEmptyDesc),
                                starType: 0, starColorIndex: 1)
            } else {
                List {
                    ForEach(items) { n in
                        // Android NotificationItem 행 구조: [이모지] [이름 · 시간 / 문구 / (댓글 내용)]
                        HStack(alignment: .top, spacing: 12) {
                            Text(n.emoji)
                                .font(.system(size: 20))
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(directory.name(n.actorId, fallback: n.actorName))
                                        .font(.minSans(14))
                                        .foregroundStyle(Theme.textPrimary)
                                    Spacer()
                                    Text(RelativeTime.string(fromMillis: n.createdAt))
                                        .font(.minSans(11))
                                        .foregroundStyle(Theme.textSecondary)
                                }
                                Text(n.displayText)
                                    .font(.minSans(13))
                                    .foregroundStyle(Theme.textSecondary)
                                if n.type == "COMMENT", !n.content.isEmpty {
                                    Text("\"\(n.content)\"")
                                        .font(.minSans(13))
                                        .foregroundStyle(Theme.textPrimary)
                                        .padding(.top, 2)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .watchUser(n.actorId)
                        .listRowBackground(n.read ? Theme.background : Theme.surface)
                    }
                    .onDelete { idx in
                        // 인덱스는 화면에 그린 목록(items) 기준 — vm.items 로 접근하면 차단 필터만큼 어긋난다.
                        let targets = idx.map { items[$0] }
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
