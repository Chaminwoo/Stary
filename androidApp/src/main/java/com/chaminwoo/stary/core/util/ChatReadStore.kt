package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * 채팅방 읽음 시각 로컬 저장소 — 친구 목록의 "미읽음 파란 점" 판정에 사용.
 * chatId → 마지막으로 그 방을 본 시각(ms). 방 메타(updatedAt)가 이 시각보다 크고
 * 마지막 발신자가 내가 아니면 미읽음.
 *
 * Compose 상태 맵을 겸해 [markRead] 호출 시 관찰 중인 화면(친구 목록)이 즉시 갱신된다.
 * 서버 스키마 변경 없는 기기 로컬 기준(다른 기기에서 읽어도 이 기기엔 미읽음 유지 — 허용 범위).
 */
object ChatReadStore {
    private const val PREFS = "chat_read_store"
    private val state = mutableStateMapOf<String, Long>()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).all.forEach { (k, v) -> (v as? Long)?.let { state[k] = it } }
    }

    /** 그 방을 마지막으로 본 시각(없으면 0). Compose 에서 읽으면 markRead 시 리컴포즈된다. */
    fun lastReadAt(context: Context, chatId: String): Long {
        ensureLoaded(context)
        return state[chatId] ?: 0L
    }

    /** 방을 열람했음을 기록(기본 = 지금). */
    fun markRead(context: Context, chatId: String, at: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        state[chatId] = at
        prefs(context).edit().putLong(chatId, at).apply()
    }

    fun isUnread(
        context: Context,
        chatId: String,
        lastMessageTime: Long
    ): Boolean {
        return lastMessageTime > lastReadAt(context, chatId)
    }
}
