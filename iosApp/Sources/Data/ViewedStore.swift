import FirebaseFirestore
import Foundation

/// 열람 기록 — users/{uid}/viewedDiaries/{diaryId}. (Android FirebaseViewedRepository 패리티)
///  - `viewedAt`  : 마지막으로 상세에 들어온 시각(잠겨 있어도 기록) — 미조회 필터·열람 업적용.
///  - `unlockedAt`: 본문을 **실제로 연(해금한)** 시각 — 100m 접근 또는 광고. 별 도감·영구 해금의 서버 사본
///    (`DiaryUnlockStore` 가 쓰고 읽는다). 두 필드를 따로 쓰므로 쓰기는 항상 merge.
enum ViewedRepository {
    static func markViewed(uid: String, diaryId: String) async {
        // merge — 덮어쓰면 unlockedAt(해금 기록)이 지워진다.
        try? await FirestoreService.viewedDiaries(of: uid)
            .document(diaryId)
            .setData(["viewedAt": FirestoreService.nowMillis], merge: true)
    }

    /// 해금 기록을 서버에 남긴다(여러 개면 배치). 실패는 무시 — 다음 동기화 때 다시 올라간다.
    static func markUnlocked(uid: String, unlocks: [String: Int64]) async {
        guard !unlocks.isEmpty else { return }
        let col = FirestoreService.viewedDiaries(of: uid)
        let entries = Array(unlocks)
        for start in stride(from: 0, to: entries.count, by: 400) {
            let batch = FirestoreService.db.batch()
            for (id, at) in entries[start..<min(start + 400, entries.count)] {
                batch.setData(["unlockedAt": at], forDocument: col.document(id), merge: true)
            }
            try? await batch.commit()
        }
    }

    /// 서버에 남은 "실제로 연" 기록. 실패하면 nil. (Android fetchOpened 패리티)
    ///  - `unlocked`: `unlockedAt` 이 있는 문서.
    ///  - `legacy`  : `unlockedAt` 은 없지만 `viewedAt` 이 `AppConfig.diaryLockSinceMs` 이전(잠금이 없던 시절 = 본문까지 열람).
    static func fetchOpened(uid: String) async -> (unlocked: [String: Int64], legacy: [String: Int64])? {
        guard let snap = try? await FirestoreService.viewedDiaries(of: uid).getDocuments() else { return nil }
        var unlocked: [String: Int64] = [:]
        var legacy: [String: Int64] = [:]
        for d in snap.documents {
            let data = d.data()
            if let at = (data["unlockedAt"] as? NSNumber)?.int64Value {
                unlocked[d.documentID] = at
            } else if let viewed = (data["viewedAt"] as? NSNumber)?.int64Value, viewed < AppConfig.diaryLockSinceMs {
                legacy[d.documentID] = viewed
            }
        }
        return (unlocked, legacy)
    }
}

/// 내가 연 다이어리 id 집합을 실시간 관찰 — "미조회만" 필터에 사용.
/// (Android MainListScreen 의 observeViewedIds 구독 패리티.)
@MainActor
final class ViewedStore: ObservableObject {
    @Published private(set) var viewedIds: Set<String> = []

    private var reg: ListenerRegistration?
    private var uid: String?

    func start(uid: String) {
        guard self.uid != uid else { return }
        stop()
        self.uid = uid
        reg = FirestoreService.viewedDiaries(of: uid).addSnapshotListener { [weak self] snap, _ in
            self?.viewedIds = Set(snap?.documents.map { $0.documentID } ?? [])
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
        uid = nil
        viewedIds = []
    }

    deinit { reg?.remove() }
}
