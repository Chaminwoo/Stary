import SwiftUI

/// 신고 사유 — (저장 키 = rawValue, 표시 L10n). 다이어리/사용자/댓글 신고에 공용.
/// (Android `core.ui.ReportDialog` 의 REPORT_REASONS 패리티 — 같은 키를 reports.reason 에 저장.)
enum ReportReason: String, CaseIterable, Identifiable {
    case spam, abuse, inappropriate, impersonation, other
    var id: String { rawValue }
    var label: L10n {
        switch self {
        case .spam:          return .reportReasonSpam
        case .abuse:         return .reportReasonAbuse
        case .inappropriate: return .reportReasonInappropriate
        case .impersonation: return .reportReasonImpersonation
        case .other:         return .reportReasonOther
        }
    }
}

/// "기타" 사유를 골랐을 때 직접 적는 설명의 최대 길이 (Android REPORT_DETAIL_MAX_LEN 패리티).
let reportDetailMaxLen = 200

extension View {
    /// 신고 사유 선택 시트(네이티브 confirmationDialog).
    /// [onPick] 에 선택한 사유 키("spam" 등)와, **"기타"를 골랐을 때 직접 적은 설명**을 넘긴다
    /// (그 외 사유는 빈 문자열). Android ReportDialog 의 2단계(사유 → 상세) 흐름 패리티.
    func reportDialog(title: String, isPresented: Binding<Bool>,
                      onPick: @escaping (_ reason: String, _ detail: String) -> Void) -> some View {
        modifier(ReportDialogModifier(title: title, isPresented: isPresented, onPick: onPick))
    }
}

/// 사유 선택 시트 + "기타" 상세 입력 알럿을 한 쌍으로 묶은 수정자.
/// (confirmationDialog 안에는 TextField 를 넣을 수 없어, "기타"만 알럿으로 한 단계 더 받는다.)
private struct ReportDialogModifier: ViewModifier {
    let title: String
    @Binding var isPresented: Bool
    let onPick: (String, String) -> Void

    @ObservedObject private var locale = LocaleManager.shared
    @State private var showDetail = false
    @State private var detail = ""

    func body(content: Content) -> some View {
        content
            .confirmationDialog(title, isPresented: $isPresented, titleVisibility: .visible) {
                ForEach(ReportReason.allCases) { reason in
                    Button(locale.t(reason.label)) {
                        guard reason == .other else { onPick(reason.rawValue, ""); return }
                        detail = ""
                        // 시트가 닫히는 동안 알럿을 띄우면 표시되지 않는 경우가 있어 한 프레임 뒤에 연다.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showDetail = true }
                    }
                }
                Button(locale.t(.commonCancel), role: .cancel) {}
            }
            .alert(locale.t(.reportReasonOther), isPresented: $showDetail) {
                TextField(locale.t(.reportReasonDetailHint), text: $detail)
                Button(locale.t(.commonCancel), role: .cancel) {}
                Button(locale.t(.reportSubmit)) {
                    let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
                    // 설명이 비면 관리자가 검토할 수 없으므로 접수하지 않는다(Android 전송 버튼 비활성 대응).
                    guard !trimmed.isEmpty else { return }
                    onPick(ReportReason.other.rawValue, String(trimmed.prefix(reportDetailMaxLen)))
                }
            } message: {
                Text(locale.t(.reportReasonDetailHint))
            }
    }
}
