import Foundation

/// 업적/별 해금 시스템 — Android `feature.profile.Achievements` 의 Swift 포팅.
/// 정의(조건/보상)를 양 플랫폼이 동일하게 유지한다.

/// 업적 판정용 사용자 누적 통계.
struct UserStats {
    var diariesCreated = 0
    var likesReceived = 0
    var viewsReceived = 0
    var diariesViewed = 0
    var friends = 0
    var maxSpanMeters: Double = 0   // 내 다이어리 두 곳 사이의 최대 거리
    var maxLikesOnOne = 0
    var distinctDays = 0            // 기록한 서로 다른 날짜 수(기기 현지 날짜)
    var nightPosts = 0              // 자정~새벽(현지 0~4시)에 올린 기록 수
    // ── 히든 업적용 통계 ──
    var secretKeywordTitle = false // 제목에 히든 키워드를 넣은 다이어리가 있는가
    var remoteRegions: Set<String> = [] // 도달한 오지 region 집합(glacier/desert/trench/triangle)
    // ── 친구 초대 보상용 통계(체크리스트 31) ──
    var invitedFriends = 0        // 내 초대를 리딤해 가입한 친구 수
    var redeemedInvite = false    // 내가 초대를 리딤했는가
    // ── 2026-09-26 추가 업적용 통계 — 시간 판정은 전부 기기 현지 시간(Android 와 같은 규칙) ──
    var photoPosts = 0            // 사진을 넣은 기록(영상 제외)
    var videoPosts = 0
    var longPosts = 0             // 본문 500자 이상
    var shortPosts = 0            // 본문 20자 이하(익명 게시는 기능이 없어 업적에서 제외)
    var privatePosts = 0          // 나만보기
    var maxStreakDays = 0         // 최장 연속 기록 일수
    var dawnPosts = 0             // 새벽 5~7시(5:00~6:59)
    var maxPostsInDay = 0         // 하루 최다 기록 수
    var distinctMonths = 0        // 1~12월 중 기록한 달 수(연도 무관)
    var wishTimePost = false      // 11:11 또는 23:11
    var fullMoonPost = false      // 보름달 밤(18~6시)
    var newYearPost = false       // 1월 1일
    var christmasPost = false     // 12월 24·25일
    var distinctPlaces = 0        // 서로 1km 이상 떨어진 장소 수
    var maxLocalCluster = 0       // 반경 1km 안에 모인 내 별 최대 개수
    var revisitedSpot = false     // 같은 자리(50m)에서 30일 넘게 지나 다시 기록
    var firstInArea = 0           // 1km 안에 먼저 남겨진 (남의) 별이 없던 곳에 남긴 별 수
    var nearNeighbor = false      // 남의 별 100m 안에 내 별이 있음
    var starLogCount = 0          // 별 도감에 모은 다른 사람의 별(연 글 ∩ 지금 보이는 남의 글)
    var maxViewsOnOne = 0
    var commentsReceived = 0
    var distinctShapes = 0
    var distinctColors = 0
    var achievementsDone = 0      // 왕관 판정용 — 왕관 자신을 뺀 일반 업적 달성 수
}

/// 업적 보상 — 칭호 / 별 모양 / 별 색.
enum Reward {
    case title(String)
    case shape(Int)
    case color(Int)
}

struct Achievement: Identifiable {
    let id: String
    let name: String
    let condition: String
    let reward: Reward
    var hidden = false
    let unlocked: (UserStats) -> Bool

    var titleName: String? {
        if case let .title(n) = reward { return n }
        return nil
    }
}

/// 칭호 업적(이름 = 칭호) 짧은 생성자. (Android `title(...)` 패리티)
private func titleAch(_ id: String, _ name: String, _ condition: String, hidden: Bool = false,
                      _ unlocked: @escaping (UserStats) -> Bool) -> Achievement {
    Achievement(id: id, name: name, condition: condition, reward: .title(name), hidden: hidden, unlocked: unlocked)
}

enum Achievements {
    /// 왕관(일반 업적 N개 달성) 업적 id — 통계 계산 시 자기 자신을 빼고 센다.
    static let crownId = "shape_crown"
    static let crownTarget = 30

