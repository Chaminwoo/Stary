package com.chaminwoo.stary.feature.starlight

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.core.designsystem.StarCatalog
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * 별가루(2026-10-26 개편, 옛 "별빛 공방") — 별가루 지갑 · 우주 항해 · 뽑기 풀.
 *
 * - 지갑 `wallets/{uid}` 는 **읽기만**(규칙상 쓰기 불가). 바꾸는 일은 전부 [request] 로 서버(functions/starlight.js)에 맡긴다:
 *   `users/{uid}/starlightOps/{id}` 에 `{op, 인자, status:"pending"}` 을 만들면 서버가 같은 문서에 결과를 쓴다.
 * - 뽑기 풀 `starlightItems`(active) · 설정 `starlightConfig/main` 도 DB 에서 읽는다 — 앱 업데이트 없이 늘리고 확률을 바꾼다.
 *   설정 문서가 없으면 [Config.DEFAULT](서버 DEFAULT_CONFIG 와 같은 값).
 * - 우주 항해([voyage]) 는 경로(일반 · 희귀 탐사)마다 값과 확률이 다르다. **천장(확정 보정)은 없다** — 매 항해가 독립.
 * - 광고 보상([adReward]) 은 하루 한도·연속 간격을 서버가 센다(한국 시간 자정에 초기화).
 * - 모양·색 아이템은 [StarCatalog] 에 가르쳐 둔다(업로드 피커·업적 탐사 칸에서 그릴 수 있게).
 * - 장착(프레임·배지·칭호)은 서버가 소유를 확인하고 `starlightEquips/{uid}` 에 쓴다([equip]).
 *   별 고리는 장착이 아니라 **별마다** 고른다(업로드 → `diaries/{id}.ring`, 서버 checkDiaryRing 이 소유 확인).
 * - 천체 망원경: 업적 ??? 공개는 [revealed], 히든 업적 단서는 [hints](서버가 문구까지 지갑에 넣어 준다).
 * iOS `StarlightStore.swift` 와 같은 필드·규칙.
 */
object StarlightStore {
    private const val TAG = "StarlightStore"
    private const val WALLETS = "wallets"
    private const val ITEMS = "starlightItems"
    private const val CONFIG = "starlightConfig"
    private const val OPS = "starlightOps"
    private const val EQUIPS = "starlightEquips"
    private const val OP_TIMEOUT_MS = 25_000L
    private const val RESUBSCRIBE_MS = 3_000L

    val RARITIES = listOf("common", "rare", "epic", "legendary")

    /** 항해 경로 id — 화면 순서. 설정에 다른 경로가 있어도 화면엔 이 둘만(새 경로는 앱 업데이트 때). */
    val ROUTES = listOf("normal", "rare")

    /** 항해 경로 하나 — [cost] 한 번 떠나는 별가루, [weights] 등급 확률(합이 100이 아니어도 비율). */
    data class Route(val cost: Int, val weights: Map<String, Double>) {
        /** 등급별 확률(%) — 확률 안내용. */
        fun percent(rarity: String): Double {
            val total = weights.values.sum()
            return if (total <= 0) 0.0 else (weights[rarity] ?: 0.0) * 100 / total
        }
    }

    data class Config(
        val questStardust: Int,
        val bonusStardust: Int,
        val keyCost: Int,
        /** 옛 성운 상자 한 개 값 — 남은 상자를 별가루로 돌려줄 때(convertBoxes) 서버가 쓴다. */
        val boxCost: Int,
        val voyages: Map<String, Route>,
        val dupRefund: Map<String, Int>,
        val maxVoyage: Int,
        val maxExchange: Int,
        val adStardust: Int,
        val adDailyCap: Int,
        val adCooldownSec: Int,
        /** 단서가 준비된 히든 업적 id — 반사·전파 망원경 버튼은 여기 있는 업적에만. */
        val hintTargets: Set<String> = emptySet(),
    ) {
        fun route(id: String): Route = voyages[id] ?: DEFAULT.voyages[id] ?: Route(0, emptyMap())

        companion object {
            val DEFAULT = Config(
                questStardust = 10, bonusStardust = 20, keyCost = 10, boxCost = 100,
                voyages = mapOf(
                    "normal" to Route(50, mapOf("common" to 66.0, "rare" to 28.0, "epic" to 5.0, "legendary" to 1.0)),
                    "rare" to Route(300, mapOf("common" to 12.0, "rare" to 28.0, "epic" to 50.0, "legendary" to 10.0)),
                ),
                dupRefund = mapOf("common" to 10, "rare" to 25, "epic" to 50, "legendary" to 100),
                maxVoyage = 10, maxExchange = 99,
                adStardust = 10, adDailyCap = 10, adCooldownSec = 10,
            )
        }
    }

