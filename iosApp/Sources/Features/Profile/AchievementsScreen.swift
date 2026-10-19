import FirebaseFirestore
import SwiftUI

/// 업적 화면 — **공용 / 특별 / 히든** 3탭(2026-10-19 재구성, Android AchievementsScreen 패리티).
///  - 공용: 누구나 얻는 업적 — 칭호(주제별 묶음) · 별 모양 · 별 색(3열 타일).
///  - 특별: 모두가 갖지는 못하는 업적 — 특별한 순간 · 퀘스트 보상 · 초대 · 개척 칭호.
///  - 히든: 앱 전체에서 단 한 명.
/// 프로필의 칭호/업적 아이콘 탭으로 진입한다. 장착한 칭호는 users/{uid}.equippedTitle 에 기록.
struct AchievementsScreen: View {
    /// 프로필과 공유하는 장착 칭호 id(장착 즉시 프로필 칭호에 반영).
    @Binding var equippedTitleId: String?

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared

    @ObservedObject private var hidden = HiddenAchievementStore.shared
    // 달성자 이름을 users/{uid} 의 "현재" 값으로 표시(선점 시점 스냅샷 아님) — 34-4(a).
    @ObservedObject private var directory = UserDirectory.shared
    /// 배경 시차 스크롤(관찰하지 않는다 — 배경만 다시 그린다).
    @State private var bgScroll = BackgroundScroll()
    @State private var friendsCount = 0
    @State private var invitedFriends = 0
    @State private var redeemedInvite = false
    @State private var myPioneerCodes: [String] = []
    @State private var tab = 0
    /// 공용 탭 안의 작은 탭 — 0 칭호 / 1 별 모양 / 2 별 색.
    @State private var commonSub = 0
    @State private var hiddenAlert: HiddenAchievement?

