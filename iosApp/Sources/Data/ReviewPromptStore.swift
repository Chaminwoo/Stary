import Foundation
import StoreKit
import UIKit

/// 평점 요청 정책 — Android `core/ui/ReviewPrompt.kt` 패리티(2026-11-02).
///
/// Apple 가이드라인 5.6.1 은 **커스텀 평점 카드를 금지**하므로 iOS 는 카드/토스트 없이,
/// Android 가 카드를 띄우는 것과 같은 시점(별 탄생 연출이 끝난 1.5초 뒤)·같은 조건에서
/// 시스템 평점 창(`SKStoreReviewController.requestReview(in:)`)만 요청한다. 실제로 창이 뜨는지는 시스템이 정한다
/// (연 3회 상한 등) — 그래도 요청한 순간을 "노출 1회"로 센다. 의견 보내기는 설정 화면에서만 열린다.
///
/// 조건·상수는 Android 와 같은 값: 설치 [minInstallDays]일 이상 + 내가 올린 별 [minStars]개 이상, 평생 [maxShows]번,
/// 직전 요청으로부터 [minGapDays]일 이상. 기록은 이 기기의 UserDefaults(`review_prompt.` 접두어 전용 키).
/// (iOS 는 버튼이 없어 "눌렀다 = 다시는 안 띄움" 규칙이 해당 없다.)
enum ReviewPromptStore {
    static let minInstallDays = 7
    static let minStars = 3
    static let maxShows = 3
    static let minGapDays = 90
    /// 별 탄생 연출이 끝난 뒤 요청하기까지의 대기(초) — Android DELAY_AFTER_BIRTH_MS(1500) 와 같다.
    static let delayAfterBirthSeconds: Double = 1.5

    private static let shownCountKey = "review_prompt.shown_count"
    private static let lastShownAtKey = "review_prompt.last_shown_at"
    private static let firstSeenKey = "review_prompt.first_seen_at"
    private static let daySeconds: TimeInterval = 24 * 3600

    /// 앱을 설치한 시각 — 앱 컨테이너(Documents)가 만들어진 날(업데이트로는 바뀌지 않음). 못 읽으면 이 기기에서 처음 본 시각.
    private static var installDate: Date {
        if let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
           let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
           let created = attrs[.creationDate] as? Date {
            return created
        }
        let defaults = UserDefaults.standard
        if let seen = defaults.object(forKey: firstSeenKey) as? Date { return seen }
        let now = Date()
        defaults.set(now, forKey: firstSeenKey)
        return now
    }

    /// 설치 기간 + 별 개수 + 기록 — 화면 상태(가드)를 뺀 자격.
    static func eligible(starCount: Int) -> Bool {
        guard starCount >= minStars else { return false }
        guard Date().timeIntervalSince(installDate) >= Double(minInstallDays) * daySeconds else { return false }
        let defaults = UserDefaults.standard
        guard defaults.integer(forKey: shownCountKey) < maxShows else { return false }
        if let last = defaults.object(forKey: lastShownAtKey) as? Date,
           Date().timeIntervalSince(last) < Double(minGapDays) * daySeconds { return false }
        return true
    }

    /// 시스템 평점 창을 요청하고 노출 1회로 기록한다. 앞에 떠 있는(foregroundActive) 장면이 없으면 요청하지 않고 기록도 안 한다.
    @MainActor
    static func requestSystemReview() {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else { return }
        let defaults = UserDefaults.standard
        defaults.set(defaults.integer(forKey: shownCountKey) + 1, forKey: shownCountKey)
        defaults.set(Date(), forKey: lastShownAtKey)
        SKStoreReviewController.requestReview(in: scene)
    }
}