    /** 뽑기 풀의 아이템 하나. [payload] 는 종류별(kind) 그림/효과 정의 — StarlightArt.kt 가 해석한다. */
    data class Item(
        val id: String,
        val kind: String,
        val rarity: String,
        val name: Map<String, String>,
        val desc: Map<String, String>,
        val payload: Map<String, Any?>,
        val order: Int,
        val active: Boolean = true,
    ) {
        fun nameIn(lang: String) = name[lang] ?: name["ko"] ?: id
        fun descIn(lang: String) = desc[lang] ?: desc["ko"].orEmpty()
    }

    /** 장착 칸 — 별 고리는 별마다 고르므로 칸이 없다(서버 equip 은 옛 앱용 aura 칸을 아직 받는다). */
    enum class Slot(val id: String, val kind: String) { FRAME("frame", "frame"), BADGE("badge", "badge"), TITLE("title", "title") }

    /** 한 사람의 장착 상태(아이템 id). */
    data class Equips(val frame: String? = null, val badge: String? = null, val title: String? = null) {
        fun of(slot: Slot) = when (slot) {
            Slot.FRAME -> frame
            Slot.BADGE -> badge
            Slot.TITLE -> title
        }
    }

    /** 히든 업적 단서 — [level] 1 흐릿 · 2 선명, [text] 언어별 문구. */
    data class Hint(val level: Int, val text: Map<String, String>) {
        fun textIn(lang: String) = text[lang] ?: text["ko"].orEmpty()
    }

    /** 항해 한 번에서 찾아온 것. */
    data class Draw(val id: String, val rarity: String, val kind: String, val dup: Boolean, val refund: Int, val keys: Int)

    sealed interface OpResult {
        data class Done(val result: Map<String, Any?>) : OpResult
        /**
         * [code] = 서버 오류 코드(insufficient · invalid · empty_pool · ad_limit · ad_cooldown · already · not_ready · server)
         * 또는 timeout · offline.
         */
        data class Failed(val code: String) : OpResult
    }

    /** 지갑 문서의 별가루(서버 값). */
    private var serverStardust by mutableIntStateOf(0)
    /**
     * 받는 중인 보상을 더한 "적어도 이만큼"(퀘스트·광고 보상이 누르는 즉시 보이게, 2026-10-27) — 받기 요청이 모두 끝나면 0.
     * 서버 값과 더하지 않고 큰 쪽을 보여 준다 — 지갑 갱신과 요청 완료 중 무엇이 먼저 와도 두 번 더해 보이지 않게.
     */
    private var creditFloor by mutableIntStateOf(0)
    private var crediting = 0
    val stardust: Int get() = max(serverStardust, creditFloor)
    /** 옛 성운 상자(개편 전에 만들어 둔 것) — 별가루 화면을 열면 [convertBoxes] 로 별가루가 된다. */
    var boxes by mutableIntStateOf(0)
        private set
    /** 가진 아이템 id → 개수(꾸미기는 1, 소모품은 남은 수). */
    var owned by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    /** 오늘(한국 시간) 받은 광고 보상 횟수 · 마지막으로 받은 시각(ms). */
    private var adsDay by mutableStateOf("")
    private var adsN by mutableIntStateOf(0)
    var adsLast by mutableLongStateOf(0L)
        private set
    /**
     * 지갑을 한 번이라도 받았는지(받기 전엔 잔액을 "…"로). 기기 캐시 값도 받은 것으로 친다(2026-10-27) — 예전엔 서버 확정 값만 기다렸는데,
     * 캐시와 서버 값이 같으면 다시 알림이 오지 않아 "…"가 계속 남았다. 문서가 없으면(아직 한 번도 받은 적 없음) 0.
     */
    var walletLoaded by mutableStateOf(false)
        private set
    var config by mutableStateOf(Config.DEFAULT)
        private set
    /** 뽑기 풀 전체(뽑히지 않게 내린 것 포함 — 이미 가진 사람이 계속 그려야 한다). */
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    private var itemIndex: Map<String, Item> = emptyMap()

