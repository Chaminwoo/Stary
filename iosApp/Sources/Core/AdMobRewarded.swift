import Foundation
import UIKit
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

/// 보상형 광고 **폴백(backfill)** — Google AdMob. `AdsManager` 가 LevelPlay 에서 광고를 못 받았을 때(또는 LevelPlay 키가 없을 때)만 쓴다.
/// Android `core/ads/AdMobRewarded.kt` 패리티.
///
/// 어떤 광고가 나오나:
///  - **DEBUG 빌드 → 언제나** 구글 공식 iOS 보상형 **테스트** 단위(`ca-app-pub-3940256099942544/1712485313`) — 항상 채워지고 수익은 0.
///    (개발 중 실광고 요청/클릭은 구글 정책상 무효 트래픽이라 실제 단위를 넣어도 DEBUG 는 테스트 광고 — Android 동일)
///  - **Release + `ADMOB_REWARDED_AD_UNIT` 설정** → 그 실제 단위.
///  - **Release + 값 미설정** → `isConfigured == false`. 폴백이 아예 꺼진다(테스트 광고가 스토어에 나가지 않게).
///
/// ⚠️ AdMob SDK 는 Info.plist `GADApplicationIdentifier`(앱 ID)가 없으면 시작 시 **크래시**한다 →
///    앱 ID 가 비어 있으면 SDK 를 시작하지도 않는다(`isConfigured` false). DEBUG 의 테스트 앱 ID 는 `Config/Local.xcconfig` 가 넣는다.
/// ⚠️ SPM 패키지(GoogleMobileAds)가 없는 빌드에서도 컴파일되도록 `#if canImport` 로 감싼다(없으면 항상 미설정).
@MainActor
final class AdMobRewarded {
    static let shared = AdMobRewarded()
    private init() {}

    /// 구글이 공개한 iOS 보상형 **테스트** 광고 단위(개발용, 비밀 아님).
    private static let testRewardedUnit = "ca-app-pub-3940256099942544/1712485313"

    /// SPM 으로 SDK 가 링크됐는지.
    static var sdkLinked: Bool {
        #if canImport(GoogleMobileAds)
        return true
        #else
        return false
        #endif
    }

    private func plistString(_ key: String) -> String {
        ((Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Info.plist `GADApplicationIdentifier`(빌드설정 ADMOB_APP_ID).
    var appId: String { plistString("GADApplicationIdentifier") }

    /// DEBUG 는 항상 테스트 단위, Release 는 주입된 실제 단위(없으면 빈 값 = 폴백 꺼짐).
    var adUnitId: String {
        #if DEBUG
        return Self.testRewardedUnit
        #else
        return plistString("ADMOB_REWARDED_AD_UNIT")
        #endif
    }

    /// 폴백을 쓸 수 있는 빌드인지.
    var isConfigured: Bool { Self.sdkLinked && !appId.isEmpty && !adUnitId.isEmpty }

    /// 지금 바로 보여줄 광고가 손에 있는지.
    private(set) var isReady = false

    /// 로드 진행 중(AdsManager 가 "아직 기다릴 가치가 있나" 판단에 쓴다).
    private(set) var isLoading = false

    /// 마지막 실패 사유(진단용).
    private(set) var lastError: String?

    private var settleWaiters: [() -> Void] = []

    #if canImport(GoogleMobileAds)
    private var ad: RewardedAd?
    private var sdkStarted = false
    /// FullScreenContentDelegate 는 weak 참조라 우리가 쥐고 있어야 한다.
    private var presentDelegate: AdMobPresentDelegate?
    #endif

    /// 광고 한 개를 받아 둔다. 끝나면(성공/실패/할 일 없음) [onSettled] 를 **항상 한 번** 부른다 —
    /// 호출부가 "기다리던 재생 요청"을 정리할 수 있게. 이미 로드 중이면 그 결과가 날 때 함께 부른다.
    func load(onSettled: @escaping () -> Void) {
        #if canImport(GoogleMobileAds)
        guard isConfigured, !isReady else { onSettled(); return }
        if isLoading {
            settleWaiters.append(onSettled)
            return
        }
        if !sdkStarted {
            sdkStarted = true
            MobileAds.shared.start(completionHandler: nil)
        }
        isLoading = true
        settleWaiters.append(onSettled)
        RewardedAd.load(with: adUnitId, request: Request()) { [weak self] loaded, error in
            Task { @MainActor in
                guard let self else { return }
                self.isLoading = false
                if let loaded {
                    self.ad = loaded
                    self.isReady = true
                    self.lastError = nil
                } else {
                    self.ad = nil
                    self.isReady = false
                    self.lastError = "AdMob: \(error?.localizedDescription ?? "unknown")"
                    print("⚠️ [Ads] 폴백(AdMob) 보상형 로드 실패: \(self.lastError ?? "")")
                }
                let waiters = self.settleWaiters
                self.settleWaiters = []
                waiters.forEach { $0() }
            }
        }
        #else
        onSettled()
        #endif
    }

    /// 받아 둔 광고를 재생한다. [onResult] 는 **항상 한 번**: true = 끝까지 봐서 보상.
    /// AdMob 은 보상 콜백이 닫힘보다 먼저 오는 것이 보장돼 별도 유예가 필요 없다(LevelPlay 와 다른 점).
    func show(from viewController: UIViewController, onResult: @escaping (Bool) -> Void) {
        #if canImport(GoogleMobileAds)
        guard let current = ad else { onResult(false); return }
        // 한 번 쓴 광고 객체는 재사용 불가 — 즉시 손에서 놓는다.
        ad = nil
        isReady = false
        var rewarded = false
        var delivered = false
        let finish: () -> Void = { [weak self] in
            guard !delivered else { return }
            delivered = true
            self?.presentDelegate = nil
            onResult(rewarded)
        }
        let delegate = AdMobPresentDelegate(onFinish: finish)
        presentDelegate = delegate
        current.fullScreenContentDelegate = delegate
        current.present(from: viewController) { rewarded = true }
        #else
        onResult(false)
        #endif
    }
}

#if canImport(GoogleMobileAds)
/// 전체화면 광고 닫힘/표시 실패 → 결과 1회 전달.
private final class AdMobPresentDelegate: NSObject, FullScreenContentDelegate {
    private let onFinish: () -> Void

    init(onFinish: @escaping () -> Void) {
        self.onFinish = onFinish
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        onFinish()
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("⚠️ [Ads] 폴백(AdMob) 재생 실패: \(error.localizedDescription)")
        onFinish()
    }
}
#endif
