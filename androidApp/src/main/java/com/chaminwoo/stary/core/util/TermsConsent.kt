package com.chaminwoo.stary.core.util

import android.content.Context
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.SetOptions

/**
 * 이용약관(EULA) 동의 기록 — App Store Guideline 1.2(사용자 생성 콘텐츠) 대응(2026-09-26).
 *
 * 로그인·둘러보기 **전에** 약관(무관용 원칙 · 자동 필터 · 신고/차단 · 24시간 내 조치)에 동의해야 한다.
 *  - 기기 단위로 먼저 저장(prefs) — 둘러보기(비로그인)도 동의가 필요하기 때문.
 *  - 로그인 상태면 `users/{uid}.termsAcceptedAt/termsVersion` 에도 남긴다(운영 기록).
 *  - 약관 내용이 바뀌면 [VERSION] 을 올린다 → 모두 다시 동의해야 한다.
 * iOS: `Data/TermsConsent.swift`(같은 키·버전).
 */
object TermsConsent {
    const val VERSION = 1
    private const val PREFS = "stary_prefs"
    private const val KEY = "terms_accepted_v$VERSION"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    /** 동의 저장. [userId] 가 있으면 서버 프로필에도 기록(실패해도 동의 자체는 유지). */
    fun accept(context: Context, userId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
        userId?.takeIf { it.isNotBlank() }?.let { record(it) }
    }

    /** 이미 동의한 기기에서 로그인했을 때 서버 기록만 남긴다. */
    fun record(userId: String) {
        try {
            staryFirestore.collection(StaryConfig.Collections.USERS).document(userId)
                .set(
                    mapOf("termsAcceptedAt" to System.currentTimeMillis(), "termsVersion" to VERSION),
                    SetOptions.merge()
                )
        } catch (_: Exception) {}
    }
}
