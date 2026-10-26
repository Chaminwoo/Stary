package com.chaminwoo.stary.feature.quest

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.data.staryFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

/**
 * 일일 퀘스트(2026-10-19) — 하루 3개. 보상 = 별가루(난이도별, 서버 지급).
 *
 * **정의는 DB `starlightConfig/quests`**(2026-10-22, 원본 tools/starlight/quests.js) — 목록·목표 수·난이도·보상·문구(ko/en/ja)를
 * 앱에 박지 않고 읽는다. 서버(functions/starlight.js claimQuest)도 같은 문서로 목표·보상을 확인한다.
 * 앱에 남는 건 **이벤트 보고 지점**([QuestEvent])과 뽑는 식([DailyQuests.pick]) 뿐 — 모르는 이벤트의 퀘스트는 이 앱에서 뽑지 않는다.
 *
 * ⚠️ iOS `DailyQuests.swift` 와 **이벤트 철자·뽑는 식(난수 호출 순서까지)이 같아야 한다** —
 *    같은 계정을 두 기기에서 쓰면 먼저 만든 쪽 목록을 같이 쓰는데, 식이 다르면 기기마다 목록이 달라진다.
 */
enum class QuestEvent {
    TUTORIAL_STAR,      // 웰컴(튜토리얼) 별 게시물 열기
    UPLOAD,             // 별(다이어리) 올리기 — 시간대 퀘스트도 이 이벤트(올린 시각으로 판정)
    UPLOAD_MEDIA,       // 사진/영상이 있는 별 올리기
    UPLOAD_STACK,       // 이미 별이 있는 자리(머지 반경 안)에 올리기
    VIEW_OTHER,         // 남의 별 상세 열기(잠겨 있어도 — "들여다보기")
    UNLOCK_VISIT,       // 남의 별을 직접 가서(100m 안) 처음 열기
    LIKE,               // 좋아요 누르기(취소는 제외)
    COMMENT,            // 댓글 달기(내 별 포함)
    COMMENT_OTHER,      // 남의 별에 댓글 달기
    EQUIP_TITLE,        // 칭호 장착
    PROFILE_EDIT,       // 닉네임 또는 프로필 사진 변경
    PIN_STAR,           // 프로필에 별 걸기
    CONSTELLATION,      // 별자리 선 켜기
    FILTER,             // 지도 필터 켜기
    GLOBE,              // 지구(글로브) 보기 진입
    METEOR,             // 글로브에서 유성 보기
    VISIT_ACHIEVEMENTS, // 업적 화면
    VISIT_STARLOG,      // 별 도감 화면
    VISIT_PROFILE,      // 남의 프로필 화면
    FRIEND_REQUEST,     // 친구 요청 보내기
    FRIEND_ADD,         // 친구가 한 명 늘었다(수락했거나 내 요청이 수락됨)
    INVITE,             // 초대 링크 공유
    SHARE,              // 공유 영상 만들기
    MUSIC,              // 배경음악 바꾸기
    FOCUS,              // 지도를 가만히 두어 집중 모드 들어가기
    UNLOCK_KEY,         // 열쇠로 별 열기
    OPEN_BOX,           // 우주 항해 떠나기(별가루 — 2026-10-26 전엔 성운 상자 열기, 이름은 서버 퀘스트 정의와 맞춰 그대로)
    ACHIEVEMENT,        // 새 업적 달성
}

/**
 * DB 퀘스트 하나. [event] 가 null 이면 이 앱이 모르는 이벤트(새 버전용) — 목록에 있으면 보이지만 진행되지 않는다.
 * @param hours 로컬 "시"(양끝 포함) — 24 를 넘으면 자정을 넘긴다(21..25 = 오후 9:00~새벽 1:59). null = 언제든.
 * @param group 같은 group 은 하루에 하나만(올리기 계열이 여럿 나오지 않게).
 */
