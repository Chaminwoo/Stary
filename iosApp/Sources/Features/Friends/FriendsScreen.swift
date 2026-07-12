import SwiftUI

/// 친구 탭 — 사용자 검색/요청 보내기, 받은 요청 수락·거절, 친구 목록(→채팅).
struct FriendsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @StateObject private var vm = FriendsViewModel()
    /// 읽음 기록 — markRead 되면 미읽음 파란 점이 즉시 사라지도록 관찰한다.
    @ObservedObject private var readStore = ChatReadStore.shared
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

    /// 친구 목록 — 메신저형 행(Android FriendRow 패리티):
    /// [프로필 사진] [이름 / 마지막 채팅 · 상대시간] [미읽음 파란 점]. 행 탭 = 채팅.
    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("친구 \(vm.friends.count)").font(.headline).foregroundStyle(Theme.textPrimary)
            if vm.friends.isEmpty {
                Text("아직 친구가 없어요. 위에서 찾아 추가해보세요.")
                    .font(.subheadline).foregroundStyle(Theme.textSecondary)
            } else {
                ForEach(vm.friends) { friend in
                    NavigationLink(value: friend) {
                        friendRow(friend)
                    }
                    .buttonStyle(.plain)
                    .simultaneousGesture(TapGesture().onEnded {
                        // 열자마자 읽음 처리 — 파란 점이 즉시 사라진다(ChatScreen 도 중복 호출하지만 멱등).
                        if let uid = auth.uid {
                            ChatReadStore.shared.markRead(AppConfig.chatId(uid, friend.userId))
                        }
                    })
                }
            }
        }
    }

    private func friendRow(_ friend: Friend) -> some View {
        let summary = vm.chatSummaries[friend.userId]
        let chatId = AppConfig.chatId(auth.uid ?? "", friend.userId)
        let unread = summary.map { s in
            !s.lastMessage.isEmpty
                && s.lastSenderId != (auth.uid ?? "")
                && s.updatedAt > readStore.lastRead(chatId)
        } ?? false

        return HStack(spacing: 12) {
            // 사진은 텍스트 2줄보다 조금 크게(52pt)
            FriendAvatar(name: friend.userName, photoUrl: friend.photoUrl, userId: friend.userId, size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(friend.userName.isEmpty ? "(이름 없음)" : friend.userName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                if let summary, !summary.lastMessage.isEmpty {
                    Text("\(summary.lastMessage) · \(RelativeTime.string(fromMillis: summary.updatedAt))")
                        .font(.caption)
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                } else {
                    Text(LocaleManager.shared.t(.friendNoChatYet))
                        .font(.caption)
                        .foregroundStyle(Theme.textSecondary.opacity(0.7))
                        .lineLimit(1)
                }
            }
            Spacer()
            if unread {
                Circle()
                    .fill(Color(red: 0.298, green: 0.553, blue: 1.0)) // 0xFF4C8DFF
                    .frame(width: 10, height: 10)
            }
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
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
    var size: CGFloat = 36
    @State private var resolved: String?

    private var effectiveUrl: String? {
        if !photoUrl.isEmpty { return photoUrl }
        return resolved
    }

    var body: some View {
        Group {
            if let url = effectiveUrl, !url.isEmpty {
                // 작은 아바타 — 원본 대신 다운샘플 썸네일로 즉시 표시(목록 스크롤 렉 방지).
                AvatarThumbView(url: url, pixelSize: size * 3)
            } else {
                Theme.surfaceAlt.overlay(
                    Text(String(name.prefix(1)).uppercased())
                        .foregroundStyle(Theme.mint).bold()
                )
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Theme.mint.opacity(0.30), lineWidth: 1))
        .task(id: userId) {
            if photoUrl.isEmpty, !userId.isEmpty {
                resolved = await ProfileImageCache.shared.url(for: userId)
            }
        }
    }
}
