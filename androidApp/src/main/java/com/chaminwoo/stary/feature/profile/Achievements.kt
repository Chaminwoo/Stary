package com.chaminwoo.stary.feature.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.core.geo.GeoUtils
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.DiaryUnlockStore
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos

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
    val distinctDays: Int = 0,     // 기록한 서로 다른 날짜 수(기기 현지 날짜)
    val nightPosts: Int = 0,       // 자정~새벽(현지 0~4시)에 올린 기록 수
    // ── 히든 업적용 통계 ──
    val secretKeywordTitle: Boolean = false, // 제목에 히든 키워드를 넣은 다이어리가 있는가
    val remoteRegions: Set<String> = emptySet(), // 도달한 오지 region 집합(glacier/desert/trench/triangle)
    // ── 친구 초대 보상용 통계(체크리스트 31) ──
    val invitedFriends: Int = 0,        // 내 초대를 리딤해 가입한 친구 수
    val redeemedInvite: Boolean = false, // 내가 초대를 리딤했는가
    // ── 2026-09-26 추가 업적용 통계 — 시간 판정은 전부 기기 현지 시간 ──
    val photoPosts: Int = 0,             // 사진을 넣은 기록(영상 제외)
    val videoPosts: Int = 0,
    val longPosts: Int = 0,              // 본문 500자 이상
    val shortPosts: Int = 0,             // 본문 20자 이하(익명 게시는 기능이 없어 업적에서 제외)
    val privatePosts: Int = 0,           // 나만보기
    val maxStreakDays: Int = 0,          // 최장 연속 기록 일수
    val dawnPosts: Int = 0,              // 새벽 5~7시(5:00~6:59)
    val maxPostsInDay: Int = 0,          // 하루 최다 기록 수
    val distinctMonths: Int = 0,         // 1~12월 중 기록한 달 수(연도 무관)
    val wishTimePost: Boolean = false,   // 11:11 또는 23:11 에 기록
    val fullMoonPost: Boolean = false,   // 보름달 밤(18~6시)에 기록
    val newYearPost: Boolean = false,    // 1월 1일
    val christmasPost: Boolean = false,  // 12월 24·25일
    val distinctPlaces: Int = 0,         // 서로 1km 이상 떨어진 장소 수
    val maxLocalCluster: Int = 0,        // 반경 1km 안에 모인 내 별 최대 개수
    val revisitedSpot: Boolean = false,  // 같은 자리(50m)에서 30일 넘게 지나 다시 기록
    val firstInArea: Int = 0,            // 1km 안에 먼저 남겨진 (남의) 별이 없던 곳에 남긴 별 수
    val nearNeighbor: Boolean = false,   // 남의 별 100m 안에 내 별이 있음
    val starLogCount: Int = 0,           // 별 도감에 모은 다른 사람의 별(= 연 글 ∩ 지금 보이는 남의 글)
    val maxViewsOnOne: Int = 0,
    val commentsReceived: Int = 0,
    val distinctShapes: Int = 0,
    val distinctColors: Int = 0,
    val achievementsDone: Int = 0,       // 왕관 판정용 — 왕관 자신을 뺀 일반 업적 달성 수
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

/** 칭호 업적(이름 = 칭호) 짧은 생성자. */
private fun title(id: String, name: String, condition: String, hidden: Boolean = false, unlocked: (UserStats) -> Boolean) =
    Achievement(id, name, condition, Reward.Title(name), hidden, unlocked)

object Achievements {
    /** 왕관(일반 업적 N개 달성) 업적 id — 통계 수집 시 자기 자신을 빼고 센다. */
    const val CROWN_ID = "shape_crown"
    const val CROWN_TARGET = 30

