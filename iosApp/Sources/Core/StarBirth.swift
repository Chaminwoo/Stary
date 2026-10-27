import SwiftUI
import UIKit

/// 별 탄생 연출 — 다이어리 저장이 **성공한 순간** 별빛이 모이고(①) 혜성처럼 날아(②) 지도의 내 위치(화면 중앙)에 내려앉는다(③).
/// 2026-10-28 "하나의 우주" 개편: 예전 응축→발광→하강(0.95초)을 수정구 시안 기법으로 다시 짰다.
/// (Android `core/ui/StarBirth.kt` 패리티 — 시간표·경로·크기·입자 식은 같은 값. 한쪽만 고치지 말 것.)
///
/// 전역 오버레이인 이유: 저장 직후 지도 탭으로 전환되므로 업로드 화면 안에서 재생하면 연출이 잘린다.
/// [StarBirthStore.trigger] 만 호출하면 [StarBirthHost](MainTabView 최상단)가 지도 위에서 이어 재생한다.
/// 실패(업로드 에러)에는 트리거하지 않는다 — 성공 경로에서만 호출할 것.
///
/// 성능 약속(지도 위에서 도는 연출): 모든 빛은 미리 만든 **스프라이트 한 장**을 더하기(.plusLighter)로 찍는 것(프레임마다 그라데이션 생성 없음),
/// 입자는 상태 배열 없이 **시간의 함수**, 한 프레임 스프라이트 ≤ 약 150 · 선 ≤ 약 30 · 반짝 ≤ 약 30.
@MainActor
final class StarBirthStore: ObservableObject {
    static let shared = StarBirthStore()
    private init() {}

    struct Event: Identifiable {
        let id = UUID()
        let type: Int
        let colorIndex: Int
        let startedAt = Date()
    }

    /// 재생 중인 별. nil = 연출 없음.
    @Published private(set) var event: Event?

    func trigger(starType: Int, starColor: Int) {
        event = Event(type: starType, colorIndex: starColor)
        Haptics.celebrate() // 축하 진동
        // 유성이 내려와 안착 — 화음이 안착 순간에 오도록 음원 자체를 맞춰 두었다(Android 동일).
        MusicManager.shared.playStarBirth()
    }

    func clear() { event = nil }
}

// ── 시간표(초). 기준 화면은 폭 390 · 높이 844 "디자인 px"(u = 실제 폭 / 390). Android 상수와 같은 값. ──
private let chargeS: Double = 1.2   // ① 별빛 모으기
private let flyS: Double = 1.4      // ② 혜성 비행
private let landS: Double = 1.4     // ③ 내려앉기(마지막에 지도의 별로 이어 넘김)
private let birthDuration: Double = chargeS + flyS + landS
private let gatherParticles = 46
private let dustParticles = 30
private let trailSteps = 30
private let groundK: CGFloat = 0.7  // 땅 위 원이 화면에서 납작해지는 비율(기울기 40° → cos 40°)
private let startYFrac: CGFloat = 0.30
private let landYFrac: CGFloat = 0.50

/// 별 탄생 오버레이 호스트 — 앱 최상단(MainTabView)에 1개만 둔다.
/// 장식 전용: 터치를 받지 않으므로 연출 중에도 지도 조작이 막히지 않는다.
struct StarBirthHost: View {
    @ObservedObject private var store = StarBirthStore.shared

    var body: some View {
        if let e = store.event {
            StarBirthOverlay(event: e)
                .allowsHitTesting(false)
                .task(id: e.id) {
                    try? await Task.sleep(nanoseconds: UInt64(birthDuration * 1_000_000_000) + 50_000_000)
                    store.clear()
                }
        }
    }
}

// MARK: - 수학 도우미

private func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
private func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
private func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
private func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
private func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double { let t = clamp01((x - a) / (b - a)); return t * t * (3 - 2 * t) }
/// 결정론적 의사 난수 0..1 — 시안 `rnd(i, k)` / Android `rnd` 와 같은 식.
private func rnd(_ i: Int, _ k: Int = 0) -> Double {
    let v = sin(Double(i) * 127.1 + Double(k) * 311.7) * 43758.5453
    return v - floor(v)
}

