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

    func stop() {
        reg?.remove()
        reg = nil
    }
}
