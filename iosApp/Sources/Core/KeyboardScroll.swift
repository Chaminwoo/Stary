import SwiftUI

/// 키보드에 입력칸이 가려지지 않게 — 포커스가 잡히면 키보드가 다 올라온 뒤 그 칸을 보이는 영역 가운데로 스크롤한다.
/// (Android 는 스크롤 영역에 `imePadding()` 을 주면 Compose 가 포커스 칸을 따라가지만, SwiftUI ScrollView 는
///  키보드만큼 줄어들기만 하고 포커스 칸까지 스크롤해 주지 않는다.)
///
/// 사용: `ScrollViewReader { proxy in ScrollView { ... 입력칸.id(키) ... }.scrollToFocused(포커스키, proxy: proxy) }`
/// - `target` 은 현재 포커스된 입력칸의 `.id(...)` 값(없으면 nil).
/// - ⚠️ 이 화면 배경을 `ScreenBackground` ZStack 형제로 두면 스크롤 영역이 키보드 아래까지 늘어나 소용없다 — `.background { }` 로.
extension View {
    func scrollToFocused<ID: Hashable>(_ target: ID?, proxy: ScrollViewProxy,
                                       anchor: UnitPoint = .center) -> some View {
        onChange(of: target) { id in
            guard let id else { return }
            // 키보드 등장 애니메이션(~0.25s)이 끝나 스크롤 영역이 줄어든 뒤에 맞춰야 정확하다.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(id, anchor: anchor) }
            }
        }
    }
}
