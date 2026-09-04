package com.chaminwoo.stary.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Stary 알림 채널 id — 서버(Cloud Functions)의 `android.notification.channelId` 와 반드시 일치. */
const val STARY_CHANNEL_ID = "stary_default"

/** 매일 1회 "오늘 기록해보세요" 알림 전용 채널 — 사교(좋아요/댓글/채팅) 알림과 분리해
 *  사용자가 시스템 설정에서 이것만 따로 끌 수 있게 한다. heads-up 이 아닌 기본 중요도로 조용히. */
const val STARY_DAILY_REMINDER_CHANNEL_ID = "stary_daily_reminder"

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
    if (manager.getNotificationChannel(STARY_DAILY_REMINDER_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                STARY_DAILY_REMINDER_CHANNEL_ID, "Stary 일일 알림", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "매일 한 번, 오늘을 기록해보라는 알림"
            }
        )
    }
}
