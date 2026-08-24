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
        diaries.document(diaryId).collection(AppConfig.Collections.comments)
    }

    static func likes(of diaryId: String) -> CollectionReference {
        diaries.document(diaryId).collection(AppConfig.Collections.likes)
    }

    static func friends(of userId: String) -> CollectionReference {
        users.document(userId).collection(AppConfig.Collections.friends)
    }

    static func viewedDiaries(of userId: String) -> CollectionReference {
        users.document(userId).collection(AppConfig.Collections.viewedDiaries)
    }

    static func blocked(of userId: String) -> CollectionReference {
        users.document(userId).collection(AppConfig.Collections.blocked)
    }

    /// users/{uid}/fcmTokens — 기기별 푸시 토큰(문서 id = 토큰).
    static func fcmTokens(of uid: String) -> CollectionReference {
        users.document(uid).collection(AppConfig.Collections.fcmTokens)
    }

    static func messages(of chatId: String) -> CollectionReference {
        chats.document(chatId).collection(AppConfig.Collections.messages)
    }

    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
