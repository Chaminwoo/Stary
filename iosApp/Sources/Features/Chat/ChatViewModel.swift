import FirebaseFirestore
import Foundation

/// 1:1 채팅. chats/{chatId}/messages. chatId 는 두 uid 정렬·결합(AppConfig.chatId).
@MainActor
final class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    private var reg: ListenerRegistration?
    private let chatId: String

    init(myUid: String, friendUid: String) {
        self.chatId = AppConfig.chatId(myUid, friendUid)
    }

    deinit { reg?.remove() }

    func start() {
        reg?.remove()
        reg = FirestoreService.messages(of: chatId)
            .order(by: "createdAt", descending: false)
            .addSnapshotListener { [weak self] snap, _ in
                self?.messages = snap?.documents.compactMap { try? $0.data(as: ChatMessage.self) } ?? []
            }
    }

    func send(senderId: String, senderName: String, text: String) async {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        let now = FirestoreService.nowMillis
        // async 컨텍스트에선 addDocument(data:) 의 async throws 오버로드가 선택됨.
        _ = try? await FirestoreService.messages(of: chatId).addDocument(data: [
            "senderId": senderId, "senderName": senderName, "text": body, "createdAt": now,
        ])
        // 방 메타(목록/미리보기용). 실패해도 메시지는 전송됨.
        try? await FirestoreService.chats.document(chatId).setData([
            "participants": chatId.components(separatedBy: "_"),
            "lastMessage": body,
            "lastSenderId": senderId,
            "lastSenderName": senderName, // 인앱 채팅 배너 발신자명
            "updatedAt": now,
        ], merge: true)
    }

    /// 이 메시지를 지금 삭제할 수 있는가 — 내가 보냈고 전송 후 1분 이내일 때만. (삭제 UI 노출 판단)
    func canDelete(_ message: ChatMessage, myUid: String?) -> Bool {
        guard let myUid, message.senderId == myUid else { return false }
        return FirestoreService.nowMillis - message.createdAt <= AppConfig.chatDeleteWindowMs
    }

    /// 내가 보낸 메시지를 전송 후 1분 이내에 한해 완전 삭제(상대방 쪽에서도 사라짐). 조건 미충족 시 무시.
    func deleteMessage(_ message: ChatMessage, myUid: String?) async {
        guard canDelete(message, myUid: myUid), let id = message.id else { return }
        try? await FirestoreService.messages(of: chatId).document(id).delete()
    }

    func stop() {
        reg?.remove()
        reg = nil
    }
}
