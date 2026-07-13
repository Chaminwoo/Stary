package com.chaminwoo.stary.shared.config

/**
 * 주간 개척 퀘스트(체크리스트 32) — 매주 세계에서 나라 1개가 결정적으로 뽑히고,
 * 그 나라에 "처음으로" 다이어리를 올린 사람이 `pioneer_{code}` 칭호(○○ 개척자)를 가진다.
 *
 * 서버 없이 동작하는 결정적 로테이션:
 *  - 나라 목록을 고정 시드로 셔플한 순열을 만들고, 주차(weekIndex)가 순열을 순서대로 가리킨다.
 *    → 모든 클라이언트가 같은 "이번 주 나라"를 계산한다(값 drift 없음).
 *  - 아직 개척되지 않은 대상국은 계속 지도에 특별 표시로 남는다(여러 주 누적 가능).
 *  - 선점 기록은 Firestore `pioneerClaims/{countryCode}` create-only 문서 — 전 세계 단 1명(서버 강제).
 *
 * ⚠️ iOS `PioneerQuest.swift` 와 목록 순서/시드/주차 계산이 완전히 같아야 한다(결정성).
 */
object PioneerQuest {

    /** Firestore 컬렉션 — 문서 id = ISO 국가 코드, { userId, userName, weekIndex, createdAt }. */
    const val COLLECTION = "pioneerClaims"

    /** 장착 칭호 id 접두사 — equippedTitle = "pioneer_KR" 형태. */
    const val TITLE_PREFIX = "pioneer_"

    /** 퀘스트 시작 주(월요일 00:00 UTC, 2026-07-06). 이전 시각이면 weekIndex = 0. */
    const val EPOCH_WEEK_START_MS: Long = 1_783_296_000_000L

    private const val WEEK_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 대상 나라(ISO 3166-1 alpha-2 코드 + 지도 표시용 대략 중심좌표). 순서 변경 금지(결정성). */
    data class Country(val code: String, val lat: Double, val lng: Double)

    val COUNTRIES: List<Country> = listOf(
        Country("KR", 36.5, 127.8), Country("JP", 36.2, 138.3), Country("CN", 35.9, 104.2),
        Country("TW", 23.7, 121.0), Country("MN", 46.9, 103.8), Country("VN", 14.1, 108.3),
        Country("TH", 15.9, 100.99), Country("PH", 12.9, 121.8), Country("MY", 4.2, 102.0),
        Country("SG", 1.35, 103.82), Country("ID", -0.8, 113.9), Country("IN", 20.6, 79.0),
        Country("NP", 28.4, 84.1), Country("LK", 7.9, 80.8), Country("KH", 12.6, 105.0),
        Country("LA", 19.9, 102.5), Country("MM", 21.9, 95.96), Country("BD", 23.7, 90.4),
        Country("PK", 30.4, 69.3), Country("KZ", 48.0, 66.9), Country("UZ", 41.4, 64.6),
        Country("AE", 23.4, 53.8), Country("SA", 23.9, 45.1), Country("QA", 25.35, 51.18),
        Country("IL", 31.0, 34.9), Country("TR", 38.96, 35.24), Country("GR", 39.1, 21.8),
        Country("IT", 41.9, 12.6), Country("ES", 40.5, -3.7), Country("PT", 39.4, -8.2),
        Country("FR", 46.2, 2.2), Country("DE", 51.2, 10.4), Country("NL", 52.1, 5.3),
        Country("BE", 50.5, 4.5), Country("CH", 46.8, 8.2), Country("AT", 47.5, 14.6),
        Country("CZ", 49.8, 15.5), Country("PL", 51.9, 19.1), Country("HU", 47.2, 19.5),
        Country("GB", 55.4, -3.4), Country("IE", 53.4, -8.2), Country("SE", 60.1, 18.6),
        Country("NO", 60.5, 8.5), Country("DK", 56.3, 9.5), Country("FI", 61.9, 25.7),
        Country("IS", 64.96, -19.0), Country("EE", 58.6, 25.0), Country("HR", 45.1, 15.2),
        Country("RO", 45.9, 25.0), Country("UA", 48.4, 31.2), Country("US", 39.8, -98.6),
        Country("CA", 56.1, -106.3), Country("MX", 23.6, -102.6), Country("GT", 15.8, -90.2),
        Country("CU", 21.5, -77.8), Country("BR", -14.2, -51.9), Country("AR", -38.4, -63.6),
        Country("CL", -35.7, -71.5), Country("PE", -9.2, -75.0), Country("CO", 4.6, -74.3),
        Country("EC", -1.8, -78.2), Country("BO", -16.3, -63.6), Country("UY", -32.5, -55.8),
        Country("AU", -25.3, 133.8), Country("NZ", -40.9, 174.9), Country("FJ", -17.7, 178.1),
        Country("EG", 26.8, 30.8), Country("MA", 31.8, -7.1), Country("ZA", -30.6, 22.9),
        Country("KE", -0.02, 37.9), Country("TZ", -6.4, 34.9), Country("ET", 9.1, 40.5),
        Country("GH", 7.9, -1.0), Country("NG", 9.1, 8.7), Country("SN", 14.5, -14.5),
        Country("MG", -18.8, 47.0), Country("RU", 61.5, 105.3), Country("GE", 42.3, 43.4),
        Country("AM", 40.1, 45.0),
    )

