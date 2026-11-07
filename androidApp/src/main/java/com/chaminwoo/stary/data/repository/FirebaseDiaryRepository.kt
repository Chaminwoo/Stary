package com.chaminwoo.stary.data.repository

import androidx.compose.runtime.snapshotFlow
import com.chaminwoo.stary.data.staryFirestore
import android.util.Log
import com.chaminwoo.stary.core.designsystem.StarCatalog
import com.chaminwoo.stary.data.toDiaryOrNull
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 공용 [DiaryRepository] 인터페이스(commonMain)의 Android/Firestore 구현.
 * iOS 에서는 동일 인터페이스를 Firebase iOS SDK 등으로 별도 구현한다.
 */
class FirebaseDiaryRepository : DiaryRepository {

    private val diaries = staryFirestore.collection(StaryConfig.Collections.DIARIES)

    companion object {
        /**
         * 전체 다이어리 실시간 구독 상한.
         * 전 컬렉션을 무제한으로 받으면 다이어리가 늘수록 모든 기기가 전부 메모리에 들고
         * 변경마다 재읽기해 Firestore 비용·메모리·렌더 부담이 선형 증가한다.
         * 최신순 상한으로 잘라 가드(필요 시 추후 뷰포트/지오해시 쿼리로 대체).
         */
        private const val MAX_OBSERVED_DIARIES = 1000L
        /** 나를 대상으로 한 친구 공개 글 구독 상한(공개 상한과 따로 — 합집합 뒤에는 자르지 않는다). */
        private const val MAX_FRIEND_DIARIES = 300L
        private const val RETRY_MIN_MS = 3_000L
        private const val RETRY_MAX_MS = 60_000L
        private const val TAG = "DiaryRepo"

        /**
         * 스냅샷 콜백 전용 백그라운드 실행자.
         * executor 없이 등록하면 콜백이 메인 스레드에서 돌아, 변경 1건에도 최대 1000건
         * 리플렉션 매핑(toObject)이 메인 스레드를 막는다. trySend 는 스레드 안전.
         */
        private val listenerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "diary-snapshots").apply { isDaemon = true }
        }

        /** 새 별 번호(네트워크 없이 만든다) — 업로드 화면이 들고 있다가 다시 시도할 때도 같은 번호로 쓴다. */
        /** Firestore 문서 id 로 쓸 수 있는 모양인가 — 길이·문자 제한(경로 구분자 '/' 불가). 딥링크·알림 인자 방어용. */
        fun isSafeDocId(id: String?): Boolean =
            !id.isNullOrEmpty() && id.length <= 128 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        fun newId(): String = staryFirestore.collection(StaryConfig.Collections.DIARIES).document().id
    }

    // 전체 다이어리 실시간 조회 — 나만 보기는 본인만. 받은 순간이 아니라 **지금 로그인한 사용자** 기준으로 매번 다시 거른다
    // (예전엔 콜백 시점의 uid 로 걸러, 로그인 직후 uid 가 늦게 채워지면 내 나만 보기 별이 다음 변경까지 빠졌다).
    override fun observeAllDiaries(): Flow<List<Diary>> =
        combine(observeAllDocs(), snapshotFlow { GoogleAuthHelper.currentUserId }) { list, uid ->
            list.filter { it.visibilityType != "private" || it.userId == uid }
        }

    /**
     * 지도용 구독 = **세 리스너의 합집합**(id 로 중복 제거, 작성 시각 내림차순).
     *  1. 공개 글 최신 [MAX_OBSERVED_DIARIES]건  2. 내 글  3. 나를 대상으로 한 친구 공개 글 최신 [MAX_FRIEND_DIARIES]건.
     * 합집합 뒤에는 자르지 않는다(친구 글이 공개 글 상한에 밀려 사라지지 않게).
     * 로그인(비익명 세션 + 사용자 id)이 확인되기 전에는 구독하지 않고, 인증 상태 · 사용자 id 가 바뀔 때마다 다시 건다.
     * 첫 공개 목록이 오기 전에는 아무것도 내보내지 않는다(=불러오는 중). 로그아웃되면 빈 목록을 한 번 내보낸다.
     */
    private fun observeAllDocs(): Flow<List<Diary>> = callbackFlow {
        val union = MapUnion(this)
        union.update()
        val auth = FirebaseAuth.getInstance()
        // addAuthStateListener 는 등록 즉시 1회 + 이후 상태 변경마다 호출된다.
        val authListener = FirebaseAuth.AuthStateListener { union.update() }
        auth.addAuthStateListener(authListener)
        // 사용자 id(Google sub)는 Firebase 로그인이 끝난 **뒤에** 채워진다 — 그때 다시 건다.
        launch(Dispatchers.Main) {
            snapshotFlow { GoogleAuthHelper.currentUserId }.distinctUntilChanged().collect { union.update() }
        }
        awaitClose {
            auth.removeAuthStateListener(authListener)
            union.close()
        }
    }

    /**
     * [observeAllDocs] 의 구독 묶음. 상태는 모두 [lock] 안에서만 바꾼다(스냅샷은 전용 스레드, 인증 변경은 메인 스레드에서 온다).
     * 스냅샷 콜백은 처리되지 않은 예외가 앱을 끄므로 본문 전체를 try/catch 로 감싼다.
     */
    private inner class MapUnion(private val scope: ProducerScope<List<Diary>>) {
        private val lock = Any()
        private var registrations = ArrayList<ListenerRegistration>()
        private var generation = 0
        private var subscribedKey: String? = null
        private var everSubscribed = false
        private var retryJob: Job? = null
        private var retryDelayMs = RETRY_MIN_MS
        private var publicDocs: List<Diary> = emptyList()
        private var mineDocs: List<Diary> = emptyList()
        private var friendDocs: List<Diary> = emptyList()
        private var publicLoaded = false

        fun update(force: Boolean = false) {
            synchronized(lock) {
                val fbUser = FirebaseAuth.getInstance().currentUser
                val me = GoogleAuthHelper.currentUserId
                val key = if (fbUser == null || fbUser.isAnonymous || me == null) null else "${fbUser.uid}|$me"
                if (!force && key == subscribedKey && (key == null || registrations.isNotEmpty())) return
                clearLocked()
                subscribedKey = key
                val gen = generation
                if (key == null || me == null) {
                    // 로그인 전: 구독하지 않는다. 이전에 구독하던 계정이었다면 목록을 비운다.
                    retryDelayMs = RETRY_MIN_MS
                    if (everSubscribed) scope.trySend(emptyList())
                    everSubscribed = false
                    return
                }
                everSubscribed = true
                listenPublic(gen, legacy = false)
                listenMine(gen, me)
                listenFriends(gen, me)
            }
        }

        fun close() {
            synchronized(lock) { clearLocked(); subscribedKey = null }
        }

        private fun clearLocked() {
            registrations.forEach { it.remove() }
            registrations = ArrayList()
            retryJob?.cancel()
            generation++
            publicDocs = emptyList(); mineDocs = emptyList(); friendDocs = emptyList()
            publicLoaded = false
        }

        private fun emitMergedLocked() {
            if (!publicLoaded) return
            val byId = LinkedHashMap<String, Diary>()
            for (d in publicDocs) byId[d.id] = d
            for (d in friendDocs) byId[d.id] = d
            for (d in mineDocs) byId[d.id] = d
            scope.trySend(byId.values.sortedByDescending { it.createdAt })
        }

        private fun decode(snapshot: QuerySnapshot?): List<Diary> {
            val now = System.currentTimeMillis()
            return snapshot?.documents?.mapNotNull { it.toDiaryOrNull(forMap = true, nowMs = now) } ?: emptyList()
        }

        private fun scheduleRetry(gen: Int) {
            synchronized(lock) {
                if (generation != gen) return
                retryJob?.cancel()
                val wait = retryDelayMs
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
                retryJob = scope.launch(Dispatchers.Main) {
                    delay(wait)
                    update(force = true)
                }
            }
        }

        // lock 안에서 호출
        private fun listenPublic(gen: Int, legacy: Boolean) {
            val query = if (legacy) {
                diaries.orderBy("createdAt", Query.Direction.DESCENDING).limit(MAX_OBSERVED_DIARIES)
            } else {
                diaries.whereEqualTo("visibilityType", "public")
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(MAX_OBSERVED_DIARIES)
            }
            registrations += query.addSnapshotListener(listenerExecutor) { snapshot, error ->
                try {
                    if (error != null) {
                        if (!legacy && error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                            // 인덱스가 아직 없다 — 예전 전체 쿼리로 대신한다.
                            synchronized(lock) { if (generation == gen) listenPublic(gen, legacy = true) }
                        } else {
                            scheduleRetry(gen)
                        }
                        return@addSnapshotListener
                    }
                    val list = decode(snapshot)
                    synchronized(lock) {
                        if (generation != gen) return@addSnapshotListener
                        retryDelayMs = RETRY_MIN_MS
                        publicDocs = list
                        publicLoaded = true
                        emitMergedLocked()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "공개 목록 처리 실패: ${e.javaClass.simpleName}")
                }
            }
        }

        private fun listenMine(gen: Int, me: String) {
            registrations += diaries.whereEqualTo("userId", me)
                .addSnapshotListener(listenerExecutor) { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleRetry(gen)
                            return@addSnapshotListener
                        }
                        val list = decode(snapshot)
                        synchronized(lock) {
                            if (generation != gen) return@addSnapshotListener
                            mineDocs = list
                            emitMergedLocked()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "내 글 처리 실패: ${e.javaClass.simpleName}")
                    }
                }
        }

        private fun listenFriends(gen: Int, me: String) {
            registrations += diaries.whereEqualTo("visibilityType", "friends")
                .whereArrayContains("audience", me)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_FRIEND_DIARIES)
                .addSnapshotListener(listenerExecutor) { snapshot, error ->
                    try {
                        // 실패(인덱스 없음 등)는 빈 목록 — 지도가 죽지 않게.
                        val list = if (error != null) emptyList() else decode(snapshot)
                        synchronized(lock) {
                            if (generation != gen) return@addSnapshotListener
                            friendDocs = list
                            emitMergedLocked()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "친구 글 처리 실패: ${e.javaClass.simpleName}")
                    }
                }
        }
    }

    // 다이어리 저장
    override suspend fun saveDiary(diary: Diary): Boolean {
        return try {
            // 번호가 정해져 오면 그 번호로 쓴다 — 연결이 약해 다시 시도해도(첫 시도는 기기에 쌓여 있다가 나중에 올라간다)
            // 별이 두 개가 되지 않게. 번호가 없으면 새로 만든다.
            val docRef = if (diary.id.isNotBlank()) diaries.document(diary.id) else diaries.document()
            val withMeta = diary.copy(
                id = docRef.id,
                createdAt = if (diary.createdAt == 0L) System.currentTimeMillis() else diary.createdAt,
                // 앱에 없는 모양·색(DB 정의)이면 그릴 정보를 함께 싣는다 — 다른 버전 앱도 그대로 그리게(StarCatalog).
                starPath = diary.starPath.ifBlank { StarCatalog.shapeSvg(diary.starType).orEmpty() },
                starColors = diary.starColors.ifEmpty { StarCatalog.colorHexes(diary.starColor).orEmpty() },
            )
            docRef.set(withMeta).await()
            // fire-and-forget: 친구 알림 생성이 저장 완료 이벤트(토스트/화면전환)를 막지 않게 분리
            CoroutineScope(Dispatchers.IO).launch { notifyFriendsOfPost(withMeta) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 업로드 성공 시 친구들에게 FRIEND_POST 인앱 알림 생성 (실패해도 저장엔 영향 없음).
     * 공개 범위: 전체/친구 공개 → 친구에게만, **나만 보기 → 아무에게도 알리지 않는다**(iOS UploadScreen 과 동일,
     * 서버 푸시 notifyFriendsOnDiaryCreate 도 같은 규칙). 전체 공개 첫 별의 전체 공지는 서버(announceFirstStar) 담당.
     */
    private suspend fun notifyFriendsOfPost(diary: Diary) {
        if (diary.userId.isBlank()) return
        if (diary.visibilityType == "private") return
        try {
            val friendIds = staryFirestore
                .collection(StaryConfig.Collections.USERS)
                .document(diary.userId)
                .collection(StaryConfig.Collections.FRIENDS)
                .get().await()
                .documents.map { it.id }
            FirebaseNotificationRepository().notifyFriendPost(
                actorId = diary.userId,
                actorName = diary.userName,
                diaryId = diary.id,
                diaryTitle = diary.title,
                friendIds = friendIds
            )
        } catch (_: Exception) {}
    }

    // 다이어리 삭제
    override suspend fun deleteDiary(diaryId: String): Boolean {
        return try {
            diaries.document(diaryId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getDiaryById(diaryId: String): Diary? {
        // 문서 id 에 '/' 가 있으면 document() 가 다른 경로를 가리키거나 예외를 던진다 — 형식이 아니면 없는 글로 본다.
        if (!isSafeDocId(diaryId)) return null
        return try {
            diaries.document(diaryId).get().await().toDiaryOrNull()
        } catch (e: Exception) {
            null // 거부(권한)·네트워크 모두 "못 찾음"
        }
    }

    override fun observeMyDiaries(userId: String): Flow<List<Diary>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        // userId 동등 필터만 서버에서 수행하고 정렬은 클라이언트에서 처리한다.
        // (userId + createdAt 복합 인덱스를 새 DB 에 따로 만들 필요 없게 — 내 글은 소량이라 부담 없음)
        // ⚠️ 내 id 일 때만 쓴다. 남의 글은 [observeUserDiaries].
        val listener = diaries
            .whereEqualTo("userId", userId)
            .addSnapshotListener(listenerExecutor) { snapshot, error ->
                try {
                    if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                    val list = snapshot?.documents?.mapNotNull { it.toDiaryOrNull() }
                        ?.sortedByDescending { it.createdAt } ?: emptyList()
                    trySend(list)
                } catch (e: Exception) {
                    Log.w(TAG, "내 글 처리 실패: ${e.javaClass.simpleName}")
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * **남의** 글 목록 — "공개" + "나를 대상으로 한 친구 공개" 두 쿼리의 합집합(작성 시각 내림차순).
     * 나만 보기는 서버 규칙이 막으므로 아예 묻지 않는다. 친구 쿼리 실패(인덱스 없음 · 거부)는 그 몫만 빈 목록.
     * 정렬은 클라이언트에서 한다(복합 인덱스 없이 동작).
     */
    override fun observeUserDiaries(userId: String): Flow<List<Diary>> = callbackFlow {
        val me = GoogleAuthHelper.currentUserId
        if (userId.isBlank() || me == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val lock = Any()
        var publicDocs: List<Diary> = emptyList()
        var friendDocs: List<Diary> = emptyList()
        var publicReady = false
        fun emitLocked() {
            if (!publicReady) return
            val byId = LinkedHashMap<String, Diary>()
            for (d in publicDocs) byId[d.id] = d
            for (d in friendDocs) byId[d.id] = d
            trySend(byId.values.sortedByDescending { it.createdAt })
        }
        val base = diaries.whereEqualTo("userId", userId)
        val pub = base.whereEqualTo("visibilityType", "public")
            .addSnapshotListener(listenerExecutor) { snapshot, error ->
                try {
                    val list = if (error != null) emptyList() else snapshot?.documents?.mapNotNull { it.toDiaryOrNull() } ?: emptyList()
                    synchronized(lock) { publicDocs = list; publicReady = true; emitLocked() }
                } catch (e: Exception) {
                    Log.w(TAG, "공개 글 처리 실패: ${e.javaClass.simpleName}")
                }
            }
        val fri = base.whereEqualTo("visibilityType", "friends").whereArrayContains("audience", me)
            .addSnapshotListener(listenerExecutor) { snapshot, error ->
                try {
                    val list = if (error != null) emptyList() else snapshot?.documents?.mapNotNull { it.toDiaryOrNull() } ?: emptyList()
                    synchronized(lock) { friendDocs = list; emitLocked() }
                } catch (e: Exception) {
                    Log.w(TAG, "친구 글 처리 실패: ${e.javaClass.simpleName}")
                }
            }
        awaitClose { pub.remove(); fri.remove() }
    }

    /**
     * [userId] 가 **가장 최근에 올린, 내가 볼 수 있는** 별 1개를 실시간 구독(없으면 null).
     * 친구 목록 행 우측의 "최근 별" 표시용(체크리스트 34-6).
     *
     * 쿼리: 공개 + (나를 대상으로 한) 친구 공개 두 개의 합집합. 나만 보기는 묻지 않는다.
     * 정렬+limit(적은 읽기)이 1순위지만 이 경로엔 복합 인덱스가 필요하다. 인덱스가 없으면(첫 배포 등)
     * 에러 시 **정렬 없는 쿼리 + 클라 정렬**로 자동 폴백한다(=인덱스 없어도 동작, 있으면 저렴).
     * 친구 쿼리 실패는 그 몫만 비운다.
     */
    fun observeLatestVisibleDiaryOf(userId: String): Flow<Diary?> = callbackFlow {
        val me = GoogleAuthHelper.currentUserId
        if (userId.isBlank() || me == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val lock = Any()
        val regs = ArrayList<ListenerRegistration>()
        var publicLatest: Diary? = null
        var friendLatest: Diary? = null
        var publicReady = false
        fun emitLocked() {
            if (!publicReady) return
            trySend(listOfNotNull(publicLatest, friendLatest).maxByOrNull { it.createdAt })
        }

        fun listen(base: Query, onResult: (Diary?) -> Unit) {
            fun attach(query: Query, fallbackToUnordered: Boolean) {
                val reg = query.addSnapshotListener(listenerExecutor) { snapshot, error ->
                    try {
                        if (error != null) {
                            if (fallbackToUnordered) {
                                synchronized(lock) { attach(base, false) }
                            } else {
                                onResult(null)
                            }
                            return@addSnapshotListener
                        }
                        val latest = snapshot?.documents
                            ?.mapNotNull { it.toDiaryOrNull() }
                            ?.filter { it.visibilityType != "private" }
                            ?.maxByOrNull { it.createdAt }
                        onResult(latest)
                    } catch (e: Exception) {
                        Log.w(TAG, "최근 별 처리 실패: ${e.javaClass.simpleName}")
                    }
                }
                regs += reg
            }
            synchronized(lock) {
                attach(base.orderBy("createdAt", Query.Direction.DESCENDING).limit(6), true)
            }
        }

        val base = diaries.whereEqualTo("userId", userId)
        listen(base.whereEqualTo("visibilityType", "public")) { d ->
            synchronized(lock) { publicLatest = d; publicReady = true; emitLocked() }
        }
        listen(base.whereEqualTo("visibilityType", "friends").whereArrayContains("audience", me)) { d ->
            synchronized(lock) { friendLatest = d; emitLocked() }
        }
        awaitClose { synchronized(lock) { regs.forEach { it.remove() } } }
    }

    override suspend fun incrementViewCount(diaryId: String) {
        try {
            diaries.document(diaryId).update("viewCount", FieldValue.increment(1)).await()
        } catch (_: Exception) {}
    }

    override suspend fun prefetchDiaries(diaryIds: List<String>) {
        diaryIds.forEach { id ->
            if (!isSafeDocId(id) || DiaryCache.get(id) != null) return@forEach
            try {
                diaries.document(id).get().await().toDiaryOrNull()?.let { DiaryCache.put(it) }
            } catch (_: Exception) {} // 거부·네트워크는 건너뜀
        }
    }

    override suspend fun updateDiary(diary: Diary): Boolean {
        return try {
            diaries.document(diary.id).update(
                mapOf(
                    "title" to diary.title,
                    "content" to diary.content,
                    "visibilityType" to diary.visibilityType, // 공개 범위도 수정 가능(전체공개/친구만/나만보기)
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
