package com.chaminwoo.stary.core.util

import android.content.Context

/**
 * 기기 첫 실행(재설치)이라도 **이전에 가입했던 계정**으로 로그인하면 처음 쓰는 사람용 안내를 건너뛴다(2026-10-19 사용자 지시).
 *
 * 판정은 [com.chaminwoo.stary.feature.auth.GoogleAuthHelper.returningAccount] — 로그인 후 첫 서버 읽기에서
 * `users/{uid}` 에 닉네임/사진이 이미 있었으면 이전 가입 계정이다.
 * 건너뛰는 것: 지도 코치마크(`main_coach_seen`) · 웰컴(튜토리얼) 별 · 화면별 첫 진입 안내([com.chaminwoo.stary.core.ui.FirstVisitInfo]).
 * 설정 > "도움말 다시 보기"는 그대로 동작한다(코치마크만 다시 띄움).
 * iOS `ReturningAccountOnboarding`(AuthManager.swift) 와 같은 키.
 */
object ReturningAccountOnboarding {
    private const val PREFS = "stary_onboarding"

    /** 코치마크 + 화면별 첫 진입 안내 키 — 새 FirstVisitInfo 를 추가하면 여기에도 넣는다. */
    private val SEEN_KEYS = listOf(
        "main_coach_seen",
        "info_friends",
        "info_achievements",
        "info_music",
        "info_mydiary",
        "info_profile",
        "info_starlog",
    )

    fun apply(context: Context) {
        val edit = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        SEEN_KEYS.forEach { edit.putBoolean(it, true) }
        edit.apply()
        if (!TutorialStarState.done) TutorialStarState.markDone(context)
    }
}