    // ── 칭호 업적 — 달성 시 프로필에 칭호를 장착할 수 있다 ──
    val titleAchievements: List<Achievement> = listOf(
        Achievement("first_step", "첫 발자국", "다이어리 1개 작성하기", Reward.Title("첫 발자국")) { it.diariesCreated >= 1 },
        Achievement("star_traveler", "별의 여행자", "다른 사람의 다이어리 10개 열람하기", Reward.Title("별의 여행자")) { it.diariesViewed >= 10 },
        Achievement("storyteller", "이야기꾼", "다이어리 10개 작성하기", Reward.Title("이야기꾼")) { it.diariesCreated >= 10 },
        Achievement("popular", "인기쟁이", "좋아요 50개 받기", Reward.Title("인기쟁이")) { it.likesReceived >= 50 },
        Achievement("watched_star", "주목받는 별", "총 조회수 100 달성하기", Reward.Title("주목받는 별")) { it.viewsReceived >= 100 },
        Achievement("companion", "길동무", "친구 3명 만들기", Reward.Title("길동무")) { it.friends >= 3 },
        Achievement("pilgrim", "우주의 순례자", "다이어리 30개 작성하기", Reward.Title("우주의 순례자")) { it.diariesCreated >= 30 },
        Achievement("guide", "별빛의 인도자", "좋아요 200개 받기", Reward.Title("별빛의 인도자")) { it.likesReceived >= 200 },
        // ── 친구 초대 보상(체크리스트 31) — 초대한 쪽/받은 쪽 모두 칭호 ──
        Achievement("invite_bond", "별의 인연", "초대를 받아 Stary 에 합류하기", Reward.Title("별의 인연")) { it.redeemedInvite },
        Achievement("invite_beacon", "별의 등대", "친구 1명을 Stary 로 초대하기", Reward.Title("별의 등대")) { it.invitedFriends >= 1 },
        Achievement("invite_flock", "별무리의 길잡이", "친구 5명을 Stary 로 초대하기", Reward.Title("별무리의 길잡이")) { it.invitedFriends >= 5 },
        // ※ 기존 '???' 칭호(우주의 악동/고독한 관측자)는 히든 업적(HiddenAchievements)으로 이관됨.

        // ── 2026-09-26 추가 칭호(사용자 승인 — 계절 업적은 나라마다 달라 제외) ──
        // 기록
        title("count_five", "작은 성좌", "다이어리 5개 작성하기") { it.diariesCreated >= 5 },
        title("photo_ten", "빛을 인화하다", "사진을 넣은 다이어리 10개 작성하기") { it.photoPosts >= 10 },
        title("video_first", "재생되는 기억", "영상을 넣은 다이어리 작성하기") { it.videoPosts >= 1 },
        title("long_letter", "부치지 못한 편지", "본문 500자 이상인 다이어리 작성하기") { it.longPosts >= 1 },
        title("short_post", "여백의 한 줄", "본문 20자 이하로 다이어리 작성하기") { it.shortPosts >= 1 },
        title("private_five", "서랍 속 은하", "'나만보기' 다이어리 5개 작성하기") { it.privatePosts >= 5 },
        // 꾸준함
        title("streak_three", "궤도 진입", "3일 연속 기록하기") { it.maxStreakDays >= 3 },
        title("streak_month", "한 번의 삭망", "30일 연속 기록하기") { it.maxStreakDays >= 30 },
        title("days_hundred", "백일몽", "서로 다른 100일에 기록하기") { it.distinctDays >= 100 },
        // 시간 · 특별한 날
        title("all_months", "열두 개의 달", "1월부터 12월까지 모든 달에 기록하기") { it.distinctMonths >= 12 },
        title("new_year", "첫 페이지", "1월 1일에 기록하기", hidden = true) { it.newYearPost },
        title("christmas", "고요한 밤", "12월 24일이나 25일에 기록하기", hidden = true) { it.christmasPost },
        // 여행 · 장소
        title("places_five", "흩어진 좌표", "서로 1km 이상 떨어진 5곳에서 기록하기") { it.distinctPlaces >= 5 },
        title("places_twenty", "나침반의 기억", "서로 1km 이상 떨어진 20곳에서 기록하기") { it.distinctPlaces >= 20 },
        title("home_cluster", "골목의 성단", "반경 1km 안에 내 별 5개 남기기") { it.maxLocalCluster >= 5 },
        title("revisit", "다시, 그 자리", "같은 자리(50m 이내)에서 30일 넘게 지나 다시 기록하기", hidden = true) { it.revisitedSpot },
        // 열람 · 별 도감
        title("log_ten", "작은 표본 상자", "별 도감에 다른 사람의 별 10개 모으기") { it.starLogCount >= 10 },
        title("log_hundred", "백 개의 우주", "별 도감에 다른 사람의 별 100개 모으기") { it.starLogCount >= 100 },
        title("neighbor", "쌍성", "다른 사람의 별 100m 안에 내 별 남기기") { it.nearNeighbor },
        // 사랑받기 · 친구
        title("like_ten_one", "잔잔한 박수", "한 다이어리에 좋아요 10개 받기") { it.maxLikesOnOne >= 10 },
        title("views_one", "모두가 올려다본 별", "한 다이어리 조회수 100 달성하기") { it.maxViewsOnOne >= 100 },
        title("comments_ten", "창가의 대화", "댓글 10개 받기") { it.commentsReceived >= 10 },
        title("friend_first", "교신이 닿다", "친구 1명 만들기") { it.friends >= 1 },
        title("friends_ten", "함께 공전하는 사이", "친구 10명 만들기") { it.friends >= 10 },
        // 꾸미기
        title("shapes_five", "변주곡", "서로 다른 별 모양 5개로 기록하기") { it.distinctShapes >= 5 },
        title("colors_eight", "일곱 빛깔 너머", "서로 다른 별 색 8개로 기록하기") { it.distinctColors >= 8 },
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
        // ── 별 모양 보상 (2026-09-26 추가 — 9~20) ──
        Achievement("shape_heart", "하트 성운", "좋아요 500개 받기", Reward.Shape(9)) { it.likesReceived >= 500 },
        Achievement("shape_comet", "지평선 너머로", "기록 두 곳이 5,000km 이상 떨어지기", Reward.Shape(10)) { it.maxSpanMeters >= 5_000_000f },
        Achievement("shape_snow", "아무도 밟지 않은 눈밭", "1km 안에 먼저 남겨진 별이 없던 곳에 별 3개 남기기", Reward.Shape(11)) { it.firstInArea >= 3 },
        Achievement("shape_sakura", "꽃잎이 흩날린 하루", "하루에 별 3개 남기기", Reward.Shape(12)) { it.maxPostsInDay >= 3 },
        Achievement("shape_sun", "해보다 이른 걸음", "새벽 5~7시에 5번 기록하기", Reward.Shape(13)) { it.dawnPosts >= 5 },
        Achievement("shape_flame", "꺼지지 않는 등불", "7일 연속 기록하기", Reward.Shape(14)) { it.maxStreakDays >= 7 },
        Achievement("shape_key", "잠긴 문의 수집가", "별 도감에 다른 사람의 별 30개 모으기", Reward.Shape(15)) { it.starLogCount >= 30 },
        Achievement("shape_clover", "숫자가 나란히 선 순간", "11시 11분에 기록하기", Reward.Shape(16), hidden = true) { it.wishTimePost },
        Achievement(CROWN_ID, "대관식", "일반 업적 30개 달성하기", Reward.Shape(17)) { it.achievementsDone >= CROWN_TARGET },
        Achievement("shape_galaxy", "나선의 연대기", "다이어리 200개 작성하기", Reward.Shape(18)) { it.diariesCreated >= 200 },
        Achievement("shape_cat", "만월 아래 고양이", "보름달 뜬 밤(저녁 6시~새벽 6시)에 기록하기", Reward.Shape(19), hidden = true) { it.fullMoonPost },
        Achievement("shape_plane", "밤하늘 우체국", "댓글 50개 받기", Reward.Shape(20)) { it.commentsReceived >= 50 },
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
        Achievement("color_grad_dawn", "여명을 기다린 자", "자정~새벽(0~4시)에 10번 기록하기", Reward.StarColor(20)) { it.nightPosts >= 10 },
    )

