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
                let base = snap?.documents.compactMap { try? $0.data(as: Friend.self) } ?? []
                self?.friends = base
                // friends 문서는 요청/수락 시점의 스냅샷이라 이후 상대가 이름/사진을 바꾸면 낡은 값이 남는다.
                // users/{friendUid} 의 현재 공개 프로필로 덮어써서 항상 최신 이름/사진이 보이게 한다. (Android observeFriends 패리티)
                Task { [weak self] in
                    var enriched: [Friend] = []
                    for friend in base {
                        guard let doc = try? await FirestoreService.users.document(friend.userId).getDocument() else {
                            enriched.append(friend)
                            continue
                        }
                        let name = doc.get("userName") as? String
                        let photo = doc.get("profileImageUrl") as? String
                        var updated = friend
                        if let name, !name.isEmpty { updated.userName = name }
                        if let photo, !photo.isEmpty { updated.photoUrl = photo }
                        enriched.append(updated)
                    }
                    self?.friends = enriched
                }
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
            var list = snap.documents.compactMap { try? $0.data(as: UserProfile.self) }
                .filter { $0.userId != uid && !$0.userName.isEmpty }
            // 2명 이상이면 "나와 겹치는 친구가 많은 순"으로 정렬(동률은 이름 순 유지).
            if list.count >= 2 && !uid.isEmpty {
                let myFriends = await Self.friendIds(of: uid)
                var scored: [(idx: Int, u: UserProfile, c: Int)] = []
                for (i, u) in list.enumerated() {
                    let theirs = await Self.friendIds(of: u.userId)
                    scored.append((i, u, theirs.intersection(myFriends).count))
                }
                list = scored.sorted { $0.c != $1.c ? $0.c > $1.c : $0.idx < $1.idx }.map { $0.u }
            }
            results = list
        } catch {
            results = []
        }
    }

    /// 한 사용자의 친구 uid 집합(users/{uid}/friends 문서 id). 공통 친구 수 계산용. (Android friendIds 패리티)
    static func friendIds(of uid: String) async -> Set<String> {
        guard let snap = try? await FirestoreService.friends(of: uid).getDocuments() else { return [] }
        return Set(snap.documents.map { $0.documentID })
    }

    func sendRequest(fromId: String, fromName: String, to: UserProfile) async {
        do {
            let dup = try await FirestoreService.friendRequests
                .whereField("fromId", isEqualTo: fromId)
                .whereField("toId", isEqualTo: to.userId)
                .getDocuments()
            guard dup.documents.isEmpty else { return }
            // 보낸 사람 사진을 채워 빈 값으로 저장하지 않는다(상대 친구목록 아바타에 보이도록).
            let myPhoto = ((try? await FirestoreService.users.document(fromId).getDocument())?
                .get("profileImageUrl") as? String) ?? ""
            let ref = FirestoreService.friendRequests.document()
            try await ref.setData([
                "fromId": fromId, "fromName": fromName,
                "fromPhotoUrl": myPhoto,
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
        // 상대 친구 목록에 나 추가 — 내 사진을 채워 빈 값으로 저장하지 않는다.
        let myPhoto = ((try? await FirestoreService.users.document(myUid).getDocument())?
            .get("profileImageUrl") as? String) ?? ""
        batch.setData(["userId": myUid, "userName": myName, "photoUrl": myPhoto, "createdAt": now],
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