    static let titleAchievements: [Achievement] = [
        Achievement(id: "first_step", name: "첫 발자국", condition: "다이어리 1개 작성하기", reward: .title("첫 발자국")) { $0.diariesCreated >= 1 },
        Achievement(id: "star_traveler", name: "별의 여행자", condition: "다른 사람의 다이어리 10개 열람하기", reward: .title("별의 여행자")) { $0.diariesViewed >= 10 },
        Achievement(id: "storyteller", name: "이야기꾼", condition: "다이어리 10개 작성하기", reward: .title("이야기꾼")) { $0.diariesCreated >= 10 },
        Achievement(id: "popular", name: "인기쟁이", condition: "좋아요 50개 받기", reward: .title("인기쟁이")) { $0.likesReceived >= 50 },
        Achievement(id: "watched_star", name: "주목받는 별", condition: "총 조회수 100 달성하기", reward: .title("주목받는 별")) { $0.viewsReceived >= 100 },
        Achievement(id: "companion", name: "길동무", condition: "친구 3명 만들기", reward: .title("길동무")) { $0.friends >= 3 },
        Achievement(id: "pilgrim", name: "우주의 순례자", condition: "다이어리 30개 작성하기", reward: .title("우주의 순례자")) { $0.diariesCreated >= 30 },
        Achievement(id: "guide", name: "별빛의 인도자", condition: "좋아요 200개 받기", reward: .title("별빛의 인도자")) { $0.likesReceived >= 200 },
        // ── 친구 초대 보상(체크리스트 31) — 초대한 쪽/받은 쪽 모두 칭호 ──
        Achievement(id: "invite_bond", name: "별의 인연", condition: "초대를 받아 Stary 에 합류하기", reward: .title("별의 인연")) { $0.redeemedInvite },
        Achievement(id: "invite_beacon", name: "별의 등대", condition: "친구 1명을 Stary 로 초대하기", reward: .title("별의 등대")) { $0.invitedFriends >= 1 },
        Achievement(id: "invite_flock", name: "별무리의 길잡이", condition: "친구 5명을 Stary 로 초대하기", reward: .title("별무리의 길잡이")) { $0.invitedFriends >= 5 },
        // ※ 기존 '???' 칭호(우주의 악동/고독한 관측자)는 히든 업적(HiddenAchievements)으로 이관됨.

        // ── 2026-09-26 추가 칭호(사용자 승인 — 계절 업적은 나라마다 달라 제외) ── Android 와 순서·값 동일
        // 기록
        titleAch("count_five", "작은 성좌", "다이어리 5개 작성하기") { $0.diariesCreated >= 5 },
        titleAch("photo_ten", "빛을 인화하다", "사진을 넣은 다이어리 10개 작성하기") { $0.photoPosts >= 10 },
        titleAch("video_first", "재생되는 기억", "영상을 넣은 다이어리 작성하기") { $0.videoPosts >= 1 },
        titleAch("long_letter", "부치지 못한 편지", "본문 500자 이상인 다이어리 작성하기") { $0.longPosts >= 1 },
        titleAch("short_post", "여백의 한 줄", "본문 20자 이하로 다이어리 작성하기") { $0.shortPosts >= 1 },
        titleAch("private_five", "서랍 속 은하", "'나만보기' 다이어리 5개 작성하기") { $0.privatePosts >= 5 },
        // 꾸준함
        titleAch("streak_three", "궤도 진입", "3일 연속 기록하기") { $0.maxStreakDays >= 3 },
        titleAch("streak_month", "한 번의 삭망", "30일 연속 기록하기") { $0.maxStreakDays >= 30 },
        titleAch("days_hundred", "백일몽", "서로 다른 100일에 기록하기") { $0.distinctDays >= 100 },
        // 시간 · 특별한 날
        titleAch("all_months", "열두 개의 달", "1월부터 12월까지 모든 달에 기록하기") { $0.distinctMonths >= 12 },
        titleAch("new_year", "첫 페이지", "1월 1일에 기록하기", hidden: true) { $0.newYearPost },
        titleAch("christmas", "고요한 밤", "12월 24일이나 25일에 기록하기", hidden: true) { $0.christmasPost },
        // 여행 · 장소
        titleAch("places_five", "흩어진 좌표", "서로 1km 이상 떨어진 5곳에서 기록하기") { $0.distinctPlaces >= 5 },
        titleAch("places_twenty", "나침반의 기억", "서로 1km 이상 떨어진 20곳에서 기록하기") { $0.distinctPlaces >= 20 },
        titleAch("home_cluster", "골목의 성단", "반경 1km 안에 내 별 5개 남기기") { $0.maxLocalCluster >= 5 },
        titleAch("revisit", "다시, 그 자리", "같은 자리(50m 이내)에서 30일 넘게 지나 다시 기록하기", hidden: true) { $0.revisitedSpot },
        // 열람 · 별 도감
        titleAch("log_ten", "작은 표본 상자", "별 도감에 다른 사람의 별 10개 모으기") { $0.starLogCount >= 10 },
        titleAch("log_hundred", "백 개의 우주", "별 도감에 다른 사람의 별 100개 모으기") { $0.starLogCount >= 100 },
        titleAch("neighbor", "쌍성", "다른 사람의 별 100m 안에 내 별 남기기") { $0.nearNeighbor },
        // 사랑받기 · 친구
        titleAch("like_ten_one", "잔잔한 박수", "한 다이어리에 좋아요 10개 받기") { $0.maxLikesOnOne >= 10 },
        titleAch("views_one", "모두가 올려다본 별", "한 다이어리 조회수 100 달성하기") { $0.maxViewsOnOne >= 100 },
        titleAch("comments_ten", "창가의 대화", "댓글 10개 받기") { $0.commentsReceived >= 10 },
        titleAch("friend_first", "교신이 닿다", "친구 1명 만들기") { $0.friends >= 1 },
        titleAch("friends_ten", "함께 공전하는 사이", "친구 10명 만들기") { $0.friends >= 10 },
        // 꾸미기
        titleAch("shapes_five", "변주곡", "서로 다른 별 모양 5개로 기록하기") { $0.distinctShapes >= 5 },
        titleAch("colors_eight", "일곱 빛깔 너머", "서로 다른 별 색 8개로 기록하기") { $0.distinctColors >= 8 },
    ]

