import SwiftUI
import UIKit

/// 부메랑(3초 움짤) 커스텀 촬영 화면 — Android `BoomerangCaptureScreen` 패리티.
///
/// 하단에 카메라 전환 버튼 + 가운데 촬영 버튼. 촬영을 누르면 약 1.5초간 프레임을 모아
/// 정→역으로 이어 붙인 3초 GIF(저화질)로 만들고, 확인 후 [onResult] 로 데이터를 돌려준다.
struct BoomerangCaptureView: View {
    let onResult: (Data) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var camera = BoomerangCamera()

    private enum Stage { case live, capturing, processing, review }
    @State private var stage: Stage = .live
    @State private var reviewFrames: [UIImage] = []
    @State private var gifData: Data?
    @State private var frameIdx = 0

    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.02, blue: 0.06).ignoresSafeArea()
            VStack(spacing: 0) {
                topBar
                Spacer()
                frameArea
                statusArea
                    .padding(.top, 14)
                Spacer()
                bottomControls
            }
        }
        .onAppear {
            camera.onComplete = { frames in handleCaptured(frames) }
            camera.checkPermissionAndStart { granted in
                if !granted { dismiss() }
            }
        }
        .onDisappear { camera.stop() }
    }

    // ── 상단 바 ──
    private var topBar: some View {
        ZStack {
            Text("움직이는 사진")
                .font(.subheadline).bold()
                .foregroundStyle(.white)
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(.white)
                        .padding(12)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    // ── 4:3 프레임 — 프리뷰 또는 결과 미리보기(움짤 재생) ──
    private var frameArea: some View {
        ZStack {
            if stage == .review, !reviewFrames.isEmpty {
                Image(uiImage: reviewFrames[frameIdx % reviewFrames.count])
                    .resizable()
                    .scaledToFill()
                    .task(id: stage == .review) {
                        while stage == .review {
                            try? await Task.sleep(nanoseconds: UInt64(BoomerangConfig.frameDelay * 1_000_000_000))
                            frameIdx = (frameIdx + 1) % max(reviewFrames.count, 1)
                        }
                    }
            } else {
                CameraPreviewView(session: camera.session)
                if stage == .processing {
                    Color.black.opacity(0.6)
                    VStack(spacing: 12) {
                        ProgressView().tint(Theme.mint)
                        Text("움직이는 사진을 만드는 중…")
                            .font(.footnote).foregroundStyle(.white)
                    }
                }
            }
        }
        .aspectRatio(BoomerangConfig.aspect, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.white.opacity(0.15), lineWidth: 1))
    }

    // ── 안내 문구 / 캡처 진행바 ──
    @ViewBuilder
    private var statusArea: some View {
        switch stage {
        case .live:
            Text("버튼을 누르면 순간을 이어 붙여\n3초 움직이는 사진을 만들어요")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.65))
        case .capturing:
            VStack(spacing: 8) {
                Text("담는 중…")
                    .font(.footnote).bold()
                    .foregroundStyle(Theme.mint)
                ProgressView(value: Double(camera.progress), total: Double(BoomerangConfig.captureFrames))
                    .tint(Theme.mint)
                    .frame(width: 180)
            }
        default:
            EmptyView()
        }
    }

    // ── 하단 컨트롤 ──
    @ViewBuilder
    private var bottomControls: some View {
        if stage == .review {
            HStack(spacing: 14) {
                Button {
                    gifData = nil
                    reviewFrames = []
                    stage = .live
                } label: {
                    Text("다시 찍기")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(.white)
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.35)))
                }
                Button {
                    if let gifData { onResult(gifData) }
                } label: {
                    Text("이 장면 사용")
                        .bold()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Theme.mint, in: RoundedRectangle(cornerRadius: 14))
                        .foregroundStyle(.black)
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 26)
        } else {
            // 하단 바 — 왼쪽 카메라 전환 + 가운데 촬영 버튼(요청 레이아웃)
            ZStack {
                HStack {
                    Button { camera.flip() } label: {
                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                            .font(.system(size: 22))
                            .foregroundStyle(.white)
                            .frame(width: 52, height: 52)
                            .background(.white.opacity(0.1), in: Circle())
                    }
                    .disabled(stage != .live)
                    .padding(.leading, 40)
                    Spacer()
                }

                // 촬영 버튼 — 이중 링, 캡처 중이면 민트 링
                Button {
                    guard stage == .live else { return }
                    stage = .capturing
                    frameIdx = 0
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
            .padding(.vertical, 26)
        }
    }

    /// 캡처 완료 → 부메랑 시퀀스 구성 + GIF 인코딩(백그라운드) → 리뷰.
    private func handleCaptured(_ frames: [UIImage]) {
        stage = .processing
        Task {
            let seq = BoomerangConfig.boomerangSequence(frames)
            let data = await Task.detached(priority: .userInitiated) {
                BoomerangConfig.encodeGif(frames: seq, delay: BoomerangConfig.frameDelay)
            }.value
            await MainActor.run {
                if let data {
                    gifData = data
                    reviewFrames = seq
                    frameIdx = 0
                    stage = .review
                } else {
                    stage = .live
                }
            }
        }
    }
}
