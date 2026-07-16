import CoreGraphics
import SwiftUI
import UIKit

/// 별 내부를 "수정 결정(크리스탈)"로 채우는 렌더러 — Android `StarStyle.drawCrystalFill` 의 Swift 포팅.
///
/// 실루엣([StarShape])은 그대로 clip 하고 내부만 **불규칙 파편 메시**로 갈라 조각마다 색을 달리한다.
/// 방사형 "패턴"이 생기지 않도록:
///  · 중심은 한 점이 아니라 불규칙 코어 다각형(스포크/부챗살 소멸)
///  · 링 3겹의 꼭짓점 각도를 서로 어긋나게(중간 링은 반 스텝 시프트) + 강한 지터
///    → 경계선이 한 줄로 이어지지 않아 그냥 "깨진 조각"으로 읽힌다
///  · 명도는 중심(밝음)→가장자리(어두움) 구배 = 가운데가 볼록한 돔 셰이딩
///
/// ⚠️ 좌표/색은 전부 결정론적 해시라 같은 별은 언제나 같은 무늬다(Android 와 같은 해시식).
///    수식/상수를 고칠 때는 Android `StarStyle.kt` 와 반드시 함께 맞출 것.
enum StarCrystal {

    /// 팔레트 색 인덱스로 그리기. [alpha] 는 전체 불투명도(0..1, 페이드용).
    static func draw(
        in cg: CGContext,
        type: Int,
        colorIndex: Int,
        rect: CGRect,
        alpha: CGFloat = 1
    ) {
        draw(in: cg, type: type, colors: colorsOf(colorIndex), rect: rect, alpha: alpha)
    }

    /// 색 직접 지정 버전(1색 또는 그라데이션 2색) — 팔레트 밖 색으로 그리는 곳용.
    static func draw(
        in cg: CGContext,
        type: Int,
        colors: [UIColor],
        rect: CGRect,
        alpha: CGFloat = 1
    ) {
        let size = min(rect.width, rect.height)
        guard size > 0, !colors.isEmpty, alpha > 0 else { return }

        let t = min(max(type, 0), StarStyle.typeCount - 1)
        let left = rect.midX - size / 2
        let top = rect.midY - size / 2
        let square = CGRect(x: left, y: top, width: size, height: size)
        drawMesh(
            in: cg, salt: t,
            silhouette: StarShape(type: t).path(in: square).cgPath,
            colors: colors, square: square, alpha: alpha
        )
    }

    /// 실루엣 없이 정사각형 전체를 파편으로 채운다 — 아이콘 알파 마스크(SRC_IN) 위에 얹는 용도.
    /// (Android `StarStyle.drawCrystalFacets(silhouette = null)` 패리티 — [seed] 는 아이콘 인덱스.)
    static func drawFacets(
        in cg: CGContext,
        seed: Int,
        colors: [UIColor],
        rect: CGRect,
        alpha: CGFloat = 1
    ) {
        let size = min(rect.width, rect.height)
        guard size > 0, !colors.isEmpty, alpha > 0 else { return }
        let square = CGRect(x: rect.midX - size / 2, y: rect.midY - size / 2, width: size, height: size)
        drawMesh(in: cg, salt: max(seed, 0), silhouette: nil, colors: colors, square: square, alpha: alpha)
    }

