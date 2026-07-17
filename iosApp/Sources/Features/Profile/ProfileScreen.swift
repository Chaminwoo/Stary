import FirebaseAuth
import FirebaseFirestore
import PhotosUI
import SwiftUI

/// 프로필 탭 — 중앙 아바타/이름/칭호 + **떠다니는 통계 아이콘**(좋아요·친구·다이어리·업적) +
/// 핀한 내 별이 별 모양으로 함께 떠다니고, 하단 로그아웃. 우상단 +로 띄울 별을 고른다.
/// (Android ProfileScreen + FloatingStatBox 패리티. 업적 목록은 AchievementsScreen 으로 분리.)
struct ProfileScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var viewed: ViewedStore
    @ObservedObject private var locale = LocaleManager.shared

    @State private var friendsCount = 0
    @State private var profileImageUrl: String?
    @State private var equippedTitleId: String?
    @State private var photoItem: PhotosPickerItem?
    @State private var pinnedIds: [String] = []
    @State private var showPinPicker = false
    // 루트 스택 push 로 전환(Android 단일 NavHost 대응) — 자체 NavigationPath 대신 목적지별 bool.
    @State private var showAchievements = false
    @State private var showMyStars = false
    @State private var showNicknameEditor = false
    @State private var nicknameDraft = ""
    @ObservedObject private var hidden = HiddenAchievementStore.shared
    @State private var hiddenAlert: HiddenAchievement?

    private var mine: [Diary] { store.mine(uid: auth.uid).sorted { $0.createdAt > $1.createdAt } }
    private var othersViewedCount: Int {
        let myIds = Set(mine.compactMap { $0.id })
        return viewed.viewedIds.subtracting(myIds).count
    }
    private var stats: UserStats {
        Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: othersViewedCount)
    }
    private var unlockedCount: Int { Achievements.unlockedIds(stats).count }
    // 칭호는 언어 전환에 맞춰 표시(정의는 한국어 데이터, 표시만 로케일 해석)
    private var equippedTitle: String? { LocalizedNames.equippedTitle(equippedTitleId) }
    /// 히든 칭호는 일반 칭호와 다르게(금색 + 『 』) 표시.
    private var equippedTitleIsHidden: Bool { HiddenAchievements.byId(equippedTitleId) != nil }
    private var titleDisplayText: String {
        guard let t = equippedTitle else { return locale.t(.userNoTitle) }
        return equippedTitleIsHidden ? "『\(t)』" : t
    }
    private var titleDisplayColor: Color {
        if equippedTitle == nil { return Theme.textSecondary }
        return equippedTitleIsHidden ? Color(hex: 0xFFD86F) : Theme.navyAccent
    }

    /// 내가 달성한 히든 업적 id.
    private var myHiddenIds: [String] { hidden.myIds(uid: auth.uid) }
    /// 내가 달성한 히든 업적(떠다니는 아이콘용).
    private var myHiddenAch: [HiddenAchievement] { myHiddenIds.compactMap { HiddenAchievements.byId($0) } }
    /// 히든을 제외한 모든 일반 업적을 달성했는가.
    private var allNormalDone: Bool {
        !Achievements.all.isEmpty && Achievements.unlockedIds(stats).count >= Achievements.all.count
    }

    /// 핀한 다이어리(최대 3) — 저장된 id 순서대로.
    private var pinnedDiaries: [Diary] {
        pinnedIds.compactMap { id in mine.first { $0.id == id } }
    }

    /// 떠다니는 통계 + 핀 별.
    private var bubbles: [StatBubble] {
        var arr: [StatBubble] = [
            StatBubble(systemImage: "heart.fill", count: stats.likesReceived,
                       color: Color(hex: 0xE7556B), label: locale.t(.statLikes), burstOnTap: true),
            StatBubble(systemImage: "person.fill", count: stats.friends,
                       color: Theme.navyAccent, label: locale.t(.profileFriends)),
            StatBubble(systemImage: "book.fill", count: stats.diariesCreated,
                       color: Color(hex: 0xF7E067), label: locale.t(.profileDiaries)),
            StatBubble(systemImage: "trophy.fill", count: unlockedCount,
                       color: Color(hex: 0xF2C94C), label: locale.t(.profileAchievements), burstOnTap: true),
        ]
        for d in pinnedDiaries {
            arr.append(StatBubble(
                systemImage: "star.fill", count: 0, color: StarStyle.color(d.starColor),
                label: d.title.isEmpty ? locale.t(.profileMyStars) : d.title,
                showCount: false, starType: d.starType, starColorIndex: d.starColor
            ))
        }
        // 내가 달성한 히든 업적 — 떠다니는 아이콘(오라/잔상/버스트).
        for ach in myHiddenAch {
            arr.append(StatBubble(
                systemImage: ach.icon.systemImage, count: 0, color: ach.icon.color,
                label: LocalizedNames.title(ach.id, fallback: ach.title) ?? ach.title,
                burstOnTap: true, showCount: false, hiddenEffect: ach.effect
            ))
        }
        return arr
    }

    // 루트(MainTabView)의 단일 NavigationStack 에 push 되므로 자체 스택은 두지 않는다(Android 단일 NavHost 대응).
    var body: some View {
        Group {
            ZStack {
                // Android ProfileScreen 배경 — mydiary_bg + 검정 0.82 틴트.
                ScreenBackground(name: "mydiary_bg", darken: 0.82)

                // 중앙: 아바타 + 이름 + 칭호 (화면 가운데보다 살짝 위)
                VStack(spacing: 14) {
                    avatar
                    // 닉네임 — 누르면 변경(기본=구글 닉네임). 레이아웃은 그대로.
                    HStack(spacing: 6) {
                        Text(auth.displayName.isEmpty ? "Stargazer" : auth.displayName)
                            .font(.poorStory(26))
                            .foregroundStyle(Theme.textPrimary)
                            .onTapGesture {
                                nicknameDraft = auth.displayName
                                showNicknameEditor = true
                            }
                        // 내가 달성한 히든 업적 전용 크리스탈 배지(34-4).
                        HiddenStarBadges(userId: auth.uid ?? "", size: 13)
                    }
                    Button { showAchievements = true } label: {
                        Text(titleDisplayText)
                            .font(.poorStory(15))
                            .foregroundStyle(titleDisplayColor)
                            .shadow(color: (equippedTitle == nil ? .clear : titleDisplayColor).opacity(0.9),
                                    radius: equippedTitleIsHidden ? 16 : 12)
                            .padding(6)
                    }
                    .buttonStyle(.plain)
                }
                .offset(y: -44)

                // 떠다니는 통계 + 핀 별 (전체 화면 오버레이, 중앙/하단은 비켜서 배치)
                FloatingStatBox(items: bubbles, onTap: handleBubbleTap, avoidCenterYFraction: 0.42)

                // 하단 로그아웃
                VStack {
                    Spacer()
                    Button(role: .destructive) { auth.signOut() } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                            Text(locale.t(.drawerLogout))
                        }
                        .font(.poorStory(15))
                        .foregroundStyle(Color(hex: 0xFF6B6B))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color(hex: 0x14181C).opacity(0.8), in: RoundedRectangle(cornerRadius: 18))
                        .overlay(RoundedRectangle(cornerRadius: 18)
                            .strokeBorder(Color(hex: 0xFF6B6B).opacity(0.4), lineWidth: 1))
                    }
                    .padding(.horizontal, 22)
                    .padding(.bottom, 10)
                }
            }
            .navigationTitle(locale.t(.tabProfile))
            .navigationBarTitleDisplayMode(.inline)
            // Android 프로필 탑바 = 뒤로가기 + "프로필" + 우측 "+"(핀 별 고르기)만.
            // (배경음악/설정/알림 진입점은 드로어와 지도 상단 하트로 이동 — Android MainScreen 대응.)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showPinPicker = true } label: { Image(systemName: "plus") }
                        .tint(Theme.textPrimary)
                }
            }
            .navigationDestination(isPresented: $showAchievements) {
                AchievementsScreen(equippedTitleId: $equippedTitleId)
            }
            .navigationDestination(isPresented: $showMyStars) {
                MyStarsScreen()
            }
            // (Diary 상세 push 는 MyStarsScreen 이 자체 등록 — 중복 등록 방지)
            .sheet(isPresented: $showPinPicker) {
                PinDiaryPicker(diaries: mine, initial: pinnedIds) { ids in
                    pinnedIds = ids
                    savePinned(ids)
                }
            }
            .task {
                hidden.start()
                guard let uid = auth.uid else { return }
                // 어드민(테스트) 계정이 과거에 실수로 선점한 히든 업적은 슬롯을 되돌린다(실제 유저가 첫 달성자가 되게).
                if AppConfig.isAdminEmail(Auth.auth().currentUser?.email) { await hidden.releaseOwnedBy(uid: uid) }
                let snap = try? await FirestoreService.friends(of: uid).getDocuments()
                friendsCount = snap?.documents.count ?? 0
                if let doc = try? await FirestoreService.users.document(uid).getDocument() {
                    profileImageUrl = doc.get("profileImageUrl") as? String
                    equippedTitleId = doc.get("equippedTitle") as? String
                    pinnedIds = (doc.get("pinnedDiaries") as? [String]) ?? []
                }
                runHiddenClaims()
            }
            .onChange(of: hidden.loaded) { _ in runHiddenClaims() }
            .onChange(of: friendsCount) { _ in runHiddenClaims() }
            .onChange(of: photoItem) { item in
                Task {
                    guard let uid = auth.uid, let item,
                          let data = try? await item.loadTransferable(type: Data.self) else { return }
                    if let url = try? await ImageUploader.uploadProfile(uid: uid, data: data) {
                        profileImageUrl = url
                    }
                }
            }
            .firstVisitInfo(key: "profile", systemImage: "person.fill",
                            title: LocaleManager.shared.t(.onbProfileTitle),
                            message: LocaleManager.shared.t(.onbProfileMsg))
            .alert(locale.t(.profileEditNickname), isPresented: $showNicknameEditor) {
                TextField(locale.t(.profileNicknameHint), text: $nicknameDraft)
                Button(locale.t(.commonSave)) {
                    // 20자 클램프(AppConfig.nicknameMaxLen) — Android NicknameEditDialog 패리티.
                    let n = String(nicknameDraft.prefix(AppConfig.nicknameMaxLen))
                    Task { await auth.setNickname(n) }
                }
                Button(locale.t(.commonCancel), role: .cancel) {}
            }
            // alert 안 TextField 는 onChange 를 못 다니 화면 레벨에서 초과분을 잘라 선차단.
            .onChange(of: nicknameDraft) { v in
                if v.count > AppConfig.nicknameMaxLen {
                    nicknameDraft = String(v.prefix(AppConfig.nicknameMaxLen))
                }
            }
            .alert(locale.t(.hiddenWonTitle),
                   isPresented: Binding(get: { hiddenAlert != nil },
                                        set: { if !$0 { hiddenAlert = nil } })) {
                Button(locale.t(.commonOk), role: .cancel) { hiddenAlert = nil }
            } message: {
                if let a = hiddenAlert {
                    Text("\(LocalizedNames.title(a.id, fallback: a.title) ?? a.title)\n\(a.condition)\n\(locale.t(.hiddenWonFirst))")
                }
            }
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

    /// 떠다니는 아이콘 탭 동작 — 친구/내 별/업적/지도(핀 별).
    private func handleBubbleTap(_ idx: Int) {
        switch idx {
        case 1: TabRouter.shared.go(TabRouter.friends)
        case 2: showMyStars = true
        case 3: showAchievements = true
        default:
            // 핀한 별 탭 → 지도로 가서 파동(물결) 후 그 별까지 도보 길찾기. 그 뒤 인덱스는 히든 아이콘 → 업적 화면.
            let p = pinnedDiaries
            let hiddenStart = 4 + p.count
            if idx >= 4, idx < hiddenStart, let id = p[idx - 4].id {
                MapFocusStore.shared.request(diaryId: id, withRoute: true)
            } else if idx >= hiddenStart {
                showAchievements = true
            }
        }
    }

    private func savePinned(_ ids: [String]) {
        guard let uid = auth.uid else { return }
        Task {
            try? await FirestoreService.users.document(uid)
                .setData(["pinnedDiaries": Array(ids.prefix(3))], merge: true)
        }
    }

    private var avatar: some View {
        PhotosPicker(selection: $photoItem, matching: .images) {
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [Theme.navyAccent.opacity(0.28), .clear],
                                         center: .center, startRadius: 0, endRadius: 130))
                    .frame(width: 200, height: 200)
                avatarImage
                    .frame(width: 150, height: 150)
                    .clipShape(Circle())
                    .overlay(
                        Circle().strokeBorder(
                            LinearGradient(colors: [Color(hex: 0x3B82F6), Theme.navyDeep],
                                           startPoint: .topLeading, endPoint: .bottomTrailing),
                            lineWidth: 3)
                    )
            }
        }
        .buttonStyle(.plain)
    }

    private var avatarImage: some View {
        Group {
            if let url = profileImageUrl, !url.isEmpty {
                AsyncImage(url: URL(string: url)) { image in
                    image.resizable().scaledToFill()
                } placeholder: { Color(hex: 0x0D0D0D) }
            } else {
                Color(hex: 0x0D0D0D).overlay(
                    Image(systemName: "person.crop.circle.fill")
                        .resizable().scaledToFit().padding(34)
                        .foregroundStyle(Color(hex: 0x555555))
                )
            }
        }
    }
}

