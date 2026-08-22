import FirebaseFirestore
import Foundation

/// 차단/신고 — 안전 기능(App Store 정책 대응). (Android `FirebaseModerationRepository` 패리티)
///  - users/{uid}/blocked/{blockedUid} : 내가 차단한 사용자(문서 id = 상대 uid)
///  - reports/{id}                     : 콘텐츠/사용자 신고
enum ModerationRepository {

    /// 사용자 차단. fire-and-forget.
    /// 이름/사진은 차단 목록 화면에서 바로 보여주기 위한 차단 시점 스냅샷(Android `block(targetPhotoUrl=)` 패리티).
    static func block(userId: String, targetId: String, targetName: String, targetPhotoUrl: String = "") async {
        guard !userId.isEmpty, !targetId.isEmpty, userId != targetId else { return }
        try? await FirestoreService.blocked(of: userId).document(targetId).setData([
            "userName": targetName,
            "photoUrl": targetPhotoUrl,
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

/// 차단 목록 화면용 한 줄 — Firestore `users/{uid}/blocked/{blockedUid}`.
/// (Android `BlockedUser` 모델 패리티. 이름/사진은 차단 시점 스냅샷.)
struct BlockedUser: Identifiable, Equatable {
    let userId: String
    let userName: String
    let photoUrl: String
    let createdAt: Int64

    var id: String { userId }
}

/// 내가 차단한 사용자 id 집합을 실시간 관찰 — 차단한 사람의 다이어리/댓글을 숨기는 데 쓴다.
/// (Android `observeBlockedIds` 구독 패리티. ViewedStore 와 동일 구조.)
@MainActor
final class BlockStore: ObservableObject {
    @Published private(set) var blockedIds: Set<String> = []
    /// 차단 목록 화면(BlockedUsersScreen)이 쓰는 상세 목록 — 최근 차단 순.
    @Published private(set) var blockedUsers: [BlockedUser] = []

    private var reg: ListenerRegistration?
    private var uid: String?

    func start(uid: String) {
        guard self.uid != uid else { return }
        stop()
        self.uid = uid
        reg = FirestoreService.blocked(of: uid).addSnapshotListener { [weak self] snap, _ in
            let docs = snap?.documents ?? []
            self?.blockedIds = Set(docs.map { $0.documentID })
            self?.blockedUsers = docs.map { doc in
                BlockedUser(
                    userId: doc.documentID,
                    userName: doc.get("userName") as? String ?? "",
                    photoUrl: doc.get("photoUrl") as? String ?? "",
                    createdAt: (doc.get("createdAt") as? Int64) ?? Int64(doc.get("createdAt") as? Double ?? 0)
                )
            }
            .sorted { $0.createdAt > $1.createdAt }
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
        uid = nil
        blockedIds = []
        blockedUsers = []
    }

    deinit { reg?.remove() }
}
