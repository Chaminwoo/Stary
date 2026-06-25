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

    func save(_ diary: Diary) async throws { try await repo.save(diary) }
    func delete(_ id: String) async throws { try await repo.delete(id) }
    func incrementView(_ id: String) async { await repo.incrementViewCount(id) }
}
