import Foundation
import SwiftUI

/// 보상형 광고(Unity LevelPlay) iOS 래퍼 — Android `core.ads.AdsManager` 의 자리.
///
/// ⚠️ **아직 SDK 가 붙지 않았다.** 사용자 결정에 따라 Android 광고를 먼저 완성하고, iOS 는 다음 라운드에서 붙인다.
/// 그때까지 [isConfigured] 가 false 라 잠금 화면의 재생 아이콘을 탭하면 "지금은 광고를 불러올 수 없어요" 안내만 뜬다
/// (`DetailScreen.watchAdToUnlock`).
///
/// 2026-09-22 방향 전환: Unity Ads SDK 직접 연동은 2026-01-31 로 수익화 지원 종료 + 새 광고 단위는 헤더 비딩 전용이라
/// 직접 `UnityAds.load` 는 `adMarkup is missing` 으로 항상 실패(Android 실기기 로그). → iOS 도 **LevelPlay** 로 붙인다.
///
/// 붙일 때 할 일(Android `AdsManager.kt` 와 같은 계약 유지):
///  1. `project.yml` 에 LevelPlay iOS SDK + Unity Ads 어댑터 추가(SPM/CocoaPods — LevelPlay iOS 가이드 기준 버전)
///  2. 빌드설정 → Info.plist 로 `LEVELPLAY_APP_KEY` / `LEVELPLAY_REWARDED_AD_UNIT` 주입
///     (LevelPlay 에 **iOS 앱을 따로 추가**해서 받은 값 — Android 값과 다르다)
///  3. `initialize` = LevelPlay 초기화(성공 후에만 보상형 광고 객체 생성) / `preload` = loadAd / `showRewarded`
///  4. 보상 판정은 onAdRewarded 뿐 — 닫힘보다 늦게 올 수 있으니 닫힌 뒤 1.5초 기다렸다 결과 1회(Android 동일)
///  5. 탭 시 아직 로드 전이면 "광고를 불러오는 중이에요" 후 최대 8초 기다렸다 재생(Android `showRewardedWhenReady`)
///  6. iOS 는 ATT(앱 추적 투명성) 동의 팝업 + Info.plist `NSUserTrackingUsageDescription`, SKAdNetwork ID 목록 필요
///  7. **AdMob 폴백도 같이**(2026-09-23 Android 추가분): Android 는 LevelPlay 가 `509 Mediation No fill` 일 때
///     Google AdMob 보상형으로 backfill 한다(`AdMobRewarded.kt`). iOS 도 `GoogleMobileAds` + Info.plist
///     `GADApplicationIdentifier` + `ADMOB_REWARDED_AD_UNIT` 주입으로 같은 2단 구성을 만든다.
///     값이 없으면 디버그만 구글 공식 테스트 단위(`ca-app-pub-3940256099942544/1712485313` — iOS 보상형), 릴리즈는 폴백 off.
@MainActor
final class AdsManager: ObservableObject {
    static let shared = AdsManager()
    private init() {}

    /// LevelPlay iOS 앱 키(Info.plist 주입). SDK 연결 전에는 쓰이지 않는다.
    var appKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "LEVELPLAY_APP_KEY") as? String) ?? ""
    }

    /// LevelPlay 보상형 광고 단위 ID(Info.plist 주입).
    var rewardedAdUnitId: String {
        (Bundle.main.object(forInfoDictionaryKey: "LEVELPLAY_REWARDED_AD_UNIT") as? String) ?? ""
    }

    /// 광고를 띄울 수 있는지 — SDK 미연결이라 현재는 항상 false.
    var isConfigured: Bool { false }

    /// 광고 재생 중(아이콘 연타 방지).
    @Published private(set) var showing = false

    /// 앱 시작 시 1회. SDK 연결 전에는 아무 일도 하지 않는다.
    func initialize() {
        // TODO(iOS 광고 라운드): LevelPlay 초기화(appKey) → 성공 시 보상형 광고 객체(rewardedAdUnitId) 생성
    }

    /// 잠긴 상세 화면 진입 시 미리 로드.
    func preload() {
        // TODO(iOS 광고 라운드): 보상형 광고 loadAd()
    }

    /// 보상형 광고 재생. [completion] 은 항상 1회 — true = 끝까지 봄(보상), false = 건너뜀/실패.
    func showRewarded(completion: @escaping (Bool) -> Void) {
        // TODO(iOS 광고 라운드): 준비됐으면 showAd(viewController), 아니면 로드 대기(최대 8초)
        completion(false)
    }
}
