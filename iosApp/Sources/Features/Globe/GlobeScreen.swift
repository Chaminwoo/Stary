import MetalKit
import SwiftUI
import UIKit

/// 3D 행성(지구) 화면 — 지도 하단 "우주에서 보기" 버튼으로 진입하는 전체화면 오버레이.
/// Android `feature/globe/GlobeScreen.kt` 패리티(렌더링은 [GlobeRenderer] — Android GL 렌더러의 Metal 포팅).
///
/// - 드래그: 행성 회전(관성), 3초 무입력 시 느린 자동 회전.
/// - 핀치: 카메라 줌(최소/최대 클램프) — 화면 전환 없음.
/// - 화면 아래쪽(55% 이하) 탭: 닫기(X) 버튼 표시(4초 후 자동 숨김) → 누르면 지금 정면 지점의 지도로 복귀.
///
/// 성능: MTKView/렌더러/텍스처는 이 뷰가 나타날 때만 생성, 사라지면 렌더 루프 정지(지도 평상시 비용 0).
struct GlobeScreen: View {
    @ObservedObject private var locale = LocaleManager.shared
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let onRequestExit: (_ lat: Double, _ lng: Double) -> Void

    @State private var hintVisible = true
    @State private var closeVisible = false
    @State private var closeToken = 0
    @StateObject private var bridge = GlobeBridge()

    var body: some View {
        ZStack(alignment: .bottom) {
            GlobeMetalView(
                diaries: diaries, startLat: startLat, startLng: startLng,
                bridge: bridge,
                onBottomTap: { showClose() }
            )
            .ignoresSafeArea()
            .background(Color.black.ignoresSafeArea())

            // 조작 힌트 — 잠깐 보였다 사라짐(Android globe_hint: 13sp, 흰 0.75, 검정 0.35 캡슐, 닫기 버튼 자리 위).
            Text(locale.t(.globeHint))
                .font(.minSans(13))
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(Color.black.opacity(0.35), in: Capsule())
                .padding(.bottom, 96)
                .opacity(hintVisible ? 1 : 0)
                .animation(.easeInOut(duration: 0.7), value: hintVisible)
                .allowsHitTesting(false)

            // 닫기(X) — 아래쪽 탭으로 표시, 누르면 지금 정면 지점의 지도로 복귀(Android 52dp, 흰 0.14 원).
            if closeVisible {
                Button { fireExit() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 52, height: 52)
                        .background(Color.white.opacity(0.14), in: Circle())
                }
                .accessibilityLabel(locale.t(.globeClose))
                .padding(.bottom, 24)
                .transition(.opacity)
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 4_200_000_000)
            hintVisible = false
        }
    }

    private func showClose() {
        withAnimation(.easeInOut(duration: 0.25)) { closeVisible = true }
        closeToken += 1
        let token = closeToken
        Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if token == closeToken {
                withAnimation(.easeInOut(duration: 0.25)) { closeVisible = false }
            }
        }
    }

    /// 한 번만 — 지금 정면 좌표로 지도 복귀 요청.
    private func fireExit() {
        guard !bridge.exitFired else { return }
        bridge.exitFired = true
        let facing = bridge.renderer?.facingLatLng() ?? (startLat, startLng)
        onRequestExit(facing.0, facing.1)
    }
}

/// SwiftUI(X 버튼) ↔ 렌더러(정면 좌표)를 잇는 참조 보관소.
final class GlobeBridge: ObservableObject {
    var renderer: GlobeRenderer?
    var exitFired = false
}

