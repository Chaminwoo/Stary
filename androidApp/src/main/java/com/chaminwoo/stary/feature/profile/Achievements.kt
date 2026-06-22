package com.chaminwoo.stary.feature.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.core.geo.GeoUtils
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
    // ── 창의적 업적용 통계 ──
    val maxSpanMeters: Float = 0f, // 내 다이어리 두 곳 사이의 최대 거리(미터)
    val maxLikesOnOne: Int = 0,    // 한 다이어리가 받은 최다 좋아요
    val distinctDays: Int = 0,     // 기록한 서로 다른 날짜 수
    val nightPosts: Int = 0,       // 자정~새벽(0~4시 UTC)에 올린 기록 수
)

/** 업적 보상 — 칭호 / 별 모양 / 별 색 중 하나. (칭호 업적과 별·색 업적을 분리) */
sealed interface Reward {
    data class Title(val name: String) : Reward       // 칭호(프로필에 장착)
    data class Shape(val shapeType: Int) : Reward      // 별 모양 해금(StarStyle type)
    data class StarColor(val colorIndex: Int) : Reward // 별 색 해금(StarStyle palette)
}

/**
 * 업적 정의. 완료 시 [reward] 를 획득한다.
 * [hidden] 이면 조건을 가려(???) 궁금증을 유발한다.
 */
data class Achievement(
    val id: String,
    val name: String,
    val condition: String,
    val reward: Reward,
    val hidden: Boolean = false,
    val unlocked: (UserStats) -> Boolean,
) {
    /** 칭호 업적이면 칭호명, 아니면 null. */
    val titleName: String? get() = (reward as? Reward.Title)?.name
}

object Achievements {
    // ── 칭호 업적 — 달성 시 프로필에 칭호를 장착할 수 있다 ──
    val titleAchievements: List<Achievement> = listOf(
        Achievement("first_step", "첫 발자국", "다이어리 1개 작성하기", Reward.Title("첫 발자국")) { it.diariesCreated >= 1 },
        Achievement("star_traveler", "별의 여행자", "다이어리 10개 열람하기", Reward.Title("별의 여행자")) { it.diariesViewed >= 10 },
        Achievement("storyteller", "이야기꾼", "다이어리 10개 작성하기", Reward.Title("이야기꾼")) { it.diariesCreated >= 10 },
        Achievement("popular", "인기쟁이", "좋아요 50개 받기", Reward.Title("인기쟁이")) { it.likesReceived >= 50 },
        Achievement("watched_star", "주목받는 별", "총 조회수 100 달성하기", Reward.Title("주목받는 별")) { it.viewsReceived >= 100 },
        Achievement("companion", "길동무", "친구 3명 만들기", Reward.Title("길동무")) { it.friends >= 3 },
        Achievement("pilgrim", "우주의 순례자", "다이어리 30개 작성하기", Reward.Title("우주의 순례자")) { it.diariesCreated >= 30 },
        Achievement("guide", "별빛의 인도자", "좋아요 200개 받기", Reward.Title("별빛의 인도자")) { it.likesReceived >= 200 },
        // 숨겨진 칭호 업적 — 조건 비공개
        Achievement("cosmic_rascal", "우주의 악동", "???", Reward.Title("우주의 악동"), hidden = true) { it.diariesViewed >= 42 },
        Achievement("lone_observer", "고독한 관측자", "???", Reward.Title("고독한 관측자"), hidden = true) { it.diariesCreated >= 20 && it.friends == 0 },
    )