data class QuestDef(
    val id: String,
    val event: QuestEvent?,
    val tier: String,
    val target: Int,
    val group: String?,
    val hours: IntRange?,
    val daily: Boolean,
    val title: Map<String, String>,
    val hint: Map<String, String>,
) {
    fun titleIn(lang: String) = title[lang] ?: title["ko"] ?: id
    fun hintIn(lang: String) = hint[lang] ?: hint["ko"].orEmpty()

    fun matchesHour(hour: Int): Boolean {
        val h = hours ?: return true
        // 21..25 처럼 자정을 넘기면 0~1시를 24~25 로 본다.
        val shifted = if (hour < h.first && h.last >= 24) hour + 24 else hour
        return shifted in h
    }
}

object DailyQuests {
    private const val TAG = "QuestCatalog"
    private const val RETRY_MS = 10_000L
    /** ⚠️ 서버 functions/starlight.js QUESTS_PER_DAY 와 같아야 한다. */
    const val PER_DAY = 3

    /** 첫 사용 안내(FirstStarGuide)가 가리키는 퀘스트 id — DB firstDay 의 첫 칸. */
    const val PROFILE_EDIT_ID = "profile_edit"

    data class Catalog(
        val pool: List<QuestDef>,
        val firstDay: List<String>,
        val hardChance: Double,
        val hardFromDay: Int,
        val rewards: Map<String, Int>,
    ) {
        private val byId = pool.associateBy { it.id }
        fun byId(id: String) = byId[id]
        fun reward(def: QuestDef, fallback: Int) = rewards[def.tier] ?: fallback
    }

    /** 읽어 온 정의(Compose 상태). null = 아직 못 읽음 — 목록을 만들지 않고 기다린다. */
    var catalog by mutableStateOf<Catalog?>(null)
        private set

    /**
     * 정의를 못 읽는 상태 — 서버에 문서가 없거나(seed 업로드 전) 구독이 실패했다. 퀘스트 창이 무한 로딩 대신 안내 + 다시 시도를 보인다
     * (2026-10-23 — 문서가 없으면 조용히 기다리기만 해서 "퀘스트 무한 로딩"이 났다). 문서가 생기면 구독이 그대로 받아 풀린다.
     */
    var unavailable by mutableStateOf(false)
        private set

    private var reg: ListenerRegistration? = null
    private var onLoaded: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** 정의 문서 구독(QuestStore.start 가 부른다). 공개 문서라 계정과 무관 — 한 번 붙이면 유지. */
    fun watch(onLoaded: () -> Unit) {
        this.onLoaded = onLoaded
        if (reg != null) return
        reg = staryFirestore.collection("starlightConfig").document("quests").addSnapshotListener { snap, e ->
            if (e != null) {
                // 거부된 구독은 다시 살아나지 않는다(예: 인증이 막 붙는 순간) — 잠시 뒤 다시 붙인다.
                Log.w(TAG, "퀘스트 정의 구독 실패 — 다시 시도", e)
                reg?.remove(); reg = null
                if (catalog == null) unavailable = true
                main.postDelayed({ if (reg == null) this.onLoaded?.let { watch(it) } }, RETRY_MS)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                // 캐시의 "없음"은 아직 모른다 — 서버가 없다고 확인했을 때만 안내로 바꾼다.
                if (!snap.metadata.isFromCache && catalog == null) unavailable = true
                return@addSnapshotListener
            }
            catalog = parse(snap)
            unavailable = false
            this.onLoaded?.invoke()
        }
    }

    /** "다시 시도" — 구독을 새로 붙여 서버에서 다시 읽는다. */
    fun retry() {
        val cb = onLoaded ?: return
        reg?.remove(); reg = null
        unavailable = false
        watch(cb)
    }

    fun unwatch() {
        reg?.remove(); reg = null
    }

    fun byId(id: String): QuestDef? = catalog?.byId(id)

