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

    @ObservedObject private var hidden = HiddenAchievementStore.shared
    // 달성자 이름을 users/{uid} 의 "현재" 값으로 표시(선점 시점 스냅샷 아님) — 34-4(a).
    @ObservedObject private var directory = UserDirectory.shared
    @State private var friendsCount = 0
    @State private var invitedFriends = 0
    @State private var redeemedInvite = false
    @State private var myPioneerCodes: [String] = []
    @State private var tab = 0
    @State private var hiddenAlert: HiddenAchievement?

    private var mine: [Diary] { store.mine(uid: auth.uid) }
    /// 열람 업적은 "다른 사람의 다이어리" 기준 — 내 글 열람 기록 제외.
    private var othersViewedCount: Int {
        let myIds = Set(mine.compactMap { $0.id })
        return viewed.viewedIds.subtracting(myIds).count
    }
    private var stats: UserStats {
        Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: othersViewedCount,
                                  invitedFriends: invitedFriends, redeemedInvite: redeemedInvite)
    }
    private var unlocked: Set<String> { Achievements.unlockedIds(stats) }
    private var allNormalDone: Bool {
        !Achievements.all.isEmpty && unlocked.count >= Achievements.all.count
    }

    var body: some View {
        ZStack {
            // Android AchievementsScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82)
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
            // 진행도 = 막대 대신 **성운이 차오르는 밴드**(34-5). 달성할수록 성운이 오른쪽으로 짙어진다.
            NebulaProgressBand(fraction: Double(unlocked.count) / Double(Swift.max(Achievements.all.count, 1))) {
                Text("\(unlocked.count) / \(Achievements.all.count)")
                    .font(.caption).foregroundStyle(Theme.mint)
            }
            .frame(height: 52)
            ForEach(Achievements.all) { ach in
                achievementRow(ach)
            }
            // 개척 칭호(체크리스트 32) — 내가 개척한 나라만 노출, 장착 가능.
            ForEach(myPioneerCodes, id: \.self) { code in
                pioneerRow(code)
            }
            AboutView()
                .padding(.top, 12)
        }
        .padding(16)
    }

    /// 개척 칭호 행 — 항상 달성 상태, 탭으로 장착/해제. (Android NormalTab 개척 섹션 패리티)
    private func pioneerRow(_ code: String) -> some View {
        let titleId = PioneerQuest.titleId(code)
        let display = LocalizedNames.pioneerTitle(titleId) ?? LocalizedNames.countryName(code)
        return HStack(spacing: 10) {
            Image(systemName: "flag.fill").foregroundStyle(Theme.mint)
            VStack(alignment: .leading, spacing: 2) {
                Text(display).font(.subheadline).foregroundStyle(Theme.textPrimary)
                Text(locale.t(.pioneerCondition))
                    .font(.caption2).foregroundStyle(Theme.textSecondary)
            }
            Spacer()
            Button(equippedTitleId == titleId ? "장착됨" : "장착") {
                equipTitle(equippedTitleId == titleId ? nil : titleId)
            }
            .font(.caption2).tint(Theme.mint)
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
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
                // 칭호는 항상 노출 + 이 업적 달성자에게 붙는 전용 크리스탈 배지 미리보기(34-4).
                HStack(spacing: 6) {
                    Text(LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title)
                        .font(.subheadline).foregroundStyle(Theme.textPrimary)
                    HiddenStarBadge(type: ach.badgeType, colorIndex: ach.badgeColor, size: 14)
                }
                // 조건은 달성 후에만 공개.
                Text(claimed ? ach.condition : "???")
                    .font(.caption2)
                    .foregroundStyle(claimed ? Theme.textSecondary : Color(hex: 0xB388FF).opacity(0.85))
                if claimed {
                    // 달성자 이름은 선점 시점 스냅샷이 아니라 users/{uid} 의 **현재 닉네임**(34-4a).
                    let snapshot = (claim?.achieverName.isEmpty == false) ? claim!.achieverName : "?"
                    let name = directory.name(claim?.achieverId ?? "", fallback: snapshot)
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
        // 달성자 현재 이름 구독 시작(이미 구독 중이면 no-op).
        .task(id: claim?.achieverId) {
            if let id = claim?.achieverId, !id.isEmpty { directory.ensureWatching(id) }
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

    private static let green = Color(hex: 0x6EE7B7)
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
