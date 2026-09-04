package com.chaminwoo.stary.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 재부팅(또는 앱 업데이트) 시 시스템이 예약된 [android.app.AlarmManager] 알람을 모두 지우므로,
 * 여기서 [DailyReminderScheduler] 예약을 복구한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                DailyReminderScheduler.ensureScheduled(context)
        }
    }
}
