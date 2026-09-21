import Foundation
import SwiftUI

/// Unity Ads(보상형 광고) iOS 래퍼 — Android `core.ads.UnityAdsManager` 의 자리.
///
/// ⚠️ **아직 SDK 가 붙지 않았다.** 사용자 결정에 따라 Android 광고를 먼저 완성하고,
/// iOS 는 다음 라운드에서 UnityAds SPM 패키지(`unity-ads-ios`)를 `project.yml` 에 추가하고
/// 아래 TODO 자리를 채운다. 그때까지 [isConfigured] 가 false 라
/// 잠금 화면은 광고 버튼을 아예 띄우지 않고 "100m 접근" 안내만 보여준다(빈 버튼 방지).
///
/// 붙일 때 할 일(Android 와 같은 계약 유지):
///  1. `project.yml` packages 에 UnityAds 추가 + 타깃 dependencies 연결
///  2. `Info.plist` 빌드설정으로 `UNITY_GAME_ID` / `UNITY_REWARDED_PLACEMENT` 주입
///     (Android `secrets.properties` 와 **같은 Unity 프로젝트의 iOS 게임 ID** — 값은 서로 다르다)
///  3. 아래 `initialize` / `preload` / `showRewarded` 구현
///  4. 보상 판정은 `UnityAdsShowCompletionState.COMPLETED` 뿐(건너뛰기는 보상 없음)
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

    /// 광고 UI 를 띄워도 되는지 — SDK 미연결이라 현재는 항상 false.
    var isConfigured: Bool { false }

    /// 광고 재생 중(버튼 연타 방지).
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
