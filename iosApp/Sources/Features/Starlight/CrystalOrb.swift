import QuartzCore
import SwiftUI
import UIKit

/*
 * 수정구(2026-10-26) — 별가루 화면의 우주 항해 무대와 항해 연출이 함께 쓰는 그림. 시안 "천문 원반 받침"(C안).
 * Android `CrystalOrb.kt` 와 같은 수치.
 *
 *  - 무대는 시안 단위 340 × 420(그리는 쪽이 pt/단위 k 를 곱한다). 구슬 가운데 (170, 186), 반지름 94, 원반 윗면 y 344.
 *  - 받침: 법랑 원반(금 눈금 72개 · 동심원 · 별자리가 천천히 돈다) + 금속 목·띠 + 구슬을 받치는 고리(뒤 반쪽은 구슬 뒤, 앞 반쪽은 앞).
 *  - 구슬: 두 점 원뿔 그라데이션 유리(굽는다) 안에서 성운 덩어리 46개 · 별 티끌 24개가 돈다. 앞면에 프레넬 테 · 바닥 집광 ·
 *    부드러운 반사광 · 날카로운 빛줄. 일반 탐사 = 맑은 유리·푸른 빛·은, 희귀 탐사 = 연기 유리·금빛·금(경로를 바꾸면 0.6초 동안 섞인다).
 *  - 깨짐: 누른 자리에서 방사형 금 + 거미줄 금. 세 번째 두드림 뒤 1초 동안 결과 등급 빛으로 모이고(converge),
 *    누른 자리를 중심으로 보로노이로 자른 조각이 1.8초 동안 흩어진다(조각마다 깨지기 직전 구슬 그림을 그대로 입힌다).
 *    등급이 높을수록 빛이 넓고 화려하다(일반: 흰 빛 한 번 · 희귀: 빛줄기 8 · 영웅: 보랏빛 번쩍임 + 14 · 전설: 금빛 섬광 + 22 + 금가루).
 *  - 화면 번쩍임(영웅·전설)은 "동작 줄이기"면 생략하고 움직임도 느리게(`OrbEngine.motion`).
 *
 * 그리기는 SwiftUI Canvas(GraphicsContext) — Android 의 'lighter'(더하기) 합성은 `.plusLighter`.
 * 프레임은 CADisplayLink 가 `step` 하고 `OrbFrames.tick` 을 올려 `OrbCanvas` 만 다시 그린다(화면 body 는 다시 계산하지 않는다).
 * 조각에 입힐 구슬 사진은 그리는 도중이 아니라 프레임 콜백에서 `ImageRenderer` 로 한 번 찍는다.
 */

// MARK: - 무대 치수(시안 단위)

let orbW: CGFloat = 340
let orbH: CGFloat = 420
let orbCX: CGFloat = 170
let orbCY: CGFloat = 186
let orbR: CGFloat = 94
/// 깨진 뒤 이만큼(초) 지나면 카드가 솟는다 — 조각이 거의 다 떨어졌을 때.
let orbCardsAt: CGFloat = 1.5

private let diskY: CGFloat = 344
private let orbTau = CGFloat.pi * 2
private let convDur: CGFloat = 1.0
private let shatDur: CGFloat = 1.8
private let grav: CGFloat = 240

private func c01(_ x: CGFloat) -> CGFloat { x < 0 ? 0 : (x > 1 ? 1 : x) }
private func inC(_ t: CGFloat) -> CGFloat { t * t * t }
private func outC(_ t: CGFloat) -> CGFloat { let u = 1 - t; return 1 - u * u * u }
private func backOut(_ t: CGFloat) -> CGFloat {
    let c1: CGFloat = 1.70158
    let c3 = c1 + 1
    let u = t - 1
    return 1 + c3 * u * u * u + c1 * u * u
}
private func easeInOut(_ t: CGFloat) -> CGFloat {
    if t < 0.5 { return 4 * t * t * t }
    let v = -2 * t + 2
    return 1 - v * v * v / 2
}

