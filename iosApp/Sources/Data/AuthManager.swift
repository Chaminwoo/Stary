import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseStorage
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

    /// 계정 영구 삭제 — Firestore 데이터 + Storage 프로필 + Auth 사용자 제거 후 로그아웃.
    /// (Android `GoogleAuthHelper.deleteAccount` 패리티. iOS 는 uid = Auth uid = userId.)
    /// 최근 로그인 만료 시 구글 재인증 후 재시도. 성공하면 true.
    func deleteAccount() async -> Bool {
        guard let user = Auth.auth().currentUser else { return false }
        let uid = user.uid
        isBusy = true
        defer { isBusy = false }
        await Self.deleteUserData(uid: uid)
        // 프로필 이미지(베스트에포트)
        try? await Storage.storage().reference()
            .child("profile_images/\(uid).jpg").delete()
        do {
            try await user.delete()
        } catch {
            // 보통 최근 로그인 만료(requiresRecentLogin) — 구글 재인증 후 1회 재시도.
            // (SDK 버전별 AuthErrorCode 차이를 피하려 에러코드 비교 대신 무조건 재인증을 시도한다.)
            guard let credential = await reauthGoogleCredential() else {
                errorMessage = error.localizedDescription
                return false
            }
            do {
                try await user.reauthenticate(with: credential)
                try await user.delete()
            } catch {
                errorMessage = error.localizedDescription
                return false
            }
        }
        signOut()
        return true
    }

    /// 재인증용 구글 credential 획득(Firebase 로그인은 하지 않음).
    private func reauthGoogleCredential() async -> AuthCredential? {
        guard let rootVC = Self.rootViewController() else { return nil }
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }
        guard let result = try? await GIDSignIn.sharedInstance.signIn(withPresenting: rootVC),
              let idToken = result.user.idToken?.tokenString else { return nil }
        return GoogleAuthProvider.credential(withIDToken: idToken,
                                             accessToken: result.user.accessToken.tokenString)
    }

    /// 내 Firestore 데이터 정리(베스트에포트). 하위 컬렉션은 문서 삭제로 자동 정리되지 않아 직접 지운다.
    private static func deleteUserData(uid: String) async {
        let users = FirestoreService.users
        // 내가 쓴 다이어리
        if let snap = try? await FirestoreService.diaries.whereField("userId", isEqualTo: uid).getDocuments() {
            for d in snap.documents { try? await d.reference.delete() }
        }
        // 친구 양방향 정리(상대 친구목록에서 나 제거 + 내 친구목록)
        if let snap = try? await FirestoreService.friends(of: uid).getDocuments() {
            for f in snap.documents {
                try? await users.document(f.documentID).collection(AppConfig.Collections.friends).document(uid).delete()
                try? await f.reference.delete()
            }
        }
        // 열람/차단 하위 컬렉션
        for sub in [AppConfig.Collections.viewedDiaries, AppConfig.Collections.blocked] {
            if let snap = try? await users.document(uid).collection(sub).getDocuments() {
                for doc in snap.documents { try? await doc.reference.delete() }
            }
        }
        // 프로필 문서
        try? await users.document(uid).delete()
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
