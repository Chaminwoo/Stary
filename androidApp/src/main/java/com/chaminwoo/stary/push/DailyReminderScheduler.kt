package com.chaminwoo.stary.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar
import kotlin.random.Random

/**
 * 매일 1회 "오늘 기록해보세요" 알림 — 시간은 매일 **랜덤**(점심 12시 ~ 밤 10시 사이), 아침엔 안 온다.
 *
 * 정밀한 시각이 중요한 알림이 아니라서 `setAndAllowWhileIdle`(비정밀, Doze 중에도 깨움)을 쓴다 —
 * `setExact*`/`setAlarmClock` 과 달리 Android 12+ 의 `SCHEDULE_EXACT_ALARM` 특수 권한이 필요 없다.
 *
 * 흐름: 예약 시각을 SharedPreferences 에 같이 저장해 두고, [ensureScheduled] 가 그 시각이
 * 이미 지났으면(알림이 울렸거나, 기기가 꺼져 있어 못 울렸거나) 다음 예약을 다시 잡는다.
 * [DailyReminderReceiver] 가 알림을 띄운 직후에도 [scheduleNext] 를 불러 항상 다음 날짜가 예약돼 있게 한다.
 * `BOOT_COMPLETED`(재부팅 시 알람 전체 소실) 대비는 [BootReceiver] 가 [ensureScheduled] 로 복구한다.
 */
object DailyReminderScheduler {
    private const val PREFS = "stary_prefs"
    private const val KEY_SCHEDULED_AT = "daily_reminder_scheduled_at"
    private const val REQUEST_CODE = 3001

    /** 알림이 울릴 수 있는 창 — 점심부터 밤 10시까지(아침 제외). */
    private const val WINDOW_START_HOUR = 12
    private const val WINDOW_END_HOUR = 22

    /** 이미 미래에 예약돼 있으면 그대로 둔다(중복 예약 방지) — 앱 시작 시 항상 호출해도 안전. */
    fun ensureScheduled(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scheduledAt = prefs.getLong(KEY_SCHEDULED_AT, 0L)
        if (scheduledAt > System.currentTimeMillis()) return
        scheduleNext(context)
    }

    /** 다음 랜덤 시각(오늘 창이 안 지났으면 오늘, 지났으면 내일)을 새로 예약. */
    fun scheduleNext(context: Context) {
        val target = nextRandomTrigger()
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SCHEDULED_AT, target).apply()
    }

    private fun nextRandomTrigger(): Long {
        val now = Calendar.getInstance()
        val todayTarget = randomTimeInWindow(now)
        // 오늘 창의 랜덤 시각이 이미 지났으면(또는 1분 이내로 임박) 내일 창에서 다시 뽑는다.
        if (todayTarget.timeInMillis > now.timeInMillis + 60_000L) return todayTarget.timeInMillis
        val tomorrow = now.clone() as Calendar
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        return randomTimeInWindow(tomorrow).timeInMillis
    }

    private fun randomTimeInWindow(base: Calendar): Calendar {
        val cal = base.clone() as Calendar
        val startMinute = WINDOW_START_HOUR * 60
        val endMinute = WINDOW_END_HOUR * 60
        val minuteOfDay = Random.nextInt(startMinute, endMinute)
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        cal.set(Calendar.MINUTE, minuteOfDay % 60)
        cal.set(Calendar.SECOND, Random.nextInt(0, 60))
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
