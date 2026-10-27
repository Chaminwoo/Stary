import SwiftUI

/// 지도 위쪽 "하늘" + 광택(2026-10-28 "하나의 우주"): 기울인 지도 윗부분이 안개 낀 밤하늘로 이어지고(탑바 영역까지),
/// 땅은 알갱이 대신 넓은 빛띠가 천천히 비치는 매끈한 판처럼 보인다. 정적 그리기(프레임마다 재계산 없음)라 지도 성능에 영향이 없다.
/// Android `drawMapSky`(DiaryMapMarkers.kt) 와 같은 수치 — 하늘 높이 비율·안개 색/투명도·성운·빛띠·별 점(개수 48, 반지름 0.5~1.6dp).
/// 별 점의 난수 수열은 Kotlin `Random` 과 달라 배치는 다르지만 "시드 고정이라 매번 같은 배치"라는 계약은 같다.
struct MapSkyOverlay: View {
    /// 0...1 세기. 저줌 글로브 전환 구간에선 0 으로 빠진다(Android `1 - hazeAlpha`).
    var strength: Double

    static let skyHeightFrac: CGFloat = 0.30
    private static let stars: [(x: CGFloat, y: CGFloat, r: CGFloat, a: CGFloat)] = {
        var state: UInt64 = 2810
        func next() -> CGFloat {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return CGFloat((state >> 33) & 0xFFFFFF) / CGFloat(0x1000000)
        }
        return (0..<48).map { _ in
            let x = next()
            let y = next(); let yy = y * y * 0.9
            let r = 0.5 + next() * 1.1
            let a = 0.35 + next() * 0.65
            return (x, yy, r, a)
        }
    }()

    var body: some View {
        Canvas { ctx, size in
            let k = strength
            guard k > 0.01 else { return }
            let w = size.width, h = size.height
            let skyH = h * Self.skyHeightFrac
            // 땅 광택: 비스듬한 넓은 빛띠 두 줄(아주 옅게)
            let band = Color(red: 0xB4 / 255.0, green: 0xC0 / 255.0, blue: 1)
            ctx.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: .clear, location: 0.00),
                        .init(color: .clear, location: 0.30),
                        .init(color: band.opacity(0.045 * k), location: 0.36),
                        .init(color: .clear, location: 0.42),
                        .init(color: .clear, location: 0.64),
                        .init(color: band.opacity(0.03 * k), location: 0.68),
                        .init(color: .clear, location: 0.73),
                    ]),
                    startPoint: CGPoint(x: 0, y: h * 0.15),
                    endPoint: CGPoint(x: w, y: h * 0.95)
                )
            )
            // 하늘 안개: 위에서 아래로 짙은 남보라 → 투명
            let skyRect = CGRect(x: 0, y: 0, width: w, height: skyH)
            ctx.fill(
                Path(skyRect),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Color(red: 0x04 / 255.0, green: 0x03 / 255.0, blue: 0x1A / 255.0).opacity(0.86 * k), location: 0.00),
                        .init(color: Color(red: 0x0A / 255.0, green: 0x08 / 255.0, blue: 0x30 / 255.0).opacity(0.50 * k), location: 0.40),
                        .init(color: Color(red: 0x14 / 255.0, green: 0x10 / 255.0, blue: 0x4A / 255.0).opacity(0.16 * k), location: 0.75),
                        .init(color: .clear, location: 1.00),
                    ]),
                    startPoint: CGPoint(x: 0, y: 0),
                    endPoint: CGPoint(x: 0, y: skyH)
                )
            )
            // 성운 두 덩이
            ctx.fill(
                Path(skyRect),
                with: .radialGradient(
                    Gradient(colors: [Color(red: 0x7C / 255.0, green: 0x5C / 255.0, blue: 1).opacity(0.20 * k), .clear]),
                    center: CGPoint(x: w * 0.82, y: skyH * 0.12), startRadius: 0, endRadius: w * 0.55
                )
            )
            ctx.fill(
                Path(skyRect),
                with: .radialGradient(
                    Gradient(colors: [Color(red: 0x3F / 255.0, green: 0xD0 / 255.0, blue: 0xC0 / 255.0).opacity(0.10 * k), .clear]),
                    center: CGPoint(x: w * 0.14, y: skyH * 0.30), startRadius: 0, endRadius: w * 0.45
                )
            )
            // 별 점
            for s in Self.stars {
                let y = s.y * skyH
                let fade = 1 - y / skyH
                let r = s.r * 0.8
                ctx.fill(
                    Path(ellipseIn: CGRect(x: s.x * w - r, y: y - r, width: r * 2, height: r * 2)),
                    with: .color(.white.opacity(min(1, max(0, Double(s.a * 0.8 * fade) * k))))
                )
            }
        }
        .allowsHitTesting(false)
    }
}
