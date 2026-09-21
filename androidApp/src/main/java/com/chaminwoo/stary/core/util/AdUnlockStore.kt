package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * 광고 시청으로 열어 둔 게시물(다이어리) 기록.
 *
 * 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
 * ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, ②로 푼 기록이 여기 남는다.
 *
 * - diaryId → 해제 시각(ms). [UNLOCK_TTL_MS] 동안 유효해서 앱을 껐다 켜도 다시 광고를 보지 않는다.
 * - Compose 상태 맵이라 [unlock] 즉시 화면이 갱신된다([ChatReadStore] 와 같은 패턴).
 * - **기기 로컬 저장**(서버 스키마 변경 없음) — 기기를 바꾸면 다시 봐야 한다(허용 범위).
 */
object AdUnlockStore {
    private const val PREFS = "ad_unlock_store"

    /** 광고 1회 시청으로 열리는 기간 — 7일(그 뒤 다시 잠긴다). */
    const val UNLOCK_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

    private val state = mutableStateMapOf<String, Long>()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).all.forEach { (k, v) -> (v as? Long)?.let { state[k] = it } }
    }

    /** 이 게시물이 광고로 열려 있는지(만료 포함 판정). Compose 에서 읽으면 [unlock] 시 리컴포즈. */
    fun isUnlocked(context: Context, diaryId: String): Boolean {
        ensureLoaded(context)
        val at = state[diaryId] ?: return false
        return System.currentTimeMillis() - at < UNLOCK_TTL_MS
    }

    /** 광고 시청 완료 → 이 게시물을 연다. */
    fun unlock(context: Context, diaryId: String) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        state[diaryId] = now
        prefs(context).edit().putLong(diaryId, now).apply()
    }
}
