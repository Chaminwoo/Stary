import SwiftUI
import UIKit

/// 별빛 공방 지도 별 오라(2026-10-22 리디자인) — **한 장의 스프라이트**를 지도와 미리보기가 똑같이 쓴다.
/// 스프라이트는 별 둘레(가운데 별 자리는 비움)에 그린 정지 그림이고, 움직임은 그리는 쪽이 회전(`Motion.spinDegPerSec`)·
/// 흔들림(`Motion.wobbleDeg`)·밝기 숨쉬기로 준다 — 지도에선 심볼 레이어의 icon-rotate/opacity, 미리보기에선 rotationEffect.
/// Android `StarlightAura.kt` 와 같은 패턴·좌표.
///
/// payload: `pattern`(아래) · `colors`(#RRGGBB 1~3) · `speed`(한 주기 초) · `count` · `tilt`(saturn, 도)
///  - halo / pulse : 스프라이트 없음(빛·퍼지는 고리는 원 레이어로)
///  - orbit     : 옅은 궤도 + count 개 위성(1 = 달, 2 = 쌍둥이 반짝이)
///  - fireflies : count 개 반딧불(반지름·각도를 흩뜨린 점)
///  - spiral    : 별가루 점으로 그린 나선 팔 둘
///  - saturn    : 기울인 겹고리 + 고리 위 작은 위성 — 회전 대신 살짝 흔들린다
///  - comet     : 둘레를 도는 혜성(밝은 머리 + 가늘어지며 사라지는 꼬리)
/// 모르는 pattern 은 nil(빛만) — 새 패턴을 DB 에 넣어도 이전 버전은 빛으로만 보인다.
enum StarlightAura {
    /// 스프라이트 변(px) — 지도 별 비트맵(160px)의 2.4배.
    static let spritePx: CGFloat = 384

    static let spritePatterns: Set<String> = ["orbit", "fireflies", "spiral", "saturn", "comet"]

    struct Motion {
        let spinDegPerSec: Double
        let wobbleDeg: Double
        let flicker: Bool
    }

    /// 패턴별 움직임 — speed(초) = 한 바퀴. saturn 은 돌지 않고 ±6° 흔들린다.
    static func motion(_ payload: [String: Any]) -> Motion {
        let speed = min(max((payload["speed"] as? NSNumber)?.doubleValue ?? 10, 2), 120)
        switch payload["pattern"] as? String {
        case "saturn": return Motion(spinDegPerSec: 0, wobbleDeg: 6, flicker: false)
        case "fireflies": return Motion(spinDegPerSec: 360 / speed, wobbleDeg: 0, flicker: true)
        case "orbit", "spiral", "comet": return Motion(spinDegPerSec: 360 / speed, wobbleDeg: 0, flicker: false)
        default: return Motion(spinDegPerSec: 0, wobbleDeg: 0, flicker: false)
        }
    }

