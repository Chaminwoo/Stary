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
                    }
                }
                idToken
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "구글 로그인 실패: ${e.localizedMessage}")
            null
        }
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
}
