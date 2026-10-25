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

    private var likeBusy = false

    /// 좋아요 토글 — 트랜잭션으로 "했는지 읽기 → 쓰기"를 묶는다(Android FirebaseLikeRepository 와 같다).
    /// 예전엔 읽기와 배치 쓰기가 따로라 빠르게 두 번 누르면 +2 와 알림 두 번이 될 수 있었다.
    /// 실패(오프라인 등)면 false — 화면이 연결 안내를 띄운다. 하트·숫자는 구독값이라 그대로 남는다.
    func toggleLike(uid: String?, userName: String) async -> Bool {
        guard let uid, let diaryId = diary.id else { return false }
        guard !likeBusy else { return true } // 연타는 앞의 처리로 충분
        likeBusy = true
        defer { likeBusy = false }
        let diaryRef = FirestoreService.diaries.document(diaryId)
        let likeRef = FirestoreService.likes(of: diaryId).document(uid)
        let notifRef: DocumentReference? = diary.userId != uid ? FirestoreService.notifications.document() : nil
        let notifData = notif(type: "LIKE", actorId: uid, actorName: userName, content: "")
        let now = FirestoreService.nowMillis
        do {
            let liked = try await FirestoreService.db.runTransaction { tx, errorPointer -> Any? in
                let snap: DocumentSnapshot
                do {
                    snap = try tx.getDocument(likeRef)
                } catch let err as NSError {
                    errorPointer?.pointee = err
                    return nil
                }
                if snap.exists {
                    tx.deleteDocument(likeRef)
                    tx.updateData(["likeCount": FieldValue.increment(Int64(-1))], forDocument: diaryRef)
                    return false
                }
                tx.setData(["userId": uid, "userName": userName, "createdAt": now], forDocument: likeRef)
                tx.updateData(["likeCount": FieldValue.increment(Int64(1))], forDocument: diaryRef)
                if let notifRef { tx.setData(notifData, forDocument: notifRef) }
                return true
            }
            // 일일 퀘스트 — 좋아요를 "누를" 때만, 같은 별은 하루 한 번(Android InteractionViewModel 과 같은 규칙).
            if (liked as? Bool) == true { QuestStore.report(.like, key: diaryId) }
            return true
        } catch {
            return false
        }
    }

    /// 댓글 등록 — 실패(권한 거부 등)면 false. 오프라인이면 실패하지 않고 연결될 때까지 기다린다(목록엔 바로 보임).
    func addComment(uid: String?, userName: String, text: String) async -> Bool {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let uid, let diaryId = diary.id, !body.isEmpty else { return false }
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
        do { try await batch.commit() } catch { return false }
        QuestStore.report(.comment)
        if diary.userId != uid { QuestStore.report(.commentOther, key: diaryId) }
        return true
    }

    func deleteComment(_ c: Comment) async -> Bool {
        guard let diaryId = diary.id, let cid = c.id else { return false }
        let diaryRef = FirestoreService.diaries.document(diaryId)
        let batch = FirestoreService.db.batch()
        batch.deleteDocument(FirestoreService.comments(of: diaryId).document(cid))
        batch.updateData(["commentCount": FieldValue.increment(Int64(-1))], forDocument: diaryRef)
        do { try await batch.commit(); return true } catch { return false }
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
