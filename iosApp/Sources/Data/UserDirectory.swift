import FirebaseFirestore
import SwiftUI

/// 사용자 현재 프로필(이름/사진) 디렉터리 — Android `core.util.UserDirectory` 패리티.
///
/// 다이어리/댓글 문서에는 작성 당시 userName 이 스냅샷으로 박혀 있어, 이후 닉네임/프사를
/// 바꾸면 과거 글이 옛 이름으로 남는다. 화면에서는 항상 `users/{uid}` 의 **현재 값**을
/// 보여주기 위해 uid 별 실시간 리스너 1개를 붙여 캐시에 반영한다(뷰가 @Published 관찰).
@MainActor
final class UserDirectory: ObservableObject {
    static let shared = UserDirectory()

    struct Info {
        let name: String?
        let photoUrl: String?
    }

    @Published private(set) var cache: [String: Info] = [:]
    private var listening: Set<String> = []

    private init() {}

    /// uid 의 users/{uid} 문서를 실시간 구독(최초 1회만).
    func ensureWatching(_ userId: String) {
        guard !userId.isEmpty, !listening.contains(userId) else { return }
        listening.insert(userId)
        FirestoreService.users.document(userId).addSnapshotListener { [weak self] snap, _ in
            guard let snap, snap.exists else { return }
            let name = (snap.get("userName") as? String).flatMap { $0.isEmpty ? nil : $0 }
            let photo = (snap.get("profileImageUrl") as? String).flatMap { $0.isEmpty ? nil : $0 }
            Task { @MainActor in
                self?.cache[userId] = Info(name: name, photoUrl: photo)
            }
        }
    }

    /// 현재 이름. 아직 로드 전/없으면 폴백(문서 스냅샷 이름).
    func name(_ userId: String, fallback: String) -> String {
        cache[userId]?.name ?? fallback
    }

    /// 현재 프로필 사진 URL(없으면 nil).
    func photoUrl(_ userId: String) -> String? {
        cache[userId]?.photoUrl
    }

    /// 현재 프로필 사진 URL. 아직 로드 전/없으면 폴백(문서에 박힌 스냅샷 사진).
    func photoUrl(_ userId: String, fallback: String) -> String {
        cache[userId]?.photoUrl ?? fallback
    }
}

extension View {

    /// 이 뷰가 떠 있는 동안 [userId] 의 현재 프로필(users/{uid})을 구독한다.
    ///
    /// ⚠️ **타인의 이름/사진을 그리는 행은 예외 없이 이걸 붙이고 `UserDirectory` 값을 쓴다.**
    ///    문서에 박힌 스냅샷(diary.userName / notif.actorName / friend.photoUrl …)을 그대로 그리면
    ///    상대가 닉네임·프사를 바꾼 뒤에도 옛 값(대개 구글 기본값)이 남는다.
    func watchUser(_ userId: String) -> some View {
        // View 프로토콜 확장은 @MainActor 격리가 아니므로 명시적으로 메인 액터에서 실행한다.
        task(id: userId) {
            await MainActor.run { UserDirectory.shared.ensureWatching(userId) }
        }
    }
}
