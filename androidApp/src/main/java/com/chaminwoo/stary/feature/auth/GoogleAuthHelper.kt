package com.chaminwoo.stary.feature.auth

import android.content.Context
import android.util.Log
import com.chaminwoo.stary.BuildConfig
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.chaminwoo.stary.core.model.UserProfile
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
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Auth 로그인 실패: ${e.localizedMessage}")
                }
                // 친구 검색이 가능하도록 공개 프로필(users/{uid}) 기록.
                // fire-and-forget: Firestore 쓰기가 지연/실패해도 로그인 흐름을 막지 않는다.
                currentUserId?.let { uid ->
                    CoroutineScope(Dispatchers.IO).launch {
                        FirebaseFriendRepository().upsertProfile(
                            UserProfile(
                                userId = uid,
                                userName = currentUserName ?: "",
                                profileImageUrl = currentUserPhotoUrl ?: ""
                            )
                        )
                        // FCM 토큰 저장 — 친구 새 글 푸시(Cloud Functions) 발송 대상 조회용
                        try {
                            val fcmToken = FirebaseMessaging.getInstance().token.await()
                            staryFirestore.collection(StaryConfig.Collections.USERS)
                                .document(uid)
                                .set(mapOf("fcmToken" to fcmToken), SetOptions.merge())
                                .await()
                        } catch (e: Exception) {
                            Log.w(TAG, "FCM 토큰 저장 실패: ${e.localizedMessage}")
                        }
                    }
                }
                idToken
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "구글 로그인 실패: ${e.localizedMessage}")
            null
        }
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
        return true
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            currentUserId = null
            currentUserName = null
            currentUserPhotoUrl = null
            FirebaseAuth.getInstance().signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "로그아웃 성공")
        } catch (e: Exception) {
            Log.e(TAG, "로그아웃 실패: ${e.localizedMessage}")
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
     * 계정·데이터 삭제(Play 정책: 데이터 삭제 경로 제공).
     * Firestore 의 내 데이터(다이어리/친구/열람/차단/프로필)와 Storage 프로필 이미지를 베스트에포트로 지우고,
     * FirebaseAuth 사용자를 삭제한 뒤 로그아웃한다. 최근 로그인 필요 시 구글 재인증 후 재시도.
     */
    suspend fun deleteAccount(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId
        try {
            if (!uid.isNullOrBlank()) {
                deleteUserData(uid)
                try {
                    com.google.firebase.storage.FirebaseStorage.getInstance()
                        .reference.child("profile_images/$uid.jpg").delete().await()
                } catch (_: Exception) {}
            }
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.let { user ->
                try {
                    user.delete().await()
                } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    val idToken = obtainGoogleIdToken(context)   // 재인증 후 재시도
                    if (idToken != null) {
                        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
                        user.delete().await()
                    }
                }
            }
            currentUserId = null
            currentUserName = null
            currentUserPhotoUrl = null
            auth.signOut()
            try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            Log.e(TAG, "계정 삭제 실패: ${e.localizedMessage}")
            false
        }
    }

    /** 내 Firestore 데이터 정리(베스트에포트). 하위 컬렉션은 문서 삭제로 자동 정리되지 않아 직접 지운다. */
    private suspend fun deleteUserData(uid: String) {
        val db = staryFirestore
        val users = db.collection(StaryConfig.Collections.USERS)
        // 내가 쓴 다이어리
        try {
            db.collection(StaryConfig.Collections.DIARIES).whereEqualTo("userId", uid).get().await()
                .documents.forEach { runCatching { it.reference.delete().await() } }
        } catch (_: Exception) {}
        // 친구 양방향 정리(상대 친구목록에서 나 제거 + 내 친구목록)
        try {
            users.document(uid).collection(StaryConfig.Collections.FRIENDS).get().await()
                .documents.forEach { f ->
                    runCatching { users.document(f.id).collection(StaryConfig.Collections.FRIENDS).document(uid).delete().await() }
                    runCatching { f.reference.delete().await() }
                }
        } catch (_: Exception) {}
        // 열람/차단 하위 컬렉션
        for (sub in listOf(StaryConfig.Collections.VIEWED_DIARIES, StaryConfig.Collections.BLOCKED)) {
            try {
                users.document(uid).collection(sub).get().await()
                    .documents.forEach { runCatching { it.reference.delete().await() } }
            } catch (_: Exception) {}
        }
        // 프로필 문서
        runCatching { users.document(uid).delete().await() }
    }

    /** 재인증용 구글 ID 토큰 획득(로그인과 동일 플로우, Firebase 로그인은 하지 않음). */
    private suspend fun obtainGoogleIdToken(context: Context): String? {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val credential = credentialManager.getCredential(context = context, request = request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "재인증 토큰 획득 실패: ${e.localizedMessage}")
            null
        }
    }
}
