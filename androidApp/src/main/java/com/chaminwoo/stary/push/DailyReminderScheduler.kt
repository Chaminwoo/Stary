package com.chaminwoo.stary.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import com.chaminwoo.stary.R
import java.util.Calendar
import kotlin.random.Random

/**
 * 매일 1회 "오늘 기록해보세요" 알림 — **매번 다른 시간대, 매번 다른 문장**. 아침엔 오지 않는다.
 *
 * 왜 이렇게(2026-09-23 개편): 기존엔 12~22시 사이 균일 랜덤 + 시간대별 **고정 문장 1개**라,
 * 며칠만 써 보면 "또 그 문장"이 되어 알림이 읽히지 않았다. 두 축을 같이 흔든다 —
 * 1. **시간대(밴드)**: [Band] 5구간 중 **직전에 쓴 밴드를 빼고** 고른 뒤 그 안에서 분 단위 랜덤.
 *    → 어제 저녁에 왔으면 오늘은 저녁이 아니다. 같은 시간대가 이틀 연속 오지 않는다.
 * 2. **문장**: 밴드마다 여러 개(`reminder_messages.xml`(values / values-en / values-ja)) 중 **직전에 쓴 문장을 빼고** 랜덤.
 *
 * 정밀한 시각이 중요한 알림이 아니라서 `setAndAllowWhileIdle`(비정밀, Doze 중에도 깨움)을 쓴다 —
 * `setExact*`/`setAlarmClock` 과 달리 Android 12+ 의 `SCHEDULE_EXACT_ALARM` 특수 권한이 필요 없다.
 *
 * 흐름: 예약 시각과 **고른 밴드**를 SharedPreferences 에 같이 저장한다. [ensureScheduled] 가 그 시각이
 * 이미 지났으면(알림이 울렸거나, 기기가 꺼져 있어 못 울렸거나) 다음 예약을 다시 잡는다.
 * [DailyReminderReceiver] 는 저장된 밴드로 문구를 고르고(알람이 밀려 늦게 울려도 의도한 시간대 문구가 나간다),
 * 알림을 띄운 직후 [scheduleNext] 를 불러 항상 다음 날짜가 예약돼 있게 한다.
 * `BOOT_COMPLETED`(재부팅 시 알람 전체 소실) 대비는 [BootReceiver] 가 [ensureScheduled] 로 복구한다.
 */
object DailyReminderScheduler {
    private const val PREFS = "stary_prefs"
    private const val KEY_SCHEDULED_AT = "daily_reminder_scheduled_at"
    private const val KEY_LAST_BAND = "daily_reminder_last_band"
    private const val KEY_LAST_MSG_PREFIX = "daily_reminder_last_msg_"
    private const val REQUEST_CODE = 3001

    /**
     * 알림이 울릴 수 있는 시간대 — 낮 11시 ~ 밤 11시 반을 **성격이 다른 5구간**으로 나눴다.
     * 구간마다 어울리는 문장 풀이 따로 있다(`messages`). 아침(~11시)은 일부러 비워 둔다.
     * ⚠️ iOS `DailyReminderMessages.Band` 와 **같은 경계·같은 순서**를 유지할 것.
     */
    enum class Band(val startMinute: Int, val endMinute: Int, @ArrayRes val messages: Int) {
        /** 점심 무렵 11:00~13:30 */
        NOON(11 * 60, 13 * 60 + 30, R.array.daily_reminder_noon),
        /** 오후 13:30~17:00 */
        AFTERNOON(13 * 60 + 30, 17 * 60, R.array.daily_reminder_afternoon),
        /** 저녁·퇴근길 17:00~19:30 */
        EVENING(17 * 60, 19 * 60 + 30, R.array.daily_reminder_evening),
        /** 밤 19:30~22:00 */
        NIGHT(19 * 60 + 30, 22 * 60, R.array.daily_reminder_night),
        /** 자기 전 22:00~23:30 */
        LATE(22 * 60, 23 * 60 + 30, R.array.daily_reminder_late),
    }

    /** 이미 미래에 예약돼 있으면 그대로 둔다(중복 예약 방지) — 앱 시작 시 항상 호출해도 안전. */
    fun ensureScheduled(context: Context) {
        val prefs = prefs(context)
        val scheduledAt = prefs.getLong(KEY_SCHEDULED_AT, 0L)
        if (scheduledAt > System.currentTimeMillis()) return
        scheduleNext(context)
    }

    /** 다음 알림을 새로 예약 — 직전과 다른 밴드를 골라 그 안의 랜덤 시각으로. */
    fun scheduleNext(context: Context) {
        val prefs = prefs(context)
        val band = pickBand(prefs)
        val target = nextTrigger(band)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending) }
        prefs.edit()
            .putLong(KEY_SCHEDULED_AT, target)
            .putString(KEY_LAST_BAND, band.name)
            .apply()
    }

    /** 이번에 울리는 알림이 어느 시간대로 예약됐는지 — 문구 풀을 고르는 기준([DailyReminderReceiver]). */
    fun scheduledBand(context: Context): Band {
        val saved = prefs(context).getString(KEY_LAST_BAND, null)
        return Band.entries.firstOrNull { it.name == saved } ?: Band.NIGHT
    }

    /**
     * 밴드의 문구 풀에서 하나 고른다 — **직전에 쓴 문장은 빼고**(풀이 2개 이상일 때).
     * 고른 위치를 저장해 다음 번에 제외한다.
     */
    fun pickMessage(context: Context, band: Band): String {
        val pool = context.resources.getStringArray(band.messages)
        if (pool.isEmpty()) return ""
        val prefs = prefs(context)
        val key = KEY_LAST_MSG_PREFIX + band.name
        val last = prefs.getInt(key, -1)
        val choices = pool.indices.filter { it != last }.ifEmpty { pool.indices.toList() }
        val picked = choices[Random.nextInt(choices.size)]
        prefs.edit().putInt(key, picked).apply()
        return pool[picked]
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 직전에 쓴 밴드를 빼고 고른다 — 같은 시간대가 이틀 연속 오지 않게. */
    private fun pickBand(prefs: SharedPreferences): Band {
        val last = prefs.getString(KEY_LAST_BAND, null)
        val choices = Band.entries.filter { it.name != last }.ifEmpty { Band.entries.toList() }
        return choices[Random.nextInt(choices.size)]
    }

    /** 오늘 그 밴드가 아직 안 지났으면 오늘, 지났으면 내일 같은 밴드에서 뽑는다. */
    private fun nextTrigger(band: Band): Long {
        val now = Calendar.getInstance()
        val today = randomTimeIn(band, now)
        // 1분 이내로 임박한 경우도 내일로 — 예약 직후 바로 울리는 걸 막는다.
        if (today.timeInMillis > now.timeInMillis + 60_000L) return today.timeInMillis
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return randomTimeIn(band, tomorrow).timeInMillis
    }

    private fun randomTimeIn(band: Band, base: Calendar): Calendar {
        val cal = base.clone() as Calendar
        val minuteOfDay = Random.nextInt(band.startMinute, band.endMinute)
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        cal.set(Calendar.MINUTE, minuteOfDay % 60)
        cal.set(Calendar.SECOND, Random.nextInt(0, 60))
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
