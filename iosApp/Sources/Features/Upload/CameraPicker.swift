import AVFoundation
import SwiftUI
import UIKit

/// 카메라로 사진 1장 촬영 — Android `ActivityResultContracts.TakePicture()` 대응.
/// 결과는 JPEG Data 로 돌려준다(업로드 경로가 이미지 Data 를 쓰므로 동일 형식).
/// 취소하면 `onPicked(nil)`.
struct CameraPicker: UIViewControllerRepresentable {
    /// 촬영 결과(JPEG). 취소 시 nil.
    let onPicked: (Data?) -> Void

    /// 이 기기에서 카메라를 쓸 수 있는지(시뮬레이터는 false) — 호출부에서 토스트로 막을 때 사용.
    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    /// 카메라 권한 확인(필요하면 요청). Android launchCamera 의 권한 게이팅 대응.
    static func requestPermission(_ done: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            done(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { done(granted) }
            }
        default:
            done(false)
        }
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let onPicked: (Data?) -> Void
        init(onPicked: @escaping (Data?) -> Void) { self.onPicked = onPicked }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let image = info[.originalImage] as? UIImage
            // 업로드 용량 방어 — 긴 변 1600px 로 축소 후 JPEG 0.85.
            onPicked(image.flatMap { Self.jpegData($0) })
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPicked(nil)
        }

        /// 촬영 원본(수 MB)을 그대로 올리지 않도록 축소 + JPEG 인코딩.
        private static func jpegData(_ image: UIImage, maxSide: CGFloat = 1600) -> Data? {
            let w = image.size.width, h = image.size.height
            guard w > 0, h > 0 else { return nil }
            let longest = max(w, h)
            let scale = longest > maxSide ? maxSide / longest : 1
            guard scale < 1 else { return image.jpegData(compressionQuality: 0.85) }
            let size = CGSize(width: (w * scale).rounded(), height: (h * scale).rounded())
            let fmt = UIGraphicsImageRendererFormat()
            fmt.scale = 1
            return UIGraphicsImageRenderer(size: size, format: fmt)
                .image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
                .jpegData(compressionQuality: 0.85)
        }
    }
}
