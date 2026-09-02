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
        // 친구 초대 보상 칭호(체크리스트 31)
        "invite_bond" to R.string.title_invite_bond,
        "invite_beacon" to R.string.title_invite_beacon,
        "invite_flock" to R.string.title_invite_flock,
        // 별 모양/색 보상 업적 — 칭호는 아니지만 업적 이름도 로케일 해석 대상.
        "shape_flower" to R.string.ach_name_shape_flower,
        "shape_gem" to R.string.ach_name_shape_gem,
        "shape_moon" to R.string.ach_name_shape_moon,
        "shape_planet" to R.string.ach_name_shape_planet,
        "shape_farjourney" to R.string.ach_name_shape_farjourney,
        "shape_border" to R.string.ach_name_shape_border,
        "color_passion" to R.string.ach_name_color_passion,
        "color_sunset" to R.string.ach_name_color_sunset,
        "color_steady" to R.string.ach_name_color_steady,
        "color_abyss" to R.string.ach_name_color_abyss,
        "color_wanderer" to R.string.ach_name_color_wanderer,
        "color_midnight" to R.string.ach_name_color_midnight,
        "color_life" to R.string.ach_name_color_life,
        "color_gold" to R.string.ach_name_color_gold,
        "color_nebula" to R.string.ach_name_color_nebula,
        "color_grad_aurora" to R.string.ach_name_color_grad_aurora,
        "color_grad_emerald" to R.string.ach_name_color_grad_emerald,
        "color_grad_sunset" to R.string.ach_name_color_grad_sunset,
        "color_grad_glacier" to R.string.ach_name_color_grad_glacier,
        "color_grad_dawn" to R.string.ach_name_color_grad_dawn,
    )

    /** 업적 달성 조건 문구 — 일반 + 별 모양/색 보상 + 히든 전부. */
    private val conditionRes: Map<String, Int> = mapOf(
        "first_step" to R.string.ach_cond_first_step,
        "star_traveler" to R.string.ach_cond_star_traveler,
        "storyteller" to R.string.ach_cond_storyteller,
        "popular" to R.string.ach_cond_popular,
        "watched_star" to R.string.ach_cond_watched_star,
        "companion" to R.string.ach_cond_companion,
        "pilgrim" to R.string.ach_cond_pilgrim,
        "guide" to R.string.ach_cond_guide,
        "invite_bond" to R.string.ach_cond_invite_bond,
        "invite_beacon" to R.string.ach_cond_invite_beacon,
        "invite_flock" to R.string.ach_cond_invite_flock,
        "shape_flower" to R.string.ach_cond_shape_flower,
        "shape_gem" to R.string.ach_cond_shape_gem,
        "shape_moon" to R.string.ach_cond_shape_moon,
        "shape_planet" to R.string.ach_cond_shape_planet,
        "shape_farjourney" to R.string.ach_cond_shape_farjourney,
        "shape_border" to R.string.ach_cond_shape_border,
        "color_passion" to R.string.ach_cond_color_passion,
        "color_sunset" to R.string.ach_cond_color_sunset,
        "color_steady" to R.string.ach_cond_color_steady,
        "color_abyss" to R.string.ach_cond_color_abyss,
        "color_wanderer" to R.string.ach_cond_color_wanderer,
        "color_midnight" to R.string.ach_cond_color_midnight,
        "color_life" to R.string.ach_cond_color_life,
        "color_gold" to R.string.ach_cond_color_gold,
        "color_nebula" to R.string.ach_cond_color_nebula,
        "color_grad_aurora" to R.string.ach_cond_color_grad_aurora,
        "color_grad_emerald" to R.string.ach_cond_color_grad_emerald,
        "color_grad_sunset" to R.string.ach_cond_color_grad_sunset,
        "color_grad_glacier" to R.string.ach_cond_color_grad_glacier,
        "color_grad_dawn" to R.string.ach_cond_color_grad_dawn,
        // 히든 — 조건 문구는 달성 후에만 노출되지만 문구 자체는 동일하게 로케일 해석.
        // (secret_word 의 키워드 '우주먼지' 는 판정에 쓰이는 실제 입력값이라 번역하지 않고 그대로 둔다)
        "secret_word" to R.string.ach_cond_secret_word,
        "remote_place" to R.string.ach_cond_remote_place,
        "place_desert" to R.string.ach_cond_place_desert,
        "place_trench" to R.string.ach_cond_place_trench,
        "place_triangle" to R.string.ach_cond_place_triangle,
        "all_rounder" to R.string.ach_cond_all_rounder,
        "cosmic_rascal" to R.string.ach_cond_cosmic_rascal,
        "lone_observer" to R.string.ach_cond_lone_observer,
        "heart_frenzy" to R.string.ach_cond_heart_frenzy,
        "melomaniac" to R.string.ach_cond_melomaniac,
        "earth_pilgrim" to R.string.ach_cond_earth_pilgrim,
    )

    /** 음악 트랙 표시명(현재 언어). 매핑에 없으면 [fallback]. */
    fun music(context: Context, trackId: String, fallback: String): String =
        musicRes[trackId]?.let { context.getString(it) } ?: fallback

    /** 업적 id → 칭호 표시명(현재 언어). 매핑에 없으면 [fallback]. */
    fun title(context: Context, achievementId: String?, fallback: String? = null): String? =
        titleRes[achievementId]?.let { context.getString(it) } ?: fallback

    /** 업적 id → 달성 조건 문구(현재 언어). 매핑에 없으면 [fallback](정의의 한국어 원문). */
    fun condition(context: Context, achievementId: String?, fallback: String): String =
        conditionRes[achievementId]?.let { context.getString(it) } ?: fallback

    /**
     * 장착 칭호 id → 표시명(현재 언어). 일반+히든+개척 통합.
     * (기존 [com.chaminwoo.stary.feature.profile.equippedTitleName] 의 로케일 대응판)
     */
    fun equippedTitle(context: Context, id: String?): String? {
        if (id.isNullOrBlank()) return null
        // 개척 칭호(pioneer_{code}) — 국가명은 로케일 API 로 동적 표시(체크리스트 32).
        pioneerTitle(context, id)?.let { return it }
        val fallback = Achievements.byId(id)?.titleName ?: HiddenAchievements.byId(id)?.title
        return title(context, id, fallback)
    }

    /** 개척 칭호 표시명 — "대한민국 개척자" 형태. pioneer_ id 가 아니면 null. */
    fun pioneerTitle(context: Context, id: String?): String? {
        val code = com.chaminwoo.stary.shared.config.PioneerQuest.codeFromTitleId(id) ?: return null
        return context.getString(R.string.pioneer_title_format, countryName(code))
    }

    /** ISO 국가 코드 → 현재 언어 국가명(모르면 코드 그대로). */
    fun countryName(code: String): String =
        java.util.Locale("", code).displayCountry.ifBlank { code }
}
