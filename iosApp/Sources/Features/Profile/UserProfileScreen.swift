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
    @State private var showBlockConfirm = false
    @State private var showReportDialog = false
    @State private var showReportedConfirm = false
    @State private var friendsCount = 0
    /// 그 사람이 프로필에 띄우기로 선택(핀)한 다이어리 id — 타인 프로필엔 이 별들만 뜬다.
    @State private var pinnedIds: [String] = []
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
            // Android UserProfileScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82)

            // 중앙: 아바타 + 이름 + 칭호 (내 프로필과 동일 배치 — #6)
            VStack(spacing: 14) {
                avatar
                HStack(spacing: 6) {
                    Text(userName.isEmpty ? locale.t(.unknownUser) : userName)
                        .font(.minSans(26))
                        .foregroundStyle(Theme.textPrimary)
                    HiddenStarBadges(userId: userId, size: 13)
                }
                if let title = LocalizedNames.equippedTitle(equippedTitleId) {
                    let hiddenT = HiddenAchievements.byId(equippedTitleId) != nil
                    let titleColor = hiddenT ? Color(hex: 0xFFD86F) : Theme.navyAccent
                    Text(hiddenT ? "『\(title)』" : title)
                        .font(.minSans(15))
                        .foregroundStyle(titleColor)
                        .shadow(color: titleColor.opacity(0.9), radius: hiddenT ? 16 : 12)
                }
            }
            .offset(y: -60)

            // 떠다니는 통계/별 아이콘 (내 프로필과 동일한 FloatingStatBox — #6)
            FloatingStatBox(items: bubbleData.items, onTap: handleBubbleTap, avoidCenterYFraction: 0.42)

            // 하단: 친구 추가/채팅 액션
            VStack {
                Spacer()
                actionRow
                    .padding(.bottom, 24)
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
                            // 차단은 되돌리기 번거로운 동작(친구 해제 포함)이라 확인창을 거친다. 해제는 바로.
                            if isBlocked { Task { await toggleBlock() } } else { showBlockConfirm = true }
                        } label: {
                            Label(locale.t(isBlocked ? .unblockAction : .blockAction),
                                  systemImage: isBlocked ? "hand.raised.slash" : "hand.raised")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .tint(Theme.navyAccent)
                }
            }
        }
        .navigationDestination(isPresented: $openChat) {
            ChatScreen(friendId: userId, friendName: userName, myUid: auth.uid ?? "")
        }
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
        // 차단 확인 — 무엇이 숨겨지는지(별/댓글) + 친구 해제를 미리 알린다(Android 패리티).
        .alert(String(format: locale.t(.blockConfirmTitle),
                      userName.isEmpty ? locale.t(.unknownUser) : userName),
               isPresented: $showBlockConfirm) {
            Button(locale.t(.commonCancel), role: .cancel) {}
            Button(locale.t(.blockAction), role: .destructive) { Task { await toggleBlock() } }
        } message: {
            Text(locale.t(.blockConfirmMsg))
        }
        .task {
            hidden.start()
            if let doc = try? await FirestoreService.users.document(userId).getDocument() {
                profileImageUrl = doc.get("profileImageUrl") as? String
                equippedTitleId = doc.get("equippedTitle") as? String
                pinnedIds = (doc.get("pinnedDiaries") as? [String]) ?? []
            }
            // 떠다니는 통계용 친구 수(#6).
            let fsnap = try? await FirestoreService.friends(of: userId).getDocuments()
            friendsCount = fsnap?.documents.count ?? 0
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
            await ModerationRepository.block(userId: myUid, targetId: userId, targetName: userName,
                                             targetPhotoUrl: profileImageUrl ?? "")
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
                    .font(.minSans(20))
                    .foregroundStyle(Theme.textPrimary)
                // 히든 업적 달성자 전용 크리스탈 배지(34-4).
                HiddenStarBadges(userId: userId, size: 13)
            }
            // 칭호는 언어 전환에 맞춰 표시(로케일 해석)
            if let title = LocalizedNames.equippedTitle(equippedTitleId) {
                // 히든 칭호는 금색 + 『 』 로 감싸 일반 칭호와 구분.
                let hiddenT = HiddenAchievements.byId(equippedTitleId) != nil
                let titleColor = hiddenT ? Color(hex: 0xFFD86F) : Theme.navyAccent
                Text(hiddenT ? "『\(title)』" : title)
                    .font(.minSans(12))
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(titleColor.opacity(0.2), in: Capsule())
                    .foregroundStyle(titleColor)
            }
        }
        .padding(.top, 8)
    }

    /// 내 프로필(ProfileScreen)과 동일한 톤 — 민트 후광 + 민트→블루 그라데이션 테두리(#15).
    private var avatar: some View {
        ZStack {
            Circle()
                .fill(RadialGradient(colors: [Theme.navyAccent.opacity(0.28), .clear],
                                     center: .center, startRadius: 0, endRadius: 90))
                .frame(width: 140, height: 140)
            Group {
                if let url = profileImageUrl, !url.isEmpty {
                    AsyncImage(url: URL(string: url)) { image in
                        image.resizable().scaledToFill()
                    } placeholder: { Theme.surfaceAlt }
                } else {
                    Theme.surfaceAlt.overlay(
                        Text(String((userName.isEmpty ? "?" : userName).prefix(1)))
                            .font(.minSans(40))
                            .foregroundStyle(Theme.navyAccent)
                    )
                }
            }
            // 프로필 사진 바깥 링(테두리)은 제거 — 후광만 남긴다(2026-07-17 사용자 지시).
            .frame(width: 96, height: 96)
            .clipShape(Circle())
        }
    }

    @ViewBuilder
    private var actionRow: some View {
        if isMe {
            Text(locale.t(.userProfileMe))
                .font(.minSans(15)).foregroundStyle(Theme.textSecondary)
        } else if isFriend {
            HStack(spacing: 12) {
                Label(locale.t(.userStatusFriend), systemImage: "checkmark.seal.fill")
                    .font(.minSans(15)).foregroundStyle(Theme.navyAccent)
                Button { openChat = true } label: {
                    Label(locale.t(.userChatAction), systemImage: "bubble.left.fill")
                        .font(.minSans(15))
                        .padding(.horizontal, 16).padding(.vertical, 8)
                        .background(Theme.navyAccent.opacity(0.18), in: Capsule())
                        .foregroundStyle(Theme.navyAccent)
                }
            }
        } else {
            Button {
                Task { await sendRequest() }
            } label: {
                Label(requested ? locale.t(.userRequested) : locale.t(.userAddFriend),
                      systemImage: requested ? "checkmark" : "person.badge.plus")
                    .font(.minSans(15))
                    .padding(.horizontal, 18).padding(.vertical, 9)
                    .background((requested ? Theme.textFaint : Theme.navyAccent).opacity(0.18), in: Capsule())
                    .foregroundStyle(requested ? Theme.textSecondary : Theme.navyAccent)
            }
            .disabled(requested)
        }
    }

    /// 떠다니는 통계/별 버블 — 내 프로필(ProfileScreen)과 동일 구성(#6).
    /// 통계(좋아요·친구·별 수·조회) + 그 사람의 핀 별(탭 → 지도 길찾기) + 달성한 히든 업적 아이콘.
    private var bubbleData: (items: [StatBubble], diaryAt: [Int: Diary]) {
        var items: [StatBubble] = []
        var diaryAt: [Int: Diary] = [:]
        items.append(StatBubble(systemImage: "heart.fill", count: totalLikes,
                                color: Color(hex: 0xE7556B), label: locale.t(.statLikes), burstOnTap: true))
        items.append(StatBubble(systemImage: "person.fill", count: friendsCount,
                                color: Theme.navyAccent, label: locale.t(.profileFriends), burstOnTap: true))
        items.append(StatBubble(systemImage: "book.fill", count: visibleDiaries.count,
                                color: Color(hex: 0xF7E067), label: locale.t(.profileDiaries)))
        items.append(StatBubble(systemImage: "eye.fill", count: totalViews,
                                color: Theme.navyAccent, label: locale.t(.statViews)))
        // 그 사람이 "프로필에 띄우기"로 선택(핀)한 별만 — 전체 다이어리를 다 띄우지 않는다.
        // (Android UserProfileScreen pinnedDiaries 패리티. 공개 범위 필터는 visibleDiaries 가 이미 적용.)
        let pinned = pinnedIds.compactMap { id in visibleDiaries.first { $0.id == id } }
        for d in pinned {
            diaryAt[items.count] = d
            items.append(StatBubble(
                systemImage: "star.fill", count: 0, color: StarStyle.color(d.starColor),
                label: d.title.isEmpty ? locale.t(.userNoTitle) : d.title,
                showCount: false, starType: d.starType, starColorIndex: d.starColor
            ))
        }
        for ach in theirHiddenAch {
            items.append(StatBubble(
                systemImage: ach.icon.systemImage, count: 0, color: ach.icon.color,
                label: LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title,
                burstOnTap: true, showCount: false, hiddenEffect: ach.effect
            ))
        }
        return (items, diaryAt)
    }

    /// 핀 별 버블을 빠르게 탭하면 지도(루트)로 나가 그 별 위치로 카메라 + 파동 후 도보 길찾기.
    /// (Android NavGraph UserProfileScreen.onOpenDiary = MapFocusState.request(withRoute) 패리티.
    ///  통계 버블은 동작 없음 — 하트/친구/업적은 버스트만.)
    private func handleBubbleTap(_ idx: Int) {
        if let d = bubbleData.diaryAt[idx], let id = d.id {
            MapFocusStore.shared.request(diaryId: id, withRoute: true)
        }
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