    /// 파편 메시 본체 — [salt] 는 해시 솔트(별 = 모양 타입, 아이콘 = 인덱스. Android 와 동일 계약).
    private static func drawMesh(
        in cg: CGContext,
        salt t: Int,
        silhouette: CGPath?,
        colors: [UIColor],
        square: CGRect,
        alpha: CGFloat
    ) {
        let size = square.width
        let left = square.minX
        let top = square.minY
        let cx = square.midX
        let cy = square.midY
        let n = facetDensity(t)

        cg.saveGState()
        if let silhouette {
            // 실루엣 clip — 바깥 형태는 절대 건드리지 않는다(even-odd = 꽃/초승달/행성의 빈 공간 유지).
            cg.addPath(silhouette)
            cg.clip(using: .evenOdd)
        } else {
            cg.clip(to: square)
        }

        // ── 링 3겹 꼭짓점(어긋난 각도 + 지터) ─────────────────────────
        let step = 2 * Double.pi / Double(n)
        func jit(_ seed: Int) -> Double { Double(facetHash(seed)) - 0.5 }

        var p0 = [CGPoint](repeating: .zero, count: n + 1) // 코어 다각형
        var p1 = [CGPoint](repeating: .zero, count: n + 1) // 중간 링(반 스텝 시프트)
        var p2 = [CGPoint](repeating: .zero, count: n + 1) // 외곽(실루엣 밖 — clip 이 마무리)
        for k in 0..<n {
            let a0 = Double(k) * step + jit(k * 17 + t * 57 + 3) * step * 0.7
            let a1 = (Double(k) + 0.5) * step + jit(k * 29 + t * 71 + 5) * step * 0.7
            let a2 = Double(k) * step + jit(k * 41 + t * 13 + 9) * step * 0.7
            let r0 = size * CGFloat(0.09 + 0.10 * facetHash(k * 23 + t * 91 + 11))
            let r1 = size * CGFloat(0.24 + 0.15 * facetHash(k * 37 + t * 143 + 17))
            p0[k] = CGPoint(x: cx + CGFloat(cos(a0)) * r0, y: cy + CGFloat(sin(a0)) * r0)
            p1[k] = CGPoint(x: cx + CGFloat(cos(a1)) * r1, y: cy + CGFloat(sin(a1)) * r1)
            p2[k] = CGPoint(x: cx + CGFloat(cos(a2)) * size, y: cy + CGFloat(sin(a2)) * size)
        }
        p0[n] = p0[0]; p1[n] = p1[0]; p2[n] = p2[0]

        // ── 파편 채우기 ────────────────────────────────────────────
        let edges = CGMutablePath()

        func drawShard(_ seed: Int, _ ringBias: CGFloat, _ pts: [CGPoint]) {
            guard pts.count >= 3 else { return }
            let path = CGMutablePath()
            path.move(to: pts[0])
            for i in 1..<pts.count { path.addLine(to: pts[i]) }
            path.closeSubpath()
            let center = pts.reduce(CGPoint.zero) { CGPoint(x: $0.x + $1.x, y: $0.y + $1.y) }
            let m = CGFloat(pts.count)
            let mid = CGPoint(x: center.x / m, y: center.y / m)
            let color = shardColor(
                seed: seed, type: t, colors: colors, point: mid,
                origin: CGPoint(x: left, y: top), size: size, ringBias: ringBias
            )
            cg.setFillColor(color.withAlphaComponent(alpha).cgColor)
            cg.addPath(path)
            cg.fillPath()
            edges.addPath(path)
        }

        // 1) 중심 코어 다각형 — 가장 밝게(볼록의 정점)
        drawShard(1, 0.15, Array(p0[0..<n]))

        // 2) 코어→중간 / 3) 중간→외곽 — 엇갈린 삼각형 스트립(공유 꼭짓점 없음 = 무패턴)
        for k in 0..<n {
            drawShard(100 + k * 2, 0.05, [p0[k], p1[k], p0[k + 1]])
            drawShard(101 + k * 2, 0.02, [p1[k], p0[k + 1], p1[k + 1]])
            drawShard(400 + k * 2, -0.05, [p1[k], p2[k], p1[k + 1]])
            drawShard(401 + k * 2, -0.09, [p2[k], p1[k + 1], p2[k + 1]])
        }

        // 능선 — 모든 파편 경계를 한 번에 옅은 흰 선으로
        cg.setStrokeColor(UIColor.white.withAlphaComponent(40.0 / 255.0 * alpha).cgColor)
        cg.setLineWidth(max(size * 0.011, 0.7))
        cg.addPath(edges)
        cg.strokePath()

        // ── 볼록 돔 셰이딩 ────────────────────────────────────────
        drawDomeShading(in: cg, center: CGPoint(x: cx, y: cy), size: size, alpha: alpha)

        cg.restoreGState()
    }

    /// 좌상단으로 치우친 하이라이트(빛을 받는 볼록면) + 가장자리로 가라앉는 음영(돔의 낙조면).
    private static func drawDomeShading(in cg: CGContext, center: CGPoint, size: CGFloat, alpha: CGFloat) {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB) else { return }

        let highlight = [
            UIColor.white.withAlphaComponent(85.0 / 255.0 * alpha).cgColor,
            UIColor.white.withAlphaComponent(0).cgColor,
        ]
        if let g = CGGradient(colorsSpace: space, colors: highlight as CFArray, locations: [0, 1]) {
            let c = CGPoint(x: center.x - size * 0.10, y: center.y - size * 0.12)
            cg.drawRadialGradient(g, startCenter: c, startRadius: 0,
                                  endCenter: c, endRadius: size * 0.45,
                                  options: .drawsAfterEndLocation)
        }

