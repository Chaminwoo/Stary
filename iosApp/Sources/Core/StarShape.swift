import SwiftUI

/// 별/형태 외곽선 Path. Android `StarStyle.starPath` 의 Swift 포팅.
///
/// 0: 4꼭지 스파클 / 1: 5꼭지 별 / 2: 6꼭지 별 / 3: 8꼭지 가는 스파클 / 4: 다이아 스파클 /
/// 5: 꽃 / 6: 보석 / 7: 초승달 / 8: 행성
/// 9: 하트 / 10: 혜성 / 11: 눈꽃 / 12: 벚꽃 / 13: 태양 / 14: 불꽃 / 15: 열쇠 / 16: 네잎클로버 /
/// 17: 왕관 / 18: 나선 은하 / 19: 고양이 / 20: 종이비행기  (업적 보상, 2026-09-26)
///
/// 5~20 형태는 Android `Path.Op` 와 동일한 boolean 연산(`union`/`subtracting`)으로 만든다 —
/// 결과 경로는 정규화되어 even-odd/non-zero 어느 채움 규칙에서도 같은 모양이다.
/// 9~20 은 부품이 많아 비싸므로 모든 타입을 기준 크기(100)로 한 번만 만들어 캐시하고 배율만 준다
/// (Android `StarStyle.starPath` 의 pathCache 패리티).
struct StarShape: Shape {
    let type: Int

