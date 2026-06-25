import FirebaseFirestore
import Foundation

/// Firestore <-> 도메인 모델 (Swift Codable).
/// KMP `shared` 의 commonMain 모델과 필드 의미가 동일하다.
/// createdAt 은 플랫폼 공용을 위해 epoch millis(Int64) 로 저장한다.

struct Diary: Identifiable, Codable, Hashable {
    // @DocumentID 의 Hashable/Equatable 합성에 의존하지 않도록 id 기반으로 직접 구현.
    static func == (lhs: Diary, rhs: Diary) -> Bool {
        lhs.id == rhs.id && lhs.createdAt == rhs.createdAt && lhs.viewCount == rhs.viewCount
    }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }

    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
    var isAnonymous: Bool = false
    var title: String = ""
    var content: String = ""
    var imageUrl: String = ""
    var latitude: Double = 0
    var longitude: Double = 0
    var createdAt: Int64 = 0          // epoch millis (UTC)
    var likeCount: Int = 0
    var commentCount: Int = 0
    var viewCount: Int = 0
    var starType: Int = 0             // 0..8
    var starColor: Int = 0            // 0..20
    var visibilityType: String = "public"   // public | friends | private

    enum CodingKeys: String, CodingKey {
        case id, userId, userName, isAnonymous, title, content, imageUrl
        case latitude, longitude, createdAt, likeCount, commentCount, viewCount
        case starType, starColor, visibilityType
    }

    var createdDate: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }
}

struct Comment: Identifiable, Codable {
    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
    var content: String = ""
    var createdAt: Int64 = 0
}

struct AppNotification: Identifiable, Codable {
    @DocumentID var id: String?
    var ownerId: String = ""
    var type: String = ""            // like | comment | friend | newDiary ...
    var actorName: String = ""
    var diaryId: String = ""
    var message: String = ""
    var read: Bool = false
    var createdAt: Int64 = 0
}

struct Friend: Identifiable, Codable {
    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
}

struct UserProfile: Identifiable, Codable {
    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
    var equippedTitle: String = ""
    var equippedStarType: Int = 0
    var equippedStarColor: Int = 0
}

enum Visibility: String, CaseIterable {
    case publicAll = "public"
    case friends = "friends"
    case privateOnly = "private"

    var label: String {
        switch self {
        case .publicAll: return "전체 공개"
        case .friends: return "친구만"
        case .privateOnly: return "나만 보기"
        }
    }
}
