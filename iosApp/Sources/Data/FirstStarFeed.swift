import Combine
import FirebaseFirestore
import Foundation

/// 첫 별 공지 피드(2026-10-02) — "○○님이 세상에 첫 별을 남겼어요". Android `FirstStarFeed.kt` 패리티.
///
/// 서버(announceFirstStar)가 누군가 **처음으로 전체 공개** 별을 올리면 `firstStars/{작성자}` 를 하나 만든다.
/// 모든 사용자 앞으로 notifications 문서를 만들면 사용자 수만큼 쓰기가 생기므로, 앱이 이 컬렉션을 직접 읽어
/// 알림 목록(`NotificationsViewModel`)에 `type = "FIRST_STAR"` 로 합친다.
///
/// - 읽음/삭제는 **이 기기에만** 저장한다(공용 문서라 사용자별 필드를 둘 수 없다).
///   읽음 = 마지막으로 알림 화면을 연 시각(`markAllSeen`) 이전에 생긴 공지. 처음 설치한 기기는 그 시각부터 센다.
/// - 내 공지는 빼고, 최근 14일 안의 최신 30개만.
@MainActor
final class FirstStarFeed: ObservableObject {
    static let shared = FirstStarFeed()

    static let type = "FIRST_STAR"
    /// 알림 id 접두어 — notifications 문서 id 와 섞이지 않게(삭제 분기에 쓴다).
    private static let idPrefix = "firstStar_"
    private static let seenKey = "first_star_seen_at"
    private static let hiddenKey = "first_star_hidden"
    private static let maxAgeMs: Int64 = 14 * 24 * 60 * 60 * 1000
    static let limit = 30

    @Published private(set) var seenAt: Int64
    @Published private(set) var hidden: Set<String>

    private init() {
        let d = UserDefaults.standard
        if d.object(forKey: Self.seenKey) == nil { d.set(FirestoreService.nowMillis, forKey: Self.seenKey) }
        seenAt = (d.object(forKey: Self.seenKey) as? NSNumber)?.int64Value ?? 0
        hidden = Set(d.stringArray(forKey: Self.hiddenKey) ?? [])
    }

    static func isFirstStar(_ n: AppNotification) -> Bool { n.type == type }

    /// 최신 공지 쿼리(서버 정렬 — 단일 필드라 복합 인덱스 불필요).
    static var query: Query {
        FirestoreService.db.collection(AppConfig.Collections.firstStars)
            .order(by: "createdAt", descending: true)
            .limit(to: limit)
    }

    /// 공지 문서 → 알림 행. 오래된 공지·필드 누락은 버린다.
    static func parse(_ doc: QueryDocumentSnapshot) -> AppNotification? {
        let data = doc.data()
        guard let createdAt = (data["createdAt"] as? NSNumber)?.int64Value,
              createdAt >= FirestoreService.nowMillis - maxAgeMs else { return nil }
        return AppNotification(
            id: idPrefix + doc.documentID,
            type: type,
            diaryId: data["diaryId"] as? String ?? "",
            diaryTitle: data["diaryTitle"] as? String ?? "",
            actorId: data["actorId"] as? String ?? doc.documentID,
            actorName: data["actorName"] as? String ?? "",
            createdAt: createdAt
        )
    }

    /// 알림 목록에 합칠 공지 — 내 것·숨긴 것 제외, 읽음 여부 반영.
    func visible(_ list: [AppNotification], myId: String) -> [AppNotification] {
        list.compactMap { n in
            guard n.actorId != myId, let id = n.id, !hidden.contains(id) else { return nil }
            var copy = n
            copy.read = n.createdAt <= seenAt
            return copy
        }
    }

    /// 알림 화면을 열면 지금까지의 공지를 모두 읽음으로.
    func markAllSeen() {
        seenAt = FirestoreService.nowMillis
        UserDefaults.standard.set(NSNumber(value: seenAt), forKey: Self.seenKey)
    }

    /// 스와이프 삭제 — 공용 문서라 지우지 않고 이 기기에서만 숨긴다.
    func hide(_ n: AppNotification) {
        guard let id = n.id else { return }
        hidden.insert(id)
        UserDefaults.standard.set(Array(hidden), forKey: Self.hiddenKey)
    }
}
