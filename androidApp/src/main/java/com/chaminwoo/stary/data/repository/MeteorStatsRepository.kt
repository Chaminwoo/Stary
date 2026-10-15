package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 글로브에서 본 유성 기록 — 유성 업적(첫 유성 · 3/5연속 · 누적 50개)과 히든 "7연속" 판정용(2026-10-15).
 *
 * `users/{uid}` 에 두 필드로 남긴다(재설치·기기 변경에도 유지, 오프라인이면 쓰기 대기 후 반영):
 *  - `meteorsSeen` : 본 유성 수(`FieldValue.increment`)
 *  - `meteorStreaks` : 도달한 연속 횟수들(`FieldValue.arrayUnion`) — 최고 연속 = 최댓값.
 *    "최고값" 하나를 덮어쓰면 다른 기기·재설치 직후(로컬 최고값 0)에 낮은 값으로 덮일 수 있어,
 *    줄어들 수 없는 집합으로 쌓는다.
 * ⚠️ `userName`/`profileImageUrl` 은 건드리지 않는다(CLAUDE.md §2.6 — 항상 merge).
 * iOS `MeteorStats.swift` 와 같은 필드·규칙.
 */
class MeteorStatsRepository {

    data class Stats(val seen: Int = 0, val bestStreak: Int = 0)

    private val users = staryFirestore.collection(StaryConfig.Collections.USERS)

    /** 유성 1개를 봤다 — [streak] = 이번 유성까지의 연속 횟수(1 = 연속 아님). */
    fun record(uid: String, streak: Int) {
        if (uid.isBlank()) return
        users.document(uid).set(
            mapOf(
                FIELD_SEEN to FieldValue.increment(1),
                FIELD_STREAKS to FieldValue.arrayUnion(streak.toLong()),
            ),
            SetOptions.merge(),
        )
    }

    fun observe(uid: String): Flow<Stats> = callbackFlow {
        if (uid.isBlank()) {
            trySend(Stats())
            awaitClose { }
            return@callbackFlow
        }
        val listener = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val seen = (snapshot.get(FIELD_SEEN) as? Number)?.toInt() ?: 0
            val best = (snapshot.get(FIELD_STREAKS) as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }?.maxOrNull() ?: 0
            trySend(Stats(seen, best))
        }
        awaitClose { listener.remove() }
    }

    companion object {
        const val FIELD_SEEN = "meteorsSeen"
        const val FIELD_STREAKS = "meteorStreaks"
    }
}
