package com.chaminwoo.stary.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig

/**
 * 사용자 현재 프로필(이름/사진) 디렉터리 — **표시 시점 해석**용 전역 캐시.
 *
 * 다이어리/댓글 문서에는 작성 당시의 userName 이 스냅샷으로 박혀 있어, 이후 닉네임/프사를
 * 바꾸면 과거 글이 옛 이름으로 남는다. 화면에서는 항상 `users/{uid}` 의 **현재 값**을
 * 보여주기 위해, uid 별 실시간 리스너 1개를 붙여 mutableStateMap 에 반영한다.
 * (한 화면에 뜨는 서로 다른 작성자 수는 소수라 리스너 수는 자연히 바운드됨.
 *  프로세스 생존 동안 유지 — 화면 이동 시 재구독 비용 없음.)
 *
 * 값이 아직 없거나 users 문서가 없는 사용자는 폴백(문서에 저장된 스냅샷 이름)을 쓴다.
 */
object UserDirectory {
    data class Info(val name: String?, val photoUrl: String?)

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

    /** 현재 이름. 아직 로드 전/없으면 [fallback]. */
    fun name(userId: String, fallback: String): String =
        cache[userId]?.name ?: fallback

    /** 현재 프로필 사진 URL(없으면 null). */
    fun photoUrl(userId: String): String? = cache[userId]?.photoUrl
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
