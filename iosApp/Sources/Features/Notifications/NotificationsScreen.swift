import SwiftUI

/// 알림 화면 — 좋아요/댓글/친구 새 글. 스와이프 삭제 + 진입 시 모두 읽음.
/// (ProfileScreen 에서 push 되므로 자체 NavigationStack 없음)
struct NotificationsScreen: View {
    @EnvironmentObject var auth: AuthManager
    /// 차단한 사용자가 남긴 좋아요/댓글/친구 새 글 알림은 숨긴다(Android NotificationScreen 패리티).
    @EnvironmentObject var blocks: BlockStore
    @EnvironmentObject var store: DiaryStore
    @StateObject private var vm = NotificationsViewModel()
    /// 알림 문서의 actorName 은 발생 시점 스냅샷 → users/{actorId} 의 현재 이름으로 표시.
    @ObservedObject private var directory = UserDirectory.shared
    /// 좋아요·댓글 알림을 눌러 연 상세 — 이 목록 위에 push 되므로 뒤로가기가 다시 알림 목록으로 돌아온다(Android 동일).
    @State private var detailDiary: Diary?

    /// 행 탭 — Android `NotificationScreen` 과 같은 분기([PushRoute.from]).
    ///  - 좋아요·댓글: 상세를 이 목록 위에 push
    ///  - 친구 새 글·첫 별 공지: 지도로 나가 그 별로 카메라 이동 + 파장 / 친구 요청: 친구 화면
    ///  (예전 iOS 는 행에 탭 동작이 아예 없어 알림을 눌러도 아무 일도 일어나지 않았다.)
    private func open(_ n: AppNotification) {
        guard let route = PushRoute.from(n) else { return }
        if case .diaryDetail(let id) = route {
            Task { @MainActor in
                if let d = await store.diary(id: id, viewerUid: auth.uid) {
                    detailDiary = d
                } else {
                    GlobalToast.shared.show(LocaleManager.shared.t(.notifDiaryGone))
                }
            }
        } else {
            PushRouter.shared.request(route)
        }
    }

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
                        // 행 전체가 탭 영역(Spacer/여백 포함) — 이동 대상이 없는 알림은 탭해도 무반응.
                        .contentShape(Rectangle())
                        .onTapGesture { open(n) }
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
        .navigationDestination(isPresented: Binding(
            get: { detailDiary != nil }, set: { if !$0 { detailDiary = nil } }
        )) {
            if let d = detailDiary { DetailScreen(diary: d) }
        }
        .onAppear { if let uid = auth.uid { vm.start(ownerId: uid) } }
        .onDisappear { vm.stop() }
        .task {
            if let uid = auth.uid { await vm.markAllRead(ownerId: uid) }
        }
    }
}