    private static let cacheBase: CGFloat = 100
    /// static let 은 지연 + 스레드 안전 초기화 — 어느 스레드에서 처음 그려도 한 번만 만든다.
    private static let cache: [Path] = (0..<StarStyle.typeCount).map { StarShape(type: $0).build(s: cacheBase) }

    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height)
        let origin = CGPoint(x: rect.midX - s / 2, y: rect.midY - s / 2)
        let k = s / Self.cacheBase
        return Self.cache[type.clamped(0, StarStyle.typeCount - 1)]
            .applying(CGAffineTransform(scaleX: k, y: k).concatenating(
                CGAffineTransform(translationX: origin.x, y: origin.y)))
    }

    private func build(s: CGFloat) -> Path {
        switch type.clamped(0, StarStyle.typeCount - 1) {
        case 5: return flower(s)
        case 6: return gem(s)
        case 7: return crescent(s)
        case 8: return planet(s)
        case 9: return heart(s)
        case 10: return comet(s)
        case 11: return snowflake(s)
        case 12: return sakura(s)
        case 13: return sun(s)
        case 14: return flame(s)
        case 15: return key(s)
        case 16: return clover(s)
        case 17: return crown(s)
        case 18: return galaxy(s)
        case 19: return cat(s)
        case 20: return paperPlane(s)
        default: return polygonStar(type: type, s: s)
        }
    }

    // MARK: - 0~4 별/스파클

    private func polygonStar(type: Int, s: CGFloat) -> Path {
        // 모든 별을 곡선(quad) 스파이크로 통일 — 직선 별(구 5각/6각)이 투박해 보여 재구성(Android StarStyle 와 동기).
        struct Spec { let spikes: Int; let inner: CGFloat; let rotate: Double; let curved: Bool }
        let spec: Spec
        switch type.clamped(0, StarStyle.typeCount - 1) {
        case 0: spec = Spec(spikes: 4, inner: 0.085, rotate: 0, curved: true)
        case 1: spec = Spec(spikes: 5, inner: 0.14, rotate: -90, curved: true)
        case 2: spec = Spec(spikes: 6, inner: 0.11, rotate: -90, curved: true)
        case 3: spec = Spec(spikes: 8, inner: 0.10, rotate: 0, curved: true)
        default: spec = Spec(spikes: 4, inner: 0.085, rotate: 45, curved: true) // 4
        }
        let c = CGPoint(x: s / 2, y: s / 2)
        let outer = s / 2 * 0.95
        let inner = outer * spec.inner
        let total = spec.spikes * 2
        func point(_ i: Int, _ len: CGFloat) -> CGPoint {
            let a = (Double(i) * 360.0 / Double(total) + spec.rotate) * .pi / 180
            return CGPoint(x: c.x + CGFloat(cos(a)) * len, y: c.y + CGFloat(sin(a)) * len)
        }

        var path = Path()
        if spec.curved {
            path.move(to: point(0, outer))
            for i in 0..<spec.spikes {
                let ctrl = point(i * 2 + 1, inner)
                let to = point(((i + 1) % spec.spikes) * 2, outer)
                path.addQuadCurve(to: to, control: ctrl)
            }
        } else {
            for i in 0..<total {
                let len = i % 2 == 0 ? outer : inner
                let pt = point(i, len)
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
        }
        path.closeSubpath()
        return path
    }

    // MARK: - 5~8 창의적 형태 (even-odd 근사)

    private func flower(_ s: CGFloat) -> Path {
        // Android flowerPath 와 동일: 꽃잎 6장 합집합 − 가운데 원.
        // (even-odd 로는 꽃잎끼리 겹치는 부분이 구멍으로 뚫려 모양이 깨졌다.)
        let c = CGPoint(x: s / 2, y: s / 2)
        let scale: CGFloat = 0.8
        let ring = s * 0.255 * scale
        let petal = s * 0.225 * scale
        var body = Path()
        for i in 0..<6 {
            let a = (Double(i) * 60.0 - 90.0) * .pi / 180
            let pc = CGPoint(x: c.x + ring * CGFloat(cos(a)), y: c.y + ring * CGFloat(sin(a)))
            let circle = Path(ellipseIn: CGRect(x: pc.x - petal, y: pc.y - petal, width: petal * 2, height: petal * 2))
            body = i == 0 ? circle : body.unionCompat(circle)
        }
        let hole = s * 0.135
        return body.subtractingCompat(Path(ellipseIn: CGRect(x: c.x - hole, y: c.y - hole, width: hole * 2, height: hole * 2)))
    }

    /// 결정(다이아몬드) 모양 크기 — Android StarStyle.GEM_SCALE 과 같은 값.
    /// 0.66(2026-09-14) → 0.56(2026-09-22 "더 줄여" 피드백).
    private static let gemScale: CGFloat = 0.56

    private func gem(_ s: CGFloat) -> Path {
        // Android gemPath 와 동일: 컷 다이아몬드 실루엣.
        // 2026-09 단순화: 컷 라인 12개 → 거들 높이 중심에서 5갈래(세로 중심 0.53 → 0.5)
        // → 2026-09-22 "가운데 선 제거": 중심→컬릿 세로선 삭제, 크라운 V + 거들 가로선만.
        // → 2026-09-25 "안쪽 검은 선 전부 제거": 패싯 라인(실루엣에서 빼내던 컷 라인)을 완전히 없앴다 —
        //   어두운 배경에 검은 선으로 비쳤기 때문. 대신 StarCrystal.facetDensity(6) 를 7→16 으로 높여
        //   파편 무늬가 촘촘해지면서 "컷 면이 많다"는 인상을 대신 준다(Android 와 값 동일).
        let k = StarShape.gemScale
        func p(_ fx: CGFloat, _ fy: CGFloat) -> CGPoint {
            CGPoint(x: (0.5 + (fx - 0.5) * k) * s, y: (0.5 + (fy - 0.53) * k) * s)
        }
        let pts: [(CGFloat, CGFloat)] = [
            (0.31, 0.11), (0.69, 0.11), (0.84, 0.14), (0.97, 0.40),
            (0.50, 0.95), (0.03, 0.40), (0.16, 0.14),
        ]
        var outline = Path()
        for (i, f) in pts.enumerated() {
            let pt = p(f.0, f.1)
            if i == 0 { outline.move(to: pt) } else { outline.addLine(to: pt) }
        }
        outline.closeSubpath()
        return outline
    }

    private func crescent(_ s: CGFloat) -> Path {
        // Android crescentPath 와 동일: 진짜 boolean 차집합(outer − inner).
        // (구 even-odd 근사는 안쪽 원이 바깥 원 밖으로 삐져나온 부분까지 채워져 모양이 깨졌다.)
        let c = CGPoint(x: s / 2, y: s / 2)
        let outerR = s * 0.42
        let oc = CGPoint(x: c.x - s * 0.05, y: c.y)
        let outer = Path(ellipseIn: CGRect(x: oc.x - outerR, y: oc.y - outerR, width: outerR * 2, height: outerR * 2))
        let innerR = s * 0.37
        let ic = CGPoint(x: c.x + s * 0.16, y: c.y - s * 0.04)
        let inner = Path(ellipseIn: CGRect(x: ic.x - innerR, y: ic.y - innerR, width: innerR * 2, height: innerR * 2))
        // 누운 초승달(반시계 22°).
        return outer.subtractingCompat(inner)
            .applying(CGAffineTransform(rotationAngle: -22 * .pi / 180).rotated(around: c))
    }

    private func planet(_ s: CGFloat) -> Path {
        // Android planetPath 와 동일: 본체 ∪ (고리 밴드 = 바깥 타원 − 안쪽 타원).
        // (구 even-odd 근사는 본체와 고리가 겹치는 부분이 구멍으로 뚫렸다.)
        let c = CGPoint(x: s / 2, y: s * 0.52)
        let bodyR = s * 0.26
        let body = Path(ellipseIn: CGRect(x: c.x - bodyR, y: c.y - bodyR, width: bodyR * 2, height: bodyR * 2))
        let ringOuter = Path(ellipseIn: CGRect(x: c.x - s * 0.46, y: c.y - s * 0.15, width: s * 0.92, height: s * 0.30))
        let ringInner = Path(ellipseIn: CGRect(x: c.x - s * 0.37, y: c.y - s * 0.105, width: s * 0.74, height: s * 0.21))
        let band = ringOuter.subtractingCompat(ringInner)
            .applying(CGAffineTransform(rotationAngle: -20 * .pi / 180).rotated(around: c))
        return body.unionCompat(band)
    }

    // MARK: - 9~20 업적 보상 형태 (2026-09-26)
    // Android StarStyle 의 heartPath…paperPlanePath 와 **같은 0..1 좌표**(값 drift 금지).
    // 부품을 0..1 로 그린 뒤 UnitKit 이 s 로 확대하고, 합집합/차집합으로 실루엣을 만든다.

    /// 0..1 좌표 부품 → 픽셀 경로. `scale` 은 정사각 중심 기준 배율(속이 꽉 찬 형태를 줄일 때).
    private struct UnitKit {
        let toPx: CGAffineTransform

        init(_ s: CGFloat, scale: CGFloat = 1) {
            toPx = CGAffineTransform(translationX: -0.5, y: -0.5)
                .concatenating(CGAffineTransform(scaleX: scale, y: scale))
                .concatenating(CGAffineTransform(translationX: 0.5, y: 0.5))
                .concatenating(CGAffineTransform(scaleX: s, y: s))
        }

        func part(_ local: CGAffineTransform = .identity, _ build: (inout Path) -> Void) -> Path {
            var p = Path()
            build(&p)
            return p.applying(local.concatenating(toPx))
        }
        func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ local: CGAffineTransform = .identity) -> Path {
            part(local) { $0.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
        }
        func oval(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> Path {
            part { $0.addEllipse(in: CGRect(x: x - rx, y: y - ry, width: rx * 2, height: ry * 2)) }
        }
        func rrect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat,
                   _ local: CGAffineTransform = .identity) -> Path {
            part(local) { $0.addRoundedRect(in: CGRect(x: x, y: y, width: w, height: h), cornerSize: CGSize(width: r, height: r)) }
        }
        func poly(_ pts: [CGPoint]) -> Path {
            part { p in
                p.move(to: pts[0])
                for pt in pts.dropFirst() { p.addLine(to: pt) }
                p.closeSubpath()
            }
        }
    }

    private func union(_ parts: [Path]) -> Path {
        parts.dropFirst().reduce(parts[0]) { $0.unionCompat($1) }
    }

    /// 원점 기준 부품 → 회전(도) 후 정사각 중앙으로 이동. (Android aroundCenter)
    private func aroundCenter(_ deg: CGFloat) -> CGAffineTransform {
        CGAffineTransform(rotationAngle: deg * .pi / 180).concatenating(CGAffineTransform(translationX: 0.5, y: 0.5))
    }

    /// 하트 윤곽(0..1, 뾰족한 끝 = (0.5, 0.84)). 네잎클로버 잎에도 쓴다.
    private func heartOutline(_ p: inout Path) {
        p.move(to: CGPoint(x: 0.5, y: 0.84))
        p.addCurve(to: CGPoint(x: 0.14, y: 0.28), control1: CGPoint(x: 0.14, y: 0.62), control2: CGPoint(x: 0.06, y: 0.42))
        p.addCurve(to: CGPoint(x: 0.5, y: 0.30), control1: CGPoint(x: 0.23, y: 0.12), control2: CGPoint(x: 0.43, y: 0.13))
        p.addCurve(to: CGPoint(x: 0.86, y: 0.28), control1: CGPoint(x: 0.57, y: 0.13), control2: CGPoint(x: 0.77, y: 0.12))
        p.addCurve(to: CGPoint(x: 0.5, y: 0.84), control1: CGPoint(x: 0.94, y: 0.42), control2: CGPoint(x: 0.86, y: 0.62))
        p.closeSubpath()
    }

    /// 9 하트 — 속이 꽉 찬 형태라 0.82 로 줄인다.
    private func heart(_ s: CGFloat) -> Path {
        UnitKit(s, scale: 0.82).part { heartOutline(&$0) }
    }

    /// 10 혜성 — 둥근 머리 + 휘며 가늘어지는 먼지 꼬리 + 틈을 둔 이온 꼬리 2줄 + 머리 앞 작은 반짝임.
    /// 좌표는 머리 (0.64, 0.64) 기준 (a = 꼬리 방향 ↖, b = 옆 방향 ↗). Android cometPath 와 동일.
    private func comet(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        let d: CGFloat = 0.70710677
        func pt(_ a: CGFloat, _ b: CGFloat) -> CGPoint { CGPoint(x: 0.64 - a * d + b * d, y: 0.64 - a * d - b * d) }
        func band(_ m: (CGFloat, CGFloat), _ c1: (CGFloat, CGFloat), _ e1: (CGFloat, CGFloat),
                  _ c2: (CGFloat, CGFloat), _ e2: (CGFloat, CGFloat)) -> Path {
            k.part { p in
                p.move(to: pt(m.0, m.1))
                p.addQuadCurve(to: pt(e1.0, e1.1), control: pt(c1.0, c1.1))
                p.addQuadCurve(to: pt(e2.0, e2.1), control: pt(c2.0, c2.1))
                p.closeSubpath()
            }
        }
        let head = k.circle(0.64, 0.64, 0.145)
        let tail = band((0, 0.145), (0.36, 0.14), (0.76, 0.05), (0.40, -0.02), (0, -0.145))
        // 이온 꼬리는 양 끝이 뾰족한 렌즈 모양(시작점 = 끝점) — 뭉툭하게 잘린 밑동이 없게.
        let ionUpper = band((0.06, 0.20), (0.30, 0.26), (0.62, 0.20), (0.32, 0.185), (0.06, 0.20))
        let ionLower = band((0.08, -0.19), (0.28, -0.235), (0.50, -0.10), (0.30, -0.155), (0.08, -0.19))
        let g = pt(-0.30, 0)
        let glint = k.part { p in sparkle(&p, cx: g.x, cy: g.y, r: 0.075, innerRatio: 0.12, spikes: 4) }
        return union([head, tail, ionUpper, ionLower, glint])
    }

    /// 곡선 스파이크 별 — 임의 중심/반지름(0~4 와 같은 방식, 스파이크는 0° 부터).
    private func sparkle(_ p: inout Path, cx: CGFloat, cy: CGFloat, r: CGFloat, innerRatio: CGFloat, spikes: Int) {
        let total = spikes * 2
        func at(_ i: Int, _ len: CGFloat) -> CGPoint {
            let a = Double(i) * 2 * .pi / Double(total)
            return CGPoint(x: cx + CGFloat(cos(a)) * len, y: cy + CGFloat(sin(a)) * len)
        }
        p.move(to: at(0, r))
        for i in 0..<spikes {
            p.addQuadCurve(to: at(((i + 1) % spikes) * 2, r), control: at(i * 2 + 1, r * innerRatio))
        }
        p.closeSubpath()
    }

    /// 11 눈꽃 — 둥근 막대 팔 6개 + 팔마다 비스듬한 가지 2개 + 가운데 원.
    private func snowflake(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        var parts = [k.circle(0.5, 0.5, 0.10)]
        for i in 0..<6 {
            let arm = CGFloat(i) * 60
            parts.append(k.rrect(-0.04, -0.44, 0.08, 0.44, 0.04, aroundCenter(arm)))
            for sgn in [-1.0, 1.0] as [CGFloat] {
                let m = CGAffineTransform(rotationAngle: sgn * 55 * .pi / 180)
                    .concatenating(CGAffineTransform(translationX: 0, y: -0.24))
                    .concatenating(aroundCenter(arm))
                parts.append(k.rrect(-0.034, -0.15, 0.068, 0.15, 0.034, m))
            }
        }
        return union(parts)
    }

    /// 12 벚꽃 — 끝이 살짝 갈라진 꽃잎 5장 + 가운데 원.
    private func sakura(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        var parts = [k.circle(0.5, 0.5, 0.08)]
        for i in 0..<5 {
            parts.append(k.part(aroundCenter(CGFloat(i) * 72)) { p in
                p.move(to: CGPoint(x: 0, y: -0.05))
                p.addCurve(to: CGPoint(x: -0.12, y: -0.44), control1: CGPoint(x: -0.11, y: -0.11), control2: CGPoint(x: -0.20, y: -0.27))
                p.addQuadCurve(to: CGPoint(x: 0, y: -0.39), control: CGPoint(x: -0.05, y: -0.47))
                p.addQuadCurve(to: CGPoint(x: 0.12, y: -0.44), control: CGPoint(x: 0.05, y: -0.47))
                p.addCurve(to: CGPoint(x: 0, y: -0.05), control1: CGPoint(x: 0.20, y: -0.27), control2: CGPoint(x: 0.11, y: -0.11))
                p.closeSubpath()
            })
        }
        return union(parts)
    }

    /// 13 태양 — 원 + 떨어져 있는 곡선 광선 8갈래.
    private func sun(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        var parts = [k.circle(0.5, 0.5, 0.21)]
        for i in 0..<8 {
            parts.append(k.part(aroundCenter(CGFloat(i) * 45)) { p in
                p.move(to: CGPoint(x: -0.055, y: -0.28))
                p.addQuadCurve(to: CGPoint(x: 0, y: -0.47), control: CGPoint(x: -0.02, y: -0.38))
                p.addQuadCurve(to: CGPoint(x: 0.055, y: -0.28), control: CGPoint(x: 0.02, y: -0.38))
                p.closeSubpath()
            })
        }
        return union(parts)
    }

    /// 14 불꽃 — 옆으로 한 갈래 날름거리는 불꽃 + 아래쪽 속불(빈 물방울).
    private func flame(_ s: CGFloat) -> Path {
        let k = UnitKit(s, scale: 0.95)
        func c(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
        let outer = k.part { p in
            p.move(to: c(0.52, 0.06))
            p.addCurve(to: c(0.80, 0.60), control1: c(0.60, 0.22), control2: c(0.80, 0.34))
            p.addCurve(to: c(0.50, 0.92), control1: c(0.80, 0.80), control2: c(0.66, 0.92))
            p.addCurve(to: c(0.20, 0.62), control1: c(0.34, 0.92), control2: c(0.20, 0.80))
            p.addCurve(to: c(0.30, 0.30), control1: c(0.20, 0.48), control2: c(0.26, 0.40))
            p.addCurve(to: c(0.42, 0.48), control1: c(0.34, 0.40), control2: c(0.38, 0.46))
            p.addCurve(to: c(0.52, 0.06), control1: c(0.40, 0.34), control2: c(0.44, 0.18))
            p.closeSubpath()
        }
        let inner = k.part { p in
            p.move(to: c(0.50, 0.54))
            p.addCurve(to: c(0.62, 0.78), control1: c(0.57, 0.63), control2: c(0.63, 0.69))
            p.addCurve(to: c(0.38, 0.78), control1: c(0.61, 0.86), control2: c(0.39, 0.86))
            p.addCurve(to: c(0.50, 0.54), control1: c(0.37, 0.69), control2: c(0.43, 0.63))
            p.closeSubpath()
        }
        return outer.subtractingCompat(inner)
    }

    /// 15 열쇠 — 고리(가운데 구멍) + 자루 + 이 2개, 45° 기울임.
    private func key(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        let tilt = CGAffineTransform(rotationAngle: 45 * .pi / 180).rotated(around: CGPoint(x: 0.5, y: 0.5))
        let body = union([
            k.circle(0.5, 0.24, 0.18, tilt),
            k.rrect(0.455, 0.38, 0.09, 0.52, 0.03, tilt),
            k.rrect(0.5, 0.72, 0.18, 0.06, 0.02, tilt),
            k.rrect(0.5, 0.82, 0.13, 0.06, 0.02, tilt),
        ])
        return body.subtractingCompat(k.circle(0.5, 0.24, 0.075, tilt))
    }

    /// 16 네잎클로버 — 하트 잎 4장(끝이 가운데로) + 오른쪽 아래로 휜 줄기.
    private func clover(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        var parts: [Path] = []
        for i in 0..<4 {
            let m = CGAffineTransform(translationX: -0.5, y: -0.84)
                .concatenating(CGAffineTransform(scaleX: 0.5, y: 0.5))
                .concatenating(CGAffineTransform(translationX: 0, y: -0.02))
                .concatenating(aroundCenter(CGFloat(i) * 90))
            parts.append(k.part(m) { heartOutline(&$0) })
        }
        parts.append(k.part { p in
            p.move(to: CGPoint(x: 0.52, y: 0.52))
            p.addQuadCurve(to: CGPoint(x: 0.80, y: 0.87), control: CGPoint(x: 0.66, y: 0.70))
            p.addLine(to: CGPoint(x: 0.85, y: 0.83))
            p.addQuadCurve(to: CGPoint(x: 0.57, y: 0.49), control: CGPoint(x: 0.70, y: 0.67))
            p.closeSubpath()
        })
        return union(parts)
    }

    /// 17 왕관 — 뾰족 세 개 몸통 + 아래 띠 + 꼭대기 구슬 3개.
    private func crown(_ s: CGFloat) -> Path {
        let k = UnitKit(s, scale: 0.92)
        let body: [CGPoint] = [(0.16, 0.72), (0.12, 0.30), (0.33, 0.50), (0.50, 0.22), (0.67, 0.50), (0.88, 0.30), (0.84, 0.72)]
            .map { CGPoint(x: $0.0, y: $0.1) }
        return union([
            k.poly(body),
            k.rrect(0.16, 0.70, 0.68, 0.12, 0.03),
            k.circle(0.12, 0.27, 0.055),
            k.circle(0.5, 0.19, 0.06),
            k.circle(0.88, 0.27, 0.055),
        ])
    }

    /// 18 나선 은하 — 가운데 팽대부 + 가늘어지며 도는 팔 2개(극좌표 샘플링).
    private func galaxy(_ s: CGFloat) -> Path {
        let k = UnitKit(s)
        func arm(_ start: Double) -> Path {
            let n = 28
            var outer: [CGPoint] = []
            var inner: [CGPoint] = []
            for i in 0...n {
                let t = Double(i) / Double(n)
                let th = start + t * 1.25 * .pi
                let r = 0.10 + 0.34 * t
                let ri = max(0, r - (0.13 * pow(1 - t, 0.8) + 0.006))
                outer.append(CGPoint(x: 0.5 + r * cos(th), y: 0.5 + r * sin(th)))
                inner.append(CGPoint(x: 0.5 + ri * cos(th), y: 0.5 + ri * sin(th)))
            }
            // 바깥 가장자리를 따라 나갔다가 안쪽 가장자리로 되돌아온다.
            return k.poly(outer + inner.reversed())
        }
        return union([k.circle(0.5, 0.5, 0.11), arm(0), arm(.pi)])
    }

    /// 19 고양이 — 둥근 얼굴 + 뾰족 귀 2개 + 눈(빈 타원 2개).
    private func cat(_ s: CGFloat) -> Path {
        let k = UnitKit(s, scale: 0.95)
        func ear(_ sx: CGFloat) -> Path {
            // sx = 1 왼쪽 귀, -1 오른쪽 귀(가운데 0.5 기준 좌우 반전)
            func c(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: 0.5 + (x - 0.5) * sx, y: y) }
            return k.part { p in
                p.move(to: c(0.2, 0.5))
                p.addLine(to: c(0.19, 0.2))
                p.addQuadCurve(to: c(0.25, 0.17), control: c(0.19, 0.14))
                p.addLine(to: c(0.45, 0.36))
                p.closeSubpath()
            }
        }
        let head = union([k.oval(0.5, 0.6, 0.34, 0.27), ear(1), ear(-1)])
        return head
            .subtractingCompat(k.oval(0.38, 0.6, 0.045, 0.065))
            .subtractingCompat(k.oval(0.62, 0.6, 0.045, 0.065))
    }

    /// 20 종이비행기 — 접힌 날개 실루엣(안쪽 접힌 선은 넣지 않는다 — 결정 모양 "검은 선" 피드백과 같은 이유).
    private func paperPlane(_ s: CGFloat) -> Path {
        let pts: [CGPoint] = [(0.92, 0.14), (0.07, 0.47), (0.37, 0.57), (0.45, 0.88), (0.56, 0.66), (0.78, 0.77)]
            .map { CGPoint(x: $0.0, y: $0.1) }
        return UnitKit(s).poly(pts)
    }
}

