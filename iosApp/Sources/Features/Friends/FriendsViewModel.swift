import FirebaseFirestore
import Foundation

/// 친구 목록/요청/검색. Android FirebaseFriendRepository 와 동일 스키마.
@MainActor
final class FriendsViewModel: ObservableObject {
    @Published var friends: [Friend] = []
    @Published var requests: [FriendRequest] = []
    @Published var results: [UserProfile] = []
    @Published var searching = false
    /// 내가 보낸 pending 요청의 상대 uid — 검색 결과의 "요청됨" 상태 칩 표시용. (Android observeOutgoingRequests 패리티)
    @Published var outgoingIds: Set<String> = []
    /// 친구 uid → 그 친구와의 채팅방 메타(마지막 메시지/시각) — 친구 행 미리보기·미읽음 점.
    @Published var chatSummaries: [String: ChatSummary] = [:]
    private var regs: [ListenerRegistration] = []

    deinit { regs.forEach { $0.remove() } }

    func start(uid: String) {
        stop()
        // 내 채팅방 메타 — orderBy 서버 금지(복합 인덱스 회피), 정렬/판단은 클라이언트.
        // (InAppWatcher 와 같은 쿼리지만 용도가 달라 별도 구독: 여기선 목록 행 표시용.)
        regs.append(
            FirestoreService.chats
                .whereField("participants", arrayContains: uid)
                .addSnapshotListener { [weak self] snap, _ in
                    var byFriend: [String: ChatSummary] = [:]
                    for doc in snap?.documents ?? [] {
                        let participants = (doc.get("participants") as? [String])
                            ?? doc.documentID.components(separatedBy: "_")
                        guard let friendId = participants.first(where: { $0 != uid }) else { continue }
                        byFriend[friendId] = ChatSummary(
                            chatId: doc.documentID,
                            participants: participants,
                            lastMessage: doc.get("lastMessage") as? String ?? "",
                            lastSenderId: doc.get("lastSenderId") as? String ?? "",
                            lastSenderName: doc.get("lastSenderName") as? String ?? "",
                            updatedAt: (doc.get("updatedAt") as? NSNumber)?.int64Value ?? 0
                        )
                    }
                    self?.chatSummaries = byFriend
                }
        )
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
        // 내가 보낸 pending 요청 — "요청됨" 칩(검색 결과)용.
        regs.append(
            FirestoreService.friendRequests.whereField("fromId", isEqualTo: uid)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.outgoingIds = Set(snap?.documents.compactMap { $0.get("toId") as? String } ?? [])
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

    /// 친구 요청 전송 — 성공(중복 포함) true / 실패 false. (Android sendRequest 반환값 패리티 — 토스트용)
    @discardableResult
    func sendRequest(fromId: String, fromName: String, to: UserProfile) async -> Bool {
        do {
            let dup = try await FirestoreService.friendRequests
                .whereField("fromId", isEqualTo: fromId)
                .whereField("toId", isEqualTo: to.userId)
                .getDocuments()
            guard dup.documents.isEmpty else { return true }
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
            // 상대에게 알림 문서 생성 — 전면이면 인앱 배너, 후면/종료면 Cloud Functions 가 FCM 푸시.
            // (Android FirebaseFriendRepository.sendRequest 와 동일 — 중복 요청일 땐 위에서 return.)
            await Self.notifyFriendRequest(actorId: fromId, actorName: fromName, toId: to.userId)
            return true
        } catch {
            return false
        }
    }

    /// 친구 요청 알림 문서(notifications/{id}) 생성 — 수신자 = toId.
    /// 필드/타입은 Android `AppNotification` 저장 형태와 동일해야 한다(양쪽이 같은 문서를 읽음).
    static func notifyFriendRequest(actorId: String, actorName: String, toId: String) async {
        guard !actorId.isEmpty, !toId.isEmpty else { return }
        let ref = FirestoreService.notifications.document()
        try? await ref.setData([
            "id": ref.documentID,
            "type": "FRIEND_REQUEST",
            "diaryId": "",
            "diaryTitle": "",
            "diaryOwnerId": toId,   // 알림 수신자
            "actorId": actorId,
            "actorName": actorName,
            "content": "",
            "createdAt": FirestoreService.nowMillis,
            "read": false,          // Kotlin isRead 직렬화 규칙상 필드명은 read
        ])
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
        chatSummaries = [:]
    }
}
