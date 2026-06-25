import FirebaseFirestore
import Foundation

/// 알림 목록/읽음. 컬렉션 notifications, 수신자 diaryOwnerId, 읽음 필드 read.
@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var items: [AppNotification] = []
    @Published var unread = 0
    private var regs: [ListenerRegistration] = []

    deinit { regs.forEach { $0.remove() } }

    func start(ownerId: String) {
        stop()
        regs.append(
            FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: ownerId)
                .order(by: "createdAt", descending: true)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.items = snap?.documents.compactMap { try? $0.data(as: AppNotification.self) } ?? []
                }
        )
        regs.append(
            FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: ownerId)
                .whereField("read", isEqualTo: false)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.unread = snap?.documents.count ?? 0
                }
        )
    }

    func markAllRead(ownerId: String) async {
        do {
            let snap = try await FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: ownerId)
                .whereField("read", isEqualTo: false)
                .getDocuments()
            let batch = FirestoreService.db.batch()
            snap.documents.forEach { batch.updateData(["read": true], forDocument: $0.reference) }
            try await batch.commit()
        } catch {}
    }

    func delete(_ n: AppNotification) async {
        guard let id = n.id else { return }
        try? await FirestoreService.notifications.document(id).delete()
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
    }
}
