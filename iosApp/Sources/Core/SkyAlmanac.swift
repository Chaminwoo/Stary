import Foundation

/// 실제 하늘 상태 계산 — **태양 고도(여명·황혼)**.
///
/// ⚠️ Android `shared/.../core/sky/SkyAlmanac.kt` 와 **같은 수식·같은 상수**다.
///    한쪽을 고치면 반드시 반대쪽도 함께 고칠 것(값 drift 금지 — CLAUDE.md §1.5).
///
/// 외부 API·라이브러리 없이 순수 계산이다(네트워크/권한 불필요, 오프라인 동작).
/// 정밀 천문 계산이 아니라 **연출용 근사**다(태양 고도 ±0.5° 수준).
enum SkyAlmanac {

    // MARK: - 태양(여명·황혼)

    /// 태양 고도(도). 음수 = 지평선 아래. 저정밀 근사(연출용, ±0.5°).
    static func sunAltitudeDeg(nowMs: Double, latitude: Double, longitude: Double) -> Double {
        // 율리우스일 기준 J2000 경과일.
        let jd = nowMs / 86_400_000 + 2_440_587.5
        let n = jd - 2_451_545.0

        let meanLong = norm360(280.460 + 0.9856474 * n)
        let meanAnom = norm360(357.528 + 0.9856003 * n) * deg
        let eclLong = (meanLong + 1.915 * sin(meanAnom) + 0.020 * sin(2 * meanAnom)) * deg
        let obliquity = (23.439 - 0.0000004 * n) * deg

        let ra = atan2(cos(obliquity) * sin(eclLong), cos(eclLong))   // 라디안
        let dec = asin(sin(obliquity) * sin(eclLong))

        // 그리니치 항성시(시간) → 지방 항성시 → 시간각.
        let gmstHours = (18.697374558 + 24.06570982441908 * n).truncatingRemainder(dividingBy: 24)
        let gmst = gmstHours < 0 ? gmstHours + 24 : gmstHours
        let lstDeg = norm360(gmst * 15 + longitude)
        let hourAngle = (lstDeg - ra / deg) * deg

        let lat = latitude * deg
        let sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)
        return asin(min(max(sinAlt, -1), 1)) / deg
    }

    /// 여명/황혼 강도 0..1 — +6° ~ -12° 구간에서만 0 보다 크고, -2° 부근이 가장 강하다.
    static func twilightStrength(sunAltitudeDeg alt: Double) -> Double {
        if alt > 6 || alt < -12 { return 0 }
        let peak = -2.0
        let span = alt > peak ? 8.0 : 10.0
        return min(max(1 - abs(alt - peak) / span, 0), 1)
    }

    private static let deg = Double.pi / 180

    /// 각도를 0..360 으로 정규화.
    private static func norm360(_ d: Double) -> Double {
        let r = d - 360 * floor(d / 360)
        return r < 0 ? r + 360 : r
    }
}
