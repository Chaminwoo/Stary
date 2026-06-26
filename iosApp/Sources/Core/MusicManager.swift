import AVFoundation
import SwiftUI

/// 앱 전역 배경음악 + 효과음 관리자 — Android `core.util.MusicManager` 의 Swift 포팅.
///
/// - 기본 켜짐. 기본 음악 = [MusicCatalog.defaultId].
/// - 트랙 전환은 [playTrack] (이어듣기용 위치 인자), 확정은 [commitSelectedTrack].
/// - 효과음: 다이얼 회전음([setDialTurning], 겹침 없이 끝나면 아직 돌리는 중일 때만 재생),
///   다이어리 열람음([playOpenDiary], 배경음악보다 작게).
final class MusicManager: ObservableObject {
    static let shared = MusicManager()

    private let keyEnabled = "music_enabled"
    private let keyTrack = "music_track"

    @Published private(set) var enabled: Bool
    @Published private(set) var selectedTrackId: String

    private var player: AVAudioPlayer?
    private var playingId: String?

    // 효과음
    private var openPlayer: AVAudioPlayer?
    private let openVolume: Float = 0.35
    private var dialPlayer: AVAudioPlayer?
    private var dialDelegate: DialDelegate?
    private var dialTurning = false
    private let dialVolume: Float = 0.6

    private init() {
        let d = UserDefaults.standard
        enabled = (d.object(forKey: keyEnabled) as? Bool) ?? true
        selectedTrackId = d.string(forKey: keyTrack) ?? MusicCatalog.defaultId
        playingId = selectedTrackId
        configureSession()
    }

    private func configureSession() {
        // .ambient: 다른 앱 오디오를 끊지 않고, 무음 스위치를 존중한다(앱 BGM 용).
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func makePlayer(_ resName: String) -> AVAudioPlayer? {
        guard let url = Bundle.main.url(forResource: resName, withExtension: "mp3") else { return nil }
        return try? AVAudioPlayer(contentsOf: url)
    }

    // MARK: - 배경음악

    /// 앱 전면 복귀 or 켜짐 → 현재 트랙을 마지막 위치에서 재생.
    func resume() {
        guard enabled else { return }
        if player == nil {
            let id = playingId ?? selectedTrackId
            guard let res = MusicCatalog.byId(id)?.resName, let p = makePlayer(res) else { return }
            p.numberOfLoops = -1
            player = p
            playingId = id
        }
        if let p = player, !p.isPlaying { p.play() }
    }

    /// 일시정지 — 현재 위치 보존.
    func pause() { player?.pause() }

    /// 토글 — on/off 저장 후 즉시 반영.
    func setActive(_ value: Bool) {
        guard enabled != value else { return }
        enabled = value
        UserDefaults.standard.set(value, forKey: keyEnabled)
        if value { resume() } else { pause() }
    }

    /// 현재 재생 위치(초).
    var currentTime: TimeInterval { player?.currentTime ?? 0 }

    /// 지정 트랙을 [time] 위치부터 재생(기존 교체). 미리듣기/이어듣기 공용.
    /// 음소거면 준비만 하고 재생하지 않는다.
    func playTrack(_ id: String, at time: TimeInterval = 0) {
        guard let res = MusicCatalog.byId(id)?.resName, let p = makePlayer(res) else { return }
        p.numberOfLoops = -1
        let dur = p.duration
        p.currentTime = (dur > 0) ? min(max(time, 0), max(dur - 0.2, 0)) : max(time, 0)
        if enabled { p.play() }
        player?.stop()
        player = p
        playingId = id
    }

    /// 영구 선택 트랙 확정(저장). 재생은 이미 [playTrack] 으로 진행 중.
    func commitSelectedTrack(_ id: String) {
        selectedTrackId = id
        playingId = id
        UserDefaults.standard.set(id, forKey: keyTrack)
    }

    // MARK: - 효과음

    /// 다이어리 열람 효과음(배경음악보다 작게). 음소거면 무음.
    func playOpenDiary() {
        guard enabled, let p = makePlayer("open_diary") else { return }
        p.volume = openVolume
        openPlayer = p
        p.play()
    }

    /// 다이얼 회전 상태 — true(돌리기 시작)면 회전음, 겹치지 않게, 끝났을 때 아직 돌리는 중이면 재생.
    func setDialTurning(_ turning: Bool) {
        if turning && !enabled { return }
        dialTurning = turning
        if turning { startDialIfNeeded() }
    }

    private func startDialIfNeeded() {
        if dialPlayer?.isPlaying == true { return } // 이미 재생 중이면 겹치지 않음
        guard let p = makePlayer("turning_dial") else { return }
        p.volume = dialVolume
        let del = DialDelegate { [weak self] in
            guard let self, self.dialTurning, self.enabled else { return }
            self.dialPlayer?.currentTime = 0
            self.dialPlayer?.play() // 아직 돌리는 중이면 이어서
        }
        dialDelegate = del
        p.delegate = del
        dialPlayer = p
        p.play()
    }
}

/// AVAudioPlayer 완료 콜백 → 클로저.
private final class DialDelegate: NSObject, AVAudioPlayerDelegate {
    private let onFinish: () -> Void
    init(_ onFinish: @escaping () -> Void) { self.onFinish = onFinish }
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { onFinish() }
}
