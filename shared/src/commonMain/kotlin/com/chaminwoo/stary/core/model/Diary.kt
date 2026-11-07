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
    /**
     * 앱에 내장되지 않은 모양(starType ≥ 내장 개수, DB 정의 — 2026-10-22)일 때만 싣는 SVG 경로(0~1 단위 박스).
     * 이전 버전 앱도 이걸로 그린다(androidApp `StarCatalog` / iOS `StarCatalog`). 내장 모양이면 빈 값.
     */
    val starPath: String = "",
    /** 앱에 내장되지 않은 색(starColor ≥ 내장 개수)일 때만 싣는 "#RRGGBB" 정지점 1~3개. 내장 색이면 빈 목록. */
    val starColors: List<String> = emptyList(),
    /** 공개 범위: "public"(전체), "friends"(친구만), "private"(나만보기). */
    val visibilityType: String = "public",
    /**
     * 별 고리(2026-10-26) — 올릴 때 고른 고리 아이템 id(`starlightItems` 의 kind "aura"). 빈 값 = 미착용.
     * 서버(`checkDiaryRing`)가 만들 때 소유를 확인하고, 안 가진 고리면 지운다. 지도는 이 값으로 그 별에만 고리를 그린다.
     */
    val ring: String = "",
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

/**
 * 서버에서 읽은 다이어리를 화면·지도에 쓰기 전에 거르는 방어선 한도.
 * 앱의 입력 제한(제목 30 · 본문 2000 — [com.chaminwoo.stary.shared.config.StaryConfig])보다 넉넉하게 잡아, 정상 글은 그대로 두고
 * 비정상적으로 큰 값만 잘라 메모리·렌더 부담을 막는다.
 */
object DiaryLimits {
    const val TITLE = 100
    const val CONTENT = 4000
    const val USER_NAME = 60
    const val URL = 2048
    const val STAR_PATH = 2000
    const val STAR_COLORS = 3
    const val RING = 64
    const val TRANSLATION_ENTRIES = 8
    const val LANG_KEY = 12
    const val COUNT_MAX = 1_000_000_000
    /** 지도 정렬에서 "미래"로 보아 제외하는 여유(하루). */
    const val FUTURE_SLACK_MS = 24L * 60 * 60 * 1000
}

private fun String.clip(max: Int): String {
    if (length <= max) return this
    // 서로게이트 쌍 한가운데를 자르지 않는다.
    val cut = if (this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, cut)
}

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/** 위도·경도가 유한하고 범위 안인가 — 아니면 지도에 올리지 않는다. */
fun Diary.hasValidLocation(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0

/** 작성 시각이 [nowMs] 보다 하루 넘게 미래인가 — 지도 정렬 맨 위에 고정되는 것을 막으려고 지도에서 제외한다. */
fun Diary.isFutureDated(nowMs: Long): Boolean = createdAt > nowMs + DiaryLimits.FUTURE_SLACK_MS

private fun Map<String, String>.clipMap(max: Int): Map<String, String> =
    entries.asSequence()
        .filter { it.key.length <= DiaryLimits.LANG_KEY }
        .take(DiaryLimits.TRANSLATION_ENTRIES)
        .associate { it.key to it.value.clip(max) }

/**
 * 외부(Firestore)에서 온 값을 안전한 범위로 정리한 복사본. 던지지 않는다.
 *  - 좌표가 비정상이면 0/0 으로 둔다(지도 사용 여부는 [hasValidLocation] 으로 따로 판단 — 이 함수 호출 **전** 원본에서).
 *  - 음수 카운트 → 0, 문자열 길이 상한, 알 수 없는 공개 범위는 "private"(남에게 보이지 않는 쪽).
 *  - 미디어 URL 은 https 만, 별 경로·색·고리·번역 맵은 개수·길이를 제한한다.
 */
fun Diary.sanitized(): Diary = copy(
    userId = userId.clip(128),
    userName = userName.clip(DiaryLimits.USER_NAME),
    title = title.clip(DiaryLimits.TITLE),
    content = content.clip(DiaryLimits.CONTENT),
    imageUrl = imageUrl.takeIf { it.length <= DiaryLimits.URL && it.startsWith("https://") }.orEmpty(),
    videoUrl = videoUrl.takeIf { it.length <= DiaryLimits.URL && it.startsWith("https://") }.orEmpty(),
    latitude = if (hasValidLocation()) latitude else 0.0,
    longitude = if (hasValidLocation()) longitude else 0.0,
    createdAt = createdAt.coerceAtLeast(0L),
    likeCount = likeCount.coerceIn(0, DiaryLimits.COUNT_MAX),
    commentCount = commentCount.coerceIn(0, DiaryLimits.COUNT_MAX),
    viewCount = viewCount.coerceIn(0, DiaryLimits.COUNT_MAX),
    starType = starType.coerceIn(0, 9999),
    starColor = starColor.coerceIn(0, 9999),
    starPath = starPath.takeIf { it.length <= DiaryLimits.STAR_PATH }.orEmpty(),
    starColors = starColors.asSequence().filter { HEX_COLOR.matches(it) }.take(DiaryLimits.STAR_COLORS).toList(),
    visibilityType = if (visibilityType == "public" || visibilityType == "friends" || visibilityType == "private") visibilityType else "private",
    ring = ring.clip(DiaryLimits.RING),
    translations = translations?.let { t ->
        t.copy(
            sourceLang = t.sourceLang.clip(DiaryLimits.LANG_KEY),
            title = t.title.clipMap(DiaryLimits.TITLE),
            content = t.content.clipMap(DiaryLimits.CONTENT),
            status = t.status.clip(16),
            srcHash = t.srcHash.clip(64),
        )
    },
)
