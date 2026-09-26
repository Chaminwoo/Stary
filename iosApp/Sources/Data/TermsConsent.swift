import FirebaseFirestore
import SwiftUI

/// 이용약관(EULA) 동의 기록 — App Store Guideline 1.2(사용자 생성 콘텐츠) 대응(2026-09-26).
/// Android `core.util.TermsConsent` 패리티(같은 키·버전).
///
/// 로그인·둘러보기 **전에** 약관(무관용 원칙 · 자동 필터 · 신고/차단 · 24시간 내 조치)에 동의해야 한다.
///  - 기기 단위로 먼저 저장(UserDefaults) — 둘러보기(비로그인)도 동의가 필요하기 때문.
///  - 로그인 상태면 `users/{uid}.termsAcceptedAt/termsVersion` 에도 남긴다(운영 기록).
///  - 약관 내용이 바뀌면 `version` 을 올린다 → 모두 다시 동의해야 한다.
enum TermsConsent {
    static let version = 1
    private static var key: String { "terms_accepted_v\(version)" }

    static var isAccepted: Bool { UserDefaults.standard.bool(forKey: key) }

    /// 동의 저장. `uid` 가 있으면 서버 프로필에도 기록(실패해도 동의 자체는 유지).
    static func accept(uid: String?) {
        UserDefaults.standard.set(true, forKey: key)
        if let uid, !uid.isEmpty { record(uid: uid) }
    }

    /// 이미 동의한 기기에서 로그인했을 때 서버 기록만 남긴다.
    static func record(uid: String) {
        FirestoreService.users.document(uid).setData(
            ["termsAcceptedAt": FirestoreService.nowMillis, "termsVersion": version],
            merge: true
        )
    }
}

/// 이용약관 카드 — `staryDialog` 안에 넣는다. Android `TermsDialog` 패리티.
///  - `requireAgreement` = true : 로그인/둘러보기 전 동의 게이트("동의하고 계속" + 거절 문구).
///  - false : 설정 > 이용약관 보기 전용(확인 버튼 하나).
struct TermsDialogCard: View {
    let requireAgreement: Bool
    var declineTitle: String = LocaleManager.shared.t(.termsDecline)
    var onAgree: () -> Void = {}
    let onDismiss: () -> Void
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(locale.t(.termsTitle))
                .font(.minSans(18, .semibold)).foregroundStyle(Theme.textPrimary)
            if requireAgreement {
                Spacer().frame(height: 6)
                Text(locale.t(.termsIntro))
                    .font(.minSans(13)).foregroundStyle(Theme.mint)
            }
            Spacer().frame(height: 12)
            ScrollView {
                Text(locale.t(.termsBody))
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textPrimary.opacity(0.86))
                    .lineSpacing(5)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
            }
            .frame(maxHeight: 360)
            .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
            Spacer().frame(height: 14)
            if requireAgreement {
                Button(action: onAgree) {
                    Text(locale.t(.termsAgree))
                        .font(.minSans(15)).foregroundStyle(Color(hex: 0x0D0D0D))
                        .frame(maxWidth: .infinity).padding(.vertical, 13)
                        .background(LinearGradient(colors: [Theme.mint, Color(hex: 0x4DD0E1)],
                                                   startPoint: .leading, endPoint: .trailing),
                                    in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                Button(action: onDismiss) {
                    Text(declineTitle)
                        .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            } else {
                HStack {
                    Spacer()
                    StaryDialogTextButton(locale.t(.commonOk), color: Theme.mint, weight: .semibold, action: onDismiss)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: StaryDialogStyle.maxWidth)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: StaryDialogStyle.corner))
        .overlay(RoundedRectangle(cornerRadius: StaryDialogStyle.corner).stroke(Theme.outline, lineWidth: 1))
        .padding(.horizontal, StaryDialogStyle.screenPadding)
    }
}
