import AVFoundation
import ImageIO
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// 부메랑(3초 움짤) 상수 — Android `BoomerangHelper` 패리티.
enum BoomerangConfig {
    /// 촬영으로 담는 프레임 수(약 1.5초의 순간).
    static let captureFrames = 12
    /// 캡처 프레임 간격(초).
    static let captureInterval: CFTimeInterval = 0.125
    /// 재생 프레임 지연(초) — 22프레임 × 0.13s ≈ 2.9초 루프.
    static let frameDelay: Double = 0.13
    /// 결과 GIF 폭(4:3 → 400×300). 저화질/저용량(사용자 요구: 용량 우선).
    static let targetWidth: CGFloat = 400
    static let aspect: CGFloat = 4.0 / 3.0

    /// 부메랑 시퀀스 — 정방향 + 역방향(양 끝 중복 제거).
    static func boomerangSequence(_ capture: [UIImage]) -> [UIImage] {
        guard capture.count >= 3 else { return capture }
        return capture + capture.reversed().dropFirst().dropLast()
    }

    /// ImageIO 로 무한 루프 GIF 인코딩.
    static func encodeGif(frames: [UIImage], delay: Double) -> Data? {
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(
            data, UTType.gif.identifier as CFString, frames.count, nil
        ) else { return nil }
        let gifProps = [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFLoopCount: 0]] as CFDictionary
        CGImageDestinationSetProperties(dest, gifProps)
        let frameProps = [kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: delay]] as CFDictionary
        for f in frames {
            guard let cg = f.cgImage else { continue }
            CGImageDestinationAddImage(dest, cg, frameProps)
        }
        guard CGImageDestinationFinalize(dest) else { return nil }
        return data as Data
    }
}

/// AVCaptureSession 카메라 — 프리뷰 + 촬영 시 프레임 버스트 수집(부메랑용).
/// connection 에서 세로 방향/전면 미러를 미리 적용해 버퍼가 화면 그대로 들어온다.
final class BoomerangCamera: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    @Published var progress = 0            // 캡처 진행(0..captureFrames)
    @Published var position: AVCaptureDevice.Position = .back
    /// 캡처 완료 콜백(메인 스레드) — 정규화된 프레임 목록.
    var onComplete: (([UIImage]) -> Void)?

    private let output = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "boomerang.session")
    private let ciContext = CIContext()
    private var frames: [UIImage] = []
    private var lastCaptureAt: CFTimeInterval = 0
    private var capturing = false

    /// 권한 확인(필요 시 요청) 후 세션 시작. 거부되면 granted=false.
    func checkPermissionAndStart(_ done: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startSession(); done(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.startSession() }
                    done(granted)
                }
            }
        default:
            done(false)
        }
    }

    private func startSession() {
        sessionQueue.async { [self] in
            configure(for: .back)
            session.startRunning()
        }
    }

    func stop() {
        sessionQueue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }

    /// 전/후면 전환.
    func flip() {
        let next: AVCaptureDevice.Position = position == .back ? .front : .back
        position = next
        sessionQueue.async { [self] in configure(for: next) }
    }

    private func configure(for position: AVCaptureDevice.Position) {
        session.beginConfiguration()
        session.sessionPreset = .vga640x480 // 저해상도로 충분(결과 400px) + 가벼움
        for input in session.inputs { session.removeInput(input) }
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
           let input = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(input) {
            session.addInput(input)
        }
        if !session.outputs.contains(output) {
            output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(self, queue: sessionQueue)
            if session.canAddOutput(output) { session.addOutput(output) }
        }
        if let conn = output.connection(with: .video) {
            if conn.isVideoOrientationSupported { conn.videoOrientation = .portrait }
            if conn.isVideoMirroringSupported {
                conn.automaticallyAdjustsVideoMirroring = false
                conn.isVideoMirrored = position == .front
            }
        }
        session.commitConfiguration()
    }

    /// 촬영 시작 — captureFrames 장을 간격에 맞춰 모은 뒤 onComplete.
    func beginCapture() {
        sessionQueue.async { [self] in
            frames.removeAll()
            lastCaptureAt = 0
            capturing = true
        }
        progress = 0
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        guard capturing, let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let now = CACurrentMediaTime()
        if !frames.isEmpty, now - lastCaptureAt < BoomerangConfig.captureInterval { return }
        lastCaptureAt = now

        guard let img = normalize(pixelBuffer) else { return }
        frames.append(img)
        let count = frames.count
        DispatchQueue.main.async { [weak self] in self?.progress = count }
        if count >= BoomerangConfig.captureFrames {
            capturing = false
            let done = frames
            frames = []
            DispatchQueue.main.async { [weak self] in self?.onComplete?(done) }
        }
    }

    /// 픽셀 버퍼 → 4:3 센터 크롭 + 400px 다운스케일 UIImage. (방향/미러는 connection 에서 처리됨)
    private func normalize(_ buffer: CVPixelBuffer) -> UIImage? {
        let ci = CIImage(cvPixelBuffer: buffer)
        guard let cg = ciContext.createCGImage(ci, from: ci.extent) else { return nil }
        let w = CGFloat(cg.width), h = CGFloat(cg.height)

        // 4:3 센터 크롭(프리뷰 resizeAspectFill 과 동일 영역)
        var cropW = w, cropH = h
        if w / h > BoomerangConfig.aspect { cropW = h * BoomerangConfig.aspect }
        else { cropH = w / BoomerangConfig.aspect }
        let cropRect = CGRect(x: (w - cropW) / 2, y: (h - cropH) / 2, width: cropW, height: cropH)
        guard let cropped = cg.cropping(to: cropRect) else { return nil }

        let targetW = BoomerangConfig.targetWidth
        let targetH = (targetW / BoomerangConfig.aspect).rounded()
        // scale=1 고정 — 레티나 배율로 프레임이 3배 커지면 GIF 용량이 폭증한다.
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: targetW, height: targetH), format: fmt)
        return renderer.image { _ in
            UIImage(cgImage: cropped).draw(in: CGRect(x: 0, y: 0, width: targetW, height: targetH))
        }
    }
}

