import FirebaseFirestore
import Foundation

/// 1:1 채팅. chats/{chatId}/messages. chatId 는 두 uid 정렬·결합(AppConfig.chatId).
/// 나에게서만 삭제한 것은 users/{나}/chatHidden/{chatId}(`ChatHidden`)로 가린다(Android ChatViewModel 패리티).
@MainActor
final class ChatViewModel: ObservableObject {
    /// 화면에 보일 메시지 = 방 전체 − 나에게서만 삭제한 것.
    @Published private(set) var messages: [ChatMessage] = []
    /// 방의 전체 메시지(양쪽 공용, createdAt 오름차순).
    private var allMessages: [ChatMessage] = [] { didSet { applyHidden() } }
    /// 내가 나에게서만 지운 것.
    private var hidden: ChatHidden = .empty { didSet { applyHidden() } }
    private var reg: ListenerRegistration?
    private var hiddenReg: ListenerRegistration?
    private let chatId: String
    private let myUid: String

    init(myUid: String, friendUid: String) {
        self.myUid = myUid
        self.chatId = AppConfig.chatId(myUid, friendUid)
    }

    deinit {
        reg?.remove()
        hiddenReg?.remove()
    }

    func start() {
        stop()
        reg = FirestoreService.messages(of: chatId)
            .order(by: "createdAt", descending: false)
            .addSnapshotListener { [weak self] snap, _ in
                self?.allMessages = snap?.documents.compactMap { try? $0.data(as: ChatMessage.self) } ?? []
            }
        guard !myUid.isEmpty else { return }
        hiddenReg = FirestoreService.chatHidden(of: myUid).document(chatId)
            .addSnapshotListener { [weak self] snap, error in
                // 규칙 미배포(chatHidden 경로)면 권한 오류 — 숨김 없이 보여 주고 채팅은 계속 동작.
                if let error { print("⚠️ chatHidden 구독 실패: \(error.localizedDescription)") }
                self?.hidden = snap?.data().map(ChatHidden.init(data:)) ?? .empty
            }
    }

    private func applyHidden() {
        let h = hidden
        messages = allMessages.filter { !h.hides($0) }
    }

    /// 메시지 전송. 실패하면 false(호출부에서 토스트) — 예전엔 try? 로 조용히 삼켜
    /// "보냈는데 안 가는" 상태를 알 수 없었다.
    @discardableResult
    func send(senderId: String, senderName: String, text: String) async -> Bool {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, !senderId.isEmpty else { return false }
        let now = FirestoreService.nowMillis
        do {
            // ⚠️ 방 메타를 **먼저** 만든다(Android FirebaseChatRepository 와 동일 순서).
            // 서버 규칙이 방 문서로 참여자를 확인하던 시절엔 방이 없으면 메시지 생성이 거부됐다.
            try await FirestoreService.chats.document(chatId).setData([
                "participants": chatId.components(separatedBy: "_"),
                "lastMessage": body,
                "lastSenderId": senderId,
                "lastSenderName": senderName, // 인앱 채팅 배너 발신자명
                "updatedAt": now,
                "lastReadAt": [senderId: now], // 보낸 사람은 읽은 것으로(Android 패리티)
            ], merge: true)
            // async 컨텍스트에선 addDocument(data:) 의 async throws 오버로드가 선택됨.
            _ = try await FirestoreService.messages(of: chatId).addDocument(data: [
                "senderId": senderId, "senderName": senderName, "text": body, "createdAt": now,
            ])
            return true
        } catch {
            print("⚠️ 채팅 전송 실패: \(error.localizedDescription)")
            return false
        }
    }

    /// 이 메시지를 지금 삭제할 수 있는가 — 내가 보냈고 전송 후 1분 이내일 때만. (삭제 UI 노출 판단)
    func canDelete(_ message: ChatMessage, myUid: String?) -> Bool {
        guard let myUid, message.senderId == myUid else { return false }
        return FirestoreService.nowMillis - message.createdAt <= AppConfig.chatDeleteWindowMs
    }

    /// 내가 보낸 메시지를 전송 후 1분 이내에 한해 완전 삭제(상대방 쪽에서도 사라짐). 실패/조건 미충족이면 false.
    @discardableResult
    func deleteMessage(_ message: ChatMessage, myUid: String?) async -> Bool {
        guard canDelete(message, myUid: myUid), let id = message.id else { return false }
        do {
            try await FirestoreService.messages(of: chatId).document(id).delete()
            return true
        } catch {
            print("⚠️ 메시지 삭제 실패: \(error.localizedDescription)")
            return false
        }
    }

    /// 메시지 하나를 **나에게서만** 삭제(내 것/상대 것, 시간 제한 없음). 상대 방에는 그대로 남는다.
    /// 친구 목록 미리보기용으로 "숨긴 뒤 내게 남는 마지막 메시지"를 같이 적는다(Android hideMessageForMe 패리티).
    func hideForMe(_ message: ChatMessage) async -> Bool {
        guard !myUid.isEmpty, let id = message.id else { return false }
        var after = hidden
        after.messageIds.insert(id)
        let remaining = allMessages.last { !after.hides($0) }
        let latestAt = max(allMessages.map(\.createdAt).max() ?? 0, message.createdAt)
        do {
            try await FirestoreService.chatHidden(of: myUid).document(chatId).setData([
                "messageIds": FieldValue.arrayUnion([id]),
                "previewFor": latestAt,
                "previewText": remaining?.text ?? "",
                "previewAt": remaining?.createdAt ?? 0,
                "previewSenderId": remaining?.senderId ?? "",
            ], merge: true)
            return true
        } catch {
            print("⚠️ 나만 삭제 실패: \(error.localizedDescription)")
            return false
        }
    }

    /// 지금 보이는 대화가 있는가 — "대화 내용 삭제" 메뉴 판단용.
    var hasVisibleMessages: Bool { !messages.isEmpty }

    /// 대화방을 **나에게서만** 삭제 — 지금까지의 메시지를 내 화면에서 전부 가린다(새 메시지는 그대로 보인다).
    func clearForMe() async -> Bool {
        let upTo = allMessages.map(\.createdAt).max() ?? 0
        return await Self.clearChatForMe(myUid: myUid, chatId: chatId, upTo: upTo)
    }

    /// 대화 나만 삭제 — 친구 목록 롱프레스에서도 쓴다(Android FirebaseChatRepository.clearChatForMe 패리티).
    /// 방 메타 updatedAt 도 함께 본다(1분 삭제로 사라진 메시지가 방 미리보기에는 남아 있을 수 있어서).
    /// clearedAt 이 그 이전 것을 전부 가리므로 개별 숨김 id·미리보기 대체값은 비운다(문서가 계속 커지지 않게).
    static func clearChatForMe(myUid: String, chatId: String, upTo: Int64) async -> Bool {
        guard !myUid.isEmpty else { return false }
        let meta = try? await FirestoreService.chats.document(chatId).getDocument()
        let metaAt = (meta?.get("updatedAt") as? NSNumber)?.int64Value ?? 0
        let cut = max(upTo, metaAt)
        guard cut > 0 else { return true }
        do {
            try await FirestoreService.chatHidden(of: myUid).document(chatId).setData([
                "clearedAt": cut,
                "messageIds": [String](),
                "previewFor": Int64(0),
                "previewText": "",
                "previewAt": Int64(0),
                "previewSenderId": "",
            ])
            return true
        } catch {
            print("⚠️ 대화 나만 삭제 실패: \(error.localizedDescription)")
            return false
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
        hiddenReg?.remove()
        hiddenReg = nil
    }
}