        let shade = UIColor(red: 0, green: 0, blue: 20.0 / 255.0, alpha: 70.0 / 255.0 * alpha)
        let edge = [
            UIColor.clear.cgColor,
            UIColor.clear.cgColor,
            shade.cgColor,
        ]
        if let g = CGGradient(colorsSpace: space, colors: edge as CFArray, locations: [0, 0.55, 1]) {
            cg.drawRadialGradient(g, startCenter: center, startRadius: 0,
                                  endCenter: center, endRadius: size * 0.5,
                                  options: .drawsAfterEndLocation)
        }
    }

    // MARK: - 조각 색

    /// 파편 색 — 명도 = 돔 구배(ringBias) + 해시 변주, 색상 ±24°, 채도 0.8~1.2, 6% 글린트(백색 혼합).
    private static func shardColor(
        seed: Int, type t: Int, colors: [UIColor],
        point: CGPoint, origin: CGPoint, size: CGFloat, ringBias: CGFloat
    ) -> UIColor {
        let base: UIColor
        if colors.count >= 2 {
            // 그라데이션은 좌상→우하 방향 투영으로 두 색을 섞는다(Android 와 동일).
            let f = (((point.x - origin.x) + (point.y - origin.y)) / (2 * size)).clamped(0, 1)
            base = colors[0].blended(with: colors[1], fraction: f)
        } else {
            base = colors[0]
        }

        let h1 = facetHash(seed * 7 + t * 131 + 1)
        let h2 = facetHash(seed * 13 + t * 29 + 7)
        let h3 = facetHash(seed * 31 + t * 173 + 19)
        if h3 > 0.94 { return base.blended(with: .white, fraction: 0.55) } // 글린트

        var hsl = base.hsl
        hsl.h = (hsl.h + CGFloat(h1 - 0.5) * 48 + 360).truncatingRemainder(dividingBy: 360)
        hsl.s = (hsl.s * CGFloat(0.80 + 0.40 * h2)).clamped(0, 1)
        hsl.l = (hsl.l + ringBias + CGFloat(h1 - 0.5) * 0.18).clamped(0.05, 0.97)
        return UIColor(hsl: hsl)
    }

    /// 팔레트 색 인덱스 → 채움 색(단색 1개 / 그라데이션 2개).
    static func colorsOf(_ index: Int) -> [UIColor] {
        if let g = StarStyle.gradient(index) { return [UIColor(g.0), UIColor(g.1)] }
        return [UIColor(StarStyle.color(index))]
    }

    /// 크리스탈 별을 비트맵으로 구워 캐시한다 — Canvas 안에 Canvas 를 중첩할 수 없는 자리
    /// (예: `Canvas(symbols:)` 심볼)에서 `Image(uiImage:)` 로 쓰기 위한 경로.
    static func image(type: Int, colorIndex: Int, size: CGFloat) -> UIImage {
        let key = "\(type)-\(colorIndex)-\(Int(size.rounded()))" as NSString
        if let hit = imageCache.object(forKey: key) { return hit }
        let rect = CGRect(x: 0, y: 0, width: size, height: size)
        let img = UIGraphicsImageRenderer(size: rect.size).image { ctx in
            draw(in: ctx.cgContext, type: type, colorIndex: colorIndex, rect: rect)
        }
        imageCache.setObject(img, forKey: key)
        return img
    }

    private static let imageCache = NSCache<NSString, UIImage>()

    /// SF Symbol 실루엣을 크리스탈 파편으로 채운 아이콘을 굽는다 — Android `bakeCrystalIcon` 패리티.
    /// 아이콘을 먼저 그려 알파 마스크로 쓰고, 그 위에 파편 사각형을 `sourceIn` 으로 얹어
    /// 아이콘 모양 안에만 파편이 남는다. 무늬는 정적이므로 1회만 굽고 캐시한다.
    static func iconImage(systemName: String, color: UIColor, seed: Int, size: CGFloat) -> UIImage {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        let key = "icon-\(systemName)-\(seed)-\(Int(size.rounded()))-" +
            "\(Int(r * 255))-\(Int(g * 255))-\(Int(b * 255))" as NSString
        if let hit = imageCache.object(forKey: key) { return hit }

        let rect = CGRect(x: 0, y: 0, width: size, height: size)
        let facets = UIGraphicsImageRenderer(size: rect.size).image { ctx in
            drawFacets(in: ctx.cgContext, seed: seed, colors: [color], rect: rect)
        }
        let cfg = UIImage.SymbolConfiguration(pointSize: size, weight: .regular)
        let symbol = UIImage(systemName: systemName, withConfiguration: cfg)?
            .withTintColor(.white, renderingMode: .alwaysOriginal)
        let img = UIGraphicsImageRenderer(size: rect.size).image { _ in
            guard let symbol, symbol.size.width > 0, symbol.size.height > 0 else { return }
            let s = min(rect.width / symbol.size.width, rect.height / symbol.size.height)
            let w = symbol.size.width * s
            let h = symbol.size.height * s
            symbol.draw(in: CGRect(x: (rect.width - w) / 2, y: (rect.height - h) / 2, width: w, height: h))
            facets.draw(in: rect, blendMode: .sourceIn, alpha: 1)
        }
        imageCache.setObject(img, forKey: key)
        return img
    }

    // MARK: - 메시 파라미터

    /// 파편 메시 밀도(링당 꼭짓점 수) — 실제 조각 수 = 1(코어) + 4n.
    /// 각도는 전부 지터되므로 별 꼭지 정렬 개념은 없다(불규칙 파편이 목적).
    private static func facetDensity(_ type: Int) -> Int {
        switch type {
        case 0, 4: return 10
        case 1, 6, 7: return 10
        case 2, 5: return 12
        case 3: return 14
        default: return 12
        }
    }

    /// 0..1 결정론적 해시 — 같은 조각은 항상 같은 색/좌표(렌더마다 흔들리지 않게).
    /// Android `StarStyle.facetHash` 와 같은 식이라 두 플랫폼의 무늬가 동일하다.
    private static func facetHash(_ seed: Int) -> Double {
        let x = sin(Double(seed) * 12.9898) * 43758.5453
        return x - floor(x)
    }
}

