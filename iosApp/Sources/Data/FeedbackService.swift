import FirebaseFirestore
import Foundation

/// 앱 안 "고칠 점 보내기" — 최상위 `feedback/{자동 id}` 에 한 건씩 쓴다(Android `FeedbackRepository` 와 같은 스키마).
/// 필드: uid(appUserId — 규칙의 isMe 가 검사) · message · platform("ios") · appVersion · locale · createdAt(서버 시각).
/// 이름·이메일은 넣지 않고 users/{uid} 도 건드리지 않는다. 읽기/수정/삭제는 규칙상 불가(콘솔에서만 본다).
enum FeedbackService {
    /// 메시지 최대 길이 — firestore.rules 의 size() <= 2000, Android FeedbackRepository.MAX_LENGTH 와 같은 값.
    static let maxLength = 2000
    /// 오프라인이면 쓰기 완료 신호가 오지 않아 끝없이 기다리게 되므로 이 시간 뒤 실패로 본다.
    private static let timeoutSeconds: Double = 15

    /// 한 번만 결과를 돌려주기 위한 잠금(완료 콜백과 시간 초과 중 먼저 온 쪽만 이긴다).
    private final class Once: @unchecked Sendable {
        private let lock = NSLock()
        private var done = false
        func claim() -> Bool {
            lock.lock(); defer { lock.unlock() }
            if done { return false }
            done = true
            return true
        }
    }

    /// 성공하면 true. 네트워크/권한/시간 초과면 false(호출한 쪽이 입력을 보존한 채 오류를 알린다).
    static func send(uid: String, message: String) async -> Bool {
        let text = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !uid.isEmpty, !text.isEmpty, text.count <= maxLength else { return false }
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        let language = await MainActor.run { LocaleManager.shared.effectiveLanguage }
        let data: [String: Any] = [
            "uid": uid,
            "message": text,
            "platform": "ios",
            "appVersion": "\(version) (\(build))",
            "locale": language,
            "createdAt": FieldValue.serverTimestamp(),
        ]
        return await withCheckedContinuation { (cont: CheckedContinuation<Bool, Never>) in
            let once = Once()
            FirestoreService.db.collection(AppConfig.Collections.feedback).addDocument(data: data) { error in
                if once.claim() { cont.resume(returning: error == nil) }
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + timeoutSeconds) {
                if once.claim() { cont.resume(returning: false) }
            }
        }
    }
}
