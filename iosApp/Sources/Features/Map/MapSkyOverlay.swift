import SwiftUI

/// 지도 위 하늘 · 땅 빛 · 비네트(2026-10-28 "하나의 우주" 지도 시안 ground.js / world.js 를 수치 그대로 옮김).
///  - 땅 빛: (1) 먼 곳 보랏빛 반사(프레넬) (2) 화면을 비스듬히 가로지르는 넓은 빛띠 두 겹(아주 느리게 흐른다)
///          (3) 한 곳에 모인 부드러운 반사광 + 위쪽 땅이 남보라 안개로 잠기는 것.
///  - 하늘: 위쪽 띠(성운 4 · 은하수 · 반짝이는 별 150 · 8초마다 유성 · 지평선 도시 빛).
///  - 비네트: 아래쪽 어둠 + 가장자리 둥근 어둠.
/// 시안은 화면 390×844 기준 px — 위치는 화면 비율, 크기는 pt(시안 1px ≈ 1pt).
/// ⚠️ 지도(MapLibre)는 별도 표면이라 더하기 합성이 지도와 섞이지 않는다 → 시안의 "더하기 빛 C" 는
///    C 를 가장 밝은 채널로 나눈 색을 불투명도 max(C) 로 덮어 근사한다(어두운 땅에서 ≈ +C).
/// Android `MapSky.kt` 와 같은 수치. 별 배치 난수 수열은 Kotlin `Random` 과 달라 배치는 다르다(시드 고정 계약은 같음).
struct MapSkyOverlay: View {
    @ObservedObject private var cam = MapSkyCamera.shared

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.05)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 100_000)
            Canvas { ctx, size in
                let zoom = cam.zoom
                // 저줌 글로브 전환 구간(줌 4.4→2.4)엔 0 으로 빠진다(Android `1 - hazeAlpha`).
                let k = 1 - min(max((4.4 - zoom) / 2.0, 0), 1)
                MapSkyDraw.groundLight(&ctx, size, k: k, zoom: zoom, t: t)
                MapSkyDraw.skyBand(&ctx, size, k: k, zoom: zoom, t: t)
                MapSkyDraw.vignette(&ctx, size)
            }
        }
        .allowsHitTesting(false)
    }
}

/// 하늘 오버레이가 보는 카메라 줌 — 지도 코디네이터가 0.05 단위로 바뀔 때만 알린다(이 뷰만 다시 그린다, MapScreen 은 그대로).
final class MapSkyCamera: ObservableObject {
    static let shared = MapSkyCamera()
    @Published private(set) var zoom: Double = 15

    func report(_ z: Double) {
        let q = (z * 20).rounded() / 20
        guard q != zoom else { return }
        zoom = q
    }
}

