import SwiftUI
import UIKit

/// 앱 공용 팝업 — **Android `AlertDialog` 패리티**.
///
/// iOS 기본 `confirmationDialog`(하단 액션시트)/`alert` 은 위치를 시스템이 정하고 모서리가 크게 둥근
/// 말풍선처럼 보여, Android 와 생김새·위치가 전혀 달랐다(2026-08-25 사용자 지시).
/// → **화면 한가운데 뜨는 사각(모서리 18) 카드**로 통일한다.
///
/// 규격(Android `AlertDialog` + `ReportDialog` 에서 그대로 가져옴):
///  - 배경 `Theme.surface`(0x1A1A1A) / 테두리 `Theme.outline` 1pt / 모서리 18 / 안쪽 여백 20
///  - 제목 MinSans 17 SemiBold `textPrimary`, 설명 MinSans 14 `textSecondary`
///  - 최대 폭 340, 화면 좌우 여백 32
///
/// 구현 메모: 화면 전체(내비게이션 바 포함)를 덮어야 해서 `fullScreenCover` 로 띄우고
/// 배경을 투명하게 만든다([ClearBackgroundView]). 커버 자체의 슬라이드 전환은 끄고
/// 카드가 **가운데서 페이드+살짝 확대**되며 나타난다(Android 다이얼로그 등장 느낌).
enum StaryDialogStyle {
    /// 카드 모서리 반경 — "말풍선"처럼 보이지 않게 낮게(사용자 지시).
    static let corner: CGFloat = 18
    static let maxWidth: CGFloat = 340
    static let screenPadding: CGFloat = 32
    static let scrimOpacity: Double = 0.55
}

/// 선택지 한 줄(액션시트 항목 대체).
struct StaryDialogOption: Identifiable {
    let title: String
    /// 한 팝업 안에서 제목은 겹치지 않으므로 제목을 id 로 쓴다
    /// (UUID 를 쓰면 body 가 다시 평가될 때마다 id 가 바뀌어 ForEach 가 행을 새로 만든다).
    var id: String { title }
    /// 왼쪽 아이콘(SF Symbol). 없으면 글자만.
    var systemImage: String?
    /// 빨간 글씨(삭제/차단 등).
    var isDestructive: Bool
    let action: () -> Void

    init(_ title: String, systemImage: String? = nil, isDestructive: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.isDestructive = isDestructive
        self.action = action
    }
}

extension View {

    /// 임의 내용을 담는 가운데 팝업. 카드 껍데기([StaryDialogCard])는 호출부가 붙인다.
    func staryDialog<C: View>(isPresented: Binding<Bool>,
                              @ViewBuilder content: @escaping () -> C) -> some View {
        // ⚠️ 0 크기 호스트에 달아서 띄운다 — 전환 애니메이션을 끄는 `.transaction` 이
        //    화면 본문의 다른 애니메이션까지 죽이지 않도록 격리하기 위함.
        background(
            Color.clear
                .frame(width: 0, height: 0)
                .fullScreenCover(isPresented: isPresented) {
                    StaryDialogHost(content: content)
                }
                .transaction { $0.disablesAnimations = true }
        )
    }

