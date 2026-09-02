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

/// 화면 배경 이미지 + 어둡게 틴트 — Android 의 `Image(painterResource(R.drawable.xxx_bg)) + darken` 패턴 대응.
/// 이미지가 없으면(로드 실패) Theme.background 단색으로 폴백해 화면은 항상 그려진다.
struct ScreenBackground: View {
    let name: String
    /// 검정 틴트 불투명도(0 = 원본 그대로, Android 의 `darken` 값과 동일 의미).
    var darken: Double = 0.7

    var body: some View {
        ZStack {
            Theme.background
            if let img = BundleImage.named(name) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            }
            Color.black.opacity(darken)
        }
        .ignoresSafeArea()
    }
}
