package com.chaminwoo.stary

import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.DiaryLimits
import com.chaminwoo.stary.core.model.DiaryTranslations
import com.chaminwoo.stary.core.model.hasValidLocation
import com.chaminwoo.stary.core.model.isFutureDated
import com.chaminwoo.stary.core.model.sanitized
import com.chaminwoo.stary.core.util.DeepLinkState
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.data.safeDecode
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버에서 온 값이 깨져 있어도 앱이 죽지 않는지 — 문서 변환 실패는 null, [Diary.sanitized] 경계값, 외부 id 형식 검사.
 * 변환은 Firestore 의 실제 매퍼([CustomClassMapper], `toObject` 가 쓰는 것)로 돌린다.
 */
class DiaryHardeningTest {

    private fun decode(data: Map<String, Any?>): Diary? =
        safeDecode("Diary") { CustomClassMapper.convertToCustomClass(data, Diary::class.java, null) }

    private val ok = mapOf<String, Any?>(
        "userId" to "u1", "title" to "t", "content" to "c", "latitude" to 37.5, "longitude" to 127.0,
        "createdAt" to 1L, "visibilityType" to "public",
    )

    // ── safeToObject 의 핵심(safeDecode): 타입이 틀린 문서는 null, 정상 문서는 그대로 ──

    @Test fun `정상 문서는 변환된다`() = assertNotNull(decode(ok))

    @Test fun `숫자 필드에 문자열이 들어간 문서는 null`() {
        assertNull(decode(ok + ("latitude" to "abc")))
        assertNull(decode(ok + ("likeCount" to "many")))
        assertNull(decode(ok + ("createdAt" to mapOf("seconds" to 1))))
    }

    @Test fun `문자열 필드에 맵이 들어간 문서는 null`() {
        assertNull(decode(ok + ("title" to mapOf("a" to 1))))
    }

    @Test fun `목록 필드에 엉뚱한 타입이 들어간 문서는 null`() {
        assertNull(decode(ok + ("starColors" to "not-a-list")))
        assertNull(decode(ok + ("translations" to "x")))
    }

    @Test fun `safeDecode 는 어떤 예외도 삼킨다`() {
        assertNull(safeDecode<String>("x") { throw IllegalStateException("boom") })
        assertNull(safeDecode<String>("x") { throw ClassCastException() })
        assertEquals("v", safeDecode("x") { "v" })
    }

    // ── sanitized 경계값 ──

    @Test fun `좌표 NaN 무한대 범위 밖은 지도 대상이 아니다`() {
        assertFalse(Diary(latitude = Double.NaN, longitude = 0.0).hasValidLocation())
        assertFalse(Diary(latitude = 0.0, longitude = Double.POSITIVE_INFINITY).hasValidLocation())
        assertFalse(Diary(latitude = 90.0001, longitude = 0.0).hasValidLocation())
        assertFalse(Diary(latitude = 0.0, longitude = -180.5).hasValidLocation())
        assertTrue(Diary(latitude = -90.0, longitude = 180.0).hasValidLocation())
    }

    @Test fun `sanitized 는 비정상 좌표를 0 으로 둔다`() {
        val d = Diary(latitude = Double.NaN, longitude = 999.0).sanitized()
        assertEquals(0.0, d.latitude, 0.0)
        assertEquals(0.0, d.longitude, 0.0)
    }

    @Test fun `미래 시각은 하루 여유를 넘으면 제외 대상`() {
        val now = 1_000_000_000_000L
        assertFalse(Diary(createdAt = now + DiaryLimits.FUTURE_SLACK_MS).isFutureDated(now))
        assertTrue(Diary(createdAt = now + DiaryLimits.FUTURE_SLACK_MS + 1).isFutureDated(now))
        assertTrue(Diary(createdAt = Long.MAX_VALUE).isFutureDated(now))
    }

