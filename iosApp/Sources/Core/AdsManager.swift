import AppTrackingTransparency
import Foundation
import SwiftUI
import UIKit

/// 보상형 광고 — iOS 에서 광고를 쓰는 **유일한 진입점**. Android `core.ads.AdsManager` 와 같은 계약(2026-10-04 연결).
///
/// 광고원은 두 곳, **순서가 있다**:
///  1. **Unity LevelPlay**(`LevelPlayRewarded`) — 기본. 미디에이션이 이긴 광고를 보여준다.
///  2. **Google AdMob 폴백**(`AdMobRewarded`) — LevelPlay 가 광고를 못 줬을 때(`509 No fill` 등)만. LevelPlay 키가 없으면 AdMob 만.
/// 두 광고원이 **모두** 미설정이면 `isConfigured == false` → 호출부는 탭해도 "지금은 광고를 불러올 수 없어요"(키 없이도 앱은 정상 동작).
///
/// 쓰는 곳: 100m 밖 게시물 잠금 해제(`DiaryUnlockStore`, DetailScreen `lockedContentCard`).
/// 흐름: `initialize()`(앱 시작 1회) → `preload()`(잠금 화면 진입 시) → `showRewardedWhenReady`(재생 아이콘 탭).
///  아직 로드 중이면 최대 `waitForLoad`(12초) 기다렸다가 도착하는 즉시 재생한다(그동안 "광고를 불러오는 중이에요").
///
/// - 보상 판정: LevelPlay 는 `didRewardAd` 뿐. 닫힘보다 **늦게** 올 수 있다고 명시돼 있어 닫힌 뒤 `rewardGrace`(1.5초)
///   동안 보상 콜백을 기다렸다가 결과를 한 번만 넘긴다. AdMob 은 순서가 보장돼 불필요.
/// - **ATT**(앱 추적 투명성): 광고 키가 하나라도 있으면 첫 실행 때 한 번 동의 팝업을 띄우고(앱이 active 가 된 뒤, 비차단),
///   **답이 난 뒤에** SDK 를 시작한다(거부해도 광고는 나온다 — 개인화만 빠짐). 키가 없으면 팝업도 없다.
/// - ⚠️ 이 클래스는 `@MainActor` — 호출부/콜백 모두 메인 스레드.
@MainActor
final class AdsManager: ObservableObject {
    static let shared = AdsManager()
    private init() {}

    private let levelPlay = LevelPlayRewarded.shared
    private let admob = AdMobRewarded.shared

    /// 탭했는데 아직 로드 전이면 이만큼 기다린다(LevelPlay 실패 → 폴백 로드까지 이어질 수 있어 넉넉히).
    private static let waitForLoad: TimeInterval = 12
    /// 광고가 닫힌 뒤 늦게 오는 LevelPlay didRewardAd 를 기다리는 시간.
    private static let rewardGrace: TimeInterval = 1.5

    /// 광고를 시도라도 해볼 수 있는지 — 둘 중 하나라도 살아 있으면 true.
    var isConfigured: Bool { levelPlay.isConfigured || admob.isConfigured }

    /// 광고 재생 중(아이콘 연타 방지).
    @Published private(set) var showing = false

    /// 로드돼 바로 보여줄 수 있는 상태(광고원 무관).
    @Published private(set) var rewardedReady = false

    /// 진단용 한 줄(LevelPlay 실패 + 폴백 실패) — 로그/디버그용.
    var diagnostics: String {
        [levelPlay.lastError, admob.lastError.map { "폴백 " + $0 }]
            .compactMap { $0 }
            .joined(separator: " / ")
    }

    private enum Source { case levelPlay, admob }

    /// 로드를 기다리는 재생 요청(최대 1개).
    private final class PendingShow {
        let onResult: (Bool) -> Void
        let onUnavailable: () -> Void
        init(onResult: @escaping (Bool) -> Void, onUnavailable: @escaping () -> Void) {
            self.onResult = onResult
            self.onUnavailable = onUnavailable
        }
    }
    private var pending: PendingShow?

