package com.chaminwoo.stary.core.util

import android.content.Context
import com.chaminwoo.stary.R
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.HiddenAchievements

/**
 * 언어 전환 대응 이름 해석 — 음악 트랙명 + 칭호(일반/히든).
 *
 * 트랙/업적 정의 자체는 공용 데이터(iOS 와 id·판정 공유)라 한국어 원문을 유지하고,
 * **표시할 때만** 여기서 id → string resource 로 해석한다(ko/en/ja).
 * 매핑에 없는 id 는 폴백(정의의 한국어 이름)을 그대로 쓴다.
 *
 * ⚠️ 새 트랙/칭호를 추가하면 이 매핑과 strings.xml(ko/en/ja) 3벌에도 키를 추가할 것.
 */
object LocalizedNames {

    private val musicRes: Map<String, Int> = mapOf(
        "star_whisper" to R.string.music_star_whisper,
        "tiny_explorer" to R.string.music_tiny_explorer,
        "celestial_drift" to R.string.music_celestial_drift,
        "cosmic_funk" to R.string.music_cosmic_funk,
        "forgotten_galaxy" to R.string.music_forgotten_galaxy,
        "nebula_garden" to R.string.music_nebula_garden,
    )

    private val titleRes: Map<String, Int> = mapOf(
        // 일반 칭호 업적 (이름 = 칭호)
        "first_step" to R.string.title_first_step,
        "star_traveler" to R.string.title_star_traveler,
        "storyteller" to R.string.title_storyteller,
        "popular" to R.string.title_popular,
        "watched_star" to R.string.title_watched_star,
        "companion" to R.string.title_companion,
        "pilgrim" to R.string.title_pilgrim,
        "guide" to R.string.title_guide,
        // 히든 업적 칭호
        "secret_word" to R.string.title_secret_word,
        "remote_place" to R.string.title_remote_place,
        "place_desert" to R.string.title_place_desert,
        "place_trench" to R.string.title_place_trench,
        "place_triangle" to R.string.title_place_triangle,
        "all_rounder" to R.string.title_all_rounder,
        "cosmic_rascal" to R.string.title_cosmic_rascal,
        "lone_observer" to R.string.title_lone_observer,
        "heart_frenzy" to R.string.title_heart_frenzy,
        "melomaniac" to R.string.title_melomaniac,
        "earth_pilgrim" to R.string.title_earth_pilgrim,
    )

    /** 음악 트랙 표시명(현재 언어). 매핑에 없으면 [fallback]. */
    fun music(context: Context, trackId: String, fallback: String): String =
        musicRes[trackId]?.let { context.getString(it) } ?: fallback

    /** 업적 id → 칭호 표시명(현재 언어). 매핑에 없으면 [fallback]. */
    fun title(context: Context, achievementId: String?, fallback: String? = null): String? =
        titleRes[achievementId]?.let { context.getString(it) } ?: fallback

    /**
     * 장착 칭호 id → 표시명(현재 언어). 일반+히든 통합.
     * (기존 [com.chaminwoo.stary.feature.profile.equippedTitleName] 의 로케일 대응판)
     */
    fun equippedTitle(context: Context, id: String?): String? {
        if (id.isNullOrBlank()) return null
        val fallback = Achievements.byId(id)?.titleName ?: HiddenAchievements.byId(id)?.title
        return title(context, id, fallback)
    }
}