    val all: List<Achievement> = titleAchievements + rewardAchievements

    fun byId(id: String?): Achievement? = all.firstOrNull { it.id == id }

    /** 주어진 통계로 현재 해금된 업적 id 집합. */
    fun unlockedIds(stats: UserStats): Set<String> =
        all.filter { it.unlocked(stats) }.map { it.id }.toSet()

    /** 왕관 판정용 — 왕관 자신을 뺀 일반 업적 달성 수를 채워 넣은 통계. */
    fun withAchievementCount(stats: UserStats): UserStats =
        stats.copy(achievementsDone = all.count { it.id != CROWN_ID && it.unlocked(stats) })
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
    val context = LocalContext.current
    val myDiaries by diaryViewModel.getMyDiaries(userId).collectAsState()
    // 전체(보이는) 다이어리 — 이웃 별 / 첫 발자국(눈밭) / 별 도감 수 판정용.
    val allDiaries by diaryViewModel.diaries.collectAsState()

    val viewedIds by remember(userId) {
        FirebaseViewedRepository().observeViewedIds(userId)
    }.collectAsState(initial = emptySet())

    val friends by remember(userId) {
        FirebaseFriendRepository().observeFriends(userId)
    }.collectAsState(initial = emptyList())

    // 친구 초대 보상 통계(체크리스트 31) — 초대해 가입시킨 수 / 내가 리딤했는지.
    val invitedCount by remember(userId) {
        com.chaminwoo.stary.data.repository.FirebaseInviteRepository().observeInvitedCount(userId)
    }.collectAsState(initial = 0)
    val redeemedInvite by remember(userId) {
        com.chaminwoo.stary.data.repository.FirebaseInviteRepository().observeRedeemed(userId)
    }.collectAsState(initial = false)

