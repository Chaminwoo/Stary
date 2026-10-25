package com.chaminwoo.stary.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 앱 색 토큰(코스믹 네이비) — 이름 붙은 아홉 가지. 머티리얼 색표([StaryTheme])도 이 값에 연결되어 있어
 * 따로 색을 주지 않은 입력칸·메뉴·확인 창도 이 색을 따른다.
 * iOS `Theme.swift` 와 값 1:1 동기화(한쪽 바꾸면 반대쪽도).
 * 화면 코드 속 색 리터럴은 그 화면을 고칠 때 여기 이름으로 바꾼다(한 번에 갈아엎지 않는다).
 */
object StaryColors {
    val Bg      = Color(0xFF0E1018) // 바탕
    val Surface = Color(0xFF181C2A) // 면(카드·시트)
    val Raised  = Color(0xFF1E2334) // 높은 면(카드 윗단·눌린 칸)
    val Line    = Color(0xFF2B3247) // 선(외곽선·구분선)
    val Text    = Color(0xFFF2F4FA) // 글자
    val Muted   = Color(0xFF8A92A6) // 흐린 글자
    val Accent  = Color(0xFF9FB3E8) // 강조(별빛 남색 — 선택·링크·로딩)
    val Mint    = Color(0xFF6EE7B7) // 민트(브랜드 — 지도 FAB/별자리/필터/친구)
    val Danger  = Color(0xFFFF6B6B) // 위험(지우기·오류)
}

// 예전 이름 — 아직 쓰는 화면이 있어 토큰을 가리키게만 둔다.
val TextPrimary = StaryColors.Text
val TextSub     = StaryColors.Muted
val MintBlue    = Color(0xFF3B82F6) // 민트→블루 그라데이션 짝(업로드/생성 FAB 등)
