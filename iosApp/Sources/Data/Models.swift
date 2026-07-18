import FirebaseFirestore
import Foundation

/// Firestore <-> 도메인 모델 (Swift Codable).
/// KMP `shared` commonMain 모델 + Android Firebase 리포지토리와 필드명/컬렉션이 동일해야
/// 양 플랫폼이 같은 DB(stary-db)를 공유할 수 있다.
/// createdAt 은 epoch millis(Int64).
struct Diary: Identifiable, Codable, Hashable {

    @DocumentID var id: String?

    var userId: String = ""
    var userName: String = ""

    /// (사용 안 함) 익명 게시 기능은 제거됨. 옛 문서 디코딩 하위호환 + Android 모델 패리티용으로만 남겨둠 — 항상 false.
    /// Firestore에서는 Android와 동일하게 `anonymous: 0 또는 1`로 저장.
    var isAnonymous: Bool = false

    var title: String = ""
    var content: String = ""
    var imageUrl: String = ""

    /// 3초 이내 짧은 영상 URL(Storage).
    /// 비어 있으면 영상 없음 — imageUrl과 배타적.
    var videoUrl: String = ""

    var latitude: Double = 0
    var longitude: Double = 0

    var createdAt: Int64 = 0

    var likeCount: Int = 0
    var commentCount: Int = 0
    var viewCount: Int = 0

    var starType: Int = 0
    var starColor: Int = 0

    var visibilityType: String = "public"

    // MARK: - 기본 생성자

    init(
        id: String? = nil,
        userId: String = "",
        userName: String = "",
        isAnonymous: Bool = false,
        title: String = "",
        content: String = "",
        imageUrl: String = "",
        videoUrl: String = "",
        latitude: Double = 0,
        longitude: Double = 0,
        createdAt: Int64 = 0,
        likeCount: Int = 0,
        commentCount: Int = 0,
        viewCount: Int = 0,
        starType: Int = 0,
        starColor: Int = 0,
        visibilityType: String = "public"
    ) {
        self.id = id
        self.userId = userId
        self.userName = userName
        self.isAnonymous = isAnonymous
        self.title = title
        self.content = content
        self.imageUrl = imageUrl
        self.videoUrl = videoUrl
        self.latitude = latitude
        self.longitude = longitude
        self.createdAt = createdAt
        self.likeCount = likeCount
        self.commentCount = commentCount
        self.viewCount = viewCount
        self.starType = starType
        self.starColor = starColor
        self.visibilityType = visibilityType
    }

    // MARK: - Firestore 키

    enum CodingKeys: String, CodingKey {
        case id
        case userId
        case userName

        // Android Firestore 필드명
        case anonymous

        case title
        case content
        case imageUrl
        case videoUrl

        case latitude
        case longitude

        case createdAt

        case likeCount
        case commentCount
        case viewCount

        case starType
        case starColor

        case visibilityType
    }

    // MARK: - Firestore → Swift

