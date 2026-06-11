package com.chaminwoo.stary

import android.app.Application

/**
 * 네이버맵 SDK 초기화 코드는 Google Maps 전환으로 제거됨.
 *
 * Google Maps SDK 는 별도 런타임 초기화가 필요 없으며, API 키는
 * AndroidManifest 의 com.google.android.geo.API_KEY (secrets-gradle-plugin 의
 * ${MAPS_API_KEY} 플레이스홀더)로 주입한다. local.properties/secrets.properties 참고.
 */
class StaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: 필요 시 Firebase 등 초기화 추가 (google-services.json 기반 자동 초기화 사용 중)
    }
}