    /// 지금 재생 중인 LevelPlay 광고 1건의 결과 수집 — 보상/닫힘 순서가 뒤바뀌어도 결과는 한 번만.
    private final class ShowSession {
        let onResult: (Bool) -> Void
        var rewarded = false
        var closed = false
        var delivered = false
        init(onResult: @escaping (Bool) -> Void) { self.onResult = onResult }
    }
    private var session: ShowSession?

    private var started = false
    /// ATT 답이 난 뒤에만 SDK 를 건드린다.
    private var sdkGateOpen = false

    // MARK: - 시작

    /// 앱 시작 시 1회(StaryApp). 키가 없으면 아무 일도 하지 않는다(ATT 팝업도 없음).
    func initialize() {
        guard !started else { return }
        started = true
        guard isConfigured else {
            print("ℹ️ [Ads] 광고 키 미설정 — 광고 비활성(docs/IOS_ADS_SETUP.md 참고)")
            return
        }
        wireLevelPlay()
        Task {
            await requestTrackingIfNeeded()
            sdkGateOpen = true
            levelPlay.initSDK() // 성공 콜백이 preload 를 이어받는다(LevelPlay 키가 없으면 no-op)
            preload()
        }
    }

    /// ATT 동의 요청(첫 실행 1회) — 앱이 active 가 될 때까지 기다린 뒤, 로그인/약관 화면과 겹치지 않게 잠깐 뒤에 띄운다.
    private func requestTrackingIfNeeded() async {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
        var waited = 0
        while UIApplication.shared.applicationState != .active && waited < 40 {
            try? await Task.sleep(nanoseconds: 250_000_000)
            waited += 1
        }
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        _ = await ATTrackingManager.requestTrackingAuthorization()
    }

    private func wireLevelPlay() {
        levelPlay.onInitResult = { [weak self] ok in
            guard let self else { return }
            if ok { self.preload() } else { self.loadFallback() } // LevelPlay 가 안 서면 폴백으로
        }
        levelPlay.onLoaded = { [weak self] in
            self?.refreshReady()
            self?.resolvePending()
        }
        levelPlay.onLoadFailed = { [weak self] in
            self?.refreshReady()
            self?.loadFallback() // LevelPlay 가 못 채웠으면 폴백 — 기다리는 요청 정리는 폴백이 끝난 뒤에
        }
        levelPlay.onRewarded = { [weak self] in
            guard let s = self?.session else { return }
            s.rewarded = true
            if s.closed { self?.deliver(s) } // 닫힌 뒤에 온 보상
        }
        levelPlay.onDisplayFailed = { [weak self] in
            guard let self else { return }
            if let s = self.session {
                s.closed = true
                self.deliver(s)
            }
            self.afterShow()
        }
        levelPlay.onClosed = { [weak self] in
            guard let self else { return }
            let s = self.session
            self.afterShow()
            guard let s else { return }
            s.closed = true
            if s.rewarded {
                self.deliver(s)
            } else {
                // 늦은 보상 대기
                DispatchQueue.main.asyncAfter(deadline: .now() + Self.rewardGrace) { [weak self] in
                    self?.deliver(s)
                }
            }
        }
    }

    // MARK: - 로드

    /// 재생이 끝나면(닫힘/실패) 다음 광고를 미리 받아 둔다.
    private func afterShow() {
        showing = false
        rewardedReady = false
        preload()
    }

    /// LevelPlay 보상 결과를 호출부에 **한 번만** 넘긴다.
    private func deliver(_ s: ShowSession) {
        guard !s.delivered else { return }
        s.delivered = true
        if session === s { session = nil }
        s.onResult(s.rewarded)
    }

    /// 지금 바로 보여줄 수 있는 광고원 — LevelPlay 우선.
    private func readySource() -> Source? {
        if levelPlay.isReady { return .levelPlay }
        if admob.isReady { return .admob }
        return nil
    }

    private func refreshReady() {
        rewardedReady = readySource() != nil
    }