    init(from decoder: Decoder) throws {

        let c = try decoder.container(keyedBy: CodingKeys.self)

        // ⚠️ Android/iOS 가 같은 DB(stary-db)를 공유하므로 필드 타입이 문서마다 미묘하게
        // 다를 수 있다(Long/Int/Double/Timestamp/문자열 등). 한 필드라도 타입이 어긋나면
        // decodeIfPresent 가 throw 되어 문서 전체가 누락되므로, 모든 필드를 방어적으로 디코딩한다.

        /// 문자열(숫자로 저장돼 있어도 문자열화).
        func str(_ k: CodingKeys) -> String? {
            if let v = try? c.decodeIfPresent(String.self, forKey: k) { return v }
            if let v = try? c.decodeIfPresent(Int64.self, forKey: k) { return String(v) }
            if let v = try? c.decodeIfPresent(Double.self, forKey: k) { return String(v) }
            return nil
        }
        /// 정수(Int/Int64/Double/문자열 혼용 허용).
        func int(_ k: CodingKeys) -> Int? {
            if let v = try? c.decodeIfPresent(Int.self, forKey: k) { return v }
            if let v = try? c.decodeIfPresent(Int64.self, forKey: k) { return Int(v) }
            if let v = try? c.decodeIfPresent(Double.self, forKey: k) { return Int(v) }
            if let v = try? c.decodeIfPresent(String.self, forKey: k) { return Int(v) }
            return nil
        }
        /// 실수(Double/Int 혼용 허용).
        func dbl(_ k: CodingKeys) -> Double? {
            if let v = try? c.decodeIfPresent(Double.self, forKey: k) { return v }
            if let v = try? c.decodeIfPresent(Int64.self, forKey: k) { return Double(v) }
            if let v = try? c.decodeIfPresent(String.self, forKey: k) { return Double(v) }
            return nil
        }
        /// epoch millis(Int64) — Long/Double 또는 Firestore Timestamp 모두 허용.
        func millis(_ k: CodingKeys) -> Int64? {
            if let v = try? c.decodeIfPresent(Int64.self, forKey: k) { return v }
            if let v = try? c.decodeIfPresent(Double.self, forKey: k) { return Int64(v) }
            if let ts = try? c.decodeIfPresent(Timestamp.self, forKey: k) {
                return Int64(ts.seconds) * 1000 + Int64(ts.nanoseconds) / 1_000_000
            }
            return nil
        }

        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)

        userId = str(.userId) ?? ""
        userName = str(.userName) ?? ""

        // anonymous: 0/1(Int) 또는 true/false(Bool) 모두 호환.
        if let v = try? c.decodeIfPresent(Int.self, forKey: .anonymous) {
            isAnonymous = v != 0
        } else if let v = try? c.decodeIfPresent(Bool.self, forKey: .anonymous) {
            isAnonymous = v
        } else {
            isAnonymous = false
        }

        title = str(.title) ?? ""
        content = str(.content) ?? ""
        imageUrl = str(.imageUrl) ?? ""
        videoUrl = str(.videoUrl) ?? ""

        latitude = dbl(.latitude) ?? 0
        longitude = dbl(.longitude) ?? 0

        createdAt = millis(.createdAt) ?? 0

        likeCount = int(.likeCount) ?? 0
        commentCount = int(.commentCount) ?? 0
        viewCount = int(.viewCount) ?? 0

        starType = int(.starType) ?? 0
        starColor = int(.starColor) ?? 0

        visibilityType = str(.visibilityType) ?? "public"
    }

    // MARK: - Swift → Firestore

    func encode(to encoder: Encoder) throws {

        var container = encoder.container(
            keyedBy: CodingKeys.self
        )

        /*
         @DocumentID는 Firestore 문서 ID로 관리되므로
         문서 내부 필드에는 저장하지 않는다.
         */

        try container.encode(
            userId,
            forKey: .userId
        )

        try container.encode(
            userName,
            forKey: .userName
        )

        // Android와 동일한 형식:
        // false → 0
        // true → 1
        try container.encode(
            isAnonymous ? 1 : 0,
            forKey: .anonymous
        )

        try container.encode(
            title,
            forKey: .title
        )

        try container.encode(
            content,
            forKey: .content
        )

        try container.encode(
            imageUrl,
            forKey: .imageUrl
        )

        try container.encode(
            videoUrl,
            forKey: .videoUrl
        )

        try container.encode(
            latitude,
            forKey: .latitude
        )

        try container.encode(
            longitude,
            forKey: .longitude
        )

        try container.encode(
            createdAt,
            forKey: .createdAt
        )

        try container.encode(
            likeCount,
            forKey: .likeCount
        )

        try container.encode(
            commentCount,
            forKey: .commentCount
        )

        try container.encode(
            viewCount,
            forKey: .viewCount
        )

        try container.encode(
            starType,
            forKey: .starType
        )

        try container.encode(
            starColor,
            forKey: .starColor
        )

        try container.encode(
            visibilityType,
            forKey: .visibilityType
        )
    }

    // MARK: - Hashable / Equatable

    static func == (
        lhs: Diary,
        rhs: Diary
    ) -> Bool {
        lhs.id == rhs.id &&
        lhs.createdAt == rhs.createdAt &&
        lhs.viewCount == rhs.viewCount
    }

    func hash(
        into hasher: inout Hasher
    ) {
        hasher.combine(id)
    }

    // MARK: - 날짜

    var createdDate: Date {
        Date(
            timeIntervalSince1970:
                Double(createdAt) / 1000
        )
    }
}


