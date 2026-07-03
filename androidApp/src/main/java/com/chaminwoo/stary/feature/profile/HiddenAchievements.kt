package com.chaminwoo.stary.feature.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 히든 업적 — **앱 전체에서 단 한 사람만** 달성할 수 있는 특별 업적.
 *
 * 일반 업적([Achievement])과 다른 점:
 *  - 선착순: 조건을 가장 먼저 만족해 선점(Firestore 트랜잭션)한 1명만 달성자.
 *  - 표시 규칙: 달성 전에도 **칭호·아이콘·이펙트는 항상 노출**하되 **조건은 `???`** 로 가린다.
 *    누군가 달성하면 조건이 공개되고 옆에 `달성자: 이름` 이 붙는다.
 *  - 보상: 칭호 + 프로필에 뜨는 **전용 아이콘 + 파티클 이펙트**.
 *
 * ⚠️ iOS(`iosApp/.../Core/HiddenAchievements.swift`)와 정의(조건/보상/판정)를 항상 동일하게 유지한다.
 */

/** 히든 업적의 프로필 아이콘. (Material 아이콘 + 대표색) */
enum class HiddenIcon(val vector: ImageVector, val color: Color) {
    COMET(Icons.Filled.AutoAwesome, Color(0xFF9BE7FF)),
    GLACIER(Icons.Filled.AcUnit, Color(0xFFCFE8FF)),
    DESERT(Icons.Filled.WbSunny, Color(0xFFF2C46B)),
    TRENCH(Icons.Filled.Waves, Color(0xFF4FC3E0)),
    TRIANGLE(Icons.Filled.ChangeHistory, Color(0xFF9AE6C4)),
    CROWN(Icons.Filled.WorkspacePremium, Color(0xFFFFD86F)),
    TRICKSTER(Icons.Filled.Bolt, Color(0xFFB388FF)),
    LONE_EYE(Icons.Filled.RemoveRedEye, Color(0xFF9AA7B0)),
    HEART(Icons.Filled.Favorite, Color(0xFFFF6FA3)),
    BATON(Icons.Filled.MusicNote, Color(0xFF7CE0C0)),
    GLOBE(Icons.Filled.Public, Color(0xFF6EE7B7)),
}

/** 프로필 아이콘 주변에 흩뿌려지는 파티클/이펙트 종류. */
enum class ParticleEffect { STARDUST, SNOW, AURORA, EMBER, SHADOW, HEART, MUSIC, ORBIT, BUBBLE }

/** 자동 판정에 쓰는 컨텍스트. */
data class HiddenContext(
    val stats: UserStats,
    /** 히든을 제외한 모든 일반 업적을 달성했는가. */
    val allNormalDone: Boolean,
)

/**
 * 히든 업적 정의.
 * [auto] 가 null 이면 이벤트형(화면에서 직접 [com.chaminwoo.stary.data.repository.HiddenAchievementRepository.claim] 호출),
 * null 이 아니면 통계로 자동 판정한다.
 */
data class HiddenAchievement(
    val id: String,
    val title: String,        // 칭호 (= 업적 이름)
    val condition: String,    // 달성 후 공개되는 조건 문구
    val icon: HiddenIcon,
    val effect: ParticleEffect,
    val auto: ((HiddenContext) -> Boolean)? = null,
)

/** 도달하기 어려운 곳 후보. 같은 [region] 은 하나의 히든 업적으로 묶인다. */
data class RemoteLandmark(val region: String, val name: String, val lat: Double, val lng: Double)

/** 히든 업적 선점(달성) 기록. Firestore `hiddenAchievements/{id}`. */
data class HiddenClaim(
    val achieverId: String = "",
    val achieverName: String = "",
    val claimedAt: Long = 0L,
) {
    val claimed: Boolean get() = achieverId.isNotBlank()
}

object HiddenAchievements {
    // ── 달성 후에만 공개되는 조건 상수 ──
    /** 제목 키워드 히든. */
    const val SECRET_KEYWORD = "우주먼지"

    /** "도달하기 어려운 곳" 후보 — region 별로 묶는다(같은 region 은 한 업적). 반경 내에 별을 남기면 그 region 도달로 인정. */
    val remoteLandmarks: List<RemoteLandmark> = listOf(
        RemoteLandmark("glacier", "에베레스트", 27.9881, 86.9250),
        RemoteLandmark("glacier", "남극 대륙", -78.0, 0.0),
        RemoteLandmark("desert", "사하라 사막", 23.4162, 12.6628),
        RemoteLandmark("trench", "마리아나 해구", 11.35, 142.2),
        RemoteLandmark("triangle", "버뮤다 삼각지대", 25.0, -71.0),
    )
    /** 오지 인정 반경(미터) — 이 안이면 "도달하기 어려운 곳"으로 본다. */
    const val REMOTE_RADIUS_M = 300_000f

