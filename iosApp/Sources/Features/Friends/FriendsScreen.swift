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
                        inviteSection
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
            .firstVisitInfo(key: "friends", systemImage: "person.2.fill",
                            title: LocaleManager.shared.t(.onbFriendsTitle),
                            message: LocaleManager.shared.t(.onbFriendsMsg))
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
                    avatar(user.userName, photoUrl: user.profileImageUrl ?? "", userId: user.userId)
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

    /// 친구 초대(체크리스트 31) — 초대 링크 공유. 가입+리딤 시 양쪽 다 칭호 보상. (Android InviteCard 패리티)
    private var inviteSection: some View {
        Group {
            if let uid = auth.uid {
                ShareLink(item: LocaleManager.shared.t(.inviteShareText) + "\n" + AppConfig.inviteLink(inviterUid: uid)) {
                    HStack(spacing: 12) {
                        Image(systemName: "person.badge.plus")
                            .foregroundStyle(Theme.mint)
                            .frame(width: 40, height: 40)
                            .background(Theme.mint.opacity(0.14), in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(LocaleManager.shared.t(.inviteFriends))
                                .font(.subheadline).bold()
                                .foregroundStyle(Theme.textPrimary)
                            Text(LocaleManager.shared.t(.inviteFriendsDesc))
                                .font(.caption)
                                .foregroundStyle(Theme.textSecondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Theme.textSecondary)
                    }
                    .padding(12)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }

    private var requestsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("받은 요청").font(.headline).foregroundStyle(Theme.textPrimary)
            ForEach(vm.requests) { req in
                HStack {
                    avatar(req.fromName, photoUrl: req.fromPhotoUrl, userId: req.fromId)
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
                            avatar(friend.userName, photoUrl: friend.photoUrl, userId: friend.userId)
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

    private func avatar(_ name: String, photoUrl: String = "", userId: String = "") -> some View {
        FriendAvatar(name: name, photoUrl: photoUrl, userId: userId)
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

/// 친구/요청/검색 아바타 — photoUrl 이 있으면 사진, 비어 있으면 userId 로 users/{uid}.profileImageUrl 조회.
/// (Android FriendScreen Avatar 패리티. 예전 친구 데이터의 빈 photoUrl 도 채워 보여준다.)
private struct FriendAvatar: View {
    let name: String
    let photoUrl: String
    let userId: String
    @State private var resolved: String?

    private var effectiveUrl: String? {
        if !photoUrl.isEmpty { return photoUrl }
        return resolved
    }

    var body: some View {
        Group {
            if let url = effectiveUrl, !url.isEmpty {
                AsyncImage(url: URL(string: url)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Theme.surfaceAlt
                }
            } else {
                Theme.surfaceAlt.overlay(
                    Text(String(name.prefix(1)).uppercased())
                        .foregroundStyle(Theme.mint).bold()
                )
            }
        }
        .frame(width: 36, height: 36)
        .clipShape(Circle())
        .overlay(Circle().stroke(Theme.mint.opacity(0.30), lineWidth: 1))
        .task(id: userId) {
            if photoUrl.isEmpty, !userId.isEmpty {
                resolved = await ProfileImageCache.shared.url(for: userId)
            }
        }
    }
}
