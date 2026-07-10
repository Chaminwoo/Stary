import CoreLocation
import FirebaseAuth
import FirebaseFirestore
import Foundation

/// 주간 개척 퀘스트(체크리스트 32) — Android `FirebasePioneerRepository`+`PioneerClaimHelper` 패리티.
/// pioneerClaims/{countryCode} = { userId, userName, weekIndex, createdAt } — 전 세계 1명(선착순).
@MainActor
final class PioneerStore: ObservableObject {
    /// countryCode → 개척자 userId. (지도 비콘/업적 화면이 구독)
    @Published var claimedBy: [String: String] = [:]
    private var reg: ListenerRegistration?

    private static var collection: CollectionReference { FirestoreService.db.collection(PioneerQuest.collection) }

    func start() {
        guard reg == nil else { return }
        reg = Self.collection.addSnapshotListener { [weak self] snap, _ in
            let map = snap?.documents.reduce(into: [String: String]()) { acc, d in
                acc[d.documentID] = d.get("userId") as? String ?? ""
            } ?? [:]
            Task { @MainActor in self?.claimedBy = map }
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
    }

    /// 아직 개척되지 않은 대상국(지도 비콘 대상).
    var featured: [PioneerQuest.Country] {
        PioneerQuest.featuredCountries(nowMs: FirestoreService.nowMillis, claimedCodes: Set(claimedBy.keys))
    }

    /// 업로드 성공 직후 선점 시도 — 역지오코딩 → 활성 대상국 확인 → 트랜잭션 선점.
    /// 모든 실패는 조용히 무시(업로드 UX 영향 없음). 어드민 계정은 기록하지 않는다.
    static func attemptClaim(lat: Double, lng: Double, uid: String, name: String) async {
        guard lat != 0 || lng != 0 else { return }
        guard !AppConfig.isAdminEmail(Auth.auth().currentUser?.email) else { return }
        let location = CLLocation(latitude: lat, longitude: lng)
        guard let code = (try? await CLGeocoder().reverseGeocodeLocation(location).first)?
            .isoCountryCode?.uppercased() else { return }
        // 활성 대상국(이번 주까지 등장 + 미개척)인지 1회 조회로 확인
        guard let snap = try? await collection.getDocuments() else { return }
        let claimed = Set(snap.documents.map { $0.documentID })
        let featured = PioneerQuest.featuredCountries(nowMs: FirestoreService.nowMillis, claimedCodes: claimed)
        guard featured.contains(where: { $0.code == code }) else { return }
        // 트랜잭션 선점 — 이미 주인이 있으면 아무 것도 하지 않는다.
        let ref = collection.document(code)
        _ = try? await FirestoreService.db.runTransaction { tx, _ in
            let doc = try? tx.getDocument(ref)
            let owner = doc?.get("userId") as? String
            if owner == nil || owner?.isEmpty == true {
                tx.setData([
                    "userId": uid,
                    "userName": name,
                    "weekIndex": PioneerQuest.weekIndex(nowMs: FirestoreService.nowMillis),
                    "createdAt": FirestoreService.nowMillis,
                ], forDocument: ref)
            }
            return nil
        }
    }
}
