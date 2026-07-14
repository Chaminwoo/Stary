import SwiftUI

/// 히든 업적 — **앱 전체에서 단 한 사람만** 달성 가능한 특별 업적.
/// (Android `feature.profile.HiddenAchievements` 의 Swift 포팅 — 정의를 항상 동일하게 유지.)
///
/// 표시 규칙: 달성 전에도 칭호·아이콘·이펙트는 노출, **조건은 `???`**. 누군가 달성하면 조건 공개 + `달성자: 이름`.

// MARK: - 아이콘 / 이펙트

/// 히든 업적 프로필 아이콘(SF Symbol + 대표색).
enum HiddenIcon {
    case comet, glacier, desert, trench, triangle, crown, trickster, loneEye, heart, baton, globe

    var systemImage: String {
        switch self {
        case .comet: return "sparkles"
        case .glacier: return "snowflake"
        case .desert: return "sun.max.fill"
        case .trench: return "water.waves"
        case .triangle: return "triangle.fill"
        case .crown: return "crown.fill"
        case .trickster: return "bolt.fill"
        case .loneEye: return "eye.fill"
        case .heart: return "heart.fill"
        case .baton: return "music.note"
        case .globe: return "globe.asia.australia.fill"
        }
    }

    var color: Color {
        switch self {
        case .comet: return Color(hex: 0x9BE7FF)
        case .glacier: return Color(hex: 0xCFE8FF)
        case .desert: return Color(hex: 0xF2C46B)
        case .trench: return Color(hex: 0x4FC3E0)
        case .triangle: return Color(hex: 0x9AE6C4)
        case .crown: return Color(hex: 0xFFD86F)
        case .trickster: return Color(hex: 0xB388FF)
        case .loneEye: return Color(hex: 0x9AA7B0)
        case .heart: return Color(hex: 0xFF6FA3)
        case .baton: return Color(hex: 0x7CE0C0)
        case .globe: return Color(hex: 0x6EE7B7)
        }
    }
}

/// 아이콘 주변 파티클 종류.
enum ParticleEffect: Int { case stardust, snow, aurora, ember, shadow, heart, music, orbit, bubble }

// MARK: - 모델

struct HiddenContext {
    let stats: UserStats
    /// 히든을 제외한 모든 일반 업적을 달성했는가.
    let allNormalDone: Bool
}

struct HiddenAchievement: Identifiable {
    let id: String
    let title: String        // 칭호
    let condition: String    // 달성 후 공개되는 조건
    let icon: HiddenIcon
    let effect: ParticleEffect
    /// 달성자의 **이름 옆에 붙는 전용 크리스탈 별**(모양/색 인덱스) — [HiddenStarBadges].
    /// ⚠️ Android `HiddenAchievements.kt` 의 badgeType/badgeColor 와 값이 같아야 한다.
    let badgeType: Int
    let badgeColor: Int
    /// nil 이면 이벤트형(화면에서 직접 claim). 아니면 통계로 자동 판정.
    let auto: ((HiddenContext) -> Bool)?
}

/// 히든 업적 선점(달성) 기록. Firestore `hiddenAchievements/{id}`.
struct HiddenClaim {
    var achieverId = ""
    var achieverName = ""
    var claimedAt: Int64 = 0
    var claimed: Bool { !achieverId.isEmpty }
}

struct HiddenLandmark { let region: String; let name: String; let lat: Double; let lng: Double }

enum HiddenAchievements {
    static let secretKeyword = "우주먼지"

    static let remoteLandmarks: [HiddenLandmark] = [
        HiddenLandmark(region: "glacier", name: "에베레스트", lat: 27.9881, lng: 86.9250),
        HiddenLandmark(region: "glacier", name: "남극 대륙", lat: -78.0, lng: 0.0),
        HiddenLandmark(region: "desert", name: "사하라 사막", lat: 23.4162, lng: 12.6628),
        HiddenLandmark(region: "trench", name: "마리아나 해구", lat: 11.35, lng: 142.2),
        HiddenLandmark(region: "triangle", name: "버뮤다 삼각지대", lat: 25.0, lng: -71.0),
    ]
    static let remoteRadiusM: Double = 300_000

