import CoreLocation
import SwiftUI
import UIKit

/// 첫 사용 안내(2026-10-22) — 새 계정이 앱을 한 바퀴 돌아보게 다음 할 일을 하나씩 보여 준다. Android `FirstStarGuide.kt` 와 같은 단계·조건.
///
/// 코치마크 → 웰컴 별 열기(`TutorialStarState`) 뒤에:
///  - `.quest`   : 지도 "!"(오늘의 퀘스트) 버튼만 밝히는 스포트라이트(`QuestSpotlight`, RootView 가 띄움) — 탭하면 퀘스트 창.
///  - `.profile` : 첫 퀘스트 "이름이나 프로필 사진 바꾸기" — 퀘스트 창에서 그 행을 강조, 지도에선 카드 → 프로필 화면.
///  - `.write`   : 내 별이 아직 없다 → 업로드 화면.
///  - `.claim`   : 받을 보상이 있다(또는 열쇠 0개) → 퀘스트 창 / 받을 게 없으면 안내용 열쇠 1개를 바로 준다.
///  - `.open`    : 열쇠가 있다 → 가장 가까운 "잠긴" 남의 별로 카메라를 옮긴 뒤 상세(잠금 카드에서 열쇠 사용).
///  - 끝: 열쇠를 처음 쓰면(`QuestStore.spendKey`) 또는 열어 볼 별이 없으면 `users/{uid}.guideDone` — 다시 뜨지 않는다.
/// 앞 세 단계의 진행은 이 기기의 계정별 UserDefaults(`FirstStarGuideProgress`), 별·열쇠는 서버 상태에서 계산한다.
/// ⚠️ **새 계정으로 확인된 계정만**(`started` — 로그인 때 returningAccount == false) 안내한다. 닫기(X)·"나중에"는 이번 실행에서만 숨긴다.
enum GuideStep: String {
    case quest, profile, write, claim, exchange, open
}

/// 계정별 안내 진행(이 기기) — Android `GuideProgress` 패리티.
@MainActor
final class FirstStarGuideProgress: ObservableObject {
    static let shared = FirstStarGuideProgress()
    private init() {}

    /// 지금 읽어 둔 계정 — 뷰 갱신 중 @Published 를 바꾸지 않게 `bind` 는 MapScreen onAppear/onChange(uid) 에서 부른다.
    private(set) var boundUid: String?
    /// 새 계정으로 확인돼 안내 대상인지.
    @Published private(set) var started = false
    /// 퀘스트 창을 안내 중에 한 번 열었는지(→ 스포트라이트 끝).
    @Published private(set) var questSeen = false
    /// 닉네임이나 프로필 사진을 바꿨는지(첫 퀘스트).
    @Published private(set) var profileDone = false

    private static func key(_ name: String, _ uid: String) -> String { "first_guide_\(name)_\(uid)" }

    /// 이 계정 값으로 맞춘다(계정이 바뀌면 다시 읽음).
    func bind(_ uid: String?) {
        guard uid != boundUid else { return }
        boundUid = uid
        let d = UserDefaults.standard
        started = uid.map { d.bool(forKey: Self.key("started", $0)) } ?? false
        questSeen = uid.map { d.bool(forKey: Self.key("quest", $0)) } ?? false
        profileDone = uid.map { d.bool(forKey: Self.key("profile", $0)) } ?? false
    }

    private func mark(_ name: String, _ uid: String) {
        bind(uid)
        UserDefaults.standard.set(true, forKey: Self.key(name, uid))
        switch name {
        case "started": started = true
        case "quest": questSeen = true
        default: profileDone = true
        }
    }

    func markStarted(_ uid: String) { if boundUid != uid || !started { mark("started", uid) } }
    func markQuestSeen(_ uid: String) { if boundUid != uid || !questSeen { mark("quest", uid) } }
    func markProfileDone(_ uid: String) { if boundUid != uid || !profileDone { mark("profile", uid) } }
}

