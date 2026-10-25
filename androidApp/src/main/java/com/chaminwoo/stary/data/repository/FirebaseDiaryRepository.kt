package com.chaminwoo.stary.data.repository

import androidx.compose.runtime.snapshotFlow
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.core.designsystem.StarCatalog
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.DiaryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
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

        /**
         * 스냅샷 콜백 전용 백그라운드 실행자.
         * executor 없이 등록하면 콜백이 메인 스레드에서 돌아, 변경 1건에도 최대 1000건
         * 리플렉션 매핑(toObject)이 메인 스레드를 막는다. trySend 는 스레드 안전.
         */
        private val listenerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "diary-snapshots").apply { isDaemon = true }
        }

        /** 새 별 번호(네트워크 없이 만든다) — 업로드 화면이 들고 있다가 다시 시도할 때도 같은 번호로 쓴다. */
        fun newId(): String = staryFirestore.collection(StaryConfig.Collections.DIARIES).document().id
    }

    // 전체 다이어리 실시간 조회 — 나만 보기는 본인만. 받은 순간이 아니라 **지금 로그인한 사용자** 기준으로 매번 다시 거른다
    // (예전엔 콜백 시점의 uid 로 걸러, 로그인 직후 uid 가 늦게 채워지면 내 나만 보기 별이 다음 변경까지 빠졌다).
    override fun observeAllDiaries(): Flow<List<Diary>> =
        combine(observeAllDocs(), snapshotFlow { GoogleAuthHelper.currentUserId }) { list, uid ->
            list.filter { it.visibilityType != "private" || it.userId == uid }
        }

    private fun observeAllDocs(): Flow<List<Diary>> = callbackFlow {
        // 앱 시작 시 지도는 로그인 전(auth==null)에 미리 렌더된다. 그때 첫 구독이 시작되면
        // 보안 규칙(request.auth!=null)에 막혀 PERMISSION_DENIED 로 Firestore 리스너가 죽고,
        // 로그인 후에도 스스로 복구되지 않아 지도에 다이어리가 안 뜬다.
        // → auth 상태가 바뀔 때마다(로그인/익명 완료) 리스너를 다시 건다.
        var registration: ListenerRegistration? = null

        fun subscribe() {
            registration?.remove()
            registration = diaries
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_OBSERVED_DIARIES)
                .addSnapshotListener(listenerExecutor) { snapshot, error ->
                    if (error != null) {
                        if (FirebaseAuth.getInstance().currentUser == null) {
                            // 로그아웃 순간의 권한 거부 — 아무것도 안 보내면 이전 계정의 목록(나만 보기 포함)이 그대로 남았다.
                            trySend(emptyList())
                        } else {
                            // 로그인 중인데 끊김(드문 일시 오류) — 끊긴 리스너는 스스로 살아나지 않으니 잠시 뒤 다시 건다.
                            launch(Dispatchers.Main) { delay(3_000); subscribe() }
                        }
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = doc.id)
                    } ?: emptyList()
                    trySend(list)
                }
        }

        subscribe()
        val auth = FirebaseAuth.getInstance()
        // addAuthStateListener 는 등록 즉시 1회 + 이후 상태 변경마다 호출된다.
        val authListener = FirebaseAuth.AuthStateListener { subscribe() }
        auth.addAuthStateListener(authListener)

        awaitClose {
            registration?.remove()
            auth.removeAuthStateListener(authListener)
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
        return try {
            val doc = diaries.document(diaryId).get().await()
            doc.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    override fun observeMyDiaries(userId: String): Flow<List<Diary>> = callbackFlow {
        // userId 동등 필터만 서버에서 수행하고 정렬은 클라이언트에서 처리한다.
        // (userId + createdAt 복합 인덱스를 새 DB 에 따로 만들 필요 없게 — 내 글은 소량이라 부담 없음)
        val listener = diaries
            .whereEqualTo("userId", userId)
            .addSnapshotListener(listenerExecutor) { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * [userId] 가 **가장 최근에 올린, 내가 볼 수 있는** 별 1개를 실시간 구독(없으면 null).
     * 친구 목록 행 우측의 "최근 별" 표시용(체크리스트 34-6).
     *
     * 필터:
     *  - `private`(나만보기)는 남에게 보이면 안 되므로 제외(`friends` 는 친구 목록에서 보는 것이므로 포함).
     *  - 익명 다이어리 제외 — 이름 옆에 그 별을 띄우면 익명성이 깨진다.
     *
     * 쿼리: 정렬+limit(적은 읽기)이 1순위지만 이 경로엔 **복합 인덱스(userId, createdAt desc)** 가 필요하다.
     * 인덱스가 없으면(첫 배포 등) 리스너가 에러로 죽어 별이 아예 안 뜨므로,
     * 에러 시 **정렬 없는 쿼리 + 클라 정렬**로 자동 폴백한다(=인덱스 없어도 동작, 있으면 저렴).
     */
    fun observeLatestVisibleDiaryOf(userId: String): Flow<Diary?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        fun visible(d: Diary): Boolean = d.visibilityType != "private"

        var fallback: ListenerRegistration? = null
        // 최신 몇 개만 받아 그중 "보이는" 첫 별을 고른다(맨 앞이 private/익명일 수 있어 여유분).
        val primary = diaries
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(6)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (fallback == null) {
                        fallback = diaries
                            .whereEqualTo("userId", userId)
                            .addSnapshotListener { snap2, err2 ->
                                if (err2 != null) return@addSnapshotListener
                                val latest = snap2?.documents
                                    ?.mapNotNull { d -> d.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = d.id) }
                                    ?.filter(::visible)
                                    ?.maxByOrNull { it.createdAt }
                                trySend(latest)
                            }
                    }
                    return@addSnapshotListener
                }
                val latest = snapshot?.documents
                    ?.mapNotNull { d -> d.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = d.id) }
                    ?.filter(::visible)
                    ?.maxByOrNull { it.createdAt }
                trySend(latest)
            }
        awaitClose {
            primary.remove()
            fallback?.remove()
        }
    }

    override suspend fun incrementViewCount(diaryId: String) {
        try {
            diaries.document(diaryId).update("viewCount", FieldValue.increment(1)).await()
        } catch (_: Exception) {}
    }

    override suspend fun prefetchDiaries(diaryIds: List<String>) {
        diaryIds.forEach { id ->
            if (DiaryCache.get(id) != null) return@forEach
            try {
                val doc = diaries.document(id).get().await()
                doc.toObject(Diary::class.java)?.also(StarCatalog::learn)?.copy(id = doc.id)?.let { DiaryCache.put(it) }
            } catch (_: Exception) {}
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
