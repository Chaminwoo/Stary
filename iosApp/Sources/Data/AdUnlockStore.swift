import Foundation
import SwiftUI

/// 광고 시청으로 열어 둔 게시물(다이어리) 기록.
///
/// 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
/// ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, ②로 푼 기록이 여기 남는다.
///
/// Android `core.util.AdUnlockStore` 패리티 — diaryId → 해제 시각(ms), [unlockTtlMs] 동안 유효.
/// `ObservableObject` 라 [unlock] 시 관찰 중인 화면이 즉시 갱신된다.
/// **기기 로컬 저장**(서버 스키마 변경 없음) — 기기를 바꾸면 다시 봐야 한다(허용 범위).
@MainActor
final class AdUnlockStore: ObservableObject {
    static let shared = AdUnlockStore()

    private static let prefsKey = "ad_unlock_store"

    /// 광고 1회 시청으로 열리는 기간 — 7일(Android `UNLOCK_TTL_MS` 와 같은 값).
    static let unlockTtlMs: Int64 = 7 * 24 * 60 * 60 * 1000

    /// diaryId → 해제 시각(ms).
    @Published private(set) var unlockedAt: [String: Int64] = [:]

    private init() {
        let raw = UserDefaults.standard.dictionary(forKey: Self.prefsKey) as? [String: NSNumber] ?? [:]
        unlockedAt = raw.mapValues { $0.int64Value }
    }

    /// 이 게시물이 광고로 열려 있는지(만료 포함 판정).
    func isUnlocked(_ diaryId: String) -> Bool {
        guard let at = unlockedAt[diaryId] else { return false }
        return FirestoreService.nowMillis - at < Self.unlockTtlMs
    }

    /// 광고 시청 완료 → 이 게시물을 연다.
    func unlock(_ diaryId: String) {
        unlockedAt[diaryId] = FirestoreService.nowMillis
        UserDefaults.standard.set(
            unlockedAt.mapValues { NSNumber(value: $0) },
            forKey: Self.prefsKey
        )
    }
}
