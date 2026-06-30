package com.chaminwoo.stary.core.util

import android.content.Context

/**
 * 사용자가 직접 정한 닉네임의 **로컬 캐시**(uid별).
 * 진짜 소스는 Firestore `users/{uid}.userName`(검색/친구목록 표시) 지만,
 * 앱 재시작 직후 Firestore 응답 전에도 즉시 보이도록 prefs 에도 둔다.
 *
 * 기본값(미설정) = 구글 닉네임(GoogleAuthHelper.currentUserName). 여기 값이 있으면 그게 우선.
 */
object NicknameStore {
    private const val PREFS = "stary_nickname"

    fun get(context: Context, uid: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(uid, null)?.takeIf { it.isNotBlank() }

    fun set(context: Context, uid: String, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(uid, name).apply()
    }
}
