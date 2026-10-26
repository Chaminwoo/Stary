import QuartzCore
import SwiftUI
import UIKit

/*
 * 수정구 — 잠든 우주(2026-10-27). 별가루 화면 무대와 깨우기 창이 함께 쓰는 그림. 시안 engine.js 를 옮겼다(Android `CrystalOrb.kt` 와 같은 수치).
 *
 *  - 무대 단위 340 × 440(그리는 쪽이 pt/단위 k 를 곱한다). 세계 좌표(X 오른쪽, Y 위, Z 보는 사람 쪽)를 카메라(고도 0.43rad, 거리 820)로 비춰 원근을 준다.
 *  - 아래: 불규칙한 별로 이룬 마법진(높이가 다른 여러 층, 원근으로 앞이 크다) — 바닥 빛, 별가루 고리 + 사슬로 이은 밝은 별, 별자리 띠, 칠각별,
 *    떠 있는 둘째·셋째 층(빛 실), 고리에서 솟는 빛 커튼, 피어오르는 반딧불.
 *  - 그 위로 피어오르는 은하수 꽃잔(두 팔 나선 별가루 · 성운 띠 · 먼지 띠 · 가운데 빛 기둥)이 둥둥 뜬 수정구를 감싸고, 둘레를 3D 궤도로 티끌이 돈다.
 *  - 수정구 안: 3D 별밭 · 나선 은하 · 성운/먼지 · 가끔 별똥별 — 앞으로 기울어진 세로축을 따라 돈다. 누른 쪽으로 돌고(앞은 누른 쪽, 뒤는 반대),
 *    같은 쪽을 거듭 누르면 빨라지고, 빨리 돌 때 반대쪽을 누르면 먼저 느려진다(관성). 빨리 돌면 별이 잔상으로 늘어진다.
 *  - 문지르기(1~2초): 둘레 고리가 차오르고 속이 휘저어지다가 다 차면 경로가 바뀐다(옅은 잠 ↔ 깊은 잠, 색이 0.9초 동안 섞인다).
 *  - 깨기(깨우기 창): 두드릴 때마다 유리 금(곧은 방사 금 + 다각형 거미줄 금 + 부서진 점 + 튀는 유리 조각), 세 번째 뒤 결과 등급 빛이
 *    금 사이로 새어 나오다가 수많은 조각으로 깨지며 속의 우주가 터져 나온다(조각마다 깨지기 직전 구슬 그림을 그대로 입힌다).
 *  - 화면 번쩍임(영웅·전설)은 "동작 줄이기"면 생략하고 움직임도 느리게(`OrbEngine.motion`).
 *
 * 그리기는 SwiftUI Canvas(GraphicsContext) — 시안의 'lighter'(더하기) 합성은 `.plusLighter`. 그리는 함수마다 합성 방식을 직접 정한다.
 * 프레임은 CADisplayLink 가 `step` 하고 `OrbFrames.tick` 을 올려 `OrbCanvas` 만 다시 그린다(화면 body 는 다시 계산하지 않는다).
 * 조각에 입힐 구슬 사진은 그리는 도중이 아니라 프레임 콜백에서 `ImageRenderer` 로 한 번 찍는다.
 */

// MARK: - 무대 치수(무대 단위)

let orbW: CGFloat = 340
let orbH: CGFloat = 440
/// 수정구 반지름(무대 단위).
let orbR: CGFloat = 84
/// 수정구 가운데(무대 단위) — 세계 (0, orbY0, 0) 을 비춘 자리.
let orbCX: CGFloat = 170
let orbCY: CGFloat = stageGY - orbY0 * camCE * orbS
/// 깨진 뒤 이만큼(초) 지나면 카드가 솟는다 — 조각이 거의 다 떨어졌을 때.
let orbCardsAt: CGFloat = 1.55

private let stageCX: CGFloat = 170
private let stageGY: CGFloat = 350
private let camD: CGFloat = 820
private let camSE: CGFloat = sin(0.43)
private let camCE: CGFloat = cos(0.43)
/// 마법진 반지름(세계).
private let circR: CGFloat = 118
/// 수정구 높이(세계).
private let orbY0: CGFloat = 172
/// 은하수 꽃잔 가장자리 높이(세계) — 수정구 아랫도리를 감싼다.
private let cupTop: CGFloat = orbY0 * 0.9
/// 수정구 속 회전축 기울기(위가 조금 보이게).
private let innerTilt: CGFloat = 0.5
private let orbS: CGFloat = camD / (camD - orbY0 * camSE)
/// 수정구 반지름(세계).
private let orbRW: CGFloat = orbR / orbS

private let orbTau = CGFloat.pi * 2
private let spinIdle: CGFloat = 0.22
private let spinKick: CGFloat = 2.3
private let spinMax: CGFloat = 14
private let spinDecay: CGFloat = 0.55
private let rubLen: CGFloat = 340
private let rubMinT: CGFloat = 1
private let convDur: CGFloat = 1
private let shatDur: CGFloat = 1.9
private let grav: CGFloat = 270

/// 등급별 깨짐 빛줄기 수.
private let raysByLevel = [0, 8, 14, 22]
/// 금 마디 밝기 셋(어둑 · 보통 · 번쩍).
private let crackLum: [CGFloat] = [0.3, 0.62, 1]
/// 은하판을 회전축에 비스듬히 눕히는 두 각.
private let gaC: CGFloat = cos(0.42)
private let gaS: CGFloat = sin(0.42)
private let gbC: CGFloat = cos(0.3)
private let gbS: CGFloat = sin(0.3)

private func c01(_ x: CGFloat) -> CGFloat { x < 0 ? 0 : (x > 1 ? 1 : x) }

private func smooth(_ a: CGFloat, _ b: CGFloat, _ x: CGFloat) -> CGFloat {
    let t = c01((x - a) / (b - a))
    return t * t * (3 - 2 * t)
}

private func inC(_ t: CGFloat) -> CGFloat { t * t * t }

private func outC(_ t: CGFloat) -> CGFloat {
    let u = 1 - t
    return 1 - u * u * u
}

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

private func hyp(_ x: CGFloat, _ y: CGFloat) -> CGFloat { (x * x + y * y).squareRoot() }

