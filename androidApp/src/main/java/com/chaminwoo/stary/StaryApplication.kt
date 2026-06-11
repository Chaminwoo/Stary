package com.chaminwoo.stary

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Firebase 는 google-services.json 기반 자동 초기화 사용.
 *
 * Firestore/Storage 보안 규칙(request.auth != null)을 통과하려면 항상 Firebase Auth
 * 세션이 필요하므로, 로그인 전(둘러보기 포함)에는 **익명 로그인**으로 세션을 만든다.
 * (Firebase Console > Authentication > Sign-in method 에서 '익명' 활성화 필요)
 * Google 로그인 시 GoogleAuthHelper 가 signInWithCredential 로 교체한다.
 */
class StaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnFailureListener { e ->
                    Log.w("StaryApplication", "익명 로그인 실패(콘솔에서 익명 인증 활성화 필요): ${e.localizedMessage}")
                }
        }
    }
}
