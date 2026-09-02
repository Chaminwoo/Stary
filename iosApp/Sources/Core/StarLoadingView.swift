import SwiftUI
import UIKit

/// 앱 공용 로딩 인디케이터 — 기본 `ProgressView` 대신 별 스피너 애니메이션
/// (`spinner_128`, 128×128 애니메이션 WebP)을 재생한다.
/// (Android `core/ui/StarLoading.kt` 패리티 — 같은 에셋을 공유한다.)
///
/// 애니메이션 WebP 는 `UIImage(data:)` 로는 첫 프레임만 나오므로 프레임을 직접 뽑는
/// `GifImageView`(CGImageSource) 로 재생한다.
struct StarLoadingView: View {
    var size: CGFloat = 36
    /// 주면 스피너를 이 색으로 칠한다(밝은 버튼 위 등 대비가 필요할 때).
    /// 기본(nil)은 원본 아트워크 색 그대로 — 어두운 배경용.
    var color: Color? = nil

    var body: some View {
        Group {
            if let data = BundleImage.data("spinner_128") {
                GifImageView(
                    data: data,
                    contentMode: .scaleAspectFit,
                    tint: color.map { UIColor($0) }
                )
            } else {
                ProgressView()
            }
        }
        .frame(width: size, height: size)
        .allowsHitTesting(false)
    }
}