    // ── 별 모양/색 업적 — 달성 시 업로드 피커에서 해당 모양·색이 해금된다 ──
    //    난이도가 높을수록 더 화려한 모양/보석빛 색을 보상으로 준다.
    val rewardAchievements: List<Achievement> = listOf(
        // ── 별 모양 보상 ──
        Achievement("shape_flower", "별꽃을 피운 자", "좋아요 80개 받기", Reward.Shape(5)) { it.likesReceived >= 80 },          // 꽃
        Achievement("shape_gem", "결정의 시간", "서로 다른 14일에 기록하기", Reward.Shape(6)) { it.distinctDays >= 14 },        // 보석
        Achievement("shape_moon", "달의 인도자", "총 조회수 500 달성하기", Reward.Shape(7)) { it.viewsReceived >= 500 },         // 초승달
        Achievement("shape_planet", "나만의 행성", "서로 다른 30일에 기록하기", Reward.Shape(8)) { it.distinctDays >= 30 },      // 행성
        Achievement("shape_farjourney", "머나먼 여정", "기록 두 곳이 50km 이상 떨어지기", Reward.Shape(3)) { it.maxSpanMeters >= 50_000f },
        Achievement("shape_border", "국경을 넘어", "기록 두 곳이 1,000km 이상 떨어지기", Reward.Shape(4)) { it.maxSpanMeters >= 1_000_000f },
        // ── 별 색 보상 (단색) ──
        Achievement("color_passion", "정열의 한 방", "한 다이어리에 좋아요 30개 받기", Reward.StarColor(3)) { it.maxLikesOnOne >= 30 },
        Achievement("color_sunset", "노을 수집가", "다이어리 25개 작성하기", Reward.StarColor(4)) { it.diariesCreated >= 25 },
        Achievement("color_steady", "꾸준한 관측자", "서로 다른 7일에 기록하기", Reward.StarColor(5)) { it.distinctDays >= 7 },
        Achievement("color_abyss", "심연의 탐구자", "좋아요 150개 받기", Reward.StarColor(6)) { it.likesReceived >= 150 },
        Achievement("color_wanderer", "대지의 방랑자", "기록 두 곳이 10km 이상 떨어지기", Reward.StarColor(11)) { it.maxSpanMeters >= 10_000f },
        Achievement("color_midnight", "심야의 관측자", "자정~새벽(0~4시)에 5번 기록하기", Reward.StarColor(13)) { it.nightPosts >= 5 },
        Achievement("color_life", "생명의 인연", "친구 5명 만들기", Reward.StarColor(14)) { it.friends >= 5 },
        Achievement("color_gold", "황금빛 발견", "총 조회수 300 달성하기", Reward.StarColor(15)) { it.viewsReceived >= 300 },
        Achievement("color_nebula", "성운의 빛", "다이어리 50개 작성하기", Reward.StarColor(12)) { it.diariesCreated >= 50 },
        // ── 별 색 보상 (그라데이션 · 최고 난이도) ──
        Achievement("color_grad_aurora", "오로라의 주인", "좋아요 300개 받기", Reward.StarColor(16)) { it.likesReceived >= 300 },
        Achievement("color_grad_emerald", "수많은 벗", "친구 20명 만들기", Reward.StarColor(17)) { it.friends >= 20 },
        Achievement("color_grad_sunset", "백 개의 별빛", "다이어리 100개 작성하기", Reward.StarColor(18)) { it.diariesCreated >= 100 },
        Achievement("color_grad_glacier", "은하의 정복자", "총 조회수 1,000 달성하기", Reward.StarColor(19)) { it.viewsReceived >= 1000 },
    )

    val all: List<Achievement> = titleAchievements + rewardAchievements

    fun byId(id: String?): Achievement? = all.firstOrNull { it.id == id }

    /** 주어진 통계로 현재 해금된 업적 id 집합. */
    fun unlockedIds(stats: UserStats): Set<String> =
        all.filter { it.unlocked(stats) }.map { it.id }.toSet()
}

/**
 * 별 모양/색의 업적 해금 매핑. (업적 정의의 보상에서 자동 도출 — 한 곳만 고치면 동기화됨)
 * 맵에 없는 인덱스는 기본 해금(처음부터 사용 가능).
 */
object StarUnlocks {
    val shape: Map<Int, String> = Achievements.all
        .mapNotNull { a -> (a.reward as? Reward.Shape)?.let { it.shapeType to a.id } }.toMap()

    val color: Map<Int, String> = Achievements.all
        .mapNotNull { a -> (a.reward as? Reward.StarColor)?.let { it.colorIndex to a.id } }.toMap()

    /** 해당 모양이 잠겨 있으면 필요한 업적, 아니면 null. */
    fun lockedShapeAch(type: Int, unlockedIds: Set<String>): Achievement? =
        shape[type]?.takeIf { it !in unlockedIds }?.let { Achievements.byId(it) }

    /** 해당 색이 잠겨 있으면 필요한 업적, 아니면 null. */
    fun lockedColorAch(idx: Int, unlockedIds: Set<String>): Achievement? =
        color[idx]?.takeIf { it !in unlockedIds }?.let { Achievements.byId(it) }
}

/** 장착한 칭호(업적 id)를 기기에 저장. 사용자별로 보관. */
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

    // 다이어리 좌표/시간 기반 파생 통계 (목록이 바뀔 때만 재계산)
    val derived = remember(myDiaries) {
        val coords = myDiaries.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        var maxSpan = 0f
        for (i in coords.indices) {
            for (j in i + 1 until coords.size) {
                val d = GeoUtils.distanceBetween(
                    coords[i].latitude, coords[i].longitude,
                    coords[j].latitude, coords[j].longitude
                )
                if (d > maxSpan) maxSpan = d
            }
        }
        val days = myDiaries.filter { it.createdAt > 0 }.map { it.createdAt / 86_400_000L }.distinct().size
        val night = myDiaries.count {
            it.createdAt > 0 && ((it.createdAt / 3_600_000L) % 24L).toInt() in 0..4
        }
        UserStats(
            diariesCreated = myDiaries.size,
            likesReceived = myDiaries.sumOf { it.likeCount },
            viewsReceived = myDiaries.sumOf { it.viewCount },
            maxSpanMeters = maxSpan,
            maxLikesOnOne = myDiaries.maxOfOrNull { it.likeCount } ?: 0,
            distinctDays = days,
            nightPosts = night,
        )
    }

    return derived.copy(diariesViewed = viewedIds.size, friends = friends.size)
}
