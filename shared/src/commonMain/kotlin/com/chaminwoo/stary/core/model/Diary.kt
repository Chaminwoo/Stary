package com.chaminwoo.stary.core.model

/**
 * 플랫폼 공용(KMP commonMain) 도메인 모델.
 *
 * 기존 Android 전용 코드에서는 createdAt 이 Firebase 의 [com.google.firebase.Timestamp] 였지만,
 * iOS 와 공용으로 쓰기 위해 플랫폼 비종속 타입인 epoch millis(Long) 로 변경했다.
 * Firestore <-> 모델 변환은 각 플랫폼의 Repository 구현부에서 담당한다.
 */
data class Diary(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    // (사용 안 함) 익명 게시 기능은 제거됨. 옛 문서 디코딩 하위호환 + iOS 모델 패리티용으로만 남겨둠 — 항상 false.
    val isAnonymous: Boolean = false,
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    /** 3초 이내 짧은 영상 URL(Storage). 비어 있으면 영상 없음 — imageUrl 과 배타적으로 사용. */
    val videoUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = 0L, // epoch millis (UTC)
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    /** 별 모양 종류 인덱스 (0..4). 렌더는 starType×starColor 조합으로 결정. */
    val starType: Int = 0,
    /** 별 색상 팔레트 인덱스 (0..11). 팔레트는 androidApp designsystem StarStyle 참고. */
    val starColor: Int = 0,
    /** 공개 범위: "public"(전체), "friends"(친구만), "private"(나만보기). */
    val visibilityType: String = "public",
    /**
     * 서버(Cloud Functions `translateDiaryOn*`)가 채우는 자동 선번역 — **앱은 읽기만 한다**(쓰지 않음).
     * 예전 글·번역 전·번역 실패는 null/빈 값이고, 그때는 원문이 나온다. 구조는 functions/translations.js 머리말 참고.
     * ⚠️ 수정·신고·공유·알림은 반드시 원문(title/content)을 쓴다. 화면 표시만 [localizedTitle]/[localizedContent].
     */
    val translations: DiaryTranslations? = null
)

/** `diaries/{id}.translations` — 언어 코드가 key 라서 언어가 늘어도 이 모델은 그대로다. */
data class DiaryTranslations(
    /** 감지된 원문 언어("ko" 등). */
    val sourceLang: String = "",
    val title: Map<String, String> = emptyMap(),
    val content: Map<String, String> = emptyMap(),
    /** done / partial / failed / skipped / processing — 표시 판단엔 쓰지 않는다(맵에 값이 있는지만 본다). */
    val status: String = "",
    val srcHash: String = "",
    val updatedAt: Long = 0L,
    val startedAt: Long = 0L,
)

/**
 * [lang] 로 보여 줄 수 있는 번역. 아래일 때만 non-null:
 *  - 표시 언어가 원문 언어와 다르다(같으면 번역이 필요 없다),
 *  - 번역이 **지금 원문의 번역**이다 — 번역 맵의 원문 언어 칸은 번역 당시 원문이라, 글을 수정한 직후 재번역이 끝나기 전의
 *    낡은 번역은 걸러진다.
 */
fun Diary.translationFor(lang: String): DiaryTranslations? {
    val t = translations ?: return null
    if (t.sourceLang.isBlank() || t.sourceLang == lang) return null
    if (t.title[t.sourceLang] != title || t.content[t.sourceLang] != content) return null
    return t
}

/** 화면에 보여 줄 제목 — [lang] 번역이 있으면 번역, 없으면 원문. */
fun Diary.localizedTitle(lang: String): String =
    translationFor(lang)?.title?.get(lang)?.takeIf { it.isNotBlank() } ?: title

/** 화면에 보여 줄 본문 — [lang] 번역이 있으면 번역, 없으면 원문. */
fun Diary.localizedContent(lang: String): String =
    translationFor(lang)?.content?.get(lang)?.takeIf { it.isNotBlank() } ?: content

/** [lang] 로 보여 주는 글이 원문과 다른가(= "번역됨" 표시/원문 보기 토글이 필요한가). */
fun Diary.isTranslatedFor(lang: String): Boolean =
    localizedTitle(lang) != title || localizedContent(lang) != content