    private fun parse(snap: DocumentSnapshot): Catalog {
        fun strMap(v: Any?) = (v as? Map<*, *>)
            ?.mapNotNull { (k, x) -> (k as? String)?.let { key -> (x as? String)?.let { key to it } } }?.toMap() ?: emptyMap()
        val pool = (snap.get("pool") as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val hours = (m["hours"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.size == 2 }
            QuestDef(
                id = id,
                event = (m["event"] as? String)?.let { name -> QuestEvent.entries.firstOrNull { it.name == name } },
                tier = m["tier"] as? String ?: "easy",
                target = ((m["target"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                group = m["group"] as? String,
                hours = hours?.let { it[0]..it[1] },
                daily = m["daily"] as? Boolean ?: true,
                title = strMap(m["title"]),
                hint = strMap(m["hint"]),
            )
        }
        return Catalog(
            pool = pool,
            firstDay = (snap.get("firstDay") as? List<*>)?.mapNotNull { it as? String } ?: listOf(PROFILE_EDIT_ID, "upload", "view_other"),
            hardChance = (snap.get("hardChance") as? Number)?.toDouble() ?: 0.35,
            hardFromDay = (snap.get("hardFromDay") as? Number)?.toInt() ?: 4,
            rewards = (snap.get("rewards") as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }?.toMap() ?: emptyMap(),
        )
    }

    /**
     * 그날의 퀘스트 3개.
     * - 1일째: [Catalog.firstDay](첫 사용 안내 순서와 같다 — 프로필 바꾸기 · 별 올리기 · 남의 별 열어 보기).
     * - 2일째부터: 쉬움 1개 + 그 이상 2개 — 칸마다 [Catalog.hardChance] 로 어려움(그 전엔 보통), [Catalog.hardFromDay] 일째부터.
     *   어제 것은 되도록 빼고, 같은 group 은 하루 하나.
     * 무작위는 `uid|날짜|일차` 시드 — 두 기기가 동시에 만들어도 같은 목록이 나온다.
     * ⚠️ 난수 호출 순서(쉬움·보통·어려움 순으로 섞기 → 칸 2개 굴리기)는 iOS 와 같아야 한다.
     */
    fun pick(cat: Catalog, uid: String, day: String, dayNo: Int, previous: List<String>): List<String> {
        if (dayNo <= 1) return cat.firstDay.take(PER_DAY)
        val rng = SplitMix64(fnv1a("$uid|$day|$dayNo"))
        fun shuffled(tier: String): List<QuestDef> {
            val list = cat.pool.filter { it.tier == tier && it.daily && it.event != null }.toMutableList()
            // Fisher–Yates — iOS 와 같은 순서로 섞는다.
            for (i in list.lastIndex downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = list[i]; list[i] = list[j]; list[j] = t
            }
            return list
        }
        val easy = shuffled("easy")
        val normal = shuffled("normal")
        val hard = shuffled("hard")
        val picked = mutableListOf<QuestDef>()
        fun takeFrom(list: List<QuestDef>): Boolean {
            // 어제 나온 건 되도록 빼고, 모자라면 다시 넣는다.
            for (allowPrevious in listOf(false, true)) {
                val q = list.firstOrNull { q ->
                    q !in picked && (allowPrevious || q.id !in previous) &&
                        (q.group == null || picked.none { it.group == q.group })
                }
                if (q != null) { picked += q; return true }
            }
            return false
        }
        takeFrom(easy)
        repeat(PER_DAY - 1) {
            // 굴림은 항상 한 번씩(일차와 무관) — 두 앱의 난수 흐름이 같게.
            val roll = rng.nextInt(1000)
            val useHard = dayNo >= cat.hardFromDay && roll < (cat.hardChance * 1000).toInt()
            val first = if (useHard) hard else normal
            val second = if (useHard) normal else hard
            if (!takeFrom(first) && !(dayNo >= cat.hardFromDay && takeFrom(second))) takeFrom(easy)
        }
        return picked.map { it.id }
    }

    /** FNV-1a 32bit(UTF-8) — iOS `DailyQuests.fnv1a` 와 같은 값. */
    internal fun fnv1a(s: String): Long {
        var h = 0x811C9DC5.toInt()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toInt() and 0xFF)
            h *= 16777619
        }
        return h.toLong() and 0xFFFFFFFFL
    }

    internal class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /** 0 until [n] — `(next >>> 1) % n`(부호 없는 값으로 계산해 iOS UInt64 와 같다). */
        fun nextInt(n: Int): Int = ((next() ushr 1) % n.toLong()).toInt()
    }
}
