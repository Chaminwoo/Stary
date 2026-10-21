import Foundation

/// App Store 스크린샷 자동 촬영 모드 — CI(`.github/workflows/ios.yml` 의 screenshots 잡)가
/// 시뮬레이터에서 `-StaryShot <장면>` 실행 인자를 줄 때만 켜진다. 일반 실행·스토어 설치본에는 영향 없음.
/// (실행 인자 `-키 값` 은 UserDefaults 인자 도메인으로 들어오므로 그대로 읽는다 — `-app_language en` 처럼 언어도 같은 방식.)
///
/// 장면:
///  - `login`         : 로그인 화면 그대로
///  - `map`           : 둘러보기(게스트)로 바로 진입한 지도
///  - `constellation` : 지도 + 별자리 선 켜기
///  - `globe`         : 지도 → "우주에서 보기"(3D 글로브) 자동 진입
///
/// 켜지면: 새 버전 팝업·광고(ATT 팝업)·집중 모드(10초 무입력 시 버튼 숨김)·웰컴 별을 끄고,
/// login 외 장면은 약관/로그인 화면 없이 둘러보기로 들어간다.
enum ScreenshotMode {
    static let scene: String? = UserDefaults.standard.string(forKey: "StaryShot")

    static var isActive: Bool { scene != nil }

    /// 둘러보기로 바로 들어가는 장면인가(login 만 아니면).
    static var entersAsGuest: Bool { isActive && scene != "login" }

    /// 지도 최초 자동 이동 줌(`-StaryShotZoom 12`) — 없으면 nil(앱 기본 15).
    static var mapZoom: Double? {
        guard isActive, UserDefaults.standard.object(forKey: "StaryShotZoom") != nil else { return nil }
        return UserDefaults.standard.double(forKey: "StaryShotZoom")
    }
}