enum FirstStarGuideLogic {
    /// 지금 안내할 단계(nil = 안내 없음).
    @MainActor
    static func step(uid: String?, diaries: [Diary], loading: Bool, here: CLLocationCoordinate2D?) -> GuideStep? {
        let quests = QuestStore.shared
        guard let uid, quests.guideLoaded, !quests.guideDone else { return nil }
        let progress = FirstStarGuideProgress.shared
        guard progress.boundUid == uid, progress.started else { return nil }
        // 목록을 아직 못 받았으면 "내 별 없음"으로 오판하지 않게 기다린다.
        guard !loading, !diaries.isEmpty else { return nil }
        if !diaries.contains(where: { $0.userId == uid }) {
            // 웰컴 별을 먼저 열어 본 뒤부터(위치를 못 잡아 웰컴 별이 안 놓인 경우는 기다리지 않는다).
            let tutorial = TutorialStarState.shared
            if !tutorial.done && (tutorial.placed != nil || here != nil) { return nil }
            if !progress.questSeen { return .quest }
            if !progress.profileDone { return .profile }
            return .write
        }
        if (quests.daily?.claimable ?? 0) > 0 { return .claim }
        if quests.keys > 0 { return .open }
        // 열쇠가 없다 — 별가루로 만들 수 있으면 공방으로(지갑을 아직 모르면 기다린다: 안내 열쇠를 괜히 주지 않게).
        // (2026-10-22 퀘스트 보상이 별가루가 되며 추가 — Android guideStep 패리티)
        let starlight = StarlightStore.shared
        guard starlight.walletLoaded else { return nil }
        return starlight.stardust >= starlight.config.keyCost ? .exchange : .claim
    }

    /// 열쇠로 열어 볼 별 — 남의 공개 별 중 아직 안 연 것, **100m 밖(= 잠긴) 가장 가까운 별** 우선.
    /// 잠긴 별이 없으면 가장 가까운 남의 별(열쇠 없이 열린다 — 그래도 "남의 별 보기"는 된다).
    @MainActor
    static func target(diaries: [Diary], uid: String, here: CLLocationCoordinate2D?, blocked: Set<String>) -> Diary? {
        let unlocks = DiaryUnlockStore.shared
        let candidates = diaries.filter { d in
            guard let id = d.id else { return false }
            return d.userId != uid && !blocked.contains(d.userId) && !unlocks.isUnlocked(id) &&
                d.visibilityType == "public" && id != TutorialStarState.diaryId &&
                (d.latitude != 0 || d.longitude != 0) &&
                !ContentFilter.anyObjectionable(d.title, d.content)
        }
        guard let here else { return candidates.max { $0.likeCount < $1.likeCount } }
        func dist(_ d: Diary) -> Double {
            Geo.distanceMeters(lat1: here.latitude, lng1: here.longitude, lat2: d.latitude, lng2: d.longitude)
        }
        return candidates.filter { dist($0) > AppConfig.diaryOpenRadiusM }.min { dist($0) < dist($1) }
            ?? candidates.min { dist($0) < dist($1) }
    }
}

/// 지도 위 안내 카드(.profile·.write·.claim·.open). 단계 계산·행동은 호출부(MapScreen)가 넘겨준다.
struct FirstStarGuideCard: View {
    let step: GuideStep
    let onAction: () -> Void
    let onClose: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    private var texts: (symbol: String, title: L10n, msg: L10n, btn: L10n) {
        switch step {
        case .quest, .profile: return ("pencil", .guideProfileTitle, .guideProfileMsg, .questGo)
        case .write: return ("plus", .guideWriteTitle, .guideWriteMsg, .guideWriteBtn)
        case .claim: return ("key.fill", .guideClaimTitle, .guideClaimMsg, .guideClaimBtn)
        case .exchange: return ("sparkles", .guideExchangeTitle, .guideExchangeMsg, .starlightGo)
        case .open: return ("lock.open.fill", .guideOpenTitle, .guideOpenMsg, .guideOpenBtn)
        }
    }