// MARK: - 색 유틸

/// HSL 삼중항 — Android `ColorUtils.colorToHSL/HSLToColor` 와 같은 정의(h 0..360, s/l 0..1).
struct HSL {
    var h: CGFloat
    var s: CGFloat
    var l: CGFloat
}

extension UIColor {
    /// RGB → HSL (androidx ColorUtils 와 동일 공식).
    var hsl: HSL {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        let maxC = Swift.max(r, g, b)
        let minC = Swift.min(r, g, b)
        let delta = maxC - minC
        let l = (maxC + minC) / 2

        var h: CGFloat = 0
        var s: CGFloat = 0
        if delta > 0 {
            s = l > 0.5 ? delta / (2 - maxC - minC) : delta / (maxC + minC)
            switch maxC {
            case r: h = (g - b) / delta + (g < b ? 6 : 0)
            case g: h = (b - r) / delta + 2
            default: h = (r - g) / delta + 4
            }
            h *= 60
        }
        return HSL(h: h, s: s, l: l)
    }

    /// HSL → RGB.
    convenience init(hsl: HSL) {
        let h = hsl.h.truncatingRemainder(dividingBy: 360) / 360
        let s = hsl.s
        let l = hsl.l
        guard s > 0 else {
            self.init(red: l, green: l, blue: l, alpha: 1)
            return
        }
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        func hue(_ tRaw: CGFloat) -> CGFloat {
            var t = tRaw
            if t < 0 { t += 1 }
            if t > 1 { t -= 1 }
            if t < 1.0 / 6.0 { return p + (q - p) * 6 * t }
            if t < 1.0 / 2.0 { return q }
            if t < 2.0 / 3.0 { return p + (q - p) * (2.0 / 3.0 - t) * 6 }
            return p
        }
        self.init(red: hue(h + 1.0 / 3.0), green: hue(h), blue: hue(h - 1.0 / 3.0), alpha: 1)
    }

    /// 두 색을 [fraction] 비율로 섞는다(0 = self, 1 = other). Android ColorUtils.blendARGB 패리티.
    func blended(with other: UIColor, fraction: CGFloat) -> UIColor {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let f = fraction.clamped(0, 1)
        return UIColor(
            red: r1 + (r2 - r1) * f,
            green: g1 + (g2 - g1) * f,
            blue: b1 + (b2 - b1) * f,
            alpha: a1 + (a2 - a1) * f
        )
    }
}

extension CGFloat {
    func clamped(_ lo: CGFloat, _ hi: CGFloat) -> CGFloat { Swift.min(Swift.max(self, lo), hi) }
}
