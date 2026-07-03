import AVKit
import SwiftUI

/// 짧은 영상(3초 이내)을 루프 재생하는 뷰. 업로드 미리보기 + 상세 조회 공용.
/// (Android core/ui/VideoPlayer.kt LoopingVideoPlayer 패리티 — iOS 는 AVQueuePlayer + AVPlayerLooper.)
///
/// - [url]: 로컬 임시 파일(미리보기) 또는 원격 Storage URL(조회).
/// - [muted]: true 면 음소거(자동재생용).
struct LoopingVideoPlayer: View {
    let url: URL
    var muted: Bool = true

    @State private var player: AVQueuePlayer?
    @State private var looper: AVPlayerLooper?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear { setup() }
            .onDisappear { player?.pause() }
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
    }
}