    private var mine: [Diary] { store.mine(uid: auth.uid) }
    /// 열람 업적은 "다른 사람의 다이어리" 기준 — 내 글 열람 기록 제외.
    private var othersViewedCount: Int {
        let myIds = Set(mine.compactMap { $0.id })
        return viewed.viewedIds.subtracting(myIds).count
    }
    private var stats: UserStats {
        Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: othersViewedCount,
                                  invitedFriends: invitedFriends, redeemedInvite: redeemedInvite,
                                  uid: auth.uid, allDiaries: store.diaries, unlockedIds: Set(DiaryUnlockStore.shared.unlockedAt.keys))
    }
    private var unlocked: Set<String> { Achievements.unlockedIds(stats) }
    private var allNormalDone: Bool {
        !Achievements.all.isEmpty && unlocked.count >= Achievements.all.count
    }

    private static let special = Color(hex: 0xB388FF)
    private static let gold = Color(hex: 0xFFD86F)

    var body: some View {
        ZStack {
            // Android AchievementsScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82, scroll: bgScroll)
            VStack(spacing: 0) {
                // 공용 / 특별 / 히든(2026-10-19 재구성 — Android TabBar 패리티).
                HStack(spacing: 8) {
                    tabChip(locale.t(.achTabNormal), 0, Theme.navyAccent)
                    tabChip(locale.t(.achTabSpecial), 1, Self.special)
                    tabChip(locale.t(.achTabHidden), 2, Self.gold)
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 6)

                ScrollView {
                    switch tab {
                    case 0: commonList
                    case 1: specialList
                    default: hiddenListView
                    }
                }
                .backgroundScrollSource(bgScroll)
            }
        }
        .navigationTitle(locale.t(.navAchievements))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            hidden.start()
            guard let uid = auth.uid else { return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            friendsCount = snap?.documents.count ?? 0
            // 친구 초대 보상 통계(체크리스트 31)
            let invite = await InviteStore.fetchStats(uid: uid)
            invitedFriends = invite.invited
            redeemedInvite = invite.redeemed
            // 내가 개척한 나라(체크리스트 32)
            if let snap = try? await FirestoreService.db.collection(PioneerQuest.collection)
                .whereField("userId", isEqualTo: uid).getDocuments() {
                myPioneerCodes = snap.documents.map { $0.documentID }.sorted()
            }
            if equippedTitleId == nil,
               let doc = try? await FirestoreService.users.document(uid).getDocument() {
                equippedTitleId = doc.get("equippedTitle") as? String
            }
            runHiddenClaims()
        }
        .onChange(of: hidden.loaded) { _ in runHiddenClaims() }
        .onChange(of: friendsCount) { _ in runHiddenClaims() }
        .staryInfoDialog(locale.t(.hiddenWonTitle),
                         isPresented: Binding(get: { hiddenAlert != nil },
                                              set: { if !$0 { hiddenAlert = nil } }),
                         message: hiddenAlert.map { a in
                             "\(LocalizedNames.title(a.id, fallback: a.title) ?? a.title)\n\(LocalizedNames.condition(a.id, fallback: a.condition))\n\(locale.t(.hiddenWonFirst))"
                         }) { hiddenAlert = nil }
        .onAppear { QuestStore.report(.visitAchievements) }
        .firstVisitInfo(key: "achievements", systemImage: "trophy.fill",
                        title: locale.t(.onbAchievementsTitle),
                        message: locale.t(.onbAchievementsMsg))
    }

    private func tabChip(_ label: String, _ index: Int, _ accent: Color) -> some View {
        let selected = tab == index
        return Button { tab = index } label: {
            Text(label)
                .font(.minSans(14, selected ? .semibold : .normal))
                .foregroundStyle(selected ? accent : Theme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(selected ? accent.opacity(0.16) : Color.white.opacity(0.04),
                            in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12)
                    .stroke(selected ? accent.opacity(0.7) : Color.white.opacity(0.06), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // ── 공용 업적 — 칭호(주제별) / 별 모양 / 별 색 ──
    private var commonList: some View {
        let titles = Achievements.commonTitles
        let shapes = Achievements.commonShapes
        let colors = Achievements.commonColors
        let all = titles + shapes + colors
        let done = all.filter { unlocked.contains($0.id) }.count
        return VStack(alignment: .leading, spacing: 10) {
            Text(locale.t(.achIntro))
                .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
            // 진행도 = 막대 대신 **성운이 차오르는 밴드**(34-5). 달성할수록 성운이 오른쪽으로 짙어진다.
            NebulaProgressBand(fraction: Double(done) / Double(Swift.max(all.count, 1))) {
                Text("\(done) / \(all.count)")
                    .font(.minSans(12)).foregroundStyle(Theme.navyAccent)
            }
            .frame(height: 52)

            HStack(spacing: 8) {
                subChip(locale.t(.achSectionTitles), titles, 0)
                subChip(locale.t(.achSubShapes), shapes, 1)
                subChip(locale.t(.achSubColors), colors, 2)
            }
            .padding(.top, 2)
            Text(commonSub == 0 ? locale.t(.achTitlesHint) : locale.t(.achRewardsHint))
                .font(.minSans(11)).foregroundStyle(Theme.textSecondary.opacity(0.8))

            if commonSub == 0 {
                let groups = Achievements.commonTitleGroups
                ForEach(groups.indices, id: \.self) { i in
                    let group = groups[i]
                    themeHeader(themeLabel(group.0), group.1, accent: Theme.navyAccent)
                    ForEach(group.1) { achievementRow($0, accent: Theme.navyAccent) }
                }
            } else {
                // 모양·색은 이름이 짧고 그림이 중요해 3열 타일로 — 한눈에 무엇이 열렸는지 보인다.
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10, alignment: .top), count: 3),
                          spacing: 10) {
                    ForEach(commonSub == 1 ? shapes : colors) { rewardTile($0) }
                }
                .padding(.top, 4)
            }
        }
        .padding(16)
    }

    private func themeLabel(_ t: Achievements.Theme) -> String {
        switch t {
        case .record: return locale.t(.achThemeRecord)
        case .streak: return locale.t(.achThemeStreak)
        case .place: return locale.t(.achThemePlace)
        case .explore: return locale.t(.achThemeExplore)
        case .social: return locale.t(.achThemeSocial)
        case .decor: return locale.t(.achThemeDecor)
        case .meteor: return locale.t(.achThemeMeteor)
        }
    }

    /// 공용 탭 안의 작은 탭(칭호/모양/색) — 이름 아래에 달성 수.
    private func subChip(_ label: String, _ list: [Achievement], _ index: Int) -> some View {
        let selected = commonSub == index
        let done = list.filter { unlocked.contains($0.id) }.count
        return Button { commonSub = index } label: {
            VStack(spacing: 1) {
                Text(label).font(.minSans(13, selected ? .medium : .normal))
                    .foregroundStyle(selected ? Theme.textPrimary : Theme.textSecondary)
                Text("\(done) / \(list.count)").font(.minSans(10))
                    .foregroundStyle(selected ? Theme.navyAccent : Theme.textSecondary.opacity(0.7))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(selected ? Color.white.opacity(0.08) : Color.clear, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12)
                .stroke(selected ? Theme.navyAccent.opacity(0.45) : Color.white.opacity(0.06), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    /// 칭호 주제 머리글 — 이름 · 가는 선 · 달성 수.
    private func themeHeader(_ label: String, _ list: [Achievement], accent: Color) -> some View {
        let done = list.filter { unlocked.contains($0.id) }.count
        return HStack(spacing: 10) {
            Text(label).font(.minSans(13, .medium)).foregroundStyle(accent)
            Rectangle().fill(accent.opacity(0.18)).frame(height: 1)
            Text("\(done) / \(list.count)").font(.minSans(11))
                .foregroundStyle(done == list.count ? accent : Theme.textSecondary)
        }
        .padding(.top, 10)
    }

    /// 별 모양/색 타일 — 큰 미리보기 + 이름 + 조건. 열리면 그 별빛으로 은은하게 빛난다(Android RewardTile).
    private func rewardTile(_ ach: Achievement) -> some View {
        let done = unlocked.contains(ach.id)
        let glow: Color = { if case .color(let c) = ach.reward { return StarStyle.color(c) }; return StarStyle.color(9) }()
        return VStack(spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                Group {
                    switch ach.reward {
                    case .shape(let t): StarView(type: t, colorIndex: 9, size: 32, glow: false)
                    case .color(let c): Circle().fill(StarStyle.fill(c)).frame(width: 28, height: 28)
                    case .title: Image(systemName: "star.fill").foregroundStyle(Theme.navyAccent)
                    }
                }
                .frame(width: 52, height: 52)
                .opacity(done ? 1 : 0.28)
                .background(RadialGradient(colors: [done ? glow.opacity(0.26) : .clear, .clear],
                                           center: .center, startRadius: 0, endRadius: 30))
                if !done {
                    Image(systemName: "lock.fill").font(.system(size: 9))
                        .foregroundStyle(.white.opacity(0.85))
                        .frame(width: 16, height: 16)
                        .background(Color.black.opacity(0.65), in: Circle())
                }
            }
            Text(LocalizedNames.title(ach.id, fallback: ach.name) ?? ach.name)
                .font(.minSans(12, .medium))
                .foregroundStyle(done ? Theme.textPrimary : Theme.textSecondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(minHeight: 30, alignment: .top)
            Text(ach.hidden && !done ? "???" : LocalizedNames.condition(ach.id, fallback: ach.condition))
                .font(.minSans(10))
                .foregroundStyle(Theme.textSecondary.opacity(done ? 0.9 : 0.7))
                .multilineTextAlignment(.center)
                .lineLimit(3)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8).padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: 150, alignment: .top)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .stroke(done ? glow.opacity(0.35) : Color.white.opacity(0.06), lineWidth: 1))
    }

    // ── 특별 업적 — 특별한 순간 · 퀘스트 보상 · 초대 · 개척 ──
    private var specialList: some View {
        let list = Achievements.special
        let done = list.filter { unlocked.contains($0.id) }.count + myPioneerCodes.count
        return VStack(alignment: .leading, spacing: 10) {
            Text(locale.t(.achSpecialIntro))
                .font(.minSans(13)).foregroundStyle(Self.special.opacity(0.9))
            NebulaProgressBand(fraction: Double(done) / Double(Swift.max(list.count + myPioneerCodes.count, 1))) {
                Text(String(format: locale.t(.achSpecialCount), done))
                    .font(.minSans(12)).foregroundStyle(Self.special)
            }
            .frame(height: 52)

            let groups = Achievements.specialGroups
            ForEach(groups.indices, id: \.self) { i in
                let group = groups[i]
                specialHeader(specialTitle(group.0), specialSub(group.0),
                              done: group.1.filter { unlocked.contains($0.id) }.count, total: group.1.count)
                ForEach(group.1) { achievementRow($0, accent: Self.special) }
            }

            // 개척 칭호(체크리스트 32) — 내가 개척한 나라만, 장착 가능. 없으면 얻는 법을 한 장으로 안내.
            specialHeader(locale.t(.achSectionPioneer), locale.t(.pioneerCondition), done: myPioneerCodes.count, total: nil)
            if myPioneerCodes.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "flag.fill").foregroundStyle(Theme.textFaint)
                        .frame(width: 24)
                    Text(locale.t(.achPioneerEmpty))
                        .font(.minSans(11)).foregroundStyle(Theme.textSecondary)
                    Spacer(minLength: 0)
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            } else {
                ForEach(myPioneerCodes, id: \.self) { pioneerRow($0) }
            }
        }
        .padding(16)
    }

    private func specialTitle(_ g: Achievements.Special) -> String {
        switch g {
        case .moments: return locale.t(.achSpecialMoments)
        case .quest: return locale.t(.achSpecialQuest)
        case .invite: return locale.t(.achSpecialInvite)
        }
    }

    private func specialSub(_ g: Achievements.Special) -> String {
        switch g {
        case .moments: return locale.t(.achSpecialMomentsSub)
        case .quest: return locale.t(.achSpecialQuestSub)
        case .invite: return locale.t(.achSpecialInviteSub)
        }
    }

    /// 특별 묶음 머리글 — 제목·달성 수 + 한 줄 설명(어떻게 얻는지). total nil = 개수 제한 없음(개척).
    private func specialHeader(_ title: String, _ sub: String, done: Int, total: Int?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 10) {
                Text(title).font(.minSans(15, .semibold)).foregroundStyle(Theme.textPrimary)
                Rectangle().fill(Self.special.opacity(0.22)).frame(height: 1)
                Text(total.map { "\(done) / \($0)" } ?? "\(done)").font(.minSans(11))
                    .foregroundStyle(total == done ? Self.special : Theme.textSecondary)
            }
            Text(sub).font(.minSans(11)).foregroundStyle(Theme.textSecondary)
        }
        .padding(.top, 12)
    }

    /// 개척 칭호 행 — 항상 달성 상태, 탭으로 장착/해제. (Android SpecialTab 개척 묶음 패리티)
    private func pioneerRow(_ code: String) -> some View {
        let titleId = PioneerQuest.titleId(code)
        let display = LocalizedNames.pioneerTitle(titleId) ?? LocalizedNames.countryName(code)
        return HStack(spacing: 10) {
            Image(systemName: "flag.fill").foregroundStyle(Self.special)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(display).font(.minSans(15)).foregroundStyle(Theme.textPrimary)
                Text(locale.t(.pioneerCondition))
                    .font(.minSans(11)).foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            Button(equippedTitleId == titleId ? locale.t(.achEquipped) : locale.t(.achEquip)) {
                equipTitle(equippedTitleId == titleId ? nil : titleId)
            }
            .font(.minSans(11)).tint(Self.special)
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12)
            .stroke(equippedTitleId == titleId ? Self.special : .clear, lineWidth: 1.5))
    }

    private func achievementRow(_ ach: Achievement, accent: Color) -> some View {
        let done = unlocked.contains(ach.id)
        let isEquipped = equippedTitleId == ach.id
        return HStack(spacing: 10) {
            Group {
                switch ach.reward {
                case .title:
                    Image(systemName: done ? "star.fill" : "lock.fill")
                        .foregroundStyle(done ? accent : Theme.textFaint)
                case .shape, .color:
                    rewardBadge(ach)
                }
            }
            .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                // 업적명·조건 모두 언어 전환에 맞춰 표시(칭호 업적 + 별 모양/색 보상 업적).
                Text(LocalizedNames.title(ach.id, fallback: ach.name) ?? ach.name)
                    .font(.minSans(15)).foregroundStyle(Theme.textPrimary)
                // 숨김 업적은 달성 전까지 조건을 ??? 로 가린다(이름은 힌트로 보여 준다). Android 패리티.
                Text(ach.hidden && !done ? "???" : LocalizedNames.condition(ach.id, fallback: ach.condition))
                    .font(.minSans(11)).foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            if case .title = ach.reward, done {
                Button(isEquipped ? locale.t(.achEquipped) : locale.t(.achEquip)) {
                    equipTitle(isEquipped ? nil : ach.id)
                }
                .font(.minSans(11)).tint(accent)
            } else if done {
                Text(locale.t(.achObtained)).font(.minSans(11)).foregroundStyle(accent)
            }
        }
        .opacity(done ? 1 : 0.6)
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(isEquipped ? accent : .clear, lineWidth: 1.5))
    }

    @ViewBuilder
    private func rewardBadge(_ ach: Achievement) -> some View {
        switch ach.reward {
        case .title(let n):
            Text(LocalizedNames.title(ach.id, fallback: n) ?? n)
                .font(.minSans(11)).foregroundStyle(Theme.textFaint)
        case .shape(let t):
            StarView(type: t, colorIndex: 9, size: 22, glow: false)
        case .color(let c):
            Circle().fill(StarStyle.fill(c)).frame(width: 18, height: 18)
        }
    }

    // ── 히든 업적 ──
    private var hiddenListView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(locale.t(.achHiddenIntro))
                .font(.minSans(13))
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
            // 엠블럼(수집 진열장): 내 것 = 이펙트까지 / 남의 것 = 색·광택만 / 아직 아무도 = 어두운 봉인 + 흐린 이펙트.
            HiddenIconBadge(ach: ach, size: 48, state: mineClaim ? .owned : (claimed ? .claimed : .sealed))
                .frame(width: 52, height: 52)
            VStack(alignment: .leading, spacing: 2) {
                // 칭호는 항상 노출 + 이 업적 달성자에게 붙는 전용 크리스탈 배지 미리보기(34-4).
                HStack(spacing: 6) {
                    Text(LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title)
                        .font(.minSans(15)).foregroundStyle(Theme.textPrimary)
                    HiddenStarBadge(type: ach.badgeType, colorIndex: ach.badgeColor, size: 14)
                }
                // 조건은 달성 후에만 공개.
                Text(claimed ? LocalizedNames.condition(ach.id, fallback: ach.condition) : "???")
                    .font(.minSans(11))
                    .foregroundStyle(claimed ? Theme.textSecondary : Color(hex: 0xB388FF).opacity(0.85))
                if claimed {
                    // 달성자 이름은 선점 시점 스냅샷이 아니라 users/{uid} 의 **현재 닉네임**(34-4a).
                    let snapshot = (claim?.achieverName.isEmpty == false) ? claim!.achieverName : "?"
                    let name = directory.name(claim?.achieverId ?? "", fallback: snapshot)
                    Text(mineClaim ? locale.t(.achHiddenByMe)
                                   : String(format: locale.t(.achHiddenAchiever), name))
                        .font(.minSans(11, .medium))
                        .foregroundStyle(mineClaim ? Color(hex: 0xFFD86F) : Theme.navyAccent)
                }
            }
            Spacer()
            if mineClaim {
                Button(equippedTitleId == ach.id ? locale.t(.achEquipped) : locale.t(.achEquip)) {
                    equipTitle(equippedTitleId == ach.id ? nil : ach.id)
                }
                .font(.minSans(11)).tint(Color(hex: 0xFFD86F))
            } else if claimed {
                Image(systemName: "lock.fill").foregroundStyle(Theme.textFaint)
            } else {
                Text(locale.t(.achHiddenUnclaimed))
                    .font(.minSans(11)).foregroundStyle(Theme.textFaint)
            }
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
        // 달성자 현재 이름 구독 시작(이미 구독 중이면 no-op).
        .task(id: claim?.achieverId) {
            if let id = claim?.achieverId, !id.isEmpty { directory.ensureWatching(id) }
        }
    }

    /// 칭호 장착(업적 id 저장). users/{uid}.equippedTitle 에 기록(타인 프로필에서도 보이도록).
    private func equipTitle(_ id: String?) {
        equippedTitleId = id
        guard let uid = auth.uid else { return }
        if id != nil { QuestStore.report(.equipTitle) }
        // 로컬 캐시 즉시 갱신 — ProfileScreen.onChange 가 저장하지만 AchievementsEntry 경로에서도 보장
        if let id, !id.isEmpty {
            UserDefaults.standard.set(id, forKey: "equipped_title_\(uid)")
        } else {
            UserDefaults.standard.removeObject(forKey: "equipped_title_\(uid)")
        }
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
        Task {
            // 선점 기록에 박히는 이름은 서버 확인값 — 모르면 이번엔 시도하지 않는다(CLAUDE.md §2.6).
            guard let name = await auth.confirmedNameOrWait() else { return }
            let won = await hidden.attemptAutoClaims(stats: s, allNormalDone: done, uid: uid, name: name)
            if let first = won.first { hiddenAlert = first }
        }
    }
}

