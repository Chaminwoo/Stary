import FirebaseFirestore
import SwiftUI

/// 업적 화면 — 일반(칭호/별 모양·색) / 히든 두 탭. (Android AchievementsScreen 패리티)
/// 프로필의 칭호/업적 아이콘 탭으로 진입한다. 장착한 칭호는 users/{uid}.equippedTitle 에 기록.
struct AchievementsScreen: View {
    /// 프로필과 공유하는 장착 칭호 id(장착 즉시 프로필 칭호에 반영).
    @Binding var equippedTitleId: String?

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared

    @StateObject private var hidden = HiddenAchievementStore()
    @State private var friendsCount = 0
    @State private var tab = 0
    @State private var hiddenAlert: HiddenAchievement?

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
    private var allNormalDone: Bool {
        !Achievements.all.isEmpty && unlocked.count >= Achievements.all.count
    }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 0) {
                Picker("", selection: $tab) {
                    Text(locale.t(.achTabNormal)).tag(0)
                    Text(locale.t(.achTabHidden)).tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 6)

                ScrollView {
                    if tab == 0 { normalList } else { hiddenListView }
                }
            }
        }
        .navigationTitle(locale.t(.navAchievements))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            hidden.start()
            guard let uid = auth.uid else { return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            friendsCount = snap?.documents.count ?? 0
            if equippedTitleId == nil,
               let doc = try? await FirestoreService.users.document(uid).getDocument() {
                equippedTitleId = doc.get("equippedTitle") as? String
            }
            runHiddenClaims()
        }
        .onChange(of: hidden.loaded) { _ in runHiddenClaims() }
        .onChange(of: friendsCount) { _ in runHiddenClaims() }
        .alert("히든 업적 달성!",
               isPresented: Binding(get: { hiddenAlert != nil },
                                    set: { if !$0 { hiddenAlert = nil } })) {
            Button("확인", role: .cancel) { hiddenAlert = nil }
        } message: {
            if let a = hiddenAlert {
                Text("\(LocalizedNames.title(a.id, fallback: a.title) ?? a.title)\n\(a.condition)\n앱에서 단 한 명 — 당신이 처음입니다")
            }
        }
        .firstVisitInfo(key: "achievements", systemImage: "trophy.fill",
                        title: locale.t(.onbAchievementsTitle),
                        message: locale.t(.onbAchievementsMsg))
    }

    // ── 일반 업적 ──
    private var normalList: some View {
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

    private func achievementRow(_ ach: Achievement) -> some View {
        let done = unlocked.contains(ach.id)
        return HStack(spacing: 10) {
            Image(systemName: done ? "checkmark.seal.fill" : "lock.fill")
                .foregroundStyle(done ? Theme.mint : Theme.textFaint)
            VStack(alignment: .leading, spacing: 2) {
                // 칭호 업적명(=칭호)은 언어 전환에 맞춰 표시(보상 업적은 매핑 밖 → 원문)
                Text(LocalizedNames.title(ach.id, fallback: ach.name) ?? ach.name)
                    .font(.subheadline).foregroundStyle(Theme.textPrimary)
                Text(ach.condition)
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

    // ── 히든 업적 ──
    private var hiddenListView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(locale.t(.achHiddenIntro))
                .font(.footnote)
                .foregroundStyle(Color(hex: 0xFFD86F).opacity(0.85))
            ForEach(HiddenAchievements.all) { ach in
                hiddenRow(ach)
            }
        }
        .padding(16)
    }

    private func hiddenRow(_ ach: HiddenAchievement) -> some View {
        let claim = hidden.claims[ach.id]
        let claimed = claim?.claimed == true
        let mineClaim = claim?.achieverId == auth.uid && (auth.uid?.isEmpty == false)
        return HStack(spacing: 10) {
            HiddenIconBadge(ach: ach, size: 40)
                .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                // 칭호는 항상 노출. (언어 전환에 맞춰 로케일 해석)
                Text(LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title)
                    .font(.subheadline).foregroundStyle(Theme.textPrimary)
                // 조건은 달성 후에만 공개.
                Text(claimed ? ach.condition : "???")
                    .font(.caption2)
                    .foregroundStyle(claimed ? Theme.textSecondary : Color(hex: 0xB388FF).opacity(0.85))
                if claimed {
                    let name = (claim?.achieverName.isEmpty == false) ? claim!.achieverName : "?"
                    Text(mineClaim ? locale.t(.achHiddenByMe)
                                   : String(format: locale.t(.achHiddenAchiever), name))
                        .font(.caption2).bold()
                        .foregroundStyle(mineClaim ? Color(hex: 0xFFD86F) : Theme.mint)
                }
            }
            Spacer()
            if mineClaim {
                Button(equippedTitleId == ach.id ? "장착됨" : "장착") {
                    equipTitle(equippedTitleId == ach.id ? nil : ach.id)
                }
                .font(.caption2).tint(Color(hex: 0xFFD86F))
            } else if claimed {
                Image(systemName: "lock.fill").foregroundStyle(Theme.textFaint)
            } else {
                Text(locale.t(.achHiddenUnclaimed))
                    .font(.caption2).foregroundStyle(Theme.textFaint)
            }
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
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

    /// 자동 조건을 만족한 히든 업적을 선점 시도하고, 새로 달성했으면 알림을 띄운다.
    private func runHiddenClaims() {
        guard let uid = auth.uid else { return }
        let s = stats
        let done = allNormalDone
        let name = auth.displayName
        Task {
            let won = await hidden.attemptAutoClaims(stats: s, allNormalDone: done, uid: uid, name: name)
            if let first = won.first { hiddenAlert = first }
        }
    }
}
