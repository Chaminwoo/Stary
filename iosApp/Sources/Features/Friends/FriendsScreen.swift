import SwiftUI

/// 친구 탭 — 사용자 검색/요청 보내기, 받은 요청 수락·거절, 친구 목록(→채팅).
struct FriendsScreen: View {
    @EnvironmentObject var auth: AuthManager
    /// 친구의 "최근 별"을 찾기 위한 다이어리 목록(비공개/익명은 여기서 걸러 쓴다).
    @EnvironmentObject var store: DiaryStore
    @StateObject private var vm = FriendsViewModel()
    /// 읽음 기록 — 채팅 화면과 공유(행 탭 시 markRead).
    @ObservedObject private var readStore = ChatReadStore.shared
    @State private var query = ""
    /// 하단 토스트(Android StaryToast 대응) — 친구 요청 전송/실패 피드백.
    @State private var toast: String?
    /// 프로필 사진 탭 → 타인 프로필 push(Android 사진 탭=프로필 패리티).
    @State private var profileTarget: FriendProfileTarget?

    struct FriendProfileTarget: Identifiable {
        let userId: String
        let userName: String
        var id: String { userId }
    }

    // 루트(MainTabView)의 단일 NavigationStack 에 push 되므로 자체 스택은 두지 않는다(Android 단일 NavHost 대응).
    var body: some View {
        ZStack {
            // Android FriendScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82)
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    searchSection
                    inviteSection
                    if !vm.requests.isEmpty { requestsSection }
                    friendsSection
                }
                .padding(16)
            }
            if let t = toast {
                ToastView(text: t)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }
        }
        .navigationTitle(LocaleManager.shared.t(.tabFriends))
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: Friend.self) { friend in
            ChatScreen(friend: friend, myUid: auth.uid ?? "")
        }
        // 사진 탭 → 타인 프로필 push(Android onOpenProfile 대응).
        .navigationDestination(isPresented: Binding(
            get: { profileTarget != nil }, set: { if !$0 { profileTarget = nil } }
        )) {
            if let t = profileTarget {
                UserProfileScreen(userId: t.userId, userName: t.userName)
            }
        }
        .onAppear { if let uid = auth.uid { vm.start(uid: uid) } }
        .onDisappear { vm.stop() }
        .firstVisitInfo(key: "friends", systemImage: "person.2.fill",
                        title: LocaleManager.shared.t(.onbFriendsTitle),
                        message: LocaleManager.shared.t(.onbFriendsMsg))
    }

    private var searchSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                TextField(LocaleManager.shared.t(.friendSearchPlaceholder), text: $query)
                    .padding(10)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textPrimary)
                    .onSubmit { runSearch() }
                Button(LocaleManager.shared.t(.commonSearch)) { runSearch() }
                    .tint(Theme.mint)
            }
            if vm.searching { StarLoadingView(size: 26) }
            ForEach(vm.results) { user in
                HStack {
                    Button { profileTarget = FriendProfileTarget(userId: user.userId, userName: user.userName) } label: {
                        avatar(user.userName, photoUrl: user.profileImageUrl ?? "", userId: user.userId)
                    }
                    .buttonStyle(.plain)
                    Text(user.userName).foregroundStyle(Theme.textPrimary)
                    HiddenStarBadges(userId: user.userId, size: 11)
                    Spacer()
                    // 이미 친구/요청 보냄 → 상태 칩, 아니면 추가 버튼. (Android StatusChip 패리티)
                    if vm.friends.contains(where: { $0.userId == user.userId }) {
                        statusChip(LocaleManager.shared.t(.friendStatusFriend))
                    } else if vm.outgoingIds.contains(user.userId) {
                        statusChip(LocaleManager.shared.t(.friendStatusRequested))
                    } else {
                        Button(LocaleManager.shared.t(.friendAdd)) {
                            guard let uid = auth.uid else { return }
                            Task {
                                let ok = await vm.sendRequest(fromId: uid, fromName: auth.displayName, to: user)
                                showToast(ok
                                    ? String(format: LocaleManager.shared.t(.friendRequestSent), user.userName)
                                    : LocaleManager.shared.t(.friendRequestFail))
                            }
                        }
                        .font(.poorStory(12)).tint(Theme.mint)
                    }
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    /// 상태 칩(친구/요청됨) — Android StatusChip(민트 알약) 대응.
    private func statusChip(_ text: String) -> some View {
        Text(text)
            .font(.poorStory(12))
            .foregroundStyle(Theme.mint)
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(Theme.mint.opacity(0.10), in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.mint.opacity(0.25), lineWidth: 1))
    }

    private func showToast(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            toast = nil
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
            Text(LocaleManager.shared.t(.friendRequests)).font(.poorStory(17)).foregroundStyle(Theme.textPrimary)
            ForEach(vm.requests) { req in
                HStack {
                    Button { profileTarget = FriendProfileTarget(userId: req.fromId, userName: req.fromName) } label: {
                        avatar(req.fromName, photoUrl: req.fromPhotoUrl, userId: req.fromId)
                    }
                    .buttonStyle(.plain)
                    Text(req.fromName).foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Button(LocaleManager.shared.t(.friendAccept)) {
                        guard let uid = auth.uid else { return }
                        Task { await vm.accept(req, myUid: uid, myName: auth.displayName) }
                    }
                    .font(.poorStory(12)).tint(Theme.mint)
                    Button(LocaleManager.shared.t(.friendDecline)) { Task { await vm.decline(req) } }
                        .font(.poorStory(12)).tint(.red)
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    /// 친구 목록 — 메신저형 행(Android FriendRow 패리티):
    /// [프로필 사진] [이름 / 마지막 채팅 · 상대시간] [그 친구의 최근 별]. 행 탭 = 채팅.
    /// 최근 별을 탭하면 지도로 가서 그 별까지 도보 길찾기(프로필 핀 별과 동일 동선).
    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(LocaleManager.shared.t(.friendMyFriends)) \(vm.friends.count)").font(.poorStory(17)).foregroundStyle(Theme.textPrimary)
            if vm.friends.isEmpty {
                Text(LocaleManager.shared.t(.friendEmpty))
                    .font(.poorStory(15)).foregroundStyle(Theme.textSecondary)
            } else {
                ForEach(vm.friends) { friend in
                    ZStack(alignment: .trailing) {
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
                        // 사진 탭 = 프로필 — 행(채팅)보다 먼저 탭을 받게 겹쳐 둔다(Android 사진 탭 패리티).
                        Button {
                            profileTarget = FriendProfileTarget(userId: friend.userId, userName: friend.userName)
                        } label: {
                            Color.clear
                                .frame(width: 52, height: 52)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .padding(.leading, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        // 최근 별 버튼은 행 위에 겹쳐 둔다 — 행(채팅)보다 먼저 탭을 받는다.
                        if let star = latestStar(of: friend.userId), let id = star.id {
                            Button {
                                MapFocusStore.shared.request(diaryId: id, withRoute: true)
                            } label: {
                                StarView(type: star.starType, colorIndex: star.starColor, size: 26)
                                    .frame(width: 40, height: 40)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .padding(.trailing, 8)
                        }
                    }
                }
            }
        }
    }

    /// 친구가 가장 최근에 남긴, 내가 볼 수 있는 별(비공개/익명 제외). Android observeLatestVisibleDiaryOf 패리티.
    private func latestStar(of userId: String) -> Diary? {
        store.diaries
            .filter { $0.userId == userId && $0.visibilityType != "private" && !$0.isAnonymous }
            .max { $0.createdAt < $1.createdAt }
    }

    private func friendRow(_ friend: Friend) -> some View {
        let summary = vm.chatSummaries[friend.userId]
        let hasStar = latestStar(of: friend.userId) != nil

        return HStack(spacing: 12) {
            // 사진은 텍스트 2줄보다 조금 크게(52pt)
            FriendAvatar(name: friend.userName, photoUrl: friend.photoUrl, userId: friend.userId, size: 52)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 5) {
                    Text(friend.userName.isEmpty ? LocaleManager.shared.t(.friendNoName) : friend.userName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                    // 히든 업적 달성자 전용 크리스탈 배지(34-4).
                    HiddenStarBadges(userId: friend.userId, size: 11)
                }
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
            // 최근 별이 놓일 자리(겹쳐 둔 버튼과 겹치지 않게 폭만 비워 둔다).
            if hasStar {
                Color.clear.frame(width: 40, height: 40)
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
