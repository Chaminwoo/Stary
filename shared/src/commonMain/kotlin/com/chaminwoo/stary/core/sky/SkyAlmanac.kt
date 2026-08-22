package com.chaminwoo.stary.core.sky

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 실제 하늘 상태 계산 — **태양 고도(여명·황혼)**.
 *
 * 지도의 밤하늘이 늘 똑같아서 "그림"처럼 보였다. 이 계산으로 지금 이 순간의 진짜 하늘
 * (해가 막 지고 있는지)을 지도에 반영한다.
 *
 * 외부 API·라이브러리 없이 순수 계산이다(네트워크/권한 불필요, 오프라인 동작).
 * 정밀 천문 계산이 아니라 **연출용 근사**다(태양 고도 ±0.5° 수준).
 *
 * ⚠️ iOS 는 `iosApp/Sources/Core/SkyAlmanac.swift` 에 **같은 수식·같은 상수**로 복제돼 있다.
 *    한쪽을 고치면 반드시 반대쪽도 함께 고칠 것(값 drift 금지 — CLAUDE.md §1.5).
 */
object SkyAlmanac {

    // ── 태양(여명·황혼) ───────────────────────────────────────────────────

    /**
     * 태양 고도(도). 음수 = 지평선 아래.
     *  -0.83° 이상 = 낮 / -6° ~ -0.83° = 상용박명(가장 붉다) / -18° 이하 = 완전한 밤.
     *
     * 저정밀 태양 위치(NOAA 근사) — 연출용으로 충분(±0.5°).
     */
    fun sunAltitudeDeg(nowMs: Long, latitude: Double, longitude: Double): Double {
        // 율리우스일 기준 J2000 경과일.
        val jd = nowMs / 86_400_000.0 + 2440587.5
        val n = jd - 2451545.0

        val meanLong = norm360(280.460 + 0.9856474 * n)
        val meanAnom = norm360(357.528 + 0.9856003 * n) * DEG
        val eclLong = (meanLong + 1.915 * sin(meanAnom) + 0.020 * sin(2 * meanAnom)) * DEG
        val obliquity = (23.439 - 0.0000004 * n) * DEG

        val ra = atan2(cos(obliquity) * sin(eclLong), cos(eclLong))          // 라디안
        val dec = asin(sin(obliquity) * sin(eclLong))

        // 그리니치 항성시(시간) → 지방 항성시 → 시간각.
        val gmstHours = (18.697374558 + 24.06570982441908 * n) % 24.0
        val gmst = if (gmstHours < 0) gmstHours + 24.0 else gmstHours
        val lstDeg = norm360(gmst * 15.0 + longitude)
        val hourAngle = (lstDeg - ra / DEG) * DEG

        val lat = latitude * DEG
        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)
        return asin(sinAlt.coerceIn(-1.0, 1.0)) / DEG
    }

    /**
     * 여명/황혼 강도 0..1 — 지평선 근처 따뜻한 빛의 세기.
     * 태양 고도 +6°(밝은 낮의 끝) ~ -12°(항해박명 끝) 사이에서만 0 보다 크고, -2° 부근에서 가장 강하다.
     */
    fun twilightStrength(sunAltitudeDeg: Double): Double {
        if (sunAltitudeDeg > 6.0 || sunAltitudeDeg < -12.0) return 0.0
        // -2° 를 정점으로 하는 삼각 프로파일.
        val peak = -2.0
        val span = if (sunAltitudeDeg > peak) 8.0 else 10.0
        return (1.0 - abs(sunAltitudeDeg - peak) / span).coerceIn(0.0, 1.0)
    }

    private const val DEG = PI / 180.0

    /** 각도를 0..360 으로 정규화. */
    private fun norm360(deg: Double): Double {
        val r = deg - 360.0 * floor(deg / 360.0)
        return if (r < 0) r + 360.0 else r
    }
}
