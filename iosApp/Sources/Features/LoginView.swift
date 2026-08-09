import AVFoundation
import SwiftUI

/// 번들에 담긴 로고 이미지(에셋 카탈로그가 아닌 loose png 라 UIImage 로 로드).
private let appLogo: UIImage = UIImage(named: "logo") ?? UIImage()

/// 첫 진입 — 인트로 영상 후 구글 로그인 / 둘러보기.
/// Android `LoginScreen` 대응: 무음 인트로 영상 1회 재생 → 페이드인 → 빛나는 후광 로고 + 하단 버튼.
struct LoginView: View {
    @EnvironmentObject var auth: AuthManager

    /// 인트로 영상은 앱 실행 후 최초 1회만 재생(로그아웃 재진입 시 즉시 UI — Android `immediate` 대응).
    private static var didPlayIntro = false

    @State private var showUI: Bool
    @State private var haloWidth: CGFloat = 100
    private let playsIntro: Bool

    init() {
        let immediate = LoginView.didPlayIntro
        self.playsIntro = !immediate
        _showUI = State(initialValue: immediate)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 인트로 영상(무음). 재생이 끝나면 UI 등장.
            if playsIntro {
                IntroVideoView(resource: "login_video", ext: "mp4") {
                    revealUI()
                }
                .ignoresSafeArea()
                .scaleEffect(1.12)
            }

            if showUI {
                content
                    .transition(.opacity)
            }

            if auth.isBusy {
                StarLoadingView(size: 40)   // 앱 공용 크리스탈 별 로딩(34-9)
            }
        }
        .onAppear {
            if !playsIntro { animateHalo() }
        }
    }

    // MARK: - 로그인 UI(로고 + 버튼)

    private var content: some View {
        ZStack {
            // 중앙 후광 로고 — 위로 살짝 올림(Android offset y = -80).
            ZStack {
                // 뒤에 깔리는 빛나는 후광: 로고 복제 + 블러 + 밝게 + 폭 애니메이션.
                Image(uiImage: appLogo)
                    .resizable()
                    .scaledToFit()
                    .frame(width: haloWidth)
                    .brightness(0.35)
                    .blur(radius: 12)
                    .opacity(0.9)
                // 선명한 로고(앞).
                Image(uiImage: appLogo)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 220)
            }
            .frame(maxHeight: .infinity, alignment: .center)
            .offset(y: -80)

            // 하단 버튼 영역.
            VStack(spacing: 8) {
                StarDiaryButton(text: LocaleManager.shared.t(.loginGoogle)) {
                    Task { await auth.signInWithGoogle() }
                }

                Button {
                    Task { await auth.signInAnonymously() }
                } label: {
                    Text(LocaleManager.shared.t(.loginBrowse))
                        .font(.minSans(14))
                        .foregroundStyle(Theme.mint)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }

                if let error = auth.errorMessage {
                    Text(error)
                        .font(.minSans(12))
                        .foregroundStyle(.red.opacity(0.9))
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .disabled(auth.isBusy)
    }

    // MARK: - 연출

    private func revealUI() {
        LoginView.didPlayIntro = true
        withAnimation(.easeIn(duration: 0.8)) { showUI = true }
        animateHalo()
    }

    /// 후광이 살짝 부풀었다 가라앉으며 빛이 번지는 연출(스프링 오버슈트로 근사).
    private func animateHalo() {
        withAnimation(.spring(response: 0.9, dampingFraction: 0.55)) {
            haloWidth = 230
        }
    }
}

// MARK: - 크림색 별 버튼 (Android StarDiaryButton 대응)

/// 밝게 빛나는 크림색 그라데이션 캡슐 버튼 + 별 아이콘.
struct StarDiaryButton: View {
    var text: String = "별 다이어리 남기기"
    var onClick: () -> Void = {}

    private let creamTop = Color(hex: 0xF7EDD8)
    private let creamBottom = Color(hex: 0xE9D6AE)
    private let charcoal = Color(hex: 0x2C2723)
    private let glow = Color(hex: 0xF3E4C0)

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                Image(systemName: "star.fill")
                    .font(.system(size: 20))
                Text(text)
                    .font(.minSans(16))
            }
            .foregroundStyle(charcoal)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 30)
            .padding(.vertical, 16)
            .background(
                LinearGradient(
                    colors: [creamTop, creamBottom],
                    startPoint: .top,
                    endPoint: .bottom
                ),
                in: Capsule()
            )
            // 뒤로 번지는 은은한 후광.
            .background(
                Capsule()
                    .fill(glow.opacity(0.55))
                    .blur(radius: 28)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 6)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 인트로 영상 뷰

