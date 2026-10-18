import SwiftUI
import UIKit

// 정지된 배경 화면(지도 제외)의 반짝이는 잔별 — 로그인 화면 하늘(`LivingSkyView`)과 같은 별을 **더 어둡게**.
// Android `core/ui/AmbientStarSky.kt` 와 같은 값/식(별 배치도 `JavaRandom` 으로 같은 시드 → 같은 위치).

/// 배경 잔별 1개. 좌표는 화면 비율(0..1), 반지름 pt(= Android dp).
private struct AmbientStar {
    let x: Float, y: Float, r: Float
    let speed: Float, phase: Float
    let color: Color
    let sparkle: Bool
}

private enum AmbientSky {
    static let starCount = 72
    static let sparkleCount = 5
    /// 로그인 하늘 대비 밝기 — 배경(검정 틴트 0.82~0.86)이 이미 아주 어두워 은은하게만 보이게.
    static let brightness = 0.42
    /// 화면에 들어올 때 별이 나타나는 시간(초).
    static let fadeIn = 0.9

    static let colors: [Color] = [
        Color(hex: 0xFFFFFF), Color(hex: 0xFFFFFF), Color(hex: 0xFFFFFF), Color(hex: 0xBFD8FF), Color(hex: 0xBFD8FF),
        Color(hex: 0xFFE9B8), Color(hex: 0xFFC2E2),
    ]

    /// Android `ambientStars` 와 **같은 난수 호출 순서**(x, y, r, speed, phase, color) — 순서를 바꾸면 배치가 달라진다.
    static let stars: [AmbientStar] = {
        var rnd = JavaRandom(20261019)
        return (0..<starCount).map { i in
            let sparkle = i < sparkleCount
            let x = rnd.nextFloat()
            let y = rnd.nextFloat()
            let r: Float = sparkle ? 0.8 + rnd.nextFloat() * 0.5 : 0.4 + rnd.nextFloat() * 0.55
            let speed = 0.4 + rnd.nextFloat() * 1.0
            let phase = rnd.nextFloat() * (2 * Float.pi)
            let color = colors[Int(rnd.nextInt(Int32(colors.count)))]
            return AmbientStar(x: x, y: y, r: r, speed: speed, phase: phase, color: color, sparkle: sparkle)
        }
    }()
}

/// 천천히 숨 쉬듯 반짝이는 잔별(일부는 짧은 빛줄기) — 화면 전체에 흩어져 있고 로그인 하늘보다 훨씬 어둡다. 유성은 없다.
/// `ScreenBackground` 가 배경 이미지 위에 깐다. 화면에 들어오면 0.9초에 걸쳐 나타난다.
struct AmbientStarSkyView: View {
    /// 스크롤 시차(×`BackgroundScroll.starParallax`). 매 프레임 그리므로 관찰하지 않고 그릴 때 값만 읽는다.
    var scroll: BackgroundScroll? = nil
    @State private var t0 = Date.timeIntervalSinceReferenceDate
    @State private var shown = false

    var body: some View {
        TimelineView(.animation) { tl in
            Canvas { ctx, size in
                let t = tl.date.timeIntervalSinceReferenceDate - t0
                let w = size.width, h = size.height
                guard h >= 1 else { return }
                let a = AmbientSky.brightness
                let shift = (scroll?.offset ?? 0) * BackgroundScroll.starParallax
                for s in AmbientSky.stars {
                    let tw = 0.5 + 0.5 * sin(t * Double(s.speed) + Double(s.phase))
                    let k = 0.30 + 0.70 * tw
                    // 스크롤 시차 — 위로 밀려 나간 별은 아래에서 다시 들어온다(화면 높이로 감싼다).
                    let y = ((CGFloat(s.y) * h - shift).truncatingRemainder(dividingBy: h) + h).truncatingRemainder(dividingBy: h)
                    let c = CGPoint(x: CGFloat(s.x) * w, y: y)
                    let r = CGFloat(s.r)
                    if s.sparkle {
                        SparkleStar.draw(ctx, c, r, s.color, k, t * 0.12 + Double(s.phase), dim: a)
                    } else {
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                                 with: .color(s.color.opacity(min(max(0.80 * k * a, 0), 1))))
                    }
                }
            }
        }
        .opacity(shown ? 1 : 0)
        .onAppear {
            t0 = Date.timeIntervalSinceReferenceDate
            withAnimation(.easeOut(duration: AmbientSky.fadeIn)) { shown = true }
        }
        .allowsHitTesting(false)
    }
}

