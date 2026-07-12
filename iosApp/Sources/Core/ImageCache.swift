import SwiftUI
import UIKit

/// 이미지 네트워크 캐시 — "작은 이미지가 뜨는 데 오래 걸리는" 문제를 줄이기 위한 공용 세션.
/// Android 의 전역 Coil 로더(`StaryApplication.newImageLoader`, respectCacheHeaders(false) + 대용량
/// 디스크 캐시) 패리티.
///
/// 핵심: Firebase Storage 응답의 보수적 `Cache-Control` 때문에 매번 다시 받는 일이 없도록
/// **`.returnCacheDataElseLoad`** 로 캐시가 있으면 재검증 없이 바로 쓴다(프로필/썸네일은 바뀌면
/// URL 자체가 바뀌므로 안전).
enum ImageCache {
    /// 메모리 32MB + 디스크 256MB (Android 디스크 256MB 와 동일 규모).
    static let session: URLSession = {
        let cache = URLCache(
            memoryCapacity: 32 * 1024 * 1024,
            diskCapacity: 256 * 1024 * 1024,
            diskPath: "stary_image_cache"
        )
        let config = URLSessionConfiguration.default
        config.urlCache = cache
        config.requestCachePolicy = .returnCacheDataElseLoad
        return URLSession(configuration: config)
    }()
}

/// 작은/중요하지 않은 이미지(아바타·목록 썸네일) 고속 표시 — 원본 대신 [pixelSize] 로
/// 다운샘플 디코드해 CPU/메모리를 아끼고 같은 URL·크기의 메모리 캐시를 재사용한다.
/// Android `ThumbAsyncImage` 패리티.
struct AvatarThumbView: View {
    let url: String
    var pixelSize: CGFloat = 128
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Theme.surfaceAlt
            }
        }
        .task(id: url) {
            image = await AvatarThumbCache.shared.image(for: url, maxPixel: pixelSize)
        }
    }
}
