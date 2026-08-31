package com.chaminwoo.stary.feature.auth

import android.content.Context
import android.util.Log
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    // 하드코딩 금지: secrets.properties -> BuildConfig 로 주입.
    // TODO: secrets.properties 에 GOOGLE_WEB_CLIENT_ID 실제 값 설정 (커밋 금지)
    private val WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    var currentUserId: String? = null
    var currentUserName: String? = null
    var currentUserPhotoUrl: String? = null
    var currentUserEmail: String? = null  // 어드민 판정용(히든 업적 선점 제외)

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
                currentUserId = getUserIdFromToken(idToken)
                currentUserName = googleIdTokenCredential.displayName
                currentUserPhotoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                // Firebase Auth 에도 로그인 — Firestore/Storage 보안 규칙(request.auth != null)이
                // 통과하려면 필수. 실패해도 앱 흐름은 막지 않는다.
                try {
                    FirebaseAuth.getInstance()
                        .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                        .await()
                    currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Auth 로그인 실패: ${e.localizedMessage}")
                }
                // 친구 검색이 가능하도록 공개 프로필(users/{uid}) 기록.
                // fire-and-forget: Firestore 쓰기가 지연/실패해도 로그인 흐름을 막지 않는다.
                val appCtx = context.applicationContext
                currentUserId?.let { uid ->
                    CoroutineScope(Dispatchers.IO).launch {
                        // ⚠️ **"읽기 실패"와 "문서 없음"을 반드시 구분한다.**
                        //    둘 다 null 로 뭉뚱그리면, 네트워크가 잠깐 안 될 때 아래 upsertProfile 이
                        //    커스텀 닉네임/프로필 사진을 **구글 기본값으로 덮어써** 버린다.
                        //    (다른 사람 화면에도 그 값이 그대로 보이므로 계정 전체가 구글 기본으로 되돌아간 것처럼 된다)
                        val loaded: Pair<Boolean, UserProfile?> = try {
                            val doc = staryFirestore
                                .collection(StaryConfig.Collections.USERS).document(uid)
                                .get().await()
                            val profile =
                                if (doc.exists()) doc.toObject(UserProfile::class.java)?.copy(userId = uid)
                                else null
                            true to profile
                        } catch (e: Exception) {
                            Log.w(TAG, "공개 프로필 조회 실패 — 프로필 기록을 건너뜀: ${e.localizedMessage}")
                            false to null
                        }
                        val readOk = loaded.first
                        val saved = loaded.second
                        // 이미 정해둔 닉네임(커스텀 포함)이 있으면 그걸 우선 — 구글 이름으로 덮어쓰지 않는다.
                        // (다른 기기에서 재로그인해도 닉네임이 유지되도록.)
                        val existing = saved?.userName?.takeIf { it.isNotBlank() }
                        if (existing != null) {
                            currentUserName = existing
                            NicknameStore.set(appCtx, uid, existing)
                        }
                        // 프로필 **사진**도 동일 — 앱에서 바꾼 사진이 있으면 유지한다.
                        // (예전엔 아래 upsertProfile 이 항상 구글 사진으로 덮어써서 재로그인마다 사진이 초기화됐다.)
                        val existingPhoto = saved?.profileImageUrl?.takeIf { it.isNotBlank() }
                        if (existingPhoto != null) currentUserPhotoUrl = existingPhoto
                        // 현재 값을 확실히 아는 경우에만 기록한다(읽기 실패 시엔 손대지 않는다).
                        if (readOk) {
                            FirebaseFriendRepository().upsertProfile(
                                UserProfile(
                                    userId = uid,
                                    userName = currentUserName ?: "",
                                    profileImageUrl = currentUserPhotoUrl ?: ""
                                )
                            )
                        }
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
                null
            }
        } catch (e: Exception) {
            lastSignInError = describeSignInFailure(e)
            Log.e(TAG, "구글 로그인 실패: $lastSignInError", e)
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
    private fun describeSignInFailure(e: Exception): String = when (e) {
        is GetCredentialCancellationException -> "로그인을 취소했어요."
        is NoCredentialException ->
            "사용할 수 있는 구글 계정을 찾지 못했어요. (앱 서명 지문 미등록이거나 기기에 구글 계정이 없음)"
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
    fun restoreSession(): Boolean {
        if (currentUserId != null) return true
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val uid = (google?.uid ?: user.uid).takeIf { it.isNotBlank() } ?: return false
        currentUserId = uid
        currentUserName = google?.displayName ?: user.displayName
        currentUserPhotoUrl = (google?.photoUrl ?: user.photoUrl)?.toString()
        currentUserEmail = user.email
        // 앱 재시작(세션 복원)도 "로그인"으로 보고 삭제 예약을 취소(7일 유예 정책).
        // ⚠️ FCM 토큰도 여기서 다시 기록한다 — 예전엔 "구글 로그인 화면을 실제로 거친 순간"에만
        //    저장해서, 로그인이 유지되는 기기(대부분)는 토큰이 재발급돼도 갱신되지 않아
        //    푸시가 조용히 끊길 수 있었다(친구 새 글/친구 요청 알림 미수신 원인).
        CoroutineScope(Dispatchers.IO).launch {
            // 앱에서 바꾼 닉네임/프로필 사진이 있으면 구글 값 대신 그걸 쓴다.
            // (사진은 users/{uid}.profileImageUrl 이 원본 — 여기서 안 채우면 재시작 후 친구 요청 등에
            //  구글 사진이 다시 실려 나가 사진이 되돌아간 것처럼 보인다.)
            FirebaseFriendRepository().getProfile(uid)?.let { saved ->
                saved.userName.takeIf { it.isNotBlank() }?.let { currentUserName = it }
                saved.profileImageUrl.takeIf { it.isNotBlank() }?.let { currentUserPhotoUrl = it }
            }
            cancelPendingDeletion(uid)
            syncFcmToken(uid)
        }

        return true
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            // ⚠️ 인증이 살아 있는 지금 이 기기 토큰을 떼어낸다 — 로그아웃 뒤엔 규칙에 막혀 못 지운다.
            currentUserId?.let { clearFcmToken(it) }
            currentUserId = null
            currentUserName = null
            currentUserPhotoUrl = null
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
            .set(mapOf("platform" to "android", "updatedAt" to System.currentTimeMillis()))
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
