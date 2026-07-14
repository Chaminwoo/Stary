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
        static let invites = "invites"          // 최상위 (친구 초대 리딤, 문서 id = 리딤한 사람 uid)
    }

    /// 두 사용자 ID 로 결정적 채팅방 ID 생성(정렬 후 결합).
    static func chatId(_ a: String, _ b: String) -> String {
        a <= b ? "\(a)_\(b)" : "\(b)_\(a)"
    }

    /// 다이어리 열람 가능 반경(미터).
    static let diaryOpenRadiusM: Double = 100

    /// 하루(로컬 자정 기준) 최대 업로드 개수. (StaryConfig.DAILY_UPLOAD_LIMIT 와 동기화)
    static let dailyUploadLimit = 10

    /// 입력 글자수 제한 — 필드에서 초과분을 잘라 선차단. (StaryConfig.*_MAX_LEN 과 동기화)
    static let diaryTitleMaxLen = 30
    static let diaryContentMaxLen = 2000
    static let commentMaxLen = 300
    static let chatMessageMaxLen = 500
    static let nicknameMaxLen = 20

    /// 별 합치기(머지) 반경(m) — 이 거리 안에 겹치는 다이어리는 지도에서 한 별로 합쳐 표시.
    /// (StaryConfig.STAR_MERGE_RADIUS_M 와 동기화. iOS 지도 머지 렌더는 후속 TODO)
    static let starMergeRadiusM: Double = 30

    /// 근처 미조회 별 발견 알림(체크리스트 33). (StaryConfig.NEARBY_ALERT_* 와 동기화)
    static let nearbyAlertRadiusM: Double = 250
    static let nearbyAlertDailyLimit = 5
    static let nearbyAlertMinIntervalMs: Int64 = 3 * 60 * 1000

    /// 채팅 메시지 완전 삭제 허용 시간(ms) — 전송 후 이 시간 이내에만 보낸 본인이 삭제 가능.
    /// (StaryConfig.CHAT_DELETE_WINDOW_MS 와 동기화)
    static let chatDeleteWindowMs: Int64 = 60_000

    /// 다이어리 짧은 영상 최대 길이(ms). 이보다 길면 업로드 거부. (StaryConfig.VIDEO_MAX_DURATION_MS 와 동기화)
    static let videoMaxDurationMs: Int64 = 3_000

    /// 어드민(테스트) 계정 — 히든 업적 선점을 서버에 기록하지 않음. (StaryConfig.ADMIN_EMAILS 와 동기화)
    static let adminEmails: Set<String> = ["chaalsdn0217@gmail.com"]

    static func isAdminEmail(_ email: String?) -> Bool {
        guard let email, !email.isEmpty else { return false }
        return adminEmails.contains(email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
    }

    /// 지도 초기 좌표 폴백(서울 시청 부근).
    static let defaultLat = 37.5409
    static let defaultLng = 127.0794

    /// 다이어리 공유 웹 랜딩 베이스 URL. (StaryConfig.SHARE_BASE_URL 와 동기화)
    static let shareBaseUrl = "https://momentdiary-f26c8.web.app"

    /// 다이어리 공유 링크 — 랜딩이 앱 설치자는 stary://diary/{id} 딥링크로 돌려보낸다.
    static func shareLink(diaryId: String) -> String { "\(shareBaseUrl)/s/\(diaryId)" }

    /// 앱 딥링크 스킴/호스트 (stary://diary/{id}). (StaryConfig.DEEP_LINK_* 와 동기화)
    static let deepLinkScheme = "stary"
    static let deepLinkHostDiary = "diary"

    /// 친구 초대 딥링크 호스트 + 초대 링크(체크리스트 31). (StaryConfig 와 동기화)
    static let deepLinkHostInvite = "invite"
    static func inviteLink(inviterUid: String) -> String { "\(shareBaseUrl)/i/\(inviterUid)" }

    /// 초대 리딤 허용 기간(ms) — 가입 후 이 시간 이내에만. (StaryConfig.INVITE_REDEEM_WINDOW_MS 와 동기화)
    static let inviteRedeemWindowMs: Int64 = 7 * 24 * 60 * 60 * 1000
}
