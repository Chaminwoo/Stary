import QuartzCore
import SwiftUI
import UIKit

/// 크리스탈 별 · 아이콘의 **빛번짐 + 유리 결 빛** — 지도 별(`StarImageRenderer` 글로우 · `MapStarAnnotationView` sheen)과 같은 연출을
/// 앱 곳곳의 크리스탈(별 아이콘 · 크리스탈 아이콘 · 공방 결정 · 배지 · 자물쇠)에 입힌다(2026-10-28 사용자 요청).
///  - 빛번짐: 실루엣을 그 색으로 흐리게 번진 테두리 빛(그림자) + 아주 옅은 둥근 후광([CrystalHalo], 지도 후광 α .15).
///    요소 바깥으로 번져 그린다 — 레이아웃 크기는 그대로.
///  - 유리 결 빛: 요소마다 5.5~8초에 한 번, 흰 사선 빛줄기가 몸을 왼쪽 위 → 오른쪽 아래로 훑고 작은 굽은 반짝이가
///    그 빛을 타고 건너간다([CrystalSheenView] — Core Animation 이라 지나가지 않는 동안엔 CPU 를 쓰지 않는다).
///    매 프레임 다시 그리는 캔버스는 [drawSheen] 으로 같은 장면을 직접 그린다.
/// 쓰는 법: 크리스탈 그림 뷰에 `.crystalShine(.star(type) 또는 .image(img), color:, side:)`.
/// ⚠️ 유리 결 빛은 UIKit 뷰라 `Canvas(symbols:)` 심볼 · `ImageRenderer` · `drawingGroup()` 안에서는 그려지지 않는다
///    — 그런 자리는 [drawSheen] 으로 직접 그린다(FloatingStatBox · StarLogScreen 고리).
/// Android `CrystalShine.kt` 와 같은 값.
enum CrystalShine {
    /// 유리 결 빛 주기(초) = MIN + 0..SPREAD(요소마다), 지나가는 시간 = 주기 × WINDOW, 불투명도 OPACITY × sin.
    static let sheenPeriodMin = 5.5
    static let sheenPeriodSpread = 2.5
    static let sheenWindow = 0.26
    static let sheenOpacity = 0.75

    /// 테두리 빛번짐(그림자) 반경 · 진하기 — 몸 한 변 대비(예전 `StarView` 글로우 값 그대로).
    static let glowRadius: CGFloat = 0.22
    static let glowOpacity = 0.8
    /// 둥근 후광 반지름(몸 한 변 대비) · 가운데 불투명도(지도 후광 α .15).
    static let haloRadius: CGFloat = 1.0
    static let haloAlpha = 0.15

    // MARK: 때(유리 결 빛)

    /// 요소마다 다른 주기(초) — [seed] 해시.
    static func sheenPeriod(seed: Int) -> Double { sheenPeriodMin + hash01(seed, 0x51ED27) * sheenPeriodSpread }

    /// 요소마다 다른 위상(0..1).
    static func sheenPhase(seed: Int) -> Double { hash01(seed, 0x2C1B3F) }

    /// 단조 시계(초) — 유리 결 빛의 기준 시각.
    static func now() -> Double { CACurrentMediaTime() }

    /// [t] 초에 유리 결 빛 진행(0..1) — 지나가는 중이 아니면 -1. 매 프레임 그리는 캔버스용.
    static func sheenAt(_ t: Double, seed: Int) -> Double {
        let x = t / sheenPeriod(seed: seed) + sheenPhase(seed: seed)
        let cyc = x - floor(x)
        return cyc < sheenWindow ? cyc / sheenWindow : -1
    }

    /// 0..1 해시(murmur3 마무리 섞기) — Android `hash01` 과 같은 식(같은 seed 면 두 플랫폼이 같은 때에 빛난다).
    private static func hash01(_ seed: Int, _ salt: UInt32) -> Double {
        var h = UInt32(truncatingIfNeeded: seed) &* 0x9E37_79B9 &+ salt
        h ^= h >> 16
        h &*= 0x85EB_CA6B
        h ^= h >> 13
        h &*= 0xC2B2_AE35
        h ^= h >> 16
        return Double(h >> 8) / 16_777_216.0
    }