/// 업적 진행도(34-5) — 퍼센트 막대 대신 **성운이 차오르는 밴드**.
/// 왼쪽부터 달성 비율만큼 성운(민트+보라 blob)이 짙어지고, 나머지는 빈 밤하늘(잔별만).
/// 경계는 blob 별 알파 감쇠로 부드럽게(하드 컷 없음), 값이 바뀌면 채움이 애니메이션.
/// 별/blob 배치는 고정 시드라 리컴포지션마다 흔들리지 않는다. (Android NebulaProgress 패리티)
private struct NebulaProgressBand<Content: View>: View {
    let fraction: Double
    @ViewBuilder let content: Content

    var body: some View {
        ZStack(alignment: .leading) {
            NebulaCanvas(filled: min(max(fraction, 0), 1))
                .animation(.easeOut(duration: 0.7), value: fraction)
            content
                .padding(.horizontal, 14)
        }
        .frame(maxWidth: .infinity)
        .background(Color.white.opacity(0.03))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

/// 성운 밴드 캔버스 — [Animatable] 로 채움 비율이 부드럽게 보간된다.
private struct NebulaCanvas: View, Animatable {
    var filled: Double

    var animatableData: Double {
        get { filled }
        set { filled = newValue }
    }

    private static let green = Color(hex: 0x9FB3E8) // 남색 계열 라이트(구 민트)
    private static let purple = Color(hex: 0xB388FF)
    /// (x비율, y비율, 색) — Android 와 동일 배치.
    private static let blobs: [(Double, Double, Color)] = [
        (0.06, 0.55, green), (0.20, 0.30, purple), (0.34, 0.68, green),
        (0.48, 0.36, purple), (0.62, 0.60, green), (0.76, 0.32, purple),
        (0.90, 0.58, green),
    ]

    var body: some View {
        Canvas { ctx, size in
            let w = size.width
            let h = size.height
            let fw = w * filled
            let soft = w * 0.14 // 채움 경계가 흐려지는 폭

            // 성운 blob — 채움 경계를 넘어갈수록 옅어진다(부드러운 채움).
            for (px, py, color) in Self.blobs {
                let cx = w * px
                let reveal = min(max((fw - cx) / soft + 0.5, 0), 1)
                if reveal <= 0.01 { continue }
                let r = h * 0.95
                let grad = Gradient(stops: [
                    .init(color: color.opacity(0.30 * reveal), location: 0),
                    .init(color: color.opacity(0.10 * reveal), location: 0.5),
                    .init(color: .clear, location: 1),
                ])
                ctx.fill(
                    Path(ellipseIn: CGRect(x: cx - r, y: h * py - r, width: r * 2, height: r * 2)),
                    with: .radialGradient(grad, center: CGPoint(x: cx, y: h * py),
                                          startRadius: 0, endRadius: r)
                )
            }

            // 잔별 — 전 구간에 뿌리되, 성운 안쪽이 더 밝다.
            for i in 0..<30 {
                let fx = Double(i * 37 % 100) / 100
                let fy = Double(i * 61 % 100) / 100
                let cx = w * fx
                let inNebula = cx <= fw ? 1.0 : 0.35
                let rad = 0.7 + Double(i % 3) * 0.5
                let alpha = 0.10 + 0.35 * inNebula * (Double(i % 5) / 5 + 0.2)
                let cy = h * (0.12 + 0.76 * fy)
                ctx.fill(
                    Path(ellipseIn: CGRect(x: cx - rad, y: cy - rad, width: rad * 2, height: rad * 2)),
                    with: .color(.white.opacity(alpha))
                )
            }
        }
    }
}