/// MTKView 래퍼 — 제스처는 Android detectTransformGestures(드래그+핀치 동시)와 같은 규칙으로 렌더러 상태를 바꾼다.
private struct GlobeMetalView: UIViewRepresentable {
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let bridge: GlobeBridge
    let onBottomTap: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onBottomTap: onBottomTap) }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        // GL 기본 프레임버퍼와 같은 감마 공간(sRGB 변환 없음) + 깊이 버퍼(Android DepthFirstConfigChooser 24비트 우선 → 32F).
        view.colorPixelFormat = .bgra8Unorm
        view.depthStencilPixelFormat = .depth32Float
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        view.clearDepth = 1
        view.preferredFramesPerSecond = 60
        view.backgroundColor = .black
        view.isOpaque = true
        view.enableSetNeedsDisplay = false
        view.isPaused = false

        if let renderer = GlobeRenderer(view: view) {
            renderer.setInitialFacing(lat: startLat, lng: startLng)
            renderer.setDiaries(diaries)
            view.delegate = renderer          // MTKView.delegate 는 weak — Coordinator 가 소유한다.
            context.coordinator.renderer = renderer
            bridge.renderer = renderer
        }
        context.coordinator.lastDiaryKey = Self.diaryKey(diaries)
        context.coordinator.observeAppState(view)

        let c = context.coordinator
        let pan = UIPanGestureRecognizer(target: c, action: #selector(Coordinator.onPan(_:)))
        let pinch = UIPinchGestureRecognizer(target: c, action: #selector(Coordinator.onPinch(_:)))
        let tap = UITapGestureRecognizer(target: c, action: #selector(Coordinator.onTap(_:)))
        pan.delegate = c
        pinch.delegate = c
        view.addGestureRecognizer(pan)
        view.addGestureRecognizer(pinch)
        view.addGestureRecognizer(tap)
        return view
    }

    func updateUIView(_ uiView: MTKView, context: Context) {
        // 다이어리 목록이 실제로 바뀐 경우만 스프라이트 재빌드(Android LaunchedEffect(diaries)).
        let key = Self.diaryKey(diaries)
        if key != context.coordinator.lastDiaryKey {
            context.coordinator.lastDiaryKey = key
            context.coordinator.renderer?.setDiaries(diaries)
        }
    }

    static func dismantleUIView(_ uiView: MTKView, coordinator: Coordinator) {
        uiView.isPaused = true
        uiView.delegate = nil
        coordinator.stopObserving()
    }

    /// 글로브 표시에 영향을 주는 값(id/좌표/좋아요)만으로 만든 목록 키.
    static func diaryKey(_ diaries: [Diary]) -> Int {
        var h = Hasher()
        h.combine(diaries.count)
        for d in diaries {
            h.combine(d.id)
            h.combine(d.likeCount)
            h.combine(d.latitude)
            h.combine(d.longitude)
        }
        return h.finalize()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var renderer: GlobeRenderer?
        var lastDiaryKey = 0
        let onBottomTap: () -> Void
        private var lastPanTime: CFTimeInterval = 0
        private var lastPinchScale: CGFloat = 1
        private weak var metalView: MTKView?

        init(onBottomTap: @escaping () -> Void) {
            self.onBottomTap = onBottomTap
        }

        /// 백그라운드에선 GPU 접근이 막히므로 렌더 루프를 멈춘다(Android GLSurfaceView onPause/onResume 대응).
        /// (셀렉터 방식 — 클로저 옵저버의 Sendable 캡처 검사를 피한다)
        func observeAppState(_ view: MTKView) {
            metalView = view
            let center = NotificationCenter.default
            center.addObserver(self, selector: #selector(onBackground),
                               name: UIApplication.didEnterBackgroundNotification, object: nil)
            center.addObserver(self, selector: #selector(onForeground),
                               name: UIApplication.willEnterForegroundNotification, object: nil)
        }

        @objc private func onBackground() { metalView?.isPaused = true }
        @objc private func onForeground() { metalView?.isPaused = false }

        func stopObserving() {
            NotificationCenter.default.removeObserver(self)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }

        /// 드래그 회전 — Android: degPerPx = 0.075 × ((camDist−1)/2.2), 속도 = 이번 이동/경과시간 × 0.55.
        /// iOS 는 포인트 단위라 화면 배율(px/pt)을 곱해 같은 손가락 거리에 같은 회전량이 되게 한다.
        @objc func onPan(_ g: UIPanGestureRecognizer) {
            guard let v = g.view, let r = renderer else { return }
            let now = CACurrentMediaTime()
            switch g.state {
            case .began:
                lastPanTime = now
                r.lastInteraction = now
            case .changed:
                let tr = g.translation(in: v)
                g.setTranslation(.zero, in: v)
                let dt = Float(min(max(now - lastPanTime, 0.004), 0.1))
                lastPanTime = now
                r.lastInteraction = now
                let pxPerPt = Float(v.contentScaleFactor)
                let degPerPx: Float = 0.075 * ((r.camDist - 1) / 2.2)
                let dYaw = Float(tr.x) * pxPerPt * degPerPx
                let dPitch = Float(tr.y) * pxPerPt * degPerPx
                r.yawDeg += dYaw
                r.pitchDeg = min(max(r.pitchDeg + dPitch, -75), 75)
                r.yawVelDeg = dYaw / dt * 0.55
                r.pitchVelDeg = dPitch / dt * 0.55
            default:
                break
            }
        }

        /// 핀치 = 카메라 줌만(화면 전환 없음) — camDist / zoom 을 [minDist, maxDist] 로.
        @objc func onPinch(_ g: UIPinchGestureRecognizer) {
            guard let r = renderer else { return }
            r.lastInteraction = CACurrentMediaTime()
            switch g.state {
            case .began:
                lastPinchScale = g.scale
            case .changed:
                let zoom = Float(g.scale / max(lastPinchScale, 0.0001))
                lastPinchScale = g.scale
                r.camDist = min(max(r.camDist / max(zoom, 0.0001), GlobeRenderer.minDist), GlobeRenderer.maxDist)
            default:
                break
            }
        }

        /// 화면 아래쪽(55% 이하) 탭 → 닫기(X) 버튼 표시.
        @objc func onTap(_ g: UITapGestureRecognizer) {
            guard let v = g.view else { return }
            if g.location(in: v).y >= v.bounds.height * 0.55 { onBottomTap() }
        }
    }
}
