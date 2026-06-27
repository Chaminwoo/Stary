package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 앱 전역 사용자 설정(음악/효과음 볼륨 외의 잡다한 토글).
 *
 * - [notificationsEnabled] : 인앱 알림 팝업(채팅/다이어리 알림 배너) 표시 여부.
 *   끄면 새 알림/메시지가 와도 배너를 띄우지 않는다(미읽음 카운트·알림 목록 자체는 유지).
 *
 * SharedPreferences("stary_prefs") 에 영속. Compose 에서 관찰 가능하도록 mutableStateOf 사용.
 */
object AppSettings {
    private const val PREFS = "stary_prefs"
    private const val KEY_NOTIF = "notifications_enabled"

    private var appContext: Context? = null

    /** 인앱 알림 팝업 on/off (기본 켜짐). */
    var notificationsEnabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        notificationsEnabled = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF, true)
    }

    // property setter(setNotificationsEnabled) 와 JVM 시그니처 충돌을 피해 함수명을 다르게 둔다.
    fun updateNotificationsEnabled(value: Boolean) {
        if (notificationsEnabled == value) return
        notificationsEnabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_NOTIF, value)?.apply()
    }
}
