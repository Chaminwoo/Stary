import PhotosUI
import SwiftUI

/// 프로필 탭 — 내 통계 + 업적(칭호 장착) + 프로필 사진 + 내 별 목록 + 로그아웃.
struct ProfileScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore

    @State private var friendsCount = 0
    @State private var profileImageUrl: String?
    @State private var equippedTitleId: String?
    @State private var photoItem: PhotosPickerItem?

    private var mine: [Diary] { store.mine(uid: auth.uid).sorted { $0.createdAt > $1.createdAt } }
    private var totalViews: Int { mine.reduce(0) { $0 + $1.viewCount } }
    private var totalLikes: Int { mine.reduce(0) { $0 + $1.likeCount } }
    private var stats: UserStats {
        Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: 0)
    }
    private var unlocked: Set<String> { Achievements.unlockedIds(stats) }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 18) {
                        header
                        statRow
                        achievementsSection
                        myDiaries
                        AboutView()
                        signOutButton
                    }
                    .padding(16)
                }
            }
            .navigationTitle("프로필")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink { MusicScreen() } label: {
                        Image(systemName: "music.note")
                    }
                    .tint(Theme.mint)
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    NavigationLink { SettingsScreen() } label: {
                        Image(systemName: "gearshape")
                    }
                    .tint(Theme.mint)
                    NavigationLink { NotificationsScreen() } label: {
                        Image(systemName: "bell")
                    }
                    .tint(Theme.mint)
                }
            }
            .navigationDestination(for: Diary.self) { DetailScreen(diary: $0) }
            .task {
                guard let uid = auth.uid else { return }
                let snap = try? await FirestoreService.friends(of: uid).getDocuments()
                friendsCount = snap?.documents.count ?? 0
                if let doc = try? await FirestoreService.users.document(uid).getDocument() {
                    profileImageUrl = doc.get("profileImageUrl") as? String
                    equippedTitleId = doc.get("equippedTitle") as? String
                }
            }
            .onChange(of: photoItem) { item in
                Task {
                    guard let uid = auth.uid, let item,
                          let data = try? await item.loadTransferable(type: Data.self) else { return }
                    if let url = try? await ImageUploader.uploadProfile(uid: uid, data: data) {
                        profileImageUrl = url
                    }
                }
            }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                ZStack(alignment: .bottomTrailing) {
                    avatarImage
                    Image(systemName: "camera.circle.fill")
                        .font(.title3)
                        .foregroundStyle(Theme.mint)
                        .background(Circle().fill(Theme.background))
                }
            }
            Text(auth.displayName)
                .font(.title3).bold()
                .foregroundStyle(Theme.textPrimary)
            if let title = Achievements.byId(equippedTitleId)?.titleName {
                Text(title)
                    .font(.caption).bold()
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(Theme.mint.opacity(0.2), in: Capsule())
                    .foregroundStyle(Theme.mint)
            }
        }
        .padding(.top, 8)
    }

    private var avatarImage: some View {
        Group {
            if let url = profileImageUrl, !url.isEmpty {
                AsyncImage(url: URL(string: url)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Theme.surfaceAlt
                }
            } else {
                Theme.surfaceAlt.overlay(
                    Text(String(auth.displayName.prefix(1)))
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(Theme.mint)
                )
            }
        }
        .frame(width: 84, height: 84)
        .clipShape(Circle())
    }

    private var statRow: some View {
        HStack(spacing: 12) {
            statCell("별", mine.count)
            statCell("조회", totalViews)
            statCell("좋아요", totalLikes)
        }
    }

    private func statCell(_ label: String, _ value: Int) -> some View {
        VStack(spacing: 4) {
            Text("\(value)").font(.title3).bold().foregroundStyle(Theme.textPrimary)
            Text(label).font(.caption).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var achievementsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("업적").font(.headline).foregroundStyle(Theme.textPrimary)
                Spacer()
                Text("\(unlocked.count) / \(Achievements.all.count)")
                    .font(.caption).foregroundStyle(Theme.mint)
            }
            ForEach(Achievements.all) { ach in
                let done = unlocked.contains(ach.id)
                HStack(spacing: 10) {
                    Image(systemName: done ? "checkmark.seal.fill" : "lock.fill")
                        .foregroundStyle(done ? Theme.mint : Theme.textFaint)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ach.name).font(.subheadline).foregroundStyle(Theme.textPrimary)
                        Text(ach.hidden && !done ? "???" : ach.condition)
                            .font(.caption2).foregroundStyle(Theme.textSecondary)
                    }
                    Spacer()
                    if case .title = ach.reward, done {
                        Button(equippedTitleId == ach.id ? "장착됨" : "장착") {
                            equipTitle(equippedTitleId == ach.id ? nil : ach.id)
                        }
                        .font(.caption2).tint(Theme.mint)
                    } else {
                        rewardBadge(ach.reward)
                    }
                }
                .opacity(done ? 1 : 0.6)
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    @ViewBuilder
    private func rewardBadge(_ reward: Reward) -> some View {
        switch reward {
        case .title(let n):
            Text(n).font(.caption2).foregroundStyle(Theme.textFaint)
        case .shape(let t):
            StarView(type: t, colorIndex: 0, size: 22, glow: false)
        case .color(let c):
            Circle().fill(StarStyle.fill(c)).frame(width: 18, height: 18)
        }
    }

    private var myDiaries: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("내 별")
                .font(.headline)
                .foregroundStyle(Theme.textPrimary)
            if mine.isEmpty {
                Text("아직 남긴 별이 없어요.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.textSecondary)
            } else {
                ForEach(mine) { diary in
                    NavigationLink(value: diary) {
                        DiaryCard(diary: diary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// 칭호 장착(업적 id 저장). users/{uid}.equippedTitle 에 기록(타인 프로필에서도 보이도록).
    private func equipTitle(_ id: String?) {
        equippedTitleId = id
        guard let uid = auth.uid else { return }
        Task {
            try? await FirestoreService.users.document(uid)
                .setData(["equippedTitle": id ?? ""], merge: true)
        }
    }

    private var signOutButton: some View {
        Button(role: .destructive) {
            auth.signOut()
        } label: {
            Text("로그아웃")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14))
        }
        .tint(.red)
    }
}
