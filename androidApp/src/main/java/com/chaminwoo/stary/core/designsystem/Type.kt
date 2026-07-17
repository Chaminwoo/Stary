package com.chaminwoo.stary.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R

// 영문 디스플레이용 (타이틀, 대형 헤더)
val PoetsenOne = FontFamily(
    Font(R.font.poetsen_one_regular, FontWeight.Normal),
)

// 한글 본문·UI용 (한글 + 영문 혼용 영역)
val PoorStory = FontFamily(
    Font(R.font.poor_story_regular, FontWeight.Normal),
)

// 상단 바 타이틀 + 드로어 목록용
val HSHwalkongSerif = FontFamily(
    Font(R.font.hs_hwalkong_serif_regular, FontWeight.Normal),
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
    titleMedium = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall  = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge   = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium  = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall   = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge  = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontFamily = PoorStory, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
