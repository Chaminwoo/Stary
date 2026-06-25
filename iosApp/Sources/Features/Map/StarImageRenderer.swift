import SwiftUI
import UIKit

/// 별 모양 Path 를 지도 마커용 UIImage 로 렌더링한다.
/// (MapLibre 어노테이션 이미지는 비트맵이 필요해 SwiftUI Path → CGPath → 비트맵으로 굽는다.)
enum StarImageRenderer {
    static func image(type: Int, colorIndex: Int, size: CGFloat = 40) -> UIImage {
        let rect = CGRect(x: 0, y: 0, width: size, height: size)
        let cgPath = StarShape(type: type).path(in: rect).cgPath
        let color = UIColor(StarStyle.color(colorIndex))
        let renderer = UIGraphicsImageRenderer(size: rect.size)
        return renderer.image { ctx in
            let cg = ctx.cgContext
            // 후광
            cg.setShadow(offset: .zero, blur: size * 0.18, color: color.withAlphaComponent(0.8).cgColor)
            cg.setFillColor(color.cgColor)
            cg.addPath(cgPath)
            cg.fillPath(using: .evenOdd)
        }
    }
}
