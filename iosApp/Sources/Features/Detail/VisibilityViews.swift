import SwiftUI

/// 공개 범위 표시/선택 부품 — Android `feature/diary/screen/DiaryVisibility.kt` 패리티.
/// 지구본(전체공개) / 친구 / 자물쇠(나만보기). 문구는 업로드 화면과 같은 `uploadVis*` 키를 쓴다.
extension Visibility {

    /// 서버 문자열(`visibilityType`)에서 복원 — 알 수 없는 값(옛 문서 등)은 전체공개(앱 전체의 기본값과 동일).
    init(type: String) {
        self = Visibility(rawValue: type) ?? .publicAll
    }

    /// SF Symbols — Android Icons.Filled.Public / People / Lock 대응.
    var symbolName: String {
        switch self {
        case .publicAll: return "globe"
        case .friends: return "person.2.fill"
        case .privateOnly: return "lock.fill"
        }
    }

    /// 현재 앱 언어의 라벨(전체공개/친구만/나만보기). ⚠️ `LocaleManager` 가 메인 액터 전용이라 이 속성도 메인 액터 —
    /// 사용처가 전부 SwiftUI 뷰 본문이라 그대로 호출된다(CI 컴파일 오류 "main actor-isolated … in a synchronous nonisolated context" 수정).
    @MainActor var localizedLabel: String {
        switch self {
        case .publicAll: return LocaleManager.shared.t(.uploadVisPublic)
        case .friends: return LocaleManager.shared.t(.uploadVisFriends)
        case .privateOnly: return LocaleManager.shared.t(.uploadVisPrivate)
        }
    }
}

/// 상세 화면 날짜 옆의 공개 범위 아이콘.
struct VisibilityBadge: View {
    let visibility: Visibility
    var size: CGFloat = 12
    var color: Color = Theme.textSecondary

    var body: some View {
        Image(systemName: visibility.symbolName)
            .font(.system(size: size))
            .foregroundStyle(color)
            .accessibilityLabel(visibility.localizedLabel)
    }
}

/// 공개 범위 선택 줄 — 업로드 화면과 수정 팝업이 함께 쓴다. 테두리 없이 면으로만 나누고 고른 칸에 옅은 별빛 판
/// (`StarySegmented`). Android `VisibilityChooser` 패리티.
struct VisibilityChooser: View {
    @Binding var selection: Visibility
    var enabled = true

    var body: some View {
        StarySegmented(
            options: Visibility.allCases.map { v -> (label: String, systemImage: String?) in (v.localizedLabel, v.symbolName) },
            selection: Binding(
                get: { Visibility.allCases.firstIndex(of: selection) ?? 0 },
                set: { i in if Visibility.allCases.indices.contains(i) { selection = Visibility.allCases[i] } }
            ),
            enabled: enabled
        )
    }
}
