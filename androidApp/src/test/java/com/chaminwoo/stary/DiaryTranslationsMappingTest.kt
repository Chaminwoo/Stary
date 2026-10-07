package com.chaminwoo.stary

import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.isTranslatedFor
import com.chaminwoo.stary.core.model.localizedContent
import com.chaminwoo.stary.core.model.localizedTitle
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버 선번역(functions/translations.js)이 쓰는 `translations` 맵이 **Firestore 의 실제 매퍼**로 [Diary] 에 읽히는지.
 * ⚠️ 지도 목록은 `doc.toObject(Diary)` 를 try/catch 없이 돌린다 — 매핑이 한 번이라도 던지면 목록 전체가 영향을 받으므로,
 *    모델/서버 스키마를 바꿀 때 이 테스트로 먼저 확인한다.
 */
class DiaryTranslationsMappingTest {

    private fun base(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
        "userId" to "u1", "userName" to "민우", "title" to "제목", "content" to "본문입니다",
        "latitude" to 37.5, "longitude" to 127.0, "createdAt" to 1L, "likeCount" to 3L,
        "starType" to 1L, "starColor" to 2L, "visibilityType" to "public",
    ) + extra

    private fun map(data: Map<String, Any?>): Diary =
        CustomClassMapper.convertToCustomClass(data, Diary::class.java, null)

    /** 서버가 done 으로 저장한 모양 그대로(숫자는 Firestore 가 Long 으로 돌려준다). */
    private val done = mapOf(
        "sourceLang" to "ko",
        "title" to mapOf("ko" to "제목", "en" to "Title", "ja" to "タイトル", "zh" to "标题"),
        "content" to mapOf("ko" to "본문입니다", "en" to "This is the body", "ja" to "本文です", "zh" to "这是正文"),
        "status" to "done", "srcHash" to "0123456789abcdef", "updatedAt" to 1790000000000L,
    )

    @Test fun `서버가 저장한 translations 가 읽힌다`() {
        val d = map(base(mapOf("translations" to done)))
        val t = d.translations
        assertNotNull(t)
        assertEquals("ko", t!!.sourceLang)
        assertEquals("Title", t.title["en"])
        assertEquals("本文です", t.content["ja"])
        assertEquals("done", t.status)
        assertEquals(1790000000000L, t.updatedAt)
        assertEquals("제목", d.title) // 원문 필드는 그대로
    }

    @Test fun `translations 가 없거나 null 이면 null`() {
        assertNull(map(base()).translations)
        assertNull(map(base(mapOf("translations" to null))).translations)
    }

    @Test fun `진행 중(processing) 문서도 읽히고 원문이 나온다`() {
        val d = map(base(mapOf("translations" to mapOf(
            "srcHash" to "abc", "status" to "processing", "startedAt" to 1790000000000L))))
        assertEquals("processing", d.translations!!.status)
        assertEquals("제목", d.localizedTitle("en"))
        assertFalse(d.isTranslatedFor("en"))
    }

    @Test fun `앱 언어 번역이 있으면 번역 없으면 원문`() {
        val d = map(base(mapOf("translations" to done)))
        assertEquals("Title", d.localizedTitle("en"))
        assertEquals("本文です", d.localizedContent("ja"))
        assertEquals("제목", d.localizedTitle("ko")) // 원문 언어와 같으면 번역 불필요
        assertTrue(d.isTranslatedFor("en"))
        assertFalse(d.isTranslatedFor("ko"))
        assertEquals("제목", d.localizedTitle("es")) // 번역에 없는 언어 → 원문
    }

    @Test fun `수정 직후 낡은 번역은 쓰지 않는다`() {
        val d = map(base(mapOf("title" to "고친 제목", "translations" to done)))
        assertEquals("고친 제목", d.localizedTitle("en"))
        assertEquals("본문입니다", d.localizedContent("en"))
        assertFalse(d.isTranslatedFor("en"))
    }

    @Test fun `일부 언어만 있으면 있는 언어만 번역`() {
        val partial = done + mapOf(
            "status" to "partial",
            "title" to mapOf("ko" to "제목", "en" to "Title"),
            "content" to mapOf("ko" to "본문입니다", "en" to "This is the body"),
        )
        val d = map(base(mapOf("translations" to partial)))
        assertEquals("Title", d.localizedTitle("en"))
        assertEquals("제목", d.localizedTitle("ja"))
    }
}
