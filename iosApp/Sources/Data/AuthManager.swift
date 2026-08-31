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
    /// "로그인 없이 둘러보기"(게스트) 모드 — Android `GoogleAuthHelper.currentUserId == null` 상태 대응.
    /// 익명 FirebaseAuth 세션은 규칙(request.auth != null) 통과용일 뿐 앱 사용자로 치지 않는다.
    @Published var isGuest = false

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.uid = Self.appUserId(of: user)
                if let user, !user.isAnonymous {
                    let appUid = Self.appUserId(of: user) ?? user.uid
                    // 캐시된 커스텀 닉네임이 있으면 즉시 반영(기본=구글 이름). Firestore 값으로 ensureProfile 에서 확정.
                    let cached = UserDefaults.standard.string(forKey: "nickname_\(appUid)")
                    self?.displayName = cached ?? user.displayName ?? user.email ?? "익명의 별"
                    await self?.ensureProfile(user)
                } else {
                    self?.displayName = ""
                }
            }
        }
    }

    /// 앱 데이터 식별자(userId) — **Google sub**(providerData google.com 의 uid).
    /// Android `GoogleAuthHelper` 가 userId = Google sub 를 쓰므로, 같은 구글 계정이 OS 와 무관하게
    /// 같은 계정으로 묶이려면 iOS 도 같은 값을 써야 한다(FirebaseAuth uid 는 프로젝트별 발급이라 별개 값).
    /// ⚠️ **익명 세션은 nil** — Android 는 둘러보기에서 userId 가 null 이고(작성/댓글/친구 잠금),
    ///    익명 로그인은 보안 규칙 통과용 세션일 뿐이다. iOS 도 같은 의미로 맞춘다.
    nonisolated static func appUserId(of user: User?) -> String? {
        guard let user, !user.isAnonymous else { return nil }
        if let sub = user.providerData.first(where: { $0.providerID == "google.com" })?.uid,
           !sub.isEmpty {
            return sub
        }
        return user.uid
    }

    var isSignedIn: Bool { uid != nil }

    /// 본 화면(지도)에 들어갈 수 있는가 — 로그인했거나 게스트(둘러보기)면 true.
    var canEnterApp: Bool { isSignedIn || isGuest }

    /// 로그인 없이 둘러보기 — **Android 와 동일하게 로그인 없이 바로 진입한다.**
    /// Firestore/Storage 규칙(`request.auth != null`)을 통과하려면 세션이 필요해 익명 로그인을 시도하지만
    /// (Android `StaryApplication` 과 동일한 best-effort), 콘솔에서 익명 인증이 꺼져 있어 실패해도 **진입을 막지 않는다**.
    /// (예전엔 실패 시 빨간 에러만 뜨고 화면이 넘어가지 않았다 — 2026-08-15 수정.)
    func browseAsGuest() async {
        isBusy = true
        errorMessage = nil
        if Auth.auth().currentUser == nil {
            _ = try? await Auth.auth().signInAnonymously()
        }
        isBusy = false
        isGuest = true
    }

    /// 게스트 → 로그인 화면으로 되돌리기(드로어의 "로그인"). Android 드로어 `drawer_login` 대응.
    func exitGuest() {
        isGuest = false
        try? Auth.auth().signOut()
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
        isGuest = false
        // ⚠️ 인증이 살아 있는 지금 이 기기 토큰을 떼어낸다 — 로그아웃 뒤엔 규칙에 막혀 못 지운다.
        //    네트워크가 느려도 로그아웃이 막히지 않게 짧게(1.5초) 기다린 뒤 진행한다.
        let uid = Self.appUserId(of: Auth.auth().currentUser)
        Task { @MainActor in
            if let uid {
                await withTaskGroup(of: Void.self) { group in
                    group.addTask { await PushManager.shared.clearToken(for: uid) }
                    group.addTask { try? await Task.sleep(nanoseconds: 1_500_000_000) }
                    _ = await group.next()
                    group.cancelAll()
                }
            }
            try? Auth.auth().signOut()
            GIDSignIn.sharedInstance.signOut()
        }
    }

    /// 계정 삭제 "예약"(soft) — 7일 유예. (Android `GoogleAuthHelper.requestDeletion` 패리티)
    /// 즉시 지우지 않고 users/{uid}.deletionRequestedAt 에 요청 시각을, authUid 에 FirebaseAuth uid 를 기록한 뒤 로그아웃한다.
    /// 유예 동안 다시 로그인하면 [cancelPendingDeletion] 으로 취소되고,
    /// 끝까지 로그인하지 않으면 서버 자정 스케줄 함수가 데이터/Storage/Auth 를 완전 삭제한다.
    /// (userId=Google sub ≠ FirebaseAuth uid 라, 서버가 Auth 계정을 지우려면 authUid 가 필요 — Android 동일.)
    func requestDeletion() async -> Bool {
        guard let user = Auth.auth().currentUser, let uid = Self.appUserId(of: user) else { return false }
        isBusy = true
        defer { isBusy = false }
        do {
            try await FirestoreService.users.document(uid).setData([
                "deletionRequestedAt": FirestoreService.nowMillis,
                "authUid": user.uid,
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

    /// users/{uid} 프로필 문서를 생성/갱신(없으면 만든다). uid = [appUserId](Google sub) 기준.
    private func ensureProfile(_ user: User) async {
        let appUid = Self.appUserId(of: user) ?? user.uid
        let ref = FirestoreService.users.document(appUid)
        // ⚠️ **"읽기 실패"와 "문서 없음"을 반드시 구분한다.**
        //    `try?` 로 뭉뚱그리면 네트워크가 잠깐 안 될 때 아래 setData 가 커스텀 닉네임/사진을
        //    **구글 기본값으로 덮어써** 버린다(다른 사람 화면에도 그 값이 그대로 나간다).
        //    Android `GoogleAuthHelper.signInWithGoogle` 과 동일한 방어.
        var readOk = true
        var saved: DocumentSnapshot?
        do { saved = try await ref.getDocument() } catch { readOk = false }
        // 이미 정해둔 닉네임(커스텀 포함)이 있으면 우선 — 구글 이름으로 덮어쓰지 않는다(재로그인 유지).
        let existing = saved?.get("userName") as? String
        let cached = UserDefaults.standard.string(forKey: "nickname_\(appUid)")
        let googleName = user.displayName ?? user.email ?? "익명의 별"
        let name: String
        if existing?.isEmpty == false {
            name = existing!
        } else if !readOk, cached?.isEmpty == false {
            // 읽기 실패 — 이 기기에 남아 있는 닉네임이 구글 기본값보다 정확하다.
            name = cached!
        } else {
            name = googleName
        }
        // 프로필 **사진**도 동일 — 앱에서 올린 사진이 있으면 그대로 둔다.
        // (예전엔 항상 구글 사진으로 덮어써서 재로그인마다 프로필 사진이 초기화됐다 — 2026-08-15 수정.)
        let existingPhoto = saved?.get("profileImageUrl") as? String
        let photo = (existingPhoto?.isEmpty == false) ? existingPhoto! : (user.photoURL?.absoluteString ?? "")
        displayName = name
        UserDefaults.standard.set(name, forKey: "nickname_\(appUid)")
        // 현재 값을 확실히 아는 경우에만 기록한다(읽기 실패 시엔 손대지 않고 다음 로그인에 다시 시도).
        if readOk {
            // Android upsertProfile 과 동일한 3필드(검색 가능하도록) + 서버 계정삭제용 authUid.
            let data: [String: Any] = [
                "userId": appUid,
                "userName": name,
                "profileImageUrl": photo,
                "authUid": user.uid,
            ]
            try? await ref.setData(data, merge: true)
        }
        // 로그인 → 삭제 예약이 있으면 취소(7일 유예 정책).
        await Self.cancelPendingDeletion(uid: appUid)
    }

    /// 닉네임 변경 — 표시명(@Published)·검색용 `users/{uid}.userName`·로컬 캐시를 함께 갱신.
    /// 기본값은 구글 닉네임이고 여기서 정한 값이 이후 우선한다. 빈 값은 무시. (Android setNickname 패리티)
    @discardableResult
    func setNickname(_ name: String) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let uid = uid, !trimmed.isEmpty else { return false }
        displayName = trimmed
        UserDefaults.standard.set(trimmed, forKey: "nickname_\(uid)")
        do {
            try await FirestoreService.users.document(uid).setData(["userName": trimmed], merge: true)
            return true
        } catch {
            return false
        }
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
