import SwiftUI
import UIKit

/// 좌우 세이프 에어리어 여백 처리 — **iPhone Duo(폴더블)** 대응(2026-10-28, App Review 스크린샷 반려 후).
///
/// 증상(references/KakaoTalk_20261028_1420*.png): Duo 는 세로로 서 있어도 오른쪽 약 18% 에 카메라 구멍·시스템 상태 위젯이 있어
/// 시스템이 **좌/우 세이프 에어리어**를 준다. 지도는 `ignoresSafeArea` 라 끝까지 그려지지만 상단바 스크림·버튼·플로팅 버튼·목록은
/// 여백 안쪽에만 놓여 "상단바가 오른쪽 끝에 닿지 않고 모든 콘텐츠가 왼쪽으로 쏠려" 보였다.
///
/// 해법: 루트 뷰 컨트롤러의 `additionalSafeAreaInsets` 에 좌우 여백만큼 **음수**를 줘 앱 전체(푸시 화면 포함)의 좌우 여백을 0 으로 만든다.
/// 일반 아이폰은 세로에서 좌우 여백이 0 이라 아무 일도 일어나지 않는다(앱은 세로 고정).
/// 다만 카메라 구멍 위로 버튼이 올라가면 안 되므로, 상단바처럼 **윗줄에 있는 조작 버튼**은 [trailing] 만큼 안쪽에 둔다(RootView.topBar).
@MainActor
final class SideInsets: ObservableObject {
    static let shared = SideInsets()

    /// 시스템이 준 오른쪽 여백(pt) — 0 이면 일반 기기. 윗줄 버튼이 카메라/상태 영역을 피할 때 쓴다.
    @Published private(set) var trailing: CGFloat = 0

    /// 창 하나에 적용. 창 크기·세이프 에어리어가 바뀔 때마다 다시 부른다(접기/펴기 포함).
    func apply(to window: UIWindow?) {
        guard let window, let root = window.rootViewController else { return }
        // window.safeAreaInsets 는 시스템 값(뷰 컨트롤러의 additionalSafeAreaInsets 영향 없음).
        let left = window.safeAreaInsets.left
        let right = window.safeAreaInsets.right
        let wanted = UIEdgeInsets(top: root.additionalSafeAreaInsets.top, left: -left,
                                  bottom: root.additionalSafeAreaInsets.bottom, right: -right)
        if root.additionalSafeAreaInsets != wanted { root.additionalSafeAreaInsets = wanted }
        if trailing != right { trailing = right }
    }
}

/// RootView 배경에 깔아 두는 보이지 않는 UIView — 창에 붙을 때와 세이프 에어리어가 바뀔 때 [SideInsets.apply] 를 부른다.
struct SideInsetsReader: UIViewRepresentable {
    func makeUIView(context: Context) -> Probe { Probe() }
    func updateUIView(_ uiView: Probe, context: Context) {}

    final class Probe: UIView {
        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            backgroundColor = .clear
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            refresh()
        }

        override func safeAreaInsetsDidChange() {
            super.safeAreaInsetsDidChange()
            refresh()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            refresh()
        }

        private func refresh() {
            guard let window else { return }
            // 레이아웃 도중에 부모 뷰 컨트롤러 값을 바꾸면 다시 레이아웃이 돌므로 한 틱 미룬다(값이 같으면 apply 가 아무것도 안 한다).
            // (DispatchQueue 클로저는 메인 액터 격리가 없어 @MainActor 객체를 못 부른다 → 메인 액터 Task)
            Task { @MainActor [weak window] in SideInsets.shared.apply(to: window) }
        }
    }
}
