package com.chaminwoo.stary.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Stary 알림 채널 id — 서버(Cloud Functions)의 `android.notification.channelId` 와 반드시 일치. */
const val STARY_CHANNEL_ID = "stary_default"

/**
 * heads-up(IMPORTANCE_HIGH) 알림 채널을 보장한다.
 *
 * ⚠️ **앱 시작 시 미리 만들어 둔다.** 서버가 notification 페이로드를 보내면 앱이 백그라운드/종료 상태여도
 * 시스템(Play services)이 직접 알림을 표시하는데, 이때 채널이 없으면(안드 O+) 상단 heads-up 이 안 뜬다.
 * 채널은 한 번 만들면 영속되므로 앱을 한 번이라도 켠 뒤엔 종료 상태 알림도 상단 배너로 뜬다.
 * (minSdk 26 이라 버전 가드 불필요.)
 */
fun ensureStaryNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(STARY_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(STARY_CHANNEL_ID, "Stary 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "채팅·다이어리 알림"
            }
        )
    }
}
