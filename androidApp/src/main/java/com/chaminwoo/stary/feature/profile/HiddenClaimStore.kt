package com.chaminwoo.stary.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig

/**
 * 히든 업적 선점 현황 전역 스토어 — `hiddenAchievements` 컬렉션(문서 ≤ 히든 업적 수, 현재 11개)을
 * **리스너 1개**로 통째 구독해 어느 화면에서든 uid 로 O(1) 조회하게 한다.
 *
 * 용도:
 *  - 이름이 뜨는 모든 곳의 **전용 크리스탈 배지**([com.chaminwoo.stary.core.ui.HiddenStarBadges]).
 *  - 업적 화면의 달성 현황/달성자, 프로필의 히든 아이콘.
 *
 * (컬렉션이 작고 전역에서 계속 필요하므로 화면별 구독 대신 프로세스 수명 캐시 — [UserDirectory] 와 같은 패턴.)
 */
object HiddenClaimStore {

    /** achievementId → 선점 기록. Compose 가 관찰하는 상태 맵(리스너가 갱신). */
    private val claims = mutableStateMapOf<String, HiddenClaim>()
    private var listening = false

    /** 컬렉션 실시간 구독 시작(최초 1회만). */
    fun ensureWatching() {
        if (listening) return
        listening = true
        staryFirestore.collection(StaryConfig.Collections.HIDDEN_ACHIEVEMENTS)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener // 권한/네트워크 에러는 무시
                claims.clear()
                for (d in snap.documents) {
                    claims[d.id] = HiddenClaim(
                        achieverId = d.getString("achieverId") ?: "",
                        achieverName = d.getString("achieverName") ?: "",
                        claimedAt = d.getLong("claimedAt") ?: 0L,
                    )
                }
            }
    }

    /** 업적 id 의 선점 기록(없으면 null = 아직 달성자 없음). */
    fun claimOf(achievementId: String): HiddenClaim? =
        claims[achievementId]?.takeIf { it.claimed }

    /** [userId] 가 달성한 히든 업적들 — 정의 순서(안정적 표시 순서). */
    fun achievementsOf(userId: String): List<HiddenAchievement> {
        if (userId.isBlank()) return emptyList()
        val mine = claims.filterValues { it.achieverId == userId }.keys
        return HiddenAchievements.all.filter { it.id in mine }
    }
}

/** 구독을 시작하고 [userId] 가 달성한 히든 업적 목록을 반환(바뀌면 리컴포즈). */
@Composable
fun rememberHiddenAchievementsOf(userId: String): List<HiddenAchievement> {
    LaunchedEffect(Unit) { HiddenClaimStore.ensureWatching() }
    return HiddenClaimStore.achievementsOf(userId)
}