    static func colors(_ payload: [String: Any]) -> [UIColor] {
        let cs: [UIColor] = ((payload["colors"] as? [Any]) ?? []).compactMap { v in
            guard let s = v as? String else { return nil }
            let h = s.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "")
            guard h.count == 6, let n = UInt32(h, radix: 16) else { return nil }
            return UIColor(red: CGFloat((n >> 16) & 0xFF) / 255, green: CGFloat((n >> 8) & 0xFF) / 255,
                           blue: CGFloat(n & 0xFF) / 255, alpha: 1)
        }
        return cs.isEmpty ? [UIColor(red: 0xBF / 255.0, green: 0xD8 / 255.0, blue: 1, alpha: 1)] : cs
    }

    /// 그림이 같은 payload 끼리 같은 키(speed 는 그림에 영향 없음).
    static func key(_ payload: [String: Any]) -> String {
        let p = payload["pattern"] as? String ?? ""
        let cs = ((payload["colors"] as? [Any]) ?? []).map { "\($0)" }.joined(separator: ",")
        let n = (payload["count"] as? NSNumber)?.intValue ?? -1
        let tilt = (payload["tilt"] as? NSNumber)?.doubleValue ?? -999
        return "\(p)|\(cs)|\(n)|\(tilt)"
    }

    private static let cache = NSCache<NSString, UIImage>()

    /// 스프라이트(가운데 별 자리는 비어 있다). 스프라이트가 없는 패턴이면 nil.
    static func sprite(_ payload: [String: Any], side: CGFloat = spritePx) -> UIImage? {
        guard let pattern = payload["pattern"] as? String, spritePatterns.contains(pattern) else { return nil }
        let k = "\(key(payload))|\(Int(side))" as NSString
        if let hit = cache.object(forKey: k) { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { ctx in
            draw(ctx.cgContext, pattern, payload, side)
        }
        cache.setObject(img, forKey: k)
        return img
    }

    // MARK: - 그리기

    private static func draw(_ c: CGContext, _ pattern: String, _ payload: [String: Any], _ s: CGFloat) {
        let cs = colors(payload)
        let cx = s / 2, cy = s / 2
        func col(_ i: Int) -> UIColor { cs[i % cs.count] }
        let count = (payload["count"] as? NSNumber)?.intValue
        let space = CGColorSpace(name: CGColorSpace.sRGB)!

        func radial(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ colors: [UIColor], _ locs: [CGFloat]) {
            guard let g = CGGradient(colorsSpace: space, colors: colors.map(\.cgColor) as CFArray, locations: locs) else { return }
            c.drawRadialGradient(g, startCenter: CGPoint(x: x, y: y), startRadius: 0, endCenter: CGPoint(x: x, y: y), endRadius: r, options: [])
        }
        func glowDot(_ x: CGFloat, _ y: CGFloat, _ core: CGFloat, _ glow: CGFloat, _ color: UIColor, _ coreColor: UIColor? = nil) {
            radial(x, y, glow, [color.withAlphaComponent(0.75), color.withAlphaComponent(0.25), color.withAlphaComponent(0)], [0, 0.45, 1])
            c.setFillColor((coreColor ?? color.blended(with: .white, fraction: 0.55)).cgColor)
            c.fillEllipse(in: CGRect(x: x - core, y: y - core, width: core * 2, height: core * 2))
        }
        func sparkle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ color: UIColor) {
            radial(x, y, r * 2.2, [color.withAlphaComponent(0.55), color.withAlphaComponent(0)], [0, 1])
            let k: CGFloat = 0.14
            let p = CGMutablePath()
            p.move(to: CGPoint(x: x, y: y - r))
            p.addQuadCurve(to: CGPoint(x: x + r, y: y), control: CGPoint(x: x + r * k, y: y - r * k))
            p.addQuadCurve(to: CGPoint(x: x, y: y + r), control: CGPoint(x: x + r * k, y: y + r * k))
            p.addQuadCurve(to: CGPoint(x: x - r, y: y), control: CGPoint(x: x - r * k, y: y + r * k))
            p.addQuadCurve(to: CGPoint(x: x, y: y - r), control: CGPoint(x: x - r * k, y: y - r * k))
            p.closeSubpath()
            c.setFillColor(color.blended(with: .white, fraction: 0.45).cgColor)
            c.addPath(p)
            c.fillPath()
        }
        func pt(_ deg: CGFloat, _ r: CGFloat) -> CGPoint {
            let a = deg * .pi / 180
            return CGPoint(x: cx + cos(a) * r, y: cy + sin(a) * r)
        }

        switch pattern {
        case "orbit":
            let n = min(max(count ?? 1, 1), 4)
            let r = s * 0.36
            // 궤도선 — 위성 쪽(0°)은 진하고 반대쪽은 옅어지는 쓸림(돌 때 꼬리처럼 보인다).
            c.setLineWidth(s * 0.008)
            let seg = 72
            for i in 0..<seg {
                let f = CGFloat(i) / CGFloat(seg)
                let d = 1 - abs(f - 0.5) * 2 // 0°·360° 에서 0, 180° 에서 1
                let a = 0.5 - 0.45 * d
                c.setStrokeColor((d < 0.5 ? col(0) : col(n - 1)).withAlphaComponent(a).cgColor)
                c.move(to: pt(f * 360, r))
                c.addLine(to: pt((f + 1 / CGFloat(seg)) * 360 + 0.4, r))
                c.strokePath()
            }
            for i in 0..<n {
                let p = pt(CGFloat(i) * 360 / CGFloat(n), r)
                if n == 1 { glowDot(p.x, p.y, s * 0.042, s * 0.11, col(1), col(0)) } // 달: 흰 핵 + 푸른 달무리
                else { sparkle(p.x, p.y, s * 0.045, col(i)) }
            }
        case "fireflies":
            let n = min(max(count ?? 5, 2), 9)
            for i in 0..<n {
                // 각도·반지름을 결정적으로 흩뜨린다(같은 아이템 = 항상 같은 배치).
                let deg = CGFloat(i) * 360 / CGFloat(n) + (i % 2 == 0 ? 14 : -9)
                let r = s * (0.27 + 0.13 * CGFloat((i * 37) % 10) / 10)
                let p = pt(deg, r)
                glowDot(p.x, p.y, s * 0.014, s * 0.06, col(i))
            }
        case "spiral":
            for arm in 0..<2 {
                let steps = 16
                for i in 0...steps {
                    let t = CGFloat(i) / CGFloat(steps)
                    let p = pt(CGFloat(arm) * 180 + t * 290, s * (0.17 + 0.28 * t))
                    let color = col(0).blended(with: col(1), fraction: t)
                    let rr = s * (0.02 - 0.011 * t)
                    c.saveGState()
                    c.setShadow(offset: .zero, blur: s * 0.012, color: color.withAlphaComponent(0.9).cgColor)
                    c.setFillColor(color.withAlphaComponent(0.95 - 0.6 * t).cgColor)
                    c.fillEllipse(in: CGRect(x: p.x - rr, y: p.y - rr, width: rr * 2, height: rr * 2))
                    c.restoreGState()
                }
            }
        case "saturn":
            let tilt = CGFloat((payload["tilt"] as? NSNumber)?.doubleValue ?? -20)
            c.saveGState()
            c.translateBy(x: cx, y: cy)
            c.rotate(by: tilt * .pi / 180)
            c.translateBy(x: -cx, y: -cy)
            func band(_ rx: CGFloat, _ ry: CGFloat, _ w: CGFloat, _ a: UIColor, _ b: UIColor, blur: CGFloat = 0) {
                c.saveGState()
                c.addEllipse(in: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
                c.setLineWidth(w)
                c.replacePathWithStrokedPath()
                c.clip()
                if let g = CGGradient(colorsSpace: space, colors: [a.cgColor, b.cgColor, a.cgColor] as CFArray, locations: [0, 0.5, 1]) {
                    if blur > 0 { c.setAlpha(0.8) }
                    c.drawLinearGradient(g, start: CGPoint(x: cx - rx, y: cy), end: CGPoint(x: cx + rx, y: cy), options: [])
                }
                c.restoreGState()
            }
            // 바깥 → 안: 가는 실고리, 넓은 A 고리(번짐 + 본띠), (카시니 틈), B 고리
            band(s * 0.47, s * 0.135, s * 0.008, col(2).withAlphaComponent(0.35), col(0).withAlphaComponent(0.6))
            band(s * 0.42, s * 0.12, s * 0.07, col(1).withAlphaComponent(0.12), col(0).withAlphaComponent(0.22), blur: 1)
            band(s * 0.42, s * 0.12, s * 0.034, col(1).withAlphaComponent(0.85), col(0).withAlphaComponent(0.95))
            band(s * 0.345, s * 0.098, s * 0.022, col(2).withAlphaComponent(0.7), col(1).withAlphaComponent(0.85))
            // 고리 위의 작은 위성(오른쪽 앞)
            let ma: CGFloat = 28 * .pi / 180
            glowDot(cx + cos(ma) * s * 0.42, cy + sin(ma) * s * 0.12, s * 0.018, s * 0.05, col(2), col(0))
            c.restoreGState()
        case "comet":
            let r = s * 0.37
            let seg = 48
            c.setLineCap(.round)
            // 두 번 그린다: 번진 빛 → 선명한 꼬리. 머리(0°) 뒤로 170°, 뒤로 갈수록 가늘고 흐릿하게.
            for pass in 0..<2 {
                for i in 0..<seg {
                    let t0 = CGFloat(i) / CGFloat(seg)
                    let a0 = -t0 * 170
                    let a1 = -(t0 + 1 / CGFloat(seg)) * 170
                    let color = t0 < 0.5 ? col(0).blended(with: col(1), fraction: t0 * 2) : col(1).blended(with: col(2), fraction: (t0 - 0.5) * 2)
                    let fade = (1 - t0) * (1 - t0)
                    c.saveGState()
                    if pass == 0 { c.setShadow(offset: .zero, blur: s * 0.05, color: color.withAlphaComponent(fade * 0.7).cgColor) }
                    c.setStrokeColor(color.withAlphaComponent(fade * (pass == 0 ? 0.45 : 0.95)).cgColor)
                    c.setLineWidth(s * ((pass == 0 ? 0.075 : 0.05) * (1 - t0) + 0.004))
                    c.move(to: pt(a0 + 0.3, r))
                    c.addLine(to: pt((a0 + a1) / 2, r))
                    c.addLine(to: pt(a1, r))
                    c.strokePath()
                    c.restoreGState()
                }
            }
            // 꼬리 곁에 흩날리는 부스러기 셋
            for (k, deg) in [CGFloat(-38), -74, -112].enumerated() {
                let p = pt(deg, r + s * (k % 2 == 0 ? 0.035 : -0.03))
                glowDot(p.x, p.y, s * 0.007, s * 0.026, col(1))
            }
            glowDot(cx + r, cy, s * 0.032, s * 0.12, col(1), .white)
        default:
            break
        }
    }
}
