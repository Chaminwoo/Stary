package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 인앱 언어(로케일) 전환 — AndroidX **per-app language API**(`AppCompatDelegate.setApplicationLocales`) 사용.
 *
 * ⚠️ 예전엔 `MainActivity.attachBaseContext` 에서 Configuration 을 수동으로 래핑하는 방식이었는데,
 *    Android 13+(API 33+) 의 시스템 "앱별 언어" 기능과 충돌해 **영어로 바꿔도 계속 한국어(시스템 로케일)로
 *    뜨는 버그**가 있었다(2026-09-03) — 이 앱은 `values-en`/`values-ja` 를 갖고 있어 API 33+ 에서
 *    자동으로 앱별 언어 후보가 되는데, 시스템이 관리하는 로케일(기본값=미설정→시스템 따라감)이
 *    수동 wrap 보다 우선 적용되면서 전환이 무시됐다.
 * - `res/xml/locales_config.xml` + manifest `android:localeConfig` 로 지원 언어를 명시(시스템 API 33+ 가 직접 사용).
 * - API 26~32는 `AppLocalesMetadataHolderService`(manifest, `autoStoreLocales=true`) 가 AppCompat 저장소에
 *   자동 저장/적용 — 별도 SharedPreferences 관리가 필요 없다(재실행해도 유지).
 * - 언어를 바꾸면 시스템이 알아서 액티비티를 재구성한다(수동 `recreate()` 불필요하지만, 화면단
 *   상태 초기화를 확실히 하려면 호출측에서 recreate 해도 무해하다).
 *
 * ⚠️ 코드에 하드코딩된 한국어 문자열은 리소스(stringResource)로 옮겨야 번역이 반영된다.
 */
object LocaleManager {
    /** "" = 시스템 기본. 그 외 BCP-47 태그("ko","en","ja"). */
    const val SYSTEM = ""

    /** 설정 화면 선택지로 노출할 지원 언어(태그). */
    val SUPPORTED = listOf(SYSTEM, "ko", "en", "ja")

    /** 현재 적용된 언어 태그("" = 시스템 기본 따르는 중). [context] 는 호출부 시그니처 호환용(미사용). */
    fun getLanguageTag(context: Context): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return if (tag.isBlank()) SYSTEM else tag.substringBefore(',')
    }

    /** 언어 변경 — 시스템(API 33+) 또는 AppCompat 저장소(API<33)에 즉시 반영된다. */
    fun setLanguageTag(context: Context, tag: String) {
        val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