/// 무음 인트로 영상을 1회 재생하고, **끝나기 [Coordinator.earlyRevealSeconds] 초 전에** 콜백. (Android ExoPlayer 대응)
/// 재생 속도 곡선(빠르게 시작 → 종반 감속)까지 근사한다.
private struct IntroVideoView: UIViewRepresentable {
    let resource: String
    let ext: String
    /// 로그인 UI(로고+버튼) 등장 신호. 영상은 이 뒤에도 계속 재생돼 자연스럽게 마무리된다.
    let onEnded: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onEnded: onEnded) }

    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        guard let url = Bundle.main.url(forResource: resource, withExtension: ext) else {
            // 영상이 없으면(예: CI 시뮬레이터) 즉시 종료 콜백.
            DispatchQueue.main.async { self.onEnded() }
            return view
        }
        let player = AVPlayer(url: url)
        player.isMuted = true
        player.actionAtItemEnd = .pause
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspectFill
        context.coordinator.attach(player: player)
        player.playImmediately(atRate: 2.5)
        return view
    }

    func updateUIView(_ uiView: PlayerContainerView, context: Context) {}

    static func dismantleUIView(_ uiView: PlayerContainerView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class Coordinator: NSObject {
        /// 영상 종료보다 이만큼 **먼저** 로그인 UI 를 띄운다(초, 실제 시간).
        /// 값을 키우면 로고/버튼이 그만큼 더 빨리 뜬다.
        ///
        /// 현재 인트로: 미디어 7.83초를 속도 곡선(2.5x→1.8x→0.5x)으로 재생 = **실제 약 4.85초**.
        /// 1.0 이면 약 3.85초에 노출(+페이드 0.8초).
        /// ⚠️ Android 는 영상과 무관하게 **1.5초 고정 후 노출**이라 아직 iOS 가 더 늦다 —
        ///    완전한 패리티를 원하면 이 값 대신 "재생 시작 후 1.5초 타이머" 방식으로 바꿀 것.
        static let earlyRevealSeconds: Double = 1.0

        private let onEnded: () -> Void
        private weak var player: AVPlayer?
        private var timeObserver: Any?
        private var didEnd = false

        init(onEnded: @escaping () -> Void) { self.onEnded = onEnded }

        func attach(player: AVPlayer) {
            self.player = player
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(playbackEnded),
                name: .AVPlayerItemDidPlayToEndTime,
                object: player.currentItem
            )
            // 진행도에 따라 재생 속도를 조절(Android 속도 곡선 근사).
            let interval = CMTime(seconds: 0.05, preferredTimescale: 600)
            timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] _ in
                self?.adjustRate()
            }
        }

        private func adjustRate() {
            guard let player, let item = player.currentItem else { return }
            let total = CMTimeGetSeconds(item.duration)
            guard total.isFinite, total > 0 else { return }
            let progress = CMTimeGetSeconds(player.currentTime()) / total
            if player.rate > 0 { player.rate = Self.rate(atProgress: progress) }

            // 영상이 끝나기 전에 미리 로그인 UI 를 띄운다(체감 대기 단축 — 사용자 요청).
            // 종반이 0.5배속까지 감속하므로 "남은 재생 시간"은 실제 시간 기준으로 계산해야 한다.
            if !didEnd, Self.remainingWallSeconds(fromProgress: progress, total: total) <= Self.earlyRevealSeconds {
                didEnd = true
                onEnded()   // 영상은 계속 재생 — UI 가 그 위로 페이드인된다(Android 도 재생 중 노출).
            }
        }

        /// 진행도별 재생 속도(Android LoginScreen 의 속도 곡선과 동일 값).
        /// ⚠️ [remainingWallSeconds] 가 이 곡선을 적분하므로 값을 바꾸면 둘 다 함께 반영된다.
        private static func rate(atProgress p: Double) -> Float {
            if p <= 0.5 {
                return Float(2.5 - (p / 0.5) * 0.7)                       // 2.5x → 1.8x
            } else if p >= 0.75 {
                return Float(max(1.8 - ((p - 0.75) / 0.25) * 1.3, 0.25))  // 1.8x → 0.25x(하한)
            }
            return 1.8
        }

        /// 지금 진행도에서 영상이 끝날 때까지 남은 **실제(벽시계) 시간** — 속도 곡선을 수치 적분.
        /// (미디어 시간 ÷ 배속. 종반 감속 때문에 단순히 "남은 미디어 시간"을 쓰면 크게 어긋난다.)
        private static func remainingWallSeconds(fromProgress p0: Double, total: Double) -> Double {
            guard p0 < 1 else { return 0 }
            let steps = 60
            let dp = (1 - p0) / Double(steps)
            var seconds = 0.0
            for i in 0..<steps {
                let mid = p0 + dp * (Double(i) + 0.5)
                seconds += (dp * total) / Double(rate(atProgress: mid))
            }
            return seconds
        }

        @objc private func playbackEnded() {
            guard !didEnd else { return }
            didEnd = true
            onEnded()
        }

        func detach() {
            if let timeObserver { player?.removeTimeObserver(timeObserver) }
            timeObserver = nil
            NotificationCenter.default.removeObserver(self)
        }
    }
}

/// AVPlayerLayer 를 뒷면 레이어로 쓰는 컨테이너.
private final class PlayerContainerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
