import FirebaseFirestore
import Foundation

/// 열람 기록 쓰기 — users/{uid}/viewedDiaries/{diaryId}. (Android FirebaseViewedRepository.markViewed 패리티)
/// 미조회 필터용. 상세 진입 시 fire-and-forget 으로 기록한다.
enum ViewedRepository {
    static func markViewed(uid: String, diaryId: String) async {
        try? await FirestoreService.viewedDiaries(of: uid)
            .document(diaryId)
            .setData(["viewedAt": FirestoreService.nowMillis])
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
