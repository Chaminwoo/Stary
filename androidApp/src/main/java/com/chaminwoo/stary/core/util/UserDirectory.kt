package com.chaminwoo.stary.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig

/**
 * 사용자 현재 프로필(이름/사진) 디렉터리 — **표시 시점 해석**용 전역 캐시.
 *
 * 다이어리/댓글/알림/채팅/친구 문서에는 작성·수락 당시의 이름·사진이 스냅샷으로 박혀 있어,
 * 이후 닉네임/프사를 바꾸면 과거 글이 옛 값으로 남는다. 특히 **처음 로그인한 사용자**는 그 스냅샷이
 * 대부분 상대의 "구글 기본 이름/사진"이라 앱 전체가 구글 프로필로 보였다.
 * 화면에서는 항상 `users/{uid}` 의 **현재 값**을 보여주기 위해, uid 별 실시간 리스너 1개를 붙여
 * mutableStateMap 에 반영한다.
 * (한 화면에 뜨는 서로 다른 작성자 수는 소수라 리스너 수는 자연히 바운드됨.
 *  프로세스 생존 동안 유지 — 화면 이동 시 재구독 비용 없음.)
 *
 * 값이 아직 없거나 users 문서가 없는 사용자는 폴백(문서에 저장된 스냅샷 이름/사진)을 쓴다.
 *
 * ⚠️ **타인의 이름/사진을 그리는 곳은 예외 없이 이 디렉터리를 거친다.** 새 화면을 만들 때
 *    `diary.userName` / `notif.actorName` / `friend.photoUrl` 같은 스냅샷 필드를 그대로 그리지 말고
 *    [rememberUserDisplay] 에 uid 와 함께 넘겨 폴백으로만 쓰이게 할 것.
 */
object UserDirectory {
    data class Info(val name: String?, val photoUrl: String?)

    /** 화면에 그릴 최종 표시값(현재 프로필 우선, 없으면 스냅샷 폴백). */
    data class Display(val name: String, val photoUrl: String)

    /** uid → 현재 프로필. Compose 가 관찰하는 상태 맵(리스너가 갱신). */
    private val cache = mutableStateMapOf<String, Info>()
    private val listening = HashSet<String>()

    /** uid 의 users/{uid} 문서를 실시간 구독(최초 1회만). 메인 스레드에서 호출. */
    fun ensureWatching(userId: String) {
        if (userId.isBlank() || !listening.add(userId)) return
        staryFirestore.collection(StaryConfig.Collections.USERS).document(userId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    cache[userId] = Info(
                        name = snap.getString("userName")?.takeIf { it.isNotBlank() },
                        photoUrl = snap.getString("profileImageUrl")?.takeIf { it.isNotBlank() },
                    )
                }
            }
    }

    /** 여러 uid 를 한 번에 구독 시작(목록 화면에서 행마다 호출하는 대신). */
    fun ensureWatchingAll(userIds: Collection<String>) = userIds.forEach(::ensureWatching)

    /** 현재 이름. 아직 로드 전/없으면 [fallback]. */
    fun name(userId: String, fallback: String): String =
        cache[userId]?.name ?: fallback

    /** 현재 프로필 사진 URL(없으면 null). */
    fun photoUrl(userId: String): String? = cache[userId]?.photoUrl

    /** 현재 프로필 사진 URL. 아직 로드 전/없으면 [fallback]. */
    fun photoUrl(userId: String, fallback: String): String =
        cache[userId]?.photoUrl ?: fallback
}

/** [UserDirectory] 구독을 시작하고 현재 이름을 반환(상태 관찰 — 바뀌면 리컴포즈). */
@Composable
fun rememberCurrentUserName(userId: String, fallback: String): String {
    LaunchedEffect(userId) { UserDirectory.ensureWatching(userId) }
    return UserDirectory.name(userId, fallback)
}

/** [UserDirectory] 구독을 시작하고 현재 프로필 사진 URL 을 반환(없으면 null). */
@Composable
fun rememberCurrentUserPhoto(userId: String): String? {
    LaunchedEffect(userId) { UserDirectory.ensureWatching(userId) }
    return UserDirectory.photoUrl(userId)
}

/**
 * 이름 + 사진을 한 번에 — 아바타와 이름을 같이 그리는 행(친구/알림/채팅/차단 목록)용.
 * [fallbackName]/[fallbackPhoto] 는 각 문서에 박힌 스냅샷 값(현재 프로필을 못 읽을 때만 쓰임).
 */
@Composable
fun rememberUserDisplay(
    userId: String,
    fallbackName: String = "",
    fallbackPhoto: String = "",
): UserDirectory.Display {
    LaunchedEffect(userId) { UserDirectory.ensureWatching(userId) }
    return UserDirectory.Display(
        name = UserDirectory.name(userId, fallbackName),
        photoUrl = UserDirectory.photoUrl(userId, fallbackPhoto),
    )
}
