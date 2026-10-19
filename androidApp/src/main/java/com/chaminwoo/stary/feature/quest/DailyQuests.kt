package com.chaminwoo.stary.feature.quest

import androidx.annotation.StringRes
import com.chaminwoo.stary.R

/**
 * 일일 퀘스트 정의(2026-10-19) — 하루 3개, 앱 기능을 하나씩 써 보게 하는 튜토리얼 성격.
 * 보상 = 열쇠([QuestStore.keys]) — 멀리 있는(100m 밖) 남의 별을 여는 데 쓴다(DiaryLock.kt).
 *
 * ⚠️ iOS `DailyQuests.swift` 와 **id·이벤트·목표 수·시간대·풀(pool)·뽑는 식이 같아야 한다** —
 *    같은 계정을 두 기기에서 쓰면 서버의 그날 목록을 같이 쓰는데, 먼저 만든 쪽 식이 다르면 목록이 달라진다.
 */
enum class QuestEvent {
    TUTORIAL_STAR,      // 웰컴(튜토리얼) 별 게시물 열기
    UPLOAD,             // 별(다이어리) 올리기 — 시간대 퀘스트도 이 이벤트(올린 시각으로 판정)
    UPLOAD_MEDIA,       // 사진/영상이 있는 별 올리기
    VIEW_OTHER,         // 남의 별 상세 열기(잠겨 있어도 — "들여다보기")
    LIKE,               // 좋아요 누르기(취소는 제외)
    COMMENT,            // 댓글 달기
    EQUIP_TITLE,        // 칭호 장착
    PROFILE_EDIT,       // 닉네임 또는 프로필 사진 변경
    PIN_STAR,           // 프로필에 별 걸기
    CONSTELLATION,      // 별자리 선 켜기
    FILTER,             // 지도 필터 켜기
    GLOBE,              // 지구(글로브) 보기 진입
    METEOR,             // 글로브에서 유성 보기
    VISIT_ACHIEVEMENTS, // 업적 화면
    VISIT_STARLOG,      // 별 도감 화면
    VISIT_PROFILE,      // 남의 프로필 화면
    FRIEND_REQUEST,     // 친구 요청 보내기
    SHARE,              // 공유 영상 만들기
    MUSIC,              // 배경음악 바꾸기
    FOCUS,              // 지도를 가만히 두어 집중 모드 들어가기
    UNLOCK_KEY,         // 열쇠로 별 열기
}

/**
 * @param hours 로컬 "시"(양끝 포함) — 24 를 넘으면 자정을 넘긴다(21..25 = 오후 9:00~새벽 1:59). null = 언제든.
 * @param group 같은 group 은 하루에 하나만(올리기 계열이 3개 다 나오지 않게).
 */
data class QuestDef(
    val id: String,
    val event: QuestEvent,
    val target: Int = 1,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val hours: IntRange? = null,
    val group: String? = null,
) {
    fun matchesHour(hour: Int): Boolean {
        val h = hours ?: return true
        // 21..25 처럼 자정을 넘기면 0~1시를 24~25 로 본다.
        val shifted = if (hour < h.first && h.last >= 24) hour + 24 else hour
        return shifted in h
    }
}

object DailyQuests {
    /** 퀘스트 1개 보상(열쇠). */
    const val REWARD_KEYS = 1
    /** 하루 3개를 모두 받으면 주는 보너스 열쇠. */
    const val BONUS_KEYS = 1
    const val PER_DAY = 3

    private const val G_UPLOAD = "upload"

    val TUTORIAL = QuestDef("tutorial_star", QuestEvent.TUTORIAL_STAR, title = R.string.quest_tutorial_star, hint = R.string.quest_tutorial_star_hint)
    val UPLOAD = QuestDef("upload", QuestEvent.UPLOAD, title = R.string.quest_upload, hint = R.string.quest_upload_hint, group = G_UPLOAD)
    val VIEW_OTHER = QuestDef("view_other", QuestEvent.VIEW_OTHER, title = R.string.quest_view_other, hint = R.string.quest_view_other_hint)
    val LIKE = QuestDef("like", QuestEvent.LIKE, title = R.string.quest_like, hint = R.string.quest_like_hint)

    /** 2~4일째 — 기본 기능. */
    val BASIC = listOf(
        UPLOAD,
        LIKE,
        QuestDef("view_other3", QuestEvent.VIEW_OTHER, 3, R.string.quest_view_other3, R.string.quest_view_other_hint),
        QuestDef("comment", QuestEvent.COMMENT, title = R.string.quest_comment, hint = R.string.quest_comment_hint),
        QuestDef("equip_title", QuestEvent.EQUIP_TITLE, title = R.string.quest_equip_title, hint = R.string.quest_equip_title_hint),
        QuestDef("profile_edit", QuestEvent.PROFILE_EDIT, title = R.string.quest_profile_edit, hint = R.string.quest_profile_edit_hint),
        QuestDef("pin_star", QuestEvent.PIN_STAR, title = R.string.quest_pin_star, hint = R.string.quest_pin_star_hint),
        QuestDef("constellation", QuestEvent.CONSTELLATION, title = R.string.quest_constellation, hint = R.string.quest_constellation_hint),
        QuestDef("filter", QuestEvent.FILTER, title = R.string.quest_filter, hint = R.string.quest_filter_hint),
        QuestDef("globe", QuestEvent.GLOBE, title = R.string.quest_globe, hint = R.string.quest_globe_hint),
        QuestDef("achievements", QuestEvent.VISIT_ACHIEVEMENTS, title = R.string.quest_achievements, hint = R.string.quest_achievements_hint),
        QuestDef("user_profile", QuestEvent.VISIT_PROFILE, title = R.string.quest_user_profile, hint = R.string.quest_user_profile_hint),
    )

