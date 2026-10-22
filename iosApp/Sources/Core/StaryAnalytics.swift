import FirebaseAnalytics
import Foundation

/// 사용 흐름 이벤트(Firebase Analytics, 2026-10-22) — "광고로 들어온 사람이 어디서 나가는지"를 숫자로 보려고 넣었다.
/// 이벤트 이름·파라미터는 Android `StaryAnalytics.kt` 와 **같아야** 콘솔에서 한 퍼널로 합쳐 볼 수 있다.
/// (FirebaseAnalytics 모듈의 `Analytics` 클래스와 이름이 겹치지 않게 Stary 접두어.)
///
/// 퍼널: login_view → (login_google_tap/login_apple_tap → login_success | login_fail) / guest_browse → map_view
///      → guide_step(write) → upload_open → upload_success | upload_fail(reason)
///      → guide_step(claim) → quest_open → quest_claim → guide_step(open) → detail_open(locked) → key_used → guide_done
/// 개인 정보(이름·이메일·글 내용·좌표)는 넣지 않는다.
enum StaryAnalytics {
    @MainActor private static var sent = Set<String>()

    static func log(_ event: String, _ params: [String: Any?] = [:]) {
        var p: [String: Any] = [:]
        for (k, v) in params {
            switch v {
            case nil: continue
            case let b as Bool: p[k] = b ? 1 : 0
            case let i as Int: p[k] = i
            case let s as String: p[k] = String(s.prefix(100))
            case let some?: p[k] = String(String(describing: some).prefix(100))
            }
        }
        Analytics.logEvent(event, parameters: p.isEmpty ? nil : p)
    }

    /// 이번 실행에서 `key` 로 한 번만 보낸다(화면이 다시 그려질 때마다 쌓이지 않게).
    @MainActor static func logOnce(_ key: String, _ event: String, _ params: [String: Any?] = [:]) {
        guard sent.insert(key).inserted else { return }
        log(event, params)
    }
}
