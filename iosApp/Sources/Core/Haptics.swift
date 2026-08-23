import UIKit

/// 앱 전역 햅틱(진동) — Android `core/util/Haptics.kt` 패리티.
///
/// 설계 의도: 별 탭·열람 파장·좋아요·다이얼 눈금·업적 달성처럼 **이미 시각/청각 연출이 있는 지점에만**
/// 짧게 준다. 아무 버튼에나 넣으면 진동이 배경소음이 되어 오히려 싸구려로 느껴진다.
///
/// - `AppSettings.shared.hapticsEnabled` 가 꺼져 있으면 전부 무음(설정 > 사운드 토글).
/// - 제너레이터는 재사용(매번 만들면 첫 진동이 늦게 온다). 호출은 항상 메인 스레드에서.
@MainActor
enum Haptics {

    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let mediumGen = UIImpactFeedbackGenerator(style: .medium)
    private static let heavyGen = UIImpactFeedbackGenerator(style: .heavy)
    private static let selection = UISelectionFeedbackGenerator()
    private static let notify = UINotificationFeedbackGenerator()

    private static var enabled: Bool { AppSettings.shared.hapticsEnabled }

    /// 곧 진동이 필요할 때 미리 예열(드래그 시작 등) — 첫 tick 지연을 없앤다.
    static func prepare() {
        guard enabled else { return }
        selection.prepare()
        mediumGen.prepare()
    }

    /// 눈금/스크롤 스냅 — 가장 약한 한 점(다이얼, 휠 피커).
    static func tick() {
        guard enabled else { return }
        selection.selectionChanged()
    }

    /// 가벼운 확인 — 토글/칩 선택.
    static func soft() {
        guard enabled else { return }
        light.impactOccurred(intensity: 0.6)
    }

    /// 보통 — 좋아요, 친구 수락처럼 "일이 일어난" 순간.
    static func medium() {
        guard enabled else { return }
        mediumGen.impactOccurred()
    }

    /// 묵직한 한 방 — 별 열람 파장이 터지는 순간.
    static func heavy() {
        guard enabled else { return }
        heavyGen.impactOccurred()
    }

    /// 보상 패턴 — 업적 달성/별 탄생처럼 축하하는 순간.
    static func celebrate() {
        guard enabled else { return }
        notify.notificationOccurred(.success)
    }

    /// 파장(warp) 전용 — 묵직한 한 방 뒤 파문이 번지듯 잦아든다(Android createWaveform 대응).
    static func warp() {
        guard enabled else { return }
        heavyGen.impactOccurred(intensity: 1.0)
        Task {
            try? await Task.sleep(nanoseconds: 90_000_000)
            guard enabled else { return }
            mediumGen.impactOccurred(intensity: 0.55)
            try? await Task.sleep(nanoseconds: 100_000_000)
            guard enabled else { return }
            light.impactOccurred(intensity: 0.35)
        }
    }
}