    /** 현재 시각(epoch ms)의 주차 인덱스(0부터). 시작 전이면 0. */
    fun weekIndex(nowMs: Long): Int {
        val diff = nowMs - EPOCH_WEEK_START_MS
        return if (diff <= 0) 0 else (diff / WEEK_MS).toInt()
    }

    /**
     * 고정 시드 셔플 순열 — xorshift64 로 Fisher-Yates. 코드가 곧 스펙(iOS 와 동일 구현 유지).
     * 순열이 고정이므로 한 사이클(나라 수) 안에서는 같은 나라가 두 번 나오지 않는다.
     */
    private val permutation: List<Country> by lazy {
        val list = COUNTRIES.toMutableList()
        var state = 0x5DEECE66DL // 고정 시드
        fun nextRand(): Long {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return state
        }
        for (i in list.size - 1 downTo 1) {
            val j = ((nextRand() ushr 1) % (i + 1)).toInt()
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        list
    }

    /** 해당 주차의 대상 나라(결정적). 사이클을 넘어가면 순열을 반복한다. */
    fun countryForWeek(week: Int): Country = permutation[week % permutation.size]

    /**
     * 지금까지 등장한 대상국(0..현재 주차, 중복 제거) 중 아직 개척되지 않은 나라들.
     * 지도에 특별 표시할 대상 = 이 목록 − 개척된 나라(claimedCodes).
     */
    fun featuredCountries(nowMs: Long, claimedCodes: Set<String>): List<Country> {
        val week = weekIndex(nowMs)
        val seen = LinkedHashMap<String, Country>()
        for (w in 0..week) {
            val c = countryForWeek(w)
            if (c.code !in seen) seen[c.code] = c
        }
        return seen.values.filter { it.code !in claimedCodes }
    }

    /** 이번 주 대상 나라. */
    fun currentCountry(nowMs: Long): Country = countryForWeek(weekIndex(nowMs))

    /**
     * 다음 나라 교체(다음 주 경계)까지 남은 ms. 퀘스트 안내문의 "d일 h시간 후 나라 변경" 계산용.
     * (시작 전이면 첫 주가 끝나는 시각까지 — weekIndex 가 0 으로 고정되는 규칙과 일치.)
     */
    fun msUntilCountryChange(nowMs: Long): Long {
        val next = EPOCH_WEEK_START_MS + (weekIndex(nowMs) + 1L) * WEEK_MS
        return (next - nowMs).coerceAtLeast(0L)
    }

    /** [msUntilCountryChange] 를 (일, 시간) 으로 — 시간은 0..23 (분 이하 버림). */
    fun daysHoursUntilCountryChange(nowMs: Long): Pair<Int, Int> {
        val ms = msUntilCountryChange(nowMs)
        val totalHours = ms / (60L * 60 * 1000)
        return (totalHours / 24).toInt() to (totalHours % 24).toInt()
    }

    /** 칭호 id ↔ 국가 코드. */
    fun titleId(code: String): String = "$TITLE_PREFIX${code.uppercase()}"
    fun codeFromTitleId(titleId: String?): String? =
        titleId?.takeIf { it.startsWith(TITLE_PREFIX) }?.removePrefix(TITLE_PREFIX)?.takeIf { it.isNotBlank() }
}
