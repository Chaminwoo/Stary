import Combine
import FirebaseFirestore
import Foundation

/// 알림 목록/읽음. 컬렉션 notifications, 수신자 diaryOwnerId, 읽음 필드 read.
/// + 첫 별 공지(FirstStarFeed — firstStars 컬렉션)를 시간순으로 합친다(Android NotificationViewModel 패리티).
@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var items: [AppNotification] = []
    @Published var unread = 0
    private var regs: [ListenerRegistration] = []
    private var feedSub: AnyCancellable?

    private var ownerId = ""
    private var mine: [AppNotification] = []
    private var mineUnread = 0
    private var firstStars: [AppNotification] = []

    deinit { regs.forEach { $0.remove() } }

    func start(ownerId: String) {
        stop()
        self.ownerId = ownerId
        regs.append(
            // order(by:) 를 서버에 두면 (diaryOwnerId + createdAt) 복합 인덱스 필요 → 미생성 시 누락.
            // 서버는 whereField 만, 정렬은 클라이언트(Android observeNotifications 와 동일 패턴).
            FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: ownerId)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.mine = snap?.documents.compactMap { try? $0.data(as: AppNotification.self) } ?? []
                    self?.merge()
                }
        )
        regs.append(
            FirestoreService.notifications
                .whereField("diaryOwnerId", isEqualTo: ownerId)
                .whereField("read", isEqualTo: false)
                .addSnapshotListener { [weak self] snap, _ in
                    self?.mineUnread = snap?.documents.count ?? 0
                    self?.merge()
                }
        )
        regs.append(
            FirstStarFeed.query.addSnapshotListener { [weak self] snap, _ in
                self?.firstStars = snap?.documents.compactMap { FirstStarFeed.parse($0) } ?? []
                self?.merge()
            }
        )
        // 읽음/숨김(이 기기 저장)이 바뀌면 — 다른 화면의 뷰모델(탑바 빨간 점)도 같이 갱신.
        feedSub = FirstStarFeed.shared.$seenAt.combineLatest(FirstStarFeed.shared.$hidden)
            .sink { [weak self] _ in
                // @Published 는 값이 바뀌기 **직전**에 내보내므로 한 박자 뒤에 합친다.
                Task { @MainActor in self?.merge() }
            }
    }

    private func merge() {
        let stars = FirstStarFeed.shared.visible(firstStars, myId: ownerId)
        items = (mine + stars).sorted { $0.createdAt > $1.createdAt }
        unread = mineUnread + stars.filter { !$0.read }.count
    }

    func markAllRead(ownerId: String) async {
        FirstStarFeed.shared.markAllSeen()
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
        // 첫 별 공지는 모두가 보는 공용 문서 → 이 기기에서만 숨긴다.
        if FirstStarFeed.isFirstStar(n) {
            FirstStarFeed.shared.hide(n)
            return
        }
        guard let id = n.id else { return }
        try? await FirestoreService.notifications.document(id).delete()
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
        feedSub = nil
    }
}
