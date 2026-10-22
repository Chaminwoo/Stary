import CoreLocation
import SwiftUI

/// 첫 별 안내(2026-10-22) — 광고로 들어온 새 사용자가 "첫 별 남기기 → 퀘스트에서 열쇠 받기 → 그 열쇠로 남의 별 열기"를
/// 차례로 해 보도록 지도 위에 한 장의 카드로 다음 할 일을 보여 준다. Android `FirstStarGuide.kt` 와 같은 단계·조건.
///
/// 단계는 저장하지 않고 **지금 상태에서 계산**한다(어느 기기에서 무엇을 했든 맞게 따라온다):
///  - `.write` : 내 별이 아직 없다 → 업로드 화면으로.
///  - `.claim` : 내 별은 있고 열쇠가 0개 → 퀘스트 창(받을 보상이 있을 때) / 없으면 안내용 열쇠 1개를 바로 준다.
///  - `.open`  : 열쇠가 있다 → 가장 가까운 "잠긴" 남의 별로 카메라를 옮긴 뒤 상세(잠금 카드에서 열쇠 사용).
///  - 끝: 열쇠를 처음 쓰면(`QuestStore.spendKey`) 또는 열어 볼 별이 없으면 `users/{uid}.guideDone` — 다시 뜨지 않는다.
/// ⚠️ 이미 별을 올려 본 기존 사용자에게 claim/open 이 갑자기 뜨지 않도록, **이 기기에서 write 단계를 본 계정만** 다음 단계로 간다
///    (`startedKey`, 계정별 UserDefaults). 닫기(X)는 이번 실행에서만 숨긴다.
enum GuideStep: String {
    case write, claim, open
}

enum FirstStarGuideLogic {
    static func startedKey(_ uid: String) -> String { "first_guide_started_\(uid)" }

    /// 지금 안내할 단계(nil = 안내 없음).
    @MainActor
    static func step(uid: String?, diaries: [Diary], loading: Bool) -> GuideStep? {
        let quests = QuestStore.shared
        guard let uid, quests.guideLoaded, !quests.guideDone else { return nil }
        // 목록을 아직 못 받았으면 "내 별 없음"으로 오판하지 않게 기다린다.
        guard !loading, !diaries.isEmpty else { return nil }
        if !diaries.contains(where: { $0.userId == uid }) { return .write }
        guard UserDefaults.standard.bool(forKey: startedKey(uid)) else { return nil }
        return quests.keys > 0 ? .open : .claim
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

/// 지도 위 안내 카드. 단계 계산·행동은 호출부(MapScreen)가 넘겨준다.
struct FirstStarGuideCard: View {
    let step: GuideStep
    let onAction: () -> Void
    let onClose: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    private var texts: (symbol: String, title: L10n, msg: L10n, btn: L10n) {
        switch step {
        case .write: return ("plus", .guideWriteTitle, .guideWriteMsg, .guideWriteBtn)
        case .claim: return ("key.fill", .guideClaimTitle, .guideClaimMsg, .guideClaimBtn)
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
                Text(locale.t(t.msg))
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
