import SwiftUI

/// 친구 탭 — 사용자 검색/요청 보내기, 받은 요청 수락·거절, 친구 목록(→채팅).
struct FriendsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @StateObject private var vm = FriendsViewModel()
    @State private var query = ""

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        searchSection
                        if !vm.requests.isEmpty { requestsSection }
                        friendsSection
                    }
                    .padding(16)
                }
            }
            .navigationTitle("친구")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Friend.self) { friend in
                ChatScreen(friend: friend, myUid: auth.uid ?? "")
            }
            .onAppear { if let uid = auth.uid { vm.start(uid: uid) } }
            .onDisappear { vm.stop() }
        }
    }

    private var searchSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                TextField("이름으로 친구 찾기", text: $query)
                    .padding(10)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textPrimary)
                    .onSubmit { runSearch() }
                Button("검색") { runSearch() }
                    .tint(Theme.mint)
            }
            if vm.searching { ProgressView().tint(Theme.mint) }
            ForEach(vm.results) { user in
                HStack {
                    avatar(user.userName)
                    Text(user.userName).foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Button("추가") {
                        guard let uid = auth.uid else { return }
                        Task { await vm.sendRequest(fromId: uid, fromName: auth.displayName, to: user) }
                    }
                    .font(.caption).tint(Theme.mint)
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var requestsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("받은 요청").font(.headline).foregroundStyle(Theme.textPrimary)
            ForEach(vm.requests) { req in
                HStack {
                    avatar(req.fromName)
                    Text(req.fromName).foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Button("수락") {
                        guard let uid = auth.uid else { return }
                        Task { await vm.accept(req, myUid: uid, myName: auth.displayName) }
                    }
                    .font(.caption).tint(Theme.mint)
                    Button("거절") { Task { await vm.decline(req) } }
                        .font(.caption).tint(.red)
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("친구 \(vm.friends.count)").font(.headline).foregroundStyle(Theme.textPrimary)
            if vm.friends.isEmpty {
                Text("아직 친구가 없어요. 위에서 찾아 추가해보세요.")
                    .font(.subheadline).foregroundStyle(Theme.textSecondary)
            } else {
                ForEach(vm.friends) { friend in
                    NavigationLink(value: friend) {
                        HStack {
                            avatar(friend.userName)
                            Text(friend.userName).foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Image(systemName: "bubble.right").foregroundStyle(Theme.textFaint)
                        }
                        .padding(10)
                        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func avatar(_ name: String) -> some View {
        Circle()
            .fill(Theme.surfaceAlt)
            .frame(width: 36, height: 36)
            .overlay(Text(String(name.prefix(1))).foregroundStyle(Theme.mint).bold())
    }

    private func runSearch() {
        guard let uid = auth.uid else { return }
        Task { await vm.search(query: query, excluding: uid) }
    }
}

extension Friend: Hashable {
    static func == (lhs: Friend, rhs: Friend) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}