    static let rewardAchievements: [Achievement] = [
        // 모양 보상
        Achievement(id: "shape_flower", name: "별꽃을 피운 자", condition: "좋아요 80개 받기", reward: .shape(5)) { $0.likesReceived >= 80 },
        Achievement(id: "shape_gem", name: "결정의 시간", condition: "서로 다른 14일에 기록하기", reward: .shape(6)) { $0.distinctDays >= 14 },
        Achievement(id: "shape_moon", name: "달의 인도자", condition: "총 조회수 500 달성하기", reward: .shape(7)) { $0.viewsReceived >= 500 },
        Achievement(id: "shape_planet", name: "나만의 행성", condition: "서로 다른 30일에 기록하기", reward: .shape(8)) { $0.distinctDays >= 30 },
        Achievement(id: "shape_farjourney", name: "머나먼 여정", condition: "기록 두 곳이 50km 이상 떨어지기", reward: .shape(3)) { $0.maxSpanMeters >= 50_000 },
        Achievement(id: "shape_border", name: "국경을 넘어", condition: "기록 두 곳이 1,000km 이상 떨어지기", reward: .shape(4)) { $0.maxSpanMeters >= 1_000_000 },
        // 모양 보상(2026-09-26 추가 — 9~20)
        Achievement(id: "shape_heart", name: "하트 성운", condition: "좋아요 500개 받기", reward: .shape(9)) { $0.likesReceived >= 500 },
        Achievement(id: "shape_comet", name: "지평선 너머로", condition: "기록 두 곳이 5,000km 이상 떨어지기", reward: .shape(10)) { $0.maxSpanMeters >= 5_000_000 },
        Achievement(id: "shape_snow", name: "아무도 밟지 않은 눈밭", condition: "1km 안에 먼저 남겨진 별이 없던 곳에 별 3개 남기기", reward: .shape(11)) { $0.firstInArea >= 3 },
        Achievement(id: "shape_sakura", name: "꽃잎이 흩날린 하루", condition: "하루에 별 3개 남기기", reward: .shape(12)) { $0.maxPostsInDay >= 3 },
        Achievement(id: "shape_sun", name: "해보다 이른 걸음", condition: "새벽 5~7시에 5번 기록하기", reward: .shape(13)) { $0.dawnPosts >= 5 },
        Achievement(id: "shape_flame", name: "꺼지지 않는 등불", condition: "7일 연속 기록하기", reward: .shape(14)) { $0.maxStreakDays >= 7 },
        Achievement(id: "shape_key", name: "잠긴 문의 수집가", condition: "별 도감에 다른 사람의 별 30개 모으기", reward: .shape(15)) { $0.starLogCount >= 30 },
        Achievement(id: "shape_clover", name: "숫자가 나란히 선 순간", condition: "11시 11분에 기록하기", reward: .shape(16), hidden: true) { $0.wishTimePost },
        Achievement(id: Achievements.crownId, name: "대관식", condition: "일반 업적 30개 달성하기", reward: .shape(17)) { $0.achievementsDone >= Achievements.crownTarget },
        Achievement(id: "shape_galaxy", name: "나선의 연대기", condition: "다이어리 200개 작성하기", reward: .shape(18)) { $0.diariesCreated >= 200 },
        Achievement(id: "shape_cat", name: "만월 아래 고양이", condition: "보름달 뜬 밤(저녁 6시~새벽 6시)에 기록하기", reward: .shape(19), hidden: true) { $0.fullMoonPost },
        Achievement(id: "shape_plane", name: "밤하늘 우체국", condition: "댓글 50개 받기", reward: .shape(20)) { $0.commentsReceived >= 50 },
        // 색 보상(단색)
        Achievement(id: "color_passion", name: "정열의 한 방", condition: "한 다이어리에 좋아요 30개 받기", reward: .color(3)) { $0.maxLikesOnOne >= 30 },
        Achievement(id: "color_sunset", name: "노을 수집가", condition: "다이어리 25개 작성하기", reward: .color(4)) { $0.diariesCreated >= 25 },
        Achievement(id: "color_steady", name: "꾸준한 관측자", condition: "서로 다른 7일에 기록하기", reward: .color(5)) { $0.distinctDays >= 7 },
        Achievement(id: "color_abyss", name: "심연의 탐구자", condition: "좋아요 150개 받기", reward: .color(6)) { $0.likesReceived >= 150 },
        Achievement(id: "color_wanderer", name: "대지의 방랑자", condition: "기록 두 곳이 10km 이상 떨어지기", reward: .color(11)) { $0.maxSpanMeters >= 10_000 },
        Achievement(id: "color_midnight", name: "심야의 관측자", condition: "자정~새벽(0~4시)에 5번 기록하기", reward: .color(13)) { $0.nightPosts >= 5 },
        Achievement(id: "color_life", name: "생명의 인연", condition: "친구 5명 만들기", reward: .color(14)) { $0.friends >= 5 },
        Achievement(id: "color_gold", name: "황금빛 발견", condition: "총 조회수 300 달성하기", reward: .color(15)) { $0.viewsReceived >= 300 },
        Achievement(id: "color_nebula", name: "성운의 빛", condition: "다이어리 50개 작성하기", reward: .color(12)) { $0.diariesCreated >= 50 },
        // 색 보상(그라데이션)
        Achievement(id: "color_grad_aurora", name: "오로라의 주인", condition: "좋아요 300개 받기", reward: .color(16)) { $0.likesReceived >= 300 },
        Achievement(id: "color_grad_emerald", name: "수많은 벗", condition: "친구 20명 만들기", reward: .color(17)) { $0.friends >= 20 },
        Achievement(id: "color_grad_sunset", name: "백 개의 별빛", condition: "다이어리 100개 작성하기", reward: .color(18)) { $0.diariesCreated >= 100 },
        Achievement(id: "color_grad_glacier", name: "은하의 정복자", condition: "총 조회수 1,000 달성하기", reward: .color(19)) { $0.viewsReceived >= 1000 },
        Achievement(id: "color_grad_dawn", name: "여명을 기다린 자", condition: "자정~새벽(0~4시)에 10번 기록하기", reward: .color(20)) { $0.nightPosts >= 10 },
    ]

