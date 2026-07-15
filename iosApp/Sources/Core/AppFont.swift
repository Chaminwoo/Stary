import SwiftUI

/// 앱 커스텀 폰트. (Android `Type.kt` 대응)
/// - PoetsenOne : 영문 디스플레이/타이틀(대형 헤더)
/// - PoorStory  : 한글 본문·UI(한글+영문 혼용)
enum AppFont {
    static let display = "PoetsenOne-Regular"   // 영문 타이틀
    static let body = "PoorStory-Regular"        // 한글 본문/UI
}

extension Font {
    /// 영문 디스플레이/타이틀용(PoetsenOne).
    static func poetsenOne(_ size: CGFloat) -> Font { .custom(AppFont.display, size: size) }
    /// 한글 본문·UI용(PoorStory). 앱 기본 폰트.
    static func poorStory(_ size: CGFloat) -> Font { .custom(AppFont.body, size: size) }
}
