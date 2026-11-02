package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * 앱 안 "고칠 점 보내기" — 최상위 `feedback/{자동 id}` 에 한 건씩 쓴다(읽기/수정/삭제는 규칙상 불가, 콘솔에서만 본다).
 * 필드: uid(appUserId — 규칙의 isMe 가 검사) · message · platform · appVersion · locale · createdAt(서버 시각).
 * 이름·이메일은 넣지 않고 users/{uid} 도 건드리지 않는다. 모델 클래스 없이 평범한 Map 이라 R8 keep 규칙이 필요 없다.
 * iOS `FeedbackRepository.swift`(FirestoreService.sendFeedback)와 같은 스키마.
 */
object FeedbackRepository {
    const val COLLECTION = "feedback"
    /** 메시지 최대 길이 — firestore.rules 의 size() <= 2000 과 같은 값. */
    const val MAX_LENGTH = 2000
    /** 오프라인이면 쓰기 완료 신호가 오지 않아 끝없이 기다리게 되므로 이 시간 뒤 실패로 본다. */
    private const val TIMEOUT_MS = 15_000L

    /** 성공하면 true. 네트워크/권한/시간 초과면 false(호출한 쪽이 입력을 보존한 채 오류를 알린다). */
    suspend fun send(uid: String, message: String, appVersion: String, locale: String): Boolean {
        val text = message.trim()
        if (uid.isBlank() || text.isEmpty() || text.length > MAX_LENGTH) return false
        return try {
            withTimeout(TIMEOUT_MS) {
                staryFirestore.collection(COLLECTION).add(
                    mapOf(
                        "uid" to uid,
                        "message" to text,
                        "platform" to "android",
                        "appVersion" to appVersion,
                        "locale" to locale,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
