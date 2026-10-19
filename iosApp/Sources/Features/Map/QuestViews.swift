import SwiftUI

/// 열쇠·퀘스트 강조색 — 웰컴 별/개척 비콘과 같은 "시스템 안내" 금빛(Android `QuestGold` 0xFFD27A).
enum QuestTheme {
    static let gold = Color(hex: 0xFFD27A)
    static let goldDeep = Color(hex: 0xFFB259)
    static let ink = Color(hex: 0x2A1A00)
}

/// 지도 우측 버튼 열의 "!"(오늘의 퀘스트) — 예전 몰입 버튼 자리(2026-10-19). Android `QuestButton` 패리티.
/// 받을 보상이 있으면 금빛 + 우상단 민트 점 + 은은한 맥박, 오늘 것을 다 받았으면 흰색 반투명.
struct QuestMapButton: View {
    @ObservedObject private var quests = QuestStore.shared
    let action: () -> Void
    @State private var pulse = false

    var body: some View {
        let claimable = quests.daily?.claimable ?? 0
        let allDone = quests.daily?.allClaimed == true
        Button(action: action) {
            Image(systemName: "exclamationmark")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(allDone ? Color.white.opacity(0.55) : QuestTheme.gold)
                .scaleEffect(claimable > 0 && pulse ? 1.12 : 1)
                .frame(width: 48, height: 48)
                .background(Color(hex: 0x111120).opacity(0.93), in: Circle())
                .raisedCosmicBorder()
                .shadow(color: .black.opacity(0.3), radius: 5, y: 2)
                .overlay(alignment: .topTrailing) {
                    if claimable > 0 {
                        Circle()
                            .fill(Theme.mint)
                            .frame(width: 11, height: 11)
                            .overlay(Circle().strokeBorder(Color(hex: 0x111120), lineWidth: 1.5))
                            .offset(x: 1, y: -1)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(LocaleManager.shared.t(.questToday))
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

/// 보유 열쇠 칩 — 퀘스트 창 머리 / 잠금 화면. `bump` 가 바뀌면 통통 튄다.
struct KeyChip: View {
    let keys: Int
    var bump: Int = 0
    @State private var scale: CGFloat = 1

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "key.fill")
                .font(.system(size: 12, weight: .semibold))
            Text("\(keys)")
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
        }
        .foregroundStyle(QuestTheme.gold)
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background(QuestTheme.gold.opacity(0.14), in: Capsule())
        .overlay(Capsule().strokeBorder(QuestTheme.gold.opacity(0.45), lineWidth: 0.75))
        .scaleEffect(scale)
        .accessibilityLabel(String(format: LocaleManager.shared.t(.questKeysOwned), keys))
        .onChange(of: bump) { _ in
            scale = 1.35
            withAnimation(.spring(response: 0.35, dampingFraction: 0.4)) { scale = 1 }
        }
    }
}

/// 오늘의 퀘스트 창 — Android `QuestSheet` 패리티(3개 퀘스트 + 보너스 + 새 퀘스트까지 남은 시간).
/// 화면 첫 진입 안내(FirstVisitInfoCard)와 같은 카드 모양. 떠 있는 동안 지도 집중 모드는 멈춘다(`MapChromeState.holdIdle`).
struct QuestSheetView: View {
    @ObservedObject private var quests = QuestStore.shared
    @ObservedObject private var locale = LocaleManager.shared
    let onDismiss: () -> Void
    @State private var bump = 0
    @State private var now = Date()
    @State private var showAdKey = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
                .onTapGesture { onDismiss() }
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Text(locale.t(.questToday))
                        .font(.minSans(20, .medium))
                        .foregroundStyle(Theme.textPrimary)
                    Spacer()
                    // 열쇠 칩 → "광고를 보고 열쇠를 얻으시겠어요?"
                    KeyChip(keys: quests.keys, bump: bump)
                        .contentShape(Capsule())
                        .onTapGesture { showAdKey = true }
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color(hex: 0x8A93A3))
                            .frame(width: 32, height: 32)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(locale.t(.questClose))
                }
                Text(locale.t(.questSubtitle))
                    .font(.minSans(13))
                    .foregroundStyle(Color(hex: 0xB8C0CC))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 6)
                    .padding(.bottom, 16)

                if let d = quests.daily, d.day == QuestStore.today() {
                    VStack(spacing: 10) {
                        ForEach(Array(d.quests.enumerated()), id: \.element.id) { i, q in
                            QuestRowView(
                                index: i, symbol: questSymbol(q.event),
                                title: locale.t(q.title), hint: locale.t(q.hint),
                                target: q.target, reward: DailyQuests.rewardKeys,
                                progress: min(d.progressOf(q.id), q.target),
                                claimed: d.claimed.contains(q.id)
                            ) { rewarded(quests.claim(q.id)) }
                        }
                        // 보너스 퀘스트 — 위 3개 보상을 모두 받으면 받을 수 있다(Android QuestSheet 4번째 행 패리티).
                        QuestRowView(
                            index: d.ids.count, symbol: "trophy.fill",
                            title: locale.t(.questBonusTitle), hint: locale.t(.questBonusHint),
                            target: d.ids.count, reward: DailyQuests.bonusKeys,
                            progress: d.claimed.intersection(d.ids).count,
                            claimed: d.bonus
                        ) { rewarded(quests.claimBonus()) }
                    }
                } else {
                    HStack(spacing: 10) {
                        ProgressView().tint(QuestTheme.gold)
                        Text(locale.t(.questLoading))
                            .font(.minSans(13))
                            .foregroundStyle(Color(hex: 0xB8C0CC))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)
                }

                Text(String(format: locale.t(.questResetIn), resetText))
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0x7D8696))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.top, 10)
            }
            .padding(.horizontal, 18).padding(.vertical, 20)
            .background(Color(hex: 0x121821), in: RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(LinearGradient(colors: [QuestTheme.gold.opacity(0.55), Color(hex: 0x3B82F6).opacity(0.40)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1)
            )
            .padding(.horizontal, 18)
            .frame(maxWidth: 520)

            // 열쇠 칩 → 광고 팝업. 퀘스트 창 자체가 오버레이라 그 위에 한 겹 더 얹는다.
            if showAdKey {
                Color.black.opacity(0.45).ignoresSafeArea()
                    .onTapGesture { showAdKey = false }
                KeyAdDialogCard(
                    accent: QuestTheme.gold,
                    message: locale.t(.questAdKeyAsk),
                    onWatchAd: {
                        showAdKey = false
                        watchAdForKey()
                    },
                    onCancel: { showAdKey = false }
                )
                .transition(.opacity)
            }
        }
        .onAppear {
            quests.ensureToday()
            MapChromeState.shared.holdIdle()
        }
        .onDisappear { MapChromeState.shared.releaseIdle() }
        // 30초마다 남은 시간 갱신 + 자정이 지나면 새 목록.
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                now = Date()
                quests.ensureToday()
            }
        }
    }

    /// 보상 수령 결과 — 받았으면 진동 + 칩 튀기기 + 토스트.
    private func rewarded(_ gained: Int) {
        guard gained > 0 else { return }
        Haptics.celebrate()
        bump += 1
        GlobalToast.shared.show(String(format: locale.t(.questKeyGained), gained))
    }

    /// 광고를 끝까지 보면 열쇠 1개(Android QuestSheet `watchAdForKey` → `grantAdKey` 패리티).
    private func watchAdForKey() {
        let ads = AdsManager.shared
        guard !ads.showing else { return }
        guard ads.isConfigured else {
            GlobalToast.shared.show(locale.t(.detailAdUnavailable))
            return
        }
        ads.showRewardedWhenReady(
            onWaiting: { GlobalToast.shared.show(locale.t(.detailAdLoading)) },
            onUnavailable: { GlobalToast.shared.show(locale.t(.detailAdUnavailable)) },
            onResult: { ok in
                if ok {
                    quests.grantAdKey()
                    rewarded(1)
                } else {
                    GlobalToast.shared.show(locale.t(.detailAdNotFinished))
                }
            }
        )
    }

    /// "N시간 M분" — 기기 현지 자정까지.
    private var resetText: String {
        let cal = Calendar.current
        let midnight = cal.startOfDay(for: cal.date(byAdding: .day, value: 1, to: now) ?? now)
        let left = max(0, Int(midnight.timeIntervalSince(now) / 60))
        let h = left / 60, m = left % 60
        return h > 0 ? String(format: locale.t(.questHm), h, m) : String(format: locale.t(.questM), max(1, m))
    }
}

