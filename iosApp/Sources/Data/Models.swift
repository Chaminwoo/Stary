import FirebaseFirestore
import Foundation

/// Firestore <-> 도메인 모델 (Swift Codable).
/// KMP `shared` commonMain 모델 + Android Firebase 리포지토리와 **필드명/컬렉션이 동일**해야
/// 양 플랫폼이 같은 DB(stary-db)를 공유할 수 있다.
/// createdAt 은 epoch millis(Int64).

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
    var createdAt: Int64 = 0
    var likeCount: Int = 0
    var commentCount: Int = 0
    var viewCount: Int = 0
    var starType: Int = 0
    var starColor: Int = 0
    var visibilityType: String = "public"

    enum CodingKeys: String, CodingKey {
        case id, userId, userName, isAnonymous, title, content, imageUrl
        case latitude, longitude, createdAt, likeCount, commentCount, viewCount
        case starType, starColor, visibilityType
    }

    var createdDate: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }
}

struct Comment: Identifiable, Codable {
    @DocumentID var id: String?
    var diaryId: String = ""
    var userId: String = ""
    var userName: String = ""
    var content: String = ""
    var createdAt: Int64 = 0
}

/// 인앱 알림. 컬렉션: notifications (최상위). 수신자 = diaryOwnerId.
/// ⚠️ 읽음 필드는 Kotlin `isRead` 직렬화 규칙상 Firestore 엔 `read` 로 저장된다 → Swift 도 `read`.
struct AppNotification: Identifiable, Codable {
    @DocumentID var id: String?
    var type: String = "LIKE"           // LIKE | COMMENT | FRIEND_POST
    var diaryId: String = ""
    var diaryTitle: String = ""
    var diaryOwnerId: String = ""       // 수신자
    var actorId: String = ""
    var actorName: String = ""
    var content: String = ""
    var createdAt: Int64 = 0
    var read: Bool = false

    var displayText: String {
        switch type {
        case "COMMENT": return "\(actorName)님이 댓글을 남겼어요"
        case "FRIEND_POST": return "\(actorName)님이 새 별을 남겼어요"
        default: return "\(actorName)님이 좋아요를 눌렀어요"
        }
    }
}

/// 수락된 친구. users/{uid}/friends/{friendUid} (문서 id = 상대 uid).
struct Friend: Identifiable, Codable {
    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
    var photoUrl: String = ""
    var createdAt: Int64 = 0
}

/// 친구 요청. friendRequests/{id} (pending 만).
struct FriendRequest: Identifiable, Codable {
    @DocumentID var id: String?
    var fromId: String = ""
    var fromName: String = ""
    var fromPhotoUrl: String = ""
    var toId: String = ""
    var toName: String = ""
    var createdAt: Int64 = 0
}

/// 공개 프로필. users/{uid}.
/// profileImageUrl/equippedTitle 은 문서에 없을 수 있어(Android 부분 upsert) Optional —
/// Firestore Codable 은 누락 비옵셔널 필드에서 throw 하므로 디코딩 실패 방지.
struct UserProfile: Identifiable, Codable {
    @DocumentID var id: String?
    var userId: String = ""
    var userName: String = ""
    var profileImageUrl: String? = nil
    var equippedTitle: String? = nil
}

/// 1:1 채팅 메시지. chats/{chatId}/messages/{id}.
struct ChatMessage: Identifiable, Codable {
    @DocumentID var id: String?
    var senderId: String = ""
    var senderName: String = ""
    var text: String = ""
    var createdAt: Int64 = 0
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
