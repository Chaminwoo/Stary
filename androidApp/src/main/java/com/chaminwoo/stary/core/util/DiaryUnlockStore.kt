package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 해금한(열어 본) 다른 사람의 게시물(다이어리) 기록 — **영구, 계정별**.
 *
 * 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
 * ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, **한 번 풀린 게시물은 그 계정에서 계속 열려 있다**
 * (2026-09-22 "다이어리도 아예 해금 형식" 피드백 — 예전 7일 TTL 폐지, 접근 해금도 기록).
 * ⚠️ 해금은 **열람**만 연다. 댓글 작성은 해금 여부와 무관하게 항상 100m 이내에서만 가능하다.
 *
 * - diaryId → 해금 시각(ms). 시각은 기록용이고 판정에는 쓰지 않는다(존재 = 해금).
 * - Compose 상태 맵이라 [unlock] 즉시 화면이 갱신된다([ChatReadStore] 와 같은 패턴).
 * - 기기 로컬(prefs)이 1차 저장소이고, **서버 사본**(`users/{uid}/viewedDiaries/{id}.unlockedAt`)에도 남긴다
 *   (2026-09-26 별 도감 "열람한 모든 다이어리" 요청). [syncWithServer] 가 앱 시작 시 둘을 합친다 —
 *   기기를 바꿔도 복원되고, 잠금이 생기기 전(2026-09-21)에 열람한 글도 "연 것"으로 들어온다.
 * - ⚠️ 로컬 저장소는 **계정(uid)마다 따로**(`ad_unlock_store_u_{uid}`, 둘러보기는 `_guest`) — 2026-10-22 수정.
 *   예전엔 기기에 하나라 다른 계정으로 로그인해도 별 도감·해금이 그대로 보였고, 동기화가 계정 서버 기록을 한데 섞었다.
 *   읽을 때마다 [GoogleAuthHelper.currentUserId] 를 보고 계정이 바뀌었으면 그 계정 저장소로 갈아탄다.
 */
object DiaryUnlockStore {
    /** 계정 구분 전의 기기 공용 저장소(7일 TTL 시절 `AdUnlockStore` 이름) — [migrateLegacy] 가 한 번 정리한다. */
    private const val LEGACY_PREFS = "ad_unlock_store"
    /** 예전 공용 저장소를 서버로 처음 올린 계정 uid(LEGACY_PREFS 안의 키). */
    private const val KEY_BACKFILLED = "__backfilled_uid"
    private const val GUEST = "guest"

    private val state = mutableStateMapOf<String, Long>()
    /** 지금 [state] 에 올라와 있는 저장소의 계정(null = 아직 안 읽음). */
    private var loadedKey: String? = null

    private fun accountKey(): String = GoogleAuthHelper.currentUserId ?: GUEST

    private fun prefs(context: Context, key: String) =
        context.applicationContext.getSharedPreferences(
            if (key == GUEST) "${LEGACY_PREFS}_guest" else "${LEGACY_PREFS}_u_$key",
            Context.MODE_PRIVATE,
        )

    private fun ensureLoaded(context: Context) {
        val key = accountKey()
        if (loadedKey == key) return
        if (key != GUEST) migrateLegacy(context, key)
        loadedKey = key
        state.clear()
        prefs(context, key).all.forEach { (k, v) -> (v as? Long)?.let { state[k] = it } }
    }

    /**
     * 계정 구분 전의 공용 저장소 정리(1회).
     *  - 이미 서버로 올린 적이 있으면(= [KEY_BACKFILLED]) 각 계정의 해금은 서버 사본에 있으니 버린다
     *    — 여러 계정의 기록이 섞여 있어 어느 한 계정에 넘기면 다시 섞인다.
     *  - 올린 적이 없으면 이 기기에서 처음 로그인한 계정의 것으로 넘긴다([syncWithServer] 가 서버에도 올린다).
     */
    private fun migrateLegacy(context: Context, uid: String) {
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val all = legacy.all
        if (all.isEmpty()) return
        if (legacy.getString(KEY_BACKFILLED, null) == null) {
            val edit = prefs(context, uid).edit()
            all.forEach { (k, v) -> (v as? Long)?.let { edit.putLong(k, it) } }
            edit.apply()
        }
        legacy.edit().clear().apply()
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

    /** 광고 시청 완료 또는 100m 이내 접근 → 이 게시물을 영구 해금한다(이미 해금이면 무시). 서버 사본도 남긴다. */
    fun unlock(context: Context, diaryId: String) {
        ensureLoaded(context)
        if (state.containsKey(diaryId)) return
        val now = System.currentTimeMillis()
        state[diaryId] = now
        prefs(context, accountKey()).edit().putLong(diaryId, now).apply()
        GoogleAuthHelper.currentUserId?.let { uid ->
            ioScope.launch { FirebaseViewedRepository().markUnlocked(uid, mapOf(diaryId to now)) }
        }
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 이번 프로세스에서 동기화를 마친 uid — 화면마다 불러도 1회만. */
    private var syncedUid: String? = null

    /**
     * 이 계정의 로컬 ↔ 서버 해금 기록을 합친다(프로세스당 uid 1회, 실패하면 다음 호출 때 재시도).
     *  ① 서버 `unlockedAt` + 잠금 이전 열람(`viewedAt` < [com.chaminwoo.stary.shared.config.StaryConfig.DIARY_LOCK_SINCE_MS]) → 로컬에 합침.
     *  ② 잠금 이전 열람은 서버에도 `unlockedAt` 으로 굳혀 둔다(다시 열면 viewedAt 이 갱신돼 증거가 사라지므로).
     *  ③ 로컬에만 있는 기록(서버 쓰기 실패·옛 공용 저장소에서 넘어온 것)은 서버에 올린다 — 저장소가 계정별이라 늘 이 계정 것이다.
     */
    suspend fun syncWithServer(context: Context, uid: String) {
        if (syncedUid == uid || GoogleAuthHelper.currentUserId != uid) return
        ensureLoaded(context)
        val repo = FirebaseViewedRepository()
        val server = repo.fetchOpened(uid) ?: return
        // 서버를 읽는 사이 로그아웃/계정 전환 — 다른 계정 저장소에 섞지 않는다.
        if (GoogleAuthHelper.currentUserId != uid) return
        ensureLoaded(context)
        val edit = prefs(context, uid).edit()
        val upload = HashMap(server.legacy)
        state.forEach { (id, at) -> if (id !in server.unlocked) upload[id] = at }
        (server.unlocked + server.legacy).forEach { (id, at) ->
            val cur = state[id]
            if (cur == null || at < cur) {
                state[id] = at
                edit.putLong(id, at)
            }
        }
        edit.apply()
        repo.markUnlocked(uid, upload)
        syncedUid = uid
    }
}