/// 이벤트 → 행 아이콘(Android `iconOf` 와 같은 의미).
private func questSymbol(_ e: QuestEvent) -> String {
    switch e {
    case .tutorialStar: return "star.fill"
    case .upload: return "plus"
    case .uploadMedia: return "photo.fill"
    case .viewOther: return "eye.fill"
    case .like: return "heart.fill"
    case .comment: return "bubble.left.fill"
    case .equipTitle: return "rosette"
    case .profileEdit: return "pencil"
    case .pinStar: return "pin.fill"
    case .constellation: return "sparkles"
    case .filter: return "safari.fill"
    case .globe: return "globe.asia.australia.fill"
    case .meteor: return "bolt.fill"
    case .visitAchievements: return "trophy.fill"
    case .visitStarlog: return "book.fill"
    case .visitProfile: return "person.fill"
    case .friendRequest: return "person.badge.plus"
    case .share: return "square.and.arrow.up"
    case .music: return "music.note"
    case .focus: return "leaf.fill"
    case .unlockKey: return "lock.open.fill"
    }
}

private struct QuestRowView: View {
    let index: Int
    let symbol: String
    let title: String
    let hint: String
    let target: Int
    let reward: Int
    let progress: Int
    let claimed: Bool
    let onClaim: () -> Void
    @ObservedObject private var locale = LocaleManager.shared
    @State private var shown = false

