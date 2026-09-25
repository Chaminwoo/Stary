package com.chaminwoo.stary.core.util

import android.content.Context
import com.chaminwoo.stary.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사람이 읽기 좋은 상대 시간 표기.
 * 최근(1주 이내)은 "방금 전 / N분 전 / N시간 전 / N일 전"(현재 언어 리소스),
 * 그보다 오래되면 절대 날짜(yyyy.MM.dd)로 폴백한다.
 * ⚠️ [context] 는 화면(액티비티) Context 를 넘길 것 — API 32 이하에서 applicationContext 는
 *    인앱 언어 래핑이 안 돼 시스템 언어로 나온다(CLAUDE.md "언어 전환" 주의 참고).
 */
object RelativeTime {
    fun format(context: Context, createdAt: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - createdAt
        val res = context.resources
        if (diff < 0) return res.getString(R.string.time_just_now) // 시계 차이 등으로 미래값이면 방금 전으로
        val sec = diff / 1000
        val min = sec / 60
        val hour = min / 60
        val day = hour / 24
        return when {
            sec < 60 -> res.getString(R.string.time_just_now)
            min < 60 -> res.getString(R.string.time_minutes_ago, min.toInt())
            hour < 24 -> res.getString(R.string.time_hours_ago, hour.toInt())
            day < 7 -> res.getString(R.string.time_days_ago, day.toInt())
            else -> SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(Date(createdAt))
        }
    }
}
