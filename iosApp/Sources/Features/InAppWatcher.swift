import FirebaseFirestore
import Foundation

/// 채팅방 메타 요약(인앱 팝업용). Android ChatSummary 패리티.
struct ChatSummary {
    let chatId: String
    let participants: [String]
    let lastMessage: String
    let lastSenderId: String
    let lastSenderName: String
    let updatedAt: Int64
}

/// 지금 보고 있는 채팅 상대 — 그 방 메시지는 배너를 띄우지 않게 한다(Android suppressChatWith).
@MainActor
final class ChatPresence {
    static let shared = ChatPresence()
    var activeFriendId: String?
}

/// 인앱 팝업 감시기 — 내가 참여한 채팅방 + 내 알림을 관찰해, 새로 도착하면 상단 배너를 띄운다.
/// (Android `ChatPopupWatcher` + `NotificationPopupWatcher` 패리티.)
/// 최초 구독분은 기준선으로만 잡아 앱 켤 때 과거 항목이 폭주하지 않게 한다.
@MainActor
final class InAppWatcher: ObservableObject {
    private var regs: [ListenerRegistration] = []

    // 채팅 dedup — "방:updatedAt" 키 집합. 같은 메시지는 두 번 다시 뜨지 않는다.
    private var shownChatKeys: Set<String> = []
    private var chatBaselineDone = false

    // 알림 dedup
    private var shownNotifIds: Set<String> = []
    private var notifBaseline: Int64?

    private var uid: String?
    private var onOpenChat: ((String, String) -> Void)?
    private var onOpenNotification: ((AppNotification) -> Void)?

    func start(uid: String,
               onOpenChat: @escaping (String, String) -> Void,
               onOpenNotification: @escaping (AppNotification) -> Void) {
        guard self.uid != uid else { return }
        stop()
        self.uid = uid
        self.onOpenChat = onOpenChat
        self.onOpenNotification = onOpenNotification

        // 내 채팅방 메타 — orderBy 서버 금지(복합 인덱스 회피), 판단은 클라이언트.
        regs.append(
            FirestoreService.chats
                .whereField("participants", arrayContains: uid)
                .addSnapshotListener { [weak self] snap, _ in
                    let chats = snap?.documents.map { doc -> ChatSummary in
                        let participants = (doc.get("participants") as? [String]) ?? doc.documentID.components(separatedBy: "_")
                        return ChatSummary(
                            chatId: doc.documentID,
                            participants: participants,
                            lastMessage: doc.get("lastMessage") as? String ?? "",
                            lastSenderId: doc.get("lastSenderId") as? String ?? "",
                            lastSenderName: doc.get("lastSenderName") as? String ?? "",
                            // Firestore 정수는 NSNumber 로 브리지됨 → int64Value 로 안전 변환.
                            updatedAt: (doc.get("updatedAt") as? NSNumber)?.int64Value ?? 0
                        )
                    } ?? []
                    self?.handleChats(chats) // Firestore 콜백은 메인 큐(기존 VM 패턴과 동일)
                }
        )

        // 내 알림(수신자 diaryOwnerId). 서버는 whereField 만, 정렬/판단은 클라이언트.
        regs.append(
            FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: uid)
                .addSnapshotListener { [weak self] snap, _ in
                    let list = snap?.documents.compactMap { try? $0.data(as: AppNotification.self) } ?? []
                    self?.handleNotifications(list)
                }
        )
    }

    private func handleChats(_ chats: [ChatSummary]) {
        guard let uid else { return }
        if !chatBaselineDone {
            chats.forEach { shownChatKeys.insert("\($0.chatId):\($0.updatedAt)") }
            chatBaselineDone = true
            return
        }
        for c in chats {
            let key = "\(c.chatId):\(c.updatedAt)"
            if shownChatKeys.contains(key) { continue }
            shownChatKeys.insert(key)
            let friendId = c.participants.first { $0 != uid } ?? ""
            let isIncoming = c.lastSenderId != uid && !c.lastMessage.isEmpty
            guard isIncoming else { continue }
            let viewingThisChat = ChatPresence.shared.activeFriendId == friendId
            guard !viewingThisChat, AppSettings.shared.notificationsEnabled else { continue }
            let name = c.lastSenderName.isEmpty ? "새 메시지" : c.lastSenderName
            InAppBanner.shared.show(title: name, body: c.lastMessage, kind: .chat, key: key) { [weak self] in
                self?.onOpenChat?(friendId, name)
            }
        }
    }

    private func handleNotifications(_ list: [AppNotification]) {
        let maxCreated = list.map { $0.createdAt }.max() ?? 0
        if notifBaseline == nil {
            notifBaseline = maxCreated
            return
        }
        let base = notifBaseline ?? 0
        let fresh = list
            .filter { $0.createdAt > base && !shownNotifIds.contains($0.id ?? "") }
            .sorted { $0.createdAt < $1.createdAt }
        for n in fresh {
            shownNotifIds.insert(n.id ?? "")
            guard AppSettings.shared.notificationsEnabled else { continue }
            InAppBanner.shared.show(
                title: n.displayText,
                body: n.content.isEmpty ? n.diaryTitle : n.content,
                kind: .notification,
                key: "notif:\(n.id ?? "")"
            ) { [weak self] in self?.onOpenNotification?(n) }
        }
        notifBaseline = Swift.max(base, maxCreated)
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
        uid = nil
    }

    deinit { regs.forEach { $0.remove() } }
}
