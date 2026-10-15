import FirebaseAuth
import FirebaseFirestore
import Foundation

/// 글로브에서 본 유성 기록 — 유성 업적(첫 유성 · 3/5연속 · 누적 50개)과 히든 "7연속" 판정용(2026-10-15).
/// Android `MeteorStatsRepository` 패리티(같은 필드·규칙).
///
/// `users/{uid}` 두 필드(재설치·기기 변경에도 유지, 오프라인이면 쓰기 대기 후 반영):
///  - `meteorsSeen` : 본 유성 수(`FieldValue.increment`)
///  - `meteorStreaks` : 도달한 연속 횟수들(`FieldValue.arrayUnion`) — 최고 연속 = 최댓값.
///    "최고값" 하나를 덮어쓰면 다른 기기·재설치 직후(로컬 최고값 0)에 낮은 값으로 덮일 수 있어, 줄어들 수 없는 집합으로 쌓는다.
/// ⚠️ `userName`/`profileImageUrl` 은 건드리지 않는다(CLAUDE.md §2.6 — 항상 merge).
final class MeteorStats: ObservableObject {
    static let shared = MeteorStats()
    private init() {}

    static let fieldSeen = "meteorsSeen"
    static let fieldStreaks = "meteorStreaks"

    @Published private(set) var seen = 0
    @Published private(set) var bestStreak = 0

    private var listener: ListenerRegistration?
    private var listeningUid: String?

    /// 로그인 사용자 문서를 구독한다(uid 가 바뀌면 다시, nil 이면 끊고 0 으로).
    func start(uid: String?) {
        guard uid != listeningUid else { return }
        listener?.remove()
        listener = nil
        listeningUid = uid
        seen = 0
        bestStreak = 0
        guard let uid, !uid.isEmpty else { return }
        listener = FirestoreService.users.document(uid).addSnapshotListener { [weak self] snap, error in
            guard let self, error == nil, let snap else { return }
            let seen = (snap.get(Self.fieldSeen) as? NSNumber)?.intValue ?? 0
            let best = ((snap.get(Self.fieldStreaks) as? [Any]) ?? [])
                .compactMap { ($0 as? NSNumber)?.intValue }.max() ?? 0
            DispatchQueue.main.async {
                self.seen = seen
                self.bestStreak = best
            }
        }
    }

    /// 유성 1개를 봤다 — `streak` = 이번 유성까지의 연속 횟수(1 = 연속 아님). 비로그인이면 무시. 어느 스레드에서 불러도 된다.
    static func record(streak: Int) {
        guard let uid = AuthManager.appUserId(of: Auth.auth().currentUser), !uid.isEmpty else { return }
        FirestoreService.users.document(uid).setData([
            fieldSeen: FieldValue.increment(Int64(1)),
            fieldStreaks: FieldValue.arrayUnion([streak]),
        ], merge: true)
    }
}
