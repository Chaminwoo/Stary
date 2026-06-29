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

extension View {
    /// 신고 사유 선택 시트(네이티브 confirmationDialog). [onPick] 에 선택한 사유 키("spam" 등)를 넘긴다.
    func reportDialog(title: String, isPresented: Binding<Bool>, onPick: @escaping (String) -> Void) -> some View {
        let locale = LocaleManager.shared
        return confirmationDialog(title, isPresented: isPresented, titleVisibility: .visible) {
            ForEach(ReportReason.allCases) { reason in
                Button(locale.t(reason.label)) { onPick(reason.rawValue) }
            }
            Button(locale.t(.commonCancel), role: .cancel) {}
        }
    }
}
