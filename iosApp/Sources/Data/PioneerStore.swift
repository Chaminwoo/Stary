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
    /// 주 경계에서 `activeCountries` 를 다시 계산시키기 위한 틱(값 자체는 의미 없음) —
    /// Android `DiaryMap` 비콘 루프의 `delay(msUntilCountryChange)` 재평가와 같은 역할.
    @Published private var weekTick: Int = 0
    private var reg: ListenerRegistration?
    private var weekTask: Task<Void, Never>?

    private static var collection: CollectionReference { FirestoreService.db.collection(PioneerQuest.collection) }

    func start() {
        guard reg == nil else { return }
        reg = Self.collection.addSnapshotListener { [weak self] snap, _ in
            let map = snap?.documents.reduce(into: [String: String]()) { acc, d in
                acc[d.documentID] = d.get("userId") as? String ?? ""
            } ?? [:]
            Task { @MainActor in self?.claimedBy = map }
        }
        // 주 경계가 지나면 활성 대상국이 바뀌므로 남은 시간만큼 자고 일어나 재평가시킨다
        // (시계 어긋남 방어로 1분~1시간 캡 — Android 비콘 루프와 동일 규칙).
        weekTask = Task { [weak self] in
            while !Task.isCancelled {
                let waitMs = min(max(PioneerQuest.msUntilCountryChange(nowMs: FirestoreService.nowMillis), 60_000), 60 * 60 * 1000)
                try? await Task.sleep(nanoseconds: UInt64(waitMs) * 1_000_000)
                if Task.isCancelled { return }
                self?.weekTick &+= 1
            }
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
        weekTask?.cancel()
        weekTask = nil
    }

    /// 지도 비콘 대상 — **미개척 나라 중 이번 주 1개만**(과거 나라 누적 없음). 없으면 빈 배열.
    var activeCountries: [PioneerQuest.Country] {
        _ = weekTick // 주 경계 틱에 의존시켜 재계산되게 한다
        guard let c = PioneerQuest.activeCountry(
            nowMs: FirestoreService.nowMillis, claimedCodes: Set(claimedBy.keys)
        ) else { return [] }
        return [c]
    }

    /// 업로드 성공 직후 선점 시도 — 역지오코딩 → 활성 대상국 확인 → 트랜잭션 선점.
    /// 모든 실패는 조용히 무시(업로드 UX 영향 없음). 어드민 계정은 기록하지 않는다.
    static func attemptClaim(lat: Double, lng: Double, uid: String, name: String) async {
        guard lat != 0 || lng != 0 else { return }
        guard !AppConfig.isAdminEmail(Auth.auth().currentUser?.email) else { return }
        let location = CLLocation(latitude: lat, longitude: lng)
        guard let code = (try? await CLGeocoder().reverseGeocodeLocation(location).first)?
            .isoCountryCode?.uppercased() else { return }
        // 이번 주 활성 대상국(미개척 나라 중 이번 주 1개)과 일치하는지 1회 조회로 확인 — 비콘과 동일 기준.
        guard let snap = try? await collection.getDocuments() else { return }
        let claimed = Set(snap.documents.map { $0.documentID })
        let active = PioneerQuest.activeCountry(nowMs: FirestoreService.nowMillis, claimedCodes: claimed)
        guard active?.code == code else { return }
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
