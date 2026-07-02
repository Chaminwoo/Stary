import Foundation

/// 앱 전역 구조 상수.
///
/// ⚠️ 비밀값 아님 — 컬렉션 이름/DB ID/기본 좌표 등 구조 상수만 둔다.
/// (KMP `shared` 모듈의 `StaryConfig` 와 의미가 동일하다. 값이 바뀌면 양쪽을 함께 갱신할 것.)
enum AppConfig {
    /// momentdiary-f26c8 는 (default) 가 아닌 named DB(stary-db) 로 생성되어 있어 명시 지정 필요.
    static let firestoreDbId = "stary-db"

    enum Collections {
        static let diaries = "diaries"
        static let comments = "comments"
        static let likes = "likes"
        static let notifications = "notifications"
        static let users = "users"
        static let friends = "friends"          // users/{uid} 하위
        static let friendRequests = "friendRequests"
        static let viewedDiaries = "viewedDiaries"
        static let blocked = "blocked"           // users/{uid} 하위 (차단한 사용자)
        static let reports = "reports"           // 최상위 (신고 접수)
        static let chats = "chats"
        static let messages = "messages"        // chats/{chatId} 하위
        static let hiddenAchievements = "hiddenAchievements" // 최상위 (히든 업적 선점, 앱 전체 1명)
    }

    /// 두 사용자 ID 로 결정적 채팅방 ID 생성(정렬 후 결합).
    static func chatId(_ a: String, _ b: String) -> String {
        a <= b ? "\(a)_\(b)" : "\(b)_\(a)"
    }

    /// 다이어리 열람 가능 반경(미터).
    static let diaryOpenRadiusM: Double = 100

    /// 지도 초기 좌표 폴백(서울 시청 부근).
    static let defaultLat = 37.5409
    static let defaultLng = 127.0794
}
