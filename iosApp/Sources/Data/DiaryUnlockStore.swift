import Foundation
import SwiftUI

/// 해금한(열어 본) 다른 사람의 게시물(다이어리) 기록 — **영구**.
///
/// 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
/// ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, **한 번 풀린 게시물은 이 기기에서 계속 열려 있다**
/// (2026-09-22 "다이어리도 아예 해금 형식" 피드백 — 예전 7일 TTL 폐지, 접근 해금도 기록).
/// ⚠️ 해금은 **열람**만 연다. 댓글 작성은 해금 여부와 무관하게 항상 100m 이내에서만 가능하다.
///
/// Android `core.util.DiaryUnlockStore` 패리티 — diaryId → 해금 시각(ms, 기록용 — 존재 = 해금).
/// `ObservableObject` 라 [unlock] 시 관찰 중인 화면이 즉시 갱신된다.
/// **기기 로컬 저장**(서버 스키마 변경 없음) — 기기를 바꾸면 다시 열어야 한다(허용 범위).
@MainActor
final class DiaryUnlockStore: ObservableObject {
    static let shared = DiaryUnlockStore()

    /// 키 이름은 7일 TTL 시절(`AdUnlockStore`) 그대로 — 그때 광고로 연 기록도 영구 해금으로 승계된다.
    private static let prefsKey = "ad_unlock_store"

    /// diaryId → 해금 시각(ms).
    @Published private(set) var unlockedAt: [String: Int64] = [:]

    private init() {
        let raw = UserDefaults.standard.dictionary(forKey: Self.prefsKey) as? [String: NSNumber] ?? [:]
        unlockedAt = raw.mapValues { $0.int64Value }
    }

    /// 이 게시물이 해금돼 있는지.
    func isUnlocked(_ diaryId: String) -> Bool {
        unlockedAt[diaryId] != nil
    }

    /// 광고 시청 완료 또는 100m 이내 접근 → 이 게시물을 영구 해금한다(이미 해금이면 무시).
    func unlock(_ diaryId: String) {
        guard unlockedAt[diaryId] == nil else { return }
        unlockedAt[diaryId] = FirestoreService.nowMillis
        UserDefaults.standard.set(
            unlockedAt.mapValues { NSNumber(value: $0) },
            forKey: Self.prefsKey
        )
    }
}
