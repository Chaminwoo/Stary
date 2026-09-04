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
 * - [hapticsEnabled] : 햅틱(진동) 피드백. 끄면 [Haptics] 호출이 전부 무음이 된다.
 * - [dailyReminderEnabled] : 매일 1회(점심~밤 10시 랜덤) "오늘 기록해보세요" 알림. 끄면 시스템 알림은
 *   안 뜨지만 예약 자체는 [com.chaminwoo.stary.push.DailyReminderScheduler] 가 계속 갱신한다(다시 켜면 바로 이어짐).
 *
 * SharedPreferences("stary_prefs") 에 영속. Compose 에서 관찰 가능하도록 mutableStateOf 사용.
 */
object AppSettings {
    private const val PREFS = "stary_prefs"
    private const val KEY_NOTIF = "notifications_enabled"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_DAILY_REMINDER = "daily_reminder_enabled"

    private var appContext: Context? = null

    /** 인앱 알림 팝업 on/off (기본 켜짐). */
    var notificationsEnabled by mutableStateOf(true)
        private set

    /** 햅틱(진동) on/off (기본 켜짐). */
    var hapticsEnabled by mutableStateOf(true)
        private set

    /** 일일 알림(오늘 기록 유도) on/off (기본 켜짐). */
    var dailyReminderEnabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        notificationsEnabled = prefs.getBoolean(KEY_NOTIF, true)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        dailyReminderEnabled = prefs.getBoolean(KEY_DAILY_REMINDER, true)
        Haptics.init(ctx)
    }

    // property setter(setNotificationsEnabled) 와 JVM 시그니처 충돌을 피해 함수명을 다르게 둔다.
    fun updateNotificationsEnabled(value: Boolean) {
        if (notificationsEnabled == value) return
        notificationsEnabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_NOTIF, value)?.apply()
    }

    fun updateHapticsEnabled(value: Boolean) {
        if (hapticsEnabled == value) return
        hapticsEnabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_HAPTICS, value)?.apply()
        if (value) Haptics.light() // 켠 순간 어떤 느낌인지 바로 보여준다
    }

    fun updateDailyReminderEnabled(value: Boolean) {
        if (dailyReminderEnabled == value) return
        dailyReminderEnabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_DAILY_REMINDER, value)?.apply()
    }
}
