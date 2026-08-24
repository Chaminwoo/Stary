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
    /// 신고 사유 선택 팝업 — **Android `ReportDialog` 와 같은 모양**(가운데 사각 카드,
    /// 라디오 목록, "기타"를 고르면 그 자리에서 사유 입력칸이 열린다).
    /// [onPick] 에 선택한 사유 키("spam" 등)와 "기타"일 때 적은 설명(그 외엔 빈 문자열)을 넘긴다.
    func reportDialog(title: String, isPresented: Binding<Bool>,
                      onPick: @escaping (_ reason: String, _ detail: String) -> Void) -> some View {
        staryDialog(isPresented: isPresented) {
            ReportDialogContent(title: title) { reason, detail in
                isPresented.wrappedValue = false
                onPick(reason, detail)
            } onCancel: {
                isPresented.wrappedValue = false
            }
        }
    }
}

/// 신고 사유 카드 본문 — 라디오 목록 + "기타" 상세 입력.
private struct ReportDialogContent: View {
    let title: String
    let onSubmit: (String, String) -> Void
    let onCancel: () -> Void

    @ObservedObject private var locale = LocaleManager.shared
    @State private var selected: ReportReason?
    @State private var detail = ""
    @FocusState private var detailFocused: Bool

    private var isOther: Bool { selected == .other }
    /// "기타"는 설명을 적어야 접수된다(빈 설명은 관리자가 검토할 수 없음 — Android 동일).
    private var canSubmit: Bool {
        guard let selected else { return false }
        return selected != .other || !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        StaryDialogCard(title: title) {
            VStack(spacing: 0) {
                ForEach(ReportReason.allCases) { reason in
                    Button {
                        selected = reason
                        if reason != .other { detailFocused = false }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: selected == reason ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 18))
                                .foregroundStyle(selected == reason ? Theme.mint : Theme.textSecondary.opacity(0.5))
                            Text(locale.t(reason.label))
                                .font(.minSans(15))
                                .foregroundStyle(selected == reason ? Theme.textPrimary : Theme.textSecondary)
                            Spacer(minLength: 0)
                        }
                        .padding(.vertical, 11)
                        .padding(.horizontal, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                // "기타"는 사유만으론 관리자가 판단할 수 없다 → 그 자리에서 적을 칸을 연다.
                if isOther {
                    TextField(locale.t(.reportReasonDetailHint), text: $detail, axis: .vertical)
                        .font(.minSans(15))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(2...4)
                        .focused($detailFocused)
                        .padding(10)
                        .background(Theme.surfaceAlt, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(Theme.mint.opacity(0.45), lineWidth: 1))
                        .onChange(of: detail) { v in
                            if v.count > reportDetailMaxLen { detail = String(v.prefix(reportDetailMaxLen)) }
                        }
                    Text("\(detail.count)/\(reportDetailMaxLen)")
                        .font(.minSans(11))
                        .foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.top, 4)
                }
            }
        } actions: {
            StaryDialogTextButton(locale.t(.commonCancel), color: Theme.textSecondary, action: onCancel)
            StaryDialogTextButton(locale.t(.reportSubmit),
                                  color: Theme.accentRed, weight: .semibold, enabled: canSubmit) {
                guard let selected, canSubmit else { return }
                let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
                onSubmit(selected.rawValue, selected == .other ? trimmed : "")
            }
        }
    }
}