    static let all: [HiddenAchievement] = [
        HiddenAchievement(id: "secret_word", title: "별의 암호",
                          condition: "다이어리 제목에 ‘\(secretKeyword)’ 를 넣기",
                          icon: .comet, effect: .stardust,
                          badgeType: 0, badgeColor: 8,   // 4꼭지 스파클 · 시안
                          auto: { $0.stats.secretKeywordTitle }),
        HiddenAchievement(id: "remote_place", title: "극야의 개척자",
                          condition: "남극 · 에베레스트 둘 중 한 곳에 별 남기기",
                          icon: .glacier, effect: .snow,
                          badgeType: 3, badgeColor: 19,  // 8꼭지 가는 스파클 · 빙하(시안→블루)
                          auto: { $0.stats.remoteRegions.contains("glacier") }),
        HiddenAchievement(id: "place_desert", title: "태양의 잔영",
                          condition: "사하라 사막에 별 남기기",
                          icon: .desert, effect: .ember,
                          badgeType: 1, badgeColor: 15,  // 5꼭지 별 · 앰버골드
                          auto: { $0.stats.remoteRegions.contains("desert") }),
        HiddenAchievement(id: "place_trench", title: "심연의 별",
                          condition: "마리아나 해구에 별 남기기",
                          icon: .trench, effect: .bubble,
                          badgeType: 6, badgeColor: 13,  // 다이아몬드 · 코발트
                          auto: { $0.stats.remoteRegions.contains("trench") }),
        HiddenAchievement(id: "place_triangle", title: "사라진 항로",
                          condition: "버뮤다 삼각지대에 별 남기기",
                          icon: .triangle, effect: .aurora,
                          badgeType: 4, badgeColor: 17,  // 다이아 스파클 · 에메랄드 오로라
                          auto: { $0.stats.remoteRegions.contains("triangle") }),
        HiddenAchievement(id: "all_rounder", title: "은하의 정점",
                          condition: "히든을 제외한 모든 업적 달성하기",
                          icon: .crown, effect: .aurora,
                          badgeType: 2, badgeColor: 18,  // 6꼭지 별 · 석양(골드→코랄)
                          auto: { $0.allNormalDone }),
        HiddenAchievement(id: "cosmic_rascal", title: "별도둑",
                          condition: "다른 사람의 다이어리 300개 열람하기",
                          icon: .trickster, effect: .ember,
                          badgeType: 7, badgeColor: 6,   // 초승달 · 퍼플
                          auto: { $0.stats.diariesViewed >= 300 }),
        HiddenAchievement(id: "lone_observer", title: "홀로 빛나는 별",
                          condition: "친구 없이 다이어리 50개 작성하기",
                          icon: .loneEye, effect: .shadow,
                          badgeType: 8, badgeColor: 20,  // 행성 · 흑백(밤→여명)
                          auto: { $0.stats.diariesCreated >= 50 && $0.stats.friends == 0 }),
        // ── 이벤트형(후속 라운드에서 화면 연동) ──
        HiddenAchievement(id: "heart_frenzy", title: "두근두근",
                          condition: "프로필에서 나가지 않고 하트를 100번 두드리기",
                          icon: .heart, effect: .heart,
                          badgeType: 5, badgeColor: 4,   // 꽃 · 핑크
                          auto: nil),
        HiddenAchievement(id: "melomaniac", title: "별들의 오케스트라",
                          condition: "배경음악 화면에서 나가지 않고 모든 곡 감상하기",
                          icon: .baton, effect: .music,
                          badgeType: 0, badgeColor: 9,   // 4꼭지 스파클 · 민트
                          auto: nil),
        HiddenAchievement(id: "earth_pilgrim", title: "푸른 행성의 발자취",
                          condition: "세계 유명 관광지에 별을 남기고 다른 사람이 그 별을 열람하기",
                          icon: .globe, effect: .orbit,
                          badgeType: 2, badgeColor: 14,  // 6꼭지 별 · 에메랄드
                          auto: nil),
    ]

    static func byId(_ id: String?) -> HiddenAchievement? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    /// 통계상 자동 달성 조건을 만족한 히든 업적 id.
    static func satisfiedAutoIds(_ ctx: HiddenContext) -> Set<String> {
        Set(all.filter { $0.auto?(ctx) == true }.map { $0.id })
    }
}

/// 장착 칭호 id → 표시 이름. 일반 업적 + 히든 업적 통합 조회.
func equippedTitleName(_ id: String?) -> String? {
    guard let id, !id.isEmpty else { return nil }
    return Achievements.byId(id)?.titleName ?? HiddenAchievements.byId(id)?.title
}

// MARK: - 배지 + 파티클 뷰

/// 히든 업적 아이콘 + 주변 파티클(팝업·프로필·목록 공용).
struct HiddenIconBadge: View {
    let ach: HiddenAchievement
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            HiddenParticlesView(effect: ach.effect, tint: ach.icon.color, size: size)
            Image(systemName: ach.icon.systemImage)
                .font(.system(size: size * 0.5, weight: .semibold))
                .foregroundStyle(ach.icon.color)
        }
        .frame(width: size, height: size)
    }
}

private struct PSeed {
    let angle: Double, radiusFrac: Double, speed: Double, phase: Double, sizeFrac: Double, drift: Double
}