    val all: List<HiddenAchievement> = listOf(
        HiddenAchievement(
            id = "secret_word", title = "별의 암호",
            condition = "다이어리 제목에 ‘$SECRET_KEYWORD’ 를 넣기",
            icon = HiddenIcon.COMET, effect = ParticleEffect.STARDUST,
            auto = { it.stats.secretKeywordTitle },
        ),
        HiddenAchievement(
            id = "remote_place", title = "극야의 개척자",
            condition = "남극 · 에베레스트 둘 중 한 곳에 별 남기기",
            icon = HiddenIcon.GLACIER, effect = ParticleEffect.SNOW,
            auto = { "glacier" in it.stats.remoteRegions },
        ),
        HiddenAchievement(
            id = "place_desert", title = "태양의 잔영",
            condition = "사하라 사막에 별 남기기",
            icon = HiddenIcon.DESERT, effect = ParticleEffect.EMBER,
            auto = { "desert" in it.stats.remoteRegions },
        ),
        HiddenAchievement(
            id = "place_trench", title = "심연의 별",
            condition = "마리아나 해구에 별 남기기",
            icon = HiddenIcon.TRENCH, effect = ParticleEffect.BUBBLE,
            auto = { "trench" in it.stats.remoteRegions },
        ),
        HiddenAchievement(
            id = "place_triangle", title = "사라진 항로",
            condition = "버뮤다 삼각지대에 별 남기기",
            icon = HiddenIcon.TRIANGLE, effect = ParticleEffect.AURORA,
            auto = { "triangle" in it.stats.remoteRegions },
        ),
        HiddenAchievement(
            id = "all_rounder", title = "은하의 정점",
            condition = "히든을 제외한 모든 업적 달성하기",
            icon = HiddenIcon.CROWN, effect = ParticleEffect.AURORA,
            auto = { it.allNormalDone },
        ),
        HiddenAchievement(
            id = "cosmic_rascal", title = "별도둑",
            condition = "다른 사람의 다이어리 300개 열람하기",
            icon = HiddenIcon.TRICKSTER, effect = ParticleEffect.EMBER,
            auto = { it.stats.diariesViewed >= 300 },
        ),
        HiddenAchievement(
            id = "lone_observer", title = "홀로 빛나는 별",
            condition = "친구 없이 다이어리 50개 작성하기",
            icon = HiddenIcon.LONE_EYE, effect = ParticleEffect.SHADOW,
            auto = { it.stats.diariesCreated >= 50 && it.stats.friends == 0 },
        ),
        // ── 이벤트형(후속 라운드에서 화면 연동) ──
        HiddenAchievement(
            id = "heart_frenzy", title = "두근두근",
            condition = "프로필에서 나가지 않고 하트를 100번 두드리기",
            icon = HiddenIcon.HEART, effect = ParticleEffect.HEART,
            auto = null,
        ),
        HiddenAchievement(
            id = "melomaniac", title = "별들의 오케스트라",
            condition = "배경음악 화면에서 나가지 않고 모든 곡 감상하기",
            icon = HiddenIcon.BATON, effect = ParticleEffect.MUSIC,
            auto = null,
        ),
        HiddenAchievement(
            id = "earth_pilgrim", title = "푸른 행성의 발자취",
            condition = "세계 유명 관광지에 별을 남기고 다른 사람이 그 별을 열람하기",
            icon = HiddenIcon.GLOBE, effect = ParticleEffect.ORBIT,
            auto = null,
        ),
    )

    fun byId(id: String?): HiddenAchievement? = all.firstOrNull { it.id == id }

    /** 통계상 자동 달성 조건을 만족한 히든 업적 id 집합. (선점 시도 대상) */
    fun satisfiedAutoIds(ctx: HiddenContext): Set<String> =
        all.filter { it.auto?.invoke(ctx) == true }.map { it.id }.toSet()
}

/** 장착 칭호 id → 표시 이름. 일반 업적 + 히든 업적을 통합 조회한다. */
fun equippedTitleName(id: String?): String? {
    if (id.isNullOrBlank()) return null
    return Achievements.byId(id)?.titleName ?: HiddenAchievements.byId(id)?.title
}
