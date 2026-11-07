import FirebaseCore
#if canImport(FirebaseAppCheck)
import FirebaseAppCheck
#endif

/// Firebase App Check — **모니터링 전용**(2026-11-02). 요청에 기기 증명 토큰을 실어 콘솔에서 정상 앱 비율을 본다.
/// 강제(enforce)는 켜지 않는다 — 콘솔에서 Firestore/Storage 에 강제하기 전까지 토큰이 없어도 동작은 같다.
/// DeviceCheck 공급자만 쓴다(App Attest 권한/entitlement 은 추가하지 않는다). 시뮬레이터는 증명이 안 돼 토큰 없이 동작한다.
/// ⚠️ 반드시 `FirebaseApp.configure()` **이전**에 불러야 한다.
enum StaryAppCheck {
    static func install() {
        #if canImport(FirebaseAppCheck)
        AppCheck.setAppCheckProviderFactory(StaryAppCheckProviderFactory())
        #endif
    }
}

#if canImport(FirebaseAppCheck)
private final class StaryAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        DeviceCheckProvider(app: app)
    }
}
#endif