/// 빛 스프라이트(부드럽게 꺼지는 원) 한 벌 — 색 4종(기본/중간/뜨거운/흰). 이벤트마다 한 번만 만든다.
private final class BirthSprites {
    let base: UIImage, mid: UIImage, hot: UIImage, white: UIImage
    init(accent: UIColor) {
        func mix(_ c: UIColor, _ f: CGFloat) -> UIColor {
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            c.getRed(&r, green: &g, blue: &b, alpha: &a)
            return UIColor(red: r + (1 - r) * f, green: g + (1 - g) * f, blue: b + (1 - b) * f, alpha: 1)
        }
        base = Self.sprite(accent)
        mid = Self.sprite(mix(accent, 0.5))
        hot = Self.sprite(mix(accent, 0.7))
        white = Self.sprite(.white)
    }
    private static func sprite(_ color: UIColor) -> UIImage {
        let side: CGFloat = 96
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        fmt.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: fmt).image { ctx in
            let colors = [color.withAlphaComponent(1).cgColor, color.withAlphaComponent(0.5).cgColor,
                          color.withAlphaComponent(0.13).cgColor, color.withAlphaComponent(0).cgColor] as CFArray
            guard let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors,
                                        locations: [0, 0.22, 0.58, 1]) else { return }
            let c = CGPoint(x: side / 2, y: side / 2)
            ctx.cgContext.drawRadialGradient(grad, startCenter: c, startRadius: 0, endCenter: c, endRadius: side / 2, options: [])
        }
    }
}

private struct StarBirthOverlay: View {
    let event: StarBirthStore.Event
    @State private var sprites: BirthSprites

