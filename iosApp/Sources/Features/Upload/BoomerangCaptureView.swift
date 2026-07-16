import SwiftUI
import UIKit

/// 부메랑(3초 움짤) 커스텀 촬영 화면 — Android `BoomerangCaptureScreen` 패리티.
///
/// 프리뷰가 화면을 꽉 채우고 하단에 카메라 전환 + 가운데 촬영 버튼만 둔다(안내 텍스트 없음).
/// 촬영하면 약 1.5초간 프레임을 모으고, **사진 크롭처럼 드래그/핀치로 4:3 프레임 안에서
/// 위치·확대를 조정**한 뒤 확정하면 3초 GIF 로 만들어 [onResult] 로 돌려준다.
/// (iOS 는 시스템 뒤로가기가 없어 좌상단에 반투명 닫기만 최소로 유지 — Android 는 뒤로가기.)
struct BoomerangCaptureView: View {
    let onResult: (Data) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var camera = BoomerangCamera()

    private enum Stage { case live, capturing, adjust, encoding }
    @State private var stage: Stage = .live
    @State private var capturedFrames: [UIImage] = []
    @State private var frameIdx = 0

    // 크롭 상태(조정 단계) — Android CropController 와 동일 모델/클램프.
    @State private var cropScale: CGFloat = 1
    @State private var cropOffset: CGSize = .zero
    @State private var dragStartOffset: CGSize?
    @State private var pinchStartScale: CGFloat?
    /// 조정 프레임의 실제 표시 크기(pt) — 제스처/크롭 확정이 같은 기준을 쓰도록 보관.
    @State private var adjustFrameSize: CGSize = .zero
    /// 조정 진입 시 "찍힌 화면 전체가 들어오는 배율"을 한 번만 적용했는지.
    @State private var fitApplied = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if stage == .adjust || stage == .encoding {
                adjustUi
            } else {
                captureUi
            }
        }
        .onAppear {
            camera.onComplete = { frames in
                capturedFrames = frames
                cropScale = 1
                cropOffset = .zero
                fitApplied = false
                frameIdx = 0
                stage = .adjust
            }
            camera.checkPermissionAndStart { granted in
                if !granted { dismiss() }
            }
        }
        .onDisappear { camera.stop() }
    }

    // ── 촬영 모드 — 프리뷰 풀스크린 + 하단 컨트롤 ──
    private var captureUi: some View {
        ZStack {
            CameraPreviewView(session: camera.session)
                .ignoresSafeArea()

            // iOS 는 시스템 뒤로가기가 없어 최소한의 닫기만 남긴다(반투명).
            VStack {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.8))
                            .frame(width: 38, height: 38)
                            .background(.black.opacity(0.35), in: Circle())
                    }
                    .padding(.leading, 14)
                    Spacer()
                }
                Spacer()
            }

            VStack(spacing: 18) {
                Spacer()
                if stage == .capturing {
                    ProgressView(value: Double(camera.progress), total: Double(BoomerangConfig.captureFrames))
                        .tint(Theme.mint)
                        .frame(width: 180)
                }

                // 하단 바 — 왼쪽 카메라 전환 + 가운데 촬영 버튼
                ZStack {
                    HStack {
                        Button { camera.flip() } label: {
                            Image(systemName: "arrow.triangle.2.circlepath.camera")
                                .font(.system(size: 22))
                                .foregroundStyle(.white)
                                .frame(width: 52, height: 52)
                                .background(.white.opacity(0.14), in: Circle())
                        }
                        .disabled(stage != .live)
                        .padding(.leading, 40)
                        Spacer()
                    }

                    Button {
                        guard stage == .live else { return }
                        stage = .capturing
                        camera.beginCapture()
                    } label: {
                        ZStack {
                            Circle()
                                .strokeBorder(stage == .capturing ? Theme.mint : .white, lineWidth: 3)
                                .frame(width: 78, height: 78)
                            Circle()
                                .fill(stage == .capturing ? Theme.mint.opacity(0.5) : .white)
                                .frame(width: 62, height: 62)
                            Image(systemName: "infinity")
                                .font(.system(size: 24, weight: .semibold))
                                .foregroundStyle(.black)
                        }
                    }
                    .disabled(stage != .live)
                }
                .padding(.bottom, 26)
            }
        }
    }

    // ── 조정 모드 — 4:3 프레임 안에서 움짤 재생 + 드래그/핀치 크롭 ──
    private var adjustUi: some View {
        VStack(spacing: 0) {
            Spacer()

            GeometryReader { geo in
                let fw = geo.size.width
                let fh = geo.size.height
                ZStack {
                    if let img = currentFrame {
                        let bw = img.size.width
                        let bh = img.size.height
                        let disp = max(fw / bw, fh / bh) * cropScale
                        Image(uiImage: img)
                            .resizable()
                            .frame(width: bw * disp, height: bh * disp)
                            .position(x: fw / 2 + cropOffset.width, y: fh / 2 + cropOffset.height)
                    }
                    cropGuides(fw: fw, fh: fh)
                    Color.clear.onAppear {
                        adjustFrameSize = geo.size
                        applyFitIfNeeded(frameW: fw, frameH: fh)
                    }
                }
                .contentShape(Rectangle())
                .gesture(cropGesture(frameW: fw, frameH: fh))
            }
            .aspectRatio(BoomerangConfig.aspect, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .clipped()
            .overlay(Rectangle().strokeBorder(.white.opacity(0.25), lineWidth: 1))
            .task(id: stage == .adjust) {
                let playback = BoomerangConfig.boomerangSequence(capturedFrames)
                while stage == .adjust, !playback.isEmpty {
                    try? await Task.sleep(nanoseconds: UInt64(BoomerangConfig.frameDelay * 1_000_000_000))
                    frameIdx = (frameIdx + 1) % playback.count
                }
            }

            Spacer()

            HStack(spacing: 14) {
                Button {
                    capturedFrames = []
                    fitApplied = false
                    stage = .live
                } label: {
                    Text(LocaleManager.shared.t(.boomerRetake))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(.white)
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.35)))
                }
                .disabled(stage == .encoding)
                Button {
                    confirmCrop()
                } label: {
                    Group {
                        if stage == .encoding {
                            StarLoadingView(size: 20, color: .black)
                        } else {
                            Text(LocaleManager.shared.t(.boomerUse)).bold()
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.mint, in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(.black)
                }
                .disabled(stage == .encoding)
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 26)
        }
    }

    /// 조정 진입 직후 한 번 — 찍힌 화면 전체가 프레임에 들어오도록 맞춘다(잘림 없음).
    /// 여기서 핀치로 확대하면 원하는 부분만 채워 담을 수 있다. (Android BoomerangAdjustUi 패리티)
    private func applyFitIfNeeded(frameW: CGFloat, frameH: CGFloat) {
        guard !fitApplied, let img = capturedFrames.first, frameW > 0, frameH > 0 else { return }
        cropScale = BoomerangConfig.minScale(
            bmpW: img.size.width, bmpH: img.size.height, frameW: frameW, frameH: frameH
        )
        cropOffset = .zero
        fitApplied = true
    }

    private var currentFrame: UIImage? {
        let playback = BoomerangConfig.boomerangSequence(capturedFrames)
        guard !playback.isEmpty else { return nil }
        return playback[frameIdx % playback.count]
    }

    private func cropGuides(fw: CGFloat, fh: CGFloat) -> some View {
        Path { p in
            p.move(to: CGPoint(x: fw / 3, y: 0)); p.addLine(to: CGPoint(x: fw / 3, y: fh))
            p.move(to: CGPoint(x: fw * 2 / 3, y: 0)); p.addLine(to: CGPoint(x: fw * 2 / 3, y: fh))
            p.move(to: CGPoint(x: 0, y: fh / 3)); p.addLine(to: CGPoint(x: fw, y: fh / 3))
            p.move(to: CGPoint(x: 0, y: fh * 2 / 3)); p.addLine(to: CGPoint(x: fw, y: fh * 2 / 3))
        }
        .stroke(.white.opacity(0.35), lineWidth: 1)
        .allowsHitTesting(false)
    }

    /// 드래그 + 핀치 동시 제스처 — 델타를 크롭 상태에 반영(클램프 포함).
    private func cropGesture(frameW: CGFloat, frameH: CGFloat) -> some Gesture {
        let drag = DragGesture()
            .onChanged { v in
                if dragStartOffset == nil { dragStartOffset = cropOffset }
                let base = dragStartOffset ?? cropOffset
                setCrop(
                    scale: cropScale,
                    offset: CGSize(width: base.width + v.translation.width,
                                   height: base.height + v.translation.height),
                    frameW: frameW, frameH: frameH
                )
            }
            .onEnded { _ in dragStartOffset = nil }
        let pinch = MagnificationGesture()
            .onChanged { m in
                if pinchStartScale == nil { pinchStartScale = cropScale }
                let base = pinchStartScale ?? cropScale
                setCrop(scale: base * m, offset: cropOffset, frameW: frameW, frameH: frameH)
            }
            .onEnded { _ in pinchStartScale = nil }
        return drag.simultaneously(with: pinch)
    }

    /// 크롭 상태 반영 — scale 은 (전체 화각이 다 들어오는 최소 배율)..4, offset 은 프레임을 벗어나지
    /// 않게 클램프(Android 동일). 최소 배율까지 축소하면 찍힌 화면이 하나도 잘리지 않는다.
    private func setCrop(scale: CGFloat, offset: CGSize, frameW: CGFloat, frameH: CGFloat) {
        guard let img = capturedFrames.first, frameW > 0, frameH > 0 else { return }
        let minS = BoomerangConfig.minScale(
            bmpW: img.size.width, bmpH: img.size.height, frameW: frameW, frameH: frameH
        )
        let s = min(max(scale, minS), 4)
        let disp = max(frameW / img.size.width, frameH / img.size.height) * s
        let dispW = img.size.width * disp
        let dispH = img.size.height * disp
        let maxX = max((dispW - frameW) / 2, 0)
        let maxY = max((dispH - frameH) / 2, 0)
        cropScale = s
        cropOffset = CGSize(
            width: min(max(offset.width, -maxX), maxX),
            height: min(max(offset.height, -maxY), maxY)
        )
    }

    /// 확정 — 크롭 상태로 전 프레임을 잘라 GIF 인코딩(백그라운드) 후 반환.
    private func confirmCrop() {
        // 제스처와 동일한 기준(조정 프레임 실측 크기). 실측 전이면 화면 폭으로 폴백.
        let fw = adjustFrameSize.width > 0 ? adjustFrameSize.width : UIScreen.main.bounds.width
        let fh = adjustFrameSize.height > 0 ? adjustFrameSize.height : fw / BoomerangConfig.aspect
        stage = .encoding
        let frames = capturedFrames
        let scale = cropScale
        let offset = cropOffset
        Task {
            let data = await Task.detached(priority: .userInitiated) {
                let cropped = BoomerangConfig.cropFrames(frames, frameW: fw, frameH: fh, scale: scale, offset: offset)
                let seq = BoomerangConfig.boomerangSequence(cropped)
                return BoomerangConfig.encodeGif(frames: seq, delay: BoomerangConfig.frameDelay)
            }.value
            await MainActor.run {
                if let data {
                    onResult(data)
                } else {
                    stage = .adjust
                }
            }
        }
    }
}
