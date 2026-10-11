import AVFoundation
import SwiftUI
import UIKit

/// 공유 화면 — 공유 버튼을 누르면 뜨는 전체 화면(fullScreenCover).
/// 뜨자마자 스토리용 영상(8초)을 만들고(진행률 표시), 다 되면 반복 재생으로 미리 보여 준다.
/// 아래 두 버튼: **SNS에 공유**(인스타 스토리, 없으면 공유 시트) · **저장**(사진 앱).
/// 예전 공유 카드 편집 화면(`ShareCardEditorView`)을 대체한다 — 편집 기능은 없다(2026-10-11 사용자 결정).
/// Android `ShareVideoDialog` 대응.
struct ShareVideoView: View {
    let diary: Diary
    /// 지구 위에 같이 띄울 내 별 조회용(fullScreenCover 로 뜨는 화면이라 환경 객체 대신 값으로 받는다).
    let myUid: String?

    @Environment(\.dismiss) private var dismiss
    @State private var progress: Float = 0
    @State private var videoURL: URL?
    @State private var failed = false
    @State private var saving = false
    /// 저장 결과 안내 — 이 화면 안에서 잠깐 띄운다(전역 토스트는 fullScreenCover 뒤에 가려질 수 있다).
    @State private var notice: String?

    private var accent: Color { StarStyle.color(diary.starColor) }

    var body: some View {
        VStack(spacing: 0) {
            header
            preview
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            buttons
                .padding(.top, 14)
                .padding(.bottom, 18)
        }
        .padding(.horizontal, 18)
        .background(Color(hex: 0x090D16).ignoresSafeArea())
        .overlay(alignment: .bottom) { noticeView }
        // 화면이 닫히면 이 task 가 취소되고, 렌더러는 다음 프레임에서 멈춘다.
        .task {
            let url = await ShareVideo.generate(diary: diary, myUid: myUid) { progress = $0 }
            if let url { videoURL = url } else if !Task.isCancelled { failed = true }
        }
    }

    private var header: some View {
        HStack {
            Text(LocaleManager.shared.t(.shareVideoTitle))
                .font(.minSans(17, .semibold))
                .foregroundStyle(Theme.textPrimary)
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
        }
    }

    /// 미리보기 — 남는 공간에 9:16 으로 맞춘다.
    private var preview: some View {
        ZStack {
            Color(hex: 0x04050C)
            if let url = videoURL {
                LoopingVideoView(url: url)
            } else if failed {
                Text(LocaleManager.shared.t(.shareFailed))
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textSecondary)
                    .padding(24)
            } else {
                VStack(spacing: 14) {
                    StarLoadingView(size: 40)
                    Text(String(format: LocaleManager.shared.t(.shareVideoMaking), Int(progress * 100)))
                        .font(.minSans(13))
                        .foregroundStyle(Theme.textSecondary)
                }
            }
        }
        .aspectRatio(9.0 / 16.0, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private var buttons: some View {
        HStack(spacing: 10) {
            Button {
                guard let url = videoURL, let id = diary.id, !id.isEmpty else { return }
                ShareVideo.shareToSns(url, diaryId: id)
            } label: {
                Text(LocaleManager.shared.t(.shareToStory))
                    .font(.minSans(13))
                    .foregroundStyle(videoURL == nil ? Theme.textSecondary : Color(hex: 0x0B0F18))
                    .frame(maxWidth: .infinity).frame(height: 48)
                    .background(videoURL == nil ? Color.white.opacity(0.06) : accent, in: RoundedRectangle(cornerRadius: 13))
            }
            Button {
                guard let url = videoURL, !saving else { return }
                saving = true
                Task {
                    let ok = await ShareVideo.saveToPhotos(url)
                    saving = false
                    show(LocaleManager.shared.t(ok ? .shareVideoSaved : .shareVideoSaveFailed))
                }
            } label: {
                Text(LocaleManager.shared.t(.shareVideoSave))
                    .font(.minSans(13))
                    .foregroundStyle(videoURL == nil ? Theme.textSecondary : Theme.textPrimary)
                    .frame(maxWidth: .infinity).frame(height: 48)
                    .background(Color.white.opacity(videoURL == nil ? 0.06 : 0.10), in: RoundedRectangle(cornerRadius: 13))
            }
        }
        .buttonStyle(.plain)
        .disabled(videoURL == nil || saving)
    }

    @ViewBuilder
    private var noticeView: some View {
        if let notice {
            Text(notice)
                .font(.minSans(13))
                .foregroundStyle(.white)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(Color.black.opacity(0.78), in: Capsule())
                .padding(.bottom, 84)
                .transition(.opacity)
        }
    }

    private func show(_ message: String) {
        withAnimation(.easeOut(duration: 0.2)) { notice = message }
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation(.easeIn(duration: 0.25)) { if notice == message { notice = nil } }
        }
    }
}

/// 만든 영상을 소리 없이 반복 재생(영상에는 소리가 없다). 화면에서 사라지면 재생을 멈춘다.
private struct LoopingVideoView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PlayerView {
        let view = PlayerView()
        view.play(url)
        return view
    }

    func updateUIView(_ uiView: PlayerView, context: Context) {}

    static func dismantleUIView(_ uiView: PlayerView, coordinator: ()) { uiView.stop() }

    final class PlayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        private let player = AVQueuePlayer()
        private var looper: AVPlayerLooper?

        func play(_ url: URL) {
            looper = AVPlayerLooper(player: player, templateItem: AVPlayerItem(url: url))
            player.isMuted = true
            if let layer = layer as? AVPlayerLayer {
                layer.player = player
                layer.videoGravity = .resizeAspect
            }
            player.play()
        }

        func stop() {
            player.pause()
            looper = nil
        }
    }
}
