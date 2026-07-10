import FirebaseAuth
import FirebaseFirestore
import Foundation

/// 친구 초대 보상(체크리스트 31) — Android `FirebaseInviteRepository` 패리티.
///
/// Firestore: invites/{redeemerUid} = { inviterId, redeemerId, createdAt }
///  - 문서 id = 초대받은 사람 uid → 계정당 평생 1회만 리딤(중복 자동 방지).
///  - 리딤 조건: 본인 링크 아님 + 가입 `AppConfig.inviteRedeemWindowMs` 이내.
///  - 업적: 초대한 쪽 = inviterId 문서 수(별의 등대/별무리의 길잡이), 받은 쪽 = 내 문서 존재(별의 인연).
enum InviteStore {
    /// 로그인 전에 딥링크가 들어온 경우 보관 → 로그인 후 RootView 가 재시도.
    static var pendingInviterId: String?

    /// stary://invite/{uid} 딥링크 수신 시 호출.
    static func handleDeepLink(inviterId: String) {
        guard !inviterId.isEmpty else { return }
        pendingInviterId = inviterId
        Task { await redeemPendingIfPossible(uid: Auth.auth().currentUser?.uid) }
    }

    /// 보관된 초대를 리딤(로그인 상태일 때만 소비). 실패는 조용히 무시.
    static func redeemPendingIfPossible(uid: String?) async {
        guard let uid, !uid.isEmpty, let inviter = pendingInviterId else { return }
        pendingInviterId = nil
        guard inviter != uid else { return }
        if let created = Auth.auth().currentUser?.metadata.creationDate,
           Date().timeIntervalSince(created) * 1000 > Double(AppConfig.inviteRedeemWindowMs) {
            return
        }
        let doc = FirestoreService.invites.document(uid)
        if let snap = try? await doc.getDocument(), snap.exists { return } // 이미 리딤
        try? await doc.setData([
            "inviterId": inviter,
            "redeemerId": uid,
            "createdAt": FirestoreService.nowMillis,
        ])
    }

    /// 업적 통계용 일회 조회 — (내가 초대해 가입시킨 수, 내가 리딤했는지).
    static func fetchStats(uid: String) async -> (invited: Int, redeemed: Bool) {
        let invited = (try? await FirestoreService.invites
            .whereField("inviterId", isEqualTo: uid).getDocuments())?.documents.count ?? 0
        let redeemed = ((try? await FirestoreService.invites.document(uid).getDocument())?.exists) ?? false
        return (invited, redeemed)
    }
}