/// 결정적(시드) 난수 — 플랫폼 간 재현성보다 "안정된 배치"가 목적.
private struct SeededRNG {
    var state: UInt64
    init(_ seed: UInt64) { state = seed &* 2862933555777941757 &+ 3037000493 }
    mutating func nextDouble() -> Double {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Double(state >> 11) / Double(UInt64(1) << 53)
    }
}

private enum Motion { case orbit, rise, fall }

/// 아이콘 주변에 효과별로 움직이는 입자. 레이아웃은 [size] 로 두되 그리기는 넉넉히 넘치게 한다.
struct HiddenParticlesView: View {
    let effect: ParticleEffect
    let tint: Color
    let size: CGFloat
    private let seeds: [PSeed]

    init(effect: ParticleEffect, tint: Color, size: CGFloat, count: Int = 12) {
        self.effect = effect
        self.tint = tint
        self.size = size
        var arr: [PSeed] = []
        for i in 0..<count {
            var r = SeededRNG(UInt64(i * 9973 + effect.rawValue * 31 + 7))
            arr.append(PSeed(
                angle: r.nextDouble() * 2 * .pi,
                radiusFrac: 0.30 + r.nextDouble() * 0.22,
                speed: 0.35 + r.nextDouble() * 0.85,
                phase: r.nextDouble(),
                sizeFrac: 0.035 + r.nextDouble() * 0.05,
                drift: (r.nextDouble() - 0.5) * 0.5
            ))
        }
        seeds = arr
    }

    private var motion: Motion {
        switch effect {
        case .snow: return .fall
        case .ember, .heart, .music, .bubble: return .rise
        default: return .orbit
        }
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            Canvas { ctx, sz in
                let cx = Double(sz.width) / 2, cy = Double(sz.height) / 2
                let half = Double(min(sz.width, sz.height)) / 2
                let dim: Double = (effect == .shadow) ? 0.5 : 1
                for s in seeds {
                    let rad = s.sizeFrac * half * (0.8 + 0.4 * sin(t * s.speed * 3 + s.phase * 6.28))
                    var x = cx, y = cy, alpha = 1.0
                    switch motion {
                    case .orbit:
                        let a = s.angle + t * s.speed * 0.9
                        let rr = s.radiusFrac * half * (1 + 0.10 * sin(t * 1.3 + s.phase * 6.28))
                        x = cx + cos(a) * rr
                        y = cy + sin(a) * rr
                        alpha = (0.35 + 0.55 * (0.5 + 0.5 * sin(t * 2.2 * s.speed + s.phase * 6.28))) * dim
                    case .rise:
                        let p = (t * s.speed * 0.5 + s.phase).truncatingRemainder(dividingBy: 1)
                        x = cx + s.drift * half + sin((p + s.phase) * 6.28) * half * 0.14
                        y = cy + half * 0.9 - p * half * 1.8
                        alpha = (1 - p) * 0.85
                    case .fall:
                        let p = (t * s.speed * 0.4 + s.phase).truncatingRemainder(dividingBy: 1)
                        x = cx + s.drift * half + sin((p + s.phase) * 6.28) * half * 0.16
                        y = cy - half * 0.9 + p * half * 1.8
                        alpha = p < 0.15 ? p / 0.15 : (p > 0.85 ? (1 - p) / 0.15 : 0.85)
                    }
                    let col = particleColor(s: s, t: t)
                    let rect = CGRect(x: x - rad, y: y - rad, width: rad * 2, height: rad * 2)
                    ctx.fill(Path(ellipseIn: rect), with: .color(col.opacity(max(0, min(1, alpha)))))
                }
            }
        }
        .frame(width: size * 1.7, height: size * 1.7)
        .allowsHitTesting(false)
    }

    private func particleColor(s: PSeed, t: Double) -> Color {
        switch effect {
        case .stardust: return tint.blended(with: .white, fraction: 0.5 + 0.5 * sin(t * 2 + s.phase * 6.28))
        case .aurora: return Color(hex: 0xFFD86F).blended(with: Color(hex: 0xB388FF), fraction: 0.5 + 0.5 * sin(t * 1.2 + s.phase * 6.28))
        case .shadow: return Color(hex: 0x9AA7B0)
        case .ember: return Color(hex: 0xFFB74D).blended(with: Color(hex: 0xFF7043), fraction: s.phase)
        case .heart: return Color(hex: 0xFF8FB3).blended(with: Color(hex: 0xFF4D79), fraction: s.phase)
        case .music: return tint.blended(with: .white, fraction: 0.3)
        case .snow: return .white
        case .orbit: return tint
        case .bubble: return Color(hex: 0x7FE0FF).blended(with: .white, fraction: 0.5 + 0.5 * sin(t * 2.4 + s.phase * 6.28))
        }
    }
}
