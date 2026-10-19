package com.chaminwoo.stary.feature.quest

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StaryToast
import com.chaminwoo.stary.core.util.Haptics
import com.chaminwoo.stary.core.util.LocaleManager
import com.chaminwoo.stary.core.util.TutorialStarState
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import com.chaminwoo.stary.data.repository.MeteorStatsRepository
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 일일 퀘스트 진행 + 열쇠 지갑(2026-10-19).
 *
 * 저장 위치 = `users/{uid}` 의 두 필드(유성 기록 `MeteorStatsRepository` 와 같은 방식 — 규칙 변경 없이 소유자 쓰기, 항상 merge):
 *  - `questKeys` : 가진 열쇠 수(`FieldValue.increment` 로만 바꾼다 — 두 기기가 동시에 써도 합이 맞다)
 *  - `quest`     : 그날 상태 `{ day: "yyyy-MM-dd"(기기 현지 날짜), dayNo, ids[3], progress{id: n}, claimed[], bonus }`
 * ⚠️ `userName`/`profileImageUrl` 은 건드리지 않는다(CLAUDE.md §2.6).
 *
 * 새 날 목록은 **서버 스냅샷을 본 뒤에만** 만든다 — 재설치 직후 빈 캐시를 보고 만들면 다른 기기에서 진행 중인 오늘 목록을 덮는다.
 * 1일째 웰컴 별 퀘스트는 "새 계정 + 이 기기에 웰컴 별이 떠 있음"일 때만(이전 가입 계정은 웰컴 별을 건너뛰므로 —
 * [com.chaminwoo.stary.core.util.ReturningAccountOnboarding]) → 그래서 [GoogleAuthHelper.returningAccount] 가 정해진 뒤에 만든다.
 * iOS `QuestStore.swift` 와 같은 필드·규칙.
 */
object QuestStore {
    private const val TAG = "QuestStore"
    private const val FIELD_KEYS = "questKeys"
    private const val FIELD_QUEST = "quest"
    private const val RETURNING_WAIT_MS = 8_000L

    data class Daily(
        val day: String,
        val dayNo: Int,
        val ids: List<String>,
        val progress: Map<String, Int>,
        val claimed: Set<String>,
        val bonus: Boolean,
    ) {
        fun progressOf(id: String) = progress[id] ?: 0
        fun isComplete(def: QuestDef) = progressOf(def.id) >= def.target
        val quests: List<QuestDef> get() = ids.mapNotNull { DailyQuests.byId(it) }
        /** 완료했지만 아직 보상을 안 받은 수 — 지도 "!" 버튼 배지. */
        val claimable: Int get() = quests.count { isComplete(it) && it.id !in claimed } + if (bonusReady && !bonus) 1 else 0
        /** 보너스 퀘스트(4번째 행)를 받을 수 있는가 — 3개 보상을 모두 받았다. */
        val bonusReady: Boolean get() = ids.isNotEmpty() && ids.all { it in claimed }
        /** 오늘 것을 보너스까지 전부 받았다. */
        val allClaimed: Boolean get() = bonusReady && bonus
    }

    /** 가진 열쇠 수(Compose 상태). 비로그인이면 0. */
    var keys by mutableIntStateOf(0)
        private set

    /** 오늘 퀘스트(Compose 상태). null = 아직 모름(로딩/비로그인). */
    var daily by mutableStateOf<Daily?>(null)
        private set