    var body: some View {
        let complete = progress >= target
        let glow = complete && !claimed
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(claimed
                    ? LinearGradient(colors: [Theme.mint.opacity(0.35), Color(hex: 0x3B82F6).opacity(0.35)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    : LinearGradient(colors: [Color(hex: 0x26304A), Color(hex: 0x161C2C)], startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: claimed ? "checkmark" : symbol)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(claimed ? Theme.mint : QuestTheme.gold)
            }
            .frame(width: 38, height: 38)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.minSans(14, .medium))
                    .foregroundStyle(claimed ? Color(hex: 0x8A93A3) : Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(hint)
                    .font(.minSans(11.5))
                    .foregroundStyle(Color(hex: 0x8A93A3))
                    .fixedSize(horizontal: false, vertical: true)
                if target > 1 && !claimed {
                    HStack(spacing: 8) {
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.13))
                                Capsule()
                                    .fill(LinearGradient(colors: [Theme.mint, QuestTheme.gold], startPoint: .leading, endPoint: .trailing))
                                    .frame(width: g.size.width * CGFloat(progress) / CGFloat(max(1, target)))
                            }
                        }
                        .frame(height: 4)
                        Text("\(progress)/\(target)")
                            .font(.system(size: 11).monospacedDigit())
                            .foregroundStyle(Color(hex: 0xB8C0CC))
                    }
                    .padding(.top, 5)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if claimed {
                Text(locale.t(.questClaimed))
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0x6B7484))
            } else if complete {
                Button(action: onClaim) {
                    HStack(spacing: 4) {
                        Image(systemName: "key.fill").font(.system(size: 12, weight: .semibold))
                        Text(locale.t(.questClaim)).font(.minSans(13, .medium))
                    }
                    .foregroundStyle(QuestTheme.ink)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(LinearGradient(colors: [QuestTheme.gold, QuestTheme.goldDeep], startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            } else {
                HStack(spacing: 2) {
                    Image(systemName: "key.fill").font(.system(size: 11))
                    Text("×\(reward)").font(.minSans(12, .medium))
                }
                .foregroundStyle(QuestTheme.gold.opacity(0.75))
            }
        }
        .padding(12)
        .background(glow ? QuestTheme.gold.opacity(0.08) : Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .strokeBorder(glow ? QuestTheme.gold.opacity(0.45) : Color.white.opacity(0.10), lineWidth: 0.75))
        .opacity(shown ? 1 : 0)
        .offset(y: shown ? 0 : 10)
        .onAppear {
            withAnimation(.easeOut(duration: 0.26).delay(0.06 * Double(index))) { shown = true }
        }
    }
}
