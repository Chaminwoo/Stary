import SwiftUI
import UIKit

/// 앱 공용 로딩 인디케이터 — 기본 `ProgressView` 대신 **크리스탈 별이 맥동하고 스파클 2개가 공전**한다.
/// (Android `core/ui/StarLoading.kt` 패리티 — 맥동/궤도/트윙클 식·상수 동일.)
///
/// 별 본체는 비트맵을 1회 구워(NSCache) 매 프레임 스케일만 바꾼다 — 크리스탈 파편 렌더는 비싸다.
struct StarLoadingView: View {
    var size: CGFloat = 36
    var type: Int = 0
    var colorIndex: Int = 0
    /// 팔레트 밖 색으로 그릴 때(밝은 버튼 위의 어두운 별 등). 주면 [colorIndex] 대신 이 색.
    var color: Color? = nil

    var body: some View {
        TimelineView(.animation) { tl in
            // 하나의 위상(주기 1.4s)에서 공전 각도/맥동/트윙클을 모두 파생.
            let phase = (tl.date.timeIntervalSinceReferenceDate / 1.4).truncatingRemainder(dividingBy: 1)
            let angle = phase * 2 * .pi
            Canvas { ctx, sz in
                let side = min(sz.width, sz.height)
                let center = CGPoint(x: sz.width / 2, y: sz.height / 2)
                let accent = color ?? StarStyle.color(colorIndex)

                // 본체 — 맥동(크기 ±7%) + 옅은 후광(맥동에 맞춰 숨쉼).
                let pulse = 1 + 0.07 * sin(angle * 2)
                let starSize = side * 0.56 * pulse
                let haloR = starSize * 0.72
                ctx.fill(
                    Path(ellipseIn: CGRect(x: center.x - haloR, y: center.y - haloR,
                                           width: haloR * 2, height: haloR * 2)),
                    with: .color(accent.opacity(0.14 + 0.06 * sin(angle * 2)))
                )
                let img = Self.starImage(type: type, colorIndex: colorIndex, color: color, size: side * 0.62)
                ctx.draw(
                    Image(uiImage: img),
                    in: CGRect(x: center.x - starSize / 2, y: center.y - starSize / 2,
                               width: starSize, height: starSize)
                )

                // 공전 스파클 2개 — 반대편에서 살짝 눕힌 타원 궤도를 돌며 각자 트윙클.
                let orbit = side * 0.44
                for i in 0..<2 {
                    let a = angle + Double(i) * .pi
                    let p = CGPoint(x: center.x + orbit * cos(a), y: center.y + orbit * sin(a) * 0.62)
                    let twinkle = 0.45 + 0.55 * (0.5 + 0.5 * sin(angle * 3 + Double(i) * 1.7))
                    ctx.fill(Path(ellipseIn: dotRect(p, side * 0.075)),
                             with: .color(accent.opacity(0.22 * twinkle)))
                    ctx.fill(Path(ellipseIn: dotRect(p, side * 0.035)),
                             with: .color(accent.opacity(0.9 * twinkle)))
                }
            }
        }
        .frame(width: size, height: size)
        .allowsHitTesting(false)
    }

    private func dotRect(_ c: CGPoint, _ r: CGFloat) -> CGRect {
        CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
    }

    /// 팔레트 밖 색의 별은 별도 캐시([StarCrystal.image] 는 colorIndex 전용).
    private static let customCache = NSCache<NSString, UIImage>()

    private static func starImage(type: Int, colorIndex: Int, color: Color?, size: CGFloat) -> UIImage {
        guard let color else { return StarCrystal.image(type: type, colorIndex: colorIndex, size: size) }
        let ui = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let key = "\(type)-\(Int(size.rounded()))-\(r)-\(g)-\(b)-\(a)" as NSString
        if let hit = customCache.object(forKey: key) { return hit }
        let rect = CGRect(x: 0, y: 0, width: size, height: size)
        let img = UIGraphicsImageRenderer(size: rect.size).image { ctx in
            StarCrystal.draw(in: ctx.cgContext, type: type, colors: [ui], rect: rect)
        }
        customCache.setObject(img, forKey: key)
        return img
    }
}
