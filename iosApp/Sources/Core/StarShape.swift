import SwiftUI

/// 별/형태 외곽선 Path. Android `StarStyle.starPath` 의 Swift 포팅.
///
/// 0: 4꼭지 스파클 / 1: 5꼭지 별 / 2: 6꼭지 별 / 3: 8꼭지 가는 스파클 / 4: 다이아 스파클 /
/// 5: 꽃 / 6: 보석 / 7: 초승달 / 8: 행성
///
/// 5~8 형태는 Android `Path.Op` 와 동일한 boolean 연산(`union`/`subtracting`)으로 만든다 —
/// 결과 경로는 정규화되어 even-odd/non-zero 어느 채움 규칙에서도 같은 모양이다.
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

    private func gem(_ s: CGFloat) -> Path {
        // Android gemPath 와 동일: 컷 다이아몬드 실루엣 − 패싯(컷) 라인(스트로크를 채움 경로로 변환해 뺌).
        func p(_ fx: CGFloat, _ fy: CGFloat) -> CGPoint { CGPoint(x: fx * s, y: fy * s) }
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

        // 패싯 라인 — Android 와 같은 세그먼트(크라운 X자 + 거들 + 파빌리온).
        var lines = Path()
        func seg(_ a: (CGFloat, CGFloat), _ b: (CGFloat, CGFloat)) {
            lines.move(to: p(a.0, a.1)); lines.addLine(to: p(b.0, b.1))
        }
        let a: (CGFloat, CGFloat) = (0.50, 0.22)     // 크라운 중앙 수렴점
        seg((0.03, 0.40), (0.97, 0.40))              // 거들(가로)
        seg((0.31, 0.11), (0.69, 0.11))              // 테이블 윗변
        seg((0.31, 0.11), a); seg((0.69, 0.11), a)   // 테이블 양끝 → 중앙점 A
        seg(a, (0.40, 0.40)); seg(a, (0.60, 0.40))   // A → 거들 중앙 두 점
        seg((0.31, 0.11), (0.29, 0.40)); seg((0.69, 0.11), (0.71, 0.40)) // 테이블 모서리 → 중간 거들점
        seg((0.16, 0.14), (0.29, 0.40)); seg((0.84, 0.14), (0.71, 0.40)) // 어깨 → 중간 거들점
        seg((0.29, 0.40), (0.50, 0.95)); seg((0.71, 0.40), (0.50, 0.95)) // 파빌리온 → 컬릿

        let lineFill = lines.strokedPath(StrokeStyle(lineWidth: s * 0.03, lineJoin: .miter))
        return outline.subtractingCompat(lineFill)
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
}

private extension Int {
    func clamped(_ lo: Int, _ hi: Int) -> Int { Swift.min(Swift.max(self, lo), hi) }
}

private extension Path {
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