    fun item(id: String?): Item? = id?.takeIf { it.isNotBlank() }?.let { itemIndex[it] }

    /** 공방 칭호는 users.equippedTitle 에 "sl:{아이템 id}" 로 들어간다(업적 칭호 id 와 안 겹치게 — 서버 equip 이 쓴다). */
    const val TITLE_PREFIX = "sl:"

    /** "sl:{id}" 칭호 → 그 아이템(모르면 null). */
    fun titleItem(titleId: String?): Item? =
        titleId?.takeIf { it.startsWith(TITLE_PREFIX) }?.let { item(it.removePrefix(TITLE_PREFIX)) }

    /** 오늘(한국 시간) 광고 보상을 더 받을 수 있는 횟수. */
    val adsLeft: Int
        get() = (config.adDailyCap - (if (adsDay == kstDay()) adsN else 0)).coerceAtLeast(0)

    /** 지금 뽑히는 아이템 중 [rarity] 인 것의 수 — 확률 안내("n종"). */
    fun poolCount(rarity: String): Int = items.count { it.active && it.rarity == rarity }

    /** 내 장착 상태. */
    var myEquips by mutableStateOf(Equips())
        private set
    /** 굴절 망원경으로 조건을 연 업적 id. */
    var revealed by mutableStateOf<Set<String>>(emptySet())
        private set
    /** 반사·전파 망원경으로 얻은 히든 업적 단서. */
    var hints by mutableStateOf<Map<String, Hint>>(emptyMap())
        private set

    /** 가진 별 모양 번호(우주 항해) — 업로드 피커에서 내장 0~20 뒤에 붙인다. */
    val ownedShapeTypes: List<Int>
        get() = items.filter { it.kind == "shape" && (owned[it.id] ?: 0) > 0 }
            .mapNotNull { (it.payload["type"] as? Number)?.toInt() }.distinct().sorted()
    val ownedColorIndices: List<Int>
        get() = items.filter { it.kind == "color" && (owned[it.id] ?: 0) > 0 }
            .mapNotNull { (it.payload["index"] as? Number)?.toInt() }.distinct().sorted()

    /** 가진 아이템 중 [kind] 인 것(희귀한 것부터). */
    fun ownedOf(kind: String): List<Item> = items.filter { it.kind == kind && (owned[it.id] ?: 0) > 0 }

    private var uid: String? = null
    private var walletReg: ListenerRegistration? = null
    private var itemsReg: ListenerRegistration? = null
    private var configReg: ListenerRegistration? = null
    private var equipReg: ListenerRegistration? = null
    private var retryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun db() = staryFirestore

    /** 서버 adReward 와 같은 "하루" — 한국 시간 날짜(yyyy-MM-dd). */
    private fun kstDay(): String = LocalDate.now(ZoneOffset.ofHours(9)).toString()

    /** 로그인 사용자로 구독 시작(같은 uid 면 무시). MainScreen `LaunchedEffect(userId)` — QuestStore 와 함께. */
    fun start(uid: String) {
        if (this.uid == uid && walletReg != null) return
        stop()
        this.uid = uid
        subscribe(uid)
    }