    // MARK: 반짝이

    private static var sparkleCache: [Int: UIImage] = [:]

    /// 빛을 타고 가는 굽은 4갈래 반짝 + 둥근 번짐(반지름 [r]) — 지도 sheen · 앱 곳곳의 유리 결 빛이 같이 쓴다.
    static func sparkleImage(radius r: CGFloat) -> UIImage {
        let key = Int((r * 4).rounded())
        if let cached = sparkleCache[key] { return cached }
        let side = r * 2
        let fmt = UIGraphicsImageRendererFormat()
        fmt.opaque = false
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: fmt).image { ctx in
            let cg = ctx.cgContext
            let c = CGPoint(x: r, y: r)
            if let grad = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                     colors: [UIColor.white.withAlphaComponent(0.5).cgColor,
                                              UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                                     locations: [0, 1]) {
                cg.drawRadialGradient(grad, startCenter: c, startRadius: 0, endCenter: c, endRadius: r * 0.9, options: [])
            }
            cg.setFillColor(UIColor.white.cgColor)
            cg.addPath(sparklePath(c, r).cgPath)
            cg.fillPath()
        }
        sparkleCache[key] = img
        return img
    }

    /// 굽은 4갈래 반짝 경로(수정구 시안 sparkleR — 지도 sheen 반짝과 같은 곡률).
    static func sparklePath(_ c: CGPoint, _ r: CGFloat) -> Path {
        let k = r * 0.10
        var p = Path()
        p.move(to: CGPoint(x: c.x, y: c.y - r))
        p.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: CGPoint(x: c.x + k, y: c.y - k))
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: CGPoint(x: c.x + k, y: c.y + k))
        p.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: CGPoint(x: c.x - k, y: c.y + k))
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: CGPoint(x: c.x - k, y: c.y - k))
        p.closeSubpath()
        return p
    }

    // MARK: 캔버스에 직접

    /// 유리 결 빛 한 장면(진행 [q] 0..1)을 캔버스에 직접 — [mask] 가 그린 알파 안에서만 흰 사선 빛줄기가 지나가고,
    /// 작은 굽은 반짝이가 그 빛을 탄다. [body] = 몸 정사각 자리(화면 기준 — 빛 방향은 회전과 무관).
    /// [mask] 는 받은 컨텍스트에 몸 실루엣을 불투명하게 그린다(회전 · 이동은 그 안에서 자유롭게).
    /// 수치는 지도 시안 drawSheen(마커 한 변 = 몸 / 0.78 인 128 캔버스에서 띠 중심 -46 + q·220, 반폭 30) 그대로 — Android `drawSheen`.
    static func drawSheen(
        in context: GraphicsContext,
        q: Double,
        body: CGRect,
        alpha: Double = 1,
        mask: (inout GraphicsContext) -> Void
    ) {
        let size = body.width
        guard q >= 0, size >= 2 else { return }
        let e = sin(q * .pi)
        let a = sheenOpacity * e * alpha
        guard a >= 0.004 else { return }
        let marker = size / 0.78
        let p = CGFloat(-46 + q * 220) / 128 * marker - (marker - size) / 2
        let hw = marker * 30 / 128
        let area = body.insetBy(dx: -size * 0.3, dy: -size * 0.3)
        context.drawLayer { layer in
            var shape = layer
            mask(&shape)
            var band = layer
            band.blendMode = .sourceIn
            band.fill(Path(area), with: .linearGradient(
                Gradient(stops: [
                    Gradient.Stop(color: Color.white.opacity(0), location: 0),
                    Gradient.Stop(color: Color.white.opacity(0.95 * a), location: 0.5),
                    Gradient.Stop(color: Color.white.opacity(0), location: 1),
                ]),
                startPoint: CGPoint(x: body.minX + p - hw, y: body.minY + p - hw),
                endPoint: CGPoint(x: body.minX + p + hw, y: body.minY + p + hw)
            ))
        }
        // 빛을 타고 건너가는 반짝(중심에서 (q - 0.5)·0.62·크기, 반지름 0.17·크기·sin)
        let rr = 0.17 * size * CGFloat(e)
        guard rr > 0.5 else { return }
        let off = CGFloat(q - 0.5) * 0.62 * size
        let c = CGPoint(x: body.midX + off, y: body.midY + off)
        let gr = rr * 0.9
        context.fill(
            Path(ellipseIn: CGRect(x: c.x - gr, y: c.y - gr, width: gr * 2, height: gr * 2)),
            with: .radialGradient(
                Gradient(colors: [Color.white.opacity(0.5 * e * a), Color.white.opacity(0)]),
                center: c, startRadius: 0, endRadius: gr
            )
        )
        context.fill(sparklePath(c, rr), with: .color(Color.white.opacity(0.95 * e * a)))
    }
}