    init(event: StarBirthStore.Event) {
        self.event = event
        _sprites = State(initialValue: BirthSprites(accent: UIColor(StarStyle.color(event.colorIndex))))
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let t = min(max(tl.date.timeIntervalSince(event.startedAt), 0), birthDuration)
            Canvas { ctx, size in
                draw(ctx: ctx, size: size, T: t)
            }
        }
        .ignoresSafeArea()
    }

    // MARK: 그리기 도구

    private func glow(_ ctx: inout GraphicsContext, _ img: UIImage, _ x: Double, _ y: Double, _ r: Double, _ a: Double) {
        guard a > 0.003, r > 0.4 else { return }
        var c = ctx
        c.blendMode = .plusLighter
        c.opacity = min(a, 1)
        c.draw(Image(uiImage: img), in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private func glowE(_ ctx: inout GraphicsContext, _ img: UIImage, _ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ a: Double) {
        guard a > 0.003, rx > 0.4, ry > 0.2 else { return }
        var c = ctx
        c.blendMode = .plusLighter
        c.opacity = min(a, 1)
        c.draw(Image(uiImage: img), in: CGRect(x: x - rx, y: y - ry, width: rx * 2, height: ry * 2))
    }

    /// 굽은 4갈래 반짝 — 수정구 시안 `sparkleR`(중심을 지나는 2차 곡선 4개). rot = 라디안.
    private func glint(_ ctx: inout GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ rot: Double, _ a: Double) {
        guard a > 0.003, r > 0.3 else { return }
        let co = cos(rot) * r, si = sin(rot) * r
        var p = Path()
        p.move(to: CGPoint(x: x + co, y: y + si))
        p.addQuadCurve(to: CGPoint(x: x - si, y: y + co), control: CGPoint(x: x, y: y))
        p.addQuadCurve(to: CGPoint(x: x - co, y: y - si), control: CGPoint(x: x, y: y))
        p.addQuadCurve(to: CGPoint(x: x + si, y: y - co), control: CGPoint(x: x, y: y))
        p.addQuadCurve(to: CGPoint(x: x + co, y: y + si), control: CGPoint(x: x, y: y))
        p.closeSubpath()
        var c = ctx
        c.blendMode = .plusLighter
        c.fill(p, with: .color(.white.opacity(min(a, 1))))
    }

    // MARK: 본체

    private func draw(ctx: GraphicsContext, size: CGSize, T: Double) {
        var ctx = ctx
        let w = Double(size.width), h = Double(size.height)
        let u = w / 390
        let sx = w * 0.5, sy = h * Double(startYFrac)
        let lx = w * 0.5, ly = h * Double(landYFrac)

        func pathAt(_ f: Double) -> CGPoint {
            let e = easeInOut(f), o = easeOut(f)
            return CGPoint(x: lerp(sx, lx, e) + sin(f * .pi * 2) * 20 * u * (1 - f),
                           y: lerp(sy, ly, o) - sin(f * .pi) * 74 * u)
        }
        func sizeAt(_ f: Double) -> Double { lerp(128, 46, easeInOut(f)) * u }

        let tFly = chargeS, tLand = chargeS + flyS
        let fly = clamp01((T - tFly) / flyS)
        let lt = T - tLand // 착지 후 경과(초)

        var px = sx, py = sy
        var starSize = 128 * u
        var flash = 0.0
        var bodyAlpha = 1.0

        if T < tFly {
            let cp = clamp01(T / chargeS)
            drawGather(&ctx, cp: cp, T: T, sx: sx, sy: sy, u: u)
            starSize *= 1 + 0.05 * sin(cp * .pi) + 0.05 * cp * cp
            flash = 0.25 * cp * cp
            glow(&ctx, sprites.base, sx, sy, (120 + cp * 80) * u, 0.18 + cp * 0.4)
            if cp > 0.86 { // 떠나기 직전 팍
                let k = (cp - 0.86) / 0.14
                glow(&ctx, sprites.white, sx, sy, (90 + 150 * k) * u, 0.55 * k)
            }
            bodyAlpha = smooth(0, 0.25, T) // 별이 서서히 떠오른다
        } else {
            let p = pathAt(fly)
            px = Double(p.x); py = Double(p.y); starSize = sizeAt(fly)
            if T < tFly + 0.18 { // 떠나는 순간의 번쩍 한 번
                let k = 1 - (T - tFly) / 0.18
                glow(&ctx, sprites.white, sx, sy, 230 * u, 0.5 * k * k)
            }
        }
        if fly > 0, fly < 1 { drawComet(&ctx, fly: fly, flyElapsed: T - tFly, u: u, pathAt: pathAt, sizeAt: sizeAt) }
        if fly >= 1 { // 내려앉는 별: 한 번 눌렸다 살아난다(감쇠하는 출렁임)
            let q = clamp01(lt / 1.1)
            starSize *= 1 + 0.26 * exp(-q * 4.2) * cos(q * 10.5)
            py += 3 * u * exp(-q * 5) * cos(q * 12)
            flash += 0.55 * exp(-lt * 5)
            drawLanding(&ctx, lt: lt, lx: lx, ly: ly, u: u)
            // 끝 0.4초: 지도에 이미 올라온 진짜 별로 자연스럽게 넘긴다.
            bodyAlpha = 1 - smooth(landS - 0.4, landS, lt)
        }

        // 별 본체 — 앱의 모든 별과 같은 크리스탈 렌더(비트맵 1회 굽고 스케일만 변경) + 번쩍임(흰 빛)
        if bodyAlpha > 0.01, starSize > 1 {
            var starCtx = ctx
            starCtx.opacity = bodyAlpha
            let img = StarCrystal.image(type: event.type, colorIndex: event.colorIndex, size: 128)
            starCtx.draw(Image(uiImage: img),
                         in: CGRect(x: px - starSize / 2, y: py - starSize / 2, width: starSize, height: starSize))
            if flash > 0.01 { glow(&ctx, sprites.hot, px, py, starSize * 0.8, min(flash * bodyAlpha, 0.8)) }
        }
        if fly >= 1 { drawArrivalGlints(&ctx, lt: lt, lx: lx, ly: ly, starSize: starSize, T: T, u: u) }
    }

    // ① 별빛 모으기 — 로그 나선 팔을 따라 알갱이가 별로 빨려들고, 얇은 빛 고리가 오므라든다.
    private func drawGather(_ ctx: inout GraphicsContext, cp: Double, T: Double, sx: Double, sy: Double, u: Double) {
        let ec = easeInOut(clamp01(cp * 1.02))
        for i in 0..<gatherParticles {
            let delay = rnd(i) * 0.36
            let q = clamp01((cp - delay) / (1 - delay))
            if q <= 0 || q >= 1 { continue }
            let rad = lerp(190 + 60 * rnd(i, 2), 8, q * q) * u
            let ang = Double(i) * 2.399963 + log(rad / (8 * u) + 1) * 1.1 + cp * 2.2
            let x = sx + cos(ang) * rad, y = sy + sin(ang) * rad * 0.78
            let a = sin(q * .pi) * 0.9
            let img = i % 5 == 0 ? sprites.white : (i % 3 == 0 ? sprites.mid : sprites.base)
            glow(&ctx, img, x, y, (6 + 5 * rnd(i, 6)) * u, 0.5 * a)
            if i % 4 == 0 { glint(&ctx, x, y, (2 + 3.4 * rnd(i, 8)) * u, ang * 2 + T * 2, a) }
        }
        // 오므라드는 빛 고리(기울어진 타원, 두 겹) — 꼬리가 사라지는 호를 짧은 호 조각으로 그린다.
        let r = lerp(190, 82, ec) * u, ry = r * 0.4
        let tilt = -0.28 + cp * 0.5
        let segs = 12, a0 = cp * 5
        let accent = StarStyle.color(event.colorIndex)
        var c = ctx
        c.blendMode = .plusLighter
        c.translateBy(x: sx, y: sy)
        c.rotate(by: .radians(tilt))
        for layer in 0..<2 {
            let sw = (layer == 1 ? 1.6 : 7.0) * u
            let baseA = (layer == 1 ? 1.0 : 0.28) * sin(min(1, cp * 1.2) * .pi * 0.5) * (1 - 0.35 * cp)
            let col: Color = layer == 1 ? accent.mixedWhite(0.7) : accent
            for k in 0..<segs {
                let f = pow(Double(k) / Double(segs), 1.4)
                let s0 = a0 + Double(k) / Double(segs) * 2 * .pi * 0.92
                let sweep = 2 * .pi * 0.92 / Double(segs) + 0.02
                var arc = Path()
                // 타원 호: 단위원 호를 (r, ry) 로 늘린다.
                arc.addArc(center: .zero, radius: 1, startAngle: .radians(s0), endAngle: .radians(s0 + sweep), clockwise: false)
                let xf = CGAffineTransform(scaleX: r, y: ry)
                // 선 굵기가 비균일 늘림에 휘지 않도록, 늘린 경로 위에서 stroke 한다.
                let stroke = StrokeStyle(lineWidth: sw, lineCap: .round)
                c.stroke(arc.applying(xf), with: .color(col.opacity(max(0, min(1, baseA * f)))), style: stroke)
            }
        }
    }

    // ② 혜성 꼬리 + 흩날리는 빛
    private func drawComet(_ ctx: inout GraphicsContext, fly: Double, flyElapsed: Double, u: Double,
                           pathAt: (Double) -> CGPoint, sizeAt: (Double) -> Double) {
        let step = 0.012
        for i in stride(from: trailSteps, through: 1, by: -1) {
            let f = max(0, fly - Double(i) * step)
            let p = pathAt(f)
            let k = 1 - Double(i) / Double(trailSteps + 1)
            let sz = sizeAt(f)
            glow(&ctx, sprites.base, Double(p.x), Double(p.y), sz * (0.3 + 0.5 * k), 0.06 + 0.3 * k * k)
            glow(&ctx, sprites.hot, Double(p.x), Double(p.y), sz * 0.2 * k + 2 * u, 0.62 * k * k * k)
        }
        // 맨 안쪽의 가는 흰 심지
        var prev = pathAt(fly)
        var c = ctx
        c.blendMode = .plusLighter
        for i in 1...12 {
            let f = max(0, fly - Double(i) * step * 1.6)
            let p = pathAt(f)
            let k = 1 - Double(i) / 13
            var line = Path()
            line.move(to: prev)
            line.addLine(to: p)
            c.stroke(line, with: .color(.white.opacity(0.85 * k * k)), style: StrokeStyle(lineWidth: (1 + 2.4 * k) * u, lineCap: .round))
            prev = p
        }
        // 흩날리는 별가루 — 길을 따라 태어나 천천히 흩어지며 사라진다(시간의 함수)
        for j in 0..<dustParticles {
            let tb = Double(j) * 0.043 + rnd(j) * 0.03
            let age = flyElapsed - tb
            if age <= 0 || age > 0.75 { continue }
            let q = age / 0.75
            let p = pathAt(clamp01(tb / flyS))
            let dx = (rnd(j, 3) - 0.5) * 70 * q * u
            let dy = ((rnd(j, 5) - 0.3) * 48 * q + 40 * q * q) * u
            let a = sin(min(1, q * 1.6) * .pi * 0.5) * (1 - q)
            let x = Double(p.x) + dx, y = Double(p.y) + dy
            let img = j % 3 == 0 ? sprites.white : (j % 3 == 1 ? sprites.mid : sprites.base)
            glow(&ctx, img, x, y, (5 + 5 * rnd(j, 7) * (1 - q)) * u, 0.5 * a)
            if j % 2 == 0 { glint(&ctx, x, y, (2 + 3.2 * rnd(j, 9) * (1 - q * 0.5)) * u, q * 3 + Double(j), a) }
        }
    }

    // ③ 내려앉기 — 바닥을 쓸고 가는 빛 한 번, 얇은 고리 하나, 고리 위에 떠오르는 반짝
    private func drawLanding(_ ctx: inout GraphicsContext, lt: Double, lx: Double, ly: Double, u: Double) {
        if lt < 0 { return }
        let q = clamp01(lt / 1.15), eo = easeOut(q), fade = 1 - q
        let gy = ly + 8 * u
        let k = Double(groundK)
        let r1 = (40 + 92 * eo) * u
        glowE(&ctx, sprites.base, lx, gy, r1, r1 * k, 0.6 * fade * fade)
        let r2 = (22 + 30 * eo) * u
        glowE(&ctx, sprites.hot, lx, gy, r2, r2 * k, 0.6 * fade * fade * fade)
        // 얇은 고리 하나(두 겹 — 번짐 + 선)
        let rr = (18 + 104 * eo) * u
        let rect = CGRect(x: lx - rr, y: gy - rr * k, width: rr * 2, height: rr * 2 * k)
        let accent = StarStyle.color(event.colorIndex)
        var c = ctx
        c.blendMode = .plusLighter
        c.stroke(Path(ellipseIn: rect), with: .color(accent.opacity(0.22 * fade)), lineWidth: (6 * fade + 1) * u)
        c.stroke(Path(ellipseIn: rect), with: .color(accent.mixedWhite(0.7).opacity(0.9 * fade)), lineWidth: 1.2 * u)
        // 고리 위에 돋는 작은 반짝들
        for i in 0..<9 {
            let an = Double(i) / 9 * 2 * .pi + 0.4
            let d = clamp01((q - rnd(i) * 0.35) / 0.5)
            if d <= 0 || d >= 1 { continue }
            let a = sin(d * .pi)
            let x = lx + cos(an) * rr
            let y = gy + sin(an) * rr * k - 8 * u * d
            glow(&ctx, sprites.base, x, y, 9 * u, 0.35 * a)
            glint(&ctx, x, y, (2.4 + 3.4 * a) * u, d * 3 + Double(i), 0.95 * a)
        }
    }

    // 별 위에 한 번: 윗 가장자리의 큰 반짝 하나 + 지도의 새 별(반딧불)로 이어지는 떠오르는 빛
    private func drawArrivalGlints(_ ctx: inout GraphicsContext, lt: Double, lx: Double, ly: Double, starSize: Double, T: Double, u: Double) {
        if lt < 0 { return }
        let q = clamp01((lt - 0.12) / 0.9)
        if q > 0, q < 1 {
            let a = sin(q * .pi)
            let x = lx - starSize * 0.08, y = ly - starSize * 0.42
            glow(&ctx, sprites.hot, x, y, (26 * a + 8) * u, 0.6 * a)
            glint(&ctx, x, y, (5 + 17 * a) * u, q * 2.6, a)
        }
        let on = smooth(0.3, 1.0, lt)
        let fadeOut = 1 - smooth(landS - 0.5, landS, lt)
        for i in 0..<7 {
            let ph = (T * 0.2 + Double(i) / 7).truncatingRemainder(dividingBy: 1)
            let ang = Double(i) * 2.4 + T * 0.35
            let x = lx + cos(ang) * starSize * (0.75 + 0.45 * sin(Double(i) * 7.1))
            let y = ly + starSize * 0.5 - ph * starSize * 2.3
            let al = sin(ph * .pi) * on * fadeOut * (0.55 + 0.45 * sin(T * 3.1 + Double(i) * 1.7))
            glow(&ctx, sprites.base, x, y, max(3 * u, starSize * 0.2), 0.55 * al)
            glow(&ctx, sprites.hot, x, y, 1.6 * u, 0.9 * al)
        }
    }
}

private extension Color {
    /// 흰색 쪽으로 [f] 만큼 섞은 색(Android `lerp(c, White, f)`).
    func mixedWhite(_ f: CGFloat) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        return Color(red: Double(r + (1 - r) * f), green: Double(g + (1 - g) * f), blue: Double(b + (1 - b) * f))
    }
}
