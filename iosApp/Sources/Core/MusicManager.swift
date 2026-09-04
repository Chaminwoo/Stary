import AVFoundation
import SwiftUI

/// 앱 전역 배경음악 + 효과음 관리자 — Android `core.util.MusicManager` 의 Swift 포팅.
///
/// - 기본 켜짐. 기본 음악 = [MusicCatalog.defaultId].
/// - 트랙 전환은 [playTrack] (이어듣기용 위치 인자), 확정은 [commitSelectedTrack].
/// - 효과음: 맷돌(다이얼) 그라인딩음([dialTick]/[dialRelease], 실제 회전 속도에 비례해 반복),
///   다이어리 열람음([playOpenDiary], 배경음악보다 작게).
final class MusicManager: ObservableObject {
    static let shared = MusicManager()

    private let keyEnabled = "music_enabled"
    private let keyTrack = "music_track"
    private let keyMusicVol = "music_volume"
    private let keySfxVol = "sfx_volume"

    @Published private(set) var enabled: Bool
    @Published private(set) var selectedTrackId: String
    /// 배경음악 볼륨(0..1). 설정 화면에서 조절. (Android MusicManager.musicVolume 패리티)
    @Published private(set) var musicVolume: Float
    /// 효과음(SFX) 볼륨(0..1). 열람/다이얼 효과음에 곱해진다.
    @Published private(set) var sfxVolume: Float

    private var player: AVAudioPlayer?
    private var playingId: String?

    // 효과음
    private var openPlayer: AVAudioPlayer?
    private let openBaseVolume: Float = 0.35
    private var windPlayer: AVAudioPlayer?
    private var dialPlayer: AVAudioPlayer?
    private var dialDelegate: DialDelegate?
    private let dialBaseVolume: Float = 0.6
    /// 맷돌 눈금음 최소 간격(초) — 이보다 빠른 연속 호출은 뭉개짐 방지로 무시한다.
    /// (Android `MusicManager.DIAL_TICK_MIN_GAP_MS` 와 같은 값 — drift 금지.)
    private let dialTickMinGap: TimeInterval = 0.04
    private var lastDialTickAt: TimeInterval = 0
    /// 놓았을 때 관성으로 잦아드는 "드르륵" 잔향 간격(초, 점점 벌어짐) — Android `DIAL_RELEASE_GAPS_MS` 패리티.
    private let dialReleaseGaps: [TimeInterval] = [0.045, 0.070, 0.105, 0.150, 0.210]
    private var dialReleaseGen = 0

    private init() {
        let d = UserDefaults.standard
        enabled = (d.object(forKey: keyEnabled) as? Bool) ?? true
        selectedTrackId = d.string(forKey: keyTrack) ?? MusicCatalog.defaultId
        // 저장값 없으면 1(최대). UserDefaults.float 은 부재 시 0 이라 존재 여부로 분기.
        musicVolume = (d.object(forKey: keyMusicVol) != nil ? d.float(forKey: keyMusicVol) : 1).clampedUnit
        sfxVolume = (d.object(forKey: keySfxVol) != nil ? d.float(forKey: keySfxVol) : 1).clampedUnit
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
            p.volume = musicVolume
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
        p.volume = musicVolume
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

    /// 배경음악 볼륨 설정(0..1) — 저장 + 현재 재생 중인 player 에 즉시 반영.
    func updateMusicVolume(_ value: Float) {
        let v = value.clampedUnit
        guard musicVolume != v else { return }
        musicVolume = v
        player?.volume = v
        UserDefaults.standard.set(v, forKey: keyMusicVol)
    }

    /// 효과음 볼륨 설정(0..1) — 저장. 다음 효과음 재생부터 반영.
    func updateSfxVolume(_ value: Float) {
        let v = value.clampedUnit
        guard sfxVolume != v else { return }
        sfxVolume = v
        UserDefaults.standard.set(v, forKey: keySfxVol)
    }

    // MARK: - 효과음

    /// 다이어리 열람 효과음(배경음악보다 작게). 음소거면 무음.
    func playOpenDiary() {
        guard enabled, let p = makePlayer("open_diary") else { return }
        p.volume = openBaseVolume * sfxVolume
        openPlayer = p
        p.play()
    }

    /// 바람 효과음 — 내 다이어리 정렬 다이얼 선택 시(Android MusicManager.playWind 대응).
    func playWind() {
        guard enabled, let p = makePlayer("wind") else { return }
        p.volume = openBaseVolume * sfxVolume
        windPlayer = p
        p.play()
    }

    /// 맷돌(다이얼) 눈금음 — **실제로 돌릴 때마다**(각도 눈금을 지날 때) 호출한다. 고정 타이머가 아니라
    /// 호출 빈도 자체가 회전 속도이므로, 빠르게 돌리면 "드드드드" 촘촘하게, 천천히 돌리면 드문드문 울린다.
    /// 가만히 잡고만 있으면(호출이 없으면) 아무 소리도 나지 않는다. Android `MusicManager.dialTick` 패리티.
    func dialTick() {
        guard enabled else { return }
        dialReleaseGen += 1 // 다시 잡고 돌리기 시작하면 이전 놓음-잔향 예약은 세대 불일치로 자동 무효화
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastDialTickAt >= dialTickMinGap else { return }
        lastDialTickAt = now
        restartDial(volumeScale: 1)
    }

    /// 놓았을 때 — 관성으로 점점 잦아드는 "드르륵" 잔향(간격 벌어짐 + 볼륨 감쇠 5회).
    /// Android `MusicManager.dialRelease` 패리티.
    func dialRelease() {
        guard enabled else { return }
        dialReleaseGen += 1
        scheduleDialRelease(index: 0, gen: dialReleaseGen)
    }

    private func scheduleDialRelease(index: Int, gen: Int) {
        guard gen == dialReleaseGen, enabled, index < dialReleaseGaps.count else { return }
        restartDial(volumeScale: 1 - Float(index) * 0.16)
        DispatchQueue.main.asyncAfter(deadline: .now() + dialReleaseGaps[index]) { [weak self] in
            self?.scheduleDialRelease(index: index + 1, gen: gen)
        }
    }

    /// 회전음을 처음부터 다시 재생. player 는 한 번 만들어 두고 되감아 쓴다(생성 지연 방지).
    private func restartDial(volumeScale: Float) {
        if dialPlayer == nil {
            guard let p = makePlayer("turning_dial") else { return }
            let del = DialDelegate { [weak self] in
                self?.dialPlayer?.currentTime = 0 // 다음 dialTick/dialRelease 가 재사용
            }
            dialDelegate = del
            p.delegate = del
            dialPlayer = p
        }
        guard let p = dialPlayer else { return }
        p.volume = dialBaseVolume * sfxVolume * min(max(volumeScale, 0), 1)
        p.currentTime = 0
        p.play()
    }
}

/// AVAudioPlayer 완료 콜백 → 클로저.
private final class DialDelegate: NSObject, AVAudioPlayerDelegate {
    private let onFinish: () -> Void
    init(_ onFinish: @escaping () -> Void) { self.onFinish = onFinish }
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { onFinish() }
}

private extension Float {
    /// 0...1 로 클램프.
    var clampedUnit: Float { Swift.min(Swift.max(self, 0), 1) }
}
