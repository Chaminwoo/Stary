import Foundation

/// 언어 전환 대응 이름 해석 — 음악 트랙명 + 칭호(일반/히든).
/// Android `core.util.LocalizedNames` 패리티: 정의(id·판정)는 한국어 데이터를 유지하고
/// **표시할 때만** id → (ko, en, ja) 로 해석한다. 매핑에 없으면 폴백(한국어 원문).
///
/// ⚠️ 새 트랙/칭호를 추가하면 이 매핑과 Android strings.xml(ko/en/ja)에도 함께 추가할 것.
@MainActor
enum LocalizedNames {

    private static let musicTable: [String: (String, String, String)] = [
        "star_whisper":     ("별의 속삭임", "Star Whisper", "星のささやき"),
        "tiny_explorer":    ("작은 탐험가", "Tiny Explorer", "小さな探検家"),
        "celestial_drift":  ("천상의 표류", "Celestial Drift", "天上の漂流"),
        "cosmic_funk":      ("코스믹 펑크", "Cosmic Funk", "コズミック・ファンク"),
        "forgotten_galaxy": ("잊혀진 은하", "Forgotten Galaxy", "忘れられた銀河"),
        "nebula_garden":    ("성운의 정원", "Nebula Garden", "星雲の庭園"),
    ]

    private static let titleTable: [String: (String, String, String)] = [
        // 일반 칭호 업적 (이름 = 칭호)
        "first_step":    ("첫 발자국", "First Steps", "はじめの一歩"),
        "star_traveler": ("별의 여행자", "Star Traveler", "星の旅人"),
        "storyteller":   ("이야기꾼", "Storyteller", "語り部"),
        "popular":       ("인기쟁이", "Crowd Favorite", "人気者"),
        "watched_star":  ("주목받는 별", "Star of Attention", "注目の星"),
        "companion":     ("길동무", "Companion", "道連れ"),
        "pilgrim":       ("우주의 순례자", "Cosmic Pilgrim", "宇宙の巡礼者"),
        "guide":         ("별빛의 인도자", "Starlight Guide", "星明かりの導き手"),
        // 히든 업적 칭호
        "secret_word":    ("별의 암호", "Stellar Cipher", "星の暗号"),
        "remote_place":   ("극야의 개척자", "Pioneer of the Polar Night", "極夜の開拓者"),
        "place_desert":   ("태양의 잔영", "Afterglow of the Sun", "太陽の残照"),
        "place_trench":   ("심연의 별", "Star of the Abyss", "深淵の星"),
        "place_triangle": ("사라진 항로", "The Lost Route", "消えた航路"),
        "all_rounder":    ("은하의 정점", "Apex of the Galaxy", "銀河の頂点"),
        "cosmic_rascal":  ("별도둑", "Star Thief", "星泥棒"),
        "lone_observer":  ("홀로 빛나는 별", "Lone Shining Star", "孤高に輝く星"),
        "heart_frenzy":   ("두근두근", "Heartbeat", "ドキドキ"),
        "melomaniac":     ("별들의 오케스트라", "Orchestra of the Stars", "星々のオーケストラ"),
        "earth_pilgrim":  ("푸른 행성의 발자취", "Footprints on the Blue Planet", "青い惑星の足跡"),
        // 친구 초대 보상 칭호(체크리스트 31)
        "invite_bond":    ("별의 인연", "Bonded by Stars", "星の縁"),
        "invite_beacon":  ("별의 등대", "Star Beacon", "星の灯台"),
        "invite_flock":   ("별무리의 길잡이", "Guide of the Flock", "星団の道しるべ"),
    ]

    private static func pick(_ t: (String, String, String)) -> String {
        switch LocaleManager.shared.effectiveLanguage {
        case "en": return t.1
        case "ja": return t.2
        default:   return t.0
        }
    }

    /// 음악 트랙 표시명(현재 언어). 매핑에 없으면 폴백.
    static func music(_ id: String, fallback: String) -> String {
        guard let t = musicTable[id] else { return fallback }
        return pick(t)
    }

    /// 업적 id → 칭호 표시명(현재 언어). 매핑에 없으면 폴백.
    static func title(_ id: String?, fallback: String? = nil) -> String? {
        guard let id, let t = titleTable[id] else { return fallback }
        return pick(t)
    }

    /// 장착 칭호 id → 표시명(현재 언어). 일반+히든+개척 통합(Android `equippedTitle` 패리티).
    static func equippedTitle(_ id: String?) -> String? {
        guard let id, !id.isEmpty else { return nil }
        if let pioneer = pioneerTitle(id) { return pioneer }
        let fallback = Achievements.byId(id)?.titleName ?? HiddenAchievements.byId(id)?.title
        return title(id, fallback: fallback)
    }

    /// 개척 칭호(pioneer_{code}) 표시명 — "대한민국 개척자" 형태. 아니면 nil. (체크리스트 32)
    static func pioneerTitle(_ id: String?) -> String? {
        guard let code = PioneerQuest.codeFromTitleId(id) else { return nil }
        let country = countryName(code)
        switch LocaleManager.shared.effectiveLanguage {
        case "en": return "\(country) Pioneer"
        case "ja": return "\(country)開拓者"
        default:   return "\(country) 개척자"
        }
    }

    /// 개척 퀘스트 비콘 탭 안내문(Android pioneer_quest_toast 패리티).
    static func pioneerQuestMessage(_ code: String) -> String {
        let country = countryName(code)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let (d, h) = PioneerQuest.daysHoursUntilCountryChange(nowMs: nowMs)
        switch LocaleManager.shared.effectiveLanguage {
        case "en": return "Be the first to leave a star in \(country) and earn a special title.\n(Country changes in \(d)d \(h)h)"
        case "ja": return "\(country)で最初の星を残して特別な称号を手に入れましょう。\n(\(d)日\(h)時間後に国が変わります)"
        default:   return "\(country)에서 처음으로 별을 만들어 특별한 칭호를 얻으세요.\n(\(d)일 \(h)시간 후 나라 변경)"
        }
    }

    /// ISO 국가 코드 → 현재 언어 국가명(모르면 코드 그대로).
    static func countryName(_ code: String) -> String {
        let localeId: String
        switch LocaleManager.shared.effectiveLanguage {
        case "en": localeId = "en"
        case "ja": localeId = "ja"
        default:   localeId = "ko"
        }
        return Locale(identifier: localeId).localizedString(forRegionCode: code) ?? code
    }
}
