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

    /** Firestore 컬렉션 이름 (비밀 아님). */
    object Collections {
        const val DIARIES = "diaries"
        const val COMMENTS = "comments"
        const val LIKES = "likes"
        const val NOTIFICATIONS = "notifications"
        const val USERS = "users"
    }

    /** 다이어리 열람 가능 반경(미터). */
    const val DIARY_OPEN_RADIUS_M: Float = 100f

    /** 지도 초기 좌표 폴백 (서울 시청 부근 — 비밀 아님). */
    const val DEFAULT_LAT: Double = 37.5409
    const val DEFAULT_LNG: Double = 127.0794
}
