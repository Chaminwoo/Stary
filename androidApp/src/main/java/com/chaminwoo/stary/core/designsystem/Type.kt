package com.chaminwoo.stary.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R

// 영문 디스플레이용 (타이틀, 대형 헤더)
val PoetsenOne = FontFamily(
    Font(R.font.poetsen_one_regular, FontWeight.Normal),
)

// 한글 본문·UI용 (한글 + 영문 혼용 영역, 상단 바 타이틀 + 드로어 목록 포함).
// MinSans 는 가변 폰트(wght 100~900) — 단일 등록이면 굵은 글씨가 시스템 가짜 볼드(faux bold)로
// 뭉툭해지므로, 굵기 단계를 실제 wght 축의 한 단계 얇은 값으로 매핑해 등록한다(전체적으로 얇게).
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun minSans(weight: FontWeight, wght: Int) = Font(
    R.font.min_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(wght)),
)

// ⚠️ 이 테이블은 "요청 굵기 → 실제 wght 축" 매핑이라 굵기 하향 일괄 치환 대상이 아니다.
//    같은 FontWeight 를 두 번 등록하면 어느 wght 가 걸릴지 모호해진다 — 항상 단조 증가 1:1 유지.
val MinSans = FontFamily(
    minSans(FontWeight.Light, 300),
    minSans(FontWeight.Normal, 400),
    minSans(FontWeight.Medium, 450),
    minSans(FontWeight.SemiBold, 500),
    minSans(FontWeight.Bold, 550),
)

val Typography = Typography(
    // ── 영문 위주 대형 표시 텍스트 ──────────────────────────────
    displayLarge  = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontFamily = PoetsenOne, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),

    // ── 한글 혼용 본문·UI 텍스트 ────────────────────────────────
    titleMedium = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall  = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge   = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium  = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall   = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge  = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontFamily = MinSans, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
