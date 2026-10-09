import MapLibre
import SwiftUI
import UIKit

/// 지도 줌 버튼(+/−) **꾹 누르기 → 손을 뗄 때까지 연속 줌**. Android `DiaryMap.ZoomHoldButton` 패리티 —
/// 값도 같다(320ms 뒤 시작 · 1.1 → 2.4 줌레벨/초로 1.2초에 걸쳐 가속).
/// 탭(짧게 누름)은 예전처럼 `MapLibreView.zoomRequest` 로 한 단계 부드럽게 줌한다.
///
/// 구조: 버튼(`ZoomHoldGesture`)이 꾹 누름을 감지 → [start] 가 CADisplayLink 로 프레임마다 `setZoomLevel` →
/// 손을 떼면 [stop] 이 [onFinish] 를 불러 지도 델리게이트가 미뤄 둔 마무리 일을 한 번 한다(`Coordinator.settleCamera`).
///
/// ⚠️ 메인 스레드 전용(CADisplayLink + MLNMapView). actor 격리는 일부러 두지 않는다 —
///    View 의 `@State` 초기값(비격리 컨텍스트)에서도 만들 수 있어야 한다.
final class MapZoomHold {
    /// 이보다 짧게 떼면 "탭"(한 단계 줌) — Android ZOOM_HOLD_START_MS.
    static let startDelay: TimeInterval = 0.32
    /// 연속 줌 속도(줌 레벨/초). 누른 시간이 길수록 [rampSeconds] 동안 최대치까지 서서히 빨라진다.
    static let minSpeed = 1.1
    static let maxSpeed = 2.4
    static let rampSeconds = 1.2

    /// 연속 줌 중인가 — 지도 델리게이트(`regionDidChange`)가 프레임마다 별자리 재구성·카메라 저장 같은
    /// 무거운 일을 건너뛰는 데 쓴다(손을 떼면 [onFinish] 로 한 번에 처리).
    fileprivate(set) static var isHolding = false

    /// MapLibreView.updateUIView 가 매번 연결해 둔다.
    weak var mapView: MLNMapView?
    /// 연속 줌이 끝났을 때(=손을 뗐을 때) 1회 — 미뤄 둔 카메라 마무리.
    var onFinish: (() -> Void)?

    private var link: CADisplayLink?
    private var direction = 0.0
    private var startTime: CFTimeInterval = 0
    private var lastTime: CFTimeInterval = 0

    /// [direction] +1 = 확대, −1 = 축소.
    func start(direction: Double) {
        stop()
        guard mapView != nil else { return }
        self.direction = direction
        startTime = CACurrentMediaTime()
        lastTime = startTime
        // CADisplayLink 는 target 을 강하게 쥔다 → 약한 참조 프록시를 거쳐 순환 참조를 피한다.
        let l = CADisplayLink(target: LinkProxy(owner: self), selector: #selector(LinkProxy.tick(_:)))
        l.add(to: .main, forMode: .common)
        link = l
        Self.isHolding = true
    }

    /// 여러 번 불러도 안전(손을 뗄 때·제스처 취소·뷰 사라짐 모두 여기로).
    func stop() {
        guard let l = link else { return }
        l.invalidate()
        link = nil
        Self.isHolding = false
        onFinish?()
    }

    fileprivate func tick(_ l: CADisplayLink) {
        guard let mapView else { stop(); return }
        let now = l.timestamp
        // 프레임이 튀어도(렉) 한 번에 크게 점프하지 않게 dt 상한.
        let dt = min(max(now - lastTime, 0), 0.05)
        lastTime = now
        let t = min(max((now - startTime) / Self.rampSeconds, 0), 1)
        let speed = Self.minSpeed + (Self.maxSpeed - Self.minSpeed) * t
        let current = mapView.zoomLevel
        let target = min(max(current + direction * speed * dt, mapView.minimumZoomLevel), mapView.maximumZoomLevel)
        if target != current { mapView.setZoomLevel(target, animated: false) }
    }
}

/// CADisplayLink → MapZoomHold 약한 참조 다리.
private final class LinkProxy: NSObject {
    weak var owner: MapZoomHold?

    init(owner: MapZoomHold) { self.owner = owner }

    @objc func tick(_ l: CADisplayLink) {
        guard let owner else {
            // 주인이 먼저 사라졌다 — 링크만 정리하고 플래그가 남지 않게 한다.
            l.invalidate()
            MapZoomHold.isHolding = false
            return
        }
        owner.tick(l)
    }
}

/// 줌 버튼 제스처 — **탭 = [onTap](한 단계)**, **꾹 = [MapZoomHold] 연속 줌**.
/// Button 대신 `DragGesture(minimumDistance: 0)` 를 쓰는 이유: 누른 "순간"과 뗀 "순간"을 모두 알아야 연속 줌을 시작/종료할 수 있다.
struct ZoomHoldGesture: ViewModifier {
    let direction: Double
    let size: CGFloat
    let hold: MapZoomHold
    let onTap: () -> Void

    /// 손가락이 닿아 있는 동안만 true — 정상 종료·시스템 취소 모두에서 자동으로 false 로 돌아온다(취소 때 onEnded 가 안 불려도 안전).
    @GestureState private var pressing = false
    @State private var holdTask: Task<Void, Never>?
    @State private var didHold = false
    @State private var began = false

    func body(content: Content) -> some View {
        content
            // 눌림 피드백(기존 Button 의 눌림 흐려짐과 같은 톤).
            .opacity(pressing ? 0.65 : 1)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($pressing) { _, state, _ in state = true }
                    .onChanged { _ in begin() }
                    .onEnded { value in end(at: value.location) }
            )
            .onChange(of: pressing) { down in
                if !down { finish() }
            }
            .onDisappear { finish() }
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onTap() }
    }

    private func begin() {
        guard !began else { return }
        began = true
        didHold = false
        holdTask?.cancel()
        holdTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(MapZoomHold.startDelay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            didHold = true
            hold.start(direction: direction)
        }
    }

    private func end(at point: CGPoint) {
        // 버튼 밖으로 한참 끌어낸 뒤 떼면 탭이 아니다(Button 과 같은 감각). 꾹 눌러 연속 줌을 했으면 뗄 때 한 단계가 더 얹히지 않게 한다.
        let slop: CGFloat = 24
        let area = CGRect(x: -slop, y: -slop, width: size + slop * 2, height: size + slop * 2)
        if !didHold && area.contains(point) { onTap() }
        finish()
    }

    private func finish() {
        holdTask?.cancel()
        holdTask = nil
        hold.stop()
        began = false
    }
}

extension View {
    /// 줌 버튼용 — 탭 = 한 단계, 꾹 = 연속 줌.
    func zoomHoldGesture(direction: Double, size: CGFloat, hold: MapZoomHold, onTap: @escaping () -> Void) -> some View {
        modifier(ZoomHoldGesture(direction: direction, size: size, hold: hold, onTap: onTap))
    }
}
