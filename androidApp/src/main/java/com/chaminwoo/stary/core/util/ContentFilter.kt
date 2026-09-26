package com.chaminwoo.stary.core.util

/**
 * 부적절한 표현 필터 — App Store Guideline 1.2(사용자 생성 콘텐츠) "A method for filtering objectionable content"(2026-09-26).
 *
 * - **올리기 차단**: 다이어리(제목·본문, 새 글/수정) · 댓글 · 채팅 메시지 · 닉네임에 걸리면 저장하지 않고 안내 토스트.
 * - **보이기 차단**: 지도/목록의 남의 다이어리 · 남의 댓글 중 걸리는 것은 숨긴다(필터가 생기기 전 글·구버전 앱 글 대비).
 *
 * 판정 규칙(오탐을 줄이는 쪽으로 보수적으로):
 *  - 한국어·일본어 [CJK_TERMS]: 소문자화 → 숫자·기호만 지우고("씨1발", "시.발" 잡기) **단어(띄어쓰기) 단위**로 찾는다.
 *    공백을 통째로 지우면 "다시 발걸음" → "다시발걸음" 처럼 단어 경계를 넘는 오탐이 생겨서, 공백은 남기고
 *    **한 글자짜리 토막이 이어진 부분만**("씨 발 놈") 붙여서 한 번 더 본다. 정상 단어(시발점 등)는 [ALLOW] 로 먼저 지운다.
 *  - 영어 [EN_WORDS]: **단어 경계**로만 찾는다(class 안의 ass 같은 오탐 방지).
 *  - 뜻이 여럿인 말("뒤져라" = 찾아라)이나 도움이 필요한 사람의 표현(자해·자살 언급)은 막지 않는다.
 * 목록을 바꾸면 iOS `Core/ContentFilter.swift` 도 같이(값 drift 금지). 진짜 조치는 신고 → 운영자 검토(24시간)가 맡는다.
 */
object ContentFilter {

    private val CJK_TERMS = listOf(
        // 한국어 욕설·비하
        "씨발", "시발", "씨바", "씨팔", "시팔", "씨빨", "씨벌", "쓰벌", "ㅅㅂ", "ㅆㅂ", "ㅆㅃ",
        "병신", "븅신", "빙신", "ㅂㅅ", "개새끼", "개새기", "개색기", "개색끼", "개세끼", "개쉐이",
        "좆", "존나", "지랄", "염병", "엠창", "느금마", "니애미", "니미럴", "애미뒤", "애비뒤",
        "창녀", "창놈", "걸레년", "미친년", "미친놈", "썅", "쌍년", "쌍놈", "호로새끼", "후레자식",
        "섹스", "강간", "뒈져", "한남충", "김치녀", "틀딱", "급식충", "짱깨", "쪽바리",
        // 일본어
        "死ね", "氏ね", "殺す", "きちがい", "キチガイ", "基地外", "レイプ", "強姦", "クソ野郎", "くそやろう", "ガイジ", "ちんこ",
    )

    private val EN_WORDS = listOf(
        "fuck", "fucks", "fucked", "fucker", "fuckers", "fucking", "motherfucker", "shit", "shitty", "bullshit",
        "bitch", "bitches", "cunt", "cunts", "nigger", "niggers", "nigga", "niggas", "faggot", "faggots", "fag",
        "retard", "retarded", "whore", "whores", "slut", "sluts", "rape", "rapist", "asshole", "assholes",
        "bastard", "dickhead", "porn", "pussy", "kys",
    )

    /** 걸리는 말이 들어 있지만 정상적으로 쓰이는 단어 — 정규화된 문자열에서 먼저 지운다. */
    private val ALLOW = listOf("시발점", "시발역", "유니섹스")

    private val enRegex = Regex("\\b(" + EN_WORDS.joinToString("|") + ")\\b")
    private val killYourself = Regex("kill\\s+your\\s*self")
    private val whitespace = Regex("\\s+")

    /** 부적절한 표현이 들어 있는가. */
    fun isObjectionable(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        if (enRegex.containsMatchIn(lower) || killYourself.containsMatchIn(lower)) return true
        // 숫자·기호 제거(공백은 유지) → 단어로 나눈다.
        val tokens = lower.filter { it.isLetter() || it.isWhitespace() }
            .split(whitespace).filter { it.isNotEmpty() }
        val chunks = ArrayList<String>(tokens)
        // 한 글자 토막이 이어진 곳은 붙여서 한 덩어리로("씨 발" → "씨발").
        val run = StringBuilder()
        for (t in tokens + "") {
            if (t.length == 1) run.append(t) else {
                if (run.length > 1) chunks += run.toString()
                run.clear()
            }
        }
        return chunks.any { chunk ->
            var c = chunk
            ALLOW.forEach { c = c.replace(it, "") }
            CJK_TERMS.any { c.contains(it) }
        }
    }

    /** 여러 칸(제목+본문 등) 중 하나라도 걸리면 true. */
    fun anyObjectionable(vararg texts: String?): Boolean = texts.any { isObjectionable(it) }
}
