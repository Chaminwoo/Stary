import Foundation

/// 주간 개척 퀘스트(체크리스트 32) — KMP `shared/.../PioneerQuest.kt` 의 Swift 포팅.
///
/// ⚠️ 결정성 계약: 나라 목록 **순서**, 셔플 시드/알고리즘(xorshift64), 주차 계산이
/// Kotlin 구현과 완전히 같아야 양 플랫폼이 같은 "이번 주 나라"를 계산한다.
enum PioneerQuest {

    /// Firestore 컬렉션 — 문서 id = ISO 국가 코드, { userId, userName, weekIndex, createdAt }.
    static let collection = "pioneerClaims"

    /// 장착 칭호 id 접두사 — equippedTitle = "pioneer_KR" 형태.
    static let titlePrefix = "pioneer_"

    /// 퀘스트 시작 주(월요일 00:00 UTC, 2026-07-06). (Kotlin EPOCH_WEEK_START_MS)
    static let epochWeekStartMs: Int64 = 1_783_296_000_000

    private static let weekMs: Int64 = 7 * 24 * 60 * 60 * 1000

    struct Country {
        let code: String
        let lat: Double
        let lng: Double
    }

    /// 대상 나라 — Kotlin COUNTRIES 와 순서까지 동일해야 한다(변경 금지).
    static let countries: [Country] = [
        Country(code: "KR", lat: 36.5, lng: 127.8), Country(code: "JP", lat: 36.2, lng: 138.3), Country(code: "CN", lat: 35.9, lng: 104.2),
        Country(code: "TW", lat: 23.7, lng: 121.0), Country(code: "MN", lat: 46.9, lng: 103.8), Country(code: "VN", lat: 14.1, lng: 108.3),
        Country(code: "TH", lat: 15.9, lng: 100.99), Country(code: "PH", lat: 12.9, lng: 121.8), Country(code: "MY", lat: 4.2, lng: 102.0),
        Country(code: "SG", lat: 1.35, lng: 103.82), Country(code: "ID", lat: -0.8, lng: 113.9), Country(code: "IN", lat: 20.6, lng: 79.0),
        Country(code: "NP", lat: 28.4, lng: 84.1), Country(code: "LK", lat: 7.9, lng: 80.8), Country(code: "KH", lat: 12.6, lng: 105.0),
        Country(code: "LA", lat: 19.9, lng: 102.5), Country(code: "MM", lat: 21.9, lng: 95.96), Country(code: "BD", lat: 23.7, lng: 90.4),
        Country(code: "PK", lat: 30.4, lng: 69.3), Country(code: "KZ", lat: 48.0, lng: 66.9), Country(code: "UZ", lat: 41.4, lng: 64.6),
        Country(code: "AE", lat: 23.4, lng: 53.8), Country(code: "SA", lat: 23.9, lng: 45.1), Country(code: "QA", lat: 25.35, lng: 51.18),
        Country(code: "IL", lat: 31.0, lng: 34.9), Country(code: "TR", lat: 38.96, lng: 35.24), Country(code: "GR", lat: 39.1, lng: 21.8),
        Country(code: "IT", lat: 41.9, lng: 12.6), Country(code: "ES", lat: 40.5, lng: -3.7), Country(code: "PT", lat: 39.4, lng: -8.2),
        Country(code: "FR", lat: 46.2, lng: 2.2), Country(code: "DE", lat: 51.2, lng: 10.4), Country(code: "NL", lat: 52.1, lng: 5.3),
        Country(code: "BE", lat: 50.5, lng: 4.5), Country(code: "CH", lat: 46.8, lng: 8.2), Country(code: "AT", lat: 47.5, lng: 14.6),
        Country(code: "CZ", lat: 49.8, lng: 15.5), Country(code: "PL", lat: 51.9, lng: 19.1), Country(code: "HU", lat: 47.2, lng: 19.5),
        Country(code: "GB", lat: 55.4, lng: -3.4), Country(code: "IE", lat: 53.4, lng: -8.2), Country(code: "SE", lat: 60.1, lng: 18.6),
        Country(code: "NO", lat: 60.5, lng: 8.5), Country(code: "DK", lat: 56.3, lng: 9.5), Country(code: "FI", lat: 61.9, lng: 25.7),
        Country(code: "IS", lat: 64.96, lng: -19.0), Country(code: "EE", lat: 58.6, lng: 25.0), Country(code: "HR", lat: 45.1, lng: 15.2),
        Country(code: "RO", lat: 45.9, lng: 25.0), Country(code: "UA", lat: 48.4, lng: 31.2), Country(code: "US", lat: 39.8, lng: -98.6),
        Country(code: "CA", lat: 56.1, lng: -106.3), Country(code: "MX", lat: 23.6, lng: -102.6), Country(code: "GT", lat: 15.8, lng: -90.2),
        Country(code: "CU", lat: 21.5, lng: -77.8), Country(code: "BR", lat: -14.2, lng: -51.9), Country(code: "AR", lat: -38.4, lng: -63.6),
        Country(code: "CL", lat: -35.7, lng: -71.5), Country(code: "PE", lat: -9.2, lng: -75.0), Country(code: "CO", lat: 4.6, lng: -74.3),
        Country(code: "EC", lat: -1.8, lng: -78.2), Country(code: "BO", lat: -16.3, lng: -63.6), Country(code: "UY", lat: -32.5, lng: -55.8),
        Country(code: "AU", lat: -25.3, lng: 133.8), Country(code: "NZ", lat: -40.9, lng: 174.9), Country(code: "FJ", lat: -17.7, lng: 178.1),
        Country(code: "EG", lat: 26.8, lng: 30.8), Country(code: "MA", lat: 31.8, lng: -7.1), Country(code: "ZA", lat: -30.6, lng: 22.9),
        Country(code: "KE", lat: -0.02, lng: 37.9), Country(code: "TZ", lat: -6.4, lng: 34.9), Country(code: "ET", lat: 9.1, lng: 40.5),
        Country(code: "GH", lat: 7.9, lng: -1.0), Country(code: "NG", lat: 9.1, lng: 8.7), Country(code: "SN", lat: 14.5, lng: -14.5),
        Country(code: "MG", lat: -18.8, lng: 47.0), Country(code: "RU", lat: 61.5, lng: 105.3), Country(code: "GE", lat: 42.3, lng: 43.4),
        Country(code: "AM", lat: 40.1, lng: 45.0),
    ]

