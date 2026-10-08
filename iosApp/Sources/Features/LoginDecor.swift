import SwiftUI

// 로그인 화면 장식 — 살아있는 하늘 · 로고 글린트 · 크림 캡슐 버튼.
// Android `feature/auth/screen/LoginDecor.kt` 와 같은 값/식(별 배치도 `JavaRandom` 으로 같은 시드 → 같은 위치).

// MARK: - 살아있는 하늘

/// 하늘 위 잔별 1개. 좌표는 화면 비율(x 0..1, y 0..skyMaxY), 반지름 pt(= Android dp).
private struct LoginSkyStar {
    let x: Float, y: Float, r: Float
    let speed: Float, phase: Float
    let color: Color
    let sparkle: Bool
}

private enum LoginSky {
    static let starCount = 84
    static let sparkleCount = 6
    /// 별은 화면 위 60% 에만 — 영상의 지구(화면 약 71% 아래)와 푸른 테두리 빛을 가리지 않는다.
    static let skyMaxY: Float = 0.60
    /// 유성 — 주기(초) / 한 번 날아가는 시간(초) / 첫 유성이 나오기 전 대기(초).
    static let meteorPeriod: Double = 7.5
    static let meteorDuration: Double = 0.95
    static let meteorFirstAfter: Double = 1.8

    static let colors: [Color] = [
        Color(hex: 0xFFFFFF), Color(hex: 0xFFFFFF), Color(hex: 0xFFFFFF), Color(hex: 0xBFD8FF), Color(hex: 0xBFD8FF),
        Color(hex: 0xFFE9B8), Color(hex: 0xFFC2E2),
    ]

    /// Android `buildSkyStars()` 와 **같은 난수 호출 순서**(x, y, r, speed, phase, color) — 순서를 바꾸면 배치가 달라진다.
    static let stars: [LoginSkyStar] = {
        var rnd = JavaRandom(20261008)
        return (0..<starCount).map { i in
            let sparkle = i < sparkleCount
            let x = rnd.nextFloat()
            let y = rnd.nextFloat() * skyMaxY
            let r: Float = sparkle ? 0.9 + rnd.nextFloat() * 0.6 : 0.45 + rnd.nextFloat() * 0.65
            let speed = 0.6 + rnd.nextFloat() * 1.7
            let phase = rnd.nextFloat() * (2 * Float.pi)
            let color = colors[Int(rnd.nextInt(Int32(colors.count)))]
            return LoginSkyStar(x: x, y: y, r: r, speed: speed, phase: phase, color: color, sparkle: sparkle)
        }
    }()
}

/// 영상이 멈춘 뒤에도 하늘이 살아 있게 — 잔별이 천천히 반짝이고(일부는 십자 빛줄기) 7~8초마다 유성이 스친다.
/// `visible` 이 true 가 되면 1.4초에 걸쳐 나타나고, 보이지 않는 동안엔 타임라인도 쉰다.
/// (Android 는 영상 종료 시점에, iOS 는 로그인 UI 가 뜨는 시점에 시작 — iOS 는 영상 종료 2초 전에 UI 를 띄우는 구조라서.)
struct LivingSkyView: View {
    let visible: Bool
    @State private var t0 = Date.timeIntervalSinceReferenceDate

