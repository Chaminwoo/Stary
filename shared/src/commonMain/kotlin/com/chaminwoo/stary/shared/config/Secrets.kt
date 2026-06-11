package com.chaminwoo.stary.shared.config

/**
 * 플랫폼별로 주입되는 민감 설정값의 공용 계약.
 *
 * commonMain 에는 "값"을 두지 않고 "어떤 값이 필요한지"만 선언한다.
 * 실제 값은 각 플랫폼(actual / DI)에서 BuildConfig·Info.plist 등 안전한 경로로 주입한다.
 *
 * TODO(android): androidApp 에서 BuildConfig / manifest placeholder 기반 구현 제공
 * TODO(ios): iOS 앱에서 Info.plist / xcconfig 기반 구현 제공
 */
interface Secrets {
    /** Google Maps SDK API Key. 절대 하드코딩 금지. */
    val googleMapsApiKey: String

    /** Google 로그인 Web Client ID (OAuth). 절대 하드코딩 금지. */
    val googleWebClientId: String
}
