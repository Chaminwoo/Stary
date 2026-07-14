import FirebaseAuth
import FirebaseFirestore
import Foundation

/// 히든 업적 선점(달성) 저장소 — `hiddenAchievements/{achievementId}`.
/// "앱 전체에서 단 한 사람만" 을 **Firestore 트랜잭션**으로 보장한다.
/// (Android `HiddenAchievementRepository` 패리티.)
@MainActor
final class HiddenAchievementStore: ObservableObject {
    /// 전역 공유 인스턴스 — 컬렉션이 작고(≤11 문서) 어디서든 필요하므로 리스너 1개로 공유한다.
    /// (Android `HiddenClaimStore` 승격 패턴 — 이름 옆 배지/업적 화면/프로필이 함께 쓴다.)
    static let shared = HiddenAchievementStore()

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

    /// [userId] 가 달성한 히든 업적들 — 정의 순서(안정적 표시 순서). 이름 옆 배지용.
    func achievements(of userId: String) -> [HiddenAchievement] {
        guard !userId.isEmpty else { return [] }
        let mine = Set(claims.filter { $0.value.achieverId == userId }.map { $0.key })
        guard !mine.isEmpty else { return [] }
        return HiddenAchievements.all.filter { mine.contains($0.id) }
    }

    /// [uid] 가 주인으로 기록된 히든 업적 선점을 서버에서 모두 제거해 슬롯을 되돌린다.
    /// 어드민(테스트) 계정이 과거에 실수로 선점한 히든 업적을 풀어 실제 유저가 첫 달성자가 되게 한다.
    /// (어드민 로그인 시에만 호출 — 일반 유저의 정당한 선점을 지우지 않도록 호출부에서 가드한다.)
    func releaseOwnedBy(uid: String) async {
        guard !uid.isEmpty else { return }
        let snap = try? await FirestoreService.hiddenAchievements
            .whereField("achieverId", isEqualTo: uid).getDocuments()
        for doc in snap?.documents ?? [] {
            try? await doc.reference.delete()
        }
    }

    /// 원자적 선점. 아직 주인이 없으면 [uid] 로 기록하고 true(=내가 달성). 이미 주인이면 그게 나면 true.
    func claim(id: String, uid: String, name: String) async -> Bool {
        guard !id.isEmpty, !uid.isEmpty else { return false }
        // 어드민(테스트) 계정은 선점을 서버에 기록하지 않는다(실제 유저가 첫 달성자가 될 수 있게).
        if AppConfig.isAdminEmail(Auth.auth().currentUser?.email) { return false }
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
