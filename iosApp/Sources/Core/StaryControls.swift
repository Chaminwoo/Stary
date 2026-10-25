import SwiftUI

/// 버튼 종류 — 주(달빛 크림) / 보조(뒤가 비치는 유리) / 위험(옅은 장미색, 지우기·차단). Android `StaryButtonKind` 패리티.
enum StaryButtonKind { case primary, ghost, danger }

/// 앱 공용 버튼(Android `StaryButton` 패리티) — 둥근 캡슐(2026-10-26 "딱딱해 보인다" → 감성 다이어리에 맞게 부드럽게).
/// 주 버튼은 로그인 화면 크림 캡슐과 같은 달빛 크림, 보조는 뒤가 비치는 유리, 위험은 옅은 장미색. 누르면 살짝 작아지며 밝아진다.
/// 기본은 화면 아래 큰 버튼(높이 50), `compact` 는 확인 창·목록 행 안의 작은 버튼(높이 40). `fullWidth` 면 가로를 꽉 채운다.
/// 확인 창 버튼 줄([StaryDialogCard])에 들어가면 줄을 반씩 나눠 꽉 채우고 높이 44 가 된다(`staryButtonFill`).
/// `loading` 이면 글자 대신 도는 별을 보여 주고 눌리지 않는다(흐려지지는 않는다 — 진행 중이지 막힌 게 아니라서).
/// 글자가 칸보다 길면 한 줄 안에서 조금 줄어든다.
struct StaryButton: View {
    let title: String
    var kind: StaryButtonKind = .primary
    var compact = false
    var fullWidth = false
    var systemImage: String?
    /// SF Symbol 이 아닌 그림 아이콘(광고 재생 표시 등) — 글자색으로 칠한다.
    var image: Image?
    var enabled = true
    var loading = false
    let action: () -> Void
    @Environment(\.staryButtonFill) private var fillsRow

    init(_ title: String, kind: StaryButtonKind = .primary, compact: Bool = false, fullWidth: Bool = false,
         systemImage: String? = nil, image: Image? = nil, enabled: Bool = true, loading: Bool = false,
         action: @escaping () -> Void) {
        self.title = title
        self.kind = kind
        self.compact = compact
        self.fullWidth = fullWidth
        self.systemImage = systemImage
        self.image = image
        self.enabled = enabled
        self.loading = loading
        self.action = action
    }

    var body: some View {
        let ink = kind.ink
        Button(action: action) {
            HStack(spacing: 6) {
                if loading {
                    StarLoadingView(size: compact ? 16 : 20, color: ink)
                } else {
                    if let systemImage {
                        Image(systemName: systemImage).font(.system(size: compact ? 13 : 15, weight: .semibold))
                    } else if let image {
                        image.renderingMode(.template).resizable().scaledToFit()
                            .frame(width: compact ? 15 : 17, height: compact ? 15 : 17)
                    }
                    Text(title)
                        .font(.minSans(compact ? 14 : 15, kind == .ghost ? .medium : .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
            }
            .foregroundStyle(ink)
            .padding(.horizontal, compact ? 18 : 24)
            .frame(maxWidth: fullWidth || fillsRow ? .infinity : nil, minHeight: minHeight)
            .contentShape(Capsule())
        }
        .buttonStyle(StaryCapsuleStyle(kind: kind))
        .disabled(!enabled || loading)
        .opacity(enabled || loading ? 1 : 0.4)
    }

    private var minHeight: CGFloat { fillsRow ? 44 : (compact ? 40 : 50) }
}

/// 캡슐 면·테두리·눌림 — 누르면 0.98 로 줄며 위쪽부터 밝아진다(Android `StaryButton` 의 눌림과 같은 값, 물결 없음).
private struct StaryCapsuleStyle: ButtonStyle {
    let kind: StaryButtonKind

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        return configuration.label
            .background { Capsule().fill(kind.fill(pressed: pressed)) }
            .overlay { Capsule().strokeBorder(kind.edge, lineWidth: 1) }
            .scaleEffect(pressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.12), value: pressed)
    }
}

private extension StaryButtonKind {
    /// 글자·아이콘 색.
    var ink: Color {
        switch self {
        case .primary: return Color(hex: 0x2C2723) // 크림 위 숯색(로그인 버튼과 같은 값)
        case .ghost: return Color(hex: 0xE3E7F1)
        case .danger: return Color(hex: 0x3E1A22)
        }
    }

    /// 면 — 누르면 위쪽부터 밝아진다. 보조는 흰빛이 조금 짙어진다.
    func fill(pressed: Bool) -> LinearGradient {
        let p: Double = pressed ? 1 : 0
        switch self {
        case .primary: return Self.pill(Color(hex: 0xF7EDD8), Color(hex: 0xE9D6AE), p)
        case .danger: return Self.pill(Color(hex: 0xF5C9CF), Color(hex: 0xE5A3AE), p)
        case .ghost:
            let c = Color.white.opacity(0.08 + 0.06 * p)
            return LinearGradient(colors: [c, c], startPoint: .top, endPoint: .bottom)
        }
    }

    static func pill(_ top: Color, _ bottom: Color, _ p: Double) -> LinearGradient {
        let t = top.blended(with: .white, fraction: 0.35 * p).opacity(0.94)
        let b = bottom.blended(with: .white, fraction: 0.25 * p).opacity(0.94)
        return LinearGradient(colors: [t, b], startPoint: .top, endPoint: .bottom)
    }

    /// 테두리 — 위가 밝고 아래로 옅어지는 1pt(빛을 받은 가장자리).
    var edge: LinearGradient {
        let top: Double = self == .ghost ? 0.20 : 0.70
        let bottom: Double = self == .ghost ? 0.05 : 0.08
        return LinearGradient(colors: [Color.white.opacity(top), Color.white.opacity(bottom)],
                              startPoint: .top, endPoint: .bottom)
    }
}

private struct StaryButtonFillKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// 확인 창 버튼 줄([StaryDialogCard])이 켠다 — 안의 [StaryButton] 이 가로로 늘어나 줄을 반씩 나눠 채우고 높이 44 가 된다
    /// (Android `StaryDialog` 의 `ButtonSlot` 패리티).
    var staryButtonFill: Bool {
        get { self[StaryButtonFillKey.self] }
        set { self[StaryButtonFillKey.self] = newValue }
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