private extension Int {
    func clamped(_ lo: Int, _ hi: Int) -> Int { Swift.min(Swift.max(self, lo), hi) }
}

/// iOS 16 호환 boolean 연산 — 별 모양뿐 아니라 달 위상(SkyOverlay) 등 다른 곳에서도 쓰므로
/// 파일 밖에서 보이게 둔다(같은 이름을 다른 파일에 또 정의하지 말 것).
extension Path {
    /// iOS 16 호환 boolean 연산 — SwiftUI `Path.union/subtracting` 은 iOS 17+ 라
    /// 같은 기능의 CGPath 연산(iOS 16+)으로 우회한다(결과는 동일한 정규화 경로).
    func unionCompat(_ other: Path) -> Path { Path(cgPath.union(other.cgPath)) }
    func subtractingCompat(_ other: Path) -> Path { Path(cgPath.subtracting(other.cgPath)) }
}

private extension CGAffineTransform {
    /// 특정 점을 중심으로 회전하도록 평행이동을 감싼다.
    /// (행벡터 규약: p·M, concatenating 은 "먼저 self 그다음 인자" 순서로 결합 →
    ///  중심 회전은 T(-c) → R → T(c) 순으로 적용해야 한다.)
    func rotated(around center: CGPoint) -> CGAffineTransform {
        CGAffineTransform(translationX: -center.x, y: -center.y)
            .concatenating(self)
            .concatenating(CGAffineTransform(translationX: center.x, y: center.y))
    }
}

/// 별 + 후광을 그리는 표시용 뷰.
/// 채움은 **수정 결정(크리스탈)** — 실루엣은 [StarShape] 그대로 두고 내부를 불규칙 파편으로
/// 갈라 조각마다 색을 달리한다([StarCrystal], Android `drawCrystalFill` 패리티).
struct StarView: View {
    let type: Int
    let colorIndex: Int
    var size: CGFloat = 28
    var glow: Bool = true

    var body: some View {
        Canvas { ctx, canvasSize in
            ctx.withCGContext { cg in
                StarCrystal.draw(
                    in: cg, type: type, colorIndex: colorIndex,
                    rect: CGRect(origin: .zero, size: canvasSize)
                )
            }
        }
        .frame(width: size, height: size)
        .shadow(color: glow ? StarStyle.color(colorIndex).opacity(0.8) : .clear,
                radius: glow ? size * 0.22 : 0)
    }
}