// (MyStarsScreen 은 별자리 보드판으로 재작성되어 MyDiaryBoardScreen.swift 로 이동 — Android MyDiaryScreen 이식)

/// 프로필에 띄울 별(다이어리) 고르기 — 최대 3개 토글. (Android PinDiaryPicker 패리티)
private struct PinDiaryPicker: View {
    let diaries: [Diary]
    let initial: [String]
    let onConfirm: ([String]) -> Void

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var locale = LocaleManager.shared
    @State private var selected: Set<String>

    init(diaries: [Diary], initial: [String], onConfirm: @escaping ([String]) -> Void) {
        self.diaries = diaries
        self.initial = initial
        self.onConfirm = onConfirm
        _selected = State(initialValue: Set(initial))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(locale.t(.profilePinHint))
                            .font(.caption).foregroundStyle(Theme.textSecondary)
                            .padding(.bottom, 4)
                        if diaries.isEmpty {
                            Text(locale.t(.profileEmptyStars))
                                .font(.subheadline).foregroundStyle(Theme.textSecondary)
                                .padding(.vertical, 20)
                        } else {
                            ForEach(diaries) { d in row(d) }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle(locale.t(.profilePinTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(locale.t(.commonCancel)) { dismiss() }.tint(Theme.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(locale.t(.commonSave)) { onConfirm(Array(selected)); dismiss() }.tint(Theme.navyAccent)
                }
            }
        }
    }

    private func row(_ d: Diary) -> some View {
        let isSel = selected.contains(d.id ?? "")
        return Button {
            guard let id = d.id else { return }
            if isSel { selected.remove(id) }
            else if selected.count < 3 { selected.insert(id) }
        } label: {
            HStack(spacing: 12) {
                StarView(type: d.starType, colorIndex: d.starColor, size: 24)
                Text(d.title.isEmpty ? locale.t(.profileMyStars) : d.title)
                    .font(.subheadline).foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                Spacer()
                if isSel { Image(systemName: "checkmark").foregroundStyle(Theme.navyAccent) }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(isSel ? Theme.navyAccent.opacity(0.14) : Color.white.opacity(0.04),
                        in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
