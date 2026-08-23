package com.chaminwoo.stary.shared.config

/**
 * 플랫폼 공용 설정 진입점.
 *
 * ⚠️ 보안 주의
 * 실제 API 키 / Firebase 프로젝트 식별자 / OAuth 클라이언트 ID / 지도 키 등 민감값은
 * 절대 이 파일(또는 소스 코드)에 하드코딩하지 않는다.
 *
 * 주입 경로:
 *  - Android : local.properties / secrets.properties -> Gradle(BuildConfig, manifest placeholder)
 *              + google-services.json (Firebase)
 *  - iOS     : Info.plist / xcconfig + GoogleService-Info.plist (Firebase)
 *
 * 아래 값들은 "비밀이 아닌" 구조적 상수만 둔다.
 */
object StaryConfig {

    /**
     * Firestore 데이터베이스 ID (비밀 아님 — 구조 상수).
     * momentdiary-f26c8 에는 (default) 가 아닌 named DB(stary-db)로 생성되어 있어 명시 지정 필요.
     */
    const val FIRESTORE_DB_ID = "stary-db"

    /** Firestore 컬렉션 이름 (비밀 아님). */
    object Collections {
        const val DIARIES = "diaries"
        const val COMMENTS = "comments"
        const val LIKES = "likes"
        const val NOTIFICATIONS = "notifications"
        const val USERS = "users"
        /** users/{uid} 하위: 수락된 친구 목록 */
        const val FRIENDS = "friends"
        /** 최상위: pending 친구 요청 */
        const val FRIEND_REQUESTS = "friendRequests"
        /** users/{uid} 하위: 열람한 다이어리 기록 (미조회 필터용) */
        const val VIEWED_DIARIES = "viewedDiaries"
        /** 최상위: 1:1 친구 채팅 방 */
        const val CHATS = "chats"
        /** chats/{chatId} 하위: 채팅 메시지 */
        const val MESSAGES = "messages"
        /** users/{uid} 하위: 내가 차단한 사용자 (문서 id = 상대 uid) */
        const val BLOCKED = "blocked"
        /** 최상위: 콘텐츠/사용자 신고 */
        const val REPORTS = "reports"
        /** 최상위: 히든 업적 선점 기록 (문서 id = 업적 id, 앱 전체 단 한 명만) */
        const val HIDDEN_ACHIEVEMENTS = "hiddenAchievements"
        /** 최상위: 친구 초대 리딤 기록 (문서 id = 초대받은 사람 uid — 계정당 1회, 체크리스트 31) */
        const val INVITES = "invites"
    }

    /**
     * 두 사용자 ID 로 결정적 채팅방 ID 를 만든다(정렬 후 결합).
     * 양쪽 사용자가 인자 순서와 무관하게 같은 방(chatId)을 가리키도록 보장한다.
     */
    fun chatId(a: String, b: String): String =
        if (a <= b) "${a}_$b" else "${b}_$a"

    /** 다이어리 열람 가능 반경(미터). */
    const val DIARY_OPEN_RADIUS_M: Float = 100f

    /** 하루(로컬 자정 기준) 최대 업로드 개수. */
    const val DAILY_UPLOAD_LIMIT: Int = 10

    /** 입력 글자수 제한 — 필드에서 초과분을 잘라 선차단. iOS AppConfig 와 동기화. */
    const val DIARY_TITLE_MAX_LEN: Int = 30
    const val DIARY_CONTENT_MAX_LEN: Int = 2000
    const val COMMENT_MAX_LEN: Int = 300
    const val CHAT_MESSAGE_MAX_LEN: Int = 500
    const val NICKNAME_MAX_LEN: Int = 20

    /**
     * 별 합치기(머지) 반경(m) — 업로드된 다이어리가 이 거리 안에 기존 다이어리와 겹치면
     * 지도에서 한 별로 합쳐 표시한다. 대표(모양/색)는 좋아요 내림차순 → 오래된 순 1순위,
     * 크기/밝기는 합쳐진 모든 별의 좋아요 합산으로 반영. iOS AppConfig 와 동기화.
     */
    const val STAR_MERGE_RADIUS_M: Float = 30f

