package com.chaminwoo.stary.core.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 반투명 탑바가 덮는 높이(상태바 + 탑바). `MainScreen` 의 Scaffold 가 제공한다.
 *
 * 탑바가 반투명해지면서 **각 화면은 화면 전체(탑바 뒤까지)를 차지**하고 배경(별 이미지/지도)을
 * 탑바 뒤로 그대로 올린다 — 그래야 탑바가 화면과 이어져 보인다.
 * 대신 화면의 **콘텐츠 레이어**는 이 값만큼 내려서(padding / LazyColumn contentPadding)
 * 탑바에 가리지 않게 한다. 탑바가 없는 상태(몰입 모드 등)에선 0.dp.
 *
 * iOS 대응: 시스템 내비바(`StaryApp.configureNavigationBarAppearance`)와
 * 지도 상단바(`RootView.safeAreaInset`)가 같은 역할을 자동으로 한다.
 */
val LocalTopBarInset = compositionLocalOf { 0.dp }

/** 탑바 기본 높이(상태바 제외) — 값이 필요할 때의 폴백. */
val TopBarHeight: Dp = 56.dp

/**
 * 반투명 탑바 배경 — 위(상태바)는 거의 검정, 아래로 갈수록 투명해져 화면과 자연스럽게 이어진다.
 * (배경 블러는 지도가 SurfaceView 라 Compose 로 캡처할 수 없어 스크림 그라데이션으로 대체.)
 */
val TopBarScrim: Brush = Brush.verticalGradient(
    0.0f to Color(0xFF0D0D0D),
    0.55f to Color(0xEC0D0D0D),
    1.0f to Color(0xCC0D0D0D),
)