/// 큰 잔별 — 가장자리가 부드럽게 사라지는 작은 번짐 + 심지 + **짧고 가는** 빛줄기(가로가 세로보다 길다). Android `drawSparkleStar` 와 같은 값.
/// `k` 는 반짝임(0..1 — 빛줄기 길이와 밝기), `dim` 은 밝기에만 곱한다(배경 잔별을 어둡게 할 때 빛줄기 길이는 그대로).
/// 로그인 하늘(`LivingSkyView`)과 배경 잔별이 함께 쓴다.
/// (처음엔 딱 떨어지는 반투명 원판 후광 + 긴 십자선이라 부자연스러웠다 — 방사형 그라데이션으로 바꾸고 반 이하로 줄였다.)
enum SparkleStar {
    static func draw(_ ctx: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color, _ k: Double, _ angle: Double,
                     dim: Double = 1) {
        let a = min(max(k * dim, 0), 1)
        let glowR = r * 3.4
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - glowR, y: c.y - glowR, width: glowR * 2, height: glowR * 2)),
                 with: .radialGradient(Gradient(colors: [color.opacity(0.22 * a), .clear]),
                                       center: c, startRadius: 0, endRadius: glowR))
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(color.opacity(0.95 * a)))
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
}

// MARK: - 배경 시차 스크롤

/// 배경 시차용 스크롤 값 — 화면이 `@State private var bgScroll = BackgroundScroll()` 로 들고(관찰하지 않아 화면 본문은 다시 그리지 않는다)
/// `ScreenBackground(scroll:)` 와 세로 `ScrollView` 의 `.backgroundScrollSource(_:)` 에 같이 넘긴다. Android `BackgroundScroll` 대응.
final class BackgroundScroll: ObservableObject {
    /// 콘텐츠가 위로 올라간 거리(pt) = 스크롤뷰 contentOffset.y(+ 위쪽 inset).
    @Published var offset: CGFloat = 0

    /// Android `BG_PARALLAX` / `STAR_PARALLAX` 와 같은 값.
    static let imageParallax: CGFloat = 0.12
    static let starParallax: CGFloat = 0.20
    /// 스크롤 없는 화면용(값이 바뀌지 않는다).
    static let still = BackgroundScroll()
}

extension View {
    /// 이 세로 `ScrollView` 의 스크롤 위치를 배경 시차에 알린다 — **ScrollView 바로 뒤에** 붙인다(탐침 영역 = 스크롤뷰 영역).
    func backgroundScrollSource(_ scroll: BackgroundScroll) -> some View {
        background(BackgroundScrollProbe(scroll: scroll).allowsHitTesting(false))
    }
}

/// iOS 16 SwiftUI 는 ScrollView 오프셋을 직접 주지 않는다 → 같은 자리(영역이 가장 많이 겹치는)의 `UIScrollView` 를 찾아
/// `contentOffset` 을 KVO 로 받는다. 텍스트뷰(UITextView 도 UIScrollView)는 제외.
private struct BackgroundScrollProbe: UIViewRepresentable {
    let scroll: BackgroundScroll

    func makeUIView(context: Context) -> ProbeView {
        let v = ProbeView()
        v.isUserInteractionEnabled = false
        v.backgroundColor = .clear
        v.scroll = scroll
        return v
    }

    func updateUIView(_ uiView: ProbeView, context: Context) {
        uiView.scroll = scroll
    }

    final class ProbeView: UIView {
        weak var scroll: BackgroundScroll?
        private weak var target: UIScrollView?
        private var observation: NSKeyValueObservation?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window == nil { detach() } else { setNeedsLayout() }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            if target == nil || target?.window == nil { attach() }
        }

        private func detach() {
            observation?.invalidate()
            observation = nil
            target = nil
        }

        private func attach() {
            guard let window, bounds.width >= 1, bounds.height >= 1 else { return }
            detach()
            let mine = convert(bounds, to: window)
            var best: (view: UIScrollView, iou: CGFloat)?
            var stack: [UIView] = [window]
            while let v = stack.popLast() {
                if let sv = v as? UIScrollView, !(sv is UITextView), !sv.isHidden {
                    let r = sv.convert(sv.bounds, to: window)
                    let inter = r.intersection(mine)
                    if !inter.isNull {
                        let i = inter.width * inter.height
                        let iou = i / max(1, r.width * r.height + mine.width * mine.height - i)
                        if iou > 0.8, iou > (best?.iou ?? 0) { best = (sv, iou) }
                    }
                }
                stack.append(contentsOf: v.subviews)
            }
            guard let sv = best?.view else { return }
            target = sv
            observation = sv.observe(\.contentOffset, options: [.initial, .new]) { [weak self] sv, _ in
                let y = sv.contentOffset.y + sv.adjustedContentInset.top
                // 뷰 갱신 도중 발행 경고를 피하려고 다음 런루프에 반영(1프레임 이내 지연).
                DispatchQueue.main.async {
                    guard let s = self?.scroll, abs(s.offset - y) > 0.25 else { return }
                    s.offset = y
                }
            }
        }
    }
}