    /** 채팅 메시지 완전 삭제 허용 시간(전송 후 이 시간 이내에만, 보낸 본인이 삭제 가능). iOS AppConfig 와 동기화. */
    const val CHAT_DELETE_WINDOW_MS: Long = 60_000L

    /** 다이어리 짧은 영상 최대 길이(ms). 이보다 길면 업로드 거부. iOS AppConfig 와 동기화. */
    const val VIDEO_MAX_DURATION_MS: Long = 3_000L

    /**
     * 어드민(테스트) 계정 이메일. 이 계정은 히든 업적을 만족해도 **서버에 선점을 기록하지 않아**
     * 히든 업적 슬롯을 차지하지 않는다(실제 유저가 첫 달성자가 될 수 있게). iOS `AppConfig.adminEmails` 와 동기화.
     */
    val ADMIN_EMAILS: Set<String> = setOf("chaalsdn0217@gmail.com")

    fun isAdminEmail(email: String?): Boolean =
        !email.isNullOrBlank() && email.trim().lowercase() in ADMIN_EMAILS

    /** 지도 초기 좌표 폴백 (서울 시청 부근 — 비밀 아님). */
    const val DEFAULT_LAT: Double = 37.5409
    const val DEFAULT_LNG: Double = 127.0794

    /**
     * 다이어리 공유 웹 랜딩(Firebase Hosting, momentdiary-f26c8 — 비밀 아님) 베이스 URL.
     * 랜딩은 `web/` 소스, 배포는 `firebase deploy --only hosting`. iOS AppConfig 와 동기화.
     */
    const val SHARE_BASE_URL: String = "https://momentdiary-f26c8.web.app"

    /**
     * App Links(https) 매칭용 호스트/경로. `SHARE_BASE_URL` 을 쪼갠 값이라 **함께 바꿔야 한다.**
     * AndroidManifest 의 autoVerify intent-filter, `web/.well-known/assetlinks.json`,
     * iOS `apple-app-site-association` 이 모두 이 호스트를 가리킨다.
     */
    const val SHARE_HOST: String = "momentdiary-f26c8.web.app"
    const val SHARE_PATH_DIARY: String = "s"
    const val SHARE_PATH_INVITE: String = "i"

    /** Play 스토어 링크 — 웹 랜딩의 "설치" 버튼/미설치 폴백에 쓴다(비밀 아님). */
    const val PLAY_STORE_URL: String =
        "https://play.google.com/store/apps/details?id=com.chaminwoo.stary_ios"

    /** 다이어리 공유 링크 — 웹 랜딩이 앱 설치자는 stary://diary/{id} 딥링크로 돌려보낸다. */
    fun shareLink(diaryId: String): String = "$SHARE_BASE_URL/s/$diaryId"

    /** 앱 딥링크 스킴/호스트 (stary://diary/{id}). Android Manifest·iOS Info.plist 와 동기화. */
    const val DEEP_LINK_SCHEME: String = "stary"
    const val DEEP_LINK_HOST_DIARY: String = "diary"

    /** 친구 초대 딥링크 호스트 (stary://invite/{inviterUid}) + 초대 링크({BASE}/i/{uid}). */
    const val DEEP_LINK_HOST_INVITE: String = "invite"
    fun inviteLink(inviterUid: String): String = "$SHARE_BASE_URL/i/$inviterUid"

    /** 초대 리딤 허용 기간 — 가입 후 이 시간 이내에만(신규 유입 보상, 체크리스트 31). iOS AppConfig 와 동기화. */
    const val INVITE_REDEEM_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 근처 미조회 별 알림(체크리스트 33) — 감지 반경/하루 상한/알림 최소 간격. iOS AppConfig 와 동기화. */
    const val NEARBY_ALERT_RADIUS_M: Float = 250f
    const val NEARBY_ALERT_DAILY_LIMIT: Int = 5
    const val NEARBY_ALERT_MIN_INTERVAL_MS: Long = 3L * 60 * 1000
}
