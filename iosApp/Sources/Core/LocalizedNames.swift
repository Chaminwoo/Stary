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
        // 별 모양/색 보상 업적 — 칭호는 아니지만 업적 이름도 로케일 해석 대상.
        "shape_flower":       ("별꽃을 피운 자", "Starflower Bloomer", "星花を咲かせた者"),
        "shape_gem":          ("결정의 시간", "Crystal Moment", "結晶の刻"),
        "shape_moon":         ("달의 인도자", "Guide of the Moon", "月の導き手"),
        "shape_planet":       ("나만의 행성", "My Own Planet", "自分だけの惑星"),
        "shape_farjourney":   ("머나먼 여정", "A Far Journey", "遥かなる旅路"),
        "shape_border":       ("국경을 넘어", "Crossing Borders", "国境を越えて"),
        "color_passion":      ("정열의 한 방", "A Burst of Passion", "情熱の一撃"),
        "color_sunset":       ("노을 수집가", "Sunset Collector", "夕焼けの収集家"),
        "color_steady":       ("꾸준한 관측자", "Steady Observer", "地道な観測者"),
        "color_abyss":        ("심연의 탐구자", "Explorer of the Abyss", "深淵の探求者"),
        "color_wanderer":     ("대지의 방랑자", "Wanderer of the Earth", "大地の放浪者"),
        "color_midnight":     ("심야의 관측자", "Midnight Observer", "深夜の観測者"),
        "color_life":         ("생명의 인연", "Bond of Life", "生命の縁"),
        "color_gold":         ("황금빛 발견", "Golden Discovery", "黄金の発見"),
        "color_nebula":       ("성운의 빛", "Light of the Nebula", "星雲の光"),
        "color_grad_aurora":  ("오로라의 주인", "Master of the Aurora", "オーロラの主"),
        "color_grad_emerald": ("수많은 벗", "Countless Friends", "数多の友"),
        "color_grad_sunset":  ("백 개의 별빛", "A Hundred Starlights", "百の星明かり"),
        "color_grad_glacier": ("은하의 정복자", "Conqueror of the Galaxy", "銀河の征服者"),
        "color_grad_dawn":    ("여명을 기다린 자", "One Who Awaited the Dawn", "夜明けを待った者"),
    ]

    /// 업적 달성 조건 문구 — 일반 + 별 모양/색 보상 + 히든 전부.
    /// (secret_word 의 키워드 '우주먼지' 는 판정에 쓰이는 실제 입력값이라 번역하지 않고 그대로 둔다)
    private static let conditionTable: [String: (String, String, String)] = [
        "first_step":    ("다이어리 1개 작성하기", "Write 1 diary entry", "日記を1件作成する"),
        "star_traveler": ("다른 사람의 다이어리 10개 열람하기", "View 10 other people's diary entries", "他の人の日記を10件閲覧する"),
        "storyteller":   ("다이어리 10개 작성하기", "Write 10 diary entries", "日記を10件作成する"),
        "popular":       ("좋아요 50개 받기", "Receive 50 likes", "いいねを50件もらう"),
        "watched_star":  ("총 조회수 100 달성하기", "Reach 100 total views", "累計閲覧数100を達成する"),
        "companion":     ("친구 3명 만들기", "Make 3 friends", "友達を3人作る"),
        "pilgrim":       ("다이어리 30개 작성하기", "Write 30 diary entries", "日記を30件作成する"),
        "guide":         ("좋아요 200개 받기", "Receive 200 likes", "いいねを200件もらう"),
        "invite_bond":   ("초대를 받아 Stary 에 합류하기", "Join Stary through an invite", "招待を受けてStaryに参加する"),
        "invite_beacon": ("친구 1명을 Stary 로 초대하기", "Invite 1 friend to Stary", "友達を1人Staryに招待する"),
        "invite_flock":  ("친구 5명을 Stary 로 초대하기", "Invite 5 friends to Stary", "友達を5人Staryに招待する"),
        "shape_flower":     ("좋아요 80개 받기", "Receive 80 likes", "いいねを80件もらう"),
        "shape_gem":        ("서로 다른 14일에 기록하기", "Post on 14 different days", "異なる14日に記録する"),
        "shape_moon":       ("총 조회수 500 달성하기", "Reach 500 total views", "累計閲覧数500を達成する"),
        "shape_planet":     ("서로 다른 30일에 기록하기", "Post on 30 different days", "異なる30日に記録する"),
        "shape_farjourney": ("기록 두 곳이 50km 이상 떨어지기", "Have two entries 50km or more apart", "記録した2箇所の距離が50km以上離れる"),
        "shape_border":     ("기록 두 곳이 1,000km 이상 떨어지기", "Have two entries 1,000km or more apart", "記録した2箇所の距離が1,000km以上離れる"),
        "color_passion":    ("한 다이어리에 좋아요 30개 받기", "Receive 30 likes on a single diary entry", "1件の日記でいいねを30件もらう"),
        "color_sunset":     ("다이어리 25개 작성하기", "Write 25 diary entries", "日記を25件作成する"),
        "color_steady":     ("서로 다른 7일에 기록하기", "Post on 7 different days", "異なる7日に記録する"),
        "color_abyss":      ("좋아요 150개 받기", "Receive 150 likes", "いいねを150件もらう"),
        "color_wanderer":   ("기록 두 곳이 10km 이상 떨어지기", "Have two entries 10km or more apart", "記録した2箇所の距離が10km以上離れる"),
        "color_midnight":   ("자정~새벽(0~4시)에 5번 기록하기", "Post 5 times between midnight and dawn (0–4 AM)", "深夜0時〜4時の間に5回記録する"),
        "color_life":       ("친구 5명 만들기", "Make 5 friends", "友達を5人作る"),
        "color_gold":       ("총 조회수 300 달성하기", "Reach 300 total views", "累計閲覧数300を達成する"),
        "color_nebula":     ("다이어리 50개 작성하기", "Write 50 diary entries", "日記を50件作成する"),
        "color_grad_aurora":  ("좋아요 300개 받기", "Receive 300 likes", "いいねを300件もらう"),
        "color_grad_emerald": ("친구 20명 만들기", "Make 20 friends", "友達を20人作る"),
        "color_grad_sunset":  ("다이어리 100개 작성하기", "Write 100 diary entries", "日記を100件作成する"),
        "color_grad_glacier": ("총 조회수 1,000 달성하기", "Reach 1,000 total views", "累計閲覧数1,000を達成する"),
        "color_grad_dawn":    ("자정~새벽(0~4시)에 10번 기록하기", "Post 10 times between midnight and dawn (0–4 AM)", "深夜0時〜4時の間に10回記録する"),
        // 히든 — 조건은 달성 후에만 노출되지만 문구 자체는 동일하게 로케일 해석.
        "secret_word":    ("다이어리 제목에 ‘우주먼지’ 를 넣기", "Include “우주먼지” in a diary title", "日記のタイトルに「우주먼지」を入れる"),
        "remote_place":   ("남극 · 에베레스트 둘 중 한 곳에 별 남기기", "Leave a star in Antarctica or on Mount Everest", "南極大陸かエベレストのどちらかに星を残す"),
        "place_desert":   ("사하라 사막에 별 남기기", "Leave a star in the Sahara Desert", "サハラ砂漠に星を残す"),
        "place_trench":   ("마리아나 해구에 별 남기기", "Leave a star at the Mariana Trench", "マリアナ海溝に星を残す"),
        "place_triangle": ("버뮤다 삼각지대에 별 남기기", "Leave a star in the Bermuda Triangle", "バミューダトライアングルに星を残す"),
        "all_rounder":    ("히든을 제외한 모든 업적 달성하기", "Unlock every achievement except the hidden ones", "ヒドゥンを除くすべての実績を達成する"),
        "cosmic_rascal":  ("다른 사람의 다이어리 300개 열람하기", "View 300 other people's diary entries", "他の人の日記を300件閲覧する"),
        "lone_observer":  ("친구 없이 다이어리 50개 작성하기", "Write 50 diary entries with zero friends", "友達0人の状態で日記を50件作成する"),
        "heart_frenzy":   ("프로필에서 나가지 않고 하트를 100번 두드리기", "Tap the heart 100 times without leaving the profile screen", "プロフィール画面を離れずにハートを100回タップする"),
        "melomaniac":     ("배경음악 화면에서 나가지 않고 모든 곡 감상하기", "Listen to every track without leaving the music screen", "BGM画面を離れずにすべての曲を聴く"),
        "earth_pilgrim":  ("세계 유명 관광지에 별을 남기고 다른 사람이 그 별을 열람하기", "Leave a star at a famous world landmark and have someone else view it", "世界的に有名な観光地に星を残し、他の人にその星を閲覧してもらう"),
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

    /// 업적 id → 달성 조건 문구(현재 언어). 매핑에 없으면 폴백(정의의 한국어 원문).
    static func condition(_ id: String?, fallback: String) -> String {
        guard let id, let t = conditionTable[id] else { return fallback }
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