private func circlePath(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// 타원 호(Android drawArc 와 같은 매개 각도) — a0 → a1(라디안, 화면에서 시계 방향으로 커진다).
private func arcPath(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ a0: CGFloat, _ a1: CGFloat) -> Path {
    var p = Path()
    p.addArc(center: .zero, radius: 1, startAngle: .radians(Double(a0)), endAngle: .radians(Double(a1)), clockwise: false)
    return p.applying(CGAffineTransform(a: rx, b: 0, c: 0, d: ry, tx: cx, ty: cy))
}

// MARK: - 색

/// 0..1 sRGB 색 — 섞기·알파 덮어쓰기를 숫자로 하고 그릴 때만 Color 로 바꾼다(Android Int 색과 같은 쓰임).
private struct OrbRGB {
    var r: CGFloat
    var g: CGFloat
    var b: CGFloat
    var a: CGFloat

    init(r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    init(_ r: Int, _ g: Int, _ b: Int, _ a: CGFloat = 1) {
        self.init(r: CGFloat(r) / 255, g: CGFloat(g) / 255, b: CGFloat(b) / 255, a: a)
    }

    init(hex: UInt32) {
        self.init(Int((hex >> 16) & 0xFF), Int((hex >> 8) & 0xFF), Int(hex & 0xFF))
    }

    static let white = OrbRGB(255, 255, 255)
    static let black = OrbRGB(0, 0, 0)

    /// 알파를 덮어쓴 색(0..1 로 자른다 — 시안 rgba() 와 같다).
    func color(_ alpha: CGFloat) -> Color {
        Color(.sRGB, red: Double(r), green: Double(g), blue: Double(b), opacity: Double(c01(alpha)))
    }

    func mix(_ o: OrbRGB, _ t: CGFloat) -> OrbRGB {
        let u = c01(t)
        return OrbRGB(r: r + (o.r - r) * u, g: g + (o.g - g) * u, b: b + (o.b - b) * u, a: a + (o.a - a) * u)
    }
}

/// 결과 등급 빛(시안 값 — 더하기 합성에 맞춘 색).
private func burstColor(_ rarity: String) -> OrbRGB {
    switch rarity {
    case "rare": return OrbRGB(hex: 0x7CC8FF)
    case "epic": return OrbRGB(hex: 0xB47CFF)
    case "legendary": return OrbRGB(hex: 0xFFD27A)
    default: return OrbRGB(hex: 0xEEF3FB)
    }
}

private func rarityLevel(_ rarity: String) -> Int {
    switch rarity {
    case "rare": return 1
    case "epic": return 2
    case "legendary": return 3
    default: return 0
    }
}

/// 일반 탐사 = 맑은 유리·푸른 빛·은 / 희귀 탐사 = 연기 유리·금빛·금.
private struct OrbPalette {
    var fog: [OrbRGB]
    var ink: OrbRGB
    var body: [OrbRGB]
    var rim: OrbRGB
    var caustic: OrbRGB
    var glow: OrbRGB
    var star: OrbRGB
    var metal: [OrbRGB]
    var enamel: [OrbRGB]

    static let normal = OrbPalette(
        fog: [OrbRGB(79, 125, 255), OrbRGB(127, 176, 255), OrbRGB(155, 123, 255), OrbRGB(95, 211, 255)],
        ink: OrbRGB(3, 5, 18),
        body: [OrbRGB(44, 66, 140, 0.2), OrbRGB(14, 20, 52, 0.58), OrbRGB(5, 8, 24, 0.92)],
        rim: OrbRGB(200, 220, 255), caustic: OrbRGB(150, 195, 255), glow: OrbRGB(90, 140, 255), star: OrbRGB(221, 232, 255),
        metal: [OrbRGB(hex: 0xF7F9FD), OrbRGB(hex: 0xC5CEDC), OrbRGB(hex: 0x8A94A8), OrbRGB(hex: 0x4E5668)],
        enamel: [OrbRGB(hex: 0x1B2452), OrbRGB(hex: 0x080B20)]
    )

    static let rare = OrbPalette(
        fog: [OrbRGB(255, 179, 71), OrbRGB(255, 210, 122), OrbRGB(255, 138, 92), OrbRGB(255, 230, 168)],
        ink: OrbRGB(10, 5, 3),
        body: [OrbRGB(90, 52, 30, 0.24), OrbRGB(26, 14, 14, 0.66), OrbRGB(8, 5, 7, 0.94)],
        rim: OrbRGB(255, 222, 160), caustic: OrbRGB(255, 201, 112), glow: OrbRGB(255, 159, 67), star: OrbRGB(255, 233, 176),
        metal: [OrbRGB(hex: 0xFFF2CC), OrbRGB(hex: 0xFFC970), OrbRGB(hex: 0xC98A3A), OrbRGB(hex: 0x6E461A)],
        enamel: [OrbRGB(hex: 0x2E1C15), OrbRGB(hex: 0x0D0706)]
    )

    static func lerp(_ a: OrbPalette, _ b: OrbPalette, _ t: CGFloat) -> OrbPalette {
        func m(_ x: [OrbRGB], _ y: [OrbRGB]) -> [OrbRGB] { zip(x, y).map { $0.mix($1, t) } }
        return OrbPalette(
            fog: m(a.fog, b.fog), ink: a.ink.mix(b.ink, t), body: m(a.body, b.body),
            rim: a.rim.mix(b.rim, t), caustic: a.caustic.mix(b.caustic, t), glow: a.glow.mix(b.glow, t), star: a.star.mix(b.star, t),
            metal: m(a.metal, b.metal), enamel: m(a.enamel, b.enamel)
        )
    }
}

// MARK: - 한 번 굽는 것

/// 유리 몸통 — 시안의 두 점 원뿔 그라데이션(안쪽 원 (-.25, -.3) r .05 → 바깥 원 (0, 0) r 1)을 직접 굽는다.
/// 색은 미리 곱한 알파로 섞는다(Android `OrbInk.body` 와 같은 식).
private enum OrbInk {
    static let bodyNormal: Image? = bake(OrbPalette.normal.body).map { Image(decorative: $0, scale: 1).interpolation(.high) }
    static let bodyRare: Image? = bake(OrbPalette.rare.body).map { Image(decorative: $0, scale: 1).interpolation(.high) }

    static func body(rare: Bool) -> Image? { rare ? bodyRare : bodyNormal }

    private static func bake(_ cols: [OrbRGB]) -> CGImage? {
        let n = 256
        var px = [UInt8](repeating: 0, count: n * n * 4)
        let dcx: CGFloat = 0.25
        let dcy: CGFloat = 0.3
        let r0: CGFloat = 0.05
        let dr: CGFloat = 0.95
        let qa = dcx * dcx + dcy * dcy - dr * dr
        let mid: CGFloat = 0.72
        let fn = CGFloat(n)
        for j in 0..<n {
            for i in 0..<n {
                let x = (CGFloat(i) + 0.5) / fn * 2 - 1
                let y = (CGFloat(j) + 0.5) / fn * 2 - 1
                let dist = (x * x + y * y).squareRoot()
                let cover = c01((1 - dist) * fn / 2 + 0.5)
                if cover <= 0 { continue }
                let qx = x + dcx
                let qy = y + dcy
                let qb = -2 * (qx * dcx + qy * dcy + r0 * dr)
                let qc = qx * qx + qy * qy - r0 * r0
                let disc = qb * qb - 4 * qa * qc
                var t: CGFloat = 1
                if disc >= 0 {
                    let s = disc.squareRoot()
                    let t1 = (-qb + s) / (2 * qa)
                    let t2 = (-qb - s) / (2 * qa)
                    let best = max(t1, t2)
                    t = r0 + best * dr >= 0 ? best : min(t1, t2)
                }
                t = c01(t)
                let k = t <= mid ? 0 : 1
                let u = k == 0 ? t / mid : (t - mid) / (1 - mid)
                let c0 = cols[k]
                let c1 = cols[k + 1]
                let al = c0.a + (c1.a - c0.a) * u
                // 미리 곱한 값으로 섞고, 덮는 비율(cover)만큼 다시 곱한다.
                let pr = (c0.r * c0.a + (c1.r * c1.a - c0.r * c0.a) * u) * cover
                let pg = (c0.g * c0.a + (c1.g * c1.a - c0.g * c0.a) * u) * cover
                let pb = (c0.b * c0.a + (c1.b * c1.a - c0.b * c0.a) * u) * cover
                let o = (j * n + i) * 4
                px[o] = UInt8(clamping: Int((pr * 255).rounded()))
                px[o + 1] = UInt8(clamping: Int((pg * 255).rounded()))
                px[o + 2] = UInt8(clamping: Int((pb * 255).rounded()))
                px[o + 3] = UInt8(clamping: Int((al * cover * 255).rounded()))
            }
        }
        guard let provider = CGDataProvider(data: Data(px) as CFData),
              let space = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        return CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4, space: space,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}

/// 시드 고정 난수(SplitMix64) — 같은 시드면 같은 하늘.
private final class OrbRandom {
    private var s: UInt64

    init(_ seed: UInt64) { s = seed }

    private func next() -> UInt64 {
        s &+= 0x9E37_79B9_7F4A_7C15
        var z = s
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// [0, 1)
    func f() -> CGFloat { CGFloat(next() >> 40) / 16_777_216 }

    func int(_ n: Int) -> Int { n <= 0 ? 0 : Int(next() % UInt64(n)) }
}

// MARK: - 조각 · 입자

private struct OrbRay {
    let xs: [CGFloat]
    let ys: [CGFloat]
    let cum: [CGFloat]
    let start: CGFloat
    let len: CGFloat
    var n: Int { xs.count }
}

private struct OrbRingSeg {
    let ax: CGFloat, ay: CGFloat
    let mx: CGFloat, my: CGFloat
    let bx: CGFloat, by: CGFloat
    let d: CGFloat
}

private final class OrbImpact {
    let x: CGFloat
    let y: CGFloat
    let rays: [OrbRay]
    let rings: [OrbRingSeg]
    var target: CGFloat
    var g: CGFloat = 0

    init(x: CGFloat, y: CGFloat, rays: [OrbRay], rings: [OrbRingSeg], target: CGFloat) {
        self.x = x
        self.y = y
        self.rays = rays
        self.rings = rings
        self.target = target
    }
}

private struct OrbShard {
    let path: Path
    let x0: CGFloat, y0: CGFloat
    let vx: CGFloat, vy: CGFloat
    let vr: CGFloat, vt: CGFloat, gp: CGFloat
    let life: CGFloat, delay: CGFloat
}

private struct OrbGrit {
    let x0: CGFloat, y0: CGFloat, vx: CGFloat, vy: CGFloat, s: CGFloat, life: CGFloat, ph: CGFloat
}

private struct OrbBlob {
    let rr: CGFloat, a: CGFloat, w: CGFloat, h: CGFloat, s: CGFloat
    let c: Int
    let ph: CGFloat
    let dark: Bool
}

private struct OrbMote {
    let rr: CGFloat, a: CGFloat, w: CGFloat, h: CGFloat, s: CGFloat, ph: CGFloat, f: CGFloat
}

private struct OrbSpark {
    let a: CGFloat, v: CGFloat, s: CGFloat, g: CGFloat, ph: CGFloat
    let lv: Int
    let gold: Bool
}

private struct OrbGlit {
    let a: CGFloat, r: CGFloat, w: CGFloat, y: CGFloat, v: CGFloat, s: CGFloat, ph: CGFloat
}

// MARK: - 엔진

/// 프레임 번호 — 이것만 구독하는 `OrbCanvas` 가 매 프레임 다시 그린다(엔진 상태 변화로 화면 전체가 다시 계산되지 않게).
final class OrbFrames: ObservableObject {
    @Published var tick = 0
}

/// 수정구 한 개의 상태와 그림. 프레임마다 `step` → `draw`.
/// `interactive` = false(별가루 화면 무대)면 두드려도 금이 가지 않고 빛만 출렁인다.
/// 메인 스레드에서만 쓴다(CADisplayLink · SwiftUI) — Canvas 그리기 클로저에서도 부를 수 있게 actor 격리는 두지 않는다.
final class OrbEngine: ObservableObject {
    enum Phase { case idle, wait, converge, shatter, reveal }

    @Published private(set) var phase = Phase.idle
    @Published private(set) var taps = 0
    /// 깨진 뒤 `orbCardsAt` 이 지났거나 건너뛰었다 — 카드를 띄울 때.
    @Published private(set) var cardsReady = false

    /// 1 = 보통, 0.35 = "동작 줄이기"(시안 prefers-reduced-motion).
    private(set) var motion: CGFloat = 1
    /// 영웅·전설 화면 번쩍임 — 동작 줄이기를 켠 사람에겐 끈다.
    private(set) var flashOk = true
    /// 결과 등급이 정해져 모이기 시작할 때 / 깨지는 순간(등급 0..3) — 진동은 부르는 쪽이.
    var onConverge: (() -> Void)?
    var onShatter: ((Int) -> Void)?
    /// 조각 사진 해상도(화면 배율) — 뷰가 displayScale 로 맞춘다.
    var pxScale: CGFloat = 3
    let frames = OrbFrames()

    private(set) var t: CGFloat = 0
    private var clock: CGFloat = 0
    private var pulse: CGFloat = 0
    private var pt: CGFloat = 0
    private var conv: CGFloat = 0
    private var lv = 0
    private var rc = OrbRGB.white
    private var pendingRarity: String?

    private var from = OrbPalette.normal
    private var to = OrbPalette.normal
    private var toRare = false
    private var mixT: CGFloat = 1
    private var mixFrom: CGFloat = 0
    private var pal = OrbPalette.normal

    private let interactive: Bool
    private let rnd: OrbRandom
    private let blobs: [OrbBlob]
    private let motes: [OrbMote]
    private let sparks: [OrbSpark]
    private let glit: [OrbGlit]

    private var impacts: [OrbImpact] = []
    private var shards: [OrbShard] = []
    private var dust: [OrbGrit] = []
    private var shardImage: Image?
    private var shardOrigin = CGPoint.zero
    private var needShards = false
    private var bx = orbCX
    private var by = orbCY
    private var lastK: CGFloat = 1

    private var link: CADisplayLink?
    private var lastTs: CFTimeInterval = 0

    private static let rings: [(CGFloat, CGFloat)] = [(0.93, 0.6), (0.8, 0.45), (0.58, 0.35)]
    private static let starAngles: [CGFloat] = [0, 0.5, 1.1, 1.6, 2.4, 3, 3.7, 4.5, 5.3]
    private static let starLines: [(Int, Int)] = [(0, 1), (1, 2), (2, 3), (4, 5), (5, 6), (7, 8)]
    private static let rayCounts = [0, 8, 14, 22]

    init(seed: UInt64, interactive: Bool, rare: Bool) {
        self.interactive = interactive
        let r = OrbRandom(seed)
        rnd = r
        blobs = (0..<46).map { _ in
            let rr = 0.12 + 0.78 * r.f().squareRoot()
            let a = r.f() * orbTau
            let w = 0.22 + 0.34 * r.f()
            let h = (r.f() - 0.5) * 1.1
            let s = 0.2 + 0.3 * r.f()
            let c = r.int(4)
            let ph = r.f() * orbTau
            return OrbBlob(rr: rr, a: a, w: w, h: h, s: s, c: c, ph: ph, dark: r.f() < 0.24)
        }
        motes = (0..<24).map { _ in
            let rr = 0.1 + 0.78 * r.f()
            let a = r.f() * orbTau
            let w = 0.3 + 0.4 * r.f()
            let h = (r.f() - 0.5) * 1.3
            let s = 0.6 + 1.2 * r.f()
            let ph = r.f() * orbTau
            return OrbMote(rr: rr, a: a, w: w, h: h, s: s, ph: ph, f: 1.5 + 3 * r.f())
        }
        sparks = (0..<110).map { i in
            let a = r.f() * orbTau
            let v = 0.4 + r.f() * 1.1
            let s = 1.5 + r.f() * 3.5
            let g = r.f()
            let ph = r.f() * orbTau
            let lv = i < 8 ? 0 : i < 22 ? 1 : i < 50 ? 2 : 3
            return OrbSpark(a: a, v: v, s: s, g: g, ph: ph, lv: lv, gold: r.f() < 0.5)
        }
        glit = (0..<26).map { _ in
            let a = r.f() * orbTau
            let rr = 0.2 + 0.9 * r.f()
            let w = (r.f() - 0.5) * 0.6
            let y = r.f()
            let v = 0.12 + 0.15 * r.f()
            let s = 1.5 + 2.5 * r.f()
            return OrbGlit(a: a, r: rr, w: w, y: y, v: v, s: s, ph: r.f() * orbTau)
        }
        setRoute(rare: rare, animate: false)
    }

    deinit {
        link?.invalidate()
    }

    private var going: Bool { phase == .converge || phase == .shatter || phase == .reveal }

    /// 깨지기 시작한 뒤 흐른 시간(초), 깨지기 전엔 -1.
    var shatterAge: CGFloat {
        switch phase {
        case .shatter: return pt
        case .reveal: return shatDur + pt
        default: return -1
        }
    }

    /// 지금 경로 빛이 희귀 쪽으로 얼마나 섞였나(0 일반 … 1 희귀) — 바탕 성운 색을 같이 바꾼다.
    var routeMix: CGFloat {
        let end: CGFloat = toRare ? 1 : 0
        if mixT >= 1 { return end }
        return mixFrom + (end - mixFrom) * easeInOut(mixT)
    }

    /// "동작 줄이기" — 움직임을 줄이고 번쩍임을 뺀다.
    func setReducedMotion(_ on: Bool) {
        motion = on ? 0.35 : 1
        flashOk = !on
    }

    /// 경로 색 — animate 면 0.6초 동안 섞는다(무대에서 경로를 바꿀 때).
    func setRoute(rare: Bool, animate: Bool) {
        let target = rare ? OrbPalette.rare : OrbPalette.normal
        if animate {
            // 이미 그쪽으로 가는 중이면 그대로(섞는 도중 되돌려도 지금 색에서 이어 간다).
            if rare == toRare { return }
            mixFrom = routeMix
            from = pal
            to = target
            toRare = rare
            mixT = 0
        } else {
            from = target
            to = target
            toRare = rare
            mixT = 1
            mixFrom = rare ? 1 : 0
            pal = target
        }
    }

    /// 결과 등급 — 세 번 두드린 뒤라면 바로 모이기 시작한다.
    func setResult(_ rarity: String) {
        pendingRarity = rarity
        if phase == .wait { startConverge() }
    }

    /// 건너뛰기 — 조각 없이 결과 빛만 남기고 카드로.
    func skip() {
        guard let r = pendingRarity else { return }
        lv = rarityLevel(r)
        rc = burstColor(r)
        shards = []
        dust = []
        needShards = false
        phase = .reveal
        pt = 3
        if !cardsReady { cardsReady = true }
    }

    /// 무대에서 두드림 — 시안 단위 좌표(nil = 아무 데나). 금이 갔으면 true.
    @discardableResult
    func tap(_ ux: CGFloat?, _ uy: CGFloat?) -> Bool {
        pulse = 1
        guard interactive, phase == .idle, taps < 3 else { return false }
        var x: CGFloat = ux.map { ($0 - orbCX) / orbR } ?? 9
        var y: CGFloat = uy.map { ($0 - orbCY) / orbR } ?? 9
        if (x * x + y * y).squareRoot() > 0.9 {
            let a = rnd.f() * orbTau
            let d = 0.15 + rnd.f() * 0.45
            x = cos(a) * d
            y = sin(a) * d
        }
        taps += 1
        switch taps {
        case 1:
            impacts.append(makeImpact(x, y, 0.32))
        case 2:
            impacts[0].target = 0.7
            impacts.append(makeImpact(x, y, 0.36))
        default:
            impacts.append(makeImpact(x, y, 0.25))
            if pendingRarity != nil {
                startConverge()
            } else {
                // 결과를 기다리는 동안 — 금이 천천히 번지고 구슬이 떤다.
                phase = .wait
                for im in impacts { im.target = max(im.target, 0.9) }
            }
        }
        return true
    }

    private func startConverge() {
        guard let r = pendingRarity else { return }
        lv = rarityLevel(r)
        rc = burstColor(r)
        for im in impacts { im.target = 1.5 }
        phase = .converge
        pt = 0
        onConverge?()
    }

    func step(_ dt: CGFloat) {
        t += dt
        pulse = max(0, pulse - dt * 1.8)
        if mixT < 1 {
            mixT = min(1, mixT + dt / 0.6)
            pal = OrbPalette.lerp(from, to, easeInOut(mixT))
        }
        let rate: CGFloat
        switch phase {
        case .converge: rate = 2.6
        case .wait: rate = 1.2
        default: rate = 8
        }
        let follow = min(1, dt * rate)
        for im in impacts { im.g += (im.target - im.g) * follow }
        switch phase {
        case .converge:
            pt += dt
            if pt >= convDur {
                phase = .shatter
                pt = 0
                needShards = true
                onShatter?(lv)
            }
        case .shatter:
            pt += dt
            if pt >= shatDur {
                phase = .reveal
                pt = 0
            }
        case .reveal:
            pt += dt
        default:
            break
        }
        switch phase {
        case .converge: conv = c01(pt / convDur)
        case .idle, .wait: conv = 0
        default: conv = 1
        }
        var speed: CGFloat = 1 + CGFloat(taps) * 1.2 + pulse * 2.6
        if phase == .converge { speed += conv * 10 }
        if phase == .wait { speed += 1.5 }
        clock += dt * motion * speed
        if !cardsReady && shatterAge >= orbCardsAt { cardsReady = true }
    }

    // MARK: 프레임 시계

    /// 프레임 시계 켜기/끄기 — 화면이 보이는 동안만(무대 수정구는 항해 연출 창이 덮는 동안 쉰다).
    @MainActor
    func run(_ on: Bool) {
        if on {
            guard link == nil else { return }
            let l = CADisplayLink(target: OrbLinkProxy(self), selector: #selector(OrbLinkProxy.tick(_:)))
            // 120Hz 화면에서도 60프레임이면 충분하다(그라데이션이 많아 배터리를 아낀다).
            l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
            l.add(to: .main, forMode: .common)
            link = l
            lastTs = 0
        } else {
            link?.invalidate()
            link = nil
        }
    }

    @MainActor
    fileprivate func frame(_ l: CADisplayLink) {
        let now = l.timestamp
        let dt: CGFloat = lastTs == 0 ? 0 : CGFloat(min(max(now - lastTs, 0), 0.05))
        lastTs = now
        step(dt)
        if needShards { buildShards() }
        frames.tick &+= 1
    }

    // MARK: 그리기

    /// 한 프레임 — 캔버스 pt 좌표에서 무대 원점이 (ox, oy), 시안 1단위 = k pt. viewW×viewH 는 캔버스 크기(번쩍임용).
    /// 카드가 뜨면 stageAlpha 로 받침·문장을 지우고, 후광은 auraShift 만큼 (auraX, auraY)(시안 단위)로 옮긴다.
    func draw(
        _ c: inout GraphicsContext, k: CGFloat, ox: CGFloat, oy: CGFloat, viewW: CGFloat, viewH: CGFloat,
        stageAlpha: CGFloat = 1, auraShift: CGFloat = 0, auraX: CGFloat = orbCX, auraY: CGFloat = orbCY
    ) {
        guard k > 0 else { return }
        lastK = k
        var g = c
        g.translateBy(x: ox, y: oy)
        g.scaleBy(x: k, y: k)
        let view = CGRect(x: -ox / k, y: -oy / k, width: viewW / k, height: viewH / k)
        let sa = c01(stageAlpha)
        if sa > 0 { inLayer(&g, sa) { l in self.standBack(&l) } }
        switch phase {
        case .idle, .wait, .converge:
            var x = orbCX
            var y = orbCY
            let j: CGFloat = phase == .converge ? conv * 2 : (phase == .wait ? 0.7 : 0)
            if j > 0 {
                x += (rnd.f() - 0.5) * j
                y += (rnd.f() - 0.5) * j
            }
            drawBall(&g, x, y, layered: true)
            bx = x
            by = y
            collar(&g, orbCX, orbCY + orbR * 0.9, front: true)
        case .shatter, .reveal:
            let s = shatterAge
            let ax = bx + (auraX - bx) * auraShift
            let ay = by + (auraY - by) * auraShift
            aura(&g, ax, ay, orbR, c01((s - 0.4) / 1) * (1 - 0.35 * auraShift))
            if sa > 0 {
                let es = orbR * 0.28 * backOut(c01((s - 0.06) / 0.5))
                inLayer(&g, sa) { l in
                    self.emblem(&l, self.bx, self.by, es, self.rc, c01(s / 0.25))
                    self.collar(&l, orbCX, orbCY + orbR * 0.9, front: true)
                }
            }
            if phase == .shatter {
                drawShards(&g, s)
                rarityBurst(&g, bx, by, orbR, s, view)
            }
        }
    }

    /// alpha < 1 이면 한 장으로 모아 그 투명도로 합친다(Android saveLayerAlpha).
    private func inLayer(_ c: inout GraphicsContext, _ alpha: CGFloat, _ body: (inout GraphicsContext) -> Void) {
        if alpha >= 0.999 {
            body(&c)
            return
        }
        var l = c
        l.opacity = Double(alpha)
        l.drawLayer { inner in
            inner.opacity = 1
            body(&inner)
        }
    }

    // MARK: 빛·반짝이

    private func glow(_ c: inout GraphicsContext, _ col: OrbRGB, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a: CGFloat, add: Bool) {
        guard a > 0, r > 0 else { return }
        let al = min(a, 1)
        let stops: [Gradient.Stop] = [
            .init(color: col.color(al), location: 0),
            .init(color: col.color(al * 0.6), location: 0.22),
            .init(color: col.color(al * 0.16), location: 0.55),
            .init(color: col.color(0), location: 1),
        ]
        let prev = c.blendMode
        c.blendMode = add ? .plusLighter : .normal
        c.fill(
            Path(CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(Gradient(stops: stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
        )
        c.blendMode = prev
    }

    /// 네 꼭지 반짝이(가운데를 조절점으로 휜 변).
    private func sparkle(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a: CGFloat, _ col: OrbRGB, add: Bool) {
        guard a > 0, r > 0 else { return }
        let prev = c.blendMode
        c.blendMode = add ? .plusLighter : .normal
        c.fill(StarlightArt.sparkle(CGPoint(x: x, y: y), r), with: .color(col.color(a)))
        c.blendMode = prev
    }

    private func metalGrad(_ x0: CGFloat, _ x1: CGFloat, _ alpha: CGFloat = 1) -> GraphicsContext.Shading {
        let m = pal.metal
        let cols = [m[3], m[1], m[0], m[2], m[1], m[0], m[3]]
        let locs: [CGFloat] = [0, 0.15, 0.32, 0.5, 0.7, 0.86, 1]
        var stops: [Gradient.Stop] = []
        for i in 0..<cols.count { stops.append(.init(color: cols[i].color(alpha), location: locs[i])) }
        return .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0))
    }

    // MARK: 받침(천문 원반)

    private func standBack(_ c: inout GraphicsContext) {
        let p = pal
        let m = p.metal
        let cx = orbCX
        let y = diskY
        let rx: CGFloat = 126
        let ry: CGFloat = 25
        let h: CGFloat = 11
        let f = ry / rx
        let lc = going ? p.glow.mix(rc, conv) : p.glow
        // 바닥 그림자 + 은은한 바닥 빛
        var sh = c
        sh.translateBy(x: cx, y: y + h + 8)
        sh.scaleBy(x: 1, y: 0.15)
        sh.fill(circlePath(0, 0, 150), with: .radialGradient(
            Gradient(colors: [OrbRGB.black.color(0.55), OrbRGB.black.color(0)]), center: .zero, startRadius: 0, endRadius: 150))
        glow(&sh, p.glow, 0, 0, 150 * 1.25, 0.1, add: true)
        // 두께 있는 원반(아래 면 → 옆면 → 법랑 윗면 → 앞 테두리 빛)
        c.fill(Path(ellipseIn: CGRect(x: cx - rx, y: y + h - ry, width: rx * 2, height: ry * 2)), with: .color(m[3].color(1)))
        let side = metalGrad(cx - rx, cx + rx)
        c.fill(Path(CGRect(x: cx - rx, y: y, width: rx * 2, height: h)), with: side)
        var lower = arcPath(cx, y + h, rx, ry, 0, .pi)
        lower.closeSubpath()
        c.fill(lower, with: side)
        c.fill(Path(ellipseIn: CGRect(x: cx - rx, y: y - ry, width: rx * 2, height: ry * 2)), with: .radialGradient(
            Gradient(colors: [p.enamel[0].color(1), p.enamel[1].color(1)]), center: CGPoint(x: cx, y: y - 3), startRadius: 0, endRadius: rx))
        c.stroke(arcPath(cx, y, rx, ry, 0.08, .pi - 0.08), with: .color(m[0].color(0.7)), lineWidth: 0.8)
        // 원반 위 빛 웅덩이(두드릴수록 · 깨질 때 밝아진다)
        var pool = c
        pool.translateBy(x: cx, y: y)
        pool.scaleBy(x: 1, y: f)
        let poolA: CGFloat = 0.2 + CGFloat(taps) * 0.06 + pulse * 0.2 + (going ? 0.3 : 0)
        glow(&pool, lc, 0, 0, rx * 0.75, poolA, add: true)
        // 동심원
        for (kk, a) in Self.rings {
            let rect = CGRect(x: cx - rx * kk, y: y - ry * kk, width: rx * kk * 2, height: ry * kk * 2)
            c.stroke(Path(ellipseIn: rect), with: .color(m[1].color(a)), lineWidth: 0.8)
        }
        // 눈금 72개(큰 눈금 6칸마다) — 천천히 돈다
        let rot = t * 0.05 * motion
        var major = Path()
        var minor = Path()
        for i in 0..<72 {
            let a = CGFloat(i) / 72 * orbTau + rot
            let isMajor = i % 6 == 0
            let r0 = rx * (isMajor ? 0.8 : 0.86)
            let r1 = rx * 0.93
            let ca = cos(a)
            let sa = sin(a)
            let p0 = CGPoint(x: cx + ca * r0, y: y + sa * r0 * f)
            let p1 = CGPoint(x: cx + ca * r1, y: y + sa * r1 * f)
            if isMajor {
                major.move(to: p0)
                major.addLine(to: p1)
            } else {
                minor.move(to: p0)
                minor.addLine(to: p1)
            }
        }
        c.stroke(major, with: .color(m[1].color(0.6)), lineWidth: 0.9)
        c.stroke(minor, with: .color(m[1].color(0.32)), lineWidth: 0.6)
        // 별자리 — 눈금과 반대로 돈다
        var sxs = [CGFloat](repeating: 0, count: 9)
        var sys = [CGFloat](repeating: 0, count: 9)
        for i in 0..<9 {
            let a = Self.starAngles[i] - rot * 1.4
            let r = rx * (0.66 + CGFloat(i % 3) * 0.035)
            sxs[i] = cx + cos(a) * r
            sys[i] = y + sin(a) * r * f
        }
        var lines = Path()
        for e in Self.starLines {
            lines.move(to: CGPoint(x: sxs[e.0], y: sys[e.0]))
            lines.addLine(to: CGPoint(x: sxs[e.1], y: sys[e.1]))
        }
        c.stroke(lines, with: .color(m[1].color(0.28)), lineWidth: 0.6)
        for i in 0..<9 {
            glow(&c, m[1], sxs[i], sys[i], 5, 0.5, add: true)
            let tw = 0.7 + 0.3 * sin(t * 2 + CGFloat(i))
            sparkle(&c, sxs[i], sys[i], 2 + CGFloat(i % 3), tw, m[0], add: true)
        }
        // 목과 띠
        let top = orbCY + orbR * 0.9
        var neck = Path()
        neck.move(to: CGPoint(x: cx - 11, y: top))
        neck.addLine(to: CGPoint(x: cx + 11, y: top))
        neck.addLine(to: CGPoint(x: cx + 17, y: y))
        neck.addLine(to: CGPoint(x: cx - 17, y: y))
        neck.closeSubpath()
        c.fill(neck, with: metalGrad(cx - 18, cx + 18))
        c.fill(Path(ellipseIn: CGRect(x: cx - 17, y: y - 3.4, width: 34, height: 6.8)), with: .color(OrbRGB.black.color(0.25)))
        for yy in [y - 12, top + 14] {
            let w = 11 + (yy - top) / (y - top) * 6 + 2
            c.fill(Path(ellipseIn: CGRect(x: cx - w, y: yy - 2.6, width: w * 2, height: 5.2)), with: metalGrad(cx - w, cx + w))
            c.stroke(arcPath(cx, yy, w, 2.6, 0.1, .pi - 0.1), with: .color(m[0].color(0.7)), lineWidth: 0.6)
        }
        collar(&c, cx, top, front: false)
    }

    /// 구슬을 받치는 고리 — 뒤 반쪽(구슬 뒤) / 앞 반쪽(구슬 앞, 위 모서리 빛).
    private func collar(_ c: inout GraphicsContext, _ cx: CGFloat, _ y: CGFloat, front: Bool) {
        let m = pal.metal
        let rx: CGFloat = 40
        let ry: CGFloat = 8.5
        let alpha: CGFloat = front ? 1 : 0.8
        let a0: CGFloat = front ? 0 : .pi
        let ring = arcPath(cx, y, rx, ry, a0, a0 + .pi)
        c.stroke(ring, with: .color(m[3].color(alpha)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
        c.stroke(ring, with: metalGrad(cx - rx, cx + rx, alpha), style: StrokeStyle(lineWidth: 3.6, lineCap: .round))
        if front {
            c.stroke(arcPath(cx, y - 1, rx, ry, 0.2, .pi - 0.2), with: .color(m[0].color(0.9)),
                     style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        }
    }

    // MARK: 유리구슬

    private func drawBody(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let rect = CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
        let m = routeMix
        if m <= 0.001 || m >= 0.999 {
            if let img = OrbInk.body(rare: m >= 0.999) { c.draw(img, in: rect) }
            return
        }
        var l = c
        if let a = OrbInk.body(rare: false) {
            l.opacity = Double(1 - m)
            l.draw(a, in: rect)
        }
        if let b = OrbInk.body(rare: true) {
            l.opacity = Double(m)
            l.draw(b, in: rect)
        }
    }

    /// 구슬 한 장 — layered 면 구슬 칸만 따로 그려 합친다(성운 빛이 무대가 아니라 유리에만 더해지게,
    /// 그리고 깨질 때 찍는 사진과 같은 모습이 되게).
    private func drawBall(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, layered: Bool) {
        glow(&c, pal.glow, cx, cy, orbR * 2.1, 0.2, add: true)
        if layered {
            c.drawLayer { l in self.ballCore(&l, cx, cy) }
        } else {
            ballCore(&c, cx, cy)
        }
    }

    private func ballCore(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat) {
        let p = pal
        let r = orbR
        let cv = inC(conv)
        let g = going
        drawBody(&c, cx, cy, r)
        var inner = c
        inner.clip(to: circlePath(cx, cy, r * 0.985))
        for b in blobs {
            let a = b.a + clock * b.w * (1.25 - b.rr * 0.55)
            let rr = b.rr * (1 - cv * 0.95)
            let d = sin(a)
            let x = cx + cos(a) * rr * r + sin(t * 0.6 + b.ph) * r * 0.035
            let y = cy + b.h * r * 0.62 * (1 - cv) + d * rr * r * 0.3
            let sz = b.s * r * (0.8 + 0.25 * d) * (1 - cv * 0.55)
            if b.dark {
                glow(&inner, p.ink, x, y, sz * 1.1, 0.42 * (1 - cv), add: false)
            } else {
                let col = g ? p.fog[b.c].mix(rc, cv) : p.fog[b.c]
                let a0: CGFloat = 0.11 + 0.035 * (d + 1)
                glow(&inner, col, x, y, sz, a0 * (1 + pulse * 0.7 + cv * 1.2), add: true)
            }
        }
        for s in motes {
            let a = s.a + clock * s.w
            let rr = s.rr * (1 - cv)
            let d = sin(a)
            let x = cx + cos(a) * rr * r
            let y = cy + s.h * r * 0.6 * (1 - cv) + d * rr * r * 0.3
            let tw = 0.5 + 0.5 * sin(t * s.f + s.ph)
            let col = g ? p.star.mix(rc, cv) : p.star
            glow(&inner, col, x, y, r * 0.045 * s.s * (1 + d * 0.3), 0.35 * tw, add: true)
            inner.blendMode = .plusLighter
            inner.fill(Path(CGRect(x: x - 0.6, y: y - 0.6, width: 1.2, height: 1.2)), with: .color(OrbRGB.white.color(0.4 + 0.6 * tw)))
            inner.blendMode = .normal
            if s.s > 1.5 { sparkle(&inner, x, y, 3.5 * tw + 1, 0.8 * tw, .white, add: true) }
        }
        if g {
            glow(&inner, rc, cx, cy, r * (0.15 + 0.55 * cv), cv * 0.95, add: true)
            glow(&inner, .white, cx, cy, r * (0.05 + 0.14 * cv), cv, add: true)
        }
        glassFront(&c, cx, cy, r)
        if !impacts.isEmpty {
            let col = g ? p.rim.mix(rc, conv) : p.rim
            let ga: CGFloat = g ? 0.4 + 0.6 * conv : 0.25 + pulse * 0.4
            drawCracks(&c, cx, cy, r, col, ga)
        }
    }

    /// 유리 앞면 — 프레넬 테 · 바닥 집광(타원 + 호) · 부드러운 반사광 · 날카로운 빛줄 둘 · 점 반짝임 · 가는 테.
    private func glassFront(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let p = pal
        var add = c
        add.blendMode = .plusLighter
        // 프레넬: 안쪽 .78R 까지 비우고 가장자리에서 밝게
        let fresnel: [Gradient.Stop] = [
            .init(color: p.rim.color(0), location: 0),
            .init(color: p.rim.color(0), location: 0.78),
            .init(color: p.rim.color(0.1), location: 0.78 + 0.22 * 0.8),
            .init(color: p.rim.color(0.38), location: 0.78 + 0.22 * 0.96),
            .init(color: p.rim.color(0.12), location: 1),
        ]
        add.fill(circlePath(cx, cy, r), with: .radialGradient(
            Gradient(stops: fresnel), center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: r))
        // 바닥 집광 타원
        var caustic = add
        caustic.translateBy(x: cx, y: cy + r * 0.66)
        caustic.scaleBy(x: 1, y: 0.42)
        let cStops: [Gradient.Stop] = [
            .init(color: p.caustic.color(0.42), location: 0),
            .init(color: p.caustic.color(0.14), location: 0.5),
            .init(color: p.caustic.color(0), location: 1),
        ]
        caustic.fill(circlePath(0, 0, r * 0.62), with: .radialGradient(
            Gradient(stops: cStops), center: .zero, startRadius: 0, endRadius: r * 0.62))
        // 바닥 집광 호
        let arcStops: [Gradient.Stop] = [
            .init(color: p.caustic.color(0), location: 0),
            .init(color: p.caustic.color(0.55), location: 0.5),
            .init(color: p.caustic.color(0), location: 1),
        ]
        add.stroke(
            arcPath(cx, cy, r * 0.9, r * 0.9, 0.22 * .pi, 0.78 * .pi),
            with: .linearGradient(Gradient(stops: arcStops), startPoint: CGPoint(x: cx - r * 0.7, y: 0), endPoint: CGPoint(x: cx + r * 0.7, y: 0)),
            style: StrokeStyle(lineWidth: r * 0.025, lineCap: .round)
        )
        // 부드러운 반사광(왼쪽 위, 기울인 타원)
        var soft = c
        soft.translateBy(x: cx - r * 0.36, y: cy - r * 0.42)
        soft.rotate(by: .radians(-0.7))
        soft.scaleBy(x: 1, y: 0.62)
        let sStops: [Gradient.Stop] = [
            .init(color: OrbRGB.white.color(0.42), location: 0),
            .init(color: OrbRGB.white.color(0.1), location: 0.55),
            .init(color: OrbRGB.white.color(0), location: 1),
        ]
        soft.fill(circlePath(0, 0, r * 0.34), with: .radialGradient(
            Gradient(stops: sStops), center: .zero, startRadius: 0, endRadius: r * 0.34))
        // 날카로운 빛줄(가운데가 가장 밝고 양끝이 사라진다)
        let a0 = CGFloat.pi * 1.07
        let a1 = CGFloat.pi * 1.4
        let seg = (a1 - a0) / 16
        let sharp = StrokeStyle(lineWidth: r * 0.042, lineCap: .butt)
        for i in 0..<16 {
            let u0 = CGFloat(i) / 16
            let u1 = CGFloat(i + 1) / 16
            let w = sin(CGFloat.pi * (u0 + u1) / 2)
            let s0 = a0 + (a1 - a0) * u0
            c.stroke(arcPath(cx, cy, r * 0.8, r * 0.8, s0, s0 + seg + 0.003), with: .color(OrbRGB.white.color(0.75 * w * w)), style: sharp)
        }
        let thin = StrokeStyle(lineWidth: r * 0.018, lineCap: .butt)
        for i in 0..<10 {
            let u0 = CGFloat(i) / 10
            let u1 = CGFloat(i + 1) / 10
            let w = sin(CGFloat.pi * (u0 + u1) / 2)
            let s0 = CGFloat.pi * (0.08 + 0.22 * u0)
            let s1 = s0 + CGFloat.pi * 0.022 + 0.003
            c.stroke(arcPath(cx, cy, r * 0.86, r * 0.86, s0, s1), with: .color(OrbRGB.white.color(0.28 * w)), style: thin)
        }
        glow(&c, .white, cx - r * 0.16, cy - r * 0.7, r * 0.06, 0.7, add: true)
        c.stroke(circlePath(cx, cy, r), with: .color(p.rim.color(0.35)), lineWidth: 1)
    }

    // MARK: 금

    private func crackPoint(_ ray: OrbRay, _ s: CGFloat) -> CGPoint? {
        if s > ray.len || ray.n < 2 { return nil }
        for j in 1..<ray.n where ray.cum[j] >= s {
            let u = (s - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
            return CGPoint(x: ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u, y: ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u)
        }
        return nil
    }

    /// 누른 자리(구슬 단위 좌표)에서 갈라져 나가는 방사형 금 6~9 + 가지 + 그 사이를 잇는 거미줄 금.
    private func makeImpact(_ x: CGFloat, _ y: CGFloat, _ target: CGFloat) -> OrbImpact {
        let r = rnd
        var rays: [OrbRay] = []
        var mains: [OrbRay] = []
        let n = 6 + r.int(4)
        let base = r.f() * orbTau
        func grow(_ sx: CGFloat, _ sy: CGFloat, _ ang: CGFloat, _ start: CGFloat, _ maxLen: CGFloat) -> OrbRay {
            var xs: [CGFloat] = [sx]
            var ys: [CGFloat] = [sy]
            var cum: [CGFloat] = [0]
            var px = sx
            var py = sy
            var a = ang
            var len: CGFloat = 0
            while len < maxLen {
                let step = 0.04 + 0.07 * r.f()
                a += (r.f() - 0.5) * 0.24
                px += cos(a) * step
                py += sin(a) * step
                len += step
                xs.append(px)
                ys.append(py)
                cum.append(len)
                if px * px + py * py > 1.03 { break }
            }
            return OrbRay(xs: xs, ys: ys, cum: cum, start: start, len: len)
        }
        for i in 0..<n {
            let ang = base + CGFloat(i) / CGFloat(n) * orbTau + (r.f() - 0.5) * 0.55
            let ray = grow(x, y, ang, 0, 0.35 + r.f() * 1.1)
            rays.append(ray)
            mains.append(ray)
            if ray.n > 4 && r.f() < 0.4 {
                let k = 1 + r.int(ray.n - 3)
                let side: CGFloat = r.f() < 0.5 ? -1 : 1
                let branch = ang + side * (0.45 + r.f() * 0.6)
                rays.append(grow(ray.xs[k], ray.ys[k], branch, ray.cum[k], 0.15 + r.f() * 0.6))
            }
        }
        var rings: [OrbRingSeg] = []
        let depths: [CGFloat] = [0.07, 0.15, 0.27, 0.43]
        for d in depths {
            if r.f() < 0.2 { continue }
            for i in 0..<n {
                if r.f() < 0.3 { continue }
                let pa = crackPoint(mains[i], d * (0.85 + 0.3 * r.f()))
                let pb = crackPoint(mains[(i + 1) % n], d * (0.85 + 0.3 * r.f()))
                if let a = pa, let b = pb {
                    let mx = (a.x + b.x) / 2 + (r.f() - 0.5) * 0.04
                    let my = (a.y + b.y) / 2 + (r.f() - 0.5) * 0.04
                    rings.append(OrbRingSeg(ax: a.x, ay: a.y, mx: mx, my: my, bx: b.x, by: b.y, d: d))
                }
            }
        }
        return OrbImpact(x: x, y: y, rays: rays, rings: rings, target: target)
    }

    private func drawCracks(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ col: OrbRGB, _ ga: CGFloat) {
        var g = c
        g.clip(to: circlePath(cx, cy, r * 0.995))
        let dark = StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round)
        let wide = StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round)
        let thin = StrokeStyle(lineWidth: 0.75, lineCap: .round, lineJoin: .round)
        let bright = col.mix(.white, 0.65)
        for im in impacts {
            let gv = im.g
            if gv <= 0.001 { continue }
            var crack = Path()
            for ray in im.rays {
                let vis = gv - ray.start
                if vis <= 0 || ray.n < 2 { continue }
                crack.move(to: CGPoint(x: cx + ray.xs[0] * r, y: cy + ray.ys[0] * r))
                for j in 1..<ray.n {
                    if ray.cum[j] <= vis {
                        crack.addLine(to: CGPoint(x: cx + ray.xs[j] * r, y: cy + ray.ys[j] * r))
                        continue
                    }
                    let u = (vis - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
                    let px = ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u
                    let py = ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u
                    crack.addLine(to: CGPoint(x: cx + px * r, y: cy + py * r))
                    break
                }
            }
            for s in im.rings where gv >= s.d * 1.3 + 0.04 {
                crack.move(to: CGPoint(x: cx + s.ax * r, y: cy + s.ay * r))
                crack.addQuadCurve(to: CGPoint(x: cx + s.bx * r, y: cy + s.by * r), control: CGPoint(x: cx + s.mx * r, y: cy + s.my * r))
            }
            g.blendMode = .normal
            g.stroke(crack, with: .color(OrbRGB.black.color(0.4)), style: dark)
            g.blendMode = .plusLighter
            g.stroke(crack, with: .color(col.color(ga * 0.4)), style: wide)
            g.stroke(crack, with: .color(bright.color(0.6 + ga * 0.4)), style: thin)
            g.blendMode = .normal
            let ix = cx + im.x * r
            let iy = cy + im.y * r
            let kk = min(1, gv * 4)
            glow(&g, col, ix, iy, r * 0.14 * kk, 0.3 + ga * 0.4, add: true)
            glow(&g, .white, ix, iy, r * 0.04 * kk, 0.85 * kk, add: true)
        }
    }

    // MARK: 조각

    /// 볼록 다각형([x0,y0,x1,y1,…])을 반평면 n·(p - m) ≤ 0 으로 자른다.
    private func clipHalf(_ poly: [CGFloat], _ nx: CGFloat, _ ny: CGFloat, _ mx: CGFloat, _ my: CGFloat) -> [CGFloat] {
        let n = poly.count / 2
        var out: [CGFloat] = []
        out.reserveCapacity((n + 1) * 2 + 2)
        for i in 0..<n {
            let ax = poly[i * 2]
            let ay = poly[i * 2 + 1]
            let j = (i + 1) % n
            let qx = poly[j * 2]
            let qy = poly[j * 2 + 1]
            let da = (ax - mx) * nx + (ay - my) * ny
            let db = (qx - mx) * nx + (qy - my) * ny
            if da <= 0 {
                out.append(ax)
                out.append(ay)
            }
            if (da <= 0) != (db <= 0) {
                let u = da / (da - db)
                out.append(ax + (qx - ax) * u)
                out.append(ay + (qy - ay) * u)
            }
        }
        return out
    }

    /// 누른 자리 둘레에 씨앗을 동심원으로 뿌려 보로노이로 자른다(바깥쪽일수록 큰 조각), 절반은 한 번 더 쪼갠다.
    private func shardCells(_ ix: CGFloat, _ iy: CGFloat) -> [[CGFloat]] {
        var seeds: [CGPoint] = [CGPoint(x: ix, y: iy)]
        for k in 0..<6 {
            let rad = 0.07 * pow(1.8, CGFloat(k))
            let n = 7 + 3 * k
            let off = rnd.f() * orbTau
            let cell = orbTau / CGFloat(n)
            for i in 0..<n {
                let a = off + CGFloat(i) * cell + (rnd.f() - 0.5) * cell * 0.7
                let rr = rad * (0.7 + 0.6 * rnd.f())
                let sx = ix + cos(a) * rr
                let sy = iy + sin(a) * rr
                if sx * sx + sy * sy < 1.8 { seeds.append(CGPoint(x: sx, y: sy)) }
            }
        }
        var disk = [CGFloat](repeating: 0, count: 96)
        for i in 0..<48 {
            let a = CGFloat(i) / 48 * orbTau
            disk[i * 2] = cos(a)
            disk[i * 2 + 1] = sin(a)
        }
        var cells: [[CGFloat]] = []
        for (si, s) in seeds.enumerated() {
            var poly = disk
            for (qi, q) in seeds.enumerated() where qi != si {
                poly = clipHalf(poly, q.x - s.x, q.y - s.y, (s.x + q.x) / 2, (s.y + q.y) / 2)
                if poly.count < 6 { break }
            }
            if poly.count < 6 { continue }
            if poly.count >= 10 && rnd.f() < 0.5 {
                var mx: CGFloat = 0
                var my: CGFloat = 0
                let n = poly.count / 2
                for i in 0..<n {
                    mx += poly[i * 2]
                    my += poly[i * 2 + 1]
                }
                mx /= CGFloat(n)
                my /= CGFloat(n)
                let ang = rnd.f() * CGFloat.pi
                let a = clipHalf(poly, cos(ang), sin(ang), mx, my)
                let b = clipHalf(poly, -cos(ang), -sin(ang), mx, my)
                if a.count >= 6 { cells.append(a) }
                if b.count >= 6 { cells.append(b) }
            } else {
                cells.append(poly)
            }
        }
        return cells
    }

    /// 깨지기 직전 구슬을 한 장 찍어 조각마다 입힌다 — 조각이 돌며 날아가도 그 자리 그림이 따라간다.
    @MainActor
    private func buildShards() {
        needShards = false
        let r = orbR
        let pad: CGFloat = 2
        let ox = bx - r - pad
        let oy = by - r - pad
        let side = (r + pad) * 2
        let scale = min(max(lastK * pxScale, 1), 3)
        shardImage = snapshot(ox: ox, oy: oy, side: side, scale: scale)
        shardOrigin = CGPoint(x: ox, y: oy)
        let last = impacts.last
        let ix = last?.x ?? 0
        let iy = last?.y ?? 0
        let cx = bx
        let cy = by
        var list: [OrbShard] = []
        for poly in shardCells(ix, iy) {
            let n = poly.count / 2
            var ux: CGFloat = 0
            var uy: CGFloat = 0
            for i in 0..<n {
                ux += poly[i * 2]
                uy += poly[i * 2 + 1]
            }
            ux /= CGFloat(n)
            uy /= CGFloat(n)
            var sp = Path()
            for i in 0..<n {
                let pnt = CGPoint(x: (poly[i * 2] - ux) * r, y: (poly[i * 2 + 1] - uy) * r)
                if i == 0 { sp.move(to: pnt) } else { sp.addLine(to: pnt) }
            }
            sp.closeSubpath()
            var dx = ux - ix
            var dy = uy - iy
            let dRaw = (dx * dx + dy * dy).squareRoot()
            let dist = dRaw == 0 ? 1 : dRaw
            dx /= dist
            dy /= dist
            let cRaw = (ux * ux + uy * uy).squareRoot()
            let cl = cRaw == 0 ? 1 : cRaw
            var vx = dx * 0.65 + ux / cl * 0.35
            var vy = dy * 0.65 + uy / cl * 0.35
            let vRaw = (vx * vx + vy * vy).squareRoot()
            let vl = vRaw == 0 ? 1 : vRaw
            vx /= vl
            vy /= vl
            let speed = r * (1.4 + 1.8 * rnd.f()) * (1.2 - 0.45 * min(1, dist))
            let vr = (rnd.f() - 0.5) * 7
            let vt = (rnd.f() - 0.5) * 10
            let gp = rnd.f() * orbTau
            let life = 0.9 + rnd.f() * 0.7
            list.append(OrbShard(
                path: sp, x0: cx + ux * r, y0: cy + uy * r, vx: vx * speed, vy: vy * speed - r * 0.7,
                vr: vr, vt: vt, gp: gp, life: life, delay: dist * 0.07
            ))
        }
        shards = list
        var grit: [OrbGrit] = []
        for _ in 0..<90 {
            let a = rnd.f() * orbTau
            let speed = r * (1 + 2.6 * rnd.f())
            let x0 = cx + (ix + (rnd.f() - 0.5) * 0.7) * r
            let y0 = cy + (iy + (rnd.f() - 0.5) * 0.7) * r
            let s = 0.6 + rnd.f() * 1.2
            let life = 0.5 + rnd.f() * 0.8
            grit.append(OrbGrit(x0: x0, y0: y0, vx: cos(a) * speed, vy: sin(a) * speed - r * 0.5, s: s, life: life, ph: rnd.f() * orbTau))
        }
        dust = grit
    }

    /// 구슬만 따로 그린 사진(시안 단위 side × side, 왼쪽 위 = (ox, oy)).
    @MainActor
    private func snapshot(ox: CGFloat, oy: CGFloat, side: CGFloat, scale: CGFloat) -> Image? {
        let cx = bx
        let cy = by
        let content = Canvas { ctx, _ in
            ctx.translateBy(x: -ox, y: -oy)
            self.drawBall(&ctx, cx, cy, layered: false)
        }
        .frame(width: side, height: side)
        let renderer = ImageRenderer(content: content)
        renderer.scale = scale
        guard let cg = renderer.cgImage else { return nil }
        return Image(decorative: cg, scale: scale)
    }

    private func drawShards(_ c: inout GraphicsContext, _ s: CGFloat) {
        let edge = rc.mix(.white, 0.5)
        let edgeStyle = StrokeStyle(lineWidth: 0.8, lineJoin: .round)
        let photo = shardImage.map { c.resolve($0) }
        for sh in shards {
            let u = max(0, s - sh.delay)
            let dr = (1 - exp(-2.2 * u)) / 2.2
            let fadeAt = sh.life * 0.55
            let a: CGFloat = u < fadeAt ? 1 : c01(1 - (u - fadeAt) / (sh.life * 0.45))
            if a <= 0 { continue }
            let x = sh.x0 + sh.vx * dr
            let y = sh.y0 + sh.vy * dr + 0.5 * grav * u * u
            let ph = sh.vt * u
            let cs = cos(ph)
            let sx: CGFloat = (cs < 0 ? -1 : 1) * max(0.06, abs(cs))
            var g = c
            g.translateBy(x: x, y: y)
            g.rotate(by: .radians(Double(sh.vr * u)))
            g.scaleBy(x: sx, y: 1)
            if let photo {
                var l = g
                l.clip(to: sh.path)
                l.opacity = Double(a)
                l.draw(photo, at: CGPoint(x: shardOrigin.x - sh.x0, y: shardOrigin.y - sh.y0), anchor: .topLeading)
            } else {
                g.fill(sh.path, with: .color(pal.rim.color(0.22 * a)))
            }
            let gl = pow(abs(sin(ph * 0.5 + sh.gp)), 14)
            g.blendMode = .plusLighter
            if gl > 0.03 { g.fill(sh.path, with: .color(OrbRGB.white.color(gl * 0.55 * a))) }
            g.fill(sh.path, with: .color(rc.color(0.14 * a * (1 - c01(u / 0.6)))))
            g.stroke(sh.path, with: .color(edge.color((0.35 + gl * 0.6) * a)), style: edgeStyle)
        }
        var d = c
        d.blendMode = .plusLighter
        let dr = (1 - exp(-2.6 * s)) / 2.6
        for p in dust {
            let a = c01(1 - s / p.life)
            if a <= 0 { continue }
            let x = p.x0 + p.vx * dr
            let y = p.y0 + p.vy * dr + 0.5 * grav * s * s
            let tw = 0.5 + 0.5 * sin(s * 30 + p.ph)
            d.fill(Path(CGRect(x: x - p.s / 2, y: y - p.s / 2, width: p.s, height: p.s)), with: .color(edge.color(a * (0.4 + 0.6 * tw))))
        }
    }

    // MARK: 등급 빛

    private func wedge(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ ang: CGFloat, _ len: CGFloat, _ w: CGFloat,
                       _ c0: Color, _ c1: Color) {
        var p = Path()
        p.move(to: CGPoint(x: cx, y: cy))
        p.addLine(to: CGPoint(x: cx + cos(ang - w) * len, y: cy + sin(ang - w) * len))
        p.addLine(to: CGPoint(x: cx + cos(ang + w) * len, y: cy + sin(ang + w) * len))
        p.closeSubpath()
        let end = CGPoint(x: cx + cos(ang) * len, y: cy + sin(ang) * len)
        let prev = c.blendMode
        c.blendMode = .plusLighter
        c.fill(p, with: .linearGradient(Gradient(colors: [c0, c1]), startPoint: CGPoint(x: cx, y: cy), endPoint: end))
        c.blendMode = prev
    }

    /// 깨지는 순간 — 번쩍임(영웅·전설) · 번짐 · 가로 섬광(희귀↑) · 빛줄기 0/8/14/22 · 충격파 등급 수만큼 · 반짝이(전설은 금가루가 쏟아진다).
    private func rarityBurst(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ s: CGFloat, _ view: CGRect) {
        let f = c01(s / shatDur)
        let e = outC(f)
        let a = 1 - f
        let l = CGFloat(lv)
        if lv >= 2 && flashOk {
            let q = c01(1 - s / 0.45)
            let peak: CGFloat = lv == 3 ? 0.55 : 0.28
            let fa = peak * q * q
            if fa > 0 {
                var fl = c
                fl.blendMode = .plusLighter
                let col = lv == 3 ? rc.mix(.white, 0.4) : rc
                fl.fill(Path(view), with: .color(col.color(fa)))
            }
        }
        glow(&c, rc, cx, cy, r * (0.7 + 0.7 * l) * (0.45 + 0.55 * e), (0.55 + 0.12 * l) * a, add: true)
        glow(&c, .white, cx, cy, r * (0.35 + 0.25 * l) * (0.3 + 0.7 * e), pow(a, 1.5) * 0.9, add: true)
        if lv >= 1 {
            var flat = c
            flat.translateBy(x: cx, y: cy)
            flat.scaleBy(x: 1, y: 0.05 + 0.02 * l)
            let fr = r * (1.4 + 1.1 * l) * (0.5 + 0.5 * e)
            glow(&flat, rc.mix(.white, 0.3), 0, 0, fr, a * (0.3 + 0.15 * l), add: true)
        }
        let nr = Self.rayCounts[min(max(lv, 0), 3)]
        let light = rc.mix(.white, 0.5)
        let lightC = light.color(0.75 * a)
        let clear = rc.color(0)
        for i in 0..<nr {
            let ang = CGFloat(i) / CGFloat(nr) * orbTau + t * 0.25 + CGFloat(i % 2) * 0.13
            let reach: CGFloat = i % 3 == 0 ? 1 : 0.62
            let len = r * (0.9 + 0.55 * l) * (0.35 + 0.65 * e) * reach
            wedge(&c, cx, cy, ang, len, 0.035 + 0.015 * CGFloat(i % 2), lightC, clear)
        }
        let ring = rc.mix(.white, 0.3)
        if lv > 0 {
            var shock = c
            shock.blendMode = .plusLighter
            for kk in 0..<lv {
                let u = c01((s - CGFloat(kk) * 0.18) / 0.9)
                if u <= 0 || u >= 1 { continue }
                let rr = r * (0.4 + 2.4 * outC(u))
                let rect = CGRect(x: cx - rr, y: cy - rr * 0.92, width: rr * 2, height: rr * 1.84)
                shock.stroke(Path(ellipseIn: rect), with: .color(ring.color((1 - u) * 0.8)), lineWidth: (3.5 - CGFloat(kk)) * (1 - u) + 0.5)
            }
        }
        let goldDust = OrbRGB(255, 214, 130)
        let fall: CGFloat = lv == 3 ? 1.2 : 0.4
        for p in sparks where p.lv <= lv {
            let d = r * (0.25 + p.v * (1.4 + 0.5 * l) * e)
            let x = cx + cos(p.a) * d
            let y = cy + sin(p.a) * d * 0.9 + p.g * r * fall * f * f
            let tw = 0.6 + 0.4 * sin(t * 14 + p.ph)
            let col: OrbRGB = lv == 3 ? (p.gold ? goldDust : .white) : ring
            glow(&c, col, x, y, p.s * 2.6, a * 0.5 * tw, add: true)
            sparkle(&c, x, y, p.s * (1 - 0.4 * f), a * tw, col, add: true)
        }
    }

    /// 열린 뒤 남는 후광 — 번짐(모든 등급) · 도는 빛줄기(영웅 10 / 전설 16) · 전설은 고리 + 내려앉는 금가루. k 0..1.
    private func aura(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ k: CGFloat) {
        guard k > 0 else { return }
        let l = CGFloat(lv)
        glow(&c, rc, cx, cy, r * (0.7 + 0.35 * l), (0.25 + 0.1 * l) * k * (0.85 + 0.15 * sin(t * 2)), add: true)
        if lv >= 2 {
            let n = lv == 3 ? 16 : 10
            let amp: CGFloat = (lv == 3 ? 0.22 : 0.12) * k
            let from = rc.color(amp)
            let clear = rc.color(0)
            for i in 0..<n {
                let ang = CGFloat(i) / CGFloat(n) * orbTau + t * 0.15
                let reach: CGFloat = i % 2 == 1 ? 0.7 : 1
                wedge(&c, cx, cy, ang, r * (0.8 + 0.4 * l) * reach, 0.05, from, clear)
            }
        }
        if lv == 3 {
            var ringC = c
            ringC.blendMode = .plusLighter
            ringC.stroke(circlePath(cx, cy, r * 0.62), with: .color(rc.color(0.35 * k)), lineWidth: 1)
            let gold = OrbRGB(hex: 0xFFE9B0)
            for p in glit {
                let ang = p.a + t * p.w
                let ph = (p.y + t * p.v).truncatingRemainder(dividingBy: 1)
                let yy = cy - r * 0.9 + ph * r * 2
                let x = cx + cos(ang) * p.r * r
                let tw = 0.4 + 0.6 * abs(sin(t * 3 + p.ph))
                sparkle(&c, x, yy, p.s, k * tw * sin(ph * CGFloat.pi), gold, add: true)
            }
        }
    }

    /// 결과 문장 — 등급 빛 별(왼쪽 위에서 빛을 받는 입체 그라데이션)이 통 튀어나와 둥실, 둘레를 반짝이 다섯이 돈다.
    private func emblem(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ s: CGFloat, _ col: OrbRGB, _ alpha: CGFloat) {
        guard alpha > 0, s > 0.5 else { return }
        glow(&c, col, x, y, s * 3.4, 0.6 * alpha, add: true)
        glow(&c, .white, x, y, s * 1.2, 0.3 * alpha, add: true)
        let yy = y + sin(t * 1.6) * s * 0.06
        let rot = sin(t * 0.8) * 0.12
        var star = Path()
        for i in 0..<10 {
            let rr = i % 2 == 1 ? s * 0.47 : s
            let a = rot - CGFloat.pi / 2 + CGFloat(i) * CGFloat.pi / 5
            let pnt = CGPoint(x: x + cos(a) * rr, y: yy + sin(a) * rr)
            if i == 0 { star.move(to: pnt) } else { star.addLine(to: pnt) }
        }
        star.closeSubpath()
        let stops: [Gradient.Stop] = [
            .init(color: OrbRGB.white.color(1), location: 0),
            .init(color: col.mix(.white, 0.35).color(1), location: 0.45),
            .init(color: col.mix(OrbRGB(20, 10, 40), 0.3).color(1), location: 1),
        ]
        let shading = GraphicsContext.Shading.radialGradient(
            Gradient(stops: stops), center: CGPoint(x: x - s * 0.18, y: yy - s * 0.25), startRadius: 0, endRadius: s * 1.25)
        var g = c
        g.opacity = Double(c01(alpha))
        g.fill(star, with: shading)
        g.stroke(star, with: shading, style: StrokeStyle(lineWidth: s * 0.18, lineJoin: .round))
        for i in 0..<5 {
            let fi = CGFloat(i)
            let a = t * 0.6 + fi * orbTau / 5
            let rr = s * (1.9 + 0.25 * sin(t * 1.3 + fi))
            let size = s * (0.17 + 0.07 * sin(t * 3 + fi * 2))
            let sa = alpha * (0.55 + 0.45 * sin(t * 2.4 + fi))
            sparkle(&c, x + cos(a) * rr, yy + sin(a) * rr * 0.8, size, sa, .white, add: true)
        }
    }
}

/// CADisplayLink 대상 — 엔진을 약하게 잡아(엔진이 사라지면 스스로 멈춘다) 순환 참조를 끊는다.
@MainActor
private final class OrbLinkProxy: NSObject {
    private weak var owner: OrbEngine?

    init(_ owner: OrbEngine) {
        self.owner = owner
    }

    @objc func tick(_ l: CADisplayLink) {
        guard let owner else {
            l.invalidate()
            return
        }
        owner.frame(l)
    }
}

// MARK: - 그리기 뷰 · 하늘 별

/// 엔진 프레임마다 다시 그리는 캔버스 — GPU 로 합성(drawingGroup)해 매 프레임 그라데이션이 많아도 가볍게.
struct OrbCanvas: View {
    @ObservedObject var frames: OrbFrames
    let draw: (inout GraphicsContext, CGSize) -> Void

    var body: some View {
        let tick = frames.tick
        Canvas { c, size in
            _ = tick
            draw(&c, size)
        }
        .drawingGroup()
    }
}

/// 하늘 별(무대·연출 바탕) — 0..1 위치 · 크기 · 깜빡임 위상 · 속도. Android `SkyStars` 와 같은 범위.
struct SkyStars {
    let x: [CGFloat]
    let y: [CGFloat]
    let s: [CGFloat]
    let p: [CGFloat]
    let w: [CGFloat]

    var count: Int { x.count }

    init(seed: UInt64, n: Int = 70) {
        let r = OrbRandom(seed)
        x = (0..<n).map { _ in r.f() }
        y = (0..<n).map { _ in r.f() }
        s = (0..<n).map { _ in 0.5 + r.f() * 1.1 }
        p = (0..<n).map { _ in r.f() * orbTau }
        w = (0..<n).map { _ in 0.6 + r.f() * 1.6 }
    }
}