    /// 보상형 광고 미리 로드 — 잠긴 상세 화면에 들어온 순간 호출해 두면 탭했을 때 기다림이 없다.
    /// LevelPlay 초기화가 실패했었다면 여기서 다시 초기화부터 시도하고, LevelPlay 를 쓸 수 없으면 폴백을 데운다.
    func preload() {
        guard !showing, sdkGateOpen else { return }
        if levelPlay.isConfigured {
            if !levelPlay.initialized {
                levelPlay.initSDK()
                // 초기화가 아직 안 끝났는데 사용자가 아이콘을 눌러 기다리는 중이면 폴백도 같이 데운다.
                if !levelPlay.initialized, pending != nil { loadFallback() }
                return
            }
            if levelPlay.isReady {
                refreshReady()
                return
            }
            if levelPlay.isLoading { return }
            levelPlay.load()
            return
        }
        loadFallback()
    }

    /// 폴백(AdMob)을 한 개 받아 둔다. 결과가 나면 기다리던 재생 요청을 정리한다.
    private func loadFallback() {
        admob.load { [weak self] in
            self?.refreshReady()
            self?.resolvePending()
        }
    }

    // MARK: - 재생

    /// 탭 → 광고. 이미 로드돼 있으면 바로 재생, 아니면 로드를 걸고 최대 12초 기다려 도착하는 즉시 재생한다
    /// (그동안 [onWaiting] 1회 — "광고를 불러오는 중이에요").
    /// 키 없음 / 재생 중 / 로드 실패·시간 초과면 [onUnavailable]. 이미 기다리는 요청이 있으면 무시(연타 방지).
    /// [onResult] 는 재생했을 때 **항상 1회**: true = 끝까지 봐서 보상, false = 건너뜀/실패.
    func showRewardedWhenReady(
        onWaiting: @escaping () -> Void,
        onUnavailable: @escaping () -> Void,
        onResult: @escaping (Bool) -> Void
    ) {
        guard isConfigured, !showing else {
            onUnavailable()
            return
        }
        if let source = readySource() {
            show(source, onResult: onResult, onUnavailable: onUnavailable)
            return
        }
        guard pending == nil else { return }
        let req = PendingShow(onResult: onResult, onUnavailable: onUnavailable)
        pending = req
        onWaiting()
        preload() // 초기화 전이면 초기화부터 — 성공 콜백의 preload 가 이어받는다.
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.waitForLoad) { [weak self] in
            guard let self, self.pending === req else { return }
            self.pending = nil
            req.onUnavailable()
        }
    }

    /// 로드가 한 단계 끝날 때마다 호출 — 쓸 광고가 생겼으면 기다리던 요청을 재생한다.
    private func resolvePending() {
        guard let req = pending else { return }
        guard let source = readySource() else {
            // 아직 초기화/로드 중이면 기다린다(끝내는 건 waitForLoad 타임아웃).
            if !sdkGateOpen || levelPlay.isLoading || admob.isLoading
                || (levelPlay.isConfigured && levelPlay.initStarted && !levelPlay.initialized) {
                return
            }
            pending = nil
            req.onUnavailable()
            return
        }
        pending = nil
        show(source, onResult: req.onResult, onUnavailable: req.onUnavailable)
    }

    private func show(
        _ source: Source,
        onResult: @escaping (Bool) -> Void,
        onUnavailable: @escaping () -> Void
    ) {
        guard let vc = Self.topViewController() else {
            onUnavailable()
            return
        }
        showing = true
        rewardedReady = false
        switch source {
        case .levelPlay:
            session = ShowSession(onResult: onResult)
            levelPlay.show(from: vc)
        case .admob:
            // AdMob 은 보상 → 닫힘 순서가 보장돼 결과를 그대로 넘기면 된다.
            admob.show(from: vc) { [weak self] rewarded in
                self?.afterShow()
                onResult(rewarded)
            }
        }
    }

    /// 지금 화면 맨 위의 뷰컨트롤러(시트/풀스크린커버 위에서도 광고가 뜨게 presented 체인을 끝까지 탄다).
    private static func topViewController() -> UIViewController? {
        var top = AuthManager.rootViewController()
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