    // 별 도감 = 연 글(영구 해금 기록) ∩ 지금 보이는 남의 글. 상태 맵이라 해금되면 같이 갱신된다.
    // 해금 기록은 이 기기(로그인한 나)의 것이라, 남의 프로필(UserProfileScreen)에서는 쓰지 않는다.
    val isMe = userId == com.chaminwoo.stary.feature.auth.GoogleAuthHelper.currentUserId
    val unlockedIds = if (isMe) DiaryUnlockStore.unlockedIds(context) else emptySet()

    // 다이어리 좌표/시간 기반 파생 통계 (목록이 바뀔 때만 재계산)
    val derived = remember(myDiaries) { deriveOwnStats(myDiaries) }
    // 남의 별과 비교하는 통계 — 전체 목록이 바뀔 때만 재계산.
    val social = remember(myDiaries, allDiaries, userId) { deriveSocialStats(userId, myDiaries, allDiaries) }
    val starLogCount = remember(allDiaries, unlockedIds, userId) {
        allDiaries.count { it.userId != userId && it.id in unlockedIds }
    }

    // 열람 업적은 "다른 사람의 다이어리" 기준 — 내가 쓴 글의 열람 기록은 제외한다.
    val myDiaryIds = myDiaries.map { it.id }.toSet()
    val othersViewed = viewedIds.count { it !in myDiaryIds }
    return Achievements.withAchievementCount(
        derived.copy(
            diariesViewed = othersViewed,
            friends = friends.size,
            invitedFriends = invitedCount,
            redeemedInvite = redeemedInvite,
            firstInArea = social.first,
            nearNeighbor = social.second,
            starLogCount = starLogCount,
        )
    )
}

// ── 파생 통계 계산(iOS Achievements.computeStats 와 같은 규칙 — 값 drift 금지) ──

private const val DAY_MS = 86_400_000L
/** 보름달 판정: 기준 삭(2000-01-06 18:14 UTC, JD 2451550.26)부터 삭망월 29.530588853일 주기, 월령 14.77±1일. */
private const val SYNODIC_DAYS = 29.530588853
private const val NEW_MOON_JD = 2451550.26
private const val FULL_MOON_AGE = 14.765
private const val FULL_MOON_WINDOW = 1.0

private fun isFullMoon(ms: Long): Boolean {
    val jd = ms / DAY_MS.toDouble() + 2440587.5
    val age = ((jd - NEW_MOON_JD) % SYNODIC_DAYS + SYNODIC_DAYS) % SYNODIC_DAYS
    return abs(age - FULL_MOON_AGE) <= FULL_MOON_WINDOW
}

/** 두 좌표가 [meters] 안인지 — 위도/경도 차로 먼저 거른 뒤 정확한 거리를 잰다. */
private fun within(aLat: Double, aLng: Double, bLat: Double, bLng: Double, meters: Float): Boolean {
    val dLat = meters / 111_000.0
    if (abs(aLat - bLat) > dLat) return false
    val dLng = dLat / maxOf(0.01, cos(Math.toRadians(aLat)))
    if (abs(aLng - bLng) > dLng) return false
    return GeoUtils.distanceBetween(aLat, aLng, bLat, bLng) <= meters
}

private fun Diary.hasCoord() = latitude != 0.0 || longitude != 0.0

