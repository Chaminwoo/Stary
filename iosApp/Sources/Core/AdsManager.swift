import Foundation
import SwiftUI

/// Unity Ads(보상형 광고) iOS 래퍼 — Android `core.ads.UnityAdsManager` 의 자리.
///
/// ⚠️ **아직 SDK 가 붙지 않았다.** 사용자 결정에 따라 Android 광고를 먼저 완성하고,
/// iOS 는 다음 라운드에서 UnityAds SPM 패키지(`unity-ads-ios`)를 `project.yml` 에 추가하고
/// 아래 TODO 자리를 채운다. 그때까지 [isConfigured] 가 false 라
/// 잠금 화면의 크리스탈 자물쇠/재생 아이콘을 탭하면 "지금은 광고를 불러올 수 없어요" 안내만 뜬다
/// (`DetailScreen.watchAdToUnlock`).
///
/// 붙일 때 할 일(Android 와 같은 계약 유지):
///  1. `project.yml` packages 에 UnityAds 추가 + 타깃 dependencies 연결
///  2. `Info.plist` 빌드설정으로 `UNITY_GAME_ID` / `UNITY_REWARDED_PLACEMENT` 주입
///     (Android `secrets.properties` 와 **같은 Unity 프로젝트의 iOS 게임 ID** — 값은 서로 다르다)
///  3. 아래 `initialize` / `preload` / `showRewarded` 구현
///  4. 보상 판정은 `UnityAdsShowCompletionState.COMPLETED` 뿐(건너뛰기는 보상 없음)
///  5. 탭 시 아직 로드 전이면 "광고를 불러오는 중이에요" 후 최대 8초 기다렸다 재생(Android `showRewardedWhenReady`)
///  ⚠️ Placement 는 **비딩이 아닌(waterfall) 보상형**이어야 한다 — LevelPlay 용 헤더 비딩 Placement(`BP_…`)는
///     SDK 직접 로드 시 `adMarkup is missing` 으로 항상 실패한다(2026-09-22 Android 실기기 로그로 확인).
@MainActor
final class AdsManager: ObservableObject {
    static let shared = AdsManager()
    private init() {}

    /// Info.plist 에 게임 ID 가 주입됐는지. SDK 연결 전에는 항상 false.
    var gameId: String {
        (Bundle.main.object(forInfoDictionaryKey: "UNITY_GAME_ID") as? String) ?? ""
    }

    /// 보상형 배치 id(Unity Dashboard 의 Placement ID 와 철자까지 같아야 한다).
    var rewardedPlacementId: String {
        let v = (Bundle.main.object(forInfoDictionaryKey: "UNITY_REWARDED_PLACEMENT") as? String) ?? ""
        return v.isEmpty ? "Rewarded_iOS" : v
    }

    /// 광고를 띄울 수 있는지 — SDK 미연결이라 현재는 항상 false.
    var isConfigured: Bool { false }

    /// 광고 재생 중(아이콘 연타 방지).
    @Published private(set) var showing = false

    /// 앱 시작 시 1회. SDK 연결 전에는 아무 일도 하지 않는다.
    func initialize() {
        // TODO(iOS 광고 라운드): UnityAds.initialize(gameId, testMode:, initializationDelegate:)
    }

    /// 잠긴 상세 화면 진입 시 미리 로드.
    func preload() {
        // TODO(iOS 광고 라운드): UnityAds.load(rewardedPlacementId, loadDelegate:)
    }

    /// 보상형 광고 재생. [completion] 은 항상 1회 — true = 끝까지 봄(보상), false = 건너뜀/실패.
    func showRewarded(completion: @escaping (Bool) -> Void) {
        // TODO(iOS 광고 라운드): UnityAds.show(viewController, placementId:, options:, showDelegate:)
        completion(false)
    }
}
