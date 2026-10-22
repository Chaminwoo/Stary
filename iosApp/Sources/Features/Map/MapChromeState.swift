import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// 지도 위 앱 크롬(상단바/글쓰기 FAB) 표시 여부 단일 소스.
/// 글로브(3D 지구)나 집중 모드처럼 지도를 가득 채우는 상태에서는 크롬을 숨긴다.
/// (Android `MapUiState.mapOnly` 대응 — 글로브/집중 진입 시 Scaffold 의 topBar/FAB 를 감춘다.)
@MainActor
final class MapChromeState: ObservableObject {
    static let shared = MapChromeState()
    private init() {}

    /// 이만큼 가만히 있으면 집중 모드(Android `MapUiState.IDLE_MS` 와 같은 값).
    static let idleSeconds: TimeInterval = 10
    /// 집중 모드로 들어갈 때 버튼·상단바가 사라지는 페이드(천천히) / 나올 때 다시 보이는 페이드(빠르게) — Android `CHROME_HIDE_MS/SHOW_MS`.
    static let hideSeconds: Double = 1.2
    static let showSeconds: Double = 0.26

    /// true 면 상단바·FAB 를 숨긴다(글로브 진입).
    @Published var hidden = false

    /// 집중(지도만 보기) — 지도 위 모든 버튼/필터까지 숨긴다. 2026-10-19 부터 버튼이 아니라
    /// **지도에서 [idleSeconds] 동안 아무것도 안 건드리면** 들어가고, 화면을 건드리면 바로 나온다(Android `MapUiState.mapOnly` 대응).
    @Published private(set) var mapOnly = false

    /// 3D 글로브가 열려 있음 — **상단바는 그대로**(Android 도 반투명 탑바가 글로브 위에 남는다), 글쓰기 FAB 만 숨긴다
    /// (Android 의 업로드 버튼은 지도 안에 있어 글로브 오버레이에 가려진다).
    @Published var globeOpen = false

    /// 오늘의 퀘스트 창 — 지도 "!" 버튼이 켜고, RootView 가 상단바·버튼까지 덮도록 맨 위에 띄운다.
    @Published var questsOpen = false

    /// 첫 실행 코치마크가 떠 있음(RootView 가 맞춘다) — 그동안 첫 별 안내 카드를 숨긴다.
    @Published var coachMarkShowing = false
    /// 첫 사용 안내 단계(지도가 띄울 수 있을 때만) — MapScreen 이 정하고 RootView 가 "!" 스포트라이트·퀘스트 창 강조에 쓴다.
    @Published var guideStep: GuideStep?
    /// 이번 실행에서 닫은(X/나중에) 안내 단계.
    @Published var guideHidden: Set<GuideStep> = []

    /// 지도(루트)가 화면에 보이는지 — MapScreen onAppear/onDisappear(상세·겹친 별 push 로 가려지면 false).
    @Published var mapVisible = true

    /// 지도 위에 떠 있는 창(퀘스트 창·코치마크) 수 — 0 보다 크면 집중 모드에 들어가지 않는다.
    @Published private(set) var idleHolds = 0

    /// 마지막으로 화면을 건드린 시각.
    private(set) var lastTouchAt = Date()

    /// 상단바/FAB 를 숨겨야 하는 상태(글로브 또는 집중).
    var chromeHidden: Bool { hidden || mapOnly }

    func enterMapOnly() {
        guard !mapOnly else { return }
        withAnimation(.easeInOut(duration: Self.hideSeconds)) { mapOnly = true }
    }

    func exitMapOnly() {
        guard mapOnly else { return }
        withAnimation(.easeOut(duration: Self.showSeconds)) { mapOnly = false }
    }

    /// 화면 터치(TouchActivityMonitor) — 집중 모드면 나오고, 대기 시간을 처음부터.
    func noteTouch() {
        lastTouchAt = Date()
        exitMapOnly()
    }

    /// 터치는 아니지만 대기 시간을 처음부터(지도 복귀·앱 복귀·창 닫힘).
    func resetIdle() { lastTouchAt = Date() }

    func holdIdle() { idleHolds += 1 }
    func releaseIdle() {
        idleHolds = max(0, idleHolds - 1)
        resetIdle()
    }
}

/// 창(UIWindow) 전체의 터치를 **가로채지 않고** 지켜보는 제스처 — 집중 모드 해제·대기 시간 초기화용.
/// SwiftUI 제스처는 MapLibre(UIView) 위 터치를 못 보므로 창에 UIKit 인식기를 단다.
/// 손가락이 닿자마자 실패 처리해 다른 제스처·버튼·지도 조작에 영향이 없다(cancelsTouchesInView = false).
final class TouchActivityRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        Task { @MainActor in MapChromeState.shared.noteTouch() }
        state = .failed
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    /// 앱의 모든 창(키 윈도우)에 한 번만 단다. RootView onAppear 에서 부른다.
    @MainActor static func installIfNeeded() {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        for w in windows where !(w.gestureRecognizers ?? []).contains(where: { $0 is TouchActivityRecognizer }) {
            w.addGestureRecognizer(TouchActivityRecognizer(target: nil, action: nil))
        }
    }
}
