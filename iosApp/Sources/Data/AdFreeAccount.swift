import FirebaseFirestore
import Foundation

/// 광고제거 계정(2026-10-02) — `adFree/{appUserId}` 문서가 **있으면** 100m 밖 게시물을 광고 없이 탭 한 번에 연다
/// (DetailScreen `lockedContentCard`). 앱 어디에도 "광고제거 계정"이라고 표시하지 않는다(사용자 결정).
/// Android `core/ads/AdFreeAccount.kt` 패리티.
///
/// - 부여: Firebase Console → Firestore(stary-db) → `adFree` 컬렉션에 문서 id = 대상 appUserId 로 문서 생성.
///   지우면 즉시 해제된다(스냅샷 리스너).
/// - 규칙: 본인만 읽기, 클라이언트 쓰기 금지 → 사용자가 스스로 켤 수 없다.
/// - 읽기 실패(규칙 미배포/오프라인 등)는 **꺼짐**으로 본다(fail closed).
@MainActor
final class AdFreeAccount: ObservableObject {
    static let shared = AdFreeAccount()

    @Published private(set) var active = false

    private var watchingUid: String?
    private var reg: ListenerRegistration?

    /// 로그인/로그아웃/계정 전환마다 호출(RootView). 같은 uid 면 아무것도 하지 않는다.
    func watch(uid: String?) {
        guard uid != watchingUid else { return }
        reg?.remove()
        reg = nil
        watchingUid = uid
        active = false
        guard let uid, !uid.isEmpty else { return }
        reg = FirestoreService.db.collection(AppConfig.Collections.adFree).document(uid)
            .addSnapshotListener { [weak self] snap, _ in
                self?.active = snap?.exists == true
            }
    }
}
