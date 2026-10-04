import Foundation
import UIKit
#if canImport(IronSource)
import IronSource
#endif

/// Unity LevelPlay(ironSource) 보상형 광고 래퍼 — `AdsManager` 의 1순위 광고원. Android `AdsManager.kt` 의 LevelPlay 부분 패리티.
///
/// SDK 는 SPM(`LevelPlay-Swift-Package`, 제품 `UnityMediationSDK`) 로 들어온다(project.yml). 모듈 이름은 `IronSource`.
/// 패키지가 없는 빌드에서도 컴파일되도록 `#if canImport(IronSource)` 로 감싼다(없으면 항상 미설정 → AdMob 폴백만).
/// ⚠️ LevelPlay API(`LPMInitRequestBuilder`/`LevelPlay.initWith`/`LPMRewardedAd`)는 공식 문서(SDK 8.5+/9.x) 기준으로 작성했고
///    Windows 에서 컴파일 검증을 못 했다 — CI 에서 이 파일이 빨갛게 되면 시그니처부터 확인할 것(docs/IOS_ADS_SETUP.md).
///
/// 이 클래스는 광고 객체 수명주기만 맡고, 결과 해석(보상/닫힘 순서, 폴백, 대기)은 `AdsManager` 가 한다 — 이벤트는 클로저로 올린다.
@MainActor
final class LevelPlayRewarded: NSObject {
    static let shared = LevelPlayRewarded()
    private override init() { super.init() }

    /// SPM 으로 SDK 가 링크됐는지.
    static var sdkLinked: Bool {
        #if canImport(IronSource)
        return true
        #else
        return false
        #endif
    }

    private func plistString(_ key: String) -> String {
        ((Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// LevelPlay iOS 앱 키(Info.plist ← 빌드설정 LEVELPLAY_APP_KEY). Android 와 **다른 값**(iOS 앱을 따로 추가해 받는다).
    var appKey: String { plistString("LEVELPLAY_APP_KEY") }

    /// LevelPlay 보상형 광고 단위 ID(Info.plist ← LEVELPLAY_REWARDED_AD_UNIT).
    var adUnitId: String { plistString("LEVELPLAY_REWARDED_AD_UNIT") }

    /// 키가 실제 값이고 SDK 가 링크돼 있는지.
    var isConfigured: Bool { Self.sdkLinked && !appKey.isEmpty && !adUnitId.isEmpty }

    private(set) var initialized = false
    private(set) var initStarted = false
    private(set) var isLoading = false
    private(set) var lastError: String?

    // ── AdsManager 가 다는 이벤트 ──
    var onInitResult: ((Bool) -> Void)?
    var onLoaded: (() -> Void)?
    var onLoadFailed: (() -> Void)?
    var onRewarded: (() -> Void)?
    var onClosed: (() -> Void)?
    var onDisplayFailed: (() -> Void)?

    #if canImport(IronSource)
    private var rewardedAd: LPMRewardedAd?
    #endif

    /// 지금 바로 보여줄 수 있는 상태인지.
    var isReady: Bool {
        #if canImport(IronSource)
        return rewardedAd?.isAdReady() ?? false
        #else
        return false
        #endif
    }

    /// SDK 초기화 — 성공 뒤에만 광고 객체를 만든다(LevelPlay 규칙). 실패하면 다음 호출에서 다시 시도한다.
    func initSDK() {
        #if canImport(IronSource)
        guard isConfigured, !initStarted, !initialized else { return }
        initStarted = true
        let request = LPMInitRequestBuilder(appKey: appKey).build()
        LevelPlay.initWith(request) { [weak self] _, error in
            let ok = (error == nil)
            let message = error.map { "init: \($0.localizedDescription)" }
            Task { @MainActor in
                guard let self else { return }
                if ok {
                    self.initialized = true
                    self.lastError = nil
                    self.createRewardedAd()
                } else {
                    self.initStarted = false // 다음 preload/탭에서 다시 시도
                    self.lastError = message
                    print("⚠️ [Ads] LevelPlay 초기화 실패: \(message ?? "")")
                }
                self.onInitResult?(ok)
            }
        }
        #endif
    }

    #if canImport(IronSource)
    /// 광고 객체는 **초기화 성공 뒤에만** 만든다. 델리게이트는 로드 전에 단다.
    private func createRewardedAd() {
        guard rewardedAd == nil else { return }
        let ad = LPMRewardedAd(adUnitId: adUnitId)
        ad.setDelegate(self)
        rewardedAd = ad
    }
    #endif

    /// 광고 로드 시작(이미 로드 중이거나 준비돼 있으면 무시).
    func load() {
        #if canImport(IronSource)
        guard let ad = rewardedAd, !isLoading, !ad.isAdReady() else { return }
        isLoading = true
        ad.loadAd()
        #endif
    }

    /// 재생. 호출 전에 `isReady` 확인.
    func show(from viewController: UIViewController) {
        #if canImport(IronSource)
        rewardedAd?.showAd(viewController: viewController, placementName: nil)
        #endif
    }

    // MARK: - 델리게이트 → 메인 액터 이벤트

    fileprivate func handleLoaded() {
        isLoading = false
        lastError = nil
        onLoaded?()
    }

    fileprivate func handleLoadFailed(_ message: String) {
        isLoading = false
        lastError = message
        print("⚠️ [Ads] LevelPlay 보상형 로드 실패(\(adUnitId)): \(message)")
        onLoadFailed?()
    }

    fileprivate func handleDisplayFailed(_ message: String) {
        print("⚠️ [Ads] LevelPlay 보상형 재생 실패: \(message)")
        onDisplayFailed?()
    }
}

#if canImport(IronSource)
// SDK 콜백은 메인 스레드에서 오지만, 델리게이트 메서드는 격리 없이 선언하고 Task 로 메인 액터에 넘긴다(격리 불일치 경고 회피).
extension LevelPlayRewarded: LPMRewardedAdDelegate {
    nonisolated func didLoadAd(with adInfo: LPMAdInfo) {
        Task { @MainActor in self.handleLoaded() }
    }

    nonisolated func didFailToLoadAd(withAdUnitId adUnitId: String, error: Error) {
        let message = "load: \(error.localizedDescription)"
        Task { @MainActor in self.handleLoadFailed(message) }
    }

    nonisolated func didChangeAdInfo(_ adInfo: LPMAdInfo) {}

    nonisolated func didDisplayAd(with adInfo: LPMAdInfo) {}

    nonisolated func didFailToDisplayAd(with adInfo: LPMAdInfo, error: Error) {
        let message = error.localizedDescription
        Task { @MainActor in self.handleDisplayFailed(message) }
    }

    nonisolated func didClickAd(with adInfo: LPMAdInfo) {}

    nonisolated func didCloseAd(with adInfo: LPMAdInfo) {
        Task { @MainActor in self.onClosed?() }
    }

    nonisolated func didRewardAd(with adInfo: LPMAdInfo, reward: LPMReward) {
        Task { @MainActor in self.onRewarded?() }
    }
}
#endif
