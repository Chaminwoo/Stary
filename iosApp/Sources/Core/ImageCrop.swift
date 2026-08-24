import SwiftUI
import UIKit

/// 사진 크롭 좌표 모델 + 실제 잘라내기 — Android `core.util.ImageCropHelper` 패리티.
///
/// 좌표 모델(업로드/프로필 크롭 프레임의 그리기와 동일):
///   cover     = max(frameW/bmpW, frameH/bmpH)   // 프레임을 항상 덮는 최소 배율
///   dispScale = cover × scale                   // scale 은 사용자 핀치
///   disp(W,H) = bmp(W,H) × dispScale
///   left      = (frameW − dispW)/2 + offsetX     (top 동일)
/// [crop] 이 이 식을 그대로 역산해 잘라낸다.
enum ImageCrop {

    /// 다이어리 사진 고정 비율(가로/세로) — Android `ImageCropHelper.ASPECT` 와 같은 값.
    static let diaryAspect: CGFloat = 4.0 / 3.0

    /// 프로필 사진 결과 한 변(px). 원형으로 표시되므로 정사각으로 잘라 올린다.
    static let profileOutPixels: CGFloat = 640

    /// 다이어리 사진 결과 가로(px) — Android `cropToFile(outWidth = 1280)` 과 동일.
    static let diaryOutPixels: CGFloat = 1280

    /// 표시 상태([scale]/[offset])대로 [frame] 영역을 잘라 JPEG 데이터로 만든다.
    /// [outWidth] 는 결과 가로 픽셀, 세로는 프레임 비율을 따른다. 실패하면 nil.
    static func crop(_ image: UIImage, frame: CGSize, scale: CGFloat, offset: CGSize,
                     outWidth: CGFloat, quality: CGFloat = 0.9) -> Data? {
        guard frame.width > 0, frame.height > 0,
              let cg = image.normalizedUp().cgImage else { return nil }

        let bw = CGFloat(cg.width)
        let bh = CGFloat(cg.height)
        let dispScale = max(frame.width / bw, frame.height / bh) * scale
        guard dispScale > 0 else { return nil }
        let left = (frame.width - bw * dispScale) / 2 + offset.width
        let top = (frame.height - bh * dispScale) / 2 + offset.height

        // 프레임(0..frameW, 0..frameH)에 대응하는 원본 사각형(px).
        var sx = -left / dispScale
        var sy = -top / dispScale
        var sw = frame.width / dispScale
        var sh = frame.height / dispScale
        sx = min(max(sx, 0), bw - 1)
        sy = min(max(sy, 0), bh - 1)
        sw = min(max(sw, 1), bw - sx)
        sh = min(max(sh, 1), bh - sy)

        guard let cropped = cg.cropping(to: CGRect(x: sx, y: sy, width: sw, height: sh)) else { return nil }

        let outHeight = (outWidth * frame.height / frame.width).rounded()
        let outSize = CGSize(width: outWidth.rounded(), height: max(outHeight, 1))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1                      // 픽셀 = 포인트(결과 해상도를 정확히 고정)
        format.opaque = true
        let rendered = UIGraphicsImageRenderer(size: outSize, format: format).image { _ in
            UIImage(cgImage: cropped).draw(in: CGRect(origin: .zero, size: outSize))
        }
        return rendered.jpegData(compressionQuality: quality)
    }

    /// 프레임을 항상 덮도록 offset 을 제한한 값 — 확대/드래그 제스처가 공유한다.
    /// (Android `CropController.onTransform` 의 클램프와 동일.)
    static func clampedOffset(_ offset: CGSize, image: CGSize, frame: CGSize, scale: CGFloat) -> CGSize {
        guard image.width > 0, image.height > 0, frame.width > 0, frame.height > 0 else { return .zero }
        let dispScale = max(frame.width / image.width, frame.height / image.height) * scale
        let maxX = max((image.width * dispScale - frame.width) / 2, 0)
        let maxY = max((image.height * dispScale - frame.height) / 2, 0)
        return CGSize(width: min(max(offset.width, -maxX), maxX),
                      height: min(max(offset.height, -maxY), maxY))
    }
}

extension UIImage {
    /// EXIF 회전이 걸린 이미지를 위로 세운 새 이미지로 — cgImage 픽셀 좌표로 계산하기 전에 한 번 편다.
    func normalizedUp() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = scale
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
