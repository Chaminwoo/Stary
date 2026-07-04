import FirebaseFirestore
import Foundation

/// 차단/신고 — 안전 기능(App Store 정책 대응). (Android `FirebaseModerationRepository` 패리티)
///  - users/{uid}/blocked/{blockedUid} : 내가 차단한 사용자(문서 id = 상대 uid)
///  - reports/{id}                     : 콘텐츠/사용자 신고
enum ModerationRepository {

    /// 사용자 차단. fire-and-forget.
    static func block(userId: String, targetId: String, targetName: String) async {
        guard !userId.isEmpty, !targetId.isEmpty, userId != targetId else { return }
        try? await FirestoreService.blocked(of: userId).document(targetId).setData([
            "userName": targetName,
            "createdAt": FirestoreService.nowMillis,
        ])
    }

    /// 차단 해제.
    static func unblock(userId: String, targetId: String) async {
        guard !userId.isEmpty, !targetId.isEmpty else { return }
        try? await FirestoreService.blocked(of: userId).document(targetId).delete()
    }

    static func isBlocked(userId: String, targetId: String) async -> Bool {
        guard !userId.isEmpty, !targetId.isEmpty else { return false }
        let doc = try? await FirestoreService.blocked(of: userId).document(targetId).getDocument()
        return doc?.exists ?? false
    }

    /// 신고 접수. [type] = "diary" | "comment" | "user".
    /// [extra] 는 관리자가 Firebase Console 에서 바로 검토하도록 넣는 사람이 읽을 스냅샷
    /// (targetTitle/targetContent/targetOwnerName/targetImageUrl 등). Android `report(extra=)` 패리티.
    /// status 흐름: "open" → 관리자가 Console 에서 "action_delete"(다이어리 삭제)/"action_ban"(계정 제재)/"dismissed"(기각).
    static func report(reporterId: String, type: String, targetId: String,
                       targetOwnerId: String, reason: String,
                       extra: [String: Any] = [:]) async {
        guard !reporterId.isEmpty, !targetId.isEmpty else { return }
        var data: [String: Any] = [
            "reporterId": reporterId,
            "type": type,
            "targetId": targetId,
            "targetOwnerId": targetOwnerId,
            "reason": reason,
            "createdAt": FirestoreService.nowMillis,
            "status": "open",
        ]
        for (k, v) in extra { data[k] = v }
        try? await FirestoreService.reports.addDocument(data: data)
    }
}

/// 내가 차단한 사용자 id 집합을 실시간 관찰 — 차단한 사람의 다이어리/댓글을 숨기는 데 쓴다.
/// (Android `observeBlockedIds` 구독 패리티. ViewedStore 와 동일 구조.)
@MainActor
final class BlockStore: ObservableObject {
    @Published private(set) var blockedIds: Set<String> = []

    private var reg: ListenerRegistration?
    private var uid: String?

    func start(uid: String) {
        guard self.uid != uid else { return }
        stop()
        self.uid = uid
        reg = FirestoreService.blocked(of: uid).addSnapshotListener { [weak self] snap, _ in
            self?.blockedIds = Set(snap?.documents.map { $0.documentID } ?? [])
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
        uid = nil
        blockedIds = []
    }

    deinit { reg?.remove() }
}
