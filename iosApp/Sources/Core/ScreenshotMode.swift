import CoreLocation
import Foundation

/// App Store 스크린샷 자동 촬영 모드 — CI(`.github/workflows/ios-screenshots.yml`)가
/// 시뮬레이터에서 `-StaryShot <장면>` 실행 인자를 줄 때만 켜진다. 일반 실행·스토어 설치본에는 영향 없음.
/// (실행 인자 `-키 값` 은 UserDefaults 인자 도메인으로 들어오므로 그대로 읽는다 — `-app_language en` 처럼 언어도 같은 방식.)
///
/// 장면:
///  - `login`         : 로그인 화면 그대로
///  - `map`           : 둘러보기(게스트)로 바로 진입한 지도
///  - `constellation` : 지도 + 별자리 선 켜기
///  - `globe`         : 지도 → "우주에서 보기"(3D 글로브) 자동 진입
///
/// 켜지면: 새 버전 팝업·광고(ATT 팝업)·집중 모드(10초 무입력 시 버튼 숨김)·웰컴 별·위치 이유 카드를 끄고,
/// login 외 장면은 약관/로그인 화면 없이 둘러보기로 들어간다.
///
/// ⚠️ 별은 **서버에서 읽지 않고 샘플(`demoDiaries`)을 쓴다** — 1차(2026-10-21)에 둘러보기는 Firebase 익명 인증이 꺼져 있어
///    Firestore 읽기가 거부돼 별이 0개였다. 샘플이라 실제 사용자의 이름·글이 스토어 화면에 찍힐 일도 없다.
enum ScreenshotMode {
    static let scene: String? = UserDefaults.standard.string(forKey: "StaryShot")

    static var isActive: Bool { scene != nil }

    /// 둘러보기로 바로 들어가는 장면인가(login 만 아니면).
    static var entersAsGuest: Bool { isActive && scene != "login" }

    /// 지도 최초 자동 이동 줌(`-StaryShotZoom 13`) — 없으면 nil(앱 기본 15).
    static var mapZoom: Double? { number("StaryShotZoom") }

    /// 시뮬레이터 시작 위치(`-StaryShotLat 37.56 -StaryShotLng 126.97`) — 없으면 nil(앱 기본 위치).
    static var coordinate: CLLocationCoordinate2D? {
        guard let lat = number("StaryShotLat"), let lng = number("StaryShotLng") else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    private static func number(_ key: String) -> Double? {
        guard isActive, UserDefaults.standard.object(forKey: key) != nil else { return nil }
        return UserDefaults.standard.double(forKey: key)
    }

    // MARK: - 샘플 별

    private static let titles: [String: [String]] = [
        "ko": ["한강에서 본 노을", "첫 출근길", "비 오는 날의 카페", "고양이와 낮잠", "남산 야경", "오랜만의 가족 저녁",
               "퇴근길 하늘", "벚꽃 엔딩", "친구와 떡볶이", "새벽 러닝", "서점 구석 자리", "첫눈 오던 날",
               "합격 소식", "생일 케이크", "골목길 산책", "비행기 창가", "동네 빵집", "여름밤 수박",
               "캠핑장 별 보기", "졸업 사진", "버스 맨 뒷자리", "겨울 붕어빵", "운동장 노을", "도서관 오후"],
        "en": ["Sunset by the river", "First day commute", "Rainy day café", "Nap with my cat", "Night view from the hill",
               "Family dinner", "Sky on the way home", "Cherry blossoms", "Street food with friends", "Dawn run",
               "Corner of a bookstore", "First snow", "Good news today", "Birthday cake", "Alley walk",
               "Window seat", "Neighborhood bakery", "Watermelon night", "Stargazing camp", "Graduation photo",
               "Back seat of the bus", "Winter street snack", "Sunset on the field", "Library afternoon"],
        "ja": ["川辺の夕焼け", "初出勤の朝", "雨の日のカフェ", "猫とお昼寝", "丘から見た夜景", "久しぶりの家族の夕食",
               "帰り道の空", "桜の季節", "友だちとたこ焼き", "夜明けのランニング", "本屋の隅っこ", "初雪の日",
               "合格の知らせ", "誕生日ケーキ", "路地の散歩", "飛行機の窓際", "町のパン屋さん", "夏の夜のスイカ",
               "キャンプの星空", "卒業写真", "バスの一番後ろ", "冬のたい焼き", "校庭の夕焼け", "図書館の午後"],
    ]

    private static let names: [String: [String]] = [
        "ko": ["하늘", "민준", "서연", "별이", "지우", "도윤", "하은"],
        "en": ["Sky", "Mina", "Leo", "Stella", "Jay", "Noa", "Ella"],
        "ja": ["そら", "ひなた", "あおい", "ほし", "れん", "みお", "はる"],
    ]

    /// 앱 언어 → 샘플 문구 언어(ko / en / ja, 그 밖엔 en).
    private static var sampleLang: String {
        let l = (UserDefaults.standard.string(forKey: "app_language") ?? "ko").lowercased()
        if l.hasPrefix("ko") { return "ko" }
        if l.hasPrefix("ja") { return "ja" }
        return "en"
    }

    /// 지도 가운데 둘레에 고르게 흩어진 샘플 별 — 해바라기 씨앗(황금각) 배치라 겹치지 않고 화면을 채운다.
    /// 흩어지는 폭은 줌에 맞춘다(줌 13 기준 세로 ±0.026° · 가로 ±0.024°, 줌이 1 낮아지면 2배).
    static func demoDiaries() -> [Diary] {
        let lang = sampleLang
        let ts = titles[lang] ?? titles["en"]!
        let ns = names[lang] ?? names["en"]!
        let center = coordinate ?? CLLocationCoordinate2D(latitude: AppConfig.defaultLat, longitude: AppConfig.defaultLng)
        let zoom = mapZoom ?? 13
        let scale = pow(2.0, 13.0 - zoom)
        let rLat = 0.026 * scale
        let rLng = 0.024 * scale
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let golden = 2.399963229728653   // 황금각(라디안)
        let n = ts.count
        // (식을 잘게 나눈다 — 한 식에 몰아 쓰면 Swift 타입 추론 시간 초과로 CI 컴파일이 실패한 적이 있다)
        var list: [Diary] = []
        for i in 0..<n {
            let t = Double(i)
            let r: Double = (t + 0.6).squareRoot() / Double(n).squareRoot()
            let a: Double = t * golden
            let lat: Double = center.latitude + r * sin(a) * rLat
            let lng: Double = center.longitude + r * cos(a) * rLng
            let created: Int64 = now - Int64(i) * 18_000_000
            list.append(Diary(
                id: "demo_\(i)",
                userId: "demo_u\(i % ns.count)",
                userName: ns[i % ns.count],
                title: ts[i],
                content: ts[i],
                latitude: lat,
                longitude: lng,
                createdAt: created,
                likeCount: 3 + (i * 7) % 29,
                commentCount: (i * 5) % 9,
                viewCount: 20 + (i * 13) % 140,
                starType: i % StarStyle.typeCount,
                starColor: (i * 4 + 1) % StarStyle.colorCount
            ))
        }
        return list
    }
}