private enum MapSkyDraw {
    static let baseTiltDeg = Double(MapLibreView.baseTiltDeg)

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(max((x - a) / (b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }

    static func fract(_ x: Double) -> Double { x - x.rounded(.down) }

    /// 시안 카메라(거리 1.5H, 기울기 40°)에서 화면 y 비율(0=위) → 바닥 깊이(화면 가운데 = 1).
    static func depthAt(_ yFrac: Double) -> Double {
        let s = sin(baseTiltDeg * .pi / 180)
        let c = cos(baseTiltDeg * .pi / 180)
        let u = (0.5 - yFrac) / 1.5
        let den = c - u * s
        return den > 0.02 ? 1 + u * s / den : 4
    }

    static let fogNear = depthAt(268.0 / 844.0)
    static let fogFar = depthAt(92.0 / 844.0)

    /// 하늘 띠 높이(화면 높이 비율) — 줌 13 에서 122px, 17 에서 94px(844 중).
    static func skyHeightFrac(_ zoom: Double) -> Double {
        let px: Double = zoom <= 13 ? 122 : zoom >= 17 ? 94 : 122 + (94 - 122) * (zoom - 13) / 4
        return px / 844
    }

    /// "더하기 빛" 근사 — 더할 양 (r, g, b) → 정규화 색 + 가장 밝은 채널 값의 불투명도.
    static func addLight(_ r: Double, _ g: Double, _ b: Double, _ k: Double) -> Color {
        let m = max(r, g, b)
        guard m > 1e-4, k > 0 else { return .clear }
        return Color(red: r / m, green: g / m, blue: b / m).opacity(min(max(m * k, 0), 1))
    }

    static func rgb(_ r: Double, _ g: Double, _ b: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255)
    }

    // MARK: 땅 빛

    static func groundLight(_ ctx: inout GraphicsContext, _ size: CGSize, k: Double, zoom: Double, t: Double) {
        guard k > 0.01 else { return }
        let w = size.width, h = size.height
        let full = Path(CGRect(origin: .zero, size: size))
        let near = 1 - smooth(0.7, 3.2, pow(2, 15 - zoom))

        // (1) 먼 곳일수록 하늘빛(보랏빛)을 비춘다 — col += (0.07, 0.062, 0.19) · gl0² · 0.5
        let fres: [Gradient.Stop] = (0...8).map { i in
            let y = Double(i) / 8
            let gl0 = min(max((depthAt(y) - 0.55) * 1.15, 0), 1)
            return .init(color: addLight(0.07, 0.062, 0.19, gl0 * gl0 * 0.5 * k), location: y)
        }
        ctx.fill(full, with: .linearGradient(Gradient(stops: fres), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

        // (2) 넓은 빛줄기 두 겹 — ph = x/W·0.78 + y/H·0.46 을 따라 가우스 띠가 아주 느리게 흐른다
        let fac = 0.3 + 0.7 * 0.52 + 1.1 * near
        let phMax = 1.24
        let bands: [Gradient.Stop] = (0...48).map { i in
            let s = Double(i) / 48
            let ph = s * phMax
            let b1 = exp(-pow((fract(ph * 0.55 - t * 0.011) - 0.5) * 5.2, 2))
            let b2 = exp(-pow((fract(ph * 0.9 + 0.37 - t * 0.017) - 0.5) * 9.0, 2))
            let a = (b1 * 0.55 + b2 * 0.3) * fac
            return .init(color: addLight(0.075, 0.085, 0.17, a * k), location: s)
        }
        let gx = 0.78 / Double(w), gy = 0.46 / Double(h)
        let g2 = gx * gx + gy * gy
        ctx.fill(full, with: .linearGradient(
            Gradient(stops: bands), startPoint: .zero,
            endPoint: CGPoint(x: gx * phMax / g2, y: gy * phMax / g2)))

        // (3) 한 곳에 모인 부드러운 반사광 — (0.62W, 0.58H), exp(-(dx² + (1.45dy)²)·7)
        let hot = (1 + 1.4 * near) * k
        let gauss: [Gradient.Stop] = (0...12).map { i in
            let r = Double(i) / 12
            return .init(color: addLight(0.045, 0.045, 0.10, exp(-r * r * 7) * hot), location: r)
        }
        let c = CGPoint(x: w * 0.62, y: h * 0.58)
        var hc = ctx
        hc.translateBy(x: c.x, y: c.y)
        hc.scaleBy(x: 1, y: (h / 1.45) / w)
        hc.fill(Path(ellipseIn: CGRect(x: -w, y: -w, width: w * 2, height: w * 2)),
                with: .radialGradient(Gradient(stops: gauss), center: .zero, startRadius: 0, endRadius: w))

        // 위쪽 땅은 남보라 안개(0.045, 0.045, 0.11)로 잠긴다 — fog = smoothstep(near, far, depth) 를 한 번 더 부드럽게 × 0.97
        let fogTop = 0.34
        let fog: [Gradient.Stop] = (0...10).map { i in
            let y = fogTop * Double(i) / 10
            let f = smooth(fogNear, fogFar, depthAt(y))
            return .init(color: Color(red: 0.045, green: 0.045, blue: 0.11).opacity(f * f * (3 - 2 * f) * 0.97 * k),
                         location: y / fogTop)
        }
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h * fogTop)),
                 with: .linearGradient(Gradient(stops: fog), startPoint: .zero, endPoint: CGPoint(x: 0, y: h * fogTop)))
    }

    // MARK: 하늘

    struct SkyStar { let x, y, s, ph, w: Double; let warm: Bool }
    struct Milky { let u, off, s, ph, w: Double }
    struct Nebula { let x, y, rx, ry, rot: Double; let c: Color; let a, v: Double }

    /// 시드 고정 난수(LCG) — 매 실행 같은 배치.
    struct Rng {
        var state: UInt64
        mutating func f() -> Double {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return Double((state >> 33) & 0xFFFFFF) / Double(0x1000000)
        }
    }

    static let data: (stars: [SkyStar], milky: [Milky]) = {
        var r = Rng(state: 20261027)
        let stars = (0..<150).map { _ -> SkyStar in
            let x = r.f(), y = pow(r.f(), 1.3), s = 0.35 + pow(r.f(), 3) * 1.5
            let ph = r.f() * 2 * .pi, w = 0.5 + r.f() * 1.8, warm = r.f() < 0.18
            return SkyStar(x: x, y: y, s: s, ph: ph, w: w, warm: warm)
        }
        let milky = (0..<300).map { _ -> Milky in
            let u = r.f()
            let gj = (r.f() + r.f() + r.f() - 1.5) / 1.5
            let s = 0.25 + pow(r.f(), 2.5) * 0.9
            let ph = r.f() * 2 * .pi, w = 0.4 + r.f() * 1.5
            return Milky(u: u, off: gj * 0.085, s: s, ph: ph, w: w)
        }
        return (stars, milky)
    }()