    private var uid: String? = null
    private var appContext: Context? = null
    private var registration: ListenerRegistration? = null
    private var returningJob: Job? = null
    private var serverSeen = false
    /** 서버 확인 뒤에도 [GoogleAuthHelper.returningAccount] 를 끝내 모르면(프로필 읽기 실패) "이전 계정"으로 보고 목록을 만든다. */
    private var returningFallback = false
    private var fallbackJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun doc(uid: String) = staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)

    fun today(): String = LocalDate.now().toString()

    /** 로그인 사용자로 구독 시작(같은 uid 면 무시). MainScreen `LaunchedEffect(userId)`. */
    fun start(context: Context, uid: String) {
        if (this.uid == uid && registration != null) return
        stop()
        this.uid = uid
        appContext = context.applicationContext
        // MetadataChanges.INCLUDE — 캐시 값이 서버와 같으면 "서버 확인됨"은 메타데이터만 바뀐 이벤트로 온다.
        // 이걸 안 받으면 serverSeen 이 영영 false 라 오늘 목록을 못 만들어 퀘스트 창이 무한 로딩이었다(2026-10-19).
        registration = doc(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null || snap == null || this.uid != uid) return@addSnapshotListener
            if (!snap.metadata.isFromCache && !serverSeen) {
                serverSeen = true
                fallbackJob = scope.launch {
                    delay(RETURNING_WAIT_MS)
                    returningFallback = true
                    ensureToday()
                }
            }
            apply(snap)
            ensureToday()
        }
        // 이전 가입 계정 여부가 정해지면(1일째 목록에 필요) 다시 시도.
        returningJob = scope.launch {
            GoogleAuthHelper.returningAccount.collect { if (it != null) ensureToday() }
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
        returningJob?.cancel()
        returningJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        uid = null
        serverSeen = false
        returningFallback = false
        keys = 0
        daily = null
    }

    private fun apply(snap: DocumentSnapshot) {
        keys = (snap.get(FIELD_KEYS) as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val q = snap.get(FIELD_QUEST) as? Map<*, *>
        daily = q?.let { m ->
            val day = m["day"] as? String ?: return@let null
            Daily(
                day = day,
                dayNo = (m["dayNo"] as? Number)?.toInt() ?: 1,
                ids = (m["ids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                progress = (m["progress"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }
                    ?.toMap() ?: emptyMap(),
                claimed = (m["claimed"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                bonus = m["bonus"] as? Boolean ?: false,
            )
        }
    }

    /** 날짜가 바뀌었으면 새 목록을 만든다(서버 확인 후). 자정을 넘겨 켜 둔 경우를 위해 MainScreen 이 1분마다도 부른다. */
    fun ensureToday() {
        val uid = uid ?: return
        if (!serverSeen) return
        val today = today()
        val cur = daily
        if (cur != null && cur.day == today) return
        val returning = GoogleAuthHelper.returningAccount.value ?: if (returningFallback) true else return
        val dayNo = (cur?.dayNo ?: 0) + 1
        val tutorial = !returning && !TutorialStarState.done
        val ids = DailyQuests.pick(uid, today, dayNo, cur?.ids ?: emptyList(), tutorial)
        val next = Daily(today, dayNo, ids, emptyMap(), emptySet(), false)
        daily = next
        // mergeFields("quest") = quest 필드만 통째로 교체(어제 progress/claimed 가 섞이지 않게), 다른 필드는 그대로.
        doc(uid).set(
            mapOf(
                FIELD_QUEST to mapOf(
                    "day" to today, "dayNo" to dayNo, "ids" to ids,
                    "progress" to emptyMap<String, Int>(), "claimed" to emptyList<String>(), "bonus" to false,
                )
            ),
            SetOptions.mergeFields(FIELD_QUEST),
        ).addOnFailureListener { Log.w(TAG, "오늘 퀘스트 기록 실패", it) }
    }

    /**
     * 앱 곳곳에서 "이걸 했다"를 알린다. 오늘 목록에 그 이벤트가 있으면 진행도 +1(목표 이상은 안 셈).
     * [key] 를 주면 같은 날 같은 key 는 한 번만 센다(예: 다이어리 id). 어느 스레드에서 불러도 된다(메인에서 처리).
     */
    fun report(event: QuestEvent, key: String? = null) {
        scope.launch { reportNow(event, key) }
    }

    /** [report] 의 key 중복 거르기 — "남의 별 3개"는 서로 다른 별 3개여야 한다(같은 별을 세 번 열기 X). 날마다 비운다. */
    private val countedKeys = mutableSetOf<String>()
    private var countedDay: String? = null

    private fun reportNow(event: QuestEvent, key: String?) {
        val uid = uid ?: return
        val d = daily ?: return
        if (d.day != today()) {
            ensureToday()
            return
        }
        if (countedDay != d.day) {
            countedDay = d.day
            countedKeys.clear()
        }
        if (key != null && !countedKeys.add("${event.name}:$key")) return
        val hour = LocalTime.now().hour
        val updates = mutableMapOf<String, Int>()
        val finished = mutableListOf<QuestDef>()
        for (def in d.quests) {
            if (def.event != event || !def.matchesHour(hour)) continue
            val cur = d.progressOf(def.id)
            if (cur >= def.target) continue
            updates[def.id] = cur + 1
            if (cur + 1 >= def.target) finished += def
        }
        if (updates.isEmpty()) return
        daily = d.copy(progress = d.progress + updates)
        doc(uid).set(mapOf(FIELD_QUEST to mapOf("progress" to updates)), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "퀘스트 진행 기록 실패", it) }
        finished.firstOrNull()?.let { def ->
            val ctx = appContext?.let { LocaleManager.wrap(it) } ?: return@let
            Haptics.medium()
            StaryToast.show(ctx.getString(R.string.quest_done_toast, ctx.getString(def.title)))
        }
    }

    /**
     * 완료한 퀘스트의 보상(열쇠)을 받는다. 3개를 모두 받으면 보너스까지.
     * @return 이번에 받은 열쇠 수(0 = 받을 수 없음).
     */
    fun claim(id: String): Int {
        val uid = uid ?: return 0
        val d = daily ?: return 0
        val def = d.quests.firstOrNull { it.id == id } ?: return 0
        if (!d.isComplete(def) || id in d.claimed) return 0
        val gained = DailyQuests.REWARD_KEYS
        daily = d.copy(claimed = d.claimed + id)
        keys += gained
        doc(uid).set(
            mapOf(FIELD_KEYS to FieldValue.increment(gained.toLong()), FIELD_QUEST to mapOf("claimed" to FieldValue.arrayUnion(id))),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "열쇠 보상 기록 실패", it) }
        return gained
    }

    /**
     * 보너스 퀘스트(4번째 행, 2026-10-19 — 예전엔 3번째 보상과 함께 자동 지급) — 3개 보상을 모두 받았으면 열쇠 [DailyQuests.BONUS_KEYS].
     * 이 날짜를 `questClearDays` 에도 남긴다(퀘스트 보상 칭호 — 날짜 집합이라 두 기기가 같은 날 받아도 한 번만 센다).
     */
    fun claimBonus(): Int {
        val uid = uid ?: return 0
        val d = daily ?: return 0
        if (!d.bonusReady || d.bonus) return 0
        val gained = DailyQuests.BONUS_KEYS
        daily = d.copy(bonus = true)
        keys += gained
        doc(uid).set(
            mapOf(
                FIELD_KEYS to FieldValue.increment(gained.toLong()),
                FIELD_QUEST to mapOf("bonus" to true),
                MeteorStatsRepository.FIELD_QUEST_CLEARS to FieldValue.arrayUnion(d.day),
            ),
            SetOptions.merge(),
        ).addOnFailureListener { Log.w(TAG, "보너스 열쇠 기록 실패", it) }
        return gained
    }

    /** 광고 보상 — 열쇠 1개(퀘스트 창 열쇠 칩 → "광고를 보고 열쇠를 얻으시겠어요?"). */
    fun grantAdKey() {
        val uid = uid ?: return
        keys += 1
        doc(uid).set(mapOf(FIELD_KEYS to FieldValue.increment(1)), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "광고 열쇠 기록 실패", it) }
    }

    /** 열쇠 1개를 쓴다(별 해금). 없으면 false. */
    fun spendKey(): Boolean {
        val uid = uid ?: return false
        if (keys <= 0) return false
        keys -= 1
        doc(uid).set(mapOf(FIELD_KEYS to FieldValue.increment(-1)), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "열쇠 사용 기록 실패", it) }
        return true
    }

    /** 로그인해서 열쇠를 쓸 수 있는 상태인지(비로그인은 광고로만 연다). */
    val available: Boolean get() = uid != null
}
