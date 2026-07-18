package com.chaminwoo.stary.core.model

/**
 * 플랫폼 공용(KMP commonMain) 도메인 모델.
 *
 * 기존 Android 전용 코드에서는 createdAt 이 Firebase 의 [com.google.firebase.Timestamp] 였지만,
 * iOS 와 공용으로 쓰기 위해 플랫폼 비종속 타입인 epoch millis(Long) 로 변경했다.
 * Firestore <-> 모델 변환은 각 플랫폼의 Repository 구현부에서 담당한다.
 */
data class Diary(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    // (사용 안 함) 익명 게시 기능은 제거됨. 옛 문서 디코딩 하위호환 + iOS 모델 패리티용으로만 남겨둠 — 항상 false.
    val isAnonymous: Boolean = false,
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    /** 3초 이내 짧은 영상 URL(Storage). 비어 있으면 영상 없음 — imageUrl 과 배타적으로 사용. */
    val videoUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = 0L, // epoch millis (UTC)
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    /** 별 모양 종류 인덱스 (0..4). 렌더는 starType×starColor 조합으로 결정. */
    val starType: Int = 0,
    /** 별 색상 팔레트 인덱스 (0..11). 팔레트는 androidApp designsystem StarStyle 참고. */
    val starColor: Int = 0,
    /** 공개 범위: "public"(전체), "friends"(친구만), "private"(나만보기). */
    val visibilityType: String = "public"
)
