import FirebaseFirestore
import Foundation

/// named DB(stary-db) 접근을 한 곳으로 모은다.
/// 모든 Repository 는 `FirestoreService.db` 로 같은 인스턴스를 공유한다.
enum FirestoreService {
    /// (default) 가 아닌 named DB 를 명시 지정.
    static let db: Firestore = Firestore.firestore(database: AppConfig.firestoreDbId)

    static var diaries: CollectionReference { db.collection(AppConfig.Collections.diaries) }
    static var users: CollectionReference { db.collection(AppConfig.Collections.users) }
    static var notifications: CollectionReference { db.collection(AppConfig.Collections.notifications) }

    static var friendRequests: CollectionReference { db.collection(AppConfig.Collections.friendRequests) }
    static var reports: CollectionReference { db.collection(AppConfig.Collections.reports) }
    static var chats: CollectionReference { db.collection(AppConfig.Collections.chats) }
    static var hiddenAchievements: CollectionReference { db.collection(AppConfig.Collections.hiddenAchievements) }
    static var invites: CollectionReference { db.collection(AppConfig.Collections.invites) }

    static func comments(of diaryId: String) -> CollectionReference {
        diaries.safeDocument(diaryId).collection(AppConfig.Collections.comments)
    }

    static func likes(of diaryId: String) -> CollectionReference {
        diaries.safeDocument(diaryId).collection(AppConfig.Collections.likes)
    }

    static func friends(of userId: String) -> CollectionReference {
        users.safeDocument(userId).collection(AppConfig.Collections.friends)
    }

    static func viewedDiaries(of userId: String) -> CollectionReference {
        users.safeDocument(userId).collection(AppConfig.Collections.viewedDiaries)
    }

    static func blocked(of userId: String) -> CollectionReference {
        users.safeDocument(userId).collection(AppConfig.Collections.blocked)
    }

    /// users/{uid}/fcmTokens — 기기별 푸시 토큰(문서 id = 토큰).
    static func fcmTokens(of uid: String) -> CollectionReference {
        users.safeDocument(uid).collection(AppConfig.Collections.fcmTokens)
    }

    static func messages(of chatId: String) -> CollectionReference {
        chats.safeDocument(chatId).collection(AppConfig.Collections.messages)
    }

    /// users/{uid}/chatHidden — 나에게서만 지운 채팅(문서 id = chatId). Android FirebaseChatRepository.hiddenRef 와 같은 경로.
    static func chatHidden(of uid: String) -> CollectionReference {
        users.safeDocument(uid).collection(AppConfig.Collections.chatHidden)
    }

    /// 외부(문서 필드·푸시·딥링크)에서 온 id 가 `document(_:)` 에 넣어도 안전한가.
    /// Firestore 는 빈 문자열이나 `/` 가 든 경로로 문서를 만들면 **크래시**한다 — 외부 값은 반드시 이걸로 거른 뒤 쓴다.
    nonisolated static func isValidId(_ id: String) -> Bool {
        !id.isEmpty && !id.contains("/") && id != "." && id != ".." && id.utf8.count <= 1500
    }

    /// 친구 요청 문서 id — 보낸 사람·받는 사람 쌍마다 하나("{from}_{to}"). 중복 요청이 같은 문서로 합쳐진다.
    static func friendRequestId(from: String, to: String) -> String { "\(from)_\(to)" }

    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

extension CollectionReference {
    /// `document(_:)` 의 크래시 방어판 — 빈 문자열·`/` 가 든 id 는 존재하지 않는 자리표시 id 로 바꿔
    /// 앱이 죽는 대신 "문서 없음/권한 거부"로 처리되게 한다. 외부(문서 필드·푸시·링크)에서 온 id 는 전부 이걸로 연다.
    func safeDocument(_ id: String) -> DocumentReference {
        document(FirestoreService.isValidId(id) ? id : "_invalid_id")
    }
}
