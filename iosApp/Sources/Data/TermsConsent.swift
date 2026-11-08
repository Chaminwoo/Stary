import FirebaseFirestore
import SwiftUI

/// 이용약관(EULA) 동의 기록 — App Store Guideline 1.2(사용자 생성 콘텐츠) 대응(2026-09-26).
/// Android `core.util.TermsConsent` 패리티(같은 키·버전).
///
/// 로그인 **전에** 약관(무관용 원칙 · 자동 필터 · 신고/차단 · 24시간 내 조치)에 동의해야 한다.
///  - 기기 단위로 먼저 저장(UserDefaults) — 로그인 전에도 동의를 받아야 하기 때문.
///  - 로그인 상태면 `users/{uid}.termsAcceptedAt/termsVersion` 에도 남긴다(운영 기록).
///  - 약관 내용이 바뀌면 `version` 을 올린다 → 모두 다시 동의해야 한다.
enum TermsConsent {
    /// 2(2026-11-08): 위치기반서비스 이용약관 · 만 14세 이상 · 개인정보 처리방침 확인을 같은 동의에 묶었다 → 기존 사용자도 한 번 다시 동의한다.
    static let version = 2
    private static var key: String { "terms_accepted_v\(version)" }

    static var isAccepted: Bool { UserDefaults.standard.bool(forKey: key) }

    /// 동의 저장. `uid` 가 있으면 서버 프로필에도 기록(실패해도 동의 자체는 유지).
    static func accept(uid: String?) {
        UserDefaults.standard.set(true, forKey: key)
        if let uid, !uid.isEmpty { record(uid: uid) }
    }

    /// 이미 동의한 기기에서 로그인했을 때 서버 기록만 남긴다.
    static func record(uid: String) {
        FirestoreService.users.safeDocument(uid).setData(
            ["termsAcceptedAt": FirestoreService.nowMillis, "termsVersion": version],
            merge: true
        )
    }
}

/// 이용약관 카드 — `staryDialog` 안에 넣는다. Android `TermsDialog` 패리티.
///  - `requireAgreement` = true : 로그인 전 동의 게이트("동의하고 계속" + 거절 문구).
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
                .font(.minSans(17, .semibold)).foregroundStyle(Theme.textPrimary)
            if requireAgreement {
                Spacer().frame(height: 6)
                Text(locale.t(.termsIntro))
                    .font(.minSans(13)).foregroundStyle(Theme.navyAccent)
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
            Spacer().frame(height: 10)
            // 위치기반서비스 이용약관 · 개인정보 처리방침 — 동의 전에 열어 볼 수 있어야 한다(위치정보법 §18 · 개인정보보호법 §30).
            HStack(spacing: 14) {
                policyLink(locale.t(.settingsLocationTerms), AppConfig.locationTermsUrl)
                policyLink(locale.t(.settingsPrivacy), AppConfig.privacyPolicyUrl)
            }
            if requireAgreement {
                Spacer().frame(height: 8)
                // 한 번의 동의에 무엇이 들어 있는지 — 만 14세 이상(개인정보보호법 §22-2) · 두 약관 · 처리방침 확인.
                Text(locale.t(.termsConsentNote))
                    .font(.minSans(11.5)).foregroundStyle(Theme.textSecondary)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer().frame(height: 14)
            if requireAgreement {
                // 동의가 주 동작 — 넓은 크림 캡슐, 거절은 그 아래 작은 글씨(Android 패리티).
                StaryButton(locale.t(.termsAgree), fullWidth: true, action: onAgree)
                Button(action: onDismiss) {
                    Text(declineTitle)
                        .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            } else {
                StaryButton(locale.t(.commonOk), compact: true, fullWidth: true, action: onDismiss)
            }
        }
        .padding(20)
        .frame(maxWidth: 360)
        // 판은 다른 팝업과 같은 유리(2026-10-26, 불투명 면에서 바꿈).
        .glassPanel(cornerRadius: StaryDialogStyle.corner, tint: StaryDialogStyle.glassTint)
        .padding(.horizontal, StaryDialogStyle.screenPadding)
    }

    /// 약관 창 아래 작은 밑줄 링크 — 사파리로 연다(web/privacy.html · web/location-terms.html).
    @ViewBuilder
    private func policyLink(_ title: String, _ urlString: String) -> some View {
        if let url = URL(string: urlString) {
            Link(destination: url) {
                Text(title)
                    .font(.minSans(12)).underline()
                    .foregroundStyle(Theme.navyAccent)
            }
        }
    }
}
