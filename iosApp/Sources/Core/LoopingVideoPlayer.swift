import AVKit
import SwiftUI

/// 짧은 영상(3초 이내)을 루프 재생하는 뷰. 업로드 미리보기 + 상세 조회 공용.
/// (Android core/ui/VideoPlayer.kt LoopingVideoPlayer 패리티 — iOS 는 AVQueuePlayer + AVPlayerLooper.)
///
/// - [url]: 로컬 임시 파일(미리보기) 또는 원격 Storage URL(조회).
/// - [muted]: true 면 음소거(자동재생용).
/// - [onFirstFrameRendered]: 실제로 재생이 시작되어 프레임이 화면에 그려지는 시점(Android
///   `LoopingVideoPlayer.onFirstFrameRendered` = `MEDIA_INFO_VIDEO_RENDERING_START` 패리티)에 1회 호출.
struct LoopingVideoPlayer: View {
    let url: URL
    var muted: Bool = true
    var onFirstFrameRendered: () -> Void = {}

    @State private var player: AVQueuePlayer?
    @State private var looper: AVPlayerLooper?
    @State private var timeObserver: Any?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear { setup() }
            .onDisappear {
                player?.pause()
                if let observer = timeObserver {
                    player?.removeTimeObserver(observer)
                    timeObserver = nil
                }
            }
    }

    private func setup() {
        guard player == nil else { return }
        let item = AVPlayerItem(url: url)
        let queue = AVQueuePlayer(playerItem: item)
        queue.isMuted = muted
        // templateItem 을 반복 큐잉해 끊김 없이 루프.
        looper = AVPlayerLooper(player: queue, templateItem: item)
        player = queue
        queue.play()

        // 시간이 0을 넘는 첫 콜백 = 첫 프레임이 실제로 재생 중이라는 뜻(prepared 보다 늦은 시점).
        let interval = CMTime(seconds: 0.03, preferredTimescale: 600)
        timeObserver = queue.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
            guard time.seconds > 0 else { return }
            onFirstFrameRendered()
            if let observer = timeObserver {
                queue.removeTimeObserver(observer)
                timeObserver = nil
            }
        }
    }
}
