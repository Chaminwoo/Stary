import Foundation
import SwiftUI

/// 전체/내 다이어리 목록을 구독해 화면들이 공유하는 관찰 가능 저장소.
@MainActor
final class DiaryStore: ObservableObject {
    @Published var diaries: [Diary] = []
    @Published var loading = true

    private let repo = DiaryRepository()
    private var started = false
    private var startedUid: String?

    /// uid 가 바뀔 때만 재구독.
    func startIfNeeded(uid: String?) {
        if started && startedUid == uid { return }
        started = true
        startedUid = uid
        repo.stopAll()
        loading = true
        repo.observeAll(currentUid: uid) { [weak self] list in
            guard let self else { return }
            self.diaries = list
            self.loading = false
        }
    }

    func mine(uid: String?) -> [Diary] {
        guard let uid else { return [] }
        return diaries.filter { $0.userId == uid }
    }

    @discardableResult
    func save(_ diary: Diary) async throws -> String { try await repo.save(diary) }
    func delete(_ id: String) async throws { try await repo.delete(id) }
    func incrementView(_ id: String) async { await repo.incrementViewCount(id) }

    /// 새 글 작성 시 친구들에게 FRIEND_POST 인앱 알림 생성(Android notifyFriendPost 대응).
    /// (private 글은 호출하지 않음)
    func notifyFriends(uid: String, name: String, diaryId: String, title: String) async {
        do {
            let friends = try await FirestoreService.friends(of: uid).getDocuments()
            guard !friends.isEmpty else { return }
            let now = FirestoreService.nowMillis
            let batch = FirestoreService.db.batch()
            for doc in friends.documents {
                batch.setData([
                    "type": "FRIEND_POST", "diaryId": diaryId, "diaryTitle": title,
                    "diaryOwnerId": doc.documentID, "actorId": uid, "actorName": name,
                    "content": "", "createdAt": now, "read": false,
                ], forDocument: FirestoreService.notifications.document())
            }
            try await batch.commit()
        } catch {}
    }
}