    @Test fun `음수 카운트와 시각은 0`() {
        val d = Diary(likeCount = -5, commentCount = Int.MIN_VALUE, viewCount = -1, createdAt = -9).sanitized()
        assertEquals(0, d.likeCount); assertEquals(0, d.commentCount); assertEquals(0, d.viewCount)
        assertEquals(0L, d.createdAt)
    }

    @Test fun `길이 상한을 넘는 문자열은 잘린다`() {
        val d = Diary(title = "a".repeat(10_000), content = "b".repeat(100_000), userName = "c".repeat(999)).sanitized()
        assertEquals(DiaryLimits.TITLE, d.title.length)
        assertEquals(DiaryLimits.CONTENT, d.content.length)
        assertEquals(DiaryLimits.USER_NAME, d.userName.length)
    }

    @Test fun `서로게이트 쌍을 가운데서 자르지 않는다`() {
        val s = "a".repeat(DiaryLimits.TITLE - 1) + "😀"
        val t = Diary(title = s).sanitized().title
        assertTrue(t.length <= DiaryLimits.TITLE)
        assertFalse(t.last().isHighSurrogate())
    }

    @Test fun `정상 글은 그대로`() {
        val d = Diary(id = "x", userId = "u", title = "제목", content = "본문", latitude = 37.5, longitude = 127.0, createdAt = 5, visibilityType = "friends")
        assertEquals(d, d.sanitized())
    }

    @Test fun `알 수 없는 공개 범위는 private 으로`() {
        assertEquals("private", Diary(visibilityType = "everyone").sanitized().visibilityType)
        assertEquals("friends", Diary(visibilityType = "friends").sanitized().visibilityType)
    }

    @Test fun `깨진 별 경로 색 번호 개수 방어`() {
        val d = Diary(
            starType = Int.MAX_VALUE, starColor = -3, starPath = "M".repeat(5000),
            starColors = listOf("#112233", "red", "#GGGGGG", "#AABBCC", "#000000", "#FFFFFF", "#123456"),
        ).sanitized()
        assertEquals(9999, d.starType)
        assertEquals(0, d.starColor)
        assertEquals("", d.starPath)
        assertEquals(listOf("#112233", "#AABBCC", "#000000"), d.starColors)
    }

    @Test fun `https 가 아닌 미디어 주소는 비운다`() {
        val d = Diary(imageUrl = "content://x/y", videoUrl = "https://firebasestorage.googleapis.com/v0/b/x/o/y").sanitized()
        assertEquals("", d.imageUrl)
        assertTrue(d.videoUrl.startsWith("https://"))
    }

    @Test fun `번역 맵은 항목 수와 길이가 제한된다`() {
        val many = (1..50).associate { "k$it" to "v".repeat(100_000) }
        val t = DiaryTranslations(sourceLang = "ko", title = many, content = many).sanitized()
        assertTrue(t.title.size <= DiaryLimits.TRANSLATION_ENTRIES)
        assertTrue(t.title.values.all { it.length <= DiaryLimits.TITLE })
        assertTrue(t.content.values.all { it.length <= DiaryLimits.CONTENT })
    }

    private fun DiaryTranslations.sanitized(): DiaryTranslations = Diary(translations = this).sanitized().translations!!

    // ── 외부 id 형식 ──

    @Test fun `딥링크 id 는 경로 구분자와 긴 값을 거른다`() {
        assertEquals("abcDEF123_-", DeepLinkState.safeId("abcDEF123_-"))
        assertNull(DeepLinkState.safeId("a/b"))
        assertNull(DeepLinkState.safeId("../x"))
        assertNull(DeepLinkState.safeId(""))
        assertNull(DeepLinkState.safeId(null))
        assertNull(DeepLinkState.safeId("a".repeat(129)))
    }

    @Test fun `문서 id 검사`() {
        assertTrue(FirebaseDiaryRepository.isSafeDocId("Zk3Jd9aQ"))
        assertFalse(FirebaseDiaryRepository.isSafeDocId("a/b"))
        assertFalse(FirebaseDiaryRepository.isSafeDocId(null))
    }
}