    var body: some View {
        TimelineView(.animation(paused: !visible)) { tl in
            Canvas { ctx, size in
                let t = tl.date.timeIntervalSinceReferenceDate - t0
                let w = size.width, h = size.height
                for s in LoginSky.stars {
                    let tw = 0.5 + 0.5 * sin(t * Double(s.speed) + Double(s.phase))
                    let k = 0.40 + 0.60 * tw                      // 은은한 깜빡임(번쩍이지 않고 숨 쉬듯)
                    let c = CGPoint(x: CGFloat(s.x) * w, y: CGFloat(s.y) * h)
                    let r = CGFloat(s.r)
                    if s.sparkle {
                        Self.drawSparkle(ctx, c, r, s.color, k, t * 0.15 + Double(s.phase))
                    } else {
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                                 with: .color(s.color.opacity(min(max(0.80 * k, 0), 1))))
                    }
                }
                Self.drawMeteor(ctx, t, w, h)
            }
        }
        .opacity(visible ? 1 : 0)
        .animation(.easeInOut(duration: 1.4), value: visible)
        .onChange(of: visible) { v in
            if v { t0 = Date.timeIntervalSinceReferenceDate }
        }
        .allowsHitTesting(false)
    }

    private static func disc(_ ctx: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color) {
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(color))
    }

    /// 큰 잔별 — 가장자리가 부드럽게 사라지는 작은 번짐 + 심지 + **짧고 가는** 빛줄기(가로가 세로보다 길다). Android `drawSparkleStar` 와 같은 값.
    /// (처음엔 딱 떨어지는 반투명 원판 후광 + 긴 십자선이라 부자연스러웠다 — 방사형 그라데이션으로 바꾸고 반 이하로 줄였다.)
    private static func drawSparkle(_ ctx: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color, _ k: Double, _ angle: Double) {
        let a = min(max(k, 0), 1)
        let glowR = r * 3.4
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - glowR, y: c.y - glowR, width: glowR * 2, height: glowR * 2)),
                 with: .radialGradient(Gradient(colors: [color.opacity(0.22 * a), .clear]),
                                       center: c, startRadius: 0, endRadius: glowR))
        disc(ctx, c, r, color.opacity(0.95 * a))
        let len = r * CGFloat(3.0 + 2.6 * k)
        let ca = cos(angle) * 0.35   // ±20° 안에서만 흔들려 반듯한 십자가 되지 않게
        let sa = sin(angle) * 0.35
        for dir in 0..<2 {
            let dx = dir == 0 ? 1.0 : -sa
            let dy = dir == 0 ? ca : 1.0
            let n = (dx * dx + dy * dy).squareRoot()
            let l = dir == 0 ? len : len * 0.7   // 세로는 가로의 70%
            let ux = CGFloat(dx / n) * l, uy = CGFloat(dy / n) * l
            let p0 = CGPoint(x: c.x - ux, y: c.y - uy), p1 = CGPoint(x: c.x + ux, y: c.y + uy)
            var line = Path()
            line.move(to: p0)
            line.addLine(to: p1)
            ctx.stroke(line,
                       with: .linearGradient(Gradient(colors: [.clear, color.opacity(0.60 * a), .clear]),
                                             startPoint: p0, endPoint: p1),
                       style: StrokeStyle(lineWidth: max(0.8, r * 0.38), lineCap: .round))
        }
    }

    /// 주기마다 한 번, 주기 안의 무작위 시각에 우상단→좌하단으로 스치는 유성 — Android `drawMeteor` 와 같은 식·같은 난수 순서
    /// (start, x0, y0, angle).
    private static func drawMeteor(_ ctx: GraphicsContext, _ t: Double, _ w: CGFloat, _ h: CGFloat) {
        guard t >= LoginSky.meteorFirstAfter else { return }
        let tt = t - LoginSky.meteorFirstAfter
        let k = Int(tt / LoginSky.meteorPeriod)
        var rnd = JavaRandom(Int64(k) * 7919 + 13)
        let start = Double(0.6 + rnd.nextFloat() * 3.2)
        let local = tt - (Double(k) * LoginSky.meteorPeriod + start)
        guard local >= 0, local <= LoginSky.meteorDuration else { return }
        let p = local / LoginSky.meteorDuration
        let x0 = w * CGFloat(0.55 + rnd.nextFloat() * 0.40)
        let y0 = h * CGFloat(0.03 + rnd.nextFloat() * 0.22)
        let ang = Double((205 + rnd.nextFloat() * 25) * (Float.pi / 180))   // 왼쪽 아래로
        let dirX = CGFloat(cos(ang)), dirY = CGFloat(-sin(ang))              // 화면 좌표(y 아래)
        let travel = w * 0.55
        let e = CGFloat(1 - (1 - p) * (1 - p))                                // easeOut
        let head = CGPoint(x: x0 + dirX * travel * e, y: y0 + dirY * travel * e)
        let tailLen = w * 0.20 * CGFloat(min(1, p * 3) * (1 - 0.5 * p))
        let tail = CGPoint(x: head.x - dirX * tailLen, y: head.y - dirY * tailLen)
        let fade = sin(Double.pi * p)                                         // 양 끝에서 사라짐
        var line = Path()
        line.move(to: tail)
        line.addLine(to: head)
        ctx.stroke(line,
                   with: .linearGradient(Gradient(colors: [.clear, Color.white.opacity(0.9 * fade)]),
                                         startPoint: tail, endPoint: head),
                   style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        disc(ctx, head, 5.5, Color.white.opacity(0.35 * fade))
        disc(ctx, head, 1.8, Color.white.opacity(fade))
    }
}

// MARK: - 로고 글린트

