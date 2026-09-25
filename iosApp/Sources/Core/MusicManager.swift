import AVFoundation
import SwiftUI

/// 앱 전역 배경음악 + 효과음 관리자 — Android `core.util.MusicManager` 의 Swift 포팅.
///
/// - 기본 켜짐. 기본 음악 = [MusicCatalog.defaultId].
/// - 트랙 전환은 [playTrack] (이어듣기용 위치 인자), 확정은 [commitSelectedTrack].
/// - 효과음: 맷돌(다이얼) 그라인딩음(돌리는 동안 [dialTick] 이 회전 속도에 비례해 반복,
///   놓으면 [dialRelease] 가 한 번만 끝까지), 다이어리 열람음([playOpenDiary], 배경음악보다 작게).
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

    // 연출용 짧은 효과음(2026-09-25 사용자 선택 — 후보는 tools/sfx 로 합성해 미리듣기 후 확정).
    // Android MusicManager.playSparkTick/playStarBirth/playLike/playDrawer 패리티 — 볼륨·간격 값 동일 유지.
    //  - sfx_spark_1~3 : 별이 하나 둘 떠오를 때(지도 필터 전환 · 별 도감 원) — 3종 중 직전과 다른 것.
    //  - sfx_star_birth: 업로드 성공 별 탄생 · sfx_like: 좋아요 · sfx_drawer: 드로어 열기.
    private var sfxData: [String: Data] = [:]
    /// 재생 중 참조 유지 — 반짝임이 겹쳐 울리므로 여러 개를 들고 있는다(최대 8, Android SoundPool maxStreams 대응).
    private var sfxPlayers: [AVAudioPlayer] = []
    private let sparkBaseVolume: Float = 0.30
    /// 별이 촘촘히 뜰 때 반짝임 폭주 방지 최소 간격(초) — Android SPARK_MIN_GAP_MS(55) 와 같은 값.
    private let sparkMinGap: TimeInterval = 0.055
    private var lastSparkAt: TimeInterval = 0
    private var lastSparkIdx = -1
    private let starBirthBaseVolume: Float = 0.55
    private let likeBaseVolume: Float = 0.50
    private let drawerBaseVolume: Float = 0.32

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

    /// 미리 읽어 둔 짧은 효과음 1회 재생(겹쳐 울릴 수 있음). 음소거면 무음.
    private func playSfx(_ name: String, volume: Float) {
        guard enabled else { return }
        let data: Data
        if let cached = sfxData[name] {
            data = cached
        } else {
            guard let url = Bundle.main.url(forResource: name, withExtension: "mp3"),
                  let loaded = try? Data(contentsOf: url) else { return }
            sfxData[name] = loaded
            data = loaded
        }
        guard let p = try? AVAudioPlayer(data: data) else { return }
        p.volume = volume * sfxVolume
        sfxPlayers.removeAll { !$0.isPlaying }
        if sfxPlayers.count >= 8 { sfxPlayers.removeFirst().stop() }
        sfxPlayers.append(p)
        p.play()
    }

    /// 별 하나가 "톡" 떠오를 때의 반짝임 — 지도 필터 전환 순차 등장 / 별 도감 원 등장에서 별마다 호출.
    /// [sparkMinGap] 보다 촘촘한 호출은 건너뛴다(별이 수십 개여도 소리는 적당한 밀도).
    func playSparkTick() {
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastSparkAt >= sparkMinGap else { return }
        lastSparkAt = now
        var idx = Int.random(in: 0..<3)
        if idx == lastSparkIdx { idx = (idx + 1) % 3 }
        lastSparkIdx = idx
        playSfx("sfx_spark_\(idx + 1)", volume: sparkBaseVolume * Float.random(in: 0.75...1.0))
    }

    /// 업로드 성공 별 탄생 — 유성이 내려와 안착(화음이 발광 순간 ≈0.42s 에 맞춰진 음원).
    func playStarBirth() { playSfx("sfx_star_birth", volume: starBirthBaseVolume) }

    /// 좋아요를 눌렀을 때(취소엔 호출하지 않는다) — 뽀옹 + 반짝.
    func playLike() { playSfx("sfx_like", volume: likeBaseVolume) }

    /// 드로어(메뉴)가 열릴 때 — 스윽. 닫힐 때는 무음.
    func playDrawer() { playSfx("sfx_drawer", volume: drawerBaseVolume) }

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
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastDialTickAt >= dialTickMinGap else { return }
        lastDialTickAt = now
        restartDial(volumeScale: 1)
    }

    /// 놓았을 때 — 음원을 처음부터 **한 번만 끝까지** 울린다(중간에 자르지 않는다).
    /// 예전엔 간격을 벌리며 5회 반복하는 잔향이었는데 너무 길고 어색해서 단발로 바꿨다(2026-09-04).
    /// Android `MusicManager.dialRelease` 패리티.
    func dialRelease() {
        guard enabled else { return }
        restartDial(volumeScale: 1)
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
