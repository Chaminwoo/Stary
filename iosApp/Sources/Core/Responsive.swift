import SwiftUI

/// 반응형 UI 규칙 — Android `core/designsystem/Responsive.kt` 패리티.
///
/// ## Android 와 기법이 다른 이유
/// Android 는 `LocalDensity` 를 한 곳에서 스케일해 `dp`/`sp` → px 변환을 통째로 배율에 태울 수 있다.
/// SwiftUI 에는 그에 대응하는 1급 개념이 없다(`scaleEffect` 로 흉내내면 MapLibre 뷰가 가상 해상도로
/// 렌더돼 흐려지고 safe area 계산도 어긋난다). 그래서 iOS 는 **깨짐을 막는 두 축만** 가져온다:
/// 1. 글꼴 배율 상한([maxDynamicType]) — Android `MAX_FONT_SCALE` 대응
/// 2. 콘텐츠 폭 상한([maxContentWidth]) — Android `MAX_CONTENT_WIDTH_DP` 대응
///
/// ⚠️ 남은 격차: 작은 아이폰(SE 320pt)에서의 **비례 축소**는 iOS 에 아직 없다(Android 는 배율 0.76 까지 축소).
/// 필요해지면 화면별로 `GeometryReader` 기반 축소를 넣어야 한다 — PROJECT_NOTES 의 iOS TODO 참고.
enum StaryResponsive {
    /// 콘텐츠(지도 제외) 최대 폭. iPad 에서 카드/행이 화면 폭만큼 늘어나지 않게 끊고 가운데 정렬한다.
    /// 아이폰은 이 값보다 좁아 **영향 없음**(상한이 걸리지 않는다). Android `MAX_CONTENT_WIDTH_DP` 와 동일.
    static let maxContentWidth: CGFloat = 480

    /// 시스템 글꼴 크기(Dynamic Type) 상한 — Android `MAX_FONT_SCALE = 1.15` 에 대응.
    /// 기본(.large) 대비 약 +15% 인 `.xLarge` 까지만 허용해 고정 높이 카드에서 글자가 잘리지 않게 한다.
    static let maxDynamicType: DynamicTypeSize = .xLarge
}

extension View {
    /// 콘텐츠 폭을 [StaryResponsive.maxContentWidth] 로 제한하고 남는 공간에서 가운데 정렬한다.
    /// 지도처럼 화면 전체를 써야 하는 뷰에는 붙이지 않는다.
    func staryContentWidth() -> some View {
        self
            .frame(maxWidth: StaryResponsive.maxContentWidth)
            // 상한 밖 남는 폭은 배경으로 채워 iPad 에서 양옆이 비어 보이지 않게 한다.
            .frame(maxWidth: .infinity)
            .background(Theme.background)
    }
}
