import SwiftUI

/// 배경음악 카탈로그(순수 데이터) — Android `core.util.MusicCatalog` 의 Swift 포팅.
/// 트랙 정의(색·별모양·해금 업적)를 양 플랫폼이 동일하게 유지한다.
enum MusicCatalog {
    struct Track: Identifiable {
        let id: String
        let displayName: String
        let resName: String               // 번들 mp3 리소스명(확장자 제외)
        let colorHex: UInt32              // 별자리/다이얼 테마색(0xRRGGBB)
        let starType: Int                 // 다이얼 별 모양(StarStyle 0..8)
        let unlockAchievementId: String?  // nil = 기본 해금

        var color: Color { Color(hex: colorHex) }
    }

    /// 기본(처음부터 사용 가능) 음악 = 별의 속삭임.
    static let defaultId = "star_whisper"

    static let tracks: [Track] = [
        Track(id: "star_whisper",     displayName: "별의 속삭임", resName: "bgm_star_whisper",     colorHex: 0x6EE7B7, starType: 1, unlockAchievementId: nil),
        Track(id: "tiny_explorer",    displayName: "작은 탐험가", resName: "bgm_tiny_explorer",    colorHex: 0xFFD27F, starType: 4, unlockAchievementId: "first_step"),
        Track(id: "celestial_drift",  displayName: "천상의 표류", resName: "bgm_celestial_drift",  colorHex: 0x7FB7FF, starType: 0, unlockAchievementId: "storyteller"),
        Track(id: "cosmic_funk",      displayName: "코스믹 펑크", resName: "bgm_cosmic_funk",      colorHex: 0xFF9CC6, starType: 2, unlockAchievementId: "popular"),
        Track(id: "forgotten_galaxy", displayName: "잊혀진 은하", resName: "bgm_forgotten_galaxy", colorHex: 0xB89BFF, starType: 7, unlockAchievementId: "star_traveler"),
        Track(id: "nebula_garden",    displayName: "성운의 정원", resName: "bgm_nebula_garden",    colorHex: 0x9CE6A0, starType: 5, unlockAchievementId: "companion"),
    ]

    static func byId(_ id: String?) -> Track? {
        guard let id else { return nil }
        return tracks.first { $0.id == id }
    }

    static func index(of id: String?) -> Int {
        max(tracks.firstIndex { $0.id == id } ?? 0, 0)
    }

    static var defaultTrack: Track { byId(defaultId) ?? tracks[0] }
}
