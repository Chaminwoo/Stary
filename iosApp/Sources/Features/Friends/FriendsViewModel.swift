import FirebaseFirestore
import Foundation

/// 친구 목록/요청/검색. Android FirebaseFriendRepository 와 동일 스키마.
@MainActor
final class FriendsViewModel: ObservableObject {
    @Published var friends: [Friend] = []
    @Published var requests: [FriendRequest] = []
    @Published var results: [UserProfile] = []
    @Published var searching = false
    private var regs: [ListenerRegistration] = []

    deinit { regs.forEach { $0.remove() } }

    func start(uid: String) {
        stop()
        regs.append(
            FirestoreService.friends(of: uid).addSnapshotListener { [weak self] snap, _ in
                self?.friends = snap?.documents.compactMap { try? $0.data(as: Friend.self) } ?? []
            }
        )
        regs.append(
            FirestoreService.friendRequests.whereField("toId", isEqualTo: uid)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.requests = snap?.documents.compactMap { try? $0.data(as: FriendRequest.self) } ?? []
                }
        )
    }

    func search(query: String, excluding uid: String) async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { results = []; return }
        searching = true
        defer { searching = false }
        do {
            let snap = try await FirestoreService.users
                .whereField("userName", isGreaterThanOrEqualTo: q)
                .whereField("userName", isLessThanOrEqualTo: q + "\u{f8ff}")
                .limit(to: 20)
                .getDocuments()
            results = snap.documents.compactMap { try? $0.data(as: UserProfile.self) }
                .filter { $0.userId != uid && !$0.userName.isEmpty }
        } catch {
            results = []
        }
    }

    func sendRequest(fromId: String, fromName: String, to: UserProfile) async {
        do {
            let dup = try await FirestoreService.friendRequests
                .whereField("fromId", isEqualTo: fromId)
                .whereField("toId", isEqualTo: to.userId)
                .getDocuments()
            guard dup.documents.isEmpty else { return }
            let ref = FirestoreService.friendRequests.document()
            try await ref.setData([
                "fromId": fromId, "fromName": fromName,
                "fromPhotoUrl": "",
                "toId": to.userId, "toName": to.userName,
                "createdAt": FirestoreService.nowMillis,
            ])
        } catch {}
    }

    func accept(_ r: FriendRequest, myUid: String, myName: String) async {
        guard let reqId = r.id else { return }
        let now = FirestoreService.nowMillis
        let batch = FirestoreService.db.batch()
        // 내 친구 목록에 상대 추가
        batch.setData(["userId": r.fromId, "userName": r.fromName, "photoUrl": r.fromPhotoUrl, "createdAt": now],
                      forDocument: FirestoreService.friends(of: myUid).document(r.fromId))
        // 상대 친구 목록에 나 추가
        batch.setData(["userId": myUid, "userName": myName, "photoUrl": "", "createdAt": now],
                      forDocument: FirestoreService.friends(of: r.fromId).document(myUid))
        batch.deleteDocument(FirestoreService.friendRequests.document(reqId))
        try? await batch.commit()
    }

    func decline(_ r: FriendRequest) async {
        guard let id = r.id else { return }
        try? await FirestoreService.friendRequests.document(id).delete()
    }

    func remove(uid: String, friendId: String) async {
        let batch = FirestoreService.db.batch()
        batch.deleteDocument(FirestoreService.friends(of: uid).document(friendId))
        batch.deleteDocument(FirestoreService.friends(of: friendId).document(uid))
        try? await batch.commit()
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
    }
}
