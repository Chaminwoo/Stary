import SwiftUI

/// 별 사진/영상 로딩 중 밑에 깔아 두는 배경(`loading_dipper`) 위에 실제 미디어를 얹고,
/// [loaded] 가 true 로 바뀌면 부드럽게 페이드인한다. (Android `core/ui/MediaLoadingFrame.kt` 패리티.)
///
/// loading_dipper 는 애니메이션 WebP 라 `UIImage(data:)` 로는 첫 프레임만 나온다 →
/// 프레임을 직접 뽑는 `GifImageView`(CGImageSource)로 재생한다.
struct MediaLoadingFrame<Content: View>: View {
    let loaded: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            if let data = BundleImage.data("loading_dipper") {
                GifImageView(data: data)
            } else {
                Theme.surfaceAlt
            }
            content()
                .opacity(loaded ? 1 : 0)
                .animation(.easeInOut(duration: 0.32), value: loaded)
        }
        .clipped()
    }
}
