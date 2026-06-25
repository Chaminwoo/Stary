import FirebaseFirestore
import Foundation

/// 상세 화면의 좋아요/댓글 상태. Android Like/Comment 리포지토리와 동일 스키마.
@MainActor
final class DetailViewModel: ObservableObject {
    @Published var comments: [Comment] = []
    @Published var isLiked = false
    @Published var likeCount: Int

    let diary: Diary
    private var regs: [ListenerRegistration] = []

    init(diary: Diary) {
        self.diary = diary
        self.likeCount = diary.likeCount
    }

    deinit { regs.forEach { $0.remove() } }

    func start(uid: String?) {
        guard let id = diary.id else { return }
        stop()
        regs.append(
            FirestoreService.comments(of: id)
                .order(by: "createdAt", descending: true)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.comments = snap?.documents.compactMap { try? $0.data(as: Comment.self) } ?? []
                }
        )
        regs.append(
            FirestoreService.diaries.document(id).addSnapshotListener { [weak self] snap, _ in
                if let n = snap?.get("likeCount") as? NSNumber { self?.likeCount = n.intValue }
            }
        )
        if let uid {
            regs.append(
                FirestoreService.likes(of: id).document(uid).addSnapshotListener { [weak self] snap, _ in
                    self?.isLiked = snap?.exists ?? false
                }
            )
        }
    }

    func toggleLike(uid: String?, userName: String) async {
        guard let uid, let diaryId = diary.id else { return }
        let diaryRef = FirestoreService.diaries.document(diaryId)
        let likeRef = FirestoreService.likes(of: diaryId).document(uid)
        do {
            let snap = try await likeRef.getDocument()
            let batch = FirestoreService.db.batch()
            if snap.exists {
                batch.deleteDocument(likeRef)
                batch.updateData(["likeCount": FieldValue.increment(Int64(-1))], forDocument: diaryRef)
            } else {
                batch.setData(["userId": uid, "userName": userName, "createdAt": FirestoreService.nowMillis],
                              forDocument: likeRef)
                batch.updateData(["likeCount": FieldValue.increment(Int64(1))], forDocument: diaryRef)
                if diary.userId != uid {
                    batch.setData(notif(type: "LIKE", actorId: uid, actorName: userName, content: ""),
                                  forDocument: FirestoreService.notifications.document())
                }
            }
            try await batch.commit()
        } catch {}
    }

    func addComment(uid: String?, userName: String, text: String) async {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let uid, let diaryId = diary.id, !body.isEmpty else { return }
        let diaryRef = FirestoreService.diaries.document(diaryId)
        let commentRef = FirestoreService.comments(of: diaryId).document()
        let batch = FirestoreService.db.batch()
        batch.setData([
            "diaryId": diaryId, "userId": uid, "userName": userName,
            "content": body, "createdAt": FirestoreService.nowMillis,
        ], forDocument: commentRef)
        batch.updateData(["commentCount": FieldValue.increment(Int64(1))], forDocument: diaryRef)
        if diary.userId != uid {
            batch.setData(notif(type: "COMMENT", actorId: uid, actorName: userName, content: body),
                          forDocument: FirestoreService.notifications.document())
        }
        try? await batch.commit()
    }

    func deleteComment(_ c: Comment) async {
        guard let diaryId = diary.id, let cid = c.id else { return }
        let diaryRef = FirestoreService.diaries.document(diaryId)
        let batch = FirestoreService.db.batch()
        batch.deleteDocument(FirestoreService.comments(of: diaryId).document(cid))
        batch.updateData(["commentCount": FieldValue.increment(Int64(-1))], forDocument: diaryRef)
        try? await batch.commit()
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
    }

    private func notif(type: String, actorId: String, actorName: String, content: String) -> [String: Any] {
        [
            "type": type,
            "diaryId": diary.id ?? "",
            "diaryTitle": diary.title,
            "diaryOwnerId": diary.userId,
            "actorId": actorId,
            "actorName": actorName,
            "content": content,
            "createdAt": FirestoreService.nowMillis,
            "read": false,
        ]
    }
}
