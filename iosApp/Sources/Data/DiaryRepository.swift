import FirebaseFirestore
import Foundation

/// 다이어리 Firestore 접근. (KMP `DiaryRepository` 인터페이스의 iOS 구현격)
///
/// Android 와 동일한 제약을 따른다:
///  - 복합 인덱스 의존을 피하려 정렬은 클라이언트에서(`sorted`).
///  - private 다이어리는 클라이언트에서 필터.
@MainActor
final class DiaryRepository {
    private let col = FirestoreService.diaries
    private var listeners: [ListenerRegistration] = []

    deinit { listeners.forEach { $0.remove() } }

    /// 전체 공개 + (내) 다이어리 구독. private 는 본인 것만 통과.
    func observeAll(currentUid: String?, onChange: @escaping ([Diary]) -> Void) {
        let reg = col
            .order(by: "createdAt", descending: true)
            .limit(to: 500)
            .addSnapshotListener { snapshot, _ in
                let all = snapshot?.documents.compactMap { try? $0.data(as: Diary.self) } ?? []
                let visible = all.filter { d in
                    d.visibilityType != "private" || d.userId == currentUid
                }
                onChange(visible)
            }
        listeners.append(reg)
    }

    /// 내 다이어리 구독(서버는 userId 필터만, 정렬은 클라이언트).
    func observeMine(userId: String, onChange: @escaping ([Diary]) -> Void) {
        let reg = col
            .whereField("userId", isEqualTo: userId)
            .addSnapshotListener { snapshot, _ in
                let mine = (snapshot?.documents.compactMap { try? $0.data(as: Diary.self) } ?? [])
                    .sorted { $0.createdAt > $1.createdAt }
                onChange(mine)
            }
        listeners.append(reg)
    }

    func save(_ diary: Diary) async throws {
        var d = diary
        if d.createdAt == 0 { d.createdAt = FirestoreService.nowMillis }
        if let id = d.id, !id.isEmpty {
            try col.document(id).setData(from: d, merge: true)
        } else {
            d.id = nil
            _ = try col.addDocument(from: d)
        }
    }

    func delete(_ diaryId: String) async throws {
        try await col.document(diaryId).delete()
    }

    func diary(by id: String) async throws -> Diary? {
        try await col.document(id).getDocument().data(as: Diary.self)
    }

    /// 조회수 +1 (본인 제외는 호출부에서 판단).
    func incrementViewCount(_ diaryId: String) async {
        try? await col.document(diaryId).updateData(["viewCount": FieldValue.increment(Int64(1))])
    }

    func stopAll() {
        listeners.forEach { $0.remove() }
        listeners.removeAll()
    }
}
