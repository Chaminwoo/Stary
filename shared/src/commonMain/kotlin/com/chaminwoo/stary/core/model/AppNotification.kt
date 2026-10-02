package com.chaminwoo.stary.core.model

/**
 * 알림 종류.
 * - LIKE / COMMENT      : 내 다이어리에 달린 반응 (diaryId 有)
 * - FRIEND_POST         : 친구의 새 다이어리 (diaryId 有)
 * - FRIEND_REQUEST      : 받은 친구 요청 (diaryId 無 — 탭하면 친구 화면)
 * - FIRST_STAR          : 누군가의 첫 전체 공개 별 (diaryId 有) — notifications 문서가 아니라
 *                         최상위 firstStars 를 앱이 읽어 목록에 합친 것(서버 announceFirstStar 가 생성)
 */
enum class NotificationType { LIKE, COMMENT, FRIEND_POST, FRIEND_REQUEST, FIRST_STAR }

data class AppNotification(
    val id: String = "",
    val type: String = NotificationType.LIKE.name,
    val diaryId: String = "",
    val diaryTitle: String = "",
    val diaryOwnerId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val content: String = "",
    val createdAt: Long = 0L, // epoch millis (UTC)
    val isRead: Boolean = false
)