/// 유리 결 빛이 지나갈 몸 실루엣 — 별 모양 번호 또는 구운 크리스탈 그림(알파).
enum CrystalMask: Equatable {
    case star(Int)
    case image(UIImage)

    static func == (a: CrystalMask, b: CrystalMask) -> Bool {
        switch (a, b) {
        case let (.star(x), .star(y)): return x == y
        case let (.image(x), .image(y)): return x === y
        default: return false
        }
    }
}

/// 유리 결 빛 겹(UIKit) — 몸 실루엣([CrystalMask]) 안에서만 흰 사선 빛줄기가 지나가고, 굽은 반짝이가 그 빛을 탄다.
/// 지도 별 sheen(`MapStarAnnotationView.installSheen`)과 같은 Core Animation 구성. 몸 한 변 [refSide] 기준으로 한 번 짓고
/// 크기는 배율로만 맞춘다(크기가 움직여도 다시 짓지 않는다). 시각은 전역 시계로 맞춰, 애니메이션을 다시 걸어도 위상이 튀지 않는다.
final class CrystalSheenView: UIView {
    private static let refSide: CGFloat = 128

    private let seed: Int
    private var silhouette: CrystalMask?
    /// 진하기(색 알파) — 애니메이션이 도는 host 의 opacity 와 따로 곱한다.
    private let dim = CALayer()
    private let host = CALayer()
    private let bandHost = CALayer()
    private let band = CAGradientLayer()
    private let spark = CALayer()
    private var anims: [(CALayer, String, CAKeyframeAnimation)] = []

    init(seed: Int) {
        self.seed = seed
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        host.opacity = 0
        spark.opacity = 0
        bandHost.addSublayer(band)
        host.addSublayer(bandHost)
        host.addSublayer(spark)
        dim.addSublayer(host)
        layer.addSublayer(dim)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// 실루엣 · 진하기(색 알파 — 잠김 · 흐림만큼 같이 옅어진다). 실루엣이 바뀔 때만 다시 짓는다.
    func configure(mask: CrystalMask, strength: CGFloat) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        dim.opacity = Float(min(max(strength, 0), 1))
        CATransaction.commit()
        guard mask != silhouette else { return }
        silhouette = mask
        build(mask)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let side = min(bounds.width, bounds.height)
        let k = side / Self.refSide
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        dim.frame = bounds
        host.bounds = CGRect(x: 0, y: 0, width: Self.refSide, height: Self.refSide)
        host.position = CGPoint(x: bounds.midX, y: bounds.midY)
        host.transform = CATransform3DMakeScale(k, k, 1)
        host.isHidden = side < 2
        CATransaction.commit()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        installAnimations()
    }

    @objc private func appWillEnterForeground() {
        installAnimations()
    }

