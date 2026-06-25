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
        let data: [String: Any] = [
            "userId": user.uid,
            "userName": name
        ]
        try? await ref.setData(data, merge: true)
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
