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
import com.chaminwoo.stary.core.util.LocaleManager

/**
 * [DailyReminderScheduler] 가 예약한 알람이 울리는 지점.
 *
 * 알림을 띄운 뒤(꺼져 있으면 생략) **항상** 다음 날짜를 다시 예약한다 —
 * 그래야 매일 계속된다(한 번 울리고 끝나는 `setAndAllowWhileIdle` 특성상 재예약은 필수).
 * ⚠️ 순서 주의: 문구를 고를 때 쓰는 "예약된 밴드"를 [DailyReminderScheduler.scheduleNext] 가 덮어쓰므로
 * **알림을 먼저 띄우고 그다음에 재예약**한다.
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

    private fun showNotification(rawContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(rawContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // 알림 문구도 인앱 언어를 따르게 한다 — API 32 이하에서는 리시버 context 가 래핑되지 않아
        // 시스템 언어로 나온다(33+ 는 시스템이 앱 로케일을 이미 적용해 두므로 wrap 이 무동작).
        val context = LocaleManager.wrap(rawContext)

        ensureStaryNotificationChannel(context)

        // 문구는 **예약할 때 정해 둔 시간대**의 풀에서 고른다(직전에 쓴 문장은 제외) —
        // 알람이 Doze 로 밀려 늦게 울려도 의도한 시간대의 문장이 나간다.
        val band = DailyReminderScheduler.scheduledBand(context)
        val body = DailyReminderScheduler.pickMessage(context, band)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_UPLOAD, true)
        }
        val pending = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STARY_DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