    static let nebulae: [Nebula] = [
        Nebula(x: 0.16, y: 0.26, rx: 0.46, ry: 0.17, rot: -0.22, c: rgb(123, 92, 255), a: 0.16, v: 0.006),
        Nebula(x: 0.80, y: 0.20, rx: 0.38, ry: 0.14, rot: 0.20, c: rgb(62, 123, 255), a: 0.12, v: -0.004),
        Nebula(x: 0.50, y: 0.50, rx: 0.60, ry: 0.13, rot: -0.06, c: rgb(176, 124, 255), a: 0.08, v: 0.003),
        Nebula(x: 0.32, y: 0.82, rx: 0.70, ry: 0.12, rot: 0.04, c: rgb(255, 150, 110), a: 0.045, v: 0.002),
    ]
    static let starCool = rgb(225, 233, 255)
    static let starWarm = rgb(255, 226, 190)

    /// 시안 빛 스프라이트(가운데 1 → 0.2 에서 0.58 → 0.5 에서 0.16 → 0) 를 원 하나로.
    static func glow(_ ctx: inout GraphicsContext, _ color: Color, _ c: CGPoint, _ radius: Double, _ alpha: Double) {
        guard alpha > 0.003, radius > 0.2 else { return }
        let a = min(alpha, 1)
        let g = Gradient(stops: [
            .init(color: color.opacity(a), location: 0),
            .init(color: color.opacity(0.58 * a), location: 0.2),
            .init(color: color.opacity(0.16 * a), location: 0.5),
            .init(color: color.opacity(0), location: 1),
        ])
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: radius * 2, height: radius * 2)),
                 with: .radialGradient(g, center: c, startRadius: 0, endRadius: radius))
    }

    /// 비스듬한 타원 빛(성운 · 지평선) — 시안 glowE.
    static func glowE(_ ctx: inout GraphicsContext, _ color: Color, _ c: CGPoint, _ rx: Double, _ ry: Double, _ rot: Double, _ alpha: Double) {
        guard alpha > 0.003, rx > 0.2, ry > 0.05 else { return }
        var e = ctx
        e.translateBy(x: c.x, y: c.y)
        e.rotate(by: .radians(rot))
        e.scaleBy(x: 1, y: ry / rx)
        glow(&e, color, .zero, rx, alpha)
    }

    /// 굽은 4갈래 반짝(시안 sparkle) — 가운데로 오목하게 휜 네 꼭지.
    static func sparkle(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: Double, _ color: Color, _ alpha: Double) {
        guard alpha > 0.003, r > 0.1 else { return }
        var p = Path()
        p.move(to: CGPoint(x: c.x, y: c.y - r))
        p.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: c)
        ctx.fill(p, with: .color(color.opacity(min(alpha, 1))))
    }

    static func posMod(_ a: Double, _ m: Double) -> Double {
        (a.truncatingRemainder(dividingBy: m) + m).truncatingRemainder(dividingBy: m)
    }

    /// 하늘 띠 — 화면 맨 위(상태 표시줄 · 탑바 뒤)에서 지도 땅으로 이어진다. 시안 drawSky 와 같은 그리기.
    static func skyBand(_ ctx: inout GraphicsContext, _ size: CGSize, k: Double, zoom: Double, t: Double) {
        guard k > 0.01 else { return }
        let w = Double(size.width)
        let hs = Double(size.height) * skyHeightFrac(zoom)
        // 바탕: 위는 거의 검정 → 아래로 옅어진다
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: hs)), with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(3, 5, 13).opacity(k), location: 0),
                .init(color: rgb(5, 7, 20).opacity(0.95 * k), location: 0.28),
                .init(color: rgb(8, 9, 26).opacity(0.78 * k), location: 0.5),
                .init(color: rgb(10, 10, 30).opacity(0.4 * k), location: 0.72),
                .init(color: rgb(11, 11, 30).opacity(0.12 * k), location: 0.9),
                .init(color: rgb(11, 11, 30).opacity(0), location: 1),
            ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: hs)))
        let ox = t * 1.1 // 하늘이 아주 천천히 옆으로 흐른다
        // 성운
        for n in nebulae {
            let x = posMod(n.x + ox / w * 0.25 + t * n.v * 0.02, 1.4) - 0.2
            glowE(&ctx, n.c, CGPoint(x: x * w, y: n.y * hs), n.rx * w, n.ry * hs * 2.2, n.rot, n.a * k)
        }
        // 은하수(비스듬한 띠)
        let span = w * 1.2
        let milkyColor = rgb(220, 226, 255)
        for m in data.milky {
            let x0 = -0.1 + m.u * 1.2
            let y0 = 0.64 - m.u * 0.5 + m.off
            let xx = posMod(x0 * w + ox * 0.7, span) - w * 0.1
            let yy = y0 * hs
            let fade = 1 - smooth(0.5, 0.95, yy / hs)
            let tw = 0.55 + 0.45 * sin(t * m.w + m.ph)
            let a = 0.32 * tw * fade * k
            if a <= 0.004 { continue }
            ctx.fill(Path(CGRect(x: xx, y: yy, width: m.s, height: m.s)), with: .color(milkyColor.opacity(min(a, 1))))
        }
        glowE(&ctx, rgb(150, 140, 255), CGPoint(x: w * 0.5, y: hs * 0.42), w * 0.75, hs * 0.09, -0.38, 0.07 * k)
        // 별
        for s in data.stars {
            let xx = posMod(s.x * w + ox, w)
            let yy = s.y * hs * 0.92
            let fade = 1 - smooth(0.48, 0.92, yy / hs)
            if fade <= 0.01 { continue }
            let tw = 0.5 + 0.5 * sin(t * s.w + s.ph)
            let c = s.warm ? starWarm : starCool
            let p = CGPoint(x: xx, y: yy)
            if s.s > 1.45 {
                glow(&ctx, c, p, 6 + 3 * tw, 0.22 * fade * tw * k)
                sparkle(&ctx, p, 2.4 + 1.8 * tw, c, (0.35 + 0.5 * tw) * fade * k)
            } else {
                let r = s.s * 0.72
                let a = min(max((0.12 + 0.5 * tw) * min(s.s / 1.4, 1) * fade * k, 0), 1)
                ctx.fill(Path(ellipseIn: CGRect(x: xx - r, y: yy - r, width: r * 2, height: r * 2)), with: .color(c.opacity(a)))
            }
        }
        // 유성 — 8초마다 한 번 0.9초
        let idx = (t / 8).rounded(.down)
        let u = (t - idx * 8 - 2) / 0.9
        if u >= 0, u <= 1 {
            let sd = abs((sin(idx * 12.9898) * 43758.547).truncatingRemainder(dividingBy: 1))
            let sx = w * (0.12 + 0.6 * sd)
            let sy = hs * (0.08 + 0.3 * (sd * 7.31).truncatingRemainder(dividingBy: 1))
            let len = w * 0.3
            let dx = len * 0.92, dy = len * 0.36
            let hx = sx + dx * u, hy = sy + dy * u
            let tx = hx - dx * 0.45, ty = hy - dy * 0.45
            let fade = sin(u * .pi) * (1 - smooth(0.5, 0.9, hy / hs)) * k
            if fade > 0.003 {
                var line = Path()
                line.move(to: CGPoint(x: tx, y: ty))
                line.addLine(to: CGPoint(x: hx, y: hy))
                ctx.stroke(line, with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.6 * fade)]),
                    startPoint: CGPoint(x: tx, y: ty), endPoint: CGPoint(x: hx, y: hy)),
                    style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                glow(&ctx, .white, CGPoint(x: hx, y: hy), 5, 0.5 * fade)
            }
        }
        // 지평선의 도시 빛(보랏빛 + 따뜻한 빛) — 땅과 하늘이 만나는 곳
        glowE(&ctx, rgb(120, 100, 255), CGPoint(x: w * 0.5, y: hs * 0.86), w * 0.9, hs * 0.16, 0, 0.14 * k)
        glowE(&ctx, rgb(255, 160, 110), CGPoint(x: w * 0.55, y: hs * 0.92), w * 0.7, hs * 0.1, 0, 0.07 * k)
    }

    // MARK: 비네트

    /// 아래쪽 190px(844 중) 어둠 + 화면 가장자리 둥근 어둠(시안 drawVignette).
    static func vignette(_ ctx: inout GraphicsContext, _ size: CGSize) {
        let w = size.width, h = size.height
        let bh = h * 190 / 844
        let dark = rgb(3, 4, 12)
        ctx.fill(Path(CGRect(x: 0, y: h - bh, width: w, height: bh)), with: .linearGradient(
            Gradient(colors: [dark.opacity(0), dark.opacity(0.55)]),
            startPoint: CGPoint(x: 0, y: h - bh), endPoint: CGPoint(x: 0, y: h)))
        let edge = rgb(2, 3, 10)
        let r1 = h * 0.78
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            Gradient(stops: [
                .init(color: edge.opacity(0), location: 0),
                .init(color: edge.opacity(0), location: 0.32 / 0.78),
                .init(color: edge.opacity(0.5), location: 1),
            ]), center: CGPoint(x: w / 2, y: h * 0.55), startRadius: 0, endRadius: r1))
    }
}
