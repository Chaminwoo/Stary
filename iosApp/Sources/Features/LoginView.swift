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
                ProgressView().tint(Theme.mint)
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
                StarDiaryButton(text: "Google 계정으로 로그인") {
                    Task { await auth.signInWithGoogle() }
                }

                Button {
                    Task { await auth.signInAnonymously() }
                } label: {
                    Text("로그인 없이 둘러보기")
                        .font(.poorStory(14))
                        .foregroundStyle(Theme.mint)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }

                if let error = auth.errorMessage {
                    Text(error)
                        .font(.poorStory(12))
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
                    .font(.poorStory(16))
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

/// 무음 인트로 영상을 1회 재생하고 끝나면 콜백. (Android ExoPlayer 대응)
/// 재생 속도 곡선(빠르게 시작 → 종반 감속)까지 근사한다.
private struct IntroVideoView: UIViewRepresentable {
    let resource: String
    let ext: String
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
            let progress = Float(CMTimeGetSeconds(player.currentTime()) / total)
            let rate: Float
            if progress <= 0.5 {
                rate = 2.5 - (progress / 0.5) * 0.7          // 2.5x → 1.8x
            } else if progress >= 0.75 {
                rate = max(1.8 - ((progress - 0.75) / 0.25) * 1.3, 0.25) // 1.8x → 0.25x
            } else {
                rate = 1.8
            }
            if player.rate > 0 { player.rate = rate }
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
