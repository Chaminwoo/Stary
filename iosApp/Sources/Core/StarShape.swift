import SwiftUI

/// 별/형태 외곽선 Path. Android `StarStyle.starPath` 의 Swift 포팅.
///
/// 0: 4꼭지 스파클 / 1: 5꼭지 별 / 2: 6꼭지 별 / 3: 8꼭지 가는 스파클 / 4: 다이아 스파클 /
/// 5: 꽃 / 6: 보석 / 7: 초승달 / 8: 행성
///
/// 채우기는 짝수-홀수(even-odd) 규칙을 전제로 한다(꽃·초승달·행성의 빈 공간 표현).
/// ⚠️ 5~8 의 일부 형태는 boolean path 연산 대신 even-odd 근사다(추후 정교화 가능).
struct StarShape: Shape {
    let type: Int

    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height)
        let origin = CGPoint(x: rect.midX - s / 2, y: rect.midY - s / 2)
        var p = build(s: s)
        p = p.applying(CGAffineTransform(translationX: origin.x, y: origin.y))
        return p
    }

    private func build(s: CGFloat) -> Path {
        switch type.clamped(0, StarStyle.typeCount - 1) {
        case 5: return flower(s)
        case 6: return gem(s)
        case 7: return crescent(s)
        case 8: return planet(s)
        default: return polygonStar(type: type, s: s)
        }
    }

    // MARK: - 0~4 별/스파클

    private func polygonStar(type: Int, s: CGFloat) -> Path {
        // 내부 반지름 비율을 낮춰 꼭지를 더 뾰족하고 깔끔하게(Android StarStyle 와 동기).
        struct Spec { let spikes: Int; let inner: CGFloat; let rotate: Double; let curved: Bool }
        let spec: Spec
        switch type.clamped(0, StarStyle.typeCount - 1) {
        case 0: spec = Spec(spikes: 4, inner: 0.085, rotate: 0, curved: true)
        case 1: spec = Spec(spikes: 5, inner: 0.34, rotate: -90, curved: false)
        case 2: spec = Spec(spikes: 6, inner: 0.21, rotate: -90, curved: false)
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
        let c = CGPoint(x: s / 2, y: s / 2)
        let scale: CGFloat = 0.8
        let ring = s * 0.255 * scale
        let petal = s * 0.225 * scale
        var path = Path()
        for i in 0..<6 {
            let a = (Double(i) * 60.0 - 90.0) * .pi / 180
            let pc = CGPoint(x: c.x + ring * CGFloat(cos(a)), y: c.y + ring * CGFloat(sin(a)))
            path.addEllipse(in: CGRect(x: pc.x - petal, y: pc.y - petal, width: petal * 2, height: petal * 2))
        }
        // 가운데 빈 원(even-odd 로 구멍).
        let hole = s * 0.135
        path.addEllipse(in: CGRect(x: c.x - hole, y: c.y - hole, width: hole * 2, height: hole * 2))
        return path
    }

    private func gem(_ s: CGFloat) -> Path {
        func p(_ fx: CGFloat, _ fy: CGFloat) -> CGPoint { CGPoint(x: fx * s, y: fy * s) }
        let pts: [(CGFloat, CGFloat)] = [
            (0.31, 0.11), (0.69, 0.11), (0.84, 0.14), (0.97, 0.40),
            (0.50, 0.95), (0.03, 0.40), (0.16, 0.14),
        ]
        var path = Path()
        for (i, f) in pts.enumerated() {
            let pt = p(f.0, f.1)
            if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
        }
        path.closeSubpath()
        return path
    }

    private func crescent(_ s: CGFloat) -> Path {
        let c = CGPoint(x: s / 2, y: s / 2)
        var path = Path()
        let outerR = s * 0.42
        let oc = CGPoint(x: c.x - s * 0.05, y: c.y)
        path.addEllipse(in: CGRect(x: oc.x - outerR, y: oc.y - outerR, width: outerR * 2, height: outerR * 2))
        let innerR = s * 0.37
        let ic = CGPoint(x: c.x + s * 0.16, y: c.y - s * 0.04)
        path.addEllipse(in: CGRect(x: ic.x - innerR, y: ic.y - innerR, width: innerR * 2, height: innerR * 2))
        // 누운 초승달(반시계 22°).
        return path.applying(CGAffineTransform(rotationAngle: -22 * .pi / 180).rotated(around: c))
    }

    private func planet(_ s: CGFloat) -> Path {
        let c = CGPoint(x: s / 2, y: s * 0.52)
        var path = Path()
        let bodyR = s * 0.26
        path.addEllipse(in: CGRect(x: c.x - bodyR, y: c.y - bodyR, width: bodyR * 2, height: bodyR * 2))
        // 고리(밴드) = 바깥 타원 − 안쪽 타원, 살짝 기울임.
        var ring = Path()
        ring.addEllipse(in: CGRect(x: c.x - s * 0.46, y: c.y - s * 0.15, width: s * 0.92, height: s * 0.30))
        ring.addEllipse(in: CGRect(x: c.x - s * 0.37, y: c.y - s * 0.105, width: s * 0.74, height: s * 0.21))
        ring = ring.applying(CGAffineTransform(rotationAngle: -20 * .pi / 180).rotated(around: c))
        path.addPath(ring)
        return path
    }
}

private extension Int {
    func clamped(_ lo: Int, _ hi: Int) -> Int { Swift.min(Swift.max(self, lo), hi) }
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
struct StarView: View {
    let type: Int
    let colorIndex: Int
    var size: CGFloat = 28
    var glow: Bool = true

    var body: some View {
        StarShape(type: type)
            .fill(StarStyle.fill(colorIndex), style: FillStyle(eoFill: true))
            .frame(width: size, height: size)
            .shadow(color: glow ? StarStyle.color(colorIndex).opacity(0.8) : .clear,
                    radius: glow ? size * 0.22 : 0)
    }
}
