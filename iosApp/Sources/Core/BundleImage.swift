import SwiftUI
import UIKit

/// 번들에 loose 파일로 담긴 이미지(webp/png) 로더 — Android `res/drawable` 대응.
/// 에셋 카탈로그를 쓰지 않으므로 `UIImage(named:)` 가 아닌 URL 로드가 필요하다. NSCache 로 1회만 디코드.
enum BundleImage {
    private static let cache = NSCache<NSString, UIImage>()
    private static let dataCache = NSCache<NSString, NSData>()

    static func named(_ name: String, ext: String = "webp") -> UIImage? {
        let key = "\(name).\(ext)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        guard let data = data(name, ext: ext), let img = UIImage(data: data) else { return nil }
        cache.setObject(img, forKey: key)
        return img
    }

    /// 번들 파일 원본 바이트 — 애니메이션 WebP 처럼 프레임을 직접 뽑아야 하는 경우에 쓴다.
    static func data(_ name: String, ext: String = "webp") -> Data? {
        let key = "data:\(name).\(ext)" as NSString
        if let hit = dataCache.object(forKey: key) { return hit as Data }
        guard let url = Bundle.main.url(forResource: name, withExtension: ext),
              let data = try? Data(contentsOf: url) else { return nil }
        dataCache.setObject(data as NSData, forKey: key)
        return data
    }
}

/// 화면 배경 이미지 + 어둡게 틴트 + 반짝이는 잔별(`AmbientStarSkyView`, 2026-10-19) — Android `core/ui/ScreenBackground` 대응.
/// 이미지가 없으면(로드 실패) Theme.background 단색으로 폴백해 화면은 항상 그려진다.
///
/// **시차 스크롤**: `scroll` 을 넘기고 같은 객체를 그 화면의 세로 `ScrollView` 에 `.backgroundScrollSource(scroll)` 로 달면,
/// 스크롤한 만큼 배경 이미지는 ×0.12, 잔별은 ×0.20 비율로 같은 방향(내리면 위로)으로 살짝 따라간다(Android `BG_PARALLAX`/`STAR_PARALLAX`).
/// 이미지는 위아래로 뒤집은 사본을 번갈아 이어 붙여(거울 타일) 이음매 없이 끝없이 이어진다.
struct ScreenBackground: View {
    let name: String
    /// 검정 틴트 불투명도(0 = 원본 그대로, Android 의 `darken` 값과 동일 의미).
    var darken: Double = 0.7
    var scroll: BackgroundScroll? = nil

    var body: some View {
        ZStack {
            Theme.background
            ParallaxImageTiles(name: name, scroll: scroll ?? BackgroundScroll.still)
            Color.black.opacity(darken)
            AmbientStarSkyView(scroll: scroll)
        }
        .ignoresSafeArea()
    }
}

/// 배경 이미지 거울 타일 — 스크롤 값을 관찰해 이 배경만 다시 그린다(화면 본문은 다시 그리지 않는다).
private struct ParallaxImageTiles: View {
    let name: String
    @ObservedObject var scroll: BackgroundScroll

    var body: some View {
        let offset = scroll.offset
        Canvas { ctx, size in
            guard let ui = BundleImage.named(name), ui.size.width >= 1, ui.size.height >= 1,
                  size.width >= 1, size.height >= 1 else { return }
            let img = ctx.resolve(Image(uiImage: ui))
            // scaledToFill(가운데 기준)과 같은 맞춤 — 스크롤 전에는 예전 모습 그대로.
            let s = max(size.width / ui.size.width, size.height / ui.size.height)
            let tw = ceil(ui.size.width * s), th = ceil(ui.size.height * s)
            let left = floor((size.width - tw) / 2)
            let base = (size.height - th) / 2 - offset * BackgroundScroll.imageParallax
            var k = Int(floor(-base / th))
            while base + CGFloat(k) * th < size.height {
                // 1pt 겹쳐 그려 타일 사이 머리카락 같은 틈이 생기지 않게
                let rect = CGRect(x: left, y: floor(base + CGFloat(k) * th), width: tw, height: th + 1)
                if ((k % 2) + 2) % 2 == 1 {
                    var c = ctx
                    c.translateBy(x: 0, y: rect.midY)
                    c.scaleBy(x: 1, y: -1)
                    c.translateBy(x: 0, y: -rect.midY)
                    c.draw(img, in: rect)
                } else {
                    ctx.draw(img, in: rect)
                }
                k += 1
            }
        }
    }
}