private func circlePath(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// 타원 호(Android drawArc 와 같은 매개 각도) — a0 → a1(라디안, 화면에서 시계 방향으로 커진다).
private func arcPath(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, _ a0: CGFloat, _ a1: CGFloat) -> Path {
    var p = Path()
    p.addArc(center: .zero, radius: 1, startAngle: .radians(Double(a0)), endAngle: .radians(Double(a1)), clockwise: false)
    return p.applying(CGAffineTransform(a: rx, b: 0, c: 0, d: ry, tx: cx, ty: cy))
}

// MARK: - 원근

/// 세계 점을 비춘 무대 자리(x, y)와 원근 배율 s.
private struct OrbP {
    let x, y, s: CGFloat
}

private func proj(_ x: CGFloat, _ y: CGFloat, _ z: CGFloat) -> OrbP {
    let yc = y * camCE - z * camSE
    let zc = y * camSE + z * camCE
    let s = camD / (camD - zc)
    return OrbP(x: stageCX + x * s, y: stageGY - yc * s, s: s)
}

/// 앞(보는 사람 쪽)일수록 밝게.
private func dep(_ z: CGFloat) -> CGFloat { 0.42 + 0.58 * c01((z / circR + 1) / 2) }

/// 땅 위 원 반쪽(뒤 = π..2π, 앞 = 0..π)을 원근으로 비춘 길.
private func ringPath(_ rr: CGFloat, _ y: CGFloat, front: Bool) -> Path {
    var out = Path()
    for i in 0...40 {
        let a = (front ? 0 : CGFloat.pi) + CGFloat(i) / 40 * .pi
        let q = proj(cos(a) * rr, y, sin(a) * rr)
        if i == 0 {
            out.move(to: CGPoint(x: q.x, y: q.y))
        } else {
            out.addLine(to: CGPoint(x: q.x, y: q.y))
        }
    }
    return out
}

// MARK: - 색

/// 0..1 sRGB 색 — 섞기·알파 덮어쓰기를 숫자로 하고 그릴 때만 Color/Shading 으로 바꾼다(Android Int 색과 같은 쓰임).
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

    /// 알파를 덮어쓴 단색 칠(Color 를 만들지 않아 가볍다).
    func shade(_ alpha: CGFloat) -> GraphicsContext.Shading {
        .color(.sRGB, red: Double(r), green: Double(g), blue: Double(b), opacity: Double(c01(alpha)))
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

/// 옅은 잠 = 맑은 유리 · 푸른 은 / 깊은 잠 = 연기 유리 · 금. 모든 색이 둘 사이를 곧게 섞이므로 섞인 정도(0..1) 하나로 나타낸다.
private struct OrbPalette {
    var fog: [OrbRGB]
    var ink: OrbRGB
    var body: [OrbRGB]
    var rim: OrbRGB
    var caustic: OrbRGB
    var glow: OrbRGB
    var star: OrbRGB
    var line: OrbRGB
    var cstar: OrbRGB
    var band: [OrbRGB]
    var gal: [OrbRGB]

    static let normal = OrbPalette(
        fog: [OrbRGB(hex: 0x4F7DFF), OrbRGB(hex: 0x7FB0FF), OrbRGB(hex: 0x9B7BFF), OrbRGB(hex: 0x5FD3FF), OrbRGB(hex: 0xD08BFF)],
        ink: OrbRGB(hex: 0x030512),
        body: [OrbRGB(44, 66, 140, 0.22), OrbRGB(14, 20, 52, 0.6), OrbRGB(5, 8, 24, 0.93)],
        rim: OrbRGB(hex: 0xC8DCFF), caustic: OrbRGB(hex: 0x96C3FF), glow: OrbRGB(hex: 0x5A8CFF), star: OrbRGB(hex: 0xDDE8FF),
        line: OrbRGB(hex: 0x9FC3FF), cstar: OrbRGB(hex: 0xE8F0FF),
        band: [OrbRGB(hex: 0xFFF0DA), OrbRGB(hex: 0xB9CCFF), OrbRGB(hex: 0x9A86FF), OrbRGB(hex: 0x6A8DFF)],
        gal: [OrbRGB(hex: 0xFFF1D6), OrbRGB(hex: 0xBFD4FF), OrbRGB(hex: 0x7C8CFF)]
    )

    static let rare = OrbPalette(
        fog: [OrbRGB(hex: 0xFFB347), OrbRGB(hex: 0xFFD27A), OrbRGB(hex: 0xFF8A5C), OrbRGB(hex: 0xFFE6A8), OrbRGB(hex: 0xFF7A9C)],
        ink: OrbRGB(hex: 0x0A0503),
        body: [OrbRGB(90, 52, 30, 0.26), OrbRGB(26, 14, 14, 0.66), OrbRGB(8, 5, 7, 0.94)],
        rim: OrbRGB(hex: 0xFFDEA0), caustic: OrbRGB(hex: 0xFFC970), glow: OrbRGB(hex: 0xFF9F43), star: OrbRGB(hex: 0xFFE9B0),
        line: OrbRGB(hex: 0xFFD27A), cstar: OrbRGB(hex: 0xFFF4D8),
        band: [OrbRGB(hex: 0xFFFFFF), OrbRGB(hex: 0xFFDDA6), OrbRGB(hex: 0xFF9FB4), OrbRGB(hex: 0xFFB566)],
        gal: [OrbRGB(hex: 0xFFFFFF), OrbRGB(hex: 0xFFD9A0), OrbRGB(hex: 0xFF8F70)]
    )

    static func lerp(_ a: OrbPalette, _ b: OrbPalette, _ t: CGFloat) -> OrbPalette {
        func m(_ x: [OrbRGB], _ y: [OrbRGB]) -> [OrbRGB] { zip(x, y).map { $0.mix($1, t) } }
        return OrbPalette(
            fog: m(a.fog, b.fog), ink: a.ink.mix(b.ink, t), body: m(a.body, b.body),
            rim: a.rim.mix(b.rim, t), caustic: a.caustic.mix(b.caustic, t), glow: a.glow.mix(b.glow, t), star: a.star.mix(b.star, t),
            line: a.line.mix(b.line, t), cstar: a.cstar.mix(b.cstar, t), band: m(a.band, b.band), gal: m(a.gal, b.gal)
        )
    }
}

// MARK: - 한 번 굽는 것

/// 유리 몸통 — 시안의 두 점 원뿔 그라데이션(안쪽 원 (-.25, -.3) r .05 → 바깥 원 (0, 0) r 1, 정지점 0 · .72 · 1)을 직접 굽는다.
/// 색은 미리 곱한 알파로 섞는다(Android `OrbBody` 와 같은 식). 경로가 섞이는 동안은 두 장을 더해 겹친다.
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

/// 은하 별 하나가 팔에서 벗어난 정도(가운데로 몰리고 가끔 멀리).
private func galScatter(_ r: OrbRandom, _ k: CGFloat, _ rr: CGFloat) -> CGFloat {
    let mag = pow(r.f(), 2.2)
    let side: CGFloat = r.f() < 0.5 ? -1 : 1
    return mag * side * k * (0.3 + rr)
}

// MARK: - 그림 재료(구슬 속 · 마법진 · 은하수 · 깨짐)

private struct OrbStar {
    let x, y, z, s: CGFloat
    let big: Bool
    let f, ph: CGFloat
}

private struct OrbGalPt {
    let rr, a, ox, oy, oz, w, ph, s: CGFloat
}

private struct OrbNeb {
    let x, y, z, s: CGFloat
    let c: Int
    let dark: Bool
    let ph, al, el, rot: CGFloat
}

private struct OrbFar {
    let x, y, z, rot, s: CGFloat
}

private struct OrbRingDust {
    let a, rr, y, s, ph, f: CGFloat
}

private struct OrbRingStar {
    let a, rr, s: CGFloat
    let big: Bool
    let f, ph, y: CGFloat
    let link: Bool
}

private struct OrbCPt {
    let a, rr, s, ph: CGFloat
}

private struct OrbHDust {
    let e: Int
    let u, off, s, ph: CGFloat
}

private struct OrbRiser {
    let a, d, p0, life, h, s, ph, sw: CGFloat
}

private struct OrbFlow {
    let p0, life: CGFloat
    let arm: Int
    let off, rj, s: CGFloat
    let ci: Int
    let ph, f: CGFloat
    let bright, core: Bool
}

private struct OrbBand {
    let p0, life: CGFloat
    let arm: Int
    let off, rj, s: CGFloat
    let ci: Int
    let al: CGFloat
    let dark: Bool
}

private struct OrbMote {
    let a0, w, rr, s, ph, f: CGFloat
    /// 궤도 기울기 · 방향(sin/cos 를 미리).
    let si, ci, sy, cy: CGFloat
}

private struct OrbRubSpark {
    let x, y, vx, vy, born, life, s: CGFloat
}

private struct OrbWave {
    let born, dur: CGFloat
    let c: OrbRGB
}

private struct OrbShoot {
    let x, y, dir, born: CGFloat
}

private struct OrbRay {
    let xs: [CGFloat]
    let ys: [CGFloat]
    let cum: [CGFloat]
    /// 마디마다 빛을 받는 정도(0 어둑 · 1 보통 · 2 번쩍).
    let lum: [Int]
    let start: CGFloat
    let len: CGFloat
    var n: Int { xs.count }
}

private struct OrbRingSeg {
    let ax, ay, mx, my, bx, by, d: CGFloat
}

private struct OrbGlint {
    let ray: Int
    let v, ph: CGFloat
}

private final class OrbImpact {
    let x: CGFloat
    let y: CGFloat
    let rays: [OrbRay]
    let rings: [OrbRingSeg]
    /// 잔금 — [x0, y0, x1, y1] 이 이어진다.
    let micro: [CGFloat]
    /// 부서진 점 — 일곱 꼭짓점 [x, y] 가 이어진다.
    let crush: [CGFloat]
    let glints: [OrbGlint]
    var target: CGFloat
    var g: CGFloat = 0

    init(x: CGFloat, y: CGFloat, rays: [OrbRay], rings: [OrbRingSeg], micro: [CGFloat], crush: [CGFloat], glints: [OrbGlint], target: CGFloat) {
        self.x = x
        self.y = y
        self.rays = rays
        self.rings = rings
        self.micro = micro
        self.crush = crush
        self.glints = glints
        self.target = target
    }
}

private struct OrbFlake {
    let x, y, vx, vy, vz, born, life, rot, vr: CGFloat
    let tri: [CGFloat]
}

private struct OrbShard {
    let path: Path
    let x0, y0, vx, vy, vz, vr, vt, vq, gp, life, delay: CGFloat
}

private struct OrbGrit {
    let x0, y0, vx, vy, s, life, ph: CGFloat
}

private struct OrbEscape {
    let a, r0, v, w, life: CGFloat
    let c: OrbRGB
}

private struct OrbPuff {
    let a, d: CGFloat
    let c: OrbRGB
    let s0: CGFloat
}

private struct OrbLeak {
    let a, w, l: CGFloat
}

/// 속 우주의 한 점을 비춘 결과 — 구슬 화면 자리(x, y) · 깊이 z · 돌기 속도용 (u, v).
private struct OrbQ {
    var x, y, z, u, v: CGFloat
    static let zero = OrbQ(x: 0, y: 0, z: 0, u: 0, v: 0)
}

/// 이번 프레임의 속 우주 자세(돌기 · 기울기 · 흔들림 · 모일 때 줄어듦 · 문지를 때 휘저음).
private struct OrbInnerXf {
    var ca: CGFloat = 1
    var sa: CGFloat = 0
    var ct: CGFloat = 1
    var st: CGFloat = 0
    var cr: CGFloat = 1
    var sr: CGFloat = 0
    var shrink: CGFloat = 1
    var sw: CGFloat = 0
    var swT: CGFloat = 0
    var cx: CGFloat = 0
    var cy: CGFloat = 0
    var r: CGFloat = orbR

    /// 렌즈처럼 가운데가 조금 크고, 문지르면 휘저어진다.
    func pr(_ x: CGFloat, _ y: CGFloat, _ z: CGFloat) -> OrbQ {
        let x1 = x * ca + z * sa
        let z1 = -x * sa + z * ca
        let y2 = y * ct - z1 * st
        let z2 = y * st + z1 * ct
        let rho2 = x1 * x1 + y2 * y2
        let l = (1.14 - 0.17 * rho2) * shrink
        var sx = (x1 * cr + y2 * sr) * l
        var sy = (x1 * sr - y2 * cr) * l
        if sw > 0.001 {
            let rho = rho2.squareRoot()
            let an = sw * (1 - min(1, rho)) * sin(swT + rho * 5)
            let cc = cos(an)
            let ss = sin(an)
            let nx = sx * cc - sy * ss
            sy = sx * ss + sy * cc
            sx = nx
        }
        return OrbQ(x: cx + sx * r, y: cy + sy * r, z: z2, u: x1 * l, v: z1 * l)
    }
}

/// 은하수 별가루 한 알의 이번 프레임 자리 · 크기 · 빛 · 색.
private struct OrbFlowP {
    var x: CGFloat = 0
    var y: CGFloat = 0
    var s: CGFloat = 0
    var a: CGFloat = 0
    var c = OrbRGB.white
    /// 0 = 그리지 않음(수정구 속) · 1 = 뒤 · 2 = 앞.
    var side: UInt8 = 0
}

/// 빛 커튼 기둥 하나(바닥 · 꼭대기 자리, 물결 높이, 앞뒤).
private struct OrbCurtainP {
    var b0x: CGFloat = 0
    var b0y: CGFloat = 0
    var b1x: CGFloat = 0
    var b1y: CGFloat = 0
    var n: CGFloat = 0
    var z: CGFloat = 0
}

// MARK: - 엔진

/// 프레임 번호 — 이것만 구독하는 `OrbCanvas` 가 매 프레임 다시 그린다(엔진 상태 변화로 화면 전체가 다시 계산되지 않게).
final class OrbFrames: ObservableObject {
    @Published var tick = 0
}

/// 수정구 한 개의 상태와 그림. 프레임마다 `step` → `draw`.
/// `interactive` = false(별가루 화면 무대)면 두드려도 금이 가지 않고 누른 쪽으로 돈다. 문지르기(`rubStart`/`rubMove`/`rubEnd`)는 무대에서만.
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

    private let interactive: Bool
    private let rnd: OrbRandom

    // ── 돌리기 ──
    private var omega = spinIdle
    private var idleDir: CGFloat = 1
    private var spinA: CGFloat = 0
    private var pulse: CGFloat = 0
    private var flowA: CGFloat = 0
    private var ringA: CGFloat = 0
    private var tiltNow = innerTilt

    // ── 문지르기 ──
    /// 문지른 정도 0..1 — 다 차면 경로가 바뀐다.
    private(set) var rubP: CGFloat = 0
    private var rubbing = false
    private var rubLock = false
    private var lastRubT: CGFloat = -9
    private var flash: CGFloat = 0
    private var rubSparks: [OrbRubSpark] = []
    private var waves: [OrbWave] = []
    private let ringR = orbR + 11

    // ── 색 ──
    /// 지금 경로(깊은 잠 = true). 문지르기로 바뀌면 화면이 이 값을 따라간다.
    private(set) var rare = false
    private var fromM: CGFloat = 0
    private var toM: CGFloat = 0
    private var mixT: CGFloat = 1
    /// 지금 그리는 색이 깊은 잠 쪽으로 섞인 정도(0 옅은 잠 … 1 깊은 잠) — 문지르는 동안 반대 경로로 반쯤 섞이는 것까지.
    private var palM: CGFloat = -1
    private var pal = OrbPalette.normal
    private var palVer = 0

    /// 지금 경로 빛(바탕 성운이 따라 쓴다 — 색이 섞이는 동안 · 문지르는 동안에도).
    var glowColor: Color { pal.glow.color(1) }

    // ── 깨기 ──
    private var pt: CGFloat = 0
    private var conv: CGFloat = 0
    private var lv = 0
    private var rc = OrbRGB.white
    private var pending: String?
    private var impacts: [OrbImpact] = []
    private var flakes: [OrbFlake] = []
    private var shards: [OrbShard] = []
    private var grit: [OrbGrit] = []
    private var escape: [OrbEscape] = []
    private var puffs: [OrbPuff] = []
    private var leaks: [OrbLeak] = []
    private var needShards = false
    private var shake: CGFloat = 0
    private var flareAt: CGFloat = -9
    private var shardImage: Image?
    private var shardOrigin = CGPoint.zero
    private var lastK: CGFloat = 1

    /// 지금 그려진 구슬 가운데(무대 단위) — 누른 자리가 구슬 위인지 볼 때.
    private(set) var bx = orbCX
    private(set) var by = orbCY
    private var orbYW = orbY0
    private var viewTop: CGFloat = 0
    /// 무대 전체 투명도(카드가 뜰 때 무대를 지운다) — 빛·반짝이·점이 곱한다.
    private var fade: CGFloat = 1

    // ── 수정구 속 우주 ──
    private let stars: [OrbStar]
    /// 나선 은하 — 두 팔 + 흩어진 원반. 은하판은 회전축에 비스듬해서 돌 때마다 모양이 달라 보인다.
    private let gal: [OrbGalPt]
    private let neb: [OrbNeb]
    private let far: [OrbFar]
    private var shoot: OrbShoot?
    private var shootAt: CGFloat = 0

    // ── 마법진 ──
    /// 바깥 고리 — 밀도가 들쭉날쭉한 별가루 수백 개(뭉친 데 · 성긴 데).
    private let ringDust: [OrbRingDust]
    /// 크기가 제각각인 밝은 별(이웃끼리 짧은 별자리 사슬) — 각도 순.
    private let ring1: [OrbRingStar]
    private let constel: [[OrbCPt]]
    private let heptaDust: [OrbHDust]
    /// 떠 있는 둘째 층(높이 14) · 셋째 층(높이 30).
    private let ring2: [OrbCPt]
    private let ring3: [OrbCPt]
    /// 마법진에서 피어올라 사라지는 반딧불.
    private let risers: [OrbRiser]

    // ── 은하수 꽃잔 — 두 팔 나선으로 감겨 오르며 활짝 벌어지는 별가루(팔 가운데일수록 촘촘하고 밝다) ──
    private let flow: [OrbFlow]
    private let band: [OrbBand]
    private let motes: [OrbMote]

    // ── 프레임마다 다시 쓰는 계산 칸 ──
    private var xf = OrbInnerXf()
    private var innerCv: CGFloat = 0
    private var innerGo = false
    private var sweep: CGFloat = 0
    private var starQ = [OrbQ](repeating: .zero, count: 240)
    private var flowP = [OrbFlowP](repeating: OrbFlowP(), count: 760)
    private var flFl: CGFloat = 1
    private var curtain = [OrbCurtainP](repeating: OrbCurtainP(), count: 73)
    private var galCol = [OrbRGB](repeating: .white, count: 420)
    private var galColVer = -1

    private var link: CADisplayLink?
    private var lastTs: CFTimeInterval = 0

    /// 땅 위 안내선 셋(반지름 · 높이)의 뒤/앞 반쪽 — 움직이지 않으니 한 번 비춘다.
    private static let staticRings: [Path] = {
        let defs: [CGFloat] = [circR * 1.05, 0, circR * 0.5, 14, circR * 0.26, 30]
        return (0..<6).map { k in ringPath(defs[(k / 2) * 2], defs[(k / 2) * 2 + 1], front: k % 2 == 1) }
    }()

    /// 창 반사 — 왼쪽 위 가장자리를 따라 휜 초승달(가운데가 두껍고 끝이 가늘다), 구슬 가운데 기준.
    private static let crescent: Path = {
        let rr = orbR
        var p = Path()
        p.addArc(center: .zero, radius: rr * 0.935, startAngle: .radians(1.05 * Double.pi), endAngle: .radians(1.47 * Double.pi), clockwise: false)
        p.addArc(center: CGPoint(x: rr * 0.236, y: rr * 0.236), radius: rr * 1.178,
                 startAngle: .radians(1.414 * Double.pi), endAngle: .radians(1.101 * Double.pi), clockwise: true)
        p.closeSubpath()
        return p
    }()

    init(seed: UInt64, interactive: Bool, rare: Bool) {
        self.interactive = interactive
        let r = OrbRandom(seed)
        rnd = r
        stars = (0..<240).map { _ -> OrbStar in
            let d = 0.97 * pow(r.f(), 1.0 / 3.0)
            let u = r.f() * 2 - 1
            let th = r.f() * orbTau
            let q = (1 - u * u).squareRoot()
            let big = r.f() < 0.06
            let s = big ? 1.5 + r.f() * 1.1 : 0.3 + pow(r.f(), 3) * 1.1
            let f = 0.8 + r.f() * 2.6
            return OrbStar(x: q * cos(th) * d, y: u * d, z: q * sin(th) * d, s: s, big: big, f: f, ph: r.f() * orbTau)
        }
        gal = (0..<420).map { i -> OrbGalPt in
            let disc = i % 5 == 4
            let rr = 0.72 * pow(r.f(), disc ? 0.8 : 1.25)
            let a = disc ? r.f() * orbTau : CGFloat(i % 2) * .pi + rr * 7.2
            let spread: CGFloat = disc ? 0.1 : 0.16
            let ox = galScatter(r, spread, rr)
            let oy = galScatter(r, 0.04, rr)
            let oz = galScatter(r, spread, rr)
            let ph = r.f() * orbTau
            return OrbGalPt(rr: rr, a: a, ox: ox, oy: oy, oz: oz, w: 0.05 / (0.1 + rr), ph: ph, s: 0.4 + r.f() * 0.8)
        }
        neb = (0..<46).map { _ -> OrbNeb in
            let d = 0.16 + 0.68 * r.f().squareRoot()
            let u = r.f() * 2 - 1
            let th = r.f() * orbTau
            let q = (1 - u * u).squareRoot()
            let dark = r.f() < 0.24
            let s = dark ? 0.14 + r.f() * 0.18 : 0.22 + r.f() * 0.36
            let c = min(Int(r.f() * 5), 4)
            let ph = r.f() * orbTau
            let al: CGFloat = dark ? 0.42 : 0.1 + r.f() * 0.08
            let el = 0.4 + r.f() * 0.55
            return OrbNeb(x: q * cos(th) * d, y: u * d * 0.8, z: q * sin(th) * d, s: s, c: c, dark: dark, ph: ph, al: al, el: el,
                          rot: r.f() * .pi)
        }
        shootAt = 1.2 + r.f() * 2
        far = (0..<3).map { _ -> OrbFar in
            let u = r.f() * 1.4 - 0.7
            let th = r.f() * orbTau
            let q = (1 - u * u).squareRoot()
            let rot = r.f() * .pi
            return OrbFar(x: q * cos(th) * 0.88, y: u * 0.88, z: q * sin(th) * 0.88, rot: rot, s: 0.05 + r.f() * 0.04)
        }
        var dust: [OrbRingDust] = []
        dust.reserveCapacity(420)
        while dust.count < 420 {
            let a = r.f() * orbTau
            let dens = 0.55 + 0.45 * sin(a * 3 + 1.3) * sin(a * 7 + 0.4)
            if r.f() > dens + 0.25 { continue }
            let gj = (r.f() + r.f() + r.f() - 1.5) / 1.5
            let y = abs(gj) * 4 * r.f()
            let s = 0.3 + pow(r.f(), 3) * 1.1
            let ph = r.f() * orbTau
            dust.append(OrbRingDust(a: a, rr: 1 + gj * 0.05, y: y, s: s, ph: ph, f: 0.6 + r.f() * 2.2))
        }
        ringDust = dust
        ring1 = (0..<56).map { _ -> OrbRingStar in
            let big = r.f() < 0.15
            let a = r.f() * orbTau
            let rr = 1 + (r.f() - 0.5) * 0.08
            let s = big ? 1.3 + r.f() * 1.3 : 0.4 + pow(r.f(), 2) * 0.9
            let f = 0.7 + r.f() * 2.4
            let ph = r.f() * orbTau
            let y: CGFloat = r.f() < 0.2 ? 3 + r.f() * 9 : 0
            return OrbRingStar(a: a, rr: rr, s: s, big: big, f: f, ph: ph, y: y, link: r.f() < 0.62)
        }.sorted { $0.a < $1.a }
        constel = (0..<7).map { k -> [OrbCPt] in
            let c0 = CGFloat(k) / 7 * orbTau + 0.22 + (r.f() - 0.5) * 0.16
            let m = 3 + min(Int(r.f() * 3), 2)
            return (0..<m).map { _ -> OrbCPt in
                let a = c0 + (r.f() - 0.5) * 0.3
                let rr = 0.76 + r.f() * 0.12
                let s = 0.6 + r.f() * 1.1
                return OrbCPt(a: a, rr: rr, s: s, ph: r.f() * orbTau)
            }.sorted { $0.a < $1.a }
        }
        heptaDust = (0..<35).map { i -> OrbHDust in
            let u = 0.08 + r.f() * 0.84
            let off = (r.f() - 0.5) * 0.03
            let s = 0.3 + r.f() * 0.8
            return OrbHDust(e: i / 5, u: u, off: off, s: s, ph: r.f() * orbTau)
        }
        ring2 = (0..<36).map { i -> OrbCPt in
            let a = CGFloat(i) / 36 * orbTau + (r.f() - 0.5) * 0.14
            let rr = 0.5 + (r.f() - 0.5) * 0.04
            let s = 0.35 + r.f() * 0.85
            return OrbCPt(a: a, rr: rr, s: s, ph: r.f() * orbTau)
        }
        ring3 = (0..<6).map { i -> OrbCPt in
            let a = CGFloat(i) / 6 * orbTau + (r.f() - 0.5) * 0.25
            let rr = 0.26 + (r.f() - 0.5) * 0.03
            let s = 0.9 + r.f() * 0.9
            return OrbCPt(a: a, rr: rr, s: s, ph: r.f() * orbTau)
        }
        risers = (0..<40).map { _ -> OrbRiser in
            let a = r.f() * orbTau
            let d = r.f().squareRoot() * circR * 0.95
            let p0 = r.f()
            let life = 2.6 + r.f() * 3
            let h = 40 + r.f() * 90
            let s = 0.6 + r.f() * 1.2
            let ph = r.f() * orbTau
            return OrbRiser(a: a, d: d, p0: p0, life: life, h: h, s: s, ph: ph, sw: (r.f() - 0.5) * 0.9)
        }
        flow = (0..<760).map { i -> OrbFlow in
            let gj = (r.f() + r.f() + r.f() - 1.5) / 1.5
            let p0 = r.f()
            let life = 4 + r.f() * 3
            let rj = 0.8 + r.f() * 0.3 + abs(gj) * 0.2
            let s = 0.45 + pow(r.f(), 2.6) * 1.6
            let ci = 1 + min(Int(r.f() * 3), 2)
            let ph = r.f() * orbTau
            let f = 1 + r.f() * 3
            return OrbFlow(p0: p0, life: life, arm: i % 2, off: gj * 0.32, rj: rj, s: s, ci: ci, ph: ph, f: f,
                           bright: r.f() < 0.04, core: abs(gj) < 0.25)
        }
        band = (0..<40).map { i -> OrbBand in
            let dark = i % 4 == 3
            let p0 = r.f()
            let life = 7 + r.f() * 5
            let off = (r.f() - 0.5) * 0.3 + (dark ? 0.18 : 0)
            let rj = 0.85 + r.f() * 0.3
            let s = dark ? 9 + r.f() * 10 : 16 + r.f() * 22
            let ci = 1 + min(Int(r.f() * 3), 2)
            let al: CGFloat = dark ? 0.32 : 0.08 + r.f() * 0.06
            return OrbBand(p0: p0, life: life, arm: i % 2, off: off, rj: rj, s: s, ci: ci, al: al, dark: dark)
        }
        motes = (0..<34).map { _ -> OrbMote in
            let a0 = r.f() * orbTau
            let speed = 0.16 + r.f() * 0.38
            let w: CGFloat = speed * (r.f() < 0.5 ? 1 : -1)
            let rr = orbRW * (1.25 + r.f() * 0.7)
            let inc = (r.f() - 0.5) * 1.2
            let yaw = r.f() * orbTau
            let s = 0.5 + r.f() * 1.2
            let ph = r.f() * orbTau
            return OrbMote(a0: a0, w: w, rr: rr, s: s, ph: ph, f: 1 + r.f() * 3, si: sin(inc), ci: cos(inc), sy: sin(yaw), cy: cos(yaw))
        }
        setRoute(rare: rare, animate: false)
        updatePalette()
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

    /// "동작 줄이기" — 움직임을 줄이고 번쩍임을 뺀다.
    func setReducedMotion(_ on: Bool) {
        motion = on ? 0.35 : 1
        flashOk = !on
    }

    // MARK: 조작

    /// 경로 색 — animate 면 0.9초 동안 섞는다(지금 보이는 색에서 이어 간다. 이미 그쪽으로 가고 있으면 그대로 둔다).
    func setRoute(rare: Bool, animate: Bool) {
        let target: CGFloat = rare ? 1 : 0
        if self.rare == rare && toM == target { return }
        self.rare = rare
        if animate {
            fromM = max(0, palM)
            toM = target
            mixT = 0
        } else {
            fromM = target
            toM = target
            mixT = 1
        }
        updatePalette()
    }

    /// 누른 쪽으로 돌린다(-1 왼쪽 · 1 오른쪽) — 더해지기만 하니 빨리 돌 때 반대쪽은 먼저 브레이크가 된다.
    func spin(_ side: Int) {
        omega = min(max(omega + CGFloat(side) * spinKick, -spinMax), spinMax)
        if abs(omega) > spinIdle * 0.5 { idleDir = omega < 0 ? -1 : 1 }
        pulse = 1
    }

    func rubStart() {
        if !rubLock {
            rubbing = true
            lastRubT = t
        }
    }

    /// 문지르기 — (x, y) 무대 단위, dist 움직인 거리, dt 직전 움직임과의 간격(초). 다 차서 경로가 바뀌었으면 true.
    func rubMove(_ x: CGFloat, _ y: CGFloat, dist: CGFloat, dt: CGFloat) -> Bool {
        if !rubbing || rubLock { return false }
        if hyp(x - bx, y - by) < orbR * 1.45 {
            rubP = min(1, rubP + min(dist / rubLen, max(0, dt) / rubMinT))
            lastRubT = t
        }
        if rubSparks.count < 90 {
            for _ in 0..<2 {
                let vx = (rnd.f() - 0.5) * 70
                let vy = (rnd.f() - 0.5) * 70 - 24
                let life = 0.45 + rnd.f() * 0.4
                rubSparks.append(OrbRubSpark(x: x, y: y, vx: vx, vy: vy, born: t, life: life, s: 0.8 + rnd.f() * 1.5))
            }
        }
        if rubP >= 1 {
            rubLock = true
            rubbing = false
            transform()
            return true
        }
        return false
    }

    func rubEnd() {
        rubbing = false
        rubLock = false
    }

    /// 경로 바꾸기 — 색이 0.9초 동안 섞이고, 번쩍임 · 바닥 고리 · 불꽃이 함께 터진다.
    func transform() {
        setRoute(rare: !rare, animate: true)
        flash = 1
        rubP = 0
        pulse = 1
        omega = idleDir * max(abs(omega), 7)
        waves.append(OrbWave(born: t, dur: 1.1, c: (rare ? OrbPalette.rare : OrbPalette.normal).line))
        for _ in 0..<26 {
            let a = rnd.f() * orbTau
            let v = 60 + rnd.f() * 120
            let life = 0.6 + rnd.f() * 0.5
            let s = 1 + rnd.f() * 1.8
            rubSparks.append(OrbRubSpark(x: bx + cos(a) * orbR * 0.9, y: by + sin(a) * orbR * 0.9,
                                         vx: cos(a) * v, vy: sin(a) * v - 30, born: t, life: life, s: s))
        }
    }

    /// 두드림(무대 단위 좌표) — 무대에서는 누른 쪽으로 돌리기, 깨우기 창에서는 금. 금이 갔으면 true.
    @discardableResult
    func tap(_ ux: CGFloat, _ uy: CGFloat) -> Bool {
        pulse = 1
        if !interactive {
            spin(ux < bx ? -1 : 1)
            return false
        }
        guard phase == .idle, taps < 3 else { return false }
        var x = (ux - bx) / orbR
        var y = (uy - by) / orbR
        if hyp(x, y) > 0.92 {
            let a = rnd.f() * orbTau
            let d = 0.15 + rnd.f() * 0.45
            x = cos(a) * d
            y = sin(a) * d
        }
        taps += 1
        spin(x < 0 ? -1 : 1)
        shake = 1
        switch taps {
        case 1:
            impacts.append(makeImpact(x, y, 0.8))
        case 2:
            impacts[0].target = 1.25
            impacts.append(makeImpact(x, y, 1))
        default:
            for im in impacts { im.target = max(im.target, 1.5) }
            impacts.append(makeImpact(x, y, 1.25))
            if pending != nil {
                startConverge()
            } else {
                phase = .wait
            }
        }
        spawnFlakes(x, y, taps == 3 ? 12 : 7)
        return true
    }

    /// 결과 등급 — 세 번 두드린 뒤라면 바로 모이기 시작한다.
    func setResult(_ rarity: String) {
        pending = rarity
        if phase == .wait { startConverge() }
    }

    private func startConverge() {
        guard let rar = pending else { return }
        lv = rarityLevel(rar)
        rc = burstColor(rar)
        for im in impacts { im.target = 1.9 }
        // 가장자리까지 간 금 — 빛이 새어 나갈 자리
        var out: [OrbLeak] = []
        for im in impacts {
            for ray in im.rays {
                let n = ray.n - 1
                let x = ray.xs[n]
                let y = ray.ys[n]
                if x * x + y * y > 0.9 && out.count < 14 {
                    let w = 0.035 + rnd.f() * 0.03
                    out.append(OrbLeak(a: atan2(y, x), w: w, l: 0.8 + rnd.f() * 0.7))
                }
            }
        }
        leaks = out
        phase = .converge
        pt = 0
        onConverge?()
    }

    /// 건너뛰기 — 조각 없이 결과 빛만 남기고 카드로.
    func skip() {
        guard let rar = pending else { return }
        lv = rarityLevel(rar)
        rc = burstColor(rar)
        shards = []
        grit = []
        escape = []
        puffs = []
        needShards = false
        phase = .reveal
        pt = 3
        if !cardsReady { cardsReady = true }
    }

    // MARK: 시간

    func step(_ dt: CGFloat) {
        t += dt
        let now = t
        let m = motion
        pulse = max(0, pulse - dt * 1.8)
        flash = max(0, flash - dt * 1.5)
        if mixT < 1 { mixT = min(1, mixT + dt / 0.9) }
        updatePalette()
        // 문지르기 — 손을 멈추면 천천히, 떼면 빨리 빠진다
        if rubbing && now - lastRubT > 0.15 { rubP = max(0, rubP - dt * 0.8) }
        if !rubbing && rubP > 0 { rubP = max(0, rubP - dt * 1.6) }
        // 돌기 — 목표(평소: 마지막 방향으로 천천히)로 지수적으로 돌아간다
        var target = idleDir * spinIdle
        var rate = spinDecay
        if rubbing {
            target = idleDir * (1.5 + 7 * rubP)
            rate = 2.5
        }
        if phase == .wait {
            target = idleDir * 3
            rate = 1.2
        }
        if phase == .converge {
            target = idleDir * (3 + 15 * conv)
            rate = 3
        }
        omega += (target - omega) * (1 - exp(-dt * rate))
        spinA += omega * dt * m
        let flowBoost: CGFloat = phase == .converge ? conv * 1.6 : 0
        let ringBoost: CGFloat = phase == .converge ? conv * 4 : 0
        flowA += dt * m * (0.22 + abs(omega) * 0.03 + flowBoost)
        ringA += dt * m * (1 + abs(omega) * 0.07 + ringBoost)
        // 가끔 속을 가로지르는 별똥별
        if now > shootAt {
            let a = rnd.f() * orbTau
            let d = 0.15 + rnd.f() * 0.5
            let dir = a + .pi * (0.65 + rnd.f() * 0.7)
            shoot = OrbShoot(x: cos(a) * d, y: sin(a) * d, dir: dir, born: now)
            shootAt = now + 2.2 + rnd.f() * 3.5
        }
        rubSparks.removeAll { now - $0.born >= $0.life }
        waves.removeAll { now - $0.born >= $0.dur }
        flakes.removeAll { now - $0.born >= $0.life }
        // 금 번짐 — 두드릴 때는 "팍"(0.2초), 기다릴 땐 천천히
        let gr: CGFloat
        switch phase {
        case .converge: gr = 3
        case .wait: gr = 1.2
        default: gr = 13
        }
        let follow = min(1, dt * gr)
        for im in impacts { im.g += (im.target - im.g) * follow }
        shake = max(0, shake - dt * 6)
        switch phase {
        case .converge:
            pt += dt
            if pt >= convDur {
                phase = .shatter
                pt = 0
                needShards = true
                flareAt = now
                waves.append(OrbWave(born: now, dur: 1.3, c: rc))
                if lv >= 2 { waves.append(OrbWave(born: now + 0.16, dur: 1.4, c: rc.mix(.white, 0.4))) }
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
        if !cardsReady && shatterAge >= orbCardsAt { cardsReady = true }
    }

    /// 경로 색 섞기 + 문지르는 동안 반대 경로 쪽으로 반쯤. 바뀐 게 없으면 그대로 둔다.
    private func updatePalette() {
        let e: CGFloat = mixT < 1 ? easeInOut(mixT) : 1
        var m = fromM + (toM - fromM) * e
        if rubP > 0 && mixT >= 1 { m += ((rare ? 0 : 1) - m) * rubP * 0.5 }
        if m == palM { return }
        palM = m
        pal = OrbPalette.lerp(.normal, .rare, m)
        palVer += 1
    }

    // MARK: 프레임 시계

    /// 프레임 시계 켜기/끄기 — 화면이 보이는 동안만(무대 수정구는 깨우기 창이 덮는 동안 쉰다).
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

    /// 한 프레임 — 캔버스 pt 좌표에서 무대 원점이 (ox, oy), 무대 1단위 = k pt. viewW×viewH 는 캔버스 크기.
    /// 카드가 뜨면 stageAlpha 로 무대를 지우고, 후광은 auraShift 만큼 (auraX, auraY)(무대 단위)로 옮긴다.
    func draw(
        _ c: inout GraphicsContext, k: CGFloat, ox: CGFloat, oy: CGFloat, viewW: CGFloat, viewH: CGFloat,
        stageAlpha: CGFloat = 1, auraShift: CGFloat = 0, auraX: CGFloat = orbCX, auraY: CGFloat = orbCY
    ) {
        guard k > 0 else { return }
        lastK = k
        var g = c
        g.translateBy(x: ox, y: oy)
        g.scaleBy(x: k, y: k)
        viewTop = -oy / k
        let m = motion
        let bob = sin(t * 1.15) * 3.2 * m
        orbYW = orbY0 + bob / (camCE * orbS)
        var cx = orbCX + sin(t * 0.7) * 1.1 * m
        var cy = orbCY - bob
        let sa = c01(stageAlpha)
        fade = sa
        if sa > 0 {
            prepCurtain()
            prepFlow()
            drawCircle(&g)
            drawCurtain(&g, front: false)
            drawBand(&g)
            drawCore(&g)
            drawFlow(&g, front: false)
            drawRisers(&g, front: false)
            drawMotes(&g, front: false)
        }
        fade = 1
        switch phase {
        case .idle, .wait, .converge:
            var j: CGFloat = shake * 2.4
            if phase == .converge { j += conv * 2.6 }
            if phase == .wait { j += 0.8 }
            if j > 0 {
                cx += (rnd.f() - 0.5) * j
                cy += (rnd.f() - 0.5) * j
            }
            bx = cx
            by = cy
            drawBall(&g, cx, cy, snap: false)
            if phase == .converge { drawLeaks(&g, cx, cy) }
        case .shatter, .reveal:
            let s = shatterAge
            let ax = bx + (auraX - bx) * auraShift
            let ay = by + (auraY - by) * auraShift
            aura(&g, ax, ay, orbR, c01((s - 0.4) / 1) * (1 - 0.35 * auraShift))
            if sa > 0 {
                fade = sa
                emblem(&g, bx, by, orbR * 0.28 * backOut(c01((s - 0.1) / 0.5)), rc, c01((s - 0.05) / 0.25))
                fade = 1
            }
            if phase == .shatter {
                drawPillar(&g, s)
                drawEscape(&g, s)
                drawShards(&g, s)
                rarityBurst(&g, bx, by, orbR, s)
            }
        }
        drawFlakes(&g)
        fade = sa
        if sa > 0 {
            drawCurtain(&g, front: true)
            drawFlow(&g, front: true)
            drawRisers(&g, front: true)
            drawMotes(&g, front: true)
        }
        fade = 1
        drawRub(&g)
    }

    // MARK: 빛 · 반짝이 · 점

    /// 둥근 빛 번짐(시안 sprite — 0:1 · .22:.6 · .55:.16 · 1:0).
    private func glow(_ c: inout GraphicsContext, _ col: OrbRGB, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a: CGFloat, _ add: Bool) {
        let aa = a * fade
        if aa <= 0.002 || r <= 0.1 { return }
        let al = min(aa, 1)
        let stops: [Gradient.Stop] = [
            .init(color: col.color(al), location: 0),
            .init(color: col.color(al * 0.6), location: 0.22),
            .init(color: col.color(al * 0.16), location: 0.55),
            .init(color: col.color(0), location: 1),
        ]
        c.blendMode = add ? .plusLighter : .normal
        c.fill(
            Path(CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(Gradient(stops: stops), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: r)
        )
    }

    /// 길게 늘인 빛(성운 가닥 · 먼 은하).
    private func glowE(_ c: inout GraphicsContext, _ col: OrbRGB, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat,
                       _ sy: CGFloat, _ rot: CGFloat, _ a: CGFloat, _ add: Bool) {
        let aa = a * fade
        if aa <= 0.002 || r <= 0.1 { return }
        var g = c
        g.translateBy(x: x, y: y)
        g.rotate(by: .radians(Double(rot)))
        g.scaleBy(x: 1, y: sy)
        glow(&g, col, 0, 0, r, a, add)
    }

    /// 네 꼭지 반짝이(가운데를 조절점으로 휜 변).
    private func sparkle(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a: CGFloat, _ col: OrbRGB, _ add: Bool) {
        let aa = a * fade
        if aa <= 0.002 || r <= 0.1 { return }
        c.blendMode = add ? .plusLighter : .normal
        c.fill(StarlightArt.sparkle(CGPoint(x: x, y: y), r), with: col.shade(aa))
    }

    /// 작은 둥근 점(지름 ≈ s) — 시안에서 점은 늘 더하기 합성.
    private func dot(_ c: inout GraphicsContext, _ col: OrbRGB, _ x: CGFloat, _ y: CGFloat, _ s: CGFloat, _ a: CGFloat) {
        let aa = a * fade
        if aa <= 0.002 { return }
        let r = s * 0.56
        c.blendMode = .plusLighter
        c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: col.shade(aa))
    }

    /// 두 점 사이 그라데이션 선(같은 색, 알파 a0 → a1).
    private func gradLine(_ c: inout GraphicsContext, _ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat,
                          _ col: OrbRGB, _ a0: CGFloat, _ a1: CGFloat, _ w: CGFloat, _ add: Bool) {
        if a0 < 0.002 && a1 < 0.002 { return }
        let p0 = CGPoint(x: x0, y: y0)
        let p1 = CGPoint(x: x1, y: y1)
        var p = Path()
        p.move(to: p0)
        p.addLine(to: p1)
        c.blendMode = add ? .plusLighter : .normal
        c.stroke(p, with: .linearGradient(Gradient(colors: [col.color(a0), col.color(a1)]), startPoint: p0, endPoint: p1), lineWidth: w)
    }

    /// 한 점에서 뻗는 쐐기 빛(늘 더하기).
    private func wedge(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ ang: CGFloat, _ len: CGFloat, _ w: CGFloat,
                       _ c0: Color, _ c1: Color) {
        var p = Path()
        p.move(to: CGPoint(x: x, y: y))
        p.addLine(to: CGPoint(x: x + cos(ang - w) * len, y: y + sin(ang - w) * len))
        p.addLine(to: CGPoint(x: x + cos(ang + w) * len, y: y + sin(ang + w) * len))
        p.closeSubpath()
        let end = CGPoint(x: x + cos(ang) * len, y: y + sin(ang) * len)
        c.blendMode = .plusLighter
        c.fill(p, with: .linearGradient(Gradient(colors: [c0, c1]), startPoint: CGPoint(x: x, y: y), endPoint: end))
    }

    // MARK: 마법진

    private func flare() -> CGFloat {
        var f: CGFloat = 0
        if phase == .converge { f += conv * 0.9 }
        if flareAt > 0 { f += 1.8 * exp(-(t - flareAt) * 1.8) }
        return 1 + f + flash * 0.8 + pulse * 0.15
    }

    /// 마법진을 도는 빛 띠(쓸고 지나가는 자리가 밝다).
    private func boost(_ a: CGFloat) -> CGFloat {
        var d = abs(((a - sweep).truncatingRemainder(dividingBy: orbTau) + orbTau).truncatingRemainder(dividingBy: orbTau))
        d = min(d, orbTau - d)
        return exp(-d * d / 0.05)
    }

    /// 땅 위 원 하나를 원근으로(뒤 반쪽 · 앞 반쪽 따로 — 뒤가 흐리다). idx 0..2 = 고정 안내선, -1 = rr · y 로 새로.
    private func ringLine(_ c: inout GraphicsContext, _ idx: Int, _ rr: CGFloat, _ y: CGFloat, _ col: OrbRGB,
                          _ aF: CGFloat, _ aB: CGFloat, _ w: CGFloat) {
        c.blendMode = .plusLighter
        for half in 0...1 {
            let p = idx >= 0 ? Self.staticRings[idx * 2 + half] : ringPath(rr, y, front: half == 1)
            c.stroke(p, with: col.shade((half == 1 ? aF : aB) * fade), lineWidth: w)
        }
    }

    private func drawCircle(_ c: inout GraphicsContext) {
        let p = pal
        let rot = ringA * 0.03
        let fl = flare()
        let ff = min(1.6, fl)
        sweep = (t * 0.5).truncatingRemainder(dividingBy: orbTau)
        // 바닥 빛(수정구가 비추는 웅덩이 + 은하수 뿌리)
        var pool = c
        pool.translateBy(x: stageCX, y: stageGY)
        pool.scaleBy(x: 1, y: camSE * 1.05)
        glow(&pool, p.glow, 0, 0, circR * 1.5, 0.11 * fl, true)
        glow(&pool, p.glow.mix(.white, 0.35), 0, 0, circR * 0.55, (0.14 + sin(t * 1.15) * 0.03) * fl, true)
        // 가는 안내선 — 바깥 하나는 땅에, 안쪽 둘은 층마다 떠 있다(높이가 달라 원근으로 어긋나 보인다)
        ringLine(&c, 0, 0, 0, p.line, 0.1 * fl, 0.045 * fl, 0.5)
        ringLine(&c, 1, 0, 0, p.line, 0.15 * fl, 0.06 * fl, 0.5)
        ringLine(&c, 2, 0, 0, p.line, 0.2 * fl, 0.08 * fl, 0.5)
        // 퍼져 나가는 고리(문지르기 완료 · 깨짐)
        for w in waves {
            let u = (t - w.born) / w.dur
            if u < 0 || u > 1 { continue }
            let rr = circR * (0.08 + 1.3 * outC(u))
            ringLine(&c, -1, rr, 1, w.c, 0.8 * (1 - u), 0.4 * (1 - u), 0.5 + 1.8 * (1 - u))
        }
        // 별가루 고리
        for d in ringDust {
            let a = d.a + rot
            let q = proj(cos(a) * circR * d.rr, d.y, sin(a) * circR * d.rr)
            let tw = 0.55 + 0.45 * sin(t * d.f + d.ph)
            let al = dep(sin(a) * circR) * tw * (0.7 + 0.8 * boost(a)) * ff
            dot(&c, p.cstar, q.x, q.y, d.s * q.s, al)
        }
        // 밝은 별 — 이웃끼리 짧은 사슬로, 떠 있는 별은 땅까지 빛 실을 늘어뜨린다
        let n1 = ring1.count
        var r1 = [OrbP]()
        var r1X = [CGFloat]()
        var r1Z = [CGFloat]()
        var r1a = [CGFloat]()
        r1.reserveCapacity(n1)
        r1X.reserveCapacity(n1)
        r1Z.reserveCapacity(n1)
        r1a.reserveCapacity(n1)
        for s in ring1 {
            let a = s.a + rot
            let x = cos(a) * circR * s.rr
            let z = sin(a) * circR * s.rr
            r1.append(proj(x, s.y, z))
            r1X.append(x)
            r1Z.append(z)
            r1a.append(a)
        }
        c.blendMode = .plusLighter
        for i in 0..<n1 {
            let j = (i + 1) % n1
            var gap = ring1[j].a - ring1[i].a
            if gap < 0 { gap += orbTau }
            if !ring1[i].link || gap > 0.3 { continue }
            var l = Path()
            l.move(to: CGPoint(x: r1[i].x, y: r1[i].y))
            l.addLine(to: CGPoint(x: r1[j].x, y: r1[j].y))
            c.stroke(l, with: p.line.shade(0.3 * dep((r1Z[i] + r1Z[j]) / 2) * ff * fade), lineWidth: 0.5)
        }
        for i in 0..<n1 {
            let s = ring1[i]
            let tw = 0.55 + 0.45 * sin(t * s.f + s.ph)
            let b = boost(r1a[i])
            let al = dep(r1Z[i]) * tw * (0.75 + 0.9 * b) * ff
            let sz = s.s * r1[i].s
            if s.y > 0 {
                let g0 = proj(r1X[i], 0, r1Z[i])
                gradLine(&c, r1[i].x, r1[i].y, g0.x, g0.y, p.line, 0.32 * al * fade, 0, 0.5, true)
            }
            if s.big {
                glow(&c, p.glow, r1[i].x, r1[i].y, sz * 5, 0.3 * al, true)
                sparkle(&c, r1[i].x, r1[i].y, sz * (1.7 + b), al, p.cstar, true)
            } else {
                dot(&c, p.cstar, r1[i].x, r1[i].y, sz * 1.15, al * 0.9)
            }
        }
        // 별자리 띠 — 반대로 돈다(시안의 합성 상태 그대로: 첫 무리의 선만 더하기)
        let rot2 = -ringA * 0.024
        for (gi, cs) in constel.enumerated() {
            var pts: [OrbP] = []
            var zs: [CGFloat] = []
            var angs: [CGFloat] = []
            for s in cs {
                let a = s.a + rot2
                pts.append(proj(cos(a) * circR * s.rr, 1, sin(a) * circR * s.rr))
                zs.append(sin(a))
                angs.append(a)
            }
            c.blendMode = gi == 0 ? .plusLighter : .normal
            for q in 0..<(cs.count - 1) {
                var l = Path()
                l.move(to: CGPoint(x: pts[q].x, y: pts[q].y))
                l.addLine(to: CGPoint(x: pts[q + 1].x, y: pts[q + 1].y))
                c.stroke(l, with: p.line.shade(0.26 * dep(zs[q] * circR) * ff * fade), lineWidth: 0.5)
            }
            for (q, s) in cs.enumerated() {
                let tw = 0.6 + 0.4 * sin(t * 1.7 + s.ph)
                let al = dep(zs[q] * circR) * tw * (0.8 + 0.8 * boost(angs[q])) * ff
                glow(&c, p.glow, pts[q].x, pts[q].y, s.s * pts[q].s * 3.2, 0.25 * al, true)
                sparkle(&c, pts[q].x, pts[q].y, s.s * pts[q].s * 1.5, al, p.cstar, true)
            }
        }
        // 칠각별 {7/3} — 꼭짓점은 밝은 별, 변은 가는 선 + 그 위 잔별
        let rot3 = ringA * 0.05
        var hv: [OrbP] = []
        var hz: [CGFloat] = []
        var ha: [CGFloat] = []
        for i in 0..<7 {
            let a = CGFloat(i) / 7 * orbTau + rot3
            hv.append(proj(cos(a) * circR * 0.64, 2, sin(a) * circR * 0.64))
            hz.append(sin(a) * circR)
            ha.append(a)
        }
        c.blendMode = .plusLighter
        for i in 0..<7 {
            let j = (i + 3) % 7
            let p0 = CGPoint(x: hv[i].x, y: hv[i].y)
            let p1 = CGPoint(x: hv[j].x, y: hv[j].y)
            var l = Path()
            l.move(to: p0)
            l.addLine(to: p1)
            let stops: [Gradient.Stop] = [
                .init(color: p.line.color(0.24 * dep(hz[i]) * fl * fade), location: 0),
                .init(color: p.line.color(0.06 * fl * fade), location: 0.5),
                .init(color: p.line.color(0.24 * dep(hz[j]) * fl * fade), location: 1),
            ]
            c.stroke(l, with: .linearGradient(Gradient(stops: stops), startPoint: p0, endPoint: p1), lineWidth: 0.5)
        }
        for d in heptaDust {
            let i = d.e
            let j = (i + 3) % 7
            let x = hv[i].x + (hv[j].x - hv[i].x) * d.u
            let y = hv[i].y + (hv[j].y - hv[i].y) * d.u + d.off * circR
            dot(&c, p.cstar, x, y, d.s, (0.35 + 0.35 * sin(t * 2.2 + d.ph)) * ff)
        }
        for i in 0..<7 {
            let al = dep(hz[i]) * (0.75 + 0.25 * sin(t * 2 + ha[i] * 3)) * (0.8 + boost(ha[i])) * ff
            glow(&c, p.glow, hv[i].x, hv[i].y, 9 * hv[i].s, 0.3 * al, true)
            sparkle(&c, hv[i].x, hv[i].y, 2.8 * hv[i].s, al, p.cstar, true)
        }
        // 떠 있는 둘째 층(높이 14) · 셋째 층(높이 30, 땅까지 빛 실)
        let rot4 = -ringA * 0.08
        for s in ring2 {
            let a = s.a + rot4
            let q = proj(cos(a) * circR * s.rr, 14, sin(a) * circR * s.rr)
            dot(&c, p.cstar, q.x, q.y, s.s * q.s * 1.1, dep(sin(a) * circR) * (0.45 + 0.3 * sin(t * 2.4 + s.ph)) * ff)
        }
        let rot5 = ringA * 0.12
        for (i, s) in ring3.enumerated() {
            let a = s.a + rot5
            let x = cos(a) * circR * s.rr
            let z = sin(a) * circR * s.rr
            let top = proj(x, 30, z)
            let bot = proj(x, 0, z)
            gradLine(&c, top.x, top.y, bot.x, bot.y, p.line, 0.3 * fl * fade, 0, 0.55, i == 0)
            glow(&c, p.glow, top.x, top.y, 7 * top.s, 0.3 * fl, true)
            sparkle(&c, top.x, top.y, s.s * 1.6 * top.s, (0.7 + 0.3 * sin(t * 2 + s.ph)) * ff, p.cstar, true)
        }
        // 빛기둥 — 바깥 고리 별 몇 개에서 잠깐 솟는다
        for i in 0..<5 {
            let cyc = t / 2.7 + CGFloat(i) / 5
            let u = cyc.truncatingRemainder(dividingBy: 1)
            let idx = Int(floor(cyc * 7.31 + CGFloat(i) * 13)) % n1
            let s = ring1[idx]
            let a = s.a + rot
            let x = cos(a) * circR * s.rr
            let z = sin(a) * circR * s.rr
            let h = 22 + CGFloat((idx * 37) % 40)
            let al = sin(.pi * u) * dep(z) * 0.5 * min(1.6, fl)
            let g0 = proj(x, 0, z)
            let g1 = proj(x, h * (0.6 + 0.4 * u), z)
            gradLine(&c, g0.x, g0.y, g1.x, g1.y, p.line, al * fade, 0, 0.9 * g0.s, false)
        }
        // 가운데 — 은하수가 솟는 자리
        let ce = proj(0, 3, 0)
        glow(&c, p.band[0], ce.x, ce.y, 26, 0.5 * min(1.8, fl), true)
        glow(&c, .white, ce.x, ce.y, 7, 0.7, true)
        sparkle(&c, ce.x, ce.y, 6 + sin(t * 2.3) * 1.2, 0.9, .white, true)
    }

    // MARK: 은하수

    /// 꽃잔 위의 한 점 — 오른 정도 u(0 뿌리 ~ 1 가장자리) · 팔 · 팔에서 벗어난 각 · 반지름 배율 → 세계 좌표(x, y, z)와 높이 비율 h.
    /// 마법진 한가운데서 가늘게 솟아 수정구 아래에서 활짝 벌어지고, 끝(u > .82)에서는 꽃잎처럼 바깥 · 아래로 살짝 휜다.
    /// 잔 전체는 flowA 로 돌고, 모일 때는 조인다.
    private func cupPoint(_ u: CGFloat, _ arm: Int, _ off: CGFloat, _ rj: CGFloat) -> (x: CGFloat, y: CGFloat, z: CGFloat, h: CGFloat) {
        let w = min(u / 0.82, 1)
        let curl: CGFloat = u > 0.82 ? (u - 0.82) / 0.18 : 0
        let y = cupTop * (1 - pow(1 - w, 1.6)) - curl * curl * 16
        let h = y / cupTop
        let open = 0.05 + 1.18 * pow(smooth(0, 1, h), 1.2)
        let rr = orbRW * open * rj * (1 + curl * 0.22) * (1 - 0.3 * inC(conv))
        let a = CGFloat(arm) * .pi + off + u * orbTau * 1.55 + flowA
        return (cos(a) * rr, y, sin(a) * rr, h)
    }

    /// 부드러운 성운 띠(밝은 덩이 + 팔 안쪽을 따라가는 어두운 먼지 띠).
    private func drawBand(_ c: inout GraphicsContext) {
        let p = pal
        for b in band {
            let u = (t / b.life + b.p0).truncatingRemainder(dividingBy: 1)
            let f = cupPoint(u, b.arm, b.off, b.rj)
            let q = proj(f.x, f.y, f.z)
            let al = sin(.pi * u) * b.al
            if b.dark {
                glow(&c, p.ink, q.x, q.y, b.s * q.s, al, false)
            } else {
                glow(&c, p.band[0].mix(p.band[b.ci], smooth(0.1, 1, f.h)), q.x, q.y, b.s * q.s, al, true)
            }
        }
    }

    /// 가운데 빛 기둥 — 마법진 한가운데에서 수정구 바닥까지, 아래가 밝다.
    private func drawCore(_ c: inout GraphicsContext) {
        let p = pal
        let fl = min(1.8, flare())
        let top = orbYW - orbRW * 0.85
        for i in 0..<10 {
            let u = CGFloat(i) / 9
            let q = proj(0, top * u, 0)
            glow(&c, p.band[0].mix(p.band[1], u), q.x, q.y, (10 + 9 * u) * q.s, (0.28 - 0.18 * u) * fl, true)
        }
    }

    /// 별가루 자리 · 빛 · 색을 한 번 셈한다(뒤 반쪽 · 앞 반쪽이 나눠 그린다). 수정구 속으로 들어가는 것은 깨지기 전까지 가린다.
    private func prepFlow() {
        let p = pal
        let oy = orbYW
        let whole = shatterAge < 0
        let inside = orbRW * orbRW * 1.03
        flFl = min(1.5, flare())
        for i in 0..<flow.count {
            let f = flow[i]
            let u = (t / f.life + f.p0).truncatingRemainder(dividingBy: 1)
            let cp = cupPoint(u, f.arm, f.off, f.rj)
            let front = cp.z > 0
            let dy = cp.y - oy
            if whole && cp.x * cp.x + dy * dy + cp.z * cp.z < inside {
                flowP[i].side = 0
                continue
            }
            let q = proj(cp.x, cp.y, cp.z)
            let rise: CGFloat = u < 0.05 ? u / 0.05 : 1
            let fall: CGFloat = u > 0.8 ? c01(1 - (u - 0.8) / 0.2) : 1
            let tw = 0.6 + 0.4 * sin(t * f.f + f.ph)
            let al = rise * fall * (front ? 0.85 : 0.6) * tw * (f.core ? 1.2 : 0.8)
            flowP[i] = OrbFlowP(x: q.x, y: q.y, s: f.s * q.s, a: al, c: p.band[0].mix(p.band[f.ci], smooth(0.15, 1, cp.h)), side: front ? 2 : 1)
        }
    }

    /// 별가루 — 두 팔에 몰려 감겨 오른다.
    private func drawFlow(_ c: inout GraphicsContext, front: Bool) {
        let want: UInt8 = front ? 2 : 1
        for i in 0..<flow.count {
            let q = flowP[i]
            if q.side != want { continue }
            if flow[i].bright {
                glow(&c, q.c, q.x, q.y, q.s * 4.5, q.a * 0.4, true)
                sparkle(&c, q.x, q.y, q.s * 2.4, q.a, .white, true)
            } else {
                dot(&c, q.c, q.x, q.y, q.s, q.a * flFl)
            }
        }
    }

    private func prepCurtain() {
        for i in 0...72 {
            let a = CGFloat(i) / 72 * orbTau + ringA * 0.02
            let x = cos(a) * circR * 1.05
            let z = sin(a) * circR * 1.05
            let n = 0.5 + 0.5 * sin(a * 5 + t * 0.7) * sin(a * 3 - t * 0.45 + 1.7)
            let q0 = proj(x, 0, z)
            let q1 = proj(x, 14 + 40 * n, z)
            curtain[i] = OrbCurtainP(b0x: q0.x, b0y: q0.y, b1x: q1.x, b1y: q1.y, n: n, z: z)
        }
    }

    /// 빛 커튼 — 바깥 고리에서 솟는 옅은 빛의 벽. 높이 · 밝기가 천천히 물결친다(이웃 기둥끼리 사각형으로 이어 칠한다).
    private func drawCurtain(_ c: inout GraphicsContext, front: Bool) {
        let col = pal.line
        let fl = min(1.6, flare())
        c.blendMode = .plusLighter
        for i in 1...72 {
            let a = curtain[i]
            let b = curtain[i - 1]
            if ((a.z + b.z) / 2 > 0) != front { continue }
            let nn = (a.n + b.n) / 2
            let al = (0.015 + 0.085 * nn * nn) * (front ? 1 : 0.6) * fl * fade
            if al <= 0.002 { continue }
            let yb = (a.b0y + b.b0y) / 2
            let yt = (a.b1y + b.b1y) / 2
            var path = Path()
            path.move(to: CGPoint(x: b.b0x, y: b.b0y))
            path.addLine(to: CGPoint(x: a.b0x, y: a.b0y))
            path.addLine(to: CGPoint(x: a.b1x, y: a.b1y))
            path.addLine(to: CGPoint(x: b.b1x, y: b.b1y))
            path.closeSubpath()
            c.fill(path, with: .linearGradient(Gradient(colors: [col.color(al), col.color(0)]),
                                               startPoint: CGPoint(x: 0, y: yb), endPoint: CGPoint(x: 0, y: yt)))
        }
    }

    /// 수정구 둘레를 3D 궤도로 도는 티끌.
    private func drawMotes(_ c: inout GraphicsContext, front: Bool) {
        let p = pal
        for m in motes {
            let a = m.a0 + t * m.w
            let x = cos(a) * m.rr
            let z = sin(a) * m.rr
            let y1 = -z * m.si
            let z1 = z * m.ci
            let xx = x * m.cy + z1 * m.sy
            let zz = -x * m.sy + z1 * m.cy
            if (zz > 0) != front { continue }
            let q = proj(xx, orbYW + y1, zz)
            let tw = 0.5 + 0.5 * sin(t * m.f + m.ph)
            let al: CGFloat = (front ? 0.9 : 0.45) * tw
            if m.s > 1.4 {
                glow(&c, p.star, q.x, q.y, m.s * 3 * q.s, al * 0.3, true)
                sparkle(&c, q.x, q.y, m.s * 1.6 * q.s, al, .white, true)
            } else {
                dot(&c, p.star, q.x, q.y, m.s * q.s, al)
            }
        }
    }

    /// 마법진에서 피어올라 사라지는 반딧불.
    private func drawRisers(_ c: inout GraphicsContext, front: Bool) {
        let p = pal
        for s in risers {
            let u = (t / s.life + s.p0).truncatingRemainder(dividingBy: 1)
            let a = s.a + s.sw * u + ringA * 0.03
            let x = cos(a) * s.d
            let z = sin(a) * s.d
            if (z > 0) != front { continue }
            let q = proj(x, s.h * u, z)
            let size = s.s * q.s * (0.8 + 0.4 * sin(t * 4 + s.ph))
            sparkle(&c, q.x, q.y, size, sin(.pi * u) * (front ? 0.75 : 0.45), p.star, true)
        }
    }

    // MARK: 수정구

    /// 유리 몸통(구운 그림) — 경로 색이 섞이는 동안은 두 장을 미리 곱한 채로 더해 겹친다.
    private func drawBody(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let rect = CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
        let m = c01(palM)
        c.blendMode = .normal
        if m <= 0.001 || m >= 0.999 {
            if let img = OrbInk.body(rare: m >= 0.999) { c.draw(img, in: rect) }
            return
        }
        c.drawLayer { l in
            l.blendMode = .plusLighter
            if let a = OrbInk.body(rare: false) {
                l.opacity = Double(1 - m)
                l.draw(a, in: rect)
            }
            if let b = OrbInk.body(rare: true) {
                l.opacity = Double(m)
                l.draw(b, in: rect)
            }
        }
    }

    /// 구슬 한 장 — snap 이면 깨지기 직전 사진용(바깥 번짐 없이).
    private func drawBall(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, snap: Bool) {
        let p = pal
        let r = orbR * (1 + pulse * 0.022)
        if !snap { glow(&c, p.glow, cx, cy, r * 2.2, 0.22 + pulse * 0.12 + rubP * 0.25, true) }
        var inner = c
        inner.clip(to: circlePath(cx, cy, r))
        drawBody(&inner, cx, cy, r)
        drawInner(&inner, cx, cy, r)
        glassFront(&c, cx, cy, r)
        if !impacts.isEmpty { drawCracks(&c, cx, cy, r) }
    }

    private func tint(_ col: OrbRGB) -> OrbRGB { innerGo ? col.mix(rc, innerCv) : col }

    /// 속 우주 — 세로축(앞으로 기울어진, 천천히 흔들리는)을 따라 돈 3D 점들. 뒤 → 은하 → 앞 순서.
    private func drawInner(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let p = pal
        let cv = inC(conv)
        let go = going
        let tilt = innerTilt + 0.07 * sin(t * 0.37)
        let roll = 0.06 * sin(t * 0.23)
        tiltNow = tilt
        xf = OrbInnerXf(ca: cos(spinA), sa: sin(spinA), ct: cos(tilt), st: sin(tilt), cr: cos(roll), sr: sin(roll),
                        shrink: 1 - cv * 0.92, sw: rubP * 0.9, swT: t * 3.2, cx: cx, cy: cy, r: r)
        innerCv = cv
        innerGo = go
        for f in far {
            let q = xf.pr(f.x, f.y, f.z)
            glowE(&c, p.gal[1], q.x, q.y, f.s * r, 0.32, f.rot, 0.26 * (1 - cv), true)
        }
        nebPass(&c, false, r, cv)
        for i in 0..<stars.count {
            let s = stars[i]
            starQ[i] = xf.pr(s.x, s.y, s.z)
        }
        starPass(&c, false, r, cv)
        galaxy(&c, r, cv)
        nebPass(&c, true, r, cv)
        starPass(&c, true, r, cv)
        if let sh = shoot, !go {
            let su = (t - sh.born) / 0.55
            if su >= 0 && su < 1 {
                let hx = sh.x + cos(sh.dir) * 0.6 * su
                let hy = sh.y + sin(sh.dir) * 0.6 * su
                let tx = hx - cos(sh.dir) * 0.24
                let ty = hy - sin(sh.dir) * 0.24
                let fa = sin(.pi * su)
                let p0 = CGPoint(x: cx + tx * r, y: cy + ty * r)
                let p1 = CGPoint(x: cx + hx * r, y: cy + hy * r)
                var l = Path()
                l.move(to: p0)
                l.addLine(to: p1)
                c.blendMode = .plusLighter
                c.stroke(l, with: .linearGradient(Gradient(colors: [p.star.color(0), OrbRGB.white.color(0.85 * fa)]), startPoint: p0, endPoint: p1),
                         style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
                glow(&c, .white, p1.x, p1.y, 4, 0.6 * fa, true)
            }
        }
        glow(&c, p.band[0], cx, cy + r * 0.84, r * 0.55, 0.16 + rubP * 0.12, true)
        glow(&c, p.glow, cx, cy, r * 0.75, 0.1 + pulse * 0.14 + rubP * 0.12, true)
        if go {
            glow(&c, rc, cx, cy, r * (0.15 + 0.55 * cv), cv * 0.95, true)
            glow(&c, .white, cx, cy, r * (0.05 + 0.14 * cv), cv, true)
        }
    }

    private func nebPass(_ c: inout GraphicsContext, _ front: Bool, _ r: CGFloat, _ cv: CGFloat) {
        let p = pal
        for b in neb {
            let wx = b.x + sin(t * 0.31 + b.ph) * 0.035
            let wy = b.y + cos(t * 0.27 + b.ph * 1.3) * 0.035
            let wz = b.z + sin(t * 0.23 + b.ph * 0.7) * 0.035
            let q = xf.pr(wx, wy, wz)
            if (q.z >= 0) != front { continue }
            let dd = (q.z + 1) / 2
            let sz = b.s * r * (0.7 + 0.5 * dd) * (1 - cv * 0.5)
            let rot = b.rot + spinA * 0.25
            if b.dark {
                glowE(&c, p.ink, q.x, q.y, sz, b.el, rot, b.al * (0.55 + 0.45 * dd) * (1 - cv), false)
            } else {
                let lift = 1 + pulse * 0.6 + rubP * 0.5 + cv * 1.4
                glowE(&c, tint(p.fog[b.c]), q.x, q.y, sz, b.el, rot, b.al * (0.5 + 0.65 * dd) * lift, true)
            }
        }
    }

    /// 별밭 — 빨리 돌면 0.022초 노출 동안 움직인 만큼 잔상으로 늘어진다(길이는 반지름의 0.2배까지).
    private func starPass(_ c: inout GraphicsContext, _ front: Bool, _ r: CGFloat, _ cv: CGFloat) {
        let om = omega * motion
        let k = r / 84
        let st = sin(tiltNow)
        let col = tint(pal.star)
        let colW = col.mix(.white, 0.5)
        let cap = r * 0.2
        for i in 0..<stars.count {
            let q = starQ[i]
            if (q.z >= 0) != front { continue }
            let s = stars[i]
            let dd = (q.z + 1) / 2
            let tw = 0.55 + 0.45 * sin(t * s.f + s.ph)
            let al = (0.2 + 0.8 * dd) * tw * (1 - cv * 0.4)
            let sz = s.s * (0.55 + 0.75 * dd) * k
            var vx = om * q.v * r * 0.022
            var vy = -om * q.u * st * r * 0.022
            let vl = (vx * vx + vy * vy).squareRoot()
            if vl > cap {
                vx *= cap / vl
                vy *= cap / vl
            }
            if vx * vx + vy * vy > 2 {
                let aa = al * 0.85 * fade
                if aa > 0.002 {
                    var l = Path()
                    l.move(to: CGPoint(x: q.x - vx, y: q.y - vy))
                    l.addLine(to: CGPoint(x: q.x, y: q.y))
                    c.blendMode = .plusLighter
                    c.stroke(l, with: col.shade(aa), style: StrokeStyle(lineWidth: max(0.5, sz * 0.9), lineCap: .round))
                }
            } else if s.big {
                glow(&c, col, q.x, q.y, sz * 3.2, al * 0.45, true)
                sparkle(&c, q.x, q.y, sz * (1.6 + tw), al, .white, true)
            } else {
                dot(&c, colW, q.x, q.y, sz, al)
            }
        }
    }

    /// 은하판의 한 점 — 판을 회전축에 비스듬히(.3 · .42rad) 눕혀 구슬이 돌 때 납작했다 둥글었다 한다.
    private func gp(_ x: CGFloat, _ y: CGFloat, _ z: CGFloat) -> OrbQ {
        let x1 = x * gbC - y * gbS
        let y1 = x * gbS + y * gbC
        return xf.pr(x1, y1 * gaC - z * gaS, y1 * gaS + z * gaC)
    }

    /// 나선 은하 — 판 빛(판 방향 그대로의 타원) + 별 420개(안쪽일수록 빨리 돈다) + 밝은 핵.
    private func galaxy(_ c: inout GraphicsContext, _ r: CGFloat, _ cv: CGFloat) {
        let p = pal
        let k = r / 84
        if galColVer != palVer {
            for i in 0..<gal.count {
                let rr = gal[i].rr
                galCol[i] = rr < 0.22 ? p.gal[0].mix(p.gal[1], rr / 0.22) : p.gal[1].mix(p.gal[2], c01((rr - 0.22) / 0.5))
            }
            galColVer = palVer
        }
        let o = gp(0, 0, 0)
        let ax = gp(0.62, 0, 0)
        let vz = gp(0, 0, 0.62)
        let mt = CGAffineTransform(a: ax.x - o.x, b: ax.y - o.y, c: vz.x - o.x, d: vz.y - o.y, tx: o.x, ty: o.y)
        if abs(mt.a * mt.d - mt.b * mt.c) > 1e-4 {
            var g = c
            g.concatenate(mt)
            glow(&g, tint(p.gal[1]), 0, 0, 1, 0.2, true)
            glow(&g, tint(p.gal[2]), 0, 0, 1.25, 0.1, true)
        }
        for i in 0..<gal.count {
            let s = gal[i]
            let a = s.a + t * s.w
            let q = gp(cos(a) * s.rr + s.ox, s.oy, sin(a) * s.rr + s.oz)
            let dd = (q.z + 1) / 2
            let col = innerGo ? galCol[i].mix(rc, innerCv) : galCol[i]
            let al = (0.32 + 0.45 * dd) * (0.7 + 0.3 * sin(t * 2 + s.ph)) * (1 - cv * 0.3)
            dot(&c, col, q.x, q.y, s.s * k * (0.7 + 0.5 * dd), al)
        }
        glow(&c, tint(p.gal[0]), o.x, o.y, r * 0.2, 0.5, true)
        glow(&c, .white, o.x, o.y, r * 0.055, 0.75, true)
    }

    /// 유리 앞면 — 프레넬 테 · 바닥 집광(타원 + 호) · 창 반사 초승달 · 부드러운 반사광 · 오른쪽 아래 가는 되비침 · 점 반짝임 · 가는 테.
    /// 구슬 가운데 기준으로 그리고 두드림 출렁임(r / orbR)만큼 늘린다.
    private func glassFront(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let p = pal
        let rr = orbR
        let sc = r / rr
        var g = c
        g.translateBy(x: cx, y: cy)
        g.scaleBy(x: sc, y: sc)
        g.blendMode = .plusLighter
        let fresnel: [Gradient.Stop] = [
            .init(color: p.rim.color(0), location: 0),
            .init(color: p.rim.color(0), location: 0.78),
            .init(color: p.rim.color(0.1), location: 0.954),
            .init(color: p.rim.color(0.38), location: 0.991),
            .init(color: p.rim.color(0.12), location: 1),
        ]
        g.fill(circlePath(0, 0, rr), with: .radialGradient(Gradient(stops: fresnel), center: .zero, startRadius: 0, endRadius: rr))
        // 바닥 집광 — 아래 은하수 빛이 유리 바닥에 모인다
        var cg = g
        cg.translateBy(x: 0, y: rr * 0.66)
        cg.scaleBy(x: 1, y: 0.42)
        let caustic: [Gradient.Stop] = [
            .init(color: p.caustic.color(0.45), location: 0),
            .init(color: p.caustic.color(0.15), location: 0.5),
            .init(color: p.caustic.color(0), location: 1),
        ]
        cg.fill(circlePath(0, 0, rr * 0.62), with: .radialGradient(Gradient(stops: caustic), center: .zero, startRadius: 0, endRadius: rr * 0.62))
        let arcStops: [Gradient.Stop] = [
            .init(color: p.caustic.color(0), location: 0),
            .init(color: p.caustic.color(0.5), location: 0.5),
            .init(color: p.caustic.color(0), location: 1),
        ]
        g.stroke(
            arcPath(0, 0, rr * 0.91, rr * 0.91, 0.24 * .pi, 0.76 * .pi),
            with: .linearGradient(Gradient(stops: arcStops), startPoint: CGPoint(x: -rr * 0.7, y: 0), endPoint: CGPoint(x: rr * 0.7, y: 0)),
            style: StrokeStyle(lineWidth: rr * 0.022, lineCap: .round)
        )
        // 창 반사 — 왼쪽 위 가장자리를 따라 휜 초승달
        let crescent: [Gradient.Stop] = [
            .init(color: OrbRGB.white.color(0), location: 0),
            .init(color: OrbRGB.white.color(0.55), location: 0.42),
            .init(color: OrbRGB.white.color(0.2), location: 0.75),
            .init(color: OrbRGB.white.color(0), location: 1),
        ]
        g.fill(Self.crescent, with: .linearGradient(Gradient(stops: crescent), startPoint: CGPoint(x: -rr * 0.95, y: rr * 0.1),
                                                     endPoint: CGPoint(x: -rr * 0.05, y: -rr * 0.95)))
        // 부드러운 반사광(넓게 번진 하늘빛)
        var soft = g
        soft.translateBy(x: -rr * 0.3, y: -rr * 0.38)
        soft.rotate(by: .radians(-0.7))
        soft.scaleBy(x: 1, y: 0.6)
        let softStops: [Gradient.Stop] = [
            .init(color: OrbRGB.white.color(0.16), location: 0),
            .init(color: OrbRGB.white.color(0.04), location: 0.6),
            .init(color: OrbRGB.white.color(0), location: 1),
        ]
        soft.fill(circlePath(0, 0, rr * 0.4), with: .radialGradient(Gradient(stops: softStops), center: .zero, startRadius: 0, endRadius: rr * 0.4))
        // 오른쪽 아래 가는 되비침
        g.blendMode = .plusLighter
        g.stroke(arcPath(0, 0, rr * 0.89, rr * 0.89, 0.06 * .pi, 0.2 * .pi), with: p.rim.mix(.white, 0.5).shade(0.3),
                 style: StrokeStyle(lineWidth: rr * 0.014, lineCap: .round))
        glow(&g, .white, -rr * 0.52, -rr * 0.71, rr * 0.07, 0.8, true)
        sparkle(&g, -rr * 0.52, -rr * 0.71, rr * 0.06, 0.9, .white, true)
        g.blendMode = .normal
        g.stroke(circlePath(0, 0, rr), with: p.rim.shade(0.35), lineWidth: 1)
    }

    // MARK: 문지르기 고리 · 불꽃

    private func drawRub(_ c: inout GraphicsContext) {
        let p = pal
        if rubP > 0.01 {
            let tc = (rare ? OrbPalette.normal : OrbPalette.rare).line
            let rr = ringR
            let a0 = -CGFloat.pi / 2
            let a1 = a0 + rubP * orbTau
            c.blendMode = .plusLighter
            c.stroke(circlePath(bx, by, rr), with: tc.shade(0.09), lineWidth: 0.8)
            let arc = arcPath(bx, by, rr, rr, a0, a1)
            c.stroke(arc, with: tc.shade(0.22), style: StrokeStyle(lineWidth: 3.4, lineCap: .round))
            c.stroke(arc, with: tc.mix(.white, 0.25).shade(0.95), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            let hx = bx + cos(a1) * rr
            let hy = by + sin(a1) * rr
            glow(&c, tc, hx, hy, 10, 0.85, true)
            sparkle(&c, hx, hy, 4.6, 1, .white, true)
        }
        for s in rubSparks {
            let age = t - s.born
            let a = 1 - age / s.life
            if a <= 0 || age < 0 { continue }
            let x = s.x + s.vx * age
            let y = s.y + s.vy * age + 50 * age * age
            sparkle(&c, x, y, s.s * 2 * (0.5 + 0.5 * a), a, p.star, true)
        }
        if flash > 0 {
            let f = flash
            glow(&c, .white, bx, by, orbR * (1.1 + (1 - f) * 1.4), f * 0.65, true)
            glow(&c, p.glow, bx, by, orbR * (1.8 + (1 - f) * 2.2), f * 0.5, true)
        }
    }

    // MARK: 금

    /// 금 위 s 거리의 점. 금이 거기까지 안 가면 nil.
    private func crackPoint(_ ray: OrbRay, _ s: CGFloat) -> CGPoint? {
        if s > ray.len || ray.n < 2 { return nil }
        for j in 1..<ray.n where ray.cum[j] >= s {
            let u = (s - ray.cum[j - 1]) / (ray.cum[j] - ray.cum[j - 1])
            return CGPoint(x: ray.xs[j - 1] + (ray.xs[j] - ray.xs[j - 1]) * u, y: ray.ys[j - 1] + (ray.ys[j] - ray.ys[j - 1]) * u)
        }
        return nil
    }

    /// 곧게 뻗는 방사 금 8~12(가끔 꺾이고 Y 로 갈라진다) + 그 사이를 곧은 선으로 잇는 다각형 거미줄 + 부서진 점 + 잔금.
    private func makeImpact(_ x: CGFloat, _ y: CGFloat, _ target: CGFloat) -> OrbImpact {
        let r = rnd
        var rays: [OrbRay] = []
        var mains: [OrbRay] = []
        let n = 8 + min(Int(r.f() * 5), 4)
        let base = r.f() * orbTau
        // 마디마다 빛을 받는 정도(0 어둑 · 1 보통 · 2 번쩍) — 몇 마디씩 이어지게 바꾼다
        func grow(_ sx: CGFloat, _ sy: CGFloat, _ ang: CGFloat, _ start: CGFloat, _ maxLen: CGFloat, _ wob: CGFloat) -> OrbRay {
            var xs: [CGFloat] = [sx]
            var ys: [CGFloat] = [sy]
            var cum: [CGFloat] = [0]
            var lum: [Int] = []
            var px = sx
            var py = sy
            var a = ang
            var len: CGFloat = 0
            var lum0 = min(Int(r.f() * 3), 2)
            while len < maxLen {
                let step = 0.05 + 0.09 * r.f()
                a += (r.f() - 0.5) * wob
                if r.f() < 0.12 { a += (r.f() - 0.5) * 0.5 }
                px += cos(a) * step
                py += sin(a) * step
                len += step
                if r.f() < 0.32 { lum0 = min(Int(r.f() * 3), 2) }
                xs.append(px)
                ys.append(py)
                cum.append(len)
                lum.append(lum0)
                if px * px + py * py > 1.02 { break }
            }
            return OrbRay(xs: xs, ys: ys, cum: cum, lum: lum, start: start, len: len)
        }
        for i in 0..<n {
            let cell = orbTau / CGFloat(n)
            let ang = base + CGFloat(i) * cell + (r.f() - 0.5) * cell * 0.6
            let ray = grow(x, y, ang, 0, 0.5 + r.f() * 1.6, 0.1)
            rays.append(ray)
            mains.append(ray)
            let forks = r.f() < 0.55 ? 1 + (r.f() < 0.3 ? 1 : 0) : 0
            for _ in 0..<forks {
                if ray.n <= 4 { break }
                let kk = 2 + min(Int(r.f() * CGFloat(ray.n - 3)), ray.n - 4)
                let side: CGFloat = r.f() < 0.5 ? -1 : 1
                let fork = ang + side * (0.3 + r.f() * 0.45)
                rays.append(grow(ray.xs[kk], ray.ys[kk], fork, ray.cum[kk], 0.15 + r.f() * 0.55, 0.12))
            }
        }
        var rings: [OrbRingSeg] = []
        let depths: [CGFloat] = [0.05, 0.11, 0.19, 0.3, 0.45]
        for d in depths {
            if r.f() < 0.2 { continue }
            for i in 0..<n {
                if r.f() < 0.38 { continue }
                guard let pa = crackPoint(mains[i], d * (0.8 + 0.4 * r.f())) else { continue }
                guard let pb = crackPoint(mains[(i + 1) % n], d * (0.8 + 0.4 * r.f())) else { continue }
                let mx = (pa.x + pb.x) / 2 + (r.f() - 0.5) * 0.03
                let my = (pa.y + pb.y) / 2 + (r.f() - 0.5) * 0.03
                rings.append(OrbRingSeg(ax: pa.x, ay: pa.y, mx: mx, my: my, bx: pb.x, by: pb.y, d: d))
            }
        }
        let nm = 14 + min(Int(r.f() * 8), 7)
        var micro = [CGFloat](repeating: 0, count: nm * 4)
        for i in 0..<nm {
            let a = r.f() * orbTau
            let l = 0.015 + r.f() * 0.05
            micro[i * 4] = x + cos(a) * 0.008
            micro[i * 4 + 1] = y + sin(a) * 0.008
            micro[i * 4 + 2] = x + cos(a) * l
            micro[i * 4 + 3] = y + sin(a) * l
        }
        let cr = 0.022 + r.f() * 0.018
        var crush = [CGFloat](repeating: 0, count: 14)
        for i in 0..<7 {
            let a = CGFloat(i) / 7 * orbTau + (r.f() - 0.5) * 0.6
            let rr = cr * (0.6 + 0.6 * r.f())
            crush[i * 2] = x + cos(a) * rr
            crush[i * 2 + 1] = y + sin(a) * rr
        }
        let glints = (0..<3).map { _ -> OrbGlint in
            let ray = min(Int(r.f() * CGFloat(mains.count)), mains.count - 1)
            let v = 0.35 + r.f() * 0.5
            return OrbGlint(ray: ray, v: v, ph: r.f())
        }
        return OrbImpact(x: x, y: y, rays: rays, rings: rings, micro: micro, crush: crush, glints: glints, target: target)
    }

    private func spawnFlakes(_ x: CGFloat, _ y: CGFloat, _ n: Int) {
        let r = rnd
        for _ in 0..<n {
            let a = r.f() * orbTau
            let sp = 40 + r.f() * 110
            let s = 1.2 + r.f() * 2.4
            var tri = [CGFloat](repeating: 0, count: 6)
            for j in 0...2 {
                let q = CGFloat(j) / 3 * orbTau + (r.f() - 0.5) * 1.2
                tri[j * 2] = cos(q) * s
                tri[j * 2 + 1] = sin(q) * s * (0.5 + r.f() * 0.5)
            }
            let vz = 0.4 + r.f() * 1.6
            let life = 0.5 + r.f() * 0.45
            let rot = r.f() * orbTau
            let vr = (r.f() - 0.5) * 20
            flakes.append(OrbFlake(x: bx + x * orbR, y: by + y * orbR, vx: cos(a) * sp, vy: sin(a) * sp - 60, vz: vz, born: t,
                                   life: life, rot: rot, vr: vr, tri: tri))
        }
    }

    /// 두드릴 때 튀는 작은 유리 조각(앞으로 다가오며 뒤집힌다).
    private func drawFlakes(_ c: inout GraphicsContext) {
        if flakes.isEmpty { return }
        let fillC = pal.rim.mix(.white, 0.5)
        for f in flakes {
            let age = t - f.born
            let a = 1 - age / f.life
            if a <= 0 { continue }
            let sc = 1 + f.vz * age
            let sx = sc * cos(age * 9 + f.rot)
            if abs(sx) < 1e-4 { continue }
            var g = c
            g.translateBy(x: f.x + f.vx * age, y: f.y + f.vy * age + 0.5 * grav * age * age)
            g.rotate(by: .radians(Double(f.rot + f.vr * age)))
            g.scaleBy(x: sx, y: sc)
            var p = Path()
            p.move(to: CGPoint(x: f.tri[0], y: f.tri[1]))
            p.addLine(to: CGPoint(x: f.tri[2], y: f.tri[3]))
            p.addLine(to: CGPoint(x: f.tri[4], y: f.tri[5]))
            p.closeSubpath()
            g.blendMode = .plusLighter
            g.fill(p, with: fillC.shade(0.45 * a))
            g.stroke(p, with: OrbRGB.white.shade(0.8 * a), lineWidth: 0.5)
        }
    }

    /// 금 — 마디를 밝기별 세 갈래 길로 나눠 그린다(같은 금도 어떤 마디는 번쩍이고 어떤 마디는 거의 안 보인다, 진짜 유리처럼).
    /// 어두운 그림자 → 번짐 → 밝은 심 → 거미줄 → 비켜 난 반사 → 잔금 → 부서진 점 · 반짝 지나가는 빛.
    private func drawCracks(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) {
        let p = pal
        let go = phase == .converge || phase == .wait
        let cv = conv
        let col = phase == .converge ? p.rim.mix(rc, cv) : p.rim
        var g = c
        g.clip(to: circlePath(cx, cy, r * 0.995))
        var lit = [Path(), Path(), Path()]
        var ringsP = Path()
        var microP = Path()
        for im in impacts {
            let gg = im.g
            if gg <= 0.001 { continue }
            for ray in im.rays {
                let vis = gg - ray.start
                if vis <= 0 || ray.n < 2 { continue }
                for j in 1..<ray.n {
                    let c0 = ray.cum[j - 1]
                    if c0 >= vis { break }
                    var x1 = ray.xs[j]
                    var y1 = ray.ys[j]
                    if ray.cum[j] > vis {
                        let u = (vis - c0) / (ray.cum[j] - c0)
                        x1 = ray.xs[j - 1] + (x1 - ray.xs[j - 1]) * u
                        y1 = ray.ys[j - 1] + (y1 - ray.ys[j - 1]) * u
                    }
                    let li = ray.lum[j - 1]
                    lit[li].move(to: CGPoint(x: cx + ray.xs[j - 1] * r, y: cy + ray.ys[j - 1] * r))
                    lit[li].addLine(to: CGPoint(x: cx + x1 * r, y: cy + y1 * r))
                }
            }
            for s in im.rings {
                if gg < s.d * 1.25 + 0.05 { continue }
                ringsP.move(to: CGPoint(x: cx + s.ax * r, y: cy + s.ay * r))
                ringsP.addLine(to: CGPoint(x: cx + s.mx * r, y: cy + s.my * r))
                ringsP.addLine(to: CGPoint(x: cx + s.bx * r, y: cy + s.by * r))
            }
            let mk = min(1, gg * 6)
            let m = im.micro
            for q in 0..<(m.count / 4) {
                let x0 = m[q * 4]
                let y0 = m[q * 4 + 1]
                microP.move(to: CGPoint(x: cx + x0 * r, y: cy + y0 * r))
                microP.addLine(to: CGPoint(x: cx + (x0 + (m[q * 4 + 2] - x0) * mk) * r, y: cy + (y0 + (m[q * 4 + 3] - y0) * mk) * r))
            }
        }
        let ga: CGFloat = go ? 0.45 + 0.55 * cv : 0.3 + pulse * 0.3
        let light = col.mix(.white, 0.7)
        let shadow = StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round)
        g.blendMode = .normal
        for pa in lit { g.stroke(pa, with: OrbRGB.black.shade(0.3), style: shadow) }
        g.stroke(ringsP, with: OrbRGB.black.shade(0.3), style: shadow)
        g.blendMode = .plusLighter
        let wide = StrokeStyle(lineWidth: 2.3, lineCap: .round, lineJoin: .round)
        g.stroke(lit[1], with: col.shade(ga * 0.2), style: wide)
        g.stroke(lit[2], with: col.shade(ga * 0.2), style: wide)
        for i in 0...2 {
            let core = StrokeStyle(lineWidth: 0.46 + CGFloat(i) * 0.1, lineCap: .round, lineJoin: .round)
            g.stroke(lit[i], with: light.shade((0.55 + ga * 0.45) * crackLum[i]), style: core)
        }
        let fine = StrokeStyle(lineWidth: 0.4, lineCap: .round, lineJoin: .round)
        g.stroke(ringsP, with: light.shade(0.25 + ga * 0.3), style: fine)
        var off = g
        off.translateBy(x: 0.7, y: 0.55)
        off.stroke(lit[2], with: OrbRGB.white.shade(0.14), style: fine)
        g.stroke(microP, with: OrbRGB.white.shade(0.5), style: fine)
        for (n, im) in impacts.enumerated() {
            let kk = min(1, im.g * 5)
            let ix = cx + im.x * r
            let iy = cy + im.y * r
            var path = Path()
            for q in 0..<7 {
                let pnt = CGPoint(x: cx + im.crush[q * 2] * r, y: cy + im.crush[q * 2 + 1] * r)
                if q == 0 {
                    path.move(to: pnt)
                } else {
                    path.addLine(to: pnt)
                }
            }
            path.closeSubpath()
            // 시안의 합성 상태 그대로 — 첫 부서진 점만 더하기(뒤는 빛을 그린 뒤라 보통 합성)
            g.blendMode = n == 0 ? .plusLighter : .normal
            g.fill(path, with: OrbRGB.white.shade(0.24 * kk))
            glow(&g, col, ix, iy, r * 0.16 * kk, 0.3 + ga * 0.4, true)
            glow(&g, .white, ix, iy, r * 0.05 * kk, 0.9 * kk, true)
            for gl in im.glints {
                let ray = im.rays[gl.ray]
                let vis = min(im.g - ray.start, ray.len)
                if vis <= 0.05 { continue }
                let u = (t * gl.v + gl.ph).truncatingRemainder(dividingBy: 1)
                if let cp = crackPoint(ray, u * vis) {
                    let su = sin(u * .pi)
                    sparkle(&g, cx + cp.x * r, cy + cp.y * r, 2.8 * su + 0.5, 0.9 * su, .white, true)
                }
            }
        }
    }

    /// 모이는 동안 금 사이로 새어 나오는 빛줄기.
    private func drawLeaks(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat) {
        let cv = conv
        let light = rc.mix(.white, 0.6)
        for (i, lk) in leaks.enumerated() {
            let x = cx + cos(lk.a) * orbR * 0.97
            let y = cy + sin(lk.a) * orbR * 0.97
            let len = orbR * (0.3 + 1.5 * cv) * lk.l * (0.85 + 0.15 * sin(t * 23 + CGFloat(i) * 1.7))
            wedge(&c, x, y, lk.a, len, lk.w * 1.8, rc.color(0.26 * cv), rc.color(0))
            wedge(&c, x, y, lk.a, len * 0.8, lk.w * 0.45, light.color(0.9 * cv), light.color(0))
            glow(&c, light, x, y, orbR * 0.13, 0.55 * cv, true)
        }
    }

    // MARK: 깨짐

    /// 볼록 다각형([x0,y0,x1,y1,…])을 반평면 n·(p - m) ≤ 0 으로 자른다(모두 안쪽이면 그대로 돌려준다).
    private func clipHalf(_ poly: [CGFloat], _ nx: CGFloat, _ ny: CGFloat, _ mx: CGFloat, _ my: CGFloat) -> [CGFloat] {
        let n = poly.count / 2
        var cut = false
        for i in 0..<n where (poly[i * 2] - mx) * nx + (poly[i * 2 + 1] - my) * ny > 0 {
            cut = true
            break
        }
        if !cut { return poly }
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

    private func polyArea(_ p: [CGFloat]) -> CGFloat {
        let n = p.count / 2
        var s: CGFloat = 0
        for i in 0..<n {
            let j = (i + 1) % n
            s += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return abs(s) / 2
    }

    /// 누른 자리 둘레로 씨앗을 여덟 겹 동심원(바깥일수록 성기게) + 가장자리에 뿌려 보로노이로 자른다. 큰 칸은 한 번 더 쪼갠다.
    private func shardCells(_ ix: CGFloat, _ iy: CGFloat) -> [[CGFloat]] {
        let r = rnd
        var seeds: [CGPoint] = [CGPoint(x: ix, y: iy)]
        for k in 0..<8 {
            let rad = 0.045 * pow(1.47, CGFloat(k))
            let n = 5 + 4 * k
            let off = r.f() * orbTau
            let cell = orbTau / CGFloat(n)
            for i in 0..<n {
                let a = off + CGFloat(i) * cell + (r.f() - 0.5) * cell * 0.75
                let rr = rad * (0.72 + 0.56 * r.f())
                let sx = ix + cos(a) * rr
                let sy = iy + sin(a) * rr
                if sx * sx + sy * sy < 1.5 { seeds.append(CGPoint(x: sx, y: sy)) }
            }
        }
        for i in 0..<14 {
            let a = CGFloat(i) / 14 * orbTau + r.f() * 0.3
            let rr = 0.9 + r.f() * 0.15
            let sx = cos(a) * rr
            let sy = sin(a) * rr
            if hyp(sx - ix, sy - iy) > 0.35 { seeds.append(CGPoint(x: sx, y: sy)) }
        }
        var disk = [CGFloat](repeating: 0, count: 112)
        for i in 0..<56 {
            let a = CGFloat(i) / 56 * orbTau
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
            if polyArea(poly) > 0.018 && r.f() < 0.75 {
                var mx: CGFloat = 0
                var my: CGFloat = 0
                let n = poly.count / 2
                for i in 0..<n {
                    mx += poly[i * 2]
                    my += poly[i * 2 + 1]
                }
                mx /= CGFloat(n)
                my /= CGFloat(n)
                let ang = r.f() * .pi
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

    /// 깨지기 직전 구슬을 한 장 찍어 조각마다 입힌다 + 유리 가루 · 터져 나오는 별줄기 · 성운 덩이.
    @MainActor
    private func buildShards() {
        needShards = false
        let rr = orbR
        let pad: CGFloat = 3
        let ox = bx - rr - pad
        let oy = by - rr - pad
        let side = (rr + pad) * 2
        let scale = min(max(lastK * pxScale, 1), 3)
        shardImage = snapshot(ox: ox, oy: oy, side: side, scale: scale)
        shardOrigin = CGPoint(x: ox, y: oy)
        let last = impacts.last
        let ix = last?.x ?? 0
        let iy = last?.y ?? 0
        let r = rnd
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
                let pnt = CGPoint(x: (poly[i * 2] - ux) * rr, y: (poly[i * 2 + 1] - uy) * rr)
                if i == 0 {
                    sp.move(to: pnt)
                } else {
                    sp.addLine(to: pnt)
                }
            }
            sp.closeSubpath()
            var dx = ux - ix
            var dy = uy - iy
            let dRaw = hyp(dx, dy)
            let dist = dRaw == 0 ? 1 : dRaw
            dx /= dist
            dy /= dist
            let cRaw = hyp(ux, uy)
            let cl = cRaw == 0 ? 1 : cRaw
            var vx = dx * 0.7 + ux / cl * 0.3
            var vy = dy * 0.7 + uy / cl * 0.3
            let vRaw = hyp(vx, vy)
            let vl = vRaw == 0 ? 1 : vRaw
            vx /= vl
            vy /= vl
            let speed = rr * (1.3 + 2.1 * r.f()) * (1.3 - 0.5 * min(1, dist))
            let toCam = r.f() < 0.38
            let vz = toCam ? rr * (1.2 + 2.2 * r.f()) : rr * (r.f() - 0.5) * 0.5
            let vr = (r.f() - 0.5) * 8
            let vt = (r.f() - 0.5) * 14
            let vq: CGFloat = r.f() < 0.4 ? (r.f() - 0.5) * 9 : 0
            let gp = r.f() * orbTau
            let life = 1 + r.f() * 0.75
            list.append(OrbShard(path: sp, x0: cx + ux * rr, y0: cy + uy * rr, vx: vx * speed, vy: vy * speed - rr * 0.6, vz: vz,
                                 vr: vr, vt: vt, vq: vq, gp: gp, life: life, delay: dist * 0.05))
        }
        shards = list
        grit = (0..<200).map { _ -> OrbGrit in
            let a = r.f() * orbTau
            let sp = rr * (0.8 + 2.8 * r.f())
            let x0 = cx + (ix + (r.f() - 0.5) * 0.8) * rr
            let y0 = cy + (iy + (r.f() - 0.5) * 0.8) * rr
            let s = 0.5 + r.f() * 1.3
            let life = 0.5 + r.f() * 0.9
            return OrbGrit(x0: x0, y0: y0, vx: cos(a) * sp, vy: sin(a) * sp - rr * 0.5, s: s, life: life, ph: r.f() * orbTau)
        }
        let p = pal
        let burst = rc
        let cols = [p.star, p.fog[0], p.fog[1], p.fog[2], p.gal[0], burst, burst]
        escape = (0..<160).map { _ -> OrbEscape in
            let a = r.f() * orbTau
            let r0 = rr * (0.04 + 0.5 * r.f().squareRoot())
            let v = rr * (2.4 + 6 * r.f())
            let w = 0.5 + r.f() * 1.2
            let life = 0.55 + r.f() * 0.6
            return OrbEscape(a: a, r0: r0, v: v, w: w, life: life, c: cols[min(Int(r.f() * CGFloat(cols.count)), cols.count - 1)])
        }
        puffs = (0..<12).map { i -> OrbPuff in
            let a = r.f() * orbTau
            let d = rr * (0.1 + 0.4 * r.f())
            let c = i % 3 == 0 ? burst : p.fog[min(Int(r.f() * 5), 4)]
            return OrbPuff(a: a, d: d, c: c, s0: rr * (0.3 + 0.3 * r.f()))
        }
    }

    /// 구슬만 따로 그린 사진(무대 단위 side × side, 왼쪽 위 = (ox, oy)).
    @MainActor
    private func snapshot(ox: CGFloat, oy: CGFloat, side: CGFloat, scale: CGFloat) -> Image? {
        let cx = bx
        let cy = by
        fade = 1
        let content = Canvas { ctx, _ in
            ctx.translateBy(x: -ox, y: -oy)
            self.drawBall(&ctx, cx, cy, snap: true)
        }
        .frame(width: side, height: side)
        let renderer = ImageRenderer(content: content)
        renderer.scale = scale
        guard let cg = renderer.cgImage else { return nil }
        return Image(decorative: cg, scale: scale)
    }

    private func drawShards(_ c: inout GraphicsContext, _ s: CGFloat) {
        let edge = rc.mix(.white, 0.5)
        let photo = shardImage.map { c.resolve($0) }
        for sh in shards {
            let u = max(0, s - sh.delay)
            let dr = (1 - exp(-2.1 * u)) / 2.1
            let z = sh.vz * dr
            let ps = min(3.2, 1 / max(0.3, 1 - z / (orbR * 5)))
            var a: CGFloat = u < sh.life * 0.55 ? 1 : c01(1 - (u - sh.life * 0.55) / (sh.life * 0.45))
            if ps > 1.8 { a *= c01(1 - (ps - 1.8) / 1.2) }
            if a <= 0 { continue }
            let x = sh.x0 + sh.vx * dr
            let y = sh.y0 + sh.vy * dr + 0.5 * grav * u * u
            let px = bx + (x - bx) * ps
            let py = by + (y - by) * ps
            let ph = sh.vt * u
            let cs = cos(ph)
            let sx: CGFloat = (cs < 0 ? -1 : 1) * max(0.06, abs(cs))
            let cq = cos(sh.vq * u)
            let sy: CGFloat = (cq < 0 ? -1 : 1) * max(0.1, abs(cq))
            var g = c
            g.translateBy(x: px, y: py)
            g.rotate(by: .radians(Double(sh.vr * u)))
            g.scaleBy(x: sx * ps, y: sy * ps)
            if let photo {
                var l = g
                l.clip(to: sh.path)
                l.opacity = Double(a)
                l.blendMode = .normal
                l.draw(photo, at: CGPoint(x: shardOrigin.x - sh.x0, y: shardOrigin.y - sh.y0), anchor: .topLeading)
            }
            let gl = pow(abs(sin(ph * 0.5 + sh.gp)), 14)
            g.blendMode = .plusLighter
            if gl > 0.03 { g.fill(sh.path, with: OrbRGB.white.shade(gl * 0.6 * a)) }
            g.fill(sh.path, with: rc.shade(0.14 * a * (1 - c01(u / 0.6))))
            g.stroke(sh.path, with: edge.shade((0.35 + gl * 0.6) * a), style: StrokeStyle(lineWidth: 0.8 / ps, lineJoin: .round))
        }
        let dr = (1 - exp(-2.6 * s)) / 2.6
        for d in grit {
            let a = c01(1 - s / d.life)
            if a <= 0 { continue }
            let tw = 0.5 + 0.5 * sin(s * 30 + d.ph)
            dot(&c, edge, d.x0 + d.vx * dr, d.y0 + d.vy * dr + 0.5 * grav * s * s, d.s, a * (0.4 + 0.6 * tw))
        }
    }

    /// 속의 우주가 터져 나온다 — 바깥으로 뻗는 별줄기(워프) + 커지며 흩어지는 성운.
    private func drawEscape(_ c: inout GraphicsContext, _ s: CGFloat) {
        let cx = bx
        let cy = by
        let u = c01(s / 1.3)
        for pf in puffs {
            let d = pf.d * (1 + 2.4 * outC(u))
            glow(&c, pf.c, cx + cos(pf.a) * d, cy + sin(pf.a) * d, pf.s0 * (1 + 2.6 * outC(u)), 0.32 * (1 - u) * (1 - u), true)
        }
        c.blendMode = .plusLighter
        for e in escape {
            let eu = s / e.life
            if eu >= 1 { continue }
            let head = e.r0 + e.v * s * (1 - 0.3 * s)
            let tail = max(e.r0, head - e.v * 0.1)
            let ca = cos(e.a)
            let sa = sin(e.a)
            var l = Path()
            l.move(to: CGPoint(x: cx + ca * tail, y: cy + sa * tail))
            l.addLine(to: CGPoint(x: cx + ca * head, y: cy + sa * head))
            c.stroke(l, with: e.c.shade(pow(1 - eu, 1.4)), style: StrokeStyle(lineWidth: e.w * (1 - 0.5 * eu), lineCap: .round))
        }
    }

    /// 영웅·전설 — 마법진에서 하늘로 치솟는 빛기둥.
    private func drawPillar(_ c: inout GraphicsContext, _ s: CGFloat) {
        if lv < 2 { return }
        let u = c01(s / 1.3)
        let a: CGFloat = (lv == 3 ? 0.75 : 0.5) * (1 - u) * (1 - u)
        if a <= 0 { return }
        let w = orbR * (0.36 * (1 - u) + 0.05)
        let stops: [Gradient.Stop] = [
            .init(color: rc.color(0), location: 0),
            .init(color: rc.color(a * 0.22), location: 0.3),
            .init(color: rc.mix(.white, 0.8).color(a), location: 0.5),
            .init(color: rc.color(a * 0.22), location: 0.7),
            .init(color: rc.color(0), location: 1),
        ]
        c.blendMode = .plusLighter
        c.fill(Path(CGRect(x: stageCX - w, y: viewTop, width: w * 2, height: stageGY - viewTop)),
               with: .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: stageCX - w, y: 0), endPoint: CGPoint(x: stageCX + w, y: 0)))
    }

    /// 깨지는 순간 — 번쩍임(영웅·전설: 가운데가 하얗고 빨리 빠지는 큰 빛 두 겹) · 번짐 · 가로 섬광(희귀↑) · 빛줄기 0/8/14/22 · 충격파.
    private func rarityBurst(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ s: CGFloat) {
        let f = c01(s / shatDur)
        let e = outC(f)
        let a = 1 - f
        let l = CGFloat(lv)
        if lv >= 2 && flashOk {
            let q = c01(1 - s / 0.4)
            let fa: CGFloat = (lv == 3 ? 1 : 0.6) * q * q
            if fa > 0 {
                glow(&c, rc.mix(.white, 0.5), cx, cy, r * (2.6 + 2 * (1 - q)), fa * 0.75, true)
                glow(&c, .white, cx, cy, r * (1.5 + 2 * (1 - q)), fa, true)
            }
        }
        glow(&c, rc, cx, cy, r * (0.7 + 0.7 * l) * (0.45 + 0.55 * e), (0.55 + 0.12 * l) * a, true)
        glow(&c, .white, cx, cy, r * (0.35 + 0.25 * l) * (0.3 + 0.7 * e), pow(a, 1.5) * 0.9, true)
        if lv >= 1 {
            var flat = c
            flat.translateBy(x: cx, y: cy)
            flat.scaleBy(x: 1, y: 0.05 + 0.02 * l)
            glow(&flat, rc.mix(.white, 0.3), 0, 0, r * (1.4 + 1.1 * l) * (0.5 + 0.5 * e), a * (0.3 + 0.15 * l), true)
        }
        let nr = raysByLevel[min(max(lv, 0), 3)]
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
        c.blendMode = .plusLighter
        for kk in 0..<max(lv, 0) {
            let u = c01((s - CGFloat(kk) * 0.18) / 0.9)
            if u <= 0 || u >= 1 { continue }
            let rr = r * (0.4 + 2.4 * outC(u))
            let oval = Path(ellipseIn: CGRect(x: cx - rr, y: cy - rr * 0.92, width: rr * 2, height: rr * 1.84))
            c.stroke(oval, with: rc.shade((1 - u) * 0.14), lineWidth: 7 * (1 - u) + 1)
            c.stroke(oval, with: ring.shade((1 - u) * 0.6), lineWidth: (1.5 - CGFloat(kk) * 0.3) * (1 - u) + 0.4)
        }
    }

    /// 열린 뒤 남는 후광 — 번짐 · 도는 빛줄기(영웅 10 / 전설 16) · 전설은 고리. k 0..1.
    private func aura(_ c: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ k: CGFloat) {
        guard k > 0 else { return }
        let l = CGFloat(lv)
        glow(&c, rc, cx, cy, r * (0.7 + 0.35 * l), (0.25 + 0.1 * l) * k * (0.85 + 0.15 * sin(t * 2)), true)
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
            c.blendMode = .plusLighter
            c.stroke(circlePath(cx, cy, r * 0.62), with: rc.shade(0.35 * k), lineWidth: 1)
        }
    }

    /// 결과 문장 — 등급 빛 별(왼쪽 위에서 빛을 받는 입체 그라데이션)이 통 튀어나와 둥실, 둘레를 반짝이 다섯이 돈다.
    private func emblem(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ s: CGFloat, _ col: OrbRGB, _ alpha: CGFloat) {
        guard alpha > 0, s > 0.5 else { return }
        glow(&c, col, x, y, s * 3.4, 0.6 * alpha, true)
        glow(&c, .white, x, y, s * 1.2, 0.3 * alpha, true)
        let yy = y + sin(t * 1.6) * s * 0.06
        let rot = sin(t * 0.8) * 0.12
        var star = Path()
        for i in 0..<10 {
            let rr = i % 2 == 1 ? s * 0.47 : s
            let a = rot - CGFloat.pi / 2 + CGFloat(i) * CGFloat.pi / 5
            let pnt = CGPoint(x: x + cos(a) * rr, y: yy + sin(a) * rr)
            if i == 0 {
                star.move(to: pnt)
            } else {
                star.addLine(to: pnt)
            }
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
        g.opacity = Double(c01(alpha * fade))
        g.blendMode = .normal
        g.fill(star, with: shading)
        g.stroke(star, with: shading, style: StrokeStyle(lineWidth: s * 0.18, lineJoin: .round))
        for i in 0..<5 {
            let fi = CGFloat(i)
            let a = t * 0.6 + fi * orbTau / 5
            let rr = s * (1.9 + 0.25 * sin(t * 1.3 + fi))
            let size = s * (0.17 + 0.07 * sin(t * 3 + fi * 2))
            let sa = alpha * (0.55 + 0.45 * sin(t * 2.4 + fi))
            sparkle(&c, x + cos(a) * rr, yy + sin(a) * rr * 0.8, size, sa, .white, true)
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

    init(seed: UInt64, n: Int = 110) {
        let r = OrbRandom(seed)
        x = (0..<n).map { _ in r.f() }
        y = (0..<n).map { _ in r.f() }
        s = (0..<n).map { _ in 0.5 + r.f() * 1.1 }
        p = (0..<n).map { _ in r.f() * orbTau }
        w = (0..<n).map { _ in 0.6 + r.f() * 1.6 }
    }
}
