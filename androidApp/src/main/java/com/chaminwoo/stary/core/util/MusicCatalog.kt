package com.chaminwoo.stary.core.util

/**
 * 배경음악 카탈로그(순수 데이터) — [MusicManager](재생) 와 음악 탭 UI 가 공유한다.
 *
 * - 음원은 `res/raw/` 에 평면으로 둔다(raw 하위 폴더는 Android 리소스로 인식 안 됨).
 * - 기본 음악([DEFAULT_ID]) 은 처음부터 해금. 나머지는 [unlockAchievementId] 업적 달성 시 해금.
 * - 색([colorArgb])·별 모양([starType]) 은 음악마다 달라 다이얼/별자리에 반영된다.
 *   (별자리 문양은 UI 쪽 MusicScreen 의 MUSIC_CONSTELLATIONS 가 id 로 매핑)
 */
object MusicCatalog {
    data class Track(
        val id: String,
        val displayName: String,
        val rawName: String,               // res/raw 리소스명(확장자 제외)
        val colorArgb: Long,               // 별자리/다이얼 테마색
        val starType: Int,                 // 다이얼 별 모양(StarStyle type 0..7)
        val unlockAchievementId: String?,  // null = 기본 해금
    )

    /** 기본(처음부터 사용 가능) 음악 = 별의 속삭임. */
    const val DEFAULT_ID = "star_whisper"

    val tracks: List<Track> = listOf(
        Track("star_whisper",    "별의 속삭임", "bgm_star_whisper",     0xFF6EE7B7, 1, null),
        Track("tiny_explorer",   "작은 탐험가", "bgm_tiny_explorer",    0xFFFFD27F, 4, "first_step"),
        Track("celestial_drift", "천상의 표류", "bgm_celestial_drift",  0xFF7FB7FF, 0, "storyteller"),
        Track("cosmic_funk",     "코스믹 펑크", "bgm_cosmic_funk",      0xFFFF9CC6, 2, "popular"),
        Track("forgotten_galaxy","잊혀진 은하", "bgm_forgotten_galaxy", 0xFFB89BFF, 7, "star_traveler"),
        Track("nebula_garden",   "성운의 정원", "bgm_nebula_garden",    0xFF9CE6A0, 5, "companion"),
    )

    fun byId(id: String?): Track? = tracks.firstOrNull { it.id == id }
    fun rawName(id: String?): String? = byId(id)?.rawName
    val default: Track get() = byId(DEFAULT_ID) ?: tracks.first()
    fun indexOf(id: String?): Int = tracks.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