    private fun subscribe(uid: String) {
        walletReg = db().collection(WALLETS).document(uid).addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener retry(uid, "지갑", e)
            if (snap == null || this.uid != uid) return@addSnapshotListener
            applyWallet(snap)
        }
        itemsReg = db().collection(ITEMS).addSnapshotListener { q, e ->
            if (e != null) return@addSnapshotListener retry(uid, "뽑기 풀", e)
            if (q == null || this.uid != uid) return@addSnapshotListener
            items = q.documents.mapNotNull(::parseItem).sortedWith(compareBy({ RARITIES.indexOf(it.rarity) }, { it.order }))
            itemIndex = items.associateBy { it.id }
            items.forEach(::teach)
        }
        equipReg = db().collection(EQUIPS).document(uid).addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener retry(uid, "장착", e)
            if (snap == null || this.uid != uid) return@addSnapshotListener
            myEquips = parseEquips(snap)
        }
        configReg = db().collection(CONFIG).document("main").addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener retry(uid, "설정", e)
            if (snap == null || this.uid != uid) return@addSnapshotListener
            config = parseConfig(snap)
        }
    }

    /** 거부된 구독은 다시 살아나지 않는다(로그인 직후 인증이 붙는 순간 등) — 잠시 뒤 통째로 다시 붙인다. */
    private fun retry(uid: String, what: String, e: Exception) {
        Log.w(TAG, "$what 구독 실패 — 다시 시도", e)
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(RESUBSCRIBE_MS)
            if (this@StarlightStore.uid != uid) return@launch
            removeListeners()
            subscribe(uid)
        }
    }

    private fun removeListeners() {
        walletReg?.remove(); walletReg = null
        itemsReg?.remove(); itemsReg = null
        configReg?.remove(); configReg = null
        equipReg?.remove(); equipReg = null
    }

    fun stop() {
        removeListeners()
        retryJob?.cancel(); retryJob = null
        uid = null
        serverStardust = 0; creditFloor = 0; crediting = 0; boxes = 0; owned = emptyMap()
        adsDay = ""; adsN = 0; adsLast = 0L
        walletLoaded = false
        myEquips = Equips()
        revealed = emptySet()
        hints = emptyMap()
    }

    private fun applyWallet(snap: DocumentSnapshot) {
        serverStardust = (snap.get("stardust") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        boxes = (snap.get("boxes") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        owned = (snap.get("items") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } } }
            ?.toMap() ?: emptyMap()
        val ads = snap.get("ads") as? Map<*, *>
        adsDay = ads?.get("day") as? String ?: ""
        adsN = (ads?.get("n") as? Number)?.toInt() ?: 0
        adsLast = (ads?.get("last") as? Number)?.toLong() ?: 0L
        revealed = (snap.get("revealed") as? Map<*, *>)?.filterValues { it == true }?.keys?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        hints = (snap.get("hints") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val id = k as? String ?: return@mapNotNull null
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val text = (m["text"] as? Map<*, *>)?.mapNotNull { (lk, lv) -> (lk as? String)?.let { l -> (lv as? String)?.let { l to it } } }?.toMap()
                ?: return@mapNotNull null
            id to Hint((m["level"] as? Number)?.toInt() ?: 1, text)
        }?.toMap() ?: emptyMap()
        walletLoaded = true
    }

    private fun parseConfig(snap: DocumentSnapshot): Config {
        val d = Config.DEFAULT
        if (!snap.exists()) return d
        fun int(k: String, def: Int) = (snap.get(k) as? Number)?.toInt() ?: def
        fun numMap(m: Any?): Map<String, Number>? = (m as? Map<*, *>)
            ?.mapNotNull { (key, v) -> (key as? String)?.let { s -> (v as? Number)?.let { s to it } } }?.toMap()
        // 경로마다 기본값 위에 덮는다(서버 mergeVoyages 와 같은 규칙).
        val voyages = d.voyages.toMutableMap()
        (snap.get("voyages") as? Map<*, *>)?.forEach { (k, v) ->
            val id = k as? String ?: return@forEach
            val m = v as? Map<*, *> ?: return@forEach
            val base = voyages[id] ?: Route(0, emptyMap())
            voyages[id] = Route(
                cost = (m["cost"] as? Number)?.toInt() ?: base.cost,
                weights = base.weights + (numMap(m["weights"])?.mapValues { it.value.toDouble() } ?: emptyMap()),
            )
        }
        return Config(
            questStardust = int("questStardust", d.questStardust),
            bonusStardust = int("bonusStardust", d.bonusStardust),
            keyCost = int("keyCost", d.keyCost),
            boxCost = int("boxCost", d.boxCost),
            voyages = voyages,
            dupRefund = d.dupRefund + (numMap(snap.get("dupRefund"))?.mapValues { it.value.toInt() } ?: emptyMap()),
            maxVoyage = int("maxVoyage", d.maxVoyage),
            maxExchange = int("maxExchange", d.maxExchange),
            adStardust = int("adStardust", d.adStardust),
            adDailyCap = int("adDailyCap", d.adDailyCap),
            adCooldownSec = int("adCooldownSec", d.adCooldownSec),
            hintTargets = (snap.get("hintTargets") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
        )
    }

    internal fun parseEquips(snap: DocumentSnapshot): Equips =
        if (!snap.exists()) Equips()
        else Equips(
            frame = snap.getString("frame")?.takeIf { it.isNotBlank() },
            badge = snap.getString("badge")?.takeIf { it.isNotBlank() },
            title = snap.getString("title")?.takeIf { it.isNotBlank() },
        )

    /** 다른 사람(또는 나)의 장착 상태 구독 — 프로필 화면용. 반환값으로 해제. */
    fun watchEquips(uid: String, onChange: (Equips) -> Unit): ListenerRegistration =
        db().collection(EQUIPS).document(uid).addSnapshotListener { snap, _ -> if (snap != null) onChange(parseEquips(snap)) }

    /** 장착/해제(서버가 소유 확인). */
    suspend fun equip(slot: Slot, itemId: String?): OpResult =
        request("equip", buildMap { put("slot", slot.id); if (itemId != null) put("itemId", itemId) })

    /** 우주 항해 — [route] 경로로 [count] 번(서버가 별가루를 빼고 결과를 정한다). */
    suspend fun voyage(route: String, count: Int): OpResult = request("voyage", mapOf("route" to route, "count" to count))

    /** 열쇠 구매 — 별가루 [Config.keyCost] × [count]. */
    suspend fun buyKeys(count: Int): OpResult = request("exchange", mapOf("to" to "key", "count" to count))

    /** 광고를 끝까지 본 뒤 — 별가루 [Config.adStardust](하루 한도·간격은 서버가 센다). 받는 즉시 화면에 더한다. */
    suspend fun adReward(): OpResult = requestCredit("adReward", emptyMap(), config.adStardust)

    /**
     * 별가루를 받는 요청 — 서버가 처리하기 전에 [amount] 를 화면에 먼저 더해 두고([creditFloor]), 끝나면(성공·실패 모두) 걷는다.
     * 성공이면 그 사이 지갑 구독이 서버 값을 이미 가져왔고, 실패면 원래 값으로 돌아간다.
     */
    suspend fun requestCredit(op: String, args: Map<String, Any>, amount: Int): OpResult {
        creditFloor = stardust + amount.coerceAtLeast(0)
        crediting++
        val r = request(op, args)
        // 성공 결과의 잔액은 서버 확정 값 — 지갑 알림보다 요청 완료가 먼저 와도 바로 맞춘다.
        if (r is OpResult.Done) (r.result["stardust"] as? Number)?.toInt()?.let { if (it > serverStardust) serverStardust = it }
        if (--crediting <= 0) { crediting = 0; creditFloor = 0 }
        return r
    }

    /** 남은 옛 성운 상자를 별가루로(상자 값 그대로). 상자가 없으면 서버가 "already". */
    suspend fun convertBoxes(): OpResult = request("convertBoxes")

    /** 천체 망원경 사용 — [target] = 업적 id(굴절: ??? 업적 / 반사·전파: 히든 업적). */
    suspend fun useTelescope(itemId: String, target: String): OpResult =
        request("useItem", mapOf("itemId" to itemId, "target" to target))

    /** 가진 망원경 중 [mode](reveal / hint) 인 것, 효과가 약한 것부터(아껴 쓰게). */
    fun telescopes(mode: String): List<Item> =
        ownedOf("telescope").filter { it.payload["mode"] == mode }
            .sortedBy { (it.payload["level"] as? Number)?.toInt() ?: 0 }

    private fun parseItem(doc: DocumentSnapshot): Item? {
        val kind = doc.getString("kind") ?: return null
        val rarity = doc.getString("rarity")?.takeIf { it in RARITIES } ?: return null
        fun strMap(k: String) = (doc.get(k) as? Map<*, *>)
            ?.mapNotNull { (key, v) -> (key as? String)?.let { s -> (v as? String)?.let { s to it } } }?.toMap() ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val payload = (doc.get("payload") as? Map<String, Any?>) ?: emptyMap()
        return Item(
            doc.id, kind, rarity, strMap("name"), strMap("desc"), payload,
            (doc.get("order") as? Number)?.toInt() ?: 0, doc.getBoolean("active") ?: true,
        )
    }

    /** 모양·색 아이템의 정의를 [StarCatalog] 에 가르친다 — 아직 그 번호로 올린 별을 본 적 없어도 그릴 수 있게. */
    private fun teach(item: Item) {
        val p = item.payload
        when (item.kind) {
            "shape" -> StarCatalog.learnShape((p["type"] as? Number)?.toInt() ?: return, p["path"] as? String)
            "color" -> StarCatalog.learnColor(
                (p["index"] as? Number)?.toInt() ?: return,
                (p["colors"] as? List<*>)?.mapNotNull { it as? String },
            )
        }
    }

    /**
     * 서버에 요청을 보내고 결과를 기다린다(최대 [OP_TIMEOUT_MS]). 시간이 넘으면 [OpResult.Failed]("timeout") —
     * 요청은 이미 남아 있어 망이 돌아오면 서버가 처리하고, 지갑 구독으로 결과가 반영된다.
     */
    suspend fun request(op: String, args: Map<String, Any> = emptyMap()): OpResult {
        val uid = uid ?: return OpResult.Failed("offline")
        val ref = db().collection(StaryConfig.Collections.USERS).document(uid).collection(OPS).document()
        val body = args + mapOf("op" to op, "status" to "pending", "createdAt" to System.currentTimeMillis())
        ref.set(body).addOnFailureListener { Log.w(TAG, "요청 기록 실패 $op", it) }
        return withTimeoutOrNull(OP_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var reg: ListenerRegistration? = null
                reg = ref.addSnapshotListener { snap, e ->
                    if (!cont.isActive) return@addSnapshotListener
                    if (e != null) {
                        reg?.remove()
                        cont.resume(OpResult.Failed("server"))
                        return@addSnapshotListener
                    }
                    when (snap?.getString("status")) {
                        "done" -> {
                            reg?.remove()
                            @Suppress("UNCHECKED_CAST")
                            cont.resume(OpResult.Done((snap.get("result") as? Map<String, Any?>) ?: emptyMap()))
                        }
                        "error" -> {
                            reg?.remove()
                            cont.resume(OpResult.Failed(snap.getString("error") ?: "server"))
                        }
                    }
                }
                cont.invokeOnCancellation { reg?.remove() }
            }
        } ?: OpResult.Failed("timeout")
    }

    /** voyage 결과 → [Draw] 목록. */
    fun draws(result: Map<String, Any?>): List<Draw> =
        (result["results"] as? List<*>)?.mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            Draw(
                id = m["id"] as? String ?: return@mapNotNull null,
                rarity = m["rarity"] as? String ?: "common",
                kind = m["kind"] as? String ?: "",
                dup = m["dup"] as? Boolean ?: false,
                refund = (m["refund"] as? Number)?.toInt() ?: 0,
                keys = (m["keys"] as? Number)?.toInt() ?: 0,
            )
        } ?: emptyList()
}