/** 내 다이어리만으로 정해지는 통계. */
private fun deriveOwnStats(myDiaries: List<Diary>): UserStats {
    val zone = ZoneId.systemDefault()
    val coords = myDiaries.filter { it.hasCoord() }
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
    val timed = myDiaries.filter { it.createdAt > 0 }
    val local = timed.map { Instant.ofEpochMilli(it.createdAt).atZone(zone) }
    val days = local.map { it.toLocalDate().toEpochDay() }
    val dayCounts = days.groupingBy { it }.eachCount()
    // 최장 연속 일수
    var streak = 0; var best = 0; var prev = Long.MIN_VALUE
    for (d in dayCounts.keys.sorted()) {
        streak = if (d == prev + 1) streak + 1 else 1
        best = maxOf(best, streak); prev = d
    }
    // 서로 1km 이상 떨어진 장소 — 작성 순서대로 기존 장소들과 모두 1km 이상이면 새 장소.
    val places = ArrayList<Diary>()
    coords.sortedBy { it.createdAt }.forEach { d ->
        if (places.none { within(it.latitude, it.longitude, d.latitude, d.longitude, 1000f) }) places += d
    }
    val cluster = coords.maxOfOrNull { c -> coords.count { within(c.latitude, c.longitude, it.latitude, it.longitude, 1000f) } } ?: 0
    val revisit = coords.any { a ->
        coords.any { b ->
            b.createdAt - a.createdAt > 30 * DAY_MS && within(a.latitude, a.longitude, b.latitude, b.longitude, 50f)
        }
    }
    // ── 히든 업적 판정용 파생 ──
    val keywordHit = myDiaries.any { it.title.contains(HiddenAchievements.SECRET_KEYWORD) }
    val regions = coords.flatMap { d ->
        HiddenAchievements.remoteLandmarks
            .filter { lm -> GeoUtils.distanceBetween(d.latitude, d.longitude, lm.lat, lm.lng) <= HiddenAchievements.REMOTE_RADIUS_M }
            .map { it.region }
    }.toSet()
    return UserStats(
        diariesCreated = myDiaries.size,
        likesReceived = myDiaries.sumOf { it.likeCount },
        viewsReceived = myDiaries.sumOf { it.viewCount },
        maxSpanMeters = maxSpan,
        maxLikesOnOne = myDiaries.maxOfOrNull { it.likeCount } ?: 0,
        distinctDays = dayCounts.size,
        nightPosts = local.count { it.hour in 0..4 },
        secretKeywordTitle = keywordHit,
        remoteRegions = regions,
        photoPosts = myDiaries.count { it.imageUrl.isNotEmpty() && it.videoUrl.isEmpty() },
        videoPosts = myDiaries.count { it.videoUrl.isNotEmpty() },
        longPosts = myDiaries.count { it.content.length >= 500 },
        shortPosts = myDiaries.count { it.content.trim().length <= 20 },
        privatePosts = myDiaries.count { it.visibilityType == "private" },
        maxStreakDays = best,
        dawnPosts = local.count { it.hour in 5..6 },
        maxPostsInDay = dayCounts.values.maxOrNull() ?: 0,
        distinctMonths = local.map { it.monthValue }.distinct().size,
        wishTimePost = local.any { (it.hour == 11 || it.hour == 23) && it.minute == 11 },
        fullMoonPost = timed.indices.any { i ->
            val h = local[i].hour
            (h >= 18 || h < 6) && isFullMoon(timed[i].createdAt)
        },
        newYearPost = local.any { it.monthValue == 1 && it.dayOfMonth == 1 },
        christmasPost = local.any { it.monthValue == 12 && (it.dayOfMonth == 24 || it.dayOfMonth == 25) },
        distinctPlaces = places.size,
        maxLocalCluster = cluster,
        revisitedSpot = revisit,
        maxViewsOnOne = myDiaries.maxOfOrNull { it.viewCount } ?: 0,
        commentsReceived = myDiaries.sumOf { it.commentCount },
        distinctShapes = myDiaries.map { it.starType }.distinct().size,
        distinctColors = myDiaries.map { it.starColor }.distinct().size,
    )
}

/**
 * 남의 별과 비교하는 통계 → (첫 발자국 수, 이웃 별 여부).
 *  - 첫 발자국: 내 별 1km 안에 **그보다 먼저** 남겨진 남의 별이 없던 경우(나중에 누가 옆에 와도 유지된다).
 *  - 이웃 별: 내 별 100m 안에 남의 별이 있다(선후 무관).
 */
private fun deriveSocialStats(userId: String, myDiaries: List<Diary>, allDiaries: List<Diary>): Pair<Int, Boolean> {
    val mine = myDiaries.filter { it.hasCoord() }
    // 전체 목록이 아직 안 왔으면(빈 목록) 판정하지 않는다 — 내 글만 먼저 온 순간 "주변에 아무도 없음"으로
    // 오판해 눈밭 업적이 잘못 해금·팝업되는 걸 막는다.
    if (mine.isEmpty() || allDiaries.isEmpty()) return 0 to false
    val others = allDiaries.filter { it.userId != userId && it.hasCoord() }
    val first = mine.count { m ->
        others.none { o -> o.createdAt < m.createdAt && within(m.latitude, m.longitude, o.latitude, o.longitude, 1000f) }
    }
    val near = mine.any { m -> others.any { o -> within(m.latitude, m.longitude, o.latitude, o.longitude, 100f) } }
    return first to near
}
