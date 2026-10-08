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

    /// 현재 앱 언어의 라벨(전체공개/친구만/나만보기).
    var localizedLabel: String {
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

/// 수정 팝업의 공개 범위 선택 줄 — 업로드 화면 칩(Android `VisibilityChooser`)과 같은 모양:
/// 선택 = 남색 테두리 + 옅은 면, 미선택 = 회색 테두리.
struct VisibilityChooser: View {
    @Binding var selection: Visibility

    var body: some View {
        HStack(spacing: 8) {
            ForEach(Visibility.allCases, id: \.self) { v in
                let on = selection == v
                Button {
                    selection = v
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: v.symbolName)
                            .font(.system(size: 16))
                        Text(v.localizedLabel)
                            .font(.minSans(12))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(on ? Theme.navyAccent : Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(on ? Color.white.opacity(0.15) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10)
                        .strokeBorder(on ? Theme.navyAccent : Theme.outline, lineWidth: on ? 2 : 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
