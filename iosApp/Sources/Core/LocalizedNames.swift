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
        // 2026-09-26 추가 업적(칭호 + 별 모양 9~20) — Android strings.xml 과 같은 문구
        "count_five": ("작은 성좌", "A Small Constellation", "小さな星座"),
        "photo_ten": ("빛을 인화하다", "Developing the Light", "光を焼きつける"),
        "video_first": ("재생되는 기억", "Memory on Replay", "再生される記憶"),
        "long_letter": ("부치지 못한 편지", "The Unsent Letter", "出せなかった手紙"),
        "short_post": ("여백의 한 줄", "A Single Line", "余白の一行"),
        "private_five": ("서랍 속 은하", "A Galaxy in the Drawer", "引き出しの中の銀河"),
        "streak_three": ("궤도 진입", "Into Orbit", "軌道に乗る"),
        "streak_month": ("한 번의 삭망", "One Lunar Cycle", "ひとめぐりの月"),
        "days_hundred": ("백일몽", "A Hundred-Day Dream", "百日の夢"),
        "all_months": ("열두 개의 달", "Twelve Moons", "十二の月"),
        "new_year": ("첫 페이지", "The First Page", "最初のページ"),
        "christmas": ("고요한 밤", "Silent Night", "きよしこの夜"),
        "places_five": ("흩어진 좌표", "Scattered Coordinates", "散らばった座標"),
        "places_twenty": ("나침반의 기억", "What the Compass Remembers", "羅針盤の記憶"),
        "home_cluster": ("골목의 성단", "A Cluster Close to Home", "路地の星団"),
        "revisit": ("다시, 그 자리", "Back to That Place", "もう一度、あの場所で"),
        "log_ten": ("작은 표본 상자", "A Small Specimen Box", "小さな標本箱"),
        "log_hundred": ("백 개의 우주", "A Hundred Universes", "百の宇宙"),
        "neighbor": ("쌍성", "Binary Star", "連星"),
        "like_ten_one": ("잔잔한 박수", "Gentle Applause", "ささやかな拍手"),
        "views_one": ("모두가 올려다본 별", "Everyone Looked Up", "みんなが見上げた星"),
        "comments_ten": ("창가의 대화", "Talks by the Window", "窓辺の会話"),
        "friend_first": ("교신이 닿다", "Contact Made", "交信成立"),
        "friends_ten": ("함께 공전하는 사이", "Orbiting Together", "ともに公転する仲"),
        "shapes_five": ("변주곡", "Variations", "変奏曲"),
        "colors_eight": ("일곱 빛깔 너머", "Beyond the Rainbow", "七色の向こう"),
        "shape_heart": ("하트 성운", "Heart Nebula", "ハート星雲"),
        "shape_comet": ("지평선 너머로", "Beyond the Horizon", "地平線の向こうへ"),
        "shape_snow": ("아무도 밟지 않은 눈밭", "Untouched Snow", "誰も踏んでいない雪原"),
        "shape_sakura": ("꽃잎이 흩날린 하루", "A Day of Falling Petals", "花びらが舞った一日"),
        "shape_sun": ("해보다 이른 걸음", "Steps Before Sunrise", "日の出より早い足どり"),
        "shape_flame": ("꺼지지 않는 등불", "An Unfading Lamp", "消えない灯"),
        "shape_key": ("잠긴 문의 수집가", "Keeper of Locked Doors", "閉ざされた扉の収集家"),
        "shape_clover": ("숫자가 나란히 선 순간", "When the Numbers Align", "数字がそろう瞬間"),
        "shape_crown": ("대관식", "Coronation", "戴冠式"),
        "shape_galaxy": ("나선의 연대기", "Spiral Chronicle", "渦巻きの年代記"),
        "shape_cat": ("만월 아래 고양이", "Cat Under a Full Moon", "満月の下の猫"),
        "shape_plane": ("밤하늘 우체국", "Night Sky Post Office", "夜空の郵便局"),
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
        // 2026-09-26 추가 업적
        "count_five": ("다이어리 5개 작성하기", "Write 5 diary entries", "日記を5件作成する"),
        "photo_ten": ("사진을 넣은 다이어리 10개 작성하기", "Write 10 diary entries with a photo", "写真付きの日記を10件作成する"),
        "video_first": ("영상을 넣은 다이어리 작성하기", "Write a diary entry with a video", "動画付きの日記を作成する"),
        "long_letter": ("본문 500자 이상인 다이어리 작성하기", "Write a diary entry of 500+ characters", "本文500文字以上の日記を作成する"),
        "short_post": ("본문 20자 이하로 다이어리 작성하기", "Write a diary entry of 20 characters or fewer", "本文20文字以下の日記を作成する"),
        "private_five": ("'나만보기' 다이어리 5개 작성하기", "Write 5 “Only me” diary entries", "「自分のみ」の日記を5件作成する"),
        "streak_three": ("3일 연속 기록하기", "Post 3 days in a row", "3日連続で記録する"),
        "streak_month": ("30일 연속 기록하기", "Post 30 days in a row", "30日連続で記録する"),
        "days_hundred": ("서로 다른 100일에 기록하기", "Post on 100 different days", "異なる100日に記録する"),
        "all_months": ("1월부터 12월까지 모든 달에 기록하기", "Post in every month, January through December", "1月から12月まですべての月に記録する"),
        "new_year": ("1월 1일에 기록하기", "Post on January 1", "1月1日に記録する"),
        "christmas": ("12월 24일이나 25일에 기록하기", "Post on December 24 or 25", "12月24日か25日に記録する"),
        "places_five": ("서로 1km 이상 떨어진 5곳에서 기록하기", "Post from 5 places at least 1 km apart", "1km以上離れた5か所で記録する"),
        "places_twenty": ("서로 1km 이상 떨어진 20곳에서 기록하기", "Post from 20 places at least 1 km apart", "1km以上離れた20か所で記録する"),
        "home_cluster": ("반경 1km 안에 내 별 5개 남기기", "Leave 5 of your stars within a 1 km radius", "半径1km以内に自分の星を5つ残す"),
        "revisit": ("같은 자리(50m 이내)에서 30일 넘게 지나 다시 기록하기", "Post at the same spot (within 50 m) again after more than 30 days", "同じ場所(50m以内)で30日以上あけて再び記録する"),
        "log_ten": ("별 도감에 다른 사람의 별 10개 모으기", "Collect 10 other people's stars in your Star Log", "星の図鑑に他の人の星を10個集める"),
        "log_hundred": ("별 도감에 다른 사람의 별 100개 모으기", "Collect 100 other people's stars in your Star Log", "星の図鑑に他の人の星を100個集める"),
        "neighbor": ("다른 사람의 별 100m 안에 내 별 남기기", "Leave a star within 100 m of someone else's star", "他の人の星から100m以内に星を残す"),
        "like_ten_one": ("한 다이어리에 좋아요 10개 받기", "Get 10 likes on a single diary entry", "1つの日記でいいねを10個もらう"),
        "views_one": ("한 다이어리 조회수 100 달성하기", "Reach 100 views on a single diary entry", "1つの日記で閲覧数100を達成する"),
        "comments_ten": ("댓글 10개 받기", "Receive 10 comments", "コメントを10件もらう"),
        "friend_first": ("친구 1명 만들기", "Make 1 friend", "友達を1人作る"),
        "friends_ten": ("친구 10명 만들기", "Make 10 friends", "友達を10人作る"),
        "shapes_five": ("서로 다른 별 모양 5개로 기록하기", "Post with 5 different star shapes", "5種類の星の形で記録する"),
        "colors_eight": ("서로 다른 별 색 8개로 기록하기", "Post with 8 different star colors", "8種類の星の色で記録する"),
        "shape_heart": ("좋아요 500개 받기", "Receive 500 likes", "いいねを500個もらう"),
        "shape_comet": ("기록 두 곳이 5,000km 이상 떨어지기", "Have two posts 5,000 km or more apart", "2つの記録が5,000km以上離れる"),
        "shape_snow": ("1km 안에 먼저 남겨진 별이 없던 곳에 별 3개 남기기", "Leave 3 stars where no one had left a star within 1 km", "半径1km以内にまだ星がなかった場所に星を3つ残す"),
        "shape_sakura": ("하루에 별 3개 남기기", "Leave 3 stars in a single day", "1日に星を3つ残す"),
        "shape_sun": ("새벽 5~7시에 5번 기록하기", "Post 5 times between 5 and 7 AM", "朝5時〜7時に5回記録する"),
        "shape_flame": ("7일 연속 기록하기", "Post 7 days in a row", "7日連続で記録する"),
        "shape_key": ("별 도감에 다른 사람의 별 30개 모으기", "Collect 30 other people's stars in your Star Log", "星の図鑑に他の人の星を30個集める"),
        "shape_clover": ("11시 11분에 기록하기", "Post at 11:11", "11時11分に記録する"),
        "shape_crown": ("일반 업적 30개 달성하기", "Unlock 30 achievements", "実績を30個達成する"),
        "shape_galaxy": ("다이어리 200개 작성하기", "Write 200 diary entries", "日記を200件作成する"),
        "shape_cat": ("보름달 뜬 밤(저녁 6시~새벽 6시)에 기록하기", "Post on a full-moon night (6 PM–6 AM)", "満月の夜(18時〜翌6時)に記録する"),
        "shape_plane": ("댓글 50개 받기", "Receive 50 comments", "コメントを50件もらう"),
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
