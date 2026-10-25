import SwiftUI

/// 버튼 종류 — 주(남색 그라데이션 + 별빛 테두리) / 보조(테두리만) / 위험(지우기·차단). Android `StaryButtonKind` 패리티.
enum StaryButtonKind { case primary, ghost, danger }

/// 앱 공용 버튼(Android `StaryButton` 패리티). 기본은 화면 아래 큰 버튼(높이 50),
/// `compact` 는 확인 창·목록 행 안의 작은 버튼(높이 40). `fullWidth` 면 가로를 꽉 채운다.
/// `loading` 이면 글자 대신 도는 별을 보여 주고 눌리지 않는다(흐려지지는 않는다 — 진행 중이지 막힌 게 아니라서).
struct StaryButton: View {
    let title: String
    var kind: StaryButtonKind = .primary
    var compact = false
    var fullWidth = false
    var systemImage: String?
    var enabled = true
    var loading = false
    let action: () -> Void

    init(_ title: String, kind: StaryButtonKind = .primary, compact: Bool = false, fullWidth: Bool = false,
         systemImage: String? = nil, enabled: Bool = true, loading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.kind = kind
        self.compact = compact
        self.fullWidth = fullWidth
        self.systemImage = systemImage
        self.enabled = enabled
        self.loading = loading
        self.action = action
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: compact ? 10 : 14, style: .continuous)
        let ink = kind == .ghost ? StaryControlStyle.softInk : Color.white
        Button(action: action) {
            HStack(spacing: 6) {
                if loading {
                    StarLoadingView(size: compact ? 16 : 20, color: ink)
                } else {
                    if let systemImage {
                        Image(systemName: systemImage).font(.system(size: compact ? 13 : 16, weight: .semibold))
                    }
                    Text(title)
                        .font(.minSans(compact ? 13 : 15, kind == .ghost ? .medium : .semibold))
                        .lineLimit(1)
                }
            }
            .foregroundStyle(ink)
            .padding(.horizontal, compact ? 16 : 20)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: compact ? 40 : 50)
            .background { fill(shape) }
            .overlay { edge(shape) }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(!enabled || loading)
        .opacity(enabled || loading ? 1 : 0.4)
    }

    @ViewBuilder private func fill(_ shape: RoundedRectangle) -> some View {
        switch kind {
        case .primary:
            shape.fill(LinearGradient(colors: [Color(hex: 0x3A4676), Color(hex: 0x111936)], startPoint: .top, endPoint: .bottom))
        case .ghost:
            Color.clear
        case .danger:
            shape.fill(LinearGradient(colors: [Color(hex: 0xB04A5C), Color(hex: 0x6B2033)], startPoint: .top, endPoint: .bottom))
        }
    }

    @ViewBuilder private func edge(_ shape: RoundedRectangle) -> some View {
        switch kind {
        case .primary: shape.strokeBorder(Theme.cosmicEdge, lineWidth: 1)
        case .ghost: shape.strokeBorder(Theme.navyAccent.opacity(0.32), lineWidth: 1)
        case .danger: shape.strokeBorder(Color(hex: 0xFF96A0, alpha: 0.4), lineWidth: 1)
        }
    }
}

/// 앱 공용 칩(Android `StaryChip` 패리티) — 선택 / 선택 안 됨 / 잠김 세 상태가 어디서나 같은 모양.
/// 선택되면 `accent` 로 꽉 채우고 글씨는 어둡게. `action` 이 nil 이면 누를 수 없는 상태 표시.
/// `fill` 은 한 줄을 나눠 채우는 탭(업적 탭)용.
struct StaryChip: View {
    let label: String
    let selected: Bool
    var systemImage: String?
    var accent: Color = Theme.navyAccent
    var locked = false
    var fill = false
    var action: (() -> Void)?

    init(_ label: String, selected: Bool, systemImage: String? = nil, accent: Color = Theme.navyAccent,
         locked: Bool = false, fill: Bool = false, action: (() -> Void)? = nil) {
        self.label = label
        self.selected = selected
        self.systemImage = systemImage
        self.accent = accent
        self.locked = locked
        self.fill = fill
        self.action = action
    }

    var body: some View {
        if let action {
            Button(action: action) { chip }.buttonStyle(.plain)
        } else {
            chip
        }
    }

    private var chip: some View {
        let on = selected && !locked
        return HStack(spacing: 5) {
            if let icon = locked ? "lock.fill" : systemImage {
                Image(systemName: icon).font(.system(size: 12, weight: .semibold))
            }
            Text(label).font(.minSans(13, on ? .semibold : .normal)).lineLimit(1)
        }
        .foregroundStyle(on ? Theme.background : (locked ? Theme.textSecondary : StaryControlStyle.softInk))
        .padding(.horizontal, 13)
        .padding(.vertical, 7)
        .frame(maxWidth: fill ? .infinity : nil)
        .background(on ? accent : StaryControlStyle.chipIdle, in: Capsule())
        .overlay {
            if !on {
                Capsule().strokeBorder(locked ? Color.white.opacity(0.08) : accent.opacity(0.28), lineWidth: 1)
            }
        }
        .contentShape(Capsule())
    }
}

/// 면으로만 나눈 선택 줄(테두리 없음) — 고른 칸 아래로 옅은 별빛 판이 미끄러져 간다.
/// 업로드 화면 공개 범위·별 고르기 시트의 모양/색 탭. Android `StarySegmented` 패리티.
struct StarySegmented: View {
    let options: [(label: String, systemImage: String?)]
    @Binding var selection: Int
    var enabled = true
    @Namespace private var thumb

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options.indices, id: \.self) { i in
                let on = i == selection
                Button {
                    guard !on else { return }
                    withAnimation(.easeInOut(duration: 0.22)) { selection = i }
                } label: {
                    HStack(spacing: 6) {
                        if let icon = options[i].systemImage {
                            Image(systemName: icon)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(on ? Theme.navyAccent : Theme.textSecondary)
                        }
                        Text(options[i].label)
                            .font(.minSans(13, on ? .semibold : .normal))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .foregroundStyle(on ? Theme.textPrimary : Theme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 38)
                    .background {
                        if on {
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .fill(StaryControlStyle.segmentThumb)
                                .matchedGeometryEffect(id: "thumb", in: thumb)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(StaryControlStyle.segmentTrack, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .opacity(enabled ? 1 : 0.5)
        .allowsHitTesting(enabled)
    }
}

enum StaryControlStyle {
    /// 보조 버튼·칩 글씨(흐린 글자보다 한 단 밝게).
    static let softInk = Color(hex: 0xC5CBDB)
    /// 별하늘 위에서도 글씨가 읽히는 반투명 남색.
    static let chipIdle = Color(hex: 0x181E34, alpha: 0.55)
    /// 선택 줄 바탕 / 고른 칸(별빛 18%) — Android SegTrack / SegThumb.
    static let segmentTrack = Color(hex: 0x181E34, alpha: 0.6)
    static let segmentThumb = Color(hex: 0x9FB3E8, alpha: 0.18)
}
