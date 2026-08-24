import SwiftUI
import UIKit

/// 업로드 화면 첨부(사진 1장 / 3초 움짤 여러 장)의 크롭 상태 —
/// Android `UploadScreen.CropController` 패리티(좌표 모델/클램프 동일).
///
/// 프레임을 항상 덮는 cover 배율 위에 사용자 핀치([scale])와 드래그([offset])를 얹고,
/// 프레임 밖 빈자리가 생기지 않게 offset 을 클램프한다. 3초 움짤은 [minScale] 을
/// **촬영 원본이 통째로 들어오는 배율**까지 낮춰 화각 전체를 담을 수도 있다.
@MainActor
final class MediaCropState: ObservableObject {
    /// 크롭 대상 원본. 사진은 1장, 3초 움짤은 촬영 프레임 전부.
    @Published private(set) var frames: [UIImage] = []
    /// 미리보기 재생 시퀀스(움짤은 정→역, 사진은 1장 그대로).
    @Published private(set) var playback: [UIImage] = []
    @Published var frameIndex = 0
    @Published var scale: CGFloat = 1
    @Published var offset: CGSize = .zero
    /// 축소 하한 — 사진은 1(프레임을 빈틈없이 덮는다), 움짤은 화각 전체가 들어오는 배율.
    @Published var minScale: CGFloat = 1
    /// 크롭 프레임 실측 크기(pt) — 제스처/저장이 같은 기준을 쓴다.
    @Published private(set) var frameSize: CGSize = .zero

    /// 촬영 화면에서 정해 온 크롭 상태(offset 은 프레임 대비 비율) — 프레임을 잰 뒤 반영한다.
    private var pendingBoomerang: (scale: CGFloat, nx: CGFloat, ny: CGFloat)?

    var isEmpty: Bool { frames.isEmpty }
    var isAnimated: Bool { playback.count > 1 }
    var first: UIImage? { frames.first }
    var current: UIImage? { playback.isEmpty ? nil : playback[frameIndex % playback.count] }

    func clear() {
        frames = []; playback = []; frameIndex = 0
        pendingBoomerang = nil
        minScale = 1
        reset()
    }

    func setPhoto(_ image: UIImage) {
        frames = [image]; playback = [image]; frameIndex = 0
        pendingBoomerang = nil
        minScale = 1
        reset()
    }

    /// 3초 움짤 촬영 프레임을 건다. 촬영 화면에서 정한 위치·확대를 그대로 이어받는다.
    func setBoomerang(_ captured: [UIImage], scale startScale: CGFloat, offsetNorm: CGSize) {
        frames = captured
        playback = BoomerangConfig.boomerangSequence(captured)
        frameIndex = 0
        minScale = 1
        reset()
        pendingBoomerang = (startScale, offsetNorm.width, offsetNorm.height)
        applyPendingIfReady()
    }

    func setFrameSize(_ size: CGSize) {
        guard size.width > 0, size.height > 0, size != frameSize else { return }
        frameSize = size
        applyPendingIfReady()
        offset = ImageCrop.clampedOffset(offset, image: first?.size ?? .zero, frame: frameSize, scale: scale)
    }

    func reset() { scale = minScale; offset = .zero }

    /// 핀치/드래그 반영 — 배율은 [minScale]..4, offset 은 프레임을 벗어나지 않게 클램프.
    func apply(scale newScale: CGFloat, offset newOffset: CGSize) {
        guard let img = first, frameSize.width > 0, frameSize.height > 0 else { return }
        scale = min(max(newScale, minScale), 4)
        offset = ImageCrop.clampedOffset(newOffset, image: img.size, frame: frameSize, scale: scale)
    }

    private func applyPendingIfReady() {
        guard let p = pendingBoomerang, let img = first,
              frameSize.width > 0, frameSize.height > 0 else { return }
        minScale = BoomerangConfig.minScale(bmpW: img.size.width, bmpH: img.size.height,
                                            frameW: frameSize.width, frameH: frameSize.height)
        scale = min(max(p.scale, minScale), 4)
        offset = CGSize(width: p.nx * frameSize.width, height: p.ny * frameSize.height)
        pendingBoomerang = nil
    }
}

/// 고정 비율(4:3) 프레임 안에서 사진/3초 움짤을 cover-fit 으로 보여주고
/// **드래그로 위치, 두 손가락으로 확대/축소**를 받는다. 움짤이면 프레임을 돌려가며 재생한다.
/// (Android `UploadScreen.ImageCropFrame` 패리티 — 이 프레임이 곧 잘릴 영역이다.)
struct MediaCropFrame: View {
    @ObservedObject var state: MediaCropState

    @State private var dragStart: CGSize?
    @State private var pinchStart: CGFloat?

    var body: some View {
        // 높이는 폭에서 비율로 결정 — ScrollView 안에서는 세로 제안이 무한대라
        // GeometryReader 에 직접 aspectRatio 를 걸면 크기가 불안정해진다(빈 자리 차지).
        Color.clear
            .aspectRatio(ImageCrop.diaryAspect, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay { frameContent }
            .clipped()
            // 움짤 재생 — 사진(1장)일 땐 돌지 않는다.
            .task(id: state.playback.count) {
                guard state.playback.count > 1 else { return }
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: UInt64(BoomerangConfig.frameDelay * 1_000_000_000))
                    if Task.isCancelled { return }
                    state.frameIndex = (state.frameIndex + 1) % max(state.playback.count, 1)
                }
            }
    }

    private var frameContent: some View {
        GeometryReader { geo in
            let fw = geo.size.width
            let fh = geo.size.height
            ZStack {
                Color(hex: 0x14181F)
                if let img = state.current {
                    let bw = img.size.width
                    let bh = img.size.height
                    let disp = max(fw / bw, fh / bh) * state.scale
                    Image(uiImage: img)
                        .resizable()
                        .frame(width: bw * disp, height: bh * disp)
                        .offset(state.offset)
                } else {
                    StarLoadingView(size: 28)
                }
                cropGuides(fw: fw, fh: fh)
            }
            .frame(width: fw, height: fh)
            .contentShape(Rectangle())
            .gesture(cropGesture)
            .onAppear { state.setFrameSize(geo.size) }
            .onChange(of: geo.size) { state.setFrameSize($0) }
        }
    }

    /// 3분할 크롭 가이드(Android 동일).
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

    /// 드래그 + 핀치 동시 제스처.
    private var cropGesture: some Gesture {
        let drag = DragGesture()
            .onChanged { v in
                if dragStart == nil { dragStart = state.offset }
                let base = dragStart ?? state.offset
                state.apply(scale: state.scale,
                            offset: CGSize(width: base.width + v.translation.width,
                                           height: base.height + v.translation.height))
            }
            .onEnded { _ in dragStart = nil }
        let pinch = MagnificationGesture()
            .onChanged { m in
                if pinchStart == nil { pinchStart = state.scale }
                state.apply(scale: (pinchStart ?? state.scale) * m, offset: state.offset)
            }
            .onEnded { _ in pinchStart = nil }
        return drag.simultaneously(with: pinch)
    }
}
