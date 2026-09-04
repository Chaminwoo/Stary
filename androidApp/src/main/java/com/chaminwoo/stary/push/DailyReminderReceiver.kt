package com.chaminwoo.stary.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.MainActivity
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.AppSettings
import java.util.Calendar

/**
 * [DailyReminderScheduler] 가 예약한 알람이 울리는 지점.
 *
 * 알림을 띄운 뒤(꺼져 있으면 생략) **항상** 다음 날짜의 랜덤 시각을 다시 예약한다 —
 * 그래야 매일 계속된다(한 번 울리고 끝나는 `setAndAllowWhileIdle` 특성상 재예약은 필수).
 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 별도 프로세스로 깨어날 수 있어(앱이 완전히 종료된 상태) 저장된 설정값을 다시 읽어온다.
        AppSettings.init(context)
        if (AppSettings.dailyReminderEnabled) {
            showNotification(context)
        }
        DailyReminderScheduler.scheduleNext(context)
    }

    private fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureStaryNotificationChannel(context)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val body = context.getString(
            when (hour) {
                in 12..14 -> R.string.daily_reminder_lunch
                in 15..17 -> R.string.daily_reminder_afternoon
                in 18..19 -> R.string.daily_reminder_dinner
                else -> R.string.daily_reminder_night
            }
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_UPLOAD, true)
        }
        val pending = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STARY_DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val REQUEST_CODE = 3002
        private const val NOTIFICATION_ID = 3003
    }
}
