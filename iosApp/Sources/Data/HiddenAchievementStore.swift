import FirebaseFirestore
import Foundation

/// 히든 업적 선점(달성) 저장소 — `hiddenAchievements/{achievementId}`.
/// "앱 전체에서 단 한 사람만" 을 **Firestore 트랜잭션**으로 보장한다.
/// (Android `HiddenAchievementRepository` 패리티.)
@MainActor
final class HiddenAchievementStore: ObservableObject {
    @Published var claims: [String: HiddenClaim] = [:]
    @Published var loaded = false

    private var reg: ListenerRegistration?
    /// 세션 내 중복 선점(트랜잭션/알림) 방지.
    private var attempted: Set<String> = []

    func start() {
        guard reg == nil else { return }
        reg = FirestoreService.hiddenAchievements.addSnapshotListener { [weak self] snap, err in
            guard let self else { return }
            if err != nil { return } // 권한/네트워크 에러로 죽지 않게 무시
            var map: [String: HiddenClaim] = [:]
            for d in snap?.documents ?? [] {
                map[d.documentID] = HiddenClaim(
                    achieverId: d.get("achieverId") as? String ?? "",
                    achieverName: d.get("achieverName") as? String ?? "",
                    claimedAt: (d.get("claimedAt") as? Int64) ?? 0
                )
            }
            Task { @MainActor in
                self.claims = map
                self.loaded = true
            }
        }
    }

    func stop() {
        reg?.remove()
        reg = nil
    }

    func myIds(uid: String?) -> [String] {
        guard let uid, !uid.isEmpty else { return [] }
        return claims.filter { $0.value.achieverId == uid }.map { $0.key }
    }

    /// 원자적 선점. 아직 주인이 없으면 [uid] 로 기록하고 true(=내가 달성). 이미 주인이면 그게 나면 true.
    func claim(id: String, uid: String, name: String) async -> Bool {
        guard !id.isEmpty, !uid.isEmpty else { return false }
        let ref = FirestoreService.hiddenAchievements.document(id)
        return await withCheckedContinuation { cont in
            FirestoreService.db.runTransaction({ txn, errorPointer -> Any? in
                let snap: DocumentSnapshot
                do {
                    snap = try txn.getDocument(ref)
                } catch let err as NSError {
                    errorPointer?.pointee = err
                    return false
                }
                let owner = snap.get("achieverId") as? String ?? ""
                if owner.isEmpty {
                    txn.setData([
                        "achieverId": uid,
                        "achieverName": name,
                        "claimedAt": FirestoreService.nowMillis,
                    ], forDocument: ref)
                    return true
                } else {
                    return owner == uid
                }
            }, completion: { result, _ in
                cont.resume(returning: (result as? Bool) ?? false)
            })
        }
    }

    /// 자동 조건을 만족했지만 아직 아무도 선점하지 않은 히든 업적을 선점 시도.
    /// 이번에 **새로** 내가 달성한 업적들을 반환한다.
    func attemptAutoClaims(stats: UserStats, allNormalDone: Bool, uid: String, name: String) async -> [HiddenAchievement] {
        guard loaded, !uid.isEmpty else { return [] }
        let satisfied = HiddenAchievements.satisfiedAutoIds(HiddenContext(stats: stats, allNormalDone: allNormalDone))
        var won: [HiddenAchievement] = []
        for id in satisfied {
            if attempted.contains(id) { continue }       // 이번 세션에 이미 시도함
            if claims[id]?.claimed == true { continue }  // 이미 누군가(또는 내가) 달성함
            attempted.insert(id)                          // await 이전에 잠가 동시 호출 중복 방지
            if await claim(id: id, uid: uid, name: name), let ach = HiddenAchievements.byId(id) {
                won.append(ach)
            }
        }
        return won
    }
}