    /// 현재 시각(epoch ms)의 주차 인덱스(0부터). 시작 전이면 0.
    static func weekIndex(nowMs: Int64) -> Int {
        let diff = nowMs - epochWeekStartMs
        return diff <= 0 ? 0 : Int(diff / weekMs)
    }

    /// 고정 시드 셔플 순열(xorshift64 Fisher-Yates) — Kotlin permutation 과 비트 단위 동일.
    private static let permutation: [Country] = {
        var list = countries
        var state: UInt64 = 0x5DEECE66D
        func nextRand() -> UInt64 {
            state ^= state << 13
            state ^= state >> 7
            state ^= state << 17
            return state
        }
        var i = list.count - 1
        while i >= 1 {
            let j = Int((nextRand() >> 1) % UInt64(i + 1))
            list.swapAt(i, j)
            i -= 1
        }
        return list
    }()

    /// 해당 주차의 대상 나라(결정적). 사이클을 넘어가면 순열을 반복한다.
    static func countryForWeek(_ week: Int) -> Country { permutation[week % permutation.count] }

    /// 지금까지 등장한 대상국(0..현재 주차, 중복 제거) 중 아직 개척되지 않은 나라들.
    static func featuredCountries(nowMs: Int64, claimedCodes: Set<String>) -> [Country] {
        let week = weekIndex(nowMs: nowMs)
        var seenCodes = Set<String>()
        var result: [Country] = []
        for w in 0...week {
            let c = countryForWeek(w)
            if !seenCodes.contains(c.code) {
                seenCodes.insert(c.code)
                if !claimedCodes.contains(c.code) { result.append(c) }
            }
        }
        return result
    }

    /// 이번 주 **활성 개척 대상국 1개** — 아직 개척되지 않은 나라(pool)에서 주차로 하나만 결정적으로 고른다.
    /// 과거 주의 나라를 누적하지 않고 항상 1개만 활성이다(모두 개척되면 nil). 개척된 나라는 pool 에서 빠지므로
    /// 자연히 "개척된 나라만 쌓이고" 미개척 나라 중에서만 매주 새로 뽑힌다. 지도 비콘/선점 판정 공용 기준.
    /// ⚠️ Kotlin `PioneerQuest.activeCountry` 와 로직이 동일해야 한다(결정성/파리티).
    static func activeCountry(nowMs: Int64, claimedCodes: Set<String>) -> Country? {
        let pool = permutation.filter { !claimedCodes.contains($0.code) }
        if pool.isEmpty { return nil }
        return pool[weekIndex(nowMs: nowMs) % pool.count]
    }

    /// 다음 나라 교체(다음 주 경계)까지 남은 ms — 안내문의 "d일 h시간 후 나라 변경" 계산용.
    static func msUntilCountryChange(nowMs: Int64) -> Int64 {
        let next = epochWeekStartMs + Int64(weekIndex(nowMs: nowMs) + 1) * weekMs
        return max(0, next - nowMs)
    }

    /// `msUntilCountryChange` 를 (일, 시간) 으로 — 시간은 0..23 (분 이하 버림).
    static func daysHoursUntilCountryChange(nowMs: Int64) -> (days: Int, hours: Int) {
        let totalHours = msUntilCountryChange(nowMs: nowMs) / (60 * 60 * 1000)
        return (Int(totalHours / 24), Int(totalHours % 24))
    }

    /// 칭호 id ↔ 국가 코드.
    static func titleId(_ code: String) -> String { "\(titlePrefix)\(code.uppercased())" }
    static func codeFromTitleId(_ titleId: String?) -> String? {
        guard let titleId, titleId.hasPrefix(titlePrefix) else { return nil }
        let code = String(titleId.dropFirst(titlePrefix.count))
        return code.isEmpty ? nil : code
    }
}