// MARK: - 댓글

struct Comment: Identifiable, Codable {

    @DocumentID var id: String?

    var diaryId: String = ""
    var userId: String = ""
    var userName: String = ""
    var content: String = ""
    var createdAt: Int64 = 0

    enum CodingKeys: String, CodingKey { case id, diaryId, userId, userName, content, createdAt }

    init(id: String? = nil, diaryId: String = "", userId: String = "",
         userName: String = "", content: String = "", createdAt: Int64 = 0) {
        self.id = id; self.diaryId = diaryId; self.userId = userId
        self.userName = userName; self.content = content; self.createdAt = createdAt
    }

    // 타입 드리프트(문자열/숫자/Timestamp)로 문서가 통째로 누락되지 않게 방어 디코딩(Diary 와 동일 정책).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        diaryId = c.flexString(.diaryId) ?? ""
        userId = c.flexString(.userId) ?? ""
        userName = c.flexString(.userName) ?? ""
        content = c.flexString(.content) ?? ""
        createdAt = c.flexMillis(.createdAt) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(diaryId, forKey: .diaryId)
        try c.encode(userId, forKey: .userId)
        try c.encode(userName, forKey: .userName)
        try c.encode(content, forKey: .content)
        try c.encode(createdAt, forKey: .createdAt)
    }
}


// MARK: - 인앱 알림

/// 컬렉션: notifications (최상위).
/// 수신자 = diaryOwnerId.
///
/// Kotlin `isRead` 직렬화 규칙상
/// Firestore에는 `read`로 저장된다.
struct AppNotification: Identifiable, Codable {

    @DocumentID var id: String?

    // LIKE | COMMENT | FRIEND_POST
    var type: String = "LIKE"

    var diaryId: String = ""
    var diaryTitle: String = ""

    // 알림 수신자
    var diaryOwnerId: String = ""

    var actorId: String = ""
    var actorName: String = ""

    var content: String = ""

    var createdAt: Int64 = 0

    var read: Bool = false

    enum CodingKeys: String, CodingKey {
        case id, type, diaryId, diaryTitle, diaryOwnerId, actorId, actorName, content, createdAt, read
    }

    init(id: String? = nil, type: String = "LIKE", diaryId: String = "", diaryTitle: String = "",
         diaryOwnerId: String = "", actorId: String = "", actorName: String = "",
         content: String = "", createdAt: Int64 = 0, read: Bool = false) {
        self.id = id; self.type = type; self.diaryId = diaryId; self.diaryTitle = diaryTitle
        self.diaryOwnerId = diaryOwnerId; self.actorId = actorId; self.actorName = actorName
        self.content = content; self.createdAt = createdAt; self.read = read
    }

    // 타입 드리프트로 문서가 통째로 누락되지 않게 방어 디코딩(read 는 Bool/0·1 모두 허용).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        type = c.flexString(.type) ?? "LIKE"
        diaryId = c.flexString(.diaryId) ?? ""
        diaryTitle = c.flexString(.diaryTitle) ?? ""
        diaryOwnerId = c.flexString(.diaryOwnerId) ?? ""
        actorId = c.flexString(.actorId) ?? ""
        actorName = c.flexString(.actorName) ?? ""
        content = c.flexString(.content) ?? ""
        createdAt = c.flexMillis(.createdAt) ?? 0
        if let b = try? c.decodeIfPresent(Bool.self, forKey: .read) {
            read = b
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .read) {
            read = i != 0
        } else {
            read = false
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(type, forKey: .type)
        try c.encode(diaryId, forKey: .diaryId)
        try c.encode(diaryTitle, forKey: .diaryTitle)
        try c.encode(diaryOwnerId, forKey: .diaryOwnerId)
        try c.encode(actorId, forKey: .actorId)
        try c.encode(actorName, forKey: .actorName)
        try c.encode(content, forKey: .content)
        try c.encode(createdAt, forKey: .createdAt)
        try c.encode(read, forKey: .read)
    }