    /// 몸 = host 전체(refSide 정사각). 지도 마커 좌표(몸 = 마커 한 변의 78%)로 시안 수치를 그대로 쓴다.
    private func build(_ mask: CrystalMask) {
        let side = Self.refSide
        let body = CGRect(x: 0, y: 0, width: side, height: side)
        let marker = side / 0.78
        let origin = (side - marker) / 2
        func bandAt(_ q: Double) -> CGPoint {
            let p = origin + CGFloat(-46 + q * 220) / 128 * marker
            return CGPoint(x: p, y: p)
        }
        let rMax = 0.17 * side

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        bandHost.frame = body
        bandHost.mask = Self.maskLayer(mask, body: body)
        // 빛줄기 — 사선(↘) 그라데이션. 큰 정사각 레이어의 대각선 가운데에 띠를 둔다(반폭 30/128 마커).
        let hw = marker * 30 / 128
        let big = marker * 3
        band.bounds = CGRect(x: 0, y: 0, width: big, height: big)
        band.startPoint = CGPoint(x: 0, y: 0)
        band.endPoint = CGPoint(x: 1, y: 1)
        let a = Double(hw / big)
        let clear = UIColor.white.withAlphaComponent(0).cgColor
        band.colors = [clear, clear, UIColor.white.withAlphaComponent(0.95).cgColor, clear, clear]
        band.locations = [0, NSNumber(value: 0.5 - a), 0.5, NSNumber(value: 0.5 + a), 1]
        band.position = bandAt(0)
        spark.bounds = CGRect(x: 0, y: 0, width: rMax * 2, height: rMax * 2)
        spark.contents = CrystalShine.sparkleImage(radius: rMax).cgImage
        CATransaction.commit()

        // 지나가는 동안(주기의 26%)을 24단계로 — 나머지는 다음 주기까지 쉰다.
        let w = CrystalShine.sheenWindow
        let steps = 24
        var keyTimes: [NSNumber] = []
        var hostAlpha: [NSNumber] = []
        var sparkPos: [NSValue] = []
        var sparkScale: [NSNumber] = []
        var sparkAlpha: [NSNumber] = []
        for i in 0...steps {
            let q = Double(i) / Double(steps)
            let e = sin(q * .pi)
            keyTimes.append(NSNumber(value: q * w))
            hostAlpha.append(NSNumber(value: CrystalShine.sheenOpacity * e))
            let c = side / 2 + CGFloat(q - 0.5) * 0.62 * side
            sparkPos.append(NSValue(cgPoint: CGPoint(x: c, y: c)))
            sparkScale.append(NSNumber(value: max(e, 0.001)))
            sparkAlpha.append(NSNumber(value: 0.95 * e))
        }
        keyTimes.append(1)
        hostAlpha.append(0)
        sparkPos.append(sparkPos[sparkPos.count - 1])
        sparkScale.append(0.001)
        sparkAlpha.append(0)

        let period = CrystalShine.sheenPeriod(seed: seed)
        func keyframe(_ keyPath: String, _ values: [Any], _ times: [NSNumber]) -> CAKeyframeAnimation {
            let anim = CAKeyframeAnimation(keyPath: keyPath)
            anim.values = values
            anim.keyTimes = times
            anim.calculationMode = .linear
            anim.duration = period
            anim.repeatCount = .infinity
            anim.isRemovedOnCompletion = false
            return anim
        }
        let bandFrom = NSValue(cgPoint: bandAt(0))
        let bandTo = NSValue(cgPoint: bandAt(1))

        for (l, key, _) in anims { l.removeAnimation(forKey: key) }
        anims = [
            (host, "sheen-alpha", keyframe("opacity", hostAlpha, keyTimes)),
            (band as CALayer, "sheen-pos", keyframe("position", [bandFrom, bandTo, bandTo], [0, NSNumber(value: w), 1])),
            (spark, "sheen-pos", keyframe("position", sparkPos, keyTimes)),
            (spark, "sheen-scale", keyframe("transform.scale", sparkScale, keyTimes)),
            (spark, "sheen-op", keyframe("opacity", sparkAlpha, keyTimes)),
        ]
        installAnimations()
    }

    /// 빠져 있는 애니메이션만 다시 건다 — 위상은 전역 시계로(언제 걸어도 [CrystalShine.sheenAt] 과 같은 때).
    private func installAnimations() {
        guard window != nil, !anims.isEmpty else { return }
        let period = CrystalShine.sheenPeriod(seed: seed)
        let x = CrystalShine.now() / period + CrystalShine.sheenPhase(seed: seed)
        let offset = (x - floor(x)) * period
        for (l, key, anim) in anims where l.animation(forKey: key) == nil {
            anim.timeOffset = offset
            l.add(anim, forKey: key)
        }
    }

