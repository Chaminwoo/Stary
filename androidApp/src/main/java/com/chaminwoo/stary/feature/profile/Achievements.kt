package com.chaminwoo.stary.feature.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.diary.DiaryViewModel

/** 업적 판정에 쓰이는 사용자 누적 통계. */
data class UserStats(
    val diariesCreated: Int = 0,
    val likesReceived: Int = 0,
    val viewsReceived: Int = 0,
    val diariesViewed: Int = 0,
    val friends: Int = 0,
)

/**
 * 업적 정의. 완료 시 보상으로 칭호 '성흔([stigma])'을 프로필에 장착할 수 있다.
 * [hidden] 이면 조건을 가려(???) 궁금증을 유발한다.
 */
data class Achievement(
    val id: String,
    val stigma: String,
    val condition: String,
    val hidden: Boolean = false,
    val unlocked: (UserStats) -> Boolean,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_step", "첫 발자국", "다이어리 1개 작성하기") { it.diariesCreated >= 1 },
        Achievement("star_traveler", "별의 여행자", "다이어리 10개 열람하기") { it.diariesViewed >= 10 },
        Achievement("storyteller", "이야기꾼", "다이어리 10개 작성하기") { it.diariesCreated >= 10 },
        Achievement("popular", "인기쟁이", "좋아요 50개 받기") { it.likesReceived >= 50 },
        Achievement("watched_star", "주목받는 별", "총 조회수 100 달성하기") { it.viewsReceived >= 100 },
        Achievement("companion", "길동무", "친구 3명 만들기") { it.friends >= 3 },
        Achievement("collector", "밤하늘 수집가", "다이어리 30개 작성하기") { it.diariesCreated >= 30 },
        Achievement("guide", "별빛 인도자", "좋아요 200개 받기") { it.likesReceived >= 200 },
        // 숨겨진 업적 — 조건 비공개
        Achievement("cosmic_rascal", "우주의 악동", "???", hidden = true) { it.diariesViewed >= 42 },
        Achievement("lone_observer", "고독한 관측자", "???", hidden = true) { it.diariesCreated >= 20 && it.friends == 0 },
    )

    fun byId(id: String?): Achievement? = all.firstOrNull { it.id == id }
}

/** 장착한 성흔(업적 id)을 기기에 저장. 사용자별로 보관. */
object StigmaStore {
    private const val PREFS = "stary_prefs"
    private fun key(userId: String) = "stigma_$userId"

    fun equipped(context: Context, userId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(userId), null)

    fun equip(context: Context, userId: String, achievementId: String?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (achievementId == null) edit.remove(key(userId)) else edit.putString(key(userId), achievementId)
        edit.apply()
    }
}

/** 현재 사용자의 누적 통계를 실시간 수집. */
@Composable
fun rememberUserStats(
    userId: String,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory()),
): UserStats {
    val myDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()

    val viewedIds by remember(userId) {
        FirebaseViewedRepository().observeViewedIds(userId)
    }.collectAsState(initial = emptySet())

    val friends by remember(userId) {
        FirebaseFriendRepository().observeFriends(userId)
    }.collectAsState(initial = emptyList())

    return UserStats(
        diariesCreated = myDiaries.size,
        likesReceived = myDiaries.sumOf { it.likeCount },
        viewsReceived = myDiaries.sumOf { it.viewCount },
        diariesViewed = viewedIds.size,
        friends = friends.size,
    )
}