    /// 알림 행 문구(현재 언어) — Android notif_like/notif_comment/notif_friend_post(%@ = 다이어리 제목) 대응.
    @MainActor var displayText: String {
        switch type {
        case "COMMENT":
            return String(format: LocaleManager.shared.t(.notifCommentRow), diaryTitle)
        case "FRIEND_POST":
            return String(format: LocaleManager.shared.t(.notifFriendPostRow), diaryTitle)
        default:
            return String(format: LocaleManager.shared.t(.notifLikeRow), diaryTitle)
        }
    }
}


// MARK: - 친구

/// 수락된 친구.
/// users/{uid}/friends/{friendUid}
///
/// 문서 ID = 상대 UID.
struct Friend: Identifiable, Codable {

    @DocumentID var id: String?

    var userId: String = ""
    var userName: String = ""
    var photoUrl: String = ""

    var createdAt: Int64 = 0

    enum CodingKeys: String, CodingKey { case id, userId, userName, photoUrl, profileImageUrl, createdAt }

    init(id: String? = nil, userId: String = "", userName: String = "", photoUrl: String = "", createdAt: Int64 = 0) {
        self.id = id; self.userId = userId; self.userName = userName; self.photoUrl = photoUrl; self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        userId = c.flexString(.userId) ?? ""
        userName = c.flexString(.userName) ?? ""
        // Android 가 photoUrl / profileImageUrl 중 무엇으로 저장해도 받는다.
        photoUrl = c.flexString(.photoUrl) ?? c.flexString(.profileImageUrl) ?? ""
        createdAt = c.flexMillis(.createdAt) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(userId, forKey: .userId)
        try c.encode(userName, forKey: .userName)
        try c.encode(photoUrl, forKey: .photoUrl)
        try c.encode(createdAt, forKey: .createdAt)
    }
}


// MARK: - 친구 요청

/// friendRequests/{id}
///
/// pending 요청만 저장.
struct FriendRequest: Identifiable, Codable {

    @DocumentID var id: String?

    var fromId: String = ""
    var fromName: String = ""
    var fromPhotoUrl: String = ""

    var toId: String = ""
    var toName: String = ""

    var createdAt: Int64 = 0

    enum CodingKeys: String, CodingKey {
        case id, fromId, fromName, fromPhotoUrl, toId, toName, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        fromId = c.flexString(.fromId) ?? ""
        fromName = c.flexString(.fromName) ?? ""
        fromPhotoUrl = c.flexString(.fromPhotoUrl) ?? ""
        toId = c.flexString(.toId) ?? ""
        toName = c.flexString(.toName) ?? ""
        createdAt = c.flexMillis(.createdAt) ?? 0
    }
}


// MARK: - 사용자 프로필

/// 공개 프로필.
/// users/{uid}.
///
/// profileImageUrl 및 equippedTitle은
/// Android 부분 upsert 때문에 문서에 없을 수 있다.
struct UserProfile: Identifiable, Codable {

    @DocumentID var id: String?

    var userId: String = ""
    var userName: String = ""

    var profileImageUrl: String? = nil
    var equippedTitle: String? = nil

    enum CodingKeys: String, CodingKey { case id, userId, userName, profileImageUrl, equippedTitle }

    init(id: String? = nil, userId: String = "", userName: String = "",
         profileImageUrl: String? = nil, equippedTitle: String? = nil) {
        self.id = id; self.userId = userId; self.userName = userName
        self.profileImageUrl = profileImageUrl; self.equippedTitle = equippedTitle
    }