/// 가끔 비스듬한 빛줄기가 로고 글자·별 위를 훑고 지나간다(약 5초 간격, 1.3초) — 로고 알파로 마스크해 로고가 있는 곳에만.
/// Android `Modifier.logoGlint` 와 같은 밴드(폭 ±0.13, 기울기 dy 0.45). `start` 는 로고 UI 가 뜬 시각.
struct LogoGlintOverlay: View {
    let image: UIImage
    let active: Bool
    @State private var t0 = Date.timeIntervalSinceReferenceDate

    private static let delay = 2.2, sweep = 1.3, pause = 5.2

    var body: some View {
        TimelineView(.animation(paused: !active)) { tl in
            let p = Self.progress(elapsed: tl.date.timeIntervalSinceReferenceDate - t0)
            Rectangle()
                .fill(LinearGradient(
                    colors: [.clear, Color.white.opacity(0.80), .clear],
                    startPoint: UnitPoint(x: p - 0.13, y: 0),
                    endPoint: UnitPoint(x: p + 0.13, y: 0.45)))
                .opacity(p > -0.3 && p < 1.3 ? 1 : 0)
        }
        .mask {
            Image(uiImage: image).resizable().interpolation(.high).scaledToFit()
        }
        .allowsHitTesting(false)
        .onChange(of: active) { a in
            if a { t0 = Date.timeIntervalSinceReferenceDate }
        }
    }

    /// 로고 폭 비율 위치(-0.3 → 1.3). 범위 밖이면 그리지 않는다.
    private static func progress(elapsed: Double) -> CGFloat {
        guard elapsed >= delay else { return -1 }
        let local = (elapsed - delay).truncatingRemainder(dividingBy: sweep + pause)
        guard local <= sweep else { return -1 }
        let u = local / sweep
        let eased = u * u * (3 - 2 * u)
        return CGFloat(-0.3 + 1.6 * eased)
    }
}

// MARK: - 크림 캡슐 버튼

/// 로그인 버튼 — 예전 크림색 캡슐(`StarDiaryButton`)의 모습으로 되돌리되, 아주 살짝 비치는 면(알파 0.94) + 위에서 아래로 약해지는
/// 얇은 하이라이트 테두리 + 누를 때 반응(작아짐·밝아짐·**진동**)만 더했다. 글자/아이콘은 진한 숯색(0x2C2723) — 크림 면 위에서 가장 잘 읽힌다.
/// (첫 시도인 "글래스(투명 흰 면 + 흰 글씨)"는 지구 위에서 회색빛으로 흐려 글씨가 안 보여 되돌림.) Android `CreamCapsuleButton` 과 같은 값.
/// 진동은 **터치 down 순간**(`Haptics.soft`).
struct CreamCapsuleButton: View {
    var text: String
    var onClick: () -> Void

    private let charcoal = Color(hex: 0x2C2723)
    private let glow = Color(hex: 0xF3E4C0)

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                Image(systemName: "star.fill")
                    .font(.system(size: 20))
                Text(text)
                    .font(.minSans(16, .bold))
            }
            .foregroundStyle(charcoal)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 30)
            .padding(.vertical, 16)
        }
        .buttonStyle(CreamPressStyle())
        // 뒤로 번지는 은은한 후광(예전 크림 버튼의 0.55 에서 조금 높여 기존에 가깝게).
        .background(
            Capsule()
                .fill(glow.opacity(0.50))
                .blur(radius: 28)
                .padding(.horizontal, 4)
                .padding(.vertical, 6)
        )
    }
}

private struct CreamPressStyle: ButtonStyle {
    private let creamTop = Color(hex: 0xF7EDD8)
    private let creamBottom = Color(hex: 0xE9D6AE)

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        return configuration.label
            .background(
                LinearGradient(
                    colors: [creamTop.blended(with: .white, fraction: pressed ? 0.35 : 0).opacity(0.94),
                             creamBottom.blended(with: .white, fraction: pressed ? 0.25 : 0).opacity(0.94)],
                    startPoint: .top, endPoint: .bottom),
                in: Capsule()
            )
            .overlay(
                Capsule().strokeBorder(
                    LinearGradient(colors: [Color.white.opacity(0.75), Color.white.opacity(0.10)],
                                   startPoint: .top, endPoint: .bottom),
                    lineWidth: 1)
            )
            .scaleEffect(pressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.12), value: pressed)
            .onChange(of: pressed) { isDown in
                if isDown { Haptics.soft() }   // 터치 down 순간 진동
            }
    }
}
