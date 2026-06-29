import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn
import SwiftUI

/// 로그인 상태 단일 소스.
/// - 익명 로그인(빠른 시작) + 구글 로그인(Firebase Auth credential) 지원.
/// - users/{uid} 프로필 문서를 보장한다.
@MainActor
final class AuthManager: ObservableObject {
    @Published var uid: String?
    @Published var displayName: String = ""
    @Published var isBusy = false
    @Published var errorMessage: String?

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.uid = user?.uid
                self?.displayName = user?.displayName ?? user?.email ?? "익명의 별"
                if let user { await self?.ensureProfile(user) }
            }
        }
    }

    var isSignedIn: Bool { uid != nil }

    /// 익명 로그인 — 온보딩 없이 둘러보기.
    func signInAnonymously() async {
        await run {
            try await Auth.auth().signInAnonymously()
        }
    }

    /// 구글 로그인 → Firebase Auth credential 교환.
    func signInWithGoogle() async {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            errorMessage = "Firebase clientID 없음 (GoogleService-Info.plist 확인)"
            return
        }
        guard let rootVC = Self.rootViewController() else {
            errorMessage = "표시할 화면을 찾지 못했어요."
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        await run {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: rootVC)
            guard let idToken = result.user.idToken?.tokenString else {
                throw AuthError.missingToken
            }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            try await Auth.auth().signIn(with: credential)
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }

    /// 계정 삭제 "예약"(soft) — 7일 유예. (Android `GoogleAuthHelper.requestDeletion` 패리티)
    /// 즉시 지우지 않고 users/{uid}.deletionRequestedAt 에 요청 시각을, authUid 에 uid 를 기록한 뒤 로그아웃한다.
    /// 유예 동안 다시 로그인하면 [cancelPendingDeletion] 으로 취소되고,
    /// 끝까지 로그인하지 않으면 서버 자정 스케줄 함수가 데이터/Storage/Auth 를 완전 삭제한다.
    /// (iOS 는 uid = FirebaseAuth uid = userId 라 authUid 도 uid 와 같다.)
    func requestDeletion() async -> Bool {
        guard let uid = Auth.auth().currentUser?.uid else { return false }
        isBusy = true
        defer { isBusy = false }
        do {
            try await FirestoreService.users.document(uid).setData([
                "deletionRequestedAt": FirestoreService.nowMillis,
                "authUid": uid,
            ], merge: true)
            signOut()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    /// 삭제 예약 취소 — 유예 기간 내 재로그인 시 호출(fire-and-forget).
    static func cancelPendingDeletion(uid: String) async {
        guard !uid.isEmpty else { return }
        try? await FirestoreService.users.document(uid).updateData([
            "deletionRequestedAt": FieldValue.delete()
        ])
    }

    // MARK: - Helpers

    private func run(_ block: @escaping () async throws -> Void) async {
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        do { try await block() }
        catch { errorMessage = error.localizedDescription }
    }

    /// users/{uid} 프로필 문서를 생성/갱신(없으면 만든다).
    private func ensureProfile(_ user: User) async {
        let ref = FirestoreService.users.document(user.uid)
        let name = user.displayName ?? user.email ?? "익명의 별"
        // Android upsertProfile 과 동일한 3필드(검색 가능하도록).
        let data: [String: Any] = [
            "userId": user.uid,
            "userName": name,
            "profileImageUrl": user.photoURL?.absoluteString ?? ""
        ]
        try? await ref.setData(data, merge: true)
        // 로그인 → 삭제 예약이 있으면 취소(7일 유예 정책).
        await Self.cancelPendingDeletion(uid: user.uid)
    }

    static func rootViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return scene?.keyWindow?.rootViewController
    }

    enum AuthError: LocalizedError {
        case missingToken
        var errorDescription: String? { "구글 토큰을 받지 못했어요." }
    }
}