    // userId/userName 이 없는 옛 문서도 검색 결과에서 누락되지 않게 방어 디코딩.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        userId = c.flexString(.userId) ?? ""
        userName = c.flexString(.userName) ?? ""
        profileImageUrl = c.flexString(.profileImageUrl)
        equippedTitle = c.flexString(.equippedTitle)
    }
}


// MARK: - 채팅 메시지

/// chats/{chatId}/messages/{id}
struct ChatMessage: Identifiable, Codable {

    @DocumentID var id: String?

    var senderId: String = ""
    var senderName: String = ""

    var text: String = ""

    var createdAt: Int64 = 0

    enum CodingKeys: String, CodingKey { case id, senderId, senderName, text, createdAt }

    init(id: String? = nil, senderId: String = "", senderName: String = "",
         text: String = "", createdAt: Int64 = 0) {
        self.id = id; self.senderId = senderId; self.senderName = senderName
        self.text = text; self.createdAt = createdAt
    }

    // 타입 드리프트로 메시지가 누락되지 않게 방어 디코딩.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // 문서 id = **Firestore 문서 ID** — 커스텀 init(from:) 은 @DocumentID 합성 주입을 대체하므로
        // 합성 디코더와 동일하게 명시 디코드한다(문서 안에 "id" 필드가 없어도 문서 참조에서 채워진다).
        // ⚠️ 구 코드는 "id" **필드**만 읽어서, id 필드를 저장하지 않는 iOS 생성 문서가 전부 id=nil 로
        // 풀렸다(→ 좋아요/댓글/공유/수락 등 guard let id 가 조용히 실패). 비 Firestore 디코더는 필드 폴백.
        _id = (try? c.decode(DocumentID<String>.self, forKey: .id))
            ?? DocumentID(wrappedValue: (try? c.decodeIfPresent(String.self, forKey: .id)) ?? nil)
        senderId = c.flexString(.senderId) ?? ""
        senderName = c.flexString(.senderName) ?? ""
        text = c.flexString(.text) ?? ""
        createdAt = c.flexMillis(.createdAt) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(senderId, forKey: .senderId)
        try c.encode(senderName, forKey: .senderName)
        try c.encode(text, forKey: .text)
        try c.encode(createdAt, forKey: .createdAt)
    }
}


// MARK: - 공개 범위

enum Visibility: String, CaseIterable {

    case publicAll = "public"
    case friends = "friends"
    case privateOnly = "private"

    var label: String {

        switch self {

        case .publicAll:
            return "전체 공개"

        case .friends:
            return "친구만"

        case .privateOnly:
            return "나만 보기"
        }
    }
}


// MARK: - 유연 디코딩 헬퍼

/// Firestore 문서의 필드 타입이 Android/iOS/버전별로 미묘하게 달라도(문자열/Int/Long/Double/Timestamp)
/// 문서가 통째로 누락되지 않도록 관대하게 읽는다. (Diary/Friend/FriendRequest 등 공유)
extension KeyedDecodingContainer {
    func flexString(_ k: Key) -> String? {
        if let v = try? decodeIfPresent(String.self, forKey: k) { return v }
        if let v = try? decodeIfPresent(Int64.self, forKey: k) { return String(v) }
        if let v = try? decodeIfPresent(Double.self, forKey: k) { return String(v) }
        return nil
    }
    func flexInt(_ k: Key) -> Int? {
        if let v = try? decodeIfPresent(Int.self, forKey: k) { return v }
        if let v = try? decodeIfPresent(Int64.self, forKey: k) { return Int(v) }
        if let v = try? decodeIfPresent(Double.self, forKey: k) { return Int(v) }
        if let v = try? decodeIfPresent(String.self, forKey: k) { return Int(v) }
        return nil
    }
    func flexMillis(_ k: Key) -> Int64? {
        if let v = try? decodeIfPresent(Int64.self, forKey: k) { return v }
        if let v = try? decodeIfPresent(Double.self, forKey: k) { return Int64(v) }
        if let ts = try? decodeIfPresent(Timestamp.self, forKey: k) {
            return Int64(ts.seconds) * 1000 + Int64(ts.nanoseconds) / 1_000_000
        }
        return nil
    }
}