    static var all: [Achievement] { titleAchievements + rewardAchievements }

    static func byId(_ id: String?) -> Achievement? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    static func unlockedIds(_ stats: UserStats) -> Set<String> {
        Set(all.filter { $0.unlocked(stats) }.map { $0.id })
    }

    /// 내 다이어리 + 친구/열람 수로 통계를 계산. (Android rememberUserStats 의 파생 로직 — 규칙 동일)
    /// invitedFriends/redeemedInvite 는 InviteStore.fetchStats 로 조회해 넘긴다(기본 0/false).
    /// `allDiaries`(DiaryStore.diaries)·`unlockedIds`(DiaryUnlockStore) 는 이웃 별/눈밭/별 도감 업적용 —
    /// 넘기지 않으면 그 업적들은 미달성으로 본다. `uid` 는 남의 글을 가려낼 때 쓴다.
    static func computeStats(
        diaries: [Diary], friendsCount: Int, viewedCount: Int,
        invitedFriends: Int = 0, redeemedInvite: Bool = false,
        uid: String? = nil, allDiaries: [Diary] = [], unlockedIds: Set<String> = []
    ) -> UserStats {
        let coords = diaries.filter { $0.latitude != 0 || $0.longitude != 0 }
        var maxSpan = 0.0
        if coords.count > 1 {
            for i in 0..<coords.count {
                for j in (i + 1)..<coords.count {
                    let d = Geo.distanceMeters(lat1: coords[i].latitude, lng1: coords[i].longitude,
                                               lat2: coords[j].latitude, lng2: coords[j].longitude)
                    if d > maxSpan { maxSpan = d }
                }
            }
        }
        // ── 현지 시간 파생 ──
        let cal = Calendar.current
        let timed = diaries.filter { $0.createdAt > 0 }
        let comps = timed.map {
            cal.dateComponents([.year, .month, .day, .hour, .minute],
                               from: Date(timeIntervalSince1970: TimeInterval($0.createdAt) / 1000))
        }
        let dayKeys = comps.map { ($0.year ?? 0) * 10_000 + ($0.month ?? 0) * 100 + ($0.day ?? 0) }
        var dayCounts: [Int: Int] = [:]
        for k in dayKeys { dayCounts[k, default: 0] += 1 }
        // 최장 연속 일수 — 날짜 키를 실제 날짜로 바꿔 하루 차이인지 본다.
        let dates = Set(timed.map { cal.startOfDay(for: Date(timeIntervalSince1970: TimeInterval($0.createdAt) / 1000)) })
            .sorted()
        var best = 0, streak = 0
        var prev: Date?
        for d in dates {
            if let p = prev, let next = cal.date(byAdding: .day, value: 1, to: p), cal.isDate(next, inSameDayAs: d) {
                streak += 1
            } else {
                streak = 1
            }
            best = max(best, streak)
            prev = d
        }
        // 서로 1km 이상 떨어진 장소 — 작성 순서대로 기존 장소들과 모두 1km 이상이면 새 장소.
        var places: [Diary] = []
        for d in coords.sorted(by: { $0.createdAt < $1.createdAt })
        where !places.contains(where: { within($0, d, 1000) }) {
            places.append(d)
        }
        let cluster = coords.map { c in coords.filter { within(c, $0, 1000) }.count }.max() ?? 0
        let revisit = coords.contains { a in
            coords.contains { b in b.createdAt - a.createdAt > 30 * dayMs && within(a, b, 50) }
        }
        // ── 남의 별과 비교(전체 목록이 비어 있으면 판정 안 함 — 로드 전 오판 방지) ──
        var firstInArea = 0
        var nearNeighbor = false
        if !coords.isEmpty && !allDiaries.isEmpty {
            let myUid = uid ?? diaries.first?.userId ?? ""
            let others = allDiaries.filter { $0.userId != myUid && ($0.latitude != 0 || $0.longitude != 0) }
            firstInArea = coords.filter { m in
                !others.contains { o in o.createdAt < m.createdAt && within(m, o, 1000) }
            }.count
            nearNeighbor = coords.contains { m in others.contains { within(m, $0, 100) } }
        }
        let owner = uid ?? diaries.first?.userId
        let starLogCount = owner.map { owner in
            allDiaries.filter { $0.userId != owner && unlockedIds.contains($0.id ?? "") }.count
        } ?? 0
        // ── 히든 업적 판정용 파생 ──
        let keywordHit = diaries.contains { $0.title.contains(HiddenAchievements.secretKeyword) }
        var regions = Set<String>()
        for d in coords {
            for lm in HiddenAchievements.remoteLandmarks where
                Geo.distanceMeters(lat1: d.latitude, lng1: d.longitude, lat2: lm.lat, lng2: lm.lng) <= HiddenAchievements.remoteRadiusM {
                regions.insert(lm.region)
            }
        }
        var stats = UserStats(
            diariesCreated: diaries.count,
            likesReceived: diaries.reduce(0) { $0 + $1.likeCount },
            viewsReceived: diaries.reduce(0) { $0 + $1.viewCount },
            diariesViewed: viewedCount,
            friends: friendsCount,
            maxSpanMeters: maxSpan,
            maxLikesOnOne: diaries.map { $0.likeCount }.max() ?? 0,
            distinctDays: dayCounts.count,
            nightPosts: comps.filter { (0...4).contains($0.hour ?? -1) }.count,
            secretKeywordTitle: keywordHit,
            remoteRegions: regions,
            invitedFriends: invitedFriends,
            redeemedInvite: redeemedInvite,
            photoPosts: diaries.filter { !$0.imageUrl.isEmpty && $0.videoUrl.isEmpty }.count,
            videoPosts: diaries.filter { !$0.videoUrl.isEmpty }.count,
            longPosts: diaries.filter { $0.content.count >= 500 }.count,
            shortPosts: diaries.filter { $0.content.trimmingCharacters(in: .whitespacesAndNewlines).count <= 20 }.count,
            privatePosts: diaries.filter { $0.visibilityType == "private" }.count,
            maxStreakDays: best,
            dawnPosts: comps.filter { (5...6).contains($0.hour ?? -1) }.count,
            maxPostsInDay: dayCounts.values.max() ?? 0,
            distinctMonths: Set(comps.compactMap { $0.month }).count,
            wishTimePost: comps.contains { ($0.hour == 11 || $0.hour == 23) && $0.minute == 11 },
            fullMoonPost: zip(timed, comps).contains { d, c in
                let h = c.hour ?? 12
                return (h >= 18 || h < 6) && isFullMoon(d.createdAt)
            },
            newYearPost: comps.contains { $0.month == 1 && $0.day == 1 },
            christmasPost: comps.contains { $0.month == 12 && ($0.day == 24 || $0.day == 25) },
            distinctPlaces: places.count,
            maxLocalCluster: cluster,
            revisitedSpot: revisit,
            firstInArea: firstInArea,
            nearNeighbor: nearNeighbor,
            starLogCount: starLogCount,
            maxViewsOnOne: diaries.map { $0.viewCount }.max() ?? 0,
            commentsReceived: diaries.reduce(0) { $0 + $1.commentCount },
            distinctShapes: Set(diaries.map { $0.starType }).count,
            distinctColors: Set(diaries.map { $0.starColor }).count
        )
        stats.achievementsDone = all.filter { $0.id != crownId && $0.unlocked(stats) }.count
        return stats
    }

