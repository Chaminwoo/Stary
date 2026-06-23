package com.chaminwoo.stary.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사람이 읽기 좋은 상대 시간 표기.
 * 최근(1주 이내)은 "방금 전 / N분 전 / N시간 전 / N일 전",
 * 그보다 오래되면 절대 날짜(yyyy.MM.dd)로 폴백한다.
 */
object RelativeTime {
    fun format(createdAt: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - createdAt
        if (diff < 0) return "방금 전" // 시계 차이 등으로 미래값이면 방금 전으로
        val sec = diff / 1000
        val min = sec / 60
        val hour = min / 60
        val day = hour / 24
        return when {
            sec < 60 -> "방금 전"
            min < 60 -> "${min}분 전"
            hour < 24 -> "${hour}시간 전"
            day < 7 -> "${day}일 전"
            else -> SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(createdAt))
        }
    }
}
