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

    static func comments(of diaryId: String) -> CollectionReference {
        diaries.document(diaryId).collection(AppConfig.Collections.comments)
    }

    static func likes(of diaryId: String) -> CollectionReference {
        diaries.document(diaryId).collection(AppConfig.Collections.likes)
    }

    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
