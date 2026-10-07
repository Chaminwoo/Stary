package com.chaminwoo.stary.core.util

import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.isTranslatedFor
import com.chaminwoo.stary.core.model.localizedContent
import com.chaminwoo.stary.core.model.localizedTitle

/**
 * 화면에 **보여 줄** 다이어리 제목/본문 — 앱 언어(ko/en/ja)의 서버 선번역이 있으면 번역, 없으면 원문.
 * 번역이 없는 예전 글·번역 전/실패 글도 같은 호출로 원문이 나오므로 호출부에 분기가 필요 없다.
 *
 * ⚠️ **표시 전용.** 수정 입력칸 초기값·신고·공유 카드·알림 제목은 `diary.title`/`diary.content`(원문)를 써야 한다 —
 *    번역문이 원문으로 저장되거나 신고에 실리면 안 된다.
 * 언어는 푸시 문구와 같은 규칙(앱 언어, 지원 외는 ko)이라 [LocaleManager.pushLanguage] 를 쓴다.
 */
fun Diary.displayTitle(): String = localizedTitle(LocaleManager.pushLanguage())

fun Diary.displayContent(): String = localizedContent(LocaleManager.pushLanguage())

/** 지금 앱 언어로 번역된 글을 보여 주는 중인가("번역됨 · 원문 보기" 토글 노출 조건). */
fun Diary.isDisplayTranslated(): Boolean = isTranslatedFor(LocaleManager.pushLanguage())
