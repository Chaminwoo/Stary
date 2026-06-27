package com.chaminwoo.stary.core.util

/**
 * 앱이 현재 전면(foreground)인지 추적. [StaryApplication] 이 ActivityLifecycleCallbacks 로 갱신한다.
 *
 * 용도: 팝업 알림 이중 표시 방지.
 *  - 전면일 때: 인앱 배너(Compose 오버레이)로 표시 → FCM 시스템 알림은 띄우지 않는다.
 *  - 후면/종료일 때: FCM 시스템 알림(상단 heads-up)으로 표시 → 인앱 배너는 동작하지 않는다.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
        private set

    private var resumedCount = 0

    @Synchronized
    fun onResumed() {
        resumedCount++
        isForeground = true
    }

    @Synchronized
    fun onPaused() {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
        isForeground = resumedCount > 0
    }
}
