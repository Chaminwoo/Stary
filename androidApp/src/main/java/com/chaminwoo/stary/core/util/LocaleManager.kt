package com.chaminwoo.stary.core.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 인앱 언어(로케일) 전환.
 *
 * **버전별로 "진짜 소스"가 다르다** — 둘 다 직접 다룬다.
 *  - **API 33+**: 시스템 `android.app.LocaleManager.applicationLocales`(공식 앱별 언어 API).
 *    시스템이 값을 영속 저장하고 리소스 해석·액티비티 재구성까지 직접 해준다 →
 *    이 구간에서는 [wrap] 이 아무것도 하지 않는다(시스템 값과 이중 적용 금지).
 *  - **API 26~32**: 시스템에 앱별 언어 기능이 없다 → 자체 prefs 저장 + [wrap] 으로
 *    `MainActivity.attachBaseContext` 에서 Configuration 을 덮어써 리소스를 해석시킨다.
 *
 * ### 왜 AppCompatDelegate 를 안 쓰나 (2026-09-04)
 * `AppCompatDelegate.setApplicationLocales()` 는 **등록된 AppCompat 델리게이트(=AppCompatActivity)**
 * 를 훑어서 시스템 서비스를 얻을 Context 를 찾는다. 이 앱의 `MainActivity` 는 순수 `ComponentActivity`
 * 라 그 목록이 **비어 있어서** 호출이 조용히 무시됐다(설정에서 골라도 계속 "시스템 기본"으로 표시됨).
 * `MainActivity` 를 `AppCompatActivity` 로 바꾸는 방법도 있지만 테마(Theme.AppCompat 계열 강제) 등
 * 파급이 커서, 각 버전의 실제 메커니즘을 직접 호출하는 쪽으로 정리했다.
 *
 * ⚠️ 그 이전(~2026-09-03)엔 API 33+ 에서도 수동 wrap 만 썼는데, 시스템이 추적하는 앱별 로케일이
 *    우선 적용되면서 전환이 무시됐다 — 지금은 33+ 에서 **시스템 값 자체를 바꾸므로** 그 충돌이 없다.
 *
 * ⚠️ 코드에 하드코딩된 한국어 문자열은 리소스(stringResource)로 옮겨야 번역이 반영된다.
 */
object LocaleManager {
    private const val PREFS = "stary_prefs"
    private const val KEY_LANG = "app_language"

    /** "" = 시스템 기본. 그 외 BCP-47 태그("ko","en","ja"). */
    const val SYSTEM = ""

    /** 설정 화면 선택지로 노출할 지원 언어(태그). */
    val SUPPORTED = listOf(SYSTEM, "ko", "en", "ja")

    /** 현재 적용된 언어 태그("" = 시스템 기본 따르는 중). */
    fun getLanguageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags = systemLocaleManager(context)?.applicationLocales?.toLanguageTags().orEmpty()
            return if (tags.isBlank()) SYSTEM else tags.substringBefore(',')
        }
        return storedTag(context)
    }

    /** 언어 변경 — 33+ 는 시스템에, 그 아래는 prefs 에 기록한다(둘 다 써 두면 읽는 쪽이 단순해진다). */
    fun setLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = systemLocaleManager(context) ?: return
            manager.applicationLocales =
                if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    /**
     * 저장된 언어로 context 를 래핑 — `MainActivity.attachBaseContext` 가 호출한다.
     * **API 33+ 에서는 시스템이 직접 적용하므로 원본을 그대로 돌려준다**(이중 적용 방지).
     */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = storedTag(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, SYSTEM) ?: SYSTEM

    /** ⚠️ 이 object 와 이름이 같으므로 시스템 클래스는 반드시 전체 이름으로 참조한다. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun systemLocaleManager(context: Context): android.app.LocaleManager? =
        context.getSystemService(android.app.LocaleManager::class.java)
}
