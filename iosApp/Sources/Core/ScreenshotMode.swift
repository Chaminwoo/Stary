import CoreLocation
import Foundation

/// App Store 스크린샷 촬영 모드 — 실행 인자 `-StaryShot <장면>` 을 줄 때만 켜진다(Xcode 스킴 > Arguments 또는 `simctl launch`).
/// 일반 실행·스토어 설치본에는 인자가 없으므로 아무 영향이 없다.
/// (실행 인자 `-키 값` 은 UserDefaults 인자 도메인으로 들어오므로 그대로 읽는다 — `-app_language en` 처럼 언어도 같은 방식.)
///
/// **네트워크·로그인에 기대지 않는다**(2026-10-28 재작성 — 1차 자동 촬영은 익명 인증이 꺼져 있어 별이 0개였다):
/// 게스트로 바로 들어가고, 별은 서버 대신 [demoDiaries] 의 내장 샘플을 쓴다. 지도 타일만 인터넷이 필요하다.
/// 실기기(Duo)에서도, 시뮬레이터에서도 같다 — 위치는 [coordinate] 로 고정한다.
///
/// 장면(`-StaryShot` 값):
///  - `login`         : 로그인 화면 그대로(심사 기준상 "앱 사용 중" 스샷이 아니라 촬영 대상은 아님)
///  - `map`           : 지도 + 샘플 별
///  - `constellation` : 지도 + 별자리 선
///  - `globe`         : "우주에서 보기"(3D 글로브) 자동 진입
///  - `detail`        : 가장 가까운 샘플 별을 연 상세 화면
///  - `drawer`        : 지도 + 왼쪽 메뉴(드로어) 펼침
///
/// 선택 인자: `-StaryShotZoom 14`(지도 줌) · `-StaryShotLat 37.5665 -StaryShotLng 126.978`(위치).
/// 켜지면: 새 버전 팝업·광고(ATT 팝업)·권한 안내 카드·집중 모드(10초 무입력 시 버튼 숨김)·웰컴 별을 끈다.
/// 촬영 방법: docs/STORE_SCREENSHOTS.md
enum ScreenshotMode {
    static let scene: String? = UserDefaults.standard.string(forKey: "StaryShot")

    static var isActive: Bool { scene != nil }

    /// 둘러보기(게스트)로 바로 들어가는 장면인가(login 만 아니면).
    static var entersAsGuest: Bool { isActive && scene != "login" }

    /// 지도 줌(`-StaryShotZoom`) — 없으면 장면 기본 14(샘플 별이 한 화면에 모이는 값).
    static var mapZoom: Double? { isActive ? (number("StaryShotZoom") ?? 14) : nil }

