import FirebaseFirestore
import SwiftUI

/// 타인 프로필 — 아바타/이름/장착 칭호 + 친구 액션(추가/채팅) + 그 사람의 별 목록.
/// (Android UserProfileScreen 패리티. 댓글/작성자 탭에서 진입.)
struct UserProfileScreen: View {
    let userId: String
    let userName: String

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @ObservedObject private var locale = LocaleManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var profileImageUrl: String?
    @State private var equippedTitleId: String?
    @State private var isFriend = false
    @State private var requested = false
    @State private var openChat = false
    @State private var isBlocked = false
    @State private var showReportDialog = false
    @State private var showReportedConfirm = false
    @ObservedObject private var hidden = HiddenAchievementStore.shared

    private var isMe: Bool { userId == auth.uid }

    /// 그 사람이 달성한 히든 업적(전용 아이콘/파티클로 표시).
    private var theirHiddenAch: [HiddenAchievement] {
        hidden.myIds(uid: userId).compactMap { HiddenAchievements.byId($0) }
    }

    /// 그 사람의 공개 별만(비공개 제외, 친구공개는 친구일 때만).
    private var visibleDiaries: [Diary] {
        store.diaries
            .filter { $0.userId == userId }
            .filter { d in
                switch d.visibilityType {
                case "private": return false
                case "friends": return isFriend || isMe
                default: return true
                }
            }
            .sorted { $0.createdAt > $1.createdAt }
    }

