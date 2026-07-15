import Shared   // KMP 공유 프레임워크(baseName "Shared")
import SwiftUI

/// 정보 화면 — 공유 KMP 모듈이 정상 링크되는지 확인하는 디버그 표시를 겸한다.
/// `PlatformKt.describePlatform()` 는 commonMain 의 top-level 함수(Platform.kt)다.
struct AboutView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("Stary for iOS")
                .font(.poorStory(17))
                .foregroundStyle(Theme.textPrimary)
            Text(PlatformKt.describePlatform())
                .font(.poorStory(12))
                .foregroundStyle(Theme.textSecondary)
            Text("공유 모듈 연동 OK · v1.0.0")
                .font(.poorStory(11))
                .foregroundStyle(Theme.textFaint)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
    }
}
