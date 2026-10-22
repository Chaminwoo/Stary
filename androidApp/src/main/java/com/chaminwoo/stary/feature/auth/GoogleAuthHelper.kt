package com.chaminwoo.stary.feature.auth

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.BuildConfig
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.core.util.NicknameStore
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    // 하드코딩 금지: secrets.properties -> BuildConfig 로 주입.
    // TODO: secrets.properties 에 GOOGLE_WEB_CLIENT_ID 실제 값 설정 (커밋 금지)
    private val WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    /**
     * 앱 사용자 id(Google sub). Compose 상태라 화면이 읽으면 바뀔 때 다시 그려진다 — 로그인 때 Firebase Auth 가 끝난 **뒤에**
     * 채워지므로(signInWithGoogle), 인증 상태 리스너만 보던 곳은 이 값을 따로 관찰해야 한다(MainListScreen · AdFreeAccount).
     */
    var currentUserId: String? by androidx.compose.runtime.mutableStateOf(null)
    /**
     * 화면 표시용 내 이름 — 서버를 읽기 전엔 **임시값**(기기에 남은 닉네임 또는 구글 이름)일 수 있다.
     * 다른 사람에게 나가는 문서(별·댓글·좋아요·친구 요청·채팅·선점)에 이름을 박을 땐 쓰지 말고 [requireConfirmedName] 을 쓴다
     * — 기기 첫 실행(재설치) 직후엔 이 값이 구글 기본 이름이라 설정해 둔 닉네임 대신 구글 이름이 퍼졌다(CLAUDE.md §2.6).
     */
    var currentUserName: String? = null
    /**
     * 내 프로필 사진 — **서버 `users/{uid}.profileImageUrl` 확정값**(사진을 정한 적 없다고 확인되면 [googlePhotoUrl]).
     * 서버를 읽기 전엔 null — 구글 사진을 먼저 넣지 않는다(재설치 직후 구글 사진이 뜨던 원인, CLAUDE.md §2.6).
     */
    var currentUserPhotoUrl: String? = null
    /** 구글 계정 사진 — 프로필 사진을 정하기 전에만 쓴다. */
    var googlePhotoUrl: String? = null
    var currentUserEmail: String? = null  // 어드민 판정용(히든 업적 선점 제외)
    /** 구글 계정 이름 — 서버에 닉네임이 없다고 **확인됐을 때만** 쓴다(처음 가입). */
    private var googleName: String? = null

    /** 서버에서 확인한 내 닉네임(`users/{uid}.userName`). null = 아직 모름(읽기 전/실패). */
    private val _confirmedName = MutableStateFlow<String?>(null)
    val confirmedName: StateFlow<String?> = _confirmedName.asStateFlow()

    /**
     * 이번 로그인의 계정이 **이전에 가입했던 계정**인지 — 서버 `users/{uid}` 를 처음 읽었을 때 닉네임/사진이 이미 있었으면 true.
     * null = 아직 모름(읽기 전/실패). 로그인(세션 복원 포함)마다 **첫 서버 읽기에서 한 번만** 정한다 — 그 뒤 재시도 때는
     * 방금 이 기기가 쓴 프로필 때문에 새 계정도 "이미 있음"으로 보이므로 바꾸지 않는다.
     * 기기 첫 실행(재설치)이라도 이미 가입한 계정이면 코치마크·웰컴 별·화면 안내를 건너뛰는 데 쓴다(MainScreen).
     */
    private val _returningAccount = MutableStateFlow<Boolean?>(null)
    val returningAccount: StateFlow<Boolean?> = _returningAccount.asStateFlow()

    private var appContext: Context? = null
    private var profileSyncJob: Job? = null
    private var profileSyncUid: String? = null
    /** 서버 프로필을 못 읽어 아직 동기화가 안 된 상태 — 앱 복귀 때 [retryProfileSyncIfNeeded] 가 다시 읽는다. */
    @Volatile private var profileSyncPending = false

    /**
     * 마지막 구글 로그인 실패 사유(사람이 읽을 수 있는 한 줄).
     *
     * ⚠️ 예전엔 [signInWithGoogle] 이 예외를 통째로 삼키고 `null` 만 돌려줘, 화면에는
     *    "잠시만 기다려주세요" 토스트만 떴다. 실기기(Play 설치본)는 logcat 을 붙이기 전엔
     *    원인을 볼 방법이 아예 없어서, 서명 SHA-1 미등록 하나를 찾는 데 USB 디버깅까지 필요했다.
     *    (실제 원인은 Play 앱 서명 키 지문 미등록 → `[28444] Developer console is not set up correctly.`)
     */
    var lastSignInError: String? = null
        private set

    suspend fun signInWithGoogle(context: Context): String? {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lastSignInError = null
        return try {
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                // ⚠️ currentUserId 는 아래 Firebase Auth 로그인이 **끝난 뒤에** 넣는다(2026-10-22). 먼저 넣으면 그 사이
                //    화면이 다시 그려지며 users/{uid}·diaries 등 구독이 인증 없이 시작돼 PERMISSION_DENIED 로 죽고 다시 붙지 않았다
                //    (새 계정 첫 로그인: 퀘스트 무한 로딩 · 지도 별 목록 빔 · 첫 사용 안내 멈춤).
                val signedInUid = getUserIdFromToken(idToken)
                googleName = googleIdTokenCredential.displayName
                currentUserName = googleName
                _confirmedName.value = null
                _returningAccount.value = null
                googlePhotoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                // 내 사진은 서버(users/{uid}.profileImageUrl) 확정값만 — 아래 서버 읽기 전엔 비워 둔다(구글 사진을 먼저 싣지 않게).
                currentUserPhotoUrl = null
                // Firebase Auth 에도 로그인 — Firestore/Storage 보안 규칙(request.auth != null)이
                // 통과하려면 필수. 실패해도 앱 흐름은 막지 않는다.
                try {
                    FirebaseAuth.getInstance()
                        .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                        .await()
                    currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Auth 로그인 실패: ${e.localizedMessage}")
                    // 계정 선택은 됐는데 Firebase 로그인만 실패 — 이후 저장이 규칙에 막힌다. 얼마나 생기는지 본다.
                    com.chaminwoo.stary.core.util.StaryAnalytics.log(
                        "firebase_auth_fail", "reason" to e::class.java.simpleName,
                    )
                }
                currentUserId = signedInUid
                com.chaminwoo.stary.core.util.StaryAnalytics.log("login_success", "method" to "google")
                // 친구 검색이 가능하도록 공개 프로필(users/{uid}) 확정·기록 — startProfileSync 주석 참고.
                // fire-and-forget: Firestore 쓰기가 지연/실패해도 로그인 흐름을 막지 않는다.
                val appCtx = context.applicationContext
                appContext = appCtx
                currentUserId?.let { uid ->
                    // 화면 표시용 임시 이름 — 이 기기에 남은 닉네임(서버 확인값만 저장된다)이 구글 이름보다 정확하다.
                    NicknameStore.get(appCtx, uid)?.let { currentUserName = it }
                    val sync = startProfileSync(appCtx, uid)
                    CoroutineScope(Dispatchers.IO).launch {
                        sync.join()
                        // 삭제 예약이 걸려 있으면 재로그인으로 취소(7일 유예 정책).
                        cancelPendingDeletion(uid)
                        // FCM 토큰 + authUid 저장 — 친구 새 글 푸시(Cloud Functions) 발송 대상 / 서버 계정삭제용
                        syncFcmToken(uid)
                    }
                }
                idToken
            } else {
                lastSignInError = "예상과 다른 자격증명 타입(${credential.type})"
                Log.e(TAG, "구글 로그인 실패: $lastSignInError")
                com.chaminwoo.stary.core.util.StaryAnalytics.log("login_fail", "method" to "google", "reason" to "credential_type")
                null
            }
        } catch (e: Exception) {
            lastSignInError = describeSignInFailure(context, e)
            Log.e(TAG, "구글 로그인 실패: $lastSignInError", e)
            com.chaminwoo.stary.core.util.StaryAnalytics.log(
                "login_fail", "method" to "google",
                "reason" to when (e) {
                    is GetCredentialCancellationException -> "canceled"
                    is NoCredentialException -> "no_credential"
                    is GetCredentialException -> e.type.substringAfterLast('.')
                    else -> e::class.java.simpleName
                },
            )
            null
        }
    }

    /**
     * Credential Manager 실패 예외 → 화면에 띄울 한 줄 설명.
     *
     * [GetCredentialException.type] 이 핵심 단서다.
     * - `TYPE_NO_CREDENTIAL` : 쓸 수 있는 구글 계정이 없거나, **이 빌드의 서명 지문이 Firebase /
     *   Google Cloud 에 등록돼 있지 않아** 구글이 계정을 하나도 내주지 않는 상태.
     *   → 로컬 빌드는 되는데 **Play 설치본만** 실패하면 업로드 키가 아니라 **Play 앱 서명 키**의
     *     SHA-1/SHA-256 미등록이 1순위 원인(Play 가 AAB 를 자기 키로 재서명한다).
     * - `TYPE_USER_CANCELED` : 사용자가 시트를 닫음(정상).
     * - 그 밖(`TYPE_UNKNOWN`/`INTERRUPTED`) : 네트워크·Play 서비스 문제.
     */
    private fun describeSignInFailure(context: Context, e: Exception): String = when (e) {
        is GetCredentialCancellationException -> context.getString(com.chaminwoo.stary.R.string.login_canceled)
        // 괄호 안 진단(서명 지문/계정 없음)은 개발 확인용이라 번역하지 않는다.
        is NoCredentialException ->
            context.getString(com.chaminwoo.stary.R.string.login_no_account) +
                " (app signing fingerprint not registered, or no Google account on device)"
        is GetCredentialException -> "구글 로그인 실패 [${e.type}] ${e.errorMessage ?: ""}"
        else -> "구글 로그인 실패 [${e::class.java.simpleName}] ${e.localizedMessage ?: ""}"
    }

    /**
     * 앱 재시작 시 로그인 세션 복원.
     * FirebaseAuth 세션은 디스크에 영속되지만 [currentUserId](=Google sub)/이름/사진은 메모리 var 라
     * 프로세스가 재생성되면 null 이 된다 → 로그인 화면이 다시 떠 "로그인 유지가 안 되는" 것처럼 보였다.
     * 영속된 FirebaseUser 의 google.com providerData(uid=Google sub) 에서 식별자를 복원한다.
     * @return 복원 성공(=로그인 유지) 시 true.
     */
    fun restoreSession(context: Context): Boolean {
        if (currentUserId != null) return true
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val uid = (google?.uid ?: user.uid).takeIf { it.isNotBlank() } ?: return false
        currentUserId = uid
        googleName = google?.displayName ?: user.displayName
        currentUserName = googleName // 표시용 임시값 — 이어지는 applyStoredNickname/서버 확정으로 바뀐다
        _confirmedName.value = null
        _returningAccount.value = null
        googlePhotoUrl = (google?.photoUrl ?: user.photoUrl)?.toString()
        currentUserPhotoUrl = null // 서버 값(아래)으로만 채운다 — 구글 사진을 먼저 싣지 않게
        currentUserEmail = user.email
        val appCtx = context.applicationContext
        appContext = appCtx
        // 앱에서 바꾼 닉네임/프로필 사진을 서버에서 확정한다(예전엔 기본 get() 이라 캐시 응답을 믿었다).
        val sync = startProfileSync(appCtx, uid)
        // 앱 재시작(세션 복원)도 "로그인"으로 보고 삭제 예약을 취소(7일 유예 정책).
        // ⚠️ FCM 토큰도 여기서 다시 기록한다 — 예전엔 "구글 로그인 화면을 실제로 거친 순간"에만
        //    저장해서, 로그인이 유지되는 기기(대부분)는 토큰이 재발급돼도 갱신되지 않아
        //    푸시가 조용히 끊길 수 있었다(친구 새 글/친구 요청 알림 미수신 원인).
        CoroutineScope(Dispatchers.IO).launch {
            sync.join()
            cancelPendingDeletion(uid)
            syncFcmToken(uid)
        }

        return true
    }

    /**
     * `users/{uid}` 를 **서버에서** 읽어 내 닉네임/사진을 확정한다(로그인·세션 복원·앱 복귀 재시도 공용, 이미 도는 중이면 그 작업).
     *
     * ⚠️ **"읽기 실패" / "문서 없음" / "필드 없음"을 구분한다**(CLAUDE.md §2.6, iOS `AuthManager.ensureProfile` 동일).
     *  - 읽기는 반드시 [Source.SERVER] — 기본 get() 은 서버에 못 닿을 때 "쓰기 대기분만 있는 불완전한 캐시"를 성공으로 돌려줘,
     *    그걸 믿으면 진짜 닉네임·사진을 구글 기본값으로 덮어쓴다.
     *  - 읽기 성공: 서버 값 우선. **없다고 확인된 필드만** 구글 기본값으로 채워 merge 기록(처음 가입).
     *  - 읽기 실패: 서버·기기 캐시 둘 다 쓰지 않는다. 값이 **있는** 캐시(Firestore 로컬 캐시 / [NicknameStore] — 둘 다
     *    서버 확인값·본인 변경으로만 채워진다)만 믿고, 앱 복귀 때 [retryProfileSyncIfNeeded] 가 다시 읽는다.
     */
    @Synchronized
    private fun startProfileSync(appCtx: Context, uid: String): Job {
        profileSyncJob?.takeIf { it.isActive && profileSyncUid == uid }?.let { return it }
        profileSyncJob?.cancel()
        profileSyncUid = uid
        return CoroutineScope(Dispatchers.IO).launch {
            val (readOk, doc) = readServerProfile(uid)
            if (currentUserId != uid) return@launch // 그사이 로그아웃/계정 전환
            if (readOk) {
                val savedName = doc?.getString("userName")?.takeIf { it.isNotBlank() }
                val savedPhoto = doc?.getString("profileImageUrl")?.takeIf { it.isNotBlank() }
                // 첫 서버 읽기에서만 — 이 뒤 upsertProfile 이 채우면 새 계정도 "있음"이 된다.
                if (_returningAccount.value == null) _returningAccount.value = savedName != null || savedPhoto != null
                val name = savedName ?: googleName.orEmpty()
                val photo = savedPhoto ?: googlePhotoUrl
                currentUserName = name
                currentUserPhotoUrl = photo
                if (name.isNotBlank()) NicknameStore.set(appCtx, uid, name)
                _confirmedName.value = name
                profileSyncPending = false
                // 빠진 필드가 있을 때만 기록(merge) — 있는 값은 그대로 다시 쓰일 뿐 바뀌지 않는다.
                if (savedName == null || savedPhoto == null || doc?.getString("userId").isNullOrBlank()) {
                    FirebaseFriendRepository().upsertProfile(
                        UserProfile(userId = uid, userName = name, profileImageUrl = photo ?: "")
                    )
                }
            } else {
                Log.w(TAG, "공개 프로필 조회 최종 실패 — 기록하지 않고 앱 복귀 때 다시 시도")
                profileSyncPending = true
                val cached = runCatching {
                    staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
                        .get(Source.CACHE).await()
                }.getOrNull()
                val cachedName = cached?.getString("userName")?.takeIf { it.isNotBlank() }
                    ?: NicknameStore.get(appCtx, uid)
                cachedName?.let {
                    currentUserName = it
                    _confirmedName.value = it
                }
                cached?.getString("profileImageUrl")?.takeIf { it.isNotBlank() }?.let { currentUserPhotoUrl = it }
            }
        }.also { profileSyncJob = it }
    }

    /** users/{uid} 를 서버에서 읽는다(최대 3번, 2초·4초 간격). first = 읽기 성공 여부(문서가 없어도 성공). */
    private suspend fun readServerProfile(uid: String): Pair<Boolean, DocumentSnapshot?> {
        for (attempt in 0 until 3) {
            try {
                val doc = staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
                    .get(Source.SERVER).await()
                return true to doc
            } catch (e: Exception) {
                Log.w(TAG, "공개 프로필 조회 실패(${attempt + 1}/3): ${e.localizedMessage}")
                if (attempt < 2) delay(2000L * (attempt + 1))
            }
        }
        return false to null
    }

    /** 읽기 실패로 미뤄 둔 프로필 동기화를 다시 시도 — 앱 복귀(MainActivity.onResume) 때 부른다. */
    fun retryProfileSyncIfNeeded() {
        if (!profileSyncPending) return
        val uid = currentUserId ?: return
        val ctx = appContext ?: return
        startProfileSync(ctx, uid)
    }

    /**
     * 다른 사람에게 나가는 문서에 박을 내 이름 — **서버 확인값**. 아직 모르면 프로필을 다시 읽으며 최대 [timeoutMs] 기다린다.
     * 끝내 모르면 null(호출부는 그 동작을 취소) — 구글 기본 이름을 대신 쓰지 않는다. [notify] 면 "프로필 불러오는 중" 토스트.
     */
    suspend fun requireConfirmedName(timeoutMs: Long = 10_000, notify: Boolean = true): String? {
        _confirmedName.value?.let { return it }
        val uid = currentUserId ?: return null
        appContext?.let { startProfileSync(it, uid) }
        val name = withTimeoutOrNull(timeoutMs) { _confirmedName.filterNotNull().first() }
        if (name == null && notify) {
            appContext?.let {
                com.chaminwoo.stary.core.ui.StaryToast.show(
                    com.chaminwoo.stary.core.util.LocaleManager.wrap(it).getString(com.chaminwoo.stary.R.string.profile_not_ready)
                )
            }
        }
        return name
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            // ⚠️ 인증이 살아 있는 지금 이 기기 토큰을 떼어낸다 — 로그아웃 뒤엔 규칙에 막혀 못 지운다.
            currentUserId?.let { clearFcmToken(it) }
            currentUserId = null
            currentUserName = null
            googleName = null
            _confirmedName.value = null
            _returningAccount.value = null
            profileSyncPending = false
            profileSyncJob?.cancel()
            currentUserPhotoUrl = null
            googlePhotoUrl = null
            currentUserEmail = null
            FirebaseAuth.getInstance().signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "로그아웃 성공")
        } catch (e: Exception) {
            Log.e(TAG, "로그아웃 실패: ${e.localizedMessage}")
        }
    }

    /** 앱 시작/세션 복원 직후 — 같은 기기에 저장해 둔 커스텀 닉네임이 있으면 표시명에 반영. */
    fun applyStoredNickname(context: Context) {
        val uid = currentUserId ?: return
        NicknameStore.get(context, uid)?.let { currentUserName = it }
    }

    /**
     * 프로필에서 닉네임 변경 — 표시명(메모리)·검색용 `users/{uid}.userName`·로컬 캐시를 함께 갱신.
     * 기본값은 구글 닉네임이고, 여기서 정한 값이 이후 우선한다. 빈 값은 무시.
     */
    suspend fun setNickname(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext false
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext false
        currentUserName = trimmed
        _confirmedName.value = trimmed
        NicknameStore.set(context, uid, trimmed)
        try {
            staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
                .set(mapOf("userName" to trimmed), SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "닉네임 저장 실패: ${e.localizedMessage}")
            false
        }
    }

    fun getUserIdFromToken(idToken: String): String? {
        return try {
            val jwt = com.auth0.android.jwt.JWT(idToken)
            jwt.subject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 계정 삭제 "예약"(soft) — Play 정책 + 7일 유예.
     * 즉시 지우지 않고 users/{uid}.deletionRequestedAt 에 요청 시각을, authUid 에 FirebaseAuth uid 를 기록한 뒤 로그아웃한다.
     * 유예(7일) 동안 다시 로그인하면 [cancelPendingDeletion] 으로 자동 취소되고,
     * 끝까지 로그인하지 않으면 서버 자정 스케줄 함수(functions)가 데이터/Storage/Auth 를 완전 삭제한다.
     * (Android 는 userId=Google sub ≠ FirebaseAuth uid 라, 서버가 Auth 계정을 지우려면 authUid 가 필요.)
     */
    suspend fun requestDeletion(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext false
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        try {
            staryFirestore.collection(StaryConfig.Collections.USERS).document(uid).set(
                mapOf(
                    "deletionRequestedAt" to System.currentTimeMillis(),
                    "authUid" to authUid,
                ),
                SetOptions.merge()
            ).await()
            signOut(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "계정 삭제 예약 실패: ${e.localizedMessage}")
            false
        }
    }

    /**
     * 현재 FCM 토큰 + FirebaseAuth uid 를 users/{uid} 에 기록.
     * 서버(Cloud Functions)가 이 토큰으로 푸시를 보내므로, 로그인/세션 복원마다 최신값으로 맞춘다.
     * (토큰 회전은 [com.chaminwoo.stary.push.StaryMessagingService.onNewToken] 이 별도로 처리)
     *
     * **두 곳에 쓴다**:
     *  - `users/{uid}/fcmTokens/{token}` : 기기별(문서 id = 토큰). 한 계정이 폰+태블릿처럼
     *    여러 기기에 로그인해도 **모든 기기**로 알림이 간다.
     *  - `users/{uid}.fcmToken`          : 예전 단일 필드(구버전 서버 함수 호환).
     */
    suspend fun syncFcmToken(uid: String) {
        if (uid.isBlank()) return
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            registerFcmToken(uid, fcmToken)
            // 이 문서에 토큰이 없으면 서버가 그 사용자를 조용히 건너뛴다 → 푸시 미수신 1순위 확인 지점.
            Log.i(TAG, "fcmToken 저장 완료 users/$uid …${fcmToken.takeLast(8)}")
        } catch (e: Exception) {
            Log.w(TAG, "FCM 토큰 저장 실패: ${e.localizedMessage}")
        }
    }

    /** 토큰 1건을 기기 문서 + 예전 단일 필드에 기록. [syncFcmToken] 과 토큰 회전 콜백이 공유한다. */
    suspend fun registerFcmToken(uid: String, fcmToken: String) {
        if (uid.isBlank() || fcmToken.isBlank()) return
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val userDoc = staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
        userDoc.set(mapOf("fcmToken" to fcmToken, "authUid" to authUid), SetOptions.merge()).await()
        userDoc.collection(StaryConfig.Collections.FCM_TOKENS)
            .document(fcmToken)
            .set(
                mapOf(
                    "platform" to "android",
                    "updatedAt" to System.currentTimeMillis(),
                    // 이 기기의 앱 언어 — 서버가 전체 공지 푸시(첫 별)를 기기 언어로 보낼 때 쓴다(없으면 한국어).
                    "lang" to com.chaminwoo.stary.core.util.LocaleManager.pushLanguage(),
                )
            )
            .await()
    }

    /**
     * 이 기기의 토큰을 사용자에게서 떼어낸다 — **로그아웃 직전**에 부른다.
     *
     * ⚠️ 안 지우면 이 기기 토큰이 이전 계정 문서에 남아, 그 계정으로 온 알림이
     *    지금 로그인한 **다른 사람 화면에** 뜬다(또는 원래 주인은 못 받는다).
     *    Firestore 규칙이 본인 문서만 허용하므로 **로그아웃 전(인증이 살아 있을 때)** 해야 한다.
     */
    suspend fun clearFcmToken(uid: String) {
        if (uid.isBlank()) return
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            val userDoc = staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
            userDoc.collection(StaryConfig.Collections.FCM_TOKENS).document(fcmToken).delete().await()
            // 예전 단일 필드는 값이 이 기기 것일 때만 지운다(다른 기기가 나중에 쓴 값은 보존).
            val current = userDoc.get().await().getString("fcmToken")
            if (current == fcmToken) {
                userDoc.update("fcmToken", com.google.firebase.firestore.FieldValue.delete()).await()
            }
            Log.i(TAG, "fcmToken 해제 완료 users/$uid …${fcmToken.takeLast(8)}")
        } catch (e: Exception) {
            Log.w(TAG, "FCM 토큰 해제 실패: ${e.localizedMessage}")
        }
    }

    /** 삭제 예약 취소 — 유예 기간 내 재로그인/세션 복원 시 호출(fire-and-forget). */
    suspend fun cancelPendingDeletion(uid: String) {
        if (uid.isBlank()) return
        try {
            staryFirestore.collection(StaryConfig.Collections.USERS).document(uid)
                .update("deletionRequestedAt", com.google.firebase.firestore.FieldValue.delete())
                .await()
        } catch (_: Exception) {}
    }
}