    /// 선택지 목록 팝업 — iOS 액션시트(`confirmationDialog`) 대체.
    func staryChoiceDialog(_ title: String,
                           isPresented: Binding<Bool>,
                           message: String? = nil,
                           options: [StaryDialogOption]) -> some View {
        staryDialog(isPresented: isPresented) {
            StaryDialogCard(title: title, message: message) {
                VStack(spacing: 2) {
                    ForEach(options) { option in
                        Button {
                            isPresented.wrappedValue = false
                            // 이 팝업이 닫히는 도중에 다른 모달(카메라/사진 선택 등)을 띄우면
                            // 표시가 씹히므로 한 틱 뒤에 실행한다.
                            DispatchQueue.main.async { option.action() }
                        } label: {
                            HStack(spacing: 10) {
                                if let icon = option.systemImage {
                                    Image(systemName: icon)
                                        .font(.system(size: 16))
                                        .frame(width: 20)
                                }
                                Text(option.title)
                                    .font(.minSans(16))
                                Spacer(minLength: 0)
                            }
                            .foregroundStyle(option.isDestructive ? Theme.accentRed : Theme.textPrimary)
                            .padding(.vertical, 13)
                            .padding(.horizontal, 6)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            } actions: {
                StaryDialogTextButton(LocaleManager.shared.t(.commonCancel), color: Theme.textSecondary) {
                    isPresented.wrappedValue = false
                }
            }
        }
    }

    /// 확인/취소 2버튼 팝업 — `alert` 대체.
    func staryConfirmDialog(_ title: String,
                            isPresented: Binding<Bool>,
                            message: String? = nil,
                            confirmTitle: String,
                            destructive: Bool = false,
                            onConfirm: @escaping () -> Void) -> some View {
        staryDialog(isPresented: isPresented) {
            StaryDialogCard(title: title, message: message) {
                EmptyView()
            } actions: {
                StaryDialogTextButton(LocaleManager.shared.t(.commonCancel), color: Theme.textSecondary) {
                    isPresented.wrappedValue = false
                }
                StaryDialogTextButton(confirmTitle,
                                      color: destructive ? Theme.accentRed : Theme.navyAccent,
                                      weight: .semibold) {
                    isPresented.wrappedValue = false
                    DispatchQueue.main.async { onConfirm() }
                }
            }
        }
    }

    /// 확인 1버튼 안내 팝업 — 단순 `alert` 대체.
    func staryInfoDialog(_ title: String,
                         isPresented: Binding<Bool>,
                         message: String? = nil,
                         onDismiss: (() -> Void)? = nil) -> some View {
        staryDialog(isPresented: isPresented) {
            StaryDialogCard(title: title, message: message) {
                EmptyView()
            } actions: {
                StaryDialogTextButton(LocaleManager.shared.t(.commonOk),
                                      color: Theme.navyAccent, weight: .semibold) {
                    isPresented.wrappedValue = false
                    onDismiss?()
                }
            }
        }
    }
}

/// 팝업 카드 껍데기 — 제목/설명/본문/하단 버튼 줄.
struct StaryDialogCard<Content: View, Actions: View>: View {
    let title: String
    var message: String?
    @ViewBuilder let content: () -> Content
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !title.isEmpty {
                Text(title)
                    .font(.minSans(17, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let message, !message.isEmpty {
                Text(message)
                    .font(.minSans(14))
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, title.isEmpty ? 0 : 8)
            }
            content()
                .padding(.top, 14)
            HStack(spacing: 6) {
                Spacer(minLength: 0)
                actions()
            }
            .padding(.top, 12)
        }
        .padding(20)
        .frame(maxWidth: StaryDialogStyle.maxWidth)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: StaryDialogStyle.corner))
        .overlay(
            RoundedRectangle(cornerRadius: StaryDialogStyle.corner)
                .strokeBorder(Theme.outline, lineWidth: 1)
        )
        .padding(.horizontal, StaryDialogStyle.screenPadding)
    }
}

/// 팝업 하단 텍스트 버튼(Android `TextButton` 톤).
struct StaryDialogTextButton: View {
    let title: String
    var color: Color = Theme.navyAccent
    var weight: MinSansWeight = .normal
    var enabled: Bool = true
    let action: () -> Void

    init(_ title: String, color: Color = Theme.navyAccent, weight: MinSansWeight = .normal,
         enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.color = color
        self.weight = weight
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.minSans(15, weight))
                .foregroundStyle(color.opacity(enabled ? 1 : 0.35))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

// MARK: - 내부 구현

/// 투명 배경 + 스크림 위에 카드를 얹는 커버 내용물.
private struct StaryDialogHost<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss
    @State private var shown = false

    var body: some View {
        ZStack {
            // 바깥을 누르면 닫힌다 — Android `Dialog(onDismissRequest = ...)` 와 같은 동작.
            Color.black.opacity(shown ? StaryDialogStyle.scrimOpacity : 0)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { dismiss() }
            content()
                .scaleEffect(shown ? 1 : 0.94)
                .opacity(shown ? 1 : 0)
        }
        .background(ClearBackgroundView())
        .onAppear {
            withAnimation(.easeOut(duration: 0.16)) { shown = true }
        }
    }
}

/// `fullScreenCover` 의 흰(불투명) 배경을 지워 스크림이 비치게 한다.
/// (SwiftUI 에 투명 커버 API 가 없어 쓰는 표준 우회 — 호스팅 뷰의 배경색만 건드린다.)
private struct ClearBackgroundView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        DispatchQueue.main.async { [weak view] in
            view?.superview?.superview?.backgroundColor = .clear
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
