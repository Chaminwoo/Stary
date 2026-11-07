package com.chaminwoo.stary.data

import android.util.Log
import com.chaminwoo.stary.core.designsystem.StarCatalog
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.hasValidLocation
import com.chaminwoo.stary.core.model.isFutureDated
import com.chaminwoo.stary.core.model.sanitized
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Firestore 문서 → 모델 변환을 **던지지 않게** 감싼다.
 * 서버 데이터는 우리 앱이 쓴 것만 있다는 보장이 없다 — 필드 타입이 어긋난 문서 한 건이 `toObject` 에서 예외를 던지면,
 * 스냅샷 콜백(특히 지도의 전용 백그라운드 스레드)에서는 처리되지 않은 예외로 앱이 꺼진다. 깨진 문서는 건너뛴다.
 * 로그에는 모델 종류와 예외 종류만 남기고 문서 내용은 남기지 않는다.
 */
inline fun <reified T : Any> DocumentSnapshot.safeToObject(): T? =
    safeDecode(T::class.java.simpleName) { toObject(T::class.java) }

/** [block] 의 모든 예외를 삼키고 null. 단위 테스트에서 직접 쓸 수 있게 따로 둔다. */
fun <T> safeDecode(kind: String, block: () -> T?): T? = try {
    block()
} catch (e: Exception) {
    try {
        Log.w("SafeDecode", "skip $kind: ${e.javaClass.simpleName}")
    } catch (_: Throwable) {
        // 단위 테스트(android.util.Log 없음)
    }
    null
}

/**
 * 다이어리 문서를 읽어 [sanitized] 로 정리한다.
 * @param forMap true 면 지도에 올릴 수 없는 문서(좌표 비정상 · 하루 넘는 미래 시각)를 null 로 버린다.
 */
fun DocumentSnapshot.toDiaryOrNull(forMap: Boolean = false, nowMs: Long = System.currentTimeMillis()): Diary? {
    val raw = safeToObject<Diary>() ?: return null
    if (forMap && (!raw.hasValidLocation() || raw.isFutureDated(nowMs))) return null
    return safeDecode("Diary") {
        raw.copy(id = id).sanitized().also(StarCatalog::learn)
    }
}

/** 다른 사용자가 쓴 이미지 주소는 https 만 쓴다(그 밖의 스킴 · 지나치게 긴 값은 빈 값 = 기본 아이콘). */
fun safeImageUrl(url: String?): String =
    url?.takeIf { it.length <= 2048 && it.startsWith("https://") }.orEmpty()