    private static let dayMs: Int64 = 86_400_000

    /// 보름달 판정 — 기준 삭(2000-01-06 18:14 UTC, JD 2451550.26)부터 삭망월 29.530588853일, 월령 14.765±1일.
    /// (Android isFullMoon 과 같은 상수)
    private static func isFullMoon(_ ms: Int64) -> Bool {
        let synodic = 29.530588853
        let jd = Double(ms) / 86_400_000 + 2440587.5
        let raw = (jd - 2451550.26).truncatingRemainder(dividingBy: synodic)
        let age = raw < 0 ? raw + synodic : raw
        return abs(age - 14.765) <= 1.0
    }

    /// 두 다이어리가 `meters` 안인지 — 위도/경도 차로 먼저 거른 뒤 정확한 거리를 잰다.
    private static func within(_ a: Diary, _ b: Diary, _ meters: Double) -> Bool {
        let dLat = meters / 111_000
        if abs(a.latitude - b.latitude) > dLat { return false }
        let dLng = dLat / max(0.01, cos(a.latitude * .pi / 180))
        if abs(a.longitude - b.longitude) > dLng { return false }
        return Geo.distanceMeters(lat1: a.latitude, lng1: a.longitude, lat2: b.latitude, lng2: b.longitude) <= meters
    }
}

/// 별 모양/색의 업적 해금 매핑(보상 정의에서 자동 도출). 맵에 없으면 기본 해금.
enum StarUnlocks {
    static let shape: [Int: String] = Dictionary(uniqueKeysWithValues:
        Achievements.all.compactMap { a -> (Int, String)? in
            if case let .shape(t) = a.reward { return (t, a.id) }
            return nil
        }
    )

    static let color: [Int: String] = Dictionary(uniqueKeysWithValues:
        Achievements.all.compactMap { a -> (Int, String)? in
            if case let .color(c) = a.reward { return (c, a.id) }
            return nil
        }
    )

    static func lockedShapeAch(_ type: Int, _ unlocked: Set<String>) -> Achievement? {
        guard let id = shape[type], !unlocked.contains(id) else { return nil }
        return Achievements.byId(id)
    }

    static func lockedColorAch(_ idx: Int, _ unlocked: Set<String>) -> Achievement? {
        guard let id = color[idx], !unlocked.contains(id) else { return nil }
        return Achievements.byId(id)
    }
}
