import SwiftUI
import UIKit

/// 히든 업적 달성자의 **이름 옆 전용 크리스탈 별** — Android `core/ui/HiddenStarBadge.kt` 패리티.
///
/// 앱 전체에서 이름이 뜨는 곳(다이어리 작성자·댓글·친구 목록·채팅·프로필 …)에 붙어
/// "이 사람은 이 히든 업적의 유일한 달성자" 임을 보여준다. 업적마다 (모양×색) 조합이 다르다
/// (`HiddenAchievement.badgeType/badgeColor` — Android 정의와 값 동일).
///
/// - 달성한 히든이 없으면 **아무것도 그리지 않는다**(레이아웃 영향 없음).
/// - 파티클 없는 정적 렌더 — 리스트 스크롤에서 가볍게([StarCrystal.image] NSCache 재사용).
/// - ⚠️ 익명 다이어리/댓글에는 붙이지 말 것(작성자 은닉이 깨진다).
struct HiddenStarBadges: View {
    let userId: String
    var size: CGFloat = 12
    var maxCount: Int = 3

    @ObservedObject private var store = HiddenAchievementStore.shared

    var body: some View {
        let achievements = store.achievements(of: userId)
        if !userId.isEmpty, !achievements.isEmpty {
            HStack(spacing: 2) {
                ForEach(achievements.prefix(maxCount), id: \.id) { ach in
                    HiddenStarBadge(type: ach.badgeType, colorIndex: ach.badgeColor, size: size)
                }
            }
        }
    }
}

/// 배지 별 1개(캐시된 비트맵). 업적 화면 등에서 단독으로도 쓸 수 있다.
struct HiddenStarBadge: View {
    let type: Int
    let colorIndex: Int
    var size: CGFloat = 12

    var body: some View {
        Image(uiImage: StarCrystal.image(type: type, colorIndex: colorIndex, size: size))
            .resizable()
            .frame(width: size, height: size)
    }
}
