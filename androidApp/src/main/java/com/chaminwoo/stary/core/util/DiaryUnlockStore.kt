package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * 해금한(열어 본) 다른 사람의 게시물(다이어리) 기록 — **영구**.
 *
 * 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
 * ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, **한 번 풀린 게시물은 이 기기에서 계속 열려 있다**
 * (2026-09-22 "다이어리도 아예 해금 형식" 피드백 — 예전 7일 TTL 폐지, 접근 해금도 기록).
 * ⚠️ 해금은 **열람**만 연다. 댓글 작성은 해금 여부와 무관하게 항상 100m 이내에서만 가능하다.
 *
 * - diaryId → 해금 시각(ms). 시각은 기록용이고 판정에는 쓰지 않는다(존재 = 해금).
 * - Compose 상태 맵이라 [unlock] 즉시 화면이 갱신된다([ChatReadStore] 와 같은 패턴).
 * - **기기 로컬 저장**(서버 스키마 변경 없음) — 기기를 바꾸면 다시 열어야 한다(허용 범위).
 */
object DiaryUnlockStore {
    /** 이름은 7일 TTL 시절(`AdUnlockStore`) 그대로 — 그때 광고로 연 기록도 영구 해금으로 승계된다. */
    private const val PREFS = "ad_unlock_store"

    private val state = mutableStateMapOf<String, Long>()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).all.forEach { (k, v) -> (v as? Long)?.let { state[k] = it } }
    }

    /** 이 게시물이 해금돼 있는지. Compose 에서 읽으면 [unlock] 시 리컴포즈. */
    fun isUnlocked(context: Context, diaryId: String): Boolean {
        ensureLoaded(context)
        return state.containsKey(diaryId)
    }

    /**
     * 해금된 게시물 id 전체 — 지도 "해금만" 필터(`MainListScreen`)가 쓴다.
     * 상태 맵을 읽으므로 Compose 에서 호출하면 [unlock] 시 같이 갱신된다.
     */
    fun unlockedIds(context: Context): Set<String> {
        ensureLoaded(context)
        return state.keys.toSet()
    }

    /**
     * 해금된 게시물 id → 해금 시각(ms) — 별 도감(StarLogScreen)의 "해금순" 정렬이 쓴다.
     * 상태 맵을 읽으므로 Compose 에서 호출하면 [unlock] 시 같이 갱신된다.
     */
    fun unlockedAt(context: Context): Map<String, Long> {
        ensureLoaded(context)
        return state.toMap()
    }

    /** 광고 시청 완료 또는 100m 이내 접근 → 이 게시물을 영구 해금한다(이미 해금이면 무시). */
    fun unlock(context: Context, diaryId: String) {
        ensureLoaded(context)
        if (state.containsKey(diaryId)) return
        val now = System.currentTimeMillis()
        state[diaryId] = now
        prefs(context).edit().putLong(diaryId, now).apply()
    }
}
