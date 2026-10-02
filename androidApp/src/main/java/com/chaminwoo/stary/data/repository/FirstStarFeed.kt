package com.chaminwoo.stary.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.NotificationType
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * 첫 별 공지 피드(2026-10-02) — "○○님이 세상에 첫 별을 남겼어요".
 *
 * 서버(announceFirstStar)가 누군가 **처음으로 전체 공개** 별을 올리면 `firstStars/{작성자}` 를 하나 만든다.
 * 모든 사용자 앞으로 notifications 문서를 만들면 사용자 수만큼 쓰기가 생기므로, 앱이 이 컬렉션을 직접 읽어
 * 알림 목록([com.chaminwoo.stary.feature.diary.NotificationViewModel])에 [NotificationType.FIRST_STAR] 로 합친다.
 *
 * - 읽음/삭제는 **이 기기에만** 저장한다(공용 문서라 사용자별 필드를 둘 수 없다).
 *   읽음 = 마지막으로 알림 화면을 연 시각([markAllSeen]) 이전에 생긴 공지. 처음 설치한 기기는 그 시각부터 센다
 *   (설치하자마자 지난 공지들로 빨간 점이 뜨지 않게 — 목록에는 읽음으로 보인다).
 * - 내 공지는 빼고, 최근 [MAX_AGE_MS] 안의 최신 [LIMIT] 개만.
 * - iOS `FirstStarFeed.swift` 패리티.
 */
object FirstStarFeed {
    private const val PREFS = "first_star_feed"
    private const val KEY_SEEN_AT = "seen_at"
    private const val KEY_HIDDEN = "hidden"
    /** 알림 id 접두어 — notifications 문서 id 와 섞이지 않게(삭제 분기에 쓴다). */
    private const val ID_PREFIX = "firstStar_"
    private const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val LIMIT = 30L

    private var prefs: SharedPreferences? = null
    private val seenAt = MutableStateFlow(0L)
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    /** 앱 시작 시 1회(StaryApplication). */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        if (!p.contains(KEY_SEEN_AT)) p.edit().putLong(KEY_SEEN_AT, System.currentTimeMillis()).apply()
        seenAt.value = p.getLong(KEY_SEEN_AT, 0L)
        hidden.value = p.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
    }

    fun isFirstStar(notificationId: String) = notificationId.startsWith(ID_PREFIX)

    /** 알림 목록에 합칠 첫 별 공지(내 것·숨긴 것 제외, 읽음 여부 반영). 읽기 실패면 빈 목록. */
    fun observe(myId: String): Flow<List<AppNotification>> =
        combine(observeServer(), seenAt, hidden) { list, seen, hiddenIds ->
            list.filter { it.actorId != myId && it.id !in hiddenIds }
                .map { it.copy(isRead = it.createdAt <= seen) }
        }

    private fun observeServer(): Flow<List<AppNotification>> = callbackFlow {
        val listener = staryFirestore.collection(StaryConfig.Collections.FIRST_STARS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val since = System.currentTimeMillis() - MAX_AGE_MS
                trySend(snap?.documents.orEmpty().mapNotNull { doc ->
                    val createdAt = doc.getLong("createdAt") ?: return@mapNotNull null
                    if (createdAt < since) return@mapNotNull null
                    AppNotification(
                        id = ID_PREFIX + doc.id,
                        type = NotificationType.FIRST_STAR.name,
                        diaryId = doc.getString("diaryId").orEmpty(),
                        diaryTitle = doc.getString("diaryTitle").orEmpty(),
                        actorId = doc.getString("actorId") ?: doc.id,
                        actorName = doc.getString("actorName").orEmpty(),
                        createdAt = createdAt,
                    )
                })
            }
        awaitClose { listener.remove() }
    }

    /** 알림 화면을 열면 지금까지의 공지를 모두 읽음으로. */
    fun markAllSeen() {
        val now = System.currentTimeMillis()
        seenAt.value = now
        prefs?.edit()?.putLong(KEY_SEEN_AT, now)?.apply()
    }

    /** 스와이프 삭제 — 공용 문서라 지우지 않고 이 기기에서만 숨긴다. */
    fun hide(notificationId: String) {
        hidden.value = hidden.value + notificationId
        prefs?.edit()?.putStringSet(KEY_HIDDEN, hidden.value)?.apply()
    }
}
