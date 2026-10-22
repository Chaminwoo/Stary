package com.chaminwoo.stary.core.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * 사용 흐름 이벤트(Firebase Analytics, 2026-10-22) — "광고로 들어온 사람이 어디서 나가는지"를 숫자로 보려고 넣었다.
 * 이벤트 이름·파라미터는 iOS `StaryAnalytics.swift` 와 **같아야** 콘솔에서 한 퍼널로 합쳐 볼 수 있다.
 *
 * 퍼널: login_view → (login_google_tap → login_success | login_fail) / guest_browse → map_view
 *      → guide_step(write) → upload_open → upload_success | upload_fail(reason)
 *      → guide_step(claim) → quest_open → quest_claim → guide_step(open) → detail_open(locked) → key_used → guide_done
 * 개인 정보(이름·이메일·글 내용·좌표)는 넣지 않는다.
 */
object StaryAnalytics {
    private var fa: FirebaseAnalytics? = null
    /** [logOnce] 로 이번 실행에 이미 보낸 키. */
    private val sent = mutableSetOf<String>()

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun log(event: String, vararg params: Pair<String, Any?>) {
        val bundle = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> {}
                is Boolean -> bundle.putLong(k, if (v) 1 else 0)
                is Int -> bundle.putLong(k, v.toLong())
                is Long -> bundle.putLong(k, v)
                else -> bundle.putString(k, v.toString().take(100))
            }
        }
        fa?.logEvent(event, bundle)
    }

    /** 이번 실행에서 [key] 로 한 번만 보낸다(화면이 다시 그려질 때마다 쌓이지 않게). */
    fun logOnce(key: String, event: String, vararg params: Pair<String, Any?>) {
        if (!sent.add(key)) return
        log(event, *params)
    }
}