    private var totalLikes: Int { visibleDiaries.reduce(0) { $0 + $1.likeCount } }
    private var totalViews: Int { visibleDiaries.reduce(0) { $0 + $1.viewCount } }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 18) {
                    header
                    actionRow
                    statRow
                    hiddenSection
                    diariesSection
                }
                .padding(16)
            }
        }
        .navigationTitle(locale.t(.profileTitle))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !isMe, auth.uid != nil {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button { showReportDialog = true } label: {
                            Label(locale.t(.reportUser), systemImage: "exclamationmark.bubble")
                        }
                        Button(role: .destructive) {
                            Task { await toggleBlock() }
                        } label: {
                            Label(locale.t(isBlocked ? .unblockAction : .blockAction),
                                  systemImage: isBlocked ? "hand.raised.slash" : "hand.raised")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .tint(Theme.mint)
                }
            }
        }
        .navigationDestination(isPresented: $openChat) {
            ChatScreen(friendId: userId, friendName: userName, myUid: auth.uid ?? "")
        }
        .navigationDestination(for: Diary.self) { DetailScreen(diary: $0) }
        .reportDialog(title: locale.t(.reportUser), isPresented: $showReportDialog) { reason in
            guard let myUid = auth.uid else { return }
            Task {
                await ModerationRepository.report(reporterId: myUid, type: "user",
                                                  targetId: userId, targetOwnerId: userId, reason: reason)
                showReportedConfirm = true
            }
        }
        .alert(locale.t(.toastReported), isPresented: $showReportedConfirm) {
            Button("OK", role: .cancel) {}
        }
        .task {
            hidden.start()
            if let doc = try? await FirestoreService.users.document(userId).getDocument() {
                profileImageUrl = doc.get("profileImageUrl") as? String
                equippedTitleId = doc.get("equippedTitle") as? String
            }
            if let myUid = auth.uid, !isMe {
                let f = try? await FirestoreService.friends(of: myUid).document(userId).getDocument()
                isFriend = f?.exists ?? false
                isBlocked = await ModerationRepository.isBlocked(userId: myUid, targetId: userId)
            }
        }
    }

    /// 차단/해제 토글. 차단 시 친구 양방향 해제(Android 패리티).
    private func toggleBlock() async {
        guard let myUid = auth.uid, !isMe else { return }
        if isBlocked {
            await ModerationRepository.unblock(userId: myUid, targetId: userId)
            isBlocked = false
        } else {
            await ModerationRepository.block(userId: myUid, targetId: userId, targetName: userName)
            isBlocked = true
            try? await FirestoreService.friends(of: myUid).document(userId).delete()
            try? await FirestoreService.friends(of: userId).document(myUid).delete()
            isFriend = false
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            avatar
            HStack(spacing: 6) {
                Text(userName.isEmpty ? locale.t(.unknownUser) : userName)
                    .font(.poorStory(20))
                    .foregroundStyle(Theme.textPrimary)
                // 히든 업적 달성자 전용 크리스탈 배지(34-4).
                HiddenStarBadges(userId: userId, size: 13)
            }
            // 칭호는 언어 전환에 맞춰 표시(로케일 해석)
            if let title = LocalizedNames.equippedTitle(equippedTitleId) {
                // 히든 칭호는 금색 + 『 』 로 감싸 일반 칭호와 구분.
                let hiddenT = HiddenAchievements.byId(equippedTitleId) != nil
                let titleColor = hiddenT ? Color(hex: 0xFFD86F) : Theme.mint
                Text(hiddenT ? "『\(title)』" : title)
                    .font(.poorStory(12))
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(titleColor.opacity(0.2), in: Capsule())
                    .foregroundStyle(titleColor)
            }
        }
        .padding(.top, 8)
    }

    private var avatar: some View {
        Group {
            if let url = profileImageUrl, !url.isEmpty {
                AsyncImage(url: URL(string: url)) { image in
                    image.resizable().scaledToFill()
                } placeholder: { Theme.surfaceAlt }
            } else {
                Theme.surfaceAlt.overlay(
                    Text(String((userName.isEmpty ? "?" : userName).prefix(1)))
                        .font(.poorStory(34))
                        .foregroundStyle(Theme.mint)
                )
            }
        }
        .frame(width: 84, height: 84)
        .clipShape(Circle())
    }

    @ViewBuilder
    private var actionRow: some View {
        if isMe {
            Text(locale.t(.userProfileMe))
                .font(.poorStory(15)).foregroundStyle(Theme.textSecondary)
        } else if isFriend {
            HStack(spacing: 12) {
                Label(locale.t(.userStatusFriend), systemImage: "checkmark.seal.fill")
                    .font(.poorStory(15)).foregroundStyle(Theme.mint)
                Button { openChat = true } label: {
                    Label(locale.t(.userChatAction), systemImage: "bubble.left.fill")
                        .font(.poorStory(15))
                        .padding(.horizontal, 16).padding(.vertical, 8)
                        .background(Theme.mint.opacity(0.18), in: Capsule())
                        .foregroundStyle(Theme.mint)
                }
            }
        } else {
            Button {
                Task { await sendRequest() }
            } label: {
                Label(requested ? locale.t(.userRequested) : locale.t(.userAddFriend),
                      systemImage: requested ? "checkmark" : "person.badge.plus")
                    .font(.poorStory(15))
                    .padding(.horizontal, 18).padding(.vertical, 9)
                    .background((requested ? Theme.textFaint : Theme.mint).opacity(0.18), in: Capsule())
                    .foregroundStyle(requested ? Theme.textSecondary : Theme.mint)
            }
            .disabled(requested)
        }
    }

    /// 그 사람이 달성한 히든 업적 — 전용 아이콘 + 파티클을 가로로 나열(달성한 게 있을 때만).
    @ViewBuilder
    private var hiddenSection: some View {
        if !theirHiddenAch.isEmpty {
            HStack(spacing: 16) {
                ForEach(theirHiddenAch) { ach in
                    VStack(spacing: 4) {
                        HiddenIconBadge(ach: ach, size: 40)
                        Text(LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title)
                            .font(.caption2).bold()
                            .foregroundStyle(Color(hex: 0xFFD86F))
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }

    private var statRow: some View {
        HStack(spacing: 12) {
            statCell(locale.t(.statStars), visibleDiaries.count)
            statCell(locale.t(.statViews), totalViews)
            statCell(locale.t(.statLikes), totalLikes)
        }
    }

    private func statCell(_ label: String, _ value: Int) -> some View {
        VStack(spacing: 4) {
            Text("\(value)").font(.poorStory(20)).foregroundStyle(Theme.textPrimary)
            Text(label).font(.poorStory(12)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var diariesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(locale.t(.userStarsHeader))
                .font(.poorStory(17))
                .foregroundStyle(Theme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
            if visibleDiaries.isEmpty {
                Text(locale.t(.userNoDiaries))
                    .font(.poorStory(15))
                    .foregroundStyle(Theme.textSecondary)
            } else {
                ForEach(visibleDiaries) { diary in
                    HStack(spacing: 8) {
                        NavigationLink(value: diary) {
                            DiaryCard(diary: diary)
                        }
                        .buttonStyle(.plain)
                        // 친구 별로 도보 길찾기 — 지도 탭으로 전환해 현위치→그 별 경로를 띄운다.
                        if !isMe {
                            Button { startRoute(to: diary) } label: {
                                Image(systemName: "figure.walk")
                                    .font(.headline)
                                    .frame(width: 44, height: 44)
                                    .background(Theme.mint.opacity(0.16), in: Circle())
                                    .foregroundStyle(Theme.mint)
                            }
                            .accessibilityLabel(locale.t(.routeDirections))
                        }
                    }
                }
            }
        }
    }

    /// 친구 별로 도보 길찾기 시작 — 지도 탭으로 전환해 현위치→그 별 경로를 띄운다.
    private func startRoute(to diary: Diary) {
        guard let id = diary.id else { return }
        MapFocusStore.shared.request(diaryId: id, withRoute: true)
        dismiss()
    }

    /// 친구 요청 전송(중복 방지) — FriendsViewModel.sendRequest 와 동일 스키마.
    private func sendRequest() async {
        guard let myUid = auth.uid, !isMe else { return }
        requested = true
        let dup = try? await FirestoreService.friendRequests
            .whereField("fromId", isEqualTo: myUid)
            .whereField("toId", isEqualTo: userId)
            .getDocuments()
        if let dup, !dup.documents.isEmpty { return } // 이미 요청됨
        let ref = FirestoreService.friendRequests.document()
        try? await ref.setData([
            "fromId": myUid, "fromName": auth.displayName,
            "fromPhotoUrl": "",
            "toId": userId, "toName": userName,
            "createdAt": FirestoreService.nowMillis,
        ])
    }
}
