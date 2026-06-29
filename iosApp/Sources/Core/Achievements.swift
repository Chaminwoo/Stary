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
    var distinctDays = 0
    var nightPosts = 0              // 자정~새벽(0~4시 UTC)에 올린 기록 수
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

enum Achievements {
    static let titleAchievements: [Achievement] = [
        Achievement(id: "first_step", name: "첫 발자국", condition: "다이어리 1개 작성하기", reward: .title("첫 발자국")) { $0.diariesCreated >= 1 },
        Achievement(id: "star_traveler", name: "별의 여행자", condition: "다른 사람의 다이어리 10개 열람하기", reward: .title("별의 여행자")) { $0.diariesViewed >= 10 },
        Achievement(id: "storyteller", name: "이야기꾼", condition: "다이어리 10개 작성하기", reward: .title("이야기꾼")) { $0.diariesCreated >= 10 },
        Achievement(id: "popular", name: "인기쟁이", condition: "좋아요 50개 받기", reward: .title("인기쟁이")) { $0.likesReceived >= 50 },
        Achievement(id: "watched_star", name: "주목받는 별", condition: "총 조회수 100 달성하기", reward: .title("주목받는 별")) { $0.viewsReceived >= 100 },
        Achievement(id: "companion", name: "길동무", condition: "친구 3명 만들기", reward: .title("길동무")) { $0.friends >= 3 },
        Achievement(id: "pilgrim", name: "우주의 순례자", condition: "다이어리 30개 작성하기", reward: .title("우주의 순례자")) { $0.diariesCreated >= 30 },
        Achievement(id: "guide", name: "별빛의 인도자", condition: "좋아요 200개 받기", reward: .title("별빛의 인도자")) { $0.likesReceived >= 200 },
        Achievement(id: "cosmic_rascal", name: "우주의 악동", condition: "???", reward: .title("우주의 악동"), hidden: true) { $0.diariesViewed >= 42 },
        Achievement(id: "lone_observer", name: "고독한 관측자", condition: "???", reward: .title("고독한 관측자"), hidden: true) { $0.diariesCreated >= 20 && $0.friends == 0 },
    ]

    static let rewardAchievements: [Achievement] = [
        // 모양 보상
        Achievement(id: "shape_flower", name: "별꽃을 피운 자", condition: "좋아요 80개 받기", reward: .shape(5)) { $0.likesReceived >= 80 },
        Achievement(id: "shape_gem", name: "결정의 시간", condition: "서로 다른 14일에 기록하기", reward: .shape(6)) { $0.distinctDays >= 14 },
        Achievement(id: "shape_moon", name: "달의 인도자", condition: "총 조회수 500 달성하기", reward: .shape(7)) { $0.viewsReceived >= 500 },
        Achievement(id: "shape_planet", name: "나만의 행성", condition: "서로 다른 30일에 기록하기", reward: .shape(8)) { $0.distinctDays >= 30 },
        Achievement(id: "shape_farjourney", name: "머나먼 여정", condition: "기록 두 곳이 50km 이상 떨어지기", reward: .shape(3)) { $0.maxSpanMeters >= 50_000 },
        Achievement(id: "shape_border", name: "국경을 넘어", condition: "기록 두 곳이 1,000km 이상 떨어지기", reward: .shape(4)) { $0.maxSpanMeters >= 1_000_000 },
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

    /// 내 다이어리 + 친구/열람 수로 통계를 계산. (Android rememberUserStats 의 파생 로직)
    static func computeStats(diaries: [Diary], friendsCount: Int, viewedCount: Int) -> UserStats {
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
        let days = Set(diaries.filter { $0.createdAt > 0 }.map { $0.createdAt / 86_400_000 }).count
        let night = diaries.filter { $0.createdAt > 0 && (0...4).contains(Int(($0.createdAt / 3_600_000) % 24)) }.count
        return UserStats(
            diariesCreated: diaries.count,
            likesReceived: diaries.reduce(0) { $0 + $1.likeCount },
            viewsReceived: diaries.reduce(0) { $0 + $1.viewCount },
            diariesViewed: viewedCount,
            friends: friendsCount,
            maxSpanMeters: maxSpan,
            maxLikesOnOne: diaries.map { $0.likeCount }.max() ?? 0,
            distinctDays: days,
            nightPosts: night
        )
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