    /// 촬영 위치 — 실행 인자가 없으면 앱 기본 위치. 실제 GPS 는 무시하고 이 좌표로 고정한다.
    static var coordinate: CLLocationCoordinate2D? {
        guard isActive else { return nil }
        if let lat = number("StaryShotLat"), let lng = number("StaryShotLng") {
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        return CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
    }

    private static func number(_ key: String) -> Double? {
        guard UserDefaults.standard.object(forKey: key) != nil else { return nil }
        return UserDefaults.standard.double(forKey: key)
    }

    // MARK: - 샘플 별

    /// (북쪽 m, 동쪽 m, 모양, 색, 제목, 내용, 작성자, 좋아요, 댓글) — ko / en / ja 순.
    private struct Sample {
        let north: Double, east: Double
        let type: Int, color: Int
        let ko: (String, String), en: (String, String), ja: (String, String)
        let author: String
        let likes: Int, comments: Int
    }

    private static let samples: [Sample] = [
        // 0번은 20m 안 — `detail` 장면이 잠금 없이 바로 열린다.
        Sample(north: 14, east: 12, type: 1, color: 1,
               ko: ("퇴근길 노을", "오늘은 일부러 한 정거장 먼저 내렸다. 강변 위로 번지는 노을이 하루를 다 용서해 주는 것 같았다."),
               en: ("Sunset on the way home", "I got off a stop early on purpose today. The sunset spilling over the river felt like it forgave the whole day."),
               ja: ("帰り道の夕焼け", "今日はわざと一駅手前で降りた。川の上に広がる夕焼けが、一日を全部許してくれるみたいだった。"),
               author: "하늘", likes: 24, comments: 5),
        Sample(north: 240, east: -310, type: 0, color: 4,
               ko: ("첫 출근", "새 명함을 받았다. 이름 석 자가 낯설게 반짝였다."),
               en: ("First day at work", "I got my new business card. My own name looked strangely bright."),
               ja: ("初出勤", "新しい名刺をもらった。自分の名前が見慣れない光り方をしていた。"),
               author: "민서", likes: 18, comments: 3),
        Sample(north: -180, east: 420, type: 2, color: 7,
               ko: ("비 온 뒤 골목", "비가 그치고 나니 골목이 젖은 유리처럼 반짝였다."),
               en: ("Alley after the rain", "When the rain stopped, the alley shone like wet glass."),
               ja: ("雨上がりの路地", "雨が止むと、路地が濡れたガラスのように光っていた。"),
               author: "도윤", likes: 31, comments: 7),
        Sample(north: 520, east: 140, type: 4, color: 9,
               ko: ("동네 빵집", "아침마다 줄 서는 이유가 있었다. 오늘은 내가 마지막 한 개를 샀다."),
               en: ("Neighborhood bakery", "There's a reason for the morning line. Today I bought the very last one."),
               ja: ("町のパン屋", "毎朝並ぶ理由があった。今日は私が最後の一個を買った。"),
               author: "서연", likes: 12, comments: 2),
        Sample(north: -460, east: -120, type: 3, color: 16,
               ko: ("한강 야경", "다리 위 불빛이 물 위에서 한 번 더 흔들렸다."),
               en: ("Han River at night", "The lights on the bridge swayed once more on the water."),
               ja: ("漢江の夜景", "橋の灯りが、水の上でもう一度揺れた。"),
               author: "지호", likes: 42, comments: 9),
        Sample(north: 90, east: -560, type: 5, color: 5,
               ko: ("책방 구석자리", "두 시간 동안 한 페이지도 못 넘겼는데, 그래도 좋은 오후였다."),
               en: ("Corner seat at the bookshop", "I couldn't turn a single page in two hours, and it was still a good afternoon."),
               ja: ("本屋の隅の席", "二時間一ページも進まなかったけれど、いい午後だった。"),
               author: "하린", likes: 15, comments: 4),
        Sample(north: -330, east: 560, type: 6, color: 17,
               ko: ("옥상 별 보기", "도시에서도 별이 보인다는 걸 처음 알았다."),
               en: ("Stargazing on the roof", "I learned for the first time that you can see stars even in the city."),
               ja: ("屋上の星空", "都会でも星が見えると、初めて知った。"),
               author: "유준", likes: 56, comments: 11),
        Sample(north: 380, east: 600, type: 7, color: 2,
               ko: ("첫눈 오던 날", "약속 시간보다 일찍 나가서 눈을 맞았다."),
               en: ("The day of the first snow", "I left early for our meeting just to walk in the snow."),
               ja: ("初雪の日", "約束より早く出て、雪に降られた。"),
               author: "나은", likes: 27, comments: 6),
        Sample(north: -620, east: 280, type: 8, color: 8,
               ko: ("자전거 길", "페달을 밟을수록 생각이 가벼워졌다."),
               en: ("Bike path", "The more I pedaled, the lighter my thoughts became."),
               ja: ("自転車道", "ペダルを踏むほど、考えが軽くなった。"),
               author: "시우", likes: 9, comments: 1),
        Sample(north: 700, east: -240, type: 9, color: 18,
               ko: ("시장 국수 한 그릇", "할머니가 고명을 한 줌 더 얹어 주셨다."),
               en: ("A bowl of market noodles", "The grandmother put an extra handful of toppings on top."),
               ja: ("市場のうどん", "おばあさんが具をもうひとつかみ足してくれた。"),
               author: "예린", likes: 21, comments: 3),
        Sample(north: -90, east: -380, type: 10, color: 19,
               ko: ("새벽 러닝", "아무도 없는 길 위에 내 발소리만 남았다."),
               en: ("Dawn run", "On the empty road, only the sound of my footsteps remained."),
               ja: ("明け方のランニング", "誰もいない道に、自分の足音だけが残った。"),
               author: "태오", likes: 14, comments: 2),
        Sample(north: 160, east: 820, type: 11, color: 10,
               ko: ("공원 벤치", "오래된 노래 하나가 오늘의 배경음악이 되었다."),
               en: ("Park bench", "One old song became the soundtrack of today."),
               ja: ("公園のベンチ", "昔の歌がひとつ、今日のBGMになった。"),
               author: "아린", likes: 33, comments: 5),
        Sample(north: -240, east: -700, type: 12, color: 6,
               ko: ("옛 친구와 커피", "오 년 만이었는데 어제 만난 것처럼 웃었다."),
               en: ("Coffee with an old friend", "It had been five years, yet we laughed as if we'd met yesterday."),
               ja: ("旧友とコーヒー", "五年ぶりなのに、昨日会ったみたいに笑った。"),
               author: "준서", likes: 19, comments: 4),
    ]

    /// 샘플 별 목록 — 촬영 위치 둘레에 흩뿌린다. 서버를 읽지 않으므로 항상 같은 화면이 나온다.
    @MainActor
    static func demoDiaries() -> [Diary] {
        guard let c = coordinate else { return [] }
        let lang = LocaleManager.shared.effectiveLanguage
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let latPerMeter = 1.0 / 111_320.0
        let lngPerMeter = 1.0 / (111_320.0 * max(cos(c.latitude * .pi / 180), 0.01))
        return samples.enumerated().map { i, s in
            let text = lang == "en" ? s.en : (lang == "ja" ? s.ja : s.ko)
            return Diary(
                id: "demo-\(i)",
                userId: "demo-user-\(i)",
                userName: s.author,
                title: text.0,
                content: text.1,
                latitude: c.latitude + s.north * latPerMeter,
                longitude: c.longitude + s.east * lngPerMeter,
                createdAt: now - Int64(i + 1) * 3 * 3_600_000,
                likeCount: s.likes,
                commentCount: s.comments,
                viewCount: s.likes * 4,
                starType: s.type,
                starColor: s.color
            )
        }
    }

    /// `detail` 장면이 여는 별(촬영 위치에서 가장 가까운 0번).
    @MainActor
    static func detailDiary() -> Diary? { demoDiaries().first }
}
