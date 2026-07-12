import SwiftUI
import UIKit

/// 별 모양 Path 를 지도 마커용 UIImage 로 렌더링한다.
/// (MapLibre 어노테이션 이미지는 비트맵이 필요해 SwiftUI Path → CGPath → 비트맵으로 굽는다.)
///
/// 본체는 **수정 결정(크리스탈)** 채움([StarCrystal]) — Android `DiaryMap.starBitmap` 패리티:
/// 팔레트색 글로우(그림자) 위에 파편 패싯 본체를 얹는다.
enum StarImageRenderer {
    static func image(type: Int, colorIndex: Int, size: CGFloat = 40) -> UIImage {
        let rect = CGRect(x: 0, y: 0, width: size, height: size)
        // 글로우가 잘리지 않도록 별 본체는 캔버스의 78%(Android MARKER_SIDE_PX 비율과 동일).
        let bodySize = size * 0.78
        let bodyRect = CGRect(
            x: (size - bodySize) / 2, y: (size - bodySize) / 2,
            width: bodySize, height: bodySize
        )
        let cgPath = StarShape(type: type).path(in: bodyRect).cgPath
        let color = UIColor(StarStyle.color(colorIndex))

        let renderer = UIGraphicsImageRenderer(size: rect.size)
        return renderer.image { ctx in
            let cg = ctx.cgContext
            // 1) 후광 — 별 실루엣을 그림자로 두 번 깔아 진하게(Android 글로우 2회 패리티)
            cg.saveGState()
            cg.setShadow(offset: .zero, blur: size * 0.14, color: color.withAlphaComponent(0.9).cgColor)
            cg.setFillColor(color.cgColor)
            cg.addPath(cgPath)
            cg.fillPath(using: .evenOdd)
            cg.restoreGState()

            // 2) 본체 — 크리스탈 파편 채움(실루엣 동일, 조각마다 색 변주)
            StarCrystal.draw(in: cg, type: type, colorIndex: colorIndex, rect: bodyRect)
        }
    }
}
