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

    // 와처 시작 시각. 채팅은 이 시각 이후 "갱신된(updatedAt)", 알림은 "생성된(createdAt)" 것만 배너로 띄운다
    // (앱 켤 때 기존 항목이 새 항목으로 잘못 떠 한 번씩 뜨던 문제 방지 — Android 와 동일 타임스탬프 기준).
    private var sinceTime: Int64 = 0

    // 알림 dedup
    private var shownNotifIds: Set<String> = []

    private var uid: String?
    private var onOpenChat: ((String, String) -> Void)?
    private var onOpenNotification: ((AppNotification) -> Void)?

    func start(uid: String,
               onOpenChat: @escaping (String, String) -> Void,
               onOpenNotification: @escaping (AppNotification) -> Void) {
        guard self.uid != uid else { return }
        stop()
        self.uid = uid
        self.sinceTime = FirestoreService.nowMillis
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
        for c in chats {
            guard c.updatedAt > sinceTime else { continue } // 와처 시작 전 메시지(=기존) → 무시
            let friendId = c.participants.first { $0 != uid } ?? ""
            let isIncoming = c.lastSenderId != uid && !c.lastMessage.isEmpty
            guard isIncoming else { continue }
            let viewingThisChat = ChatPresence.shared.activeFriendId == friendId
            guard !viewingThisChat, AppSettings.shared.notificationsEnabled else { continue }
            // 방 메타의 lastSenderName 은 보낸 시점 스냅샷 → 상대의 현재 닉네임으로.
            UserDirectory.shared.ensureWatching(friendId)
            let live = UserDirectory.shared.name(friendId, fallback: c.lastSenderName)
            let name = live.isEmpty ? "새 메시지" : live
            InAppBanner.shared.show(title: name, body: c.lastMessage, kind: .chat, key: "\(c.chatId):\(c.updatedAt)") { [weak self] in
                self?.onOpenChat?(friendId, name)
            }
        }
    }

    private func handleNotifications(_ list: [AppNotification]) {
        // ⚠️ 예전엔 "첫 스냅샷의 최대 createdAt" 을 기준선으로 삼았는데, 로그아웃/권한 오류로 **빈 스냅샷**이
        //    먼저 도착하면 기준선이 0 으로 주저앉아 재로그인 직후 과거 알림이 전부 새 알림처럼 배너로 떴다.
        //    채팅과 동일하게 와처 시작 시각(sinceTime) 기준으로 판단한다.
        //    (Android `NotificationPopupWatcher` 패리티)
        let fresh = list
            .filter { $0.createdAt > sinceTime && !shownNotifIds.contains($0.id ?? "") }
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
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
        uid = nil
    }

    deinit { regs.forEach { $0.remove() } }
}