    private static func maskLayer(_ mask: CrystalMask, body: CGRect) -> CALayer {
        switch mask {
        case .star(let type):
            let shape = CAShapeLayer()
            shape.frame = body
            // 크리스탈 채움과 같은 even-odd(꽃 · 초승달 · 행성의 빈 공간 유지).
            shape.path = StarShape(type: StarStyle.safeType(type)).path(in: body).cgPath
            shape.fillRule = .evenOdd
            shape.fillColor = UIColor.white.cgColor
            return shape
        case .image(let image):
            let l = CALayer()
            l.frame = body
            l.contents = image.cgImage
            l.contentsGravity = .resizeAspect
            return l
        }
    }
}

/// SwiftUI 용 유리 결 빛 겹 — 요소 크기 그대로 덮는다(몸 = 가운데 정사각). [seed] 가 없으면 요소마다 아무 때나.
struct CrystalSheen: UIViewRepresentable {
    let mask: CrystalMask
    var seed: Int? = nil
    var strength: Double = 1

    func makeUIView(context: Context) -> CrystalSheenView {
        CrystalSheenView(seed: seed ?? Int.random(in: 0...Int(Int32.max)))
    }

    func updateUIView(_ view: CrystalSheenView, context: Context) {
        view.configure(mask: mask, strength: CGFloat(strength))
    }
}

/// 둥근 후광 — 몸 한 변 [side] 의 가운데에서 α .15 로 시작해 바깥으로 부드럽게 사라진다(지도 후광). Android `drawHalo`.
struct CrystalHalo: View {
    let color: Color
    let side: CGFloat

    var body: some View {
        let a = CrystalShine.haloAlpha
        let r = side * CrystalShine.haloRadius
        Circle()
            .fill(RadialGradient(
                gradient: Gradient(stops: [
                    Gradient.Stop(color: color.opacity(a), location: 0),
                    Gradient.Stop(color: color.opacity(a * 0.45), location: 0.45),
                    Gradient.Stop(color: color.opacity(0), location: 1),
                ]),
                center: .center, startRadius: 0, endRadius: r
            ))
            .frame(width: r * 2, height: r * 2)
            .allowsHitTesting(false)
    }
}

extension View {
    /// 크리스탈 그림(요소 가운데 한 변 [side] 정사각)에 빛번짐 + 유리 결 빛 — Android `CrystalImage` / `StarShapeIcon(shine)`.
    /// [mask] = 유리 결 빛이 지나갈 몸 실루엣, [color] = 번짐 색(알파만큼 전부 옅어진다),
    /// [halo] = 둥근 후광까지(뒤에 이미 후광을 깐 자리는 false). 회전 · 확대 · 이동 수정자는 이 뒤에 붙인다(빛도 같이 움직이게).
    func crystalShine(
        _ mask: CrystalMask,
        color: Color,
        side: CGFloat,
        glow: Bool = true,
        halo: Bool = true,
        seed: Int? = nil
    ) -> some View {
        modifier(CrystalShineModifier(mask: mask, color: color, side: side, glow: glow, halo: halo, seed: seed))
    }
}

private struct CrystalShineModifier: ViewModifier {
    let mask: CrystalMask
    let color: Color
    let side: CGFloat
    let glow: Bool
    let halo: Bool
    let seed: Int?

    func body(content: Content) -> some View {
        content
            .shadow(color: glow ? color.opacity(CrystalShine.glowOpacity) : .clear,
                    radius: glow ? side * CrystalShine.glowRadius : 0)
            .overlay(
                CrystalSheen(mask: mask, seed: seed, strength: Double(UIColor(color).cgColor.alpha))
                    .allowsHitTesting(false)
            )
            .background(CrystalHalo(color: color, side: side).opacity(halo ? 1 : 0))
    }
}