    /** 5일째부터 기본 풀에 더해진다. */
    val ADVANCED = listOf(
        QuestDef("like3", QuestEvent.LIKE, 3, R.string.quest_like3, R.string.quest_like_hint),
        QuestDef("upload_morning", QuestEvent.UPLOAD, title = R.string.quest_upload_morning, hint = R.string.quest_upload_morning_hint, hours = 5..10, group = G_UPLOAD),
        QuestDef("upload_night", QuestEvent.UPLOAD, title = R.string.quest_upload_night, hint = R.string.quest_upload_night_hint, hours = 21..25, group = G_UPLOAD),
        QuestDef("upload_media", QuestEvent.UPLOAD_MEDIA, title = R.string.quest_upload_media, hint = R.string.quest_upload_media_hint, group = G_UPLOAD),
        QuestDef("meteor", QuestEvent.METEOR, title = R.string.quest_meteor, hint = R.string.quest_meteor_hint),
        QuestDef("friend_request", QuestEvent.FRIEND_REQUEST, title = R.string.quest_friend_request, hint = R.string.quest_friend_request_hint),
        QuestDef("share", QuestEvent.SHARE, title = R.string.quest_share, hint = R.string.quest_share_hint),
        QuestDef("music", QuestEvent.MUSIC, title = R.string.quest_music, hint = R.string.quest_music_hint),
        QuestDef("star_log", QuestEvent.VISIT_STARLOG, title = R.string.quest_star_log, hint = R.string.quest_star_log_hint),
        QuestDef("focus", QuestEvent.FOCUS, title = R.string.quest_focus, hint = R.string.quest_focus_hint),
        QuestDef("use_key", QuestEvent.UNLOCK_KEY, title = R.string.quest_use_key, hint = R.string.quest_use_key_hint),
    )

    private val ALL: Map<String, QuestDef> =
        (listOf(TUTORIAL, VIEW_OTHER) + BASIC + ADVANCED).associateBy { it.id }

    fun byId(id: String): QuestDef? = ALL[id]

    /**
     * 그날의 퀘스트 3개.
     * - 1일째: 웰컴 별(아직 이 기기에 떠 있을 때만, 아니면 좋아요) · 별 올리기 · 남의 별 열어 보기.
     * - 2~4일째: [BASIC] 에서, 5일째부터: [BASIC] + [ADVANCED] 에서 무작위 3개(어제 것과 같은 group 은 하루 1개).
     * 무작위는 `uid|날짜|일차` 시드 — 두 기기가 동시에 만들어도 같은 목록이 나온다.
     */
    fun pick(uid: String, day: String, dayNo: Int, previous: List<String>, tutorialAvailable: Boolean): List<String> {
        if (dayNo <= 1) {
            return listOf(if (tutorialAvailable) TUTORIAL.id else LIKE.id, UPLOAD.id, VIEW_OTHER.id)
        }
        val pool = (if (dayNo <= 4) BASIC else BASIC + ADVANCED).toMutableList()
        val rng = SplitMix64(fnv1a("$uid|$day|$dayNo"))
        // Fisher–Yates — iOS 와 같은 순서로 섞는다.
        for (i in pool.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = pool[i]; pool[i] = pool[j]; pool[j] = t
        }
        val picked = mutableListOf<QuestDef>()
        // 어제 나온 건 되도록 빼고, 모자라면 다시 넣는다.
        for (allowPrevious in listOf(false, true)) {
            for (q in pool) {
                if (picked.size >= PER_DAY) break
                if (q in picked) continue
                if (!allowPrevious && q.id in previous) continue
                if (q.group != null && picked.any { it.group == q.group }) continue
                picked += q
            }
        }
        return picked.map { it.id }
    }

    /** FNV-1a 32bit(UTF-8) — iOS `DailyQuests.fnv1a` 와 같은 값. */
    internal fun fnv1a(s: String): Long {
        var h = 0x811C9DC5.toInt()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toInt() and 0xFF)
            h *= 16777619
        }
        return h.toLong() and 0xFFFFFFFFL
    }

    internal class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /** 0 until [n] — `(next >>> 1) % n`(부호 없는 값으로 계산해 iOS UInt64 와 같다). */
        fun nextInt(n: Int): Int = ((next() ushr 1) % n.toLong()).toInt()
    }
}