    var body: some View {
        let t = texts
        HStack(spacing: 12) {
            Image(systemName: t.symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(QuestTheme.gold)
                .frame(width: 38, height: 38)
                .background(QuestTheme.gold.opacity(0.16), in: Circle())
                .overlay(Circle().strokeBorder(QuestTheme.gold.opacity(0.5), lineWidth: 0.75))
            VStack(alignment: .leading, spacing: 2) {
                Text(locale.t(t.title))
                    .font(.minSans(14, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                Text(step == .exchange ? String(format: locale.t(t.msg), StarlightStore.shared.config.keyCost) : locale.t(t.msg))
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0xB8BCCB))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onAction) {
                Text(locale.t(t.btn))
                    .font(.minSans(12, .semibold))
                    .foregroundStyle(Color(hex: 0x111120))
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(QuestTheme.gold, in: Capsule())
            }
            .buttonStyle(.plain)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(locale.t(.questClose))
        }
        .padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 12)
        .background(Color(hex: 0x111120).opacity(0.94), in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(QuestTheme.gold.opacity(0.45), lineWidth: 0.75))
    }
}

/// `.quest` 단계 — 화면 전체를 어둡게 하고 지도 "!" 버튼만 금빛 링으로 밝힌다(코치마크와 같은 스포트라이트).
/// 구멍(버튼)을 탭하면 `onOpen`(퀘스트 창), 우상단 "나중에"는 이번 실행에서만 숨긴다. 구멍 밖 탭은 무시.
/// `center` = 버튼 중심(global) — 코치마크와 같은 `CoachMarkAnchor.eye` 값. RootView 에서 `.ignoresSafeArea()` 로 띄운다.
struct QuestSpotlight: View {
    let center: CGPoint
    let onOpen: () -> Void
    let onLater: () -> Void
    @State private var visible = false
    @State private var pulse = false

    private let r: CGFloat = 32

    private var topInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?.safeAreaInsets.top ?? 44
    }

    var body: some View {
        GeometryReader { geo in
            // 버튼 좌표는 global, 이 오버레이 원점이 어긋나도 맞도록 자기 global 원점을 뺀다(MainOnboardingOverlay 와 같은 보정).
            let o = geo.frame(in: .global).origin
            let c = CGPoint(x: center.x - o.x, y: center.y - o.y)
            ZStack {
                ZStack {
                    Color.black.opacity(0.74)
                    RadialGradient(
                        stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.62), .init(color: .clear, location: 1)],
                        center: .center, startRadius: 0, endRadius: r * 1.35
                    )
                    .frame(width: r * 2.7, height: r * 2.7)
                    .position(c)
                    .blendMode(.destinationOut)
                }
                .compositingGroup()
                .contentShape(Rectangle())
                .onTapGesture {} // 구멍 밖 탭은 삼킨다

                Circle()
                    .stroke(QuestTheme.gold.opacity(0.85), lineWidth: 2)
                    .frame(width: r * 2, height: r * 2)
                    .position(c)
                    .allowsHitTesting(false)
                Circle()
                    .stroke(QuestTheme.gold.opacity(pulse ? 0 : 0.5), lineWidth: 1.5)
                    .frame(width: r * 2, height: r * 2)
                    .scaleEffect(pulse ? 1.18 : 1)
                    .position(c)
                    .allowsHitTesting(false)

                // 버튼 자리 탭 → 퀘스트 창
                Circle()
                    .fill(Color.white.opacity(0.001))
                    .frame(width: r * 2.8, height: r * 2.8)
                    .position(c)
                    .onTapGesture(perform: onOpen)

                // 말풍선 — 버튼 왼쪽에 세로 중심을 맞춘다(코치마크 우측 버튼 단계와 같은 자리).
                let room = max(0, c.x - r - 12 - 10)
                Text(LocaleManager.shared.t(.guideQuestTip))
                    .font(.minSans(14))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Color(hex: 0x14181C), in: RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(LinearGradient(colors: [QuestTheme.gold, QuestTheme.goldDeep], startPoint: .leading, endPoint: .trailing), lineWidth: 1)
                    )
                    .frame(maxWidth: min(232, room))
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(width: room, alignment: .trailing)
                    .position(x: 10 + room / 2, y: c.y)
                    .allowsHitTesting(false)

                Text(LocaleManager.shared.t(.guideLater))
                    .font(.minSans(13))
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(6)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onLater)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(.top, topInset + 8)
                    .padding(.trailing, 14)
            }
        }
        .opacity(visible ? 1 : 0)
        .onAppear {
            withAnimation(.easeOut(duration: 0.32)) { visible = true }
            withAnimation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true)) { pulse = true }
            StaryAnalytics.logOnce("guide_quest", "guide_step", ["step": GuideStep.quest.rawValue])
        }
    }
}
