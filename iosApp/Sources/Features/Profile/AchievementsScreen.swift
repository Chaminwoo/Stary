import FirebaseFirestore
import SwiftUI

/// 업적 화면 — 칭호/별 모양/별 색 업적 진행도 + 칭호 장착. (Android AchievementsScreen 패리티)
/// 프로필의 칭호/업적 아이콘 탭으로 진입한다. 장착한 칭호는 users/{uid}.equippedTitle 에 기록.
struct AchievementsScreen: View {
    /// 프로필과 공유하는 장착 칭호 id(장착 즉시 프로필 칭호에 반영).
    @Binding var equippedTitleId: String?

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared

    @State private var friendsCount = 0

    private var mine: [Diary] { store.mine(uid: auth.uid) }
    /// 열람 업적은 "다른 사람의 다이어리" 기준 — 내 글 열람 기록 제외.
    private var othersViewedCount: Int {
        let myIds = Set(mine.compactMap { $0.id })
        return viewed.viewedIds.subtracting(myIds).count
    }
    private var stats: UserStats {
        Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: othersViewedCount)
    }
    private var unlocked: Set<String> { Achievements.unlockedIds(stats) }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("\(unlocked.count) / \(Achievements.all.count)")
                            .font(.caption).foregroundStyle(Theme.mint)
                        Spacer()
                    }
                    ForEach(Achievements.all) { ach in
                        achievementRow(ach)
                    }
                    AboutView()
                        .padding(.top, 12)
                }
                .padding(16)
            }
        }
        .navigationTitle(locale.t(.navAchievements))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let uid = auth.uid else { return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            friendsCount = snap?.documents.count ?? 0
            if equippedTitleId == nil,
               let doc = try? await FirestoreService.users.document(uid).getDocument() {
                equippedTitleId = doc.get("equippedTitle") as? String
            }
        }
    }

    private func achievementRow(_ ach: Achievement) -> some View {
        let done = unlocked.contains(ach.id)
        return HStack(spacing: 10) {
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

    /// 칭호 장착(업적 id 저장). users/{uid}.equippedTitle 에 기록(타인 프로필에서도 보이도록).
    private func equipTitle(_ id: String?) {
        equippedTitleId = id
        guard let uid = auth.uid else { return }
        Task {
            try? await FirestoreService.users.document(uid)
                .setData(["equippedTitle": id ?? ""], merge: true)
        }
    }
}
