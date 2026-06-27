package com.chaminwoo.stary.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 인앱 언어(로케일) 전환.
 *
 * 동작: 선택한 언어 태그를 SharedPreferences 에 저장하고, [MainActivity.attachBaseContext] 에서
 * [wrap] 으로 context 의 로케일을 덮어쓴다 → 모든 리소스(res/values-xx)가 그 언어로 해석된다.
 * 언어를 바꾸면 액티비티를 recreate 해 새 로케일로 다시 그린다.
 *
 * ⚠️ 코드에 하드코딩된 한국어 문자열은 리소스(stringResource)로 옮겨야 번역이 반영된다.
 *    (현재는 설정/네비게이션 등 주요 chrome 부터 리소스화 — 나머지는 점진 이관 대상.)
 */
object LocaleManager {
    private const val PREFS = "stary_prefs"
    private const val KEY_LANG = "app_language"

    /** "" = 시스템 기본. 그 외 BCP-47 태그("ko","en","ja"). */
    const val SYSTEM = ""

    /** 설정 화면 선택지로 노출할 지원 언어(태그). */
    val SUPPORTED = listOf(SYSTEM, "ko", "en", "ja")

    fun getLanguageTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, SYSTEM) ?: SYSTEM

    fun setLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply()
    }

    /** 저장된 언어로 context 를 래핑. 시스템 기본이면 원본 그대로. */
    fun wrap(context: Context): Context {
        val tag = getLanguageTag(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