/// AVCaptureVideoPreviewLayer 를 담는 프리뷰 뷰.
struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewUIView {
        let v = PreviewUIView()
        v.previewLayer.session = session
        v.previewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}
}

/// 애니메이션 GIF 표시(UIImageView.animatedImage) — 로컬 Data 재생.
struct GifImageView: UIViewRepresentable {
    let data: Data

    func makeUIView(context: Context) -> UIImageView {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFill
        iv.clipsToBounds = true
        // SwiftUI 프레임을 따르도록(고유 크기로 레이아웃이 커지는 것 방지)
        iv.setContentHuggingPriority(.defaultLow, for: .horizontal)
        iv.setContentHuggingPriority(.defaultLow, for: .vertical)
        iv.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        iv.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        iv.image = GifImageView.animatedImage(from: data)
        context.coordinator.lastHash = data.hashValue
        return iv
    }

    func updateUIView(_ uiView: UIImageView, context: Context) {
        // 같은 데이터면 재디코드하지 않는다.
        guard context.coordinator.lastHash != data.hashValue else { return }
        context.coordinator.lastHash = data.hashValue
        uiView.image = GifImageView.animatedImage(from: data)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator { var lastHash: Int = 0 }

    /// GIF Data → 프레임 지연 합산 animatedImage.
    static func animatedImage(from data: Data) -> UIImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let count = CGImageSourceGetCount(src)
        var images: [UIImage] = []
        var duration = 0.0
        for i in 0..<count {
            guard let cg = CGImageSourceCreateImageAtIndex(src, i, nil) else { continue }
            let props = CGImageSourceCopyPropertiesAtIndex(src, i, nil) as? [CFString: Any]
            let gif = props?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            let unclamped = gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double
            let delay = (unclamped.flatMap { $0 > 0 ? $0 : nil })
                ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double ?? 0.1)
            duration += delay
            images.append(UIImage(cgImage: cg))
        }
        guard images.count > 1 else { return images.first }
        return UIImage.animatedImage(with: images, duration: duration)
    }
}

/// 원격 GIF(부메랑 움짤) — URL 에서 데이터를 받아 재생. 상세 화면용.
struct RemoteGifView: View {
    let urlString: String
    @State private var data: Data?

    var body: some View {
        Group {
            if let data {
                GifImageView(data: data)
            } else {
                Theme.surfaceAlt.overlay(ProgressView().tint(Theme.mint))
            }
        }
        .task(id: urlString) {
            guard let url = URL(string: urlString) else { return }
            data = try? await URLSession.shared.data(from: url).0
        }
    }
}

/// URL 이 부메랑 GIF(움짤)인지 — Storage 경로가 `.gif` 를 포함하는지로 판별. (Android isGifUrl 패리티)
func isGifUrl(_ url: String) -> Bool {
    url.lowercased().contains(".gif")
}
