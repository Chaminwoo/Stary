package com.chaminwoo.stary.core.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * 광고제거 계정(2026-10-02) — `adFree/{appUserId}` 문서가 **있으면** 100m 밖 게시물을 광고 없이 탭 한 번에 연다
 * (잠금 화면 `DiaryLock.kt` `LockedContentCard`). 앱 어디에도 "광고제거 계정"이라고 표시하지 않는다(사용자 결정).
 *
 * - 부여: Firebase Console → Firestore(stary-db) → `adFree` 컬렉션에 문서 id = 대상 appUserId 로 문서 생성
 *   (필드는 자유 — 메모용 `note` 정도). 지우면 즉시 해제된다(스냅샷 리스너).
 * - 규칙: 본인만 읽기, 클라이언트 쓰기 금지 → 사용자가 스스로 켤 수 없다(`users/{uid}` 필드로 두지 않은 이유).
 * - 읽기 실패(규칙 미배포/오프라인 등)는 **꺼짐**으로 본다(fail closed).
 * - iOS `AdFreeAccount.swift` 패리티.
 */
object AdFreeAccount {

    /** 지금 로그인한 계정이 광고제거 계정인지 — Compose 상태(바뀌면 잠금 화면이 바로 갱신). */
    var active by mutableStateOf(false)
        private set

    private var watchingUid: String? = null
    private var registration: ListenerRegistration? = null
    private var started = false

    /** 앱 시작 시 1회(StaryApplication). 로그인/로그아웃/계정 전환마다 감시 대상을 바꾼다. */
    fun start() {
        if (started) return
        started = true
        // 등록 즉시 1회 + 인증 상태가 바뀔 때마다 호출된다.
        FirebaseAuth.getInstance().addAuthStateListener { watch(GoogleAuthHelper.currentUserId) }
    }

    private fun watch(uid: String?) {
        if (uid == watchingUid) return
        registration?.remove()
        registration = null
        watchingUid = uid
        active = false
        if (uid.isNullOrBlank()) return
        registration = staryFirestore.collection(StaryConfig.Collections.AD_FREE)
            .document(uid)
            .addSnapshotListener { snap, _ -> active = snap?.exists() == true }
    }
}
